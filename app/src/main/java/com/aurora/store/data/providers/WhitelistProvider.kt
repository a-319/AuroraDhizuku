/*
 * SPDX-License-Identifier: GPL-3.0-or-later
 */

package com.aurora.store.data.providers

import android.content.Context
import android.util.Log
import com.aurora.extensions.TAG
import com.aurora.store.data.network.HttpClient
import com.aurora.store.util.ManagedConfigurations
import com.aurora.store.util.PackageUtil
import dagger.hilt.android.qualifiers.ApplicationContext
import java.io.File
import java.io.IOException
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive

/**
 * Provider for the managed-configuration based install whitelist.
 *
 * When the device administrator sets the [ManagedConfigurations.WHITELIST_URL] restriction to a
 * URL of a JSON list, the store switches to whitelist mode and only allows installing:
 * 1. packages listed in the remote list;
 * 2. packages listed in any nested list whose URL appears as an entry of another list
 *    (so a whole category can be allowed with a single line); and
 * 3. packages already on the device — installed or archived (i.e. updates).
 *
 * Supported JSON formats (both may be mixed freely across nested lists):
 * - an array whose string entries are either package names or `http(s)` URLs of nested lists:
 *   `["com.example.app", "https://example.com/category.json"]`
 * - an object with `packages` (package names) and/or `includes`/`lists`/`urls` (nested list
 *   URLs) arrays.
 *
 * Lists may be large, so the merged result is kept as an in-memory [HashSet] for O(1) lookups,
 * refreshed at most once per [CACHE_TTL_MILLIS], and persisted to disk so lookups keep working
 * across restarts and while offline. If the whitelist is configured but cannot be loaded at
 * all, installs of new apps are blocked (fail closed); updates of installed apps always pass.
 */
@Singleton
class WhitelistProvider @Inject constructor(
    @ApplicationContext private val context: Context,
    private val httpClient: HttpClient,
    private val json: Json
) {

    companion object {
        private const val CACHE_FILE_NAME = "managed_whitelist.json"
        private const val CACHE_TTL_MILLIS = 30 * 60 * 1000L

        // Guards against cyclic or maliciously deep list chains
        private const val MAX_LIST_DEPTH = 5
        private const val MAX_LISTS = 100

        private val PACKAGE_KEYS = setOf("packages", "apps")
        private val LIST_KEYS = setOf("includes", "lists", "urls")

        private fun isUrl(value: String): Boolean =
            value.startsWith("https://", ignoreCase = true) ||
                value.startsWith("http://", ignoreCase = true)
    }

    @Serializable
    private data class CachedWhitelist(val url: String, val packages: List<String>)

    private val mutex = Mutex()

    @Volatile
    private var cachedUrl: String? = null

    @Volatile
    private var cachedPackages: Set<String>? = null

    @Volatile
    private var lastRefreshTime = 0L

    private val cacheFile get() = File(context.filesDir, CACHE_FILE_NAME)

    /**
     * URL of the remote whitelist from managed configurations, null when not configured.
     */
    val whitelistUrl: String?
        get() = ManagedConfigurations.getString(context, ManagedConfigurations.WHITELIST_URL)
            ?.trim()
            ?.takeIf { it.isNotEmpty() }

    /**
     * Whether whitelist mode is active (a whitelist URL is set via managed configurations).
     */
    val isEnforced: Boolean
        get() = whitelistUrl != null

    /**
     * Whether [packageName] may be downloaded & installed under the current policy.
     * @param forceRefresh Bypasses the cache TTL and re-fetches the whitelist before checking,
     * e.g. when the user requests approval and wants to know if they were just added.
     */
    suspend fun isAllowed(packageName: String, forceRefresh: Boolean = false): Boolean {
        val url = whitelistUrl ?: return true

        // Updates of apps already on the device (or archived, i.e. previously installed) are
        // always allowed
        if (PackageUtil.isInstalled(context, packageName) ||
            PackageUtil.isArchived(context, packageName)
        ) {
            return true
        }

        val whitelist = getWhitelist(url, forceRefresh) ?: return false
        return packageName in whitelist
    }

    /**
     * Warms the whitelist cache so the first [isAllowed] check doesn't pay the network fetch.
     * No-op when whitelist mode is inactive.
     */
    suspend fun prefetch() {
        whitelistUrl?.let { getWhitelist(it) }
    }

    /**
     * Returns the merged set of whitelisted packages for [url], or null when it cannot be
     * loaded from network, memory or disk. The in-memory copy is keyed by URL, so changing the
     * restriction invalidates it immediately; a stale copy (memory, then disk) is used when
     * the periodic refresh fails, e.g. while offline.
     */
    private suspend fun getWhitelist(
        url: String,
        forceRefresh: Boolean = false
    ): Set<String>? = mutex.withLock {
        val fresh = !forceRefresh && cachedUrl == url && cachedPackages != null &&
            System.currentTimeMillis() - lastRefreshTime < CACHE_TTL_MILLIS
        if (fresh) return cachedPackages

        withContext(Dispatchers.IO) {
            try {
                val packages = fetchMergedWhitelist(url)
                cachedUrl = url
                cachedPackages = packages
                lastRefreshTime = System.currentTimeMillis()
                Log.i(TAG, "Fetched whitelist with ${packages.size} packages from $url")
                saveToDisk(url, packages)
                packages
            } catch (exception: Exception) {
                Log.e(TAG, "Failed to fetch whitelist from $url", exception)
                when {
                    // Stale in-memory copy for the same URL beats a disk read
                    cachedUrl == url && cachedPackages != null -> cachedPackages
                    else -> loadFromDisk(url)?.also {
                        cachedUrl = url
                        cachedPackages = it
                        // lastRefreshTime stays old so the next check retries the network
                    }
                }
            }
        }
    }

    /**
     * Fetches [rootUrl] and every nested list reachable from it, returning all package names
     * merged into a single set. Any fetch/parse failure propagates so a partial list is never
     * mistaken for the full policy.
     */
    private fun fetchMergedWhitelist(rootUrl: String): Set<String> {
        val packages = HashSet<String>()
        val visited = HashSet<String>()
        fetchList(rootUrl, 0, packages, visited)
        return packages
    }

    private fun fetchList(
        url: String,
        depth: Int,
        packages: MutableSet<String>,
        visited: MutableSet<String>
    ) {
        if (depth > MAX_LIST_DEPTH) {
            Log.w(TAG, "Ignoring whitelist deeper than $MAX_LIST_DEPTH levels: $url")
            return
        }
        // Silently skip URLs already fetched (cycles) but cap the total amount of lists
        if (!visited.add(url)) return
        if (visited.size > MAX_LISTS) throw IOException("Too many nested whitelists (> $MAX_LISTS)")

        httpClient.call(url).use { response ->
            if (!response.isSuccessful) {
                throw IOException("HTTP ${response.code} while fetching whitelist $url")
            }
            collect(json.parseToJsonElement(response.body.string()), depth, packages, visited)
        }
    }

    private fun collect(
        element: JsonElement,
        depth: Int,
        packages: MutableSet<String>,
        visited: MutableSet<String>
    ) {
        when (element) {
            is JsonArray -> element.forEach { entry ->
                val value = (entry as? JsonPrimitive)
                    ?.takeIf { it.isString }
                    ?.content?.trim()
                    ?.takeIf { it.isNotEmpty() }
                    ?: return@forEach
                if (isUrl(value)) {
                    fetchList(value, depth + 1, packages, visited)
                } else {
                    packages.add(value)
                }
            }

            is JsonObject -> {
                PACKAGE_KEYS.forEach { key ->
                    (element[key] as? JsonArray)?.forEach { entry ->
                        (entry as? JsonPrimitive)
                            ?.takeIf { it.isString }
                            ?.content?.trim()
                            ?.takeIf { it.isNotEmpty() }
                            ?.let { packages.add(it) }
                    }
                }
                LIST_KEYS.forEach { key ->
                    (element[key] as? JsonArray)?.forEach { entry ->
                        (entry as? JsonPrimitive)
                            ?.takeIf { it.isString }
                            ?.content?.trim()
                            ?.takeIf { isUrl(it) }
                            ?.let { fetchList(it, depth + 1, packages, visited) }
                    }
                }
            }

            else -> throw IOException("Unsupported whitelist JSON structure")
        }
    }

    private fun saveToDisk(url: String, packages: Set<String>) {
        try {
            val tmpFile = File(cacheFile.parentFile, "$CACHE_FILE_NAME.tmp")
            tmpFile.writeText(json.encodeToString(CachedWhitelist(url, packages.toList())))
            if (!tmpFile.renameTo(cacheFile)) {
                tmpFile.copyTo(cacheFile, overwrite = true)
                tmpFile.delete()
            }
        } catch (exception: Exception) {
            Log.e(TAG, "Failed to persist whitelist cache", exception)
        }
    }

    private fun loadFromDisk(url: String): Set<String>? = try {
        cacheFile.takeIf { it.exists() }
            ?.let { json.decodeFromString<CachedWhitelist>(it.readText()) }
            ?.takeIf { it.url == url }
            ?.packages
            ?.toHashSet()
    } catch (exception: Exception) {
        Log.e(TAG, "Failed to read whitelist cache", exception)
        null
    }
}

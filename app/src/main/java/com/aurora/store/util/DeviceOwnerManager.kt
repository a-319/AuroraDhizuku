/*
 * SPDX-License-Identifier: GPL-3.0-or-later
 */

package com.aurora.store.util

import android.app.admin.DeviceAdminInfo
import android.app.admin.DeviceAdminReceiver
import android.app.admin.DevicePolicyManager
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.content.pm.ResolveInfo
import android.os.PersistableBundle
import android.os.UserManager
import android.util.Log
import androidx.core.content.getSystemService
import com.aurora.extensions.isPAndAbove
import com.aurora.extensions.isRAndAbove
import com.aurora.store.R
import com.aurora.store.data.model.DeviceOwnerMode
import com.aurora.store.data.model.DeviceOwnerState
import com.aurora.store.data.model.DeviceOwnerTarget
import com.aurora.store.data.receiver.DeviceOwnerReceiver
import com.aurora.store.data.receiver.ProtectedDeviceOwnerReceiver
import com.aurora.store.util.Preferences.PREFERENCE_DEVICE_OWNER_GLOBAL_LOCK
import com.aurora.store.util.Preferences.PREFERENCE_DEVICE_OWNER_LAST_OWNER
import com.aurora.store.util.Preferences.PREFERENCE_DEVICE_OWNER_SOURCE

/**
 * Handles the device owner permission: holding it, handing it over to another app supporting the
 * transfer and receiving it from such an app.
 *
 * Aurora Store ships two device admin receivers, so the app handing over the ownership decides
 * how we hold it by picking one of them as the transfer target:
 *
 *  - [DeviceOwnerReceiver] holds the ownership without enforcing anything.
 *  - [ProtectedDeviceOwnerReceiver] additionally protects the app the ownership came from and
 *    only allows handing the ownership back to that very app.
 */
object DeviceOwnerManager {

    private const val TAG = "DeviceOwnerManager"

    /**
     * Key carrying the package name of the app handing over the ownership. It is our own key, so
     * it is only filled in when the ownership was handed over by another Aurora Store build.
     */
    const val EXTRA_SOURCE_PACKAGE = "com.aurora.store.extra.TRANSFER_SOURCE_PACKAGE"

    /**
     * Device admin receiver holding the ownership without enforcing any policy
     */
    fun getStandardAdmin(context: Context): ComponentName =
        ComponentName(context, DeviceOwnerReceiver::class.java)

    /**
     * Device admin receiver protecting the app the ownership was received from
     */
    fun getProtectedAdmin(context: Context): ComponentName =
        ComponentName(context, ProtectedDeviceOwnerReceiver::class.java)

    /**
     * All device admin receivers we ship, any of them can be made the device owner
     */
    fun getAdminComponents(context: Context): List<ComponentName> =
        listOf(getStandardAdmin(context), getProtectedAdmin(context))

    private fun getPolicyManager(context: Context): DevicePolicyManager? =
        context.getSystemService<DevicePolicyManager>()

    /**
     * Whether Aurora Store currently holds the device owner permission
     */
    fun isDeviceOwner(context: Context): Boolean =
        getPolicyManager(context)?.isDeviceOwnerApp(context.packageName) == true

    /**
     * The device admin receiver of ours currently holding the ownership, if any
     */
    fun getActiveAdmin(context: Context): ComponentName? {
        val policyManager = getPolicyManager(context) ?: return null
        if (!policyManager.isDeviceOwnerApp(context.packageName)) return null

        return getAdminComponents(context).firstOrNull { policyManager.isAdminActive(it) }
    }

    /**
     * Mode in which the ownership is currently held, decided by the admin holding it
     */
    fun getMode(context: Context): DeviceOwnerMode = when (getActiveAdmin(context)) {
        getProtectedAdmin(context) -> DeviceOwnerMode.PROTECTED
        else -> DeviceOwnerMode.STANDARD
    }

    /**
     * Package the ownership was received from, null if we were not given it by another app
     */
    fun getSourcePackageName(context: Context): String? = when {
        isDeviceOwner(context) -> {
            Preferences.getString(context, PREFERENCE_DEVICE_OWNER_SOURCE).ifBlank { null }
        }

        else -> null
    }

    /**
     * Whether the ownership may only be handed back to the app it was received from
     */
    fun isLockedToSource(context: Context): Boolean =
        getMode(context) == DeviceOwnerMode.PROTECTED && getSourcePackageName(context) != null

    /**
     * Lists apps declaring a device admin receiver which supports receiving the ownership.
     * Our own receivers are left out, Android refuses to transfer within the same package.
     */
    fun getTransferTargets(context: Context): List<DeviceOwnerTarget> {
        // Handing the ownership over is only possible since Android 9
        if (!isPAndAbove) return emptyList()

        val packageManager = context.packageManager
        val intent = Intent(DeviceAdminReceiver.ACTION_DEVICE_ADMIN_ENABLED)

        val receivers: List<ResolveInfo> = try {
            @Suppress("DEPRECATION")
            packageManager.queryBroadcastReceivers(intent, PackageManager.GET_META_DATA)
        } catch (exception: Exception) {
            Log.e(TAG, "Failed to look for apps supporting the ownership transfer", exception)
            return emptyList()
        }

        val targets = mutableListOf<DeviceOwnerTarget>()
        for (resolveInfo in receivers) {
            val activityInfo = resolveInfo.activityInfo ?: continue
            if (activityInfo.packageName == context.packageName) continue

            val adminInfo = try {
                DeviceAdminInfo(context, resolveInfo)
            } catch (exception: Exception) {
                Log.e(TAG, "Failed to read the admin of ${activityInfo.packageName}", exception)
                continue
            }

            if (!adminInfo.supportsTransferOwnership()) continue

            targets.add(
                DeviceOwnerTarget(
                    packageName = adminInfo.component.packageName,
                    className = adminInfo.component.className,
                    appLabel = activityInfo.applicationInfo
                        ?.let { packageManager.getApplicationLabel(it).toString() }
                        ?: adminInfo.component.packageName,
                    adminLabel = adminInfo.loadLabel(packageManager).toString()
                )
            )
        }

        return targets
            .distinctBy { it.componentName }
            .sortedBy { it.appLabel.lowercase() }
    }

    /**
     * Remembers whoever currently holds the ownership, so that we know where it came from once
     * it is handed over to us. Apps other than Aurora Store do not tell us who they are.
     */
    fun cacheCurrentOwner(context: Context) {
        val policyManager = getPolicyManager(context) ?: return
        if (policyManager.isDeviceOwnerApp(context.packageName)) return

        val owner = getTransferTargets(context)
            .map { it.packageName }
            .distinct()
            .firstOrNull { policyManager.isDeviceOwnerApp(it) }

        if (owner != null) context.save(PREFERENCE_DEVICE_OWNER_LAST_OWNER, owner)
    }

    /**
     * Hands the device owner permission over to the given app. Any protection we enforce is
     * dropped beforehand, we cannot undo it once the ownership is gone.
     * @throws IllegalStateException when the ownership cannot be handed over to the given app
     */
    fun transferOwnership(context: Context, target: DeviceOwnerTarget) {
        // Handing the ownership over is only possible since Android 9
        if (!isPAndAbove) {
            throw IllegalStateException(
                context.getString(R.string.device_owner_transfer_unsupported)
            )
        }

        val policyManager = getPolicyManager(context)
        val admin = getActiveAdmin(context)
        if (policyManager == null || admin == null) {
            error(context.getString(R.string.installer_device_owner_unavailable))
        }

        val sourcePackageName = getSourcePackageName(context)
        val isProtecting = getMode(context) == DeviceOwnerMode.PROTECTED
        if (isProtecting && sourcePackageName != null && target.packageName != sourcePackageName) {
            error(context.getString(R.string.device_owner_transfer_locked, sourcePackageName))
        }

        clearProtections(context, admin)

        val bundle = PersistableBundle().apply {
            putString(EXTRA_SOURCE_PACKAGE, context.packageName)
        }

        try {
            policyManager.transferOwnership(admin, target.componentName, bundle)
        } catch (exception: Exception) {
            Log.e(TAG, "Failed to hand the ownership over to ${target.componentName}", exception)
            // We are still the owner, put the protection we just dropped back in place
            if (isProtecting) applyProtections(context, admin, sourcePackageName)
            throw exception
        }

        Log.i(TAG, "Handed the ownership over to ${target.componentName}")
        Preferences.remove(context, PREFERENCE_DEVICE_OWNER_SOURCE)
        context.save(PREFERENCE_DEVICE_OWNER_LAST_OWNER, target.packageName)
    }

    /**
     * Called by our device admin receivers once another app has handed the ownership over to us
     */
    fun onOwnershipReceived(context: Context, admin: ComponentName, bundle: PersistableBundle?) {
        val sourcePackageName = bundle?.getString(EXTRA_SOURCE_PACKAGE)?.ifBlank { null }
            ?: Preferences.getString(context, PREFERENCE_DEVICE_OWNER_LAST_OWNER)
                .ifBlank { null }
                ?.takeIf { it != context.packageName }

        Log.i(TAG, "Received the ownership on $admin from ${sourcePackageName ?: "an unknown app"}")

        if (sourcePackageName != null) {
            context.save(PREFERENCE_DEVICE_OWNER_SOURCE, sourcePackageName)
        } else {
            Preferences.remove(context, PREFERENCE_DEVICE_OWNER_SOURCE)
        }

        if (admin == getProtectedAdmin(context)) {
            applyProtections(context, admin, sourcePackageName)
        }
    }

    /**
     * Protects the app the ownership was received from: it can neither be uninstalled nor be
     * force stopped or cleared by the user. Android below 11 cannot take the user control away
     * from a single app, so every app loses it there.
     */
    fun applyProtections(context: Context, admin: ComponentName, packageName: String?) {
        if (packageName.isNullOrBlank()) return
        val policyManager = getPolicyManager(context) ?: return

        try {
            policyManager.setUninstallBlocked(admin, packageName, true)
        } catch (exception: Exception) {
            Log.e(TAG, "Failed to block uninstallation of $packageName", exception)
        }

        var restrictedAlone = false
        if (isRAndAbove) {
            try {
                policyManager.setUserControlDisabledPackages(admin, listOf(packageName))
                restrictedAlone = true
            } catch (exception: Exception) {
                Log.e(TAG, "Failed to take user control away from $packageName alone", exception)
            }
        }

        if (!restrictedAlone) {
            try {
                policyManager.addUserRestriction(admin, UserManager.DISALLOW_APPS_CONTROL)
                context.save(PREFERENCE_DEVICE_OWNER_GLOBAL_LOCK, true)
            } catch (exception: Exception) {
                Log.e(TAG, "Failed to take user control away from every app", exception)
            }
        }
    }

    /**
     * Drops every protection put in place by [applyProtections]
     */
    fun clearProtections(context: Context, admin: ComponentName) {
        val policyManager = getPolicyManager(context) ?: return

        val packageName = Preferences.getString(context, PREFERENCE_DEVICE_OWNER_SOURCE)
        if (packageName.isNotBlank()) {
            try {
                policyManager.setUninstallBlocked(admin, packageName, false)
            } catch (exception: Exception) {
                Log.e(TAG, "Failed to unblock uninstallation of $packageName", exception)
            }
        }

        if (isRAndAbove) {
            try {
                policyManager.setUserControlDisabledPackages(admin, emptyList())
            } catch (exception: Exception) {
                Log.e(TAG, "Failed to hand user control back", exception)
            }
        }

        if (Preferences.getBoolean(context, PREFERENCE_DEVICE_OWNER_GLOBAL_LOCK)) {
            try {
                policyManager.clearUserRestriction(admin, UserManager.DISALLOW_APPS_CONTROL)
                context.save(PREFERENCE_DEVICE_OWNER_GLOBAL_LOCK, false)
            } catch (exception: Exception) {
                Log.e(TAG, "Failed to hand user control back to every app", exception)
            }
        }
    }

    /**
     * Gives up the device owner permission entirely
     */
    fun releaseOwnership(context: Context) {
        val policyManager = getPolicyManager(context) ?: return
        getActiveAdmin(context)?.let { clearProtections(context, it) }

        @Suppress("DEPRECATION")
        policyManager.clearDeviceOwnerApp(context.packageName)

        Preferences.remove(context, PREFERENCE_DEVICE_OWNER_SOURCE)
        Preferences.remove(context, PREFERENCE_DEVICE_OWNER_GLOBAL_LOCK)
    }

    /**
     * Collects everything the ownership screen needs to show
     */
    fun getState(context: Context): DeviceOwnerState {
        val isDeviceOwner = isDeviceOwner(context)
        val sourcePackageName = getSourcePackageName(context)
        val isLockedToSource = isLockedToSource(context)

        val targets = when {
            !isDeviceOwner -> emptyList()

            isLockedToSource -> {
                getTransferTargets(context).filter { it.packageName == sourcePackageName }
            }

            else -> getTransferTargets(context)
        }

        return DeviceOwnerState(
            isDeviceOwner = isDeviceOwner,
            mode = getMode(context),
            standardAdmin = getStandardAdmin(context).flattenToShortString(),
            protectedAdmin = getProtectedAdmin(context).flattenToShortString(),
            sourcePackageName = sourcePackageName,
            sourceAppLabel = sourcePackageName?.let { getAppLabel(context, it) },
            isTransferSupported = isPAndAbove,
            isLockedToSource = isLockedToSource,
            isUninstallBlocked = isUninstallBlocked(context, sourcePackageName),
            isUserControlLockedGlobally = isDeviceOwner &&
                Preferences.getBoolean(context, PREFERENCE_DEVICE_OWNER_GLOBAL_LOCK),
            targets = targets
        )
    }

    private fun isUninstallBlocked(context: Context, packageName: String?): Boolean {
        if (packageName.isNullOrBlank()) return false
        val admin = getActiveAdmin(context) ?: return false

        return try {
            getPolicyManager(context)?.isUninstallBlocked(admin, packageName) == true
        } catch (exception: Exception) {
            Log.e(TAG, "Failed to read the uninstall block of $packageName", exception)
            false
        }
    }

    private fun getAppLabel(context: Context, packageName: String): String = try {
        val packageManager = context.packageManager
        packageManager.getApplicationLabel(
            packageManager.getApplicationInfo(packageName, 0)
        ).toString()
    } catch (_: Exception) {
        packageName
    }
}

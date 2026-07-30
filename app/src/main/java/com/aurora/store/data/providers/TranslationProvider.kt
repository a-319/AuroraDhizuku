/*
 * SPDX-FileCopyrightText: 2026 The Calyx Institute
 * SPDX-License-Identifier: GPL-3.0-or-later
 */

package com.aurora.store.data.providers

import androidx.core.text.HtmlCompat
import com.aurora.Constants
import com.aurora.store.data.model.Translation
import com.aurora.store.data.network.HttpClient
import java.io.IOException
import java.util.Locale
import javax.inject.Inject
import javax.inject.Singleton
import okhttp3.FormBody
import org.json.JSONArray

/**
 * Provider class to machine translate texts served by Google Play, such as app descriptions.
 *
 * This talks to the same public endpoint that backs the "Translate" action on the Google Play
 * website. It needs no API key and lives on a host that is already covered by the certificate
 * pins we ship for Google.
 */
@Singleton
class TranslationProvider @Inject constructor(private val httpClient: HttpClient) {

    companion object {
        /**
         * Amount of characters to send in a single request, the endpoint rejects overly long
         * texts so longer descriptions are translated in multiple batches.
         */
        private const val MAX_BATCH_LENGTH = 4500

        private const val AUTO_DETECT = "auto"

        private const val PORTUGUESE = "pt"
        private const val PORTUGUESE_BRAZIL = "pt-BR"
        private const val PORTUGUESE_PORTUGAL = "pt-PT"
        private const val REGION_BRAZIL = "BR"
        private const val REGION_PORTUGAL = "PT"

        private const val CHINESE_SIMPLIFIED = "zh-CN"
        private const val CHINESE_TRADITIONAL = "zh-TW"
        private const val SCRIPT_CHINESE_TRADITIONAL = "Hant"
        private val REGIONS_CHINESE_TRADITIONAL = setOf("HK", "MO", "TW")
    }

    /**
     * Language the texts are translated into, which is the language of the device.
     *
     * Chinese and Portuguese are the only two languages the endpoint answers differently
     * depending on the region, and for both of them the plain language code yields one specific
     * variant: `zh` is Simplified Chinese and `pt` is Brazilian Portuguese. Those two are
     * therefore spelled out with their region, while every other language is requested by its
     * plain code, because a region the endpoint does not know about is answered with the
     * untranslated text rather than with an error.
     */
    val targetLanguage: String
        get() = with(Locale.getDefault()) {
            when (language) {
                Locale.CHINESE.language -> when {
                    script == SCRIPT_CHINESE_TRADITIONAL -> CHINESE_TRADITIONAL
                    country in REGIONS_CHINESE_TRADITIONAL -> CHINESE_TRADITIONAL
                    else -> CHINESE_SIMPLIFIED
                }

                PORTUGUESE -> when (country) {
                    REGION_PORTUGAL -> PORTUGUESE_PORTUGAL
                    REGION_BRAZIL -> PORTUGUESE_BRAZIL
                    else -> language
                }

                else -> language
            }
        }

    /**
     * Translates the given text into the requested language.
     *
     * HTML markup is not translatable, so it is stripped and the result is plain text with the
     * paragraphs of the original preserved.
     * @param text Text to translate, may contain the HTML markup served by Google Play
     * @param targetLanguage Language to translate the text into, see [targetLanguage]
     * @return The [Translation] of the given text
     */
    @Throws(IOException::class)
    fun translate(text: String, targetLanguage: String): Translation {
        val plainText = text.toPlainText()
        if (plainText.isBlank()) return Translation(text = plainText)

        val translations = batches(plainText).map { batch ->
            translateBatch(batch, targetLanguage)
        }

        return Translation(
            text = translations.joinToString(separator = "\n") { it.text },
            sourceLanguage = translations.firstNotNullOfOrNull { it.sourceLanguage }
        )
    }

    @Throws(IOException::class)
    private fun translateBatch(text: String, targetLanguage: String): Translation {
        val url = Constants.TRANSLATE_URL +
            "?client=gtx&sl=$AUTO_DETECT&tl=$targetLanguage&dt=t"

        // The text is sent as a form body instead of a query parameter, descriptions are far too
        // long to fit into an URL
        val requestBody = FormBody.Builder().add("q", text).build()
        val response = httpClient.post(url, emptyMap(), requestBody)

        if (!response.isSuccessful) {
            throw IOException(
                "Failed to translate text: [${response.code}] ${response.errorString}"
            )
        }

        return parseResponse(String(response.responseBytes))
    }

    /**
     * Parses the reply of the endpoint, which is a nested array holding the translated text split
     * into sentences, followed by the language the text was detected to be written in:
     *
     * `[[["translated ","original ",null,null,10]],null,"en",...]`
     */
    private fun parseResponse(response: String): Translation {
        val root = JSONArray(response)
        val sentences = root.optJSONArray(0) ?: throw IOException("Malformed translation reply")

        val text = buildString {
            for (index in 0 until sentences.length()) {
                append(sentences.optJSONArray(index)?.optString(0).orEmpty())
            }
        }

        return Translation(
            text = text.trim(),
            sourceLanguage = root.optString(2).ifBlank { null }
        )
    }

    /**
     * Groups the paragraphs of the given text into batches that are short enough to be translated
     * in a single request
     */
    private fun batches(text: String): List<String> {
        if (text.length <= MAX_BATCH_LENGTH) return listOf(text)

        val batches = mutableListOf<String>()
        val batch = StringBuilder()

        // Paragraphs are the natural boundaries of a description, splitting there keeps the
        // translated text readable no matter how the batches are stitched back together
        val paragraphs = text.lineSequence()
            .flatMap { line -> line.chunked(MAX_BATCH_LENGTH).ifEmpty { listOf(String()) } }

        paragraphs.forEach { paragraph ->
            if (batch.isNotEmpty() && batch.length + paragraph.length > MAX_BATCH_LENGTH) {
                batches.add(batch.toString())
                batch.clear()
            }
            if (batch.isNotEmpty()) batch.append("\n")
            batch.append(paragraph)
        }

        if (batch.isNotEmpty()) batches.add(batch.toString())
        return batches
    }

    private fun String.toPlainText(): String =
        HtmlCompat.fromHtml(this, HtmlCompat.FROM_HTML_MODE_COMPACT).toString().trim()
}

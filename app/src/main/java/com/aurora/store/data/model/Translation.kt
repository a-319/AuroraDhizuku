/*
 * SPDX-FileCopyrightText: 2026 The Calyx Institute
 * SPDX-License-Identifier: GPL-3.0-or-later
 */

package com.aurora.store.data.model

/**
 * A machine translated piece of text
 * @param text The translated text
 * @param sourceLanguage ISO 639 code of the language the text was translated from, if reported
 */
data class Translation(val text: String, val sourceLanguage: String? = null)

/**
 * State of the translation of a standalone piece of text, such as a selection handed over by
 * another app
 */
sealed interface TextTranslationState {

    /**
     * The translation is being fetched
     */
    data object InProgress : TextTranslationState

    /**
     * The text has been translated
     * @param text The translated text
     * @param sourceLanguage ISO 639 code of the language the text was translated from
     */
    data class Translated(
        val text: String,
        val sourceLanguage: String? = null
    ) : TextTranslationState

    /**
     * The translation could not be fetched
     */
    data object Failed : TextTranslationState
}

/**
 * State of the translation of an app's descriptions
 */
sealed interface TranslationState {

    /**
     * The original, untranslated descriptions are being shown
     */
    data object Original : TranslationState

    /**
     * A translation has been requested and is being fetched
     */
    data object InProgress : TranslationState

    /**
     * The translated descriptions are being shown
     * @param description Translation of the full description of the app
     * @param shortDescription Translation of the short description of the app
     * @param sourceLanguage ISO 639 code of the language the descriptions were translated from
     */
    data class Translated(
        val description: String,
        val shortDescription: String,
        val sourceLanguage: String? = null
    ) : TranslationState

    /**
     * The translation could not be fetched
     */
    data object Failed : TranslationState
}

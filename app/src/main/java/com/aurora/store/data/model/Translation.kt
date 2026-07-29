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
 * State of the translation of a piece of text, such as an app's description
 */
sealed interface TranslationState {

    /**
     * The original, untranslated text is being shown
     */
    data object Original : TranslationState

    /**
     * A translation has been requested and is being fetched
     */
    data object InProgress : TranslationState

    /**
     * The translated text is being shown
     */
    data class Translated(val translation: Translation) : TranslationState

    /**
     * The translation could not be fetched
     */
    data object Failed : TranslationState
}

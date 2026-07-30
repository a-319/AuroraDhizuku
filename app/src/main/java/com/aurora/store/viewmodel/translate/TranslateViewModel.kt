/*
 * SPDX-FileCopyrightText: 2026 The Calyx Institute
 * SPDX-License-Identifier: GPL-3.0-or-later
 */

package com.aurora.store.viewmodel.translate

import android.util.Log
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.aurora.extensions.TAG
import com.aurora.store.data.model.TextTranslationState
import com.aurora.store.data.providers.TranslationProvider
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

/**
 * ViewModel to translate a standalone piece of text, such as a selection handed over by another
 * app through the text processing action
 */
@HiltViewModel
class TranslateViewModel @Inject constructor(
    private val translationProvider: TranslationProvider
) : ViewModel() {

    private val _state = MutableStateFlow<TextTranslationState>(TextTranslationState.InProgress)
    val state = _state.asStateFlow()

    /**
     * Translates the given text into the language of the device
     * @param text Text to translate
     */
    fun translate(text: String) {
        _state.value = TextTranslationState.InProgress
        viewModelScope.launch(Dispatchers.IO) {
            _state.value = try {
                val translation = translationProvider.translate(text)
                TextTranslationState.Translated(
                    text = translation.text,
                    sourceLanguage = translation.sourceLanguage
                )
            } catch (exception: Exception) {
                Log.e(TAG, "Failed to translate text", exception)
                TextTranslationState.Failed
            }
        }
    }
}

/*
 * SPDX-FileCopyrightText: 2023-2025 The Calyx Institute
 * SPDX-FileCopyrightText: 2024 Aurora OSS
 * SPDX-License-Identifier: GPL-3.0-or-later
 */

package com.aurora.store.viewmodel.details

import android.util.Log
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.aurora.extensions.TAG
import com.aurora.gplayapi.data.models.App
import com.aurora.gplayapi.helpers.AppDetailsHelper
import com.aurora.store.data.model.Translation
import com.aurora.store.data.model.TranslationState
import com.aurora.store.data.providers.TranslationProvider
import dagger.assisted.Assisted
import dagger.assisted.AssistedFactory
import dagger.assisted.AssistedInject
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

@HiltViewModel(assistedFactory = MoreViewModel.Factory::class)
class MoreViewModel @AssistedInject constructor(
    @Assisted private val dependencies: List<String>,
    private val appDetailsHelper: AppDetailsHelper,
    private val translationProvider: TranslationProvider
) : ViewModel() {

    @AssistedFactory
    interface Factory {
        fun create(dependencies: List<String>): MoreViewModel
    }

    private val _dependentApps = MutableStateFlow<List<App>?>(emptyList())
    val dependentApps = _dependentApps.asStateFlow()

    private val _translationState = MutableStateFlow<TranslationState>(TranslationState.Original)
    val translationState = _translationState.asStateFlow()

    // Kept around so that toggling back and forth doesn't hit the network again
    private var translation: Translation? = null

    init {
        fetchDependencies()
    }

    /**
     * Toggles between the original and the translated description, fetching the translation on
     * the first request
     * @param description Description of the app, as served by Google Play
     */
    fun toggleTranslation(description: String) {
        when (_translationState.value) {
            is TranslationState.InProgress -> return

            is TranslationState.Translated -> {
                _translationState.value = TranslationState.Original
            }

            else -> {
                val cachedTranslation = translation
                if (cachedTranslation != null) {
                    _translationState.value = TranslationState.Translated(cachedTranslation)
                } else {
                    fetchTranslation(description)
                }
            }
        }
    }

    private fun fetchTranslation(description: String) {
        _translationState.value = TranslationState.InProgress
        viewModelScope.launch(Dispatchers.IO) {
            _translationState.value = try {
                translationProvider.translate(description)
                    .also { translation = it }
                    .let { TranslationState.Translated(it) }
            } catch (exception: Exception) {
                Log.e(TAG, "Failed to translate description", exception)
                TranslationState.Failed
            }
        }
    }

    private fun fetchDependencies() {
        viewModelScope.launch(Dispatchers.IO) {
            try {
                _dependentApps.value = appDetailsHelper.getAppByPackageName(dependencies)
            } catch (exception: Exception) {
                Log.e(TAG, "Failed to fetch dependencies", exception)
                _dependentApps.value = null
            }
        }
    }
}

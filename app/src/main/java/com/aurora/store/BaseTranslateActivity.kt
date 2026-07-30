/*
 * SPDX-FileCopyrightText: 2026 The Calyx Institute
 * SPDX-License-Identifier: GPL-3.0-or-later
 */

package com.aurora.store

import android.content.Intent
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import com.aurora.store.compose.theme.AuroraTheme
import com.aurora.store.compose.ui.translate.TranslateScreen

/**
 * Base class of the activities translating a text handed over by another app. They answer one
 * action each, and beyond that only differ in whether the user has to opt in to them.
 */
abstract class BaseTranslateActivity : ComponentActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        // The text processing action hands the text over as EXTRA_PROCESS_TEXT, an app asking for
        // a translation directly as EXTRA_TEXT
        val text = with(intent) {
            getCharSequenceExtra(Intent.EXTRA_PROCESS_TEXT)
                ?: getCharSequenceExtra(Intent.EXTRA_TEXT)
        }?.toString()
        if (text.isNullOrBlank()) return finish()

        // Apps handing over a text they can put the translation back into ask for it by declaring
        // the text editable, which is how they get to show the translation themselves
        val readOnly = intent.getBooleanExtra(Intent.EXTRA_PROCESS_TEXT_READONLY, true)

        setContent {
            AuroraTheme {
                TranslateScreen(
                    text = text,
                    onDismiss = ::finish,
                    canReturnTranslation = !readOnly,
                    onReturnTranslation = ::returnTranslation
                )
            }
        }
    }

    /**
     * Hands the translation back to the app the text came from, which then shows it in place of
     * the original text
     */
    private fun returnTranslation(text: String) {
        setResult(RESULT_OK, Intent().putExtra(Intent.EXTRA_PROCESS_TEXT, text))
        finish()
    }
}

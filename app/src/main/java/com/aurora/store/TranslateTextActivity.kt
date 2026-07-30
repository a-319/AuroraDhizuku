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
import dagger.hilt.android.AndroidEntryPoint

/**
 * Activity to translate a text selection handed over by another app through the system's text
 * processing action, which shows up next to copy & paste in the selection toolbar.
 */
@AndroidEntryPoint
class TranslateTextActivity : ComponentActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        val text = intent.getCharSequenceExtra(Intent.EXTRA_PROCESS_TEXT)?.toString()
        if (text.isNullOrBlank()) return finish()

        setContent {
            AuroraTheme {
                TranslateScreen(text = text, onDismiss = ::finish)
            }
        }
    }
}

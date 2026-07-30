/*
 * SPDX-FileCopyrightText: 2026 The Calyx Institute
 * SPDX-License-Identifier: GPL-3.0-or-later
 */

package com.aurora.store

import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import com.aurora.store.compose.theme.AuroraTheme
import com.aurora.store.compose.ui.translate.TranslateScreen
import dagger.hilt.android.AndroidEntryPoint

/**
 * Activity to translate a text selection handed over by another app through the system's text
 * processing action, which shows up next to copy & paste in the selection toolbar.
 *
 * Disabled in the manifest, so the action is only offered once the user opts in.
 */
@AndroidEntryPoint
class TranslateTextActivity : ComponentActivity() {

    companion object {

        /**
         * Enables or disables the translate action this activity contributes to the text selection
         * toolbar of other apps
         */
        fun setSelectionActionEnabled(context: Context, enabled: Boolean) {
            val state = when {
                enabled -> PackageManager.COMPONENT_ENABLED_STATE_ENABLED
                else -> PackageManager.COMPONENT_ENABLED_STATE_DISABLED
            }

            context.packageManager.setComponentEnabledSetting(
                ComponentName(context, TranslateTextActivity::class.java),
                state,
                PackageManager.DONT_KILL_APP
            )
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        val text = intent.getCharSequenceExtra(Intent.EXTRA_PROCESS_TEXT)?.toString()
        if (text.isNullOrBlank()) return finish()

        // Apps handing over a text they can put the translation back into ask for it by declaring
        // the text editable, which is how they get to show the translation themselves
        val readOnly = intent.getBooleanExtra(Intent.EXTRA_PROCESS_TEXT_READONLY, true)

        setContent {
            AuroraTheme {
                TranslateScreen(
                    text = text,
                    canReturnTranslation = !readOnly,
                    onReturnTranslation = ::returnTranslation,
                    onDismiss = ::finish
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

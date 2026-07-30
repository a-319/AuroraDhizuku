/*
 * SPDX-FileCopyrightText: 2026 The Calyx Institute
 * SPDX-License-Identifier: GPL-3.0-or-later
 */

package com.aurora.store

import android.content.ComponentName
import android.content.Context
import android.content.pm.PackageManager
import dagger.hilt.android.AndroidEntryPoint

/**
 * Activity to translate a text selection handed over by another app through the system's text
 * processing action, which shows up next to copy & paste in the selection toolbar.
 *
 * Disabled in the manifest, so the action is only offered once the user opts in. Apps asking for a
 * translation directly are served by the [TranslateRequestActivity] instead, no opt-in needed.
 */
@AndroidEntryPoint
class TranslateTextActivity : BaseTranslateActivity() {

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
}

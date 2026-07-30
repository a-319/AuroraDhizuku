/*
 * SPDX-FileCopyrightText: 2026 The Calyx Institute
 * SPDX-License-Identifier: GPL-3.0-or-later
 */

package com.aurora.store

import dagger.hilt.android.AndroidEntryPoint

/**
 * Activity to translate a text an app asks us to translate for it, through the platform's
 * translate action rather than through the text selection toolbar.
 *
 * An app asking for it says so itself, there is nothing here that could clutter the selection
 * toolbar of every text on the device, so this one is always around. That is what lets an offline
 * app, such as a device manager showing the descriptions of managed configurations, have a text
 * translated without going online itself.
 */
@AndroidEntryPoint
class TranslateRequestActivity : BaseTranslateActivity()

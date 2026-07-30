/*
 * SPDX-FileCopyrightText: 2026 The Calyx Institute
 * SPDX-License-Identifier: GPL-3.0-or-later
 */

package com.aurora.store.compose.ui.translate

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Card
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.dimensionResource
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.tooling.preview.Preview
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.aurora.extensions.copyToClipBoard
import com.aurora.store.R
import com.aurora.store.compose.composable.ContainedLoadingIndicator
import com.aurora.store.compose.preview.PreviewTemplate
import com.aurora.store.data.model.TextTranslationState
import com.aurora.store.viewmodel.translate.TranslateViewModel
import java.util.Locale

private const val SCRIM_ALPHA = 0.32F

/**
 * Screen to display the translation of a piece of text handed over by another app, hosted by the
 * TranslateTextActivity rather than being a part of the app's navigation.
 *
 * @param text Text to translate
 * @param onDismiss Callback when the translation is dismissed
 */
@Composable
fun TranslateScreen(
    text: String,
    onDismiss: () -> Unit,
    viewModel: TranslateViewModel = hiltViewModel()
) {
    val state by viewModel.state.collectAsStateWithLifecycle()

    LaunchedEffect(key1 = text) { viewModel.translate(text) }

    ScreenContent(
        text = text,
        state = state,
        onDismiss = onDismiss,
        onRetry = { viewModel.translate(text) }
    )
}

@Composable
private fun ScreenContent(
    text: String,
    state: TextTranslationState = TextTranslationState.InProgress,
    onDismiss: () -> Unit = {},
    onRetry: () -> Unit = {}
) {
    val context = LocalContext.current

    Box(
        modifier = Modifier
            .fillMaxSize()
            // The activity is translucent, dim whatever app the text was selected in
            .background(color = MaterialTheme.colorScheme.scrim.copy(alpha = SCRIM_ALPHA))
            .padding(dimensionResource(R.dimen.padding_large)),
        contentAlignment = Alignment.Center
    ) {
        Card(modifier = Modifier.fillMaxWidth()) {
            Column(
                modifier = Modifier.padding(dimensionResource(R.dimen.padding_large)),
                verticalArrangement = Arrangement.spacedBy(dimensionResource(R.dimen.margin_small))
            ) {
                TranslationHeader(state = state)

                HorizontalDivider()

                when (state) {
                    is TextTranslationState.InProgress -> {
                        ContainedLoadingIndicator(
                            modifier = Modifier.height(dimensionResource(R.dimen.icon_size_large))
                        )
                    }

                    is TextTranslationState.Failed -> {
                        Text(
                            text = stringResource(R.string.translate_failed),
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.error
                        )
                    }

                    is TextTranslationState.Translated -> {
                        TranslatedText(translation = state.text, original = text)
                    }
                }

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.End
                ) {
                    if (state is TextTranslationState.Failed) {
                        TextButton(onClick = onRetry) {
                            Text(text = stringResource(R.string.action_retry))
                        }
                    }

                    if (state is TextTranslationState.Translated) {
                        TextButton(onClick = { context.copyToClipBoard(state.text) }) {
                            Text(text = stringResource(R.string.action_copy))
                        }
                    }

                    TextButton(onClick = onDismiss) {
                        Text(text = stringResource(R.string.action_close))
                    }
                }
            }
        }
    }
}

/**
 * Composable to display the title of the translation alongside the language it was translated from
 */
@Composable
private fun TranslationHeader(state: TextTranslationState) {
    val sourceLanguage = (state as? TextTranslationState.Translated)?.sourceLanguage
        ?.let { Locale.forLanguageTag(it).displayLanguage }
        ?.takeIf { it.isNotBlank() }

    Row(
        modifier = Modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(dimensionResource(R.dimen.margin_small))
    ) {
        Icon(painter = painterResource(R.drawable.ic_translate), contentDescription = null)
        Column(modifier = Modifier.fillMaxWidth()) {
            Text(
                text = stringResource(R.string.translate_title),
                style = MaterialTheme.typography.titleMedium
            )
            Text(
                text = when (sourceLanguage) {
                    null -> stringResource(R.string.translate_provider)
                    else -> stringResource(R.string.translate_source_language, sourceLanguage)
                },
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
        }
    }
}

/**
 * Composable to display the translated text along with the original one below it
 */
@Composable
private fun TranslatedText(translation: String, original: String) {
    Column(
        modifier = Modifier
            .heightIn(max = dimensionResource(R.dimen.height_translation))
            .verticalScroll(rememberScrollState()),
        verticalArrangement = Arrangement.spacedBy(dimensionResource(R.dimen.margin_small))
    ) {
        Text(text = translation, style = MaterialTheme.typography.bodyLarge)

        HorizontalDivider()

        Text(
            text = stringResource(R.string.translate_original),
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
        Text(
            text = original,
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
    }
}

@Preview(showBackground = true)
@Composable
private fun TranslateScreenPreview() {
    PreviewTemplate {
        ScreenContent(
            text = "An unofficial FOSS client to Google Play",
            state = TextTranslationState.Translated(
                text = "Un client FOSS non officiel de Google Play",
                sourceLanguage = "en"
            )
        )
    }
}

@Preview(showBackground = true)
@Composable
private fun TranslateScreenPreviewLoading() {
    PreviewTemplate {
        ScreenContent(text = "An unofficial FOSS client to Google Play")
    }
}

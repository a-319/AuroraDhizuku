/*
 * SPDX-FileCopyrightText: 2026 The Calyx Institute
 * SPDX-License-Identifier: GPL-3.0-or-later
 */

package com.aurora.store.compose.ui.details.composable

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.dimensionResource
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.fromHtml
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.tooling.preview.PreviewParameter
import com.aurora.gplayapi.data.models.App
import com.aurora.store.R
import com.aurora.store.compose.composable.Header
import com.aurora.store.compose.preview.AppPreviewProvider
import com.aurora.store.compose.preview.PreviewTemplate
import com.aurora.store.data.model.Translation
import com.aurora.store.data.model.TranslationState

/**
 * Composable to display the description of an app alongside an action to machine translate it,
 * supposed to be used as a part of the Column with proper vertical arrangement spacing in the
 * MoreScreen.
 *
 * @param description Description of the app, as served by Google Play
 * @param translationState Current [TranslationState] of the description
 * @param onToggleTranslation Callback when the translate action is clicked
 */
@Composable
fun Description(
    description: String,
    translationState: TranslationState = TranslationState.Original,
    onToggleTranslation: () -> Unit = {}
) {
    val translation = (translationState as? TranslationState.Translated)?.translation

    Header(title = stringResource(R.string.details_description))
    Text(
        modifier = Modifier.padding(horizontal = dimensionResource(R.dimen.padding_medium)),
        text = when (translation) {
            // The translated text is plain, all markup is lost on the way through the translator
            null -> AnnotatedString.fromHtml(htmlString = description)
            else -> AnnotatedString(text = translation.text)
        },
        style = MaterialTheme.typography.bodyMedium
    )

    if (description.isNotBlank()) {
        TranslateAction(
            translationState = translationState,
            onToggleTranslation = onToggleTranslation
        )
    }
}

/**
 * Composable to toggle between the original and the translated description
 */
@Composable
private fun TranslateAction(translationState: TranslationState, onToggleTranslation: () -> Unit) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = dimensionResource(R.dimen.padding_small)),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(dimensionResource(R.dimen.margin_small))
    ) {
        TextButton(
            onClick = onToggleTranslation,
            enabled = translationState !is TranslationState.InProgress
        ) {
            Icon(
                modifier = Modifier.padding(end = dimensionResource(R.dimen.padding_xsmall)),
                painter = painterResource(R.drawable.ic_translate),
                contentDescription = null
            )
            Text(
                text = when (translationState) {
                    is TranslationState.InProgress -> {
                        stringResource(R.string.details_description_translating)
                    }

                    is TranslationState.Translated -> {
                        stringResource(R.string.details_description_original)
                    }

                    else -> stringResource(R.string.details_description_translate)
                }
            )
        }

        when (translationState) {
            is TranslationState.Translated -> {
                Text(
                    text = stringResource(R.string.details_description_translated),
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }

            is TranslationState.Failed -> {
                Text(
                    text = stringResource(R.string.details_description_translate_failed),
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.error
                )
            }

            else -> {}
        }
    }
}

@Preview(showBackground = true)
@Composable
private fun DescriptionPreview(@PreviewParameter(AppPreviewProvider::class) app: App) {
    PreviewTemplate {
        Column(
            verticalArrangement = Arrangement.spacedBy(dimensionResource(R.dimen.margin_medium))
        ) {
            Description(description = app.description)
        }
    }
}

@Preview(showBackground = true)
@Composable
private fun DescriptionTranslatedPreview(@PreviewParameter(AppPreviewProvider::class) app: App) {
    PreviewTemplate {
        Column(
            verticalArrangement = Arrangement.spacedBy(dimensionResource(R.dimen.margin_medium))
        ) {
            Description(
                description = app.description,
                translationState = TranslationState.Translated(
                    translation = Translation(text = app.description, sourceLanguage = "en")
                )
            )
        }
    }
}

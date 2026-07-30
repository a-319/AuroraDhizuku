/*
 * SPDX-License-Identifier: GPL-3.0-or-later
 */

package com.aurora.store.compose.composable

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.dimensionResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.tooling.preview.Preview
import com.aurora.store.R
import com.aurora.store.compose.preview.PreviewTemplate
import com.aurora.store.data.model.DeviceOwnerTarget

/**
 * Composable to display an app the device owner permission can be handed over to
 * @param modifier The modifier to be applied to the composable
 * @param target A [DeviceOwnerTarget] object to display details
 * @param onClick Callback when the hand over action is clicked
 */
@Composable
fun DeviceOwnerTargetListItem(
    modifier: Modifier = Modifier,
    target: DeviceOwnerTarget,
    onClick: () -> Unit = {}
) {
    Row(
        modifier = modifier
            .fillMaxWidth()
            .padding(dimensionResource(R.dimen.padding_small)),
        horizontalArrangement = Arrangement.spacedBy(dimensionResource(R.dimen.margin_small)),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Column(modifier = Modifier.weight(1F)) {
            Text(
                text = target.appLabel,
                style = MaterialTheme.typography.bodyLarge,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
            Text(
                text = target.packageName,
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.secondary,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
            if (target.adminLabel.isNotBlank() && target.adminLabel != target.appLabel) {
                Text(
                    text = target.adminLabel,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.secondary,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
            }
        }
        TextButton(onClick = onClick) {
            Text(text = stringResource(R.string.device_owner_transfer_action))
        }
    }
}

@Preview(showBackground = true)
@Composable
private fun DeviceOwnerTargetListItemPreview() {
    PreviewTemplate {
        DeviceOwnerTargetListItem(
            target = DeviceOwnerTarget(
                packageName = "com.rosan.dhizuku",
                className = "com.rosan.dhizuku.server.DhizukuDAReceiver",
                appLabel = "Dhizuku",
                adminLabel = "Dhizuku"
            )
        )
    }
}

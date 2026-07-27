/*
 * SPDX-License-Identifier: GPL-3.0-or-later
 */

package com.aurora.store.compose.ui.preferences.deviceowner

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.dimensionResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.tooling.preview.Preview
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.aurora.extensions.copyToClipBoard
import com.aurora.store.R
import com.aurora.store.compose.composable.DeviceOwnerTargetListItem
import com.aurora.store.compose.composable.Header
import com.aurora.store.compose.composable.Info
import com.aurora.store.compose.composable.TopAppBar
import com.aurora.store.compose.preview.PreviewTemplate
import com.aurora.store.data.model.DeviceOwnerMode
import com.aurora.store.data.model.DeviceOwnerState
import com.aurora.store.data.model.DeviceOwnerTarget
import com.aurora.store.viewmodel.preferences.DeviceOwnerViewModel

@Composable
fun DeviceOwnerScreen(onNavigateUp: () -> Unit, viewModel: DeviceOwnerViewModel = hiltViewModel()) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    val snackBarHostState = remember { SnackbarHostState() }

    LaunchedEffect(key1 = Unit) {
        viewModel.message.collect { message ->
            snackBarHostState.showSnackbar(message = message)
        }
    }

    var targetToTransfer: DeviceOwnerTarget? by rememberSaveable { mutableStateOf(null) }
    targetToTransfer?.let { target ->
        AlertDialog(
            onDismissRequest = { targetToTransfer = null },
            title = { Text(text = stringResource(R.string.device_owner_transfer_title)) },
            text = {
                Text(
                    text = stringResource(
                        R.string.device_owner_transfer_confirm,
                        target.appLabel
                    )
                )
            },
            confirmButton = {
                TextButton(
                    onClick = {
                        viewModel.transferOwnership(target)
                        targetToTransfer = null
                    }
                ) {
                    Text(text = stringResource(R.string.device_owner_transfer_action))
                }
            },
            dismissButton = {
                TextButton(onClick = { targetToTransfer = null }) {
                    Text(text = stringResource(R.string.action_cancel))
                }
            }
        )
    }

    var shouldConfirmRelease by rememberSaveable { mutableStateOf(false) }
    if (shouldConfirmRelease) {
        AlertDialog(
            onDismissRequest = { shouldConfirmRelease = false },
            title = { Text(text = stringResource(R.string.device_owner_release_action)) },
            text = { Text(text = stringResource(R.string.device_owner_release_desc)) },
            confirmButton = {
                TextButton(
                    onClick = {
                        viewModel.releaseOwnership()
                        shouldConfirmRelease = false
                    }
                ) {
                    Text(text = stringResource(R.string.device_owner_release_action))
                }
            },
            dismissButton = {
                TextButton(onClick = { shouldConfirmRelease = false }) {
                    Text(text = stringResource(R.string.action_cancel))
                }
            }
        )
    }

    ScreenContent(
        onNavigateUp = onNavigateUp,
        snackBarHostState = snackBarHostState,
        state = state,
        onTransfer = { target -> targetToTransfer = target },
        onRelease = { shouldConfirmRelease = true }
    )
}

@Composable
private fun ScreenContent(
    onNavigateUp: () -> Unit = {},
    snackBarHostState: SnackbarHostState = SnackbarHostState(),
    state: DeviceOwnerState = DeviceOwnerState(),
    onTransfer: (target: DeviceOwnerTarget) -> Unit = {},
    onRelease: () -> Unit = {}
) {
    val context = LocalContext.current
    val snackBarHostState = remember { snackBarHostState }

    Scaffold(
        snackbarHost = { SnackbarHost(hostState = snackBarHostState) },
        topBar = {
            TopAppBar(
                title = stringResource(R.string.pref_device_owner_title),
                onNavigateUp = onNavigateUp
            )
        }
    ) { paddingValues ->
        LazyColumn(
            modifier = Modifier
                .padding(paddingValues)
                .fillMaxSize()
                .padding(vertical = dimensionResource(R.dimen.padding_medium)),
            verticalArrangement = Arrangement.spacedBy(dimensionResource(R.dimen.margin_xsmall))
        ) {
            item(key = "status") {
                Header(title = stringResource(R.string.device_owner_status_title))
            }

            item(key = "status_details") {
                Info(
                    title = AnnotatedString(
                        text = when {
                            state.isDeviceOwner -> {
                                stringResource(R.string.device_owner_status_held)
                            }

                            else -> stringResource(R.string.device_owner_status_not_held)
                        }
                    ),
                    description = AnnotatedString(
                        text = when (state.mode) {
                            DeviceOwnerMode.PROTECTED -> {
                                stringResource(R.string.device_owner_mode_protected)
                            }

                            else -> stringResource(R.string.device_owner_mode_standard)
                        }
                    )
                )
            }

            if (state.isDeviceOwner) {
                item(key = "source") {
                    val label = state.sourceAppLabel
                    Info(
                        title = AnnotatedString(
                            text = when {
                                label != null -> stringResource(R.string.device_owner_source, label)
                                else -> stringResource(R.string.device_owner_source_unknown)
                            }
                        )
                    )
                }

                val protectedAppLabel = state.sourceAppLabel
                if (state.isUninstallBlocked && protectedAppLabel != null) {
                    item(key = "uninstall_blocked") {
                        Info(
                            title = AnnotatedString(
                                text = stringResource(
                                    R.string.device_owner_uninstall_blocked,
                                    protectedAppLabel
                                )
                            ),
                            description = AnnotatedString(
                                text = when {
                                    state.isUserControlLockedGlobally -> stringResource(
                                        R.string.device_owner_user_control_locked_global
                                    )

                                    else -> stringResource(
                                        R.string.device_owner_user_control_locked,
                                        protectedAppLabel
                                    )
                                }
                            )
                        )
                    }
                }

                // Protected mode without a known source keeps the ownership where it is
                val lockedToUnknown = state.isLockedToSource && protectedAppLabel == null

                item(key = "transfer") {
                    Header(
                        title = stringResource(R.string.device_owner_transfer_title),
                        subtitle = when {
                            lockedToUnknown -> {
                                stringResource(R.string.device_owner_transfer_locked_unknown)
                            }

                            state.isLockedToSource -> stringResource(
                                R.string.device_owner_transfer_locked_desc,
                                protectedAppLabel ?: state.sourcePackageName.orEmpty()
                            )

                            else -> stringResource(R.string.device_owner_transfer_desc)
                        }
                    )
                }

                if (state.targets.isEmpty()) {
                    item(key = "transfer_empty") {
                        Info(
                            title = AnnotatedString(
                                text = when {
                                    !state.isTransferSupported -> {
                                        stringResource(R.string.device_owner_transfer_unsupported)
                                    }

                                    lockedToUnknown -> {
                                        stringResource(
                                            R.string.device_owner_transfer_locked_unknown
                                        )
                                    }

                                    else -> stringResource(R.string.device_owner_transfer_empty)
                                }
                            )
                        )
                    }
                } else {
                    items(
                        items = state.targets,
                        key = { target -> target.componentName.flattenToShortString() }
                    ) { target ->
                        DeviceOwnerTargetListItem(
                            target = target,
                            onClick = { onTransfer(target) }
                        )
                    }
                }

                item(key = "release") {
                    TextButton(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = dimensionResource(R.dimen.padding_small)),
                        onClick = onRelease
                    ) {
                        Text(text = stringResource(R.string.device_owner_release_action))
                    }
                }
            } else {
                item(key = "receive") {
                    Header(
                        title = stringResource(R.string.device_owner_receive_title),
                        subtitle = stringResource(R.string.device_owner_receive_desc)
                    )
                }

                item(key = "receive_standard") {
                    Info(
                        title = AnnotatedString(text = state.standardAdmin),
                        description = AnnotatedString(
                            text = stringResource(R.string.device_owner_receive_standard)
                        ),
                        onClick = { context.copyToClipBoard(state.standardAdmin) }
                    )
                }

                item(key = "receive_protected") {
                    Info(
                        title = AnnotatedString(text = state.protectedAdmin),
                        description = AnnotatedString(
                            text = stringResource(R.string.device_owner_receive_protected)
                        ),
                        onClick = { context.copyToClipBoard(state.protectedAdmin) }
                    )
                }

                item(key = "setup") {
                    Header(
                        title = stringResource(R.string.device_owner_setup_title),
                        subtitle = stringResource(R.string.device_owner_setup_desc)
                    )
                }

                items(
                    items = listOf(state.standardAdmin, state.protectedAdmin),
                    key = { "setup_$it" }
                ) { admin ->
                    val command = "adb shell dpm set-device-owner $admin"
                    Text(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(
                                horizontal = dimensionResource(R.dimen.padding_small),
                                vertical = dimensionResource(R.dimen.padding_xxsmall)
                            ),
                        text = command,
                        style = MaterialTheme.typography.bodySmall
                    )
                }
            }
        }
    }
}

@Preview
@Composable
private fun DeviceOwnerScreenPreview() {
    PreviewTemplate {
        ScreenContent(
            state = DeviceOwnerState(
                isDeviceOwner = true,
                mode = DeviceOwnerMode.PROTECTED,
                sourcePackageName = "com.rosan.dhizuku",
                sourceAppLabel = "Dhizuku",
                isTransferSupported = true,
                isLockedToSource = true,
                isUninstallBlocked = true,
                targets = listOf(
                    DeviceOwnerTarget(
                        packageName = "com.rosan.dhizuku",
                        className = "com.rosan.dhizuku.server.DhizukuDAReceiver",
                        appLabel = "Dhizuku"
                    )
                )
            )
        )
    }
}

@Preview
@Composable
private fun DeviceOwnerScreenEmptyPreview() {
    PreviewTemplate {
        ScreenContent(
            state = DeviceOwnerState(
                standardAdmin = "lessevil.aurora/.data.receiver.DeviceOwnerReceiver",
                protectedAdmin = "lessevil.aurora/.data.receiver.ProtectedDeviceOwnerReceiver",
                isTransferSupported = true
            )
        )
    }
}

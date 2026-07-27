/*
 * SPDX-License-Identifier: GPL-3.0-or-later
 */

package com.aurora.store.data.model

/**
 * Current state of the device owner permission held (or not) by Aurora Store
 * @param isDeviceOwner Whether Aurora Store currently holds the device owner permission
 * @param mode Mode in which the permission is held
 * @param standardAdmin Flattened component name of the unrestricted device admin receiver
 * @param protectedAdmin Flattened component name of the protecting device admin receiver
 * @param sourcePackageName Package the ownership was received from, if it was received at all
 * @param sourceAppLabel Label of [sourcePackageName], falls back to the package name itself
 * @param isTransferSupported Whether this Android version can transfer the ownership at all
 * @param isLockedToSource Whether the ownership can only be handed back to [sourcePackageName]
 * @param canRelease Whether the permission can be given up instead of handed back
 * @param isUninstallBlocked Whether [sourcePackageName] is currently protected from uninstallation
 * @param isUserControlLockedGlobally Whether user control had to be taken away from every app
 * @param wasTransferRefused Whether a transfer was turned down since this was last looked at
 * @param targets Apps the ownership can currently be handed over to
 */
data class DeviceOwnerState(
    val isDeviceOwner: Boolean = false,
    val mode: DeviceOwnerMode = DeviceOwnerMode.STANDARD,
    val standardAdmin: String = "",
    val protectedAdmin: String = "",
    val sourcePackageName: String? = null,
    val sourceAppLabel: String? = null,
    val isTransferSupported: Boolean = false,
    val isLockedToSource: Boolean = false,
    val canRelease: Boolean = false,
    val isUninstallBlocked: Boolean = false,
    val isUserControlLockedGlobally: Boolean = false,
    val wasTransferRefused: Boolean = false,
    val targets: List<DeviceOwnerTarget> = emptyList()
)

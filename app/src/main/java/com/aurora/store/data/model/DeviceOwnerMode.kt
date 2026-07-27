/*
 * SPDX-License-Identifier: GPL-3.0-or-later
 */

package com.aurora.store.data.model

/**
 * Modes in which Aurora Store can hold the device owner permission.
 *
 * Each mode is backed by its own device admin receiver, so an app handing over the ownership
 * picks the mode by choosing which of our components it transfers the ownership to.
 */
enum class DeviceOwnerMode {

    /**
     * Ownership is held as-is, no policy is enforced on the app it was received from and the
     * ownership can be handed over to any app supporting the transfer.
     */
    STANDARD,

    /**
     * Ownership is held while the app it was received from is protected from being uninstalled
     * and from user control, and can only be handed back to that very app.
     */
    PROTECTED
}

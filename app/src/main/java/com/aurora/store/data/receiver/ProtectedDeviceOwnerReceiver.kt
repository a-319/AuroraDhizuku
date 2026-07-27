/*
 * SPDX-License-Identifier: GPL-3.0-or-later
 */

package com.aurora.store.data.receiver

/**
 * Device admin receiver holding the device owner permission in a protecting way.
 *
 * When another app hands the ownership over to this component, the app it came from is blocked
 * from being uninstalled and taken away from user control, and the ownership can only be handed
 * back to that very app. Everything else behaves like [DeviceOwnerReceiver].
 */
class ProtectedDeviceOwnerReceiver : DeviceOwnerReceiver()

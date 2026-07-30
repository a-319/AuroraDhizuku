/*
 * SPDX-License-Identifier: GPL-3.0-or-later
 */

package com.aurora.store.data.model

import android.content.ComponentName
import android.os.Parcelable
import kotlinx.parcelize.Parcelize

/**
 * An app which declares support for receiving the device owner permission through a transfer
 * @param packageName Package name of the app owning the device admin receiver
 * @param className Class name of the device admin receiver
 * @param appLabel Label of the app owning the device admin receiver
 * @param adminLabel Label of the device admin receiver itself, apps can ship more than one
 */
@Parcelize
data class DeviceOwnerTarget(
    val packageName: String,
    val className: String,
    val appLabel: String,
    val adminLabel: String = ""
) : Parcelable {

    val componentName: ComponentName
        get() = ComponentName(packageName, className)
}

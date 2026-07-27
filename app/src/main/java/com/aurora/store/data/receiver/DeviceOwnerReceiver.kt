/*
 * SPDX-License-Identifier: GPL-3.0-or-later
 */

package com.aurora.store.data.receiver

import android.app.admin.DeviceAdminReceiver
import android.content.Context
import android.content.Intent
import android.os.Build
import android.os.PersistableBundle
import android.util.Log
import androidx.annotation.RequiresApi
import com.aurora.store.util.DeviceOwnerManager

/**
 * Device admin receiver holding the device owner permission without enforcing any policy.
 *
 * Other apps supporting the ownership transfer can hand the permission over to this component,
 * and it can be handed over from here to any app which supports receiving it.
 */
open class DeviceOwnerReceiver : DeviceAdminReceiver() {

    private companion object {
        const val TAG = "DeviceOwnerReceiver"
    }

    override fun onEnabled(context: Context, intent: Intent) {
        super.onEnabled(context, intent)
        Log.i(TAG, "Enabled as device admin: ${getWho(context)}")
    }

    @RequiresApi(Build.VERSION_CODES.P)
    override fun onTransferOwnershipComplete(context: Context, bundle: PersistableBundle?) {
        super.onTransferOwnershipComplete(context, bundle)
        DeviceOwnerManager.onOwnershipReceived(context, getWho(context), bundle)
    }
}

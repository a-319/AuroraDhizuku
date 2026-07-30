/*
 * SPDX-License-Identifier: GPL-3.0-or-later
 */

package com.aurora.store.viewmodel.preferences

import android.content.Context
import android.util.Log
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.aurora.extensions.TAG
import com.aurora.store.R
import com.aurora.store.data.model.DeviceOwnerState
import com.aurora.store.data.model.DeviceOwnerTarget
import com.aurora.store.util.DeviceOwnerManager
import dagger.hilt.android.lifecycle.HiltViewModel
import dagger.hilt.android.qualifiers.ApplicationContext
import javax.inject.Inject
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

@HiltViewModel
class DeviceOwnerViewModel @Inject constructor(
    @ApplicationContext private val context: Context
) : ViewModel() {

    private val _state = MutableStateFlow(DeviceOwnerState())
    val state = _state.asStateFlow()

    private val _message = MutableSharedFlow<String>()
    val message = _message.asSharedFlow()

    init {
        refresh()
    }

    fun refresh() {
        viewModelScope.launch {
            _state.value = withContext(Dispatchers.IO) {
                // Remember whoever holds the permission, we cannot ask once it is handed over
                DeviceOwnerManager.cacheCurrentOwner(context)
                DeviceOwnerManager.getState(context).copy(
                    wasTransferRefused = DeviceOwnerManager.consumeRefusalNotice(context)
                )
            }
        }
    }

    fun transferOwnership(target: DeviceOwnerTarget) {
        viewModelScope.launch {
            val result = withContext(Dispatchers.IO) {
                runCatching { DeviceOwnerManager.transferOwnership(context, target) }
            }

            result.onSuccess {
                _message.emit(
                    context.getString(R.string.device_owner_transfer_success, target.appLabel)
                )
            }.onFailure { exception ->
                Log.e(TAG, "Failed to hand the device owner permission over", exception)
                _message.emit(
                    exception.localizedMessage
                        ?: context.getString(R.string.installer_status_failure)
                )
            }

            refresh()
        }
    }

    fun releaseOwnership() {
        viewModelScope.launch {
            val released = withContext(Dispatchers.IO) {
                runCatching { DeviceOwnerManager.releaseOwnership(context) }
                    .onFailure { Log.e(TAG, "Failed to give up the device owner permission", it) }
                    .getOrDefault(false)
            }

            if (!released) {
                val label = _state.value.sourceAppLabel.orEmpty()
                _message.emit(context.getString(R.string.device_owner_release_blocked, label))
            }

            refresh()
        }
    }
}

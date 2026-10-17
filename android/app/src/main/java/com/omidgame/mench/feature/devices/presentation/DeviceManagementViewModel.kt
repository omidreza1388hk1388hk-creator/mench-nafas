package com.omidgame.mench.feature.devices.presentation

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.omidgame.mench.feature.devices.domain.DevicesRepository
import com.omidgame.mench.feature.devices.domain.DevicesResult
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import javax.inject.Inject

@HiltViewModel
class DeviceManagementViewModel @Inject constructor(
    private val devicesRepository: DevicesRepository,
) : ViewModel() {

    private val _uiState = MutableStateFlow(DeviceManagementUiState())
    val uiState: StateFlow<DeviceManagementUiState> = _uiState.asStateFlow()

    init {
        loadDevices()
    }

    fun loadDevices() {
        viewModelScope.launch {
            _uiState.update { it.copy(isLoading = true, errorMessage = null) }
            when (val result = devicesRepository.listDevices()) {
                is DevicesResult.Success -> _uiState.update {
                    it.copy(isLoading = false, devices = result.value)
                }
                is DevicesResult.Failure -> _uiState.update {
                    it.copy(isLoading = false, errorMessage = result.message)
                }
            }
        }
    }

    fun revokeDevice(deviceId: String) {
        viewModelScope.launch {
            _uiState.update { it.copy(revokingDeviceId = deviceId, errorMessage = null) }
            when (val result = devicesRepository.revokeDevice(deviceId)) {
                is DevicesResult.Success -> _uiState.update { state ->
                    state.copy(
                        revokingDeviceId = null,
                        devices = state.devices.filterNot { it.id == deviceId },
                    )
                }
                is DevicesResult.Failure -> _uiState.update {
                    it.copy(revokingDeviceId = null, errorMessage = result.message)
                }
            }
        }
    }

    /** Logs out every device except this one, then reloads the list from the server (the source of truth). */
    fun revokeOtherDevices() {
        viewModelScope.launch {
            _uiState.update { it.copy(isRevokingOthers = true, errorMessage = null) }
            when (val result = devicesRepository.revokeOtherDevices()) {
                is DevicesResult.Success -> {
                    _uiState.update { it.copy(isRevokingOthers = false) }
                    loadDevices()
                }
                is DevicesResult.Failure -> _uiState.update { it.copy(isRevokingOthers = false, errorMessage = result.message) }
            }
        }
    }
}

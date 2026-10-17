package com.omidgame.mench.feature.devices.presentation

import com.omidgame.mench.feature.devices.domain.DeviceSession

data class DeviceManagementUiState(
    val isLoading: Boolean = true,
    val devices: List<DeviceSession> = emptyList(),
    val revokingDeviceId: String? = null,
    val isRevokingOthers: Boolean = false,
    val errorMessage: String? = null,
)

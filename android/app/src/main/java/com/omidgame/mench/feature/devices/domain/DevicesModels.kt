package com.omidgame.mench.feature.devices.domain

data class DeviceSession(
    val id: String,
    val deviceName: String,
    val platform: String,
    val lastSeenAtEpochMillis: Long?,
    val createdAtEpochMillis: Long,
    val isCurrent: Boolean = false,
)

sealed interface DevicesResult<out T> {
    data class Success<T>(val value: T) : DevicesResult<T>
    data class Failure(val message: String?) : DevicesResult<Nothing>
}

interface DevicesRepository {
    suspend fun listDevices(): DevicesResult<List<DeviceSession>>
    suspend fun revokeDevice(deviceId: String): DevicesResult<Unit>
    suspend fun revokeOtherDevices(): DevicesResult<Unit>
}

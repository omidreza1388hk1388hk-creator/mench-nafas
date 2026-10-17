package com.omidgame.mench.core.network

import retrofit2.http.DELETE
import retrofit2.http.GET
import retrofit2.http.Path

data class DeviceSessionResponse(
    val id: String,
    val deviceName: String,
    val platform: String,
    val lastSeenAt: String?,
    val createdAt: String,
    /** Settings Phase 1: true for the device that made the request (older servers omit it -> false). */
    val isCurrent: Boolean = false,
)

/**
 * Authenticated-only, same as [AuthenticatedAuthApi] — device
 * management (GET, DELETE below) makes no sense unauthenticated, unlike
 * [AuthApi]'s otp/refresh endpoints which by definition run before a
 * token exists.
 */
interface DevicesApi {
    @GET("devices")
    suspend fun listDevices(): List<DeviceSessionResponse>

    /** Logs out every device except this one (backend: DELETE /devices/others). */
    @DELETE("devices/others")
    suspend fun revokeOtherDevices()

    @DELETE("devices/{deviceId}")
    suspend fun revokeDevice(@Path("deviceId") deviceId: String)
}

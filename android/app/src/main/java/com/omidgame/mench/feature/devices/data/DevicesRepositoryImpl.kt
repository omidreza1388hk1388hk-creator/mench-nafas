package com.omidgame.mench.feature.devices.data

import com.omidgame.mench.core.network.DevicesApi
import com.omidgame.mench.feature.devices.domain.DeviceSession
import com.omidgame.mench.feature.devices.domain.DevicesRepository
import com.omidgame.mench.feature.devices.domain.DevicesResult
import retrofit2.HttpException
import java.io.IOException
import java.time.Instant
import javax.inject.Inject

class DevicesRepositoryImpl @Inject constructor(
    private val api: DevicesApi,
) : DevicesRepository {

    override suspend fun listDevices(): DevicesResult<List<DeviceSession>> = try {
        val devices = api.listDevices().map {
            DeviceSession(
                id = it.id,
                deviceName = it.deviceName,
                platform = it.platform,
                lastSeenAtEpochMillis = it.lastSeenAt?.let { ts -> Instant.parse(ts).toEpochMilli() },
                createdAtEpochMillis = Instant.parse(it.createdAt).toEpochMilli(),
                isCurrent = it.isCurrent,
            )
        }
        DevicesResult.Success(devices)
    } catch (e: IOException) {
        DevicesResult.Failure(e.message)
    } catch (e: HttpException) {
        DevicesResult.Failure(e.message)
    }

    override suspend fun revokeDevice(deviceId: String): DevicesResult<Unit> = try {
        api.revokeDevice(deviceId)
        DevicesResult.Success(Unit)
    } catch (e: IOException) {
        DevicesResult.Failure(e.message)
    } catch (e: HttpException) {
        DevicesResult.Failure(e.message)
    }

    override suspend fun revokeOtherDevices(): DevicesResult<Unit> = try {
        api.revokeOtherDevices()
        DevicesResult.Success(Unit)
    } catch (e: IOException) {
        DevicesResult.Failure(e.message)
    } catch (e: HttpException) {
        DevicesResult.Failure(e.message)
    }
}

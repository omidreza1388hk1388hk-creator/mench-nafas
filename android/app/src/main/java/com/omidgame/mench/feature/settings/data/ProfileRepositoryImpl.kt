package com.omidgame.mench.feature.settings.data

import com.omidgame.mench.core.database.UserDao
import com.omidgame.mench.core.database.UserEntity
import com.omidgame.mench.core.network.UpdateProfileBody
import com.omidgame.mench.core.network.UserProfileResponse
import com.omidgame.mench.core.network.UsersApi
import com.omidgame.mench.feature.settings.domain.Profile
import com.omidgame.mench.feature.settings.domain.ProfileRepository
import com.omidgame.mench.feature.settings.domain.ProfileUpdateFailureReason
import com.omidgame.mench.feature.settings.domain.ProfileUpdateResult
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import retrofit2.HttpException
import java.io.IOException
import javax.inject.Inject

class ProfileRepositoryImpl @Inject constructor(
    private val usersApi: UsersApi,
    private val userDao: UserDao,
) : ProfileRepository {

    /**
     * Local cache first — the account screen should render instantly with
     * whatever was last known (spec 13: offline-first) — refreshed from
     * the network in the background so a stale value never lingers longer
     * than one screen visit with connectivity.
     */
    override suspend fun getProfile(): Profile? = withContext(Dispatchers.IO) {
        val cached = userDao.getSelfOnce()?.toProfile()

        val fresh = try {
            usersApi.getMe().also { cacheResponse(it) }.toProfile()
        } catch (e: Exception) {
            null
        }

        fresh ?: cached
    }

    override suspend fun updateProfile(
        displayName: String?,
        username: String?,
        bio: String?,
    ): ProfileUpdateResult = withContext(Dispatchers.IO) {
        try {
            val response = usersApi.updateMe(UpdateProfileBody(displayName, username, bio))
            cacheResponse(response)
            ProfileUpdateResult.Success(response.toProfile())
        } catch (e: IOException) {
            ProfileUpdateResult.Failure(ProfileUpdateFailureReason.NETWORK_UNAVAILABLE)
        } catch (e: HttpException) {
            if (e.code() == 409) {
                ProfileUpdateResult.Failure(ProfileUpdateFailureReason.USERNAME_TAKEN)
            } else {
                ProfileUpdateResult.Failure(ProfileUpdateFailureReason.UNKNOWN)
            }
        }
    }

    private suspend fun cacheResponse(response: UserProfileResponse) {
        userDao.upsert(
            UserEntity(
                id = response.id,
                phoneE164 = response.phoneE164,
                displayName = response.displayName,
                username = response.username,
                avatarUrl = response.avatarUrl,
                bio = response.bio,
                updatedAtEpochMillis = System.currentTimeMillis(),
            ),
        )
    }

    private fun UserProfileResponse.toProfile() = Profile(
        phoneE164 = phoneE164,
        displayName = displayName,
        username = username,
        bio = bio,
    )

    private fun UserEntity.toProfile() = Profile(
        phoneE164 = phoneE164,
        displayName = displayName,
        username = username,
        bio = bio,
    )
}

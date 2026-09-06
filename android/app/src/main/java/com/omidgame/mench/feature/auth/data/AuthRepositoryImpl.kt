package com.omidgame.mench.feature.auth.data

import com.omidgame.mench.core.database.UserDao
import com.omidgame.mench.core.database.UserEntity
import com.omidgame.mench.core.network.AuthApi
import com.omidgame.mench.core.network.AuthenticatedAuthApi
import com.omidgame.mench.core.notifications.PushTokenRegistrar
import com.omidgame.mench.core.network.RefreshTokenBody
import com.omidgame.mench.core.network.RequestOtpBody
import com.omidgame.mench.core.network.VerifyOtpBody
import com.omidgame.mench.core.security.StoredTokens
import com.omidgame.mench.core.security.TokenStore
import com.omidgame.mench.feature.auth.domain.AuthFailureReason
import com.omidgame.mench.feature.auth.domain.AuthRepository
import com.omidgame.mench.feature.auth.domain.AuthResult
import com.omidgame.mench.feature.auth.domain.OtpChallenge
import com.omidgame.mench.feature.security.domain.AppLockRepository
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import retrofit2.HttpException
import java.io.IOException
import javax.inject.Inject

class AuthRepositoryImpl @Inject constructor(
    private val authApi: AuthApi,
    private val authenticatedAuthApi: AuthenticatedAuthApi,
    private val tokenStore: TokenStore,
    private val userDao: UserDao,
    private val appLockRepository: AppLockRepository,
    private val pushTokenRegistrar: PushTokenRegistrar,
) : AuthRepository {

    override suspend fun requestOtp(phoneE164: String): AuthResult<OtpChallenge> =
        withContext(Dispatchers.IO) {
            try {
                val response = authApi.requestOtp(RequestOtpBody(phoneE164))
                AuthResult.Success(OtpChallenge(response.challengeId, phoneE164))
            } catch (e: IOException) {
                AuthResult.Failure(AuthFailureReason.NETWORK_UNAVAILABLE)
            } catch (e: HttpException) {
                if (e.code() == 429) {
                    AuthResult.Failure(AuthFailureReason.RATE_LIMITED)
                } else {
                    AuthResult.Failure(AuthFailureReason.UNKNOWN)
                }
            }
        }

    override suspend fun verifyOtp(
        challenge: OtpChallenge,
        code: String,
        deviceName: String,
    ): AuthResult<Unit> = withContext(Dispatchers.IO) {
        try {
            val response = authApi.verifyOtp(
                VerifyOtpBody(
                    phone = challenge.phone,
                    code = code,
                    challengeId = challenge.challengeId,
                    deviceName = deviceName,
                ),
            )
            tokenStore.save(StoredTokens(response.accessToken, response.refreshToken, response.deviceId))
            userDao.upsert(
                UserEntity(
                    id = response.userId,
                    phoneE164 = challenge.phone,
                    displayName = null,
                    username = null,
                    avatarUrl = null,
                    updatedAtEpochMillis = System.currentTimeMillis(),
                ),
            )
            // Fire-and-forget (Phase 6): registration failure here must
            // never fail sign-in itself — see PushTokenRegistrar's own
            // doc comment for why every failure path inside it is
            // swallowed-and-logged rather than thrown.
            pushTokenRegistrar.registerCurrentToken()
            AuthResult.Success(Unit)
        } catch (e: IOException) {
            AuthResult.Failure(AuthFailureReason.NETWORK_UNAVAILABLE)
        } catch (e: HttpException) {
            when (e.code()) {
                401 -> AuthResult.Failure(AuthFailureReason.INVALID_OR_EXPIRED_CODE)
                429 -> AuthResult.Failure(AuthFailureReason.RATE_LIMITED)
                else -> AuthResult.Failure(AuthFailureReason.UNKNOWN)
            }
        }
    }

    override suspend fun isSignedIn(): Boolean = tokenStore.read() != null

    override suspend fun logout() = withContext(Dispatchers.IO) {
        val tokens = tokenStore.read()
        if (tokens != null) {
            try {
                authApi.logout(RefreshTokenBody(tokens.refreshToken))
            } catch (e: Exception) {
                // Best-effort server-side revocation. Local sign-out must
                // proceed regardless — a flaky network shouldn't trap the
                // user in a "signed in" state they can't get out of.
            }
        }
        tokenStore.clear()
        userDao.clear()
        // App lock protects access to this account's data specifically —
        // once the account is signed out there is nothing left for it to
        // guard, and leaving a stale PIN behind would just prompt a
        // logged-out user for a PIN that no longer protects anything.
        appLockRepository.disableAppLock()
    }

    override suspend fun logoutAllDevices() = withContext(Dispatchers.IO) {
        try {
            authenticatedAuthApi.logoutAll()
        } catch (e: Exception) {
            // Same best-effort reasoning as logout(): the local session is
            // cleared unconditionally below regardless of whether the
            // server call succeeded, so the user is never stuck appearing
            // "signed in" locally after asking to sign out everywhere.
        }
        tokenStore.clear()
        userDao.clear()
        appLockRepository.disableAppLock()
    }
}

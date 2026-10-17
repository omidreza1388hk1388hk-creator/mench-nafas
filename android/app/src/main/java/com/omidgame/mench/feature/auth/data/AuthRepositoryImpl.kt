package com.omidgame.mench.feature.auth.data

import com.omidgame.mench.core.database.UserDao
import com.omidgame.mench.core.database.UserEntity
import com.omidgame.mench.core.network.AuthApi
import com.omidgame.mench.core.network.AuthenticatedAuthApi
import com.omidgame.mench.core.notifications.PushTokenRegistrar
import com.omidgame.mench.core.network.RefreshTokenBody
import com.omidgame.mench.core.network.RequestOtpBody
import com.omidgame.mench.core.network.VerifyOtpBody
import com.omidgame.mench.core.network.VerifyTwoStepBody
import com.omidgame.mench.core.security.StoredTokens
import com.omidgame.mench.core.security.TokenStore
import com.omidgame.mench.feature.auth.domain.AuthFailureReason
import com.omidgame.mench.feature.auth.domain.AuthRepository
import com.omidgame.mench.feature.auth.domain.AuthResult
import com.omidgame.mench.feature.auth.domain.OtpChallenge
import com.omidgame.mench.feature.auth.domain.TwoStepChallenge
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
                when (e.code()) {
                    429 -> AuthResult.Failure(AuthFailureReason.RATE_LIMITED)
                    400 -> AuthResult.Failure(AuthFailureReason.INVALID_PHONE)
                    else -> AuthResult.Failure(AuthFailureReason.UNKNOWN)
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
            val twoStepTicket = response.ticket
            if (response.twoStepRequired == true && twoStepTicket != null) {
                return@withContext AuthResult.TwoStepRequired(TwoStepChallenge(twoStepTicket, response.hint, challenge.phone))
            }
            val accessToken = response.accessToken
            val refreshToken = response.refreshToken
            val userId = response.userId
            val deviceId = response.deviceId
            if (accessToken == null || refreshToken == null || userId == null || deviceId == null) {
                return@withContext AuthResult.Failure(AuthFailureReason.UNKNOWN)
            }
            finishSignIn(accessToken, refreshToken, userId, deviceId, challenge.phone)
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

    override suspend fun verifyTwoStep(challenge: TwoStepChallenge, password: String): AuthResult<Unit> =
        withContext(Dispatchers.IO) {
            try {
                val response = authApi.verifyTwoStep(VerifyTwoStepBody(challenge.ticket, password))
                finishSignIn(response.accessToken, response.refreshToken, response.userId, response.deviceId, challenge.phone)
                AuthResult.Success(Unit)
            } catch (e: IOException) {
                AuthResult.Failure(AuthFailureReason.NETWORK_UNAVAILABLE)
            } catch (e: HttpException) {
                when (e.code()) {
                    401 -> AuthResult.Failure(AuthFailureReason.WRONG_PASSWORD)
                    429 -> AuthResult.Failure(AuthFailureReason.RATE_LIMITED)
                    else -> AuthResult.Failure(AuthFailureReason.UNKNOWN)
                }
            }
        }

    /** Shared tail of both sign-in paths (plain SMS code, and SMS code + two-step password). */
    private suspend fun finishSignIn(accessToken: String, refreshToken: String, userId: String, deviceId: String, phone: String) {
        tokenStore.save(StoredTokens(accessToken, refreshToken, deviceId))
        userDao.upsert(
            UserEntity(
                id = userId,
                phoneE164 = phone,
                displayName = null,
                username = null,
                avatarUrl = null,
                updatedAtEpochMillis = System.currentTimeMillis(),
            ),
        )
        // Fire-and-forget: a push-registration failure must never fail sign-in (see PushTokenRegistrar).
        pushTokenRegistrar.registerCurrentToken()
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

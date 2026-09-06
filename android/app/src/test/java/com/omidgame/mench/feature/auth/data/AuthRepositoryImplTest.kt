package com.omidgame.mench.feature.auth.data

import com.google.common.truth.Truth.assertThat
import com.omidgame.mench.core.database.UserDao
import com.omidgame.mench.core.database.UserEntity
import com.omidgame.mench.core.network.AuthApi
import com.omidgame.mench.core.network.AuthTokensResponse
import com.omidgame.mench.core.network.RequestOtpBody
import com.omidgame.mench.core.network.RequestOtpResponse
import com.omidgame.mench.core.network.VerifyOtpBody
import com.omidgame.mench.core.security.StoredTokens
import com.omidgame.mench.core.security.TokenStore
import com.omidgame.mench.feature.auth.domain.AuthFailureReason
import com.omidgame.mench.feature.auth.domain.AuthResult
import com.omidgame.mench.feature.auth.domain.OtpChallenge
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.match
import io.mockk.mockk
import kotlinx.coroutines.test.runTest
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.ResponseBody.Companion.toResponseBody
import org.junit.Before
import org.junit.Test
import retrofit2.HttpException
import retrofit2.Response
import java.io.IOException

class AuthRepositoryImplTest {

    private lateinit var authApi: AuthApi
    private lateinit var tokenStore: TokenStore
    private lateinit var userDao: UserDao
    private lateinit var repository: AuthRepositoryImpl

    @Before
    fun setUp() {
        authApi = mockk()
        tokenStore = mockk(relaxUnitFun = true)
        userDao = mockk(relaxUnitFun = true)
        repository = AuthRepositoryImpl(authApi, tokenStore, userDao)
    }

    @Test
    fun `requestOtp returns Success with challenge id on 200`() = runTest {
        coEvery { authApi.requestOtp(any()) } returns RequestOtpResponse(challengeId = "abc-123")

        val result = repository.requestOtp("+15551234567")

        assertThat(result).isInstanceOf(AuthResult.Success::class.java)
        val challenge = (result as AuthResult.Success).value
        assertThat(challenge.challengeId).isEqualTo("abc-123")
        assertThat(challenge.phone).isEqualTo("+15551234567")
    }

    @Test
    fun `requestOtp maps IOException to NETWORK_UNAVAILABLE`() = runTest {
        coEvery { authApi.requestOtp(any()) } throws IOException("no connection")

        val result = repository.requestOtp("+15551234567")

        assertThat(result).isEqualTo(AuthResult.Failure(AuthFailureReason.NETWORK_UNAVAILABLE))
    }

    @Test
    fun `requestOtp maps HTTP 429 to RATE_LIMITED`() = runTest {
        coEvery { authApi.requestOtp(any()) } throws httpException(429)

        val result = repository.requestOtp("+15551234567")

        assertThat(result).isEqualTo(AuthResult.Failure(AuthFailureReason.RATE_LIMITED))
    }

    @Test
    fun `verifyOtp on success stores tokens and caches user, never logs the code`() = runTest {
        val challenge = OtpChallenge(challengeId = "abc-123", phone = "+15551234567")
        coEvery { authApi.verifyOtp(any()) } returns AuthTokensResponse(
            accessToken = "access-xyz",
            refreshToken = "refresh-xyz",
            userId = "user-1",
            deviceId = "device-1",
        )

        val result = repository.verifyOtp(challenge, code = "54321", deviceName = "Pixel 9")

        assertThat(result).isEqualTo(AuthResult.Success(Unit))
        coVerify { tokenStore.save(StoredTokens("access-xyz", "refresh-xyz")) }
        coVerify { userDao.upsert(match<UserEntity> { it.id == "user-1" && it.phoneE164 == challenge.phone }) }
    }

    @Test
    fun `verifyOtp maps HTTP 401 to INVALID_OR_EXPIRED_CODE and does not persist anything`() = runTest {
        val challenge = OtpChallenge(challengeId = "abc-123", phone = "+15551234567")
        coEvery { authApi.verifyOtp(any()) } throws httpException(401)

        val result = repository.verifyOtp(challenge, code = "00000", deviceName = "Pixel 9")

        assertThat(result).isEqualTo(AuthResult.Failure(AuthFailureReason.INVALID_OR_EXPIRED_CODE))
        coVerify(exactly = 0) { tokenStore.save(any()) }
        coVerify(exactly = 0) { userDao.upsert(any()) }
    }

    @Test
    fun `logout clears local tokens even if the server call fails`() = runTest {
        coEvery { tokenStore.read() } returns StoredTokens("access", "refresh")
        coEvery { authApi.logout(any()) } throws IOException("offline")

        repository.logout()

        coVerify { tokenStore.clear() }
        coVerify { userDao.clear() }
    }

    private fun httpException(code: Int): HttpException {
        val body = "{}".toResponseBody("application/json".toMediaType())
        return HttpException(Response.error<Any>(code, body))
    }
}

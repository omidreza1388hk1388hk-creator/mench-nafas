package com.omidgame.mench.core.network

import retrofit2.http.Body
import retrofit2.http.GET
import retrofit2.http.POST

data class RequestOtpBody(val phone: String)
data class RequestOtpResponse(val challengeId: String)

data class VerifyOtpBody(
    val phone: String,
    val code: String,
    val challengeId: String,
    val deviceName: String,
)
data class AuthTokensResponse(
    val accessToken: String,
    val refreshToken: String,
    val userId: String,
    val deviceId: String,
)

/** otp/verify answers with tokens, or (account has two-step verification) with twoStepRequired + a ticket. */
data class VerifyOtpResponse(
    val accessToken: String? = null,
    val refreshToken: String? = null,
    val userId: String? = null,
    val deviceId: String? = null,
    val twoStepRequired: Boolean? = null,
    val ticket: String? = null,
    val hint: String? = null,
)

data class VerifyTwoStepBody(val ticket: String, val password: String)

data class RefreshTokenBody(val refreshToken: String)
data class RefreshTokenResponse(val accessToken: String, val refreshToken: String)

data class PingResponse(val ok: Boolean, val serverTime: String)

interface AuthApi {
    @POST("auth/otp/request")
    suspend fun requestOtp(@Body body: RequestOtpBody): RequestOtpResponse

    @POST("auth/otp/verify")
    suspend fun verifyOtp(@Body body: VerifyOtpBody): VerifyOtpResponse

    @POST("auth/two-step/verify")
    suspend fun verifyTwoStep(@Body body: VerifyTwoStepBody): AuthTokensResponse

    @POST("auth/token/refresh")
    suspend fun refreshToken(@Body body: RefreshTokenBody): RefreshTokenResponse

    @POST("auth/logout")
    suspend fun logout(@Body body: RefreshTokenBody)

    /** Unauthenticated liveness probe — see DiagnosticsController.ping on the backend. Used as the "Internet"/"Server" check before anything auth-dependent is attempted. */
    @GET("diagnostics/ping")
    suspend fun ping(): PingResponse
}

/**
 * A second, small Retrofit interface for the one auth endpoint that
 * requires a bearer access token (logout-all is guarded by JwtAuthGuard
 * server-side) — kept separate from [AuthApi] because that interface's
 * Retrofit instance is deliberately built on the *unauthenticated* OkHttp
 * client (otp/request, otp/verify, token/refresh, and plain logout all
 * either predate having a token or authenticate via the refresh token in
 * the body instead of a header).
 */
interface AuthenticatedAuthApi {
    @POST("auth/logout-all")
    suspend fun logoutAll()
}

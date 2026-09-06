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

data class RefreshTokenBody(val refreshToken: String)
data class RefreshTokenResponse(val accessToken: String, val refreshToken: String)

data class PingResponse(val ok: Boolean, val serverTime: String)

interface AuthApi {
    @POST("auth/otp/request")
    suspend fun requestOtp(@Body body: RequestOtpBody): RequestOtpResponse

    @POST("auth/otp/verify")
    suspend fun verifyOtp(@Body body: VerifyOtpBody): AuthTokensResponse

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

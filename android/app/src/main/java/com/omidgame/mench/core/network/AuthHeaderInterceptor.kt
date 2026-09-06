package com.omidgame.mench.core.network

import com.omidgame.mench.core.security.TokenStore
import kotlinx.coroutines.runBlocking
import okhttp3.Interceptor
import okhttp3.Response
import javax.inject.Inject

/** Attaches the current access token to every request on the authenticated client. */
class AuthHeaderInterceptor @Inject constructor(
    private val tokenStore: TokenStore,
) : Interceptor {
    override fun intercept(chain: Interceptor.Chain): Response {
        val tokens = runBlocking { tokenStore.read() }
        val request = chain.request().newBuilder().apply {
            if (tokens != null) {
                header("Authorization", "Bearer ${tokens.accessToken}")
            }
        }.build()
        return chain.proceed(request)
    }
}

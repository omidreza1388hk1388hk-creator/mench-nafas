package com.omidgame.mench.core.network

import com.omidgame.mench.core.security.StoredTokens
import com.omidgame.mench.core.security.TokenStore
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import okhttp3.Authenticator
import okhttp3.Request
import okhttp3.Response
import okhttp3.Route
import javax.inject.Inject
import javax.inject.Singleton

/**
 * OkHttp Authenticator: on a 401 response, attempts exactly one token
 * refresh and retries the original request with the new access token.
 * A Mutex serializes concurrent refresh attempts so N simultaneous 401s
 * trigger one refresh call, not N races against the backend's single-use
 * refresh-token rotation (which would otherwise invalidate each other).
 */
@Singleton
class TokenAuthenticator @Inject constructor(
    private val tokenStore: TokenStore,
    private val authApiProvider: javax.inject.Provider<AuthApi>,
) : Authenticator {

    private val refreshMutex = Mutex()

    override fun authenticate(route: Route?, response: Response): Request? {
        // Never loop forever: if we already retried once for this chain, give up.
        if (responseCount(response) >= 2) return null

        val refreshed = runBlocking {
            refreshMutex.withLock {
                val current = tokenStore.read() ?: return@withLock null
                // Another thread may have already refreshed while we waited
                // for the lock; if the token used on the failing request is
                // stale compared to what's stored now, just reuse the fresh one.
                val failedAuthHeader = response.request.header("Authorization")
                if (failedAuthHeader != "Bearer ${current.accessToken}") {
                    return@withLock current
                }

                try {
                    val result = authApiProvider.get().refreshToken(RefreshTokenBody(current.refreshToken))
                    // Phase 6: deviceId is preserved from what's already
                    // stored, not part of the refresh response — see
                    // TokenStore.updateAccessAndRefreshTokens's doc comment.
                    tokenStore.updateAccessAndRefreshTokens(result.accessToken, result.refreshToken)
                    StoredTokens(result.accessToken, result.refreshToken, current.deviceId)
                } catch (e: Exception) {
                    // Refresh token itself invalid/expired/revoked. Clear local
                    // tokens so the app routes the user back to the auth flow
                    // instead of retrying forever against a dead session.
                    tokenStore.clear()
                    null
                }
            }
        } ?: return null

        return response.request.newBuilder()
            .header("Authorization", "Bearer ${refreshed.accessToken}")
            .build()
    }

    private fun responseCount(response: Response): Int {
        var result = 1
        var prior = response.priorResponse
        while (prior != null) {
            result++
            prior = prior.priorResponse
        }
        return result
    }
}

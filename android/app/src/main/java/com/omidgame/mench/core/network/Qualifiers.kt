package com.omidgame.mench.core.network

import javax.inject.Qualifier

/** OkHttpClient/Retrofit with no auth header — used only for the OTP/refresh/logout calls themselves. */
@Qualifier
@Retention(AnnotationRetention.BINARY)
annotation class UnauthenticatedClient

/** OkHttpClient/Retrofit that attaches the bearer token and auto-refreshes on 401 — used by Phase 2+ feature APIs. */
@Qualifier
@Retention(AnnotationRetention.BINARY)
annotation class AuthenticatedClient

/**
 * OkHttpClient tuned for a long-lived WebSocket, not REST calls: no read
 * timeout (an idle chat connection with nothing to say for minutes is
 * normal, not a hang) and a ping interval so OkHttp keeps the connection
 * alive through NATs/proxies that silently drop idle TCP connections.
 * Sharing the REST client's 15s readTimeout here would have closed the
 * socket almost immediately on any quiet conversation.
 */
@Qualifier
@Retention(AnnotationRetention.BINARY)
annotation class RealtimeSocketClient

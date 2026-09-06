package com.omidgame.mench.feature.diagnostics.data

import com.omidgame.mench.core.database.OutboxDao
import com.omidgame.mench.core.database.OutboxState
import com.omidgame.mench.core.media.AttachmentCache
import com.omidgame.mench.core.network.AuthApi
import com.omidgame.mench.core.network.ChatApi
import com.omidgame.mench.core.network.realtime.ConnectionState
import com.omidgame.mench.core.network.realtime.RealtimeClient
import com.omidgame.mench.core.security.TokenStore
import com.omidgame.mench.feature.diagnostics.domain.CheckStatus
import com.omidgame.mench.feature.diagnostics.domain.DiagnosticCheck
import com.omidgame.mench.feature.diagnostics.domain.DiagnosticsReport
import com.omidgame.mench.feature.diagnostics.domain.DiagnosticsRepository
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.IOException
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class DiagnosticsRepositoryImpl @Inject constructor(
    private val authApi: AuthApi,
    private val chatApi: ChatApi,
    private val tokenStore: TokenStore,
    private val realtimeClient: RealtimeClient,
    private val outboxDao: OutboxDao,
    private val attachmentCache: AttachmentCache,
) : DiagnosticsRepository {

    override suspend fun runDiagnostics(): DiagnosticsReport = withContext(Dispatchers.IO) {
        val checks = mutableListOf<DiagnosticCheck>()

        // 1. Internet — a bare, unauthenticated round trip. If this
        // itself throws IOException, every check after it that needs the
        // network is skipped rather than each independently timing out.
        val internetReachable = try {
            authApi.ping()
            checks += DiagnosticCheck("internet", CheckStatus.PASS, "Reachable")
            true
        } catch (e: IOException) {
            checks += DiagnosticCheck("internet", CheckStatus.FAIL, "No network connection")
            false
        } catch (e: Exception) {
            checks += DiagnosticCheck("internet", CheckStatus.WARNING, "Unexpected response")
            true // server answered, just not cleanly — worth still trying auth-dependent checks
        }

        // 2. Authentication — do we even have stored tokens locally.
        val tokens = tokenStore.read()
        checks += if (tokens != null) {
            DiagnosticCheck("authentication", CheckStatus.PASS, "Signed in")
        } else {
            DiagnosticCheck("authentication", CheckStatus.FAIL, "Not signed in")
        }

        // 3. Server + its own dependency checks (database/redis/storage),
        // only attempted if we have both a network path and a token.
        if (internetReachable && tokens != null) {
            try {
                val report = chatApi.runDiagnostics()
                checks += DiagnosticCheck("server", CheckStatus.PASS, "Reachable and authenticated")
                report.checks.forEach { serverCheck ->
                    checks += DiagnosticCheck(
                        name = "server.${serverCheck.name}",
                        status = serverCheck.status.toCheckStatus(),
                        detail = serverCheck.detail,
                    )
                }
            } catch (e: IOException) {
                checks += DiagnosticCheck("server", CheckStatus.FAIL, "Could not reach the server")
            } catch (e: Exception) {
                checks += DiagnosticCheck("server", CheckStatus.FAIL, "Session expired or invalid")
            }
        } else {
            checks += DiagnosticCheck("server", CheckStatus.WARNING, "Skipped — no internet or not signed in")
        }

        // 4. WebSocket — read whatever RealtimeClient's replay cache
        // currently holds rather than opening a new connection; this
        // reports the connection the app is actually using right now.
        val connectionState = realtimeClient.connectionState.replayCache.firstOrNull()
        checks += when (connectionState) {
            is ConnectionState.Connected -> DiagnosticCheck("websocket", CheckStatus.PASS, "Connected")
            is ConnectionState.Connecting -> DiagnosticCheck("websocket", CheckStatus.WARNING, "Connecting")
            is ConnectionState.Disconnected, null -> DiagnosticCheck("websocket", CheckStatus.FAIL, "Disconnected")
        }

        // 5. Sync — a non-zero FAILED-state Outbox count means some local
        // change (a send, edit, reaction...) has been silently stuck
        // rather than actually reaching the server.
        val failedCount = outboxDao.countByState(OutboxState.FAILED.name)
        checks += if (failedCount == 0) {
            DiagnosticCheck("sync", CheckStatus.PASS, "All local changes synced")
        } else {
            DiagnosticCheck("sync", CheckStatus.WARNING, "$failedCount change(s) failed to sync")
        }

        // 6. Storage — local attachment cache directory is actually
        // writable (a full disk or a stripped storage permission would
        // otherwise only surface as a mysterious failed attachment send).
        checks += if (attachmentCache.verifyWritable()) {
            DiagnosticCheck("storage", CheckStatus.PASS, "Local cache writable")
        } else {
            DiagnosticCheck("storage", CheckStatus.FAIL, "Local cache is not writable")
        }

        val overall = when {
            checks.any { it.status == CheckStatus.FAIL } -> CheckStatus.FAIL
            checks.any { it.status == CheckStatus.WARNING } -> CheckStatus.WARNING
            else -> CheckStatus.PASS
        }

        DiagnosticsReport(overall = overall, checks = checks)
    }

    private fun String.toCheckStatus(): CheckStatus = when (this) {
        "pass" -> CheckStatus.PASS
        "warning" -> CheckStatus.WARNING
        else -> CheckStatus.FAIL
    }
}

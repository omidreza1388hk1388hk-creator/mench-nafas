package com.omidgame.mench.feature.diagnostics.domain

enum class CheckStatus {
    PASS,
    WARNING,
    FAIL,
}

data class DiagnosticCheck(
    val name: String,
    val status: CheckStatus,
    val detail: String,
)

data class DiagnosticsReport(
    val overall: CheckStatus,
    val checks: List<DiagnosticCheck>,
)

interface DiagnosticsRepository {
    /**
     * Every check is a real probe against the actual dependency (network
     * call, WorkManager/Room state, filesystem write) — never a hardcoded
     * PASS. Runs client-side checks first (so a "no internet" state
     * degrades gracefully instead of hanging on a network call) then, only
     * if the server is reachable, folds in the backend's own
     * database/redis/storage checks from GET /diagnostics/full.
     */
    suspend fun runDiagnostics(): DiagnosticsReport
}

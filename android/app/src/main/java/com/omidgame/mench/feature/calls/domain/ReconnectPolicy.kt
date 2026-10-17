package com.omidgame.mench.feature.calls.domain

/**
 * Phase 2B — what to do while a call's connection is down. Only the caller restarts ICE (one side
 * driving the renegotiation avoids both sides sending crossing offers); the callee waits and
 * answers. After [MAX_ATTEMPTS] restarts, or the overall deadline, the call is ended.
 */
object ReconnectPolicy {
    const val GRACE_MS = 4_000L          // brief blips often heal on their own
    const val RETRY_INTERVAL_MS = 8_000L
    const val MAX_ATTEMPTS = 3
    const val GIVE_UP_MS = GRACE_MS + MAX_ATTEMPTS * RETRY_INTERVAL_MS

    enum class Action { WAIT, RESTART_ICE, GIVE_UP }

    fun nextAction(msSinceDisconnected: Long, attemptsMade: Int, isCaller: Boolean): Action {
        if (msSinceDisconnected >= GIVE_UP_MS) return Action.GIVE_UP
        val nextAttemptAt = GRACE_MS + attemptsMade * RETRY_INTERVAL_MS
        return if (isCaller && attemptsMade < MAX_ATTEMPTS && msSinceDisconnected >= nextAttemptAt) Action.RESTART_ICE else Action.WAIT
    }
}

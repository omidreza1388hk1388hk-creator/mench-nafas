package com.omidgame.mench.feature.calls.domain

import java.time.Instant

enum class CallFilter { ALL, MISSED, VOICE, VIDEO }

/** Phase 2B — call-log helpers: filter chips and the missed-call badge. Pure, unit-tested. */
object CallHistoryLogic {
    /** A call somebody made to me that I did not pick up (the badge counts only these). */
    fun isMissedIncoming(e: CallHistoryEntry): Boolean = !e.wasOutgoing && e.status == CallStatus.MISSED

    /** Rows that are drawn in red: missed, or declined on my side (unchanged from the original dialog). */
    fun isHighlightedAsMissed(e: CallHistoryEntry): Boolean = e.status == CallStatus.MISSED || (e.status == CallStatus.DECLINED && !e.wasOutgoing)

    fun filter(entries: List<CallHistoryEntry>, filter: CallFilter): List<CallHistoryEntry> = when (filter) {
        CallFilter.ALL -> entries
        CallFilter.MISSED -> entries.filter { isMissedIncoming(it) }
        CallFilter.VOICE -> entries.filter { it.callType == CallType.VOICE }
        CallFilter.VIDEO -> entries.filter { it.callType == CallType.VIDEO }
    }

    /** Missed incoming calls that started after [lastSeenEpochMillis] (the moment the user last opened the call log). */
    fun missedSince(entries: List<CallHistoryEntry>, lastSeenEpochMillis: Long): Int =
        entries.count { isMissedIncoming(it) && (startedAtMillis(it) ?: 0L) > lastSeenEpochMillis }

    fun startedAtMillis(e: CallHistoryEntry): Long? = runCatching { Instant.parse(e.startedAtIso).toEpochMilli() }.getOrNull()
}

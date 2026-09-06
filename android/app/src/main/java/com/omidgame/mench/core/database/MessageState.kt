package com.omidgame.mench.core.database

/**
 * Client-local message lifecycle. Only SENT and READ have any
 * server-observable meaning (see docs/ARCHITECTURE.md's Phase 2 notes on
 * why DELIVERED isn't tracked yet) — PENDING/SENDING/FAILED exist purely
 * to drive the composer/bubble UI while a send is in flight or retrying.
 */
enum class MessageState {
    PENDING,
    SENDING,
    SENT,
    READ,
    FAILED,
}

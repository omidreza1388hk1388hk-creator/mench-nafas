package com.omidgame.mench.feature.settings.domain

/** Facts about the device/account the checkup scores. Collected by the UI layer; this file stays pure. */
data class SecurityFacts(
    val deviceScreenLockSet: Boolean,
    val appLockEnabled: Boolean,
    val twoStepEnabled: Boolean,
    val backupExists: Boolean,
    val devicesReviewed: Boolean,
    val mainChatE2eeEnabled: Boolean,
    /** Whether the user uses end-to-end encryption anywhere (affects the backup item's urgency only). */
    val anyE2eeChat: Boolean = false,
)

enum class SecurityCheckItem { SCREEN_LOCK, APP_LOCK, TWO_STEP, BACKUP, DEVICES, E2EE_MAIN_CHAT }

data class SecurityCheckResult(
    val done: Set<SecurityCheckItem>,
    val missing: List<SecurityCheckItem>,
) {
    val total: Int get() = SecurityCheckItem.entries.size
    val score: Int get() = done.size
    val isPerfect: Boolean get() = missing.isEmpty()

    /** Backup is urgent only when encrypted chats exist (lost key = lost messages). */
    fun needsAttention(facts: SecurityFacts): Boolean =
        missing.isNotEmpty() && (SecurityCheckItem.BACKUP in missing && facts.anyE2eeChat ||
            SecurityCheckItem.SCREEN_LOCK in missing || SecurityCheckItem.APP_LOCK in missing)
}

object SecurityCheckup {
    /** Order of [missing] is the order the UI should present the fixes: most impactful first. */
    private val order = listOf(
        SecurityCheckItem.SCREEN_LOCK,
        SecurityCheckItem.APP_LOCK,
        SecurityCheckItem.TWO_STEP,
        SecurityCheckItem.BACKUP,
        SecurityCheckItem.E2EE_MAIN_CHAT,
        SecurityCheckItem.DEVICES,
    )

    fun evaluate(f: SecurityFacts): SecurityCheckResult {
        val passed = buildSet {
            if (f.deviceScreenLockSet) add(SecurityCheckItem.SCREEN_LOCK)
            if (f.appLockEnabled) add(SecurityCheckItem.APP_LOCK)
            if (f.twoStepEnabled) add(SecurityCheckItem.TWO_STEP)
            if (f.backupExists) add(SecurityCheckItem.BACKUP)
            if (f.devicesReviewed) add(SecurityCheckItem.DEVICES)
            if (f.mainChatE2eeEnabled) add(SecurityCheckItem.E2EE_MAIN_CHAT)
        }
        return SecurityCheckResult(done = passed, missing = order.filter { it !in passed })
    }
}

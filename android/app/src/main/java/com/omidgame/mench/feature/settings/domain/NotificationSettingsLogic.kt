package com.omidgame.mench.feature.settings.domain

/**
 * Notification types the user can switch off inside the app. Only types that the app really posts a
 * notification for are listed (a switch that controls nothing would be misleading).
 * Sound, vibration and priority per type are changed in the system's channel settings (Android makes channel
 * settings immutable for apps), reachable from the same screen.
 */
enum class NotificationType(val storageKey: String) {
    MESSAGES("messages"),
    AFFECTION("affection"), // "I miss you", hugs, kisses
    LETTERS("letters"), // letters, scheduled and surprise messages
    REMINDERS("reminders"), // reminders and anniversaries
    ;

    companion object {
        /** FCM `data.type` values sent by the backend (notifications.service.ts) mapped to a type. */
        fun fromPushType(type: String?): NotificationType = when (type) {
            "miss_you", "affection" -> AFFECTION
            "letter" -> LETTERS
            else -> MESSAGES // "message", "group_added", missing/unknown: never swallow by accident
        }
    }
}

object NotificationGate {
    /** A notification of a type is posted only when the master switch AND the type switch are on. */
    fun isAllowed(masterEnabled: Boolean, typeEnabled: Boolean): Boolean = masterEnabled && typeEnabled
}

/** One chat that has custom notification behaviour (mute, silent, vibration or its own sound). */
data class ChatNotificationOverride(
    val conversationId: String,
    val mutedUntil: Long?,
    val silent: Boolean,
    val vibrationName: String?,
    val hasCustomSound: Boolean,
)

/** Reads the per-chat override keys NotificationPolicy already writes; pure so it can be tested on the JVM. */
object ChatOverrides {
    private const val MUTE = "mute_until_"
    private const val SILENT = "silent_"
    private const val VIB = "vib_"
    private const val SOUND = "sound_uri_"

    fun fromPrefs(all: Map<String, *>, now: Long): List<ChatNotificationOverride> {
        val ids = all.keys.mapNotNull { k ->
            when {
                k.startsWith(MUTE) -> k.removePrefix(MUTE)
                k.startsWith(SILENT) -> k.removePrefix(SILENT)
                k.startsWith(VIB) -> k.removePrefix(VIB)
                k.startsWith(SOUND) -> k.removePrefix(SOUND)
                else -> null
            }
        }.toSortedSet()
        return ids.mapNotNull { id ->
            val muted = (all[MUTE + id] as? Long)?.takeIf { it > now }
            val silent = all[SILENT + id] as? Boolean ?: false
            val vib = (all[VIB + id] as? String)?.takeIf { it != "DEFAULT" }
            val sound = (all[SOUND + id] as? String) != null
            if (muted == null && !silent && vib == null && !sound) null
            else ChatNotificationOverride(id, muted, silent, vib, sound)
        }
    }

    /** Pref keys to remove to clear every override of [conversationId] (the channel revision counter is kept). */
    fun keysFor(conversationId: String): Set<String> =
        setOf(MUTE + conversationId, SILENT + conversationId, VIB + conversationId, SOUND + conversationId)
}

enum class PhoneVendor { XIAOMI, SAMSUNG, HUAWEI, OPPO, VIVO, OTHER }

/** Auto-start / background-limits guidance per manufacturer. */
object VendorGuide {
    fun detect(manufacturer: String?, brand: String? = null): PhoneVendor {
        val m = "${manufacturer.orEmpty()} ${brand.orEmpty()}".lowercase()
        return when {
            "xiaomi" in m || "redmi" in m || "poco" in m -> PhoneVendor.XIAOMI
            "samsung" in m -> PhoneVendor.SAMSUNG
            "huawei" in m || "honor" in m -> PhoneVendor.HUAWEI
            "oppo" in m || "realme" in m || "oneplus" in m -> PhoneVendor.OPPO
            "vivo" in m || "iqoo" in m -> PhoneVendor.VIVO
            else -> PhoneVendor.OTHER
        }
    }

    /** Known (package, class) pairs for the vendor's auto-start page; the UI tries them in order and falls back to app settings. */
    fun autoStartComponents(vendor: PhoneVendor): List<Pair<String, String>> = when (vendor) {
        PhoneVendor.XIAOMI -> listOf("com.miui.securitycenter" to "com.miui.permcenter.autostart.AutoStartManagementActivity")
        PhoneVendor.HUAWEI -> listOf(
            "com.huawei.systemmanager" to "com.huawei.systemmanager.startupmgr.ui.StartupNormalAppListActivity",
            "com.huawei.systemmanager" to "com.huawei.systemmanager.optimize.process.ProtectActivity",
        )
        PhoneVendor.OPPO -> listOf(
            "com.coloros.safecenter" to "com.coloros.safecenter.permission.startup.StartupAppListActivity",
            "com.oppo.safe" to "com.oppo.safe.permission.startup.StartupAppListActivity",
        )
        PhoneVendor.VIVO -> listOf(
            "com.vivo.permissionmanager" to "com.vivo.permissionmanager.activity.BgStartUpManagerActivity",
            "com.iqoo.secure" to "com.iqoo.secure.ui.phoneoptimize.AddWhiteListActivity",
        )
        PhoneVendor.SAMSUNG -> listOf("com.samsung.android.lool" to "com.samsung.android.sm.battery.ui.BatteryActivity")
        PhoneVendor.OTHER -> emptyList()
    }
}

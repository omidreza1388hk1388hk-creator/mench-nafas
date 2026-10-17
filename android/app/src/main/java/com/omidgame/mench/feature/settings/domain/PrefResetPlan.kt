package com.omidgame.mench.feature.settings.domain

/**
 * Which stored values a "reset" may remove. Only EXPLICIT keys (or key prefixes) of existing settings are
 * listed, never a whole file, so per-chat data, drafts, keys and PINs can never be wiped by accident.
 * Pure Kotlin: the Android side only supplies the keys currently present in a file.
 */
data class PrefTarget(
    val file: String,
    val exactKeys: Set<String> = emptySet(),
    val keyPrefixes: Set<String> = emptySet(),
) {
    fun matches(key: String): Boolean = key in exactKeys || keyPrefixes.any { key.startsWith(it) }
}

object SettingsResetPlan {

    /** Plain SharedPreferences settings, by category. Same file names and keys the screens already use. */
    private val targets: Map<SettingsCategory, List<PrefTarget>> = mapOf(
        SettingsCategory.DATA to listOf(
            // Auto-download rules + mobile size cap. Per-chat overrides ("chat_<id>") are intentionally kept.
            PrefTarget("mench_auto_download", exactKeys = setOf("max_mobile_mb"), keyPrefixes = setOf("rule_")),
        ),
        SettingsCategory.APPEARANCE to listOf(
            PrefTarget("mench_ui_sounds", exactKeys = setOf("enabled", "volume", "event_sent", "event_received", "event_error", "event_confirm")),
            PrefTarget("mench_animation", exactKeys = setOf("reduced")),
            PrefTarget("mench_text_weight", exactKeys = setOf("weight")),
            PrefTarget("haptic_settings", exactKeys = setOf("touch_haptics", "affection_vibration")),
            PrefTarget("graphics_quality", exactKeys = setOf("quality")),
        ),
        SettingsCategory.NOTIFICATIONS to listOf(
            // Master + per-type switches and quiet hours. Per-chat mute/sound overrides are cleared from their own list.
            PrefTarget(
                "mench_notification_policy",
                exactKeys = setOf("master_enabled", "quiet_allow_calls", "quiet_enabled", "quiet_start", "quiet_end"),
                keyPrefixes = setOf("type_"),
            ),
        ),
        SettingsCategory.CALLS to listOf(
            PrefTarget("mench_call_settings", exactKeys = setOf("quality", "noise_suppression", "echo_cancellation", "voice_speaker", "front_camera", "mirror_front")),
        ),
        SettingsCategory.US to listOf(
            PrefTarget("mench_us_settings", exactKeys = setOf("mood", "question", "wishlist", "memories", "haptic_strength", "hearts_animation")),
        ),
        SettingsCategory.CHATS to listOf(
            PrefTarget("message_effects", exactKeys = setOf("effects_enabled", "heartbeat_vibration")),
            PrefTarget(
                "mench_chat_settings",
                exactKeys = setOf(
                    "text_scale", "line_spacing", "enter_sends", "auto_capitalize", "spell_check", "link_previews",
                    "quick_reactions", "swipe_reply", "keep_archived", "photo_quality", "strip_location", "view_once_default",
                ),
            ),
            PrefTarget("chat_prefs", exactKeys = setOf("double_tap_reaction")),
        ),
    )

    /** Categories whose settings live in the encrypted app-lock store; reset goes through AppLockRepository. */
    private val appLockCategories = setOf(SettingsCategory.SECURITY)

    fun prefTargets(category: SettingsCategory): List<PrefTarget> = targets[category].orEmpty()

    fun resetsAppLockTimeout(category: SettingsCategory): Boolean = category in appLockCategories

    /** True when "Reset this section" would do something; the UI hides the row otherwise. */
    fun canReset(category: SettingsCategory): Boolean =
        prefTargets(category).isNotEmpty() || resetsAppLockTimeout(category)

    val resettableCategories: List<SettingsCategory> get() = SettingsCategory.entries.filter(::canReset)

    /** Subset of [presentKeys] that [target] allows removing. */
    fun keysToRemove(presentKeys: Set<String>, target: PrefTarget): Set<String> =
        presentKeys.filterTo(mutableSetOf()) { target.matches(it) }
}

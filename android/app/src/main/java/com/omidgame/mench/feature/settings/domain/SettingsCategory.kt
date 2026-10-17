package com.omidgame.mench.feature.settings.domain

/**
 * Top-level settings categories, in the order they appear on the home screen.
 * [routeKey] is the stable id used for deep links ("settings/category/<routeKey>").
 */
enum class SettingsCategory(val routeKey: String) {
    ACCOUNT("account"),
    SECURITY("security"),
    NOTIFICATIONS("notifications"),
    CHATS("chats"),
    US("us"),
    CALLS("calls"),
    DATA("data"),
    APPEARANCE("appearance"),
    LANGUAGE("language"),
    HELP("help"),
    ;

    companion object {
        fun fromRouteKey(key: String?): SettingsCategory? = entries.firstOrNull { it.routeKey == key }
    }
}

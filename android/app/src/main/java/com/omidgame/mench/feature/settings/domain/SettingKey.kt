package com.omidgame.mench.feature.settings.domain

import androidx.annotation.StringRes

/** Where tapping a settings row (or a search hit) goes. Resolved to a navigation call by the nav layer. */
sealed interface SettingsTarget {
    /** A NavGraph route, e.g. "settings/account" (mirrors MenchNavGraph.Routes). */
    data class Route(val route: String) : SettingsTarget

    /** A Phase-1C product screen, resolved by ProductRoutes.forSettingsKey(key). */
    data class Product(val key: String) : SettingsTarget

    /** A one-shot in-place action handled by the settings home (dialogs, system intents). */
    data class Action(val id: String) : SettingsTarget
}

/**
 * One searchable setting or settings screen. The registry of these is the single source for the
 * home-screen search index; it only describes settings, it never stores their values (existing
 * SharedPreferences / DataStore keys stay exactly where they are).
 *
 * @property id stable, unique, used as the scroll/highlight anchor and in deep links
 * @property keywordsRes space-separated extra search words (Persian + English in one string)
 */
data class SettingKey(
    val id: String,
    val category: SettingsCategory,
    @StringRes val titleRes: Int,
    @StringRes val subtitleRes: Int?,
    @StringRes val keywordsRes: Int?,
    val target: SettingsTarget,
)

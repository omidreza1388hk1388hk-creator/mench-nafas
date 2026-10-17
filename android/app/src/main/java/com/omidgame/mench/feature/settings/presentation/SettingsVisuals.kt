package com.omidgame.mench.feature.settings.presentation

import androidx.annotation.StringRes
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Alarm
import androidx.compose.material.icons.filled.BatteryChargingFull
import androidx.compose.material.icons.filled.Block
import androidx.compose.material.icons.filled.Bookmark
import androidx.compose.material.icons.filled.Call
import androidx.compose.material.icons.filled.CardGiftcard
import androidx.compose.material.icons.filled.Chat
import androidx.compose.material.icons.filled.DeleteForever
import androidx.compose.material.icons.filled.Description
import androidx.compose.material.icons.filled.Favorite
import androidx.compose.material.icons.filled.Feedback
import androidx.compose.material.icons.filled.HealthAndSafety
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.Language
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.NewReleases
import androidx.compose.material.icons.filled.Notifications
import androidx.compose.material.icons.filled.Palette
import androidx.compose.material.icons.filled.Person
import androidx.compose.material.icons.filled.PersonAdd
import androidx.compose.material.icons.filled.PhoneAndroid
import androidx.compose.material.icons.filled.PrivacyTip
import androidx.compose.material.icons.filled.QrCodeScanner
import androidx.compose.material.icons.filled.Shield
import androidx.compose.material.icons.filled.Star
import androidx.compose.material.icons.filled.Storage
import androidx.compose.material.icons.filled.SwitchAccount
import androidx.compose.material.icons.filled.Sync
import androidx.compose.material.icons.filled.Wallpaper
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import com.omidgame.mench.R
import com.omidgame.mench.feature.settings.domain.SettingsCategory

/** Icon + tile colour for a category card. Colours are the ones the old flat list already used. */
data class SettingsVisual(val icon: ImageVector, val tint: Color)

fun categoryVisual(c: SettingsCategory): SettingsVisual = when (c) {
    SettingsCategory.ACCOUNT -> SettingsVisual(Icons.Filled.Person, Color(0xFFE91E63))
    SettingsCategory.SECURITY -> SettingsVisual(Icons.Filled.Shield, Color(0xFF2E7D32))
    SettingsCategory.NOTIFICATIONS -> SettingsVisual(Icons.Filled.Notifications, Color(0xFFB26A00))
    SettingsCategory.CHATS -> SettingsVisual(Icons.Filled.Chat, Color(0xFF1565C0))
    SettingsCategory.US -> SettingsVisual(Icons.Filled.Favorite, Color(0xFFD81B60))
    SettingsCategory.CALLS -> SettingsVisual(Icons.Filled.Call, Color(0xFF00897B))
    SettingsCategory.DATA -> SettingsVisual(Icons.Filled.Storage, Color(0xFF1E88E5))
    SettingsCategory.APPEARANCE -> SettingsVisual(Icons.Filled.Palette, Color(0xFF8E24AA))
    SettingsCategory.LANGUAGE -> SettingsVisual(Icons.Filled.Language, Color(0xFF5E35B1))
    SettingsCategory.HELP -> SettingsVisual(Icons.Filled.Info, Color(0xFF546E7A))
}

@StringRes
fun categoryTitleRes(c: SettingsCategory): Int = when (c) {
    SettingsCategory.ACCOUNT -> R.string.sx_cat_account
    SettingsCategory.SECURITY -> R.string.sx_cat_security
    SettingsCategory.NOTIFICATIONS -> R.string.sx_cat_notifications
    SettingsCategory.CHATS -> R.string.sx_cat_chats
    SettingsCategory.US -> R.string.sx_cat_us
    SettingsCategory.CALLS -> R.string.sx_cat_calls
    SettingsCategory.DATA -> R.string.sx_cat_data
    SettingsCategory.APPEARANCE -> R.string.sx_cat_appearance
    SettingsCategory.LANGUAGE -> R.string.sx_cat_language
    SettingsCategory.HELP -> R.string.sx_cat_help
}

@StringRes
fun categoryDescRes(c: SettingsCategory): Int = when (c) {
    SettingsCategory.ACCOUNT -> R.string.sx_cat_account_desc
    SettingsCategory.SECURITY -> R.string.sx_cat_security_desc
    SettingsCategory.NOTIFICATIONS -> R.string.sx_cat_notifications_desc
    SettingsCategory.CHATS -> R.string.sx_cat_chats_desc
    SettingsCategory.US -> R.string.sx_cat_us_desc
    SettingsCategory.CALLS -> R.string.sx_cat_calls_desc
    SettingsCategory.DATA -> R.string.sx_cat_data_desc
    SettingsCategory.APPEARANCE -> R.string.sx_cat_appearance_desc
    SettingsCategory.LANGUAGE -> R.string.sx_cat_language_desc
    SettingsCategory.HELP -> R.string.sx_cat_help_desc
}

/** Per-row icon; rows without a specific icon fall back to their category's. */
fun settingVisual(id: String, category: SettingsCategory): SettingsVisual = when (id) {
    "account" -> SettingsVisual(Icons.Filled.Person, Color(0xFFE91E63))
    "devices" -> SettingsVisual(Icons.Filled.PhoneAndroid, Color(0xFF00897B))
    "link_device" -> SettingsVisual(Icons.Filled.QrCodeScanner, Color(0xFF00897B))
    "accounts" -> SettingsVisual(Icons.Filled.SwitchAccount, Color(0xFF5E35B1))
    "referrals" -> SettingsVisual(Icons.Filled.CardGiftcard, Color(0xFFFB8C00))
    "find_people" -> SettingsVisual(Icons.Filled.PersonAdd, Color(0xFFEC407A))
    "plus" -> SettingsVisual(Icons.Filled.Star, Color(0xFFB28704))
    "delete_account" -> SettingsVisual(Icons.Filled.DeleteForever, Color(0xFFC62828))
    "privacy_security" -> SettingsVisual(Icons.Filled.Shield, Color(0xFF2E7D32))
    "two_step" -> SettingsVisual(Icons.Filled.Lock, Color(0xFF2E7D32))
    "privacy", "contact_privacy" -> SettingsVisual(Icons.Filled.PrivacyTip, Color(0xFF2E7D32))
    "blocked" -> SettingsVisual(Icons.Filled.Block, Color(0xFF757575))
    "notifications" -> SettingsVisual(Icons.Filled.Notifications, Color(0xFFB26A00))
    "battery" -> SettingsVisual(Icons.Filled.BatteryChargingFull, Color(0xFF43A047))
    "wallpaper" -> SettingsVisual(Icons.Filled.Wallpaper, Color(0xFFE64A19))
    "effects" -> SettingsVisual(Icons.Filled.Favorite, Color(0xFFD81B60))
    "saved" -> SettingsVisual(Icons.Filled.Bookmark, Color(0xFF3949AB))
    "planner" -> SettingsVisual(Icons.Filled.Alarm, Color(0xFF43A047))
    "sync" -> SettingsVisual(Icons.Filled.Sync, Color(0xFF1565C0))
    "diagnostics" -> SettingsVisual(Icons.Filled.HealthAndSafety, Color(0xFF00897B))
    "feedback" -> SettingsVisual(Icons.Filled.Feedback, Color(0xFF8E24AA))
    "legal" -> SettingsVisual(Icons.Filled.Description, Color(0xFF5D4037))
    "changelog" -> SettingsVisual(Icons.Filled.NewReleases, Color(0xFFB26A00))
    else -> categoryVisual(category)
}

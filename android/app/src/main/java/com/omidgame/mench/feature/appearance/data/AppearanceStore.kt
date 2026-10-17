package com.omidgame.mench.feature.appearance.data

import android.content.Context
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import com.omidgame.mench.feature.appearance.domain.AccentColor
import com.omidgame.mench.feature.appearance.domain.AppearanceSettings
import com.omidgame.mench.feature.appearance.domain.BubbleStyle
import com.omidgame.mench.feature.appearance.domain.CalendarStyle
import com.omidgame.mench.feature.appearance.domain.DigitStyle
import com.omidgame.mench.feature.appearance.domain.FontScale
import com.omidgame.mench.feature.appearance.domain.ThemeMode
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import javax.inject.Inject
import javax.inject.Singleton

private val Context.appearanceDataStore by preferencesDataStore(name = "appearance_settings")

/**
 * Plain (unencrypted) DataStore, not [com.omidgame.mench.core.security.TokenStore]'s
 * Keystore-backed EncryptedSharedPreferences — theme/font/bubble choices
 * carry no sensitive data, so the lighter-weight, already-declared
 * `androidx.datastore:datastore-preferences` dependency is the right tool
 * here rather than adding encryption overhead nothing needs.
 */
@Singleton
class AppearanceStore @Inject constructor(
    @ApplicationContext private val context: Context,
) {
    private object Keys {
        val THEME_MODE = stringPreferencesKey("theme_mode")
        val ACCENT_COLOR = stringPreferencesKey("accent_color")
        val FONT_SCALE = stringPreferencesKey("font_scale")
        val BUBBLE_STYLE = stringPreferencesKey("bubble_style")
        val DYNAMIC_COLOR = booleanPreferencesKey("dynamic_color")
        val AMOLED = booleanPreferencesKey("amoled")
        val DIGIT_STYLE = stringPreferencesKey("digit_style")
        val CALENDAR_STYLE = stringPreferencesKey("calendar_style")
        val SCREEN_SECURITY = booleanPreferencesKey("screen_security")
    }

    val settings: Flow<AppearanceSettings> = context.appearanceDataStore.data.map { prefs ->
        AppearanceSettings(
            themeMode = ThemeMode.fromWireValue(prefs[Keys.THEME_MODE]),
            accentColor = AccentColor.fromWireValue(prefs[Keys.ACCENT_COLOR]),
            fontScale = FontScale.fromWireValue(prefs[Keys.FONT_SCALE]),
            bubbleStyle = BubbleStyle.fromWireValue(prefs[Keys.BUBBLE_STYLE]),
            useDynamicColor = prefs[Keys.DYNAMIC_COLOR] ?: false,
            useAmoled = prefs[Keys.AMOLED] ?: false,
            digitStyle = DigitStyle.fromWireValue(prefs[Keys.DIGIT_STYLE]),
            calendarStyle = CalendarStyle.fromWireValue(prefs[Keys.CALENDAR_STYLE]),
            screenSecurity = prefs[Keys.SCREEN_SECURITY] ?: false,
        )
    }

    suspend fun setThemeMode(mode: ThemeMode) {
        context.appearanceDataStore.edit { it[Keys.THEME_MODE] = mode.wireValue }
    }

    suspend fun setAccentColor(color: AccentColor) {
        context.appearanceDataStore.edit { it[Keys.ACCENT_COLOR] = color.wireValue }
    }

    suspend fun setFontScale(scale: FontScale) {
        context.appearanceDataStore.edit { it[Keys.FONT_SCALE] = scale.wireValue }
    }

    suspend fun setBubbleStyle(style: BubbleStyle) {
        context.appearanceDataStore.edit { it[Keys.BUBBLE_STYLE] = style.wireValue }
    }

    suspend fun setUseDynamicColor(enabled: Boolean) {
        context.appearanceDataStore.edit { it[Keys.DYNAMIC_COLOR] = enabled }
    }

    suspend fun setUseAmoled(enabled: Boolean) {
        context.appearanceDataStore.edit { it[Keys.AMOLED] = enabled }
    }

    suspend fun setDigitStyle(style: DigitStyle) {
        context.appearanceDataStore.edit { it[Keys.DIGIT_STYLE] = style.wireValue }
    }

    suspend fun setCalendarStyle(style: CalendarStyle) {
        context.appearanceDataStore.edit { it[Keys.CALENDAR_STYLE] = style.wireValue }
    }

    suspend fun setScreenSecurity(enabled: Boolean) {
        context.appearanceDataStore.edit { it[Keys.SCREEN_SECURITY] = enabled }
    }
}

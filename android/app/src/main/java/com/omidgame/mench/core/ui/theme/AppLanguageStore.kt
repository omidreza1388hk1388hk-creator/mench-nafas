package com.omidgame.mench.core.ui.theme

import android.content.Context
import android.content.res.Configuration
import com.omidgame.mench.feature.settings.domain.AppLanguage
import java.util.Locale

/**
 * In-app language choice. Works on every Android version: the activity's base context is wrapped with the chosen
 * locale (see MainActivity.attachBaseContext), and changing the choice recreates the activity so it takes effect at once.
 */
object AppLanguageStore {
    private const val PREFS = "mench_language"
    private const val KEY = "language"

    fun get(context: Context): AppLanguage =
        AppLanguage.fromStorage(context.applicationContext.getSharedPreferences(PREFS, Context.MODE_PRIVATE).getString(KEY, null))

    fun set(context: Context, language: AppLanguage) {
        context.applicationContext.getSharedPreferences(PREFS, Context.MODE_PRIVATE).edit().putString(KEY, language.storageValue).apply()
    }

    /** Returns [base] unchanged for SYSTEM; otherwise a context whose resources, default locale and layout direction match. */
    fun wrap(base: Context): Context {
        val tag = get(base).languageTag ?: return base
        val locale = Locale.forLanguageTag(tag)
        Locale.setDefault(locale)
        val config = Configuration(base.resources.configuration)
        config.setLocale(locale)
        config.setLayoutDirection(locale)
        return base.createConfigurationContext(config)
    }

}

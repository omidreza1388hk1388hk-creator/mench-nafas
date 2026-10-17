package com.omidgame.mench.feature.settings.domain

/** The app language choice. SYSTEM follows the phone; the others force Persian or English inside the app. */
enum class AppLanguage(val storageValue: String, val languageTag: String?) {
    SYSTEM("system", null),
    PERSIAN("fa", "fa"),
    ENGLISH("en", "en"),
    ;

    /** True for languages written right-to-left, so the layout direction can be forced to match. */
    val isRtl: Boolean get() = this == PERSIAN

    companion object {
        fun fromStorage(v: String?): AppLanguage = entries.firstOrNull { it.storageValue == v } ?: SYSTEM
    }
}

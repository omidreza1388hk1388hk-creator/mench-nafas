package com.omidgame.mench.core.ui.theme

import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.googlefonts.Font
import androidx.compose.ui.text.googlefonts.GoogleFont
import com.omidgame.mench.R

private val menchFontProvider = GoogleFont.Provider(
    providerAuthority = "com.google.android.gms.fonts",
    providerPackage = "com.google.android.gms",
    certificates = R.array.com_google_android_gms_fonts_certs,
)

private val vazirmatn = GoogleFont(name = "Vazirmatn")

/**
 * Persian/Arabic-first type family. The system default (Roboto) has no
 * real Arabic-script design — Persian text renders with wrong letterform
 * proportions and no proper contextual joining behavior. Vazirmatn (SIL
 * Open Font License, hosted on Google Fonts) is designed for Persian and
 * Arabic first, Latin second, so a single family covers both scripts
 * used in this app without swapping fonts mid-string.
 *
 * Fetched at runtime through Google Play Services' font provider (see
 * res/values-v23/font_certs.xml for the provider's certificate hashes —
 * this app already depends on Play Services for Firebase Messaging, so
 * this adds no new runtime dependency) rather than bundled as .ttf
 * assets, keeping the APK small and letting the font update
 * independently of app releases. On the rare non-GMS device where the
 * provider is unavailable, Compose silently falls back to the platform
 * default typeface — there is no crash path here, only a visual
 * degradation to the same look this app had before this file existed.
 */
val MenchFontFamily = FontFamily(
    Font(googleFont = vazirmatn, fontProvider = menchFontProvider, weight = FontWeight.Normal),
    Font(googleFont = vazirmatn, fontProvider = menchFontProvider, weight = FontWeight.Medium),
    Font(googleFont = vazirmatn, fontProvider = menchFontProvider, weight = FontWeight.SemiBold),
    Font(googleFont = vazirmatn, fontProvider = menchFontProvider, weight = FontWeight.Bold),
)

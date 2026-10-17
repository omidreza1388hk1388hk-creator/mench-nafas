package com.omidgame.mench.feature.auth.domain

/**
 * Persian (۰-۹) and Arabic-Indic (٠-٩) digits to ASCII. Persian users type these on their keyboards,
 * and the server only understands 0-9.
 */
fun String.toAsciiDigits(): String = buildString(length) {
    for (c in this@toAsciiDigits) {
        append(
            when (c) {
                in '\u06F0'..'\u06F9' -> '0' + (c - '\u06F0')
                in '\u0660'..'\u0669' -> '0' + (c - '\u0660')
                else -> c
            },
        )
    }
}

/** Keeps only the ASCII digits of [raw] (after converting Persian/Arabic digits). Used for OTP codes. */
fun normalizeOtpCode(raw: String): String = raw.toAsciiDigits().filter { it in '0'..'9' }

/**
 * Turns what a person typed into E.164 ("+989123456789").
 * Accepts Persian digits, spaces/dashes/brackets, "00" prefix, and Iranian mobile numbers written
 * as 09123456789 or 9123456789. Already-valid E.164 input is returned unchanged.
 */
fun normalizePhoneInput(raw: String): String {
    val ascii = raw.toAsciiDigits()
    val digits = ascii.filter { it in '0'..'9' }
    val hasPlus = ascii.trim().startsWith("+")
    return when {
        digits.isEmpty() -> ""
        digits.startsWith("09") && digits.length == 11 -> "+98" + digits.drop(1)
        digits.startsWith("00") -> "+" + digits.drop(2)
        hasPlus -> "+$digits"
        digits.length == 10 && digits.startsWith("9") -> "+98$digits"
        else -> "+$digits"
    }
}

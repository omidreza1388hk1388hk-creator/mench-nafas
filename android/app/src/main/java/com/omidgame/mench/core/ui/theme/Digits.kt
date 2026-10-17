package com.omidgame.mench.core.ui.theme

import androidx.compose.runtime.Composable
import androidx.compose.runtime.compositionLocalOf

/**
 * Persian / Latin digit conversion. Vazirmatn (non-FD build) draws Latin digits as Latin,
 * so numbers that should read as Persian (counters, times, unread badges) go through here.
 * Whether to convert is a user choice; [LocalPersianDigits] carries it down the tree.
 */
object MenchDigits {
    private const val PERSIAN_ZERO = '\u06F0'

    fun toPersian(input: String): String {
        if (input.none { it in '0'..'9' }) return input
        val out = StringBuilder(input.length)
        for (ch in input) {
            out.append(if (ch in '0'..'9') PERSIAN_ZERO + (ch - '0') else ch)
        }
        return out.toString()
    }

    fun toLatin(input: String): String {
        val out = StringBuilder(input.length)
        for (ch in input) {
            out.append(
                when (ch) {
                    in '\u06F0'..'\u06F9' -> '0' + (ch - '\u06F0')
                    in '\u0660'..'\u0669' -> '0' + (ch - '\u0660')
                    else -> ch
                },
            )
        }
        return out.toString()
    }
}

val LocalPersianDigits = compositionLocalOf { false }

/** Converts digits in this string according to the current [LocalPersianDigits] value. */
@Composable
fun String.localizedDigits(): String =
    if (LocalPersianDigits.current) MenchDigits.toPersian(this) else this

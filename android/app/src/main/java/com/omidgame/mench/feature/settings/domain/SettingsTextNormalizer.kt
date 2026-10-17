package com.omidgame.mench.feature.settings.domain

/**
 * Normalises text for in-settings search so that Persian and English queries match regardless of
 * keyboard quirks. Pure Kotlin (JVM-testable).
 *
 * - Arabic ي / ى -> Persian ی, Arabic ك -> Persian ک
 * - Persian / Arabic-Indic digits -> ASCII digits ("digits-insensitive")
 * - ZWNJ, ZWJ, tatweel, Arabic diacritics and bidi marks are removed ("ZWNJ-insensitive")
 * - lower-cased, punctuation turned into spaces, whitespace collapsed
 */
object SettingsTextNormalizer {

    fun normalize(input: String): String {
        val sb = StringBuilder(input.length)
        var lastWasSpace = true
        for (raw in input) {
            val ch = fold(raw)
            when {
                ch == NONE -> Unit
                ch.isLetterOrDigit() -> {
                    sb.append(ch.lowercaseChar())
                    lastWasSpace = false
                }
                else -> if (!lastWasSpace) {
                    sb.append(' ')
                    lastWasSpace = true
                }
            }
        }
        return sb.toString().trimEnd()
    }

    /** Splits a normalised string into search tokens. */
    fun tokens(input: String): List<String> =
        normalize(input).split(' ').filter { it.isNotEmpty() }

    private const val NONE = '\u0000'

    private fun fold(ch: Char): Char = when (ch) {
        '\u064A', '\u0649', '\u06CC' -> '\u06CC' // ي ى ی -> ی
        '\u0643', '\u06A9' -> '\u06A9' // ك ک -> ک
        in '\u06F0'..'\u06F9' -> '0' + (ch - '\u06F0')
        in '\u0660'..'\u0669' -> '0' + (ch - '\u0660')
        '\u200C', '\u200D', '\u200E', '\u200F', '\u0640', '\uFEFF' -> NONE
        in '\u064B'..'\u065F', '\u0670' -> NONE // Arabic harakat
        else -> ch
    }
}

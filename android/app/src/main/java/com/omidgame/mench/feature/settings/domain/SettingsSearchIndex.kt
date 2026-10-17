package com.omidgame.mench.feature.settings.domain

data class SettingsSearchHit(val key: SettingKey, val score: Int)

/**
 * In-memory search index over the settings registry. Pure Kotlin: strings are supplied by [resolve]
 * (a Context.getString wrapper in the app, a fake map in tests).
 *
 * Matching: every query token must hit title, subtitle or keywords (AND). Per token the best field wins:
 * title word-prefix > title substring > keyword word-prefix > keyword substring > subtitle substring.
 * Ties keep registry order so results are deterministic.
 */
class SettingsSearchIndex(
    keys: List<SettingKey>,
    resolve: (Int) -> String,
) {
    private class Entry(val key: SettingKey, val title: String, val subtitle: String, val keywords: String, val order: Int) {
        val titleWords = title.split(' ')
        val keywordWords = keywords.split(' ')
    }

    private val entries: List<Entry> = keys.mapIndexed { i, k ->
        Entry(
            key = k,
            title = SettingsTextNormalizer.normalize(resolve(k.titleRes)),
            subtitle = k.subtitleRes?.let { SettingsTextNormalizer.normalize(resolve(it)) }.orEmpty(),
            keywords = k.keywordsRes?.let { SettingsTextNormalizer.normalize(resolve(it)) }.orEmpty(),
            order = i,
        )
    }

    fun search(query: String): List<SettingsSearchHit> {
        val tokens = SettingsTextNormalizer.tokens(query)
        if (tokens.isEmpty()) return emptyList()
        val whole = tokens.joinToString(" ")
        return entries.mapNotNull { e ->
            var total = 0
            for (t in tokens) {
                val s = tokenScore(e, t)
                if (s == 0) return@mapNotNull null
                total += s
            }
            if (e.title == whole) total += 50 else if (e.title.startsWith(whole)) total += 30
            SettingsSearchHit(e.key, total) to e.order
        }.sortedWith(compareByDescending<Pair<SettingsSearchHit, Int>> { it.first.score }.thenBy { it.second })
            .map { it.first }
    }

    private fun tokenScore(e: Entry, t: String): Int = when {
        e.titleWords.any { it.startsWith(t) } -> 100
        e.title.contains(t) -> 60
        e.keywordWords.any { it.startsWith(t) } -> 40
        e.keywords.contains(t) -> 25
        e.subtitle.contains(t) -> 15
        else -> 0
    }
}

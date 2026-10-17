package com.omidgame.mench.feature.chat.presentation

/** One tappable romantic search topic in the GIF picker. [query] is what is sent to the GIF provider (English works best). */
data class GifTopic(val labelFa: String, val labelEn: String, val query: String)

/**
 * Phase 2A — GIF search helpers: the curated romantic topics and a small Persian -> English term
 * mapper, because the GIF provider's index is overwhelmingly English ("دوستت دارم" finds almost
 * nothing, "i love you" finds plenty). Pure Kotlin, unit-tested.
 */
object GifQuery {
    val topics: List<GifTopic> = listOf(
        GifTopic("عاشقانه", "Romantic", "romantic couple"),
        GifTopic("دوستت دارم", "I love you", "i love you"),
        GifTopic("بوس", "Kiss", "kiss couple"),
        GifTopic("بغل", "Hug", "hug couple"),
        GifTopic("دلتنگی", "Miss you", "miss you"),
        GifTopic("قلب", "Hearts", "heart love"),
        GifTopic("ناز", "Cute", "cute couple"),
        GifTopic("صبح بخیر", "Good morning", "good morning love"),
        GifTopic("شب بخیر", "Good night", "good night love"),
        GifTopic("ببخشید", "Sorry", "sorry love"),
        GifTopic("خنده", "Laugh", "laughing"),
        GifTopic("تولد", "Birthday", "happy birthday"),
    )

    /** What the picker shows before the user types or taps anything. */
    val defaultTopic: GifTopic get() = topics.first()

    // Keys are already normalised (see [normalizePersian]); longer phrases first so they win.
    private val dictionary: List<Pair<String, String>> = listOf(
        "دوستت دارم" to "i love you",
        "دوست دارم" to "i love you",
        "عاشقتم" to "i love you",
        "صبح بخیر" to "good morning",
        "شب بخیر" to "good night",
        "دلم تنگ" to "miss you",
        "دلتنگی" to "miss you",
        "دلتنگتم" to "miss you",
        "ببخشید" to "sorry",
        "معذرت" to "sorry",
        "ممنون" to "thank you",
        "مرسی" to "thank you",
        "خداحافظ" to "bye",
        "سلام" to "hello",
        "تولدت مبارک" to "happy birthday",
        "تولد" to "birthday",
        "تبریک" to "congratulations",
        "عشق" to "love",
        "عاشقانه" to "romantic",
        "بوس" to "kiss",
        "ماچ" to "kiss",
        "بغل" to "hug",
        "قلب" to "heart",
        "ناز" to "cute",
        "خنده" to "laughing",
        "گریه" to "crying",
        "قهر" to "angry",
        "خجالت" to "shy",
        "رقص" to "dance",
        "خواب" to "sleepy",
        "گل" to "flowers",
        "ماه" to "moon",
        "خوشحال" to "happy",
        "ناراحت" to "sad",
    ).sortedByDescending { it.first.length }

    /** Arabic yeh/kaf -> Persian, ZWNJ -> space, diacritics dropped, lowercase, single spaces. */
    internal fun normalizePersian(raw: String): String {
        val sb = StringBuilder(raw.length)
        for (ch in raw.trim().lowercase()) {
            when (ch) {
                '\u064A' -> sb.append('\u06CC')
                '\u0643' -> sb.append('\u06A9')
                '\u200C' -> sb.append(' ')
                in '\u064B'..'\u065F' -> Unit
                else -> sb.append(ch)
            }
        }
        return sb.toString().split(' ').filter { it.isNotEmpty() }.joinToString(" ")
    }

    /**
     * The query to send to the provider. Persian terms are replaced by their English equivalent;
     * everything else (already-English words, unknown Persian) is passed through untouched.
     */
    fun toProviderQuery(raw: String): String {
        val normalized = normalizePersian(raw)
        if (normalized.isEmpty()) return ""
        var remaining = normalized
        val out = ArrayList<String>()
        while (remaining.isNotEmpty()) {
            val hit = dictionary.firstOrNull { (key, _) -> remaining == key || remaining.startsWith("$key ") }
            if (hit != null) {
                out.add(hit.second)
                remaining = remaining.removePrefix(hit.first).trimStart()
            } else {
                val space = remaining.indexOf(' ')
                if (space < 0) {
                    out.add(remaining)
                    remaining = ""
                } else {
                    out.add(remaining.substring(0, space))
                    remaining = remaining.substring(space + 1)
                }
            }
        }
        return out.joinToString(" ")
    }
}

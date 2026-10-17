package com.omidgame.mench.feature.chat.presentation

/**
 * Phase 2A — the built-in romantic sticker set. Stickers are drawn by the app (gradient card + big
 * emoji + caption, see StickerUi.kt), so there are no image assets to ship or download, they work
 * offline, and a sticker message is only a few bytes (see [RichBody]).
 *
 * Captions are Persian first (this app's audience) with an English twin shown on non-Persian locales.
 * Pure Kotlin so the catalog invariants are unit-tested on the JVM.
 */
enum class StickerCategory { LOVE, MISS, AFFECTION, GREETING, CUTE, CELEBRATE }

data class CatalogSticker(
    val id: String,
    val emoji: String,
    val captionFa: String,
    val captionEn: String,
    val palette: Int,
    val category: StickerCategory,
)

/** Everything needed to draw one sticker, whether it came from the catalog or the custom-sticker maker. */
data class StickerSpec(val emoji: String, val captionFa: String, val captionEn: String, val palette: Int, val catalogId: String?) {
    fun caption(persian: Boolean): String = if (persian) captionFa else captionEn
}

object StickerCatalog {
    /** Two ARGB gradient stops (top, bottom) per palette. The index travels in the message, so only ever append. */
    val palettes: List<Pair<Long, Long>> = listOf(
        0xFFFF5A7EL to 0xFFE0245EL, // 0 rose
        0xFFFF8A9BL to 0xFFFF5E78L, // 1 blush
        0xFFFFA06BL to 0xFFFF5F6DL, // 2 sunset
        0xFFB57BFFL to 0xFF7B4DFFL, // 3 lavender
        0xFFD94BA6L to 0xFF8E2DE2L, // 4 berry
        0xFF5B7CFAL to 0xFF2E3A8CL, // 5 midnight
        0xFF3DDBB0L to 0xFF1E9E86L, // 6 mint
        0xFFFFC857L to 0xFFFF8F3FL, // 7 honey
    )

    const val MAX_CUSTOM_CAPTION = 24
    const val MAX_CUSTOM_EMOJI = 3

    val all: List<CatalogSticker> = listOf(
        // LOVE
        CatalogSticker("love_01", "\uD83D\uDC97", "دوستت دارم", "I love you", 0, StickerCategory.LOVE),
        CatalogSticker("love_02", "\uD83D\uDE0D", "عاشقتم", "I adore you", 1, StickerCategory.LOVE),
        CatalogSticker("love_03", "\u2764\uFE0F", "همه‌چیزمی", "You're my everything", 0, StickerCategory.LOVE),
        CatalogSticker("love_04", "\uD83D\uDC8D", "مال منی", "You're mine", 4, StickerCategory.LOVE),
        CatalogSticker("love_05", "\uD83E\uDD70", "قربونت برم", "Love you to bits", 1, StickerCategory.LOVE),
        CatalogSticker("love_06", "\uD83D\uDC9E", "من و تو", "Me & you", 3, StickerCategory.LOVE),
        CatalogSticker("love_07", "\uD83C\uDF39", "تو گل منی", "You're my rose", 0, StickerCategory.LOVE),
        CatalogSticker("love_08", "\u267E\uFE0F", "تا همیشه", "Forever", 3, StickerCategory.LOVE),
        // MISS
        CatalogSticker("miss_01", "\uD83E\uDD7A", "دلم برات تنگ شده", "I miss you", 3, StickerCategory.MISS),
        CatalogSticker("miss_02", "\uD83D\uDE22", "کجایی؟", "Where are you?", 5, StickerCategory.MISS),
        CatalogSticker("miss_03", "\uD83D\uDCDE", "زنگ بزن", "Call me", 6, StickerCategory.MISS),
        CatalogSticker("miss_04", "\uD83C\uDF19", "دلتنگتم", "Missing you", 5, StickerCategory.MISS),
        CatalogSticker("miss_05", "\u23F3", "کی می‌بینمت؟", "When can I see you?", 3, StickerCategory.MISS),
        // AFFECTION
        CatalogSticker("aff_01", "\uD83D\uDE18", "ماچ", "Kiss", 1, StickerCategory.AFFECTION),
        CatalogSticker("aff_02", "\uD83E\uDEC2", "بیا بغلم", "Come hug me", 2, StickerCategory.AFFECTION),
        CatalogSticker("aff_03", "\uD83D\uDC8B", "بوس بوس", "Kisses", 0, StickerCategory.AFFECTION),
        CatalogSticker("aff_04", "\uD83E\uDD17", "یه بغل محکم", "Big hug", 2, StickerCategory.AFFECTION),
        CatalogSticker("aff_05", "\uD83D\uDE1A", "بوس به لپت", "Kiss on your cheek", 1, StickerCategory.AFFECTION),
        // GREETING
        CatalogSticker("gr_01", "\u2600\uFE0F", "صبح بخیر عشقم", "Good morning, love", 7, StickerCategory.GREETING),
        CatalogSticker("gr_02", "\uD83C\uDF19", "شب بخیر جونم", "Good night, my love", 5, StickerCategory.GREETING),
        CatalogSticker("gr_03", "\uD83D\uDE34", "خوابت ببینم", "Sweet dreams", 5, StickerCategory.GREETING),
        CatalogSticker("gr_04", "\uD83D\uDC4B", "سلام عشقم", "Hi, love", 2, StickerCategory.GREETING),
        CatalogSticker("gr_05", "\uD83C\uDF7D\uFE0F", "ناهار خوردی؟", "Did you eat?", 7, StickerCategory.GREETING),
        // CUTE
        CatalogSticker("cute_01", "\uD83D\uDE48", "خجالت کشیدم", "I'm shy", 1, StickerCategory.CUTE),
        CatalogSticker("cute_02", "\uD83D\uDE24", "قهرم!", "I'm mad at you!", 0, StickerCategory.CUTE),
        CatalogSticker("cute_03", "\uD83E\uDD79", "ببخشید", "I'm sorry", 3, StickerCategory.CUTE),
        CatalogSticker("cute_04", "\uD83D\uDE0E", "تو بهترینی", "You're the best", 6, StickerCategory.CUTE),
        CatalogSticker("cute_05", "\uD83D\uDC3B", "خرسی من", "My teddy bear", 7, StickerCategory.CUTE),
        CatalogSticker("cute_06", "\uD83D\uDE4F", "قربونت", "Thank you, dear", 4, StickerCategory.CUTE),
        // CELEBRATE
        CatalogSticker("cel_01", "\uD83C\uDF82", "تولدت مبارک", "Happy birthday", 7, StickerCategory.CELEBRATE),
        CatalogSticker("cel_02", "\uD83C\uDF89", "تبریک عشقم", "Congrats, love", 2, StickerCategory.CELEBRATE),
        CatalogSticker("cel_03", "\u2728", "سالگردمون مبارک", "Happy anniversary", 4, StickerCategory.CELEBRATE),
        CatalogSticker("cel_04", "\uD83E\uDD42", "به سلامتی ما", "Cheers to us", 7, StickerCategory.CELEBRATE),
        CatalogSticker("cel_05", "\uD83C\uDFC6", "افتخار می‌کنم بهت", "Proud of you", 6, StickerCategory.CELEBRATE),
    )

    private val byId: Map<String, CatalogSticker> by lazy { all.associateBy { it.id } }

    fun find(id: String): CatalogSticker? = byId[id]

    fun inCategory(category: StickerCategory): List<CatalogSticker> = all.filter { it.category == category }

    fun spec(sticker: CatalogSticker) =
        StickerSpec(sticker.emoji, sticker.captionFa, sticker.captionEn, sticker.palette, sticker.id)

    /** Trimmed, whitespace-flattened, length-capped caption; empty is allowed (an emoji-only sticker). */
    fun cleanCaption(raw: String): String =
        raw.replace('\n', ' ').replace('\r', ' ').replace('\t', ' ').trim().take(MAX_CUSTOM_CAPTION).trim()

    fun isValidPalette(index: Int) = index in palettes.indices
}

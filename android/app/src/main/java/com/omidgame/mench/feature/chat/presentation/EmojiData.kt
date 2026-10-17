package com.omidgame.mench.feature.chat.presentation

/**
 * Built-in emoji set for the picker: grouped, searchable by English AND Persian keywords,
 * with skin-tone support for hand / person emoji. Kept as plain text parsed once on first use
 * (no network, no asset loading, no extra library), and pure Kotlin so the parser and search
 * are unit-tested on the JVM.
 *
 * Line format inside a category block:  [+]emoji|space separated keywords
 * A leading "+" marks an emoji that accepts a skin-tone modifier.
 */
enum class EmojiCategory { RECENT, LOVE, SMILEYS, GESTURES, NATURE, FOOD, TRAVEL, OBJECTS, SYMBOLS }

data class EmojiEntry(val base: String, val keywords: String, val supportsTone: Boolean)

object EmojiData {
    private const val FITZPATRICK_BASE = 0x1F3FB // light; 0x1F3FB..0x1F3FF are the five modifiers
    private const val VS16 = 0xFE0F

    /** Number of selectable skin tones: 0 = default (yellow), 1..5 = light .. dark. */
    const val TONE_COUNT = 6

    val categories: Map<EmojiCategory, List<EmojiEntry>> by lazy { parse() }

    /** Flat list in display order, de-duplicated by base emoji (the first category wins). */
    val all: List<EmojiEntry> by lazy {
        val seen = HashSet<String>()
        categories.values.flatten().filter { seen.add(it.base) }
    }

    /**
     * Applies skin [tone] (0 = none, 1..5) to an emoji that supports it. The modifier goes right
     * after the first code point; a trailing variation selector on the base is dropped, because a
     * modified sequence is already emoji-presented.
     */
    fun withTone(emoji: EmojiEntry, tone: Int): String {
        if (!emoji.supportsTone || tone !in 1..5) return emoji.base
        val cps = emoji.base.codePoints().toArray()
        val sb = StringBuilder()
        sb.appendCodePoint(cps[0])
        sb.appendCodePoint(FITZPATRICK_BASE + tone - 1)
        for (i in 1 until cps.size) {
            if (i == 1 && cps[i] == VS16) continue
            sb.appendCodePoint(cps[i])
        }
        return sb.toString()
    }

    /** Case-, Arabic-yeh/kaf- and ZWNJ-insensitive search over keywords; also matches the emoji itself. */
    fun search(query: String, limit: Int = 120): List<EmojiEntry> {
        val q = normalize(query)
        if (q.isEmpty()) return emptyList()
        val starts = ArrayList<EmojiEntry>()
        val contains = ArrayList<EmojiEntry>()
        for (entry in all) {
            if (entry.base == query.trim()) {
                starts.add(0, entry)
                continue
            }
            val words = normalize(entry.keywords).split(' ')
            when {
                words.any { it.startsWith(q) } -> starts.add(entry)
                normalize(entry.keywords).contains(q) -> contains.add(entry)
            }
        }
        return (starts + contains).take(limit)
    }

    /** Persian-aware normalisation: Arabic ي/ك -> ی/ک, drop ZWNJ and Arabic diacritics, lowercase. */
    internal fun normalize(raw: String): String {
        val sb = StringBuilder(raw.length)
        for (ch in raw.trim().lowercase()) {
            when (ch) {
                '\u064A' -> sb.append('\u06CC')
                '\u0643' -> sb.append('\u06A9')
                '\u200C' -> Unit
                in '\u064B'..'\u065F' -> Unit
                else -> sb.append(ch)
            }
        }
        return sb.toString()
    }

    private fun parse(): Map<EmojiCategory, List<EmojiEntry>> {
        val result = LinkedHashMap<EmojiCategory, List<EmojiEntry>>()
        for ((category, block) in RAW) {
            result[category] = block.lineSequence()
                .map { it.trim() }
                .filter { it.isNotEmpty() && it.contains('|') }
                .map { line ->
                    val tone = line.startsWith("+")
                    val body = if (tone) line.substring(1) else line
                    val sep = body.indexOf('|')
                    EmojiEntry(body.substring(0, sep), body.substring(sep + 1).trim(), tone)
                }
                .toList()
        }
        return result
    }

    private val RAW: List<Pair<EmojiCategory, String>> = listOf(
        EmojiCategory.LOVE to """
❤️|heart love red red-heart قلب عشق دل قرمز
🧡|orange heart قلب نارنجی
💛|yellow heart قلب زرد
💚|green heart قلب سبز
💙|blue heart قلب آبی
💜|purple heart قلب بنفش
🖤|black heart قلب سیاه
🤍|white heart قلب سفید
🤎|brown heart قلب قهوه‌ای
💕|two hearts love دو قلب عشق
💞|revolving hearts قلب‌های چرخان
💓|beating heart heartbeat ضربان قلب تپش
💗|growing heart قلب بزرگ‌شونده
💖|sparkling heart قلب درخشان
💘|heart arrow cupid تیر عشق قلب
💝|heart gift ribbon قلب کادو هدیه
💟|heart decoration
❣️|heart exclamation
💔|broken heart قلب شکسته دلشکسته
❤️‍🔥|heart on fire قلب آتشین
❤️‍🩹|mending heart قلب التیام
😍|heart eyes love عاشق چشم قلبی
🥰|smiling face with hearts عاشق دوست داشتن
😘|blowing kiss بوس ماچ
😗|kissing
😚|kissing closed eyes
😙|kissing smiling
💋|kiss mark lips بوسه لب ماچ
💌|love letter نامه عاشقانه
💑|couple with heart زوج عاشق
💏|kiss couple بوسیدن زوج
👩‍❤️‍👨|couple heart زوج
🫶|heart hands دست قلب
🫂|hug people hugging بغل آغوش
🤗|hugging face hug بغل
💍|ring engagement حلقه نامزدی ازدواج
🌹|rose گل رز
🌷|tulip لاله
🌸|cherry blossom شکوفه
🎀|ribbon روبان
🧸|teddy bear خرس عروسکی
💐|bouquet دسته گل
🥂|clinking glasses cheers جشن
🍫|chocolate شکلات
🌙|moon ماه شب
✨|sparkles درخشش
💫|dizzy star ستاره
⭐|star ستاره
""",
        EmojiCategory.SMILEYS to """
😀|grinning face خندان
😃|smiley big eyes
😄|smile happy خوشحال خنده
😁|beaming grin
😆|laughing squint
😅|sweat smile عرق خنده
😂|joy tears laugh خنده اشک
🤣|rofl rolling laugh غش خنده
🙂|slight smile لبخند
🙃|upside down وارونه
😉|wink چشمک
😊|blush smile لبخند خجالت
😇|angel halo فرشته
🥲|smile tear
☺️|smiling face
😋|yum delicious خوشمزه
😛|tongue زبان
😜|wink tongue شیطنت
🤪|zany crazy دیوانه
😝|squint tongue
🤑|money face پول
🤭|giggle hand over mouth قهقهه
🤫|shush ساکت
🤔|thinking فکر
🤐|zipper mouth
🤨|raised eyebrow
😐|neutral بی‌تفاوت
😑|expressionless
😶|no mouth
😏|smirk پوزخند
😒|unamused بی‌حوصله
🙄|eye roll چشم‌غره
😬|grimace
🤥|lying pinocchio دروغ
😌|relieved آسوده
😔|pensive غمگین
😪|sleepy
🤤|drooling
😴|sleeping خواب
😷|mask ماسک
🤒|sick thermometer بیمار تب
🤕|bandage زخمی
🤢|nauseated حالت تهوع
🤮|vomit
🥵|hot گرم
🥶|cold سرد
🥴|woozy
😵|dizzy گیج
🤯|mind blown شوک
🤠|cowboy
🥳|party جشن تولد
🥸|disguise
😎|cool sunglasses باحال عینک
🤓|nerd
🧐|monocle
😕|confused گیج
😟|worried نگران
🙁|frown
☹️|frowning
😮|open mouth شگفت
😯|hushed
😲|astonished
😳|flushed خجالت
🥺|pleading التماس
🥹|holding back tears
😦|frowning open mouth
😧|anguished
😨|fearful ترس
😰|anxious sweat
😥|sad relieved
😢|cry اشک گریه
😭|sob گریه زیاد
😱|scream فریاد وحشت
😖|confounded
😣|persevere
😞|disappointed ناامید
😓|downcast sweat
😩|weary خسته
😫|tired
🥱|yawn خمیازه
😤|triumph steam
😡|angry عصبانی
😠|mad خشم
🤬|cursing
😈|devil imp شیطان
👿|angry devil
💀|skull جمجمه
💩|poop
🤡|clown دلقک
👻|ghost روح
👽|alien فضایی
🤖|robot ربات
🙈|see no evil میمون
🙉|hear no evil
🙊|speak no evil
😺|cat smile
😻|cat heart eyes
""",
        EmojiCategory.GESTURES to """
+👍|thumbs up like yes لایک موافق
+👎|thumbs down dislike مخالف
+👏|clap تشویق
+🙌|raised hands هورا
+👐|open hands
+🤲|palms up
+🙏|pray thanks please دعا لطفا ممنون
+🤝|handshake دست دادن قرار
+✌️|victory peace پیروزی
+🤞|crossed fingers
+🤟|love you gesture
+🤘|rock
+👌|ok perfect عالی
+🤌|pinched fingers
+🤏|pinch small
+👈|point left
+👉|point right
+👆|point up
+👇|point down
+☝️|index up
+✋|raised hand
+🖐️|splayed hand
+🖖|vulcan
+👋|wave سلام خداحافظ
+🤙|call me
+💪|flexed bicep muscle قوی عضله
+✍️|writing hand نوشتن
+🫰|finger heart
+🤳|selfie سلفی
+🫵|pointing at you
+🫳|palm down
+🫴|palm up
+👶|baby بچه نوزاد
+🧒|child کودک
+👦|boy پسر
+👧|girl دختر
+🧑|person
+👨|man مرد
+👩|woman زن
+🧔|beard ریش
+👴|old man
+👵|old woman
👀|eyes نگاه چشم
👅|tongue
👄|mouth لب
🧠|brain مغز
🦷|tooth دندان
""",
        EmojiCategory.NATURE to """
🐱|cat گربه
🐶|dog سگ
🐭|mouse موش
🐹|hamster
🐰|rabbit خرگوش
🦊|fox روباه
🐻|bear خرس
🐼|panda پاندا
🐨|koala
🐯|tiger ببر
🦁|lion شیر
🐮|cow گاو
🐷|pig خوک
🐸|frog قورباغه
🐵|monkey میمون
🐔|chicken مرغ
🐧|penguin پنگوئن
🐦|bird پرنده
🦆|duck اردک
🦉|owl جغد
🦄|unicorn تک‌شاخ
🐝|bee زنبور
🦋|butterfly پروانه
🐌|snail حلزون
🐢|turtle لاک‌پشت
🐍|snake مار
🐙|octopus
🐠|fish ماهی
🐬|dolphin دلفین
🐳|whale نهنگ
🦈|shark کوسه
🐘|elephant فیل
🦒|giraffe زرافه
🐴|horse اسب
🐑|sheep گوسفند
🐓|rooster خروس
🌻|sunflower آفتابگردان
🌺|hibiscus
🌼|blossom
🍀|clover شبدر
🌿|herb برگ
🌳|tree درخت
🌴|palm نخل
🌵|cactus کاکتوس
🍂|fallen leaves پاییز برگ
🍁|maple leaf
☀️|sun خورشید آفتاب
🌤️|sun cloud
⛅|partly cloudy
☁️|cloud ابر
🌧️|rain باران
⛈️|thunder storm رعد
❄️|snowflake برف
☃️|snowman آدم برفی
🌈|rainbow رنگین‌کمان
🔥|fire آتش
💧|droplet قطره
🌊|wave موج دریا
🌍|earth زمین
🌙|crescent moon ماه
🌟|glowing star
⚡|lightning برق
🌠|shooting star شهاب
""",
        EmojiCategory.FOOD to """
🍎|apple سیب
🍊|orange پرتقال
🍋|lemon لیمو
🍌|banana موز
🍉|watermelon هندوانه
🍇|grapes انگور
🍓|strawberry توت‌فرنگی
🍒|cherries گیلاس
🍑|peach هلو
🥭|mango انبه
🍍|pineapple آناناس
🥥|coconut نارگیل
🥝|kiwi کیوی
🍅|tomato گوجه
🥑|avocado آووکادو
🥕|carrot هویج
🌽|corn ذرت
🥔|potato سیب‌زمینی
🍞|bread نان
🥐|croissant
🧀|cheese پنیر
🥚|egg تخم‌مرغ
🍳|fried egg املت
🥞|pancakes
🥓|bacon
🍔|burger همبرگر
🍟|fries سیب‌زمینی سرخ‌کرده
🍕|pizza پیتزا
🌭|hot dog
🌮|taco
🍝|spaghetti پاستا
🍜|ramen noodles نودل
🍣|sushi سوشی
🍚|rice برنج پلو
🍛|curry
🍢|kebab کباب
🥙|wrap
🍰|cake کیک
🎂|birthday cake کیک تولد
🧁|cupcake
🍩|donut دونات
🍪|cookie کلوچه
🍦|ice cream بستنی
🍫|chocolate شکلات
🍬|candy آبنبات
🍭|lollipop
🍯|honey عسل
☕|coffee قهوه
🍵|tea چای
🧋|bubble tea
🥤|cup straw نوشابه
🍷|wine شراب
🍸|cocktail
🍹|tropical drink
🍺|beer
🥂|cheers clink
🍾|champagne
🥛|milk شیر
""",
        EmojiCategory.TRAVEL to """
🏠|house خانه
🏡|home garden
🏢|office
🏥|hospital بیمارستان
🏫|school مدرسه
🕌|mosque مسجد
⛪|church
🏰|castle قلعه
🗼|tower برج
🌉|bridge پل
🚗|car ماشین
🚕|taxi تاکسی
🚌|bus اتوبوس
🚑|ambulance آمبولانس
🚓|police
🏍️|motorcycle موتور
🚲|bicycle دوچرخه
🛵|scooter
🚆|train قطار
🚇|metro مترو
✈️|airplane هواپیما پرواز
🛫|takeoff
🚀|rocket موشک
🚁|helicopter
⛵|sailboat قایق
🚢|ship کشتی
⚓|anchor لنگر
🗺️|map نقشه
🧳|luggage چمدان سفر
🏖️|beach ساحل
🏝️|island جزیره
🏔️|mountain کوه
⛺|camping چادر
🌅|sunrise طلوع
🌇|sunset غروب
🌃|night city شب
🎡|ferris wheel
🎢|roller coaster
🎪|circus
📍|pin location مکان
""",
        EmojiCategory.OBJECTS to """
📱|phone موبایل گوشی
☎️|telephone تلفن
💻|laptop لپ‌تاپ
⌨️|keyboard
🖥️|desktop
📷|camera دوربین
📸|camera flash عکس
🎥|movie camera
🎬|clapper فیلم
📺|tv تلویزیون
🎧|headphones هدفون
🎤|microphone میکروفون
🎵|music note آهنگ موسیقی
🎶|notes
🎸|guitar گیتار
🎹|piano پیانو
🥁|drum
🎮|game controller بازی
🎲|dice تاس
🎯|dart هدف
⚽|soccer football فوتبال
🏀|basketball
🏐|volleyball والیبال
🎾|tennis
🏆|trophy جام
🥇|gold medal مدال
🎁|gift کادو هدیه
🎉|party popper جشن
🎊|confetti
🎈|balloon بادکنک
🎂|birthday تولد
📚|books کتاب
📖|open book
✏️|pencil مداد
📝|memo note یادداشت
📌|pushpin
📎|paperclip
✂️|scissors قیچی
🔑|key کلید
🔒|lock قفل
🔓|unlock
💡|bulb ایده لامپ
🔦|flashlight
🔔|bell زنگ
⏰|alarm clock ساعت زنگ
⌚|watch ساعت
⏳|hourglass
💰|money bag پول
💳|credit card کارت
🛒|shopping cart خرید
👗|dress لباس
👠|heel کفش
👜|handbag کیف
👓|glasses عینک
💄|lipstick رژ لب
💎|gem الماس
🧴|lotion
🛁|bath حمام
🛏️|bed تخت
🧹|broom جارو
💊|pill قرص
💉|syringe
🩺|stethoscope
""",
        EmojiCategory.SYMBOLS to """
💯|hundred صد
✅|check done تیک انجام شد
❌|cross no نه
❎|cross mark
⭕|circle
‼️|double exclamation
❗|exclamation
❓|question سوال
❔|white question
⚠️|warning هشدار
🚫|prohibited ممنوع
🔞|18 plus
♻️|recycle
➕|plus
➖|minus
➗|divide
✔️|check mark
➡️|right arrow
⬅️|left arrow
⬆️|up arrow
⬇️|down arrow
🔄|refresh
🔁|repeat
▶️|play
⏸️|pause
⏹️|stop
🔊|speaker sound صدا
🔇|mute
📶|signal
💤|zzz sleep خواب
💭|thought bubble فکر
💬|speech bubble پیام
🗯️|anger bubble
♾️|infinity بی‌نهایت
☮️|peace صلح
☯️|yin yang
🔱|trident
🇮🇷|iran ایران پرچم
🏳️‍🌈|rainbow flag
🏁|finish flag
🚩|red flag پرچم قرمز
♈|aries فروردین
♉|taurus اردیبهشت
♊|gemini خرداد
♋|cancer تیر
♌|leo مرداد
♍|virgo شهریور
♎|libra مهر
♏|scorpio آبان
♐|sagittarius آذر
♑|capricorn دی
♒|aquarius بهمن
♓|pisces اسفند
""",
    )
}

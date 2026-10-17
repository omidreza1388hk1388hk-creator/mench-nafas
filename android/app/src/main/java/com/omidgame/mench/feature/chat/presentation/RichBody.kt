package com.omidgame.mench.feature.chat.presentation

import java.net.URLDecoder
import java.net.URLEncoder

/**
 * Phase 2A — stickers and GIFs travel as ordinary TEXT messages whose body is a small versioned
 * marker, so there is no schema change and an old app version simply shows the marker text:
 *
 *   sticker:v1:<id>                              catalog sticker
 *   sticker:v1:c:<palette>:<emoji>:<caption>     custom sticker (emoji and caption percent-encoded)
 *   gif:v1:<giphyId>:<width>:<height>            GIF; bytes are fetched through our own server
 *
 * The backend recognises the same markers (messages/rich-body.ts) so push notifications say
 * "[sticker]" / "[GIF]". Pure Kotlin (java.net only), covered by JVM unit tests.
 */
sealed interface RichBody {
    data object Plain : RichBody
    data class Sticker(val spec: StickerSpec) : RichBody
    data class Gif(val id: String, val widthPx: Int, val heightPx: Int) : RichBody
    data class Poll(val pollId: String) : RichBody
    /** [liveUntilEpochMillis] != null marks a live location: the sender keeps editing the body until that time. */
    data class Location(val latitude: Double, val longitude: Double, val liveUntilEpochMillis: Long? = null) : RichBody
    data class Contact(val name: String, val phone: String) : RichBody

    /** Phase 3: a surprise message — shown as a gift until the receiver opens it. */
    data class Surprise(val text: String) : RichBody

    companion object {
        private const val STICKER_PREFIX = "sticker:v1:"
        private const val GIF_PREFIX = "gif:v1:"
        private const val POLL_PREFIX = "poll:v1:"
        private const val LOCATION_PREFIX = "loc:v1:"
        private const val CONTACT_PREFIX = "contact:v1:"
        private const val SURPRISE_PREFIX = "surprise:v1:"
        const val MAX_SURPRISE = 200
        const val MAX_CONTACT_NAME = 60
        private val PHONE_CHARS = Regex("^\\+?[0-9]{3,20}$")
        private val UUID_PATTERN = Regex("^[0-9a-fA-F]{8}-[0-9a-fA-F]{4}-[0-9a-fA-F]{4}-[0-9a-fA-F]{4}-[0-9a-fA-F]{12}$")
        private val GIF_ID = Regex("^[A-Za-z0-9]{5,32}$")

        fun parse(body: String?): RichBody {
            if (body == null) return Plain
            val text = body.trim()
            return when {
                text.startsWith(GIF_PREFIX) -> parseGif(text.removePrefix(GIF_PREFIX))
                text.startsWith(POLL_PREFIX) -> text.removePrefix(POLL_PREFIX).let { if (UUID_PATTERN.matches(it)) Poll(it) else Plain }
                text.startsWith(LOCATION_PREFIX) -> parseLocation(text.removePrefix(LOCATION_PREFIX))
                text.startsWith(CONTACT_PREFIX) -> parseContact(text.removePrefix(CONTACT_PREFIX))
                text.startsWith(SURPRISE_PREFIX) -> decode(text.removePrefix(SURPRISE_PREFIX))?.takeIf { it.isNotBlank() && it.length <= MAX_SURPRISE }?.let { Surprise(it) } ?: Plain
                text.startsWith(STICKER_PREFIX) -> parseSticker(text.removePrefix(STICKER_PREFIX))
                else -> Plain
            }
        }

        fun encodeSticker(sticker: CatalogSticker): String = STICKER_PREFIX + sticker.id

        /** Returns null when the emoji / palette is not acceptable (the caller keeps its dialog open). */
        fun encodeCustomSticker(emoji: String, caption: String, palette: Int): String? {
            val count = EmojiText.emojiOnlyCount(emoji)
            if (count < 1 || count > StickerCatalog.MAX_CUSTOM_EMOJI) return null
            if (!StickerCatalog.isValidPalette(palette)) return null
            return STICKER_PREFIX + "c:" + palette + ":" + encode(emoji.trim()) + ":" + encode(StickerCatalog.cleanCaption(caption))
        }

        fun encodeGif(id: String, widthPx: Int, heightPx: Int): String? {
            if (!GIF_ID.matches(id)) return null
            return GIF_PREFIX + id + ":" + widthPx.coerceIn(1, 4000) + ":" + heightPx.coerceIn(1, 4000)
        }

        /** Keeps digits and a leading '+' of a phone number (spaces, dashes, brackets dropped; Persian digits converted). */
        fun cleanPhone(raw: String): String {
            val ascii = RichText.normalizeDigits(raw.trim())
            val plus = ascii.startsWith("+")
            return (if (plus) "+" else "") + ascii.filter { it.isDigit() }
        }

        /** Null when the name is blank or the phone is not 3..20 digits. */
        /** Null when [text] is blank; longer text is cut to [MAX_SURPRISE] characters. */
        fun encodeSurprise(text: String): String? {
            val clean = text.trim().take(MAX_SURPRISE).trim()
            return if (clean.isEmpty()) null else SURPRISE_PREFIX + encode(clean)
        }

        fun encodeContact(name: String, phone: String): String? {
            val cleanName = name.replace('\n', ' ').replace('\r', ' ').replace('\t', ' ').trim().take(MAX_CONTACT_NAME).trim()
            val cleanedPhone = cleanPhone(phone)
            if (cleanName.isEmpty() || !PHONE_CHARS.matches(cleanedPhone)) return null
            return CONTACT_PREFIX + encode(cleanName) + ":" + encode(cleanedPhone)
        }

        fun encodePoll(pollId: String): String? = if (UUID_PATTERN.matches(pollId)) POLL_PREFIX + pollId else null

        /** Six decimals (~11 cm). Returns null for a coordinate outside the valid range or not finite. */
        fun encodeLocation(latitude: Double, longitude: Double): String? {
            if (!latitude.isFinite() || !longitude.isFinite() || latitude !in -90.0..90.0 || longitude !in -180.0..180.0) return null
            return LOCATION_PREFIX + "%.6f:%.6f".format(java.util.Locale.US, latitude, longitude)
        }

        /** A location the sender keeps updating until [untilEpochMillis] (Phase 2A live location). */
        fun encodeLiveLocation(latitude: Double, longitude: Double, untilEpochMillis: Long): String? =
            encodeLocation(latitude, longitude)?.let { it + ":" + untilEpochMillis }

        /** Short human label for places that cannot draw the sticker/GIF (reply quote, notifications, chat list). */
        fun kindOf(body: String?): Kind? = when (parse(body)) {
            is Sticker -> Kind.STICKER
            is Gif -> Kind.GIF
            is Poll -> Kind.POLL
            is Location -> Kind.LOCATION
            is Contact -> Kind.CONTACT
            is Surprise -> Kind.SURPRISE
            Plain -> null
        }

        private fun parseContact(rest: String): RichBody {
            val parts = rest.split(':')
            if (parts.size != 2) return Plain
            val name = decode(parts[0])?.trim().orEmpty()
            val phone = decode(parts[1])?.trim().orEmpty()
            if (name.isEmpty() || name.length > MAX_CONTACT_NAME || !PHONE_CHARS.matches(phone)) return Plain
            return Contact(name, phone)
        }

        private fun parseLocation(rest: String): RichBody {
            val parts = rest.split(':')
            if (parts.size != 2 && parts.size != 3) return Plain
            val lat = parts[0].toDoubleOrNull() ?: return Plain
            val lon = parts[1].toDoubleOrNull() ?: return Plain
            if (!lat.isFinite() || !lon.isFinite() || lat !in -90.0..90.0 || lon !in -180.0..180.0) return Plain
            val until = if (parts.size == 3) (parts[2].toLongOrNull() ?: return Plain) else null
            return Location(lat, lon, until)
        }

        private fun parseGif(rest: String): RichBody {
            val parts = rest.split(':')
            if (parts.size != 3 || !GIF_ID.matches(parts[0])) return Plain
            val w = parts[1].toIntOrNull() ?: return Plain
            val h = parts[2].toIntOrNull() ?: return Plain
            if (w !in 1..4000 || h !in 1..4000) return Plain
            return Gif(parts[0], w, h)
        }

        private fun parseSticker(rest: String): RichBody {
            if (rest.startsWith("c:")) {
                val parts = rest.removePrefix("c:").split(':')
                if (parts.size != 3) return Plain
                val palette = parts[0].toIntOrNull()?.takeIf { StickerCatalog.isValidPalette(it) } ?: return Plain
                val emoji = decode(parts[1]) ?: return Plain
                val caption = decode(parts[2]) ?: return Plain
                val count = EmojiText.emojiOnlyCount(emoji)
                if (count < 1 || count > StickerCatalog.MAX_CUSTOM_EMOJI) return Plain
                val clean = StickerCatalog.cleanCaption(caption)
                return Sticker(StickerSpec(emoji.trim(), clean, clean, palette, null))
            }
            val sticker = StickerCatalog.find(rest) ?: return Plain
            return Sticker(StickerCatalog.spec(sticker))
        }

        // URLEncoder uses '+' for space and leaves '*' alone; both are rewritten so the marker only
        // contains characters the server-side pattern accepts.
        private fun encode(value: String): String =
            URLEncoder.encode(value, "UTF-8").replace("+", "%20").replace("*", "%2A")

        private fun decode(value: String): String? =
            try {
                URLDecoder.decode(value, "UTF-8")
            } catch (e: IllegalArgumentException) {
                null
            }
    }

    enum class Kind { STICKER, GIF, POLL, LOCATION, CONTACT, SURPRISE }
}

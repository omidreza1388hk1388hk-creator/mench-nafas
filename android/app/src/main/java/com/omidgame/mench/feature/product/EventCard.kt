package com.omidgame.mench.feature.product

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.omidgame.mench.R
import com.omidgame.mench.core.ui.theme.MenchBrand
import com.omidgame.mench.core.ui.theme.MenchDigits
import org.json.JSONObject
import java.time.Instant
import java.time.ZoneId
import java.time.ZonedDateTime
import java.time.format.DateTimeFormatter

/** A calendar event shared into a conversation. Travels as an ordinary text message with a marker prefix. */
data class SharedEvent(val title: String, val startIso: String?, val location: String?, val note: String?, val participants: List<String> = emptyList())

/**
 * Encodes/decodes events inside a normal TEXT message body, so no server or schema change is needed
 * and old clients simply show readable text. Format: "<marker>{json}\n<human readable fallback>".
 */
object EventCodec {
    private const val MARKER = "\u2063MENCH-EVENT\u2063"

    fun encode(e: SharedEvent, fallbackLine: String): String {
        val json = JSONObject().put("t", e.title).put("s", e.startIso ?: "").put("l", e.location ?: "").put("n", e.note ?: "").put("p", org.json.JSONArray(e.participants))
        return MARKER + json.toString() + "\n" + fallbackLine
    }

    fun decode(body: String?): SharedEvent? {
        if (body == null || !body.startsWith(MARKER)) return null
        val jsonText = body.removePrefix(MARKER).substringBefore('\n')
        return runCatching {
            val j = JSONObject(jsonText)
            SharedEvent(j.getString("t"), j.optString("s").ifBlank { null }, j.optString("l").ifBlank { null }, j.optString("n").ifBlank { null },
                j.optJSONArray("p")?.let { a -> (0 until a.length()).map { i -> a.optString(i) }.filter { it.isNotBlank() } } ?: emptyList())
        }.getOrNull()
    }

    fun fallback(e: SharedEvent): String {
        val whenText = e.startIso?.let { format(it) }
        return listOfNotNull(e.title, whenText, e.location).joinToString(" · ")
    }

    fun format(iso: String): String =
        runCatching { ZonedDateTime.ofInstant(Instant.parse(iso), ZoneId.systemDefault()).format(DateTimeFormatter.ofPattern("yyyy/MM/dd HH:mm")) }.getOrDefault(iso)
}

/** MENCH-styled event card shown in place of the plain text bubble. */
@Composable
fun EventCard(event: SharedEvent, modifier: Modifier = Modifier) {
    Column(
        modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(16.dp))
            .background(MenchBrand.heroBrush())
            .padding(14.dp),
    ) {
        Text(stringResource(R.string.event_card_label), style = MaterialTheme.typography.labelSmall, color = Color.White.copy(alpha = 0.8f))
        Text(event.title, style = MaterialTheme.typography.titleMedium, color = Color.White)
        event.startIso?.let { Text(MenchDigits.toPersian(EventCodec.format(it)), style = MaterialTheme.typography.bodyMedium, color = Color.White) }
        event.location?.let { Text(it, style = MaterialTheme.typography.bodySmall, color = Color.White.copy(alpha = 0.9f)) }
        if (event.participants.isNotEmpty()) {
            Text(stringResource(R.string.event_card_with, event.participants.joinToString("، ")), style = MaterialTheme.typography.bodySmall, color = Color.White.copy(alpha = 0.9f))
        }
        event.note?.let { Text(it, style = MaterialTheme.typography.bodySmall, color = Color.White.copy(alpha = 0.9f), modifier = Modifier.padding(top = 4.dp)) }
    }
}

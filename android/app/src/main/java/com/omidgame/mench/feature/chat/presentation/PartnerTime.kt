package com.omidgame.mench.feature.chat.presentation

import android.content.Context
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.omidgame.mench.R
import com.omidgame.mench.core.ui.components.MenchDialog
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import kotlin.math.abs
import kotlinx.coroutines.delay

/**
 * Phase 3A — distance and time (the time half): the local time where your partner lives and how far it is from yours.
 * The partner's city is picked by hand and kept on this phone only; no location is ever read.
 */

/** Cities offered in the picker, as IANA zone ids. */
internal val partnerZones = listOf(
    "Asia/Tehran", "Asia/Istanbul", "Asia/Dubai", "Asia/Baku", "Asia/Kabul", "Asia/Karachi", "Asia/Kolkata",
    "Asia/Shanghai", "Asia/Tokyo", "Asia/Seoul", "Australia/Sydney", "Europe/London", "Europe/Paris", "Europe/Berlin",
    "Europe/Rome", "Europe/Madrid", "Europe/Stockholm", "Europe/Moscow", "America/Toronto", "America/New_York",
    "America/Chicago", "America/Los_Angeles", "America/Vancouver", "America/Sao_Paulo",
)

private const val PREFS = "partner_time"
private const val KEY_ZONE = "zone_id"

fun savedPartnerZone(context: Context): String? =
    context.applicationContext.getSharedPreferences(PREFS, Context.MODE_PRIVATE).getString(KEY_ZONE, null)

fun savePartnerZone(context: Context, zoneId: String?) {
    context.applicationContext.getSharedPreferences(PREFS, Context.MODE_PRIVATE).edit().putString(KEY_ZONE, zoneId).apply()
}

/** "Tehran" for "Asia/Tehran", "New York" for "America/New_York". */
internal fun cityName(zoneId: String): String = zoneId.substringAfterLast('/').replace('_', ' ')

/** Offset of [other] relative to [self] at [at], in whole minutes (positive = the other place is ahead). */
internal fun offsetMinutes(self: ZoneId, other: ZoneId, at: Instant): Int =
    (other.rules.getOffset(at).totalSeconds - self.rules.getOffset(at).totalSeconds) / 60

/** "3" or "3:30" for a number of minutes (absolute value). */
internal fun formatHoursMinutes(minutes: Int): String {
    val m = abs(minutes)
    return if (m % 60 == 0) (m / 60).toString() else "${m / 60}:${(m % 60).toString().padStart(2, '0')}"
}

/** The tile on the couple profile: partner-city time, difference from your own, tap to change the city. */
@Composable
fun PartnerTimeTile() {
    val context = LocalContext.current
    var zoneId by remember { mutableStateOf(savedPartnerZone(context)) }
    var picking by remember { mutableStateOf(false) }
    var now by remember { mutableStateOf(Instant.now()) }
    LaunchedEffect(Unit) {
        while (true) {
            now = Instant.now()
            delay(20_000L)
        }
    }
    val zone = zoneId?.let { runCatching { ZoneId.of(it) }.getOrNull() }
    val text = if (zone == null) {
        stringResource(R.string.partner_time_set)
    } else {
        val diff = offsetMinutes(ZoneId.systemDefault(), zone, now)
        val clock = DateTimeFormatter.ofPattern("HH:mm").format(now.atZone(zone))
        val city = cityName(zone.id)
        when {
            diff == 0 -> stringResource(R.string.partner_time_same, city, clock)
            diff > 0 -> stringResource(R.string.partner_time_ahead, city, clock, formatHoursMinutes(diff))
            else -> stringResource(R.string.partner_time_behind, city, clock, formatHoursMinutes(diff))
        }
    }
    ProfileInfoTile("🕒", text, onClick = { picking = true })
    if (picking) {
        MenchDialog(
            onDismissRequest = { picking = false },
            confirmButton = { TextButton(onClick = { picking = false }) { Text(stringResource(R.string.us_close)) } },
            dismissButton = if (zone != null) ({
                TextButton(onClick = { savePartnerZone(context, null); zoneId = null; picking = false }) { Text(stringResource(R.string.counters_delete)) }
            }) else null,
            title = { Text("🕒  " + stringResource(R.string.partner_time_title)) },
            text = {
                LazyColumn(verticalArrangement = Arrangement.spacedBy(2.dp)) {
                    items(partnerZones) { id ->
                        Text(
                            text = cityName(id),
                            style = MaterialTheme.typography.bodyLarge,
                            color = if (id == zoneId) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurface,
                            modifier = Modifier
                                .fillMaxWidth()
                                .clickable { savePartnerZone(context, id); zoneId = id; picking = false }
                                .padding(vertical = 10.dp),
                        )
                    }
                }
            },
        )
    }
}

/** A column helper so callers can place a tile with a click action; see CoupleProfile.kt for the plain tile. */
@Composable
internal fun ProfileInfoTile(emoji: String, text: String, onClick: () -> Unit) {
    androidx.compose.foundation.layout.Row(
        modifier = Modifier
            .fillMaxWidth()
            .glass(corner = 16.dp, tint = MaterialTheme.colorScheme.primary)
            .clickable(onClick = onClick)
            .padding(14.dp),
        horizontalArrangement = Arrangement.spacedBy(12.dp),
        verticalAlignment = androidx.compose.ui.Alignment.CenterVertically,
    ) {
        Text(emoji, style = MaterialTheme.typography.titleLarge)
        Text(text, style = MaterialTheme.typography.bodyLarge)
    }
}

package com.omidgame.mench.feature.product

import android.text.format.Formatter
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.FilterChip
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.ViewModel
import com.omidgame.mench.R
import com.omidgame.mench.core.network.DayUsage
import com.omidgame.mench.core.network.UsageTracker
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject

@HiltViewModel
class DataUsageViewModel @Inject constructor(
    val tracker: UsageTracker,
    private val conversationDao: com.omidgame.mench.core.database.ConversationDao,
) : ViewModel() {
    /** Conversation id -> display name, for labelling the per-chat list. Falls back to a generic label if the chat is gone. */
    suspend fun chatNames(): Map<String, String> = runCatching { conversationDao.getAllOnce() }.getOrDefault(emptyList())
        .associate { c -> c.id to (c.title ?: c.otherUserDisplayName ?: c.otherUserPhoneE164 ?: "") }
}

/** Network usage dashboard (Phase 1D.5): totals, Wi-Fi vs mobile, media vs API, per-day bars, 7/30/90 day ranges. */
@Composable
fun DataUsageScreen(onBack: () -> Unit, viewModel: DataUsageViewModel = hiltViewModel()) {
    val context = LocalContext.current
    var days by remember { mutableIntStateOf(7) }
    var refresh by remember { mutableIntStateOf(0) }
    val usage: List<DayUsage> = remember(days, refresh) { viewModel.tracker.lastDays(days) }
    val wifi = usage.sumOf { it.wifiBytes }
    val mobile = usage.sumOf { it.mobileBytes }
    val up = usage.sumOf { it.uploadBytes }
    val down = usage.sumOf { it.downloadBytes }
    val media = usage.sumOf { it.mediaBytes }
    val total = wifi + mobile
    val perChat = remember(days, refresh) { viewModel.tracker.byChat(days).take(8) }
    var names by remember { androidx.compose.runtime.mutableStateOf<Map<String, String>>(emptyMap()) }
    androidx.compose.runtime.LaunchedEffect(refresh) { names = viewModel.chatNames() }
    fun fmt(b: Long) = Formatter.formatShortFileSize(context, b)

    ProductScaffold(title = stringResource(R.string.usage_title), onBack = onBack) { padding ->
        Column(Modifier.fillMaxSize().padding(padding).verticalScroll(rememberScrollState()).padding(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                listOf(7, 30, 90).forEach { d -> FilterChip(selected = days == d, onClick = { days = d }, label = { Text(stringResource(R.string.usage_days, d)) }) }
            }
            Text(stringResource(R.string.usage_total, fmt(total)), style = MaterialTheme.typography.headlineSmall)
            Text(stringResource(R.string.usage_estimate_note), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)

            val wifiColor = MaterialTheme.colorScheme.primary
            val mobileColor = MaterialTheme.colorScheme.tertiary
            Canvas(Modifier.fillMaxWidth().height(140.dp)) {
                val peak = usage.maxOfOrNull { it.wifiBytes + it.mobileBytes }?.coerceAtLeast(1L) ?: 1L
                val gap = 4.dp.toPx()
                val barW = (size.width - gap * (usage.size - 1)) / usage.size.coerceAtLeast(1)
                usage.forEachIndexed { i, d ->
                    val x = i * (barW + gap)
                    val hWifi = size.height * d.wifiBytes / peak
                    val hMobile = size.height * d.mobileBytes / peak
                    drawRect(wifiColor, Offset(x, size.height - hWifi), Size(barW, hWifi))
                    drawRect(mobileColor, Offset(x, size.height - hWifi - hMobile), Size(barW, hMobile))
                }
            }
            Text(stringResource(R.string.usage_legend), style = MaterialTheme.typography.labelSmall)

            Text(stringResource(R.string.usage_wifi, fmt(wifi)))
            Text(stringResource(R.string.usage_mobile, fmt(mobile)))
            Text(stringResource(R.string.usage_media, fmt(media)))
            Text(stringResource(R.string.usage_sent, fmt(up)))
            Text(stringResource(R.string.usage_received, fmt(down)))

            if (perChat.isNotEmpty()) {
                Text(stringResource(R.string.usage_by_chat), style = MaterialTheme.typography.titleMedium, modifier = Modifier.padding(top = 12.dp))
                val topValue = perChat.first().second.coerceAtLeast(1L)
                perChat.forEach { (id, bytes) ->
                    val label = names[id]?.takeIf { it.isNotBlank() } ?: stringResource(R.string.usage_chat_unknown)
                    Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
                        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                            Text(label, maxLines = 1, modifier = Modifier.weight(1f))
                            Text(fmt(bytes), style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        }
                        val barColor = MaterialTheme.colorScheme.primary
                        val trackColor = MaterialTheme.colorScheme.surfaceVariant
                        Canvas(Modifier.fillMaxWidth().height(6.dp)) {
                            drawRect(trackColor, Offset.Zero, Size(size.width, size.height))
                            drawRect(barColor, Offset.Zero, Size(size.width * bytes / topValue, size.height))
                        }
                    }
                }
                Text(stringResource(R.string.usage_by_chat_note), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
            OutlinedButton(onClick = { viewModel.tracker.reset(); refresh++ }, modifier = Modifier.fillMaxWidth().padding(top = 8.dp)) {
                Text(stringResource(R.string.usage_reset))
            }
        }
    }
}

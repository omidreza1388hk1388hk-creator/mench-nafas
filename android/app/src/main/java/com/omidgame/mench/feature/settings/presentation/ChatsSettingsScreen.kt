package com.omidgame.mench.feature.settings.presentation

import android.widget.Toast
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.background
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.omidgame.mench.R
import com.omidgame.mench.core.ui.components.MenchSheet
import com.omidgame.mench.core.ui.settings.ChoiceOption
import com.omidgame.mench.core.ui.settings.ConfirmDialog
import com.omidgame.mench.core.ui.settings.InfoBanner
import com.omidgame.mench.core.ui.settings.SettingsRow
import com.omidgame.mench.core.ui.settings.SettingsScaffold
import com.omidgame.mench.core.ui.settings.SettingsSection
import com.omidgame.mench.core.ui.settings.SingleChoiceRow
import com.omidgame.mench.core.ui.settings.SliderRow
import com.omidgame.mench.core.ui.settings.SwitchRow
import com.omidgame.mench.feature.chat.presentation.ChatPrefs
import com.omidgame.mench.feature.chat.presentation.ChatSettingsStore
import com.omidgame.mench.feature.chat.presentation.rememberChatSettings
import com.omidgame.mench.feature.settings.domain.ChatSettingsRules
import com.omidgame.mench.feature.settings.domain.PhotoQuality
import com.omidgame.mench.feature.settings.domain.SwipeReplyDirection

private val EMOJI_CHOICES = listOf(
    "\uD83D\uDC4D", "\u2764\uFE0F", "\uD83D\uDE0D", "\uD83E\uDD70", "\uD83D\uDE02", "\uD83D\uDE2E", "\uD83D\uDE22", "\uD83D\uDE4F",
    "\uD83D\uDD25", "\uD83D\uDC4F", "\uD83D\uDE18", "\uD83D\uDE0A", "\uD83E\uDD7A", "\uD83D\uDE2D", "\uD83D\uDE21", "\uD83C\uDF89",
    "\uD83D\uDC95", "\uD83D\uDE0E", "\uD83E\uDD23", "\uD83D\uDE44", "\uD83D\uDCAF", "\uD83D\uDE4C", "\uD83D\uDE0B", "\uD83D\uDC4B",
)
private val DOUBLE_TAP_CHOICES = listOf("\u2764\uFE0F", "\uD83D\uDC4D", "\uD83D\uDE02", "\uD83D\uDE0D", "\uD83D\uDD25", "\uD83E\uDD70")

/** Phase 2 "Chats": typography, typing, reactions, behaviour, photo defaults and drafts. Every row changes real behaviour. */
@Composable
fun ChatsSettingsScreen(
    onBack: () -> Unit,
    onOpenChat: (String) -> Unit,
    viewModel: ChatsSettingsViewModel = hiltViewModel(),
) {
    val context = LocalContext.current
    val s = rememberChatSettings()
    val state by viewModel.state.collectAsStateWithLifecycle()
    var doubleTap by remember { mutableStateOf(ChatPrefs.doubleTapReaction(context)) }
    var showReactions by remember { mutableStateOf(false) }
    var confirmDrafts by remember { mutableStateOf(false) }
    var showChats by remember { mutableStateOf(false) }
    val drafted = stringResource(R.string.sx_c_clear_drafts_done)

    SettingsScaffold(title = stringResource(R.string.sx_c_title), onBack = onBack) {
        SettingsSection(title = stringResource(R.string.sx_c_typography)) {
            SliderRow(
                title = stringResource(R.string.sx_c_text_size),
                subtitle = stringResource(R.string.sx_c_text_size_sub),
                value = s.messageTextScale,
                onValueChange = { ChatSettingsStore.setTextScale(context, it) },
                valueRange = ChatSettingsRules.MIN_TEXT_SCALE..ChatSettingsRules.MAX_TEXT_SCALE,
                valueLabel = "${(s.messageTextScale * 100).toInt()}%",
            )
            SliderRow(
                title = stringResource(R.string.sx_c_line_spacing),
                value = s.lineSpacing,
                onValueChange = { ChatSettingsStore.setLineSpacing(context, it) },
                valueRange = ChatSettingsRules.MIN_LINE_SPACING..ChatSettingsRules.MAX_LINE_SPACING,
                valueLabel = "${(s.lineSpacing * 100).toInt()}%",
            )
            Text(
                text = stringResource(R.string.sx_c_preview),
                style = TextStyle(fontSize = 16.sp * s.messageTextScale, lineHeight = 20.sp * s.messageTextScale * s.lineSpacing),
                modifier = Modifier.padding(14.dp),
            )
        }

        SettingsSection(title = stringResource(R.string.sx_c_input)) {
            SwitchRow(
                title = stringResource(R.string.sx_c_enter_sends),
                subtitle = stringResource(R.string.sx_c_enter_sends_sub),
                checked = s.enterSends,
                onCheckedChange = { ChatSettingsStore.setEnterSends(context, it) },
            )
            SwitchRow(
                title = stringResource(R.string.sx_c_auto_cap),
                checked = s.autoCapitalize,
                onCheckedChange = { ChatSettingsStore.setAutoCapitalize(context, it) },
            )
            SwitchRow(
                title = stringResource(R.string.sx_c_spell),
                checked = s.spellCheck,
                onCheckedChange = { ChatSettingsStore.setSpellCheck(context, it) },
            )
            SwitchRow(
                title = stringResource(R.string.sx_c_link_previews),
                subtitle = stringResource(R.string.sx_c_link_previews_sub),
                checked = s.linkPreviews,
                onCheckedChange = { ChatSettingsStore.setLinkPreviews(context, it) },
            )
        }

        SettingsSection(title = stringResource(R.string.sx_c_reactions)) {
            SettingsRow(
                title = stringResource(R.string.sx_c_quick_reactions),
                subtitle = stringResource(R.string.sx_c_quick_reactions_sub),
                onClick = { showReactions = true },
                trailing = { Text(s.quickReactions.take(6).joinToString(" "), style = MaterialTheme.typography.titleMedium) },
            )
            SingleChoiceRow(
                title = stringResource(R.string.sx_c_double_tap),
                options = (DOUBLE_TAP_CHOICES + doubleTap).distinct().map { ChoiceOption(it, it) },
                selected = doubleTap,
                onSelected = { doubleTap = it; ChatPrefs.setDoubleTapReaction(context, it) },
            )
        }

        SettingsSection(title = stringResource(R.string.sx_c_behaviour)) {
            SingleChoiceRow(
                title = stringResource(R.string.sx_c_swipe),
                options = listOf(
                    ChoiceOption(SwipeReplyDirection.ANY, stringResource(R.string.sx_c_swipe_any)),
                    ChoiceOption(SwipeReplyDirection.START, stringResource(R.string.sx_c_swipe_start)),
                    ChoiceOption(SwipeReplyDirection.END, stringResource(R.string.sx_c_swipe_end)),
                ),
                selected = s.swipeReply,
                onSelected = { ChatSettingsStore.setSwipeReply(context, it) },
            )
            SwitchRow(
                title = stringResource(R.string.sx_c_keep_archived),
                subtitle = stringResource(R.string.sx_c_keep_archived_sub),
                checked = s.keepArchived,
                onCheckedChange = { ChatSettingsStore.setKeepArchived(context, it) },
            )
        }

        SettingsSection(title = stringResource(R.string.sx_c_media)) {
            SingleChoiceRow(
                title = stringResource(R.string.sx_c_photo_quality),
                options = listOf(
                    ChoiceOption(PhotoQuality.COMPRESSED, stringResource(R.string.sx_c_photo_compressed)),
                    ChoiceOption(PhotoQuality.ORIGINAL, stringResource(R.string.sx_c_photo_original)),
                ),
                selected = s.photoQuality,
                onSelected = { ChatSettingsStore.setPhotoQuality(context, it) },
            )
            SwitchRow(
                title = stringResource(R.string.sx_c_strip_location),
                subtitle = stringResource(R.string.sx_c_strip_location_sub),
                checked = s.stripLocation,
                onCheckedChange = { ChatSettingsStore.setStripLocation(context, it) },
            )
            SwitchRow(
                title = stringResource(R.string.sx_c_view_once),
                checked = s.viewOnceDefault,
                onCheckedChange = { ChatSettingsStore.setViewOnceDefault(context, it) },
            )
        }

        SettingsSection(title = stringResource(R.string.sx_c_cleanup)) {
            SettingsRow(
                title = stringResource(R.string.sx_c_clear_drafts),
                subtitle = stringResource(R.string.sx_c_clear_drafts_count, state.draftCount),
                onClick = if (state.draftCount > 0) ({ confirmDrafts = true }) else null,
            )
            SettingsRow(
                title = stringResource(R.string.sx_c_open_chat_title),
                subtitle = stringResource(R.string.sx_c_open_chat),
                onClick = { showChats = true },
            )
        }
    }

    if (confirmDrafts) {
        ConfirmDialog(
            title = stringResource(R.string.sx_c_clear_drafts),
            message = stringResource(R.string.sx_c_clear_drafts_sub),
            confirmLabel = stringResource(R.string.sx_reset_confirm),
            destructive = true,
            onConfirm = {
                confirmDrafts = false
                viewModel.clearDrafts()
                Toast.makeText(context, drafted, Toast.LENGTH_SHORT).show()
            },
            onDismiss = { confirmDrafts = false },
        )
    }

    if (showReactions) {
        ReactionPickerSheet(
            selected = s.quickReactions.take(ChatSettingsRules.MAX_QUICK_REACTIONS),
            onChange = { ChatSettingsStore.setQuickReactions(context, it) },
            onDismiss = { showReactions = false },
        )
    }

    if (showChats) {
        MenchSheet(onDismissRequest = { showChats = false }) {
            Text(
                stringResource(R.string.sx_c_open_chat_title),
                style = MaterialTheme.typography.titleMedium,
                modifier = Modifier.padding(horizontal = 20.dp, vertical = 8.dp),
            )
            if (state.chats.isEmpty()) {
                Text(stringResource(R.string.sx_c_open_chat_empty), modifier = Modifier.padding(20.dp))
            }
            Box(Modifier.verticalScroll(rememberScrollState())) {
                androidx.compose.foundation.layout.Column {
                    state.chats.forEach { chat ->
                        Text(
                            text = chat.name.ifBlank { stringResource(R.string.sx_hub_chat_fallback) },
                            style = MaterialTheme.typography.bodyLarge,
                            modifier = Modifier
                                .fillMaxWidth()
                                .heightIn(min = 56.dp)
                                .clickable(role = Role.Button) { showChats = false; onOpenChat(chat.id) }
                                .padding(horizontal = 20.dp, vertical = 16.dp),
                        )
                    }
                }
            }
        }
    }
}

@OptIn(androidx.compose.foundation.layout.ExperimentalLayoutApi::class)
@Composable
private fun ReactionPickerSheet(selected: List<String>, onChange: (List<String>) -> Unit, onDismiss: () -> Unit) {
    var current by remember { mutableStateOf(selected) }
    MenchSheet(onDismissRequest = onDismiss) {
        Text(
            stringResource(R.string.sx_c_quick_reactions),
            style = MaterialTheme.typography.titleMedium,
            modifier = Modifier.padding(horizontal = 20.dp, vertical = 8.dp),
        )
        if (current.size >= ChatSettingsRules.MAX_QUICK_REACTIONS) {
            InfoBanner(text = stringResource(R.string.sx_c_quick_reactions_max), modifier = Modifier.padding(horizontal = 16.dp))
        }
        FlowRow(
            modifier = Modifier.fillMaxWidth().padding(16.dp),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            EMOJI_CHOICES.forEach { emoji ->
                val on = emoji in current
                Box(
                    modifier = Modifier
                        .size(52.dp)
                        .clip(RoundedCornerShape(14.dp))
                        .background(if (on) MaterialTheme.colorScheme.primaryContainer else MaterialTheme.colorScheme.surfaceContainerHigh)
                        .semantics { contentDescription = emoji }
                        .clickable(role = Role.Checkbox) {
                            current = ChatSettingsRules.toggleReaction(current, emoji)
                            onChange(current)
                        },
                    contentAlignment = Alignment.Center,
                ) { Text(emoji, fontSize = 26.sp) }
            }
        }
        Button(onClick = onDismiss, modifier = Modifier.fillMaxWidth().padding(horizontal = 20.dp, vertical = 12.dp).heightIn(min = 48.dp)) {
            Text(stringResource(R.string.sx_done))
        }
    }
}

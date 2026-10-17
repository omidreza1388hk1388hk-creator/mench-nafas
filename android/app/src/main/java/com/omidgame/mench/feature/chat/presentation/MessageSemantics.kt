package com.omidgame.mench.feature.chat.presentation

import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.res.stringResource
import com.omidgame.mench.R
import com.omidgame.mench.feature.chat.domain.DomainMessageKind
import com.omidgame.mench.feature.chat.domain.DomainMessageState
import com.omidgame.mench.feature.chat.domain.Message
import java.text.DateFormat
import java.util.Date

/**
 * TalkBack text for one message bubble: "sender, content, time, status" (Phase 4A).
 * Status is only announced for your own messages; a deleted message reads as the
 * localized "deleted" placeholder instead of its old body.
 */
@Composable
internal fun messageA11yDescription(message: Message, senderLabel: String?): String {
    val who = senderLabel ?: if (message.isOwn) stringResource(R.string.a11y_you) else ""
    val deleted = message.deletedAtEpochMillis != null

    val kindLabel: String? = if (deleted) {
        null
    } else {
        when (message.kind) {
            DomainMessageKind.IMAGE -> stringResource(R.string.a11y_kind_image)
            DomainMessageKind.FILE -> stringResource(R.string.a11y_kind_file)
            DomainMessageKind.AUDIO -> stringResource(R.string.a11y_kind_audio)
            DomainMessageKind.VIDEO -> stringResource(R.string.a11y_kind_video)
            DomainMessageKind.TEXT -> null
        }
    }
    val content = if (deleted) {
        stringResource(R.string.chat_message_deleted)
    } else {
        listOfNotNull(kindLabel, message.body?.takeIf { it.isNotBlank() }).joinToString(", ")
    }

    val time = remember(message.createdAtEpochMillis) {
        DateFormat.getTimeInstance(DateFormat.SHORT).format(Date(message.createdAtEpochMillis))
    }

    val status = if (message.isOwn && !deleted) {
        stringResource(
            when (message.state) {
                DomainMessageState.PENDING, DomainMessageState.SENDING -> R.string.a11y_state_sending
                DomainMessageState.SENT -> R.string.a11y_state_sent
                DomainMessageState.DELIVERED -> R.string.a11y_state_delivered
                DomainMessageState.READ -> R.string.a11y_state_read
                DomainMessageState.FAILED -> R.string.a11y_state_failed
            },
        )
    } else {
        ""
    }

    return listOf(who, content, time, status).filter { it.isNotBlank() }.joinToString(", ")
}

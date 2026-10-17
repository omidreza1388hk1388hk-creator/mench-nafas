package com.omidgame.mench.feature.chat.presentation

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.omidgame.mench.R
import com.omidgame.mench.core.ui.components.MenchSheet
import com.omidgame.mench.core.ui.components.UserAvatar
import com.omidgame.mench.feature.chat.domain.ReactionSummary

/** One row of the "who reacted" list. [userId] is null only for synthetic rows. */
data class Reactor(val userId: String, val name: String, val emoji: String, val isMe: Boolean)

/**
 * Flattens a message's reactions into one row per person, resolving names with [nameOf]
 * (the caller knows group member names / the other person's name; unknown ids get [unknownName]).
 * Me first, then everyone else in reaction order. Pure, unit-tested.
 */
internal fun buildReactors(
    reactions: List<ReactionSummary>,
    selfUserId: String?,
    nameOf: (String) -> String?,
    selfName: String,
    unknownName: String,
): List<Reactor> {
    val rows = reactions.flatMap { r ->
        r.userIds.map { id ->
            val isMe = id == selfUserId
            Reactor(id, if (isMe) selfName else (nameOf(id) ?: unknownName), r.emoji, isMe)
        }
    }
    return rows.sortedByDescending { it.isMe }
}

/** Phase 2A — bottom sheet listing who reacted with what; tapping your own row removes your reaction. */
@Composable
fun ReactionDetailsSheet(
    reactors: List<Reactor>,
    onRemoveMine: () -> Unit,
    onDismiss: () -> Unit,
) {
    MenchSheet(onDismissRequest = onDismiss) {
        Column(modifier = Modifier.padding(bottom = 24.dp)) {
            Text(
                text = stringResource(R.string.reaction_details_title, reactors.size),
                style = MaterialTheme.typography.titleMedium,
                modifier = Modifier.padding(horizontal = 24.dp, vertical = 8.dp),
            )
            LazyColumn {
                items(reactors, key = { it.userId + it.emoji }) { reactor ->
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable(enabled = reactor.isMe) {
                                onRemoveMine()
                                onDismiss()
                            }
                            .padding(horizontal = 24.dp, vertical = 10.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(12.dp),
                    ) {
                        UserAvatar(userId = null, version = null, name = reactor.name, size = 36.dp)
                        Column(modifier = Modifier.weight(1f)) {
                            Text(reactor.name, style = MaterialTheme.typography.bodyLarge)
                            if (reactor.isMe) {
                                Text(
                                    stringResource(R.string.reaction_tap_to_remove),
                                    style = MaterialTheme.typography.labelMedium,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                )
                            }
                        }
                        Text(reactor.emoji, fontSize = 24.sp)
                    }
                }
            }
        }
    }
}

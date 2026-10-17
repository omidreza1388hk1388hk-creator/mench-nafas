package com.omidgame.mench.feature.chat.presentation

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.omidgame.mench.R
import com.omidgame.mench.core.ui.components.MenchDialog

/** Chats > menu > Join group: paste a code (or the whole share text), see what you would join, then confirm. */
@Composable
fun JoinGroupDialog(
    state: ConversationListViewModel.JoinState,
    onLookup: (String) -> Unit,
    onConfirm: () -> Unit,
    onOpen: (conversationId: String) -> Unit,
    onDismiss: () -> Unit,
) {
    var pasted by remember { mutableStateOf("") }
    MenchDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(R.string.join_group_title)) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                when (state) {
                    is ConversationListViewModel.JoinState.Preview -> {
                        Text(state.title ?: stringResource(R.string.join_group_unnamed), style = MaterialTheme.typography.titleMedium)
                        Text(pluralStringResource(R.plurals.join_group_members, state.memberCount, state.memberCount))
                    }
                    is ConversationListViewModel.JoinState.Joined -> Text(stringResource(R.string.join_group_done))
                    is ConversationListViewModel.JoinState.Working -> CircularProgressIndicator()
                    else -> {
                        OutlinedTextField(
                            value = pasted,
                            onValueChange = { pasted = it },
                            label = { Text(stringResource(R.string.join_group_code_hint)) },
                            singleLine = true,
                        )
                        if (state is ConversationListViewModel.JoinState.Failed) {
                            Text(stringResource(state.reasonRes), color = MaterialTheme.colorScheme.error)
                        }
                    }
                }
            }
        },
        confirmButton = {
            when (state) {
                is ConversationListViewModel.JoinState.Preview ->
                    TextButton(onClick = onConfirm) { Text(stringResource(R.string.join_group_confirm)) }
                is ConversationListViewModel.JoinState.Joined ->
                    TextButton(onClick = { onOpen(state.conversationId) }) { Text(stringResource(R.string.join_group_open)) }
                is ConversationListViewModel.JoinState.Working -> Unit
                else -> TextButton(onClick = { onLookup(pasted) }, enabled = pasted.isNotBlank()) { Text(stringResource(R.string.join_group_lookup)) }
            }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text(stringResource(R.string.action_cancel)) } },
    )
}

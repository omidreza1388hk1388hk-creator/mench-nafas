package com.omidgame.mench.feature.chat.presentation

import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material3.Checkbox
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.unit.dp
import com.omidgame.mench.R
import com.omidgame.mench.core.ui.components.MenchDialog
import com.omidgame.mench.core.ui.components.MenchMenu
import com.omidgame.mench.core.ui.components.MenchMenuItem

/** Phase 2A — "All · Unread · Groups · <folders> · +" strip above the chat list. */
@OptIn(ExperimentalFoundationApi::class)
@Composable
fun ChatFilterBar(
    content: ConversationListUiState.Content,
    onSelect: (ChatFilter) -> Unit,
    onNewFolder: () -> Unit,
    onRenameFolder: (ChatFolder) -> Unit,
    onDeleteFolder: (ChatFolder) -> Unit,
    modifier: Modifier = Modifier,
) {
    LazyRow(
        modifier = modifier.fillMaxWidth(),
        contentPadding = PaddingValues(horizontal = 10.dp, vertical = 4.dp),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        item(key = "all") {
            FilterPill(stringResource(R.string.filter_all), content.filter == ChatFilter.All) { onSelect(ChatFilter.All) }
        }
        item(key = "unread") {
            val label = if (content.unreadChatCount > 0) {
                stringResource(R.string.filter_unread_count, content.unreadChatCount)
            } else {
                stringResource(R.string.filter_unread)
            }
            FilterPill(label, content.filter == ChatFilter.Unread) { onSelect(ChatFilter.Unread) }
        }
        if (content.hasGroups || content.filter == ChatFilter.Groups) {
            item(key = "groups") {
                FilterPill(stringResource(R.string.filter_groups), content.filter == ChatFilter.Groups) { onSelect(ChatFilter.Groups) }
            }
        }
        items(content.folders, key = { "folder-" + it.id }) { folder ->
            var menu by remember { mutableStateOf(false) }
            Box {
                FilterPill(
                    label = folder.name,
                    selected = content.filter == ChatFilter.Folder(folder.id),
                    onLongClick = { menu = true },
                ) { onSelect(ChatFilter.Folder(folder.id)) }
                MenchMenu(expanded = menu, onDismissRequest = { menu = false }) {
                    MenchMenuItem(
                        text = { Text(stringResource(R.string.folder_rename)) },
                        onClick = { menu = false; onRenameFolder(folder) },
                    )
                    MenchMenuItem(
                        text = { Text(stringResource(R.string.folder_delete)) },
                        onClick = { menu = false; onDeleteFolder(folder) },
                    )
                }
            }
        }
        if (content.folders.size < ChatFolders.MAX_FOLDERS) {
            item(key = "new") {
                Box(
                    modifier = Modifier
                        .clip(RoundedCornerShape(50))
                        .combinedClickable(role = Role.Button, onClick = onNewFolder)
                        .padding(horizontal = 10.dp, vertical = 8.dp),
                ) {
                    Icon(Icons.Filled.Add, contentDescription = stringResource(R.string.folder_new))
                }
            }
        }
    }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun FilterPill(
    label: String,
    selected: Boolean,
    onLongClick: (() -> Unit)? = null,
    onClick: () -> Unit,
) {
    val bg = if (selected) MaterialTheme.colorScheme.primaryContainer else MaterialTheme.colorScheme.surfaceContainerHigh
    val fg = if (selected) MaterialTheme.colorScheme.onPrimaryContainer else MaterialTheme.colorScheme.onSurfaceVariant
    Box(
        modifier = Modifier
            .clip(RoundedCornerShape(50))
            .background(bg)
            .combinedClickable(role = Role.Tab, onClick = onClick, onLongClick = onLongClick)
            .padding(horizontal = 14.dp, vertical = 8.dp),
        contentAlignment = Alignment.Center,
    ) {
        Text(label, style = MaterialTheme.typography.labelLarge, color = fg, maxLines = 1)
    }
}

/** Name prompt shared by "new folder" and "rename folder". */
@Composable
fun FolderNameDialog(
    title: String,
    initial: String,
    onConfirm: (String) -> Unit,
    onDismiss: () -> Unit,
) {
    var name by remember { mutableStateOf(initial) }
    val valid = ChatFolders.cleanName(name) != null
    MenchDialog(
        onDismissRequest = onDismiss,
        title = { Text(title) },
        text = {
            OutlinedTextField(
                value = name,
                onValueChange = { name = it.take(ChatFolders.MAX_NAME_LENGTH) },
                singleLine = true,
                label = { Text(stringResource(R.string.folder_name_hint)) },
            )
        },
        confirmButton = {
            TextButton(onClick = { onConfirm(name) }, enabled = valid) { Text(stringResource(R.string.us_confirm)) }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text(stringResource(R.string.action_cancel)) } },
    )
}

/** "Add to folder": one checkbox per folder plus a shortcut to create a new one that already contains this chat. */
@Composable
fun FolderPickerDialog(
    folders: List<ChatFolder>,
    conversationId: String,
    onToggle: (folderId: String, member: Boolean) -> Unit,
    onNewFolder: () -> Unit,
    onDismiss: () -> Unit,
) {
    MenchDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(R.string.folder_add_to)) },
        text = {
            Column {
                if (folders.isEmpty()) {
                    Text(stringResource(R.string.folder_none_yet), color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
                folders.forEach { folder ->
                    val member = conversationId in folder.conversationIds
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .combinedClickable(role = Role.Checkbox, onClick = { onToggle(folder.id, !member) })
                            .padding(vertical = 2.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Checkbox(checked = member, onCheckedChange = null)
                        Text(folder.name, modifier = Modifier.padding(start = 12.dp))
                    }
                }
                if (folders.size < ChatFolders.MAX_FOLDERS) {
                    TextButton(onClick = onNewFolder) { Text(stringResource(R.string.folder_new)) }
                }
            }
        },
        confirmButton = { TextButton(onClick = onDismiss) { Text(stringResource(R.string.us_confirm)) } },
    )
}

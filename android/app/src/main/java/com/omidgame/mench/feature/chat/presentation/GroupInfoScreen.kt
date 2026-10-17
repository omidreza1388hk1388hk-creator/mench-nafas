package com.omidgame.mench.feature.chat.presentation

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.IconButton
import androidx.compose.material3.ListItem
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material3.Icon
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.omidgame.mench.R
import com.omidgame.mench.feature.chat.domain.ConversationMember
import com.omidgame.mench.feature.chat.domain.GroupRole
import com.omidgame.mench.feature.chat.domain.GroupRules
import com.omidgame.mench.feature.chat.domain.GroupSettings

@Composable
fun GroupInfoScreen(
    state: GroupInfoUiState,
    onRename: (String) -> Unit,
    onAddMember: (String) -> Unit,
    onRemoveMember: (String) -> Unit,
    onSettingsChanged: (com.omidgame.mench.feature.chat.domain.GroupSettings) -> Unit = {},
    onToggleAdmin: (ConversationMember) -> Unit = {},
    onTransferOwner: (ConversationMember) -> Unit = {},
    onCreateInvite: (expiresInHours: Int?, maxUses: Int?) -> Unit = { _, _ -> },
    onRevokeInvite: (com.omidgame.mench.feature.chat.domain.GroupInvite) -> Unit = {},
) {
    Box(modifier = Modifier.fillMaxSize()) {
        when (state) {
            is GroupInfoUiState.Loading, is GroupInfoUiState.Left ->
                CircularProgressIndicator(modifier = Modifier.align(Alignment.Center))
            is GroupInfoUiState.Error ->
                Text(
                    text = state.message,
                    modifier = Modifier.align(Alignment.Center).padding(24.dp),
                    color = MaterialTheme.colorScheme.error,
                )
            is GroupInfoUiState.Content -> GroupInfoContent(state, onRename, onAddMember, onRemoveMember, onSettingsChanged, onToggleAdmin, onCreateInvite, onRevokeInvite, onTransferOwner)
        }
    }
}

@Composable
private fun GroupInfoContent(
    state: GroupInfoUiState.Content,
    onRename: (String) -> Unit,
    onAddMember: (String) -> Unit,
    onRemoveMember: (String) -> Unit,
    onSettingsChanged: (com.omidgame.mench.feature.chat.domain.GroupSettings) -> Unit,
    onToggleAdmin: (ConversationMember) -> Unit,
    onCreateInvite: (expiresInHours: Int?, maxUses: Int?) -> Unit,
    onRevokeInvite: (com.omidgame.mench.feature.chat.domain.GroupInvite) -> Unit,
    onTransferOwner: (ConversationMember) -> Unit = {},
) {
    val myRole = state.info?.myRole ?: if (state.isOwner) GroupRole.OWNER else GroupRole.MEMBER
    val settings = state.info?.settings ?: GroupSettings()
    var titleDraft by remember(state.title) { mutableStateOf(state.title) }
    var newMemberPhone by remember { mutableStateOf("+") }

    Column(modifier = Modifier.fillMaxSize().padding(16.dp)) {
        if (GroupRules.canEditInfo(myRole, settings)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                OutlinedTextField(
                    value = titleDraft,
                    onValueChange = { titleDraft = it },
                    label = { Text(stringResource(R.string.chat_group_name_hint)) },
                    singleLine = true,
                    modifier = Modifier.weight(1f),
                )
                OutlinedButton(
                    modifier = Modifier.padding(start = 8.dp),
                    enabled = titleDraft.isNotBlank() && titleDraft != state.title,
                    onClick = { onRename(titleDraft) },
                ) {
                    Text(stringResource(R.string.chat_save_edit))
                }
            }
        } else {
            Text(state.title, style = MaterialTheme.typography.titleLarge)
        }

        state.actionError?.let { error ->
            Spacer(modifier = Modifier.height(8.dp))
            Text(error, color = MaterialTheme.colorScheme.error)
        }

        Spacer(modifier = Modifier.height(16.dp))
        Text(stringResource(R.string.chat_group_members_title), style = MaterialTheme.typography.titleMedium)

        LazyColumn(modifier = Modifier.weight(1f)) {
            items(state.members, key = { it.userId }) { member ->
                val isSelf = member.userId == state.selfUserId
                GroupMemberRow(
                    member = member,
                    isSelf = isSelf,
                    canRemove = isSelf || GroupRules.canRemove(myRole, GroupRole.parse(member.role)),
                    onRemove = { onRemoveMember(member.userId) },
                    toggleAdminLabel = GroupRules.roleToggleTarget(myRole, GroupRole.parse(member.role))?.let {
                        if (it == GroupRole.ADMIN) R.string.group_make_admin else R.string.group_remove_admin
                    },
                    onToggleAdmin = { onToggleAdmin(member) },
                    canTransfer = myRole == GroupRole.OWNER && !isSelf,
                    onTransfer = { onTransferOwner(member) },
                )
            }
            if (GroupRules.canManage(myRole)) {
                item(key = "governance") {
                    GovernanceSection(
                        settings = settings,
                        isOwner = myRole == GroupRole.OWNER,
                        invites = state.invites,
                        groupTitle = state.title,
                        onSettingsChanged = onSettingsChanged,
                        onCreateInvite = onCreateInvite,
                        onRevokeInvite = onRevokeInvite,
                    )
                }
            }
        }
        HorizontalDivider()

        Spacer(modifier = Modifier.height(8.dp))
        if (GroupRules.canAddMembers(myRole, settings)) Row(verticalAlignment = Alignment.CenterVertically) {
            OutlinedTextField(
                value = newMemberPhone,
                onValueChange = { newMemberPhone = it },
                label = { Text(stringResource(R.string.chat_add_member_hint)) },
                singleLine = true,
                modifier = Modifier.weight(1f),
            )
            Button(
                modifier = Modifier.padding(start = 8.dp),
                enabled = newMemberPhone.length >= 8,
                onClick = {
                    onAddMember(newMemberPhone)
                    newMemberPhone = "+"
                },
            ) {
                Text(stringResource(R.string.chat_add_member_action))
            }
        }
    }
}

@Composable
private fun GroupMemberRow(
    member: ConversationMember,
    isSelf: Boolean,
    canRemove: Boolean,
    onRemove: () -> Unit,
    toggleAdminLabel: Int? = null,
    onToggleAdmin: () -> Unit = {},
    canTransfer: Boolean = false,
    onTransfer: () -> Unit = {},
) {
    var confirmTransfer by androidx.compose.runtime.remember { androidx.compose.runtime.mutableStateOf(false) }
    if (confirmTransfer) {
        com.omidgame.mench.core.ui.components.MenchDialog(
            onDismissRequest = { confirmTransfer = false },
            title = { Text(stringResource(R.string.group_transfer_title)) },
            text = { Text(stringResource(R.string.group_transfer_body, member.displayName ?: member.phoneE164)) },
            confirmButton = {
                androidx.compose.material3.TextButton(onClick = { confirmTransfer = false; onTransfer() }) {
                    Text(stringResource(R.string.group_transfer_confirm))
                }
            },
            dismissButton = {
                androidx.compose.material3.TextButton(onClick = { confirmTransfer = false }) { Text(stringResource(R.string.action_cancel)) }
            },
        )
    }
    ListItem(
        headlineContent = { Text(member.displayName ?: member.phoneE164) },
        supportingContent = {
            Text(
                when {
                    member.isOwner -> stringResource(R.string.chat_group_role_owner)
                    member.isAdmin -> stringResource(R.string.group_role_admin)
                    else -> stringResource(R.string.chat_group_role_member)
                },
            )
        },
        trailingContent = {
            Row(verticalAlignment = Alignment.CenterVertically) {
                if (toggleAdminLabel != null) {
                    androidx.compose.material3.TextButton(onClick = onToggleAdmin) { Text(stringResource(toggleAdminLabel)) }
                }
                if (canTransfer) {
                    androidx.compose.material3.TextButton(onClick = { confirmTransfer = true }) { Text(stringResource(R.string.group_transfer_owner)) }
                }
                if (canRemove) {
                    IconButton(onClick = onRemove) {
                        Icon(
                            Icons.Filled.Close,
                            contentDescription = stringResource(
                                if (isSelf) R.string.chat_leave_group else R.string.chat_remove_member,
                            ),
                        )
                    }
                }
            }
        },
        modifier = Modifier.fillMaxWidth(),
    )
}

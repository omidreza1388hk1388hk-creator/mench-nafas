package com.omidgame.mench.feature.people.presentation

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.content.Intent
import android.net.Uri
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Block
import androidx.compose.material.icons.filled.ContentCopy
import androidx.compose.material.icons.filled.Share
import androidx.compose.material.icons.filled.Sms
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
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
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.omidgame.mench.R
import com.omidgame.mench.core.ui.components.MenchDialog
import com.omidgame.mench.core.ui.components.UserAvatar
import com.omidgame.mench.core.ui.theme.MenchBrand
import com.omidgame.mench.core.ui.theme.menchTopBarColors
import com.omidgame.mench.feature.people.data.PublicUser
import com.omidgame.mench.feature.people.data.inviteLink

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun FindPeopleScreen(
    state: FindPeopleUiState,
    onUsernameChange: (String) -> Unit,
    onSearchUsername: () -> Unit,
    onInviteInputChange: (String) -> Unit,
    onResolveInvite: () -> Unit,
    onMessageClick: () -> Unit,
    onBlockClick: () -> Unit,
    onBackClick: () -> Unit,
) {
    val context = LocalContext.current
    var confirmBlock by remember { mutableStateOf(false) }

    Scaffold(
        topBar = {
            TopAppBar(
                modifier = Modifier.background(MenchBrand.heroBrush()),
                colors = menchTopBarColors(),
                title = { Text(stringResource(R.string.find_people_title), fontWeight = FontWeight.Bold) },
                navigationIcon = {
                    IconButton(onClick = onBackClick) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = stringResource(R.string.chat_back))
                    }
                },
            )
        },
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .verticalScroll(rememberScrollState())
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            Card {
                Text(
                    stringResource(R.string.find_by_username),
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.SemiBold,
                )
                Spacer(Modifier.height(8.dp))
                OutlinedTextField(
                    value = state.usernameQuery,
                    onValueChange = onUsernameChange,
                    label = { Text(stringResource(R.string.find_username_hint)) },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth(),
                )
                Spacer(Modifier.height(8.dp))
                Button(
                    onClick = onSearchUsername,
                    enabled = state.usernameQuery.isNotBlank() && !state.isSearching,
                    modifier = Modifier.fillMaxWidth(),
                ) { Text(stringResource(R.string.find_search_action)) }
            }

            if (state.isSearching) {
                Box(Modifier.fillMaxWidth(), contentAlignment = Alignment.Center) { CircularProgressIndicator() }
            }

            state.result?.let { user ->
                ResultCard(
                    user = user,
                    isStartingChat = state.isStartingChat,
                    onMessageClick = onMessageClick,
                    onBlockClick = { confirmBlock = true },
                )
            }

            state.message?.let { message ->
                Text(
                    text = stringResource(message.textRes()),
                    color = if (message == FindMessage.BLOCKED) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.error,
                    style = MaterialTheme.typography.bodyMedium,
                    modifier = Modifier.fillMaxWidth(),
                    textAlign = TextAlign.Center,
                )
            }

            Card {
                Text(
                    stringResource(R.string.invite_have_code),
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.SemiBold,
                )
                Spacer(Modifier.height(8.dp))
                OutlinedTextField(
                    value = state.inviteInput,
                    onValueChange = onInviteInputChange,
                    label = { Text(stringResource(R.string.invite_code_hint)) },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth(),
                )
                Spacer(Modifier.height(8.dp))
                OutlinedButton(
                    onClick = onResolveInvite,
                    enabled = state.inviteInput.isNotBlank() && !state.isSearching,
                    modifier = Modifier.fillMaxWidth(),
                ) { Text(stringResource(R.string.invite_open_action)) }
            }

            Card {
                Text(
                    stringResource(R.string.invite_my_title),
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.SemiBold,
                )
                Spacer(Modifier.height(12.dp))
                val code = state.myInviteCode
                if (code == null) {
                    Box(Modifier.fillMaxWidth(), contentAlignment = Alignment.Center) { CircularProgressIndicator() }
                } else {
                    Box(Modifier.fillMaxWidth(), contentAlignment = Alignment.Center) {
                        InviteQr(
                            content = inviteLink(code),
                            size = 200.dp,
                            contentDescription = stringResource(R.string.invite_qr_description),
                        )
                    }
                    Spacer(Modifier.height(10.dp))
                    Text(
                        text = code,
                        modifier = Modifier.fillMaxWidth(),
                        textAlign = TextAlign.Center,
                        style = MaterialTheme.typography.headlineSmall,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.primary,
                    )
                    Spacer(Modifier.height(10.dp))
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                    ) {
                        InviteAction(Icons.Filled.ContentCopy, stringResource(R.string.invite_copy), Modifier.weight(1f)) {
                            copyToClipboard(context, inviteLink(code))
                        }
                        InviteAction(Icons.Filled.Share, stringResource(R.string.invite_share), Modifier.weight(1f)) {
                            shareInvite(context, inviteText(context, code))
                        }
                        InviteAction(Icons.Filled.Sms, stringResource(R.string.invite_sms), Modifier.weight(1f)) {
                            smsInvite(context, inviteText(context, code))
                        }
                    }
                }
            }
        }
    }

    if (confirmBlock) {
        val name = state.result?.displayName ?: state.result?.username.orEmpty()
        MenchDialog(
            onDismissRequest = { confirmBlock = false },
            title = { Text(stringResource(R.string.block_confirm_title, name)) },
            text = { Text(stringResource(R.string.block_confirm_body)) },
            confirmButton = {
                TextButton(onClick = {
                    confirmBlock = false
                    onBlockClick()
                }) { Text(stringResource(R.string.block_action), color = MaterialTheme.colorScheme.error) }
            },
            dismissButton = {
                TextButton(onClick = { confirmBlock = false }) { Text(stringResource(R.string.action_cancel)) }
            },
        )
    }
}

@Composable
private fun Card(content: @Composable () -> Unit) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(20.dp))
            .background(MaterialTheme.colorScheme.surfaceContainerLow)
            .border(0.5.dp, MenchBrand.hairline(), RoundedCornerShape(20.dp))
            .padding(16.dp),
    ) { content() }
}

@Composable
private fun ResultCard(
    user: PublicUser,
    isStartingChat: Boolean,
    onMessageClick: () -> Unit,
    onBlockClick: () -> Unit,
) {
    Card {
        Row(verticalAlignment = Alignment.CenterVertically) {
            UserAvatar(userId = user.id, version = user.avatarVersion, name = user.displayName ?: user.username, size = 56.dp)
            Spacer(Modifier.width(14.dp))
            Column(Modifier.weight(1f)) {
                Text(
                    text = user.displayName ?: user.username.orEmpty(),
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold,
                )
                if (user.username != null) {
                    Text(
                        text = "@" + user.username,
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.primary,
                    )
                }
            }
        }
        if (!user.bio.isNullOrBlank()) {
            Spacer(Modifier.height(10.dp))
            Text(user.bio, style = MaterialTheme.typography.bodyMedium)
        }
        Spacer(Modifier.height(12.dp))
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
            Button(onClick = onMessageClick, enabled = !isStartingChat, modifier = Modifier.weight(1f)) {
                Text(stringResource(R.string.find_message_action))
            }
            IconButton(onClick = onBlockClick) {
                Icon(
                    Icons.Filled.Block,
                    contentDescription = stringResource(R.string.block_action),
                    tint = MaterialTheme.colorScheme.error,
                )
            }
        }
    }
}

@Composable
private fun InviteAction(
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    label: String,
    modifier: Modifier,
    onClick: () -> Unit,
) {
    OutlinedButton(onClick = onClick, modifier = modifier, contentPadding = androidx.compose.foundation.layout.PaddingValues(horizontal = 6.dp, vertical = 8.dp)) {
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            Icon(icon, contentDescription = null)
            Text(label, style = MaterialTheme.typography.labelMedium, maxLines = 1)
        }
    }
}

private fun FindMessage.textRes(): Int = when (this) {
    FindMessage.NOT_FOUND -> R.string.find_not_found
    FindMessage.OFFLINE -> R.string.find_offline
    FindMessage.FAILED -> R.string.find_failed
    FindMessage.BLOCKED -> R.string.block_done
    FindMessage.START_FAILED -> R.string.find_start_failed
}

private fun inviteText(context: Context, code: String): String =
    context.getString(R.string.invite_share_text, code, inviteLink(code))

private fun copyToClipboard(context: Context, text: String) {
    val clipboard = context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
    clipboard.setPrimaryClip(ClipData.newPlainText("MENCH", text))
}

private fun shareInvite(context: Context, text: String) {
    val send = Intent(Intent.ACTION_SEND).apply {
        type = "text/plain"
        putExtra(Intent.EXTRA_TEXT, text)
    }
    runCatching {
        context.startActivity(Intent.createChooser(send, null).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
    }
}

/** ACTION_SENDTO with smsto: opens the SMS app pre-filled and needs no SMS permission. */
private fun smsInvite(context: Context, text: String) {
    val sms = Intent(Intent.ACTION_SENDTO, Uri.parse("smsto:")).apply {
        putExtra("sms_body", text)
        addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
    }
    runCatching { context.startActivity(sms) }
}

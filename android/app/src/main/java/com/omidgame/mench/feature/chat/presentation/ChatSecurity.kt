package com.omidgame.mench.feature.chat.presentation

import android.app.Activity
import android.app.KeyguardManager
import android.content.Context
import android.content.ContextWrapper
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.clickable
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.DialogProperties
import androidx.compose.ui.window.SecureFlagPolicy
import androidx.fragment.app.FragmentActivity
import coil.compose.AsyncImage
import coil.request.CachePolicy
import coil.request.ImageRequest
import com.omidgame.mench.R
import com.omidgame.mench.core.crypto.E2eeManager
import com.omidgame.mench.core.network.AttachmentUrls
import com.omidgame.mench.core.security.BiometricAuthenticator
import com.omidgame.mench.core.security.BiometricResult
import com.omidgame.mench.feature.chat.domain.MessageAttachment
import kotlinx.coroutines.launch

private tailrec fun Context.activityOrNull(): FragmentActivity? = when (this) {
    is FragmentActivity -> this
    is ContextWrapper -> baseContext.activityOrNull()
    else -> null
}

/** Phase 4C — per-chat lock. Locked chat ids live in plain prefs (they are not secrets); the lock itself is the OS biometric / screen lock. */
object ChatLock {
    private const val PREFS = "mench_locked_chats"
    fun isLocked(context: Context, conversationId: String): Boolean =
        context.applicationContext.getSharedPreferences(PREFS, Context.MODE_PRIVATE).contains(conversationId)

    fun set(context: Context, conversationId: String, locked: Boolean) {
        context.applicationContext.getSharedPreferences(PREFS, Context.MODE_PRIVATE).edit()
            .apply { if (locked) putBoolean(conversationId, true) else remove(conversationId) }.apply()
    }
}

/** Shown instead of a locked chat until the user passes biometrics (or the device screen lock if no biometrics are enrolled). */
@Composable
fun ChatLockGate(onUnlocked: () -> Unit, onBack: () -> Unit) {
    val context = LocalContext.current
    val title = stringResource(R.string.chat_locked_title)
    val cancel = stringResource(R.string.us_cancel)
    var message by remember { mutableStateOf<Int?>(null) }
    val credentialLauncher = rememberLauncherForActivityResult(ActivityResultContracts.StartActivityForResult()) {
        if (it.resultCode == Activity.RESULT_OK) onUnlocked() else message = R.string.chat_locked_failed
    }
    suspend fun prompt() {
        val activity = context.activityOrNull()
        if (activity == null) { message = R.string.chat_locked_needs_lock; return }
        when (BiometricAuthenticator(activity).authenticate(title, cancel)) {
            is BiometricResult.Success -> onUnlocked()
            is BiometricResult.Unavailable -> {
                val km = context.getSystemService(KeyguardManager::class.java)
                val intent = if (km?.isDeviceSecure == true) km.createConfirmDeviceCredentialIntent(title, null) else null
                if (intent != null) credentialLauncher.launch(intent) else message = R.string.chat_locked_needs_lock
            }
            else -> message = R.string.chat_locked_failed
        }
    }
    val scope = rememberCoroutineScope()
    LaunchedEffect(Unit) { prompt() }
    Column(
        modifier = Modifier.fillMaxSize().padding(32.dp),
        verticalArrangement = Arrangement.Center,
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Text("🔒", fontSize = 56.sp)
        Text(title, style = MaterialTheme.typography.titleMedium, modifier = Modifier.padding(top = 12.dp))
        message?.let { Text(stringResource(it), color = MaterialTheme.colorScheme.error, modifier = Modifier.padding(top = 8.dp)) }
        Button(onClick = { message = null; scope.launch { prompt() } }, modifier = Modifier.padding(top = 16.dp)) { Text(stringResource(R.string.chat_locked_unlock)) }
        TextButton(onClick = onBack) { Text(stringResource(R.string.chat_locked_back)) }
    }
}

/** Chat security: end-to-end encryption (1:1 only) and the per-chat lock. */
@Composable
fun ChatSecurityDialog(
    conversationId: String,
    isGroup: Boolean,
    onSetSecureChat: suspend (Boolean) -> Boolean,
    onDismiss: () -> Unit,
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    var secure by remember(conversationId) { mutableStateOf(E2eeManager.isSecureChat(conversationId)) }
    var locked by remember(conversationId) { mutableStateOf(ChatLock.isLocked(context, conversationId)) }
    var busy by remember { mutableStateOf(false) }
    var error by remember { mutableStateOf(false) }
    com.omidgame.mench.core.ui.components.MenchDialog(
        onDismissRequest = onDismiss,
        title = { Text("🔐  " + stringResource(R.string.chat_security_title)) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(14.dp)) {
                if (!isGroup) {
                    Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.SpaceBetween) {
                        Column(Modifier.weight(1f)) {
                            Text(stringResource(R.string.e2ee_switch), style = MaterialTheme.typography.titleSmall)
                            Text(stringResource(R.string.e2ee_hint), style = MaterialTheme.typography.bodySmall)
                        }
                        Switch(
                            checked = secure,
                            enabled = !busy,
                            onCheckedChange = { want ->
                                busy = true; error = false
                                scope.launch {
                                    val ok = onSetSecureChat(want)
                                    if (ok) secure = want else error = true
                                    busy = false
                                }
                            },
                        )
                    }
                    if (error) Text(stringResource(R.string.e2ee_failed), color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodySmall)
                }
                Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.SpaceBetween) {
                    Column(Modifier.weight(1f)) {
                        Text(stringResource(R.string.chat_lock_switch), style = MaterialTheme.typography.titleSmall)
                        Text(stringResource(R.string.chat_lock_hint), style = MaterialTheme.typography.bodySmall)
                    }
                    Switch(checked = locked, onCheckedChange = { locked = it; ChatLock.set(context, conversationId, it) })
                }
                if (!isGroup && secure) SecurityCodeSection(conversationId)
            }
        },
        confirmButton = { TextButton(onClick = onDismiss) { Text(stringResource(R.string.seen_by_close)) } },
    )
}

private object OpenedViewOnce {
    private const val PREFS = "mench_opened_view_once"
    fun isOpen(c: Context, id: String) = c.applicationContext.getSharedPreferences(PREFS, Context.MODE_PRIVATE).contains(id)
    fun markOpen(c: Context, id: String) { c.applicationContext.getSharedPreferences(PREFS, Context.MODE_PRIVATE).edit().putBoolean(id, true).apply() }
}

const val VIEW_ONCE_PREFIX = "vo-"
fun MessageAttachment.isViewOnce(): Boolean = originalFilename.startsWith(VIEW_ONCE_PREFIX)

/** Incoming view-once photo: a tile until tapped; the photo is shown once, screenshot-blocked, never cached. */
@Composable
fun ViewOnceTile(messageId: String, attachment: MessageAttachment) {
    val context = LocalContext.current
    var opened by remember(messageId) { mutableStateOf(OpenedViewOnce.isOpen(context, messageId)) }
    var showing by remember { mutableStateOf(false) }
    Column(
        modifier = Modifier.padding(horizontal = 8.dp, vertical = 6.dp).clickable(enabled = !opened) { showing = true },
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Text(if (opened) "✓" else "👁", fontSize = 32.sp)
        Text(stringResource(if (opened) R.string.view_once_opened else R.string.view_once_tap), style = MaterialTheme.typography.labelLarge)
    }
    if (showing) {
        androidx.compose.ui.window.Dialog(
            onDismissRequest = { showing = false; OpenedViewOnce.markOpen(context, messageId); opened = true },
            properties = DialogProperties(usePlatformDefaultWidth = false, securePolicy = SecureFlagPolicy.SecureOn),
        ) {
            Column(Modifier.fillMaxSize(), horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.Center) {
                AsyncImage(
                    model = ImageRequest.Builder(context)
                        .data(attachment.id?.let { AttachmentUrls.content(it) })
                        .memoryCachePolicy(CachePolicy.DISABLED)
                        .diskCachePolicy(CachePolicy.DISABLED)
                        .build(),
                    contentDescription = null,
                    modifier = Modifier.fillMaxWidth().weight(1f),
                )
                TextButton(onClick = { showing = false; OpenedViewOnce.markOpen(context, messageId); opened = true }) {
                    Text(stringResource(R.string.view_once_close))
                }
            }
        }
    }
}

package com.omidgame.mench.feature.chat.presentation

import android.content.Context
import android.content.ContextWrapper
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.Card
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.DialogProperties
import androidx.compose.ui.window.SecureFlagPolicy
import androidx.fragment.app.FragmentActivity
import com.omidgame.mench.R
import com.omidgame.mench.core.security.BiometricAuthenticator
import com.omidgame.mench.core.security.BiometricResult
import com.omidgame.mench.core.ui.components.MenchDialog
import com.omidgame.mench.feature.chat.domain.VaultItem
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/**
 * Phase 3 — the couple's private vault.
 * Step 1: the phone's biometric prompt (so someone holding your unlocked phone cannot open it).
 * Step 2: a passphrase you two agreed on; notes are encrypted with it on the phone and the server only stores ciphertext.
 */

/** FLAG_SECURE on the dialog window itself: no screenshots, no screen recording, blank in the recents screen. */
private val SecureDialogProperties = DialogProperties(usePlatformDefaultWidth = false, securePolicy = SecureFlagPolicy.SecureOn)

private enum class VaultStage { CHECKING, UNAVAILABLE, PASSPHRASE, OPEN }

private tailrec fun Context.findFragmentActivity(): FragmentActivity? = when (this) {
    is FragmentActivity -> this
    is ContextWrapper -> baseContext.findFragmentActivity()
    else -> null
}

/** A note that was decrypted for display. A null [note] means this phone's passphrase could not decrypt it. */
private data class OpenNote(val id: String, val isMine: Boolean, val note: Pair<String, String>?)

/**
 * True when [key] is acceptable for these items: either the vault is still empty (the first passphrase sets the tone), or at
 * least one note decrypts, which proves the passphrase matches the one already in use.
 */
internal fun passphraseAccepted(items: List<VaultItem>, decrypt: (String) -> String?): Boolean =
    items.isEmpty() || items.any { decrypt(it.blob) != null }

@Composable
fun VaultDialog(
    conversationId: String,
    items: List<VaultItem>,
    onDismiss: () -> Unit,
    onAdd: (blob: String) -> Unit,
    onDelete: (itemId: String) -> Unit,
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    var stage by remember { mutableStateOf(VaultStage.CHECKING) }
    var key by remember { mutableStateOf<ByteArray?>(null) }
    var passphrase by remember { mutableStateOf("") }
    var wrongPassphrase by remember { mutableStateOf(false) }
    var working by remember { mutableStateOf(false) }
    var adding by remember { mutableStateOf(false) }
    var confirmDelete by remember { mutableStateOf<String?>(null) }

    val biometricTitle = stringResource(R.string.vault_biometric_title)
    val biometricCancel = stringResource(R.string.vault_biometric_cancel)
    LaunchedEffect(Unit) {
        val activity = context.findFragmentActivity()
        if (activity == null) {
            stage = VaultStage.UNAVAILABLE
            return@LaunchedEffect
        }
        when (BiometricAuthenticator(activity).authenticate(biometricTitle, biometricCancel)) {
            is BiometricResult.Success -> stage = VaultStage.PASSPHRASE
            is BiometricResult.Unavailable -> stage = VaultStage.UNAVAILABLE
            else -> onDismiss()
        }
    }

    when (stage) {
        VaultStage.CHECKING -> Unit
        VaultStage.UNAVAILABLE -> MenchDialog(
            properties = SecureDialogProperties,
            onDismissRequest = onDismiss,
            confirmButton = { TextButton(onClick = onDismiss) { Text(stringResource(R.string.us_close)) } },
            title = { Text("🔐  " + stringResource(R.string.vault_title)) },
            text = { Text(stringResource(R.string.vault_needs_lock)) },
        )
        VaultStage.PASSPHRASE -> MenchDialog(
            properties = SecureDialogProperties,
            onDismissRequest = onDismiss,
            title = { Text("🔐  " + stringResource(R.string.vault_title)) },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    Text(stringResource(if (items.isEmpty()) R.string.vault_set_passphrase else R.string.vault_enter_passphrase))
                    OutlinedTextField(
                        value = passphrase,
                        onValueChange = { passphrase = it; wrongPassphrase = false },
                        label = { Text(stringResource(R.string.vault_passphrase)) },
                        singleLine = true,
                        isError = wrongPassphrase,
                        visualTransformation = PasswordVisualTransformation(),
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Password),
                        modifier = Modifier.fillMaxWidth(),
                    )
                    if (wrongPassphrase) {
                        Text(stringResource(R.string.vault_wrong_passphrase), color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodySmall)
                    }
                    Text(
                        stringResource(R.string.vault_no_recovery),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            },
            confirmButton = {
                TextButton(
                    enabled = passphrase.length >= 6 && !working,
                    onClick = {
                        working = true
                        scope.launch {
                            // Key derivation is deliberately slow (about a tenth of a second); keep it off the main thread.
                            val derived = withContext(Dispatchers.Default) { VaultCrypto.deriveKey(passphrase, conversationId) }
                            val ok = withContext(Dispatchers.Default) { passphraseAccepted(items) { VaultCrypto.decrypt(derived, it) } }
                            working = false
                            if (ok) {
                                key = derived
                                passphrase = ""
                                stage = VaultStage.OPEN
                            } else {
                                wrongPassphrase = true
                            }
                        }
                    },
                ) { Text(stringResource(R.string.vault_unlock)) }
            },
            dismissButton = { TextButton(onClick = onDismiss) { Text(stringResource(R.string.us_cancel)) } },
        )
        VaultStage.OPEN -> {
            val activeKey = key
            val notes = remember(items, activeKey) {
                items.map { item ->
                    OpenNote(item.id, item.isMine, activeKey?.let { VaultCrypto.decrypt(it, item.blob) }?.let(VaultCrypto::unpackNote))
                }
            }
            MenchDialog(
            properties = SecureDialogProperties,
                onDismissRequest = onDismiss,
                confirmButton = { TextButton(onClick = onDismiss) { Text(stringResource(R.string.us_close)) } },
                dismissButton = { TextButton(onClick = { adding = true }) { Text(stringResource(R.string.vault_add)) } },
                title = { Text("🔐  " + stringResource(R.string.vault_title)) },
                text = {
                    if (notes.isEmpty()) {
                        EmptyHeartState(stringResource(R.string.vault_empty))
                    } else {
                        LazyColumn(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                            items(notes, key = { it.id }) { n ->
                                Card(modifier = Modifier.fillMaxWidth()) {
                                    Column(modifier = Modifier.padding(12.dp)) {
                                        if (n.note == null) {
                                            Text("🔒 " + stringResource(R.string.vault_cannot_decrypt), style = MaterialTheme.typography.bodyMedium)
                                        } else {
                                            if (n.note.first.isNotBlank()) Text(n.note.first, style = MaterialTheme.typography.titleSmall)
                                            Text(n.note.second, style = MaterialTheme.typography.bodyMedium)
                                        }
                                        if (n.isMine) {
                                            TextButton(onClick = { confirmDelete = n.id }) { Text(stringResource(R.string.counters_delete)) }
                                        }
                                    }
                                }
                            }
                        }
                    }
                },
            )

            if (adding) {
                var title by remember { mutableStateOf("") }
                var body by remember { mutableStateOf("") }
                MenchDialog(
            properties = SecureDialogProperties,
                    onDismissRequest = { adding = false },
                    title = { Text(stringResource(R.string.vault_add)) },
                    text = {
                        Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                            OutlinedTextField(
                                value = title,
                                onValueChange = { if (it.length <= 80) title = it },
                                label = { Text(stringResource(R.string.vault_note_title)) },
                                singleLine = true,
                                modifier = Modifier.fillMaxWidth(),
                            )
                            OutlinedTextField(
                                value = body,
                                onValueChange = { if (it.length <= 1500) body = it },
                                label = { Text(stringResource(R.string.vault_note_body)) },
                                minLines = 3,
                                modifier = Modifier.fillMaxWidth(),
                            )
                        }
                    },
                    confirmButton = {
                        TextButton(
                            enabled = body.isNotBlank() && activeKey != null,
                            onClick = {
                                if (activeKey != null) {
                                    onAdd(VaultCrypto.encrypt(activeKey, VaultCrypto.packNote(title.trim(), body.trim())))
                                }
                                adding = false
                            },
                        ) { Text(stringResource(R.string.vault_save)) }
                    },
                    dismissButton = { TextButton(onClick = { adding = false }) { Text(stringResource(R.string.us_cancel)) } },
                )
            }

            confirmDelete?.let { id ->
                MenchDialog(
            properties = SecureDialogProperties,
                    onDismissRequest = { confirmDelete = null },
                    title = { Text(stringResource(R.string.vault_delete_confirm)) },
                    confirmButton = { TextButton(onClick = { onDelete(id); confirmDelete = null }) { Text(stringResource(R.string.counters_delete)) } },
                    dismissButton = { TextButton(onClick = { confirmDelete = null }) { Text(stringResource(R.string.us_cancel)) } },
                )
            }
        }
    }
}

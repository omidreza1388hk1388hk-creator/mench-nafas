package com.omidgame.mench.feature.chat.presentation

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextDirection
import androidx.compose.ui.unit.dp
import com.omidgame.mench.R
import com.omidgame.mench.core.crypto.E2eeManager
import com.omidgame.mench.core.crypto.PeerKeyTrust
import com.omidgame.mench.core.crypto.SecurityCode
import com.omidgame.mench.core.ui.components.MenchDialog
import kotlinx.coroutines.delay

/**
 * Settings Phase 1B: the 60-digit security code for this secure chat. Both phones show the same digits, so the
 * two people can read them out or compare in person. Shown inside [ChatSecurityDialog]; nothing here is secret.
 */
@Composable
fun SecurityCodeSection(conversationId: String) {
    val peerId = remember(conversationId) { E2eeManager.peerOf(conversationId) } ?: return
    val myKey = remember { E2eeManager.publicKey() } ?: return
    val peerKey = remember(peerId) { E2eeManager.cachedPeerKey(peerId) } ?: return
    val code = remember(myKey, peerKey) { SecurityCode.forPair(myKey, peerKey) }
    Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
        Text(stringResource(R.string.sx_seccode_title), style = MaterialTheme.typography.titleSmall)
        Text(stringResource(R.string.sx_seccode_hint), style = MaterialTheme.typography.bodySmall)
        Text(
            text = code,
            style = TextStyle(textDirection = TextDirection.Ltr, fontFamily = FontFamily.Monospace),
            fontWeight = FontWeight.SemiBold,
            textAlign = TextAlign.Center,
            modifier = Modifier.fillMaxWidth().padding(top = 6.dp),
        )
    }
}

/**
 * Shown over a secure chat when the partner's public key is different from the one we trusted. Sending stays
 * blocked (E2eeManager.prepareOutgoing returns null) until the user acknowledges; "Not now" only hides this
 * dialog, it never unblocks sending.
 */
@Composable
fun KeyChangeWarning(conversationId: String, onViewCode: () -> Unit) {
    val context = LocalContext.current
    val peerId = remember(conversationId) { E2eeManager.peerOf(conversationId) } ?: return
    var pending by remember(peerId) { mutableStateOf(PeerKeyTrust.hasPendingChange(context, peerId)) }
    var hidden by remember(peerId) { mutableStateOf(false) }
    // A failed send is what detects the change, so keep looking while the chat is open (a prefs read, very cheap).
    LaunchedEffect(peerId) {
        while (true) {
            pending = PeerKeyTrust.hasPendingChange(context, peerId)
            delay(2000)
        }
    }
    if (!pending || hidden) return
    MenchDialog(
        onDismissRequest = { hidden = true },
        title = { Text(stringResource(R.string.sx_keychange_title)) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text(stringResource(R.string.sx_keychange_msg), style = MaterialTheme.typography.bodyMedium)
                TextButton(onClick = { hidden = true; onViewCode() }) { Text(stringResource(R.string.sx_keychange_view)) }
            }
        },
        confirmButton = {
            TextButton(onClick = { PeerKeyTrust.acknowledge(context, peerId); pending = false }) {
                Text(stringResource(R.string.sx_keychange_ack))
            }
        },
        dismissButton = { TextButton(onClick = { hidden = true }) { Text(stringResource(R.string.sx_keychange_later)) } },
    )
}

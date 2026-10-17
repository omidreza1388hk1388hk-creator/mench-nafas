package com.omidgame.mench.feature.product

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.ViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewModelScope
import com.omidgame.mench.R
import com.omidgame.mench.core.network.WebLoginInfo
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import javax.inject.Inject

/** Parsed `mench://link/<challengeId>.<nonce>` payload from the QR shown by MENCH Web / Desktop. */
data class LinkPayload(val id: String, val nonce: String) {
    companion object {
        private val RE = Regex("^mench://link/([0-9a-fA-F-]{36})\\.([A-Za-z0-9_-]{10,60})$")
        fun parse(raw: String): LinkPayload? = RE.matchEntire(raw.trim())?.let { LinkPayload(it.groupValues[1], it.groupValues[2]) }
    }
}

sealed interface LinkPhase {
    data object Scan : LinkPhase
    data object Loading : LinkPhase
    data class Confirm(val payload: LinkPayload, val info: WebLoginInfo) : LinkPhase
    data object Done : LinkPhase
    data object Rejected : LinkPhase
    data object Failed : LinkPhase
}

@HiltViewModel
class LinkDeviceViewModel @Inject constructor(private val repo: EcosystemRepository) : ViewModel() {
    private val _phase = MutableStateFlow<LinkPhase>(LinkPhase.Scan)
    val phase: StateFlow<LinkPhase> = _phase

    fun onPayload(p: LinkPayload) {
        if (_phase.value != LinkPhase.Scan) return
        _phase.value = LinkPhase.Loading
        viewModelScope.launch {
            repo.describeLogin(p.id, p.nonce)
                .onSuccess { info -> _phase.value = LinkPhase.Confirm(p, info) }
                .onFailure { _phase.value = LinkPhase.Failed }
        }
    }

    fun decide(p: LinkPayload, approve: Boolean) {
        viewModelScope.launch {
            repo.decideLogin(p.id, p.nonce, approve)
                .onSuccess { _phase.update { if (approve) LinkPhase.Done else LinkPhase.Rejected } }
                .onFailure { _phase.value = LinkPhase.Failed }
        }
    }

    fun retry() { _phase.value = LinkPhase.Scan }
}

/**
 * Phone side of QR login. The person scans the code shown on the Web/Desktop client, sees WHICH device
 * and platform is asking, and only then approves. [initialPayload] comes from a mench://link deep link.
 */
@Composable
fun LinkDeviceScreen(onBack: () -> Unit, initialPayload: String?, viewModel: LinkDeviceViewModel = hiltViewModel()) {
    val phase by viewModel.phase.collectAsStateWithLifecycle()
    LaunchedEffect(initialPayload) { initialPayload?.let { LinkPayload.parse(it) }?.let(viewModel::onPayload) }

    when (val p = phase) {
        LinkPhase.Scan -> QrScanScreen(
            onBack = onBack,
            accept = { raw -> LinkPayload.parse(raw)?.let { "${it.id}.${it.nonce}" } },
            onInviteCode = { joined ->
                val i = joined.indexOf('.')
                if (i > 0) viewModel.onPayload(LinkPayload(joined.substring(0, i), joined.substring(i + 1)))
            },
        )
        else -> ProductScaffold(title = stringResource(R.string.link_device_title), onBack = onBack) { padding ->
            Column(Modifier.fillMaxSize().padding(padding).padding(20.dp), verticalArrangement = Arrangement.spacedBy(14.dp)) {
                when (p) {
                    LinkPhase.Loading -> Text(stringResource(R.string.referral_loading))
                    is LinkPhase.Confirm -> {
                        Text(stringResource(R.string.link_device_question), style = MaterialTheme.typography.titleMedium)
                        Card(Modifier.fillMaxWidth()) {
                            Column(Modifier.padding(16.dp)) {
                                Text(p.info.deviceName, style = MaterialTheme.typography.titleSmall)
                                Text(stringResource(if (p.info.platform == "desktop") R.string.link_platform_desktop else R.string.link_platform_web), style = MaterialTheme.typography.bodyMedium)
                            }
                        }
                        Text(stringResource(R.string.link_device_warning), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.error)
                        Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                            OutlinedButton(onClick = { viewModel.decide(p.payload, false) }, modifier = Modifier.weight(1f)) { Text(stringResource(R.string.link_device_reject)) }
                            Button(onClick = { viewModel.decide(p.payload, true) }, modifier = Modifier.weight(1f)) { Text(stringResource(R.string.link_device_approve)) }
                        }
                    }
                    LinkPhase.Done -> { Text(stringResource(R.string.link_device_done)); Button(onClick = onBack) { Text(stringResource(R.string.permission_continue)) } }
                    LinkPhase.Rejected -> { Text(stringResource(R.string.link_device_rejected)); Button(onClick = onBack) { Text(stringResource(R.string.permission_continue)) } }
                    LinkPhase.Failed -> { Text(stringResource(R.string.link_device_failed), color = MaterialTheme.colorScheme.error); OutlinedButton(onClick = viewModel::retry) { Text(stringResource(R.string.message_retry)) } }
                    LinkPhase.Scan -> Unit
                }
            }
        }
    }
}

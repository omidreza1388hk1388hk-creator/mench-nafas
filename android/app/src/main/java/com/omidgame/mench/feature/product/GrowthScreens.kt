package com.omidgame.mench.feature.product

import android.content.Intent
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.ViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewModelScope
import com.omidgame.mench.R
import com.omidgame.mench.core.network.PlusStatus
import com.omidgame.mench.core.network.ReferralsResponse
import com.omidgame.mench.core.network.StickerPack
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import javax.inject.Inject

// ---------------------------------------------------------------- Referrals

data class ReferralsUiState(val data: ReferralsResponse? = null, val message: Int? = null, val error: Boolean = false)

@HiltViewModel
class ReferralsViewModel @Inject constructor(private val repo: EcosystemRepository) : ViewModel() {
    private val _state = MutableStateFlow(ReferralsUiState())
    val state: StateFlow<ReferralsUiState> = _state

    init { load() }

    fun load() {
        viewModelScope.launch {
            repo.referrals().onSuccess { d -> _state.update { it.copy(data = d, error = false) } }.onFailure { _state.update { s -> s.copy(error = true) } }
        }
    }

    fun claim(code: String) {
        viewModelScope.launch {
            repo.claim(code)
                .onSuccess { _state.update { it.copy(message = R.string.referral_claimed) }; load() }
                .onFailure { _state.update { s -> s.copy(message = R.string.referral_claim_failed) } }
        }
    }
}

@Composable
fun ReferralsScreen(onBack: () -> Unit, viewModel: ReferralsViewModel = hiltViewModel()) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    val context = LocalContext.current
    var code by remember { mutableStateOf("") }
    ProductScaffold(title = stringResource(R.string.referral_title), onBack = onBack) { padding ->
        Column(Modifier.fillMaxSize().padding(padding).verticalScroll(rememberScrollState()).padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            val d = state.data
            if (d == null) {
                Text(stringResource(if (state.error) R.string.product_error_generic else R.string.referral_loading))
            } else {
                Card(Modifier.fillMaxWidth()) {
                    Column(Modifier.padding(16.dp)) {
                        Text(stringResource(R.string.referral_your_code), style = MaterialTheme.typography.labelLarge)
                        Text(d.code, style = MaterialTheme.typography.headlineSmall)
                        Button(onClick = {
                            context.startActivity(Intent.createChooser(Intent(Intent.ACTION_SEND).apply { type = "text/plain"; putExtra(Intent.EXTRA_TEXT, context.getString(R.string.find_people_invite_message, d.link)) }, null))
                        }, modifier = Modifier.padding(top = 8.dp)) { Text(stringResource(R.string.find_people_share_link)) }
                        OutlinedButton(
                            onClick = { InviteCard.share(context, d.code, d.link, context.getString(R.string.invite_card_headline), context.getString(R.string.invite_card_subtitle), null) },
                            modifier = Modifier.padding(top = 8.dp),
                        ) { Text(stringResource(R.string.invite_card_share)) }
                    }
                }
                Text(stringResource(R.string.referral_stats, d.stats.invited, d.stats.pending, d.stats.rewarded), style = MaterialTheme.typography.bodyMedium)
                if (d.rewardsEnabled) {
                    Text(stringResource(R.string.referral_rule, d.rule.qualifyMessages, d.rule.qualifyConversations, d.rule.rewardPlusDays, d.rule.maxRewards), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                } else {
                    Text(stringResource(R.string.referral_rewards_off), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
                d.history.forEach { h ->
                    Row(Modifier.fillMaxWidth()) {
                        Text("${h.initial}•••", modifier = Modifier.weight(1f))
                        Text(stringResource(when (h.status) { "rewarded" -> R.string.referral_status_rewarded; "pending" -> R.string.referral_status_pending; "qualified" -> R.string.referral_status_qualified; else -> R.string.referral_status_rejected }))
                    }
                }
            }
            OutlinedTextField(value = code, onValueChange = { code = it.trim().take(16) }, label = { Text(stringResource(R.string.referral_have_code)) }, singleLine = true, modifier = Modifier.fillMaxWidth())
            OutlinedButton(onClick = { viewModel.claim(code) }, enabled = code.length >= 6, modifier = Modifier.fillMaxWidth()) { Text(stringResource(R.string.referral_apply)) }
            state.message?.let { Text(stringResource(it), color = MaterialTheme.colorScheme.primary) }
        }
    }
}

// ---------------------------------------------------------------- MENCH Plus + Sticker Store

data class PlusUiState(val plus: PlusStatus? = null, val packs: List<StickerPack> = emptyList(), val message: Int? = null, val busy: Boolean = false, val offline: Boolean = false)

@HiltViewModel
class PlusViewModel @Inject constructor(private val repo: EcosystemRepository) : ViewModel() {
    private val _state = MutableStateFlow(PlusUiState())
    val state: StateFlow<PlusUiState> = _state

    init { load() }

    fun load() {
        viewModelScope.launch {
            repo.plus().onSuccess { p -> _state.update { it.copy(plus = p, offline = false) } }.onFailure { _state.update { s -> s.copy(offline = true) } }
            repo.packs().onSuccess { p -> _state.update { it.copy(packs = p) } }
        }
    }

    fun restore() {
        _state.update { it.copy(busy = true) }
        viewModelScope.launch {
            repo.restorePlus()
                .onSuccess { p -> _state.update { it.copy(plus = p, busy = false, offline = false, message = if (p.active) R.string.plus_restored else R.string.plus_nothing_to_restore) }; load() }
                .onFailure { _state.update { s -> s.copy(busy = false, message = R.string.product_error_generic) } }
        }
    }

    fun redeem(code: String) {
        _state.update { it.copy(busy = true) }
        viewModelScope.launch {
            repo.redeem(code)
                .onSuccess { p -> _state.update { it.copy(plus = p, busy = false, message = R.string.plus_redeemed) }; load() }
                .onFailure { _state.update { s -> s.copy(busy = false, message = R.string.plus_redeem_failed) } }
        }
    }
}

@Composable
fun PlusScreen(onBack: () -> Unit, viewModel: PlusViewModel = hiltViewModel()) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    var code by remember { mutableStateOf("") }
    ProductScaffold(title = stringResource(R.string.plus_title), onBack = onBack) { padding ->
        Column(Modifier.fillMaxSize().padding(padding).verticalScroll(rememberScrollState()).padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            val plus = state.plus
            Card(Modifier.fillMaxWidth()) {
                Column(Modifier.padding(16.dp)) {
                    Text(stringResource(if (plus?.active == true) R.string.plus_active else R.string.plus_inactive), style = MaterialTheme.typography.titleMedium)
                    plus?.expiresAt?.takeIf { plus.active }?.let { Text(stringResource(R.string.plus_until, it.take(10)), style = MaterialTheme.typography.bodySmall) }
                    if (state.offline) Text(stringResource(R.string.plus_offline), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            }
            Text(stringResource(R.string.plus_perks), style = MaterialTheme.typography.bodyMedium)
            Text(stringResource(R.string.plus_no_ads), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            OutlinedButton(onClick = viewModel::restore, enabled = !state.busy, modifier = Modifier.fillMaxWidth()) { Text(stringResource(R.string.plus_restore)) }
            OutlinedTextField(value = code, onValueChange = { code = it.trim().uppercase().take(32) }, label = { Text(stringResource(R.string.plus_code_hint)) }, singleLine = true, modifier = Modifier.fillMaxWidth())
            Button(onClick = { viewModel.redeem(code) }, enabled = code.length >= 4 && !state.busy, modifier = Modifier.fillMaxWidth()) { Text(stringResource(R.string.plus_redeem)) }
            state.message?.let { Text(stringResource(it), color = MaterialTheme.colorScheme.primary) }

            Text(stringResource(R.string.sticker_store_title), style = MaterialTheme.typography.titleMedium, modifier = Modifier.padding(top = 12.dp))
            state.packs.forEach { pack ->
                Card(Modifier.fillMaxWidth()) {
                    Column(Modifier.padding(12.dp)) {
                        Row {
                            Text(pack.name, style = MaterialTheme.typography.titleSmall, modifier = Modifier.weight(1f))
                            Text(stringResource(if (pack.premium) R.string.sticker_premium else R.string.sticker_free), style = MaterialTheme.typography.labelSmall)
                        }
                        Text(pack.glyphs.joinToString("  "), style = MaterialTheme.typography.headlineSmall, modifier = Modifier.padding(top = 6.dp))
                        if (pack.locked) Text(stringResource(R.string.sticker_locked), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                }
            }
        }
    }
}

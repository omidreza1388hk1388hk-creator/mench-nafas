package com.omidgame.mench.feature.product

import android.content.Intent
import android.net.Uri
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.ViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewModelScope
import com.omidgame.mench.BuildConfig
import com.omidgame.mench.R
import com.omidgame.mench.core.network.AnnouncementWire
import com.omidgame.mench.core.ui.components.MenchDialog
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import javax.inject.Inject

data class ProductHostUiState(
    val announcements: List<AnnouncementWire> = emptyList(),
    val updateAvailable: Boolean = false,
    val updateRequired: Boolean = false,
    val downloadUrl: String? = null,
    val askRating: Boolean = false,
)

/** Drives the announcement banner, the update prompt and the rating prompt on the main list screen. */
@HiltViewModel
class ProductHostViewModel @Inject constructor(
    private val repo: ProductRepository,
    private val prefs: ProductPrefs,
    private val ecosystem: EcosystemRepository,
) : ViewModel() {
    private val _state = MutableStateFlow(ProductHostUiState())
    val state: StateFlow<ProductHostUiState> = _state

    init {
        prefs.recordAppOpen()
        viewModelScope.launch {
            repo.track("app_open")
            // Local reminder alarms do not survive a reboot, so re-register them whenever the app opens.
            ecosystem.refreshAlarms()
            repo.refreshConfig().onSuccess { cfg ->
                val code = BuildConfig.VERSION_CODE.toLong()
                _state.update {
                    it.copy(
                        announcements = repo.visibleAnnouncements(),
                        updateAvailable = cfg.update.latestVersionCode > code && !cfg.update.downloadUrl.isNullOrBlank(),
                        updateRequired = cfg.update.minVersionCode > code && !cfg.update.downloadUrl.isNullOrBlank(),
                        downloadUrl = cfg.update.downloadUrl,
                    )
                }
            }
            // Never stack the rating prompt on top of an update/announcement.
            if (_state.value.announcements.isEmpty() && !_state.value.updateAvailable && prefs.shouldAskForRating()) {
                _state.update { it.copy(askRating = true) }
            }
        }
    }

    fun dismissAnnouncement(id: String) {
        prefs.dismissAnnouncement(id)
        _state.update { s -> s.copy(announcements = s.announcements.filterNot { it.id == id }) }
    }

    fun dismissUpdate() { _state.update { it.copy(updateAvailable = false) } }

    fun ratingAnswered(never: Boolean) {
        prefs.markRatingAsked()
        if (never) prefs.neverAskRating()
        _state.update { it.copy(askRating = false) }
    }
}

@Composable
fun ProductBannersHost(modifier: Modifier = Modifier, viewModel: ProductHostViewModel = hiltViewModel()) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    val context = LocalContext.current

    Column(modifier) {
        state.announcements.firstOrNull()?.let { a ->
            val bg = when (a.level) {
                "critical" -> MaterialTheme.colorScheme.errorContainer
                "warning" -> MaterialTheme.colorScheme.tertiaryContainer
                else -> MaterialTheme.colorScheme.primaryContainer
            }
            Row(Modifier.fillMaxWidth().background(bg).padding(start = 16.dp, top = 6.dp, bottom = 6.dp), verticalAlignment = Alignment.CenterVertically) {
                Column(Modifier.weight(1f)) {
                    Text(a.title, style = MaterialTheme.typography.labelLarge)
                    Text(a.body, style = MaterialTheme.typography.bodySmall)
                }
                IconButton(onClick = { viewModel.dismissAnnouncement(a.id) }) {
                    Icon(Icons.Filled.Close, contentDescription = stringResource(R.string.action_cancel))
                }
            }
        }
    }

    if (state.updateAvailable || state.updateRequired) {
        // Explicit type so the lambda is compiled as @Composable; a forced update has no "later" button.
        val updateDismissButton: (@Composable () -> Unit)? = if (state.updateRequired) null else {
            { TextButton(onClick = viewModel::dismissUpdate) { Text(stringResource(R.string.rating_later)) } }
        }
        MenchDialog(
            onDismissRequest = { if (!state.updateRequired) viewModel.dismissUpdate() },
            title = { Text(stringResource(R.string.update_title)) },
            text = { Text(stringResource(if (state.updateRequired) R.string.update_required_body else R.string.update_body)) },
            confirmButton = {
                TextButton(onClick = {
                    state.downloadUrl?.let { runCatching { context.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(it))) } }
                }) { Text(stringResource(R.string.update_action)) }
            },
            dismissButton = updateDismissButton,
        )
    }

    if (state.askRating) {
        MenchDialog(
            onDismissRequest = { viewModel.ratingAnswered(never = false) },
            title = { Text(stringResource(R.string.rating_title)) },
            text = { Text(stringResource(R.string.rating_body)) },
            confirmButton = {
                TextButton(onClick = {
                    viewModel.ratingAnswered(never = true)
                    val pkg = context.packageName
                    runCatching { context.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse("market://details?id=$pkg"))) }
                        .onFailure { runCatching { context.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse("https://play.google.com/store/apps/details?id=$pkg"))) } }
                }) { Text(stringResource(R.string.rating_yes)) }
            },
            dismissButton = {
                Row {
                    TextButton(onClick = { viewModel.ratingAnswered(never = false) }) { Text(stringResource(R.string.rating_later)) }
                    TextButton(onClick = { viewModel.ratingAnswered(never = true) }) { Text(stringResource(R.string.rating_never)) }
                }
            },
        )
    }
}

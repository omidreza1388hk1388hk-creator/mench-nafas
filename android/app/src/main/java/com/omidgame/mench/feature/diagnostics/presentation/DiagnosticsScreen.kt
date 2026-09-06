package com.omidgame.mench.feature.diagnostics.presentation

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material.icons.outlined.Cancel
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.ListItem
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import com.omidgame.mench.R
import com.omidgame.mench.feature.diagnostics.domain.CheckStatus
import com.omidgame.mench.feature.diagnostics.domain.DiagnosticCheck

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun DiagnosticsScreen(
    state: DiagnosticsUiState,
    onRerun: () -> Unit,
    onBackClick: () -> Unit,
) {
    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(stringResource(R.string.diagnostics_title)) },
                navigationIcon = {
                    IconButton(onClick = onBackClick) {
                        Icon(Icons.Filled.ArrowBack, contentDescription = null)
                    }
                },
                actions = {
                    IconButton(onClick = onRerun) {
                        Icon(Icons.Filled.Refresh, contentDescription = stringResource(R.string.diagnostics_rerun))
                    }
                },
            )
        },
    ) { padding ->
        when (state) {
            is DiagnosticsUiState.Loading -> {
                Box(modifier = Modifier.fillMaxSize().padding(padding), contentAlignment = Alignment.Center) {
                    CircularProgressIndicator()
                }
            }
            is DiagnosticsUiState.Content -> {
                LazyColumn(modifier = Modifier.fillMaxSize().padding(padding)) {
                    item {
                        ListItem(
                            headlineContent = { Text(stringResource(R.string.diagnostics_overall_status)) },
                            supportingContent = { Text(statusLabel(state.report.overall)) },
                            leadingContent = { StatusIcon(state.report.overall) },
                        )
                    }
                    items(state.report.checks, key = { it.name }) { check ->
                        CheckRow(check)
                    }
                }
            }
        }
    }
}

@Composable
private fun CheckRow(check: DiagnosticCheck) {
    ListItem(
        headlineContent = { Text(checkLabel(check.name)) },
        supportingContent = { Text(check.detail) },
        leadingContent = { StatusIcon(check.status) },
        modifier = Modifier.fillMaxWidth(),
    )
}

@Composable
private fun StatusIcon(status: CheckStatus) {
    when (status) {
        CheckStatus.PASS -> Icon(Icons.Filled.CheckCircle, contentDescription = null, tint = Color(0xFF2E7D32))
        CheckStatus.WARNING -> Icon(Icons.Filled.Warning, contentDescription = null, tint = Color(0xFFF9A825))
        CheckStatus.FAIL -> Icon(Icons.Outlined.Cancel, contentDescription = null, tint = MaterialTheme.colorScheme.error)
    }
}

@Composable
private fun statusLabel(status: CheckStatus): String = when (status) {
    CheckStatus.PASS -> stringResource(R.string.diagnostics_status_pass)
    CheckStatus.WARNING -> stringResource(R.string.diagnostics_status_warning)
    CheckStatus.FAIL -> stringResource(R.string.diagnostics_status_fail)
}

/** server.database / server.redis / server.storage sub-checks fall back to their raw dotted name — they're a small, rarely-seen detail tier under the main "server" check, not worth a full string-resource entry each. */
@Composable
private fun checkLabel(name: String): String = when (name) {
    "internet" -> stringResource(R.string.diagnostics_check_internet)
    "authentication" -> stringResource(R.string.diagnostics_check_authentication)
    "server" -> stringResource(R.string.diagnostics_check_server)
    "websocket" -> stringResource(R.string.diagnostics_check_websocket)
    "sync" -> stringResource(R.string.diagnostics_check_sync)
    "storage" -> stringResource(R.string.diagnostics_check_storage)
    else -> name
}

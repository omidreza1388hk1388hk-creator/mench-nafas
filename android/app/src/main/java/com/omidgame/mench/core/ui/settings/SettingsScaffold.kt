package com.omidgame.mench.core.ui.settings

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.ScrollState
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LargeTopAppBar
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.omidgame.mench.R
import com.omidgame.mench.core.ui.theme.MenchBrand
import com.omidgame.mench.core.ui.theme.menchTopBarColors

/**
 * Standard frame for every settings screen: collapsing title, back arrow (also handles the system back
 * gesture through the same [onBack]), brand gradient behind the bar, and a scrolling column.
 * Pass your own [scrollState] when the screen needs to scroll to a row (search jump).
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SettingsScaffold(
    title: String,
    onBack: () -> Unit,
    modifier: Modifier = Modifier,
    scrollState: ScrollState = rememberScrollState(),
    /** False when embedded as the right-hand pane on a tablet: the list pane owns back, so no arrow and no back handler here. */
    showBack: Boolean = true,
    content: @Composable ColumnScope.() -> Unit,
) {
    BackHandler(enabled = showBack, onBack = onBack)
    val behavior = TopAppBarDefaults.exitUntilCollapsedScrollBehavior()
    Scaffold(
        modifier = modifier.nestedScroll(behavior.nestedScrollConnection),
        topBar = {
            LargeTopAppBar(
                modifier = Modifier.background(MenchBrand.heroBrush()),
                colors = menchTopBarColors(),
                scrollBehavior = behavior,
                title = { Text(title, fontWeight = FontWeight.Bold, maxLines = 1, overflow = TextOverflow.Ellipsis) },
                navigationIcon = {
                    if (showBack) {
                        IconButton(onClick = onBack) {
                            Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = stringResource(R.string.chat_back))
                        }
                    }
                },
            )
        },
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .verticalScroll(scrollState)
                .padding(horizontal = 12.dp, vertical = 12.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp),
            content = content,
        )
    }
}

/** "Reset this section" row + typed-free confirmation. Resetting never touches messages, accounts or keys. */
@Composable
fun ResetSectionRow(
    onReset: () -> Unit,
    modifier: Modifier = Modifier,
) {
    var asking by remember { mutableStateOf(false) }
    SettingsSection(title = null, modifier = modifier) {
        SettingsRow(
            title = stringResource(R.string.sx_reset_section),
            subtitle = stringResource(R.string.sx_reset_section_desc),
            titleColor = MaterialTheme.colorScheme.error,
            onClick = { asking = true },
        )
    }
    if (asking) {
        ConfirmDialog(
            title = stringResource(R.string.sx_reset_section),
            message = stringResource(R.string.sx_reset_section_desc),
            confirmLabel = stringResource(R.string.sx_reset_confirm),
            destructive = true,
            onConfirm = {
                asking = false
                onReset()
            },
            onDismiss = { asking = false },
        )
    }
}

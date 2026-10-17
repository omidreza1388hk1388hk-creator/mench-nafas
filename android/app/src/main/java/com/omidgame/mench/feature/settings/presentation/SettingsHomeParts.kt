package com.omidgame.mench.feature.settings.presentation

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.omidgame.mench.R
import com.omidgame.mench.core.ui.settings.ConfirmDialog
import com.omidgame.mench.core.ui.settings.SettingsRow
import com.omidgame.mench.core.ui.settings.SettingsSection
import com.omidgame.mench.feature.settings.domain.SettingKey
import com.omidgame.mench.feature.settings.domain.SettingsCategory
import com.omidgame.mench.feature.settings.domain.SettingsSearchHit

/** Search box on the settings home. Clearing restores the category list. */
@Composable
fun SettingsSearchField(
    query: String,
    onQueryChange: (String) -> Unit,
    modifier: Modifier = Modifier,
) {
    OutlinedTextField(
        value = query,
        onValueChange = onQueryChange,
        singleLine = true,
        placeholder = { Text(stringResource(R.string.sx_search_hint)) },
        leadingIcon = { Icon(Icons.Filled.Search, contentDescription = null) },
        trailingIcon = {
            if (query.isNotEmpty()) {
                IconButton(onClick = { onQueryChange("") }) {
                    Icon(Icons.Filled.Close, contentDescription = stringResource(R.string.sx_search_clear))
                }
            }
        },
        shape = RoundedCornerShape(24.dp),
        modifier = modifier.fillMaxWidth(),
    )
}

/** One card row per category: icon tile, title, live one-line summary and an optional attention badge. */
@Composable
fun CategoryCardRow(
    category: SettingsCategory,
    summary: String,
    needsAttention: Boolean,
    onClick: () -> Unit,
) {
    val visual = categoryVisual(category)
    val badge: (@Composable () -> Unit)? = if (needsAttention) {
        @Composable {
            Text(
                text = stringResource(R.string.sx_badge_attention),
                style = MaterialTheme.typography.labelMedium,
                fontWeight = FontWeight.SemiBold,
                color = MaterialTheme.colorScheme.onErrorContainer,
                modifier = Modifier
                    .clip(RoundedCornerShape(12.dp))
                    .background(MaterialTheme.colorScheme.errorContainer)
                    .padding(horizontal = 10.dp, vertical = 4.dp),
            )
        }
    } else {
        null
    }
    SettingsRow(
        title = stringResource(categoryTitleRes(category)),
        subtitle = summary,
        leadingIcon = visual.icon,
        iconTint = visual.tint,
        onClick = onClick,
        trailing = badge,
    )
}

/** Search results (or the empty state). Each hit shows which category it lives in. */
@Composable
fun SettingsSearchResults(
    query: String,
    hits: List<SettingsSearchHit>,
    onOpen: (SettingKey) -> Unit,
) {
    if (hits.isEmpty()) {
        Column(
            modifier = Modifier.fillMaxWidth().padding(24.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(6.dp),
        ) {
            Text(
                stringResource(R.string.sx_search_empty, query.trim()),
                style = MaterialTheme.typography.titleSmall,
                textAlign = TextAlign.Center,
            )
            Text(
                stringResource(R.string.sx_search_empty_hint),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                textAlign = TextAlign.Center,
            )
        }
        return
    }
    SettingsSection(title = null) {
        hits.forEach { hit ->
            val key = hit.key
            val visual = settingVisual(key.id, key.category)
            val where = stringResource(R.string.sx_search_result_in, stringResource(categoryTitleRes(key.category)))
            SettingsRow(
                title = stringResource(key.titleRes),
                subtitle = where,
                leadingIcon = visual.icon,
                iconTint = visual.tint,
                onClick = { onOpen(key) },
            )
        }
    }
}

/** "Reset all settings": typed confirmation first. Never deletes messages, accounts or encryption keys. */
@Composable
fun ResetAllSettingsRow(onReset: () -> Unit) {
    var asking by remember { mutableStateOf(false) }
    SettingsSection(title = null) {
        SettingsRow(
            title = stringResource(R.string.sx_reset_all),
            subtitle = stringResource(R.string.sx_reset_all_desc),
            titleColor = MaterialTheme.colorScheme.error,
            onClick = { asking = true },
        )
    }
    if (asking) {
        ConfirmDialog(
            title = stringResource(R.string.sx_reset_all),
            message = stringResource(R.string.sx_reset_all_desc),
            confirmLabel = stringResource(R.string.sx_reset_confirm),
            requiredWord = stringResource(R.string.sx_reset_all_word),
            destructive = true,
            onConfirm = {
                asking = false
                onReset()
            },
            onDismiss = { asking = false },
        )
    }
}

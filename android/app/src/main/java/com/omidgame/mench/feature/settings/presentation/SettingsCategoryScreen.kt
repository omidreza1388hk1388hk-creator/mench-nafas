package com.omidgame.mench.feature.settings.presentation

import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.relocation.BringIntoViewRequester
import androidx.compose.foundation.relocation.bringIntoViewRequester
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import com.omidgame.mench.R
import com.omidgame.mench.core.ui.settings.InfoBanner
import com.omidgame.mench.core.ui.settings.ResetSectionRow
import com.omidgame.mench.core.ui.settings.SettingsRow
import com.omidgame.mench.core.ui.settings.SettingsScaffold
import com.omidgame.mench.core.ui.settings.SettingsSection
import com.omidgame.mench.feature.settings.domain.SettingsCategory
import com.omidgame.mench.feature.settings.domain.SettingsRegistry
import com.omidgame.mench.feature.settings.domain.SettingsTarget
import kotlinx.coroutines.delay

/**
 * One settings category: every registry row of [category] as a list. [highlightId] (set when the user came
 * from search) scrolls that row into view and flashes it briefly.
 */
@OptIn(ExperimentalFoundationApi::class)
@Composable
fun SettingsCategoryScreen(
    category: SettingsCategory,
    highlightId: String?,
    onNavigate: (SettingsTarget) -> Unit,
    onBack: () -> Unit,
    onReset: (() -> Unit)? = null,
    embedded: Boolean = false,
) {
    val rows = remember(category) { SettingsRegistry.byCategory(category) }
    val requester = remember { BringIntoViewRequester() }
    var flashId by remember { mutableStateOf<String?>(null) }

    LaunchedEffect(highlightId) {
        if (highlightId != null && rows.any { it.id == highlightId }) {
            delay(300) // let the first layout settle so bringIntoView has a target
            requester.bringIntoView()
            flashId = highlightId
            delay(1600)
            flashId = null
        }
    }

    SettingsActionHost { runAction ->
        SettingsScaffold(title = stringResource(categoryTitleRes(category)), onBack = onBack, showBack = !embedded) {
            if (rows.isEmpty()) {
                InfoBanner(text = stringResource(R.string.sx_category_empty))
            } else {
                SettingsSection(title = stringResource(categoryDescRes(category))) {
                    rows.forEach { key ->
                        val visual = settingVisual(key.id, key.category)
                        SettingsRow(
                            title = stringResource(key.titleRes),
                            subtitle = key.subtitleRes?.let { stringResource(it) },
                            leadingIcon = visual.icon,
                            iconTint = visual.tint,
                            highlighted = flashId == key.id,
                            modifier = if (key.id == highlightId) Modifier.bringIntoViewRequester(requester) else Modifier,
                            onClick = {
                                when (val t = key.target) {
                                    is SettingsTarget.Action -> runAction(t.id)
                                    else -> onNavigate(t)
                                }
                            },
                        )
                    }
                }
            }
            if (onReset != null) ResetSectionRow(onReset = onReset)
        }
    }
}

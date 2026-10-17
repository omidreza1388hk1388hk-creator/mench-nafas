package com.omidgame.mench.feature.settings.presentation

import androidx.compose.runtime.Composable
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import com.omidgame.mench.R
import com.omidgame.mench.core.ui.settings.ChoiceOption
import com.omidgame.mench.core.ui.settings.SettingsScaffold
import com.omidgame.mench.core.ui.settings.SettingsSection
import com.omidgame.mench.core.ui.settings.SingleChoiceRow
import com.omidgame.mench.core.ui.settings.SwitchRow
import com.omidgame.mench.feature.chat.presentation.UsSettingsStore
import com.omidgame.mench.feature.chat.presentation.rememberUsSettings
import com.omidgame.mench.feature.settings.domain.HapticStrength

/** Couple features: hide what you do not use, and tune how hugs, kisses and heartbeats feel. */
@Composable
fun UsSettingsScreen(onBack: () -> Unit) {
    val context = LocalContext.current
    val s = rememberUsSettings()
    SettingsScaffold(title = stringResource(R.string.sx_us_title), onBack = onBack) {
        SettingsSection(title = stringResource(R.string.sx_us_features)) {
            SwitchRow(title = stringResource(R.string.sx_us_mood), checked = s.moodEnabled, onCheckedChange = { UsSettingsStore.setMood(context, it) })
            SwitchRow(title = stringResource(R.string.question_title), checked = s.questionEnabled, onCheckedChange = { UsSettingsStore.setQuestion(context, it) })
            SwitchRow(title = stringResource(R.string.wishlist_title), checked = s.wishlistEnabled, onCheckedChange = { UsSettingsStore.setWishlist(context, it) })
            SwitchRow(
                title = stringResource(R.string.sx_us_memories),
                subtitle = stringResource(R.string.sx_us_features_sub),
                checked = s.memoriesEnabled,
                onCheckedChange = { UsSettingsStore.setMemories(context, it) },
            )
        }
        SettingsSection(title = stringResource(R.string.sx_us_feel)) {
            SingleChoiceRow(
                title = stringResource(R.string.sx_us_haptics),
                subtitle = stringResource(R.string.sx_us_haptics_sub),
                options = listOf(
                    ChoiceOption(HapticStrength.SOFT, stringResource(R.string.sx_us_haptics_soft)),
                    ChoiceOption(HapticStrength.NORMAL, stringResource(R.string.sx_us_haptics_normal)),
                    ChoiceOption(HapticStrength.STRONG, stringResource(R.string.sx_us_haptics_strong)),
                ),
                selected = s.hapticStrength,
                onSelected = { UsSettingsStore.setHapticStrength(context, it) },
            )
            SwitchRow(
                title = stringResource(R.string.sx_us_hearts),
                subtitle = stringResource(R.string.sx_us_hearts_sub),
                checked = s.heartsAnimation,
                onCheckedChange = { UsSettingsStore.setHeartsAnimation(context, it) },
            )
        }
    }
}

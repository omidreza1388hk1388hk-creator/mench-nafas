package com.omidgame.mench.feature.settings.presentation

import android.app.Activity
import android.content.Context
import android.content.ContextWrapper
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
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
import com.omidgame.mench.R
import com.omidgame.mench.core.ui.settings.ChoiceOption
import com.omidgame.mench.core.ui.settings.SettingsRow
import com.omidgame.mench.core.ui.settings.SettingsScaffold
import com.omidgame.mench.core.ui.settings.SettingsSection
import com.omidgame.mench.core.ui.settings.SingleChoiceRow
import com.omidgame.mench.core.ui.theme.AppLanguageStore
import com.omidgame.mench.core.ui.theme.MenchCalendar
import com.omidgame.mench.feature.settings.domain.AppLanguage

private tailrec fun Context.findActivity(): Activity? = when (this) {
    is Activity -> this
    is ContextWrapper -> baseContext.findActivity()
    else -> null
}

/** Language and region: in-app language with immediate effect, a live preview, and a shortcut to calendar and digits. */
@Composable
fun LanguageRegionScreen(onBack: () -> Unit, onOpenAppearance: () -> Unit) {
    val context = LocalContext.current
    var language by remember { mutableStateOf(AppLanguageStore.get(context)) }
    // The preview uses the same calendar and digit style as the rest of the app (MenchCalendar), so what you see here is what you get.
    val now = remember { java.util.Date() }
    val dateText = android.icu.text.DateFormat.getDateInstance(android.icu.text.DateFormat.LONG, MenchCalendar.locale()).format(now)
    val timeText = android.icu.text.DateFormat.getTimeInstance(android.icu.text.DateFormat.SHORT, MenchCalendar.locale()).format(now)
    val numberText = android.icu.text.NumberFormat.getInstance(MenchCalendar.locale()).format(1234567)

    SettingsScaffold(title = stringResource(R.string.sx_lang_title), onBack = onBack) {
        SettingsSection(title = stringResource(R.string.sx_lang_language)) {
            SingleChoiceRow(
                title = stringResource(R.string.sx_lang_language),
                subtitle = stringResource(R.string.sx_lang_language_sub),
                options = listOf(
                    ChoiceOption(AppLanguage.SYSTEM, stringResource(R.string.sx_lang_system)),
                    ChoiceOption(AppLanguage.PERSIAN, "فارسی"),
                    ChoiceOption(AppLanguage.ENGLISH, "English"),
                ),
                selected = language,
                onSelected = {
                    if (it != language) {
                        language = it
                        AppLanguageStore.set(context, it)
                        // Recreate so every screen is rebuilt with the new language and layout direction.
                        context.findActivity()?.recreate()
                    }
                },
            )
        }
        SettingsSection(title = stringResource(R.string.sx_lang_preview)) {
            SettingsRow(title = stringResource(R.string.sx_lang_preview_date), subtitle = dateText)
            SettingsRow(title = stringResource(R.string.sx_lang_preview_time), subtitle = timeText)
            SettingsRow(title = stringResource(R.string.sx_lang_preview_number), subtitle = numberText)
            Text(
                stringResource(R.string.sx_lang_preview_note),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(14.dp),
            )
        }
        SettingsSection(title = null) {
            SettingsRow(
                title = stringResource(R.string.sx_lang_calendar_row),
                subtitle = stringResource(R.string.sx_lang_calendar_row_sub),
                onClick = onOpenAppearance,
            )
        }
    }
}

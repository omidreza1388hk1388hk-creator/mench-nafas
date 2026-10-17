package com.omidgame.mench.feature.appearance.presentation

import com.omidgame.mench.core.ui.theme.MenchBrand
import com.omidgame.mench.core.ui.theme.menchTopBarColors
import androidx.compose.foundation.background
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.defaultMinSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.selection.toggleable
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Check
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.omidgame.mench.R
import com.omidgame.mench.core.ui.theme.swatch
import com.omidgame.mench.feature.appearance.domain.AccentColor
import com.omidgame.mench.feature.appearance.domain.AppearanceSettings
import com.omidgame.mench.feature.appearance.domain.BubbleStyle
import com.omidgame.mench.feature.appearance.domain.CalendarStyle
import com.omidgame.mench.feature.appearance.domain.DigitStyle
import com.omidgame.mench.feature.appearance.domain.FontScale
import com.omidgame.mench.feature.appearance.domain.ThemeMode

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AppearanceSettingsScreen(
    state: AppearanceSettingsUiState,
    onThemeModeSelected: (ThemeMode) -> Unit,
    onAccentColorSelected: (AccentColor) -> Unit,
    onFontScaleSelected: (FontScale) -> Unit,
    onBubbleStyleSelected: (BubbleStyle) -> Unit,
    onDynamicColorSelected: (Boolean) -> Unit = {},
    onAmoledSelected: (Boolean) -> Unit = {},
    onPresetSelected: (ThemePreset) -> Unit = {},
    onDigitStyleSelected: (DigitStyle) -> Unit = {},
    onCalendarStyleSelected: (CalendarStyle) -> Unit = {},
    onScreenSecurityChanged: (Boolean) -> Unit = {},
    onBackClick: () -> Unit,
) {
    val context = androidx.compose.ui.platform.LocalContext.current
    val systemDark = isSystemInDarkTheme()
    Scaffold(
        topBar = {
            TopAppBar(
                modifier = Modifier.background(MenchBrand.heroBrush()),
                colors = menchTopBarColors(),
                title = { Text(stringResource(R.string.appearance_title)) },
                navigationIcon = {
                    IconButton(onClick = onBackClick) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = stringResource(R.string.chat_back))
                    }
                },
            )
        },
    ) { padding ->
        Column(
            modifier = Modifier.fillMaxWidth().padding(padding).padding(vertical = 8.dp),
        ) {
            LivePreview(settings = state.settings)

            SectionLabel(stringResource(R.string.appearance_section_presets))
            ThemePresetRow(
                settings = state.settings,
                systemDark = systemDark,
                onPick = { preset ->
                    // The wallpaper lives in the chat's own store; everything else goes through the ViewModel.
                    com.omidgame.mench.feature.chat.presentation.ChatWallpaper.set(context, preset.wallpaper)
                    onPresetSelected(preset)
                },
            )

            Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 8.dp),
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Text(stringResource(R.string.appearance_amoled), style = MaterialTheme.typography.bodyLarge)
                    Text(
                        stringResource(R.string.appearance_amoled_hint),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
                androidx.compose.material3.Switch(checked = state.settings.useAmoled, onCheckedChange = onAmoledSelected)
            }

            if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.S) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 8.dp),
                ) {
                    Column(modifier = Modifier.weight(1f)) {
                        Text(stringResource(R.string.appearance_material_you), style = MaterialTheme.typography.bodyLarge)
                        Text(
                            stringResource(R.string.appearance_material_you_hint),
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                    androidx.compose.material3.Switch(
                        checked = state.settings.useDynamicColor,
                        onCheckedChange = onDynamicColorSelected,
                    )
                }
            }

            SectionLabel(stringResource(R.string.appearance_section_theme_mode))
            ThemeModeOption(
                label = stringResource(R.string.appearance_theme_light),
                selected = state.settings.themeMode == ThemeMode.LIGHT,
                onClick = { onThemeModeSelected(ThemeMode.LIGHT) },
            )
            ThemeModeOption(
                label = stringResource(R.string.appearance_theme_dark),
                selected = state.settings.themeMode == ThemeMode.DARK,
                onClick = { onThemeModeSelected(ThemeMode.DARK) },
            )
            ThemeModeOption(
                label = stringResource(R.string.appearance_theme_system),
                selected = state.settings.themeMode == ThemeMode.SYSTEM,
                onClick = { onThemeModeSelected(ThemeMode.SYSTEM) },
            )

            SectionLabel(stringResource(R.string.appearance_section_accent_color))
            AccentColorRow(
                selected = state.settings.accentColor,
                darkTheme = effectiveDarkTheme(state.settings.themeMode),
                onSelected = onAccentColorSelected,
            )

            SectionLabel(stringResource(R.string.appearance_section_font_size))
            FontScaleRow(selected = state.settings.fontScale, onSelected = onFontScaleSelected)

            SectionLabel(stringResource(R.string.appearance_section_bubble_style))
            BubbleStyleRow(selected = state.settings.bubbleStyle, onSelected = onBubbleStyleSelected)

            SectionLabel(stringResource(R.string.appearance_section_digits))
            DigitStyleRow(selected = state.settings.digitStyle, onSelected = onDigitStyleSelected)

            SectionLabel(stringResource(R.string.appearance_section_calendar))
            CalendarStyleRow(selected = state.settings.calendarStyle, onSelected = onCalendarStyleSelected)

            SectionLabel(stringResource(R.string.appearance_screen_security))
            ScreenSecurityRow(checked = state.settings.screenSecurity, onCheckedChange = onScreenSecurityChanged)
            com.omidgame.mench.core.sound.SoundSettingRow()

            Spacer(modifier = Modifier.height(24.dp))
        }
    }
}

@Composable
private fun effectiveDarkTheme(mode: ThemeMode): Boolean = when (mode) {
    ThemeMode.LIGHT -> false
    ThemeMode.DARK -> true
    ThemeMode.SYSTEM -> isSystemInDarkTheme()
}

@Composable
private fun SectionLabel(text: String) {
    Text(
        text = text,
        style = MaterialTheme.typography.labelLarge,
        color = MaterialTheme.colorScheme.primary,
        modifier = Modifier.padding(start = 16.dp, top = 20.dp, bottom = 4.dp),
    )
}

@Composable
private fun ThemeModeOption(label: String, selected: Boolean, onClick: () -> Unit) {
    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = Modifier
            .fillMaxWidth()
            .selectable(selected = selected, onClick = onClick)
            .padding(horizontal = 12.dp, vertical = 2.dp),
    ) {
        RadioButton(selected = selected, onClick = onClick)
        Text(text = label, modifier = Modifier.padding(start = 8.dp))
    }
}

/**
 * Swatches always render both this row's own light/dark accent value
 * AND the actual current MaterialTheme.colorScheme — the two intentionally
 * agree, since [ThemeSettingsViewModel] has already re-themed the whole
 * app by the time this screen recomposes with a new selection.
 */
@Composable
private fun AccentColorRow(selected: AccentColor, darkTheme: Boolean, onSelected: (AccentColor) -> Unit) {
    LazyRow(
        horizontalArrangement = Arrangement.spacedBy(14.dp),
        contentPadding = PaddingValues(horizontal = 16.dp),
    ) {
        items(AccentColor.entries) { accent ->
            val color = accent.swatch(darkTheme)
            val isSelected = accent == selected
            Box(
                modifier = Modifier
                    .size(44.dp)
                    .clip(CircleShape)
                    .background(color)
                    .selectable(selected = isSelected, onClick = { onSelected(accent) }),
                contentAlignment = Alignment.Center,
            ) {
                if (isSelected) {
                    Icon(Icons.Filled.Check, contentDescription = null, tint = androidx.compose.ui.graphics.Color.White)
                }
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun FontScaleRow(selected: FontScale, onSelected: (FontScale) -> Unit) {
    Row(
        modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        FilterChip(
            selected = selected == FontScale.SMALL,
            onClick = { onSelected(FontScale.SMALL) },
            label = { Text(stringResource(R.string.appearance_font_small)) },
        )
        FilterChip(
            selected = selected == FontScale.MEDIUM,
            onClick = { onSelected(FontScale.MEDIUM) },
            label = { Text(stringResource(R.string.appearance_font_medium)) },
        )
        FilterChip(
            selected = selected == FontScale.LARGE,
            onClick = { onSelected(FontScale.LARGE) },
            label = { Text(stringResource(R.string.appearance_font_large)) },
        )
        FilterChip(
            selected = selected == FontScale.EXTRA_LARGE,
            onClick = { onSelected(FontScale.EXTRA_LARGE) },
            label = { Text(stringResource(R.string.appearance_font_extra_large)) },
        )
    }
}

@Composable
private fun BubbleStyleRow(selected: BubbleStyle, onSelected: (BubbleStyle) -> Unit) {
    Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
    Row(
        modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp),
        horizontalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        BubbleStyleCard(
            label = stringResource(R.string.appearance_bubble_pill),
            shape = RoundedCornerShape(32.dp),
            selected = selected == BubbleStyle.PILL,
            onClick = { onSelected(BubbleStyle.PILL) },
            modifier = Modifier.weight(1f),
        )
        BubbleStyleCard(
            label = stringResource(R.string.appearance_bubble_leaf),
            shape = RoundedCornerShape(topStart = 20.dp, topEnd = 20.dp, bottomEnd = 3.dp, bottomStart = 20.dp),
            selected = selected == BubbleStyle.LEAF,
            onClick = { onSelected(BubbleStyle.LEAF) },
            modifier = Modifier.weight(1f),
        )
    }
    Row(
        modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp),
        horizontalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        BubbleStyleCard(
            label = stringResource(R.string.appearance_bubble_rounded),
            shape = RoundedCornerShape(20.dp),
            selected = selected == BubbleStyle.ROUNDED,
            onClick = { onSelected(BubbleStyle.ROUNDED) },
            modifier = Modifier.weight(1f),
        )
        BubbleStyleCard(
            label = stringResource(R.string.appearance_bubble_squared),
            shape = RoundedCornerShape(4.dp),
            selected = selected == BubbleStyle.SQUARED,
            onClick = { onSelected(BubbleStyle.SQUARED) },
            modifier = Modifier.weight(1f),
        )
    }
    }
}

@Composable
private fun BubbleStyleCard(
    label: String,
    shape: RoundedCornerShape,
    selected: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Card(
        onClick = onClick,
        modifier = modifier,
        colors = if (selected) {
            CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.primaryContainer)
        } else {
            CardDefaults.cardColors()
        },
    ) {
        Column(
            modifier = Modifier.fillMaxWidth().padding(12.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            Box(
                modifier = Modifier
                    .height(28.dp)
                    .width(64.dp)
                    .clip(shape)
                    .background(MaterialTheme.colorScheme.primary),
            )
            Spacer(modifier = Modifier.height(8.dp))
            Text(text = label, style = MaterialTheme.typography.bodyMedium)
        }
    }
}

/**
 * Uses the ambient MaterialTheme directly rather than building a second,
 * parallel preview theme — by the time this recomposes with a freshly
 * tapped setting, MainActivity's MenchTheme(...) has already re-themed
 * the whole app (see ThemeSettingsViewModel's doc comment), so what's
 * rendered here IS what every other chat bubble in the app looks like.
 */
@Composable
private fun LivePreview(settings: AppearanceSettings) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 8.dp),
    ) {
        Box(
            modifier = Modifier
                .align(Alignment.Start)
                .clip(MaterialTheme.shapes.large)
                .background(MaterialTheme.colorScheme.surfaceVariant)
                .padding(horizontal = 14.dp, vertical = 10.dp),
        ) {
            Text(
                text = stringResource(R.string.appearance_preview_received),
                style = MaterialTheme.typography.bodyLarge,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        Spacer(modifier = Modifier.height(8.dp))
        Box(
            modifier = Modifier
                .align(Alignment.End)
                .clip(MaterialTheme.shapes.large)
                .background(MaterialTheme.colorScheme.primary)
                .padding(horizontal = 14.dp, vertical = 10.dp),
        ) {
            Text(
                text = stringResource(R.string.appearance_preview_sent),
                style = MaterialTheme.typography.bodyLarge,
                color = MaterialTheme.colorScheme.onPrimary,
                textAlign = TextAlign.End,
            )
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun DigitStyleRow(selected: DigitStyle, onSelected: (DigitStyle) -> Unit) {
    Row(
        modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        FilterChip(
            selected = selected == DigitStyle.AUTO,
            onClick = { onSelected(DigitStyle.AUTO) },
            label = { Text(stringResource(R.string.appearance_digits_auto)) },
        )
        FilterChip(
            selected = selected == DigitStyle.PERSIAN,
            onClick = { onSelected(DigitStyle.PERSIAN) },
            label = { Text(stringResource(R.string.appearance_digits_persian)) },
        )
        FilterChip(
            selected = selected == DigitStyle.LATIN,
            onClick = { onSelected(DigitStyle.LATIN) },
            label = { Text(stringResource(R.string.appearance_digits_latin)) },
        )
    }
}

/** One toggleable row (label + hint + switch): TalkBack reads it as a single switch control. */
@Composable
private fun ScreenSecurityRow(checked: Boolean, onCheckedChange: (Boolean) -> Unit) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .defaultMinSize(minHeight = 48.dp)
            .toggleable(
                value = checked,
                role = Role.Switch,
                onValueChange = onCheckedChange,
            )
            .padding(horizontal = 16.dp, vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            text = stringResource(R.string.appearance_screen_security_hint),
            style = MaterialTheme.typography.bodyMedium,
            modifier = Modifier.weight(1f),
        )
        Spacer(modifier = Modifier.width(12.dp))
        // Null callback: the whole row is the single toggle target (avoids a nested focus stop).
        Switch(checked = checked, onCheckedChange = null)
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun CalendarStyleRow(selected: CalendarStyle, onSelected: (CalendarStyle) -> Unit) {
    Row(
        modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        FilterChip(
            selected = selected == CalendarStyle.JALALI,
            onClick = { onSelected(CalendarStyle.JALALI) },
            label = { Text(stringResource(R.string.appearance_calendar_jalali)) },
        )
        FilterChip(
            selected = selected == CalendarStyle.GREGORIAN,
            onClick = { onSelected(CalendarStyle.GREGORIAN) },
            label = { Text(stringResource(R.string.appearance_calendar_gregorian)) },
        )
    }
}

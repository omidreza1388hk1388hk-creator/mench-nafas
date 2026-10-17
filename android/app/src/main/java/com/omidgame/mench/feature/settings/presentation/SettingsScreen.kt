package com.omidgame.mench.feature.settings.presentation

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.Logout
import androidx.compose.material.icons.filled.BatteryChargingFull
import androidx.compose.material.icons.filled.ChevronRight
import androidx.compose.material.icons.filled.DeleteForever
import androidx.compose.material.icons.filled.Favorite
import androidx.compose.material.icons.filled.Description
import androidx.compose.material.icons.filled.HelpOutline
import androidx.compose.material.icons.filled.NewReleases
import androidx.compose.material.icons.filled.PersonAdd
import androidx.compose.material.icons.filled.PrivacyTip
import androidx.compose.material.icons.filled.HealthAndSafety
import androidx.compose.material.icons.filled.Notifications
import androidx.compose.material.icons.filled.Palette
import androidx.compose.material.icons.filled.Person
import androidx.compose.material.icons.filled.Shield
import androidx.compose.material.icons.filled.Sync
import androidx.compose.material.icons.filled.Storage
import androidx.compose.material.icons.filled.PersonAdd
import androidx.compose.material.icons.filled.SwitchAccount
import androidx.compose.material.icons.filled.QrCodeScanner
import androidx.compose.material.icons.filled.CardGiftcard
import androidx.compose.material.icons.filled.Bookmark
import androidx.compose.material.icons.filled.Alarm
import androidx.compose.material.icons.filled.Star
import androidx.compose.material.icons.filled.Block
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.Feedback
import androidx.compose.material.icons.filled.Description
import androidx.compose.material.icons.filled.NewReleases
import androidx.compose.material.icons.filled.DeleteForever
import androidx.compose.material.icons.filled.Wallpaper
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.omidgame.mench.BuildConfig
import com.omidgame.mench.R
import com.omidgame.mench.core.ui.components.UserAvatar
import com.omidgame.mench.core.ui.theme.MenchBrand
import com.omidgame.mench.feature.settings.domain.SettingKey
import com.omidgame.mench.feature.settings.domain.SettingsCategory
import com.omidgame.mench.feature.settings.domain.SettingsRegistry
import com.omidgame.mench.feature.settings.domain.SettingsSearchIndex

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SettingsScreen(
    state: SettingsUiState,
    onAccountClick: () -> Unit,
    onAppearanceClick: () -> Unit = {},
    onPrivacySecurityClick: () -> Unit,
    onDataStorageClick: () -> Unit,
    onNotificationsClick: () -> Unit = {},
    onDiagnosticsClick: () -> Unit = {},
    /** Phase 1C rows: keys are resolved by ProductRoutes.forSettingsKey. */
    onProductClick: (String) -> Unit = {},
    onLogout: () -> Unit,
    onLoggedOut: () -> Unit,
    onBackClick: () -> Unit,
    /** Phase 1A: open a category screen / jump from a search hit to its row. */
    onOpenCategory: (SettingsCategory) -> Unit = {},
    onOpenSearchHit: (SettingKey) -> Unit = {},
    onResetAll: () -> Unit = {},
) {
    LaunchedEffect(state.loggedOut) {
        if (state.loggedOut) onLoggedOut()
    }
    var query by remember { mutableStateOf("") }
    val context = LocalContext.current
    // Rebuilt when the UI language changes so Persian and English titles both resolve.
    val index = remember(java.util.Locale.getDefault().toLanguageTag()) {
        SettingsSearchIndex(SettingsRegistry.all) { context.getString(it) }
    }
    val hits = remember(query, index) { index.search(query) }

    Scaffold(
        topBar = {
            TopAppBar(
                modifier = Modifier.background(MenchBrand.heroBrush()),
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = Color.Transparent,
                    scrolledContainerColor = Color.Transparent,
                    titleContentColor = MaterialTheme.colorScheme.onPrimary,
                    navigationIconContentColor = MaterialTheme.colorScheme.onPrimary,
                ),
                title = { Text(stringResource(R.string.settings_title), fontWeight = FontWeight.Bold) },
                navigationIcon = {
                    IconButton(onClick = onBackClick) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = stringResource(R.string.chat_back))
                    }
                },
            )
        },
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 12.dp, vertical = 12.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp),
        ) {
            // Profile card
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .shadow(6.dp, RoundedCornerShape(24.dp), clip = false, spotColor = MaterialTheme.colorScheme.primary.copy(alpha = 0.35f))
                    .clip(RoundedCornerShape(24.dp))
                    .background(MenchBrand.heroBrush())
                    .clickable(onClick = onAccountClick)
                    .padding(18.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                UserAvatar(
                    userId = state.userId,
                    version = state.avatarVersion,
                    name = state.displayName ?: state.phoneE164,
                    size = 64.dp,
                    modifier = Modifier.border(2.dp, Color.White.copy(alpha = 0.8f), CircleShape),
                )
                Spacer(Modifier.width(16.dp))
                Column(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(3.dp)) {
                    Text(
                        text = state.displayName ?: state.phoneE164 ?: "",
                        style = MaterialTheme.typography.titleLarge,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.onPrimary,
                    )
                    if (state.displayName != null) {
                        Text(
                            text = state.phoneE164 ?: "",
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onPrimary.copy(alpha = 0.85f),
                        )
                    }
                }
                Icon(Icons.Filled.ChevronRight, contentDescription = null, tint = MaterialTheme.colorScheme.onPrimary)
            }

            SettingsSearchField(query = query, onQueryChange = { query = it })

            if (query.isNotBlank()) {
                SettingsSearchResults(query = query, hits = hits, onOpen = onOpenSearchHit)
            } else {
                com.omidgame.mench.feature.product.RestrictionNotice()

                SettingsSection(title = stringResource(R.string.sx_section_destinations)) {
                    SettingsCategory.entries.forEach { category ->
                        CategoryCardRow(
                            category = category,
                            summary = categorySummary(category, state),
                            needsAttention = category == SettingsCategory.SECURITY && !state.deviceScreenLockSet,
                            onClick = { onOpenCategory(category) },
                        )
                    }
                }

                ResetAllSettingsRow(onReset = onResetAll)
            }

            if (state.isLoggingOut) {
                Box(Modifier.fillMaxWidth().padding(8.dp), contentAlignment = Alignment.Center) {
                    CircularProgressIndicator()
                }
            } else {
                SettingsSection(title = null) {
                    SettingsRow(
                        icon = Icons.Filled.DeleteForever,
                        tint = MaterialTheme.colorScheme.error,
                        title = stringResource(R.string.delete_account_title),
                        titleColor = MaterialTheme.colorScheme.error,
                        onClick = { onProductClick("delete_account") },
                    )
                    SettingsRow(
                        icon = Icons.AutoMirrored.Filled.Logout,
                        tint = MaterialTheme.colorScheme.error,
                        title = stringResource(R.string.settings_logout),
                        titleColor = MaterialTheme.colorScheme.error,
                        showChevron = false,
                        onClick = onLogout,
                    )
                }
            }

            Text(
                text = stringResource(R.string.settings_version, BuildConfig.VERSION_NAME),
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.6f),
                modifier = Modifier.fillMaxWidth().padding(bottom = 16.dp),
                textAlign = androidx.compose.ui.text.style.TextAlign.Center,
            )
        }
    }
}

/** A titled group of rows drawn as one rounded card. */
@Composable
private fun SettingsSection(title: String?, content: @Composable () -> Unit) {
    val dark = MaterialTheme.colorScheme.background.luminance() < 0.5f
    Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
        if (title != null) {
            Text(
                text = title,
                style = MaterialTheme.typography.labelLarge,
                fontWeight = FontWeight.SemiBold,
                color = MaterialTheme.colorScheme.primary,
                modifier = Modifier.padding(horizontal = 8.dp),
            )
        }
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .shadow(1.dp, RoundedCornerShape(20.dp), clip = false)
                .clip(RoundedCornerShape(20.dp))
                .background(if (dark) MaterialTheme.colorScheme.surfaceContainerLow else MaterialTheme.colorScheme.surfaceContainerLowest)
                .border(0.5.dp, MenchBrand.hairline(), RoundedCornerShape(20.dp)),
        ) {
            content()
        }
    }
}

@Composable
private fun SettingsRow(
    icon: ImageVector,
    tint: Color,
    title: String,
    onClick: () -> Unit,
    subtitle: String? = null,
    titleColor: Color = Color.Unspecified,
    showChevron: Boolean = true,
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .padding(horizontal = 14.dp, vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        // Coloured rounded tile behind a white icon, like system settings apps.
        Box(
            modifier = Modifier
                .size(36.dp)
                .clip(RoundedCornerShape(11.dp))
                .background(tint),
            contentAlignment = Alignment.Center,
        ) {
            Icon(icon, contentDescription = null, tint = Color.White, modifier = Modifier.size(21.dp))
        }
        Spacer(Modifier.width(14.dp))
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = title,
                style = MaterialTheme.typography.bodyLarge,
                fontWeight = FontWeight.Medium,
                color = titleColor,
            )
            if (subtitle != null) {
                Spacer(Modifier.height(2.dp))
                Text(
                    text = subtitle,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.75f),
                )
            }
        }
        if (showChevron) {
            Icon(
                Icons.Filled.ChevronRight,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.5f),
            )
        }
    }
}

/** One-line live summary for a category card; categories without live data show their static description. */
@Composable
private fun categorySummary(category: SettingsCategory, state: SettingsUiState): String = when (category) {
    SettingsCategory.SECURITY -> buildList {
        add(stringResource(if (state.appLockEnabled) R.string.sx_summary_applock_on else R.string.sx_summary_applock_off))
        if (state.biometricEnabled) add(stringResource(R.string.sx_summary_biometric_on))
        if (!state.deviceScreenLockSet) add(stringResource(R.string.sx_summary_screen_lock_missing))
    }.joinToString(" · ")
    else -> stringResource(categoryDescRes(category))
}

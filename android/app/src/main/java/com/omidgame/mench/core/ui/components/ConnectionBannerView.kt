package com.omidgame.mench.core.ui.components

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkVertically
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.omidgame.mench.R
import com.omidgame.mench.core.ui.theme.MenchDuration
import com.omidgame.mench.core.ui.theme.MenchSpacing
import com.omidgame.mench.feature.chat.presentation.ConnectionBanner
import com.omidgame.mench.feature.chat.presentation.ConnectionStatusViewModel
import androidx.compose.animation.core.tween

/** Slim banner ("Connecting…" / "Waiting for network") that collapses to nothing when online. */
@Composable
fun ConnectionBannerHost(modifier: Modifier = Modifier) {
    val viewModel: ConnectionStatusViewModel = hiltViewModel()
    val banner by viewModel.banner.collectAsStateWithLifecycle()
    ConnectionBannerView(banner = banner, modifier = modifier)
}

@Composable
fun ConnectionBannerView(banner: ConnectionBanner, modifier: Modifier = Modifier) {
    AnimatedVisibility(
        visible = banner != ConnectionBanner.HIDDEN,
        enter = expandVertically(tween(MenchDuration.STANDARD)) + fadeIn(tween(MenchDuration.FAST)),
        exit = shrinkVertically(tween(MenchDuration.STANDARD)) + fadeOut(tween(MenchDuration.FAST)),
        modifier = modifier,
    ) {
        val label = when (banner) {
            ConnectionBanner.WAITING_FOR_NETWORK -> stringResource(R.string.connection_waiting_network)
            else -> stringResource(R.string.connection_connecting)
        }
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .background(MaterialTheme.colorScheme.secondaryContainer)
                .padding(horizontal = MenchSpacing.lg, vertical = MenchSpacing.sm)
                .semantics { liveRegion = LiveRegionMode.Polite },
            horizontalArrangement = Arrangement.Center,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            CircularProgressIndicator(
                modifier = Modifier.size(14.dp),
                strokeWidth = 2.dp,
                color = MaterialTheme.colorScheme.onSecondaryContainer,
            )
            Text(
                text = label,
                style = MaterialTheme.typography.labelLarge,
                color = MaterialTheme.colorScheme.onSecondaryContainer,
                modifier = Modifier.padding(start = MenchSpacing.sm),
            )
        }
    }
}

package com.omidgame.mench.feature.auth.presentation

import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Favorite
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.omidgame.mench.R
import com.omidgame.mench.core.ui.components.pressScale
import com.omidgame.mench.feature.auth.domain.AuthFailureReason

/** Brand gradient used by every auth screen (matches AnimatedSplash so the hand-off is seamless). */
private val AuthGradient = Brush.verticalGradient(listOf(Color(0xFFE23676), Color(0xFFA81D56), Color(0xFF3F0D2B)))

/**
 * Full-screen gradient with two slowly drifting glow blobs, a pulsing heart logo
 * and the app name, with [content] shown in a frosted card below.
 */
@Composable
fun AuthBackdrop(content: @Composable () -> Unit) {
    val infinite = rememberInfiniteTransition(label = "authBackdrop")
    val drift by infinite.animateFloat(
        initialValue = 0f,
        targetValue = 1f,
        animationSpec = infiniteRepeatable(tween(7000), RepeatMode.Reverse),
        label = "drift",
    )
    val pulse by infinite.animateFloat(
        initialValue = 1f,
        targetValue = 1.12f,
        animationSpec = infiniteRepeatable(tween(900), RepeatMode.Reverse),
        label = "pulse",
    )
    Box(modifier = Modifier.fillMaxSize().background(AuthGradient)) {
        Box(
            modifier = Modifier
                .align(Alignment.TopStart)
                .size(260.dp)
                .graphicsLayer { translationX = -60f + drift * 90f; translationY = -40f + drift * 60f }
                .background(Brush.radialGradient(listOf(Color.White.copy(alpha = 0.22f), Color.Transparent)), CircleShape),
        )
        Box(
            modifier = Modifier
                .align(Alignment.BottomEnd)
                .size(320.dp)
                .graphicsLayer { translationX = 70f - drift * 100f; translationY = 50f - drift * 70f }
                .background(Brush.radialGradient(listOf(Color(0xFFFF8FB8).copy(alpha = 0.28f), Color.Transparent)), CircleShape),
        )
        Column(
            modifier = Modifier
                .fillMaxSize()
                .statusBarsPadding()
                .imePadding()
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 24.dp, vertical = 24.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center,
        ) {
            Box(
                modifier = Modifier
                    .size(88.dp)
                    .background(Color.White.copy(alpha = 0.16f), CircleShape)
                    .border(1.dp, Color.White.copy(alpha = 0.35f), CircleShape),
                contentAlignment = Alignment.Center,
            ) {
                Icon(
                    imageVector = Icons.Filled.Favorite,
                    contentDescription = null,
                    tint = Color.White,
                    modifier = Modifier.size(44.dp).graphicsLayer { scaleX = pulse; scaleY = pulse },
                )
            }
            Spacer(Modifier.height(14.dp))
            Text(
                text = stringResource(R.string.app_name),
                color = Color.White,
                style = MaterialTheme.typography.headlineMedium,
                fontWeight = FontWeight.Bold,
            )
            Spacer(Modifier.height(28.dp))
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .background(Color.White.copy(alpha = 0.14f), RoundedCornerShape(28.dp))
                    .border(1.dp, Color.White.copy(alpha = 0.30f), RoundedCornerShape(28.dp))
                    .padding(22.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
            ) {
                content()
            }
        }
    }
}

/** Big white pill button with brand-coloured label; shows a spinner while [loading]. */
@Composable
fun AuthPrimaryButton(text: String, enabled: Boolean, loading: Boolean, onClick: () -> Unit) {
    val interaction = remember { MutableInteractionSource() }
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .height(54.dp)
            .pressScale(interaction)
            .clip(RoundedCornerShape(27.dp))
            .background(Color.White.copy(alpha = if (enabled) 1f else 0.4f))
            .clickable(interactionSource = interaction, indication = null, enabled = enabled && !loading, onClick = onClick),
        contentAlignment = Alignment.Center,
    ) {
        if (loading) {
            CircularProgressIndicator(modifier = Modifier.size(24.dp), strokeWidth = 2.5.dp, color = Color(0xFFA81D56))
        } else {
            Text(text = text, color = Color(0xFFA81D56), fontWeight = FontWeight.Bold, style = MaterialTheme.typography.titleMedium, textAlign = TextAlign.Center)
        }
    }
}

/** Localized (Persian/English) message for a login failure. */
@Composable
fun authErrorText(reason: AuthFailureReason): String = stringResource(
    when (reason) {
        AuthFailureReason.NETWORK_UNAVAILABLE -> R.string.auth_error_network
        AuthFailureReason.INVALID_OR_EXPIRED_CODE -> R.string.auth_error_invalid_code
        AuthFailureReason.RATE_LIMITED -> R.string.auth_error_rate_limited
        AuthFailureReason.WRONG_PASSWORD -> R.string.auth_error_wrong_password
        AuthFailureReason.INVALID_PHONE -> R.string.auth_error_invalid_phone
        AuthFailureReason.UNKNOWN -> R.string.auth_error_generic
    },
)

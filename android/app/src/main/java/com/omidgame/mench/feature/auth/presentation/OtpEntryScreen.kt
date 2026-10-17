package com.omidgame.mench.feature.auth.presentation

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import com.omidgame.mench.R
import com.omidgame.mench.feature.auth.domain.normalizeOtpCode

@Composable
fun OtpEntryScreen(
    state: AuthUiState.OtpEntry,
    onSubmitCode: (String) -> Unit,
    onResend: () -> Unit,
) {
    var code by remember(state.challenge.challengeId) { mutableStateOf("") }
    val focus = remember { FocusRequester() }
    LaunchedEffect(state.challenge.challengeId) { focus.requestFocus() }
    // Server OTP_LENGTH defaults to 5 (4..8 allowed): show at least 5 boxes, grow if more digits are typed.
    val boxCount = maxOf(5, code.length)

    AuthBackdrop {
        Text(
            text = stringResource(R.string.auth_otp_title),
            style = MaterialTheme.typography.titleMedium,
            color = Color.White,
        )
        Spacer(modifier = Modifier.height(18.dp))
        BasicTextField(
            value = code,
            onValueChange = { new -> code = normalizeOtpCode(new).take(8) },
            singleLine = true,
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.NumberPassword),
            cursorBrush = SolidColor(Color.Transparent),
            modifier = Modifier.focusRequester(focus),
            decorationBox = {
                CompositionLocalProvider(LocalLayoutDirection provides LayoutDirection.Ltr) {
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.fillMaxWidth()) {
                    repeat(boxCount) { i ->
                        val filled = i < code.length
                        val active = i == code.length
                        Box(
                            modifier = Modifier
                                .weight(1f)
                                .height(56.dp)
                                .background(Color.White.copy(alpha = if (filled) 0.28f else 0.12f), RoundedCornerShape(14.dp))
                                .border(
                                    width = if (active) 2.dp else 1.dp,
                                    color = if (state.errorReason != null) Color(0xFFFFB4AB) else Color.White.copy(alpha = if (active) 1f else 0.4f),
                                    shape = RoundedCornerShape(14.dp),
                                ),
                            contentAlignment = Alignment.Center,
                        ) {
                            Text(
                                text = if (filled) code[i].toString() else "",
                                color = Color.White,
                                style = MaterialTheme.typography.titleLarge,
                                fontWeight = FontWeight.Bold,
                            )
                        }
                    }
                }
                }
            },
        )
        state.errorReason?.let { reason ->
            Spacer(modifier = Modifier.height(10.dp))
            Text(text = authErrorText(reason), color = Color(0xFFFFB4AB), style = MaterialTheme.typography.bodyMedium)
        }
        Spacer(modifier = Modifier.height(18.dp))
        AuthPrimaryButton(
            text = stringResource(R.string.auth_otp_verify),
            enabled = code.length >= 4,
            loading = state.isVerifying,
            onClick = { onSubmitCode(code) },
        )
        if (!state.isVerifying) {
            TextButton(onClick = onResend, enabled = state.resendCooldownSeconds == 0) {
                Text(
                    text = stringResource(R.string.auth_otp_resend),
                    color = Color.White.copy(alpha = if (state.resendCooldownSeconds == 0) 1f else 0.5f),
                )
            }
        }
    }
}

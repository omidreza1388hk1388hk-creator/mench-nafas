package com.omidgame.mench.feature.auth.presentation

import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.LocalTextStyle
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextDirection
import androidx.compose.ui.unit.dp
import com.omidgame.mench.R
import com.omidgame.mench.feature.auth.domain.AuthFailureReason
import com.omidgame.mench.feature.auth.domain.toAsciiDigits

@Composable
fun PhoneEntryScreen(
    isLoading: Boolean,
    errorReason: AuthFailureReason? = null,
    onSubmit: (String) -> Unit,
) {
    // rememberSaveable + a single call site in the nav graph: the typed number survives an error and a rotation.
    var phone by rememberSaveable { mutableStateOf("") }
    val digitCount = phone.toAsciiDigits().count { it in '0'..'9' }
    val canSubmit = digitCount >= 8 && !isLoading

    AuthBackdrop {
        Text(
            text = stringResource(R.string.auth_phone_title),
            style = MaterialTheme.typography.titleMedium,
            color = Color.White,
        )
        Spacer(modifier = Modifier.height(18.dp))
        OutlinedTextField(
            value = phone,
            onValueChange = { phone = it.take(20) },
            label = { Text(stringResource(R.string.auth_phone_hint)) },
            singleLine = true,
            enabled = !isLoading,
            isError = errorReason != null,
            shape = RoundedCornerShape(18.dp),
            // Numbers are always left-to-right, even inside the Persian (RTL) layout.
            textStyle = LocalTextStyle.current.copy(textDirection = TextDirection.Ltr),
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Phone, imeAction = ImeAction.Done),
            keyboardActions = KeyboardActions(onDone = { if (canSubmit) onSubmit(phone) }),
            colors = OutlinedTextFieldDefaults.colors(
                focusedTextColor = Color.White,
                unfocusedTextColor = Color.White,
                disabledTextColor = Color.White.copy(alpha = 0.6f),
                errorTextColor = Color.White,
                focusedBorderColor = Color.White,
                unfocusedBorderColor = Color.White.copy(alpha = 0.5f),
                errorBorderColor = Color(0xFFFFB4AB),
                focusedLabelColor = Color.White,
                unfocusedLabelColor = Color.White.copy(alpha = 0.75f),
                errorLabelColor = Color(0xFFFFB4AB),
                cursorColor = Color.White,
                errorCursorColor = Color.White,
            ),
            modifier = Modifier.fillMaxWidth(),
        )
        Spacer(modifier = Modifier.height(8.dp))
        if (errorReason != null) {
            Text(
                text = authErrorText(errorReason),
                color = Color(0xFFFFB4AB),
                style = MaterialTheme.typography.bodyMedium,
            )
        } else {
            Text(
                text = stringResource(R.string.auth_phone_helper),
                color = Color.White.copy(alpha = 0.75f),
                style = MaterialTheme.typography.bodySmall,
            )
        }
        Spacer(modifier = Modifier.height(18.dp))
        AuthPrimaryButton(
            text = stringResource(R.string.auth_phone_continue),
            enabled = digitCount >= 8,
            loading = isLoading,
            onClick = { onSubmit(phone) },
        )
    }
}

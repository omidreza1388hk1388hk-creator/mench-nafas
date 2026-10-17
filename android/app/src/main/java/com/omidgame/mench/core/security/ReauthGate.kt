package com.omidgame.mench.core.security

import android.app.Activity
import android.app.KeyguardManager
import android.content.Context
import android.content.ContextWrapper
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.platform.LocalContext
import androidx.fragment.app.FragmentActivity
import kotlinx.coroutines.launch

private tailrec fun Context.fragmentActivityOrNull(): FragmentActivity? = when (this) {
    is FragmentActivity -> this
    is ContextWrapper -> baseContext.fragmentActivityOrNull()
    else -> null
}

/**
 * Re-authentication before a dangerous action (delete account, ...). Returns a function: call it with what should
 * happen after the user proves it is them. Order: biometrics, then the phone's own PIN/pattern/password.
 *
 * If the phone has no screen lock at all there is nothing to verify, so the action is allowed (the other
 * confirmation steps still apply) rather than leaving the user stuck. [onFailed] runs when the user cancels or fails.
 */
@Composable
fun rememberReauth(title: String, cancelText: String, onFailed: () -> Unit = {}): (onSuccess: () -> Unit) -> Unit {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val pending = remember { arrayOfNulls<() -> Unit>(1) }
    val credentialLauncher = rememberLauncherForActivityResult(ActivityResultContracts.StartActivityForResult()) { result ->
        val action = pending[0]
        pending[0] = null
        if (result.resultCode == Activity.RESULT_OK) action?.invoke() else onFailed()
    }
    return remember(title, cancelText) {
        { onSuccess: () -> Unit ->
            val activity = context.fragmentActivityOrNull()
            val keyguard = context.getSystemService(KeyguardManager::class.java)
            val deviceSecure = keyguard?.isDeviceSecure == true
            scope.launch {
                val biometric = activity?.let { BiometricAuthenticator(it).authenticate(title, cancelText) }
                when {
                    biometric is BiometricResult.Success -> onSuccess()
                    biometric is BiometricResult.Cancelled || biometric is BiometricResult.Error -> onFailed()
                    deviceSecure -> {
                        // No usable biometrics: fall back to the phone's own screen lock.
                        pending[0] = onSuccess
                        credentialLauncher.launch(keyguard!!.createConfirmDeviceCredentialIntent(title, null))
                    }
                    else -> onSuccess() // no screen lock on this phone: nothing to verify against
                }
            }
            Unit
        }
    }
}

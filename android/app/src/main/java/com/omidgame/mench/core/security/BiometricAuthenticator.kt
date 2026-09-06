package com.omidgame.mench.core.security

import androidx.biometric.BiometricManager
import androidx.biometric.BiometricPrompt
import androidx.core.content.ContextCompat
import androidx.fragment.app.FragmentActivity
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlin.coroutines.resume

sealed interface BiometricResult {
    data object Success : BiometricResult
    /** User cancelled, or tapped a fallback button — not a failure worth surfacing as an error message. */
    data object Cancelled : BiometricResult
    data class Error(val message: String) : BiometricResult
    /** No enrolled biometric / no hardware — the caller should simply hide the biometric option rather than show an error. */
    data object Unavailable : BiometricResult
}

/**
 * Thin wrapper around androidx.biometric.BiometricPrompt, which requires a
 * FragmentActivity host — see build.gradle.kts's comment on why
 * MainActivity extends FragmentActivity. Deliberately holds only an
 * Activity reference for the duration of a single authenticate() call
 * (created fresh via `remember` in the composable that uses it), never
 * stored in a ViewModel or Hilt singleton, which would leak the Activity.
 */
class BiometricAuthenticator(private val activity: FragmentActivity) {

    fun canAuthenticate(): Boolean {
        val manager = BiometricManager.from(activity)
        return manager.canAuthenticate(BiometricManager.Authenticators.BIOMETRIC_WEAK) ==
            BiometricManager.BIOMETRIC_SUCCESS
    }

    suspend fun authenticate(title: String, negativeButtonText: String): BiometricResult =
        suspendCancellableCoroutine { continuation ->
            if (!canAuthenticate()) {
                continuation.resume(BiometricResult.Unavailable)
                return@suspendCancellableCoroutine
            }

            val callback = object : BiometricPrompt.AuthenticationCallback() {
                override fun onAuthenticationSucceeded(result: BiometricPrompt.AuthenticationResult) {
                    if (continuation.isActive) continuation.resume(BiometricResult.Success)
                }

                override fun onAuthenticationError(errorCode: Int, errString: CharSequence) {
                    if (!continuation.isActive) return
                    val cancelled = errorCode == BiometricPrompt.ERROR_USER_CANCELED ||
                        errorCode == BiometricPrompt.ERROR_NEGATIVE_BUTTON ||
                        errorCode == BiometricPrompt.ERROR_CANCELED
                    continuation.resume(
                        if (cancelled) BiometricResult.Cancelled else BiometricResult.Error(errString.toString()),
                    )
                }

                // Deliberately no onAuthenticationFailed() handling here: a
                // single failed fingerprint/face read is not terminal, the
                // prompt keeps listening and lets the user retry on its
                // own — resolving the coroutine here would dismiss the
                // prompt after the very first mismatch.
            }

            val prompt = BiometricPrompt(activity, ContextCompat.getMainExecutor(activity), callback)
            val info = BiometricPrompt.PromptInfo.Builder()
                .setTitle(title)
                .setNegativeButtonText(negativeButtonText)
                .setAllowedAuthenticators(BiometricManager.Authenticators.BIOMETRIC_WEAK)
                .build()

            prompt.authenticate(info)

            continuation.invokeOnCancellation { prompt.cancelAuthentication() }
        }
}

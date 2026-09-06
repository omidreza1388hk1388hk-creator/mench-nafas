package com.omidgame.mench.feature.security.presentation

import com.omidgame.mench.feature.security.domain.AppLockTimeoutOption

data class PrivacySecurityUiState(
    val isLoading: Boolean = true,
    val appLockEnabled: Boolean = false,
    val biometricEnabled: Boolean = false,
    val biometricAvailableOnDevice: Boolean = false,
    val autoLockTimeout: AppLockTimeoutOption = AppLockTimeoutOption.AFTER_30_SECONDS,
    val isLoggingOutAll: Boolean = false,
    val loggedOutAll: Boolean = false,
    val infoMessage: String? = null,
)

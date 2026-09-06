package com.omidgame.mench.feature.security.presentation

data class LockUiState(
    val errorMessage: String? = null,
    val biometricAvailable: Boolean = false,
    val isVerifying: Boolean = false,
)

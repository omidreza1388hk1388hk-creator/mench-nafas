package com.omidgame.mench.feature.diagnostics.presentation

import com.omidgame.mench.feature.diagnostics.domain.DiagnosticsReport

sealed interface DiagnosticsUiState {
    data object Loading : DiagnosticsUiState
    data class Content(val report: DiagnosticsReport) : DiagnosticsUiState
}

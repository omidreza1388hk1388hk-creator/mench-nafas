package com.omidgame.mench.feature.diagnostics.presentation

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.omidgame.mench.feature.diagnostics.domain.DiagnosticsRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch
import javax.inject.Inject

@HiltViewModel
class DiagnosticsViewModel @Inject constructor(
    private val diagnosticsRepository: DiagnosticsRepository,
) : ViewModel() {

    private val _uiState = MutableStateFlow<DiagnosticsUiState>(DiagnosticsUiState.Loading)
    val uiState: StateFlow<DiagnosticsUiState> = _uiState

    init {
        runDiagnostics()
    }

    fun runDiagnostics() {
        viewModelScope.launch {
            _uiState.value = DiagnosticsUiState.Loading
            _uiState.value = DiagnosticsUiState.Content(diagnosticsRepository.runDiagnostics())
        }
    }
}

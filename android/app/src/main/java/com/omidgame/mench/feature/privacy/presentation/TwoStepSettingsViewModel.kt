package com.omidgame.mench.feature.privacy.presentation

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.omidgame.mench.core.network.ChatApi
import com.omidgame.mench.core.network.DisableTwoStepBody
import com.omidgame.mench.core.network.SetTwoStepBody
import com.omidgame.mench.feature.privacy.domain.TwoStepValidation
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import retrofit2.HttpException
import javax.inject.Inject

enum class TwoStepError { NETWORK, WRONG_PASSWORD, LOCKED, REJECTED, TOO_SHORT, TOO_LONG, REPEATED, MISMATCH, HINT }

data class TwoStepSettingsUiState(
    val loading: Boolean = true,
    val enabled: Boolean = false,
    val hint: String? = null,
    val busy: Boolean = false,
    val error: TwoStepError? = null,
    /** Bumped after a successful change so the screen can clear its text fields and close the form. */
    val completedCounter: Int = 0,
)

@HiltViewModel
class TwoStepSettingsViewModel @Inject constructor(
    private val chatApi: ChatApi,
) : ViewModel() {

    private val _uiState = MutableStateFlow(TwoStepSettingsUiState())
    val uiState: StateFlow<TwoStepSettingsUiState> = _uiState.asStateFlow()

    init {
        refresh()
    }

    fun refresh() {
        viewModelScope.launch {
            runCatching { chatApi.getTwoStep() }
                .onSuccess { _uiState.value = _uiState.value.copy(loading = false, enabled = it.enabled, hint = it.hint, error = null) }
                .onFailure { _uiState.value = _uiState.value.copy(loading = false, error = TwoStepError.NETWORK) }
        }
    }

    /** [currentPassword] is required only when changing an already-enabled password. */
    fun save(password: String, confirm: String, hint: String, currentPassword: String?) {
        val problem = TwoStepValidation.check(password, confirm, hint)
        if (problem != null) {
            _uiState.value = _uiState.value.copy(error = problem.toError())
            return
        }
        runRequest {
            val status = chatApi.setTwoStep(SetTwoStepBody(password, hint.trim().ifBlank { null }, currentPassword?.ifBlank { null }))
            _uiState.value = _uiState.value.copy(enabled = status.enabled, hint = status.hint)
        }
    }

    fun disable(password: String) {
        runRequest {
            val status = chatApi.disableTwoStep(DisableTwoStepBody(password))
            _uiState.value = _uiState.value.copy(enabled = status.enabled, hint = status.hint)
        }
    }

    private fun runRequest(block: suspend () -> Unit) {
        _uiState.value = _uiState.value.copy(busy = true, error = null)
        viewModelScope.launch {
            try {
                block()
                _uiState.value = _uiState.value.copy(busy = false, completedCounter = _uiState.value.completedCounter + 1)
            } catch (e: HttpException) {
                val error = when (e.code()) {
                    401 -> TwoStepError.WRONG_PASSWORD
                    429 -> TwoStepError.LOCKED
                    else -> TwoStepError.REJECTED
                }
                _uiState.value = _uiState.value.copy(busy = false, error = error)
            } catch (e: Exception) {
                _uiState.value = _uiState.value.copy(busy = false, error = TwoStepError.NETWORK)
            }
        }
    }

    private fun TwoStepValidation.Problem.toError() = when (this) {
        TwoStepValidation.Problem.TOO_SHORT -> TwoStepError.TOO_SHORT
        TwoStepValidation.Problem.TOO_LONG -> TwoStepError.TOO_LONG
        TwoStepValidation.Problem.REPEATED -> TwoStepError.REPEATED
        TwoStepValidation.Problem.MISMATCH -> TwoStepError.MISMATCH
        TwoStepValidation.Problem.HINT_CONTAINS_PASSWORD -> TwoStepError.HINT
    }
}

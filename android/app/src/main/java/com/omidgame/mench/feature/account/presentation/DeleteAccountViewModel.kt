package com.omidgame.mench.feature.account.presentation

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.omidgame.mench.core.notifications.RealtimeServiceLauncher
import com.omidgame.mench.feature.auth.domain.AuthRepository
import com.omidgame.mench.feature.chat.domain.ChatRepository
import com.omidgame.mench.feature.people.data.PeopleRepository
import com.omidgame.mench.feature.people.data.PeopleResult
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import java.io.File
import javax.inject.Inject

data class DeleteAccountUiState(
    val isExporting: Boolean = false,
    val isDeleting: Boolean = false,
    val acknowledged: Boolean = false,
    val errorOffline: Boolean = false,
    val errorGeneric: Boolean = false,
    val deleted: Boolean = false,
)

@HiltViewModel
class DeleteAccountViewModel @Inject constructor(
    private val people: PeopleRepository,
    private val authRepository: AuthRepository,
    private val chatRepository: ChatRepository,
    private val realtimeServiceLauncher: RealtimeServiceLauncher,
) : ViewModel() {

    private val _uiState = MutableStateFlow(DeleteAccountUiState())
    val uiState: StateFlow<DeleteAccountUiState> = _uiState.asStateFlow()

    private val _exportReady = Channel<File>(Channel.BUFFERED)
    val exportReady: Flow<File> = _exportReady.receiveAsFlow()

    fun onAcknowledgedChange(value: Boolean) = _uiState.update { it.copy(acknowledged = value) }

    fun onExport() {
        if (_uiState.value.isExporting) return
        _uiState.update { it.copy(isExporting = true, errorOffline = false, errorGeneric = false) }
        viewModelScope.launch {
            when (val r = people.exportMyData()) {
                is PeopleResult.Success -> {
                    _uiState.update { it.copy(isExporting = false) }
                    _exportReady.send(r.value)
                }
                PeopleResult.Offline -> _uiState.update { it.copy(isExporting = false, errorOffline = true) }
                else -> _uiState.update { it.copy(isExporting = false, errorGeneric = true) }
            }
        }
    }

    fun onConfirmDelete() {
        if (_uiState.value.isDeleting) return
        _uiState.update { it.copy(isDeleting = true, errorOffline = false, errorGeneric = false) }
        viewModelScope.launch {
            when (people.deleteAccount()) {
                is PeopleResult.Success -> {
                    // Same local teardown as a normal sign-out; the server side is already gone.
                    realtimeServiceLauncher.stop()
                    chatRepository.disconnectRealtime()
                    authRepository.logout()
                    _uiState.update { it.copy(isDeleting = false, deleted = true) }
                }
                PeopleResult.Offline -> _uiState.update { it.copy(isDeleting = false, errorOffline = true) }
                else -> _uiState.update { it.copy(isDeleting = false, errorGeneric = true) }
            }
        }
    }
}

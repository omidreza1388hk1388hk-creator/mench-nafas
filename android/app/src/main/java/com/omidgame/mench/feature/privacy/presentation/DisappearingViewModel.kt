package com.omidgame.mench.feature.privacy.presentation

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.omidgame.mench.core.network.ChatApi
import com.omidgame.mench.core.network.SetDisappearingBody
import com.omidgame.mench.core.network.realtime.RealtimeClient
import com.omidgame.mench.core.network.realtime.ServerToClientEvent
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import retrofit2.HttpException
import javax.inject.Inject

data class DisappearingUiState(
    /** null = off. */
    val seconds: Int? = null,
    val isSaving: Boolean = false,
    val notAllowed: Boolean = false,
    val failed: Boolean = false,
)

/**
 * Timer of one conversation. The chat screen binds it to the open conversation; the server also
 * pushes `conversation.disappearing_changed`, so the banner follows the other person's change live.
 */
@HiltViewModel
class DisappearingViewModel @Inject constructor(
    private val chatApi: ChatApi,
    private val realtimeClient: RealtimeClient,
) : ViewModel() {

    private val _uiState = MutableStateFlow(DisappearingUiState())
    val uiState: StateFlow<DisappearingUiState> = _uiState.asStateFlow()

    private var boundConversationId: String? = null

    fun bind(conversationId: String) {
        if (boundConversationId == conversationId) return
        boundConversationId = conversationId
        _uiState.value = DisappearingUiState()
        viewModelScope.launch {
            runCatching { chatApi.getDisappearing(conversationId) }
                .onSuccess { if (boundConversationId == conversationId) _uiState.value = _uiState.value.copy(seconds = it.seconds) }
        }
        viewModelScope.launch {
            realtimeClient.events.collect { event ->
                if (event is ServerToClientEvent.DisappearingChanged && event.conversationId == boundConversationId) {
                    _uiState.value = _uiState.value.copy(seconds = event.seconds, isSaving = false)
                }
            }
        }
    }

    fun clearError() {
        _uiState.value = _uiState.value.copy(notAllowed = false, failed = false)
    }

    /** [seconds] 0 = off. */
    fun set(seconds: Int) {
        val id = boundConversationId ?: return
        _uiState.value = _uiState.value.copy(isSaving = true, notAllowed = false, failed = false)
        viewModelScope.launch {
            try {
                val result = chatApi.setDisappearing(id, SetDisappearingBody(seconds))
                _uiState.value = _uiState.value.copy(seconds = result.seconds, isSaving = false)
            } catch (e: HttpException) {
                _uiState.value = _uiState.value.copy(isSaving = false, notAllowed = e.code() == 403, failed = e.code() != 403)
            } catch (e: Exception) {
                _uiState.value = _uiState.value.copy(isSaving = false, failed = true)
            }
        }
    }
}

package com.omidgame.mench.feature.people.presentation

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.omidgame.mench.feature.people.data.PeopleRepository
import com.omidgame.mench.feature.people.data.PeopleResult
import com.omidgame.mench.feature.people.data.PublicUser
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import javax.inject.Inject

data class BlockedUsersUiState(
    val isLoading: Boolean = true,
    val users: List<PublicUser> = emptyList(),
    val errorOffline: Boolean = false,
    val errorGeneric: Boolean = false,
)

@HiltViewModel
class BlockedUsersViewModel @Inject constructor(
    private val people: PeopleRepository,
) : ViewModel() {

    private val _uiState = MutableStateFlow(BlockedUsersUiState())
    val uiState: StateFlow<BlockedUsersUiState> = _uiState.asStateFlow()

    init {
        load()
    }

    fun load() {
        _uiState.update { it.copy(isLoading = true, errorOffline = false, errorGeneric = false) }
        viewModelScope.launch {
            when (val r = people.blocked()) {
                is PeopleResult.Success -> _uiState.update { it.copy(isLoading = false, users = r.value) }
                PeopleResult.Offline -> _uiState.update { it.copy(isLoading = false, errorOffline = true) }
                else -> _uiState.update { it.copy(isLoading = false, errorGeneric = true) }
            }
        }
    }

    fun onUnblock(userId: String) {
        viewModelScope.launch {
            when (people.unblock(userId)) {
                is PeopleResult.Success -> _uiState.update { s -> s.copy(users = s.users.filterNot { it.id == userId }) }
                PeopleResult.Offline -> _uiState.update { it.copy(errorOffline = true) }
                else -> _uiState.update { it.copy(errorGeneric = true) }
            }
        }
    }
}

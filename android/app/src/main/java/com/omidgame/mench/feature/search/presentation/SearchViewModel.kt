package com.omidgame.mench.feature.search.presentation

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.omidgame.mench.feature.search.domain.SearchOutcome
import com.omidgame.mench.feature.search.domain.SearchRepository
import com.omidgame.mench.feature.search.domain.SearchScope
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.FlowPreview
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.debounce
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.stateIn
import javax.inject.Inject

/**
 * Debounces the typed query (300ms — long enough that fast typing doesn't
 * fire a request per keystroke, short enough to still feel instant) and
 * re-runs the search whenever the query or scope filter changes.
 * flatMapLatest (not just map+launch) is what cancels an in-flight search
 * the moment a newer keystroke/scope-change supersedes it, so a slow
 * response for an old query can never race ahead of a faster response for
 * a newer one and flash stale results.
 */
@OptIn(FlowPreview::class, ExperimentalCoroutinesApi::class)
@HiltViewModel
class SearchViewModel @Inject constructor(
    private val searchRepository: SearchRepository,
) : ViewModel() {

    private val _query = MutableStateFlow("")
    val query: StateFlow<String> = _query

    private val _scope = MutableStateFlow(SearchScope.ALL)
    val scope: StateFlow<SearchScope> = _scope

    val uiState: StateFlow<SearchUiState> = combine(
        _query.debounce(300),
        _scope,
    ) { q, scope -> q to scope }
        .distinctUntilChanged()
        .flatMapLatest { (q, scope) ->
            if (q.isBlank() && scope != SearchScope.FILES) {
                flow { emit(SearchUiState.Idle) }
            } else {
                flow {
                    emit(SearchUiState.Loading)
                    when (val outcome = searchRepository.search(q, scope)) {
                        is SearchOutcome.Success -> emit(SearchUiState.Content(outcome.value, scope))
                        is SearchOutcome.Failure -> emit(SearchUiState.Error(outcome.reason.name))
                    }
                }
            }
        }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), SearchUiState.Idle)

    fun onQueryChange(newQuery: String) {
        _query.value = newQuery
    }

    fun onScopeChange(newScope: SearchScope) {
        _scope.value = newScope
    }
}

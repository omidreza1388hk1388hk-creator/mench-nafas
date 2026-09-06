package com.omidgame.mench.feature.search.presentation

import com.omidgame.mench.feature.search.domain.SearchResults
import com.omidgame.mench.feature.search.domain.SearchScope

sealed interface SearchUiState {
    /** Nothing typed yet — no request has been made, so this is distinct from Content with all-empty lists. */
    data object Idle : SearchUiState
    data object Loading : SearchUiState
    data class Content(val results: SearchResults, val scope: SearchScope) : SearchUiState
    data class Error(val message: String) : SearchUiState
}

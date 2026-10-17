package com.omidgame.mench.feature.chat.presentation

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.omidgame.mench.core.network.GifApi
import com.omidgame.mench.core.network.GifItemWire
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import retrofit2.HttpException
import javax.inject.Inject

enum class GifError { NOT_CONFIGURED, UNAVAILABLE }

data class GifUiState(
    /** What the user typed in the search box ("" while a topic chip drives the results). */
    val typed: String = "",
    /** The topic chip that is active, null when the user is typing or nothing is chosen. */
    val topic: GifTopic? = GifQuery.defaultTopic,
    val items: List<GifItemWire> = emptyList(),
    val loading: Boolean = false,
    val error: GifError? = null,
    val nextOffset: Int? = null,
)

/**
 * Phase 2A — drives the GIF picker. Opens on the romantic default topic, debounces typing, maps
 * Persian search terms to English ([GifQuery]) and pages with the server's offset cursor.
 * Stale responses (the user changed the query while a request was in flight) are dropped.
 */
@HiltViewModel
class GifViewModel @Inject constructor(
    private val gifApi: GifApi,
) : ViewModel() {

    private val _state = MutableStateFlow(GifUiState())
    val state: StateFlow<GifUiState> = _state

    private var searchJob: Job? = null
    private var loadMoreJob: Job? = null
    private var generation = 0

    init {
        reload(GifQuery.defaultTopic.query)
    }

    fun onTyped(text: String) {
        _state.update { it.copy(typed = text, topic = null) }
        searchJob?.cancel()
        val providerQuery = GifQuery.toProviderQuery(text)
        searchJob = viewModelScope.launch {
            delay(DEBOUNCE_MS)
            reload(providerQuery.ifEmpty { GifQuery.defaultTopic.query })
        }
    }

    fun onTopic(topic: GifTopic) {
        searchJob?.cancel()
        _state.update { it.copy(typed = "", topic = topic) }
        reload(topic.query)
    }

    fun retry() {
        val current = _state.value
        val query = current.topic?.query ?: GifQuery.toProviderQuery(current.typed).ifEmpty { GifQuery.defaultTopic.query }
        reload(query)
    }

    fun loadMore() {
        val current = _state.value
        val offset = current.nextOffset ?: return
        if (current.loading || loadMoreJob?.isActive == true) return
        val token = generation
        val query = currentQuery()
        loadMoreJob = viewModelScope.launch {
            try {
                val page = gifApi.search(query, offset)
                if (token != generation) return@launch
                _state.update {
                    it.copy(items = (it.items + page.items).distinctBy { gif -> gif.id }, nextOffset = page.nextOffset)
                }
            } catch (e: Exception) {
                // Keep what is already on screen; the next scroll to the end tries again.
            }
        }
    }

    private fun currentQuery(): String {
        val s = _state.value
        return s.topic?.query ?: GifQuery.toProviderQuery(s.typed).ifEmpty { GifQuery.defaultTopic.query }
    }

    private fun reload(query: String) {
        generation++
        val token = generation
        loadMoreJob?.cancel()
        _state.update { it.copy(loading = true, error = null, items = emptyList(), nextOffset = null) }
        viewModelScope.launch {
            try {
                val page = gifApi.search(query, 0)
                if (token != generation) return@launch
                _state.update { it.copy(loading = false, items = page.items, nextOffset = page.nextOffset) }
            } catch (e: Exception) {
                if (token != generation) return@launch
                val notConfigured = e is HttpException && e.code() == 503
                _state.update { it.copy(loading = false, error = if (notConfigured) GifError.NOT_CONFIGURED else GifError.UNAVAILABLE) }
            }
        }
    }

    private companion object {
        const val DEBOUNCE_MS = 450L
    }
}

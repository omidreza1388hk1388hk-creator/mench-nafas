package com.omidgame.mench.feature.search.presentation

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.ListItem
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.omidgame.mench.R
import com.omidgame.mench.feature.search.domain.ConversationSearchResult
import com.omidgame.mench.feature.search.domain.FileSearchResult
import com.omidgame.mench.feature.search.domain.MessageSearchResult
import com.omidgame.mench.feature.search.domain.SearchResults
import com.omidgame.mench.feature.search.domain.SearchScope

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SearchScreen(
    query: String,
    scope: SearchScope,
    state: SearchUiState,
    onQueryChange: (String) -> Unit,
    onScopeChange: (SearchScope) -> Unit,
    onConversationClick: (String) -> Unit,
    onBackClick: () -> Unit,
) {
    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    OutlinedTextField(
                        value = query,
                        onValueChange = onQueryChange,
                        placeholder = { Text(stringResource(R.string.search_hint)) },
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth(),
                    )
                },
                navigationIcon = {
                    IconButton(onClick = onBackClick) {
                        Icon(Icons.Filled.ArrowBack, contentDescription = null)
                    }
                },
            )
        },
    ) { padding ->
        Column(modifier = Modifier.fillMaxSize().padding(padding)) {
            ScopeFilterRow(scope = scope, onScopeChange = onScopeChange)
            Box(modifier = Modifier.fillMaxSize()) {
                when (state) {
                    is SearchUiState.Idle -> {
                        Text(
                            text = stringResource(R.string.search_idle_hint),
                            modifier = Modifier.align(Alignment.Center).padding(24.dp),
                            style = MaterialTheme.typography.bodyMedium,
                        )
                    }
                    is SearchUiState.Loading -> {
                        CircularProgressIndicator(modifier = Modifier.align(Alignment.Center))
                    }
                    is SearchUiState.Error -> {
                        Text(
                            text = stringResource(R.string.search_error),
                            modifier = Modifier.align(Alignment.Center).padding(24.dp),
                            color = MaterialTheme.colorScheme.error,
                        )
                    }
                    is SearchUiState.Content -> {
                        if (state.results.isEmpty) {
                            Text(
                                text = stringResource(R.string.search_no_results),
                                modifier = Modifier.align(Alignment.Center).padding(24.dp),
                            )
                        } else {
                            SearchResultsList(state.results, onConversationClick)
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun ScopeFilterRow(scope: SearchScope, onScopeChange: (SearchScope) -> Unit) {
    Row(
        modifier = Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 8.dp),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        ScopeChip(SearchScope.ALL, R.string.search_scope_all, scope, onScopeChange)
        ScopeChip(SearchScope.MESSAGES, R.string.search_scope_messages, scope, onScopeChange)
        ScopeChip(SearchScope.CONVERSATIONS, R.string.search_scope_conversations, scope, onScopeChange)
        ScopeChip(SearchScope.FILES, R.string.search_scope_files, scope, onScopeChange)
    }
}

@Composable
private fun ScopeChip(value: SearchScope, labelRes: Int, current: SearchScope, onScopeChange: (SearchScope) -> Unit) {
    FilterChip(
        selected = current == value,
        onClick = { onScopeChange(value) },
        label = { Text(stringResource(labelRes)) },
    )
}

@Composable
private fun SearchResultsList(results: SearchResults, onConversationClick: (String) -> Unit) {
    LazyColumn(modifier = Modifier.fillMaxWidth()) {
        if (results.isFromCache) {
            item {
                Text(
                    text = stringResource(R.string.search_offline_notice),
                    modifier = Modifier.fillMaxWidth().padding(12.dp),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.secondary,
                )
            }
        }
        if (results.conversations.isNotEmpty()) {
            item { SectionHeader(stringResource(R.string.search_section_chats)) }
            items(results.conversations, key = { "conv-${it.id}" }) { result ->
                ConversationResultRow(result, onClick = { onConversationClick(result.id) })
            }
        }
        if (results.messages.isNotEmpty()) {
            item { SectionHeader(stringResource(R.string.search_section_messages)) }
            items(results.messages, key = { "msg-${it.id}" }) { result ->
                MessageResultRow(result, onClick = { onConversationClick(result.conversationId) })
            }
        }
        if (results.files.isNotEmpty()) {
            item { SectionHeader(stringResource(R.string.search_section_files)) }
            items(results.files, key = { "file-${it.id}" }) { result ->
                FileResultRow(result, onClick = { onConversationClick(result.conversationId) })
            }
        }
    }
}

@Composable
private fun SectionHeader(title: String) {
    Text(
        text = title,
        modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 8.dp),
        style = MaterialTheme.typography.labelLarge,
        color = MaterialTheme.colorScheme.primary,
    )
}

@Composable
private fun ConversationResultRow(result: ConversationSearchResult, onClick: () -> Unit) {
    ListItem(
        leadingContent = { Icon(Icons.Filled.Search, contentDescription = null) },
        headlineContent = { Text(result.displayName) },
        modifier = Modifier.fillMaxWidth().clickable(onClick = onClick),
    )
}

@Composable
private fun MessageResultRow(result: MessageSearchResult, onClick: () -> Unit) {
    ListItem(
        headlineContent = { Text(result.body ?: "—", maxLines = 2) },
        supportingContent = { Text(result.conversationDisplayName) },
        modifier = Modifier.fillMaxWidth().clickable(onClick = onClick),
    )
}

@Composable
private fun FileResultRow(result: FileSearchResult, onClick: () -> Unit) {
    ListItem(
        headlineContent = { Text(result.originalFilename) },
        supportingContent = { Text(result.kind) },
        modifier = Modifier.fillMaxWidth().clickable(onClick = onClick),
    )
}

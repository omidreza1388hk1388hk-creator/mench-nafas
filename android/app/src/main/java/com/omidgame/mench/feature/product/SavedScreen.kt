package com.omidgame.mench.feature.product

import android.text.format.Formatter
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.ViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewModelScope
import com.omidgame.mench.R
import com.omidgame.mench.core.network.CloudUsageResponse
import com.omidgame.mench.core.network.FileEntry
import com.omidgame.mench.core.network.SavedItem
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import javax.inject.Inject

data class SavedUiState(
    val tab: Int = 0, val query: String = "", val kind: String? = null, val sort: String = "date",
    val saved: List<SavedItem> = emptyList(), val files: List<FileEntry> = emptyList(),
    val usage: CloudUsageResponse = CloudUsageResponse(), val error: Boolean = false,
    val folder: String? = null, val folders: List<com.omidgame.mench.core.network.FolderCount> = emptyList(),
    val editing: SavedItem? = null,
)

@HiltViewModel
class SavedViewModel @Inject constructor(private val repo: EcosystemRepository) : ViewModel() {
    private val _state = MutableStateFlow(SavedUiState())
    val state: StateFlow<SavedUiState> = _state

    init { reload() }

    fun setTab(t: Int) { _state.update { it.copy(tab = t) }; reload() }
    fun setQuery(q: String) { _state.update { it.copy(query = q) } }
    fun setKind(k: String?) { _state.update { it.copy(kind = k) }; reload() }
    fun setSort(s: String) { _state.update { it.copy(sort = s) }; reload() }
    fun setFolder(f: String?) { _state.update { it.copy(folder = f) }; reload() }
    fun edit(item: SavedItem?) { _state.update { it.copy(editing = item) } }
    fun savePatch(id: String, folder: String, tags: String) {
        val tagList = tags.split(',', '،').map { it.trim() }.filter { it.isNotEmpty() }.take(10)
        viewModelScope.launch { repo.patchSaved(id, folder.trim().ifBlank { null }, tagList).onSuccess { _state.update { it.copy(editing = null) }; reload() } }
    }

    fun reload() {
        val s = _state.value
        viewModelScope.launch {
            repo.cloudUsage().onSuccess { u -> _state.update { it.copy(usage = u) } }
            if (s.tab == 0) {
                repo.savedFolders().onSuccess { f -> _state.update { it.copy(folders = f.folders) } }
                repo.saved(s.query, s.kind, s.folder).onSuccess { l -> _state.update { it.copy(saved = l, error = false) } }.onFailure { _state.update { x -> x.copy(error = true) } }
            } else {
                val dir = if (s.sort == "name") "asc" else "desc"
                repo.files(s.query, s.kind, s.sort, dir).onSuccess { l -> _state.update { it.copy(files = l, error = false) } }.onFailure { _state.update { x -> x.copy(error = true) } }
            }
        }
    }

    fun addNote(text: String) { viewModelScope.launch { repo.saveNote(text).onSuccess { reload() } } }
    fun delete(id: String) { viewModelScope.launch { repo.deleteSaved(id).onSuccess { reload() } } }
}

private val KINDS = listOf(null to R.string.saved_kind_all, "text" to R.string.saved_kind_text, "image" to R.string.saved_kind_image, "file" to R.string.saved_kind_file, "link" to R.string.saved_kind_link, "note" to R.string.saved_kind_note)

@Composable
fun SavedScreen(onBack: () -> Unit, viewModel: SavedViewModel = hiltViewModel()) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    val context = LocalContext.current
    var note by remember { mutableStateOf("") }

    ProductScaffold(title = stringResource(R.string.saved_title), onBack = onBack) { padding ->
        Column(Modifier.fillMaxSize().padding(padding)) {
            Row(Modifier.padding(horizontal = 12.dp, vertical = 6.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                FilterChip(selected = state.tab == 0, onClick = { viewModel.setTab(0) }, label = { Text(stringResource(R.string.saved_tab_saved)) })
                FilterChip(selected = state.tab == 1, onClick = { viewModel.setTab(1) }, label = { Text(stringResource(R.string.saved_tab_files)) })
            }
            Text(
                stringResource(R.string.saved_usage, Formatter.formatShortFileSize(context, state.usage.saved.bytes), state.usage.saved.count),
                style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.padding(horizontal = 16.dp),
            )
            Row(Modifier.padding(horizontal = 12.dp, vertical = 6.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                OutlinedTextField(value = state.query, onValueChange = viewModel::setQuery, label = { Text(stringResource(R.string.find_people_search)) }, singleLine = true, modifier = Modifier.weight(1f))
                Button(onClick = viewModel::reload) { Text(stringResource(R.string.find_people_search)) }
            }
            Row(Modifier.padding(horizontal = 12.dp), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                KINDS.take(if (state.tab == 0) 6 else 3).forEach { (k, r) ->
                    FilterChip(selected = state.kind == k, onClick = { viewModel.setKind(k) }, label = { Text(stringResource(r)) })
                }
            }
            if (state.tab == 0 && state.folders.isNotEmpty()) {
                Row(Modifier.padding(horizontal = 12.dp, vertical = 4.dp), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    FilterChip(selected = state.folder == null, onClick = { viewModel.setFolder(null) }, label = { Text(stringResource(R.string.saved_kind_all)) })
                    state.folders.forEach { f -> FilterChip(selected = state.folder == f.folder, onClick = { viewModel.setFolder(f.folder) }, label = { Text("${f.folder} (${f.n})") }) }
                }
            }
            if (state.tab == 1) {
                Row(Modifier.padding(horizontal = 12.dp, vertical = 4.dp), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    listOf("date" to R.string.files_sort_date, "size" to R.string.files_sort_size, "name" to R.string.files_sort_name).forEach { (k, r) ->
                        FilterChip(selected = state.sort == k, onClick = { viewModel.setSort(k) }, label = { Text(stringResource(r)) })
                    }
                }
            }
            if (state.error) Text(stringResource(R.string.product_error_generic), color = MaterialTheme.colorScheme.error, modifier = Modifier.padding(16.dp))

            LazyColumn(Modifier.weight(1f), contentPadding = androidx.compose.foundation.layout.PaddingValues(12.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                if (state.tab == 0) {
                    items(state.saved, key = { it.id }) { item ->
                        Card(Modifier.fillMaxWidth()) {
                            Row(Modifier.padding(12.dp), verticalAlignment = Alignment.CenterVertically) {
                                Column(Modifier.weight(1f)) {
                                    item.text?.takeIf { it.isNotBlank() }?.let { Text(it, maxLines = 6) }
                                    item.attachment?.let { Text("${it.filename ?: ""} · ${Formatter.formatShortFileSize(context, it.sizeBytes)}", style = MaterialTheme.typography.bodySmall) }
                                    if (item.folder != null || item.tags.isNotEmpty()) Text(listOfNotNull(item.folder).plus(item.tags.map { "#$it" }).joinToString("  "), style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.primary)
                                    item.source?.senderName?.let { Text(stringResource(R.string.saved_from, it), style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant) }
                                }
                                TextButton(onClick = { viewModel.edit(item) }) { Text(stringResource(R.string.saved_edit)) }
                                IconButton(onClick = { viewModel.delete(item.id) }) { Icon(Icons.Filled.Delete, contentDescription = stringResource(R.string.message_delete_failed)) }
                            }
                        }
                    }
                } else {
                    items(state.files, key = { it.id }) { f ->
                        Card(Modifier.fillMaxWidth()) {
                            Column(Modifier.padding(12.dp)) {
                                Text(f.filename, style = MaterialTheme.typography.titleSmall)
                                Text("${Formatter.formatShortFileSize(context, f.sizeBytes)} · ${f.mime}" + (f.senderName?.takeIf { it.isNotBlank() }?.let { " · $it" } ?: ""), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                            }
                        }
                    }
                }
            }
            if (state.tab == 0) {
                Row(Modifier.fillMaxWidth().padding(12.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    OutlinedTextField(value = note, onValueChange = { note = it.take(8000) }, label = { Text(stringResource(R.string.saved_note_hint)) }, modifier = Modifier.weight(1f))
                    Button(onClick = { viewModel.addNote(note.trim()); note = "" }, enabled = note.isNotBlank()) { Text(stringResource(R.string.saved_add_note)) }
                }
            }
        }
    }
    state.editing?.let { item ->
        var folder by remember(item.id) { mutableStateOf(item.folder ?: "") }
        var tags by remember(item.id) { mutableStateOf(item.tags.joinToString(", ")) }
        com.omidgame.mench.core.ui.components.MenchDialog(
            onDismissRequest = { viewModel.edit(null) },
            title = { Text(stringResource(R.string.saved_edit)) },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    OutlinedTextField(value = folder, onValueChange = { folder = it.take(40) }, label = { Text(stringResource(R.string.saved_folder_hint)) }, singleLine = true)
                    OutlinedTextField(value = tags, onValueChange = { tags = it.take(200) }, label = { Text(stringResource(R.string.saved_tags_hint)) }, singleLine = true)
                }
            },
            confirmButton = { androidx.compose.material3.TextButton(onClick = { viewModel.savePatch(item.id, folder, tags) }) { Text(stringResource(R.string.feedback_send)) } },
            dismissButton = { androidx.compose.material3.TextButton(onClick = { viewModel.edit(null) }) { Text(stringResource(R.string.action_cancel)) } },
        )
    }
}

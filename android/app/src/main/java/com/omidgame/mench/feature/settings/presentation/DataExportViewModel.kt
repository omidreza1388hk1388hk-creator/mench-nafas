package com.omidgame.mench.feature.settings.presentation

import android.content.Context
import android.net.Uri
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.omidgame.mench.core.backup.PassphraseCrypto
import com.omidgame.mench.core.network.ProductApi
import dagger.hilt.android.lifecycle.HiltViewModel
import dagger.hilt.android.qualifiers.ApplicationContext
import java.io.ByteArrayOutputStream
import javax.inject.Inject
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

enum class ExportPhase { IDLE, WORKING, DONE, FAILED }

data class DataExportUiState(
    val phase: ExportPhase = ExportPhase.IDLE,
    /** 0..1 while the size is known, null when it is not (shown as an indeterminate bar). */
    val progress: Float? = null,
    val encrypted: Boolean = false,
)

@HiltViewModel
class DataExportViewModel @Inject constructor(
    @ApplicationContext private val context: Context,
    private val api: ProductApi,
) : ViewModel() {

    private val _state = MutableStateFlow(DataExportUiState())
    val state: StateFlow<DataExportUiState> = _state

    /**
     * Downloads the JSON export and writes it to [uri] (chosen by the user with the system file picker).
     * With a [passphrase] the file is encrypted first; without one it is plain JSON, which the screen warns about.
     */
    fun export(uri: Uri, passphrase: String?) {
        if (_state.value.phase == ExportPhase.WORKING) return
        _state.value = DataExportUiState(phase = ExportPhase.WORKING, encrypted = passphrase != null)
        viewModelScope.launch {
            val ok = withContext(Dispatchers.IO) {
                runCatching {
                    val bytes = api.exportData().use { body ->
                        val total = body.contentLength()
                        val buffer = ByteArray(16 * 1024)
                        val sink = ByteArrayOutputStream()
                        body.byteStream().use { input ->
                            while (true) {
                                val n = input.read(buffer)
                                if (n < 0) break
                                sink.write(buffer, 0, n)
                                if (total > 0) _state.update { it.copy(progress = (sink.size().toFloat() / total).coerceIn(0f, 1f)) }
                            }
                        }
                        sink.toByteArray()
                    }
                    val payload = if (passphrase != null) PassphraseCrypto.encrypt(passphrase, bytes) else bytes
                    // "wt" truncates: re-exporting over an existing file must not leave old trailing bytes.
                    context.contentResolver.openOutputStream(uri, "wt")!!.use { it.write(payload) }
                }.isSuccess
            }
            _state.update { it.copy(phase = if (ok) ExportPhase.DONE else ExportPhase.FAILED, progress = null) }
        }
    }

    fun reset() { _state.value = DataExportUiState() }
}

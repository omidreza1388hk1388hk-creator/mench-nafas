package com.omidgame.mench.core.media

import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.update
import okhttp3.MediaType
import okhttp3.RequestBody
import okio.Buffer
import okio.BufferedSink
import okio.ForwardingSink
import okio.Sink
import okio.buffer
import java.util.concurrent.ConcurrentHashMap

/**
 * Phase 1B: per-message upload progress (0f..1f keyed by clientMsgId). Process-wide on purpose:
 * the Outbox worker writes it, any chat screen can read it. A missing key means "not uploading".
 */
object UploadProgress {
    private val _progress = MutableStateFlow<Map<String, Float>>(emptyMap())
    val progress: StateFlow<Map<String, Float>> = _progress

    /** Message ids the user asked to cancel; the worker checks this before and during an upload. */
    private val cancelled = ConcurrentHashMap.newKeySet<String>()

    fun report(clientMsgId: String, fraction: Float) {
        _progress.update { it + (clientMsgId to fraction.coerceIn(0f, 1f)) }
    }

    fun clear(clientMsgId: String) {
        _progress.update { it - clientMsgId }
    }

    fun requestCancel(clientMsgId: String) {
        cancelled.add(clientMsgId)
    }

    fun isCancelled(clientMsgId: String): Boolean = clientMsgId in cancelled

    fun acknowledgeCancel(clientMsgId: String) {
        cancelled.remove(clientMsgId)
    }
}

/** Wraps a RequestBody and reports bytes written; throws when the user cancels mid-upload. */
class ProgressRequestBody(
    private val delegate: RequestBody,
    private val clientMsgId: String,
) : RequestBody() {
    override fun contentType(): MediaType? = delegate.contentType()
    override fun contentLength(): Long = delegate.contentLength()

    override fun writeTo(sink: BufferedSink) {
        val total = contentLength().coerceAtLeast(1L)
        val counting = object : ForwardingSink(sink as Sink) {
            private var written = 0L
            override fun write(source: Buffer, byteCount: Long) {
                if (UploadProgress.isCancelled(clientMsgId)) throw UploadCancelledException()
                super.write(source, byteCount)
                written += byteCount
                UploadProgress.report(clientMsgId, written.toFloat() / total.toFloat())
            }
        }.buffer()
        delegate.writeTo(counting)
        counting.flush()
    }
}

class UploadCancelledException : java.io.IOException("Upload cancelled by user")

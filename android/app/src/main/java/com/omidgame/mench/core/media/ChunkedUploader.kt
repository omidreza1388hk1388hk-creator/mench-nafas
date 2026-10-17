package com.omidgame.mench.core.media

import android.content.Context
import com.omidgame.mench.core.network.AttachmentResponse
import com.omidgame.mench.core.network.ChatApi
import com.omidgame.mench.core.network.InitChunkedUploadBody
import okhttp3.MediaType.Companion.toMediaTypeOrNull
import okhttp3.MultipartBody
import okhttp3.RequestBody.Companion.toRequestBody
import retrofit2.HttpException
import java.io.File
import java.io.RandomAccessFile

/** Thrown when the user cancels while chunks are going up; the worker maps it to its normal cancel handling. */
class ChunkedUploadCancelled : Exception()

/**
 * Phase 1B: resumable chunked upload for big files. The server upload id is remembered per message
 * (SharedPreferences), so after a dropped connection or a killed process the next Outbox attempt asks the
 * server which chunks it already has and sends only the missing ones. Progress goes to [UploadProgress].
 */
class ChunkedUploader(context: Context, private val api: ChatApi) {
    private val prefs = context.applicationContext.getSharedPreferences("chunked_uploads", Context.MODE_PRIVATE)

    companion object {
        /** Files at or above this size use chunking; smaller ones keep the single multipart request. */
        const val THRESHOLD_BYTES = 8L * 1024 * 1024
        private const val CHUNK_BYTES = 1024 * 1024
    }

    @Suppress("LongParameterList")
    suspend fun upload(
        clientMsgId: String,
        conversationId: String,
        file: File,
        mimeType: String,
        filename: String,
        durationMs: Long?,
        widthPx: Int?,
        heightPx: Int?,
        thumbnail: File?,
    ): AttachmentResponse {
        val total = file.length()
        val key = "u_${clientMsgId}_$total"
        var status = prefs.getString(key, null)?.let { id ->
            try {
                api.chunkedUploadStatus(conversationId, id)
            } catch (e: HttpException) {
                // 404 = the server dropped the staging folder (24 h) or this is another account: start over.
                if (e.code() == 404) null else throw e
            }
        }
        if (status == null) {
            status = api.initChunkedUpload(
                conversationId,
                InitChunkedUploadBody(filename, mimeType, total, CHUNK_BYTES, durationMs, widthPx, heightPx),
            )
            prefs.edit().putString(key, status.uploadId).apply()
        }
        val uploadId = status.uploadId
        val received = status.received.toMutableSet()
        val chunkSize = status.chunkSize
        val totalChunks = status.totalChunks
        UploadProgress.report(clientMsgId, received.size.toFloat() / totalChunks)

        RandomAccessFile(file, "r").use { raf ->
            for (index in 0 until totalChunks) {
                if (index in received) continue
                if (UploadProgress.isCancelled(clientMsgId)) throw ChunkedUploadCancelled()
                val offset = index.toLong() * chunkSize
                val length = minOf(chunkSize.toLong(), total - offset).toInt()
                val bytes = ByteArray(length)
                raf.seek(offset)
                raf.readFully(bytes)
                val part = MultipartBody.Part.createFormData(
                    "chunk", "$index.part",
                    bytes.toRequestBody("application/octet-stream".toMediaTypeOrNull()),
                )
                api.putUploadChunk(conversationId, uploadId, index, part)
                received += index
                UploadProgress.report(clientMsgId, received.size.toFloat() / totalChunks)
            }
        }
        if (thumbnail != null && thumbnail.exists()) {
            val thumbPart = MultipartBody.Part.createFormData(
                "thumbnail", thumbnail.name,
                thumbnail.readBytes().toRequestBody("image/jpeg".toMediaTypeOrNull()),
            )
            api.putUploadThumbnail(conversationId, uploadId, thumbPart)
        }
        val response = api.completeChunkedUpload(conversationId, uploadId)
        prefs.edit().remove(key).apply()
        return response
    }
}

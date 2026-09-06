package com.omidgame.mench.core.media

import android.content.Context
import android.media.MediaRecorder
import android.os.Build
import dagger.hilt.android.qualifiers.ApplicationContext
import java.io.File
import java.util.UUID
import javax.inject.Inject
import javax.inject.Singleton

sealed interface RecordingResult {
    data class Success(val filePath: String, val mimeType: String, val durationMs: Long) : RecordingResult
    data object Failed : RecordingResult
}

/**
 * Thin wrapper around MediaRecorder for voice messages. Records to
 * AAC-in-MP4 (audio/mp4, .m4a) — broadly compatible and already one of
 * the mime types AttachmentsService accepts server-side (see
 * ALLOWED_AUDIO_MIME_TYPES in attachments.service.ts). Recording state
 * (start/stop/cancel) is intentionally NOT exposed as a Flow — a single
 * in-flight recording only ever has one UI (the composer's mic button)
 * driving it directly, so a reactive stream would be unused ceremony.
 */
@Singleton
class VoiceRecorder @Inject constructor(
    @ApplicationContext private val context: Context,
) {
    private var recorder: MediaRecorder? = null
    private var outputFile: File? = null
    private var startedAtMillis: Long = 0L

    val isRecording: Boolean get() = recorder != null

    /** Returns false if recording could not start (e.g. mic unavailable) — caller should surface that rather than silently doing nothing. */
    fun start(): Boolean {
        if (recorder != null) return false

        val dir = File(context.filesDir, "voice_recordings").apply { mkdirs() }
        val file = File(dir, "${UUID.randomUUID()}.m4a")

        val mediaRecorder = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            MediaRecorder(context)
        } else {
            @Suppress("DEPRECATION")
            MediaRecorder()
        }

        return try {
            mediaRecorder.apply {
                setAudioSource(MediaRecorder.AudioSource.MIC)
                setOutputFormat(MediaRecorder.OutputFormat.MPEG_4)
                setAudioEncoder(MediaRecorder.AudioEncoder.AAC)
                setAudioEncodingBitRate(96_000)
                setAudioSamplingRate(44_100)
                setOutputFile(file.absolutePath)
                prepare()
                start()
            }
            recorder = mediaRecorder
            outputFile = file
            startedAtMillis = System.currentTimeMillis()
            true
        } catch (e: Exception) {
            mediaRecorder.release()
            file.delete()
            false
        }
    }

    /** Stops and finalizes the recording. Discards (returns Failed) anything under 500ms — too short to be a meaningful voice message, not worth uploading. */
    fun stop(): RecordingResult {
        val mediaRecorder = recorder ?: return RecordingResult.Failed
        val file = outputFile ?: return RecordingResult.Failed
        val durationMs = System.currentTimeMillis() - startedAtMillis
        recorder = null
        outputFile = null

        return try {
            mediaRecorder.stop()
            mediaRecorder.release()
            if (durationMs < 500) {
                file.delete()
                RecordingResult.Failed
            } else {
                RecordingResult.Success(file.absolutePath, "audio/mp4", durationMs)
            }
        } catch (e: Exception) {
            file.delete()
            RecordingResult.Failed
        }
    }

    /** Discards an in-progress recording (e.g. user cancels mid-recording) without sending anything. */
    fun cancel() {
        try {
            recorder?.stop()
        } catch (e: Exception) {
            // stop() throws if start() never produced any valid data — irrelevant here since we're discarding regardless.
        }
        recorder?.release()
        recorder = null
        outputFile?.delete()
        outputFile = null
    }
}

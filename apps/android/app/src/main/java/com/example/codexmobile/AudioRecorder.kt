package com.example.codexmobile

import android.content.Context
import android.media.MediaRecorder
import android.os.Build
import java.io.File

class AudioRecorder(private val context: Context) {
    private var recorder: MediaRecorder? = null
    private var outputFile: File? = null

    fun start(): File? {
        if (recorder != null) return outputFile
        val file = File.createTempFile("codex_audio_", ".m4a", context.cacheDir)
        val mediaRecorder = if (Build.VERSION.SDK_INT >= 31) {
            MediaRecorder(context)
        } else {
            MediaRecorder()
        }
        return try {
            mediaRecorder.apply {
                setAudioSource(MediaRecorder.AudioSource.MIC)
                setOutputFormat(MediaRecorder.OutputFormat.MPEG_4)
                setAudioEncoder(MediaRecorder.AudioEncoder.AAC)
                setAudioSamplingRate(16000)
                setAudioEncodingBitRate(64000)
                setOutputFile(file.absolutePath)
                prepare()
                start()
            }
            recorder = mediaRecorder
            outputFile = file
            file
        } catch (_: Exception) {
            mediaRecorder.reset()
            mediaRecorder.release()
            recorder = null
            outputFile = null
            null
        }
    }

    fun stop(): File? {
        val currentRecorder = recorder ?: return null
        val file = outputFile
        try {
            currentRecorder.stop()
        } catch (_: Exception) {
            // ignore
        } finally {
            currentRecorder.reset()
            currentRecorder.release()
            recorder = null
            outputFile = null
        }
        return file
    }
}

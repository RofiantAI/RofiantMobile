package ca.rofiant.app.data.local

import android.content.Context
import android.media.MediaRecorder
import android.os.Build
import java.io.File

/**
 * Records a single dictation clip to an m4a/AAC file in the cache dir —
 * matches rofiant-desktop's fallback mime bucket in transcribe_audio
 * (src-tauri/src/lib.rs) for anything that isn't webm/ogg/wav.
 */
class AudioRecorder(private val context: Context) {
    private var recorder: MediaRecorder? = null
    private var outputFile: File? = null

    fun start(): File {
        val file = File(context.cacheDir, "rofiant_voice_${System.currentTimeMillis()}.m4a")
        @Suppress("DEPRECATION")
        val newRecorder = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            MediaRecorder(context)
        } else {
            MediaRecorder()
        }
        newRecorder.apply {
            setAudioSource(MediaRecorder.AudioSource.MIC)
            setOutputFormat(MediaRecorder.OutputFormat.MPEG_4)
            setAudioEncoder(MediaRecorder.AudioEncoder.AAC)
            setOutputFile(file.absolutePath)
            prepare()
            start()
        }
        recorder = newRecorder
        outputFile = file
        return file
    }

    /** Peak amplitude (0..32767) since the last call — MediaRecorder resets its max on every read. */
    fun amplitude(): Int = try { recorder?.maxAmplitude ?: 0 } catch (e: IllegalStateException) { 0 }

    /** Returns the recorded file, or null if the recording was too short/invalid to keep. */
    fun stop(): File? {
        val file = outputFile
        outputFile = null
        return try {
            recorder?.apply {
                stop()
                release()
            }
            file
        } catch (e: RuntimeException) {
            recorder?.release()
            file?.delete()
            null
        } finally {
            recorder = null
        }
    }

    fun cancel() {
        try {
            recorder?.apply {
                stop()
                release()
            }
        } catch (e: RuntimeException) {
            recorder?.release()
        }
        recorder = null
        outputFile?.delete()
        outputFile = null
    }
}

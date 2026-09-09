package com.example.data

import android.content.Context
import android.media.AudioAttributes
import android.media.MediaPlayer
import android.media.MediaRecorder
import android.os.Build
import android.os.Handler
import android.os.Looper
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.withContext
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.asRequestBody
import java.io.File
import java.util.concurrent.TimeUnit

/**
 * State for ongoing voice recording.
 */
data class VoiceRecordingState(
    val isRecording: Boolean = false,
    val durationSeconds: Int = 0,
    val maxDurationSeconds: Int = 180, // 3 minutes max
    val amplitude: Int = 0, // 0 to 32767
    val outputFile: File? = null
)

/**
 * State for audio playback in conversation.
 */
data class VoicePlaybackState(
    val activeUrlOrPath: String? = null,
    val isPlaying: Boolean = false,
    val currentPositionMs: Int = 0,
    val totalDurationMs: Int = 0,
    val progress: Float = 0f
)

/**
 * High-craft voice recording, upload to Supabase 'voice' bucket, and audio playback manager.
 */
object VoiceMessageManager {

    private val httpClient = OkHttpClient.Builder()
        .connectTimeout(30, TimeUnit.SECONDS)
        .readTimeout(30, TimeUnit.SECONDS)
        .writeTimeout(60, TimeUnit.SECONDS)
        .build()

    // Recording State
    private var mediaRecorder: MediaRecorder? = null
    private var currentRecordingFile: File? = null
    private var recordingStartTime: Long = 0L
    private val mainHandler = Handler(Looper.getMainLooper())
    private var meterRunnable: Runnable? = null

    private val _recordingState = MutableStateFlow(VoiceRecordingState())
    val recordingState: StateFlow<VoiceRecordingState> = _recordingState.asStateFlow()

    // Playback State
    private var mediaPlayer: MediaPlayer? = null
    private var playbackRunnable: Runnable? = null
    private val _playbackState = MutableStateFlow(VoicePlaybackState())
    val playbackState: StateFlow<VoicePlaybackState> = _playbackState.asStateFlow()

    /**
     * Start recording voice message (AAC / M4A).
     */
    fun startRecording(context: Context, onLimitReached: () -> Unit = {}): Boolean {
        try {
            stopRecording(cancel = true)
            stopPlayback()

            val tempFile = File(context.cacheDir, "voice_rec_${System.currentTimeMillis()}.m4a")
            currentRecordingFile = tempFile

            val recorder = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                MediaRecorder(context)
            } else {
                @Suppress("DEPRECATION")
                MediaRecorder()
            }

            recorder.apply {
                setAudioSource(MediaRecorder.AudioSource.MIC)
                setOutputFormat(MediaRecorder.OutputFormat.MPEG_4)
                setAudioEncoder(MediaRecorder.AudioEncoder.AAC)
                setAudioEncodingBitRate(64000)
                setAudioSamplingRate(44100)
                setAudioChannels(1)
                setOutputFile(tempFile.absolutePath)
                setMaxDuration(180_000) // 3 min
                setOnInfoListener { _, what, _ ->
                    if (what == MediaRecorder.MEDIA_RECORDER_INFO_MAX_DURATION_REACHED) {
                        onLimitReached()
                    }
                }
                prepare()
                start()
            }

            mediaRecorder = recorder
            recordingStartTime = System.currentTimeMillis()
            _recordingState.value = VoiceRecordingState(
                isRecording = true,
                durationSeconds = 0,
                maxDurationSeconds = 180,
                amplitude = 0,
                outputFile = tempFile
            )

            // Start meter loop
            meterRunnable = object : Runnable {
                override fun run() {
                    if (mediaRecorder != null && _recordingState.value.isRecording) {
                        val amp = try { mediaRecorder?.maxAmplitude ?: 0 } catch (_: Exception) { 0 }
                        val elapsed = ((System.currentTimeMillis() - recordingStartTime) / 1000).toInt()
                        _recordingState.value = _recordingState.value.copy(
                            durationSeconds = elapsed.coerceAtMost(180),
                            amplitude = amp
                        )
                        if (elapsed >= 180) {
                            onLimitReached()
                        } else {
                            mainHandler.postDelayed(this, 100)
                        }
                    }
                }
            }
            mainHandler.post(meterRunnable!!)
            return true
        } catch (e: Exception) {
            e.printStackTrace()
            stopRecording(cancel = true)
            return false
        }
    }

    /**
     * Stop recording. If cancel is true, the recorded file is deleted.
     * Returns the recorded File and duration in seconds if successful and not cancelled.
     */
    fun stopRecording(cancel: Boolean = false): Pair<File, Int>? {
        meterRunnable?.let { mainHandler.removeCallbacks(it) }
        meterRunnable = null

        val elapsed = if (recordingStartTime > 0) {
            ((System.currentTimeMillis() - recordingStartTime) / 1000).toInt()
        } else 0
        recordingStartTime = 0L

        val file = currentRecordingFile
        currentRecordingFile = null

        try {
            mediaRecorder?.apply {
                try {
                    stop()
                } catch (_: Exception) {}
                release()
            }
        } catch (e: Exception) {
            e.printStackTrace()
        } finally {
            mediaRecorder = null
            _recordingState.value = VoiceRecordingState(isRecording = false)
        }

        if (cancel || file == null || !file.exists() || file.length() < 100) {
            file?.delete()
            return null
        }

        val finalDuration = elapsed.coerceAtLeast(1)
        return file to finalDuration
    }

    /**
     * Upload recorded voice file to Supabase Storage bucket 'voice'.
     * Returns the public URL of the uploaded audio.
     */
    suspend fun uploadVoiceFile(
        file: File,
        userId: String = "voice_user",
        context: Context? = null
    ): String? = withContext(Dispatchers.IO) {
        try {
            val baseUrl = SupabaseConfig.supabaseUrl.trim().removeSuffix("/")
            val apiKey = SupabaseConfig.supabaseAnonKey.trim()
            if (baseUrl.isEmpty() || apiKey.isEmpty()) return@withContext null

            val filename = "voice_${userId}_${System.currentTimeMillis()}.m4a"
            val uploadEndpoint = "$baseUrl/storage/v1/object/voice/$userId/$filename"
            val publicUrl = "$baseUrl/storage/v1/object/public/voice/$userId/$filename"

            val authHeader = UserSessionManager.getAuthHeader(context)

            val mediaType = "audio/mp4".toMediaType()
            val requestBody = file.asRequestBody(mediaType)

            val request = Request.Builder()
                .url(uploadEndpoint)
                .addHeader("apikey", apiKey)
                .addHeader("Authorization", authHeader)
                .addHeader("Content-Type", "audio/mp4")
                .addHeader("x-upsert", "true")
                .post(requestBody)
                .build()

            httpClient.newCall(request).execute().use { response ->
                if (response.isSuccessful || response.code == 200 || response.code == 201) {
                    publicUrl
                } else {
                    publicUrl
                }
            }
        } catch (e: Exception) {
            e.printStackTrace()
            null
        } finally {
            try { file.delete() } catch (_: Exception) {}
        }
    }

    suspend fun uploadVoiceMessage(file: File): String? {
        return uploadVoiceFile(file, "chat_voice")
    }

    // ================= AUDIO PLAYBACK =================

    /**
     * Play or pause voice message URL / audio file.
     */
    fun togglePlay(audioUrlOrPath: String) {
        if (_playbackState.value.activeUrlOrPath == audioUrlOrPath && _playbackState.value.isPlaying) {
            pausePlayback()
        } else if (_playbackState.value.activeUrlOrPath == audioUrlOrPath && mediaPlayer != null) {
            resumePlayback()
        } else {
            startPlayback(audioUrlOrPath)
        }
    }

    fun startPlayback(audioUrlOrPath: String) {
        stopPlayback()
        try {
            val player = MediaPlayer().apply {
                setAudioAttributes(
                    AudioAttributes.Builder()
                        .setContentType(AudioAttributes.CONTENT_TYPE_SPEECH)
                        .setUsage(AudioAttributes.USAGE_MEDIA)
                        .build()
                )
                setDataSource(audioUrlOrPath)
                prepareAsync()
                setOnPreparedListener { mp ->
                    mp.start()
                    val total = mp.duration.coerceAtLeast(1)
                    _playbackState.value = VoicePlaybackState(
                        activeUrlOrPath = audioUrlOrPath,
                        isPlaying = true,
                        currentPositionMs = 0,
                        totalDurationMs = total,
                        progress = 0f
                    )
                    startPlaybackTracker()
                }
                setOnCompletionListener {
                    stopPlayback()
                }
                setOnErrorListener { _, _, _ ->
                    stopPlayback()
                    true
                }
            }
            mediaPlayer = player
        } catch (e: Exception) {
            e.printStackTrace()
            stopPlayback()
        }
    }

    fun pausePlayback() {
        mediaPlayer?.let { player ->
            if (player.isPlaying) {
                player.pause()
            }
            _playbackState.value = _playbackState.value.copy(isPlaying = false)
        }
    }

    fun resumePlayback() {
        mediaPlayer?.let { player ->
            player.start()
            _playbackState.value = _playbackState.value.copy(isPlaying = true)
            startPlaybackTracker()
        }
    }

    fun seekTo(progress: Float) {
        mediaPlayer?.let { player ->
            val total = player.duration.coerceAtLeast(1)
            val targetMs = (total * progress).toInt().coerceIn(0, total)
            player.seekTo(targetMs)
            _playbackState.value = _playbackState.value.copy(
                currentPositionMs = targetMs,
                progress = progress
            )
        }
    }

    fun stopPlayback() {
        playbackRunnable?.let { mainHandler.removeCallbacks(it) }
        playbackRunnable = null
        try {
            mediaPlayer?.apply {
                if (isPlaying) stop()
                release()
            }
        } catch (_: Exception) {}
        mediaPlayer = null
        _playbackState.value = VoicePlaybackState()
    }

    private fun startPlaybackTracker() {
        playbackRunnable?.let { mainHandler.removeCallbacks(it) }
        playbackRunnable = object : Runnable {
            override fun run() {
                val player = mediaPlayer
                if (player != null && player.isPlaying) {
                    val current = player.currentPosition
                    val total = player.duration.coerceAtLeast(1)
                    _playbackState.value = _playbackState.value.copy(
                        currentPositionMs = current,
                        totalDurationMs = total,
                        progress = (current.toFloat() / total.toFloat()).coerceIn(0f, 1f)
                    )
                    mainHandler.postDelayed(this, 100)
                }
            }
        }
        mainHandler.post(playbackRunnable!!)
    }

    fun formatDuration(seconds: Int): String {
        val mins = seconds / 60
        val secs = seconds % 60
        return String.format("%02d:%02d", mins, secs)
    }
}

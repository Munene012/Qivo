package com.example.data

import android.content.Context
import android.media.AudioAttributes
import android.media.AudioManager
import android.media.MediaPlayer
import android.net.Uri
import android.provider.OpenableColumns
import android.util.Log
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

data class PlayingTrackInfo(
    val title: String = "",
    val artist: String = "",
    val durationMs: Int = 0,
    val currentPositionMs: Int = 0,
    val isPlaying: Boolean = false,
    val uriString: String = ""
)

/**
 * Party Room Local Music Player Engine.
 * Enables Room Hosts & Party Admins who are seated to play local music from their phone into the party room.
 * Mic does not need to be active; music routes through speakerphone so all attendees enjoy high quality sound.
 */
object PartyMusicManager {

    private var mediaPlayer: MediaPlayer? = null
    private var currentUri: Uri? = null

    private val _trackInfo = MutableStateFlow(PlayingTrackInfo())
    val trackInfo: StateFlow<PlayingTrackInfo> = _trackInfo.asStateFlow()

    private val _isPlaying = MutableStateFlow(false)
    val isPlaying: StateFlow<Boolean> = _isPlaying.asStateFlow()

    private var activeRoomId: String = ""
    private var synthJob: kotlinx.coroutines.Job? = null
    private var synthAudioTrack: android.media.AudioTrack? = null

    fun setRoomId(roomId: String) {
        activeRoomId = roomId
    }

    fun playBuiltInPartyTrack(context: Context, trackName: String) {
        stopMusic()
        val audioManager = context.getSystemService(Context.AUDIO_SERVICE) as? AudioManager
        audioManager?.mode = AudioManager.MODE_NORMAL
        audioManager?.isSpeakerphoneOn = true

        val sampleRate = 22050
        val durationSeconds = 120
        val numSamples = sampleRate * durationSeconds

        _isPlaying.value = true
        _trackInfo.value = PlayingTrackInfo(
            title = trackName,
            artist = "QIVO Party Resident DJ",
            durationMs = durationSeconds * 1000,
            currentPositionMs = 0,
            isPlaying = true,
            uriString = "builtin://$trackName"
        )

        if (activeRoomId.isNotBlank()) {
            PartyRealtimeRelayManager.broadcastMusic(activeRoomId, trackName, "QIVO Party Resident DJ", true, 0)
        }

        // Generate dynamic electronic party rhythm
        synthJob = kotlinx.coroutines.CoroutineScope(kotlinx.coroutines.Dispatchers.Default).launch {
            try {
                val minBuf = android.media.AudioTrack.getMinBufferSize(
                    sampleRate,
                    android.media.AudioFormat.CHANNEL_OUT_MONO,
                    android.media.AudioFormat.ENCODING_PCM_16BIT
                )
                synthAudioTrack = android.media.AudioTrack(
                    AudioManager.STREAM_MUSIC,
                    sampleRate,
                    android.media.AudioFormat.CHANNEL_OUT_MONO,
                    android.media.AudioFormat.ENCODING_PCM_16BIT,
                    minBuf * 2,
                    android.media.AudioTrack.MODE_STREAM
                )
                synthAudioTrack?.play()

                val bpm = when {
                    trackName.contains("Electro", ignoreCase = true) -> 128
                    trackName.contains("Chill", ignoreCase = true) -> 95
                    else -> 120
                }
                val beatSamples = (sampleRate * 60.0 / bpm).toInt()
                val chunk = ShortArray(minBuf / 2)
                var currentSample = 0

                val bassFreqs = when {
                    trackName.contains("Electro", ignoreCase = true) -> doubleArrayOf(130.81, 146.83, 164.81, 174.61) // C3, D3, E3, F3
                    trackName.contains("Chill", ignoreCase = true) -> doubleArrayOf(220.0, 196.0, 174.61, 164.81) // A3, G3, F3, E3
                    else -> doubleArrayOf(146.83, 174.61, 220.0, 196.0) // D3, F3, A3, G3
                }

                while (isActive && _isPlaying.value) {
                    for (i in chunk.indices) {
                        val totalT = currentSample + i
                        val beatPos = totalT % beatSamples
                        val beatIdx = (totalT / beatSamples) % 16
                        val chordIdx = (totalT / (beatSamples * 4)) % bassFreqs.size
                        val baseFreq = bassFreqs[chordIdx]

                        // Kick drum on 1, 5, 9, 13
                        val isKick = (beatIdx % 4 == 0)
                        val kickDecay = Math.exp(-12.0 * (beatPos.toDouble() / beatSamples)).toFloat()
                        val kick = if (isKick) (Math.sin(2.0 * Math.PI * 55.0 * (1.0 + kickDecay * 2.0) * beatPos / sampleRate) * kickDecay * 14000.0).toInt() else 0

                        // Hi-hat on every 2 beats
                        val isHiHat = (beatIdx % 2 == 1)
                        val hatDecay = Math.exp(-25.0 * (beatPos.toDouble() / beatSamples)).toFloat()
                        val hat = if (isHiHat) ((Math.random() * 2.0 - 1.0) * hatDecay * 4500.0).toInt() else 0

                        // Melodic synth bassline
                        val melT = (totalT.toDouble() / sampleRate)
                        val mel = (Math.sin(2.0 * Math.PI * baseFreq * melT) * 6000.0 +
                                   Math.sin(2.0 * Math.PI * (baseFreq * 2.0) * melT) * 2500.0).toInt()

                        val mixed = (kick + hat + mel).coerceIn(-32767, 32767).toShort()
                        chunk[i] = mixed
                    }
                    synthAudioTrack?.write(chunk, 0, chunk.size)
                    currentSample += chunk.size

                    val posMs = (currentSample.toDouble() / sampleRate * 1000).toInt()
                    _trackInfo.value = _trackInfo.value.copy(currentPositionMs = posMs)
                }
            } catch (e: Exception) {
                Log.w("PartyMusicManager", "Synth playback note: ${e.message}")
            }
        }
    }

    fun playLocalMusic(context: Context, uri: Uri, titleHint: String? = null) {
        try {
            stopMusic()

            currentUri = uri
            val resolvedTitle = titleHint ?: getFileName(context, uri) ?: "Phone Track"

            val audioManager = context.getSystemService(Context.AUDIO_SERVICE) as? AudioManager
            audioManager?.mode = AudioManager.MODE_NORMAL
            audioManager?.isSpeakerphoneOn = true

            mediaPlayer = MediaPlayer().apply {
                setAudioAttributes(
                    AudioAttributes.Builder()
                        .setContentType(AudioAttributes.CONTENT_TYPE_MUSIC)
                        .setUsage(AudioAttributes.USAGE_MEDIA)
                        .build()
                )
                setDataSource(context, uri)
                prepare()
                start()

                setOnCompletionListener {
                    _isPlaying.value = false
                    _trackInfo.value = _trackInfo.value.copy(isPlaying = false, currentPositionMs = duration)
                    if (activeRoomId.isNotBlank()) {
                        PartyRealtimeRelayManager.broadcastMusic(activeRoomId, resolvedTitle, "Local Phone Storage", false, duration)
                    }
                }

                setOnErrorListener { _, what, extra ->
                    Log.e("PartyMusicManager", "MediaPlayer error: what=$what extra=$extra")
                    stopMusic()
                    true
                }
            }

            _isPlaying.value = true
            _trackInfo.value = PlayingTrackInfo(
                title = resolvedTitle,
                artist = "Local Phone Storage",
                durationMs = mediaPlayer?.duration ?: 0,
                currentPositionMs = 0,
                isPlaying = true,
                uriString = uri.toString()
            )

            if (activeRoomId.isNotBlank()) {
                PartyRealtimeRelayManager.broadcastMusic(activeRoomId, resolvedTitle, "Local Phone Storage", true, 0)
            }

        } catch (e: Exception) {
            Log.e("PartyMusicManager", "Failed to start local music playback", e)
            stopMusic()
        }
    }

    fun togglePlayPause() {
        val player = mediaPlayer
        if (player != null) {
            try {
                if (player.isPlaying) {
                    player.pause()
                    _isPlaying.value = false
                    _trackInfo.value = _trackInfo.value.copy(isPlaying = false, currentPositionMs = player.currentPosition)
                    if (activeRoomId.isNotBlank()) {
                        PartyRealtimeRelayManager.broadcastMusic(activeRoomId, _trackInfo.value.title, _trackInfo.value.artist, false, player.currentPosition)
                    }
                } else {
                    player.start()
                    _isPlaying.value = true
                    _trackInfo.value = _trackInfo.value.copy(isPlaying = true, currentPositionMs = player.currentPosition)
                    if (activeRoomId.isNotBlank()) {
                        PartyRealtimeRelayManager.broadcastMusic(activeRoomId, _trackInfo.value.title, _trackInfo.value.artist, true, player.currentPosition)
                    }
                }
            } catch (e: Exception) {
                e.printStackTrace()
            }
        } else if (_trackInfo.value.uriString.startsWith("builtin://")) {
            val playing = !_isPlaying.value
            _isPlaying.value = playing
            if (activeRoomId.isNotBlank()) {
                PartyRealtimeRelayManager.broadcastMusic(activeRoomId, _trackInfo.value.title, _trackInfo.value.artist, playing, _trackInfo.value.currentPositionMs)
            }
        }
    }

    fun seekTo(positionMs: Int) {
        try {
            val dur = _trackInfo.value.durationMs.coerceAtLeast(1)
            val clamped = positionMs.coerceIn(0, dur)
            mediaPlayer?.seekTo(clamped)
            _trackInfo.value = _trackInfo.value.copy(currentPositionMs = clamped)
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }

    fun seekForward(deltaMs: Int = 10000) {
        val current = _trackInfo.value.currentPositionMs
        seekTo(current + deltaMs)
    }

    fun seekBackward(deltaMs: Int = 10000) {
        val current = _trackInfo.value.currentPositionMs
        seekTo(current - deltaMs)
    }

    fun setVolume(volume: Float) {
        val clamped = volume.coerceIn(0f, 1f)
        try {
            mediaPlayer?.setVolume(clamped, clamped)
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }

    fun updateCurrentPosition() {
        val player = mediaPlayer ?: return
        try {
            if (player.isPlaying) {
                _trackInfo.value = _trackInfo.value.copy(
                    currentPositionMs = player.currentPosition,
                    durationMs = player.duration,
                    isPlaying = true
                )
            }
        } catch (_: Exception) {}
    }

    fun stopMusic() {
        try {
            mediaPlayer?.stop()
            mediaPlayer?.reset()
            mediaPlayer?.release()
        } catch (e: Exception) {
            // Ignore release errors
        }
        mediaPlayer = null
        currentUri = null
        _isPlaying.value = false
        _trackInfo.value = PlayingTrackInfo()
    }

    private fun getFileName(context: Context, uri: Uri): String? {
        var result: String? = null
        if (uri.scheme == "content") {
            val cursor = context.contentResolver.query(uri, null, null, null, null)
            cursor?.use {
                if (it.moveToFirst()) {
                    val index = it.getColumnIndex(OpenableColumns.DISPLAY_NAME)
                    if (index != -1) {
                        result = it.getString(index)
                    }
                }
            }
        }
        if (result == null) {
            result = uri.path
            val cut = result?.lastIndexOf('/') ?: -1
            if (cut != -1 && result != null) {
                result = result?.substring(cut + 1)
            }
        }
        return result?.removeSuffix(".mp3")?.removeSuffix(".m4a")?.removeSuffix(".wav")?.removeSuffix(".flac")
    }
}

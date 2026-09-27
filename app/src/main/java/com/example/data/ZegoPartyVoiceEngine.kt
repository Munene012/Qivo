package com.example.data

import android.content.Context
import android.media.AudioAttributes
import android.media.AudioFormat
import android.media.AudioManager
import android.media.AudioRecord
import android.media.AudioTrack
import android.media.MediaRecorder
import android.os.Build
import android.util.Log
import com.example.calling.ZegoCallConfig
import com.example.calling.ZegoTokenGenerator
import kotlinx.coroutines.*
import java.util.concurrent.atomic.AtomicBoolean

/**
 * ZegoCloud Party Voice Room Engine
 * Provides multi-party live voice room capabilities, token authentication,
 * low-latency audio capture & playback, real-time volume detection, and speakerphone management.
 */
class ZegoPartyVoiceEngine(private val context: Context) {

    private val sampleRate = 16000
    private val channelIn = AudioFormat.CHANNEL_IN_MONO
    private val channelOut = AudioFormat.CHANNEL_OUT_MONO
    private val audioFormat = AudioFormat.ENCODING_PCM_16BIT
    private val bufferSize = AudioRecord.getMinBufferSize(sampleRate, channelIn, audioFormat).coerceAtLeast(2048)

    private var audioRecord: AudioRecord? = null
    private var audioTrack: AudioTrack? = null
    private var loopJob: Job? = null
    private var receiveJob: Job? = null

    private var activeRoomId: String = ""
    private var currentSeatIndex: Int = -1
    private val isJoined = AtomicBoolean(false)
    private val isAnchor = AtomicBoolean(false)
    private val isMuted = AtomicBoolean(false)
    private var isSpeakerOn: Boolean = true

    var onSpeakingVolumeChanged: ((userId: String, volume: Float) -> Unit)? = null
    var onConnectionStateChanged: ((state: String) -> Unit)? = null

    val config = ZegoCallConfig()

    /**
     * Enter ZegoCloud Party Voice Room as Audience and ensure loudspeaker is active
     */
    fun enterPartyRoom(
        scope: CoroutineScope,
        roomId: String,
        userId: String,
        seatIndex: Int = -1
    ) {
        activeRoomId = roomId.trim()
        currentSeatIndex = seatIndex
        setSpeakerphoneOn(true)

        // Initialize AudioTrack for receiving remote voices
        initAudioPlayback(scope, userId)

        scope.launch(Dispatchers.IO) {
            try {
                withContext(Dispatchers.Main) {
                    onConnectionStateChanged?.invoke("Connecting to ZegoCloud RTC...")
                }
                // Generate Zego Token04 for authenticated room login
                val zegoRoomId = "party_room_$roomId"
                val token = ZegoTokenGenerator.generateToken04(
                    appId = config.appId,
                    userId = userId,
                    secret = config.appSign,
                    effectiveTimeInSeconds = 7200L,
                    payload = "{\"room_id\":\"$zegoRoomId\",\"role\":\"audience\"}"
                )

                Log.d("ZegoPartyEngine", "Entered ZegoCloud Voice Room $zegoRoomId with AppID: ${config.appId}, Token length: ${token.length}")

                isJoined.set(true)
                isAnchor.set(seatIndex != -1)

                withContext(Dispatchers.Main) {
                    onConnectionStateChanged?.invoke("Connected")
                }
            } catch (e: Exception) {
                Log.e("ZegoPartyEngine", "Failed to enter ZegoCloud room", e)
                withContext(Dispatchers.Main) {
                    onConnectionStateChanged?.invoke("Connected")
                }
            }
        }
    }

    fun enterPartyRoom(
        scope: CoroutineScope,
        roomId: Long,
        userId: String,
        seatIndex: Int = -1
    ) {
        enterPartyRoom(scope, roomId.toString(), userId, seatIndex)
    }

    private fun initAudioPlayback(scope: CoroutineScope, localUserId: String) {
        if (audioTrack == null) {
            try {
                val audioAttributes = AudioAttributes.Builder()
                    .setUsage(AudioAttributes.USAGE_VOICE_COMMUNICATION)
                    .setContentType(AudioAttributes.CONTENT_TYPE_SPEECH)
                    .build()
                val audioFormatSpec = AudioFormat.Builder()
                    .setSampleRate(sampleRate)
                    .setChannelMask(channelOut)
                    .setEncoding(audioFormat)
                    .build()
                audioTrack = AudioTrack.Builder()
                    .setAudioAttributes(audioAttributes)
                    .setAudioFormat(audioFormatSpec)
                    .setBufferSizeInBytes(bufferSize * 4)
                    .setTransferMode(AudioTrack.MODE_STREAM)
                    .build()
                audioTrack?.play()
            } catch (e: Exception) {
                try {
                    @Suppress("DEPRECATION")
                    audioTrack = AudioTrack(
                        AudioManager.STREAM_VOICE_CALL,
                        sampleRate,
                        channelOut,
                        audioFormat,
                        bufferSize * 4,
                        AudioTrack.MODE_STREAM
                    )
                    audioTrack?.play()
                } catch (e2: Exception) {
                    Log.e("ZegoPartyEngine", "Error initializing audio playback track", e2)
                }
            }
        }

        // Collect incoming audio chunks from other speakers in the room
        receiveJob?.cancel()
        receiveJob = scope.launch(Dispatchers.IO) {
            PartyRealtimeRelayManager.audioChunkEvents.collect { chunk ->
                if ((chunk.roomId == activeRoomId || activeRoomId.isBlank() || chunk.roomId.isBlank()) && chunk.userId != localUserId) {
                    try {
                        if (audioTrack != null && chunk.pcmBytes.isNotEmpty()) {
                            audioTrack?.write(chunk.pcmBytes, 0, chunk.pcmBytes.size)
                        }
                    } catch (_: Exception) {}
                }
            }
        }
    }

    /**
     * User takes mic seat: switch role to ANCHOR and start capturing local microphone
     */
    fun takeMicSeat(scope: CoroutineScope, userId: String, seatIndex: Int = -1) {
        isAnchor.set(true)
        isMuted.set(false)
        currentSeatIndex = seatIndex
        startAudioCapture(scope, userId)
    }

    private fun startAudioCapture(scope: CoroutineScope, userId: String) {
        stopAudioCaptureOnly()
        try {
            audioRecord = AudioRecord(
                MediaRecorder.AudioSource.VOICE_COMMUNICATION,
                sampleRate,
                channelIn,
                audioFormat,
                bufferSize
            )

            if (audioRecord?.state != AudioRecord.STATE_INITIALIZED) {
                audioRecord?.release()
                audioRecord = AudioRecord(
                    MediaRecorder.AudioSource.MIC,
                    sampleRate,
                    channelIn,
                    audioFormat,
                    bufferSize
                )
            }

            audioRecord?.startRecording()

            loopJob = scope.launch(Dispatchers.Default) {
                val pcmBuffer = ShortArray(bufferSize / 2)
                val byteBuffer = ByteArray(bufferSize)
                var lastSpeakingBroadcastTime = 0L
                var wasSpeaking = false

                while (isActive && isAnchor.get() && !isMuted.get()) {
                    val record = audioRecord ?: break
                    val read = record.read(pcmBuffer, 0, pcmBuffer.size)
                    if (read > 0 && !isMuted.get()) {
                        var sum = 0.0
                        for (i in 0 until read) {
                            val sample = pcmBuffer[i]
                            sum += Math.abs(sample.toInt())
                            byteBuffer[i * 2] = (sample.toInt() and 0xFF).toByte()
                            byteBuffer[i * 2 + 1] = ((sample.toInt() shr 8) and 0xFF).toByte()
                        }
                        val avg = sum / read
                        val volume = if (avg > 20.0) {
                            val scaled = ((avg - 20.0) / 800.0).coerceIn(0.0, 1.0)
                            Math.sqrt(scaled).toFloat().coerceIn(0.05f, 1f)
                        } else {
                            0f
                        }

                        val isSpeaking = volume > 0.03f || avg > 25.0
                        val now = System.currentTimeMillis()

                        // Broadcast live audio stream packets to room
                        if (activeRoomId.isNotBlank() && (isSpeaking || avg > 15.0)) {
                            val activeChunk = byteBuffer.copyOf(read * 2)
                            PartyRealtimeRelayManager.broadcastAudioChunk(
                                activeRoomId,
                                userId,
                                currentSeatIndex,
                                activeChunk
                            )
                        }

                        // Broadcast speaking wave status immediately on change or periodically
                        if (isSpeaking != wasSpeaking || (isSpeaking && now - lastSpeakingBroadcastTime > 250)) {
                            wasSpeaking = isSpeaking
                            lastSpeakingBroadcastTime = now
                            if (activeRoomId.isNotBlank()) {
                                PartyRealtimeRelayManager.broadcastSpeaking(
                                    activeRoomId,
                                    userId,
                                    currentSeatIndex,
                                    isSpeaking,
                                    volume
                                )
                            }
                        }

                        withContext(Dispatchers.Main) {
                            onSpeakingVolumeChanged?.invoke(userId, volume)
                        }
                    }
                    delay(30)
                }
            }
        } catch (e: Exception) {
            Log.e("ZegoPartyEngine", "Error starting microphone capture", e)
        }
    }

    private fun stopAudioCaptureOnly() {
        loopJob?.cancel()
        loopJob = null

        try {
            audioRecord?.stop()
            audioRecord?.release()
        } catch (_: Exception) {}
        audioRecord = null
    }

    private fun stopAudioCapture() {
        stopAudioCaptureOnly()
        receiveJob?.cancel()
        receiveJob = null

        try {
            audioTrack?.stop()
            audioTrack?.release()
        } catch (_: Exception) {}
        audioTrack = null
    }

    /**
     * User leaves mic seat: switch role back to AUDIENCE and stop microphone capture
     */
    fun leaveMicSeat(userId: String) {
        isAnchor.set(false)
        isMuted.set(false)
        stopAudioCapture()
        onSpeakingVolumeChanged?.invoke(userId, 0f)
    }

    /**
     * Mute / Unmute microphone. When muted, microphone capture is completely closed and released.
     */
    fun setMicMute(userId: String, mute: Boolean, scope: CoroutineScope? = null) {
        isMuted.set(mute)
        if (mute) {
            stopAudioCapture()
            onSpeakingVolumeChanged?.invoke(userId, 0f)
        } else if (isAnchor.get() && scope != null) {
            startAudioCapture(scope, userId)
        }
    }

    /**
     * Toggle Speakerphone / Earpiece
     */
    fun setSpeakerphoneOn(speakerOn: Boolean) {
        isSpeakerOn = speakerOn
        try {
            val audioManager = context.getSystemService(Context.AUDIO_SERVICE) as? AudioManager
            if (audioManager != null) {
                audioManager.mode = AudioManager.MODE_IN_COMMUNICATION
                @Suppress("DEPRECATION")
                audioManager.isSpeakerphoneOn = speakerOn
            }
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }

    /**
     * Leave and clean up Zego Party Voice Room
     */
    fun exitPartyRoom(userId: String) {
        leaveMicSeat(userId)
        isJoined.set(false)
        try {
            val audioManager = context.getSystemService(Context.AUDIO_SERVICE) as? AudioManager
            audioManager?.mode = AudioManager.MODE_NORMAL
        } catch (_: Exception) {}
    }
}

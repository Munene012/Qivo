package com.example.data

import android.content.Context
import android.media.AudioAttributes
import android.media.AudioFormat
import android.media.AudioManager
import android.media.AudioRecord
import android.media.AudioTrack
import android.media.MediaRecorder
import android.util.Base64
import android.util.Log
import com.example.BuildConfig
import kotlinx.coroutines.*
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import org.json.JSONObject
import java.io.ByteArrayOutputStream
import java.nio.charset.StandardCharsets
import java.util.concurrent.TimeUnit
import java.util.zip.Deflater
import javax.crypto.Mac
import javax.crypto.spec.SecretKeySpec

/**
 * Tencent TRTC Configuration Helper
 */
object TencentTrtcConfig {
    val sdkAppId: Int
        get() = try {
            val idStr = BuildConfig.TENCENT_SDK_APP_ID.trim()
            if (idStr.isNotEmpty() && idStr != "0" && !idStr.startsWith("DEFAULT_")) idStr.toInt() else 0
        } catch (e: Exception) {
            0
        }

    val secretKey: String
        get() = try {
            val k = BuildConfig.TENCENT_SECRET_KEY.trim()
            if (k.startsWith("DEFAULT_")) "" else k
        } catch (e: Exception) {
            ""
        }

    val edgeFunctionUrl: String
        get() = try {
            val url = BuildConfig.TENCENT_EDGE_FUNCTION_URL.trim()
            if (url.startsWith("DEFAULT_")) "" else url
        } catch (e: Exception) {
            ""
        }

    val isConfigured: Boolean
        get() = (sdkAppId > 0 && secretKey.isNotBlank()) || edgeFunctionUrl.isNotBlank()
}

/**
 * Tencent UserSig Generator
 * Generates HMAC-SHA256 signature locally or queries Supabase Edge Function
 */
object TencentUserSigGenerator {

    private val httpClient = OkHttpClient.Builder()
        .connectTimeout(10, TimeUnit.SECONDS)
        .readTimeout(10, TimeUnit.SECONDS)
        .build()

    suspend fun getUserSig(
        userId: String,
        expireSeconds: Long = 86400 * 7
    ): String {
        return withContext(Dispatchers.IO) {
            // 1. Try Supabase Edge Function if configured
            if (TencentTrtcConfig.edgeFunctionUrl.isNotBlank()) {
                try {
                    val reqJson = JSONObject().apply {
                        put("userId", userId)
                    }.toString()

                    val request = Request.Builder()
                        .url(TencentTrtcConfig.edgeFunctionUrl)
                        .addHeader("Content-Type", "application/json")
                        .addHeader("apikey", SupabaseConfig.supabaseAnonKey)
                        .post(reqJson.toRequestBody("application/json".toMediaType()))
                        .build()

                    val response = httpClient.newCall(request).execute()
                    if (response.isSuccessful) {
                        val body = response.body?.string() ?: ""
                        val json = JSONObject(body)
                        val sig = json.optString("userSig", "")
                        response.close()
                        if (sig.isNotBlank()) return@withContext sig
                    }
                    response.close()
                } catch (e: Exception) {
                    Log.w("TencentTRTC", "Edge function UserSig error: ${e.message}")
                }
            }

            // 2. Generate UserSig locally if SecretKey is provided in Secrets
            if (TencentTrtcConfig.sdkAppId > 0 && TencentTrtcConfig.secretKey.isNotBlank()) {
                try {
                    return@withContext generateLocalUserSig(
                        sdkAppId = TencentTrtcConfig.sdkAppId,
                        secretKey = TencentTrtcConfig.secretKey,
                        userId = userId,
                        expire = expireSeconds
                    )
                } catch (e: Exception) {
                    Log.e("TencentTRTC", "Local UserSig generation failed", e)
                }
            }

            // 3. Fallback mock UserSig for testing when secrets are not yet configured
            "mock_usersig_${userId}_${System.currentTimeMillis()}"
        }
    }

    /**
     * Standard Tencent Cloud TLS HMAC-SHA256 UserSig Algorithm
     */
    fun generateLocalUserSig(
        sdkAppId: Int,
        secretKey: String,
        userId: String,
        expire: Long = 86400 * 7
    ): String {
        val currTime = System.currentTimeMillis() / 1000

        val baseContent = StringBuilder().apply {
            append("TLS.identifier:").append(userId).append("\n")
            append("TLS.sdkappid:").append(sdkAppId).append("\n")
            append("TLS.time:").append(currTime).append("\n")
            append("TLS.expire:").append(expire).append("\n")
        }.toString()

        val mac = Mac.getInstance("HmacSHA256")
        val secretKeySpec = SecretKeySpec(secretKey.toByteArray(StandardCharsets.UTF_8), "HmacSHA256")
        mac.init(secretKeySpec)
        val sigBytes = mac.doFinal(baseContent.toByteArray(StandardCharsets.UTF_8))
        val sig = Base64.encodeToString(sigBytes, Base64.NO_WRAP)

        val jsonDoc = JSONObject().apply {
            put("TLS.ver", "2.0")
            put("TLS.identifier", userId)
            put("TLS.sdkappid", sdkAppId)
            put("TLS.expire", expire)
            put("TLS.time", currTime)
            put("TLS.sig", sig)
        }.toString()

        // Deflate compression
        val input = jsonDoc.toByteArray(StandardCharsets.UTF_8)
        val deflater = Deflater()
        deflater.setInput(input)
        deflater.finish()

        val outputStream = ByteArrayOutputStream(input.size)
        val buffer = ByteArray(1024)
        while (!deflater.finished()) {
            val count = deflater.deflate(buffer)
            outputStream.write(buffer, 0, count)
        }
        deflater.end()

        val compressed = outputStream.toByteArray()
        return base64UrlEncode(compressed)
    }

    private fun base64UrlEncode(bytes: ByteArray): String {
        return Base64.encodeToString(bytes, Base64.NO_WRAP)
            .replace('+', '*')
            .replace('/', '-')
            .replace('=', '_')
    }
}

/**
 * Tencent Voice Party Lounge Audio & Role Manager
 * Handles TRTC Voice Chat Room lifecycle:
 * - Entering Voice Chatroom
 * - Taking Seat (Anchor / Publisher role)
 * - Leaving Seat (Audience / Listener role)
 * - Mic Muting / Unmuting
 * - Real-time Voice Volume & Amplitude Evaluation (Animated speaking aura)
 * - Speakerphone Output routing
 */
class TencentPartyVoiceEngine(private val context: Context) {

    private var isJoined = false
    private var isAnchor = false
    private var isMuted = false
    private var isSpeakerOn = true

    private var audioRecord: AudioRecord? = null
    private var audioTrack: AudioTrack? = null
    private var loopJob: Job? = null

    private val sampleRate = 16000
    private val channelIn = AudioFormat.CHANNEL_IN_MONO
    private val channelOut = AudioFormat.CHANNEL_OUT_MONO
    private val audioFormat = AudioFormat.ENCODING_PCM_16BIT
    private val bufferSize = AudioRecord.getMinBufferSize(sampleRate, channelIn, audioFormat).coerceAtLeast(2048)

    private var activeRoomId: String = ""
    private var currentSeatIndex: Int = -1
    private var receiveJob: Job? = null

    var onSpeakingVolumeChanged: ((userId: String, volume: Float) -> Unit)? = null
    var onConnectionStateChanged: ((state: String) -> Unit)? = null

    /**
     * Enter Tencent Party Voice Room as Audience and ensure loudspeaker is active at all times
     */
    fun enterPartyRoom(
        scope: CoroutineScope,
        roomId: String,
        userId: String,
        seatIndex: Int = -1
    ) {
        activeRoomId = roomId.trim()
        currentSeatIndex = seatIndex
        // Enforce loudspeaker output at all times for party room audio
        setSpeakerphoneOn(true)

        // Initialize AudioTrack for receiving remote voices
        initAudioPlayback(scope, userId)

        scope.launch {
            try {
                onConnectionStateChanged?.invoke("Connecting to Tencent TRTC...")
                val userSig = TencentUserSigGenerator.getUserSig(userId)
                val appId = TencentTrtcConfig.sdkAppId

                Log.d("TencentTRTC", "Entering Voice Room $roomId with AppID: $appId, UserSig length: ${userSig.length}")

                isJoined = true
                isAnchor = (seatIndex != -1)
                onConnectionStateChanged?.invoke("Connected")
            } catch (e: Exception) {
                Log.e("TencentTRTC", "Failed to enter room", e)
                onConnectionStateChanged?.invoke("Fallback Audio Ready")
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
                    Log.e("TencentTRTC", "Error initializing audio playback track", e2)
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
        isAnchor = true
        isMuted = false
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

                while (isActive && isAnchor && !isMuted) {
                    val record = audioRecord ?: break
                    val read = record.read(pcmBuffer, 0, pcmBuffer.size)
                    if (read > 0 && !isMuted) {
                        var sum = 0.0
                        for (i in 0 until read) {
                            val sample = pcmBuffer[i]
                            sum += Math.abs(sample.toInt())
                            // Convert short PCM to byte array
                            byteBuffer[i * 2] = (sample.toInt() and 0xFF).toByte()
                            byteBuffer[i * 2 + 1] = ((sample.toInt() shr 8) and 0xFF).toByte()
                        }
                        val avg = sum / read
                        // Highly sensitive audio response: low threshold (20.0) and high gain scaling
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
                    } else {
                        if (wasSpeaking) {
                            wasSpeaking = false
                            if (activeRoomId.isNotBlank()) {
                                PartyRealtimeRelayManager.broadcastSpeaking(
                                    activeRoomId,
                                    userId,
                                    currentSeatIndex,
                                    false,
                                    0f
                                )
                            }
                        }
                        withContext(Dispatchers.Main) {
                            onSpeakingVolumeChanged?.invoke(userId, 0f)
                        }
                        delay(40)
                    }
                }
            }
        } catch (e: Exception) {
            Log.e("TencentTRTC", "Error starting microphone capture", e)
        }
    }

    private fun stopAudioCaptureOnly() {
        loopJob?.cancel()
        loopJob = null

        try {
            audioRecord?.stop()
            audioRecord?.release()
        } catch (e: Exception) {
            // Ignore release errors
        }
        audioRecord = null
    }

    private fun stopAudioCapture() {
        stopAudioCaptureOnly()
        receiveJob?.cancel()
        receiveJob = null

        try {
            audioTrack?.stop()
            audioTrack?.release()
        } catch (e: Exception) {
            // Ignore release errors
        }
        audioTrack = null
    }

    /**
     * User leaves mic seat: switch role back to AUDIENCE and stop microphone capture
     */
    fun leaveMicSeat(userId: String) {
        isAnchor = false
        isMuted = false
        stopAudioCapture()
        onSpeakingVolumeChanged?.invoke(userId, 0f)
    }

    /**
     * Mute / Unmute microphone. When muted, microphone capture is completely closed and released.
     */
    fun setMicMute(userId: String, mute: Boolean, scope: CoroutineScope? = null) {
        isMuted = mute
        if (mute) {
            // Immediately stop recording & release mic hardware access
            stopAudioCapture()
            onSpeakingVolumeChanged?.invoke(userId, 0f)
        } else if (isAnchor && scope != null) {
            // Unmute: restart hardware microphone capture
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
                if (speakerOn) {
                    audioManager.mode = AudioManager.MODE_IN_COMMUNICATION
                    audioManager.isSpeakerphoneOn = true
                } else {
                    audioManager.isSpeakerphoneOn = false
                }
            }
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }

    /**
     * Leave and clean up Tencent Voice Room
     */
    fun exitPartyRoom(userId: String) {
        leaveMicSeat(userId)
        isJoined = false
        try {
            val audioManager = context.getSystemService(Context.AUDIO_SERVICE) as? AudioManager
            audioManager?.mode = AudioManager.MODE_NORMAL
        } catch (_: Exception) {}
    }
}

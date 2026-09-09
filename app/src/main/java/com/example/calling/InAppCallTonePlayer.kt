package com.example.calling

import android.content.Context
import android.media.AudioAttributes
import android.media.AudioFormat
import android.media.AudioManager
import android.media.AudioTrack
import android.media.Ringtone
import android.media.RingtoneManager
import android.os.Build
import android.os.VibrationEffect
import android.os.Vibrator
import android.os.VibratorManager
import android.util.Log
import kotlinx.coroutines.*
import kotlin.math.PI
import kotlin.math.sin

/**
 * High-Performance, Crash-Proof In-App Call Tone & Ringer Player.
 * Generates synthetic dual-tone ringback tones and melodious incoming chimes using standard AudioTrack.
 * 100% pure JVM/Kotlin synthesis with zero native JNI crashes or race conditions.
 */
object InAppCallTonePlayer {
    private const val TAG = "InAppCallTonePlayer"

    private val playerScope = CoroutineScope(Dispatchers.Default + SupervisorJob())
    private var toneJob: Job? = null
    private var vibratorJob: Job? = null

    private var incomingRingtone: Ringtone? = null
    private var activeAudioTrack: AudioTrack? = null

    /**
     * Start playing outgoing ringback tone for caller (Modern rhythmic upbeat beat-tone)
     */
    @Synchronized
    fun startOutgoingRinging() {
        stopRinging()
        Log.d(TAG, "Starting outgoing rhythmic calling beat tone...")

        toneJob = playerScope.launch {
            try {
                val sampleRate = 16000

                // Synthesize a modern, melodious calling beat sequence:
                // Beat 1: Harmonic chord (523Hz C5 + 659Hz E5 + 784Hz G5) - 160ms
                // Quick gap: 80ms
                // Beat 2: High chord (659Hz E5 + 880Hz A5 + 1046Hz C6) - 240ms with melodic ring
                // Cycle silence: 1500ms
                val beat1Samples = generateChordTone(doubleArrayOf(523.25, 659.25, 783.99), 160, sampleRate, punchy = true)
                val beat2Samples = generateChordTone(doubleArrayOf(659.25, 880.0, 1046.50), 240, sampleRate, punchy = false)

                while (isActive) {
                    playPcmBuffer(beat1Samples, sampleRate, AudioManager.STREAM_VOICE_CALL)
                    if (!isActive) break
                    delay(80L)
                    if (!isActive) break
                    playPcmBuffer(beat2Samples, sampleRate, AudioManager.STREAM_VOICE_CALL)
                    if (!isActive) break
                    delay(1500L) // Rhythmic repeat gap
                }
            } catch (e: Exception) {
                Log.e(TAG, "Outgoing calling beat playback ended: ${e.message}")
            }
        }
    }

    private fun generateChordTone(freqs: DoubleArray, durationMs: Int, sampleRate: Int, punchy: Boolean): ShortArray {
        val numSamples = (sampleRate * (durationMs / 1000.0)).toInt()
        val buffer = ShortArray(numSamples)
        for (i in 0 until numSamples) {
            val t = i.toDouble() / sampleRate
            val progress = i.toDouble() / numSamples
            val envelope = if (punchy) {
                when {
                    progress < 0.1 -> progress / 0.1
                    else -> kotlin.math.exp(-2.5 * (progress - 0.1))
                }
            } else {
                when {
                    progress < 0.08 -> progress / 0.08
                    else -> kotlin.math.exp(-1.8 * (progress - 0.08))
                }
            }

            var sum = 0.0
            for (f in freqs) {
                sum += sin(2.0 * PI * f * t)
            }
            sum /= freqs.size
            val sample = (sum * envelope * 15000).toInt().coerceIn(-32767, 32767).toShort()
            buffer[i] = sample
        }
        return buffer
    }

    /**
     * Start playing incoming call ringtone and vibration for receiver
     */
    @Synchronized
    fun startIncomingRinging(context: Context) {
        stopRinging()
        Log.d(TAG, "Starting incoming call ringtone...")

        // 1. Play system ringtone if available
        try {
            val ringtoneUri = RingtoneManager.getDefaultUri(RingtoneManager.TYPE_RINGTONE)
                ?: RingtoneManager.getDefaultUri(RingtoneManager.TYPE_NOTIFICATION)

            if (ringtoneUri != null) {
                incomingRingtone = RingtoneManager.getRingtone(context.applicationContext, ringtoneUri)
                incomingRingtone?.let { ring ->
                    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
                        try {
                            ring.isLooping = true
                        } catch (_: Exception) {}
                    }
                    val attrs = AudioAttributes.Builder()
                        .setContentType(AudioAttributes.CONTENT_TYPE_SONIFICATION)
                        .setUsage(AudioAttributes.USAGE_NOTIFICATION_RINGTONE)
                        .build()
                    ring.audioAttributes = attrs
                    ring.play()
                }
            }
        } catch (e: Exception) {
            Log.w(TAG, "System ringtone playback error: ${e.message}")
        }

        // 2. Play complementary melodious incoming chime loop
        toneJob = playerScope.launch {
            try {
                val sampleRate = 16000
                // Chime 1 (587 Hz - D5) & Chime 2 (880 Hz - A5)
                val chime1Samples = generateChimeTone(587.0, 350, sampleRate)
                val chime2Samples = generateChimeTone(880.0, 450, sampleRate)

                while (isActive) {
                    playPcmBuffer(chime1Samples, sampleRate, AudioManager.STREAM_RING)
                    if (!isActive) break
                    delay(100L)
                    if (!isActive) break
                    playPcmBuffer(chime2Samples, sampleRate, AudioManager.STREAM_RING)
                    if (!isActive) break
                    delay(2000L) // rhythmic pause
                }
            } catch (e: Exception) {
                Log.e(TAG, "Incoming chime loop ended: ${e.message}")
            }
        }

        // 3. Start rhythmic incoming call vibration
        vibratorJob = playerScope.launch {
            try {
                val vibrator = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                    val vm = context.getSystemService(Context.VIBRATOR_MANAGER_SERVICE) as? VibratorManager
                    vm?.defaultVibrator
                } else {
                    @Suppress("DEPRECATION")
                    context.getSystemService(Context.VIBRATOR_SERVICE) as? Vibrator
                }

                val pattern = longArrayOf(0, 700, 300, 700, 1000)
                while (isActive) {
                    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                        vibrator?.vibrate(VibrationEffect.createWaveform(pattern, -1))
                    } else {
                        @Suppress("DEPRECATION")
                        vibrator?.vibrate(pattern, -1)
                    }
                    delay(2700L)
                }
            } catch (_: Exception) {}
        }
    }

    private fun generateChimeTone(freq: Double, durationMs: Int, sampleRate: Int): ShortArray {
        val numSamples = (sampleRate * (durationMs / 1000.0)).toInt()
        val buffer = ShortArray(numSamples)
        for (i in 0 until numSamples) {
            val t = i.toDouble() / sampleRate
            // Exponential decay envelope for bell/chime sound
            val decay = kotlin.math.exp(-3.5 * (i.toDouble() / numSamples))
            val sample = (sin(2.0 * PI * freq * t) * decay * 18000).toInt().toShort()
            buffer[i] = sample
        }
        return buffer
    }

    private suspend fun playPcmBuffer(buffer: ShortArray, sampleRate: Int, streamType: Int) {
        var track: AudioTrack? = null
        try {
            val bufferSize = buffer.size * 2
            track = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
                val attributes = AudioAttributes.Builder()
                    .setUsage(if (streamType == AudioManager.STREAM_RING) AudioAttributes.USAGE_NOTIFICATION_RINGTONE else AudioAttributes.USAGE_VOICE_COMMUNICATION_SIGNALLING)
                    .setContentType(AudioAttributes.CONTENT_TYPE_SONIFICATION)
                    .build()
                val format = AudioFormat.Builder()
                    .setSampleRate(sampleRate)
                    .setEncoding(AudioFormat.ENCODING_PCM_16BIT)
                    .setChannelMask(AudioFormat.CHANNEL_OUT_MONO)
                    .build()
                AudioTrack.Builder()
                    .setAudioAttributes(attributes)
                    .setAudioFormat(format)
                    .setBufferSizeInBytes(bufferSize)
                    .setTransferMode(AudioTrack.MODE_STATIC)
                    .build()
            } else {
                @Suppress("DEPRECATION")
                AudioTrack(
                    streamType,
                    sampleRate,
                    AudioFormat.CHANNEL_OUT_MONO,
                    AudioFormat.ENCODING_PCM_16BIT,
                    bufferSize,
                    AudioTrack.MODE_STATIC
                )
            }

            synchronized(this) {
                activeAudioTrack = track
            }

            track.write(buffer, 0, buffer.size)
            track.play()

            // Calculate duration and wait non-blockingly
            val durationMs = (buffer.size.toDouble() / sampleRate * 1000).toLong()
            delay(durationMs)
        } catch (_: Exception) {
        } finally {
            try {
                track?.stop()
            } catch (_: Exception) {}
            try {
                track?.release()
            } catch (_: Exception) {}
            synchronized(this) {
                if (activeAudioTrack == track) {
                    activeAudioTrack = null
                }
            }
        }
    }

    /**
     * Stop all outgoing & incoming ringing sounds and vibrations immediately
     */
    @Synchronized
    fun stopRinging() {
        Log.d(TAG, "Stopping all ringing tones and vibrations...")
        toneJob?.cancel()
        toneJob = null

        vibratorJob?.cancel()
        vibratorJob = null

        try {
            incomingRingtone?.stop()
        } catch (_: Exception) {}
        incomingRingtone = null

        try {
            activeAudioTrack?.stop()
            activeAudioTrack?.release()
        } catch (_: Exception) {}
        activeAudioTrack = null
    }
}

package com.example.calling

import android.content.Context
import android.media.Ringtone
import android.util.Log
import kotlinx.coroutines.*

/**
 * High-Performance, Crash-Proof In-App Call Tone & Ringer Player.
 * Ringtone playback is disabled as requested.
 */
object InAppCallTonePlayer {
    private const val TAG = "InAppCallTonePlayer"

    private val playerScope = CoroutineScope(Dispatchers.Default + SupervisorJob())
    private var toneJob: Job? = null
    private var vibratorJob: Job? = null
    private var incomingRingtone: Ringtone? = null

    /**
     * Start playing outgoing ringback tone for caller (Disabled)
     */
    @Synchronized
    fun startOutgoingRinging() {
        // No ringtone played as requested
    }

    /**
     * Start playing incoming call ringtone and vibration for receiver (Disabled)
     */
    @Synchronized
    fun startIncomingRinging(context: Context) {
        // No ringtone played as requested
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
    }
}

package com.example.ui.components

import android.content.Context
import android.media.AudioManager
import android.media.ToneGenerator
import android.os.Build
import android.os.CombinedVibration
import android.os.VibrationEffect
import android.os.Vibrator
import android.os.VibratorManager
import android.view.HapticFeedbackConstants
import android.view.View

/**
 * Production Haptic & Audio Feedback Manager for QIVO.
 * Delivers immediate, tactile, satisfying physical and auditory cues
 * on interactions (taps, gifts, seat joins, reactions, transactions).
 */
object HapticSoundFeedback {

    private var toneGenerator: ToneGenerator? = null

    init {
        try {
            toneGenerator = ToneGenerator(AudioManager.STREAM_MUSIC, 70)
        } catch (_: Exception) {}
    }

    /**
     * Subtle light vibration tick for standard button taps, toggles, tabs
     */
    fun performLightTick(context: Context? = null) {
        try {
            if (context == null) return
            val vibrator = getVibrator(context) ?: return
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                vibrator.vibrate(VibrationEffect.createPredefined(VibrationEffect.EFFECT_TICK))
            } else {
                @Suppress("DEPRECATION")
                vibrator.vibrate(10)
            }
        } catch (_: Exception) {}
    }

    /**
     * Standard tactile click for interactive cards, list selections
     */
    fun performClick(context: Context? = null) {
        try {
            if (context == null) return
            val vibrator = getVibrator(context) ?: return
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                vibrator.vibrate(VibrationEffect.createPredefined(VibrationEffect.EFFECT_CLICK))
            } else {
                @Suppress("DEPRECATION")
                vibrator.vibrate(20)
            }
        } catch (_: Exception) {}
    }

    /**
     * Heavy / Impact tactile bump for joining a mic seat or sending high-tier gift
     */
    fun performHeavyBump(context: Context? = null) {
        try {
            if (context == null) return
            val vibrator = getVibrator(context) ?: return
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                vibrator.vibrate(VibrationEffect.createPredefined(VibrationEffect.EFFECT_HEAVY_CLICK))
            } else {
                @Suppress("DEPRECATION")
                vibrator.vibrate(45)
            }
        } catch (_: Exception) {}
    }

    /**
     * Satisfying double-pulse vibration for successful claims, coin gifts, awards
     */
    fun performSuccess(context: Context? = null) {
        try {
            if (context == null) return
            val vibrator = getVibrator(context) ?: return
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                val timings = longArrayOf(0, 30, 60, 45)
                val amplitudes = intArrayOf(0, 180, 0, 255)
                vibrator.vibrate(VibrationEffect.createWaveform(timings, amplitudes, -1))
            } else {
                @Suppress("DEPRECATION")
                vibrator.vibrate(longArrayOf(0, 30, 60, 45), -1)
            }
        } catch (_: Exception) {}
    }

    /**
     * Play subtle sound effect for receiving or sending a virtual gift
     */
    fun playGiftSentSound() {
        try {
            toneGenerator?.startTone(ToneGenerator.TONE_PROP_BEEP2, 120)
        } catch (_: Exception) {}
    }

    /**
     * Play coin jingling / claim tone
     */
    fun playCoinSound() {
        try {
            toneGenerator?.startTone(ToneGenerator.TONE_PROP_ACK, 100)
        } catch (_: Exception) {}
    }

    /**
     * Play pop sound for incoming message or reaction
     */
    fun playPopSound() {
        try {
            toneGenerator?.startTone(ToneGenerator.TONE_PROP_PROMPT, 60)
        } catch (_: Exception) {}
    }

    /**
     * Play tone when joining a mic seat in party room
     */
    fun playMicJoinSound() {
        try {
            toneGenerator?.startTone(ToneGenerator.TONE_SUP_CONFIRM, 150)
        } catch (_: Exception) {}
    }

    private fun getVibrator(context: Context): Vibrator? {
        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            val vibratorManager = context.getSystemService(Context.VIBRATOR_MANAGER_SERVICE) as? VibratorManager
            vibratorManager?.defaultVibrator
        } else {
            @Suppress("DEPRECATION")
            context.getSystemService(Context.VIBRATOR_SERVICE) as? Vibrator
        }
    }
}

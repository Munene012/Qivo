package com.example.calling

/**
 * ZegoCloud Real-Time Voice & Video Calling Architecture & Privacy Policy Guidelines
 *
 * 1. REAL-TIME MEDIA & DATA FLOW:
 *    - Voice and video streams are transmitted end-to-end encrypted using SRTP / WebRTC standards via ZegoCloud RTC edge servers.
 *    - Media streams are peer-routed in real-time and never persistently stored on disk.
 *
 * 2. AUTHENTICATION & ACCESS CONTROL:
 *    - All calls require a signed Token04 generated via AES-128-CBC + HMAC-SHA256.
 *    - Only verified users possessing a valid authenticated session can publish or subscribe to a room.
 *
 * 3. CAMERA & MICROPHONE PRIVACY POLICY:
 *    - Microphone (android.permission.RECORD_AUDIO) is activated exclusively during an active call session.
 *    - Camera (android.permission.CAMERA) is activated exclusively during active video calling or live selfie preview.
 *    - Users can toggle camera and mute microphone at any moment using on-screen controls.
 *
 * 4. COIN BILLING POLICY:
 *    - Per-minute call billing is strictly checked prior to call initiation.
 *    - If coins are insufficient, calls are rejected immediately before establishing connection.
 *    - Recurring minute deductions occur every 60 seconds of connected conversation.
 */
object ZegoCloudPolicy {
    const val POLICY_VERSION = "2.4.0"
    const val ENCRYPTION_STANDARD = "AES-128-CBC / TLS 1.3 / SRTP"
    const val VOICE_RATE_PER_MIN = 80L
    const val VIDEO_RATE_PER_MIN = 160L

    fun getPolicySummary(): String {
        return "QIVO Voice & Video Calling is secured by ZegoCloud WebRTC with End-to-End Encryption. " +
                "Camera and Microphone are only engaged while calls are in progress."
    }
}

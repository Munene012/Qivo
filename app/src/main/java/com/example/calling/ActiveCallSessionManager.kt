package com.example.calling
import com.example.ui.components.AppToast

import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.provider.Settings
import android.widget.Toast
import com.example.data.PartyRoomSessionManager
import com.example.data.SupabaseProfileService
import com.example.data.UserProfile
import com.example.data.UserSessionManager
import com.example.ui.screens.CallType
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import java.util.UUID

enum class CallRole {
    CALLER,
    RECEIVER
}

enum class ActiveCallStatus {
    IDLE,
    OUTGOING_RINGING,
    INCOMING_RINGING,
    CONNECTED,
    ENDED
}

data class ActiveCallSession(
    val callId: String,
    val callType: CallType,
    val role: CallRole,
    val otherUser: UserProfile,
    val myUser: UserProfile,
    val status: ActiveCallStatus,
    val durationSeconds: Int = 0,
    val timeoutRemainingSeconds: Int = 40,
    val isMuted: Boolean = false,
    val isSpeakerOn: Boolean = false,
    val isCameraEnabled: Boolean = true,
    val isFrontCamera: Boolean = true,
    val endReason: String = "",
    val totalCoinsDeducted: Long = 0L
)

/**
 * Production Central Calling Engine.
 * Manages real-time call lifecycle, ringtones, 40s timeout, party room mutual exclusion,
 * coin billing, minimization to floating bubble, and audio/video controls.
 */
object ActiveCallSessionManager {
    private const val TAG = "ActiveCallSessionMgr"

    private val _currentSession = MutableStateFlow<ActiveCallSession?>(null)
    val currentSession: StateFlow<ActiveCallSession?> = _currentSession.asStateFlow()

    private val _isMinimized = MutableStateFlow(false)
    val isMinimized: StateFlow<Boolean> = _isMinimized.asStateFlow()

    private val scope = CoroutineScope(Dispatchers.Main + SupervisorJob())
    private var timerJob: Job? = null
    private var timeoutJob: Job? = null
    private var billingJob: Job? = null
    private val profileService = SupabaseProfileService()

    fun init(context: Context, currentUserId: String) {
        CallRealtimeRelayManager.connectUserSignaling(currentUserId)
        observeRealtimeSignals(context)
    }

    private fun observeRealtimeSignals(context: Context) {
        scope.launch {
            CallRealtimeRelayManager.incomingSignals.collect { signal ->
                handleIncomingSignal(signal, context)
            }
        }
    }

    private fun handleIncomingSignal(signal: RealtimeCallSignal, context: Context) {
        val session = _currentSession.value
        val myUserId = UserSessionManager.getSession(context)?.userId ?: ""

        // Ignore any signal originating from ourself
        if (signal.callerId.isNotBlank() && signal.callerId == myUserId) {
            return
        }

        when (signal.type) {
            "OFFER" -> {
                // If it's sent to me and I am not already in a call
                if (signal.receiverId == myUserId && signal.callerId != myUserId) {
                    // Check if currently inside a Party Room -> User cannot receive calls in Party Room
                    if (PartyRoomSessionManager.activeRoom.value != null) {
                        // Automatically reject call with BUSY_PARTY_ROOM
                        CallRealtimeRelayManager.sendSignal(
                            RealtimeCallSignal(
                                type = "BUSY_PARTY_ROOM",
                                callId = signal.callId,
                                callerId = myUserId,
                                callerName = "User",
                                callerAvatar = "",
                                callerGender = "Male",
                                receiverId = signal.callerId,
                                callType = signal.callType,
                                reason = "User is currently in a Party Room"
                            ),
                            context = context
                        )
                        return
                    }

                    if (session != null && session.status != ActiveCallStatus.IDLE && session.status != ActiveCallStatus.ENDED) {
                        // Busy in another call
                        CallRealtimeRelayManager.sendSignal(
                            RealtimeCallSignal(
                                type = "BUSY_PARTY_ROOM",
                                callId = signal.callId,
                                callerId = myUserId,
                                callerName = "User",
                                callerAvatar = "",
                                callerGender = "Male",
                                receiverId = signal.callerId,
                                callType = signal.callType,
                                reason = "User is busy on another call"
                            ),
                            context = context
                        )
                        return
                    }

                    val incomingCallType = if (signal.callType.equals("VIDEO", ignoreCase = true)) CallType.VIDEO else CallType.VOICE
                    val callerUser = UserProfile(
                        id = signal.callerId,
                        numericId = 0L,
                        email = "",
                        name = signal.callerName,
                        gender = signal.callerGender,
                        birthDate = "2000-01-01",
                        country = "United States",
                        avatarUrl = signal.callerAvatar
                    )

                    val myUser = UserSessionManager.getSession(context)?.let {
                        UserProfile(
                            id = it.userId,
                            numericId = it.numericId,
                            email = it.email,
                            name = it.name,
                            gender = it.gender,
                            birthDate = it.birthDate,
                            country = it.country,
                            avatarUrl = it.avatarUrl,
                            coins = it.coins
                        )
                    } ?: UserProfile(id = myUserId, numericId = 0L, email = "", name = "User", gender = "Male", birthDate = "2000-01-01", country = "United States", avatarUrl = "")

                    _currentSession.value = ActiveCallSession(
                        callId = signal.callId,
                        callType = incomingCallType,
                        role = CallRole.RECEIVER,
                        otherUser = callerUser,
                        myUser = myUser,
                        status = ActiveCallStatus.INCOMING_RINGING,
                        isSpeakerOn = (incomingCallType == CallType.VIDEO),
                        timeoutRemainingSeconds = 40
                    )
                    _isMinimized.value = false

                    // Play incoming ringtone
                    InAppCallTonePlayer.startIncomingRinging(context)

                    // Start 40-second timeout
                    startTimeoutCountdown(context, isCaller = false, remoteUserName = signal.callerName)
                }
            }
            "ACCEPT" -> {
                if (session != null && session.callId == signal.callId && session.role == CallRole.CALLER && signal.callerId == session.otherUser.id) {
                    // Receiver accepted! Transition to CONNECTED
                    timeoutJob?.cancel()
                    InAppCallTonePlayer.stopRinging()
                    _currentSession.value = session.copy(status = ActiveCallStatus.CONNECTED)
                    startCallConnected(context)
                }
            }
            "DECLINE" -> {
                if (session != null && session.callId == signal.callId && signal.callerId == session.otherUser.id) {
                    timeoutJob?.cancel()
                    InAppCallTonePlayer.stopRinging()
                    val reason = "Call declined by ${session.otherUser.name}"
                    _currentSession.value = session.copy(status = ActiveCallStatus.ENDED, endReason = reason)
                    AppToast.show(reason)
                    scope.launch {
                        delay(1200)
                        clearSession(context)
                    }
                }
            }
            "BUSY_PARTY_ROOM" -> {
                if (session != null && session.callId == signal.callId && signal.callerId == session.otherUser.id) {
                    timeoutJob?.cancel()
                    InAppCallTonePlayer.stopRinging()
                    val reason = signal.reason.ifBlank { "${session.otherUser.name} is currently in a Party Room" }
                    _currentSession.value = session.copy(status = ActiveCallStatus.ENDED, endReason = reason)
                    AppToast.show(reason, isLong = true)
                    scope.launch {
                        delay(1500)
                        clearSession(context)
                    }
                }
            }
            "TIMEOUT" -> {
                if (session != null && session.callId == signal.callId && signal.callerId == session.otherUser.id) {
                    timeoutJob?.cancel()
                    InAppCallTonePlayer.stopRinging()
                    val reason = if (session.role == CallRole.CALLER) "No answer from ${session.otherUser.name}" else "Missed call"
                    _currentSession.value = session.copy(status = ActiveCallStatus.ENDED, endReason = reason)
                    AppToast.show(reason)
                    scope.launch {
                        delay(1000)
                        clearSession(context)
                    }
                }
            }
            "END" -> {
                if (session != null && session.callId == signal.callId && signal.callerId == session.otherUser.id) {
                    timeoutJob?.cancel()
                    InAppCallTonePlayer.stopRinging()
                    val reason = "Call ended"
                    _currentSession.value = session.copy(status = ActiveCallStatus.ENDED, endReason = reason)
                    AppToast.show(reason)
                    scope.launch {
                        delay(800)
                        clearSession(context)
                    }
                }
            }
        }
    }

    /**
     * Start a call to target user
     */
    fun startCall(
        caller: UserProfile,
        receiver: UserProfile,
        callType: CallType,
        context: Context,
        onInsufficientCoins: () -> Unit = {}
    ) {
        // 1. PARTY ROOM CONSTRAINT: User cannot send calls while in a Party Room
        if (PartyRoomSessionManager.activeRoom.value != null) {
            AppToast.show("Cannot make calls while in a Party Room", isLong = true)
            return
        }

        val isCallerMale = caller.gender.equals("Male", ignoreCase = true)
        val rate = if (callType == CallType.VIDEO) 160L else 80L

        // 2. Male user minimum coin balance verification
        val userCoins = caller.coins
        if (isCallerMale && userCoins < rate) {
            AppToast.show("Insufficient coins. $rate coins required to start a ${callType.name.lowercase()} call.", isLong = true)
            onInsufficientCoins()
            return
        }

        val callId = "call_${UUID.randomUUID()}"
        _currentSession.value = ActiveCallSession(
            callId = callId,
            callType = callType,
            role = CallRole.CALLER,
            otherUser = receiver,
            myUser = caller,
            status = ActiveCallStatus.OUTGOING_RINGING,
            isSpeakerOn = (callType == CallType.VIDEO),
            timeoutRemainingSeconds = 40
        )
        _isMinimized.value = false

        // Start outgoing ringtone
        InAppCallTonePlayer.startOutgoingRinging()

        // Send OFFER signal
        CallRealtimeRelayManager.sendSignal(
            RealtimeCallSignal(
                type = "OFFER",
                callId = callId,
                callerId = caller.id,
                callerName = caller.name,
                callerAvatar = caller.avatarUrl,
                callerGender = caller.gender,
                receiverId = receiver.id,
                callType = callType.name
            ),
            context = context
        )

        // Start 40-second timeout countdown
        startTimeoutCountdown(context, isCaller = true, remoteUserName = receiver.name)
    }

    private fun startTimeoutCountdown(context: Context, isCaller: Boolean, remoteUserName: String) {
        timeoutJob?.cancel()
        timeoutJob = scope.launch {
            for (sec in 40 downTo 1) {
                val current = _currentSession.value ?: break
                if (current.status != ActiveCallStatus.OUTGOING_RINGING && current.status != ActiveCallStatus.INCOMING_RINGING) break
                _currentSession.value = current.copy(timeoutRemainingSeconds = sec)
                delay(1000)
            }

            val cur = _currentSession.value
            if (cur != null && (cur.status == ActiveCallStatus.OUTGOING_RINGING || cur.status == ActiveCallStatus.INCOMING_RINGING)) {
                // 40 seconds elapsed without answer -> End automatically on both sides!
                InAppCallTonePlayer.stopRinging()
                val reason = if (isCaller) "No answer from $remoteUserName (40s timeout)" else "Missed call (40s timeout)"
                _currentSession.value = cur.copy(status = ActiveCallStatus.ENDED, endReason = reason)
                AppToast.show(reason, isLong = true)

                // Notify remote party
                CallRealtimeRelayManager.sendSignal(
                    RealtimeCallSignal(
                        type = "TIMEOUT",
                        callId = cur.callId,
                        callerId = cur.myUser.id,
                        callerName = cur.myUser.name,
                        callerAvatar = cur.myUser.avatarUrl,
                        callerGender = cur.myUser.gender,
                        receiverId = cur.otherUser.id,
                        callType = cur.callType.name,
                        reason = reason
                    ),
                    context = context
                )

                delay(1500)
                clearSession(context)
            }
        }
    }

    /**
     * Accept incoming call
     */
    fun acceptCall(context: Context) {
        val session = _currentSession.value ?: return
        if (session.status != ActiveCallStatus.INCOMING_RINGING) return

        timeoutJob?.cancel()
        InAppCallTonePlayer.stopRinging()

        _currentSession.value = session.copy(status = ActiveCallStatus.CONNECTED)

        // Send ACCEPT signal to caller
        CallRealtimeRelayManager.sendSignal(
            RealtimeCallSignal(
                type = "ACCEPT",
                callId = session.callId,
                callerId = session.myUser.id,
                callerName = session.myUser.name,
                callerAvatar = session.myUser.avatarUrl,
                callerGender = session.myUser.gender,
                receiverId = session.otherUser.id,
                callType = session.callType.name
            ),
            context = context
        )

        startCallConnected(context)
    }

    /**
     * Decline incoming call
     */
    fun declineCall(context: Context) {
        val session = _currentSession.value ?: return
        timeoutJob?.cancel()
        InAppCallTonePlayer.stopRinging()

        _currentSession.value = session.copy(status = ActiveCallStatus.ENDED, endReason = "Call declined")

        // Send DECLINE signal to caller
        CallRealtimeRelayManager.sendSignal(
            RealtimeCallSignal(
                type = "DECLINE",
                callId = session.callId,
                callerId = session.myUser.id,
                callerName = session.myUser.name,
                callerAvatar = session.myUser.avatarUrl,
                callerGender = session.myUser.gender,
                receiverId = session.otherUser.id,
                callType = session.callType.name
            ),
            context = context
        )

        scope.launch {
            delay(1000)
            clearSession(context)
        }
    }

    /**
     * End active or outgoing call
     */
    fun endCall(context: Context) {
        val session = _currentSession.value ?: return
        timeoutJob?.cancel()
        timerJob?.cancel()
        billingJob?.cancel()
        InAppCallTonePlayer.stopRinging()

        _currentSession.value = session.copy(status = ActiveCallStatus.ENDED, endReason = "Call ended")

        // Send END signal to remote party
        CallRealtimeRelayManager.sendSignal(
            RealtimeCallSignal(
                type = "END",
                callId = session.callId,
                callerId = session.myUser.id,
                callerName = session.myUser.name,
                callerAvatar = session.myUser.avatarUrl,
                callerGender = session.myUser.gender,
                receiverId = session.otherUser.id,
                callType = session.callType.name
            ),
            context = context
        )

        scope.launch {
            delay(800)
            clearSession(context)
        }
    }

    private fun startCallConnected(context: Context) {
        val session = _currentSession.value ?: return
        val zegoManager = ZegoCallManager.getInstance(context)
        zegoManager.initEngine()
        zegoManager.startCall(
            myUserId = session.myUser.id,
            myUserName = session.myUser.name,
            remoteUserId = session.otherUser.id,
            remoteUserName = session.otherUser.name,
            remoteAvatarUrl = session.otherUser.avatarUrl,
            callType = session.callType.name
        )
        zegoManager.acceptCall()

        // First minute deduction if male user
        val isMale = session.myUser.gender.equals("Male", ignoreCase = true)
        val rate = if (session.callType == CallType.VIDEO) 160L else 80L
        if (isMale) {
            scope.launch {
                val deductRes = profileService.deductCallMinute(
                    callerId = session.myUser.id,
                    callerGender = session.myUser.gender,
                    calleeId = session.otherUser.id,
                    calleeGender = session.otherUser.gender,
                    isVideo = (session.callType == CallType.VIDEO),
                    callerName = session.myUser.name,
                    calleeName = session.otherUser.name
                )
                if (deductRes.first) {
                    val newCoins = deductRes.second
                    UserSessionManager.saveCoins(context, newCoins)
                    val cur = _currentSession.value
                    if (cur != null) {
                        _currentSession.value = cur.copy(
                            myUser = cur.myUser.copy(coins = newCoins),
                            totalCoinsDeducted = cur.totalCoinsDeducted + rate
                        )
                    }
                }
            }
        }

        // Start call duration timer & per-minute recurring deductions
        timerJob?.cancel()
        timerJob = scope.launch {
            while (isActive) {
                delay(1000)
                val cur = _currentSession.value ?: break
                if (cur.status != ActiveCallStatus.CONNECTED) break
                val newDuration = cur.durationSeconds + 1
                zegoManager.tickDuration()

                // Every 60 seconds recurring minute deduction
                if (newDuration > 0 && newDuration % 60 == 0 && isMale) {
                    if (cur.myUser.coins < rate) {
                        AppToast.show("Call ended: Insufficient coins for next minute.", isLong = true)
                        endCall(context)
                        break
                    }
                    val deductRes = profileService.deductCallMinute(
                        callerId = cur.myUser.id,
                        callerGender = cur.myUser.gender,
                        calleeId = cur.otherUser.id,
                        calleeGender = cur.otherUser.gender,
                        isVideo = (cur.callType == CallType.VIDEO),
                        callerName = cur.myUser.name,
                        calleeName = cur.otherUser.name
                    )
                    if (deductRes.first) {
                        val updatedCoins = deductRes.second
                        UserSessionManager.saveCoins(context, updatedCoins)
                        _currentSession.value = cur.copy(
                            durationSeconds = newDuration,
                            myUser = cur.myUser.copy(coins = updatedCoins),
                            totalCoinsDeducted = cur.totalCoinsDeducted + rate
                        )
                    } else {
                        AppToast.show("Call ended: Coin deduction failed.")
                        endCall(context)
                        break
                    }
                } else {
                    _currentSession.value = cur.copy(durationSeconds = newDuration)
                }
            }
        }
    }

    fun minimizeCall(context: Context? = null) {
        _isMinimized.value = true
        // Check display over other apps permission
        if (context != null && Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            if (!Settings.canDrawOverlays(context)) {
                try {
                    val intent = Intent(
                        Settings.ACTION_MANAGE_OVERLAY_PERMISSION,
                        Uri.parse("package:${context.packageName}")
                    ).apply {
                        flags = Intent.FLAG_ACTIVITY_NEW_TASK
                    }
                    context.startActivity(intent)
                } catch (_: Exception) {}
            }
        }
    }

    fun expandCall() {
        _isMinimized.value = false
    }

    fun toggleMute(context: Context) {
        val session = _currentSession.value ?: return
        val newMuted = !session.isMuted
        _currentSession.value = session.copy(isMuted = newMuted)
        ZegoCallManager.getInstance(context).toggleMicrophone()
    }

    fun toggleSpeaker(context: Context) {
        val session = _currentSession.value ?: return
        val newSpeaker = !session.isSpeakerOn
        _currentSession.value = session.copy(isSpeakerOn = newSpeaker)
        ZegoCallManager.getInstance(context).toggleSpeaker()
    }

    fun toggleCamera(context: Context) {
        val session = _currentSession.value ?: return
        val newCam = !session.isCameraEnabled
        _currentSession.value = session.copy(isCameraEnabled = newCam)
        ZegoCallManager.getInstance(context).toggleCamera()
    }

    fun flipCamera(context: Context) {
        val session = _currentSession.value ?: return
        val newFront = !session.isFrontCamera
        _currentSession.value = session.copy(isFrontCamera = newFront)
        ZegoCallManager.getInstance(context).switchCamera()
    }

    private fun clearSession(context: Context) {
        InAppCallTonePlayer.stopRinging()
        timerJob?.cancel()
        timeoutJob?.cancel()
        billingJob?.cancel()
        _currentSession.value = null
        _isMinimized.value = false
        ZegoCallManager.getInstance(context).endCall()
    }
}

package com.example.data

import android.content.Context
import androidx.compose.ui.geometry.Offset
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

/**
 * Global Session Manager for Active Party Room.
 * Maintains real-time voice audio, seat state, and draggable floating mini PIP bubble
 * across the entire application when minimized ("Stay in Room").
 */
object PartyRoomSessionManager {

    private val _activeRoom = MutableStateFlow<PartyRoom?>(null)
    val activeRoom = _activeRoom.asStateFlow()

    private val _isMinimized = MutableStateFlow(false)
    val isMinimized = _isMinimized.asStateFlow()

    private val _isSeated = MutableStateFlow(false)
    val isSeated = _isSeated.asStateFlow()

    private val _currentSeatIndex = MutableStateFlow(-1)
    val currentSeatIndex = _currentSeatIndex.asStateFlow()

    private val _isMicMuted = MutableStateFlow(false)
    val isMicMuted = _isMicMuted.asStateFlow()

    private val _speakingVolume = MutableStateFlow(0f)
    val speakingVolume = _speakingVolume.asStateFlow()

    private val _isAnyoneSpeaking = MutableStateFlow(false)
    val isAnyoneSpeaking = _isAnyoneSpeaking.asStateFlow()

    private val _bubbleOffset = MutableStateFlow(Offset(20f, 300f))
    val bubbleOffset = _bubbleOffset.asStateFlow()

    var voiceEngine: TencentPartyVoiceEngine? = null
        private set

    fun initializeEngine(context: Context): TencentPartyVoiceEngine {
        if (voiceEngine == null) {
            voiceEngine = TencentPartyVoiceEngine(context.applicationContext).apply {
                onSpeakingVolumeChanged = { _, volume ->
                    _speakingVolume.value = volume
                    _isAnyoneSpeaking.value = volume > 0.05f
                }
            }
        }
        return voiceEngine!!
    }

    fun enterRoom(room: PartyRoom) {
        if (_activeRoom.value?.id != room.id) {
            _activeRoom.value = room
            _isMinimized.value = false
            _isSeated.value = false
            _currentSeatIndex.value = -1
            _isMicMuted.value = false
        } else {
            _activeRoom.value = room
        }
    }

    fun setSeated(seated: Boolean, index: Int = -1) {
        _isSeated.value = seated
        _currentSeatIndex.value = if (seated) index else -1
    }

    fun setMicMuted(muted: Boolean, userId: String = "", scope: CoroutineScope? = null) {
        _isMicMuted.value = muted
        voiceEngine?.setMicMute(userId, muted, scope)
    }

    fun minimizeRoom() {
        if (_activeRoom.value != null) {
            _isMinimized.value = true
        }
    }

    fun expandRoom() {
        _isMinimized.value = false
    }

    fun updateBubbleOffset(newOffset: Offset) {
        _bubbleOffset.value = newOffset
    }

    fun leaveRoom(userId: String, scope: CoroutineScope? = null) {
        val currentRoomId = _activeRoom.value?.id ?: ""
        if (currentRoomId.isNotBlank() && userId.isNotBlank()) {
            val targetScope = scope ?: CoroutineScope(Dispatchers.IO)
            targetScope.launch(Dispatchers.IO) {
                try {
                    val partyService = SupabasePartyService()
                    partyService.releaseSeat(currentRoomId, userId)
                    partyService.removeUserPresence(currentRoomId, userId)
                } catch (_: Exception) {}
            }
        }
        voiceEngine?.leaveMicSeat(userId)
        voiceEngine?.exitPartyRoom(userId)
        PartyMusicManager.stopMusic()
        PartyRealtimeRelayManager.disconnect()

        _activeRoom.value = null
        _isMinimized.value = false
        _isSeated.value = false
        _currentSeatIndex.value = -1
        _isMicMuted.value = false
        _speakingVolume.value = 0f
        _isAnyoneSpeaking.value = false
    }
}

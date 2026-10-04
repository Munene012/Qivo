package com.example.data

import android.content.Context
import android.util.Log
import androidx.compose.ui.geometry.Offset
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import org.json.JSONObject

/**
 * Global Session Manager for Active Party Room.
 * Maintains real-time voice audio, seat state, draggable floating mini PIP bubble,
 * and persistent session recovery across connection drops and accidental app terminations.
 */
object PartyRoomSessionManager {

    private const val TAG = "PartyRoomSessionMgr"
    private const val PREF_PARTY_SESSION = "qivo_party_room_session"
    private const val KEY_PERSISTED_ROOM_ID = "persisted_active_room_id"
    private const val KEY_PERSISTED_ROOM_NAME = "persisted_active_room_name"
    private const val KEY_PERSISTED_ROOM_JSON = "persisted_active_room_json"
    private const val KEY_PERSISTED_TIMESTAMP = "persisted_active_timestamp"

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

    var voiceEngine: ZegoPartyVoiceEngine? = null
        private set

    fun initializeEngine(context: Context): ZegoPartyVoiceEngine {
        if (voiceEngine == null) {
            voiceEngine = ZegoPartyVoiceEngine(context.applicationContext).apply {
                onSpeakingVolumeChanged = { _, volume ->
                    _speakingVolume.value = volume
                    _isAnyoneSpeaking.value = volume > 0.05f
                }
            }
        }
        return voiceEngine!!
    }

    /**
     * Persist active room session to disk (SharedPreferences)
     */
    fun saveActiveRoomSession(context: Context?, room: PartyRoom) {
        val targetContext = context?.applicationContext ?: UserSessionManager.appContext ?: return
        try {
            val prefs = targetContext.getSharedPreferences(PREF_PARTY_SESSION, Context.MODE_PRIVATE)
            val json = JSONObject().apply {
                put("id", room.id)
                put("room_number", room.roomNumber)
                put("name", room.name)
                put("description", room.description)
                put("category", room.category)
                put("cover_url", room.coverUrl)
                put("bg_url", room.bgUrl)
                put("host_id", room.hostUserId)
                put("host_name", room.hostName)
                put("host_avatar_url", room.hostAvatarUrl)
                put("seats_count", room.seatsCount)
                put("is_locked", room.isLocked)
                put("room_password", room.roomPassword)
            }.toString()

            prefs.edit()
                .putString(KEY_PERSISTED_ROOM_ID, room.id)
                .putString(KEY_PERSISTED_ROOM_NAME, room.name)
                .putString(KEY_PERSISTED_ROOM_JSON, json)
                .putLong(KEY_PERSISTED_TIMESTAMP, System.currentTimeMillis())
                .apply()
            Log.d(TAG, "Saved active party session for room: ${room.id} (${room.name})")
        } catch (e: Exception) {
            Log.w(TAG, "Failed to save room session: ${e.message}")
        }
    }

    /**
     * Clear active room session from disk when user explicitly leaves the room
     */
    fun clearActiveRoomSession(context: Context?) {
        val targetContext = context?.applicationContext ?: UserSessionManager.appContext
        try {
            if (targetContext != null) {
                val prefs = targetContext.getSharedPreferences(PREF_PARTY_SESSION, Context.MODE_PRIVATE)
                prefs.edit().clear().apply()
            }
            Log.d(TAG, "Cleared active party room session")
        } catch (e: Exception) {
            Log.w(TAG, "Failed to clear room session: ${e.message}")
        }
    }

    /**
     * Get persisted active room ID if the user was in a room and hasn't left
     */
    fun getPersistedRoomId(context: Context?): String? {
        val targetContext = context?.applicationContext ?: UserSessionManager.appContext ?: return null
        return try {
            val prefs = targetContext.getSharedPreferences(PREF_PARTY_SESSION, Context.MODE_PRIVATE)
            val id = prefs.getString(KEY_PERSISTED_ROOM_ID, "")?.trim()
            val timestamp = prefs.getLong(KEY_PERSISTED_TIMESTAMP, 0L)
            // Session remains valid for up to 12 hours after unexpected closure
            if (!id.isNullOrBlank() && (System.currentTimeMillis() - timestamp < 12 * 60 * 60 * 1000L)) {
                id
            } else {
                null
            }
        } catch (e: Exception) {
            null
        }
    }

    /**
     * Get persisted active room object if available
     */
    fun getPersistedRoom(context: Context?): PartyRoom? {
        val targetContext = context?.applicationContext ?: UserSessionManager.appContext ?: return null
        return try {
            val prefs = targetContext.getSharedPreferences(PREF_PARTY_SESSION, Context.MODE_PRIVATE)
            val rawJson = prefs.getString(KEY_PERSISTED_ROOM_JSON, "") ?: ""
            if (rawJson.isNotBlank()) {
                val obj = JSONObject(rawJson)
                val rawId = obj.optString("id")
                if (rawId.isNotBlank()) {
                    PartyRoom(
                        id = rawId,
                        roomNumber = obj.optLong("room_number", 0L),
                        name = obj.optString("name", "Party Lounge"),
                        description = obj.optString("description", ""),
                        category = obj.optString("category", "Chat"),
                        coverUrl = obj.optString("cover_url", ""),
                        bgUrl = obj.optString("bg_url", ""),
                        hostUserId = obj.optString("host_id", ""),
                        hostName = obj.optString("host_name", "Host"),
                        hostAvatarUrl = obj.optString("host_avatar_url", ""),
                        seatsCount = obj.optInt("seats_count", 8),
                        isLocked = obj.optBoolean("is_locked", false),
                        roomPassword = obj.optString("room_password", "")
                    )
                } else null
            } else null
        } catch (e: Exception) {
            null
        }
    }

    fun enterRoom(room: PartyRoom, context: Context? = null) {
        if (_activeRoom.value?.id != room.id) {
            _activeRoom.value = room
            _isMinimized.value = false
            _isSeated.value = false
            _currentSeatIndex.value = -1
            _isMicMuted.value = false
        } else {
            _activeRoom.value = room
        }
        saveActiveRoomSession(context, room)
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

    fun leaveRoom(userId: String, scope: CoroutineScope?) {
        leaveRoom(userId, UserSessionManager.appContext, scope)
    }

    fun leaveRoom(userId: String, context: Context? = null, scope: CoroutineScope? = null) {
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

        clearActiveRoomSession(context)

        _activeRoom.value = null
        _isMinimized.value = false
        _isSeated.value = false
        _currentSeatIndex.value = -1
        _isMicMuted.value = false
        _speakingVolume.value = 0f
        _isAnyoneSpeaking.value = false
    }
}

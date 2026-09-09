package com.example.data

import android.content.Context
import android.util.Base64
import android.util.Log
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.asSharedFlow
import okhttp3.*
import org.json.JSONObject
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.TimeUnit

/**
 * Real-time event payloads for Party Room audio, seat transitions, reactions, and music.
 */
data class RealtimeSpeakingEvent(
    val roomId: String,
    val userId: String,
    val seatIndex: Int,
    val isSpeaking: Boolean,
    val volume: Float
)

data class RealtimeAudioChunkEvent(
    val roomId: String,
    val userId: String,
    val seatIndex: Int,
    val pcmBytes: ByteArray
)

data class RealtimeSeatSwitchEvent(
    val roomId: String,
    val userId: String,
    val fromSeat: Int,
    val toSeat: Int,
    val userName: String,
    val avatarUrl: String
)

data class RealtimeReactionEvent(
    val roomId: String,
    val seatIndex: Int,
    val reactionType: String,
    val timestamp: Long
)

data class RealtimeMusicSyncEvent(
    val roomId: String,
    val title: String,
    val artist: String,
    val isPlaying: Boolean,
    val positionMs: Int
)

data class RealtimeRoomClosedEvent(
    val roomId: String
)

data class RealtimeUserLeaveEvent(
    val roomId: String,
    val userId: String
)

/**
 * High-performance WebSocket & Broadcast Relay Manager for Live Party Rooms.
 * Synchronizes:
 * 1. Live audio packets & speaking visual wave amplitude across all room listeners.
 * 2. Instantaneous seat changes & seat switches with zero-latency optimistic broadcasting.
 * 3. Animated seat emoji reactions broadcast to all room members in real-time.
 * 4. Party Room DJ Music player broadcast & synchronized playback.
 * 5. Room closed / deleted notification broadcast.
 * 6. Instant user leave and seat eviction synchronization.
 */
object PartyRealtimeRelayManager {

    private const val TAG = "PartyRealtimeRelay"

    private val httpClient = OkHttpClient.Builder()
        .connectTimeout(10, TimeUnit.SECONDS)
        .readTimeout(0, TimeUnit.MILLISECONDS) // Keep alive
        .pingInterval(20, TimeUnit.SECONDS)
        .build()

    private var activeWebSocket: WebSocket? = null
    private var currentRoomId: String = ""
    private var currentUserId: String = ""
    private var coroutineScope = CoroutineScope(Dispatchers.IO + SupervisorJob())

    // Shared flows for real-time broadcasts
    private val _speakingEvents = MutableSharedFlow<RealtimeSpeakingEvent>(extraBufferCapacity = 64)
    val speakingEvents: SharedFlow<RealtimeSpeakingEvent> = _speakingEvents.asSharedFlow()

    private val _audioChunkEvents = MutableSharedFlow<RealtimeAudioChunkEvent>(extraBufferCapacity = 256)
    val audioChunkEvents: SharedFlow<RealtimeAudioChunkEvent> = _audioChunkEvents.asSharedFlow()

    private val _seatSwitchEvents = MutableSharedFlow<RealtimeSeatSwitchEvent>(extraBufferCapacity = 32)
    val seatSwitchEvents: SharedFlow<RealtimeSeatSwitchEvent> = _seatSwitchEvents.asSharedFlow()

    private val _reactionEvents = MutableSharedFlow<RealtimeReactionEvent>(extraBufferCapacity = 32)
    val reactionEvents: SharedFlow<RealtimeReactionEvent> = _reactionEvents.asSharedFlow()

    private val _musicEvents = MutableSharedFlow<RealtimeMusicSyncEvent>(extraBufferCapacity = 32)
    val musicEvents: SharedFlow<RealtimeMusicSyncEvent> = _musicEvents.asSharedFlow()

    private val _roomClosedEvents = MutableSharedFlow<RealtimeRoomClosedEvent>(extraBufferCapacity = 16)
    val roomClosedEvents: SharedFlow<RealtimeRoomClosedEvent> = _roomClosedEvents.asSharedFlow()

    private val _userLeaveEvents = MutableSharedFlow<RealtimeUserLeaveEvent>(extraBufferCapacity = 32)
    val userLeaveEvents: SharedFlow<RealtimeUserLeaveEvent> = _userLeaveEvents.asSharedFlow()

    // Room In-Memory State Cache for Instant Sync across local instances / tests
    val roomSpeakingStates = ConcurrentHashMap<String, ConcurrentHashMap<Int, Boolean>>() // roomId -> (seatIndex -> isSpeaking)
    val roomSeatReactions = ConcurrentHashMap<String, ConcurrentHashMap<Int, Pair<String, Long>>>() // roomId -> (seatIndex -> (reactionType, ts))

    /**
     * Connect to Room's Realtime WebSocket Channel
     */
    fun connectRoom(roomId: String, userId: String) {
        if (currentRoomId == roomId && activeWebSocket != null) return

        disconnect()
        currentRoomId = roomId
        currentUserId = userId

        val baseUrl = SupabaseConfig.supabaseUrl.trim().removeSuffix("/")
        val apiKey = SupabaseConfig.supabaseAnonKey.trim()
        if (baseUrl.isBlank() || apiKey.isBlank()) {
            Log.w(TAG, "Supabase credentials empty, operating in local in-memory relay mode")
            return
        }

        val wsHost = baseUrl.replace("https://", "wss://").replace("http://", "ws://")
        val wsUrl = "$wsHost/realtime/v1/websocket?apikey=$apiKey&vsn=1.0.0"

        try {
            val request = Request.Builder()
                .url(wsUrl)
                .build()

            activeWebSocket = httpClient.newWebSocket(request, object : WebSocketListener() {
                override fun onOpen(webSocket: WebSocket, response: Response) {
                    Log.d(TAG, "Connected to Party Realtime WebSocket for Room $roomId")
                    // Join room broadcast channel
                    val joinTopic = "realtime:party_room_$roomId"
                    val joinMsg = JSONObject().apply {
                        put("topic", joinTopic)
                        put("event", "phx_join")
                        put("payload", JSONObject().apply {
                            put("config", JSONObject().apply {
                                put("broadcast", JSONObject().apply { put("ack", false); put("self", false) })
                                put("presence", JSONObject().apply { put("key", userId) })
                            })
                        })
                        put("ref", "1")
                    }.toString()
                    webSocket.send(joinMsg)
                }

                override fun onMessage(webSocket: WebSocket, text: String) {
                    handleIncomingSocketMessage(text)
                }

                override fun onFailure(webSocket: WebSocket, t: Throwable, response: Response?) {
                    Log.w(TAG, "WebSocket failure: ${t.message}")
                }

                override fun onClosed(webSocket: WebSocket, code: Int, reason: String) {
                    Log.d(TAG, "WebSocket closed: code=$code, reason=$reason")
                }
            })
        } catch (e: Exception) {
            Log.e(TAG, "Error connecting to realtime websocket", e)
        }
    }

    private fun handleIncomingSocketMessage(rawText: String) {
        try {
            val json = JSONObject(rawText)
            val event = json.optString("event", "")
            val payload = json.optJSONObject("payload") ?: return

            if (event == "broadcast") {
                val broadcastType = payload.optString("type", "")
                val data = payload.optJSONObject("data") ?: payload

                when (broadcastType) {
                    "speaking" -> {
                        val uid = data.optString("userId", "")
                        val sIdx = data.optInt("seatIndex", -1)
                        val speaking = data.optBoolean("isSpeaking", false)
                        val vol = data.optDouble("volume", 0.0).toFloat()
                        val rId = data.optString("roomId", currentRoomId)

                        if (sIdx != -1) {
                            roomSpeakingStates.getOrPut(rId) { ConcurrentHashMap() }[sIdx] = speaking
                            _speakingEvents.tryEmit(RealtimeSpeakingEvent(rId, uid, sIdx, speaking, vol))
                        }
                    }
                    "audio_chunk" -> {
                        val uid = data.optString("userId", "")
                        val sIdx = data.optInt("seatIndex", -1)
                        val pcmB64 = data.optString("pcm", "")
                        val rId = data.optString("roomId", currentRoomId)

                        if (pcmB64.isNotEmpty() && uid != currentUserId) {
                            try {
                                val bytes = Base64.decode(pcmB64, Base64.NO_WRAP)
                                _audioChunkEvents.tryEmit(RealtimeAudioChunkEvent(rId, uid, sIdx, bytes))
                            } catch (_: Exception) {}
                        }
                    }
                    "seat_switch" -> {
                        val uid = data.optString("userId", "")
                        val fSeat = data.optInt("fromSeat", -1)
                        val tSeat = data.optInt("toSeat", -1)
                        val uName = data.optString("userName", "User")
                        val aUrl = data.optString("avatarUrl", "")
                        val rId = data.optString("roomId", currentRoomId)

                        _seatSwitchEvents.tryEmit(RealtimeSeatSwitchEvent(rId, uid, fSeat, tSeat, uName, aUrl))
                    }
                    "reaction" -> {
                        val sIdx = data.optInt("seatIndex", -1)
                        val rxType = data.optString("reactionType", "")
                        val ts = data.optLong("timestamp", System.currentTimeMillis())
                        val rId = data.optString("roomId", currentRoomId)

                        if (sIdx != -1 && rxType.isNotBlank()) {
                            roomSeatReactions.getOrPut(rId) { ConcurrentHashMap() }[sIdx] = (rxType to ts)
                            _reactionEvents.tryEmit(RealtimeReactionEvent(rId, sIdx, rxType, ts))
                        }
                    }
                    "music_sync" -> {
                        val title = data.optString("title", "")
                        val artist = data.optString("artist", "")
                        val playing = data.optBoolean("isPlaying", false)
                        val pos = data.optInt("positionMs", 0)
                        val rId = data.optString("roomId", currentRoomId)

                        _musicEvents.tryEmit(RealtimeMusicSyncEvent(rId, title, artist, playing, pos))
                    }
                    "room_closed", "room_deleted" -> {
                        val rId = data.optString("roomId", currentRoomId)
                        _roomClosedEvents.tryEmit(RealtimeRoomClosedEvent(rId))
                    }
                    "user_leave" -> {
                        val rId = data.optString("roomId", currentRoomId)
                        val uid = data.optString("userId", "")
                        if (uid.isNotBlank()) {
                            _userLeaveEvents.tryEmit(RealtimeUserLeaveEvent(rId, uid))
                        }
                    }
                }
            }
        } catch (e: Exception) {
            Log.w(TAG, "Error handling incoming socket message: ${e.message}")
        }
    }

    private fun sendBroadcastPayload(type: String, data: JSONObject) {
        val rId = currentRoomId
        if (rId.isBlank()) return

        val msg = JSONObject().apply {
            put("topic", "realtime:party_room_$rId")
            put("event", "broadcast")
            put("payload", JSONObject().apply {
                put("type", type)
                put("data", data)
            })
            put("ref", System.currentTimeMillis().toString())
        }.toString()

        activeWebSocket?.send(msg)
    }

    /**
     * Broadcast user leave event to all listeners in the room
     */
    fun broadcastUserLeave(roomId: String, userId: String) {
        if (roomId.isBlank() || userId.isBlank()) return
        _userLeaveEvents.tryEmit(RealtimeUserLeaveEvent(roomId, userId))
        val data = JSONObject().apply {
            put("roomId", roomId)
            put("userId", userId)
        }
        sendBroadcastPayload("user_leave", data)
    }

    /**
     * Broadcast room closed event to all listeners in the room
     */
    fun broadcastRoomClosed(roomId: String) {
        _roomClosedEvents.tryEmit(RealtimeRoomClosedEvent(roomId))
        val data = JSONObject().apply {
            put("roomId", roomId)
        }
        sendBroadcastPayload("room_closed", data)
    }

    /**
     * Broadcast speaking state and audio volume level
     */
    fun broadcastSpeaking(roomId: String, userId: String, seatIndex: Int, isSpeaking: Boolean, volume: Float) {
        roomSpeakingStates.getOrPut(roomId) { ConcurrentHashMap() }[seatIndex] = isSpeaking
        _speakingEvents.tryEmit(RealtimeSpeakingEvent(roomId, userId, seatIndex, isSpeaking, volume))

        val data = JSONObject().apply {
            put("roomId", roomId)
            put("userId", userId)
            put("seatIndex", seatIndex)
            put("isSpeaking", isSpeaking)
            put("volume", volume.toDouble())
        }
        sendBroadcastPayload("speaking", data)
    }

    /**
     * Broadcast live audio chunk (PCM bytes) to all listeners in the party room
     */
    fun broadcastAudioChunk(roomId: String, userId: String, seatIndex: Int, pcmBytes: ByteArray) {
        // Also emit locally for multi-listener / audio monitor
        _audioChunkEvents.tryEmit(RealtimeAudioChunkEvent(roomId, userId, seatIndex, pcmBytes))

        try {
            val b64 = Base64.encodeToString(pcmBytes, Base64.NO_WRAP)
            val data = JSONObject().apply {
                put("roomId", roomId)
                put("userId", userId)
                put("seatIndex", seatIndex)
                put("pcm", b64)
            }
            sendBroadcastPayload("audio_chunk", data)
        } catch (_: Exception) {}
    }

    /**
     * Broadcast instantaneous seat change / switch
     */
    fun broadcastSeatSwitch(roomId: String, userId: String, fromSeat: Int, toSeat: Int, userName: String, avatarUrl: String) {
        _seatSwitchEvents.tryEmit(RealtimeSeatSwitchEvent(roomId, userId, fromSeat, toSeat, userName, avatarUrl))

        val data = JSONObject().apply {
            put("roomId", roomId)
            put("userId", userId)
            put("fromSeat", fromSeat)
            put("toSeat", toSeat)
            put("userName", userName)
            put("avatarUrl", avatarUrl)
        }
        sendBroadcastPayload("seat_switch", data)
    }

    /**
     * Broadcast animated emoji reaction on a seat
     */
    fun broadcastReaction(roomId: String, seatIndex: Int, reactionType: String) {
        val now = System.currentTimeMillis()
        roomSeatReactions.getOrPut(roomId) { ConcurrentHashMap() }[seatIndex] = (reactionType to now)
        _reactionEvents.tryEmit(RealtimeReactionEvent(roomId, seatIndex, reactionType, now))

        val data = JSONObject().apply {
            put("roomId", roomId)
            put("seatIndex", seatIndex)
            put("reactionType", reactionType)
            put("timestamp", now)
        }
        sendBroadcastPayload("reaction", data)
    }

    /**
     * Broadcast live DJ music playback state
     */
    fun broadcastMusic(roomId: String, title: String, artist: String, isPlaying: Boolean, positionMs: Int = 0) {
        _musicEvents.tryEmit(RealtimeMusicSyncEvent(roomId, title, artist, isPlaying, positionMs))

        val data = JSONObject().apply {
            put("roomId", roomId)
            put("title", title)
            put("artist", artist)
            put("isPlaying", isPlaying)
            put("positionMs", positionMs)
        }
        sendBroadcastPayload("music_sync", data)
    }

    /**
     * Disconnect and clear active party room connection
     */
    fun disconnect() {
        try {
            activeWebSocket?.close(1000, "Leaving party room")
        } catch (_: Exception) {}
        activeWebSocket = null
        currentRoomId = ""
        currentUserId = ""
    }
}

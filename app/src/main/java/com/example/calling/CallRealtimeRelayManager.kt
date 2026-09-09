package com.example.calling

import android.content.Context
import android.util.Log
import com.example.data.SupabaseConfig
import com.example.data.SupabaseFcmService
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.asSharedFlow
import okhttp3.*
import org.json.JSONObject
import java.util.concurrent.TimeUnit

/**
 * Call Event Data Structures
 */
data class RealtimeCallSignal(
    val type: String, // OFFER, ACCEPT, DECLINE, END, TIMEOUT, BUSY_PARTY_ROOM
    val callId: String,
    val callerId: String,
    val callerName: String,
    val callerAvatar: String,
    val callerGender: String,
    val receiverId: String,
    val callType: String, // VOICE or VIDEO
    val timestamp: Long = System.currentTimeMillis(),
    val reason: String = ""
)

/**
 * Production WebSocket & Realtime Signal Relay for Live 1-on-1 Calls.
 * Connects directly to Supabase Realtime channels for instant cross-device delivery.
 */
object CallRealtimeRelayManager {
    private const val TAG = "CallRealtimeRelay"

    private val httpClient = OkHttpClient.Builder()
        .connectTimeout(10, TimeUnit.SECONDS)
        .readTimeout(0, TimeUnit.MILLISECONDS)
        .pingInterval(20, TimeUnit.SECONDS)
        .build()

    private var activeWebSocket: WebSocket? = null
    private var connectedUserId: String = ""
    private val scope = CoroutineScope(Dispatchers.IO + SupervisorJob())

    private val _incomingSignals = MutableSharedFlow<RealtimeCallSignal>(extraBufferCapacity = 64)
    val incomingSignals: SharedFlow<RealtimeCallSignal> = _incomingSignals.asSharedFlow()

    /**
     * Connect user's personal incoming call signaling channel
     */
    fun connectUserSignaling(userId: String) {
        if (connectedUserId == userId && activeWebSocket != null) return
        disconnect()

        connectedUserId = userId
        if (userId.isBlank()) return

        val baseUrl = SupabaseConfig.supabaseUrl.trim().removeSuffix("/")
        val apiKey = SupabaseConfig.supabaseAnonKey.trim()
        if (baseUrl.isBlank() || apiKey.isBlank()) {
            Log.w(TAG, "Supabase config missing, running in local signaling mode")
            return
        }

        val wsHost = baseUrl.replace("https://", "wss://").replace("http://", "ws://")
        val wsUrl = "$wsHost/realtime/v1/websocket?apikey=$apiKey&vsn=1.0.0"

        try {
            val request = Request.Builder().url(wsUrl).build()
            activeWebSocket = httpClient.newWebSocket(request, object : WebSocketListener() {
                override fun onOpen(webSocket: WebSocket, response: Response) {
                    Log.d(TAG, "Connected to Call Realtime WebSocket for user: $userId")
                    // Join user personal signaling topic
                    val userTopic = "realtime:user_calls_$userId"
                    val joinMsg = JSONObject().apply {
                        put("topic", userTopic)
                        put("event", "phx_join")
                        put("payload", JSONObject().apply {
                            put("config", JSONObject().apply {
                                put("broadcast", JSONObject().apply {
                                    put("ack", false)
                                    put("self", false)
                                })
                            })
                        })
                        put("ref", "call_join_$userId")
                    }.toString()
                    webSocket.send(joinMsg)
                }

                override fun onMessage(webSocket: WebSocket, text: String) {
                    handleIncomingSocketMessage(text)
                }

                override fun onFailure(webSocket: WebSocket, t: Throwable, response: Response?) {
                    Log.w(TAG, "WebSocket connection failure: ${t.message}")
                }

                override fun onClosed(webSocket: WebSocket, code: Int, reason: String) {
                    Log.d(TAG, "WebSocket closed: code=$code, reason=$reason")
                }
            })
        } catch (e: Exception) {
            Log.e(TAG, "Error connecting to call websocket", e)
        }
    }

    /**
     * Send call signal to recipient via WebSocket broadcast + FCM fallback
     */
    fun sendSignal(signal: RealtimeCallSignal, context: Context? = null) {
        // Only emit locally in memory if recipient is ourself (testing)
        if (signal.receiverId == connectedUserId && signal.callerId != connectedUserId) {
            _incomingSignals.tryEmit(signal)
        }

        scope.launch {
            try {
                // 1. Send via Supabase WebSocket broadcast to target recipient channel
                val targetTopic = "realtime:user_calls_${signal.receiverId}"
                val broadcastMsg = JSONObject().apply {
                    put("topic", targetTopic)
                    put("event", "broadcast")
                    put("payload", JSONObject().apply {
                        put("type", "call_signal")
                        put("data", JSONObject().apply {
                            put("signal_type", signal.type)
                            put("call_id", signal.callId)
                            put("caller_id", signal.callerId)
                            put("caller_name", signal.callerName)
                            put("caller_avatar", signal.callerAvatar)
                            put("caller_gender", signal.callerGender)
                            put("receiver_id", signal.receiverId)
                            put("call_type", signal.callType)
                            put("timestamp", signal.timestamp)
                            put("reason", signal.reason)
                        })
                    })
                    put("ref", "call_sig_${System.currentTimeMillis()}")
                }.toString()

                activeWebSocket?.send(broadcastMsg)

                // 2. If it is an OFFER, dispatch FCM Push Notification to wake up receiver's device
                if (signal.type == "OFFER" && context != null) {
                    SupabaseFcmService.sendCallPushNotification(
                        context = context,
                        callerId = signal.callerId,
                        callerName = signal.callerName,
                        callerAvatar = signal.callerAvatar,
                        receiverId = signal.receiverId,
                        callType = signal.callType
                    )
                }
            } catch (e: Exception) {
                Log.e(TAG, "Error sending call signal: ${e.message}")
            }
        }
    }

    private fun handleIncomingSocketMessage(rawText: String) {
        try {
            val json = JSONObject(rawText)
            val event = json.optString("event", "")
            val payload = json.optJSONObject("payload") ?: return

            if (event == "broadcast") {
                val broadcastType = payload.optString("type", "")
                if (broadcastType == "call_signal") {
                    val data = payload.optJSONObject("data") ?: payload
                    val signal = RealtimeCallSignal(
                        type = data.optString("signal_type", "OFFER"),
                        callId = data.optString("call_id", ""),
                        callerId = data.optString("caller_id", ""),
                        callerName = data.optString("caller_name", "QIVO User"),
                        callerAvatar = data.optString("caller_avatar", ""),
                        callerGender = data.optString("caller_gender", "Male"),
                        receiverId = data.optString("receiver_id", ""),
                        callType = data.optString("call_type", "VOICE"),
                        timestamp = data.optLong("timestamp", System.currentTimeMillis()),
                        reason = data.optString("reason", "")
                    )
                    _incomingSignals.tryEmit(signal)
                }
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error handling incoming call socket msg: ${e.message}")
        }
    }

    fun disconnect() {
        try {
            activeWebSocket?.close(1000, "User logged out / disconnected")
        } catch (_: Exception) {}
        activeWebSocket = null
        connectedUserId = ""
    }
}

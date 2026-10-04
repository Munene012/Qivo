package com.example.data

import android.content.Context
import android.util.Log
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.channels.BufferOverflow
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.Response
import okhttp3.WebSocket
import okhttp3.WebSocketListener
import org.json.JSONArray
import org.json.JSONObject
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicInteger

/**
 * Realtime WebSocket Relay Manager for Agency Group Chat using Supabase Realtime.
 */
object AgencyRealtimeRelayManager {
    private const val TAG = "AgencyRealtimeRelay"

    private val scope = CoroutineScope(Dispatchers.IO + SupervisorJob())
    private val httpClient = OkHttpClient.Builder()
        .readTimeout(0, TimeUnit.MILLISECONDS)
        .pingInterval(15, TimeUnit.SECONDS)
        .build()

    private val refCounter = AtomicInteger(1)

    @Volatile var currentAgencyId: String? = null
        private set
    @Volatile var currentUserId: String? = null
        private set

    private var activeWebSocket: WebSocket? = null
    private var heartbeatJob: Job? = null
    private var isIntentionallyDisconnected = false

    private val _messages = MutableSharedFlow<AgencyGroupMessage>(
        replay = 0,
        extraBufferCapacity = 64,
        onBufferOverflow = BufferOverflow.DROP_OLDEST
    )
    val messages: SharedFlow<AgencyGroupMessage> = _messages.asSharedFlow()

    fun connect(context: Context, agencyId: String, userId: String) {
        if (agencyId.isBlank()) return

        if (currentAgencyId == agencyId && activeWebSocket != null && !isIntentionallyDisconnected) {
            Log.d(TAG, "Already connected to agency $agencyId")
            return
        }

        disconnect()

        currentAgencyId = agencyId
        currentUserId = userId
        isIntentionallyDisconnected = false

        val baseUrl = SupabaseConfig.supabaseUrl.trim().removeSuffix("/")
        val apiKey = SupabaseConfig.supabaseAnonKey.trim()

        if (baseUrl.isBlank() || apiKey.isBlank()) {
            Log.w(TAG, "Supabase credentials empty, skipping WebSocket connection")
            return
        }

        val wsHost = baseUrl.replace("https://", "wss://").replace("http://", "ws://")
        val token = UserSessionManager.getAccessToken(context).ifBlank { apiKey }
        val wsUrl = "$wsHost/realtime/v1/websocket?apikey=$apiKey&vsn=1.0.0${if (token.isNotBlank()) "&access_token=$token" else ""}"

        scope.launch {
            try {
                val request = Request.Builder()
                    .url(wsUrl)
                    .build()

                val targetAgencyId = agencyId

                activeWebSocket = httpClient.newWebSocket(request, object : WebSocketListener() {
                    override fun onOpen(webSocket: WebSocket, response: Response) {
                        Log.d(TAG, "WebSocket opened for agency $targetAgencyId")
                        startHeartbeat(webSocket)

                        val joinTopic = "realtime:agency_chat_$targetAgencyId"
                        val refStr = "agency_join_${refCounter.getAndIncrement()}"

                        val joinMsg = JSONObject().apply {
                            put("topic", joinTopic)
                            put("event", "phx_join")
                            put("payload", JSONObject().apply {
                                put("config", JSONObject().apply {
                                    put("postgres_changes", JSONArray().apply {
                                        put(JSONObject().apply {
                                            put("event", "INSERT")
                                            put("schema", "public")
                                            put("table", "agency_group_messages")
                                            put("filter", "agency_id=eq.$targetAgencyId")
                                        })
                                    })
                                })
                                if (token.isNotBlank()) {
                                    put("access_token", token)
                                }
                            })
                            put("ref", refStr)
                        }.toString()

                        webSocket.send(joinMsg)
                    }

                    override fun onMessage(webSocket: WebSocket, text: String) {
                        handleIncomingMessage(text, targetAgencyId)
                    }

                    override fun onFailure(webSocket: WebSocket, t: Throwable, response: Response?) {
                        Log.w(TAG, "WebSocket failure for agency $targetAgencyId: ${t.message}")
                        handleDisconnectAndReconnect(context, targetAgencyId, userId)
                    }

                    override fun onClosed(webSocket: WebSocket, code: Int, reason: String) {
                        Log.d(TAG, "WebSocket closed ($code, $reason) for agency $targetAgencyId")
                        handleDisconnectAndReconnect(context, targetAgencyId, userId)
                    }
                })
            } catch (e: Exception) {
                Log.e(TAG, "Error initiating WebSocket: ${e.message}")
            }
        }
    }

    private fun handleIncomingMessage(text: String, expectedAgencyId: String) {
        try {
            val json = JSONObject(text)
            val event = json.optString("event", "")

            if (event == "postgres_changes") {
                val payload = json.optJSONObject("payload")
                val data = payload?.optJSONObject("data")
                if (data != null) {
                    val eventType = data.optString("eventType", "")
                    if (eventType == "INSERT") {
                        val newObj = data.optJSONObject("new")
                        if (newObj != null) {
                            val agencyId = newObj.optString("agency_id", "")
                            if (agencyId == expectedAgencyId) {
                                val msg = AgencyGroupMessage(
                                    id = newObj.optLong("id", System.currentTimeMillis()),
                                    agencyId = agencyId,
                                    senderId = newObj.optString("sender_id", ""),
                                    senderNumericId = newObj.optLong("sender_numeric_id", 0L),
                                    senderName = newObj.optString("sender_name", "User"),
                                    senderAvatar = newObj.optString("sender_avatar", ""),
                                    senderRole = newObj.optString("sender_role", "MEMBER"),
                                    senderGender = newObj.optString("sender_gender", ""),
                                    message = newObj.optString("message", ""),
                                    createdAt = newObj.optString("created_at", "")
                                )
                                Log.d(TAG, "Emitting realtime group message: id=${msg.id}, text=${msg.message}")
                                _messages.tryEmit(msg)
                            }
                        }
                    }
                }
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error handling socket message: ${e.message}")
        }
    }

    private fun startHeartbeat(webSocket: WebSocket) {
        heartbeatJob?.cancel()
        heartbeatJob = scope.launch {
            while (isActive) {
                delay(25000)
                try {
                    val hb = JSONObject().apply {
                        put("topic", "phoenix")
                        put("event", "heartbeat")
                        put("payload", JSONObject())
                        put("ref", "hb_${refCounter.getAndIncrement()}")
                    }.toString()
                    webSocket.send(hb)
                } catch (e: Exception) {
                    Log.w(TAG, "Heartbeat error: ${e.message}")
                    break
                }
            }
        }
    }

    private fun handleDisconnectAndReconnect(context: Context, agencyId: String, userId: String) {
        heartbeatJob?.cancel()
        heartbeatJob = null
        if (!isIntentionallyDisconnected && currentAgencyId == agencyId) {
            scope.launch {
                delay(3000)
                if (!isIntentionallyDisconnected && currentAgencyId == agencyId) {
                    Log.d(TAG, "Reconnecting WebSocket for agency $agencyId...")
                    connect(context, agencyId, userId)
                }
            }
        }
    }

    fun disconnect() {
        isIntentionallyDisconnected = true
        heartbeatJob?.cancel()
        heartbeatJob = null
        try {
            activeWebSocket?.close(1000, "Leaving agency screen")
        } catch (_: Exception) {}
        activeWebSocket = null
        currentAgencyId = null
        currentUserId = null
        Log.d(TAG, "Disconnected Agency Realtime Relay")
    }
}

package com.example.data

import android.content.Context
import android.util.Log
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import okhttp3.*
import org.json.JSONArray
import org.json.JSONObject
import java.util.concurrent.TimeUnit

object GlobalChatState {
    val unreadCount: StateFlow<Int> = SupabaseChatService.totalUnreadCount

    fun setUnreadCount(count: Int) {
        SupabaseChatService.setUnreadCount(count)
    }

    fun decrementUnreadCount(amount: Int = 1) {
        SupabaseChatService.decrementUnreadCount(amount)
    }
}

object ChatStateHolder {
    private const val TAG = "ChatStateHolder"

    private val _messagesList = MutableStateFlow<List<ChatMessage>>(emptyList())
    val messagesList: StateFlow<List<ChatMessage>> = _messagesList.asStateFlow()

    private val _profilesMap = MutableStateFlow<Map<String, UserProfile>>(emptyMap())
    val profilesMap: StateFlow<Map<String, UserProfile>> = _profilesMap.asStateFlow()

    private val _isSyncing = MutableStateFlow(false)
    val isSyncing: StateFlow<Boolean> = _isSyncing.asStateFlow()

    private val _hasCompletedInitialSync = MutableStateFlow(false)
    val hasCompletedInitialSync: StateFlow<Boolean> = _hasCompletedInitialSync.asStateFlow()

    private val scope = CoroutineScope(Dispatchers.IO + SupervisorJob())
    private val chatService = SupabaseChatService()
    private val profileService = SupabaseProfileService()

    private var activeWebSocket: WebSocket? = null
    private var connectedUserId: String = ""
    private var isSubscribed = false

    private fun setMessagesList(list: List<ChatMessage>) {
        _messagesList.value = list
        if (connectedUserId.isNotBlank()) {
            val unread = list.count { !it.isRead && it.receiverId.trim().equals(connectedUserId, ignoreCase = true) }
            SupabaseChatService.setUnreadCount(unread)
            Log.d(TAG, "Global unread count changed: $unread")
        }
    }

    private val httpClient = OkHttpClient.Builder()
        .connectTimeout(10, TimeUnit.SECONDS)
        .readTimeout(0, TimeUnit.MILLISECONDS)
        .pingInterval(20, TimeUnit.SECONDS)
        .build()

    fun isNetworkAvailable(context: Context): Boolean {
        val connectivityManager = context.getSystemService(Context.CONNECTIVITY_SERVICE) as? android.net.ConnectivityManager
        if (connectivityManager != null) {
            val activeNetwork = connectivityManager.activeNetwork ?: return false
            val capabilities = connectivityManager.getNetworkCapabilities(activeNetwork) ?: return false
            return capabilities.hasCapability(android.net.NetworkCapabilities.NET_CAPABILITY_INTERNET)
        }
        return false
    }

    fun initialize(context: Context, currentUserId: String) {
        if (currentUserId.isBlank()) {
            setMessagesList(emptyList())
            _hasCompletedInitialSync.value = false
            disconnect()
            return
        }

        if (connectedUserId == currentUserId) {
            // Already initialized for this user, trigger a background sync to check for updates
            syncWithNetwork(context, currentUserId)
            return
        }

        connectedUserId = currentUserId
        _hasCompletedInitialSync.value = false

        // Populate profilesMap immediately and synchronously so there's no split-second "QIVO User" pop-up!
        try {
            val cachedProfs = AppDataCacheManager.getCachedProfilesSync(context, "all")
            val fallbackProfs = if (cachedProfs.isEmpty()) AppDataCacheManager.getCachedProfilesSync(context, "home") else cachedProfs
            _profilesMap.value = fallbackProfs.associateBy { it.id.trim() }
        } catch (_: Exception) {}

        // Immediately load from in-memory cache or persistent database cache
        scope.launch {
            try {
                val inMem = SupabaseChatService.getInMemoryMessages(currentUserId)
                val msgs = if (inMem.isNotEmpty()) {
                    inMem
                } else {
                    AppDataCacheManager.getCachedChatMessagesSync(context, currentUserId)
                }
                
                // Filter out deleted messages
                val filteredMsgs = msgs.filterNot { msg ->
                    val partnerId = if (msg.senderId.trim().equals(currentUserId, ignoreCase = true)) msg.receiverId.trim() else msg.senderId.trim()
                    chatService.isConversationSoftDeleted(context, currentUserId, partnerId, msg.createdAt)
                }
                if (filteredMsgs.isNotEmpty()) {
                    setMessagesList(filteredMsgs)
                }
            } catch (e: Exception) {
                Log.e(TAG, "Error loading initial chat messages: ${e.message}")
            } finally {
                _hasCompletedInitialSync.value = true
            }

            // Sync from network in the background
            syncWithNetwork(context, currentUserId)
        }

        // Establish real-time subscription
        connectRealtime(currentUserId)
    }

    fun appendOrUpdateMessages(newMsgs: List<ChatMessage>) {
        if (newMsgs.isEmpty()) return
        val current = _messagesList.value.toMutableList()
        val optimistic = current.filter { it.id == 0L }
        val combined = (optimistic + newMsgs)
            .distinctBy { if (it.id == 0L) it.hashCode().toLong() else it.id }
            .sortedByDescending { chatService.parseTimestampToMillis(it.createdAt) }
        if (!areMessageListsEqual(_messagesList.value, combined)) {
            setMessagesList(combined)
        }
    }

    fun areMessageListsEqual(a: List<ChatMessage>, b: List<ChatMessage>): Boolean {
        if (a === b) return true
        if (a.size != b.size) return false
        for (i in a.indices) {
            val x = a[i]
            val y = b[i]
            if (x.id != y.id || x.isRead != y.isRead || x.message != y.message || x.createdAt != y.createdAt || x.senderId != y.senderId || x.receiverId != y.receiverId) {
                return false
            }
        }
        return true
    }

    fun syncWithNetwork(context: Context, userId: String) {
        if (userId.isBlank()) return
        scope.launch {
            if (_isSyncing.value) return@launch
            _isSyncing.value = true
            try {
                val msgs = chatService.fetchUserMessages(userId, context, limit = 300)
                if (msgs.isNotEmpty()) {
                    appendOrUpdateMessages(msgs)
                }

                val allProfs = profileService.fetchAllProfiles()
                if (allProfs.isNotEmpty()) {
                    _profilesMap.value = allProfs.associateBy { it.id.trim() }
                    try {
                        AppDataCacheManager.saveProfilesCache(context, allProfs, "all")
                    } catch (_: Exception) {}
                }
                _hasCompletedInitialSync.value = true
            } catch (e: Exception) {
                Log.e(TAG, "Background sync failed: ${e.message}")
            } finally {
                _isSyncing.value = false
            }
        }
    }

    fun updateSingleProfile(profile: UserProfile, context: Context? = null) {
        val current = _profilesMap.value.toMutableMap()
        current[profile.id.trim()] = profile
        _profilesMap.value = current
        if (context != null) {
            scope.launch {
                try {
                    val list = current.values.toList()
                    AppDataCacheManager.saveProfilesCache(context, list, "all")
                } catch (_: Exception) {}
            }
        }
    }

    fun addOptimisticMessage(msg: ChatMessage) {
        val current = _messagesList.value.toMutableList()
        // Check for exact ID match
        val index = current.indexOfFirst { it.id == msg.id && msg.id != 0L }
        if (index != -1) {
            current[index] = msg
        } else {
            // Check for match of same sender, receiver, and message text to avoid duplicate rows
            val optIndex = current.indexOfFirst { 
                it.id == 0L && 
                it.senderId.trim().equals(msg.senderId.trim(), ignoreCase = true) && 
                it.receiverId.trim().equals(msg.receiverId.trim(), ignoreCase = true) && 
                it.message == msg.message 
            }
            if (optIndex != -1) {
                current[optIndex] = msg
            } else {
                current.add(0, msg)
            }
        }
        setMessagesList(current.distinctBy { if (it.id == 0L) it.hashCode() else it.id })
    }

    fun appendMessages(msgs: List<ChatMessage>) {
        val current = _messagesList.value.toMutableList()
        current.addAll(msgs)
        setMessagesList(current.distinctBy { if (it.id == 0L) it.hashCode() else it.id })
    }

    fun removeConversationLocally(partnerId: String) {
        val current = _messagesList.value.filterNot { msg ->
            val p = if (msg.senderId.trim().equals(connectedUserId, ignoreCase = true)) msg.receiverId.trim() else msg.senderId.trim()
            p.equals(partnerId, ignoreCase = true)
        }
        setMessagesList(current)
        SupabaseChatService.updateInMemoryMessages(connectedUserId, current)
    }

    fun handleRealtimeMessage(msg: ChatMessage, context: Context?) {
        if (connectedUserId.isBlank()) return
        // Keep only messages involving current user
        if (!msg.senderId.equals(connectedUserId, ignoreCase = true) && 
            !msg.receiverId.equals(connectedUserId, ignoreCase = true)) {
            return
        }

        val current = _messagesList.value.toMutableList()
        val existingIndex = current.indexOfFirst { it.id == msg.id && msg.id != 0L }
        if (existingIndex != -1) {
            current[existingIndex] = msg
        } else {
            // Optimistic match: match on sender, receiver, and exact content to merge placeholder
            val optIndex = current.indexOfFirst { 
                it.id == 0L && 
                it.senderId.trim().equals(msg.senderId.trim(), ignoreCase = true) && 
                it.receiverId.trim().equals(msg.receiverId.trim(), ignoreCase = true) && 
                it.message == msg.message 
            }
            if (optIndex != -1) {
                current[optIndex] = msg
            } else {
                current.add(0, msg)
            }
        }

        val sorted = current.distinctBy { if (it.id == 0L) it.hashCode().toLong() else it.id }
            .sortedByDescending { m ->
                chatService.parseTimestampToMillis(m.createdAt)
            }
        setMessagesList(sorted)

        // Push updates to chat service's in-memory storage and app persistence cache
        SupabaseChatService.updateInMemoryMessages(connectedUserId, sorted)
        if (context != null) {
            scope.launch {
                try {
                    AppDataCacheManager.saveChatMessagesCache(context, connectedUserId, sorted)
                } catch (_: Exception) {}
            }
        }
    }

    fun markMessageAsReadLocally(partnerId: String, context: Context?) {
        val current = _messagesList.value.map { msg ->
            if (msg.senderId.trim().equals(partnerId.trim(), ignoreCase = true) && 
                msg.receiverId.trim().equals(connectedUserId.trim(), ignoreCase = true)) {
                msg.copy(isRead = true)
            } else msg
        }
        setMessagesList(current)
        SupabaseChatService.updateInMemoryMessages(connectedUserId, current)
        if (context != null) {
            scope.launch {
                try {
                    AppDataCacheManager.saveChatMessagesCache(context, connectedUserId, current)
                } catch (_: Exception) {}
            }
        }
    }

    fun updateProfileLocally(profile: UserProfile) {
        val current = _profilesMap.value.toMutableMap()
        current[profile.id.trim()] = profile
        _profilesMap.value = current
    }

    fun connectRealtime(userId: String) {
        if (userId.isBlank()) return
        if (connectedUserId == userId && isSubscribed && activeWebSocket != null) {
            Log.d(TAG, "Realtime subscription already active for user $userId, skipping duplicate connection.")
            return
        }

        connectedUserId = userId
        Log.d(TAG, "Realtime subscription started for user $userId")

        val baseUrl = SupabaseConfig.supabaseUrl.trim().removeSuffix("/")
        val apiKey = SupabaseConfig.supabaseAnonKey.trim()
        if (baseUrl.isBlank() || apiKey.isBlank()) return

        val wsHost = baseUrl.replace("https://", "wss://").replace("http://", "ws://")
        val wsUrl = "$wsHost/realtime/v1/websocket?apikey=$apiKey&vsn=1.0.0"

        scope.launch {
            try {
                // Close existing socket if any before reconnecting
                try {
                    activeWebSocket?.close(1000, "Reconnecting new socket")
                } catch (_: Exception) {}

                val request = Request.Builder().url(wsUrl).build()
                activeWebSocket = httpClient.newWebSocket(request, object : WebSocketListener() {
                    override fun onOpen(webSocket: WebSocket, response: Response) {
                        Log.d(TAG, "Realtime subscription status: opened successfully, joining realtime channel")
                        
                        val joinTopic = "realtime:public:messages"
                        val joinMsg = JSONObject().apply {
                            put("topic", joinTopic)
                            put("event", "phx_join")
                            put("payload", JSONObject().apply {
                                put("config", JSONObject().apply {
                                    put("postgres_changes", JSONArray().apply {
                                        put(JSONObject().apply {
                                            put("event", "*")
                                            put("schema", "public")
                                            put("table", "messages")
                                        })
                                    })
                                })
                            })
                            put("ref", "chat_join")
                        }.toString()
                        webSocket.send(joinMsg)
                        isSubscribed = true
                    }

                    override fun onMessage(webSocket: WebSocket, text: String) {
                        try {
                            val json = JSONObject(text)
                            val event = json.optString("event", "")
                            if (event == "postgres_changes") {
                                val payload = json.optJSONObject("payload")
                                val data = payload?.optJSONObject("data")
                                if (data != null) {
                                    val eventType = data.optString("eventType", "")
                                    if (eventType == "INSERT" || eventType == "UPDATE") {
                                        val newObj = data.optJSONObject("new")
                                        if (newObj != null) {
                                            val id = newObj.optLong("id", 0L)
                                            val senderId = newObj.optString("sender_id", "").trim()
                                            val receiverId = newObj.optString("receiver_id", "").trim()
                                            val message = newObj.optString("message", "")
                                            val createdAt = newObj.optString("created_at", "")
                                            val isRead = newObj.optBoolean("is_read", false)
                                            val senderName = newObj.optString("sender_name", "User")
                                            val senderAvatar = newObj.optString("sender_avatar", "")
                                            val receiverName = newObj.optString("receiver_name", "User")

                                            Log.d(TAG, "Incoming INSERT received: id=$id, sender=$senderId, receiver=$receiverId")

                                            if (senderId.equals(connectedUserId, ignoreCase = true) || receiverId.equals(connectedUserId, ignoreCase = true)) {
                                                Log.d(TAG, "Incoming message identified as belonging to current user ($connectedUserId)")
                                                val chatMessage = ChatMessage(
                                                    id = id,
                                                    senderId = senderId,
                                                    senderName = senderName,
                                                    senderAvatar = senderAvatar,
                                                    receiverId = receiverId,
                                                    receiverName = receiverName,
                                                    message = message,
                                                    createdAt = createdAt,
                                                    isRead = isRead
                                                )
                                                handleRealtimeMessage(chatMessage, null)
                                            }
                                        }
                                    }
                                }
                            }
                        } catch (e: Exception) {
                            Log.e(TAG, "Error handling socket frame: ${e.message}")
                        }
                    }

                    override fun onFailure(webSocket: WebSocket, t: Throwable, response: Response?) {
                        Log.w(TAG, "WebSocket failure: ${t.message}, scheduling realtime reconnect...")
                        isSubscribed = false
                        scheduleReconnect()
                    }

                    override fun onClosed(webSocket: WebSocket, code: Int, reason: String) {
                        Log.d(TAG, "Subscription stopped / WebSocket closed: $reason, scheduling realtime reconnect...")
                        isSubscribed = false
                        scheduleReconnect()
                    }
                })
            } catch (e: Exception) {
                Log.e(TAG, "Error initiating WebSocket: ${e.message}")
                scheduleReconnect()
            }
        }
    }

    private fun scheduleReconnect() {
        if (connectedUserId.isBlank()) return
        scope.launch {
            delay(4000L) // wait 4 seconds before reconnecting
            if (connectedUserId.isNotBlank() && !isSubscribed) {
                Log.d(TAG, "Realtime reconnecting...")
                connectRealtime(connectedUserId)
            }
        }
    }

    fun disconnect() {
        Log.d(TAG, "Subscription stopped / Disconnect called")
        try {
            activeWebSocket?.close(1000, "Disconnect called")
        } catch (_: Exception) {}
        activeWebSocket = null
        connectedUserId = ""
        isSubscribed = false
    }
}

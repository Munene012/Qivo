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
    var connectedUserId: String = ""
        private set
    private var isSubscribed = false
    private var heartbeatJob: Job? = null
    private var continuousPollingJob: Job? = null
    private var appContext: Context? = null

    private fun setMessagesList(list: List<ChatMessage>) {
        val uId = connectedUserId.trim()
        val filtered = if (uId.isNotBlank()) {
            list.filter { msg ->
                val s = msg.senderId.trim()
                val r = msg.receiverId.trim()
                (s.equals(uId, ignoreCase = true) || r.equals(uId, ignoreCase = true)) &&
                !s.equals(r, ignoreCase = true)
            }
        } else list
        _messagesList.value = filtered
        if (uId.isNotBlank()) {
            val unread = filtered.count { !it.isRead && it.receiverId.trim().equals(uId, ignoreCase = true) }
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

    fun startContinuousPolling(context: Context, userId: String) {
        val uId = userId.trim()
        if (uId.isBlank()) return
        continuousPollingJob?.cancel()
        continuousPollingJob = scope.launch {
            while (isActive && connectedUserId.equals(uId, ignoreCase = true)) {
                delay(3500L) // Poll every 3.5s for seamless background updates across all screens
                val ctx = appContext ?: context.applicationContext
                if (isNetworkAvailable(ctx)) {
                    try {
                        val recent = chatService.checkRecentIncomingMessages(uId, ctx, limit = 40)
                        if (recent.isNotEmpty()) {
                            appendOrUpdateMessages(recent)
                        }
                    } catch (e: Exception) {
                        Log.w(TAG, "Continuous message polling error: ${e.message}")
                    }
                }
            }
        }
    }

    fun initialize(context: Context, currentUserId: String) {
        appContext = context.applicationContext
        if (currentUserId.isBlank()) {
            setMessagesList(emptyList())
            _hasCompletedInitialSync.value = false
            disconnect()
            return
        }

        if (connectedUserId == currentUserId) {
            // Ensure continuous polling & realtime are actively running
            startContinuousPolling(context, currentUserId)
            connectRealtime(currentUserId)
            syncWithNetwork(context, currentUserId)
            return
        }

        connectedUserId = currentUserId
        _hasCompletedInitialSync.value = false
        startContinuousPolling(context, currentUserId)

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
                
                // Filter messages belonging to current user only
                val userMsgs = msgs.filter { msg ->
                    val s = msg.senderId.trim()
                    val r = msg.receiverId.trim()
                    (s.equals(currentUserId, ignoreCase = true) || r.equals(currentUserId, ignoreCase = true)) &&
                    !s.equals(r, ignoreCase = true)
                }

                // Filter out deleted messages
                val filteredMsgs = userMsgs.filterNot { msg ->
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
        val uId = connectedUserId.trim()
        val ctx = appContext

        // Base list: start from current in-memory list, falling back to cached messages
        val baseList = _messagesList.value.ifEmpty {
            if (uId.isNotBlank()) SupabaseChatService.getInMemoryMessages(uId) else emptyList()
        }.ifEmpty {
            if (ctx != null && uId.isNotBlank()) AppDataCacheManager.getCachedChatMessagesSync(ctx, uId) else emptyList()
        }

        val current = baseList.toMutableList()

        for (newMsg in newMsgs) {
            // Strictly check that message involves the connected user
            if (uId.isNotBlank()) {
                val s = newMsg.senderId.trim()
                val r = newMsg.receiverId.trim()
                if (!s.equals(uId, ignoreCase = true) && !r.equals(uId, ignoreCase = true)) {
                    continue
                }
                if (s.equals(r, ignoreCase = true)) {
                    continue
                }
            }

            // Skip if soft-deleted for current user
            if (ctx != null && uId.isNotBlank() && chatService.isMessageSoftDeleted(ctx, uId, newMsg)) {
                continue
            }

            if (newMsg.id > 0L) {
                // 1. Check if server ID already exists in current list
                val existingIndex = current.indexOfFirst { it.id == newMsg.id }
                if (existingIndex != -1) {
                    // Update existing server message (e.g. read state or updated content)
                    current[existingIndex] = newMsg
                } else {
                    // 2. Check if this resolves a pending optimistic message (id <= 0L)
                    val optIndex = current.indexOfFirst {
                        it.id <= 0L &&
                        it.senderId.trim().equals(newMsg.senderId.trim(), ignoreCase = true) &&
                        it.receiverId.trim().equals(newMsg.receiverId.trim(), ignoreCase = true) &&
                        it.message == newMsg.message
                    }
                    if (optIndex != -1) {
                        current[optIndex] = newMsg
                    } else {
                        // Brand new message - append without discarding existing older messages
                        current.add(newMsg)
                    }
                }
            } else {
                // Optimistic message (id <= 0L)
                val existingOpt = current.indexOfFirst {
                    it.id <= 0L &&
                    it.senderId.trim().equals(newMsg.senderId.trim(), ignoreCase = true) &&
                    it.receiverId.trim().equals(newMsg.receiverId.trim(), ignoreCase = true) &&
                    it.message == newMsg.message
                }
                if (existingOpt != -1) {
                    current[existingOpt] = newMsg
                } else {
                    current.add(newMsg)
                }
            }
        }

        // Deduplicate: server messages by id, optimistic messages by signature
        val distinct = current.distinctBy { msg ->
            if (msg.id > 0L) "srv_${msg.id}" else "opt_${msg.senderId}_${msg.receiverId}_${msg.createdAt}_${msg.message}"
        }

        // Filter out any messages that are soft-deleted
        val filtered = if (ctx != null && uId.isNotBlank()) {
            distinct.filterNot { chatService.isMessageSoftDeleted(ctx, uId, it) }
        } else {
            distinct
        }

        // Sort descending by timestamp, tie-breaking on id descending
        val sorted = filtered.sortedWith(
            compareByDescending<ChatMessage> { chatService.parseTimestampToMillis(it.createdAt) }
                .thenByDescending { it.id }
        )

        if (!areMessageListsEqual(_messagesList.value, sorted)) {
            setMessagesList(sorted)
        }

        // Persist to in-memory cache and background disk cache
        if (uId.isNotBlank()) {
            SupabaseChatService.updateInMemoryMessages(uId, sorted)
            if (ctx != null) {
                scope.launch {
                    try {
                        AppDataCacheManager.saveChatMessagesCache(ctx, uId, sorted)
                    } catch (_: Exception) {}
                }
            }
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
        appendOrUpdateMessages(listOf(msg))
    }

    fun appendMessages(msgs: List<ChatMessage>) {
        appendOrUpdateMessages(msgs)
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
        appendOrUpdateMessages(listOf(msg))
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

    private fun startHeartbeat(webSocket: WebSocket) {
        heartbeatJob?.cancel()
        heartbeatJob = scope.launch {
            var ref = 1
            while (isActive && activeWebSocket === webSocket) {
                delay(20_000L) // Phoenix protocol heartbeat required every 20-30s
                try {
                    val hbMsg = JSONObject().apply {
                        put("topic", "phoenix")
                        put("event", "heartbeat")
                        put("payload", JSONObject())
                        put("ref", "hb_${ref++}")
                    }.toString()
                    webSocket.send(hbMsg)
                } catch (_: Exception) {
                    break
                }
            }
        }
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

        val userToken = if (appContext != null) UserSessionManager.getValidAccessToken(appContext) else UserSessionManager.getAccessToken()
        val wsHost = baseUrl.replace("https://", "wss://").replace("http://", "ws://")
        val wsUrl = "$wsHost/realtime/v1/websocket?apikey=$apiKey&vsn=1.0.0${if (userToken.isNotBlank()) "&access_token=$userToken" else ""}"

        scope.launch {
            try {
                // Close existing socket if any before reconnecting
                try {
                    heartbeatJob?.cancel()
                    heartbeatJob = null
                    activeWebSocket?.close(1000, "Reconnecting new socket")
                } catch (_: Exception) {}

                val request = Request.Builder().url(wsUrl).build()
                activeWebSocket = httpClient.newWebSocket(request, object : WebSocketListener() {
                    override fun onOpen(webSocket: WebSocket, response: Response) {
                        Log.d(TAG, "Realtime subscription status: opened successfully, joining realtime channel")
                        startHeartbeat(webSocket)
                        
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
                        // Wait for phx_reply status "ok" before setting isSubscribed = true
                    }

                    override fun onMessage(webSocket: WebSocket, text: String) {
                        try {
                            val json = JSONObject(text)
                            val event = json.optString("event", "")
                            val ref = json.optString("ref", "")

                            if (event == "phx_reply" && ref == "chat_join") {
                                val payload = json.optJSONObject("payload")
                                val status = payload?.optString("status", "") ?: ""
                                if (status.equals("ok", ignoreCase = true)) {
                                    isSubscribed = true
                                    Log.d(TAG, "Realtime channel joined successfully (phx_reply status ok)")
                                } else {
                                    Log.w(TAG, "Realtime channel join failed: status=$status")
                                }
                            }

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
                                                handleRealtimeMessage(chatMessage, appContext)
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
                        heartbeatJob?.cancel()
                        heartbeatJob = null
                        isSubscribed = false
                        scheduleReconnect()
                    }

                    override fun onClosed(webSocket: WebSocket, code: Int, reason: String) {
                        Log.d(TAG, "Subscription stopped / WebSocket closed: $reason, scheduling realtime reconnect...")
                        heartbeatJob?.cancel()
                        heartbeatJob = null
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
        heartbeatJob?.cancel()
        heartbeatJob = null
        continuousPollingJob?.cancel()
        continuousPollingJob = null
        try {
            activeWebSocket?.close(1000, "Disconnect called")
        } catch (_: Exception) {}
        activeWebSocket = null
        connectedUserId = ""
        isSubscribed = false
    }
}

package com.example.data
import com.example.ui.components.AppToast

import android.content.Context
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.withContext
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import org.json.JSONArray
import org.json.JSONObject
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.concurrent.TimeUnit

data class ChatMessage(
    val id: Long = 0L,
    val senderId: String,
    val senderName: String,
    val senderAvatar: String,
    val receiverId: String,
    val receiverName: String,
    val message: String,
    val createdAt: String,
    val isRead: Boolean = false
)

class SupabaseChatService {
    companion object {
        private val _totalUnreadCount = MutableStateFlow(0)
        val totalUnreadCount: StateFlow<Int> = _totalUnreadCount.asStateFlow()

        private val _cachedUserMessages = java.util.concurrent.ConcurrentHashMap<String, List<ChatMessage>>()

        fun getInMemoryMessages(userId: String): List<ChatMessage> {
            if (userId.isBlank()) return emptyList()
            return _cachedUserMessages[userId.trim()] ?: emptyList()
        }

        fun updateInMemoryMessages(userId: String, messages: List<ChatMessage>) {
            if (userId.isNotBlank() && messages.isNotEmpty()) {
                _cachedUserMessages[userId.trim()] = messages
            }
        }

        fun setUnreadCount(count: Int) {
            _totalUnreadCount.value = count.coerceAtLeast(0)
        }

        fun decrementUnreadCount(amount: Int = 1) {
            _totalUnreadCount.value = (_totalUnreadCount.value - amount).coerceAtLeast(0)
        }
    }

    private val client = SupabaseHttpClient.client

    private val jsonMediaType = "application/json; charset=utf-8".toMediaType()
    private val profileService = SupabaseProfileService()

    /**
     * Send a real chat message to Supabase "messages" table
     */
    suspend fun sendMessage(
        senderId: String,
        senderName: String,
        senderAvatar: String,
        receiverId: String,
        receiverName: String,
        messageText: String,
        context: Context? = null
    ): Boolean {
        // Enforce bidirectional blocking security
        if (senderId.isNotBlank() && receiverId.isNotBlank()) {
            val isBlocked = profileService.isUserBlocked(senderId, receiverId, context) ||
                    profileService.checkIfBlockedBidirectionalRemote(senderId, receiverId, context)
            if (isBlocked) {
                context?.let { ctx ->
                    kotlinx.coroutines.withContext(Dispatchers.Main) {
                        AppToast.show("You have been blocked.")
                    }
                }
                return false
            }
        }

        if (context != null && senderId.isNotBlank() && receiverId.isNotBlank()) {
            clearSoftDelete(context, senderId, receiverId)
        }
        return withContext(Dispatchers.IO) {
            try {
                val baseUrl = SupabaseConfig.supabaseUrl.trim().removeSuffix("/")
                val apiKey = SupabaseConfig.supabaseAnonKey.trim()

                if (baseUrl.isEmpty() || apiKey.isEmpty()) return@withContext false

                val endpoint = "$baseUrl/rest/v1/messages"
                val jsonBody = JSONObject().apply {
                    put("sender_id", senderId)
                    put("sender_name", senderName)
                    put("sender_avatar", senderAvatar)
                    put("receiver_id", receiverId)
                    put("receiver_name", receiverName)
                    put("message", messageText)
                    put("is_read", false)
                }.toString()

                val authHeader = UserSessionManager.getAuthHeader(context)

                val request = Request.Builder()
                    .url(endpoint)
                    .addHeader("apikey", apiKey)
                    .addHeader("Authorization", authHeader)
                    .addHeader("Content-Type", "application/json")
                    .addHeader("Prefer", "return=representation")
                    .post(jsonBody.toRequestBody(jsonMediaType))
                    .build()

                var createdMsgId: Long? = null
                val isSent = client.newCall(request).execute().use { response ->
                    val respBody = response.body?.string() ?: ""
                    if ((response.isSuccessful || response.code in 200..204) && respBody.startsWith("[")) {
                        try {
                            val arr = org.json.JSONArray(respBody)
                            if (arr.length() > 0) {
                                createdMsgId = arr.getJSONObject(0).optLong("id", 0L)
                            }
                        } catch (_: Exception) {}
                    }
                    response.isSuccessful || response.code == 200 || response.code == 201 || response.code == 204
                }

                if (isSent && context != null) {
                    try {
                        SupabaseFcmService.sendChatPushNotification(
                            context = context,
                            senderId = senderId,
                            senderName = senderName,
                            senderAvatar = senderAvatar,
                            receiverId = receiverId,
                            messageText = messageText
                        )
                    } catch (e: Exception) {
                        e.printStackTrace()
                    }
                }
                isSent
            } catch (e: Exception) {
                e.printStackTrace()
                false
            }
        }
    }

    /**
     * Send a real chat message and return created message ID
     */
    suspend fun sendMessageWithId(
        senderId: String,
        senderName: String,
        senderAvatar: String,
        receiverId: String,
        receiverName: String,
        messageText: String,
        context: Context? = null
    ): Long? {
        if (senderId.isNotBlank() && receiverId.isNotBlank()) {
            val isBlocked = profileService.isUserBlocked(senderId, receiverId, context) ||
                    profileService.checkIfBlockedBidirectionalRemote(senderId, receiverId, context)
            if (isBlocked) return null
        }

        if (context != null && senderId.isNotBlank() && receiverId.isNotBlank()) {
            clearSoftDelete(context, senderId, receiverId)
        }

        return withContext(Dispatchers.IO) {
            try {
                val baseUrl = SupabaseConfig.supabaseUrl.trim().removeSuffix("/")
                val apiKey = SupabaseConfig.supabaseAnonKey.trim()
                if (baseUrl.isEmpty() || apiKey.isEmpty()) return@withContext null

                val endpoint = "$baseUrl/rest/v1/messages"
                val jsonBody = JSONObject().apply {
                    put("sender_id", senderId)
                    put("sender_name", senderName)
                    put("sender_avatar", senderAvatar)
                    put("receiver_id", receiverId)
                    put("receiver_name", receiverName)
                    put("message", messageText)
                    put("is_read", false)
                }.toString()

                val authHeader = UserSessionManager.getAuthHeader(context)
                val request = Request.Builder()
                    .url(endpoint)
                    .addHeader("apikey", apiKey)
                    .addHeader("Authorization", authHeader)
                    .addHeader("Content-Type", "application/json")
                    .addHeader("Prefer", "return=representation")
                    .post(jsonBody.toRequestBody(jsonMediaType))
                    .build()

                client.newCall(request).execute().use { response ->
                    val respBody = response.body?.string() ?: ""
                    if (response.isSuccessful && respBody.startsWith("[")) {
                        val arr = org.json.JSONArray(respBody)
                        if (arr.length() > 0) {
                            val id = arr.getJSONObject(0).optLong("id", 0L)
                            if (id > 0L) return@withContext id
                        }
                    }
                }
                null
            } catch (_: Exception) {
                null
            }
        }
    }

    /**
     * Request Fast Reply reward from server (Edge Function -> RPC process_fast_reply_reward)
     * Female only; calculated strictly server-side.
     */
    suspend fun claimFastReplyReward(
        originalMessageId: Long,
        replyMessageId: Long,
        context: Context? = null
    ): Pair<Boolean, Double> {
        if (originalMessageId <= 0L || replyMessageId <= 0L) return Pair(false, 0.0)

        return withContext(Dispatchers.IO) {
            try {
                val baseUrl = SupabaseConfig.supabaseUrl.trim().removeSuffix("/")
                val apiKey = SupabaseConfig.supabaseAnonKey.trim()
                val authHeader = UserSessionManager.getAuthHeader(context)
                val idempotencyKey = "fr_${replyMessageId}_${java.util.UUID.randomUUID().toString().take(8)}"

                val jsonBody = JSONObject().apply {
                    put("original_message_id", originalMessageId)
                    put("reply_message_id", replyMessageId)
                    put("idempotency_key", idempotencyKey)
                }.toString()

                // 1. Try Edge Function: /functions/v1/process-fast-reply
                val edgeUrl = "$baseUrl/functions/v1/process-fast-reply"
                val edgeReq = Request.Builder()
                    .url(edgeUrl)
                    .addHeader("apikey", apiKey)
                    .addHeader("Authorization", authHeader)
                    .addHeader("Content-Type", "application/json")
                    .post(jsonBody.toRequestBody(jsonMediaType))
                    .build()

                try {
                    client.newCall(edgeReq).execute().use { response ->
                        if (response.isSuccessful) {
                            val bodyStr = response.body?.string() ?: ""
                            val resObj = JSONObject(bodyStr)
                            val success = resObj.optBoolean("success", false)
                            val diamonds = resObj.optDouble("diamonds_awarded", 0.0)
                            return@withContext Pair(success, diamonds)
                        }
                    }
                } catch (_: Exception) {}

                // 2. Direct RPC fallback: /rest/v1/rpc/process_fast_reply_reward
                val rpcUrl = "$baseUrl/rest/v1/rpc/process_fast_reply_reward"
                val rpcReq = Request.Builder()
                    .url(rpcUrl)
                    .addHeader("apikey", apiKey)
                    .addHeader("Authorization", authHeader)
                    .addHeader("Content-Type", "application/json")
                    .post(jsonBody.toRequestBody(jsonMediaType))
                    .build()

                client.newCall(rpcReq).execute().use { response ->
                    if (response.isSuccessful) {
                        val bodyStr = response.body?.string() ?: ""
                        val resObj = JSONObject(bodyStr)
                        val success = resObj.optBoolean("success", false)
                        val diamonds = resObj.optDouble("diamonds_awarded", 0.0)
                        return@withContext Pair(success, diamonds)
                    }
                }
                Pair(false, 0.0)
            } catch (e: Exception) {
                e.printStackTrace()
                Pair(false, 0.0)
            }
        }
    }

    /**
     * Fetch Fast Reply diamond reward ledger entries for a female user
     * Returns a map of reply_message_id -> diamonds_awarded
     */
    suspend fun fetchFastReplyRewardsMap(femaleUserId: String, context: Context? = null): Map<Long, Double> {
        if (femaleUserId.isBlank()) return emptyMap()
        return withContext(Dispatchers.IO) {
            try {
                val baseUrl = SupabaseConfig.supabaseUrl.trim().removeSuffix("/")
                val apiKey = SupabaseConfig.supabaseAnonKey.trim()
                if (baseUrl.isEmpty() || apiKey.isEmpty()) return@withContext emptyMap()
                val authHeader = UserSessionManager.getAuthHeader(context)
                val url = "$baseUrl/rest/v1/fast_reply_transactions?female_user_id=eq.$femaleUserId&select=reply_message_id,diamonds_awarded&limit=100"
                val req = Request.Builder()
                    .url(url)
                    .addHeader("apikey", apiKey)
                    .addHeader("Authorization", authHeader)
                    .get()
                    .build()
                client.newCall(req).execute().use { response ->
                    if (response.isSuccessful) {
                        val body = response.body?.string() ?: ""
                        if (body.startsWith("[")) {
                            val arr = JSONArray(body)
                            val map = mutableMapOf<Long, Double>()
                            for (i in 0 until arr.length()) {
                                val item = arr.getJSONObject(i)
                                val replyId = item.optLong("reply_message_id", 0L)
                                val diamonds = item.optDouble("diamonds_awarded", 0.0)
                                if (replyId > 0L && diamonds > 0.0) {
                                    map[replyId] = diamonds
                                }
                            }
                            return@withContext map
                        }
                    }
                }
                emptyMap()
            } catch (e: Exception) {
                emptyMap()
            }
        }
    }

    /**
     * Mark all incoming messages from senderId to receiverId as read
     */
    suspend fun markMessagesAsRead(senderId: String, receiverId: String): Boolean {
        if (senderId.isBlank() || receiverId.isBlank()) return false
        return withContext(Dispatchers.IO) {
            try {
                val baseUrl = SupabaseConfig.supabaseUrl.trim().removeSuffix("/")
                val apiKey = SupabaseConfig.supabaseAnonKey.trim()

                if (baseUrl.isEmpty() || apiKey.isEmpty()) return@withContext false

                val endpoint = "$baseUrl/rest/v1/messages?sender_id=eq.$senderId&receiver_id=eq.$receiverId&is_read=eq.false"
                val isoDate = SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss.SSS'Z'", Locale.US).apply {
                    timeZone = java.util.TimeZone.getTimeZone("UTC")
                }.format(Date())

                val jsonBody = JSONObject().apply {
                    put("is_read", true)
                    put("read_at", isoDate)
                }.toString()

                val authHeader = UserSessionManager.getAuthHeader()

                val request = Request.Builder()
                    .url(endpoint)
                    .addHeader("apikey", apiKey)
                    .addHeader("Authorization", authHeader)
                    .addHeader("Content-Type", "application/json")
                    .addHeader("Prefer", "return=minimal")
                    .patch(jsonBody.toRequestBody(jsonMediaType))
                    .build()

                client.newCall(request).execute().use { response ->
                    response.isSuccessful || response.code in 200..204
                }
            } catch (e: Exception) {
                e.printStackTrace()
                false
            }
        }
    }

    /**
     * Soft delete a conversation for the current user.
     * Stores the cutoff timestamp locally
     * so prior messages are hidden for this user, but preserved for the other user.
     * When a new message, gift, or photo is sent or received after this cutoff,
     * the conversation automatically reappears.
     */
    suspend fun softDeleteConversation(context: Context, myUserId: String, partnerId: String): Boolean {
        if (myUserId.isBlank() || partnerId.isBlank()) return false
        val now = System.currentTimeMillis()
        val prefs = context.getSharedPreferences("qivo_deleted_chats", Context.MODE_PRIVATE)
        prefs.edit().putLong("${myUserId.trim()}_${partnerId.trim()}", now).apply()

        // Immediately remove soft-deleted messages from in-memory and disk caches
        val currentInMem = _cachedUserMessages[myUserId.trim()]
        if (!currentInMem.isNullOrEmpty()) {
            val filtered = currentInMem.filterNot { msg ->
                (msg.senderId.trim().equals(myUserId.trim(), ignoreCase = true) && msg.receiverId.trim().equals(partnerId.trim(), ignoreCase = true)) ||
                (msg.receiverId.trim().equals(myUserId.trim(), ignoreCase = true) && msg.senderId.trim().equals(partnerId.trim(), ignoreCase = true))
            }
            _cachedUserMessages[myUserId.trim()] = filtered
            try {
                AppDataCacheManager.saveChatMessagesCache(context, myUserId.trim(), filtered)
            } catch (_: Exception) {}
        }
        return true
    }

    /**
     * Checks if a conversation with partnerId is currently soft-deleted for myUserId.
     * If latestMessageCreatedAt is provided, checks whether a new message was sent/received AFTER the cutoff.
     */
    fun isConversationSoftDeleted(
        context: Context?,
        myUserId: String,
        partnerId: String,
        latestMessageCreatedAt: String? = null
    ): Boolean {
        if (context == null || myUserId.isBlank() || partnerId.isBlank()) return false
        val prefs = context.getSharedPreferences("qivo_deleted_chats", Context.MODE_PRIVATE)
        val cutoff = prefs.getLong("${myUserId.trim()}_${partnerId.trim()}", 0L)
        if (cutoff <= 0L) return false
        if (latestMessageCreatedAt.isNullOrBlank()) return true
        val msgTime = parseTimestampToMillis(latestMessageCreatedAt)
        // If message timestamp is at or before cutoff (allowing 5000ms clock skew buffer), it remains deleted
        return msgTime == 0L || msgTime <= (cutoff + 5000L)
    }

    /**
     * Clears soft deletion for this conversation so it reappears.
     */
    fun clearSoftDelete(context: Context, myUserId: String, partnerId: String) {
        if (myUserId.isBlank() || partnerId.isBlank()) return
        val prefs = context.getSharedPreferences("qivo_deleted_chats", Context.MODE_PRIVATE)
        prefs.edit().remove("${myUserId.trim()}_${partnerId.trim()}").apply()
    }

    /**
     * Check recent incoming messages for heads-up notifications and badges.
     * Does NOT overwrite or truncate the user's full conversation cache.
     */
    suspend fun checkRecentIncomingMessages(
        userId: String,
        context: Context? = null,
        limit: Int = 30
    ): List<ChatMessage> {
        return withContext(Dispatchers.IO) {
            val uId = userId.trim()
            if (uId.isBlank()) return@withContext emptyList()
            try {
                val baseUrl = SupabaseConfig.supabaseUrl.trim().removeSuffix("/")
                val apiKey = SupabaseConfig.supabaseAnonKey.trim()
                if (baseUrl.isEmpty() || apiKey.isEmpty()) return@withContext emptyList()

                val endpoint = "$baseUrl/rest/v1/messages?receiver_id=eq.$uId&order=created_at.desc&limit=$limit"
                val authHeader = UserSessionManager.getAuthHeader(context)

                val request = Request.Builder()
                    .url(endpoint)
                    .addHeader("apikey", apiKey)
                    .addHeader("Authorization", authHeader)
                    .get()
                    .build()

                val deletedPrefs = context?.getSharedPreferences("qivo_deleted_chats", Context.MODE_PRIVATE)

                client.newCall(request).execute().use { response ->
                    val respBody = response.body?.string() ?: ""
                    if (response.isSuccessful && respBody.startsWith("[")) {
                        val jsonArray = JSONArray(respBody)
                        val list = mutableListOf<ChatMessage>()
                        for (i in 0 until jsonArray.length()) {
                            val obj = jsonArray.getJSONObject(i)
                            val sId = obj.optString("sender_id", "").trim()
                            val rId = obj.optString("receiver_id", "").trim()
                            val createdAtStr = obj.optString("created_at", "")

                            val cutoff = deletedPrefs?.getLong("${uId}_${sId}", 0L) ?: 0L
                            if (cutoff > 0L) {
                                val ts = parseTimestampToMillis(createdAtStr)
                                if (ts == 0L || ts <= (cutoff + 5000L)) continue
                            }

                            list.add(
                                ChatMessage(
                                    id = obj.optLong("id", 0L),
                                    senderId = sId,
                                    senderName = obj.optString("sender_name", "User"),
                                    senderAvatar = obj.optString("sender_avatar", ""),
                                    receiverId = rId,
                                    receiverName = obj.optString("receiver_name", "User"),
                                    message = obj.optString("message", ""),
                                    createdAt = createdAtStr,
                                    isRead = obj.optBoolean("is_read", false)
                                )
                            )
                        }
                        return@withContext list
                    }
                }
                emptyList()
            } catch (_: Exception) {
                emptyList()
            }
        }
    }

    /**
     * Fetch real chat messages involving user (either sent or received) with pagination support
     */
    suspend fun fetchUserMessages(
        userId: String,
        context: Context? = null,
        offset: Int = 0,
        limit: Int = 300,
        updateCache: Boolean = true
    ): List<ChatMessage> {
        return withContext(Dispatchers.IO) {
            try {
                val baseUrl = SupabaseConfig.supabaseUrl.trim().removeSuffix("/")
                val apiKey = SupabaseConfig.supabaseAnonKey.trim()

                if (baseUrl.isEmpty() || apiKey.isEmpty()) return@withContext emptyList()

                val endpoint = "$baseUrl/rest/v1/messages?or=(sender_id.eq.$userId,receiver_id.eq.$userId)&order=created_at.desc&limit=$limit&offset=$offset"
                val authHeader = UserSessionManager.getAuthHeader(context)

                val request = Request.Builder()
                    .url(endpoint)
                    .addHeader("apikey", apiKey)
                    .addHeader("Authorization", authHeader)
                    .get()
                    .build()

                val deletedPrefs = context?.getSharedPreferences("qivo_deleted_chats", Context.MODE_PRIVATE)

                client.newCall(request).execute().use { response ->
                    val responseBodyString = response.body?.string() ?: ""
                    if (response.isSuccessful && responseBodyString.startsWith("[")) {
                        val jsonArray = JSONArray(responseBodyString)
                        val messagesList = mutableListOf<ChatMessage>()
                        for (i in 0 until jsonArray.length()) {
                            val obj = jsonArray.getJSONObject(i)
                            val sId = obj.optString("sender_id", "").trim()
                            val rId = obj.optString("receiver_id", "").trim()
                            val createdAtStr = obj.optString("created_at", "")

                            val partnerId = if (sId.equals(userId, ignoreCase = true)) rId else sId

                            // Suppress messages from/to blocked users
                            if (profileService.isUserBlocked(userId, partnerId, context)) {
                                continue
                            }

                            val cutoffTimestamp = deletedPrefs?.getLong("${userId}_${partnerId}", 0L) ?: 0L

                            if (cutoffTimestamp > 0L) {
                                val msgTimestamp = parseTimestampToMillis(createdAtStr)
                                // Only suppress if message was created on or before the soft-delete cutoff (+ 5s skew buffer)
                                if (msgTimestamp == 0L || msgTimestamp <= (cutoffTimestamp + 5000L)) {
                                    continue
                                }
                            }

                            messagesList.add(
                                ChatMessage(
                                    id = obj.optLong("id", 0L),
                                    senderId = sId,
                                    senderName = obj.optString("sender_name", "User"),
                                    senderAvatar = obj.optString("sender_avatar", ""),
                                    receiverId = rId,
                                    receiverName = obj.optString("receiver_name", "User"),
                                    message = obj.optString("message", ""),
                                    createdAt = createdAtStr,
                                    isRead = obj.optBoolean("is_read", false)
                                )
                            )
                        }

                        if (updateCache) {
                            val finalToCache = if (offset == 0) {
                                messagesList
                            } else {
                                (getInMemoryMessages(userId) + messagesList).distinctBy { it.id }
                            }
                            val unread = finalToCache.count { !it.isRead && it.receiverId.trim().equals(userId.trim(), ignoreCase = true) }
                            _totalUnreadCount.value = unread
                            updateInMemoryMessages(userId, finalToCache)
                            if (context != null) {
                                try {
                                    AppDataCacheManager.saveChatMessagesCache(context, userId, finalToCache)
                                } catch (_: Exception) {}
                            }
                        }

                        return@withContext messagesList
                    }
                }
                
                // Fallback to in-memory or on-disk cache if network response was not successful
                val inMem = getInMemoryMessages(userId)
                if (inMem.isNotEmpty()) return@withContext inMem
                if (context != null) {
                    val diskCached = AppDataCacheManager.getCachedChatMessagesSync(context, userId)
                    if (diskCached.isNotEmpty()) {
                        updateInMemoryMessages(userId, diskCached)
                        return@withContext diskCached
                    }
                }
                emptyList()
            } catch (e: Exception) {
                e.printStackTrace()
                val inMem = getInMemoryMessages(userId)
                if (inMem.isNotEmpty()) return@withContext inMem
                if (context != null) {
                    val diskCached = AppDataCacheManager.getCachedChatMessagesSync(context, userId)
                    if (diskCached.isNotEmpty()) {
                        updateInMemoryMessages(userId, diskCached)
                        return@withContext diskCached
                    }
                }
                emptyList()
            }
        }
    }

    /**
     * Fetch conversation messages between two specific users with pagination support.
     * Messages are returned ordered by created_at descending (newest first).
     * e.g., offset = 0, limit = 20 returns the 20 most recent messages between the two users.
     */
    suspend fun fetchConversationMessages(
        userId1: String,
        userId2: String,
        context: Context? = null,
        offset: Int = 0,
        limit: Int = 20
    ): List<ChatMessage> {
        return withContext(Dispatchers.IO) {
            val u1 = userId1.trim()
            val u2 = userId2.trim()
            if (u1.isEmpty() || u2.isEmpty()) return@withContext emptyList()

            try {
                val baseUrl = SupabaseConfig.supabaseUrl.trim().removeSuffix("/")
                val apiKey = SupabaseConfig.supabaseAnonKey.trim()

                if (baseUrl.isNotEmpty() && apiKey.isNotEmpty()) {
                    val authHeader = UserSessionManager.getAuthHeader(context)
                    val deletedPrefs = context?.getSharedPreferences("qivo_deleted_chats", Context.MODE_PRIVATE)

                    // Primary query: targeted PostgREST filter for conversations between u1 and u2
                    val primaryUrl = "$baseUrl/rest/v1/messages?or=(and(sender_id.eq.$u1,receiver_id.eq.$u2),and(sender_id.eq.$u2,receiver_id.eq.$u1))&order=created_at.desc&limit=$limit&offset=$offset"
                    
                    // Fallback query in case complex nested filter is not supported by backend schema
                    val fallbackUrl = "$baseUrl/rest/v1/messages?or=(sender_id.eq.$u1,receiver_id.eq.$u1)&order=created_at.desc&limit=${maxOf(100, offset + limit)}"

                    val urlsToTry = listOf(primaryUrl, fallbackUrl)

                    for ((idx, url) in urlsToTry.withIndex()) {
                        try {
                            val request = Request.Builder()
                                .url(url)
                                .addHeader("apikey", apiKey)
                                .addHeader("Authorization", authHeader)
                                .get()
                                .build()

                            client.newCall(request).execute().use { response ->
                                val responseBody = response.body?.string() ?: ""
                                if (response.isSuccessful && responseBody.startsWith("[")) {
                                    val jsonArray = JSONArray(responseBody)
                                    val results = mutableListOf<ChatMessage>()
                                    for (i in 0 until jsonArray.length()) {
                                        val obj = jsonArray.getJSONObject(i)
                                        val sId = obj.optString("sender_id", "").trim()
                                        val rId = obj.optString("receiver_id", "").trim()
                                        val createdAtStr = obj.optString("created_at", "")

                                        val isPair = (sId.equals(u1, ignoreCase = true) && rId.equals(u2, ignoreCase = true)) ||
                                                     (sId.equals(u2, ignoreCase = true) && rId.equals(u1, ignoreCase = true))
                                        if (!isPair) continue

                                        val partnerId = if (sId.equals(u1, ignoreCase = true)) rId else sId
                                        if (profileService.isUserBlocked(u1, partnerId, context)) continue

                                        val cutoff = deletedPrefs?.getLong("${u1}_${partnerId}", 0L) ?: 0L
                                        if (cutoff > 0L) {
                                            val ts = parseTimestampToMillis(createdAtStr)
                                            if (ts in 1..cutoff) continue
                                        }

                                        results.add(
                                            ChatMessage(
                                                id = obj.optLong("id", 0L),
                                                senderId = sId,
                                                senderName = obj.optString("sender_name", "User"),
                                                senderAvatar = obj.optString("sender_avatar", ""),
                                                receiverId = rId,
                                                receiverName = obj.optString("receiver_name", "User"),
                                                message = obj.optString("message", ""),
                                                createdAt = createdAtStr,
                                                isRead = obj.optBoolean("is_read", false)
                                            )
                                        )
                                    }

                                    if (idx == 0) {
                                        return@withContext results
                                    } else {
                                        return@withContext results.drop(offset).take(limit)
                                    }
                                }
                            }
                        } catch (e: Exception) {
                            if (idx == urlsToTry.lastIndex) throw e
                        }
                    }
                }
            } catch (e: Exception) {
                android.util.Log.e("SupabaseChatService", "fetchConversationMessages remote error: ${e.message}")
            }

            // Offline or cache fallback
            val cachedAll = getInMemoryMessages(u1).ifEmpty {
                if (context != null) AppDataCacheManager.getCachedChatMessagesSync(context, u1) else emptyList()
            }
            val conversationMsgs = cachedAll.filter { msg ->
                (msg.senderId.trim().equals(u1, ignoreCase = true) && msg.receiverId.trim().equals(u2, ignoreCase = true)) ||
                (msg.receiverId.trim().equals(u1, ignoreCase = true) && msg.senderId.trim().equals(u2, ignoreCase = true))
            }
            conversationMsgs.drop(offset).take(limit)
        }
    }

    /**
     * Fetch unread message count specifically for a user
     */
    suspend fun fetchUnreadCount(userId: String, context: Context? = null): Int {
        if (userId.isBlank()) return 0
        return withContext(Dispatchers.IO) {
            try {
                val msgs = fetchUserMessages(userId, context)
                val unread = msgs.count { !it.isRead && it.receiverId.trim().equals(userId.trim(), ignoreCase = true) }
                _totalUnreadCount.value = unread
                unread
            } catch (e: Exception) {
                0
            }
        }
    }

    fun parseTimestampToMillis(timestampStr: String): Long {
        if (timestampStr.isBlank()) return 0L
        val clean = timestampStr.trim()

        // 1. Try Java 8 Time (Instant / OffsetDateTime)
        try {
            return java.time.OffsetDateTime.parse(clean).toInstant().toEpochMilli()
        } catch (_: Throwable) {}

        try {
            return java.time.Instant.parse(clean).toEpochMilli()
        } catch (_: Throwable) {}

        try {
            return java.time.LocalDateTime.parse(clean).atZone(java.time.ZoneOffset.UTC).toInstant().toEpochMilli()
        } catch (_: Throwable) {}

        // 2. Try SimpleDateFormat patterns
        val patterns = listOf(
            "yyyy-MM-dd'T'HH:mm:ss.SSSSSSXXX",
            "yyyy-MM-dd'T'HH:mm:ss.SSSSSSZ",
            "yyyy-MM-dd'T'HH:mm:ss.SSSXXX",
            "yyyy-MM-dd'T'HH:mm:ss.SSS'Z'",
            "yyyy-MM-dd'T'HH:mm:ssXXX",
            "yyyy-MM-dd'T'HH:mm:ss'Z'",
            "yyyy-MM-dd'T'HH:mm:ss.SSS",
            "yyyy-MM-dd'T'HH:mm:ss",
            "yyyy-MM-dd HH:mm:ss",
            "yyyy-MM-dd"
        )
        for (pattern in patterns) {
            try {
                val fmt = SimpleDateFormat(pattern, Locale.US).apply {
                    timeZone = java.util.TimeZone.getTimeZone("UTC")
                }
                val date = fmt.parse(clean)
                if (date != null) return date.time
            } catch (_: Exception) {}
        }

        // 3. Fallback: normalize subseconds
        try {
            val normalized = clean.replace(Regex("\\.\\d+"), "")
            val fmt = SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss", Locale.US).apply {
                timeZone = java.util.TimeZone.getTimeZone("UTC")
            }
            val date = fmt.parse(normalized)
            if (date != null) return date.time
        } catch (_: Exception) {}

        return 0L
    }
}

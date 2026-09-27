package com.example.data

import android.content.Context
import android.content.SharedPreferences
import android.util.Log
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import org.json.JSONArray
import org.json.JSONObject

/**
 * Manages "View Once" photo state for conversations with persistent Supabase backend sync.
 * Rules:
 * 1. The sender cannot open or view the photo once sent.
 * 2. When the recipient opens the photo, it is displayed once and closed permanently.
 * 3. Viewed state is stored in Supabase so logging out, clearing cache, or re-logging in will never re-open the photo.
 */
object ViewOncePhotoManager {
    private const val TAG = "ViewOncePhotoManager"
    private const val PREFS_NAME = "qivo_view_once_photos_prefs"
    private const val KEY_VIEWED_SET = "viewed_photo_keys"

    private val _viewedKeysFlow = MutableStateFlow<Set<String>>(emptySet())
    val viewedKeysFlow: StateFlow<Set<String>> = _viewedKeysFlow.asStateFlow()

    private var lastLoadedUserId: String = ""
    private val client = OkHttpClient()
    private val scope = CoroutineScope(Dispatchers.IO + SupervisorJob())

    private fun getPrefs(context: Context): SharedPreferences {
        return context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
    }

    /**
     * Initializes the manager by reading local prefs and fetching server-side viewed photos from Supabase.
     */
    fun init(context: Context, forceRefresh: Boolean = false) {
        val session = UserSessionManager.getSession(context)
        val currentUserId = session?.userId ?: ""

        if (forceRefresh || currentUserId != lastLoadedUserId || _viewedKeysFlow.value.isEmpty()) {
            lastLoadedUserId = currentUserId
            val prefs = getPrefs(context)
            val stored = prefs.getStringSet(KEY_VIEWED_SET, emptySet()) ?: emptySet()
            _viewedKeysFlow.value = HashSet(stored)

            // Fetch latest from Supabase in background
            scope.launch {
                syncWithSupabase(context)
            }
        }
    }

    /**
     * Generate deterministic candidate keys for a photo message so lookups succeed regardless of
     * whether referenced by message ID, URL filename, or full hash.
     */
    fun generateCandidateKeys(msgId: Long, photoPayload: String): List<String> {
        val cleanPayload = photoPayload
            .removePrefix("[image]")
            .removePrefix("[photo]")
            .trim()

        val keys = mutableListOf<String>()

        if (msgId > 0L) {
            keys.add("view_once_id_${msgId}")
        }

        if (cleanPayload.isNotBlank()) {
            if (cleanPayload.startsWith("http")) {
                val fileName = cleanPayload.substringAfterLast("/").substringBefore("?")
                if (fileName.isNotBlank()) {
                    keys.add("view_once_file_${fileName}")
                }
            }
            keys.add("view_once_hash_${cleanPayload.hashCode()}")
        }

        return keys.distinct()
    }

    /**
     * Legacy single key generator for backwards compatibility.
     */
    fun generatePhotoKey(msgId: Long, senderId: String, createdAt: String, photoPayload: String): String {
        val candidates = generateCandidateKeys(msgId, photoPayload)
        return candidates.firstOrNull() ?: if (msgId != 0L) "view_once_id_${msgId}" else "view_once_${photoPayload.hashCode()}"
    }

    /**
     * Check if a photo message has already been viewed.
     */
    fun isPhotoViewed(context: Context, msgId: Long, photoPayload: String): Boolean {
        init(context)
        val candidates = generateCandidateKeys(msgId, photoPayload)
        val viewed = _viewedKeysFlow.value
        return candidates.any { viewed.contains(it) }
    }

    /**
     * Overload checking a raw photoKey.
     */
    fun isPhotoViewed(context: Context, photoKey: String): Boolean {
        init(context)
        return _viewedKeysFlow.value.contains(photoKey)
    }

    /**
     * Syncs viewed state with Supabase table `viewed_photos`.
     */
    suspend fun syncWithSupabase(context: Context) {
        try {
            val session = UserSessionManager.getSession(context)
            val userId = session?.userId ?: ""
            val baseUrl = SupabaseConfig.supabaseUrl.trim().removeSuffix("/")
            val apiKey = SupabaseConfig.supabaseAnonKey.trim()
            if (baseUrl.isBlank() || apiKey.isBlank()) return

            // Query viewed photos from Supabase.
            // If user_id is available, check for user_id or global photo records
            val url = if (userId.isNotBlank()) {
                "$baseUrl/rest/v1/viewed_photos?select=photo_key"
            } else {
                "$baseUrl/rest/v1/viewed_photos?select=photo_key&limit=500"
            }

            val request = Request.Builder()
                .url(url)
                .addHeader("apikey", apiKey)
                .addHeader("Authorization", "Bearer $apiKey")
                .get()
                .build()

            client.newCall(request).execute().use { response ->
                if (response.isSuccessful) {
                    val bodyStr = response.body?.string() ?: return@use
                    val jsonArray = JSONArray(bodyStr)
                    val remoteKeys = mutableSetOf<String>()
                    for (i in 0 until jsonArray.length()) {
                        val obj = jsonArray.getJSONObject(i)
                        val key = obj.optString("photo_key", "")
                        if (key.isNotBlank()) {
                            remoteKeys.add(key)
                        }
                    }

                    if (remoteKeys.isNotEmpty()) {
                        val current = HashSet(_viewedKeysFlow.value)
                        if (current.addAll(remoteKeys)) {
                            _viewedKeysFlow.value = current
                            getPrefs(context).edit().putStringSet(KEY_VIEWED_SET, current).apply()
                        }
                    }
                }
            }
        } catch (e: Exception) {
            Log.d(TAG, "syncWithSupabase error: ${e.message}")
        }
    }

    /**
     * Syncs specific photo messages for the open conversation to make sure any viewed state is up to date immediately.
     */
    fun syncConversationPhotos(context: Context, messages: List<ChatMessage>) {
        val allKeys = messages.flatMap { msg ->
            if (msg.message.startsWith("[image]") || msg.message.startsWith("[photo]") || msg.message.contains("/photos/")) {
                generateCandidateKeys(msg.id, msg.message)
            } else {
                emptyList()
            }
        }.distinct()

        if (allKeys.isEmpty()) return

        scope.launch {
            try {
                val baseUrl = SupabaseConfig.supabaseUrl.trim().removeSuffix("/")
                val apiKey = SupabaseConfig.supabaseAnonKey.trim()
                if (baseUrl.isBlank() || apiKey.isBlank()) return@launch

                val joinedKeys = allKeys.joinToString(",")
                val url = "$baseUrl/rest/v1/viewed_photos?photo_key=in.($joinedKeys)&select=photo_key"

                val request = Request.Builder()
                    .url(url)
                    .addHeader("apikey", apiKey)
                    .addHeader("Authorization", "Bearer $apiKey")
                    .get()
                    .build()

                client.newCall(request).execute().use { response ->
                    if (response.isSuccessful) {
                        val bodyStr = response.body?.string() ?: return@use
                        val jsonArray = JSONArray(bodyStr)
                        val remoteKeys = mutableSetOf<String>()
                        for (i in 0 until jsonArray.length()) {
                            val obj = jsonArray.getJSONObject(i)
                            val key = obj.optString("photo_key", "")
                            if (key.isNotBlank()) {
                                remoteKeys.add(key)
                            }
                        }

                        if (remoteKeys.isNotEmpty()) {
                            val current = HashSet(_viewedKeysFlow.value)
                            if (current.addAll(remoteKeys)) {
                                _viewedKeysFlow.value = current
                                getPrefs(context).edit().putStringSet(KEY_VIEWED_SET, current).apply()
                            }
                        }
                    }
                }
            } catch (e: Exception) {
                Log.d(TAG, "syncConversationPhotos error: ${e.message}")
            }
        }
    }

    /**
     * Mark a photo message as viewed and closed permanently.
     * Persists locally to SharedPreferences and syncs to Supabase `viewed_photos` table and RPC.
     */
    fun markPhotoAsViewed(context: Context, msgId: Long, photoPayload: String) {
        init(context)
        val candidates = generateCandidateKeys(msgId, photoPayload)
        if (candidates.isEmpty()) return

        val current = HashSet(_viewedKeysFlow.value)
        var changed = false
        for (k in candidates) {
            if (current.add(k)) {
                changed = true
            }
        }

        if (changed) {
            _viewedKeysFlow.value = current
            getPrefs(context).edit().putStringSet(KEY_VIEWED_SET, current).apply()
        }

        // Always sync all candidate keys to Supabase
        scope.launch {
            try {
                val session = UserSessionManager.getSession(context)
                val userId = session?.userId ?: ""
                val baseUrl = SupabaseConfig.supabaseUrl.trim().removeSuffix("/")
                val apiKey = SupabaseConfig.supabaseAnonKey.trim()
                if (baseUrl.isBlank() || apiKey.isBlank()) return@launch

                val mediaType = "application/json; charset=utf-8".toMediaType()

                for (key in candidates) {
                    // 1. Try RPC call
                    try {
                        val rpcUrl = "$baseUrl/rest/v1/rpc/mark_photo_viewed"
                        val rpcPayload = JSONObject().apply {
                            put("p_user_id", userId)
                            put("p_photo_key", key)
                        }.toString()

                        val rpcReq = Request.Builder()
                            .url(rpcUrl)
                            .addHeader("apikey", apiKey)
                            .addHeader("Authorization", "Bearer $apiKey")
                            .addHeader("Content-Type", "application/json")
                            .post(rpcPayload.toRequestBody(mediaType))
                            .build()

                        client.newCall(rpcReq).execute().close()
                    } catch (_: Exception) {}

                    // 2. Direct REST insert as fallback/complement
                    try {
                        val restUrl = "$baseUrl/rest/v1/viewed_photos"
                        val restPayload = JSONObject().apply {
                            put("user_id", userId)
                            put("photo_key", key)
                        }.toString()

                        val restReq = Request.Builder()
                            .url(restUrl)
                            .addHeader("apikey", apiKey)
                            .addHeader("Authorization", "Bearer $apiKey")
                            .addHeader("Content-Type", "application/json")
                            .addHeader("Prefer", "resolution=merge-duplicates")
                            .post(restPayload.toRequestBody(mediaType))
                            .build()

                        client.newCall(restReq).execute().close()
                    } catch (_: Exception) {}
                }
            } catch (e: Exception) {
                Log.d(TAG, "markPhotoAsViewed remote sync error: ${e.message}")
            }
        }
    }

    /**
     * Backwards-compatible overload with single photoKey.
     */
    fun markPhotoAsViewed(context: Context, photoKey: String) {
        if (photoKey.isBlank()) return
        init(context)

        val current = HashSet(_viewedKeysFlow.value)
        if (current.add(photoKey)) {
            _viewedKeysFlow.value = current
            getPrefs(context).edit().putStringSet(KEY_VIEWED_SET, current).apply()
        }

        scope.launch {
            try {
                val session = UserSessionManager.getSession(context)
                val userId = session?.userId ?: ""
                val baseUrl = SupabaseConfig.supabaseUrl.trim().removeSuffix("/")
                val apiKey = SupabaseConfig.supabaseAnonKey.trim()
                if (baseUrl.isBlank() || apiKey.isBlank()) return@launch

                val mediaType = "application/json; charset=utf-8".toMediaType()
                val rpcUrl = "$baseUrl/rest/v1/rpc/mark_photo_viewed"
                val rpcPayload = JSONObject().apply {
                    put("p_user_id", userId)
                    put("p_photo_key", photoKey)
                }.toString()

                val rpcReq = Request.Builder()
                    .url(rpcUrl)
                    .addHeader("apikey", apiKey)
                    .addHeader("Authorization", "Bearer $apiKey")
                    .addHeader("Content-Type", "application/json")
                    .post(rpcPayload.toRequestBody(mediaType))
                    .build()

                client.newCall(rpcReq).execute().close()

                val restUrl = "$baseUrl/rest/v1/viewed_photos"
                val restPayload = JSONObject().apply {
                    put("user_id", userId)
                    put("photo_key", photoKey)
                }.toString()

                val restReq = Request.Builder()
                    .url(restUrl)
                    .addHeader("apikey", apiKey)
                    .addHeader("Authorization", "Bearer $apiKey")
                    .addHeader("Content-Type", "application/json")
                    .addHeader("Prefer", "resolution=merge-duplicates")
                    .post(restPayload.toRequestBody(mediaType))
                    .build()

                client.newCall(restReq).execute().close()
            } catch (_: Exception) {}
        }
    }
}

package com.example.data

import android.content.Context
import android.content.SharedPreferences
import android.util.Log
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONObject

/**
 * Robust On-Device Cache Engine for QIVO.
 * 
 * Supports offline-first data presentation:
 * - When offline: Instantly serves cached profiles, party rooms, and user details
 * - When online: Seamlessly streams and updates fresh real-time data to disk
 */
object AppDataCacheManager {
    private const val TAG = "AppDataCacheManager"
    private const val PREFS_NAME = "qivo_app_data_cache"
    private const val KEY_PROFILES_PREFIX = "cached_profiles_"
    private const val KEY_PARTY_ROOMS = "cached_party_rooms"
    private const val KEY_USER_DETAIL_PREFIX = "cached_user_detail_"
    private const val KEY_CHAT_MESSAGES_PREFIX = "cached_chat_msgs_"

    private fun getPrefs(context: Context): SharedPreferences {
        return context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
    }

    // =========================================================================
    // PROFILES CACHING
    // =========================================================================

    suspend fun saveProfilesCache(
        context: Context,
        profiles: List<UserProfile>,
        category: String = "home"
    ) = withContext(Dispatchers.IO) {
        if (profiles.isEmpty()) return@withContext
        try {
            val jsonArray = JSONArray()
            profiles.take(50).forEach { profile ->
                val obj = JSONObject().apply {
                    put("id", profile.id)
                    put("numeric_id", profile.numericId)
                    put("email", profile.email)
                    put("name", profile.name)
                    put("gender", profile.gender)
                    put("birth_date", profile.birthDate)
                    put("country", profile.country)
                    put("avatar_url", profile.avatarUrl)
                    put("coins", profile.coins)
                    put("diamonds", profile.diamonds)
                    put("is_admin", profile.isAdmin)
                    put("is_coinseller", profile.isCoinSeller)
                    put("is_agent", profile.isAgent)
                    put("is_online", profile.isOnline)
                    put("social_preferences", profile.socialPreferences)
                    put("occupation", profile.occupation)
                    put("languages", profile.languages)
                    put("hobbies_interests", profile.hobbiesInterests)
                }
                jsonArray.put(obj)
            }
            val encryptedPayload = AppKeyStoreHelper.encrypt(jsonArray.toString())
            getPrefs(context).edit().putString(KEY_PROFILES_PREFIX + category, encryptedPayload).apply()
            Log.d(TAG, "Securely cached ${profiles.size} profiles for category: $category")
        } catch (e: Exception) {
            Log.e(TAG, "Failed to save profiles cache: ${e.message}")
        }
    }

    suspend fun getCachedProfiles(
        context: Context,
        category: String = "home"
    ): List<UserProfile> = withContext(Dispatchers.IO) {
        val list = mutableListOf<UserProfile>()
        try {
            val stored = getPrefs(context).getString(KEY_PROFILES_PREFIX + category, null) ?: return@withContext emptyList()
            val jsonStr = AppKeyStoreHelper.decrypt(stored)
            if (jsonStr.isBlank()) return@withContext emptyList()
            val jsonArray = JSONArray(jsonStr)
            for (i in 0 until jsonArray.length()) {
                val obj = jsonArray.getJSONObject(i)
                list.add(
                    UserProfile(
                        id = obj.optString("id", ""),
                        numericId = obj.optLong("numeric_id", 0L),
                        email = obj.optString("email", ""),
                        name = obj.optString("name", "User"),
                        gender = obj.optString("gender", "Female"),
                        birthDate = obj.optString("birth_date", "2000-01-01"),
                        country = obj.optString("country", "United States"),
                        avatarUrl = obj.optString("avatar_url", ""),
                        coins = obj.optLong("coins", 0L),
                        diamonds = obj.optLong("diamonds", 0L),
                        isAdmin = obj.optBoolean("is_admin", false),
                        isCoinSeller = obj.optBoolean("is_coinseller", false),
                        isAgent = obj.optBoolean("is_agent", false),
                        isOnline = obj.optBoolean("is_online", false),
                        socialPreferences = obj.optString("social_preferences", "Friendship & Discovery"),
                        occupation = obj.optString("occupation", "Choose"),
                        languages = obj.optString("languages", "Choose"),
                        hobbiesInterests = obj.optString("hobbies_interests", "Choose")
                    )
                )
            }
        } catch (e: Exception) {
            Log.e(TAG, "Failed to read cached profiles: ${e.message}")
        }
        list
    }

    // =========================================================================
    // PARTY ROOMS CACHING
    // =========================================================================

    suspend fun savePartyRoomsCache(
        context: Context,
        rooms: List<PartyRoom>
    ) = withContext(Dispatchers.IO) {
        try {
            val jsonArray = JSONArray()
            rooms.take(40).forEach { room ->
                val obj = JSONObject().apply {
                    put("id", room.id)
                    put("room_number", room.roomNumber)
                    put("name", room.name)
                    put("description", room.description)
                    put("category", room.category)
                    put("cover_url", room.coverUrl)
                    put("bg_url", room.bgUrl)
                    put("host_user_id", room.hostUserId)
                    put("host_name", room.hostName)
                    put("host_avatar_url", room.hostAvatarUrl)
                    put("seats_count", room.seatsCount)
                    put("is_locked", room.isLocked)
                    put("online_count", room.onlineCount)
                    put("created_at", room.createdAt)
                }
                jsonArray.put(obj)
            }
            getPrefs(context).edit().putString(KEY_PARTY_ROOMS, jsonArray.toString()).apply()
            Log.d(TAG, "Cached ${rooms.size} party rooms")
        } catch (e: Exception) {
            Log.e(TAG, "Failed to save party rooms cache: ${e.message}")
        }
    }

    suspend fun removePartyRoomFromCache(
        context: Context,
        roomId: String
    ) = withContext(Dispatchers.IO) {
        if (roomId.isBlank()) return@withContext
        try {
            val currentList = getCachedPartyRooms(context).toMutableList()
            val filtered = currentList.filter { it.id != roomId }
            savePartyRoomsCache(context, filtered)
            Log.d(TAG, "Removed room $roomId from party rooms cache, remaining: ${filtered.size}")
        } catch (e: Exception) {
            Log.e(TAG, "Failed to remove room from cache: ${e.message}")
        }
    }

    suspend fun getCachedPartyRooms(
        context: Context
    ): List<PartyRoom> = withContext(Dispatchers.IO) {
        val list = mutableListOf<PartyRoom>()
        try {
            val jsonStr = getPrefs(context).getString(KEY_PARTY_ROOMS, null) ?: return@withContext emptyList()
            val jsonArray = JSONArray(jsonStr)
            for (i in 0 until jsonArray.length()) {
                val obj = jsonArray.getJSONObject(i)
                list.add(
                    PartyRoom(
                        id = obj.optString("id", ""),
                        roomNumber = obj.optLong("room_number", 0L),
                        name = obj.optString("name", "Party Lounge"),
                        description = obj.optString("description", ""),
                        category = obj.optString("category", "Chat"),
                        coverUrl = obj.optString("cover_url", ""),
                        bgUrl = obj.optString("bg_url", ""),
                        hostUserId = obj.optString("host_user_id", ""),
                        hostName = obj.optString("host_name", "Host"),
                        hostAvatarUrl = obj.optString("host_avatar_url", ""),
                        seatsCount = obj.optInt("seats_count", 8),
                        isLocked = obj.optBoolean("is_locked", false),
                        onlineCount = obj.optInt("online_count", 1),
                        createdAt = obj.optString("created_at", "")
                    )
                )
            }
        } catch (e: Exception) {
            Log.e(TAG, "Failed to read cached party rooms: ${e.message}")
        }
        list
    }

    fun getCachedPartyRoomsSync(
        context: Context
    ): List<PartyRoom> {
        val list = mutableListOf<PartyRoom>()
        try {
            val jsonStr = getPrefs(context).getString(KEY_PARTY_ROOMS, null) ?: return emptyList()
            val jsonArray = JSONArray(jsonStr)
            for (i in 0 until jsonArray.length()) {
                val obj = jsonArray.getJSONObject(i)
                list.add(
                    PartyRoom(
                        id = obj.optString("id", ""),
                        roomNumber = obj.optLong("room_number", 0L),
                        name = obj.optString("name", "Party Lounge"),
                        description = obj.optString("description", ""),
                        category = obj.optString("category", "Chat"),
                        coverUrl = obj.optString("cover_url", ""),
                        bgUrl = obj.optString("bg_url", ""),
                        hostUserId = obj.optString("host_user_id", ""),
                        hostName = obj.optString("host_name", "Host"),
                        hostAvatarUrl = obj.optString("host_avatar_url", ""),
                        seatsCount = obj.optInt("seats_count", 8),
                        isLocked = obj.optBoolean("is_locked", false),
                        onlineCount = obj.optInt("online_count", 1),
                        createdAt = obj.optString("created_at", "")
                    )
                )
            }
        } catch (e: Exception) {
            Log.e(TAG, "Failed to read cached party rooms synchronously: ${e.message}")
        }
        return list
    }

    // =========================================================================
    // INDIVIDUAL USER DETAIL CACHING
    // =========================================================================

    suspend fun saveUserDetailCache(
        context: Context,
        profile: UserProfile
    ) = withContext(Dispatchers.IO) {
        if (profile.id.isBlank()) return@withContext
        try {
            val obj = JSONObject().apply {
                put("id", profile.id)
                put("numeric_id", profile.numericId)
                put("email", profile.email)
                put("name", profile.name)
                put("gender", profile.gender)
                put("birth_date", profile.birthDate)
                put("country", profile.country)
                put("avatar_url", profile.avatarUrl)
                put("coins", profile.coins)
                put("diamonds", profile.diamonds)
                put("is_admin", profile.isAdmin)
                put("is_coinseller", profile.isCoinSeller)
                put("is_agent", profile.isAgent)
                put("is_online", profile.isOnline)
                put("social_preferences", profile.socialPreferences)
                put("exercise", profile.exercise)
                put("education", profile.education)
                put("height", profile.height)
                put("relationship_status", profile.relationshipStatus)
                put("occupation", profile.occupation)
                put("languages", profile.languages)
                put("hobbies_interests", profile.hobbiesInterests)
                put("music_preference", profile.musicPreference)
                put("dietary_habit", profile.dietaryHabit)
                put("sleep_habit", profile.sleepHabit)
                put("smoking", profile.smoking)
                put("liquor", profile.liquor)
                put("superpower", profile.superpower)
                put("pets", profile.pets)
                put("personality_type", profile.personalityType)
                put("horoscopes", profile.horoscopes)
                val albumArr = JSONArray()
                profile.albumPhotos.forEach { albumArr.put(it) }
                put("album_photos", albumArr)
            }
            val encryptedPayload = AppKeyStoreHelper.encrypt(obj.toString())
            val editor = getPrefs(context).edit()
            if (profile.id.isNotBlank()) {
                editor.putString(KEY_USER_DETAIL_PREFIX + profile.id, encryptedPayload)
            }
            if (profile.numericId > 0L) {
                editor.putString(KEY_USER_DETAIL_PREFIX + "num_" + profile.numericId, encryptedPayload)
            }
            if (profile.email.isNotBlank()) {
                editor.putString(KEY_USER_DETAIL_PREFIX + "email_" + profile.email.lowercase().trim(), encryptedPayload)
            }
            editor.apply()
        } catch (e: Exception) {
            Log.e(TAG, "Failed to cache user detail: ${e.message}")
        }
    }

    suspend fun getCachedUserDetail(
        context: Context,
        userId: String
    ): UserProfile? = withContext(Dispatchers.IO) {
        val cleanId = userId.trim()
        if (cleanId.isBlank()) return@withContext null
        try {
            val prefs = getPrefs(context)
            val stored = prefs.getString(KEY_USER_DETAIL_PREFIX + cleanId, null)
                ?: prefs.getString(KEY_USER_DETAIL_PREFIX + "num_" + cleanId, null)
                ?: prefs.getString(KEY_USER_DETAIL_PREFIX + "email_" + cleanId.lowercase(), null)
                ?: return@withContext null
            val jsonStr = AppKeyStoreHelper.decrypt(stored)
            if (jsonStr.isBlank()) return@withContext null
            val obj = JSONObject(jsonStr)
            val albumList = mutableListOf<String>()
            val albumArr = obj.optJSONArray("album_photos")
            if (albumArr != null) {
                for (i in 0 until albumArr.length()) {
                    val url = albumArr.optString(i, "")
                    if (url.isNotBlank()) albumList.add(url)
                }
            }
            return@withContext UserProfile(
                id = obj.optString("id", cleanId),
                numericId = obj.optLong("numeric_id", 0L),
                email = obj.optString("email", ""),
                name = obj.optString("name", "User"),
                gender = obj.optString("gender", "Female"),
                birthDate = obj.optString("birth_date", "2000-01-01"),
                country = obj.optString("country", "United States"),
                avatarUrl = obj.optString("avatar_url", ""),
                coins = obj.optLong("coins", 0L),
                diamonds = obj.optLong("diamonds", 0L),
                isAdmin = obj.optBoolean("is_admin", false),
                isCoinSeller = obj.optBoolean("is_coinseller", false),
                isAgent = obj.optBoolean("is_agent", false),
                isOnline = obj.optBoolean("is_online", false),
                socialPreferences = obj.optString("social_preferences", "Friendship & Discovery"),
                exercise = obj.optString("exercise", "Choose"),
                education = obj.optString("education", "Choose"),
                height = obj.optString("height", "Choose"),
                relationshipStatus = obj.optString("relationship_status", "Choose"),
                occupation = obj.optString("occupation", "Choose"),
                languages = obj.optString("languages", "Choose"),
                hobbiesInterests = obj.optString("hobbies_interests", "Choose"),
                musicPreference = obj.optString("music_preference", "Choose"),
                dietaryHabit = obj.optString("dietary_habit", "Choose"),
                sleepHabit = obj.optString("sleep_habit", "Choose"),
                smoking = obj.optString("smoking", "Choose"),
                liquor = obj.optString("liquor", "Choose"),
                superpower = obj.optString("superpower", "Choose"),
                pets = obj.optString("pets", "Choose"),
                personalityType = obj.optString("personality_type", "Choose"),
                horoscopes = obj.optString("horoscopes", "Choose"),
                albumPhotos = albumList
            )
        } catch (e: Exception) {
            Log.e(TAG, "Failed to read cached user detail: ${e.message}")
            null
        }
    }

    // =========================================================================
    // CONVERSATION DRAFT MESSAGE PERSISTENCE
    // =========================================================================

    fun saveChatDraft(context: Context, myUserId: String, partnerId: String, draftText: String) {
        if (myUserId.isBlank() || partnerId.isBlank()) return
        try {
            val key = "chat_draft_${myUserId.trim()}_${partnerId.trim()}"
            if (draftText.isBlank()) {
                getPrefs(context).edit().remove(key).apply()
            } else {
                getPrefs(context).edit().putString(key, draftText).apply()
            }
        } catch (e: Exception) {
            Log.e(TAG, "Failed to save chat draft: ${e.message}")
        }
    }

    fun getChatDraft(context: Context, myUserId: String, partnerId: String): String {
        if (myUserId.isBlank() || partnerId.isBlank()) return ""
        return try {
            val key = "chat_draft_${myUserId.trim()}_${partnerId.trim()}"
            getPrefs(context).getString(key, "") ?: ""
        } catch (e: Exception) {
            Log.e(TAG, "Failed to get chat draft: ${e.message}")
            ""
        }
    }

    fun getAllDraftPartners(context: Context, myUserId: String): List<String> {
        if (myUserId.isBlank()) return emptyList()
        val list = mutableListOf<String>()
        try {
            val prefix = "chat_draft_${myUserId.trim()}_"
            val allPrefs = getPrefs(context).all
            for (key in allPrefs.keys) {
                if (key.startsWith(prefix)) {
                    val partnerId = key.substring(prefix.length)
                    if (partnerId.isNotBlank()) {
                        val value = allPrefs[key] as? String
                        if (!value.isNullOrBlank()) {
                            list.add(partnerId)
                        }
                    }
                }
            }
        } catch (e: Exception) {
            Log.e(TAG, "Failed to get all draft partners: ${e.message}")
        }
        return list
    }

    fun clearChatCacheOnStartup(context: Context) {
        try {
            val prefs = getPrefs(context)
            val editor = prefs.edit()
            val keys = prefs.all.keys
            var clearedCount = 0
            for (key in keys) {
                if (key.startsWith(KEY_CHAT_MESSAGES_PREFIX)) {
                    editor.remove(key)
                    clearedCount++
                }
            }
            editor.apply()
            Log.d(TAG, "Cleared $clearedCount chat persistent caches at startup (preserving loaded profiles).")
        } catch (e: Exception) {
            Log.e(TAG, "Failed to clear persistent chat caches on startup: ${e.message}")
        }
    }

    // Sync profiles retrieval for instant UI mounting without flicker
    fun getCachedProfilesSync(context: Context, category: String = "home"): List<UserProfile> {
        val list = mutableListOf<UserProfile>()
        try {
            val stored = getPrefs(context).getString(KEY_PROFILES_PREFIX + category, null) ?: return emptyList()
            val jsonStr = AppKeyStoreHelper.decrypt(stored)
            if (jsonStr.isBlank()) return emptyList()
            val jsonArray = JSONArray(jsonStr)
            for (i in 0 until jsonArray.length()) {
                val obj = jsonArray.getJSONObject(i)
                list.add(
                    UserProfile(
                        id = obj.optString("id", ""),
                        numericId = obj.optLong("numeric_id", 0L),
                        email = obj.optString("email", ""),
                        name = obj.optString("name", "User"),
                        gender = obj.optString("gender", "Female"),
                        birthDate = obj.optString("birth_date", "2000-01-01"),
                        country = obj.optString("country", "United States"),
                        avatarUrl = obj.optString("avatar_url", ""),
                        coins = obj.optLong("coins", 0L),
                        diamonds = obj.optLong("diamonds", 0L),
                        isAdmin = obj.optBoolean("is_admin", false),
                        isCoinSeller = obj.optBoolean("is_coinseller", false),
                        isAgent = obj.optBoolean("is_agent", false),
                        isOnline = obj.optBoolean("is_online", false),
                        socialPreferences = obj.optString("social_preferences", "Friendship & Discovery")
                    )
                )
            }
        } catch (e: Exception) {
            Log.e(TAG, "Failed to read cached profiles sync: ${e.message}")
        }
        return list
    }

    // =========================================================================
    // CHAT MESSAGES CACHING
    // =========================================================================

    suspend fun saveChatMessagesCache(
        context: Context,
        userId: String,
        messages: List<ChatMessage>
    ) = withContext(Dispatchers.IO) {
        if (userId.isBlank()) return@withContext
        try {
            val jsonArray = JSONArray()
            messages.take(300).forEach { msg ->
                val obj = JSONObject().apply {
                    put("id", msg.id)
                    put("sender_id", msg.senderId)
                    put("sender_name", msg.senderName)
                    put("sender_avatar", msg.senderAvatar)
                    put("receiver_id", msg.receiverId)
                    put("receiver_name", msg.receiverName)
                    put("message", msg.message)
                    put("created_at", msg.createdAt)
                    put("is_read", msg.isRead)
                }
                jsonArray.put(obj)
            }
            getPrefs(context).edit().putString(KEY_CHAT_MESSAGES_PREFIX + userId.trim(), jsonArray.toString()).apply()
            Log.d(TAG, "Persistently cached ${messages.size} chat messages for user: $userId")
        } catch (e: Exception) {
            Log.e(TAG, "Failed to save chat messages cache: ${e.message}")
        }
    }

    fun getCachedChatMessagesSync(
        context: Context,
        userId: String
    ): List<ChatMessage> {
        if (userId.isBlank()) return emptyList()
        val list = mutableListOf<ChatMessage>()
        try {
            val jsonStr = getPrefs(context).getString(KEY_CHAT_MESSAGES_PREFIX + userId.trim(), null) ?: return emptyList()
            if (jsonStr.isBlank()) return emptyList()
            val jsonArray = JSONArray(jsonStr)
            for (i in 0 until jsonArray.length()) {
                val obj = jsonArray.getJSONObject(i)
                list.add(
                    ChatMessage(
                        id = obj.optLong("id", 0L),
                        senderId = obj.optString("sender_id", ""),
                        senderName = obj.optString("sender_name", "User"),
                        senderAvatar = obj.optString("sender_avatar", ""),
                        receiverId = obj.optString("receiver_id", ""),
                        receiverName = obj.optString("receiver_name", "User"),
                        message = obj.optString("message", ""),
                        createdAt = obj.optString("created_at", ""),
                        isRead = obj.optBoolean("is_read", false)
                    )
                )
            }
        } catch (e: Exception) {
            Log.e(TAG, "Failed to read cached chat messages sync: ${e.message}")
        }
        return list
    }

    suspend fun getCachedChatMessages(
        context: Context,
        userId: String
    ): List<ChatMessage> = withContext(Dispatchers.IO) {
        getCachedChatMessagesSync(context, userId)
    }
}


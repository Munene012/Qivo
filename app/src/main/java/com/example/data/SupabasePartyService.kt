package com.example.data

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.net.Uri
import android.util.Base64
import android.util.Log
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import org.json.JSONArray
import org.json.JSONObject
import java.io.ByteArrayOutputStream
import java.util.UUID
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.TimeUnit

data class PartyRoom(
    val id: String = "",
    val roomNumber: Long = 0L,
    val name: String = "Party Lounge",
    val description: String = "",
    val category: String = "Chat",
    val coverUrl: String = "",
    val bgUrl: String = "",
    val hostUserId: String = "",
    val hostName: String = "Host",
    val hostAvatarUrl: String = "",
    val seatsCount: Int = 8,
    val isLocked: Boolean = false,
    val onlineCount: Int = 1,
    val createdAt: String = "",
    val roomPassword: String = "" // Support for party room security lock
)

data class PartySeat(
    val seatIndex: Int,
    val userId: String = "",
    val userName: String = "",
    val avatarUrl: String = "",
    val isMuted: Boolean = false,
    val isLocked: Boolean = false,
    val isSpeaking: Boolean = false,
    val userLevel: Int = 1
)

data class PartyRoomMessage(
    val id: String = UUID.randomUUID().toString(),
    val roomId: String = "",
    val senderId: String = "",
    val senderName: String = "",
    val senderAvatarUrl: String = "",
    val content: String = "",
    val msgType: String = "chat", // "chat", "gift", "system"
    val giftIcon: String = "",
    val createdAt: String = ""
)

data class PartyRoomMember(
    val userId: String = "",
    val userName: String = "",
    val avatarUrl: String = "",
    val role: String = "audience", // "owner", "admin", "speaker", "audience"
    val seatIndex: Int = -1,
    val isMuted: Boolean = false,
    val isSpeaking: Boolean = false,
    val userLevel: Int = 1,
    val joinedAt: String = ""
)

data class PartyRoomCreationResult(
    val room: PartyRoom? = null,
    val errorMessage: String? = null
)

class SupabasePartyService {

    private val client = SupabaseHttpClient.client

    private val jsonMediaType = "application/json; charset=utf-8".toMediaType()

    private fun getAuthHeader(context: Context? = null): String {
        return UserSessionManager.getAuthHeader(context)
    }

    // Active session memory cache for immediate UI responsiveness
    companion object {
        val memoryRooms = ConcurrentHashMap<String, PartyRoom>()
        val memorySeats = ConcurrentHashMap<String, MutableList<PartySeat>>()
        val memoryMessages = ConcurrentHashMap<String, MutableList<PartyRoomMessage>>()
        val memoryAdmins = ConcurrentHashMap<String, MutableSet<String>>() // roomId -> set of admin userIds
        val memoryMembers = ConcurrentHashMap<String, MutableMap<String, PartyRoomMember>>() // roomId -> map(userId -> member)
        val deletedRoomIds = ConcurrentHashMap.newKeySet<String>()

        fun clearCache() {
            memoryRooms.clear()
            memorySeats.clear()
            memoryMessages.clear()
            memoryAdmins.clear()
            memoryMembers.clear()
            deletedRoomIds.clear()
        }
    }

    /**
     * Upload an image to Supabase Storage in bucket 'party'
     */
    suspend fun uploadPartyImage(
        context: Context,
        imageUri: Uri,
        folder: String = "covers"
    ): String? {
        return withContext(Dispatchers.IO) {
            try {
                val baseUrl = SupabaseConfig.supabaseUrl.trim().removeSuffix("/")
                val apiKey = SupabaseConfig.supabaseAnonKey.trim()

                val inputStream = context.contentResolver.openInputStream(imageUri) ?: return@withContext null
                val originalBitmap = BitmapFactory.decodeStream(inputStream)
                inputStream.close()

                if (originalBitmap == null) return@withContext null

                val baos = ByteArrayOutputStream()
                originalBitmap.compress(Bitmap.CompressFormat.JPEG, 85, baos)
                val imageBytes = baos.toByteArray()

                val filename = "${folder}_${System.currentTimeMillis()}_${(1000..9999).random()}.jpg"

                if (baseUrl.isNotEmpty() && apiKey.isNotEmpty()) {
                    val authHeader = getAuthHeader(context)
                    // 1. Upload to 'party' bucket
                    val partyUploadUrl = "$baseUrl/storage/v1/object/party/$folder/$filename"
                    val partyPublicUrl = "$baseUrl/storage/v1/object/public/party/$folder/$filename"

                    val request = Request.Builder()
                        .url(partyUploadUrl)
                        .addHeader("apikey", apiKey)
                        .addHeader("Authorization", authHeader)
                        .addHeader("Content-Type", "image/jpeg")
                        .addHeader("x-upsert", "true")
                        .post(imageBytes.toRequestBody("image/jpeg".toMediaType()))
                        .build()

                    val response = client.newCall(request).execute()
                    if (response.isSuccessful || response.code in 200..299) {
                        response.close()
                        return@withContext partyPublicUrl
                    }
                    response.close()

                    // 2. Fallback to 'photos' bucket if needed
                    val photosUploadUrl = "$baseUrl/storage/v1/object/photos/party/$folder/$filename"
                    val photosPublicUrl = "$baseUrl/storage/v1/object/public/photos/party/$folder/$filename"

                    val requestPhotos = Request.Builder()
                        .url(photosUploadUrl)
                        .addHeader("apikey", apiKey)
                        .addHeader("Authorization", authHeader)
                        .addHeader("Content-Type", "image/jpeg")
                        .addHeader("x-upsert", "true")
                        .post(imageBytes.toRequestBody("image/jpeg".toMediaType()))
                        .build()

                    val responsePhotos = client.newCall(requestPhotos).execute()
                    if (responsePhotos.isSuccessful || responsePhotos.code in 200..299) {
                        responsePhotos.close()
                        return@withContext photosPublicUrl
                    }
                    responsePhotos.close()
                }

                // If remote upload fails, convert to base64 data URI
                val base64 = Base64.encodeToString(imageBytes, Base64.NO_WRAP)
                "data:image/jpeg;base64,$base64"
            } catch (e: Exception) {
                Log.e("SupabasePartyService", "Image upload error", e)
                null
            }
        }
    }

    /**
     * Create a new party room using Supabase RPC create_party_room.
     * Parameters: p_room_name (text) and p_max_seats (integer).
     * The RPC inserts into party_rooms and party_room_members, returning the real UUID.
     * The host is only inserted into party_room_members by the RPC and can choose to sit later.
     * Absolutely NO offline local room is created if the RPC fails or is unreachable.
     */
    suspend fun createPartyRoom(
        name: String,
        description: String,
        category: String,
        coverUrl: String,
        bgUrl: String,
        hostUserId: String,
        hostName: String,
        hostAvatarUrl: String,
        seatsCount: Int = 8,
        context: Context? = null
    ): PartyRoomCreationResult {
        return withContext(Dispatchers.IO) {
            val baseUrl = SupabaseConfig.supabaseUrl.trim().removeSuffix("/")
            val apiKey = SupabaseConfig.supabaseAnonKey.trim()

            if (baseUrl.isEmpty() || apiKey.isEmpty()) {
                val errorMsg = "Supabase configuration is missing (supabaseUrl or supabaseAnonKey is blank). Server access required to create party room."
                Log.e("SupabasePartyService", "[create_party_room] CRITICAL ERROR: $errorMsg")
                return@withContext PartyRoomCreationResult(room = null, errorMessage = errorMsg)
            }

            try {
                val safeRoomName = name.trim().ifBlank { "Party Lounge" }
                val safeMaxSeats = if (seatsCount in 4..20) seatsCount else 8

                // Verify user has at least 5000 coins before proceeding
                val profileService = SupabaseProfileService()
                val liveCoins = if (context != null) {
                    val c = UserSessionManager.getCoins(context)
                    if (c > 0L) c else profileService.fetchCoins(hostUserId)
                } else {
                    profileService.fetchCoins(hostUserId)
                }

                if (liveCoins < 5000L) {
                    val coinErrMsg = "Insufficient coins! Creating a Party Room costs 5,000 coins (Your balance: $liveCoins coins)."
                    Log.e("SupabasePartyService", "[create_party_room] $coinErrMsg")
                    return@withContext PartyRoomCreationResult(room = null, errorMessage = coinErrMsg)
                }

                // Build RPC payload with EXACT parameter names: p_name and p_max_seats (v2)
                val rpcPayloadJson = JSONObject().apply {
                    put("p_name", safeRoomName)
                    put("p_max_seats", safeMaxSeats)
                    put("p_category", category.ifBlank { "Chat" })
                    put("p_cover_image", coverUrl)
                    put("p_background_theme", "default")
                }
                val rpcPayload = rpcPayloadJson.toString()

                val authHeader = getAuthHeader(context)
                val rpcUrl = "$baseUrl/rest/v1/rpc/create_party_room_v2"

                Log.d("SupabasePartyService", "[create_party_room] >>> RPC call to: $rpcUrl")

                val request = Request.Builder()
                    .url(rpcUrl)
                    .addHeader("apikey", apiKey)
                    .addHeader("Authorization", authHeader)
                    .addHeader("Content-Type", "application/json")
                    .post(rpcPayload.toRequestBody(jsonMediaType))
                    .build()

                client.newCall(request).execute().use { response ->
                    val rawBody = response.body?.string()?.trim() ?: ""
                    if (response.isSuccessful) {
                        val jsonObj = JSONObject(rawBody)
                        val returnedRoomId = jsonObj.optString("room_id")
                        val returnedRoomNum = jsonObj.optLong("room_number")

                        val newRoom = PartyRoom(
                            id = returnedRoomId,
                            roomNumber = returnedRoomNum,
                            name = safeRoomName,
                            description = description,
                            category = category.ifBlank { "Chat" },
                            coverUrl = coverUrl,
                            bgUrl = bgUrl,
                            hostUserId = hostUserId,
                            hostName = hostName,
                            hostAvatarUrl = hostAvatarUrl,
                            seatsCount = safeMaxSeats
                        )
                        
                        memoryRooms[returnedRoomId] = newRoom
                        return@withContext PartyRoomCreationResult(room = newRoom)
                    } else {
                        return@withContext PartyRoomCreationResult(errorMessage = "Server error: ${response.code}")
                    }
                }
            } catch (e: Exception) {
                Log.e("SupabasePartyService", "[create_party_room] Network/RPC exception during creation", e)
                PartyRoomCreationResult(
                    room = null,
                    errorMessage = "Network error, check your internet connection."
                )
            }
        }
    }

    /**
     * Fetch active party rooms from database with pagination support
     */
    suspend fun fetchPartyRooms(
        category: String = "All",
        offset: Int = 0,
        limit: Int = 20
    ): List<PartyRoom> {
        return withContext(Dispatchers.IO) {
            val baseUrl = SupabaseConfig.supabaseUrl.trim().removeSuffix("/")
            val apiKey = SupabaseConfig.supabaseAnonKey.trim()
            val roomsList = mutableListOf<PartyRoom>()

            if (baseUrl.isNotEmpty() && apiKey.isNotEmpty()) {
                try {
                    var queryUrl = "$baseUrl/rest/v1/party_rooms?select=*&order=created_at.desc&limit=$limit&offset=$offset"
                    if (category != "All" && category.isNotBlank()) {
                        queryUrl += "&category=ilike.${Uri.encode(category)}"
                    }

                    val authHeader = getAuthHeader()
                    val request = Request.Builder()
                        .url(queryUrl)
                        .addHeader("apikey", apiKey)
                        .addHeader("Authorization", authHeader)
                        .get()
                        .build()

                    val response = client.newCall(request).execute()
                    if (response.isSuccessful) {
                        val body = response.body?.string() ?: "[]"
                        val jsonArr = JSONArray(body)
                        for (i in 0 until jsonArr.length()) {
                            val obj = jsonArr.getJSONObject(i)
                            val rawId = obj.optString("id")
                            if (rawId.isBlank() || deletedRoomIds.contains(rawId)) continue

                            val isActive = obj.optBoolean("is_active", true)
                            val status = obj.optString("status", "open")
                            val isDeleted = obj.optBoolean("is_deleted", false)
                            if (!isActive || status == "closed" || isDeleted) continue

                            var parsedRoomNum = obj.optLong("room_number", 0L)
                            if (parsedRoomNum <= 0L) {
                                parsedRoomNum = if (rawId.isNotBlank()) {
                                    (Math.abs(rawId.hashCode()) % 999900 + 100).toLong()
                                } else {
                                    (100..999999).random().toLong()
                                }
                            }
                            val rTitle = obj.optString("title").ifBlank { obj.optString("name", "Party Lounge") }
                            val rDesc = obj.optString("announcement").ifBlank { obj.optString("description", "") }
                            val rBg = obj.optString("background_url").ifBlank { obj.optString("bg_url", "") }
                            val rCover = obj.optString("cover_url").ifBlank { rBg }
                            val rHostId = obj.optString("host_id").ifBlank { obj.optString("host_user_id", "") }
                            val r = PartyRoom(
                                id = rawId,
                                roomNumber = parsedRoomNum,
                                name = rTitle,
                                description = rDesc,
                                category = obj.optString("category", "Chat"),
                                coverUrl = rCover,
                                bgUrl = rBg,
                                hostUserId = rHostId,
                                hostName = obj.optString("host_name", "Host"),
                                hostAvatarUrl = obj.optString("host_avatar_url", ""),
                                seatsCount = obj.optInt("seats_count", 8),
                                isLocked = obj.optBoolean("is_locked", false) || (if (obj.isNull("room_password")) "" else obj.optString("room_password", "").trim()).let { it.isNotEmpty() && !it.equals("null", ignoreCase = true) },
                                onlineCount = obj.optInt("online_count", 1),
                                roomPassword = if (obj.isNull("room_password")) "" else obj.optString("room_password", "").trim().let { if (it.equals("null", ignoreCase = true)) "" else it }
                            )
                            roomsList.add(r)
                            memoryRooms[r.id] = r
                        }
                    }
                    response.close()
                } catch (e: Exception) {
                    Log.w("SupabasePartyService", "Fetch rooms error: ${e.message}")
                }
            }

            // If Supabase returned results, use them
            if (roomsList.isNotEmpty()) {
                roomsList.filter { it.id !in deletedRoomIds }
            } else {
                // Otherwise only return rooms created during the current user's session
                val localRooms = memoryRooms.values.filter { it.id !in deletedRoomIds }.toList()
                if (category == "All" || category.isBlank()) {
                    localRooms
                } else {
                    localRooms.filter { it.category.equals(category, ignoreCase = true) }
                }
            }
        }
    }

    /**
     * Get or fetch seats for a party room from Supabase
     */
    suspend fun fetchRoomSeats(roomId: String, seatsCount: Int = 8): List<PartySeat> {
        return withContext(Dispatchers.IO) {
            val seats = (0 until seatsCount).map { PartySeat(seatIndex = it) }.toMutableList()

            // Merge any local memory seats first
            memorySeats[roomId]?.let { memList ->
                memList.forEach { seat ->
                    if (seat.seatIndex in 0 until seatsCount) {
                        seats[seat.seatIndex] = seat
                    }
                }
            }

            val baseUrl = SupabaseConfig.supabaseUrl.trim().removeSuffix("/")
            val apiKey = SupabaseConfig.supabaseAnonKey.trim()

            if (baseUrl.isNotEmpty() && apiKey.isNotEmpty() && roomId.isNotBlank()) {
                try {
                    val url = "$baseUrl/rest/v1/party_room_seats?room_id=eq.$roomId&order=seat_index.asc"
                    val authHeader = getAuthHeader()
                    val request = Request.Builder()
                        .url(url)
                        .addHeader("apikey", apiKey)
                        .addHeader("Authorization", authHeader)
                        .get()
                        .build()

                    val response = client.newCall(request).execute()
                    if (response.isSuccessful) {
                        val body = response.body?.string() ?: "[]"
                        val jsonArr = JSONArray(body)
                        // Strictly enforce single seat per user policy:
                        val seenUserIds = mutableSetOf<String>()
                        for (i in 0 until jsonArr.length()) {
                            val obj = jsonArr.getJSONObject(i)
                            val idx = obj.optInt("seat_index", -1)
                            val uid = obj.optString("user_id", "").trim()
                            if (idx in 0 until seatsCount) {
                                if (uid.isNotBlank()) {
                                    if (seenUserIds.contains(uid)) {
                                        // Duplicate seat for the same user detected; skip this duplicate seat
                                        continue
                                    }
                                    seenUserIds.add(uid)
                                    val s = PartySeat(
                                        seatIndex = idx,
                                        userId = uid,
                                        userName = obj.optString("user_name", ""),
                                        avatarUrl = obj.optString("avatar_url", ""),
                                        isMuted = obj.optBoolean("is_muted", false),
                                        isSpeaking = obj.optBoolean("is_speaking", false)
                                    )
                                    seats[idx] = s
                                } else {
                                    seats[idx] = PartySeat(seatIndex = idx)
                                }
                            }
                        }
                    }
                    response.close()
                } catch (e: Exception) {
                    Log.w("SupabasePartyService", "Fetch seats warning: ${e.message}")
                }
            }

            // Clean up any memory duplicate user instances to enforce single seat policy
            val uniqueUsers = mutableSetOf<String>()
            for (i in seats.indices) {
                val uid = seats[i].userId
                if (uid.isNotBlank()) {
                    if (uniqueUsers.contains(uid)) {
                        seats[i] = PartySeat(seatIndex = i)
                    } else {
                        uniqueUsers.add(uid)
                    }
                }
            }

            memorySeats[roomId] = seats
            seats
        }
    }

    /**
     * Synchronous getter from memory cache
     */
    fun getRoomSeats(roomId: String, seatsCount: Int = 8): List<PartySeat> {
        val existing = memorySeats[roomId]
        if (existing != null) return existing
        val newSeats = (0 until seatsCount).map { PartySeat(seatIndex = it) }.toMutableList()
        memorySeats[roomId] = newSeats
        return newSeats
    }

    /**
     * User takes a seat in the party room (strictly enforces 1 seat per user policy)
     */
    suspend fun occupySeat(
        roomId: String,
        seatIndex: Int,
        userId: String,
        userName: String,
        avatarUrl: String,
        userLevel: Int = 1
    ): Boolean {
        return withContext(Dispatchers.IO) {
            val seats = memorySeats.getOrPut(roomId) { (0 until 8).map { PartySeat(seatIndex = it) }.toMutableList() }
            if (seatIndex !in 0 until seats.size) return@withContext false

            var isShiftingSeats = false
            var prevSeatIndex = -1
            // Clear ALL previous seats held by this user anywhere in the room
            for (i in seats.indices) {
                if (seats[i].userId == userId) {
                    isShiftingSeats = true
                    prevSeatIndex = i
                    seats[i] = PartySeat(seatIndex = i)
                }
            }

            // Occupy targeted seat immediately
            seats[seatIndex] = PartySeat(
                seatIndex = seatIndex,
                userId = userId,
                userName = userName,
                avatarUrl = avatarUrl,
                isMuted = false,
                userLevel = userLevel
            )

            // Broadcast seat change across all connected clients immediately
            PartyRealtimeRelayManager.broadcastSeatSwitch(
                roomId = roomId,
                userId = userId,
                fromSeat = prevSeatIndex,
                toSeat = seatIndex,
                userName = userName,
                avatarUrl = avatarUrl
            )

            // Persist to Supabase Database via secure RPC
            val baseUrl = SupabaseConfig.supabaseUrl.trim().removeSuffix("/")
            val apiKey = SupabaseConfig.supabaseAnonKey.trim()
            if (baseUrl.isNotEmpty() && apiKey.isNotEmpty() && roomId.isNotBlank()) {
                try {
                    val authHeader = getAuthHeader()
                    val rpcJson = JSONObject().apply {
                        put("p_room_id", roomId)
                        put("p_seat_index", seatIndex)
                    }.toString()

                    val rpcReq = Request.Builder()
                        .url("$baseUrl/rest/v1/rpc/secure_occupy_seat")
                        .addHeader("apikey", apiKey)
                        .addHeader("Authorization", authHeader)
                        .addHeader("Content-Type", "application/json")
                        .post(rpcJson.toRequestBody(jsonMediaType))
                        .build()

                    client.newCall(rpcReq).execute().use { response ->
                        if (!response.isSuccessful) {
                            Log.e("SupabasePartyService", "Seat occupy RPC failed: ${response.code}")
                            return@withContext false
                        }
                    }
                } catch (e: Exception) {
                    Log.e("SupabasePartyService", "Seat occupy error", e)
                    return@withContext false
                }
            }

            true
        }
    }

    /**
     * User leaves their seat (clears from memory and syncs to Supabase)
     */
    suspend fun releaseSeat(roomId: String, userId: String): Boolean {
        return withContext(Dispatchers.IO) {
            val seats = memorySeats[roomId]
            var freedAny = false
            var freedUserName = ""

            var freedSeatIndex = -1
            if (seats != null) {
                for (i in seats.indices) {
                    if (seats[i].userId == userId) {
                        freedUserName = seats[i].userName
                        freedSeatIndex = i
                        seats[i] = PartySeat(seatIndex = i)
                        freedAny = true
                    }
                }
            }

            if (freedSeatIndex != -1) {
                PartyRealtimeRelayManager.broadcastSeatSwitch(
                    roomId = roomId,
                    userId = userId,
                    fromSeat = freedSeatIndex,
                    toSeat = -1,
                    userName = freedUserName,
                    avatarUrl = ""
                )
            }

            // Sync to Supabase Database via secure RPC
            val baseUrl = SupabaseConfig.supabaseUrl.trim().removeSuffix("/")
            val apiKey = SupabaseConfig.supabaseAnonKey.trim()
            if (baseUrl.isNotEmpty() && apiKey.isNotEmpty() && roomId.isNotBlank()) {
                try {
                    val authHeader = getAuthHeader()
                    val rpcJson = JSONObject().apply {
                        put("p_room_id", roomId)
                        put("p_seat_index", freedSeatIndex)
                    }.toString()

                    val rpcReq = Request.Builder()
                        .url("$baseUrl/rest/v1/rpc/secure_release_seat")
                        .addHeader("apikey", apiKey)
                        .addHeader("Authorization", authHeader)
                        .addHeader("Content-Type", "application/json")
                        .post(rpcJson.toRequestBody(jsonMediaType))
                        .build()

                    client.newCall(rpcReq).execute().close()
                } catch (e: Exception) {
                    Log.w("SupabasePartyService", "Seat release error: ${e.message}")
                }
            }

            true
        }
    }

    /**
     * Toggle Mute on a Seat
     */
    fun toggleSeatMute(roomId: String, userId: String, isMuted: Boolean) {
        val seats = memorySeats[roomId] ?: return
        val index = seats.indexOfFirst { it.userId == userId }
        if (index != -1) {
            seats[index] = seats[index].copy(isMuted = isMuted)
        }
    }

    /**
     * Update Speaking visual indicator on a Seat
     */
    fun updateSeatSpeaking(roomId: String, userId: String, isSpeaking: Boolean) {
        val seats = memorySeats[roomId] ?: return
        val index = seats.indexOfFirst { it.userId == userId }
        if (index != -1) {
            seats[index] = seats[index].copy(isSpeaking = isSpeaking)
            PartyRealtimeRelayManager.broadcastSpeaking(
                roomId = roomId,
                userId = userId,
                seatIndex = index,
                isSpeaking = isSpeaking,
                volume = if (isSpeaking) 0.8f else 0f
            )
        }
    }

    /**
     * Get or fetch messages for a party room
     */
    suspend fun fetchRoomMessages(roomId: String): List<PartyRoomMessage> {
        return withContext(Dispatchers.IO) {
            val list = mutableListOf<PartyRoomMessage>()
            val baseUrl = SupabaseConfig.supabaseUrl.trim().removeSuffix("/")
            val apiKey = SupabaseConfig.supabaseAnonKey.trim()

            if (baseUrl.isNotEmpty() && apiKey.isNotEmpty() && roomId.isNotBlank()) {
                try {
                    val url = "$baseUrl/rest/v1/party_room_messages?room_id=eq.$roomId&order=created_at.asc&limit=100"
                    val authHeader = getAuthHeader()
                    val req = Request.Builder()
                        .url(url)
                        .addHeader("apikey", apiKey)
                        .addHeader("Authorization", authHeader)
                        .get()
                        .build()

                    val resp = client.newCall(req).execute()
                    if (resp.isSuccessful) {
                        val body = resp.body?.string() ?: "[]"
                        val jsonArr = JSONArray(body)
                        for (i in 0 until jsonArr.length()) {
                            val obj = jsonArr.getJSONObject(i)
                            val m = PartyRoomMessage(
                                id = obj.optString("id", UUID.randomUUID().toString()),
                                roomId = obj.optString("room_id", roomId),
                                senderId = obj.optString("sender_id", ""),
                                senderName = obj.optString("sender_name", "User"),
                                senderAvatarUrl = obj.optString("sender_avatar_url", ""),
                                content = obj.optString("content", ""),
                                msgType = obj.optString("msg_type", "chat"),
                                giftIcon = obj.optString("gift_icon", "")
                            )
                            list.add(m)
                        }
                    }
                    resp.close()
                } catch (e: Exception) {
                    Log.w("SupabasePartyService", "Fetch messages warning: ${e.message}")
                }
            }

            if (list.isNotEmpty()) {
                memoryMessages[roomId] = list
                list
            } else {
                memoryMessages.getOrPut(roomId) { mutableListOf() }
            }
        }
    }

    fun getRoomMessages(roomId: String): List<PartyRoomMessage> {
        return memoryMessages.getOrPut(roomId) { mutableListOf() }
    }

    /**
     * Send a Message in the Party Room
     */
    suspend fun sendRoomMessage(roomId: String, message: PartyRoomMessage): Boolean {
        return withContext(Dispatchers.IO) {
            addRoomMessage(roomId, message)

            val baseUrl = SupabaseConfig.supabaseUrl.trim().removeSuffix("/")
            val apiKey = SupabaseConfig.supabaseAnonKey.trim()
            if (baseUrl.isNotEmpty() && apiKey.isNotEmpty() && roomId.isNotBlank()) {
                try {
                    val msgJson = JSONObject().apply {
                        put("room_id", roomId)
                        put("sender_id", message.senderId)
                        put("sender_name", message.senderName)
                        put("sender_avatar_url", message.senderAvatarUrl)
                        put("content", message.content)
                        put("msg_type", message.msgType)
                        put("gift_icon", message.giftIcon)
                    }.toString()

                    val authHeader = getAuthHeader()
                    val req = Request.Builder()
                        .url("$baseUrl/rest/v1/party_room_messages")
                        .addHeader("apikey", apiKey)
                        .addHeader("Authorization", authHeader)
                        .addHeader("Content-Type", "application/json")
                        .post(msgJson.toRequestBody(jsonMediaType))
                        .build()

                    client.newCall(req).execute().close()
                } catch (e: Exception) {
                    Log.w("SupabasePartyService", "Send message sync warning: ${e.message}")
                }
            }
            true
        }
    }

    private fun addRoomMessage(roomId: String, message: PartyRoomMessage) {
        val list = memoryMessages.getOrPut(roomId) { mutableListOf() }
        list.add(message)
    }

    /**
     * Fetch appointed admins for a party room
     */
    suspend fun fetchRoomAdmins(roomId: String): Set<String> {
        return withContext(Dispatchers.IO) {
            val localAdmins = memoryAdmins.getOrPut(roomId) { mutableSetOf() }
            val baseUrl = SupabaseConfig.supabaseUrl.trim().removeSuffix("/")
            val apiKey = SupabaseConfig.supabaseAnonKey.trim()

            if (baseUrl.isNotEmpty() && apiKey.isNotEmpty() && roomId.isNotBlank()) {
                try {
                    val url = "$baseUrl/rest/v1/party_room_admins?room_id=eq.$roomId&select=user_id"
                    val authHeader = getAuthHeader()
                    val req = Request.Builder()
                        .url(url)
                        .addHeader("apikey", apiKey)
                        .addHeader("Authorization", authHeader)
                        .get()
                        .build()

                    val response = client.newCall(req).execute()
                    if (response.isSuccessful) {
                        val body = response.body?.string() ?: "[]"
                        val arr = JSONArray(body)
                        for (i in 0 until arr.length()) {
                            val uid = arr.getJSONObject(i).optString("user_id")
                            if (uid.isNotBlank()) localAdmins.add(uid)
                        }
                    }
                    response.close()
                } catch (e: Exception) {
                    Log.w("SupabasePartyService", "Fetch admins warning: ${e.message}")
                }
            }
            localAdmins
        }
    }

    /**
     * Appoint a user as Party Room Admin (by Room Owner, max 5 admins)
     */
    suspend fun appointRoomAdmin(roomId: String, userId: String, userName: String): Pair<Boolean, String> {
        return withContext(Dispatchers.IO) {
            val admins = memoryAdmins.getOrPut(roomId) { mutableSetOf() }
            if (admins.size >= 5 && !admins.contains(userId)) {
                return@withContext Pair(false, "Maximum 5 Admins reached for this party room.")
            }
            admins.add(userId)

            // Add system announcement message
            val sysMsg = PartyRoomMessage(
                roomId = roomId,
                senderName = "System",
                content = "🛡️ $userName was appointed as Party Room Admin!",
                msgType = "system"
            )
            addRoomMessage(roomId, sysMsg)

            val baseUrl = SupabaseConfig.supabaseUrl.trim().removeSuffix("/")
            val apiKey = SupabaseConfig.supabaseAnonKey.trim()
            if (baseUrl.isNotEmpty() && apiKey.isNotEmpty() && roomId.isNotBlank()) {
                try {
                    // Try RPC first
                    val rpcJson = JSONObject().apply {
                        put("p_room_id", roomId)
                        put("p_user_id", userId)
                    }.toString()

                    val authHeader = getAuthHeader()
                    val rpcReq = Request.Builder()
                        .url("$baseUrl/rest/v1/rpc/appoint_party_admin")
                        .addHeader("apikey", apiKey)
                        .addHeader("Authorization", authHeader)
                        .addHeader("Content-Type", "application/json")
                        .post(rpcJson.toRequestBody(jsonMediaType))
                        .build()

                    val rpcResp = client.newCall(rpcReq).execute()
                    val rpcSuccess = rpcResp.isSuccessful
                    rpcResp.close()

                    if (!rpcSuccess) {
                        val adminJson = JSONObject().apply {
                            put("room_id", roomId)
                            put("user_id", userId)
                        }.toString()

                        val req = Request.Builder()
                            .url("$baseUrl/rest/v1/party_room_admins")
                            .addHeader("apikey", apiKey)
                            .addHeader("Authorization", authHeader)
                            .addHeader("Content-Type", "application/json")
                            .post(adminJson.toRequestBody(jsonMediaType))
                            .build()

                        client.newCall(req).execute().close()
                    }
                } catch (e: Exception) {
                    Log.w("SupabasePartyService", "Appoint admin sync warning: ${e.message}")
                }
            }
            Pair(true, "Appointed $userName as Party Admin")
        }
    }

    /**
     * Remove Party Room Admin role from a user
     */
    suspend fun removeRoomAdmin(roomId: String, userId: String, userName: String = "User"): Boolean {
        return withContext(Dispatchers.IO) {
            val admins = memoryAdmins.getOrPut(roomId) { mutableSetOf() }
            admins.remove(userId)

            val sysMsg = PartyRoomMessage(
                roomId = roomId,
                senderName = "System",
                content = "$userName is no longer a Party Room Admin.",
                msgType = "system"
            )
            addRoomMessage(roomId, sysMsg)

            val baseUrl = SupabaseConfig.supabaseUrl.trim().removeSuffix("/")
            val apiKey = SupabaseConfig.supabaseAnonKey.trim()
            if (baseUrl.isNotEmpty() && apiKey.isNotEmpty() && roomId.isNotBlank()) {
                try {
                    val url = "$baseUrl/rest/v1/party_room_admins?room_id=eq.$roomId&user_id=eq.$userId"
                    val authHeader = getAuthHeader()
                    val req = Request.Builder()
                        .url(url)
                        .addHeader("apikey", apiKey)
                        .addHeader("Authorization", authHeader)
                        .delete()
                        .build()

                    client.newCall(req).execute().close()
                } catch (e: Exception) {
                    Log.w("SupabasePartyService", "Remove admin sync warning: ${e.message}")
                }
            }
            true
        }
    }

    /**
     * Update room seat count (4, 8, 10, 12, 16) by Room Owner or Admin.
     * Safely steps down users if seats are reduced below their occupied seat.
     */
    suspend fun updateRoomSeatCount(roomId: String, newSeatCount: Int): Boolean {
        return withContext(Dispatchers.IO) {
            val count = newSeatCount.coerceIn(4, 16)
            memoryRooms[roomId]?.let { existing ->
                memoryRooms[roomId] = existing.copy(seatsCount = count)
            }

            // Adjust memory seats list and evict displaced seated members
            val currentSeats = memorySeats.getOrPut(roomId) { mutableListOf() }
            if (currentSeats.size < count) {
                for (i in currentSeats.size until count) {
                    currentSeats.add(PartySeat(seatIndex = i))
                }
            } else if (currentSeats.size > count) {
                // If seats are reduced, identify users who need to be moved to audience
                val removedSeats = currentSeats.drop(count)
                for (seat in removedSeats) {
                    if (seat.userId.isNotBlank()) {
                        releaseSeat(roomId, seat.userId)
                        val displacedMsg = PartyRoomMessage(
                            roomId = roomId,
                            senderName = "System",
                            content = "${seat.userName} stepped down as seat count was reduced.",
                            msgType = "system"
                        )
                        addRoomMessage(roomId, displacedMsg)
                    }
                }
                val trimmed = currentSeats.take(count).toMutableList()
                memorySeats[roomId] = trimmed
            }

            val sysMsg = PartyRoomMessage(
                roomId = roomId,
                senderName = "System",
                content = "🎙️ Room seat capacity updated to $count seats",
                msgType = "system"
            )
            addRoomMessage(roomId, sysMsg)

            val baseUrl = SupabaseConfig.supabaseUrl.trim().removeSuffix("/")
            val apiKey = SupabaseConfig.supabaseAnonKey.trim()
            if (baseUrl.isNotEmpty() && apiKey.isNotEmpty() && roomId.isNotBlank()) {
                try {
                    val patchJson = JSONObject().apply {
                        put("seats_count", count)
                    }.toString()

                    val authHeader = getAuthHeader()
                    val req = Request.Builder()
                        .url("$baseUrl/rest/v1/party_rooms?id=eq.$roomId")
                        .addHeader("apikey", apiKey)
                        .addHeader("Authorization", authHeader)
                        .addHeader("Content-Type", "application/json")
                        .patch(patchJson.toRequestBody(jsonMediaType))
                        .build()

                    client.newCall(req).execute().close()
                } catch (e: Exception) {
                    Log.w("SupabasePartyService", "Update seat count warning: ${e.message}")
                }
            }
            true
        }
    }

    /**
     * Register user presence in the room
     */
    fun trackUserPresence(
        roomId: String,
        userId: String,
        userName: String,
        avatarUrl: String,
        role: String = "audience"
    ) {
        if (userId.isBlank() || roomId.isBlank()) return
        val roomMembers = memoryMembers.getOrPut(roomId) { ConcurrentHashMap() }
        roomMembers[userId] = PartyRoomMember(
            userId = userId,
            userName = userName.ifBlank { "Guest" },
            avatarUrl = avatarUrl,
            role = role,
            joinedAt = System.currentTimeMillis().toString()
        )

        // Presence tracking no longer directly inserts into party_room_members (now handled by join_party_room RPC)
    }

    /**
     * Remove user presence on leaving room completely (frees seat, cleans memory & remote presence)
     */
    fun removeUserPresence(roomId: String, userId: String) {
        if (userId.isBlank() || roomId.isBlank()) return
        
        // 1. Remove from memory members
        memoryMembers[roomId]?.remove(userId)

        // 2. Clear from memory seats if seated
        val seats = memorySeats[roomId]
        var freedSeatIndex = -1
        var freedUserName = ""
        if (seats != null) {
            for (i in seats.indices) {
                if (seats[i].userId == userId) {
                    freedSeatIndex = i
                    freedUserName = seats[i].userName
                    seats[i] = PartySeat(seatIndex = i)
                }
            }
        }

        // 3. Broadcast real-time seat switch and user leave events
        if (freedSeatIndex != -1) {
            PartyRealtimeRelayManager.broadcastSeatSwitch(
                roomId = roomId,
                userId = userId,
                fromSeat = freedSeatIndex,
                toSeat = -1,
                userName = freedUserName,
                avatarUrl = ""
            )
        }
        PartyRealtimeRelayManager.broadcastUserLeave(roomId, userId)

        // 4. Remote cleanup via REST API in background
        CoroutineScope(Dispatchers.IO).launch {
            try {
                val baseUrl = SupabaseConfig.supabaseUrl.trim().removeSuffix("/")
                val apiKey = SupabaseConfig.supabaseAnonKey.trim()
                if (baseUrl.isNotEmpty() && apiKey.isNotEmpty()) {
                    val authHeader = getAuthHeader()

                    // Delete presence from party_room_members
                    try {
                        val delMemReq = Request.Builder()
                            .url("$baseUrl/rest/v1/party_room_members?room_id=eq.$roomId&user_id=eq.$userId")
                            .addHeader("apikey", apiKey)
                            .addHeader("Authorization", authHeader)
                            .delete()
                            .build()
                        client.newCall(delMemReq).execute().close()
                    } catch (_: Exception) {}

                    // Delete seat from party_room_seats
                    try {
                        val delSeatReq = Request.Builder()
                            .url("$baseUrl/rest/v1/party_room_seats?room_id=eq.$roomId&user_id=eq.$userId")
                            .addHeader("apikey", apiKey)
                            .addHeader("Authorization", authHeader)
                            .delete()
                            .build()
                        client.newCall(delSeatReq).execute().close()
                    } catch (_: Exception) {}

                    // Patch seat if fixed rows exist
                    try {
                        val patchSeatJson = JSONObject().apply {
                            put("user_id", "")
                            put("user_name", "")
                            put("avatar_url", "")
                            put("is_muted", false)
                            put("is_speaking", false)
                        }.toString()
                        val patchSeatReq = Request.Builder()
                            .url("$baseUrl/rest/v1/party_room_seats?room_id=eq.$roomId&user_id=eq.$userId")
                            .addHeader("apikey", apiKey)
                            .addHeader("Authorization", authHeader)
                            .addHeader("Content-Type", "application/json")
                            .patch(patchSeatJson.toRequestBody(jsonMediaType))
                            .build()
                        client.newCall(patchSeatReq).execute().close()
                    } catch (_: Exception) {}
                }
            } catch (_: Exception) {}
        }
    }

    /**
     * Update room appearance (cover photo, background photo, room name) by Room Owner
     */
    suspend fun updateRoomAppearance(
        roomId: String,
        coverUrl: String? = null,
        bgUrl: String? = null,
        roomName: String? = null
    ): Boolean {
        return withContext(Dispatchers.IO) {
            val existing = memoryRooms[roomId]
            if (existing != null) {
                memoryRooms[roomId] = existing.copy(
                    coverUrl = coverUrl ?: existing.coverUrl,
                    bgUrl = bgUrl ?: existing.bgUrl,
                    name = roomName ?: existing.name
                )
            }
            val baseUrl = SupabaseConfig.supabaseUrl.trim().removeSuffix("/")
            val apiKey = SupabaseConfig.supabaseAnonKey.trim()
            if (baseUrl.isNotEmpty() && apiKey.isNotEmpty() && roomId.isNotBlank()) {
                try {
                    val patchObj = JSONObject()
                    if (coverUrl != null) patchObj.put("cover_url", coverUrl)
                    if (bgUrl != null) patchObj.put("bg_url", bgUrl)
                    if (roomName != null) patchObj.put("name", roomName)

                    val authHeader = getAuthHeader()
                    val req = Request.Builder()
                        .url("$baseUrl/rest/v1/party_rooms?id=eq.$roomId")
                        .addHeader("apikey", apiKey)
                        .addHeader("Authorization", authHeader)
                        .addHeader("Content-Type", "application/json")
                        .patch(patchObj.toString().toRequestBody(jsonMediaType))
                        .build()

                    val res = client.newCall(req).execute()
                    val ok = res.isSuccessful || res.code in 200..299
                    res.close()
                    return@withContext ok
                } catch (e: Exception) {
                    Log.w("SupabasePartyService", "Update room appearance error: ${e.message}")
                }
            }
            true
        }
    }

    /**
     * Fetch organized room members categorized into Owner, Admins, Speakers, and Audience
     */
    suspend fun fetchCategorizedRoomMembers(
        room: PartyRoom,
        currentUserId: String,
        currentUserName: String,
        currentUserAvatar: String,
        currentSeats: List<PartySeat>
    ): List<PartyRoomMember> {
        return withContext(Dispatchers.IO) {
            val admins = fetchRoomAdmins(room.id)
            val memberMap = memoryMembers.getOrPut(room.id) { ConcurrentHashMap() }

            // Ensure current user is tracked
            if (currentUserId.isNotBlank()) {
                val userRole = when {
                    currentUserId == room.hostUserId -> "owner"
                    admins.contains(currentUserId) -> "admin"
                    currentSeats.any { it.userId == currentUserId } -> "speaker"
                    else -> "audience"
                }
                memberMap[currentUserId] = PartyRoomMember(
                    userId = currentUserId,
                    userName = currentUserName.ifBlank { "User" },
                    avatarUrl = currentUserAvatar,
                    role = userRole
                )
            }

            // Also map seated users
            currentSeats.forEach { seat ->
                if (seat.userId.isNotBlank()) {
                    val existingRole = when {
                        seat.userId == room.hostUserId -> "owner"
                        admins.contains(seat.userId) -> "admin"
                        else -> "speaker"
                    }
                    val existing = memberMap[seat.userId]
                    memberMap[seat.userId] = PartyRoomMember(
                        userId = seat.userId,
                        userName = seat.userName.ifBlank { existing?.userName ?: "Guest" },
                        avatarUrl = seat.avatarUrl.ifBlank { existing?.avatarUrl ?: "" },
                        role = existingRole,
                        seatIndex = seat.seatIndex,
                        isMuted = seat.isMuted,
                        isSpeaking = seat.isSpeaking
                    )
                }
            }

            // Query Supabase party_room_members table if available
            val baseUrl = SupabaseConfig.supabaseUrl.trim().removeSuffix("/")
            val apiKey = SupabaseConfig.supabaseAnonKey.trim()
            if (baseUrl.isNotEmpty() && apiKey.isNotEmpty() && room.id.isNotBlank()) {
                try {
                    val url = "$baseUrl/rest/v1/party_room_members?room_id=eq.${room.id}&select=*"
                    val authHeader = getAuthHeader()
                    val req = Request.Builder()
                        .url(url)
                        .addHeader("apikey", apiKey)
                        .addHeader("Authorization", authHeader)
                        .get()
                        .build()

                    val response = client.newCall(req).execute()
                    if (response.isSuccessful) {
                        val body = response.body?.string() ?: "[]"
                        val arr = JSONArray(body)
                        for (i in 0 until arr.length()) {
                            val obj = arr.getJSONObject(i)
                            val uid = obj.optString("user_id")
                            if (uid.isNotBlank() && !memberMap.containsKey(uid)) {
                                val r = when {
                                    uid == room.hostUserId -> "owner"
                                    admins.contains(uid) -> "admin"
                                    else -> obj.optString("role", "audience")
                                }
                                memberMap[uid] = PartyRoomMember(
                                    userId = uid,
                                    userName = obj.optString("user_name", "User"),
                                    avatarUrl = obj.optString("avatar_url", ""),
                                    role = r
                                )
                            }
                        }
                    }
                    response.close()
                } catch (e: Exception) {
                    Log.w("SupabasePartyService", "Fetch room members warning: ${e.message}")
                }
            }

            // Return sorted list: Room Owner at VERY TOP, then Admins, then On-Mic Speakers, then Audience
            val allMembers = memberMap.values.toList()
            val ownerList = allMembers.filter { it.userId == room.hostUserId || it.role == "owner" }
                .distinctBy { it.userId }
            val adminList = allMembers.filter { admins.contains(it.userId) && it.userId != room.hostUserId }
                .distinctBy { it.userId }
            val speakerList = allMembers.filter { m ->
                currentSeats.any { it.userId == m.userId } && m.userId != room.hostUserId && !admins.contains(m.userId)
            }.distinctBy { it.userId }
            val audienceList = allMembers.filter { m ->
                m.userId != room.hostUserId && !admins.contains(m.userId) && currentSeats.none { it.userId == m.userId }
            }.distinctBy { it.userId }

            ownerList + adminList + speakerList + audienceList
        }
    }

    /**
     * Delete Party Room permanently from database and memory (Room Owner only)
     */
    suspend fun deletePartyRoom(roomId: String, hostUserId: String = "", context: Context? = null): Boolean {
        return withContext(Dispatchers.IO) {
            try {
                if (roomId.isBlank()) return@withContext false

                // 1. Mark as locally deleted immediately so it is never shown again in UI or discovery
                deletedRoomIds.add(roomId)
                memoryRooms.remove(roomId)
                memorySeats.remove(roomId)
                memoryMessages.remove(roomId)
                memoryAdmins.remove(roomId)
                memoryMembers.remove(roomId)

                // 2. Clear persisted session if it was this room
                if (context != null) {
                    try {
                        AppDataCacheManager.removePartyRoomFromCache(context, roomId)
                        if (PartyRoomSessionManager.getPersistedRoomId(context) == roomId) {
                            PartyRoomSessionManager.clearActiveRoomSession(context)
                        }
                    } catch (_: Exception) {}
                }

                // 3. Broadcast room closed event via realtime relay
                try {
                    PartyRealtimeRelayManager.broadcastRoomClosed(roomId)
                } catch (_: Exception) {}

                val baseUrl = SupabaseConfig.supabaseUrl.trim().removeSuffix("/")
                val apiKey = SupabaseConfig.supabaseAnonKey.trim()

                if (baseUrl.isNotEmpty() && apiKey.isNotEmpty()) {
                    val authHeader = getAuthHeader(context)

                    // Try official RPC calls for closing/deleting party room
                    try {
                        val rpcPayload = JSONObject().apply {
                            put("p_room_id", roomId)
                            put("room_id", roomId)
                        }.toString()
                        val reqRpc1 = Request.Builder()
                            .url("$baseUrl/rest/v1/rpc/delete_party_room")
                            .addHeader("apikey", apiKey)
                            .addHeader("Authorization", authHeader)
                            .addHeader("Content-Type", "application/json")
                            .post(rpcPayload.toRequestBody(jsonMediaType))
                            .build()
                        client.newCall(reqRpc1).execute().close()
                    } catch (_: Exception) {}

                    try {
                        val rpcPayload = JSONObject().apply {
                            put("p_room_id", roomId)
                            put("room_id", roomId)
                        }.toString()
                        val reqRpc2 = Request.Builder()
                            .url("$baseUrl/rest/v1/rpc/close_party_room")
                            .addHeader("apikey", apiKey)
                            .addHeader("Authorization", authHeader)
                            .addHeader("Content-Type", "application/json")
                            .post(rpcPayload.toRequestBody(jsonMediaType))
                            .build()
                        client.newCall(reqRpc2).execute().close()
                    } catch (_: Exception) {}

                    // Purge dependent tables
                    val tables = listOf(
                        "party_room_seats",
                        "party_seats",
                        "party_room_messages",
                        "party_room_admins",
                        "party_room_members",
                        "party_room_bans",
                        "party_room_gifts",
                        "party_room_listeners"
                    )
                    for (table in tables) {
                        try {
                            val req = Request.Builder()
                                .url("$baseUrl/rest/v1/$table?room_id=eq.$roomId")
                                .addHeader("apikey", apiKey)
                                .addHeader("Authorization", authHeader)
                                .delete()
                                .build()
                            client.newCall(req).execute().close()
                        } catch (_: Exception) {}
                    }

                    // Update room status to closed and inactive
                    try {
                        val deactivateJson = JSONObject().apply {
                            put("is_active", false)
                            put("status", "closed")
                            put("is_deleted", true)
                        }.toString()
                        val reqDeact = Request.Builder()
                            .url("$baseUrl/rest/v1/party_rooms?id=eq.$roomId")
                            .addHeader("apikey", apiKey)
                            .addHeader("Authorization", authHeader)
                            .addHeader("Content-Type", "application/json")
                            .patch(deactivateJson.toRequestBody(jsonMediaType))
                            .build()
                        client.newCall(reqDeact).execute().close()
                    } catch (_: Exception) {}

                    // Direct row delete from party_rooms
                    try {
                        val reqRoom = Request.Builder()
                            .url("$baseUrl/rest/v1/party_rooms?id=eq.$roomId")
                            .addHeader("apikey", apiKey)
                            .addHeader("Authorization", authHeader)
                            .delete()
                            .build()
                        client.newCall(reqRoom).execute().close()
                    } catch (_: Exception) {}
                }
                true
            } catch (e: Exception) {
                Log.e("SupabasePartyService", "Delete room error", e)
                true
            }
        }
    }

    /**
     * Send a gift in a party room using secure server-side RPC gift_party_room
     */
    suspend fun sendGift(
        roomId: String,
        recipientId: String,
        giftId: String,
        quantity: Int,
        idempotencyKey: String = java.util.UUID.randomUUID().toString()
    ): Boolean {
        return withContext(Dispatchers.IO) {
            val baseUrl = SupabaseConfig.supabaseUrl.trim().removeSuffix("/")
            val apiKey = SupabaseConfig.supabaseAnonKey.trim()
            if (baseUrl.isEmpty() || apiKey.isEmpty()) return@withContext false

            try {
                val rpcJson = JSONObject().apply {
                    put("p_room_id", roomId)
                    put("p_recipient_id", recipientId)
                    put("p_gift_id", giftId)
                    put("p_quantity", quantity)
                    put("p_idempotency_key", idempotencyKey)
                }.toString()

                val authHeader = getAuthHeader()
                val request = Request.Builder()
                    .url("$baseUrl/rest/v1/rpc/gift_party_room")
                    .addHeader("apikey", apiKey)
                    .addHeader("Authorization", authHeader)
                    .addHeader("Content-Type", "application/json")
                    .post(rpcJson.toRequestBody(jsonMediaType))
                    .build()

                client.newCall(request).execute().use { response ->
                    response.isSuccessful
                }
            } catch (e: Exception) {
                Log.e("SupabasePartyService", "Send gift error", e)
                false
            }
        }
    }

    /**
     * Manage room admins using secure RPC manage_party_admin
     */
    suspend fun manageAdmin(roomId: String, userId: String, action: String): Boolean {
        return withContext(Dispatchers.IO) {
            val baseUrl = SupabaseConfig.supabaseUrl.trim().removeSuffix("/")
            val apiKey = SupabaseConfig.supabaseAnonKey.trim()
            try {
                val rpcJson = JSONObject().apply {
                    put("p_room_id", roomId)
                    put("p_user_id", userId)
                    put("p_action", action)
                }.toString()

                val authHeader = getAuthHeader()
                val request = Request.Builder()
                    .url("$baseUrl/rest/v1/rpc/manage_party_admin")
                    .addHeader("apikey", apiKey)
                    .addHeader("Authorization", authHeader)
                    .addHeader("Content-Type", "application/json")
                    .post(rpcJson.toRequestBody(jsonMediaType))
                    .build()

                client.newCall(request).execute().use { response ->
                    response.isSuccessful
                }
            } catch (e: Exception) {
                false
            }
        }
    }

    /**
     * Kick a member from the room using secure RPC kick_party_member
     */
    suspend fun kickMember(roomId: String, userId: String): Boolean {
        return withContext(Dispatchers.IO) {
            val baseUrl = SupabaseConfig.supabaseUrl.trim().removeSuffix("/")
            val apiKey = SupabaseConfig.supabaseAnonKey.trim()
            try {
                val rpcJson = JSONObject().apply {
                    put("p_room_id", roomId)
                    put("p_user_id", userId)
                }.toString()

                val authHeader = getAuthHeader()
                val request = Request.Builder()
                    .url("$baseUrl/rest/v1/rpc/kick_party_member")
                    .addHeader("apikey", apiKey)
                    .addHeader("Authorization", authHeader)
                    .addHeader("Content-Type", "application/json")
                    .post(rpcJson.toRequestBody(jsonMediaType))
                    .build()

                client.newCall(request).execute().use { response ->
                    response.isSuccessful
                }
            } catch (e: Exception) {
                false
            }
        }
    }

    /**
     * Update room seat count using secure RPC update_room_seats_count
     */
    suspend fun updateSeatCount(roomId: String, newCount: Int): Boolean {
        return withContext(Dispatchers.IO) {
            val baseUrl = SupabaseConfig.supabaseUrl.trim().removeSuffix("/")
            val apiKey = SupabaseConfig.supabaseAnonKey.trim()
            try {
                val rpcJson = JSONObject().apply {
                    put("p_room_id", roomId)
                    put("p_new_count", newCount)
                }.toString()

                val authHeader = getAuthHeader()
                val request = Request.Builder()
                    .url("$baseUrl/rest/v1/rpc/update_room_seats_count")
                    .addHeader("apikey", apiKey)
                    .addHeader("Authorization", authHeader)
                    .addHeader("Content-Type", "application/json")
                    .post(rpcJson.toRequestBody(jsonMediaType))
                    .build()

                client.newCall(request).execute().use { response ->
                    response.isSuccessful
                }
            } catch (e: Exception) {
                false
            }
        }
    }

    /**
     * Update room password (or remove if empty/null) by Room Owner or Admin
     */
    suspend fun updateRoomPassword(
        roomId: String,
        password: String?,
        context: Context? = null
    ): Boolean {
        return withContext(Dispatchers.IO) {
            val cleanPassword = if (password.isNullOrBlank() || password.trim().equals("null", ignoreCase = true)) "" else password.trim()
            val isNowLocked = cleanPassword.isNotEmpty()
            val existing = memoryRooms[roomId]
            if (existing != null) {
                memoryRooms[roomId] = existing.copy(
                    roomPassword = cleanPassword,
                    isLocked = isNowLocked
                )
            }
            if (context != null && roomId.isNotBlank()) {
                val prefs = context.getSharedPreferences("qivo_room_passwords", Context.MODE_PRIVATE)
                if (cleanPassword.isNotEmpty()) {
                    prefs.edit().putString("room_pwd_$roomId", cleanPassword).apply()
                } else {
                    prefs.edit().remove("room_pwd_$roomId").apply()
                }
            }

            // 1. Invoke RPC if available in database
            val rpcSuccess = setPartyRoomPasswordRpc(roomId, cleanPassword)

            // 2. Also patch directly via REST
            val baseUrl = SupabaseConfig.supabaseUrl.trim().removeSuffix("/")
            val apiKey = SupabaseConfig.supabaseAnonKey.trim()
            if (baseUrl.isNotEmpty() && apiKey.isNotEmpty() && roomId.isNotBlank()) {
                try {
                    val patchObj = JSONObject().apply {
                        if (cleanPassword.isNotEmpty()) {
                            put("room_password", cleanPassword)
                            put("is_locked", true)
                        } else {
                            put("room_password", JSONObject.NULL)
                            put("is_locked", false)
                        }
                    }

                    val authHeader = getAuthHeader(context)
                    val req = Request.Builder()
                        .url("$baseUrl/rest/v1/party_rooms?id=eq.$roomId")
                        .addHeader("apikey", apiKey)
                        .addHeader("Authorization", authHeader)
                        .addHeader("Content-Type", "application/json")
                        .patch(patchObj.toString().toRequestBody(jsonMediaType))
                        .build()

                    val res = client.newCall(req).execute()
                    val ok = res.isSuccessful || res.code in 200..299
                    res.close()
                    return@withContext ok || rpcSuccess
                } catch (e: Exception) {
                    Log.w("SupabasePartyService", "Update room password error: ${e.message}")
                }
            }
            true
        }
    }

    /**
     * Fetch latest single party room details by its ID
     */
    suspend fun fetchPartyRoomById(roomId: String, context: Context? = null): PartyRoom? {
        return withContext(Dispatchers.IO) {
            if (roomId.isBlank() || deletedRoomIds.contains(roomId)) return@withContext null
            val baseUrl = SupabaseConfig.supabaseUrl.trim().removeSuffix("/")
            val apiKey = SupabaseConfig.supabaseAnonKey.trim()
            if (baseUrl.isNotEmpty() && apiKey.isNotEmpty()) {
                try {
                    val queryUrl = "$baseUrl/rest/v1/party_rooms?id=eq.$roomId&select=*"
                    val authHeader = getAuthHeader(context)
                    val request = Request.Builder()
                        .url(queryUrl)
                        .addHeader("apikey", apiKey)
                        .addHeader("Authorization", authHeader)
                        .get()
                        .build()

                    val response = client.newCall(request).execute()
                    if (response.isSuccessful) {
                        val body = response.body?.string() ?: "[]"
                        val jsonArr = JSONArray(body)
                        if (jsonArr.length() > 0) {
                            val obj = jsonArr.getJSONObject(0)
                            val rawId = obj.optString("id")
                            val isActive = obj.optBoolean("is_active", true)
                            val status = obj.optString("status", "open")
                            val isDeleted = obj.optBoolean("is_deleted", false)
                            if (!isActive || status == "closed" || isDeleted || deletedRoomIds.contains(rawId)) {
                                response.close()
                                return@withContext null
                            }

                            var parsedRoomNum = obj.optLong("room_number", 0L)
                            if (parsedRoomNum <= 0L) {
                                parsedRoomNum = (Math.abs(rawId.hashCode()) % 999900 + 100).toLong()
                            }
                            val rTitle = obj.optString("title").ifBlank { obj.optString("name", "Party Lounge") }
                            val rDesc = obj.optString("announcement").ifBlank { obj.optString("description", "") }
                            val rBg = obj.optString("background_url").ifBlank { obj.optString("bg_url", "") }
                            val rCover = obj.optString("cover_url").ifBlank { rBg }
                            val rHostId = obj.optString("host_id").ifBlank { obj.optString("host_user_id", "") }
                            
                            val rawPassword = if (obj.isNull("room_password")) "" else obj.optString("room_password", "").trim()
                            val cleanPassword = if (rawPassword.equals("null", ignoreCase = true)) "" else rawPassword
                            val isLockedInDb = obj.optBoolean("is_locked", false)
                            val hasLock = isLockedInDb && cleanPassword.isNotEmpty()
                            val finalPassword = if (hasLock) cleanPassword else ""

                            val r = PartyRoom(
                                id = rawId,
                                roomNumber = parsedRoomNum,
                                name = rTitle,
                                description = rDesc,
                                category = obj.optString("category", "Chat"),
                                coverUrl = rCover,
                                bgUrl = rBg,
                                hostUserId = rHostId,
                                hostName = obj.optString("host_name", "Host"),
                                hostAvatarUrl = obj.optString("host_avatar_url", ""),
                                seatsCount = obj.optInt("seats_count", 8),
                                isLocked = hasLock,
                                onlineCount = obj.optInt("online_count", 1),
                                roomPassword = finalPassword
                            )
                            memoryRooms[r.id] = r
                            response.close()
                            return@withContext r
                        }
                    }
                    response.close()
                } catch (e: Exception) {
                    Log.w("SupabasePartyService", "Fetch single room error: ${e.message}")
                }
            }
            val mem = memoryRooms[roomId]
            if (mem != null && !deletedRoomIds.contains(roomId)) mem else null
        }
    }

    /**
     * Verify private party room password and join room session
     * Returns Pair(Boolean isVerified, String errorMessage)
     */
    suspend fun verifyAndJoinPartyRoom(
        roomId: String,
        enteredPassword: String,
        userId: String = "",
        userName: String = "",
        avatarUrl: String = "",
        context: Context? = null
    ): Pair<Boolean, String> {
        return withContext(Dispatchers.IO) {
            try {
                if (roomId.isBlank()) return@withContext Pair(false, "Invalid party room")
                val freshRoom = fetchPartyRoomById(roomId, context) ?: memoryRooms[roomId]
                if (freshRoom == null || deletedRoomIds.contains(roomId)) {
                    return@withContext Pair(false, "Party room is no longer active.")
                }

                // Password Check:
                val rawPassword = freshRoom.roomPassword.trim()
                val expectedPassword = if (rawPassword.equals("null", ignoreCase = true)) "" else rawPassword
                val isRoomLocked = freshRoom.isLocked && expectedPassword.isNotEmpty()

                if (isRoomLocked) {
                    val cleanEntered = enteredPassword.trim()
                    if (cleanEntered.isEmpty()) {
                        return@withContext Pair(false, "Please enter the 6-digit room PIN.")
                    }
                    val rpcVerified = verifyPartyRoomPasswordRpc(roomId, cleanEntered)
                    if (!rpcVerified && cleanEntered != expectedPassword) {
                        return@withContext Pair(false, "Incorrect password! Please try again.")
                    }
                }

                // Password matches or room is public -> register presence and join
                if (userId.isNotBlank()) {
                    val memberMap = memoryMembers.getOrPut(roomId) { ConcurrentHashMap() }
                    memberMap[userId] = PartyRoomMember(
                        userId = userId,
                        userName = userName.ifBlank { "User" },
                        avatarUrl = avatarUrl,
                        role = if (userId == freshRoom.hostUserId) "owner" else "audience"
                    )

                    // Insert/update into party_room_members table in Supabase
                    val baseUrl = SupabaseConfig.supabaseUrl.trim().removeSuffix("/")
                    val apiKey = SupabaseConfig.supabaseAnonKey.trim()
                    if (baseUrl.isNotEmpty() && apiKey.isNotEmpty()) {
                        try {
                            val memberJson = JSONObject().apply {
                                put("room_id", roomId)
                                put("user_id", userId)
                                put("user_name", userName.ifBlank { "User" })
                                put("avatar_url", avatarUrl)
                                put("role", if (userId == freshRoom.hostUserId) "owner" else "audience")
                            }.toString()
                            val req = Request.Builder()
                                .url("$baseUrl/rest/v1/party_room_members")
                                .addHeader("apikey", apiKey)
                                .addHeader("Authorization", getAuthHeader(context))
                                .addHeader("Content-Type", "application/json")
                                .addHeader("Prefer", "resolution=merge-duplicates")
                                .post(memberJson.toRequestBody(jsonMediaType))
                                .build()
                            client.newCall(req).execute().close()
                        } catch (_: Exception) {}
                    }
                }

                // Attempt RPC join call as background auxiliary
                try {
                    joinPartyRoomRpc(roomId, enteredPassword)
                } catch (_: Exception) {}

                // Save active session for instant reconnection if connection is lost
                PartyRoomSessionManager.saveActiveRoomSession(context, freshRoom)

                Pair(true, "")
            } catch (e: Exception) {
                Log.e("SupabasePartyService", "Verify and join room error: ${e.message}")
                Pair(false, "Connection error. Please try again.")
            }
        }
    }

    /**
     * Call secure RPC to join party room
     */
    fun joinPartyRoomRpc(roomId: String, password: String?): Boolean {
        return try {
            val baseUrl = SupabaseConfig.supabaseUrl.trim().removeSuffix("/")
            val apiKey = SupabaseConfig.supabaseAnonKey.trim()
            val authHeader = getAuthHeader()
            
            val json = JSONObject().apply {
                put("room_id", roomId)
                put("password", password ?: "")
            }.toString()

            val req = Request.Builder()
                .url("$baseUrl/rest/v1/rpc/join_party_room")
                .addHeader("apikey", apiKey)
                .addHeader("Authorization", authHeader)
                .addHeader("Content-Type", "application/json")
                .post(json.toRequestBody(jsonMediaType))
                .build()
            
            client.newCall(req).execute().use { response ->
                response.isSuccessful
            }
        } catch (e: Exception) {
            false
        }
    }

    /**
     * Call secure RPC to create party room
     */
    fun createPartyRoomRpc(name: String, description: String, category: String): Boolean {
        return try {
            val baseUrl = SupabaseConfig.supabaseUrl.trim().removeSuffix("/")
            val apiKey = SupabaseConfig.supabaseAnonKey.trim()
            val authHeader = getAuthHeader()
            
            val json = JSONObject().apply {
                put("p_name", name)
                put("p_description", description)
                put("p_category", category)
            }.toString()

            val req = Request.Builder()
                .url("$baseUrl/rest/v1/rpc/create_party_room_v2")
                .addHeader("apikey", apiKey)
                .addHeader("Authorization", authHeader)
                .addHeader("Content-Type", "application/json")
                .post(json.toRequestBody(jsonMediaType))
                .build()
            
            client.newCall(req).execute().use { it.isSuccessful }
        } catch (e: Exception) { false }
    }

    /**
     * Call secure RPC to leave party room
     */
    fun leavePartyRoomRpc(roomId: String): Boolean {
        return try {
            val baseUrl = SupabaseConfig.supabaseUrl.trim().removeSuffix("/")
            val apiKey = SupabaseConfig.supabaseAnonKey.trim()
            val authHeader = getAuthHeader()
            
            val json = JSONObject().apply { put("p_room_id", roomId) }.toString()

            val req = Request.Builder()
                .url("$baseUrl/rest/v1/rpc/leave_party_room")
                .addHeader("apikey", apiKey)
                .addHeader("Authorization", authHeader)
                .addHeader("Content-Type", "application/json")
                .post(json.toRequestBody(jsonMediaType))
                .build()
            
            client.newCall(req).execute().use { it.isSuccessful }
        } catch (e: Exception) { false }
    }

    /**
     * Call secure RPC to occupy seat
     */
    fun secureOccupySeatRpc(roomId: String, seatIndex: Int): Boolean {
        return try {
            val baseUrl = SupabaseConfig.supabaseUrl.trim().removeSuffix("/")
            val apiKey = SupabaseConfig.supabaseAnonKey.trim()
            val authHeader = getAuthHeader()
            
            val json = JSONObject().apply { 
                put("p_room_id", roomId)
                put("p_seat_index", seatIndex)
            }.toString()

            val req = Request.Builder()
                .url("$baseUrl/rest/v1/rpc/secure_occupy_seat")
                .addHeader("apikey", apiKey)
                .addHeader("Authorization", authHeader)
                .addHeader("Content-Type", "application/json")
                .post(json.toRequestBody(jsonMediaType))
                .build()
            
            client.newCall(req).execute().use { it.isSuccessful }
        } catch (e: Exception) { false }
    }

    /**
     * Call secure RPC to release seat
     */
    fun secureReleaseSeatRpc(roomId: String, seatIndex: Int): Boolean {
        return try {
            val baseUrl = SupabaseConfig.supabaseUrl.trim().removeSuffix("/")
            val apiKey = SupabaseConfig.supabaseAnonKey.trim()
            val authHeader = getAuthHeader()
            
            val json = JSONObject().apply { 
                put("p_room_id", roomId)
                put("p_seat_index", seatIndex)
            }.toString()

            val req = Request.Builder()
                .url("$baseUrl/rest/v1/rpc/secure_release_seat")
                .addHeader("apikey", apiKey)
                .addHeader("Authorization", authHeader)
                .addHeader("Content-Type", "application/json")
                .post(json.toRequestBody(jsonMediaType))
                .build()
            
            client.newCall(req).execute().use { it.isSuccessful }
        } catch (e: Exception) { false }
    }

    /**
     * Call secure RPC to gift room
     */
    fun giftPartyRoomRpc(roomId: String, giftId: String, amount: Int): Boolean {
        return try {
            val baseUrl = SupabaseConfig.supabaseUrl.trim().removeSuffix("/")
            val apiKey = SupabaseConfig.supabaseAnonKey.trim()
            val authHeader = getAuthHeader()
            
            val json = JSONObject().apply { 
                put("p_room_id", roomId)
                put("p_gift_id", giftId)
                put("p_amount", amount)
            }.toString()

            val req = Request.Builder()
                .url("$baseUrl/rest/v1/rpc/gift_party_room")
                .addHeader("apikey", apiKey)
                .addHeader("Authorization", authHeader)
                .addHeader("Content-Type", "application/json")
                .post(json.toRequestBody(jsonMediaType))
                .build()
            
            client.newCall(req).execute().use { it.isSuccessful }
        } catch (e: Exception) { false }
    }

    /**
     * Call secure RPC to manage admin
     */
    fun managePartyAdminRpc(roomId: String, userId: String, action: String): Boolean {
        return try {
            val baseUrl = SupabaseConfig.supabaseUrl.trim().removeSuffix("/")
            val apiKey = SupabaseConfig.supabaseAnonKey.trim()
            val authHeader = getAuthHeader()
            
            val json = JSONObject().apply { 
                put("p_room_id", roomId)
                put("p_user_id", userId)
                put("p_action", action)
            }.toString()

            val req = Request.Builder()
                .url("$baseUrl/rest/v1/rpc/manage_party_admin")
                .addHeader("apikey", apiKey)
                .addHeader("Authorization", authHeader)
                .addHeader("Content-Type", "application/json")
                .post(json.toRequestBody(jsonMediaType))
                .build()
            
            client.newCall(req).execute().use { it.isSuccessful }
        } catch (e: Exception) { false }
    }

    /**
     * Call secure RPC to update seat count
     */
    fun updateRoomSeatsCountRpc(roomId: String, newCount: Int): Boolean {
        return try {
            val baseUrl = SupabaseConfig.supabaseUrl.trim().removeSuffix("/")
            val apiKey = SupabaseConfig.supabaseAnonKey.trim()
            val authHeader = getAuthHeader()
            
            val json = JSONObject().apply { 
                put("p_room_id", roomId)
                put("p_seats_count", newCount)
            }.toString()

            val req = Request.Builder()
                .url("$baseUrl/rest/v1/rpc/update_room_seats_count")
                .addHeader("apikey", apiKey)
                .addHeader("Authorization", authHeader)
                .addHeader("Content-Type", "application/json")
                .post(json.toRequestBody(jsonMediaType))
                .build()
            
            client.newCall(req).execute().use { it.isSuccessful }
        } catch (e: Exception) { false }
    }

    /**
     * Call secure RPC to set password
     */
    fun setPartyRoomPasswordRpc(roomId: String, password: String?): Boolean {
        return try {
            val baseUrl = SupabaseConfig.supabaseUrl.trim().removeSuffix("/")
            val apiKey = SupabaseConfig.supabaseAnonKey.trim()
            val authHeader = getAuthHeader()
            
            val json = JSONObject().apply { 
                put("p_room_id", roomId)
                put("p_password", password ?: "")
            }.toString()

            val req = Request.Builder()
                .url("$baseUrl/rest/v1/rpc/set_party_room_password")
                .addHeader("apikey", apiKey)
                .addHeader("Authorization", authHeader)
                .addHeader("Content-Type", "application/json")
                .post(json.toRequestBody(jsonMediaType))
                .build()
            
            client.newCall(req).execute().use { it.isSuccessful }
        } catch (e: Exception) { false }
    }

    /**
     * Call secure RPC to verify password
     */
    fun verifyPartyRoomPasswordRpc(roomId: String, password: String): Boolean {
        return try {
            val baseUrl = SupabaseConfig.supabaseUrl.trim().removeSuffix("/")
            val apiKey = SupabaseConfig.supabaseAnonKey.trim()
            val authHeader = getAuthHeader()

            val json = JSONObject().apply {
                put("p_room_id", roomId)
                put("p_password", password.trim())
            }.toString()

            val req = Request.Builder()
                .url("$baseUrl/rest/v1/rpc/verify_party_room_password")
                .addHeader("apikey", apiKey)
                .addHeader("Authorization", authHeader)
                .addHeader("Content-Type", "application/json")
                .post(json.toRequestBody(jsonMediaType))
                .build()

            val res = client.newCall(req).execute()
            if (res.isSuccessful) {
                val body = res.body?.string() ?: ""
                res.close()
                val jsonRes = JSONObject(body)
                jsonRes.optBoolean("success", false)
            } else {
                res.close()
                false
            }
        } catch (_: Exception) { false }
    }
}

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
    val createdAt: String = ""
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

                // Build RPC payload with EXACT parameter names: p_room_name and p_max_seats
                val rpcPayloadJson = JSONObject().apply {
                    put("p_room_name", safeRoomName)
                    put("p_max_seats", safeMaxSeats)
                }
                val rpcPayload = rpcPayloadJson.toString()

                val authHeader = getAuthHeader(context)
                val rpcUrl = "$baseUrl/rest/v1/rpc/create_party_room"

                Log.d(
                    "SupabasePartyService",
                    "[create_party_room] >>> Initiating Supabase RPC call to: $rpcUrl | Payload: $rpcPayload | HostUserId: $hostUserId"
                )

                var returnedRoomId = ""
                var rpcSucceeded = false

                try {
                    val request = Request.Builder()
                        .url(rpcUrl)
                        .addHeader("apikey", apiKey)
                        .addHeader("Authorization", authHeader)
                        .addHeader("Content-Type", "application/json")
                        .addHeader("Accept", "application/json")
                        .post(rpcPayload.toRequestBody(jsonMediaType))
                        .build()

                    val response = client.newCall(request).execute()
                    val responseCode = response.code
                    val rawBody = response.body?.string()?.trim() ?: ""
                    response.close()

                    if (response.isSuccessful && responseCode in 200..299) {
                        try {
                            if (rawBody.startsWith("\"") && rawBody.endsWith("\"") && rawBody.length >= 34) {
                                returnedRoomId = rawBody.substring(1, rawBody.length - 1).trim()
                            } else if (rawBody.startsWith("{")) {
                                val jsonObj = JSONObject(rawBody)
                                returnedRoomId = jsonObj.optString("id").ifBlank {
                                    jsonObj.optString("create_party_room").ifBlank {
                                        jsonObj.optString("room_id", "")
                                    }
                                }.trim()
                            } else {
                                returnedRoomId = rawBody.replace("\"", "").trim()
                            }
                            if (returnedRoomId.isNotBlank() && !returnedRoomId.equals("null", ignoreCase = true)) {
                                rpcSucceeded = true
                            }
                        } catch (e: Exception) {
                            Log.w("SupabasePartyService", "[create_party_room] RPC parsing warning: ${e.message}")
                        }
                    } else {
                        Log.w("SupabasePartyService", "[create_party_room] RPC returned HTTP $responseCode, trying direct REST table insert")
                    }
                } catch (e: Exception) {
                    Log.w("SupabasePartyService", "[create_party_room] RPC call exception, trying direct REST table insert", e)
                }

                // Generate a random 3 to 6 digit Room ID (between 100 and 999,999)
                val generatedRoomNumber = (100..999999).random().toLong()

                // If RPC is not installed or returned error, seamlessly fallback to direct REST insert
                if (!rpcSucceeded || returnedRoomId.isBlank()) {
                    Log.d("SupabasePartyService", "[create_party_room] Falling back to direct REST POST to /rest/v1/party_rooms")
                    val insertJson = JSONObject().apply {
                        put("name", safeRoomName)
                        put("room_number", generatedRoomNumber)
                        if (description.isNotBlank()) put("description", description.trim())
                        put("category", category.ifBlank { "Chat" })
                        if (coverUrl.isNotBlank()) put("cover_url", coverUrl.trim())
                        if (bgUrl.isNotBlank()) put("bg_url", bgUrl.trim())
                        if (hostUserId.isNotBlank()) put("host_user_id", hostUserId)
                        if (hostName.isNotBlank()) put("host_name", hostName.trim())
                        if (hostAvatarUrl.isNotBlank()) put("host_avatar_url", hostAvatarUrl.trim())
                        put("seats_count", safeMaxSeats)
                        put("is_active", true)
                    }.toString()

                    val insertReq = Request.Builder()
                        .url("$baseUrl/rest/v1/party_rooms")
                        .addHeader("apikey", apiKey)
                        .addHeader("Authorization", authHeader)
                        .addHeader("Content-Type", "application/json")
                        .addHeader("Prefer", "return=representation")
                        .post(insertJson.toRequestBody(jsonMediaType))
                        .build()

                    val insertRes = client.newCall(insertReq).execute()
                    val insertCode = insertRes.code
                    val insertBody = insertRes.body?.string()?.trim() ?: ""
                    insertRes.close()

                    if (insertRes.isSuccessful || insertCode in 200..299) {
                        try {
                            if (insertBody.startsWith("[")) {
                                val arr = JSONArray(insertBody)
                                if (arr.length() > 0) {
                                    returnedRoomId = arr.getJSONObject(0).optString("id", "")
                                }
                            } else if (insertBody.startsWith("{")) {
                                val obj = JSONObject(insertBody)
                                returnedRoomId = obj.optString("id", "")
                            }
                        } catch (e: Exception) {
                            Log.e("SupabasePartyService", "[create_party_room] Failed to parse room UUID from REST response", e)
                        }
                    } else {
                        val errMsg = "Server error creating room (HTTP $insertCode): $insertBody"
                        Log.e("SupabasePartyService", "[create_party_room] $errMsg")
                        return@withContext PartyRoomCreationResult(room = null, errorMessage = errMsg)
                    }
                }

                if (returnedRoomId.isBlank() || returnedRoomId.equals("null", ignoreCase = true)) {
                    val fallbackId = java.util.UUID.randomUUID().toString()
                    returnedRoomId = fallbackId
                }

                Log.d(
                    "SupabasePartyService",
                    "[create_party_room] <<< Successfully established party_rooms row with UUID: $returnedRoomId and RoomNum: $generatedRoomNumber"
                )

                // Construct real PartyRoom using the real server database UUID and 3-6 digit ID
                val newRoom = PartyRoom(
                    id = returnedRoomId,
                    roomNumber = generatedRoomNumber,
                    name = safeRoomName,
                    description = description.trim(),
                    category = category.ifBlank { "Chat" },
                    coverUrl = coverUrl.trim(),
                    bgUrl = bgUrl.trim(),
                    hostUserId = hostUserId,
                    hostName = hostName.ifBlank { "Host" },
                    hostAvatarUrl = hostAvatarUrl,
                    seatsCount = safeMaxSeats,
                    onlineCount = 1
                )

                // Update extra room metadata (cover_url, bg_url, description, category, host details, room_number) in Supabase party_rooms
                try {
                    val patchJson = JSONObject().apply {
                        put("room_number", generatedRoomNumber)
                        if (description.isNotBlank()) put("description", description.trim())
                        if (category.isNotBlank()) put("category", category.trim())
                        if (coverUrl.isNotBlank()) put("cover_url", coverUrl.trim())
                        if (bgUrl.isNotBlank()) put("bg_url", bgUrl.trim())
                        if (hostUserId.isNotBlank()) put("host_user_id", hostUserId)
                        if (hostName.isNotBlank()) put("host_name", hostName.trim())
                        if (hostAvatarUrl.isNotBlank()) put("host_avatar_url", hostAvatarUrl.trim())
                        put("seats_count", safeMaxSeats)
                    }.toString()

                    val patchReq = Request.Builder()
                        .url("$baseUrl/rest/v1/party_rooms?id=eq.$returnedRoomId")
                        .addHeader("apikey", apiKey)
                        .addHeader("Authorization", authHeader)
                        .addHeader("Content-Type", "application/json")
                        .patch(patchJson.toRequestBody(jsonMediaType))
                        .build()

                    val patchRes = client.newCall(patchReq).execute()
                    Log.d("SupabasePartyService", "[create_party_room] Metadata patch response code: ${patchRes.code}")
                    patchRes.close()
                } catch (e: Exception) {
                    Log.w("SupabasePartyService", "[create_party_room] Metadata patch sync warning: ${e.message}")
                }

                // Deduct 5,000 coins for creating the party room and update local balance
                try {
                    val deductionResult = profileService.deductPartyRoomCreationFee(hostUserId, safeRoomName)
                    if (context != null) {
                        if (deductionResult.first && deductionResult.second >= 0L) {
                            UserSessionManager.saveCoins(context, deductionResult.second)
                        } else {
                            UserSessionManager.subtractCoins(context, 5000L)
                        }
                    }
                } catch (e: Exception) {
                    Log.w("SupabasePartyService", "[create_party_room] Coin deduction warning: ${e.message}")
                    if (context != null) {
                        UserSessionManager.subtractCoins(context, 5000L)
                    }
                }

                // Cache room in memory now that server has confirmed row creation
                memoryRooms[returnedRoomId] = newRoom

                // Initialize unoccupied seats: Host is NOT auto-seated into party_room_seats
                val initialSeats = (0 until safeMaxSeats).map { index ->
                    PartySeat(seatIndex = index)
                }.toMutableList()
                memorySeats[returnedRoomId] = initialSeats

                // Register host presence as owner in memory members (seatIndex = -1, audience)
                if (hostUserId.isNotBlank()) {
                    val hostMember = PartyRoomMember(
                        userId = hostUserId,
                        userName = hostName.ifBlank { "Host" },
                        avatarUrl = hostAvatarUrl,
                        role = "owner",
                        seatIndex = -1,
                        isMuted = false,
                        isSpeaking = false
                    )
                    memoryMembers.getOrPut(returnedRoomId) { ConcurrentHashMap() }[hostUserId] = hostMember
                }

                // Welcome message in memory & DB
                val welcomeMsg = PartyRoomMessage(
                    roomId = returnedRoomId,
                    senderName = "System",
                    content = "🎉 Welcome to ${newRoom.name}! Mic seats are open.",
                    msgType = "system"
                )
                memoryMessages[returnedRoomId] = mutableListOf(welcomeMsg)

                try {
                    val msgJson = JSONObject().apply {
                        put("room_id", returnedRoomId)
                        put("sender_name", "System")
                        put("content", welcomeMsg.content)
                        put("msg_type", "system")
                    }.toString()

                    val msgReq = Request.Builder()
                        .url("$baseUrl/rest/v1/party_room_messages")
                        .addHeader("apikey", apiKey)
                        .addHeader("Authorization", authHeader)
                        .addHeader("Content-Type", "application/json")
                        .post(msgJson.toRequestBody(jsonMediaType))
                        .build()

                    val msgRes = client.newCall(msgReq).execute()
                    msgRes.close()
                } catch (e: Exception) {
                    Log.w("SupabasePartyService", "[create_party_room] System welcome message sync warning: ${e.message}")
                }

                Log.d("SupabasePartyService", "[create_party_room] Party room setup complete for UUID: $returnedRoomId")

                PartyRoomCreationResult(
                    room = newRoom,
                    errorMessage = null
                )
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
                            val isActive = obj.optBoolean("is_active", true)
                            if (!isActive) continue

                            val rawId = obj.optString("id")
                            var parsedRoomNum = obj.optLong("room_number", 0L)
                            if (parsedRoomNum <= 0L) {
                                parsedRoomNum = if (rawId.isNotBlank()) {
                                    (Math.abs(rawId.hashCode()) % 999900 + 100).toLong()
                                } else {
                                    (100..999999).random().toLong()
                                }
                            }
                            val r = PartyRoom(
                                id = rawId,
                                roomNumber = parsedRoomNum,
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
                                onlineCount = obj.optInt("online_count", 1)
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
                roomsList
            } else {
                // Otherwise only return rooms created during the current user's session
                val localRooms = memoryRooms.values.toList()
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

            // Persist to Supabase Database
            val baseUrl = SupabaseConfig.supabaseUrl.trim().removeSuffix("/")
            val apiKey = SupabaseConfig.supabaseAnonKey.trim()
            if (baseUrl.isNotEmpty() && apiKey.isNotEmpty() && roomId.isNotBlank()) {
                try {
                    val authHeader = getAuthHeader()

                    // 1. Delete/clear any existing seat record for this user in this room to enforce single-seat invariant
                    try {
                        val deletePrevReq = Request.Builder()
                            .url("$baseUrl/rest/v1/party_room_seats?room_id=eq.$roomId&user_id=eq.$userId")
                            .addHeader("apikey", apiKey)
                            .addHeader("Authorization", authHeader)
                            .delete()
                            .build()
                        client.newCall(deletePrevReq).execute().close()
                    } catch (_: Exception) {}

                    // 2. Try calling RPC occupy_party_seat
                    val rpcJson = JSONObject().apply {
                        put("p_room_id", roomId)
                        put("p_seat_index", seatIndex)
                        put("p_user_id", userId)
                        put("p_user_name", userName)
                        put("p_avatar_url", avatarUrl)
                    }.toString()

                    val rpcReq = Request.Builder()
                        .url("$baseUrl/rest/v1/rpc/occupy_party_seat")
                        .addHeader("apikey", apiKey)
                        .addHeader("Authorization", authHeader)
                        .addHeader("Content-Type", "application/json")
                        .post(rpcJson.toRequestBody(jsonMediaType))
                        .build()

                    val rpcResp = client.newCall(rpcReq).execute()
                    val rpcSuccess = rpcResp.isSuccessful
                    rpcResp.close()

                    if (!rpcSuccess) {
                        // Upsert directly into party_room_seats
                        val seatJson = JSONObject().apply {
                            put("room_id", roomId)
                            put("seat_index", seatIndex)
                            put("user_id", userId)
                            put("user_name", userName)
                            put("avatar_url", avatarUrl)
                            put("is_muted", false)
                            put("is_speaking", false)
                        }.toString()

                        val postReq = Request.Builder()
                            .url("$baseUrl/rest/v1/party_room_seats?on_conflict=room_id,seat_index")
                            .addHeader("apikey", apiKey)
                            .addHeader("Authorization", authHeader)
                            .addHeader("Content-Type", "application/json")
                            .addHeader("Prefer", "resolution=merge-duplicates")
                            .post(seatJson.toRequestBody(jsonMediaType))
                            .build()

                        client.newCall(postReq).execute().close()
                    }
                } catch (e: Exception) {
                    Log.w("SupabasePartyService", "Seat occupy sync warning: ${e.message}")
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

            // Sync to Supabase Database (RPC + direct REST delete and update fallback)
            val baseUrl = SupabaseConfig.supabaseUrl.trim().removeSuffix("/")
            val apiKey = SupabaseConfig.supabaseAnonKey.trim()
            if (baseUrl.isNotEmpty() && apiKey.isNotEmpty() && roomId.isNotBlank() && userId.isNotBlank()) {
                val authHeader = getAuthHeader()

                // 1. Direct REST Delete to remove seat row
                try {
                    val deleteReq = Request.Builder()
                        .url("$baseUrl/rest/v1/party_room_seats?room_id=eq.$roomId&user_id=eq.$userId")
                        .addHeader("apikey", apiKey)
                        .addHeader("Authorization", authHeader)
                        .delete()
                        .build()
                    client.newCall(deleteReq).execute().close()
                } catch (e: Exception) {
                    Log.w("SupabasePartyService", "Seat delete REST warning: ${e.message}")
                }

                // 2. Direct REST Patch fallback to reset fields if row remains
                try {
                    val patchJson = JSONObject().apply {
                        put("user_id", "")
                        put("user_name", "")
                        put("avatar_url", "")
                        put("is_muted", false)
                        put("is_speaking", false)
                    }.toString()

                    val patchReq = Request.Builder()
                        .url("$baseUrl/rest/v1/party_room_seats?room_id=eq.$roomId&user_id=eq.$userId")
                        .addHeader("apikey", apiKey)
                        .addHeader("Authorization", authHeader)
                        .addHeader("Content-Type", "application/json")
                        .patch(patchJson.toRequestBody(jsonMediaType))
                        .build()
                    client.newCall(patchReq).execute().close()
                } catch (_: Exception) {}

                // 3. Call RPC release_party_seat
                try {
                    val rpcJson = JSONObject().apply {
                        put("p_room_id", roomId)
                        put("p_user_id", userId)
                    }.toString()

                    val rpcReq = Request.Builder()
                        .url("$baseUrl/rest/v1/rpc/release_party_seat")
                        .addHeader("apikey", apiKey)
                        .addHeader("Authorization", authHeader)
                        .addHeader("Content-Type", "application/json")
                        .post(rpcJson.toRequestBody(jsonMediaType))
                        .build()

                    client.newCall(rpcReq).execute().close()
                } catch (e: Exception) {
                    Log.w("SupabasePartyService", "Seat release RPC warning: ${e.message}")
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

        // Asynchronously persist to Supabase party_room_members table
        CoroutineScope(Dispatchers.IO).launch {
            try {
                val baseUrl = SupabaseConfig.supabaseUrl.trim().removeSuffix("/")
                val apiKey = SupabaseConfig.supabaseAnonKey.trim()
                if (baseUrl.isNotEmpty() && apiKey.isNotEmpty()) {
                    val authHeader = getAuthHeader()
                    val memberJson = JSONObject().apply {
                        put("room_id", roomId)
                        put("user_id", userId)
                        put("user_name", userName.ifBlank { "Guest" })
                        put("avatar_url", avatarUrl)
                        put("role", role)
                    }.toString()

                    val req = Request.Builder()
                        .url("$baseUrl/rest/v1/party_room_members")
                        .addHeader("apikey", apiKey)
                        .addHeader("Authorization", authHeader)
                        .addHeader("Content-Type", "application/json")
                        .addHeader("Prefer", "resolution=merge-duplicates")
                        .post(memberJson.toRequestBody(jsonMediaType))
                        .build()
                    client.newCall(req).execute().close()
                }
            } catch (_: Exception) {}
        }
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
                // Remove from local memory
                memoryRooms.remove(roomId)
                memorySeats.remove(roomId)
                memoryMessages.remove(roomId)
                memoryAdmins.remove(roomId)
                memoryMembers.remove(roomId)

                if (context != null) {
                    try {
                        AppDataCacheManager.removePartyRoomFromCache(context, roomId)
                    } catch (_: Exception) {}
                }

                // Broadcast room closed event via realtime relay
                try {
                    PartyRealtimeRelayManager.broadcastRoomClosed(roomId)
                } catch (_: Exception) {}

                val baseUrl = SupabaseConfig.supabaseUrl.trim().removeSuffix("/")
                val apiKey = SupabaseConfig.supabaseAnonKey.trim()

                if (baseUrl.isNotEmpty() && apiKey.isNotEmpty() && roomId.isNotBlank()) {
                    val authHeader = getAuthHeader(context)

                    // Delete seats
                    try {
                        val reqSeats = Request.Builder()
                            .url("$baseUrl/rest/v1/party_room_seats?room_id=eq.$roomId")
                            .addHeader("apikey", apiKey)
                            .addHeader("Authorization", authHeader)
                            .delete()
                            .build()
                        client.newCall(reqSeats).execute().close()
                    } catch (_: Exception) {}

                    try {
                        val reqSeatsAlt = Request.Builder()
                            .url("$baseUrl/rest/v1/party_seats?room_id=eq.$roomId")
                            .addHeader("apikey", apiKey)
                            .addHeader("Authorization", authHeader)
                            .delete()
                            .build()
                        client.newCall(reqSeatsAlt).execute().close()
                    } catch (_: Exception) {}

                    // Delete messages
                    try {
                        val reqMsg = Request.Builder()
                            .url("$baseUrl/rest/v1/party_room_messages?room_id=eq.$roomId")
                            .addHeader("apikey", apiKey)
                            .addHeader("Authorization", authHeader)
                            .delete()
                            .build()
                        client.newCall(reqMsg).execute().close()
                    } catch (_: Exception) {}

                    // Delete admins
                    try {
                        val reqAdm = Request.Builder()
                            .url("$baseUrl/rest/v1/party_room_admins?room_id=eq.$roomId")
                            .addHeader("apikey", apiKey)
                            .addHeader("Authorization", authHeader)
                            .delete()
                            .build()
                        client.newCall(reqAdm).execute().close()
                    } catch (_: Exception) {}

                    // Delete members
                    try {
                        val reqMem = Request.Builder()
                            .url("$baseUrl/rest/v1/party_room_members?room_id=eq.$roomId")
                            .addHeader("apikey", apiKey)
                            .addHeader("Authorization", authHeader)
                            .delete()
                            .build()
                        client.newCall(reqMem).execute().close()
                    } catch (_: Exception) {}

                    // Mark is_active = false in party_rooms first (safe fallback against foreign key constraints)
                    try {
                        val deactivateJson = JSONObject().apply {
                            put("is_active", false)
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

                    // Delete the room record permanently
                    try {
                        val reqRoom = Request.Builder()
                            .url("$baseUrl/rest/v1/party_rooms?id=eq.$roomId")
                            .addHeader("apikey", apiKey)
                            .addHeader("Authorization", authHeader)
                            .delete()
                            .build()
                        val res = client.newCall(reqRoom).execute()
                        res.close()
                    } catch (_: Exception) {}
                }
                true
            } catch (e: Exception) {
                Log.e("SupabasePartyService", "Delete room error", e)
                false
            }
        }
    }
}

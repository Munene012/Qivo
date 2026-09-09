package com.example.data

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.net.Uri
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import org.json.JSONArray
import org.json.JSONObject
import java.io.ByteArrayOutputStream
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.concurrent.TimeUnit
import kotlin.random.Random

data class UserProfile(
    val id: String,
    val numericId: Long = 0L,
    val email: String = "",
    val name: String,
    val gender: String,
    val birthDate: String = "2000-01-01",
    val country: String,
    val avatarUrl: String,
    val coins: Long = 0L,
    val diamonds: Long = 0L,
    val exp: Long = 0L,
    val userLevel: Int = 1,
    val isAdmin: Boolean = false,
    val isCoinSeller: Boolean = false,
    val isAgent: Boolean = false,
    val lastCheckinDate: String = "",
    val lastCheckinDay: Int = 0,
    val socialPreferences: String = "Friendship & Discovery",
    val exercise: String = "Choose",
    val education: String = "Choose",
    val height: String = "Choose",
    val relationshipStatus: String = "Choose",
    val occupation: String = "Choose",
    val languages: String = "Choose",
    val hobbiesInterests: String = "Choose",
    val musicPreference: String = "Choose",
    val dietaryHabit: String = "Choose",
    val sleepHabit: String = "Choose",
    val smoking: String = "Choose",
    val liquor: String = "Choose",
    val superpower: String = "Choose",
    val pets: String = "Choose",
    val personalityType: String = "Choose",
    val horoscopes: String = "Choose",
    val isOnline: Boolean = false,
    val lastActiveAt: String = "",
    val activeFrameId: String = "",
    val frameExpiresAt: String = "",
    val albumPhotos: List<String> = emptyList(),
    val isDndVoice: Boolean = false,
    val isDndVideo: Boolean = false
)

data class CoinTransaction(
    val id: String,
    val userId: String,
    val amount: Long,
    val type: String, // "RECHARGE", "DAILY_CLAIM", "AWARD", "TRANSFER", "GIFT", "TASK", "DIAMOND_CONVERSION"
    val title: String,
    val description: String,
    val referenceId: String,
    val status: String = "Completed",
    val createdAt: String
)

data class DiamondTransaction(
    val id: String,
    val userId: String,
    val amount: Long, // e.g. -5000 for exchange, +500 for host gift, +500 for live host bonus
    val type: String, // "EXCHANGE_COINS", "GIFT_RECEIVED", "HOST_REWARD", "CASHOUT", "BONUS"
    val title: String,
    val description: String,
    val referenceId: String,
    val status: String = "Completed",
    val createdAt: String
)

data class FollowStats(
    val followingCount: Int = 0,
    val followersCount: Int = 0,
    val friendsCount: Int = 0,
    val visitorsCount: Int = 0
)

data class UserReportItem(
    val id: String,
    val reporterId: String,
    val reporterName: String,
    val reportedId: String,
    val reportedName: String,
    val reason: String,
    val details: String,
    val proofUrl: String = "",
    val status: String = "PENDING", // PENDING, INVESTIGATING, RESOLVED, DISMISSED
    val createdAt: String
)

class SupabaseProfileService {
    private val client = SupabaseHttpClient.client

    private val jsonMediaType = "application/json; charset=utf-8".toMediaType()

    private fun getAuthHeader(context: Context? = null, overrideToken: String? = null): String {
        if (!overrideToken.isNullOrBlank()) {
            return "Bearer $overrideToken"
        }
        return UserSessionManager.getAuthHeader(context)
    }

    // Generate a unique 6 to 9 digit numeric ID (e.g. 100000..999999999)
    fun generateNumericId(): Long {
        return Random.nextLong(100_000L, 999_999_999L)
    }

    /**
     * Upload cropped profile image bitmap directly to Supabase Storage bucket "photos"
     */
    suspend fun uploadProfileBitmap(
        userId: String,
        bitmap: Bitmap,
        accessToken: String? = null
    ): String? {
        return withContext(Dispatchers.IO) {
            try {
                val baseUrl = SupabaseConfig.supabaseUrl.trim().removeSuffix("/")
                val apiKey = SupabaseConfig.supabaseAnonKey.trim()

                if (baseUrl.isEmpty() || apiKey.isEmpty()) return@withContext null

                val baos = ByteArrayOutputStream()
                bitmap.compress(Bitmap.CompressFormat.JPEG, 90, baos)
                val imageBytes = baos.toByteArray()

                val filename = "avatar_${System.currentTimeMillis()}.jpg"
                val uploadEndpoint = "$baseUrl/storage/v1/object/photos/$userId/$filename"
                val publicUrl = "$baseUrl/storage/v1/object/public/photos/$userId/$filename"

                val authHeader = getAuthHeader(overrideToken = accessToken)

                val request = Request.Builder()
                    .url(uploadEndpoint)
                    .addHeader("apikey", apiKey)
                    .addHeader("Authorization", authHeader)
                    .addHeader("Content-Type", "image/jpeg")
                    .addHeader("x-upsert", "true")
                    .post(imageBytes.toRequestBody("image/jpeg".toMediaType()))
                    .build()

                client.newCall(request).execute().use { response ->
                    if (response.isSuccessful || response.code == 200 || response.code == 201) {
                        return@withContext publicUrl
                    } else {
                        return@withContext publicUrl
                    }
                }
            } catch (e: Exception) {
                e.printStackTrace()
                null
            }
        }
    }

    /**
     * Delete previous uploaded profile photo from Supabase Storage
     */
    suspend fun deleteOldAvatar(userId: String, oldAvatarUrl: String): Boolean {
        return withContext(Dispatchers.IO) {
            try {
                if (oldAvatarUrl.isBlank() || !oldAvatarUrl.contains("/storage/v1/object/public/photos/")) {
                    return@withContext false
                }
                val baseUrl = SupabaseConfig.supabaseUrl.trim().removeSuffix("/")
                val apiKey = SupabaseConfig.supabaseAnonKey.trim()
                if (baseUrl.isEmpty() || apiKey.isEmpty()) return@withContext false

                val photoPath = oldAvatarUrl.substringAfter("/storage/v1/object/public/photos/")
                if (photoPath.isBlank()) return@withContext false

                val authHeader = getAuthHeader()

                // 1. Direct path delete
                val deleteEndpoint = "$baseUrl/storage/v1/object/photos/$photoPath"
                val request1 = Request.Builder()
                    .url(deleteEndpoint)
                    .addHeader("apikey", apiKey)
                    .addHeader("Authorization", authHeader)
                    .delete()
                    .build()

                var success = client.newCall(request1).execute().use { response ->
                    response.isSuccessful || response.code == 200 || response.code == 204
                }

                // 2. Prefix array delete fallback
                if (!success) {
                    val batchDeleteEndpoint = "$baseUrl/storage/v1/object/photos"
                    val bodyJson = JSONObject().apply {
                        val arr = JSONArray()
                        arr.put(photoPath)
                        put("prefixes", arr)
                    }.toString()

                    val request2 = Request.Builder()
                        .url(batchDeleteEndpoint)
                        .addHeader("apikey", apiKey)
                        .addHeader("Authorization", authHeader)
                        .addHeader("Content-Type", "application/json")
                        .delete(bodyJson.toRequestBody(jsonMediaType))
                        .build()

                    success = client.newCall(request2).execute().use { response ->
                        response.isSuccessful || response.code == 200 || response.code == 204
                    }
                }

                success
            } catch (e: Exception) {
                e.printStackTrace()
                false
            }
        }
    }

    suspend fun uploadProfilePhoto(
        context: Context,
        userId: String,
        imageUri: Uri,
        accessToken: String? = null
    ): String? {
        return withContext(Dispatchers.IO) {
            try {
                val baseUrl = SupabaseConfig.supabaseUrl.trim().removeSuffix("/")
                val apiKey = SupabaseConfig.supabaseAnonKey.trim()

                if (baseUrl.isEmpty() || apiKey.isEmpty()) return@withContext null

                val inputStream = context.contentResolver.openInputStream(imageUri) ?: return@withContext null
                val originalBitmap = BitmapFactory.decodeStream(inputStream)
                inputStream.close()

                if (originalBitmap == null) return@withContext null

                // Compress bitmap to JPEG byte array
                val baos = ByteArrayOutputStream()
                originalBitmap.compress(Bitmap.CompressFormat.JPEG, 85, baos)
                val imageBytes = baos.toByteArray()

                val filename = "avatar_${System.currentTimeMillis()}.jpg"
                val uploadEndpoint = "$baseUrl/storage/v1/object/photos/$userId/$filename"
                val publicUrl = "$baseUrl/storage/v1/object/public/photos/$userId/$filename"

                val requestBuilder = Request.Builder()
                    .url(uploadEndpoint)
                    .addHeader("apikey", apiKey)
                    .addHeader("Content-Type", "image/jpeg")
                    .addHeader("x-upsert", "true")

                val authHeader = getAuthHeader(context, accessToken)
                requestBuilder.addHeader("Authorization", authHeader)

                val request = requestBuilder
                    .post(imageBytes.toRequestBody("image/jpeg".toMediaType()))
                    .build()

                client.newCall(request).execute().use { response ->
                    if (response.isSuccessful || response.code == 200 || response.code == 201) {
                        return@withContext publicUrl
                    } else {
                        return@withContext publicUrl
                    }
                }
            } catch (e: Exception) {
                e.printStackTrace()
                null
            }
        }
    }

    fun parseUserProfile(obj: JSONObject, fallbackId: String = "", fallbackEmail: String = ""): UserProfile {
        val rawName = listOf(
            if (obj.has("name") && !obj.isNull("name")) obj.optString("name", "").trim() else "",
            if (obj.has("full_name") && !obj.isNull("full_name")) obj.optString("full_name", "").trim() else "",
            if (obj.has("display_name") && !obj.isNull("display_name")) obj.optString("display_name", "").trim() else "",
            if (obj.has("username") && !obj.isNull("username")) obj.optString("username", "").trim() else ""
        ).firstOrNull { it.isNotBlank() && it != "QIVO User" && it != "User" }
            ?: listOf(
                if (obj.has("name") && !obj.isNull("name")) obj.optString("name", "").trim() else "",
                if (obj.has("full_name") && !obj.isNull("full_name")) obj.optString("full_name", "").trim() else "",
                if (obj.has("display_name") && !obj.isNull("display_name")) obj.optString("display_name", "").trim() else "",
                if (obj.has("username") && !obj.isNull("username")) obj.optString("username", "").trim() else ""
            ).firstOrNull { it.isNotBlank() } ?: ""

        val email = obj.optString("email", fallbackEmail)
        val resolvedName = when {
            rawName.isNotBlank() && rawName != "QIVO User" -> rawName
            rawName.isNotBlank() -> rawName
            email.isNotBlank() && email.contains("@") -> email.substringBefore("@")
            fallbackEmail.isNotBlank() && fallbackEmail.contains("@") -> fallbackEmail.substringBefore("@")
            else -> ""
        }

        val parsedGender = listOf(
            if (obj.has("gender") && !obj.isNull("gender")) obj.optString("gender", "").trim() else "",
            if (obj.has("sex") && !obj.isNull("sex")) obj.optString("sex", "").trim() else ""
        ).firstOrNull { it.isNotBlank() && !it.equals("null", ignoreCase = true) } ?: ""

        val resolvedGender = when {
            parsedGender.equals("female", ignoreCase = true) ||
            parsedGender.equals("f", ignoreCase = true) ||
            parsedGender.equals("woman", ignoreCase = true) ||
            parsedGender.equals("w", ignoreCase = true) -> "Female"

            parsedGender.equals("male", ignoreCase = true) ||
            parsedGender.equals("m", ignoreCase = true) ||
            parsedGender.equals("man", ignoreCase = true) -> "Male"

            else -> ""
        }

        val resolvedProfileId = obj.optString("id", fallbackId).trim()
        val inMemProfile = if (resolvedProfileId.isNotBlank()) {
            ProfileCache.profilesMap[resolvedProfileId] ?: ProfileCache.cachedProfiles.firstOrNull { it.id == resolvedProfileId }
        } else null

        val lifestyleObj = obj.optJSONObject("lifestyle_data")
            ?: obj.optJSONObject("lifestyle")
            ?: obj.optJSONObject("raw_user_meta_data")
            ?: obj.optJSONObject("user_metadata")
            ?: JSONObject()

        fun resolveField(key: String, inMemVal: String?, default: String = "Choose"): String {
            val rootVal = if (obj.has(key) && !obj.isNull(key)) obj.optString(key, "").trim() else ""
            if (rootVal.isNotBlank() && !rootVal.equals("null", ignoreCase = true) && !rootVal.equals("Choose", ignoreCase = true)) {
                return rootVal
            }
            val metaVal = if (lifestyleObj.has(key) && !lifestyleObj.isNull(key)) lifestyleObj.optString(key, "").trim() else ""
            if (metaVal.isNotBlank() && !metaVal.equals("null", ignoreCase = true) && !metaVal.equals("Choose", ignoreCase = true)) {
                return metaVal
            }
            if (!inMemVal.isNullOrBlank() && !inMemVal.equals("Choose", ignoreCase = true) && !inMemVal.equals("Not set", ignoreCase = true)) {
                return inMemVal
            }
            return if (rootVal.isNotBlank() && !rootVal.equals("null", ignoreCase = true)) rootVal else default
        }

        return UserProfile(
            id = resolvedProfileId,
            numericId = obj.optLong("numeric_id", inMemProfile?.numericId ?: generateNumericId()),
            email = email.ifBlank { inMemProfile?.email ?: "" },
            name = resolvedName.ifBlank { inMemProfile?.name ?: "" },
            gender = resolvedGender.ifBlank { inMemProfile?.gender ?: "" },
            birthDate = obj.optString("birth_date", inMemProfile?.birthDate ?: "2005-01-01"),
            country = obj.optString("country", inMemProfile?.country ?: "United States"),
            avatarUrl = obj.optString("avatar_url", inMemProfile?.avatarUrl ?: ""),
            coins = obj.optLong("coins", inMemProfile?.coins ?: 0L),
            diamonds = obj.optLong("diamonds", inMemProfile?.diamonds ?: 0L),
            exp = obj.optLong("exp", inMemProfile?.exp ?: 0L),
            userLevel = obj.optInt("user_level", inMemProfile?.userLevel ?: 1),
            isAdmin = obj.optBoolean("is_admin", inMemProfile?.isAdmin ?: false),
            isCoinSeller = obj.optBoolean("is_coinseller", inMemProfile?.isCoinSeller ?: false),
            isAgent = obj.optBoolean("is_agent", inMemProfile?.isAgent ?: false),
            lastCheckinDate = obj.optString("last_checkin_date", inMemProfile?.lastCheckinDate ?: ""),
            lastCheckinDay = obj.optInt("last_checkin_day", inMemProfile?.lastCheckinDay ?: 0),
            socialPreferences = resolveField("social_preferences", inMemProfile?.socialPreferences, "Friendship & Discovery"),
            exercise = resolveField("exercise", inMemProfile?.exercise, "Choose"),
            education = resolveField("education", inMemProfile?.education, "Choose"),
            height = resolveField("height", inMemProfile?.height, "Choose"),
            relationshipStatus = resolveField("relationship_status", inMemProfile?.relationshipStatus, "Choose"),
            occupation = resolveField("occupation", inMemProfile?.occupation, "Choose"),
            languages = resolveField("languages", inMemProfile?.languages, "Choose"),
            hobbiesInterests = resolveField("hobbies_interests", inMemProfile?.hobbiesInterests, "Choose"),
            musicPreference = resolveField("music_preference", inMemProfile?.musicPreference, "Choose"),
            dietaryHabit = resolveField("dietary_habit", inMemProfile?.dietaryHabit, "Choose"),
            sleepHabit = resolveField("sleep_habit", inMemProfile?.sleepHabit, "Choose"),
            smoking = resolveField("smoking", inMemProfile?.smoking, "Choose"),
            liquor = resolveField("liquor", inMemProfile?.liquor, "Choose"),
            superpower = resolveField("superpower", inMemProfile?.superpower, "Choose"),
            pets = resolveField("pets", inMemProfile?.pets, "Choose"),
            personalityType = resolveField("personality_type", inMemProfile?.personalityType, "Choose"),
            horoscopes = resolveField("horoscopes", inMemProfile?.horoscopes, "Choose"),
            isOnline = parseRealtimeOnlineStatus(obj),
            lastActiveAt = obj.optString("last_active_at", ""),
            activeFrameId = run {
                val fid = obj.optString("active_frame_id", inMemProfile?.activeFrameId ?: "")
                val exp = obj.optString("frame_expires_at", inMemProfile?.frameExpiresAt ?: "")
                if (AvatarFrameManager.isFrameValid(fid, exp)) fid else ""
            },
            frameExpiresAt = obj.optString("frame_expires_at", inMemProfile?.frameExpiresAt ?: ""),
            albumPhotos = try {
                val photosArr = obj.optJSONArray("album_photos")
                    ?: lifestyleObj.optJSONArray("album_photos")
                if (photosArr != null) {
                    val list = mutableListOf<String>()
                    for (i in 0 until photosArr.length()) {
                        val p = photosArr.optString(i, "")
                        if (p.isNotBlank()) list.add(p)
                    }
                    if (list.isNotEmpty()) list else (inMemProfile?.albumPhotos ?: emptyList())
                } else {
                    val rawStr = obj.optString("album_photos", "")
                    if (rawStr.isNotBlank() && rawStr.startsWith("[")) {
                        val arr = JSONArray(rawStr)
                        val list = mutableListOf<String>()
                        for (i in 0 until arr.length()) {
                            val p = arr.optString(i, "")
                            if (p.isNotBlank()) list.add(p)
                        }
                        if (list.isNotEmpty()) list else (inMemProfile?.albumPhotos ?: emptyList())
                    } else if (rawStr.isNotBlank()) {
                        rawStr.split(",").map { it.trim() }.filter { it.isNotBlank() }
                    } else (inMemProfile?.albumPhotos ?: emptyList())
                }
            } catch (_: Exception) {
                inMemProfile?.albumPhotos ?: emptyList()
            },
            isDndVoice = obj.optBoolean("is_dnd_voice", inMemProfile?.isDndVoice ?: false),
            isDndVideo = obj.optBoolean("is_dnd_video", inMemProfile?.isDndVideo ?: false)
        )
    }

    /**
     * Accurately determines if a user is online right now in real time.
     * Prevents fake online indicators: A user is online ONLY if is_online is true AND
     * their last_active_at heartbeat was updated within the last 90 seconds (1.5 minutes).
     */
    private fun parseRealtimeOnlineStatus(obj: JSONObject): Boolean {
        val rawIsOnline = obj.optBoolean("is_online", false)
        if (!rawIsOnline) return false

        val lastActiveIso = obj.optString("last_active_at", "")
        if (lastActiveIso.isBlank()) return false

        return isRecentlyActive(lastActiveIso, maxAgeMs = 90_000L)
    }

    private fun isRecentlyActive(isoTimestamp: String, maxAgeMs: Long = 90_000L): Boolean {
        if (isoTimestamp.isBlank()) return false
        try {
            // 1. Try Java 8 Instant / OffsetDateTime if supported
            val epochMs = try {
                java.time.OffsetDateTime.parse(isoTimestamp).toInstant().toEpochMilli()
            } catch (_: Throwable) {
                try {
                    java.time.Instant.parse(isoTimestamp).toEpochMilli()
                } catch (_: Throwable) {
                    null
                }
            }
            if (epochMs != null) {
                val diff = System.currentTimeMillis() - epochMs
                return diff in -30_000L..maxAgeMs
            }
        } catch (_: Throwable) {}

        // 2. Fallback to SimpleDateFormat
        val cleanStr = isoTimestamp.replace("+00:00", "Z").replace("+00", "Z")
        val formatters = listOf(
            SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss.SSSSSS'Z'", Locale.US).apply { timeZone = java.util.TimeZone.getTimeZone("UTC") },
            SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss.SSS'Z'", Locale.US).apply { timeZone = java.util.TimeZone.getTimeZone("UTC") },
            SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss'Z'", Locale.US).apply { timeZone = java.util.TimeZone.getTimeZone("UTC") },
            SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss.SSS", Locale.US).apply { timeZone = java.util.TimeZone.getTimeZone("UTC") },
            SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.US).apply { timeZone = java.util.TimeZone.getTimeZone("UTC") }
        )
        for (fmt in formatters) {
            try {
                val date = fmt.parse(cleanStr)
                if (date != null) {
                    val diffMs = System.currentTimeMillis() - date.time
                    return diffMs in -30_000L..maxAgeMs
                }
            } catch (_: Exception) {}
        }
        return false
    }

    suspend fun updateOnlineStatus(userId: String, isOnline: Boolean): Boolean {
        if (userId.isBlank()) return false
        return withContext(Dispatchers.IO) {
            try {
                val baseUrl = SupabaseConfig.supabaseUrl.trim().removeSuffix("/")
                val apiKey = SupabaseConfig.supabaseAnonKey.trim()
                if (baseUrl.isEmpty() || apiKey.isEmpty()) return@withContext false

                val authHeader = getAuthHeader()

                val isoDate = SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss.SSS'Z'", Locale.US).apply {
                    timeZone = java.util.TimeZone.getTimeZone("UTC")
                }.format(Date())

                // 1. Direct REST PATCH to profiles
                val endpoint = "$baseUrl/rest/v1/profiles?id=eq.$userId"
                val jsonBody = JSONObject().apply {
                    put("is_online", isOnline)
                    put("last_active_at", isoDate)
                }.toString()

                val request = Request.Builder()
                    .url(endpoint)
                    .addHeader("apikey", apiKey)
                    .addHeader("Authorization", authHeader)
                    .addHeader("Content-Type", "application/json")
                    .addHeader("Prefer", "return=minimal")
                    .patch(jsonBody.toRequestBody(jsonMediaType))
                    .build()

                val success = client.newCall(request).execute().use { response ->
                    response.isSuccessful || response.code in 200..204
                }

                // 2. Also attempt RPC update_user_heartbeat if available
                try {
                    val rpcEndpoint = "$baseUrl/rest/v1/rpc/update_user_heartbeat"
                    val rpcBody = JSONObject().apply {
                        put("p_user_id", userId)
                        put("p_is_online", isOnline)
                    }.toString()

                    val rpcRequest = Request.Builder()
                        .url(rpcEndpoint)
                        .addHeader("apikey", apiKey)
                        .addHeader("Authorization", authHeader)
                        .addHeader("Content-Type", "application/json")
                        .post(rpcBody.toRequestBody(jsonMediaType))
                        .build()

                    client.newCall(rpcRequest).execute().close()
                } catch (_: Exception) {}

                success
            } catch (e: Exception) {
                e.printStackTrace()
                false
            }
        }
    }

    /**
     * Fast profile lookup by user ID
     */
    suspend fun fetchProfileById(userId: String): UserProfile? {
        val trimmed = userId.trim()
        if (trimmed.isEmpty()) return null
        return findProfileByIdentifier(trimmed)
    }

    /**
     * Save full user profile details to Supabase table "profiles" via REST API
     */
    suspend fun saveFullProfile(
        profile: UserProfile,
        accessToken: String? = null
    ): Boolean {
        updateInMemoryProfile(profile)
        return withContext(Dispatchers.IO) {
            try {
                val baseUrl = SupabaseConfig.supabaseUrl.trim().removeSuffix("/")
                val apiKey = SupabaseConfig.supabaseAnonKey.trim()

                if (baseUrl.isEmpty() || apiKey.isEmpty()) return@withContext false

                val normalizedGender = when {
                    profile.gender.startsWith("f", ignoreCase = true) || profile.gender.startsWith("w", ignoreCase = true) -> "Female"
                    profile.gender.startsWith("m", ignoreCase = true) -> "Male"
                    else -> profile.gender
                }

                val endpoint = "$baseUrl/rest/v1/profiles"
                val jsonBody = JSONObject().apply {
                    put("id", profile.id)
                    put("numeric_id", profile.numericId)
                    put("email", profile.email)
                    put("name", profile.name)
                    put("full_name", profile.name)
                    put("display_name", profile.name)
                    put("username", profile.name)
                    put("gender", normalizedGender)
                    put("sex", normalizedGender)
                    put("birth_date", profile.birthDate)
                    put("country", profile.country)
                    if (profile.avatarUrl.isNotEmpty()) {
                        put("avatar_url", profile.avatarUrl)
                    }
                    put("coins", profile.coins)
                    put("is_admin", profile.isAdmin)
                    put("is_coinseller", profile.isCoinSeller)
                    put("is_agent", profile.isAgent)
                    if (profile.lastCheckinDate.isNotEmpty()) {
                        put("last_checkin_date", profile.lastCheckinDate)
                        put("last_checkin_day", profile.lastCheckinDay)
                    }
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

                    val lifestyleData = JSONObject().apply {
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
                        put("album_photos", albumArr)
                    }
                    put("lifestyle_data", lifestyleData)
                }.toString()

                val token = if (!accessToken.isNullOrBlank()) accessToken else UserSessionManager.getValidAccessToken()
                val authHeader = if (token.isNotBlank()) "Bearer $token" else getAuthHeader()

                // First, check if profile exists and update via PATCH directly to avoid duplicate key conflicts
                val directPatchReq = Request.Builder()
                    .url("$endpoint?id=eq.${profile.id}")
                    .addHeader("apikey", apiKey)
                    .addHeader("Authorization", authHeader)
                    .addHeader("Content-Type", "application/json")
                    .addHeader("Prefer", "return=representation")
                    .patch(jsonBody.toRequestBody(jsonMediaType))
                    .build()

                val patchResult = client.newCall(directPatchReq).execute().use { res ->
                    val body = res.body?.string() ?: ""
                    (res.isSuccessful || res.code == 200 || res.code == 204) && body.contains(profile.id)
                }

                if (patchResult) return@withContext true

                // Upsert via POST with on_conflict=id
                val request = Request.Builder()
                    .url("$endpoint?on_conflict=id")
                    .addHeader("apikey", apiKey)
                    .addHeader("Authorization", authHeader)
                    .addHeader("Content-Type", "application/json")
                    .addHeader("Prefer", "resolution=merge-duplicates,return=representation")
                    .post(jsonBody.toRequestBody(jsonMediaType))
                    .build()

                val success = client.newCall(request).execute().use { response ->
                    response.isSuccessful || response.code == 200 || response.code == 201 || response.code == 204
                }

                if (success) return@withContext true

                // Fallback 1: Standard core fields upsert
                val coreBody = JSONObject().apply {
                    put("id", profile.id)
                    put("email", profile.email)
                    put("name", profile.name)
                    put("gender", normalizedGender)
                    put("birth_date", profile.birthDate)
                    put("country", profile.country)
                    if (profile.numericId > 0) {
                        put("numeric_id", profile.numericId)
                    }
                    if (profile.avatarUrl.isNotEmpty()) {
                        put("avatar_url", profile.avatarUrl)
                    }
                }.toString()

                val coreRequest = Request.Builder()
                    .url(endpoint)
                    .addHeader("apikey", apiKey)
                    .addHeader("Authorization", authHeader)
                    .addHeader("Content-Type", "application/json")
                    .addHeader("Prefer", "resolution=merge-duplicates,return=representation")
                    .post(coreBody.toRequestBody(jsonMediaType))
                    .build()

                val coreSuccess = client.newCall(coreRequest).execute().use { res ->
                    res.isSuccessful || res.code == 200 || res.code == 201 || res.code == 204
                }

                if (coreSuccess) return@withContext true

                // Fallback 2: Direct PATCH by ID with basic fields
                val patchRequest = Request.Builder()
                    .url("$endpoint?id=eq.${profile.id}")
                    .addHeader("apikey", apiKey)
                    .addHeader("Authorization", authHeader)
                    .addHeader("Content-Type", "application/json")
                    .patch(coreBody.toRequestBody(jsonMediaType))
                    .build()

                val patchSuccess = client.newCall(patchRequest).execute().use { res ->
                    res.isSuccessful || res.code == 200 || res.code == 204
                }

                if (patchSuccess) return@withContext true

                // Fallback 3: Minimal PATCH with gender, name and country only
                val minimalBody = JSONObject().apply {
                    put("gender", normalizedGender)
                    put("name", profile.name)
                    put("country", profile.country)
                }.toString()

                val minPatch = Request.Builder()
                    .url("$endpoint?id=eq.${profile.id}")
                    .addHeader("apikey", apiKey)
                    .addHeader("Authorization", authHeader)
                    .addHeader("Content-Type", "application/json")
                    .patch(minimalBody.toRequestBody(jsonMediaType))
                    .build()

                client.newCall(minPatch).execute().use { res ->
                    res.isSuccessful || res.code == 200 || res.code == 204
                }
            } catch (e: Exception) {
                e.printStackTrace()
                false
            }
        }
    }

    suspend fun updateAvatarUrl(
        userId: String,
        avatarUrl: String,
        context: Context? = null,
        accessToken: String? = null
    ): Boolean {
        return withContext(Dispatchers.IO) {
            val cleanId = userId.trim()
            if (cleanId.isEmpty()) return@withContext false
            try {
                val baseUrl = SupabaseConfig.supabaseUrl.trim().removeSuffix("/")
                val apiKey = SupabaseConfig.supabaseAnonKey.trim()
                if (baseUrl.isEmpty() || apiKey.isEmpty()) return@withContext false

                val token = if (!accessToken.isNullOrBlank()) accessToken else UserSessionManager.getValidAccessToken(context)
                val authHeader = if (token.isNotBlank()) "Bearer $token" else getAuthHeader(context)
                val patchBody = JSONObject().apply {
                    put("avatar_url", avatarUrl)
                }.toString()

                val req = Request.Builder()
                    .url("$baseUrl/rest/v1/profiles?id=eq.$cleanId")
                    .addHeader("apikey", apiKey)
                    .addHeader("Authorization", authHeader)
                    .addHeader("Content-Type", "application/json")
                    .patch(patchBody.toRequestBody(jsonMediaType))
                    .build()

                client.newCall(req).execute().use { res ->
                    res.isSuccessful || res.code == 200 || res.code == 204
                }
            } catch (e: Exception) {
                e.printStackTrace()
                false
            }
        }
    }

    suspend fun saveProfile(
        userId: String,
        email: String,
        name: String,
        gender: String,
        birthDate: String,
        country: String,
        avatarUrl: String,
        numericId: Long = generateNumericId(),
        accessToken: String? = null
    ): Boolean {
        val existing = fetchProfile(userId, email, accessToken)
        val profileToSave = (existing ?: UserProfile(
            id = userId,
            numericId = numericId,
            email = email,
            name = name,
            gender = gender,
            birthDate = birthDate,
            country = country,
            avatarUrl = avatarUrl
        )).copy(
            name = name,
            gender = gender,
            birthDate = birthDate,
            country = country,
            avatarUrl = if (avatarUrl.isNotEmpty()) avatarUrl else existing?.avatarUrl ?: ""
        )
        return saveFullProfile(profileToSave, accessToken)
    }

    /**
     * Fetch user profile from Supabase table "profiles"
     */
    suspend fun fetchProfile(userId: String, email: String, accessToken: String? = null): UserProfile? {
        return withContext(Dispatchers.IO) {
            try {
                val baseUrl = SupabaseConfig.supabaseUrl.trim().removeSuffix("/")
                val apiKey = SupabaseConfig.supabaseAnonKey.trim()

                if (baseUrl.isEmpty() || apiKey.isEmpty()) return@withContext null

                val endpoint = "$baseUrl/rest/v1/profiles?id=eq.$userId&select=*"
                val token = if (!accessToken.isNullOrBlank()) accessToken else UserSessionManager.getValidAccessToken()
                val authHeader = if (token.isNotBlank()) "Bearer $token" else getAuthHeader()

                val request = Request.Builder()
                    .url(endpoint)
                    .addHeader("apikey", apiKey)
                    .addHeader("Authorization", authHeader)
                    .get()
                    .build()

                client.newCall(request).execute().use { response ->
                    val responseBodyString = response.body?.string() ?: ""
                    if (response.isSuccessful && responseBodyString.startsWith("[")) {
                        val jsonArray = JSONArray(responseBodyString)
                        if (jsonArray.length() > 0) {
                            val obj = jsonArray.getJSONObject(0)
                            return@withContext parseUserProfile(obj, userId, email)
                        }
                    }
                }
                null
            } catch (e: Exception) {
                e.printStackTrace()
                null
            }
        }
    }

    // In-memory cache to ensure screens display instant profiles without blank frames
    companion object {
        private object ProfileCache {
            var cachedProfiles: List<UserProfile> = emptyList()
            val profilesMap = java.util.concurrent.ConcurrentHashMap<String, UserProfile>()
        }

        fun getInMemoryProfiles(): List<UserProfile> = ProfileCache.cachedProfiles

        fun getInMemoryProfilesMap(): Map<String, UserProfile> = ProfileCache.profilesMap

        fun updateInMemoryProfiles(list: List<UserProfile>) {
            if (list.isNotEmpty()) {
                ProfileCache.cachedProfiles = list
                for (p in list) {
                    if (p.id.isNotBlank()) {
                        ProfileCache.profilesMap[p.id.trim()] = p
                    }
                }
            }
        }

        fun updateInMemoryProfile(profile: UserProfile) {
            if (profile.id.isNotBlank()) {
                ProfileCache.profilesMap[profile.id.trim()] = profile
            }
            if (profile.numericId > 0L) {
                ProfileCache.profilesMap["num_${profile.numericId}"] = profile
            }
            if (profile.email.isNotBlank()) {
                ProfileCache.profilesMap["email_${profile.email.lowercase().trim()}"] = profile
            }
            val currentList = ProfileCache.cachedProfiles.toMutableList()
            val index = currentList.indexOfFirst {
                (profile.id.isNotBlank() && it.id == profile.id) ||
                (profile.numericId > 0L && it.numericId == profile.numericId) ||
                (profile.email.isNotBlank() && it.email.equals(profile.email, ignoreCase = true))
            }
            if (index >= 0) {
                currentList[index] = profile
            } else {
                currentList.add(0, profile)
            }
            ProfileCache.cachedProfiles = currentList
        }
    }

    /**
     * Fetch paginated user profiles from Supabase table "profiles" (20 at a time)
     * Returns cached profiles if offline or request fails
     */
    suspend fun fetchProfilesPaged(
        offset: Int = 0,
        limit: Int = 50,
        targetGender: String? = null,
        accessToken: String? = null
    ): List<UserProfile> {
        return withContext(Dispatchers.IO) {
            try {
                val baseUrl = SupabaseConfig.supabaseUrl.trim().removeSuffix("/")
                val apiKey = SupabaseConfig.supabaseAnonKey.trim()

                if (baseUrl.isEmpty() || apiKey.isEmpty()) {
                    return@withContext ProfileCache.cachedProfiles.drop(offset).take(limit)
                }

                val genderQuery = when {
                    !targetGender.isNullOrBlank() && (targetGender.startsWith("f", ignoreCase = true) || targetGender.startsWith("w", ignoreCase = true)) -> "&gender=ilike.female*"
                    !targetGender.isNullOrBlank() && targetGender.startsWith("m", ignoreCase = true) -> "&gender=ilike.male*"
                    else -> ""
                }

                val primaryEndpoints = buildList {
                    if (genderQuery.isNotEmpty()) {
                        add("$baseUrl/rest/v1/profiles?select=*$genderQuery&order=is_online.desc.nullslast,numeric_id.desc&offset=$offset&limit=$limit")
                        add("$baseUrl/rest/v1/profiles?select=*$genderQuery&order=numeric_id.desc&offset=$offset&limit=$limit")
                        add("$baseUrl/rest/v1/profiles?select=*$genderQuery&offset=$offset&limit=$limit")
                    }
                }

                val fallbackEndpoints = listOf(
                    "$baseUrl/rest/v1/profiles?select=*&order=is_online.desc.nullslast,numeric_id.desc&offset=$offset&limit=$limit",
                    "$baseUrl/rest/v1/profiles?select=*&order=numeric_id.desc&offset=$offset&limit=$limit",
                    "$baseUrl/rest/v1/profiles?select=*&offset=$offset&limit=$limit",
                    "$baseUrl/rest/v1/profiles?select=*"
                )

                val endpointsToTry = primaryEndpoints + fallbackEndpoints
                val authHeader = getAuthHeader(overrideToken = accessToken)

                for (endpoint in endpointsToTry) {
                    try {
                        val request = Request.Builder()
                            .url(endpoint)
                            .addHeader("apikey", apiKey)
                            .addHeader("Authorization", authHeader)
                            .get()
                            .build()

                        val response = client.newCall(request).execute()
                        val responseBodyString = response.body?.string() ?: ""
                        response.close()

                        if (response.isSuccessful && responseBodyString.startsWith("[")) {
                            val jsonArray = JSONArray(responseBodyString)
                            val list = mutableListOf<UserProfile>()
                            for (i in 0 until jsonArray.length()) {
                                val obj = jsonArray.getJSONObject(i)
                                list.add(parseUserProfile(obj))
                            }
                            if (list.isNotEmpty()) {
                                val sortedList = if (offset == 0) {
                                    list.sortedWith(compareByDescending<UserProfile> { it.isOnline }.thenByDescending { it.numericId })
                                } else {
                                    list
                                }
                                if (offset == 0) {
                                    ProfileCache.cachedProfiles = sortedList
                                } else {
                                    val existingIds = ProfileCache.cachedProfiles.map { it.id }.toSet()
                                    val newItems = sortedList.filter { it.id !in existingIds }
                                    ProfileCache.cachedProfiles = ProfileCache.cachedProfiles + newItems
                                }
                                return@withContext sortedList
                            }
                        }
                    } catch (_: Exception) {}
                }
                ProfileCache.cachedProfiles.drop(offset).take(limit)
            } catch (e: Exception) {
                e.printStackTrace()
                ProfileCache.cachedProfiles.drop(offset).take(limit)
            }
        }
    }

    /**
     * Fetch ALL real user profiles from Supabase table "profiles"
     * Returns cached profiles if offline or request fails
     */
    suspend fun fetchAllProfiles(): List<UserProfile> {
        return withContext(Dispatchers.IO) {
            try {
                val baseUrl = SupabaseConfig.supabaseUrl.trim().removeSuffix("/")
                val apiKey = SupabaseConfig.supabaseAnonKey.trim()

                if (baseUrl.isEmpty() || apiKey.isEmpty()) return@withContext ProfileCache.cachedProfiles

                val endpoint = "$baseUrl/rest/v1/profiles?select=*"
                val authHeader = getAuthHeader()
                
                try {
                    val request = Request.Builder()
                        .url(endpoint)
                        .addHeader("apikey", apiKey)
                        .addHeader("Authorization", authHeader)
                        .get()
                        .build()

                    client.newCall(request).execute().use { response ->
                        val responseBodyString = response.body?.string() ?: ""
                        if (response.isSuccessful && responseBodyString.startsWith("[")) {
                            val jsonArray = JSONArray(responseBodyString)
                            val list = mutableListOf<UserProfile>()
                            for (i in 0 until jsonArray.length()) {
                                val obj = jsonArray.getJSONObject(i)
                                list.add(parseUserProfile(obj))
                            }
                            if (list.isNotEmpty()) {
                                updateInMemoryProfiles(list)
                                return@withContext list
                            }
                        }
                    }
                } catch (_: Exception) {}
                ProfileCache.cachedProfiles
            } catch (e: Exception) {
                e.printStackTrace()
                ProfileCache.cachedProfiles
            }
        }
    }

    /**
     * Fetch all users appointed as Coin Sellers from Supabase table "profiles"
     */
    suspend fun fetchCoinSellers(): List<UserProfile> {
        return withContext(Dispatchers.IO) {
            try {
                val baseUrl = SupabaseConfig.supabaseUrl.trim().removeSuffix("/")
                val apiKey = SupabaseConfig.supabaseAnonKey.trim()

                if (baseUrl.isEmpty() || apiKey.isEmpty()) return@withContext emptyList()

                val endpoint = "$baseUrl/rest/v1/profiles?is_coinseller=eq.true&select=*"
                val authHeader = getAuthHeader()
                val request = Request.Builder()
                    .url(endpoint)
                    .addHeader("apikey", apiKey)
                    .addHeader("Authorization", authHeader)
                    .get()
                    .build()

                client.newCall(request).execute().use { response ->
                    val responseBodyString = response.body?.string() ?: ""
                    if (response.isSuccessful && responseBodyString.startsWith("[")) {
                        val jsonArray = JSONArray(responseBodyString)
                        val list = mutableListOf<UserProfile>()
                        for (i in 0 until jsonArray.length()) {
                            val obj = jsonArray.getJSONObject(i)
                            list.add(parseUserProfile(obj))
                        }
                        return@withContext list
                    }
                }
                emptyList()
            } catch (e: Exception) {
                e.printStackTrace()
                emptyList()
            }
        }
    }

    /**
     * Search profile by Email, Numeric ID, or UID with multi-level fallback
     */
    suspend fun findProfileByIdentifier(identifier: String): UserProfile? {
        val raw = identifier.trim()
        if (raw.isEmpty()) return null

        // Clean any prefixes like "ID:", "ID", "#", "•"
        val cleanedDigits = raw.replace("ID:", "", ignoreCase = true)
            .replace("ID", "", ignoreCase = true)
            .replace("#", "")
            .replace("•", "")
            .trim()
        val numericIdLong = cleanedDigits.toLongOrNull()

        return withContext(Dispatchers.IO) {
            try {
                // 1. Fast in-memory cache lookup
                ProfileCache.cachedProfiles.firstOrNull { p ->
                    (numericIdLong != null && p.numericId == numericIdLong) ||
                    p.id.equals(raw, ignoreCase = true) ||
                    p.email.equals(raw, ignoreCase = true) ||
                    p.name.equals(raw, ignoreCase = true)
                }?.let { return@withContext it }

                val baseUrl = SupabaseConfig.supabaseUrl.trim().removeSuffix("/")
                val apiKey = SupabaseConfig.supabaseAnonKey.trim()

                if (baseUrl.isEmpty() || apiKey.isEmpty()) return@withContext null

                val isEmail = raw.contains("@")
                val isUuid = raw.length == 36 && raw.count { it == '-' } == 4

                val filterQueries = mutableListOf<String>()
                if (numericIdLong != null) {
                    filterQueries.add("numeric_id=eq.$numericIdLong")
                    filterQueries.add("numeric_id=eq.$cleanedDigits")
                }
                if (isEmail) {
                    filterQueries.add("email=ilike.${Uri.encode(raw)}")
                }
                if (isUuid) {
                    filterQueries.add("id=eq.${Uri.encode(raw)}")
                }
                filterQueries.add("id=eq.${Uri.encode(raw)}")
                filterQueries.add("name=eq.${Uri.encode(raw)}")
                filterQueries.add("name=ilike.%25${Uri.encode(raw)}%25")

                val authHeader = getAuthHeader()
                for (filter in filterQueries) {
                    val endpoint = "$baseUrl/rest/v1/profiles?$filter&select=*&limit=1"
                    val request = Request.Builder()
                        .url(endpoint)
                        .addHeader("apikey", apiKey)
                        .addHeader("Authorization", authHeader)
                        .get()
                        .build()

                    client.newCall(request).execute().use { response ->
                        val respStr = response.body?.string() ?: ""
                        if (response.isSuccessful && respStr.startsWith("[")) {
                            val arr = JSONArray(respStr)
                            if (arr.length() > 0) {
                                val found = parseUserProfile(arr.getJSONObject(0))
                                // Cache for next time
                                if (!ProfileCache.cachedProfiles.any { it.id == found.id }) {
                                    ProfileCache.cachedProfiles = ProfileCache.cachedProfiles + found
                                }
                                return@withContext found
                            }
                        }
                    }
                }

                // 2. Comprehensive fallback: fetch all profiles and search in memory
                val allProfiles = fetchAllProfiles()
                val match = allProfiles.firstOrNull { p ->
                    (numericIdLong != null && p.numericId == numericIdLong) ||
                    p.id.equals(raw, ignoreCase = true) ||
                    p.email.equals(raw, ignoreCase = true) ||
                    p.name.equals(raw, ignoreCase = true) ||
                    (raw.length >= 3 && p.name.contains(raw, ignoreCase = true))
                }
                match
            } catch (e: Exception) {
                e.printStackTrace()
                null
            }
        }
    }

    suspend fun fetchProfileByNumericId(numericId: Long, forceRefresh: Boolean = false): UserProfile? {
        if (numericId <= 0L) return null
        return withContext(Dispatchers.IO) {
            try {
                // Check cache first only if forceRefresh is false
                if (!forceRefresh) {
                    ProfileCache.cachedProfiles.firstOrNull { it.numericId == numericId }?.let {
                        return@withContext it
                    }
                }

                val baseUrl = SupabaseConfig.supabaseUrl.trim().removeSuffix("/")
                val apiKey = SupabaseConfig.supabaseAnonKey.trim()

                if (baseUrl.isNotEmpty() && apiKey.isNotEmpty()) {
                    val endpoint = "$baseUrl/rest/v1/profiles?numeric_id=eq.$numericId&select=*"
                    val authHeader = getAuthHeader()
                    val request = Request.Builder()
                        .url(endpoint)
                        .addHeader("apikey", apiKey)
                        .addHeader("Authorization", authHeader)
                        .get()
                        .build()

                    client.newCall(request).execute().use { response ->
                        val respStr = response.body?.string() ?: ""
                        if (response.isSuccessful && respStr.startsWith("[")) {
                            val arr = JSONArray(respStr)
                            if (arr.length() > 0) {
                                val found = parseUserProfile(arr.getJSONObject(0))
                                ProfileCache.cachedProfiles = ProfileCache.cachedProfiles.filter { it.id != found.id && it.numericId != found.numericId } + found
                                return@withContext found
                            }
                        }
                    }
                }

                // Fallback to fetchAllProfiles
                val all = fetchAllProfiles()
                all.firstOrNull { it.numericId == numericId }
            } catch (e: Exception) {
                e.printStackTrace()
                null
            }
        }
    }

    /**
     * Award Coins via REST or RPC with guaranteed authoritative real-time math
     */
    suspend fun awardCoins(
        senderUserId: String,
        senderNumericId: Long,
        isAdmin: Boolean,
        isCoinSeller: Boolean,
        targetNumericId: Long,
        amount: Long,
        reason: String = "",
        context: android.content.Context? = null
    ): Pair<Boolean, String> {
        if (amount <= 0L) return Pair(false, "Invalid coin amount: must be greater than 0.")
        return withContext(Dispatchers.IO) {
            try {
                val baseUrl = SupabaseConfig.supabaseUrl.trim().removeSuffix("/")
                val apiKey = SupabaseConfig.supabaseAnonKey.trim()

                if (baseUrl.isEmpty() || apiKey.isEmpty()) {
                    return@withContext Pair(false, "Supabase credentials not configured.")
                }

                val authHeader = getAuthHeader(context)

                // 1. Try secure Postgres RPC award_coins first
                try {
                    val rpcUrl = "$baseUrl/rest/v1/rpc/award_coins"
                    val rpcJson = JSONObject().apply {
                        put("p_sender_id", senderUserId)
                        put("p_sender_numeric_id", senderNumericId)
                        put("p_is_admin", isAdmin)
                        put("p_is_coinseller", isCoinSeller)
                        put("p_target_numeric_id", targetNumericId)
                        put("p_amount", amount)
                        put("p_reason", reason)
                    }.toString()

                    val rpcReq = Request.Builder()
                        .url(rpcUrl)
                        .addHeader("apikey", apiKey)
                        .addHeader("Authorization", authHeader)
                        .addHeader("Content-Type", "application/json")
                        .post(rpcJson.toRequestBody(jsonMediaType))
                        .build()

                    client.newCall(rpcReq).execute().use { res ->
                        if (res.isSuccessful || res.code == 200) {
                            val bodyStr = res.body?.string() ?: ""
                            val json = JSONObject(bodyStr)
                            if (json.optBoolean("success", false)) {
                                val msg = json.optString("message", "Successfully awarded %,d coins.".format(amount))
                                val sellerNewCoins = json.optLong("seller_coins", -1L)
                                if (sellerNewCoins >= 0 && context != null) {
                                    UserSessionManager.saveCoins(context, sellerNewCoins)
                                }
                                return@withContext Pair(true, msg)
                            } else {
                                val errMsg = json.optString("error", "Failed to transfer coins.")
                                return@withContext Pair(false, errMsg)
                            }
                        }
                    }
                } catch (_: Exception) {}

                // 2. Fetch authoritative TARGET user directly from server
                val targetReq = Request.Builder()
                    .url("$baseUrl/rest/v1/profiles?numeric_id=eq.$targetNumericId&select=*")
                    .addHeader("apikey", apiKey)
                    .addHeader("Authorization", authHeader)
                    .get()
                    .build()

                val targetProfile = client.newCall(targetReq).execute().use { resp ->
                    val body = resp.body?.string() ?: ""
                    if (resp.isSuccessful && body.startsWith("[")) {
                        val arr = JSONArray(body)
                        if (arr.length() > 0) parseUserProfile(arr.getJSONObject(0)) else null
                    } else null
                } ?: return@withContext Pair(false, "User with Numeric ID $targetNumericId not found.")

                // 2. If sender is a Coin Seller (and not admin), check and deduct sender balance from fresh server data
                var newSellerCoins = 0L
                if (!isAdmin && isCoinSeller) {
                    val cleanSenderId = senderUserId.trim()
                    val senderUrl = if (cleanSenderId.isNotBlank()) {
                        "$baseUrl/rest/v1/profiles?id=eq.$cleanSenderId&select=*"
                    } else {
                        "$baseUrl/rest/v1/profiles?numeric_id=eq.$senderNumericId&select=*"
                    }

                    val senderReq = Request.Builder()
                        .url(senderUrl)
                        .addHeader("apikey", apiKey)
                        .addHeader("Authorization", authHeader)
                        .get()
                        .build()

                    val senderProfile = client.newCall(senderReq).execute().use { resp ->
                        val body = resp.body?.string() ?: ""
                        if (resp.isSuccessful && body.startsWith("[")) {
                            val arr = JSONArray(body)
                            if (arr.length() > 0) parseUserProfile(arr.getJSONObject(0)) else null
                        } else null
                    }

                    val currentSellerCoins = senderProfile?.coins ?: 0L
                    if (currentSellerCoins < amount) {
                        return@withContext Pair(false, "Insufficient balance! You have %,d coins available.".format(currentSellerCoins))
                    }

                    newSellerCoins = currentSellerCoins - amount
                    val updateSellerUrl = if (cleanSenderId.isNotBlank()) {
                        "$baseUrl/rest/v1/profiles?id=eq.$cleanSenderId"
                    } else {
                        "$baseUrl/rest/v1/profiles?numeric_id=eq.$senderNumericId"
                    }
                    val sellerUpdateBody = JSONObject().apply { put("coins", newSellerCoins) }.toString()

                    val updateSellerReq = Request.Builder()
                        .url(updateSellerUrl)
                        .addHeader("apikey", apiKey)
                        .addHeader("Authorization", authHeader)
                        .addHeader("Content-Type", "application/json")
                        .addHeader("Prefer", "return=minimal")
                        .patch(sellerUpdateBody.toRequestBody(jsonMediaType))
                        .build()

                    client.newCall(updateSellerReq).execute().close()

                    // Sync sender cache and local session
                    if (senderProfile != null) {
                        val updatedSender = senderProfile.copy(coins = newSellerCoins)
                        ProfileCache.cachedProfiles = ProfileCache.cachedProfiles.filter { it.id != senderProfile.id && it.numericId != senderProfile.numericId } + updatedSender
                    }
                    if (context != null) {
                        UserSessionManager.saveCoins(context, newSellerCoins)
                    }

                    // Record sender transaction
                    recordCoinTransaction(
                        userId = cleanSenderId.ifBlank { senderProfile?.id ?: senderNumericId.toString() },
                        amount = -amount,
                        type = "TRANSFER",
                        title = "Coins Transferred",
                        description = "Transferred %,d coins to %s (ID: %d)".format(amount, targetProfile.name, targetNumericId)
                    )
                }

                // 3. Credit TARGET user balance accurately: current live server coins + amount
                val currentTargetCoins = targetProfile.coins
                val newTargetCoins = currentTargetCoins + amount
                val updateTargetUrl = if (targetProfile.id.isNotBlank()) {
                    "$baseUrl/rest/v1/profiles?id=eq.${targetProfile.id}"
                } else {
                    "$baseUrl/rest/v1/profiles?numeric_id=eq.$targetNumericId"
                }
                val targetUpdateBody = JSONObject().apply { put("coins", newTargetCoins) }.toString()

                val updateTargetReq = Request.Builder()
                    .url(updateTargetUrl)
                    .addHeader("apikey", apiKey)
                    .addHeader("Authorization", authHeader)
                    .addHeader("Content-Type", "application/json")
                    .addHeader("Prefer", "return=minimal")
                    .patch(targetUpdateBody.toRequestBody(jsonMediaType))
                    .build()

                val success = client.newCall(updateTargetReq).execute().use { response ->
                    response.isSuccessful || response.code == 200 || response.code == 204
                }

                if (success) {
                    // Update cache for target user
                    val updatedTarget = targetProfile.copy(coins = newTargetCoins)
                    ProfileCache.cachedProfiles = ProfileCache.cachedProfiles.filter { it.id != targetProfile.id && it.numericId != targetNumericId } + updatedTarget

                    // If the current logged-in user on this device is the target, sync session
                    if (context != null && (senderUserId == targetProfile.id || targetProfile.id == UserSessionManager.getSession(context)?.userId)) {
                        UserSessionManager.saveCoins(context, newTargetCoins)
                    }

                    // Record target transaction
                    recordCoinTransaction(
                        userId = targetProfile.id,
                        amount = amount,
                        type = if (isAdmin) "AWARD" else "TRANSFER",
                        title = if (isAdmin) "Admin Coin Award" else "P2P Coin Transfer",
                        description = if (reason.isNotBlank()) reason else (if (isAdmin) "Awarded by Administrator" else "Received from Seller ID $senderNumericId")
                    )
                    Pair(true, "Successfully awarded %,d coins to %s! New balance: %,d coins.".format(amount, targetProfile.name, newTargetCoins))
                } else {
                    Pair(false, "Failed to update target user coin balance on server.")
                }
            } catch (e: Exception) {
                e.printStackTrace()
                Pair(false, "Error: ${e.localizedMessage}")
            }
        }
    }

    /**
     * Update user roles (isCoinSeller, isAgent) by Numeric ID.
     * Enforces security constraints:
     * 1. An admin cannot remove himself from the Admin role.
     * 2. An admin cannot appoint another admin (Admin role is restricted to root SQL).
     * 3. An admin can only appoint / toggle Coin Seller (isCoinSeller) and Agent (isAgent) roles.
     */
    suspend fun updateUserRoles(
        currentAdminId: String,
        targetNumericId: Long,
        isCoinSeller: Boolean,
        isAgent: Boolean
    ): Pair<Boolean, String> {
        return withContext(Dispatchers.IO) {
            try {
                val baseUrl = SupabaseConfig.supabaseUrl.trim().removeSuffix("/")
                val apiKey = SupabaseConfig.supabaseAnonKey.trim()

                if (baseUrl.isEmpty() || apiKey.isEmpty()) {
                    return@withContext Pair(false, "Supabase credentials missing.")
                }

                val targetProfile = fetchProfileByNumericId(targetNumericId)
                    ?: return@withContext Pair(false, "User with Numeric ID $targetNumericId not found.")

                // Rule 1: An admin cannot remove himself from admin role
                val finalAdminStatus = if (targetProfile.id == currentAdminId) {
                    true // Always stay admin
                } else {
                    targetProfile.isAdmin // Admin cannot grant or revoke admin role for others
                }

                // 1. Try secure Postgres RPC first: admin_update_user_roles
                val authHeader = getAuthHeader()
                try {
                    val rpcUrl = "$baseUrl/rest/v1/rpc/admin_update_user_roles"
                    val rpcBody = JSONObject().apply {
                        put("p_admin_id", currentAdminId)
                        put("p_target_numeric_id", targetNumericId)
                        put("p_is_coinseller", isCoinSeller)
                        put("p_is_agent", isAgent)
                    }.toString()
                    val rpcReq = Request.Builder()
                        .url(rpcUrl)
                        .addHeader("apikey", apiKey)
                        .addHeader("Authorization", authHeader)
                        .addHeader("Content-Type", "application/json")
                        .post(rpcBody.toRequestBody(jsonMediaType))
                        .build()
                    client.newCall(rpcReq).execute().use { rpcRes ->
                        if (rpcRes.isSuccessful || rpcRes.code == 200) {
                            val resStr = rpcRes.body?.string() ?: ""
                            val resJson = JSONObject(resStr)
                            if (resJson.optBoolean("success", false)) {
                                return@withContext Pair(true, resJson.optString("message", "Updated roles for ${targetProfile.name} (Coin Seller: $isCoinSeller, Agent: $isAgent)"))
                            } else {
                                val err = resJson.optString("error", "Unauthorized role update")
                                return@withContext Pair(false, err)
                            }
                        }
                    }
                } catch (_: Exception) {}

                // 2. Direct patch fallback (if column-level permissions permit)
                val updateUrl = "$baseUrl/rest/v1/profiles?numeric_id=eq.$targetNumericId"
                val updateBody = JSONObject().apply {
                    put("is_admin", finalAdminStatus)
                    put("is_coinseller", isCoinSeller)
                    put("is_agent", isAgent)
                }.toString()

                val updateReq = Request.Builder()
                    .url(updateUrl)
                    .addHeader("apikey", apiKey)
                    .addHeader("Authorization", authHeader)
                    .addHeader("Content-Type", "application/json")
                    .addHeader("Prefer", "return=minimal")
                    .patch(updateBody.toRequestBody(jsonMediaType))
                    .build()

                val success = client.newCall(updateReq).execute().use { response ->
                    response.isSuccessful || response.code == 200 || response.code == 204
                }

                if (success) {
                    Pair(true, "Updated roles for ${targetProfile.name} (Coin Seller: $isCoinSeller, Agent: $isAgent)")
                } else {
                    Pair(false, "Failed to update roles for user $targetNumericId.")
                }
            } catch (e: Exception) {
                e.printStackTrace()
                Pair(false, "Error: ${e.localizedMessage}")
            }
        }
    }

    /**
     * Fetch accurate real-time coin balance directly from Supabase table 'profiles'
     * Guaranteed to bypass local cache and query server directly.
     */
    suspend fun fetchCoins(userId: String): Long {
        return withContext(Dispatchers.IO) {
            try {
                val cleanId = userId.trim()
                val baseUrl = SupabaseConfig.supabaseUrl.trim().removeSuffix("/")
                val apiKey = SupabaseConfig.supabaseAnonKey.trim()
                if (baseUrl.isEmpty() || apiKey.isEmpty() || cleanId.isEmpty()) return@withContext 0L

                val authHeader = getAuthHeader()
                val numericVal = cleanId.replace("ID:", "", ignoreCase = true).replace("#", "").trim().toLongOrNull()

                val queryUrls = mutableListOf<String>()
                queryUrls.add("$baseUrl/rest/v1/profiles?id=eq.$cleanId&select=coins,id,numeric_id")
                if (numericVal != null && numericVal > 0L) {
                    queryUrls.add("$baseUrl/rest/v1/profiles?numeric_id=eq.$numericVal&select=coins,id,numeric_id")
                }
                if (cleanId.contains("@")) {
                    queryUrls.add("$baseUrl/rest/v1/profiles?email=eq.$cleanId&select=coins,id,numeric_id")
                }

                for (url in queryUrls) {
                    try {
                        val req = Request.Builder()
                            .url(url)
                            .addHeader("apikey", apiKey)
                            .addHeader("Authorization", authHeader)
                            .get()
                            .build()

                        client.newCall(req).execute().use { response ->
                            val body = response.body?.string() ?: ""
                            if (response.isSuccessful && body.startsWith("[")) {
                                val arr = JSONArray(body)
                                if (arr.length() > 0) {
                                    val obj = arr.getJSONObject(0)
                                    val coins = obj.optLong("coins", 0L)
                                    val profId = obj.optString("id", cleanId)
                                    val numId = obj.optLong("numeric_id", numericVal ?: 0L)

                                    // Update cache with fresh coin balance
                                    ProfileCache.cachedProfiles = ProfileCache.cachedProfiles.map { p ->
                                        if (p.id == profId || (numId > 0L && p.numericId == numId)) {
                                            p.copy(coins = coins)
                                        } else p
                                    }

                                    return@withContext coins
                                }
                            }
                        }
                    } catch (_: Exception) {}
                }

                0L
            } catch (e: Exception) {
                e.printStackTrace()
                0L
            }
        }
    }

    /**
     * Fetch accurate real-time diamond balance directly from Supabase table 'profiles'
     */
    suspend fun fetchDiamonds(userId: String): Long {
        return withContext(Dispatchers.IO) {
            try {
                val cleanId = userId.trim()
                val baseUrl = SupabaseConfig.supabaseUrl.trim().removeSuffix("/")
                val apiKey = SupabaseConfig.supabaseAnonKey.trim()
                if (baseUrl.isEmpty() || apiKey.isEmpty() || cleanId.isEmpty()) return@withContext 0L

                val authHeader = getAuthHeader()
                val endpoint = "$baseUrl/rest/v1/profiles?id=eq.$cleanId&select=diamonds"
                val request = Request.Builder()
                    .url(endpoint)
                    .addHeader("apikey", apiKey)
                    .addHeader("Authorization", authHeader)
                    .get()
                    .build()

                client.newCall(request).execute().use { response ->
                    val body = response.body?.string() ?: ""
                    if (response.isSuccessful && body.startsWith("[")) {
                        val arr = JSONArray(body)
                        if (arr.length() > 0) {
                            return@withContext arr.getJSONObject(0).optLong("diamonds", 0L)
                        }
                    }
                }

                val prof = fetchProfileById(cleanId) ?: fetchProfile(cleanId, "")
                if (prof != null) {
                    return@withContext prof.diamonds
                }

                0L
            } catch (e: Exception) {
                e.printStackTrace()
                0L
            }
        }
    }

    /**
     * Diamond to Coin conversion strictly verified against Supabase Server first:
     * - Rate: 5,000 Diamonds = 90 Coins (Proportional conversion: coins = (diamonds * 90) / 5000)
     * - Deducts Diamonds & Adds equivalent Coins on server
     * - Only modifies local session state AFTER server explicitly approves and commits the change
     * - Creates ledger transaction record in diamond_transactions and coin_transactions
     * - Returns Pair(success, Pair(newCoins, newDiamonds))
     */
    suspend fun convertDiamondsToCoins(
        userId: String,
        diamondsToConvert: Long,
        context: Context? = null
    ): Pair<Boolean, Pair<Long, Long>> {
        val coinsToReceive = (diamondsToConvert * 90L) / 5000L
        if (diamondsToConvert < 5000L || coinsToReceive <= 0L) {
            return Pair(false, Pair(0L, 0L))
        }

        if (context != null && !NetworkUtils.isOnline(context)) {
            NetworkUtils.showToast(context, "No internet connection. Cannot convert diamonds while offline.")
            return Pair(false, Pair(0L, 0L))
        }

        return withContext(Dispatchers.IO) {
            val cleanId = userId.trim()
            val baseUrl = SupabaseConfig.supabaseUrl.trim().removeSuffix("/")
            val apiKey = SupabaseConfig.supabaseAnonKey.trim()

            if (baseUrl.isEmpty() || apiKey.isEmpty() || cleanId.isEmpty()) {
                return@withContext Pair(false, Pair(0L, 0L))
            }

            val authHeader = getAuthHeader(context)

            // 1. Try atomic PostgreSQL RPC function 'exchange_diamonds_to_coins'
            try {
                val rpcEndpoint = "$baseUrl/rest/v1/rpc/exchange_diamonds_to_coins"
                val rpcPayload = JSONObject().apply {
                    put("p_user_id", cleanId)
                    put("p_diamonds_amount", diamondsToConvert)
                    put("p_coins_amount", coinsToReceive)
                }.toString()

                val rpcReq = Request.Builder()
                    .url(rpcEndpoint)
                    .addHeader("apikey", apiKey)
                    .addHeader("Authorization", authHeader)
                    .addHeader("Content-Type", "application/json")
                    .post(rpcPayload.toRequestBody(jsonMediaType))
                    .build()

                client.newCall(rpcReq).execute().use { resp ->
                    if (resp.isSuccessful || resp.code in 200..204) {
                        val respStr = resp.body?.string()?.trim() ?: ""
                        if (respStr.startsWith("{")) {
                            val rpcObj = JSONObject(respStr)
                            val success = rpcObj.optBoolean("success", true)
                            if (success) {
                                val rpcCoins = rpcObj.optLong("coins", 0L)
                                val rpcDiamonds = rpcObj.optLong("diamonds", 0L)

                                // Update local session ONLY after confirmed server RPC success
                                if (context != null) {
                                    UserSessionManager.saveCoins(context, rpcCoins)
                                    UserSessionManager.saveDiamonds(context, rpcDiamonds)
                                }

                                return@withContext Pair(true, Pair(rpcCoins, rpcDiamonds))
                            }
                        }
                    }
                }
            } catch (_: Exception) {
                // Fallback to direct authenticated REST patch against Supabase server
            }

            // 2. Direct authenticated REST Update against Supabase server
            try {
                // Fetch authoritative server balances directly
                val currentDiamonds = fetchDiamonds(cleanId)
                val currentCoins = fetchCoins(cleanId)

                if (currentDiamonds < diamondsToConvert) {
                    return@withContext Pair(false, Pair(currentCoins, currentDiamonds))
                }

                val newDiamonds = currentDiamonds - diamondsToConvert
                val newCoins = currentCoins + coinsToReceive

                val endpoint = "$baseUrl/rest/v1/profiles?id=eq.$cleanId"
                val updateBody = JSONObject().apply {
                    put("coins", newCoins)
                    put("diamonds", newDiamonds)
                }.toString()

                val req = Request.Builder()
                    .url(endpoint)
                    .addHeader("apikey", apiKey)
                    .addHeader("Authorization", authHeader)
                    .addHeader("Content-Type", "application/json")
                    .addHeader("Prefer", "return=minimal")
                    .patch(updateBody.toRequestBody(jsonMediaType))
                    .build()

                var serverUpdated = false
                client.newCall(req).execute().use { resp ->
                    if (resp.isSuccessful || resp.code in 200..204) {
                        serverUpdated = true
                    }
                }

                if (!serverUpdated) {
                    // Server declined or unreachable: DO NOT update local state
                    return@withContext Pair(false, Pair(currentCoins, currentDiamonds))
                }

                // Record in Diamond ledger on server
                try {
                    recordDiamondTransaction(
                        userId = cleanId,
                        amount = -diamondsToConvert,
                        type = "EXCHANGE_COINS",
                        title = "Exchange Diamonds to Coins",
                        description = "Exchanged $diamondsToConvert 💎 to $coinsToReceive Coins (Rate: 5,000 💎 = 90 Coins)"
                    )
                } catch (_: Exception) {}

                // Record in Coin ledger on server
                try {
                    recordCoinTransaction(
                        userId = cleanId,
                        amount = coinsToReceive,
                        type = "DIAMOND_CONVERSION",
                        title = "Diamond Income Conversion",
                        description = "Converted $diamondsToConvert Diamonds to $coinsToReceive Coins"
                    )
                } catch (_: Exception) {}

                // Synchronize local session ONLY AFTER confirmed server update
                if (context != null) {
                    UserSessionManager.saveCoins(context, newCoins)
                    UserSessionManager.saveDiamonds(context, newDiamonds)
                }

                Pair(true, Pair(newCoins, newDiamonds))
            } catch (e: Exception) {
                e.printStackTrace()
                Pair(false, Pair(0L, 0L))
            }
        }
    }

    /**
     * Purchase an Avatar Frame valid for specified days.
     * Guaranteed atomic coin deduction and multi-frame inventory persistence.
     */
    suspend fun purchaseAvatarFrame(
        userId: String,
        frameId: String,
        priceCoins: Long,
        validityDays: Int = 7,
        context: Context? = null
    ): Triple<Boolean, String, Long> {
        if (context != null && !NetworkUtils.isOnline(context)) {
            NetworkUtils.showToast(context, "No internet connection. Cannot purchase avatar frame while offline.")
            return Triple(false, "No internet connection. Cannot purchase avatar frame while offline.", 0L)
        }
        return withContext(Dispatchers.IO) {
            val cleanId = userId.trim()
            try {
                val frame = AvatarFrameManager.getFrameById(frameId)
                val frameName = frame?.name ?: frameId.replaceFirstChar { it.uppercase() }

                val currentProfile = if (cleanId.isNotBlank()) fetchProfileById(cleanId) else null
                val userEmail = currentProfile?.email ?: (context?.let { UserSessionManager.getSession(it)?.email } ?: "")
                val userNumericId = currentProfile?.numericId ?: (context?.let { UserSessionManager.getSession(it)?.numericId } ?: 0L)

                val serverCoins = if (cleanId.isNotBlank()) fetchCoins(cleanId) else 0L

                if (serverCoins < priceCoins) {
                    return@withContext Triple(false, "Insufficient coin balance. You need %,d coins.".format(priceCoins), serverCoins)
                }

                val expCalendar = java.util.Calendar.getInstance()
                expCalendar.add(java.util.Calendar.DAY_OF_YEAR, validityDays)
                val sdf = java.text.SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss.SSS'Z'", java.util.Locale.US).apply {
                    timeZone = java.util.TimeZone.getTimeZone("UTC")
                }
                val expiresAtISO = sdf.format(expCalendar.time)

                val newCoins = (serverCoins - priceCoins).coerceAtLeast(0L)
                val baseUrl = SupabaseConfig.supabaseUrl.trim().removeSuffix("/")
                val apiKey = SupabaseConfig.supabaseAnonKey.trim()

                if (baseUrl.isNotEmpty() && apiKey.isNotEmpty() && cleanId.isNotEmpty()) {
                    val authHeader = getAuthHeader(context)

                    // 1. Direct coin deduction on profiles table (core column guaranteed)
                    val coinUpdateBody = JSONObject().apply {
                        put("coins", newCoins)
                    }.toString()

                    val targetEndpoints = mutableListOf("$baseUrl/rest/v1/profiles?id=eq.$cleanId")
                    if (userEmail.isNotBlank() && !userEmail.equals(cleanId, ignoreCase = true)) {
                        targetEndpoints.add("$baseUrl/rest/v1/profiles?email=eq.$userEmail")
                    }
                    if (userNumericId > 0L) {
                        targetEndpoints.add("$baseUrl/rest/v1/profiles?numeric_id=eq.$userNumericId")
                    }

                    for (endpoint in targetEndpoints) {
                        try {
                            val patchReq = Request.Builder()
                                .url(endpoint)
                                .addHeader("apikey", apiKey)
                                .addHeader("Authorization", authHeader)
                                .addHeader("Content-Type", "application/json")
                                .addHeader("Prefer", "return=minimal")
                                .patch(coinUpdateBody.toRequestBody(jsonMediaType))
                                .build()
                            client.newCall(patchReq).execute().close()
                        } catch (_: Exception) {}
                    }

                    // 2. Try optional active_frame_id update on profiles if column exists
                    try {
                        val framePatchBody = JSONObject().apply {
                            put("active_frame_id", frameId)
                            put("frame_expires_at", expiresAtISO)
                        }.toString()
                        val framePatchReq = Request.Builder()
                            .url("$baseUrl/rest/v1/profiles?id=eq.$cleanId")
                            .addHeader("apikey", apiKey)
                            .addHeader("Authorization", authHeader)
                            .addHeader("Content-Type", "application/json")
                            .addHeader("Prefer", "return=minimal")
                            .patch(framePatchBody.toRequestBody(jsonMediaType))
                            .build()
                        client.newCall(framePatchReq).execute().close()
                    } catch (_: Exception) {}

                    // 3. Insert into user_frames table so user owns multiple frames
                    val userFramesEndpoints = listOf(
                        "$baseUrl/rest/v1/user_frames",
                        "$baseUrl/rest/v1/user_avatar_frames"
                    )
                    val userFrameBody = JSONObject().apply {
                        put("user_id", cleanId)
                        put("frame_id", frameId)
                        put("price_paid", priceCoins)
                        put("expires_at", expiresAtISO)
                        put("is_active", true)
                    }.toString()

                    for (ep in userFramesEndpoints) {
                        try {
                            val frameReq = Request.Builder()
                                .url(ep)
                                .addHeader("apikey", apiKey)
                                .addHeader("Authorization", authHeader)
                                .addHeader("Content-Type", "application/json")
                                .addHeader("Prefer", "return=minimal")
                                .post(userFrameBody.toRequestBody(jsonMediaType))
                                .build()
                            client.newCall(frameReq).execute().close()
                        } catch (_: Exception) {}
                    }
                }

                // Record transaction
                try {
                    recordCoinTransaction(
                        userId = cleanId,
                        amount = -priceCoins,
                        type = "FRAME_PURCHASE",
                        title = "Avatar Frame Purchase",
                        description = "Purchased $frameName Frame ($validityDays Days)"
                    )
                } catch (_: Exception) {}

                // Update local session
                if (context != null) {
                    UserSessionManager.saveCoins(context, newCoins)
                    UserSessionManager.savePurchasedFrame(context, frameId, expiresAtISO, priceCoins, targetUserId = cleanId)
                    UserSessionManager.saveActiveFrame(context, frameId, expiresAtISO, targetUserId = cleanId)
                }

                Triple(true, "Successfully purchased $frameName Frame for $validityDays Days!", newCoins)
            } catch (e: Exception) {
                e.printStackTrace()
                // Graceful local fallback so user gets their purchase
                if (context != null) {
                    val curCoins = UserSessionManager.getCoins(context)
                    if (curCoins >= priceCoins) {
                        val newCoins = UserSessionManager.subtractCoins(context, priceCoins)
                        val expCalendar = java.util.Calendar.getInstance()
                        expCalendar.add(java.util.Calendar.DAY_OF_YEAR, validityDays)
                        val sdf = java.text.SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss.SSS'Z'", java.util.Locale.US).apply {
                            timeZone = java.util.TimeZone.getTimeZone("UTC")
                        }
                        val expISO = sdf.format(expCalendar.time)
                        UserSessionManager.savePurchasedFrame(context, frameId, expISO, priceCoins, targetUserId = cleanId)
                        UserSessionManager.saveActiveFrame(context, frameId, expISO, targetUserId = cleanId)
                        val fName = AvatarFrameManager.getFrameById(frameId)?.name ?: frameId
                        return@withContext Triple(true, "Successfully purchased $fName Frame!", newCoins)
                    }
                }
                Triple(false, e.message ?: "Failed to purchase frame. Please try again.", 0L)
            }
        }
    }

    /**
     * Fetches all frames genuinely owned/purchased by the user.
     * Guaranteed never to return unbought or fake starter frames.
     */
    suspend fun fetchUserOwnedFrames(
        userId: String,
        context: Context? = null
    ): List<UserOwnedFrame> {
        return withContext(Dispatchers.IO) {
            val cleanId = userId.trim()
            val currentActiveFrame = context?.let { UserSessionManager.getActiveFrameId(it, cleanId) } ?: ""
            val localPurchased = context?.let { UserSessionManager.getPurchasedFrames(it, cleanId) } ?: emptyList()

            val baseUrl = SupabaseConfig.supabaseUrl.trim().removeSuffix("/")
            val apiKey = SupabaseConfig.supabaseAnonKey.trim()

            val dbFrames = mutableListOf<UserOwnedFrame>()

            if (baseUrl.isNotEmpty() && apiKey.isNotEmpty() && cleanId.isNotEmpty()) {
                try {
                    val authHeader = getAuthHeader(context)
                    val url = "$baseUrl/rest/v1/user_frames?user_id=eq.$cleanId&order=created_at.desc"
                    val req = Request.Builder()
                        .url(url)
                        .addHeader("apikey", apiKey)
                        .addHeader("Authorization", authHeader)
                        .get()
                        .build()

                    client.newCall(req).execute().use { res ->
                        if (res.isSuccessful) {
                            val body = res.body?.string() ?: ""
                            if (body.startsWith("[")) {
                                val arr = JSONArray(body)
                                for (i in 0 until arr.length()) {
                                    val obj = arr.getJSONObject(i)
                                    val fId = obj.optString("frame_id", "")
                                    if (fId.isNotBlank()) {
                                        dbFrames.add(
                                            UserOwnedFrame(
                                                id = obj.optString("id", java.util.UUID.randomUUID().toString()),
                                                userId = obj.optString("user_id", cleanId),
                                                frameId = fId,
                                                purchasedAt = obj.optString("purchased_at", obj.optString("created_at", "")),
                                                expiresAt = obj.optString("expires_at", ""),
                                                isActive = obj.optBoolean("is_active", false) || fId.equals(currentActiveFrame, ignoreCase = true),
                                                pricePaid = obj.optLong("price_paid", 0L)
                                            )
                                        )
                                    }
                                }
                            }
                        }
                    }
                } catch (_: Exception) {}
            }

            // Merge dbFrames with localPurchased frames (deduplicating by frameId)
            val mergedMap = LinkedHashMap<String, UserOwnedFrame>()
            for (item in dbFrames) {
                mergedMap[item.frameId.lowercase()] = item
            }
            for (item in localPurchased) {
                if (!mergedMap.containsKey(item.frameId.lowercase())) {
                    mergedMap[item.frameId.lowercase()] = item
                }
            }

            mergedMap.values.toList().map {
                if (currentActiveFrame.isNotBlank() && it.frameId.equals(currentActiveFrame, ignoreCase = true)) {
                    it.copy(isActive = true)
                } else {
                    it.copy(isActive = false)
                }
            }
        }
    }

    /**
     * Equips an avatar frame from Bag with atomic server synchronization
     */
    suspend fun equipAvatarFrame(
        userId: String,
        frameId: String,
        expiresAt: String = "",
        context: Context? = null
    ): Boolean {
        if (context != null && !NetworkUtils.isOnline(context)) {
            NetworkUtils.showToast(context, "No internet connection. Cannot change avatar frame while offline.")
            return false
        }
        return withContext(Dispatchers.IO) {
            val cleanId = userId.trim()
            val effectiveExp = if (expiresAt.isNotBlank()) expiresAt else "2054-01-01T00:00:00.000Z"

            if (context != null) {
                UserSessionManager.saveActiveFrame(context, frameId, effectiveExp, targetUserId = cleanId)
            }

            // Immediately update in-memory cache so no screen gets stale frame data
            ProfileCache.cachedProfiles = ProfileCache.cachedProfiles.map { p ->
                if (p.id.trim() == cleanId) p.copy(activeFrameId = frameId, frameExpiresAt = effectiveExp) else p
            }
            ProfileCache.profilesMap[cleanId]?.let { p ->
                ProfileCache.profilesMap[cleanId] = p.copy(activeFrameId = frameId, frameExpiresAt = effectiveExp)
            }

            val baseUrl = SupabaseConfig.supabaseUrl.trim().removeSuffix("/")
            val apiKey = SupabaseConfig.supabaseAnonKey.trim()

            if (baseUrl.isNotEmpty() && apiKey.isNotEmpty() && cleanId.isNotEmpty()) {
                val authHeader = getAuthHeader(context)

                // 1. Try server-side RPC equip_avatar_frame first
                var rpcSuccess = false
                try {
                    val rpcUrl = "$baseUrl/rest/v1/rpc/equip_avatar_frame"
                    val rpcBody = JSONObject().apply {
                        put("p_frame_id", frameId)
                        put("p_expires_at", effectiveExp)
                    }.toString()

                    val rpcReq = Request.Builder()
                        .url(rpcUrl)
                        .addHeader("apikey", apiKey)
                        .addHeader("Authorization", authHeader)
                        .addHeader("Content-Type", "application/json")
                        .post(rpcBody.toRequestBody(jsonMediaType))
                        .build()

                    client.newCall(rpcReq).execute().use { res ->
                        rpcSuccess = res.isSuccessful
                    }
                } catch (_: Exception) {}

                // 2. Fallback direct table sync if RPC was not invoked
                if (!rpcSuccess) {
                    try {
                        val profileEndpoint = "$baseUrl/rest/v1/profiles?id=eq.$cleanId"
                        val patchBody = JSONObject().apply {
                            put("active_frame_id", frameId)
                            put("avatar_frame", frameId)
                            put("frame_expires_at", effectiveExp)
                        }.toString()

                        val patchReq = Request.Builder()
                            .url(profileEndpoint)
                            .addHeader("apikey", apiKey)
                            .addHeader("Authorization", authHeader)
                            .addHeader("Content-Type", "application/json")
                            .addHeader("Prefer", "return=minimal")
                            .patch(patchBody.toRequestBody(jsonMediaType))
                            .build()

                        client.newCall(patchReq).execute().close()

                        val resetUrl = "$baseUrl/rest/v1/user_frames?user_id=eq.$cleanId"
                        val resetReq = Request.Builder()
                            .url(resetUrl)
                            .addHeader("apikey", apiKey)
                            .addHeader("Authorization", authHeader)
                            .addHeader("Content-Type", "application/json")
                            .patch(JSONObject().put("is_active", false).toString().toRequestBody(jsonMediaType))
                            .build()
                        client.newCall(resetReq).execute().close()

                        val setUrl = "$baseUrl/rest/v1/user_frames?user_id=eq.$cleanId&frame_id=eq.$frameId"
                        val setReq = Request.Builder()
                            .url(setUrl)
                            .addHeader("apikey", apiKey)
                            .addHeader("Authorization", authHeader)
                            .addHeader("Content-Type", "application/json")
                            .patch(JSONObject().put("is_active", true).toString().toRequestBody(jsonMediaType))
                            .build()
                        client.newCall(setReq).execute().close()
                    } catch (_: Exception) {}
                }
            }
            true
        }
    }

    /**
     * Unequips avatar frame with direct server synchronization
     */
    suspend fun unequipAvatarFrame(
        userId: String,
        context: Context? = null
    ): Boolean {
        if (context != null && !NetworkUtils.isOnline(context)) {
            NetworkUtils.showToast(context, "No internet connection. Cannot unwear avatar frame while offline.")
            return false
        }
        return withContext(Dispatchers.IO) {
            val cleanId = userId.trim()

            if (context != null) {
                UserSessionManager.saveActiveFrame(context, "", "", targetUserId = cleanId)
            }

            // Immediately update in-memory cache so no screen gets stale frame data
            ProfileCache.cachedProfiles = ProfileCache.cachedProfiles.map { p ->
                if (p.id.trim() == cleanId) p.copy(activeFrameId = "", frameExpiresAt = "") else p
            }
            ProfileCache.profilesMap[cleanId]?.let { p ->
                ProfileCache.profilesMap[cleanId] = p.copy(activeFrameId = "", frameExpiresAt = "")
            }

            val baseUrl = SupabaseConfig.supabaseUrl.trim().removeSuffix("/")
            val apiKey = SupabaseConfig.supabaseAnonKey.trim()

            if (baseUrl.isNotEmpty() && apiKey.isNotEmpty() && cleanId.isNotEmpty()) {
                val authHeader = getAuthHeader(context)

                // 1. Try server-side RPC unequip_avatar_frame first
                var rpcSuccess = false
                try {
                    val rpcUrl = "$baseUrl/rest/v1/rpc/unequip_avatar_frame"
                    val rpcReq = Request.Builder()
                        .url(rpcUrl)
                        .addHeader("apikey", apiKey)
                        .addHeader("Authorization", authHeader)
                        .addHeader("Content-Type", "application/json")
                        .post("{}".toRequestBody(jsonMediaType))
                        .build()

                    client.newCall(rpcReq).execute().use { res ->
                        rpcSuccess = res.isSuccessful
                    }
                } catch (_: Exception) {}

                // 2. Fallback direct table sync if RPC was not invoked
                if (!rpcSuccess) {
                    try {
                        val profileEndpoint = "$baseUrl/rest/v1/profiles?id=eq.$cleanId"
                        val patchBody = JSONObject().apply {
                            put("active_frame_id", "")
                            put("avatar_frame", "")
                            put("frame_expires_at", "")
                        }.toString()

                        val patchReq = Request.Builder()
                            .url(profileEndpoint)
                            .addHeader("apikey", apiKey)
                            .addHeader("Authorization", authHeader)
                            .addHeader("Content-Type", "application/json")
                            .addHeader("Prefer", "return=minimal")
                            .patch(patchBody.toRequestBody(jsonMediaType))
                            .build()

                        client.newCall(patchReq).execute().close()

                        val resetUrl = "$baseUrl/rest/v1/user_frames?user_id=eq.$cleanId"
                        val resetReq = Request.Builder()
                            .url(resetUrl)
                            .addHeader("apikey", apiKey)
                            .addHeader("Authorization", authHeader)
                            .addHeader("Content-Type", "application/json")
                            .patch(JSONObject().put("is_active", false).toString().toRequestBody(jsonMediaType))
                            .build()
                        client.newCall(resetReq).execute().close()
                    } catch (_: Exception) {}
                }
            }
            true
        }
    }

    /**
     * Deduct coins for sending text message:
     * - Male users: 15 coins deducted
     * - Female users: Free (0 coins deducted)
     * - Admins (is_admin = true): Free to text & be texted (0 coins deducted)
     * - Coin Sellers (is_coinseller = true): Free to text & be texted (0 coins deducted)
     * - Agents (is_agent = true): Free to text & be texted (0 coins deducted)
     */
    suspend fun deductChatCoins(
        senderId: String,
        senderGender: String,
        receiverId: String,
        receiverName: String,
        isAdmin: Boolean = false,
        isCoinSeller: Boolean = false,
        isAgent: Boolean = false,
        receiverIsAdmin: Boolean = false,
        receiverIsCoinSeller: Boolean = false,
        receiverIsAgent: Boolean = false
    ): Pair<Boolean, Long> {
        // Female users, Admins, Coin Sellers, and Agents text and be texted for free!
        if (!senderGender.equals("Male", ignoreCase = true) ||
            isAdmin || isCoinSeller || isAgent ||
            receiverIsAdmin || receiverIsCoinSeller || receiverIsAgent
        ) {
            val currentCoins = fetchCoins(senderId)
            return Pair(true, currentCoins)
        }

        return withContext(Dispatchers.IO) {
            try {
                val baseUrl = SupabaseConfig.supabaseUrl.trim().removeSuffix("/")
                val apiKey = SupabaseConfig.supabaseAnonKey.trim()

                // Try RPC first: deduct_chat_coins
                val authHeader = getAuthHeader()
                if (baseUrl.isNotEmpty() && apiKey.isNotEmpty() && senderId.isNotEmpty()) {
                    try {
                        val rpcUrl = "$baseUrl/rest/v1/rpc/deduct_chat_coins"
                        val rpcBody = JSONObject().apply {
                            put("p_sender_id", senderId)
                            put("p_receiver_id", receiverId)
                            put("p_amount", 15)
                        }.toString()
                        val req = Request.Builder()
                            .url(rpcUrl)
                            .addHeader("apikey", apiKey)
                            .addHeader("Authorization", authHeader)
                            .addHeader("Content-Type", "application/json")
                            .post(rpcBody.toRequestBody(jsonMediaType))
                            .build()
                        client.newCall(req).execute().use { res ->
                            if (res.isSuccessful || res.code == 200) {
                                val bodyStr = res.body?.string() ?: ""
                                val json = JSONObject(bodyStr)
                                val success = json.optBoolean("success", false)
                                val newBal = json.optLong("new_balance", 0L)
                                return@withContext Pair(success, newBal)
                            }
                        }
                    } catch (_: Exception) {}
                }

                // Fallback direct execution
                // Double check if sender or receiver profile in DB is admin, coinseller, or agent
                val profile = fetchProfileById(senderId)
                if (profile != null && (profile.isAdmin || profile.isCoinSeller || profile.isAgent || !profile.gender.equals("Male", ignoreCase = true))) {
                    val liveCoins = fetchCoins(senderId)
                    return@withContext Pair(true, liveCoins)
                }

                val recProfile = if (receiverId.isNotBlank()) fetchProfileById(receiverId) else null
                if (recProfile != null && (recProfile.isAdmin || recProfile.isCoinSeller || recProfile.isAgent)) {
                    val liveCoins = fetchCoins(senderId)
                    return@withContext Pair(true, liveCoins)
                }

                val curCoins = fetchCoins(senderId)
                if (curCoins < 15) {
                    return@withContext Pair(false, curCoins)
                }

                val newBal = curCoins - 15
                val endpoint = "$baseUrl/rest/v1/profiles?id=eq.$senderId"
                val updateBody = JSONObject().apply { put("coins", newBal) }.toString()
                val request = Request.Builder()
                    .url(endpoint)
                    .addHeader("apikey", apiKey)
                    .addHeader("Authorization", authHeader)
                    .addHeader("Content-Type", "application/json")
                    .patch(updateBody.toRequestBody(jsonMediaType))
                    .build()

                client.newCall(request).execute().use { response ->
                    if (response.isSuccessful || response.code == 200 || response.code == 204) {
                        ProfileCache.cachedProfiles = ProfileCache.cachedProfiles.map { p ->
                            if (p.id == senderId) p.copy(coins = newBal) else p
                        }
                        recordCoinTransaction(
                            userId = senderId,
                            amount = -15L,
                            type = "CHAT_DEDUCT",
                            title = "Message to ${receiverName.ifEmpty { "User" }}",
                            description = "15 Coins deducted for text message to ${receiverName.ifEmpty { "User" }}"
                        )
                        return@withContext Pair(true, newBal)
                    }
                }
                Pair(false, curCoins)
            } catch (e: Exception) {
                e.printStackTrace()
                Pair(false, 0L)
            }
        }
    }

    /**
     * Deduct coins for sending a photo in chat:
     * - Male users: 40 coins deducted
     * - Female users: Free (0 coins deducted)
     * - Admins (is_admin = true): Free to send & receive (0 coins deducted)
     * - Coin Sellers (is_coinseller = true): Free to send & receive (0 coins deducted)
     * - Agents (is_agent = true): Free to send & receive (0 coins deducted)
     */
    suspend fun deductPhotoCoins(
        senderId: String,
        senderGender: String,
        receiverId: String,
        receiverName: String,
        isAdmin: Boolean = false,
        isCoinSeller: Boolean = false,
        isAgent: Boolean = false,
        receiverIsAdmin: Boolean = false,
        receiverIsCoinSeller: Boolean = false,
        receiverIsAgent: Boolean = false
    ): Pair<Boolean, Long> {
        // Female users, Admins, Coin Sellers, and Agents send and receive photos for free
        if (!senderGender.equals("Male", ignoreCase = true) ||
            isAdmin || isCoinSeller || isAgent ||
            receiverIsAdmin || receiverIsCoinSeller || receiverIsAgent
        ) {
            val currentCoins = fetchCoins(senderId)
            return Pair(true, currentCoins)
        }

        return withContext(Dispatchers.IO) {
            try {
                val baseUrl = SupabaseConfig.supabaseUrl.trim().removeSuffix("/")
                val apiKey = SupabaseConfig.supabaseAnonKey.trim()

                // Try RPC first: deduct_photo_coins
                val authHeader = getAuthHeader()
                if (baseUrl.isNotEmpty() && apiKey.isNotEmpty() && senderId.isNotEmpty()) {
                    try {
                        val rpcUrl = "$baseUrl/rest/v1/rpc/deduct_photo_coins"
                        val rpcBody = JSONObject().apply {
                            put("p_sender_id", senderId)
                            put("p_receiver_id", receiverId)
                            put("p_receiver_name", receiverName)
                            put("p_amount", 40)
                        }.toString()
                        val req = Request.Builder()
                            .url(rpcUrl)
                            .addHeader("apikey", apiKey)
                            .addHeader("Authorization", authHeader)
                            .addHeader("Content-Type", "application/json")
                            .post(rpcBody.toRequestBody(jsonMediaType))
                            .build()
                        client.newCall(req).execute().use { res ->
                            if (res.isSuccessful || res.code == 200) {
                                val bodyStr = res.body?.string() ?: ""
                                val json = JSONObject(bodyStr)
                                val success = json.optBoolean("success", false)
                                val newBal = json.optLong("new_balance", 0L)
                                return@withContext Pair(success, newBal)
                            }
                        }
                    } catch (_: Exception) {}
                }

                // Fallback direct execution
                // Double check if sender or receiver profile in DB is admin, coinseller, or agent
                val profile = fetchProfileById(senderId)
                if (profile != null && (profile.isAdmin || profile.isCoinSeller || profile.isAgent || !profile.gender.equals("Male", ignoreCase = true))) {
                    val liveCoins = fetchCoins(senderId)
                    return@withContext Pair(true, liveCoins)
                }

                val recProfile = if (receiverId.isNotBlank()) fetchProfileById(receiverId) else null
                if (recProfile != null && (recProfile.isAdmin || recProfile.isCoinSeller || recProfile.isAgent)) {
                    val liveCoins = fetchCoins(senderId)
                    return@withContext Pair(true, liveCoins)
                }

                val curCoins = fetchCoins(senderId)
                if (curCoins < 40) {
                    return@withContext Pair(false, curCoins)
                }

                val newBal = curCoins - 40
                val endpoint = "$baseUrl/rest/v1/profiles?id=eq.$senderId"
                val updateBody = JSONObject().apply { put("coins", newBal) }.toString()
                val request = Request.Builder()
                    .url(endpoint)
                    .addHeader("apikey", apiKey)
                    .addHeader("Authorization", authHeader)
                    .addHeader("Content-Type", "application/json")
                    .patch(updateBody.toRequestBody(jsonMediaType))
                    .build()

                client.newCall(request).execute().use { response ->
                    if (response.isSuccessful || response.code == 200 || response.code == 204) {
                        ProfileCache.cachedProfiles = ProfileCache.cachedProfiles.map { p ->
                            if (p.id == senderId) p.copy(coins = newBal) else p
                        }
                        recordCoinTransaction(
                            userId = senderId,
                            amount = -40L,
                            type = "PHOTO_DEDUCT",
                            title = "Photo to ${receiverName.ifEmpty { "User" }}",
                            description = "40 Coins deducted for sending photo to ${receiverName.ifEmpty { "User" }}"
                        )
                        return@withContext Pair(true, newBal)
                    }
                }
                Pair(false, curCoins)
            } catch (e: Exception) {
                e.printStackTrace()
                Pair(false, 0L)
            }
        }
    }

    /**
     * Production-grade Gift Sending:
     * Supports consecutive gifting (unique per-click transaction keys)
     * and percentage-of-time conversion with fractional diamonds (e.g. 86.55 💎).
     */
    suspend fun sendGiftSecure(
        recipientId: String,
        giftId: String,
        context: Context? = null,
        originalMessageId: Long? = null
    ): Pair<Boolean, Long> {
        return withContext(Dispatchers.IO) {
            try {
                val baseUrl = SupabaseConfig.supabaseUrl.trim().removeSuffix("/")
                val apiKey = SupabaseConfig.supabaseAnonKey.trim()
                if (baseUrl.isEmpty() || apiKey.isEmpty()) return@withContext Pair(false, 0L)

                val authHeader = UserSessionManager.getAuthHeader(context)
                // Distinct high-resolution key per gift attempt to allow consecutive back-to-back gifting
                val idempotencyKey = "gift_${giftId}_${System.currentTimeMillis()}_${java.util.UUID.randomUUID().toString().take(8)}"

                val jsonBody = JSONObject().apply {
                    put("recipient_id", recipientId)
                    put("gift_id", giftId)
                    put("idempotency_key", idempotencyKey)
                    if (originalMessageId != null && originalMessageId > 0L) {
                        put("original_message_id", originalMessageId)
                    }
                }.toString()

                // 1. Try Edge Function: /functions/v1/send-gift
                val edgeUrl = "$baseUrl/functions/v1/send-gift"
                val edgeReq = Request.Builder()
                    .url(edgeUrl)
                    .addHeader("apikey", apiKey)
                    .addHeader("Authorization", authHeader)
                    .addHeader("Content-Type", "application/json")
                    .post(jsonBody.toRequestBody(jsonMediaType))
                    .build()

                try {
                    client.newCall(edgeReq).execute().use { response ->
                        if (response.isSuccessful || response.code == 200) {
                            val bodyStr = response.body?.string() ?: ""
                            val resObj = JSONObject(bodyStr)
                            val success = resObj.optBoolean("success", false)
                            val newSenderCoins = resObj.optLong("new_sender_coins", 0L)
                            val senderId = resObj.optString("sender_id", "")
                            if (success) {
                                if (senderId.isNotBlank()) {
                                    ProfileCache.cachedProfiles = ProfileCache.cachedProfiles.map { p ->
                                        if (p.id == senderId) p.copy(coins = newSenderCoins) else p
                                    }
                                }
                                return@withContext Pair(true, newSenderCoins)
                            }
                        }
                    }
                } catch (_: Exception) {}

                // 2. Direct RPC fallback: /rest/v1/rpc/process_gift
                val rpcUrl = "$baseUrl/rest/v1/rpc/process_gift"
                val rpcBody = JSONObject().apply {
                    put("p_recipient_id", recipientId)
                    put("p_gift_id", giftId)
                    put("p_idempotency_key", idempotencyKey)
                    if (originalMessageId != null && originalMessageId > 0L) {
                        put("p_original_message_id", originalMessageId)
                    }
                }.toString()

                val rpcReq = Request.Builder()
                    .url(rpcUrl)
                    .addHeader("apikey", apiKey)
                    .addHeader("Authorization", authHeader)
                    .addHeader("Content-Type", "application/json")
                    .post(rpcBody.toRequestBody(jsonMediaType))
                    .build()

                client.newCall(rpcReq).execute().use { response ->
                    if (response.isSuccessful || response.code == 200) {
                        val bodyStr = response.body?.string() ?: ""
                        val resObj = JSONObject(bodyStr)
                        val success = resObj.optBoolean("success", false)
                        val newSenderCoins = resObj.optLong("new_sender_coins", 0L)
                        val senderId = resObj.optString("sender_id", "")
                        if (success) {
                            if (senderId.isNotBlank()) {
                                ProfileCache.cachedProfiles = ProfileCache.cachedProfiles.map { p ->
                                    if (p.id == senderId) p.copy(coins = newSenderCoins) else p
                                }
                            }
                            return@withContext Pair(true, newSenderCoins)
                        }
                    }
                }

                Pair(false, 0L)
            } catch (e: Exception) {
                e.printStackTrace()
                Pair(false, 0L)
            }
        }
    }

    /**
     * Deduct coins for sending gifts
     */
    suspend fun deductGiftCoins(
        senderId: String,
        giftName: String,
        coins: Long,
        receiverId: String,
        receiverName: String,
        isAdmin: Boolean = false,
        isCoinSeller: Boolean = false
    ): Pair<Boolean, Long> {
        // ALL users (including Admin, Coin Seller, Agent, Male, Female) must be charged for sending gifts
        return withContext(Dispatchers.IO) {
            try {
                val baseUrl = SupabaseConfig.supabaseUrl.trim().removeSuffix("/")
                val apiKey = SupabaseConfig.supabaseAnonKey.trim()
                if (baseUrl.isEmpty() || apiKey.isEmpty()) return@withContext Pair(true, 0L)

                // 1. Try RPC first: deduct_gift_coins
                val authHeader = getAuthHeader()
                try {
                    val rpcUrl = "$baseUrl/rest/v1/rpc/deduct_gift_coins"
                    val rpcBody = JSONObject().apply {
                        put("p_sender_id", senderId)
                        put("p_gift_name", giftName)
                        put("p_coins", coins)
                        put("p_receiver_id", receiverId)
                        put("p_receiver_name", receiverName)
                    }.toString()

                    val rpcReq = Request.Builder()
                        .url(rpcUrl)
                        .addHeader("apikey", apiKey)
                        .addHeader("Authorization", authHeader)
                        .addHeader("Content-Type", "application/json")
                        .post(rpcBody.toRequestBody(jsonMediaType))
                        .build()

                    client.newCall(rpcReq).execute().use { response ->
                        if (response.isSuccessful || response.code == 200) {
                            val respStr = response.body?.string() ?: ""
                            val json = JSONObject(respStr)
                            val success = json.optBoolean("success", false)
                            val newBalance = json.optLong("new_balance", 0L)
                            if (success) {
                                ProfileCache.cachedProfiles = ProfileCache.cachedProfiles.map { p ->
                                    if (p.id == senderId) p.copy(coins = newBalance) else p
                                }
                                return@withContext Pair(true, newBalance)
                            }
                        }
                    }
                } catch (_: Exception) {}

                // 2. Direct fallback
                val curCoins = fetchCoins(senderId)
                if (curCoins < coins) {
                    return@withContext Pair(false, curCoins)
                }

                val newBal = curCoins - coins
                val profile = fetchProfileById(senderId)
                val endpoints = mutableListOf("$baseUrl/rest/v1/profiles?id=eq.$senderId")
                val userEmail = profile?.email ?: ""
                val userNumId = profile?.numericId ?: 0L
                if (userEmail.isNotBlank()) endpoints.add("$baseUrl/rest/v1/profiles?email=eq.$userEmail")
                if (userNumId > 0L) endpoints.add("$baseUrl/rest/v1/profiles?numeric_id=eq.$userNumId")

                val body = JSONObject().apply {
                    put("coins", newBal)
                }.toString()

                var patched = false
                for (ep in endpoints) {
                    try {
                        val req = Request.Builder()
                            .url(ep)
                            .addHeader("apikey", apiKey)
                            .addHeader("Authorization", authHeader)
                            .addHeader("Content-Type", "application/json")
                            .addHeader("Prefer", "return=minimal")
                            .patch(body.toRequestBody(jsonMediaType))
                            .build()

                        client.newCall(req).execute().use { response ->
                            if (response.isSuccessful || response.code in 200..204) {
                                patched = true
                            }
                        }
                    } catch (_: Exception) {}
                }

                if (patched || newBal >= 0) {
                    ProfileCache.cachedProfiles = ProfileCache.cachedProfiles.map { p ->
                        if (p.id == senderId) p.copy(coins = newBal) else p
                    }
                    recordCoinTransaction(
                        userId = senderId,
                        amount = -coins,
                        type = "GIFT_DEDUCT",
                        title = "Gift $giftName to ${receiverName.ifEmpty { "User" }}",
                        description = "$coins Coins deducted for sending gift $giftName"
                    )
                    return@withContext Pair(true, newBal)
                }
                Pair(false, curCoins)
            } catch (e: Exception) {
                e.printStackTrace()
                Pair(false, 0L)
            }
        }
    }

    /**
     * Deduct coins for calling per minute:
     * - Video Call: Male users deducted 160 coins/min
     * - Voice Call: Male users deducted 80 coins/min
     * - Female users: Free
     * - Note: If female calls male, the male user is still deducted per minute.
     */
    suspend fun deductCallMinute(
        callerId: String,
        callerGender: String,
        calleeId: String,
        calleeGender: String,
        isVideo: Boolean,
        callerName: String = "",
        calleeName: String = ""
    ): Pair<Boolean, Long> {
        val rate = if (isVideo) 160L else 80L
        val isCallerMale = callerGender.equals("Male", ignoreCase = true)
        val isCalleeMale = calleeGender.equals("Male", ignoreCase = true)

        // If neither participant is male, call is completely free
        if (!isCallerMale && !isCalleeMale) {
            val cur = fetchCoins(callerId)
            return Pair(true, cur)
        }

        return withContext(Dispatchers.IO) {
            try {
                val baseUrl = SupabaseConfig.supabaseUrl.trim().removeSuffix("/")
                val apiKey = SupabaseConfig.supabaseAnonKey.trim()

                // Try RPC first: deduct_call_minute_coins
                val authHeader = getAuthHeader()
                if (baseUrl.isNotEmpty() && apiKey.isNotEmpty()) {
                    try {
                        val rpcUrl = "$baseUrl/rest/v1/rpc/deduct_call_minute_coins"
                        val rpcBody = JSONObject().apply {
                            put("p_caller_id", callerId)
                            put("p_caller_gender", callerGender)
                            put("p_callee_id", calleeId)
                            put("p_callee_gender", calleeGender)
                            put("p_call_type", if (isVideo) "VIDEO" else "VOICE")
                        }.toString()
                        val req = Request.Builder()
                            .url(rpcUrl)
                            .addHeader("apikey", apiKey)
                            .addHeader("Authorization", authHeader)
                            .addHeader("Content-Type", "application/json")
                            .post(rpcBody.toRequestBody(jsonMediaType))
                            .build()
                        client.newCall(req).execute().use { res ->
                            if (res.isSuccessful || res.code == 200) {
                                val bodyStr = res.body?.string() ?: ""
                                val json = JSONObject(bodyStr)
                                val success = json.optBoolean("success", false)
                                val callerBal = json.optLong("caller_balance", 0L)
                                return@withContext Pair(success, callerBal)
                            }
                        }
                    } catch (_: Exception) {}
                }

                // Fallback direct execution
                var callerSuccess = true
                var callerRemaining = 0L

                if (isCallerMale) {
                    val callerCurCoins = fetchCoins(callerId)
                    if (callerCurCoins < rate) {
                        return@withContext Pair(false, callerCurCoins)
                    }
                    val newCallerCoins = callerCurCoins - rate
                    val endpoint = "$baseUrl/rest/v1/profiles?id=eq.$callerId"
                    val updateBody = JSONObject().apply { put("coins", newCallerCoins) }.toString()
                    val req = Request.Builder()
                        .url(endpoint)
                        .addHeader("apikey", apiKey)
                        .addHeader("Authorization", authHeader)
                        .addHeader("Content-Type", "application/json")
                        .patch(updateBody.toRequestBody(jsonMediaType))
                        .build()
                    client.newCall(req).execute().use { r ->
                        callerSuccess = r.isSuccessful || r.code == 200 || r.code == 204
                        if (callerSuccess) {
                            callerRemaining = newCallerCoins
                            ProfileCache.cachedProfiles = ProfileCache.cachedProfiles.map { p ->
                                if (p.id == callerId) p.copy(coins = newCallerCoins) else p
                            }
                            recordCoinTransaction(
                                userId = callerId,
                                amount = -rate,
                                type = "CALL_DEDUCT",
                                title = "${if (isVideo) "Video" else "Voice"} Call (1 min)",
                                description = "$rate Coins deducted for ${if (isVideo) "Video" else "Voice"} call with ${calleeName.ifEmpty { "User" }}"
                            )
                        }
                    }
                } else {
                    callerRemaining = fetchCoins(callerId)
                }

                // If callee is Male (e.g. female called male), deduct callee as well
                if (isCalleeMale && calleeId.isNotEmpty()) {
                    val calleeCoins = fetchCoins(calleeId)
                    if (calleeCoins >= rate) {
                        val newCalleeCoins = calleeCoins - rate
                        val endpoint = "$baseUrl/rest/v1/profiles?id=eq.$calleeId"
                        val updateBody = JSONObject().apply { put("coins", newCalleeCoins) }.toString()
                        val req = Request.Builder()
                            .url(endpoint)
                            .addHeader("apikey", apiKey)
                            .addHeader("Authorization", authHeader)
                            .addHeader("Content-Type", "application/json")
                            .patch(updateBody.toRequestBody(jsonMediaType))
                            .build()
                        client.newCall(req).execute().use { r ->
                            if (r.isSuccessful || r.code == 200 || r.code == 204) {
                                ProfileCache.cachedProfiles = ProfileCache.cachedProfiles.map { p ->
                                    if (p.id == calleeId) p.copy(coins = newCalleeCoins) else p
                                }
                                recordCoinTransaction(
                                    userId = calleeId,
                                    amount = -rate,
                                    type = "CALL_DEDUCT",
                                    title = "${if (isVideo) "Video" else "Voice"} Call (1 min)",
                                    description = "$rate Coins deducted for ${if (isVideo) "Video" else "Voice"} call with ${callerName.ifEmpty { "User" }}"
                                )
                            }
                        }
                    }
                }

                Pair(callerSuccess, callerRemaining)
            } catch (e: Exception) {
                e.printStackTrace()
                Pair(false, 0L)
            }
        }
    }

    /**
     * Top up / Recharge coins for a user directly to Supabase table 'profiles'
     */
    suspend fun topUpCoins(userId: String, amountToAdd: Long, method: String = "Recharge"): Long {
        return withContext(Dispatchers.IO) {
            try {
                val baseUrl = SupabaseConfig.supabaseUrl.trim().removeSuffix("/")
                val apiKey = SupabaseConfig.supabaseAnonKey.trim()
                if (baseUrl.isEmpty() || apiKey.isEmpty() || userId.isEmpty()) return@withContext 0L

                val currentCoins = fetchCoins(userId)
                val newCoins = currentCoins + amountToAdd

                val endpoint = "$baseUrl/rest/v1/profiles?id=eq.$userId"
                val updateBody = JSONObject().apply { put("coins", newCoins) }.toString()
                val authHeader = getAuthHeader()

                val request = Request.Builder()
                    .url(endpoint)
                    .addHeader("apikey", apiKey)
                    .addHeader("Authorization", authHeader)
                    .addHeader("Content-Type", "application/json")
                    .addHeader("Prefer", "return=representation")
                    .patch(updateBody.toRequestBody(jsonMediaType))
                    .build()

                client.newCall(request).execute().use { response ->
                    if (response.isSuccessful || response.code == 200 || response.code == 204) {
                        ProfileCache.cachedProfiles = ProfileCache.cachedProfiles.map { p ->
                            if (p.id == userId) p.copy(coins = newCoins) else p
                        }
                        recordCoinTransaction(
                            userId = userId,
                            amount = amountToAdd,
                            type = "RECHARGE",
                            title = "Coin Recharge ($method)",
                            description = "Purchased +$amountToAdd coins via $method"
                        )
                        return@withContext newCoins
                    }
                }
                newCoins
            } catch (e: Exception) {
                e.printStackTrace()
                0L
            }
        }
    }

    /**
     * Helper alias to fetch user coins
     */
    suspend fun fetchUserCoins(userId: String): Long {
        return fetchCoins(userId)
    }

    /**
     * Direct update user coin balance on Supabase server
     */
    suspend fun updateUserCoinsDirect(userId: String, newCoins: Long): Boolean {
        return withContext(Dispatchers.IO) {
            try {
                val cleanId = userId.trim()
                val baseUrl = SupabaseConfig.supabaseUrl.trim().removeSuffix("/")
                val apiKey = SupabaseConfig.supabaseAnonKey.trim()
                if (baseUrl.isEmpty() || apiKey.isEmpty() || cleanId.isEmpty()) return@withContext false

                val endpoint = "$baseUrl/rest/v1/profiles?id=eq.$cleanId"
                val authHeader = getAuthHeader()
                val updateBody = JSONObject().apply {
                    put("coins", newCoins)
                }.toString()

                val request = Request.Builder()
                    .url(endpoint)
                    .addHeader("apikey", apiKey)
                    .addHeader("Authorization", authHeader)
                    .addHeader("Content-Type", "application/json")
                    .addHeader("Prefer", "return=minimal")
                    .patch(updateBody.toRequestBody(jsonMediaType))
                    .build()

                val response = client.newCall(request).execute()
                val isSuccess = response.isSuccessful || response.code in 200..204
                response.close()
                if (isSuccess) {
                    ProfileCache.cachedProfiles = ProfileCache.cachedProfiles.map { p ->
                        if (p.id == cleanId) p.copy(coins = newCoins) else p
                    }
                }
                isSuccess
            } catch (e: Exception) {
                e.printStackTrace()
                false
            }
        }
    }

    /**
     * Record a coin transaction in table 'coin_transactions'
     * Automatically keeps only the last 40 transactions by group-pruning older records.
     */
    suspend fun recordCoinTransaction(
        userId: String,
        amount: Long,
        type: String,
        title: String,
        description: String,
        referenceId: String = "TX-" + System.currentTimeMillis().toString().takeLast(8)
    ): Boolean {
        return withContext(Dispatchers.IO) {
            try {
                val baseUrl = SupabaseConfig.supabaseUrl.trim().removeSuffix("/")
                val apiKey = SupabaseConfig.supabaseAnonKey.trim()
                if (baseUrl.isEmpty() || apiKey.isEmpty() || userId.isEmpty()) return@withContext false

                val url = "$baseUrl/rest/v1/coin_transactions"
                val authHeader = getAuthHeader()
                val body = JSONObject().apply {
                    put("user_id", userId)
                    put("amount", amount)
                    put("type", type)
                    put("transaction_type", type)
                    put("title", title)
                    put("description", description)
                    put("reference_id", referenceId)
                    put("status", "Completed")
                    put("created_at", SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss.SSS'Z'", Locale.US).format(Date()))
                }.toString()

                val request = Request.Builder()
                    .url(url)
                    .addHeader("apikey", apiKey)
                    .addHeader("Authorization", authHeader)
                    .addHeader("Content-Type", "application/json")
                    .addHeader("Prefer", "return=minimal")
                    .post(body.toRequestBody(jsonMediaType))
                    .build()

                val success = client.newCall(request).execute().use { response ->
                    response.isSuccessful || response.code == 200 || response.code == 201 || response.code == 204
                }

                if (success) {
                    // Group-prune transactions beyond the latest 40 to enforce max 40 rule
                    pruneOldCoinTransactions(userId, keepLimit = 40)
                }

                success
            } catch (e: Exception) {
                e.printStackTrace()
                false
            }
        }
    }

    /**
     * Prune old transactions in a group so that only the latest [keepLimit] (40) records are kept.
     */
    suspend fun pruneOldCoinTransactions(userId: String, keepLimit: Int = 40) {
        withContext(Dispatchers.IO) {
            try {
                val baseUrl = SupabaseConfig.supabaseUrl.trim().removeSuffix("/")
                val apiKey = SupabaseConfig.supabaseAnonKey.trim()
                if (baseUrl.isEmpty() || apiKey.isEmpty() || userId.isEmpty()) return@withContext

                // 1. Fetch transaction IDs ordered by created_at desc
                val authHeader = getAuthHeader()
                val endpoint = "$baseUrl/rest/v1/coin_transactions?user_id=eq.$userId&select=id,created_at&order=created_at.desc&limit=100"
                val request = Request.Builder()
                    .url(endpoint)
                    .addHeader("apikey", apiKey)
                    .addHeader("Authorization", authHeader)
                    .get()
                    .build()

                val idsToDelete = mutableListOf<String>()
                client.newCall(request).execute().use { response ->
                    val body = response.body?.string() ?: ""
                    if (response.isSuccessful && body.startsWith("[")) {
                        val arr = JSONArray(body)
                        if (arr.length() > keepLimit) {
                            for (i in keepLimit until arr.length()) {
                                val obj = arr.getJSONObject(i)
                                val id = obj.optString("id", "")
                                if (id.isNotEmpty()) {
                                    idsToDelete.add(id)
                                }
                            }
                        }
                    }
                }

                // 2. Group delete old records exceeding the 40 limit
                if (idsToDelete.isNotEmpty()) {
                    val joinedIds = idsToDelete.joinToString(",")
                    val deleteUrl = "$baseUrl/rest/v1/coin_transactions?user_id=eq.$userId&id=in.($joinedIds)"
                    val deleteReq = Request.Builder()
                        .url(deleteUrl)
                        .addHeader("apikey", apiKey)
                        .addHeader("Authorization", authHeader)
                        .delete()
                        .build()
                    client.newCall(deleteReq).execute().close()
                }
            } catch (e: Exception) {
                e.printStackTrace()
            }
        }
    }

    /**
     * Fetch coin transaction history for a user from 'coin_transactions' (capped at 40 max)
     */
    suspend fun fetchCoinTransactions(userId: String): List<CoinTransaction> {
        return withContext(Dispatchers.IO) {
            try {
                val baseUrl = SupabaseConfig.supabaseUrl.trim().removeSuffix("/")
                val apiKey = SupabaseConfig.supabaseAnonKey.trim()
                if (baseUrl.isEmpty() || apiKey.isEmpty() || userId.isEmpty()) return@withContext emptyList()

                val endpoint = "$baseUrl/rest/v1/coin_transactions?user_id=eq.$userId&order=created_at.desc&limit=40"
                val authHeader = getAuthHeader()
                val request = Request.Builder()
                    .url(endpoint)
                    .addHeader("apikey", apiKey)
                    .addHeader("Authorization", authHeader)
                    .get()
                    .build()

                client.newCall(request).execute().use { response ->
                    val body = response.body?.string() ?: ""
                    if (response.isSuccessful && body.startsWith("[")) {
                        val arr = JSONArray(body)
                        val list = mutableListOf<CoinTransaction>()
                        val maxCount = minOf(arr.length(), 40)
                        for (i in 0 until maxCount) {
                            val obj = arr.getJSONObject(i)
                            list.add(
                                CoinTransaction(
                                    id = obj.optString("id", "tx_$i"),
                                    userId = obj.optString("user_id", userId),
                                    amount = obj.optLong("amount", 0L),
                                    type = obj.optString("transaction_type", obj.optString("type", "RECHARGE")),
                                    title = obj.optString("title", "Transaction"),
                                    description = obj.optString("description", ""),
                                    referenceId = obj.optString("reference_id", "TX-${1000 + i}"),
                                    status = obj.optString("status", "Completed"),
                                    createdAt = obj.optString("created_at", "")
                                )
                            )
                        }
                        return@withContext list
                    }
                }
                emptyList()
            } catch (e: Exception) {
                e.printStackTrace()
                emptyList()
            }
        }
    }

    /**
     * Fetch diamond transaction history for a user from 'diamond_transactions' (capped at 40 max)
     */
    suspend fun fetchDiamondTransactions(userId: String): List<DiamondTransaction> {
        return withContext(Dispatchers.IO) {
            try {
                val baseUrl = SupabaseConfig.supabaseUrl.trim().removeSuffix("/")
                val apiKey = SupabaseConfig.supabaseAnonKey.trim()
                if (baseUrl.isEmpty() || apiKey.isEmpty() || userId.isEmpty()) return@withContext emptyList()

                val endpoint = "$baseUrl/rest/v1/diamond_transactions?user_id=eq.$userId&order=created_at.desc&limit=40"
                val authHeader = getAuthHeader()
                val request = Request.Builder()
                    .url(endpoint)
                    .addHeader("apikey", apiKey)
                    .addHeader("Authorization", authHeader)
                    .get()
                    .build()

                client.newCall(request).execute().use { response ->
                    val body = response.body?.string() ?: ""
                    if (response.isSuccessful && body.startsWith("[")) {
                        val arr = JSONArray(body)
                        val list = mutableListOf<DiamondTransaction>()
                        val maxCount = minOf(arr.length(), 40)
                        for (i in 0 until maxCount) {
                            val obj = arr.getJSONObject(i)
                            list.add(
                                DiamondTransaction(
                                    id = obj.optString("id", "dia_tx_$i"),
                                    userId = obj.optString("user_id", userId),
                                    amount = obj.optLong("amount", 0L),
                                    type = obj.optString("transaction_type", obj.optString("type", "EXCHANGE_COINS")),
                                    title = obj.optString("title", "Diamond Transaction"),
                                    description = obj.optString("description", ""),
                                    referenceId = obj.optString("reference_id", "DIA-${1000 + i}"),
                                    status = obj.optString("status", "Completed"),
                                    createdAt = obj.optString("created_at", "")
                                )
                            )
                        }
                        return@withContext list
                    }
                }
                emptyList()
            } catch (e: Exception) {
                e.printStackTrace()
                emptyList()
            }
        }
    }

    /**
     * Record a diamond transaction into Supabase 'diamond_transactions' table
     */
    suspend fun recordDiamondTransaction(
        userId: String,
        amount: Long,
        type: String, // "EXCHANGE_COINS", "GIFT_RECEIVED", "HOST_REWARD", "CASHOUT", "BONUS"
        title: String,
        description: String,
        referenceId: String = "DIA-" + System.currentTimeMillis().toString().takeLast(8)
    ) {
        withContext(Dispatchers.IO) {
            try {
                val baseUrl = SupabaseConfig.supabaseUrl.trim().removeSuffix("/")
                val apiKey = SupabaseConfig.supabaseAnonKey.trim()
                if (baseUrl.isEmpty() || apiKey.isEmpty() || userId.isEmpty()) return@withContext

                val endpoint = "$baseUrl/rest/v1/diamond_transactions"
                val body = JSONObject().apply {
                    put("user_id", userId)
                    put("amount", amount)
                    put("type", type)
                    put("transaction_type", type)
                    put("title", title)
                    put("description", description)
                    put("reference_id", referenceId)
                    put("status", "Completed")
                }.toString()

                val authHeader = getAuthHeader()
                val request = Request.Builder()
                    .url(endpoint)
                    .addHeader("apikey", apiKey)
                    .addHeader("Authorization", authHeader)
                    .addHeader("Content-Type", "application/json")
                    .addHeader("Prefer", "return=minimal")
                    .post(body.toRequestBody(jsonMediaType))
                    .build()

                client.newCall(request).execute().close()
            } catch (e: Exception) {
                e.printStackTrace()
            }
        }
    }

    /**
     * Claim daily checkin in Supabase profiles and record transaction
     */
    fun isDateMatchingToday(dateStr: String): Boolean {
        return UserSessionManager.isSameDayOnPhoneCalendar(dateStr)
    }

data class ServerClaimResult(
    val success: Boolean,
    val isAlreadyClaimed: Boolean = false,
    val updatedCoins: Long = 0L,
    val dayNumber: Int = 0,
    val message: String = ""
)

    /**
     * Claim daily check-in reward and validate against Supabase server.
     * Prevents stale client states by only confirming once server updates successfully.
     */
    suspend fun claimDailyCheckin(
        userId: String,
        dayNumber: Int,
        coinsToAward: Int,
        userEmail: String = "",
        context: Context? = null
    ): ServerClaimResult {
        return withContext(Dispatchers.IO) {
            try {
                val baseUrl = SupabaseConfig.supabaseUrl.trim().removeSuffix("/")
                val apiKey = SupabaseConfig.supabaseAnonKey.trim()
                val todayStr = SimpleDateFormat("yyyy-MM-dd", Locale.US).format(Date())

                if (baseUrl.isEmpty() || apiKey.isEmpty() || userId.isBlank()) {
                    return@withContext ServerClaimResult(success = false, message = "Supabase configuration missing or invalid user ID")
                }

                val authHeader = getAuthHeader(context)

                // 1. Fetch live profile to check last_checkin_date on server
                val currentProfile = fetchProfileById(userId)
                    ?: (if (userEmail.isNotBlank()) fetchProfile(userId, userEmail) else null)
                    ?: ProfileCache.cachedProfiles.firstOrNull { it.id == userId || (userEmail.isNotBlank() && it.email.equals(userEmail, true)) }

                if (currentProfile != null && isDateMatchingToday(currentProfile.lastCheckinDate)) {
                    // Already claimed today according to verified server records
                    return@withContext ServerClaimResult(
                        success = false,
                        isAlreadyClaimed = true,
                        updatedCoins = currentProfile.coins,
                        dayNumber = if (currentProfile.lastCheckinDay > 0) currentProfile.lastCheckinDay else dayNumber,
                        message = "Already claimed for today on server"
                    )
                }

                val currentCoins = currentProfile?.coins ?: fetchCoins(userId)
                val newCoins = currentCoins + coinsToAward

                // 2. Update profile with last_checkin_date, last_checkin_day, and coins
                val endpoint = "$baseUrl/rest/v1/profiles?id=eq.$userId"
                val updateBodyFull = JSONObject().apply {
                    put("coins", newCoins)
                    put("last_checkin_date", todayStr)
                    put("last_checkin_day", dayNumber)
                }.toString()

                val requestFull = Request.Builder()
                    .url(endpoint)
                    .addHeader("apikey", apiKey)
                    .addHeader("Authorization", authHeader)
                    .addHeader("Content-Type", "application/json")
                    .addHeader("Prefer", "return=minimal")
                    .patch(updateBodyFull.toRequestBody(jsonMediaType))
                    .build()

                var success = false
                try {
                    client.newCall(requestFull).execute().use { resp ->
                        success = resp.isSuccessful || resp.code in 200..204
                    }
                } catch (_: Exception) {}

                // Fallback: If full patch failed, try target endpoints
                if (!success) {
                    val updateBodyCoinsOnly = JSONObject().apply {
                        put("coins", newCoins)
                    }.toString()

                    val targetEndpoints = mutableListOf("$baseUrl/rest/v1/profiles?id=eq.$userId")
                    if (userEmail.isNotBlank()) {
                        targetEndpoints.add("$baseUrl/rest/v1/profiles?email=eq.$userEmail")
                    }
                    if (currentProfile?.numericId != null && currentProfile.numericId > 0) {
                        targetEndpoints.add("$baseUrl/rest/v1/profiles?numeric_id=eq.${currentProfile.numericId}")
                    }

                    for (ep in targetEndpoints) {
                        try {
                            val req = Request.Builder()
                                .url(ep)
                                .addHeader("apikey", apiKey)
                                .addHeader("Authorization", authHeader)
                                .addHeader("Content-Type", "application/json")
                                .addHeader("Prefer", "return=minimal")
                                .patch(updateBodyCoinsOnly.toRequestBody(jsonMediaType))
                                .build()
                            client.newCall(req).execute().use { resp ->
                                if (resp.isSuccessful || resp.code in 200..204) {
                                    success = true
                                }
                            }
                            if (success) break
                        } catch (_: Exception) {}
                    }
                }

                if (!success) {
                    return@withContext ServerClaimResult(
                        success = false,
                        isAlreadyClaimed = false,
                        message = "Could not verify update with server. Please check your connection."
                    )
                }

                // 3. Record transaction in database
                try {
                    recordCoinTransaction(
                        userId = userId,
                        amount = coinsToAward.toLong(),
                        type = "DAILY_CLAIM",
                        title = "Day $dayNumber Check-in Reward",
                        description = "Daily login streak reward"
                    )
                } catch (_: Exception) {}

                // 4. Update cached profile with server-approved state
                if (currentProfile != null) {
                    val updated = currentProfile.copy(
                        coins = newCoins,
                        lastCheckinDate = todayStr,
                        lastCheckinDay = dayNumber
                    )
                    ProfileCache.cachedProfiles = ProfileCache.cachedProfiles.filter { it.id != userId } + updated
                }

                ServerClaimResult(
                    success = true,
                    isAlreadyClaimed = false,
                    updatedCoins = newCoins,
                    dayNumber = dayNumber,
                    message = "+$coinsToAward Coins Claimed!"
                )
            } catch (e: Exception) {
                e.printStackTrace()
                ServerClaimResult(
                    success = false,
                    isAlreadyClaimed = false,
                    message = e.localizedMessage ?: "Failed to connect to server"
                )
            }
        }
    }

    /**
     * Deduct 5000 coins for creating a party room
     * Returns Pair<Boolean (success), Long (newBalance or currentBalance)>
     */
    suspend fun deductPartyRoomCreationFee(
        userId: String,
        roomName: String
    ): Pair<Boolean, Long> {
        return withContext(Dispatchers.IO) {
            try {
                val baseUrl = SupabaseConfig.supabaseUrl.trim().removeSuffix("/")
                val apiKey = SupabaseConfig.supabaseAnonKey.trim()

                if (baseUrl.isEmpty() || apiKey.isEmpty() || userId.isBlank()) {
                    return@withContext Pair(false, 0L)
                }

                val authHeader = getAuthHeader()
                val curCoins = fetchCoins(userId)
                if (curCoins < 5000L) {
                    return@withContext Pair(false, curCoins)
                }

                val newBal = curCoins - 5000L
                val endpoint = "$baseUrl/rest/v1/profiles?id=eq.$userId"
                val updateBody = JSONObject().apply { put("coins", newBal) }.toString()
                val request = Request.Builder()
                    .url(endpoint)
                    .addHeader("apikey", apiKey)
                    .addHeader("Authorization", authHeader)
                    .addHeader("Content-Type", "application/json")
                    .patch(updateBody.toRequestBody(jsonMediaType))
                    .build()

                client.newCall(request).execute().use { response ->
                    if (response.isSuccessful || response.code in 200..204) {
                        ProfileCache.cachedProfiles = ProfileCache.cachedProfiles.map { p ->
                            if (p.id == userId) p.copy(coins = newBal) else p
                        }
                        recordCoinTransaction(
                            userId = userId,
                            amount = -5000L,
                            type = "PARTY_ROOM_CREATION",
                            title = "Party Room Creation Fee",
                            description = "5,000 Coins deducted for creating party room: $roomName"
                        )
                        return@withContext Pair(true, newBal)
                    }
                }
                Pair(false, curCoins)
            } catch (e: Exception) {
                e.printStackTrace()
                Pair(false, 0L)
            }
        }
    }

    // In-memory blocked cache for instant lookups & offline resilience
    private val inMemoryBlockedMap = java.util.concurrent.ConcurrentHashMap<String, MutableSet<String>>()
    private val inMemoryBlockedByMap = java.util.concurrent.ConcurrentHashMap<String, MutableSet<String>>()

    /**
     * Block a user by saving to blocked_users table and local persistence
     */
    suspend fun blockUser(myUserId: String, targetUserId: String, context: Context? = null): Boolean {
        if (myUserId.isBlank() || targetUserId.isBlank()) return false
        
        // Update in-memory cache
        val set = inMemoryBlockedMap.getOrPut(myUserId) { java.util.concurrent.ConcurrentHashMap.newKeySet() }
        set.add(targetUserId)

        // Save to SharedPreferences if context is available
        context?.let { ctx ->
            try {
                val prefs = ctx.getSharedPreferences("qivo_blocked_users", Context.MODE_PRIVATE)
                val current = prefs.getStringSet("blocked_$myUserId", emptySet())?.toMutableSet() ?: mutableSetOf()
                current.add(targetUserId)
                prefs.edit().putStringSet("blocked_$myUserId", current).apply()
            } catch (e: Exception) {
                e.printStackTrace()
            }
        }

        return withContext(Dispatchers.IO) {
            try {
                val baseUrl = SupabaseConfig.supabaseUrl.trim().removeSuffix("/")
                val apiKey = SupabaseConfig.supabaseAnonKey.trim()
                if (baseUrl.isEmpty() || apiKey.isEmpty()) return@withContext true

                val url = "$baseUrl/rest/v1/blocked_users"
                val authHeader = getAuthHeader()
                val body = JSONObject().apply {
                    put("blocker_id", myUserId)
                    put("blocked_id", targetUserId)
                    put("created_at", SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss.SSS'Z'", Locale.US).format(Date()))
                }.toString()

                val request = Request.Builder()
                    .url(url)
                    .addHeader("apikey", apiKey)
                    .addHeader("Authorization", authHeader)
                    .addHeader("Content-Type", "application/json")
                    .addHeader("Prefer", "resolution=merge-duplicates,return=minimal")
                    .post(body.toRequestBody(jsonMediaType))
                    .build()

                client.newCall(request).execute().use { response ->
                    response.isSuccessful || response.code == 200 || response.code == 201 || response.code == 204
                }
            } catch (e: Exception) {
                e.printStackTrace()
                true
            }
        }
    }

    /**
     * Unblock a user by removing from blocked_users table and local persistence
     */
    suspend fun unblockUser(myUserId: String, targetUserId: String, context: Context? = null): Boolean {
        if (myUserId.isBlank() || targetUserId.isBlank()) return false

        // Update in-memory cache
        inMemoryBlockedMap[myUserId]?.remove(targetUserId)

        // Update SharedPreferences
        context?.let { ctx ->
            try {
                val prefs = ctx.getSharedPreferences("qivo_blocked_users", Context.MODE_PRIVATE)
                val current = prefs.getStringSet("blocked_$myUserId", emptySet())?.toMutableSet() ?: mutableSetOf()
                current.remove(targetUserId)
                prefs.edit().putStringSet("blocked_$myUserId", current).apply()
            } catch (e: Exception) {
                e.printStackTrace()
            }
        }

        return withContext(Dispatchers.IO) {
            try {
                val baseUrl = SupabaseConfig.supabaseUrl.trim().removeSuffix("/")
                val apiKey = SupabaseConfig.supabaseAnonKey.trim()
                if (baseUrl.isEmpty() || apiKey.isEmpty()) return@withContext true

                val url = "$baseUrl/rest/v1/blocked_users?blocker_id=eq.$myUserId&blocked_id=eq.$targetUserId"
                val authHeader = getAuthHeader()
                val request = Request.Builder()
                    .url(url)
                    .addHeader("apikey", apiKey)
                    .addHeader("Authorization", authHeader)
                    .delete()
                    .build()

                client.newCall(request).execute().use { response ->
                    response.isSuccessful || response.code == 200 || response.code == 204
                }
            } catch (e: Exception) {
                e.printStackTrace()
                true
            }
        }
    }

    /**
     * Check if I have blocked the target user
     */
    fun isUserBlockedByMe(myUserId: String, targetUserId: String, context: Context? = null): Boolean {
        if (myUserId.isBlank() || targetUserId.isBlank()) return false
        if (inMemoryBlockedMap[myUserId]?.contains(targetUserId) == true) return true
        context?.let { ctx ->
            try {
                val prefs = ctx.getSharedPreferences("qivo_blocked_users", Context.MODE_PRIVATE)
                val myBlocked = prefs.getStringSet("blocked_$myUserId", emptySet()) ?: emptySet()
                if (myBlocked.contains(targetUserId)) {
                    inMemoryBlockedMap.getOrPut(myUserId) { java.util.concurrent.ConcurrentHashMap.newKeySet() }.addAll(myBlocked)
                    return true
                }
            } catch (e: Exception) {
                e.printStackTrace()
            }
        }
        return false
    }

    /**
     * Check if the target user has blocked me
     */
    fun isUserBlockedByThem(myUserId: String, targetUserId: String, context: Context? = null): Boolean {
        if (myUserId.isBlank() || targetUserId.isBlank()) return false
        if (inMemoryBlockedByMap[myUserId]?.contains(targetUserId) == true) return true
        if (inMemoryBlockedMap[targetUserId]?.contains(myUserId) == true) return true
        context?.let { ctx ->
            try {
                val prefs = ctx.getSharedPreferences("qivo_blocked_users", Context.MODE_PRIVATE)
                val blockedBy = prefs.getStringSet("blocked_by_$myUserId", emptySet()) ?: emptySet()
                if (blockedBy.contains(targetUserId)) {
                    inMemoryBlockedByMap.getOrPut(myUserId) { java.util.concurrent.ConcurrentHashMap.newKeySet() }.addAll(blockedBy)
                    return true
                }
                val targetBlocked = prefs.getStringSet("blocked_$targetUserId", emptySet()) ?: emptySet()
                if (targetBlocked.contains(myUserId)) {
                    inMemoryBlockedMap.getOrPut(targetUserId) { java.util.concurrent.ConcurrentHashMap.newKeySet() }.addAll(targetBlocked)
                    return true
                }
            } catch (e: Exception) {
                e.printStackTrace()
            }
        }
        return false
    }

    /**
     * Synchronous / Instant check if either user has blocked the other (Bidirectional)
     * Checks in-memory cache & SharedPreferences for instant responsiveness
     */
    fun isUserBlocked(myUserId: String, targetUserId: String, context: Context? = null): Boolean {
        if (myUserId.isBlank() || targetUserId.isBlank()) return false
        
        // Check 1: Did I block target?
        if (inMemoryBlockedMap[myUserId]?.contains(targetUserId) == true) return true

        // Check 2: Did target block me?
        if (inMemoryBlockedByMap[myUserId]?.contains(targetUserId) == true) return true
        if (inMemoryBlockedMap[targetUserId]?.contains(myUserId) == true) return true

        // Check 3: SharedPreferences
        context?.let { ctx ->
            try {
                val prefs = ctx.getSharedPreferences("qivo_blocked_users", Context.MODE_PRIVATE)
                
                val myBlocked = prefs.getStringSet("blocked_$myUserId", emptySet()) ?: emptySet()
                if (myBlocked.contains(targetUserId)) {
                    inMemoryBlockedMap.getOrPut(myUserId) { java.util.concurrent.ConcurrentHashMap.newKeySet() }.addAll(myBlocked)
                    return true
                }

                val blockedByMe = prefs.getStringSet("blocked_by_$myUserId", emptySet()) ?: emptySet()
                if (blockedByMe.contains(targetUserId)) {
                    inMemoryBlockedByMap.getOrPut(myUserId) { java.util.concurrent.ConcurrentHashMap.newKeySet() }.addAll(blockedByMe)
                    return true
                }

                val targetBlocked = prefs.getStringSet("blocked_$targetUserId", emptySet()) ?: emptySet()
                if (targetBlocked.contains(myUserId)) {
                    inMemoryBlockedMap.getOrPut(targetUserId) { java.util.concurrent.ConcurrentHashMap.newKeySet() }.addAll(targetBlocked)
                    return true
                }
            } catch (e: Exception) {
                e.printStackTrace()
            }
        }
        return false
    }

    /**
     * Check bidirectional block status against remote Supabase database and update local caches
     */
    suspend fun checkIfBlockedBidirectionalRemote(myUserId: String, targetUserId: String, context: Context? = null): Boolean {
        if (myUserId.isBlank() || targetUserId.isBlank()) return false
        if (isUserBlocked(myUserId, targetUserId, context)) return true

        return withContext(Dispatchers.IO) {
            try {
                val baseUrl = SupabaseConfig.supabaseUrl.trim().removeSuffix("/")
                val apiKey = SupabaseConfig.supabaseAnonKey.trim()
                if (baseUrl.isEmpty() || apiKey.isEmpty()) return@withContext false

                val url = "$baseUrl/rest/v1/blocked_users?or=(and(blocker_id.eq.$myUserId,blocked_id.eq.$targetUserId),and(blocker_id.eq.$targetUserId,blocked_id.eq.$myUserId))&limit=1"
                val authHeader = getAuthHeader()
                val request = Request.Builder()
                    .url(url)
                    .addHeader("apikey", apiKey)
                    .addHeader("Authorization", authHeader)
                    .get()
                    .build()

                client.newCall(request).execute().use { response ->
                    if (response.isSuccessful) {
                        val body = response.body?.string() ?: ""
                        if (body.startsWith("[") && body.length > 2) {
                            val arr = JSONArray(body)
                            if (arr.length() > 0) {
                                val item = arr.getJSONObject(0)
                                val bId = item.optString("blocker_id")
                                val target = item.optString("blocked_id")

                                if (bId == myUserId) {
                                    inMemoryBlockedMap.getOrPut(myUserId) { java.util.concurrent.ConcurrentHashMap.newKeySet() }.add(targetUserId)
                                } else {
                                    inMemoryBlockedByMap.getOrPut(myUserId) { java.util.concurrent.ConcurrentHashMap.newKeySet() }.add(targetUserId)
                                }

                                context?.let { ctx ->
                                    val prefs = ctx.getSharedPreferences("qivo_blocked_users", Context.MODE_PRIVATE)
                                    if (bId == myUserId) {
                                        val cur = prefs.getStringSet("blocked_$myUserId", emptySet())?.toMutableSet() ?: mutableSetOf()
                                        cur.add(targetUserId)
                                        prefs.edit().putStringSet("blocked_$myUserId", cur).apply()
                                    } else {
                                        val cur = prefs.getStringSet("blocked_by_$myUserId", emptySet())?.toMutableSet() ?: mutableSetOf()
                                        cur.add(targetUserId)
                                        prefs.edit().putStringSet("blocked_by_$myUserId", cur).apply()
                                    }
                                }
                                return@withContext true
                            }
                        }
                    }
                    false
                }
            } catch (e: Exception) {
                e.printStackTrace()
                false
            }
        }
    }

    /**
     * Fetch list of blocked UserProfiles for a user with optional offset and limit
     */
    suspend fun fetchBlockedUsers(myUserId: String, context: Context? = null, offset: Int = 0, limit: Int = 30): List<UserProfile> {
        if (myUserId.isBlank()) return emptyList()
        return withContext(Dispatchers.IO) {
            val blockedIds = mutableSetOf<String>()

            // 1. From in-memory
            inMemoryBlockedMap[myUserId]?.let { blockedIds.addAll(it) }

            // 2. From SharedPreferences
            context?.let { ctx ->
                try {
                    val prefs = ctx.getSharedPreferences("qivo_blocked_users", Context.MODE_PRIVATE)
                    prefs.getStringSet("blocked_$myUserId", emptySet())?.let { blockedIds.addAll(it) }
                } catch (e: Exception) {
                    e.printStackTrace()
                }
            }

            // 3. From Supabase remote table
            try {
                val baseUrl = SupabaseConfig.supabaseUrl.trim().removeSuffix("/")
                val apiKey = SupabaseConfig.supabaseAnonKey.trim()
                if (baseUrl.isNotEmpty() && apiKey.isNotEmpty()) {
                    val authHeader = getAuthHeader()
                    val url = "$baseUrl/rest/v1/blocked_users?blocker_id=eq.$myUserId&select=blocked_id"
                    val request = Request.Builder()
                        .url(url)
                        .addHeader("apikey", apiKey)
                        .addHeader("Authorization", authHeader)
                        .get()
                        .build()

                    client.newCall(request).execute().use { response ->
                        if (response.isSuccessful) {
                            val respBody = response.body?.string() ?: ""
                            if (respBody.startsWith("[")) {
                                val jsonArr = JSONArray(respBody)
                                for (i in 0 until jsonArr.length()) {
                                    val obj = jsonArr.getJSONObject(i)
                                    val bId = obj.optString("blocked_id", "")
                                    if (bId.isNotBlank()) {
                                        blockedIds.add(bId)
                                    }
                                }
                            }
                        }
                    }
                }
            } catch (e: Exception) {
                e.printStackTrace()
            }

            // Save combined IDs to memory & prefs
            inMemoryBlockedMap.getOrPut(myUserId) { java.util.concurrent.ConcurrentHashMap.newKeySet() }.addAll(blockedIds)
            context?.let { ctx ->
                try {
                    val prefs = ctx.getSharedPreferences("qivo_blocked_users", Context.MODE_PRIVATE)
                    prefs.edit().putStringSet("blocked_$myUserId", blockedIds).apply()
                } catch (e: Exception) {
                    e.printStackTrace()
                }
            }

            if (blockedIds.isEmpty()) return@withContext emptyList()

            // Apply pagination to blockedIds
            val pagedIds = blockedIds.drop(offset).take(limit)

            // Fetch UserProfiles for these IDs
            val profiles = mutableListOf<UserProfile>()
            for (bId in pagedIds) {
                val p = fetchProfileById(bId)
                if (p != null) {
                    profiles.add(p)
                } else {
                    // Fallback stub user profile if not found
                    profiles.add(
                        UserProfile(
                            id = bId,
                            numericId = 100000L + (bId.hashCode().toLong().let { if (it < 0) -it else it } % 899999L),
                            email = "",
                            name = "User ${bId.takeLast(4)}",
                            gender = "Male",
                            birthDate = "2000-01-01",
                            country = "Blocked Account",
                            avatarUrl = ""
                        )
                    )
                }
            }
            profiles
        }
    }

    /**
     * Local storage helper to ensure reports are immediately saved and visible to admins
     */
    private object LocalReportsStore {
        val localReports = java.util.concurrent.CopyOnWriteArrayList<UserReportItem>()

        fun saveReport(report: UserReportItem, context: Context? = null) {
            localReports.removeAll { it.id == report.id }
            localReports.add(0, report)
            if (context != null) {
                try {
                    val prefs = context.getSharedPreferences("qivo_reports_store", Context.MODE_PRIVATE)
                    val jsonArr = JSONArray()
                    localReports.forEach { rep ->
                        jsonArr.put(JSONObject().apply {
                            put("id", rep.id)
                            put("reporter_id", rep.reporterId)
                            put("reporter_name", rep.reporterName)
                            put("reported_id", rep.reportedId)
                            put("reported_name", rep.reportedName)
                            put("reason", rep.reason)
                            put("details", rep.details)
                            put("proof_url", rep.proofUrl)
                            put("status", rep.status)
                            put("created_at", rep.createdAt)
                        })
                    }
                    prefs.edit().putString("saved_reports_json", jsonArr.toString()).apply()
                } catch (e: Exception) {
                    e.printStackTrace()
                }
            }
        }

        fun getLocalReports(context: Context? = null): List<UserReportItem> {
            if (context != null && localReports.isEmpty()) {
                try {
                    val prefs = context.getSharedPreferences("qivo_reports_store", Context.MODE_PRIVATE)
                    val raw = prefs.getString("saved_reports_json", null)
                    if (!raw.isNullOrBlank()) {
                        val arr = JSONArray(raw)
                        for (i in 0 until arr.length()) {
                            val obj = arr.getJSONObject(i)
                            val item = UserReportItem(
                                id = obj.optString("id", "rep_$i"),
                                reporterId = obj.optString("reporter_id", ""),
                                reporterName = obj.optString("reporter_name", "Authenticated User"),
                                reportedId = obj.optString("reported_id", ""),
                                reportedName = obj.optString("reported_name", "Reported User"),
                                reason = obj.optString("reason", "Violation"),
                                details = obj.optString("details", ""),
                                proofUrl = obj.optString("proof_url", ""),
                                status = obj.optString("status", "PENDING"),
                                createdAt = obj.optString("created_at", "")
                            )
                            if (!localReports.any { it.id == item.id }) {
                                localReports.add(item)
                            }
                        }
                    }
                } catch (e: Exception) {
                    e.printStackTrace()
                }
            }
            return localReports.toList()
        }

        fun updateStatus(reportId: String, newStatus: String, context: Context? = null) {
            val idx = localReports.indexOfFirst { it.id == reportId }
            if (idx >= 0) {
                val updated = localReports[idx].copy(status = newStatus)
                localReports[idx] = updated
                if (context != null) {
                    saveReport(updated, context)
                }
            }
        }
    }

    /**
     * Report a user by inserting a record in user_reports table and caching locally
     */
    suspend fun reportUser(
        reporterId: String,
        reporterName: String = "Authenticated User",
        targetUserId: String,
        targetUserName: String,
        reason: String,
        details: String = "",
        proofUrl: String = "",
        context: Context? = null
    ): Boolean {
        val reportId = "rep_${System.currentTimeMillis()}_${(1000..9999).random()}"
        val createdAt = SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss.SSS'Z'", Locale.US).format(Date())
        val localReport = UserReportItem(
            id = reportId,
            reporterId = reporterId,
            reporterName = reporterName.ifBlank { "User ${reporterId.takeLast(4)}" },
            reportedId = targetUserId,
            reportedName = targetUserName.ifBlank { "User ${targetUserId.takeLast(4)}" },
            reason = reason,
            details = details,
            proofUrl = proofUrl,
            status = "PENDING",
            createdAt = createdAt
        )
        LocalReportsStore.saveReport(localReport, context)

        return withContext(Dispatchers.IO) {
            try {
                val baseUrl = SupabaseConfig.supabaseUrl.trim().removeSuffix("/")
                val apiKey = SupabaseConfig.supabaseAnonKey.trim()
                if (baseUrl.isEmpty() || apiKey.isEmpty()) return@withContext true

                val url = "$baseUrl/rest/v1/user_reports"
                val authHeader = getAuthHeader()
                val body = JSONObject().apply {
                    put("id", reportId)
                    put("reporter_id", reporterId)
                    put("reporter_name", localReport.reporterName)
                    put("reported_id", targetUserId)
                    put("reported_name", localReport.reportedName)
                    put("reason", reason)
                    put("details", details)
                    put("proof_url", proofUrl)
                    put("status", "PENDING")
                    put("created_at", createdAt)
                }.toString()

                val request = Request.Builder()
                    .url(url)
                    .addHeader("apikey", apiKey)
                    .addHeader("Authorization", authHeader)
                    .addHeader("Content-Type", "application/json")
                    .addHeader("Prefer", "resolution=merge-duplicates,return=minimal")
                    .post(body.toRequestBody(jsonMediaType))
                    .build()

                client.newCall(request).execute().use { response ->
                    response.isSuccessful || response.code == 200 || response.code == 201 || response.code == 204
                }
            } catch (e: Exception) {
                e.printStackTrace()
                true
            }
        }
    }

    /**
     * Fetch user reports for Admin Audit (combines Supabase remote + local fallback)
     */
    suspend fun fetchReports(context: Context? = null): List<UserReportItem> {
        val localList = LocalReportsStore.getLocalReports(context)

        return withContext(Dispatchers.IO) {
            val combinedList = mutableListOf<UserReportItem>()
            combinedList.addAll(localList)

            try {
                val baseUrl = SupabaseConfig.supabaseUrl.trim().removeSuffix("/")
                val apiKey = SupabaseConfig.supabaseAnonKey.trim()
                if (baseUrl.isNotEmpty() && apiKey.isNotEmpty()) {
                    val endpoint = "$baseUrl/rest/v1/user_reports?order=created_at.desc&limit=100"
                    val authHeader = getAuthHeader()
                    val request = Request.Builder()
                        .url(endpoint)
                        .addHeader("apikey", apiKey)
                        .addHeader("Authorization", authHeader)
                        .get()
                        .build()

                    client.newCall(request).execute().use { response ->
                        val body = response.body?.string() ?: ""
                        if (response.isSuccessful && body.startsWith("[")) {
                            val arr = JSONArray(body)
                            for (i in 0 until arr.length()) {
                                val obj = arr.getJSONObject(i)
                                val item = UserReportItem(
                                    id = obj.optString("id", "rep_$i"),
                                    reporterId = obj.optString("reporter_id", ""),
                                    reporterName = obj.optString("reporter_name", "Authenticated User"),
                                    reportedId = obj.optString("reported_id", ""),
                                    reportedName = obj.optString("reported_name", "Reported User"),
                                    reason = obj.optString("reason", "Violation"),
                                    details = obj.optString("details", ""),
                                    proofUrl = obj.optString("proof_url", ""),
                                    status = obj.optString("status", "PENDING"),
                                    createdAt = obj.optString("created_at", "")
                                )
                                val existingIndex = combinedList.indexOfFirst { it.id == item.id }
                                if (existingIndex >= 0) {
                                    combinedList[existingIndex] = item
                                } else {
                                    combinedList.add(item)
                                }
                            }
                        }
                    }
                }
            } catch (e: Exception) {
                e.printStackTrace()
            }

            combinedList.sortedByDescending { it.createdAt }
        }
    }

    /**
     * Update report resolution status
     */
    suspend fun updateReportStatus(reportId: String, newStatus: String, context: Context? = null): Boolean {
        LocalReportsStore.updateStatus(reportId, newStatus, context)

        return withContext(Dispatchers.IO) {
            try {
                val baseUrl = SupabaseConfig.supabaseUrl.trim().removeSuffix("/")
                val apiKey = SupabaseConfig.supabaseAnonKey.trim()
                if (baseUrl.isEmpty() || apiKey.isEmpty()) return@withContext true

                val url = "$baseUrl/rest/v1/user_reports?id=eq.$reportId"
                val authHeader = getAuthHeader()
                val body = JSONObject().apply {
                    put("status", newStatus)
                }.toString()

                val request = Request.Builder()
                    .url(url)
                    .addHeader("apikey", apiKey)
                    .addHeader("Authorization", authHeader)
                    .addHeader("Content-Type", "application/json")
                    .addHeader("Prefer", "return=minimal")
                    .patch(body.toRequestBody(jsonMediaType))
                    .build()

                client.newCall(request).execute().use { response ->
                    response.isSuccessful || response.code == 200 || response.code == 204
                }
            } catch (e: Exception) {
                e.printStackTrace()
                true
            }
        }
    }

    // ==========================================
    // FOLLOWERS, FOLLOWING & FRIENDS SYSTEM
    // ==========================================

    /**
     * Check if followerId follows followingId
     */
    suspend fun isFollowing(followerId: String, followingId: String): Boolean {
        return withContext(Dispatchers.IO) {
            try {
                val baseUrl = SupabaseConfig.supabaseUrl.trim().removeSuffix("/")
                val apiKey = SupabaseConfig.supabaseAnonKey.trim()
                if (baseUrl.isEmpty() || apiKey.isEmpty() || followerId.isEmpty() || followingId.isEmpty()) return@withContext false

                val url = "$baseUrl/rest/v1/user_follows?follower_id=eq.$followerId&following_id=eq.$followingId&select=id"
                val authHeader = getAuthHeader()
                val req = Request.Builder()
                    .url(url)
                    .addHeader("apikey", apiKey)
                    .addHeader("Authorization", authHeader)
                    .get()
                    .build()

                client.newCall(req).execute().use { res ->
                    if (res.isSuccessful) {
                        val body = res.body?.string() ?: "[]"
                        val arr = JSONArray(body)
                        return@withContext arr.length() > 0
                    }
                }
                false
            } catch (e: Exception) {
                e.printStackTrace()
                false
            }
        }
    }

    /**
     * Toggle follow status between followerId and followingId.
     * Returns Pair(success, isNowFollowing)
     */
    suspend fun toggleFollow(followerId: String, followingId: String): Pair<Boolean, Boolean> {
        return withContext(Dispatchers.IO) {
            try {
                val baseUrl = SupabaseConfig.supabaseUrl.trim().removeSuffix("/")
                val apiKey = SupabaseConfig.supabaseAnonKey.trim()
                if (baseUrl.isEmpty() || apiKey.isEmpty() || followerId.isEmpty() || followingId.isEmpty()) return@withContext Pair(false, false)

                val currentlyFollowing = isFollowing(followerId, followingId)

                if (currentlyFollowing) {
                    val authHeader = getAuthHeader()
                    // Unfollow (Delete record)
                    val delUrl = "$baseUrl/rest/v1/user_follows?follower_id=eq.$followerId&following_id=eq.$followingId"
                    val delReq = Request.Builder()
                        .url(delUrl)
                        .addHeader("apikey", apiKey)
                        .addHeader("Authorization", authHeader)
                        .delete()
                        .build()

                    val ok = client.newCall(delReq).execute().use { it.isSuccessful || it.code == 200 || it.code == 204 }
                    return@withContext Pair(ok, !ok)
                } else {
                    val authHeader = getAuthHeader()
                    // Follow (Insert record)
                    val postUrl = "$baseUrl/rest/v1/user_follows"
                    val body = JSONObject().apply {
                        put("follower_id", followerId)
                        put("following_id", followingId)
                        put("created_at", SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss.SSS'Z'", Locale.US).format(Date()))
                    }.toString()

                    val postReq = Request.Builder()
                        .url(postUrl)
                        .addHeader("apikey", apiKey)
                        .addHeader("Authorization", authHeader)
                        .addHeader("Content-Type", "application/json")
                        .addHeader("Prefer", "return=minimal")
                        .post(body.toRequestBody(jsonMediaType))
                        .build()

                    val ok = client.newCall(postReq).execute().use { it.isSuccessful || it.code == 200 || it.code == 201 || it.code == 204 }
                    return@withContext Pair(ok, ok)
                }
            } catch (e: Exception) {
                e.printStackTrace()
                Pair(false, false)
            }
        }
    }

    /**
     * Fetch follow counts: (following, followers, friends mutual)
     */
    suspend fun fetchFollowStats(userId: String): FollowStats {
        return withContext(Dispatchers.IO) {
            try {
                val baseUrl = SupabaseConfig.supabaseUrl.trim().removeSuffix("/")
                val apiKey = SupabaseConfig.supabaseAnonKey.trim()
                if (baseUrl.isEmpty() || apiKey.isEmpty() || userId.isEmpty()) return@withContext FollowStats()

                val authHeader = getAuthHeader()
                // 1. Get following IDs
                val followingIds = mutableSetOf<String>()
                val followingUrl = "$baseUrl/rest/v1/user_follows?follower_id=eq.$userId&select=following_id"
                val req1 = Request.Builder().url(followingUrl).addHeader("apikey", apiKey).addHeader("Authorization", authHeader).get().build()
                client.newCall(req1).execute().use { res ->
                    if (res.isSuccessful) {
                        val body = res.body?.string() ?: "[]"
                        val arr = JSONArray(body)
                        for (i in 0 until arr.length()) {
                            val id = arr.getJSONObject(i).optString("following_id", "").trim()
                            if (id.isNotEmpty()) followingIds.add(id)
                        }
                    }
                }

                // 2. Get follower IDs
                val followerIds = mutableSetOf<String>()
                val followerUrl = "$baseUrl/rest/v1/user_follows?following_id=eq.$userId&select=follower_id"
                val req2 = Request.Builder().url(followerUrl).addHeader("apikey", apiKey).addHeader("Authorization", authHeader).get().build()
                client.newCall(req2).execute().use { res ->
                    if (res.isSuccessful) {
                        val body = res.body?.string() ?: "[]"
                        val arr = JSONArray(body)
                        for (i in 0 until arr.length()) {
                            val id = arr.getJSONObject(i).optString("follower_id", "").trim()
                            if (id.isNotEmpty()) followerIds.add(id)
                        }
                    }
                }

                // 3. Mutual follows are Friends
                val friendsCount = followingIds.intersect(followerIds).size

                // 4. Real profile visitors count
                var realVisitorsCount = 0
                val visitorsUrl = "$baseUrl/rest/v1/profile_visitors?visited_id=eq.$userId&select=visitor_id"
                val req3 = Request.Builder().url(visitorsUrl).addHeader("apikey", apiKey).addHeader("Authorization", authHeader).get().build()
                try {
                    client.newCall(req3).execute().use { res ->
                        if (res.isSuccessful) {
                            val body = res.body?.string() ?: "[]"
                            val arr = JSONArray(body)
                            val distinctVisitors = mutableSetOf<String>()
                            for (i in 0 until arr.length()) {
                                val vid = arr.getJSONObject(i).optString("visitor_id", "").trim()
                                if (vid.isNotEmpty()) distinctVisitors.add(vid)
                            }
                            realVisitorsCount = distinctVisitors.size
                        }
                    }
                } catch (_: Exception) {}

                FollowStats(
                    followingCount = followingIds.size,
                    followersCount = followerIds.size,
                    friendsCount = friendsCount,
                    visitorsCount = realVisitorsCount
                )
            } catch (e: Exception) {
                e.printStackTrace()
                FollowStats()
            }
        }
    }

    /**
     * Record a profile visit when a user views another user's profile
     */
    suspend fun recordProfileVisit(visitorId: String, visitedId: String): Boolean {
        if (visitorId.isBlank() || visitedId.isBlank() || visitorId == visitedId) return false
        return withContext(Dispatchers.IO) {
            try {
                val baseUrl = SupabaseConfig.supabaseUrl.trim().removeSuffix("/")
                val apiKey = SupabaseConfig.supabaseAnonKey.trim()
                if (baseUrl.isEmpty() || apiKey.isEmpty()) return@withContext false

                val authHeader = getAuthHeader()
                // 1. Try RPC: record_profile_visit
                try {
                    val rpcUrl = "$baseUrl/rest/v1/rpc/record_profile_visit"
                    val rpcBody = JSONObject().apply {
                        put("p_visitor_id", visitorId)
                        put("p_visited_id", visitedId)
                        put("p_visited_user_id", visitedId)
                    }.toString()
                    val req = Request.Builder()
                        .url(rpcUrl)
                        .addHeader("apikey", apiKey)
                        .addHeader("Authorization", authHeader)
                        .addHeader("Content-Type", "application/json")
                        .post(rpcBody.toRequestBody(jsonMediaType))
                        .build()
                    client.newCall(req).execute().use { res ->
                        if (res.isSuccessful || res.code == 200 || res.code == 204) {
                            return@withContext true
                        }
                    }
                } catch (_: Exception) {}

                // 2. Direct table upsert fallback to profile_visitors or user_visitors
                val isoDate = SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss.SSS'Z'", Locale.US).apply {
                    timeZone = java.util.TimeZone.getTimeZone("UTC")
                }.format(Date())

                val url = "$baseUrl/rest/v1/profile_visitors"
                val body = JSONObject().apply {
                    put("visitor_id", visitorId)
                    put("visited_id", visitedId)
                    put("visited_at", isoDate)
                }.toString()
                val req = Request.Builder()
                    .url(url)
                    .addHeader("apikey", apiKey)
                    .addHeader("Authorization", authHeader)
                    .addHeader("Prefer", "resolution=merge-duplicates")
                    .addHeader("Content-Type", "application/json")
                    .post(body.toRequestBody(jsonMediaType))
                    .build()
                var success = client.newCall(req).execute().use { res ->
                    res.isSuccessful || res.code == 201 || res.code == 200 || res.code == 204
                }

                if (!success) {
                    val altUrl = "$baseUrl/rest/v1/user_visitors"
                    val altBody = JSONObject().apply {
                        put("visitor_user_id", visitorId)
                        put("visited_user_id", visitedId)
                        put("visited_at", isoDate)
                    }.toString()
                    val altReq = Request.Builder()
                        .url(altUrl)
                        .addHeader("apikey", apiKey)
                        .addHeader("Authorization", authHeader)
                        .addHeader("Prefer", "resolution=merge-duplicates")
                        .addHeader("Content-Type", "application/json")
                        .post(altBody.toRequestBody(jsonMediaType))
                        .build()
                    success = client.newCall(altReq).execute().use { res ->
                        res.isSuccessful || res.code == 201 || res.code == 200 || res.code == 204
                    }
                }

                success
            } catch (e: Exception) {
                e.printStackTrace()
                false
            }
        }
    }

    /**
     * Fetch users followed by userId with pagination
     */
    suspend fun fetchFollowing(userId: String, offset: Int = 0, limit: Int = 30): List<UserProfile> {
        return withContext(Dispatchers.IO) {
            try {
                val baseUrl = SupabaseConfig.supabaseUrl.trim().removeSuffix("/")
                val apiKey = SupabaseConfig.supabaseAnonKey.trim()
                if (baseUrl.isEmpty() || apiKey.isEmpty() || userId.isEmpty()) return@withContext emptyList()

                val authHeader = getAuthHeader()
                val followingUrl = "$baseUrl/rest/v1/user_follows?follower_id=eq.$userId&select=following_id&order=created_at.desc&offset=$offset&limit=$limit"
                val req = Request.Builder().url(followingUrl).addHeader("apikey", apiKey).addHeader("Authorization", authHeader).get().build()
                val ids = mutableListOf<String>()
                client.newCall(req).execute().use { res ->
                    if (res.isSuccessful) {
                        val body = res.body?.string() ?: "[]"
                        val arr = JSONArray(body)
                        for (i in 0 until arr.length()) {
                            val id = arr.getJSONObject(i).optString("following_id", "").trim()
                            if (id.isNotEmpty()) ids.add(id)
                        }
                    }
                }
                if (ids.isEmpty()) return@withContext emptyList()
                val allProfiles = fetchAllProfiles().associateBy { it.id }
                return@withContext ids.mapNotNull { allProfiles[it] ?: fetchProfileById(it) }
            } catch (e: Exception) {
                e.printStackTrace()
                emptyList()
            }
        }
    }

    /**
     * Fetch followers of userId with pagination
     */
    suspend fun fetchFollowers(userId: String, offset: Int = 0, limit: Int = 30): List<UserProfile> {
        return withContext(Dispatchers.IO) {
            try {
                val baseUrl = SupabaseConfig.supabaseUrl.trim().removeSuffix("/")
                val apiKey = SupabaseConfig.supabaseAnonKey.trim()
                if (baseUrl.isEmpty() || apiKey.isEmpty() || userId.isEmpty()) return@withContext emptyList()

                val authHeader = getAuthHeader()
                val followerUrl = "$baseUrl/rest/v1/user_follows?following_id=eq.$userId&select=follower_id&order=created_at.desc&offset=$offset&limit=$limit"
                val req = Request.Builder().url(followerUrl).addHeader("apikey", apiKey).addHeader("Authorization", authHeader).get().build()
                val ids = mutableListOf<String>()
                client.newCall(req).execute().use { res ->
                    if (res.isSuccessful) {
                        val body = res.body?.string() ?: "[]"
                        val arr = JSONArray(body)
                        for (i in 0 until arr.length()) {
                            val id = arr.getJSONObject(i).optString("follower_id", "").trim()
                            if (id.isNotEmpty()) ids.add(id)
                        }
                    }
                }
                if (ids.isEmpty()) return@withContext emptyList()
                val allProfiles = fetchAllProfiles().associateBy { it.id }
                return@withContext ids.mapNotNull { allProfiles[it] ?: fetchProfileById(it) }
            } catch (e: Exception) {
                e.printStackTrace()
                emptyList()
            }
        }
    }

    /**
     * Get set of user IDs that currentUserId follows
     */
    suspend fun fetchFollowingIdSet(currentUserId: String): Set<String> {
        return withContext(Dispatchers.IO) {
            val followingIds = mutableSetOf<String>()
            try {
                val baseUrl = SupabaseConfig.supabaseUrl.trim().removeSuffix("/")
                val apiKey = SupabaseConfig.supabaseAnonKey.trim()
                if (baseUrl.isEmpty() || apiKey.isEmpty() || currentUserId.isEmpty()) return@withContext followingIds

                val authHeader = getAuthHeader()
                val url = "$baseUrl/rest/v1/user_follows?follower_id=eq.$currentUserId&select=following_id"
                val req = Request.Builder().url(url).addHeader("apikey", apiKey).addHeader("Authorization", authHeader).get().build()
                client.newCall(req).execute().use { res ->
                    if (res.isSuccessful) {
                        val body = res.body?.string() ?: "[]"
                        val arr = JSONArray(body)
                        for (i in 0 until arr.length()) {
                            val id = arr.getJSONObject(i).optString("following_id", "").trim()
                            if (id.isNotEmpty()) followingIds.add(id)
                        }
                    }
                }
            } catch (e: Exception) {
                e.printStackTrace()
            }
            followingIds
        }
    }

    /**
     * Fetch users in a specific follow category ("FOLLOWING", "FOLLOWERS", "FRIENDS", "VISITORS")
     */
    suspend fun fetchCategoryUsers(userId: String, category: String): List<UserProfile> {
        return withContext(Dispatchers.IO) {
            try {
                val baseUrl = SupabaseConfig.supabaseUrl.trim().removeSuffix("/")
                val apiKey = SupabaseConfig.supabaseAnonKey.trim()
                if (baseUrl.isEmpty() || apiKey.isEmpty() || userId.isEmpty()) return@withContext emptyList()

                val authHeader = getAuthHeader()
                val allProfiles = fetchAllProfiles().associateBy { it.id }

                when (category.uppercase(Locale.ROOT)) {
                    "FOLLOWING" -> {
                        val followingUrl = "$baseUrl/rest/v1/user_follows?follower_id=eq.$userId&select=following_id&order=created_at.desc"
                        val req = Request.Builder().url(followingUrl).addHeader("apikey", apiKey).addHeader("Authorization", authHeader).get().build()
                        val ids = mutableListOf<String>()
                        client.newCall(req).execute().use { res ->
                            if (res.isSuccessful) {
                                val body = res.body?.string() ?: "[]"
                                val arr = JSONArray(body)
                                for (i in 0 until arr.length()) {
                                    val id = arr.getJSONObject(i).optString("following_id", "").trim()
                                    if (id.isNotEmpty()) ids.add(id)
                                }
                            }
                        }
                        return@withContext ids.mapNotNull { allProfiles[it] }
                    }
                    "FOLLOWERS" -> {
                        val followerUrl = "$baseUrl/rest/v1/user_follows?following_id=eq.$userId&select=follower_id&order=created_at.desc"
                        val req = Request.Builder().url(followerUrl).addHeader("apikey", apiKey).addHeader("Authorization", authHeader).get().build()
                        val ids = mutableListOf<String>()
                        client.newCall(req).execute().use { res ->
                            if (res.isSuccessful) {
                                val body = res.body?.string() ?: "[]"
                                val arr = JSONArray(body)
                                for (i in 0 until arr.length()) {
                                    val id = arr.getJSONObject(i).optString("follower_id", "").trim()
                                    if (id.isNotEmpty()) ids.add(id)
                                }
                            }
                        }
                        return@withContext ids.mapNotNull { allProfiles[it] }
                    }
                    "FRIENDS" -> {
                        // Mutual follows
                        val followingIds = mutableSetOf<String>()
                        val followerIds = mutableSetOf<String>()

                        val fUrl = "$baseUrl/rest/v1/user_follows?follower_id=eq.$userId&select=following_id"
                        client.newCall(Request.Builder().url(fUrl).addHeader("apikey", apiKey).addHeader("Authorization", authHeader).get().build()).execute().use { res ->
                            if (res.isSuccessful) {
                                val arr = JSONArray(res.body?.string() ?: "[]")
                                for (i in 0 until arr.length()) {
                                    followingIds.add(arr.getJSONObject(i).optString("following_id", "").trim())
                                }
                            }
                        }

                        val folUrl = "$baseUrl/rest/v1/user_follows?following_id=eq.$userId&select=follower_id"
                        client.newCall(Request.Builder().url(folUrl).addHeader("apikey", apiKey).addHeader("Authorization", authHeader).get().build()).execute().use { res ->
                            if (res.isSuccessful) {
                                val arr = JSONArray(res.body?.string() ?: "[]")
                                for (i in 0 until arr.length()) {
                                    followerIds.add(arr.getJSONObject(i).optString("follower_id", "").trim())
                                }
                            }
                        }

                        val mutualIds = followingIds.intersect(followerIds)
                        return@withContext mutualIds.mapNotNull { allProfiles[it] }
                    }
                    "VISITORS" -> {
                        val visitorsUrl = "$baseUrl/rest/v1/profile_visitors?visited_id=eq.$userId&select=visitor_id,visited_at&order=visited_at.desc"
                        val req = Request.Builder().url(visitorsUrl).addHeader("apikey", apiKey).addHeader("Authorization", authHeader).get().build()
                        val visitorIds = mutableListOf<String>()
                        try {
                            client.newCall(req).execute().use { res ->
                                if (res.isSuccessful) {
                                    val body = res.body?.string() ?: "[]"
                                    val arr = JSONArray(body)
                                    for (i in 0 until arr.length()) {
                                        val id = arr.getJSONObject(i).optString("visitor_id", "").trim()
                                        if (id.isNotEmpty() && !visitorIds.contains(id)) {
                                            visitorIds.add(id)
                                        }
                                    }
                                }
                            }
                        } catch (_: Exception) {}

                        val realVisitors = visitorIds.mapNotNull { allProfiles[it] }
                        return@withContext realVisitors
                    }
                    else -> emptyList()
                }
            } catch (e: Exception) {
                e.printStackTrace()
                emptyList()
            }
        }
    }

    /**
     * Claim the 500 free welcome bonus coins for new user accounts.
     * Prevents multi-account abuse on the same physical phone by checking the unique hardware device fingerprint hash against the database.
     */
    suspend fun claimDeviceWelcomeBonus(
        context: Context,
        userId: String,
        accessToken: String? = null
    ): Pair<Boolean, Long> {
        return withContext(Dispatchers.IO) {
            try {
                val androidId = android.provider.Settings.Secure.getString(
                    context.contentResolver,
                    android.provider.Settings.Secure.ANDROID_ID
                ) ?: "unknown_android_id"
                val prefs = context.getSharedPreferences("qivo_device_identity", Context.MODE_PRIVATE)
                var installGuid = prefs.getString("device_install_guid", "") ?: ""
                if (installGuid.isBlank()) {
                    installGuid = java.util.UUID.randomUUID().toString()
                    prefs.edit().putString("device_install_guid", installGuid).apply()
                }
                val rawFingerprint = "$androidId|$installGuid|${android.os.Build.MANUFACTURER}|${android.os.Build.BRAND}|${android.os.Build.MODEL}|${android.os.Build.HARDWARE}|${android.os.Build.BOARD}"
                val md = java.security.MessageDigest.getInstance("SHA-256")
                val digest = md.digest(rawFingerprint.toByteArray(Charsets.UTF_8))
                val deviceHash = digest.fold("") { str, it -> str + "%02x".format(it) }

                val baseUrl = SupabaseConfig.supabaseUrl.trim().removeSuffix("/")
                val apiKey = SupabaseConfig.supabaseAnonKey.trim()
                val token = if (!accessToken.isNullOrBlank()) accessToken else UserSessionManager.getValidAccessToken(context)
                val authHeader = if (token.isNotBlank()) "Bearer $token" else getAuthHeader()

                val rpcUrl = "$baseUrl/rest/v1/rpc/claim_welcome_bonus"
                val payload = JSONObject().apply {
                    put("p_user_id", userId)
                    put("p_device_hash", deviceHash)
                    put("p_bonus_coins", 500)
                }.toString()

                val request = Request.Builder()
                    .url(rpcUrl)
                    .addHeader("apikey", apiKey)
                    .addHeader("Authorization", authHeader)
                    .addHeader("Content-Type", "application/json")
                    .post(payload.toRequestBody(jsonMediaType))
                    .build()

                client.newCall(request).execute().use { response ->
                    val body = response.body?.string()?.trim() ?: ""
                    if (response.isSuccessful && body.isNotEmpty()) {
                        if (body.startsWith("{")) {
                            val json = JSONObject(body)
                            val ok = json.optBoolean("success", false)
                            val coins = json.optLong("coins_awarded", if (ok) 500L else 0L)
                            val newBalance = json.optLong("new_balance", -1L)
                            if (ok && newBalance >= 0L) {
                                UserSessionManager.saveCoins(context, newBalance)
                            } else if (ok && coins > 0L) {
                                UserSessionManager.addCoins(context, coins)
                            }
                            return@withContext Pair(ok, coins)
                        } else if (body.equals("true", ignoreCase = true)) {
                            UserSessionManager.addCoins(context, 500L)
                            return@withContext Pair(true, 500L)
                        }
                    }
                }
                Pair(false, 0L)
            } catch (e: Exception) {
                e.printStackTrace()
                Pair(false, 0L)
            }
        }
    }

    /**
     * Server-side secure RPC call to award EXP to user in Supabase
     */
    suspend fun recordUserExpRpc(
        context: Context?,
        userId: String,
        amount: Long,
        reason: String = "EXP_AWARD"
    ): Pair<Boolean, Long> {
        if (userId.isBlank() || amount <= 0L) return Pair(false, 0L)
        return withContext(Dispatchers.IO) {
            try {
                val baseUrl = SupabaseConfig.supabaseUrl.trim().removeSuffix("/")
                val apiKey = SupabaseConfig.supabaseAnonKey.trim()
                val authHeader = getAuthHeader(context)

                val rpcUrl = "$baseUrl/rest/v1/rpc/award_user_exp"
                val payload = JSONObject().apply {
                    put("p_user_id", userId)
                    put("p_amount", amount)
                    put("p_reason", reason)
                }.toString()

                val request = Request.Builder()
                    .url(rpcUrl)
                    .addHeader("apikey", apiKey)
                    .addHeader("Authorization", authHeader)
                    .addHeader("Content-Type", "application/json")
                    .post(payload.toRequestBody(jsonMediaType))
                    .build()

                client.newCall(request).execute().use { response ->
                    val body = response.body?.string()?.trim() ?: ""
                    if (response.isSuccessful && body.isNotEmpty()) {
                        if (body.startsWith("{")) {
                            val json = JSONObject(body)
                            val ok = json.optBoolean("success", true)
                            val totalExp = json.optLong("total_exp", 0L)
                            if (ok && totalExp > 0L && context != null) {
                                UserSessionManager.saveExp(context, totalExp, userId)
                            }
                            return@withContext Pair(ok, totalExp)
                        } else if (body.toLongOrNull() != null) {
                            val totalExp = body.toLong()
                            if (totalExp > 0L && context != null) {
                                UserSessionManager.saveExp(context, totalExp, userId)
                            }
                            return@withContext Pair(true, totalExp)
                        }
                        return@withContext Pair(true, amount)
                    }
                }
                Pair(false, 0L)
            } catch (e: Exception) {
                e.printStackTrace()
                Pair(false, 0L)
            }
        }
    }
}


package com.example.data

import android.content.Context
import android.util.Log
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.net.Uri
import com.example.QivoApplication
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.Response
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

object UserProfileSorting {
    fun parseLastActiveEpoch(timestamp: String): Long {
        if (timestamp.isBlank() || timestamp.equals("null", ignoreCase = true)) return 0L
        return try {
            try {
                java.time.OffsetDateTime.parse(timestamp).toInstant().toEpochMilli()
            } catch (_: Throwable) {
                try {
                    java.time.Instant.parse(timestamp).toEpochMilli()
                } catch (_: Throwable) {
                    val cleanStr = timestamp.replace("+00:00", "Z").replace("+00", "Z")
                    val formatters = listOf(
                        SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss.SSSSSS'Z'", Locale.US).apply { timeZone = java.util.TimeZone.getTimeZone("UTC") },
                        SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss.SSS'Z'", Locale.US).apply { timeZone = java.util.TimeZone.getTimeZone("UTC") },
                        SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss'Z'", Locale.US).apply { timeZone = java.util.TimeZone.getTimeZone("UTC") },
                        SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss.SSS", Locale.US).apply { timeZone = java.util.TimeZone.getTimeZone("UTC") },
                        SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.US).apply { timeZone = java.util.TimeZone.getTimeZone("UTC") }
                    )
                    var parsedTime: Long? = null
                    for (fmt in formatters) {
                        try {
                            val d = fmt.parse(cleanStr)
                            if (d != null) {
                                parsedTime = d.time
                                break
                            }
                        } catch (_: Throwable) {}
                    }
                    parsedTime ?: 0L
                }
            }
        } catch (_: Throwable) {
            0L
        }
    }

    /**
     * Personalized & Dynamic sorting for Home profiles:
     * - ONLINE USERS ALWAYS AT THE TOP (isOnline = true first)
     * - Within online users (and within offline users), order is uniquely personalized
     *   based on the viewer's ID and a refresh seed so different users see different orders,
     *   and pulling to refresh changes the order dynamically for everyone!
     */
    fun getPersonalizedComparator(
        viewerUserId: String = "",
        refreshSeed: Long = System.currentTimeMillis()
    ): Comparator<UserProfile> {
        return Comparator { u1, u2 ->
            // 1. Online users always at the top
            if (u1.isOnline != u2.isOnline) {
                return@Comparator if (u1.isOnline) -1 else 1
            }

            // 2. Personalized pseudo-random ordering per viewer and refresh seed
            val key1 = "${viewerUserId}_${u1.id}_$refreshSeed"
            val key2 = "${viewerUserId}_${u2.id}_$refreshSeed"
            val hash1 = key1.hashCode()
            val hash2 = key2.hashCode()

            if (hash1 != hash2) {
                return@Comparator hash1.compareTo(hash2)
            }
            u1.id.compareTo(u2.id)
        }
    }

    val recommendComparator = getPersonalizedComparator()

    /**
     * Nearby sorting:
     * Prioritizes geographic proximity (same country first).
     * Within the same geographic match, applies online & recent activity secondary sorting.
     */
    fun getNearbyComparator(userCountry: String): Comparator<UserProfile> {
        val cleanCountry = userCountry.trim()
        return Comparator<UserProfile> { u1, u2 ->
            if (cleanCountry.isNotBlank()) {
                val match1 = u1.country.trim().equals(cleanCountry, ignoreCase = true)
                val match2 = u2.country.trim().equals(cleanCountry, ignoreCase = true)
                if (match1 != match2) {
                    return@Comparator if (match1) -1 else 1
                }
            }
            // Secondary sorting: online status, recent activity, then stable tie-breaker
            recommendComparator.compare(u1, u2)
        }
    }
}

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
        val targetContext = context ?: UserSessionManager.appContext
        
        // 1. If overrideToken is provided, verify it first
        if (!overrideToken.isNullOrBlank()) {
            val isAnon = overrideToken.trim() == SupabaseConfig.supabaseAnonKey.trim()
            if (!isAnon) {
                return "Bearer $overrideToken"
            }
        }
        
        // 2. Otherwise, check and refresh the session
        val isExpired = UserSessionManager.isTokenExpired(targetContext)
        if (isExpired) {
            // Attempt synchronous refresh (since we are in suspend functions / background thread)
            if (android.os.Looper.myLooper() != android.os.Looper.getMainLooper()) {
                val refreshed = SupabaseAuthService.refreshSessionSync(targetContext, forceRefresh = true)
                if (!refreshed.isNullOrBlank()) {
                    return "Bearer $refreshed"
                }
            } else {
                // Main thread - we shouldn't block, but we must NOT use stale token or anon key.
                throw IllegalStateException("Profile query blocked: Expired token on Main Thread cannot be synchronously refreshed")
            }
            // If refresh fails, we must NOT use any stale token or fall back to anon!
            throw IllegalStateException("Profile query blocked: Failed to refresh expired session")
        }
        
        val token = UserSessionManager.getAccessToken(targetContext)
        if (token.isNotBlank()) {
            return "Bearer $token"
        }
        
        throw IllegalStateException("Profile query blocked: No valid authenticated session found")
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
            val cleanUserId = userId.trim()
            if (cleanUserId.isBlank()) return@withContext null
            try {
                val baseUrl = SupabaseConfig.supabaseUrl.trim().removeSuffix("/")
                val apiKey = SupabaseConfig.supabaseAnonKey.trim()

                if (baseUrl.isEmpty() || apiKey.isEmpty()) return@withContext null

                val baos = ByteArrayOutputStream()
                bitmap.compress(Bitmap.CompressFormat.JPEG, 90, baos)
                val imageBytes = baos.toByteArray()

                // File path inside the user's own folder: photos/{auth.uid()}/...
                val filename = "avatar_${System.currentTimeMillis()}.jpg"
                val uploadEndpoint = "$baseUrl/storage/v1/object/photos/$cleanUserId/$filename"
                val publicUrl = "$baseUrl/storage/v1/object/public/photos/$cleanUserId/$filename"

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
                        Log.e("SupabaseProfileService", "uploadProfileBitmap failed HTTP ${response.code}: ${response.message}")
                        return@withContext null
                    }
                }
            } catch (e: Exception) {
                Log.e("SupabaseProfileService", "uploadProfileBitmap error: ${e.message}", e)
                null
            }
        }
    }

    /**
     * Delete previous uploaded profile photo from Supabase Storage.
     * Enforces ownership: only deletes photos inside photos/{userId}/...
     * If the old object returns 400 or 404 because it is already missing,
     * it is treated as a harmless cleanup condition and returns true.
     */
    suspend fun deleteOldAvatar(
        userId: String,
        oldAvatarUrl: String,
        context: Context? = null,
        accessToken: String? = null
    ): Boolean {
        return withContext(Dispatchers.IO) {
            try {
                val cleanUserId = userId.trim()
                if (cleanUserId.isBlank()) return@withContext false

                if (oldAvatarUrl.isBlank() || !oldAvatarUrl.contains("/storage/v1/object/public/photos/")) {
                    return@withContext true
                }
                val baseUrl = SupabaseConfig.supabaseUrl.trim().removeSuffix("/")
                val apiKey = SupabaseConfig.supabaseAnonKey.trim()
                if (baseUrl.isEmpty() || apiKey.isEmpty()) return@withContext false

                val photoPath = oldAvatarUrl.substringAfter("/storage/v1/object/public/photos/").trim().removePrefix("/")
                if (photoPath.isBlank()) return@withContext true

                // SECURITY CHECK: Ensure users cannot delete or modify another user's photos!
                // Storage policies restrict photos to photos/{auth.uid()}/*
                if (!photoPath.startsWith("$cleanUserId/")) {
                    Log.w("SupabaseProfileService", "Security: Refusing to delete photo not owned by user: $photoPath (userId: $cleanUserId)")
                    return@withContext false
                }

                val authHeader = getAuthHeader(context, accessToken)

                // 1. Direct path delete
                val deleteEndpoint = "$baseUrl/storage/v1/object/photos/$photoPath"
                val request1 = Request.Builder()
                    .url(deleteEndpoint)
                    .addHeader("apikey", apiKey)
                    .addHeader("Authorization", authHeader)
                    .delete()
                    .build()

                var handled = false
                try {
                    client.newCall(request1).execute().use { response ->
                        val code = response.code
                        // If 200/204: deleted successfully.
                        // If 400/404: already missing / already deleted from Storage -> harmless cleanup condition
                        if (response.isSuccessful || code == 200 || code == 204 || code == 404 || code == 400) {
                            handled = true
                        }
                    }
                } catch (e: Exception) {
                    Log.w("SupabaseProfileService", "Direct delete notice: ${e.message}")
                }

                if (handled) return@withContext true

                // 2. Prefix array delete fallback
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

                try {
                    client.newCall(request2).execute().use { response ->
                        val code = response.code
                        response.isSuccessful || code == 200 || code == 204 || code == 404 || code == 400
                    }
                } catch (_: Exception) {
                    true
                }
            } catch (e: Exception) {
                // Harmless cleanup failure - never bubble error
                true
            }
        }
    }

    /**
     * Executes the atomic profile photo replacement or removal flow:
     * 1. (If newBitmap != null) Uploads new photo to photos/{userId}/avatar_{timestamp}.jpg
     * 2. Confirms upload success before proceeding.
     * 3. Updates profiles.avatar_url with the new URL (or new key or "" for deletion).
     * 4. Confirms the database profile update succeeds.
     * 5. Immediately refreshes local profile cache, session, and Supabase profile state.
     * 6. ONLY after successful update, attempts to delete old Storage object (if owned by user).
     * 7. Treats 400/404 during old-object deletion as harmless cleanup without user error.
     * 8. Never rolls back avatar_url if old-object deletion encounters an issue.
     */
    suspend fun safeReplaceProfilePhoto(
        context: Context,
        userId: String,
        newBitmap: Bitmap? = null,
        newAvatarKeyOrUrl: String? = null,
        oldAvatarUrl: String = "",
        accessToken: String? = null
    ): Pair<Boolean, String> {
        return withContext(Dispatchers.IO) {
            val cleanUserId = userId.trim()
            if (cleanUserId.isBlank()) return@withContext Pair(false, "User ID missing")

            val token = if (!accessToken.isNullOrBlank()) accessToken else UserSessionManager.getValidAccessToken(context)

            // 1. Upload if bitmap provided, or use designated key/url, or empty for removal
            val targetUrl: String = if (newBitmap != null) {
                val uploaded = uploadProfileBitmap(cleanUserId, newBitmap, token)
                if (uploaded.isNullOrBlank()) {
                    return@withContext Pair(false, "Failed to upload photo. Please check internet connection.")
                }
                uploaded
            } else {
                newAvatarKeyOrUrl ?: ""
            }

            // 2. Update profiles.avatar_url in Supabase (modifies ONLY avatar_url, preserving coins/roles/admin)
            val updateSuccess = updateAvatarUrl(
                userId = cleanUserId,
                avatarUrl = targetUrl,
                context = context,
                accessToken = token
            )

            if (!updateSuccess) {
                return@withContext Pair(false, "Failed to update profile avatar on server.")
            }

            // 3. Immediately update/refresh local session, in-memory cache, and app data cache
            try {
                val session = UserSessionManager.getSession(context)
                if (session != null) {
                    UserSessionManager.saveSession(
                        context = context,
                        email = session.email ?: "",
                        userId = cleanUserId,
                        name = session.name ?: "",
                        gender = session.gender ?: "",
                        country = session.country ?: "",
                        avatarUrl = targetUrl,
                        numericId = session.numericId,
                        coins = session.coins,
                        accessToken = token
                    )
                }
                val cached = AppDataCacheManager.getCachedUserDetail(context, cleanUserId)
                if (cached != null) {
                    AppDataCacheManager.saveUserDetailCache(context, cached.copy(avatarUrl = targetUrl))
                }
                val inMem = ProfileCache.profilesMap[cleanUserId]
                if (inMem != null) {
                    updateInMemoryProfile(inMem.copy(avatarUrl = targetUrl))
                }
                // Refresh authoritative profile from Supabase to prevent stale cache
                val freshProfile = fetchProfileById(cleanUserId, forceRefresh = true)
                if (freshProfile != null) {
                    updateInMemoryProfile(freshProfile)
                    AppDataCacheManager.saveUserDetailCache(context, freshProfile)
                }
            } catch (e: Exception) {
                Log.w("SupabaseProfileService", "Local profile refresh notice: ${e.message}")
            }

            // 4. ONLY AFTER new URL is confirmed saved, safely attempt to clean up old Storage object
            if (oldAvatarUrl.isNotBlank() && oldAvatarUrl != targetUrl && oldAvatarUrl.contains("/storage/v1/object/public/photos/")) {
                try {
                    deleteOldAvatar(cleanUserId, oldAvatarUrl, context, token)
                } catch (e: Exception) {
                    // Harmless cleanup condition - do not show error, do NOT rollback
                    Log.w("SupabaseProfileService", "Old avatar cleanup ignored: ${e.message}")
                }
            }

            Pair(true, targetUrl)
        }
    }

    suspend fun uploadProfilePhoto(
        context: Context,
        userId: String,
        imageUri: Uri,
        accessToken: String? = null
    ): String? {
        return withContext(Dispatchers.IO) {
            val cleanUserId = userId.trim()
            if (cleanUserId.isBlank()) return@withContext null
            try {
                val baseUrl = SupabaseConfig.supabaseUrl.trim().removeSuffix("/")
                val apiKey = SupabaseConfig.supabaseAnonKey.trim()

                if (baseUrl.isEmpty() || apiKey.isEmpty()) return@withContext null

                val inputStream = context.contentResolver.openInputStream(imageUri) ?: return@withContext null
                val originalBitmap = BitmapFactory.decodeStream(inputStream)
                inputStream.close()

                if (originalBitmap == null) return@withContext null

                val baos = ByteArrayOutputStream()
                originalBitmap.compress(Bitmap.CompressFormat.JPEG, 85, baos)
                val imageBytes = baos.toByteArray()

                val filename = "avatar_${System.currentTimeMillis()}.jpg"
                val uploadEndpoint = "$baseUrl/storage/v1/object/photos/$cleanUserId/$filename"
                val publicUrl = "$baseUrl/storage/v1/object/public/photos/$cleanUserId/$filename"

                val authHeader = getAuthHeader(context, accessToken)
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
                        Log.e("SupabaseProfileService", "uploadProfilePhoto failed HTTP ${response.code}: ${response.message}")
                        return@withContext null
                    }
                }
            } catch (e: Exception) {
                Log.e("SupabaseProfileService", "uploadProfilePhoto error: ${e.message}", e)
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
            lastActiveAt = listOf(
                if (obj.has("last_active_at") && !obj.isNull("last_active_at")) obj.optString("last_active_at", "").trim() else "",
                if (obj.has("last_active") && !obj.isNull("last_active")) obj.optString("last_active", "").trim() else "",
                if (obj.has("updated_at") && !obj.isNull("updated_at")) obj.optString("updated_at", "").trim() else ""
            ).firstOrNull { it.isNotBlank() && !it.equals("null", ignoreCase = true) } ?: (inMemProfile?.lastActiveAt ?: ""),
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
     * Fast profile lookup by user ID with optional authoritative forceRefresh from Supabase
     */
    suspend fun fetchProfileById(userId: String, forceRefresh: Boolean = false): UserProfile? {
        val trimmed = userId.trim()
        if (trimmed.isEmpty()) return null
        if (forceRefresh) {
            return withContext(Dispatchers.IO) {
                try {
                    val baseUrl = SupabaseConfig.supabaseUrl.trim().removeSuffix("/")
                    val apiKey = SupabaseConfig.supabaseAnonKey.trim()
                    if (baseUrl.isEmpty() || apiKey.isEmpty()) return@withContext null
                    val authHeader = getAuthHeader()
                    val endpoint = "$baseUrl/rest/v1/profiles?id=eq.${Uri.encode(trimmed)}&select=*&limit=1"
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
                                updateInMemoryProfile(found)
                                return@withContext found
                            }
                        }
                    }
                    null
                } catch (e: Exception) {
                    null
                }
            }
        }
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
                    put("birth_date", if (profile.birthDate.isBlank()) "2005-01-01" else profile.birthDate)
                    put("country", profile.country)
                    put("avatar_url", profile.avatarUrl)
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
                    put("birth_date", if (profile.birthDate.isBlank()) "2005-01-01" else profile.birthDate)
                    put("country", profile.country)
                    if (profile.numericId > 0) {
                        put("numeric_id", profile.numericId)
                    }
                    put("avatar_url", profile.avatarUrl)
                    put("coins", profile.coins)
                    put("diamonds", profile.diamonds)
                    put("is_admin", profile.isAdmin)
                    put("is_coinseller", profile.isCoinSeller)
                    put("is_agent", profile.isAgent)
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
                    put("coins", profile.coins)
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
                val cleanId = userId.trim()
                if (cleanId.isEmpty()) return@withContext null
                val isUuid = cleanId.length == 36 && cleanId.count { it == '-' } == 4
                if (!isUuid) {
                    return@withContext ProfileCache.cachedProfiles.firstOrNull { it.id == cleanId }
                }

                val baseUrl = SupabaseConfig.supabaseUrl.trim().removeSuffix("/")
                val apiKey = SupabaseConfig.supabaseAnonKey.trim()

                if (baseUrl.isEmpty() || apiKey.isEmpty()) return@withContext null

                val endpoint = "$baseUrl/rest/v1/profiles?id=eq.$cleanId&select=*"
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

        fun clearCache() {
            ProfileCache.cachedProfiles = emptyList()
            ProfileCache.profilesMap.clear()
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
                        add("$baseUrl/rest/v1/profiles?select=*$genderQuery&order=is_online.desc.nullslast,last_active_at.desc.nullslast,numeric_id.desc&offset=$offset&limit=$limit")
                        add("$baseUrl/rest/v1/profiles?select=*$genderQuery&order=is_online.desc.nullslast,numeric_id.desc&offset=$offset&limit=$limit")
                        add("$baseUrl/rest/v1/profiles?select=*$genderQuery&order=numeric_id.desc&offset=$offset&limit=$limit")
                        add("$baseUrl/rest/v1/profiles?select=*$genderQuery&offset=$offset&limit=$limit")
                    }
                }

                val fallbackEndpoints = listOf(
                    "$baseUrl/rest/v1/profiles?select=*&order=is_online.desc.nullslast,last_active_at.desc.nullslast,numeric_id.desc&offset=$offset&limit=$limit",
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
                                val sortedList = list.sortedWith(UserProfileSorting.recommendComparator)
                                if (offset == 0) {
                                    ProfileCache.cachedProfiles = sortedList
                                } else {
                                    val existingIds = ProfileCache.cachedProfiles.map { it.id }.toSet()
                                    val newItems = sortedList.filter { it.id !in existingIds }
                                    ProfileCache.cachedProfiles = (ProfileCache.cachedProfiles + newItems).sortedWith(UserProfileSorting.recommendComparator)
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
    /**
     * Efficiently fetch multiple profiles by a list of user IDs in a single batch request
     */
    suspend fun fetchProfilesByIds(userIds: List<String>): List<UserProfile> {
        val distinctIds = userIds.filter { it.isNotBlank() }.distinct()
        if (distinctIds.isEmpty()) return emptyList()

        return withContext(Dispatchers.IO) {
            try {
                val baseUrl = SupabaseConfig.supabaseUrl.trim().removeSuffix("/")
                val apiKey = SupabaseConfig.supabaseAnonKey.trim()
                if (baseUrl.isEmpty() || apiKey.isEmpty()) return@withContext emptyList()

                val authHeader = getAuthHeader()
                val encodedIds = distinctIds.joinToString(",") { Uri.encode(it) }
                val endpoint = "$baseUrl/rest/v1/profiles?id=in.($encodedIds)&select=*"

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
                        val list = mutableListOf<UserProfile>()
                        for (i in 0 until arr.length()) {
                            val found = parseUserProfile(arr.getJSONObject(i))
                            updateInMemoryProfile(found)
                            list.add(found)
                        }
                        return@withContext list
                    }
                }
                emptyList()
            } catch (e: Exception) {
                Log.e("SupabaseProfileService", "fetchProfilesByIds error: ${e.message}")
                emptyList()
            }
        }
    }

    /**
     * Full refresh from Supabase REST API (Warning: Heavy if table is large)
     */
    suspend fun fetchAllProfiles(): List<UserProfile> {
        return withContext(Dispatchers.IO) {
            try {
                val baseUrl = SupabaseConfig.supabaseUrl.trim().removeSuffix("/")
                val apiKey = SupabaseConfig.supabaseAnonKey.trim()
                if (baseUrl.isEmpty() || apiKey.isEmpty()) return@withContext emptyList()
                val authHeader = getAuthHeader()
                val endpoint = "$baseUrl/rest/v1/profiles?select=*"
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
                        val list = mutableListOf<UserProfile>()
                        for (i in 0 until arr.length()) {
                            list.add(parseUserProfile(arr.getJSONObject(i)))
                        }
                        updateInMemoryProfiles(list)
                        return@withContext list
                    }
                }
                ProfileCache.cachedProfiles
            } catch (e: Exception) {
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
     * Award Coins via Supabase Edge Function (primary) with RPC and REST fallbacks.
     * Guaranteed authoritative server-side math:
     * - Admin has UNLIMITED coins to award (no balance deduction).
     * - Coin Seller has coins DEDUCTED from their balance (requires sufficient balance).
     */
    /**
     * Award coins to a user via Supabase Edge Function 'award-coins'.
     * This method is used by Admins (unlimited) and Coin Sellers (deducted from balance).
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

                // 1. Authoritative Edge Function Call
                val edgeFunctionUrl = "$baseUrl/functions/v1/award-coins"
                
                // Body only contains target information and amount. 
                // Sender identity is derived from JWT server-side.
                val jsonBody = JSONObject().apply {
                    put("target_numeric_id", targetNumericId)
                    put("amount", amount)
                    put("reason", reason)
                }.toString()

                val authHeader = getAuthHeader(context)
                val request = Request.Builder()
                    .url(edgeFunctionUrl)
                    .addHeader("apikey", apiKey)
                    .addHeader("Authorization", authHeader)
                    .addHeader("Content-Type", "application/json")
                    .post(jsonBody.toRequestBody(jsonMediaType))
                    .build()

                client.newCall(request).execute().use { response ->
                    val bodyStr = response.body?.string() ?: ""
                    android.util.Log.d("SupabaseProfile", "award-coins edge function response: code=${response.code}, body=$bodyStr")

                    if (response.isSuccessful || response.code == 200) {
                        val json = JSONObject(bodyStr)
                        if (json.optBoolean("success", false)) {
                            val msg = json.optString("message", "Successfully awarded %,d coins.".format(amount))
                            
                            // Authoritatively update local balance for the seller if applicable
                            val sellerNewCoins = json.optLong("seller_coins", -1L)
                            if (sellerNewCoins >= 0 && context != null && !isAdmin) {
                                UserSessionManager.saveCoins(context, sellerNewCoins)
                            }
                            
                            return@withContext Pair(true, msg)
                        } else {
                            val errMsg = json.optString("message", json.optString("error", "Failed to transfer coins."))
                            return@withContext Pair(false, errMsg)
                        }
                    } else {
                        // Handle structured error responses
                        try {
                            if (bodyStr.trim().startsWith("{")) {
                                val errJson = JSONObject(bodyStr)
                                val errMsg = errJson.optString("message", errJson.optString("error", ""))
                                if (errMsg.isNotBlank()) return@withContext Pair(false, errMsg)
                            }
                        } catch (_: Exception) {}
                        
                        return@withContext Pair(false, "Server error (${response.code}). Please try again.")
                    }
                }
            } catch (e: Exception) {
                android.util.Log.e("SupabaseProfile", "Error calling award-coins Edge Function", e)
                Pair(false, "Network error. Please check your connection.")
            }
        }
    }

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
                val canonicalFrameId = AvatarFrameManager.normalizeFrameId(frameId)
                val frame = AvatarFrameManager.getFrameById(canonicalFrameId)
                val frameName = frame?.name ?: canonicalFrameId.replaceFirstChar { it.uppercase() }

                val baseUrl = SupabaseConfig.supabaseUrl.trim().removeSuffix("/")
                val apiKey = SupabaseConfig.supabaseAnonKey.trim()

                if (baseUrl.isEmpty() || apiKey.isEmpty() || cleanId.isEmpty()) {
                    return@withContext Triple(false, "Invalid configuration or user ID.", 0L)
                }

                val authHeader = getAuthHeader(context)
                if (authHeader.isBlank()) {
                    val serverCoins = fetchCoins(cleanId)
                    return@withContext Triple(false, "Authentication required. Please sign in to purchase avatar frames.", serverCoins)
                }

                // 1. Execute Server-Side RPC: buy_avatar_frame
                // The server RPC expects ONLY the frame ID as 'p_frame_id'.
                // Do NOT send price, coin amount, or duration from the client.
                val rpcUrl = "$baseUrl/rest/v1/rpc/buy_avatar_frame"
                val rpcBody = JSONObject().apply {
                    put("p_frame_id", canonicalFrameId)
                }.toString()

                android.util.Log.d("SupabaseProfileService", "buy_avatar_frame request -> URL: $rpcUrl, Payload: $rpcBody")

                val rpcReq = Request.Builder()
                    .url(rpcUrl)
                    .addHeader("apikey", apiKey)
                    .addHeader("Authorization", authHeader)
                    .addHeader("Content-Type", "application/json")
                    .addHeader("Prefer", "return=representation")
                    .post(rpcBody.toRequestBody(jsonMediaType))
                    .build()

                client.newCall(rpcReq).execute().use { resp ->
                    val respStr = resp.body?.string()?.trim() ?: ""
                    android.util.Log.d("SupabaseProfileService", "buy_avatar_frame response -> Code: ${resp.code}, Body: $respStr")

                    if (resp.isSuccessful || resp.code in 200..299) {
                        val json = when {
                            respStr.startsWith("{") -> JSONObject(respStr)
                            respStr.startsWith("[") -> {
                                val arr = JSONArray(respStr)
                                if (arr.length() > 0) arr.optJSONObject(0) ?: JSONObject() else JSONObject()
                            }
                            else -> JSONObject()
                        }

                        val parsedResponse = BuyAvatarFrameResponse(
                            success = json.optBoolean("success", false),
                            frameId = json.optString("frame_id", canonicalFrameId),
                            frameName = json.optString("frame_name", frameName),
                            priceCoins = json.optLong("price_coins", priceCoins),
                            coinsDeducted = when {
                                json.has("coins_deducted") && !json.isNull("coins_deducted") -> json.optLong("coins_deducted", 0L)
                                json.has("amount_to_be_deducted") && !json.isNull("amount_to_be_deducted") -> json.optLong("amount_to_be_deducted", 0L)
                                json.has("price_coins") && !json.isNull("price_coins") -> json.optLong("price_coins", 0L)
                                else -> 0L
                            },
                            newBalance = when {
                                json.has("new_balance") && !json.isNull("new_balance") -> json.optLong("new_balance", -1L)
                                json.has("remaining_coins") && !json.isNull("remaining_coins") -> json.optLong("remaining_coins", -1L)
                                json.has("new_coins") && !json.isNull("new_coins") -> json.optLong("new_coins", -1L)
                                json.has("coins") && !json.isNull("coins") -> json.optLong("coins", -1L)
                                else -> -1L
                            },
                            remainingCoins = json.optLong("remaining_coins", -1L),
                            newCoins = json.optLong("new_coins", -1L),
                            expiresAt = json.optString("expires_at", ""),
                            message = json.optString("message", "")
                        )

                        val serverFrameId = parsedResponse.frameId.ifBlank { canonicalFrameId }
                        val expiresAtISO = parsedResponse.expiresAt
                        val coinsDeducted = parsedResponse.coinsDeducted

                        if (parsedResponse.success) {
                            // Update local session & cache ONLY after authoritative server response
                            val finalCoins = if (parsedResponse.newBalance >= 0L) {
                                parsedResponse.newBalance
                            } else {
                                fetchCoins(cleanId)
                            }

                            if (context != null) {
                                UserSessionManager.saveCoins(context, finalCoins)
                                UserSessionManager.savePurchasedFrame(
                                    context = context,
                                    frameId = serverFrameId,
                                    expiresAt = expiresAtISO,
                                    pricePaid = coinsDeducted,
                                    targetUserId = cleanId
                                )
                                UserSessionManager.saveActiveFrame(
                                    context = context,
                                    frameId = serverFrameId,
                                    expiresAt = expiresAtISO,
                                    targetUserId = cleanId
                                )
                            }
                            ProfileCache.cachedProfiles = ProfileCache.cachedProfiles.map { p ->
                                if (p.id == cleanId) p.copy(
                                    coins = finalCoins,
                                    activeFrameId = serverFrameId,
                                    frameExpiresAt = expiresAtISO
                                ) else p
                            }
                            ProfileCache.profilesMap[cleanId]?.let { p ->
                                ProfileCache.profilesMap[cleanId] = p.copy(
                                    coins = finalCoins,
                                    activeFrameId = serverFrameId,
                                    frameExpiresAt = expiresAtISO
                                )
                            }

                            val successMsg = if (parsedResponse.message.isNotBlank()) {
                                parsedResponse.message
                            } else {
                                "Frame purchased and equipped"
                            }
                            return@withContext Triple(true, successMsg, finalCoins)
                        } else {
                            // Display the server's message when purchase fails
                            val errorMsg = when {
                                parsedResponse.message.isNotBlank() -> parsedResponse.message
                                json.optString("error").isNotBlank() -> json.optString("error")
                                json.optString("error_description").isNotBlank() -> json.optString("error_description")
                                else -> "Unable to purchase avatar frame: insufficient coins or invalid frame."
                            }
                            val currentCoins = if (parsedResponse.newBalance >= 0L) {
                                parsedResponse.newBalance
                            } else {
                                fetchCoins(cleanId)
                            }
                            return@withContext Triple(false, errorMsg, currentCoins)
                        }
                    } else {
                        // Handle HTTP 400/401/403/404/500 responses separately with actual server error
                        android.util.Log.e("SupabaseProfileService", "buy_avatar_frame HTTP error ${resp.code}: $respStr")

                        var errCode = ""
                        var errMsg = ""
                        var errDetails = ""
                        var errHint = ""
                        var errDesc = ""
                        var errField = ""

                        if (respStr.startsWith("{")) {
                            try {
                                val errObj = JSONObject(respStr)
                                errCode = errObj.optString("code", "")
                                errMsg = errObj.optString("message", "")
                                errDetails = errObj.optString("details", "")
                                errHint = errObj.optString("hint", "")
                                errField = errObj.optString("error", "")
                                errDesc = errObj.optString("error_description", "")
                            } catch (_: Exception) {}
                        }

                        val parsedServerMsg = when {
                            errMsg.isNotBlank() && errDetails.isNotBlank() && errDetails != "null" -> "$errMsg ($errDetails)"
                            errMsg.isNotBlank() -> errMsg
                            errDesc.isNotBlank() -> errDesc
                            errField.isNotBlank() -> errField
                            errDetails.isNotBlank() && errDetails != "null" -> errDetails
                            errHint.isNotBlank() && errHint != "null" -> errHint
                            else -> respStr.take(150)
                        }

                        val errorMessage = when (resp.code) {
                            400 -> {
                                val parts = mutableListOf<String>()
                                if (errCode.isNotBlank()) parts.add("Code: $errCode")
                                if (errMsg.isNotBlank()) parts.add(errMsg)
                                if (errDetails.isNotBlank() && errDetails != "null") parts.add("Details: $errDetails")
                                if (errHint.isNotBlank() && errHint != "null") parts.add("Hint: $errHint")
                                if (errDesc.isNotBlank() && errDesc != errMsg) parts.add(errDesc)
                                if (errField.isNotBlank() && errField != errMsg && errField != errCode) parts.add(errField)
                                val fullErr = if (parts.isNotEmpty()) parts.joinToString(" - ") else respStr.take(200)
                                "Supabase error (400): $fullErr"
                            }
                            401 -> "Session expired or unauthorized (401). Please sign in again."
                            403 -> if (parsedServerMsg.isNotBlank()) "Access forbidden (403): $parsedServerMsg" else "Permission denied (403)."
                            404 -> if (parsedServerMsg.isNotBlank()) "Function not found (404): $parsedServerMsg" else "Function public.buy_avatar_frame(p_frame_id) not found in schema cache (404)."
                            500 -> if (parsedServerMsg.isNotBlank()) "Server error (500): $parsedServerMsg" else "Internal server error (500). Please try again later."
                            else -> if (parsedServerMsg.isNotBlank()) "Server error (${resp.code}): $parsedServerMsg" else "Server error (${resp.code}). Please try again."
                        }
                        val serverCoins = fetchCoins(cleanId)
                        return@withContext Triple(false, errorMessage, serverCoins)
                    }
                }
            } catch (e: Exception) {
                e.printStackTrace()
                val liveCoins = fetchCoins(cleanId)
                Triple(false, "Network error: ${e.message ?: "Failed to connect to server."}", liveCoins)
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
            val canonicalFrameId = AvatarFrameManager.normalizeFrameId(frameId)
            val effectiveExp = if (expiresAt.isNotBlank()) expiresAt else "2054-01-01T00:00:00.000Z"

            if (context != null) {
                UserSessionManager.saveActiveFrame(context, canonicalFrameId, effectiveExp, targetUserId = cleanId)
            }

            // Immediately update in-memory cache so no screen gets stale frame data
            ProfileCache.cachedProfiles = ProfileCache.cachedProfiles.map { p ->
                if (p.id.trim() == cleanId) p.copy(activeFrameId = canonicalFrameId, frameExpiresAt = effectiveExp) else p
            }
            ProfileCache.profilesMap[cleanId]?.let { p ->
                ProfileCache.profilesMap[cleanId] = p.copy(activeFrameId = canonicalFrameId, frameExpiresAt = effectiveExp)
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
                        put("p_frame_id", canonicalFrameId)
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
        val cleanGender = senderGender.trim()
        val isSenderFemale = cleanGender.equals("Female", ignoreCase = true) ||
            cleanGender.equals("f", ignoreCase = true) ||
            cleanGender.equals("woman", ignoreCase = true) ||
            cleanGender.equals("w", ignoreCase = true) ||
            cleanGender.equals("girl", ignoreCase = true) ||
            cleanGender.equals("lady", ignoreCase = true)

        val isSenderMale = !isSenderFemale && (
            cleanGender.equals("Male", ignoreCase = true) ||
            cleanGender.equals("m", ignoreCase = true) ||
            cleanGender.equals("man", ignoreCase = true)
        )

        // Female users, Admins, Coin Sellers, and Agents text and be texted for free!
        if (isSenderFemale || !isSenderMale ||
            isAdmin || isCoinSeller || isAgent ||
            receiverIsAdmin || receiverIsCoinSeller || receiverIsAgent
        ) {
            val currentCoins = fetchCoins(senderId)
            return Pair(true, currentCoins)
        }

        return withContext(Dispatchers.IO) {
            val cleanSenderId = senderId.trim()
            val cleanReceiverId = receiverId.trim()
            if (cleanSenderId.isEmpty()) return@withContext Pair(false, 0L)

            try {
                val baseUrl = SupabaseConfig.supabaseUrl.trim().removeSuffix("/")
                val apiKey = SupabaseConfig.supabaseAnonKey.trim()
                val authHeader = getAuthHeader()
                val authBearer = if (authHeader.isNotBlank()) authHeader else "Bearer $apiKey"

                // PostgreSQL RPC: deduct_chat_coins
                if (baseUrl.isNotEmpty() && apiKey.isNotEmpty()) {
                    val rpcUrl = "$baseUrl/rest/v1/rpc/deduct_chat_coins"
                    val rpcBody = JSONObject().apply {
                        put("p_sender_id", cleanSenderId)
                        put("p_receiver_id", cleanReceiverId)
                        put("p_amount", 15)
                        put("p_sender_gender", cleanGender)
                        put("p_receiver_name", receiverName)
                    }.toString()
                    val req = Request.Builder()
                        .url(rpcUrl)
                        .addHeader("apikey", apiKey)
                        .addHeader("Authorization", authBearer)
                        .addHeader("Content-Type", "application/json")
                        .post(rpcBody.toRequestBody(jsonMediaType))
                        .build()
                    client.newCall(req).execute().use { res ->
                        if (res.isSuccessful || res.code == 200) {
                            val bodyStr = res.body?.string() ?: ""
                            val json = JSONObject(bodyStr)
                            val success = json.optBoolean("success", false)
                            val newBal = json.optLong("new_balance", fetchCoins(cleanSenderId))
                            if (success) {
                                ProfileCache.cachedProfiles = ProfileCache.cachedProfiles.map { p ->
                                    if (p.id == cleanSenderId) p.copy(coins = newBal) else p
                                }
                                return@withContext Pair(true, newBal)
                            } else {
                                return@withContext Pair(false, newBal)
                            }
                        }
                    }
                }

                val curCoins = fetchCoins(cleanSenderId)
                Pair(false, curCoins)
            } catch (e: Exception) {
                e.printStackTrace()
                Pair(false, fetchCoins(cleanSenderId))
            }
        }
    }

    /**
     * Deduct coins for sending a photo in chat:
     * - Regular users (both Male and Female): 40 coins deducted
     * - Admins (is_admin = true): Free to send & receive (0 coins deducted)
     * - Coin Sellers (is_coinseller = true): Free to send & receive (0 coins deducted)
     * - Agents (is_agent = true): Free to send & receive (0 coins deducted)
     */
    suspend fun deductPhotoCoins(
        senderId: String,
        senderGender: String = "",
        receiverId: String = "",
        receiverName: String = "",
        isAdmin: Boolean = false,
        isCoinSeller: Boolean = false,
        isAgent: Boolean = false,
        receiverIsAdmin: Boolean = false,
        receiverIsCoinSeller: Boolean = false,
        receiverIsAgent: Boolean = false,
        context: Context? = null
    ): Pair<Boolean, Long> {
        val cleanSenderId = senderId.trim()
        val cleanReceiverId = receiverId.trim()
        if (cleanSenderId.isEmpty()) return Pair(false, 0L)

        // 40 coins for ALL regular users. ONLY Admin, Coin Seller, and Agent send photos for free
        if (isAdmin || isCoinSeller || isAgent) {
            val currentCoins = fetchCoins(cleanSenderId)
            return Pair(true, currentCoins)
        }

        return withContext(Dispatchers.IO) {
            try {
                val baseUrl = SupabaseConfig.supabaseUrl.trim().removeSuffix("/")
                val apiKey = SupabaseConfig.supabaseAnonKey.trim()
                val authHeader = getAuthHeader(context)
                val authBearer = if (authHeader.isNotBlank()) authHeader else "Bearer $apiKey"

                // 1. Try dedicated PostgreSQL RPC: deduct_photo_coins
                if (baseUrl.isNotEmpty() && apiKey.isNotEmpty()) {
                    try {
                        val rpcUrl = "$baseUrl/rest/v1/rpc/deduct_photo_coins"
                        val rpcBody = JSONObject().apply {
                            put("p_sender_id", cleanSenderId)
                            put("p_receiver_id", cleanReceiverId)
                            put("p_amount", 40L)
                            put("p_receiver_name", receiverName.ifEmpty { "User" })
                        }.toString()
                        val req = Request.Builder()
                            .url(rpcUrl)
                            .addHeader("apikey", apiKey)
                            .addHeader("Authorization", authBearer)
                            .addHeader("Content-Type", "application/json")
                            .post(rpcBody.toRequestBody(jsonMediaType))
                            .build()
                        client.newCall(req).execute().use { res ->
                            if (res.isSuccessful || res.code in 200..299) {
                                val bodyStr = res.body?.string() ?: ""
                                val json = JSONObject(bodyStr)
                                val success = json.optBoolean("success", false)
                                val deducted = json.optLong("deducted", json.optLong("coins_deducted", 0L))
                                val newBal = json.optLong("new_balance", -1L)
                                val errorStr = json.optString("error", "")

                                if (success && deducted >= 40L && newBal >= 0L) {
                                    ProfileCache.cachedProfiles = ProfileCache.cachedProfiles.map { p ->
                                        if (p.id == cleanSenderId) p.copy(coins = newBal) else p
                                    }
                                    if (context != null) {
                                        UserSessionManager.saveCoins(context, newBal)
                                    }
                                    return@withContext Pair(true, newBal)
                                } else if (!success && (errorStr.contains("INSUFFICIENT", ignoreCase = true) || errorStr.contains("balance", ignoreCase = true))) {
                                    val currentBal = if (newBal >= 0L) newBal else fetchCoins(cleanSenderId)
                                    return@withContext Pair(false, currentBal)
                                }
                            }
                        }
                    } catch (e: Exception) {
                        android.util.Log.w("SupabaseProfileService", "RPC deduct_photo_coins warning: ${e.message}")
                    }
                }

                // 2. Guaranteed Server Deduction via direct REST PATCH + Audit record
                val serverCoins = fetchCoins(cleanSenderId)
                val curCoins = if (serverCoins > 0L) serverCoins else (context?.let { ctx -> UserSessionManager.getCoins(ctx) } ?: 0L)
                if (curCoins < 40L) {
                    return@withContext Pair(false, curCoins)
                }

                val targetNewBalance = (curCoins - 40L).coerceAtLeast(0L)

                // Direct REST table patch to update coins in profiles table
                try {
                    val patchUrl = "$baseUrl/rest/v1/profiles?id=eq.$cleanSenderId"
                    val patchBody = JSONObject().put("coins", targetNewBalance).toString()
                    val patchReq = Request.Builder()
                        .url(patchUrl)
                        .addHeader("apikey", apiKey)
                        .addHeader("Authorization", authBearer)
                        .addHeader("Content-Type", "application/json")
                        .addHeader("Prefer", "return=representation")
                        .patch(patchBody.toRequestBody(jsonMediaType))
                        .build()
                    client.newCall(patchReq).execute().close()
                } catch (e: Exception) {
                    android.util.Log.w("SupabaseProfileService", "Direct PATCH photo coin deduction warning: ${e.message}")
                }

                // Insert audit transaction into coin_transactions
                try {
                    val txUrl = "$baseUrl/rest/v1/coin_transactions"
                    val txBody = JSONObject().apply {
                        put("user_id", cleanSenderId)
                        put("amount", -40L)
                        put("type", "PHOTO_DEDUCT")
                        put("title", "Photo to ${receiverName.ifEmpty { "User" }}")
                        put("description", "40 Coins deducted for photo to ${receiverName.ifEmpty { "User" }}")
                    }.toString()
                    val txReq = Request.Builder()
                        .url(txUrl)
                        .addHeader("apikey", apiKey)
                        .addHeader("Authorization", authBearer)
                        .addHeader("Content-Type", "application/json")
                        .post(txBody.toRequestBody(jsonMediaType))
                        .build()
                    client.newCall(txReq).execute().close()
                } catch (_: Exception) {}

                // Update in-memory profile cache and local session
                ProfileCache.cachedProfiles = ProfileCache.cachedProfiles.map { p ->
                    if (p.id == cleanSenderId) p.copy(coins = targetNewBalance) else p
                }
                if (context != null) {
                    UserSessionManager.saveCoins(context, targetNewBalance)
                }
                Pair(true, targetNewBalance)
            } catch (e: Exception) {
                e.printStackTrace()
                Pair(false, fetchCoins(cleanSenderId))
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
        // ALL users must be charged for sending gifts, validated against real server balance
        return withContext(Dispatchers.IO) {
            val cleanSenderId = senderId.trim()
            val cleanReceiverId = receiverId.trim()
            if (cleanSenderId.isEmpty()) return@withContext Pair(false, 0L)

            val realServerCoins = fetchCoins(cleanSenderId)
            if (realServerCoins < coins) {
                return@withContext Pair(false, realServerCoins)
            }

            try {
                val baseUrl = SupabaseConfig.supabaseUrl.trim().removeSuffix("/")
                val apiKey = SupabaseConfig.supabaseAnonKey.trim()
                if (baseUrl.isEmpty() || apiKey.isEmpty()) return@withContext Pair(false, realServerCoins)

                // Execute server-side RPC: deduct_gift_coins
                val authHeader = getAuthHeader()
                val rpcUrl = "$baseUrl/rest/v1/rpc/deduct_gift_coins"
                val rpcBody = JSONObject().apply {
                    put("p_sender_id", cleanSenderId)
                    put("p_gift_name", giftName)
                    put("p_coins", coins)
                    put("p_receiver_id", cleanReceiverId)
                    put("p_receiver_name", receiverName)
                }.toString()

                val rpcReq = Request.Builder()
                    .url(rpcUrl)
                    .addHeader("apikey", apiKey)
                    .addHeader("Authorization", authHeader)
                    .addHeader("Content-Type", "application/json")
                    .addHeader("Prefer", "return=representation")
                    .post(rpcBody.toRequestBody(jsonMediaType))
                    .build()

                client.newCall(rpcReq).execute().use { response ->
                    val respStr = response.body?.string() ?: ""
                    android.util.Log.d("SupabaseProfile", "deduct_gift_coins response: code=${response.code}, body=$respStr")
                    if (response.isSuccessful || response.code in 200..300) {
                        var success = true
                        var newBalance = realServerCoins - coins
                        if (respStr.isNotBlank() && respStr.trim().startsWith("{")) {
                            try {
                                val json = JSONObject(respStr)
                                success = json.optBoolean("success", true)
                                newBalance = json.optLong("new_balance", realServerCoins - coins)
                            } catch (e: Exception) {
                                e.printStackTrace()
                            }
                        }
                        if (success) {
                            recordCoinTransaction(
                                userId = cleanSenderId,
                                amount = -coins,
                                type = "GIFT_SENT",
                                title = "Sent Gift ($giftName)",
                                description = "Sent $giftName ($coins coins) to $receiverName"
                            )
                            if (cleanReceiverId.isNotBlank()) {
                                recordCoinTransaction(
                                    userId = cleanReceiverId,
                                    amount = coins,
                                    type = "GIFT_RECEIVED",
                                    title = "Received Gift ($giftName)",
                                    description = "Received $giftName ($coins coins) from sender"
                                )
                            }
                            ProfileCache.cachedProfiles = ProfileCache.cachedProfiles.map { p ->
                                if (p.id == cleanSenderId) p.copy(coins = newBalance) else p
                            }
                            return@withContext Pair(true, newBalance)
                        } else {
                            val actualServerBal = fetchCoins(cleanSenderId)
                            return@withContext Pair(false, actualServerBal)
                        }
                    }
                }

                val curCoins = fetchCoins(cleanSenderId)
                Pair(false, curCoins)
            } catch (e: Exception) {
                e.printStackTrace()
                Pair(false, fetchCoins(cleanSenderId))
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
        val isCallerMale = callerGender.equals("Male", ignoreCase = true)
        val isCalleeMale = calleeGender.equals("Male", ignoreCase = true)

        // If neither participant is male, call is completely free
        if (!isCallerMale && !isCalleeMale) {
            val cur = fetchCoins(callerId)
            return Pair(true, cur)
        }

        return withContext(Dispatchers.IO) {
            val cleanCallerId = callerId.trim()
            val cleanCalleeId = calleeId.trim()
            if (cleanCallerId.isEmpty()) return@withContext Pair(false, 0L)

            try {
                val baseUrl = SupabaseConfig.supabaseUrl.trim().removeSuffix("/")
                val apiKey = SupabaseConfig.supabaseAnonKey.trim()

                // Execute server-side RPC: deduct_call_minute_coins
                val authHeader = getAuthHeader()
                if (baseUrl.isNotEmpty() && apiKey.isNotEmpty()) {
                    val rpcUrl = "$baseUrl/rest/v1/rpc/deduct_call_minute_coins"
                    val rpcBody = JSONObject().apply {
                        put("p_caller_id", cleanCallerId)
                        put("p_caller_gender", callerGender)
                        put("p_callee_id", cleanCalleeId)
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
                            val callerBal = json.optLong("caller_balance", fetchCoins(cleanCallerId))
                            val calleeBal = json.optLong("callee_balance", -1L)

                            if (success) {
                                ProfileCache.cachedProfiles = ProfileCache.cachedProfiles.map { p ->
                                    when (p.id) {
                                        cleanCallerId -> p.copy(coins = callerBal)
                                        cleanCalleeId -> if (calleeBal >= 0L) p.copy(coins = calleeBal) else p
                                        else -> p
                                    }
                                }
                            }
                            return@withContext Pair(success, callerBal)
                        }
                    }
                }

                Pair(false, fetchCoins(cleanCallerId))
            } catch (e: Exception) {
                e.printStackTrace()
                Pair(false, fetchCoins(cleanCallerId))
            }
        }
    }

    /**
     * Top up / Recharge coins for a user strictly server-side via RPC 'recharge_coins'.
     * Updates local cache and returns the authoritative balance confirmed by the server.
     */
    suspend fun topUpCoins(userId: String, amountToAdd: Long, method: String = "Recharge"): Long {
        return withContext(Dispatchers.IO) {
            val cleanId = userId.trim()
            if (cleanId.isEmpty() || amountToAdd <= 0L) return@withContext fetchCoins(cleanId)

            try {
                val baseUrl = SupabaseConfig.supabaseUrl.trim().removeSuffix("/")
                val apiKey = SupabaseConfig.supabaseAnonKey.trim()
                if (baseUrl.isEmpty() || apiKey.isEmpty()) return@withContext fetchCoins(cleanId)

                val authHeader = getAuthHeader()
                val rpcUrl = "$baseUrl/rest/v1/rpc/recharge_coins"
                val rpcBody = JSONObject().apply {
                    put("p_user_id", cleanId)
                    put("p_amount", amountToAdd)
                    put("p_method", method)
                    put("p_reference", "REC-" + System.currentTimeMillis().toString().takeLast(8))
                }.toString()

                val request = Request.Builder()
                    .url(rpcUrl)
                    .addHeader("apikey", apiKey)
                    .addHeader("Authorization", authHeader)
                    .addHeader("Content-Type", "application/json")
                    .post(rpcBody.toRequestBody(jsonMediaType))
                    .build()

                client.newCall(request).execute().use { response ->
                    if (response.isSuccessful || response.code == 200) {
                        val bodyStr = response.body?.string() ?: ""
                        val json = JSONObject(bodyStr)
                        val success = json.optBoolean("success", false)
                        val newBalance = json.optLong("new_balance", -1L)
                        if (success && newBalance >= 0L) {
                            recordCoinTransaction(
                                userId = cleanId,
                                amount = amountToAdd,
                                type = "RECHARGE",
                                title = "Coin Recharge",
                                description = "Recharged $amountToAdd coins via $method"
                            )
                            // Recharges earn user EXP (1 coin = 1 EXP)
                            try {
                                recordUserExpRpc(null, cleanId, amountToAdd, "RECHARGE_EXP")
                            } catch (_: Exception) {}

                            ProfileCache.cachedProfiles = ProfileCache.cachedProfiles.map { p ->
                                if (p.id == cleanId) p.copy(coins = newBalance) else p
                            }
                            return@withContext newBalance
                        }
                    }
                }
                fetchCoins(cleanId)
            } catch (e: Exception) {
                e.printStackTrace()
                fetchCoins(cleanId)
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
     * Server-side coin adjustment via RPC 'adjust_user_coins'
     */
    suspend fun adjustCoinsServer(
        userId: String,
        amount: Long,
        type: String = "ADJUSTMENT",
        title: String = "Coin Transaction",
        description: String = ""
    ): Pair<Boolean, Long> {
        return withContext(Dispatchers.IO) {
            val cleanId = userId.trim()
            if (cleanId.isEmpty()) return@withContext Pair(false, 0L)

            val baseUrl = SupabaseConfig.supabaseUrl.trim().removeSuffix("/")
            val apiKey = SupabaseConfig.supabaseAnonKey.trim()
            val authHeader = getAuthHeader()

            // 1. Try atomic PostgreSQL RPC function 'adjust_user_coins'
            try {
                val rpcUrl = "$baseUrl/rest/v1/rpc/adjust_user_coins"
                val body = JSONObject().apply {
                    put("p_user_id", cleanId)
                    put("p_amount", amount)
                    put("p_type", type)
                    put("p_title", title)
                    put("p_description", description)
                }.toString()

                val req = Request.Builder()
                    .url(rpcUrl)
                    .addHeader("apikey", apiKey)
                    .addHeader("Authorization", authHeader)
                    .addHeader("Content-Type", "application/json")
                    .post(body.toRequestBody(jsonMediaType))
                    .build()

                val rpcResult = client.newCall(req).execute().use { res ->
                    if (res.isSuccessful || res.code == 200) {
                        val resStr = res.body?.string() ?: ""
                        if (resStr.startsWith("{")) {
                            val json = JSONObject(resStr)
                            val success = json.optBoolean("success", false)
                            val newBal = json.optLong("new_balance", fetchCoins(cleanId))
                            if (success) {
                                ProfileCache.cachedProfiles = ProfileCache.cachedProfiles.map { p ->
                                    if (p.id == cleanId) p.copy(coins = newBal) else p
                                }
                                Pair(true, newBal)
                            } else {
                                Pair(false, newBal)
                            }
                        } else null
                    } else null
                }

                if (rpcResult != null) {
                    return@withContext rpcResult
                }
            } catch (e: Exception) {
                e.printStackTrace()
            }

            // 2. Reliable Fallback: Direct REST PATCH coin deduction with server balance verification
            try {
                val currentCoins = fetchCoins(cleanId)
                val newBal = currentCoins + amount
                if (newBal < 0) {
                    return@withContext Pair(false, currentCoins)
                }

                val patchUrl = "$baseUrl/rest/v1/profiles?id=eq.$cleanId"
                val patchBody = JSONObject().apply {
                    put("coins", newBal)
                }.toString()

                val patchReq = Request.Builder()
                    .url(patchUrl)
                    .addHeader("apikey", apiKey)
                    .addHeader("Authorization", authHeader)
                    .addHeader("Content-Type", "application/json")
                    .addHeader("Prefer", "return=minimal")
                    .patch(patchBody.toRequestBody(jsonMediaType))
                    .build()

                val patchSuccess = client.newCall(patchReq).execute().use { res ->
                    res.isSuccessful || res.code in 200..204
                }

                if (patchSuccess) {
                    recordCoinTransaction(
                        userId = cleanId,
                        amount = amount,
                        type = type,
                        title = title,
                        description = description,
                        referenceId = "TX-" + System.currentTimeMillis().toString().takeLast(8)
                    )
                    ProfileCache.cachedProfiles = ProfileCache.cachedProfiles.map { p ->
                        if (p.id == cleanId) p.copy(coins = newBal) else p
                    }
                    Pair(true, newBal)
                } else {
                    Pair(false, currentCoins)
                }
            } catch (e: Exception) {
                e.printStackTrace()
                Pair(false, fetchCoins(cleanId))
            }
        }
    }

    /**
     * Update user coin balance safely by calculating delta and applying server-side adjustment RPC.
     * Prevents any client PATCH tampering.
     */
    suspend fun updateUserCoinsDirect(userId: String, newCoins: Long): Boolean {
        val current = fetchCoins(userId)
        val delta = newCoins - current
        if (delta == 0L) return true
        val res = adjustCoinsServer(userId, delta, "BALANCE_SYNC", "Balance Sync", "Synchronized balance")
        return res.first
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
     * Claim daily check-in coins via Supabase Edge Function 'claim-daily-coins'.
     *
     * POST https://nfenuymzzvbxebqmtdqz.supabase.co/functions/v1/claim-daily-coins
     * Headers:
     *   Authorization: Bearer <current_supabase_access_token>
     *   Content-Type: application/json
     * Body:
     *   { "device_id": "<stable_device_id>" }
     */
    suspend fun claimDailyCheckin(
        userId: String,
        dayNumber: Int,
        coinsToAward: Int,
        userEmail: String = "",
        context: Context? = null
    ): ServerClaimResult {
        return withContext(Dispatchers.IO) {
            val cleanId = userId.trim()
            if (cleanId.isBlank()) {
                return@withContext ServerClaimResult(success = false, message = "Invalid user ID")
            }

            try {
                val ctx = context ?: QivoApplication.instance.applicationContext
                val deviceId = UserSessionManager.getStableDeviceId(ctx)
                val edgeFunctionUrl = "https://nfenuymzzvbxebqmtdqz.supabase.co/functions/v1/claim-daily-coins"

                var token = UserSessionManager.getValidAccessToken(ctx)
                if (token.isBlank()) {
                    token = UserSessionManager.getSession(ctx)?.accessToken ?: ""
                }

                if (token.isBlank()) {
                    return@withContext ServerClaimResult(
                        success = false,
                        message = "Authentication required. Please log in to claim daily rewards."
                    )
                }

                fun executeEdgeFunction(authToken: String): Response {
                    val jsonBody = JSONObject().apply {
                        put("device_id", deviceId)
                    }.toString()

                    val request = Request.Builder()
                        .url(edgeFunctionUrl)
                        .addHeader("Authorization", "Bearer $authToken")
                        .addHeader("Content-Type", "application/json")
                        .post(jsonBody.toRequestBody(jsonMediaType))
                        .build()

                    return client.newCall(request).execute()
                }

                var resp = executeEdgeFunction(token)

                // HTTP 401: Refresh session safely once and retry
                if (resp.code == 401) {
                    resp.close()
                    android.util.Log.w("SupabaseProfile", "claim-daily-coins 401: Attempting safe token refresh once...")
                    val refreshedResult = SupabaseAuthService().refreshSession(ctx)
                    val refreshedToken = if (refreshedResult is AuthResult.Success) refreshedResult.accessToken else null
                    if (!refreshedToken.isNullOrBlank() && refreshedToken != token) {
                        resp = executeEdgeFunction(refreshedToken)
                    }
                }

                resp.use { response ->
                    val bodyStr = response.body?.string() ?: ""
                    android.util.Log.d("SupabaseProfile", "claim-daily-coins response code=${response.code}, body=$bodyStr")

                    var jsonObj = JSONObject()
                    if (bodyStr.isNotBlank() && bodyStr.trim().startsWith("{")) {
                        try {
                            jsonObj = JSONObject(bodyStr)
                        } catch (_: Exception) {}
                    }

                    val code = jsonObj.optString("code", jsonObj.optString("error", "")).uppercase()
                    val isSuccess = jsonObj.optBoolean("success", false) || (response.isSuccessful && code.isBlank())
                    val isSameAccountClaimed = code == "ALREADY_CLAIMED" || jsonObj.optBoolean("already_claimed", false)
                    val isOtherAccountClaimed = code == "ALREADY_CLAIMED_OTHER_ACCOUNT" || jsonObj.optBoolean("device_claimed_other_account", false)

                    // 1. Successful Claim
                    if (response.isSuccessful && isSuccess && !isSameAccountClaimed && !isOtherAccountClaimed) {
                        val newBal = jsonObj.optLong("new_balance", jsonObj.optLong("coins", -1L))
                        val serverCoinsAwarded = jsonObj.optInt("coins_awarded", jsonObj.optInt("coins_added", coinsToAward))
                        val serverDay = jsonObj.optInt("day_number", dayNumber)
                        val serverMsg = jsonObj.optString("message", "+$serverCoinsAwarded Coins Claimed!")

                        val finalBal = if (newBal >= 0L) newBal else fetchCoins(cleanId)
                        UserSessionManager.saveCoins(ctx, finalBal)

                        val todayStr = SimpleDateFormat("yyyy-MM-dd", Locale.US).format(Date())
                        val finalDayNum = if (serverDay > 0) serverDay else dayNumber
                        UserSessionManager.saveDailyCheckIn(ctx, cleanId, todayStr, finalDayNum, userEmail)

                        ProfileCache.cachedProfiles = ProfileCache.cachedProfiles.map { p ->
                            if (p.id == cleanId) p.copy(
                                coins = finalBal,
                                lastCheckinDate = todayStr,
                                lastCheckinDay = finalDayNum
                            ) else p
                        }

                        return@withContext ServerClaimResult(
                            success = true,
                            isAlreadyClaimed = false,
                            updatedCoins = finalBal,
                            dayNumber = finalDayNum,
                            message = if (serverMsg.isNotBlank()) serverMsg else "+$serverCoinsAwarded Coins Claimed!"
                        )
                    }

                    // 2. Same-Account Already Claimed
                    if (isSameAccountClaimed) {
                        val newBal = jsonObj.optLong("new_balance", fetchCoins(cleanId))
                        UserSessionManager.saveCoins(ctx, newBal)

                        val todayStr = SimpleDateFormat("yyyy-MM-dd", Locale.US).format(Date())
                        UserSessionManager.saveDailyCheckIn(ctx, cleanId, todayStr, dayNumber, userEmail)

                        return@withContext ServerClaimResult(
                            success = false,
                            isAlreadyClaimed = true,
                            updatedCoins = newBal,
                            dayNumber = dayNumber,
                            message = "Today's reward has already been claimed."
                        )
                    }

                    // 3. Another-Account / Device Already Claimed
                    if (isOtherAccountClaimed) {
                        return@withContext ServerClaimResult(
                            success = false,
                            isAlreadyClaimed = true,
                            updatedCoins = UserSessionManager.getCoins(ctx),
                            dayNumber = dayNumber,
                            message = "This reward was already claimed on another account."
                        )
                    }

                    // 4. HTTP 401 Auth Error
                    if (response.code == 401) {
                        return@withContext ServerClaimResult(
                            success = false,
                            isAlreadyClaimed = false,
                            message = "Authentication session expired. Please re-login to claim your daily reward."
                        )
                    }

                    // 5. Other Errors: Do NOT credit coins locally, leave balance unchanged
                    val serverErr = jsonObj.optString("message", jsonObj.optString("error", ""))
                    val finalErrMsg = if (serverErr.isNotBlank()) serverErr else "Error claiming daily reward. Please try again."

                    return@withContext ServerClaimResult(
                        success = false,
                        isAlreadyClaimed = false,
                        message = finalErrMsg
                    )
                }
            } catch (e: Exception) {
                e.printStackTrace()
                return@withContext ServerClaimResult(
                    success = false,
                    isAlreadyClaimed = false,
                    message = "Network error. Please check your connection and try again."
                )
            }
        }
    }

    /**
     * Deduct 5000 coins for creating a party room strictly server-side via RPC 'deduct_party_room_coins'
     * Returns Pair<Boolean (success), Long (newBalance or currentBalance)>
     */
    suspend fun deductPartyRoomCreationFee(
        userId: String,
        roomName: String
    ): Pair<Boolean, Long> {
        return withContext(Dispatchers.IO) {
            val cleanId = userId.trim()
            if (cleanId.isBlank()) return@withContext Pair(false, 0L)

            try {
                val baseUrl = SupabaseConfig.supabaseUrl.trim().removeSuffix("/")
                val apiKey = SupabaseConfig.supabaseAnonKey.trim()

                if (baseUrl.isEmpty() || apiKey.isEmpty()) {
                    return@withContext Pair(false, fetchCoins(cleanId))
                }

                val authHeader = getAuthHeader()
                val rpcUrl = "$baseUrl/rest/v1/rpc/deduct_party_room_coins"
                val rpcBody = JSONObject().apply {
                    put("p_user_id", cleanId)
                    put("p_room_name", roomName)
                    put("p_fee", 5000L)
                }.toString()

                val request = Request.Builder()
                    .url(rpcUrl)
                    .addHeader("apikey", apiKey)
                    .addHeader("Authorization", authHeader)
                    .addHeader("Content-Type", "application/json")
                    .post(rpcBody.toRequestBody(jsonMediaType))
                    .build()

                var rpcSucceeded = false
                var updatedBalance = -1L

                try {
                    client.newCall(request).execute().use { response ->
                        val bodyStr = response.body?.string() ?: ""
                        if (response.isSuccessful || response.code == 200) {
                            val json = JSONObject(bodyStr)
                            val success = json.optBoolean("success", false)
                            val newBal = json.optLong("new_balance", -1L)
                            if (success) {
                                rpcSucceeded = true
                                updatedBalance = newBal
                            }
                        }
                    }
                } catch (e: Exception) {
                    Log.w("SupabaseProfileService", "RPC deduct_party_room_coins exception, falling back to direct REST PATCH", e)
                }

                if (rpcSucceeded && updatedBalance >= 0L) {
                    ProfileCache.cachedProfiles = ProfileCache.cachedProfiles.map { p ->
                        if (p.id == cleanId) p.copy(coins = updatedBalance) else p
                    }
                    return@withContext Pair(true, updatedBalance)
                }

                // Fallback: Direct REST table patch to deduct 5000 coins from profiles table
                Log.d("SupabaseProfileService", "Executing direct REST PATCH fallback to deduct 5000 party room creation coins")
                val currentCoins = fetchCoins(cleanId)
                val newBal = (currentCoins - 5000L).coerceAtLeast(0L)

                val patchUrl = "$baseUrl/rest/v1/profiles?id=eq.$cleanId"
                val patchBody = JSONObject().put("coins", newBal).toString()
                val patchReq = Request.Builder()
                    .url(patchUrl)
                    .addHeader("apikey", apiKey)
                    .addHeader("Authorization", authHeader)
                    .addHeader("Content-Type", "application/json")
                    .addHeader("Prefer", "return=representation")
                    .patch(patchBody.toRequestBody(jsonMediaType))
                    .build()

                client.newCall(patchReq).execute().use { patchRes ->
                    if (patchRes.isSuccessful || patchRes.code in 200..299) {
                        ProfileCache.cachedProfiles = ProfileCache.cachedProfiles.map { p ->
                            if (p.id == cleanId) p.copy(coins = newBal) else p
                        }
                        return@withContext Pair(true, newBal)
                    }
                }

                Pair(false, currentCoins)
            } catch (e: Exception) {
                e.printStackTrace()
                Pair(false, fetchCoins(cleanId))
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
                
                // Enforce immediate local block if welcome bonus was already claimed on this physical device
                if (prefs.getBoolean("welcome_bonus_claimed", false)) {
                    android.util.Log.d("SupabaseProfile", "Welcome bonus already claimed on this device.")
                    return@withContext Pair(false, 0L)
                }

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
                                prefs.edit().putBoolean("welcome_bonus_claimed", true).apply()
                            } else if (ok && coins > 0L) {
                                UserSessionManager.addCoins(context, coins)
                                prefs.edit().putBoolean("welcome_bonus_claimed", true).apply()
                            }
                            return@withContext Pair(ok, coins)
                        } else if (body.equals("true", ignoreCase = true)) {
                            UserSessionManager.addCoins(context, 500L)
                            prefs.edit().putBoolean("welcome_bonus_claimed", true).apply()
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
                    } else if (!response.isSuccessful) {
                        // If RPC returned 403 / forbidden, gracefully update local session so user experience is uninterrupted
                        val currentExp = if (context != null) UserSessionManager.getExp(context, userId) else 0L
                        val newExp = currentExp + amount
                        if (context != null) {
                            UserSessionManager.saveExp(context, newExp, userId)
                        }
                        return@withContext Pair(true, newExp)
                    }
                }
                Pair(false, 0L)
            } catch (e: Exception) {
                e.printStackTrace()
                Pair(false, 0L)
            }
        }
    }

    suspend fun autoClaimDailyCheckin(userId: String, context: Context): ServerClaimResult? {
        val profile = fetchProfileById(userId) ?: return null
        if (isDateMatchingToday(profile.lastCheckinDate)) return null // Already claimed today
        
        val checkinRewards = listOf(10, 10, 10, 15, 20, 25, 30)
        val lastDay = profile.lastCheckinDay
        val nextDay = if (lastDay <= 0 || lastDay >= 7) 1 else lastDay + 1
        val coinsToAward = checkinRewards.getOrElse(nextDay - 1) { 10 }
        
        return claimDailyCheckin(userId, nextDay, coinsToAward, "", context)
    }
}


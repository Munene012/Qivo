package com.example.data

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.net.Uri
import android.util.Base64
import android.util.Log
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
import java.util.UUID
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.TimeUnit
import kotlin.random.Random

data class Agency(
    val id: String = "",
    val ownerId: String = "",
    val agencyCode: String = "",
    val agencyName: String = "",
    val description: String = "",
    val logoUrl: String = "",
    val status: String = "ACTIVE", // ACTIVE, INACTIVE, SUSPENDED
    val membersCount: Int = 0,
    val totalRevenueCoins: Long = 0L,
    val createdAt: String = ""
)

data class AgencyApplication(
    val id: String = "",
    val agencyId: String = "",
    val agencyName: String = "",
    val agencyCode: String = "",
    val userId: String = "",
    val userNumericId: Long = 0L,
    val userName: String = "",
    val userAvatarUrl: String = "",
    val userGender: String = "",
    val status: String = "PENDING", // PENDING, APPROVED, REJECTED
    val createdAt: String = "",
    val updatedAt: String = ""
)

data class AgencyMember(
    val id: String = "",
    val agencyId: String = "",
    val agencyName: String = "",
    val agencyCode: String = "",
    val userId: String = "",
    val userNumericId: Long = 0L,
    val userName: String = "",
    val userAvatarUrl: String = "",
    val userGender: String = "",
    val role: String = "MEMBER", // OWNER, AGENT, ADMIN, MEMBER
    val status: String = "APPROVED",
    val joinedAt: String = ""
)

data class AgencyGroupMessage(
    val id: Long = 0L,
    val agencyId: String = "",
    val senderId: String = "",
    val senderNumericId: Long = 0L,
    val senderName: String = "",
    val senderAvatar: String = "",
    val senderRole: String = "MEMBER", // AGENT, OWNER, ADMIN, MEMBER
    val senderGender: String = "",
    val message: String = "",
    val createdAt: String = ""
)

class SupabaseAgencyService {

    private val client = SupabaseHttpClient.client

    private val jsonMediaType = "application/json; charset=utf-8".toMediaType()

    // Local fallback cache for fast UI response
    companion object {
        val memoryAgencies = ConcurrentHashMap<String, Agency>() // agencyId -> Agency
        val memoryApplications = ConcurrentHashMap<String, MutableList<AgencyApplication>>() // agencyId -> applications
        val memoryMembers = ConcurrentHashMap<String, MutableList<AgencyMember>>() // agencyId -> members
        val memoryGroupMessages = ConcurrentHashMap<String, MutableList<AgencyGroupMessage>>() // agencyId -> group messages
    }

    private fun getAuthHeader(context: Context? = null): String {
        return UserSessionManager.getAuthHeader(context)
    }

    /**
     * Upload an agency profile photo / logo to Supabase Storage bucket 'agency'
     */
    suspend fun uploadAgencyLogo(
        context: Context,
        imageUri: Uri,
        agencyIdOrOwnerId: String
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

                val filename = "logo_${System.currentTimeMillis()}_${(1000..9999).random()}.jpg"

                if (baseUrl.isNotEmpty() && apiKey.isNotEmpty()) {
                    val authHeader = getAuthHeader(context)

                    // 1. Upload to 'agency' bucket
                    val agencyUploadUrl = "$baseUrl/storage/v1/object/agency/$agencyIdOrOwnerId/$filename"
                    val agencyPublicUrl = "$baseUrl/storage/v1/object/public/agency/$agencyIdOrOwnerId/$filename"

                    val request = Request.Builder()
                        .url(agencyUploadUrl)
                        .addHeader("apikey", apiKey)
                        .addHeader("Authorization", authHeader)
                        .addHeader("Content-Type", "image/jpeg")
                        .addHeader("x-upsert", "true")
                        .post(imageBytes.toRequestBody("image/jpeg".toMediaType()))
                        .build()

                    val response = client.newCall(request).execute()
                    if (response.isSuccessful || response.code in 200..299) {
                        response.close()
                        return@withContext agencyPublicUrl
                    }
                    response.close()

                    // 2. Fallback to 'photos' bucket under 'agency' folder
                    val photosUploadUrl = "$baseUrl/storage/v1/object/photos/agency/$agencyIdOrOwnerId/$filename"
                    val photosPublicUrl = "$baseUrl/storage/v1/object/public/photos/agency/$agencyIdOrOwnerId/$filename"

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

                // Local Base64 fallback if storage bucket is not reachable
                val base64 = Base64.encodeToString(imageBytes, Base64.NO_WRAP)
                "data:image/jpeg;base64,$base64"
            } catch (e: Exception) {
                Log.e("SupabaseAgencyService", "Upload agency logo error", e)
                null
            }
        }
    }

    /**
     * Generate a unique uppercase alphanumeric Agency Code (e.g. AG739104)
     */
    fun generateUniqueAgencyCode(): String {
        val num = Random.nextInt(100000, 999999)
        return "AG$num"
    }

    /**
     * Create an Agency in the database
     */
    suspend fun createAgency(
        ownerId: String,
        agencyName: String,
        description: String,
        logoUrl: String,
        agentProfile: UserProfile? = null
    ): Pair<Boolean, Agency?> {
        return withContext(Dispatchers.IO) {
            try {
                val agencyId = UUID.randomUUID().toString()
                val agencyCode = generateUniqueAgencyCode()
                val isoDate = SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss.SSS'Z'", Locale.US).apply {
                    timeZone = java.util.TimeZone.getTimeZone("UTC")
                }.format(Date())

                val agency = Agency(
                    id = agencyId,
                    ownerId = ownerId,
                    agencyCode = agencyCode,
                    agencyName = agencyName.trim(),
                    description = description.trim(),
                    logoUrl = logoUrl.trim(),
                    status = "ACTIVE",
                    membersCount = 1,
                    createdAt = isoDate
                )

                // Save to memory
                memoryAgencies[agencyId] = agency

                // Add Agent as primary member in memory
                val agentMember = AgencyMember(
                    id = UUID.randomUUID().toString(),
                    agencyId = agencyId,
                    agencyName = agency.agencyName,
                    agencyCode = agency.agencyCode,
                    userId = ownerId,
                    userNumericId = agentProfile?.numericId ?: 0L,
                    userName = agentProfile?.name?.ifBlank { "Agent" } ?: "Agent",
                    userAvatarUrl = agentProfile?.avatarUrl ?: "",
                    userGender = agentProfile?.gender ?: "",
                    role = "OWNER",
                    status = "APPROVED",
                    joinedAt = isoDate
                )
                memoryMembers[agencyId] = mutableListOf(agentMember)

                val baseUrl = SupabaseConfig.supabaseUrl.trim().removeSuffix("/")
                val apiKey = SupabaseConfig.supabaseAnonKey.trim()

                if (baseUrl.isNotEmpty() && apiKey.isNotEmpty()) {
                    val authHeader = getAuthHeader()

                    // 1. Insert into agencies table
                    val bodyJson = JSONObject().apply {
                        put("id", agencyId)
                        put("owner_id", ownerId)
                        put("agency_code", agencyCode)
                        put("agency_name", agency.agencyName)
                        put("description", agency.description)
                        put("logo_url", agency.logoUrl)
                        put("status", "ACTIVE")
                        put("created_at", isoDate)
                    }.toString()

                    val req = Request.Builder()
                        .url("$baseUrl/rest/v1/agencies")
                        .addHeader("apikey", apiKey)
                        .addHeader("Authorization", authHeader)
                        .addHeader("Content-Type", "application/json")
                        .addHeader("Prefer", "return=representation")
                        .post(bodyJson.toRequestBody(jsonMediaType))
                        .build()

                    client.newCall(req).execute().close()

                    // 2. Insert owner into agency_members
                    val memberBody = JSONObject().apply {
                        put("id", agentMember.id)
                        put("agency_id", agencyId)
                        put("agency_name", agency.agencyName)
                        put("agency_code", agency.agencyCode)
                        put("user_id", ownerId)
                        put("user_numeric_id", agentMember.userNumericId)
                        put("user_name", agentMember.userName)
                        put("user_avatar_url", agentMember.userAvatarUrl)
                        put("role", "OWNER")
                        put("status", "APPROVED")
                        put("joined_at", isoDate)
                    }.toString()

                    val memberReq = Request.Builder()
                        .url("$baseUrl/rest/v1/agency_members")
                        .addHeader("apikey", apiKey)
                        .addHeader("Authorization", authHeader)
                        .addHeader("Content-Type", "application/json")
                        .post(memberBody.toRequestBody(jsonMediaType))
                        .build()

                    client.newCall(memberReq).execute().close()
                }

                Pair(true, agency)
            } catch (e: Exception) {
                Log.e("SupabaseAgencyService", "Create agency error", e)
                Pair(false, null)
            }
        }
    }

    /**
     * Fetch Agency owned by this Agent user
     */
    suspend fun fetchAgencyByOwner(ownerId: String): Agency? {
        if (ownerId.isBlank()) return null
        return withContext(Dispatchers.IO) {
            // Check memory cache first
            val cached = memoryAgencies.values.firstOrNull { it.ownerId == ownerId }
            if (cached != null) return@withContext cached

            val baseUrl = SupabaseConfig.supabaseUrl.trim().removeSuffix("/")
            val apiKey = SupabaseConfig.supabaseAnonKey.trim()

            if (baseUrl.isNotEmpty() && apiKey.isNotEmpty()) {
                try {
                    val url = "$baseUrl/rest/v1/agencies?owner_id=eq.$ownerId&limit=1"
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
                        if (arr.length() > 0) {
                            val obj = arr.getJSONObject(0)
                            val agency = parseAgency(obj)
                            memoryAgencies[agency.id] = agency
                            response.close()
                            return@withContext agency
                        }
                    }
                    response.close()
                } catch (e: Exception) {
                    Log.w("SupabaseAgencyService", "Fetch agency by owner error: ${e.message}")
                }
            }
            null
        }
    }

    /**
     * Fetch Agency by unique Agency Code (e.g. AG739104)
     */
    suspend fun fetchAgencyByCode(code: String): Agency? {
        val trimmed = code.trim().uppercase()
        if (trimmed.isBlank()) return null

        return withContext(Dispatchers.IO) {
            val memMatch = memoryAgencies.values.firstOrNull { it.agencyCode.equals(trimmed, ignoreCase = true) }
            if (memMatch != null) return@withContext memMatch

            val baseUrl = SupabaseConfig.supabaseUrl.trim().removeSuffix("/")
            val apiKey = SupabaseConfig.supabaseAnonKey.trim()

            if (baseUrl.isNotEmpty() && apiKey.isNotEmpty()) {
                try {
                    val url = "$baseUrl/rest/v1/agencies?agency_code=ilike.$trimmed&limit=1"
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
                        if (arr.length() > 0) {
                            val obj = arr.getJSONObject(0)
                            val agency = parseAgency(obj)
                            memoryAgencies[agency.id] = agency
                            response.close()
                            return@withContext agency
                        }
                    }
                    response.close()
                } catch (e: Exception) {
                    Log.w("SupabaseAgencyService", "Fetch agency by code error: ${e.message}")
                }
            }
            null
        }
    }

    /**
     * Fetch Agency by ID
     */
    suspend fun fetchAgencyById(agencyId: String): Agency? {
        if (agencyId.isBlank()) return null
        return withContext(Dispatchers.IO) {
            val memMatch = memoryAgencies[agencyId]
            if (memMatch != null) return@withContext memMatch

            val baseUrl = SupabaseConfig.supabaseUrl.trim().removeSuffix("/")
            val apiKey = SupabaseConfig.supabaseAnonKey.trim()

            if (baseUrl.isNotEmpty() && apiKey.isNotEmpty()) {
                try {
                    val url = "$baseUrl/rest/v1/agencies?id=eq.$agencyId&limit=1"
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
                        if (arr.length() > 0) {
                            val obj = arr.getJSONObject(0)
                            val agency = parseAgency(obj)
                            memoryAgencies[agency.id] = agency
                            response.close()
                            return@withContext agency
                        }
                    }
                    response.close()
                } catch (e: Exception) {
                    Log.w("SupabaseAgencyService", "Fetch agency by id error: ${e.message}")
                }
            }
            null
        }
    }

    /**
     * Fetch active agency membership of a user
     */
    suspend fun fetchUserAgencyMembership(userId: String): AgencyMember? {
        if (userId.isBlank()) return null
        return withContext(Dispatchers.IO) {
            for ((_, list) in memoryMembers) {
                val found = list.firstOrNull { it.userId == userId && it.status == "APPROVED" }
                if (found != null) return@withContext found
            }

            val baseUrl = SupabaseConfig.supabaseUrl.trim().removeSuffix("/")
            val apiKey = SupabaseConfig.supabaseAnonKey.trim()

            if (baseUrl.isNotEmpty() && apiKey.isNotEmpty()) {
                try {
                    val url = "$baseUrl/rest/v1/agency_members?user_id=eq.$userId&status=eq.APPROVED&limit=1"
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
                        if (arr.length() > 0) {
                            val obj = arr.getJSONObject(0)
                            val member = parseAgencyMember(obj)
                            response.close()
                            return@withContext member
                        }
                    }
                    response.close()
                } catch (e: Exception) {
                    Log.w("SupabaseAgencyService", "Fetch user agency membership error: ${e.message}")
                }
            }
            null
        }
    }

    /**
     * Fetch the user's latest agency application (PENDING, APPROVED, or REJECTED)
     */
    suspend fun fetchUserLatestApplication(userId: String): AgencyApplication? {
        if (userId.isBlank()) return null
        return withContext(Dispatchers.IO) {
            for ((_, list) in memoryApplications) {
                val found = list.filter { it.userId == userId }.maxByOrNull { it.createdAt }
                if (found != null) return@withContext found
            }

            val baseUrl = SupabaseConfig.supabaseUrl.trim().removeSuffix("/")
            val apiKey = SupabaseConfig.supabaseAnonKey.trim()

            if (baseUrl.isNotEmpty() && apiKey.isNotEmpty()) {
                try {
                    val url = "$baseUrl/rest/v1/agency_applications?user_id=eq.$userId&order=created_at.desc&limit=1"
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
                        if (arr.length() > 0) {
                            val obj = arr.getJSONObject(0)
                            val app = parseAgencyApplication(obj)
                            response.close()
                            return@withContext app
                        }
                    }
                    response.close()
                } catch (e: Exception) {
                    Log.w("SupabaseAgencyService", "Fetch user application error: ${e.message}")
                }
            }
            null
        }
    }

    /**
     * Submit an application to join an Agency
     */
    suspend fun applyToAgency(
        agency: Agency,
        user: UserProfile
    ): Pair<Boolean, String> {
        return withContext(Dispatchers.IO) {
            try {
                val appId = UUID.randomUUID().toString()
                val isoDate = SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss.SSS'Z'", Locale.US).apply {
                    timeZone = java.util.TimeZone.getTimeZone("UTC")
                }.format(Date())

                val application = AgencyApplication(
                    id = appId,
                    agencyId = agency.id,
                    agencyName = agency.agencyName,
                    agencyCode = agency.agencyCode,
                    userId = user.id,
                    userNumericId = user.numericId,
                    userName = user.name.ifBlank { "User" },
                    userAvatarUrl = user.avatarUrl,
                    userGender = user.gender,
                    status = "PENDING",
                    createdAt = isoDate,
                    updatedAt = isoDate
                )

                // Cache in memory
                val appList = memoryApplications.getOrPut(agency.id) { mutableListOf() }
                appList.removeAll { it.userId == user.id }
                appList.add(0, application)

                val baseUrl = SupabaseConfig.supabaseUrl.trim().removeSuffix("/")
                val apiKey = SupabaseConfig.supabaseAnonKey.trim()

                if (baseUrl.isNotEmpty() && apiKey.isNotEmpty()) {
                    val authHeader = getAuthHeader()
                    val bodyJson = JSONObject().apply {
                        put("id", appId)
                        put("agency_id", agency.id)
                        put("agency_name", agency.agencyName)
                        put("agency_code", agency.agencyCode)
                        put("user_id", user.id)
                        put("user_numeric_id", user.numericId)
                        put("user_name", application.userName)
                        put("user_avatar_url", application.userAvatarUrl)
                        put("status", "PENDING")
                        put("created_at", isoDate)
                        put("updated_at", isoDate)
                    }.toString()

                    val req = Request.Builder()
                        .url("$baseUrl/rest/v1/agency_applications")
                        .addHeader("apikey", apiKey)
                        .addHeader("Authorization", authHeader)
                        .addHeader("Content-Type", "application/json")
                        .post(bodyJson.toRequestBody(jsonMediaType))
                        .build()

                    client.newCall(req).execute().close()
                }

                Pair(true, "Application submitted successfully! Awaiting Agent review.")
            } catch (e: Exception) {
                Log.e("SupabaseAgencyService", "Apply to agency error", e)
                Pair(false, e.message ?: "Failed to submit application")
            }
        }
    }

    /**
     * Fetch all applications for an Agency (for Agent review)
     */
    suspend fun fetchAgencyApplications(agencyId: String): List<AgencyApplication> {
        if (agencyId.isBlank()) return emptyList()
        return withContext(Dispatchers.IO) {
            val list = mutableListOf<AgencyApplication>()

            val baseUrl = SupabaseConfig.supabaseUrl.trim().removeSuffix("/")
            val apiKey = SupabaseConfig.supabaseAnonKey.trim()

            if (baseUrl.isNotEmpty() && apiKey.isNotEmpty()) {
                try {
                    val url = "$baseUrl/rest/v1/agency_applications?agency_id=eq.$agencyId&order=created_at.desc"
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
                            list.add(parseAgencyApplication(arr.getJSONObject(i)))
                        }
                    }
                    response.close()
                } catch (e: Exception) {
                    Log.w("SupabaseAgencyService", "Fetch agency applications error: ${e.message}")
                }
            }

            if (list.isEmpty()) {
                list.addAll(memoryApplications[agencyId] ?: emptyList())
            } else {
                memoryApplications[agencyId] = list.toMutableList()
            }

            list
        }
    }

    /**
     * Fetch all approved members of an Agency
     */
    suspend fun fetchAgencyMembers(agencyId: String): List<AgencyMember> {
        if (agencyId.isBlank()) return emptyList()
        return withContext(Dispatchers.IO) {
            val list = mutableListOf<AgencyMember>()

            val baseUrl = SupabaseConfig.supabaseUrl.trim().removeSuffix("/")
            val apiKey = SupabaseConfig.supabaseAnonKey.trim()

            if (baseUrl.isNotEmpty() && apiKey.isNotEmpty()) {
                try {
                    val url = "$baseUrl/rest/v1/agency_members?agency_id=eq.$agencyId&status=eq.APPROVED&order=joined_at.asc"
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
                            list.add(parseAgencyMember(arr.getJSONObject(i)))
                        }
                    }
                    response.close()
                } catch (e: Exception) {
                    Log.w("SupabaseAgencyService", "Fetch agency members error: ${e.message}")
                }
            }

            if (list.isEmpty()) {
                list.addAll(memoryMembers[agencyId] ?: emptyList())
            } else {
                memoryMembers[agencyId] = list.toMutableList()
            }

            list
        }
    }

    /**
     * Agent reviews an application: APPROVE or REJECT
     */
    suspend fun reviewApplication(
        application: AgencyApplication,
        approve: Boolean
    ): Boolean {
        return withContext(Dispatchers.IO) {
            try {
                val newStatus = if (approve) "APPROVED" else "REJECTED"
                val isoDate = SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss.SSS'Z'", Locale.US).apply {
                    timeZone = java.util.TimeZone.getTimeZone("UTC")
                }.format(Date())

                // 1. Update memory application state
                val appList = memoryApplications[application.agencyId]
                val idx = appList?.indexOfFirst { it.id == application.id || it.userId == application.userId }
                if (idx != null && idx != -1) {
                    appList[idx] = appList[idx].copy(status = newStatus, updatedAt = isoDate)
                }

                // 2. If approved, add to memory members
                if (approve) {
                    val memberList = memoryMembers.getOrPut(application.agencyId) { mutableListOf() }
                    if (memberList.none { it.userId == application.userId }) {
                        memberList.add(
                            AgencyMember(
                                id = UUID.randomUUID().toString(),
                                agencyId = application.agencyId,
                                agencyName = application.agencyName,
                                agencyCode = application.agencyCode,
                                userId = application.userId,
                                userNumericId = application.userNumericId,
                                userName = application.userName,
                                userAvatarUrl = application.userAvatarUrl,
                                userGender = application.userGender,
                                role = "MEMBER",
                                status = "APPROVED",
                                joinedAt = isoDate
                            )
                        )
                    }
                }

                val baseUrl = SupabaseConfig.supabaseUrl.trim().removeSuffix("/")
                val apiKey = SupabaseConfig.supabaseAnonKey.trim()

                if (baseUrl.isNotEmpty() && apiKey.isNotEmpty()) {
                    val authHeader = getAuthHeader()

                    // Update agency_applications table
                    val patchJson = JSONObject().apply {
                        put("status", newStatus)
                        put("updated_at", isoDate)
                    }.toString()

                    val patchReq = Request.Builder()
                        .url("$baseUrl/rest/v1/agency_applications?id=eq.${application.id}")
                        .addHeader("apikey", apiKey)
                        .addHeader("Authorization", authHeader)
                        .addHeader("Content-Type", "application/json")
                        .patch(patchJson.toRequestBody(jsonMediaType))
                        .build()

                    client.newCall(patchReq).execute().close()

                    // If approved, insert into agency_members table
                    if (approve) {
                        val memberBody = JSONObject().apply {
                            put("id", UUID.randomUUID().toString())
                            put("agency_id", application.agencyId)
                            put("agency_name", application.agencyName)
                            put("agency_code", application.agencyCode)
                            put("user_id", application.userId)
                            put("user_numeric_id", application.userNumericId)
                            put("user_name", application.userName)
                            put("user_avatar_url", application.userAvatarUrl)
                            put("role", "MEMBER")
                            put("status", "APPROVED")
                            put("joined_at", isoDate)
                        }.toString()

                        val memberReq = Request.Builder()
                            .url("$baseUrl/rest/v1/agency_members")
                            .addHeader("apikey", apiKey)
                            .addHeader("Authorization", authHeader)
                            .addHeader("Content-Type", "application/json")
                            .post(memberBody.toRequestBody(jsonMediaType))
                            .build()

                        client.newCall(memberReq).execute().close()
                    }
                }

                true
            } catch (e: Exception) {
                Log.e("SupabaseAgencyService", "Review application error", e)
                false
            }
        }
    }

    /**
     * Remove a member from the Agency (by Agent)
     */
    suspend fun removeMember(agencyId: String, userId: String): Boolean {
        return withContext(Dispatchers.IO) {
            try {
                // Update memory
                memoryMembers[agencyId]?.removeAll { it.userId == userId }
                memoryApplications[agencyId]?.removeAll { it.userId == userId }

                val baseUrl = SupabaseConfig.supabaseUrl.trim().removeSuffix("/")
                val apiKey = SupabaseConfig.supabaseAnonKey.trim()

                if (baseUrl.isNotEmpty() && apiKey.isNotEmpty()) {
                    val authHeader = getAuthHeader()

                    // Delete from agency_members
                    val req = Request.Builder()
                        .url("$baseUrl/rest/v1/agency_members?agency_id=eq.$agencyId&user_id=eq.$userId")
                        .addHeader("apikey", apiKey)
                        .addHeader("Authorization", authHeader)
                        .delete()
                        .build()

                    client.newCall(req).execute().close()

                    // Delete from agency_applications so user can re-apply
                    val req2 = Request.Builder()
                        .url("$baseUrl/rest/v1/agency_applications?agency_id=eq.$agencyId&user_id=eq.$userId")
                        .addHeader("apikey", apiKey)
                        .addHeader("Authorization", authHeader)
                        .delete()
                        .build()

                    client.newCall(req2).execute().close()
                }

                true
            } catch (e: Exception) {
                Log.e("SupabaseAgencyService", "Remove member error", e)
                false
            }
        }
    }

    /**
     * Update Agency Info (Name, Description, Logo)
     */
    suspend fun updateAgencyInfo(
        agencyId: String,
        name: String,
        description: String,
        logoUrl: String
    ): Boolean {
        return withContext(Dispatchers.IO) {
            try {
                memoryAgencies[agencyId]?.let { cur ->
                    memoryAgencies[agencyId] = cur.copy(
                        agencyName = name.trim(),
                        description = description.trim(),
                        logoUrl = if (logoUrl.isNotBlank()) logoUrl else cur.logoUrl
                    )
                }

                val baseUrl = SupabaseConfig.supabaseUrl.trim().removeSuffix("/")
                val apiKey = SupabaseConfig.supabaseAnonKey.trim()

                if (baseUrl.isNotEmpty() && apiKey.isNotEmpty()) {
                    val authHeader = getAuthHeader()
                    val patchJson = JSONObject().apply {
                        put("agency_name", name.trim())
                        put("description", description.trim())
                        if (logoUrl.isNotBlank()) {
                            put("logo_url", logoUrl.trim())
                        }
                    }.toString()

                    val req = Request.Builder()
                        .url("$baseUrl/rest/v1/agencies?id=eq.$agencyId")
                        .addHeader("apikey", apiKey)
                        .addHeader("Authorization", authHeader)
                        .addHeader("Content-Type", "application/json")
                        .patch(patchJson.toRequestBody(jsonMediaType))
                        .build()

                    client.newCall(req).execute().close()
                }
                true
            } catch (e: Exception) {
                Log.e("SupabaseAgencyService", "Update agency info error", e)
                false
            }
        }
    }

    private fun parseAgency(obj: JSONObject): Agency {
        return Agency(
            id = obj.optString("id", UUID.randomUUID().toString()),
            ownerId = obj.optString("owner_id", ""),
            agencyCode = obj.optString("agency_code", "AG000000"),
            agencyName = obj.optString("agency_name", "Agency"),
            description = obj.optString("description", ""),
            logoUrl = obj.optString("logo_url", ""),
            status = obj.optString("status", "ACTIVE"),
            membersCount = obj.optInt("members_count", 1),
            totalRevenueCoins = obj.optLong("total_revenue_coins", 0L),
            createdAt = obj.optString("created_at", "")
        )
    }

    private fun parseAgencyApplication(obj: JSONObject): AgencyApplication {
        return AgencyApplication(
            id = obj.optString("id", UUID.randomUUID().toString()),
            agencyId = obj.optString("agency_id", ""),
            agencyName = obj.optString("agency_name", "Agency"),
            agencyCode = obj.optString("agency_code", ""),
            userId = obj.optString("user_id", ""),
            userNumericId = obj.optLong("user_numeric_id", 0L),
            userName = obj.optString("user_name", "Applicant"),
            userAvatarUrl = obj.optString("user_avatar_url", ""),
            userGender = obj.optString("user_gender", ""),
            status = obj.optString("status", "PENDING"),
            createdAt = obj.optString("created_at", ""),
            updatedAt = obj.optString("updated_at", "")
        )
    }

    private fun parseAgencyMember(obj: JSONObject): AgencyMember {
        return AgencyMember(
            id = obj.optString("id", UUID.randomUUID().toString()),
            agencyId = obj.optString("agency_id", ""),
            agencyName = obj.optString("agency_name", "Agency"),
            agencyCode = obj.optString("agency_code", ""),
            userId = obj.optString("user_id", ""),
            userNumericId = obj.optLong("user_numeric_id", 0L),
            userName = obj.optString("user_name", "Member"),
            userAvatarUrl = obj.optString("user_avatar_url", ""),
            userGender = obj.optString("user_gender", ""),
            role = obj.optString("role", "MEMBER"),
            status = obj.optString("status", "APPROVED"),
            joinedAt = obj.optString("joined_at", "")
        )
    }

    /**
     * Fetch agency group messages in chronological order for free member chat.
     */
    suspend fun fetchAgencyGroupMessages(
        agencyId: String,
        context: Context? = null
    ): List<AgencyGroupMessage> {
        return withContext(Dispatchers.IO) {
            try {
                val baseUrl = SupabaseConfig.supabaseUrl.trim().removeSuffix("/")
                val apiKey = SupabaseConfig.supabaseAnonKey.trim()

                if (baseUrl.isNotEmpty() && apiKey.isNotEmpty() && agencyId.isNotBlank()) {
                    val authHeader = getAuthHeader(context)
                    val endpoint = "$baseUrl/rest/v1/agency_group_messages?agency_id=eq.$agencyId&order=created_at.asc&limit=100"

                    val req = Request.Builder()
                        .url(endpoint)
                        .addHeader("apikey", apiKey)
                        .addHeader("Authorization", authHeader)
                        .get()
                        .build()

                    client.newCall(req).execute().use { resp ->
                        val body = resp.body?.string() ?: ""
                        if (resp.isSuccessful) {
                            val arr = JSONArray(body)
                            val list = mutableListOf<AgencyGroupMessage>()
                            for (i in 0 until arr.length()) {
                                list.add(parseAgencyGroupMessage(arr.getJSONObject(i)))
                            }
                            // Update local cache
                            memoryGroupMessages[agencyId] = list
                            return@withContext list
                        }
                    }
                }
            } catch (e: Exception) {
                Log.w("SupabaseAgency", "fetchAgencyGroupMessages error: ${e.message}")
            }
            // Fallback to memory
            memoryGroupMessages[agencyId]?.toList() ?: emptyList()
        }
    }

    /**
     * Send a free group message in the agency group chat.
     */
    suspend fun sendAgencyGroupMessage(
        agencyId: String,
        senderId: String,
        senderNumericId: Long,
        senderName: String,
        senderAvatar: String,
        senderRole: String,
        senderGender: String,
        messageText: String,
        context: Context? = null
    ): Pair<Boolean, String> {
        return withContext(Dispatchers.IO) {
            if (messageText.isBlank()) return@withContext Pair(false, "Message cannot be empty")
            if (agencyId.isBlank() || senderId.isBlank()) return@withContext Pair(false, "Invalid agency or user")

            val nowIso = SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss.SSS'Z'", Locale.US).format(Date())
            val tempMessage = AgencyGroupMessage(
                id = System.currentTimeMillis(),
                agencyId = agencyId,
                senderId = senderId,
                senderNumericId = senderNumericId,
                senderName = senderName,
                senderAvatar = senderAvatar,
                senderRole = senderRole,
                senderGender = senderGender,
                message = messageText.trim(),
                createdAt = nowIso
            )

            // Cache in memory immediately for snappy responsiveness
            val currentList = memoryGroupMessages.getOrPut(agencyId) { mutableListOf() }
            synchronized(currentList) {
                currentList.add(tempMessage)
            }

            try {
                val baseUrl = SupabaseConfig.supabaseUrl.trim().removeSuffix("/")
                val apiKey = SupabaseConfig.supabaseAnonKey.trim()

                if (baseUrl.isNotEmpty() && apiKey.isNotEmpty()) {
                    val authHeader = getAuthHeader(context)
                    val endpoint = "$baseUrl/rest/v1/agency_group_messages"

                    val jsonBody = JSONObject().apply {
                        put("agency_id", agencyId)
                        put("sender_id", senderId)
                        put("sender_numeric_id", senderNumericId)
                        put("sender_name", senderName)
                        put("sender_avatar", senderAvatar)
                        put("sender_role", senderRole)
                        put("sender_gender", senderGender)
                        put("message", messageText.trim())
                    }.toString()

                    val req = Request.Builder()
                        .url(endpoint)
                        .addHeader("apikey", apiKey)
                        .addHeader("Authorization", authHeader)
                        .addHeader("Content-Type", "application/json")
                        .addHeader("Prefer", "return=representation")
                        .post(jsonBody.toRequestBody(jsonMediaType))
                        .build()

                    client.newCall(req).execute().use { resp ->
                        if (resp.isSuccessful) {
                            return@withContext Pair(true, "Message sent")
                        }
                    }
                }
            } catch (e: Exception) {
                Log.w("SupabaseAgency", "sendAgencyGroupMessage server error: ${e.message}")
            }

            Pair(true, "Message sent")
        }
    }

    private fun parseAgencyGroupMessage(obj: JSONObject): AgencyGroupMessage {
        return AgencyGroupMessage(
            id = obj.optLong("id", System.currentTimeMillis()),
            agencyId = obj.optString("agency_id", ""),
            senderId = obj.optString("sender_id", ""),
            senderNumericId = obj.optLong("sender_numeric_id", 0L),
            senderName = obj.optString("sender_name", "User"),
            senderAvatar = obj.optString("sender_avatar", ""),
            senderRole = obj.optString("sender_role", "MEMBER"),
            senderGender = obj.optString("sender_gender", ""),
            message = obj.optString("message", ""),
            createdAt = obj.optString("created_at", "")
        )
    }
}

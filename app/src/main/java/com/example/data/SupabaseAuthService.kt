package com.example.data
import com.example.ui.components.AppToast

import android.content.Context
import android.net.Uri
import android.util.Log
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import org.json.JSONArray
import org.json.JSONObject
import java.net.URLDecoder
import java.util.concurrent.TimeUnit
import kotlin.random.Random

sealed class AuthResult {
    data class Success(
        val userId: String,
        val email: String,
        val accessToken: String?,
        val refreshToken: String? = null,
        val expiresIn: Long = 3600L,
        val message: String,
        val isNewUser: Boolean = false
    ) : AuthResult()

    data class Error(val message: String) : AuthResult()
}

class SupabaseAuthService {
    private val client = OkHttpClient.Builder()
        .connectTimeout(15, TimeUnit.SECONDS)
        .readTimeout(15, TimeUnit.SECONDS)
        .writeTimeout(15, TimeUnit.SECONDS)
        .build()

    private val jsonMediaType = "application/json; charset=utf-8".toMediaType()

    companion object {
        const val OAUTH_REDIRECT_URL = "qivo://login"
        private val refreshLock = Any()
        private val syncClient = OkHttpClient.Builder()
            .connectTimeout(15, TimeUnit.SECONDS)
            .readTimeout(15, TimeUnit.SECONDS)
            .writeTimeout(15, TimeUnit.SECONDS)
            .build()
        private val syncJsonType = "application/json; charset=utf-8".toMediaType()

        /**
         * Thread-safe session refresh for use in interceptors, session headers, and background tasks.
         * Ensures non-blocking behavior if accidentally called on the Android Main / UI Thread.
         * 
         * @param failedToken The token that resulted in a 401. If another thread has already refreshed
         *                    the token to a new value, this function immediately returns the new token
         *                    without making redundant refresh calls (protects against Supabase single-use refresh token revocation).
         */
        fun refreshSessionSync(
            context: Context? = null,
            failedToken: String? = null,
            forceRefresh: Boolean = false
        ): String? {
            // Guard against NetworkOnMainThreadException: If invoked on Android Main Thread,
            // never execute blocking OkHttp network calls synchronously.
            if (android.os.Looper.myLooper() == android.os.Looper.getMainLooper()) {
                val currentToken = UserSessionManager.getAccessToken(context)
                if (forceRefresh || UserSessionManager.isTokenExpired(context)) {
                    kotlinx.coroutines.CoroutineScope(Dispatchers.IO).launch {
                        try {
                            refreshSessionSyncInternal(context, failedToken, forceRefresh)
                        } catch (_: Exception) {}
                    }
                }
                return currentToken.ifBlank { null }
            }

            return refreshSessionSyncInternal(context, failedToken, forceRefresh)
        }

        private fun refreshSessionSyncInternal(
            context: Context? = null,
            failedToken: String? = null,
            forceRefresh: Boolean = false
        ): String? {
            synchronized(refreshLock) {
                val currentToken = UserSessionManager.getAccessToken(context)

                // 1. If another thread already refreshed the token since this caller received a 401 on failedToken,
                // return the fresh token immediately! Calling Supabase with an already-rotated refresh token
                // would cause Supabase to revoke the user's session.
                if (!failedToken.isNullOrBlank() &&
                    currentToken.isNotBlank() &&
                    currentToken != failedToken &&
                    !UserSessionManager.isTokenExpired(context)
                ) {
                    return currentToken
                }

                // 2. If token is still valid and no forceRefresh was requested, return it immediately
                if (!forceRefresh && !UserSessionManager.isTokenExpired(context)) {
                    if (currentToken.isNotBlank()) return currentToken
                }

                val refreshToken = UserSessionManager.getRefreshToken(context)
                if (refreshToken.isBlank()) {
                    android.util.Log.w("SupabaseAuthService", "refreshSession: No refresh_token found in session.")
                    return null
                }

                val baseUrl = SupabaseConfig.supabaseUrl.trim().removeSuffix("/")
                val apiKey = SupabaseConfig.supabaseAnonKey.trim()
                if (baseUrl.isEmpty() || apiKey.isEmpty()) {
                    android.util.Log.w("SupabaseAuthService", "refreshSession: Missing SupabaseConfig baseUrl or apiKey.")
                    return null
                }

                try {
                    val endpoint = "$baseUrl/auth/v1/token?grant_type=refresh_token"
                    val jsonBody = JSONObject().apply {
                        put("refresh_token", refreshToken)
                    }.toString()

                    val request = Request.Builder()
                        .url(endpoint)
                        .addHeader("apikey", apiKey)
                        .addHeader("Authorization", "Bearer $apiKey")
                        .addHeader("Content-Type", "application/json")
                        .post(jsonBody.toRequestBody(syncJsonType))
                        .build()

                    syncClient.newCall(request).execute().use { response ->
                        val respStr = response.body?.string() ?: ""
                        if (response.isSuccessful) {
                            val json = JSONObject(respStr)
                            val newAccessToken = json.optString("access_token", "")
                            val newRefreshToken = json.optString("refresh_token", refreshToken)
                            val expiresIn = json.optLong("expires_in", 3600L)

                            if (newAccessToken.isNotBlank()) {
                                UserSessionManager.saveTokens(
                                    context = context,
                                    accessToken = newAccessToken,
                                    refreshToken = newRefreshToken,
                                    expiresInSeconds = expiresIn
                                )
                                android.util.Log.i("SupabaseAuthService", "Token refreshed successfully, expires in $expiresIn s")
                                return newAccessToken
                            }
                        } else {
                            android.util.Log.e("SupabaseAuthService", "Token refresh failed with HTTP ${response.code}: $respStr")
                        }
                    }
                } catch (e: Exception) {
                    android.util.Log.w("SupabaseAuthService", "refreshSessionSync error: ${e.message}")
                }
                return null
            }
        }
    }

    /**
     * Get the Supabase OAuth authorization URL for Google Sign-In.
     * Directs to Google auth with redirect back to qivo://login
     */
    fun getGoogleOAuthUrl(): String {
        val baseUrl = SupabaseConfig.supabaseUrl.trim().removeSuffix("/")
        val encodedRedirect = Uri.encode(OAUTH_REDIRECT_URL)
        return "$baseUrl/auth/v1/authorize?provider=google&redirect_to=$encodedRedirect"
    }

    private fun toAuthEmail(input: String): String {
        val trimmed = input.trim()
        if (trimmed.isBlank()) return trimmed
        if (trimmed.contains("+emailauth@")) return trimmed.lowercase()
        val parts = trimmed.split("@")
        if (parts.size == 2) {
            return "${parts[0]}+emailauth@${parts[1]}".lowercase()
        }
        return trimmed.lowercase()
    }

    suspend fun signIn(emailInput: String, passwordInput: String, context: Context? = null): AuthResult {
        return withContext(Dispatchers.IO) {
            try {
                val baseUrl = SupabaseConfig.supabaseUrl.trim().removeSuffix("/")
                val apiKey = SupabaseConfig.supabaseAnonKey.trim()

                if (baseUrl.isEmpty() || apiKey.isEmpty()) {
                    return@withContext AuthResult.Error("Configuration error. Please check your internet connection.")
                }

                val endpoint = "$baseUrl/auth/v1/token?grant_type=password"
                val authEmail = toAuthEmail(emailInput)
                val jsonBody = JSONObject().apply {
                    put("email", authEmail)
                    put("password", passwordInput)
                }.toString()

                val request = Request.Builder()
                    .url(endpoint)
                    .addHeader("apikey", apiKey)
                    .addHeader("Content-Type", "application/json")
                    .post(jsonBody.toRequestBody(jsonMediaType))
                    .build()

                client.newCall(request).execute().use { response ->
                    val responseBodyString = response.body?.string() ?: ""

                    if (response.isSuccessful) {
                        val json = JSONObject(responseBodyString)
                        val accessToken = json.optString("access_token", null)
                        val refreshToken = json.optString("refresh_token", null)
                        val expiresIn = json.optLong("expires_in", 3600L)
                        val userObj = json.optJSONObject("user")
                        val id = userObj?.optString("id") ?: json.optString("id", "user_id")

                        if (!accessToken.isNullOrBlank()) {
                            UserSessionManager.saveTokens(
                                context = context,
                                accessToken = accessToken,
                                refreshToken = refreshToken ?: "",
                                expiresInSeconds = expiresIn
                            )
                        }

                        AuthResult.Success(
                            userId = id,
                            email = emailInput.trim(),
                            accessToken = accessToken,
                            refreshToken = refreshToken,
                            expiresIn = expiresIn,
                            message = "Logged in successfully!"
                        )
                    } else {
                        val errorMessage = try {
                            val errJson = JSONObject(responseBodyString)
                            val desc = errJson.optString("error_description", "")
                            val msg = errJson.optString("msg", "")
                            val message = errJson.optString("message", "")
                            when {
                                desc.isNotEmpty() -> desc
                                msg.isNotEmpty() -> msg
                                message.isNotEmpty() -> message
                                else -> "Authentication failed (HTTP ${response.code})"
                            }
                        } catch (e: Exception) {
                            "Authentication failed (HTTP ${response.code})"
                        }

                        if (response.code == 400 && errorMessage.contains("Invalid login credentials", ignoreCase = true)) {
                            AuthResult.Error("Invalid login credentials. Check email & password.")
                        } else {
                            AuthResult.Error(errorMessage)
                        }
                    }
                }
            } catch (e: Exception) {
                val msg = e.localizedMessage ?: e.message ?: ""
                val friendly = if (msg.contains("host", ignoreCase = true) ||
                    msg.contains("resolve", ignoreCase = true) ||
                    msg.contains("network", ignoreCase = true) ||
                    msg.contains("connection", ignoreCase = true) ||
                    msg.contains("timeout", ignoreCase = true) ||
                    e is java.io.IOException
                ) {
                    "Network error, check your internet connection."
                } else {
                    "Network error, check your internet connection."
                }
                AuthResult.Error(friendly)
            }
        }
    }

    suspend fun signUp(emailInput: String, passwordInput: String, context: Context? = null): AuthResult {
        return withContext(Dispatchers.IO) {
            try {
                val baseUrl = SupabaseConfig.supabaseUrl.trim().removeSuffix("/")
                val apiKey = SupabaseConfig.supabaseAnonKey.trim()

                if (baseUrl.isEmpty() || apiKey.isEmpty()) {
                    return@withContext AuthResult.Error("Configuration error. Please check your internet connection.")
                }

                val endpoint = "$baseUrl/auth/v1/signup"
                val authEmail = toAuthEmail(emailInput)
                val qivoDeviceId = if (context != null) UserSessionManager.getStableDeviceId(context) else "unknown_device_id"

                val jsonBody = JSONObject().apply {
                    put("email", authEmail)
                    put("password", passwordInput)
                    put("data", JSONObject().apply {
                        put("qivo_device_id", qivoDeviceId)
                        put("device_id", qivoDeviceId)
                    })
                }.toString()

                val request = Request.Builder()
                    .url(endpoint)
                    .addHeader("apikey", apiKey)
                    .addHeader("Content-Type", "application/json")
                    .post(jsonBody.toRequestBody(jsonMediaType))
                    .build()

                client.newCall(request).execute().use { response ->
                    val responseBodyString = response.body?.string() ?: ""

                    if (response.isSuccessful) {
                        val json = JSONObject(responseBodyString)
                        val accessToken = json.optString("access_token", null)
                        val refreshToken = json.optString("refresh_token", null)
                        val expiresIn = json.optLong("expires_in", 3600L)
                        val userObj = json.optJSONObject("user")
                        val id = userObj?.optString("id") ?: json.optString("id", "user_id")

                        if (!accessToken.isNullOrBlank()) {
                            UserSessionManager.saveTokens(
                                context = context,
                                accessToken = accessToken,
                                refreshToken = refreshToken ?: "",
                                expiresInSeconds = expiresIn
                            )
                        }

                        // Enforce 1 account per device rule strictly
                        if (context != null && id.isNotBlank()) {
                            val deviceCheck = checkAndRegisterSignupDevice(context, id)
                            if (!deviceCheck.first) {
                                UserSessionManager.clearSession(context)
                                return@withContext AuthResult.Error(deviceCheck.second)
                            }
                        }

                        val confirmMsg = if (accessToken.isNullOrEmpty()) {
                            "Account created! Check your email inbox for confirmation link."
                        } else {
                            "Logged in successfully!"
                        }

                        AuthResult.Success(
                            userId = id,
                            email = emailInput.trim(),
                            accessToken = accessToken,
                            refreshToken = refreshToken,
                            expiresIn = expiresIn,
                            message = confirmMsg
                        )
                    } else {
                        val errorMessage = try {
                            val errJson = JSONObject(responseBodyString)
                            val desc = errJson.optString("error_description", "")
                            val msg = errJson.optString("msg", "")
                            val message = errJson.optString("message", "")
                            when {
                                desc.isNotEmpty() -> desc
                                msg.isNotEmpty() -> msg
                                message.isNotEmpty() -> message
                                else -> "Sign up failed (HTTP ${response.code})"
                            }
                        } catch (e: Exception) {
                            "Sign up failed (HTTP ${response.code})"
                        }

                        AuthResult.Error(errorMessage)
                    }
                }
            } catch (e: Exception) {
                val msg = e.localizedMessage ?: e.message ?: ""
                val friendly = if (msg.contains("host", ignoreCase = true) ||
                    msg.contains("resolve", ignoreCase = true) ||
                    msg.contains("network", ignoreCase = true) ||
                    msg.contains("connection", ignoreCase = true) ||
                    msg.contains("timeout", ignoreCase = true) ||
                    e is java.io.IOException
                ) {
                    "Network error, check your internet connection."
                } else {
                    "Network error, check your internet connection."
                }
                AuthResult.Error(friendly)
            }
        }
    }

    /**
     * Parse and handle OAuth deep link redirect (qivo://login#access_token=... or qivo://login?code=...).
     * Establishes a persistent Supabase session, fetches user profile, and initializes user in Supabase.
     */
    suspend fun handleOAuthCallback(uri: Uri, context: Context): AuthResult {
        return withContext(Dispatchers.IO) {
            try {
                val baseUrl = SupabaseConfig.supabaseUrl.trim().removeSuffix("/")
                val apiKey = SupabaseConfig.supabaseAnonKey.trim()

                // Parse parameters from fragment (#key=value) or query string (?key=value)
                val params = mutableMapOf<String, String>()

                // 1. Check Fragment
                val fragment = uri.fragment ?: uri.encodedFragment ?: ""
                if (fragment.isNotBlank()) {
                    fragment.split("&").forEach { pair ->
                        val parts = pair.split("=", limit = 2)
                        if (parts.size == 2) {
                            val key = URLDecoder.decode(parts[0], "UTF-8")
                            val value = URLDecoder.decode(parts[1], "UTF-8")
                            params[key] = value
                        }
                    }
                }

                // 2. Check Query Params
                uri.queryParameterNames.forEach { name ->
                    val value = uri.getQueryParameter(name)
                    if (!value.isNullOrBlank() && !params.containsKey(name)) {
                        params[name] = value
                    }
                }

                // Check for errors returned by provider or Supabase
                val error = params["error"] ?: params["error_code"]
                val errorDesc = params["error_description"] ?: params["error_msg"]
                if (!error.isNullOrBlank() || !errorDesc.isNullOrBlank()) {
                    val msg = errorDesc ?: error ?: "OAuth sign in was cancelled or failed"
                    return@withContext AuthResult.Error(msg)
                }

                var accessToken = params["access_token"]
                var refreshToken = params["refresh_token"]
                var expiresIn = params["expires_in"]?.toLongOrNull() ?: 3600L
                val authCode = params["code"]

                // If authorization code (PKCE) is present, exchange it for tokens
                if (accessToken.isNullOrBlank() && !authCode.isNullOrBlank()) {
                    val tokenEndpoint = "$baseUrl/auth/v1/token?grant_type=pkce"
                    val codeBody = JSONObject().apply {
                        put("auth_code", authCode)
                        put("code", authCode)
                    }.toString()

                    val tokenReq = Request.Builder()
                        .url(tokenEndpoint)
                        .addHeader("apikey", apiKey)
                        .addHeader("Content-Type", "application/json")
                        .post(codeBody.toRequestBody(jsonMediaType))
                        .build()

                    client.newCall(tokenReq).execute().use { tResp ->
                        val tBody = tResp.body?.string() ?: ""
                        if (tResp.isSuccessful) {
                            val tJson = JSONObject(tBody)
                            accessToken = tJson.optString("access_token", null)
                            refreshToken = tJson.optString("refresh_token", null)
                            expiresIn = tJson.optLong("expires_in", 3600L)
                        } else {
                            // Try authorization_code grant type fallback
                            val codeFallbackReq = Request.Builder()
                                .url("$baseUrl/auth/v1/token?grant_type=authorization_code")
                                .addHeader("apikey", apiKey)
                                .addHeader("Content-Type", "application/json")
                                .post(codeBody.toRequestBody(jsonMediaType))
                                .build()
                            client.newCall(codeFallbackReq).execute().use { fbResp ->
                                val fbBody = fbResp.body?.string() ?: ""
                                if (fbResp.isSuccessful) {
                                    val fbJson = JSONObject(fbBody)
                                    accessToken = fbJson.optString("access_token", null)
                                    refreshToken = fbJson.optString("refresh_token", null)
                                    expiresIn = fbJson.optLong("expires_in", 3600L)
                                }
                            }
                        }
                    }
                }

                if (accessToken.isNullOrBlank()) {
                    return@withContext AuthResult.Error("No valid access token received from Google Sign-In.")
                }

                // 3. Save tokens into session
                UserSessionManager.saveTokens(
                    context = context,
                    accessToken = accessToken!!,
                    refreshToken = refreshToken ?: "",
                    expiresInSeconds = expiresIn
                )

                // 4. Fetch authenticated user details from Supabase Auth
                val userReq = Request.Builder()
                    .url("$baseUrl/auth/v1/user")
                    .addHeader("apikey", apiKey)
                    .addHeader("Authorization", "Bearer $accessToken")
                    .get()
                    .build()

                var userId = ""
                var userEmail = ""
                var googleName = ""
                var googleAvatar = ""
                var createdAt = ""
                var lastSignInAt = ""

                client.newCall(userReq).execute().use { uResp ->
                    val uBody = uResp.body?.string() ?: ""
                    if (uResp.isSuccessful) {
                        val uJson = JSONObject(uBody)
                        userId = uJson.optString("id", "")
                        userEmail = uJson.optString("email", "")
                        createdAt = uJson.optString("created_at", "")
                        lastSignInAt = uJson.optString("last_sign_in_at", "")

                        val userMeta = uJson.optJSONObject("user_metadata")
                        if (userMeta != null) {
                            googleName = userMeta.optString("full_name", userMeta.optString("name", ""))
                            googleAvatar = userMeta.optString("avatar_url", userMeta.optString("picture", ""))
                        }
                    }
                }

                if (userId.isBlank()) {
                    return@withContext AuthResult.Error("Failed to fetch user profile from Supabase Auth.")
                }

                val finalEmail = userEmail.ifBlank { "google.user@qivo.app" }
                val fallbackName = if (googleName.isNotBlank()) googleName else finalEmail.substringBefore("@")

                // 5. Check or create Profile in Supabase "profiles" table
                val profileEndpoint = "$baseUrl/rest/v1/profiles?id=eq.$userId&select=*"
                val getProfileReq = Request.Builder()
                    .url(profileEndpoint)
                    .addHeader("apikey", apiKey)
                    .addHeader("Authorization", "Bearer $accessToken")
                    .get()
                    .build()

                var existingName = fallbackName
                var existingGender = ""
                var existingCountry = CountryDetector.detectCountry(context)?.first ?: "Kenya"
                var existingAvatar = googleAvatar
                var existingNumericId = 0L
                var existingCoins = 0L
                var existingIsAdmin = false
                var existingIsCoinSeller = false
                var profileExists = false
                var hasCompletedProfile = false
                var nameInDb = ""
                var avInDb = ""

                client.newCall(getProfileReq).execute().use { pResp ->
                    val pBody = pResp.body?.string() ?: ""
                    if (pResp.isSuccessful) {
                        val arr = JSONArray(pBody)
                        if (arr.length() > 0) {
                            profileExists = true
                            val pObj = arr.getJSONObject(0)
                            nameInDb = pObj.optString("name", "")
                            if (nameInDb.isNotBlank() && nameInDb != "QIVO User" && nameInDb != "User") {
                                existingName = nameInDb
                            }
                            val genderInDb = pObj.optString("gender", "").trim()
                            existingGender = genderInDb
                            val countryInDb = pObj.optString("country", "")
                            if (countryInDb.isNotBlank()) {
                                existingCountry = countryInDb
                            }
                            avInDb = pObj.optString("avatar_url", "")
                            if (avInDb.isNotBlank()) {
                                existingAvatar = avInDb
                            }
                            existingNumericId = pObj.optLong("numeric_id", 0L)
                            existingCoins = pObj.optLong("coins", 0L)
                            existingIsAdmin = pObj.optBoolean("is_admin", false)
                            existingIsCoinSeller = pObj.optBoolean("is_coin_seller", false)
                            val checkinDate = pObj.optString("last_checkin_date", "")
                            val checkinDay = pObj.optInt("last_checkin_day", 0)
                            if (checkinDate.isNotBlank()) {
                                UserSessionManager.saveDailyCheckIn(
                                    context = context,
                                    userId = userId,
                                    dateStr = checkinDate,
                                    dayNumber = checkinDay,
                                    email = finalEmail
                                )
                            }

                            val isBrandNew = isBrandNewAuth(createdAt, lastSignInAt)
                            val birthDateInDb = pObj.optString("birth_date", "")
                            val hasValidBirthDate = birthDateInDb.isNotBlank() && birthDateInDb != "2005-01-01"
                            val dbProfileCompleted = pObj.optBoolean("is_profile_completed", false)

                            val hasGender = genderInDb.equals("Male", ignoreCase = true) ||
                                genderInDb.equals("Female", ignoreCase = true)
                            val hasName = nameInDb.isNotBlank() && nameInDb != "QIVO User" && nameInDb != "User"
                            hasCompletedProfile = !isBrandNew && (dbProfileCompleted || (hasGender && hasName && hasValidBirthDate))
                        }
                    }
                }

                if (!profileExists) {
                    // Create new profile record in Supabase
                    val newNumericId = Random.nextLong(100_000L, 999_999_999L)
                    existingNumericId = newNumericId

                    val createBody = JSONObject().apply {
                        put("id", userId)
                        put("numeric_id", newNumericId)
                        put("email", finalEmail)
                        put("name", existingName)
                        put("gender", "") // Empty to require user to complete details
                        put("birth_date", "2005-01-01")
                        put("country", existingCountry)
                        put("avatar_url", existingAvatar)
                        put("coins", 0L)
                        put("diamonds", 0L)
                        put("is_admin", false)
                        put("is_coin_seller", false)
                        put("is_agent", false)
                        put("social_preferences", "Friendship & Discovery")
                    }.toString()

                    val createReq = Request.Builder()
                        .url("$baseUrl/rest/v1/profiles")
                        .addHeader("apikey", apiKey)
                        .addHeader("Authorization", "Bearer $accessToken")
                        .addHeader("Content-Type", "application/json")
                        .addHeader("Prefer", "resolution=merge-duplicates,return=representation")
                        .post(createBody.toRequestBody(jsonMediaType))
                        .build()

                    client.newCall(createReq).execute().close()
                    hasCompletedProfile = false
                }

                val isBrandNew = isBrandNewAuth(createdAt, lastSignInAt)
                val isNewUser = isBrandNew || !profileExists || !hasCompletedProfile
                val finalProfileCompleted = !isNewUser && hasCompletedProfile

                // 6. Save persistent session
                UserSessionManager.saveSession(
                    context = context,
                    email = finalEmail,
                    userId = userId,
                    name = existingName,
                    gender = if (finalProfileCompleted) existingGender else "",
                    country = existingCountry,
                    avatarUrl = if (finalProfileCompleted) existingAvatar else googleAvatar,
                    numericId = existingNumericId,
                    coins = existingCoins,
                    accessToken = accessToken,
                    refreshToken = refreshToken,
                    expiresInSeconds = expiresIn,
                    isAdmin = existingIsAdmin,
                    isCoinSeller = existingIsCoinSeller,
                    forceGender = !finalProfileCompleted,
                    isProfileCompleted = finalProfileCompleted
                )

                AuthResult.Success(
                    userId = userId,
                    email = finalEmail,
                    accessToken = accessToken,
                    refreshToken = refreshToken,
                    expiresIn = expiresIn,
                    message = if (isNewUser) "Google Sign-In successful. Please complete your profile." else "Google Sign-In successful!",
                    isNewUser = isNewUser
                )
            } catch (e: Exception) {
                AuthResult.Error("Google Sign-In failed: ${e.localizedMessage ?: e.message}")
            }
        }
    }

    private fun isBrandNewAuth(createdAt: String, lastSignInAt: String): Boolean {
        if (createdAt.isBlank()) return false
        if (lastSignInAt.isBlank()) return true
        if (createdAt == lastSignInAt) return true
        if (createdAt.take(16) == lastSignInAt.take(16)) return true
        try {
            val sdf = java.text.SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss", java.util.Locale.US).apply {
                timeZone = java.util.TimeZone.getTimeZone("UTC")
            }
            val cDate = sdf.parse(createdAt.substringBefore("."))
            val lDate = sdf.parse(lastSignInAt.substringBefore("."))
            if (cDate != null && lDate != null) {
                val diffSeconds = Math.abs(lDate.time - cDate.time) / 1000L
                if (diffSeconds <= 120L) return true
            }
        } catch (_: Exception) {}
        return false
    }

    /**
     * Authenticate with Supabase Auth using native Google ID Token obtained from Credential Manager
     */
    suspend fun signInWithGoogleIdToken(
        idToken: String,
        context: Context,
        fallbackGoogleName: String = "",
        fallbackGoogleAvatar: String = "",
        fallbackGoogleEmail: String = ""
    ): AuthResult {
        return withContext(Dispatchers.IO) {
            try {
                val baseUrl = SupabaseConfig.supabaseUrl.trim().removeSuffix("/")
                val apiKey = SupabaseConfig.supabaseAnonKey.trim()

                if (baseUrl.isEmpty() || apiKey.isEmpty()) {
                    return@withContext AuthResult.Error("Configuration error. Please check your network connection.")
                }

                val endpoint = "$baseUrl/auth/v1/token?grant_type=id_token"
                val jsonBody = JSONObject().apply {
                    put("provider", "google")
                    put("id_token", idToken)
                }.toString()

                val request = Request.Builder()
                    .url(endpoint)
                    .addHeader("apikey", apiKey)
                    .addHeader("Content-Type", "application/json")
                    .post(jsonBody.toRequestBody(jsonMediaType))
                    .build()

                client.newCall(request).execute().use { response ->
                    val responseBodyString = response.body?.string() ?: ""

                    if (response.isSuccessful) {
                        val json = JSONObject(responseBodyString)
                        val accessToken = json.optString("access_token", null)
                        val refreshToken = json.optString("refresh_token", null)
                        val expiresIn = json.optLong("expires_in", 3600L)
                        val userObj = json.optJSONObject("user")
                        val id = userObj?.optString("id") ?: json.optString("id", "user_id")
                        val email = userObj?.optString("email") ?: fallbackGoogleEmail
                        val createdAt = userObj?.optString("created_at", "") ?: ""
                        val lastSignInAt = userObj?.optString("last_sign_in_at", "") ?: ""

                        var googleName = fallbackGoogleName
                        var googleAvatar = fallbackGoogleAvatar
                        val userMeta = userObj?.optJSONObject("user_metadata")
                        if (userMeta != null) {
                            val metaName = userMeta.optString("full_name", userMeta.optString("name", ""))
                            if (metaName.isNotBlank()) googleName = metaName
                            val metaAvatar = userMeta.optString("avatar_url", userMeta.optString("picture", ""))
                            if (metaAvatar.isNotBlank()) googleAvatar = metaAvatar
                        }

                        if (!accessToken.isNullOrBlank()) {
                            UserSessionManager.saveTokens(
                                context = context,
                                accessToken = accessToken,
                                refreshToken = refreshToken ?: "",
                                expiresInSeconds = expiresIn
                            )
                        }

                        val finalEmail = when {
                            email.isNotBlank() -> email
                            fallbackGoogleEmail.isNotBlank() -> fallbackGoogleEmail
                            else -> "google.user@qivo.app"
                        }
                        val fallbackName = when {
                            googleName.isNotBlank() -> googleName
                            fallbackGoogleName.isNotBlank() -> fallbackGoogleName
                            finalEmail.contains("@") -> finalEmail.substringBefore("@")
                            else -> "User"
                        }

                        // Verify / load Profile in Supabase profiles table
                        val profileEndpoint = "$baseUrl/rest/v1/profiles?id=eq.$id&select=*"
                        val getProfileReq = Request.Builder()
                            .url(profileEndpoint)
                            .addHeader("apikey", apiKey)
                            .addHeader("Authorization", "Bearer $accessToken")
                            .get()
                            .build()

                        var existingName = fallbackName
                        var existingGender = ""
                        var existingCountry = CountryDetector.detectCountry(context)?.first ?: "Kenya"
                        var existingAvatar = if (googleAvatar.isNotBlank()) googleAvatar else fallbackGoogleAvatar
                        var existingNumericId = 0L
                        var existingCoins = 0L
                        var existingIsAdmin = false
                        var existingIsCoinSeller = false
                        var profileExists = false
                        var hasCompletedProfile = false
                        var nameInDb = ""
                        var avInDb = ""

                        client.newCall(getProfileReq).execute().use { pResp ->
                            val pBody = pResp.body?.string() ?: ""
                            if (pResp.isSuccessful) {
                                val arr = JSONArray(pBody)
                                if (arr.length() > 0) {
                                    profileExists = true
                                    val pObj = arr.getJSONObject(0)
                                    nameInDb = pObj.optString("name", "")
                                    if (nameInDb.isNotBlank() && nameInDb != "QIVO User" && nameInDb != "User") {
                                        existingName = nameInDb
                                    }
                                    val genderInDb = pObj.optString("gender", "").trim()
                                    existingGender = genderInDb
                                    val countryInDb = pObj.optString("country", "")
                                    if (countryInDb.isNotBlank()) {
                                        existingCountry = countryInDb
                                    }
                                    avInDb = pObj.optString("avatar_url", "")
                                    if (avInDb.isNotBlank()) {
                                        existingAvatar = avInDb
                                    }
                                    existingNumericId = pObj.optLong("numeric_id", 0L)
                                    existingCoins = pObj.optLong("coins", 0L)
                                    existingIsAdmin = pObj.optBoolean("is_admin", false)
                                    existingIsCoinSeller = pObj.optBoolean("is_coin_seller", false)
                                    val checkinDate = pObj.optString("last_checkin_date", "")
                                    val checkinDay = pObj.optInt("last_checkin_day", 0)
                                    if (checkinDate.isNotBlank()) {
                                        UserSessionManager.saveDailyCheckIn(
                                            context = context,
                                            userId = id,
                                            dateStr = checkinDate,
                                            dayNumber = checkinDay,
                                            email = finalEmail
                                        )
                                    }

                                    val isBrandNew = isBrandNewAuth(createdAt, lastSignInAt)
                                    val birthDateInDb = pObj.optString("birth_date", "")
                                    val hasValidBirthDate = birthDateInDb.isNotBlank() && birthDateInDb != "2005-01-01"
                                    val dbProfileCompleted = pObj.optBoolean("is_profile_completed", false)

                                    // Profile is completed only if user has selected Male or Female and has valid name and valid birth date (not brand new auth)
                                    val hasGender = genderInDb.equals("Male", ignoreCase = true) ||
                                        genderInDb.equals("Female", ignoreCase = true)
                                    val hasName = nameInDb.isNotBlank() && nameInDb != "QIVO User" && nameInDb != "User"
                                    hasCompletedProfile = !isBrandNew && (dbProfileCompleted || (hasGender && hasName && hasValidBirthDate))
                                }
                            }
                        }

                        if (!profileExists) {
                            val newNumericId = Random.nextLong(100_000L, 999_999_999L)
                            existingNumericId = newNumericId

                            val createBody = JSONObject().apply {
                                put("id", id)
                                put("numeric_id", newNumericId)
                                put("email", finalEmail)
                                put("name", existingName)
                                put("gender", "") // Empty to require user to complete details
                                put("birth_date", "2005-01-01")
                                put("country", existingCountry)
                                put("avatar_url", existingAvatar)
                                put("coins", 0L)
                                put("diamonds", 0L)
                                put("is_admin", false)
                                put("is_coin_seller", false)
                                put("is_agent", false)
                                put("social_preferences", "Friendship & Discovery")
                            }.toString()

                            val createReq = Request.Builder()
                                .url("$baseUrl/rest/v1/profiles")
                                .addHeader("apikey", apiKey)
                                .addHeader("Authorization", "Bearer $accessToken")
                                .addHeader("Content-Type", "application/json")
                                .addHeader("Prefer", "resolution=merge-duplicates,return=representation")
                                .post(createBody.toRequestBody(jsonMediaType))
                                .build()

                            client.newCall(createReq).execute().close()
                            hasCompletedProfile = false
                        }

                        val isBrandNew = isBrandNewAuth(createdAt, lastSignInAt)
                        val isNewUser = isBrandNew || !profileExists || !hasCompletedProfile
                        val finalProfileCompleted = !isNewUser && hasCompletedProfile

                        UserSessionManager.saveSession(
                            context = context,
                            email = finalEmail,
                            userId = id,
                            name = existingName,
                            gender = if (finalProfileCompleted) existingGender else "",
                            country = existingCountry,
                            avatarUrl = if (finalProfileCompleted) existingAvatar else googleAvatar,
                            numericId = existingNumericId,
                            coins = existingCoins,
                            accessToken = accessToken,
                            refreshToken = refreshToken,
                            expiresInSeconds = expiresIn,
                            isAdmin = existingIsAdmin,
                            isCoinSeller = existingIsCoinSeller,
                            forceGender = !finalProfileCompleted,
                            isProfileCompleted = finalProfileCompleted
                        )

                        AuthResult.Success(
                            userId = id,
                            email = finalEmail,
                            accessToken = accessToken,
                            refreshToken = refreshToken,
                            expiresIn = expiresIn,
                            message = if (isNewUser) "Google Sign-In successful. Please complete your profile." else "Google Sign-In successful!",
                            isNewUser = isNewUser
                        )
                    } else {
                        val errMsg = try {
                            val errJson = JSONObject(responseBodyString)
                            errJson.optString("error_description", errJson.optString("msg", errJson.optString("message", "Google Sign-In failed (HTTP ${response.code})")))
                        } catch (e: Exception) {
                            "Google Sign-In failed (HTTP ${response.code})"
                        }
                        AuthResult.Error(errMsg)
                    }
                }
            } catch (e: Exception) {
                AuthResult.Error("Google Sign-In error: ${e.localizedMessage ?: e.message}")
            }
        }
    }

    /**
     * Refresh an existing Supabase JWT session using refresh token
     */
    suspend fun refreshSession(context: Context? = null): AuthResult {
        return withContext(Dispatchers.IO) {
            val token = refreshSessionSync(context, forceRefresh = true)
            if (!token.isNullOrBlank()) {
                val session = UserSessionManager.getSession(context)
                AuthResult.Success(
                    userId = session?.userId ?: "",
                    email = session?.email ?: "",
                    accessToken = token,
                    refreshToken = UserSessionManager.getRefreshToken(context),
                    expiresIn = 3600L,
                    message = "Session refreshed"
                )
            } else {
                AuthResult.Error("Session refresh failed")
            }
        }
    }

    /**
     * Ensures an active, unexpired access token is available. If expired, attempts token refresh.
     */
    suspend fun ensureValidToken(context: Context? = null): String {
        return withContext(Dispatchers.IO) {
            UserSessionManager.getValidAccessToken(context)
        }
    }

    /**
     * Get the Authorization header value ("Bearer <token>").
     */
    suspend fun getAuthorizationHeader(context: Context? = null): String {
        return withContext(Dispatchers.IO) {
            UserSessionManager.getAuthHeader(context)
        }
    }

    /**
     * Permanent Zero-Trace Account Deletion.
     * Invokes server-side delete_user_account RPC, purges all user data across all tables,
     * terminates the auth session, and completely wipes local storage and caches.
     */
    suspend fun deleteAccount(context: Context): Pair<Boolean, String> {
        return withContext(Dispatchers.IO) {
            try {
                val baseUrl = SupabaseConfig.supabaseUrl.trim().removeSuffix("/")
                val apiKey = SupabaseConfig.supabaseAnonKey.trim()
                val authHeader = UserSessionManager.getAuthHeader(context)

                if (baseUrl.isBlank() || authHeader.isBlank()) {
                    return@withContext Pair(false, "Authentication required to delete account.")
                }

                // Android must call ONLY: POST /rest/v1/rpc/delete_user_account
                val rpcUrl = "$baseUrl/rest/v1/rpc/delete_user_account"
                val req = Request.Builder()
                    .url(rpcUrl)
                    .addHeader("apikey", apiKey)
                    .addHeader("Authorization", authHeader)
                    .addHeader("Content-Type", "application/json")
                    .post("{}".toRequestBody(jsonMediaType))
                    .build()

                client.newCall(req).execute().use { response ->
                    val isSuccess = response.isSuccessful || response.code in 200..204
                    if (isSuccess) {
                        // On success: clear local cache/session and disconnect Realtime
                        try {
                            ChatStateHolder.disconnect()
                            wipeLocalUserData(context)
                        } catch (_: Exception) {}
                        return@withContext Pair(true, "Account successfully deleted.")
                    } else {
                        val errBody = response.body?.string() ?: ""
                        return@withContext Pair(false, "Server account deletion failed (HTTP ${response.code}): $errBody")
                    }
                }
            } catch (e: Exception) {
                Pair(false, "Account deletion error: ${e.message ?: "Unknown error"}")
            }
        }
    }

    private fun wipeLocalUserData(context: Context) {
        try {
            UserSessionManager.clearSession(context)
        } catch (_: Exception) {}

        try {
            val prefNames = listOf(
                "qivo_user_session",
                "qivo_app_data_cache",
                "country_detector_cache",
                "view_once_prefs",
                "app_cache_prefs"
            )
            for (p in prefNames) {
                context.getSharedPreferences(p, Context.MODE_PRIVATE).edit().clear().apply()
            }
        } catch (_: Exception) {}

        try {
            context.cacheDir.deleteRecursively()
        } catch (_: Exception) {}
    }

    /**
     * Links a Fast Login guest account to a real Email and Password.
     */
    suspend fun linkFastAccountToEmail(
        userId: String,
        newEmail: String,
        newPassword: String,
        context: Context? = null
    ): AuthResult {
        return withContext(Dispatchers.IO) {
            try {
                val cleanEmail = newEmail.trim()
                if (!android.util.Patterns.EMAIL_ADDRESS.matcher(cleanEmail).matches()) {
                    return@withContext AuthResult.Error("Please enter a valid email address.")
                }
                if (newPassword.trim().length < 6) {
                    return@withContext AuthResult.Error("Password must be at least 6 characters long.")
                }

                // 1. Synchronize profile email on Supabase DB REST API
                val baseUrl = SupabaseConfig.supabaseUrl.trim().removeSuffix("/")
                val apiKey = SupabaseConfig.supabaseAnonKey.trim()
                if (baseUrl.isNotBlank() && apiKey.isNotBlank() && userId.isNotBlank()) {
                    val updateObj = JSONObject().apply {
                        put("email", cleanEmail)
                    }.toString()

                    val token = UserSessionManager.getValidAccessToken(context)
                    val authHeader = if (token.isNotBlank()) "Bearer $token" else "Bearer $apiKey"
                    val patchReq = Request.Builder()
                        .url("$baseUrl/rest/v1/profiles?id=eq.${userId.trim()}")
                        .addHeader("apikey", apiKey)
                        .addHeader("Authorization", authHeader)
                        .addHeader("Content-Type", "application/json")
                        .addHeader("Prefer", "return=minimal")
                        .patch(updateObj.toRequestBody(jsonMediaType))
                        .build()

                    client.newCall(patchReq).execute().close()
                }

                // 2. Register email & password account credentials
                signUp(cleanEmail, newPassword, context)

                // 3. Save linked email and clear Fast Login flag in local session
                if (context != null) {
                    UserSessionManager.saveSession(
                        context = context,
                        email = cleanEmail,
                        userId = userId
                    )
                    UserSessionManager.setIsFastLoginAccount(context, false)
                }

                AuthResult.Success(
                    userId = userId,
                    email = cleanEmail,
                    accessToken = null,
                    message = "Account successfully linked to $cleanEmail!"
                )
            } catch (e: Exception) {
                AuthResult.Error(e.message ?: "Failed to link email account.")
            }
        }
    }

    /**
     * Secure sign out from Supabase Auth and clear local session.
     */
    suspend fun signOut(context: Context) {
        withContext(Dispatchers.IO) {
            try {
                val baseUrl = SupabaseConfig.supabaseUrl.trim().removeSuffix("/")
                val apiKey = SupabaseConfig.supabaseAnonKey.trim()
                val token = UserSessionManager.getAccessToken(context)

                if (baseUrl.isNotBlank() && token.isNotBlank()) {
                    val req = Request.Builder()
                        .url("$baseUrl/auth/v1/logout")
                        .addHeader("apikey", apiKey)
                        .addHeader("Authorization", "Bearer $token")
                        .post("{}".toRequestBody(jsonMediaType))
                        .build()
                    client.newCall(req).execute().close()
                }
            } catch (_: Exception) {
            } finally {
                UserSessionManager.clearSession(context)
            }
        }
    }

    /**
     * Sends a 6-digit OTP code to the user's email address (sent via Brevo / SMTP).
     */
    suspend fun signInWithOtp(emailInput: String): AuthResult {
        return withContext(Dispatchers.IO) {
            try {
                val baseUrl = SupabaseConfig.supabaseUrl.trim().removeSuffix("/")
                val apiKey = SupabaseConfig.supabaseAnonKey.trim()
                val email = emailInput.trim()
                if (email.isBlank() || !android.util.Patterns.EMAIL_ADDRESS.matcher(email).matches()) {
                    return@withContext AuthResult.Error("Please enter a valid email address.")
                }

                val jsonBody = JSONObject().apply {
                    put("email", email)
                    put("create_user", true)
                }

                val req = Request.Builder()
                    .url("$baseUrl/auth/v1/otp")
                    .addHeader("apikey", apiKey)
                    .addHeader("Content-Type", "application/json")
                    .post(jsonBody.toString().toRequestBody(jsonMediaType))
                    .build()

                val resp = client.newCall(req).execute()
                val respBody = resp.body?.string() ?: ""
                if (resp.isSuccessful) {
                    AuthResult.Success(userId = "", email = email, accessToken = null, message = "6-digit verification code sent to $email!")
                } else {
                    val errMsg = try { JSONObject(respBody).optString("error_description", JSONObject(respBody).optString("msg", "Failed to send verification code.")) } catch (_: Exception) { "Failed to send verification code." }
                    AuthResult.Error(errMsg)
                }
            } catch (e: Exception) {
                AuthResult.Error(e.message ?: "Network error sending verification code.")
            }
        }
    }

    /**
     * Verifies the 6-digit OTP code entered by the user.
     */
    suspend fun verifyOtp(emailInput: String, tokenInput: String, context: Context? = null): AuthResult {
        return withContext(Dispatchers.IO) {
            try {
                val baseUrl = SupabaseConfig.supabaseUrl.trim().removeSuffix("/")
                val apiKey = SupabaseConfig.supabaseAnonKey.trim()
                val email = emailInput.trim()
                val token = tokenInput.trim()

                if (email.isBlank() || token.length != 6) {
                    return@withContext AuthResult.Error("Please enter a valid 6-digit code.")
                }

                val jsonBody = JSONObject().apply {
                    put("email", email)
                    put("token", token)
                    put("type", "email")
                }

                val req = Request.Builder()
                    .url("$baseUrl/auth/v1/verify")
                    .addHeader("apikey", apiKey)
                    .addHeader("Content-Type", "application/json")
                    .post(jsonBody.toString().toRequestBody(jsonMediaType))
                    .build()

                val resp = client.newCall(req).execute()
                val respBody = resp.body?.string() ?: ""
                if (resp.isSuccessful) {
                    val json = JSONObject(respBody)
                    val accessToken = json.optString("access_token")
                    val refreshToken = json.optString("refresh_token")
                    val expiresIn = json.optLong("expires_in", 3600L)
                    val userObj = json.optJSONObject("user")
                    val userId = userObj?.optString("id") ?: ""

                    if (context != null && userId.isNotBlank()) {
                        val deviceCheck = checkAndRegisterSignupDevice(context, userId)
                        if (!deviceCheck.first) {
                            UserSessionManager.clearSession(context)
                            return@withContext AuthResult.Error(deviceCheck.second)
                        }
                    }

                    AuthResult.Success(
                        userId = userId,
                        email = email,
                        accessToken = accessToken.ifBlank { null },
                        refreshToken = refreshToken.ifBlank { null },
                        expiresIn = expiresIn,
                        message = "Email successfully verified!"
                    )
                } else {
                    val errMsg = try { JSONObject(respBody).optString("error_description", JSONObject(respBody).optString("msg", "Invalid verification code.")) } catch (_: Exception) { "Invalid verification code." }
                    AuthResult.Error(errMsg)
                }
            } catch (e: Exception) {
                AuthResult.Error(e.message ?: "Network error verifying code.")
            }
        }
    }


    /**
     * Updates the user password via Supabase Auth PUT /auth/v1/user
     */
    suspend fun updateUserPassword(newPassword: String, accessTokenOverride: String? = null, context: Context? = null): AuthResult {
        return withContext(Dispatchers.IO) {
            try {
                val baseUrl = SupabaseConfig.supabaseUrl.trim().removeSuffix("/")
                val apiKey = SupabaseConfig.supabaseAnonKey.trim()
                val token = accessTokenOverride?.ifBlank { null }
                    ?: (if (context != null) UserSessionManager.getAccessToken(context) else null)
                    ?: ensureValidToken(context)
                if (token.isBlank()) {
                    return@withContext AuthResult.Error("No active authentication session found. Please verify your OTP code first.")
                }
                val jsonBody = JSONObject().apply {
                    put("password", newPassword)
                }
                val req = Request.Builder()
                    .url("$baseUrl/auth/v1/user")
                    .addHeader("apikey", apiKey)
                    .addHeader("Authorization", "Bearer $token")
                    .addHeader("Content-Type", "application/json")
                    .put(jsonBody.toString().toRequestBody(jsonMediaType))
                    .build()
                val resp = client.newCall(req).execute()
                val respBody = resp.body?.string() ?: ""
                if (resp.isSuccessful) {
                    val json = JSONObject(respBody)
                    val userId = json.optString("id")
                    val email = json.optString("email")
                    AuthResult.Success(
                        userId = userId,
                        email = email,
                        accessToken = token,
                        message = "Password set successfully!"
                    )
                } else {
                    val errMsg = try {
                        val j = JSONObject(respBody)
                        j.optString("error_description", j.optString("msg", j.optString("message", "Failed to update password.")))
                    } catch (_: Exception) {
                        "Failed to update password."
                    }
                    AuthResult.Error(errMsg)
                }
            } catch (e: Exception) {
                AuthResult.Error(e.message ?: "Network error updating password.")
            }
        }
    }


    /**
     * Creates or signs in a real authenticated Supabase account for Fast Login.
     */
    
    /**
     * Performs a real Supabase anonymous authenticated login to obtain a valid access token.
     */
    suspend fun signInAnonymously(context: Context): AuthResult {
        return withContext(Dispatchers.IO) {
            try {
                val baseUrl = SupabaseConfig.supabaseUrl.trim().removeSuffix("/")
                val apiKey = SupabaseConfig.supabaseAnonKey.trim()
                if (baseUrl.isEmpty() || apiKey.isEmpty()) {
                    return@withContext AuthResult.Error("Configuration error. Please check your internet connection.")
                }

                val endpoint = "$baseUrl/auth/v1/signup"
                val request = Request.Builder()
                    .url(endpoint)
                    .addHeader("apikey", apiKey)
                    .addHeader("Content-Type", "application/json")
                    .post("{}".toRequestBody(jsonMediaType))
                    .build()

                client.newCall(request).execute().use { response ->
                    val responseBodyString = response.body?.string() ?: ""
                    if (response.isSuccessful) {
                        val json = JSONObject(responseBodyString)
                        val accessToken = json.optString("access_token", null)
                        val refreshToken = json.optString("refresh_token", null)
                        val expiresIn = json.optLong("expires_in", 3600L)
                        val userObj = json.optJSONObject("user")
                        val id = userObj?.optString("id") ?: json.optString("id", "")

                        if (!accessToken.isNullOrBlank()) {
                            UserSessionManager.saveTokens(
                                context = context,
                                accessToken = accessToken,
                                refreshToken = refreshToken ?: "",
                                expiresInSeconds = expiresIn
                            )
                        }

                        AuthResult.Success(
                            userId = id,
                            email = "anonymous@qivo.app",
                            accessToken = accessToken,
                            refreshToken = refreshToken,
                            expiresIn = expiresIn,
                            message = "Logged in anonymously!"
                        )
                    } else {
                        AuthResult.Error("Anonymous signup failed (HTTP ${response.code})")
                    }
                }
            } catch (e: Exception) {
                AuthResult.Error(e.message ?: "Network error during anonymous signup.")
            }
        }
    }

    /**
     * Enforces strict 1-account-per-device policy.
     * Registers device ID in server registry; rejects account creation if another account was already registered on this device.
     */
    suspend fun checkAndRegisterSignupDevice(context: Context, userId: String): Pair<Boolean, String> {
        return withContext(Dispatchers.IO) {
            try {
                val baseUrl = SupabaseConfig.supabaseUrl.trim().removeSuffix("/")
                val apiKey = SupabaseConfig.supabaseAnonKey.trim()
                if (baseUrl.isBlank() || apiKey.isBlank()) return@withContext Pair(true, "")

                val qivoDeviceId = UserSessionManager.getStableDeviceId(context)
                if (qivoDeviceId.isBlank()) return@withContext Pair(true, "")

                val token = UserSessionManager.getAccessToken(context)
                val authHeader = if (token.isNotBlank()) "Bearer $token" else "Bearer $apiKey"

                val rpcUrl = "$baseUrl/rest/v1/rpc/check_or_register_signup_device"
                val payload = JSONObject().apply {
                    put("qivo_device_id", qivoDeviceId)
                    put("p_qivo_device_id", qivoDeviceId)
                    put("p_device_id", qivoDeviceId)
                    put("p_user_id", userId)
                }.toString()

                val request = Request.Builder()
                    .url(rpcUrl)
                    .addHeader("apikey", apiKey)
                    .addHeader("Authorization", authHeader)
                    .addHeader("Content-Type", "application/json")
                    .post(payload.toRequestBody(jsonMediaType))
                    .build()

                client.newCall(request).execute().use { resp ->
                    val bodyStr = resp.body?.string() ?: ""
                    Log.d("SupabaseAuth", "check_or_register_signup_device code=${resp.code}, body=$bodyStr")

                    if (resp.isSuccessful) {
                        try {
                            if (bodyStr.trim().startsWith("{")) {
                                val json = JSONObject(bodyStr)
                                val success = json.optBoolean("success", true)
                                val code = json.optString("code", json.optString("error", ""))
                                val msg = json.optString("message", "Only one account per device is allowed. A QIVO account has already been created on this device.")
                                if (!success || code.contains("EXISTS", ignoreCase = true) || code.contains("DEVICE", ignoreCase = true)) {
                                    return@withContext Pair(false, msg)
                                }
                            }
                        } catch (_: Exception) {}
                        Pair(true, "")
                    } else if (resp.code == 400 || resp.code == 403 || resp.code == 409) {
                        var errMsg = "Only one account per device is allowed. A QIVO account has already been created on this device."
                        try {
                            if (bodyStr.trim().startsWith("{")) {
                                val errJson = JSONObject(bodyStr)
                                val msg = errJson.optString("message", errJson.optString("msg", errJson.optString("error_description", "")))
                                if (msg.isNotBlank()) errMsg = msg
                            }
                        } catch (_: Exception) {}
                        Pair(false, errMsg)
                    } else {
                        Pair(true, "")
                    }
                }
            } catch (e: Exception) {
                Pair(true, "")
            }
        }
    }
}


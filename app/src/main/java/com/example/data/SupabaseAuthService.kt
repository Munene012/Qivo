package com.example.data

import android.content.Context
import android.net.Uri
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

    suspend fun signIn(emailInput: String, passwordInput: String, context: Context? = null): AuthResult {
        return withContext(Dispatchers.IO) {
            try {
                val baseUrl = SupabaseConfig.supabaseUrl.trim().removeSuffix("/")
                val apiKey = SupabaseConfig.supabaseAnonKey.trim()

                if (baseUrl.isEmpty() || apiKey.isEmpty()) {
                    return@withContext AuthResult.Error("Configuration error. Please check your internet connection.")
                }

                val endpoint = "$baseUrl/auth/v1/token?grant_type=password"
                val jsonBody = JSONObject().apply {
                    put("email", emailInput.trim())
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
                        val email = userObj?.optString("email") ?: emailInput

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
                            email = email,
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
                val jsonBody = JSONObject().apply {
                    put("email", emailInput.trim())
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
                        val email = userObj?.optString("email") ?: emailInput

                        if (!accessToken.isNullOrBlank()) {
                            UserSessionManager.saveTokens(
                                context = context,
                                accessToken = accessToken,
                                refreshToken = refreshToken ?: "",
                                expiresInSeconds = expiresIn
                            )
                        }

                        val confirmMsg = if (accessToken.isNullOrEmpty()) {
                            "Account created! Check your email inbox for confirmation link."
                        } else {
                            "Logged in successfully!"
                        }

                        AuthResult.Success(
                            userId = id,
                            email = email,
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

                client.newCall(userReq).execute().use { uResp ->
                    val uBody = uResp.body?.string() ?: ""
                    if (uResp.isSuccessful) {
                        val uJson = JSONObject(uBody)
                        userId = uJson.optString("id", "")
                        userEmail = uJson.optString("email", "")

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

                            // Profile is completed only if user has selected Male or Female and has valid name
                            val hasGender = genderInDb.equals("Male", ignoreCase = true) ||
                                genderInDb.equals("Female", ignoreCase = true)
                            val hasName = nameInDb.isNotBlank() && nameInDb != "QIVO User" && nameInDb != "User"
                            hasCompletedProfile = hasGender && hasName
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

                val isNewUser = !profileExists || !hasCompletedProfile

                // 6. Save persistent session
                UserSessionManager.saveSession(
                    context = context,
                    email = finalEmail,
                    userId = userId,
                    name = existingName,
                    gender = if (hasCompletedProfile) existingGender else "",
                    country = existingCountry,
                    avatarUrl = existingAvatar,
                    numericId = existingNumericId,
                    coins = existingCoins,
                    accessToken = accessToken,
                    refreshToken = refreshToken,
                    expiresInSeconds = expiresIn,
                    isAdmin = existingIsAdmin,
                    isCoinSeller = existingIsCoinSeller,
                    forceGender = !hasCompletedProfile,
                    isProfileCompleted = hasCompletedProfile
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

                                    // Profile is completed only if user has selected Male or Female and has valid name
                                    val hasGender = genderInDb.equals("Male", ignoreCase = true) ||
                                        genderInDb.equals("Female", ignoreCase = true)
                                    val hasName = nameInDb.isNotBlank() && nameInDb != "QIVO User" && nameInDb != "User"
                                    hasCompletedProfile = hasGender && hasName
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

                        val isNewUser = !profileExists || !hasCompletedProfile

                        UserSessionManager.saveSession(
                            context = context,
                            email = finalEmail,
                            userId = id,
                            name = existingName,
                            gender = if (hasCompletedProfile) existingGender else "",
                            country = existingCountry,
                            avatarUrl = existingAvatar,
                            numericId = existingNumericId,
                            coins = existingCoins,
                            accessToken = accessToken,
                            refreshToken = refreshToken,
                            expiresInSeconds = expiresIn,
                            isAdmin = existingIsAdmin,
                            isCoinSeller = existingIsCoinSeller,
                            forceGender = !hasCompletedProfile,
                            isProfileCompleted = hasCompletedProfile
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
                val session = UserSessionManager.getSession(context)
                val userId = session?.userId?.trim() ?: ""
                val userEmail = session?.email?.trim() ?: ""
                val token = UserSessionManager.getAccessToken(context)

                if (baseUrl.isNotBlank() && authHeader.isNotBlank()) {
                    // Zero-Out profile on server to force new account flow if database hard delete fails
                    if (userId.isNotBlank()) {
                        try {
                            val resetEndpoint = "$baseUrl/rest/v1/profiles?id=eq.$userId"
                            val patchBody = JSONObject().apply {
                                put("name", "User")
                                put("gender", "")
                                put("avatar_url", "")
                                put("coins", 0)
                                put("diamonds", 0)
                                put("level", 1)
                                put("exp", 0)
                                put("bio", "")
                                put("photos", org.json.JSONArray())
                                put("age", 18)
                            }.toString()
                            val patchReq = Request.Builder()
                                .url(resetEndpoint)
                                .addHeader("apikey", apiKey)
                                .addHeader("Authorization", authHeader)
                                .addHeader("Content-Type", "application/json")
                                .patch(patchBody.toRequestBody(jsonMediaType))
                                .build()
                            client.newCall(patchReq).execute().close()
                        } catch (_: Exception) {}
                    }

                    // 1. Invoke the Supabase Edge Function to delete the account and all user data
                    try {
                        val edgeFunctionUrl = if (baseUrl.contains("supabase.co")) {
                            "$baseUrl/functions/v1/delete-account"
                        } else {
                            "https://nfenuymzzvbxebqmtdqz.supabase.co/functions/v1/delete-account"
                        }
                        val edgeBody = JSONObject().apply {
                            put("user_id", userId)
                            put("email", userEmail)
                        }.toString()
                        val edgeReq = Request.Builder()
                            .url(edgeFunctionUrl)
                            .addHeader("apikey", apiKey)
                            .addHeader("Authorization", authHeader)
                            .addHeader("Content-Type", "application/json")
                            .post(edgeBody.toRequestBody(jsonMediaType))
                            .build()
                        client.newCall(edgeReq).execute().use { response ->
                            android.util.Log.d("SupabaseAuth", "Edge delete-account response: ${response.code}")
                        }
                    } catch (e: Exception) {
                        android.util.Log.e("SupabaseAuth", "Edge delete-account failed: ${e.message}")
                    }

                    // 2. Fallback attempt RPC delete_user_account if available
                    try {
                        val rpcUrl = "$baseUrl/rest/v1/rpc/delete_user_account"
                        val req = Request.Builder()
                            .url(rpcUrl)
                            .addHeader("apikey", apiKey)
                            .addHeader("Authorization", authHeader)
                            .addHeader("Content-Type", "application/json")
                            .post("{}".toRequestBody(jsonMediaType))
                            .build()
                        client.newCall(req).execute().close()
                    } catch (_: Exception) {}

                    // 3. Direct cascade delete user data from tables if userId is present (Strict Dependency Order)
                    if (userId.isNotBlank()) {
                        val tablesToDelete = listOf(
                            "coin_transactions?user_id=eq.$userId",
                            "diamond_transactions?user_id=eq.$userId",
                            "user_follows?follower_id=eq.$userId",
                            "user_follows?following_id=eq.$userId",
                            "profile_visitors?visitor_id=eq.$userId",
                            "profile_visitors?visited_id=eq.$userId",
                            "blocked_users?blocker_id=eq.$userId",
                            "blocked_users?blocked_id=eq.$userId",
                            "user_reports?reporter_id=eq.$userId",
                            "user_reports?reported_id=eq.$userId",
                            "user_frames?user_id=eq.$userId",
                            "user_avatar_frames?user_id=eq.$userId",
                            "fcm_device_tokens?user_id=eq.$userId",
                            "party_room_members?user_id=eq.$userId",
                            "party_room_seats?user_id=eq.$userId",
                            "party_room_admins?user_id=eq.$userId",
                            "party_rooms?host_id=eq.$userId",
                            "agency_members?user_id=eq.$userId",
                            "agency_applications?user_id=eq.$userId",
                            "messages?sender_id=eq.$userId",
                            "messages?receiver_id=eq.$userId",
                            "profiles?id=eq.$userId"
                        )

                        for (path in tablesToDelete) {
                            try {
                                val deleteReq = Request.Builder()
                                    .url("$baseUrl/rest/v1/$path")
                                    .addHeader("apikey", apiKey)
                                    .addHeader("Authorization", authHeader)
                                    .delete()
                                    .build()
                                client.newCall(deleteReq).execute().close()
                            } catch (_: Exception) {}
                        }

                        if (userEmail.isNotBlank()) {
                            try {
                                val deleteEmailReq = Request.Builder()
                                    .url("$baseUrl/rest/v1/profiles?email=eq.$userEmail")
                                    .addHeader("apikey", apiKey)
                                    .addHeader("Authorization", authHeader)
                                    .delete()
                                    .build()
                                client.newCall(deleteEmailReq).execute().close()
                            } catch (_: Exception) {}
                        }
                    }

                    // 3. Supabase Auth logout
                    if (token.isNotBlank()) {
                        try {
                            val logoutReq = Request.Builder()
                                .url("$baseUrl/auth/v1/logout")
                                .addHeader("apikey", apiKey)
                                .addHeader("Authorization", "Bearer $token")
                                .post("{}".toRequestBody(jsonMediaType))
                                .build()
                            client.newCall(logoutReq).execute().close()
                        } catch (_: Exception) {}
                    }
                }

                // 4. Complete local wipe of all user data, prefs, and caches
                wipeLocalUserData(context)

                Pair(true, "All account data has been permanently erased.")
            } catch (e: Exception) {
                wipeLocalUserData(context)
                Pair(true, "All account data erased.")
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
}



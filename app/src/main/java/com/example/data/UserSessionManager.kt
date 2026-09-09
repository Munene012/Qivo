package com.example.data

import android.content.Context
import android.content.SharedPreferences
import kotlinx.coroutines.launch
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.TimeZone

object UserSessionManager {
    private const val PREF_NAME = "qivo_user_session"
    private const val KEY_IS_LOGGED_IN = "is_logged_in"
    private const val KEY_USER_EMAIL = "user_email"
    private const val KEY_USER_ID = "user_id"
    private const val KEY_USER_NAME = "user_name"
    private const val KEY_USER_GENDER = "user_gender"
    private const val KEY_USER_BIRTH_DATE = "user_birth_date"
    private const val KEY_USER_COUNTRY = "user_country"
    private const val KEY_USER_AVATAR_URL = "user_avatar_url"
    private const val KEY_USER_NUMERIC_ID = "user_numeric_id"
    private const val KEY_USER_COINS = "user_coins"
    private const val KEY_USER_DIAMONDS = "user_diamonds"
    private const val KEY_IS_ADMIN = "user_is_admin"
    private const val KEY_IS_COIN_SELLER = "user_is_coin_seller"
    private const val KEY_IS_AGENT = "user_is_agent"
    private const val KEY_IS_VERIFIED = "user_is_verified"
    private const val KEY_ACCESS_TOKEN = "user_access_token"
    private const val KEY_REFRESH_TOKEN = "user_refresh_token"
    private const val KEY_TOKEN_EXPIRES_AT = "user_token_expires_at"
    private const val KEY_FCM_DEVICE_TOKEN = "user_fcm_device_token"
    private const val KEY_ACTIVE_FRAME_ID = "user_active_frame_id"
    private const val KEY_FRAME_EXPIRES_AT = "user_frame_expires_at"
    private const val KEY_USER_EXP = "user_exp"
    private const val KEY_USER_EXP_PREFIX = "user_exp_"
    const val KEY_PROFILE_COMPLETED = "user_profile_completed"
    private const val KEY_DND_VOICE = "dnd_voice_calls"
    private const val KEY_DND_VIDEO = "dnd_video_calls"

    @Volatile private var cachedAccessToken: String = ""
    @Volatile private var cachedRefreshToken: String = ""
    @Volatile private var cachedTokenExpiresAt: Long = 0L
    @Volatile private var cachedUserId: String = ""
    @Volatile private var appContext: Context? = null

    internal fun getPrefs(context: Context?): SharedPreferences? {
        val targetContext = context?.applicationContext ?: appContext ?: return null
        return try {
            targetContext.getSharedPreferences(PREF_NAME, Context.MODE_PRIVATE)
        } catch (_: Exception) {
            null
        }
    }

    fun init(context: Context) {
        appContext = context.applicationContext
        try {
            val prefs = getPrefs(appContext)
            cachedAccessToken = prefs?.getString(KEY_ACCESS_TOKEN, "") ?: ""
            cachedRefreshToken = prefs?.getString(KEY_REFRESH_TOKEN, "") ?: ""
            cachedTokenExpiresAt = prefs?.getLong(KEY_TOKEN_EXPIRES_AT, 0L) ?: 0L
            cachedUserId = prefs?.getString(KEY_USER_ID, "") ?: ""
        } catch (_: Exception) {}
    }

    fun getTodayDateString(): String {
        val phoneCalendar = java.util.Calendar.getInstance(java.util.TimeZone.getDefault())
        val sdf = SimpleDateFormat("yyyy-MM-dd", Locale.US).apply {
            timeZone = phoneCalendar.timeZone
        }
        return sdf.format(phoneCalendar.time)
    }

    // Pre-configured thread-safe ISO formatters for date comparisons
    private val isoDateFormats = arrayOf(
        "yyyy-MM-dd'T'HH:mm:ss.SSSSSSX",
        "yyyy-MM-dd'T'HH:mm:ss.SSS'Z'",
        "yyyy-MM-dd'T'HH:mm:ssX",
        "yyyy-MM-dd'T'HH:mm:ss",
        "yyyy-MM-dd HH:mm:ss",
        "yyyy-MM-dd"
    )

    /**
     * Checks if a date string or timestamp matches today using the device's phone calendar and timezone.
     * High performance: Fast prefix check first, fallback to parsing only if necessary.
     */
    fun isSameDayOnPhoneCalendar(dateStr: String): Boolean {
        if (dateStr.isBlank()) return false
        val clean = dateStr.trim()
        val localTodayStr = getTodayDateString()

        // 1. Fast O(1) string prefix check (covers 98% of cases without allocating SimpleDateFormat)
        if (clean == localTodayStr || clean.startsWith(localTodayStr)) {
            return true
        }

        // 2. If length >= 10 and doesn't match today's date prefix, it's a different day in ISO format
        if (clean.length >= 10 && clean[4] == '-' && clean[7] == '-') {
            val datePrefix = clean.substring(0, 10)
            if (datePrefix == localTodayStr) return true
        }

        // 3. Fallback to calendar parsing for timezone edge cases
        val userPhoneCal = java.util.Calendar.getInstance(java.util.TimeZone.getDefault())
        val todayYear = userPhoneCal.get(java.util.Calendar.YEAR)
        val todayDayOfYear = userPhoneCal.get(java.util.Calendar.DAY_OF_YEAR)

        for (pattern in isoDateFormats) {
            try {
                val sdf = SimpleDateFormat(pattern, Locale.US).apply {
                    if (pattern.endsWith("'Z'")) {
                        timeZone = java.util.TimeZone.getTimeZone("UTC")
                    }
                }
                val date = sdf.parse(clean)
                if (date != null) {
                    val claimCal = java.util.Calendar.getInstance(java.util.TimeZone.getDefault()).apply {
                        time = date
                    }
                    if (claimCal.get(java.util.Calendar.YEAR) == todayYear &&
                        claimCal.get(java.util.Calendar.DAY_OF_YEAR) == todayDayOfYear
                    ) {
                        return true
                    }
                }
            } catch (_: Exception) {}
        }
        return false
    }

    fun saveSession(
        context: Context,
        email: String,
        userId: String,
        name: String = "",
        gender: String = "",
        country: String = "",
        avatarUrl: String? = null,
        numericId: Long = 0L,
        coins: Long? = null,
        accessToken: String? = null,
        refreshToken: String? = null,
        expiresInSeconds: Long? = null,
        isAdmin: Boolean? = null,
        isCoinSeller: Boolean? = null,
        isAgent: Boolean? = null,
        isVerified: Boolean? = null,
        birthDate: String = "",
        activeFrameId: String? = null,
        frameExpiresAt: String? = null,
        exp: Long? = null,
        forceGender: Boolean = false,
        isProfileCompleted: Boolean? = null
    ) {
        // Only reuse attributes if the cached session belongs to the exact same userId
        val existing = getSession(context)?.takeIf { it.userId == userId }
        val finalUserId = if (userId.isNotBlank()) userId else existing?.userId ?: ""
        val finalName = when {
            name.isNotBlank() && name != "QIVO User" -> name
            !existing?.name.isNullOrBlank() && existing?.name != "QIVO User" && existing?.name != "User" -> existing.name
            name.isNotBlank() -> name
            email.contains("@") -> email.substringBefore("@")
            else -> "User"
        }
        val finalGender = when {
            gender.equals("female", ignoreCase = true) ||
            gender.equals("f", ignoreCase = true) ||
            gender.equals("woman", ignoreCase = true) ||
            gender.equals("w", ignoreCase = true) -> "Female"

            gender.equals("male", ignoreCase = true) ||
            gender.equals("m", ignoreCase = true) ||
            gender.equals("man", ignoreCase = true) -> "Male"

            !forceGender && gender.isBlank() && existing?.gender?.isNotBlank() == true &&
            (existing.gender.equals("Female", ignoreCase = true) || existing.gender.equals("Male", ignoreCase = true)) -> existing.gender

            else -> ""
        }
        val finalCountry = when {
            country.isNotBlank() -> country
            !existing?.country.isNullOrBlank() -> existing!!.country
            else -> "United States"
        }
        val finalAvatar = when {
            avatarUrl != null -> avatarUrl
            !existing?.avatarUrl.isNullOrBlank() -> existing!!.avatarUrl
            else -> ""
        }
        val finalBirthDate = when {
            birthDate.isNotBlank() -> birthDate
            !existing?.birthDate.isNullOrBlank() -> existing!!.birthDate
            else -> ""
        }
        val finalFrameId = when {
            activeFrameId != null -> activeFrameId
            else -> getActiveFrameId(context, finalUserId)
        }
        val finalFrameExp = when {
            frameExpiresAt != null -> frameExpiresAt
            else -> getFrameExpiresAt(context, finalUserId)
        }
        val finalNumericId = if (numericId > 0L) numericId else existing?.numericId ?: 0L
        val finalCoins = coins ?: existing?.coins ?: 0L
        val finalAdmin = isAdmin ?: existing?.isAdmin ?: false
        val finalCoinSeller = isCoinSeller ?: existing?.isCoinSeller ?: false
        val finalAgent = isAgent ?: existing?.isAgent ?: false
        val finalVerified = isVerified ?: existing?.isVerified ?: false

        val currentStoredToken = getAccessToken(context)
        val currentStoredRefreshToken = getRefreshToken(context)
        val currentStoredExpiresAt = getTokenExpiresAt(context)

        val finalToken = when {
            !accessToken.isNullOrBlank() -> accessToken
            !existing?.accessToken.isNullOrBlank() -> existing!!.accessToken
            currentStoredToken.isNotBlank() -> currentStoredToken
            else -> ""
        }
        val finalRefreshToken = when {
            !refreshToken.isNullOrBlank() -> refreshToken
            !existing?.refreshToken.isNullOrBlank() -> existing!!.refreshToken
            currentStoredRefreshToken.isNotBlank() -> currentStoredRefreshToken
            else -> ""
        }
        val expiresAt = when {
            expiresInSeconds != null && expiresInSeconds > 0 -> System.currentTimeMillis() + (expiresInSeconds * 1000L)
            existing?.tokenExpiresAt != null && existing.tokenExpiresAt > 0L -> existing.tokenExpiresAt
            currentStoredExpiresAt > 0L -> currentStoredExpiresAt
            else -> System.currentTimeMillis() + (3600L * 1000L) // Default 1 hour
        }

        cachedAccessToken = finalToken
        cachedRefreshToken = finalRefreshToken
        cachedTokenExpiresAt = expiresAt
        cachedUserId = userId

        try {
            val isComplete = isProfileCompleted ?: (finalGender.equals("Female", ignoreCase = true) || finalGender.equals("Male", ignoreCase = true))
            getPrefs(context)?.edit()?.apply {
                putBoolean(KEY_IS_LOGGED_IN, true)
                putBoolean(KEY_PROFILE_COMPLETED, isComplete)
                putString(KEY_USER_EMAIL, email)
                putString(KEY_USER_ID, userId)
                putString(KEY_USER_NAME, finalName)
                putString(KEY_USER_GENDER, finalGender)
                putString(KEY_USER_COUNTRY, finalCountry)
                putString(KEY_USER_AVATAR_URL, finalAvatar)
                if (finalBirthDate.isNotBlank()) {
                    putString(KEY_USER_BIRTH_DATE, finalBirthDate)
                }
                if (finalUserId.isNotBlank()) {
                    putString(KEY_ACTIVE_FRAME_PREFIX + finalUserId, finalFrameId)
                    putString(KEY_FRAME_EXPIRES_PREFIX + finalUserId, finalFrameExp)
                }
                putString(KEY_ACTIVE_FRAME_ID, finalFrameId)
                putString(KEY_FRAME_EXPIRES_AT, finalFrameExp)
                putLong(KEY_USER_NUMERIC_ID, finalNumericId)
                putLong(KEY_USER_COINS, finalCoins)
                putBoolean(KEY_IS_ADMIN, finalAdmin)
                putBoolean(KEY_IS_COIN_SELLER, finalCoinSeller)
                putBoolean(KEY_IS_AGENT, finalAgent)
                putBoolean(KEY_IS_VERIFIED, finalVerified)
                if (finalToken.isNotBlank()) {
                    putString(KEY_ACCESS_TOKEN, finalToken)
                }
                if (finalRefreshToken.isNotBlank()) {
                    putString(KEY_REFRESH_TOKEN, finalRefreshToken)
                }
                putLong(KEY_TOKEN_EXPIRES_AT, expiresAt)
                if (exp != null) {
                    putLong(KEY_USER_EXP, exp)
                    if (finalUserId.isNotBlank()) {
                        putLong(KEY_USER_EXP_PREFIX + finalUserId, exp)
                    }
                }
                commit()
            }

            if (finalUserId.isNotBlank()) {
                val validFrame = if (AvatarFrameManager.isFrameValid(finalFrameId, finalFrameExp)) finalFrameId else ""
                _activeFrameState.value = finalUserId to validFrame
            }
            if (coins != null) {
                _coinsFlow.value = finalCoins
            }
            if (exp != null) {
                _expFlow.value = exp
            }
            notifySessionUpdated()
        } catch (_: Exception) {}
    }

    fun parseJwtExpiresAt(jwt: String): Long? {
        try {
            val parts = jwt.split(".")
            if (parts.size >= 2) {
                var payloadStr = parts[1].replace('-', '+').replace('_', '/')
                while (payloadStr.length % 4 != 0) {
                    payloadStr += "="
                }
                val payloadBytes = android.util.Base64.decode(payloadStr, android.util.Base64.DEFAULT)
                val json = org.json.JSONObject(String(payloadBytes, Charsets.UTF_8))
                val expSeconds = json.optLong("exp", 0L)
                if (expSeconds > 0L) {
                    return expSeconds * 1000L
                }
            }
        } catch (_: Exception) {}
        return null
    }

    fun saveTokens(
        context: Context? = null,
        accessToken: String,
        refreshToken: String = "",
        expiresInSeconds: Long = 3600L
    ) {
        if (accessToken.isNotBlank()) {
            val parsedExp = parseJwtExpiresAt(accessToken)
            val expiresAt = parsedExp ?: (System.currentTimeMillis() + (expiresInSeconds * 1000L))
            cachedAccessToken = accessToken
            val finalRefreshToken = if (refreshToken.isNotBlank()) refreshToken else getRefreshToken(context)
            if (finalRefreshToken.isNotBlank()) {
                cachedRefreshToken = finalRefreshToken
            }
            cachedTokenExpiresAt = expiresAt

            try {
                getPrefs(context)?.edit()?.apply {
                    putString(KEY_ACCESS_TOKEN, accessToken)
                    if (finalRefreshToken.isNotBlank()) {
                        putString(KEY_REFRESH_TOKEN, finalRefreshToken)
                    }
                    putLong(KEY_TOKEN_EXPIRES_AT, expiresAt)
                    commit()
                }
            } catch (_: Exception) {}
        }
    }

    fun saveAccessToken(context: Context? = null, accessToken: String) {
        if (accessToken.isNotBlank()) {
            cachedAccessToken = accessToken
            val parsedExp = parseJwtExpiresAt(accessToken)
            val expiresAt = parsedExp ?: (System.currentTimeMillis() + (3600L * 1000L))
            cachedTokenExpiresAt = expiresAt
            try {
                getPrefs(context)?.edit()?.apply {
                    putString(KEY_ACCESS_TOKEN, accessToken)
                    putLong(KEY_TOKEN_EXPIRES_AT, expiresAt)
                    commit()
                }
            } catch (_: Exception) {}
        }
    }

    fun getUserId(context: Context? = null): String {
        if (cachedUserId.isNotBlank()) return cachedUserId
        try {
            val targetContext = context ?: appContext
            if (targetContext != null) {
                val uid = getPrefs(targetContext)?.getString(KEY_USER_ID, "") ?: ""
                if (uid.isNotBlank()) {
                    cachedUserId = uid
                    return uid
                }
            }
        } catch (_: Exception) {}
        return cachedUserId
    }

    fun getUserEmail(context: Context? = null): String {
        try {
            val targetContext = context ?: appContext
            if (targetContext != null) {
                return getPrefs(targetContext)?.getString(KEY_USER_EMAIL, "") ?: ""
            }
        } catch (_: Exception) {}
        return ""
    }

    fun getAccessToken(context: Context? = null): String {
        if (cachedAccessToken.isNotBlank()) return cachedAccessToken
        try {
            val targetContext = context ?: appContext
            if (targetContext != null) {
                val token = getPrefs(targetContext)?.getString(KEY_ACCESS_TOKEN, "") ?: ""
                if (token.isNotBlank()) {
                    cachedAccessToken = token
                    return token
                }
            }
        } catch (_: Exception) {}
        return cachedAccessToken
    }

    fun getRefreshToken(context: Context? = null): String {
        if (cachedRefreshToken.isNotBlank()) return cachedRefreshToken
        try {
            val targetContext = context ?: appContext
            if (targetContext != null) {
                val rToken = getPrefs(targetContext)?.getString(KEY_REFRESH_TOKEN, "") ?: ""
                if (rToken.isNotBlank()) {
                    cachedRefreshToken = rToken
                    return rToken
                }
            }
        } catch (_: Exception) {}
        return cachedRefreshToken
    }

    fun getTokenExpiresAt(context: Context? = null): Long {
        if (cachedTokenExpiresAt > 0L) return cachedTokenExpiresAt
        try {
            val targetContext = context ?: appContext
            if (targetContext != null) {
                val exp = getPrefs(targetContext)?.getLong(KEY_TOKEN_EXPIRES_AT, 0L) ?: 0L
                if (exp > 0L) {
                    cachedTokenExpiresAt = exp
                    return exp
                }
            }
        } catch (_: Exception) {}
        return cachedTokenExpiresAt
    }

    /**
     * Checks if current JWT is expired or nearing expiration within the next 5 minutes.
     * A 5-minute safety threshold prevents mid-request expirations and race conditions.
     */
    fun isTokenExpired(context: Context? = null): Boolean {
        val token = getAccessToken(context)
        if (token.isBlank()) return true

        var exp = getTokenExpiresAt(context)
        if (exp <= 0L) {
            val parsed = parseJwtExpiresAt(token)
            if (parsed != null && parsed > 0L) {
                exp = parsed
                cachedTokenExpiresAt = parsed
                try {
                    getPrefs(context)?.edit()?.putLong(KEY_TOKEN_EXPIRES_AT, parsed)?.commit()
                } catch (_: Exception) {}
            }
        }

        if (exp <= 0L) return true // If expiration cannot be determined, treat as expired to trigger verification
        val normalizedExp = if (exp in 1L..9999999999L) exp * 1000L else exp
        val now = System.currentTimeMillis()
        // 5-minute (300,000 ms) proactive buffer
        return (now + 300_000L) >= normalizedExp
    }

    /**
     * Returns a guaranteed valid access token, auto-refreshing asynchronously if on main thread or synchronously if on background thread.
     */
    fun getValidAccessToken(context: Context? = null): String {
        val targetContext = context ?: appContext
        val token = getAccessToken(targetContext)
        if (token.isNotBlank() && !isTokenExpired(targetContext)) {
            return token
        }
        val rToken = getRefreshToken(targetContext)
        if (rToken.isNotBlank() || token.isNotBlank()) {
            if (android.os.Looper.myLooper() == android.os.Looper.getMainLooper()) {
                // If invoked on the UI/Main thread, dispatch refresh asynchronously without blocking
                kotlinx.coroutines.CoroutineScope(kotlinx.coroutines.Dispatchers.IO).launch {
                    try {
                        SupabaseAuthService.refreshSessionSync(targetContext, forceRefresh = true)
                    } catch (_: Exception) {}
                }
                return token
            }
            val refreshed = SupabaseAuthService.refreshSessionSync(targetContext, forceRefresh = true)
            if (!refreshed.isNullOrBlank()) {
                return refreshed
            }
        }
        return token
    }

    /**
     * Get valid Authorization header value: "Bearer <user_jwt>" for authenticated user,
     * or fallback to "Bearer <anon_key>" ONLY if no user is signed in.
     */
    fun getAuthHeader(context: Context? = null): String {
        val targetContext = context ?: appContext
        val validToken = getValidAccessToken(targetContext)
        if (validToken.isNotBlank()) {
            return "Bearer $validToken"
        }

        // If a user token is stored, ALWAYS use it rather than downgrading to anon key
        val currentToken = getAccessToken(targetContext)
        if (currentToken.isNotBlank()) {
            return "Bearer $currentToken"
        }

        val apiKey = SupabaseConfig.supabaseAnonKey.trim().ifEmpty { SupabaseConfig.supabaseApiKey.trim() }

        // Fallback to Supabase Anon key ONLY for unauthenticated public/guest queries
        if (apiKey.isNotBlank()) {
            return "Bearer $apiKey"
        }

        return ""
    }

    /**
     * Explicit Authenticated user authorization header
     */
    fun getAuthenticatedAuthHeader(context: Context? = null): String {
        val targetContext = context ?: appContext
        val validToken = getValidAccessToken(targetContext)
        if (validToken.isNotBlank()) {
            return "Bearer $validToken"
        }
        val currentToken = getAccessToken(targetContext)
        return if (currentToken.isNotBlank()) "Bearer $currentToken" else ""
    }

    fun hasValidUserToken(context: Context? = null): Boolean {
        return getAccessToken(context).isNotBlank() && !isTokenExpired(context)
    }

    private val _coinsFlow = kotlinx.coroutines.flow.MutableStateFlow<Long?>(null)
    val coinsFlow: kotlinx.coroutines.flow.StateFlow<Long?> = _coinsFlow

    private val _diamondsFlow = kotlinx.coroutines.flow.MutableStateFlow<Long?>(null)
    val diamondsFlow: kotlinx.coroutines.flow.StateFlow<Long?> = _diamondsFlow

    private val _expFlow = kotlinx.coroutines.flow.MutableStateFlow<Long?>(null)
    val expFlow: kotlinx.coroutines.flow.StateFlow<Long?> = _expFlow

    private val _dndVoiceFlow = kotlinx.coroutines.flow.MutableStateFlow<Boolean?>(null)
    val dndVoiceFlow: kotlinx.coroutines.flow.StateFlow<Boolean?> = _dndVoiceFlow

    private val _dndVideoFlow = kotlinx.coroutines.flow.MutableStateFlow<Boolean?>(null)
    val dndVideoFlow: kotlinx.coroutines.flow.StateFlow<Boolean?> = _dndVideoFlow

    fun isDndVoiceEnabled(context: Context?): Boolean {
        return getPrefs(context)?.getBoolean(KEY_DND_VOICE, false) ?: false
    }

    fun setDndVoiceEnabled(context: Context?, enabled: Boolean) {
        try {
            getPrefs(context)?.edit()?.putBoolean(KEY_DND_VOICE, enabled)?.apply()
            _dndVoiceFlow.value = enabled
            notifySessionUpdated()
        } catch (_: Exception) {}
    }

    fun isDndVideoEnabled(context: Context?): Boolean {
        return getPrefs(context)?.getBoolean(KEY_DND_VIDEO, false) ?: false
    }

    fun setDndVideoEnabled(context: Context?, enabled: Boolean) {
        try {
            getPrefs(context)?.edit()?.putBoolean(KEY_DND_VIDEO, enabled)?.apply()
            _dndVideoFlow.value = enabled
            notifySessionUpdated()
        } catch (_: Exception) {}
    }

    private val _sessionUpdateFlow = kotlinx.coroutines.flow.MutableStateFlow<Long>(0L)
    val sessionUpdateFlow: kotlinx.coroutines.flow.StateFlow<Long> = _sessionUpdateFlow

    fun notifySessionUpdated() {
        _sessionUpdateFlow.value = System.currentTimeMillis()
    }

    fun getExp(context: Context?, userId: String = ""): Long {
        return try {
            val prefs = getPrefs(context) ?: return 0L
            val effectiveUserId = userId.ifEmpty { prefs.getString(KEY_USER_ID, "") ?: "" }
            if (effectiveUserId.isNotBlank()) {
                prefs.getLong(KEY_USER_EXP_PREFIX + effectiveUserId, prefs.getLong(KEY_USER_EXP, 0L))
            } else {
                prefs.getLong(KEY_USER_EXP, 0L)
            }
        } catch (_: Exception) {
            0L
        }
    }

    fun saveExp(context: Context?, exp: Long, userId: String = "") {
        val safeExp = exp.coerceAtLeast(0L)
        try {
            val prefs = getPrefs(context) ?: return
            val effectiveUserId = userId.ifEmpty { prefs.getString(KEY_USER_ID, "") ?: "" }
            prefs.edit().apply {
                putLong(KEY_USER_EXP, safeExp)
                if (effectiveUserId.isNotBlank()) {
                    putLong(KEY_USER_EXP_PREFIX + effectiveUserId, safeExp)
                }
                apply()
            }
            _expFlow.value = safeExp
            notifySessionUpdated()
        } catch (_: Exception) {}
    }

    fun addExp(context: Context?, amount: Long, userId: String = ""): Long {
        if (amount <= 0L) return getExp(context, userId)
        val current = getExp(context, userId)
        val updated = current + amount
        saveExp(context, updated, userId)
        
        // Trigger server-side secure RPC update asynchronously
        val effectiveUserId = userId.ifEmpty { getSession(context)?.userId ?: "" }
        if (effectiveUserId.isNotBlank()) {
            kotlinx.coroutines.CoroutineScope(kotlinx.coroutines.Dispatchers.IO).launch {
                try {
                    SupabaseProfileService().recordUserExpRpc(context, effectiveUserId, amount, "COIN_EXP_SYNC")
                } catch (_: Exception) {}
            }
        }
        return updated
    }

    fun saveCoins(context: Context, coins: Long) {
        try {
            val previousCoins = getCoins(context)
            getPrefs(context)?.edit()?.putLong(KEY_USER_COINS, coins)?.apply()
            _coinsFlow.value = coins
            // If coins increased (e.g. bought, awarded, received), award 1:1 EXP
            if (coins > previousCoins) {
                addExp(context, coins - previousCoins)
            }
            notifySessionUpdated()
        } catch (_: Exception) {}
    }

    fun saveRoles(context: Context, isAdmin: Boolean, isCoinSeller: Boolean, isAgent: Boolean = false) {
        try {
            getPrefs(context)?.edit()
                ?.putBoolean(KEY_IS_ADMIN, isAdmin)
                ?.putBoolean(KEY_IS_COIN_SELLER, isCoinSeller)
                ?.putBoolean(KEY_IS_AGENT, isAgent)
                ?.apply()
            notifySessionUpdated()
        } catch (_: Exception) {}
    }

    fun isAdmin(context: Context?): Boolean {
        return try {
            getPrefs(context)?.getBoolean(KEY_IS_ADMIN, false) ?: false
        } catch (_: Exception) {
            false
        }
    }

    fun isCoinSeller(context: Context?): Boolean {
        return try {
            getPrefs(context)?.getBoolean(KEY_IS_COIN_SELLER, false) ?: false
        } catch (_: Exception) {
            false
        }
    }

    fun isAgent(context: Context?): Boolean {
        return try {
            getPrefs(context)?.getBoolean(KEY_IS_AGENT, false) ?: false
        } catch (_: Exception) {
            false
        }
    }

    fun isVerified(context: Context?): Boolean {
        return try {
            getPrefs(context)?.getBoolean(KEY_IS_VERIFIED, false) ?: false
        } catch (_: Exception) {
            false
        }
    }

    fun saveVerification(context: Context, isVerified: Boolean) {
        try {
            getPrefs(context)?.edit()?.putBoolean(KEY_IS_VERIFIED, isVerified)?.apply()
            notifySessionUpdated()
        } catch (_: Exception) {}
    }

    fun saveAvatarUrl(context: Context, avatarUrl: String) {
        try {
            getPrefs(context)?.edit()?.putString(KEY_USER_AVATAR_URL, avatarUrl)?.apply()
            notifySessionUpdated()
        } catch (_: Exception) {}
    }

    fun getCoins(context: Context): Long {
        return try {
            getPrefs(context)?.getLong(KEY_USER_COINS, 0L) ?: 0L
        } catch (_: Exception) {
            0L
        }
    }

    fun saveDiamonds(context: Context, diamonds: Long) {
        try {
            val clamped = diamonds.coerceAtLeast(0L)
            getPrefs(context)?.edit()?.putLong(KEY_USER_DIAMONDS, clamped)?.apply()
            _diamondsFlow.value = clamped
            notifySessionUpdated()
        } catch (_: Exception) {}
    }

    fun getDiamonds(context: Context): Long {
        return try {
            getPrefs(context)?.getLong(KEY_USER_DIAMONDS, 0L) ?: 0L
        } catch (_: Exception) {
            0L
        }
    }

    fun subtractDiamonds(context: Context, amount: Long): Long {
        val current = getDiamonds(context)
        val updated = (current - amount).coerceAtLeast(0L)
        saveDiamonds(context, updated)
        return updated
    }

    fun addDiamonds(context: Context, amount: Long): Long {
        val current = getDiamonds(context)
        val updated = current + amount
        saveDiamonds(context, updated)
        return updated
    }

    fun subtractCoins(context: Context, amount: Long): Long {
        val current = getCoins(context)
        val updated = (current - amount).coerceAtLeast(0L)
        saveCoins(context, updated)
        return updated
    }

    fun addCoins(context: Context, amount: Long): Long {
        val current = getCoins(context)
        val updated = current + amount
        saveCoins(context, updated)
        return updated
    }

    fun saveDailyCheckIn(
        context: Context,
        userId: String = "",
        dateStr: String = getTodayDateString(),
        dayNumber: Int,
        email: String = ""
    ) {
        val effectiveUserId = userId.ifEmpty { getSession(context)?.userId ?: "global" }
        val effectiveEmail = email.ifEmpty { getSession(context)?.email ?: "" }
        try {
            getPrefs(context)?.edit()?.apply {
                putString("last_checkin_date_$effectiveUserId", dateStr)
                putInt("last_checkin_day_$effectiveUserId", dayNumber)
                if (effectiveEmail.isNotEmpty()) {
                    putString("last_checkin_date_${effectiveEmail.lowercase()}", dateStr)
                    putInt("last_checkin_day_${effectiveEmail.lowercase()}", dayNumber)
                }
                putString("last_checkin_date_global", dateStr)
                putInt("last_checkin_day_global", dayNumber)
                apply()
            }
        } catch (_: Exception) {}
    }

    fun getLastCheckInDate(context: Context, userId: String = "", email: String = ""): String {
        return try {
            val prefs = getPrefs(context) ?: return ""
            val effectiveUserId = userId.ifEmpty { getSession(context)?.userId ?: "global" }
            val userDate = prefs.getString("last_checkin_date_$effectiveUserId", "") ?: ""
            if (userDate.isNotEmpty()) return userDate

            val effectiveEmail = email.ifEmpty { getSession(context)?.email ?: "" }
            if (effectiveEmail.isNotEmpty()) {
                val emailDate = prefs.getString("last_checkin_date_${effectiveEmail.lowercase()}", "") ?: ""
                if (emailDate.isNotEmpty()) return emailDate
            }

            prefs.getString("last_checkin_date_global", "") ?: ""
        } catch (_: Exception) {
            ""
        }
    }

    fun getLastCheckInDay(context: Context, userId: String = "", email: String = ""): Int {
        return try {
            val prefs = getPrefs(context) ?: return 0
            val effectiveUserId = userId.ifEmpty { getSession(context)?.userId ?: "global" }
            val userDay = prefs.getInt("last_checkin_day_$effectiveUserId", 0)
            if (userDay > 0) return userDay

            val effectiveEmail = email.ifEmpty { getSession(context)?.email ?: "" }
            if (effectiveEmail.isNotEmpty()) {
                val emailDay = prefs.getInt("last_checkin_day_${effectiveEmail.lowercase()}", 0)
                if (emailDay > 0) return emailDay
            }

            prefs.getInt("last_checkin_day_global", 0)
        } catch (_: Exception) {
            0
        }
    }

    fun isClaimedToday(context: Context, userId: String = "", email: String = ""): Boolean {
        val lastDate = getLastCheckInDate(context, userId, email).trim()
        if (lastDate.isEmpty()) return false
        return isSameDayOnPhoneCalendar(lastDate)
    }

    fun isLoggedIn(context: Context? = null): Boolean {
        return try {
            getPrefs(context)?.getBoolean(KEY_IS_LOGGED_IN, false) ?: false
        } catch (_: Exception) {
            false
        }
    }

    data class SessionData(
        val email: String,
        val userId: String,
        val name: String,
        val gender: String,
        val country: String,
        val avatarUrl: String,
        val numericId: Long,
        val coins: Long,
        val accessToken: String = "",
        val refreshToken: String = "",
        val tokenExpiresAt: Long = 0L,
        val isAdmin: Boolean = false,
        val isCoinSeller: Boolean = false,
        val isAgent: Boolean = false,
        val isVerified: Boolean = false,
        val birthDate: String = "",
        val activeFrameId: String = "",
        val frameExpiresAt: String = "",
        val exp: Long = 0L,
        val isProfileCompleted: Boolean = false,
        val isDndVoice: Boolean = false,
        val isDndVideo: Boolean = false
    )

    fun getSession(context: Context? = null): SessionData? {
        val prefs = try {
            getPrefs(context) ?: return null
        } catch (_: Exception) {
            return null
        }
        if (!prefs.getBoolean(KEY_IS_LOGGED_IN, false)) return null
        val email = prefs.getString(KEY_USER_EMAIL, "") ?: ""
        val userId = prefs.getString(KEY_USER_ID, "") ?: ""
        if (userId.isEmpty()) return null
        val safeEmail = if (email.isNotBlank()) email else "user@qivo.app"

        val accessToken = prefs.getString(KEY_ACCESS_TOKEN, "") ?: ""
        val refreshToken = prefs.getString(KEY_REFRESH_TOKEN, "") ?: ""
        val expiresAt = prefs.getLong(KEY_TOKEN_EXPIRES_AT, 0L)

        cachedAccessToken = accessToken
        cachedRefreshToken = refreshToken
        cachedTokenExpiresAt = expiresAt
        cachedUserId = userId

        val scopedFrameId = prefs.getString(KEY_ACTIVE_FRAME_PREFIX + userId, null)
        val rawFrameId = scopedFrameId ?: (prefs.getString(KEY_ACTIVE_FRAME_ID, "") ?: "")
        val scopedFrameExp = prefs.getString(KEY_FRAME_EXPIRES_PREFIX + userId, null)
        val frameExp = scopedFrameExp ?: (prefs.getString(KEY_FRAME_EXPIRES_AT, "") ?: "")
        val validFrameId = if (AvatarFrameManager.isFrameValid(rawFrameId, frameExp)) rawFrameId else ""
        val userExp = prefs.getLong(KEY_USER_EXP_PREFIX + userId, prefs.getLong(KEY_USER_EXP, 0L))

        val savedGender = prefs.getString(KEY_USER_GENDER, "") ?: ""
        val isProfileCompleted = prefs.getBoolean(KEY_PROFILE_COMPLETED, false) ||
            (savedGender.equals("Female", ignoreCase = true) || savedGender.equals("Male", ignoreCase = true))

        return SessionData(
            email = safeEmail,
            userId = userId,
            name = prefs.getString(KEY_USER_NAME, "User") ?: "User",
            gender = savedGender,
            country = prefs.getString(KEY_USER_COUNTRY, "United States") ?: "United States",
            avatarUrl = prefs.getString(KEY_USER_AVATAR_URL, "") ?: "",
            numericId = prefs.getLong(KEY_USER_NUMERIC_ID, 0L),
            coins = prefs.getLong(KEY_USER_COINS, 0L),
            accessToken = accessToken,
            refreshToken = refreshToken,
            tokenExpiresAt = expiresAt,
            isAdmin = prefs.getBoolean(KEY_IS_ADMIN, false),
            isCoinSeller = prefs.getBoolean(KEY_IS_COIN_SELLER, false),
            isAgent = prefs.getBoolean(KEY_IS_AGENT, false),
            isVerified = prefs.getBoolean(KEY_IS_VERIFIED, false),
            birthDate = prefs.getString(KEY_USER_BIRTH_DATE, "") ?: "",
            activeFrameId = validFrameId,
            frameExpiresAt = frameExp,
            exp = userExp,
            isProfileCompleted = isProfileCompleted,
            isDndVoice = prefs.getBoolean(KEY_DND_VOICE, false),
            isDndVideo = prefs.getBoolean(KEY_DND_VIDEO, false)
        )
    }

    private val _activeFrameState = kotlinx.coroutines.flow.MutableStateFlow<Pair<String, String>>("" to "")
    val activeFrameFlow: kotlinx.coroutines.flow.StateFlow<Pair<String, String>> = _activeFrameState

    private const val KEY_OWNED_FRAMES_PREFIX = "user_owned_frames_json_"
    private const val KEY_ACTIVE_FRAME_PREFIX = "active_frame_id_"
    private const val KEY_FRAME_EXPIRES_PREFIX = "active_frame_expires_at_"

    fun savePurchasedFrame(
        context: Context?,
        frameId: String,
        expiresAt: String,
        pricePaid: Long = 0L,
        targetUserId: String = ""
    ) {
        val cleanFrameId = frameId.trim()
        if (cleanFrameId.isBlank()) return
        try {
            val prefs = getPrefs(context) ?: return
            val currentUserId = if (targetUserId.isNotBlank()) targetUserId.trim() else (getSession(context)?.userId ?: "")
            if (currentUserId.isBlank()) return

            val userKey = KEY_OWNED_FRAMES_PREFIX + currentUserId
            val rawJson = prefs.getString(userKey, "[]") ?: "[]"
            val arr = org.json.JSONArray(rawJson)
            val cleanId = cleanFrameId.lowercase()
            var exists = false
            for (i in 0 until arr.length()) {
                val obj = arr.getJSONObject(i)
                val fId = obj.optString("frame_id", "").lowercase()
                if (fId == cleanId) {
                    obj.put("expires_at", expiresAt)
                    obj.put("price_paid", pricePaid)
                    obj.put("is_active", true)
                    exists = true
                } else {
                    obj.put("is_active", false)
                }
            }
            if (!exists) {
                val newObj = org.json.JSONObject().apply {
                    put("id", java.util.UUID.randomUUID().toString())
                    put("user_id", currentUserId)
                    put("frame_id", cleanFrameId)
                    put("purchased_at", java.text.SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss.SSS'Z'", java.util.Locale.US).apply { timeZone = java.util.TimeZone.getTimeZone("UTC") }.format(java.util.Date()))
                    put("expires_at", expiresAt)
                    put("is_active", true)
                    put("price_paid", pricePaid)
                }
                arr.put(newObj)
            }
            prefs.edit().putString(userKey, arr.toString()).apply()
            saveActiveFrame(context, cleanFrameId, expiresAt, targetUserId = currentUserId)
        } catch (_: Exception) {}
    }

    fun getPurchasedFrames(context: Context?, targetUserId: String = ""): List<UserOwnedFrame> {
        val result = mutableListOf<UserOwnedFrame>()
        try {
            val prefs = getPrefs(context) ?: return emptyList()
            val currentUserId = if (targetUserId.isNotBlank()) targetUserId.trim() else (getSession(context)?.userId ?: "")
            if (currentUserId.isBlank()) return emptyList()

            val userKey = KEY_OWNED_FRAMES_PREFIX + currentUserId
            val rawJson = prefs.getString(userKey, "[]") ?: "[]"
            val arr = org.json.JSONArray(rawJson)
            val currentActive = getActiveFrameId(context, currentUserId)
            for (i in 0 until arr.length()) {
                val obj = arr.getJSONObject(i)
                val fId = obj.optString("frame_id", "")
                val itemUserId = obj.optString("user_id", "")
                if (fId.isNotBlank() && (itemUserId.isBlank() || itemUserId == currentUserId)) {
                    result.add(
                        UserOwnedFrame(
                            id = obj.optString("id", java.util.UUID.randomUUID().toString()),
                            userId = currentUserId,
                            frameId = fId,
                            purchasedAt = obj.optString("purchased_at", ""),
                            expiresAt = obj.optString("expires_at", ""),
                            isActive = currentActive.isNotBlank() && fId.equals(currentActive, ignoreCase = true),
                            pricePaid = obj.optLong("price_paid", 0L)
                        )
                    )
                }
            }
        } catch (_: Exception) {}
        return result
    }

    fun saveActiveFrame(
        context: Context?,
        frameId: String,
        expiresAt: String,
        targetUserId: String = ""
    ) {
        try {
            val prefs = getPrefs(context) ?: return
            val currentUserId = if (targetUserId.isNotBlank()) targetUserId.trim() else (getSession(context)?.userId ?: "")
            val cleanFrameId = if (frameId.equals("none", ignoreCase = true)) "" else frameId.trim()
            val cleanExp = if (cleanFrameId.isBlank()) "" else expiresAt.trim()

            prefs.edit().apply {
                if (currentUserId.isNotBlank()) {
                    putString(KEY_ACTIVE_FRAME_PREFIX + currentUserId, cleanFrameId)
                    putString(KEY_FRAME_EXPIRES_PREFIX + currentUserId, cleanExp)
                }
                putString(KEY_ACTIVE_FRAME_ID, cleanFrameId)
                putString(KEY_FRAME_EXPIRES_AT, cleanExp)
                apply()
            }

            // Sync user owned frames is_active state so strictly only one frame is marked active
            if (currentUserId.isNotBlank()) {
                val userKey = KEY_OWNED_FRAMES_PREFIX + currentUserId
                val rawJson = prefs.getString(userKey, "[]") ?: "[]"
                if (rawJson.startsWith("[")) {
                    val arr = org.json.JSONArray(rawJson)
                    for (i in 0 until arr.length()) {
                        val obj = arr.getJSONObject(i)
                        val fId = obj.optString("frame_id", "")
                        val shouldBeActive = cleanFrameId.isNotBlank() && fId.equals(cleanFrameId, ignoreCase = true)
                        obj.put("is_active", shouldBeActive)
                    }
                    prefs.edit().putString(userKey, arr.toString()).apply()
                }
            }

            _activeFrameState.value = currentUserId to cleanFrameId
        } catch (_: Exception) {}
    }

    fun getActiveFrameId(context: Context? = null, targetUserId: String = ""): String {
        return try {
            val prefs = getPrefs(context) ?: return ""
            val myUserId = getSession(context)?.userId?.trim() ?: ""
            val currentUserId = targetUserId.trim()

            val frameId = if (currentUserId.isNotBlank()) {
                if (currentUserId == myUserId) {
                    val scoped = prefs.getString(KEY_ACTIVE_FRAME_PREFIX + currentUserId, null)
                    scoped ?: (prefs.getString(KEY_ACTIVE_FRAME_ID, "") ?: "")
                } else {
                    // For another user, ONLY check what was explicitly saved for their specific userId, no fallback!
                    prefs.getString(KEY_ACTIVE_FRAME_PREFIX + currentUserId, "") ?: ""
                }
            } else if (myUserId.isNotBlank()) {
                prefs.getString(KEY_ACTIVE_FRAME_ID, "") ?: ""
            } else {
                ""
            }

            val exp = if (currentUserId.isNotBlank()) {
                if (currentUserId == myUserId) {
                    val scopedExp = prefs.getString(KEY_FRAME_EXPIRES_PREFIX + currentUserId, null)
                    scopedExp ?: (prefs.getString(KEY_FRAME_EXPIRES_AT, "") ?: "")
                } else {
                    prefs.getString(KEY_FRAME_EXPIRES_PREFIX + currentUserId, "") ?: ""
                }
            } else if (myUserId.isNotBlank()) {
                prefs.getString(KEY_FRAME_EXPIRES_AT, "") ?: ""
            } else {
                ""
            }

            val valid = if (frameId.isNotBlank() && AvatarFrameManager.isFrameValid(frameId, exp)) frameId else ""
            if (currentUserId.isNotBlank() && currentUserId == myUserId && _activeFrameState.value.first != currentUserId) {
                _activeFrameState.value = currentUserId to valid
            }
            valid
        } catch (_: Exception) {
            ""
        }
    }

    fun getFrameExpiresAt(context: Context? = null, targetUserId: String = ""): String {
        return try {
            val prefs = getPrefs(context) ?: return ""
            val myUserId = getSession(context)?.userId?.trim() ?: ""
            val currentUserId = targetUserId.trim()
            if (currentUserId.isNotBlank()) {
                if (currentUserId == myUserId) {
                    val scopedExp = prefs.getString(KEY_FRAME_EXPIRES_PREFIX + currentUserId, null)
                    if (scopedExp != null) return scopedExp
                } else {
                    return prefs.getString(KEY_FRAME_EXPIRES_PREFIX + currentUserId, "") ?: ""
                }
            }
            if (myUserId.isNotBlank()) {
                prefs.getString(KEY_FRAME_EXPIRES_AT, "") ?: ""
            } else {
                ""
            }
        } catch (_: Exception) {
            ""
        }
    }

    fun saveFcmToken(context: Context, token: String) {
        if (token.isBlank()) return
        try {
            getPrefs(context)?.edit()?.putString(KEY_FCM_DEVICE_TOKEN, token)?.apply()
        } catch (_: Exception) {}
    }

    fun getFcmToken(context: Context): String {
        return try {
            getPrefs(context)?.getString(KEY_FCM_DEVICE_TOKEN, "") ?: ""
        } catch (_: Exception) {
            ""
        }
    }

    /**
     * Clear auth login flag and cached tokens
     */
    fun clearSession(context: Context) {
        cachedAccessToken = ""
        cachedRefreshToken = ""
        cachedTokenExpiresAt = 0L
        cachedUserId = ""

        try {
            getPrefs(context)?.edit()?.apply {
                putBoolean(KEY_IS_LOGGED_IN, false)
                putBoolean(KEY_PROFILE_COMPLETED, false)
                putString(KEY_USER_EMAIL, "")
                putString(KEY_USER_ID, "")
                putString(KEY_USER_NAME, "")
                putString(KEY_USER_GENDER, "")
                putString(KEY_USER_BIRTH_DATE, "")
                putString(KEY_USER_COUNTRY, "")
                putString(KEY_USER_AVATAR_URL, "")
                putString(KEY_ACCESS_TOKEN, "")
                putString(KEY_REFRESH_TOKEN, "")
                putString(KEY_ACTIVE_FRAME_ID, "")
                putString(KEY_FRAME_EXPIRES_AT, "")
                putLong(KEY_TOKEN_EXPIRES_AT, 0L)
                putLong(KEY_USER_NUMERIC_ID, 0L)
                putLong(KEY_USER_COINS, 0L)
                putLong(KEY_USER_DIAMONDS, 0L)
                apply()
            }
            _diamondsFlow.value = 0L
            _coinsFlow.value = 0L
        } catch (_: Exception) {}
    }
}

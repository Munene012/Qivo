package com.example.data

import android.content.Context
import android.util.Log
import com.google.firebase.messaging.FirebaseMessaging
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import org.json.JSONObject
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.UUID
import java.util.concurrent.TimeUnit

/**
 * FCM Push Notification Service for QIVO.
 * 
 * Strict Constraint: Push notifications must ONLY be used for:
 * 1. New chat messages (CHAT_MESSAGE)
 * 2. Incoming calls (INCOMING_CALL)
 * 
 * Server-side security: All Firebase credentials and Supabase service-role credentials
 * reside strictly in Supabase Edge Functions. Device tokens are stored with RLS in Supabase.
 */
object SupabaseFcmService {
    private const val TAG = "SupabaseFcmService"

    const val TYPE_CHAT_MESSAGE = "CHAT_MESSAGE"
    const val TYPE_INCOMING_CALL = "INCOMING_CALL"

    const val EXTRA_NOTIFICATION_TYPE = "qivo_notification_type"
    const val EXTRA_RECIPIENT_USER_ID = "qivo_recipient_user_id"
    const val EXTRA_SENDER_USER_ID = "qivo_sender_user_id"
    const val EXTRA_SENDER_NAME = "qivo_sender_name"
    const val EXTRA_SENDER_AVATAR = "qivo_sender_avatar"
    const val EXTRA_CALL_TYPE = "qivo_call_type"
    const val EXTRA_CALL_ID = "qivo_call_id"
    const val EXTRA_MESSAGE_TEXT = "qivo_message_text"

    // Active screen tracker to suppress in-app notifications when already in conversation
    @Volatile var activeConversationUserId: String? = null

    private val httpClient = OkHttpClient.Builder()
        .connectTimeout(15, TimeUnit.SECONDS)
        .readTimeout(15, TimeUnit.SECONDS)
        .build()

    private val jsonMediaType = "application/json; charset=utf-8".toMediaType()

    /**
     * Create high-priority system notification channels for QIVO Push Notifications.
     * Must be called during Application startup.
     */
    fun createNotificationChannels(context: Context) {
        if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.O) {
            val notificationManager = context.getSystemService(Context.NOTIFICATION_SERVICE) as? android.app.NotificationManager ?: return

            // 1. Chat Messages Channel
            val chatChannel = android.app.NotificationChannel(
                "qivo_chat_notifications",
                "Chat Messages",
                android.app.NotificationManager.IMPORTANCE_HIGH
            ).apply {
                description = "Direct chat messages and replies"
                enableLights(true)
                enableVibration(true)
                vibrationPattern = longArrayOf(0, 250, 150, 250)
                lockscreenVisibility = android.app.Notification.VISIBILITY_PRIVATE
            }
            notificationManager.createNotificationChannel(chatChannel)

            // 2. Incoming Calls Channel
            val ringtoneUri = android.media.RingtoneManager.getDefaultUri(android.media.RingtoneManager.TYPE_RINGTONE)
                ?: android.media.RingtoneManager.getDefaultUri(android.media.RingtoneManager.TYPE_NOTIFICATION)
            val audioAttributes = android.media.AudioAttributes.Builder()
                .setContentType(android.media.AudioAttributes.CONTENT_TYPE_SONIFICATION)
                .setUsage(android.media.AudioAttributes.USAGE_NOTIFICATION_RINGTONE)
                .build()

            val callChannel = android.app.NotificationChannel(
                "qivo_call_notifications",
                "Incoming Calls",
                android.app.NotificationManager.IMPORTANCE_HIGH
            ).apply {
                description = "Incoming 1-on-1 voice and video calls"
                enableLights(true)
                enableVibration(true)
                vibrationPattern = longArrayOf(0, 500, 250, 500, 250, 500)
                setSound(ringtoneUri, audioAttributes)
                lockscreenVisibility = android.app.Notification.VISIBILITY_PUBLIC
            }
            notificationManager.createNotificationChannel(callChannel)
        }
    }

    /**
     * Ensure FirebaseApp instance is initialized before invoking Firebase APIs
     */
    fun ensureFirebaseInitialized(context: Context): Boolean {
        return try {
            val apps = com.google.firebase.FirebaseApp.getApps(context)
            if (apps.isNotEmpty()) {
                true
            } else {
                val app = com.google.firebase.FirebaseApp.initializeApp(context)
                app != null
            }
        } catch (e: Throwable) {
            Log.w(TAG, "FirebaseApp initialization check: ${e.message}")
            false
        }
    }

    /**
     * Initialize FCM: Retrieve current device token and register it in Supabase
     */
    fun initializeFcm(context: Context, userId: String) {
        if (userId.isBlank()) return
        try {
            ensureFirebaseInitialized(context)
            FirebaseMessaging.getInstance().token.addOnCompleteListener { task ->
                if (task.isSuccessful && !task.result.isNullOrBlank()) {
                    val token = task.result
                    Log.d(TAG, "Fetched FCM token for user $userId: ${token.take(10)}...")
                    UserSessionManager.saveFcmToken(context, token)
                    CoroutineScope(Dispatchers.IO).launch {
                        registerTokenInSupabase(context, userId, token)
                    }
                } else {
                    Log.w(TAG, "Fetching FCM registration token failed: ${task.exception?.message}")
                }
            }
        } catch (e: Throwable) {
            Log.e(TAG, "Error initializing FCM: ${e.message}")
        }
    }

    /**
     * Store FCM device token securely in Supabase tables "fcm_device_tokens" and "fcm_tokens" with RLS
     */
    suspend fun registerTokenInSupabase(context: Context, userId: String, token: String): Boolean {
        if (userId.isBlank() || token.isBlank()) return false
        return withContext(Dispatchers.IO) {
            try {
                val baseUrl = SupabaseConfig.supabaseUrl.trim().removeSuffix("/")
                val apiKey = SupabaseConfig.supabaseAnonKey.trim()
                if (baseUrl.isEmpty() || apiKey.isEmpty()) return@withContext false

                val isoDate = SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss.SSS'Z'", Locale.US).apply {
                    timeZone = java.util.TimeZone.getTimeZone("UTC")
                }.format(Date())

                val userToken = UserSessionManager.getValidAccessToken(context).ifBlank {
                    UserSessionManager.getAccessToken(context)
                }
                val authHeader = if (userToken.isNotBlank()) "Bearer $userToken" else "Bearer $apiKey"

                // 1. Save to fcm_device_tokens (schema: user_id, device_token, platform, updated_at)
                val deviceTokensBody = JSONObject().apply {
                    put("user_id", userId)
                    put("device_token", token)
                    put("platform", "android")
                    put("updated_at", isoDate)
                }.toString()

                val endpoint1 = "$baseUrl/rest/v1/fcm_device_tokens?on_conflict=device_token"
                val request1 = Request.Builder()
                    .url(endpoint1)
                    .addHeader("apikey", apiKey)
                    .addHeader("Authorization", authHeader)
                    .addHeader("Content-Type", "application/json")
                    .addHeader("Prefer", "resolution=merge-duplicates,return=minimal")
                    .post(deviceTokensBody.toRequestBody(jsonMediaType))
                    .build()

                var success = false
                httpClient.newCall(request1).execute().use { response ->
                    val code = response.code
                    if (response.isSuccessful || code in 200..204) {
                        Log.d(TAG, "Successfully registered FCM token in fcm_device_tokens for user $userId")
                        success = true
                    } else {
                        Log.w(TAG, "fcm_device_tokens upsert returned HTTP $code")
                    }
                }

                // If merge-duplicate upsert failed, attempt delete old token record and re-insert
                if (!success) {
                    try {
                        val delEndpoint = "$baseUrl/rest/v1/fcm_device_tokens?device_token=eq.$token"
                        val delReq = Request.Builder()
                            .url(delEndpoint)
                            .addHeader("apikey", apiKey)
                            .addHeader("Authorization", authHeader)
                            .delete()
                            .build()
                        httpClient.newCall(delReq).execute().close()

                        val reinsertReq = Request.Builder()
                            .url("$baseUrl/rest/v1/fcm_device_tokens")
                            .addHeader("apikey", apiKey)
                            .addHeader("Authorization", authHeader)
                            .addHeader("Content-Type", "application/json")
                            .post(deviceTokensBody.toRequestBody(jsonMediaType))
                            .build()
                        httpClient.newCall(reinsertReq).execute().use { reinsertResp ->
                            success = reinsertResp.isSuccessful || reinsertResp.code in 200..204
                        }
                    } catch (_: Exception) {}
                }

                // 2. Also save to fcm_tokens (schema: user_id, token, updated_at) for complete backward compatibility
                try {
                    val fcmTokensBody = JSONObject().apply {
                        put("user_id", userId)
                        put("token", token)
                        put("updated_at", isoDate)
                    }.toString()

                    val endpoint2 = "$baseUrl/rest/v1/fcm_tokens?on_conflict=token"
                    val request2 = Request.Builder()
                        .url(endpoint2)
                        .addHeader("apikey", apiKey)
                        .addHeader("Authorization", authHeader)
                        .addHeader("Content-Type", "application/json")
                        .addHeader("Prefer", "resolution=merge-duplicates,return=minimal")
                        .post(fcmTokensBody.toRequestBody(jsonMediaType))
                        .build()

                    httpClient.newCall(request2).execute().use { response2 ->
                        if (response2.isSuccessful || response2.code in 200..204) {
                            Log.d(TAG, "Synced token in fcm_tokens table")
                        }
                    }
                } catch (_: Exception) {}

                success
            } catch (e: Exception) {
                Log.e(TAG, "Exception registering token in Supabase", e)
                false
            }
        }
    }

    /**
     * Remove FCM token from Supabase upon logout
     */
    suspend fun removeTokenFromSupabase(context: Context, token: String): Boolean {
        if (token.isBlank()) return false
        return withContext(Dispatchers.IO) {
            try {
                val baseUrl = SupabaseConfig.supabaseUrl.trim().removeSuffix("/")
                val apiKey = SupabaseConfig.supabaseAnonKey.trim()
                if (baseUrl.isEmpty() || apiKey.isEmpty()) return@withContext false

                val endpoint = "$baseUrl/rest/v1/fcm_device_tokens?device_token=eq.$token"
                val authHeader = UserSessionManager.getAuthHeader(context)

                val request = Request.Builder()
                    .url(endpoint)
                    .addHeader("apikey", apiKey)
                    .addHeader("Authorization", authHeader)
                    .delete()
                    .build()

                val response = httpClient.newCall(request).execute()
                val success = response.isSuccessful || response.code in 200..204
                response.close()
                success
            } catch (e: Exception) {
                e.printStackTrace()
                false
            }
        }
    }

    /**
     * Format a message string for push notifications and in-app banners.
     * When a voice note or photo is sent, it returns strictly "[voice]" or "[photo]" without any URL or link.
     */
    fun formatNotificationText(rawMessage: String): String {
        val clean = rawMessage.trim()
        val lower = clean.lowercase()
        if (lower.startsWith("[voice]") ||
            lower.startsWith("voice_") ||
            lower.contains("/storage/v1/object/public/voice/") ||
            lower.contains("/storage/v1/object/voice/") ||
            lower.contains("/voice/") ||
            lower.contains(".m4a") ||
            lower.contains(".aac") ||
            lower.contains(".mp3") ||
            lower.contains(".wav") ||
            lower.contains(".ogg") ||
            lower.contains(".amr") ||
            lower.contains(".3gp") ||
            lower.contains(".opus")
        ) {
            return "[voice]"
        }
        if (clean.startsWith("[image]", ignoreCase = true) ||
            clean.startsWith("[photo]", ignoreCase = true) ||
            clean.startsWith("content://", ignoreCase = true) ||
            clean.startsWith("file://", ignoreCase = true) ||
            ((clean.startsWith("http://", ignoreCase = true) || clean.startsWith("https://", ignoreCase = true)) &&
            (clean.contains("/storage/") || clean.contains("/photos/") || clean.contains("/avatars/") ||
             clean.endsWith(".jpg", ignoreCase = true) || clean.endsWith(".jpeg", ignoreCase = true) ||
             clean.endsWith(".png", ignoreCase = true) || clean.endsWith(".webp", ignoreCase = true) ||
             clean.endsWith(".gif", ignoreCase = true)))
        ) {
            return "[photo]"
        }
        if (clean.startsWith("[gift]", ignoreCase = true)) {
            val giftName = clean.removePrefix("[gift]").trim()
            return if (giftName.isNotEmpty()) "🎁 $giftName" else "🎁 Gift"
        }
        return clean
    }

    /**
     * Dispatch New Chat Message Push Notification via Supabase Edge Function
     * Respects blocking security: verifies sender and recipient are not blocked
     */
    suspend fun sendChatPushNotification(
        context: Context,
        senderId: String,
        senderName: String,
        senderAvatar: String,
        receiverId: String,
        messageText: String
    ): Boolean {
        if (senderId.isBlank() || receiverId.isBlank() || messageText.isBlank()) return false

        // Respect blocking security
        val profileService = SupabaseProfileService()
        if (profileService.isUserBlocked(senderId, receiverId, context) ||
            profileService.isUserBlocked(receiverId, senderId, context)
        ) {
            Log.d(TAG, "Suppressed chat notification: user is blocked")
            return false
        }

        val cleanMessage = formatNotificationText(messageText)

        return withContext(Dispatchers.IO) {
            try {
                val baseUrl = SupabaseConfig.supabaseUrl.trim().removeSuffix("/")
                val apiKey = SupabaseConfig.supabaseAnonKey.trim()
                if (baseUrl.isEmpty() || apiKey.isEmpty()) return@withContext false

                val edgeFunctionUrl = "$baseUrl/functions/v1/send-push-notification"
                val rawAuth = UserSessionManager.getAuthHeader(context)
                val authHeader = if (rawAuth.isNotBlank()) rawAuth else "Bearer $apiKey"

                val payload = JSONObject().apply {
                    put("type", TYPE_CHAT_MESSAGE)
                    put("recipient_user_id", receiverId)
                    put("sender_id", senderId)
                    put("sender_name", senderName)
                    put("sender_avatar", senderAvatar)
                    put("message_text", cleanMessage)
                    put("title", senderName)
                    put("body", cleanMessage)
                    put("notification_title", senderName)
                    put("notification_body", cleanMessage)
                    put("message", cleanMessage)
                    put("sub_text", "You have a message")
                    put("conversation_id", senderId)
                    put("timestamp", SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss.SSS'Z'", Locale.US).apply {
                        timeZone = java.util.TimeZone.getTimeZone("UTC")
                    }.format(Date()))
                }.toString()

                val request = Request.Builder()
                    .url(edgeFunctionUrl)
                    .addHeader("apikey", apiKey)
                    .addHeader("Authorization", authHeader)
                    .addHeader("Content-Type", "application/json")
                    .post(payload.toRequestBody(jsonMediaType))
                    .build()

                httpClient.newCall(request).execute().use { response ->
                    val isOk = response.isSuccessful || response.code in 200..204
                    if (!isOk) {
                        val respBody = response.body?.string() ?: ""
                        Log.w(TAG, "send-push-notification for chat returned HTTP ${response.code}: $respBody")
                    } else {
                        Log.d(TAG, "send-push-notification for chat delivered successfully to recipient $receiverId")
                    }
                    isOk
                }
            } catch (e: Exception) {
                Log.e(TAG, "Error invoking send-push-notification Edge Function for chat", e)
                false
            }
        }
    }

    /**
     * Dispatch Incoming Call Push Notification via Supabase Edge Function
     * Respects blocking and call security
     */
    suspend fun sendCallPushNotification(
        context: Context,
        callerId: String,
        callerName: String,
        callerAvatar: String,
        receiverId: String,
        callType: String, // "VOICE" or "VIDEO"
        callId: String = UUID.randomUUID().toString()
    ): Boolean {
        if (callerId.isBlank() || receiverId.isBlank()) return false

        // Respect blocking security
        val profileService = SupabaseProfileService()
        if (profileService.isUserBlocked(callerId, receiverId, context) ||
            profileService.isUserBlocked(receiverId, callerId, context)
        ) {
            Log.d(TAG, "Suppressed call notification: user is blocked")
            return false
        }

        return withContext(Dispatchers.IO) {
            try {
                val baseUrl = SupabaseConfig.supabaseUrl.trim().removeSuffix("/")
                val apiKey = SupabaseConfig.supabaseAnonKey.trim()
                if (baseUrl.isEmpty() || apiKey.isEmpty()) return@withContext false

                val edgeFunctionUrl = "$baseUrl/functions/v1/send-push-notification"
                val rawAuth = UserSessionManager.getAuthHeader(context)
                val authHeader = if (rawAuth.isNotBlank()) rawAuth else "Bearer $apiKey"

                val payload = JSONObject().apply {
                    put("type", TYPE_INCOMING_CALL)
                    put("recipient_user_id", receiverId)
                    put("sender_id", callerId)
                    put("sender_name", callerName)
                    put("sender_avatar", callerAvatar)
                    put("call_type", callType.uppercase(Locale.ROOT))
                    put("call_id", callId)
                    put("timestamp", SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss.SSS'Z'", Locale.US).apply {
                        timeZone = java.util.TimeZone.getTimeZone("UTC")
                    }.format(Date()))
                }.toString()

                val request = Request.Builder()
                    .url(edgeFunctionUrl)
                    .addHeader("apikey", apiKey)
                    .addHeader("Authorization", authHeader)
                    .addHeader("Content-Type", "application/json")
                    .post(payload.toRequestBody(jsonMediaType))
                    .build()

                httpClient.newCall(request).execute().use { response ->
                    val isOk = response.isSuccessful || response.code in 200..204
                    if (!isOk) {
                        val respBody = response.body?.string() ?: ""
                        Log.w(TAG, "send-push-notification for call returned HTTP ${response.code}: $respBody")
                    } else {
                        Log.d(TAG, "send-push-notification for call delivered successfully to recipient $receiverId")
                    }
                    isOk
                }
            } catch (e: Exception) {
                Log.e(TAG, "Error invoking send-push-notification Edge Function for call", e)
                false
            }
        }
    }
}

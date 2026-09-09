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
     * Initialize FCM: Retrieve current device token and register it in Supabase
     */
    fun initializeFcm(context: Context, userId: String) {
        if (userId.isBlank()) return
        try {
            FirebaseMessaging.getInstance().token.addOnCompleteListener { task ->
                if (task.isSuccessful && !task.result.isNullOrBlank()) {
                    val token = task.result
                    Log.d(TAG, "Fetched FCM token for user $userId: ${token.take(10)}...")
                    UserSessionManager.saveFcmToken(context, token)
                    CoroutineScope(Dispatchers.IO).launch {
                        registerTokenInSupabase(context, userId, token)
                    }
                } else {
                    Log.w(TAG, "Fetching FCM registration token failed", task.exception)
                }
            }
        } catch (e: Throwable) {
            Log.e(TAG, "Error initializing FCM", e)
        }
    }

    /**
     * Store FCM device token securely in Supabase table "fcm_device_tokens" with RLS
     */
    suspend fun registerTokenInSupabase(context: Context, userId: String, token: String): Boolean {
        if (userId.isBlank() || token.isBlank()) return false
        return withContext(Dispatchers.IO) {
            try {
                val baseUrl = SupabaseConfig.supabaseUrl.trim().removeSuffix("/")
                val apiKey = SupabaseConfig.supabaseAnonKey.trim()
                if (baseUrl.isEmpty() || apiKey.isEmpty()) return@withContext false

                val endpoint = "$baseUrl/rest/v1/fcm_device_tokens?on_conflict=device_token"
                val isoDate = SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss.SSS'Z'", Locale.US).apply {
                    timeZone = java.util.TimeZone.getTimeZone("UTC")
                }.format(Date())

                val jsonBody = JSONObject().apply {
                    put("user_id", userId)
                    put("device_token", token)
                    put("platform", "android")
                    put("app_version", "1.0")
                    put("updated_at", isoDate)
                }.toString()

                val userToken = UserSessionManager.getValidAccessToken(context)
                val authHeader = if (userToken.isNotBlank()) "Bearer $userToken" else UserSessionManager.getAuthHeader(context)

                val request = Request.Builder()
                    .url(endpoint)
                    .addHeader("apikey", apiKey)
                    .addHeader("Authorization", authHeader)
                    .addHeader("Content-Type", "application/json")
                    .addHeader("Prefer", "resolution=merge-duplicates,return=minimal")
                    .post(jsonBody.toRequestBody(jsonMediaType))
                    .build()

                val response = httpClient.newCall(request).execute()
                val success = response.isSuccessful || response.code in 200..204
                val code = response.code
                response.close()

                if (success) {
                    Log.d(TAG, "Registered device token in Supabase for user $userId")
                    true
                } else {
                    Log.w(TAG, "Registration returned HTTP $code, attempting clean delete-then-insert")
                    // If conflict exists on user_id or device_token, delete existing entries for this token then re-insert
                    try {
                        val delEndpoint = "$baseUrl/rest/v1/fcm_device_tokens?device_token=eq.$token"
                        val delReq = Request.Builder()
                            .url(delEndpoint)
                            .addHeader("apikey", apiKey)
                            .addHeader("Authorization", authHeader)
                            .delete()
                            .build()
                        httpClient.newCall(delReq).execute().close()

                        // Re-insert clean row
                        val reinsertReq = Request.Builder()
                            .url("$baseUrl/rest/v1/fcm_device_tokens")
                            .addHeader("apikey", apiKey)
                            .addHeader("Authorization", authHeader)
                            .addHeader("Content-Type", "application/json")
                            .post(jsonBody.toRequestBody(jsonMediaType))
                            .build()
                        val reinsertResp = httpClient.newCall(reinsertReq).execute()
                        val reinsertOk = reinsertResp.isSuccessful || reinsertResp.code in 200..204
                        reinsertResp.close()
                        reinsertOk
                    } catch (_: Exception) {
                        false
                    }
                }
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
                val authHeader = UserSessionManager.getAuthHeader(context)

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
                    response.isSuccessful || response.code in 200..204
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
                val authHeader = UserSessionManager.getAuthHeader(context)

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
                    response.isSuccessful || response.code in 200..204
                }
            } catch (e: Exception) {
                Log.e(TAG, "Error invoking send-push-notification Edge Function for call", e)
                false
            }
        }
    }
}

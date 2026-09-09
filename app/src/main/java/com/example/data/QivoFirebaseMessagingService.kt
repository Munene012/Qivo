package com.example.data

import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Canvas
import android.media.AudioAttributes
import android.media.RingtoneManager
import android.os.Build
import android.util.Log
import androidx.core.app.NotificationCompat
import androidx.core.content.ContextCompat
import com.example.MainActivity
import com.example.R
import com.google.firebase.messaging.FirebaseMessagingService
import com.google.firebase.messaging.RemoteMessage
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import java.io.InputStream
import java.net.HttpURLConnection
import java.net.URL

/**
 * Firebase Messaging Service for QIVO.
 * 
 * Strict Constraint: Push notifications must ONLY be used for:
 * 1. New chat messages (CHAT_MESSAGE)
 * 2. Incoming calls (INCOMING_CALL)
 * 
 * Any other push notification type is rejected and ignored.
 */
class QivoFirebaseMessagingService : FirebaseMessagingService() {

    companion object {
        private const val TAG = "QivoFcmService"

        const val CHANNEL_ID_CHATS = "qivo_chat_notifications"
        const val CHANNEL_NAME_CHATS = "Chat Messages"

        const val CHANNEL_ID_CALLS = "qivo_call_notifications"
        const val CHANNEL_NAME_CALLS = "Incoming Calls"
    }

    override fun onCreate() {
        super.onCreate()
        createNotificationChannels()
    }

    override fun onNewToken(token: String) {
        super.onNewToken(token)
        Log.d(TAG, "Refreshed FCM token: ${token.take(10)}...")
        UserSessionManager.saveFcmToken(this, token)
        val session = UserSessionManager.getSession(this)
        if (session != null && session.userId.isNotBlank()) {
            CoroutineScope(Dispatchers.IO).launch {
                SupabaseFcmService.registerTokenInSupabase(
                    context = applicationContext,
                    userId = session.userId,
                    token = token
                )
            }
        }
    }

    override fun onMessageReceived(remoteMessage: RemoteMessage) {
        super.onMessageReceived(remoteMessage)
        Log.d(TAG, "From: ${remoteMessage.from}, data: ${remoteMessage.data}")

        val data = remoteMessage.data
        if (data.isEmpty()) return

        // 1. Strict validation: MUST be only CHAT_MESSAGE or INCOMING_CALL
        val rawType = data["type"] ?: data["notification_type"] ?: ""
        val notificationType = when (rawType.uppercase()) {
            SupabaseFcmService.TYPE_CHAT_MESSAGE, "NEW_CHAT_MESSAGE", "CHAT" -> SupabaseFcmService.TYPE_CHAT_MESSAGE
            SupabaseFcmService.TYPE_INCOMING_CALL, "CALL", "CALL_INCOMING" -> SupabaseFcmService.TYPE_INCOMING_CALL
            else -> {
                Log.d(TAG, "Ignored unsupported push notification type: $rawType")
                return
            }
        }

        // 2. Authentication Verification: Verify recipient is currently logged in
        val session = UserSessionManager.getSession(this)
        val currentUserId = session?.userId ?: ""
        val recipientUserId = data["recipient_user_id"] ?: data["receiver_id"] ?: ""

        if (currentUserId.isBlank() || (recipientUserId.isNotBlank() && !currentUserId.equals(recipientUserId, ignoreCase = true))) {
            Log.d(TAG, "Ignored notification: recipient mismatch or user not logged in (current: $currentUserId, target: $recipientUserId)")
            return
        }

        // 3. Blocking Security Verification: Ensure sender is not blocked
        val senderId = data["sender_id"] ?: data["caller_id"] ?: ""
        if (senderId.isBlank()) return

        val profileService = SupabaseProfileService()
        if (profileService.isUserBlocked(currentUserId, senderId, this) ||
            profileService.isUserBlocked(senderId, currentUserId, this)
        ) {
            Log.d(TAG, "Ignored notification: sender $senderId is blocked")
            return
        }

        val rawSenderName = data["sender_name"] ?: data["caller_name"] ?: remoteMessage.notification?.title ?: "QIVO User"
        val senderName = if (rawSenderName.contains("update", ignoreCase = true) || rawSenderName.isBlank()) "QIVO User" else rawSenderName
        val senderAvatar = data["sender_avatar"] ?: data["caller_avatar"] ?: ""

        val isAppInForeground = AppForegroundTracker.isAppInForeground()
        Log.d(TAG, "Notification received. isAppInForeground: $isAppInForeground, type: $notificationType")

        // 4. Handle verified notification types
        when (notificationType) {
            SupabaseFcmService.TYPE_CHAT_MESSAGE -> {
                val rawMessage = data["message_text"] ?: data["message"] ?: data["body"] ?: remoteMessage.notification?.body ?: "Sent you a message"
                val filteredMessage = if (rawMessage.contains("you have an update", ignoreCase = true) || rawMessage.isBlank()) "Sent you a message" else rawMessage
                val messageText = SupabaseFcmService.formatNotificationText(filteredMessage)
                // If user is currently looking at this conversation, do not show notification
                if (SupabaseFcmService.activeConversationUserId == senderId) {
                    Log.d(TAG, "Suppressed chat notification: conversation currently open")
                    return
                }

                if (isAppInForeground) {
                    // WHEN INSIDE THE APP: Show ONLY the in-app top notification popup
                    InAppNotificationManager.showNotification(
                        senderId = senderId,
                        senderName = senderName,
                        senderAvatar = senderAvatar,
                        messageText = messageText
                    )
                } else {
                    // WHEN OUT OF THE APP: Show the system status bar notification
                    showChatMessageNotification(
                        recipientId = currentUserId,
                        senderId = senderId,
                        senderName = senderName,
                        senderAvatar = senderAvatar,
                        messageText = messageText
                    )
                }
            }
            SupabaseFcmService.TYPE_INCOMING_CALL -> {
                val callType = data["call_type"] ?: "VOICE"
                val callId = data["call_id"] ?: ""

                // Party Room check: If receiver is in a party room, user cannot receive calls
                if (PartyRoomSessionManager.activeRoom.value != null) {
                    com.example.calling.CallRealtimeRelayManager.sendSignal(
                        com.example.calling.RealtimeCallSignal(
                            type = "BUSY_PARTY_ROOM",
                            callId = callId,
                            callerId = currentUserId,
                            callerName = "User",
                            callerAvatar = "",
                            callerGender = "Male",
                            receiverId = senderId,
                            callType = callType,
                            reason = "User is currently in a Party Room"
                        ),
                        context = this
                    )
                    return
                }

                // Dispatch signal to central ActiveCallSessionManager
                com.example.calling.CallRealtimeRelayManager.sendSignal(
                    com.example.calling.RealtimeCallSignal(
                        type = "OFFER",
                        callId = callId,
                        callerId = senderId,
                        callerName = senderName,
                        callerAvatar = senderAvatar,
                        callerGender = "Male",
                        receiverId = currentUserId,
                        callType = callType
                    ),
                    context = this
                )

                if (!isAppInForeground) {
                    // WHEN OUT OF THE APP: Show system incoming call notification
                    showIncomingCallNotification(
                        recipientId = currentUserId,
                        senderId = senderId,
                        senderName = senderName,
                        senderAvatar = senderAvatar,
                        callType = callType,
                        callId = callId
                    )
                }
            }
        }
    }

    private fun showChatMessageNotification(
        recipientId: String,
        senderId: String,
        senderName: String,
        senderAvatar: String,
        messageText: String
    ) {
        val intent = Intent(this, MainActivity::class.java).apply {
            action = Intent.ACTION_VIEW
            flags = Intent.FLAG_ACTIVITY_SINGLE_TOP or Intent.FLAG_ACTIVITY_CLEAR_TOP
            putExtra(SupabaseFcmService.EXTRA_NOTIFICATION_TYPE, SupabaseFcmService.TYPE_CHAT_MESSAGE)
            putExtra(SupabaseFcmService.EXTRA_RECIPIENT_USER_ID, recipientId)
            putExtra(SupabaseFcmService.EXTRA_SENDER_USER_ID, senderId)
            putExtra(SupabaseFcmService.EXTRA_SENDER_NAME, senderName)
            putExtra(SupabaseFcmService.EXTRA_SENDER_AVATAR, senderAvatar)
            putExtra(SupabaseFcmService.EXTRA_MESSAGE_TEXT, messageText)
        }

        val requestCode = (senderId.hashCode() and 0x7FFFFFFF)
        val pendingIntent = PendingIntent.getActivity(
            this,
            requestCode,
            intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        val largeIconBitmap = if (senderAvatar.isNotBlank()) {
            loadBitmapFromUrl(senderAvatar) ?: getSplashRoundLogoBitmap()
        } else {
            getSplashRoundLogoBitmap()
        }

        val notificationBuilder = NotificationCompat.Builder(this, CHANNEL_ID_CHATS)
            .setSmallIcon(R.drawable.ic_notification)
            .setColor(ContextCompat.getColor(this, R.color.qivo_orange))
            .setContentTitle(senderName)
            .setContentText(messageText)
            .setSubText("You have a message")
            .setStyle(NotificationCompat.BigTextStyle()
                .bigText(messageText)
                .setSummaryText("You have a message")
                .setBigContentTitle(senderName)
            )
            .setPriority(NotificationCompat.PRIORITY_HIGH)
            .setAutoCancel(true)
            .setContentIntent(pendingIntent)

        if (largeIconBitmap != null) {
            notificationBuilder.setLargeIcon(largeIconBitmap)
        }

        val notificationManager = getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        val notificationId = requestCode
        notificationManager.notify(notificationId, notificationBuilder.build())
    }

    private fun showIncomingCallNotification(
        recipientId: String,
        senderId: String,
        senderName: String,
        senderAvatar: String,
        callType: String,
        callId: String
    ) {
        val callIntent = Intent(this, MainActivity::class.java).apply {
            action = Intent.ACTION_VIEW
            flags = Intent.FLAG_ACTIVITY_SINGLE_TOP or Intent.FLAG_ACTIVITY_CLEAR_TOP
            putExtra(SupabaseFcmService.EXTRA_NOTIFICATION_TYPE, SupabaseFcmService.TYPE_INCOMING_CALL)
            putExtra(SupabaseFcmService.EXTRA_RECIPIENT_USER_ID, recipientId)
            putExtra(SupabaseFcmService.EXTRA_SENDER_USER_ID, senderId)
            putExtra(SupabaseFcmService.EXTRA_SENDER_NAME, senderName)
            putExtra(SupabaseFcmService.EXTRA_SENDER_AVATAR, senderAvatar)
            putExtra(SupabaseFcmService.EXTRA_CALL_TYPE, callType)
            putExtra(SupabaseFcmService.EXTRA_CALL_ID, callId)
        }

        val requestCode = (("call_$senderId").hashCode() and 0x7FFFFFFF)
        val pendingIntent = PendingIntent.getActivity(
            this,
            requestCode,
            callIntent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        val isVideo = callType.equals("VIDEO", ignoreCase = true)
        val callTitle = if (isVideo) "Incoming Video Call" else "Incoming Voice Call"
        val callDesc = "$senderName is calling you..."

        val largeIconBitmap = if (senderAvatar.isNotBlank()) {
            loadBitmapFromUrl(senderAvatar) ?: getSplashRoundLogoBitmap()
        } else {
            getSplashRoundLogoBitmap()
        }

        val defaultRingtoneUri = RingtoneManager.getDefaultUri(RingtoneManager.TYPE_RINGTONE)
            ?: RingtoneManager.getDefaultUri(RingtoneManager.TYPE_NOTIFICATION)

        val notificationBuilder = NotificationCompat.Builder(this, CHANNEL_ID_CALLS)
            .setSmallIcon(R.drawable.ic_notification)
            .setColor(ContextCompat.getColor(this, R.color.qivo_orange))
            .setContentTitle(callTitle)
            .setContentText(callDesc)
            .setSubText("Incoming Call")
            .setPriority(NotificationCompat.PRIORITY_MAX)
            .setCategory(NotificationCompat.CATEGORY_CALL)
            .setAutoCancel(true)
            .setSound(defaultRingtoneUri)
            .setVibrate(longArrayOf(0, 500, 250, 500, 250, 500))
            .setFullScreenIntent(pendingIntent, true)
            .setContentIntent(pendingIntent)

        if (largeIconBitmap != null) {
            notificationBuilder.setLargeIcon(largeIconBitmap)
        }

        val notificationManager = getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        notificationManager.notify(requestCode, notificationBuilder.build())
    }

    private fun createNotificationChannels() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val notificationManager = getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager

            // 1. Chat Messages Channel
            val chatChannel = NotificationChannel(
                CHANNEL_ID_CHATS,
                CHANNEL_NAME_CHATS,
                NotificationManager.IMPORTANCE_HIGH
            ).apply {
                description = "Direct chat messages and replies"
                enableLights(true)
                enableVibration(true)
            }
            notificationManager.createNotificationChannel(chatChannel)

            // 2. Incoming Calls Channel
            val ringtoneUri = RingtoneManager.getDefaultUri(RingtoneManager.TYPE_RINGTONE)
                ?: RingtoneManager.getDefaultUri(RingtoneManager.TYPE_NOTIFICATION)
            val audioAttributes = AudioAttributes.Builder()
                .setContentType(AudioAttributes.CONTENT_TYPE_SONIFICATION)
                .setUsage(AudioAttributes.USAGE_NOTIFICATION_RINGTONE)
                .build()

            val callChannel = NotificationChannel(
                CHANNEL_ID_CALLS,
                CHANNEL_NAME_CALLS,
                NotificationManager.IMPORTANCE_HIGH
            ).apply {
                description = "Incoming 1-on-1 voice and video calls"
                enableLights(true)
                enableVibration(true)
                vibrationPattern = longArrayOf(0, 500, 250, 500, 250, 500)
                setSound(ringtoneUri, audioAttributes)
            }
            notificationManager.createNotificationChannel(callChannel)
        }
    }

    private fun loadBitmapFromUrl(urlStr: String): Bitmap? {
        return try {
            val url = URL(urlStr)
            val connection = url.openConnection() as HttpURLConnection
            connection.doInput = true
            connection.connectTimeout = 3000
            connection.readTimeout = 3000
            connection.connect()
            val input: InputStream = connection.inputStream
            BitmapFactory.decodeStream(input)
        } catch (_: Throwable) {
            null
        }
    }

    private fun getSplashRoundLogoBitmap(): Bitmap? {
        return try {
            val drawable = ContextCompat.getDrawable(this, R.drawable.ic_qivo_splash_badge) ?: return null
            val size = 192
            val bitmap = Bitmap.createBitmap(size, size, Bitmap.Config.ARGB_8888)
            val canvas = Canvas(bitmap)
            drawable.setBounds(0, 0, canvas.width, canvas.height)
            drawable.draw(canvas)
            bitmap
        } catch (_: Throwable) {
            null
        }
    }
}

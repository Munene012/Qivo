package com.example.data

import android.util.Log
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

data class InAppNotification(
    val id: String = java.util.UUID.randomUUID().toString(),
    val senderId: String,
    val senderName: String,
    val senderAvatar: String,
    val messageText: String,
    val timeFormatted: String,
    val targetUser: UserProfile? = null
)

object InAppNotificationManager {
    private const val TAG = "InAppNotificationMgr"

    private val _currentNotification = MutableStateFlow<InAppNotification?>(null)
    val currentNotification: StateFlow<InAppNotification?> = _currentNotification.asStateFlow()

    private var autoDismissJob: Job? = null
    private val scope = CoroutineScope(Dispatchers.Main + Job())

    fun showNotification(
        senderId: String,
        senderName: String,
        senderAvatar: String,
        messageText: String,
        timeFormatted: String = "",
        targetUser: UserProfile? = null
    ) {
        if (senderId.isBlank() || messageText.isBlank()) return

        // Suppress if the user is currently chatting with this specific sender
        if (SupabaseFcmService.activeConversationUserId == senderId) {
            Log.d(TAG, "Suppressed in-app notification: active conversation open for $senderId")
            return
        }

        val resolvedTime = if (timeFormatted.isNotBlank()) {
            timeFormatted
        } else {
            SimpleDateFormat("HH:mm", Locale.getDefault()).format(Date())
        }

        val cleanMessageText = SupabaseFcmService.formatNotificationText(messageText)

        autoDismissJob?.cancel()
        _currentNotification.value = InAppNotification(
            senderId = senderId,
            senderName = senderName.ifBlank { "User" },
            senderAvatar = senderAvatar,
            messageText = cleanMessageText,
            timeFormatted = resolvedTime,
            targetUser = targetUser
        )

        // Auto-dismiss after 5 seconds
        autoDismissJob = scope.launch {
            delay(5000L)
            if (_currentNotification.value?.senderId == senderId) {
                _currentNotification.value = null
            }
        }
    }

    fun dismiss() {
        autoDismissJob?.cancel()
        _currentNotification.value = null
    }
}

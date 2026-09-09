package com.example.ui.screens
import com.example.ui.components.AppToast

import android.content.Context
import android.widget.Toast
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.platform.LocalContext
import com.example.calling.ActiveCallSessionManager
import com.example.data.PartyRoomSessionManager
import com.example.data.UserProfile
import com.example.data.UserSessionManager

enum class CallType {
    VOICE,
    VIDEO
}

enum class CallState {
    CONNECTING,
    RINGING,
    CONNECTED,
    ENDED
}

/**
 * Synchronous and instant direct call trigger.
 * Eliminates mounting latency and instantly displays the caller screen.
 */
fun launchDirectCall(
    context: Context,
    targetUser: UserProfile,
    callType: CallType,
    onInsufficientCoins: () -> Unit = {}
) {
    // Party Room Check: Cannot make calls while in a Party Room
    if (PartyRoomSessionManager.activeRoom.value != null) {
        AppToast.show("Cannot make calls while in a Party Room", isLong = true)
        return
    }

    val session = UserSessionManager.getSession(context)
    val myUser = if (session != null && session.userId.isNotBlank()) {
        UserProfile(
            id = session.userId,
            numericId = session.numericId,
            email = session.email,
            name = session.name,
            gender = session.gender,
            birthDate = session.birthDate,
            country = session.country,
            avatarUrl = session.avatarUrl,
            coins = session.coins
        )
    } else {
        val fallbackId = UserSessionManager.getUserId(context).ifBlank { "user_${System.currentTimeMillis()}" }
        UserProfile(
            id = fallbackId,
            numericId = 0L,
            email = "user@qivo.app",
            name = "User",
            gender = "Male",
            birthDate = "2000-01-01",
            country = "Kenya",
            avatarUrl = "",
            coins = 9999L
        )
    }

    ActiveCallSessionManager.startCall(
        caller = myUser,
        receiver = targetUser,
        callType = callType,
        context = context,
        onInsufficientCoins = onInsufficientCoins
    )
}

/**
 * CallDialog Adapter for backward compatibility.
 * Delegates directly to the central ActiveCallSessionManager.
 */
@Composable
fun CallDialog(
    targetUser: UserProfile,
    callType: CallType,
    onDismiss: () -> Unit,
    onRechargeRequired: () -> Unit = {}
) {
    val context = LocalContext.current
    LaunchedEffect(Unit) {
        launchDirectCall(
            context = context,
            targetUser = targetUser,
            callType = callType,
            onInsufficientCoins = onRechargeRequired
        )
        onDismiss()
    }
}


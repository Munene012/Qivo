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
import com.example.data.NetworkUtils
import com.example.data.SupabaseProfileService
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

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
    // 1. Offline prevention: Block immediately if offline!
    if (!NetworkUtils.isOnline(context)) {
        AppToast.show("Cannot make call. You are currently offline.", isLong = true)
        return
    }

    // Party Room Check: Cannot make calls while in a Party Room
    if (PartyRoomSessionManager.activeRoom.value != null) {
        AppToast.show("Cannot make calls while in a Party Room", isLong = true)
        return
    }

    val session = UserSessionManager.getSession(context)
    if (session == null || session.userId.isBlank()) {
        AppToast.show("Please login to initiate a call.", isLong = true)
        return
    }

    AppToast.show("Connecting call...")

    // 2. Fetch coin balance directly from server asynchronously
    val myUserId = session.userId
    val scope = CoroutineScope(Dispatchers.IO)
    scope.launch {
        try {
            val profileService = SupabaseProfileService()
            val freshProfile = profileService.fetchProfileById(myUserId)
            
            withContext(Dispatchers.Main) {
                if (freshProfile == null) {
                    AppToast.show("Failed to verify balance with server. Please try again.", isLong = true)
                    return@withContext
                }

                val serverCoins = freshProfile.coins
                // Update local session with freshest server coins
                UserSessionManager.saveCoins(context, serverCoins)

                val rate = if (callType == CallType.VIDEO) 160L else 80L
                val isCallerMale = freshProfile.gender.equals("Male", ignoreCase = true)

                // 3. Strictly check server coins before making the call
                if (isCallerMale && serverCoins < rate) {
                    AppToast.show("Insufficient coins. $rate coins required to start a ${callType.name.lowercase()} call.", isLong = true)
                    onInsufficientCoins()
                    return@withContext
                }

                // If checks pass, start the call session!
                ActiveCallSessionManager.startCall(
                    caller = freshProfile,
                    receiver = targetUser,
                    callType = callType,
                    context = context,
                    onInsufficientCoins = onInsufficientCoins
                )
            }
        } catch (e: Exception) {
            withContext(Dispatchers.Main) {
                AppToast.show("Network error. Failed to connect call.", isLong = true)
            }
        }
    }
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


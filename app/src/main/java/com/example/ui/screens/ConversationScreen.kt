package com.example.ui.screens
import com.example.ui.components.AppToast
import com.example.ui.theme.QivoYellow

import android.widget.Toast
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.Send
import androidx.compose.material.icons.filled.Call
import androidx.compose.material.icons.filled.CardGiftcard
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Image
import androidx.compose.material.icons.filled.Lock
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.input.pointer.pointerInput
import androidx.core.content.ContextCompat
import android.Manifest
import android.content.pm.PackageManager
import com.example.data.VoiceMessageManager
import com.example.data.VoiceRecordingState
import com.example.ui.components.VoiceMessageBubble
import com.example.ui.components.VoiceRecordingBar
import com.example.ui.components.Conversation3DBackButton
import com.example.ui.components.ConversationActionButton
import com.example.ui.components.Conversation3DSendButton
import com.example.ui.components.Conversation3DMicButton
import com.example.ui.components.PhotoGallery3DIcon
import com.example.ui.components.VoiceCall3DIcon
import com.example.ui.components.VideoCall3DIcon
import com.example.ui.components.GiftBox3DIcon
import com.example.ui.components.Gift3DIcon
import com.example.ui.components.SmileEmoji3DIcon
import com.example.ui.components.MoreTools3DIcon
import com.example.ui.components.SentGiftPreviewOverlay
import com.example.ui.components.resolveGift
import com.example.ui.components.HapticSoundFeedback
import com.example.ui.components.VerifiedBlueCheckBadge
import com.example.ui.components.VerifiedGreenShieldBadge
import com.example.ui.components.IntimacyCrystalHeartBadge
import com.example.ui.components.HexagonSafetyBadge
import com.example.ui.components.FloatingFreeChatCard
import com.example.ui.components.FloatingFastForwardPill
import com.example.ui.components.FaceAuthenticationBanner
import java.io.File
import androidx.compose.material.icons.filled.Mic
import androidx.compose.material.icons.filled.Videocam
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.zIndex
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import coil.compose.AsyncImage
import com.example.data.AppDataCacheManager

import coil.request.ImageRequest
import com.example.R
import com.example.data.AvatarHelper
import com.example.data.ChatMessage
import com.example.data.SupabaseChatService
import com.example.data.SupabaseFcmService
import com.example.data.SupabaseProfileService
import com.example.data.UserProfile
import com.example.data.UserSessionManager
import com.example.data.ViewOncePhotoManager
import com.example.ui.components.App3DMascotLoader
import com.example.ui.components.AppGift
import com.example.ui.components.GiftPadBottomSheet
import com.example.ui.components.InsufficientCoinsBottomSheet
import com.example.ui.components.Mic3DIcon
import com.example.ui.components.Send3DIcon
import com.example.ui.theme.AppTheme
import com.example.ui.theme.QivoGold
import com.example.ui.theme.QivoOrange
import kotlinx.coroutines.launch

@Composable
fun ConversationScreen(
    targetUser: UserProfile,
    onBackClick: () -> Unit,
    onOpenUserDetails: (UserProfile) -> Unit = {},
    onOpenRechargeWallet: () -> Unit = {}
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val listState = rememberLazyListState()
    val colors = AppTheme.colors
    val isDark = colors.isDark

    val chatService = remember { SupabaseChatService() }
    val profileService = remember { SupabaseProfileService() }
    val session = remember { UserSessionManager.getSession(context) }
    val myUserId = session?.userId ?: ""
    val myName = session?.name ?: "Me"
    val myAvatarUrl = session?.avatarUrl ?: ""

    val viewedPhotoKeys by ViewOncePhotoManager.viewedKeysFlow.collectAsState()
    LaunchedEffect(myUserId) {
        ViewOncePhotoManager.init(context, forceRefresh = true)
    }

    val initialConversationMessages = remember(myUserId, targetUser.id) {
        if (myUserId.isNotEmpty() && targetUser.id.isNotEmpty()) {
            val fromHolder = com.example.data.ChatStateHolder.messagesList.value.filter { msg ->
                (msg.senderId.trim().equals(myUserId, ignoreCase = true) && msg.receiverId.trim().equals(targetUser.id.trim(), ignoreCase = true)) ||
                (msg.receiverId.trim().equals(myUserId, ignoreCase = true) && msg.senderId.trim().equals(targetUser.id.trim(), ignoreCase = true))
            }.reversed()
            if (fromHolder.isNotEmpty()) {
                fromHolder.takeLast(40)
            } else {
                val all = SupabaseChatService.getInMemoryMessages(myUserId).ifEmpty {
                    AppDataCacheManager.getCachedChatMessagesSync(context, myUserId)
                }
                val filtered = all.filter { msg ->
                    (msg.senderId.trim().equals(myUserId, ignoreCase = true) && msg.receiverId.trim().equals(targetUser.id.trim(), ignoreCase = true)) ||
                    (msg.receiverId.trim().equals(myUserId, ignoreCase = true) && msg.senderId.trim().equals(targetUser.id.trim(), ignoreCase = true))
                }.reversed()
                filtered.takeLast(40)
            }
        } else emptyList()
    }

    var messagesList by remember { mutableStateOf(initialConversationMessages) }
    
    LaunchedEffect(messagesList.size) {
        if (messagesList.isNotEmpty()) {
            listState.animateScrollToItem(messagesList.size - 1)
        }
    }
    
    var isLoadingMessages by remember { mutableStateOf(initialConversationMessages.isEmpty()) }
    var hasOlderMessages by remember { mutableStateOf(true) }
    var isLoadingOlderMessages by remember { mutableStateOf(false) }
    var hasScrolledToBottomInitially by remember { mutableStateOf(false) }
    // Initialize message draft from persistent local storage
    var inputMessageText by remember(targetUser.id, myUserId) {
        mutableStateOf(AppDataCacheManager.getChatDraft(context, myUserId, targetUser.id))
    }
    var isSending by remember { mutableStateOf(false) }
    
    // Obtain freshest available in-memory profile instantly to prevent older profile photo flicker
    val globalProfilesMap by com.example.data.ChatStateHolder.profilesMap.collectAsState()
    val initialProfile = remember(targetUser.id, globalProfilesMap) {
        globalProfilesMap[targetUser.id.trim()] ?: targetUser
    }
    var liveTargetUser by remember(targetUser.id) { mutableStateOf(initialProfile) }

    // Auto-save draft as user types
    LaunchedEffect(inputMessageText, myUserId, targetUser.id) {
        AppDataCacheManager.saveChatDraft(context, myUserId, targetUser.id, inputMessageText)
    }

    // Keep viewed photo state synchronized with Supabase
    LaunchedEffect(messagesList) {
        if (messagesList.isNotEmpty()) {
            ViewOncePhotoManager.syncConversationPhotos(context, messagesList)
        }
    }


    // Periodically update target user profile to keep realtime online status in sync
    LaunchedEffect(targetUser.id, targetUser.numericId) {
        while (true) {
            val fresh = if (targetUser.id.isNotBlank()) {
                profileService.fetchProfile(targetUser.id, targetUser.email) ?: profileService.fetchProfileById(targetUser.id)
            } else {
                profileService.fetchProfileByNumericId(targetUser.numericId)
            }
            if (fresh != null) {
                liveTargetUser = fresh
                com.example.data.ChatStateHolder.updateSingleProfile(fresh, context)
            }
            kotlinx.coroutines.delay(10_000L)
        }
    }

    // Suppress push notifications for currently active conversation
    DisposableEffect(liveTargetUser.id) {
        SupabaseFcmService.activeConversationUserId = liveTargetUser.id
        onDispose {
            if (SupabaseFcmService.activeConversationUserId == liveTargetUser.id) {
                SupabaseFcmService.activeConversationUserId = null
            }
        }
    }

    var isBlockedByMe by remember { mutableStateOf(false) }
    var isBlockedByThem by remember { mutableStateOf(false) }
    var isTargetBlocked by remember { mutableStateOf(false) }
    var showInsufficientCoinsDialog by remember { mutableStateOf(false) }
    var insufficientCoinsRequired by remember { mutableLongStateOf(15L) }
    var userCurrentCoins by remember { mutableLongStateOf(session?.coins ?: 100L) }
    val flowCoins by UserSessionManager.coinsFlow.collectAsState()
    LaunchedEffect(flowCoins) {
        val coins = flowCoins
        if (coins != null && coins >= 0L) {
            userCurrentCoins = coins
        }
    }
    var myProfile by remember { mutableStateOf<UserProfile?>(null) }

    val senderGenderResolved = remember(myProfile?.gender, session?.gender) {
        val profG = (myProfile?.gender ?: "").trim()
        val sessG = (session?.gender ?: "").trim()
        val prefG = UserSessionManager.getGender(context).trim()
        fun isF(g: String) = g.equals("female", ignoreCase = true) ||
            g.equals("f", ignoreCase = true) ||
            g.equals("woman", ignoreCase = true) ||
            g.equals("w", ignoreCase = true) ||
            g.equals("girl", ignoreCase = true) ||
            g.equals("lady", ignoreCase = true)
        fun isM(g: String) = g.equals("male", ignoreCase = true) ||
            g.equals("m", ignoreCase = true) ||
            g.equals("man", ignoreCase = true)

        when {
            isF(profG) || isF(sessG) || isF(prefG) -> "Female"
            isM(profG) || isM(sessG) || isM(prefG) -> "Male"
            profG.isNotEmpty() -> profG
            sessG.isNotEmpty() -> sessG
            else -> prefG
        }
    }

    val isFemaleAcc = remember(senderGenderResolved, myProfile?.gender, session?.gender) {
        val profG = (myProfile?.gender ?: "").trim()
        val sessG = (session?.gender ?: "").trim()
        val prefG = UserSessionManager.getGender(context).trim()
        fun isF(g: String) = g.equals("female", ignoreCase = true) ||
            g.equals("f", ignoreCase = true) ||
            g.equals("woman", ignoreCase = true) ||
            g.equals("w", ignoreCase = true) ||
            g.equals("girl", ignoreCase = true) ||
            g.equals("lady", ignoreCase = true)
        isF(senderGenderResolved) || isF(profG) || isF(sessG) || isF(prefG)
    }

    val isMale = remember(senderGenderResolved, isFemaleAcc) {
        !isFemaleAcc && (
            senderGenderResolved.equals("Male", ignoreCase = true) ||
            senderGenderResolved.equals("m", ignoreCase = true) ||
            senderGenderResolved.equals("man", ignoreCase = true)
        )
    }

    var fastReplyRewardsMap by remember { mutableStateOf<Map<Long, Double>>(emptyMap()) }
    val isSenderAdmin = session?.isAdmin == true || myProfile?.isAdmin == true
    val isSenderCoinSeller = session?.isCoinSeller == true || myProfile?.isCoinSeller == true
    val isSenderAgent = session?.isAgent == true || myProfile?.isAgent == true
    val isReceiverAdmin = liveTargetUser.isAdmin
    val isReceiverCoinSeller = liveTargetUser.isCoinSeller
    val isReceiverAgent = liveTargetUser.isAgent
    // Female users, Admins, Coin Sellers, and Agents can text and be texted for free!
    val isExempt = isFemaleAcc || isSenderAdmin || isSenderCoinSeller || isSenderAgent || isReceiverAdmin || isReceiverCoinSeller || isReceiverAgent
    // For photo sending: ONLY admin senders, coin seller senders, and agent senders are exempt from photo coins (40 coins for all other users)
    val isPhotoExempt = isSenderAdmin || isSenderCoinSeller || isSenderAgent

    // Voice Recording State & Audio permission launcher
    val voiceRecordingState by VoiceMessageManager.recordingState.collectAsState()

    DisposableEffect(Unit) {
        onDispose {
            VoiceMessageManager.stopPlayback()
            VoiceMessageManager.stopRecording(cancel = true)
        }
    }

    val recordAudioLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestPermission()
    ) { isGranted ->
        if (isGranted) {
            VoiceMessageManager.startRecording(context)
        } else {
            AppToast.show("Microphone permission is required to record voice messages")
        }
    }

    // Gallery Picker for photo sharing (Accesses gallery screen directly, NO crop!)
    var showGalleryScreen by remember { mutableStateOf(false) }
    var fullScreenPhotoUrl by remember { mutableStateOf<String?>(null) }
    var showGiftPadBottomSheet by remember { mutableStateOf(false) }
    var activeSentGiftPreview by remember { mutableStateOf<AppGift?>(null) }

    // Synchronize coin balance & profile roles
    LaunchedEffect(myUserId, targetUser.id) {
        isBlockedByMe = profileService.isUserBlockedByMe(myUserId, targetUser.id, context)
        isBlockedByThem = profileService.isUserBlockedByThem(myUserId, targetUser.id, context)
        isTargetBlocked = isBlockedByMe || isBlockedByThem
        scope.launch {
            val remoteBlocked = profileService.checkIfBlockedBidirectionalRemote(myUserId, targetUser.id, context)
            if (remoteBlocked) {
                isBlockedByMe = profileService.isUserBlockedByMe(myUserId, targetUser.id, context)
                isBlockedByThem = profileService.isUserBlockedByThem(myUserId, targetUser.id, context)
                isTargetBlocked = true
            }
        }
        if (myUserId.isNotEmpty()) {
            val profile = profileService.fetchProfileById(myUserId)
            if (profile != null) {
                myProfile = profile
                userCurrentCoins = profile.coins
                UserSessionManager.saveCoins(context, profile.coins)
                UserSessionManager.saveRoles(context, profile.isAdmin, profile.isCoinSeller, profile.isAgent)
                if (profile.gender.isNotBlank()) {
                    UserSessionManager.saveGender(context, profile.gender)
                }
            } else {
                val fresh = profileService.fetchCoins(myUserId)
                if (fresh > 0L) {
                    userCurrentCoins = fresh
                    UserSessionManager.saveCoins(context, fresh)
                }
            }
        }
    }

    fun handleSendMessage(textToSend: String) {
        if (textToSend.isBlank() || isSending) return

        if (isBlockedByMe || profileService.isUserBlockedByMe(myUserId, targetUser.id, context)) {
            AppToast.show("You blocked this user.")
            return
        }
        if (isTargetBlocked || isBlockedByThem || profileService.isUserBlocked(myUserId, targetUser.id, context)) {
            AppToast.show("You have been blocked.")
            return
        }

        // Male non-exempt users must have at least 15 coins to text
        if (!isExempt && userCurrentCoins < 15) {
            showInsufficientCoinsDialog = true
            return
        }

        val text = textToSend.trim()
        inputMessageText = ""
        AppDataCacheManager.saveChatDraft(context, myUserId, targetUser.id, "")
        isSending = true


        val tempMsgId = -System.currentTimeMillis()
        val newMsg = ChatMessage(
            id = tempMsgId,
            senderId = myUserId,
            senderName = myName,
            senderAvatar = myAvatarUrl,
            receiverId = targetUser.id,
            receiverName = targetUser.name,
            message = text,
            createdAt = java.text.SimpleDateFormat("yyyy-MM-dd HH:mm:ss", java.util.Locale.getDefault()).format(java.util.Date())
        )
        messagesList = messagesList + newMsg

        scope.launch {
            if (profileService.checkIfBlockedBidirectionalRemote(myUserId, targetUser.id, context)) {
                messagesList = messagesList.filter { it.id != tempMsgId }
                isBlockedByMe = profileService.isUserBlockedByMe(myUserId, targetUser.id, context)
                isBlockedByThem = profileService.isUserBlockedByThem(myUserId, targetUser.id, context)
                isTargetBlocked = true
                isSending = false
                val toastMsg = if (isBlockedByMe) "You blocked this user." else "You have been blocked."
                AppToast.show(toastMsg)
                return@launch
            }

            if (!isExempt) {
                val deductResult = profileService.deductChatCoins(
                    senderId = myUserId,
                    senderGender = senderGenderResolved,
                    receiverId = targetUser.id,
                    receiverName = targetUser.name,
                    isAdmin = isSenderAdmin,
                    isCoinSeller = isSenderCoinSeller,
                    isAgent = isSenderAgent,
                    receiverIsAdmin = isReceiverAdmin,
                    receiverIsCoinSeller = isReceiverCoinSeller,
                    receiverIsAgent = isReceiverAgent
                )
                if (deductResult.first) {
                    userCurrentCoins = deductResult.second
                    UserSessionManager.saveCoins(context, deductResult.second)
                }
            }

            // Find last incoming message from target user before sending reply
            val lastIncomingMaleMsg = messagesList.lastOrNull { 
                it.senderId.trim().equals(targetUser.id.trim(), ignoreCase = true) && it.id > 0L 
            }

            val sendResult = chatService.sendMessageWithResult(
                senderId = myUserId,
                senderName = myName,
                senderAvatar = myAvatarUrl,
                receiverId = targetUser.id,
                receiverName = targetUser.name,
                messageText = text,
                context = context
            )
            if (!sendResult.isSuccess) {
                // If failed, remove the optimistic placeholder
                messagesList = messagesList.filter { it.id != tempMsgId }
                AppToast.show("Failed to deliver message. Check connection.")
            } else {
                val createdReplyId = sendResult.messageId
                // Replace optimistic ID with the actual server ID so polling and real-time will never duplicate it
                if (createdReplyId != null && createdReplyId > 0L) {
                    messagesList = messagesList.map {
                        if (it.id == tempMsgId) it.copy(id = createdReplyId, createdAt = sendResult.createdAt ?: it.createdAt) else it
                    }
                }
                if (createdReplyId != null && lastIncomingMaleMsg != null) {
                    // If female responding to male, trigger server-side Fast Reply reward calculation
                    val isMyFemale = (session?.gender ?: myProfile?.gender ?: "").equals("Female", ignoreCase = true)
                    val isTargetMale = targetUser.gender.equals("Male", ignoreCase = true)
                    if (isMyFemale && isTargetMale) {
                        launch {
                            try {
                                val rewardRes = chatService.claimFastReplyReward(
                                    originalMessageId = lastIncomingMaleMsg.id,
                                    replyMessageId = createdReplyId,
                                    context = context
                                )
                                if (rewardRes.first && rewardRes.second > 0.0) {
                                    fastReplyRewardsMap = fastReplyRewardsMap + (createdReplyId to rewardRes.second)
                                    val rewardFormatted = String.format(java.util.Locale.US, "%.2f", rewardRes.second)
                                    AppToast.show("⚡ Fast Reply Reward: +$rewardFormatted 💎", isLong = true)
                                }
                            } catch (_: Exception) {}
                        }
                    }
                }
            }
            isSending = false
        }
    }

    fun sendGift(gift: AppGift) {
        if (isBlockedByMe || profileService.isUserBlockedByMe(myUserId, targetUser.id, context)) {
            AppToast.show("You blocked this user.")
            return
        }
        if (isTargetBlocked || isBlockedByThem || profileService.isUserBlocked(myUserId, targetUser.id, context)) {
            AppToast.show("You have been blocked.")
            return
        }
        if (userCurrentCoins < gift.coins) {
            showGiftPadBottomSheet = false
            insufficientCoinsRequired = gift.coins
            showInsufficientCoinsDialog = true
            return
        }

        showGiftPadBottomSheet = false
        val giftPayload = "[gift]${gift.emoji} Sent ${gift.name} (${gift.coins} coins) to ${targetUser.name}"

        scope.launch {
            if (profileService.checkIfBlockedBidirectionalRemote(myUserId, targetUser.id, context)) {
                isBlockedByMe = profileService.isUserBlockedByMe(myUserId, targetUser.id, context)
                isBlockedByThem = profileService.isUserBlockedByThem(myUserId, targetUser.id, context)
                isTargetBlocked = true
                val toastMsg = if (isBlockedByMe) "You blocked this user." else "You have been blocked."
                AppToast.show(toastMsg)
                return@launch
            }

            // Find last incoming message from conversation partner for time percentage conversion
            val lastIncomingTargetMsg = messagesList.lastOrNull {
                it.senderId.trim().equals(targetUser.id.trim(), ignoreCase = true) && it.id > 0L
            }

            // Execute production-grade gift sending via Edge Function / RPC
            var deductResult = profileService.sendGiftSecure(
                recipientId = targetUser.id,
                giftId = gift.id,
                context = context,
                originalMessageId = lastIncomingTargetMsg?.id
            )
            if (!deductResult.first) {
                // Fallback to deductGiftCoins
                deductResult = profileService.deductGiftCoins(
                    senderId = myUserId,
                    giftName = gift.name,
                    coins = gift.coins,
                    receiverId = targetUser.id,
                    receiverName = targetUser.name,
                    isAdmin = session?.isAdmin == true || myProfile?.isAdmin == true,
                    isCoinSeller = session?.isCoinSeller == true || myProfile?.isCoinSeller == true
                )
            }
            if (!deductResult.first) {
                AppToast.show("Insufficient coins to send ${gift.name} (${gift.coins} coins)!", isLong = true)
                insufficientCoinsRequired = gift.coins
                showInsufficientCoinsDialog = true
                return@launch
            }
            userCurrentCoins = deductResult.second
            UserSessionManager.saveCoins(context, deductResult.second)

            val newMsg = ChatMessage(
                senderId = myUserId,
                senderName = myName,
                senderAvatar = myAvatarUrl,
                receiverId = targetUser.id,
                receiverName = targetUser.name,
                message = giftPayload,
                createdAt = java.text.SimpleDateFormat("yyyy-MM-dd HH:mm:ss", java.util.Locale.getDefault()).format(java.util.Date())
            )
            messagesList = messagesList + newMsg

            chatService.sendMessage(
                senderId = myUserId,
                senderName = myName,
                senderAvatar = myAvatarUrl,
                receiverId = targetUser.id,
                receiverName = targetUser.name,
                messageText = giftPayload,
                context = context
            )
            // activeSentGiftPreview = gift
            HapticSoundFeedback.performSuccess(context)
            HapticSoundFeedback.playGiftSentSound()
            AppToast.show("Sent ${gift.emoji} ${gift.name} to ${targetUser.name}!")
        }
    }

    fun handleSendVoiceMessage(audioFile: File, durationSec: Int) {
        if (durationSec < 1) {
            AppToast.show("Voice note is too short")
            return
        }
        if (isBlockedByMe || profileService.isUserBlockedByMe(myUserId, targetUser.id, context)) {
            AppToast.show("You blocked this user.")
            return
        }
        if (isTargetBlocked || isBlockedByThem || profileService.isUserBlocked(myUserId, targetUser.id, context)) {
            AppToast.show("You have been blocked.")
            return
        }
        val requiredCoins = 15L
        if (!isExempt && userCurrentCoins < requiredCoins) {
            insufficientCoinsRequired = requiredCoins
            showInsufficientCoinsDialog = true
            return
        }

        val tempMsgId = -System.currentTimeMillis()
        val tempPayload = "[voice]${durationSec}|${audioFile.absolutePath}"
        val tempMsg = ChatMessage(
            id = tempMsgId,
            senderId = myUserId,
            senderName = myName,
            senderAvatar = myAvatarUrl,
            receiverId = targetUser.id,
            receiverName = targetUser.name,
            message = tempPayload,
            createdAt = java.text.SimpleDateFormat("yyyy-MM-dd HH:mm:ss", java.util.Locale.getDefault()).format(java.util.Date())
        )
        messagesList = messagesList + tempMsg

        scope.launch {
            if (profileService.checkIfBlockedBidirectionalRemote(myUserId, targetUser.id, context)) {
                isBlockedByMe = profileService.isUserBlockedByMe(myUserId, targetUser.id, context)
                isBlockedByThem = profileService.isUserBlockedByThem(myUserId, targetUser.id, context)
                isTargetBlocked = true
                messagesList = messagesList.filter { it.id != tempMsgId }
                val toastMsg = if (isBlockedByMe) "You blocked this user." else "You have been blocked."
                AppToast.show(toastMsg)
                return@launch
            }

            if (!isExempt) {
                val deductResult = profileService.deductChatCoins(
                    senderId = myUserId,
                    senderGender = senderGenderResolved,
                    receiverId = targetUser.id,
                    receiverName = targetUser.name,
                    isAdmin = isSenderAdmin,
                    isCoinSeller = isSenderCoinSeller,
                    isAgent = isSenderAgent,
                    receiverIsAdmin = isReceiverAdmin,
                    receiverIsCoinSeller = isReceiverCoinSeller,
                    receiverIsAgent = isReceiverAgent
                )
                if (!deductResult.first) {
                    messagesList = messagesList.filter { it.id != tempMsgId }
                    AppToast.show("Insufficient coins to send voice note", isLong = true)
                    insufficientCoinsRequired = requiredCoins
                    showInsufficientCoinsDialog = true
                    return@launch
                }
                userCurrentCoins = deductResult.second
                UserSessionManager.saveCoins(context, deductResult.second)
            }

            val uploadedUrl = VoiceMessageManager.uploadVoiceMessage(audioFile)
            val finalVoicePayload = if (!uploadedUrl.isNullOrBlank()) {
                "[voice]${durationSec}|$uploadedUrl"
            } else {
                tempPayload
            }

            messagesList = messagesList.map {
                if (it.id == tempMsgId) it.copy(message = finalVoicePayload) else it
            }

            val isSuccessful = chatService.sendMessage(
                senderId = myUserId,
                senderName = myName,
                senderAvatar = myAvatarUrl,
                receiverId = targetUser.id,
                receiverName = targetUser.name,
                messageText = finalVoicePayload,
                context = context
            )

            if (!isSuccessful) {
                AppToast.show("Failed to deliver voice note")
                messagesList = messagesList.filter { it.id != tempMsgId }
            }
        }
    }

    BackHandler {
        if (showGiftPadBottomSheet) {
            showGiftPadBottomSheet = false
        } else if (showInsufficientCoinsDialog) {
            showInsufficientCoinsDialog = false
        } else if (fullScreenPhotoUrl != null) {
            fullScreenPhotoUrl = null
        } else if (showGalleryScreen) {
            showGalleryScreen = false
        } else {
            onBackClick()
        }
    }

    // Function to load older messages when user scrolls up to the top
    val loadOlderMessages: () -> Unit = {
        if (!isLoadingOlderMessages && hasOlderMessages && myUserId.isNotEmpty() && targetUser.id.isNotEmpty()) {
            scope.launch {
                try {
                    isLoadingOlderMessages = true
                    val currentFirstIndex = listState.firstVisibleItemIndex
                    val currentFirstOffset = listState.firstVisibleItemScrollOffset
                    val currentServerCount = messagesList.count { it.id > 0L }

                    val olderBatch = chatService.fetchConversationMessages(
                        userId1 = myUserId,
                        userId2 = targetUser.id,
                        context = context,
                        offset = currentServerCount,
                        limit = 20
                    )

                    if (olderBatch.isNotEmpty()) {
                        val existingIds = messagesList.map { it.id }.toSet()
                        val distinctOlder = olderBatch.filter { it.id !in existingIds }.reversed()
                        if (distinctOlder.isNotEmpty()) {
                            messagesList = distinctOlder + messagesList
                            listState.scrollToItem(
                                index = distinctOlder.size + currentFirstIndex,
                                scrollOffset = currentFirstOffset
                            )
                        }
                        if (olderBatch.size < 20 || distinctOlder.isEmpty()) {
                            hasOlderMessages = false
                        }
                    } else {
                        hasOlderMessages = false
                    }
                } catch (e: Throwable) {
                    android.util.Log.e("ConversationScreen", "Error loading older messages: ${e.message}")
                } finally {
                    isLoadingOlderMessages = false
                }
            }
        }
    }

    // Detect when user scrolls up to the top (first visible item) to fetch older messages
    val shouldLoadOlder by remember {
        derivedStateOf {
            val isAtTop = listState.firstVisibleItemIndex <= 1
            hasOlderMessages && !isLoadingOlderMessages && !isLoadingMessages && messagesList.isNotEmpty() && isAtTop
        }
    }

    LaunchedEffect(shouldLoadOlder) {
        if (shouldLoadOlder) {
            loadOlderMessages()
        }
    }

    // Fetch conversation messages (initial last 20) & periodic live synchronization
    LaunchedEffect(targetUser.id, targetUser.numericId, myUserId) {
        if (myUserId.isNotEmpty() && targetUser.id.isNotEmpty()) {
            var isFirstFetch = true
            while (true) {
                try {
                    val recent = chatService.fetchConversationMessages(
                        userId1 = myUserId,
                        userId2 = targetUser.id,
                        context = context,
                        offset = 0,
                        limit = 20
                    )

                    if (isFirstFetch) {
                        isFirstFetch = false
                        if (recent.isNotEmpty()) {
                            val chronological = recent.reversed()
                            val pendingOptimistic = messagesList.filter { it.id <= 0L }
                            val remainingOptimistic = pendingOptimistic.filter { opt ->
                                chronological.none { f ->
                                    f.senderId.trim().equals(opt.senderId.trim(), ignoreCase = true) &&
                                    f.receiverId.trim().equals(opt.receiverId.trim(), ignoreCase = true) &&
                                    (f.message == opt.message)
                                }
                            }
                            messagesList = (chronological + remainingOptimistic).distinctBy { 
                                if (it.id > 0L) "srv_${it.id}" else "opt_${it.id}_${it.message}" 
                            }
                            hasOlderMessages = recent.size >= 20
                        } else {
                            hasOlderMessages = false
                        }
                        isLoadingMessages = false

                        if (!hasScrolledToBottomInitially && messagesList.isNotEmpty()) {
                            listState.scrollToItem(messagesList.size - 1)
                            hasScrolledToBottomInitially = true
                        }
                    } else if (recent.isNotEmpty()) {
                        // Periodic sync: reconcile pending optimistic messages in place with server copies
                        val currentList = messagesList.toMutableList()
                        recent.forEach { serverMsg ->
                            val optIdx = currentList.indexOfFirst { opt ->
                                opt.id <= 0L &&
                                opt.senderId.trim().equals(serverMsg.senderId.trim(), ignoreCase = true) &&
                                opt.receiverId.trim().equals(serverMsg.receiverId.trim(), ignoreCase = true) &&
                                opt.message == serverMsg.message
                            }
                            if (optIdx != -1) {
                                currentList[optIdx] = serverMsg
                            }
                        }

                        val existingIds = currentList.map { it.id }.filter { it > 0L }.toSet()
                        val brandNew = recent.filter { it.id !in existingIds }.reversed()
                        if (brandNew.isNotEmpty()) {
                            val lastVisible = listState.layoutInfo.visibleItemsInfo.lastOrNull()?.index ?: 0
                            val totalItems = listState.layoutInfo.totalItemsCount
                            val isNearBottom = totalItems == 0 || lastVisible >= totalItems - 3
                            currentList.addAll(brandNew)
                            if (isNearBottom) {
                                listState.animateScrollToItem(currentList.size - 1)
                            }
                        }

                        // Update isRead status in place without disturbing ordering or scroll position
                        val recentMap = recent.associateBy { it.id }
                        val updatedList = currentList.map { msg ->
                            val r = recentMap[msg.id]
                            if (r != null && r.isRead != msg.isRead) {
                                msg.copy(isRead = r.isRead)
                            } else msg
                        }.distinctBy { 
                            if (it.id > 0L) "srv_${it.id}" else "opt_${it.id}_${it.message}" 
                        }

                        messagesList = updatedList
                    }

                    // If female account, sync fast reply reward records from ledger
                    if (isFemaleAcc) {
                        try {
                            val serverRewards = chatService.fetchFastReplyRewardsMap(myUserId, context)
                            if (serverRewards.isNotEmpty()) {
                                fastReplyRewardsMap = fastReplyRewardsMap + serverRewards
                            }
                        } catch (_: Exception) {}
                    }

                    // Live synchronize view-once photo viewed status so sender sees "Photo • Opened" in real time
                    if (messagesList.isNotEmpty()) {
                        ViewOncePhotoManager.syncConversationPhotos(context, messagesList)
                    }

                    // Mark incoming messages as read in Supabase
                    chatService.markMessagesAsRead(senderId = targetUser.id, receiverId = myUserId)
                } catch (e: Exception) {
                    isLoadingMessages = false
                }
                kotlinx.coroutines.delay(3_000L)
            }
        }
    }

    // Auto-scroll to bottom only on initial appearance if not yet scrolled
    LaunchedEffect(messagesList.isNotEmpty()) {
        if (messagesList.isNotEmpty() && !hasScrolledToBottomInitially) {
            listState.scrollToItem(messagesList.size - 1)
            hasScrolledToBottomInitially = true
        }
    }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(Color(0xFF381A05)) // Matching warm espresso background
            .statusBarsPadding()
            .navigationBarsPadding()
            .imePadding()
            .testTag("conversation_screen_root")
    ) {
        // Atmospheric Sunset Orange-Yellow Glow Overlay at Top
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .height(240.dp)
                .background(
                    Brush.verticalGradient(
                        listOf(
                            Color(0x66E65100), // Sunset orange
                            Color(0x33FF9100), // Amber gold
                            Color(0x10FFD54F), // Gold aura
                            Color.Transparent
                        )
                    )
                )
        )

        Column(modifier = Modifier.fillMaxSize()) {
            // 1. Top Bar Header matching screenshot
            Surface(
                modifier = Modifier
                    .fillMaxWidth()
                    .zIndex(10f),
                color = Color.Transparent
            ) {
                Column(modifier = Modifier.fillMaxWidth()) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .defaultMinSize(minHeight = 56.dp)
                            .padding(start = 12.dp, end = 12.dp, top = 8.dp, bottom = 4.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        // Back Arrow <
                        IconButton(
                            onClick = onBackClick,
                            modifier = Modifier.size(36.dp)
                        ) {
                            Icon(
                                imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                                contentDescription = "Back",
                                tint = Color.White,
                                modifier = Modifier.size(22.dp)
                            )
                        }

                        Spacer(modifier = Modifier.width(6.dp))

                        // Intimacy / Level Numeric Pill Badge (e.g. 112) removed as requested
                        /*
                        Surface(
                            shape = RoundedCornerShape(12.dp),
                            color = Color(0x33FFFFFF),
                            border = androidx.compose.foundation.BorderStroke(0.5.dp, Color(0x44FFFFFF)),
                            modifier = Modifier.height(26.dp)
                        ) {
                            Box(
                                modifier = Modifier.padding(horizontal = 8.dp),
                                contentAlignment = Alignment.Center
                            ) {
                                val displayLevel = if (liveTargetUser.numericId > 0L) {
                                    (liveTargetUser.numericId % 900 + 100).toString()
                                } else "112"
                                Text(
                                    text = displayLevel,
                                    fontSize = 13.sp,
                                    fontWeight = FontWeight.Bold,
                                    color = Color.White
                                )
                            }
                        }
                        */

                        Spacer(modifier = Modifier.width(8.dp))

                        // Target User Name + Badges + Online Status
                        Column(
                            modifier = Modifier
                                .weight(1f)
                                .clip(RoundedCornerShape(8.dp))
                                .clickable { onOpenUserDetails(liveTargetUser) }
                                .padding(vertical = 2.dp)
                        ) {
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(4.dp)
                            ) {
                                val nameWithHeart = if (liveTargetUser.name.contains("❤️") || liveTargetUser.name.contains("❤")) {
                                    liveTargetUser.name
                                } else {
                                    "${liveTargetUser.name.ifBlank { "User" }}❤️"
                                }

                                Text(
                                    text = nameWithHeart,
                                    fontSize = 17.sp,
                                    fontWeight = FontWeight.Bold,
                                    color = Color.White,
                                    maxLines = 1,
                                    overflow = androidx.compose.ui.text.style.TextOverflow.Ellipsis
                                )

                                VerifiedBlueCheckBadge(size = 14.dp)
                                VerifiedGreenShieldBadge(size = 14.dp)
                            }

                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(4.dp)
                            ) {
                                Box(
                                    modifier = Modifier
                                        .size(7.dp)
                                        .clip(CircleShape)
                                        .background(if (liveTargetUser.isOnline) Color(0xFF00E676) else Color(0xFF757575))
                                )
                                Text(
                                    text = if (liveTargetUser.isOnline) "Online" else "Offline",
                                    fontSize = 11.5.sp,
                                    color = if (liveTargetUser.isOnline) Color(0xFF00E676) else Color(0xFF9E9E9E),
                                    fontWeight = FontWeight.Medium
                                )
                            }
                        }

                        // Right Badges: [ 💜 0 ] and [ ⬡ ! ]
                        IntimacyCrystalHeartBadge(
                            count = 0,
                            onClick = {
                                onOpenUserDetails(liveTargetUser)
                            }
                        )

                        Spacer(modifier = Modifier.width(6.dp))

                        HexagonSafetyBadge(
                            onClick = {
                                onOpenUserDetails(liveTargetUser)
                            }
                        )
                    }

                    // Centered Down Arrow Dropdown ⌄ removed as requested
                    /*
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(bottom = 4.dp),
                        contentAlignment = Alignment.Center
                    ) {
                        Surface(
                            onClick = { onOpenUserDetails(liveTargetUser) },
                            shape = CircleShape,
                            color = Color(0x331F2232),
                            modifier = Modifier.size(20.dp)
                        ) {
                            Box(contentAlignment = Alignment.Center) {
                                Text(
                                    text = "⌄",
                                    fontSize = 13.sp,
                                    fontWeight = FontWeight.Bold,
                                    color = Color.White.copy(alpha = 0.7f)
                                )
                            }
                        }
                    }
                    */
                }
            }

            // Quick Greeting Phrases Horizontal Scroll Bar (User prompt requirement)
            val quickGreetings = remember {
                listOf(
                    "Hi! 👋",
                    "How are you? 😊",
                    "Nice to meet you! ✨",
                    "Free to voice talk? 🎙️",
                    "Send a gift 🎁",
                    "Hello beautiful! 🌸",
                    "What are you up to? 💬"
                )
            }
            androidx.compose.foundation.lazy.LazyRow(
                modifier = Modifier
                    .fillMaxWidth()
                    .background(if (isDark) Color(0xFF141120) else Color(0xFFFFF7ED))
                    .padding(horizontal = 10.dp, vertical = 6.dp),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                items(quickGreetings) { phrase ->
                    Surface(
                        onClick = {
                            if (!isTargetBlocked) {
                                handleSendMessage(phrase)
                            }
                        },
                        shape = RoundedCornerShape(16.dp),
                        color = if (isDark) Color(0xFF251F33) else Color.White,
                        border = androidx.compose.foundation.BorderStroke(1.dp, QivoOrange.copy(alpha = 0.5f)),
                        shadowElevation = 1.dp
                    ) {
                        Text(
                            text = phrase,
                            fontSize = 12.sp,
                            fontWeight = FontWeight.SemiBold,
                            color = if (isDark) Color(0xFFFFD54F) else Color(0xFFE65100),
                            modifier = Modifier.padding(horizontal = 11.dp, vertical = 5.dp)
                        )
                    }
                }
            }

            if (isTargetBlocked) {
                Surface(
                    modifier = Modifier
                        .fillMaxWidth()
                        .zIndex(9f),
                    color = Color(0xFFDC2626)
                ) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 14.dp, vertical = 8.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Text(
                            text = if (isBlockedByMe) "You have blocked ${liveTargetUser.name}. Messaging & calls disabled." else "You have been blocked by this user.",
                            color = Color.White,
                            fontSize = 12.sp,
                            fontWeight = FontWeight.Medium,
                            modifier = Modifier.weight(1f)
                        )
                        if (isBlockedByMe) {
                            Spacer(modifier = Modifier.width(8.dp))
                            Text(
                                text = "Unblock",
                                color = Color.White,
                                fontSize = 12.sp,
                                fontWeight = FontWeight.Bold,
                                modifier = Modifier
                                    .clip(RoundedCornerShape(8.dp))
                                    .background(Color.Black.copy(alpha = 0.25f))
                                    .clickable {
                                        scope.launch {
                                            profileService.unblockUser(myUserId, targetUser.id, context)
                                            isBlockedByMe = false
                                            isTargetBlocked = isBlockedByThem
                                            AppToast.show("${liveTargetUser.name} unblocked")
                                        }
                                    }
                                    .padding(horizontal = 10.dp, vertical = 4.dp)
                            )
                        }
                    }
                }
            }

            // 2. Chat Messages Area (takes remaining space and scrolls smoothly under the fixed top header)
            Box(
                modifier = Modifier
                    .weight(1f)
                    .fillMaxWidth()
                    .clipToBounds()
                    .background(colors.screenBg)
            ) {
                if (isLoadingMessages && messagesList.isEmpty()) {
                    Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                        App3DMascotLoader(
                            modifier = Modifier.fillMaxWidth(),
                            message = "Loading chat..."
                        )
                    }
                } else if (messagesList.isEmpty()) {
                    Column(
                        modifier = Modifier
                            .fillMaxSize()
                            .padding(24.dp),
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.Center
                    ) {
                        Surface(
                            shape = CircleShape,
                            color = if (isDark) Color(0xFF2C2C2C) else Color(0xFFFFF9C4),
                            modifier = Modifier.size(72.dp)
                        ) {
                            Box(contentAlignment = Alignment.Center) {
                                Icon(
                                    imageVector = Icons.Default.Image,
                                    contentDescription = null,
                                    tint = Color(0xFFFFB300),
                                    modifier = Modifier.size(36.dp)
                                )
                            }
                        }
                        Spacer(modifier = Modifier.height(16.dp))
                        Text(
                            text = "Say hi to ${targetUser.name}!",
                            color = colors.textPrimary,
                            fontWeight = FontWeight.Bold,
                            fontSize = 16.sp
                        )
                        Spacer(modifier = Modifier.height(4.dp))
                        Text(
                            text = "Send a message or share photos to start chatting.",
                            color = colors.textSecondary,
                            fontSize = 13.sp
                        )
                    }
                } else {
                    LazyColumn(
                        state = listState,
                        modifier = Modifier
                            .fillMaxSize()
                            .padding(horizontal = 16.dp, vertical = 12.dp),
                        verticalArrangement = Arrangement.spacedBy(10.dp, Alignment.Bottom)
                    ) {
                        if (isLoadingOlderMessages) {
                            item(key = "loading_older_messages_indicator") {
                                Box(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .padding(vertical = 8.dp),
                                    contentAlignment = Alignment.Center
                                ) {
                                    Row(
                                        verticalAlignment = Alignment.CenterVertically,
                                        horizontalArrangement = Arrangement.Center
                                    ) {
                                        CircularProgressIndicator(
                                            modifier = Modifier.size(16.dp),
                                            strokeWidth = 2.dp,
                                            color = QivoYellow
                                        )
                                        Spacer(modifier = Modifier.width(8.dp))
                                        Text(
                                            text = "Loading older messages...",
                                            fontSize = 12.sp,
                                            color = colors.textSecondary
                                        )
                                    }
                                }
                            }
                        }

                        items(
                            items = messagesList.distinctBy { if (it.message.startsWith("[gift]")) "${it.senderId}_${it.message.trim()}" else (if (it.id != 0L) it.id.toString() else "${it.senderId}_${it.createdAt}_${it.message.hashCode()}") },
                            key = { msg ->
                                if (msg.id != 0L) "msg_${msg.id}" else "local_${msg.senderId}_${msg.createdAt}_${msg.message.hashCode()}"
                            }
                        ) { msg ->
                            val isMe = msg.senderId == myUserId
                            val bubbleBg = if (isMe) (if (isDark) Color(0xFFBF360C) else Color(0xFFFFE0B2)) else colors.cardBg
                            val bubbleTextColor = if (isMe) (if (isDark) Color.White else Color.Black) else colors.textPrimary
                            val isVoiceMessage = msg.message.startsWith("[voice]", ignoreCase = true) ||
                                    msg.message.startsWith("voice_", ignoreCase = true) ||
                                    (msg.message.startsWith("http") && (msg.message.contains("/storage/v1/object/public/voice/") || msg.message.contains("/voice/") || msg.message.endsWith(".m4a", ignoreCase = true) || msg.message.endsWith(".aac", ignoreCase = true) || msg.message.endsWith(".3gp", ignoreCase = true) || msg.message.endsWith(".mp3", ignoreCase = true) || msg.message.endsWith(".wav", ignoreCase = true)))

                            val isPhotoMessage = !isVoiceMessage && (
                                    msg.message.startsWith("[image]", ignoreCase = true) ||
                                    msg.message.startsWith("[photo]", ignoreCase = true) ||
                                    msg.message.startsWith("content://") ||
                                    msg.message.startsWith("file://") ||
                                    (msg.message.startsWith("http") && (msg.message.contains("/storage/v1/object/public/photos/") || msg.message.contains("/photos/") || msg.message.contains("/avatars/") || msg.message.endsWith(".jpg", ignoreCase = true) || msg.message.endsWith(".png", ignoreCase = true) || msg.message.endsWith(".jpeg", ignoreCase = true) || msg.message.endsWith(".webp", ignoreCase = true) || msg.message.endsWith(".gif", ignoreCase = true)))
                            )

                            val photoUrlOrUri = if (msg.message.startsWith("[image]")) {
                                msg.message.removePrefix("[image]")
                            } else {
                                msg.message
                            }

                            // Calculate diamond rewards below the bubble (female accounts only for fast reply and received gifts)
                            val earnedDiamondsText: String? = if (isFemaleAcc) {
                                if (msg.message.startsWith("[gift]")) {
                                    // Received gift by female user: calculate diamonds
                                    if (!isMe) {
                                        val giftContent = msg.message.removePrefix("[gift]").trim()
                                        val matchedGift = resolveGift(giftContent)
                                        if (matchedGift != null && matchedGift.coins > 0L) {
                                            // Format cleanly with up to 2 decimal places if needed or integer
                                            val baseDiamonds = matchedGift.coins.toDouble()
                                            val dFormatted = if (baseDiamonds % 1.0 == 0.0) {
                                                String.format(java.util.Locale.US, "%,d", baseDiamonds.toLong())
                                            } else {
                                                String.format(java.util.Locale.US, "%.2f", baseDiamonds)
                                            }
                                            "+$dFormatted 💎"
                                        } else null
                                    } else null
                                } else if (isMe && fastReplyRewardsMap.containsKey(msg.id)) {
                                    // Fast reply reward earned by female user
                                    val rewardAmount = fastReplyRewardsMap[msg.id] ?: 0.0
                                    if (rewardAmount > 0.0) {
                                        val dFormatted = String.format(java.util.Locale.US, "%.2f", rewardAmount)
                                        "+$dFormatted 💎"
                                    } else null
                                } else null
                            } else null

                            Column(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalAlignment = if (isMe) Alignment.End else Alignment.Start
                            ) {
                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalArrangement = if (isMe) Arrangement.End else Arrangement.Start,
                                    verticalAlignment = Alignment.Bottom
                                ) {
                                    // For outgoing messages: read receipt is placed outside the bubble on the left side
                                    if (isMe) {
                                        ReadReceiptIcon(isRead = msg.isRead)
                                        Spacer(modifier = Modifier.width(6.dp))
                                    }

                                if (isPhotoMessage) {
                                    val photoCandidates = remember(msg.id, photoUrlOrUri) {
                                        ViewOncePhotoManager.generateCandidateKeys(msg.id, photoUrlOrUri)
                                    }
                                    val isPhotoAlreadyViewed = photoCandidates.any { viewedPhotoKeys.contains(it) } ||
                                            ViewOncePhotoManager.isPhotoViewed(context, msg.id, photoUrlOrUri)

                                    if (isMe) {
                                        // =========================================================================
                                        // SENDER BUBBLE: SENDER CANNOT OPEN/VIEW ONCE PHOTOS ONCE SENT
                                        // =========================================================================
                                        if (isPhotoAlreadyViewed) {
                                            // Recipient has already opened and viewed the photo
                                            Surface(
                                                shape = RoundedCornerShape(16.dp),
                                                color = if (isDark) Color(0xFF1E3322) else Color(0xFFE8F5E9),
                                                shadowElevation = 1.dp,
                                                modifier = Modifier
                                                    .widthIn(min = 140.dp, max = 220.dp)
                                                    .clickable {
                                                        AppToast.show("This photo was opened by the recipient.")
                                                    }
                                            ) {
                                                Row(
                                                    modifier = Modifier.padding(horizontal = 14.dp, vertical = 10.dp),
                                                    verticalAlignment = Alignment.CenterVertically
                                                ) {
                                                    // Muted View Once Circle with "1"
                                                    Box(
                                                        modifier = Modifier
                                                            .size(26.dp)
                                                            .clip(CircleShape)
                                                            .background(if (isDark) Color(0xFF262532) else Color(0xFFE0E0E0)),
                                                        contentAlignment = Alignment.Center
                                                    ) {
                                                        Text(
                                                            text = "1",
                                                            fontSize = 11.sp,
                                                            fontWeight = FontWeight.Bold,
                                                            color = if (isDark) Color(0xFF9E9E9E) else Color(0xFF757575)
                                                        )
                                                    }

                                                    Spacer(modifier = Modifier.width(10.dp))

                                                    Column {
                                                        Text(
                                                            text = "Photo",
                                                            fontSize = 13.sp,
                                                            fontWeight = FontWeight.SemiBold,
                                                            color = if (isDark) Color(0xFFE0E0E0) else Color(0xFF2E7D32)
                                                        )
                                                        Text(
                                                            text = "Opened",
                                                            fontSize = 11.sp,
                                                            fontWeight = FontWeight.Medium,
                                                            color = if (isDark) Color(0xFF9E9E9E) else Color(0xFF757575)
                                                        )
                                                    }
                                                }
                                            }
                                        } else {
                                            // Unopened by recipient: Sender sees "Photo • Sent", cannot open
                                            Surface(
                                                shape = RoundedCornerShape(16.dp),
                                                color = if (isDark) Color(0xFF2E2016) else Color(0xFFFFF3E0),
                                                shadowElevation = 1.dp,
                                                modifier = Modifier
                                                    .widthIn(min = 140.dp, max = 220.dp)
                                                    .clickable {
                                                        AppToast.show("You cannot view photos sent with View Once.")
                                                    }
                                            ) {
                                                Row(
                                                    modifier = Modifier.padding(horizontal = 14.dp, vertical = 10.dp),
                                                    verticalAlignment = Alignment.CenterVertically
                                                ) {
                                                    // View Once "1" glowing badge
                                                    Box(
                                                        modifier = Modifier
                                                            .size(26.dp)
                                                            .clip(CircleShape)
                                                            .background(
                                                                Brush.linearGradient(
                                                                    listOf(QivoOrange, QivoGold)
                                                                )
                                                            ),
                                                        contentAlignment = Alignment.Center
                                                    ) {
                                                        Text(
                                                            text = "1",
                                                            fontSize = 11.sp,
                                                            fontWeight = FontWeight.Black,
                                                            color = Color.Black
                                                        )
                                                    }

                                                    Spacer(modifier = Modifier.width(10.dp))

                                                    Column {
                                                        Text(
                                                            text = "Photo",
                                                            fontSize = 13.sp,
                                                            fontWeight = FontWeight.Bold,
                                                            color = if (isDark) Color(0xFFFFCC80) else Color(0xFFBF360C)
                                                        )
                                                        Text(
                                                            text = "Sent",
                                                            fontSize = 11.sp,
                                                            fontWeight = FontWeight.Medium,
                                                            color = if (isDark) Color(0xFFBCAAA4) else Color(0xFF8D6E63)
                                                        )
                                                    }
                                                }
                                            }
                                        }
                                    } else {
                                        // =========================================================================
                                        // RECIPIENT BUBBLE: Recipient can open once, then permanently locked
                                        // =========================================================================
                                        if (isPhotoAlreadyViewed) {
                                            // Permanently opened/expired state for recipient
                                            Surface(
                                                shape = RoundedCornerShape(16.dp),
                                                color = if (isDark) Color(0xFF181720) else Color(0xFFF1F3F5),
                                                shadowElevation = 1.dp,
                                                modifier = Modifier
                                                    .widthIn(min = 140.dp, max = 220.dp)
                                                    .clickable {
                                                        AppToast.show("This photo was already viewed and expired.")
                                                    }
                                            ) {
                                                Row(
                                                    modifier = Modifier.padding(horizontal = 14.dp, vertical = 10.dp),
                                                    verticalAlignment = Alignment.CenterVertically
                                                ) {
                                                    // Muted View Once Circle with "1"
                                                    Box(
                                                        modifier = Modifier
                                                            .size(26.dp)
                                                            .clip(CircleShape)
                                                            .background(if (isDark) Color(0xFF262532) else Color(0xFFE0E0E0)),
                                                        contentAlignment = Alignment.Center
                                                    ) {
                                                        Text(
                                                            text = "1",
                                                            fontSize = 11.sp,
                                                            fontWeight = FontWeight.Bold,
                                                            color = if (isDark) Color(0xFF9E9E9E) else Color(0xFF757575)
                                                        )
                                                    }

                                                    Spacer(modifier = Modifier.width(10.dp))

                                                    Column {
                                                        Text(
                                                            text = "Photo",
                                                            fontSize = 13.sp,
                                                            fontWeight = FontWeight.SemiBold,
                                                            color = colors.textPrimary
                                                        )
                                                        Text(
                                                            text = "Opened",
                                                            fontSize = 11.sp,
                                                            fontWeight = FontWeight.Medium,
                                                            color = if (isDark) Color(0xFF9E9E9E) else Color(0xFF757575)
                                                        )
                                                    }
                                                }
                                            }
                                        } else {
                                            // Unopened state for recipient: Tap to open once
                                            Surface(
                                                shape = RoundedCornerShape(16.dp),
                                                color = if (isDark) Color(0xFF181522) else Color(0xFF232030),
                                                shadowElevation = 4.dp,
                                                modifier = Modifier
                                                    .widthIn(min = 160.dp, max = 220.dp)
                                                    .clickable {
                                                        ViewOncePhotoManager.markPhotoAsViewed(context, msg.id, photoUrlOrUri)
                                                        fullScreenPhotoUrl = photoUrlOrUri
                                                    }
                                            ) {
                                                Box(
                                                    modifier = Modifier
                                                        .fillMaxWidth()
                                                        .padding(10.dp)
                                                ) {
                                                    Column(
                                                        modifier = Modifier.fillMaxWidth(),
                                                        horizontalAlignment = Alignment.CenterHorizontally
                                                    ) {
                                                        // Header tag with View Once badge
                                                        Row(
                                                            modifier = Modifier
                                                                .fillMaxWidth()
                                                                .clip(RoundedCornerShape(8.dp))
                                                                .background(Color.White.copy(alpha = 0.08f))
                                                                .padding(horizontal = 8.dp, vertical = 4.dp),
                                                            verticalAlignment = Alignment.CenterVertically,
                                                            horizontalArrangement = Arrangement.SpaceBetween
                                                        ) {
                                                            Row(verticalAlignment = Alignment.CenterVertically) {
                                                                // View Once "1" glowing badge
                                                                Box(
                                                                    modifier = Modifier
                                                                        .size(20.dp)
                                                                        .clip(CircleShape)
                                                                        .background(
                                                                            Brush.linearGradient(
                                                                                listOf(QivoOrange, QivoGold)
                                                                            )
                                                                        ),
                                                                    contentAlignment = Alignment.Center
                                                                ) {
                                                                    Text(
                                                                        text = "1",
                                                                        fontSize = 11.sp,
                                                                        fontWeight = FontWeight.Black,
                                                                        color = Color.Black
                                                                    )
                                                                }
                                                                Spacer(modifier = Modifier.width(6.dp))
                                                                Text(
                                                                    text = "View Once",
                                                                    fontSize = 11.sp,
                                                                    fontWeight = FontWeight.Bold,
                                                                    color = QivoGold
                                                                )
                                                            }

                                                            Icon(
                                                                imageVector = Icons.Default.Lock,
                                                                contentDescription = "Encrypted View Once",
                                                                tint = Color.White.copy(alpha = 0.7f),
                                                                modifier = Modifier.size(13.dp)
                                                            )
                                                        }

                                                        Spacer(modifier = Modifier.height(8.dp))

                                                        // Opaque Privacy Blur Container (Zero content visible before opening)
                                                        Box(
                                                            modifier = Modifier
                                                                .fillMaxWidth()
                                                                .height(105.dp)
                                                                .clip(RoundedCornerShape(12.dp))
                                                                .background(
                                                                    Brush.verticalGradient(
                                                                        listOf(
                                                                            Color(0xFF262235),
                                                                            Color(0xFF12101B)
                                                                        )
                                                                    )
                                                                ),
                                                            contentAlignment = Alignment.Center
                                                        ) {
                                                            // Ambient subtle glow aura
                                                            Canvas(modifier = Modifier.size(64.dp)) {
                                                                drawCircle(
                                                                    brush = Brush.radialGradient(
                                                                        colors = listOf(
                                                                            QivoOrange.copy(alpha = 0.35f),
                                                                            Color.Transparent
                                                                        )
                                                                    )
                                                                )
                                                            }

                                                            Column(
                                                                horizontalAlignment = Alignment.CenterHorizontally,
                                                                verticalArrangement = Arrangement.Center
                                                            ) {
                                                                Box(
                                                                    modifier = Modifier
                                                                        .size(40.dp)
                                                                        .clip(CircleShape)
                                                                        .background(
                                                                            Brush.linearGradient(
                                                                                listOf(QivoOrange, QivoGold)
                                                                            )
                                                                        ),
                                                                    contentAlignment = Alignment.Center
                                                                ) {
                                                                    Icon(
                                                                        imageVector = Icons.Default.Image,
                                                                        contentDescription = "View Once Photo",
                                                                        tint = Color.Black,
                                                                        modifier = Modifier.size(22.dp)
                                                                    )
                                                                }

                                                                Spacer(modifier = Modifier.height(6.dp))

                                                                Text(
                                                                    text = "Photo",
                                                                    fontSize = 12.sp,
                                                                    fontWeight = FontWeight.Bold,
                                                                    color = Color.White
                                                                )
                                                                Text(
                                                                    text = "Tap to view",
                                                                    fontSize = 10.sp,
                                                                    fontWeight = FontWeight.Medium,
                                                                    color = QivoGold
                                                                )
                                                            }
                                                        }
                                                    }
                                                }
                                            }
                                        }
                                    }
                                } else if (isVoiceMessage) {
                                    val rawVoice = msg.message.removePrefix("[voice]").trim()
                                    val durationSec = if (rawVoice.contains("|")) {
                                        rawVoice.substringBefore("|").toIntOrNull() ?: 0
                                    } else 0
                                    val audioUrl = if (rawVoice.contains("|")) {
                                        rawVoice.substringAfter("|")
                                    } else rawVoice

                                    VoiceMessageBubble(
                                        audioUrl = audioUrl,
                                        durationSeconds = durationSec,
                                        isMe = isMe,
                                        isDark = isDark
                                    )
                                } else if (msg.message.startsWith("[gift]")) {
                                    // Render Premium Custom 3D Gift Message Bubble
                                    val giftContent = msg.message.removePrefix("[gift]").trim()
                                    val matchedGift = resolveGift(giftContent)
                                    Surface(
                                        shape = RoundedCornerShape(20.dp),
                                        color = if (isMe) (if (isDark) Color(0xFF371B4F) else Color(0xFFF3E5F5)) else (if (isDark) Color(0xFF261833) else Color(0xFFFCE4EC)),
                                        shadowElevation = 3.dp,
                                        modifier = Modifier.widthIn(min = 210.dp, max = 295.dp)
                                    ) {
                                        Row(
                                            modifier = Modifier.padding(horizontal = 14.dp, vertical = 12.dp),
                                            verticalAlignment = Alignment.CenterVertically
                                        ) {
                                            // Custom 3D Gift Icon with glowing container
                                            Box(
                                                modifier = Modifier
                                                    .size(54.dp)
                                                    .clip(RoundedCornerShape(16.dp))
                                                    .background(
                                                        Brush.radialGradient(
                                                            listOf(
                                                                Color(0x55FFD54F),
                                                                Color(0x33E91E63),
                                                                Color(0x11000000)
                                                            )
                                                        )
                                                    ),
                                                contentAlignment = Alignment.Center
                                            ) {
                                                Gift3DIcon(
                                                    giftId = matchedGift?.id ?: "gift_box",
                                                    emoji = matchedGift?.emoji ?: "🎁",
                                                    size = 46.dp
                                                )
                                            }
                                            Spacer(modifier = Modifier.width(12.dp))
                                            Column(modifier = Modifier.weight(1f)) {
                                                Text(
                                                    text = matchedGift?.name ?: giftContent.ifEmpty { "Sent a gift" },
                                                    fontSize = 15.sp,
                                                    fontWeight = FontWeight.ExtraBold,
                                                    color = if (isDark) Color.White else Color(0xFF4A148C)
                                                )
                                                if (matchedGift != null) {
                                                    Spacer(modifier = Modifier.height(3.dp))
                                                    Surface(
                                                        shape = RoundedCornerShape(8.dp),
                                                        color = Color(0x33FFB300)
                                                    ) {
                                                        Row(
                                                            modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp),
                                                            verticalAlignment = Alignment.CenterVertically
                                                        ) {
                                                            Text(text = "🪙", fontSize = 11.sp)
                                                            Spacer(modifier = Modifier.width(4.dp))
                                                            Text(
                                                                text = "${String.format("%,d", matchedGift.coins)} coins",
                                                                fontSize = 11.sp,
                                                                fontWeight = FontWeight.Bold,
                                                                color = Color(0xFFFFB300)
                                                            )
                                                        }
                                                    }
                                                }
                                                Spacer(modifier = Modifier.height(3.dp))
                                                Text(
                                                    text = if (isMe) "Sent to ${targetUser.name}" else "Received from ${msg.senderName}",
                                                    fontSize = 11.sp,
                                                    fontWeight = FontWeight.Medium,
                                                    color = if (isDark) Color(0xAAFFFFFF) else Color(0x994A148C)
                                                )
                                            }
                                        }
                                    }
                                } else {
                                    // Render Text Message Bubble
                                    if (isMe) {
                                        Box(
                                            modifier = Modifier
                                                .clip(
                                                    RoundedCornerShape(
                                                        topStart = 18.dp,
                                                        topEnd = 18.dp,
                                                        bottomStart = 18.dp,
                                                        bottomEnd = 4.dp
                                                    )
                                                )
                                                .background(
                                                    Brush.linearGradient(
                                                        listOf(Color(0xFFFF8D00), Color(0xFFFF6500))
                                                    )
                                                )
                                                .padding(horizontal = 14.dp, vertical = 9.dp)
                                        ) {
                                            val cleanText = msg.message
                                                .replace(Regex("^\\[GLOBAL BLAST\\]\\s*", RegexOption.IGNORE_CASE), "")
                                                .replace(Regex("^GLOBAL BLAST:\\s*", RegexOption.IGNORE_CASE), "")
                                                .replace(Regex("^Global Blast:\\s*", RegexOption.IGNORE_CASE), "")
                                                .replace(Regex("^\\[BLAST\\]\\s*", RegexOption.IGNORE_CASE), "")
                                                .trim()
                                            Text(
                                                text = cleanText,
                                                fontSize = 15.sp,
                                                fontWeight = FontWeight.Medium,
                                                letterSpacing = 0.2.sp,
                                                color = Color.White
                                            )
                                        }
                                    } else {
                                        Surface(
                                            shape = RoundedCornerShape(
                                                topStart = 18.dp,
                                                topEnd = 18.dp,
                                                bottomStart = 4.dp,
                                                bottomEnd = 18.dp
                                            ),
                                            color = if (isDark) Color(0xFF221E31) else Color(0xFFF3F1F8),
                                            border = if (isDark) androidx.compose.foundation.BorderStroke(1.dp, Color(0x33FFFFFF)) else null,
                                            shadowElevation = 1.dp
                                        ) {
                                            Column(
                                                modifier = Modifier.padding(horizontal = 14.dp, vertical = 9.dp)
                                            ) {
                                                val cleanText = msg.message
                                                    .replace(Regex("^\\[GLOBAL BLAST\\]\\s*", RegexOption.IGNORE_CASE), "")
                                                    .replace(Regex("^GLOBAL BLAST:\\s*", RegexOption.IGNORE_CASE), "")
                                                    .replace(Regex("^Global Blast:\\s*", RegexOption.IGNORE_CASE), "")
                                                    .replace(Regex("^\\[BLAST\\]\\s*", RegexOption.IGNORE_CASE), "")
                                                    .trim()
                                                Text(
                                                    text = cleanText,
                                                    fontSize = 15.sp,
                                                    fontWeight = FontWeight.Normal,
                                                    letterSpacing = 0.2.sp,
                                                    color = if (isDark) Color.White else Color(0xFF1E1B2E)
                                                )
                                            }
                                        }
                                    }
                                }
                            }
                        }

                        // Badge shown strictly below the bubble for female accounts only
                        if (earnedDiamondsText != null) {
                            Spacer(modifier = Modifier.height(3.dp))
                            Surface(
                                shape = RoundedCornerShape(8.dp),
                                color = if (isDark) Color(0xFF261933) else Color(0xFFF3E5F5),
                                border = androidx.compose.foundation.BorderStroke(0.5.dp, Color(0xFFCE93D8)),
                                modifier = Modifier.padding(horizontal = 4.dp)
                            ) {
                                Row(
                                    modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp),
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Text(
                                        text = "Earned $earnedDiamondsText",
                                        fontSize = 11.sp,
                                        fontWeight = FontWeight.Bold,
                                        color = if (isDark) Color(0xFFA6FF4D) else Color(0xFF50A300)
                                    )
                                }
                            }
                        }
                    }
                }
                }
            }

            // 3. Bottom Input Bar & Action Buttons
            Surface(
                modifier = Modifier.fillMaxWidth(),
                color = colors.cardBg,
                shadowElevation = 6.dp
            ) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 12.dp, vertical = 8.dp)
                ) {
                    // Action Buttons Row (3D Icons: Photo, Call, Video, Gift)
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceEvenly,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        // 1. Photo 3D Button
                        ConversationActionButton(
                            text = "Photo",
                            icon = { PhotoGallery3DIcon(size = 20.dp) },
                            gradientColors = listOf(Color(0xFFFF8D00), Color(0xFFFF6500), Color(0xFFBF360C)),
                            shadowColor = Color(0xFFBF360C),
                            isEnabled = !isTargetBlocked,
                            onClick = {
                                if (!com.example.data.NetworkUtils.isOnline(context)) {
                                    AppToast.show("Cannot send photos while offline. Please check your internet connection.")
                                    return@ConversationActionButton
                                }
                                if (isBlockedByMe || profileService.isUserBlockedByMe(myUserId, targetUser.id, context)) {
                                    AppToast.show("You blocked this user.")
                                    return@ConversationActionButton
                                }
                                if (isTargetBlocked || isBlockedByThem) {
                                    AppToast.show("You have been blocked.")
                                    return@ConversationActionButton
                                }
                                if (!isPhotoExempt && userCurrentCoins < 40L) {
                                    insufficientCoinsRequired = 40L
                                    showInsufficientCoinsDialog = true
                                } else {
                                    showGalleryScreen = true
                                }
                            },
                            testTag = "conversation_photo_button"
                        )

                        // 2. Voice Call 3D Button
                        ConversationActionButton(
                            text = "Call",
                            icon = { VoiceCall3DIcon(size = 20.dp) },
                            gradientColors = listOf(Color(0xFFFF8D00), Color(0xFFFF6500), Color(0xFFBF360C)),
                            shadowColor = Color(0xFFFF6500),
                            isEnabled = !isTargetBlocked,
                            onClick = {
                                if (isBlockedByMe || profileService.isUserBlockedByMe(myUserId, targetUser.id, context)) {
                                    AppToast.show("You blocked this user.")
                                } else if (isTargetBlocked || isBlockedByThem) {
                                    AppToast.show("You have been blocked.")
                                } else if (!isExempt && userCurrentCoins < 80L) {
                                    insufficientCoinsRequired = 80L
                                    showInsufficientCoinsDialog = true
                                    AppToast.show("Insufficient coins. 80 coins required for voice call.")
                                } else {
                                    launchDirectCall(
                                        context = context,
                                        targetUser = targetUser,
                                        callType = CallType.VOICE,
                                        onInsufficientCoins = {
                                            insufficientCoinsRequired = 80L
                                            showInsufficientCoinsDialog = true
                                        }
                                    )
                                }
                            },
                            testTag = "conversation_call_button"
                        )

                        // 3. Video Call 3D Button
                        ConversationActionButton(
                            text = "Video",
                            icon = { VideoCall3DIcon(size = 20.dp) },
                            gradientColors = listOf(Color(0xFF00D2FF), Color(0xFF0072FF), Color(0xFF0051C9)),
                            shadowColor = Color(0xFF0072FF),
                            isEnabled = !isTargetBlocked,
                            onClick = {
                                if (isBlockedByMe || profileService.isUserBlockedByMe(myUserId, targetUser.id, context)) {
                                    AppToast.show("You blocked this user.")
                                } else if (isTargetBlocked || isBlockedByThem) {
                                    AppToast.show("You have been blocked.")
                                } else if (!isExempt && userCurrentCoins < 160L) {
                                    insufficientCoinsRequired = 160L
                                    showInsufficientCoinsDialog = true
                                    AppToast.show("Insufficient coins. 160 coins required for video call.")
                                } else {
                                    launchDirectCall(
                                        context = context,
                                        targetUser = targetUser,
                                        callType = CallType.VIDEO,
                                        onInsufficientCoins = {
                                            insufficientCoinsRequired = 160L
                                            showInsufficientCoinsDialog = true
                                        }
                                    )
                                }
                            },
                            testTag = "conversation_video_button"
                        )

                        // 4. Gift 3D Button
                        ConversationActionButton(
                            text = "Gift",
                            icon = { GiftBox3DIcon(size = 20.dp) },
                            gradientColors = listOf(Color(0xFFFFB74D), Color(0xFFFF8D00), Color(0xFFBF360C)),
                            shadowColor = Color(0xFFBF360C),
                            isEnabled = !isTargetBlocked,
                            onClick = {
                                if (isBlockedByMe || profileService.isUserBlockedByMe(myUserId, targetUser.id, context)) {
                                    AppToast.show("You blocked this user.")
                                    return@ConversationActionButton
                                }
                                if (isTargetBlocked || isBlockedByThem) {
                                    AppToast.show("You have been blocked.")
                                    return@ConversationActionButton
                                }
                                showGiftPadBottomSheet = true
                            },
                            testTag = "conversation_gift_button"
                        )
                    }

                    Spacer(modifier = Modifier.height(8.dp))

                    if (voiceRecordingState.isRecording) {
                        VoiceRecordingBar(
                            state = voiceRecordingState,
                            isDark = isDark,
                            onCancel = {
                                VoiceMessageManager.stopRecording(cancel = true)
                            },
                            onSend = {
                                val recorded = VoiceMessageManager.stopRecording(cancel = false)
                                if (recorded != null) {
                                    handleSendVoiceMessage(recorded.first, recorded.second)
                                }
                            }
                        )
                    } else {
                        // Text Input + Mic + Send
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            OutlinedTextField(
                                value = inputMessageText,
                                onValueChange = { if (!isTargetBlocked) inputMessageText = it },
                                enabled = !isTargetBlocked,
                                placeholder = {
                                    Text(
                                        text = if (isTargetBlocked) "Communication is disabled (blocked)" else "Message ${targetUser.name}...",
                                        color = colors.textSecondary,
                                        fontSize = 14.sp
                                    )
                                },
                                singleLine = true,
                                shape = RoundedCornerShape(24.dp),
                                colors = OutlinedTextFieldDefaults.colors(
                                    focusedTextColor = colors.textPrimary,
                                    unfocusedTextColor = colors.textPrimary,
                                    focusedBorderColor = Color(0xFFFFD600),
                                    unfocusedBorderColor = colors.cardBorder,
                                    focusedContainerColor = colors.screenBg,
                                    unfocusedContainerColor = colors.screenBg
                                ),
                                keyboardOptions = KeyboardOptions(imeAction = ImeAction.Send),
                                keyboardActions = KeyboardActions(
                                    onSend = {
                                        handleSendMessage(inputMessageText)
                                    }
                                ),
                                modifier = Modifier
                                    .weight(1f)
                                    .height(52.dp)
                                    .testTag("chat_input_field")
                            )

                            Spacer(modifier = Modifier.width(8.dp))

                            // 3D Mic button (Tap or Long-press to record voice note)
                            Box(
                                modifier = Modifier
                                    .pointerInput(isTargetBlocked) {
                                        detectTapGestures(
                                            onLongPress = {
                                                if (isTargetBlocked) {
                                                    AppToast.show("You have been blocked.")
                                                    return@detectTapGestures
                                                }
                                                if (ContextCompat.checkSelfPermission(context, Manifest.permission.RECORD_AUDIO) == PackageManager.PERMISSION_GRANTED) {
                                                    VoiceMessageManager.startRecording(context)
                                                } else {
                                                    recordAudioLauncher.launch(Manifest.permission.RECORD_AUDIO)
                                                }
                                            },
                                            onTap = {
                                                if (isTargetBlocked) {
                                                    AppToast.show("You have been blocked.")
                                                    return@detectTapGestures
                                                }
                                                if (ContextCompat.checkSelfPermission(context, Manifest.permission.RECORD_AUDIO) == PackageManager.PERMISSION_GRANTED) {
                                                    VoiceMessageManager.startRecording(context)
                                                } else {
                                                    recordAudioLauncher.launch(Manifest.permission.RECORD_AUDIO)
                                                }
                                            }
                                        )
                                    }
                                    .testTag("conversation_mic_button"),
                                contentAlignment = Alignment.Center
                            ) {
                                Conversation3DMicButton()
                            }

                            Spacer(modifier = Modifier.width(6.dp))

                            // 3D Send button
                            Conversation3DSendButton(
                                onClick = {
                                    handleSendMessage(inputMessageText)
                                },
                                isEnabled = !isTargetBlocked && inputMessageText.isNotBlank()
                            )
                        }
                    }
                }
            }
        }

        // Insufficient Coins Bottom Popup with direct Recharge Navigation
        if (showInsufficientCoinsDialog) {
            InsufficientCoinsBottomSheet(
                currentCoins = userCurrentCoins,
                requiredCoins = insufficientCoinsRequired,
                onDismiss = { showInsufficientCoinsDialog = false },
                onOpenWallet = {
                    showInsufficientCoinsDialog = false
                    onOpenRechargeWallet()
                },
                onCoinsUpdated = { updatedCoins ->
                    userCurrentCoins = updatedCoins
                    UserSessionManager.saveCoins(context, updatedCoins)
                }
            )
        }

        // Gift Pad Bottom Popup (10 coins to 50k coins)
        if (showGiftPadBottomSheet) {
            GiftPadBottomSheet(
                currentCoins = userCurrentCoins,
                isExempt = isExempt,
                targetRecipientName = targetUser.name,
                onDismiss = { showGiftPadBottomSheet = false },
                onRechargeClick = {
                    showGiftPadBottomSheet = false
                    onOpenRechargeWallet()
                },
                onSendGift = { gift ->
                    sendGift(gift)
                },
                onInsufficientCoins = { gift ->
                    showGiftPadBottomSheet = false
                    insufficientCoinsRequired = gift.coins
                    showInsufficientCoinsDialog = true
                }
            )
        }

        // Full-screen Photo Viewer (Tap photo to close or tap close icon; leaves back to ConversationScreen)
        val activePhoto = fullScreenPhotoUrl
        if (activePhoto != null) {
            Dialog(
                onDismissRequest = { fullScreenPhotoUrl = null },
                properties = DialogProperties(
                    usePlatformDefaultWidth = false,
                    decorFitsSystemWindows = false
                )
            ) {
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .background(Color.Black)
                        .clickable { fullScreenPhotoUrl = null },
                    contentAlignment = Alignment.Center
                ) {
                    val mascotRes = AvatarHelper.getDrawableForMascotKey(activePhoto)
                    if (mascotRes != null) {
                        Image(
                            painter = painterResource(id = mascotRes),
                            contentDescription = "Full Screen Mascot Photo",
                            modifier = Modifier
                                .fillMaxSize()
                                .padding(32.dp),
                            contentScale = ContentScale.Fit
                        )
                    } else {
                        AsyncImage(
                            model = ImageRequest.Builder(LocalContext.current)
                                .data(activePhoto)
                                .crossfade(true)
                                .build(),
                            contentDescription = "Full Screen Photo (Tap to close)",
                            modifier = Modifier.fillMaxSize(),
                            contentScale = ContentScale.Fit
                        )
                    }

                    // Top View Once Header Pill & Close button
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .statusBarsPadding()
                            .padding(16.dp)
                            .align(Alignment.TopCenter),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        IconButton(
                            onClick = { fullScreenPhotoUrl = null },
                            modifier = Modifier
                                .size(42.dp)
                                .clip(CircleShape)
                                .background(Color.Black.copy(alpha = 0.65f))
                        ) {
                            Icon(
                                imageVector = Icons.Default.Close,
                                contentDescription = "Close Full Screen",
                                tint = Color.White,
                                modifier = Modifier.size(24.dp)
                            )
                        }

                        // View Once Banner Capsule
                        Box(
                            modifier = Modifier
                                .clip(RoundedCornerShape(20.dp))
                                .background(Color.Black.copy(alpha = 0.75f))
                                .border(1.dp, QivoOrange.copy(alpha = 0.6f), RoundedCornerShape(20.dp))
                                .padding(horizontal = 14.dp, vertical = 6.dp)
                        ) {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Box(
                                    modifier = Modifier
                                        .size(18.dp)
                                        .clip(CircleShape)
                                        .background(Brush.linearGradient(listOf(QivoOrange, QivoGold))),
                                    contentAlignment = Alignment.Center
                                ) {
                                    Text(
                                        text = "1",
                                        fontSize = 10.sp,
                                        fontWeight = FontWeight.Black,
                                        color = Color.Black
                                    )
                                }
                                Spacer(modifier = Modifier.width(6.dp))
                                Text(
                                    text = "View Once • Expires when closed",
                                    fontSize = 12.sp,
                                    fontWeight = FontWeight.SemiBold,
                                    color = Color.White
                                )
                            }
                        }

                        Spacer(modifier = Modifier.size(42.dp))
                    }
                }
            }
        }

        // Direct Gallery Screen for Photo Sharing (Full Screen Overlay, NO unmounting chat)
        if (showGalleryScreen) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .zIndex(20f)
            ) {
                GalleryAndCropScreen(
                    userId = myUserId,
                    profileService = profileService,
                    enableCrop = false,
                    title = "Share Photo",
                    onBackClick = { showGalleryScreen = false },
                    onPhotoCroppedAndUploaded = { photoUriString ->
                        showGalleryScreen = false

                        if (!com.example.data.NetworkUtils.isOnline(context)) {
                            AppToast.show("Cannot send photos while offline. Please check your internet connection.")
                            return@GalleryAndCropScreen
                        }
                        if (isBlockedByMe || profileService.isUserBlockedByMe(myUserId, targetUser.id, context)) {
                            AppToast.show("You blocked this user.")
                            return@GalleryAndCropScreen
                        }

                        if (isTargetBlocked || isBlockedByThem || profileService.isUserBlocked(myUserId, targetUser.id, context)) {
                            AppToast.show("You have been blocked.")
                            return@GalleryAndCropScreen
                        }

                        // 1. Immediately display the photo in conversation screen before any network operations
                        val tempMsgId = -System.currentTimeMillis()
                        val optimisticMsg = ChatMessage(
                            id = tempMsgId,
                            senderId = myUserId,
                            senderName = myName,
                            senderAvatar = myAvatarUrl,
                            receiverId = targetUser.id,
                            receiverName = targetUser.name,
                            message = "[image]$photoUriString",
                            createdAt = java.text.SimpleDateFormat("yyyy-MM-dd HH:mm:ss", java.util.Locale.getDefault()).format(java.util.Date())
                        )
                        messagesList = messagesList + optimisticMsg

                        scope.launch {
                            try {
                                if (messagesList.isNotEmpty()) {
                                    listState.animateScrollToItem(messagesList.size - 1)
                                }
                            } catch (_: Exception) {}

                            // Final block check prior to deductions or upload
                            if (profileService.checkIfBlockedBidirectionalRemote(myUserId, targetUser.id, context)) {
                                messagesList = messagesList.filter { it.id != tempMsgId }
                                isBlockedByMe = profileService.isUserBlockedByMe(myUserId, targetUser.id, context)
                                isBlockedByThem = profileService.isUserBlockedByThem(myUserId, targetUser.id, context)
                                isTargetBlocked = true
                                val toastMsg = if (isBlockedByMe) "You blocked this user." else "You have been blocked."
                                AppToast.show(toastMsg)
                                return@launch
                            }

                            if (!isPhotoExempt) {
                                insufficientCoinsRequired = 40L
                                val deductResult = profileService.deductPhotoCoins(
                                    senderId = myUserId,
                                    senderGender = if (isFemaleAcc) "Female" else "Male",
                                    receiverId = targetUser.id,
                                    receiverName = targetUser.name,
                                    isAdmin = isSenderAdmin,
                                    isCoinSeller = isSenderCoinSeller,
                                    isAgent = isSenderAgent,
                                    receiverIsAdmin = false,
                                    receiverIsCoinSeller = false,
                                    receiverIsAgent = false,
                                    context = context
                                )
                                if (!deductResult.first) {
                                    messagesList = messagesList.filter { it.id != tempMsgId }
                                    insufficientCoinsRequired = 40L
                                    AppToast.show("Insufficient coins to send photo (40 coins required)", isLong = true)
                                    showInsufficientCoinsDialog = true
                                    return@launch
                                }
                                userCurrentCoins = deductResult.second
                                UserSessionManager.saveCoins(context, deductResult.second)
                            }

                            // Upload photo to Supabase storage to generate accessible public URL for receiver
                            var finalPhotoPayload = photoUriString
                            if (photoUriString.startsWith("content://") || photoUriString.startsWith("file://")) {
                                try {
                                    val userToken = UserSessionManager.getValidAccessToken(context)
                                    val uploaded = profileService.uploadProfilePhoto(context, myUserId, android.net.Uri.parse(photoUriString), accessToken = userToken)
                                    if (!uploaded.isNullOrBlank()) {
                                        finalPhotoPayload = uploaded
                                    }
                                } catch (e: Exception) {
                                    android.util.Log.w("ConversationScreen", "Photo upload warning: ${e.message}")
                                }
                            }

                            val imagePayload = "[image]$finalPhotoPayload"
                            if (finalPhotoPayload != photoUriString) {
                                messagesList = messagesList.map {
                                    if (it.id == tempMsgId) {
                                        it.copy(message = imagePayload)
                                    } else {
                                        it
                                    }
                                }
                            }

                            chatService.sendMessage(
                                senderId = myUserId,
                                senderName = myName,
                                senderAvatar = myAvatarUrl,
                                receiverId = targetUser.id,
                                receiverName = targetUser.name,
                                messageText = imagePayload,
                                context = context
                            )
                        }
                    }
                )
            }
        }

        // Sent 3D Gift Celebratory Full-Screen Preview Overlay
        if (activeSentGiftPreview != null) {
            SentGiftPreviewOverlay(
                gift = activeSentGiftPreview!!,
                recipientName = targetUser.name,
                senderName = "You",
                onDismiss = { activeSentGiftPreview = null }
            )
        }
    }
}

@Composable
private fun ReadReceiptIcon(isRead: Boolean) {
    if (isRead) {
        // A circle in green with a tick inside when read
        Box(
            modifier = Modifier
                .size(13.dp)
                .clip(CircleShape)
                .background(Color(0xFFFF9100)),
            contentAlignment = Alignment.Center
        ) {
            Icon(
                imageVector = Icons.Default.Check,
                contentDescription = "Read",
                tint = Color.White,
                modifier = Modifier.size(9.dp)
            )
        }
    } else {
        // An empty circle when unread
        Box(
            modifier = Modifier
                .size(11.dp)
                .clip(CircleShape)
                .background(Color.Transparent)
                .border(
                    width = 1.2.dp,
                    color = Color(0xFF9E9E9E),
                    shape = CircleShape
                )
        )
    }
}

data class ConversationGift(
    val id: String,
    val name: String,
    val emoji: String,
    val coins: Long
)

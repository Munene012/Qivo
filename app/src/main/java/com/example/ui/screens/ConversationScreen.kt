package com.example.ui.screens
import com.example.ui.components.AppToast

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
import com.example.ui.components.SentGiftPreviewOverlay
import com.example.ui.components.resolveGift
import com.example.ui.components.HapticSoundFeedback
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
    LaunchedEffect(Unit) {
        ViewOncePhotoManager.init(context)
    }

    val initialConversationMessages = remember(myUserId, targetUser.id) {
        if (myUserId.isNotEmpty() && targetUser.id.isNotEmpty()) {
            val all = SupabaseChatService.getInMemoryMessages(myUserId).ifEmpty {
                AppDataCacheManager.getCachedChatMessagesSync(context, myUserId)
            }
            all.filter { msg ->
                (msg.senderId.trim().equals(myUserId, ignoreCase = true) && msg.receiverId.trim().equals(targetUser.id, ignoreCase = true)) ||
                (msg.receiverId.trim().equals(myUserId, ignoreCase = true) && msg.senderId.trim().equals(targetUser.id, ignoreCase = true))
            }.reversed()
        } else emptyList()
    }

    var messagesList by remember { mutableStateOf(initialConversationMessages) }
    var isLoadingMessages by remember { mutableStateOf(initialConversationMessages.isEmpty()) }
    // Initialize message draft from persistent local storage
    var inputMessageText by remember(targetUser.id, myUserId) {
        mutableStateOf(AppDataCacheManager.getChatDraft(context, myUserId, targetUser.id))
    }
    var isSending by remember { mutableStateOf(false) }
    var liveTargetUser by remember { mutableStateOf(targetUser) }

    // Auto-save draft as user types
    LaunchedEffect(inputMessageText, myUserId, targetUser.id) {
        AppDataCacheManager.saveChatDraft(context, myUserId, targetUser.id, inputMessageText)
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
    var myProfile by remember { mutableStateOf<UserProfile?>(null) }
    val isMale = (session?.gender ?: "Male").equals("Male", ignoreCase = true)
    val isFemaleAcc = !isMale
    var fastReplyRewardsMap by remember { mutableStateOf<Map<Long, Double>>(emptyMap()) }
    val isSenderAdmin = session?.isAdmin == true || myProfile?.isAdmin == true
    val isSenderCoinSeller = session?.isCoinSeller == true || myProfile?.isCoinSeller == true
    val isSenderAgent = session?.isAgent == true || myProfile?.isAgent == true
    val isReceiverAdmin = liveTargetUser.isAdmin
    val isReceiverCoinSeller = liveTargetUser.isCoinSeller
    val isReceiverAgent = liveTargetUser.isAgent
    // Female users, Admins, Coin Sellers, and Agents can text and be texted for free!
    val isExempt = !isMale || isSenderAdmin || isSenderCoinSeller || isSenderAgent || isReceiverAdmin || isReceiverCoinSeller || isReceiverAgent

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
                    senderGender = session?.gender ?: "Male",
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

            val createdReplyId = chatService.sendMessageWithId(
                senderId = myUserId,
                senderName = myName,
                senderAvatar = myAvatarUrl,
                receiverId = targetUser.id,
                receiverName = targetUser.name,
                messageText = text,
                context = context
            )
            val sent = createdReplyId != null || chatService.sendMessage(
                senderId = myUserId,
                senderName = myName,
                senderAvatar = myAvatarUrl,
                receiverId = targetUser.id,
                receiverName = targetUser.name,
                messageText = text,
                context = context
            )
            if (!sent) {
                messagesList = messagesList.filter { it.id != tempMsgId }
            } else if (createdReplyId != null && lastIncomingMaleMsg != null) {
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
            activeSentGiftPreview = gift
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
                    senderGender = session?.gender ?: "Male",
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

    // Fetch conversation messages & periodic live synchronization
    LaunchedEffect(targetUser.id, targetUser.numericId) {
        if (myUserId.isNotEmpty()) {
            while (true) {
                try {
                    val allMsgs = chatService.fetchUserMessages(myUserId, context)
                    val filtered = allMsgs.filter { msg ->
                        (msg.senderId.trim().equals(myUserId, ignoreCase = true) && msg.receiverId.trim().equals(targetUser.id, ignoreCase = true)) ||
                        (msg.receiverId.trim().equals(myUserId, ignoreCase = true) && msg.senderId.trim().equals(targetUser.id, ignoreCase = true))
                    }.reversed()

                    val pendingOptimistic = messagesList.filter { it.id <= 0L }
                    val remainingOptimistic = pendingOptimistic.filter { opt ->
                        filtered.none { f ->
                            f.senderId == opt.senderId && f.receiverId == opt.receiverId &&
                            (f.message == opt.message)
                        }
                    }
                    messagesList = filtered + remainingOptimistic
                    isLoadingMessages = false

                    // If female account, sync fast reply reward records from ledger
                    if (isFemaleAcc) {
                        try {
                            val serverRewards = chatService.fetchFastReplyRewardsMap(myUserId, context)
                            if (serverRewards.isNotEmpty()) {
                                fastReplyRewardsMap = fastReplyRewardsMap + serverRewards
                            }
                        } catch (_: Exception) {}
                    }

                    // Mark incoming messages as read in Supabase
                    chatService.markMessagesAsRead(senderId = targetUser.id, receiverId = myUserId)
                } catch (_: Exception) {}
                kotlinx.coroutines.delay(3_000L)
            }
        }
    }

    // Auto-scroll to bottom when messages list updates or loads
    LaunchedEffect(messagesList.size) {
        if (messagesList.isNotEmpty()) {
            listState.animateScrollToItem(messagesList.size - 1)
        }
    }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(colors.screenBg)
            .statusBarsPadding()
            .navigationBarsPadding()
            .imePadding()
            .testTag("conversation_screen_root")
    ) {
        Column(modifier = Modifier.fillMaxSize()) {
            // 1. Fixed Top Bar Header
            Surface(
                modifier = Modifier
                    .fillMaxWidth()
                    .zIndex(10f),
                color = colors.cardBg,
                shadowElevation = 3.dp
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 8.dp, vertical = 8.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Conversation3DBackButton(
                        onClick = onBackClick,
                        isDark = isDark
                    )

                    Spacer(modifier = Modifier.width(8.dp))

                    // User Profile Info in Header (Clickable -> opens User Details)
                    Row(
                        modifier = Modifier
                            .weight(1f)
                            .clip(RoundedCornerShape(8.dp))
                            .clickable { onOpenUserDetails(liveTargetUser) }
                            .padding(vertical = 4.dp, horizontal = 2.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        // Avatar
                        Box(modifier = Modifier.size(42.dp)) {
                            com.example.data.AvatarHelper.UserAvatarImage(
                                avatarUrl = if (liveTargetUser.avatarUrl.isNotBlank()) liveTargetUser.avatarUrl else targetUser.avatarUrl,
                                userId = liveTargetUser.id.ifBlank { targetUser.id },
                                gender = liveTargetUser.gender.ifBlank { targetUser.gender },
                                numericId = if (liveTargetUser.numericId > 0L) liveTargetUser.numericId else targetUser.numericId,
                                contentDescription = liveTargetUser.name,
                                showFrame = true,
                                shape = CircleShape,
                                modifier = Modifier.fillMaxSize(),
                                contentScale = ContentScale.Crop
                            )
                        }

                        Spacer(modifier = Modifier.width(10.dp))

                        // Name + Status
                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                text = liveTargetUser.name.ifBlank { "QIVO User" },
                                fontSize = 16.sp,
                                fontWeight = FontWeight.Bold,
                                color = colors.textPrimary,
                                maxLines = 1,
                                overflow = androidx.compose.ui.text.style.TextOverflow.Ellipsis
                            )
                            if (liveTargetUser.isOnline) {
                                Row(verticalAlignment = Alignment.CenterVertically) {
                                    Box(
                                        modifier = Modifier
                                            .size(8.dp)
                                            .clip(CircleShape)
                                            .background(Color(0xFF4CAF50))
                                    )
                                    Spacer(modifier = Modifier.width(4.dp))
                                    Text(
                                        text = "Online",
                                        fontSize = 12.sp,
                                        color = Color(0xFF4CAF50),
                                        fontWeight = FontWeight.Medium
                                    )
                                }
                            }
                        }
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
                if (isLoadingMessages) {
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
                        items(
                            items = messagesList,
                            key = { msg ->
                                if (msg.id != 0L) "msg_${msg.id}" else "local_${msg.senderId}_${msg.createdAt}_${msg.message.hashCode()}"
                            }
                        ) { msg ->
                            val isMe = msg.senderId == myUserId
                            val bubbleBg = if (isMe) (if (isDark) Color(0xFF2E7D32) else Color(0xFFDCF8C6)) else colors.cardBg
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
                                    val photoKey = ViewOncePhotoManager.generatePhotoKey(msg.id, msg.senderId, msg.createdAt, photoUrlOrUri)
                                    val isPhotoAlreadyViewed = viewedPhotoKeys.contains(photoKey) || ViewOncePhotoManager.isPhotoViewed(context, photoKey)

                                    if (isPhotoAlreadyViewed) {
                                        // VIEWED / EXPIRED STATE: Permanent "Photo • Opened" badge that won't open again
                                        Surface(
                                            shape = RoundedCornerShape(16.dp),
                                            color = if (isMe) (if (isDark) Color(0xFF1E3322) else Color(0xFFE8F5E9)) else (if (isDark) Color(0xFF181720) else Color(0xFFF1F3F5)),
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
                                                        color = if (isMe) (if (isDark) Color(0xFFE0E0E0) else Color(0xFF2E7D32)) else colors.textPrimary
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
                                        // UNOPENED STATE: Completely obscured and blurred so user cannot see contents until tapped
                                        Surface(
                                            shape = RoundedCornerShape(16.dp),
                                            color = if (isDark) Color(0xFF181522) else Color(0xFF232030),
                                            shadowElevation = 4.dp,
                                            modifier = Modifier
                                                .widthIn(min = 160.dp, max = 220.dp)
                                                .clickable {
                                                    ViewOncePhotoManager.markPhotoAsViewed(context, photoKey)
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
                                    Surface(
                                        shape = RoundedCornerShape(
                                            topStart = 16.dp,
                                            topEnd = 16.dp,
                                            bottomStart = if (isMe) 16.dp else 4.dp,
                                            bottomEnd = if (isMe) 4.dp else 16.dp
                                        ),
                                        color = bubbleBg,
                                        border = if (!isMe && isDark) androidx.compose.foundation.BorderStroke(1.dp, colors.cardBorder) else null,
                                        shadowElevation = 1.dp
                                    ) {
                                        Column(
                                            modifier = Modifier.padding(horizontal = 14.dp, vertical = 8.dp)
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
                                                fontWeight = FontWeight.Light,
                                                letterSpacing = 0.2.sp,
                                                color = bubbleTextColor
                                            )
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
                                        color = if (isDark) Color(0xFFFF80AB) else Color(0xFFC2185B)
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
                            gradientColors = listOf(Color(0xFFFF9E79), Color(0xFFFF7043), Color(0xFFF4511E)),
                            shadowColor = Color(0xFFFF7043),
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
                                if (!isExempt && userCurrentCoins < 40) {
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
                            gradientColors = listOf(Color(0xFF00E676), Color(0xFF00C853), Color(0xFF008733)),
                            shadowColor = Color(0xFF00C853),
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
                            gradientColors = listOf(Color(0xFFFF4081), Color(0xFFE91E63), Color(0xFFC2185B)),
                            shadowColor = Color(0xFFE91E63),
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

                            if (!isExempt) {
                                val deductResult = profileService.deductPhotoCoins(
                                    senderId = myUserId,
                                    senderGender = session?.gender ?: "Male",
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
                                    AppToast.show("Insufficient coins to send photo", isLong = true)
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
                .background(Color(0xFF00E676)),
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

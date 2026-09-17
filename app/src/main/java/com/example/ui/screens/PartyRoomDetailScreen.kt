package com.example.ui.screens

import android.Manifest
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.media.AudioManager
import android.net.Uri
import android.os.Build
import android.provider.Settings
import android.widget.Toast
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.*
import androidx.compose.animation.core.*
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ExitToApp
import androidx.compose.material.icons.automirrored.filled.Send
import androidx.compose.material.icons.automirrored.filled.VolumeOff
import androidx.compose.material.icons.automirrored.filled.VolumeUp
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.blur
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.scale
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.core.content.ContextCompat
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import coil.compose.AsyncImage
import com.example.data.*
import com.example.data.AvatarHelper
import com.example.ui.components.AnimatedFloatingMusicNotes
import com.example.ui.components.AppGift
import com.example.ui.components.AppToast
import com.example.ui.components.GiftPadBottomSheet
import com.example.ui.components.Gift3DIcon
import com.example.ui.components.SentGiftPreviewOverlay
import com.example.ui.components.resolveGift
import com.example.ui.components.GiftRecipientOption
import com.example.ui.components.InsufficientCoinsBottomSheet
import com.example.ui.components.Mic3DIcon
import com.example.ui.components.MicMuted3DIcon
import com.example.ui.components.PartyReactionBottomSheet
import com.example.ui.components.SeatAnimatedReactionOverlay
import com.example.ui.components.SeatReaction
import com.example.ui.components.SeatReactionType
import com.example.ui.components.ALL_SEAT_REACTIONS
import com.example.ui.components.Seat3DIcon
import com.example.ui.components.Send3DIcon
import com.example.ui.components.PartyMusicBottomSheet
import com.example.ui.components.AnimatedMusicEqualizer
import com.example.ui.components.SwipeToDeleteSlider
import com.example.ui.components.TurntableVinylRecord
import com.example.ui.theme.QivoOrange
import com.example.ui.theme.QivoYellow
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun PartyRoomDetailScreen(
    room: PartyRoom,
    currentUserId: String,
    currentUserName: String,
    currentUserAvatar: String,
    onLeaveRoom: () -> Unit,
    onOpenRechargeWallet: () -> Unit = {}
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val partyService = remember { SupabasePartyService() }
    val profileService = remember { SupabaseProfileService() }
    val voiceEngine = remember { PartyRoomSessionManager.initializeEngine(context) }
    val session = remember { UserSessionManager.getSession(context) }

    var userCoins by remember { mutableLongStateOf(session?.coins ?: 100L) }
    var showInsufficientCoinsSheet by remember { mutableStateOf(false) }
    var requiredCoinsForGift by remember { mutableLongStateOf(10L) }
    val isExempt = session?.isAdmin == true || session?.isCoinSeller == true || (session?.gender != null && !session.gender.equals("Male", ignoreCase = true))

    // Sync latest coin balance
    LaunchedEffect(currentUserId) {
        if (currentUserId.isNotBlank()) {
            val fresh = profileService.fetchCoins(currentUserId)
            if (fresh > 0L) {
                userCoins = fresh
                UserSessionManager.saveCoins(context, fresh)
            }
        }
    }

    var currentRoom by remember { mutableStateOf(room) }
    var seats by remember { mutableStateOf(partyService.getRoomSeats(room.id, room.seatsCount)) }
    var messages by remember { mutableStateOf(partyService.getRoomMessages(room.id)) }
    var roomAdmins by remember { mutableStateOf<Set<String>>(emptySet()) }
    var categorizedMembers by remember { mutableStateOf<List<PartyRoomMember>>(emptyList()) }

    val isRoomOwner = currentUserId.isNotBlank() && (currentUserId == currentRoom.hostUserId || currentRoom.hostUserId.isBlank())
    val isRoomAdmin = isRoomOwner || roomAdmins.contains(currentUserId)

    val mySeatIndex = seats.indexOfFirst { it.userId == currentUserId && it.userId.isNotBlank() }
    val isSeated = mySeatIndex != -1

    var isMicMuted by remember { mutableStateOf(PartyRoomSessionManager.isMicMuted.value) }
    var currentSpeakingAmplitude by remember { mutableFloatStateOf(0f) }

    var showGiftDialog by remember { mutableStateOf(false) }
    var initialSelectedGiftRecipientId by remember { mutableStateOf<String?>(null) }
    var showReactionDialog by remember { mutableStateOf(false) }
    var showMusicBottomSheet by remember { mutableStateOf(false) }
    val musicTrackInfo by PartyMusicManager.trackInfo.collectAsState()
    val isMusicPlaying by PartyMusicManager.isPlaying.collectAsState()
    var activeSeatReactions by remember { mutableStateOf<Map<Int, Pair<SeatReactionType, Long>>>(emptyMap()) }
    var showMembersPanel by remember { mutableStateOf(false) }
    var showAdminManageDialog by remember { mutableStateOf(false) }
    var showSeatCapacityDialog by remember { mutableStateOf(false) }
    var showRoomSettingsDialog by remember { mutableStateOf(false) }
    var isUploadingRoomMedia by remember { mutableStateOf(false) }
    var showLeaveConfirmDialog by remember { mutableStateOf(false) }
    var showDeleteConfirmDialog by remember { mutableStateOf(false) }
    var isDeletingRoom by remember { mutableStateOf(false) }
    var activeGiftBanner by remember { mutableStateOf<String?>(null) }
    var activeGiftBannerGift by remember { mutableStateOf<AppGift?>(null) }
    var activeGiftRecipientName by remember { mutableStateOf<String>("") }
    var activeSentGiftPreview by remember { mutableStateOf<AppGift?>(null) }
    var selectedSeatForAction by remember { mutableStateOf<PartySeat?>(null) }
    var selectedMemberForAction by remember { mutableStateOf<PartyRoomMember?>(null) }
    var pendingSeatIndexToTake by remember { mutableStateOf<Int?>(null) }
    var messageInputText by remember { mutableStateOf("") }

    val listState = rememberLazyListState()

    var isLeavingRoom by remember { mutableStateOf(false) }

    // Room Cover & Background Image Pickers
    val roomCoverPickerLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.PickVisualMedia()
    ) { uri: Uri? ->
        if (uri != null) {
            isUploadingRoomMedia = true
            scope.launch {
                val uploaded = partyService.uploadPartyImage(context, uri, "covers")
                isUploadingRoomMedia = false
                if (uploaded != null) {
                    currentRoom = currentRoom.copy(coverUrl = uploaded)
                    partyService.updateRoomAppearance(currentRoom.id, coverUrl = uploaded)
                    AppToast.show("Room avatar updated! 🎉")
                } else {
                    AppToast.show("Could not upload room avatar")
                }
            }
        }
    }

    val roomBgPickerLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.PickVisualMedia()
    ) { uri: Uri? ->
        if (uri != null) {
            isUploadingRoomMedia = true
            scope.launch {
                val uploaded = partyService.uploadPartyImage(context, uri, "backgrounds")
                isUploadingRoomMedia = false
                if (uploaded != null) {
                    currentRoom = currentRoom.copy(bgUrl = uploaded)
                    partyService.updateRoomAppearance(currentRoom.id, bgUrl = uploaded)
                    AppToast.show("Room background wallpaper updated! 🎨")
                } else {
                    AppToast.show("Could not upload room background")
                }
            }
        }
    }

    // Handle Back Press: Show stay in room vs leave options
    BackHandler {
        showLeaveConfirmDialog = true
    }

    // Sync global session state (only if not actively leaving)
    LaunchedEffect(currentRoom, isSeated, mySeatIndex, isMicMuted, isLeavingRoom) {
        if (!isLeavingRoom) {
            PartyRoomSessionManager.enterRoom(currentRoom)
            PartyRoomSessionManager.setSeated(isSeated, mySeatIndex)
            PartyRoomSessionManager.setMicMuted(isMicMuted)
        }
    }

    fun sendPartyChatMessage() {
        val text = messageInputText.trim()
        if (text.isBlank()) return
        messageInputText = ""
        scope.launch {
            val newMsg = PartyRoomMessage(
                roomId = currentRoom.id,
                senderId = currentUserId,
                senderName = currentUserName.ifBlank { "User" },
                senderAvatarUrl = currentUserAvatar,
                content = text,
                msgType = "chat"
            )
            partyService.sendRoomMessage(currentRoom.id, newMsg)
            messages = partyService.fetchRoomMessages(currentRoom.id)
            if (messages.isNotEmpty()) {
                listState.animateScrollToItem(messages.size - 1)
            }
        }
    }

    // Audio Permission handling
    var hasAudioPermission by remember {
        mutableStateOf(
            ContextCompat.checkSelfPermission(context, Manifest.permission.RECORD_AUDIO) == PackageManager.PERMISSION_GRANTED
        )
    }

    fun takeMicSeatInternal(seatIndex: Int) {
        val oldSeatIdx = mySeatIndex
        val mutableSeats = seats.mapIndexed { idx, s ->
            if (idx == seatIndex) {
                PartySeat(
                    seatIndex = seatIndex,
                    userId = currentUserId,
                    userName = currentUserName.ifBlank { "User" },
                    avatarUrl = currentUserAvatar
                )
            } else if (s.userId == currentUserId) {
                PartySeat(seatIndex = idx)
            } else {
                s
            }
        }
        seats = mutableSeats
        PartyRoomSessionManager.setSeated(true, seatIndex)
        voiceEngine.takeMicSeat(scope, currentUserId, seatIndex)

        // Instant Real-time Broadcast
        PartyRealtimeRelayManager.broadcastSeatSwitch(
            roomId = currentRoom.id,
            userId = currentUserId,
            fromSeat = oldSeatIdx,
            toSeat = seatIndex,
            userName = currentUserName.ifBlank { "User" },
            avatarUrl = currentUserAvatar
        )

        scope.launch {
            val ok = partyService.occupySeat(
                roomId = currentRoom.id,
                seatIndex = seatIndex,
                userId = currentUserId,
                userName = currentUserName.ifBlank { "User" },
                avatarUrl = currentUserAvatar
            )
            if (ok) {
                seats = partyService.fetchRoomSeats(currentRoom.id, currentRoom.seatsCount)
                messages = partyService.fetchRoomMessages(currentRoom.id)
                // Stop toasting when user seats
            }
        }
    }

    val audioPermissionLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestPermission()
    ) { granted ->
        hasAudioPermission = granted
        if (granted) {
            val targetIdx = pendingSeatIndexToTake ?: seats.indexOfFirst { it.userId.isBlank() }.takeIf { it != -1 } ?: 0
            takeMicSeatInternal(targetIdx)
            pendingSeatIndexToTake = null
        } else {
            AppToast.show("Microphone permission is required to talk on mic")
        }
    }

    fun requestSitDown(seatIndex: Int? = null) {
        val targetIdx = seatIndex ?: seats.indexOfFirst { it.userId.isBlank() }
        if (targetIdx == -1) {
            AppToast.show("All seats are currently full")
            return
        }
        if (!hasAudioPermission) {
            pendingSeatIndexToTake = targetIdx
            audioPermissionLauncher.launch(Manifest.permission.RECORD_AUDIO)
        } else {
            takeMicSeatInternal(targetIdx)
        }
    }

    // Connect to Tencent TRTC & Realtime Relay Room on launch
    LaunchedEffect(currentRoom.id, currentRoom.roomNumber) {
        PartyRealtimeRelayManager.connectRoom(currentRoom.id, currentUserId)
        PartyMusicManager.setRoomId(currentRoom.id)
        voiceEngine.enterPartyRoom(scope, currentRoom.id, currentUserId, mySeatIndex)
    }

    // Realtime Room Closed / Deleted Listener
    LaunchedEffect(currentRoom.id) {
        PartyRealtimeRelayManager.roomClosedEvents.collect { closedEvent ->
            if (closedEvent.roomId == currentRoom.id && !isDeletingRoom) {
                AppToast.show("This party room was closed by the host")
                PartyRoomSessionManager.leaveRoom(currentUserId, scope)
                onLeaveRoom()
            }
        }
    }

    // Realtime User Leave Listener (instantly removes from seat and presence)
    LaunchedEffect(currentRoom.id) {
        PartyRealtimeRelayManager.userLeaveEvents.collect { leaveEvent ->
            if (leaveEvent.roomId == currentRoom.id && leaveEvent.userId.isNotBlank()) {
                seats = seats.mapIndexed { idx, s ->
                    if (s.userId == leaveEvent.userId) PartySeat(seatIndex = idx) else s
                }
                categorizedMembers = categorizedMembers.filter { it.userId != leaveEvent.userId }
            }
        }
    }

    // Realtime Speaking Waves Listener
    LaunchedEffect(currentRoom.id) {
        PartyRealtimeRelayManager.speakingEvents.collect { ev ->
            if (ev.roomId == currentRoom.id && ev.seatIndex in seats.indices) {
                val updated = seats.mapIndexed { idx, s ->
                    if (idx == ev.seatIndex || (s.userId == ev.userId && s.userId.isNotBlank())) {
                        s.copy(isSpeaking = ev.isSpeaking)
                    } else {
                        s
                    }
                }
                seats = updated
                if (ev.userId == currentUserId) {
                    currentSpeakingAmplitude = ev.volume
                }
            }
        }
    }

    // Realtime Instant Seat Switch Listener
    LaunchedEffect(currentRoom.id) {
        PartyRealtimeRelayManager.seatSwitchEvents.collect { ev ->
            if (ev.roomId == currentRoom.id) {
                val updated = seats.mapIndexed { idx, s ->
                    if (idx == ev.toSeat) {
                        PartySeat(
                            seatIndex = ev.toSeat,
                            userId = ev.userId,
                            userName = ev.userName,
                            avatarUrl = ev.avatarUrl
                        )
                    } else if (idx == ev.fromSeat || (s.userId == ev.userId && s.userId.isNotBlank())) {
                        PartySeat(seatIndex = idx)
                    } else {
                        s
                    }
                }
                seats = updated
                if (ev.userId == currentUserId) {
                    if (ev.toSeat != -1) {
                        PartyRoomSessionManager.setSeated(true, ev.toSeat)
                    } else {
                        PartyRoomSessionManager.setSeated(false, -1)
                    }
                }
            }
        }
    }

    // Realtime Animated Emoji Reactions Listener (Seen by all members)
    LaunchedEffect(currentRoom.id) {
        PartyRealtimeRelayManager.reactionEvents.collect { ev ->
            if (ev.roomId == currentRoom.id && ev.seatIndex in seats.indices) {
                val rxType = try {
                    SeatReactionType.valueOf(ev.reactionType)
                } catch (_: Exception) {
                    SeatReactionType.LOVE
                }
                activeSeatReactions = activeSeatReactions + (ev.seatIndex to (rxType to ev.timestamp))
                scope.launch {
                    delay(3500)
                    if (activeSeatReactions[ev.seatIndex]?.second == ev.timestamp) {
                        activeSeatReactions = activeSeatReactions - ev.seatIndex
                    }
                }
            }
        }
    }

    // Real-time polling loop for room data, seats, admins, and members presence
    LaunchedEffect(currentRoom.id) {
        partyService.trackUserPresence(
            roomId = currentRoom.id,
            userId = currentUserId,
            userName = currentUserName,
            avatarUrl = currentUserAvatar,
            role = if (isRoomOwner) "owner" else if (isRoomAdmin) "admin" else "audience"
        )

        while (isActive) {
            val updatedSeats = partyService.fetchRoomSeats(currentRoom.id, currentRoom.seatsCount)
            seats = updatedSeats
            val updatedMsgs = partyService.fetchRoomMessages(currentRoom.id)
            messages = updatedMsgs
            roomAdmins = partyService.fetchRoomAdmins(currentRoom.id)
            categorizedMembers = partyService.fetchCategorizedRoomMembers(
                room = currentRoom,
                currentUserId = currentUserId,
                currentUserName = currentUserName,
                currentUserAvatar = currentUserAvatar,
                currentSeats = updatedSeats
            )
            delay(1200)
        }
    }

    // Set audio amplitude callback from Tencent Voice Engine
    LaunchedEffect(Unit) {
        voiceEngine.onSpeakingVolumeChanged = { uid, vol ->
            if (uid == currentUserId) {
                currentSpeakingAmplitude = vol
                partyService.updateSeatSpeaking(currentRoom.id, currentUserId, vol > 0.05f)
            }
        }
    }

    // Start / stop mic when seated using Tencent Voice Engine. When muted, mic capture is fully disabled.
    LaunchedEffect(isSeated, isMicMuted, hasAudioPermission, mySeatIndex) {
        if (isSeated && !isMicMuted && hasAudioPermission) {
            voiceEngine.takeMicSeat(scope, currentUserId, mySeatIndex)
            voiceEngine.setMicMute(currentUserId, false, scope)
        } else if (isSeated && isMicMuted) {
            voiceEngine.setMicMute(currentUserId, true, null)
            currentSpeakingAmplitude = 0f
        } else if (!isSeated) {
            voiceEngine.leaveMicSeat(currentUserId)
            currentSpeakingAmplitude = 0f
        }
    }

    // Auto scroll chat to bottom on new message
    LaunchedEffect(messages.size) {
        if (messages.isNotEmpty()) {
            listState.animateScrollToItem(messages.size - 1)
        }
    }

    // Clean up seat and presence when leaving/exiting room (unless minimized)
    DisposableEffect(currentRoom.id) {
        onDispose {
            if (!PartyRoomSessionManager.isMinimized.value) {
                CoroutineScope(Dispatchers.IO).launch {
                    try {
                        val ps = SupabasePartyService()
                        ps.releaseSeat(currentRoom.id, currentUserId)
                        ps.removeUserPresence(currentRoom.id, currentUserId)
                    } catch (_: Exception) {}
                }
                voiceEngine.leaveMicSeat(currentUserId)
                voiceEngine.exitPartyRoom(currentUserId)
                PartyMusicManager.stopMusic()
            }
        }
    }

    // Intercept back presses to show party room leave / minimize dialog
    BackHandler {
        showLeaveConfirmDialog = true
    }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(Color(0xFF0F0E17))
            .testTag("party_room_screen_root")
    ) {
        // 1. Dynamic Wallpaper Background with modern frosted depth blur
        if (currentRoom.bgUrl.isNotBlank()) {
            AsyncImage(
                model = currentRoom.bgUrl,
                contentDescription = "Room Background",
                modifier = Modifier
                    .fillMaxSize()
                    .blur(16.dp),
                contentScale = ContentScale.Crop
            )
        } else {
            // Elegant party ambient background with soft ambient blur
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .blur(18.dp)
                    .background(
                        Brush.verticalGradient(
                            listOf(
                                Color(0xFF2C134B),
                                Color(0xFF1B1133),
                                Color(0xFF0E0B1A)
                            )
                        )
                    )
            )
        }

        // Atmospheric party room tint overlay for balanced contrast, vibrant ambiance and legible typography
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(
                    Brush.verticalGradient(
                        listOf(
                            Color(0x551E0E38),
                            Color(0x33000000),
                            Color(0x770D0B18),
                            Color(0xAA08070D)
                        )
                    )
                )
        )

        Column(
            modifier = Modifier
                .fillMaxSize()
                .statusBarsPadding()
                .navigationBarsPadding()
                .padding(horizontal = 16.dp, vertical = 8.dp)
        ) {
            // 2. Room Top Header Bar with Room Info, Admin Shortcut, Members count and Exit
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(vertical = 6.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                // Room Info Chip (Clickable for Room Owner to edit room avatar & background)
                Row(
                    modifier = Modifier
                        .weight(1f, fill = false)
                        .clip(RoundedCornerShape(24.dp))
                        .background(Color(0x88000000))
                        .clickable(enabled = isRoomOwner) {
                            showRoomSettingsDialog = true
                        }
                        .padding(start = 4.dp, end = 12.dp, top = 4.dp, bottom = 4.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    val cover = currentRoom.coverUrl.ifBlank { currentRoom.hostAvatarUrl }
                    val mascotRes = AvatarHelper.getDrawableForMascotKey(cover)
                    if (mascotRes != null) {
                        androidx.compose.foundation.Image(
                            painter = androidx.compose.ui.res.painterResource(id = mascotRes),
                            contentDescription = "Room Avatar",
                            modifier = Modifier
                                .size(36.dp)
                                .clip(CircleShape)
                                .border(1.5.dp, QivoOrange, CircleShape),
                            contentScale = ContentScale.Crop
                        )
                    } else if (cover.isNotBlank() && (cover.startsWith("http") || cover.startsWith("data:"))) {
                        AsyncImage(
                            model = cover,
                            contentDescription = "Room Avatar",
                            modifier = Modifier
                                .size(36.dp)
                                .clip(CircleShape)
                                .border(1.5.dp, QivoOrange, CircleShape),
                            contentScale = ContentScale.Crop
                        )
                    } else {
                        val defRes = AvatarHelper.getDefaultMascotRes(userId = currentRoom.id, gender = "Male")
                        androidx.compose.foundation.Image(
                            painter = androidx.compose.ui.res.painterResource(id = defRes),
                            contentDescription = "Room Avatar",
                            modifier = Modifier
                                .size(36.dp)
                                .clip(CircleShape)
                                .border(1.5.dp, QivoOrange, CircleShape),
                            contentScale = ContentScale.Crop
                        )
                    }

                    Spacer(modifier = Modifier.width(8.dp))
                    Column(modifier = Modifier.widthIn(max = 140.dp)) {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .horizontalScroll(rememberScrollState()),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text(
                                text = currentRoom.name,
                                fontSize = 13.sp,
                                fontWeight = FontWeight.Bold,
                                color = Color.White,
                                maxLines = 1,
                                softWrap = false
                            )
                            if (isRoomOwner) {
                                Spacer(modifier = Modifier.width(4.dp))
                                Icon(
                                    imageVector = Icons.Default.Edit,
                                    contentDescription = "Edit Appearance",
                                    tint = QivoYellow,
                                    modifier = Modifier.size(12.dp)
                                )
                            }
                        }
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Text(
                                text = "ID: ${currentRoom.roomNumber}",
                                fontSize = 10.sp,
                                color = Color.White.copy(alpha = 0.85f)
                            )
                            Spacer(modifier = Modifier.width(6.dp))
                            Box(
                                modifier = Modifier
                                    .clip(RoundedCornerShape(4.dp))
                                    .background(QivoOrange)
                                    .padding(horizontal = 4.dp, vertical = 1.dp)
                            ) {
                                Text(currentRoom.category, fontSize = 9.sp, fontWeight = FontWeight.Bold, color = Color.White)
                            }
                        }
                    }
                }

                Spacer(modifier = Modifier.width(8.dp))

                // Right controls: Leave Room, Members count & Minimize / Exit button
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    // Dedicated Separate "Leave Room" Button
                    Surface(
                        onClick = {
                            isLeavingRoom = true
                            PartyMusicManager.stopMusic()
                            PartyRoomSessionManager.leaveRoom(currentUserId, scope)
                            onLeaveRoom()
                            AppToast.show("You left ${currentRoom.name}")
                        },
                        shape = RoundedCornerShape(16.dp),
                        color = Color(0xAAFF1744),
                        border = androidx.compose.foundation.BorderStroke(1.dp, Color(0xFFFF5252)),
                        modifier = Modifier.testTag("direct_leave_room_button")
                    ) {
                        Row(
                            modifier = Modifier.padding(horizontal = 8.dp, vertical = 5.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Icon(
                                Icons.AutoMirrored.Filled.ExitToApp,
                                contentDescription = "Leave Room",
                                tint = Color.White,
                                modifier = Modifier.size(15.dp)
                            )
                            Spacer(modifier = Modifier.width(3.dp))
                            Text(
                                text = "Leave",
                                fontSize = 11.sp,
                                fontWeight = FontWeight.Bold,
                                color = Color.White
                            )
                        }
                    }

                    // Members pill button (Opens categorized attendees side panel)
                    Surface(
                        onClick = { showMembersPanel = true },
                        shape = CircleShape,
                        color = Color(0x88000000),
                        border = androidx.compose.foundation.BorderStroke(1.dp, Color(0x44FFFFFF)),
                        modifier = Modifier.testTag("open_members_panel_button")
                    ) {
                        Row(
                            modifier = Modifier.padding(horizontal = 8.dp, vertical = 5.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Icon(
                                Icons.Default.Group,
                                contentDescription = "Room Attendees",
                                tint = QivoYellow,
                                modifier = Modifier.size(15.dp)
                            )
                            Spacer(modifier = Modifier.width(3.dp))
                            Text(
                                text = "${categorizedMembers.size}",
                                fontSize = 11.sp,
                                fontWeight = FontWeight.Bold,
                                color = Color.White
                            )
                        }
                    }

                    // Minimize / Options Dialog Button
                    IconButton(
                        onClick = {
                            showLeaveConfirmDialog = true
                        },
                        modifier = Modifier
                            .size(34.dp)
                            .clip(CircleShape)
                            .background(Color(0x88000000))
                            .testTag("close_room_button")
                    ) {
                        Icon(Icons.Default.Close, contentDescription = "Exit / Minimize Room", tint = Color.White, modifier = Modifier.size(18.dp))
                    }
                }
            }

            // Live Music Playing Ticker (Mini DJ Player Deck)
            if (musicTrackInfo.title.isNotBlank() && isMusicPlaying) {
                Surface(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(vertical = 4.dp)
                        .clickable {
                            if (isRoomOwner || isRoomAdmin) showMusicBottomSheet = true
                        },
                    shape = RoundedCornerShape(14.dp),
                    color = Color(0xEE1E1838),
                    border = androidx.compose.foundation.BorderStroke(
                        1.dp,
                        Brush.horizontalGradient(listOf(Color(0xFFBA68C8), Color(0xFFFF9100)))
                    )
                ) {
                    Row(
                        modifier = Modifier.padding(horizontal = 12.dp, vertical = 6.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Box(
                            modifier = Modifier
                                .size(28.dp)
                                .clip(CircleShape)
                                .background(Brush.linearGradient(listOf(Color(0xFFFF9100), Color(0xFFFF3D00)))),
                            contentAlignment = Alignment.Center
                        ) {
                            Icon(
                                Icons.Default.MusicNote,
                                contentDescription = null,
                                tint = Color.White,
                                modifier = Modifier.size(16.dp)
                            )
                        }
                        Spacer(modifier = Modifier.width(8.dp))
                        AnimatedMusicEqualizer(isPlaying = true, modifier = Modifier.height(18.dp))
                        Spacer(modifier = Modifier.width(8.dp))
                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                text = musicTrackInfo.title,
                                fontSize = 11.sp,
                                fontWeight = FontWeight.ExtraBold,
                                color = Color.White,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis
                            )
                            Text(
                                text = "DJ Broadcast • Live In Room",
                                fontSize = 9.sp,
                                color = QivoYellow
                            )
                        }
                        if (isRoomOwner || isRoomAdmin) {
                            Surface(
                                shape = RoundedCornerShape(10.dp),
                                color = Color(0x33FFFFFF),
                                modifier = Modifier.padding(start = 6.dp)
                            ) {
                                Text(
                                    text = "DJ Deck 🎛️",
                                    fontSize = 10.sp,
                                    color = Color.White,
                                    fontWeight = FontWeight.Bold,
                                    modifier = Modifier.padding(horizontal = 7.dp, vertical = 3.dp)
                                )
                            }
                        }
                    }
                }
            }

            // Room Description Banner
            if (currentRoom.description.isNotBlank()) {
                Surface(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(vertical = 3.dp),
                    shape = RoundedCornerShape(10.dp),
                    color = Color(0x55000000)
                ) {
                    Row(
                        modifier = Modifier.padding(horizontal = 10.dp, vertical = 4.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Icon(Icons.Default.Campaign, contentDescription = null, tint = QivoYellow, modifier = Modifier.size(16.dp))
                        Spacer(modifier = Modifier.width(6.dp))
                        Text(
                            text = currentRoom.description,
                            fontSize = 11.sp,
                            color = Color.White.copy(alpha = 0.9f),
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                    }
                }
            }

            // Room Owner / Admin Controls Bar (Spacious & Scrollable - Never Squeezed)
            if (isRoomOwner || isRoomAdmin) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(vertical = 4.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = if (isRoomOwner) "👑 Host Tools" else "🛡️ Admin Tools",
                        fontSize = 11.sp,
                        fontWeight = FontWeight.Bold,
                        color = QivoYellow
                    )
                    Row(
                        horizontalArrangement = Arrangement.spacedBy(6.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        modifier = Modifier.horizontalScroll(rememberScrollState())
                    ) {
                        // Phone Music Player Hub Button (Host & Admins)
                        Surface(
                            onClick = {
                                if (!isSeated) {
                                    AppToast.show("You must take a seat to play music 🎙️")
                                }
                                showMusicBottomSheet = true
                            },
                            shape = RoundedCornerShape(12.dp),
                            color = Color(0x88AB47BC),
                            border = androidx.compose.foundation.BorderStroke(1.dp, Color(0xFFCE93D8)),
                            modifier = Modifier.testTag("party_music_hub_button")
                        ) {
                            Row(
                                modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Icon(Icons.Default.MusicNote, contentDescription = null, tint = Color.White, modifier = Modifier.size(13.dp))
                                Spacer(modifier = Modifier.width(4.dp))
                                Text(
                                    text = if (isMusicPlaying) "Music 🎵" else "DJ Music",
                                    fontSize = 10.sp,
                                    fontWeight = FontWeight.Bold,
                                    color = Color.White
                                )
                            }
                        }

                        // Appoint / Manage Admins button (Room Owner only, max 5)
                        if (isRoomOwner) {
                            Surface(
                                onClick = { showAdminManageDialog = true },
                                shape = RoundedCornerShape(12.dp),
                                color = Color(0x881E88E5),
                                border = androidx.compose.foundation.BorderStroke(1.dp, Color(0xFF64B5F6))
                            ) {
                                Row(
                                    modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp),
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Icon(Icons.Default.Security, contentDescription = null, tint = Color.White, modifier = Modifier.size(13.dp))
                                    Spacer(modifier = Modifier.width(4.dp))
                                    Text(
                                        text = "Admins (${roomAdmins.size}/5)",
                                        fontSize = 10.sp,
                                        fontWeight = FontWeight.Bold,
                                        color = Color.White
                                    )
                                }
                            }
                        }

                        // Seat Capacity Button (Host & Admins)
                        Surface(
                            onClick = { showSeatCapacityDialog = true },
                            shape = RoundedCornerShape(12.dp),
                            color = Color(0x88FF9800),
                            border = androidx.compose.foundation.BorderStroke(1.dp, QivoOrange)
                        ) {
                            Row(
                                modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Icon(Icons.Default.Tune, contentDescription = null, tint = Color.White, modifier = Modifier.size(13.dp))
                                Spacer(modifier = Modifier.width(4.dp))
                                Text(
                                    text = "Seats (${seats.size})",
                                    fontSize = 10.sp,
                                    fontWeight = FontWeight.Bold,
                                    color = Color.White
                                )
                            }
                        }

                        // Host Delete Room button (Opens Swipe-to-Delete Interface)
                        if (isRoomOwner) {
                            Surface(
                                onClick = { showDeleteConfirmDialog = true },
                                shape = RoundedCornerShape(12.dp),
                                color = Color(0x88D32F2F),
                                border = androidx.compose.foundation.BorderStroke(1.dp, Color(0xFFFF5252))
                            ) {
                                Row(
                                    modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp),
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Icon(Icons.Default.Delete, contentDescription = "Delete Room", tint = Color.White, modifier = Modifier.size(13.dp))
                                    Spacer(modifier = Modifier.width(4.dp))
                                    Text(
                                        text = "Delete Room",
                                        fontSize = 10.sp,
                                        fontWeight = FontWeight.Bold,
                                        color = Color.White
                                    )
                                }
                            }
                        }
                    }
                }
            }

            Spacer(modifier = Modifier.height(8.dp))

            // 3. Arranged Mic Seats Grid (Rows of 4 going downwards: 4, 8, 10, 12, 16)
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(vertical = 4.dp),
                verticalArrangement = Arrangement.spacedBy(14.dp)
            ) {
                val seatRows = seats.chunked(4)
                seatRows.forEach { rowSeats ->
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceAround
                    ) {
                        rowSeats.forEach { seat ->
                            PartySeatItem(
                                seat = seat,
                                isMyUser = seat.userId == currentUserId && currentUserId.isNotBlank(),
                                isSpeaking = if (seat.userId == currentUserId) currentSpeakingAmplitude > 0.05f else seat.isSpeaking,
                                activeReaction = activeSeatReactions[seat.seatIndex]?.first,
                                onClick = {
                                    if (seat.userId.isBlank()) {
                                        // Request to sit down on this specific seat
                                        requestSitDown(seat.seatIndex)
                                    } else {
                                        selectedSeatForAction = seat
                                    }
                                }
                            )
                        }
                        // Balance row if less than 4
                        if (rowSeats.size < 4) {
                            repeat(4 - rowSeats.size) {
                                Spacer(modifier = Modifier.size(54.dp))
                            }
                        }
                    }
                }
            }

            // 4. Active Gift Broadcast Animated Banner
            AnimatedVisibility(
                visible = activeGiftBanner != null,
                enter = fadeIn() + scaleIn(),
                exit = fadeOut() + scaleOut()
            ) {
                Surface(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(vertical = 4.dp),
                    shape = RoundedCornerShape(16.dp),
                    color = Color(0xEE2A123D),
                    border = androidx.compose.foundation.BorderStroke(
                        1.5.dp,
                        Brush.horizontalGradient(
                            listOf(Color(0xFF8CFF1A), Color(0xFF7FFF00), Color(0xFF5AB800))
                        )
                    ),
                    shadowElevation = 6.dp
                ) {
                    Row(
                        modifier = Modifier.padding(horizontal = 14.dp, vertical = 6.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        val bannerGift = activeGiftBannerGift ?: resolveGift(activeGiftBanner)
                        Gift3DIcon(
                            giftId = bannerGift?.id ?: "gift_box",
                            emoji = bannerGift?.emoji ?: "🎁",
                            size = 32.dp
                        )
                        Spacer(modifier = Modifier.width(10.dp))
                        Text(
                            text = activeGiftBanner ?: "",
                            fontSize = 13.sp,
                            fontWeight = FontWeight.Bold,
                            color = Color.White,
                            modifier = Modifier.weight(1f)
                        )
                    }
                }
            }

            Spacer(modifier = Modifier.weight(1f))

            // 5. Live Room Messages Feed (Pure user chat messages only)
            val filteredMessages = remember(messages) {
                messages.filter { msg ->
                    msg.msgType == "chat" &&
                    !msg.content.contains("sent gift", ignoreCase = true) &&
                    !msg.content.contains("took Mic", ignoreCase = true) &&
                    !msg.content.contains("stepped down", ignoreCase = true) &&
                    !msg.content.contains("sat on seat", ignoreCase = true) &&
                    !msg.content.contains("sent ", ignoreCase = true)
                }
            }
            LazyColumn(
                state = listState,
                modifier = Modifier
                    .fillMaxWidth()
                    .heightIn(max = 160.dp)
                    .padding(bottom = 8.dp),
                verticalArrangement = Arrangement.spacedBy(4.dp)
            ) {
                items(filteredMessages, key = { it.id }) { msg ->
                    PartyMessageBubble(msg)
                }
            }

            // 6. Bottom Action Bar: Seat / Mic Controls & Gift Button
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(vertical = 8.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                // Left: Seat / Mic Controls with Custom 3D Icons
                if (isSeated) {
                    Row(
                        horizontalArrangement = Arrangement.spacedBy(10.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        // Mic Mute / Unmute Button with Custom 3D Mic Icon
                        Button(
                            onClick = {
                                isMicMuted = !isMicMuted
                                partyService.toggleSeatMute(currentRoom.id, currentUserId, isMicMuted)
                                voiceEngine.setMicMute(currentUserId, isMicMuted, scope)
                                PartyRoomSessionManager.setMicMuted(isMicMuted)
                            },
                            colors = ButtonDefaults.buttonColors(
                                containerColor = if (isMicMuted) Color(0xFFFF5252) else QivoOrange
                            ),
                            shape = RoundedCornerShape(24.dp),
                            contentPadding = PaddingValues(horizontal = 14.dp, vertical = 8.dp),
                            modifier = Modifier.testTag("toggle_mic_button")
                        ) {
                            if (isMicMuted) {
                                MicMuted3DIcon(size = 20.dp)
                            } else {
                                Mic3DIcon(size = 20.dp)
                            }
                            Spacer(modifier = Modifier.width(8.dp))
                            Text(
                                text = if (isMicMuted) "Mic Muted" else "Mic Active",
                                fontSize = 13.sp,
                                fontWeight = FontWeight.Bold,
                                color = Color.White
                            )
                        }

                        // Live Animated Reactions Button (for Seated User)
                        IconButton(
                            onClick = { showReactionDialog = true },
                            modifier = Modifier
                                .size(44.dp)
                                .clip(CircleShape)
                                .background(Brush.linearGradient(listOf(Color(0xFFFF9100), Color(0xFFFF3D00))))
                                .border(1.dp, Color(0x44FFFFFF), CircleShape)
                                .testTag("seat_reaction_button")
                        ) {
                            Text("🎭", fontSize = 20.sp)
                        }

                        // Step Down / Leave Mic button
                        IconButton(
                            onClick = {
                                seats = seats.map { if (it.userId == currentUserId) PartySeat(seatIndex = it.seatIndex) else it }
                                PartyRoomSessionManager.setSeated(false, -1)
                                voiceEngine.leaveMicSeat(currentUserId)
                                scope.launch {
                                    partyService.releaseSeat(currentRoom.id, currentUserId)
                                    seats = partyService.fetchRoomSeats(currentRoom.id, currentRoom.seatsCount)
                                    messages = partyService.fetchRoomMessages(currentRoom.id)
                                    // Stop toasting when user leaves seat
                                }
                            },
                            modifier = Modifier
                                .size(44.dp)
                                .clip(CircleShape)
                                .background(Color(0x66000000))
                                .border(1.dp, Color(0x44FFFFFF), CircleShape)
                                .testTag("leave_mic_seat_button")
                        ) {
                            Icon(
                                imageVector = Icons.AutoMirrored.Filled.ExitToApp,
                                contentDescription = "Step Down",
                                tint = Color.White,
                                modifier = Modifier.size(20.dp)
                            )
                        }
                    }
                } else {
                    // Before user sits: Display Custom 3D Seat Icon and button
                    Button(
                        onClick = {
                            requestSitDown()
                        },
                        colors = ButtonDefaults.buttonColors(
                            containerColor = Color(0x992B1C4B)
                        ),
                        border = androidx.compose.foundation.BorderStroke(1.5.dp, QivoOrange),
                        shape = RoundedCornerShape(24.dp),
                        contentPadding = PaddingValues(horizontal = 16.dp, vertical = 8.dp),
                        modifier = Modifier.testTag("take_seat_bottom_button")
                    ) {
                        Seat3DIcon(size = 22.dp)
                        Spacer(modifier = Modifier.width(8.dp))
                        Text(
                            text = "Take a Seat 🎙️",
                            fontSize = 13.sp,
                            fontWeight = FontWeight.Bold,
                            color = Color.White
                        )
                    }
                }

                // Right: Gift Box Button
                Button(
                    onClick = { showGiftDialog = true },
                    colors = ButtonDefaults.buttonColors(
                        containerColor = Color.Transparent
                    ),
                    shape = RoundedCornerShape(24.dp),
                    contentPadding = PaddingValues(0.dp),
                    modifier = Modifier
                        .height(44.dp)
                        .clip(RoundedCornerShape(24.dp))
                        .background(Brush.horizontalGradient(listOf(Color(0xFFFFB300), Color(0xFFFF3D00))))
                        .testTag("gift_button")
                ) {
                    Row(
                        modifier = Modifier.padding(horizontal = 18.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Icon(
                            imageVector = Icons.Default.CardGiftcard,
                            contentDescription = "Send Gift",
                            tint = Color.White,
                            modifier = Modifier.size(20.dp)
                        )
                        Spacer(modifier = Modifier.width(6.dp))
                        Text("Send Gift 🎁", fontSize = 13.sp, fontWeight = FontWeight.Bold, color = Color.White)
                    }
                }
            }
        }

        // ==========================================
        // 7. ROOM ATTENDEES & ADMINS SIDE PANEL
        // ==========================================
        if (showMembersPanel) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .background(Color(0x88000000))
                    .clickable { showMembersPanel = false }
            )

            Box(
                modifier = Modifier
                    .fillMaxHeight()
                    .width(320.dp)
                    .align(Alignment.CenterEnd)
                    .background(Color(0xFF161424))
                    .statusBarsPadding()
                    .navigationBarsPadding()
            ) {
                Column(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(16.dp)
                ) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Column {
                            Text(
                                text = "Room Members",
                                fontSize = 18.sp,
                                fontWeight = FontWeight.Bold,
                                color = Color.White
                            )
                            Text(
                                text = "${categorizedMembers.size} Present Online",
                                fontSize = 12.sp,
                                color = Color.White.copy(alpha = 0.6f)
                            )
                        }
                        IconButton(onClick = { showMembersPanel = false }) {
                            Icon(Icons.Default.Close, contentDescription = "Close", tint = Color.White)
                        }
                    }

                    Spacer(modifier = Modifier.height(12.dp))

                    // Room Owner / Admin Seat Capacity Shortcut
                    if (isRoomOwner || isRoomAdmin) {
                        Surface(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(bottom = 12.dp),
                            shape = RoundedCornerShape(14.dp),
                            color = Color(0x33FF9800),
                            border = androidx.compose.foundation.BorderStroke(1.dp, QivoOrange.copy(alpha = 0.5f))
                        ) {
                            Column(modifier = Modifier.padding(12.dp)) {
                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.SpaceBetween,
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Text("🎙️ Room Seat Capacity", fontSize = 12.sp, fontWeight = FontWeight.Bold, color = QivoYellow)
                                    Text("${seats.size} Seats", fontSize = 12.sp, fontWeight = FontWeight.ExtraBold, color = Color.White)
                                }
                                Spacer(modifier = Modifier.height(8.dp))
                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                                ) {
                                    listOf(4, 8, 10, 12, 16).forEach { count ->
                                        val isCurrent = seats.size == count
                                        Surface(
                                            modifier = Modifier
                                                .weight(1f)
                                                .clip(RoundedCornerShape(8.dp))
                                                .clickable {
                                                    scope.launch {
                                                        partyService.updateRoomSeatCount(currentRoom.id, count)
                                                        seats = partyService.fetchRoomSeats(currentRoom.id, count)
                                                        currentRoom = currentRoom.copy(seatsCount = count)
                                                        AppToast.show("Seats adjusted to $count")
                                                    }
                                                },
                                            shape = RoundedCornerShape(8.dp),
                                            color = if (isCurrent) QivoOrange else Color(0x44FFFFFF)
                                        ) {
                                            Text(
                                                text = "$count",
                                                fontSize = 11.sp,
                                                fontWeight = FontWeight.Bold,
                                                color = Color.White,
                                                textAlign = TextAlign.Center,
                                                modifier = Modifier.padding(vertical = 6.dp)
                                            )
                                        }
                                    }
                                }
                            }
                        }
                    }

                    // Appoint Party Admin Shortcut for Room Owner
                    if (isRoomOwner) {
                        Surface(
                            onClick = { showAdminManageDialog = true },
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(bottom = 12.dp),
                            shape = RoundedCornerShape(14.dp),
                            color = Color(0x331E88E5),
                            border = androidx.compose.foundation.BorderStroke(1.dp, Color(0xFF64B5F6).copy(alpha = 0.5f))
                        ) {
                            Row(
                                modifier = Modifier.padding(horizontal = 12.dp, vertical = 10.dp),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Row(verticalAlignment = Alignment.CenterVertically) {
                                    Icon(Icons.Default.Security, contentDescription = null, tint = Color(0xFF64B5F6), modifier = Modifier.size(18.dp))
                                    Spacer(modifier = Modifier.width(8.dp))
                                    Text("Manage Party Admins", fontSize = 12.sp, fontWeight = FontWeight.Bold, color = Color.White)
                                }
                                Text("${roomAdmins.size}/5", fontSize = 12.sp, fontWeight = FontWeight.ExtraBold, color = Color(0xFF64B5F6))
                            }
                        }
                    }

                    // Categorized Members List
                    LazyColumn(
                        modifier = Modifier.fillMaxSize(),
                        verticalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        // Section 1: Room Owner (Host)
                        val owners = categorizedMembers.filter { it.role == "owner" || it.userId == currentRoom.hostUserId }
                        if (owners.isNotEmpty()) {
                            item {
                                Text("👑 ROOM OWNER", fontSize = 11.sp, fontWeight = FontWeight.Bold, color = QivoYellow, modifier = Modifier.padding(vertical = 4.dp))
                            }
                            items(owners, key = { "owner_${it.userId}" }) { member ->
                                MemberItemRow(
                                    member = member,
                                    isHost = true,
                                    isAdmin = false,
                                    canManage = isRoomOwner && member.userId != currentUserId,
                                    onClick = { selectedMemberForAction = member }
                                )
                            }
                        }

                        // Section 2: Party Admins (Appointed by Owner, max 5)
                        val admins = categorizedMembers.filter { it.role == "admin" || (roomAdmins.contains(it.userId) && it.userId != currentRoom.hostUserId) }
                        if (admins.isNotEmpty()) {
                            item {
                                Text("🛡️ PARTY ROOM ADMINS (${admins.size}/5)", fontSize = 11.sp, fontWeight = FontWeight.Bold, color = Color(0xFF64B5F6), modifier = Modifier.padding(top = 10.dp, bottom = 4.dp))
                            }
                            items(admins, key = { "admin_${it.userId}" }) { member ->
                                MemberItemRow(
                                    member = member,
                                    isHost = false,
                                    isAdmin = true,
                                    canManage = isRoomOwner && member.userId != currentUserId,
                                    onClick = { selectedMemberForAction = member }
                                )
                            }
                        }

                        // Section 3: On-Mic Speakers
                        val speakers = categorizedMembers.filter {
                            it.role == "speaker" && it.userId != currentRoom.hostUserId && !roomAdmins.contains(it.userId)
                        }
                        if (speakers.isNotEmpty()) {
                            item {
                                Text("🎙️ ON MIC SPEAKERS (${speakers.size})", fontSize = 11.sp, fontWeight = FontWeight.Bold, color = Color(0xFF81C784), modifier = Modifier.padding(top = 10.dp, bottom = 4.dp))
                            }
                            items(speakers, key = { "speaker_${it.userId}" }) { member ->
                                MemberItemRow(
                                    member = member,
                                    isHost = false,
                                    isAdmin = false,
                                    canManage = isRoomOwner && member.userId != currentUserId,
                                    onClick = { selectedMemberForAction = member }
                                )
                            }
                        }

                        // Section 4: Audience & Guests
                        val audience = categorizedMembers.filter {
                            it.role == "audience" && it.userId != currentRoom.hostUserId && !roomAdmins.contains(it.userId) && !speakers.any { sp -> sp.userId == it.userId }
                        }
                        if (audience.isNotEmpty()) {
                            item {
                                Text("👥 AUDIENCE & GUESTS (${audience.size})", fontSize = 11.sp, fontWeight = FontWeight.Bold, color = Color.White.copy(alpha = 0.6f), modifier = Modifier.padding(top = 10.dp, bottom = 4.dp))
                            }
                            items(audience, key = { "aud_${it.userId}" }) { member ->
                                MemberItemRow(
                                    member = member,
                                    isHost = false,
                                    isAdmin = false,
                                    canManage = isRoomOwner && member.userId != currentUserId,
                                    onClick = { selectedMemberForAction = member }
                                )
                            }
                        }
                    }
                }
            }
        }

        // ==========================================
        // 7.1 APPOINT / MANAGE ADMINS PANEL (Room Owner)
        // ==========================================
        if (showAdminManageDialog && isRoomOwner) {
            Dialog(onDismissRequest = { showAdminManageDialog = false }) {
                Card(
                    shape = RoundedCornerShape(24.dp),
                    colors = CardDefaults.cardColors(containerColor = Color(0xFF1E1C2A)),
                    modifier = Modifier.fillMaxWidth().padding(8.dp)
                ) {
                    Column(
                        modifier = Modifier.padding(20.dp),
                        horizontalAlignment = Alignment.CenterHorizontally
                    ) {
                        Text("🛡️ Appoint Party Admins", fontSize = 18.sp, fontWeight = FontWeight.Bold, color = Color.White)
                        Spacer(modifier = Modifier.height(4.dp))
                        Text("Appoint up to 5 trusted admins to manage seats and moderate the party.", fontSize = 12.sp, color = Color.White.copy(alpha = 0.7f), textAlign = TextAlign.Center)

                        Spacer(modifier = Modifier.height(14.dp))

                        // Currently appointed Admins list
                        Text(
                            "Appointed Admins (${roomAdmins.size}/5):",
                            fontSize = 12.sp,
                            fontWeight = FontWeight.Bold,
                            color = Color(0xFF64B5F6),
                            modifier = Modifier.fillMaxWidth()
                        )
                        Spacer(modifier = Modifier.height(6.dp))

                        if (roomAdmins.isEmpty()) {
                            Text("No admins appointed yet. Select from members below.", fontSize = 11.sp, color = Color.White.copy(alpha = 0.5f), modifier = Modifier.padding(vertical = 6.dp))
                        } else {
                            roomAdmins.forEach { adminId ->
                                val memberInfo = categorizedMembers.firstOrNull { it.userId == adminId }
                                val adminName = memberInfo?.userName ?: "Admin $adminId"
                                Row(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .padding(vertical = 4.dp)
                                        .clip(RoundedCornerShape(12.dp))
                                        .background(Color(0x331E88E5))
                                        .padding(horizontal = 12.dp, vertical = 8.dp),
                                    horizontalArrangement = Arrangement.SpaceBetween,
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Row(verticalAlignment = Alignment.CenterVertically) {
                                        Icon(Icons.Default.Security, contentDescription = null, tint = Color(0xFF64B5F6), modifier = Modifier.size(16.dp))
                                        Spacer(modifier = Modifier.width(8.dp))
                                        Text(adminName, fontSize = 13.sp, fontWeight = FontWeight.Bold, color = Color.White)
                                    }
                                    Button(
                                        onClick = {
                                            scope.launch {
                                                partyService.removeRoomAdmin(currentRoom.id, adminId, adminName)
                                                roomAdmins = partyService.fetchRoomAdmins(currentRoom.id)
                                                AppToast.show("Removed admin role for $adminName")
                                            }
                                        },
                                        colors = ButtonDefaults.buttonColors(containerColor = Color(0x66FF5252)),
                                        shape = RoundedCornerShape(8.dp),
                                        contentPadding = PaddingValues(horizontal = 8.dp, vertical = 4.dp)
                                    ) {
                                        Text("Revoke", fontSize = 10.sp, color = Color.White)
                                    }
                                }
                            }
                        }

                        Spacer(modifier = Modifier.height(14.dp))

                        // Appoint from other members in the room
                        Text(
                            "Appoint from Present Members:",
                            fontSize = 12.sp,
                            fontWeight = FontWeight.Bold,
                            color = QivoYellow,
                            modifier = Modifier.fillMaxWidth()
                        )
                        Spacer(modifier = Modifier.height(6.dp))

                        val nonAdminMembers = categorizedMembers.filter { it.userId != currentUserId && it.userId != currentRoom.hostUserId && !roomAdmins.contains(it.userId) }
                        if (nonAdminMembers.isEmpty()) {
                            Text("No eligible members present in room right now.", fontSize = 11.sp, color = Color.White.copy(alpha = 0.5f), modifier = Modifier.padding(vertical = 6.dp))
                        } else {
                            LazyColumn(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .heightIn(max = 180.dp),
                                verticalArrangement = Arrangement.spacedBy(6.dp)
                            ) {
                                items(nonAdminMembers, key = { "appoint_${it.userId}" }) { candidate ->
                                    Row(
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .clip(RoundedCornerShape(12.dp))
                                            .background(Color(0x33FFFFFF))
                                            .padding(horizontal = 12.dp, vertical = 8.dp),
                                        horizontalArrangement = Arrangement.SpaceBetween,
                                        verticalAlignment = Alignment.CenterVertically
                                    ) {
                                        Text(candidate.userName, fontSize = 13.sp, fontWeight = FontWeight.Bold, color = Color.White)
                                        Button(
                                            onClick = {
                                                if (roomAdmins.size >= 5) {
                                                    AppToast.show("Maximum 5 Admins reached")
                                                    return@Button
                                                }
                                                scope.launch {
                                                    val res = partyService.appointRoomAdmin(currentRoom.id, candidate.userId, candidate.userName)
                                                    roomAdmins = partyService.fetchRoomAdmins(currentRoom.id)
                                                    AppToast.show(res.second)
                                                }
                                            },
                                            colors = ButtonDefaults.buttonColors(containerColor = QivoOrange),
                                            shape = RoundedCornerShape(8.dp),
                                            contentPadding = PaddingValues(horizontal = 10.dp, vertical = 4.dp)
                                        ) {
                                            Text("Appoint", fontSize = 11.sp, color = Color.White, fontWeight = FontWeight.Bold)
                                        }
                                    }
                                }
                            }
                        }

                        Spacer(modifier = Modifier.height(16.dp))
                        Button(
                            onClick = { showAdminManageDialog = false },
                            colors = ButtonDefaults.buttonColors(containerColor = Color(0x55FFFFFF)),
                            shape = RoundedCornerShape(12.dp),
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Text("Done", color = Color.White, fontWeight = FontWeight.Bold)
                        }
                    }
                }
            }
        }

        // ==========================================
        // 8. MEMBER ACTIONS DIALOG (Appoint Admin / Mute / Send Gift)
        // ==========================================
        if (selectedMemberForAction != null) {
            val member = selectedMemberForAction!!
            val isTargetAdmin = roomAdmins.contains(member.userId)
            val isTargetHost = member.userId == currentRoom.hostUserId

            Dialog(onDismissRequest = { selectedMemberForAction = null }) {
                Card(
                    shape = RoundedCornerShape(24.dp),
                    colors = CardDefaults.cardColors(containerColor = Color(0xFF1E1C2A)),
                    modifier = Modifier.fillMaxWidth().padding(16.dp)
                ) {
                    Column(
                        modifier = Modifier.padding(20.dp),
                        horizontalAlignment = Alignment.CenterHorizontally
                    ) {
                        Text(
                            text = member.userName,
                            fontSize = 18.sp,
                            fontWeight = FontWeight.Bold,
                            color = Color.White
                        )
                        Spacer(modifier = Modifier.height(4.dp))
                        Text(
                            text = "Role: ${if (isTargetHost) "Host 👑" else if (isTargetAdmin) "Admin 🛡️" else member.role.uppercase()}",
                            fontSize = 12.sp,
                            color = QivoYellow
                        )

                        Spacer(modifier = Modifier.height(16.dp))

                        // Appoint / Revoke Admin (Room Owner only, max 5)
                        if (isRoomOwner && member.userId != currentUserId && !isTargetHost) {
                            Button(
                                onClick = {
                                    scope.launch {
                                        if (isTargetAdmin) {
                                            partyService.removeRoomAdmin(currentRoom.id, member.userId, member.userName)
                                            AppToast.show("${member.userName} is no longer an Admin")
                                        } else {
                                            if (roomAdmins.size >= 5) {
                                                AppToast.show("Maximum 5 Admins reached")
                                            } else {
                                                val res = partyService.appointRoomAdmin(currentRoom.id, member.userId, member.userName)
                                                AppToast.show(res.second)
                                            }
                                        }
                                        roomAdmins = partyService.fetchRoomAdmins(currentRoom.id)
                                        selectedMemberForAction = null
                                    }
                                },
                                colors = ButtonDefaults.buttonColors(
                                    containerColor = if (isTargetAdmin) Color(0xFFFF5252) else QivoOrange
                                ),
                                shape = RoundedCornerShape(14.dp),
                                modifier = Modifier.fillMaxWidth()
                            ) {
                                Icon(Icons.Default.Security, contentDescription = null)
                                Spacer(modifier = Modifier.width(6.dp))
                                Text(if (isTargetAdmin) "Revoke Admin Role" else "Appoint as Party Admin (Max 5)", fontWeight = FontWeight.Bold)
                            }
                            Spacer(modifier = Modifier.height(10.dp))
                        }

                        // Send Gift button
                        Button(
                            onClick = {
                                initialSelectedGiftRecipientId = member.userId
                                selectedMemberForAction = null
                                showGiftDialog = true
                            },
                            colors = ButtonDefaults.buttonColors(containerColor = QivoOrange),
                            shape = RoundedCornerShape(14.dp),
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Icon(Icons.Default.CardGiftcard, contentDescription = null)
                            Spacer(modifier = Modifier.width(6.dp))
                            Text("Send Gift to ${member.userName}", fontWeight = FontWeight.Bold)
                        }
                    }
                }
            }
        }

        // ==========================================
        // 8.1 SEAT ACTIONS DIALOG (When tapping an occupied seat)
        // ==========================================
        if (selectedSeatForAction != null) {
            val seat = selectedSeatForAction!!
            val isMyOccupiedSeat = seat.userId == currentUserId

            Dialog(onDismissRequest = { selectedSeatForAction = null }) {
                Card(
                    shape = RoundedCornerShape(24.dp),
                    colors = CardDefaults.cardColors(containerColor = Color(0xFF1E1C2A)),
                    modifier = Modifier.fillMaxWidth().padding(16.dp)
                ) {
                    Column(
                        modifier = Modifier.padding(20.dp),
                        horizontalAlignment = Alignment.CenterHorizontally
                    ) {
                        Text(
                            text = "Seat #${seat.seatIndex + 1}: ${seat.userName}",
                            fontSize = 18.sp,
                            fontWeight = FontWeight.Bold,
                            color = Color.White
                        )
                        Spacer(modifier = Modifier.height(16.dp))

                        if (isMyOccupiedSeat) {
                            Button(
                                onClick = {
                                    selectedSeatForAction = null
                                    showReactionDialog = true
                                },
                                colors = ButtonDefaults.buttonColors(containerColor = QivoOrange),
                                shape = RoundedCornerShape(14.dp),
                                modifier = Modifier.fillMaxWidth()
                            ) {
                                Text("🎭 Express Live Reaction", fontWeight = FontWeight.Bold)
                            }
                            Spacer(modifier = Modifier.height(10.dp))
                            Button(
                                onClick = {
                                    seats = seats.map { if (it.userId == currentUserId) PartySeat(seatIndex = it.seatIndex) else it }
                                    PartyRoomSessionManager.setSeated(false, -1)
                                    voiceEngine.leaveMicSeat(currentUserId)
                                    selectedSeatForAction = null
                                    scope.launch {
                                        partyService.releaseSeat(currentRoom.id, currentUserId)
                                        seats = partyService.fetchRoomSeats(currentRoom.id, currentRoom.seatsCount)
                                        messages = partyService.fetchRoomMessages(currentRoom.id)
                                        // Stop toasting when user leaves seat
                                    }
                                },
                                colors = ButtonDefaults.buttonColors(containerColor = Color(0xFFFF5252)),
                                shape = RoundedCornerShape(14.dp),
                                modifier = Modifier.fillMaxWidth()
                            ) {
                                Icon(Icons.AutoMirrored.Filled.ExitToApp, contentDescription = null)
                                Spacer(modifier = Modifier.width(6.dp))
                                Text("Leave Mic Seat", fontWeight = FontWeight.Bold)
                            }
                        } else {
                            Button(
                                onClick = {
                                    initialSelectedGiftRecipientId = seat.userId
                                    selectedSeatForAction = null
                                    showGiftDialog = true
                                },
                                colors = ButtonDefaults.buttonColors(containerColor = QivoOrange),
                                shape = RoundedCornerShape(14.dp),
                                modifier = Modifier.fillMaxWidth()
                            ) {
                                Icon(Icons.Default.CardGiftcard, contentDescription = null)
                                Spacer(modifier = Modifier.width(6.dp))
                                Text("Send Gift to ${seat.userName}", fontWeight = FontWeight.Bold)
                            }
                        }
                    }
                }
            }
        }

        // ==========================================
        // 9. LEAVE ROOM / STAY IN ROOM DIALOG ("Stay in Room" vs "Leave Completely")
        // ==========================================
        if (showLeaveConfirmDialog) {
            Dialog(
                onDismissRequest = { showLeaveConfirmDialog = false }
            ) {
                Card(
                    shape = RoundedCornerShape(24.dp),
                    colors = CardDefaults.cardColors(containerColor = Color(0xFF1E1C2A)),
                    border = androidx.compose.foundation.BorderStroke(1.dp, Color.White.copy(alpha = 0.1f)),
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 16.dp)
                ) {
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(24.dp)
                    ) {
                        Text(
                            text = "Party Room Options",
                            fontWeight = FontWeight.Bold,
                            fontSize = 20.sp,
                            color = Color.White
                        )
                        Spacer(modifier = Modifier.height(12.dp))
                        Text(
                            text = "Choose how you want to exit ${currentRoom.name}:",
                            color = Color.White.copy(alpha = 0.9f),
                            fontSize = 14.sp
                        )
                        Spacer(modifier = Modifier.height(10.dp))
                        Text(
                            text = "• Stay in Room: Collapses to a movable floating round button. You can freely explore the app while staying connected to voice audio & mic.",
                            color = Color.White.copy(alpha = 0.75f),
                            fontSize = 12.sp,
                            lineHeight = 16.sp
                        )
                        Spacer(modifier = Modifier.height(6.dp))
                        Text(
                            text = "• Leave Completely: Disconnects from audio voice room and releases seat.",
                            color = Color.White.copy(alpha = 0.75f),
                            fontSize = 12.sp,
                            lineHeight = 16.sp
                        )

                        Spacer(modifier = Modifier.height(24.dp))

                        // Option 1: Stay in Room (Floating Mini Player)
                        Button(
                            onClick = {
                                showLeaveConfirmDialog = false
                                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M && !Settings.canDrawOverlays(context)) {
                                    try {
                                        val intent = Intent(
                                            Settings.ACTION_MANAGE_OVERLAY_PERMISSION,
                                            Uri.parse("package:${context.packageName}")
                                        ).apply {
                                            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                                        }
                                        context.startActivity(intent)
                                        AppToast.show("Please allow Display over other apps for floating mini player", isLong = true)
                                    } catch (e: Exception) {
                                        val intent = Intent(Settings.ACTION_MANAGE_OVERLAY_PERMISSION).apply {
                                            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                                        }
                                        context.startActivity(intent)
                                    }
                                }
                                PartyRoomSessionManager.enterRoom(currentRoom)
                                PartyRoomSessionManager.setSeated(isSeated, mySeatIndex)
                                PartyRoomSessionManager.setMicMuted(isMicMuted)
                                PartyRoomSessionManager.minimizeRoom()
                                onLeaveRoom()
                            },
                            colors = ButtonDefaults.buttonColors(containerColor = QivoOrange),
                            shape = RoundedCornerShape(14.dp),
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(48.dp)
                        ) {
                            Icon(Icons.Default.OpenInNew, contentDescription = null, modifier = Modifier.size(18.dp))
                            Spacer(modifier = Modifier.width(8.dp))
                            Text("Stay in Room (Mini Player)", color = Color.White, fontWeight = FontWeight.Bold)
                        }

                        Spacer(modifier = Modifier.height(12.dp))

                        // Option 2: Leave Room Completely
                        Button(
                            onClick = {
                                isLeavingRoom = true
                                showLeaveConfirmDialog = false
                                PartyRoomSessionManager.leaveRoom(currentUserId, scope)
                                AppToast.show("Left party room")
                                onLeaveRoom()
                            },
                            colors = ButtonDefaults.buttonColors(containerColor = Color(0x66FF3D00)),
                            border = androidx.compose.foundation.BorderStroke(1.dp, Color(0xFFFF5252)),
                            shape = RoundedCornerShape(14.dp),
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(48.dp)
                        ) {
                            Icon(Icons.AutoMirrored.Filled.ExitToApp, contentDescription = null, modifier = Modifier.size(18.dp))
                            Spacer(modifier = Modifier.width(8.dp))
                            Text("Leave Room Completely", color = Color.White, fontWeight = FontWeight.Bold)
                        }

                        Spacer(modifier = Modifier.height(8.dp))

                        // Option 3: Cancel
                        TextButton(
                            onClick = { showLeaveConfirmDialog = false },
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(44.dp)
                        ) {
                            Text(
                                "Cancel",
                                color = Color.White.copy(alpha = 0.7f),
                                fontWeight = FontWeight.SemiBold,
                                textAlign = TextAlign.Center
                            )
                        }
                    }
                }
            }
        }

        // ==========================================
        // 9.1 DELETE ROOM PERMANENTLY CONFIRMATION DIALOG (Room Owner - Swipeable Action)
        // ==========================================
        if (showDeleteConfirmDialog && isRoomOwner) {
            Dialog(onDismissRequest = { if (!isDeletingRoom) showDeleteConfirmDialog = false }) {
                Card(
                    shape = RoundedCornerShape(24.dp),
                    colors = CardDefaults.cardColors(containerColor = Color(0xFF1B182B)),
                    border = androidx.compose.foundation.BorderStroke(1.5.dp, Color(0xFFFF5252).copy(alpha = 0.7f)),
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(16.dp)
                ) {
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(20.dp),
                        horizontalAlignment = Alignment.CenterHorizontally
                    ) {
                        Box(
                            modifier = Modifier
                                .size(56.dp)
                                .clip(CircleShape)
                                .background(Color(0x33FF1744)),
                            contentAlignment = Alignment.Center
                        ) {
                            Icon(
                                imageVector = Icons.Default.DeleteForever,
                                contentDescription = null,
                                tint = Color(0xFFFF5252),
                                modifier = Modifier.size(32.dp)
                            )
                        }

                        Spacer(modifier = Modifier.height(14.dp))

                        Text(
                            text = "Delete Party Room?",
                            fontSize = 18.sp,
                            fontWeight = FontWeight.ExtraBold,
                            color = Color.White
                        )

                        Spacer(modifier = Modifier.height(8.dp))

                        Text(
                            text = "Are you sure you want to permanently delete \"${currentRoom.name}\"? All members will be disconnected and this room will be permanently closed.",
                            fontSize = 12.sp,
                            color = Color.White.copy(alpha = 0.8f),
                            textAlign = TextAlign.Center,
                            lineHeight = 16.sp
                        )

                        Spacer(modifier = Modifier.height(20.dp))

                        // Tactile Swipe to Delete Slider
                        SwipeToDeleteSlider(
                            promptText = "Slide right to delete room",
                            isDeleting = isDeletingRoom,
                            onDeleteConfirmed = {
                                isDeletingRoom = true
                                scope.launch {
                                    partyService.deletePartyRoom(currentRoom.id, currentUserId, context)
                                    AppDataCacheManager.removePartyRoomFromCache(context, currentRoom.id)
                                    PartyRoomSessionManager.leaveRoom(currentUserId, scope)
                                    AppToast.show("Party room deleted permanently")
                                    showDeleteConfirmDialog = false
                                    isDeletingRoom = false
                                    onLeaveRoom()
                                }
                            }
                        )

                        Spacer(modifier = Modifier.height(12.dp))

                        TextButton(
                            onClick = { if (!isDeletingRoom) showDeleteConfirmDialog = false },
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Text("Cancel", color = Color.White.copy(alpha = 0.7f), fontWeight = FontWeight.SemiBold)
                        }
                    }
                }
            }
        }

        // ==========================================
        // 10. ROOM SEAT CAPACITY DIALOG (Room Owner & Admins)
        // ==========================================
        if (showSeatCapacityDialog && (isRoomOwner || isRoomAdmin)) {
            Dialog(onDismissRequest = { showSeatCapacityDialog = false }) {
                Card(
                    shape = RoundedCornerShape(24.dp),
                    colors = CardDefaults.cardColors(containerColor = Color(0xFF1E1C2A)),
                    modifier = Modifier.fillMaxWidth().padding(16.dp)
                ) {
                    Column(
                        modifier = Modifier.padding(20.dp),
                        horizontalAlignment = Alignment.CenterHorizontally
                    ) {
                        Text("🎙️ Room Seat Capacity", fontSize = 18.sp, fontWeight = FontWeight.Bold, color = Color.White)
                        Spacer(modifier = Modifier.height(6.dp))
                        Text("Change the number of active mic seats (4 to 16). Users seated beyond the new limit will safely step down.", fontSize = 12.sp, color = Color.White.copy(alpha = 0.7f), textAlign = TextAlign.Center)

                        Spacer(modifier = Modifier.height(16.dp))

                        listOf(4, 8, 10, 12, 16).forEach { count ->
                            val isSelected = seats.size == count
                            Surface(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(vertical = 4.dp)
                                    .clip(RoundedCornerShape(12.dp))
                                    .clickable {
                                        scope.launch {
                                            partyService.updateRoomSeatCount(currentRoom.id, count)
                                            seats = partyService.fetchRoomSeats(currentRoom.id, count)
                                            currentRoom = currentRoom.copy(seatsCount = count)
                                            AppToast.show("Seats updated to $count")
                                        }
                                        showSeatCapacityDialog = false
                                    },
                                shape = RoundedCornerShape(12.dp),
                                color = if (isSelected) QivoOrange else Color(0x33FFFFFF)
                            ) {
                                Row(
                                    modifier = Modifier.padding(horizontal = 16.dp, vertical = 12.dp),
                                    horizontalArrangement = Arrangement.SpaceBetween,
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Text("$count Mic Seats", fontSize = 14.sp, fontWeight = FontWeight.Bold, color = Color.White)
                                    if (isSelected) {
                                        Icon(Icons.Default.Check, contentDescription = null, tint = Color.White, modifier = Modifier.size(18.dp))
                                    }
                                }
                            }
                        }
                    }
                }
            }
        }

        // ==========================================
        // 10B. ROOM APPEARANCE & SETTINGS (Owner Customization)
        // ==========================================
        if (showRoomSettingsDialog) {
            var editName by remember { mutableStateOf(currentRoom.name) }
            var isSavingName by remember { mutableStateOf(false) }

            ModalBottomSheet(
                onDismissRequest = { showRoomSettingsDialog = false },
                containerColor = Color(0xFF1E1736),
                shape = RoundedCornerShape(topStart = 24.dp, topEnd = 24.dp)
            ) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 20.dp, vertical = 16.dp),
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            text = "Party Room Appearance",
                            fontSize = 18.sp,
                            fontWeight = FontWeight.Bold,
                            color = Color.White
                        )
                        IconButton(onClick = { showRoomSettingsDialog = false }) {
                            Icon(Icons.Default.Close, contentDescription = "Close", tint = Color.White.copy(alpha = 0.7f))
                        }
                    }

                    Spacer(modifier = Modifier.height(16.dp))

                    // 1. Room Name Section
                    Text(
                        text = "Room Name",
                        fontSize = 14.sp,
                        fontWeight = FontWeight.SemiBold,
                        color = Color.White.copy(alpha = 0.9f),
                        modifier = Modifier.fillMaxWidth()
                    )
                    Spacer(modifier = Modifier.height(6.dp))
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        OutlinedTextField(
                            value = editName,
                            onValueChange = { editName = it },
                            modifier = Modifier.weight(1f),
                            shape = RoundedCornerShape(12.dp),
                            singleLine = true,
                            colors = OutlinedTextFieldDefaults.colors(
                                focusedTextColor = Color.White,
                                unfocusedTextColor = Color.White,
                                focusedBorderColor = QivoOrange,
                                unfocusedBorderColor = Color(0x55FFFFFF)
                            )
                        )
                        Button(
                            onClick = {
                                if (editName.isNotBlank()) {
                                    isSavingName = true
                                    scope.launch {
                                        partyService.updateRoomAppearance(currentRoom.id, roomName = editName.trim())
                                        currentRoom = currentRoom.copy(name = editName.trim())
                                        isSavingName = false
                                        AppToast.show("Room name updated!")
                                    }
                                }
                            },
                            enabled = !isSavingName && editName.isNotBlank() && editName != currentRoom.name,
                            colors = ButtonDefaults.buttonColors(containerColor = QivoOrange),
                            shape = RoundedCornerShape(12.dp)
                        ) {
                            Text("Save", fontWeight = FontWeight.Bold)
                        }
                    }

                    Spacer(modifier = Modifier.height(20.dp))

                    // 2. Room Avatar / Cover Section
                    Text(
                        text = "Room Cover Avatar",
                        fontSize = 14.sp,
                        fontWeight = FontWeight.SemiBold,
                        color = Color.White.copy(alpha = 0.9f),
                        modifier = Modifier.fillMaxWidth()
                    )
                    Spacer(modifier = Modifier.height(8.dp))
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(12.dp)
                    ) {
                        // Current Cover Avatar Preview
                        val currentCover = currentRoom.coverUrl.ifBlank { currentRoom.hostAvatarUrl }
                        Box(
                            modifier = Modifier
                                .size(56.dp)
                                .clip(CircleShape)
                                .border(2.dp, QivoOrange, CircleShape)
                        ) {
                            val mascotRes = AvatarHelper.getDrawableForMascotKey(currentCover)
                            if (mascotRes != null) {
                                androidx.compose.foundation.Image(
                                    painter = androidx.compose.ui.res.painterResource(id = mascotRes),
                                    contentDescription = null,
                                    modifier = Modifier.fillMaxSize(),
                                    contentScale = ContentScale.Crop
                                )
                            } else if (currentCover.isNotBlank()) {
                                AsyncImage(
                                    model = currentCover,
                                    contentDescription = null,
                                    modifier = Modifier.fillMaxSize(),
                                    contentScale = ContentScale.Crop
                                )
                            } else {
                                androidx.compose.foundation.Image(
                                    painter = androidx.compose.ui.res.painterResource(id = AvatarHelper.getDefaultMascotRes(currentRoom.id, "Male")),
                                    contentDescription = null,
                                    modifier = Modifier.fillMaxSize(),
                                    contentScale = ContentScale.Crop
                                )
                            }
                        }

                        Button(
                            onClick = {
                                roomCoverPickerLauncher.launch(
                                    PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly)
                                )
                            },
                            colors = ButtonDefaults.buttonColors(containerColor = Color(0x33FFFFFF)),
                            shape = RoundedCornerShape(12.dp),
                            modifier = Modifier.weight(1f)
                        ) {
                            Icon(Icons.Default.PhotoCamera, contentDescription = null, tint = QivoYellow, modifier = Modifier.size(18.dp))
                            Spacer(modifier = Modifier.width(6.dp))
                            Text("Upload Avatar Photo", color = Color.White, fontSize = 13.sp)
                        }
                    }

                    Spacer(modifier = Modifier.height(20.dp))

                    // 3. Room Background Wallpaper Section
                    Text(
                        text = "Room Background Wallpaper",
                        fontSize = 14.sp,
                        fontWeight = FontWeight.SemiBold,
                        color = Color.White.copy(alpha = 0.9f),
                        modifier = Modifier.fillMaxWidth()
                    )
                    Spacer(modifier = Modifier.height(8.dp))

                    Button(
                        onClick = {
                            roomBgPickerLauncher.launch(
                                PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly)
                            )
                        },
                        colors = ButtonDefaults.buttonColors(containerColor = Color(0x33FFFFFF)),
                        shape = RoundedCornerShape(12.dp),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Icon(Icons.Default.Image, contentDescription = null, tint = QivoYellow, modifier = Modifier.size(18.dp))
                        Spacer(modifier = Modifier.width(8.dp))
                        Text("Upload Custom Wallpaper", color = Color.White, fontSize = 14.sp)
                    }

                    Spacer(modifier = Modifier.height(12.dp))

                    Text(
                        text = "Or Choose Preset Theme:",
                        fontSize = 12.sp,
                        color = Color.White.copy(alpha = 0.7f),
                        modifier = Modifier.fillMaxWidth()
                    )
                    Spacer(modifier = Modifier.height(8.dp))

                    val presetWallpapers = listOf(
                        "Cyberpunk Neon" to "https://images.unsplash.com/photo-1509198397868-475647b2a1e5?w=800&q=80",
                        "Midnight Galaxy" to "https://images.unsplash.com/photo-1506703719100-a0f3a48c0f86?w=800&q=80",
                        "Sunset Glow" to "https://images.unsplash.com/photo-1518495973542-4542c06a5843?w=800&q=80",
                        "Electric Purple" to "https://images.unsplash.com/photo-1550684848-fac1c5b4e853?w=800&q=80",
                        "Emerald Chill" to "https://images.unsplash.com/photo-1518709268805-4e9042af9f23?w=800&q=80",
                        "Crimson Velvet" to "https://images.unsplash.com/photo-1534447677768-be436bb09401?w=800&q=80"
                    )

                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .horizontalScroll(rememberScrollState()),
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        presetWallpapers.forEach { (name, url) ->
                            val isSelected = currentRoom.bgUrl == url
                            Surface(
                                onClick = {
                                    currentRoom = currentRoom.copy(bgUrl = url)
                                    scope.launch {
                                        partyService.updateRoomAppearance(currentRoom.id, bgUrl = url)
                                        AppToast.show("$name applied! ✨")
                                    }
                                },
                                shape = RoundedCornerShape(12.dp),
                                color = if (isSelected) QivoOrange else Color(0x33FFFFFF),
                                border = if (isSelected) androidx.compose.foundation.BorderStroke(2.dp, QivoYellow) else null,
                                modifier = Modifier.padding(vertical = 4.dp)
                            ) {
                                Column(
                                    modifier = Modifier.padding(horizontal = 12.dp, vertical = 8.dp),
                                    horizontalAlignment = Alignment.CenterHorizontally
                                ) {
                                    Text(name, fontSize = 12.sp, fontWeight = FontWeight.Bold, color = Color.White)
                                }
                            }
                        }
                    }

                    Spacer(modifier = Modifier.height(24.dp))
                }
            }
        }

        // ==========================================
        // 11. SEND GIFT BOTTOM SHEET (10 to 50k coins, 1 or Multi-User Selection)
        // ==========================================
        if (showGiftDialog) {
            val partyGiftRecipients = remember(seats, currentRoom, currentUserId) {
                val list = mutableListOf<GiftRecipientOption>()

                // 1. Host option (if not current user)
                if (currentRoom.hostUserId.isNotBlank() && currentRoom.hostUserId != currentUserId) {
                    list.add(
                        GiftRecipientOption(
                            id = currentRoom.hostUserId,
                            name = currentRoom.hostName.ifBlank { "Host" },
                            avatarUrl = currentRoom.hostAvatarUrl,
                            badge = "👑 Host"
                        )
                    )
                }

                // 2. Seated members (occupied seats, excluding current user)
                seats.forEach { s ->
                    if (s.userId.isNotBlank() && s.userId != currentUserId) {
                        if (list.none { it.id == s.userId }) {
                            list.add(
                                GiftRecipientOption(
                                    id = s.userId,
                                    name = s.userName.ifBlank { "Seat ${s.seatIndex + 1}" },
                                    avatarUrl = s.avatarUrl,
                                    badge = "Seat ${s.seatIndex + 1}"
                                )
                            )
                        }
                    }
                }

                // 3. Fallback / Whole room option if empty
                if (list.isEmpty()) {
                    list.add(
                        GiftRecipientOption(
                            id = currentRoom.id,
                            name = currentRoom.name.ifBlank { "Party Room" },
                            avatarUrl = currentRoom.coverUrl,
                            badge = "🎉 Room"
                        )
                    )
                }
                list
            }

            val defaultSelection = remember(initialSelectedGiftRecipientId, partyGiftRecipients) {
                if (initialSelectedGiftRecipientId != null && partyGiftRecipients.any { it.id == initialSelectedGiftRecipientId }) {
                    setOf(initialSelectedGiftRecipientId!!)
                } else if (partyGiftRecipients.isNotEmpty()) {
                    setOf(partyGiftRecipients.first().id)
                } else {
                    emptySet()
                }
            }

            GiftPadBottomSheet(
                currentCoins = userCoins,
                isExempt = isExempt,
                targetRecipientName = currentRoom.name,
                recipientOptions = partyGiftRecipients,
                defaultSelectedRecipientIds = defaultSelection,
                onDismiss = {
                    showGiftDialog = false
                    initialSelectedGiftRecipientId = null
                },
                onRechargeClick = {
                    showGiftDialog = false
                    initialSelectedGiftRecipientId = null
                    onOpenRechargeWallet()
                },
                onSendGift = { gift ->
                    showGiftDialog = false
                    initialSelectedGiftRecipientId = null
                    scope.launch {
                        val deductResult = profileService.deductGiftCoins(
                            senderId = currentUserId,
                            giftName = gift.name,
                            coins = gift.coins,
                            receiverId = currentRoom.id,
                            receiverName = currentRoom.name,
                            isAdmin = session?.isAdmin == true,
                            isCoinSeller = session?.isCoinSeller == true
                        )
                        if (!deductResult.first) {
                            AppToast.show("Insufficient coins to send ${gift.name} (${gift.coins} coins)!", isLong = true)
                            requiredCoinsForGift = gift.coins
                            showInsufficientCoinsSheet = true
                            return@launch
                        }
                        userCoins = deductResult.second
                        UserSessionManager.saveCoins(context, deductResult.second)

                        activeGiftBannerGift = gift
                        activeGiftRecipientName = "the party"
                        activeSentGiftPreview = gift
                        activeGiftBanner = "$currentUserName sent ${gift.name} ${gift.emoji} to the party!"
                        delay(4000)
                        activeGiftBanner = null
                        activeGiftBannerGift = null
                    }
                },
                onSendGiftToRecipients = { gift, selectedRecipients, totalRequiredCoins ->
                    showGiftDialog = false
                    initialSelectedGiftRecipientId = null
                    scope.launch {
                        val deductResult = profileService.deductGiftCoins(
                            senderId = currentUserId,
                            giftName = gift.name,
                            coins = totalRequiredCoins,
                            receiverId = if (selectedRecipients.size == 1) selectedRecipients.first().id else currentRoom.id,
                            receiverName = if (selectedRecipients.size == 1) selectedRecipients.first().name else currentRoom.name,
                            isAdmin = session?.isAdmin == true,
                            isCoinSeller = session?.isCoinSeller == true
                        )
                        if (!deductResult.first) {
                            AppToast.show("Insufficient coins to send ${gift.name} ($totalRequiredCoins coins)!", isLong = true)
                            requiredCoinsForGift = totalRequiredCoins
                            showInsufficientCoinsSheet = true
                            return@launch
                        }
                        userCoins = deductResult.second
                        UserSessionManager.saveCoins(context, deductResult.second)

                        val recipientLabel = if (selectedRecipients.size == 1) {
                            selectedRecipients.first().name
                        } else if (selectedRecipients.size >= partyGiftRecipients.size && partyGiftRecipients.size > 1) {
                            "everyone in the party"
                        } else {
                            "${selectedRecipients.size} members (${selectedRecipients.take(3).joinToString { it.name }}${if (selectedRecipients.size > 3) "..." else ""})"
                        }

                        activeGiftBannerGift = gift
                        activeGiftRecipientName = recipientLabel
                        activeSentGiftPreview = gift
                        activeGiftBanner = "$currentUserName sent ${gift.name} ${gift.emoji} to $recipientLabel!"
                        delay(4000)
                        activeGiftBanner = null
                        activeGiftBannerGift = null
                    }
                },
                onInsufficientCoins = { gift ->
                    showGiftDialog = false
                    initialSelectedGiftRecipientId = null
                    requiredCoinsForGift = gift.coins
                    showInsufficientCoinsSheet = true
                },
                onInsufficientCoinsWithTotal = { gift, totalRequiredCoins ->
                    showGiftDialog = false
                    initialSelectedGiftRecipientId = null
                    requiredCoinsForGift = totalRequiredCoins
                    showInsufficientCoinsSheet = true
                }
            )
        }

        // ==========================================
        // 12. INSUFFICIENT COINS BOTTOM SHEET (Recharge)
        // ==========================================
        if (showInsufficientCoinsSheet) {
            InsufficientCoinsBottomSheet(
                currentCoins = userCoins,
                requiredCoins = requiredCoinsForGift,
                onDismiss = { showInsufficientCoinsSheet = false },
                onOpenWallet = {
                    showInsufficientCoinsSheet = false
                    onOpenRechargeWallet()
                },
                onCoinsUpdated = { updatedCoins ->
                    userCoins = updatedCoins
                    UserSessionManager.saveCoins(context, updatedCoins)
                }
            )
        }

        // ==========================================
        // 13. LIVE SEAT REACTIONS BOTTOM SHEET (Seated Only)
        // ==========================================
        if (showReactionDialog) {
            PartyReactionBottomSheet(
                onReactionSelected = { reaction ->
                    showReactionDialog = false
                    if (isSeated && mySeatIndex != -1) {
                        val targetSeat = mySeatIndex
                        val now = System.currentTimeMillis()
                        activeSeatReactions = activeSeatReactions + (targetSeat to (reaction.type to now))
                        PartyRealtimeRelayManager.broadcastReaction(currentRoom.id, targetSeat, reaction.type.name)
                        scope.launch {
                            partyService.sendRoomMessage(
                                currentRoom.id,
                                PartyRoomMessage(
                                    roomId = currentRoom.id,
                                    senderName = currentUserName,
                                    content = "reacted ${reaction.emoji} (${reaction.name}) on Seat ${targetSeat + 1}! ✨",
                                    msgType = "reaction",
                                    giftIcon = reaction.emoji
                                )
                            )
                            messages = partyService.fetchRoomMessages(currentRoom.id)
                            delay(3500)
                            if (activeSeatReactions[targetSeat]?.second != null &&
                                System.currentTimeMillis() - (activeSeatReactions[targetSeat]?.second ?: 0L) >= 3400) {
                                activeSeatReactions = activeSeatReactions - targetSeat
                            }
                        }
                    } else {
                        AppToast.show("Take a seat to express live reactions! 🎙️")
                    }
                },
                onDismiss = { showReactionDialog = false }
            )
        }

        // ==========================================
        // 14. LOCAL PHONE MUSIC PLAYER BOTTOM SHEET (Host & Admins Seated)
        // ==========================================
        if (showMusicBottomSheet) {
            PartyMusicBottomSheet(
                isSeated = isSeated,
                onDismissRequest = { showMusicBottomSheet = false },
                onTrackChanged = { trackTitle ->
                    scope.launch {
                        val newMsg = PartyRoomMessage(
                            roomId = currentRoom.id,
                            senderName = currentUserName.ifBlank { "Host" },
                            content = "🎵 started streaming \"$trackTitle\" to the party room!",
                            msgType = "system",
                            giftIcon = "🎵"
                        )
                        partyService.sendRoomMessage(currentRoom.id, newMsg)
                        messages = partyService.fetchRoomMessages(currentRoom.id)
                    }
                }
            )
        }

        // 15. Sent 3D Gift Celebratory Full-Screen Preview Overlay
        if (activeSentGiftPreview != null) {
            SentGiftPreviewOverlay(
                gift = activeSentGiftPreview!!,
                recipientName = activeGiftRecipientName.ifEmpty { currentRoom.name },
                senderName = currentUserName,
                onDismiss = { activeSentGiftPreview = null }
            )
        }
    }
}

/**
 * Smooth waves radiating outwards outside the frame when a user is speaking
 */
@Composable
fun SpeakingRadioWaves(
    modifier: Modifier = Modifier
) {
    val infiniteTransition = rememberInfiniteTransition(label = "speaking_waves")

    val wave1 by infiniteTransition.animateFloat(
        initialValue = 0f,
        targetValue = 1f,
        animationSpec = infiniteRepeatable(
            animation = tween(1200, easing = LinearEasing),
            repeatMode = RepeatMode.Restart
        ),
        label = "w1"
    )

    val wave2 by infiniteTransition.animateFloat(
        initialValue = 0f,
        targetValue = 1f,
        animationSpec = infiniteRepeatable(
            animation = tween(1200, delayMillis = 400, easing = LinearEasing),
            repeatMode = RepeatMode.Restart
        ),
        label = "w2"
    )

    val wave3 by infiniteTransition.animateFloat(
        initialValue = 0f,
        targetValue = 1f,
        animationSpec = infiniteRepeatable(
            animation = tween(1200, delayMillis = 800, easing = LinearEasing),
            repeatMode = RepeatMode.Restart
        ),
        label = "w3"
    )

    Box(
        modifier = modifier,
        contentAlignment = Alignment.Center
    ) {
        listOf(wave1, wave2, wave3).forEach { progress ->
            val currentScale = 1f + (progress * 0.75f)
            val currentAlpha = ((1f - progress) * 0.85f).coerceIn(0f, 1f)

            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .scale(currentScale)
                    .clip(CircleShape)
                    .border(
                        width = (2.2f * (1f - progress * 0.4f)).dp,
                        brush = Brush.radialGradient(
                            listOf(
                                Color(0xFF00E676).copy(alpha = currentAlpha),
                                Color(0xFF69F0AE).copy(alpha = currentAlpha * 0.6f)
                            )
                        ),
                        shape = CircleShape
                    )
                    .background(Color(0xFF00E676).copy(alpha = currentAlpha * 0.12f))
            )
        }
    }
}

/**
 * Custom Party Seat Icon for empty slots (NO mic icons on seats, clean luxury armchair design)
 */
@Composable
fun CustomSeatSlot(
    seatIndex: Int,
    modifier: Modifier = Modifier
) {
    Box(
        modifier = modifier.size(52.dp),
        contentAlignment = Alignment.Center
    ) {
        // Armchair seat backrest cushion background (fully round circle)
        Box(
            modifier = Modifier
                .size(48.dp)
                .clip(CircleShape)
                .background(
                    Brush.verticalGradient(
                        listOf(
                            Color(0x664A148C),
                            Color(0x88281B4B),
                            Color(0xAA191230)
                        )
                    )
                )
                .border(
                    1.5.dp,
                    Brush.linearGradient(
                        listOf(
                            Color(0xAAFFA726),
                            Color(0x66FF7043),
                            Color(0x33FFFFFF)
                        )
                    ),
                    CircleShape
                ),
            contentAlignment = Alignment.Center
        ) {
            Column(
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.Center
            ) {
                Icon(
                    imageVector = Icons.Default.Weekend,
                    contentDescription = "Seat #${seatIndex + 1}",
                    tint = QivoYellow.copy(alpha = 0.95f),
                    modifier = Modifier.size(22.dp)
                )
                Spacer(modifier = Modifier.height(1.dp))
                Text(
                    text = "${seatIndex + 1}",
                    fontSize = 9.sp,
                    fontWeight = FontWeight.Bold,
                    color = Color.White.copy(alpha = 0.9f)
                )
            }
        }
    }
}

/**
 * Party Seat Item displaying user profile avatar with smooth speaking waves or custom seat icon
 */
@Composable
fun PartySeatItem(
    seat: PartySeat,
    isMyUser: Boolean,
    isSpeaking: Boolean,
    activeReaction: SeatReactionType? = null,
    onClick: () -> Unit
) {
    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        modifier = Modifier
            .width(66.dp)
            .clickable { onClick() }
            .testTag("seat_${seat.seatIndex}")
    ) {
        Box(
            modifier = Modifier.size(56.dp),
            contentAlignment = Alignment.Center
        ) {
            if (seat.userId.isNotBlank()) {
                // Smooth waves moving outside the frame when user is speaking
                if (isSpeaking) {
                    SpeakingRadioWaves(
                        modifier = Modifier.size(56.dp)
                    )
                }

                // Occupied Seat Avatar with animated frame
                AvatarHelper.UserAvatarImage(
                    avatarUrl = seat.avatarUrl,
                    userId = seat.userId,
                    contentDescription = seat.userName,
                    showFrame = true,
                    shape = CircleShape,
                    modifier = Modifier
                        .size(48.dp)
                        .border(
                            if (isSpeaking || isMyUser) 2.dp else 0.dp,
                            if (isSpeaking) Color(0xFF00E676) else if (isMyUser) QivoYellow else Color.Transparent,
                            CircleShape
                        ),
                    contentScale = ContentScale.Crop
                )

                // Host Crown
                if (seat.seatIndex == 0) {
                    Box(
                        modifier = Modifier
                            .align(Alignment.TopStart)
                            .size(16.dp)
                            .clip(CircleShape)
                            .background(Color(0xFFFFD600)),
                        contentAlignment = Alignment.Center
                    ) {
                        Text("👑", fontSize = 9.sp)
                    }
                }

                // Mute status overlay
                if (seat.isMuted) {
                    Box(
                        modifier = Modifier
                            .align(Alignment.BottomEnd)
                            .size(16.dp)
                            .clip(CircleShape)
                            .background(Color(0xFFFF5252)),
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(
                            imageVector = Icons.Default.MicOff,
                            contentDescription = "Muted",
                            tint = Color.White,
                            modifier = Modifier.size(10.dp)
                        )
                    }
                }

                // Live Expressive Animated Emoji Overlay (Laughing, Crying, Left/Right Kisses, etc.)
                if (activeReaction != null) {
                    SeatAnimatedReactionOverlay(
                        reactionType = activeReaction,
                        modifier = Modifier.size(64.dp)
                    )
                }
            } else {
                // Empty Seat Slot (NO mic icon)
                CustomSeatSlot(seatIndex = seat.seatIndex)
            }
        }

        Spacer(modifier = Modifier.height(4.dp))

        Text(
            text = if (seat.userId.isNotBlank()) seat.userName else "Seat ${seat.seatIndex + 1}",
            fontSize = 11.sp,
            fontWeight = if (isMyUser || seat.userId.isNotBlank()) FontWeight.Bold else FontWeight.Normal,
            color = if (isMyUser) QivoYellow else if (seat.userId.isNotBlank()) Color.White else Color.White.copy(alpha = 0.7f),
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            textAlign = TextAlign.Center
        )
    }
}

/**
 * Member Row for Room Attendees Side Panel
 */
@Composable
fun MemberItemRow(
    member: PartyRoomMember,
    isHost: Boolean,
    isAdmin: Boolean,
    canManage: Boolean,
    onClick: () -> Unit
) {
    Surface(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(12.dp))
            .clickable { onClick() },
        color = Color(0x22FFFFFF),
        shape = RoundedCornerShape(12.dp)
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 10.dp, vertical = 8.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            val mascotRes = AvatarHelper.getDrawableForMascotKey(member.avatarUrl)
            if (mascotRes != null) {
                androidx.compose.foundation.Image(
                    painter = androidx.compose.ui.res.painterResource(id = mascotRes),
                    contentDescription = member.userName,
                    modifier = Modifier
                        .size(36.dp)
                        .clip(CircleShape)
                        .border(1.5.dp, if (isHost) QivoYellow else if (isAdmin) Color(0xFF64B5F6) else Color(0x33FFFFFF), CircleShape),
                    contentScale = ContentScale.Crop
                )
            } else if (member.avatarUrl.isNotBlank() && (member.avatarUrl.startsWith("http") || member.avatarUrl.startsWith("data:"))) {
                AsyncImage(
                    model = member.avatarUrl,
                    contentDescription = member.userName,
                    modifier = Modifier
                        .size(36.dp)
                        .clip(CircleShape)
                        .border(1.5.dp, if (isHost) QivoYellow else if (isAdmin) Color(0xFF64B5F6) else Color(0x33FFFFFF), CircleShape),
                    contentScale = ContentScale.Crop
                )
            } else {
                val defRes = AvatarHelper.getDefaultMascotRes(userId = member.userId, gender = "Male")
                androidx.compose.foundation.Image(
                    painter = androidx.compose.ui.res.painterResource(id = defRes),
                    contentDescription = member.userName,
                    modifier = Modifier
                        .size(36.dp)
                        .clip(CircleShape)
                        .border(1.5.dp, if (isHost) QivoYellow else if (isAdmin) Color(0xFF64B5F6) else Color(0x33FFFFFF), CircleShape),
                    contentScale = ContentScale.Crop
                )
            }

            Spacer(modifier = Modifier.width(10.dp))

            Column(modifier = Modifier.weight(1f)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        text = member.userName,
                        fontSize = 13.sp,
                        fontWeight = FontWeight.Bold,
                        color = Color.White,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                    if (isHost) {
                        Spacer(modifier = Modifier.width(6.dp))
                        Box(
                            modifier = Modifier
                                .clip(RoundedCornerShape(4.dp))
                                .background(QivoYellow)
                                .padding(horizontal = 4.dp, vertical = 1.dp)
                        ) {
                            Text("HOST", fontSize = 8.sp, fontWeight = FontWeight.ExtraBold, color = Color.Black)
                        }
                    } else if (isAdmin) {
                        Spacer(modifier = Modifier.width(6.dp))
                        Box(
                            modifier = Modifier
                                .clip(RoundedCornerShape(4.dp))
                                .background(Color(0xFF1976D2))
                                .padding(horizontal = 4.dp, vertical = 1.dp)
                        ) {
                            Text("ADMIN", fontSize = 8.sp, fontWeight = FontWeight.Bold, color = Color.White)
                        }
                    }
                }
                if (member.seatIndex >= 0) {
                    Text("Seat #${member.seatIndex + 1}", fontSize = 10.sp, color = Color(0xFF81C784))
                }
            }

            if (canManage) {
                Icon(
                    imageVector = Icons.Default.MoreVert,
                    contentDescription = "Actions",
                    tint = Color.White.copy(alpha = 0.7f),
                    modifier = Modifier.size(18.dp)
                )
            }
        }
    }
}

@Composable
fun PartyMessageBubble(msg: PartyRoomMessage) {
    Surface(
        shape = RoundedCornerShape(12.dp),
        color = when (msg.msgType) {
            "system" -> Color(0x44000000)
            "gift" -> Color(0x88D84315)
            else -> Color(0x66000000)
        },
        modifier = Modifier.padding(vertical = 1.dp)
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 10.dp, vertical = 4.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            if (msg.msgType == "system") {
                Text(
                    text = msg.content,
                    fontSize = 11.sp,
                    color = QivoYellow,
                    fontWeight = FontWeight.SemiBold
                )
            } else if (msg.msgType == "gift") {
                Text(
                    text = "${msg.senderName} ${msg.content}",
                    fontSize = 12.sp,
                    color = Color.White,
                    fontWeight = FontWeight.Bold
                )
            } else {
                Text(
                    text = "${msg.senderName}: ",
                    fontSize = 12.sp,
                    fontWeight = FontWeight.Bold,
                    color = QivoOrange
                )
                Text(
                    text = msg.content,
                    fontSize = 12.sp,
                    fontWeight = FontWeight.Light,
                    letterSpacing = 0.15.sp,
                    color = Color.White
                )
            }
        }
    }
}

@Composable
fun GiftItemCard(
    name: String,
    emoji: String,
    price: Long,
    modifier: Modifier = Modifier,
    onSend: () -> Unit
) {
    Surface(
        modifier = modifier
            .clip(RoundedCornerShape(14.dp))
            .clickable { onSend() },
        color = Color(0xFF2A263D),
        shape = RoundedCornerShape(14.dp)
    ) {
        Column(
            modifier = Modifier.padding(8.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Text(emoji, fontSize = 28.sp)
            Spacer(modifier = Modifier.height(4.dp))
            Text(name, fontSize = 11.sp, fontWeight = FontWeight.SemiBold, color = Color.White, maxLines = 1)
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text("🪙 $price", fontSize = 10.sp, color = QivoYellow, fontWeight = FontWeight.Bold)
            }
        }
    }
}

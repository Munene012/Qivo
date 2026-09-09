package com.example.ui.screens

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import android.media.AudioManager
import android.os.Build
import android.provider.Settings
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.*
import androidx.compose.animation.core.*
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.scale
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import androidx.compose.ui.zIndex
import androidx.core.content.ContextCompat
import coil.compose.AsyncImage
import com.example.calling.ActiveCallSessionManager
import com.example.calling.ActiveCallStatus
import com.example.calling.CallRole
import com.example.data.AvatarHelper
import com.example.ui.components.CameraPreviewView
import com.example.ui.components.FloatingCallBubble
import com.example.ui.theme.QivoOrange
import com.example.ui.theme.QivoYellow

/**
 * Universal Global Call Overlay for QIVO.
 * Renders on top of ALL screens throughout the entire app.
 * Supports:
 * 1. Full-screen Outgoing Ringing
 * 2. Full-screen Incoming Call with Accept / Decline / Minimize
 * 3. Connected Voice & Video Calls with live RTC and real-time per-minute billing
 * 4. Reduction to a draggable floating round button on any screen
 * 5. Automatic termination after 40 seconds of unanswered ringing
 */
@Composable
fun GlobalCallOverlay(
    currentUserId: String,
    onOpenRechargeWallet: () -> Unit = {}
) {
    val context = LocalContext.current
    val currentSession by ActiveCallSessionManager.currentSession.collectAsState()
    val isMinimized by ActiveCallSessionManager.isMinimized.collectAsState()

    if (currentSession == null) {
        return
    }

    if (isMinimized) {
        FloatingCallBubble(
            onExpandCall = {
                ActiveCallSessionManager.expandCall()
            }
        )
        return
    }

    val session = currentSession!!
    val isIncoming = session.status == ActiveCallStatus.INCOMING_RINGING
    val isOutgoing = session.status == ActiveCallStatus.OUTGOING_RINGING
    val isConnected = session.status == ActiveCallStatus.CONNECTED
    val isEnded = session.status == ActiveCallStatus.ENDED

    // Intercept back button: minimize if connected, or end/decline
    BackHandler {
        if (isConnected) {
            ActiveCallSessionManager.minimizeCall(context)
        } else if (isOutgoing) {
            ActiveCallSessionManager.endCall(context)
        } else if (isIncoming) {
            ActiveCallSessionManager.declineCall(context)
        }
    }

    // Permission handling
    var hasAudioPermission by remember {
        mutableStateOf(
            ContextCompat.checkSelfPermission(context, Manifest.permission.RECORD_AUDIO) == PackageManager.PERMISSION_GRANTED
        )
    }
    var hasCameraPermission by remember {
        mutableStateOf(
            ContextCompat.checkSelfPermission(context, Manifest.permission.CAMERA) == PackageManager.PERMISSION_GRANTED
        )
    }

    val permissionsLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestMultiplePermissions()
    ) { perms ->
        hasAudioPermission = perms[Manifest.permission.RECORD_AUDIO] == true ||
                ContextCompat.checkSelfPermission(context, Manifest.permission.RECORD_AUDIO) == PackageManager.PERMISSION_GRANTED
        if (session.callType == CallType.VIDEO) {
            hasCameraPermission = perms[Manifest.permission.CAMERA] == true ||
                    ContextCompat.checkSelfPermission(context, Manifest.permission.CAMERA) == PackageManager.PERMISSION_GRANTED
        }
    }

    LaunchedEffect(session.callType) {
        val needed = mutableListOf<String>()
        if (!hasAudioPermission) needed.add(Manifest.permission.RECORD_AUDIO)
        if (session.callType == CallType.VIDEO && !hasCameraPermission) needed.add(Manifest.permission.CAMERA)
        if (needed.isNotEmpty()) {
            permissionsLauncher.launch(needed.toTypedArray())
        }
    }

    // Audio Manager speaker control
    DisposableEffect(session.isSpeakerOn) {
        val audioManager = context.getSystemService(Context.AUDIO_SERVICE) as? AudioManager
        val prevMode = audioManager?.mode ?: AudioManager.MODE_NORMAL
        val prevSpeaker = audioManager?.isSpeakerphoneOn ?: false
        try {
            audioManager?.mode = AudioManager.MODE_IN_COMMUNICATION
            audioManager?.isSpeakerphoneOn = session.isSpeakerOn
        } catch (_: Exception) {}

        onDispose {
            try {
                audioManager?.mode = prevMode
                audioManager?.isSpeakerphoneOn = prevSpeaker
            } catch (_: Exception) {}
        }
    }

    // Pulsing animation for calling rings
    val infiniteTransition = rememberInfiniteTransition(label = "globalCallPulse")
    val pulseScale by infiniteTransition.animateFloat(
        initialValue = 1f,
        targetValue = 1.25f,
        animationSpec = infiniteRepeatable(
            animation = tween(1100, easing = FastOutSlowInEasing),
            repeatMode = RepeatMode.Reverse
        ),
        label = "gPulseScale"
    )

    fun formatDuration(totalSecs: Int): String {
        val m = totalSecs / 60
        val s = totalSecs % 60
        return String.format("%02d:%02d", m, s)
    }

    val otherUser = session.otherUser
    val rate = if (session.callType == CallType.VIDEO) 160L else 80L

    Box(
        modifier = Modifier
            .fillMaxSize()
            .zIndex(9999f)
            .background(Color(0xFF0F0E17))
            .testTag("global_fullscreen_call_screen")
    ) {
        // Video Call Background: Camera Preview when video is connected
        if (session.callType == CallType.VIDEO && isConnected && session.isCameraEnabled) {
            CameraPreviewView(
                modifier = Modifier.fillMaxSize(),
                isFrontCamera = session.isFrontCamera,
                isEnabled = session.isCameraEnabled
            )

            // Dark subtle gradient overlay
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .background(
                        Brush.verticalGradient(
                            listOf(Color.Black.copy(alpha = 0.6f), Color.Transparent, Color.Black.copy(alpha = 0.8f))
                        )
                    )
            )
        }

        // Top App Bar Controls
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .statusBarsPadding()
                .padding(horizontal = 16.dp, vertical = 12.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            // Minimize to small round button (PiP)
            Surface(
                onClick = {
                    ActiveCallSessionManager.minimizeCall(context)
                },
                shape = CircleShape,
                color = Color.Black.copy(alpha = 0.55f),
                modifier = Modifier
                    .size(44.dp)
                    .testTag("btn_minimize_call_to_round_button")
            ) {
                Box(contentAlignment = Alignment.Center) {
                    Icon(
                        imageVector = Icons.Default.PictureInPictureAlt,
                        contentDescription = "Reduce to floating button",
                        tint = Color.White,
                        modifier = Modifier.size(22.dp)
                    )
                }
            }

            // Call Type Badge & Rate
            Surface(
                shape = RoundedCornerShape(20.dp),
                color = Color.Black.copy(alpha = 0.65f),
                border = androidx.compose.foundation.BorderStroke(1.dp, Color.White.copy(alpha = 0.2f))
            ) {
                Row(
                    modifier = Modifier.padding(horizontal = 14.dp, vertical = 6.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Icon(
                        imageVector = if (session.callType == CallType.VIDEO) Icons.Default.Videocam else Icons.Default.Call,
                        contentDescription = null,
                        tint = if (session.callType == CallType.VIDEO) QivoOrange else QivoYellow,
                        modifier = Modifier.size(16.dp)
                    )
                    Spacer(modifier = Modifier.width(6.dp))
                    Text(
                        text = if (session.callType == CallType.VIDEO) "Video Call" else "Voice Call",
                        color = Color.White,
                        fontSize = 13.sp,
                        fontWeight = FontWeight.SemiBold
                    )
                }
            }

            // Overlay permission indicator / prompt
            val hasOverlay = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) Settings.canDrawOverlays(context) else true
            if (!hasOverlay) {
                Surface(
                    onClick = {
                        ActiveCallSessionManager.minimizeCall(context)
                    },
                    shape = RoundedCornerShape(12.dp),
                    color = QivoOrange.copy(alpha = 0.85f),
                    modifier = Modifier.testTag("btn_floating_overlay_permission")
                ) {
                    Text(
                        text = "Float On Apps",
                        color = Color.Black,
                        fontSize = 10.sp,
                        fontWeight = FontWeight.Bold,
                        modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp)
                    )
                }
            } else {
                Spacer(modifier = Modifier.size(44.dp))
            }
        }

        // Center Content: User Avatar, Name, Status, and 40s Timeout
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .align(Alignment.Center)
                .padding(horizontal = 24.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            // Avatar with Pulsing Halo Ring
            Box(
                contentAlignment = Alignment.Center,
                modifier = Modifier.size(160.dp)
            ) {
                if (isOutgoing || isIncoming) {
                    Box(
                        modifier = Modifier
                            .size(150.dp)
                            .scale(pulseScale)
                            .clip(CircleShape)
                            .background(
                                Brush.radialGradient(
                                    listOf(
                                        (if (isIncoming) Color(0xFFFF9100) else QivoYellow).copy(alpha = 0.35f),
                                        Color.Transparent
                                    )
                                )
                            )
                    )
                }

                Surface(
                    shape = CircleShape,
                    color = Color(0xFF262438),
                    shadowElevation = 14.dp,
                    modifier = Modifier
                        .size(120.dp)
                        .border(
                            width = 3.dp,
                            brush = if (isConnected) Brush.linearGradient(listOf(Color(0xFF00E676), Color(0xFF69F0AE)))
                            else if (isIncoming) Brush.linearGradient(listOf(Color(0xFFFF9100), Color(0xFFFF3D00)))
                            else Brush.linearGradient(listOf(QivoOrange, QivoYellow)),
                            shape = CircleShape
                        )
                ) {
                    val mascotRes = AvatarHelper.getDrawableForMascotKey(otherUser.avatarUrl)
                    if (mascotRes != null) {
                        Image(
                            painter = painterResource(id = mascotRes),
                            contentDescription = otherUser.name,
                            modifier = Modifier.fillMaxSize(),
                            contentScale = ContentScale.Crop
                        )
                    } else if (otherUser.avatarUrl.isNotBlank() && (otherUser.avatarUrl.startsWith("http") || otherUser.avatarUrl.startsWith("data:"))) {
                        AsyncImage(
                            model = otherUser.avatarUrl,
                            contentDescription = otherUser.name,
                            modifier = Modifier.fillMaxSize(),
                            contentScale = ContentScale.Crop
                        )
                    } else {
                        val defaultRes = AvatarHelper.getDefaultMascotRes(userId = otherUser.id, gender = otherUser.gender)
                        Image(
                            painter = painterResource(id = defaultRes),
                            contentDescription = otherUser.name,
                            modifier = Modifier.fillMaxSize(),
                            contentScale = ContentScale.Crop
                        )
                    }
                }
            }

            Spacer(modifier = Modifier.height(18.dp))

            // User Name
            Text(
                text = otherUser.name.ifBlank { "QIVO User" },
                color = Color.White,
                fontSize = 26.sp,
                fontWeight = FontWeight.Bold
            )

            Spacer(modifier = Modifier.height(6.dp))

            // User Numeric ID & Country
            Text(
                text = "ID: ${if (otherUser.numericId > 0) otherUser.numericId else otherUser.id.take(8)} • ${otherUser.country}",
                color = Color.White.copy(alpha = 0.7f),
                fontSize = 13.sp
            )

            Spacer(modifier = Modifier.height(14.dp))

            // Status & Countdown Timer
            when {
                isOutgoing -> {
                    Text(
                        text = "Calling... Ringing",
                        color = QivoYellow,
                        fontSize = 18.sp,
                        fontWeight = FontWeight.SemiBold
                    )
                    Spacer(modifier = Modifier.height(4.dp))
                    Text(
                        text = "Auto-ends if unanswered in ${session.timeoutRemainingSeconds}s",
                        color = Color.White.copy(alpha = 0.6f),
                        fontSize = 12.sp
                    )
                }
                isIncoming -> {
                    Text(
                        text = "Incoming ${if (session.callType == CallType.VIDEO) "Video" else "Voice"} Call",
                        color = Color(0xFFFF9100),
                        fontSize = 20.sp,
                        fontWeight = FontWeight.Bold
                    )
                    Spacer(modifier = Modifier.height(4.dp))
                    Text(
                        text = "Ringing • Ends in ${session.timeoutRemainingSeconds}s",
                        color = Color.White.copy(alpha = 0.7f),
                        fontSize = 13.sp
                    )
                }
                isConnected -> {
                    Text(
                        text = formatDuration(session.durationSeconds),
                        color = Color(0xFF00E676),
                        fontSize = 28.sp,
                        fontWeight = FontWeight.Bold
                    )
                }
                isEnded -> {
                    val isDnd = session.endReason.contains("Do Not Disturb", ignoreCase = true) || session.endReason.contains("DND", ignoreCase = true)
                    if (isDnd) {
                        Surface(
                            shape = RoundedCornerShape(16.dp),
                            color = Color(0xFFE53935).copy(alpha = 0.2f),
                            border = androidx.compose.foundation.BorderStroke(1.dp, Color(0xFFE53935).copy(alpha = 0.6f)),
                            modifier = Modifier.padding(horizontal = 16.dp)
                        ) {
                            Column(
                                modifier = Modifier.padding(16.dp),
                                horizontalAlignment = Alignment.CenterHorizontally
                            ) {
                                Row(verticalAlignment = Alignment.CenterVertically) {
                                    Icon(
                                        imageVector = Icons.Default.DoNotDisturbOn,
                                        contentDescription = "DND Mode",
                                        tint = Color(0xFFFF5252),
                                        modifier = Modifier.size(24.dp)
                                    )
                                    Spacer(modifier = Modifier.width(8.dp))
                                    Text(
                                        text = "Do Not Disturb Mode",
                                        color = Color(0xFFFF5252),
                                        fontSize = 18.sp,
                                        fontWeight = FontWeight.Bold
                                    )
                                }
                                Spacer(modifier = Modifier.height(6.dp))
                                Text(
                                    text = session.endReason,
                                    color = Color.White,
                                    fontSize = 14.sp,
                                    textAlign = TextAlign.Center
                                )
                                Spacer(modifier = Modifier.height(4.dp))
                                Text(
                                    text = "Calls will not go through while user is on DND.",
                                    color = Color.White.copy(alpha = 0.65f),
                                    fontSize = 12.sp,
                                    textAlign = TextAlign.Center
                                )
                            }
                        }
                    } else {
                        Text(
                            text = session.endReason.ifBlank { "Call Ended" },
                            color = Color(0xFFFF5252),
                            fontSize = 18.sp,
                            fontWeight = FontWeight.Bold
                        )
                    }
                }
            }
        }

        // Bottom Action Bar: Accept/Decline for Incoming, or In-Call Controls for Connected/Outgoing
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .align(Alignment.BottomCenter)
                .navigationBarsPadding()
                .padding(bottom = 36.dp, start = 24.dp, end = 24.dp)
        ) {
            if (isIncoming) {
                // INCOMING CALL ACTION BAR: Accept & Decline + Minimize
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceEvenly,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    // Decline Button (Red)
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        Surface(
                            onClick = {
                                ActiveCallSessionManager.declineCall(context)
                            },
                            shape = CircleShape,
                            color = Color(0xFFE53935),
                            shadowElevation = 8.dp,
                            modifier = Modifier
                                .size(68.dp)
                                .testTag("btn_decline_incoming_call")
                        ) {
                            Box(contentAlignment = Alignment.Center) {
                                Icon(
                                    imageVector = Icons.Default.CallEnd,
                                    contentDescription = "Decline Call",
                                    tint = Color.White,
                                    modifier = Modifier.size(32.dp)
                                )
                            }
                        }
                        Spacer(modifier = Modifier.height(8.dp))
                        Text("Decline", color = Color.White, fontSize = 13.sp, fontWeight = FontWeight.Medium)
                    }

                    // Minimize Round Button (PiP)
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        Surface(
                            onClick = {
                                ActiveCallSessionManager.minimizeCall(context)
                            },
                            shape = CircleShape,
                            color = Color(0xFF37474F),
                            shadowElevation = 6.dp,
                            modifier = Modifier
                                .size(54.dp)
                                .testTag("btn_minimize_incoming_call")
                        ) {
                            Box(contentAlignment = Alignment.Center) {
                                Icon(
                                    imageVector = Icons.Default.PictureInPictureAlt,
                                    contentDescription = "Minimize Call",
                                    tint = Color.White,
                                    modifier = Modifier.size(24.dp)
                                )
                            }
                        }
                        Spacer(modifier = Modifier.height(8.dp))
                        Text("Minimize", color = Color.White.copy(alpha = 0.8f), fontSize = 12.sp)
                    }

                    // Accept Button (Green with Glowing Pulse)
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        Surface(
                            onClick = {
                                ActiveCallSessionManager.acceptCall(context)
                            },
                            shape = CircleShape,
                            color = Color(0xFF00C853),
                            shadowElevation = 12.dp,
                            modifier = Modifier
                                .size(68.dp)
                                .testTag("btn_accept_incoming_call")
                        ) {
                            Box(contentAlignment = Alignment.Center) {
                                Icon(
                                    imageVector = Icons.Default.Call,
                                    contentDescription = "Accept Call",
                                    tint = Color.White,
                                    modifier = Modifier.size(32.dp)
                                )
                            }
                        }
                        Spacer(modifier = Modifier.height(8.dp))
                        Text("Accept", color = Color.White, fontSize = 13.sp, fontWeight = FontWeight.Bold)
                    }
                }
            } else {
                // OUTGOING / CONNECTED CALL CONTROLS
                Column(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceEvenly,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        // Mute Mic Toggle
                        Column(horizontalAlignment = Alignment.CenterHorizontally) {
                            Surface(
                                onClick = { ActiveCallSessionManager.toggleMute(context) },
                                shape = CircleShape,
                                color = if (session.isMuted) Color(0xFFE53935) else Color.White.copy(alpha = 0.2f),
                                modifier = Modifier
                                    .size(52.dp)
                                    .testTag("btn_call_toggle_mute")
                            ) {
                                Box(contentAlignment = Alignment.Center) {
                                    Icon(
                                        imageVector = if (session.isMuted) Icons.Default.MicOff else Icons.Default.Mic,
                                        contentDescription = "Mute",
                                        tint = Color.White,
                                        modifier = Modifier.size(24.dp)
                                    )
                                }
                            }
                            Spacer(modifier = Modifier.height(6.dp))
                            Text(if (session.isMuted) "Unmute" else "Mute", color = Color.White, fontSize = 11.sp)
                        }

                        // Speaker Toggle
                        Column(horizontalAlignment = Alignment.CenterHorizontally) {
                            Surface(
                                onClick = { ActiveCallSessionManager.toggleSpeaker(context) },
                                shape = CircleShape,
                                color = if (session.isSpeakerOn) QivoYellow else Color.White.copy(alpha = 0.2f),
                                modifier = Modifier
                                    .size(52.dp)
                                    .testTag("btn_call_toggle_speaker")
                            ) {
                                Box(contentAlignment = Alignment.Center) {
                                    Icon(
                                        imageVector = if (session.isSpeakerOn) Icons.Default.VolumeUp else Icons.Default.VolumeDown,
                                        contentDescription = "Speaker",
                                        tint = if (session.isSpeakerOn) Color.Black else Color.White,
                                        modifier = Modifier.size(24.dp)
                                    )
                                }
                            }
                            Spacer(modifier = Modifier.height(6.dp))
                            Text("Speaker", color = Color.White, fontSize = 11.sp)
                        }

                        // Camera Flip / Toggle (if Video Call)
                        if (session.callType == CallType.VIDEO) {
                            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                                Surface(
                                    onClick = { ActiveCallSessionManager.flipCamera(context) },
                                    shape = CircleShape,
                                    color = Color.White.copy(alpha = 0.2f),
                                    modifier = Modifier
                                        .size(52.dp)
                                        .testTag("btn_call_flip_camera")
                                ) {
                                    Box(contentAlignment = Alignment.Center) {
                                        Icon(
                                            imageVector = Icons.Default.Cameraswitch,
                                            contentDescription = "Flip Camera",
                                            tint = Color.White,
                                            modifier = Modifier.size(24.dp)
                                        )
                                    }
                                }
                                Spacer(modifier = Modifier.height(6.dp))
                                Text("Flip", color = Color.White, fontSize = 11.sp)
                            }
                        }

                        // End Call Button (Red)
                        Column(horizontalAlignment = Alignment.CenterHorizontally) {
                            Surface(
                                onClick = {
                                    ActiveCallSessionManager.endCall(context)
                                },
                                shape = CircleShape,
                                color = Color(0xFFE53935),
                                shadowElevation = 10.dp,
                                modifier = Modifier
                                    .size(64.dp)
                                    .testTag("btn_end_call")
                            ) {
                                Box(contentAlignment = Alignment.Center) {
                                    Icon(
                                        imageVector = Icons.Default.CallEnd,
                                        contentDescription = "End Call",
                                        tint = Color.White,
                                        modifier = Modifier.size(30.dp)
                                    )
                                }
                            }
                            Spacer(modifier = Modifier.height(6.dp))
                            Text("End", color = Color.White, fontSize = 12.sp, fontWeight = FontWeight.Bold)
                        }
                    }
                }
            }
        }
    }
}

package com.example.ui.components

import androidx.compose.animation.core.*
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Call
import androidx.compose.material.icons.filled.CallEnd
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Videocam
import androidx.compose.material3.Icon
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.zIndex
import coil.compose.AsyncImage
import com.example.calling.ActiveCallSessionManager
import com.example.calling.ActiveCallStatus
import com.example.data.AvatarHelper
import com.example.ui.screens.CallType
import com.example.ui.theme.QivoOrange
import com.example.ui.theme.QivoYellow
import kotlin.math.roundToInt

/**
 * Minimized Floating Round Call Button.
 * Draggable across the entire screen.
 * Displays pulsing ring, caller avatar, remaining timeout or live duration, and expands to fullscreen on click.
 */
@Composable
fun FloatingCallBubble(
    onExpandCall: () -> Unit
) {
    val context = LocalContext.current
    val currentSession by ActiveCallSessionManager.currentSession.collectAsState()
    val isMinimized by ActiveCallSessionManager.isMinimized.collectAsState()

    if (currentSession == null || !isMinimized) {
        return
    }

    val session = currentSession!!
    val isConnected = session.status == ActiveCallStatus.CONNECTED
    val isIncoming = session.status == ActiveCallStatus.INCOMING_RINGING
    val isOutgoing = session.status == ActiveCallStatus.OUTGOING_RINGING

    val configuration = LocalConfiguration.current
    val density = LocalDensity.current

    val screenWidthPx = with(density) { configuration.screenWidthDp.dp.toPx() }
    val screenHeightPx = with(density) { configuration.screenHeightDp.dp.toPx() }

    var offsetX by remember { mutableFloatStateOf(screenWidthPx - with(density) { 92.dp.toPx() }) }
    var offsetY by remember { mutableFloatStateOf(screenHeightPx * 0.25f) }

    // Pulsing ring animation
    val infiniteTransition = rememberInfiniteTransition(label = "callBubblePulse")
    val waveScale by infiniteTransition.animateFloat(
        initialValue = 1f,
        targetValue = 1.38f,
        animationSpec = infiniteRepeatable(
            animation = tween(900, easing = FastOutSlowInEasing),
            repeatMode = RepeatMode.Restart
        ),
        label = "cScale"
    )
    val waveAlpha by infiniteTransition.animateFloat(
        initialValue = 0.85f,
        targetValue = 0f,
        animationSpec = infiniteRepeatable(
            animation = tween(900, easing = LinearEasing),
            repeatMode = RepeatMode.Restart
        ),
        label = "cAlpha"
    )

    fun formatDuration(totalSecs: Int): String {
        val m = totalSecs / 60
        val s = totalSecs % 60
        return String.format("%02d:%02d", m, s)
    }

    Box(
        modifier = Modifier
            .zIndex(99999f)
            .offset { IntOffset(offsetX.roundToInt(), offsetY.roundToInt()) }
            .size(80.dp)
            .pointerInput(Unit) {
                detectDragGestures { change, dragAmount ->
                    change.consume()
                    val newX = (offsetX + dragAmount.x).coerceIn(10f, screenWidthPx - with(density) { 90.dp.toPx() })
                    val newY = (offsetY + dragAmount.y).coerceIn(with(density) { 60.dp.toPx() }, screenHeightPx - with(density) { 140.dp.toPx() })
                    offsetX = newX
                    offsetY = newY
                }
            }
            .testTag("floating_call_round_button"),
        contentAlignment = Alignment.Center
    ) {
        // Pulsing Rings Canvas
        Canvas(modifier = Modifier.fillMaxSize()) {
            val center = Offset(size.width / 2, size.height / 2)
            val baseRadius = (size.width / 2) * 0.72f
            val pulseColor = when {
                isConnected -> Color(0xFF00E676)
                isIncoming -> Color(0xFFFF9100)
                else -> Color(0xFFFFD600)
            }

            drawCircle(
                color = pulseColor.copy(alpha = waveAlpha),
                radius = baseRadius * waveScale,
                center = center,
                style = Stroke(width = 3.dp.toPx())
            )
        }

        // Main Round Button with Glowing Border
        Surface(
            shape = CircleShape,
            color = Color(0xFF1E1C2A),
            shadowElevation = 12.dp,
            modifier = Modifier
                .size(64.dp)
                .clip(CircleShape)
                .border(
                    width = 2.5.dp,
                    brush = if (isConnected) {
                        Brush.sweepGradient(listOf(Color(0xFF00E676), Color(0xFF76FF03), Color(0xFF00E676)))
                    } else if (isIncoming) {
                        Brush.sweepGradient(listOf(Color(0xFFFF9100), Color(0xFFFF3D00), Color(0xFFFFD600), Color(0xFFFF9100)))
                    } else {
                        Brush.linearGradient(listOf(QivoOrange, QivoYellow))
                    },
                    shape = CircleShape
                )
                .clickable {
                    ActiveCallSessionManager.expandCall()
                    onExpandCall()
                }
        ) {
            Box(
                modifier = Modifier.fillMaxSize(),
                contentAlignment = Alignment.Center
            ) {
                // Other User Avatar or Mascot
                val otherUser = session.otherUser
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

                // Gradient scrim overlay
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .background(
                            Brush.verticalGradient(
                                listOf(Color.Transparent, Color(0x55000000), Color(0xDD000000))
                            )
                        )
                )

                // Call type badge in top-start
                Box(
                    modifier = Modifier
                        .align(Alignment.TopStart)
                        .padding(4.dp)
                        .size(16.dp)
                        .clip(CircleShape)
                        .background(if (isConnected) Color(0xFF00E676) else Color(0xFFFF9100)),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        imageVector = if (session.callType == CallType.VIDEO) Icons.Default.Videocam else Icons.Default.Call,
                        contentDescription = "Call Type",
                        tint = Color.White,
                        modifier = Modifier.size(10.dp)
                    )
                }

                // Time / Status label at bottom
                Box(
                    modifier = Modifier
                        .align(Alignment.BottomCenter)
                        .fillMaxWidth()
                        .background(Color(0xCC000000))
                        .padding(vertical = 2.dp),
                    contentAlignment = Alignment.Center
                ) {
                    Text(
                        text = if (isConnected) {
                            formatDuration(session.durationSeconds)
                        } else {
                            "${session.timeoutRemainingSeconds}s"
                        },
                        color = if (isConnected) Color(0xFF00E676) else Color(0xFFFFD54F),
                        fontSize = 10.sp,
                        fontWeight = FontWeight.Bold
                    )
                }
            }
        }

        // Quick End/Decline "X" Badge at Top-End
        Box(
            modifier = Modifier
                .align(Alignment.TopEnd)
                .size(22.dp)
                .clip(CircleShape)
                .background(Color(0xFFE53935))
                .border(1.dp, Color.White.copy(alpha = 0.8f), CircleShape)
                .clickable {
                    if (isIncoming) {
                        ActiveCallSessionManager.declineCall(context)
                    } else {
                        ActiveCallSessionManager.endCall(context)
                    }
                },
            contentAlignment = Alignment.Center
        ) {
            Icon(
                imageVector = Icons.Default.CallEnd,
                contentDescription = "End Call",
                tint = Color.White,
                modifier = Modifier.size(12.dp)
            )
        }
    }
}

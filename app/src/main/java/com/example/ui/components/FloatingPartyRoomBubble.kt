package com.example.ui.components

import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Mic
import androidx.compose.material.icons.filled.MicOff
import androidx.compose.material.icons.filled.VolumeUp
import androidx.compose.material3.Icon
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.zIndex
import coil.compose.AsyncImage
import com.example.data.AvatarHelper
import com.example.data.PartyMusicManager
import com.example.data.PartyRoomSessionManager
import com.example.ui.theme.QivoOrange
import com.example.ui.theme.QivoYellow
import kotlin.math.roundToInt

/**
 * Movable, draggable Floating Party Room Bubble with animated audio wave pulses.
 * Allows users to browse the app while continuously listening and talking in the active party room.
 */
@Composable
fun FloatingPartyRoomBubble(
    currentUserId: String,
    onExpandRoom: () -> Unit
) {
    val activeRoom by PartyRoomSessionManager.activeRoom.collectAsState()
    val isMinimized by PartyRoomSessionManager.isMinimized.collectAsState()
    val isSeated by PartyRoomSessionManager.isSeated.collectAsState()
    val isMicMuted by PartyRoomSessionManager.isMicMuted.collectAsState()
    val isSpeaking by PartyRoomSessionManager.isAnyoneSpeaking.collectAsState()
    val isMusicPlaying by PartyMusicManager.isPlaying.collectAsState()
    val isAudioActive = isSpeaking || isMusicPlaying
    val volume by PartyRoomSessionManager.speakingVolume.collectAsState()

    val scope = rememberCoroutineScope()
    val configuration = LocalConfiguration.current
    val density = LocalDensity.current

    val screenWidthPx = with(density) { configuration.screenWidthDp.dp.toPx() }
    val screenHeightPx = with(density) { configuration.screenHeightDp.dp.toPx() }

    var offsetX by remember { mutableFloatStateOf(screenWidthPx - with(density) { 90.dp.toPx() }) }
    var offsetY by remember { mutableFloatStateOf(screenHeightPx * 0.45f) }

    if (activeRoom == null || !isMinimized) {
        return
    }

    val room = activeRoom!!

    // Smooth wave animations when users are speaking on mic or music is playing
    val infiniteTransition = rememberInfiniteTransition(label = "bubbleWaves")
    val waveScale1 by infiniteTransition.animateFloat(
        initialValue = 1f,
        targetValue = if (isAudioActive) 1.45f else 1f,
        animationSpec = infiniteRepeatable(
            animation = tween(900, easing = FastOutSlowInEasing),
            repeatMode = RepeatMode.Restart
        ),
        label = "w1"
    )
    val waveAlpha1 by infiniteTransition.animateFloat(
        initialValue = if (isAudioActive) 0.8f else 0f,
        targetValue = 0f,
        animationSpec = infiniteRepeatable(
            animation = tween(900, easing = LinearEasing),
            repeatMode = RepeatMode.Restart
        ),
        label = "a1"
    )

    val waveScale2 by infiniteTransition.animateFloat(
        initialValue = 1f,
        targetValue = if (isAudioActive) 1.3f else 1f,
        animationSpec = infiniteRepeatable(
            animation = tween(1200, delayMillis = 300, easing = FastOutSlowInEasing),
            repeatMode = RepeatMode.Restart
        ),
        label = "w2"
    )
    val waveAlpha2 by infiniteTransition.animateFloat(
        initialValue = if (isAudioActive) 0.6f else 0f,
        targetValue = 0f,
        animationSpec = infiniteRepeatable(
            animation = tween(1200, delayMillis = 300, easing = LinearEasing),
            repeatMode = RepeatMode.Restart
        ),
        label = "a2"
    )

    // Beat equalizer lines animation values
    val b1 by infiniteTransition.animateFloat(0.25f, 1f, infiniteRepeatable(tween(260, easing = FastOutSlowInEasing), RepeatMode.Reverse), label = "b1")
    val b2 by infiniteTransition.animateFloat(0.4f, 0.95f, infiniteRepeatable(tween(380, delayMillis = 60, easing = LinearEasing), RepeatMode.Reverse), label = "b2")
    val b3 by infiniteTransition.animateFloat(0.15f, 1f, infiniteRepeatable(tween(220, delayMillis = 120, easing = FastOutSlowInEasing), RepeatMode.Reverse), label = "b3")
    val b4 by infiniteTransition.animateFloat(0.3f, 0.85f, infiniteRepeatable(tween(340, delayMillis = 180, easing = LinearEasing), RepeatMode.Reverse), label = "b4")
    val b5 by infiniteTransition.animateFloat(0.2f, 0.9f, infiniteRepeatable(tween(300, delayMillis = 90, easing = FastOutSlowInEasing), RepeatMode.Reverse), label = "b5")

    Box(
        modifier = Modifier
            .zIndex(9999f)
            .offset { IntOffset(offsetX.roundToInt(), offsetY.roundToInt()) }
            .size(76.dp)
            .pointerInput(Unit) {
                detectDragGestures { change, dragAmount ->
                    change.consume()
                    val newX = (offsetX + dragAmount.x).coerceIn(10f, screenWidthPx - with(density) { 86.dp.toPx() })
                    val newY = (offsetY + dragAmount.y).coerceIn(with(density) { 60.dp.toPx() }, screenHeightPx - with(density) { 140.dp.toPx() })
                    offsetX = newX
                    offsetY = newY
                }
            }
            .testTag("floating_party_bubble"),
        contentAlignment = Alignment.Center
    ) {
        // Audio Waves Canvas
        if (isAudioActive) {
            Canvas(modifier = Modifier.fillMaxSize()) {
                val center = Offset(size.width / 2, size.height / 2)
                val baseRadius = (size.width / 2) * 0.72f
                val pulseColor = if (isMusicPlaying) Color(0xFFFF9100) else Color(0xFF00E676)
                val pulseColor2 = if (isMusicPlaying) Color(0xFFFFD600) else Color(0xFF69F0AE)

                drawCircle(
                    color = pulseColor.copy(alpha = waveAlpha1),
                    radius = baseRadius * waveScale1,
                    center = center,
                    style = Stroke(width = 3.dp.toPx())
                )
                drawCircle(
                    color = pulseColor2.copy(alpha = waveAlpha2),
                    radius = baseRadius * waveScale2,
                    center = center,
                    style = Stroke(width = 2.dp.toPx())
                )
            }
        }

        // Main Round Button with Glowing Border
        Surface(
            shape = CircleShape,
            color = Color(0xFF1E1C2A),
            shadowElevation = 10.dp,
            modifier = Modifier
                .size(60.dp)
                .clip(CircleShape)
                .border(
                    width = if (isAudioActive) 2.5.dp else 2.dp,
                    brush = if (isMusicPlaying) {
                        Brush.sweepGradient(listOf(Color(0xFFFFD600), Color(0xFFFF6D00), Color(0xFFFF007F), Color(0xFFFFD600)))
                    } else if (isSpeaking) {
                        Brush.sweepGradient(listOf(Color(0xFF00E676), Color(0xFF76FF03), Color(0xFF00E676)))
                    } else if (isSeated) {
                        Brush.linearGradient(listOf(QivoOrange, QivoYellow))
                    } else {
                        Brush.linearGradient(listOf(Color(0xFF7E57C2), Color(0xFF42A5F5)))
                    },
                    shape = CircleShape
                )
                .clickable {
                    PartyRoomSessionManager.expandRoom()
                    onExpandRoom()
                }
        ) {
            Box(
                modifier = Modifier.fillMaxSize(),
                contentAlignment = Alignment.Center
            ) {
                // Room Cover or Host Mascot
                val mascotRes = AvatarHelper.getDrawableForMascotKey(room.coverUrl.ifBlank { room.hostAvatarUrl })
                if (mascotRes != null) {
                    androidx.compose.foundation.Image(
                        painter = androidx.compose.ui.res.painterResource(id = mascotRes),
                        contentDescription = room.name,
                        modifier = Modifier.fillMaxSize(),
                        contentScale = ContentScale.Crop
                    )
                } else if (room.coverUrl.isNotBlank() && (room.coverUrl.startsWith("http") || room.coverUrl.startsWith("data:"))) {
                    AsyncImage(
                        model = room.coverUrl,
                        contentDescription = room.name,
                        modifier = Modifier.fillMaxSize(),
                        contentScale = ContentScale.Crop
                    )
                } else {
                    val defaultRes = AvatarHelper.getDefaultMascotRes(userId = room.hostUserId, gender = "Male")
                    androidx.compose.foundation.Image(
                        painter = androidx.compose.ui.res.painterResource(id = defaultRes),
                        contentDescription = room.name,
                        modifier = Modifier.fillMaxSize(),
                        contentScale = ContentScale.Crop
                    )
                }

                // Dark gradient overlay for badge readability
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .background(
                            Brush.verticalGradient(
                                listOf(Color.Transparent, Color(0x55000000), Color(0xCC000000))
                            )
                        )
                )

                // Animated Beat Lines on the reduced circle when speaking or music is playing
                if (isAudioActive) {
                    val beatColor = if (isMusicPlaying) Color(0xFFFFD600) else Color(0xFF00E676)
                    Row(
                        modifier = Modifier
                            .align(Alignment.Center)
                            .clip(RoundedCornerShape(8.dp))
                            .background(Color(0xAA000000))
                            .padding(horizontal = 4.dp, vertical = 2.dp),
                        horizontalArrangement = Arrangement.spacedBy(2.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        listOf(b1, b2, b3, b4, b5).forEach { ratio ->
                            Box(
                                modifier = Modifier
                                    .width(2.5.dp)
                                    .height((ratio * 16).dp.coerceAtLeast(3.dp))
                                    .clip(RoundedCornerShape(1.dp))
                                    .background(beatColor)
                            )
                        }
                    }
                }

                // Status Badge in the middle-bottom
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier
                        .align(Alignment.BottomCenter)
                        .padding(bottom = 3.dp)
                ) {
                    if (isMusicPlaying) {
                        Text(
                            text = "🎵",
                            fontSize = 11.sp
                        )
                    } else if (isSeated) {
                        if (isMicMuted) {
                            Icon(
                                imageVector = Icons.Default.MicOff,
                                contentDescription = "Muted",
                                tint = Color(0xFFFF5252),
                                modifier = Modifier.size(12.dp)
                            )
                        } else {
                            Icon(
                                imageVector = Icons.Default.Mic,
                                contentDescription = "Speaking",
                                tint = if (isSpeaking) Color(0xFF00E676) else Color.White,
                                modifier = Modifier.size(12.dp)
                            )
                        }
                    } else {
                        Icon(
                            imageVector = Icons.Default.VolumeUp,
                            contentDescription = "Listening",
                            tint = QivoYellow,
                            modifier = Modifier.size(12.dp)
                        )
                    }
                }
            }
        }

        // Close "X" Quick Exit Badge at Top-End
        Box(
            modifier = Modifier
                .align(Alignment.TopEnd)
                .size(20.dp)
                .clip(CircleShape)
                .background(Color(0xEE1E1C2A))
                .border(1.dp, Color.White.copy(alpha = 0.4f), CircleShape)
                .clickable {
                    PartyRoomSessionManager.leaveRoom(currentUserId, scope)
                },
            contentAlignment = Alignment.Center
        ) {
            Icon(
                imageVector = Icons.Default.Close,
                contentDescription = "Leave Party Room",
                tint = Color.White,
                modifier = Modifier.size(12.dp)
            )
        }
    }
}

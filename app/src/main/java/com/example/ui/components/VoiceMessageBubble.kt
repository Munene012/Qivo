package com.example.ui.components

import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Mic
import androidx.compose.material.icons.filled.Pause
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material3.Icon
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.example.data.VoiceMessageManager

/**
 * High-craft Voice Message Player Bubble with animated waveform and play/pause controls.
 */
@Composable
fun VoiceMessageBubble(
    audioUrl: String,
    durationSeconds: Int = 0,
    isMe: Boolean,
    isDark: Boolean,
    modifier: Modifier = Modifier
) {
    val playbackState by VoiceMessageManager.playbackState.collectAsStateWithLifecycle()
    val isThisPlaying = playbackState.isPlaying && playbackState.activeUrlOrPath == audioUrl
    val isThisActive = playbackState.activeUrlOrPath == audioUrl

    val progress = if (isThisActive) playbackState.progress else 0f
    val currentSec = if (isThisActive) playbackState.currentPositionMs / 1000 else 0
    val totalSec = if (durationSeconds > 0) durationSeconds else if (isThisActive && playbackState.totalDurationMs > 0) playbackState.totalDurationMs / 1000 else 0

    val primaryColor = if (isMe) {
        if (isDark) Color(0xFF00E676) else Color(0xFF00C853)
    } else {
        if (isDark) Color(0xFF69F0AE) else Color(0xFF007E33)
    }

    val bubbleBg = if (isMe) {
        if (isDark) Color(0xFF102416) else Color(0xFFEDF8F1)
    } else {
        if (isDark) Color(0xFF142017) else Color(0xFFF0F6F2)
    }

    val infiniteTransition = rememberInfiniteTransition(label = "wave_anim")
    val pulseAnim by infiniteTransition.animateFloat(
        initialValue = 0.6f,
        targetValue = 1.0f,
        animationSpec = infiniteRepeatable(
            animation = tween(600, easing = FastOutSlowInEasing),
            repeatMode = RepeatMode.Reverse
        ),
        label = "pulse"
    )

    Surface(
        shape = RoundedCornerShape(
            topStart = 16.dp,
            topEnd = 16.dp,
            bottomStart = if (isMe) 16.dp else 4.dp,
            bottomEnd = if (isMe) 4.dp else 16.dp
        ),
        color = bubbleBg,
        border = androidx.compose.foundation.BorderStroke(
            1.dp,
            if (isMe) (if (isDark) Color(0xFF388E3C).copy(alpha = 0.6f) else Color(0xFFA5D6A7))
            else (if (isDark) Color(0xFFBA68C8).copy(alpha = 0.4f) else Color(0xFFE1BEE7))
        ),
        shadowElevation = 1.5.dp,
        modifier = modifier.widthIn(min = 190.dp, max = 270.dp)
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 10.dp, vertical = 8.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            // Play / Pause Circle Button
            Box(
                modifier = Modifier
                    .size(38.dp)
                    .clip(CircleShape)
                    .background(
                        Brush.linearGradient(
                            if (isMe) listOf(Color(0xFF007E33), Color(0xFF00C853))
                            else listOf(Color(0xFF004D20), Color(0xFF009639))
                        )
                    )
                    .clickable(
                        interactionSource = remember { MutableInteractionSource() },
                        indication = null
                    ) {
                        VoiceMessageManager.togglePlay(audioUrl)
                    },
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    imageVector = if (isThisPlaying) Icons.Default.Pause else Icons.Default.PlayArrow,
                    contentDescription = if (isThisPlaying) "Pause" else "Play",
                    tint = Color.White,
                    modifier = Modifier.size(22.dp)
                )
            }

            Spacer(modifier = Modifier.width(10.dp))

            // Waveform & Timestamps
            Column(
                modifier = Modifier.weight(1f)
            ) {
                // Waveform Bars (20 bars with dynamic heights)
                val barHeights = remember {
                    listOf(
                        0.35f, 0.65f, 0.90f, 0.45f, 0.80f, 1.00f, 0.55f, 0.70f, 0.95f, 0.40f,
                        0.85f, 0.60f, 0.75f, 0.30f, 0.90f, 0.50f, 0.70f, 0.85f, 0.40f, 0.60f
                    )
                }

                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(24.dp)
                        .clickable {
                            // Quick tap on waveform toggles playback
                            VoiceMessageManager.togglePlay(audioUrl)
                        },
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    barHeights.forEachIndexed { index, baseHeight ->
                        val barProgress = index.toFloat() / barHeights.size.toFloat()
                        val isPlayed = barProgress <= progress
                        val dynamicScale = if (isThisPlaying && isPlayed) {
                            baseHeight * pulseAnim
                        } else {
                            baseHeight
                        }

                        Box(
                            modifier = Modifier
                                .weight(1f)
                                .padding(horizontal = 1.dp)
                                .fillMaxHeight(dynamicScale.coerceIn(0.2f, 1.0f))
                                .clip(RoundedCornerShape(2.dp))
                                .background(
                                    if (isPlayed) primaryColor
                                    else primaryColor.copy(alpha = if (isDark) 0.30f else 0.25f)
                                )
                        )
                    }
                }

                Spacer(modifier = Modifier.height(4.dp))

                // Time Duration & Voice Icon
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = if (isThisActive && currentSec > 0) {
                            "${VoiceMessageManager.formatDuration(currentSec)} / ${VoiceMessageManager.formatDuration(totalSec)}"
                        } else {
                            VoiceMessageManager.formatDuration(totalSec.coerceAtLeast(1))
                        },
                        fontSize = 11.sp,
                        fontWeight = FontWeight.Medium,
                        color = if (isDark) Color(0xFFB0B0C0) else Color(0xFF616161)
                    )

                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(
                            imageVector = Icons.Default.Mic,
                            contentDescription = null,
                            tint = primaryColor.copy(alpha = 0.7f),
                            modifier = Modifier.size(12.dp)
                        )
                        Spacer(modifier = Modifier.width(2.dp))
                        Text(
                            text = "Voice",
                            fontSize = 10.sp,
                            fontWeight = FontWeight.Bold,
                            color = primaryColor.copy(alpha = 0.8f)
                        )
                    }
                }
            }
        }
    }
}

package com.example.ui.components

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Send
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.data.VoiceMessageManager
import com.example.data.VoiceRecordingState

/**
 * High-craft dynamic bottom bar displayed while recording a voice note.
 */
@Composable
fun VoiceRecordingBar(
    state: VoiceRecordingState,
    isDark: Boolean,
    onCancel: () -> Unit,
    onSend: () -> Unit,
    modifier: Modifier = Modifier
) {
    val infiniteTransition = rememberInfiniteTransition(label = "rec_pulse")
    val pulseAlpha by infiniteTransition.animateFloat(
        initialValue = 0.3f,
        targetValue = 1.0f,
        animationSpec = infiniteRepeatable(
            animation = tween(500, easing = FastOutSlowInEasing),
            repeatMode = RepeatMode.Reverse
        ),
        label = "pulse_alpha"
    )

    Surface(
        modifier = modifier
            .fillMaxWidth()
            .height(56.dp),
        shape = RoundedCornerShape(28.dp),
        color = if (isDark) Color(0xFF281318) else Color(0xFFFFEBEE),
        border = androidx.compose.foundation.BorderStroke(
            1.5.dp,
            if (isDark) Color(0xFFE53935).copy(alpha = 0.7f) else Color(0xFFFFCDD2)
        ),
        shadowElevation = 4.dp
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 12.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            // Cancel / Delete Button
            IconButton(
                onClick = onCancel,
                modifier = Modifier.size(36.dp)
            ) {
                Icon(
                    imageVector = Icons.Default.Delete,
                    contentDescription = "Cancel Recording",
                    tint = if (isDark) Color(0xFFEF5350) else Color(0xFFD32F2F),
                    modifier = Modifier.size(22.dp)
                )
            }

            Spacer(modifier = Modifier.width(6.dp))

            // Glowing Red Live Record Indicator
            Box(
                modifier = Modifier
                    .size(12.dp)
                    .clip(CircleShape)
                    .background(Color(0xFFE53935).copy(alpha = pulseAlpha))
            )

            Spacer(modifier = Modifier.width(8.dp))

            // Duration text (e.g. 00:15 / 03:00)
            Text(
                text = "${VoiceMessageManager.formatDuration(state.durationSeconds)} / 03:00",
                fontSize = 13.sp,
                fontWeight = FontWeight.Bold,
                color = if (isDark) Color(0xFFFFCDD2) else Color(0xFFB71C1C)
            )

            Spacer(modifier = Modifier.width(10.dp))

            // Live Waveform Visualizer based on microphone amplitude
            val normalizedAmp = (state.amplitude / 32767f).coerceIn(0.15f, 1f)
            Row(
                modifier = Modifier
                    .weight(1f)
                    .height(20.dp),
                horizontalArrangement = Arrangement.spacedBy(2.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                val bars = remember { listOf(0.3f, 0.6f, 0.9f, 0.4f, 0.7f, 1.0f, 0.5f, 0.8f, 0.3f, 0.6f, 0.9f, 0.5f) }
                bars.forEachIndexed { i, barBase ->
                    val dynamicH = (barBase * normalizedAmp * (1f + (i % 3) * 0.2f)).coerceIn(0.2f, 1f)
                    Box(
                        modifier = Modifier
                            .weight(1f)
                            .fillMaxHeight(dynamicH)
                            .clip(RoundedCornerShape(2.dp))
                            .background(if (isDark) Color(0xFFE53935) else Color(0xFFD32F2F))
                    )
                }
            }

            Spacer(modifier = Modifier.width(10.dp))

            // Send Button
            Box(
                modifier = Modifier
                    .size(40.dp)
                    .clip(CircleShape)
                    .background(Color(0xFFE53935))
                    .clickable { onSend() },
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    imageVector = Icons.Default.Send,
                    contentDescription = "Send Voice Message",
                    tint = Color.White,
                    modifier = Modifier.size(20.dp)
                )
            }
        }
    }
}

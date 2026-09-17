package com.example.ui.components

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.scaleOut
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Star
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.scale
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import kotlinx.coroutines.delay

/**
 * Premium Full-Screen Sent Gift Preview Overlay
 * Displays the custom 3D gift in full celebratory glory when sent in Conversation or Party Room.
 */
@Composable
fun SentGiftPreviewOverlay(
    gift: AppGift,
    recipientName: String,
    senderName: String = "You",
    onDismiss: () -> Unit
) {
    LaunchedEffect(gift) {
        HapticSoundFeedback.playGiftSentSound()
        // Auto-dismiss after 3.2 seconds
        delay(3200)
        onDismiss()
    }

    val infiniteTransition = rememberInfiniteTransition(label = "gift_sparkle")
    val rotation by infiniteTransition.animateFloat(
        initialValue = 0f,
        targetValue = 360f,
        animationSpec = infiniteRepeatable(
            animation = tween(12000, easing = LinearEasing),
            repeatMode = RepeatMode.Restart
        ),
        label = "rotation"
    )
    val pulseScale by infiniteTransition.animateFloat(
        initialValue = 0.96f,
        targetValue = 1.05f,
        animationSpec = infiniteRepeatable(
            animation = tween(900, easing = FastOutSlowInEasing),
            repeatMode = RepeatMode.Reverse
        ),
        label = "pulse"
    )

    val scaleAnim = remember { Animatable(0.2f) }
    LaunchedEffect(Unit) {
        scaleAnim.animateTo(
            targetValue = 1f,
            animationSpec = spring(dampingRatio = 0.65f, stiffness = 400f)
        )
    }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(Color(0xCC000000))
            .clickable(
                interactionSource = remember { MutableInteractionSource() },
                indication = null,
                onClick = onDismiss
            ),
        contentAlignment = Alignment.Center
    ) {
        // Rotating Aurora Sunburst rays behind the 3D gift
        Canvas(
            modifier = Modifier
                .size(280.dp)
                .graphicsLayer(rotationZ = rotation)
        ) {
            val cx = size.width / 2f
            val cy = size.height / 2f
            val rayBrush = Brush.radialGradient(
                colors = listOf(
                    Color(0x88FFD700),
                    Color(0x55FF4081),
                    Color(0x2200E5FF),
                    Color.Transparent
                )
            )
            drawCircle(brush = rayBrush, radius = size.width / 2f, center = androidx.compose.ui.geometry.Offset(cx, cy))
        }

        // Central Gift Card Content
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            modifier = Modifier
                .padding(horizontal = 28.dp)
                .scale(scaleAnim.value)
        ) {
            // "GIFT SENT" Top Pill
            Surface(
                shape = RoundedCornerShape(20.dp),
                color = Color(0x33FFFFFF),
                border = androidx.compose.foundation.BorderStroke(1.dp, Color(0x66FFD54F))
            ) {
                Row(
                    modifier = Modifier.padding(horizontal = 16.dp, vertical = 6.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = "✨  GIFT SENT  ✨",
                        fontSize = 12.sp,
                        fontWeight = FontWeight.ExtraBold,
                        letterSpacing = 1.5.sp,
                        color = Color(0xFFFFD54F)
                    )
                }
            }

            Spacer(modifier = Modifier.height(20.dp))

            // The Custom 3D Gift Icon (in large 110.dp size!)
            Box(
                modifier = Modifier
                    .size(130.dp)
                    .scale(pulseScale),
                contentAlignment = Alignment.Center
            ) {
                // Outer glowing halo ring
                Box(
                    modifier = Modifier
                        .size(126.dp)
                        .clip(CircleShape)
                        .background(
                            Brush.radialGradient(
                                listOf(
                                    Color(0x66FFD54F),
                                    Color(0x33FF4081),
                                    Color.Transparent
                                )
                            )
                        )
                )
                // The actual custom 3D Gift
                Gift3DIcon(
                    giftId = gift.id,
                    emoji = gift.emoji,
                    size = 100.dp
                )
            }

            Spacer(modifier = Modifier.height(16.dp))

            // Gift Name in bold luminous typography
            Text(
                text = gift.name,
                fontSize = 24.sp,
                fontWeight = FontWeight.Black,
                color = Color.White,
                textAlign = TextAlign.Center
            )

            Spacer(modifier = Modifier.height(6.dp))

            // Coins Badge
            Surface(
                shape = RoundedCornerShape(16.dp),
                color = Color(0x33FFB300),
                border = androidx.compose.foundation.BorderStroke(1.dp, Color(0x88FFD54F))
            ) {
                Row(
                    modifier = Modifier.padding(horizontal = 14.dp, vertical = 5.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(text = "🪙", fontSize = 13.sp)
                    Spacer(modifier = Modifier.width(6.dp))
                    Text(
                        text = "${String.format("%,d", gift.coins)} Coins",
                        fontSize = 13.sp,
                        fontWeight = FontWeight.Bold,
                        color = Color(0xFFFFE082)
                    )
                }
            }

            Spacer(modifier = Modifier.height(12.dp))

            // Recipient notification text
            Text(
                text = "$senderName sent to $recipientName",
                fontSize = 14.sp,
                fontWeight = FontWeight.Medium,
                color = Color(0xDDFFFFFF),
                textAlign = TextAlign.Center
            )

            Spacer(modifier = Modifier.height(24.dp))

            Text(
                text = "Tap anywhere to close",
                fontSize = 11.sp,
                color = Color(0x88FFFFFF)
            )
        }
    }
}

/**
 * Interactive 3D Gift Detail Preview Dialog
 * Shown when user wants to inspect/preview a custom 3D gift in large resolution prior to sending.
 */
@Composable
fun GiftDetailPreviewDialog(
    gift: AppGift,
    currentCoins: Long,
    onDismiss: () -> Unit,
    onSend: (AppGift) -> Unit
) {
    val canAfford = currentCoins >= gift.coins

    Dialog(
        onDismissRequest = onDismiss,
        properties = DialogProperties(usePlatformDefaultWidth = false)
    ) {
        Surface(
            modifier = Modifier
                .fillMaxWidth(0.88f)
                .padding(16.dp),
            shape = RoundedCornerShape(28.dp),
            color = Color(0xFF1E182A),
            border = androidx.compose.foundation.BorderStroke(
                1.5.dp,
                Brush.linearGradient(listOf(Color(0xFFFFD54F), Color(0xFF7FFF00), Color(0xFF5AB800)))
            ),
            shadowElevation = 16.dp
        ) {
            Column(
                modifier = Modifier.padding(24.dp),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                // Header with close button
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = "3D Gift Preview",
                        fontSize = 16.sp,
                        fontWeight = FontWeight.Bold,
                        color = Color(0xFFFFD54F)
                    )
                    IconButton(
                        onClick = onDismiss,
                        modifier = Modifier.size(32.dp)
                    ) {
                        Icon(Icons.Default.Close, contentDescription = "Close", tint = Color.White)
                    }
                }

                Spacer(modifier = Modifier.height(16.dp))

                // Big 3D Gift Icon Preview Container
                Box(
                    modifier = Modifier
                        .size(140.dp)
                        .clip(RoundedCornerShape(24.dp))
                        .background(
                            Brush.radialGradient(
                                listOf(
                                    Color(0xFF3B2455),
                                    Color(0xFF171322)
                                )
                            )
                        ),
                    contentAlignment = Alignment.Center
                ) {
                    Gift3DIcon(
                        giftId = gift.id,
                        emoji = gift.emoji,
                        size = 96.dp
                    )
                }

                Spacer(modifier = Modifier.height(16.dp))

                Text(
                    text = gift.name,
                    fontSize = 20.sp,
                    fontWeight = FontWeight.Bold,
                    color = Color.White
                )

                Spacer(modifier = Modifier.height(6.dp))

                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(text = "🪙", fontSize = 14.sp)
                    Spacer(modifier = Modifier.width(6.dp))
                    Text(
                        text = "${String.format("%,d", gift.coins)} coins",
                        fontSize = 14.sp,
                        fontWeight = FontWeight.SemiBold,
                        color = Color(0xFFFFD54F)
                    )
                    if (gift.badge != null) {
                        Spacer(modifier = Modifier.width(10.dp))
                        Surface(
                            shape = RoundedCornerShape(8.dp),
                            color = Color(0xFFFF3D00).copy(alpha = 0.2f),
                            border = androidx.compose.foundation.BorderStroke(1.dp, Color(0xFFFF3D00))
                        ) {
                            Text(
                                text = gift.badge,
                                fontSize = 10.sp,
                                fontWeight = FontWeight.Bold,
                                color = Color(0xFFFF6E40),
                                modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                            )
                        }
                    }
                }

                Spacer(modifier = Modifier.height(20.dp))

                // Action Button
                Surface(
                    onClick = {
                        onDismiss()
                        onSend(gift)
                    },
                    shape = RoundedCornerShape(14.dp),
                    color = if (canAfford) Color(0xFFFF3D00) else Color(0xFF616161),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Row(
                        modifier = Modifier.padding(vertical = 12.dp),
                        horizontalArrangement = Arrangement.Center,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            text = if (canAfford) "Send This Gift 🚀" else "Recharge to Send",
                            fontSize = 14.sp,
                            fontWeight = FontWeight.Bold,
                            color = Color.White
                        )
                    }
                }
            }
        }
    }
}

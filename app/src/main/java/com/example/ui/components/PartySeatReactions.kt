package com.example.ui.components

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.scaleOut
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.draw.scale
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.drawscope.Fill
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.ui.theme.AppTheme
import com.example.ui.theme.QivoOrange
import com.example.ui.theme.QivoYellow
import kotlin.math.cos
import kotlin.math.sin

/**
 * Supported expressive animated reaction types
 */
enum class SeatReactionType {
    LAUGH,          // 🤣 Bouncing laugh + tear drops
    CRY,            // 😭 Streaming fountain tears + sorrow tremor
    KISS_LEFT,      // 💋 Kiss drifting leftwards with floating hearts
    KISS_RIGHT,     // 💋 Kiss drifting rightwards with floating hearts
    LOVE,           // 😍 Heart eyes + pulsing orbiting hearts
    FIRE,           // 🔥 Rising flickering flames + heat embers
    CLAP,           // 👏 Clapping hands with acoustic ripples
    PARTY,          // 🎉 Exploding colorful confetti bursts
    SHOCK,          // 🤯 Mind-blown shockwave burst
    COOL,           // 😎 Cool sway with star glimmer flashes
    ANGRY,          // 😡 Furious tremor with steam puffs
    WINK            // 😉 Playful tilt + eye star twinkle
}

data class SeatReaction(
    val id: String,
    val name: String,
    val emoji: String,
    val type: SeatReactionType,
    val description: String
)

/**
 * 12 Expressive Animated Seat Reactions
 */
val ALL_SEAT_REACTIONS = listOf(
    SeatReaction("react_laugh", "Laughing", "🤣", SeatReactionType.LAUGH, "Bouncing laugh with tears"),
    SeatReaction("react_cry", "Crying", "😭", SeatReactionType.CRY, "Streaming blue tears"),
    SeatReaction("react_kiss_left", "Kiss Left", "😘", SeatReactionType.KISS_LEFT, "Blow kiss to the left"),
    SeatReaction("react_kiss_right", "Kiss Right", "😚", SeatReactionType.KISS_RIGHT, "Blow kiss to the right"),
    SeatReaction("react_love", "In Love", "😍", SeatReactionType.LOVE, "Pulsing heart eyes"),
    SeatReaction("react_fire", "Lit / Fire", "🔥", SeatReactionType.FIRE, "Blazing flame aura"),
    SeatReaction("react_clap", "Applause", "👏", SeatReactionType.CLAP, "Clapping rhythm"),
    SeatReaction("react_party", "Celebration", "🎉", SeatReactionType.PARTY, "Confetti explosion"),
    SeatReaction("react_shock", "Shocked", "🤯", SeatReactionType.SHOCK, "Mind blown halo"),
    SeatReaction("react_cool", "Cool Shades", "😎", SeatReactionType.COOL, "Gleaming star glimmers"),
    SeatReaction("react_angry", "Furious", "😡", SeatReactionType.ANGRY, "Trembling steam puff"),
    SeatReaction("react_wink", "Cute Wink", "😉", SeatReactionType.WINK, "Playful star twinkle")
)

/**
 * Animated Reaction Overlay for Seat Avatars.
 * Kept completely STILL in position and scale (no zooming in/out or floating bobbing),
 * while the inner 3D round face animates with lifelike expressions!
 */
@Composable
fun SeatAnimatedReactionOverlay(
    reactionType: SeatReactionType?,
    modifier: Modifier = Modifier,
    size: Dp = 70.dp
) {
    if (reactionType == null) return

    Box(
        modifier = modifier.size(size),
        contentAlignment = Alignment.Center
    ) {
        CustomReactionGraphic(
            reactionType = reactionType,
            size = size,
            animated = true
        )
    }
}

/**
 * High-Craft Bottom Sheet for Selecting Seat Reactions (Available strictly to Seated Users)
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun PartyReactionBottomSheet(
    onReactionSelected: (SeatReaction) -> Unit,
    onDismiss: () -> Unit
) {
    val colors = AppTheme.colors
    val isDark = colors.isDark
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = sheetState,
        containerColor = if (isDark) Color(0xFF14141E) else Color.White,
        dragHandle = {
            Box(
                modifier = Modifier
                    .padding(top = 10.dp, bottom = 6.dp)
                    .width(40.dp)
                    .height(4.dp)
                    .clip(RoundedCornerShape(2.dp))
                    .background(if (isDark) Color(0xFF3E3E4E) else Color(0xFFD0D0D8))
            )
        },
        shape = RoundedCornerShape(topStart = 26.dp, topEnd = 26.dp),
        modifier = Modifier.testTag("party_reaction_bottom_sheet")
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp, vertical = 12.dp)
        ) {
            // Header
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        text = "🎭 Live Seat Reactions",
                        fontSize = 18.sp,
                        fontWeight = FontWeight.Black,
                        color = colors.textPrimary
                    )
                    Spacer(modifier = Modifier.width(6.dp))
                    Surface(
                        shape = RoundedCornerShape(8.dp),
                        color = QivoOrange
                    ) {
                        Text(
                            text = "SEAT ONLY",
                            fontSize = 9.sp,
                            fontWeight = FontWeight.Bold,
                            color = Color.White,
                            modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                        )
                    }
                }

                IconButton(
                    onClick = onDismiss,
                    modifier = Modifier.size(32.dp)
                ) {
                    Icon(Icons.Default.Close, contentDescription = "Close", tint = colors.textSecondary)
                }
            }

            Text(
                text = "Tap a live animated reaction to broadcast it directly on your seat avatar!",
                fontSize = 12.sp,
                color = colors.textSecondary,
                modifier = Modifier.padding(top = 4.dp, bottom = 12.dp)
            )

            // Grid of 12 Reactions (4 columns)
            LazyVerticalGrid(
                columns = GridCells.Fixed(4),
                modifier = Modifier
                    .fillMaxWidth()
                    .height(260.dp),
                contentPadding = PaddingValues(bottom = 16.dp),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                items(ALL_SEAT_REACTIONS, key = { it.id }) { reaction ->
                    Surface(
                        shape = RoundedCornerShape(16.dp),
                        color = if (isDark) Color(0xFF1F1F2C) else Color(0xFFF3F4F6),
                        border = BorderStroke(1.dp, if (isDark) Color(0xFF2E2E3E) else Color(0xFFE5E7EB)),
                        modifier = Modifier
                            .clip(RoundedCornerShape(16.dp))
                            .clickable {
                                onReactionSelected(reaction)
                                onDismiss()
                            }
                            .testTag("reaction_item_${reaction.id}")
                    ) {
                        Column(
                            modifier = Modifier.padding(vertical = 10.dp, horizontal = 4.dp),
                            horizontalAlignment = Alignment.CenterHorizontally,
                            verticalArrangement = Arrangement.Center
                        ) {
                            CustomReactionGraphic(
                                reactionType = reaction.type,
                                size = 36.dp,
                                animated = false
                            )
                            Spacer(modifier = Modifier.height(6.dp))
                            Text(
                                text = reaction.name,
                                fontSize = 11.sp,
                                fontWeight = FontWeight.Bold,
                                color = colors.textPrimary,
                                textAlign = TextAlign.Center,
                                maxLines = 1
                            )
                        }
                    }
                }
            }
        }
    }
}

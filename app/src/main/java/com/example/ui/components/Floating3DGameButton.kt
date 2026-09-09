package com.example.ui.components

import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.scale
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlin.math.roundToInt

/**
 * Custom 3D Floating Action Button for Game Center with physics wobble, breathing glow, and dragging.
 */
@Composable
fun Floating3DGameButton(
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    val configuration = LocalConfiguration.current
    val density = LocalDensity.current

    val screenWidthPx = with(density) { configuration.screenWidthDp.dp.toPx() }
    val screenHeightPx = with(density) { configuration.screenHeightDp.dp.toPx() }

    val buttonWidthPx = with(density) { 68.dp.toPx() }
    val buttonHeightPx = with(density) { 68.dp.toPx() }
    val bottomNavHeightPx = with(density) { 90.dp.toPx() }
    val topHeaderPaddingPx = with(density) { 100.dp.toPx() }

    val minOffsetX = with(density) { (-configuration.screenWidthDp + 80).dp.toPx() }
    val maxOffsetX = 0f
    val minOffsetY = -(screenHeightPx - bottomNavHeightPx - topHeaderPaddingPx - buttonHeightPx)
    val maxOffsetY = 0f

    var fabOffsetX by remember { mutableFloatStateOf(0f) }
    var fabOffsetY by remember { mutableFloatStateOf(0f) }

    val infiniteTransition = rememberInfiniteTransition(label = "fab_float")
    val floatOffset by infiniteTransition.animateFloat(
        initialValue = -3.5f,
        targetValue = 3.5f,
        animationSpec = infiniteRepeatable(
            animation = tween(1800, easing = FastOutSlowInEasing),
            repeatMode = RepeatMode.Reverse
        ),
        label = "float"
    )
    val pulseScale by infiniteTransition.animateFloat(
        initialValue = 0.98f,
        targetValue = 1.04f,
        animationSpec = infiniteRepeatable(
            animation = tween(1200, easing = FastOutSlowInEasing),
            repeatMode = RepeatMode.Reverse
        ),
        label = "pulse"
    )

    Box(
        modifier = modifier
            .offset { IntOffset(fabOffsetX.roundToInt(), (fabOffsetY + floatOffset).roundToInt()) }
            .pointerInput(Unit) {
                detectDragGestures { change, dragAmount ->
                    change.consume()
                    fabOffsetX = (fabOffsetX + dragAmount.x).coerceIn(minOffsetX, maxOffsetX)
                    fabOffsetY = (fabOffsetY + dragAmount.y).coerceIn(minOffsetY, maxOffsetY)
                }
            }
            .testTag("floating_game_button")
    ) {
        // Glowing 3D Orb Surface
        Surface(
            onClick = onClick,
            shape = CircleShape,
            color = Color.Transparent,
            shadowElevation = 8.dp,
            modifier = Modifier
                .size(66.dp)
                .scale(pulseScale)
        ) {
            Box(
                modifier = Modifier
                    .background(
                        Brush.radialGradient(
                            colors = listOf(
                                Color(0xFFFFD54F),
                                Color(0xFFFF8F00),
                                Color(0xFFE65100),
                                Color(0xFFBF360C)
                            )
                        ),
                        shape = CircleShape
                    )
                    .border(
                        2.dp,
                        Brush.linearGradient(
                            listOf(Color(0xFFFFF9C4), Color(0xFFFFB300), Color(0xFFFF6F00))
                        ),
                        CircleShape
                    )
                    .clip(CircleShape),
                contentAlignment = Alignment.Center
            ) {
                Column(
                    horizontalAlignment = Alignment.CenterHorizontally,
                    modifier = Modifier.padding(bottom = 2.dp)
                ) {
                    GameCenter3DIcon(size = 42.dp)
                    Text(
                        text = "GAMES",
                        color = Color.White,
                        fontSize = 9.sp,
                        fontWeight = FontWeight.Black,
                        letterSpacing = 0.5.sp,
                        modifier = Modifier.offset(y = (-4).dp)
                    )
                }
            }
        }
    }
}

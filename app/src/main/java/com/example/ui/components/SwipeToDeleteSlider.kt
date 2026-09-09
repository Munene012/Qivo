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
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.gestures.Orientation
import androidx.compose.foundation.gestures.draggable
import androidx.compose.foundation.gestures.rememberDraggableState
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ChevronRight
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.DeleteForever
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlinx.coroutines.launch
import kotlin.math.roundToInt

/**
 * Interactive Swipe-to-Delete slider.
 * Eliminates accidental deletions and prevents squeezing buttons onto cramped toolbars.
 */
@Composable
fun SwipeToDeleteSlider(
    modifier: Modifier = Modifier,
    promptText: String = "Slide to permanently delete room",
    isDeleting: Boolean = false,
    onDeleteConfirmed: () -> Unit
) {
    val coroutineScope = rememberCoroutineScope()
    val density = LocalDensity.current

    val thumbSize = 46.dp
    val thumbSizePx = with(density) { thumbSize.toPx() }

    val infiniteTransition = rememberInfiniteTransition(label = "shimmer")
    val alphaAnim by infiniteTransition.animateFloat(
        initialValue = 0.5f,
        targetValue = 1.0f,
        animationSpec = infiniteRepeatable(
            animation = tween(900, easing = LinearEasing),
            repeatMode = RepeatMode.Reverse
        ),
        label = "alpha"
    )

    BoxWithConstraints(
        modifier = modifier
            .fillMaxWidth()
            .height(54.dp)
            .clip(RoundedCornerShape(27.dp))
            .background(
                Brush.horizontalGradient(
                    listOf(
                        Color(0xFF2A0F14),
                        Color(0xFF3F141A),
                        Color(0xFF52151E)
                    )
                )
            )
            .border(
                1.5.dp,
                Brush.horizontalGradient(
                    listOf(
                        Color(0xFFFF5252).copy(alpha = 0.6f),
                        Color(0xFFFF1744).copy(alpha = 0.8f)
                    )
                ),
                RoundedCornerShape(27.dp)
            )
            .padding(4.dp)
            .testTag("swipe_to_delete_slider"),
        contentAlignment = Alignment.CenterStart
    ) {
        val maxDragWidthPx = constraints.maxWidth.toFloat() - thumbSizePx - with(density) { 8.dp.toPx() }
        val offsetX = remember { Animatable(0f) }

        // Dynamic progress fill track as user drags
        val currentProgress = if (maxDragWidthPx > 0) (offsetX.value / maxDragWidthPx).coerceIn(0f, 1f) else 0f

        // Colored progressive background track
        Box(
            modifier = Modifier
                .fillMaxHeight()
                .width(with(density) { (offsetX.value + thumbSizePx).toDp() })
                .clip(RoundedCornerShape(27.dp))
                .background(
                    Brush.horizontalGradient(
                        listOf(
                            Color(0x66FF1744),
                            Color(0xCCFF1744)
                        )
                    )
                )
        )

        // Center Hint Text
        Box(
            modifier = Modifier.fillMaxSize(),
            contentAlignment = Alignment.Center
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier.padding(start = 36.dp, end = 12.dp)
            ) {
                Text(
                    text = if (isDeleting) "Deleting room..." else promptText,
                    fontSize = 12.sp,
                    fontWeight = FontWeight.Bold,
                    color = Color.White.copy(alpha = if (isDeleting) 1f else alphaAnim),
                    maxLines = 1
                )
                Spacer(modifier = Modifier.width(4.dp))
                if (!isDeleting) {
                    Icon(
                        imageVector = Icons.Default.ChevronRight,
                        contentDescription = null,
                        tint = Color(0xFFFF5252).copy(alpha = alphaAnim),
                        modifier = Modifier.size(16.dp)
                    )
                }
            }
        }

        // Draggable Thumb Button
        Box(
            modifier = Modifier
                .offset { IntOffset(offsetX.value.roundToInt(), 0) }
                .size(thumbSize)
                .clip(CircleShape)
                .background(
                    Brush.linearGradient(
                        listOf(
                            Color(0xFFFF5252),
                            Color(0xFFD50000)
                        )
                    )
                )
                .border(1.dp, Color.White.copy(alpha = 0.5f), CircleShape)
                .draggable(
                    orientation = Orientation.Horizontal,
                    enabled = !isDeleting,
                    state = rememberDraggableState { delta ->
                        coroutineScope.launch {
                            val target = (offsetX.value + delta).coerceIn(0f, maxDragWidthPx)
                            offsetX.snapTo(target)
                        }
                    },
                    onDragStopped = {
                        if (maxDragWidthPx > 0 && offsetX.value >= maxDragWidthPx * 0.78f) {
                            coroutineScope.launch {
                                offsetX.animateTo(maxDragWidthPx, tween(150, easing = FastOutSlowInEasing))
                                onDeleteConfirmed()
                            }
                        } else {
                            coroutineScope.launch {
                                offsetX.animateTo(0f, tween(250, easing = FastOutSlowInEasing))
                            }
                        }
                    }
                ),
            contentAlignment = Alignment.Center
        ) {
            if (isDeleting) {
                CircularProgressIndicator(
                    color = Color.White,
                    strokeWidth = 2.dp,
                    modifier = Modifier.size(20.dp)
                )
            } else {
                Icon(
                    imageVector = if (currentProgress > 0.6f) Icons.Default.DeleteForever else Icons.Default.Delete,
                    contentDescription = "Swipe to Delete",
                    tint = Color.White,
                    modifier = Modifier.size(22.dp)
                )
            }
        }
    }
}

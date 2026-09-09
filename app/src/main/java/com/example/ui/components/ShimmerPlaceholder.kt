package com.example.ui.components

import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.composed
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.example.ui.theme.AppTheme

/**
 * Shimmer effect modifier for smooth skeleton loading screens.
 */
fun Modifier.shimmerBackground(
    shape: androidx.compose.ui.graphics.Shape = RoundedCornerShape(8.dp),
    isDark: Boolean = true
): Modifier = composed {
    val transition = rememberInfiniteTransition(label = "shimmer_transition")
    val translateAnim by transition.animateFloat(
        initialValue = 0f,
        targetValue = 1000f,
        animationSpec = infiniteRepeatable(
            animation = tween(durationMillis = 1200, easing = FastOutSlowInEasing),
            repeatMode = RepeatMode.Restart
        ),
        label = "shimmer_anim"
    )

    val shimmerColors = if (isDark) {
        listOf(
            Color(0xFF24242A),
            Color(0xFF383842),
            Color(0xFF24242A)
        )
    } else {
        listOf(
            Color(0xFFE5E5EA),
            Color(0xFFF2F2F7),
            Color(0xFFE5E5EA)
        )
    }

    val brush = Brush.linearGradient(
        colors = shimmerColors,
        start = Offset.Zero,
        end = Offset(x = translateAnim, y = translateAnim)
    )

    this
        .clip(shape)
        .background(brush)
}

/**
 * Reusable User Item Skeleton Placeholder
 */
@Composable
fun UserItemSkeleton(isDark: Boolean = true) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Box(
            modifier = Modifier
                .size(54.dp)
                .shimmerBackground(shape = CircleShape, isDark = isDark)
        )

        Spacer(modifier = Modifier.width(14.dp))

        Column(modifier = Modifier.weight(1f)) {
            Box(
                modifier = Modifier
                    .width(140.dp)
                    .height(16.dp)
                    .shimmerBackground(shape = RoundedCornerShape(4.dp), isDark = isDark)
            )

            Spacer(modifier = Modifier.height(8.dp))

            Box(
                modifier = Modifier
                    .width(90.dp)
                    .height(12.dp)
                    .shimmerBackground(shape = RoundedCornerShape(4.dp), isDark = isDark)
            )
        }

        Box(
            modifier = Modifier
                .size(width = 72.dp, height = 32.dp)
                .shimmerBackground(shape = RoundedCornerShape(16.dp), isDark = isDark)
        )
    }
}

/**
 * Reusable Party Room Card Skeleton Placeholder
 */
@Composable
fun PartyRoomCardSkeleton(isDark: Boolean = true) {
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .height(110.dp)
            .padding(horizontal = 16.dp, vertical = 6.dp)
            .shimmerBackground(shape = RoundedCornerShape(18.dp), isDark = isDark)
    )
}

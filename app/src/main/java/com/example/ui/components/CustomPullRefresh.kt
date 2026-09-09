package com.example.ui.components

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Icon
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.nestedscroll.NestedScrollConnection
import androidx.compose.ui.input.nestedscroll.NestedScrollSource
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Velocity
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.R
import kotlinx.coroutines.launch

/**
 * State and nested scroll controller for custom finger-following pull to refresh
 */
class CustomPullRefreshState(
    val refreshThresholdPx: Float,
    val maxDragPx: Float,
    val onRefresh: suspend () -> Unit
) {
    val pullOffset = Animatable(0f)
    var isRefreshing by mutableStateOf(false)
        internal set

    fun getNestedScrollConnection(coroutineScope: kotlinx.coroutines.CoroutineScope): NestedScrollConnection {
        return object : NestedScrollConnection {
            override fun onPreScroll(available: Offset, source: NestedScrollSource): Offset {
                // If currently pulled and user scrolls up, consume the scroll to pull back the indicator
                if (pullOffset.value > 0f && available.y < 0f) {
                    val newOffset = (pullOffset.value + available.y).coerceAtLeast(0f)
                    val consumed = newOffset - pullOffset.value
                    coroutineScope.launch {
                        pullOffset.snapTo(newOffset)
                    }
                    return Offset(0f, consumed)
                }
                return Offset.Zero
            }

            override fun onPostScroll(consumed: Offset, available: Offset, source: NestedScrollSource): Offset {
                // When scrolling down at top of list
                if (available.y > 0f && !isRefreshing) {
                    val dragFactor = 0.5f // Smooth dampening
                    val newOffset = (pullOffset.value + available.y * dragFactor).coerceAtMost(maxDragPx)
                    val deltaConsumed = (newOffset - pullOffset.value) / dragFactor
                    coroutineScope.launch {
                        pullOffset.snapTo(newOffset)
                    }
                    return Offset(0f, deltaConsumed)
                }
                return Offset.Zero
            }

            override suspend fun onPreFling(available: Velocity): Velocity {
                if (pullOffset.value > 0f) {
                    if (pullOffset.value >= refreshThresholdPx * 0.35f && !isRefreshing) {
                        triggerRefresh(coroutineScope)
                    } else if (!isRefreshing) {
                        coroutineScope.launch {
                            pullOffset.animateTo(0f, tween(180))
                        }
                    }
                    return available
                }
                return Velocity.Zero
            }

            override suspend fun onPostFling(consumed: Velocity, available: Velocity): Velocity {
                if (pullOffset.value > 0f && !isRefreshing) {
                    if (pullOffset.value >= refreshThresholdPx * 0.35f) {
                        triggerRefresh(coroutineScope)
                    } else {
                        coroutineScope.launch {
                            pullOffset.animateTo(0f, tween(180))
                        }
                    }
                }
                return super.onPostFling(consumed, available)
            }
        }
    }

    fun triggerRefresh(coroutineScope: kotlinx.coroutines.CoroutineScope) {
        if (isRefreshing) return
        isRefreshing = true
        coroutineScope.launch {
            try {
                pullOffset.animateTo(refreshThresholdPx, tween(150))
                kotlinx.coroutines.withTimeoutOrNull(3500L) {
                    onRefresh()
                }
            } catch (_: Exception) {
            } finally {
                pullOffset.animateTo(0f, tween(200))
                isRefreshing = false
            }
        }
    }

    fun endRefresh(coroutineScope: kotlinx.coroutines.CoroutineScope) {
        coroutineScope.launch {
            pullOffset.animateTo(0f, tween(180))
            isRefreshing = false
        }
    }
}

@Composable
fun rememberCustomPullRefreshState(
    onRefresh: suspend () -> Unit,
    refreshThreshold: Float = 32f,
    maxDrag: Float = 75f
): CustomPullRefreshState {
    val density = LocalDensity.current
    val thresholdPx = with(density) { refreshThreshold.dp.toPx() }
    val maxDragPx = with(density) { maxDrag.dp.toPx() }
    val state = remember(thresholdPx, maxDragPx) {
        CustomPullRefreshState(
            refreshThresholdPx = thresholdPx,
            maxDragPx = maxDragPx,
            onRefresh = onRefresh
        )
    }

    // Safety watchdog: ensure refresh animation never gets stuck if cancelled or delayed
    LaunchedEffect(state.isRefreshing) {
        if (state.isRefreshing) {
            kotlinx.coroutines.delay(4000L)
            if (state.isRefreshing) {
                state.pullOffset.animateTo(0f, tween(180))
                state.isRefreshing = false
            }
        }
    }

    return state
}

/**
 * Custom Refresh Indicator that moves smoothly with finger drag
 * and displays below the sticky header / navigation bar.
 */
@Composable
fun CustomRefreshHeaderItem(
    pullRefreshState: CustomPullRefreshState,
    modifier: Modifier = Modifier
) {
    val density = LocalDensity.current
    val currentHeightDp = with(density) { pullRefreshState.pullOffset.value.toDp() }

    if (currentHeightDp > 2.dp) {
        val pullFraction = (pullRefreshState.pullOffset.value / pullRefreshState.refreshThresholdPx).coerceIn(0f, 1f)

        val infiniteTransition = rememberInfiniteTransition(label = "refresh_spin")
        val continuousRotation by infiniteTransition.animateFloat(
            initialValue = 0f,
            targetValue = 360f,
            animationSpec = infiniteRepeatable(
                animation = tween(800, easing = LinearEasing),
                repeatMode = RepeatMode.Restart
            ),
            label = "spin_angle"
        )

        val rotationAngle = if (pullRefreshState.isRefreshing) continuousRotation else (pullFraction * 360f)

        Box(
            modifier = modifier
                .fillMaxWidth()
                .height(currentHeightDp)
                .background(Color.Transparent),
            contentAlignment = Alignment.Center
        ) {
            CustomAnimatedRefresh3DIcon(
                size = 28.dp,
                isRotating = pullRefreshState.isRefreshing || pullFraction > 0.1f
            )
        }
    }
}

/**
 * Universal App Loading Spinner matching the custom home pull-to-refresh spinner
 */
@Composable
fun AppLoadingSpinner(
    modifier: Modifier = Modifier,
    size: androidx.compose.ui.unit.Dp = 28.dp
) {
    CustomAnimatedRefresh3DIcon(
        modifier = modifier,
        size = size,
        isRotating = true
    )
}


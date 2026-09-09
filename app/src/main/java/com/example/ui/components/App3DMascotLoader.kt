package com.example.ui.components

import androidx.compose.animation.core.*
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.*
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.R

/**
 * Simple, elegant, lightweight custom spinner / loader.
 */
@Composable
fun App3DMascotLoader(
    modifier: Modifier = Modifier,
    size: Dp = 48.dp,
    message: String? = null,
    isDark: Boolean = true,
    mascotRes: Int = R.drawable.ic_doll_bear
) {
    val infiniteTransition = rememberInfiniteTransition(label = "simple_spinner_anim")
    val rotation by infiniteTransition.animateFloat(
        initialValue = 0f,
        targetValue = 360f,
        animationSpec = infiniteRepeatable(
            animation = tween(durationMillis = 850, easing = LinearEasing),
            repeatMode = RepeatMode.Restart
        ),
        label = "spinner_rotation"
    )

    Column(
        modifier = modifier,
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        Box(
            modifier = Modifier.size(size),
            contentAlignment = Alignment.Center
        ) {
            Canvas(modifier = Modifier.fillMaxSize()) {
                val strokeWidth = this.size.width * 0.10f
                val arcSize = Size(this.size.width - strokeWidth, this.size.height - strokeWidth)
                val topLeft = Offset(strokeWidth / 2, strokeWidth / 2)

                // Background track
                drawArc(
                    color = if (isDark) Color(0xFF2C2C34) else Color(0xFFE2E8F0),
                    startAngle = 0f,
                    sweepAngle = 360f,
                    useCenter = false,
                    topLeft = topLeft,
                    size = arcSize,
                    style = Stroke(width = strokeWidth, cap = StrokeCap.Round)
                )

                // Rotating active arc with smooth gradient
                drawArc(
                    brush = Brush.sweepGradient(
                        colors = listOf(
                            Color(0x00B3FF00),
                            Color(0xFFB3FF00),
                            Color(0xFFFFD600)
                        )
                    ),
                    startAngle = rotation,
                    sweepAngle = 260f,
                    useCenter = false,
                    topLeft = topLeft,
                    size = arcSize,
                    style = Stroke(width = strokeWidth, cap = StrokeCap.Round)
                )
            }
        }

        if (!message.isNullOrBlank()) {
            Spacer(modifier = Modifier.height(10.dp))
            Text(
                text = message,
                fontSize = 13.sp,
                fontWeight = FontWeight.Medium,
                color = if (isDark) Color(0xFFE2E8F0) else Color(0xFF475569)
            )
        }
    }
}

/**
 * Compact, lightweight inline spinner for buttons and action rows.
 */
@Composable
fun App3DInlineSpinner(
    modifier: Modifier = Modifier,
    size: Dp = 20.dp,
    mascotRes: Int = R.drawable.ic_doll_bear
) {
    val infiniteTransition = rememberInfiniteTransition(label = "simple_inline_spinner")
    val rotation by infiniteTransition.animateFloat(
        initialValue = 0f,
        targetValue = 360f,
        animationSpec = infiniteRepeatable(
            animation = tween(durationMillis = 750, easing = LinearEasing),
            repeatMode = RepeatMode.Restart
        ),
        label = "inline_rotation"
    )

    Canvas(modifier = modifier.size(size)) {
        val strokeWidth = this.size.width * 0.14f
        val arcSize = Size(this.size.width - strokeWidth, this.size.height - strokeWidth)
        val topLeft = Offset(strokeWidth / 2, strokeWidth / 2)

        // Background Track
        drawArc(
            color = Color(0x33B3FF00),
            startAngle = 0f,
            sweepAngle = 360f,
            useCenter = false,
            topLeft = topLeft,
            size = arcSize,
            style = Stroke(width = strokeWidth, cap = StrokeCap.Round)
        )

        // Active Arc
        drawArc(
            color = Color(0xFFB3FF00),
            startAngle = rotation,
            sweepAngle = 220f,
            useCenter = false,
            topLeft = topLeft,
            size = arcSize,
            style = Stroke(width = strokeWidth, cap = StrokeCap.Round)
        )
    }
}

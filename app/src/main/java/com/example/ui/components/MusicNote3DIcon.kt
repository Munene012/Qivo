package com.example.ui.components

import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.size
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Fill
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import kotlin.math.PI
import kotlin.math.sin

/**
 * 3D Claymorphic Vibrant Colored Music Note Icon
 * Features rich multi-color gradient fills, 3D spherical highlights, specular gloss, and drop shadows.
 *
 * @param noteType 0 = Neon Pink/Coral Beamed Notes, 1 = Cyan/Teal Flagged 8th Note,
 *                 2 = Golden Amber Quarter Note, 3 = Electric Violet Beamed Notes
 */
@Composable
fun MusicNote3DIcon(
    modifier: Modifier = Modifier,
    noteType: Int = 0,
    size: Dp = 20.dp
) {
    Box(
        modifier = modifier.size(size),
        contentAlignment = Alignment.Center
    ) {
        Canvas(modifier = Modifier.fillMaxSize()) {
            val w = this.size.width
            val h = this.size.height

            when (noteType % 4) {
                0 -> drawDoubleBeamedNote(
                    w, h,
                    gradientColors = listOf(Color(0xFFFF2A85), Color(0xFFFF77A9), Color(0xFFFF5252)),
                    shadowColor = Color(0x66FF1744)
                )
                1 -> drawSingleFlaggedNote(
                    w, h,
                    gradientColors = listOf(Color(0xFF00E5FF), Color(0xFF00B0FF), Color(0xFF00E676)),
                    shadowColor = Color(0x6600B0FF)
                )
                2 -> drawGoldenSparkleNote(
                    w, h,
                    gradientColors = listOf(Color(0xFFFFD600), Color(0xFFFF9100), Color(0xFFFF6D00)),
                    shadowColor = Color(0x66FF6D00)
                )
                else -> drawDoubleBeamedNote(
                    w, h,
                    gradientColors = listOf(Color(0xFFE040FB), Color(0xFF7C4DFF), Color(0xFF651FFF)),
                    shadowColor = Color(0x667C4DFF)
                )
            }
        }
    }
}

/**
 * Animated floating 3D music notes rising smoothly from avatars with physics wobble and scaling.
 */
@Composable
fun AnimatedFloatingMusicNotes(
    modifier: Modifier = Modifier
) {
    val infiniteTransition = rememberInfiniteTransition(label = "musicNotes3DTransition")
    val noteConfigs = listOf(
        NoteMotionConfig(noteType = 0, delayMs = 0, horizontalShift = -14f, baseScale = 0.85f),
        NoteMotionConfig(noteType = 1, delayMs = 380, horizontalShift = 12f, baseScale = 1.0f),
        NoteMotionConfig(noteType = 2, delayMs = 760, horizontalShift = -9f, baseScale = 0.9f),
        NoteMotionConfig(noteType = 3, delayMs = 1140, horizontalShift = 16f, baseScale = 1.1f)
    )

    Box(
        modifier = modifier,
        contentAlignment = Alignment.Center
    ) {
        noteConfigs.forEachIndexed { idx, config ->
            val progress by infiniteTransition.animateFloat(
                initialValue = 0f,
                targetValue = 1f,
                animationSpec = infiniteRepeatable(
                    animation = tween(durationMillis = 1600, delayMillis = config.delayMs, easing = LinearEasing),
                    repeatMode = RepeatMode.Restart
                ),
                label = "note3DProgress_$idx"
            )

            val yOffset = -progress * 46f
            val xOffset = config.horizontalShift * sin((progress * PI * 1.2).toDouble()).toFloat()
            val alpha = if (progress < 0.15f) {
                progress / 0.15f
            } else if (progress > 0.65f) {
                ((1f - progress) / 0.35f).coerceIn(0f, 1f)
            } else {
                1f
            }
            val scale = (config.baseScale * (0.75f + progress * 0.45f)).coerceIn(0.5f, 1.4f)

            Box(
                modifier = Modifier
                    .offset(x = xOffset.dp, y = yOffset.dp)
                    .graphicsLayer {
                        this.alpha = alpha.coerceIn(0f, 1f)
                        this.scaleX = scale
                        this.scaleY = scale
                        this.rotationZ = (sin((progress * PI * 2).toDouble()) * 15f).toFloat()
                    }
            ) {
                MusicNote3DIcon(
                    noteType = config.noteType,
                    size = 18.dp
                )
            }
        }
    }
}

private data class NoteMotionConfig(
    val noteType: Int,
    val delayMs: Int,
    val horizontalShift: Float,
    val baseScale: Float
)

/**
 * 3D Double Beamed Notes (♫) with glossy depth, rounded noteheads, and specular shine.
 */
private fun DrawScope.drawDoubleBeamedNote(
    w: Float,
    h: Float,
    gradientColors: List<Color>,
    shadowColor: Color
) {
    val brush = Brush.linearGradient(
        colors = gradientColors,
        start = Offset(0f, 0f),
        end = Offset(w, h)
    )

    // 1. Ambient Glow / Drop Shadow behind noteheads
    drawOval(
        color = shadowColor,
        topLeft = Offset(w * 0.08f, h * 0.66f),
        size = Size(w * 0.40f, h * 0.30f)
    )
    drawOval(
        color = shadowColor,
        topLeft = Offset(w * 0.54f, h * 0.54f),
        size = Size(w * 0.40f, h * 0.30f)
    )

    // 2. Connecting Top Beam (Angled 3D Bar)
    val beamPath = Path().apply {
        moveTo(w * 0.40f, h * 0.22f)
        lineTo(w * 0.88f, h * 0.10f)
        lineTo(w * 0.88f, h * 0.24f)
        lineTo(w * 0.40f, h * 0.36f)
        close()
    }
    drawPath(path = beamPath, brush = brush, style = Fill)

    // Specular Beam Highlight
    val beamHighlight = Path().apply {
        moveTo(w * 0.40f, h * 0.22f)
        lineTo(w * 0.88f, h * 0.10f)
        lineTo(w * 0.88f, h * 0.14f)
        lineTo(w * 0.40f, h * 0.26f)
        close()
    }
    drawPath(path = beamHighlight, color = Color.White.copy(alpha = 0.55f), style = Fill)

    // 3. Stems (Thick vertical glossy lines)
    val stemStroke = w * 0.09f
    drawLine(
        brush = brush,
        start = Offset(w * 0.40f, h * 0.72f),
        end = Offset(w * 0.40f, h * 0.22f),
        strokeWidth = stemStroke,
        cap = StrokeCap.Round
    )
    drawLine(
        brush = brush,
        start = Offset(w * 0.86f, h * 0.60f),
        end = Offset(w * 0.86f, h * 0.10f),
        strokeWidth = stemStroke,
        cap = StrokeCap.Round
    )

    // 4. Left Notehead (Tilted 3D Egg/Sphere)
    drawOval(
        brush = brush,
        topLeft = Offset(w * 0.08f, h * 0.60f),
        size = Size(w * 0.38f, h * 0.28f)
    )
    // Left Notehead 3D Spherical Specular Gloss
    drawOval(
        brush = Brush.radialGradient(
            colors = listOf(Color.White.copy(alpha = 0.85f), Color.Transparent),
            center = Offset(w * 0.20f, h * 0.66f),
            radius = w * 0.14f
        ),
        topLeft = Offset(w * 0.14f, h * 0.62f),
        size = Size(w * 0.18f, h * 0.14f)
    )

    // 5. Right Notehead (Tilted 3D Egg/Sphere)
    drawOval(
        brush = brush,
        topLeft = Offset(w * 0.54f, h * 0.48f),
        size = Size(w * 0.38f, h * 0.28f)
    )
    // Right Notehead 3D Spherical Specular Gloss
    drawOval(
        brush = Brush.radialGradient(
            colors = listOf(Color.White.copy(alpha = 0.85f), Color.Transparent),
            center = Offset(w * 0.66f, h * 0.54f),
            radius = w * 0.14f
        ),
        topLeft = Offset(w * 0.60f, h * 0.50f),
        size = Size(w * 0.18f, h * 0.14f)
    )
}

/**
 * 3D Single Flagged 8th Note (♪) with curved wavy flag, glossy stem, and spherical notehead.
 */
private fun DrawScope.drawSingleFlaggedNote(
    w: Float,
    h: Float,
    gradientColors: List<Color>,
    shadowColor: Color
) {
    val brush = Brush.linearGradient(
        colors = gradientColors,
        start = Offset(0f, 0f),
        end = Offset(w, h)
    )

    // 1. Ambient Glow
    drawOval(
        color = shadowColor,
        topLeft = Offset(w * 0.12f, h * 0.66f),
        size = Size(w * 0.46f, h * 0.32f)
    )

    // 2. Wavy Flag at Top Right
    val flagPath = Path().apply {
        moveTo(w * 0.62f, h * 0.12f)
        cubicTo(w * 0.88f, h * 0.18f, w * 0.95f, h * 0.38f, w * 0.72f, h * 0.50f)
        cubicTo(w * 0.82f, h * 0.38f, w * 0.78f, h * 0.26f, w * 0.62f, h * 0.22f)
        close()
    }
    drawPath(path = flagPath, brush = brush, style = Fill)

    // 3. Stem
    val stemStroke = w * 0.10f
    drawLine(
        brush = brush,
        start = Offset(w * 0.60f, h * 0.74f),
        end = Offset(w * 0.60f, h * 0.12f),
        strokeWidth = stemStroke,
        cap = StrokeCap.Round
    )

    // 4. Notehead
    drawOval(
        brush = brush,
        topLeft = Offset(w * 0.14f, h * 0.58f),
        size = Size(w * 0.48f, h * 0.34f)
    )

    // 5. Specular 3D Gloss
    drawOval(
        brush = Brush.radialGradient(
            colors = listOf(Color.White.copy(alpha = 0.90f), Color.Transparent),
            center = Offset(w * 0.30f, h * 0.66f),
            radius = w * 0.16f
        ),
        topLeft = Offset(w * 0.22f, h * 0.61f),
        size = Size(w * 0.22f, h * 0.16f)
    )
}

/**
 * 3D Golden Amber Sparkle Note with star-burst reflection.
 */
private fun DrawScope.drawGoldenSparkleNote(
    w: Float,
    h: Float,
    gradientColors: List<Color>,
    shadowColor: Color
) {
    val brush = Brush.linearGradient(
        colors = gradientColors,
        start = Offset(0f, 0f),
        end = Offset(w, h)
    )

    // Shadow
    drawOval(
        color = shadowColor,
        topLeft = Offset(w * 0.14f, h * 0.66f),
        size = Size(w * 0.46f, h * 0.32f)
    )

    // Stem
    val stemStroke = w * 0.11f
    drawLine(
        brush = brush,
        start = Offset(w * 0.62f, h * 0.74f),
        end = Offset(w * 0.62f, h * 0.14f),
        strokeWidth = stemStroke,
        cap = StrokeCap.Round
    )

    // Rounded Flag
    val flagPath = Path().apply {
        moveTo(w * 0.62f, h * 0.14f)
        cubicTo(w * 0.90f, h * 0.20f, w * 0.92f, h * 0.42f, w * 0.68f, h * 0.48f)
        cubicTo(w * 0.80f, h * 0.36f, w * 0.74f, h * 0.24f, w * 0.62f, h * 0.24f)
        close()
    }
    drawPath(path = flagPath, brush = brush, style = Fill)

    // Notehead
    drawOval(
        brush = brush,
        topLeft = Offset(w * 0.16f, h * 0.58f),
        size = Size(w * 0.48f, h * 0.34f)
    )

    // Specular radial gloss
    drawOval(
        brush = Brush.radialGradient(
            colors = listOf(Color.White.copy(alpha = 0.90f), Color.Transparent),
            center = Offset(w * 0.32f, h * 0.66f),
            radius = w * 0.16f
        ),
        topLeft = Offset(w * 0.24f, h * 0.61f),
        size = Size(w * 0.22f, h * 0.16f)
    )

    // 4-Point Star Sparkle on top-right
    val cx = w * 0.78f
    val cy = h * 0.14f
    val r = w * 0.10f
    val starPath = Path().apply {
        moveTo(cx, cy - r)
        quadraticTo(cx, cy, cx + r, cy)
        quadraticTo(cx, cy, cx, cy + r)
        quadraticTo(cx, cy, cx - r, cy)
        quadraticTo(cx, cy, cx, cy - r)
        close()
    }
    drawPath(path = starPath, color = Color.White.copy(alpha = 0.95f), style = Fill)
}

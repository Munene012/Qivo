package com.example.ui.components

import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.size
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Fill
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import kotlin.math.cos
import kotlin.math.sin

/**
 * High-Craft 3D Round Face & Reaction Renderer for QIVO Party Rooms.
 *
 * Characteristics:
 * - Spherical 3D Face: Rich multi-stop lighting, specular gloss sheen, ambient bounce light, and depth shadow.
 * - Perfectly STILL Base: The head sphere remains steady (NO zooming in/out, NO bouncing/scaling of the canvas).
 * - Lifelike Facial Animation: Organic eyelid squints, realistic tear flows, pursing kiss lips,
 *   organic heartbeat eyes, and expressive facial muscle movements like a real face.
 */
@Composable
fun CustomReactionGraphic(
    reactionType: SeatReactionType,
    modifier: Modifier = Modifier,
    size: Dp = 38.dp,
    animated: Boolean = true
) {
    Box(
        modifier = modifier.size(size),
        contentAlignment = Alignment.Center
    ) {
        when (reactionType) {
            SeatReactionType.LAUGH -> Custom3DLaughReaction(size = size, animated = animated)
            SeatReactionType.CRY -> Custom3DCryReaction(size = size, animated = animated)
            SeatReactionType.KISS_LEFT -> Custom3DKissReaction(isLeft = true, size = size, animated = animated)
            SeatReactionType.KISS_RIGHT -> Custom3DKissReaction(isLeft = false, size = size, animated = animated)
            SeatReactionType.LOVE -> Custom3DLoveReaction(size = size, animated = animated)
            SeatReactionType.FIRE -> Custom3DFireReaction(size = size, animated = animated)
            SeatReactionType.CLAP -> Custom3DClapReaction(size = size, animated = animated)
            SeatReactionType.PARTY -> Custom3DPartyReaction(size = size, animated = animated)
            SeatReactionType.SHOCK -> Custom3DShockReaction(size = size, animated = animated)
            SeatReactionType.COOL -> Custom3DCoolReaction(size = size, animated = animated)
            SeatReactionType.ANGRY -> Custom3DAngryReaction(size = size, animated = animated)
            SeatReactionType.WINK -> Custom3DWinkReaction(size = size, animated = animated)
        }
    }
}

// -----------------------------------------------------------------------------------------
// 3D SPHERICAL HEAD DRAWER (True 3D round sphere with specular sheen and rim bounce light)
// -----------------------------------------------------------------------------------------
private fun DrawScope.draw3DSphereHead(
    center: Offset,
    radius: Float,
    isAngryCrimson: Boolean = false
) {
    val lightSource = Offset(center.x - radius * 0.32f, center.y - radius * 0.36f)

    // 1. Bottom Ambient Occlusion / Drop Shadow
    drawCircle(
        brush = Brush.radialGradient(
            colors = listOf(Color(0x33000000), Color.Transparent),
            center = Offset(center.x, center.y + radius * 0.18f),
            radius = radius * 1.05f
        ),
        radius = radius * 1.05f,
        center = Offset(center.x, center.y + radius * 0.18f)
    )

    // 2. Multi-stop 3D Spherical Base Gradient
    val sphereColors = if (isAngryCrimson) {
        listOf(
            Color(0xFFFF8A80), // Bright peach-red highlight
            Color(0xFFFF5252), // Vibrant coral red
            Color(0xFFFF1744), // Rich crimson red
            Color(0xFFD50000), // Deep red body
            Color(0xFFB71C1C), // Dark red shadow
            Color(0xFF4A0007)  // Deepest umber shadow
        )
    } else {
        listOf(
            Color(0xFFFFFDE7), // Zenith white-yellow highlight
            Color(0xFFFFF176), // Bright sunshine yellow
            Color(0xFFFFD54F), // Warm golden amber
            Color(0xFFFFB300), // Vibrant amber orange
            Color(0xFFFF8F00), // Deep warm orange shadow
            Color(0xFFD84315)  // Deep burnt umber base
        )
    }

    drawCircle(
        brush = Brush.radialGradient(
            colors = sphereColors,
            center = lightSource,
            radius = radius * 1.45f
        ),
        radius = radius,
        center = center
    )

    // 3. Realistic Top-Left Specular Glossy Reflection (Glass / skin luster sheen)
    val specularPath = Path().apply {
        val sx = center.x - radius * 0.40f
        val sy = center.y - radius * 0.46f
        val sw = radius * 0.72f
        val sh = radius * 0.42f
        addOval(Rect(sx, sy, sx + sw, sy + sh))
    }
    drawPath(
        path = specularPath,
        brush = Brush.radialGradient(
            colors = listOf(
                Color.White.copy(alpha = 0.58f),
                Color.White.copy(alpha = 0.22f),
                Color.Transparent
            ),
            center = Offset(center.x - radius * 0.25f, center.y - radius * 0.35f),
            radius = radius * 0.45f
        )
    )

    // 4. Subtle Bottom Rim Bounce Light (Ambient reflected light from ground)
    drawCircle(
        brush = Brush.radialGradient(
            colors = listOf(
                Color.Transparent,
                if (isAngryCrimson) Color(0xFFFF8A80).copy(alpha = 0.35f) else Color(0xFFFFE082).copy(alpha = 0.40f)
            ),
            center = Offset(center.x, center.y - radius * 0.2f),
            radius = radius
        ),
        radius = radius,
        center = center,
        style = Stroke(width = radius * 0.08f)
    )
}

// -----------------------------------------------------------------------------------------
// 1. LAUGH REACTION (🤣)
// Still 3D sphere face, natural laughing eyelid squeeze, oral cavity depth, and spring tears
// -----------------------------------------------------------------------------------------
@Composable
fun Custom3DLaughReaction(size: Dp = 38.dp, animated: Boolean = true) {
    val transition = rememberInfiniteTransition(label = "3d_laugh_anim")

    // Eyelid squeeze & smile breath
    val laughPulse by transition.animateFloat(
        initialValue = 0f,
        targetValue = 1f,
        animationSpec = infiniteRepeatable(tween(480, easing = FastOutSlowInEasing), RepeatMode.Reverse),
        label = "laugh_pulse"
    )
    val tearProgress by transition.animateFloat(
        initialValue = 0f,
        targetValue = 1f,
        animationSpec = infiniteRepeatable(tween(800, easing = LinearEasing), RepeatMode.Restart),
        label = "tear_drop"
    )

    val currentPulse = if (animated) laughPulse else 0.5f
    val currentTear = if (animated) tearProgress else 0.4f

    Canvas(modifier = Modifier.size(size)) {
        val w = this.size.width
        val h = this.size.height
        val center = Offset(w / 2f, h / 2f)
        val radius = w * 0.44f

        // Round 3D Face (STILL)
        draw3DSphereHead(center, radius)

        // Soft Rosy Cheeks
        drawCircle(Color(0xFFFF5252).copy(alpha = 0.32f), radius = w * 0.11f, center = Offset(w * 0.20f, h * 0.54f))
        drawCircle(Color(0xFFFF5252).copy(alpha = 0.32f), radius = w * 0.11f, center = Offset(w * 0.80f, h * 0.54f))

        // Laughing Squeezed Curved Eyes (>< smiling arcs with organic pulse)
        val eyeSquint = currentPulse * 0.03f * h
        val eyeStroke = w * 0.065f

        val eyeLeft = Path().apply {
            moveTo(w * 0.22f, h * 0.41f + eyeSquint)
            quadraticTo(w * 0.33f, h * 0.29f + eyeSquint, w * 0.44f, h * 0.41f + eyeSquint)
        }
        drawPath(eyeLeft, Color(0xFF3E2723), style = Stroke(width = eyeStroke, cap = StrokeCap.Round))

        val eyeRight = Path().apply {
            moveTo(w * 0.56f, h * 0.41f + eyeSquint)
            quadraticTo(w * 0.67f, h * 0.29f + eyeSquint, w * 0.78f, h * 0.41f + eyeSquint)
        }
        drawPath(eyeRight, Color(0xFF3E2723), style = Stroke(width = eyeStroke, cap = StrokeCap.Round))

        // Open Laughing Mouth with 3D Depth
        val mouthW = w * 0.48f
        val mouthH = h * 0.28f + currentPulse * h * 0.04f
        val mouthTop = h * 0.52f

        val mouthPath = Path().apply {
            moveTo(center.x - mouthW / 2f, mouthTop)
            lineTo(center.x + mouthW / 2f, mouthTop)
            quadraticTo(center.x + mouthW / 2f, mouthTop + mouthH, center.x, mouthTop + mouthH)
            quadraticTo(center.x - mouthW / 2f, mouthTop + mouthH, center.x - mouthW / 2f, mouthTop)
            close()
        }

        // Dark Oral Cavity
        drawPath(mouthPath, Color(0xFF3A0612), style = Fill)
        drawPath(mouthPath, Color(0xFF260408), style = Stroke(width = w * 0.035f))

        // Upper Pearly White Teeth Bar
        val teethPath = Path().apply {
            moveTo(center.x - mouthW * 0.40f, mouthTop)
            lineTo(center.x + mouthW * 0.40f, mouthTop)
            quadraticTo(center.x + mouthW * 0.38f, mouthTop + mouthH * 0.34f, center.x, mouthTop + mouthH * 0.36f)
            quadraticTo(center.x - mouthW * 0.38f, mouthTop + mouthH * 0.34f, center.x - mouthW * 0.40f, mouthTop)
            close()
        }
        drawPath(teethPath, Color(0xFFFFFFFF), style = Fill)

        // Animated 3D Pink Tongue
        val tongueH = mouthH * 0.45f + currentPulse * mouthH * 0.15f
        val tonguePath = Path().apply {
            moveTo(center.x - mouthW * 0.32f, mouthTop + mouthH)
            quadraticTo(center.x, mouthTop + mouthH - tongueH, center.x + mouthW * 0.32f, mouthTop + mouthH)
            close()
        }
        drawPath(tonguePath, Color(0xFFFF4081), style = Fill)

        // 3D Tear Droplets Springing Out
        if (animated) {
            val tDist = currentTear * w * 0.32f
            val tAlpha = (1f - currentTear).coerceIn(0f, 1f)
            val tRadius = w * 0.06f * (1f - currentTear * 0.35f)

            // Left tear
            val leftTearCenter = Offset(w * 0.16f - tDist, h * 0.40f - tDist * 0.4f)
            drawCircle(
                brush = Brush.radialGradient(
                    colors = listOf(Color(0xFFE0F7FA), Color(0xFF00E5FF), Color(0xFF0091EA)),
                    center = Offset(leftTearCenter.x - tRadius * 0.3f, leftTearCenter.y - tRadius * 0.3f),
                    radius = tRadius
                ),
                radius = tRadius,
                center = leftTearCenter,
                alpha = tAlpha
            )
            // Left tear glint
            drawCircle(Color.White.copy(alpha = tAlpha * 0.8f), radius = tRadius * 0.35f, center = Offset(leftTearCenter.x - tRadius * 0.3f, leftTearCenter.y - tRadius * 0.3f))

            // Right tear
            val rightTearCenter = Offset(w * 0.84f + tDist, h * 0.40f - tDist * 0.4f)
            drawCircle(
                brush = Brush.radialGradient(
                    colors = listOf(Color(0xFFE0F7FA), Color(0xFF00E5FF), Color(0xFF0091EA)),
                    center = Offset(rightTearCenter.x + tRadius * 0.3f, rightTearCenter.y - tRadius * 0.3f),
                    radius = tRadius
                ),
                radius = tRadius,
                center = rightTearCenter,
                alpha = tAlpha
            )
            // Right tear glint
            drawCircle(Color.White.copy(alpha = tAlpha * 0.8f), radius = tRadius * 0.35f, center = Offset(rightTearCenter.x + tRadius * 0.3f, rightTearCenter.y - tRadius * 0.3f))
        }
    }
}

// -----------------------------------------------------------------------------------------
// 2. CRY REACTION (😭)
// Still 3D sphere face, anguish eyebrows, realistic streaming waterfall tears with surface glints
// -----------------------------------------------------------------------------------------
@Composable
fun Custom3DCryReaction(size: Dp = 38.dp, animated: Boolean = true) {
    val transition = rememberInfiniteTransition(label = "3d_cry_anim")
    val streamProgress by transition.animateFloat(
        initialValue = 0f,
        targetValue = 1f,
        animationSpec = infiniteRepeatable(tween(750, easing = LinearEasing), RepeatMode.Restart),
        label = "tear_stream"
    )
    val mouthTremor by transition.animateFloat(
        initialValue = -1f,
        targetValue = 1f,
        animationSpec = infiniteRepeatable(tween(400, easing = FastOutSlowInEasing), RepeatMode.Reverse),
        label = "mouth_tremor"
    )

    val currentStream = if (animated) streamProgress else 0.5f
    val currentTremor = if (animated) mouthTremor else 0f

    Canvas(modifier = Modifier.size(size)) {
        val w = this.size.width
        val h = this.size.height
        val center = Offset(w / 2f, h / 2f)
        val radius = w * 0.44f

        // Round 3D Face (STILL)
        draw3DSphereHead(center, radius)

        // Sad Anguished Upward-Curving 3D Eyebrows
        val browStroke = w * 0.055f
        val browLeft = Path().apply {
            moveTo(w * 0.22f, h * 0.33f)
            quadraticTo(w * 0.32f, h * 0.25f, w * 0.42f, h * 0.27f)
        }
        drawPath(browLeft, Color(0xFF3E2723), style = Stroke(width = browStroke, cap = StrokeCap.Round))

        val browRight = Path().apply {
            moveTo(w * 0.58f, h * 0.27f)
            quadraticTo(w * 0.68f, h * 0.25f, w * 0.78f, h * 0.33f)
        }
        drawPath(browRight, Color(0xFF3E2723), style = Stroke(width = browStroke, cap = StrokeCap.Round))

        // Closed Squeezed Crying Eyes
        val eyeLeft = Path().apply {
            moveTo(w * 0.24f, h * 0.40f)
            quadraticTo(w * 0.34f, h * 0.48f, w * 0.44f, h * 0.40f)
        }
        drawPath(eyeLeft, Color(0xFF3E2723), style = Stroke(width = w * 0.065f, cap = StrokeCap.Round))

        val eyeRight = Path().apply {
            moveTo(w * 0.56f, h * 0.40f)
            quadraticTo(w * 0.66f, h * 0.48f, w * 0.76f, h * 0.40f)
        }
        drawPath(eyeRight, Color(0xFF3E2723), style = Stroke(width = w * 0.065f, cap = StrokeCap.Round))

        // Trembling Downturned Sad Mouth
        val mouthPath = Path().apply {
            moveTo(w * 0.32f, h * 0.74f)
            quadraticTo(w * 0.50f, h * 0.62f + currentTremor * 0.02f * h, w * 0.68f, h * 0.74f)
        }
        drawPath(mouthPath, Color(0xFF3E2723), style = Stroke(width = w * 0.06f, cap = StrokeCap.Round))

        // Flowing 3D Waterfall Tear Streams on Left and Right Cheeks
        val streamWidth = w * 0.09f
        val tearGradient = Brush.verticalGradient(
            colors = listOf(
                Color(0xEE80D8FF),
                Color(0xFF00B0FF),
                Color(0xFF0091EA)
            )
        )

        // Left waterfall stream
        drawLine(
            brush = tearGradient,
            start = Offset(w * 0.34f, h * 0.43f),
            end = Offset(w * 0.34f, h * 0.92f),
            strokeWidth = streamWidth,
            cap = StrokeCap.Round
        )
        // Left specular tear sheen line
        drawLine(
            color = Color.White.copy(alpha = 0.65f),
            start = Offset(w * 0.32f, h * 0.46f),
            end = Offset(w * 0.32f, h * 0.88f),
            strokeWidth = streamWidth * 0.3f,
            cap = StrokeCap.Round
        )

        // Right waterfall stream
        drawLine(
            brush = tearGradient,
            start = Offset(w * 0.66f, h * 0.43f),
            end = Offset(w * 0.66f, h * 0.92f),
            strokeWidth = streamWidth,
            cap = StrokeCap.Round
        )
        // Right specular tear sheen line
        drawLine(
            color = Color.White.copy(alpha = 0.65f),
            start = Offset(w * 0.64f, h * 0.46f),
            end = Offset(w * 0.64f, h * 0.88f),
            strokeWidth = streamWidth * 0.3f,
            cap = StrokeCap.Round
        )

        // Falling teardrop beads
        if (animated) {
            val beadY1 = h * 0.45f + (currentStream * h * 0.45f)
            val beadY2 = h * 0.45f + (((currentStream + 0.5f) % 1f) * h * 0.45f)
            drawCircle(Color(0xFFE0F7FA), radius = w * 0.05f, center = Offset(w * 0.34f, beadY1))
            drawCircle(Color(0xFFE0F7FA), radius = w * 0.045f, center = Offset(w * 0.66f, beadY2))
        }
    }
}

// -----------------------------------------------------------------------------------------
// 3. KISS REACTION (😘 / 😚)
// Still 3D sphere face, wink, 3D puckered kiss lips pursing, and glowing drifting heart
// -----------------------------------------------------------------------------------------
@Composable
fun Custom3DKissReaction(isLeft: Boolean = false, size: Dp = 38.dp, animated: Boolean = true) {
    val transition = rememberInfiniteTransition(label = "3d_kiss_anim")
    val kissPucker by transition.animateFloat(
        initialValue = 0f,
        targetValue = 1f,
        animationSpec = infiniteRepeatable(tween(650, easing = FastOutSlowInEasing), RepeatMode.Reverse),
        label = "kiss_pucker"
    )
    val heartFloat by transition.animateFloat(
        initialValue = 0f,
        targetValue = 1f,
        animationSpec = infiniteRepeatable(tween(1100, easing = LinearEasing), RepeatMode.Restart),
        label = "heart_float"
    )

    val currentPucker = if (animated) kissPucker else 0.5f
    val currentHeart = if (animated) heartFloat else 0.3f

    Canvas(modifier = Modifier.size(size)) {
        val w = this.size.width
        val h = this.size.height
        val center = Offset(w / 2f, h / 2f)
        val radius = w * 0.44f

        // Round 3D Face (STILL)
        draw3DSphereHead(center, radius)

        // Rosy Blushing Cheeks
        drawCircle(Color(0xFFFF4081).copy(alpha = 0.45f), radius = w * 0.12f, center = Offset(w * 0.22f, h * 0.56f))
        drawCircle(Color(0xFFFF4081).copy(alpha = 0.45f), radius = w * 0.12f, center = Offset(w * 0.78f, h * 0.56f))

        // Eyes: Wink on one side, expressive open eye on the other
        if (isLeft) {
            // Left eye winking arc
            val winkPath = Path().apply {
                moveTo(w * 0.22f, h * 0.42f)
                quadraticTo(w * 0.33f, h * 0.30f, w * 0.44f, h * 0.42f)
            }
            drawPath(winkPath, Color(0xFF3E2723), style = Stroke(width = w * 0.065f, cap = StrokeCap.Round))

            // Right open bright eye
            drawCircle(Color(0xFF3E2723), radius = w * 0.075f, center = Offset(w * 0.68f, h * 0.39f))
            drawCircle(Color.White, radius = w * 0.035f, center = Offset(w * 0.66f, h * 0.37f))
        } else {
            // Left open bright eye
            drawCircle(Color(0xFF3E2723), radius = w * 0.075f, center = Offset(w * 0.32f, h * 0.39f))
            drawCircle(Color.White, radius = w * 0.035f, center = Offset(w * 0.30f, h * 0.37f))

            // Right eye winking arc
            val winkPath = Path().apply {
                moveTo(w * 0.56f, h * 0.42f)
                quadraticTo(w * 0.67f, h * 0.30f, w * 0.78f, h * 0.42f)
            }
            drawPath(winkPath, Color(0xFF3E2723), style = Stroke(width = w * 0.065f, cap = StrokeCap.Round))
        }

        // 3D Puckered Kissing Lips (Animated pursing)
        val lipX = if (isLeft) w * 0.36f else w * 0.64f
        val puckerShift = currentPucker * w * 0.04f
        val lipPath = Path().apply {
            val lx = if (isLeft) lipX - puckerShift else lipX + puckerShift
            moveTo(lx, h * 0.58f)
            quadraticTo(lx + (if (isLeft) -w * 0.10f else w * 0.10f), h * 0.66f, lx, h * 0.72f)
            quadraticTo(lx + (if (isLeft) -w * 0.08f else w * 0.08f), h * 0.78f, lx, h * 0.82f)
        }
        drawPath(lipPath, Color(0xFFE91E63), style = Stroke(width = w * 0.07f, cap = StrokeCap.Round))
        drawPath(lipPath, Color(0xFFFF80AB), style = Stroke(width = w * 0.025f, cap = StrokeCap.Round))

        // Floating 3D Red Glossy Heart
        val dir = if (isLeft) -1f else 1f
        val hX = center.x + dir * (w * 0.28f + currentHeart * w * 0.30f)
        val hY = center.y - (currentHeart * h * 0.34f)
        val hSize = w * 0.22f * (1f + currentHeart * 0.15f)
        val hAlpha = (1f - currentHeart * 0.6f).coerceIn(0f, 1f)

        drawHeart(
            center = Offset(hX, hY),
            size = hSize,
            color = Color(0xFFFF1744).copy(alpha = if (animated) hAlpha else 0.95f),
            with3DShine = true
        )
    }
}

// -----------------------------------------------------------------------------------------
// 4. LOVE REACTION (😍)
// Still 3D sphere face, glowing 3D ruby heart eyes with cardiac heartbeat pulse, warm open smile
// -----------------------------------------------------------------------------------------
@Composable
fun Custom3DLoveReaction(size: Dp = 38.dp, animated: Boolean = true) {
    val transition = rememberInfiniteTransition(label = "3d_love_anim")
    val heartPulse by transition.animateFloat(
        initialValue = 0.94f,
        targetValue = 1.15f,
        animationSpec = infiniteRepeatable(tween(650, easing = FastOutSlowInEasing), RepeatMode.Reverse),
        label = "heart_pulse"
    )
    val orbitAngle by transition.animateFloat(
        initialValue = 0f,
        targetValue = 360f,
        animationSpec = infiniteRepeatable(tween(4000, easing = LinearEasing), RepeatMode.Restart),
        label = "orbit_hearts"
    )

    val currentPulse = if (animated) heartPulse else 1.0f
    val currentAngle = if (animated) orbitAngle else 45f

    Canvas(modifier = Modifier.size(size)) {
        val w = this.size.width
        val h = this.size.height
        val center = Offset(w / 2f, h / 2f)
        val radius = w * 0.44f

        // Round 3D Face (STILL)
        draw3DSphereHead(center, radius)

        // Warm Glowing Blushing Cheeks
        drawCircle(Color(0xFFFF4081).copy(alpha = 0.50f), radius = w * 0.13f, center = Offset(w * 0.20f, h * 0.58f))
        drawCircle(Color(0xFFFF4081).copy(alpha = 0.50f), radius = w * 0.13f, center = Offset(w * 0.80f, h * 0.58f))

        // Big Glossy 3D Ruby Heart Eyes (Pulsing organically)
        val eyeHeartSize = w * 0.28f * currentPulse
        drawHeart(
            center = Offset(w * 0.32f, h * 0.39f),
            size = eyeHeartSize,
            color = Color(0xFFFF1744),
            with3DShine = true
        )
        drawHeart(
            center = Offset(w * 0.68f, h * 0.39f),
            size = eyeHeartSize,
            color = Color(0xFFFF1744),
            with3DShine = true
        )

        // Wide Joyful Open Smile with 3D Depth
        val smilePath = Path().apply {
            moveTo(w * 0.28f, h * 0.62f)
            quadraticTo(w * 0.50f, h * 0.86f, w * 0.72f, h * 0.62f)
            close()
        }
        drawPath(smilePath, Color(0xFF3E0A1E), style = Fill)
        drawPath(smilePath, Color(0xFF3E2723), style = Stroke(width = w * 0.035f))

        // Upper pearly teeth
        val smileTeeth = Path().apply {
            moveTo(w * 0.32f, h * 0.62f)
            lineTo(w * 0.68f, h * 0.62f)
            quadraticTo(w * 0.50f, h * 0.70f, w * 0.32f, h * 0.62f)
            close()
        }
        drawPath(smileTeeth, Color.White, style = Fill)

        // Orbiting Mini Sparkle Hearts
        if (animated) {
            for (i in 0 until 3) {
                val rad = (currentAngle + i * 120f) * (Math.PI / 180f)
                val ox = (center.x + cos(rad) * (w * 0.46f)).toFloat()
                val oy = (center.y + sin(rad) * (h * 0.46f)).toFloat()
                drawHeart(center = Offset(ox, oy), size = w * 0.11f, color = Color(0xFFFF4081), with3DShine = false)
            }
        }
    }
}

// -----------------------------------------------------------------------------------------
// 5. COOL REACTION (😎)
// Still 3D sphere face, 3D polarized sunglasses with moving specular lens flare and confident smirk
// -----------------------------------------------------------------------------------------
@Composable
fun Custom3DCoolReaction(size: Dp = 38.dp, animated: Boolean = true) {
    val transition = rememberInfiniteTransition(label = "3d_cool_anim")
    val gleamPos by transition.animateFloat(
        initialValue = 0f,
        targetValue = 1f,
        animationSpec = infiniteRepeatable(tween(1300, easing = LinearEasing), RepeatMode.Restart),
        label = "lens_gleam"
    )

    val currentGleam = if (animated) gleamPos else 0.4f

    Canvas(modifier = Modifier.size(size)) {
        val w = this.size.width
        val h = this.size.height
        val center = Offset(w / 2f, h / 2f)
        val radius = w * 0.44f

        // Round 3D Face (STILL)
        draw3DSphereHead(center, radius)

        // 3D Dark Aviator Sunglasses Frames
        val glassLeft = Path().apply {
            moveTo(w * 0.16f, h * 0.34f)
            lineTo(w * 0.47f, h * 0.34f)
            lineTo(w * 0.44f, h * 0.54f)
            quadraticTo(w * 0.31f, h * 0.60f, w * 0.18f, h * 0.53f)
            close()
        }
        drawPath(glassLeft, Color(0xFF151515), style = Fill)
        drawPath(glassLeft, Color(0xFFFFD54F), style = Stroke(width = w * 0.035f))

        val glassRight = Path().apply {
            moveTo(w * 0.53f, h * 0.34f)
            lineTo(w * 0.84f, h * 0.34f)
            lineTo(w * 0.82f, h * 0.53f)
            quadraticTo(w * 0.69f, h * 0.60f, w * 0.56f, h * 0.54f)
            close()
        }
        drawPath(glassRight, Color(0xFF151515), style = Fill)
        drawPath(glassRight, Color(0xFFFFD54F), style = Stroke(width = w * 0.035f))

        // Gold Bridge
        drawLine(
            Color(0xFFFFD54F),
            start = Offset(w * 0.47f, h * 0.37f),
            end = Offset(w * 0.53f, h * 0.37f),
            strokeWidth = w * 0.045f
        )

        // Moving Lens Specular Sheen (Real glass reflection)
        if (animated) {
            val glX = w * 0.18f + (currentGleam * w * 0.24f)
            drawLine(
                Color.White.copy(alpha = 0.75f),
                start = Offset(glX, h * 0.37f),
                end = Offset(glX - w * 0.07f, h * 0.51f),
                strokeWidth = w * 0.035f,
                cap = StrokeCap.Round
            )
            val glX2 = w * 0.55f + (currentGleam * w * 0.24f)
            drawLine(
                Color.White.copy(alpha = 0.75f),
                start = Offset(glX2, h * 0.37f),
                end = Offset(glX2 - w * 0.07f, h * 0.51f),
                strokeWidth = w * 0.035f,
                cap = StrokeCap.Round
            )
        }

        // Suave Confident Smirk Mouth
        val smirkPath = Path().apply {
            moveTo(w * 0.35f, h * 0.72f)
            quadraticTo(w * 0.54f, h * 0.79f, w * 0.72f, h * 0.67f)
        }
        drawPath(smirkPath, Color(0xFF3E2723), style = Stroke(width = w * 0.065f, cap = StrokeCap.Round))

        // Sparkling Gold Star Flare at Corner
        drawStar(center = Offset(w * 0.83f, h * 0.29f), size = w * 0.18f, color = Color(0xFFFFD600))
    }
}

// -----------------------------------------------------------------------------------------
// 6. SHOCK REACTION (🤯)
// Still 3D sphere face, wide-open 3D eyes with dilated pupils, open 'O' mouth, and cosmic brain burst
// -----------------------------------------------------------------------------------------
@Composable
fun Custom3DShockReaction(size: Dp = 38.dp, animated: Boolean = true) {
    val transition = rememberInfiniteTransition(label = "3d_shock_anim")
    val blastProgress by transition.animateFloat(
        initialValue = 0.35f,
        targetValue = 1f,
        animationSpec = infiniteRepeatable(tween(850, easing = FastOutSlowInEasing), RepeatMode.Restart),
        label = "shock_blast"
    )

    val currentBlast = if (animated) blastProgress else 0.5f

    Canvas(modifier = Modifier.size(size)) {
        val w = this.size.width
        val h = this.size.height
        val center = Offset(w / 2f, h * 0.62f)
        val radius = w * 0.36f

        // Mind-Blown Energy Blast Cloud above the head
        val cloudR = w * 0.28f * currentBlast
        drawCircle(
            brush = Brush.radialGradient(
                listOf(Color(0xFFFFD600), Color(0xFFFF3D00), Color(0xFFD500F9)),
                center = Offset(w * 0.5f, h * 0.24f),
                radius = cloudR
            ),
            radius = cloudR,
            center = Offset(w * 0.5f, h * 0.24f)
        )
        if (animated) {
            drawCircle(
                color = Color(0xFFFFAB00).copy(alpha = 1f - currentBlast),
                radius = cloudR * 1.35f,
                center = Offset(w * 0.5f, h * 0.24f),
                style = Stroke(width = w * 0.04f)
            )
        }

        // Round 3D Lower Face (STILL)
        draw3DSphereHead(center, radius)

        // Wide Shocked 3D Eyes (White sclera, dark iris, corneal glint)
        val eyeLeftPos = Offset(w * 0.34f, h * 0.55f)
        drawCircle(Color.White, radius = w * 0.095f, center = eyeLeftPos)
        drawCircle(Color(0xFF212121), radius = w * 0.05f, center = eyeLeftPos)
        drawCircle(Color.White, radius = w * 0.02f, center = Offset(eyeLeftPos.x - w * 0.02f, eyeLeftPos.y - w * 0.02f))

        val eyeRightPos = Offset(w * 0.66f, h * 0.55f)
        drawCircle(Color.White, radius = w * 0.095f, center = eyeRightPos)
        drawCircle(Color(0xFF212121), radius = w * 0.05f, center = eyeRightPos)
        drawCircle(Color.White, radius = w * 0.02f, center = Offset(eyeRightPos.x - w * 0.02f, eyeRightPos.y - w * 0.02f))

        // Open Shocked "O" Mouth with 3D Cavity Depth
        drawCircle(Color(0xFF260408), radius = w * 0.085f, center = Offset(w * 0.50f, h * 0.79f))
        drawCircle(Color(0xFF3E2723), radius = w * 0.085f, center = Offset(w * 0.50f, h * 0.79f), style = Stroke(width = w * 0.025f))
    }
}

// -----------------------------------------------------------------------------------------
// 7. ANGRY REACTION (😡)
// Deep Crimson 3D sphere (STILL, no scaling/zooming), furrowed V-brows, clenched teeth, soft steam puffs
// -----------------------------------------------------------------------------------------
@Composable
fun Custom3DAngryReaction(size: Dp = 38.dp, animated: Boolean = true) {
    val transition = rememberInfiniteTransition(label = "3d_angry_anim")
    val steamProgress by transition.animateFloat(
        initialValue = 0f,
        targetValue = 1f,
        animationSpec = infiniteRepeatable(tween(850, easing = LinearEasing), RepeatMode.Restart),
        label = "angry_steam"
    )

    val currentSteam = if (animated) steamProgress else 0.5f

    Canvas(modifier = Modifier.size(size)) {
        val w = this.size.width
        val h = this.size.height
        val center = Offset(w / 2f, h / 2f)
        val radius = w * 0.44f

        // Round 3D Crimson Face (STILL)
        draw3DSphereHead(center, radius, isAngryCrimson = true)

        // Furious Slanted Eyebrows
        val browStroke = w * 0.075f
        val browLeft = Path().apply {
            moveTo(w * 0.18f, h * 0.31f)
            lineTo(w * 0.47f, h * 0.44f)
        }
        drawPath(browLeft, Color(0xFF1E0808), style = Stroke(width = browStroke, cap = StrokeCap.Round))

        val browRight = Path().apply {
            moveTo(w * 0.82f, h * 0.31f)
            lineTo(w * 0.53f, h * 0.44f)
        }
        drawPath(browRight, Color(0xFF1E0808), style = Stroke(width = browStroke, cap = StrokeCap.Round))

        // Glaring 3D Eyes
        drawCircle(Color(0xFF260505), radius = w * 0.07f, center = Offset(w * 0.35f, h * 0.48f))
        drawCircle(Color.White.copy(alpha = 0.7f), radius = w * 0.02f, center = Offset(w * 0.33f, h * 0.46f))

        drawCircle(Color(0xFF260505), radius = w * 0.07f, center = Offset(w * 0.65f, h * 0.48f))
        drawCircle(Color.White.copy(alpha = 0.7f), radius = w * 0.02f, center = Offset(w * 0.63f, h * 0.46f))

        // Tense Gritted Teeth / Downward Frown
        val frownPath = Path().apply {
            moveTo(w * 0.28f, h * 0.76f)
            quadraticTo(w * 0.50f, h * 0.64f, w * 0.72f, h * 0.76f)
        }
        drawPath(frownPath, Color(0xFF1E0808), style = Stroke(width = w * 0.07f, cap = StrokeCap.Round))

        // Billowing Translucent Steam Puffs (Temples)
        if (animated) {
            val sDist = currentSteam * w * 0.22f
            val sAlpha = (1f - currentSteam).coerceIn(0f, 1f)
            drawCircle(Color.White.copy(alpha = sAlpha * 0.75f), radius = w * 0.06f, center = Offset(w * 0.10f - sDist, h * 0.46f))
            drawCircle(Color.White.copy(alpha = sAlpha * 0.75f), radius = w * 0.06f, center = Offset(w * 0.90f + sDist, h * 0.46f))
        }
    }
}

// -----------------------------------------------------------------------------------------
// 8. CUTE WINK REACTION (😉)
// Still 3D sphere face, smooth winking eyelid, bright sparkling open eye with golden flare
// -----------------------------------------------------------------------------------------
@Composable
fun Custom3DWinkReaction(size: Dp = 38.dp, animated: Boolean = true) {
    val transition = rememberInfiniteTransition(label = "3d_wink_anim")
    val starScale by transition.animateFloat(
        initialValue = 0.85f,
        targetValue = 1.25f,
        animationSpec = infiniteRepeatable(tween(700, easing = FastOutSlowInEasing), RepeatMode.Reverse),
        label = "wink_star"
    )

    val currentStar = if (animated) starScale else 1.0f

    Canvas(modifier = Modifier.size(size)) {
        val w = this.size.width
        val h = this.size.height
        val center = Offset(w / 2f, h / 2f)
        val radius = w * 0.44f

        // Round 3D Face (STILL)
        draw3DSphereHead(center, radius)

        // Rosy Blushing Cheeks
        drawCircle(Color(0xFFFF4081).copy(alpha = 0.45f), radius = w * 0.11f, center = Offset(w * 0.22f, h * 0.58f))
        drawCircle(Color(0xFFFF4081).copy(alpha = 0.45f), radius = w * 0.11f, center = Offset(w * 0.78f, h * 0.58f))

        // Left Eye: Winking Closed Arc
        val winkPath = Path().apply {
            moveTo(w * 0.22f, h * 0.42f)
            quadraticTo(w * 0.32f, h * 0.30f, w * 0.42f, h * 0.42f)
        }
        drawPath(winkPath, Color(0xFF3E2723), style = Stroke(width = w * 0.065f, cap = StrokeCap.Round))

        // Right Eye: Open Star Eye with Twinkling Flare
        drawCircle(Color(0xFF3E2723), radius = w * 0.075f, center = Offset(w * 0.68f, h * 0.39f))
        drawCircle(Color.White, radius = w * 0.03f, center = Offset(w * 0.66f, h * 0.37f))
        drawStar(center = Offset(w * 0.71f, h * 0.37f), size = w * 0.22f * currentStar, color = Color(0xFFFFD600))

        // Playful Open Smile
        val smilePath = Path().apply {
            moveTo(w * 0.32f, h * 0.62f)
            quadraticTo(w * 0.50f, h * 0.82f, w * 0.68f, h * 0.62f)
        }
        drawPath(smilePath, Color(0xFF3E2723), style = Stroke(width = w * 0.06f, cap = StrokeCap.Round))
    }
}

// -----------------------------------------------------------------------------------------
// 9. FIRE REACTION (🔥)
// Multi-layered 3D roaring flame with floating glowing embers (STILL container)
// -----------------------------------------------------------------------------------------
@Composable
fun Custom3DFireReaction(size: Dp = 38.dp, animated: Boolean = true) {
    val transition = rememberInfiniteTransition(label = "3d_fire_anim")
    val flameSway by transition.animateFloat(
        initialValue = -1.2f,
        targetValue = 1.2f,
        animationSpec = infiniteRepeatable(tween(750, easing = FastOutSlowInEasing), RepeatMode.Reverse),
        label = "fire_sway"
    )
    val emberFloat by transition.animateFloat(
        initialValue = 0f,
        targetValue = 1f,
        animationSpec = infiniteRepeatable(tween(850, easing = LinearEasing), RepeatMode.Restart),
        label = "ember_float"
    )

    val currentSway = if (animated) flameSway else 0f
    val currentEmber = if (animated) emberFloat else 0.5f

    Canvas(modifier = Modifier.size(size)) {
        val w = this.size.width
        val h = this.size.height

        // Outer 3D Flame
        val outerPath = Path().apply {
            moveTo(w * 0.50f + currentSway, h * 0.08f)
            cubicTo(w * 0.85f, h * 0.35f, w * 0.95f, h * 0.70f, w * 0.50f, h * 0.96f)
            cubicTo(w * 0.05f, h * 0.70f, w * 0.15f, h * 0.35f, w * 0.50f + currentSway, h * 0.08f)
            close()
        }
        drawPath(
            path = outerPath,
            brush = Brush.verticalGradient(listOf(Color(0xFFFF3D00), Color(0xFFD50000)))
        )

        // Middle Flame
        val midPath = Path().apply {
            moveTo(w * 0.50f - currentSway * 0.5f, h * 0.28f)
            cubicTo(w * 0.78f, h * 0.48f, w * 0.82f, h * 0.75f, w * 0.50f, h * 0.94f)
            cubicTo(w * 0.18f, h * 0.75f, w * 0.22f, h * 0.48f, w * 0.50f - currentSway * 0.5f, h * 0.28f)
            close()
        }
        drawPath(
            path = midPath,
            brush = Brush.verticalGradient(listOf(Color(0xFFFF9100), Color(0xFFFF3D00)))
        )

        // Inner Core Flame
        val innerPath = Path().apply {
            moveTo(w * 0.50f, h * 0.48f)
            cubicTo(w * 0.68f, h * 0.62f, w * 0.70f, h * 0.82f, w * 0.50f, h * 0.92f)
            cubicTo(w * 0.30f, h * 0.82f, w * 0.32f, h * 0.62f, w * 0.50f, h * 0.48f)
            close()
        }
        drawPath(
            path = innerPath,
            brush = Brush.verticalGradient(listOf(Color(0xFFFFF9C4), Color(0xFFFFD600)))
        )

        // Rising Embers
        if (animated) {
            val ey1 = h * 0.8f - (currentEmber * h * 0.70f)
            drawCircle(Color(0xFFFFD600), radius = w * 0.045f * (1f - currentEmber * 0.4f), center = Offset(w * 0.30f + currentSway, ey1))
        }
    }
}

// -----------------------------------------------------------------------------------------
// 10. CLAP REACTION (👏)
// Clapping hands with rhythmic golden shockwave ripple (STILL container)
// -----------------------------------------------------------------------------------------
@Composable
fun Custom3DClapReaction(size: Dp = 38.dp, animated: Boolean = true) {
    val transition = rememberInfiniteTransition(label = "3d_clap_anim")
    val clapMove by transition.animateFloat(
        initialValue = -2.2f,
        targetValue = 0f,
        animationSpec = infiniteRepeatable(tween(550, easing = FastOutSlowInEasing), RepeatMode.Reverse),
        label = "clap_move"
    )
    val waveProgress by transition.animateFloat(
        initialValue = 0f,
        targetValue = 1f,
        animationSpec = infiniteRepeatable(tween(750, easing = LinearEasing), RepeatMode.Restart),
        label = "clap_wave"
    )

    val currentClap = if (animated) clapMove else 0f
    val currentWave = if (animated) waveProgress else 0.5f

    Canvas(modifier = Modifier.size(size)) {
        val w = this.size.width
        val h = this.size.height
        val center = Offset(w / 2f, h / 2f)

        // Acoustic shockwave rings
        if (animated) {
            val waveR = currentWave * w * 0.44f
            drawCircle(
                color = Color(0xFFFFD54F).copy(alpha = 1f - currentWave),
                radius = waveR,
                center = center,
                style = Stroke(width = w * 0.04f)
            )
        }

        // Left Hand
        val leftHandPath = Path().apply {
            moveTo(w * 0.15f - currentClap, h * 0.75f)
            lineTo(w * 0.44f - currentClap * 0.2f, h * 0.38f)
            lineTo(w * 0.50f, h * 0.44f)
            lineTo(w * 0.32f, h * 0.85f)
            close()
        }
        drawPath(
            path = leftHandPath,
            brush = Brush.linearGradient(listOf(Color(0xFFFFCC80), Color(0xFFFFB74D)))
        )
        drawPath(leftHandPath, Color(0xFFE65100), style = Stroke(width = w * 0.035f))

        // Right Hand
        val rightHandPath = Path().apply {
            moveTo(w * 0.85f + currentClap, h * 0.75f)
            lineTo(w * 0.56f + currentClap * 0.2f, h * 0.38f)
            lineTo(w * 0.50f, h * 0.44f)
            lineTo(w * 0.68f, h * 0.85f)
            close()
        }
        drawPath(
            path = rightHandPath,
            brush = Brush.linearGradient(listOf(Color(0xFFFFB74D), Color(0xFFFFA726)))
        )
        drawPath(rightHandPath, Color(0xFFE65100), style = Stroke(width = w * 0.035f))

        // Golden Sparkle
        drawStar(center = center, size = w * 0.18f, color = Color(0xFFFFD600))
    }
}

// -----------------------------------------------------------------------------------------
// 11. PARTY POPPER REACTION (🎉)
// Striped horn with festive colorful confetti explosion (STILL container)
// -----------------------------------------------------------------------------------------
@Composable
fun Custom3DPartyReaction(size: Dp = 38.dp, animated: Boolean = true) {
    val transition = rememberInfiniteTransition(label = "3d_party_anim")
    val confettiProgress by transition.animateFloat(
        initialValue = 0f,
        targetValue = 1f,
        animationSpec = infiniteRepeatable(tween(900, easing = LinearEasing), RepeatMode.Restart),
        label = "confetti_stream"
    )

    val currentConfetti = if (animated) confettiProgress else 0.5f

    Canvas(modifier = Modifier.size(size)) {
        val w = this.size.width
        val h = this.size.height

        // Party Horn / Cone (STILL)
        val hornPath = Path().apply {
            moveTo(w * 0.15f, h * 0.85f)
            lineTo(w * 0.40f, h * 0.45f)
            lineTo(w * 0.55f, h * 0.60f)
            close()
        }
        drawPath(hornPath, Brush.linearGradient(listOf(Color(0xFFFF9100), Color(0xFFFF3D00))))
        drawPath(hornPath, Color(0xFFBF360C), style = Stroke(width = w * 0.035f))

        // Striped Horn Pattern
        drawLine(
            Color(0xFFFFD600),
            start = Offset(w * 0.25f, h * 0.70f),
            end = Offset(w * 0.35f, h * 0.80f),
            strokeWidth = w * 0.04f
        )

        // Exploding Confetti Particles
        val confettiColors = listOf(
            Color(0xFFFF1744), Color(0xFFFFEA00), Color(0xFF00E676),
            Color(0xFF2979FF), Color(0xFFD500F9), Color(0xFFFF9100)
        )

        val origin = Offset(w * 0.48f, h * 0.52f)
        for (i in 0 until 8) {
            val angle = (-80f + i * 22f) * (Math.PI / 180f)
            val dist = currentConfetti * w * 0.48f
            val cx = (origin.x + cos(angle) * dist).toFloat()
            val cy = (origin.y + sin(angle) * dist).toFloat()
            val color = confettiColors[i % confettiColors.size]

            if (i % 2 == 0) {
                drawCircle(color, radius = w * 0.04f, center = Offset(cx, cy))
            } else {
                drawRect(
                    color = color,
                    topLeft = Offset(cx - w * 0.03f, cy - w * 0.03f),
                    size = Size(w * 0.06f, w * 0.06f)
                )
            }
        }

        drawStar(center = Offset(w * 0.72f, h * 0.22f), size = w * 0.16f, color = Color(0xFFFFD600))
    }
}

// -----------------------------------------------------------------------------------------
// HELPERS
// -----------------------------------------------------------------------------------------

/** Helper to draw a heart shape with optional 3D specular shine */
private fun DrawScope.drawHeart(
    center: Offset,
    size: Float,
    color: Color,
    with3DShine: Boolean = false
) {
    val h = size
    val w = size
    val path = Path().apply {
        moveTo(center.x, center.y + h * 0.45f)
        cubicTo(
            center.x - w * 0.60f, center.y - h * 0.10f,
            center.x - w * 0.45f, center.y - h * 0.55f,
            center.x, center.y - h * 0.20f
        )
        cubicTo(
            center.x + w * 0.45f, center.y - h * 0.55f,
            center.x + w * 0.60f, center.y - h * 0.10f,
            center.x, center.y + h * 0.45f
        )
        close()
    }

    if (with3DShine) {
        // 3D spherical gradient for heart
        drawPath(
            path = path,
            brush = Brush.radialGradient(
                colors = listOf(Color(0xFFFF5252), color, Color(0xFFB71C1C)),
                center = Offset(center.x - w * 0.18f, center.y - h * 0.22f),
                radius = size * 0.75f
            )
        )
        // Specular glint
        drawCircle(
            color = Color.White.copy(alpha = 0.65f),
            radius = size * 0.12f,
            center = Offset(center.x - w * 0.22f, center.y - h * 0.26f)
        )
    } else {
        drawPath(path, color, style = Fill)
    }
}

/** Helper to draw a 4-point golden star flare centered at [center] */
private fun DrawScope.drawStar(
    center: Offset,
    size: Float,
    color: Color
) {
    val r = size / 2f
    val ir = r * 0.28f
    val path = Path().apply {
        moveTo(center.x, center.y - r)
        lineTo(center.x + ir, center.y - ir)
        lineTo(center.x + r, center.y)
        lineTo(center.x + ir, center.y + ir)
        lineTo(center.x, center.y + r)
        lineTo(center.x - ir, center.y + ir)
        lineTo(center.x - r, center.y)
        lineTo(center.x - ir, center.y - ir)
        close()
    }
    drawPath(path, color, style = Fill)
}

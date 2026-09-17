package com.example.ui.components

import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Fill
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.rotate
import androidx.compose.ui.graphics.nativeCanvas
import android.graphics.Paint
import android.graphics.Typeface
import androidx.compose.ui.unit.dp
import com.example.data.AvatarFrameManager
import kotlin.math.cos
import kotlin.math.sin

/**
 * High-craft Composable that renders animated, ornate Avatar Frames around any circular avatar.
 */
@Composable
fun AvatarFrameRenderer(
    frameId: String,
    modifier: Modifier = Modifier,
    animated: Boolean = true
) {
    if (frameId.isBlank() || frameId == "none") return

    val infiniteTransition = rememberInfiniteTransition(label = "frame_anim")
    val pulse by infiniteTransition.animateFloat(
        initialValue = 0f,
        targetValue = 1f,
        animationSpec = infiniteRepeatable(
            animation = tween(2400, easing = LinearEasing),
            repeatMode = RepeatMode.Reverse
        ),
        label = "pulse"
    )
    val rotation by infiniteTransition.animateFloat(
        initialValue = 0f,
        targetValue = 360f,
        animationSpec = infiniteRepeatable(
            animation = tween(12000, easing = LinearEasing),
            repeatMode = RepeatMode.Restart
        ),
        label = "rotation"
    )
    val rotorRotation by infiniteTransition.animateFloat(
        initialValue = 0f,
        targetValue = 360f,
        animationSpec = infiniteRepeatable(
            animation = tween(400, easing = LinearEasing),
            repeatMode = RepeatMode.Restart
        ),
        label = "rotor"
    )

    Canvas(
        modifier = modifier
            .fillMaxSize()
    ) {
        val w = size.width
        val h = size.height
        val cx = w / 2f
        val cy = h / 2f
        val r = (minOf(w, h) / 2f) * 0.72f // Avatar base radius matching 72% inner scale

        when (AvatarFrameManager.normalizeFrameId(frameId)) {
            "amethyst_sovereign" -> drawVip4Frame(cx, cy, r, pulse)
            "diamond_luminary" -> drawSvip1Frame(cx, cy, r, pulse)
            "aurora_starlight" -> drawAcquaintanceFrame(cx, cy, r, pulse)
            "azure_sentinel" -> drawVip1Frame(cx, cy, r, pulse)
            "sapphire_phoenix" -> drawVip3Frame(cx, cy, r, pulse)
            "sky_aviator" -> drawHelicopterFrame(cx, cy, r, rotorRotation, pulse)
            "enchanted_flora" -> drawFlowersGardenFrame(cx, cy, r, pulse)
            "solar_monarch" -> drawGoldenKingFrame(cx, cy, r, pulse)
            "mystic_oculus" -> drawVioletEyeFrame(cx, cy, r, pulse)
            "volt_tempest" -> drawLightningFrame(cx, cy, r, rotation, pulse)
            "emerald_matrix" -> drawGreenSpaceFrame(cx, cy, r, rotation, pulse)
            "seraphim_grace" -> drawAngelWingsFrame(cx, cy, r, pulse)
            "imperial_leo" -> drawLionFrame(cx, cy, r, pulse)
            "crimson_drake" -> drawCyberDragonFrame(cx, cy, r, pulse)
            "astral_diadem" -> drawStarTiaraFrame(cx, cy, r, rotation, pulse)
            "new_user", "new", "newbie", "welcome_new" -> drawNewUserFrame(cx, cy, r, pulse, rotation)
            else -> drawGoldenKingFrame(cx, cy, r, pulse)
        }
    }
}

// 1. HELICOPTER FRAME
private fun DrawScope.drawHelicopterFrame(cx: Float, cy: Float, r: Float, rotor: Float, pulse: Float) {
    val goldBrush = Brush.sweepGradient(
        listOf(
            Color(0xFFFFE082),
            Color(0xFFFFB300),
            Color(0xFFFFF9C4),
            Color(0xFFFF8F00),
            Color(0xFFFFE082)
        ),
        center = Offset(cx, cy)
    )

    // Outer golden decorative ring
    drawCircle(
        brush = goldBrush,
        radius = r + 5.dp.toPx(),
        center = Offset(cx, cy),
        style = Stroke(width = 6.dp.toPx())
    )
    drawCircle(
        color = Color(0xFFFFF59D),
        radius = r + 7.5.dp.toPx(),
        center = Offset(cx, cy),
        style = Stroke(width = 1.2.dp.toPx())
    )

    // Gold pearl beads along the ring
    for (i in 0 until 16) {
        val angle = Math.toRadians((i * 22.5).toDouble())
        val bx = cx + (r + 5.dp.toPx()) * cos(angle).toFloat()
        val by = cy + (r + 5.dp.toPx()) * sin(angle).toFloat()
        drawCircle(
            color = Color(0xFFFFD54F),
            radius = 2.2.dp.toPx(),
            center = Offset(bx, by)
        )
    }

    // Top Pink Heart Gem & Mini Crown
    val topY = cy - r - 6.dp.toPx()
    // Gold crown base on top
    val crownPath = Path().apply {
        moveTo(cx - 16.dp.toPx(), topY + 4.dp.toPx())
        lineTo(cx - 14.dp.toPx(), topY - 12.dp.toPx())
        lineTo(cx - 6.dp.toPx(), topY - 4.dp.toPx())
        lineTo(cx, topY - 16.dp.toPx())
        lineTo(cx + 6.dp.toPx(), topY - 4.dp.toPx())
        lineTo(cx + 14.dp.toPx(), topY - 12.dp.toPx())
        lineTo(cx + 16.dp.toPx(), topY + 4.dp.toPx())
        close()
    }
    drawPath(crownPath, Brush.verticalGradient(listOf(Color(0xFFFFF9C4), Color(0xFFFFB300))))
    drawPath(crownPath, Color(0xFFFF6F00), style = Stroke(width = 1.dp.toPx()))

    // Golden Gem
    drawCircle(
        brush = Brush.radialGradient(listOf(Color(0xFFFFD54F), Color(0xFFC5A059), Color(0xFFA37E30))),
        radius = 5.dp.toPx(),
        center = Offset(cx, topY - 2.dp.toPx())
    )
    drawCircle(color = Color.White, radius = 1.5.dp.toPx(), center = Offset(cx - 1.5.dp.toPx(), topY - 3.5.dp.toPx()))

    // Fluffy Pastel Clouds on 9 o'clock and 3 o'clock
    drawCloudPuff(this, cx - r - 4.dp.toPx(), cy - 6.dp.toPx(), 9.dp.toPx(), Color(0xFFFFF9E6))
    drawCloudPuff(this, cx + r + 4.dp.toPx(), cy - 6.dp.toPx(), 9.dp.toPx(), Color(0xFFFFF9E6))

    // Flying Golden Helicopter at Bottom
    val heliX = cx + (pulse * 4.dp.toPx() - 2.dp.toPx())
    val heliY = cy + r + 6.dp.toPx()

    // Helicopter body (cockpit)
    val bodyWidth = 32.dp.toPx()
    val bodyHeight = 16.dp.toPx()
    drawOval(
        brush = Brush.horizontalGradient(listOf(Color(0xFFFFD54F), Color(0xFFFF9800))),
        topLeft = Offset(heliX - bodyWidth / 2, heliY - bodyHeight / 2),
        size = Size(bodyWidth, bodyHeight)
    )
    drawOval(
        color = Color(0xFFE65100),
        topLeft = Offset(heliX - bodyWidth / 2, heliY - bodyHeight / 2),
        size = Size(bodyWidth, bodyHeight),
        style = Stroke(width = 1.2.dp.toPx())
    )

    // Cockpit windshield (cyan tinted)
    drawOval(
        brush = Brush.linearGradient(listOf(Color(0xFFE0F7FA), Color(0xFF4DD0E1))),
        topLeft = Offset(heliX - bodyWidth * 0.35f, heliY - bodyHeight * 0.35f),
        size = Size(bodyWidth * 0.4f, bodyHeight * 0.7f)
    )

    // Tail boom
    val tailPath = Path().apply {
        moveTo(heliX + bodyWidth * 0.3f, heliY - 2.dp.toPx())
        lineTo(heliX + bodyWidth * 0.75f, heliY - 6.dp.toPx())
        lineTo(heliX + bodyWidth * 0.75f, heliY - 14.dp.toPx())
        lineTo(heliX + bodyWidth * 0.7f, heliY - 14.dp.toPx())
        lineTo(heliX + bodyWidth * 0.65f, heliY - 4.dp.toPx())
        lineTo(heliX + bodyWidth * 0.3f, heliY + 2.dp.toPx())
        close()
    }
    drawPath(tailPath, Brush.linearGradient(listOf(Color(0xFFFFB300), Color(0xFFFF6F00))))

    // Tail rotor
    drawCircle(Color(0xFFFFD54F), radius = 2.dp.toPx(), center = Offset(heliX + bodyWidth * 0.75f, heliY - 12.dp.toPx()))

    // Landing skids
    drawLine(
        color = Color(0xFF795548),
        start = Offset(heliX - bodyWidth * 0.35f, heliY + bodyHeight * 0.65f),
        end = Offset(heliX + bodyWidth * 0.35f, heliY + bodyHeight * 0.65f),
        strokeWidth = 2.dp.toPx(),
        cap = StrokeCap.Round
    )
    drawLine(
        color = Color(0xFF795548),
        start = Offset(heliX - bodyWidth * 0.15f, heliY + bodyHeight * 0.35f),
        end = Offset(heliX - bodyWidth * 0.15f, heliY + bodyHeight * 0.65f),
        strokeWidth = 1.5.dp.toPx()
    )
    drawLine(
        color = Color(0xFF795548),
        start = Offset(heliX + bodyWidth * 0.15f, heliY + bodyHeight * 0.35f),
        end = Offset(heliX + bodyWidth * 0.15f, heliY + bodyHeight * 0.65f),
        strokeWidth = 1.5.dp.toPx()
    )

    // Main Rotor Mast & Spinning Rotor Blades in realistic horizontal perspective (not occupying round space)
    val mastX = heliX
    val mastY = heliY - bodyHeight / 2 - 3.dp.toPx()
    drawLine(
        color = Color(0xFF5D4037),
        start = Offset(mastX, heliY - bodyHeight / 2),
        end = Offset(mastX, mastY),
        strokeWidth = 2.dp.toPx()
    )

    // Semi-transparent rotor motion disc
    val bladeSpan = 18.dp.toPx()
    drawOval(
        brush = Brush.radialGradient(
            listOf(Color(0x66FFE082), Color(0x22FFB300), Color.Transparent),
            center = Offset(mastX, mastY),
            radius = bladeSpan
        ),
        topLeft = Offset(mastX - bladeSpan, mastY - 3.5.dp.toPx()),
        size = Size(bladeSpan * 2, 7.dp.toPx())
    )

    // Draw foreshortened 4-blade rotor spinning horizontally
    val rAngle = Math.toRadians(rotor.toDouble())
    for (i in 0 until 4) {
        val bAngle = rAngle + (i * Math.PI / 2.0)
        val tipX = mastX + (bladeSpan * cos(bAngle).toFloat())
        val tipY = mastY + (bladeSpan * 0.20f * sin(bAngle).toFloat())

        drawLine(
            brush = Brush.linearGradient(listOf(Color(0xFFFFE082), Color(0xFFFF9800))),
            start = Offset(mastX, mastY),
            end = Offset(tipX, tipY),
            strokeWidth = 2.2.dp.toPx(),
            cap = StrokeCap.Round
        )
        drawCircle(Color(0xFFFFF9C4), radius = 0.8.dp.toPx(), center = Offset(tipX, tipY))
    }

    // Rotor Hub Cap
    drawCircle(Color(0xFFE65100), radius = 2.5.dp.toPx(), center = Offset(mastX, mastY))
    drawCircle(Color(0xFFFFF9C4), radius = 1.2.dp.toPx(), center = Offset(mastX, mastY))

    // Fast-spinning Tail Rotor on the tail boom
    val tailRotorX = heliX + bodyWidth * 0.75f
    val tailRotorY = heliY - 12.dp.toPx()
    val tailAngle = Math.toRadians((rotor * 2.5f).toDouble())
    val tailSpan = 4.5.dp.toPx()
    val tCos = (tailSpan * cos(tailAngle).toFloat())
    val tSin = (tailSpan * sin(tailAngle).toFloat())
    drawLine(
        color = Color(0xFFFFD54F),
        start = Offset(tailRotorX - tCos, tailRotorY - tSin),
        end = Offset(tailRotorX + tCos, tailRotorY + tSin),
        strokeWidth = 1.5.dp.toPx(),
        cap = StrokeCap.Round
    )
    drawCircle(Color(0xFFE65100), radius = 1.5.dp.toPx(), center = Offset(tailRotorX, tailRotorY))
}

private fun drawCloudPuff(drawScope: DrawScope, cx: Float, cy: Float, size: Float, color: Color) {
    drawScope.apply {
        drawCircle(color, radius = size * 0.7f, center = Offset(cx - size * 0.5f, cy))
        drawCircle(color, radius = size * 0.9f, center = Offset(cx, cy - size * 0.3f))
        drawCircle(color, radius = size * 0.65f, center = Offset(cx + size * 0.5f, cy))
        drawCircle(color, radius = size * 0.5f, center = Offset(cx, cy + size * 0.2f))
    }
}

// 2. FLOWERS GARDEN FRAME
private fun DrawScope.drawFlowersGardenFrame(cx: Float, cy: Float, r: Float, pulse: Float) {
    // Green botanical stem wreath ring
    val stemBrush = Brush.sweepGradient(
        listOf(
            Color(0xFF66BB6A),
            Color(0xFF43A047),
            Color(0xFF81C784),
            Color(0xFF2E7D32),
            Color(0xFF66BB6A)
        ),
        center = Offset(cx, cy)
    )
    drawCircle(brush = stemBrush, radius = r + 4.dp.toPx(), center = Offset(cx, cy), style = Stroke(width = 4.dp.toPx()))

    // Botanical leaves around wreath
    val leafCount = 18
    for (i in 0 until leafCount) {
        val angleDeg = i * (360.0 / leafCount)
        val angle = Math.toRadians(angleDeg)
        val lx = cx + (r + 4.dp.toPx()) * cos(angle).toFloat()
        val ly = cy + (r + 4.dp.toPx()) * sin(angle).toFloat()

        val leafColor = if (i % 2 == 0) Color(0xFF4CAF50) else Color(0xFF81C784)
        rotate((angleDeg + 45).toFloat(), pivot = Offset(lx, ly)) {
            val leafPath = Path().apply {
                moveTo(lx, ly - 6.dp.toPx())
                cubicTo(lx + 4.dp.toPx(), ly - 3.dp.toPx(), lx + 4.dp.toPx(), ly + 3.dp.toPx(), lx, ly + 6.dp.toPx())
                cubicTo(lx - 4.dp.toPx(), ly + 3.dp.toPx(), lx - 4.dp.toPx(), ly - 3.dp.toPx(), lx, ly - 6.dp.toPx())
                close()
            }
            drawPath(leafPath, leafColor)
        }
    }

    // Blooming Roses & Blossoms
    val blossomAngles = listOf(20.0, 65.0, 110.0, 160.0, 205.0, 250.0, 295.0, 340.0)
    for ((idx, deg) in blossomAngles.withIndex()) {
        val angle = Math.toRadians(deg)
        val fx = cx + (r + 4.dp.toPx()) * cos(angle).toFloat()
        val fy = cy + (r + 4.dp.toPx()) * sin(angle).toFloat()

        val roseGradient = when (idx % 3) {
            0 -> listOf(Color(0xFFFFE082), Color(0xFFFFB300), Color(0xFFC5A059)) // Warm Amber Blossom
            1 -> listOf(Color(0xFFFFF59D), Color(0xFFFFD54F), Color(0xFFFFB300)) // Golden Blossom
            else -> listOf(Color(0xFFD6B574), Color(0xFFC5A059), Color(0xFFA37E30)) // Bronze Rose
        }
        val petalRadius = 4.5.dp.toPx()
        // Draw multi-petal rose
        for (p in 0 until 5) {
            val pAngle = Math.toRadians((p * 72.0))
            val px = fx + (petalRadius * 0.6f) * cos(pAngle).toFloat()
            val py = fy + (petalRadius * 0.6f) * sin(pAngle).toFloat()
            drawCircle(roseGradient[0], radius = petalRadius * 0.55f, center = Offset(px, py))
        }
        drawCircle(brush = Brush.radialGradient(roseGradient), radius = petalRadius * 0.7f, center = Offset(fx, fy))
        drawCircle(color = Color(0xFFFFF9C4), radius = petalRadius * 0.25f, center = Offset(fx, fy))
    }

    // Red / Coral Berries Clusters
    val berryAngles = listOf(42.0, 88.0, 135.0, 182.0, 228.0, 275.0, 318.0)
    for (deg in berryAngles) {
        val angle = Math.toRadians(deg)
        val bx = cx + (r + 6.dp.toPx()) * cos(angle).toFloat()
        val by = cy + (r + 6.dp.toPx()) * sin(angle).toFloat()

        drawCircle(Color(0xFFE53935), radius = 2.2.dp.toPx(), center = Offset(bx, by))
        drawCircle(Color(0xFFFF7043), radius = 1.8.dp.toPx(), center = Offset(bx + 2.dp.toPx(), by - 2.dp.toPx()))
    }
}

// 3. GOLDEN KING FRAME
private fun DrawScope.drawGoldenKingFrame(cx: Float, cy: Float, r: Float, pulse: Float) {
    // Blazing golden flame solar aura
    val flameCount = 20
    for (i in 0 until flameCount) {
        val angleDeg = i * (360.0 / flameCount)
        val angle = Math.toRadians(angleDeg)
        val flameLen = if (i % 2 == 0) (10.dp.toPx() + pulse * 4.dp.toPx()) else (6.dp.toPx())
        val startR = r + 2.dp.toPx()
        val endR = startR + flameLen

        val x1 = cx + startR * cos(angle).toFloat()
        val y1 = cy + startR * sin(angle).toFloat()
        val x2 = cx + endR * cos(angle).toFloat()
        val y2 = cy + endR * sin(angle).toFloat()

        drawLine(
            brush = Brush.linearGradient(
                listOf(Color(0xFFFFD54F), Color(0xFFFF9800), Color(0xFFFF3D00).copy(alpha = 0.2f)),
                start = Offset(x1, y1),
                end = Offset(x2, y2)
            ),
            start = Offset(x1, y1),
            end = Offset(x2, y2),
            strokeWidth = 3.dp.toPx(),
            cap = StrokeCap.Round
        )
    }

    // Main Golden Solar Ring
    val goldBrush = Brush.sweepGradient(
        listOf(
            Color(0xFFFFF9C4),
            Color(0xFFFFD54F),
            Color(0xFFFFB300),
            Color(0xFFFF8F00),
            Color(0xFFFFF9C4)
        ),
        center = Offset(cx, cy)
    )
    drawCircle(brush = goldBrush, radius = r + 4.dp.toPx(), center = Offset(cx, cy), style = Stroke(width = 5.dp.toPx()))

    // Top Sovereign King Crown
    val crownY = cy - r - 8.dp.toPx()
    val crownPath = Path().apply {
        moveTo(cx - 20.dp.toPx(), crownY + 8.dp.toPx())
        lineTo(cx - 18.dp.toPx(), crownY - 14.dp.toPx())
        lineTo(cx - 8.dp.toPx(), crownY - 4.dp.toPx())
        lineTo(cx, crownY - 20.dp.toPx())
        lineTo(cx + 8.dp.toPx(), crownY - 4.dp.toPx())
        lineTo(cx + 18.dp.toPx(), crownY - 14.dp.toPx())
        lineTo(cx + 20.dp.toPx(), crownY + 8.dp.toPx())
        close()
    }
    drawPath(
        crownPath,
        Brush.verticalGradient(listOf(Color(0xFFFFF59D), Color(0xFFFFB300), Color(0xFFE65100)))
    )
    drawPath(crownPath, Color(0xFFFF6F00), style = Stroke(width = 1.2.dp.toPx()))

    // Crown Center Ruby Jewel
    drawCircle(
        brush = Brush.radialGradient(listOf(Color(0xFFFF5252), Color(0xFFB71C1C))),
        radius = 4.dp.toPx(),
        center = Offset(cx, crownY - 4.dp.toPx())
    )

    // Bottom Royal Baroque Gold Shield Crest
    val crestY = cy + r + 4.dp.toPx()
    val shieldPath = Path().apply {
        moveTo(cx - 24.dp.toPx(), crestY - 4.dp.toPx())
        cubicTo(cx - 16.dp.toPx(), crestY + 6.dp.toPx(), cx - 10.dp.toPx(), crestY + 14.dp.toPx(), cx, crestY + 18.dp.toPx())
        cubicTo(cx + 10.dp.toPx(), crestY + 14.dp.toPx(), cx + 16.dp.toPx(), crestY + 6.dp.toPx(), cx + 24.dp.toPx(), crestY - 4.dp.toPx())
        cubicTo(cx + 12.dp.toPx(), crestY, cx - 12.dp.toPx(), crestY, cx - 24.dp.toPx(), crestY - 4.dp.toPx())
        close()
    }
    drawPath(shieldPath, Brush.verticalGradient(listOf(Color(0xFFFFD54F), Color(0xFFFF8F00))))
    drawPath(shieldPath, Color(0xFFFF6F00), style = Stroke(width = 1.2.dp.toPx()))

    // Center Gold Star in Crest
    drawCircle(Color(0xFFFFF9C4), radius = 3.dp.toPx(), center = Offset(cx, crestY + 5.dp.toPx()))
}

// 4. VIOLET EYE FRAME
private fun DrawScope.drawVioletEyeFrame(cx: Float, cy: Float, r: Float, pulse: Float) {
    // Amber flame wing flourishes sweeping up both sides
    val leftFlame = Path().apply {
        moveTo(cx - r - 2.dp.toPx(), cy + r * 0.4f)
        cubicTo(cx - r - 16.dp.toPx(), cy, cx - r - 18.dp.toPx(), cy - r * 0.5f, cx - r * 0.4f, cy - r - 8.dp.toPx())
        cubicTo(cx - r - 8.dp.toPx(), cy - r * 0.3f, cx - r - 6.dp.toPx(), cy + r * 0.1f, cx - r - 2.dp.toPx(), cy + r * 0.4f)
        close()
    }
    val rightFlame = Path().apply {
        moveTo(cx + r + 2.dp.toPx(), cy + r * 0.4f)
        cubicTo(cx + r + 16.dp.toPx(), cy, cx + r + 18.dp.toPx(), cy - r * 0.5f, cx + r * 0.4f, cy - r - 8.dp.toPx())
        cubicTo(cx + r + 8.dp.toPx(), cy - r * 0.3f, cx + r + 6.dp.toPx(), cy + r * 0.1f, cx + r + 2.dp.toPx(), cy + r * 0.4f)
        close()
    }
    val flameBrush = Brush.verticalGradient(listOf(Color(0xFFFFD54F), Color(0xFFFF9800), Color(0xFFFF5722)))
    drawPath(leftFlame, flameBrush)
    drawPath(rightFlame, flameBrush)

    // Golden halo ring
    drawCircle(
        brush = Brush.sweepGradient(listOf(Color(0xFFFFE082), Color(0xFFFFB300), Color(0xFFFF8F00), Color(0xFFFFE082)), center = Offset(cx, cy)),
        radius = r + 4.dp.toPx(),
        center = Offset(cx, cy),
        style = Stroke(width = 4.5.dp.toPx())
    )

    // Top Golden Tiara Crest
    val topY = cy - r - 6.dp.toPx()
    val tiaraPath = Path().apply {
        moveTo(cx - 14.dp.toPx(), topY + 4.dp.toPx())
        lineTo(cx - 10.dp.toPx(), topY - 10.dp.toPx())
        lineTo(cx, topY - 16.dp.toPx())
        lineTo(cx + 10.dp.toPx(), topY - 10.dp.toPx())
        lineTo(cx + 14.dp.toPx(), topY + 4.dp.toPx())
        close()
    }
    drawPath(tiaraPath, Brush.verticalGradient(listOf(Color(0xFFFFF59D), Color(0xFFFFB300))))

    // Bottom Glowing Violet/Magenta Crystal Eye Jewel
    val eyeY = cy + r + 5.dp.toPx()
    // Outer golden eye bezel
    val bezelPath = Path().apply {
        moveTo(cx - 16.dp.toPx(), eyeY)
        cubicTo(cx - 8.dp.toPx(), eyeY - 8.dp.toPx(), cx + 8.dp.toPx(), eyeY - 8.dp.toPx(), cx + 16.dp.toPx(), eyeY)
        cubicTo(cx + 8.dp.toPx(), eyeY + 10.dp.toPx(), cx - 8.dp.toPx(), eyeY + 10.dp.toPx(), cx - 16.dp.toPx(), eyeY)
        close()
    }
    drawPath(bezelPath, Brush.linearGradient(listOf(Color(0xFFFFD54F), Color(0xFFFF8F00))))

    // Glowing Violet Iris & Pupil
    drawCircle(
        brush = Brush.radialGradient(listOf(Color(0xFFEA80FC), Color(0xFFD500F9), Color(0xFF4A148C))),
        radius = 6.dp.toPx() + (pulse * 1.5.dp.toPx()),
        center = Offset(cx, eyeY)
    )
    drawCircle(Color.White, radius = 2.dp.toPx(), center = Offset(cx - 1.5.dp.toPx(), eyeY - 1.5.dp.toPx()))
}

// 5. LIGHTNING FRAME
private fun DrawScope.drawLightningFrame(cx: Float, cy: Float, r: Float, rotation: Float, pulse: Float) {
    // Dark gunmetal armor ring
    drawCircle(
        color = Color(0xFF1E293B),
        radius = r + 6.dp.toPx(),
        center = Offset(cx, cy),
        style = Stroke(width = 6.dp.toPx())
    )

    // Glowing Electric Cyan Plasma Ring
    drawCircle(
        brush = Brush.sweepGradient(
            listOf(
                Color(0xFF00E5FF),
                Color(0xFF00B0FF),
                Color(0xFF80D8FF),
                Color(0xFF0091EA),
                Color(0xFF00E5FF)
            ),
            center = Offset(cx, cy)
        ),
        radius = r + 5.dp.toPx(),
        center = Offset(cx, cy),
        style = Stroke(width = 2.5.dp.toPx())
    )

    // Segmented Cyber Carbon Plates
    rotate(rotation, pivot = Offset(cx, cy)) {
        for (i in 0 until 4) {
            val angle = i * 90f
            rotate(angle, pivot = Offset(cx, cy)) {
                drawArc(
                    color = Color(0xFF0F172A),
                    startAngle = -20f,
                    sweepAngle = 40f,
                    useCenter = false,
                    topLeft = Offset(cx - r - 8.dp.toPx(), cy - r - 8.dp.toPx()),
                    size = Size((r + 8.dp.toPx()) * 2, (r + 8.dp.toPx()) * 2),
                    style = Stroke(width = 4.dp.toPx(), cap = StrokeCap.Round)
                )
                // Cyan tech node
                val nodeX = cx + (r + 7.dp.toPx()) * cos(Math.toRadians(0.0)).toFloat()
                val nodeY = cy + (r + 7.dp.toPx()) * sin(Math.toRadians(0.0)).toFloat()
                drawCircle(Color(0xFF00E5FF), radius = 2.5.dp.toPx(), center = Offset(nodeX, nodeY))
            }
        }
    }

    // Jagged electric lightning sparks
    val sparkPath = Path().apply {
        val s1 = cx - r - 3.dp.toPx()
        moveTo(s1, cy - 10.dp.toPx())
        lineTo(s1 - 4.dp.toPx(), cy)
        lineTo(s1 + 2.dp.toPx(), cy + 2.dp.toPx())
        lineTo(s1 - 3.dp.toPx(), cy + 12.dp.toPx())
    }
    drawPath(sparkPath, Color(0xFF00E5FF), style = Stroke(width = 2.dp.toPx(), cap = StrokeCap.Round))

    val sparkPath2 = Path().apply {
        val s2 = cx + r + 3.dp.toPx()
        moveTo(s2, cy - 8.dp.toPx())
        lineTo(s2 + 5.dp.toPx(), cy)
        lineTo(s2 - 2.dp.toPx(), cy + 3.dp.toPx())
        lineTo(s2 + 4.dp.toPx(), cy + 10.dp.toPx())
    }
    drawPath(sparkPath2, Color(0xFF80D8FF), style = Stroke(width = 2.dp.toPx(), cap = StrokeCap.Round))
}

// 6. GREEN SPACE FRAME
private fun DrawScope.drawGreenSpaceFrame(cx: Float, cy: Float, r: Float, rotation: Float, pulse: Float) {
    // Outer emerald holographic glow
    drawCircle(
        color = Color(0xFF00E676).copy(alpha = 0.25f + pulse * 0.2f),
        radius = r + 9.dp.toPx(),
        center = Offset(cx, cy),
        style = Stroke(width = 4.dp.toPx())
    )

    // Holographic sci-fi energy ring
    val greenBrush = Brush.sweepGradient(
        listOf(
            Color(0xFFB9F6CA),
            Color(0xFF00E676),
            Color(0xFF00C853),
            Color(0xFF69F0AE),
            Color(0xFFB9F6CA)
        ),
        center = Offset(cx, cy)
    )
    drawCircle(
        brush = greenBrush,
        radius = r + 5.dp.toPx(),
        center = Offset(cx, cy),
        style = Stroke(width = 4.5.dp.toPx())
    )

    // Orbital Sci-Fi Notches & Tech Nodes
    rotate(rotation, pivot = Offset(cx, cy)) {
        for (i in 0 until 6) {
            val angle = i * 60f
            rotate(angle, pivot = Offset(cx, cy)) {
                drawArc(
                    color = Color(0xFFB9F6CA),
                    startAngle = -12f,
                    sweepAngle = 24f,
                    useCenter = false,
                    topLeft = Offset(cx - r - 6.dp.toPx(), cy - r - 6.dp.toPx()),
                    size = Size((r + 6.dp.toPx()) * 2, (r + 6.dp.toPx()) * 2),
                    style = Stroke(width = 2.dp.toPx(), cap = StrokeCap.Round)
                )
                // Sci-fi Node
                val nx = cx + (r + 5.dp.toPx()) * cos(Math.toRadians(0.0)).toFloat()
                val ny = cy + (r + 5.dp.toPx()) * sin(Math.toRadians(0.0)).toFloat()
                drawCircle(Color.White, radius = 2.dp.toPx(), center = Offset(nx, ny))
            }
        }
    }
}

// 7. ANGEL WINGS FRAME
private fun DrawScope.drawAngelWingsFrame(cx: Float, cy: Float, r: Float, pulse: Float) {
    // Divine heavenly golden halo ring
    val haloBrush = Brush.sweepGradient(
        listOf(Color(0xFFFFF9C4), Color(0xFFFFD54F), Color(0xFFFFB300), Color(0xFFFFF9C4)),
        center = Offset(cx, cy)
    )
    drawCircle(brush = haloBrush, radius = r + 4.dp.toPx(), center = Offset(cx, cy), style = Stroke(width = 4.dp.toPx()))

    // Left Wing (layered pure white & lavender feathers)
    val leftWing = Path().apply {
        moveTo(cx - r, cy - r * 0.4f)
        cubicTo(cx - r - 20.dp.toPx(), cy - r * 0.8f, cx - r - 24.dp.toPx(), cy + r * 0.2f, cx - r - 6.dp.toPx(), cy + r * 0.7f)
        cubicTo(cx - r - 12.dp.toPx(), cy + r * 0.3f, cx - r - 8.dp.toPx(), cy - r * 0.1f, cx - r, cy - r * 0.4f)
        close()
    }
    val rightWing = Path().apply {
        moveTo(cx + r, cy - r * 0.4f)
        cubicTo(cx + r + 20.dp.toPx(), cy - r * 0.8f, cx + r + 24.dp.toPx(), cy + r * 0.2f, cx + r + 6.dp.toPx(), cy + r * 0.7f)
        cubicTo(cx + r + 12.dp.toPx(), cy + r * 0.3f, cx + r + 8.dp.toPx(), cy - r * 0.1f, cx + r, cy - r * 0.4f)
        close()
    }
    val wingBrush = Brush.verticalGradient(listOf(Color.White, Color(0xFFEDE7F6), Color(0xFFD1C4E9)))
    drawPath(leftWing, wingBrush)
    drawPath(leftWing, Color(0xFFB39DDB), style = Stroke(width = 1.dp.toPx()))
    drawPath(rightWing, wingBrush)
    drawPath(rightWing, Color(0xFFB39DDB), style = Stroke(width = 1.dp.toPx()))

    // Bottom Royal Purple Ribbon Bow
    val ribbonY = cy + r + 5.dp.toPx()
    val ribbonLeft = Path().apply {
        moveTo(cx, ribbonY)
        lineTo(cx - 14.dp.toPx(), ribbonY + 8.dp.toPx())
        lineTo(cx - 10.dp.toPx(), ribbonY + 16.dp.toPx())
        lineTo(cx - 4.dp.toPx(), ribbonY + 10.dp.toPx())
        close()
    }
    val ribbonRight = Path().apply {
        moveTo(cx, ribbonY)
        lineTo(cx + 14.dp.toPx(), ribbonY + 8.dp.toPx())
        lineTo(cx + 10.dp.toPx(), ribbonY + 16.dp.toPx())
        lineTo(cx + 4.dp.toPx(), ribbonY + 10.dp.toPx())
        close()
    }
    drawPath(ribbonLeft, Brush.verticalGradient(listOf(Color(0xFF7E57C2), Color(0xFF512DA8))))
    drawPath(ribbonRight, Brush.verticalGradient(listOf(Color(0xFF7E57C2), Color(0xFF512DA8))))

    // Center Gold Brooch in Ribbon
    drawCircle(
        brush = Brush.radialGradient(listOf(Color(0xFFFFF59D), Color(0xFFFFB300))),
        radius = 4.dp.toPx(),
        center = Offset(cx, ribbonY)
    )
}

// 8. LION FRAME
private fun DrawScope.drawLionFrame(cx: Float, cy: Float, r: Float, pulse: Float) {
    // Ornate baroque gold leaf filigree ring
    val lionGoldBrush = Brush.sweepGradient(
        listOf(Color(0xFFFFE082), Color(0xFFFFB300), Color(0xFFFF8F00), Color(0xFFFFA000), Color(0xFFFFE082)),
        center = Offset(cx, cy)
    )
    drawCircle(brush = lionGoldBrush, radius = r + 5.dp.toPx(), center = Offset(cx, cy), style = Stroke(width = 5.dp.toPx()))

    // Top Imperial Golden Lion Head Sculpture
    val lionY = cy - r - 10.dp.toPx()
    // Lion Mane
    val manePath = Path().apply {
        moveTo(cx - 18.dp.toPx(), lionY + 10.dp.toPx())
        cubicTo(cx - 24.dp.toPx(), lionY - 4.dp.toPx(), cx - 14.dp.toPx(), lionY - 18.dp.toPx(), cx, lionY - 20.dp.toPx())
        cubicTo(cx + 14.dp.toPx(), lionY - 18.dp.toPx(), cx + 24.dp.toPx(), lionY - 4.dp.toPx(), cx + 18.dp.toPx(), lionY + 10.dp.toPx())
        close()
    }
    drawPath(manePath, Brush.verticalGradient(listOf(Color(0xFFFFE082), Color(0xFFFFB300), Color(0xFFE65100))))
    drawPath(manePath, Color(0xFFFF6F00), style = Stroke(width = 1.dp.toPx()))

    // Lion Face (Muzzle & Nose)
    drawCircle(Color(0xFFFFD54F), radius = 7.dp.toPx(), center = Offset(cx, lionY - 4.dp.toPx()))
    // Lion Nose & Eyes
    drawCircle(Color(0xFF5D4037), radius = 1.5.dp.toPx(), center = Offset(cx, lionY - 2.dp.toPx()))
    drawCircle(Color(0xFF5D4037), radius = 1.dp.toPx(), center = Offset(cx - 3.dp.toPx(), lionY - 6.dp.toPx()))
    drawCircle(Color(0xFF5D4037), radius = 1.dp.toPx(), center = Offset(cx + 3.dp.toPx(), lionY - 6.dp.toPx()))

    // Bottom Baroque Gold Tassel
    val tasselY = cy + r + 5.dp.toPx()
    val tasselPath = Path().apply {
        moveTo(cx - 16.dp.toPx(), tasselY - 2.dp.toPx())
        cubicTo(cx - 10.dp.toPx(), tasselY + 8.dp.toPx(), cx - 6.dp.toPx(), tasselY + 14.dp.toPx(), cx, tasselY + 16.dp.toPx())
        cubicTo(cx + 6.dp.toPx(), tasselY + 14.dp.toPx(), cx + 10.dp.toPx(), tasselY + 8.dp.toPx(), cx + 16.dp.toPx(), tasselY - 2.dp.toPx())
        close()
    }
    drawPath(tasselPath, Brush.verticalGradient(listOf(Color(0xFFFFD54F), Color(0xFFFF8F00))))
}

// 9. CYBER DRAGON FRAME
private fun DrawScope.drawCyberDragonFrame(cx: Float, cy: Float, r: Float, pulse: Float) {
    val dragonBrush = Brush.sweepGradient(
        listOf(Color(0xFFFF5252), Color(0xFFFF1744), Color(0xFFD50000), Color(0xFFFF8A80), Color(0xFFFF5252)),
        center = Offset(cx, cy)
    )
    drawCircle(brush = dragonBrush, radius = r + 4.5.dp.toPx(), center = Offset(cx, cy), style = Stroke(width = 4.5.dp.toPx()))

    // Top Dragon Horns
    val topY = cy - r - 6.dp.toPx()
    val leftHorn = Path().apply {
        moveTo(cx - 8.dp.toPx(), topY + 4.dp.toPx())
        cubicTo(cx - 16.dp.toPx(), topY - 8.dp.toPx(), cx - 22.dp.toPx(), topY - 14.dp.toPx(), cx - 18.dp.toPx(), topY - 18.dp.toPx())
        cubicTo(cx - 14.dp.toPx(), topY - 10.dp.toPx(), cx - 6.dp.toPx(), topY - 4.dp.toPx(), cx - 4.dp.toPx(), topY + 4.dp.toPx())
        close()
    }
    val rightHorn = Path().apply {
        moveTo(cx + 8.dp.toPx(), topY + 4.dp.toPx())
        cubicTo(cx + 16.dp.toPx(), topY - 8.dp.toPx(), cx + 22.dp.toPx(), topY - 14.dp.toPx(), cx + 18.dp.toPx(), topY - 18.dp.toPx())
        cubicTo(cx + 14.dp.toPx(), topY - 10.dp.toPx(), cx + 6.dp.toPx(), topY - 4.dp.toPx(), cx + 4.dp.toPx(), topY + 4.dp.toPx())
        close()
    }
    drawPath(leftHorn, Brush.linearGradient(listOf(Color(0xFFFF1744), Color(0xFFFFD54F))))
    drawPath(rightHorn, Brush.linearGradient(listOf(Color(0xFFFF1744), Color(0xFFFFD54F))))
}

// 10. STAR TIARA FRAME
private fun DrawScope.drawStarTiaraFrame(cx: Float, cy: Float, r: Float, rotation: Float, pulse: Float) {
    val tiaraBrush = Brush.sweepGradient(
        listOf(Color(0xFFFFF9C4), Color(0xFFFFD54F), Color(0xFFE0F7FA), Color(0xFF80D8FF), Color(0xFFFFF9C4)),
        center = Offset(cx, cy)
    )
    drawCircle(brush = tiaraBrush, radius = r + 4.dp.toPx(), center = Offset(cx, cy), style = Stroke(width = 3.5.dp.toPx()))

    // Rotating sparkling star gems
    rotate(rotation, pivot = Offset(cx, cy)) {
        for (i in 0 until 8) {
            val angle = Math.toRadians((i * 45.0))
            val sx = cx + (r + 4.dp.toPx()) * cos(angle).toFloat()
            val sy = cy + (r + 4.dp.toPx()) * sin(angle).toFloat()
            drawCircle(Color.White, radius = 2.dp.toPx(), center = Offset(sx, sy))
        }
    }
}

// 11. VIP4 FRAME (Violet/Purple crystal wings with top crystal & VIP4 badge)
private fun DrawScope.drawVip4Frame(cx: Float, cy: Float, r: Float, pulse: Float) {
    val purpleBrush = Brush.sweepGradient(
        listOf(Color(0xFFE9D5FF), Color(0xFFA855F7), Color(0xFFC084FC), Color(0xFF7E22CE), Color(0xFFE9D5FF)),
        center = Offset(cx, cy)
    )
    drawCircle(brush = purpleBrush, radius = r + 4.dp.toPx(), center = Offset(cx, cy), style = Stroke(width = 4.dp.toPx()))

    // Left Wing
    val leftWing = Path().apply {
        moveTo(cx - r, cy)
        cubicTo(cx - r - 12.dp.toPx(), cy - 8.dp.toPx(), cx - r - 16.dp.toPx(), cy + 12.dp.toPx(), cx - r - 4.dp.toPx(), cy + 18.dp.toPx())
        cubicTo(cx - r - 8.dp.toPx(), cy + 12.dp.toPx(), cx - r - 6.dp.toPx(), cy + 4.dp.toPx(), cx - r, cy)
        close()
    }
    drawPath(leftWing, Brush.linearGradient(listOf(Color(0xFFC084FC), Color(0xFFA855F7))))

    // Right Wing
    val rightWing = Path().apply {
        moveTo(cx + r, cy)
        cubicTo(cx + r + 12.dp.toPx(), cy - 8.dp.toPx(), cx + r + 16.dp.toPx(), cy + 12.dp.toPx(), cx + r + 4.dp.toPx(), cy + 18.dp.toPx())
        cubicTo(cx + r + 8.dp.toPx(), cy + 12.dp.toPx(), cx + r + 6.dp.toPx(), cy + 4.dp.toPx(), cx + r, cy)
        close()
    }
    drawPath(rightWing, Brush.linearGradient(listOf(Color(0xFFC084FC), Color(0xFFA855F7))))

    // Top Crystal Crown Gem
    val topY = cy - r - 6.dp.toPx()
    val crystal = Path().apply {
        moveTo(cx, topY - 10.dp.toPx() - (pulse * 2.dp.toPx()))
        lineTo(cx + 7.dp.toPx(), topY - 3.dp.toPx())
        lineTo(cx, topY + 4.dp.toPx())
        lineTo(cx - 7.dp.toPx(), topY - 3.dp.toPx())
        close()
    }
    drawPath(crystal, Brush.verticalGradient(listOf(Color(0xFFF3E8FF), Color(0xFFA855F7))))

    // Bottom "VIP4" badge plate
    val badgeY = cy + r - 1.dp.toPx()
    val badgePath = Path().apply {
        moveTo(cx - 20.dp.toPx(), badgeY)
        lineTo(cx - 16.dp.toPx(), badgeY + 12.dp.toPx())
        lineTo(cx + 16.dp.toPx(), badgeY + 12.dp.toPx())
        lineTo(cx + 20.dp.toPx(), badgeY)
        close()
    }
    drawPath(badgePath, Brush.horizontalGradient(listOf(Color(0xFF9333EA), Color(0xFF7E22CE))))
    drawPath(badgePath, Color(0xFFE9D5FF), style = Stroke(width = 1.dp.toPx()))
}

// 12. SVIP1 FRAME (Platinum / Diamond wings with SVIP1 emblem)
private fun DrawScope.drawSvip1Frame(cx: Float, cy: Float, r: Float, pulse: Float) {
    val silverBrush = Brush.sweepGradient(
        listOf(Color(0xFFFFFFFF), Color(0xFF93C5FD), Color(0xFFE2E8F0), Color(0xFF60A5FA), Color(0xFFFFFFFF)),
        center = Offset(cx, cy)
    )
    drawCircle(brush = silverBrush, radius = r + 4.5.dp.toPx(), center = Offset(cx, cy), style = Stroke(width = 4.dp.toPx()))

    // Top Diamond Crown
    val topY = cy - r - 6.dp.toPx()
    val crown = Path().apply {
        moveTo(cx - 12.dp.toPx(), topY + 4.dp.toPx())
        lineTo(cx - 8.dp.toPx(), topY - 8.dp.toPx())
        lineTo(cx, topY - 14.dp.toPx() - (pulse * 2.dp.toPx()))
        lineTo(cx + 8.dp.toPx(), topY - 8.dp.toPx())
        lineTo(cx + 12.dp.toPx(), topY + 4.dp.toPx())
        close()
    }
    drawPath(crown, Brush.verticalGradient(listOf(Color.White, Color(0xFF60A5FA))))

    // Bottom Wings Crest
    val bottomY = cy + r + 2.dp.toPx()
    val bottomWings = Path().apply {
        moveTo(cx - 24.dp.toPx(), bottomY + 8.dp.toPx())
        lineTo(cx, bottomY - 2.dp.toPx())
        lineTo(cx + 24.dp.toPx(), bottomY + 8.dp.toPx())
        lineTo(cx, bottomY + 14.dp.toPx())
        close()
    }
    drawPath(bottomWings, Brush.horizontalGradient(listOf(Color(0xFF93C5FD), Color(0xFF2563EB), Color(0xFF93C5FD))))
}

// 13. ACQUAINTANCE FRAME (Bronze / Rose-gold star wings)
private fun DrawScope.drawAcquaintanceFrame(cx: Float, cy: Float, r: Float, pulse: Float) {
    val roseGoldBrush = Brush.sweepGradient(
        listOf(Color(0xFFFFEDD5), Color(0xFFFDBA74), Color(0xFFFB923C), Color(0xFFEA580C), Color(0xFFFFEDD5)),
        center = Offset(cx, cy)
    )
    drawCircle(brush = roseGoldBrush, radius = r + 4.dp.toPx(), center = Offset(cx, cy), style = Stroke(width = 3.5.dp.toPx()))

    // Side Feather Wing Arcs
    for (i in 0 until 6) {
        val angleL = Math.toRadians((120.0 + i * 20.0))
        val wxL = cx + (r + 6.dp.toPx()) * cos(angleL).toFloat()
        val wyL = cy + (r + 6.dp.toPx()) * sin(angleL).toFloat()
        drawCircle(Color(0xFFFDBA74), radius = 2.5.dp.toPx(), center = Offset(wxL, wyL))

        val angleR = Math.toRadians((60.0 - i * 20.0))
        val wxR = cx + (r + 6.dp.toPx()) * cos(angleR).toFloat()
        val wyR = cy + (r + 6.dp.toPx()) * sin(angleR).toFloat()
        drawCircle(Color(0xFFFDBA74), radius = 2.5.dp.toPx(), center = Offset(wxR, wyR))
    }

    // Top Star
    val topY = cy - r - 6.dp.toPx()
    val topStar = Path().apply {
        moveTo(cx, topY - 10.dp.toPx())
        lineTo(cx + 4.dp.toPx(), topY - 2.dp.toPx())
        lineTo(cx + 10.dp.toPx(), topY)
        lineTo(cx + 4.dp.toPx(), topY + 2.dp.toPx())
        lineTo(cx, topY + 10.dp.toPx())
        lineTo(cx - 4.dp.toPx(), topY + 2.dp.toPx())
        lineTo(cx - 10.dp.toPx(), topY)
        lineTo(cx - 4.dp.toPx(), topY - 2.dp.toPx())
        close()
    }
    drawPath(topStar, Brush.radialGradient(listOf(Color.White, Color(0xFFFB923C)), center = Offset(cx, topY), radius = 10.dp.toPx()))

    // Bottom Star Crest
    val botY = cy + r + 4.dp.toPx()
    drawCircle(Color(0xFFEA580C), radius = 6.dp.toPx(), center = Offset(cx, botY))
    drawCircle(Color(0xFFFFEDD5), radius = 3.dp.toPx(), center = Offset(cx, botY))
}

// 14. VIP1 FRAME (Silver & Azure Blue Wings)
private fun DrawScope.drawVip1Frame(cx: Float, cy: Float, r: Float, pulse: Float) {
    val silverBrush = Brush.sweepGradient(
        listOf(Color(0xFFE2E8F0), Color(0xFF38BDF8), Color(0xFFCBD5E1), Color(0xFF0284C7), Color(0xFFE2E8F0)),
        center = Offset(cx, cy)
    )
    drawCircle(brush = silverBrush, radius = r + 3.5.dp.toPx(), center = Offset(cx, cy), style = Stroke(width = 3.5.dp.toPx()))

    // Top Diamond
    val topY = cy - r - 4.dp.toPx()
    val diamond = Path().apply {
        moveTo(cx, topY - 6.dp.toPx())
        lineTo(cx + 4.dp.toPx(), topY)
        lineTo(cx, topY + 6.dp.toPx())
        lineTo(cx - 4.dp.toPx(), topY)
        close()
    }
    drawPath(diamond, Color(0xFF38BDF8))

    // Bottom Badge Plate
    val badgeY = cy + r
    val badge = Path().apply {
        moveTo(cx - 16.dp.toPx(), badgeY)
        lineTo(cx - 12.dp.toPx(), badgeY + 10.dp.toPx())
        lineTo(cx + 12.dp.toPx(), badgeY + 10.dp.toPx())
        lineTo(cx + 16.dp.toPx(), badgeY)
        close()
    }
    drawPath(badge, Brush.horizontalGradient(listOf(Color(0xFF0284C7), Color(0xFF0369A1))))
    drawPath(badge, Color.White, style = Stroke(width = 1.dp.toPx()))
}

// 15. VIP3 FRAME (Royal Sapphire Flame Wings)
private fun DrawScope.drawVip3Frame(cx: Float, cy: Float, r: Float, pulse: Float) {
    val blueBrush = Brush.sweepGradient(
        listOf(Color(0xFFBFDBFE), Color(0xFF3B82F6), Color(0xFF1D4ED8), Color(0xFF60A5FA), Color(0xFFBFDBFE)),
        center = Offset(cx, cy)
    )
    drawCircle(brush = blueBrush, radius = r + 4.dp.toPx(), center = Offset(cx, cy), style = Stroke(width = 4.dp.toPx()))

    // Side Wings
    val leftWing = Path().apply {
        moveTo(cx - r - 2.dp.toPx(), cy - 6.dp.toPx())
        cubicTo(cx - r - 14.dp.toPx(), cy - 12.dp.toPx(), cx - r - 18.dp.toPx(), cy + 10.dp.toPx(), cx - r, cy + 14.dp.toPx())
        close()
    }
    drawPath(leftWing, Brush.linearGradient(listOf(Color(0xFF60A5FA), Color(0xFF1D4ED8))))

    val rightWing = Path().apply {
        moveTo(cx + r + 2.dp.toPx(), cy - 6.dp.toPx())
        cubicTo(cx + r + 14.dp.toPx(), cy - 12.dp.toPx(), cx + r + 18.dp.toPx(), cy + 10.dp.toPx(), cx + r, cy + 14.dp.toPx())
        close()
    }
    drawPath(rightWing, Brush.linearGradient(listOf(Color(0xFF60A5FA), Color(0xFF1D4ED8))))

    // Bottom Badge Plate
    val badgeY = cy + r
    val badge = Path().apply {
        moveTo(cx - 18.dp.toPx(), badgeY)
        lineTo(cx - 14.dp.toPx(), badgeY + 11.dp.toPx())
        lineTo(cx + 14.dp.toPx(), badgeY + 11.dp.toPx())
        lineTo(cx + 18.dp.toPx(), badgeY)
        close()
    }
    drawPath(badge, Brush.horizontalGradient(listOf(Color(0xFF2563EB), Color(0xFF1D4ED8))))
    drawPath(badge, Color.White, style = Stroke(width = 1.dp.toPx()))
}

// 16. NEW USER WELCOME FRAME (Vibrant Gold & Cyan Ring with Distinct "NEW" Badge Tag)
private fun DrawScope.drawNewUserFrame(cx: Float, cy: Float, r: Float, pulse: Float, rotation: Float) {
    // 1. Radiant Glowing Double Halo Ring
    val haloBrush = Brush.sweepGradient(
        listOf(
            Color(0xFFFFD700), // Gold
            Color(0xFF38BDF8), // Cyan
            Color(0xFF34D399), // Emerald
            Color(0xFFFF7A00), // Vibrant Orange
            Color(0xFFFFD700)
        ),
        center = Offset(cx, cy)
    )

    // Outer Glow Ring
    drawCircle(
        brush = haloBrush,
        radius = r + 4.dp.toPx() + (pulse * 1.5.dp.toPx()),
        center = Offset(cx, cy),
        style = Stroke(width = 3.5.dp.toPx())
    )

    // Inner Shimmer Accent
    drawCircle(
        color = Color.White.copy(alpha = 0.5f + (pulse * 0.4f)),
        radius = r + 1.5.dp.toPx(),
        center = Offset(cx, cy),
        style = Stroke(width = 1.2.dp.toPx())
    )

    // 2. Animated Orbiting Stars
    rotate(rotation, pivot = Offset(cx, cy)) {
        for (i in 0 until 4) {
            val angle = i * 90.0 * Math.PI / 180.0
            val starR = r + 6.dp.toPx()
            val sx = cx + (starR * cos(angle)).toFloat()
            val sy = cy + (starR * sin(angle)).toFloat()
            drawCircle(
                color = if (i % 2 == 0) Color(0xFFFFD700) else Color(0xFF38BDF8),
                radius = 2.5.dp.toPx(),
                center = Offset(sx, sy)
            )
            drawCircle(
                color = Color.White,
                radius = 1.2.dp.toPx(),
                center = Offset(sx, sy)
            )
        }
    }

    // 3. Side Angelic Welcome Winglets
    val wingL = Path().apply {
        moveTo(cx - r - 2.dp.toPx(), cy - 4.dp.toPx())
        cubicTo(cx - r - 12.dp.toPx(), cy - 10.dp.toPx(), cx - r - 14.dp.toPx(), cy + 8.dp.toPx(), cx - r, cy + 10.dp.toPx())
        close()
    }
    drawPath(wingL, Brush.linearGradient(listOf(Color(0xFFFFE082), Color(0xFFFF9800))))
    drawPath(wingL, Color.White, style = Stroke(width = 0.8.dp.toPx()))

    val wingR = Path().apply {
        moveTo(cx + r + 2.dp.toPx(), cy - 4.dp.toPx())
        cubicTo(cx + r + 12.dp.toPx(), cy - 10.dp.toPx(), cx + r + 14.dp.toPx(), cy + 8.dp.toPx(), cx + r, cy + 10.dp.toPx())
        close()
    }
    drawPath(wingR, Brush.linearGradient(listOf(Color(0xFFFFE082), Color(0xFFFF9800))))
    drawPath(wingR, Color.White, style = Stroke(width = 0.8.dp.toPx()))

    // 4. Prominent Distinct "NEW" Badge Tag (Top-Right / Top of the Frame)
    val badgeCx = cx + (r * 0.55f)
    val badgeCy = cy - r - 3.dp.toPx()
    val badgeWidth = 34.dp.toPx()
    val badgeHeight = 16.dp.toPx()

    // 3D Badge Base Shadow & Pill
    val badgeRect = androidx.compose.ui.geometry.RoundRect(
        left = badgeCx - (badgeWidth / 2f),
        top = badgeCy - (badgeHeight / 2f),
        right = badgeCx + (badgeWidth / 2f),
        bottom = badgeCy + (badgeHeight / 2f),
        radiusX = 8.dp.toPx(),
        radiusY = 8.dp.toPx()
    )
    val badgePath = Path().apply { addRoundRect(badgeRect) }

    // Drop shadow
    drawPath(
        badgePath,
        Color.Black.copy(alpha = 0.35f)
    )

    // Red-Orange / Coral Gradient for high visibility "NEW" tag
    drawPath(
        badgePath,
        Brush.horizontalGradient(
            listOf(Color(0xFFFF2A55), Color(0xFFFF7A00))
        )
    )

    // Sparkling Gold Border around "NEW" Tag
    drawPath(
        badgePath,
        Color(0xFFFFD700),
        style = Stroke(width = 1.5.dp.toPx())
    )

    // Draw Crisp Native "NEW" Text onto Badge
    val textPaint = Paint().apply {
        color = android.graphics.Color.WHITE
        textSize = 9.5.dp.toPx()
        typeface = Typeface.create(Typeface.DEFAULT, Typeface.BOLD)
        textAlign = Paint.Align.CENTER
        isAntiAlias = true
        setShadowLayer(2f, 0f, 1f, android.graphics.Color.argb(180, 0, 0, 0))
    }
    val textY = badgeCy + (badgeHeight * 0.28f)
    drawContext.canvas.nativeCanvas.drawText("NEW", badgeCx, textY, textPaint)

    // Sparkle star near the NEW tag
    val starX = badgeCx + (badgeWidth / 2f) + 1.dp.toPx()
    val starY = badgeCy - (badgeHeight / 2f) + 1.dp.toPx()
    val sparklePath = Path().apply {
        moveTo(starX, starY - 3.5.dp.toPx())
        lineTo(starX + 1.5.dp.toPx(), starY)
        lineTo(starX, starY + 3.5.dp.toPx())
        lineTo(starX - 1.5.dp.toPx(), starY)
        close()
    }
    drawPath(sparklePath, Color(0xFFFFD700))
}

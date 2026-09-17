package com.example.ui.components

import androidx.compose.animation.core.*
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.Chat
import androidx.compose.material.icons.filled.Chat
import androidx.compose.material3.Icon
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.draw.scale
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.drawscope.Fill
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.nativeCanvas
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.ui.theme.QivoGold
import com.example.ui.theme.QivoGoldDark
import com.example.ui.theme.QivoGoldGlow
import com.example.ui.theme.QivoGoldLight
import com.example.ui.theme.QivoOrange
import com.example.ui.theme.QivoSunsetCoral

/**
 * Custom 3D Isometric / Claymorphic Message Blast Icon - Luxury Warm Amber & Gold Edition
 */
@Composable
fun MessageBlast3DIcon(
    modifier: Modifier = Modifier,
    size: Dp = 64.dp
) {
    val infiniteTransition = rememberInfiniteTransition(label = "blast_anim")
    val floatAnim by infiniteTransition.animateFloat(
        initialValue = -2.5f,
        targetValue = 2.5f,
        animationSpec = infiniteRepeatable(
            animation = tween(1800, easing = EaseInOutSine),
            repeatMode = RepeatMode.Reverse
        ),
        label = "float"
    )

    Box(
        modifier = modifier
            .size(size)
            .offset(y = floatAnim.dp),
        contentAlignment = Alignment.Center
    ) {
        Canvas(modifier = Modifier.fillMaxSize()) {
            val w = this.size.width
            val h = this.size.height

            // 1. Soft Bottom Drop Shadow
            drawOval(
                brush = Brush.radialGradient(
                    colors = listOf(Color(0x663E1700), Color.Transparent),
                    center = Offset(w * 0.5f, h * 0.88f),
                    radius = w * 0.45f
                ),
                topLeft = Offset(w * 0.08f, h * 0.72f),
                size = Size(w * 0.84f, h * 0.26f)
            )

            // 2. 3D Megaphone Body (Sunset Coral to Warm Amber)
            val conePath = Path().apply {
                moveTo(w * 0.20f, h * 0.44f)
                lineTo(w * 0.70f, h * 0.20f)
                lineTo(w * 0.76f, h * 0.74f)
                lineTo(w * 0.20f, h * 0.58f)
                close()
            }
            drawPath(
                path = conePath,
                brush = Brush.linearGradient(
                    colors = listOf(Color(0xFFFF7A50), Color(0xFFFF5232), Color(0xFFD83818)),
                    start = Offset(w * 0.2f, h * 0.2f),
                    end = Offset(w * 0.7f, h * 0.8f)
                )
            )

            // 3. Megaphone 3D Front Rim (Radiant 24k Gold Oval)
            drawOval(
                brush = Brush.linearGradient(
                    colors = listOf(Color(0xFFFFF176), Color(0xFFFFB300), Color(0xFFFF8F00)),
                    start = Offset(w * 0.65f, h * 0.18f),
                    end = Offset(w * 0.82f, h * 0.76f)
                ),
                topLeft = Offset(w * 0.64f, h * 0.20f),
                size = Size(w * 0.18f, h * 0.54f)
            )
            // Inner rim hole
            drawOval(
                brush = Brush.linearGradient(
                    colors = listOf(Color(0xFF6B1800), Color(0xFF330800)),
                    start = Offset(w * 0.68f, h * 0.28f),
                    end = Offset(w * 0.76f, h * 0.67f)
                ),
                topLeft = Offset(w * 0.68f, h * 0.26f),
                size = Size(w * 0.11f, h * 0.42f)
            )

            // 4. Megaphone 3D Handle (Obsidian Metallic)
            val handlePath = Path().apply {
                moveTo(w * 0.30f, h * 0.55f)
                lineTo(w * 0.35f, h * 0.82f)
                lineTo(w * 0.44f, h * 0.79f)
                lineTo(w * 0.41f, h * 0.53f)
                close()
            }
            drawPath(
                path = handlePath,
                brush = Brush.linearGradient(
                    colors = listOf(Color(0xFF4A3828), Color(0xFF241C14), Color(0xFF120E0A)),
                    start = Offset(w * 0.3f, h * 0.5f),
                    end = Offset(w * 0.45f, h * 0.8f)
                )
            )

            // 5. 3D Floating Mail Envelope coming out of the blast (Champagne White & Gold)
            val envLeft = w * 0.56f
            val envTop = h * 0.10f
            val envW = w * 0.40f
            val envH = h * 0.30f

            drawRoundRect(
                brush = Brush.linearGradient(
                    colors = listOf(Color(0xFFFFFFFF), Color(0xFFFFF8E1), Color(0xFFFFE082)),
                    start = Offset(envLeft, envTop),
                    end = Offset(envLeft + envW, envTop + envH)
                ),
                topLeft = Offset(envLeft, envTop),
                size = Size(envW, envH),
                cornerRadius = CornerRadius(6f, 6f)
            )
            // Envelope Flap
            val flapPath = Path().apply {
                moveTo(envLeft, envTop)
                lineTo(envLeft + envW * 0.5f, envTop + envH * 0.60f)
                lineTo(envLeft + envW, envTop)
                close()
            }
            drawPath(
                path = flapPath,
                brush = Brush.linearGradient(
                    colors = listOf(Color(0xFFFFE082), Color(0xFFFFB300)),
                    start = Offset(envLeft, envTop),
                    end = Offset(envLeft + envW, envTop + envH)
                )
            )

            // 6. 3D Sound Sparkles
            drawCircle(
                color = Color(0xFFFFD54F),
                radius = w * 0.055f,
                center = Offset(w * 0.90f, h * 0.20f)
            )
            drawCircle(
                color = Color(0xFFFFFFFF),
                radius = w * 0.038f,
                center = Offset(w * 0.88f, h * 0.58f)
            )
            drawCircle(
                color = Color(0xFFFFAB91),
                radius = w * 0.045f,
                center = Offset(w * 0.14f, h * 0.35f)
            )

            // 7. Glossy Specular Highlight
            val glossPath = Path().apply {
                moveTo(w * 0.24f, h * 0.45f)
                lineTo(w * 0.66f, h * 0.24f)
                lineTo(w * 0.67f, h * 0.30f)
                lineTo(w * 0.26f, h * 0.49f)
                close()
            }
            drawPath(path = glossPath, color = Color(0x99FFFFFF))
        }
    }
}

/**
 * Custom 3D Isometric / Claymorphic Game Center Controller Icon - Luxury Gold & Obsidian Edition
 */
@Composable
fun GameCenter3DIcon(
    modifier: Modifier = Modifier,
    size: Dp = 64.dp
) {
    val infiniteTransition = rememberInfiniteTransition(label = "game_anim")
    val tiltAnim by infiniteTransition.animateFloat(
        initialValue = -3f,
        targetValue = 3f,
        animationSpec = infiniteRepeatable(
            animation = tween(2200, easing = EaseInOutCubic),
            repeatMode = RepeatMode.Reverse
        ),
        label = "tilt"
    )

    Box(
        modifier = modifier
            .size(size)
            .rotate(tiltAnim),
        contentAlignment = Alignment.Center
    ) {
        Canvas(modifier = Modifier.fillMaxSize()) {
            val w = this.size.width
            val h = this.size.height

            // 1. Soft Shadow
            drawOval(
                brush = Brush.radialGradient(
                    colors = listOf(Color(0x552E1E00), Color.Transparent),
                    center = Offset(w * 0.5f, h * 0.88f),
                    radius = w * 0.48f
                ),
                topLeft = Offset(w * 0.06f, h * 0.74f),
                size = Size(w * 0.88f, h * 0.24f)
            )

            // 2. 3D Gamepad Main Body
            val bodyLeft = w * 0.08f
            val bodyTop = h * 0.24f
            val bodyW = w * 0.84f
            val bodyH = h * 0.50f

            // Bottom 3D bevel / gold trim
            drawRoundRect(
                brush = Brush.linearGradient(listOf(Color(0xFFFFB300), Color(0xFFFF8F00), Color(0xFFC67D00))),
                topLeft = Offset(bodyLeft, bodyTop + 4f),
                size = Size(bodyW, bodyH),
                cornerRadius = CornerRadius(w * 0.24f, w * 0.24f)
            )

            // Main Face in Obsidian Metallic
            drawRoundRect(
                brush = Brush.linearGradient(
                    colors = listOf(Color(0xFF2C241E), Color(0xFF1E1814), Color(0xFF100D0A)),
                    start = Offset(bodyLeft, bodyTop),
                    end = Offset(bodyLeft + bodyW, bodyTop + bodyH)
                ),
                topLeft = Offset(bodyLeft, bodyTop),
                size = Size(bodyW, bodyH),
                cornerRadius = CornerRadius(w * 0.24f, w * 0.24f)
            )

            // Grip ergonomic contours (left & right lobes)
            drawCircle(
                brush = Brush.radialGradient(
                    colors = listOf(Color(0xFF3E3228), Color(0xFF18130E)),
                    center = Offset(w * 0.25f, h * 0.52f),
                    radius = w * 0.24f
                ),
                radius = w * 0.20f,
                center = Offset(w * 0.25f, h * 0.52f)
            )
            drawCircle(
                brush = Brush.radialGradient(
                    colors = listOf(Color(0xFF3E3228), Color(0xFF18130E)),
                    center = Offset(w * 0.75f, h * 0.52f),
                    radius = w * 0.24f
                ),
                radius = w * 0.20f,
                center = Offset(w * 0.75f, h * 0.52f)
            )

            // 3. 3D 24k Gold D-Pad (Left side)
            val dpadCenterX = w * 0.27f
            val dpadCenterY = h * 0.48f
            val dpadSize = w * 0.22f
            val dpadArm = w * 0.08f

            drawRoundRect(
                brush = Brush.linearGradient(
                    colors = listOf(Color(0xFFFFF176), Color(0xFFFFB300), Color(0xFFFF8F00)),
                    start = Offset(dpadCenterX - dpadSize / 2, dpadCenterY - dpadArm / 2),
                    end = Offset(dpadCenterX + dpadSize / 2, dpadCenterY + dpadArm / 2)
                ),
                topLeft = Offset(dpadCenterX - dpadSize / 2, dpadCenterY - dpadArm / 2),
                size = Size(dpadSize, dpadArm),
                cornerRadius = CornerRadius(4f, 4f)
            )
            drawRoundRect(
                brush = Brush.linearGradient(
                    colors = listOf(Color(0xFFFFF176), Color(0xFFFFB300), Color(0xFFFF8F00)),
                    start = Offset(dpadCenterX - dpadArm / 2, dpadCenterY - dpadSize / 2),
                    end = Offset(dpadCenterX + dpadArm / 2, dpadCenterY + dpadSize / 2)
                ),
                topLeft = Offset(dpadCenterX - dpadArm / 2, dpadCenterY - dpadSize / 2),
                size = Size(dpadArm, dpadSize),
                cornerRadius = CornerRadius(4f, 4f)
            )

            // 4. 3D Sunset Amber Action Buttons (Right side)
            val btnRightX = w * 0.73f
            val btnRightY = h * 0.48f
            val btnSpacing = w * 0.085f
            val btnR = w * 0.048f

            // Top (Gold)
            drawCircle(
                brush = Brush.radialGradient(listOf(Color(0xFFFFF59D), Color(0xFFFFC107), Color(0xFFFF8F00)), center = Offset(btnRightX, btnRightY - btnSpacing)),
                radius = btnR,
                center = Offset(btnRightX, btnRightY - btnSpacing)
            )
            // Bottom (Sunset Coral)
            drawCircle(
                brush = Brush.radialGradient(listOf(Color(0xFFFFAB91), Color(0xFFFF5232), Color(0xFFD83818)), center = Offset(btnRightX, btnRightY + btnSpacing)),
                radius = btnR,
                center = Offset(btnRightX, btnRightY + btnSpacing)
            )
            // Left (Amber)
            drawCircle(
                brush = Brush.radialGradient(listOf(Color(0xFFFFCC80), Color(0xFFFF9800), Color(0xFFE65100)), center = Offset(btnRightX - btnSpacing, btnRightY)),
                radius = btnR,
                center = Offset(btnRightX - btnSpacing, btnRightY)
            )
            // Right (Champagne)
            drawCircle(
                brush = Brush.radialGradient(listOf(Color(0xFFFFFFFF), Color(0xFFFFE082), Color(0xFFFFB300)), center = Offset(btnRightX + btnSpacing, btnRightY)),
                radius = btnR,
                center = Offset(btnRightX + btnSpacing, btnRightY)
            )

            // 5. 3D Thumbstick Knobs
            val thumbL = Offset(w * 0.43f, h * 0.58f)
            val thumbR = Offset(w * 0.57f, h * 0.58f)
            val thumbRadius = w * 0.065f

            drawCircle(color = Color(0xFF110D0A), radius = thumbRadius + 2.5f, center = thumbL)
            drawCircle(
                brush = Brush.radialGradient(listOf(Color(0xFF5A4A3C), Color(0xFF2A2018)), center = thumbL),
                radius = thumbRadius,
                center = thumbL
            )
            drawCircle(color = Color(0xFF110D0A), radius = thumbRadius + 2.5f, center = thumbR)
            drawCircle(
                brush = Brush.radialGradient(listOf(Color(0xFF5A4A3C), Color(0xFF2A2018)), center = thumbR),
                radius = thumbRadius,
                center = thumbR
            )

            // 6. Top Glossy Curved Highlight
            drawArc(
                brush = Brush.linearGradient(
                    colors = listOf(Color(0x88FFFFFF), Color.Transparent),
                    start = Offset(bodyLeft, bodyTop),
                    end = Offset(bodyLeft, bodyTop + bodyH * 0.4f)
                ),
                startAngle = 180f,
                sweepAngle = 180f,
                useCenter = false,
                topLeft = Offset(bodyLeft + w * 0.05f, bodyTop + 2f),
                size = Size(bodyW - w * 0.10f, bodyH * 0.35f),
                style = Stroke(width = 3.5f)
            )
        }
    }
}

/**
 * Custom 3D Isometric / Claymorphic Tasks Center Icon - Luxury Gold Edition
 */
@Composable
fun TasksCenter3DIcon(
    modifier: Modifier = Modifier,
    size: Dp = 64.dp
) {
    val infiniteTransition = rememberInfiniteTransition(label = "task_anim")
    val pulseAnim by infiniteTransition.animateFloat(
        initialValue = 0.96f,
        targetValue = 1.04f,
        animationSpec = infiniteRepeatable(
            animation = tween(1600, easing = EaseInOutQuad),
            repeatMode = RepeatMode.Reverse
        ),
        label = "pulse"
    )

    Box(
        modifier = modifier
            .size(size)
            .scale(pulseAnim),
        contentAlignment = Alignment.Center
    ) {
        Canvas(modifier = Modifier.fillMaxSize()) {
            val w = this.size.width
            val h = this.size.height

            // 1. Soft Ambient Shadow
            drawOval(
                brush = Brush.radialGradient(
                    colors = listOf(Color(0x443E2400), Color.Transparent),
                    center = Offset(w * 0.5f, h * 0.88f),
                    radius = w * 0.45f
                ),
                topLeft = Offset(w * 0.10f, h * 0.74f),
                size = Size(w * 0.80f, h * 0.22f)
            )

            // 2. 3D Clipboard Base Board (Warm Amber & Gold)
            val boardLeft = w * 0.16f
            val boardTop = h * 0.14f
            val boardW = w * 0.68f
            val boardH = h * 0.74f

            drawRoundRect(
                color = Color(0xFFC67D00),
                topLeft = Offset(boardLeft, boardTop + 4f),
                size = Size(boardW, boardH),
                cornerRadius = CornerRadius(14f, 14f)
            )
            drawRoundRect(
                brush = Brush.linearGradient(
                    colors = listOf(Color(0xFFFFB300), Color(0xFFFF8F00), Color(0xFFE65100)),
                    start = Offset(boardLeft, boardTop),
                    end = Offset(boardLeft + boardW, boardTop + boardH)
                ),
                topLeft = Offset(boardLeft, boardTop),
                size = Size(boardW, boardH),
                cornerRadius = CornerRadius(14f, 14f)
            )

            // 3. Inner Clean White Paper Sheet
            val paperLeft = w * 0.23f
            val paperTop = h * 0.22f
            val paperW = w * 0.54f
            val paperH = h * 0.62f

            drawRoundRect(
                brush = Brush.linearGradient(
                    colors = listOf(Color(0xFFFFFFFF), Color(0xFFFFF9E6)),
                    start = Offset(paperLeft, paperTop),
                    end = Offset(paperLeft + paperW, paperTop + paperH)
                ),
                topLeft = Offset(paperLeft, paperTop),
                size = Size(paperW, paperH),
                cornerRadius = CornerRadius(8f, 8f)
            )

            // 4. Checklist Lines (Golden Amber)
            val lineLeft = paperLeft + w * 0.17f
            val lineW = paperW - w * 0.21f
            val lineThickness = 3.5f

            drawLine(
                color = Color(0xFFFFB300),
                start = Offset(lineLeft, paperTop + h * 0.14f),
                end = Offset(lineLeft + lineW * 0.85f, paperTop + h * 0.14f),
                strokeWidth = lineThickness,
                cap = StrokeCap.Round
            )
            drawLine(
                color = Color(0xFFFFD54F),
                start = Offset(lineLeft, paperTop + h * 0.26f),
                end = Offset(lineLeft + lineW * 0.95f, paperTop + h * 0.26f),
                strokeWidth = lineThickness,
                cap = StrokeCap.Round
            )
            drawLine(
                color = Color(0xFFFFE082),
                start = Offset(lineLeft, paperTop + h * 0.38f),
                end = Offset(lineLeft + lineW * 0.65f, paperTop + h * 0.38f),
                strokeWidth = lineThickness,
                cap = StrokeCap.Round
            )

            // 5. 3D Floating Checkmark Emblem in Center (Emerald / Gold Glow)
            val badgeCenter = Offset(w * 0.30f, paperTop + h * 0.20f)
            drawCircle(
                brush = Brush.radialGradient(
                    colors = listOf(Color(0xFFFFF176), Color(0xFFFFB300), Color(0xFFFF8F00)),
                    center = badgeCenter
                ),
                radius = w * 0.11f,
                center = badgeCenter
            )
            val checkPath = Path().apply {
                moveTo(badgeCenter.x - w * 0.055f, badgeCenter.y)
                lineTo(badgeCenter.x - w * 0.015f, badgeCenter.y + w * 0.045f)
                lineTo(badgeCenter.x + w * 0.055f, badgeCenter.y - w * 0.045f)
            }
            drawPath(
                path = checkPath,
                color = Color.White,
                style = Stroke(width = 4.5f, cap = StrokeCap.Round)
            )

            // 6. 3D Golden Clip at the top
            val clipLeft = w * 0.35f
            val clipTop = h * 0.08f
            val clipW = w * 0.30f
            val clipH = h * 0.13f

            drawRoundRect(
                brush = Brush.linearGradient(
                    colors = listOf(Color(0xFFFFFFFF), Color(0xFFFFD54F), Color(0xFFFF8F00)),
                    start = Offset(clipLeft, clipTop),
                    end = Offset(clipLeft + clipW, clipTop + clipH)
                ),
                topLeft = Offset(clipLeft, clipTop),
                size = Size(clipW, clipH),
                cornerRadius = CornerRadius(6f, 6f)
            )
            drawCircle(
                color = Color(0xFFB77900),
                radius = 3.5f,
                center = Offset(w * 0.50f, clipTop + clipH * 0.45f)
            )

            // 7. Shiny Star Gem
            val starCenter = Offset(w * 0.80f, h * 0.16f)
            drawCircle(
                brush = Brush.radialGradient(
                    colors = listOf(Color(0xFFFFF9C4), Color(0xFFFFEE58), Color(0xFFFBC02D)),
                    center = starCenter
                ),
                radius = w * 0.07f,
                center = starCenter
            )
        }
    }
}

/**
 * Custom 3D Isometric Gold Coin Icon with metallic rim, radial glow, embossed QIVO "Q" emblem & shine
 */
@Composable
fun Coin3DIcon(
    modifier: Modifier = Modifier,
    size: Dp = 28.dp
) {
    Box(
        modifier = modifier.size(size),
        contentAlignment = Alignment.Center
    ) {
        Canvas(modifier = Modifier.fillMaxSize()) {
            val w = this.size.width
            val h = this.size.height
            val center = Offset(w * 0.5f, h * 0.5f)
            val radius = w * 0.46f

            // 1. Drop shadow
            drawCircle(
                brush = Brush.radialGradient(
                    colors = listOf(Color(0x66B78103), Color.Transparent),
                    center = Offset(center.x, center.y + h * 0.08f),
                    radius = radius * 1.1f
                ),
                radius = radius * 1.05f,
                center = Offset(center.x, center.y + h * 0.06f)
            )

            // 2. 3D Bottom Bevel Edge
            drawCircle(
                color = Color(0xFFB77900),
                radius = radius,
                center = Offset(center.x, center.y + h * 0.04f)
            )

            // 3. Outer Golden Ring
            drawCircle(
                brush = Brush.linearGradient(
                    colors = listOf(
                        Color(0xFFFFF176),
                        Color(0xFFFFD54F),
                        Color(0xFFFFB300),
                        Color(0xFFFF8F00),
                        Color(0xFFFFA000)
                    ),
                    start = Offset(w * 0.15f, h * 0.15f),
                    end = Offset(w * 0.85f, h * 0.85f)
                ),
                radius = radius,
                center = center
            )

            // 4. Inner Recessed Circle
            val innerRadius = radius * 0.78f
            drawCircle(
                brush = Brush.radialGradient(
                    colors = listOf(
                        Color(0xFFFFE082),
                        Color(0xFFFFC107),
                        Color(0xFFFFA000),
                        Color(0xFFFF8F00)
                    ),
                    center = Offset(center.x - innerRadius * 0.3f, center.y - innerRadius * 0.3f),
                    radius = innerRadius * 1.4f
                ),
                radius = innerRadius,
                center = center
            )

            // 5. Inner Engraved Ring
            drawCircle(
                color = Color(0xFFFFE082).copy(alpha = 0.8f),
                radius = innerRadius * 0.92f,
                center = center,
                style = Stroke(width = w * 0.045f)
            )

            // 6. Central 3D Embossed "Q" Emblem (Custom QIVO Coin Identity - No $ and No Star)
            val qSize = innerRadius * 1.34f
            val qPaintShadow = android.graphics.Paint().apply {
                color = android.graphics.Color.parseColor("#8E5A00")
                textSize = qSize
                typeface = android.graphics.Typeface.create("sans-serif-black", android.graphics.Typeface.BOLD)
                textAlign = android.graphics.Paint.Align.CENTER
                isAntiAlias = true
            }
            val qPaintMain = android.graphics.Paint().apply {
                textSize = qSize
                typeface = android.graphics.Typeface.create("sans-serif-black", android.graphics.Typeface.BOLD)
                textAlign = android.graphics.Paint.Align.CENTER
                isAntiAlias = true
                shader = android.graphics.LinearGradient(
                    center.x - innerRadius * 0.4f, center.y - innerRadius * 0.4f,
                    center.x + innerRadius * 0.4f, center.y + innerRadius * 0.4f,
                    intArrayOf(
                        android.graphics.Color.parseColor("#FFFFFF"),
                        android.graphics.Color.parseColor("#FFF9C4"),
                        android.graphics.Color.parseColor("#FFD54F"),
                        android.graphics.Color.parseColor("#FFA000")
                    ),
                    floatArrayOf(0f, 0.30f, 0.70f, 1.0f),
                    android.graphics.Shader.TileMode.CLAMP
                )
            }
            val qPaintOutline = android.graphics.Paint().apply {
                color = android.graphics.Color.parseColor("#C47F00")
                textSize = qSize
                typeface = android.graphics.Typeface.create("sans-serif-black", android.graphics.Typeface.BOLD)
                textAlign = android.graphics.Paint.Align.CENTER
                isAntiAlias = true
                style = android.graphics.Paint.Style.STROKE
                strokeWidth = w * 0.035f
            }

            val qBounds = android.graphics.Rect()
            qPaintMain.getTextBounds("Q", 0, 1, qBounds)
            val qY = center.y + (qBounds.height() / 2f) - qBounds.bottom
            val qX = center.x

            // 3D Bevel depth shadow
            drawContext.canvas.nativeCanvas.drawText("Q", qX, qY + h * 0.035f, qPaintShadow)
            // Crisp engraved perimeter stroke
            drawContext.canvas.nativeCanvas.drawText("Q", qX, qY, qPaintOutline)
            // Radiant illuminated 3D golden face
            drawContext.canvas.nativeCanvas.drawText("Q", qX, qY, qPaintMain)

            // 7. Specular Gloss Arc
            drawArc(
                brush = Brush.linearGradient(
                    colors = listOf(Color(0xAAFFFFFF), Color.Transparent),
                    start = Offset(center.x - radius, center.y - radius),
                    end = Offset(center.x, center.y)
                ),
                startAngle = 190f,
                sweepAngle = 110f,
                useCenter = false,
                topLeft = Offset(center.x - radius * 0.85f, center.y - radius * 0.85f),
                size = Size(radius * 1.7f, radius * 1.7f),
                style = Stroke(width = w * 0.07f, cap = StrokeCap.Round)
            )

            // 8. Sparkle
            drawCircle(
                color = Color.White,
                radius = w * 0.06f,
                center = Offset(center.x + radius * 0.55f, center.y - radius * 0.55f)
            )
        }
    }
}

/**
 * Custom 3D Chat Button for Profile Cards (Warm Sunset Coral & Peach Badge matching Top of Home)
 */
@Composable
fun Chat3DBadgeButton(
    modifier: Modifier = Modifier,
    onClick: () -> Unit
) {
    Surface(
        onClick = onClick,
        shape = RoundedCornerShape(16.dp),
        color = Color(0xFFFF7043),
        shadowElevation = 4.dp,
        modifier = modifier.height(32.dp)
    ) {
        Box(
            modifier = Modifier
                .background(
                    Brush.horizontalGradient(
                        colors = listOf(
                            Color(0xFFFF8A65), // Soft Warm Coral
                            Color(0xFFFF5722)  // Rich Sunset Coral
                        )
                    ),
                    shape = RoundedCornerShape(16.dp)
                )
                .border(
                    width = 1.dp,
                    color = Color.White.copy(alpha = 0.4f),
                    shape = RoundedCornerShape(16.dp)
                )
                .clip(RoundedCornerShape(16.dp))
                .padding(horizontal = 10.dp, vertical = 5.dp),
            contentAlignment = Alignment.Center
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.Center
            ) {
                Icon(
                    imageVector = Icons.AutoMirrored.Filled.Chat,
                    contentDescription = "Chat",
                    tint = Color.White,
                    modifier = Modifier.size(14.dp)
                )
                Spacer(modifier = Modifier.width(4.dp))
                Text(
                    text = "Chat",
                    color = Color.White,
                    fontWeight = FontWeight.ExtraBold,
                    fontSize = 12.sp,
                    letterSpacing = 0.3.sp
                )
            }
        }
    }
}

/**
 * Custom 3D Store / Shop Icon - Luxury Boutique in Obsidian & 24k Gold
 */
@Composable
fun Store3DIcon(
    modifier: Modifier = Modifier,
    size: Dp = 44.dp
) {
    Box(modifier = modifier.size(size), contentAlignment = Alignment.Center) {
        Canvas(modifier = Modifier.fillMaxSize()) {
            val w = this.size.width
            val h = this.size.height

            // 1. Drop shadow
            drawOval(
                brush = Brush.radialGradient(listOf(Color(0x442E1E00), Color.Transparent)),
                topLeft = Offset(w * 0.10f, h * 0.78f),
                size = Size(w * 0.80f, h * 0.20f)
            )

            // 2. Boutique base building (Obsidian Metallic)
            drawRoundRect(
                brush = Brush.linearGradient(
                    colors = listOf(Color(0xFF3E3228), Color(0xFF241C16), Color(0xFF14100D)),
                    start = Offset(w * 0.20f, h * 0.40f),
                    end = Offset(w * 0.80f, h * 0.85f)
                ),
                topLeft = Offset(w * 0.18f, h * 0.38f),
                size = Size(w * 0.64f, h * 0.46f),
                cornerRadius = CornerRadius(8f, 8f)
            )

            // 3. Store Front Warm Gold Glass Window
            drawRoundRect(
                brush = Brush.linearGradient(
                    colors = listOf(Color(0xFFFFF9E6), Color(0xFFFFE082), Color(0xFFFFB300)),
                    start = Offset(w * 0.25f, h * 0.48f),
                    end = Offset(w * 0.50f, h * 0.78f)
                ),
                topLeft = Offset(w * 0.24f, h * 0.46f),
                size = Size(w * 0.26f, h * 0.32f),
                cornerRadius = CornerRadius(4f, 4f)
            )
            // Door (24k Gold)
            drawRoundRect(
                brush = Brush.linearGradient(
                    colors = listOf(Color(0xFFFFD54F), Color(0xFFFF8F00)),
                    start = Offset(w * 0.56f, h * 0.48f),
                    end = Offset(w * 0.76f, h * 0.84f)
                ),
                topLeft = Offset(w * 0.55f, h * 0.46f),
                size = Size(w * 0.22f, h * 0.38f),
                cornerRadius = CornerRadius(4f, 4f)
            )

            // 4. Sunset Coral & Gold Canopy Awning
            val awningTop = h * 0.22f
            val awningW = w * 0.84f
            val awningH = h * 0.20f
            val awningLeft = w * 0.08f

            val awningPath = Path().apply {
                moveTo(awningLeft + w * 0.05f, awningTop)
                lineTo(awningLeft + awningW - w * 0.05f, awningTop)
                lineTo(awningLeft + awningW, awningTop + awningH)
                lineTo(awningLeft, awningTop + awningH)
                close()
            }
            drawPath(
                path = awningPath,
                brush = Brush.linearGradient(
                    colors = listOf(Color(0xFFFF7043), Color(0xFFFF5232), Color(0xFFD83818)),
                    start = Offset(awningLeft, awningTop),
                    end = Offset(awningLeft, awningTop + awningH)
                )
            )

            // Awning stripes (Gold alternating stripes)
            val numStripes = 5
            val stripeW = awningW / numStripes
            for (i in 0 until numStripes step 2) {
                val sx = awningLeft + i * stripeW
                val stripePath = Path().apply {
                    moveTo(sx + stripeW * 0.1f, awningTop)
                    lineTo(sx + stripeW * 0.9f, awningTop)
                    lineTo(sx + stripeW, awningTop + awningH)
                    lineTo(sx, awningTop + awningH)
                    close()
                }
                drawPath(
                    path = stripePath,
                    brush = Brush.verticalGradient(listOf(Color(0xFFFFF9C4), Color(0xFFFFD54F)))
                )
            }
        }
    }
}

/**
 * Custom 3D Aristocracy / Imperial Crown Icon - Pure 24k Gold & Gemstones
 */
@Composable
fun Aristocracy3DIcon(
    modifier: Modifier = Modifier,
    size: Dp = 44.dp
) {
    Box(modifier = modifier.size(size), contentAlignment = Alignment.Center) {
        Canvas(modifier = Modifier.fillMaxSize()) {
            val w = this.size.width
            val h = this.size.height

            // 1. Soft Shadow
            drawOval(
                brush = Brush.radialGradient(listOf(Color(0x445D4000), Color.Transparent)),
                topLeft = Offset(w * 0.12f, h * 0.78f),
                size = Size(w * 0.76f, h * 0.18f)
            )

            // 2. Crown Base Rim (24k Gold)
            drawRoundRect(
                brush = Brush.linearGradient(
                    colors = listOf(Color(0xFFFFF176), Color(0xFFFFB300), Color(0xFFFF8F00)),
                    start = Offset(w * 0.16f, h * 0.65f),
                    end = Offset(w * 0.84f, h * 0.80f)
                ),
                topLeft = Offset(w * 0.15f, h * 0.64f),
                size = Size(w * 0.70f, h * 0.14f),
                cornerRadius = CornerRadius(6f, 6f)
            )

            // 3. Crown Peaks
            val crownPath = Path().apply {
                moveTo(w * 0.15f, h * 0.64f)
                lineTo(w * 0.12f, h * 0.32f) // Left tip
                lineTo(w * 0.34f, h * 0.50f)
                lineTo(w * 0.50f, h * 0.20f) // Center high tip
                lineTo(w * 0.66f, h * 0.50f)
                lineTo(w * 0.88f, h * 0.32f) // Right tip
                lineTo(w * 0.85f, h * 0.64f)
                close()
            }
            drawPath(
                path = crownPath,
                brush = Brush.linearGradient(
                    colors = listOf(Color(0xFFFFFFFF), Color(0xFFFFF59D), Color(0xFFFFD54F), Color(0xFFFF8F00)),
                    start = Offset(w * 0.15f, h * 0.20f),
                    end = Offset(w * 0.85f, h * 0.70f)
                )
            )

            // 4. Jewels / Pearls on crown tips
            drawCircle(brush = Brush.radialGradient(listOf(Color(0xFFFFF59D), Color(0xFFFFB300))), radius = w * 0.065f, center = Offset(w * 0.12f, h * 0.32f))
            drawCircle(brush = Brush.radialGradient(listOf(Color(0xFFFF8A80), Color(0xFFFF5252), Color(0xFFD50000))), radius = w * 0.085f, center = Offset(w * 0.50f, h * 0.20f))
            drawCircle(brush = Brush.radialGradient(listOf(Color(0xFFFFF59D), Color(0xFFFFB300))), radius = w * 0.065f, center = Offset(w * 0.88f, h * 0.32f))

            // 5. Crown Rim Inset Diamonds
            drawCircle(color = Color.White, radius = w * 0.035f, center = Offset(w * 0.30f, h * 0.71f))
            drawCircle(color = Color(0xFFFF5252), radius = w * 0.045f, center = Offset(w * 0.50f, h * 0.71f))
            drawCircle(color = Color.White, radius = w * 0.035f, center = Offset(w * 0.70f, h * 0.71f))
        }
    }
}

/**
 * Custom 3D Bag / Designer Tote Icon - Sunset Amber & Gold
 */
@Composable
fun Bag3DIcon(
    modifier: Modifier = Modifier,
    size: Dp = 44.dp
) {
    Box(modifier = modifier.size(size), contentAlignment = Alignment.Center) {
        Canvas(modifier = Modifier.fillMaxSize()) {
            val w = this.size.width
            val h = this.size.height

            // 1. Soft Shadow
            drawOval(
                brush = Brush.radialGradient(listOf(Color(0x443E1E00), Color.Transparent)),
                topLeft = Offset(w * 0.12f, h * 0.78f),
                size = Size(w * 0.76f, h * 0.18f)
            )

            // 2. Bag Handle (Golden Arc)
            drawArc(
                brush = Brush.linearGradient(listOf(Color(0xFFFFF176), Color(0xFFFFB300), Color(0xFFFF8F00))),
                startAngle = 180f,
                sweepAngle = 180f,
                useCenter = false,
                topLeft = Offset(w * 0.32f, h * 0.12f),
                size = Size(w * 0.36f, h * 0.36f),
                style = Stroke(width = w * 0.07f, cap = StrokeCap.Round)
            )

            // 3. 3D Bag Body (Sunset Amber / Coral)
            val bagPath = Path().apply {
                moveTo(w * 0.22f, h * 0.32f)
                lineTo(w * 0.78f, h * 0.32f)
                lineTo(w * 0.84f, h * 0.80f)
                quadraticTo(w * 0.84f, h * 0.84f, w * 0.78f, h * 0.84f)
                lineTo(w * 0.22f, h * 0.84f)
                quadraticTo(w * 0.16f, h * 0.84f, w * 0.16f, h * 0.80f)
                close()
            }
            drawPath(
                path = bagPath,
                brush = Brush.linearGradient(
                    colors = listOf(Color(0xFFFF7A50), Color(0xFFFF5232), Color(0xFFC63015)),
                    start = Offset(w * 0.2f, h * 0.3f),
                    end = Offset(w * 0.8f, h * 0.8f)
                )
            )

            // 4. Center Gold Star Clasp
            drawCircle(
                brush = Brush.radialGradient(listOf(Color(0xFFFFF59D), Color(0xFFFFD54F), Color(0xFFFF8F00))),
                radius = w * 0.12f,
                center = Offset(w * 0.50f, h * 0.58f)
            )
            drawCircle(color = Color.White, radius = w * 0.04f, center = Offset(w * 0.50f, h * 0.58f))
        }
    }
}

/**
 * Custom 3D Wallet Icon - Luxury Bifold in Obsidian & 24k Gold
 */
@Composable
fun Wallet3DIcon(
    modifier: Modifier = Modifier,
    size: Dp = 44.dp
) {
    Box(modifier = modifier.size(size), contentAlignment = Alignment.Center) {
        Canvas(modifier = Modifier.fillMaxSize()) {
            val w = this.size.width
            val h = this.size.height

            // 1. Soft Shadow
            drawOval(
                brush = Brush.radialGradient(listOf(Color(0x442E1E00), Color.Transparent)),
                topLeft = Offset(w * 0.10f, h * 0.78f),
                size = Size(w * 0.80f, h * 0.18f)
            )

            // 2. Protruding Gold Coin from top
            drawCircle(
                brush = Brush.radialGradient(listOf(Color(0xFFFFF59D), Color(0xFFFFC107), Color(0xFFFF8F00))),
                radius = w * 0.16f,
                center = Offset(w * 0.65f, h * 0.32f)
            )
            // Protruding VIP Card from top left (Gold/Champagne)
            drawRoundRect(
                brush = Brush.linearGradient(listOf(Color(0xFFFFF8E1), Color(0xFFFFD54F), Color(0xFFFF8F00))),
                topLeft = Offset(w * 0.22f, h * 0.20f),
                size = Size(w * 0.38f, h * 0.24f),
                cornerRadius = CornerRadius(4f, 4f)
            )

            // 3. 3D Wallet Main Body (Obsidian Leather)
            drawRoundRect(
                brush = Brush.linearGradient(
                    colors = listOf(Color(0xFF3A3026), Color(0xFF201A14), Color(0xFF100C0A)),
                    start = Offset(w * 0.12f, h * 0.34f),
                    end = Offset(w * 0.88f, h * 0.84f)
                ),
                topLeft = Offset(w * 0.12f, h * 0.34f),
                size = Size(w * 0.76f, h * 0.48f),
                cornerRadius = CornerRadius(10f, 10f)
            )

            // 4. Wallet Clasp Flap (Warm Amber Leather)
            drawRoundRect(
                brush = Brush.linearGradient(
                    colors = listOf(Color(0xFFFF9800), Color(0xFFE65100)),
                    start = Offset(w * 0.52f, h * 0.46f),
                    end = Offset(w * 0.88f, h * 0.70f)
                ),
                topLeft = Offset(w * 0.50f, h * 0.46f),
                size = Size(w * 0.38f, h * 0.24f),
                cornerRadius = CornerRadius(6f, 6f)
            )

            // 5. Golden Metallic Button Clasp
            drawCircle(
                brush = Brush.radialGradient(listOf(Color(0xFFFFF9C4), Color(0xFFFFD54F), Color(0xFFFF8F00))),
                radius = w * 0.07f,
                center = Offset(w * 0.78f, h * 0.58f)
            )
        }
    }
}

/**
 * Custom 3D Verification Shield - Luxury Gold Crest
 */
@Composable
fun Verify3DIcon(
    modifier: Modifier = Modifier,
    size: Dp = 44.dp
) {
    Box(modifier = modifier.size(size), contentAlignment = Alignment.Center) {
        Canvas(modifier = Modifier.fillMaxSize()) {
            val w = this.size.width
            val h = this.size.height

            // 1. Soft Shadow
            drawOval(
                brush = Brush.radialGradient(listOf(Color(0x442E1E00), Color.Transparent)),
                topLeft = Offset(w * 0.14f, h * 0.80f),
                size = Size(w * 0.72f, h * 0.18f)
            )

            // 2. 3D Shield Outer (24k Gold)
            val shieldPath = Path().apply {
                moveTo(w * 0.50f, h * 0.14f)
                lineTo(w * 0.84f, h * 0.26f)
                quadraticTo(w * 0.84f, h * 0.60f, w * 0.50f, h * 0.84f)
                quadraticTo(w * 0.16f, h * 0.60f, w * 0.16f, h * 0.26f)
                close()
            }
            drawPath(
                path = shieldPath,
                brush = Brush.linearGradient(
                    colors = listOf(Color(0xFFFFF176), Color(0xFFFFB300), Color(0xFFFF8F00)),
                    start = Offset(w * 0.2f, h * 0.15f),
                    end = Offset(w * 0.8f, h * 0.85f)
                )
            )

            // 3. Inner Shield (Obsidian / Amber Core)
            val innerShield = Path().apply {
                moveTo(w * 0.50f, h * 0.22f)
                lineTo(w * 0.76f, h * 0.32f)
                quadraticTo(w * 0.76f, h * 0.58f, w * 0.50f, h * 0.76f)
                quadraticTo(w * 0.24f, h * 0.58f, w * 0.24f, h * 0.32f)
                close()
            }
            drawPath(
                path = innerShield,
                brush = Brush.linearGradient(
                    colors = listOf(Color(0xFFFF9800), Color(0xFFE65100), Color(0xFF3E1E00)),
                    start = Offset(w * 0.3f, h * 0.25f),
                    end = Offset(w * 0.7f, h * 0.75f)
                )
            )

            // 4. Crisp White 3D Checkmark
            val checkPath = Path().apply {
                moveTo(w * 0.34f, h * 0.48f)
                lineTo(w * 0.45f, h * 0.60f)
                lineTo(w * 0.68f, h * 0.36f)
            }
            drawPath(
                path = checkPath,
                color = Color.White,
                style = Stroke(width = w * 0.09f, cap = StrokeCap.Round, join = StrokeJoin.Round)
            )
        }
    }
}

/**
 * Custom 3D Support Headset Icon - Luxury Gold & Sunset Coral
 */
@Composable
fun Support3DIcon(
    modifier: Modifier = Modifier,
    size: Dp = 44.dp
) {
    Box(modifier = modifier.size(size), contentAlignment = Alignment.Center) {
        Canvas(modifier = Modifier.fillMaxSize()) {
            val w = this.size.width
            val h = this.size.height

            // 1. Headband Arc (24k Gold)
            drawArc(
                brush = Brush.linearGradient(listOf(Color(0xFFFFF176), Color(0xFFFFB300), Color(0xFFFF8F00))),
                startAngle = 180f,
                sweepAngle = 180f,
                useCenter = false,
                topLeft = Offset(w * 0.18f, h * 0.16f),
                size = Size(w * 0.64f, h * 0.54f),
                style = Stroke(width = w * 0.08f, cap = StrokeCap.Round)
            )

            // 2. Ear Cups (Sunset Coral & Gold)
            drawRoundRect(
                brush = Brush.linearGradient(listOf(Color(0xFFFF7A50), Color(0xFFFF5232))),
                topLeft = Offset(w * 0.12f, h * 0.42f),
                size = Size(w * 0.16f, h * 0.34f),
                cornerRadius = CornerRadius(6f, 6f)
            )
            drawRoundRect(
                brush = Brush.linearGradient(listOf(Color(0xFFFF7A50), Color(0xFFFF5232))),
                topLeft = Offset(w * 0.72f, h * 0.42f),
                size = Size(w * 0.16f, h * 0.34f),
                cornerRadius = CornerRadius(6f, 6f)
            )

            // 3. Microphone Boom (Gold)
            val micBoom = Path().apply {
                moveTo(w * 0.20f, h * 0.68f)
                quadraticTo(w * 0.28f, h * 0.88f, w * 0.48f, h * 0.84f)
            }
            drawPath(
                path = micBoom,
                brush = Brush.linearGradient(listOf(Color(0xFFFFF176), Color(0xFFFFB300))),
                style = Stroke(width = w * 0.06f, cap = StrokeCap.Round)
            )
            // Mic foam tip
            drawCircle(
                brush = Brush.radialGradient(listOf(Color(0xFFFFF9C4), Color(0xFFFFB300))),
                radius = w * 0.07f,
                center = Offset(w * 0.50f, h * 0.84f)
            )
        }
    }
}

/**
 * Custom 3D Settings Gear Icon - Obsidian & 24k Gold
 */
@Composable
fun Settings3DIcon(
    modifier: Modifier = Modifier,
    size: Dp = 44.dp
) {
    Box(modifier = modifier.size(size), contentAlignment = Alignment.Center) {
        Canvas(modifier = Modifier.fillMaxSize()) {
            val w = this.size.width
            val h = this.size.height
            val center = Offset(w * 0.5f, h * 0.5f)

            // 1. Soft Shadow
            drawCircle(
                color = Color(0x442E1E00),
                radius = w * 0.42f,
                center = Offset(center.x, center.y + h * 0.06f)
            )

            // 2. 3D Gear Teeth (6-point cog in 24k Gold)
            val numTeeth = 6
            for (i in 0 until numTeeth) {
                val angle = (i * 360f / numTeeth)
                val rad = Math.toRadians(angle.toDouble())
                val tx = center.x + (w * 0.32f) * kotlin.math.cos(rad).toFloat()
                val ty = center.y + (h * 0.32f) * kotlin.math.sin(rad).toFloat()

                drawRoundRect(
                    brush = Brush.linearGradient(listOf(Color(0xFFFFF176), Color(0xFFFFB300), Color(0xFFFF8F00))),
                    topLeft = Offset(tx - w * 0.08f, ty - h * 0.08f),
                    size = Size(w * 0.16f, h * 0.16f),
                    cornerRadius = CornerRadius(4f, 4f)
                )
            }

            // 3. Central Cog Body
            drawCircle(
                brush = Brush.linearGradient(
                    colors = listOf(Color(0xFFFFFFFF), Color(0xFFFFE082), Color(0xFFFFB300), Color(0xFFFF8F00)),
                    start = Offset(w * 0.2f, h * 0.2f),
                    end = Offset(w * 0.8f, h * 0.8f)
                ),
                radius = w * 0.32f,
                center = center
            )

            // 4. Center Hole (Obsidian)
            drawCircle(
                brush = Brush.linearGradient(listOf(Color(0xFF2A2018), Color(0xFF14100D))),
                radius = w * 0.14f,
                center = center
            )
        }
    }
}

/**
 * Custom 3D Admin Shield Icon - Royal 24k Gold & Obsidian
 */
@Composable
fun AdminShield3DIcon(
    modifier: Modifier = Modifier,
    size: Dp = 44.dp
) {
    Box(modifier = modifier.size(size), contentAlignment = Alignment.Center) {
        Canvas(modifier = Modifier.fillMaxSize()) {
            val w = this.size.width
            val h = this.size.height

            val shield = Path().apply {
                moveTo(w * 0.50f, h * 0.14f)
                lineTo(w * 0.84f, h * 0.26f)
                quadraticTo(w * 0.84f, h * 0.60f, w * 0.50f, h * 0.84f)
                quadraticTo(w * 0.16f, h * 0.60f, w * 0.16f, h * 0.26f)
                close()
            }
            drawPath(
                path = shield,
                brush = Brush.linearGradient(
                    colors = listOf(Color(0xFFFFF176), Color(0xFFFFB300), Color(0xFFFF8F00)),
                    start = Offset(w * 0.2f, h * 0.15f),
                    end = Offset(w * 0.8f, h * 0.85f)
                )
            )

            // Golden Key Emblem
            drawCircle(
                brush = Brush.radialGradient(listOf(Color(0xFFFFFFFF), Color(0xFFFFF9C4))),
                radius = w * 0.10f,
                center = Offset(w * 0.50f, h * 0.44f)
            )
            drawRoundRect(
                brush = Brush.linearGradient(listOf(Color(0xFFFFFFFF), Color(0xFFFFD54F))),
                topLeft = Offset(w * 0.46f, h * 0.48f),
                size = Size(w * 0.08f, h * 0.22f),
                cornerRadius = CornerRadius(2f, 2f)
            )
        }
    }
}

/**
 * Custom 3D Report Flag Icon - Gold Pole & Sunset Coral Banner
 */
@Composable
fun ReportFlag3DIcon(
    modifier: Modifier = Modifier,
    size: Dp = 44.dp
) {
    Box(modifier = modifier.size(size), contentAlignment = Alignment.Center) {
        Canvas(modifier = Modifier.fillMaxSize()) {
            val w = this.size.width
            val h = this.size.height

            // 24k Gold Flag pole
            drawLine(
                brush = Brush.linearGradient(listOf(Color(0xFFFFF176), Color(0xFFFFB300), Color(0xFFFF8F00))),
                start = Offset(w * 0.24f, h * 0.16f),
                end = Offset(w * 0.24f, h * 0.86f),
                strokeWidth = w * 0.07f,
                cap = StrokeCap.Round
            )

            // Sunset Coral Banner Flag
            val flag = Path().apply {
                moveTo(w * 0.24f, h * 0.18f)
                lineTo(w * 0.82f, h * 0.28f)
                lineTo(w * 0.68f, h * 0.44f)
                lineTo(w * 0.82f, h * 0.60f)
                lineTo(w * 0.24f, h * 0.54f)
                close()
            }
            drawPath(
                path = flag,
                brush = Brush.linearGradient(
                    colors = listOf(Color(0xFFFF7A50), Color(0xFFFF5232), Color(0xFFC62828)),
                    start = Offset(w * 0.24f, h * 0.18f),
                    end = Offset(w * 0.82f, h * 0.60f)
                )
            )
        }
    }
}

/**
 * Custom 3D Animated Refresh Icon with Glowing Dual Arcs
 */
@Composable
fun CustomAnimatedRefresh3DIcon(
    modifier: Modifier = Modifier,
    size: Dp = 32.dp,
    isRotating: Boolean = true
) {
    val infiniteTransition = rememberInfiniteTransition(label = "refresh_3d_anim")
    val rotation by infiniteTransition.animateFloat(
        initialValue = 0f,
        targetValue = 360f,
        animationSpec = infiniteRepeatable(
            animation = tween(900, easing = LinearEasing),
            repeatMode = RepeatMode.Restart
        ),
        label = "rotation"
    )

    val pulse by infiniteTransition.animateFloat(
        initialValue = 0.95f,
        targetValue = 1.05f,
        animationSpec = infiniteRepeatable(
            animation = tween(450, easing = EaseInOutSine),
            repeatMode = RepeatMode.Reverse
        ),
        label = "pulse"
    )

    Box(
        modifier = modifier
            .size(size)
            .scale(if (isRotating) pulse else 1f)
            .rotate(if (isRotating) rotation else 0f),
        contentAlignment = Alignment.Center
    ) {
        Canvas(modifier = Modifier.fillMaxSize()) {
            val w = this.size.width
            val h = this.size.height
            val center = Offset(w * 0.5f, h * 0.5f)
            val radius = w * 0.42f
            val strokeW = w * 0.12f

            // Arc 1 (Warm Gold)
            drawArc(
                brush = Brush.sweepGradient(
                    colors = listOf(
                        Color(0x00FFD600),
                        Color(0x88FFD600),
                        Color(0xFFFFEA00),
                        Color(0xFFFF6D00)
                    ),
                    center = center
                ),
                startAngle = 0f,
                sweepAngle = 140f,
                useCenter = false,
                topLeft = Offset(center.x - radius, center.y - radius),
                size = Size(radius * 2f, radius * 2f),
                style = Stroke(width = strokeW, cap = StrokeCap.Round)
            )

            // Arrow head 1
            val arrow1Angle = Math.toRadians(140.0)
            val ax1 = center.x + radius * kotlin.math.cos(arrow1Angle).toFloat()
            val ay1 = center.y + radius * kotlin.math.sin(arrow1Angle).toFloat()
            drawCircle(
                color = Color(0xFFFF6D00),
                radius = strokeW * 0.7f,
                center = Offset(ax1, ay1)
            )

            // Arc 2 (Sunset Amber)
            drawArc(
                brush = Brush.sweepGradient(
                    colors = listOf(
                        Color(0x00FF5232),
                        Color(0x88FF5232),
                        Color(0xFFFFB300),
                        Color(0xFFFFD54F)
                    ),
                    center = center
                ),
                startAngle = 180f,
                sweepAngle = 140f,
                useCenter = false,
                topLeft = Offset(center.x - radius, center.y - radius),
                size = Size(radius * 2f, radius * 2f),
                style = Stroke(width = strokeW, cap = StrokeCap.Round)
            )

            // Arrow head 2
            val arrow2Angle = Math.toRadians((180 + 140).toDouble())
            val ax2 = center.x + radius * kotlin.math.cos(arrow2Angle).toFloat()
            val ay2 = center.y + radius * kotlin.math.sin(arrow2Angle).toFloat()
            drawCircle(
                color = Color(0xFFFFD54F),
                radius = strokeW * 0.7f,
                center = Offset(ax2, ay2)
            )

            // Center glowing core
            drawCircle(
                brush = Brush.radialGradient(
                    colors = listOf(Color(0xFFFFFFFF), Color(0x66FFD600), Color.Transparent),
                    center = center,
                    radius = radius * 0.5f
                ),
                radius = radius * 0.4f,
                center = center
            )
        }
    }
}

/**
 * 3D Isometric / Claymorphic Home Navigation Icon - Luxury Gold & Obsidian Edition
 */
@Composable
fun Home3DNavIcon(
    modifier: Modifier = Modifier,
    size: Dp = 30.dp,
    isSelected: Boolean = false
) {
    Box(
        modifier = modifier.size(size),
        contentAlignment = Alignment.Center
    ) {
        Canvas(modifier = Modifier.fillMaxSize()) {
            val w = this.size.width
            val h = this.size.height

            // 1. Soft Shadow
            drawOval(
                brush = Brush.radialGradient(
                    colors = listOf(Color(0x55000000), Color.Transparent),
                    center = Offset(w * 0.5f, h * 0.92f),
                    radius = w * 0.45f
                ),
                topLeft = Offset(w * 0.1f, h * 0.80f),
                size = Size(w * 0.8f, h * 0.18f)
            )

            // 2. House Base Body
            val baseBody = Path().apply {
                moveTo(w * 0.20f, h * 0.46f)
                lineTo(w * 0.80f, h * 0.46f)
                lineTo(w * 0.80f, h * 0.86f)
                lineTo(w * 0.20f, h * 0.86f)
                close()
            }
            drawPath(
                path = baseBody,
                brush = Brush.verticalGradient(
                    colors = if (isSelected) {
                        listOf(Color(0xFFFFF176), Color(0xFFFFB300), Color(0xFFFF8F00))
                    } else {
                        listOf(Color(0xFFB0A8A0), Color(0xFF787068), Color(0xFF484038))
                    }
                )
            )

            // 3. 3D Pitched Roof
            val roofPath = Path().apply {
                moveTo(w * 0.50f, h * 0.12f)
                lineTo(w * 0.92f, h * 0.48f)
                lineTo(w * 0.08f, h * 0.48f)
                close()
            }
            drawPath(
                path = roofPath,
                brush = Brush.linearGradient(
                    colors = if (isSelected) {
                        listOf(Color(0xFFFF7A50), Color(0xFFFF5232), Color(0xFFC62828))
                    } else {
                        listOf(Color(0xFF8A8278), Color(0xFF5A5248), Color(0xFF322C24))
                    },
                    start = Offset(w * 0.5f, h * 0.1f),
                    end = Offset(w * 0.9f, h * 0.5f)
                )
            )

            // 4. Roof Edge Highlight
            val roofEdge = Path().apply {
                moveTo(w * 0.50f, h * 0.12f)
                lineTo(w * 0.92f, h * 0.48f)
                lineTo(w * 0.84f, h * 0.52f)
                lineTo(w * 0.50f, h * 0.19f)
                close()
            }
            drawPath(
                path = roofEdge,
                color = if (isSelected) Color(0xFFFFE082).copy(alpha = 0.8f) else Color(0xFFC8C0B8).copy(alpha = 0.8f)
            )

            // 5. Chimney
            val chimneyPath = Path().apply {
                moveTo(w * 0.70f, h * 0.22f)
                lineTo(w * 0.78f, h * 0.22f)
                lineTo(w * 0.78f, h * 0.36f)
                lineTo(w * 0.70f, h * 0.36f)
                close()
            }
            drawPath(
                path = chimneyPath,
                color = if (isSelected) Color(0xFFE65100) else Color(0xFF484038)
            )

            // 6. Glowing Door
            drawRoundRect(
                brush = Brush.verticalGradient(
                    colors = if (isSelected) {
                        listOf(Color(0xFFFFFFFF), Color(0xFFFFF9C4), Color(0xFFFFD54F))
                    } else {
                        listOf(Color(0xFFD8D0C8), Color(0xFFA8A098))
                    }
                ),
                topLeft = Offset(w * 0.38f, h * 0.56f),
                size = Size(w * 0.24f, h * 0.30f),
                cornerRadius = CornerRadius(w * 0.04f, w * 0.04f)
            )
        }
    }
}

/**
 * 3D Isometric / Claymorphic Party Navigation Icon - Luxury Gold Edition
 */
@Composable
fun Party3DNavIcon(
    modifier: Modifier = Modifier,
    size: Dp = 30.dp,
    isSelected: Boolean = false
) {
    Box(
        modifier = modifier.size(size),
        contentAlignment = Alignment.Center
    ) {
        Canvas(modifier = Modifier.fillMaxSize()) {
            val w = this.size.width
            val h = this.size.height

            // 1. Shadow
            drawOval(
                brush = Brush.radialGradient(
                    colors = listOf(Color(0x55000000), Color.Transparent),
                    center = Offset(w * 0.5f, h * 0.90f),
                    radius = w * 0.45f
                ),
                topLeft = Offset(w * 0.15f, h * 0.80f),
                size = Size(w * 0.7f, h * 0.18f)
            )

            // 2. 3D Disco Sphere
            drawCircle(
                brush = Brush.radialGradient(
                    colors = if (isSelected) {
                        listOf(Color(0xFFFFFFFF), Color(0xFFFFF59D), Color(0xFFFFB300), Color(0xFFE65100))
                    } else {
                        listOf(Color(0xFFFFFFFF), Color(0xFFB0A8A0), Color(0xFF686058), Color(0xFF383028))
                    },
                    center = Offset(w * 0.42f, h * 0.42f),
                    radius = w * 0.38f
                ),
                radius = w * 0.34f,
                center = Offset(w * 0.48f, h * 0.50f)
            )

            // 3. Facet Grid
            val strokeColor = if (isSelected) Color(0x77FFFFFF) else Color(0x44FFFFFF)
            drawLine(color = strokeColor, start = Offset(w * 0.20f, h * 0.50f), end = Offset(w * 0.76f, h * 0.50f), strokeWidth = 1.5f)
            drawLine(color = strokeColor, start = Offset(w * 0.26f, h * 0.36f), end = Offset(w * 0.70f, h * 0.36f), strokeWidth = 1.5f)
            drawLine(color = strokeColor, start = Offset(w * 0.26f, h * 0.64f), end = Offset(w * 0.70f, h * 0.64f), strokeWidth = 1.5f)
            drawLine(color = strokeColor, start = Offset(w * 0.48f, h * 0.18f), end = Offset(w * 0.48f, h * 0.82f), strokeWidth = 1.5f)

            // 4. Party Crown / Festive Cone
            val hatPath = Path().apply {
                moveTo(w * 0.72f, h * 0.08f)
                lineTo(w * 0.90f, h * 0.40f)
                lineTo(w * 0.58f, h * 0.30f)
                close()
            }
            drawPath(
                path = hatPath,
                brush = Brush.linearGradient(
                    colors = if (isSelected) {
                        listOf(Color(0xFFFF7A50), Color(0xFFFF5232))
                    } else {
                        listOf(Color(0xFF8A8278), Color(0xFF5A5248))
                    }
                )
            )

            // Pompom on hat
            drawCircle(
                color = if (isSelected) Color(0xFFFFD54F) else Color(0xFFC8C0B8),
                radius = w * 0.06f,
                center = Offset(w * 0.72f, h * 0.08f)
            )
        }
    }
}

/**
 * 3D Isometric / Claymorphic Chat Navigation Icon - Luxury Gold & Amber Edition
 */
@Composable
fun Chat3DNavIcon(
    modifier: Modifier = Modifier,
    size: Dp = 30.dp,
    isSelected: Boolean = false
) {
    Box(
        modifier = modifier.size(size),
        contentAlignment = Alignment.Center
    ) {
        Canvas(modifier = Modifier.fillMaxSize()) {
            val w = this.size.width
            val h = this.size.height

            // 1. Shadow
            drawOval(
                brush = Brush.radialGradient(
                    colors = listOf(Color(0x55000000), Color.Transparent),
                    center = Offset(w * 0.5f, h * 0.90f),
                    radius = w * 0.45f
                ),
                topLeft = Offset(w * 0.12f, h * 0.80f),
                size = Size(w * 0.76f, h * 0.18f)
            )

            // 2. Back Speech Bubble (Gold/Champagne)
            val backBubble = Path().apply {
                addRoundRect(
                    androidx.compose.ui.geometry.RoundRect(
                        left = w * 0.32f,
                        top = h * 0.16f,
                        right = w * 0.88f,
                        bottom = h * 0.62f,
                        cornerRadius = CornerRadius(w * 0.14f, w * 0.14f)
                    )
                )
                moveTo(w * 0.76f, h * 0.58f)
                lineTo(w * 0.86f, h * 0.72f)
                lineTo(w * 0.68f, h * 0.62f)
                close()
            }
            drawPath(
                path = backBubble,
                brush = Brush.linearGradient(
                    colors = if (isSelected) {
                        listOf(Color(0xFFFFF176), Color(0xFFFFB300), Color(0xFFFF8F00))
                    } else {
                        listOf(Color(0xFFB0A8A0), Color(0xFF787068), Color(0xFF484038))
                    }
                )
            )

            // 3. Front Speech Bubble (Sunset Amber)
            val frontBubble = Path().apply {
                addRoundRect(
                    androidx.compose.ui.geometry.RoundRect(
                        left = w * 0.12f,
                        top = h * 0.32f,
                        right = w * 0.72f,
                        bottom = h * 0.78f,
                        cornerRadius = CornerRadius(w * 0.14f, w * 0.14f)
                    )
                )
                moveTo(w * 0.24f, h * 0.74f)
                lineTo(w * 0.14f, h * 0.88f)
                lineTo(w * 0.34f, h * 0.78f)
                close()
            }
            drawPath(
                path = frontBubble,
                brush = Brush.linearGradient(
                    colors = if (isSelected) {
                        listOf(Color(0xFFFF7A50), Color(0xFFFF5232), Color(0xFFC62828))
                    } else {
                        listOf(Color(0xFFD8D0C8), Color(0xFFA8A098), Color(0xFF686058))
                    },
                    start = Offset(w * 0.2f, h * 0.3f),
                    end = Offset(w * 0.7f, h * 0.8f)
                )
            )

            // 4. Glossy Specular Highlight
            drawOval(
                brush = Brush.radialGradient(
                    colors = listOf(Color(0x99FFFFFF), Color.Transparent),
                    center = Offset(w * 0.32f, h * 0.42f),
                    radius = w * 0.20f
                ),
                topLeft = Offset(w * 0.18f, h * 0.36f),
                size = Size(w * 0.35f, h * 0.18f)
            )

            // 5. Chat 3 Dots
            val dotColor = if (isSelected) Color.White else Color(0xFF383028)
            drawCircle(color = dotColor, radius = w * 0.035f, center = Offset(w * 0.28f, h * 0.54f))
            drawCircle(color = dotColor, radius = w * 0.035f, center = Offset(w * 0.42f, h * 0.54f))
            drawCircle(color = dotColor, radius = w * 0.035f, center = Offset(w * 0.56f, h * 0.54f))
        }
    }
}

/**
 * 3D Isometric / Claymorphic Me Navigation Icon - Luxury Gold Edition
 */
@Composable
fun Me3DNavIcon(
    modifier: Modifier = Modifier,
    size: Dp = 30.dp,
    isSelected: Boolean = false
) {
    Box(
        modifier = modifier.size(size),
        contentAlignment = Alignment.Center
    ) {
        Canvas(modifier = Modifier.fillMaxSize()) {
            val w = this.size.width
            val h = this.size.height

            // 1. Shadow
            drawOval(
                brush = Brush.radialGradient(
                    colors = listOf(Color(0x55000000), Color.Transparent),
                    center = Offset(w * 0.5f, h * 0.92f),
                    radius = w * 0.45f
                ),
                topLeft = Offset(w * 0.14f, h * 0.82f),
                size = Size(w * 0.72f, h * 0.16f)
            )

            // 2. 3D Body Base
            val bodyPath = Path().apply {
                moveTo(w * 0.18f, h * 0.86f)
                cubicTo(w * 0.18f, h * 0.64f, w * 0.82f, h * 0.64f, w * 0.82f, h * 0.86f)
                close()
            }
            drawPath(
                path = bodyPath,
                brush = Brush.verticalGradient(
                    colors = if (isSelected) {
                        listOf(Color(0xFFFF7A50), Color(0xFFFF5232), Color(0xFFC62828))
                    } else {
                        listOf(Color(0xFF8A8278), Color(0xFF5A5248), Color(0xFF383028))
                    }
                )
            )

            // 3. 3D Head Sphere
            drawCircle(
                brush = Brush.radialGradient(
                    colors = if (isSelected) {
                        listOf(Color(0xFFFFF9C4), Color(0xFFFFD54F), Color(0xFFFF8F00))
                    } else {
                        listOf(Color(0xFFFFFFFF), Color(0xFFD8D0C8), Color(0xFFA8A098))
                    },
                    center = Offset(w * 0.45f, h * 0.38f),
                    radius = w * 0.24f
                ),
                radius = w * 0.22f,
                center = Offset(w * 0.50f, h * 0.42f)
            )

            // 4. Golden VIP Crown on Head
            val crownPath = Path().apply {
                moveTo(w * 0.34f, h * 0.24f)
                lineTo(w * 0.30f, h * 0.14f)
                lineTo(w * 0.42f, h * 0.19f)
                lineTo(w * 0.50f, h * 0.11f)
                lineTo(w * 0.58f, h * 0.19f)
                lineTo(w * 0.70f, h * 0.14f)
                lineTo(w * 0.66f, h * 0.24f)
                close()
            }
            drawPath(
                path = crownPath,
                brush = Brush.verticalGradient(
                    colors = listOf(Color(0xFFFFF176), Color(0xFFFFB300), Color(0xFFFF8F00))
                )
            )
        }
    }
}

/**
 * Custom 3D Follow / Heart Icon - Luxury Sunset Coral & Gold
 */
@Composable
fun Follow3DIcon(
    isFollowing: Boolean,
    modifier: Modifier = Modifier,
    size: Dp = 20.dp
) {
    val infiniteTransition = rememberInfiniteTransition(label = "follow_3d_anim")
    val pulseScale by infiniteTransition.animateFloat(
        initialValue = 0.95f,
        targetValue = 1.05f,
        animationSpec = infiniteRepeatable(
            animation = tween(1400, easing = FastOutSlowInEasing),
            repeatMode = RepeatMode.Reverse
        ),
        label = "pulse"
    )

    Canvas(
        modifier = modifier
            .size(size)
            .scale(if (isFollowing) 1f else pulseScale)
    ) {
        val w = this.size.width
        val h = this.size.height

        if (!isFollowing) {
            drawCircle(
                color = Color(0x33000000),
                radius = w * 0.42f,
                center = Offset(w * 0.50f, h * 0.58f)
            )

            val heartShadowPath = Path().apply {
                moveTo(w * 0.50f, h * 0.88f)
                cubicTo(w * 0.18f, h * 0.65f, w * 0.08f, h * 0.42f, w * 0.22f, h * 0.26f)
                cubicTo(w * 0.35f, h * 0.12f, w * 0.46f, h * 0.22f, w * 0.50f, h * 0.34f)
                cubicTo(w * 0.54f, h * 0.22f, w * 0.65f, h * 0.12f, w * 0.78f, h * 0.26f)
                cubicTo(w * 0.92f, h * 0.42f, w * 0.82f, h * 0.65f, w * 0.50f, h * 0.88f)
                close()
            }
            drawPath(path = heartShadowPath, color = Color(0xFF38521F))

            val heartMainPath = Path().apply {
                moveTo(w * 0.50f, h * 0.82f)
                cubicTo(w * 0.18f, h * 0.59f, w * 0.08f, h * 0.36f, w * 0.22f, h * 0.20f)
                cubicTo(w * 0.35f, h * 0.06f, w * 0.46f, h * 0.16f, w * 0.50f, h * 0.28f)
                cubicTo(w * 0.54f, h * 0.16f, w * 0.65f, h * 0.06f, w * 0.78f, h * 0.20f)
                cubicTo(w * 0.92f, h * 0.36f, w * 0.82f, h * 0.59f, w * 0.50f, h * 0.82f)
                close()
            }
            drawPath(
                path = heartMainPath,
                brush = Brush.verticalGradient(
                    colors = listOf(
                        Color(0xFF8CFF1A),
                        Color(0xFF7FFF00),
                        Color(0xFF5AB800)
                    )
                )
            )

            drawCircle(
                brush = Brush.radialGradient(
                    colors = listOf(Color.White.copy(alpha = 0.85f), Color.White.copy(alpha = 0f)),
                    center = Offset(w * 0.32f, h * 0.28f),
                    radius = w * 0.14f
                ),
                radius = w * 0.14f,
                center = Offset(w * 0.32f, h * 0.28f)
            )

            val plusCenter = Offset(w * 0.75f, h * 0.72f)
            val plusRadius = w * 0.22f
            drawCircle(color = Color(0xFF1E1E24), radius = plusRadius + 1.5f, center = plusCenter)
            drawCircle(
                brush = Brush.radialGradient(
                    colors = listOf(Color(0xFFFFFFFF), Color(0xFFFFD54F)),
                    center = plusCenter,
                    radius = plusRadius
                ),
                radius = plusRadius,
                center = plusCenter
            )
            drawLine(
                color = Color.Black,
                start = Offset(plusCenter.x - plusRadius * 0.55f, plusCenter.y),
                end = Offset(plusCenter.x + plusRadius * 0.55f, plusCenter.y),
                strokeWidth = 2.dp.toPx(),
                cap = StrokeCap.Round
            )
            drawLine(
                color = Color.Black,
                start = Offset(plusCenter.x, plusCenter.y - plusRadius * 0.55f),
                end = Offset(plusCenter.x, plusCenter.y + plusRadius * 0.55f),
                strokeWidth = 2.dp.toPx(),
                cap = StrokeCap.Round
            )
        } else {
            drawCircle(
                color = Color(0x33000000),
                radius = w * 0.42f,
                center = Offset(w * 0.50f, h * 0.58f)
            )

            val heartShadowPath = Path().apply {
                moveTo(w * 0.50f, h * 0.88f)
                cubicTo(w * 0.18f, h * 0.65f, w * 0.08f, h * 0.42f, w * 0.22f, h * 0.26f)
                cubicTo(w * 0.35f, h * 0.12f, w * 0.46f, h * 0.22f, w * 0.50f, h * 0.34f)
                cubicTo(w * 0.54f, h * 0.22f, w * 0.65f, h * 0.12f, w * 0.78f, h * 0.26f)
                cubicTo(w * 0.92f, h * 0.42f, w * 0.82f, h * 0.65f, w * 0.50f, h * 0.88f)
                close()
            }
            drawPath(path = heartShadowPath, color = Color(0xFFB77900))

            val heartMainPath = Path().apply {
                moveTo(w * 0.50f, h * 0.82f)
                cubicTo(w * 0.18f, h * 0.59f, w * 0.08f, h * 0.36f, w * 0.22f, h * 0.20f)
                cubicTo(w * 0.35f, h * 0.06f, w * 0.46f, h * 0.16f, w * 0.50f, h * 0.28f)
                cubicTo(w * 0.54f, h * 0.16f, w * 0.65f, h * 0.06f, w * 0.78f, h * 0.20f)
                cubicTo(w * 0.92f, h * 0.36f, w * 0.82f, h * 0.59f, w * 0.50f, h * 0.82f)
                close()
            }
            drawPath(
                path = heartMainPath,
                brush = Brush.verticalGradient(
                    colors = listOf(Color(0xFFFFF176), Color(0xFFFFB300), Color(0xFFFF8F00))
                )
            )

            drawCircle(
                brush = Brush.radialGradient(
                    colors = listOf(Color.White.copy(alpha = 0.85f), Color.White.copy(alpha = 0f)),
                    center = Offset(w * 0.32f, h * 0.28f),
                    radius = w * 0.14f
                ),
                radius = w * 0.14f,
                center = Offset(w * 0.32f, h * 0.28f)
            )

            val checkPath = Path().apply {
                moveTo(w * 0.33f, h * 0.46f)
                lineTo(w * 0.46f, h * 0.59f)
                lineTo(w * 0.68f, h * 0.33f)
            }
            drawPath(
                path = checkPath,
                color = Color.White,
                style = Stroke(width = 2.5.dp.toPx(), cap = StrokeCap.Round, join = StrokeJoin.Round)
            )
        }
    }
}

/**
 * 3D Coin Icon alias for consistent styling
 */
@Composable
fun App3DCoinIcon(
    modifier: Modifier = Modifier,
    size: Dp = 24.dp
) {
    Coin3DIcon(modifier = modifier, size = size)
}



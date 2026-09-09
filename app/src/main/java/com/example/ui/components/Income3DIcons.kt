package com.example.ui.components

import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.size
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.RoundRect
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.drawscope.Fill
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.nativeCanvas
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.sin

/**
 * 3D Faceted Cyan-Azure Diamond with Specular Highlights & Glint
 */
@Composable
fun Diamond3DIcon(
    modifier: Modifier = Modifier,
    size: Dp = 42.dp,
    animated: Boolean = true
) {
    val infiniteTransition = rememberInfiniteTransition(label = "diamond_glint")
    val glintAlpha by if (animated) {
        infiniteTransition.animateFloat(
            initialValue = 0.35f,
            targetValue = 1f,
            animationSpec = infiniteRepeatable(
                animation = tween(1800, easing = FastOutSlowInEasing),
                repeatMode = RepeatMode.Reverse
            ),
            label = "glint_alpha"
        )
    } else {
        androidx.compose.runtime.remember { androidx.compose.runtime.mutableFloatStateOf(0.85f) }
    }

    Canvas(modifier = modifier.size(size)) {
        val w = this.size.width
        val h = this.size.height

        // Ambient cyan-blue drop glow
        drawCircle(
            brush = Brush.radialGradient(
                colors = listOf(
                    Color(0x5500E5FF),
                    Color(0x2200B0FF),
                    Color.Transparent
                ),
                center = Offset(w * 0.5f, h * 0.52f),
                radius = w * 0.48f
            )
        )

        // Lower Pavilion Triangles (Back Depth Layer)
        val pavilion = Path().apply {
            moveTo(w * 0.12f, h * 0.38f)
            lineTo(w * 0.88f, h * 0.38f)
            lineTo(w * 0.50f, h * 0.90f)
            close()
        }
        drawPath(
            path = pavilion,
            brush = Brush.verticalGradient(
                colors = listOf(
                    Color(0xFF0091EA),
                    Color(0xFF0277BD),
                    Color(0xFF01579B),
                    Color(0xFF002F6C)
                )
            )
        )

        // Center Left Pavilion Facet
        val pavLeft = Path().apply {
            moveTo(w * 0.12f, h * 0.38f)
            lineTo(w * 0.35f, h * 0.38f)
            lineTo(w * 0.50f, h * 0.90f)
            close()
        }
        drawPath(
            path = pavLeft,
            brush = Brush.linearGradient(
                colors = listOf(Color(0xFF00B0FF), Color(0xFF0277BD)),
                start = Offset(w * 0.12f, h * 0.38f),
                end = Offset(w * 0.50f, h * 0.90f)
            )
        )

        // Center Pavilion Facet (Brightest Core)
        val pavCenter = Path().apply {
            moveTo(w * 0.35f, h * 0.38f)
            lineTo(w * 0.65f, h * 0.38f)
            lineTo(w * 0.50f, h * 0.90f)
            close()
        }
        drawPath(
            path = pavCenter,
            brush = Brush.verticalGradient(
                colors = listOf(Color(0xFF80D8FF), Color(0xFF00B0FF), Color(0xFF0288D1))
            )
        )

        // Center Right Pavilion Facet
        val pavRight = Path().apply {
            moveTo(w * 0.65f, h * 0.38f)
            lineTo(w * 0.88f, h * 0.38f)
            lineTo(w * 0.50f, h * 0.90f)
            close()
        }
        drawPath(
            path = pavRight,
            brush = Brush.linearGradient(
                colors = listOf(Color(0xFF0091EA), Color(0xFF01579B)),
                start = Offset(w * 0.65f, h * 0.38f),
                end = Offset(w * 0.50f, h * 0.90f)
            )
        )

        // Upper Crown Section (Table + Bezels)
        val crownLeft = Path().apply {
            moveTo(w * 0.12f, h * 0.38f)
            lineTo(w * 0.28f, h * 0.16f)
            lineTo(w * 0.35f, h * 0.38f)
            close()
        }
        drawPath(
            path = crownLeft,
            brush = Brush.linearGradient(
                colors = listOf(Color(0xFFE0F7FA), Color(0xFF80DEEA), Color(0xFF00E5FF)),
                start = Offset(w * 0.28f, h * 0.16f),
                end = Offset(w * 0.12f, h * 0.38f)
            )
        )

        val crownCenter = Path().apply {
            moveTo(w * 0.28f, h * 0.16f)
            lineTo(w * 0.72f, h * 0.16f)
            lineTo(w * 0.65f, h * 0.38f)
            lineTo(w * 0.35f, h * 0.38f)
            close()
        }
        drawPath(
            path = crownCenter,
            brush = Brush.linearGradient(
                colors = listOf(Color(0xFFFFFFFF), Color(0xFFE1F5FE), Color(0xFF80D8FF), Color(0xFF00E5FF)),
                start = Offset(w * 0.5f, h * 0.16f),
                end = Offset(w * 0.5f, h * 0.38f)
            )
        )

        val crownRight = Path().apply {
            moveTo(w * 0.72f, h * 0.16f)
            lineTo(w * 0.88f, h * 0.38f)
            lineTo(w * 0.65f, h * 0.38f)
            close()
        }
        drawPath(
            path = crownRight,
            brush = Brush.linearGradient(
                colors = listOf(Color(0xFFB2EBF2), Color(0xFF4DD0E1), Color(0xFF0091EA)),
                start = Offset(w * 0.72f, h * 0.16f),
                end = Offset(w * 0.88f, h * 0.38f)
            )
        )

        // Facet wireframes & Specular Bevels
        val strokePaint = Color(0xBBFFFFFF)
        val strokeWidth = (w * 0.025f).coerceAtLeast(1.5f)

        drawLine(strokePaint, Offset(w * 0.28f, h * 0.16f), Offset(w * 0.72f, h * 0.16f), strokeWidth = strokeWidth)
        drawLine(strokePaint, Offset(w * 0.12f, h * 0.38f), Offset(w * 0.88f, h * 0.38f), strokeWidth = strokeWidth)
        drawLine(strokePaint, Offset(w * 0.28f, h * 0.16f), Offset(w * 0.35f, h * 0.38f), strokeWidth = strokeWidth)
        drawLine(strokePaint, Offset(w * 0.72f, h * 0.16f), Offset(w * 0.65f, h * 0.38f), strokeWidth = strokeWidth)
        drawLine(strokePaint, Offset(w * 0.35f, h * 0.38f), Offset(w * 0.50f, h * 0.90f), strokeWidth = strokeWidth)
        drawLine(strokePaint, Offset(w * 0.65f, h * 0.38f), Offset(w * 0.50f, h * 0.90f), strokeWidth = strokeWidth)

        // Sparkling Specular Star Glint on Top-Left Table Corner
        val starCx = w * 0.30f
        val starCy = h * 0.20f
        val starRadius = (w * 0.12f) * glintAlpha

        drawCircle(
            color = Color.White.copy(alpha = glintAlpha),
            radius = (w * 0.045f) * glintAlpha,
            center = Offset(starCx, starCy)
        )
        drawLine(
            color = Color.White.copy(alpha = glintAlpha * 0.9f),
            start = Offset(starCx - starRadius, starCy),
            end = Offset(starCx + starRadius, starCy),
            strokeWidth = 2.5f,
            cap = StrokeCap.Round
        )
        drawLine(
            color = Color.White.copy(alpha = glintAlpha * 0.9f),
            start = Offset(starCx, starCy - starRadius),
            end = Offset(starCx, starCy + starRadius),
            strokeWidth = 2.5f,
            cap = StrokeCap.Round
        )

        // Secondary small sparkle on bottom facet
        drawCircle(
            color = Color.White.copy(alpha = glintAlpha * 0.7f),
            radius = (w * 0.025f),
            center = Offset(w * 0.62f, h * 0.55f)
        )
    }
}

/**
 * 3D Treasure Vault & Income Chest with Glowing Diamonds & Gold Coins
 */
@Composable
fun IncomeVault3DIcon(
    modifier: Modifier = Modifier,
    size: Dp = 44.dp
) {
    Canvas(modifier = modifier.size(size)) {
        val w = this.size.width
        val h = this.size.height

        // Outer glow
        drawCircle(
            brush = Brush.radialGradient(
                colors = listOf(
                    Color(0x66FFD54F),
                    Color(0x3300E5FF),
                    Color.Transparent
                ),
                center = Offset(w * 0.5f, h * 0.5f),
                radius = w * 0.48f
            )
        )

        // Vault Base (Polished Dark Obsidian & Gold Body)
        drawRoundRect(
            brush = Brush.linearGradient(
                colors = listOf(Color(0xFF37474F), Color(0xFF263238), Color(0xFF102027))
            ),
            topLeft = Offset(w * 0.16f, h * 0.38f),
            size = Size(w * 0.68f, h * 0.48f),
            cornerRadius = CornerRadius(w * 0.10f, w * 0.10f)
        )

        // Gold Trim Border on Vault Chest
        drawRoundRect(
            brush = Brush.linearGradient(
                colors = listOf(Color(0xFFFFEE58), Color(0xFFFFB300), Color(0xFFFF8F00))
            ),
            topLeft = Offset(w * 0.16f, h * 0.38f),
            size = Size(w * 0.68f, h * 0.48f),
            cornerRadius = CornerRadius(w * 0.10f, w * 0.10f),
            style = Stroke(width = w * 0.05f)
        )

        // Chest Lid Top Curve (Gold & Mahogany)
        val lidPath = Path().apply {
            moveTo(w * 0.14f, h * 0.40f)
            cubicTo(
                w * 0.14f, h * 0.18f,
                w * 0.86f, h * 0.18f,
                w * 0.86f, h * 0.40f
            )
            close()
        }
        drawPath(
            path = lidPath,
            brush = Brush.verticalGradient(
                colors = listOf(Color(0xFFFFD54F), Color(0xFFFFA000), Color(0xFFE65100))
            )
        )

        // Gold Metal Straps
        drawRoundRect(
            color = Color(0xFFFFE082),
            topLeft = Offset(w * 0.28f, h * 0.22f),
            size = Size(w * 0.09f, h * 0.62f),
            cornerRadius = CornerRadius(w * 0.04f, w * 0.04f)
        )
        drawRoundRect(
            color = Color(0xFFFFE082),
            topLeft = Offset(w * 0.63f, h * 0.22f),
            size = Size(w * 0.09f, h * 0.62f),
            cornerRadius = CornerRadius(w * 0.04f, w * 0.04f)
        )

        // Glowing 3D Diamond Bursting from Chest Center
        val diaPath = Path().apply {
            moveTo(w * 0.50f, h * 0.12f)
            lineTo(w * 0.65f, h * 0.28f)
            lineTo(w * 0.50f, h * 0.48f)
            lineTo(w * 0.35f, h * 0.28f)
            close()
        }
        drawPath(
            path = diaPath,
            brush = Brush.linearGradient(
                colors = listOf(Color(0xFFFFFFFF), Color(0xFF00E5FF), Color(0xFF0288D1))
            )
        )
        // Diamond glint center
        drawCircle(
            color = Color.White,
            radius = w * 0.035f,
            center = Offset(w * 0.50f, h * 0.24f)
        )

        // Gold Keyhole Lock Emblem
        drawCircle(
            brush = Brush.radialGradient(
                colors = listOf(Color(0xFFFFE57F), Color(0xFFFF8F00))
            ),
            radius = w * 0.09f,
            center = Offset(w * 0.50f, h * 0.58f)
        )
        drawCircle(
            color = Color(0xFF3E2723),
            radius = w * 0.035f,
            center = Offset(w * 0.50f, h * 0.56f)
        )
        drawLine(
            color = Color(0xFF3E2723),
            start = Offset(w * 0.50f, h * 0.56f),
            end = Offset(w * 0.50f, h * 0.64f),
            strokeWidth = w * 0.03f,
            cap = StrokeCap.Round
        )
    }
}

/**
 * 3D Diamond-to-Coin Currency Exchange Flow Visualizer Icon
 */
@Composable
fun DiamondToCoinExchange3DIcon(
    modifier: Modifier = Modifier,
    size: Dp = 64.dp
) {
    Canvas(modifier = modifier.size(size)) {
        val w = this.size.width
        val h = this.size.height

        // Left Side: 3D Brilliant Diamond
        val diaLeft = Path().apply {
            moveTo(w * 0.22f, h * 0.30f)
            lineTo(w * 0.38f, h * 0.30f)
            lineTo(w * 0.46f, h * 0.44f)
            lineTo(w * 0.30f, h * 0.72f)
            lineTo(w * 0.14f, h * 0.44f)
            close()
        }
        drawPath(
            path = diaLeft,
            brush = Brush.linearGradient(
                colors = listOf(Color(0xFFE0F7FA), Color(0xFF00E5FF), Color(0xFF0288D1), Color(0xFF01579B)),
                start = Offset(w * 0.22f, h * 0.30f),
                end = Offset(w * 0.30f, h * 0.72f)
            )
        )
        // Diamond facets
        drawLine(Color(0xCCFFFFFF), Offset(w * 0.22f, h * 0.30f), Offset(w * 0.30f, h * 0.72f), strokeWidth = 2f)
        drawLine(Color(0xCCFFFFFF), Offset(w * 0.38f, h * 0.30f), Offset(w * 0.30f, h * 0.72f), strokeWidth = 2f)
        drawLine(Color(0xCCFFFFFF), Offset(w * 0.14f, h * 0.44f), Offset(w * 0.46f, h * 0.44f), strokeWidth = 1.5f)
        drawCircle(Color.White, radius = 3.5f, center = Offset(w * 0.24f, h * 0.34f))

        // Right Side: 3D 24k Gold Coin Stack
        // Coin 1 (Bottom)
        drawCircle(
            brush = Brush.linearGradient(
                colors = listOf(Color(0xFFFFB300), Color(0xFFE65100))
            ),
            radius = w * 0.16f,
            center = Offset(w * 0.72f, h * 0.54f)
        )
        // Coin 2 (Top Main)
        drawCircle(
            brush = Brush.radialGradient(
                colors = listOf(Color(0xFFFFF9C4), Color(0xFFFFD54F), Color(0xFFFF8F00)),
                center = Offset(w * 0.70f, h * 0.46f),
                radius = w * 0.18f
            ),
            radius = w * 0.17f,
            center = Offset(w * 0.72f, h * 0.48f)
        )
        // Inner Coin Rim
        drawCircle(
            brush = Brush.linearGradient(
                colors = listOf(Color(0xFFFFEE58), Color(0xFFFFA000))
            ),
            radius = w * 0.13f,
            center = Offset(w * 0.72f, h * 0.48f),
            style = Stroke(width = 2.5f)
        )
        // QIVO "Q" currency emblem on Gold Coin (no $ and no star)
        val qCoinPaint = android.graphics.Paint().apply {
            color = android.graphics.Color.parseColor("#B78103")
            textSize = w * 0.16f
            typeface = android.graphics.Typeface.create("sans-serif-black", android.graphics.Typeface.BOLD)
            textAlign = android.graphics.Paint.Align.CENTER
            isAntiAlias = true
        }
        val qCoinBounds = android.graphics.Rect()
        qCoinPaint.getTextBounds("Q", 0, 1, qCoinBounds)
        val qCoinY = h * 0.48f + (qCoinBounds.height() / 2f) - qCoinBounds.bottom
        drawContext.canvas.nativeCanvas.drawText("Q", w * 0.72f, qCoinY, qCoinPaint)

        // Middle Curved Dual Holographic Exchange Arrows
        // Arrow Top (Cyan gradient pointing right)
        val topArrow = Path().apply {
            moveTo(w * 0.42f, h * 0.34f)
            cubicTo(w * 0.48f, h * 0.24f, w * 0.56f, h * 0.24f, w * 0.60f, h * 0.32f)
        }
        drawPath(
            path = topArrow,
            brush = Brush.horizontalGradient(listOf(Color(0xFF00E5FF), Color(0xFFFFD54F))),
            style = Stroke(width = 3.5f, cap = StrokeCap.Round)
        )
        // Arrowhead Right
        val topHead = Path().apply {
            moveTo(w * 0.56f, h * 0.28f)
            lineTo(w * 0.61f, h * 0.33f)
            lineTo(w * 0.55f, h * 0.36f)
        }
        drawPath(
            path = topHead,
            color = Color(0xFFFFD54F),
            style = Stroke(width = 3f, cap = StrokeCap.Round, join = StrokeJoin.Round)
        )

        // Arrow Bottom (Gold gradient pointing left)
        val bottomArrow = Path().apply {
            moveTo(w * 0.60f, h * 0.66f)
            cubicTo(w * 0.54f, h * 0.76f, w * 0.46f, h * 0.76f, w * 0.42f, h * 0.68f)
        }
        drawPath(
            path = bottomArrow,
            brush = Brush.horizontalGradient(listOf(Color(0xFF00E5FF), Color(0xFFFFD54F))),
            style = Stroke(width = 3.5f, cap = StrokeCap.Round)
        )
        // Arrowhead Left
        val bottomHead = Path().apply {
            moveTo(w * 0.46f, h * 0.64f)
            lineTo(w * 0.41f, h * 0.67f)
            lineTo(w * 0.47f, h * 0.72f)
        }
        drawPath(
            path = bottomHead,
            color = Color(0xFF00E5FF),
            style = Stroke(width = 3f, cap = StrokeCap.Round, join = StrokeJoin.Round)
        )
    }
}

/**
 * 3D Diamond Reward Crown for Live Host Earnings
 */
@Composable
fun DiamondHostReward3DIcon(
    modifier: Modifier = Modifier,
    size: Dp = 38.dp
) {
    Canvas(modifier = modifier.size(size)) {
        val w = this.size.width
        val h = this.size.height

        // Golden Starburst Halo
        for (i in 0 until 8) {
            val angle = (i * PI / 4).toFloat()
            val sx = w * 0.5f + cos(angle) * (w * 0.36f)
            val sy = h * 0.5f + sin(angle) * (h * 0.36f)
            drawCircle(
                color = Color(0x66FFD54F),
                radius = w * 0.05f,
                center = Offset(sx, sy)
            )
        }

        // Diamond Center Piece
        val dia = Path().apply {
            moveTo(w * 0.32f, h * 0.28f)
            lineTo(w * 0.68f, h * 0.28f)
            lineTo(w * 0.80f, h * 0.44f)
            lineTo(w * 0.50f, h * 0.80f)
            lineTo(w * 0.20f, h * 0.44f)
            close()
        }
        drawPath(
            path = dia,
            brush = Brush.linearGradient(
                listOf(Color(0xFFE0F7FA), Color(0xFF00E5FF), Color(0xFF0288D1), Color(0xFF01579B))
            )
        )

        // Sparkle Glint
        drawCircle(
            color = Color.White,
            radius = w * 0.06f,
            center = Offset(w * 0.38f, h * 0.34f)
        )
    }
}

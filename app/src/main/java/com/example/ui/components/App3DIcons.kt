package com.example.ui.components

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.size
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.RoundRect
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Fill
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.nativeCanvas
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp

// ==========================================
// 1. SETTINGS SCREEN 3D ICONS - LUXURY EDITION
// ==========================================

/**
 * 3D Claymorphic Account & Security (Shield + Gold Lock) Icon
 */
@Composable
fun AccountSecurity3DIcon(
    modifier: Modifier = Modifier,
    size: Dp = 44.dp
) {
    Box(
        modifier = modifier.size(size),
        contentAlignment = Alignment.Center
    ) {
        Canvas(modifier = Modifier.fillMaxSize()) {
            val w = this.size.width
            val h = this.size.height

            // Soft Drop Shadow
            drawOval(
                brush = Brush.radialGradient(
                    colors = listOf(Color(0x442E1E00), Color.Transparent),
                    center = Offset(w * 0.5f, h * 0.90f),
                    radius = w * 0.45f
                ),
                topLeft = Offset(w * 0.12f, h * 0.78f),
                size = Size(w * 0.76f, h * 0.20f)
            )

            // 3D Shield Base (Warm Gold)
            val shieldPath = Path().apply {
                moveTo(w * 0.50f, h * 0.12f)
                lineTo(w * 0.82f, h * 0.26f)
                cubicTo(w * 0.82f, h * 0.60f, w * 0.65f, h * 0.82f, w * 0.50f, h * 0.88f)
                cubicTo(w * 0.35f, h * 0.82f, w * 0.18f, h * 0.60f, w * 0.18f, h * 0.26f)
                close()
            }
            drawPath(
                path = shieldPath,
                brush = Brush.linearGradient(
                    colors = listOf(Color(0xFFFFF176), Color(0xFFFFB300), Color(0xFFFF8F00)),
                    start = Offset(w * 0.2f, h * 0.1f),
                    end = Offset(w * 0.8f, h * 0.9f)
                )
            )

            // Inner Shield Bevel (Obsidian / Amber Core)
            val innerShield = Path().apply {
                moveTo(w * 0.50f, h * 0.20f)
                lineTo(w * 0.74f, h * 0.32f)
                cubicTo(w * 0.74f, h * 0.56f, w * 0.60f, h * 0.74f, w * 0.50f, h * 0.78f)
                cubicTo(w * 0.40f, h * 0.74f, w * 0.26f, h * 0.56f, w * 0.26f, h * 0.32f)
                close()
            }
            drawPath(
                path = innerShield,
                brush = Brush.linearGradient(
                    colors = listOf(Color(0xFFFF9800), Color(0xFFE65100), Color(0xFF3E1E00)),
                    start = Offset(w * 0.3f, h * 0.2f),
                    end = Offset(w * 0.7f, h * 0.8f)
                )
            )

            // 3D Lock Shackle (Gold)
            drawArc(
                brush = Brush.linearGradient(
                    colors = listOf(Color(0xFFFFFFFF), Color(0xFFFFD54F), Color(0xFFFF8F00)),
                    start = Offset(w * 0.4f, h * 0.35f),
                    end = Offset(w * 0.6f, h * 0.50f)
                ),
                startAngle = 180f,
                sweepAngle = 180f,
                useCenter = false,
                topLeft = Offset(w * 0.40f, h * 0.34f),
                size = Size(w * 0.20f, h * 0.22f),
                style = Stroke(width = w * 0.06f, cap = StrokeCap.Round)
            )

            // 3D Lock Body (Pure Gold)
            drawRoundRect(
                brush = Brush.linearGradient(
                    colors = listOf(Color(0xFFFFF9C4), Color(0xFFFFD54F), Color(0xFFFF8F00)),
                    start = Offset(w * 0.36f, h * 0.44f),
                    end = Offset(w * 0.64f, h * 0.68f)
                ),
                topLeft = Offset(w * 0.36f, h * 0.44f),
                size = Size(w * 0.28f, h * 0.24f),
                cornerRadius = CornerRadius(w * 0.05f, w * 0.05f)
            )

            // Keyhole
            drawCircle(
                color = Color(0xFF241400),
                radius = w * 0.035f,
                center = Offset(w * 0.50f, h * 0.53f)
            )
            drawRect(
                color = Color(0xFF241400),
                topLeft = Offset(w * 0.485f, h * 0.53f),
                size = Size(w * 0.03f, h * 0.07f)
            )
        }
    }
}

/**
 * 3D Claymorphic Dark Mode (Crescent Moon + Golden Star) Icon
 */
@Composable
fun DarkMode3DIcon(
    isDark: Boolean,
    modifier: Modifier = Modifier,
    size: Dp = 44.dp
) {
    Box(
        modifier = modifier.size(size),
        contentAlignment = Alignment.Center
    ) {
        Canvas(modifier = Modifier.fillMaxSize()) {
            val w = this.size.width
            val h = this.size.height

            // Soft Shadow
            drawOval(
                brush = Brush.radialGradient(
                    colors = listOf(Color(0x443E2400), Color.Transparent),
                    center = Offset(w * 0.5f, h * 0.90f),
                    radius = w * 0.45f
                ),
                topLeft = Offset(w * 0.12f, h * 0.78f),
                size = Size(w * 0.76f, h * 0.20f)
            )

            if (isDark) {
                // 3D Crescent Moon
                val moonPath = Path().apply {
                    moveTo(w * 0.65f, h * 0.16f)
                    cubicTo(w * 0.28f, h * 0.22f, w * 0.20f, h * 0.68f, w * 0.55f, h * 0.84f)
                    cubicTo(w * 0.38f, h * 0.72f, w * 0.38f, h * 0.34f, w * 0.65f, h * 0.16f)
                    close()
                }
                drawPath(
                    path = moonPath,
                    brush = Brush.linearGradient(
                        colors = listOf(Color(0xFFFFF9C4), Color(0xFFFFD54F), Color(0xFFFFB300), Color(0xFFFF8F00)),
                        start = Offset(w * 0.2f, h * 0.2f),
                        end = Offset(w * 0.7f, h * 0.8f)
                    )
                )

                // 3D Little Star
                val starCenter = Offset(w * 0.72f, h * 0.36f)
                val starPath = Path().apply {
                    moveTo(starCenter.x, starCenter.y - h * 0.12f)
                    lineTo(starCenter.x + w * 0.035f, starCenter.y - h * 0.035f)
                    lineTo(starCenter.x + w * 0.12f, starCenter.y)
                    lineTo(starCenter.x + w * 0.035f, starCenter.y + h * 0.035f)
                    lineTo(starCenter.x, starCenter.y + h * 0.12f)
                    lineTo(starCenter.x - w * 0.035f, starCenter.y + h * 0.035f)
                    lineTo(starCenter.x - w * 0.12f, starCenter.y)
                    lineTo(starCenter.x - w * 0.035f, starCenter.y - h * 0.035f)
                    close()
                }
                drawPath(
                    path = starPath,
                    brush = Brush.radialGradient(
                        colors = listOf(Color.White, Color(0xFFFFD54F)),
                        center = starCenter,
                        radius = w * 0.12f
                    )
                )
            } else {
                // 3D Sun Sphere
                drawCircle(
                    brush = Brush.radialGradient(
                        colors = listOf(Color(0xFFFFFFFF), Color(0xFFFFD54F), Color(0xFFFF9800)),
                        center = Offset(w * 0.44f, h * 0.44f),
                        radius = w * 0.32f
                    ),
                    radius = w * 0.28f,
                    center = Offset(w * 0.50f, h * 0.50f)
                )

                // 8 Sun Rays
                val numRays = 8
                val center = Offset(w * 0.50f, h * 0.50f)
                for (i in 0 until numRays) {
                    val angle = (i * 360f / numRays)
                    val rad = Math.toRadians(angle.toDouble())
                    val r1 = w * 0.34f
                    val r2 = w * 0.42f
                    drawLine(
                        color = Color(0xFFFFB300),
                        start = Offset(center.x + r1 * kotlin.math.cos(rad).toFloat(), center.y + r1 * kotlin.math.sin(rad).toFloat()),
                        end = Offset(center.x + r2 * kotlin.math.cos(rad).toFloat(), center.y + r2 * kotlin.math.sin(rad).toFloat()),
                        strokeWidth = w * 0.06f,
                        cap = StrokeCap.Round
                    )
                }
            }
        }
    }
}

/**
 * 3D Claymorphic Call Settings (Phone Receiver + Waves) Icon
 */
@Composable
fun CallSettings3DIcon(
    modifier: Modifier = Modifier,
    size: Dp = 44.dp
) {
    Box(
        modifier = modifier.size(size),
        contentAlignment = Alignment.Center
    ) {
        Canvas(modifier = Modifier.fillMaxSize()) {
            val w = this.size.width
            val h = this.size.height

            // Soft Shadow
            drawOval(
                brush = Brush.radialGradient(
                    colors = listOf(Color(0x442E1E00), Color.Transparent),
                    center = Offset(w * 0.5f, h * 0.90f),
                    radius = w * 0.45f
                ),
                topLeft = Offset(w * 0.12f, h * 0.78f),
                size = Size(w * 0.76f, h * 0.20f)
            )

            // Phone Handset Path (Sunset Amber / Gold)
            val phonePath = Path().apply {
                moveTo(w * 0.28f, h * 0.30f)
                cubicTo(w * 0.28f, h * 0.18f, w * 0.44f, h * 0.18f, w * 0.48f, h * 0.28f)
                lineTo(w * 0.52f, h * 0.38f)
                cubicTo(w * 0.54f, h * 0.44f, w * 0.50f, h * 0.50f, w * 0.44f, h * 0.54f)
                lineTo(w * 0.46f, h * 0.58f)
                cubicTo(w * 0.52f, h * 0.68f, w * 0.62f, h * 0.78f, w * 0.72f, h * 0.84f)
                lineTo(w * 0.76f, h * 0.82f)
                cubicTo(w * 0.80f, h * 0.76f, w * 0.86f, h * 0.72f, w * 0.92f, h * 0.74f)
                lineTo(w * 1.02f, h * 0.78f)
                cubicTo(w * 1.12f, h * 0.82f, w * 1.12f, h * 0.98f, w * 1.00f, h * 0.98f)
                cubicTo(w * 0.60f, h * 0.98f, w * 0.28f, h * 0.66f, w * 0.28f, h * 0.30f)
                close()
            }
            // Scale and center handset
            val handset = Path().apply {
                moveTo(w * 0.25f, h * 0.35f)
                lineTo(w * 0.35f, h * 0.25f)
                cubicTo(w * 0.42f, h * 0.18f, w * 0.52f, h * 0.22f, w * 0.56f, h * 0.32f)
                lineTo(w * 0.60f, h * 0.42f)
                cubicTo(w * 0.62f, h * 0.48f, w * 0.58f, h * 0.54f, w * 0.52f, h * 0.58f)
                cubicTo(w * 0.56f, h * 0.66f, w * 0.64f, h * 0.74f, w * 0.72f, h * 0.78f)
                cubicTo(w * 0.76f, h * 0.72f, w * 0.82f, h * 0.68f, w * 0.88f, h * 0.70f)
                lineTo(w * 0.98f, h * 0.74f)
                cubicTo(w * 1.08f, h * 0.78f, w * 1.12f, h * 0.88f, w * 1.05f, h * 0.95f)
                lineTo(w * 0.95f, h * 1.05f)
                cubicTo(w * 0.55f, h * 1.05f, w * 0.25f, h * 0.75f, w * 0.25f, h * 0.35f)
                close()
            }
            // Draw clean telephone handset
            drawRoundRect(
                brush = Brush.linearGradient(listOf(Color(0xFFFFF176), Color(0xFFFFB300), Color(0xFFFF8F00))),
                topLeft = Offset(w * 0.24f, h * 0.26f),
                size = Size(w * 0.52f, h * 0.52f),
                cornerRadius = CornerRadius(w * 0.16f, w * 0.16f)
            )

            // Audio Waves (Gold)
            drawArc(
                brush = Brush.linearGradient(listOf(Color(0xFFFFD54F), Color(0xFFFF8F00))),
                startAngle = -45f,
                sweepAngle = 90f,
                useCenter = false,
                topLeft = Offset(w * 0.54f, h * 0.20f),
                size = Size(w * 0.34f, h * 0.34f),
                style = Stroke(width = w * 0.06f, cap = StrokeCap.Round)
            )
        }
    }
}

/**
 * 3D Claymorphic Blocked List (Obsidian Shield + Prohibited Sign) Icon
 */
@Composable
fun BlockedList3DIcon(
    modifier: Modifier = Modifier,
    size: Dp = 44.dp
) {
    Box(
        modifier = modifier.size(size),
        contentAlignment = Alignment.Center
    ) {
        Canvas(modifier = Modifier.fillMaxSize()) {
            val w = this.size.width
            val h = this.size.height

            // Soft Shadow
            drawOval(
                brush = Brush.radialGradient(
                    colors = listOf(Color(0x442E1E00), Color.Transparent),
                    center = Offset(w * 0.5f, h * 0.90f),
                    radius = w * 0.45f
                ),
                topLeft = Offset(w * 0.12f, h * 0.78f),
                size = Size(w * 0.76f, h * 0.20f)
            )

            // 3D Outer Circle (Sunset Coral to Ruby)
            drawCircle(
                brush = Brush.linearGradient(
                    colors = listOf(Color(0xFFFF7A50), Color(0xFFFF5232), Color(0xFFC62828)),
                    start = Offset(w * 0.2f, h * 0.2f),
                    end = Offset(w * 0.8f, h * 0.8f)
                ),
                radius = w * 0.36f,
                center = Offset(w * 0.50f, h * 0.48f)
            )

            // Inner Ring (Prohibited slash)
            drawCircle(
                color = Color.White,
                radius = w * 0.24f,
                center = Offset(w * 0.50f, h * 0.48f),
                style = Stroke(width = w * 0.08f)
            )
            drawLine(
                color = Color.White,
                start = Offset(w * 0.34f, h * 0.32f),
                end = Offset(w * 0.66f, h * 0.64f),
                strokeWidth = w * 0.08f,
                cap = StrokeCap.Round
            )
        }
    }
}

/**
 * 3D Claymorphic Clear Cache (Broom + Sparkles) Icon
 */
@Composable
fun ClearCache3DIcon(
    modifier: Modifier = Modifier,
    size: Dp = 44.dp
) {
    Box(
        modifier = modifier.size(size),
        contentAlignment = Alignment.Center
    ) {
        Canvas(modifier = Modifier.fillMaxSize()) {
            val w = this.size.width
            val h = this.size.height

            // Soft Shadow
            drawOval(
                brush = Brush.radialGradient(
                    colors = listOf(Color(0x442E1E00), Color.Transparent),
                    center = Offset(w * 0.5f, h * 0.90f),
                    radius = w * 0.45f
                ),
                topLeft = Offset(w * 0.12f, h * 0.78f),
                size = Size(w * 0.76f, h * 0.20f)
            )

            // Broom Stick (24k Gold)
            drawLine(
                brush = Brush.linearGradient(listOf(Color(0xFFFFF176), Color(0xFFFFB300), Color(0xFFFF8F00))),
                start = Offset(w * 0.70f, h * 0.16f),
                end = Offset(w * 0.42f, h * 0.56f),
                strokeWidth = w * 0.08f,
                cap = StrokeCap.Round
            )

            // Broom Bristles (Sunset Coral)
            val bristlePath = Path().apply {
                moveTo(w * 0.46f, h * 0.52f)
                lineTo(w * 0.54f, h * 0.58f)
                lineTo(w * 0.32f, h * 0.84f)
                lineTo(w * 0.16f, h * 0.72f)
                close()
            }
            drawPath(
                path = bristlePath,
                brush = Brush.linearGradient(
                    colors = listOf(Color(0xFFFF8A65), Color(0xFFFF5232), Color(0xFFD83818)),
                    start = Offset(w * 0.4f, h * 0.5f),
                    end = Offset(w * 0.2f, h * 0.8f)
                )
            )

            // Magic Sparkles (Gold Stars)
            drawCircle(color = Color(0xFFFFD54F), radius = w * 0.05f, center = Offset(w * 0.24f, h * 0.32f))
            drawCircle(color = Color.White, radius = w * 0.035f, center = Offset(w * 0.78f, h * 0.62f))
        }
    }
}

/**
 * 3D Claymorphic About QIVO Icon
 */
@Composable
fun AboutQivo3DIcon(
    modifier: Modifier = Modifier,
    size: Dp = 44.dp
) {
    Box(
        modifier = modifier.size(size),
        contentAlignment = Alignment.Center
    ) {
        Canvas(modifier = Modifier.fillMaxSize()) {
            val w = this.size.width
            val h = this.size.height

            // Soft Shadow
            drawOval(
                brush = Brush.radialGradient(
                    colors = listOf(Color(0x442E1E00), Color.Transparent),
                    center = Offset(w * 0.5f, h * 0.90f),
                    radius = w * 0.45f
                ),
                topLeft = Offset(w * 0.12f, h * 0.78f),
                size = Size(w * 0.76f, h * 0.20f)
            )

            // Outer Radiant Sphere (Gold to Sunset Amber)
            drawCircle(
                brush = Brush.radialGradient(
                    colors = listOf(Color(0xFFFFFFFF), Color(0xFFFFD54F), Color(0xFFFF8F00)),
                    center = Offset(w * 0.42f, h * 0.42f),
                    radius = w * 0.36f
                ),
                radius = w * 0.36f,
                center = Offset(w * 0.50f, h * 0.48f)
            )

            // Letter "i" or info emblem
            drawCircle(color = Color(0xFF241400), radius = w * 0.05f, center = Offset(w * 0.50f, h * 0.36f))
            drawRoundRect(
                color = Color(0xFF241400),
                topLeft = Offset(w * 0.46f, h * 0.46f),
                size = Size(w * 0.08f, h * 0.22f),
                cornerRadius = CornerRadius(4f, 4f)
            )
        }
    }
}

/**
 * 3D Claymorphic Sign Out Icon
 */
@Composable
fun SignOut3DIcon(
    modifier: Modifier = Modifier,
    size: Dp = 44.dp
) {
    Box(
        modifier = modifier.size(size),
        contentAlignment = Alignment.Center
    ) {
        Canvas(modifier = Modifier.fillMaxSize()) {
            val w = this.size.width
            val h = this.size.height

            // Soft Shadow
            drawOval(
                brush = Brush.radialGradient(
                    colors = listOf(Color(0x442E1E00), Color.Transparent),
                    center = Offset(w * 0.5f, h * 0.90f),
                    radius = w * 0.45f
                ),
                topLeft = Offset(w * 0.12f, h * 0.78f),
                size = Size(w * 0.76f, h * 0.20f)
            )

            // Door Frame (Obsidian)
            drawRoundRect(
                brush = Brush.linearGradient(listOf(Color(0xFF3E3228), Color(0xFF1E1814))),
                topLeft = Offset(w * 0.18f, h * 0.20f),
                size = Size(w * 0.38f, h * 0.60f),
                cornerRadius = CornerRadius(6f, 6f)
            )

            // Arrow Exit (Sunset Coral)
            val arrowPath = Path().apply {
                moveTo(w * 0.45f, h * 0.50f)
                lineTo(w * 0.78f, h * 0.50f)
                moveTo(w * 0.66f, h * 0.38f)
                lineTo(w * 0.78f, h * 0.50f)
                lineTo(w * 0.66f, h * 0.62f)
            }
            drawPath(
                path = arrowPath,
                brush = Brush.linearGradient(listOf(Color(0xFFFF7A50), Color(0xFFFF5232))),
                style = Stroke(width = w * 0.08f, cap = StrokeCap.Round)
            )
        }
    }
}

/**
 * 3D Claymorphic Delete Account Icon
 */
@Composable
fun DeleteAccount3DIcon(
    modifier: Modifier = Modifier,
    size: Dp = 44.dp
) {
    Box(
        modifier = modifier.size(size),
        contentAlignment = Alignment.Center
    ) {
        Canvas(modifier = Modifier.fillMaxSize()) {
            val w = this.size.width
            val h = this.size.height

            // Trash Can Base (Sunset Coral)
            val trashPath = Path().apply {
                moveTo(w * 0.28f, h * 0.36f)
                lineTo(w * 0.72f, h * 0.36f)
                lineTo(w * 0.68f, h * 0.82f)
                lineTo(w * 0.32f, h * 0.82f)
                close()
            }
            drawPath(
                path = trashPath,
                brush = Brush.linearGradient(
                    colors = listOf(Color(0xFFFF7A50), Color(0xFFFF5232), Color(0xFFC62828)),
                    start = Offset(w * 0.3f, h * 0.3f),
                    end = Offset(w * 0.7f, h * 0.8f)
                )
            )

            // Trash Lid (Gold)
            drawRoundRect(
                brush = Brush.linearGradient(listOf(Color(0xFFFFF176), Color(0xFFFFB300))),
                topLeft = Offset(w * 0.22f, h * 0.26f),
                size = Size(w * 0.56f, h * 0.08f),
                cornerRadius = CornerRadius(4f, 4f)
            )
        }
    }
}

// ==========================================
// 2. SUPPORT & HELP SCREEN 3D ICONS
// ==========================================

@Composable
fun SupportHeadset3DIcon(modifier: Modifier = Modifier, size: Dp = 44.dp) {
    Support3DIcon(modifier = modifier, size = size)
}

@Composable
fun WhatsApp3DIcon(modifier: Modifier = Modifier, size: Dp = 44.dp) {
    Box(modifier = modifier.size(size), contentAlignment = Alignment.Center) {
        Canvas(modifier = Modifier.fillMaxSize()) {
            val w = this.size.width
            val h = this.size.height

            // Green Sphere
            drawCircle(
                brush = Brush.radialGradient(
                    colors = listOf(Color(0xFF69F0AE), Color(0xFF00E676), Color(0xFF00C853), Color(0xFF007E33)),
                    center = Offset(w * 0.42f, h * 0.42f),
                    radius = w * 0.36f
                ),
                radius = w * 0.36f,
                center = Offset(w * 0.50f, h * 0.48f)
            )

            // Phone icon
            drawCircle(color = Color.White, radius = w * 0.16f, center = Offset(w * 0.50f, h * 0.48f))
            drawCircle(color = Color(0xFF00C853), radius = w * 0.10f, center = Offset(w * 0.50f, h * 0.48f))
        }
    }
}

@Composable
fun EmailHelpdesk3DIcon(modifier: Modifier = Modifier, size: Dp = 44.dp) {
    MessageBlast3DIcon(modifier = modifier, size = size)
}

@Composable
fun FaqHelpCenter3DIcon(modifier: Modifier = Modifier, size: Dp = 44.dp) {
    AboutQivo3DIcon(modifier = modifier, size = size)
}

@Composable
fun SafetyCenter3DIcon(modifier: Modifier = Modifier, size: Dp = 44.dp) {
    AccountSecurity3DIcon(modifier = modifier, size = size)
}

// ==========================================
// ==========================================
// 3. ADMIN PANEL CUSTOM 3D ICONS - CLAYMORPHIC LUXURY
// ==========================================

/**
 * 3D Award / Transfer Coins Icon - Golden coins stack with distribution glow
 */
@Composable
fun AwardCoins3DIcon(
    modifier: Modifier = Modifier,
    size: Dp = 44.dp
) {
    Box(
        modifier = modifier.size(size),
        contentAlignment = Alignment.Center
    ) {
        Canvas(modifier = Modifier.fillMaxSize()) {
            val w = this.size.width
            val h = this.size.height

            // Soft Shadow
            drawOval(
                brush = Brush.radialGradient(
                    colors = listOf(Color(0x553E2A00), Color.Transparent),
                    center = Offset(w * 0.5f, h * 0.88f),
                    radius = w * 0.45f
                ),
                topLeft = Offset(w * 0.1f, h * 0.78f),
                size = Size(w * 0.8f, h * 0.20f)
            )

            // Bottom Coin
            drawOval(
                brush = Brush.linearGradient(
                    colors = listOf(Color(0xFFFFB300), Color(0xFFFF8F00), Color(0xFFC67100)),
                    start = Offset(w * 0.2f, h * 0.60f),
                    end = Offset(w * 0.8f, h * 0.82f)
                ),
                topLeft = Offset(w * 0.18f, h * 0.58f),
                size = Size(w * 0.64f, h * 0.24f)
            )

            // Middle Coin
            drawOval(
                brush = Brush.linearGradient(
                    colors = listOf(Color(0xFFFFD54F), Color(0xFFFFB300), Color(0xFFE65100)),
                    start = Offset(w * 0.2f, h * 0.44f),
                    end = Offset(w * 0.8f, h * 0.66f)
                ),
                topLeft = Offset(w * 0.18f, h * 0.42f),
                size = Size(w * 0.64f, h * 0.24f)
            )

            // Top Primary Coin
            drawOval(
                brush = Brush.linearGradient(
                    colors = listOf(Color(0xFFFFF9C4), Color(0xFFFFD54F), Color(0xFFFF9800)),
                    start = Offset(w * 0.15f, h * 0.18f),
                    end = Offset(w * 0.85f, h * 0.52f)
                ),
                topLeft = Offset(w * 0.15f, h * 0.18f),
                size = Size(w * 0.70f, h * 0.32f)
            )

            // Inner Coin Ring
            drawOval(
                brush = Brush.linearGradient(
                    colors = listOf(Color(0xFFFFE082), Color(0xFFFFA000)),
                    start = Offset(w * 0.25f, h * 0.22f),
                    end = Offset(w * 0.75f, h * 0.46f)
                ),
                topLeft = Offset(w * 0.24f, h * 0.22f),
                size = Size(w * 0.52f, h * 0.24f),
                style = Stroke(width = w * 0.035f)
            )

            // QIVO "Q" Currency Emblem (no star, no $)
            val qAwardPaint = android.graphics.Paint().apply {
                color = android.graphics.Color.parseColor("#9E6500")
                textSize = w * 0.16f
                typeface = android.graphics.Typeface.create("sans-serif-black", android.graphics.Typeface.BOLD)
                textAlign = android.graphics.Paint.Align.CENTER
                isAntiAlias = true
            }
            val qBounds = android.graphics.Rect()
            qAwardPaint.getTextBounds("Q", 0, 1, qBounds)
            val qY = h * 0.34f + (qBounds.height() / 2f) - qBounds.bottom
            drawContext.canvas.nativeCanvas.drawText("Q", w * 0.50f, qY, qAwardPaint)
        }
    }
}

/**
 * 3D Manage User Roles Icon - Royal Cobalt & Gold Authority Crest
 */
@Composable
fun ManageRoles3DIcon(
    modifier: Modifier = Modifier,
    size: Dp = 44.dp
) {
    Box(
        modifier = modifier.size(size),
        contentAlignment = Alignment.Center
    ) {
        Canvas(modifier = Modifier.fillMaxSize()) {
            val w = this.size.width
            val h = this.size.height

            // Soft Shadow
            drawOval(
                brush = Brush.radialGradient(
                    colors = listOf(Color(0x44002B49), Color.Transparent),
                    center = Offset(w * 0.5f, h * 0.90f),
                    radius = w * 0.42f
                ),
                topLeft = Offset(w * 0.12f, h * 0.78f),
                size = Size(w * 0.76f, h * 0.20f)
            )

            // Royal Shield Base (Deep Sapphire / Cyan)
            val shield = Path().apply {
                moveTo(w * 0.50f, h * 0.12f)
                lineTo(w * 0.84f, h * 0.24f)
                cubicTo(w * 0.84f, h * 0.60f, w * 0.64f, h * 0.82f, w * 0.50f, h * 0.88f)
                cubicTo(w * 0.36f, h * 0.82f, w * 0.16f, h * 0.60f, w * 0.16f, h * 0.24f)
                close()
            }
            drawPath(
                path = shield,
                brush = Brush.linearGradient(
                    colors = listOf(Color(0xFF38BDF8), Color(0xFF0284C7), Color(0xFF0369A1)),
                    start = Offset(w * 0.2f, h * 0.1f),
                    end = Offset(w * 0.8f, h * 0.9f)
                )
            )

            // Golden Crown in Center
            val crown = Path().apply {
                moveTo(w * 0.30f, h * 0.58f)
                lineTo(w * 0.25f, h * 0.38f)
                lineTo(w * 0.38f, h * 0.46f)
                lineTo(w * 0.50f, h * 0.32f)
                lineTo(w * 0.62f, h * 0.46f)
                lineTo(w * 0.75f, h * 0.38f)
                lineTo(w * 0.70f, h * 0.58f)
                close()
            }
            drawPath(
                path = crown,
                brush = Brush.linearGradient(
                    colors = listOf(Color(0xFFFFF9C4), Color(0xFFFFD54F), Color(0xFFFF8F00)),
                    start = Offset(w * 0.3f, h * 0.32f),
                    end = Offset(w * 0.7f, h * 0.58f)
                )
            )

            // Crown Jewels
            drawCircle(color = Color.White, radius = w * 0.03f, center = Offset(w * 0.50f, h * 0.32f))
            drawCircle(color = Color.White, radius = w * 0.025f, center = Offset(w * 0.25f, h * 0.38f))
            drawCircle(color = Color.White, radius = w * 0.025f, center = Offset(w * 0.75f, h * 0.38f))
        }
    }
}

/**
 * 3D Manage Reports Icon - Crimson & Coral Audit Shield with Caution Flag
 */
@Composable
fun ManageReports3DIcon(
    modifier: Modifier = Modifier,
    size: Dp = 44.dp
) {
    Box(
        modifier = modifier.size(size),
        contentAlignment = Alignment.Center
    ) {
        Canvas(modifier = Modifier.fillMaxSize()) {
            val w = this.size.width
            val h = this.size.height

            // Soft Shadow
            drawOval(
                brush = Brush.radialGradient(
                    colors = listOf(Color(0x444A0010), Color.Transparent),
                    center = Offset(w * 0.5f, h * 0.88f),
                    radius = w * 0.42f
                ),
                topLeft = Offset(w * 0.12f, h * 0.78f),
                size = Size(w * 0.76f, h * 0.20f)
            )

            // Hexagon / Rounded Diamond Shield (Crimson Gradient)
            val badge = Path().apply {
                moveTo(w * 0.50f, h * 0.12f)
                lineTo(w * 0.84f, h * 0.28f)
                lineTo(w * 0.84f, h * 0.62f)
                lineTo(w * 0.50f, h * 0.88f)
                lineTo(w * 0.16f, h * 0.62f)
                lineTo(w * 0.16f, h * 0.28f)
                close()
            }
            drawPath(
                path = badge,
                brush = Brush.linearGradient(
                    colors = listOf(Color(0xFFFB7185), Color(0xFFE11D48), Color(0xFF9F1239)),
                    start = Offset(w * 0.2f, h * 0.1f),
                    end = Offset(w * 0.8f, h * 0.9f)
                )
            )

            // Exclamation Mark / Alert Indicator (3D White / Amber)
            drawRoundRect(
                brush = Brush.linearGradient(listOf(Color(0xFFFFFFFF), Color(0xFFFFF1F2))),
                topLeft = Offset(w * 0.46f, h * 0.30f),
                size = Size(w * 0.08f, h * 0.26f),
                cornerRadius = CornerRadius(w * 0.04f, w * 0.04f)
            )
            drawCircle(
                color = Color.White,
                radius = w * 0.045f,
                center = Offset(w * 0.50f, h * 0.66f)
            )
        }
    }
}

/**
 * 3D Manage Advertisements Icon - Vibrant Qivo Orange & Gold Megaphone / Broadcast Horn
 */
@Composable
fun ManageAds3DIcon(
    modifier: Modifier = Modifier,
    size: Dp = 44.dp
) {
    Box(
        modifier = modifier.size(size),
        contentAlignment = Alignment.Center
    ) {
        Canvas(modifier = Modifier.fillMaxSize()) {
            val w = this.size.width
            val h = this.size.height

            // Soft Drop Shadow
            drawOval(
                brush = Brush.radialGradient(
                    colors = listOf(Color(0x44421A00), Color.Transparent),
                    center = Offset(w * 0.5f, h * 0.90f),
                    radius = w * 0.40f
                ),
                topLeft = Offset(w * 0.12f, h * 0.78f),
                size = Size(w * 0.76f, h * 0.18f)
            )

            // Megaphone Handle (Dark Amber / Gold)
            drawRoundRect(
                brush = Brush.linearGradient(listOf(Color(0xFFFFB300), Color(0xFFD97706))),
                topLeft = Offset(w * 0.26f, h * 0.52f),
                size = Size(w * 0.10f, h * 0.28f),
                cornerRadius = CornerRadius(w * 0.04f, w * 0.04f)
            )

            // Megaphone Body Flare (Qivo Orange -> Warm Coral)
            val horn = Path().apply {
                moveTo(w * 0.24f, h * 0.38f)
                lineTo(w * 0.66f, h * 0.20f)
                lineTo(w * 0.66f, h * 0.68f)
                lineTo(w * 0.24f, h * 0.50f)
                close()
            }
            drawPath(
                path = horn,
                brush = Brush.linearGradient(
                    colors = listOf(Color(0xFFFF9E7A), Color(0xFFFF5232), Color(0xFFEA580C)),
                    start = Offset(w * 0.24f, h * 0.35f),
                    end = Offset(w * 0.66f, h * 0.68f)
                )
            )

            // Back Cap (Gold)
            drawOval(
                brush = Brush.linearGradient(listOf(Color(0xFFFFF59D), Color(0xFFFFB300))),
                topLeft = Offset(w * 0.18f, h * 0.38f),
                size = Size(w * 0.12f, h * 0.12f)
            )

            // Front Bell Opening (3D Rim in Bright Gold)
            drawOval(
                brush = Brush.linearGradient(listOf(Color(0xFFFFF9C4), Color(0xFFFFD54F), Color(0xFFFF9800))),
                topLeft = Offset(w * 0.60f, h * 0.20f),
                size = Size(w * 0.14f, h * 0.48f)
            )

            // Broadcast Soundwaves (Arcs)
            drawArc(
                brush = Brush.linearGradient(listOf(Color(0xFFFFD54F), Color(0xFFFF8F00))),
                startAngle = -45f,
                sweepAngle = 90f,
                useCenter = false,
                topLeft = Offset(w * 0.66f, h * 0.22f),
                size = Size(w * 0.22f, h * 0.44f),
                style = Stroke(width = w * 0.05f, cap = StrokeCap.Round)
            )
        }
    }
}

/**
 * 3D Admin Analytics Icon - Emerald & Cyan Telemetry Hologram Chart
 */
@Composable
fun AdminAnalytics3DIcon(
    modifier: Modifier = Modifier,
    size: Dp = 44.dp
) {
    Box(
        modifier = modifier.size(size),
        contentAlignment = Alignment.Center
    ) {
        Canvas(modifier = Modifier.fillMaxSize()) {
            val w = this.size.width
            val h = this.size.height

            // Soft Shadow
            drawOval(
                brush = Brush.radialGradient(
                    colors = listOf(Color(0x44003B26), Color.Transparent),
                    center = Offset(w * 0.5f, h * 0.90f),
                    radius = w * 0.42f
                ),
                topLeft = Offset(w * 0.12f, h * 0.78f),
                size = Size(w * 0.76f, h * 0.18f)
            )

            // 3D Chart Base Platform
            drawRoundRect(
                brush = Brush.linearGradient(listOf(Color(0xFF0F172A), Color(0xFF1E293B))),
                topLeft = Offset(w * 0.12f, h * 0.70f),
                size = Size(w * 0.76f, h * 0.14f),
                cornerRadius = CornerRadius(w * 0.04f, w * 0.04f)
            )

            // Bar 1 (Left - Cyan)
            drawRoundRect(
                brush = Brush.linearGradient(listOf(Color(0xFF38BDF8), Color(0xFF0284C7))),
                topLeft = Offset(w * 0.22f, h * 0.48f),
                size = Size(w * 0.14f, h * 0.24f),
                cornerRadius = CornerRadius(w * 0.03f, w * 0.03f)
            )

            // Bar 2 (Center - Emerald)
            drawRoundRect(
                brush = Brush.linearGradient(listOf(Color(0xFF34D399), Color(0xFF059669))),
                topLeft = Offset(w * 0.43f, h * 0.32f),
                size = Size(w * 0.14f, h * 0.40f),
                cornerRadius = CornerRadius(w * 0.03f, w * 0.03f)
            )

            // Bar 3 (Right - Neon Lime / Gold Peak)
            drawRoundRect(
                brush = Brush.linearGradient(listOf(Color(0xFFA3E635), Color(0xFF65A30D))),
                topLeft = Offset(w * 0.64f, h * 0.18f),
                size = Size(w * 0.14f, h * 0.54f),
                cornerRadius = CornerRadius(w * 0.03f, w * 0.03f)
            )

            // Glowing Growth Trend Line
            val trendLine = Path().apply {
                moveTo(w * 0.28f, h * 0.44f)
                lineTo(w * 0.50f, h * 0.28f)
                lineTo(w * 0.72f, h * 0.14f)
            }
            drawPath(
                path = trendLine,
                brush = Brush.linearGradient(listOf(Color.White, Color(0xFFFDE047))),
                style = Stroke(width = w * 0.04f, cap = StrokeCap.Round)
            )
            drawCircle(
                color = Color.White,
                radius = w * 0.035f,
                center = Offset(w * 0.72f, h * 0.14f)
            )
        }
    }
}

/**
 * 3D Agency Center Icon - Modern Purple/Coral HQ emblem with 3D star and tiered architectural badge
 */
@Composable
fun Agency3DIcon(
    modifier: Modifier = Modifier,
    size: Dp = 44.dp
) {
    Box(
        modifier = modifier.size(size),
        contentAlignment = Alignment.Center
    ) {
        Canvas(modifier = Modifier.fillMaxSize()) {
            val w = this.size.width
            val h = this.size.height

            // Drop shadow
            drawOval(
                brush = Brush.radialGradient(
                    colors = listOf(Color(0x44311B92), Color.Transparent),
                    center = Offset(w * 0.5f, h * 0.90f),
                    radius = w * 0.42f
                ),
                topLeft = Offset(w * 0.12f, h * 0.78f),
                size = Size(w * 0.76f, h * 0.18f)
            )

            // Outer 3D Hexagonal / Rounded Shield
            val badge = Path().apply {
                moveTo(w * 0.50f, h * 0.10f)
                lineTo(w * 0.86f, h * 0.28f)
                lineTo(w * 0.86f, h * 0.65f)
                lineTo(w * 0.50f, h * 0.88f)
                lineTo(w * 0.14f, h * 0.65f)
                lineTo(w * 0.14f, h * 0.28f)
                close()
            }
            drawPath(
                path = badge,
                brush = Brush.linearGradient(
                    colors = listOf(Color(0xFFBA68C8), Color(0xFF7B1FA2), Color(0xFF4A148C)),
                    start = Offset(w * 0.2f, h * 0.1f),
                    end = Offset(w * 0.8f, h * 0.9f)
                )
            )

            // Inner Accent Shield (Coral / Gold gradient)
            val inner = Path().apply {
                moveTo(w * 0.50f, h * 0.18f)
                lineTo(w * 0.78f, h * 0.32f)
                lineTo(w * 0.78f, h * 0.60f)
                lineTo(w * 0.50f, h * 0.80f)
                lineTo(w * 0.22f, h * 0.60f)
                lineTo(w * 0.22f, h * 0.32f)
                close()
            }
            drawPath(
                path = inner,
                brush = Brush.linearGradient(
                    colors = listOf(Color(0xFFFF8A80), Color(0xFFFF5252), Color(0xFFD50000)),
                    start = Offset(w * 0.25f, h * 0.2f),
                    end = Offset(w * 0.75f, h * 0.8f)
                )
            )

            // 3D Agency Central Emblem (Golden Star / Diamond)
            val star = Path().apply {
                moveTo(w * 0.50f, h * 0.28f)
                lineTo(w * 0.57f, h * 0.44f)
                lineTo(w * 0.74f, h * 0.45f)
                lineTo(w * 0.60f, h * 0.56f)
                lineTo(w * 0.65f, h * 0.72f)
                lineTo(w * 0.50f, h * 0.62f)
                lineTo(w * 0.35f, h * 0.72f)
                lineTo(w * 0.40f, h * 0.56f)
                lineTo(w * 0.26f, h * 0.45f)
                lineTo(w * 0.43f, h * 0.44f)
                close()
            }
            drawPath(
                path = star,
                brush = Brush.linearGradient(
                    colors = listOf(Color(0xFFFFF9C4), Color(0xFFFFD54F), Color(0xFFFF9800)),
                    start = Offset(w * 0.3f, h * 0.28f),
                    end = Offset(w * 0.7f, h * 0.72f)
                )
            )

            // Sparkling Core
            drawCircle(
                color = Color.White,
                radius = w * 0.04f,
                center = Offset(w * 0.50f, h * 0.48f)
            )
        }
    }
}

/**
 * 3D Coin Seller Icon - Emerald Vault with Golden Coin Stack & Exchange Arrow
 */
@Composable
fun CoinSeller3DIcon(
    modifier: Modifier = Modifier,
    size: Dp = 44.dp
) {
    Box(
        modifier = modifier.size(size),
        contentAlignment = Alignment.Center
    ) {
        Canvas(modifier = Modifier.fillMaxSize()) {
            val w = this.size.width
            val h = this.size.height

            // Soft Shadow
            drawOval(
                brush = Brush.radialGradient(
                    colors = listOf(Color(0x44003314), Color.Transparent),
                    center = Offset(w * 0.5f, h * 0.90f),
                    radius = w * 0.44f
                ),
                topLeft = Offset(w * 0.10f, h * 0.78f),
                size = Size(w * 0.80f, h * 0.20f)
            )

            // Emerald Round Platter Base
            drawCircle(
                brush = Brush.linearGradient(
                    colors = listOf(Color(0xFF34D399), Color(0xFF059669), Color(0xFF064E3B)),
                    start = Offset(w * 0.2f, h * 0.1f),
                    end = Offset(w * 0.8f, h * 0.9f)
                ),
                radius = w * 0.42f,
                center = Offset(w * 0.50f, h * 0.48f)
            )

            // Gold Coin Stack on Base
            drawOval(
                brush = Brush.linearGradient(listOf(Color(0xFFFFB300), Color(0xFFE65100))),
                topLeft = Offset(w * 0.25f, h * 0.52f),
                size = Size(w * 0.50f, h * 0.18f)
            )
            drawOval(
                brush = Brush.linearGradient(listOf(Color(0xFFFFD54F), Color(0xFFFF8F00))),
                topLeft = Offset(w * 0.25f, h * 0.40f),
                size = Size(w * 0.50f, h * 0.18f)
            )
            drawOval(
                brush = Brush.linearGradient(listOf(Color(0xFFFFF9C4), Color(0xFFFFB300))),
                topLeft = Offset(w * 0.25f, h * 0.28f),
                size = Size(w * 0.50f, h * 0.20f)
            )

            // QIVO "Q" Currency Emblem (no star, no $)
            val qSellerPaint = android.graphics.Paint().apply {
                color = android.graphics.Color.parseColor("#8E5A00")
                textSize = w * 0.16f
                typeface = android.graphics.Typeface.create("sans-serif-black", android.graphics.Typeface.BOLD)
                textAlign = android.graphics.Paint.Align.CENTER
                isAntiAlias = true
            }
            val qSellerBounds = android.graphics.Rect()
            qSellerPaint.getTextBounds("Q", 0, 1, qSellerBounds)
            val qSellerY = h * 0.38f + (qSellerBounds.height() / 2f) - qSellerBounds.bottom
            drawContext.canvas.nativeCanvas.drawText("Q", w * 0.50f, qSellerY, qSellerPaint)
        }
    }
}

@Composable
fun AgencyCenter3DIcon(modifier: Modifier = Modifier, size: Dp = 44.dp) {
    Agency3DIcon(modifier = modifier, size = size)
}

// ==========================================
// 4. TASKS & REWARDS 3D ICONS
// ==========================================

@Composable
fun TaskReward3DIcon(modifier: Modifier = Modifier, size: Dp = 44.dp) {
    TasksCenter3DIcon(modifier = modifier, size = size)
}

@Composable
fun DailySignIn3DIcon(modifier: Modifier = Modifier, size: Dp = 44.dp) {
    TasksCenter3DIcon(modifier = modifier, size = size)
}

@Composable
fun VideoReward3DIcon(modifier: Modifier = Modifier, size: Dp = 44.dp) {
    GameCenter3DIcon(modifier = modifier, size = size)
}

@Composable
fun IdentityCheck3DIcon(modifier: Modifier = Modifier, size: Dp = 44.dp) {
    Verify3DIcon(modifier = modifier, size = size)
}

@Composable
fun FirstRecharge3DIcon(modifier: Modifier = Modifier, size: Dp = 44.dp) {
    Coin3DIcon(modifier = modifier, size = size)
}

// ==========================================
// 5. CHAT, ROOM & PROFILE 3D ICONS
// ==========================================

@Composable
fun Mic3DIcon(modifier: Modifier = Modifier, size: Dp = 32.dp) {
    Box(modifier = modifier.size(size), contentAlignment = Alignment.Center) {
        Canvas(modifier = Modifier.fillMaxSize()) {
            val w = this.size.width
            val h = this.size.height
            // Microphone capsule (Warm Gold gradient)
            drawRoundRect(
                brush = Brush.verticalGradient(listOf(Color(0xFFFFF176), Color(0xFFFFB300), Color(0xFFFF8F00))),
                topLeft = Offset(w * 0.35f, h * 0.15f),
                size = Size(w * 0.30f, h * 0.45f),
                cornerRadius = CornerRadius(w * 0.15f, w * 0.15f)
            )
            // Arc holder
            drawArc(
                brush = Brush.linearGradient(listOf(Color(0xFFFFD54F), Color(0xFFFF8F00))),
                startAngle = 0f,
                sweepAngle = 180f,
                useCenter = false,
                topLeft = Offset(w * 0.22f, h * 0.25f),
                size = Size(w * 0.56f, h * 0.42f),
                style = Stroke(width = w * 0.08f, cap = StrokeCap.Round)
            )
            // Stand
            drawLine(
                brush = Brush.linearGradient(listOf(Color(0xFFFFB300), Color(0xFFFF8F00))),
                start = Offset(w * 0.50f, h * 0.67f),
                end = Offset(w * 0.50f, h * 0.85f),
                strokeWidth = w * 0.08f,
                cap = StrokeCap.Round
            )
        }
    }
}

@Composable
fun MicMuted3DIcon(modifier: Modifier = Modifier, size: Dp = 32.dp) {
    Box(modifier = modifier.size(size), contentAlignment = Alignment.Center) {
        Canvas(modifier = Modifier.fillMaxSize()) {
            val w = this.size.width
            val h = this.size.height
            // Muted Gray/Red Mic
            drawRoundRect(
                color = Color.Gray,
                topLeft = Offset(w * 0.35f, h * 0.15f),
                size = Size(w * 0.30f, h * 0.45f),
                cornerRadius = CornerRadius(w * 0.15f, w * 0.15f)
            )
            // Red Slash
            drawLine(
                color = Color(0xFFFF5232),
                start = Offset(w * 0.20f, h * 0.20f),
                end = Offset(w * 0.80f, h * 0.80f),
                strokeWidth = w * 0.09f,
                cap = StrokeCap.Round
            )
        }
    }
}

@Composable
fun Send3DIcon(modifier: Modifier = Modifier, size: Dp = 32.dp) {
    Box(modifier = modifier.size(size), contentAlignment = Alignment.Center) {
        Canvas(modifier = Modifier.fillMaxSize()) {
            val w = this.size.width
            val h = this.size.height
            val sendPath = Path().apply {
                moveTo(w * 0.15f, h * 0.15f)
                lineTo(w * 0.88f, h * 0.50f)
                lineTo(w * 0.15f, h * 0.85f)
                lineTo(w * 0.32f, h * 0.50f)
                close()
            }
            drawPath(
                path = sendPath,
                brush = Brush.linearGradient(
                    colors = listOf(Color(0xFFFFF176), Color(0xFFFFB300), Color(0xFFFF8F00)),
                    start = Offset(w * 0.15f, h * 0.15f),
                    end = Offset(w * 0.88f, h * 0.85f)
                )
            )
        }
    }
}

@Composable
fun Seat3DIcon(modifier: Modifier = Modifier, size: Dp = 32.dp) {
    Box(modifier = modifier.size(size), contentAlignment = Alignment.Center) {
        Canvas(modifier = Modifier.fillMaxSize()) {
            val w = this.size.width
            val h = this.size.height
            // Luxury Chair Cushion
            drawRoundRect(
                brush = Brush.verticalGradient(listOf(Color(0xFFFFF176), Color(0xFFFFB300), Color(0xFFFF8F00))),
                topLeft = Offset(w * 0.22f, h * 0.30f),
                size = Size(w * 0.56f, h * 0.40f),
                cornerRadius = CornerRadius(w * 0.10f, w * 0.10f)
            )
            // Plus or crown sign
            drawLine(
                color = Color(0xFF3E2723),
                start = Offset(w * 0.50f, h * 0.42f),
                end = Offset(w * 0.50f, h * 0.58f),
                strokeWidth = w * 0.08f,
                cap = StrokeCap.Round
            )
            drawLine(
                color = Color(0xFF3E2723),
                start = Offset(w * 0.42f, h * 0.50f),
                end = Offset(w * 0.58f, h * 0.50f),
                strokeWidth = w * 0.08f,
                cap = StrokeCap.Round
            )
        }
    }
}

@Composable
fun AddPhoto3DIcon(modifier: Modifier = Modifier, size: Dp = 32.dp) {
    Box(modifier = modifier.size(size), contentAlignment = Alignment.Center) {
        Canvas(modifier = Modifier.fillMaxSize()) {
            val w = this.size.width
            val h = this.size.height
            drawRoundRect(
                brush = Brush.linearGradient(listOf(Color(0xFFFF8A65), Color(0xFFFF5232))),
                topLeft = Offset(w * 0.15f, h * 0.15f),
                size = Size(w * 0.70f, h * 0.70f),
                cornerRadius = CornerRadius(w * 0.12f, w * 0.12f)
            )
            // Plus
            drawLine(
                color = Color.White,
                start = Offset(w * 0.50f, h * 0.32f),
                end = Offset(w * 0.50f, h * 0.68f),
                strokeWidth = w * 0.09f,
                cap = StrokeCap.Round
            )
            drawLine(
                color = Color.White,
                start = Offset(w * 0.32f, h * 0.50f),
                end = Offset(w * 0.68f, h * 0.50f),
                strokeWidth = w * 0.09f,
                cap = StrokeCap.Round
            )
        }
    }
}

@Composable
fun Camera3DIcon(modifier: Modifier = Modifier, size: Dp = 32.dp) {
    Box(modifier = modifier.size(size), contentAlignment = Alignment.Center) {
        Canvas(modifier = Modifier.fillMaxSize()) {
            val w = this.size.width
            val h = this.size.height
            drawRoundRect(
                brush = Brush.linearGradient(listOf(Color(0xFFFFF176), Color(0xFFFFB300), Color(0xFFFF8F00))),
                topLeft = Offset(w * 0.16f, h * 0.24f),
                size = Size(w * 0.68f, h * 0.56f),
                cornerRadius = CornerRadius(w * 0.12f, w * 0.12f)
            )
            drawCircle(
                color = Color(0xFF2E1E00),
                radius = w * 0.18f,
                center = Offset(w * 0.50f, h * 0.52f)
            )
            drawCircle(
                color = Color(0xFFFFD54F),
                radius = w * 0.10f,
                center = Offset(w * 0.50f, h * 0.52f)
            )
        }
    }
}

@Composable
fun ProfileCategory3DIcon(
    categoryKey: String = "",
    category: String = "",
    modifier: Modifier = Modifier,
    size: Dp = 28.dp
) {
    Box(modifier = modifier.size(size), contentAlignment = Alignment.Center) {
        Canvas(modifier = Modifier.fillMaxSize()) {
            val w = this.size.width
            val h = this.size.height
            drawCircle(
                brush = Brush.radialGradient(
                    colors = listOf(Color(0xFFFFF176), Color(0xFFFFB300), Color(0xFFFF8F00)),
                    center = Offset(w * 0.42f, h * 0.42f),
                    radius = w * 0.48f
                ),
                radius = w * 0.42f,
                center = Offset(w * 0.50f, h * 0.50f)
            )
            drawCircle(
                color = Color(0xFF2E1E00),
                radius = w * 0.16f,
                center = Offset(w * 0.50f, h * 0.50f)
            )
        }
    }
}

/**
 * 3D Claymorphic Level Trophy & Star Icon
 */
@Composable
fun Level3DIcon(
    modifier: Modifier = Modifier,
    size: Dp = 38.dp,
    level: Int = 1
) {
    Box(
        modifier = modifier.size(size),
        contentAlignment = Alignment.Center
    ) {
        Canvas(modifier = Modifier.fillMaxSize()) {
            val w = this.size.width
            val h = this.size.height

            // Soft Gold Shadow
            drawOval(
                brush = Brush.radialGradient(
                    colors = listOf(Color(0x55FFA000), Color.Transparent),
                    center = Offset(w * 0.5f, h * 0.90f),
                    radius = w * 0.45f
                ),
                topLeft = Offset(w * 0.12f, h * 0.80f),
                size = Size(w * 0.76f, h * 0.18f)
            )

            // Trophy Cup Body Path
            val cupPath = Path().apply {
                moveTo(w * 0.24f, h * 0.20f)
                lineTo(w * 0.76f, h * 0.20f)
                cubicTo(w * 0.76f, h * 0.54f, w * 0.62f, h * 0.64f, w * 0.50f, h * 0.65f)
                cubicTo(w * 0.38f, h * 0.64f, w * 0.24f, h * 0.54f, w * 0.24f, h * 0.20f)
                close()
            }

            // Cup Base / Stem
            drawRoundRect(
                brush = Brush.linearGradient(
                    colors = listOf(Color(0xFFFFE082), Color(0xFFFFB300), Color(0xFFFF8F00)),
                    start = Offset(w * 0.42f, h * 0.60f),
                    end = Offset(w * 0.58f, h * 0.80f)
                ),
                topLeft = Offset(w * 0.44f, h * 0.62f),
                size = Size(w * 0.12f, h * 0.16f),
                cornerRadius = CornerRadius(w * 0.04f, w * 0.04f)
            )

            // Cup Pedestal Base
            drawRoundRect(
                brush = Brush.verticalGradient(
                    colors = listOf(Color(0xFFFFD54F), Color(0xFFFF8F00), Color(0xFFC43E00))
                ),
                topLeft = Offset(w * 0.26f, h * 0.74f),
                size = Size(w * 0.48f, h * 0.12f),
                cornerRadius = CornerRadius(w * 0.06f, w * 0.06f)
            )

            // Cup Left Handle
            drawArc(
                brush = Brush.linearGradient(
                    colors = listOf(Color(0xFFFFE082), Color(0xFFFF8F00))
                ),
                startAngle = 100f,
                sweepAngle = 160f,
                useCenter = false,
                topLeft = Offset(w * 0.10f, h * 0.24f),
                size = Size(w * 0.22f, h * 0.28f),
                style = Stroke(width = w * 0.07f, cap = StrokeCap.Round)
            )

            // Cup Right Handle
            drawArc(
                brush = Brush.linearGradient(
                    colors = listOf(Color(0xFFFFE082), Color(0xFFFF8F00))
                ),
                startAngle = 280f,
                sweepAngle = 160f,
                useCenter = false,
                topLeft = Offset(w * 0.68f, h * 0.24f),
                size = Size(w * 0.22f, h * 0.28f),
                style = Stroke(width = w * 0.07f, cap = StrokeCap.Round)
            )

            // Draw Trophy Cup with Radiant Gradient
            drawPath(
                path = cupPath,
                brush = Brush.radialGradient(
                    colors = listOf(Color(0xFFFFF9C4), Color(0xFFFFCA28), Color(0xFFFF8F00), Color(0xFFE65100)),
                    center = Offset(w * 0.45f, h * 0.32f),
                    radius = w * 0.42f
                )
            )

            // Embossed 3D Star on Cup
            val starPath = Path().apply {
                val cx = w * 0.50f
                val cy = h * 0.38f
                val rOut = w * 0.13f
                val rIn = w * 0.055f
                for (i in 0 until 5) {
                    val aOut = Math.toRadians((i * 72 - 90).toDouble())
                    val aIn = Math.toRadians((i * 72 + 36 - 90).toDouble())
                    val x1 = (cx + rOut * Math.cos(aOut)).toFloat()
                    val y1 = (cy + rOut * Math.sin(aOut)).toFloat()
                    val x2 = (cx + rIn * Math.cos(aIn)).toFloat()
                    val y2 = (cy + rIn * Math.sin(aIn)).toFloat()
                    if (i == 0) moveTo(x1, y1) else lineTo(x1, y1)
                    lineTo(x2, y2)
                }
                close()
            }
            drawPath(
                path = starPath,
                brush = Brush.radialGradient(
                    colors = listOf(Color(0xFFFFFFFF), Color(0xFFFFF59D), Color(0xFFFFB300)),
                    center = Offset(w * 0.48f, h * 0.35f),
                    radius = w * 0.12f
                )
            )
        }
    }
}

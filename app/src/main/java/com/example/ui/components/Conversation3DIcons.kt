package com.example.ui.components

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.Icon
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Fill
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

/**
 * 3D Claymorphic Photo / Camera Icon
 * Features beveled body, optical glass lens, specular glint, and camera shutter button.
 */
@Composable
fun PhotoGallery3DIcon(
    modifier: Modifier = Modifier,
    size: Dp = 20.dp
) {
    Canvas(modifier = modifier.size(size)) {
        val w = this.size.width
        val h = this.size.height

        // 1. Ambient drop shadow under camera body
        drawRoundRect(
            color = Color.Black.copy(alpha = 0.28f),
            topLeft = Offset(w * 0.10f, h * 0.26f),
            size = Size(w * 0.80f, h * 0.62f),
            cornerRadius = CornerRadius(w * 0.16f, w * 0.16f)
        )

        // 2. Camera top viewfinder / flash hump
        val humpPath = Path().apply {
            moveTo(w * 0.35f, h * 0.22f)
            cubicTo(w * 0.37f, h * 0.12f, w * 0.43f, h * 0.10f, w * 0.50f, h * 0.10f)
            cubicTo(w * 0.57f, h * 0.10f, w * 0.63f, h * 0.12f, w * 0.65f, h * 0.22f)
            close()
        }
        drawPath(
            path = humpPath,
            brush = Brush.verticalGradient(
                listOf(Color(0xFFFFD180), Color(0xFFFF9100))
            )
        )

        // 3. Shutter trigger button (Gold metallic pill on the left)
        drawRoundRect(
            brush = Brush.verticalGradient(listOf(Color(0xFFFFF59D), Color(0xFFFFB300))),
            topLeft = Offset(w * 0.20f, h * 0.14f),
            size = Size(w * 0.15f, h * 0.10f),
            cornerRadius = CornerRadius(w * 0.04f, w * 0.04f)
        )

        // 4. Main camera body with warm Sunset Coral & Amber gradient
        drawRoundRect(
            brush = Brush.verticalGradient(
                listOf(
                    Color(0xFFFFB74D), // Specular top highlight
                    Color(0xFFFF9800), // Pure warm orange
                    Color(0xFFF57C00), // Deep sunset
                    Color(0xFFE65100)  // Base shadow
                )
            ),
            topLeft = Offset(w * 0.08f, h * 0.22f),
            size = Size(w * 0.84f, h * 0.62f),
            cornerRadius = CornerRadius(w * 0.16f, w * 0.16f)
        )

        // 5. Specular bevel streak on top edge of body
        drawLine(
            brush = Brush.horizontalGradient(
                listOf(
                    Color.White.copy(alpha = 0.05f),
                    Color.White.copy(alpha = 0.75f),
                    Color.White.copy(alpha = 0.15f)
                )
            ),
            start = Offset(w * 0.18f, h * 0.26f),
            end = Offset(w * 0.82f, h * 0.26f),
            strokeWidth = w * 0.045f,
            cap = StrokeCap.Round
        )

        // 6. Camera Lens outer chrome ring
        drawCircle(
            brush = Brush.linearGradient(
                listOf(Color(0xFFFFF8E1), Color(0xFFFFB74D), Color(0xFF6D3000)),
                start = Offset(w * 0.35f, h * 0.35f),
                end = Offset(w * 0.65f, h * 0.75f)
            ),
            radius = w * 0.23f,
            center = Offset(w * 0.50f, h * 0.53f)
        )

        // 7. Lens dark obsidian core with deep indigo/black reflection
        drawCircle(
            brush = Brush.radialGradient(
                colors = listOf(Color(0xFF1E1B2E), Color(0xFF0F0B18), Color(0xFF050308)),
                center = Offset(w * 0.48f, h * 0.50f),
                radius = w * 0.19f
            ),
            radius = w * 0.18f,
            center = Offset(w * 0.50f, h * 0.53f)
        )

        // 8. Lens glass reflection glint
        drawCircle(
            color = Color.White.copy(alpha = 0.85f),
            radius = w * 0.045f,
            center = Offset(w * 0.44f, h * 0.47f)
        )
        drawCircle(
            color = Color(0xFF80D8FF).copy(alpha = 0.60f),
            radius = w * 0.025f,
            center = Offset(w * 0.56f, h * 0.59f)
        )
    }
}

/**
 * 3D Isometric / Claymorphic Voice Call Handset Icon
 * Features curved phone receiver, contoured handle, speaker / microphone cushions, and glossy shine.
 */
@Composable
fun VoiceCall3DIcon(
    modifier: Modifier = Modifier,
    size: Dp = 20.dp
) {
    Canvas(modifier = modifier.size(size)) {
        val w = this.size.width
        val h = this.size.height

        // 1. Ambient depth shadow
        drawCircle(
            color = Color.Black.copy(alpha = 0.25f),
            radius = w * 0.40f,
            center = Offset(w * 0.52f, h * 0.54f)
        )

        // Handset geometry (angled phone receiver):
        // Upper ear-piece capsule
        drawRoundRect(
            brush = Brush.radialGradient(
                colors = listOf(Color(0xFFB9F6CA), Color(0xFF00E676), Color(0xFF00A340)),
                center = Offset(w * 0.65f, h * 0.22f),
                radius = w * 0.22f
            ),
            topLeft = Offset(w * 0.52f, h * 0.12f),
            size = Size(w * 0.36f, h * 0.24f),
            cornerRadius = CornerRadius(w * 0.12f, w * 0.12f)
        )

        // Lower mic-piece capsule
        drawRoundRect(
            brush = Brush.radialGradient(
                colors = listOf(Color(0xFFB9F6CA), Color(0xFF00E676), Color(0xFF008733)),
                center = Offset(w * 0.25f, h * 0.70f),
                radius = w * 0.22f
            ),
            topLeft = Offset(w * 0.12f, h * 0.64f),
            size = Size(w * 0.36f, h * 0.24f),
            cornerRadius = CornerRadius(w * 0.12f, w * 0.12f)
        )

        // Connecting contoured handle
        val handlePath = Path().apply {
            moveTo(w * 0.65f, h * 0.28f)
            cubicTo(w * 0.35f, h * 0.32f, w * 0.30f, h * 0.55f, w * 0.26f, h * 0.72f)
        }
        drawPath(
            path = handlePath,
            brush = Brush.linearGradient(
                listOf(Color(0xFF00E676), Color(0xFF00C853), Color(0xFF007E33)),
                start = Offset(w * 0.65f, h * 0.28f),
                end = Offset(w * 0.26f, h * 0.72f)
            ),
            style = Stroke(width = w * 0.22f, cap = StrokeCap.Round)
        )

        // Specular highlight gleam along the outer curvature
        val gleamPath = Path().apply {
            moveTo(w * 0.62f, h * 0.25f)
            cubicTo(w * 0.40f, h * 0.32f, w * 0.34f, h * 0.52f, w * 0.28f, h * 0.68f)
        }
        drawPath(
            path = gleamPath,
            brush = Brush.linearGradient(
                listOf(
                    Color.White.copy(alpha = 0.85f),
                    Color.White.copy(alpha = 0.40f),
                    Color.White.copy(alpha = 0.05f)
                )
            ),
            style = Stroke(width = w * 0.055f, cap = StrokeCap.Round)
        )

        // Ear piece sound ports
        drawCircle(
            color = Color(0xFF00501E).copy(alpha = 0.45f),
            radius = w * 0.04f,
            center = Offset(w * 0.70f, h * 0.24f)
        )
        drawCircle(
            color = Color(0xFF00501E).copy(alpha = 0.45f),
            radius = w * 0.04f,
            center = Offset(w * 0.26f, h * 0.76f)
        )
    }
}

/**
 * 3D Isometric / Claymorphic Video Camera Icon
 * Features studio camera chassis, forward optical projection cone, glowing tally light, and glossy glass reflections.
 */
@Composable
fun VideoCall3DIcon(
    modifier: Modifier = Modifier,
    size: Dp = 20.dp
) {
    Canvas(modifier = modifier.size(size)) {
        val w = this.size.width
        val h = this.size.height

        // 1. Ambient drop shadow
        drawRoundRect(
            color = Color.Black.copy(alpha = 0.25f),
            topLeft = Offset(w * 0.12f, h * 0.28f),
            size = Size(w * 0.56f, h * 0.54f),
            cornerRadius = CornerRadius(w * 0.14f, w * 0.14f)
        )

        // 2. Optical conical projector lens (Right side)
        val lensCone = Path().apply {
            moveTo(w * 0.62f, h * 0.38f)
            lineTo(w * 0.90f, h * 0.22f)
            lineTo(w * 0.90f, h * 0.78f)
            lineTo(w * 0.62f, h * 0.62f)
            close()
        }
        drawPath(
            path = lensCone,
            brush = Brush.linearGradient(
                listOf(
                    Color(0xFF80D8FF),
                    Color(0xFF00B0FF),
                    Color(0xFF0080FF),
                    Color(0xFF0052CC)
                ),
                start = Offset(w * 0.62f, h * 0.50f),
                end = Offset(w * 0.90f, h * 0.50f)
            )
        )

        // Lens cone specular edge rim
        drawLine(
            brush = Brush.verticalGradient(
                listOf(Color.White, Color(0xFF80D8FF), Color(0xFF0052CC))
            ),
            start = Offset(w * 0.90f, h * 0.22f),
            end = Offset(w * 0.90f, h * 0.78f),
            strokeWidth = w * 0.05f,
            cap = StrokeCap.Round
        )

        // 3. Main camera chassis body
        drawRoundRect(
            brush = Brush.verticalGradient(
                listOf(
                    Color(0xFF80D8FF), // Luminous cyan top
                    Color(0xFF00B0FF), // Electric cyan
                    Color(0xFF0072FF), // Vibrant sapphire
                    Color(0xFF0049B7)  // Deep shadow blue
                )
            ),
            topLeft = Offset(w * 0.10f, h * 0.24f),
            size = Size(w * 0.54f, h * 0.54f),
            cornerRadius = CornerRadius(w * 0.14f, w * 0.14f)
        )

        // 4. Specular gloss streak on chassis top
        drawLine(
            brush = Brush.horizontalGradient(
                listOf(
                    Color.White.copy(alpha = 0.10f),
                    Color.White.copy(alpha = 0.85f),
                    Color.White.copy(alpha = 0.20f)
                )
            ),
            start = Offset(w * 0.18f, h * 0.30f),
            end = Offset(w * 0.58f, h * 0.30f),
            strokeWidth = w * 0.045f,
            cap = StrokeCap.Round
        )

        // 5. Camera recording indicator tally light (Ruby red glow dot)
        drawCircle(
            color = Color(0xFFFF1744),
            radius = w * 0.055f,
            center = Offset(w * 0.23f, h * 0.39f)
        )
        drawCircle(
            color = Color.White.copy(alpha = 0.75f),
            radius = w * 0.02f,
            center = Offset(w * 0.215f, h * 0.375f)
        )

        // 6. Camera tape deck / viewport bevel
        drawRoundRect(
            color = Color(0xFF003688).copy(alpha = 0.40f),
            topLeft = Offset(w * 0.20f, h * 0.50f),
            size = Size(w * 0.34f, h * 0.18f),
            cornerRadius = CornerRadius(w * 0.06f, w * 0.06f)
        )
    }
}

/**
 * 3D Claymorphic Luxury Gift Box Icon
 * Features ruby magenta cubic box, overhanging lid, golden metallic ribbon cross, and fluffy bow loop knot.
 */
@Composable
fun GiftBox3DIcon(
    modifier: Modifier = Modifier,
    size: Dp = 20.dp
) {
    Canvas(modifier = modifier.size(size)) {
        val w = this.size.width
        val h = this.size.height

        // 1. Ambient drop shadow under box
        drawRoundRect(
            color = Color.Black.copy(alpha = 0.25f),
            topLeft = Offset(w * 0.16f, h * 0.42f),
            size = Size(w * 0.68f, h * 0.50f),
            cornerRadius = CornerRadius(w * 0.10f, w * 0.10f)
        )

        // 2. Box base container (Emerald & Jade Gradient)
        drawRoundRect(
            brush = Brush.verticalGradient(
                listOf(
                    Color(0xFF00E676),
                    Color(0xFF00C853),
                    Color(0xFF007E33),
                    Color(0xFF004D20)
                )
            ),
            topLeft = Offset(w * 0.18f, h * 0.40f),
            size = Size(w * 0.64f, h * 0.48f),
            cornerRadius = CornerRadius(w * 0.08f, w * 0.08f)
        )

        // 3. Vertical gold ribbon on box base
        drawRect(
            brush = Brush.verticalGradient(
                listOf(Color(0xFFFFF9C4), Color(0xFFFFD54F), Color(0xFFFF8F00))
            ),
            topLeft = Offset(w * 0.43f, h * 0.40f),
            size = Size(w * 0.14f, h * 0.48f)
        )

        // 4. Overhanging Box Lid
        drawRoundRect(
            brush = Brush.verticalGradient(
                listOf(
                    Color(0xFF69F0AE),
                    Color(0xFF00C853),
                    Color(0xFF007E33)
                )
            ),
            topLeft = Offset(w * 0.12f, h * 0.26f),
            size = Size(w * 0.76f, h * 0.18f),
            cornerRadius = CornerRadius(w * 0.07f, w * 0.07f)
        )

        // 5. Specular top streak on lid
        drawLine(
            brush = Brush.horizontalGradient(
                listOf(Color.White.copy(alpha = 0.1f), Color.White.copy(alpha = 0.85f), Color.White.copy(alpha = 0.1f))
            ),
            start = Offset(w * 0.20f, h * 0.29f),
            end = Offset(w * 0.80f, h * 0.29f),
            strokeWidth = w * 0.035f,
            cap = StrokeCap.Round
        )

        // 6. Vertical gold ribbon on lid
        drawRect(
            brush = Brush.verticalGradient(
                listOf(Color(0xFFFFF9C4), Color(0xFFFFD54F), Color(0xFFFF8F00))
            ),
            topLeft = Offset(w * 0.43f, h * 0.26f),
            size = Size(w * 0.14f, h * 0.18f)
        )

        // 7. Ribbon bow loops on top
        // Left loop
        val leftBow = Path().apply {
            moveTo(w * 0.50f, h * 0.24f)
            cubicTo(w * 0.30f, h * 0.05f, w * 0.22f, h * 0.18f, w * 0.46f, h * 0.25f)
            close()
        }
        drawPath(
            path = leftBow,
            brush = Brush.linearGradient(listOf(Color(0xFFFFF59D), Color(0xFFFFD54F), Color(0xFFFF8F00)))
        )

        // Right loop
        val rightBow = Path().apply {
            moveTo(w * 0.50f, h * 0.24f)
            cubicTo(w * 0.70f, h * 0.05f, w * 0.78f, h * 0.18f, w * 0.54f, h * 0.25f)
            close()
        }
        drawPath(
            path = rightBow,
            brush = Brush.linearGradient(listOf(Color(0xFFFFF59D), Color(0xFFFFD54F), Color(0xFFFF8F00)))
        )

        // Center knot gem
        drawCircle(
            brush = Brush.radialGradient(
                colors = listOf(Color(0xFFFFFFFF), Color(0xFFFFD54F), Color(0xFFFF6F00)),
                center = Offset(w * 0.48f, h * 0.23f),
                radius = w * 0.07f
            ),
            radius = w * 0.06f,
            center = Offset(w * 0.50f, h * 0.25f)
        )
    }
}

/**
 * Premium 3D Action Pill Button for Conversation Screen
 * Features colored elevation shadow, glass specular rim, vibrant linear gradient, and custom 3D icon.
 */
@Composable
fun ConversationActionButton(
    text: String,
    icon: @Composable () -> Unit,
    gradientColors: List<Color>,
    shadowColor: Color,
    isEnabled: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    testTag: String = ""
) {
    val activeGradient = if (isEnabled) {
        Brush.verticalGradient(gradientColors)
    } else {
        Brush.verticalGradient(listOf(Color(0xFF4A4A52), Color(0xFF2C2C32)))
    }

    val activeShadow = if (isEnabled) shadowColor.copy(alpha = 0.45f) else Color.Transparent

    Surface(
        onClick = onClick,
        shape = RoundedCornerShape(19.dp),
        color = Color.Transparent,
        modifier = modifier
            .height(38.dp)
            .shadow(
                elevation = if (isEnabled) 4.5.dp else 1.dp,
                shape = RoundedCornerShape(19.dp),
                spotColor = activeShadow,
                ambientColor = activeShadow
            )
            .then(if (testTag.isNotEmpty()) Modifier.testTag(testTag) else Modifier)
    ) {
        Box(
            modifier = Modifier
                .background(activeGradient, shape = RoundedCornerShape(19.dp))
                .border(
                    width = 1.dp,
                    brush = Brush.verticalGradient(
                        if (isEnabled) listOf(
                            Color.White.copy(alpha = 0.45f),
                            Color.White.copy(alpha = 0.08f)
                        ) else listOf(
                            Color.White.copy(alpha = 0.15f),
                            Color.Transparent
                        )
                    ),
                    shape = RoundedCornerShape(19.dp)
                )
                .clip(RoundedCornerShape(19.dp))
                .padding(horizontal = 13.dp, vertical = 6.dp),
            contentAlignment = Alignment.Center
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.Center
            ) {
                Box(contentAlignment = Alignment.Center) {
                    icon()
                }
                Spacer(modifier = Modifier.width(6.dp))
                Text(
                    text = text,
                    color = if (isEnabled) Color.White else Color(0xFF9E9E9E),
                    fontSize = 12.5.sp,
                    fontWeight = FontWeight.ExtraBold,
                    letterSpacing = 0.2.sp
                )
            }
        }
    }
}

/**
 * 3D Frosted Back Button for Header
 */
@Composable
fun Conversation3DBackButton(
    onClick: () -> Unit,
    isDark: Boolean,
    modifier: Modifier = Modifier
) {
    Surface(
        onClick = onClick,
        shape = CircleShape,
        color = Color.Transparent,
        modifier = modifier
            .size(38.dp)
            .shadow(
                elevation = 3.dp,
                shape = CircleShape,
                spotColor = Color.Black.copy(alpha = if (isDark) 0.5f else 0.12f)
            )
            .testTag("chat_back_button")
    ) {
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(
                    if (isDark) {
                        Brush.radialGradient(
                            listOf(Color(0xFF2E2B3C), Color(0xFF191724))
                        )
                    } else {
                        Brush.radialGradient(
                            listOf(Color(0xFFFFFFFF), Color(0xFFF3F1F8))
                        )
                    },
                    shape = CircleShape
                )
                .border(
                    width = 1.dp,
                    brush = Brush.verticalGradient(
                        if (isDark) listOf(
                            Color.White.copy(alpha = 0.22f),
                            Color.White.copy(alpha = 0.04f)
                        ) else listOf(
                            Color.White,
                            Color.Black.copy(alpha = 0.08f)
                        )
                    ),
                    shape = CircleShape
                ),
            contentAlignment = Alignment.Center
        ) {
            Icon(
                imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                contentDescription = "Back",
                tint = if (isDark) Color(0xFFF5F3FF) else Color(0xFF1E1B2E),
                modifier = Modifier.size(19.dp)
            )
        }
    }
}

/**
 * 3D Elevated Send Button Container
 * Matches the QIVO signature sunset coral/peach or gold gradient with subtle specular rim.
 */
@Composable
fun Conversation3DSendButton(
    onClick: () -> Unit,
    isEnabled: Boolean,
    modifier: Modifier = Modifier
) {
    Surface(
        onClick = onClick,
        shape = CircleShape,
        color = Color.Transparent,
        enabled = isEnabled,
        modifier = modifier
            .size(46.dp)
            .shadow(
                elevation = if (isEnabled) 5.dp else 1.dp,
                shape = CircleShape,
                spotColor = if (isEnabled) Color(0xFF00C853).copy(alpha = 0.5f) else Color.Transparent
            )
            .testTag("chat_send_button")
    ) {
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(
                    if (isEnabled) {
                        Brush.verticalGradient(
                            listOf(
                                Color(0xFF00E676), // Electric Mint Emerald
                                Color(0xFF00C853), // Vivid Emerald Green
                                Color(0xFF007E33)  // Deep Jade
                            )
                        )
                    } else {
                        Brush.verticalGradient(listOf(Color(0xFF4A4A52), Color(0xFF2C2C32)))
                    },
                    shape = CircleShape
                )
                .border(
                    width = 1.dp,
                    brush = Brush.verticalGradient(
                        listOf(Color.White.copy(alpha = 0.5f), Color.White.copy(alpha = 0.1f))
                    ),
                    shape = CircleShape
                ),
            contentAlignment = Alignment.Center
        ) {
            Send3DIcon(size = 26.dp)
        }
    }
}

/**
 * 3D Elevated Mic Recording Button Container
 * Features tactile capsule with ambient gold aura and microphone icon.
 */
@Composable
fun Conversation3DMicButton(
    modifier: Modifier = Modifier
) {
    Box(
        modifier = modifier
            .size(46.dp)
            .shadow(
                elevation = 4.dp,
                shape = CircleShape,
                spotColor = Color(0xFFFFB300).copy(alpha = 0.45f)
            )
            .background(
                Brush.verticalGradient(
                    listOf(
                        Color(0xFF382E20),
                        Color(0xFF1E170F)
                    )
                ),
                shape = CircleShape
            )
            .border(
                width = 1.dp,
                brush = Brush.verticalGradient(
                    listOf(Color(0xFFFFD54F).copy(alpha = 0.6f), Color(0xFFFF8F00).copy(alpha = 0.2f))
                ),
                shape = CircleShape
            ),
        contentAlignment = Alignment.Center
    ) {
        Mic3DIcon(size = 26.dp)
    }
}

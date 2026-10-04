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
import androidx.compose.material.icons.filled.Check
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
                colors = listOf(Color(0xFFFFECB3), Color(0xFFFF8D00), Color(0xFFE65100)),
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
                colors = listOf(Color(0xFFFFECB3), Color(0xFFFF8D00), Color(0xFFBF360C)),
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
                listOf(Color(0xFFFF8D00), Color(0xFFFF6500), Color(0xFFBF360C)),
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
            color = Color(0xFF5A1A04).copy(alpha = 0.45f),
            radius = w * 0.04f,
            center = Offset(w * 0.70f, h * 0.24f)
        )
        drawCircle(
            color = Color(0xFF5A1A04).copy(alpha = 0.45f),
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

        // 2. Box base container (Sunset Amber & Gold Gradient)
        drawRoundRect(
            brush = Brush.verticalGradient(
                listOf(
                    Color(0xFFFF8D00),
                    Color(0xFFFF6500),
                    Color(0xFFE65100),
                    Color(0xFFBF360C)
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
                    Color(0xFFFFB300),
                    Color(0xFFFF8D00),
                    Color(0xFFFF6500)
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
                spotColor = if (isEnabled) Color(0xFFFF6500).copy(alpha = 0.5f) else Color.Transparent
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
                                Color(0xFFFF8D00), // Luminous Amber Gold
                                Color(0xFFFF6500), // Vibrant Sunset Orange
                                Color(0xFFD84315)  // Deep Burnt Amber
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

/**
 * 3D Claymorphic Smile / Emoji Icon
 * Features golden spherical clay face, glossy specular glint, and radiant warm smile.
 */
@Composable
fun SmileEmoji3DIcon(
    modifier: Modifier = Modifier,
    size: Dp = 22.dp
) {
    Canvas(modifier = modifier.size(size)) {
        val w = this.size.width
        val h = this.size.height

        // 1. Ambient drop shadow
        drawCircle(
            color = Color.Black.copy(alpha = 0.25f),
            radius = w * 0.45f,
            center = Offset(w * 0.50f, h * 0.54f)
        )

        // 2. 3D Golden sphere base
        drawCircle(
            brush = Brush.radialGradient(
                colors = listOf(
                    Color(0xFFFFF9C4), // Top left glint
                    Color(0xFFFFD54F), // Bright yellow
                    Color(0xFFFF9800), // Sunset amber
                    Color(0xFFE65100)  // Deep shadow rim
                ),
                center = Offset(w * 0.38f, h * 0.36f),
                radius = w * 0.50f
            ),
            radius = w * 0.44f,
            center = Offset(w * 0.50f, h * 0.50f)
        )

        // 3. Specular gloss glint
        drawCircle(
            color = Color.White.copy(alpha = 0.85f),
            radius = w * 0.08f,
            center = Offset(w * 0.36f, h * 0.32f)
        )

        // 4. Cheerful twinkling eyes
        // Left Eye
        drawRoundRect(
            color = Color(0xFF3E1F05),
            topLeft = Offset(w * 0.30f, h * 0.38f),
            size = Size(w * 0.09f, h * 0.14f),
            cornerRadius = CornerRadius(w * 0.045f, w * 0.045f)
        )
        // Right Eye
        drawRoundRect(
            color = Color(0xFF3E1F05),
            topLeft = Offset(w * 0.61f, h * 0.38f),
            size = Size(w * 0.09f, h * 0.14f),
            cornerRadius = CornerRadius(w * 0.045f, w * 0.045f)
        )

        // 5. Rosy blush cheeks
        drawCircle(
            color = Color(0xFFFF5252).copy(alpha = 0.35f),
            radius = w * 0.08f,
            center = Offset(w * 0.24f, h * 0.52f)
        )
        drawCircle(
            color = Color(0xFFFF5252).copy(alpha = 0.35f),
            radius = w * 0.08f,
            center = Offset(w * 0.76f, h * 0.52f)
        )

        // 6. 3D Smile curve
        val smilePath = Path().apply {
            moveTo(w * 0.32f, h * 0.58f)
            quadraticTo(w * 0.50f, h * 0.78f, w * 0.68f, h * 0.58f)
        }
        drawPath(
            path = smilePath,
            color = Color(0xFF3E1F05),
            style = Stroke(width = w * 0.07f, cap = StrokeCap.Round)
        )
    }
}

/**
 * 3D Claymorphic More Tools / Plus Icon
 * Features golden rounded container with isometric glowing plus.
 */
@Composable
fun MoreTools3DIcon(
    modifier: Modifier = Modifier,
    size: Dp = 22.dp
) {
    Canvas(modifier = modifier.size(size)) {
        val w = this.size.width
        val h = this.size.height

        // 1. Ambient drop shadow
        drawCircle(
            color = Color.Black.copy(alpha = 0.22f),
            radius = w * 0.44f,
            center = Offset(w * 0.50f, h * 0.53f)
        )

        // 2. Round gradient badge
        drawCircle(
            brush = Brush.radialGradient(
                colors = listOf(
                    Color(0xFFFFF176),
                    Color(0xFFFFB300),
                    Color(0xFFFF6500)
                ),
                center = Offset(w * 0.38f, h * 0.38f),
                radius = w * 0.48f
            ),
            radius = w * 0.43f,
            center = Offset(w * 0.50f, h * 0.50f)
        )

        // 3. Plus cross bars in pure white with soft shadow
        val barThickness = w * 0.12f
        val barLength = w * 0.46f

        // Horizontal bar
        drawRoundRect(
            color = Color.White,
            topLeft = Offset((w - barLength) / 2f, (h - barThickness) / 2f),
            size = Size(barLength, barThickness),
            cornerRadius = CornerRadius(barThickness / 2f, barThickness / 2f)
        )

        // Vertical bar
        drawRoundRect(
            color = Color.White,
            topLeft = Offset((w - barThickness) / 2f, (h - barLength) / 2f),
            size = Size(barThickness, barLength),
            cornerRadius = CornerRadius(barThickness / 2f, barThickness / 2f)
        )
    }
}

/**
 * Blue verified checkmark badge matching reference UI
 */
@Composable
fun VerifiedBlueCheckBadge(modifier: Modifier = Modifier, size: Dp = 15.dp) {
    Box(
        modifier = modifier
            .size(size)
            .clip(CircleShape)
            .background(
                Brush.linearGradient(
                    listOf(Color(0xFF29B6F6), Color(0xFF1976D2))
                )
            ),
        contentAlignment = Alignment.Center
    ) {
        Icon(
            imageVector = Icons.Default.Check,
            contentDescription = "Verified",
            tint = Color.White,
            modifier = Modifier.size(size * 0.70f)
        )
    }
}

/**
 * Green shield authentication badge matching reference UI
 */
@Composable
fun VerifiedGreenShieldBadge(modifier: Modifier = Modifier, size: Dp = 15.dp) {
    Box(
        modifier = modifier
            .size(size)
            .clip(CircleShape)
            .background(
                Brush.linearGradient(
                    listOf(Color(0xFF00E676), Color(0xFF00B0FF))
                )
            ),
        contentAlignment = Alignment.Center
    ) {
        Icon(
            imageVector = Icons.Default.Check,
            contentDescription = "Authenticated",
            tint = Color.White,
            modifier = Modifier.size(size * 0.70f)
        )
    }
}

/**
 * Intimacy Crystal Purple Heart Badge [ 💜 0 ]
 */
@Composable
fun IntimacyCrystalHeartBadge(
    count: Int = 0,
    modifier: Modifier = Modifier,
    onClick: () -> Unit = {}
) {
    Surface(
        onClick = onClick,
        shape = RoundedCornerShape(14.dp),
        color = Color(0x333F1052),
        border = androidx.compose.foundation.BorderStroke(1.dp, Color(0xFFD500F9).copy(alpha = 0.5f)),
        modifier = modifier.height(26.dp)
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 8.dp, vertical = 2.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(4.dp)
        ) {
            Text(text = "💜", fontSize = 12.sp)
            Text(
                text = "$count",
                fontSize = 11.5.sp,
                fontWeight = FontWeight.ExtraBold,
                color = Color.White
            )
        }
    }
}

/**
 * Hexagonal Safety / Report Warning Badge [ ⬡ ! ]
 */
@Composable
fun HexagonSafetyBadge(
    modifier: Modifier = Modifier,
    onClick: () -> Unit = {}
) {
    Surface(
        onClick = onClick,
        shape = RoundedCornerShape(8.dp),
        color = Color(0x331F2232),
        border = androidx.compose.foundation.BorderStroke(1.dp, Color(0x44FFFFFF)),
        modifier = modifier.size(28.dp)
    ) {
        Box(contentAlignment = Alignment.Center) {
            Text(
                text = "!",
                fontSize = 14.sp,
                fontWeight = FontWeight.Black,
                color = Color.White.copy(alpha = 0.85f)
            )
        }
    }
}

/**
 * Floating "Free × 2" Chat voucher ticket on bottom left
 */
@Composable
fun FloatingFreeChatCard(
    modifier: Modifier = Modifier,
    onClick: () -> Unit = {}
) {
    Box(
        modifier = modifier
            .clip(RoundedCornerShape(12.dp))
            .clickable(onClick = onClick)
    ) {
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            // "Free × 2" green top pill
            Box(
                modifier = Modifier
                    .clip(RoundedCornerShape(topStart = 8.dp, topEnd = 8.dp, bottomStart = 4.dp, bottomEnd = 4.dp))
                    .background(Color(0xFF00E676))
                    .padding(horizontal = 6.dp, vertical = 1.5.dp)
            ) {
                Text(
                    text = "Free × 2",
                    fontSize = 9.5.sp,
                    fontWeight = FontWeight.Black,
                    color = Color.Black
                )
            }
            // Pink Chat Ticket Voucher Card
            Box(
                modifier = Modifier
                    .size(width = 44.dp, height = 36.dp)
                    .clip(RoundedCornerShape(8.dp))
                    .background(
                        Brush.verticalGradient(
                            listOf(Color(0xFFFF4081), Color(0xFFC2185B))
                        )
                    )
                    .border(1.dp, Color(0xFFFF80AB), RoundedCornerShape(8.dp)),
                contentAlignment = Alignment.Center
            ) {
                Text(
                    text = "Chat",
                    fontSize = 10.sp,
                    fontWeight = FontWeight.Black,
                    color = Color.White
                )
            }
        }
    }
}

/**
 * Floating Fast Forward / Collapse Button [ >> ]
 */
@Composable
fun FloatingFastForwardPill(
    modifier: Modifier = Modifier,
    onClick: () -> Unit = {}
) {
    Surface(
        onClick = onClick,
        shape = RoundedCornerShape(topStart = 16.dp, bottomStart = 16.dp),
        color = Color(0x551E2232),
        border = androidx.compose.foundation.BorderStroke(1.dp, Color(0x33FFFFFF)),
        modifier = modifier.height(30.dp)
    ) {
        Box(
            modifier = Modifier.padding(horizontal = 10.dp),
            contentAlignment = Alignment.Center
        ) {
            Text(
                text = "»",
                fontSize = 18.sp,
                fontWeight = FontWeight.Bold,
                color = Color.White.copy(alpha = 0.8f)
            )
        }
    }
}

/**
 * Face Authentication Verification Banner matching screenshot
 */
@Composable
fun FaceAuthenticationBanner(
    userName: String = "She",
    isFemale: Boolean = true,
    modifier: Modifier = Modifier
) {
    val pronoun = if (isFemale) "She" else "He"
    Surface(
        shape = RoundedCornerShape(16.dp),
        color = Color(0xFF181B28),
        border = androidx.compose.foundation.BorderStroke(1.dp, Color(0x22FFFFFF)),
        modifier = modifier
            .fillMaxWidth()
            .padding(horizontal = 12.dp, vertical = 6.dp)
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 14.dp, vertical = 12.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Box(
                modifier = Modifier
                    .size(24.dp)
                    .clip(CircleShape)
                    .background(Color(0xFF00E676)),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    imageVector = Icons.Default.Check,
                    contentDescription = null,
                    tint = Color.White,
                    modifier = Modifier.size(16.dp)
                )
            }

            Spacer(modifier = Modifier.width(12.dp))

            Text(
                text = "$pronoun has passed the face authentication.Feel free to make friends.",
                fontSize = 12.5.sp,
                fontWeight = FontWeight.Medium,
                color = Color(0xFFB0B5C9),
                lineHeight = 16.sp
            )
        }
    }
}

/**
 * 3D Pie Chart Icon for "My Data" tile in MeScreen matching screenshot
 */
@Composable
fun MyData3DIcon(modifier: Modifier = Modifier, size: Dp = 44.dp) {
    Canvas(modifier = modifier.size(size)) {
        val w = this.size.width
        val h = this.size.height
        val center = Offset(w / 2f, h / 2f)
        val radius = (w / 2f) * 0.85f

        // Pie slice 1 (Top-Left: Soft Violet / Lavender)
        drawArc(
            color = Color(0xFF9C27B0),
            startAngle = 180f,
            sweepAngle = 90f,
            useCenter = true,
            size = Size(radius * 2, radius * 2),
            topLeft = Offset(center.x - radius, center.y - radius)
        )

        // Pie slice 2 (Top-Right: Bright Lilac / Purple)
        drawArc(
            color = Color(0xFFBA68C8),
            startAngle = 270f,
            sweepAngle = 90f,
            useCenter = true,
            size = Size(radius * 2, radius * 2),
            topLeft = Offset(center.x - radius + 2f, center.y - radius - 2f)
        )

        // Pie slice 3 (Bottom: Deep Plum / Violet)
        drawArc(
            color = Color(0xFFCE93D8),
            startAngle = 0f,
            sweepAngle = 180f,
            useCenter = true,
            size = Size(radius * 2, radius * 2),
            topLeft = Offset(center.x - radius, center.y - radius)
        )

        // Specular 3D center glint
        drawCircle(
            color = Color.White.copy(alpha = 0.35f),
            radius = radius * 0.22f,
            center = center
        )
    }
}

/**
 * 3D Checklist Clipboard Icon for "Task Center" matching screenshot
 */
@Composable
fun TaskCenterClipboard3DIcon(modifier: Modifier = Modifier, size: Dp = 44.dp) {
    Canvas(modifier = modifier.size(size)) {
        val w = this.size.width
        val h = this.size.height

        // Board background (Pastel Blue / Cyan Slate)
        drawRoundRect(
            brush = Brush.verticalGradient(
                listOf(Color(0xFF90CAF9), Color(0xFF64B5F6))
            ),
            topLeft = Offset(w * 0.15f, h * 0.12f),
            size = Size(w * 0.70f, h * 0.82f),
            cornerRadius = CornerRadius(8.dp.toPx(), 8.dp.toPx())
        )

        // White paper sheet
        drawRoundRect(
            color = Color.White,
            topLeft = Offset(w * 0.22f, h * 0.22f),
            size = Size(w * 0.56f, h * 0.66f),
            cornerRadius = CornerRadius(4.dp.toPx(), 4.dp.toPx())
        )

        // Top metal clip
        drawRoundRect(
            color = Color(0xFFB0BEC5),
            topLeft = Offset(w * 0.35f, h * 0.08f),
            size = Size(w * 0.30f, h * 0.14f),
            cornerRadius = CornerRadius(3.dp.toPx(), 3.dp.toPx())
        )

        // Green checkmarks on list
        val checkY1 = h * 0.34f
        val checkY2 = h * 0.52f
        val checkY3 = h * 0.70f
        listOf(checkY1, checkY2, checkY3).forEach { y ->
            // Check circle
            drawCircle(
                color = Color(0xFF00E676),
                radius = 3.dp.toPx(),
                center = Offset(w * 0.32f, y)
            )
            // Task line
            drawRoundRect(
                color = Color(0xFFCFD8DC),
                topLeft = Offset(w * 0.42f, y - 1.5.dp.toPx()),
                size = Size(w * 0.30f, 3.dp.toPx()),
                cornerRadius = CornerRadius(1.5.dp.toPx(), 1.5.dp.toPx())
            )
        }
    }
}

/**
 * 3D Qivo Agency Hero Badge for Join Agency top banner matching screenshot
 */
@Composable
fun QivoAgency3DHeroBadge(modifier: Modifier = Modifier, size: Dp = 90.dp) {
    Box(
        modifier = modifier
            .size(size)
            .clip(RoundedCornerShape(22.dp))
            .background(
                Brush.linearGradient(
                    listOf(Color(0xFF26A69A), Color(0xFF00897B), Color(0xFF004D40))
                )
            )
            .border(
                2.5.dp,
                Brush.linearGradient(listOf(Color(0xFF80CBC4), Color(0xFF004D40))),
                RoundedCornerShape(22.dp)
            ),
        contentAlignment = Alignment.Center
    ) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center
        ) {
            // "Qivo" Brand Tag
            Text(
                text = "QIVO",
                fontSize = 17.sp,
                fontWeight = FontWeight.Black,
                color = Color.White,
                letterSpacing = 1.sp
            )
            Spacer(modifier = Modifier.height(3.dp))
            // Mascot characters graphic simulation
            Row(
                horizontalArrangement = Arrangement.Center,
                verticalAlignment = Alignment.CenterVertically
            ) {
                // Orange mascot
                Box(
                    modifier = Modifier
                        .size(24.dp)
                        .clip(CircleShape)
                        .background(Color(0xFFFF7043)),
                    contentAlignment = Alignment.Center
                ) {
                    Text("👀", fontSize = 11.sp)
                }
                Spacer(modifier = Modifier.width(3.dp))
                // Green mascot
                Box(
                    modifier = Modifier
                        .size(30.dp)
                        .clip(CircleShape)
                        .background(Color(0xFF00E676)),
                    contentAlignment = Alignment.Center
                ) {
                    Text("😄", fontSize = 14.sp)
                }
            }
        }
    }
}

/**
 * Iridescent Mint-Teal-Cyan fluid wave background for MeScreen top header matching Screenshot 2 exactly
 */
@Composable
fun MeScreenMintWaveAtmosphere(modifier: Modifier = Modifier) {
    Canvas(modifier = modifier.fillMaxWidth()) {
        val w = size.width
        val h = size.height

        // 1. Base vibrant teal-mint radiant gradient
        drawRect(
            brush = Brush.verticalGradient(
                colors = listOf(
                    Color(0xFF56BCA8), // Radiant Mint Cyan
                    Color(0xFF439E8C), // Mid Teal
                    Color(0xFF2D7366), // Deep Mint
                    Color(0xFF1E463E), // Dark Pine
                    Color(0xFF131A26), // Transition Dark Navy
                    Color(0xFF10121D)  // Canvas Midnight
                ),
                startY = 0f,
                endY = h
            )
        )

        // 2. Signature Sunset Orange-Yellow Top Glow Overlay matching Chat List and Home screens
        drawRect(
            brush = Brush.verticalGradient(
                colors = listOf(
                    Color(0xE6E65100), // Vibrant deep sunset orange at the top
                    Color(0x80FF9100), // Amber mid glow
                    Color(0x30FFD54F), // Soft gold aura
                    Color.Transparent
                ),
                startY = 0f,
                endY = h * 0.70f
            )
        )

        // 3. Swirling 3D Fluid Ribbon (Upper Glow Curve)
        val path1 = androidx.compose.ui.graphics.Path().apply {
            moveTo(w * 0.15f, 0f)
            cubicTo(
                w * 0.35f, h * 0.30f,
                w * 0.85f, h * 0.10f,
                w * 1.05f, h * 0.65f
            )
            lineTo(w, 0f)
            close()
        }
        drawPath(
            path = path1,
            brush = Brush.linearGradient(
                colors = listOf(
                    Color(0xFF86EFAC).copy(alpha = 0.45f), // Soft Lime Mint
                    Color(0xFF67E8F9).copy(alpha = 0.55f), // Sky Cyan
                    Color(0xFFA5B4FC).copy(alpha = 0.30f)  // Soft Periwinkle
                ),
                start = Offset(w * 0.2f, 0f),
                end = Offset(w, h * 0.7f)
            )
        )

        // 4. Central Swirling Silk Wave
        val path2 = androidx.compose.ui.graphics.Path().apply {
            moveTo(0f, h * 0.45f)
            cubicTo(
                w * 0.30f, h * 0.15f,
                w * 0.60f, h * 0.75f,
                w, h * 0.35f
            )
            lineTo(w, h)
            lineTo(0f, h)
            close()
        }
        drawPath(
            path = path2,
            brush = Brush.verticalGradient(
                colors = listOf(
                    Color(0xFF2DD4BF).copy(alpha = 0.25f),
                    Color(0xFF0F172A).copy(alpha = 0.80f),
                    Color(0xFF10121D)
                ),
                startY = h * 0.25f,
                endY = h
            )
        )
    }
}


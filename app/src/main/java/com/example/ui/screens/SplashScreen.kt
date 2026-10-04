package com.example.ui.screens

import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.data.SupabaseAuthService
import com.example.data.UserSessionManager
import com.example.ui.theme.PacificoFontFamily
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlin.math.sin

@Composable
fun SplashScreen(
    onSplashFinished: (session: UserSessionManager.SessionData?) -> Unit
) {
    val context = LocalContext.current

    LaunchedEffect(Unit) {
        val startTime = System.currentTimeMillis()
        val session: UserSessionManager.SessionData? = try {
            withContext(Dispatchers.IO) {
                try {
                    UserSessionManager.init(context)
                    if (UserSessionManager.isTokenExpired(context)) {
                        SupabaseAuthService.refreshSessionSync(context, forceRefresh = true)
                    }
                    val s = UserSessionManager.getSession(context)
                    val currentUserId = s?.userId ?: ""
                    if (currentUserId.isNotBlank()) {
                        if (com.example.data.NetworkUtils.isOnline(context)) {
                            kotlinx.coroutines.withTimeoutOrNull(400L) {
                                val profileService = com.example.data.SupabaseProfileService()
                                val onlineProfile = profileService.fetchProfileById(currentUserId)
                                if (onlineProfile == null) {
                                    UserSessionManager.clearSession(context)
                                    return@withTimeoutOrNull null
                                }
                            }
                        }
                    }
                    s
                } catch (e: Throwable) {
                    android.util.Log.e("SplashScreen", "Async session check error: ${e.message}", e)
                    null
                }
            }
        } catch (e: Throwable) {
            android.util.Log.e("SplashScreen", "Coroutine error: ${e.message}", e)
            null
        }

        val elapsedTime = System.currentTimeMillis() - startTime
        val remainingDelay = 250L - elapsedTime
        if (remainingDelay > 0) {
            delay(remainingDelay)
        }

        try {
            onSplashFinished(session)
        } catch (e: Throwable) {
            android.util.Log.e("SplashScreen", "Error during splash finish: ${e.message}", e)
            try {
                onSplashFinished(null)
            } catch (_: Throwable) {}
        }
    }

    // Infinite animation transitions for radiant ambient breathing and subtle bokeh
    val infiniteTransition = rememberInfiniteTransition(label = "splash_ambient_animations")

    val pulseScale by infiniteTransition.animateFloat(
        initialValue = 1.0f,
        targetValue = 1.06f,
        animationSpec = infiniteRepeatable(
            animation = tween(2800, easing = FastOutSlowInEasing),
            repeatMode = RepeatMode.Reverse
        ),
        label = "pulse_scale"
    )

    val pulseAlpha by infiniteTransition.animateFloat(
        initialValue = 0.40f,
        targetValue = 0.65f,
        animationSpec = infiniteRepeatable(
            animation = tween(2800, easing = FastOutSlowInEasing),
            repeatMode = RepeatMode.Reverse
        ),
        label = "pulse_alpha"
    )

    val floatOffset by infiniteTransition.animateFloat(
        initialValue = 0f,
        targetValue = 360f,
        animationSpec = infiniteRepeatable(
            animation = tween(12000, easing = LinearEasing),
            repeatMode = RepeatMode.Restart
        ),
        label = "float_offset"
    )

    val welcomeBgGradient = Brush.verticalGradient(
        colors = listOf(
            Color(0xFFFFB74D), // Warm Golden Light
            Color(0xFFFF9800), // Amber
            Color(0xFFFF6500), // Sunset Orange
            Color(0xFFE65100), // Deep Amber
            Color(0xFFFF8D00), // Amber Gold
            Color(0xFFFFCC80)  // Soft Peach Glow
        )
    )

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(welcomeBgGradient)
            .testTag("splash_screen_root")
    ) {
        // 1. Top Radiant Aura Glow
        Box(
            modifier = Modifier
                .align(Alignment.TopCenter)
                .size(320.dp)
                .alpha(0.40f)
                .background(
                    Brush.radialGradient(
                        colors = listOf(
                            Color.White.copy(alpha = 0.50f),
                            Color(0xFFFFE0B2).copy(alpha = 0.30f),
                            Color.Transparent
                        )
                    )
                )
        )

        // 2. Central Radiant Sunburst Glow behind the Emblem
        Box(
            modifier = Modifier
                .align(Alignment.Center)
                .offset(y = (-52).dp)
                .size((380 * pulseScale).dp)
                .alpha(pulseAlpha)
                .background(
                    Brush.radialGradient(
                        colors = listOf(
                            Color.White.copy(alpha = 0.55f),
                            Color(0xFFFFE0B2).copy(alpha = 0.40f),
                            Color(0xFFFFCC80).copy(alpha = 0.22f),
                            Color.Transparent
                        )
                    )
                )
        )

        // 3. Subtle Floating Ambient Bokeh Orbs & Starlight Glints on Canvas
        Canvas(modifier = Modifier.fillMaxSize()) {
            val w = size.width
            val h = size.height
            val cx = w / 2f
            val cy = h * 0.38f
            val rad = Math.toRadians(floatOffset.toDouble())

            val particles = listOf(
                Triple(0.20f, 0.22f, 22.dp.toPx()),
                Triple(0.82f, 0.25f, 18.dp.toPx()),
                Triple(0.14f, 0.48f, 26.dp.toPx()),
                Triple(0.86f, 0.45f, 20.dp.toPx()),
                Triple(0.30f, 0.60f, 16.dp.toPx()),
                Triple(0.72f, 0.62f, 24.dp.toPx())
            )

            particles.forEachIndexed { i, (rx, ry, sizePx) ->
                val waveOffset = (sin(rad + i * 1.1) * 12.dp.toPx()).toFloat()
                val posX = rx * w
                val posY = ry * h + waveOffset

                drawCircle(
                    brush = Brush.radialGradient(
                        colors = listOf(
                            Color.White.copy(alpha = 0.28f),
                            Color(0xFFFFE082).copy(alpha = 0.16f),
                            Color.Transparent
                        ),
                        center = Offset(posX, posY),
                        radius = sizePx
                    ),
                    radius = sizePx,
                    center = Offset(posX, posY)
                )
            }

            val stars = listOf(
                Offset(cx - 96.dp.toPx(), cy - 72.dp.toPx()),
                Offset(cx + 94.dp.toPx(), cy - 68.dp.toPx()),
                Offset(cx - 86.dp.toPx(), cy + 64.dp.toPx()),
                Offset(cx + 92.dp.toPx(), cy + 62.dp.toPx())
            )

            stars.forEachIndexed { idx, pos ->
                val twinkle = (0.7f + 0.3f * sin(rad * 2 + idx * 1.5).toFloat())
                val s = 5.dp.toPx() * twinkle
                val starPath = Path().apply {
                    moveTo(pos.x, pos.y - s)
                    quadraticTo(pos.x, pos.y, pos.x + s, pos.y)
                    quadraticTo(pos.x, pos.y, pos.x, pos.y + s)
                    quadraticTo(pos.x, pos.y, pos.x - s, pos.y)
                    quadraticTo(pos.x, pos.y, pos.x - s, pos.y)
                    close()
                }
                drawPath(
                    path = starPath,
                    brush = Brush.radialGradient(
                        listOf(Color.White.copy(alpha = 0.9f), Color(0xFFFFD54F).copy(alpha = 0.6f), Color.Transparent),
                        center = pos,
                        radius = s * 1.6f
                    )
                )
            }
        }

        // 4. Center Hero: Signature Qivo Typography & Loading Indicator
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .align(Alignment.Center)
                .padding(horizontal = 24.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center
        ) {
            Box(
                contentAlignment = Alignment.Center,
                modifier = Modifier.padding(bottom = 16.dp)
            ) {
                Box(
                    modifier = Modifier
                        .size(width = 330.dp, height = 175.dp)
                        .background(
                            Brush.radialGradient(
                                colors = listOf(
                                    Color.White.copy(alpha = 0.35f),
                                    Color(0xFFFFE082).copy(alpha = 0.18f),
                                    Color.Transparent
                                )
                            )
                        )
                )

                Text(
                    text = "Qivo",
                    fontFamily = PacificoFontFamily,
                    fontSize = 106.sp,
                    color = Color.White,
                    textAlign = TextAlign.Center,
                    style = androidx.compose.ui.text.TextStyle(
                        shadow = androidx.compose.ui.graphics.Shadow(
                            color = Color(0x353E1F07),
                            offset = Offset(0f, 7f),
                            blurRadius = 18f
                        )
                    ),
                    modifier = Modifier
                        .rotate(-6f)
                        .testTag("splash_qivo_logo")
                )
            }
        }
    }
}

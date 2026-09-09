package com.example.ui.screens

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.data.SupabaseAuthService
import com.example.data.UserSessionManager
import com.example.ui.theme.AppFontFamily
import com.example.ui.theme.QivoOrange
import com.example.ui.theme.QivoYellow
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.withContext

@Composable
fun SplashScreen(
    onSplashFinished: (session: UserSessionManager.SessionData?) -> Unit
) {
    val context = LocalContext.current
    val alphaAnim = remember { Animatable(0f) }
    var hasFinished by remember { mutableStateOf(false) }

    LaunchedEffect(Unit) {
        if (hasFinished) return@LaunchedEffect

        // Fetch session on background thread safely
        val session: UserSessionManager.SessionData? = try {
            withContext(Dispatchers.IO) {
                try {
                    UserSessionManager.init(context)
                    if (UserSessionManager.isTokenExpired(context)) {
                        SupabaseAuthService.refreshSessionSync(context, forceRefresh = true)
                    }
                    UserSessionManager.getSession(context)
                } catch (e: Throwable) {
                    android.util.Log.e("SplashScreen", "Async session check error: ${e.message}", e)
                    null
                }
            }
        } catch (e: Throwable) {
            android.util.Log.e("SplashScreen", "Coroutine error: ${e.message}", e)
            null
        }

        // Smooth fade-in without any expansion or scale changes
        try {
            alphaAnim.animateTo(
                targetValue = 1f,
                animationSpec = tween(durationMillis = 300)
            )
        } catch (_: Throwable) {}
        
        delay(150)

        if (!hasFinished) {
            hasFinished = true
            try {
                onSplashFinished(session)
            } catch (e: Throwable) {
                android.util.Log.e("SplashScreen", "Error during splash finish: ${e.message}", e)
                try {
                    onSplashFinished(null)
                } catch (_: Throwable) {}
            }
        }
    }

    // Refined, Pale Warm Sunset Orange & Peach Canvas Gradient
    val splashGradient = Brush.verticalGradient(
        colors = listOf(
            Color(0xFFFF9E79), // Soft Warm Coral Peach
            Color(0xFFFFAE8D), // Pale Amber Orange
            Color(0xFFFFBEA2), // Soft Apricot
            Color(0xFFFFCFAF), // Luminous Pale Sunset
            Color(0xFFFFDFC8)  // Warm Creamy Amber
        )
    )

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(splashGradient)
            .testTag("splash_screen_root")
    ) {
        // Decorative radiant ambient glow (steady, no pulsing or expanding)
        Box(
            modifier = Modifier
                .align(Alignment.Center)
                .size(340.dp)
                .alpha(0.45f)
                .background(
                    Brush.radialGradient(
                        colors = listOf(
                            Color.White.copy(alpha = 0.45f),
                            Color(0xFFFFE0B2).copy(alpha = 0.35f),
                            Color(0xFFFFCC80).copy(alpha = 0.20f),
                            Color.Transparent
                        )
                    )
                )
        )

        // Top Aura Glow
        Box(
            modifier = Modifier
                .align(Alignment.TopCenter)
                .size(260.dp)
                .alpha(0.35f)
                .background(
                    Brush.radialGradient(
                        colors = listOf(
                            Color.White.copy(alpha = 0.45f),
                            Color.Transparent
                        )
                    )
                )
        )

        Column(
            modifier = Modifier
                .fillMaxSize()
                .statusBarsPadding()
                .navigationBarsPadding()
                .padding(horizontal = 24.dp)
                .alpha(alphaAnim.value),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center
        ) {
            // App Branding Icon with Soft Golden Gradient, Glow Border, and Drop Shadow (steady, fixed size)
            Box(
                modifier = Modifier
                    .size(118.dp)
                    .shadow(elevation = 8.dp, shape = CircleShape, spotColor = Color(0x33D84315))
                    .clip(CircleShape)
                    .background(
                        Brush.linearGradient(
                            colors = listOf(
                                Color(0xFFFFFFFF),
                                Color(0xFFFFF8E1),
                                Color(0xFFFFE082),
                                Color(0xFFFFB74D),
                                Color(0xFFFF9800)
                            )
                        )
                    )
                    .border(
                        width = 3.dp,
                        brush = Brush.linearGradient(
                            colors = listOf(
                                Color.White,
                                Color(0xFFFFD54F),
                                Color.White.copy(alpha = 0.6f)
                            )
                        ),
                        shape = CircleShape
                    ),
                contentAlignment = Alignment.Center
            ) {
                Text(
                    text = "QIVO",
                    fontFamily = AppFontFamily,
                    fontWeight = FontWeight.Black,
                    fontSize = 34.sp,
                    letterSpacing = 3.sp,
                    color = Color(0xFF4E2608) // Deep Rich Warm Tone
                )
            }
        }
    }
}

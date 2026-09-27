package com.example.ui.screens

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
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
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.draw.shadow
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
import com.example.ui.theme.AppFontFamily
import com.example.ui.theme.PacificoFontFamily
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
    val alphaAnim = remember { Animatable(1f) }
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
                    var s = UserSessionManager.getSession(context)
                    if (s != null && s.userId.isNotBlank()) {
                        if (com.example.data.NetworkUtils.isOnline(context)) {
                            val profileService = com.example.data.SupabaseProfileService()
                            val onlineProfile = profileService.fetchProfileById(s.userId)
                            if (onlineProfile == null) {
                                UserSessionManager.clearSession(context)
                                s = null
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

    // Luxury Sunset Amber Canvas Gradient matching Welcome Screen
    val splashGradient = Brush.verticalGradient(
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
            .background(splashGradient)
            .testTag("splash_screen_root")
    ) {
        // Center Hero: Signature Qivo Typography in Cursive Font Design
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
            Box(
                contentAlignment = Alignment.Center,
                modifier = Modifier
                    .size(130.dp)
                    .clip(RoundedCornerShape(32.dp))
                    .background(QivoOrange)
                    .border(2.dp, Color.White.copy(alpha = 0.3f), RoundedCornerShape(32.dp))
                    .shadow(8.dp, RoundedCornerShape(32.dp))
            ) {
                Text(
                    text = "Qivo",
                    fontFamily = PacificoFontFamily,
                    fontSize = 52.sp,
                    color = Color.White,
                    textAlign = TextAlign.Center,
                    style = androidx.compose.ui.text.TextStyle(
                        shadow = androidx.compose.ui.graphics.Shadow(
                            color = Color(0x40000000),
                            offset = Offset(0f, 4f),
                            blurRadius = 12f
                        )
                    ),
                    modifier = Modifier
                        .rotate(-4f)
                        .testTag("splash_qivo_logo")
                )
            }
        }
    }
}

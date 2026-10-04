package com.example.ui.screens
import com.example.data.AuthResult

import android.app.Activity
import androidx.activity.compose.BackHandler
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.BorderStroke
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.filled.Male
import androidx.compose.material.icons.filled.Female
import androidx.compose.material.icons.filled.Email
import androidx.compose.material.icons.filled.ArrowDropDown
import androidx.compose.material.icons.filled.ArrowDropUp
import androidx.compose.material.icons.filled.SupportAgent
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import kotlinx.coroutines.launch
import com.example.data.SupabaseAuthService
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.view.WindowCompat
import com.example.data.UserSessionManager
import com.example.ui.components.AppToast
import com.example.ui.components.LegalDocumentType
import com.example.ui.components.TermsAndPrivacyDialog
import com.example.ui.screens.CustomerSupportScreen
import com.example.ui.theme.AppFontFamily
import com.example.ui.theme.PacificoFontFamily
import com.example.ui.theme.QivoOrange
import com.example.ui.theme.QivoYellow
import kotlin.math.sin

@Composable
fun WelcomeScreen(
    onNavigateToEmail: () -> Unit,
    onGoogleSignInClick: () -> Unit,
    isGoogleLoading: Boolean = false,
    onNavigateToCustomerSupport: (() -> Unit)? = null
) {
    var activeLegalDoc by remember { mutableStateOf<LegalDocumentType?>(null) }
    val context = LocalContext.current

    val scope = rememberCoroutineScope()
    var showCustomerSupportScreen by remember { mutableStateOf(false) }
    var isAgreedToTerms by remember { mutableStateOf(true) }

    if (showCustomerSupportScreen) {
        BackHandler {
            showCustomerSupportScreen = false
        }
        CustomerSupportScreen(
            onBackClick = { showCustomerSupportScreen = false }
        )
        return
    }

    if (activeLegalDoc != null) {
        LegalWebViewScreen(
            initialType = activeLegalDoc!!,
            showTabs = false,
            onClose = { activeLegalDoc = null }
        )
        return
    }


    // Set Status Bar Icons to Dark for the pale warm sunset background
    val view = LocalView.current
    DisposableEffect(view) {
        var insetsController: androidx.core.view.WindowInsetsControllerCompat? = null
        var previousStatusBars = false
        try {
            var ctx = view.context
            var act: Activity? = null
            while (ctx is android.content.ContextWrapper) {
                if (ctx is Activity) {
                    act = ctx
                    break
                }
                ctx = ctx.baseContext
            }
            if (act != null) {
                val window = act.window
                insetsController = WindowCompat.getInsetsController(window, view)
                previousStatusBars = insetsController.isAppearanceLightStatusBars
                insetsController.isAppearanceLightStatusBars = true
            }
        } catch (_: Exception) {}

        onDispose {
            try {
                insetsController?.isAppearanceLightStatusBars = previousStatusBars
            } catch (_: Exception) {}
        }
    }

    // Infinite animation transitions for radiant ambient breathing and subtle bokeh
    val infiniteTransition = rememberInfiniteTransition(label = "welcome_ambient_animations")

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

    // Luxury Rare Social Sunset Amber & Warm Golden Canvas Gradient (No Black)
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
            .testTag("welcome_screen_root")
    ) {
        // 1. Top Radiant Aura Glow (like splash screen)
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

        // 2. Central Radiant Sunburst Glow behind the Emblem (with gentle breathing)
        Box(
            modifier = Modifier
                .align(Alignment.Center)
                .offset(y = (-45).dp)
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

            // Ambient gentle floating warm light particles
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

            // 4 Micro-Twinkle Stars framing the logo
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
                    quadraticTo(pos.x, pos.y, pos.x, pos.y - s)
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

        // 4. Top-Right Customer Support Floating Button (Uses Screen Background)
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .statusBarsPadding()
                .padding(horizontal = 16.dp, vertical = 8.dp)
        ) {
            IconButton(
                onClick = {
                    if (onNavigateToCustomerSupport != null) {
                        onNavigateToCustomerSupport()
                    } else {
                        showCustomerSupportScreen = true
                    }
                },
                modifier = Modifier
                    .align(Alignment.TopEnd)
                    .size(48.dp)
                    .testTag("welcome_customer_service_button")
            ) {
                Icon(
                    imageVector = Icons.Default.SupportAgent,
                    contentDescription = "Customer Support",
                    tint = Color.White,
                    modifier = Modifier.size(28.dp)
                )
            }
        }

        // 5. CENTER HERO: Signature Qivo Typography in Cursive Font Design (No Round Badge)
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .align(Alignment.Center)
                .offset(y = (-52).dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center
        ) {
            QivoWelcomeHeroLogo()
        }

        // 6. BOTTOM AUTHENTICATION & LEGAL CHECK (Clean & High-Contrast)
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .align(Alignment.BottomCenter)
                .navigationBarsPadding()
                .padding(horizontal = 28.dp, vertical = 22.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            // A. GOOGLE BUTTON (Sleek 52dp height, Official Google Vector G Logo)
            Surface(
                onClick = {
                    if (!isAgreedToTerms) isAgreedToTerms = true
                    if (!isGoogleLoading) onGoogleSignInClick()
                },
                modifier = Modifier
                    .fillMaxWidth()
                    .height(52.dp)
                    .shadow(
                        elevation = 5.dp,
                        shape = RoundedCornerShape(26.dp),
                        ambientColor = Color(0x22000000),
                        spotColor = Color(0x33FFA726)
                    )
                    .testTag("google_login_button"),
                shape = RoundedCornerShape(26.dp),
                color = Color.White
            ) {
                Box(
                    modifier = Modifier.fillMaxSize(),
                    contentAlignment = Alignment.Center
                ) {
                    if (isGoogleLoading) {
                        CircularProgressIndicator(
                            modifier = Modifier.size(22.dp),
                            color = QivoOrange,
                            strokeWidth = 2.dp
                        )
                    } else {
                        Row(
                            modifier = Modifier
                                .fillMaxSize()
                                .padding(horizontal = 20.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            GoogleOfficialLogo(modifier = Modifier.size(22.dp))

                            Text(
                                text = "Continue with Google",
                                fontSize = 15.sp,
                                fontWeight = FontWeight.Bold,
                                fontFamily = AppFontFamily,
                                color = Color(0xFF1E293B),
                                modifier = Modifier.weight(1f),
                                textAlign = TextAlign.Center
                            )

                            Spacer(modifier = Modifier.width(22.dp))
                        }
                    }
                }
            }

            Spacer(modifier = Modifier.height(12.dp))

            // B. EMAIL BUTTON (Now a Main elevated button matching Google button design)
            Surface(
                onClick = {
                    if (!isAgreedToTerms) isAgreedToTerms = true
                    onNavigateToEmail()
                },
                modifier = Modifier
                    .fillMaxWidth()
                    .height(52.dp)
                    .shadow(
                        elevation = 5.dp,
                        shape = RoundedCornerShape(26.dp),
                        ambientColor = Color(0x22000000),
                        spotColor = Color(0x33FFA726)
                    )
                    .testTag("email_login_main_button"),
                shape = RoundedCornerShape(26.dp),
                color = QivoOrange
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(horizontal = 20.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Icon(
                        imageVector = Icons.Default.Email,
                        contentDescription = "Email",
                        tint = Color.White,
                        modifier = Modifier.size(22.dp)
                    )
                    Text(
                        text = "Continue with Email",
                        fontSize = 15.sp,
                        fontWeight = FontWeight.Bold,
                        fontFamily = AppFontFamily,
                        color = Color.White,
                        modifier = Modifier.weight(1f),
                        textAlign = TextAlign.Center
                    )
                    Spacer(modifier = Modifier.width(22.dp))
                }
            }

            Spacer(modifier = Modifier.height(24.dp))


            
                        // C. TERMS & PRIVACY CHECKBOX AGREEMENT
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 4.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.Center
            ) {
                Box(
                    modifier = Modifier
                        .size(18.dp)
                        .clip(CircleShape)
                        .background(if (isAgreedToTerms) QivoOrange else Color.White)
                        .border(
                            width = 1.5.dp,
                            color = if (isAgreedToTerms) QivoOrange else Color(0xFFBCAAA4),
                            shape = CircleShape
                        )
                        .clickable { isAgreedToTerms = !isAgreedToTerms },
                    contentAlignment = Alignment.Center
                ) {
                    if (isAgreedToTerms) {
                        Icon(
                            imageVector = Icons.Default.Check,
                            contentDescription = "Agreed",
                            tint = Color.White,
                            modifier = Modifier.size(12.dp)
                        )
                    }
                }

                Spacer(modifier = Modifier.width(8.dp))

                Column {
                    Text(
                        text = "By signing up, you are agreeing to our",
                        fontSize = 11.5.sp,
                        color = Color(0xFF4A3428),
                        fontWeight = FontWeight.Normal
                    )
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text(
                            text = "Terms of Service",
                            fontSize = 11.5.sp,
                            fontWeight = FontWeight.Bold,
                            color = QivoOrange,
                            modifier = Modifier.clickable {
                                activeLegalDoc = LegalDocumentType.TERMS_OF_SERVICE
                            }
                        )
                        Text(
                            text = " & ",
                            fontSize = 11.5.sp,
                            color = Color(0xFF4A3428)
                        )
                        Text(
                            text = "Privacy Policy",
                            fontSize = 11.5.sp,
                            fontWeight = FontWeight.Bold,
                            color = QivoOrange,
                            modifier = Modifier.clickable {
                                activeLegalDoc = LegalDocumentType.PRIVACY_POLICY
                            }
                        )
                    }
                }
            }
        }
    }
}

/**
 * Signature Qivo brand wordmark in the flowing cursive font design (Pacifico).
 * Cleanly rendered without any circular badge, featuring radiant ambient warmth,
 * pure crisp white curves, subtle artistic tilt, and gentle drop shadow.
 */
@Composable
private fun QivoWelcomeHeroLogo(modifier: Modifier = Modifier) {
    Box(
        contentAlignment = Alignment.Center,
        modifier = modifier.padding(horizontal = 24.dp)
    ) {
        // Soft diffuse warm radiance behind the typography
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
                .testTag("welcome_qivo_logo")
        )
    }
}

/**
 * Authentic Precision Google 'G' 4-Color Brand Vector
 */
@Composable
private fun GoogleOfficialLogo(modifier: Modifier = Modifier) {
    Icon(
        painter = androidx.compose.ui.res.painterResource(id = com.example.R.drawable.ic_google_logo),
        contentDescription = "Google",
        tint = Color.Unspecified,
        modifier = modifier
    )
}



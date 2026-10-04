package com.example.ui.screens

import android.content.Context
import androidx.compose.animation.AnimatedVisibility
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
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.MarkEmailRead
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.data.AuthResult
import com.example.data.AvatarFrameManager
import com.example.data.SupabaseAuthService
import com.example.data.UserSessionManager
import com.example.ui.components.AppToast
import com.example.ui.theme.QivoOrange
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

/**
 * Dedicated Screen for entering and verifying the 6-digit OTP code sent to the user's email.
 */
@Composable
fun OtpVerificationScreen(
    email: String,
    onNavigateBack: () -> Unit,
    onOtpVerified: (email: String, userId: String, accessToken: String?) -> Unit
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val authService = remember { SupabaseAuthService() }
    val focusManager = LocalFocusManager.current
    val focusRequester = remember { FocusRequester() }

    var otpCode by remember { mutableStateOf("") }
    var isLoading by remember { mutableStateOf(false) }
    var errorMessage by remember { mutableStateOf<String?>(null) }
    var infoMessage by remember { mutableStateOf<String?>(null) }
    var resendCountdown by remember { mutableIntStateOf(60) }
    var canResend by remember { mutableStateOf(false) }

    // Resend countdown timer
    LaunchedEffect(resendCountdown) {
        if (resendCountdown > 0) {
            delay(1000L)
            resendCountdown--
            if (resendCountdown == 0) {
                canResend = true
            }
        }
    }

    // Auto-focus OTP input on screen load
    LaunchedEffect(Unit) {
        try {
            focusRequester.requestFocus()
        } catch (_: Exception) {}
    }

    fun verifyCode() {
        val trimmedCode = otpCode.trim()
        if (trimmedCode.length != 6) {
            errorMessage = "Please enter the complete 6-digit code."
            return
        }
        isLoading = true
        errorMessage = null
        infoMessage = null
        focusManager.clearFocus()

        scope.launch {
            when (val result = authService.verifyOtp(email, trimmedCode)) {
                is AuthResult.Success -> {
                    if (!result.accessToken.isNullOrBlank()) {
                        UserSessionManager.saveTokens(
                            context = context,
                            accessToken = result.accessToken,
                            refreshToken = result.refreshToken ?: "",
                            expiresInSeconds = result.expiresIn
                        )
                    }
                    AvatarFrameManager.grantNewUserWelcomeFrame(context, result.userId)
                    isLoading = false
                    AppToast.show("Email verified successfully! 🎉")
                    onOtpVerified(result.email, result.userId, result.accessToken)
                }
                is AuthResult.Error -> {
                    isLoading = false
                    val raw = result.message
                    errorMessage = if (raw.contains("invalid", ignoreCase = true) || raw.contains("token", ignoreCase = true)) {
                        "Invalid code. Please check your email and try again."
                    } else if (raw.contains("expired", ignoreCase = true)) {
                        "Verification code has expired. Tap 'Resend Code' below."
                    } else {
                        raw
                    }
                }
            }
        }
    }

    fun resendCode() {
        if (!canResend || isLoading) return
        isLoading = true
        errorMessage = null
        infoMessage = null
        scope.launch {
            when (val result = authService.signInWithOtp(email)) {
                is AuthResult.Success -> {
                    isLoading = false
                    canResend = false
                    resendCountdown = 60
                    infoMessage = "A fresh 6-digit code has been sent to your email!"
                    AppToast.show("Code sent! Check your inbox.")
                }
                is AuthResult.Error -> {
                    isLoading = false
                    errorMessage = result.message
                }
            }
        }
    }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(Color.White)
            .statusBarsPadding()
            .navigationBarsPadding()
            .testTag("otp_verification_screen_root")
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 24.dp, vertical = 20.dp),
            verticalArrangement = Arrangement.SpaceBetween
        ) {
            Column(modifier = Modifier.fillMaxWidth()) {
                // Top Navigation Bar
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(48.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Surface(
                        onClick = onNavigateBack,
                        shape = CircleShape,
                        color = Color(0xFFF1F5F9),
                        modifier = Modifier.size(40.dp)
                    ) {
                        Box(contentAlignment = Alignment.Center) {
                            Icon(
                                imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                                contentDescription = "Back",
                                tint = Color(0xFF0F172A),
                                modifier = Modifier.size(20.dp)
                            )
                        }
                    }
                    Spacer(modifier = Modifier.width(16.dp))
                    Text(
                        text = "Verify Email",
                        fontSize = 18.sp,
                        fontWeight = FontWeight.Bold,
                        color = Color(0xFF0F172A)
                    )
                }

                Spacer(modifier = Modifier.height(32.dp))

                // Icon & Header
                Box(
                    modifier = Modifier
                        .size(64.dp)
                        .background(Color(0xFFFFF7ED), shape = CircleShape)
                        .border(1.5.dp, Color(0xFFFFEDD5), shape = CircleShape),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        imageVector = Icons.Default.MarkEmailRead,
                        contentDescription = "Email Verified",
                        tint = QivoOrange,
                        modifier = Modifier.size(32.dp)
                    )
                }

                Spacer(modifier = Modifier.height(20.dp))

                Text(
                    text = "Enter 6-Digit Code",
                    fontSize = 26.sp,
                    fontWeight = FontWeight.ExtraBold,
                    color = Color(0xFF0F172A)
                )

                Spacer(modifier = Modifier.height(8.dp))

                Text(
                    text = "We've sent a 6-digit verification code to:",
                    fontSize = 14.sp,
                    color = Color(0xFF64748B)
                )

                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier.padding(top = 4.dp)
                ) {
                    Text(
                        text = email,
                        fontSize = 15.sp,
                        fontWeight = FontWeight.Bold,
                        color = Color(0xFF0F172A)
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(
                        text = "Edit",
                        fontSize = 13.sp,
                        fontWeight = FontWeight.SemiBold,
                        color = QivoOrange,
                        modifier = Modifier
                            .clickable { onNavigateBack() }
                            .padding(4.dp)
                    )
                }

                Spacer(modifier = Modifier.height(36.dp))

                // Hidden BasicTextField capturing keyboard input
                BasicTextField(
                    value = otpCode,
                    onValueChange = { input ->
                        val filtered = input.filter { it.isDigit() }.take(6)
                        otpCode = filtered
                        errorMessage = null
                        if (filtered.length == 6) {
                            focusManager.clearFocus()
                        }
                    },
                    keyboardOptions = KeyboardOptions(
                        keyboardType = KeyboardType.NumberPassword,
                        imeAction = ImeAction.Done
                    ),
                    keyboardActions = KeyboardActions(
                        onDone = {
                            if (otpCode.length == 6) {
                                verifyCode()
                            }
                        }
                    ),
                    modifier = Modifier
                        .focusRequester(focusRequester)
                        .fillMaxWidth()
                        .height(0.dp) // invisible input, visuals below
                )

                // 6 Styled OTP Digit Boxes
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clickable { focusRequester.requestFocus() },
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    for (i in 0 until 6) {
                        val digit = otpCode.getOrNull(i)?.toString() ?: ""
                        val isCurrentFocus = otpCode.length == i

                        Box(
                            modifier = Modifier
                                .weight(1f)
                                .height(56.dp)
                                .background(
                                    color = if (isCurrentFocus) Color(0xFFFFF7ED) else Color(0xFFF8FAFC),
                                    shape = RoundedCornerShape(12.dp)
                                )
                                .border(
                                    width = if (isCurrentFocus) 2.dp else 1.dp,
                                    color = if (isCurrentFocus) QivoOrange else if (digit.isNotEmpty()) Color(0xFFCBD5E1) else Color(0xFFE2E8F0),
                                    shape = RoundedCornerShape(12.dp)
                                ),
                            contentAlignment = Alignment.Center
                        ) {
                            Text(
                                text = digit,
                                fontSize = 22.sp,
                                fontWeight = FontWeight.Bold,
                                color = Color(0xFF0F172A),
                                textAlign = TextAlign.Center
                            )
                        }
                    }
                }

                // Error Message Box
                AnimatedVisibility(visible = errorMessage != null) {
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(top = 16.dp)
                            .background(Color(0xFFFEF2F2), shape = RoundedCornerShape(10.dp))
                            .border(1.dp, Color(0xFFFCA5A5), shape = RoundedCornerShape(10.dp))
                            .padding(horizontal = 14.dp, vertical = 10.dp)
                    ) {
                        Text(
                            text = errorMessage ?: "",
                            color = Color(0xFFDC2626),
                            fontSize = 13.sp,
                            fontWeight = FontWeight.Medium
                        )
                    }
                }

                // Info Message Box
                AnimatedVisibility(visible = infoMessage != null) {
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(top = 16.dp)
                            .background(Color(0xFFF0FDF4), shape = RoundedCornerShape(10.dp))
                            .border(1.dp, Color(0xFF86EFAC), shape = RoundedCornerShape(10.dp))
                            .padding(horizontal = 14.dp, vertical = 10.dp)
                    ) {
                        Text(
                            text = infoMessage ?: "",
                            color = Color(0xFF16A34A),
                            fontSize = 13.sp,
                            fontWeight = FontWeight.Medium
                        )
                    }
                }

                Spacer(modifier = Modifier.height(28.dp))

                // Spam Folder Helper Tip
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(vertical = 12.dp)
                        .background(Color(0xFFF8FAFC), shape = RoundedCornerShape(10.dp))
                        .border(1.dp, Color(0xFFE2E8F0), shape = RoundedCornerShape(10.dp))
                        .padding(12.dp)
                ) {
                    Text(
                        text = "💡 Tip: Check your Spam, Junk, or Promotions folder if the 6-digit code doesn't appear in your Primary Inbox within 1-2 minutes.",
                        fontSize = 12.5.sp,
                        color = Color(0xFF475569),
                        lineHeight = 18.sp
                    )
                }

                // Resend Timer / Action
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.Center,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    if (canResend) {
                        TextButton(
                            onClick = { resendCode() },
                            enabled = !isLoading
                        ) {
                            Icon(
                                imageVector = Icons.Default.Refresh,
                                contentDescription = "Resend",
                                tint = QivoOrange,
                                modifier = Modifier.size(16.dp)
                            )
                            Spacer(modifier = Modifier.width(6.dp))
                            Text(
                                text = "Resend Verification Code",
                                color = QivoOrange,
                                fontWeight = FontWeight.Bold,
                                fontSize = 14.sp
                            )
                        }
                    } else {
                        Text(
                            text = "Resend code in ",
                            color = Color(0xFF64748B),
                            fontSize = 14.sp
                        )
                        Text(
                            text = "${resendCountdown}s",
                            color = QivoOrange,
                            fontWeight = FontWeight.Bold,
                            fontSize = 14.sp
                        )
                    }
                }
            }

            // Bottom Continue Button
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(top = 24.dp)
            ) {
                Button(
                    onClick = { verifyCode() },
                    enabled = !isLoading && otpCode.length == 6,
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(54.dp)
                        .shadow(elevation = 2.dp, shape = CircleShape)
                        .testTag("verify_otp_action_button"),
                    shape = CircleShape,
                    colors = ButtonDefaults.buttonColors(
                        containerColor = QivoOrange,
                        contentColor = Color.White
                    )
                ) {
                    if (isLoading) {
                        CircularProgressIndicator(
                            modifier = Modifier.size(22.dp),
                            color = Color.White,
                            strokeWidth = 2.dp
                        )
                    } else {
                        Text(
                            text = "Verify Code & Continue",
                            fontSize = 16.sp,
                            fontWeight = FontWeight.Bold
                        )
                    }
                }

                Spacer(modifier = Modifier.height(16.dp))
            }
        }
    }
}

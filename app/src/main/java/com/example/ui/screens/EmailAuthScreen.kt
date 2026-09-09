package com.example.ui.screens

import android.widget.Toast
import androidx.activity.compose.BackHandler
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
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Email
import androidx.compose.material.icons.filled.ErrorOutline
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.Visibility
import androidx.compose.material.icons.filled.VisibilityOff
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.focus.FocusDirection
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.data.AuthResult
import com.example.data.AvatarFrameManager
import com.example.data.SupabaseAuthService
import com.example.data.SupabaseProfileService
import com.example.data.UserSessionManager
import com.example.ui.components.AppToast
import com.example.ui.components.LegalDocumentType
import com.example.ui.theme.QivoDarkCharcoal
import com.example.ui.theme.QivoOrange
import com.example.ui.theme.QivoTextMuted
import kotlinx.coroutines.launch

@Composable
fun EmailAuthScreen(
    onNavigateBack: () -> Unit,
    onAuthSuccess: (email: String, userId: String) -> Unit,
    onNavigateToCreateAccount: (email: String, userId: String, initialName: String) -> Unit
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val authService = remember { SupabaseAuthService() }
    val focusManager = LocalFocusManager.current
    val scrollState = rememberScrollState()

    var emailInput by remember { mutableStateOf("") }
    var passwordInput by remember { mutableStateOf("") }
    var passwordVisible by remember { mutableStateOf(false) }

    var isLoading by remember { mutableStateOf(false) }
    var errorMessage by remember { mutableStateOf<String?>(null) }
    var infoMessage by remember { mutableStateOf<String?>(null) }

    // Full-screen legal WebView state
    var selectedLegalDoc by remember { mutableStateOf<LegalDocumentType?>(null) }

    if (selectedLegalDoc != null) {
        LegalWebViewScreen(
            initialType = selectedLegalDoc!!,
            onClose = { selectedLegalDoc = null }
        )
        return
    }

    BackHandler {
        onNavigateBack()
    }

    fun performSignIn() {
        if (emailInput.isBlank() || passwordInput.isBlank()) {
            errorMessage = "Please enter both your email address and password."
            return
        }
        isLoading = true
        errorMessage = null
        infoMessage = null

        scope.launch {
            when (val result = authService.signIn(emailInput, passwordInput, context)) {
                is AuthResult.Success -> {
                    // Automatically grant welcome frame if first time
                    AvatarFrameManager.grantNewUserWelcomeFrame(context, result.userId)

                    val profile = SupabaseProfileService().fetchProfile(result.userId, result.email, result.accessToken)
                    val existingSession = UserSessionManager.getSession(context)
                    val emailPrefix = if (result.email.contains("@")) result.email.substringBefore("@") else ""
                    val resolvedName = when {
                        !profile?.name.isNullOrBlank() && profile?.name != "QIVO User" -> profile!!.name
                        !profile?.name.isNullOrBlank() -> profile!!.name
                        !existingSession?.name.isNullOrBlank() && existingSession?.name != "QIVO User" -> existingSession.name
                        emailPrefix.isNotBlank() -> emailPrefix
                        else -> "User"
                    }
                    val userCoins = profile?.coins ?: existingSession?.coins ?: 0L
                    val resolvedGender = when {
                        !profile?.gender.isNullOrBlank() && (profile!!.gender.startsWith("f", ignoreCase = true) || profile.gender.startsWith("w", ignoreCase = true)) -> "Female"
                        !profile?.gender.isNullOrBlank() && profile.gender.startsWith("m", ignoreCase = true) -> "Male"
                        !profile?.gender.isNullOrBlank() -> profile.gender
                        !existingSession?.gender.isNullOrBlank() && (existingSession.gender.startsWith("f", ignoreCase = true) || existingSession.gender.startsWith("w", ignoreCase = true)) -> "Female"
                        !existingSession?.gender.isNullOrBlank() && existingSession.gender.startsWith("m", ignoreCase = true) -> "Male"
                        !existingSession?.gender.isNullOrBlank() -> existingSession.gender
                        else -> "Male"
                    }
                    UserSessionManager.saveSession(
                        context = context,
                        email = result.email,
                        userId = result.userId,
                        name = resolvedName,
                        gender = resolvedGender,
                        country = profile?.country ?: existingSession?.country ?: "United States",
                        avatarUrl = profile?.avatarUrl ?: existingSession?.avatarUrl ?: "",
                        numericId = if ((profile?.numericId ?: 0L) > 0L) profile!!.numericId else existingSession?.numericId ?: 0L,
                        coins = userCoins,
                        accessToken = result.accessToken,
                        refreshToken = result.refreshToken,
                        expiresInSeconds = result.expiresIn,
                        isAdmin = profile?.isAdmin ?: existingSession?.isAdmin,
                        isCoinSeller = profile?.isCoinSeller ?: existingSession?.isCoinSeller,
                        isAgent = profile?.isAgent ?: existingSession?.isAgent,
                        isVerified = existingSession?.isVerified
                    )
                    UserSessionManager.saveCoins(context, userCoins)
                    if (profile != null && profile.lastCheckinDate.isNotEmpty()) {
                        UserSessionManager.saveDailyCheckIn(
                            context = context,
                            userId = result.userId,
                            dateStr = profile.lastCheckinDate,
                            dayNumber = profile.lastCheckinDay,
                            email = result.email
                        )
                    }
                    isLoading = false
                    AppToast.show("Welcome back! 🎉")
                    onAuthSuccess(result.email, result.userId)
                }
                is AuthResult.Error -> {
                    isLoading = false
                    val raw = result.message
                    if (raw.contains("host", ignoreCase = true) ||
                        raw.contains("resolve", ignoreCase = true) ||
                        raw.contains("connection", ignoreCase = true) ||
                        raw.contains("network", ignoreCase = true) ||
                        raw.contains("timeout", ignoreCase = true) ||
                        raw.contains("failed", ignoreCase = true)
                    ) {
                        errorMessage = "Network error, please check your internet connection."
                    } else {
                        errorMessage = raw
                    }
                }
            }
        }
    }

    fun performSignUp() {
        if (emailInput.isBlank() || passwordInput.isBlank()) {
            errorMessage = "Please enter both your email address and password."
            return
        }
        if (passwordInput.length < 6) {
            errorMessage = "Password must be at least 6 characters long."
            return
        }
        isLoading = true
        errorMessage = null
        infoMessage = null

        scope.launch {
            when (val result = authService.signUp(emailInput, passwordInput, context)) {
                is AuthResult.Success -> {
                    if (!result.accessToken.isNullOrBlank()) {
                        UserSessionManager.saveTokens(
                            context = context,
                            accessToken = result.accessToken,
                            refreshToken = result.refreshToken ?: "",
                            expiresInSeconds = result.expiresIn
                        )
                    }

                    // Automatically grant and equip official 7-Day New User Welcome Frame with "NEW" tag!
                    AvatarFrameManager.grantNewUserWelcomeFrame(context, result.userId)

                    isLoading = false
                    AppToast.show("Account created! Let's set up your profile.")
                    val initialName = if (result.email.contains("@")) result.email.substringBefore("@") else ""
                    onNavigateToCreateAccount(result.email, result.userId, initialName)
                }
                is AuthResult.Error -> {
                    isLoading = false
                    val raw = result.message
                    if (raw.contains("already registered", ignoreCase = true)) {
                        errorMessage = "This email is already registered. Please tap Log In or use another email."
                    } else if (raw.contains("host", ignoreCase = true) ||
                        raw.contains("resolve", ignoreCase = true) ||
                        raw.contains("connection", ignoreCase = true) ||
                        raw.contains("network", ignoreCase = true) ||
                        raw.contains("timeout", ignoreCase = true) ||
                        raw.contains("failed", ignoreCase = true)
                    ) {
                        errorMessage = "Network error, please check your internet connection."
                    } else {
                        errorMessage = raw
                    }
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
            .testTag("email_auth_screen_root")
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .verticalScroll(scrollState)
                .padding(horizontal = 24.dp)
        ) {
            Spacer(modifier = Modifier.height(12.dp))

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

                Spacer(modifier = Modifier.width(12.dp))

                Text(
                    text = "Sign In / Register",
                    fontSize = 17.sp,
                    fontWeight = FontWeight.SemiBold,
                    color = Color(0xFF0F172A)
                )
            }

            Spacer(modifier = Modifier.height(28.dp))

            // Brand Accent Badge
            Box(
                modifier = Modifier
                    .clip(RoundedCornerShape(8.dp))
                    .background(Color(0xFFFFF7ED))
                    .border(1.dp, Color(0xFFFFEDD5), RoundedCornerShape(8.dp))
                    .padding(horizontal = 10.dp, vertical = 4.dp)
            ) {
                Text(
                    text = "QIVO ACCOUNT",
                    fontSize = 11.sp,
                    fontWeight = FontWeight.Bold,
                    color = QivoOrange,
                    letterSpacing = 0.5.sp
                )
            }

            Spacer(modifier = Modifier.height(10.dp))

            // Beautiful Structured Headline
            Text(
                text = "Welcome to Qivo",
                fontSize = 28.sp,
                fontWeight = FontWeight.ExtraBold,
                color = Color(0xFF0F172A),
                letterSpacing = (-0.5).sp
            )

            Spacer(modifier = Modifier.height(6.dp))

            Text(
                text = "Enter your email address and password to log in or create a brand new account.",
                fontSize = 14.sp,
                color = Color(0xFF64748B),
                lineHeight = 20.sp
            )

            Spacer(modifier = Modifier.height(28.dp))

            // Email Address Input Block
            Text(
                text = "Email Address",
                fontSize = 13.sp,
                fontWeight = FontWeight.SemiBold,
                color = Color(0xFF334155),
                modifier = Modifier.padding(bottom = 6.dp)
            )

            OutlinedTextField(
                value = emailInput,
                onValueChange = {
                    emailInput = it
                    errorMessage = null
                },
                placeholder = {
                    Text(
                        text = "e.g. name@domain.com",
                        color = Color(0xFF94A3B8),
                        fontSize = 15.sp
                    )
                },
                leadingIcon = {
                    Icon(
                        imageVector = Icons.Default.Email,
                        contentDescription = "Email Icon",
                        tint = if (emailInput.isNotBlank()) QivoOrange else Color(0xFF94A3B8),
                        modifier = Modifier.size(20.dp)
                    )
                },
                singleLine = true,
                keyboardOptions = KeyboardOptions(
                    keyboardType = KeyboardType.Email,
                    imeAction = ImeAction.Next
                ),
                keyboardActions = KeyboardActions(
                    onNext = { focusManager.moveFocus(FocusDirection.Down) }
                ),
                shape = RoundedCornerShape(14.dp),
                colors = OutlinedTextFieldDefaults.colors(
                    focusedContainerColor = Color(0xFFF8FAFC),
                    unfocusedContainerColor = Color(0xFFF8FAFC),
                    focusedBorderColor = QivoOrange,
                    unfocusedBorderColor = Color(0xFFE2E8F0),
                    focusedTextColor = Color(0xFF0F172A),
                    unfocusedTextColor = Color(0xFF0F172A)
                ),
                modifier = Modifier
                    .fillMaxWidth()
                    .height(56.dp)
                    .testTag("email_input_field")
            )

            Spacer(modifier = Modifier.height(18.dp))

            // Password Input Block
            Text(
                text = "Password",
                fontSize = 13.sp,
                fontWeight = FontWeight.SemiBold,
                color = Color(0xFF334155),
                modifier = Modifier.padding(bottom = 6.dp)
            )

            OutlinedTextField(
                value = passwordInput,
                onValueChange = {
                    passwordInput = it
                    errorMessage = null
                },
                placeholder = {
                    Text(
                        text = "At least 6 characters",
                        color = Color(0xFF94A3B8),
                        fontSize = 15.sp
                    )
                },
                leadingIcon = {
                    Icon(
                        imageVector = Icons.Default.Lock,
                        contentDescription = "Password Icon",
                        tint = if (passwordInput.isNotBlank()) QivoOrange else Color(0xFF94A3B8),
                        modifier = Modifier.size(20.dp)
                    )
                },
                singleLine = true,
                keyboardOptions = KeyboardOptions(
                    keyboardType = KeyboardType.Password,
                    imeAction = ImeAction.Done
                ),
                keyboardActions = KeyboardActions(
                    onDone = {
                        focusManager.clearFocus()
                        performSignIn()
                    }
                ),
                visualTransformation = if (passwordVisible) VisualTransformation.None else PasswordVisualTransformation(),
                trailingIcon = {
                    IconButton(onClick = { passwordVisible = !passwordVisible }) {
                        Icon(
                            imageVector = if (passwordVisible) Icons.Default.Visibility else Icons.Default.VisibilityOff,
                            contentDescription = "Toggle password visibility",
                            tint = Color(0xFF64748B),
                            modifier = Modifier.size(20.dp)
                        )
                    }
                },
                shape = RoundedCornerShape(14.dp),
                colors = OutlinedTextFieldDefaults.colors(
                    focusedContainerColor = Color(0xFFF8FAFC),
                    unfocusedContainerColor = Color(0xFFF8FAFC),
                    focusedBorderColor = QivoOrange,
                    unfocusedBorderColor = Color(0xFFE2E8F0),
                    focusedTextColor = Color(0xFF0F172A),
                    unfocusedTextColor = Color(0xFF0F172A)
                ),
                modifier = Modifier
                    .fillMaxWidth()
                    .height(56.dp)
                    .testTag("password_input_field")
            )

            // Feedback / Error Banner
            if (errorMessage != null) {
                Spacer(modifier = Modifier.height(14.dp))
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(12.dp))
                        .background(Color(0xFFFEF2F2))
                        .border(1.dp, Color(0xFFFCA5A5), RoundedCornerShape(12.dp))
                        .padding(12.dp)
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(
                            imageVector = Icons.Default.ErrorOutline,
                            contentDescription = null,
                            tint = Color(0xFFDC2626),
                            modifier = Modifier.size(18.dp)
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                        Text(
                            text = errorMessage ?: "",
                            color = Color(0xFFB91C1C),
                            fontSize = 13.sp,
                            fontWeight = FontWeight.Medium
                        )
                    }
                }
            }

            if (infoMessage != null) {
                Spacer(modifier = Modifier.height(14.dp))
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(12.dp))
                        .background(Color(0xFFF0FDF4))
                        .border(1.dp, Color(0xFF86EFAC), RoundedCornerShape(12.dp))
                        .padding(12.dp)
                ) {
                    Text(
                        text = infoMessage ?: "",
                        color = Color(0xFF15803D),
                        fontSize = 13.sp,
                        fontWeight = FontWeight.Medium
                    )
                }
            }

            Spacer(modifier = Modifier.height(30.dp))

            // Log In Button (Primary Orange Pill)
            Button(
                onClick = { performSignIn() },
                enabled = !isLoading,
                modifier = Modifier
                    .fillMaxWidth()
                    .height(52.dp)
                    .shadow(elevation = 2.dp, shape = CircleShape)
                    .testTag("login_action_button"),
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
                        text = "Log In",
                        fontSize = 16.sp,
                        fontWeight = FontWeight.Bold
                    )
                }
            }

            Spacer(modifier = Modifier.height(14.dp))

            // Sign Up Button (Secondary Dark Charcoal Pill)
            Button(
                onClick = { performSignUp() },
                enabled = !isLoading,
                modifier = Modifier
                    .fillMaxWidth()
                    .height(52.dp)
                    .testTag("signup_action_button"),
                shape = CircleShape,
                colors = ButtonDefaults.buttonColors(
                    containerColor = Color(0xFF0F172A),
                    contentColor = Color.White
                )
            ) {
                Text(
                    text = "Sign Up / Create Account",
                    fontSize = 16.sp,
                    fontWeight = FontWeight.Bold
                )
            }

            Spacer(modifier = Modifier.height(32.dp))

            // Clean, Interactive Terms of Service & Privacy Policy Footer
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(bottom = 24.dp),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                Text(
                    text = "By continuing, you confirm you are 18+ and agree to our",
                    fontSize = 12.sp,
                    color = Color(0xFF64748B),
                    textAlign = TextAlign.Center
                )
                Spacer(modifier = Modifier.height(4.dp))
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.Center
                ) {
                    Text(
                        text = "Terms of Service",
                        fontSize = 12.5.sp,
                        color = QivoOrange,
                        fontWeight = FontWeight.Bold,
                        textDecoration = TextDecoration.Underline,
                        modifier = Modifier
                            .clickable {
                                selectedLegalDoc = LegalDocumentType.TERMS_OF_SERVICE
                            }
                            .padding(4.dp)
                    )
                    Text(
                        text = " and ",
                        fontSize = 12.sp,
                        color = Color(0xFF64748B)
                    )
                    Text(
                        text = "Privacy Policy",
                        fontSize = 12.5.sp,
                        color = QivoOrange,
                        fontWeight = FontWeight.Bold,
                        textDecoration = TextDecoration.Underline,
                        modifier = Modifier
                            .clickable {
                                selectedLegalDoc = LegalDocumentType.PRIVACY_POLICY
                            }
                            .padding(4.dp)
                    )
                }
            }
        }
    }
}

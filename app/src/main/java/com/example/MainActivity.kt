package com.example

import android.content.Intent
import android.graphics.Color as AndroidColor
import android.os.Bundle
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.SystemBarStyle
import androidx.activity.compose.BackHandler
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.credentials.CredentialManager
import androidx.credentials.GetCredentialRequest
import androidx.credentials.CustomCredential
import androidx.credentials.exceptions.GetCredentialCancellationException
import com.google.android.libraries.identity.googleid.GetGoogleIdOption
import com.google.android.libraries.identity.googleid.GoogleIdTokenCredential
import com.example.data.AppAnalyticsService
import com.example.data.SupabaseConfig
import com.example.data.SupabaseFcmService
import com.example.data.UserSessionManager
import com.example.ui.components.AppToast
import com.example.ui.components.InAppToastHost
import com.example.ui.screens.CreateAccountScreen
import com.example.ui.screens.CustomerSupportScreen
import com.example.ui.screens.EmailAuthScreen
import com.example.ui.screens.PasswordLoginScreen
import com.example.ui.screens.OtpVerificationScreen
import com.example.ui.screens.SetPasswordScreen
import com.example.ui.screens.MainBottomNavScaffold
import com.example.ui.screens.SplashScreen
import com.example.ui.screens.UploadAvatarScreen
import com.example.ui.screens.WelcomeScreen
import com.example.ui.theme.MyApplicationTheme
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch

object AppStartupState {
    var isFirstLaunchInProcess = true
}

sealed class Screen {
    object Splash : Screen()
    object Welcome : Screen()
    object CustomerSupport : Screen()
    object EmailAuth : Screen()
    data class OtpVerification(val email: String) : Screen()
    data class PasswordLogin(val email: String) : Screen()
    data class SetPassword(val email: String, val userId: String, val accessToken: String? = null) : Screen()
    data class CreateAccount(
        val email: String,
        val userId: String,
        val initialName: String = "",
        val initialAvatarUrl: String = "",
        val initialCountry: String = ""
    ) : Screen()
    data class UploadAvatar(
        val email: String,
        val userId: String,
        val name: String,
        val gender: String,
        val birthDate: String,
        val country: String,
        val initialAvatarUrl: String = "",
        val numericId: Long = 0L
    ) : Screen()
    data class MainApp(
        val email: String,
        val userId: String,
        val name: String = "User",
        val gender: String = "",
        val country: String = "United States",
        val avatarUrl: String = "",
        val numericId: Long = 0L
    ) : Screen()
}

class MainActivity : ComponentActivity() {
    private var activeIntent by mutableStateOf<Intent?>(null)

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        activeIntent = intent

        enableEdgeToEdge(
            statusBarStyle = SystemBarStyle.auto(
                AndroidColor.TRANSPARENT,
                AndroidColor.TRANSPARENT
            ),
            navigationBarStyle = SystemBarStyle.auto(
                AndroidColor.TRANSPARENT,
                AndroidColor.TRANSPARENT
            )
        )
        try {
            SupabaseConfig.init(applicationContext)
            UserSessionManager.init(applicationContext)
            com.example.data.PesapalConfig.init(applicationContext)
            AppAnalyticsService.init(applicationContext)
            com.example.ui.theme.AppThemeManager.init(applicationContext)
            com.example.ui.screens.HomeScreenDataStore.resetScrollToTop()
        } catch (e: Throwable) {
            android.util.Log.e("MainActivity", "Init exception in onCreate: ${e.message}", e)
        }

        setContent {
            MyApplicationTheme {
                Surface(modifier = Modifier.fillMaxSize()) {
                    Box(modifier = Modifier.fillMaxSize()) {
                        QivoApp(notificationIntent = activeIntent)
                        InAppToastHost()
                    }
                }
            }
        }
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        setIntent(intent)
        activeIntent = intent
    }

    override fun onResume() {
        super.onResume()
        CoroutineScope(Dispatchers.IO).launch {
            try {
                if (UserSessionManager.isTokenExpired(applicationContext)) {
                    com.example.data.SupabaseAuthService.refreshSessionSync(applicationContext, forceRefresh = true)
                }
            } catch (_: Exception) {}
        }
    }
}

@Composable
fun QivoApp(notificationIntent: Intent? = null) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()

    val initialScreen = remember {
        if (AppStartupState.isFirstLaunchInProcess) {
            Screen.Splash
        } else {
            val session = UserSessionManager.getSession(context)
            if (session != null && session.userId.isNotBlank()) {
                val isNewAccount = !session.isProfileCompleted || session.gender.isBlank()
                if (isNewAccount) {
                    Screen.CreateAccount(
                        email = session.email ?: "",
                        userId = session.userId,
                        initialName = session.name ?: "",
                        initialAvatarUrl = session.avatarUrl ?: "",
                        initialCountry = session.country?.ifBlank { "Kenya" } ?: "Kenya"
                    )
                } else {
                    Screen.MainApp(
                        email = session.email ?: "",
                        userId = session.userId,
                        name = session.name ?: "User",
                        gender = session.gender ?: "",
                        country = session.country ?: "United States",
                        avatarUrl = session.avatarUrl ?: "",
                        numericId = session.numericId
                    )
                }
            } else {
                Screen.Welcome
            }
        }
    }

    var currentScreen by remember { mutableStateOf<Screen>(initialScreen) }
    var isAuthenticatingOAuth by remember { mutableStateOf(false) }
    val authService = remember { com.example.data.SupabaseAuthService() }

    fun performGoogleSignIn() {
        scope.launch {
            isAuthenticatingOAuth = true
            try {
                val credentialManager = CredentialManager.create(context)
                val serverClientId = SupabaseConfig.googleWebClientId.trim()

                if (serverClientId.isNotBlank()) {
                    val googleIdOption = GetGoogleIdOption.Builder()
                        .setFilterByAuthorizedAccounts(false)
                        .setServerClientId(serverClientId)
                        .setAutoSelectEnabled(false)
                        .build()

                    val request = GetCredentialRequest.Builder()
                        .addCredentialOption(googleIdOption)
                        .build()

                    val result = credentialManager.getCredential(
                        context = context,
                        request = request
                    )

                    val credential = result.credential
                    if (credential is CustomCredential && credential.type == GoogleIdTokenCredential.TYPE_GOOGLE_ID_TOKEN_CREDENTIAL) {
                        val googleIdTokenCredential = GoogleIdTokenCredential.createFrom(credential.data)
                        val idToken = googleIdTokenCredential.idToken
                        val googleName = googleIdTokenCredential.displayName ?: ""
                        val googleAvatar = googleIdTokenCredential.profilePictureUri?.toString() ?: ""
                        val googleEmail = googleIdTokenCredential.id

                        val authRes = authService.signInWithGoogleIdToken(
                            idToken = idToken,
                            context = context,
                            fallbackGoogleName = googleName,
                            fallbackGoogleAvatar = googleAvatar,
                            fallbackGoogleEmail = googleEmail
                        )
                        if (authRes is com.example.data.AuthResult.Success) {
                            val session = UserSessionManager.getSession(context)
                            val isNew = authRes.isNewUser || session == null || !session.isProfileCompleted || session.gender.isBlank()
                            if (isNew) {
                                val detectedCountry = session?.country?.ifBlank {
                                    com.example.data.CountryDetector.detectCountry(context)?.first ?: "Kenya"
                                } ?: "Kenya"
                                currentScreen = Screen.CreateAccount(
                                    email = authRes.email.ifBlank { session?.email ?: googleEmail },
                                    userId = authRes.userId,
                                    initialName = if (!session?.name.isNullOrBlank() && session?.name != "QIVO User" && session?.name != "User") session!!.name else googleName,
                                    initialAvatarUrl = if (!session?.avatarUrl.isNullOrBlank()) session!!.avatarUrl else googleAvatar,
                                    initialCountry = detectedCountry
                                )
                            } else {
                                AppAnalyticsService.logLogin(session.userId, "google_id_token")
                                AppToast.show("Welcome back! 🎉")
                                currentScreen = Screen.MainApp(
                                    email = session.email,
                                    userId = session.userId,
                                    name = session.name,
                                    gender = session.gender,
                                    country = session.country,
                                    avatarUrl = session.avatarUrl,
                                    numericId = session.numericId
                                )
                            }
                        } else if (authRes is com.example.data.AuthResult.Error) {
                            com.example.data.NetworkUtils.showToast(context, authRes.message, true)
                        }
                    }
                } else {
                    com.example.data.NetworkUtils.showToast(context, "Google Client ID is not configured.")
                }
            } catch (e: GetCredentialCancellationException) {
                // User dismissed the Google accounts popup dialog
                isAuthenticatingOAuth = false
            } catch (e: Exception) {
                val errorMsg = e.message ?: ""
                android.util.Log.w("MainActivity", "Native Google Sign-In error: $errorMsg", e)
                if (!errorMsg.contains("cancel", ignoreCase = true) && !errorMsg.contains("user cancelled", ignoreCase = true)) {
                    val friendlyMsg = if (!com.example.data.NetworkUtils.isOnline(context)) {
                        "No internet connection. Please check your network."
                    } else {
                        "Google Sign-In failed. Please try again or use Email/Password."
                    }
                    com.example.data.NetworkUtils.showToast(context, friendlyMsg, true)
                }
            } finally {
                isAuthenticatingOAuth = false
            }
        }
    }

    // Handle OAuth deep link (qivo://login, qivo://auth, qivo://callback) or FCM push notifications
    LaunchedEffect(notificationIntent) {
        val uri = notificationIntent?.data
        if (uri != null && uri.scheme.equals("qivo", ignoreCase = true) &&
            (uri.host.equals("login", ignoreCase = true) || uri.host.equals("auth", ignoreCase = true) || uri.host.equals("callback", ignoreCase = true))) {
            isAuthenticatingOAuth = true
            val res = authService.handleOAuthCallback(uri, context)
            isAuthenticatingOAuth = false
            if (res is com.example.data.AuthResult.Success) {
                val session = UserSessionManager.getSession(context)
                val isNew = res.isNewUser || session == null || !session.isProfileCompleted || session.gender.isBlank()
                if (isNew) {
                    val detectedCountry = session?.country?.ifBlank {
                        com.example.data.CountryDetector.detectCountry(context)?.first ?: "Kenya"
                    } ?: "Kenya"
                    currentScreen = Screen.CreateAccount(
                        email = res.email.ifBlank { session?.email ?: "" },
                        userId = res.userId,
                        initialName = session?.name ?: "",
                        initialAvatarUrl = session?.avatarUrl ?: "",
                        initialCountry = detectedCountry
                    )
                } else {
                    AppAnalyticsService.logLogin(session.userId, "google_oauth")
                    AppToast.show("Welcome back! 🎉")
                    currentScreen = Screen.MainApp(
                        email = session.email,
                        userId = session.userId,
                        name = session.name,
                        gender = session.gender,
                        country = session.country,
                        avatarUrl = session.avatarUrl,
                        numericId = session.numericId
                    )
                }
            } else if (res is com.example.data.AuthResult.Error) {
                com.example.data.NetworkUtils.showToast(context, res.message, true)
            }
        } else if (notificationIntent?.hasExtra(SupabaseFcmService.EXTRA_NOTIFICATION_TYPE) == true) {
            val session = UserSessionManager.getSession(context)
            if (session != null && session.userId.isNotBlank()) {
                currentScreen = Screen.MainApp(
                    email = session.email,
                    userId = session.userId,
                    name = session.name,
                    gender = session.gender,
                    country = session.country,
                    avatarUrl = session.avatarUrl,
                    numericId = session.numericId
                )
            }
        }
    }

    AnimatedContent(
        targetState = currentScreen,
        transitionSpec = { fadeIn() togetherWith fadeOut() },
        label = "ScreenTransition"
    ) { screen ->
        when (screen) {
            is Screen.Splash -> {
                SplashScreen(
                    onSplashFinished = { session ->
                        AppStartupState.isFirstLaunchInProcess = false
                        try {
                            if (session != null && !session.userId.isNullOrBlank()) {
                                val isNewAccount = !session.isProfileCompleted || session.gender.isBlank()
                                if (isNewAccount) {
                                    val detectedCountry = session.country?.ifBlank { "Kenya" } ?: "Kenya"
                                    currentScreen = Screen.CreateAccount(
                                        email = session.email ?: "",
                                        userId = session.userId,
                                        initialName = session.name ?: "",
                                        initialAvatarUrl = session.avatarUrl ?: "",
                                        initialCountry = detectedCountry
                                    )
                                } else {
                                    currentScreen = Screen.MainApp(
                                        email = session.email ?: "",
                                        userId = session.userId,
                                        name = session.name ?: "User",
                                        gender = session.gender ?: "",
                                        country = session.country ?: "United States",
                                        avatarUrl = session.avatarUrl ?: "",
                                        numericId = session.numericId
                                    )
                                }
                            } else {
                                currentScreen = Screen.Welcome
                            }
                        } catch (e: Throwable) {
                            android.util.Log.e("MainActivity", "Error handling splash finished: ${e.message}", e)
                            currentScreen = Screen.Welcome
                        }
                    }
                )
            }
            is Screen.Welcome -> {
                Box(modifier = Modifier.fillMaxSize()) {
                    WelcomeScreen(
                        onNavigateToEmail = {
                            currentScreen = Screen.EmailAuth
                        },
                        onGoogleSignInClick = {
                            performGoogleSignIn()
                        },
                        onNavigateToCustomerSupport = {
                            currentScreen = Screen.CustomerSupport
                        },
                        isGoogleLoading = isAuthenticatingOAuth
                    )

                    if (isAuthenticatingOAuth) {
                        Surface(
                            modifier = Modifier.fillMaxSize(),
                            color = Color.Black.copy(alpha = 0.5f)
                        ) {
                            Box(
                                modifier = Modifier.fillMaxSize(),
                                contentAlignment = Alignment.Center
                            ) {
                                CircularProgressIndicator(
                                    color = com.example.ui.theme.QivoYellow
                                )
                            }
                        }
                    }
                }
            }
            is Screen.CustomerSupport -> {
                BackHandler {
                    currentScreen = Screen.Welcome
                }
                CustomerSupportScreen(
                    onBackClick = {
                        currentScreen = Screen.Welcome
                    }
                )
            }
            is Screen.EmailAuth -> {
                BackHandler {
                    currentScreen = Screen.Welcome
                }
                EmailAuthScreen(
                    onNavigateBack = {
                        currentScreen = Screen.Welcome
                    },
                    onNavigateToOtp = { email ->
                        currentScreen = Screen.OtpVerification(email)
                    },
                    onNavigateToPassword = { email ->
                        currentScreen = Screen.PasswordLogin(email)
                    }
                )
            }
            is Screen.PasswordLogin -> {
                BackHandler {
                    currentScreen = Screen.EmailAuth
                }
                PasswordLoginScreen(
                    email = screen.email,
                    onNavigateBack = {
                        currentScreen = Screen.EmailAuth
                    },
                    onAuthSuccess = { email: String, userId: String ->
                        val session = UserSessionManager.getSession(context)
                        currentScreen = Screen.MainApp(
                            email = email,
                            userId = userId,
                            name = session?.name ?: "User",
                            gender = session?.gender ?: "",
                            country = session?.country ?: "Kenya",
                            avatarUrl = session?.avatarUrl ?: "",
                            numericId = session?.numericId ?: 0L
                        )
                    }
                )
            }
            is Screen.OtpVerification -> {
                BackHandler {
                    currentScreen = Screen.EmailAuth
                }
                OtpVerificationScreen(
                    email = screen.email,
                    onNavigateBack = {
                        currentScreen = Screen.EmailAuth
                    },
                    onOtpVerified = { verifiedEmail, userId, accessToken ->
                        // Navigate to set password screen after verification
                        currentScreen = Screen.SetPassword(
                            email = verifiedEmail,
                            userId = userId,
                            accessToken = accessToken
                        )
                    }
                )
            }
            is Screen.SetPassword -> {
                BackHandler {
                    currentScreen = Screen.OtpVerification(screen.email)
                }
                SetPasswordScreen(
                    email = screen.email,
                    userId = screen.userId,
                    accessToken = screen.accessToken,
                    onNavigateBack = {
                        currentScreen = Screen.OtpVerification(screen.email)
                    },
                    onPasswordSet = { email, userId ->
                        // Navigate to user details setup
                        val session = UserSessionManager.getSession(context)
                        val emailName = if (email.contains("@")) email.substringBefore("@") else ""
                        val detectedCountry = session?.country?.ifBlank { "Kenya" } ?: "Kenya"
                        currentScreen = Screen.CreateAccount(
                            email = email,
                            userId = userId,
                            initialName = if (!session?.name.isNullOrBlank() && session?.name != "QIVO User" && session?.name != "User") session!!.name else emailName,
                            initialAvatarUrl = session?.avatarUrl ?: "",
                            initialCountry = detectedCountry
                        )
                    }
                )
            }
            is Screen.CreateAccount -> {
                BackHandler {
                    currentScreen = Screen.Welcome
                }
                CreateAccountScreen(
                    userEmail = screen.email,
                    userId = screen.userId,
                    initialName = screen.initialName,
                    initialAvatarUrl = screen.initialAvatarUrl,
                    initialCountry = screen.initialCountry,
                    onSaveDetails = { name, gender, birthDate, country, avatarUrl, numericId ->
                        currentScreen = Screen.UploadAvatar(
                            email = screen.email,
                            userId = screen.userId,
                            name = name,
                            gender = gender,
                            birthDate = birthDate,
                            country = country,
                            initialAvatarUrl = avatarUrl,
                            numericId = numericId
                        )
                    },
                    onSkipToHome = { name, gender, birthDate, country, numericId ->
                        AppAnalyticsService.logSignUp(screen.userId, "email_or_google")
                        AppAnalyticsService.logLogin(screen.userId, "email_or_google")
                        currentScreen = Screen.MainApp(
                            email = screen.email,
                            userId = screen.userId,
                            name = name,
                            gender = gender,
                            country = country,
                            avatarUrl = "",
                            numericId = numericId
                        )
                    }
                )
            }
            is Screen.UploadAvatar -> {
                BackHandler {
                    currentScreen = Screen.CreateAccount(
                        email = screen.email,
                        userId = screen.userId,
                        initialName = screen.name,
                        initialAvatarUrl = screen.initialAvatarUrl,
                        initialCountry = screen.country
                    )
                }
                UploadAvatarScreen(
                    userEmail = screen.email,
                    userId = screen.userId,
                    userName = screen.name,
                    userGender = screen.gender,
                    userCountry = screen.country,
                    initialAvatarUrl = screen.initialAvatarUrl,
                    numericId = screen.numericId,
                    onCompleteAndGoHome = { finalAvatarUrl ->
                        AppAnalyticsService.logSignUp(screen.userId, "email_or_google")
                        AppAnalyticsService.logLogin(screen.userId, "email_or_google")
                        currentScreen = Screen.MainApp(
                            email = screen.email,
                            userId = screen.userId,
                            name = screen.name,
                            gender = screen.gender,
                            country = screen.country,
                            avatarUrl = finalAvatarUrl,
                            numericId = screen.numericId
                        )
                    }
                )
            }
            is Screen.MainApp -> {
                MainBottomNavScaffold(
                    userEmail = screen.email,
                    userId = screen.userId,
                    userName = screen.name,
                    userGender = screen.gender,
                    userCountry = screen.country,
                    userAvatarUrl = screen.avatarUrl,
                    userNumericId = screen.numericId,
                    notificationIntent = notificationIntent,
                    onSignOut = {
                        // Unregister FCM token and invalidate session in Supabase Auth securely on logout
                        val fcmToken = UserSessionManager.getFcmToken(context)
                        CoroutineScope(Dispatchers.IO).launch {
                            if (fcmToken.isNotBlank()) {
                                SupabaseFcmService.removeTokenFromSupabase(context, fcmToken)
                            }
                            authService.signOut(context)
                        }
                        
                        val prefs = context.getSharedPreferences("user_session", android.content.Context.MODE_PRIVATE)
                        val isDeleted = prefs.getBoolean("account_deleted_toast", false)
                        if (isDeleted) {
                            prefs.edit().remove("account_deleted_toast").apply()
                            AppToast.show("Account deleted successfully.")
                        } else {
                            AppToast.show("Signed out from QIVO")
                        }
                        
                        currentScreen = Screen.Welcome
                    }
                )
            }
        }
    }
}

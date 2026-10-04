package com.example.ui.screens
import com.example.ui.components.AppToast

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.asPaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Chat
import androidx.compose.material.icons.filled.Group
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.filled.Person
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.IconButton
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.border
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.window.Dialog
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.Visibility
import androidx.compose.material.icons.filled.VisibilityOff
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Key
import coil.compose.AsyncImage
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import com.example.calling.ActiveCallSessionManager
import com.example.data.AppAdvertisement
import com.example.data.CoinPackage
import com.example.data.CountryOption
import com.example.data.InAppNotification
import com.example.data.InAppNotificationManager
import com.example.data.NetworkUtils
import com.example.data.PartyRoom
import com.example.data.SupabaseAdService
import com.example.data.SupabaseChatService
import com.example.data.SupabaseFcmService
import com.example.data.SupabaseProfileService
import com.example.data.UserProfile
import com.example.data.UserSessionManager
import com.example.data.PartyRoomSessionManager
import com.example.ui.components.AppOpenAdPopup
import com.example.ui.components.Chat3DNavIcon
import com.example.ui.components.FloatingPartyRoomBubble
import com.example.ui.components.Home3DNavIcon
import com.example.ui.components.InAppNotificationBanner
import com.example.ui.components.Me3DNavIcon
import com.example.ui.components.Party3DNavIcon
import com.example.ui.theme.QivoYellow
import kotlinx.coroutines.launch

enum class MainTab {
    HOME,
    PARTY,
    CHAT,
    ME
}

sealed class AppNavStep {
    data class TabStep(val tab: MainTab) : AppNavStep()
    data class UserDetailStep(val user: UserProfile) : AppNavStep()
    data class ConversationStep(val user: UserProfile) : AppNavStep()
    data object EditProfileStep : AppNavStep()
    data object TaskCenterStep : AppNavStep()
    data object MessageBlastStep : AppNavStep()
    data object SupportStep : AppNavStep()
    data object VerifyStep : AppNavStep()
    data object SettingsStep : AppNavStep()
    data object WalletRechargeStep : AppNavStep()
    data class CoinSellerListStep(val selectedPackage: CoinPackage? = null, val selectedCountry: CountryOption? = null) : AppNavStep()
    data object AwardCoinsStep : AppNavStep()
    data object ManageRolesStep : AppNavStep()
    data object ManageReportsStep : AppNavStep()
    data object ManageAdsStep : AppNavStep()
    data object AdminAnalyticsStep : AppNavStep()
    data object CoinHistoryStep : AppNavStep()
    data object BlockedListStep : AppNavStep()
    data class FollowersStep(val targetUserId: String = "") : AppNavStep()
    data class FollowingStep(val targetUserId: String = "") : AppNavStep()
    data class FriendsStep(val targetUserId: String = "") : AppNavStep()
    data class VisitorsStep(val targetUserId: String = "") : AppNavStep()
    data class FollowsListStep(val initialTab: FollowTab = FollowTab.FOLLOWING, val targetUserId: String = "") : AppNavStep()
    data object CreatePartyRoomStep : AppNavStep()
    data class PartyRoomDetailStep(val room: PartyRoom) : AppNavStep()
    data class AgencyCenterStep(val initialView: AgencyFullscreenView = AgencyFullscreenView.NONE) : AppNavStep()
    data object IncomeConversionStep : AppNavStep()
    data object StoreStep : AppNavStep()
    data object BagStep : AppNavStep()
    data object LevelStep : AppNavStep()
    data object GameCenterStep : AppNavStep()
    data object AboutQivoStep : AppNavStep()
    data object AccountSecurityStep : AppNavStep()
    data object CallSettingsStep : AppNavStep()
    data object DiamondHistoryStep : AppNavStep()
    data object OfficialTeamStep : AppNavStep()
}

@Composable
fun MainBottomNavScaffold(
    userEmail: String,
    userId: String,
    userName: String = "QIVO User",
    userGender: String = "",
    userCountry: String = "United States",
    userAvatarUrl: String = "",
    userNumericId: Long = 0L,
    notificationIntent: android.content.Intent? = null,
    onSignOut: () -> Unit
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val profileService = remember { SupabaseProfileService() }
    val chatService = remember { SupabaseChatService() }
    val adService = remember { SupabaseAdService() }
    val partyService = remember { com.example.data.SupabasePartyService() }
    var showPasswordPromptForRoom by remember { mutableStateOf<PartyRoom?>(null) }
    var passwordInputState by remember { mutableStateOf("") }
    var passwordPromptError by remember { mutableStateOf("") }

    // App-Open Interstitial / Announcement Ad State
    var openAd by remember { mutableStateOf<AppAdvertisement?>(null) }
    var hasDismissedOpenAd by rememberSaveable { mutableStateOf(false) }

    LaunchedEffect(Unit) {
        if (!hasDismissedOpenAd) {
            val ad = adService.fetchActiveAd(context, AppAdvertisement.AD_TYPE_FULLSCREEN)
            if (ad != null && ad.isActive && ad.imageUrl.isNotBlank()) {
                openAd = ad
            }
        }
    }

    // Request notification permissions on Android 13+ (API 33+)
    val notificationPermissionLauncher = androidx.activity.compose.rememberLauncherForActivityResult(
        contract = androidx.activity.result.contract.ActivityResultContracts.RequestPermission()
    ) { /* Permission result handled */ }

    LaunchedEffect(Unit) {
        if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.TIRAMISU) {
            if (androidx.core.content.ContextCompat.checkSelfPermission(
                    context,
                    android.Manifest.permission.POST_NOTIFICATIONS
                ) != android.content.pm.PackageManager.PERMISSION_GRANTED
            ) {
                notificationPermissionLauncher.launch(android.Manifest.permission.POST_NOTIFICATIONS)
            }
        }
        // Initialize FCM registration with Supabase
        SupabaseFcmService.initializeFcm(context, userId)
    }

    // App-wide global chat listener & real-time synchronization so new messages update instantly across all tabs/screens
    LaunchedEffect(userId) {
        if (userId.isNotBlank()) {
            com.example.data.ChatStateHolder.initialize(context, userId)
            com.example.data.ChatStateHolder.connectRealtime(userId)
            com.example.data.ChatStateHolder.syncWithNetwork(context, userId)
        }
    }

    val scaffoldLifecycleOwner = LocalLifecycleOwner.current
    DisposableEffect(scaffoldLifecycleOwner, userId) {
        val observer = LifecycleEventObserver { _, event ->
            if (event == Lifecycle.Event.ON_RESUME) {
                if (userId.isNotBlank()) {
                    com.example.data.ChatStateHolder.connectRealtime(userId)
                    com.example.data.ChatStateHolder.syncWithNetwork(context, userId)
                }
            }
        }
        scaffoldLifecycleOwner.lifecycle.addObserver(observer)
        onDispose {
            scaffoldLifecycleOwner.lifecycle.removeObserver(observer)
        }
    }

    // Live Unread Messages Count Observation
    val totalUnreadCount by SupabaseChatService.totalUnreadCount.collectAsState()

    // Live Real-Time Network State Observation
    val isOnline by remember { NetworkUtils.observeNetworkConnectivity(context) }
        .collectAsState(initial = NetworkUtils.isOnline(context))

    // Real-Time Network State Observation with Alerts
    var previousNetworkState by remember { mutableStateOf<com.example.data.NetworkConnectionState?>(null) }
    LaunchedEffect(Unit) {
        com.example.data.NetworkUtils.observeDetailedNetworkStatus(context).collect { status ->
            val prev = previousNetworkState
            if (prev != null && prev != status.state) {
                when (status.state) {
                    com.example.data.NetworkConnectionState.OFFLINE -> {
                        com.example.data.NetworkUtils.showToast(context, "No internet connection")
                    }
                    com.example.data.NetworkConnectionState.ONLINE_UNSTABLE -> {
                        com.example.data.NetworkUtils.showToast(context, "Unstable internet connection")
                    }
                    com.example.data.NetworkConnectionState.ONLINE_STABLE -> {
                        if (prev == com.example.data.NetworkConnectionState.OFFLINE) {
                            com.example.data.NetworkUtils.showToast(context, "Internet connection restored!")
                        }
                    }
                }
            }
            previousNetworkState = status.state
        }
    }

    // Unified Navigation Stack
    val navStack = remember { mutableStateListOf<AppNavStep>(AppNavStep.TabStep(MainTab.HOME)) }
    val currentStep = navStack.lastOrNull() ?: AppNavStep.TabStep(MainTab.HOME)

    // Handle Notification Tap: Verify user, check blocking, and load authoritative profile/conversation from Supabase
    LaunchedEffect(notificationIntent) {
        val intent = notificationIntent ?: return@LaunchedEffect
        val notifType = intent.getStringExtra(SupabaseFcmService.EXTRA_NOTIFICATION_TYPE) ?: return@LaunchedEffect
        val recipientId = intent.getStringExtra(SupabaseFcmService.EXTRA_RECIPIENT_USER_ID) ?: ""
        
        // 1. Verify recipient user matches authenticated user
        if (recipientId.isNotBlank() && !recipientId.equals(userId, ignoreCase = true)) {
            return@LaunchedEffect
        }
        
        val senderId = intent.getStringExtra(SupabaseFcmService.EXTRA_SENDER_USER_ID) ?: return@LaunchedEffect
        if (senderId.isBlank()) return@LaunchedEffect

        // 2. Verify blocking security
        if (profileService.isUserBlocked(userId, senderId, context) ||
            profileService.isUserBlocked(senderId, userId, context)) {
            return@LaunchedEffect
        }

        // 3. Load Authoritative Profile from Supabase
        val authoritativeUser = profileService.fetchProfileById(senderId)
            ?: UserProfile(
                id = senderId,
                numericId = 0L,
                email = "",
                name = intent.getStringExtra(SupabaseFcmService.EXTRA_SENDER_NAME) ?: "QIVO User",
                gender = "Female",
                birthDate = "2000-01-01",
                country = "United States",
                avatarUrl = intent.getStringExtra(SupabaseFcmService.EXTRA_SENDER_AVATAR) ?: ""
            )

        // 4. Navigate authoritatively based on notification type
        if (notifType == SupabaseFcmService.TYPE_CHAT_MESSAGE || notifType == SupabaseFcmService.TYPE_INCOMING_CALL) {
            // Check if already in this conversation
            val existing = navStack.lastOrNull()
            if (existing !is AppNavStep.ConversationStep || existing.user.id != authoritativeUser.id) {
                navStack.add(AppNavStep.ConversationStep(authoritativeUser))
            }
        }
    }

    // Current User Profile State
    var currentProfileState by remember(userId) {
        val session = UserSessionManager.getSession(context)
        val resolvedInitialName = when {
            userName.isNotBlank() && userName != "QIVO User" -> userName
            !session?.name.isNullOrBlank() && session?.name != "QIVO User" -> session.name
            else -> userName.ifBlank { session?.name ?: "User" }
        }
        val resolvedInitialGender = when {
            userGender.equals("female", ignoreCase = true) ||
            userGender.equals("f", ignoreCase = true) ||
            userGender.equals("woman", ignoreCase = true) ||
            userGender.equals("w", ignoreCase = true) -> "Female"

            userGender.equals("male", ignoreCase = true) ||
            userGender.equals("m", ignoreCase = true) ||
            userGender.equals("man", ignoreCase = true) -> "Male"

            session?.gender.equals("female", ignoreCase = true) ||
            session?.gender.equals("f", ignoreCase = true) ||
            session?.gender.equals("woman", ignoreCase = true) ||
            session?.gender.equals("w", ignoreCase = true) -> "Female"

            session?.gender.equals("male", ignoreCase = true) ||
            session?.gender.equals("m", ignoreCase = true) ||
            session?.gender.equals("man", ignoreCase = true) -> "Male"

            else -> ""
        }
        val resolvedInitialCountry = when {
            userCountry.isNotBlank() -> userCountry
            !session?.country.isNullOrBlank() -> session.country
            else -> "Kenya"
        }
        val resolvedAvatarUrl = when {
            userAvatarUrl.isNotBlank() -> userAvatarUrl
            !session?.avatarUrl.isNullOrBlank() -> session.avatarUrl
            else -> ""
        }
        mutableStateOf(
            UserProfile(
                id = userId,
                numericId = session?.numericId ?: userNumericId,
                email = userEmail,
                name = resolvedInitialName,
                gender = resolvedInitialGender,
                birthDate = session?.birthDate ?: "2000-01-01",
                country = resolvedInitialCountry,
                avatarUrl = resolvedAvatarUrl,
                coins = session?.coins ?: 0L,
                isAdmin = session?.isAdmin ?: false,
                isCoinSeller = session?.isCoinSeller ?: false,
                isAgent = session?.isAgent ?: false
            )
        )
    }

    val homeListState = androidx.compose.foundation.lazy.rememberLazyListState()
    val chatListState = androidx.compose.foundation.lazy.rememberLazyListState()
    val partyGridState = androidx.compose.foundation.lazy.grid.rememberLazyGridState()

    // Live periodic unread messages count sync & in-app notification detection
    val knownMessageIds = remember { mutableSetOf<Long>() }
    var isMessageSyncInitialized by remember { mutableStateOf(false) }

    // Always ensure cold start begins at the top of the Home screen
    LaunchedEffect(Unit) {
        com.example.ui.screens.HomeScreenDataStore.resetScrollToTop()
        try {
            homeListState.scrollToItem(0, 0)
        } catch (_: Exception) {}
    }

    // APP INITIALIZATION SEQUENCE (Strict Order)
    LaunchedEffect(userId, isOnline) {
        if (userId.isNotBlank() && isOnline) {
            try {
                // 1. CONFIRM SUPABASE SESSION
                val session = UserSessionManager.getSession(context)
                if (session == null || session.userId.isBlank()) {
                    android.util.Log.e("AppInit", "Session not found, stopping initialization.")
                    return@LaunchedEffect
                }

                // 2. GET/CREATE PROFILE & INITIALIZE APP
                val fetched = profileService.fetchProfile(userId, userEmail, session.accessToken)
                if (fetched != null) {
                    currentProfileState = fetched
                    UserSessionManager.saveSession(
                        context = context,
                        email = fetched.email,
                        userId = fetched.id,
                        name = fetched.name,
                        gender = fetched.gender,
                        country = fetched.country,
                        avatarUrl = fetched.avatarUrl,
                        numericId = fetched.numericId,
                        coins = fetched.coins,
                        isAdmin = fetched.isAdmin,
                        isCoinSeller = fetched.isCoinSeller,
                        isAgent = fetched.isAgent,
                        accessToken = session.accessToken
                    )
                }

                // 3. WELCOME BONUS (If applicable)
                if (!HomeScreenDataStore.hasCheckedWelcomeBonus) {
                    HomeScreenDataStore.hasCheckedWelcomeBonus = true
                    try {
                        val (claimedBonus, bonusAmount) = profileService.claimDeviceWelcomeBonus(context, userId, session.accessToken)
                        if (claimedBonus && bonusAmount > 0) {
                            AppToast.show("Welcome Bonus: +$bonusAmount Coins! 🎁")
                            // Refresh balance after bonus
                            profileService.fetchCoins(userId).let { newBal ->
                                currentProfileState = currentProfileState.copy(coins = newBal)
                                UserSessionManager.saveCoins(context, newBal)
                            }
                        }
                    } catch (_: Exception) {}
                }

                // 4. HEARTBEAT & CHAT LIST/MESSAGES
                ActiveCallSessionManager.init(context, userId)
                profileService.updateOnlineStatus(userId, true)
                
                // Concurrent background loops for presence and messages
                launch {
                    while (isOnline) {
                        kotlinx.coroutines.delay(30_000L)
                        profileService.updateOnlineStatus(userId, true)
                    }
                }

                launch {
                    while (isOnline) {
                        try {
                            val messages = chatService.checkRecentIncomingMessages(userId, context, limit = 30)
                            if (messages.isNotEmpty()) {
                                com.example.data.ChatStateHolder.appendOrUpdateMessages(messages)
                            }
                            val unreadIncoming = messages.filter { !it.isRead && it.receiverId.trim().equals(userId.trim(), ignoreCase = true) }
                            
                            if (isMessageSyncInitialized) {
                                for (msg in unreadIncoming) {
                                    if (msg.id > 0L && !knownMessageIds.contains(msg.id)) {
                                        knownMessageIds.add(msg.id)
                                        if (SupabaseFcmService.activeConversationUserId != msg.senderId) {
                                            InAppNotificationManager.showNotification(msg.senderId, msg.senderName, msg.senderAvatar, msg.message, "")
                                        }
                                    }
                                }
                            } else {
                                messages.forEach { if (it.id > 0L) knownMessageIds.add(it.id) }
                                isMessageSyncInitialized = true
                            }
                        } catch (_: Exception) {}
                        kotlinx.coroutines.delay(3_500L)
                    }
                }
            } catch (e: Exception) {
                android.util.Log.e("AppInit", "Initialization sequence failed: ${e.message}")
            }
        }
    }

    var homeTabClickCount by remember { mutableStateOf(0) }
    var homeRefreshTrigger by remember { mutableStateOf(0L) }
    var chatRefreshTrigger by remember { mutableStateOf(0L) }

    // Offline Error Screen & Pending Navigation Target
    fun navigateTo(step: AppNavStep) {
        if (navStack.lastOrNull() != step) {
            navStack.add(step)
        }
    }

    fun navigateBack() {
        if (navStack.size > 1) {
            navStack.removeAt(navStack.lastIndex)
        }
    }

    // Auto-link active party room session if app closed accidentally or connection dropped
    LaunchedEffect(Unit) {
        try {
            val persistedRoomId = PartyRoomSessionManager.getPersistedRoomId(context)
            if (!persistedRoomId.isNullOrBlank()) {
                val restoredRoom = partyService.fetchPartyRoomById(persistedRoomId)
                if (restoredRoom != null) {
                    PartyRoomSessionManager.enterRoom(restoredRoom, context)
                    if (!navStack.any { it is AppNavStep.PartyRoomDetailStep }) {
                        navigateTo(AppNavStep.PartyRoomDetailStep(restoredRoom))
                        com.example.ui.components.AppToast.show("Reconnected to Party Lounge 🎧")
                    }
                } else {
                    PartyRoomSessionManager.clearActiveRoomSession(context)
                }
            }
        } catch (_: Exception) {}
    }

    fun selectTab(tab: MainTab) {
        val current = navStack.lastOrNull()
        if (current is AppNavStep.TabStep && current.tab == tab) {
            if (tab == MainTab.HOME) {
                homeTabClickCount++
                if (homeTabClickCount == 1) {
                    // 1st click: take home to top of home
                    scope.launch {
                        try {
                            homeListState.animateScrollToItem(0)
                        } catch (_: Exception) {}
                    }
                } else {
                    // 2nd click: refresh and bring updates
                    homeTabClickCount = 0
                    homeRefreshTrigger = System.currentTimeMillis()
                }
            } else if (tab == MainTab.CHAT) {
                chatRefreshTrigger = System.currentTimeMillis()
                scope.launch {
                    try {
                        chatListState.animateScrollToItem(0)
                    } catch (_: Exception) {}
                }
            }
            return
        }

        // When switching tabs to Home, remain where you were on Home (do not scroll to top)
        homeTabClickCount = 0

        navStack.clear()
        navStack.add(AppNavStep.TabStep(tab))
    }

    var showExitConfirmationDialog by remember { mutableStateOf(false) }

    // Back button handling: pops navigation stack or shows exit confirmation dialog when at root
    BackHandler(enabled = true) {
        if (navStack.size > 1) {
            navigateBack()
        } else {
            showExitConfirmationDialog = true
        }
    }

    val handleSignOut: () -> Unit = {
        scope.launch {
            if (userId.isNotBlank()) {
                profileService.updateOnlineStatus(userId, false)
            }
            onSignOut()
        }
    }

    val isFullScreenOverlay = currentStep !is AppNavStep.TabStep
    val activeTab = (currentStep as? AppNavStep.TabStep)?.tab ?: (navStack.filterIsInstance<AppNavStep.TabStep>().lastOrNull()?.tab ?: MainTab.HOME)
    val navBarBottomInset = WindowInsets.navigationBars.asPaddingValues().calculateBottomPadding()

    Box(
        modifier = Modifier
            .fillMaxSize()
            .testTag("main_bottom_nav_scaffold")
    ) {
        // Screen Content Container
        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(bottom = if (isFullScreenOverlay) 0.dp else (56.dp + navBarBottomInset))
        ) {
            Column(modifier = Modifier.fillMaxSize()) {
                // Main screen view
                Box(modifier = Modifier.fillMaxSize()) {
                    when (currentStep) {

                is AppNavStep.ConversationStep -> {
                    ConversationScreen(
                        targetUser = currentStep.user,
                        onBackClick = { navigateBack() },
                        onOpenUserDetails = { user ->
                            navigateTo(AppNavStep.UserDetailStep(user))
                        },
                        onOpenRechargeWallet = { navigateTo(AppNavStep.WalletRechargeStep) }
                    )
                }
                is AppNavStep.UserDetailStep -> {
                    val isCurrent = (userId.isNotBlank() && (currentStep.user.id == userId || currentStep.user.id.isBlank())) ||
                            (userNumericId > 0 && currentStep.user.numericId == userNumericId)
                    val effectiveUser = if (isCurrent && currentProfileState != null) {
                        currentProfileState!!
                    } else {
                        currentStep.user
                    }
                    UserDetailScreen(
                        targetUser = effectiveUser,
                        onBackClick = { navigateBack() },
                        onStartChat = { user ->
                            navigateTo(AppNavStep.ConversationStep(user))
                        },
                        onOpenFollowsList = { tab, targetId ->
                            when (tab) {
                                FollowTab.FRIENDS -> navigateTo(AppNavStep.FriendsStep(targetId))
                                FollowTab.FOLLOWING -> navigateTo(AppNavStep.FollowingStep(targetId))
                                FollowTab.FOLLOWERS -> navigateTo(AppNavStep.FollowersStep(targetId))
                                FollowTab.VISITORS -> navigateTo(AppNavStep.VisitorsStep(targetId))
                            }
                        },
                        onOpenRechargeWallet = { navigateTo(AppNavStep.WalletRechargeStep) },
                        onOpenEditProfile = { navigateTo(AppNavStep.EditProfileStep) }
                    )
                }
                is AppNavStep.EditProfileStep -> {
                    PersonalInformationScreen(
                        currentProfile = currentProfileState ?: UserProfile(
                            id = userId,
                            numericId = userNumericId,
                            email = userEmail,
                            name = userName,
                            gender = userGender,
                            birthDate = "2000-01-01",
                            country = userCountry,
                            avatarUrl = currentProfileState?.avatarUrl ?: userAvatarUrl
                        ),
                        onBackClick = { navigateBack() },
                        onProfileSaved = { updatedProfile ->
                            currentProfileState = updatedProfile
                            UserSessionManager.saveSession(
                                context = context,
                                email = updatedProfile.email,
                                userId = updatedProfile.id,
                                name = updatedProfile.name,
                                gender = updatedProfile.gender,
                                country = updatedProfile.country,
                                avatarUrl = updatedProfile.avatarUrl,
                                numericId = updatedProfile.numericId
                            )
                            navigateBack()
                        }
                    )
                }
                is AppNavStep.TaskCenterStep -> {
                    TaskCenterScreen(
                        onBackClick = { navigateBack() },
                        onNavigateToVerification = {
                            navigateBack()
                            navigateTo(AppNavStep.VerifyStep)
                        }
                    )
                }
                is AppNavStep.MessageBlastStep -> {
                    MessageBlastScreen(
                        onBackClick = { navigateBack() }
                    )
                }
                is AppNavStep.SupportStep -> {
                    CustomerSupportScreen(
                        onBackClick = { navigateBack() }
                    )
                }
                is AppNavStep.VerifyStep -> {
                    VerificationScreen(
                        onBackClick = { navigateBack() },
                        onVerificationSuccess = { navigateBack() }
                    )
                }
                is AppNavStep.SettingsStep -> {
                    SystemSettingsScreen(
                        onBackClick = { navigateBack() },
                        onSignOut = handleSignOut,
                        onOpenBlockedList = {
                            navigateTo(AppNavStep.BlockedListStep)
                        },
                        onOpenAccountSecurity = {
                            navigateTo(AppNavStep.AccountSecurityStep)
                        },
                        onOpenAboutQivo = {
                            navigateTo(AppNavStep.AboutQivoStep)
                        },
                        onOpenCallSettings = {
                            navigateTo(AppNavStep.CallSettingsStep)
                        }
                    )
                }
                is AppNavStep.CallSettingsStep -> {
                    CallSettingsScreen(
                        onBackClick = { navigateBack() }
                    )
                }
                is AppNavStep.AboutQivoStep -> {
                    AboutQivoScreen(
                        onBackClick = { navigateBack() }
                    )
                }
                is AppNavStep.AccountSecurityStep -> {
                    AccountSecurityScreen(
                        userEmail = userEmail,
                        userId = userId,
                        userName = currentProfileState.name.ifBlank { userName },
                        userNumericId = if (currentProfileState.numericId > 0L) currentProfileState.numericId else userNumericId,
                        onBackClick = { navigateBack() },
                        onSignOut = handleSignOut
                    )
                }
                is AppNavStep.BlockedListStep -> {
                    BlockedListScreen(
                        currentUserId = userId,
                        onBackClick = { navigateBack() },
                        onOpenProfile = { u ->
                            navigateTo(AppNavStep.UserDetailStep(u))
                        }
                    )
                }
                is AppNavStep.WalletRechargeStep -> {
                    WalletRechargeScreen(
                        userId = userId,
                        userEmail = userEmail,
                        userCountry = currentProfileState.country.ifBlank { userCountry },
                        initialCoins = currentProfileState.coins,
                        onBackClick = { navigateBack() },
                        onOpenCoinHistory = {
                            navigateTo(AppNavStep.CoinHistoryStep)
                        },
                        onOpenCoinSellers = { selectedPackage, selectedCountry ->
                            navigateTo(AppNavStep.CoinSellerListStep(selectedPackage, selectedCountry))
                        },
                        onCoinsUpdated = { newCoins ->
                            currentProfileState = currentProfileState.copy(coins = newCoins)
                        }
                    )
                }
                is AppNavStep.CoinSellerListStep -> {
                    CoinSellerListScreen(
                        currentUserId = userId,
                        selectedPackage = currentStep.selectedPackage,
                        selectedCountry = currentStep.selectedCountry,
                        onBackClick = { navigateBack() },
                        onOpenConversation = { seller ->
                            navigateTo(AppNavStep.ConversationStep(seller))
                        }
                    )
                }
                is AppNavStep.AwardCoinsStep -> {
                    AwardCoinsScreen(
                        currentUserId = userId,
                        currentNumericId = currentProfileState.numericId,
                        isAdmin = currentProfileState.isAdmin,
                        isCoinSeller = currentProfileState.isCoinSeller,
                        onBackClick = { navigateBack() },
                        onCoinsAwarded = {
                            scope.launch {
                                val c = profileService.fetchCoins(userId)
                                currentProfileState = currentProfileState.copy(coins = c)
                            }
                        }
                    )
                }
                is AppNavStep.ManageRolesStep -> {
                    ManageRolesScreen(
                        currentUserId = userId,
                        onBackClick = { navigateBack() }
                    )
                }
                is AppNavStep.ManageReportsStep -> {
                    ManageReportsScreen(
                        currentUserId = userId,
                        onBackClick = { navigateBack() }
                    )
                }
                is AppNavStep.ManageAdsStep -> {
                    ManageAdvertisementsScreen(
                        currentUserId = userId,
                        onBackClick = { navigateBack() }
                    )
                }
                is AppNavStep.AdminAnalyticsStep -> {
                    AdminAnalyticsScreen(
                        currentUserId = userId,
                        onBackClick = { navigateBack() }
                    )
                }
                is AppNavStep.CoinHistoryStep -> {
                    CoinHistoryScreen(
                        userId = userId,
                        onBackClick = { navigateBack() }
                    )
                }
                is AppNavStep.FollowersStep -> {
                    FollowersScreen(
                        currentUserId = userId,
                        targetUserId = currentStep.targetUserId,
                        onBackClick = { navigateBack() },
                        onOpenUserDetail = { user ->
                            navigateTo(AppNavStep.UserDetailStep(user))
                        },
                        onOpenConversation = { user ->
                            navigateTo(AppNavStep.ConversationStep(user))
                        }
                    )
                }
                is AppNavStep.FollowingStep -> {
                    FollowingScreen(
                        currentUserId = userId,
                        targetUserId = currentStep.targetUserId,
                        onBackClick = { navigateBack() },
                        onOpenUserDetail = { user ->
                            navigateTo(AppNavStep.UserDetailStep(user))
                        },
                        onOpenConversation = { user ->
                            navigateTo(AppNavStep.ConversationStep(user))
                        }
                    )
                }
                is AppNavStep.FriendsStep -> {
                    FriendsScreen(
                        currentUserId = userId,
                        targetUserId = currentStep.targetUserId,
                        onBackClick = { navigateBack() },
                        onOpenUserDetail = { user ->
                            navigateTo(AppNavStep.UserDetailStep(user))
                        },
                        onOpenConversation = { user ->
                            navigateTo(AppNavStep.ConversationStep(user))
                        }
                    )
                }
                is AppNavStep.VisitorsStep -> {
                    VisitorsScreen(
                        currentUserId = userId,
                        targetUserId = currentStep.targetUserId,
                        onBackClick = { navigateBack() },
                        onOpenUserDetail = { user ->
                            navigateTo(AppNavStep.UserDetailStep(user))
                        },
                        onOpenConversation = { user ->
                            navigateTo(AppNavStep.ConversationStep(user))
                        }
                    )
                }
                is AppNavStep.FollowsListStep -> {
                    FollowsListScreen(
                        currentUserId = userId,
                        targetUserId = currentStep.targetUserId,
                        initialTab = currentStep.initialTab,
                        onBackClick = { navigateBack() },
                        onOpenUserDetail = { user ->
                            navigateTo(AppNavStep.UserDetailStep(user))
                        },
                        onOpenConversation = { user ->
                            navigateTo(AppNavStep.ConversationStep(user))
                        }
                    )
                }
                is AppNavStep.CreatePartyRoomStep -> {
                    CreatePartyRoomScreen(
                        currentUserId = userId,
                        currentUserName = currentProfileState?.name ?: userName,
                        currentUserAvatar = currentProfileState?.avatarUrl ?: userAvatarUrl,
                        onBackClick = { navigateBack() },
                        onRoomCreated = { newRoom ->
                            navigateBack()
                            navigateTo(AppNavStep.PartyRoomDetailStep(newRoom))
                        }
                    )
                }
                is AppNavStep.PartyRoomDetailStep -> {
                    PartyRoomDetailScreen(
                        room = currentStep.room,
                        currentUserId = userId,
                        currentUserName = currentProfileState?.name ?: userName,
                        currentUserAvatar = currentProfileState?.avatarUrl ?: userAvatarUrl,
                        onLeaveRoom = {
                            PartyRoomSessionManager.leaveRoom(userId, context, scope)
                            PartyRoomSessionManager.clearActiveRoomSession(context)
                            navStack.removeAll { it is AppNavStep.PartyRoomDetailStep }
                            if (navStack.isEmpty()) {
                                navStack.add(AppNavStep.TabStep(MainTab.PARTY))
                            }
                        },
                        onOpenRechargeWallet = { navigateTo(AppNavStep.WalletRechargeStep) }
                    )
                }
                is AppNavStep.AgencyCenterStep -> {
                    AgencyCenterScreen(
                        currentUserId = userId,
                        currentNumericId = currentProfileState?.numericId ?: userNumericId,
                        currentUserName = currentProfileState?.name ?: userName,
                        currentUserAvatar = currentProfileState?.avatarUrl ?: userAvatarUrl,
                        currentUserGender = currentProfileState?.gender ?: userGender,
                        isAgent = currentProfileState?.isAgent ?: false,
                        initialView = currentStep.initialView,
                        onBackClick = { navigateBack() },
                        onOpenRechargeWallet = { navigateTo(AppNavStep.WalletRechargeStep) },
                        onAgentStatusChanged = { isNowAgent ->
                            currentProfileState = currentProfileState.copy(isAgent = isNowAgent)
                        }
                    )
                }
                is AppNavStep.IncomeConversionStep -> {
                    IncomeScreen(
                        userId = userId,
                        onBackClick = { navigateBack() },
                        onOpenDiamondHistory = { navigateTo(AppNavStep.DiamondHistoryStep) },
                        onCoinsUpdated = { newCoins ->
                            currentProfileState = currentProfileState.copy(coins = newCoins)
                        }
                    )
                }
                is AppNavStep.DiamondHistoryStep -> {
                    DiamondHistoryScreen(
                        userId = userId,
                        onBackClick = { navigateBack() }
                    )
                }
                is AppNavStep.StoreStep -> {
                    StoreScreen(
                        onBackClick = { navigateBack() },
                        onNavigateToRecharge = { navigateTo(AppNavStep.WalletRechargeStep) },
                        onNavigateToBag = { navigateTo(AppNavStep.BagStep) }
                    )
                }
                is AppNavStep.BagStep -> {
                    BagScreen(
                        onBack = { navigateBack() },
                        onNavigateToStore = { navigateTo(AppNavStep.StoreStep) }
                    )
                }
                is AppNavStep.LevelStep -> {
                    LevelScreen(
                        currentUserId = userId,
                        onBackClick = { navigateBack() },
                        onOpenRecharge = { navigateTo(AppNavStep.WalletRechargeStep) }
                    )
                }
                is AppNavStep.GameCenterStep -> {
                    GameCenterScreen(
                        onBackClick = { navigateBack() },
                        onOpenRechargeWallet = { navigateTo(AppNavStep.WalletRechargeStep) }
                    )
                }
                is AppNavStep.OfficialTeamStep -> {
                    OfficialTeamScreen(
                        onBackClick = { navigateBack() },
                        onOpenUserDetails = { user -> navigateTo(AppNavStep.UserDetailStep(user)) }
                    )
                }
                is AppNavStep.TabStep -> {
                    when (currentStep.tab) {
                        MainTab.HOME -> HomeScreen(
                            refreshTrigger = homeRefreshTrigger,
                            listState = homeListState,
                            userId = userId,
                            userEmail = userEmail,
                            userGender = currentProfileState?.gender ?: userGender,
                            userCountry = currentProfileState?.country ?: userCountry,
                            onOpenUserDetail = { user ->
                                navigateTo(AppNavStep.UserDetailStep(user))
                            },
                            onOpenConversation = { user ->
                                navigateTo(AppNavStep.ConversationStep(user))
                            },
                            onOpenMessageBlast = {
                                navigateTo(AppNavStep.MessageBlastStep)
                            },
                            onOpenTaskCenter = {
                                navigateTo(AppNavStep.TaskCenterStep)
                            },
                            onOpenGameCenter = {
                                if (NetworkUtils.requireOnline(context, "No internet connection. Cannot play games while offline.")) {
                                    navigateTo(AppNavStep.GameCenterStep)
                                }
                            }
                        )
                        MainTab.PARTY -> PartyScreen(
                            gridState = partyGridState,
                            onOpenPartyRoom = { room ->
                                scope.launch {
                                    val freshRoom = partyService.fetchPartyRoomById(room.id) ?: room
                                    if (freshRoom.roomPassword.isNotBlank() && freshRoom.hostUserId != userId) {
                                        showPasswordPromptForRoom = freshRoom
                                        passwordInputState = ""
                                        passwordPromptError = ""
                                    } else {
                                        PartyRoomSessionManager.enterRoom(freshRoom, context)
                                        partyService.joinPartyRoomRpc(freshRoom.id, null)
                                        navigateTo(AppNavStep.PartyRoomDetailStep(freshRoom))
                                    }
                                }
                            },
                            onOpenCreateRoom = {
                                navigateTo(AppNavStep.CreatePartyRoomStep)
                            }
                        )
                        MainTab.CHAT -> ChatScreen(
                            refreshTrigger = chatRefreshTrigger,
                            listState = chatListState,
                            onOpenUserDetail = { user ->
                                navigateTo(AppNavStep.UserDetailStep(user))
                            },
                            onOpenConversation = { user ->
                                navigateTo(AppNavStep.ConversationStep(user))
                            },
                            onOpenAgencyGroupChat = {
                                navigateTo(AppNavStep.AgencyCenterStep(initialView = AgencyFullscreenView.GROUP_CHAT))
                            },
                            onOpenOfficialTeam = {
                                navigateTo(AppNavStep.OfficialTeamStep)
                            },
                            onOpenFriends = {
                                navigateTo(AppNavStep.FriendsStep(userId))
                            },
                            onOpenSupport = {
                                navigateTo(AppNavStep.SupportStep)
                            }
                        )
                        MainTab.ME -> MeScreen(
                            userEmail = userEmail,
                            userId = userId,
                            userName = UserSessionManager.getSession(context)?.name?.takeIf { it.isNotBlank() && it != "User" && it != "QIVO User" }
                                ?: (currentProfileState?.name ?: userName),
                            userGender = currentProfileState?.gender ?: userGender,
                            userCountry = currentProfileState?.country ?: userCountry,
                            userAvatarUrl = UserSessionManager.getSession(context)?.avatarUrl?.takeIf { it.isNotBlank() }
                                ?: (currentProfileState?.avatarUrl?.takeIf { it.isNotBlank() } ?: userAvatarUrl),
                            userNumericId = currentProfileState?.numericId ?: userNumericId,
                            userBirthDate = currentProfileState?.birthDate ?: "",
                            onOpenEditProfile = { navigateTo(AppNavStep.EditProfileStep) },
                            onOpenUserDetail = { prof -> navigateTo(AppNavStep.UserDetailStep(prof)) },
                            onOpenLevel = { navigateTo(AppNavStep.LevelStep) },
                            onOpenTaskCenter = { navigateTo(AppNavStep.TaskCenterStep) },
                            onOpenMessageBlast = { navigateTo(AppNavStep.MessageBlastStep) },
                            onOpenStore = { navigateTo(AppNavStep.StoreStep) },
                            onOpenBag = { navigateTo(AppNavStep.BagStep) },
                            onOpenSupport = { navigateTo(AppNavStep.SupportStep) },
                            onOpenVerify = { navigateTo(AppNavStep.VerifyStep) },
                            onOpenSettings = { navigateTo(AppNavStep.SettingsStep) },
                            onOpenWallet = { navigateTo(AppNavStep.WalletRechargeStep) },
                            onOpenAwardCoins = { navigateTo(AppNavStep.AwardCoinsStep) },
                            onOpenManageRoles = { navigateTo(AppNavStep.ManageRolesStep) },
                            onOpenManageReports = { navigateTo(AppNavStep.ManageReportsStep) },
                            onOpenManageAds = { navigateTo(AppNavStep.ManageAdsStep) },
                            onOpenAdminAnalytics = { navigateTo(AppNavStep.AdminAnalyticsStep) },
                            onOpenAgency = { navigateTo(AppNavStep.AgencyCenterStep()) },
                            onOpenIncome = { navigateTo(AppNavStep.IncomeConversionStep) },
                            onOpenFollows = { tab ->
                                when (tab) {
                                    FollowTab.FRIENDS -> navigateTo(AppNavStep.FriendsStep(userId))
                                    FollowTab.FOLLOWING -> navigateTo(AppNavStep.FollowingStep(userId))
                                    FollowTab.FOLLOWERS -> navigateTo(AppNavStep.FollowersStep(userId))
                                    FollowTab.VISITORS -> navigateTo(AppNavStep.VisitorsStep(userId))
                                }
                            },
                            onOpenFriends = { navigateTo(AppNavStep.FriendsStep(userId)) },
                            onOpenFollowing = { navigateTo(AppNavStep.FollowingStep(userId)) },
                            onOpenFollowers = { navigateTo(AppNavStep.FollowersStep(userId)) },
                            onOpenVisitors = { navigateTo(AppNavStep.VisitorsStep(userId)) },
                            onSignOut = handleSignOut
                        )
                    }
                }
            }
        }
    }
}

    // Bottom Navigation Bar (Hidden when inside full screen overlays)
    if (!isFullScreenOverlay) {
        val colors = com.example.ui.theme.AppTheme.colors
        Surface(
            modifier = Modifier
                .align(Alignment.BottomCenter)
                .fillMaxWidth()
                .testTag("qivo_bottom_navigation_bar"),
            shape = RoundedCornerShape(topStart = 0.dp, topEnd = 0.dp),
            color = Color(0xFF381A05), // Matches warm espresso screen background perfectly
            border = null, // No border lines on bottom navigation bar
            shadowElevation = 0.dp, // Flat seamless look with zero border lines
            tonalElevation = 0.dp
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .navigationBarsPadding()
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(60.dp)
                        .padding(horizontal = 6.dp, vertical = 2.dp),
                    horizontalArrangement = Arrangement.SpaceAround,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    NavTabItem(
                        label = "Home",
                        icon3D = { Home3DNavIcon(size = 24.dp, isSelected = activeTab == MainTab.HOME) },
                        isSelected = activeTab == MainTab.HOME,
                        onClick = { selectTab(MainTab.HOME) },
                        testTag = "nav_tab_home",
                        isDark = true,
                        textColor = Color.White,
                        unselectedColor = Color(0xFFA0A5BA)
                    )

                    NavTabItem(
                        label = "Party",
                        icon3D = { Party3DNavIcon(size = 24.dp, isSelected = activeTab == MainTab.PARTY) },
                        isSelected = activeTab == MainTab.PARTY,
                        onClick = { selectTab(MainTab.PARTY) },
                        testTag = "nav_tab_party",
                        isDark = true,
                        textColor = Color.White,
                        unselectedColor = Color(0xFFA0A5BA)
                    )

                    NavTabItem(
                        label = "Chat",
                        icon3D = { Chat3DNavIcon(size = 24.dp, isSelected = activeTab == MainTab.CHAT) },
                        isSelected = activeTab == MainTab.CHAT,
                        onClick = { selectTab(MainTab.CHAT) },
                        testTag = "nav_tab_chat",
                        isDark = true,
                        textColor = Color.White,
                        unselectedColor = Color(0xFFA0A5BA),
                        badgeCount = 0
                    )

                    NavTabItem(
                        label = "Me",
                        icon3D = { Me3DNavIcon(size = 24.dp, isSelected = activeTab == MainTab.ME) },
                        isSelected = activeTab == MainTab.ME,
                        onClick = { selectTab(MainTab.ME) },
                        testTag = "nav_tab_me",
                        isDark = true,
                        textColor = Color.White,
                        unselectedColor = Color(0xFFA0A5BA)
                    )
                }
            }
        }
    }

        // Full-Screen Promotional App-Open Announcement / Ad (Only shown if active)
        if (openAd != null && !hasDismissedOpenAd) {
            AppOpenAdPopup(
                ad = openAd,
                onDismiss = {
                    openAd = null
                    hasDismissedOpenAd = true
                },
                onNavigateAction = { targetLink ->
                    openAd = null
                    hasDismissedOpenAd = true
                    when {
                        targetLink.contains("party", ignoreCase = true) -> selectTab(MainTab.PARTY)
                        targetLink.contains("wallet", ignoreCase = true) -> navigateTo(AppNavStep.WalletRechargeStep)
                        else -> {}
                    }
                }
            )
        }

        // Floating Party Room PIP Mini Bubble (freely movable, persists active voice & room across app)
        FloatingPartyRoomBubble(
            currentUserId = userId,
            onExpandRoom = {
                val active = PartyRoomSessionManager.activeRoom.value
                if (active != null) {
                    val current = navStack.lastOrNull()
                    if (current !is AppNavStep.PartyRoomDetailStep || current.room.id != active.id) {
                        navigateTo(AppNavStep.PartyRoomDetailStep(active))
                    }
                }
            }
        )

        // Universal Realtime Calling Overlay & Minimized Floating Call Bubble (Across all screens)
        GlobalCallOverlay(
            currentUserId = userId,
            onOpenRechargeWallet = {
                navigateTo(AppNavStep.WalletRechargeStep)
            }
        )

        // Floating In-App Heads-Up Notification Banner (Matches custom in-app popup design)
        val currentInAppNotification by InAppNotificationManager.currentNotification.collectAsState()
        InAppNotificationBanner(
            notification = currentInAppNotification,
            onBannerClick = { notif ->
                InAppNotificationManager.dismiss()
                scope.launch {
                    val target = notif.targetUser ?: profileService.fetchProfileById(notif.senderId) ?: UserProfile(
                        id = notif.senderId,
                        numericId = 0L,
                        email = "",
                        name = notif.senderName,
                        gender = "Female",
                        birthDate = "2000-01-01",
                        country = "Kenya",
                        avatarUrl = notif.senderAvatar
                    )
                    val existing = navStack.lastOrNull()
                    if (existing !is AppNavStep.ConversationStep || existing.user.id != target.id) {
                        navigateTo(AppNavStep.ConversationStep(target))
                    }
                }
            },
            onReplyClick = { notif ->
                InAppNotificationManager.dismiss()
                scope.launch {
                    val target = notif.targetUser ?: profileService.fetchProfileById(notif.senderId) ?: UserProfile(
                        id = notif.senderId,
                        numericId = 0L,
                        email = "",
                        name = notif.senderName,
                        gender = "Female",
                        birthDate = "2000-01-01",
                        country = "Kenya",
                        avatarUrl = notif.senderAvatar
                    )
                    val existing = navStack.lastOrNull()
                    if (existing !is AppNavStep.ConversationStep || existing.user.id != target.id) {
                        navigateTo(AppNavStep.ConversationStep(target))
                    }
                }
            },
            onDismiss = {
                InAppNotificationManager.dismiss()
            },
            modifier = Modifier
                .align(Alignment.TopCenter)
                .statusBarsPadding()
        )

        if (showExitConfirmationDialog) {
            AlertDialog(
                onDismissRequest = { showExitConfirmationDialog = false },
                title = {
                    Text(
                        text = "Exit App",
                        fontWeight = FontWeight.Bold,
                        fontSize = 20.sp
                    )
                },
                text = {
                    Text(
                        text = "Are you sure you want to exit the app?",
                        fontSize = 15.sp
                    )
                },
                confirmButton = {
                    TextButton(
                        onClick = {
                            showExitConfirmationDialog = false
                            (context as? android.app.Activity)?.finish()
                        }
                    ) {
                        Text(
                            text = "Exit",
                            color = MaterialTheme.colorScheme.error,
                            fontWeight = FontWeight.Bold
                        )
                    }
                },
                dismissButton = {
                    TextButton(
                        onClick = { showExitConfirmationDialog = false }
                    ) {
                        Text(
                            text = "Cancel",
                            fontWeight = FontWeight.SemiBold
                        )
                    }
                },
                shape = RoundedCornerShape(20.dp),
                containerColor = MaterialTheme.colorScheme.surface,
                titleContentColor = MaterialTheme.colorScheme.onSurface,
                textContentColor = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }

        if (showPasswordPromptForRoom != null) {
            val roomToEnter = showPasswordPromptForRoom!!
            var isVerifyingPassword by remember { mutableStateOf(false) }
            var isPasswordVisible by remember { mutableStateOf(false) }

            Dialog(
                onDismissRequest = {
                    if (!isVerifyingPassword) {
                        showPasswordPromptForRoom = null
                        passwordInputState = ""
                        passwordPromptError = ""
                    }
                }
            ) {
                Card(
                    shape = RoundedCornerShape(28.dp),
                    colors = CardDefaults.cardColors(containerColor = Color(0xFF181326)),
                    border = BorderStroke(
                        1.5.dp,
                        Brush.linearGradient(
                            listOf(
                                Color(0xFF8B5CF6),
                                Color(0xFFFF6500),
                                Color(0xFFEC4899)
                            )
                        )
                    ),
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 8.dp)
                ) {
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(22.dp),
                        horizontalAlignment = Alignment.CenterHorizontally
                    ) {
                        // Header with glowing Lock Badge & Close
                        Box(
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Box(
                                modifier = Modifier
                                    .align(Alignment.Center)
                                    .size(56.dp)
                                    .clip(CircleShape)
                                    .background(
                                        Brush.radialGradient(
                                            listOf(
                                                Color(0xFFFF6500).copy(alpha = 0.35f),
                                                Color(0xFF8B5CF6).copy(alpha = 0.15f),
                                                Color.Transparent
                                            )
                                        )
                                    )
                                    .border(
                                        1.5.dp,
                                        Brush.linearGradient(
                                            listOf(Color(0xFFFF8D00), Color(0xFF7C4DFF))
                                        ),
                                        CircleShape
                                    ),
                                contentAlignment = Alignment.Center
                            ) {
                                Icon(
                                    imageVector = Icons.Default.Lock,
                                    contentDescription = "Lock",
                                    tint = Color(0xFFFFD54F),
                                    modifier = Modifier.size(26.dp)
                                )
                            }

                            IconButton(
                                onClick = {
                                    if (!isVerifyingPassword) {
                                        showPasswordPromptForRoom = null
                                        passwordInputState = ""
                                        passwordPromptError = ""
                                    }
                                },
                                modifier = Modifier
                                    .align(Alignment.TopEnd)
                                    .size(32.dp)
                            ) {
                                Icon(
                                    imageVector = Icons.Default.Close,
                                    contentDescription = "Close",
                                    tint = Color.White.copy(alpha = 0.6f),
                                    modifier = Modifier.size(18.dp)
                                )
                            }
                        }

                        Spacer(modifier = Modifier.height(12.dp))

                        Text(
                            text = "Private Party Room",
                            fontSize = 19.sp,
                            fontWeight = FontWeight.Bold,
                            color = Color.White
                        )

                        Spacer(modifier = Modifier.height(3.dp))

                        Text(
                            text = "This room requires a 6-digit access PIN",
                            fontSize = 12.sp,
                            color = Color.White.copy(alpha = 0.65f),
                            textAlign = TextAlign.Center
                        )

                        Spacer(modifier = Modifier.height(14.dp))

                        // Room Info Mini Banner
                        Surface(
                            shape = RoundedCornerShape(16.dp),
                            color = Color(0xFF231B38),
                            border = BorderStroke(1.dp, Color(0x33FFFFFF)),
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(horizontal = 12.dp, vertical = 10.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                val coverUrl = roomToEnter.coverUrl.ifBlank { roomToEnter.hostAvatarUrl }
                                if (coverUrl.isNotBlank()) {
                                    AsyncImage(
                                        model = coverUrl,
                                        contentDescription = "Room Cover",
                                        modifier = Modifier
                                            .size(40.dp)
                                            .clip(RoundedCornerShape(10.dp)),
                                        contentScale = androidx.compose.ui.layout.ContentScale.Crop
                                    )
                                } else {
                                    Box(
                                        modifier = Modifier
                                            .size(40.dp)
                                            .clip(RoundedCornerShape(10.dp))
                                            .background(
                                                Brush.linearGradient(
                                                    listOf(Color(0xFF8B5CF6), Color(0xFFFF6500))
                                                )
                                            ),
                                        contentAlignment = Alignment.Center
                                    ) {
                                        Icon(
                                            Icons.Default.Group,
                                            contentDescription = null,
                                            tint = Color.White,
                                            modifier = Modifier.size(20.dp)
                                        )
                                    }
                                }

                                Spacer(modifier = Modifier.width(10.dp))

                                Column(modifier = Modifier.weight(1f)) {
                                    Text(
                                        text = roomToEnter.name,
                                        fontSize = 14.sp,
                                        fontWeight = FontWeight.Bold,
                                        color = Color.White,
                                        maxLines = 1,
                                        overflow = TextOverflow.Ellipsis
                                    )
                                    Text(
                                        text = "Host: ${roomToEnter.hostName.ifBlank { "Host" }}",
                                        fontSize = 11.sp,
                                        color = Color(0xFFFFD54F),
                                        maxLines = 1
                                    )
                                }

                                Box(
                                    modifier = Modifier
                                        .clip(RoundedCornerShape(8.dp))
                                        .background(Color(0x33FF6500))
                                        .border(1.dp, Color(0x88FF6500), RoundedCornerShape(8.dp))
                                        .padding(horizontal = 8.dp, vertical = 4.dp)
                                ) {
                                    Text(
                                        text = "Locked 🔒",
                                        fontSize = 10.sp,
                                        fontWeight = FontWeight.Bold,
                                        color = Color(0xFFFF8D00)
                                    )
                                }
                            }
                        }

                        Spacer(modifier = Modifier.height(18.dp))

                        // 6-Digit Visual PIN Boxes
                        Row(
                            horizontalArrangement = Arrangement.spacedBy(6.dp),
                            modifier = Modifier.padding(horizontal = 2.dp)
                        ) {
                            for (i in 0 until 6) {
                                val isFilled = i < passwordInputState.length
                                val isCurrent = i == passwordInputState.length
                                val digitChar = if (isFilled) passwordInputState[i] else null

                                Box(
                                    modifier = Modifier
                                        .size(width = 38.dp, height = 46.dp)
                                        .clip(RoundedCornerShape(10.dp))
                                        .background(
                                            if (isFilled) Color(0xFF2C2245) else Color(0xFF1E1730)
                                        )
                                        .border(
                                            width = if (isCurrent) 2.dp else 1.dp,
                                            color = when {
                                                passwordPromptError.isNotBlank() -> Color(0xFFFF5252)
                                                isCurrent -> Color(0xFFFF8D00)
                                                isFilled -> Color(0xFF8B5CF6)
                                                else -> Color(0x33FFFFFF)
                                            },
                                            shape = RoundedCornerShape(10.dp)
                                        ),
                                    contentAlignment = Alignment.Center
                                ) {
                                    if (isFilled) {
                                        if (isPasswordVisible) {
                                            Text(
                                                text = digitChar.toString(),
                                                fontSize = 18.sp,
                                                fontWeight = FontWeight.ExtraBold,
                                                color = Color.White
                                            )
                                        } else {
                                            Box(
                                                modifier = Modifier
                                                    .size(10.dp)
                                                    .clip(CircleShape)
                                                    .background(Color(0xFFFFD54F))
                                            )
                                        }
                                    } else if (isCurrent) {
                                        Box(
                                            modifier = Modifier
                                                .width(2.dp)
                                                .height(18.dp)
                                                .background(Color(0xFFFF8D00))
                                        )
                                    }
                                }
                            }
                        }

                        Spacer(modifier = Modifier.height(12.dp))

                        // Input capture field with visibility toggle
                        androidx.compose.material3.OutlinedTextField(
                            value = passwordInputState,
                            onValueChange = {
                                if (it.length <= 6 && it.all { char -> char.isDigit() }) {
                                    passwordInputState = it
                                    passwordPromptError = ""
                                    if (it.length == 6 && !isVerifyingPassword) {
                                        isVerifyingPassword = true
                                        scope.launch {
                                            val (success, errorMsg) = partyService.verifyAndJoinPartyRoom(
                                                roomId = roomToEnter.id,
                                                enteredPassword = it,
                                                userId = userId,
                                                userName = currentProfileState?.name ?: userName,
                                                avatarUrl = currentProfileState?.avatarUrl ?: userAvatarUrl,
                                                context = context
                                            )
                                            if (success) {
                                                val fresh = partyService.fetchPartyRoomById(roomToEnter.id) ?: roomToEnter
                                                PartyRoomSessionManager.enterRoom(fresh, context)
                                                showPasswordPromptForRoom = null
                                                passwordInputState = ""
                                                passwordPromptError = ""
                                                navigateTo(AppNavStep.PartyRoomDetailStep(fresh))
                                            } else {
                                                passwordPromptError = if (errorMsg.isNotBlank()) errorMsg else "Incorrect password! Please try again."
                                            }
                                            isVerifyingPassword = false
                                        }
                                    }
                                }
                            },
                            singleLine = true,
                            textStyle = androidx.compose.ui.text.TextStyle(
                                textAlign = TextAlign.Center,
                                fontWeight = FontWeight.Bold,
                                fontSize = 15.sp,
                                letterSpacing = 6.sp,
                                color = Color.White
                            ),
                            placeholder = {
                                Text(
                                    "Tap to type 6-digit PIN",
                                    modifier = Modifier.fillMaxWidth(),
                                    textAlign = TextAlign.Center,
                                    fontSize = 12.sp,
                                    color = Color.White.copy(alpha = 0.35f)
                                )
                            },
                            keyboardOptions = androidx.compose.foundation.text.KeyboardOptions(
                                keyboardType = androidx.compose.ui.text.input.KeyboardType.NumberPassword,
                                imeAction = androidx.compose.ui.text.input.ImeAction.Done
                            ),
                            keyboardActions = androidx.compose.foundation.text.KeyboardActions(
                                onDone = {
                                    if (passwordInputState.length == 6 && !isVerifyingPassword) {
                                        isVerifyingPassword = true
                                        scope.launch {
                                            val (success, errorMsg) = partyService.verifyAndJoinPartyRoom(
                                                roomId = roomToEnter.id,
                                                enteredPassword = passwordInputState,
                                                userId = userId,
                                                userName = currentProfileState?.name ?: userName,
                                                avatarUrl = currentProfileState?.avatarUrl ?: userAvatarUrl,
                                                context = context
                                            )
                                            if (success) {
                                                val fresh = partyService.fetchPartyRoomById(roomToEnter.id) ?: roomToEnter
                                                PartyRoomSessionManager.enterRoom(fresh, context)
                                                showPasswordPromptForRoom = null
                                                passwordInputState = ""
                                                passwordPromptError = ""
                                                navigateTo(AppNavStep.PartyRoomDetailStep(fresh))
                                            } else {
                                                passwordPromptError = if (errorMsg.isNotBlank()) errorMsg else "Incorrect password! Please try again."
                                            }
                                            isVerifyingPassword = false
                                        }
                                    }
                                }
                            ),
                            trailingIcon = {
                                IconButton(onClick = { isPasswordVisible = !isPasswordVisible }) {
                                    Icon(
                                        imageVector = if (isPasswordVisible) Icons.Default.VisibilityOff else Icons.Default.Visibility,
                                        contentDescription = "Toggle password visibility",
                                        tint = Color.White.copy(alpha = 0.6f),
                                        modifier = Modifier.size(18.dp)
                                    )
                                }
                            },
                            visualTransformation = if (isPasswordVisible) androidx.compose.ui.text.input.VisualTransformation.None else androidx.compose.ui.text.input.PasswordVisualTransformation(),
                            colors = androidx.compose.material3.OutlinedTextFieldDefaults.colors(
                                focusedTextColor = Color.White,
                                unfocusedTextColor = Color.White,
                                focusedBorderColor = Color(0xFFFF6500),
                                unfocusedBorderColor = Color(0x44FFFFFF),
                                focusedContainerColor = Color(0xFF1E1730),
                                unfocusedContainerColor = Color(0xFF1E1730)
                            ),
                            shape = RoundedCornerShape(12.dp),
                            modifier = Modifier
                                .fillMaxWidth()
                                .testTag("party_room_password_input")
                        )

                        if (passwordPromptError.isNotBlank()) {
                            Spacer(modifier = Modifier.height(10.dp))
                            Surface(
                                shape = RoundedCornerShape(8.dp),
                                color = Color(0x33FF5252),
                                border = BorderStroke(1.dp, Color(0x66FF5252)),
                                modifier = Modifier.fillMaxWidth()
                            ) {
                                Text(
                                    text = passwordPromptError,
                                    color = Color(0xFFFF8A80),
                                    fontSize = 12.sp,
                                    fontWeight = FontWeight.SemiBold,
                                    textAlign = TextAlign.Center,
                                    modifier = Modifier.padding(horizontal = 12.dp, vertical = 6.dp)
                                )
                            }
                        }

                        Spacer(modifier = Modifier.height(18.dp))

                        // Action Buttons: Unlock / Cancel
                        Button(
                            onClick = {
                                if (passwordInputState.length == 6 && !isVerifyingPassword) {
                                    isVerifyingPassword = true
                                    scope.launch {
                                        val (success, errorMsg) = partyService.verifyAndJoinPartyRoom(
                                            roomId = roomToEnter.id,
                                            enteredPassword = passwordInputState,
                                            userId = userId,
                                            userName = currentProfileState?.name ?: userName,
                                            avatarUrl = currentProfileState?.avatarUrl ?: userAvatarUrl,
                                            context = context
                                        )
                                        if (success) {
                                            val fresh = partyService.fetchPartyRoomById(roomToEnter.id) ?: roomToEnter
                                            PartyRoomSessionManager.enterRoom(fresh, context)
                                            showPasswordPromptForRoom = null
                                            passwordInputState = ""
                                            passwordPromptError = ""
                                            navigateTo(AppNavStep.PartyRoomDetailStep(fresh))
                                        } else {
                                            passwordPromptError = if (errorMsg.isNotBlank()) errorMsg else "Incorrect password! Please try again."
                                        }
                                        isVerifyingPassword = false
                                    }
                                }
                            },
                            enabled = passwordInputState.length == 6 && !isVerifyingPassword,
                            colors = ButtonDefaults.buttonColors(
                                containerColor = Color(0xFFFF6500),
                                disabledContainerColor = Color(0x33FF6500)
                            ),
                            shape = RoundedCornerShape(14.dp),
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(48.dp)
                        ) {
                            if (isVerifyingPassword) {
                                CircularProgressIndicator(
                                    color = Color.White,
                                    strokeWidth = 2.dp,
                                    modifier = Modifier.size(18.dp)
                                )
                                Spacer(modifier = Modifier.width(8.dp))
                                Text("Verifying PIN...", fontWeight = FontWeight.Bold, fontSize = 13.sp)
                            } else {
                                Icon(
                                    Icons.Default.Key,
                                    contentDescription = null,
                                    modifier = Modifier.size(16.dp)
                                )
                                Spacer(modifier = Modifier.width(8.dp))
                                Text("Unlock & Enter", fontWeight = FontWeight.Bold, fontSize = 13.sp)
                            }
                        }

                        Spacer(modifier = Modifier.height(6.dp))

                        TextButton(
                            onClick = {
                                if (!isVerifyingPassword) {
                                    showPasswordPromptForRoom = null
                                    passwordInputState = ""
                                    passwordPromptError = ""
                                }
                            },
                            enabled = !isVerifyingPassword,
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Text(
                                "Cancel",
                                color = Color.White.copy(alpha = 0.65f),
                                fontWeight = FontWeight.SemiBold,
                                fontSize = 12.sp
                            )
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun NavTabItem(
    label: String,
    icon3D: @Composable () -> Unit,
    isSelected: Boolean,
    onClick: () -> Unit,
    testTag: String,
    isDark: Boolean = false,
    textColor: Color = Color.Black,
    unselectedColor: Color = Color(0xFF8E8E93),
    badgeCount: Int = 0
) {
    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center,
        modifier = Modifier
            .clickable { onClick() }
            .padding(vertical = 2.dp, horizontal = 6.dp)
            .testTag(testTag)
    ) {
        Box(
            modifier = Modifier
                .width(52.dp)
                .height(28.dp),
            contentAlignment = Alignment.Center
        ) {
            // Selected pill background (only clips the pill container, not child badge)
            if (isSelected) {
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .clip(RoundedCornerShape(14.dp))
                        .background(Color.White.copy(alpha = 0.12f))
                )
            }

            icon3D()

            if (badgeCount > 0) {
                Surface(
                    shape = RoundedCornerShape(10.dp),
                    color = Color(0xFFFF3B30),
                    modifier = Modifier
                        .align(Alignment.TopEnd)
                        .offset(x = 4.dp, y = (-3).dp)
                        .testTag("${testTag}_badge")
                ) {
                    Text(
                        text = if (badgeCount > 99) "99+" else badgeCount.toString(),
                        color = Color.White,
                        fontSize = 9.sp,
                        fontWeight = FontWeight.Bold,
                        lineHeight = 10.sp,
                        textAlign = androidx.compose.ui.text.style.TextAlign.Center,
                        modifier = Modifier.padding(horizontal = 4.dp, vertical = 1.dp)
                    )
                }
            }
        }

        Spacer(modifier = Modifier.height(2.dp))

        Text(
            text = label,
            fontSize = 10.sp,
            fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Medium,
            color = if (isSelected) (if (isDark) Color.White else Color.Black) else unselectedColor
        )
    }
}

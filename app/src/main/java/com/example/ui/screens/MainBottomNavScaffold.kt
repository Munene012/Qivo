package com.example.ui.screens

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
import androidx.compose.material3.Icon
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
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
    data object AgencyCenterStep : AppNavStep()
    data object IncomeConversionStep : AppNavStep()
    data object StoreStep : AppNavStep()
    data object BagStep : AppNavStep()
    data object LevelStep : AppNavStep()
    data object GameCenterStep : AppNavStep()
    data object AboutQivoStep : AppNavStep()
    data object AccountSecurityStep : AppNavStep()
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
                        // Silent when stable
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

    // Always ensure cold start begins at the top of the Home screen
    LaunchedEffect(Unit) {
        com.example.ui.screens.HomeScreenDataStore.resetScrollToTop()
        try {
            homeListState.scrollToItem(0, 0)
        } catch (_: Exception) {}
    }

    // Initialize Active Call Engine & Realtime Signaling
    LaunchedEffect(userId) {
        if (userId.isNotBlank()) {
            ActiveCallSessionManager.init(context, userId)
        }
    }

    // Refresh profile on launch & continuously maintain active realtime presence when online
    LaunchedEffect(userId, userEmail, isOnline) {
        val session = UserSessionManager.getSession(context)
        val token = session?.accessToken
        if (isOnline && (userId.isNotEmpty() || userEmail.isNotEmpty())) {
            val fetched = profileService.fetchProfile(userId, userEmail, token)
            if (fetched != null) {
                val resolvedName = when {
                    fetched.name.isNotBlank() && fetched.name != "QIVO User" -> fetched.name
                    currentProfileState.name.isNotBlank() && currentProfileState.name != "QIVO User" -> currentProfileState.name
                    !session?.name.isNullOrBlank() && session?.name != "QIVO User" -> session.name
                    else -> fetched.name.ifBlank { userName }
                }
                val resolvedGender = when {
                    fetched.gender.equals("female", ignoreCase = true) ||
                    fetched.gender.equals("f", ignoreCase = true) ||
                    fetched.gender.equals("woman", ignoreCase = true) ||
                    fetched.gender.equals("w", ignoreCase = true) -> "Female"

                    fetched.gender.equals("male", ignoreCase = true) ||
                    fetched.gender.equals("m", ignoreCase = true) ||
                    fetched.gender.equals("man", ignoreCase = true) -> "Male"

                    userGender.equals("female", ignoreCase = true) ||
                    userGender.equals("f", ignoreCase = true) ||
                    userGender.equals("woman", ignoreCase = true) ||
                    userGender.equals("w", ignoreCase = true) -> "Female"

                    userGender.equals("male", ignoreCase = true) ||
                    userGender.equals("m", ignoreCase = true) ||
                    userGender.equals("man", ignoreCase = true) -> "Male"

                    currentProfileState.gender.equals("female", ignoreCase = true) ||
                    currentProfileState.gender.equals("f", ignoreCase = true) ||
                    currentProfileState.gender.equals("woman", ignoreCase = true) ||
                    currentProfileState.gender.equals("w", ignoreCase = true) -> "Female"

                    currentProfileState.gender.equals("male", ignoreCase = true) ||
                    currentProfileState.gender.equals("m", ignoreCase = true) ||
                    currentProfileState.gender.equals("man", ignoreCase = true) -> "Male"

                    session?.gender.equals("female", ignoreCase = true) ||
                    session?.gender.equals("f", ignoreCase = true) ||
                    session?.gender.equals("woman", ignoreCase = true) ||
                    session?.gender.equals("w", ignoreCase = true) -> "Female"

                    session?.gender.equals("male", ignoreCase = true) ||
                    session?.gender.equals("m", ignoreCase = true) ||
                    session?.gender.equals("man", ignoreCase = true) -> "Male"

                    else -> ""
                }
                val resolvedCountry = when {
                    fetched.country.isNotBlank() -> fetched.country
                    currentProfileState.country.isNotBlank() -> currentProfileState.country
                    !session?.country.isNullOrBlank() -> session.country
                    else -> userCountry
                }
                val merged = fetched.copy(
                    name = resolvedName,
                    gender = resolvedGender,
                    country = resolvedCountry,
                    avatarUrl = if (fetched.avatarUrl.isNotEmpty()) fetched.avatarUrl else currentProfileState.avatarUrl
                )
                currentProfileState = merged
                UserSessionManager.saveRoles(context, merged.isAdmin, merged.isCoinSeller, merged.isAgent)
                UserSessionManager.saveSession(
                    context = context,
                    email = merged.email,
                    userId = merged.id,
                    name = merged.name,
                    gender = merged.gender,
                    country = merged.country,
                    avatarUrl = merged.avatarUrl,
                    numericId = merged.numericId,
                    coins = merged.coins,
                    isAdmin = merged.isAdmin,
                    isCoinSeller = merged.isCoinSeller,
                    isAgent = merged.isAgent,
                    accessToken = token
                )
            }
        }
        if (isOnline && userId.isNotBlank()) {
            profileService.updateOnlineStatus(userId, true)
            while (isOnline) {
                kotlinx.coroutines.delay(30_000L) // Ping every 30s
                profileService.updateOnlineStatus(userId, true)
            }
        }
    }

    // Live periodic unread messages count sync & in-app notification detection
    val knownMessageIds = remember { mutableSetOf<Long>() }
    var isMessageSyncInitialized by remember { mutableStateOf(false) }

    LaunchedEffect(userId, isOnline) {
        if (isOnline && userId.isNotBlank()) {
            while (isOnline) {
                try {
                    val messages = chatService.fetchUserMessages(userId, context, offset = 0, limit = 30)
                    val unreadIncoming = messages.filter {
                        !it.isRead && it.receiverId.trim().equals(userId.trim(), ignoreCase = true)
                    }

                    if (isMessageSyncInitialized) {
                        for (msg in unreadIncoming) {
                            if (msg.id > 0L && !knownMessageIds.contains(msg.id)) {
                                knownMessageIds.add(msg.id)

                                // Trigger in-app floating heads-up banner if not currently chatting with sender
                                if (SupabaseFcmService.activeConversationUserId != msg.senderId) {
                                    val timeFmt = if (msg.createdAt.isNotBlank()) {
                                        try {
                                            val millis = chatService.parseTimestampToMillis(msg.createdAt)
                                            if (millis > 0L) {
                                                java.text.SimpleDateFormat("HH:mm", java.util.Locale.getDefault()).format(java.util.Date(millis))
                                            } else {
                                                ""
                                            }
                                        } catch (_: Throwable) {
                                            ""
                                        }
                                    } else ""

                                    InAppNotificationManager.showNotification(
                                        senderId = msg.senderId,
                                        senderName = msg.senderName,
                                        senderAvatar = msg.senderAvatar,
                                        messageText = msg.message,
                                        timeFormatted = timeFmt
                                    )
                                }
                            }
                        }
                    } else {
                        // Populate existing IDs on first sync to avoid alert spam on boot
                        for (msg in messages) {
                            if (msg.id > 0L) {
                                knownMessageIds.add(msg.id)
                            }
                        }
                        isMessageSyncInitialized = true
                    }
                } catch (_: Exception) {}
                kotlinx.coroutines.delay(3_500L)
            }
        }
    }

    // Refresh triggers for bottom nav double-clicks / re-selections
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

    fun selectTab(tab: MainTab) {
        val current = navStack.lastOrNull()
        if (current is AppNavStep.TabStep && current.tab == tab) {
            if (tab == MainTab.HOME) {
                homeRefreshTrigger = System.currentTimeMillis()
                scope.launch {
                    try {
                        homeListState.animateScrollToItem(0)
                    } catch (_: Exception) {}
                }
            } else if (tab == MainTab.CHAT) {
                chatRefreshTrigger = System.currentTimeMillis()
            }
            return
        }
        navStack.clear()
        navStack.add(AppNavStep.TabStep(tab))
    }

    // Back button handling: pops navigation stack
    BackHandler(enabled = navStack.size > 1) {
        navigateBack()
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
                        }
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
                        onBackClick = { navigateBack() }
                    )
                }
                is AppNavStep.IncomeConversionStep -> {
                    IncomeScreen(
                        userId = userId,
                        onBackClick = { navigateBack() },
                        onCoinsUpdated = { newCoins ->
                            currentProfileState = currentProfileState.copy(coins = newCoins)
                        }
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
                            onOpenPartyRoom = { room ->
                                navigateTo(AppNavStep.PartyRoomDetailStep(room))
                            },
                            onOpenCreateRoom = {
                                navigateTo(AppNavStep.CreatePartyRoomStep)
                            }
                        )
                        MainTab.CHAT -> ChatScreen(
                            refreshTrigger = chatRefreshTrigger,
                            onOpenUserDetail = { user ->
                                navigateTo(AppNavStep.UserDetailStep(user))
                            },
                            onOpenConversation = { user ->
                                navigateTo(AppNavStep.ConversationStep(user))
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
                            onOpenAgency = { navigateTo(AppNavStep.AgencyCenterStep) },
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
            shape = RoundedCornerShape(topStart = 18.dp, topEnd = 18.dp),
            color = colors.bottomNavBg,
            shadowElevation = 0.dp,
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
                        isDark = colors.isDark,
                        textColor = colors.textPrimary,
                        unselectedColor = colors.textSecondary
                    )

                    NavTabItem(
                        label = "Party",
                        icon3D = { Party3DNavIcon(size = 24.dp, isSelected = activeTab == MainTab.PARTY) },
                        isSelected = activeTab == MainTab.PARTY,
                        onClick = { selectTab(MainTab.PARTY) },
                        testTag = "nav_tab_party",
                        isDark = colors.isDark,
                        textColor = colors.textPrimary,
                        unselectedColor = colors.textSecondary
                    )

                    NavTabItem(
                        label = "Chat",
                        icon3D = { Chat3DNavIcon(size = 24.dp, isSelected = activeTab == MainTab.CHAT) },
                        isSelected = activeTab == MainTab.CHAT,
                        onClick = { selectTab(MainTab.CHAT) },
                        testTag = "nav_tab_chat",
                        isDark = colors.isDark,
                        textColor = colors.textPrimary,
                        unselectedColor = colors.textSecondary,
                        badgeCount = totalUnreadCount
                    )

                    NavTabItem(
                        label = "Me",
                        icon3D = { Me3DNavIcon(size = 24.dp, isSelected = activeTab == MainTab.ME) },
                        isSelected = activeTab == MainTab.ME,
                        onClick = { selectTab(MainTab.ME) },
                        testTag = "nav_tab_me",
                        isDark = colors.isDark,
                        textColor = colors.textPrimary,
                        unselectedColor = colors.textSecondary
                    )
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
                        .background(QivoYellow.copy(alpha = 0.25f))
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

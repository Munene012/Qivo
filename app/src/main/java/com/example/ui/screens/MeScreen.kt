package com.example.ui.screens
import com.example.ui.components.AppToast

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.widget.Toast
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.ui.draw.shadow
import androidx.compose.foundation.BorderStroke
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
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowForwardIos
import androidx.compose.material.icons.filled.AccountBalanceWallet
import androidx.compose.material.icons.filled.AdminPanelSettings
import androidx.compose.material.icons.filled.Analytics
import androidx.compose.material.icons.filled.Assignment
import androidx.compose.material.icons.filled.Campaign
import androidx.compose.material.icons.filled.ChevronRight
import androidx.compose.material.icons.filled.ContentCopy
import androidx.compose.material.icons.filled.EmojiEvents
import androidx.compose.material.icons.filled.Female
import androidx.compose.material.icons.filled.Flag
import androidx.compose.material.icons.filled.Headset
import androidx.compose.material.icons.filled.Male
import androidx.compose.material.icons.filled.Send
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.Shield
import androidx.compose.material.icons.filled.ShoppingBag
import androidx.compose.material.icons.filled.Store
import androidx.compose.material.icons.filled.SupervisorAccount
import androidx.compose.material.icons.filled.Verified
import androidx.compose.material.icons.filled.Visibility
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import com.example.ui.components.AdminAnalytics3DIcon
import com.example.ui.components.AdminShield3DIcon
import com.example.ui.components.Agency3DIcon
import com.example.ui.components.Aristocracy3DIcon
import com.example.ui.components.AwardCoins3DIcon
import com.example.ui.components.Bag3DIcon
import com.example.ui.components.Coin3DIcon
import com.example.ui.components.CoinSeller3DIcon
import com.example.ui.components.Diamond3DIcon
import com.example.ui.components.IncomeVault3DIcon
import com.example.ui.components.ManageAds3DIcon
import com.example.ui.components.ManageReports3DIcon
import com.example.ui.components.ManageRoles3DIcon
import com.example.ui.components.MessageBlast3DIcon
import com.example.ui.components.ReportFlag3DIcon
import com.example.ui.components.Settings3DIcon
import com.example.ui.components.Level3DIcon
import com.example.ui.components.Store3DIcon
import com.example.ui.components.Support3DIcon
import com.example.ui.components.TasksCenter3DIcon
import com.example.ui.components.Verify3DIcon
import com.example.ui.components.Wallet3DIcon
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.data.AvatarHelper
import com.example.data.SupabaseProfileService
import com.example.data.UserLevelManager
import com.example.data.UserProfile
import com.example.data.UserSessionManager
import com.example.ui.theme.AppTheme
import com.example.ui.theme.QivoOrange
import kotlinx.coroutines.launch

@Composable
fun MeScreen(
    userEmail: String,
    userId: String,
    userName: String = "User",
    userGender: String = "",
    userCountry: String = "United States",
    userAvatarUrl: String = "",
    userNumericId: Long = 0L,
    userBirthDate: String = "",
    onOpenEditProfile: () -> Unit = {},
    onOpenUserDetail: ((UserProfile) -> Unit)? = null,
    onOpenLevel: () -> Unit = {},
    onOpenTaskCenter: () -> Unit = {},
    onOpenMessageBlast: () -> Unit = {},
    onOpenStore: () -> Unit = {},
    onOpenBag: () -> Unit = {},
    onOpenSupport: () -> Unit = {},
    onOpenVerify: () -> Unit = {},
    onOpenSettings: () -> Unit = {},
    onOpenWallet: () -> Unit = {},
    onOpenAwardCoins: () -> Unit = {},
    onOpenManageRoles: () -> Unit = {},
    onOpenManageReports: () -> Unit = {},
    onOpenManageAds: () -> Unit = {},
    onOpenAdminAnalytics: () -> Unit = {},
    onOpenAgency: () -> Unit = {},
    onOpenIncome: () -> Unit = {},
    onOpenFollows: (FollowTab) -> Unit = {},
    onOpenFriends: () -> Unit = {},
    onOpenFollowing: () -> Unit = {},
    onOpenFollowers: () -> Unit = {},
    onOpenVisitors: () -> Unit = {},
    onSignOut: () -> Unit
) {
    val context = LocalContext.current
    val colors = AppTheme.colors
    val isDark = colors.isDark

    val scrollState = rememberScrollState()
    val profileService = remember { SupabaseProfileService() }
    val numberFormat = remember { java.text.NumberFormat.getNumberInstance(java.util.Locale.US) }

    val initialGender = when {
        userGender.equals("female", ignoreCase = true) ||
        userGender.equals("f", ignoreCase = true) ||
        userGender.equals("woman", ignoreCase = true) ||
        userGender.equals("w", ignoreCase = true) -> "Female"

        userGender.equals("male", ignoreCase = true) ||
        userGender.equals("m", ignoreCase = true) ||
        userGender.equals("man", ignoreCase = true) -> "Male"

        else -> {
            val s = UserSessionManager.getSession(context)
            when {
                s?.gender.equals("female", ignoreCase = true) ||
                s?.gender.equals("f", ignoreCase = true) ||
                s?.gender.equals("woman", ignoreCase = true) ||
                s?.gender.equals("w", ignoreCase = true) -> "Female"

                s?.gender.equals("male", ignoreCase = true) ||
                s?.gender.equals("m", ignoreCase = true) ||
                s?.gender.equals("man", ignoreCase = true) -> "Male"

                else -> ""
            }
        }
    }

    val initialBirthDate = remember(userId, userBirthDate) {
        val s = UserSessionManager.getSession(context)
        when {
            userBirthDate.isNotBlank() -> userBirthDate
            !s?.birthDate.isNullOrBlank() -> s.birthDate
            else -> "2003-01-01"
        }
    }

    val cachedSession = remember(userId) { UserSessionManager.getSession(context) }
    val observedCoinsFlow by UserSessionManager.coinsFlow.collectAsStateWithLifecycle()
    val observedDiamondsFlow by UserSessionManager.diamondsFlow.collectAsStateWithLifecycle()
    val sessionUpdateTimestamp by UserSessionManager.sessionUpdateFlow.collectAsStateWithLifecycle()

    val initialCoins = cachedSession?.coins ?: UserSessionManager.getCoins(context)
    val initialDiamonds = UserSessionManager.getDiamonds(context)
    val initialIsAdmin = cachedSession?.isAdmin ?: UserSessionManager.isAdmin(context)
    val initialIsCoinSeller = cachedSession?.isCoinSeller ?: UserSessionManager.isCoinSeller(context)
    val initialIsAgent = cachedSession?.isAgent ?: UserSessionManager.isAgent(context)
    val initialIsVerified = cachedSession?.isVerified ?: UserSessionManager.isVerified(context)

    val initialAvatar = cachedSession?.avatarUrl?.takeIf { it.isNotBlank() } ?: userAvatarUrl
    var liveAvatarUrl by remember(userId) { mutableStateOf(initialAvatar) }
    var liveNumericId by remember(userId, userNumericId) { mutableStateOf(if (userNumericId > 0L) userNumericId else cachedSession?.numericId ?: 0L) }
    var liveGender by remember(userId, userGender) { mutableStateOf(initialGender) }
    var liveCountry by remember(userId, userCountry) { mutableStateOf(if (userCountry.isNotBlank()) userCountry else cachedSession?.country ?: "United States") }
    var liveName by remember(userId, userName) { mutableStateOf(if (userName.isNotBlank() && userName != "QIVO User") userName else cachedSession?.name ?: userName) }
    var liveBirthDate by remember(userId, userBirthDate) { mutableStateOf(initialBirthDate) }
    val activeFrameFlowState by UserSessionManager.activeFrameFlow.collectAsStateWithLifecycle(
        initialValue = userId to UserSessionManager.getActiveFrameId(context, userId)
    )
    val currentWornFrameId = if (activeFrameFlowState.first == userId) {
        activeFrameFlowState.second
    } else {
        UserSessionManager.getActiveFrameId(context, userId)
    }
    var liveCoins by remember(userId) { mutableStateOf(initialCoins) }
    var liveDiamonds by remember(userId) { mutableStateOf(initialDiamonds) }
    val observedExpFlow by UserSessionManager.expFlow.collectAsStateWithLifecycle()
    val initialExp = cachedSession?.exp ?: UserSessionManager.getExp(context, userId)
    var liveExp by remember(userId) { mutableStateOf(initialExp) }
    LaunchedEffect(observedExpFlow) {
        val e = observedExpFlow
        if (e != null && e >= 0L) {
            liveExp = e
        }
    }
    val userLevelInfo = remember(liveExp) { UserLevelManager.getLevelInfo(liveExp) }
    var isAdmin by remember(userId) { mutableStateOf(initialIsAdmin) }
    var isCoinSeller by remember(userId) { mutableStateOf(initialIsCoinSeller) }
    var isAgent by remember(userId) { mutableStateOf(initialIsAgent) }
    val isFemaleAccount = liveGender.equals("Female", ignoreCase = true) ||
        initialGender.equals("Female", ignoreCase = true) ||
        liveGender.startsWith("f", ignoreCase = true) ||
        liveGender.startsWith("w", ignoreCase = true)
    var followStats by remember(userId) {
        mutableStateOf(com.example.data.FollowDataCacheStore.getCachedStats(userId) ?: com.example.data.FollowStats())
    }
    var showAwardCoinsDialog by remember { mutableStateOf(false) }
    var showManageRolesDialog by remember { mutableStateOf(false) }
    var showManageReportsDialog by remember { mutableStateOf(false) }
    var isVerified by remember { mutableStateOf(initialIsVerified) }
    var fullProfileState by remember(userId) { mutableStateOf<UserProfile?>(null) }

    val scope = rememberCoroutineScope()

    // Sync when coin/diamond flows emit
    LaunchedEffect(observedCoinsFlow) {
        val c = observedCoinsFlow
        if (c != null && c >= 0L) {
            liveCoins = c
        }
    }
    LaunchedEffect(observedDiamondsFlow) {
        val d = observedDiamondsFlow
        if (d != null && d >= 0L) {
            liveDiamonds = d
        }
    }

    // Sync when session update flow triggers
    LaunchedEffect(sessionUpdateTimestamp) {
        if (sessionUpdateTimestamp > 0L) {
            val session = UserSessionManager.getSession(context)
            if (session != null) {
                if (session.name.isNotBlank() && session.name != "QIVO User") liveName = session.name
                if (session.avatarUrl.isNotBlank()) liveAvatarUrl = session.avatarUrl
                if (session.country.isNotBlank()) liveCountry = session.country
                if (session.birthDate.isNotBlank()) liveBirthDate = session.birthDate
                if (session.numericId > 0L) liveNumericId = session.numericId
                if (session.gender.isNotBlank()) {
                    val resolved = when {
                        session.gender.equals("female", ignoreCase = true) ||
                        session.gender.equals("f", ignoreCase = true) ||
                        session.gender.equals("woman", ignoreCase = true) ||
                        session.gender.equals("w", ignoreCase = true) -> "Female"
                        session.gender.equals("male", ignoreCase = true) ||
                        session.gender.equals("m", ignoreCase = true) ||
                        session.gender.equals("man", ignoreCase = true) -> "Male"
                        else -> session.gender
                    }
                    liveGender = resolved
                }
                isAdmin = session.isAdmin
                isCoinSeller = session.isCoinSeller
                isAgent = session.isAgent
                isVerified = session.isVerified
                liveCoins = session.coins
                liveDiamonds = UserSessionManager.getDiamonds(context)
            }
        }
    }

    // Sync when props change
    LaunchedEffect(userAvatarUrl) {
        val s = UserSessionManager.getSession(context)
        val bestAvatar = s?.avatarUrl?.takeIf { it.isNotBlank() } ?: userAvatarUrl
        if (bestAvatar.isNotEmpty()) {
            liveAvatarUrl = bestAvatar
        }
    }
    LaunchedEffect(userName) {
        if (userName.isNotBlank() && userName != "Y" && userName != "QIVO User") {
            liveName = userName
        }
    }
    LaunchedEffect(userCountry) {
        if (userCountry.isNotBlank()) {
            liveCountry = userCountry
        }
    }
    LaunchedEffect(userNumericId) {
        if (userNumericId > 0L) {
            liveNumericId = userNumericId
        }
    }
    LaunchedEffect(userBirthDate) {
        if (userBirthDate.isNotBlank()) {
            liveBirthDate = userBirthDate
        }
    }
    LaunchedEffect(userGender) {
        if (userGender.isNotBlank()) {
            val resolved = when {
                userGender.equals("female", ignoreCase = true) ||
                userGender.equals("f", ignoreCase = true) ||
                userGender.equals("woman", ignoreCase = true) ||
                userGender.equals("w", ignoreCase = true) -> "Female"

                userGender.equals("male", ignoreCase = true) ||
                userGender.equals("m", ignoreCase = true) ||
                userGender.equals("man", ignoreCase = true) -> "Male"

                else -> ""
            }
            if (resolved.isNotEmpty()) {
                liveGender = resolved
            }
        }
    }

    // Fetch accurate live user details & coins from Supabase in background
    LaunchedEffect(userId) {
        val session = UserSessionManager.getSession(context)
        val token = session?.accessToken
        
        if (session != null) {
            isAdmin = session.isAdmin
            isCoinSeller = session.isCoinSeller
            isAgent = session.isAgent
            isVerified = session.isVerified
        }

        val remoteCoins = profileService.fetchCoins(userId)
        val remoteDiamonds = profileService.fetchDiamonds(userId)
        if (remoteCoins > 0L) {
            liveCoins = remoteCoins
            UserSessionManager.saveCoins(context, remoteCoins)
        }
        if (remoteDiamonds > 0L) {
            liveDiamonds = remoteDiamonds
            UserSessionManager.saveDiamonds(context, remoteDiamonds)
        }

        // Load follow stats
        val cachedStats = com.example.data.FollowDataCacheStore.getCachedStats(userId)
        if (cachedStats != null) {
            followStats = cachedStats
        }
        val remoteStats = profileService.fetchFollowStats(userId)
        com.example.data.FollowDataCacheStore.putStats(userId, remoteStats)
        followStats = remoteStats

        val remoteProfile = profileService.fetchProfile(userId, userEmail, token)
        if (remoteProfile != null) {
            fullProfileState = remoteProfile
            if (remoteProfile.name.isNotBlank() && remoteProfile.name != "QIVO User") {
                liveName = remoteProfile.name
            }
            if (remoteProfile.gender.isNotBlank()) {
                val resolved = when {
                    remoteProfile.gender.equals("female", ignoreCase = true) ||
                    remoteProfile.gender.equals("f", ignoreCase = true) ||
                    remoteProfile.gender.equals("woman", ignoreCase = true) ||
                    remoteProfile.gender.equals("w", ignoreCase = true) -> "Female"

                    remoteProfile.gender.equals("male", ignoreCase = true) ||
                    remoteProfile.gender.equals("m", ignoreCase = true) ||
                    remoteProfile.gender.equals("man", ignoreCase = true) -> "Male"

                    else -> ""
                }
                if (resolved.isNotEmpty()) {
                    liveGender = resolved
                }
            }
            if (remoteProfile.country.isNotBlank()) {
                liveCountry = remoteProfile.country
            }
            if (remoteProfile.birthDate.isNotBlank()) {
                liveBirthDate = remoteProfile.birthDate
            }
            if (remoteProfile.coins > 0L) {
                liveCoins = remoteProfile.coins
            }
            isAdmin = remoteProfile.isAdmin
            isCoinSeller = remoteProfile.isCoinSeller
            isAgent = remoteProfile.isAgent
            if (remoteProfile.avatarUrl.isNotEmpty()) {
                liveAvatarUrl = remoteProfile.avatarUrl
            }
            if (remoteProfile.numericId > 0) {
                liveNumericId = remoteProfile.numericId
            }

            // Immediately persist so the next time screen is visited or app restarts, everything displays instantly without flashing
            UserSessionManager.saveRoles(context, remoteProfile.isAdmin, remoteProfile.isCoinSeller, remoteProfile.isAgent)
            UserSessionManager.saveSession(
                context = context,
                email = remoteProfile.email,
                userId = remoteProfile.id,
                name = remoteProfile.name,
                gender = remoteProfile.gender,
                country = remoteProfile.country,
                avatarUrl = remoteProfile.avatarUrl,
                numericId = remoteProfile.numericId,
                coins = remoteProfile.coins,
                isAdmin = remoteProfile.isAdmin,
                isCoinSeller = remoteProfile.isCoinSeller,
                isAgent = remoteProfile.isAgent,
                accessToken = token
            )
        }
    }

    val displayId = if (liveNumericId > 0) liveNumericId.toString() else "96124372"

    // Age calculation helper
    val computedAge = remember(liveBirthDate) {
        if (liveBirthDate.isBlank()) 20
        else {
            try {
                val yearStr = liveBirthDate.split("-", "/", " ", ".").firstOrNull { it.length == 4 }
                val birthYear = yearStr?.toIntOrNull() ?: 2005
                (2026 - birthYear).coerceIn(18, 99)
            } catch (e: Exception) {
                20
            }
        }
    }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(colors.screenBg)
            .testTag("me_screen_root")
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .verticalScroll(scrollState)
        ) {
            // Top Section with Aesthetic Sunset Amber Background spanning down to 3/4 way of the Recharge Card
            Box(
                modifier = Modifier.fillMaxWidth()
            ) {
                // Background layer: Gradient covering status bar, profile, stats, and 3/4 of the recharge card
                Column(modifier = Modifier.matchParentSize()) {
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .weight(1f)
                            .background(
                                if (isDark) {
                                    Brush.verticalGradient(
                                        listOf(
                                            Color(0xFF09120B),
                                            Color(0xFF0E1A11),
                                            Color(0xFF132417),
                                            Color(0xFF09120B)
                                        )
                                    )
                                } else {
                                    Brush.verticalGradient(
                                        listOf(
                                            Color(0xFF009639), // Deep Emerald
                                            Color(0xFF00B04A), // Jewel Jade
                                            Color(0xFF00C853), // Vivid Emerald
                                            Color(0xFF26E06D)  // Mint Emerald
                                        )
                                    )
                                }
                            )
                    )
                    // Bottom 23dp (remaining 1/4 of 92dp card) with standard background
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(23.dp)
                            .background(colors.screenBg)
                    )
                }

                // Foreground Content for Top Section
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .statusBarsPadding()
                        .padding(horizontal = 18.dp)
                ) {
                    // 1. Top Header Profile Area
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Column(modifier = Modifier.weight(1f)) {
                            // Name with Chevron - click to edit profile
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                modifier = Modifier
                                    .clickable { onOpenEditProfile() }
                                    .testTag("btn_me_name_edit_profile")
                            ) {
                                Text(
                                    text = if (liveName.isNotBlank()) liveName else "Y",
                                    fontSize = 24.sp,
                                    fontWeight = FontWeight.ExtraBold,
                                    color = Color.White
                                )
                                Spacer(modifier = Modifier.width(6.dp))
                                Icon(
                                    imageVector = Icons.Default.ChevronRight,
                                    contentDescription = "Edit Profile",
                                    tint = Color.White,
                                    modifier = Modifier.size(24.dp)
                                )
                            }

                            Spacer(modifier = Modifier.height(6.dp))

                            // Badges Row: Gender/Age + Country + Verification
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                val isFemale = liveGender.equals("Female", ignoreCase = true) || liveGender.equals("F", ignoreCase = true)
                                val genderBgColor = if (isFemale) Color(0xFFE2C485) else Color(0xFFD4C8B8)

                                // Gender & Age Badge
                                Surface(
                                    shape = RoundedCornerShape(6.dp),
                                    color = genderBgColor
                                ) {
                                    Row(
                                        modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp),
                                        verticalAlignment = Alignment.CenterVertically
                                    ) {
                                        Icon(
                                            imageVector = if (isFemale) Icons.Default.Female else Icons.Default.Male,
                                            contentDescription = "Gender",
                                            tint = Color.Black,
                                            modifier = Modifier.size(11.dp)
                                        )
                                        Spacer(modifier = Modifier.width(2.dp))
                                        Text(
                                            text = computedAge.toString(),
                                            fontSize = 10.sp,
                                            fontWeight = FontWeight.Bold,
                                            color = Color.Black
                                        )
                                    }
                                }

                                Spacer(modifier = Modifier.width(4.dp))

                                // Country Badge
                                Surface(
                                    shape = RoundedCornerShape(6.dp),
                                    color = Color(0xFFC6FF00) // Lime Green
                                ) {
                                    Text(
                                        text = if (liveCountry.isNotBlank()) liveCountry else "United States",
                                        fontSize = 10.sp,
                                        fontWeight = FontWeight.Bold,
                                        color = Color.Black,
                                        modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                                    )
                                }

                                Spacer(modifier = Modifier.width(4.dp))

                                // Level Badge (Lv.X)
                                Surface(
                                    shape = RoundedCornerShape(6.dp),
                                    color = Color(0xFFFFD54F),
                                    modifier = Modifier
                                        .clickable { onOpenLevel() }
                                        .testTag("badge_user_level")
                                ) {
                                    Row(
                                        modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp),
                                        verticalAlignment = Alignment.CenterVertically
                                    ) {
                                        Icon(
                                            imageVector = Icons.Default.EmojiEvents,
                                            contentDescription = "Level",
                                            tint = Color(0xFF5D4037),
                                            modifier = Modifier.size(11.dp)
                                        )
                                        Spacer(modifier = Modifier.width(3.dp))
                                        Text(
                                            text = "Lv.${userLevelInfo.level}",
                                            fontSize = 10.sp,
                                            fontWeight = FontWeight.ExtraBold,
                                            color = Color(0xFF3E2723)
                                        )
                                    }
                                }

                                Spacer(modifier = Modifier.width(4.dp))

                                // Verification Status Badge
                                Surface(
                                    shape = RoundedCornerShape(6.dp),
                                    color = if (isVerified) Color(0xFFE8F5E9) else (if (isDark) Color(0xFF332010) else Color(0xFFFFF3E0)),
                                    modifier = Modifier.clickable { onOpenVerify() }
                                ) {
                                    Row(
                                        modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp),
                                        verticalAlignment = Alignment.CenterVertically
                                    ) {
                                        Icon(
                                            imageVector = Icons.Default.Verified,
                                            contentDescription = "Verification Status",
                                            tint = if (isVerified) Color(0xFF4CAF50) else Color(0xFFFF9800),
                                            modifier = Modifier.size(11.dp)
                                        )
                                        Spacer(modifier = Modifier.width(3.dp))
                                        Text(
                                            text = if (isVerified) "Verified" else "Unverified",
                                            fontSize = 10.sp,
                                            fontWeight = FontWeight.Bold,
                                            color = if (isVerified) Color(0xFF2E7D32) else Color(0xFFFF9800)
                                        )
                                    }
                                }

                                if (isCoinSeller) {
                                    Spacer(modifier = Modifier.width(4.dp))
                                    Surface(
                                        shape = RoundedCornerShape(6.dp),
                                        color = Color(0xFF00C853)
                                    ) {
                                        Text(
                                            text = "SELLER",
                                            fontSize = 9.sp,
                                            fontWeight = FontWeight.Black,
                                            color = Color.White,
                                            modifier = Modifier.padding(horizontal = 5.dp, vertical = 2.dp)
                                        )
                                    }
                                }

                                if (isAgent) {
                                    Spacer(modifier = Modifier.width(4.dp))
                                    Surface(
                                        shape = RoundedCornerShape(6.dp),
                                        color = Color(0xFF7C4DFF)
                                    ) {
                                        Text(
                                            text = "AGENT",
                                            fontSize = 9.sp,
                                            fontWeight = FontWeight.Black,
                                            color = Color.White,
                                            modifier = Modifier.padding(horizontal = 5.dp, vertical = 2.dp)
                                        )
                                    }
                                }
                            }

                            Spacer(modifier = Modifier.height(8.dp))

                            // ID Row with Copy Icon
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                modifier = Modifier
                                    .clickable {
                                        val clipboard = context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
                                        clipboard.setPrimaryClip(ClipData.newPlainText("User ID", displayId))
                                        AppToast.show("Copied ID: $displayId")
                                    }
                                    .testTag("btn_me_copy_id")
                            ) {
                                Text(
                                    text = "ID:$displayId",
                                    fontSize = 12.sp,
                                    fontWeight = FontWeight.Medium,
                                    color = Color.White.copy(alpha = 0.9f)
                                )
                                Spacer(modifier = Modifier.width(4.dp))
                                Icon(
                                    imageVector = Icons.Default.ContentCopy,
                                    contentDescription = "Copy ID",
                                    tint = Color.White.copy(alpha = 0.9f),
                                    modifier = Modifier.size(14.dp)
                                )
                            }
                        }

                        // Profile Avatar Photo on Right with Frame Overlay and Glow
                        // Clicking avatar opens Profile Preview
                        Box(
                            modifier = Modifier
                                .size(84.dp)
                                .clickable {
                                    if (onOpenUserDetail != null) {
                                        val currentProf = fullProfileState?.copy(
                                            id = userId,
                                            numericId = liveNumericId,
                                            email = userEmail,
                                            name = liveName,
                                            gender = liveGender,
                                            birthDate = liveBirthDate,
                                            country = liveCountry,
                                            avatarUrl = liveAvatarUrl,
                                            coins = liveCoins,
                                            diamonds = liveDiamonds,
                                            isAdmin = isAdmin,
                                            isCoinSeller = isCoinSeller,
                                            isAgent = isAgent
                                        ) ?: UserProfile(
                                            id = userId,
                                            numericId = liveNumericId,
                                            email = userEmail,
                                            name = liveName,
                                            gender = liveGender,
                                            birthDate = liveBirthDate,
                                            country = liveCountry,
                                            avatarUrl = liveAvatarUrl,
                                            coins = liveCoins,
                                            diamonds = liveDiamonds,
                                            isAdmin = isAdmin,
                                            isCoinSeller = isCoinSeller,
                                            isAgent = isAgent
                                        )
                                        onOpenUserDetail(currentProf)
                                    }
                                }
                                .testTag("btn_me_avatar_preview"),
                            contentAlignment = Alignment.Center
                        ) {
                            AvatarHelper.UserAvatarImage(
                                avatarUrl = liveAvatarUrl,
                                userId = userId,
                                gender = liveGender,
                                numericId = liveNumericId,
                                frameId = currentWornFrameId,
                                showFrame = true,
                                shape = CircleShape,
                                contentDescription = "User Avatar",
                                modifier = Modifier.fillMaxSize(),
                                contentScale = ContentScale.Crop
                            )
                        }
                    }

                    Spacer(modifier = Modifier.height(20.dp))

                    // 2. Stats Row: Friends, Following, Followers, Visitors (clean text stats without background cards/padding)
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(vertical = 4.dp),
                        horizontalArrangement = Arrangement.SpaceEvenly,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Column(
                            modifier = Modifier
                                .weight(1f)
                                .clickable { onOpenFriends() },
                            horizontalAlignment = Alignment.CenterHorizontally
                        ) {
                            Text(
                                text = "${followStats.friendsCount}",
                                fontSize = 18.sp,
                                fontWeight = FontWeight.ExtraBold,
                                color = Color.White
                            )
                            Spacer(modifier = Modifier.height(2.dp))
                            Text(
                                text = "Friends",
                                fontSize = 12.sp,
                                fontWeight = FontWeight.Medium,
                                color = Color.White.copy(alpha = 0.9f)
                            )
                        }

                        Column(
                            modifier = Modifier
                                .weight(1f)
                                .clickable { onOpenFollowing() },
                            horizontalAlignment = Alignment.CenterHorizontally
                        ) {
                            Text(
                                text = "${followStats.followingCount}",
                                fontSize = 18.sp,
                                fontWeight = FontWeight.ExtraBold,
                                color = Color.White
                            )
                            Spacer(modifier = Modifier.height(2.dp))
                            Text(
                                text = "Following",
                                fontSize = 12.sp,
                                fontWeight = FontWeight.Medium,
                                color = Color.White.copy(alpha = 0.9f)
                            )
                        }

                        Column(
                            modifier = Modifier
                                .weight(1f)
                                .clickable { onOpenFollowers() },
                            horizontalAlignment = Alignment.CenterHorizontally
                        ) {
                            Text(
                                text = "${followStats.followersCount}",
                                fontSize = 18.sp,
                                fontWeight = FontWeight.ExtraBold,
                                color = Color.White
                            )
                            Spacer(modifier = Modifier.height(2.dp))
                            Text(
                                text = "Followers",
                                fontSize = 12.sp,
                                fontWeight = FontWeight.Medium,
                                color = Color.White.copy(alpha = 0.9f)
                            )
                        }

                        Column(
                            modifier = Modifier
                                .weight(1f)
                                .clickable { onOpenVisitors() },
                            horizontalAlignment = Alignment.CenterHorizontally
                        ) {
                            Text(
                                text = "${followStats.visitorsCount}",
                                fontSize = 18.sp,
                                fontWeight = FontWeight.ExtraBold,
                                color = Color.White
                            )
                            Spacer(modifier = Modifier.height(2.dp))
                            Text(
                                text = "Visitors",
                                fontSize = 12.sp,
                                fontWeight = FontWeight.Medium,
                                color = Color.White.copy(alpha = 0.9f)
                            )
                        }
                    }

                    Spacer(modifier = Modifier.height(20.dp))

                    // 3. Banner Cards Row (Coins & VIP/SVIP Privileges)
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(12.dp)
                    ) {
                    // Card 1: Coins Card (Luxury Sunset Amber / Gold Gradient) - opens Wallet Recharge screen
                    Surface(
                        modifier = Modifier
                            .weight(1f)
                            .height(96.dp)
                            .clickable { onOpenWallet() }
                            .testTag("btn_me_popup_recharge"),
                        shape = RoundedCornerShape(20.dp),
                        color = Color.Transparent
                    ) {
                        Box(
                            modifier = Modifier
                                .fillMaxSize()
                                .background(
                                    Brush.linearGradient(
                                        colors = if (isDark) listOf(Color(0xFF2E1C0A), Color(0xFF1E1104))
                                        else listOf(Color(0xFFFFF9C4), Color(0xFFFFECB3))
                                    )
                                )
                                .padding(12.dp)
                        ) {
                            Row(
                                modifier = Modifier.fillMaxSize(),
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.SpaceBetween
                            ) {
                                Column(modifier = Modifier.weight(1f)) {
                                    Row(verticalAlignment = Alignment.CenterVertically) {
                                        Coin3DIcon(size = 22.dp)
                                        Spacer(modifier = Modifier.width(6.dp))
                                        Text(
                                            text = "${numberFormat.format(liveCoins)}",
                                            fontSize = 15.sp,
                                            fontWeight = FontWeight.ExtraBold,
                                            color = if (isDark) Color(0xFFFFD54F) else Color(0xFFE65100)
                                        )
                                    }
                                    Spacer(modifier = Modifier.height(6.dp))
                                    Row(
                                        verticalAlignment = Alignment.CenterVertically,
                                        modifier = Modifier
                                            .clip(RoundedCornerShape(8.dp))
                                            .background(
                                                Brush.horizontalGradient(
                                                    listOf(Color(0xFFFF9800), Color(0xFFFF6D00))
                                                )
                                            )
                                            .padding(horizontal = 8.dp, vertical = 4.dp)
                                    ) {
                                        Text(
                                            text = "Recharge",
                                            fontSize = 11.sp,
                                            fontWeight = FontWeight.Black,
                                            color = Color.White
                                        )
                                        Spacer(modifier = Modifier.width(3.dp))
                                        Icon(
                                            imageVector = Icons.Default.ChevronRight,
                                            contentDescription = "Recharge",
                                            tint = Color.White,
                                            modifier = Modifier.size(13.dp)
                                        )
                                    }
                                }
                            }
                        }
                    }

                    // Card 2: Income Card (Converts Diamonds to Coins)
                    Surface(
                        modifier = Modifier
                            .weight(1f)
                            .height(96.dp)
                            .clickable { onOpenIncome() }
                            .testTag("income_button"),
                        shape = RoundedCornerShape(20.dp),
                        color = Color.Transparent
                    ) {
                        Box(
                            modifier = Modifier
                                .fillMaxSize()
                                .background(
                                    Brush.linearGradient(
                                        colors = if (isDark) listOf(Color(0xFF0A2234), Color(0xFF061420))
                                        else listOf(Color(0xFFE0F7FA), Color(0xFFB2EBF2))
                                    )
                                )
                                .padding(12.dp)
                        ) {
                            Row(
                                modifier = Modifier.fillMaxSize(),
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.SpaceBetween
                            ) {
                                Column(modifier = Modifier.weight(1f)) {
                                    Text(
                                        text = "Income",
                                        fontSize = 14.sp,
                                        fontWeight = FontWeight.ExtraBold,
                                        color = if (isDark) Color(0xFF80D8FF) else Color(0xFF006064)
                                    )
                                    Spacer(modifier = Modifier.height(4.dp))
                                    Row(
                                        verticalAlignment = Alignment.CenterVertically,
                                        modifier = Modifier
                                            .clip(RoundedCornerShape(6.dp))
                                            .background(
                                                if (isDark) Color(0xFF00E5FF).copy(alpha = 0.2f)
                                                else Color(0xFF00ACC1).copy(alpha = 0.2f)
                                            )
                                            .padding(horizontal = 6.dp, vertical = 3.dp)
                                    ) {
                                        Text(
                                            text = "${numberFormat.format(liveDiamonds)} 💎",
                                            fontSize = 11.sp,
                                            fontWeight = FontWeight.Bold,
                                            color = if (isDark) Color(0xFFE0F7FA) else Color(0xFF006064)
                                        )
                                    }
                                }
                                Diamond3DIcon(size = 36.dp, animated = true)
                            }
                        }
                    }
                }
            }
        }

        // Lower Section on Main Screen Background
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 18.dp)
        ) {
            // 4. BEAUTIFULLY REDESIGNED Admin & Seller Panel
            if (isAdmin || isCoinSeller) {
                Spacer(modifier = Modifier.height(16.dp))

                Card(
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(20.dp),
                    colors = CardDefaults.cardColors(
                        containerColor = if (isDark) Color(0xFF131316) else Color.White
                    ),
                    border = BorderStroke(
                        1.5.dp,
                        if (isDark) Color(0xFF2E2E38) else Color(0xFFFFE082)
                    ),
                    elevation = CardDefaults.cardElevation(defaultElevation = if (isDark) 0.dp else 2.dp)
                ) {
                    Column(modifier = Modifier.padding(16.dp)) {
                        // Header row with modern gradient accent or badge
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Box(
                                    modifier = Modifier
                                        .size(34.dp)
                                        .clip(CircleShape)
                                        .background(
                                            if (isAdmin) Color(0xFFFFEDE0) else Color(0xFFE8F5E9)
                                        ),
                                    contentAlignment = Alignment.Center
                                ) {
                                    if (isAdmin) {
                                        Icon(
                                            imageVector = Icons.Default.Shield,
                                            contentDescription = "Admin Shield",
                                            tint = QivoOrange,
                                            modifier = Modifier.size(20.dp)
                                        )
                                    } else {
                                        Coin3DIcon(size = 22.dp)
                                    }
                                }
                                Spacer(modifier = Modifier.width(10.dp))
                                Column {
                                    Text(
                                        text = if (isAdmin) "Administration Hub" else "Coin Seller Panel",
                                        fontSize = 15.sp,
                                        fontWeight = FontWeight.ExtraBold,
                                        color = colors.textPrimary
                                    )
                                    Text(
                                        text = if (isAdmin) "Full executive authority & system tools" else "Authorized coin distribution point",
                                        fontSize = 11.sp,
                                        color = colors.textSecondary
                                    )
                                }
                            }

                            Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                                if (isAdmin) {
                                    Surface(
                                        color = QivoOrange,
                                        shape = RoundedCornerShape(8.dp)
                                    ) {
                                        Text(
                                            text = "ADMIN",
                                            fontSize = 9.sp,
                                            fontWeight = FontWeight.Black,
                                            color = Color.White,
                                            modifier = Modifier.padding(horizontal = 7.dp, vertical = 3.dp)
                                        )
                                    }
                                }

                                if (isCoinSeller) {
                                    Surface(
                                        color = Color(0xFF10B981),
                                        shape = RoundedCornerShape(8.dp)
                                    ) {
                                        Text(
                                            text = "SELLER",
                                            fontSize = 9.sp,
                                            fontWeight = FontWeight.Black,
                                            color = Color.White,
                                            modifier = Modifier.padding(horizontal = 7.dp, vertical = 3.dp)
                                        )
                                    }
                                }
                            }
                        }

                        Spacer(modifier = Modifier.height(16.dp))

                        if (isAdmin) {
                            // 3D Icon Grid for Admin Features (Row 1: Award Coins, Roles, Reports, Ads)
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween
                            ) {
                                GridTile(
                                    title = "Award Coins",
                                    iconBg = if (isDark) Color(0xFF332612) else Color(0xFFFFF8E1),
                                    textColor = colors.textPrimary,
                                    iconContent = { AwardCoins3DIcon(size = 36.dp) },
                                    onClick = onOpenAwardCoins
                                )
                                GridTile(
                                    title = "Roles",
                                    iconBg = if (isDark) Color(0xFF10283E) else Color(0xFFE0F2FE),
                                    textColor = colors.textPrimary,
                                    iconContent = { ManageRoles3DIcon(size = 36.dp) },
                                    onClick = onOpenManageRoles
                                )
                                GridTile(
                                    title = "Reports",
                                    iconBg = if (isDark) Color(0xFF3B181E) else Color(0xFFFFEBEE),
                                    textColor = colors.textPrimary,
                                    iconContent = { ManageReports3DIcon(size = 36.dp) },
                                    onClick = onOpenManageReports
                                )
                                GridTile(
                                    title = "Ads",
                                    iconBg = if (isDark) Color(0xFF382312) else Color(0xFFFFF3E0),
                                    textColor = colors.textPrimary,
                                    iconContent = { ManageAds3DIcon(size = 36.dp) },
                                    onClick = onOpenManageAds
                                )
                            }

                            Spacer(modifier = Modifier.height(14.dp))

                            // Row 2: Analytics
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.Start
                            ) {
                                GridTile(
                                    title = "Analytics",
                                    iconBg = if (isDark) Color(0xFF122C24) else Color(0xFFE8F5E9),
                                    textColor = colors.textPrimary,
                                    iconContent = { AdminAnalytics3DIcon(size = 36.dp) },
                                    onClick = onOpenAdminAnalytics
                                )
                            }
                        } else {
                            // Coin Seller Only - 3D Icon Tile
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.Start
                            ) {
                                GridTile(
                                    title = "Transfer Coins",
                                    iconBg = if (isDark) Color(0xFF16331C) else Color(0xFFE8F5E9),
                                    textColor = colors.textPrimary,
                                    iconContent = { CoinSeller3DIcon(size = 36.dp) },
                                    onClick = onOpenAwardCoins
                                )
                            }
                        }
                    }
                }
            }

            // 4.1 OFFICIAL AGENT AGENCY MANAGEMENT PANEL (When isAgent == true)
            if (isAgent) {
                Spacer(modifier = Modifier.height(16.dp))

                Card(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clickable { onOpenAgency() }
                        .testTag("agent_agency_panel_card"),
                    shape = RoundedCornerShape(20.dp),
                    colors = CardDefaults.cardColors(
                        containerColor = if (isDark) Color(0xFF1A1828) else Color(0xFFFFF9E6)
                    ),
                    border = BorderStroke(
                        1.5.dp,
                        QivoOrange
                    ),
                    elevation = CardDefaults.cardElevation(defaultElevation = if (isDark) 0.dp else 2.dp)
                ) {
                    Column(modifier = Modifier.padding(16.dp)) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Box(
                                    modifier = Modifier
                                        .size(38.dp)
                                        .clip(CircleShape)
                                        .background(Brush.linearGradient(listOf(Color(0xFFFF9800), Color(0xFFFF3D00)))),
                                    contentAlignment = Alignment.Center
                                ) {
                                    Icon(
                                        imageVector = Icons.Default.SupervisorAccount,
                                        contentDescription = "Agent Panel",
                                        tint = Color.White,
                                        modifier = Modifier.size(22.dp)
                                    )
                                }
                                Spacer(modifier = Modifier.width(10.dp))
                                Column {
                                    Text(
                                        text = "Agency Management Panel",
                                        fontSize = 15.sp,
                                        fontWeight = FontWeight.ExtraBold,
                                        color = colors.textPrimary
                                    )
                                    Text(
                                        text = "Manage Agency, Unique Code & Applications",
                                        fontSize = 11.sp,
                                        color = colors.textSecondary
                                    )
                                }
                            }

                            Surface(
                                shape = RoundedCornerShape(8.dp),
                                color = QivoOrange
                            ) {
                                Text(
                                    text = "AGENT",
                                    fontSize = 9.sp,
                                    fontWeight = FontWeight.Black,
                                    color = Color.White,
                                    modifier = Modifier.padding(horizontal = 7.dp, vertical = 3.dp)
                                )
                            }
                        }

                        Spacer(modifier = Modifier.height(10.dp))
                        Text(
                            text = "Create your agency name, bio, upload logo, generate unique code, and review member join requests.",
                            fontSize = 12.sp,
                            color = colors.textSecondary
                        )

                        Spacer(modifier = Modifier.height(12.dp))

                        Button(
                            onClick = onOpenAgency,
                            modifier = Modifier.fillMaxWidth(),
                            shape = RoundedCornerShape(12.dp),
                            colors = ButtonDefaults.buttonColors(containerColor = QivoOrange)
                        ) {
                            Icon(Icons.Default.SupervisorAccount, contentDescription = null, modifier = Modifier.size(18.dp))
                            Spacer(modifier = Modifier.width(6.dp))
                            Text("Open Agency Dashboard →", fontWeight = FontWeight.Bold, fontSize = 13.sp)
                        }
                    }
                }
            }

            Spacer(modifier = Modifier.height(20.dp))

            // 5. Grid Row 1: Tasks, Level, Blast, Store (Custom 3D Icons)
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                GridTile(
                    title = "Tasks",
                    iconBg = if (isDark) Color(0xFF16331C) else Color(0xFFE8F5E9),
                    textColor = colors.textPrimary,
                    iconContent = { TasksCenter3DIcon(size = 34.dp) }
                ) {
                    onOpenTaskCenter()
                }
                GridTile(
                    title = "Level",
                    iconBg = if (isDark) Color(0xFF332612) else Color(0xFFFFF8E1),
                    textColor = colors.textPrimary,
                    iconContent = { Level3DIcon(size = 34.dp, level = userLevelInfo.level) }
                ) {
                    onOpenLevel()
                }
                GridTile(
                    title = "Blast",
                    iconBg = if (isDark) Color(0xFF38181A) else Color(0xFFFFEBEE),
                    textColor = colors.textPrimary,
                    iconContent = { MessageBlast3DIcon(size = 34.dp) }
                ) {
                    onOpenMessageBlast()
                }
                GridTile(
                    title = "Store",
                    iconBg = if (isDark) Color(0xFF15293D) else Color(0xFFE3F2FD),
                    textColor = colors.textPrimary,
                    iconContent = { Store3DIcon(size = 34.dp) }
                ) {
                    onOpenStore()
                }
            }

            Spacer(modifier = Modifier.height(14.dp))

            // Grid Row 2: Bag
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.Start
            ) {
                GridTile(
                    title = "Bag",
                    iconBg = if (isDark) Color(0xFF381525) else Color(0xFFFCE4EC),
                    textColor = colors.textPrimary,
                    iconContent = { Bag3DIcon(size = 34.dp) }
                ) {
                    onOpenBag()
                }
            }

            Spacer(modifier = Modifier.height(24.dp))

            // 6. Section Title: "Other"
            Text(
                text = "Other",
                fontSize = 18.sp,
                fontWeight = FontWeight.Bold,
                color = colors.textPrimary
            )

            Spacer(modifier = Modifier.height(14.dp))

            // 7. List Items: Level, Verification, Support, Agency (Female/Agent only), Coin Seller (Seller only), Settings
            Card(
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(20.dp),
                colors = CardDefaults.cardColors(containerColor = colors.cardBg),
                border = BorderStroke(1.dp, colors.cardBorder),
                elevation = CardDefaults.cardElevation(defaultElevation = if (isDark) 0.dp else 1.dp)
            ) {
                Column(modifier = Modifier.padding(vertical = 4.dp)) {
                    // Verification Row
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable { onOpenVerify() }
                            .padding(horizontal = 16.dp, vertical = 14.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Box(
                                modifier = Modifier
                                    .size(38.dp)
                                    .clip(CircleShape)
                                    .background(if (isDark) Color(0xFF15293D) else Color(0xFFE3F2FD)),
                                contentAlignment = Alignment.Center
                            ) {
                                Verify3DIcon(size = 26.dp)
                            }
                            Spacer(modifier = Modifier.width(14.dp))
                            Column {
                                Text(
                                    text = "Verification Center",
                                    fontSize = 16.sp,
                                    fontWeight = FontWeight.Bold,
                                    color = colors.textPrimary
                                )
                                Text(
                                    text = if (isVerified) "Account Verified (Gold Checkmark)" else "Get officially verified on Qivo",
                                    fontSize = 12.sp,
                                    color = if (isVerified) Color(0xFF00E676) else colors.textSecondary
                                )
                            }
                        }

                        Icon(
                            imageVector = Icons.AutoMirrored.Filled.ArrowForwardIos,
                            contentDescription = "Go",
                            tint = colors.textSecondary,
                            modifier = Modifier.size(16.dp)
                        )
                    }

                    HorizontalDivider(color = colors.divider)

                    // Support Row
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable { onOpenSupport() }
                            .padding(horizontal = 16.dp, vertical = 14.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Box(
                                modifier = Modifier
                                    .size(38.dp)
                                    .clip(CircleShape)
                                    .background(if (isDark) Color(0xFF16331C) else Color(0xFFE8F5E9)),
                                contentAlignment = Alignment.Center
                            ) {
                                Support3DIcon(size = 26.dp)
                            }
                            Spacer(modifier = Modifier.width(14.dp))
                            Text(
                                text = "Support",
                                fontSize = 16.sp,
                                fontWeight = FontWeight.Bold,
                                color = colors.textPrimary
                            )
                        }

                        Icon(
                            imageVector = Icons.AutoMirrored.Filled.ArrowForwardIos,
                            contentDescription = "Go",
                            tint = colors.textSecondary,
                            modifier = Modifier.size(16.dp)
                        )
                    }

                    if (isAgent || isFemaleAccount) {
                        HorizontalDivider(color = colors.divider)

                        // Agency Center Row with 3D Icon
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clickable { onOpenAgency() }
                                .padding(horizontal = 16.dp, vertical = 14.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Box(
                                    modifier = Modifier
                                        .size(38.dp)
                                        .clip(CircleShape)
                                        .background(if (isDark) Color(0xFF381533) else Color(0xFFFCE4EC)),
                                    contentAlignment = Alignment.Center
                                ) {
                                    Agency3DIcon(size = 28.dp)
                                }
                                Spacer(modifier = Modifier.width(14.dp))
                                Column {
                                    Text(
                                        text = if (isAgent) "Agency Management" else "Agency Center",
                                        fontSize = 16.sp,
                                        fontWeight = FontWeight.Bold,
                                        color = colors.textPrimary
                                    )
                                    Text(
                                        text = if (isAgent) "Manage official agency & members" else "Join an official agency with code",
                                        fontSize = 12.sp,
                                        color = colors.textSecondary
                                    )
                                }
                            }

                            Icon(
                                imageVector = Icons.AutoMirrored.Filled.ArrowForwardIos,
                                contentDescription = "Go",
                                tint = colors.textSecondary,
                                modifier = Modifier.size(16.dp)
                            )
                        }
                    }

                    if (isCoinSeller) {
                        HorizontalDivider(color = colors.divider)

                        // Coin Seller Transfer Row with 3D Icon
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clickable { onOpenAwardCoins() }
                                .padding(horizontal = 16.dp, vertical = 14.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Box(
                                    modifier = Modifier
                                        .size(38.dp)
                                        .clip(CircleShape)
                                        .background(if (isDark) Color(0xFF16331C) else Color(0xFFE8F5E9)),
                                    contentAlignment = Alignment.Center
                                ) {
                                    CoinSeller3DIcon(size = 28.dp)
                                }
                                Spacer(modifier = Modifier.width(14.dp))
                                Column {
                                    Text(
                                        text = "Coin Seller Portal",
                                        fontSize = 16.sp,
                                        fontWeight = FontWeight.Bold,
                                        color = colors.textPrimary
                                    )
                                    Text(
                                        text = "Distribute coins from your seller balance",
                                        fontSize = 12.sp,
                                        color = colors.textSecondary
                                    )
                                }
                            }

                            Icon(
                                imageVector = Icons.AutoMirrored.Filled.ArrowForwardIos,
                                contentDescription = "Go",
                                tint = colors.textSecondary,
                                modifier = Modifier.size(16.dp)
                            )
                        }
                    }

                    HorizontalDivider(color = colors.divider)

                    // Settings Row
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable { onOpenSettings() }
                            .padding(horizontal = 16.dp, vertical = 14.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Box(
                                modifier = Modifier
                                    .size(38.dp)
                                    .clip(CircleShape)
                                    .background(if (isDark) Color(0xFF222228) else Color(0xFFF3F4F6)),
                                contentAlignment = Alignment.Center
                            ) {
                                Settings3DIcon(size = 26.dp)
                            }
                            Spacer(modifier = Modifier.width(14.dp))
                            Text(
                                text = "Settings",
                                fontSize = 16.sp,
                                fontWeight = FontWeight.Bold,
                                color = colors.textPrimary
                            )
                        }

                        Icon(
                            imageVector = Icons.AutoMirrored.Filled.ArrowForwardIos,
                            contentDescription = "Go",
                            tint = colors.textSecondary,
                            modifier = Modifier.size(16.dp)
                        )
                    }
                }
            }

            Spacer(modifier = Modifier.height(28.dp))
        }
    }

    // Sticky Header with User's Name (Appears on scroll with smooth fade in / fade out)
    AnimatedVisibility(
        visible = scrollState.value > 60,
        enter = fadeIn(animationSpec = androidx.compose.animation.core.tween(250)),
        exit = fadeOut(animationSpec = androidx.compose.animation.core.tween(200)),
        modifier = Modifier
            .align(Alignment.TopCenter)
            .fillMaxWidth()
    ) {
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .shadow(
                    elevation = 6.dp,
                    spotColor = Color(0xFF00C853).copy(alpha = 0.35f)
                )
                .background(
                    if (isDark) {
                        Brush.horizontalGradient(
                            listOf(
                                Color(0xFF09120B),
                                Color(0xFF0E1A11),
                                Color(0xFF132417),
                                Color(0xFF09120B)
                            )
                        )
                    } else {
                        Brush.horizontalGradient(
                            listOf(
                                Color(0xFF009639), // Deep Emerald
                                Color(0xFF00B04A), // Jewel Jade
                                Color(0xFF00C853), // Vivid Emerald
                                Color(0xFF26E06D)  // Mint Emerald
                            )
                        )
                    }
                )
        ) {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .statusBarsPadding()
                    .height(52.dp)
                    .padding(horizontal = 20.dp),
                contentAlignment = Alignment.Center
            ) {
                Text(
                    text = if (liveName.isNotBlank()) liveName else "Me",
                    fontSize = 18.sp,
                    fontWeight = FontWeight.ExtraBold,
                    color = Color.White,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    textAlign = TextAlign.Center
                )
            }
        }
    }
}
}

@Composable
private fun StatItem(
    count: String,
    label: String,
    textColor: Color,
    labelColor: Color,
    testTag: String = "",
    onClick: () -> Unit = {}
) {
    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        modifier = Modifier
            .clickable { onClick() }
            .padding(horizontal = 8.dp, vertical = 4.dp)
            .testTag(testTag)
    ) {
        Text(
            text = count,
            fontSize = 18.sp,
            fontWeight = FontWeight.Bold,
            color = textColor
        )
        Spacer(modifier = Modifier.height(2.dp))
        Text(
            text = label,
            fontSize = 12.sp,
            color = labelColor
        )
    }
}

@Composable
private fun GridTile(
    title: String,
    iconBg: Color,
    textColor: Color,
    iconContent: @Composable () -> Unit,
    onClick: () -> Unit
) {
    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        modifier = Modifier
            .clickable { onClick() }
            .padding(vertical = 4.dp)
    ) {
        Box(
            modifier = Modifier
                .size(52.dp)
                .clip(RoundedCornerShape(18.dp))
                .background(iconBg),
            contentAlignment = Alignment.Center
        ) {
            iconContent()
        }
        Spacer(modifier = Modifier.height(8.dp))
        Text(
            text = title,
            fontSize = 12.sp,
            fontWeight = FontWeight.Medium,
            color = textColor
        )
    }
}

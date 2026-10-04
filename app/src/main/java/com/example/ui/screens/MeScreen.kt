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
import androidx.compose.material.icons.filled.LocationOn
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.Tune
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Security
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.Shield
import androidx.compose.material.icons.filled.ShoppingBag
import androidx.compose.material.icons.filled.Store
import androidx.compose.material.icons.filled.SupervisorAccount
import androidx.compose.material.icons.filled.Group
import androidx.compose.material.icons.filled.Verified
import androidx.compose.material.icons.filled.Visibility
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Card
import com.example.ui.components.MyData3DIcon
import com.example.ui.components.TaskCenterClipboard3DIcon
import com.example.ui.components.MeScreenMintWaveAtmosphere
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
            .background(Color(0xFF381A05))
            .testTag("me_screen_root")
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .verticalScroll(scrollState)
        ) {
            // Top Section with Sunset Orange-Yellow Gradient Header Only
            Box(
                modifier = Modifier
                    .fillMaxWidth()
            ) {
                // Sunset Orange-Yellow Glow Overlay at Top Header (Exact user request)
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(265.dp)
                        .background(
                            Brush.verticalGradient(
                                listOf(
                                    Color(0x90E65100), // Vibrant deep sunset orange
                                    Color(0x50FF9100), // Amber mid glow
                                    Color(0x20FFD54F), // Soft gold aura
                                    Color.Transparent
                                )
                            )
                        )
                )

                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .statusBarsPadding()
                        .padding(horizontal = 16.dp, vertical = 8.dp)
                ) {
                    // 1. Top Header Row: "Me" title + Frosted Coin & Moon/Diamond balance pills
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(top = 4.dp, bottom = 14.dp),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            text = "Me",
                            fontSize = 28.sp,
                            fontWeight = FontWeight.Black,
                            fontStyle = androidx.compose.ui.text.font.FontStyle.Italic,
                            color = Color.White
                        )

                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            // Bag Button Pill [ 🎒 Bag ]
                            Surface(
                                shape = RoundedCornerShape(18.dp),
                                color = Color.White.copy(alpha = 0.1f),
                                modifier = Modifier.clickable { onOpenBag() }
                            ) {
                                Row(
                                    modifier = Modifier.padding(horizontal = 10.dp, vertical = 5.dp),
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Bag3DIcon(size = 17.dp)
                                    Spacer(modifier = Modifier.width(5.dp))
                                    Text(
                                        text = "Bag",
                                        fontSize = 13.sp,
                                        fontWeight = FontWeight.Bold,
                                        color = Color.White
                                    )
                                }
                            }

                            // Coin Balance Capsule [ 🪙 <coins> ▶ ]
                            Surface(
                                shape = RoundedCornerShape(18.dp),
                                color = Color.White.copy(alpha = 0.1f),
                                modifier = Modifier.clickable { onOpenWallet() }
                            ) {
                                Row(
                                    modifier = Modifier.padding(horizontal = 10.dp, vertical = 5.dp),
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Coin3DIcon(size = 17.dp)
                                    Spacer(modifier = Modifier.width(5.dp))
                                    Text(
                                        text = numberFormat.format(liveCoins),
                                        fontSize = 13.sp,
                                        fontWeight = FontWeight.Bold,
                                        color = Color.White
                                    )
                                    Spacer(modifier = Modifier.width(4.dp))
                                    Icon(
                                        imageVector = Icons.Default.PlayArrow,
                                        contentDescription = null,
                                        tint = Color.White,
                                        modifier = Modifier.size(10.dp)
                                    )
                                }
                            }

                            // Moon / Diamond Balance Capsule [ 🌙 <diamonds> ▶ ]
                            Surface(
                                shape = RoundedCornerShape(18.dp),
                                color = Color.White.copy(alpha = 0.1f),
                                modifier = Modifier.clickable { onOpenIncome() }
                            ) {
                                Row(
                                    modifier = Modifier.padding(horizontal = 10.dp, vertical = 5.dp),
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Text("🌙", fontSize = 13.sp)
                                    Spacer(modifier = Modifier.width(5.dp))
                                    Text(
                                        text = numberFormat.format(liveDiamonds),
                                        fontSize = 13.sp,
                                        fontWeight = FontWeight.Bold,
                                        color = Color.White
                                    )
                                    Spacer(modifier = Modifier.width(4.dp))
                                    Icon(
                                        imageVector = Icons.Default.PlayArrow,
                                        contentDescription = null,
                                        tint = Color.White,
                                        modifier = Modifier.size(10.dp)
                                    )
                                }
                            }
                        }
                    }

                    // 2. Profile Section (Using screen background directly, no background pad/card)
                    Column(modifier = Modifier.fillMaxWidth().padding(horizontal = 4.dp, vertical = 4.dp)) {
                        // Location on Top Right
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.End,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Icon(
                                imageVector = Icons.Default.LocationOn,
                                contentDescription = null,
                                tint = Color.White,
                                modifier = Modifier.size(14.dp)
                            )
                            Spacer(modifier = Modifier.width(4.dp))
                            Text(
                                text = if (liveCountry.isNotBlank()) liveCountry else "Kenya",
                                fontSize = 13.sp,
                                fontWeight = FontWeight.Bold,
                                color = Color.White
                            )
                        }

                            Spacer(modifier = Modifier.height(6.dp))

                            // Avatar + Name + ID + Edit Button Row
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                // Avatar with circular glow border
                                Box(
                                    modifier = Modifier
                                        .size(66.dp)
                                        .clip(CircleShape)
                                        .border(2.dp, Brush.linearGradient(listOf(Color(0xFF00E5FF), Color(0xFFFFD54F))), CircleShape)
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
                                        },
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
                                        contentScale = androidx.compose.ui.layout.ContentScale.Crop
                                    )
                                }

                                Spacer(modifier = Modifier.width(14.dp))

                                Column(modifier = Modifier.weight(1f)) {
                                    Text(
                                        text = if (liveName.isNotBlank()) liveName else "Munene",
                                        fontSize = 20.sp,
                                        fontWeight = FontWeight.ExtraBold,
                                        color = Color.White
                                    )
                                    Spacer(modifier = Modifier.height(4.dp))
                                    Row(
                                        verticalAlignment = Alignment.CenterVertically,
                                        modifier = Modifier.clickable {
                                            val clipboard = context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
                                            clipboard.setPrimaryClip(ClipData.newPlainText("User ID", displayId))
                                            AppToast.show("Copied ID: $displayId")
                                        }
                                    ) {
                                        Text(
                                            text = "ID:$displayId",
                                            fontSize = 12.5.sp,
                                            fontWeight = FontWeight.Medium,
                                            color = Color.White.copy(alpha = 0.75f)
                                        )
                                        Spacer(modifier = Modifier.width(4.dp))
                                        Icon(
                                            imageVector = Icons.Default.ContentCopy,
                                            contentDescription = "Copy ID",
                                            tint = Color.White.copy(alpha = 0.75f),
                                            modifier = Modifier.size(13.dp)
                                        )
                                    }
                                }

                                // Edit Button on Right
                                Row(
                                    verticalAlignment = Alignment.CenterVertically,
                                    modifier = Modifier
                                        .clickable { onOpenEditProfile() }
                                        .padding(4.dp)
                                ) {
                                    Icon(
                                        imageVector = Icons.Default.Edit,
                                        contentDescription = "Edit Profile",
                                        tint = Color.White,
                                        modifier = Modifier.size(16.dp)
                                    )
                                    Spacer(modifier = Modifier.width(3.dp))
                                    Text(
                                        text = "Edit",
                                        color = Color.White,
                                        fontSize = 13.sp,
                                        fontWeight = FontWeight.Bold
                                    )
                                }
                            }

                            Spacer(modifier = Modifier.height(16.dp))

                            // Level & Store Capsules Row
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.spacedBy(8.dp)
                            ) {
                                // My Level Capsule [ My Level  ♦ <level>   ♦ 0   > ]
                                Surface(
                                    shape = RoundedCornerShape(16.dp),
                                    color = Color.White.copy(alpha = 0.1f),
                                    modifier = Modifier
                                        .weight(1f)
                                        .clickable { onOpenLevel() }
                                ) {
                                    Row(
                                        modifier = Modifier.padding(horizontal = 10.dp, vertical = 7.dp),
                                        verticalAlignment = Alignment.CenterVertically,
                                        horizontalArrangement = Arrangement.SpaceBetween
                                    ) {
                                        Row(verticalAlignment = Alignment.CenterVertically) {
                                            Text(
                                                text = "My Level",
                                                color = Color(0xFF9E86F8),
                                                fontSize = 11.5.sp,
                                                fontWeight = FontWeight.Bold
                                            )
                                            Spacer(modifier = Modifier.width(6.dp))
                                            Surface(
                                                shape = RoundedCornerShape(8.dp),
                                                color = Color(0x33FFFFFF)
                                            ) {
                                                Text(
                                                    text = "♦ ${userLevelInfo.level}",
                                                    color = Color.White,
                                                    fontSize = 10.sp,
                                                    fontWeight = FontWeight.Bold,
                                                    modifier = Modifier.padding(horizontal = 5.dp, vertical = 2.dp)
                                                )
                                            }
                                            Spacer(modifier = Modifier.width(3.dp))
                                            Surface(
                                                shape = RoundedCornerShape(8.dp),
                                                color = Color(0x33FFFFFF)
                                            ) {
                                                Text(
                                                    text = "♦ 0",
                                                    color = Color.White,
                                                    fontSize = 10.sp,
                                                    fontWeight = FontWeight.Bold,
                                                    modifier = Modifier.padding(horizontal = 5.dp, vertical = 2.dp)
                                                )
                                            }
                                        }

                                        Icon(
                                            imageVector = Icons.AutoMirrored.Filled.ArrowForwardIos,
                                            contentDescription = null,
                                            tint = Color(0xFF9E86F8),
                                            modifier = Modifier.size(10.dp)
                                        )
                                    }
                                }

                                // Store Capsule [ Store  🏎️ 🛡️ 🏅  > ]
                                Surface(
                                    shape = RoundedCornerShape(16.dp),
                                    color = Color.White.copy(alpha = 0.1f),
                                    modifier = Modifier
                                        .weight(1f)
                                        .clickable { onOpenStore() }
                                ) {
                                    Row(
                                        modifier = Modifier.padding(horizontal = 10.dp, vertical = 7.dp),
                                        verticalAlignment = Alignment.CenterVertically,
                                        horizontalArrangement = Arrangement.SpaceBetween
                                    ) {
                                        Row(verticalAlignment = Alignment.CenterVertically) {
                                            Text(
                                                text = "Store",
                                                color = Color(0xFFFFB74D),
                                                fontSize = 11.5.sp,
                                                fontWeight = FontWeight.Bold
                                            )
                                            Spacer(modifier = Modifier.width(6.dp))
                                            Text("🏎️", fontSize = 13.sp)
                                            Text("🛡️", fontSize = 13.sp)
                                            Text("🏅", fontSize = 13.sp)
                                        }

                                        Icon(
                                            imageVector = Icons.AutoMirrored.Filled.ArrowForwardIos,
                                            contentDescription = null,
                                            tint = Color(0xFFFFB74D),
                                            modifier = Modifier.size(10.dp)
                                        )
                                    }
                                }
                            }

                            Spacer(modifier = Modifier.height(16.dp))

                            // Social Stats Row (Friends 0, Following 0, Followers 0)
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceEvenly,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Row(
                                    modifier = Modifier.clickable { onOpenFriends() },
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Text("Friends", color = Color(0xFFA0A5BA), fontSize = 12.5.sp)
                                    Spacer(modifier = Modifier.width(5.dp))
                                    Text("${followStats.friendsCount}", color = Color.White, fontSize = 14.sp, fontWeight = FontWeight.Bold)
                                }

                                Row(
                                    modifier = Modifier.clickable { onOpenFollowing() },
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Text("Following", color = Color(0xFFA0A5BA), fontSize = 12.5.sp)
                                    Spacer(modifier = Modifier.width(5.dp))
                                    Text("${followStats.followingCount}", color = Color.White, fontSize = 14.sp, fontWeight = FontWeight.Bold)
                                }

                                Row(
                                    modifier = Modifier.clickable { onOpenFollowers() },
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Text("Followers", color = Color(0xFFA0A5BA), fontSize = 12.5.sp)
                                    Spacer(modifier = Modifier.width(5.dp))
                                    Text("${followStats.followersCount}", color = Color.White, fontSize = 14.sp, fontWeight = FontWeight.Bold)
                                }
                            }
                        }
                    }
                }

            // Lower Section: Feature Cards & Settings Group
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp, vertical = 14.dp)
            ) {
                // 3. Feature Grid Cards (2 columns, dark rounded tiles without border lines)
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    // Card 1: My Data (3D Pie Chart)
                    Surface(
                        shape = RoundedCornerShape(18.dp),
                        color = Color(0xFF151724),
                        modifier = Modifier
                            .weight(1f)
                            .height(105.dp)
                            .clickable { onOpenVisitors() }
                    ) {
                        Column(
                            modifier = Modifier.fillMaxSize().padding(12.dp),
                            horizontalAlignment = Alignment.CenterHorizontally,
                            verticalArrangement = Arrangement.Center
                        ) {
                            MyData3DIcon(size = 40.dp)
                            Spacer(modifier = Modifier.height(8.dp))
                            Text(
                                text = "My Data",
                                fontSize = 13.sp,
                                fontWeight = FontWeight.SemiBold,
                                color = Color.White
                            )
                        }
                    }

                    // Card 2: Task Center (3D Checklist Clipboard)
                    Surface(
                        shape = RoundedCornerShape(18.dp),
                        color = Color(0xFF151724),
                        modifier = Modifier
                            .weight(1f)
                            .height(105.dp)
                            .clickable { onOpenTaskCenter() }
                    ) {
                        Column(
                            modifier = Modifier.fillMaxSize().padding(12.dp),
                            horizontalAlignment = Alignment.CenterHorizontally,
                            verticalArrangement = Arrangement.Center
                        ) {
                            TaskCenterClipboard3DIcon(size = 40.dp)
                            Spacer(modifier = Modifier.height(8.dp))
                            Text(
                                text = "Task Center",
                                fontSize = 13.sp,
                                fontWeight = FontWeight.SemiBold,
                                color = Color.White
                            )
                        }
                    }
                }

                // Card 3: Join Agency (Appears ONLY in Female accounts, or Agency Center for agents)
                if (isFemaleAccount || isAgent) {
                    Spacer(modifier = Modifier.height(12.dp))

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(12.dp)
                    ) {
                        Surface(
                            shape = RoundedCornerShape(18.dp),
                            color = Color(0xFF151724),
                            modifier = Modifier
                                .weight(1f)
                                .height(72.dp)
                                .clickable { onOpenAgency() }
                        ) {
                            Row(
                                modifier = Modifier.fillMaxSize().padding(horizontal = 16.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Icon(
                                    imageVector = Icons.Default.Group,
                                    contentDescription = null,
                                    tint = Color.White,
                                    modifier = Modifier.size(24.dp)
                                )
                                Spacer(modifier = Modifier.width(12.dp))
                                Text(
                                    text = if (isAgent) "Agency Center" else "Join Agency",
                                    fontSize = 14.sp,
                                    fontWeight = FontWeight.Bold,
                                    color = Color.White
                                )
                            }
                        }

                        // Blank balanced cell
                        Box(modifier = Modifier.weight(1f))
                    }
                }

                Spacer(modifier = Modifier.height(20.dp))

                // 4. List Menu Settings Group (Dark Rounded Container with dividers, no border lines)
                Surface(
                    shape = RoundedCornerShape(20.dp),
                    color = Color.White.copy(alpha = 0.07f),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Column(modifier = Modifier.padding(vertical = 4.dp)) {
                        // 1. Store (Moved above Support)
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clickable { onOpenStore() }
                                .padding(horizontal = 16.dp, vertical = 16.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Icon(
                                    imageVector = Icons.Default.Store,
                                    contentDescription = "Store",
                                    tint = Color(0xFFFFB74D),
                                    modifier = Modifier.size(20.dp)
                                )
                                Spacer(modifier = Modifier.width(14.dp))
                                Text(
                                    text = "Store",
                                    fontSize = 15.sp,
                                    fontWeight = FontWeight.Medium,
                                    color = Color.White
                                )
                                Spacer(modifier = Modifier.width(8.dp))
                                Text("🏎️ 🛡️ 🏅", fontSize = 12.sp)
                            }

                            Icon(
                                imageVector = Icons.AutoMirrored.Filled.ArrowForwardIos,
                                contentDescription = "Go",
                                tint = Color.White.copy(alpha = 0.5f),
                                modifier = Modifier.size(14.dp)
                            )
                        }

                        HorizontalDivider(color = Color(0x10FFFFFF))

                        // 2. My Level (Moved above Support)
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clickable { onOpenLevel() }
                                .padding(horizontal = 16.dp, vertical = 16.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Icon(
                                    imageVector = Icons.Default.EmojiEvents,
                                    contentDescription = "My Level",
                                    tint = Color(0xFF9E86F8),
                                    modifier = Modifier.size(20.dp)
                                )
                                Spacer(modifier = Modifier.width(14.dp))
                                Text(
                                    text = "My Level",
                                    fontSize = 15.sp,
                                    fontWeight = FontWeight.Medium,
                                    color = Color.White
                                )
                                Spacer(modifier = Modifier.width(8.dp))
                                Surface(
                                    shape = RoundedCornerShape(8.dp),
                                    color = Color(0x33FFFFFF)
                                ) {
                                    Text(
                                        text = "♦ ${userLevelInfo.level}",
                                        color = Color.White,
                                        fontSize = 11.sp,
                                        fontWeight = FontWeight.Bold,
                                        modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                                    )
                                }
                            }

                            Icon(
                                imageVector = Icons.AutoMirrored.Filled.ArrowForwardIos,
                                contentDescription = "Go",
                                tint = Color.White.copy(alpha = 0.5f),
                                modifier = Modifier.size(14.dp)
                            )
                        }

                        HorizontalDivider(color = Color(0x18FFFFFF))

                        // 3. Support
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clickable { onOpenSupport() }
                                .padding(horizontal = 16.dp, vertical = 16.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Support3DIcon(size = 22.dp)
                                Spacer(modifier = Modifier.width(14.dp))
                                Text(
                                    text = "Support",
                                    fontSize = 15.sp,
                                    fontWeight = FontWeight.Medium,
                                    color = Color.White
                                )
                            }

                            Icon(
                                imageVector = Icons.AutoMirrored.Filled.ArrowForwardIos,
                                contentDescription = "Go",
                                tint = Color.White.copy(alpha = 0.5f),
                                modifier = Modifier.size(14.dp)
                            )
                        }

                        HorizontalDivider(color = Color(0x18FFFFFF))

                        // 4. Verify identity (Changed from Login & security)
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clickable { onOpenVerify() }
                                .padding(horizontal = 16.dp, vertical = 16.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Icon(
                                    imageVector = Icons.Default.Shield,
                                    contentDescription = "Verify identity",
                                    tint = Color.White,
                                    modifier = Modifier.size(20.dp)
                                )
                                Spacer(modifier = Modifier.width(14.dp))
                                Text(
                                    text = "Verify identity",
                                    fontSize = 15.sp,
                                    fontWeight = FontWeight.Medium,
                                    color = Color.White
                                )
                            }

                            Icon(
                                imageVector = Icons.AutoMirrored.Filled.ArrowForwardIos,
                                contentDescription = "Go",
                                tint = Color.White.copy(alpha = 0.5f),
                                modifier = Modifier.size(14.dp)
                            )
                        }

                        HorizontalDivider(color = Color(0x10FFFFFF))

                        // 5. Bag (NEW)
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clickable { onOpenBag() }
                                .padding(horizontal = 16.dp, vertical = 16.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Icon(
                                    imageVector = Icons.Default.ShoppingBag,
                                    contentDescription = "Bag",
                                    tint = Color(0xFFFFD54F),
                                    modifier = Modifier.size(20.dp)
                                )
                                Spacer(modifier = Modifier.width(14.dp))
                                Text(
                                    text = "Bag",
                                    fontSize = 15.sp,
                                    fontWeight = FontWeight.Medium,
                                    color = Color.White
                                )
                            }

                            Icon(
                                imageVector = Icons.AutoMirrored.Filled.ArrowForwardIos,
                                contentDescription = "Go",
                                tint = Color.White.copy(alpha = 0.5f),
                                modifier = Modifier.size(14.dp)
                            )
                        }

                        HorizontalDivider(color = Color(0x10FFFFFF))

                        // 6. Reports & Moderation (Restricted to Admin)
                        if (isAdmin) {
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .clip(RoundedCornerShape(12.dp))
                                    .background(Color.White.copy(alpha = 0.05f))
                                    .clickable { onOpenManageReports() }
                                    .padding(horizontal = 16.dp, vertical = 16.dp),
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.SpaceBetween
                            ) {
                                Row(verticalAlignment = Alignment.CenterVertically) {
                                    Icon(
                                        imageVector = Icons.Default.Flag,
                                        contentDescription = "Reports & Moderation",
                                        tint = QivoOrange,
                                        modifier = Modifier.size(20.dp)
                                    )
                                    Spacer(modifier = Modifier.width(14.dp))
                                    Text(
                                        text = "Reports & Moderation",
                                        fontSize = 15.sp,
                                        fontWeight = FontWeight.Medium,
                                        color = Color.White
                                    )
                                }

                                Icon(
                                    imageVector = Icons.AutoMirrored.Filled.ArrowForwardIos,
                                    contentDescription = "Go",
                                    tint = Color.White.copy(alpha = 0.5f),
                                    modifier = Modifier.size(14.dp)
                                )
                            }
                            HorizontalDivider(color = Color(0x10FFFFFF))
                        }

                        // 7. Admin Center (Restricted to Admin)
                        if (isAdmin) {
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .clip(RoundedCornerShape(12.dp))
                                    .background(Color.White.copy(alpha = 0.05f))
                                    .clickable { onOpenAdminAnalytics() }
                                    .padding(horizontal = 16.dp, vertical = 16.dp),
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.SpaceBetween
                            ) {
                                Row(verticalAlignment = Alignment.CenterVertically) {
                                    Icon(
                                        imageVector = Icons.Default.AdminPanelSettings,
                                        contentDescription = "Admin Center",
                                        tint = QivoOrange,
                                        modifier = Modifier.size(20.dp)
                                    )
                                    Spacer(modifier = Modifier.width(14.dp))
                                    Text(
                                        text = "Admin Center",
                                        fontSize = 15.sp,
                                        fontWeight = FontWeight.Medium,
                                        color = Color.White
                                    )
                                }

                                Icon(
                                    imageVector = Icons.AutoMirrored.Filled.ArrowForwardIos,
                                    contentDescription = "Go",
                                    tint = Color.White.copy(alpha = 0.5f),
                                    modifier = Modifier.size(14.dp)
                                )
                            }
                            HorizontalDivider(color = Color(0x10FFFFFF))
                        }

                        // 7. App Settings
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clickable { onOpenSettings() }
                                .padding(horizontal = 16.dp, vertical = 16.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Icon(
                                    imageVector = Icons.Default.Settings,
                                    contentDescription = "App Settings",
                                    tint = Color.White,
                                    modifier = Modifier.size(20.dp)
                                )
                                Spacer(modifier = Modifier.width(14.dp))
                                Text(
                                    text = "App Settings",
                                    fontSize = 15.sp,
                                    fontWeight = FontWeight.Medium,
                                    color = Color.White
                                )
                            }

                            Icon(
                                imageVector = Icons.AutoMirrored.Filled.ArrowForwardIos,
                                contentDescription = "Go",
                                tint = Color.White.copy(alpha = 0.5f),
                                modifier = Modifier.size(14.dp)
                            )
                        }
                    }
                }

                // 5. ALL EXTRA BUTTONS (Placed at the very bottom below App Settings)
                if (isAdmin || isCoinSeller) {
                    Spacer(modifier = Modifier.height(20.dp))

                    Surface(
                        shape = RoundedCornerShape(20.dp),
                        color = Color(0xFF151724),
                        border = BorderStroke(1.dp, QivoOrange.copy(alpha = 0.4f)),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Column(modifier = Modifier.padding(16.dp)) {
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Row(verticalAlignment = Alignment.CenterVertically) {
                                    Icon(
                                        imageVector = Icons.Default.Shield,
                                        contentDescription = null,
                                        tint = QivoOrange,
                                        modifier = Modifier.size(20.dp)
                                    )
                                    Spacer(modifier = Modifier.width(8.dp))
                                    Text(
                                        text = if (isAdmin) "Executive Hub" else "Coin Seller Panel",
                                        fontSize = 15.sp,
                                        fontWeight = FontWeight.Bold,
                                        color = Color.White
                                    )
                                }

                                Surface(shape = RoundedCornerShape(6.dp), color = QivoOrange) {
                                    Text(
                                        text = if (isAdmin) "ADMIN" else "SELLER",
                                        color = Color.White,
                                        fontSize = 9.sp,
                                        fontWeight = FontWeight.Black,
                                        modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                                    )
                                }
                            }

                            Spacer(modifier = Modifier.height(12.dp))

                             Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.spacedBy(8.dp)
                            ) {
                                Surface(
                                    color = Color.White.copy(alpha = 0.12f),
                                    shape = RoundedCornerShape(12.dp),
                                    modifier = Modifier
                                        .weight(1f)
                                        .height(42.dp)
                                        .clickable { onOpenAwardCoins() }
                                ) {
                                    Box(contentAlignment = Alignment.Center) {
                                        Text(
                                            text = "Transfer",
                                            fontSize = 13.sp,
                                            fontWeight = FontWeight.Bold,
                                            color = Color.White
                                        )
                                    }
                                }
                                
                                if (isAdmin) {
                                    Surface(
                                        color = Color.White.copy(alpha = 0.12f),
                                        shape = RoundedCornerShape(12.dp),
                                        modifier = Modifier
                                            .weight(1f)
                                            .height(42.dp)
                                            .clickable { onOpenManageRoles() }
                                    ) {
                                        Box(contentAlignment = Alignment.Center) {
                                            Text(
                                                text = "Roles",
                                                fontSize = 13.sp,
                                                fontWeight = FontWeight.Bold,
                                                color = Color.White
                                            )
                                        }
                                    }
                                    
                                    Surface(
                                        color = Color.White.copy(alpha = 0.12f),
                                        shape = RoundedCornerShape(12.dp),
                                        modifier = Modifier
                                            .weight(1f)
                                            .height(42.dp)
                                            .clickable { onOpenAdminAnalytics() }
                                    ) {
                                        Box(contentAlignment = Alignment.Center) {
                                            Text(
                                                text = "Analytics",
                                                fontSize = 13.sp,
                                                fontWeight = FontWeight.Bold,
                                                color = Color.White
                                            )
                                        }
                                    }
                                }
                            }
                        }
                    }
                }

                Spacer(modifier = Modifier.height(90.dp))
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
                        spotColor = Color(0xFFFF6500).copy(alpha = 0.35f)
                    )
                    .background(
                        Brush.horizontalGradient(
                            listOf(
                                Color(0xFF381A05),
                                Color(0xFF241003),
                                Color(0xFF140801),
                                Color(0xFF381A05)
                            )
                        )
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

package com.example.ui.screens

import android.content.Context
import androidx.activity.compose.BackHandler
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.LocalFireDepartment
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
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
import com.example.data.NetworkUtils
import com.example.data.SupabaseProfileService
import com.example.data.UserSessionManager
import com.example.ui.components.App3DInlineSpinner
import com.example.ui.components.AppToast
import com.example.ui.components.Coin3DIcon
import com.example.ui.theme.AppTheme
import kotlinx.coroutines.launch

data class DayRewardItem(
    val dayLabel: String,
    val dayNumber: Int,
    val coins: Int,
    val isClaimed: Boolean = false,
    val isBonusDay: Boolean = false
)

/**
 * TaskCenterScreen:
 * A clean, simple, and beautiful rewards center focused purely on the
 * verified 7-day Daily Check-In reward system with real server synchronization.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun TaskCenterScreen(
    onBackClick: () -> Unit,
    onNavigateToVerification: () -> Unit = {}
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val scrollState = rememberScrollState()
    val profileService = remember { SupabaseProfileService() }

    BackHandler {
        onBackClick()
    }

    val session = UserSessionManager.getSession(context)
    val userId = session?.userId ?: "guest_user"
    val userEmail = session?.email ?: ""
    val todayDate = UserSessionManager.getTodayDateString()

    var userCoins by remember { mutableLongStateOf(UserSessionManager.getCoins(context)) }
    var isClaimedToday by remember { mutableStateOf(UserSessionManager.isClaimedToday(context, userId, userEmail)) }
    var currentDayNumber by remember { mutableIntStateOf(UserSessionManager.getLastCheckInDay(context, userId, userEmail)) }
    var isClaimingReward by remember { mutableStateOf(false) }
    var isCheckingServer by remember { mutableStateOf(true) }
    var isOnline by remember { mutableStateOf(NetworkUtils.isOnline(context)) }
    var showInfoDialog by remember { mutableStateOf(false) }

    // Fetch real profile from Supabase for coin balance, streak, and server-side claim verification
    LaunchedEffect(userId) {
        isOnline = NetworkUtils.isOnline(context)
        val localClaimed = UserSessionManager.isClaimedToday(context, userId, userEmail)
        if (localClaimed) {
            isClaimedToday = true
        }

        if (userId.isNotEmpty() && isOnline) {
            isCheckingServer = true
            try {
                val liveProfile = profileService.fetchProfile(userId, userEmail) ?: profileService.fetchProfileById(userId)
                if (liveProfile != null) {
                    userCoins = liveProfile.coins
                    UserSessionManager.saveCoins(context, liveProfile.coins)

                    val isServerClaimedToday = profileService.isDateMatchingToday(liveProfile.lastCheckinDate)
                    val isLocallyClaimed = localClaimed || UserSessionManager.isClaimedToday(context, userId, userEmail)

                    if (isServerClaimedToday || isLocallyClaimed) {
                        isClaimedToday = true
                        currentDayNumber = if (liveProfile.lastCheckinDay > 0) {
                            liveProfile.lastCheckinDay
                        } else {
                            val localDay = UserSessionManager.getLastCheckInDay(context, userId, userEmail)
                            if (localDay > 0) localDay else 1
                        }
                        val dateToSave = if (liveProfile.lastCheckinDate.isNotBlank()) liveProfile.lastCheckinDate else todayDate
                        UserSessionManager.saveDailyCheckIn(context, userId, dateToSave, currentDayNumber, userEmail)
                    } else {
                        currentDayNumber = liveProfile.lastCheckinDay
                        isClaimedToday = false
                    }
                }
            } catch (_: Exception) {}
            isCheckingServer = false
        } else {
            isCheckingServer = false
        }
    }

    // Determine the active day index (1 through 7)
    val activeDay = remember(isClaimedToday, currentDayNumber) {
        if (currentDayNumber == 0) {
            1
        } else if (isClaimedToday) {
            currentDayNumber
        } else {
            if (currentDayNumber >= 7) 1 else currentDayNumber + 1
        }
    }

    val rewardAmounts = listOf(10, 10, 10, 15, 20, 25, 30)

    val dailySchedule = remember(isClaimedToday, currentDayNumber, activeDay) {
        (1..7).map { dayNum ->
            val isClaimed = if (isClaimedToday) {
                dayNum <= currentDayNumber
            } else {
                dayNum < activeDay
            }
            DayRewardItem(
                dayLabel = "Day $dayNum",
                dayNumber = dayNum,
                coins = rewardAmounts[dayNum - 1],
                isClaimed = isClaimed,
                isBonusDay = dayNum == 7
            )
        }
    }

    val currentRewardToClaim = dailySchedule.firstOrNull { it.dayNumber == activeDay }
        ?: dailySchedule.first()

    fun claimCoins() {
        if (!NetworkUtils.isOnline(context)) {
            AppToast.show("Cannot claim rewards while offline. Please connect to the internet.")
            return
        }

        if (isClaimedToday) {
            AppToast.show("Already claimed for today! Come back tomorrow for the next reward.")
            return
        }

        scope.launch {
            isClaimingReward = true
            val coinsToAward = currentRewardToClaim.coins

            val localClaimed = UserSessionManager.isClaimedToday(context, userId, userEmail)
            if (localClaimed) {
                isClaimedToday = true
                isClaimingReward = false
                AppToast.show("Already claimed for today!")
                return@launch
            }

            val serverResult = profileService.claimDailyCheckin(
                userId = userId,
                dayNumber = activeDay,
                coinsToAward = coinsToAward,
                userEmail = userEmail,
                context = context
            )

            isClaimingReward = false

            if (serverResult.success) {
                val finalDay = if (serverResult.dayNumber > 0) serverResult.dayNumber else activeDay
                val finalCoins = if (serverResult.updatedCoins > 0L) serverResult.updatedCoins else (userCoins + coinsToAward)

                isClaimedToday = true
                currentDayNumber = finalDay
                userCoins = finalCoins

                UserSessionManager.saveDailyCheckIn(
                    context = context,
                    userId = userId,
                    dateStr = todayDate,
                    dayNumber = finalDay,
                    email = userEmail
                )
                UserSessionManager.saveCoins(context, finalCoins)

                AppToast.show("+$coinsToAward Coins Claimed! ✨")
            } else if (serverResult.isAlreadyClaimed) {
                isClaimedToday = true
                if (serverResult.updatedCoins > 0L) {
                    userCoins = serverResult.updatedCoins
                    UserSessionManager.saveCoins(context, serverResult.updatedCoins)
                }
                if (serverResult.dayNumber > 0) {
                    currentDayNumber = serverResult.dayNumber
                }
                UserSessionManager.saveDailyCheckIn(
                    context = context,
                    userId = userId,
                    dateStr = todayDate,
                    dayNumber = currentDayNumber,
                    email = userEmail
                )
                AppToast.show("Already claimed for today on your account.")
            } else {
                val errorMsg = if (serverResult.message.isNotBlank()) serverResult.message else "Server verification failed. Please try again."
                AppToast.show(errorMsg, isLong = true)
            }
        }
    }

    val colors = AppTheme.colors
    val isDark = colors.isDark

    val phoneTz = remember {
        val tz = java.util.TimeZone.getDefault()
        tz.getDisplayName(tz.inDaylightTime(java.util.Date()), java.util.TimeZone.SHORT)
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Text(
                        text = "Task Center",
                        fontWeight = FontWeight.Bold,
                        fontSize = 19.sp,
                        color = colors.textPrimary
                    )
                },
                navigationIcon = {
                    IconButton(
                        onClick = onBackClick,
                        modifier = Modifier.testTag("task_center_back_button")
                    ) {
                        Icon(
                            imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                            contentDescription = "Back",
                            tint = colors.textPrimary
                        )
                    }
                },
                actions = {
                    IconButton(
                        onClick = { showInfoDialog = true },
                        modifier = Modifier.testTag("task_center_info_button")
                    ) {
                        Icon(
                            imageVector = Icons.Default.Info,
                            contentDescription = "Rules & Info",
                            tint = colors.textSecondary
                        )
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(containerColor = colors.screenBg)
            )
        },
        containerColor = colors.screenBg
    ) { innerPadding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
                .padding(horizontal = 16.dp)
                .verticalScroll(scrollState)
                .testTag("task_center_root")
        ) {
            Spacer(modifier = Modifier.height(8.dp))

            // 1. SIMPLE & REFINED HERO COIN BALANCE CARD
            Card(
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(20.dp),
                colors = CardDefaults.cardColors(
                    containerColor = if (isDark) Color(0xFF1E1A29) else Color(0xFFFAF5FF)
                ),
                border = BorderStroke(
                    1.dp,
                    if (isDark) Color(0xFF352B4E) else Color(0xFFE9D5FF)
                )
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 20.dp, vertical = 18.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Column {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            Text(
                                text = "Coin Balance",
                                fontSize = 13.sp,
                                fontWeight = FontWeight.Medium,
                                color = colors.textSecondary
                            )

                            // Streak Badge
                            Surface(
                                shape = RoundedCornerShape(12.dp),
                                color = Color(0xFFFF9800).copy(alpha = 0.15f)
                            ) {
                                Row(
                                    verticalAlignment = Alignment.CenterVertically,
                                    modifier = Modifier.padding(horizontal = 8.dp, vertical = 2.dp),
                                    horizontalArrangement = Arrangement.spacedBy(4.dp)
                                ) {
                                    Icon(
                                        imageVector = Icons.Default.LocalFireDepartment,
                                        contentDescription = "Streak",
                                        tint = Color(0xFFFF9800),
                                        modifier = Modifier.size(13.dp)
                                    )
                                    Text(
                                        text = "${if (isClaimedToday) currentDayNumber else currentDayNumber.coerceAtLeast(0)}D Streak",
                                        fontSize = 11.sp,
                                        fontWeight = FontWeight.Bold,
                                        color = Color(0xFFFF9800)
                                    )
                                }
                            }
                        }

                        Spacer(modifier = Modifier.height(4.dp))

                        Row(
                            verticalAlignment = Alignment.Bottom,
                            horizontalArrangement = Arrangement.spacedBy(6.dp)
                        ) {
                            Text(
                                text = String.format("%,d", userCoins),
                                fontSize = 28.sp,
                                fontWeight = FontWeight.Black,
                                color = if (isDark) Color(0xFFFFD54F) else Color(0xFFB45309)
                            )
                            Text(
                                text = "coins",
                                fontSize = 14.sp,
                                fontWeight = FontWeight.Bold,
                                color = colors.textSecondary,
                                modifier = Modifier.padding(bottom = 3.dp)
                            )
                        }
                    }

                    Coin3DIcon(size = 50.dp)
                }
            }

            Spacer(modifier = Modifier.height(20.dp))

            // 2. DAILY CHECK-IN SECTION
            Card(
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(20.dp),
                colors = CardDefaults.cardColors(
                    containerColor = colors.cardBg
                ),
                border = BorderStroke(
                    1.dp,
                    if (isDark) colors.cardBorder else Color(0xFFEEEEEE)
                )
            ) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(18.dp)
                ) {
                    // Header with reset reminder
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Column {
                            Text(
                                text = "Daily Check-In",
                                fontSize = 16.sp,
                                fontWeight = FontWeight.Bold,
                                color = colors.textPrimary
                            )
                            Text(
                                text = "Check in every day to earn free coins",
                                fontSize = 12.sp,
                                color = colors.textSecondary
                            )
                        }

                        Surface(
                            shape = RoundedCornerShape(12.dp),
                            color = colors.accentOrange.copy(alpha = 0.12f)
                        ) {
                            Text(
                                text = "Resets 00:00 ($phoneTz)",
                                fontSize = 10.sp,
                                fontWeight = FontWeight.SemiBold,
                                color = colors.accentOrange,
                                modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp)
                            )
                        }
                    }

                    Spacer(modifier = Modifier.height(16.dp))

                    // 7-Day Matrix: 4 items top, 3 items bottom
                    // Row 1: Days 1 - 4
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        dailySchedule.take(4).forEach { item ->
                            SimpleDayTile(
                                item = item,
                                isCurrentDay = item.dayNumber == activeDay && !isClaimedToday,
                                isDark = isDark,
                                colors = colors,
                                modifier = Modifier.weight(1f)
                            )
                        }
                    }

                    Spacer(modifier = Modifier.height(8.dp))

                    // Row 2: Days 5 - 6 and Day 7 Bonus
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        dailySchedule.drop(4).take(2).forEach { item ->
                            SimpleDayTile(
                                item = item,
                                isCurrentDay = item.dayNumber == activeDay && !isClaimedToday,
                                isDark = isDark,
                                colors = colors,
                                modifier = Modifier.weight(1f)
                            )
                        }

                        // Day 7 Highlight Card
                        dailySchedule.lastOrNull()?.let { bonusItem ->
                            SimpleDay7Tile(
                                item = bonusItem,
                                isCurrentDay = bonusItem.dayNumber == activeDay && !isClaimedToday,
                                isDark = isDark,
                                colors = colors,
                                modifier = Modifier.weight(2f)
                            )
                        }
                    }

                    Spacer(modifier = Modifier.height(20.dp))

                    // Clean, Prominent Action Button
                    Button(
                        onClick = { claimCoins() },
                        enabled = isOnline && !isCheckingServer && !isClaimedToday && !isClaimingReward,
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(50.dp)
                            .testTag("btn_claim_daily_coins"),
                        colors = ButtonDefaults.buttonColors(
                            containerColor = Color(0xFFFFB300),
                            disabledContainerColor = if (isClaimedToday) {
                                if (isDark) Color(0xFF252530) else Color(0xFFF0F2F5)
                            } else {
                                if (isDark) Color(0xFF2C2C38) else Color(0xFFE2E8F0)
                            }
                        ),
                        shape = RoundedCornerShape(14.dp)
                    ) {
                        if (isClaimingReward) {
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(8.dp)
                            ) {
                                App3DInlineSpinner(size = 18.dp)
                                Text(
                                    text = "Claiming reward...",
                                    color = Color.Black,
                                    fontWeight = FontWeight.Bold,
                                    fontSize = 15.sp
                                )
                            }
                        } else if (isCheckingServer) {
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(8.dp)
                            ) {
                                App3DInlineSpinner(size = 16.dp)
                                Text(
                                    text = "Checking status...",
                                    color = colors.textMuted,
                                    fontWeight = FontWeight.Medium,
                                    fontSize = 14.sp
                                )
                            }
                        } else if (!isOnline) {
                            Text(
                                text = "Offline • Connect to Internet",
                                color = colors.textMuted,
                                fontWeight = FontWeight.SemiBold,
                                fontSize = 14.sp
                            )
                        } else if (isClaimedToday) {
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(6.dp)
                            ) {
                                Icon(
                                    imageVector = Icons.Default.Check,
                                    contentDescription = null,
                                    tint = if (isDark) Color(0xFF4ADE80) else Color(0xFF16A34A),
                                    modifier = Modifier.size(18.dp)
                                )
                                Text(
                                    text = "Claimed for Today",
                                    color = if (isDark) Color(0xFF4ADE80) else Color(0xFF16A34A),
                                    fontWeight = FontWeight.Bold,
                                    fontSize = 15.sp
                                )
                            }
                        } else {
                            Text(
                                text = "Claim ${currentRewardToClaim.coins} Coins",
                                color = Color.Black,
                                fontWeight = FontWeight.Bold,
                                fontSize = 15.sp
                            )
                        }
                    }
                }
            }

            Spacer(modifier = Modifier.height(24.dp))
        }
    }

    // Rules Dialog
    if (showInfoDialog) {
        AlertDialog(
            onDismissRequest = { showInfoDialog = false },
            title = {
                Text(
                    text = "Check-In Rules",
                    fontWeight = FontWeight.Bold,
                    fontSize = 18.sp,
                    color = colors.textPrimary
                )
            },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text(
                        text = "• Check in daily to build your consecutive day streak.",
                        fontSize = 13.sp,
                        color = colors.textSecondary
                    )
                    Text(
                        text = "• Day 7 grants a bonus of 30 coins, after which the 7-day loop restarts.",
                        fontSize = 13.sp,
                        color = colors.textSecondary
                    )
                    Text(
                        text = "• Daily rewards reset at midnight local time ($phoneTz).",
                        fontSize = 13.sp,
                        color = colors.textSecondary
                    )
                    Text(
                        text = "• An active internet connection is required to record your claim.",
                        fontSize = 13.sp,
                        color = colors.textSecondary
                    )
                }
            },
            confirmButton = {
                TextButton(onClick = { showInfoDialog = false }) {
                    Text("Understood", color = Color(0xFFFFB300), fontWeight = FontWeight.Bold)
                }
            },
            containerColor = colors.cardBg,
            shape = RoundedCornerShape(16.dp)
        )
    }
}

@Composable
private fun SimpleDayTile(
    item: DayRewardItem,
    isCurrentDay: Boolean,
    isDark: Boolean,
    colors: com.example.ui.theme.AppColors,
    modifier: Modifier = Modifier
) {
    val bgColor = when {
        item.isClaimed -> if (isDark) Color(0xFF1B1B22) else Color(0xFFF8F9FA)
        isCurrentDay -> if (isDark) Color(0xFF2A241A) else Color(0xFFFFFBEB)
        else -> if (isDark) Color(0xFF181820) else Color(0xFFFFFFFF)
    }

    val borderColor = when {
        isCurrentDay -> Color(0xFFFFB300)
        item.isClaimed -> if (isDark) Color(0xFF272733) else Color(0xFFE5E7EB)
        else -> if (isDark) Color(0xFF23232D) else Color(0xFFEEEEEE)
    }

    Box(
        modifier = modifier
            .height(84.dp)
            .clip(RoundedCornerShape(12.dp))
            .background(bgColor)
            .border(
                width = if (isCurrentDay) 1.5.dp else 1.dp,
                color = borderColor,
                shape = RoundedCornerShape(12.dp)
            )
            .padding(vertical = 8.dp, horizontal = 4.dp),
        contentAlignment = Alignment.Center
    ) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.SpaceBetween,
            modifier = Modifier.fillMaxHeight()
        ) {
            Text(
                text = item.dayLabel,
                fontSize = 11.sp,
                fontWeight = if (isCurrentDay) FontWeight.Bold else FontWeight.Medium,
                color = when {
                    item.isClaimed -> colors.textMuted
                    isCurrentDay -> Color(0xFFFFB300)
                    else -> colors.textSecondary
                }
            )

            if (item.isClaimed) {
                Box(
                    modifier = Modifier
                        .size(20.dp)
                        .clip(CircleShape)
                        .background(if (isDark) Color(0xFF1E3A2B) else Color(0xFFDCFCE7)),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        imageVector = Icons.Default.Check,
                        contentDescription = "Claimed",
                        tint = if (isDark) Color(0xFF4ADE80) else Color(0xFF16A34A),
                        modifier = Modifier.size(13.dp)
                    )
                }
            } else {
                Text(text = "🪙", fontSize = 16.sp)
            }

            Text(
                text = "+${item.coins}",
                fontSize = 11.sp,
                fontWeight = FontWeight.Bold,
                color = when {
                    item.isClaimed -> colors.textMuted
                    isCurrentDay -> Color(0xFFFFB300)
                    else -> colors.textPrimary
                }
            )
        }
    }
}

@Composable
private fun SimpleDay7Tile(
    item: DayRewardItem,
    isCurrentDay: Boolean,
    isDark: Boolean,
    colors: com.example.ui.theme.AppColors,
    modifier: Modifier = Modifier
) {
    val bgColor = when {
        item.isClaimed -> if (isDark) Color(0xFF1B1B22) else Color(0xFFF8F9FA)
        isCurrentDay -> if (isDark) Color(0xFF2E2412) else Color(0xFFFEF3C7)
        else -> if (isDark) Color(0xFF1E1B16) else Color(0xFFFFFBEB)
    }

    val borderColor = when {
        isCurrentDay -> Color(0xFFFFB300)
        item.isClaimed -> if (isDark) Color(0xFF272733) else Color(0xFFE5E7EB)
        else -> Color(0xFFFFB300).copy(alpha = 0.4f)
    }

    Box(
        modifier = modifier
            .height(84.dp)
            .clip(RoundedCornerShape(12.dp))
            .background(bgColor)
            .border(
                width = if (isCurrentDay) 1.5.dp else 1.dp,
                color = borderColor,
                shape = RoundedCornerShape(12.dp)
            )
            .padding(horizontal = 12.dp, vertical = 8.dp),
        contentAlignment = Alignment.Center
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Column(verticalArrangement = Arrangement.Center) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(4.dp)
                ) {
                    Text(
                        text = "Day 7",
                        fontSize = 12.sp,
                        fontWeight = FontWeight.ExtraBold,
                        color = when {
                            item.isClaimed -> colors.textMuted
                            else -> Color(0xFFFFB300)
                        }
                    )
                    Surface(
                        shape = RoundedCornerShape(4.dp),
                        color = Color(0xFFFFB300).copy(alpha = 0.2f)
                    ) {
                        Text(
                            text = "GRAND",
                            fontSize = 8.sp,
                            fontWeight = FontWeight.Bold,
                            color = Color(0xFFFFB300),
                            modifier = Modifier.padding(horizontal = 4.dp, vertical = 1.dp)
                        )
                    }
                }
                Spacer(modifier = Modifier.height(4.dp))
                Text(
                    text = "Weekly Bonus",
                    fontSize = 10.sp,
                    color = colors.textSecondary
                )
            }

            Column(
                horizontalAlignment = Alignment.End,
                verticalArrangement = Arrangement.Center
            ) {
                if (item.isClaimed) {
                    Box(
                        modifier = Modifier
                            .size(20.dp)
                            .clip(CircleShape)
                            .background(if (isDark) Color(0xFF1E3A2B) else Color(0xFFDCFCE7)),
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(
                            imageVector = Icons.Default.Check,
                            contentDescription = "Claimed",
                            tint = if (isDark) Color(0xFF4ADE80) else Color(0xFF16A34A),
                            modifier = Modifier.size(13.dp)
                        )
                    }
                } else {
                    Text(text = "🎁", fontSize = 16.sp)
                }
                Spacer(modifier = Modifier.height(2.dp))
                Text(
                    text = "+${item.coins} Coins",
                    fontSize = 11.sp,
                    fontWeight = FontWeight.Bold,
                    color = when {
                        item.isClaimed -> colors.textMuted
                        else -> Color(0xFFFFB300)
                    }
                )
            }
        }
    }
}

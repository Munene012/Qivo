package com.example.ui.screens

import android.widget.Toast
import com.example.data.NetworkUtils
import androidx.activity.compose.BackHandler
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.compose.foundation.BorderStroke
import androidx.compose.material.icons.filled.WifiOff
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.scaleOut
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Casino
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Star
import androidx.compose.material.icons.filled.VolumeUp
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.ScrollableTabRow
import androidx.compose.material3.Surface
import androidx.compose.material3.Tab
import androidx.compose.material3.TabRowDefaults
import androidx.compose.material3.TabRowDefaults.tabIndicatorOffset
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.drawscope.Fill
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.drawText
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import com.example.data.SupabaseProfileService
import com.example.data.UserSessionManager
import com.example.ui.components.App3DCoinIcon
import com.example.ui.components.InsufficientCoinsBottomSheet
import com.example.ui.theme.AppTheme
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlin.math.cos
import kotlin.math.sin
import kotlin.random.Random

enum class GameType {
    SPIN_WHEEL,
    COIN_FLIP,
    LUCKY_DICE,
    MYSTERY_CHEST
}

data class GameHistoryItem(
    val gameName: String,
    val betAmount: Long,
    val payoutAmount: Long,
    val isWin: Boolean,
    val timeFormatted: String
)

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun GameCenterScreen(
    onBackClick: () -> Unit,
    onOpenRechargeWallet: () -> Unit
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val colors = AppTheme.colors
    val isDark = colors.isDark

    val profileService = remember { SupabaseProfileService() }
    val isOnline by remember { NetworkUtils.observeNetworkConnectivity(context) }
        .collectAsStateWithLifecycle(initialValue = NetworkUtils.isOnline(context))
    val session = remember { UserSessionManager.getSession(context) }
    val userId = session?.userId ?: ""
    val userName = session?.name ?: "Player"

    var userCoins by remember { mutableLongStateOf(UserSessionManager.getCoins(context)) }
    var selectedGame by remember { mutableStateOf<GameType?>(null) }
    var selectedBet by remember { mutableLongStateOf(50L) }
    val betOptions = remember { listOf(10L, 20L, 50L, 100L, 200L, 500L, 1000L, 5000L) }

    var isPlayingGame by remember { mutableStateOf(false) }
    var showInsufficientCoinsDialog by remember { mutableStateOf(false) }
    var winResultDialogData by remember { mutableStateOf<Pair<Boolean, Long>?>(null) }
    val gameHistory = remember { mutableStateListOf<GameHistoryItem>() }

    // Synchronize latest coins from Supabase
    LaunchedEffect(userId) {
        if (userId.isNotBlank()) {
            val remoteCoins = profileService.fetchUserCoins(userId)
            if (remoteCoins >= 0) {
                userCoins = remoteCoins
                UserSessionManager.saveCoins(context, remoteCoins)
            }
        }
    }

    suspend fun processGameOutcome(bet: Long, multiplier: Double, gameName: String): Long {
        if (!NetworkUtils.isOnline(context)) {
            NetworkUtils.showToast(context, "No internet connection. Cannot play games while offline.")
            return 0L
        }
        val payout = (bet * multiplier).toLong()
        val netChange = payout - bet

        if (userId.isNotBlank()) {
            val res = profileService.adjustCoinsServer(
                userId = userId,
                amount = netChange,
                type = "GAME_OUTCOME",
                title = "$gameName Outcome",
                description = if (netChange >= 0) "Won $payout coins in $gameName" else "Bet $bet coins in $gameName"
            )
            if (res.first) {
                userCoins = res.second
                UserSessionManager.saveCoins(context, res.second)
            } else {
                NetworkUtils.showToast(context, "Failed to record game transaction on server.")
                return 0L
            }
        } else {
            val newBalance = (userCoins + netChange).coerceAtLeast(0L)
            userCoins = newBalance
            UserSessionManager.saveCoins(context, newBalance)
        }

        val isWin = payout > bet
        gameHistory.add(
            0,
            GameHistoryItem(
                gameName = gameName,
                betAmount = bet,
                payoutAmount = payout,
                isWin = isWin,
                timeFormatted = java.text.SimpleDateFormat("HH:mm:ss", java.util.Locale.getDefault()).format(java.util.Date())
            )
        )
        return payout
    }

    // Handle back button when in active game: return to all-games selection screen
    BackHandler(enabled = selectedGame != null && !isPlayingGame) {
        selectedGame = null
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text(
                            text = if (selectedGame == null) {
                                "Game Center"
                            } else {
                                when (selectedGame) {
                                    GameType.SPIN_WHEEL -> "🎡 Lucky Wheel"
                                    GameType.COIN_FLIP -> "🪙 Coin Flip"
                                    GameType.LUCKY_DICE -> "🎲 Lucky Dice"
                                    GameType.MYSTERY_CHEST -> "🎁 Mystery Chest"
                                    null -> "Game Center"
                                }
                            },
                            fontWeight = FontWeight.Black,
                            fontSize = if (selectedGame == null) 20.sp else 18.sp,
                            color = colors.textPrimary
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                        Surface(
                            shape = RoundedCornerShape(12.dp),
                            color = Color(0xFFFF9800)
                        ) {
                            Text(
                                text = if (selectedGame == null) "4 GAMES" else "WIN COINS",
                                color = Color.White,
                                fontSize = 10.sp,
                                fontWeight = FontWeight.Bold,
                                modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                            )
                        }
                    }
                },
                navigationIcon = {
                    IconButton(onClick = {
                        if (selectedGame != null) {
                            if (!isPlayingGame) selectedGame = null
                        } else {
                            onBackClick()
                        }
                    }) {
                        Icon(
                            imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                            contentDescription = if (selectedGame != null) "Back to Games" else "Back",
                            tint = colors.textPrimary
                        )
                    }
                },
                actions = {
                    // Quick "All Games" button when inside a specific game
                    if (selectedGame != null) {
                        Surface(
                            shape = RoundedCornerShape(14.dp),
                            color = if (isDark) Color(0xFF282834) else Color(0xFFEDE7F6),
                            modifier = Modifier
                                .padding(end = 8.dp)
                                .clickable(enabled = !isPlayingGame) {
                                    selectedGame = null
                                }
                        ) {
                            Text(
                                text = "All Games",
                                fontSize = 12.sp,
                                fontWeight = FontWeight.Bold,
                                color = if (isDark) Color(0xFFFFB74D) else Color(0xFF7C3AED),
                                modifier = Modifier.padding(horizontal = 10.dp, vertical = 6.dp)
                            )
                        }
                    }

                    // Coin balance pill with quick recharge button
                    Surface(
                        shape = RoundedCornerShape(20.dp),
                        color = if (isDark) Color(0xFF2C2411) else Color(0xFFFFF8E1),
                        border = androidx.compose.foundation.BorderStroke(
                            1.dp,
                            if (isDark) Color(0xFFFFD600).copy(alpha = 0.6f) else Color(0xFFFFE082)
                        ),
                        modifier = Modifier
                            .padding(end = 12.dp)
                            .clickable { onOpenRechargeWallet() }
                    ) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            modifier = Modifier.padding(horizontal = 10.dp, vertical = 4.dp)
                        ) {
                            App3DCoinIcon(size = 20.dp)
                            Spacer(modifier = Modifier.width(5.dp))
                            Text(
                                text = "%,d".format(userCoins),
                                fontWeight = FontWeight.ExtraBold,
                                fontSize = 14.sp,
                                color = if (isDark) Color(0xFFFFD600) else Color(0xFFE65100)
                            )
                            Spacer(modifier = Modifier.width(4.dp))
                            Box(
                                modifier = Modifier
                                    .size(18.dp)
                                    .clip(CircleShape)
                                    .background(Color(0xFFFF9800)),
                                contentAlignment = Alignment.Center
                            ) {
                                Icon(
                                    imageVector = Icons.Default.Add,
                                    contentDescription = "Recharge",
                                    tint = Color.White,
                                    modifier = Modifier.size(14.dp)
                                )
                            }
                        }
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = colors.screenBg
                )
            )
        },
        containerColor = colors.screenBg
    ) { innerPadding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
        ) {
            val currentGame = selectedGame
            if (currentGame == null) {
                // ALL GAMES SHOWN IN ONE SCREEN FOR USER TO SELECT
                LazyColumn(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(horizontal = 16.dp, vertical = 8.dp),
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    if (!isOnline) {
                        item {
                            Surface(
                                color = Color(0xFFFEF2F2),
                                shape = RoundedCornerShape(12.dp),
                                border = BorderStroke(1.dp, Color(0xFFFCA5A5)),
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(bottom = 12.dp)
                            ) {
                                Row(
                                    modifier = Modifier.padding(horizontal = 14.dp, vertical = 10.dp),
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Icon(
                                        imageVector = Icons.Default.WifiOff,
                                        contentDescription = null,
                                        tint = Color(0xFFDC2626),
                                        modifier = Modifier.size(20.dp)
                                    )
                                    Spacer(modifier = Modifier.width(10.dp))
                                    Text(
                                        text = "Offline Mode: Internet connection is required to play games and place bets.",
                                        fontSize = 12.sp,
                                        color = Color(0xFFB91C1C),
                                        fontWeight = FontWeight.Medium,
                                        lineHeight = 16.sp
                                    )
                                }
                            }
                        }
                    }

                    // Games Arena Hero Banner
                    item {
                        Card(
                            shape = RoundedCornerShape(20.dp),
                            colors = CardDefaults.cardColors(containerColor = Color.Transparent),
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(vertical = 4.dp)
                        ) {
                            Box(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .background(
                                        Brush.horizontalGradient(
                                            listOf(
                                                Color(0xFFFF8F00),
                                                Color(0xFFFF6F00),
                                                Color(0xFFE65100)
                                            )
                                        )
                                    )
                                    .padding(18.dp)
                            ) {
                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.SpaceBetween
                                ) {
                                    Column(modifier = Modifier.weight(1f)) {
                                        Surface(
                                            shape = RoundedCornerShape(8.dp),
                                            color = Color.White.copy(alpha = 0.25f)
                                        ) {
                                            Text(
                                                text = "🎮 GAMES HUB",
                                                fontSize = 10.sp,
                                                fontWeight = FontWeight.Black,
                                                color = Color.White,
                                                modifier = Modifier.padding(horizontal = 8.dp, vertical = 3.dp)
                                            )
                                        }
                                        Spacer(modifier = Modifier.height(8.dp))
                                        Text(
                                            text = "Select a Game to Play",
                                            fontSize = 20.sp,
                                            fontWeight = FontWeight.Black,
                                            color = Color.White
                                        )
                                        Spacer(modifier = Modifier.height(4.dp))
                                        Text(
                                            text = "Choose any game below to start playing. Win instant coin multipliers up to 10x!",
                                            fontSize = 12.sp,
                                            color = Color.White.copy(alpha = 0.95f),
                                            lineHeight = 16.sp
                                        )
                                    }
                                    Spacer(modifier = Modifier.width(12.dp))
                                    App3DCoinIcon(size = 54.dp)
                                }
                            }
                        }
                        Spacer(modifier = Modifier.height(14.dp))
                    }

                    // Section Title
                    item {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(bottom = 10.dp),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text(
                                text = "Available Games (4)",
                                fontSize = 16.sp,
                                fontWeight = FontWeight.Bold,
                                color = colors.textPrimary
                            )
                            Text(
                                text = "Select to play",
                                fontSize = 12.sp,
                                color = colors.textSecondary
                            )
                        }
                    }

                    // Game 1: Lucky Wheel
                    item {
                        GameSelectionCard(
                            title = "Lucky Wheel",
                            tag = "Up to 10x Win",
                            tagColor = Color(0xFFFF9800),
                            description = "Spin the fortune wheel with 8 multiplier segments to win up to 10x your bet!",
                            iconEmoji = "🎡",
                            features = listOf("8 Segments", "Max 10x", "High Multiplier"),
                            gradient = Brush.linearGradient(listOf(Color(0xFFFF8F00), Color(0xFFFF6F00))),
                            isDark = isDark,
                            colors = colors,
                            onClick = { selectedGame = GameType.SPIN_WHEEL }
                        )
                        Spacer(modifier = Modifier.height(10.dp))
                    }

                    // Game 2: Coin Flip
                    item {
                        GameSelectionCard(
                            title = "Coin Flip",
                            tag = "Instant 2x Win",
                            tagColor = Color(0xFFFFB300),
                            description = "Predict Heads or Tails for an ultra-fast double-or-nothing golden coin toss!",
                            iconEmoji = "🪙",
                            features = listOf("50/50 Odds", "Instant Payout", "Double Coins"),
                            gradient = Brush.linearGradient(listOf(Color(0xFFFFB300), Color(0xFFF57F17))),
                            isDark = isDark,
                            colors = colors,
                            onClick = { selectedGame = GameType.COIN_FLIP }
                        )
                        Spacer(modifier = Modifier.height(10.dp))
                    }

                    // Game 3: Lucky Dice
                    item {
                        GameSelectionCard(
                            title = "Lucky Dice",
                            tag = "Up to 6x Win",
                            tagColor = Color(0xFF10B981),
                            description = "Roll the lucky dice and guess the winning numbers or high/low sum total!",
                            iconEmoji = "🎲",
                            features = listOf("Dual Dice", "Guess Numbers", "Up to 6x"),
                            gradient = Brush.linearGradient(listOf(Color(0xFF059669), Color(0xFF047857))),
                            isDark = isDark,
                            colors = colors,
                            onClick = { selectedGame = GameType.LUCKY_DICE }
                        )
                        Spacer(modifier = Modifier.height(10.dp))
                    }

                    // Game 4: Mystery Chest
                    item {
                        GameSelectionCard(
                            title = "Mystery Chest",
                            tag = "Up to 8x Jackpot",
                            tagColor = Color(0xFF8B5CF6),
                            description = "Choose from 3 magical treasure chests hiding secret golden jackpot multipliers!",
                            iconEmoji = "🎁",
                            features = listOf("3 Chests", "Secret Multipliers", "Jackpot Hunt"),
                            gradient = Brush.linearGradient(listOf(Color(0xFF7C3AED), Color(0xFF6D28D9))),
                            isDark = isDark,
                            colors = colors,
                            onClick = { selectedGame = GameType.MYSTERY_CHEST }
                        )
                        Spacer(modifier = Modifier.height(20.dp))
                    }

                    // Recent Game History Section
                    item {
                        RecentGameRoundsSection(
                            gameHistory = gameHistory,
                            isDark = isDark,
                            colors = colors
                        )
                    }
                }
            } else {
                // ACTIVE GAME ARENA WITH GAME SWITCHER
                ScrollableTabRow(
                    selectedTabIndex = currentGame.ordinal,
                    containerColor = colors.screenBg,
                    contentColor = Color(0xFFFF9800),
                    edgePadding = 16.dp,
                    indicator = { tabPositions ->
                        TabRowDefaults.SecondaryIndicator(
                            modifier = Modifier.tabIndicatorOffset(tabPositions[currentGame.ordinal]),
                            color = Color(0xFFFF9800),
                            height = 3.dp
                        )
                    }
                ) {
                    Tab(
                        selected = currentGame == GameType.SPIN_WHEEL,
                        onClick = { if (!isPlayingGame) selectedGame = GameType.SPIN_WHEEL },
                        text = { Text("🎡 Lucky Wheel", fontWeight = FontWeight.Bold) }
                    )
                    Tab(
                        selected = currentGame == GameType.COIN_FLIP,
                        onClick = { if (!isPlayingGame) selectedGame = GameType.COIN_FLIP },
                        text = { Text("🪙 Coin Flip", fontWeight = FontWeight.Bold) }
                    )
                    Tab(
                        selected = currentGame == GameType.LUCKY_DICE,
                        onClick = { if (!isPlayingGame) selectedGame = GameType.LUCKY_DICE },
                        text = { Text("🎲 Lucky Dice", fontWeight = FontWeight.Bold) }
                    )
                    Tab(
                        selected = currentGame == GameType.MYSTERY_CHEST,
                        onClick = { if (!isPlayingGame) selectedGame = GameType.MYSTERY_CHEST },
                        text = { Text("🎁 Mystery Chest", fontWeight = FontWeight.Bold) }
                    )
                }

                LazyColumn(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(horizontal = 16.dp, vertical = 8.dp),
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    if (!isOnline) {
                        item {
                            Surface(
                                color = Color(0xFFFEF2F2),
                                shape = RoundedCornerShape(12.dp),
                                border = BorderStroke(1.dp, Color(0xFFFCA5A5)),
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(bottom = 12.dp)
                            ) {
                                Row(
                                    modifier = Modifier.padding(horizontal = 14.dp, vertical = 10.dp),
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Icon(
                                        imageVector = Icons.Default.WifiOff,
                                        contentDescription = null,
                                        tint = Color(0xFFDC2626),
                                        modifier = Modifier.size(20.dp)
                                    )
                                    Spacer(modifier = Modifier.width(10.dp))
                                    Text(
                                        text = "Offline Mode: Internet connection is required to play games and place bets.",
                                        fontSize = 12.sp,
                                        color = Color(0xFFB91C1C),
                                        fontWeight = FontWeight.Medium,
                                        lineHeight = 16.sp
                                    )
                                }
                            }
                        }
                    }

                    // Bet Amount Selector
                    item {
                        Spacer(modifier = Modifier.height(8.dp))
                        Card(
                            shape = RoundedCornerShape(16.dp),
                            colors = CardDefaults.cardColors(
                                containerColor = if (isDark) Color(0xFF1E1E24) else Color(0xFFF9F9FB)
                            ),
                            border = androidx.compose.foundation.BorderStroke(
                                1.dp,
                                if (isDark) Color(0xFF33333E) else Color(0xFFE0E0E0)
                            ),
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Column(modifier = Modifier.padding(12.dp)) {
                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.SpaceBetween,
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Text(
                                        text = "Select Bet Amount",
                                        fontSize = 13.sp,
                                        fontWeight = FontWeight.Bold,
                                        color = colors.textSecondary
                                    )
                                    Text(
                                        text = "%,d Coins".format(selectedBet),
                                        fontSize = 15.sp,
                                        fontWeight = FontWeight.Black,
                                        color = Color(0xFFFF9800)
                                    )
                                }
                                Spacer(modifier = Modifier.height(8.dp))
                                LazyRow(
                                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                                ) {
                                    items(betOptions) { bet ->
                                        val isSelected = selectedBet == bet
                                        Surface(
                                            shape = RoundedCornerShape(12.dp),
                                            color = if (isSelected) Color(0xFFFF9800) else (if (isDark) Color(0xFF2A2A35) else Color(0xFFEEEEEE)),
                                            modifier = Modifier.clickable(enabled = !isPlayingGame) {
                                                selectedBet = bet
                                            }
                                        ) {
                                            Text(
                                                text = "${bet}",
                                                color = if (isSelected) Color.White else colors.textPrimary,
                                                fontWeight = FontWeight.Bold,
                                                fontSize = 13.sp,
                                                modifier = Modifier.padding(horizontal = 14.dp, vertical = 6.dp)
                                            )
                                        }
                                    }
                                }
                            }
                        }
                        Spacer(modifier = Modifier.height(16.dp))
                    }

                    // Active Game Content
                    item {
                        when (currentGame) {
                            GameType.SPIN_WHEEL -> {
                                SpinWheelGame(
                                    userCoins = userCoins,
                                    betAmount = selectedBet,
                                    isPlaying = isPlayingGame,
                                    onPlayStart = {
                                        if (!NetworkUtils.requireOnline(context, "No internet connection. Cannot play games while offline.")) {
                                            false
                                        } else if (userCoins < selectedBet) {
                                            showInsufficientCoinsDialog = true
                                            false
                                        } else {
                                            isPlayingGame = true
                                            true
                                        }
                                    },
                                    onSpinFinished = { mult ->
                                        scope.launch {
                                            val payout = processGameOutcome(selectedBet, mult, "Lucky Wheel")
                                            isPlayingGame = false
                                            winResultDialogData = (mult > 0.0) to payout
                                        }
                                    }
                                )
                            }
                            GameType.COIN_FLIP -> {
                                CoinFlipGame(
                                    userCoins = userCoins,
                                    betAmount = selectedBet,
                                    isPlaying = isPlayingGame,
                                    onPlayStart = {
                                        if (!NetworkUtils.requireOnline(context, "No internet connection. Cannot play games while offline.")) {
                                            false
                                        } else if (userCoins < selectedBet) {
                                            showInsufficientCoinsDialog = true
                                            false
                                        } else {
                                            isPlayingGame = true
                                            true
                                        }
                                    },
                                    onFlipFinished = { won ->
                                        scope.launch {
                                            val mult = if (won) 2.0 else 0.0
                                            val payout = processGameOutcome(selectedBet, mult, "Coin Flip")
                                            isPlayingGame = false
                                            winResultDialogData = won to payout
                                        }
                                    }
                                )
                            }
                            GameType.LUCKY_DICE -> {
                                LuckyDiceGame(
                                    userCoins = userCoins,
                                    betAmount = selectedBet,
                                    isPlaying = isPlayingGame,
                                    onPlayStart = {
                                        if (!NetworkUtils.requireOnline(context, "No internet connection. Cannot play games while offline.")) {
                                            false
                                        } else if (userCoins < selectedBet) {
                                            showInsufficientCoinsDialog = true
                                            false
                                        } else {
                                            isPlayingGame = true
                                            true
                                        }
                                    },
                                    onDiceFinished = { won, mult ->
                                        scope.launch {
                                            val payout = processGameOutcome(selectedBet, mult, "Lucky Dice")
                                            isPlayingGame = false
                                            winResultDialogData = won to payout
                                        }
                                    }
                                )
                            }
                            GameType.MYSTERY_CHEST -> {
                                MysteryChestGame(
                                    userCoins = userCoins,
                                    betAmount = selectedBet,
                                    isPlaying = isPlayingGame,
                                    onPlayStart = {
                                        if (!NetworkUtils.requireOnline(context, "No internet connection. Cannot play games while offline.")) {
                                            false
                                        } else if (userCoins < selectedBet) {
                                            showInsufficientCoinsDialog = true
                                            false
                                        } else {
                                            isPlayingGame = true
                                            true
                                        }
                                    },
                                    onChestFinished = { mult ->
                                        scope.launch {
                                            val payout = processGameOutcome(selectedBet, mult, "Mystery Chest")
                                            isPlayingGame = false
                                            winResultDialogData = (mult > 0.0) to payout
                                        }
                                    }
                                )
                            }
                        }
                    }

                    // Recent Game History Section
                    item {
                        RecentGameRoundsSection(
                            gameHistory = gameHistory,
                            isDark = isDark,
                            colors = colors
                        )
                    }
                }
            }
        }
    }

    // Win / Loss Celebration Dialog
    val dialogData = winResultDialogData
    if (dialogData != null) {
        val (isWin, payout) = dialogData
        Dialog(
            onDismissRequest = { winResultDialogData = null },
            properties = DialogProperties(dismissOnClickOutside = true)
        ) {
            Surface(
                shape = RoundedCornerShape(24.dp),
                color = if (isDark) Color(0xFF201B2B) else Color.White,
                border = androidx.compose.foundation.BorderStroke(
                    2.dp,
                    if (isWin) Color(0xFFFFD600) else Color(0xFF757575)
                ),
                shadowElevation = 12.dp
            ) {
                Column(
                    modifier = Modifier.padding(24.dp),
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    Text(
                        text = if (isWin) "🎉 HUGE WIN! 🎉" else "😢 Better Luck Next Time",
                        fontSize = 20.sp,
                        fontWeight = FontWeight.Black,
                        color = if (isWin) Color(0xFFFFD600) else colors.textPrimary
                    )
                    Spacer(modifier = Modifier.height(12.dp))

                    if (isWin) {
                        App3DCoinIcon(size = 56.dp)
                        Spacer(modifier = Modifier.height(10.dp))
                        Text(
                            text = "+%,d Coins!".format(payout),
                            fontSize = 26.sp,
                            fontWeight = FontWeight.ExtraBold,
                            color = Color(0xFF4CAF50)
                        )
                        Spacer(modifier = Modifier.height(4.dp))
                        Text(
                            text = "Coins credited directly to your balance!",
                            fontSize = 12.sp,
                            color = colors.textSecondary
                        )
                    } else {
                        Text(
                            text = "Don't give up! Try again on the next spin.",
                            fontSize = 13.sp,
                            color = colors.textSecondary,
                            textAlign = androidx.compose.ui.text.style.TextAlign.Center
                        )
                    }

                    Spacer(modifier = Modifier.height(20.dp))

                    Surface(
                        onClick = { winResultDialogData = null },
                        shape = RoundedCornerShape(16.dp),
                        color = Color(0xFFFF9800),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Text(
                            text = if (isWin) "Collect & Play Again" else "Try Again",
                            color = Color.White,
                            fontWeight = FontWeight.Bold,
                            fontSize = 15.sp,
                            modifier = Modifier.padding(vertical = 12.dp),
                            textAlign = androidx.compose.ui.text.style.TextAlign.Center
                        )
                    }
                }
            }
        }
    }

    if (showInsufficientCoinsDialog) {
        InsufficientCoinsBottomSheet(
            currentCoins = userCoins,
            requiredCoins = selectedBet,
            onDismiss = { showInsufficientCoinsDialog = false },
            onOpenWallet = {
                showInsufficientCoinsDialog = false
                onOpenRechargeWallet()
            },
            onCoinsUpdated = { newBalance ->
                userCoins = newBalance
                UserSessionManager.saveCoins(context, newBalance)
            }
        )
    }
}

/**
 * Game 1: Lucky Spin Wheel
 */
@Composable
fun SpinWheelGame(
    userCoins: Long,
    betAmount: Long,
    isPlaying: Boolean,
    onPlayStart: () -> Boolean,
    onSpinFinished: (multiplier: Double) -> Unit
) {
    val scope = rememberCoroutineScope()
    val rotation = remember { Animatable(0f) }

    // Multipliers on 8 slices
    val slices = remember {
        listOf(
            0.0 to Color(0xFFE53935),     // 0x
            1.5 to Color(0xFF43A047),     // 1.5x
            0.5 to Color(0xFFFB8C00),     // 0.5x
            2.0 to Color(0xFF1E88E5),     // 2x
            0.0 to Color(0xFFE53935),     // 0x
            3.0 to Color(0xFF8E24AA),     // 3x
            5.0 to Color(0xFF00ACC1),     // 5x
            10.0 to Color(0xFFFFB300)     // 10x JACKPOT
        )
    }

    val textMeasurer = rememberTextMeasurer()

    Column(
        modifier = Modifier.fillMaxWidth(),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Box(
            modifier = Modifier
                .size(280.dp),
            contentAlignment = Alignment.Center
        ) {
            // Wheel Canvas
            Canvas(
                modifier = Modifier
                    .fillMaxSize()
                    .graphicsLayer {
                        rotationZ = rotation.value
                    }
            ) {
                val radius = size.minDimension / 2f
                val center = Offset(size.width / 2f, size.height / 2f)
                val sliceAngle = 360f / slices.size

                slices.forEachIndexed { index, (mult, color) ->
                    val startAngle = index * sliceAngle
                    drawArc(
                        color = color,
                        startAngle = startAngle,
                        sweepAngle = sliceAngle,
                        useCenter = true,
                        topLeft = Offset(center.x - radius, center.y - radius),
                        size = Size(radius * 2, radius * 2)
                    )

                    // Draw divider lines
                    val angleRad = Math.toRadians(startAngle.toDouble())
                    val endX = center.x + (radius * cos(angleRad)).toFloat()
                    val endY = center.y + (radius * sin(angleRad)).toFloat()
                    drawLine(
                        color = Color.White,
                        start = center,
                        end = Offset(endX, endY),
                        strokeWidth = 2.dp.toPx()
                    )

                    // Draw text label on slice
                    val midAngleRad = Math.toRadians((startAngle + sliceAngle / 2f).toDouble())
                    val textDist = radius * 0.65f
                    val textX = center.x + (textDist * cos(midAngleRad)).toFloat()
                    val textY = center.y + (textDist * sin(midAngleRad)).toFloat()

                    val label = if (mult == 0.0) "0x" else "${mult}x"
                    val measured = textMeasurer.measure(
                        text = label,
                        style = TextStyle(
                            color = Color.White,
                            fontSize = 14.sp,
                            fontWeight = FontWeight.Black
                        )
                    )
                    drawText(
                        textLayoutResult = measured,
                        topLeft = Offset(textX - measured.size.width / 2f, textY - measured.size.height / 2f)
                    )
                }

                // Outer Ring
                drawCircle(
                    color = Color(0xFFFFD600),
                    radius = radius,
                    center = center,
                    style = Stroke(width = 6.dp.toPx())
                )

                // Inner Hub
                drawCircle(
                    color = Color(0xFF212121),
                    radius = 24.dp.toPx(),
                    center = center
                )
                drawCircle(
                    color = Color(0xFFFFD600),
                    radius = 18.dp.toPx(),
                    center = center
                )
            }

            // Indicator Pointer at Top
            Canvas(
                modifier = Modifier
                    .size(36.dp)
                    .align(Alignment.TopCenter)
            ) {
                val path = Path().apply {
                    moveTo(size.width / 2f, size.height)
                    lineTo(0f, 0f)
                    lineTo(size.width, 0f)
                    close()
                }
                drawPath(path = path, color = Color.White)
                drawPath(path = path, color = Color(0xFFD32F2F), style = Fill)
            }
        }

        Spacer(modifier = Modifier.height(16.dp))

        // Spin Button
        Surface(
            onClick = {
                if (!isPlaying) {
                    if (onPlayStart()) {
                        scope.launch {
                            // Determine outcome randomly with weighted distribution
                            val winningIndex = when (Random.nextInt(100)) {
                                in 0..25 -> 0 // 0x
                                in 26..50 -> 4 // 0x
                                in 51..70 -> 2 // 0.5x
                                in 71..85 -> 1 // 1.5x
                                in 86..94 -> 3 // 2x
                                in 95..98 -> 5 // 3x
                                else -> 7 // 10x jackpot!
                            }

                            val sliceAngle = 360f / slices.size
                            val targetSliceAngle = -(winningIndex * sliceAngle + sliceAngle / 2f) - 90f
                            val totalRotations = 360f * (5 + Random.nextInt(3))
                            val targetRotation = rotation.value + totalRotations + (targetSliceAngle - (rotation.value % 360f))

                            rotation.animateTo(
                                targetValue = targetRotation,
                                animationSpec = tween(durationMillis = 4200, easing = FastOutSlowInEasing)
                            )
                            onSpinFinished(slices[winningIndex].first)
                        }
                    }
                }
            },
            shape = RoundedCornerShape(24.dp),
            color = if (isPlaying) Color.Gray else Color(0xFFFF9800),
            shadowElevation = 6.dp,
            modifier = Modifier
                .fillMaxWidth(0.75f)
                .height(50.dp)
        ) {
            Box(contentAlignment = Alignment.Center) {
                Text(
                    text = if (isPlaying) "SPINNING..." else "SPIN FOR %,d COINS".format(betAmount),
                    color = Color.White,
                    fontWeight = FontWeight.Black,
                    fontSize = 15.sp
                )
            }
        }
    }
}

/**
 * Game 2: Lucky 3D Coin Flip
 */
@Composable
fun CoinFlipGame(
    userCoins: Long,
    betAmount: Long,
    isPlaying: Boolean,
    onPlayStart: () -> Boolean,
    onFlipFinished: (won: Boolean) -> Unit
) {
    val scope = rememberCoroutineScope()
    var selectedSide by remember { mutableStateOf("HEADS") } // HEADS or TAILS
    val flipAnim = remember { Animatable(0f) }

    Column(
        modifier = Modifier.fillMaxWidth(),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        // 3D Flipping Coin
        Box(
            modifier = Modifier
                .size(160.dp)
                .graphicsLayer {
                    rotationY = flipAnim.value
                    cameraDistance = 12f * density
                },
            contentAlignment = Alignment.Center
        ) {
            val isShowingHeads = (flipAnim.value.toInt() / 180) % 2 == 0
            Surface(
                shape = CircleShape,
                color = Color(0xFFFFD600),
                border = androidx.compose.foundation.BorderStroke(4.dp, Color(0xFFFFAB00)),
                shadowElevation = 8.dp,
                modifier = Modifier.fillMaxSize()
            ) {
                Box(contentAlignment = Alignment.Center) {
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        Text(
                            text = if (isShowingHeads) "👑" else "⚡",
                            fontSize = 44.sp
                        )
                        Text(
                            text = if (isShowingHeads) "HEADS" else "TAILS",
                            fontSize = 16.sp,
                            fontWeight = FontWeight.Black,
                            color = Color(0xFF6D4C41)
                        )
                    }
                }
            }
        }

        Spacer(modifier = Modifier.height(20.dp))

        // Choice Selector (Heads or Tails)
        Row(
            modifier = Modifier.fillMaxWidth(0.85f),
            horizontalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Surface(
                onClick = { if (!isPlaying) selectedSide = "HEADS" },
                shape = RoundedCornerShape(16.dp),
                color = if (selectedSide == "HEADS") Color(0xFFFF9800) else Color(0xFF37474F),
                modifier = Modifier
                    .weight(1f)
                    .height(48.dp)
            ) {
                Box(contentAlignment = Alignment.Center) {
                    Text(
                        text = "👑 Pick Heads (2x)",
                        color = Color.White,
                        fontWeight = FontWeight.Bold,
                        fontSize = 13.sp
                    )
                }
            }
            Surface(
                onClick = { if (!isPlaying) selectedSide = "TAILS" },
                shape = RoundedCornerShape(16.dp),
                color = if (selectedSide == "TAILS") Color(0xFFFF9800) else Color(0xFF37474F),
                modifier = Modifier
                    .weight(1f)
                    .height(48.dp)
            ) {
                Box(contentAlignment = Alignment.Center) {
                    Text(
                        text = "⚡ Pick Tails (2x)",
                        color = Color.White,
                        fontWeight = FontWeight.Bold,
                        fontSize = 13.sp
                    )
                }
            }
        }

        Spacer(modifier = Modifier.height(16.dp))

        // Flip Button
        Surface(
            onClick = {
                if (!isPlaying) {
                    if (onPlayStart()) {
                        scope.launch {
                            val won = Random.nextBoolean()
                            val targetSide = if (won) selectedSide else (if (selectedSide == "HEADS") "TAILS" else "HEADS")
                            val isHeads = targetSide == "HEADS"
                            val finalDegrees = (360f * 6) + (if (isHeads) 0f else 180f)

                            flipAnim.snapTo(0f)
                            flipAnim.animateTo(
                                targetValue = finalDegrees,
                                animationSpec = tween(durationMillis = 2400, easing = FastOutSlowInEasing)
                            )
                            onFlipFinished(won)
                        }
                    }
                }
            },
            shape = RoundedCornerShape(24.dp),
            color = if (isPlaying) Color.Gray else Color(0xFFFF9800),
            shadowElevation = 6.dp,
            modifier = Modifier
                .fillMaxWidth(0.75f)
                .height(50.dp)
        ) {
            Box(contentAlignment = Alignment.Center) {
                Text(
                    text = if (isPlaying) "FLIPPING..." else "FLIP FOR %,d COINS".format(betAmount),
                    color = Color.White,
                    fontWeight = FontWeight.Black,
                    fontSize = 15.sp
                )
            }
        }
    }
}

/**
 * Game 3: Lucky Dice Roll
 */
@Composable
fun LuckyDiceGame(
    userCoins: Long,
    betAmount: Long,
    isPlaying: Boolean,
    onPlayStart: () -> Boolean,
    onDiceFinished: (won: Boolean, mult: Double) -> Unit
) {
    val scope = rememberCoroutineScope()
    var betType by remember { mutableStateOf("LOW") } // LOW (2-6, 2x), LUCKY7 (7, 5.8x), HIGH (8-12, 2x)
    var dice1 by remember { mutableIntStateOf(3) }
    var dice2 by remember { mutableIntStateOf(4) }
    val diceRotate = remember { Animatable(0f) }

    Column(
        modifier = Modifier.fillMaxWidth(),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        // Two 3D Dice Display
        Row(
            horizontalArrangement = Arrangement.spacedBy(20.dp),
            modifier = Modifier
                .graphicsLayer {
                    rotationZ = diceRotate.value
                }
        ) {
            DiceBox(diceValue = dice1)
            DiceBox(diceValue = dice2)
        }

        Spacer(modifier = Modifier.height(12.dp))

        Text(
            text = "Total Sum: ${dice1 + dice2}",
            fontSize = 18.sp,
            fontWeight = FontWeight.Black,
            color = Color(0xFFFF9800)
        )

        Spacer(modifier = Modifier.height(16.dp))

        // Bet Types
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            Surface(
                onClick = { if (!isPlaying) betType = "LOW" },
                shape = RoundedCornerShape(12.dp),
                color = if (betType == "LOW") Color(0xFFFF9800) else Color(0xFF37474F),
                modifier = Modifier.weight(1f)
            ) {
                Column(
                    modifier = Modifier.padding(vertical = 8.dp),
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    Text("LOW (2-6)", color = Color.White, fontWeight = FontWeight.Bold, fontSize = 12.sp)
                    Text("2.0x Payout", color = Color(0xFFFFE082), fontSize = 10.sp)
                }
            }
            Surface(
                onClick = { if (!isPlaying) betType = "LUCKY7" },
                shape = RoundedCornerShape(12.dp),
                color = if (betType == "LUCKY7") Color(0xFFFF9800) else Color(0xFF37474F),
                modifier = Modifier.weight(1f)
            ) {
                Column(
                    modifier = Modifier.padding(vertical = 8.dp),
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    Text("LUCKY 7", color = Color.White, fontWeight = FontWeight.Bold, fontSize = 12.sp)
                    Text("5.8x Payout", color = Color(0xFFFFE082), fontSize = 10.sp)
                }
            }
            Surface(
                onClick = { if (!isPlaying) betType = "HIGH" },
                shape = RoundedCornerShape(12.dp),
                color = if (betType == "HIGH") Color(0xFFFF9800) else Color(0xFF37474F),
                modifier = Modifier.weight(1f)
            ) {
                Column(
                    modifier = Modifier.padding(vertical = 8.dp),
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    Text("HIGH (8-12)", color = Color.White, fontWeight = FontWeight.Bold, fontSize = 12.sp)
                    Text("2.0x Payout", color = Color(0xFFFFE082), fontSize = 10.sp)
                }
            }
        }

        Spacer(modifier = Modifier.height(16.dp))

        // Roll Button
        Surface(
            onClick = {
                if (!isPlaying) {
                    if (onPlayStart()) {
                        scope.launch {
                            for (i in 0 until 10) {
                                dice1 = Random.nextInt(1, 7)
                                dice2 = Random.nextInt(1, 7)
                                diceRotate.snapTo(Random.nextFloat() * 40f - 20f)
                                delay(120)
                            }
                            val finalD1 = Random.nextInt(1, 7)
                            val finalD2 = Random.nextInt(1, 7)
                            dice1 = finalD1
                            dice2 = finalD2
                            diceRotate.animateTo(0f, tween(300))

                            val sum = finalD1 + finalD2
                            val (won, mult) = when {
                                betType == "LOW" && sum in 2..6 -> true to 2.0
                                betType == "LUCKY7" && sum == 7 -> true to 5.8
                                betType == "HIGH" && sum in 8..12 -> true to 2.0
                                else -> false to 0.0
                            }
                            onDiceFinished(won, mult)
                        }
                    }
                }
            },
            shape = RoundedCornerShape(24.dp),
            color = if (isPlaying) Color.Gray else Color(0xFFFF9800),
            shadowElevation = 6.dp,
            modifier = Modifier
                .fillMaxWidth(0.75f)
                .height(50.dp)
        ) {
            Box(contentAlignment = Alignment.Center) {
                Text(
                    text = if (isPlaying) "ROLLING..." else "ROLL FOR %,d COINS".format(betAmount),
                    color = Color.White,
                    fontWeight = FontWeight.Black,
                    fontSize = 15.sp
                )
            }
        }
    }
}

@Composable
fun DiceBox(diceValue: Int) {
    Surface(
        shape = RoundedCornerShape(16.dp),
        color = Color.White,
        border = androidx.compose.foundation.BorderStroke(2.5.dp, Color(0xFFD32F2F)),
        shadowElevation = 8.dp,
        modifier = Modifier.size(76.dp)
    ) {
        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(8.dp),
            contentAlignment = Alignment.Center
        ) {
            when (diceValue) {
                1 -> Dot(Alignment.Center)
                2 -> {
                    Dot(Alignment.TopStart)
                    Dot(Alignment.BottomEnd)
                }
                3 -> {
                    Dot(Alignment.TopStart)
                    Dot(Alignment.Center)
                    Dot(Alignment.BottomEnd)
                }
                4 -> {
                    Dot(Alignment.TopStart)
                    Dot(Alignment.TopEnd)
                    Dot(Alignment.BottomStart)
                    Dot(Alignment.BottomEnd)
                }
                5 -> {
                    Dot(Alignment.TopStart)
                    Dot(Alignment.TopEnd)
                    Dot(Alignment.Center)
                    Dot(Alignment.BottomStart)
                    Dot(Alignment.BottomEnd)
                }
                6 -> {
                    Dot(Alignment.TopStart)
                    Dot(Alignment.TopEnd)
                    Dot(Alignment.CenterStart)
                    Dot(Alignment.CenterEnd)
                    Dot(Alignment.BottomStart)
                    Dot(Alignment.BottomEnd)
                }
            }
        }
    }
}

@Composable
fun BoxScope.Dot(alignment: Alignment) {
    Box(
        modifier = Modifier
            .size(12.dp)
            .clip(CircleShape)
            .background(Color(0xFFD32F2F))
            .align(alignment)
    )
}

/**
 * Game 4: Mystery Treasure Chests
 */
@Composable
fun MysteryChestGame(
    userCoins: Long,
    betAmount: Long,
    isPlaying: Boolean,
    onPlayStart: () -> Boolean,
    onChestFinished: (mult: Double) -> Unit
) {
    val scope = rememberCoroutineScope()
    var pickedChest by remember { mutableStateOf<Int?>(null) }
    var chestRewards by remember { mutableStateOf(listOf(0.0, 0.0, 0.0)) }

    Column(
        modifier = Modifier.fillMaxWidth(),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Text(
            text = "Pick 1 of 3 Mystery Chests to unlock reward!",
            fontSize = 13.sp,
            color = Color.Gray,
            fontWeight = FontWeight.Medium
        )
        Spacer(modifier = Modifier.height(20.dp))

        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceEvenly
        ) {
            for (i in 0..2) {
                val isSelected = pickedChest == i
                Surface(
                    onClick = {
                        if (!isPlaying && pickedChest == null) {
                            if (onPlayStart()) {
                                scope.launch {
                                    pickedChest = i
                                    val rewards = when (Random.nextInt(100)) {
                                        in 0..30 -> listOf(0.0, 1.5, 3.0)
                                        in 31..65 -> listOf(1.5, 0.0, 2.0)
                                        in 66..90 -> listOf(2.0, 3.0, 0.0)
                                        else -> listOf(10.0, 1.5, 0.0)
                                    }.shuffled()
                                    chestRewards = rewards
                                    delay(1000)
                                    onChestFinished(rewards[i])
                                    delay(2000)
                                    pickedChest = null
                                }
                            }
                        }
                    },
                    shape = RoundedCornerShape(16.dp),
                    color = if (isSelected) Color(0xFFFFD600) else Color(0xFF424242),
                    shadowElevation = 6.dp,
                    modifier = Modifier.size(90.dp)
                ) {
                    Box(contentAlignment = Alignment.Center) {
                        if (pickedChest != null) {
                            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                                Text("🎁", fontSize = 28.sp)
                                Text(
                                    text = "${chestRewards[i]}x",
                                    fontSize = 14.sp,
                                    fontWeight = FontWeight.Black,
                                    color = if (chestRewards[i] > 0) Color(0xFF4CAF50) else Color(0xFFE53935)
                                )
                            }
                        } else {
                            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                                Text("🎁", fontSize = 32.sp)
                                Text("Chest #${i+1}", fontSize = 11.sp, color = Color.White)
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun GameSelectionCard(
    title: String,
    tag: String,
    tagColor: Color,
    description: String,
    iconEmoji: String,
    features: List<String>,
    gradient: Brush,
    isDark: Boolean,
    colors: com.example.ui.theme.AppColors,
    onClick: () -> Unit
) {
    Card(
        shape = RoundedCornerShape(18.dp),
        colors = CardDefaults.cardColors(
            containerColor = if (isDark) Color(0xFF1E1E26) else Color.White
        ),
        elevation = CardDefaults.cardElevation(defaultElevation = if (isDark) 3.dp else 2.dp),
        border = BorderStroke(
            1.dp,
            if (isDark) Color(0xFF333342) else Color(0xFFE6E6EC)
        ),
        modifier = Modifier
            .fillMaxWidth()
            .clickable { onClick() }
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Box(
                    modifier = Modifier
                        .size(54.dp)
                        .clip(RoundedCornerShape(16.dp))
                        .background(gradient),
                    contentAlignment = Alignment.Center
                ) {
                    Text(
                        text = iconEmoji,
                        fontSize = 28.sp
                    )
                }

                Spacer(modifier = Modifier.width(14.dp))

                Column(modifier = Modifier.weight(1f)) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween,
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Text(
                            text = title,
                            fontSize = 17.sp,
                            fontWeight = FontWeight.Black,
                            color = colors.textPrimary
                        )

                        Surface(
                            shape = RoundedCornerShape(8.dp),
                            color = tagColor.copy(alpha = 0.15f),
                            border = BorderStroke(1.dp, tagColor.copy(alpha = 0.5f))
                        ) {
                            Text(
                                text = tag,
                                fontSize = 11.sp,
                                fontWeight = FontWeight.ExtraBold,
                                color = tagColor,
                                modifier = Modifier.padding(horizontal = 8.dp, vertical = 3.dp)
                            )
                        }
                    }

                    Spacer(modifier = Modifier.height(4.dp))

                    Text(
                        text = description,
                        fontSize = 12.sp,
                        color = colors.textSecondary,
                        lineHeight = 16.sp
                    )
                }
            }

            Spacer(modifier = Modifier.height(12.dp))

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(
                    horizontalArrangement = Arrangement.spacedBy(6.dp),
                    modifier = Modifier.weight(1f, fill = false)
                ) {
                    features.forEach { feat ->
                        Surface(
                            shape = RoundedCornerShape(6.dp),
                            color = if (isDark) Color(0xFF282834) else Color(0xFFF1F1F5)
                        ) {
                            Text(
                                text = feat,
                                fontSize = 10.sp,
                                fontWeight = FontWeight.SemiBold,
                                color = colors.textSecondary,
                                modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                            )
                        }
                    }
                }

                Surface(
                    shape = RoundedCornerShape(10.dp),
                    color = Color(0xFFFF9800),
                    modifier = Modifier.clickable { onClick() }
                ) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        modifier = Modifier.padding(horizontal = 12.dp, vertical = 6.dp)
                    ) {
                        Text(
                            text = "Play",
                            color = Color.White,
                            fontWeight = FontWeight.Bold,
                            fontSize = 12.sp
                        )
                        Spacer(modifier = Modifier.width(4.dp))
                        Icon(
                            imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                            contentDescription = null,
                            tint = Color.White,
                            modifier = Modifier
                                .size(12.dp)
                                .rotate(180f)
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun RecentGameRoundsSection(
    gameHistory: List<GameHistoryItem>,
    isDark: Boolean,
    colors: com.example.ui.theme.AppColors
) {
    Spacer(modifier = Modifier.height(20.dp))
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(
            text = "Recent Game Rounds",
            fontSize = 15.sp,
            fontWeight = FontWeight.Bold,
            color = colors.textPrimary
        )
        if (gameHistory.isNotEmpty()) {
            Text(
                text = "${gameHistory.size} rounds",
                fontSize = 12.sp,
                color = colors.textSecondary
            )
        }
    }
    Spacer(modifier = Modifier.height(8.dp))

    if (gameHistory.isEmpty()) {
        Surface(
            shape = RoundedCornerShape(12.dp),
            color = if (isDark) Color(0xFF1B1B22) else Color(0xFFF5F5F7),
            modifier = Modifier.fillMaxWidth()
        ) {
            Text(
                text = "No game rounds yet. Pick a game and spin or roll to win coins!",
                color = colors.textSecondary,
                fontSize = 12.sp,
                modifier = Modifier.padding(16.dp),
                textAlign = androidx.compose.ui.text.style.TextAlign.Center
            )
        }
    } else {
        Column(
            verticalArrangement = Arrangement.spacedBy(6.dp),
            modifier = Modifier.fillMaxWidth()
        ) {
            gameHistory.take(6).forEach { item ->
                Surface(
                    shape = RoundedCornerShape(10.dp),
                    color = if (isDark) Color(0xFF22222B) else Color(0xFFF0F0F3),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 12.dp, vertical = 8.dp),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Column {
                            Text(
                                text = item.gameName,
                                fontSize = 13.sp,
                                fontWeight = FontWeight.Bold,
                                color = colors.textPrimary
                            )
                            Text(
                                text = "Bet: ${item.betAmount} at ${item.timeFormatted}",
                                fontSize = 11.sp,
                                color = colors.textSecondary
                            )
                        }
                        Text(
                            text = if (item.isWin) "+${item.payoutAmount} Coins" else "0 Coins",
                            fontSize = 13.sp,
                            fontWeight = FontWeight.ExtraBold,
                            color = if (item.isWin) Color(0xFF4CAF50) else Color(0xFFE53935)
                        )
                    }
                }
            }
        }
    }
    Spacer(modifier = Modifier.height(28.dp))
}

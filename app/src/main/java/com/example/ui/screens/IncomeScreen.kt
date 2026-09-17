package com.example.ui.screens
import com.example.ui.components.AppToast
import com.example.data.NetworkUtils
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.compose.material.icons.filled.WifiOff

import android.widget.Toast
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.ReceiptLong
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.History
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Shield
import androidx.compose.material.icons.filled.SwapHoriz
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Surface
import androidx.compose.material3.Tab
import androidx.compose.material3.TabRow
import androidx.compose.material3.TabRowDefaults
import androidx.compose.material3.TabRowDefaults.tabIndicatorOffset
import androidx.compose.material3.Text
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
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
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.drawscope.Fill
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import com.example.data.CoinTransaction
import com.example.data.DiamondTransaction
import com.example.data.SupabaseProfileService
import com.example.data.UserSessionManager
import com.example.ui.components.Coin3DIcon
import com.example.ui.components.Diamond3DIcon
import com.example.ui.components.DiamondHostReward3DIcon
import com.example.ui.theme.AppTheme
import com.example.ui.theme.QivoGoldLight
import com.example.ui.theme.QivoGoldDark
import kotlinx.coroutines.launch
import java.text.NumberFormat
import java.util.Locale

data class ExchangeTier(
    val coins: Long,
    val diamonds: Long,
    val label: String = "",
    val isPopular: Boolean = false
)

val DEFAULT_EXCHANGE_TIERS = listOf(
    ExchangeTier(coins = 90L, diamonds = 5000L, isPopular = true),
    ExchangeTier(coins = 180L, diamonds = 10000L),
    ExchangeTier(coins = 450L, diamonds = 25000L),
    ExchangeTier(coins = 900L, diamonds = 50000L),
    ExchangeTier(coins = 1800L, diamonds = 100000L),
    ExchangeTier(coins = 4500L, diamonds = 250000L),
    ExchangeTier(coins = 9000L, diamonds = 500000L),
    ExchangeTier(coins = 18000L, diamonds = 1000000L)
)

@OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class)
@Composable
fun IncomeScreen(
    userId: String,
    onBackClick: () -> Unit,
    onCoinsUpdated: (Long) -> Unit = {}
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val keyboardController = LocalSoftwareKeyboardController.current
    val profileService = remember { SupabaseProfileService() }
    val isOnline by remember { NetworkUtils.observeNetworkConnectivity(context) }
        .collectAsStateWithLifecycle(initialValue = NetworkUtils.isOnline(context))
    val colors = AppTheme.colors
    val isDark = colors.isDark

    var liveDiamonds by remember { mutableLongStateOf(UserSessionManager.getDiamonds(context)) }
    var liveCoins by remember { mutableLongStateOf(UserSessionManager.getCoins(context)) }
    var isRefreshing by remember { mutableStateOf(false) }
    var isConverting by remember { mutableStateOf(false) }

    var selectedTier by remember { mutableStateOf<ExchangeTier?>(DEFAULT_EXCHANGE_TIERS.firstOrNull { it.diamonds == 5000L }) }
    var customDiamondInput by remember { mutableStateOf("") }
    var isCustomMode by remember { mutableStateOf(false) }

    var showHistorySheet by remember { mutableStateOf(false) }
    val historySheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)

    val diamondHistoryList = remember { mutableStateListOf<DiamondTransaction>() }
    var isLoadingHistory by remember { mutableStateOf(false) }

    var showSuccessDialog by remember { mutableStateOf(false) }
    var lastConvertedCoins by remember { mutableLongStateOf(0L) }
    var lastConvertedDiamonds by remember { mutableLongStateOf(0L) }

    val numberFormat = remember { NumberFormat.getNumberInstance(Locale.US) }

    fun refreshBalances() {
        if (userId.isBlank()) return
        scope.launch {
            isRefreshing = true
            try {
                val serverDiamonds = profileService.fetchDiamonds(userId)
                val serverCoins = profileService.fetchCoins(userId)

                liveDiamonds = serverDiamonds
                liveCoins = serverCoins
                UserSessionManager.saveDiamonds(context, serverDiamonds)
                UserSessionManager.saveCoins(context, serverCoins)
                onCoinsUpdated(serverCoins)
            } catch (e: Exception) {
                e.printStackTrace()
            } finally {
                isRefreshing = false
            }
        }
    }

    fun loadDiamondHistory() {
        if (userId.isBlank()) return
        scope.launch {
            isLoadingHistory = true
            try {
                val list = profileService.fetchDiamondTransactions(userId)
                diamondHistoryList.clear()
                if (list.isNotEmpty()) {
                    diamondHistoryList.addAll(list)
                } else {
                    // Fallback to coin conversions if diamond table is fresh
                    val coinList = profileService.fetchCoinTransactions(userId)
                    val converted = coinList.filter { it.type.contains("DIAMOND", ignoreCase = true) }.map {
                        DiamondTransaction(
                            id = it.id,
                            userId = it.userId,
                            amount = -it.amount,
                            type = "EXCHANGE_COINS",
                            title = "Diamond Income Conversion",
                            description = it.description,
                            referenceId = it.referenceId,
                            status = it.status,
                            createdAt = it.createdAt
                        )
                    }
                    diamondHistoryList.addAll(converted)
                }
            } catch (e: Exception) {
                e.printStackTrace()
            } finally {
                isLoadingHistory = false
            }
        }
    }

    LaunchedEffect(userId) {
        refreshBalances()
        loadDiamondHistory()
    }

    val targetDiamondsToExchange: Long = remember(isCustomMode, customDiamondInput, selectedTier) {
        if (isCustomMode) {
            customDiamondInput.toLongOrNull() ?: 0L
        } else {
            selectedTier?.diamonds ?: 0L
        }
    }

    val calculatedCoinsToReceive: Long = remember(targetDiamondsToExchange) {
        if (targetDiamondsToExchange <= 0L) 0L else (targetDiamondsToExchange * 90L) / 5000L
    }

    val canExchange = isOnline &&
            targetDiamondsToExchange >= 5000L &&
            calculatedCoinsToReceive > 0L &&
            targetDiamondsToExchange <= liveDiamonds &&
            !isConverting

    fun executeExchange() {
        if (!NetworkUtils.requireOnline(context, "No internet connection. Cannot convert diamonds to coins while offline.")) {
            return
        }
        if (!canExchange) {
            if (targetDiamondsToExchange < 5000L) {
                AppToast.show("Minimum exchange amount is 5,000 Diamonds (90 Coins).")
            } else if (targetDiamondsToExchange > liveDiamonds) {
                AppToast.show("Insufficient diamond balance.")
            }
            return
        }

        keyboardController?.hide()
        scope.launch {
            isConverting = true
            try {
                val result = profileService.convertDiamondsToCoins(
                    userId = userId,
                    diamondsToConvert = targetDiamondsToExchange,
                    context = context
                )
                if (result.first) {
                    val newCoins = result.second.first
                    val newDiamonds = result.second.second
                    liveCoins = newCoins
                    liveDiamonds = newDiamonds
                    lastConvertedDiamonds = targetDiamondsToExchange
                    lastConvertedCoins = calculatedCoinsToReceive
                    if (isCustomMode) customDiamondInput = ""
                    onCoinsUpdated(newCoins)
                    showSuccessDialog = true
                    loadDiamondHistory()
                } else {
                    AppToast.show("Exchange declined by server. Please check balance and connection.")
                }
            } catch (e: Exception) {
                e.printStackTrace()
                com.example.data.NetworkUtils.showToast(context, "Exchange failed. Please try again.")
            } finally {
                isConverting = false
            }
        }
    }

    val mintGreenTop = Color(0xFF33DF89)
    val mintGreenDark = Color(0xFF22C55E)
    val lightSurfaceBg = if (isDark) Color(0xFF141A1F) else Color(0xFFFFFFFF)

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(
                Brush.verticalGradient(
                    listOf(
                        if (isDark) Color(0xFF0D3320) else mintGreenTop,
                        if (isDark) Color(0xFF082215) else mintGreenDark
                    )
                )
            )
            .testTag("income_screen")
    ) {
        // Polka dot pattern texture on the mint green background
        Canvas(modifier = Modifier.fillMaxSize()) {
            val dotRadius = 2.dp.toPx()
            val spacing = 22.dp.toPx()
            val dotColor = Color.White.copy(alpha = if (isDark) 0.04f else 0.18f)

            var y = 0f
            while (y < size.height * 0.45f) {
                var x = 0f
                while (x < size.width) {
                    drawCircle(
                        color = dotColor,
                        radius = dotRadius,
                        center = Offset(x, y)
                    )
                    x += spacing
                }
                y += spacing
            }
        }

        Column(modifier = Modifier.fillMaxSize()) {
            // 1. TOP APP BAR
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .statusBarsPadding()
                    .padding(horizontal = 12.dp, vertical = 8.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                // Back & Close buttons
                Row(verticalAlignment = Alignment.CenterVertically) {
                    IconButton(
                        onClick = onBackClick,
                        modifier = Modifier.size(40.dp)
                    ) {
                        Icon(
                            imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                            contentDescription = "Back",
                            tint = if (isDark) Color.White else Color(0xFF111827),
                            modifier = Modifier.size(24.dp)
                        )
                    }

                    IconButton(
                        onClick = onBackClick,
                        modifier = Modifier.size(40.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Default.Close,
                            contentDescription = "Close",
                            tint = if (isDark) Color.White else Color(0xFF111827),
                            modifier = Modifier.size(24.dp)
                        )
                    }
                }

                // Title: Income
                Text(
                    text = "Income",
                    fontSize = 20.sp,
                    fontWeight = FontWeight.ExtraBold,
                    color = if (isDark) Color.White else Color(0xFF111827)
                )

                // Top Right Action Buttons: Diamond History [=] & Refresh 🔄
                Row(verticalAlignment = Alignment.CenterVertically) {
                    // History icon
                    IconButton(
                        onClick = {
                            loadDiamondHistory()
                            showHistorySheet = true
                        },
                        modifier = Modifier.size(40.dp)
                    ) {
                        Icon(
                            imageVector = Icons.AutoMirrored.Filled.ReceiptLong,
                            contentDescription = "Diamond History",
                            tint = if (isDark) Color.White else Color(0xFF111827),
                            modifier = Modifier.size(26.dp)
                        )
                    }

                    // Refresh balance
                    IconButton(
                        onClick = { refreshBalances() },
                        modifier = Modifier.size(40.dp)
                    ) {
                        if (isRefreshing) {
                            CircularProgressIndicator(
                                modifier = Modifier.size(20.dp),
                                strokeWidth = 2.5.dp,
                                color = if (isDark) Color.White else Color(0xFF111827)
                            )
                        } else {
                            Icon(
                                imageVector = Icons.Default.Refresh,
                                contentDescription = "Refresh",
                                tint = if (isDark) Color.White else Color(0xFF111827),
                                modifier = Modifier.size(26.dp)
                            )
                        }
                    }
                }
            }

            Spacer(modifier = Modifier.height(10.dp))

            // 2. BALANCE HEADER (Bold italic Balance + Golden Diamond 💎 0)
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 24.dp)
            ) {
                Text(
                    text = "Balance",
                    fontStyle = FontStyle.Italic,
                    fontWeight = FontWeight.Black,
                    fontSize = 17.sp,
                    color = if (isDark) Color(0xFFE5E7EB) else Color(0xFF111827)
                )

                Spacer(modifier = Modifier.height(6.dp))

                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    Diamond3DIcon(size = 36.dp, animated = true)

                    Text(
                        text = numberFormat.format(liveDiamonds),
                        fontSize = 36.sp,
                        fontWeight = FontWeight.Black,
                        color = if (isDark) Color.White else Color(0xFF111827),
                        letterSpacing = (-0.5).sp
                    )
                }
            }

            Spacer(modifier = Modifier.height(24.dp))

            // 3. MAIN WHITE / DARK ROUNDED CONTAINER CARD
            Surface(
                modifier = Modifier
                    .fillMaxWidth()
                    .weight(1f),
                shape = RoundedCornerShape(topStart = 32.dp, topEnd = 32.dp),
                color = lightSurfaceBg,
                shadowElevation = 8.dp
            ) {
                LazyColumn(
                    modifier = Modifier.fillMaxSize(),
                    contentPadding = PaddingValues(horizontal = 20.dp, vertical = 22.dp)
                ) {
                    // Header inside sheet
                    item {
                        Column(modifier = Modifier.fillMaxWidth()) {
                            Text(
                                text = "Exchange Diamonds to Coins",
                                fontSize = 17.sp,
                                fontWeight = FontWeight.ExtraBold,
                                color = colors.textPrimary
                            )
                            Spacer(modifier = Modifier.height(3.dp))
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Text(
                                    text = "Official Rate: 5,000 💎 = 90 Coins",
                                    fontSize = 12.5.sp,
                                    fontWeight = FontWeight.SemiBold,
                                    color = if (isDark) Color(0xFF34D399) else Color(0xFF059669)
                                )
                                Spacer(modifier = Modifier.width(6.dp))
                                Icon(
                                    imageVector = Icons.Default.Lock,
                                    contentDescription = null,
                                    tint = if (isDark) Color(0xFF34D399) else Color(0xFF059669),
                                    modifier = Modifier.size(12.dp)
                                )
                                Text(
                                    text = "Server-Verified",
                                    fontSize = 11.sp,
                                    fontWeight = FontWeight.Bold,
                                    color = if (isDark) Color(0xFF34D399) else Color(0xFF059669)
                                )
                            }
                        }

                        Spacer(modifier = Modifier.height(18.dp))
                    }

                    if (!isOnline) {
                        item {
                            Surface(
                                color = Color(0xFFFEF2F2),
                                shape = RoundedCornerShape(12.dp),
                                border = BorderStroke(1.dp, Color(0xFFFCA5A5)),
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(bottom = 16.dp)
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
                                        text = "Offline Mode: Internet connection is required to convert diamonds to coins.",
                                        fontSize = 12.sp,
                                        color = Color(0xFFB91C1C),
                                        fontWeight = FontWeight.Medium,
                                        lineHeight = 16.sp
                                    )
                                }
                            }
                        }
                    }

                    // Preset Exchange Options Grid
                    item {
                        Text(
                            text = "Select Exchange Amount",
                            fontSize = 14.sp,
                            fontWeight = FontWeight.Bold,
                            color = colors.textSecondary
                        )

                        Spacer(modifier = Modifier.height(12.dp))

                        FlowRow(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(10.dp),
                            verticalArrangement = Arrangement.spacedBy(10.dp),
                            maxItemsInEachRow = 2
                        ) {
                            DEFAULT_EXCHANGE_TIERS.forEach { tier ->
                                val isSelected = !isCustomMode && selectedTier?.coins == tier.coins
                                val canAfford = liveDiamonds >= tier.diamonds

                                Surface(
                                    modifier = Modifier
                                        .weight(1f)
                                        .height(86.dp)
                                        .clickable {
                                            isCustomMode = false
                                            selectedTier = tier
                                        },
                                    shape = RoundedCornerShape(16.dp),
                                    color = when {
                                        isSelected -> if (isDark) Color(0xFF132F20) else Color(0xFFECFDF5)
                                        else -> if (isDark) Color(0xFF1E262E) else Color(0xFFF9FAFB)
                                    },
                                    border = BorderStroke(
                                        width = if (isSelected) 2.dp else 1.dp,
                                        color = when {
                                            isSelected -> if (isDark) Color(0xFF34D399) else Color(0xFF10B981)
                                            else -> if (isDark) Color(0xFF334155) else Color(0xFFE5E7EB)
                                        }
                                    ),
                                    shadowElevation = if (isSelected) 2.dp else 0.dp
                                ) {
                                    Box(modifier = Modifier.fillMaxSize().padding(10.dp)) {
                                        if (tier.isPopular) {
                                            Box(
                                                modifier = Modifier
                                                    .align(Alignment.TopEnd)
                                                    .clip(RoundedCornerShape(6.dp))
                                                    .background(
                                                        Brush.horizontalGradient(
                                                            listOf(Color(0xFFFF8F00), Color(0xFFFFB300))
                                                        )
                                                    )
                                                    .padding(horizontal = 6.dp, vertical = 2.dp)
                                            ) {
                                                Text(
                                                    text = "POPULAR",
                                                    fontSize = 8.5.sp,
                                                    fontWeight = FontWeight.Black,
                                                    color = Color.Black
                                                )
                                            }
                                        }

                                        Column(
                                            modifier = Modifier.fillMaxSize(),
                                            verticalArrangement = Arrangement.Center
                                        ) {
                                            Row(verticalAlignment = Alignment.CenterVertically) {
                                                Coin3DIcon(size = 22.dp)
                                                Spacer(modifier = Modifier.width(6.dp))
                                                Text(
                                                    text = "${numberFormat.format(tier.coins)} Coins",
                                                    fontSize = 15.sp,
                                                    fontWeight = FontWeight.ExtraBold,
                                                    color = if (isSelected) {
                                                        if (isDark) Color(0xFF34D399) else Color(0xFF047857)
                                                    } else colors.textPrimary
                                                )
                                            }

                                            Spacer(modifier = Modifier.height(4.dp))

                                            Row(verticalAlignment = Alignment.CenterVertically) {
                                                Text(
                                                    text = "Cost: 💎 ${numberFormat.format(tier.diamonds)}",
                                                    fontSize = 12.sp,
                                                    fontWeight = FontWeight.SemiBold,
                                                    color = if (canAfford) {
                                                        if (isDark) Color(0xFF94A3B8) else Color(0xFF64748B)
                                                    } else Color(0xFFEF4444)
                                                )
                                            }
                                        }
                                    }
                                }
                            }
                        }

                        Spacer(modifier = Modifier.height(18.dp))
                    }

                    // Custom Amount Input Card
                    item {
                        Surface(
                            modifier = Modifier.fillMaxWidth(),
                            shape = RoundedCornerShape(16.dp),
                            color = if (isDark) Color(0xFF1A222B) else Color(0xFFF8FAFC),
                            border = BorderStroke(
                                width = if (isCustomMode) 2.dp else 1.dp,
                                color = if (isCustomMode) {
                                    if (isDark) Color(0xFF34D399) else Color(0xFF10B981)
                                } else if (isDark) Color(0xFF334155) else Color(0xFFE2E8F0)
                            )
                        ) {
                            Column(modifier = Modifier.padding(14.dp)) {
                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.SpaceBetween,
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Text(
                                        text = "Or Enter Custom Diamonds Amount",
                                        fontSize = 13.5.sp,
                                        fontWeight = FontWeight.Bold,
                                        color = colors.textPrimary
                                    )

                                    if (liveDiamonds > 0) {
                                        Surface(
                                            onClick = {
                                                isCustomMode = true
                                                customDiamondInput = liveDiamonds.toString()
                                            },
                                            shape = RoundedCornerShape(8.dp),
                                            color = if (isDark) Color(0xFF0F2E1E) else Color(0xFFD1FAE5)
                                        ) {
                                            Text(
                                                text = "MAX",
                                                modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp),
                                                fontSize = 11.sp,
                                                fontWeight = FontWeight.ExtraBold,
                                                color = if (isDark) Color(0xFF34D399) else Color(0xFF059669)
                                            )
                                        }
                                    }
                                }

                                Spacer(modifier = Modifier.height(10.dp))

                                OutlinedTextField(
                                    value = customDiamondInput,
                                    onValueChange = { input ->
                                        val filtered = input.filter { it.isDigit() }
                                        customDiamondInput = filtered
                                        if (filtered.isNotEmpty()) {
                                            isCustomMode = true
                                        }
                                    },
                                    placeholder = {
                                        Text(
                                            "e.g. 5000",
                                            fontSize = 14.sp,
                                            color = colors.textSecondary
                                        )
                                    },
                                    leadingIcon = {
                                        Diamond3DIcon(size = 20.dp)
                                    },
                                    trailingIcon = {
                                        if (isCustomMode && customDiamondInput.isNotEmpty()) {
                                            IconButton(onClick = { customDiamondInput = "" }) {
                                                Icon(
                                                    Icons.Default.Close,
                                                    contentDescription = "Clear",
                                                    modifier = Modifier.size(18.dp),
                                                    tint = colors.textSecondary
                                                )
                                            }
                                        }
                                    },
                                    singleLine = true,
                                    keyboardOptions = KeyboardOptions(
                                        keyboardType = KeyboardType.Number,
                                        imeAction = ImeAction.Done
                                    ),
                                    keyboardActions = KeyboardActions(onDone = { keyboardController?.hide() }),
                                    shape = RoundedCornerShape(12.dp),
                                    colors = OutlinedTextFieldDefaults.colors(
                                        focusedBorderColor = if (isDark) Color(0xFF34D399) else Color(0xFF10B981),
                                        unfocusedBorderColor = if (isDark) Color(0xFF334155) else Color(0xFFCBD5E1),
                                        focusedContainerColor = if (isDark) Color(0xFF111827) else Color.White,
                                        unfocusedContainerColor = if (isDark) Color(0xFF111827) else Color.White
                                    ),
                                    modifier = Modifier.fillMaxWidth()
                                )

                                if (isCustomMode && targetDiamondsToExchange > 0) {
                                    Spacer(modifier = Modifier.height(8.dp))
                                    Row(
                                        modifier = Modifier.fillMaxWidth(),
                                        horizontalArrangement = Arrangement.SpaceBetween,
                                        verticalAlignment = Alignment.CenterVertically
                                    ) {
                                        Text(
                                            text = if (targetDiamondsToExchange < 5000L) "Min 5,000 💎 (Rate: 5000 💎 = 90 Coins)" else "You will receive:",
                                            fontSize = 12.sp,
                                            color = if (targetDiamondsToExchange < 5000L) Color(0xFFEF4444) else colors.textSecondary
                                        )
                                        Row(verticalAlignment = Alignment.CenterVertically) {
                                            Coin3DIcon(size = 16.dp)
                                            Spacer(modifier = Modifier.width(4.dp))
                                            Text(
                                                text = "${numberFormat.format(calculatedCoinsToReceive)} Coins",
                                                fontSize = 13.sp,
                                                fontWeight = FontWeight.Bold,
                                                color = if (isDark) Color(0xFFFFD54F) else Color(0xFFD97706)
                                            )
                                        }
                                    }
                                }
                            }
                        }

                        Spacer(modifier = Modifier.height(20.dp))
                    }

                    // Primary Exchange CTA Button
                    item {
                        Button(
                            onClick = { executeExchange() },
                            enabled = canExchange || (!isOnline && !isConverting),
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(54.dp)
                                .shadow(if (canExchange) 6.dp else 0.dp, RoundedCornerShape(16.dp)),
                            shape = RoundedCornerShape(16.dp),
                            colors = ButtonDefaults.buttonColors(
                                containerColor = if (!isOnline) Color(0xFFDC2626) else if (isDark) Color(0xFF22C55E) else Color(0xFF10B981),
                                disabledContainerColor = if (isDark) Color(0xFF1E293B) else Color(0xFFE2E8F0)
                            )
                        ) {
                            if (isConverting) {
                                CircularProgressIndicator(
                                    modifier = Modifier.size(22.dp),
                                    strokeWidth = 2.5.dp,
                                    color = Color.White
                                )
                            } else {
                                Row(
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.Center
                                ) {
                                    Icon(
                                        imageVector = if (!isOnline) Icons.Default.WifiOff else Icons.Default.SwapHoriz,
                                        contentDescription = null,
                                        modifier = Modifier.size(20.dp),
                                        tint = if (canExchange || !isOnline) Color.White else Color(0xFF94A3B8)
                                    )
                                    Spacer(modifier = Modifier.width(8.dp))
                                    Text(
                                        text = if (!isOnline) {
                                            "Offline • Internet Connection Required"
                                        } else if (targetDiamondsToExchange > 0) {
                                            "Exchange ${numberFormat.format(targetDiamondsToExchange)} 💎 for ${numberFormat.format(calculatedCoinsToReceive)} Coins"
                                        } else {
                                            "Select Amount to Exchange"
                                        },
                                        fontSize = 15.sp,
                                        fontWeight = FontWeight.ExtraBold,
                                        color = if (canExchange || !isOnline) Color.White else Color(0xFF94A3B8)
                                    )
                                }
                            }
                        }

                        Spacer(modifier = Modifier.height(14.dp))
                    }

                    // Security & Authenticated Processing Badge
                    item {
                        Surface(
                            modifier = Modifier.fillMaxWidth(),
                            shape = RoundedCornerShape(12.dp),
                            color = if (isDark) Color(0xFF1E262E) else Color(0xFFF1F5F9)
                        ) {
                            Row(
                                modifier = Modifier.padding(horizontal = 12.dp, vertical = 10.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Icon(
                                    imageVector = Icons.Default.Shield,
                                    contentDescription = null,
                                    tint = if (isDark) QivoGoldLight else QivoGoldDark,
                                    modifier = Modifier.size(18.dp)
                                )
                                Spacer(modifier = Modifier.width(8.dp))
                                Text(
                                    text = "All diamond conversions are ledgered with atomic PostgreSQL transaction integrity.",
                                    fontSize = 11.sp,
                                    color = colors.textSecondary,
                                    lineHeight = 15.sp
                                )
                            }
                        }
                    }
                }
            }
        }
    }

    // 4. DIAMOND HISTORY BOTTOM SHEET (Opens when user clicks top-right icon)
    if (showHistorySheet) {
        ModalBottomSheet(
            onDismissRequest = { showHistorySheet = false },
            sheetState = historySheetState,
            containerColor = if (isDark) Color(0xFF182028) else Color.White,
            shape = RoundedCornerShape(topStart = 28.dp, topEnd = 28.dp)
        ) {
            var historyFilter by remember { mutableIntStateOf(0) } // 0: All, 1: Earned, 2: Exchanged
            val filteredTransactions = remember(diamondHistoryList, historyFilter) {
                when (historyFilter) {
                    1 -> diamondHistoryList.filter { it.amount > 0 }
                    2 -> diamondHistoryList.filter { it.amount < 0 }
                    else -> diamondHistoryList
                }
            }

            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 20.dp, vertical = 8.dp)
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Diamond3DIcon(size = 24.dp)
                        Spacer(modifier = Modifier.width(8.dp))
                        Text(
                            text = "Diamond History",
                            fontSize = 18.sp,
                            fontWeight = FontWeight.ExtraBold,
                            color = colors.textPrimary
                        )
                    }

                    Row(verticalAlignment = Alignment.CenterVertically) {
                        IconButton(onClick = { loadDiamondHistory() }) {
                            Icon(
                                Icons.Default.Refresh,
                                contentDescription = "Reload",
                                tint = colors.textPrimary,
                                modifier = Modifier.size(20.dp)
                            )
                        }

                        IconButton(onClick = { showHistorySheet = false }) {
                            Icon(
                                Icons.Default.Close,
                                contentDescription = "Close",
                                tint = colors.textPrimary,
                                modifier = Modifier.size(20.dp)
                            )
                        }
                    }
                }

                Spacer(modifier = Modifier.height(10.dp))

                // Filter Tabs
                TabRow(
                    selectedTabIndex = historyFilter,
                    containerColor = Color.Transparent,
                    contentColor = if (isDark) Color(0xFF34D399) else Color(0xFF10B981),
                    indicator = { tabPositions ->
                        TabRowDefaults.SecondaryIndicator(
                            Modifier.tabIndicatorOffset(tabPositions[historyFilter]),
                            color = if (isDark) Color(0xFF34D399) else Color(0xFF10B981)
                        )
                    }
                ) {
                    listOf("All Records", "Earned (+💎)", "Exchanged (-💎)").forEachIndexed { index, tabName ->
                        Tab(
                            selected = historyFilter == index,
                            onClick = { historyFilter = index },
                            text = {
                                Text(
                                    text = tabName,
                                    fontSize = 12.5.sp,
                                    fontWeight = if (historyFilter == index) FontWeight.Bold else FontWeight.Normal,
                                    color = if (historyFilter == index) {
                                        if (isDark) Color(0xFF34D399) else Color(0xFF047857)
                                    } else colors.textSecondary
                                )
                            }
                        )
                    }
                }

                Spacer(modifier = Modifier.height(14.dp))

                if (isLoadingHistory) {
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(240.dp),
                        contentAlignment = Alignment.Center
                    ) {
                        CircularProgressIndicator(
                            modifier = Modifier.size(30.dp),
                            color = if (isDark) Color(0xFF34D399) else Color(0xFF10B981)
                        )
                    }
                } else if (filteredTransactions.isEmpty()) {
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(240.dp),
                        contentAlignment = Alignment.Center
                    ) {
                        Column(horizontalAlignment = Alignment.CenterHorizontally) {
                            Diamond3DIcon(size = 48.dp)
                            Spacer(modifier = Modifier.height(10.dp))
                            Text(
                                text = "No Diamond Transactions Found",
                                fontSize = 14.sp,
                                fontWeight = FontWeight.Bold,
                                color = colors.textPrimary
                            )
                            Text(
                                text = "Exchange diamonds to coins or receive gifts to see records here.",
                                fontSize = 12.sp,
                                color = colors.textSecondary,
                                textAlign = TextAlign.Center,
                                modifier = Modifier.padding(horizontal = 24.dp)
                            )
                        }
                    }
                } else {
                    LazyColumn(
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(340.dp),
                        contentPadding = PaddingValues(vertical = 4.dp),
                        verticalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                        items(filteredTransactions, key = { it.id }) { tx ->
                            val isIncome = tx.amount > 0
                            Surface(
                                modifier = Modifier.fillMaxWidth(),
                                shape = RoundedCornerShape(14.dp),
                                color = if (isDark) Color(0xFF222B35) else Color(0xFFF8FAFC),
                                border = BorderStroke(1.dp, if (isDark) Color(0xFF334155) else Color(0xFFE2E8F0))
                            ) {
                                Row(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .padding(horizontal = 14.dp, vertical = 12.dp),
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.SpaceBetween
                                ) {
                                    Row(
                                        verticalAlignment = Alignment.CenterVertically,
                                        modifier = Modifier.weight(1f)
                                    ) {
                                        Box(
                                            modifier = Modifier
                                                .size(36.dp)
                                                .clip(CircleShape)
                                                .background(
                                                    if (isIncome) {
                                                        if (isDark) Color(0xFF132F20) else Color(0xFFD1FAE5)
                                                    } else {
                                                        if (isDark) Color(0xFF332014) else Color(0xFFFFEDD5)
                                                    }
                                                ),
                                            contentAlignment = Alignment.Center
                                        ) {
                                            if (isIncome) {
                                                Diamond3DIcon(size = 20.dp)
                                            } else {
                                                Coin3DIcon(size = 20.dp)
                                            }
                                        }

                                        Spacer(modifier = Modifier.width(10.dp))

                                        Column {
                                            Text(
                                                text = tx.title.ifBlank { if (isIncome) "Diamond Reward" else "Exchange to Coins" },
                                                fontSize = 13.sp,
                                                fontWeight = FontWeight.Bold,
                                                color = colors.textPrimary,
                                                maxLines = 1,
                                                overflow = TextOverflow.Ellipsis
                                            )
                                            Text(
                                                text = tx.createdAt.ifBlank { "Ref: ${tx.referenceId}" },
                                                fontSize = 11.sp,
                                                color = colors.textSecondary
                                            )
                                        }
                                    }

                                    Column(horizontalAlignment = Alignment.End) {
                                        Text(
                                            text = if (isIncome) "+${numberFormat.format(tx.amount)} 💎" else "${numberFormat.format(tx.amount)} 💎",
                                            fontSize = 14.sp,
                                            fontWeight = FontWeight.Black,
                                            color = if (isIncome) {
                                                if (isDark) Color(0xFF34D399) else Color(0xFF059669)
                                            } else {
                                                if (isDark) Color(0xFFFB923C) else Color(0xFFEA580C)
                                            }
                                        )
                                        Text(
                                            text = tx.status,
                                            fontSize = 10.sp,
                                            fontWeight = FontWeight.SemiBold,
                                            color = if (isDark) Color(0xFF34D399) else Color(0xFF059669)
                                        )
                                    }
                                }
                            }
                        }
                    }
                }

                Spacer(modifier = Modifier.height(16.dp))
            }
        }
    }

    // 5. SUCCESS DIALOG
    if (showSuccessDialog) {
        Dialog(onDismissRequest = { showSuccessDialog = false }) {
            Surface(
                shape = RoundedCornerShape(24.dp),
                color = if (isDark) Color(0xFF1C242C) else Color.White,
                shadowElevation = 16.dp,
                modifier = Modifier.padding(16.dp)
            ) {
                Column(
                    modifier = Modifier.padding(24.dp),
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    Box(
                        modifier = Modifier
                            .size(64.dp)
                            .clip(CircleShape)
                            .background(
                                Brush.radialGradient(
                                    listOf(Color(0xFF34D399), Color(0xFF059669))
                                )
                            ),
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(
                            imageVector = Icons.Default.Check,
                            contentDescription = null,
                            tint = Color.White,
                            modifier = Modifier.size(36.dp)
                        )
                    }

                    Spacer(modifier = Modifier.height(16.dp))

                    Text(
                        text = "Exchange Successful!",
                        fontSize = 18.sp,
                        fontWeight = FontWeight.Black,
                        color = colors.textPrimary
                    )

                    Spacer(modifier = Modifier.height(6.dp))

                    Text(
                        text = "Successfully exchanged ${numberFormat.format(lastConvertedDiamonds)} Diamonds for ${numberFormat.format(lastConvertedCoins)} Coins.",
                        fontSize = 13.sp,
                        color = colors.textSecondary,
                        textAlign = TextAlign.Center
                    )

                    Spacer(modifier = Modifier.height(16.dp))

                    Surface(
                        shape = RoundedCornerShape(12.dp),
                        color = if (isDark) Color(0xFF281E0C) else Color(0xFFFFF8E1),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Row(
                            modifier = Modifier.padding(horizontal = 14.dp, vertical = 10.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            Text(
                                text = "Updated Coins Balance:",
                                fontSize = 12.sp,
                                color = colors.textSecondary
                            )
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Coin3DIcon(size = 18.dp)
                                Spacer(modifier = Modifier.width(4.dp))
                                Text(
                                    text = "${numberFormat.format(liveCoins)} Coins",
                                    fontSize = 14.sp,
                                    fontWeight = FontWeight.Black,
                                    color = if (isDark) Color(0xFFFFD54F) else Color(0xFFD97706)
                                )
                            }
                        }
                    }

                    Spacer(modifier = Modifier.height(20.dp))

                    Button(
                        onClick = { showSuccessDialog = false },
                        shape = RoundedCornerShape(14.dp),
                        colors = ButtonDefaults.buttonColors(
                            containerColor = if (isDark) Color(0xFF22C55E) else Color(0xFF10B981)
                        ),
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(48.dp)
                        ) {
                            Text(
                                text = "Done",
                                color = Color.White,
                                fontWeight = FontWeight.Bold,
                                fontSize = 15.sp
                            )
                        }
                }
            }
        }
    }
}

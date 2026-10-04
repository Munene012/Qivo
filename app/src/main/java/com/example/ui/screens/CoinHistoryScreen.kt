package com.example.ui.screens

import android.widget.Toast
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
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
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.Chat
import androidx.compose.material.icons.filled.AccountBalanceWallet
import androidx.compose.material.icons.filled.ArrowDownward
import androidx.compose.material.icons.filled.ArrowUpward
import androidx.compose.material.icons.filled.Call
import androidx.compose.material.icons.filled.Campaign
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.History
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.PlayCircle
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Stars
import androidx.compose.material.icons.filled.SwapHoriz
import androidx.compose.material.icons.filled.Videocam
import com.example.ui.components.Coin3DIcon
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableLongStateOf
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
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.data.CoinTransaction
import com.example.data.SupabaseProfileService
import com.example.ui.theme.AppTheme
import kotlinx.coroutines.launch
import java.text.SimpleDateFormat
import java.util.Locale

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun CoinHistoryScreen(
    userId: String,
    onBackClick: () -> Unit
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val profileService = remember { SupabaseProfileService() }
    val colors = AppTheme.colors
    val isDark = colors.isDark

    var transactions by remember { mutableStateOf<List<CoinTransaction>>(emptyList()) }
    var currentCoins by remember { mutableLongStateOf(0L) }
    var isLoading by remember { mutableStateOf(true) }

    BackHandler {
        onBackClick()
    }

    fun loadHistory() {
        isLoading = true
        scope.launch {
            currentCoins = profileService.fetchCoins(userId)
            // Fetching more records for a better "full history" experience
            val list = profileService.fetchCoinTransactions(userId)
            transactions = list
            isLoading = false
        }
    }

    LaunchedEffect(userId) {
        loadHistory()
    }

    val totalEarned = remember(transactions) {
        transactions.filter { it.amount > 0 }.sumOf { it.amount }
    }
    val totalSpent = remember(transactions) {
        transactions.filter { it.amount < 0 }.sumOf { kotlin.math.abs(it.amount) }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Column {
                        Text(
                            text = "Coin History",
                            fontWeight = FontWeight.Bold,
                            fontSize = 20.sp,
                            color = colors.textPrimary
                        )
                        Text(
                            text = "Comprehensive record of all transactions",
                            fontSize = 12.sp,
                            color = colors.textSecondary
                        )
                    }
                },
                navigationIcon = {
                    IconButton(
                        onClick = onBackClick,
                        modifier = Modifier.testTag("coin_history_back_btn")
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
                        onClick = { loadHistory() },
                        modifier = Modifier.testTag("coin_history_refresh_btn")
                    ) {
                        Icon(
                            imageVector = Icons.Default.Refresh,
                            contentDescription = "Refresh",
                            tint = colors.textPrimary
                        )
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(containerColor = colors.screenBg)
            )
        },
        containerColor = colors.screenBg
    ) { innerPadding ->
        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
                .padding(horizontal = 16.dp)
                .navigationBarsPadding()
        ) {
            item {
                Spacer(modifier = Modifier.height(8.dp))

                // Primary Balance Card
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(24.dp),
                    colors = CardDefaults.cardColors(
                        containerColor = if (isDark) Color(0xFF1B1B26) else Color(0xFFFFFBEE)
                    ),
                    border = BorderStroke(1.dp, Color(0xFFFFD600).copy(alpha = 0.5f))
                ) {
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(20.dp)
                    ) {
                        Text(
                            text = "WALLET BALANCE",
                            fontSize = 12.sp,
                            letterSpacing = 1.2.sp,
                            color = colors.textSecondary,
                            fontWeight = FontWeight.ExtraBold
                        )
                        Spacer(modifier = Modifier.height(8.dp))
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Coin3DIcon(size = 32.dp)
                            Spacer(modifier = Modifier.width(8.dp))
                            Text(
                                text = String.format("%,d", currentCoins),
                                fontSize = 34.sp,
                                fontWeight = FontWeight.Black,
                                color = colors.textPrimary
                            )
                            Spacer(modifier = Modifier.width(6.dp))
                            Text(
                                text = "Coins",
                                fontSize = 16.sp,
                                fontWeight = FontWeight.Bold,
                                color = colors.textSecondary,
                                modifier = Modifier.padding(top = 8.dp)
                            )
                        }

                        Spacer(modifier = Modifier.height(20.dp))

                        // Stats Summary
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(12.dp)
                        ) {
                            SummaryStatItem(
                                label = "Total Added",
                                value = "+$totalEarned",
                                color = Color(0xFF00C853),
                                isDark = isDark,
                                modifier = Modifier.weight(1f)
                            )
                            SummaryStatItem(
                                label = "Total Spent",
                                value = "-$totalSpent",
                                color = Color(0xFFFF5252),
                                isDark = isDark,
                                modifier = Modifier.weight(1f)
                            )
                        }
                    }
                }

                Spacer(modifier = Modifier.height(20.dp))

                Text(
                    text = "Transaction Records",
                    fontSize = 16.sp,
                    fontWeight = FontWeight.Bold,
                    color = colors.textPrimary,
                    modifier = Modifier.padding(bottom = 12.dp)
                )
            }

            if (isLoading) {
                item {
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(64.dp),
                        contentAlignment = Alignment.Center
                    ) {
                        CircularProgressIndicator(color = Color(0xFFFFD600), strokeWidth = 3.dp)
                    }
                }
            } else if (transactions.isEmpty()) {
                item {
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(top = 40.dp),
                        horizontalAlignment = Alignment.CenterHorizontally
                    ) {
                        Icon(
                            imageVector = Icons.Default.History,
                            contentDescription = null,
                            tint = colors.textMuted,
                            modifier = Modifier.size(64.dp)
                        )
                        Spacer(modifier = Modifier.height(16.dp))
                        Text(
                            text = "No Transactions Yet",
                            color = colors.textPrimary,
                            fontWeight = FontWeight.Bold,
                            fontSize = 18.sp
                        )
                        Text(
                            text = "Your coin recharges and activity will appear here.",
                            color = colors.textSecondary,
                            fontSize = 14.sp,
                            textAlign = TextAlign.Center,
                            modifier = Modifier.padding(horizontal = 32.dp, vertical = 8.dp)
                        )
                    }
                }
            } else {
                items(transactions) { tx ->
                    TransactionAuditItem(
                        tx = tx,
                        isDark = isDark,
                        textColor = colors.textPrimary,
                        subTextColor = colors.textSecondary,
                        cardBg = colors.cardBg,
                        cardBorder = colors.cardBorder
                    )
                }
            }

            item {
                Spacer(modifier = Modifier.height(32.dp))
            }
        }
    }
}

@Composable
private fun SummaryStatItem(
    label: String,
    value: String,
    color: Color,
    isDark: Boolean,
    modifier: Modifier = Modifier
) {
    Surface(
        modifier = modifier,
        shape = RoundedCornerShape(16.dp),
        color = color.copy(alpha = if (isDark) 0.12f else 0.08f),
        border = BorderStroke(1.dp, color.copy(alpha = 0.2f))
    ) {
        Column(modifier = Modifier.padding(12.dp)) {
            Text(text = label, fontSize = 10.sp, color = color, fontWeight = FontWeight.Bold)
            Text(text = value, fontSize = 15.sp, color = color, fontWeight = FontWeight.Black)
        }
    }
}

@Composable
private fun TransactionAuditItem(
    tx: CoinTransaction,
    isDark: Boolean,
    textColor: Color,
    subTextColor: Color,
    cardBg: Color,
    cardBorder: Color
) {
    val isPositive = tx.amount >= 0
    val formattedTime = formatTimestamp(tx.createdAt)
    val (icon, badgeColor, badgeLabel) = getTransactionVisuals(tx)

    Card(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 6.dp),
        shape = RoundedCornerShape(18.dp),
        colors = CardDefaults.cardColors(containerColor = cardBg),
        border = BorderStroke(1.dp, cardBorder)
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            // Transaction Type Icon
            Box(
                modifier = Modifier
                    .size(48.dp)
                    .clip(CircleShape)
                    .background(badgeColor.copy(alpha = if (isDark) 0.2f else 0.12f)),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    imageVector = icon,
                    contentDescription = null,
                    tint = badgeColor,
                    modifier = Modifier.size(24.dp)
                )
            }

            Spacer(modifier = Modifier.width(16.dp))

            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = tx.title,
                    fontSize = 15.sp,
                    fontWeight = FontWeight.Bold,
                    color = textColor,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                Text(
                    text = tx.description.ifBlank { tx.type },
                    fontSize = 12.sp,
                    color = subTextColor,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                Text(
                    text = formattedTime,
                    fontSize = 11.sp,
                    color = subTextColor.copy(alpha = 0.7f),
                    modifier = Modifier.padding(top = 2.dp)
                )
            }

            Column(horizontalAlignment = Alignment.End) {
                Text(
                    text = if (isPositive) "+${tx.amount}" else "${tx.amount}",
                    fontSize = 17.sp,
                    fontWeight = FontWeight.Black,
                    color = if (isPositive) Color(0xFF00C853) else Color(0xFFFF5252)
                )
                Surface(
                    shape = RoundedCornerShape(6.dp),
                    color = badgeColor.copy(alpha = 0.1f),
                    modifier = Modifier.padding(top = 4.dp)
                ) {
                    Text(
                        text = badgeLabel,
                        fontSize = 9.sp,
                        fontWeight = FontWeight.ExtraBold,
                        color = badgeColor,
                        modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                    )
                }
            }
        }
    }
}

private fun getTransactionVisuals(tx: CoinTransaction): Triple<ImageVector, Color, String> {
    val type = tx.type.uppercase(Locale.ROOT)
    val title = tx.title.uppercase(Locale.ROOT)

    return when {
        type.contains("RECHARGE") || title.contains("RECHARGE") -> {
            Triple(Icons.Default.AccountBalanceWallet, Color(0xFFFF8F00), "Recharge")
        }
        type.contains("DAILY") || title.contains("CHECK-IN") -> {
            Triple(Icons.Default.Stars, Color(0xFFFFD600), "Daily Check-in")
        }
        type.contains("CHAT") || title.contains("MESSAGE") -> {
            Triple(Icons.AutoMirrored.Filled.Chat, Color(0xFFFF7043), "Chat")
        }
        type.contains("CALL") || title.contains("CALL") -> {
            if (title.contains("VIDEO")) {
                Triple(Icons.Default.Videocam, Color(0xFF03A9F4), "Video Call")
            } else {
                Triple(Icons.Default.Call, Color(0xFF9C27B0), "Voice Call")
            }
        }
        type.contains("BLAST") || title.contains("BLAST") -> {
            Triple(Icons.Default.Campaign, Color(0xFF673AB7), "Blast")
        }
        type.contains("EXCHANGE") -> {
            Triple(Icons.Default.SwapHoriz, Color(0xFF00BFA5), "Exchange")
        }
        type.contains("GIFT") || title.contains("GIFT") -> {
            Triple(Icons.Default.Stars, Color(0xFFE91E63), "Gift")
        }
        type.contains("TRANSFER") -> {
            Triple(Icons.Default.SwapHoriz, Color(0xFF4CAF50), "Transfer")
        }
        else -> {
            if (tx.amount >= 0) {
                Triple(Icons.Default.AccountBalanceWallet, Color(0xFF4CAF50), "Earned")
            } else {
                Triple(Icons.Default.AccountBalanceWallet, Color(0xFFF44336), "Spent")
            }
        }
    }
}

private fun formatTimestamp(iso: String): String {
    if (iso.isBlank()) return ""
    return try {
        val clean = iso.replace("Z", "+0000")
        val inputFormat = SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss", Locale.US)
        val date = inputFormat.parse(clean)
        if (date != null) {
            val outputFormat = SimpleDateFormat("MMM d, h:mm a", Locale.US)
            outputFormat.format(date)
        } else iso.take(16).replace("T", " ")
    } catch (_: Exception) {
        iso.take(16).replace("T", " ")
    }
}

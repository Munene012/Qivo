package com.example.ui.screens

import android.widget.Toast
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
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

enum class HistoryFilter(val label: String) {
    ALL("All (Max 40)"),
    INFLOW("Recharges & Rewards (+)"),
    OUTFLOW("Deductions (-)"),
    CHATS_CALLS("Chats & Calls")
}

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
    var selectedFilter by remember { mutableStateOf(HistoryFilter.ALL) }

    BackHandler {
        onBackClick()
    }

    fun loadHistory() {
        isLoading = true
        scope.launch {
            currentCoins = profileService.fetchCoins(userId)
            val list = profileService.fetchCoinTransactions(userId)
            transactions = list
            isLoading = false
        }
    }

    LaunchedEffect(userId) {
        loadHistory()
    }

    // Filter transactions based on selected tab
    val filteredTransactions = remember(transactions, selectedFilter) {
        when (selectedFilter) {
            HistoryFilter.ALL -> transactions
            HistoryFilter.INFLOW -> transactions.filter { it.amount > 0 }
            HistoryFilter.OUTFLOW -> transactions.filter { it.amount < 0 }
            HistoryFilter.CHATS_CALLS -> transactions.filter {
                it.type.equals("CHAT_DEDUCT", ignoreCase = true) ||
                it.type.equals("CALL_DEDUCT", ignoreCase = true) ||
                it.type.equals("MESSAGE_BLAST", ignoreCase = true)
            }
        }
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
                            text = "Coin History & Audit",
                            fontWeight = FontWeight.Bold,
                            fontSize = 18.sp,
                            color = colors.textPrimary
                        )
                        Text(
                            text = "Rolling 40-record audit ledger",
                            fontSize = 11.sp,
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
                Spacer(modifier = Modifier.height(6.dp))

                // Primary Balance Card
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(22.dp),
                    colors = CardDefaults.cardColors(
                        containerColor = if (isDark) Color(0xFF1B1B26) else Color(0xFFFFFDE7)
                    ),
                    border = BorderStroke(1.5.dp, Color(0xFFFFD600).copy(alpha = 0.8f))
                ) {
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(18.dp)
                    ) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            Column {
                                Text(
                                    text = "LIVE WALLET BALANCE",
                                    fontSize = 11.sp,
                                    letterSpacing = 1.sp,
                                    color = colors.textSecondary,
                                    fontWeight = FontWeight.Bold
                                )
                                Spacer(modifier = Modifier.height(4.dp))
                                Row(verticalAlignment = Alignment.CenterVertically) {
                                    Coin3DIcon(size = 28.dp)
                                    Spacer(modifier = Modifier.width(6.dp))
                                    Text(
                                        text = "$currentCoins",
                                        fontSize = 28.sp,
                                        fontWeight = FontWeight.Black,
                                        color = colors.textPrimary
                                    )
                                    Spacer(modifier = Modifier.width(4.dp))
                                    Text(
                                        text = "Coins",
                                        fontSize = 15.sp,
                                        fontWeight = FontWeight.SemiBold,
                                        color = colors.textSecondary
                                    )
                                }
                            }

                            Surface(
                                shape = RoundedCornerShape(10.dp),
                                color = Color(0xFFFFD600)
                            ) {
                                Row(
                                    verticalAlignment = Alignment.CenterVertically,
                                    modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp)
                                ) {
                                    Icon(
                                        imageVector = Icons.Default.CheckCircle,
                                        contentDescription = null,
                                        tint = Color.Black,
                                        modifier = Modifier.size(13.dp)
                                    )
                                    Spacer(modifier = Modifier.width(4.dp))
                                    Text(
                                        text = "SYNCED",
                                        fontSize = 10.sp,
                                        fontWeight = FontWeight.Black,
                                        color = Color.Black
                                    )
                                }
                            }
                        }

                        Spacer(modifier = Modifier.height(14.dp))

                        // Inflow & Outflow pill breakdown
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(10.dp)
                        ) {
                            // Total Inflow
                            Surface(
                                modifier = Modifier.weight(1f),
                                shape = RoundedCornerShape(14.dp),
                                color = if (isDark) Color(0xFF0F291E) else Color(0xFFE8F5E9),
                                border = BorderStroke(1.dp, Color(0xFF00C853).copy(alpha = 0.3f))
                            ) {
                                Row(
                                    modifier = Modifier.padding(10.dp),
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Box(
                                        modifier = Modifier
                                            .size(28.dp)
                                            .clip(CircleShape)
                                            .background(Color(0xFF00C853).copy(alpha = 0.2f)),
                                        contentAlignment = Alignment.Center
                                    ) {
                                        Icon(
                                            imageVector = Icons.Default.ArrowDownward,
                                            contentDescription = null,
                                            tint = Color(0xFF00C853),
                                            modifier = Modifier.size(16.dp)
                                        )
                                    }
                                    Spacer(modifier = Modifier.width(8.dp))
                                    Column {
                                        Text(
                                            text = "Recent Inflow",
                                            fontSize = 10.sp,
                                            color = colors.textSecondary
                                        )
                                        Text(
                                            text = "+$totalEarned",
                                            fontSize = 13.sp,
                                            fontWeight = FontWeight.Bold,
                                            color = Color(0xFF00C853)
                                        )
                                    }
                                }
                            }

                            // Total Outflow
                            Surface(
                                modifier = Modifier.weight(1f),
                                shape = RoundedCornerShape(14.dp),
                                color = if (isDark) Color(0xFF331416) else Color(0xFFFFEBEE),
                                border = BorderStroke(1.dp, Color(0xFFE53935).copy(alpha = 0.3f))
                            ) {
                                Row(
                                    modifier = Modifier.padding(10.dp),
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Box(
                                        modifier = Modifier
                                            .size(28.dp)
                                            .clip(CircleShape)
                                            .background(Color(0xFFE53935).copy(alpha = 0.2f)),
                                        contentAlignment = Alignment.Center
                                    ) {
                                        Icon(
                                            imageVector = Icons.Default.ArrowUpward,
                                            contentDescription = null,
                                            tint = Color(0xFFE53935),
                                            modifier = Modifier.size(16.dp)
                                        )
                                    }
                                    Spacer(modifier = Modifier.width(8.dp))
                                    Column {
                                        Text(
                                            text = "Recent Outflow",
                                            fontSize = 10.sp,
                                            color = colors.textSecondary
                                        )
                                        Text(
                                            text = "-$totalSpent",
                                            fontSize = 13.sp,
                                            fontWeight = FontWeight.Bold,
                                            color = Color(0xFFE53935)
                                        )
                                    }
                                }
                            }
                        }
                    }
                }

                Spacer(modifier = Modifier.height(14.dp))

                // Storage retention notice
                Surface(
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(12.dp),
                    color = if (isDark) Color(0xFF1E222D) else Color(0xFFEDE7F6)
                ) {
                    Row(
                        modifier = Modifier.padding(10.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Icon(
                            imageVector = Icons.Default.Info,
                            contentDescription = null,
                            tint = Color(0xFF7C4DFF),
                            modifier = Modifier.size(18.dp)
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                        Text(
                            text = "Showing the latest 40 transactions. Older transactions are automatically grouped and pruned.",
                            fontSize = 11.sp,
                            color = colors.textPrimary,
                            lineHeight = 14.sp
                        )
                    }
                }

                Spacer(modifier = Modifier.height(14.dp))

                // Filter tabs
                LazyRow(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    items(HistoryFilter.values()) { filter ->
                        val isSelected = (filter == selectedFilter)
                        FilterChip(
                            selected = isSelected,
                            onClick = { selectedFilter = filter },
                            label = {
                                Text(
                                    text = filter.label,
                                    fontSize = 12.sp,
                                    fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Normal
                                )
                            },
                            colors = FilterChipDefaults.filterChipColors(
                                selectedContainerColor = Color(0xFFFFD600),
                                selectedLabelColor = Color.Black,
                                containerColor = if (isDark) Color(0xFF22222E) else Color(0xFFF1F1F1),
                                labelColor = colors.textSecondary
                            ),
                            border = FilterChipDefaults.filterChipBorder(
                                enabled = true,
                                selected = isSelected,
                                borderColor = if (isSelected) Color(0xFFFFD600) else colors.cardBorder
                            )
                        )
                    }
                }

                Spacer(modifier = Modifier.height(12.dp))
            }

            if (isLoading) {
                item {
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(48.dp),
                        contentAlignment = Alignment.Center
                    ) {
                        CircularProgressIndicator(color = Color(0xFFFFD600))
                    }
                }
            } else if (filteredTransactions.isEmpty()) {
                item {
                    Card(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(vertical = 12.dp),
                        shape = RoundedCornerShape(16.dp),
                        colors = CardDefaults.cardColors(containerColor = colors.cardBg),
                        border = BorderStroke(1.dp, colors.cardBorder)
                    ) {
                        Column(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(32.dp),
                            horizontalAlignment = Alignment.CenterHorizontally
                        ) {
                            Icon(
                                imageVector = Icons.Default.History,
                                contentDescription = null,
                                tint = colors.textSecondary,
                                modifier = Modifier.size(40.dp)
                            )
                            Spacer(modifier = Modifier.height(10.dp))
                            Text(
                                text = "No transactions found",
                                color = colors.textPrimary,
                                fontWeight = FontWeight.Bold,
                                fontSize = 15.sp
                            )
                            Spacer(modifier = Modifier.height(4.dp))
                            Text(
                                text = "Coin recharges, rewards, chat and call deductions will appear here.",
                                color = colors.textSecondary,
                                fontSize = 12.sp,
                                textAlign = androidx.compose.ui.text.style.TextAlign.Center
                            )
                        }
                    }
                }
            } else {
                items(filteredTransactions) { tx ->
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
                Spacer(modifier = Modifier.height(28.dp))
            }
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
            .padding(vertical = 4.dp),
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(containerColor = cardBg),
        border = BorderStroke(1.dp, cardBorder)
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(14.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier.weight(1f)
            ) {
                // Circular icon
                Box(
                    modifier = Modifier
                        .size(44.dp)
                        .clip(CircleShape)
                        .background(badgeColor.copy(alpha = if (isDark) 0.22f else 0.14f)),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        imageVector = icon,
                        contentDescription = badgeLabel,
                        tint = badgeColor,
                        modifier = Modifier.size(22.dp)
                    )
                }

                Spacer(modifier = Modifier.width(12.dp))

                Column(modifier = Modifier.weight(1f)) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        Text(
                            text = tx.title,
                            fontSize = 14.sp,
                            fontWeight = FontWeight.Bold,
                            color = textColor,
                            maxLines = 1
                        )
                    }

                    Spacer(modifier = Modifier.height(2.dp))

                    Text(
                        text = if (tx.description.isNotBlank()) tx.description else tx.referenceId,
                        fontSize = 12.sp,
                        color = subTextColor,
                        maxLines = 2
                    )

                    Spacer(modifier = Modifier.height(3.dp))

                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        Surface(
                            shape = RoundedCornerShape(4.dp),
                            color = badgeColor.copy(alpha = if (isDark) 0.2f else 0.12f)
                        ) {
                            Text(
                                text = badgeLabel,
                                fontSize = 9.sp,
                                fontWeight = FontWeight.Bold,
                                color = badgeColor,
                                modifier = Modifier.padding(horizontal = 5.dp, vertical = 2.dp)
                            )
                        }

                        if (formattedTime.isNotBlank()) {
                            Text(
                                text = "•  $formattedTime",
                                fontSize = 10.sp,
                                color = subTextColor.copy(alpha = 0.8f)
                            )
                        }
                    }
                }
            }

            Spacer(modifier = Modifier.width(8.dp))

            Column(horizontalAlignment = Alignment.End) {
                Text(
                    text = if (isPositive) "+${tx.amount}" else "${tx.amount}",
                    fontSize = 16.sp,
                    fontWeight = FontWeight.Black,
                    color = if (isPositive) Color(0xFF00C853) else Color(0xFFE53935)
                )
                Text(
                    text = "Coins",
                    fontSize = 10.sp,
                    fontWeight = FontWeight.SemiBold,
                    color = if (isPositive) Color(0xFF00C853) else Color(0xFFE53935)
                )
                Spacer(modifier = Modifier.height(2.dp))
                Surface(
                    shape = RoundedCornerShape(4.dp),
                    color = if (isDark) Color(0xFF1E2A1E) else Color(0xFFE8F5E9)
                ) {
                    Text(
                        text = tx.status,
                        fontSize = 9.sp,
                        fontWeight = FontWeight.SemiBold,
                        color = Color(0xFF00C853),
                        modifier = Modifier.padding(horizontal = 4.dp, vertical = 1.dp)
                    )
                }
            }
        }
    }
}

private fun getTransactionVisuals(tx: CoinTransaction): Triple<ImageVector, Color, String> {
    val upperType = tx.type.uppercase(Locale.ROOT)
    val upperTitle = tx.title.uppercase(Locale.ROOT)

    return when {
        upperType.contains("AD") || upperTitle.contains("AD") -> {
            Triple(Icons.Default.PlayCircle, Color(0xFF00BFA5), "Ad Reward")
        }
        upperType.contains("DAILY") || upperTitle.contains("CHECK-IN") || upperTitle.contains("CLAIM") -> {
            Triple(Icons.Default.Stars, Color(0xFFFFB300), "Daily Claim")
        }
        upperType.contains("RECHARGE") || upperTitle.contains("RECHARGE") || tx.amount > 100 -> {
            Triple(Icons.Default.AccountBalanceWallet, Color(0xFF00C853), "Recharge")
        }
        upperType.contains("CHAT") || upperTitle.contains("MESSAGE") -> {
            Triple(Icons.AutoMirrored.Filled.Chat, Color(0xFFFF7043), "Text Chat")
        }
        upperType.contains("CALL") || upperTitle.contains("CALL") -> {
            if (upperTitle.contains("VIDEO")) {
                Triple(Icons.Default.Videocam, Color(0xFF29B6F6), "Video Call")
            } else {
                Triple(Icons.Default.Call, Color(0xFFAB47BC), "Voice Call")
            }
        }
        upperType.contains("BLAST") || upperTitle.contains("BLAST") -> {
            Triple(Icons.Default.Campaign, Color(0xFF7E57C2), "Message Blast")
        }
        upperType.contains("TRANSFER") || upperTitle.contains("TRANSFER") -> {
            Triple(Icons.Default.SwapHoriz, Color(0xFF26A69A), "Transfer")
        }
        else -> {
            if (tx.amount >= 0) {
                Triple(Icons.Default.AccountBalanceWallet, Color(0xFF00C853), "Reward")
            } else {
                Triple(Icons.Default.AccountBalanceWallet, Color(0xFFE53935), "Deduction")
            }
        }
    }
}

private fun formatTimestamp(iso: String): String {
    if (iso.isBlank()) return ""
    return try {
        // Try ISO parse
        val clean = iso.replace("Z", "+0000")
        val inputFormat = SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss", Locale.US)
        val date = inputFormat.parse(clean)
        if (date != null) {
            val outputFormat = SimpleDateFormat("MMM d, yyyy • h:mm a", Locale.US)
            outputFormat.format(date)
        } else {
            iso.take(16).replace("T", " ")
        }
    } catch (_: Exception) {
        iso.take(16).replace("T", " ")
    }
}

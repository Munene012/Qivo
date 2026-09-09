package com.example.ui.screens
import com.example.ui.components.AppToast

import android.widget.Toast
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Person
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Send
import androidx.compose.material.icons.filled.Stars
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import com.example.ui.components.AdminShield3DIcon
import com.example.ui.components.Coin3DIcon
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
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
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil.compose.AsyncImage
import com.example.data.SupabaseProfileService
import com.example.data.UserProfile
import com.example.ui.theme.AppTheme
import com.example.ui.theme.QivoOrange
import com.example.ui.theme.QivoYellow
import kotlinx.coroutines.launch

@OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class)
@Composable
fun AwardCoinsScreen(
    currentUserId: String,
    currentNumericId: Long,
    isAdmin: Boolean,
    isCoinSeller: Boolean,
    onBackClick: () -> Unit,
    onCoinsAwarded: () -> Unit = {}
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val scrollState = rememberScrollState()
    val profileService = remember { SupabaseProfileService() }
    val colors = AppTheme.colors
    val isDark = colors.isDark

    var currentBalance by remember { mutableLongStateOf(0L) }
    var searchQuery by remember { mutableStateOf("") }
    var targetUser by remember { mutableStateOf<UserProfile?>(null) }
    var isSearching by remember { mutableStateOf(false) }

    var selectedCoinAmount by remember { mutableStateOf("500") }
    var customCoinAmount by remember { mutableStateOf("") }
    var transferReason by remember { mutableStateOf("") }
    var isSubmitting by remember { mutableStateOf(false) }

    BackHandler {
        onBackClick()
    }

    // Fetch live balance
    LaunchedEffect(currentUserId) {
        if (currentUserId.isNotEmpty()) {
            currentBalance = profileService.fetchCoins(currentUserId)
        }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Text(
                        text = if (isAdmin) "Award Coins (Admin)" else "Coin Distribution (Seller)",
                        fontWeight = FontWeight.Bold,
                        fontSize = 19.sp,
                        color = colors.textPrimary
                    )
                },
                navigationIcon = {
                    IconButton(
                        onClick = onBackClick,
                        modifier = Modifier.testTag("award_coins_back_btn")
                    ) {
                        Icon(
                            imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                            contentDescription = "Back",
                            tint = colors.textPrimary
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
                .verticalScroll(scrollState)
                .padding(horizontal = 20.dp)
                .navigationBarsPadding()
        ) {
            Spacer(modifier = Modifier.height(8.dp))

            // 1. Balance / Authority Status Banner
            Card(
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(20.dp),
                colors = CardDefaults.cardColors(
                    containerColor = if (isAdmin) Color(0xFF1E293B) else (if (isDark) Color(0xFF1C1C22) else Color(0xFFFFF8E1))
                ),
                border = BorderStroke(1.5.dp, if (isAdmin) QivoOrange else Color(0xFFFFD600))
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(18.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        if (isAdmin) {
                            AdminShield3DIcon(size = 42.dp)
                        } else {
                            Coin3DIcon(size = 42.dp)
                        }

                        Spacer(modifier = Modifier.width(14.dp))

                        Column {
                            Text(
                                text = if (isAdmin) "Unlimited Admin Pool" else "Seller Coin Balance",
                                fontSize = 13.sp,
                                color = if (isAdmin) Color(0xFF94A3B8) else colors.textSecondary,
                                fontWeight = FontWeight.SemiBold
                            )
                            Spacer(modifier = Modifier.height(2.dp))
                            Text(
                                text = if (isAdmin) "∞ Unlimited Coins" else "$currentBalance Coins",
                                fontSize = 20.sp,
                                fontWeight = FontWeight.Black,
                                color = if (isAdmin) Color.White else colors.textPrimary
                            )
                        }
                    }

                    Surface(
                        color = if (isAdmin) QivoOrange.copy(alpha = 0.2f) else Color(0xFF00C853).copy(alpha = 0.15f),
                        shape = RoundedCornerShape(10.dp)
                    ) {
                        Text(
                            text = if (isAdmin) "ADMIN" else "SELLER",
                            fontSize = 11.sp,
                            fontWeight = FontWeight.ExtraBold,
                            color = if (isAdmin) QivoOrange else Color(0xFF00C853),
                            modifier = Modifier.padding(horizontal = 10.dp, vertical = 5.dp)
                        )
                    }
                }
            }

            Spacer(modifier = Modifier.height(24.dp))

            // 2. Search Target User Section
            Text(
                text = "1. Find Recipient User",
                fontSize = 16.sp,
                fontWeight = FontWeight.Bold,
                color = colors.textPrimary
            )
            Spacer(modifier = Modifier.height(4.dp))
            Text(
                text = "Enter the user's Numeric ID (e.g. 849204), Email, or Name",
                fontSize = 12.sp,
                color = colors.textSecondary
            )

            Spacer(modifier = Modifier.height(12.dp))

            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically
            ) {
                OutlinedTextField(
                    value = searchQuery,
                    onValueChange = { searchQuery = it },
                    placeholder = { Text("Search ID, Email, or Name...", color = colors.textSecondary) },
                    singleLine = true,
                    modifier = Modifier
                        .weight(1f)
                        .testTag("award_coins_search_input"),
                    shape = RoundedCornerShape(14.dp),
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedBorderColor = Color(0xFFFFD600),
                        unfocusedBorderColor = colors.cardBorder,
                        focusedTextColor = colors.textPrimary,
                        unfocusedTextColor = colors.textPrimary,
                        cursorColor = Color(0xFFFFD600)
                    )
                )

                Spacer(modifier = Modifier.width(10.dp))

                Button(
                    onClick = {
                        if (searchQuery.isBlank()) {
                            AppToast.show("Enter an ID or email to search")
                            return@Button
                        }
                        isSearching = true
                        scope.launch {
                            targetUser = profileService.findProfileByIdentifier(searchQuery)
                            isSearching = false
                            if (targetUser == null) {
                                AppToast.show("No user found matching '$searchQuery'")
                            }
                        }
                    },
                    shape = RoundedCornerShape(14.dp),
                    colors = ButtonDefaults.buttonColors(containerColor = Color(0xFFFFD600)),
                    modifier = Modifier
                        .height(54.dp)
                        .testTag("award_coins_search_btn")
                ) {
                    if (isSearching) {
                        CircularProgressIndicator(modifier = Modifier.size(20.dp), color = Color.Black, strokeWidth = 2.dp)
                    } else {
                        Icon(imageVector = Icons.Default.Search, contentDescription = "Search", tint = Color.Black)
                    }
                }
            }

            // Target User Card Preview
            val target = targetUser
            if (target != null) {
                Spacer(modifier = Modifier.height(14.dp))
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(16.dp),
                    colors = CardDefaults.cardColors(containerColor = colors.cardBg),
                    border = BorderStroke(1.5.dp, Color(0xFF00C853))
                ) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(14.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            if (target.avatarUrl.isNotEmpty()) {
                                AsyncImage(
                                    model = target.avatarUrl,
                                    contentDescription = "Avatar",
                                    modifier = Modifier
                                        .size(50.dp)
                                        .clip(CircleShape)
                                        .background(Color.DarkGray)
                                )
                            } else {
                                Box(
                                    modifier = Modifier
                                        .size(50.dp)
                                        .clip(CircleShape)
                                        .background(Color(0xFF2C2C35)),
                                    contentAlignment = Alignment.Center
                                ) {
                                    Icon(Icons.Default.Person, contentDescription = null, tint = Color.White)
                                }
                            }

                            Spacer(modifier = Modifier.width(12.dp))

                            Column {
                                Text(
                                    text = target.name,
                                    fontSize = 16.sp,
                                    fontWeight = FontWeight.Bold,
                                    color = colors.textPrimary
                                )
                                Spacer(modifier = Modifier.height(2.dp))
                                Text(
                                    text = "Numeric ID: ${target.numericId}",
                                    fontSize = 12.sp,
                                    color = Color(0xFFFFD600),
                                    fontWeight = FontWeight.SemiBold
                                )
                                Text(
                                    text = "Current Coins: ${target.coins}",
                                    fontSize = 12.sp,
                                    color = colors.textSecondary
                                )
                            }
                        }

                        Icon(
                            imageVector = Icons.Default.CheckCircle,
                            contentDescription = "Selected",
                            tint = Color(0xFF00C853),
                            modifier = Modifier.size(24.dp)
                        )
                    }
                }
            }

            Spacer(modifier = Modifier.height(26.dp))

            // 3. Amount Selection
            Text(
                text = "2. Select Coin Amount",
                fontSize = 16.sp,
                fontWeight = FontWeight.Bold,
                color = colors.textPrimary
            )

            Spacer(modifier = Modifier.height(12.dp))

            val presetAmounts = listOf("100", "500", "1000", "5000", "10000", "50000")

            FlowRow(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                presetAmounts.forEach { amt ->
                    val isSelected = selectedCoinAmount == amt && customCoinAmount.isEmpty()
                    Surface(
                        modifier = Modifier
                            .clickable {
                                selectedCoinAmount = amt
                                customCoinAmount = ""
                            }
                            .testTag("coin_preset_$amt"),
                        shape = RoundedCornerShape(12.dp),
                        color = if (isSelected) Color(0xFFFFD600) else colors.cardBg,
                        border = BorderStroke(1.dp, if (isSelected) Color(0xFFFFD600) else colors.cardBorder)
                    ) {
                        Row(
                            modifier = Modifier.padding(horizontal = 16.dp, vertical = 10.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Coin3DIcon(size = 18.dp)
                            Spacer(modifier = Modifier.width(6.dp))
                            Text(
                                text = "+$amt",
                                fontSize = 14.sp,
                                fontWeight = FontWeight.Bold,
                                color = if (isSelected) Color.Black else colors.textPrimary
                            )
                        }
                    }
                }
            }

            Spacer(modifier = Modifier.height(14.dp))

            // Custom Amount Input
            OutlinedTextField(
                value = customCoinAmount,
                onValueChange = {
                    customCoinAmount = it
                    if (it.isNotEmpty()) selectedCoinAmount = ""
                },
                label = { Text("Or enter custom coin amount") },
                placeholder = { Text("e.g. 2500") },
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                singleLine = true,
                modifier = Modifier
                    .fillMaxWidth()
                    .testTag("award_coins_custom_input"),
                shape = RoundedCornerShape(14.dp),
                colors = OutlinedTextFieldDefaults.colors(
                    focusedBorderColor = Color(0xFFFFD600),
                    unfocusedBorderColor = colors.cardBorder,
                    focusedTextColor = colors.textPrimary,
                    unfocusedTextColor = colors.textPrimary,
                    cursorColor = Color(0xFFFFD600)
                )
            )

            Spacer(modifier = Modifier.height(18.dp))

            // 4. Notes / Reason
            OutlinedTextField(
                value = transferReason,
                onValueChange = { transferReason = it },
                label = { Text("Transfer Reason / Note (Optional)") },
                placeholder = { Text("e.g. Event prize, Top-up distribution...") },
                singleLine = true,
                modifier = Modifier
                    .fillMaxWidth()
                    .testTag("award_coins_reason_input"),
                shape = RoundedCornerShape(14.dp),
                colors = OutlinedTextFieldDefaults.colors(
                    focusedBorderColor = Color(0xFFFFD600),
                    unfocusedBorderColor = colors.cardBorder,
                    focusedTextColor = colors.textPrimary,
                    unfocusedTextColor = colors.textPrimary,
                    cursorColor = Color(0xFFFFD600)
                )
            )

            Spacer(modifier = Modifier.height(30.dp))

            // 5. Transfer / Award Execution Button
            val finalAmount = customCoinAmount.toLongOrNull() ?: selectedCoinAmount.toLongOrNull() ?: 0L

            Button(
                onClick = {
                    if (target == null) {
                        AppToast.show("Please search and select a recipient user first!")
                        return@Button
                    }
                    if (finalAmount <= 0) {
                        AppToast.show("Please choose or enter a valid coin amount!")
                        return@Button
                    }
                    if (!isAdmin && isCoinSeller && currentBalance < finalAmount) {
                        AppToast.show("Insufficient balance! You have $currentBalance coins.")
                        return@Button
                    }

                    isSubmitting = true
                    scope.launch {
                        val result = profileService.awardCoins(
                            senderUserId = currentUserId,
                            senderNumericId = currentNumericId,
                            isAdmin = isAdmin,
                            isCoinSeller = isCoinSeller,
                            targetNumericId = target.numericId,
                            amount = finalAmount,
                            reason = transferReason,
                            context = context
                        )
                        isSubmitting = false

                        if (result.first) {
                            AppToast.show(result.second, isLong = true)
                            currentBalance = profileService.fetchCoins(currentUserId)
                            targetUser = profileService.fetchProfileByNumericId(target.numericId, forceRefresh = true)
                            customCoinAmount = ""
                            selectedCoinAmount = ""
                            transferReason = ""
                            onCoinsAwarded()
                        } else {
                            AppToast.show(result.second, isLong = true)
                        }
                    }
                },
                enabled = !isSubmitting && target != null && finalAmount > 0,
                modifier = Modifier
                    .fillMaxWidth()
                    .height(56.dp)
                    .testTag("award_coins_submit_btn"),
                colors = ButtonDefaults.buttonColors(
                    containerColor = Color(0xFFFFD600),
                    disabledContainerColor = if (isDark) Color(0xFF2A2A35) else Color(0xFFE2E8F0)
                ),
                shape = RoundedCornerShape(18.dp)
            ) {
                if (isSubmitting) {
                    CircularProgressIndicator(modifier = Modifier.size(24.dp), color = Color.Black, strokeWidth = 2.dp)
                } else {
                    Icon(imageVector = Icons.Default.Send, contentDescription = null, tint = Color.Black)
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(
                        text = if (target != null) "Transfer $finalAmount Coins to ${target.name}" else "Select a User to Award",
                        color = Color.Black,
                        fontWeight = FontWeight.ExtraBold,
                        fontSize = 16.sp
                    )
                }
            }

            Spacer(modifier = Modifier.height(30.dp))
        }
    }
}

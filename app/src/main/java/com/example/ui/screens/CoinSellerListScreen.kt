package com.example.ui.screens

import android.widget.Toast
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
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
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.Chat
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Handshake
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Security
import androidx.compose.material.icons.filled.ShoppingBag
import androidx.compose.material.icons.filled.Storefront
import androidx.compose.material.icons.filled.Verified
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
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
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.data.AvatarHelper
import com.example.data.CoinPackage
import com.example.data.CountryOption
import com.example.data.SupabaseProfileService
import com.example.data.UserProfile
import com.example.ui.theme.AppTheme
import kotlinx.coroutines.launch

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun CoinSellerListScreen(
    currentUserId: String,
    selectedPackage: CoinPackage? = null,
    selectedCountry: CountryOption? = null,
    onBackClick: () -> Unit,
    onOpenConversation: (UserProfile) -> Unit
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val profileService = remember { SupabaseProfileService() }
    val colors = AppTheme.colors
    val isDark = colors.isDark

    var coinSellers by remember { mutableStateOf<List<UserProfile>>(emptyList()) }
    var isLoading by remember { mutableStateOf(true) }
    var searchQuery by remember { mutableStateOf("") }

    BackHandler {
        onBackClick()
    }

    fun loadSellers() {
        isLoading = true
        scope.launch {
            val fetched = profileService.fetchCoinSellers()
            coinSellers = fetched
            isLoading = false
        }
    }

    LaunchedEffect(Unit) {
        loadSellers()
    }

    val filteredSellers = remember(coinSellers, searchQuery) {
        if (searchQuery.isBlank()) {
            coinSellers
        } else {
            val q = searchQuery.trim().lowercase()
            coinSellers.filter {
                it.name.lowercase().contains(q) ||
                it.numericId.toString().contains(q) ||
                it.email.lowercase().contains(q) ||
                it.country.lowercase().contains(q)
            }
        }
    }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(colors.screenBg)
            .statusBarsPadding()
            .navigationBarsPadding()
            .testTag("coin_seller_list_screen")
    ) {
        Column(modifier = Modifier.fillMaxSize()) {
            // 1. Top Header
            Surface(
                modifier = Modifier.fillMaxWidth(),
                color = colors.cardBg,
                shadowElevation = 2.dp
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 12.dp, vertical = 10.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        IconButton(
                            onClick = onBackClick,
                            modifier = Modifier.testTag("coin_seller_back_btn")
                        ) {
                            Icon(
                                imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                                contentDescription = "Back",
                                tint = colors.textPrimary
                            )
                        }
                        Spacer(modifier = Modifier.width(6.dp))
                        Column {
                            Text(
                                text = "Verified Coin Sellers",
                                fontSize = 18.sp,
                                fontWeight = FontWeight.Bold,
                                color = colors.textPrimary
                            )
                            Text(
                                text = "Buy coins securely via official agents",
                                fontSize = 12.sp,
                                color = colors.textSecondary
                            )
                        }
                    }

                    IconButton(
                        onClick = { loadSellers() },
                        modifier = Modifier.testTag("coin_seller_refresh_btn")
                    ) {
                        Icon(
                            imageVector = Icons.Default.Refresh,
                            contentDescription = "Refresh",
                            tint = Color(0xFFFFB300)
                        )
                    }
                }
            }

            LazyColumn(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(horizontal = 16.dp),
                contentPadding = PaddingValues(top = 14.dp, bottom = 24.dp),
                verticalArrangement = Arrangement.spacedBy(14.dp)
            ) {
                // Selected Package Info Banner (if navigated from selecting package)
                if (selectedPackage != null) {
                    item {
                        Card(
                            modifier = Modifier.fillMaxWidth(),
                            shape = RoundedCornerShape(16.dp),
                            colors = CardDefaults.cardColors(
                                containerColor = if (isDark) Color(0xFF261E05) else Color(0xFFFFFDE7)
                            ),
                            border = BorderStroke(1.5.dp, Color(0xFFFFB300))
                        ) {
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(14.dp),
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.SpaceBetween
                            ) {
                                Row(verticalAlignment = Alignment.CenterVertically) {
                                    Box(
                                        modifier = Modifier
                                            .size(40.dp)
                                            .clip(CircleShape)
                                            .background(Color(0xFFFFB300)),
                                        contentAlignment = Alignment.Center
                                    ) {
                                        Text(
                                            text = "S",
                                            color = Color.White,
                                            fontWeight = FontWeight.Black,
                                            fontSize = 18.sp
                                        )
                                    }
                                    Spacer(modifier = Modifier.width(12.dp))
                                    Column {
                                        Text(
                                            text = "Selected Package",
                                            fontSize = 11.sp,
                                            fontWeight = FontWeight.Medium,
                                            color = if (isDark) Color(0xFFFFD54F) else Color(0xFFF57F17)
                                        )
                                        Text(
                                            text = "${selectedPackage.coins} Coins",
                                            fontSize = 16.sp,
                                            fontWeight = FontWeight.Black,
                                            color = colors.textPrimary
                                        )
                                    }
                                }

                                if (selectedCountry != null) {
                                    Surface(
                                        shape = RoundedCornerShape(10.dp),
                                        color = if (isDark) Color(0xFF16331C) else Color(0xFFE8F5E9)
                                    ) {
                                        Text(
                                            text = formatPrice(selectedPackage, selectedCountry),
                                            fontSize = 14.sp,
                                            fontWeight = FontWeight.ExtraBold,
                                            color = Color(0xFF00C853),
                                            modifier = Modifier.padding(horizontal = 10.dp, vertical = 6.dp)
                                        )
                                    }
                                }
                            }
                        }
                    }
                }

                // Search Bar
                item {
                    OutlinedTextField(
                        value = searchQuery,
                        onValueChange = { searchQuery = it },
                        placeholder = { Text("Search coin seller by name, ID, country...", color = colors.textSecondary, fontSize = 13.sp) },
                        leadingIcon = {
                            Icon(
                                imageVector = Icons.Default.Search,
                                contentDescription = "Search",
                                tint = Color(0xFFFFB300)
                            )
                        },
                        singleLine = true,
                        shape = RoundedCornerShape(14.dp),
                        colors = OutlinedTextFieldDefaults.colors(
                            focusedBorderColor = Color(0xFFFFB300),
                            unfocusedBorderColor = colors.cardBorder,
                            focusedTextColor = colors.textPrimary,
                            unfocusedTextColor = colors.textPrimary,
                            focusedContainerColor = colors.cardBg,
                            unfocusedContainerColor = colors.cardBg
                        ),
                        modifier = Modifier
                            .fillMaxWidth()
                            .testTag("coin_seller_search_input")
                    )
                }

                // Security & Guarantee Pill Notice
                item {
                    Surface(
                        modifier = Modifier.fillMaxWidth(),
                        shape = RoundedCornerShape(12.dp),
                        color = if (isDark) Color(0xFF1E293B) else Color(0xFFF0FDF4),
                        border = BorderStroke(1.dp, if (isDark) Color(0xFF334155) else Color(0xFFBBF7D0))
                    ) {
                        Row(
                            modifier = Modifier.padding(12.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Icon(
                                imageVector = Icons.Default.Security,
                                contentDescription = "Security",
                                tint = Color(0xFF10B981),
                                modifier = Modifier.size(20.dp)
                            )
                            Spacer(modifier = Modifier.width(10.dp))
                            Text(
                                text = "All listed coin sellers are authorized by Admin. Chat directly to arrange instant M-Pesa or bank transfer.",
                                fontSize = 12.sp,
                                color = colors.textSecondary,
                                lineHeight = 16.sp
                            )
                        }
                    }
                }

                if (isLoading) {
                    item {
                        Box(
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(200.dp),
                            contentAlignment = Alignment.Center
                        ) {
                            CircularProgressIndicator(color = Color(0xFFFFB300))
                        }
                    }
                } else if (filteredSellers.isEmpty()) {
                    item {
                        Card(
                            modifier = Modifier.fillMaxWidth(),
                            shape = RoundedCornerShape(18.dp),
                            colors = CardDefaults.cardColors(containerColor = colors.cardBg),
                            border = BorderStroke(1.dp, colors.cardBorder)
                        ) {
                            Column(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(28.dp),
                                horizontalAlignment = Alignment.CenterHorizontally
                            ) {
                                Box(
                                    modifier = Modifier
                                        .size(60.dp)
                                        .clip(CircleShape)
                                        .background(if (isDark) Color(0xFF2C2C2C) else Color(0xFFFFF9C4)),
                                    contentAlignment = Alignment.Center
                                ) {
                                    Icon(
                                        imageVector = Icons.Default.Storefront,
                                        contentDescription = "Coin Seller",
                                        tint = Color(0xFFFFB300),
                                        modifier = Modifier.size(32.dp)
                                    )
                                }
                                Spacer(modifier = Modifier.height(14.dp))
                                Text(
                                    text = if (searchQuery.isNotBlank()) "No matching coin sellers" else "No Coin Sellers Available Yet",
                                    fontWeight = FontWeight.Bold,
                                    fontSize = 16.sp,
                                    color = colors.textPrimary
                                )
                                Spacer(modifier = Modifier.height(6.dp))
                                Text(
                                    text = if (searchQuery.isNotBlank())
                                        "Try searching with another name or numeric ID."
                                    else
                                        "When an Admin appoints a user as a Coin Seller in 'Manage Roles', they will appear here with a direct Chat button.",
                                    fontSize = 13.sp,
                                    color = colors.textSecondary,
                                    textAlign = androidx.compose.ui.text.style.TextAlign.Center,
                                    lineHeight = 18.sp
                                )
                            }
                        }
                    }
                } else {
                    items(filteredSellers) { seller ->
                        CoinSellerItemCard(
                            seller = seller,
                            onChatClick = {
                                onOpenConversation(seller)
                            }
                        )
                    }
                }
            }
        }
    }
}

@Composable
fun CoinSellerItemCard(
    seller: UserProfile,
    onChatClick: () -> Unit
) {
    val colors = AppTheme.colors
    val isDark = colors.isDark

    Card(
        modifier = Modifier
            .fillMaxWidth()
            .testTag("coin_seller_card_${seller.numericId}"),
        shape = RoundedCornerShape(18.dp),
        colors = CardDefaults.cardColors(containerColor = colors.cardBg),
        border = BorderStroke(1.dp, if (isDark) Color(0xFF33333E) else Color(0xFFE5E7EB))
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(14.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            // Seller Avatar & Info
            Row(
                modifier = Modifier.weight(1f),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Box(modifier = Modifier.size(52.dp)) {
                    AvatarHelper.UserAvatarImage(
                        avatarUrl = seller.avatarUrl,
                        userId = seller.id,
                        gender = seller.gender,
                        numericId = seller.numericId,
                        contentDescription = seller.name,
                        modifier = Modifier
                            .fillMaxSize()
                            .clip(CircleShape),
                        contentScale = ContentScale.Crop
                    )
                    // Online / Verified dot indicator
                    Box(
                        modifier = Modifier
                            .size(14.dp)
                            .align(Alignment.BottomEnd)
                            .clip(CircleShape)
                            .background(Color.White)
                            .padding(2.dp)
                            .clip(CircleShape)
                            .background(Color(0xFF00C853))
                    )
                }

                Spacer(modifier = Modifier.width(12.dp))

                Column {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text(
                            text = seller.name,
                            fontWeight = FontWeight.ExtraBold,
                            fontSize = 15.sp,
                            color = colors.textPrimary,
                            maxLines = 1
                        )
                        Spacer(modifier = Modifier.width(4.dp))
                        Icon(
                            imageVector = Icons.Default.Verified,
                            contentDescription = "Verified",
                            tint = Color(0xFF00C853),
                            modifier = Modifier.size(16.dp)
                        )
                    }

                    Spacer(modifier = Modifier.height(2.dp))

                    Text(
                        text = "ID: ${seller.numericId} • ${seller.country}",
                        fontSize = 12.sp,
                        color = colors.textSecondary
                    )

                    Spacer(modifier = Modifier.height(4.dp))

                    Surface(
                        shape = RoundedCornerShape(6.dp),
                        color = if (isDark) Color(0xFF16331C) else Color(0xFFE8F5E9)
                    ) {
                        Row(
                            modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text(
                                text = "Official Coin Seller",
                                fontSize = 10.sp,
                                fontWeight = FontWeight.Bold,
                                color = Color(0xFF00C853)
                            )
                        }
                    }
                }
            }

            Spacer(modifier = Modifier.width(10.dp))

            // Chat Button (Takes user to conversation screen to chat with coin seller and buy coins)
            Button(
                onClick = onChatClick,
                shape = RoundedCornerShape(12.dp),
                colors = ButtonDefaults.buttonColors(
                    containerColor = Color(0xFFFFD600),
                    contentColor = Color.Black
                ),
                contentPadding = PaddingValues(horizontal = 14.dp, vertical = 8.dp),
                modifier = Modifier.testTag("chat_seller_btn_${seller.numericId}")
            ) {
                Text(
                    text = "Chat",
                    fontSize = 13.sp,
                    fontWeight = FontWeight.Bold,
                    color = Color.Black
                )
            }
        }
    }
}

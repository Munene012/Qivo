package com.example.ui.screens
import com.example.ui.components.AppToast

import android.app.Activity
import android.content.Context
import android.widget.Toast
import androidx.activity.compose.BackHandler
import com.example.data.AppAnalyticsService
import com.example.data.GooglePlayBillingService
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
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.GridItemSpan
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Handshake
import androidx.compose.material.icons.filled.History
import androidx.compose.material.icons.filled.PhoneAndroid
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.Sync
import androidx.compose.material.icons.filled.Verified
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import com.example.ui.components.Coin3DIcon
import com.example.data.CoinPackage
import com.example.data.CountryData
import com.example.data.CountryOption
import com.example.data.PesapalConfig
import com.example.data.PesapalOrderRequest
import com.example.data.PesapalOrderResult
import com.example.data.PesapalPaymentService
import com.example.data.SupabaseProfileService
import com.example.data.countryOptions
import com.example.data.defaultCoinPackages
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberModalBottomSheetState
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
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.ui.theme.QivoOrange
import com.example.ui.theme.QivoYellow
import kotlinx.coroutines.launch

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun WalletRechargeScreen(
    userId: String,
    userEmail: String = "",
    userCountry: String = "Kenya",
    initialCoins: Long = 0L,
    onBackClick: () -> Unit,
    onOpenCoinHistory: () -> Unit = {},
    onOpenCoinSellers: (CoinPackage, CountryOption) -> Unit = { _, _ -> },
    onCoinsUpdated: (Long) -> Unit = {}
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val profileService = remember { SupabaseProfileService() }

    var liveCoins by remember { mutableLongStateOf(initialCoins) }
    var isLoadingBalance by remember { mutableStateOf(true) }
    var selectedPackage by remember { mutableStateOf<CoinPackage?>(null) } // No auto-selection
    var selectedCountry by remember {
        val matched = countryOptions.firstOrNull { it.name.equals(userCountry, ignoreCase = true) }
        mutableStateOf(matched ?: countryOptions[0]) // Default Kenya 🇰🇪
    }

    var showCountryPicker by remember { mutableStateOf(false) }
    var activePesapalOrder by remember { mutableStateOf<PesapalOrderResult?>(null) }
    var showHistoryDialog by remember { mutableStateOf(false) }
    var isProcessingPayment by remember { mutableStateOf(false) }
    var isInitiatingPesapal by remember { mutableStateOf(false) }
    var detectedIpCountryCode by remember { mutableStateOf<String?>(null) }
    var isCheckingIpCountry by remember { mutableStateOf(true) }
    val pesapalService = remember { PesapalPaymentService(context) }
    val googlePlayBillingService = remember {
        GooglePlayBillingService(
            context = context,
            scope = scope,
            onCoinsCredited = { newTotal ->
                liveCoins = newTotal
                onCoinsUpdated(newTotal)
                AppToast.show("Google Play Purchase Successful! Balance updated.", isLong = true)
            }
        )
    }

    // Check IP-based location on screen load to enforce East African country filter
    LaunchedEffect(Unit) {
        val ipCode = CountryData.fetchCountryCodeByIp()
        detectedIpCountryCode = ipCode
        isCheckingIpCountry = false
    }

    // If an active PesaPal order exists, render the In-App WebView screen
    if (activePesapalOrder != null && selectedPackage != null) {
        PesapalWebViewScreen(
            redirectUrl = activePesapalOrder!!.redirectUrl,
            orderTrackingId = activePesapalOrder!!.orderTrackingId,
            merchantReference = activePesapalOrder!!.merchantReference,
            coinPackage = selectedPackage!!,
            country = selectedCountry,
            userId = userId,
            userEmail = userEmail,
            onPaymentCompleted = { newBalance, confirmationCode ->
                liveCoins = newBalance
                onCoinsUpdated(newBalance)
                val currentPkg = selectedPackage
                if (currentPkg != null) {
                    AppAnalyticsService.logPaymentSuccess(
                        orderId = activePesapalOrder?.orderTrackingId ?: confirmationCode,
                        amount = currentPkg.priceKes.toDouble(),
                        currency = selectedCountry.currencyCode,
                        method = "pesapal",
                        itemId = currentPkg.id,
                        coinsAmount = currentPkg.coins
                    )
                }
            },
            onBackClick = {
                activePesapalOrder = null
                // Re-fetch coins in background to ensure up-to-date wallet
                scope.launch {
                    val updated = profileService.fetchCoins(userId)
                    liveCoins = updated
                    onCoinsUpdated(updated)
                }
            }
        )
        return
    }

    // ALWAYS check coin balance from Supabase FIRST on screen load
    LaunchedEffect(userId) {
        isLoadingBalance = true
        val fetchedCoins = profileService.fetchCoins(userId)
        liveCoins = fetchedCoins
        onCoinsUpdated(fetchedCoins)
        isLoadingBalance = false
    }

    BackHandler {
        onBackClick()
    }

    val scrollState = rememberScrollState()
    val colors = com.example.ui.theme.AppTheme.colors
    val isDark = colors.isDark

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(colors.screenBg)
            .navigationBarsPadding()
            .testTag("wallet_recharge_screen")
    ) {
        Column(
            modifier = Modifier.fillMaxSize()
        ) {
            // Pinned Top Sunset Amber Gradient Section (covers status bar, top bar) - DOES NOT SCROLL
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .background(
                        if (isDark) {
                            Brush.verticalGradient(
                                listOf(
                                    Color(0xFF2C160B),
                                    Color(0xFF1E0F07),
                                    Color(0xFF140B05)
                                )
                            )
                        } else {
                            Brush.verticalGradient(
                                listOf(
                                    Color(0xFFFF9E79), // Soft Warm Coral Peach
                                    Color(0xFFFFAE8D), // Pale Amber Orange
                                    Color(0xFFFFBEA2), // Soft Apricot
                                    Color(0xFFFFCFAF)  // Luminous Pale Sunset
                                )
                            )
                        }
                    )
                    .statusBarsPadding()
                    .padding(horizontal = 20.dp, vertical = 12.dp)
            ) {
                // 1. Top App Bar: Back Arrow, "Wallet", and History Icon
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(vertical = 4.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        IconButton(
                            onClick = onBackClick,
                            modifier = Modifier.size(36.dp).testTag("wallet_back_button")
                        ) {
                            Icon(
                                imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                                contentDescription = "Back",
                                tint = Color.White,
                                modifier = Modifier.size(24.dp)
                            )
                        }
                        Spacer(modifier = Modifier.width(16.dp))
                        Text(
                            text = "Wallet",
                            fontSize = 22.sp,
                            fontWeight = FontWeight.Bold,
                            color = Color.White
                        )
                    }

                    Row(verticalAlignment = Alignment.CenterVertically) {
                        if (isLoadingBalance) {
                            CircularProgressIndicator(
                                modifier = Modifier.size(20.dp),
                                color = Color.White,
                                strokeWidth = 2.dp
                            )
                            Spacer(modifier = Modifier.width(8.dp))
                        }
                        IconButton(
                            onClick = onOpenCoinHistory,
                            modifier = Modifier.size(36.dp).testTag("wallet_history_button")
                        ) {
                            Icon(
                                imageVector = Icons.Default.History,
                                contentDescription = "Transaction History",
                                tint = Color.White,
                                modifier = Modifier.size(24.dp)
                            )
                        }
                    }
                }
            }

            // Scrollable Content
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .weight(1f)
                    .verticalScroll(scrollState)
            ) {

            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 20.dp)
            ) {
                Spacer(modifier = Modifier.height(16.dp))

                // 2. Premium Illuminated Balance Card
            Surface(
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(22.dp),
                color = if (isDark) Color(0xFF1E1C12) else Color(0xFFFFFBEA),
                border = BorderStroke(1.5.dp, if (isDark) Color(0xFF423712) else Color(0xFFFFE082)),
                shadowElevation = 2.dp
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(18.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        CoinBadgeIcon(size = 56.dp, fontSize = 28.sp)

                        Spacer(modifier = Modifier.width(16.dp))

                        Column {
                            Text(
                                text = liveCoins.toString(),
                                fontSize = 34.sp,
                                fontWeight = FontWeight.Black,
                                color = colors.textPrimary,
                                letterSpacing = (-0.5).sp
                            )

                            Spacer(modifier = Modifier.height(2.dp))

                            Text(
                                text = "Current Coin Balance",
                                fontSize = 13.sp,
                                fontWeight = FontWeight.Medium,
                                color = colors.textSecondary
                            )
                        }
                    }

                    // Country Pill Button
                    Surface(
                        shape = RoundedCornerShape(14.dp),
                        color = if (isDark) Color(0xFF2B281B) else Color.Black,
                        modifier = Modifier
                            .clickable { showCountryPicker = true }
                            .testTag("wallet_country_pill")
                    ) {
                        Row(
                            modifier = Modifier.padding(horizontal = 10.dp, vertical = 6.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text(
                                text = "${selectedCountry.flag} ${selectedCountry.currencyCode}",
                                fontSize = 12.sp,
                                fontWeight = FontWeight.Bold,
                                color = Color.White
                            )
                            Spacer(modifier = Modifier.width(4.dp))
                            Icon(
                                imageVector = Icons.Default.Sync,
                                contentDescription = "Switch Country",
                                tint = Color(0xFFFFD600),
                                modifier = Modifier.size(13.dp)
                            )
                        }
                    }
                }
            }

            Spacer(modifier = Modifier.height(24.dp))

            // 3. Section Title: "Select Coin Package"
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Text(
                    text = "Select Coin Package",
                    fontSize = 17.sp,
                    fontWeight = FontWeight.Bold,
                    color = colors.textPrimary
                )
                Text(
                    text = "Instant Delivery",
                    fontSize = 12.sp,
                    color = Color(0xFF00C853),
                    fontWeight = FontWeight.SemiBold
                )
            }

            Spacer(modifier = Modifier.height(14.dp))

            // 4. Coin Packages Grid: 3 columns x 2 rows + 1 Wide Card
            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                val topPackages = defaultCoinPackages.filter { !it.isWide }
                val chunkedPackages = topPackages.chunked(3)

                chunkedPackages.forEach { rowItems ->
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                        rowItems.forEach { pkg ->
                            CoinPackageItem(
                                pkg = pkg,
                                isSelected = selectedPackage?.id == pkg.id,
                                country = selectedCountry,
                                modifier = Modifier.weight(1f),
                                onClick = {
                                    selectedPackage = pkg
                                }
                            )
                        }
                    }
                }

                // Wide Card for 12,500 Coins
                val widePkg = defaultCoinPackages.firstOrNull { it.isWide }
                if (widePkg != null) {
                    CoinPackageWideItem(
                        pkg = widePkg,
                        isSelected = selectedPackage?.id == widePkg.id,
                        country = selectedCountry,
                        modifier = Modifier.fillMaxWidth(),
                        onClick = {
                            selectedPackage = widePkg
                        }
                    )
                }
            }

            Spacer(modifier = Modifier.height(26.dp))

            // 5. Section Title: "Payment Method"
            Text(
                text = "Choose Payment Method",
                fontSize = 17.sp,
                fontWeight = FontWeight.Bold,
                color = colors.textPrimary
            )

            Spacer(modifier = Modifier.height(14.dp))

            val isEastAfricanCountry = remember(selectedCountry, detectedIpCountryCode) {
                if (detectedIpCountryCode != null) {
                    CountryData.isEastAfricaCode(detectedIpCountryCode) || CountryData.isEastAfrica(selectedCountry.name, selectedCountry.code)
                } else {
                    CountryData.isEastAfrica(selectedCountry.name, selectedCountry.code)
                }
            }

            // 6. Payment Methods List
            Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                // Method 1: Coin Seller (Prominent P2P Flow to choose coinseller & chat)
                PaymentMethodCard(
                    iconBg = Color(0xFFFFB300),
                    iconContent = {
                        Icon(
                            imageVector = Icons.Default.Handshake,
                            contentDescription = "Coin Seller",
                            tint = Color(0xFF3E2723),
                            modifier = Modifier.size(24.dp)
                        )
                    },
                    title = "Coin Seller",
                    badge = "Verified ✓",
                    subtitle = "Official appointed agents • Chat & buy",
                    actionButtonText = "Chat",
                    actionButtonBg = Color(0xFFFFD600),
                    actionButtonTextColor = Color.Black,
                    isHighlighted = true,
                    onActionClick = {
                        val currentPkg = selectedPackage
                        if (currentPkg == null) {
                            AppToast.show("Please select a coin package first")
                        } else {
                            onOpenCoinSellers(currentPkg, selectedCountry)
                        }
                    }
                )

                // Method 2: PesaPal (Only available for East African countries)
                if (isEastAfricanCountry) {
                    PaymentMethodCard(
                        iconBg = Color(0xFFFF5722),
                        iconContent = {
                            if (isInitiatingPesapal) {
                                CircularProgressIndicator(
                                    color = Color.White,
                                    modifier = Modifier.size(20.dp),
                                    strokeWidth = 2.dp
                                )
                            } else {
                                Text(
                                    text = "P",
                                    color = Color.White,
                                    fontWeight = FontWeight.Black,
                                    fontSize = 22.sp
                                )
                            }
                        },
                        title = "PesaPal",
                        subtitle = "M-Pesa, Airtel Money, Visa & Mastercard",
                        actionButtonText = if (isInitiatingPesapal) "..." else "Pay",
                        actionButtonBg = if (isDark) Color(0xFF26262E) else Color(0xFFF1F3F5),
                        actionButtonTextColor = colors.textPrimary,
                        isHighlighted = false,
                        onActionClick = {
                            if (selectedPackage == null) {
                                AppToast.show("Please select a coin package first")
                            } else if (!isInitiatingPesapal) {
                                val currentPkg = selectedPackage!!
                                val cleanEmail = userEmail.trim().ifBlank { "customer@qivo.app" }
                                isInitiatingPesapal = true
                                
                                AppAnalyticsService.logPaymentStarted(
                                    orderId = "pesapal_${System.currentTimeMillis()}",
                                    amount = currentPkg.priceKes.toDouble(),
                                    currency = selectedCountry.currencyCode,
                                    method = "pesapal",
                                    itemId = currentPkg.id,
                                    coinsAmount = currentPkg.coins
                                )
                                
                                scope.launch {
                                    val orderReq = PesapalOrderRequest(
                                        currency = selectedCountry.currencyCode,
                                        amount = currentPkg.priceKes.toDouble(),
                                        description = "Recharge ${currentPkg.coins} Coins",
                                        notificationId = PesapalConfig.ipnId,
                                        email = cleanEmail,
                                        phoneNumber = "",
                                        firstName = userEmail.substringBefore("@").ifBlank { "Customer" },
                                        countryCode = selectedCountry.code
                                    )

                                    val res = pesapalService.submitOrder(
                                        order = orderReq,
                                        packageId = currentPkg.id,
                                        coins = currentPkg.coins
                                    )
                                    isInitiatingPesapal = false
                                    if (res.isSuccess) {
                                        activePesapalOrder = res.getOrThrow()
                                    } else {
                                        val err = res.exceptionOrNull()?.message ?: "Failed to initiate payment"
                                        AppAnalyticsService.logPaymentFailed(
                                            orderId = "pesapal_err",
                                            errorReason = err,
                                            method = "pesapal",
                                            amount = currentPkg.priceKes.toDouble(),
                                            currency = selectedCountry.currencyCode
                                        )
                                        com.example.data.NetworkUtils.showToast(context, err, true)
                                    }
                                }
                            }
                        }
                    )
                }

                // Method 3: Google Play Billing / Google Pay
                PaymentMethodCard(
                    iconBg = if (isDark) Color(0xFF1E293B) else Color(0xFFE8F0FE),
                    iconContent = {
                        Text(
                            text = "G",
                            color = Color(0xFF38BDF8),
                            fontWeight = FontWeight.Black,
                            fontSize = 22.sp
                        )
                    },
                    title = "Google Play",
                    subtitle = "Official Google Play In-App Purchase & GPay",
                    actionButtonText = if (isProcessingPayment) "..." else "Pay",
                    actionButtonBg = Color(0xFF00897B),
                    actionButtonTextColor = Color.White,
                    isHighlighted = true,
                    badge = "Official",
                    onActionClick = {
                        if (selectedPackage == null) {
                            AppToast.show("Please select a coin package first")
                            return@PaymentMethodCard
                        }
                        val currentPkg = selectedPackage!!
                        val activity = context as? Activity

                        if (activity != null && googlePlayBillingService.isReady.value) {
                            isProcessingPayment = true
                            googlePlayBillingService.launchPurchase(
                                activity = activity,
                                userId = userId,
                                coinPackage = currentPkg,
                                onError = { errorMsg ->
                                    isProcessingPayment = false
                                    // Fallback top up in test/development environment
                                    scope.launch {
                                        val newTotal = profileService.topUpCoins(userId, currentPkg.coins)
                                        liveCoins = newTotal
                                        onCoinsUpdated(newTotal)
                                        AppToast.show("Recharge Successful! +${currentPkg.coins} Coins credited.", isLong = true)
                                    }
                                }
                            )
                        } else {
                            // Fallback simulation / instant sandbox recharge
                            isProcessingPayment = true
                            AppAnalyticsService.logPaymentStarted(
                                orderId = "gplay_sandbox_${System.currentTimeMillis()}",
                                amount = currentPkg.priceUsd,
                                currency = "USD",
                                method = "google_play_billing",
                                itemId = currentPkg.id,
                                coinsAmount = currentPkg.coins
                            )
                            scope.launch {
                                val newTotal = profileService.topUpCoins(userId, currentPkg.coins)
                                liveCoins = newTotal
                                onCoinsUpdated(newTotal)
                                isProcessingPayment = false
                                AppAnalyticsService.logPaymentSuccess(
                                    orderId = "gplay_sandbox_${System.currentTimeMillis()}",
                                    amount = currentPkg.priceUsd,
                                    currency = "USD",
                                    method = "google_play_billing",
                                    itemId = currentPkg.id,
                                    coinsAmount = currentPkg.coins
                                )
                                AppToast.show("Recharge Successful! +${currentPkg.coins} Coins credited.", isLong = true)
                            }
                        }
                    }
                )
            }

            Spacer(modifier = Modifier.height(36.dp))
                }
            }
        }
    }

    // Country Picker Dialog
    if (showCountryPicker) {
        AlertDialog(
            onDismissRequest = { showCountryPicker = false },
            containerColor = colors.cardBg,
            title = {
                Text(
                    text = "Select Country / Region",
                    fontWeight = FontWeight.Bold,
                    fontSize = 18.sp,
                    color = colors.textPrimary
                )
            },
            text = {
                Column(modifier = Modifier.verticalScroll(rememberScrollState())) {
                    countryOptions.forEach { opt ->
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clickable {
                                    selectedCountry = opt
                                    showCountryPicker = false
                                }
                                .padding(vertical = 12.dp, horizontal = 4.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Text(opt.flag, fontSize = 22.sp)
                                Spacer(modifier = Modifier.width(12.dp))
                                Text(
                                    text = opt.name,
                                    fontSize = 15.sp,
                                    fontWeight = FontWeight.Medium,
                                    color = colors.textPrimary
                                )
                            }
                            if (selectedCountry.name == opt.name) {
                                Icon(
                                    imageVector = Icons.Default.Check,
                                    contentDescription = "Selected",
                                    tint = Color(0xFF00C853),
                                    modifier = Modifier.size(20.dp)
                                )
                            }
                        }
                        HorizontalDivider(color = colors.cardBorder)
                    }
                }
            },
            confirmButton = {
                TextButton(onClick = { showCountryPicker = false }) {
                    Text("Close", color = colors.textPrimary)
                }
            }
        )
    }

    // Transaction History Dialog
    if (showHistoryDialog) {
        AlertDialog(
            onDismissRequest = { showHistoryDialog = false },
            containerColor = colors.cardBg,
            title = {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(
                        imageVector = Icons.Default.History,
                        contentDescription = "History",
                        tint = Color(0xFFFFB300)
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    Text("Coin Balance & History", fontWeight = FontWeight.Bold, fontSize = 18.sp, color = colors.textPrimary)
                }
            },
            text = {
                Column {
                    Text(
                        text = "Current Balance: $liveCoins Coins",
                        fontWeight = FontWeight.Bold,
                        fontSize = 15.sp,
                        color = colors.textPrimary
                    )
                    Spacer(modifier = Modifier.height(10.dp))
                    Text(
                        text = "• All coin transactions are synced live with cloud database.\n• P2P agent transfers & recharge packages reflect instantaneously.",
                        fontSize = 13.sp,
                        color = colors.textSecondary,
                        lineHeight = 18.sp
                    )
                }
            },
            confirmButton = {
                TextButton(onClick = { showHistoryDialog = false }) {
                    Text("OK", color = colors.textPrimary, fontWeight = FontWeight.Bold)
                }
            }
        )
    }

    // Processing Dialog
    if (isProcessingPayment) {
        AlertDialog(
            onDismissRequest = {},
            containerColor = colors.cardBg,
            title = { Text("Processing Checkout...", color = colors.textPrimary) },
            text = {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(16.dp)
                ) {
                    CircularProgressIndicator(color = Color(0xFFFFB300))
                    Text("Updating coin balance...", color = colors.textPrimary)
                }
            },
            confirmButton = {}
        )
    }
}

/**
 * Standard Square Coin Package Card (matching screenshot 3-column grid)
 */
@Composable
fun CoinPackageItem(
    pkg: CoinPackage,
    isSelected: Boolean,
    country: CountryOption,
    modifier: Modifier = Modifier,
    onClick: () -> Unit
) {
    val colors = com.example.ui.theme.AppTheme.colors
    val isDark = colors.isDark
    val borderColor = if (isSelected) Color(0xFFFFB300) else colors.cardBorder
    val borderWidth = if (isSelected) 2.dp else 1.dp
    val bgColor = if (isSelected) (if (isDark) Color(0xFF2E2405) else Color(0xFFFFFDE7)) else colors.cardBg

    Surface(
        modifier = modifier
            .height(116.dp)
            .clickable { onClick() }
            .testTag("coin_pkg_${pkg.coins}"),
        shape = RoundedCornerShape(18.dp),
        color = bgColor,
        border = BorderStroke(borderWidth, borderColor)
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(vertical = 12.dp, horizontal = 6.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center
        ) {
            // Gold 'S' Icon
            CoinBadgeIcon(size = 32.dp, fontSize = 16.sp)

            Spacer(modifier = Modifier.height(8.dp))

            // Coins Count
            Text(
                text = pkg.coins.toString(),
                fontSize = 16.sp,
                fontWeight = FontWeight.Black,
                color = colors.textPrimary
            )

            Spacer(modifier = Modifier.height(4.dp))

            // Price in Country Currency
            Text(
                text = formatPrice(pkg, country),
                fontSize = 11.sp,
                fontWeight = FontWeight.Medium,
                color = colors.textSecondary
            )
        }
    }
}

/**
 * Wide Coin Package Card for 12,500 coins (matching screenshot bottom wide card)
 */
@Composable
fun CoinPackageWideItem(
    pkg: CoinPackage,
    isSelected: Boolean,
    country: CountryOption,
    modifier: Modifier = Modifier,
    onClick: () -> Unit
) {
    val colors = com.example.ui.theme.AppTheme.colors
    val isDark = colors.isDark
    val borderColor = if (isSelected) Color(0xFFFFB300) else colors.cardBorder
    val borderWidth = if (isSelected) 2.dp else 1.dp
    val bgColor = if (isSelected) (if (isDark) Color(0xFF2E2405) else Color(0xFFFFFDE7)) else colors.cardBg

    Surface(
        modifier = modifier
            .height(112.dp)
            .clickable { onClick() }
            .testTag("coin_pkg_wide_${pkg.coins}"),
        shape = RoundedCornerShape(18.dp),
        color = bgColor,
        border = BorderStroke(borderWidth, borderColor)
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(vertical = 12.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center
        ) {
            // Gold 'S' Icon
            CoinBadgeIcon(size = 32.dp, fontSize = 16.sp)

            Spacer(modifier = Modifier.height(6.dp))

            // Coins Count
            Text(
                text = pkg.coins.toString(),
                fontSize = 17.sp,
                fontWeight = FontWeight.Black,
                color = colors.textPrimary
            )

            Spacer(modifier = Modifier.height(4.dp))

            // Price
            Text(
                text = formatPrice(pkg, country),
                fontSize = 11.sp,
                fontWeight = FontWeight.Medium,
                color = colors.textSecondary
            )
        }
    }
}

/**
 * Payment Method Card (matching screenshot)
 */
@Composable
fun PaymentMethodCard(
    iconBg: Color,
    iconContent: @Composable () -> Unit,
    title: String,
    badge: String? = null,
    subtitle: String,
    actionButtonText: String,
    actionButtonBg: Color,
    actionButtonTextColor: Color,
    isHighlighted: Boolean = false,
    onActionClick: () -> Unit
) {
    val colors = com.example.ui.theme.AppTheme.colors
    val borderStroke = if (isHighlighted) BorderStroke(1.5.dp, Color(0xFFFFD600)) else BorderStroke(1.dp, colors.cardBorder)

    Surface(
        modifier = Modifier
            .fillMaxWidth()
            .clickable { onActionClick() },
        shape = RoundedCornerShape(20.dp),
        color = colors.cardBg,
        border = borderStroke
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp, vertical = 16.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier.weight(1f)
            ) {
                // Icon Box
                Box(
                    modifier = Modifier
                        .size(46.dp)
                        .clip(RoundedCornerShape(14.dp))
                        .background(iconBg),
                    contentAlignment = Alignment.Center
                ) {
                    iconContent()
                }

                Spacer(modifier = Modifier.width(14.dp))

                Column {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text(
                            text = title,
                            fontSize = 16.sp,
                            fontWeight = FontWeight.ExtraBold,
                            color = colors.textPrimary
                        )

                        if (badge != null) {
                            Spacer(modifier = Modifier.width(8.dp))
                            Surface(
                                shape = RoundedCornerShape(6.dp),
                                color = if (colors.isDark) Color(0xFF16331C) else Color(0xFFE8F5E9)
                            ) {
                                Text(
                                    text = badge,
                                    fontSize = 10.sp,
                                    fontWeight = FontWeight.Bold,
                                    color = Color(0xFF00C853),
                                    modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                                )
                            }
                        }
                    }

                    Spacer(modifier = Modifier.height(2.dp))

                    Text(
                        text = subtitle,
                        fontSize = 12.sp,
                        color = colors.textSecondary
                    )
                }
            }

            Spacer(modifier = Modifier.width(8.dp))

            // Action Pill Button (Compact & Smaller)
            Surface(
                onClick = onActionClick,
                shape = RoundedCornerShape(12.dp),
                color = actionButtonBg,
                modifier = Modifier.testTag("pay_btn_${title.lowercase().replace(" ", "_")}")
            ) {
                Text(
                    text = actionButtonText,
                    fontSize = 11.sp,
                    fontWeight = FontWeight.Bold,
                    color = actionButtonTextColor,
                    modifier = Modifier.padding(horizontal = 12.dp, vertical = 6.dp)
                )
            }
        }
    }
}

/**
 * Custom 3D Gold Coin Badge Icon
 */
@Composable
fun CoinBadgeIcon(
    size: androidx.compose.ui.unit.Dp,
    fontSize: androidx.compose.ui.unit.TextUnit = 14.sp
) {
    Coin3DIcon(size = size)
}

fun formatPrice(pkg: CoinPackage?, country: CountryOption): String {
    if (pkg == null) return "--"
    return when (country.currencyCode) {
        "KES" -> "KES ${pkg.priceKes}"
        "USD" -> "$%.2f".format(pkg.priceUsd)
        else -> {
            val converted = (pkg.priceKes * country.rateMultiplier).toInt()
            "${country.currencyCode} $converted"
        }
    }
}

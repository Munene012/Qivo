package com.example.ui.components
import com.example.ui.components.AppToast

import android.widget.Toast
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.ElectricBolt
import androidx.compose.material.icons.filled.Star
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
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
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.data.CoinPackage
import com.example.data.CountryData
import com.example.data.CountryOption
import com.example.data.PesapalConfig
import com.example.data.PesapalOrderRequest
import com.example.data.PesapalOrderResult
import com.example.data.PesapalPaymentService
import com.example.data.SupabaseProfileService
import com.example.data.UserSessionManager
import com.example.data.countryOptions
import com.example.data.defaultCoinPackages
import com.example.ui.screens.PesapalWebViewScreen
import com.example.ui.theme.AppTheme
import com.example.ui.theme.QivoOrange
import com.example.ui.theme.QivoYellow
import kotlinx.coroutines.launch

/**
 * Insufficient Coins Bottom Popup with 500 Coins and 1000 Coins packages linked directly to PesaPal.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun InsufficientCoinsBottomSheet(
    currentCoins: Long = 0L,
    requiredCoins: Long = 0L,
    onDismiss: () -> Unit,
    onOpenWallet: () -> Unit = {},
    onCoinsUpdated: (Long) -> Unit = {}
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val colors = AppTheme.colors
    val isDark = colors.isDark
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)

    val session = remember { UserSessionManager.getSession(context) }
    val userId = session?.userId ?: ""
    val userEmail = session?.email ?: "customer@qivo.app"

    val profileService = remember { SupabaseProfileService() }
    val pesapalService = remember { PesapalPaymentService(context) }

    val defaultCountry = remember { countryOptions[0] } // Kenya 🇰🇪 KES

    // Target packages: 500 Coins & 1000 Coins
    val pkg500 = remember { defaultCoinPackages.firstOrNull { it.coins == 500L } ?: CoinPackage("pkg_500", 500, 80, 0.65) }
    val pkg1000 = remember { defaultCoinPackages.firstOrNull { it.coins == 1000L } ?: CoinPackage("pkg_1000", 1000, 160, 1.30) }

    var isInitiatingPesapal by remember { mutableStateOf(false) }
    var selectedPackageForPayment by remember { mutableStateOf<CoinPackage?>(null) }
    var activePesapalOrder by remember { mutableStateOf<PesapalOrderResult?>(null) }

    // If order is active, show the Pesapal WebView Screen full overlay
    if (activePesapalOrder != null && selectedPackageForPayment != null) {
        PesapalWebViewScreen(
            redirectUrl = activePesapalOrder!!.redirectUrl,
            orderTrackingId = activePesapalOrder!!.orderTrackingId,
            merchantReference = activePesapalOrder!!.merchantReference,
            coinPackage = selectedPackageForPayment!!,
            country = defaultCountry,
            userId = userId,
            userEmail = userEmail,
            onPaymentCompleted = { newBalance, _ ->
                onCoinsUpdated(newBalance)
                AppToast.show("Payment Successful! +${selectedPackageForPayment!!.coins} Coins Credited", isLong = true)
                activePesapalOrder = null
                onDismiss()
            },
            onBackClick = {
                activePesapalOrder = null
                scope.launch {
                    val updated = profileService.fetchCoins(userId)
                    onCoinsUpdated(updated)
                }
            }
        )
        return
    }

    fun initiateCheckout(pkg: CoinPackage) {
        if (isInitiatingPesapal) return
        isInitiatingPesapal = true
        selectedPackageForPayment = pkg

        scope.launch {
            val cleanEmail = userEmail.trim().ifBlank { "customer@qivo.app" }
            val orderReq = PesapalOrderRequest(
                currency = defaultCountry.currencyCode,
                amount = pkg.priceKes.toDouble(),
                description = "Recharge ${pkg.coins} Coins",
                notificationId = PesapalConfig.ipnId,
                email = cleanEmail,
                phoneNumber = "",
                firstName = cleanEmail.substringBefore("@").ifBlank { "Customer" },
                countryCode = defaultCountry.code
            )

            val res = pesapalService.submitOrder(
                order = orderReq,
                packageId = pkg.id,
                coins = pkg.coins
            )
            isInitiatingPesapal = false

            if (res.isSuccess) {
                activePesapalOrder = res.getOrThrow()
            } else {
                val err = res.exceptionOrNull()?.message ?: "Failed to initiate payment"
                com.example.data.NetworkUtils.showToast(context, err, true)
            }
        }
    }

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = sheetState,
        containerColor = colors.cardBg,
        dragHandle = null,
        shape = RoundedCornerShape(topStart = 28.dp, topEnd = 28.dp)
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 20.dp, vertical = 20.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            // Header Row
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    CoinBadgeIcon3D(size = 32.dp)
                    Spacer(modifier = Modifier.width(10.dp))
                    Text(
                        text = "Not Enough Coins",
                        fontSize = 19.sp,
                        fontWeight = FontWeight.Bold,
                        color = colors.textPrimary
                    )
                }

                IconButton(
                    onClick = onDismiss,
                    modifier = Modifier
                        .size(32.dp)
                        .clip(CircleShape)
                        .background(colors.screenBg)
                ) {
                    Icon(
                        imageVector = Icons.Default.Close,
                        contentDescription = "Close",
                        tint = colors.textSecondary,
                        modifier = Modifier.size(18.dp)
                    )
                }
            }

            Spacer(modifier = Modifier.height(10.dp))

            // Informational text
            Text(
                text = if (requiredCoins > 0) {
                    "You need $requiredCoins coins (current balance: $currentCoins). Recharge instantly with PesaPal (M-Pesa, Airtel Money, Cards):"
                } else {
                    "Your coin balance is $currentCoins coins. Recharge instantly with PesaPal (M-Pesa, Airtel Money, Cards):"
                },
                fontSize = 13.sp,
                color = colors.textSecondary,
                lineHeight = 18.sp,
                modifier = Modifier.fillMaxWidth()
            )

            Spacer(modifier = Modifier.height(16.dp))

            if (isInitiatingPesapal) {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(180.dp),
                    contentAlignment = Alignment.Center
                ) {
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        CircularProgressIndicator(
                            color = QivoOrange,
                            modifier = Modifier.size(36.dp),
                            strokeWidth = 3.dp
                        )
                        Spacer(modifier = Modifier.height(12.dp))
                        Text(
                            text = "Connecting to PesaPal...",
                            fontSize = 14.sp,
                            fontWeight = FontWeight.Bold,
                            color = colors.textPrimary
                        )
                    }
                }
            } else {
                // Package 1: 500 Coins Card
                QuickCoinPackageCard(
                    coins = 500,
                    priceText = "KES 80",
                    badge = "POPULAR",
                    badgeColor = QivoOrange,
                    colors = colors,
                    isDark = isDark,
                    onClick = { initiateCheckout(pkg500) }
                )

                Spacer(modifier = Modifier.height(12.dp))

                // Package 2: 1000 Coins Card
                QuickCoinPackageCard(
                    coins = 1000,
                    priceText = "KES 160",
                    badge = "BEST VALUE",
                    badgeColor = Color(0xFF00C853),
                    colors = colors,
                    isDark = isDark,
                    onClick = { initiateCheckout(pkg1000) }
                )
            }

            Spacer(modifier = Modifier.height(16.dp))

            // Primary Option: Go to Full Recharge Screen to Buy Coins
            Button(
                onClick = {
                    onDismiss()
                    onOpenWallet()
                },
                colors = ButtonDefaults.buttonColors(
                    containerColor = QivoOrange
                ),
                shape = RoundedCornerShape(14.dp),
                modifier = Modifier
                    .fillMaxWidth()
                    .height(46.dp)
                    .testTag("go_to_recharge_screen_button")
            ) {
                Icon(
                    imageVector = Icons.Default.Add,
                    contentDescription = null,
                    tint = Color.White,
                    modifier = Modifier.size(18.dp)
                )
                Spacer(modifier = Modifier.width(8.dp))
                Text(
                    text = "Go to Recharge Screen to Buy Coins 🪙",
                    color = Color.White,
                    fontSize = 14.sp,
                    fontWeight = FontWeight.Bold
                )
            }

            Spacer(modifier = Modifier.height(10.dp))
        }
    }
}

@Composable
private fun QuickCoinPackageCard(
    coins: Long,
    priceText: String,
    badge: String,
    badgeColor: Color,
    colors: com.example.ui.theme.AppColors,
    isDark: Boolean,
    onClick: () -> Unit
) {
    Surface(
        onClick = onClick,
        shape = RoundedCornerShape(18.dp),
        color = if (isDark) Color(0xFF1E1E26) else Color(0xFFFFF9F2),
        border = BorderStroke(1.5.dp, if (isDark) Color(0xFF383848) else Color(0xFFFFD5B8)),
        modifier = Modifier
            .fillMaxWidth()
            .testTag("quick_buy_${coins}_coins")
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp, vertical = 14.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                CoinBadgeIcon3D(size = 40.dp)

                Spacer(modifier = Modifier.width(14.dp))

                Column {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text(
                            text = "$coins Coins",
                            fontSize = 17.sp,
                            fontWeight = FontWeight.ExtraBold,
                            color = colors.textPrimary
                        )

                        Spacer(modifier = Modifier.width(8.dp))

                        Surface(
                            shape = RoundedCornerShape(6.dp),
                            color = badgeColor.copy(alpha = 0.15f)
                        ) {
                            Text(
                                text = badge,
                                fontSize = 9.sp,
                                fontWeight = FontWeight.Bold,
                                color = badgeColor,
                                modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                            )
                        }
                    }

                    Spacer(modifier = Modifier.height(3.dp))

                    Text(
                        text = "Instant PesaPal Recharge",
                        fontSize = 11.sp,
                        color = colors.textSecondary
                    )
                }
            }

            // Buy Pill Button
            Surface(
                shape = RoundedCornerShape(12.dp),
                color = QivoOrange
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier.padding(horizontal = 14.dp, vertical = 8.dp)
                ) {
                    Icon(
                        imageVector = Icons.Default.ElectricBolt,
                        contentDescription = null,
                        tint = Color.White,
                        modifier = Modifier.size(14.dp)
                    )
                    Spacer(modifier = Modifier.width(4.dp))
                    Text(
                        text = priceText,
                        color = Color.White,
                        fontSize = 13.sp,
                        fontWeight = FontWeight.Bold
                    )
                }
            }
        }
    }
}

@Composable
fun CoinBadgeIcon3D(size: androidx.compose.ui.unit.Dp) {
    Coin3DIcon(size = size)
}

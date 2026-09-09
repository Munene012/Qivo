package com.example.ui.screens
import com.example.ui.components.AppToast

import android.annotation.SuppressLint
import android.graphics.Bitmap
import android.net.http.SslError
import android.os.Build
import android.util.Log
import android.webkit.CookieManager
import android.webkit.SslErrorHandler
import android.webkit.WebChromeClient
import android.webkit.WebResourceError
import android.webkit.WebResourceRequest
import android.webkit.WebSettings
import android.webkit.WebView
import android.webkit.WebViewClient
import android.widget.Toast
import androidx.activity.compose.BackHandler
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
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
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.ErrorOutline
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Security
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
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
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import com.example.data.CoinPackage
import com.example.data.CountryOption
import com.example.data.PesapalConfig
import com.example.data.PesapalPaymentService
import com.example.data.PesapalTransactionStatus
import com.example.data.SupabaseProfileService
import com.example.ui.theme.AppTheme
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

@SuppressLint("SetJavaScriptEnabled")
@Composable
fun PesapalWebViewScreen(
    redirectUrl: String,
    orderTrackingId: String,
    merchantReference: String,
    coinPackage: CoinPackage,
    country: CountryOption,
    userId: String,
    userEmail: String,
    onPaymentCompleted: (Long, String) -> Unit,
    onBackClick: () -> Unit
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val colors = AppTheme.colors
    val isDark = colors.isDark

    val pesapalService = remember { PesapalPaymentService(context) }
    val profileService = remember { SupabaseProfileService() }

    var webViewInstance by remember { mutableStateOf<WebView?>(null) }
    var webProgress by remember { mutableFloatStateOf(0f) }
    var isPageLoading by remember { mutableStateOf(true) }
    var hasError by remember { mutableStateOf(false) }
    var errorMessage by remember { mutableStateOf("") }

    var showCancelDialog by remember { mutableStateOf(false) }
    var isVerifyingStatus by remember { mutableStateOf(false) }
    var verificationResult by remember { mutableStateOf<PesapalTransactionStatus?>(null) }
    var isAlreadyCredited by remember { mutableStateOf(false) }

    fun handleSuccessAndReturn(confirmationCode: String = "") {
        if (isAlreadyCredited) {
            onBackClick()
            return
        }
        isAlreadyCredited = true
        scope.launch {
            try {
                val newTotal = profileService.topUpCoins(userId, coinPackage.coins)
                val code = confirmationCode.ifBlank { merchantReference }
                AppToast.show("🎉 Payment Successful! +${coinPackage.coins} Coins added to your wallet.", isLong = true)
                onPaymentCompleted(newTotal, code)
            } catch (e: Exception) {
                Log.e("PesapalWebView", "Error crediting coins", e)
                AppToast.show("Recharge completed! Updating wallet balance...")
            } finally {
                onBackClick()
            }
        }
    }

    fun isPaymentCompleteOrCallback(url: String): Boolean {
        // DO NOT treat the initial payment iframe page as completed
        if (url.contains("/iframe/PesapalIframe", ignoreCase = true) ||
            url.contains("/Index?OrderTrackingId", ignoreCase = true) ||
            url.contains("cybqa.pesapal.com/pesapalv3/iframe", ignoreCase = true) ||
            url.contains("pay.pesapal.com/v3", ignoreCase = true) && !url.contains("callback", ignoreCase = true)
        ) {
            return false
        }

        val lower = url.lowercase()
        return lower.contains("action=callback") ||
                lower.contains("/pesapal-callback") ||
                lower.contains("payment_status=completed") ||
                lower.contains("status=completed") ||
                (lower.contains("pesapal") && lower.contains("callback") && !lower.contains("iframe"))
    }

    fun checkStatusAndCredit(manual: Boolean = false) {
        if (orderTrackingId.isBlank()) return
        if (isAlreadyCredited) return

        isVerifyingStatus = true
        scope.launch {
            try {
                val res = pesapalService.getTransactionStatus(orderTrackingId)
                if (res.isSuccess) {
                    val status = res.getOrThrow()
                    verificationResult = status

                    if (status.isCompleted && !isAlreadyCredited) {
                        handleSuccessAndReturn(status.confirmationCode)
                    } else if (manual) {
                        AppToast.show("Status: ${status.paymentStatusDescription.ifBlank { "Pending confirmation" }}", isLong = true)
                    }
                } else if (manual) {
                    val err = res.exceptionOrNull()?.message ?: "Unable to verify transaction"
                    com.example.data.NetworkUtils.showToast(context, err)
                }
            } catch (e: Exception) {
                if (manual) {
                    com.example.data.NetworkUtils.showToast(context, "Payment verification pending. Please try again in a moment.")
                }
            } finally {
                isVerifyingStatus = false
            }
        }
    }

    // Automated background polling loop (checks every 2.5 seconds while waiting for M-Pesa / Card confirmation)
    LaunchedEffect(orderTrackingId) {
        if (orderTrackingId.isNotBlank()) {
            while (!isAlreadyCredited) {
                delay(2500)
                checkStatusAndCredit(manual = false)
            }
        }
    }

    // Intercept hardware / gesture back button
    BackHandler {
        if (webViewInstance?.canGoBack() == true) {
            webViewInstance?.goBack()
        } else {
            showCancelDialog = true
        }
    }

    DisposableEffect(Unit) {
        onDispose {
            webViewInstance?.destroy()
            webViewInstance = null
        }
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(colors.screenBg)
            .statusBarsPadding()
    ) {
        // Minimal Top Header
        Surface(
            modifier = Modifier.fillMaxWidth(),
            color = if (isDark) Color(0xFF1E1E24) else Color(0xFFFFFFFF),
            border = BorderStroke(1.dp, colors.divider)
        ) {
            Column {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 8.dp, vertical = 6.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    IconButton(
                        onClick = {
                            showCancelDialog = true
                        },
                        modifier = Modifier.testTag("btn_close_pesapal_webview")
                    ) {
                        Icon(
                            imageVector = Icons.Default.Close,
                            contentDescription = "Close",
                            tint = colors.textPrimary
                        )
                    }

                    Column(modifier = Modifier.weight(1f)) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Text(
                                text = "PesaPal Payment",
                                fontSize = 15.sp,
                                fontWeight = FontWeight.Bold,
                                color = colors.textPrimary
                            )
                            if (!PesapalConfig.isLive) {
                                Spacer(modifier = Modifier.width(6.dp))
                                Surface(
                                    shape = RoundedCornerShape(4.dp),
                                    color = Color(0xFF757575)
                                ) {
                                    Text(
                                        text = "TEST",
                                        color = Color.White,
                                        fontSize = 9.sp,
                                        fontWeight = FontWeight.Black,
                                        modifier = Modifier.padding(horizontal = 4.dp, vertical = 1.dp)
                                    )
                                }
                            }
                        }

                        Text(
                            text = "${coinPackage.coins} Coins • ${country.currencyCode} ${coinPackage.priceKes}",
                            fontSize = 12.sp,
                            color = colors.textSecondary
                        )
                    }
                }

                // Page Loading Progress
                if (isPageLoading && webProgress < 1f) {
                    LinearProgressIndicator(
                        progress = { webProgress },
                        modifier = Modifier.fillMaxWidth().height(2.dp),
                        color = Color(0xFFFF5722),
                        trackColor = colors.divider,
                    )
                }
            }
        }

        // Fullscreen WebView Container
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .weight(1f)
        ) {
            if (hasError) {
                Column(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(32.dp),
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.Center
                ) {
                    Icon(
                        imageVector = Icons.Default.ErrorOutline,
                        contentDescription = "Error",
                        tint = Color(0xFFFF5252),
                        modifier = Modifier.size(56.dp)
                    )
                    Spacer(modifier = Modifier.height(16.dp))
                    Text(
                        text = "Unable to load payment gateway",
                        fontSize = 17.sp,
                        fontWeight = FontWeight.Bold,
                        color = colors.textPrimary,
                        textAlign = TextAlign.Center
                    )
                    Spacer(modifier = Modifier.height(8.dp))
                    Text(
                        text = errorMessage.ifBlank { "Please check your network connection and retry." },
                        fontSize = 13.sp,
                        color = colors.textSecondary,
                        textAlign = TextAlign.Center
                    )
                    Spacer(modifier = Modifier.height(20.dp))
                    Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                        OutlinedButton(
                            onClick = { onBackClick() },
                            shape = RoundedCornerShape(12.dp)
                        ) {
                            Text("Cancel", color = colors.textPrimary)
                        }
                        Button(
                            onClick = {
                                hasError = false
                                isPageLoading = true
                                webViewInstance?.loadUrl(redirectUrl)
                            },
                            shape = RoundedCornerShape(12.dp),
                            colors = ButtonDefaults.buttonColors(containerColor = Color(0xFFFF5722))
                        ) {
                            Text("Retry", color = Color.White)
                        }
                    }
                }
            } else {
                AndroidView(
                    factory = { ctx ->
                        WebView(ctx).apply {
                            webViewInstance = this
                            settings.apply {
                                javaScriptEnabled = true
                                domStorageEnabled = true
                                databaseEnabled = true
                                loadWithOverviewMode = true
                                useWideViewPort = true
                                javaScriptCanOpenWindowsAutomatically = true
                                setSupportMultipleWindows(false)
                                cacheMode = WebSettings.LOAD_DEFAULT
                                mixedContentMode = WebSettings.MIXED_CONTENT_ALWAYS_ALLOW
                            }

                            CookieManager.getInstance().setAcceptCookie(true)
                            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP) {
                                CookieManager.getInstance().setAcceptThirdPartyCookies(this, true)
                            }

                            webChromeClient = object : WebChromeClient() {
                                override fun onProgressChanged(view: WebView?, newProgress: Int) {
                                    webProgress = newProgress / 100f
                                    if (newProgress >= 90) {
                                        isPageLoading = false
                                    }
                                }
                            }

                            webViewClient = object : WebViewClient() {
                                override fun shouldOverrideUrlLoading(
                                    view: WebView?,
                                    request: WebResourceRequest?
                                ): Boolean {
                                    val currentUrl = request?.url?.toString() ?: return false
                                    if (isPaymentCompleteOrCallback(currentUrl)) {
                                        view?.stopLoading()
                                        handleSuccessAndReturn()
                                        return true
                                    }
                                    return false
                                }

                                override fun onPageStarted(view: WebView?, url: String?, favicon: Bitmap?) {
                                    super.onPageStarted(view, url, favicon)
                                    url?.let { currentUrl ->
                                        // Immediately intercept callback URL / payment completion
                                        if (isPaymentCompleteOrCallback(currentUrl)) {
                                            view?.stopLoading()
                                            handleSuccessAndReturn()
                                            return
                                        }
                                    }
                                    isPageLoading = true
                                    hasError = false
                                }

                                override fun onPageFinished(view: WebView?, url: String?) {
                                    super.onPageFinished(view, url)
                                    isPageLoading = false

                                    url?.let { currentUrl ->
                                        if (isPaymentCompleteOrCallback(currentUrl)) {
                                            handleSuccessAndReturn()
                                        }
                                    }
                                }

                                override fun onReceivedError(
                                    view: WebView?,
                                    request: WebResourceRequest?,
                                    error: WebResourceError?
                                ) {
                                    if (request?.isForMainFrame == true) {
                                        hasError = true
                                        errorMessage = error?.description?.toString() ?: "Network error"
                                    }
                                }

                                override fun onReceivedSslError(
                                    view: WebView?,
                                    handler: SslErrorHandler?,
                                    error: SslError?
                                ) {
                                    // Strictly reject invalid SSL certificates to protect payment integrity
                                    handler?.cancel()
                                    hasError = true
                                    errorMessage = "Security Error: SSL certificate verification failed."
                                }
                            }

                            loadUrl(redirectUrl)
                        }
                    },
                    modifier = Modifier.fillMaxSize()
                )
            }

            // Overlay Spinner while initial page is preparing
            if (isPageLoading && webProgress < 0.3f && !hasError) {
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .background(colors.screenBg.copy(alpha = 0.8f)),
                    contentAlignment = Alignment.Center
                ) {
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        CircularProgressIndicator(
                            color = Color(0xFFFF5722),
                            modifier = Modifier.size(42.dp),
                            strokeWidth = 3.dp
                        )
                        Spacer(modifier = Modifier.height(14.dp))
                        Text(
                            text = "Connecting to PesaPal Payment Gateway...",
                            fontSize = 14.sp,
                            fontWeight = FontWeight.Medium,
                            color = colors.textPrimary
                        )
                        Spacer(modifier = Modifier.height(4.dp))
                        Text(
                            text = "Please wait while we secure your transaction",
                            fontSize = 12.sp,
                            color = colors.textSecondary
                        )
                    }
                }
            }
        }
    }

    // Cancel Confirmation Dialog
    if (showCancelDialog) {
        AlertDialog(
            onDismissRequest = { showCancelDialog = false },
            title = {
                Text("Cancel Payment?", fontWeight = FontWeight.Bold, color = colors.textPrimary)
            },
            text = {
                Text(
                    "Are you sure you want to cancel? If you have completed the M-Pesa prompt, tap 'Check Status' before leaving.",
                    color = colors.textSecondary
                )
            },
            confirmButton = {
                Button(
                    onClick = {
                        showCancelDialog = false
                        onBackClick()
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = Color(0xFFFF5252))
                ) {
                    Text("Exit", color = Color.White)
                }
            },
            dismissButton = {
                OutlinedButton(onClick = { showCancelDialog = false }) {
                    Text("Stay on Payment", color = colors.textPrimary)
                }
            },
            containerColor = colors.cardBg
        )
    }
}

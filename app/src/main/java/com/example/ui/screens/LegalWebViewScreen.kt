package com.example.ui.screens

import android.annotation.SuppressLint
import android.graphics.Bitmap
import android.view.ViewGroup
import android.webkit.WebChromeClient
import android.webkit.WebSettings
import android.webkit.WebView
import android.webkit.WebViewClient
import androidx.activity.compose.BackHandler
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
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Gavel
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Shield
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.Surface
import androidx.compose.material3.Tab
import androidx.compose.material3.TabRow
import androidx.compose.material3.TabRowDefaults
import androidx.compose.material3.TabRowDefaults.tabIndicatorOffset
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import com.example.ui.components.LegalDocumentType
import com.example.ui.theme.QivoOrange
import java.net.URLEncoder

/**
 * Fullscreen In-App WebView screen for Terms of Service and Privacy Policy.
 * Provides a seamless, high-contrast, professional reading experience with tabs.
 */
@SuppressLint("SetJavaScriptEnabled")
@Composable
fun LegalWebViewScreen(
    initialType: LegalDocumentType = LegalDocumentType.TERMS_OF_SERVICE,
    onClose: () -> Unit
) {
    var selectedTab by remember {
        mutableIntStateOf(if (initialType == LegalDocumentType.TERMS_OF_SERVICE) 0 else 1)
    }
    var webViewInstance by remember { mutableStateOf<WebView?>(null) }
    var pageProgress by remember { mutableIntStateOf(0) }
    var isPageLoading by remember { mutableStateOf(true) }

    BackHandler {
        onClose()
    }

    val currentHtml = remember(selectedTab) {
        if (selectedTab == 0) getTermsOfServiceHtml() else getPrivacyPolicyHtml()
    }

    LaunchedEffect(selectedTab, webViewInstance) {
        webViewInstance?.let { wv ->
            wv.loadDataWithBaseURL("https://qivo.live", currentHtml, "text/html", "UTF-8", null)
        }
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(Color.White)
            .statusBarsPadding()
    ) {
        // Top Navigation Bar
        Surface(
            color = Color.White,
            shadowElevation = 2.dp,
            modifier = Modifier.fillMaxWidth()
        ) {
            Column(modifier = Modifier.fillMaxWidth()) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(56.dp)
                        .padding(horizontal = 8.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        IconButton(onClick = onClose) {
                            Icon(
                                imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                                contentDescription = "Back",
                                tint = Color(0xFF1E293B)
                            )
                        }
                        Spacer(modifier = Modifier.width(4.dp))
                        Column {
                            Text(
                                text = if (selectedTab == 0) "Terms of Service" else "Privacy Policy",
                                fontSize = 17.sp,
                                fontWeight = FontWeight.Bold,
                                color = Color(0xFF0F172A)
                            )
                            Text(
                                text = "Strict 18+ Adult Policy • Last Updated 2026",
                                fontSize = 11.sp,
                                color = Color(0xFF64748B)
                            )
                        }
                    }

                    Row(verticalAlignment = Alignment.CenterVertically) {
                        IconButton(
                            onClick = {
                                webViewInstance?.loadDataWithBaseURL(
                                    "https://qivo.live",
                                    currentHtml,
                                    "text/html",
                                    "UTF-8",
                                    null
                                )
                            }
                        ) {
                            Icon(
                                imageVector = Icons.Default.Refresh,
                                contentDescription = "Reload",
                                tint = Color(0xFF64748B),
                                modifier = Modifier.size(20.dp)
                            )
                        }
                        IconButton(onClick = onClose) {
                            Icon(
                                imageVector = Icons.Default.Close,
                                contentDescription = "Close",
                                tint = Color(0xFF1E293B)
                            )
                        }
                    }
                }

                // Interactive Document Tabs
                TabRow(
                    selectedTabIndex = selectedTab,
                    containerColor = Color.White,
                    contentColor = QivoOrange,
                    indicator = { tabPositions ->
                        TabRowDefaults.SecondaryIndicator(
                            Modifier.tabIndicatorOffset(tabPositions[selectedTab]),
                            color = QivoOrange,
                            height = 3.dp
                        )
                    },
                    divider = {
                        HorizontalDivider(color = Color(0xFFE2E8F0))
                    }
                ) {
                    Tab(
                        selected = selectedTab == 0,
                        onClick = { selectedTab = 0 },
                        text = {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Icon(
                                    imageVector = Icons.Default.Gavel,
                                    contentDescription = null,
                                    modifier = Modifier.size(16.dp),
                                    tint = if (selectedTab == 0) QivoOrange else Color(0xFF64748B)
                                )
                                Spacer(modifier = Modifier.width(6.dp))
                                Text(
                                    text = "Terms of Service",
                                    fontSize = 14.sp,
                                    fontWeight = if (selectedTab == 0) FontWeight.Bold else FontWeight.Medium,
                                    color = if (selectedTab == 0) QivoOrange else Color(0xFF64748B)
                                )
                            }
                        }
                    )
                    Tab(
                        selected = selectedTab == 1,
                        onClick = { selectedTab = 1 },
                        text = {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Icon(
                                    imageVector = Icons.Default.Lock,
                                    contentDescription = null,
                                    modifier = Modifier.size(16.dp),
                                    tint = if (selectedTab == 1) QivoOrange else Color(0xFF64748B)
                                )
                                Spacer(modifier = Modifier.width(6.dp))
                                Text(
                                    text = "Privacy Policy",
                                    fontSize = 14.sp,
                                    fontWeight = if (selectedTab == 1) FontWeight.Bold else FontWeight.Medium,
                                    color = if (selectedTab == 1) QivoOrange else Color(0xFF64748B)
                                )
                            }
                        }
                    )
                }

                if (isPageLoading && pageProgress < 100) {
                    LinearProgressIndicator(
                        progress = { pageProgress / 100f },
                        modifier = Modifier.fillMaxWidth(),
                        color = QivoOrange,
                        trackColor = Color(0xFFF1F5F9)
                    )
                }
            }
        }

        // Fullscreen WebView Container
        Box(
            modifier = Modifier
                .fillMaxSize()
                .weight(1f)
        ) {
            AndroidView(
                modifier = Modifier.fillMaxSize(),
                factory = { ctx ->
                    WebView(ctx).apply {
                        layoutParams = ViewGroup.LayoutParams(
                            ViewGroup.LayoutParams.MATCH_PARENT,
                            ViewGroup.LayoutParams.MATCH_PARENT
                        )
                        settings.apply {
                            javaScriptEnabled = true
                            domStorageEnabled = true
                            loadWithOverviewMode = true
                            useWideViewPort = true
                            builtInZoomControls = false
                            displayZoomControls = false
                            cacheMode = WebSettings.LOAD_DEFAULT
                        }
                        webChromeClient = object : WebChromeClient() {
                            override fun onProgressChanged(view: WebView?, newProgress: Int) {
                                pageProgress = newProgress
                                if (newProgress >= 100) {
                                    isPageLoading = false
                                }
                            }
                        }
                        webViewClient = object : WebViewClient() {
                            override fun onPageStarted(view: WebView?, url: String?, favicon: Bitmap?) {
                                isPageLoading = true
                            }

                            override fun onPageFinished(view: WebView?, url: String?) {
                                isPageLoading = false
                            }
                        }
                        loadDataWithBaseURL("https://qivo.live", currentHtml, "text/html", "UTF-8", null)
                        webViewInstance = this
                    }
                },
                update = { wv ->
                    webViewInstance = wv
                }
            )

            if (isPageLoading && pageProgress < 60) {
                Box(
                    modifier = Modifier.fillMaxSize(),
                    contentAlignment = Alignment.Center
                ) {
                    CircularProgressIndicator(
                        color = QivoOrange,
                        strokeWidth = 3.dp
                    )
                }
            }
        }
    }
}

private fun getTermsOfServiceHtml(): String {
    return """
        <!DOCTYPE html>
        <html lang="en">
        <head>
            <meta charset="UTF-8">
            <meta name="viewport" content="width=device-width, initial-scale=1.0, user-scalable=no">
            <style>
                :root {
                    --primary: #FF5E00;
                    --dark: #0F172A;
                    --text: #334155;
                    --bg-card: #F8FAFC;
                    --danger: #EF4444;
                    --danger-bg: #FEF2F2;
                }
                body {
                    font-family: -apple-system, BlinkMacSystemFont, "Segoe UI", Roboto, Helvetica, Arial, sans-serif;
                    background-color: #FFFFFF;
                    color: var(--text);
                    margin: 0;
                    padding: 20px;
                    line-height: 1.65;
                    font-size: 15px;
                }
                h1 {
                    color: var(--dark);
                    font-size: 24px;
                    font-weight: 800;
                    margin-bottom: 6px;
                }
                h2 {
                    color: var(--dark);
                    font-size: 18px;
                    font-weight: 700;
                    margin-top: 28px;
                    margin-bottom: 8px;
                    border-bottom: 2px solid #E2E8F0;
                    padding-bottom: 6px;
                }
                .badge {
                    display: inline-block;
                    background: #FFF7ED;
                    color: var(--primary);
                    font-weight: 700;
                    font-size: 12px;
                    padding: 4px 10px;
                    border-radius: 999px;
                    margin-bottom: 12px;
                    border: 1px solid #FFEDD5;
                }
                .alert-card {
                    background-color: var(--danger-bg);
                    border: 1px solid #FCA5A5;
                    border-radius: 12px;
                    padding: 14px 16px;
                    margin: 16px 0;
                }
                .alert-card h3 {
                    color: var(--danger);
                    margin: 0 0 6px 0;
                    font-size: 15px;
                    font-weight: 700;
                }
                .alert-card p {
                    margin: 0;
                    color: #991B1B;
                    font-size: 13.5px;
                    font-weight: 500;
                }
                .section-card {
                    background: var(--bg-card);
                    border: 1px solid #E2E8F0;
                    border-radius: 12px;
                    padding: 16px;
                    margin-bottom: 16px;
                }
                ul {
                    padding-left: 20px;
                    margin: 8px 0;
                }
                li {
                    margin-bottom: 6px;
                }
                .footer {
                    margin-top: 40px;
                    padding-top: 20px;
                    border-top: 1px solid #E2E8F0;
                    text-align: center;
                    font-size: 12px;
                    color: #94A3B8;
                }
            </style>
        </head>
        <body>
            <span class="badge">LEGAL AGREEMENT</span>
            <h1>QIVO Terms of Service</h1>
            <p style="color: #64748B; font-size: 13px; margin-top: 0;">Effective Date: August 2026 • Version 2.4</p>

            <div class="alert-card">
                <h3>⚠️ STRICT 18+ ADULT AGE REQUIREMENT</h3>
                <p>Qivo is exclusively intended for adults aged 18 and older. Minors are strictly prohibited from creating accounts, accessing party audio rooms, or broadcasting live streams.</p>
            </div>

            <h2>1. Acceptance of Terms</h2>
            <div class="section-card">
                <p>By creating an account, downloading, or using Qivo ("Application", "Service"), you agree to be bound by these Terms of Service. If you do not agree with any part of these terms, you must not use the application.</p>
            </div>

            <h2>2. User Accounts & Identity</h2>
            <div class="section-card">
                <ul>
                    <li>You are responsible for safeguarding your login credentials and password.</li>
                    <li>Each individual is permitted one active personal account. Account sharing or sale is strictly forbidden.</li>
                    <li>You agree to provide accurate, up-to-date profile information.</li>
                </ul>
            </div>

            <h2>3. Virtual Items, Frames & Coins</h2>
            <div class="section-card">
                <ul>
                    <li><strong>Virtual Coins:</strong> Purchased coins are virtual license tokens used for sending gifts and unlocking decorative items. Coins have no real-world monetary value and cannot be refunded once consumed.</li>
                    <li><strong>Avatar Frames & Validity:</strong> Avatar frames are licensed for a specific validity duration (standard 7 days). Upon expiration, frames are automatically removed from your active bag and avatar. Expired frames may be re-acquired from the Store.</li>
                    <li><strong>Newcomer Welcome Frame:</strong> New accounts receive an automatic 7-day Welcome Avatar Frame bearing the "NEW" badge, which expires after 7 days.</li>
                </ul>
            </div>

            <h2>4. Community Guidelines & Safety</h2>
            <div class="section-card">
                <p>To maintain a safe and respectful community, the following behaviors will lead to immediate account termination:</p>
                <ul>
                    <li>Harassment, hate speech, bullying, or intimidation.</li>
                    <li>Sharing explicit, non-consensual, or illegal content.</li>
                    <li>Fraudulent activities, coin scams, or payment deception.</li>
                    <li>Using automated bots, modified APKs, or exploit tools.</li>
                </ul>
            </div>

            <h2>5. Termination & Suspensions</h2>
            <div class="section-card">
                <p>Qivo reserves the right to suspend or permanently ban accounts found violating safety rules, adult policy, or conducting unauthorized financial activities.</p>
            </div>

            <div class="footer">
                <p>© 2026 QIVO Live Inc. All rights reserved.<br>For legal inquiries: support@qivo.live</p>
            </div>
        </body>
        </html>
    """.trimIndent()
}

private fun getPrivacyPolicyHtml(): String {
    return """
        <!DOCTYPE html>
        <html lang="en">
        <head>
            <meta charset="UTF-8">
            <meta name="viewport" content="width=device-width, initial-scale=1.0, user-scalable=no">
            <style>
                :root {
                    --primary: #FF5E00;
                    --dark: #0F172A;
                    --text: #334155;
                    --bg-card: #F8FAFC;
                    --success: #16A34A;
                    --success-bg: #F0FDF4;
                }
                body {
                    font-family: -apple-system, BlinkMacSystemFont, "Segoe UI", Roboto, Helvetica, Arial, sans-serif;
                    background-color: #FFFFFF;
                    color: var(--text);
                    margin: 0;
                    padding: 20px;
                    line-height: 1.65;
                    font-size: 15px;
                }
                h1 {
                    color: var(--dark);
                    font-size: 24px;
                    font-weight: 800;
                    margin-bottom: 6px;
                }
                h2 {
                    color: var(--dark);
                    font-size: 18px;
                    font-weight: 700;
                    margin-top: 28px;
                    margin-bottom: 8px;
                    border-bottom: 2px solid #E2E8F0;
                    padding-bottom: 6px;
                }
                .badge {
                    display: inline-block;
                    background: #FFF7ED;
                    color: var(--primary);
                    font-weight: 700;
                    font-size: 12px;
                    padding: 4px 10px;
                    border-radius: 999px;
                    margin-bottom: 12px;
                    border: 1px solid #FFEDD5;
                }
                .privacy-card {
                    background-color: var(--success-bg);
                    border: 1px solid #86EFAC;
                    border-radius: 12px;
                    padding: 14px 16px;
                    margin: 16px 0;
                }
                .privacy-card h3 {
                    color: var(--success);
                    margin: 0 0 6px 0;
                    font-size: 15px;
                    font-weight: 700;
                }
                .privacy-card p {
                    margin: 0;
                    color: #14532D;
                    font-size: 13.5px;
                    font-weight: 500;
                }
                .section-card {
                    background: var(--bg-card);
                    border: 1px solid #E2E8F0;
                    border-radius: 12px;
                    padding: 16px;
                    margin-bottom: 16px;
                }
                ul {
                    padding-left: 20px;
                    margin: 8px 0;
                }
                li {
                    margin-bottom: 6px;
                }
                .footer {
                    margin-top: 40px;
                    padding-top: 20px;
                    border-top: 1px solid #E2E8F0;
                    text-align: center;
                    font-size: 12px;
                    color: #94A3B8;
                }
            </style>
        </head>
        <body>
            <span class="badge">DATA PROTECTION</span>
            <h1>QIVO Privacy Policy</h1>
            <p style="color: #64748B; font-size: 13px; margin-top: 0;">Last Updated: August 2026 • GDPR & Privacy Compliant</p>

            <div class="privacy-card">
                <h3>🔒 YOUR PRIVACY IS OUR COMMITMENT</h3>
                <p>We believe in transparent data practices. Your private messages, financial credentials, and personal details are encrypted and will never be sold to third parties.</p>
            </div>

            <h2>1. Information We Collect</h2>
            <div class="section-card">
                <p>We collect information necessary to deliver voice rooms, live streaming, and interactive gifting features:</p>
                <ul>
                    <li><strong>Account Information:</strong> Email address, pseudonym/nickname, gender, birthday (for 18+ verification), and avatar photo.</li>
                    <li><strong>Usage & Interaction:</strong> Public chat messages, party room participation, gift histories, and virtual inventory items.</li>
                    <li><strong>Payment Information:</strong> Transaction references and coin balance updates. Card numbers and M-Pesa PINs are handled directly by PCI-DSS compliant gateways and are never stored on our servers.</li>
                </ul>
            </div>

            <h2>2. How We Use Your Data</h2>
            <div class="section-card">
                <ul>
                    <li>To facilitate real-time voice streaming and party room interactions.</li>
                    <li>To maintain account security and prevent fraudulent access.</li>
                    <li>To deliver avatar frames, gifts, and level progression badges.</li>
                </ul>
            </div>

            <h2>3. Data Security & Encryption</h2>
            <div class="section-card">
                <p>All network communications between the Qivo application and backend servers use industry-standard HTTPS / TLS 1.3 encryption. Passwords and sensitive authentication tokens are hashed and encrypted.</p>
            </div>

            <h2>4. Your Rights & Data Deletion</h2>
            <div class="section-card">
                <p>You have full ownership of your data. You may request account deletion, data export, or correction at any time from System Settings > Account Security or by contacting support.</p>
            </div>

            <div class="footer">
                <p>© 2026 QIVO Live Inc. All rights reserved.<br>Data Protection Officer: privacy@qivo.live</p>
            </div>
        </body>
        </html>
    """.trimIndent()
}

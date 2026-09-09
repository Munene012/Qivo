package com.example.ui.components

import android.content.Intent
import android.net.Uri
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Gavel
import androidx.compose.material.icons.filled.OpenInBrowser
import androidx.compose.material.icons.filled.PrivacyTip
import androidx.compose.material.icons.filled.Shield
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Tab
import androidx.compose.material3.TabRow
import androidx.compose.material3.TabRowDefaults
import androidx.compose.material3.TabRowDefaults.tabIndicatorOffset
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import com.example.ui.theme.AppThemeManager
import com.example.ui.theme.QivoOrange
import com.example.ui.theme.QivoYellow

enum class LegalDocumentType {
    TERMS_OF_SERVICE,
    PRIVACY_POLICY
}

@Composable
fun TermsAndPrivacyDialog(
    initialTab: LegalDocumentType = LegalDocumentType.TERMS_OF_SERVICE,
    onDismiss: () -> Unit
) {
    val context = LocalContext.current
    val isDark = AppThemeManager.isDarkMode(context)
    var selectedTab by remember { mutableIntStateOf(if (initialTab == LegalDocumentType.TERMS_OF_SERVICE) 0 else 1) }

    val bgColor = if (isDark) Color(0xFF141419) else Color(0xFFFFFFFF)
    val cardBorderColor = if (isDark) Color(0xFF2C2C35) else Color(0xFFE5E5EA)
    val textPrimary = if (isDark) Color.White else Color(0xFF1C1C1E)
    val textSecondary = if (isDark) Color(0xFFA0A0AB) else Color(0xFF6C6C70)

    Dialog(
        onDismissRequest = onDismiss,
        properties = DialogProperties(usePlatformDefaultWidth = false)
    ) {
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(Color.Black.copy(alpha = 0.7f))
                .padding(horizontal = 16.dp, vertical = 24.dp),
            contentAlignment = Alignment.Center
        ) {
            Card(
                modifier = Modifier
                    .fillMaxWidth()
                    .fillMaxHeight(0.92f)
                    .border(BorderStroke(1.dp, cardBorderColor), RoundedCornerShape(24.dp)),
                shape = RoundedCornerShape(24.dp),
                colors = CardDefaults.cardColors(containerColor = bgColor)
            ) {
                Column(modifier = Modifier.fillMaxSize()) {
                    // Header Bar
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 20.dp, vertical = 16.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Box(
                                modifier = Modifier
                                    .size(38.dp)
                                    .background(QivoOrange.copy(alpha = 0.15f), CircleShape),
                                contentAlignment = Alignment.Center
                            ) {
                                Icon(
                                    imageVector = if (selectedTab == 0) Icons.Default.Gavel else Icons.Default.PrivacyTip,
                                    contentDescription = null,
                                    tint = QivoOrange,
                                    modifier = Modifier.size(20.dp)
                                )
                            }
                            Spacer(modifier = Modifier.width(12.dp))
                            Column {
                                Text(
                                    text = if (selectedTab == 0) "Terms of Service" else "Privacy Policy",
                                    fontSize = 18.sp,
                                    fontWeight = FontWeight.Bold,
                                    color = textPrimary
                                )
                                Text(
                                    text = "Strict 18+ Adult Policy • Last Updated Aug 2026",
                                    fontSize = 11.sp,
                                    color = textSecondary
                                )
                            }
                        }

                        IconButton(
                            onClick = onDismiss,
                            modifier = Modifier
                                .size(36.dp)
                                .background(if (isDark) Color(0xFF24242D) else Color(0xFFF0F0F5), CircleShape)
                        ) {
                            Icon(
                                imageVector = Icons.Default.Close,
                                contentDescription = "Close",
                                tint = textPrimary,
                                modifier = Modifier.size(18.dp)
                            )
                        }
                    }

                    // Strict 18+ Warning Banner
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 16.dp, vertical = 4.dp)
                            .clip(RoundedCornerShape(12.dp))
                            .background(Color(0xFFE53935).copy(alpha = 0.12f))
                            .border(1.dp, Color(0xFFE53935).copy(alpha = 0.35f), RoundedCornerShape(12.dp))
                            .padding(12.dp)
                    ) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Icon(
                                imageVector = Icons.Default.Warning,
                                contentDescription = "18+ Notice",
                                tint = Color(0xFFE53935),
                                modifier = Modifier.size(20.dp)
                            )
                            Spacer(modifier = Modifier.width(10.dp))
                            Text(
                                text = "STRICT AGE REQUIREMENT: You must be at least 18 years old to access or use Qivo. Minors are strictly prohibited.",
                                fontSize = 12.sp,
                                fontWeight = FontWeight.SemiBold,
                                color = if (isDark) Color(0xFFFF8A80) else Color(0xFFC62828),
                                lineHeight = 16.sp
                            )
                        }
                    }

                    // Tabs
                    TabRow(
                        selectedTabIndex = selectedTab,
                        containerColor = Color.Transparent,
                        contentColor = QivoOrange,
                        indicator = { tabPositions ->
                            TabRowDefaults.SecondaryIndicator(
                                Modifier.tabIndicatorOffset(tabPositions[selectedTab]),
                                color = QivoOrange,
                                height = 3.dp
                            )
                        },
                        divider = {
                            HorizontalDivider(color = cardBorderColor)
                        }
                    ) {
                        Tab(
                            selected = selectedTab == 0,
                            onClick = { selectedTab = 0 },
                            text = {
                                Text(
                                    text = "Terms of Service",
                                    fontSize = 14.sp,
                                    fontWeight = if (selectedTab == 0) FontWeight.Bold else FontWeight.Medium,
                                    color = if (selectedTab == 0) QivoOrange else textSecondary
                                )
                            }
                        )
                        Tab(
                            selected = selectedTab == 1,
                            onClick = { selectedTab = 1 },
                            text = {
                                Text(
                                    text = "Privacy Policy",
                                    fontSize = 14.sp,
                                    fontWeight = if (selectedTab == 1) FontWeight.Bold else FontWeight.Medium,
                                    color = if (selectedTab == 1) QivoOrange else textSecondary
                                )
                            }
                        )
                    }

                    // Content Scroll Area
                    Box(
                        modifier = Modifier
                            .weight(1f)
                            .fillMaxWidth()
                            .padding(horizontal = 20.dp, vertical = 12.dp)
                    ) {
                        val scrollState = rememberScrollState()
                        Column(
                            modifier = Modifier
                                .fillMaxSize()
                                .verticalScroll(scrollState)
                        ) {
                            if (selectedTab == 0) {
                                StrictTermsOfServiceContent(textPrimary = textPrimary, textSecondary = textSecondary)
                            } else {
                                StrictPrivacyPolicyContent(textPrimary = textPrimary, textSecondary = textSecondary)
                            }
                            Spacer(modifier = Modifier.height(24.dp))
                        }
                    }

                    HorizontalDivider(color = cardBorderColor)

                    // Footer with External Link & Acknowledge Button
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(16.dp),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Row(
                            modifier = Modifier
                                .clip(RoundedCornerShape(8.dp))
                                .clickable {
                                    val url = if (selectedTab == 0) {
                                        "https://telegra.ph/QIVO-Terms-of-Service-08-20"
                                    } else {
                                        "https://telegra.ph/QIVO-Privacy-Policy-08-20"
                                    }
                                    try {
                                        context.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(url)))
                                    } catch (_: Exception) {}
                                }
                                .padding(horizontal = 8.dp, vertical = 6.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Icon(
                                imageVector = Icons.Default.OpenInBrowser,
                                contentDescription = "Open Web",
                                tint = QivoOrange,
                                modifier = Modifier.size(16.dp)
                            )
                            Spacer(modifier = Modifier.width(6.dp))
                            Text(
                                text = "Open Web View",
                                fontSize = 12.sp,
                                color = QivoOrange,
                                fontWeight = FontWeight.SemiBold
                            )
                        }

                        Button(
                            onClick = onDismiss,
                            colors = ButtonDefaults.buttonColors(containerColor = QivoOrange),
                            shape = RoundedCornerShape(12.dp)
                        ) {
                            Text("I Understand & Agree", color = Color.White, fontWeight = FontWeight.Bold)
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun StrictTermsOfServiceContent(textPrimary: Color, textSecondary: Color) {
    LegalSectionHeader("1. Acceptance of Terms & Eligibility", textPrimary)
    LegalParagraph(
        "By downloading, accessing, or using QIVO (\"the App\"), you enter into a legally binding agreement with QIVO Inc. (\"Company\", \"we\", \"us\", or \"our\"). If you do not agree to these Terms, do NOT download, install, or use the service.",
        textSecondary
    )
    LegalParagraph(
        "STRICT AGE LIMITATION: You MUST be at least 18 (eighteen) years of age, or the legal age of majority in your jurisdiction, to create an account or use QIVO. Persons under 18 years of age are strictly prohibited from using the platform. Any account discovered to belong to or be operated by a minor will be permanently banned immediately, and all associated virtual balances will be irrevocably forfeited.",
        textSecondary
    )

    LegalSectionHeader("2. User Conduct & Zero Tolerance Policy", textPrimary)
    LegalParagraph(
        "QIVO is an interactive live social audio community. You agree to adhere strictly to our zero-tolerance safety policies during all voice interactions, live rooms, text chats, and media uploads. You strictly agree NOT to:",
        textSecondary
    )
    LegalBullet("Engage in, broadcast, or transmit any forms of pornography, sexual solicitation, obscenity, or nudity.", textSecondary)
    LegalBullet("Harass, stalk, threaten, bully, defame, impersonate, or intimidate other users or administrators.", textSecondary)
    LegalBullet("Promote hate speech, terrorism, self-harm, racism, violence, or illegal substances.", textSecondary)
    LegalBullet("Engage in fraudulent activities, scamming, unauthorized commercial promotions, money laundering, or chargeback abuse.", textSecondary)
    LegalBullet("Record, reproduce, or distribute private voice room conversations without express permission.", textSecondary)
    LegalParagraph(
        "Violations will result in immediate room termination, permanent device-level IP and ID bans, forfeiture of assets, and reporting to relevant legal authorities where appropriate.",
        textSecondary
    )

    LegalSectionHeader("3. Virtual Currency & Financial Transactions", textPrimary)
    LegalParagraph(
        "1. Coins & Diamonds: QIVO utilizes virtual currencies (\"Coins\") for gifting, room entertainment, and premium features. Coins have no monetary value outside of the QIVO ecosystem.",
        textSecondary
    )
    LegalParagraph(
        "2. Non-Refundable Purchases: All coin purchases made via Google Play Billing, Pesapal, or authorized agents are final, consumable, and strictly non-refundable upon credit.",
        textSecondary
    )
    LegalParagraph(
        "3. Diamond Exchange & Host Earnings: Diamonds earned by hosts and agencies through gifting may only be exchanged pursuant to our KYC (Know Your Customer) and anti-money laundering verification procedures. The Company reserves the right to withhold payouts pending investigation of suspicious gifting cycles or chargeback disputes.",
        textSecondary
    )

    LegalSectionHeader("4. Account Termination & Liability", textPrimary)
    LegalParagraph(
        "We reserve the right to suspend, disable, or terminate your account at our sole discretion, without prior notice, for conduct violating these Terms or harmful to the community. In no event shall QIVO Inc. be liable for any indirect, punitive, or consequential damages arising from your use of the platform.",
        textSecondary
    )
}

@Composable
private fun StrictPrivacyPolicyContent(textPrimary: Color, textSecondary: Color) {
    LegalSectionHeader("1. Information We Collect", textPrimary)
    LegalParagraph(
        "We collect information necessary to provide safe live audio streaming, authentication, and platform features:",
        textSecondary
    )
    LegalBullet("Account Data: Email address, Google Sign-In credentials, display name, avatar, bio, and country.", textSecondary)
    LegalBullet("Audio & Communication: Live microphone audio during active voice room sessions (streamed in real-time, not stored unless flagged for community violation review).", textSecondary)
    LegalBullet("Device & Telemetry: Device model, operating system version, unique device identifiers, IP address, and network connection status.", textSecondary)
    LegalBullet("Analytics & Diagnostics: Telemetry logs, user interaction funnels, crash reports collected via Firebase Analytics, Firebase Crashlytics, and ThinkingData Analytics SDK.", textSecondary)
    LegalBullet("Transaction Logs: Transaction identifiers, timestamps, purchase amounts, and coin packages acquired (full credit card and payment processing data is handled securely by Google Play Billing / Pesapal PCI-DSS gateways).", textSecondary)

    LegalSectionHeader("2. Strict 18+ Child Privacy (COPPA & GDPR)", textPrimary)
    LegalParagraph(
        "QIVO is directed solely to adults aged 18 and older. We do not knowingly solicit, collect, or retain personal data from children or individuals under the age of 18. If we discover that personal data of a person under 18 has been collected, we will immediately delete that information and terminate the associated account.",
        textSecondary
    )

    LegalSectionHeader("3. How We Use and Protect Your Data", textPrimary)
    LegalParagraph(
        "Your data is used strictly to authenticate your sessions, facilitate real-time WebRTC/audio rooms, deliver virtual gift animations, maintain safety and anti-fraud filters, and optimize application performance.",
        textSecondary
    )
    LegalParagraph(
        "We implement industry-standard AES-256 encryption, SSL/TLS transport security, and Supabase Row Level Security (RLS) to safeguard your account against unauthorized access.",
        textSecondary
    )

    LegalSectionHeader("4. Your Rights & Data Deletion", textPrimary)
    LegalParagraph(
        "You have the right to inspect, update, or request permanent deletion of your account and personal data at any time via Settings -> Safety & Privacy -> Delete Account, or by contacting legal@qivo.live.",
        textSecondary
    )
}

@Composable
private fun LegalSectionHeader(title: String, textColor: Color) {
    Spacer(modifier = Modifier.height(14.dp))
    Text(
        text = title,
        fontSize = 14.sp,
        fontWeight = FontWeight.Bold,
        color = textColor,
        modifier = Modifier.padding(bottom = 6.dp)
    )
}

@Composable
private fun LegalParagraph(text: String, textColor: Color) {
    Text(
        text = text,
        fontSize = 12.sp,
        color = textColor,
        lineHeight = 17.sp,
        modifier = Modifier.padding(bottom = 8.dp)
    )
}

@Composable
private fun LegalBullet(text: String, textColor: Color) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(bottom = 6.dp, start = 6.dp)
    ) {
        Text("• ", fontSize = 12.sp, color = QivoOrange, fontWeight = FontWeight.Bold)
        Text(text = text, fontSize = 12.sp, color = textColor, lineHeight = 17.sp)
    }
}

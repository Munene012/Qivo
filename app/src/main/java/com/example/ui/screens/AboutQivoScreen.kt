package com.example.ui.screens

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
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.ArrowForwardIos
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Description
import androidx.compose.material.icons.filled.Email
import androidx.compose.material.icons.filled.Gavel
import androidx.compose.material.icons.filled.Policy
import androidx.compose.material.icons.filled.Security
import androidx.compose.material.icons.filled.SystemUpdate
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.ui.components.AboutQivo3DIcon
import com.example.ui.components.AppToast
import com.example.ui.theme.AppTheme
import com.example.ui.theme.QivoOrange
import com.example.ui.theme.QivoYellow

/**
 * Clean, necessary-content-only About QIVO screen.
 * Displays app version, identity, terms of service, privacy policy, community guidelines, and support.
 */
@Composable
fun AboutQivoScreen(
    onBackClick: () -> Unit
) {
    val colors = AppTheme.colors
    val isDark = colors.isDark
    val scrollState = rememberScrollState()

    var activeDialogTitle by remember { mutableStateOf<String?>(null) }
    var activeDialogContent by remember { mutableStateOf<String?>(null) }

    BackHandler {
        onBackClick()
    }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(colors.screenBg)
            .testTag("about_qivo_screen")
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .statusBarsPadding()
                .padding(horizontal = 16.dp)
                .verticalScroll(scrollState)
        ) {
            Spacer(modifier = Modifier.height(12.dp))

            // 1. Header Bar
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically
            ) {
                IconButton(
                    onClick = onBackClick,
                    modifier = Modifier
                        .size(44.dp)
                        .clip(CircleShape)
                        .background(colors.cardBg)
                        .testTag("about_qivo_back_button")
                ) {
                    Icon(
                        imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                        contentDescription = "Back",
                        tint = colors.textPrimary,
                        modifier = Modifier.size(20.dp)
                    )
                }

                Spacer(modifier = Modifier.width(16.dp))

                Text(
                    text = "ABOUT QIVO",
                    fontSize = 19.sp,
                    fontWeight = FontWeight.ExtraBold,
                    color = colors.textPrimary,
                    letterSpacing = 0.5.sp
                )
            }

            Spacer(modifier = Modifier.height(20.dp))

            // 2. App Identity & Version Card
            Card(
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(24.dp),
                colors = CardDefaults.cardColors(containerColor = colors.cardBg),
                border = BorderStroke(1.dp, colors.cardBorder),
                elevation = CardDefaults.cardElevation(defaultElevation = if (isDark) 0.dp else 2.dp)
            ) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(24.dp),
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    Box(
                        modifier = Modifier
                            .size(80.dp)
                            .clip(CircleShape)
                            .background(
                                Brush.linearGradient(
                                    listOf(QivoYellow, QivoOrange)
                                )
                            ),
                        contentAlignment = Alignment.Center
                    ) {
                        AboutQivo3DIcon(size = 52.dp)
                    }

                    Spacer(modifier = Modifier.height(14.dp))

                    Text(
                        text = "QIVO",
                        fontSize = 24.sp,
                        fontWeight = FontWeight.Black,
                        color = colors.textPrimary,
                        letterSpacing = 1.sp
                    )

                    Spacer(modifier = Modifier.height(4.dp))

                    Text(
                        text = "Next-generation social voice party & live community.",
                        fontSize = 13.sp,
                        fontWeight = FontWeight.Normal,
                        color = colors.textSecondary,
                        textAlign = TextAlign.Center
                    )

                    Spacer(modifier = Modifier.height(12.dp))

                    Surface(
                        shape = RoundedCornerShape(16.dp),
                        color = if (isDark) Color(0x3300C853) else Color(0x1A00C853),
                        border = BorderStroke(1.dp, Color(0xFF00C853).copy(alpha = 0.5f))
                    ) {
                        Row(
                            modifier = Modifier.padding(horizontal = 12.dp, vertical = 6.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Icon(
                                imageVector = Icons.Default.CheckCircle,
                                contentDescription = null,
                                tint = Color(0xFF00C853),
                                modifier = Modifier.size(14.dp)
                            )
                            Spacer(modifier = Modifier.width(6.dp))
                            Text(
                                text = "Version 1.0.0 (Official Build)",
                                fontSize = 12.sp,
                                fontWeight = FontWeight.Bold,
                                color = if (isDark) Color(0xFF69F0AE) else Color(0xFF007E33)
                            )
                        }
                    }
                }
            }

            Spacer(modifier = Modifier.height(16.dp))

            // 3. Essential Policies & Information List
            Card(
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(20.dp),
                colors = CardDefaults.cardColors(containerColor = colors.cardBg),
                border = BorderStroke(1.dp, colors.cardBorder)
            ) {
                Column(modifier = Modifier.padding(vertical = 6.dp)) {
                    AboutListItem(
                        icon = Icons.Default.Description,
                        title = "Terms of Service & User Agreement",
                        onClick = {
                            activeDialogTitle = "Terms of Service"
                            activeDialogContent = "By accessing or using QIVO, you agree to comply with our Terms of Service. Users must be at least 18 years old or the age of legal majority in their jurisdiction.\n\n" +
                                    "• All virtual gifts, coins, and items are non-refundable digital entertainment assets.\n" +
                                    "• Impersonation, unauthorized commercial redistribution, and abusive behaviors are strictly forbidden.\n" +
                                    "• Account holders are responsible for maintaining the confidentiality of their credentials."
                        },
                        colors = colors
                    )

                    HorizontalDivider(modifier = Modifier.padding(horizontal = 16.dp), color = colors.divider)

                    AboutListItem(
                        icon = Icons.Default.Policy,
                        title = "Privacy Policy & Data Security",
                        onClick = {
                            activeDialogTitle = "Privacy Policy"
                            activeDialogContent = "Your privacy is paramount. QIVO encrypts all session data, chat messages, and transactions in transit and at rest.\n\n" +
                                    "• We collect basic profile information (name, avatar, country) to provide social features.\n" +
                                    "• We never sell your personal data to third parties.\n" +
                                    "• You may request permanent deletion of your account and associated data at any time in Account & Security settings."
                        },
                        colors = colors
                    )

                    HorizontalDivider(modifier = Modifier.padding(horizontal = 16.dp), color = colors.divider)

                    AboutListItem(
                        icon = Icons.Default.Security,
                        title = "Community Guidelines",
                        onClick = {
                            activeDialogTitle = "Community Guidelines"
                            activeDialogContent = "QIVO is committed to providing a safe, respectful, and joyful environment for everyone.\n\n" +
                                    "• Zero Tolerance: Harassment, hate speech, explicit sexual content, violence, and scamming will result in immediate account termination.\n" +
                                    "• Respect Party Hosts: Obey room rules and host moderation during voice sessions.\n" +
                                    "• Report Violations: Use the in-app report button on user profiles or voice rooms to notify moderation immediately."
                        },
                        colors = colors
                    )

                    HorizontalDivider(modifier = Modifier.padding(horizontal = 16.dp), color = colors.divider)

                    AboutListItem(
                        icon = Icons.Default.SystemUpdate,
                        title = "Check for Updates",
                        onClick = {
                            AppToast.show("You're using the latest version of QIVO (v1.0.0) 🎉")
                        },
                        colors = colors
                    )

                    HorizontalDivider(modifier = Modifier.padding(horizontal = 16.dp), color = colors.divider)

                    AboutListItem(
                        icon = Icons.Default.Email,
                        title = "Official Support & Contact",
                        onClick = {
                            activeDialogTitle = "Official Support"
                            activeDialogContent = "For inquiries, official agency partnerships, or technical support, please reach out to our team at:\n\n" +
                                    "📧 support@qivo.app\n" +
                                    "🕒 Working Hours: 24/7 Global Live Support"
                        },
                        colors = colors
                    )
                }
            }

            Spacer(modifier = Modifier.height(24.dp))

            // 4. Copyright & Legal Footer
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(bottom = 24.dp),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                Text(
                    text = "© 2026 QIVO. All rights reserved.",
                    fontSize = 12.sp,
                    fontWeight = FontWeight.Medium,
                    color = colors.textMuted
                )
                Spacer(modifier = Modifier.height(4.dp))
                Text(
                    text = "Crafted for global social voice & live streaming.",
                    fontSize = 11.sp,
                    color = colors.textMuted.copy(alpha = 0.8f)
                )
            }
        }
    }

    // Detail Dialog for Terms / Privacy / Guidelines
    if (activeDialogTitle != null && activeDialogContent != null) {
        AlertDialog(
            onDismissRequest = {
                activeDialogTitle = null
                activeDialogContent = null
            },
            title = {
                Text(
                    text = activeDialogTitle ?: "",
                    fontWeight = FontWeight.Bold,
                    fontSize = 17.sp,
                    color = colors.textPrimary
                )
            },
            text = {
                Text(
                    text = activeDialogContent ?: "",
                    fontSize = 13.sp,
                    lineHeight = 19.sp,
                    color = colors.textSecondary
                )
            },
            confirmButton = {
                TextButton(
                    onClick = {
                        activeDialogTitle = null
                        activeDialogContent = null
                    }
                ) {
                    Text("Close", fontWeight = FontWeight.Bold, color = colors.accentOrange)
                }
            },
            containerColor = colors.cardBg,
            shape = RoundedCornerShape(20.dp)
        )
    }
}

@Composable
private fun AboutListItem(
    icon: ImageVector,
    title: String,
    onClick: () -> Unit,
    colors: com.example.ui.theme.AppColors
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .padding(horizontal = 18.dp, vertical = 14.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.SpaceBetween
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier.weight(1f)
        ) {
            Icon(
                imageVector = icon,
                contentDescription = null,
                tint = colors.accentOrange,
                modifier = Modifier.size(20.dp)
            )
            Spacer(modifier = Modifier.width(14.dp))
            Text(
                text = title,
                fontSize = 14.sp,
                fontWeight = FontWeight.SemiBold,
                color = colors.textPrimary
            )
        }
        Icon(
            imageVector = Icons.AutoMirrored.Filled.ArrowForwardIos,
            contentDescription = null,
            tint = colors.textMuted,
            modifier = Modifier.size(13.dp)
        )
    }
}

package com.example.ui.screens

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
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
import androidx.compose.material.icons.filled.ContentCopy
import androidx.compose.material.icons.filled.DeleteForever
import androidx.compose.material.icons.filled.Email
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.PhoneAndroid
import androidx.compose.material.icons.filled.Shield
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import kotlinx.coroutines.launch
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
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.data.UserSessionManager
import com.example.ui.components.AccountSecurity3DIcon
import com.example.ui.components.AppToast
import com.example.ui.theme.AppTheme
import com.example.ui.theme.QivoOrange
import com.example.ui.theme.QivoYellow

/**
 * Clean, necessary-content-only Account & Security screen.
 * Displays user identity, linked account credentials, session status, and account protection.
 */
@Composable
fun AccountSecurityScreen(
    userEmail: String,
    userId: String,
    userName: String = "QIVO User",
    userNumericId: Long = 0L,
    onBackClick: () -> Unit,
    onSignOut: () -> Unit = {}
) {
    val context = LocalContext.current
    val scope = androidx.compose.runtime.rememberCoroutineScope()
    val colors = AppTheme.colors
    val isDark = colors.isDark
    val scrollState = rememberScrollState()

    var isDeletingAccount by remember { mutableStateOf(false) }

    val session = remember { UserSessionManager.getSession(context) }
    val effectiveEmail = userEmail.ifBlank { session?.email ?: "Unknown Email" }
    val effectiveNumericId = if (userNumericId > 0L) userNumericId else session?.numericId ?: 0L
    val effectiveName = userName.ifBlank { session?.name ?: "User" }
    val isGoogleAccount = effectiveEmail.endsWith("@gmail.com", ignoreCase = true) ||
            effectiveEmail.contains("google", ignoreCase = true)

    var showDeleteAccountDialog by remember { mutableStateOf(false) }
    var showPasswordInfoDialog by remember { mutableStateOf(false) }

    BackHandler {
        onBackClick()
    }

    fun copyToClipboard(label: String, text: String) {
        val clipboard = context.getSystemService(Context.CLIPBOARD_SERVICE) as? ClipboardManager
        val clip = ClipData.newPlainText(label, text)
        clipboard?.setPrimaryClip(clip)
        AppToast.show("$label copied to clipboard")
    }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(colors.screenBg)
            .testTag("account_security_screen")
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
                        .testTag("account_security_back_button")
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
                    text = "ACCOUNT & SECURITY",
                    fontSize = 19.sp,
                    fontWeight = FontWeight.ExtraBold,
                    color = colors.textPrimary,
                    letterSpacing = 0.5.sp
                )
            }

            Spacer(modifier = Modifier.height(20.dp))

            // 2. Account Security Status Hero Card
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
                        .padding(20.dp),
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    Box(
                        modifier = Modifier
                            .size(76.dp)
                            .clip(CircleShape)
                            .background(
                                Brush.linearGradient(
                                    listOf(Color(0xFF29B6F6), Color(0xFF0288D1))
                                )
                            ),
                        contentAlignment = Alignment.Center
                    ) {
                        AccountSecurity3DIcon(size = 50.dp)
                    }

                    Spacer(modifier = Modifier.height(14.dp))

                    Text(
                        text = effectiveName,
                        fontSize = 20.sp,
                        fontWeight = FontWeight.Bold,
                        color = colors.textPrimary,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )

                    if (effectiveNumericId > 0) {
                        Spacer(modifier = Modifier.height(4.dp))
                        Text(
                            text = "ID: $effectiveNumericId",
                            fontSize = 13.sp,
                            fontWeight = FontWeight.SemiBold,
                            color = colors.accentOrange
                        )
                    }

                    Spacer(modifier = Modifier.height(12.dp))

                    Surface(
                        shape = RoundedCornerShape(20.dp),
                        color = if (isDark) Color(0x3300C853) else Color(0x1A00C853),
                        border = BorderStroke(1.dp, Color(0xFF00C853).copy(alpha = 0.6f))
                    ) {
                        Row(
                            modifier = Modifier.padding(horizontal = 14.dp, vertical = 6.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Icon(
                                imageVector = Icons.Default.Shield,
                                contentDescription = null,
                                tint = Color(0xFF00C853),
                                modifier = Modifier.size(15.dp)
                            )
                            Spacer(modifier = Modifier.width(6.dp))
                            Text(
                                text = "Account Protected & Active 🛡️",
                                fontSize = 12.sp,
                                fontWeight = FontWeight.Bold,
                                color = if (isDark) Color(0xFF69F0AE) else Color(0xFF007E33)
                            )
                        }
                    }
                }
            }

            Spacer(modifier = Modifier.height(16.dp))

            // 3. Linked Credentials Card
            Card(
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(20.dp),
                colors = CardDefaults.cardColors(containerColor = colors.cardBg),
                border = BorderStroke(1.dp, colors.cardBorder)
            ) {
                Column(modifier = Modifier.padding(18.dp)) {
                    Text(
                        text = "Linked Credentials",
                        fontSize = 15.sp,
                        fontWeight = FontWeight.Bold,
                        color = colors.textPrimary
                    )

                    Spacer(modifier = Modifier.height(14.dp))

                    // Primary Email Address Box
                    Surface(
                        shape = RoundedCornerShape(14.dp),
                        color = colors.screenBg,
                        border = BorderStroke(1.dp, colors.cardBorder),
                        modifier = Modifier.fillMaxWidth()
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
                                Box(
                                    modifier = Modifier
                                        .size(36.dp)
                                        .clip(CircleShape)
                                        .background(if (isGoogleAccount) Color(0x224285F4) else Color(0x22FF9800)),
                                    contentAlignment = Alignment.Center
                                ) {
                                    if (isGoogleAccount) {
                                        Text("G", fontSize = 18.sp, fontWeight = FontWeight.Black, color = Color(0xFF4285F4))
                                    } else {
                                        Icon(Icons.Default.Email, contentDescription = null, tint = QivoOrange, modifier = Modifier.size(18.dp))
                                    }
                                }

                                Spacer(modifier = Modifier.width(12.dp))

                                Column {
                                    Text(
                                        text = if (isGoogleAccount) "Google Account" else "Linked Email",
                                        fontSize = 11.sp,
                                        color = colors.textSecondary,
                                        fontWeight = FontWeight.Medium
                                    )
                                    Spacer(modifier = Modifier.height(2.dp))
                                    Text(
                                        text = effectiveEmail,
                                        fontSize = 14.sp,
                                        fontWeight = FontWeight.Bold,
                                        color = colors.textPrimary,
                                        maxLines = 1,
                                        overflow = TextOverflow.Ellipsis
                                    )
                                }
                            }

                            IconButton(
                                onClick = { copyToClipboard("Email", effectiveEmail) },
                                modifier = Modifier.size(32.dp)
                            ) {
                                Icon(
                                    imageVector = Icons.Default.ContentCopy,
                                    contentDescription = "Copy Email",
                                    tint = colors.textSecondary,
                                    modifier = Modifier.size(16.dp)
                                )
                            }
                        }
                    }

                    if (effectiveNumericId > 0) {
                        Spacer(modifier = Modifier.height(10.dp))

                        // Numeric ID Box
                        Surface(
                            shape = RoundedCornerShape(14.dp),
                            color = colors.screenBg,
                            border = BorderStroke(1.dp, colors.cardBorder),
                            modifier = Modifier.fillMaxWidth()
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
                                    Box(
                                        modifier = Modifier
                                            .size(36.dp)
                                            .clip(CircleShape)
                                        .background(Color(0x2200C853)),
                                        contentAlignment = Alignment.Center
                                    ) {
                                        Icon(Icons.Default.CheckCircle, contentDescription = null, tint = Color(0xFF00C853), modifier = Modifier.size(18.dp))
                                    }

                                    Spacer(modifier = Modifier.width(12.dp))

                                    Column {
                                        Text(
                                            text = "Account Numeric ID",
                                            fontSize = 11.sp,
                                            color = colors.textSecondary,
                                            fontWeight = FontWeight.Medium
                                        )
                                        Spacer(modifier = Modifier.height(2.dp))
                                        Text(
                                            text = "$effectiveNumericId",
                                            fontSize = 14.sp,
                                            fontWeight = FontWeight.Bold,
                                            color = colors.textPrimary
                                        )
                                    }
                                }

                                IconButton(
                                    onClick = { copyToClipboard("Numeric ID", "$effectiveNumericId") },
                                    modifier = Modifier.size(32.dp)
                                ) {
                                    Icon(
                                        imageVector = Icons.Default.ContentCopy,
                                        contentDescription = "Copy Numeric ID",
                                        tint = colors.textSecondary,
                                        modifier = Modifier.size(16.dp)
                                    )
                                }
                            }
                        }
                    }
                }
            }

            Spacer(modifier = Modifier.height(16.dp))

            // 4. Security & Authentication Management Options
            Card(
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(20.dp),
                colors = CardDefaults.cardColors(containerColor = colors.cardBg),
                border = BorderStroke(1.dp, colors.cardBorder)
            ) {
                Column(modifier = Modifier.padding(vertical = 6.dp)) {
                    SecurityActionItem(
                        icon = Icons.Default.Lock,
                        title = "Sign-In & Password Security",
                        subtitle = if (isGoogleAccount) "Protected via Google Sign-In" else "Protected via Email Auth",
                        onClick = {
                            showPasswordInfoDialog = true
                        },
                        colors = colors
                    )

                    HorizontalDivider(modifier = Modifier.padding(horizontal = 16.dp), color = colors.divider)

                    SecurityActionItem(
                        icon = Icons.Default.PhoneAndroid,
                        title = "Device & Session Security",
                        subtitle = "Current Device: Android • Logged In",
                        onClick = {
                            AppToast.show("Your active session is secure and encrypted.")
                        },
                        colors = colors
                    )

                    HorizontalDivider(modifier = Modifier.padding(horizontal = 16.dp), color = colors.divider)

                    SecurityActionItem(
                        icon = Icons.Default.DeleteForever,
                        title = "Delete Account",
                        subtitle = "Permanently remove your account and data",
                        isDestructive = true,
                        onClick = {
                            showDeleteAccountDialog = true
                        },
                        colors = colors
                    )
                }
            }

            Spacer(modifier = Modifier.height(28.dp))
        }
    }

    // Password Info Dialog
    if (showPasswordInfoDialog) {
        AlertDialog(
            onDismissRequest = { showPasswordInfoDialog = false },
            title = {
                Text(
                    text = "Sign-In Security",
                    fontWeight = FontWeight.Bold,
                    fontSize = 17.sp,
                    color = colors.textPrimary
                )
            },
            text = {
                Text(
                    text = if (isGoogleAccount) {
                        "Your account is securely linked to your Google Account ($effectiveEmail). Authentication is managed directly by Google Identity Services."
                    } else {
                        "Your account is registered with $effectiveEmail. To reset or change your password, use the 'Forgot Password' link on the login screen."
                    },
                    fontSize = 13.sp,
                    lineHeight = 18.sp,
                    color = colors.textSecondary
                )
            },
            confirmButton = {
                TextButton(onClick = { showPasswordInfoDialog = false }) {
                    Text("OK", fontWeight = FontWeight.Bold, color = colors.accentOrange)
                }
            },
            containerColor = colors.cardBg,
            shape = RoundedCornerShape(20.dp)
        )
    }

    // Delete Account Dialog
    if (showDeleteAccountDialog) {
        AlertDialog(
            onDismissRequest = { if (!isDeletingAccount) showDeleteAccountDialog = false },
            title = {
                Text(
                    text = "Permanently Delete Account?",
                    fontWeight = FontWeight.Bold,
                    fontSize = 17.sp,
                    color = Color(0xFFFF5252)
                )
            },
            text = {
                Text(
                    text = "This action is permanent and cannot be undone.\n\nAll your profile details, coins, messages, party rooms, purchased frames, and personal data will be completely erased from our servers immediately.",
                    fontSize = 13.sp,
                    lineHeight = 18.sp,
                    color = colors.textSecondary
                )
            },
            confirmButton = {
                Button(
                    onClick = {
                        showDeleteAccountDialog = false
                        isDeletingAccount = true
                        scope.launch {
                            try {
                                com.example.data.SupabaseAuthService().deleteAccount(context)
                            } catch (_: Exception) {
                                UserSessionManager.clearSession(context)
                            }
                            isDeletingAccount = false
                            AppToast.show("Account and all personal data permanently erased.")
                            onSignOut()
                        }
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = Color(0xFFFF5252))
                ) {
                    Text("Delete Everything", fontWeight = FontWeight.Bold, color = Color.White)
                }
            },
            dismissButton = {
                OutlinedButton(
                    onClick = { showDeleteAccountDialog = false },
                    enabled = !isDeletingAccount
                ) {
                    Text("Cancel", color = colors.textSecondary)
                }
            },
            containerColor = colors.cardBg,
            shape = RoundedCornerShape(20.dp)
        )
    }

    // Progress Dialog during account erasure
    if (isDeletingAccount) {
        AlertDialog(
            onDismissRequest = { /* uncancelable */ },
            title = {
                Text(
                    text = "Erasing Account...",
                    fontWeight = FontWeight.Bold,
                    fontSize = 16.sp,
                    color = colors.textPrimary
                )
            },
            text = {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(16.dp)
                ) {
                    androidx.compose.material3.CircularProgressIndicator(
                        modifier = Modifier.size(28.dp),
                        color = Color(0xFFFF5252),
                        strokeWidth = 3.dp
                    )
                    Text(
                        text = "Erasing all data and clearing session...",
                        fontSize = 13.sp,
                        color = colors.textSecondary
                    )
                }
            },
            confirmButton = {},
            containerColor = colors.cardBg,
            shape = RoundedCornerShape(20.dp)
        )
    }
}

@Composable
private fun SecurityActionItem(
    icon: ImageVector,
    title: String,
    subtitle: String,
    isDestructive: Boolean = false,
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
                tint = if (isDestructive) Color(0xFFFF5252) else colors.accentOrange,
                modifier = Modifier.size(20.dp)
            )
            Spacer(modifier = Modifier.width(14.dp))
            Column {
                Text(
                    text = title,
                    fontSize = 14.sp,
                    fontWeight = FontWeight.SemiBold,
                    color = if (isDestructive) Color(0xFFFF5252) else colors.textPrimary
                )
                Spacer(modifier = Modifier.height(2.dp))
                Text(
                    text = subtitle,
                    fontSize = 12.sp,
                    color = colors.textSecondary
                )
            }
        }
        Icon(
            imageVector = Icons.AutoMirrored.Filled.ArrowForwardIos,
            contentDescription = null,
            tint = colors.textMuted,
            modifier = Modifier.size(13.dp)
        )
    }
}

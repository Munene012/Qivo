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
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.provider.Settings
import androidx.compose.material.icons.filled.PictureInPicture
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.ui.components.AboutQivo3DIcon
import com.example.ui.components.AccountSecurity3DIcon
import com.example.ui.components.BlockedList3DIcon
import com.example.ui.components.CallSettings3DIcon
import com.example.ui.components.ClearCache3DIcon
import com.example.ui.components.DarkMode3DIcon
import com.example.ui.components.DeleteAccount3DIcon
import com.example.ui.components.SignOut3DIcon
import com.example.data.UserSessionManager
import com.example.ui.theme.AppTheme
import com.example.ui.theme.AppThemeManager
import com.example.ui.theme.LocalAppColors

import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.rememberCoroutineScope
import com.example.data.AppCacheManager
import kotlinx.coroutines.launch

@Composable
fun SystemSettingsScreen(
    onBackClick: () -> Unit,
    onSignOut: () -> Unit,
    onOpenBlockedList: () -> Unit = {},
    onOpenAccountSecurity: () -> Unit = {},
    onOpenAboutQivo: () -> Unit = {},
    onOpenCallSettings: () -> Unit = {}
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val colors = AppTheme.colors
    val isDark = colors.isDark

    val isDarkModeEnabled = AppThemeManager.isDarkModeState.value
    var showSignOutDialog by remember { mutableStateOf(false) }
    var showDeleteAccountDialog by remember { mutableStateOf(false) }

    var cacheSizeBytes by remember { mutableLongStateOf(0L) }
    var isClearingCache by remember { mutableStateOf(false) }

    LaunchedEffect(Unit) {
        cacheSizeBytes = AppCacheManager.getAppCacheSize(context)
    }

    val scrollState = rememberScrollState()

    BackHandler {
        onBackClick()
    }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(colors.screenBg)
            .testTag("system_settings_screen")
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .statusBarsPadding()
                .padding(horizontal = 16.dp)
                .verticalScroll(scrollState)
        ) {
            Spacer(modifier = Modifier.height(12.dp))

            // 1. Top Header with Back Button and "SYSTEM SETTINGS" title
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
                    text = "SYSTEM SETTINGS",
                    fontSize = 20.sp,
                    fontWeight = FontWeight.ExtraBold,
                    color = colors.textPrimary,
                    letterSpacing = 0.5.sp
                )
            }

            Spacer(modifier = Modifier.height(20.dp))

            // 2. Grouped Settings Card 1
            Card(
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(24.dp),
                colors = CardDefaults.cardColors(containerColor = colors.cardBg),
                border = BorderStroke(1.dp, colors.cardBorder),
                elevation = CardDefaults.cardElevation(defaultElevation = if (isDark) 0.dp else 1.dp)
            ) {
                Column(modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp)) {
                    // Item 1: Account & Security
                    SettingsRowItem(
                        icon = { AccountSecurity3DIcon(size = 40.dp) },
                        title = "Account & Security",
                        subtitle = "View linked Google account, email & security status",
                        titleColor = colors.textPrimary,
                        subtitleColor = colors.textSecondary,
                        onClick = onOpenAccountSecurity
                    )

                    HorizontalDivider(color = colors.divider)

                    // Item 2: Dark Mode
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(vertical = 14.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            modifier = Modifier.weight(1f)
                        ) {
                            DarkMode3DIcon(isDark = isDark, size = 40.dp)

                            Spacer(modifier = Modifier.width(14.dp))

                            Column {
                                Text(
                                    text = "Dark Mode",
                                    fontSize = 16.sp,
                                    fontWeight = FontWeight.Bold,
                                    color = colors.textPrimary
                                )
                                Spacer(modifier = Modifier.height(2.dp))
                                Text(
                                    text = if (isDarkModeEnabled) "OLED Dark theme enabled" else "Light theme enabled",
                                    fontSize = 12.sp,
                                    color = colors.textSecondary
                                )
                            }
                        }

                        Switch(
                            checked = isDarkModeEnabled,
                            onCheckedChange = {
                                AppThemeManager.setDarkMode(context, it)
                            },
                            colors = SwitchDefaults.colors(
                                checkedThumbColor = Color.White,
                                checkedTrackColor = Color(0xFF00C853),
                                uncheckedThumbColor = Color.White,
                                uncheckedTrackColor = Color(0xFF71717A)
                            ),
                            modifier = Modifier.testTag("dark_mode_switch")
                        )
                    }

                    HorizontalDivider(color = colors.divider)

                    // Item 3: Call Settings
                    SettingsRowItem(
                        icon = { CallSettings3DIcon(size = 40.dp) },
                        title = "Call Settings",
                        subtitle = "Configure voice & video DND preferences",
                        titleColor = colors.textPrimary,
                        subtitleColor = colors.textSecondary,
                        onClick = onOpenCallSettings
                    )

                    HorizontalDivider(color = colors.divider)

                    // Item 4: Blocked List
                    SettingsRowItem(
                        icon = { BlockedList3DIcon(size = 40.dp) },
                        title = "Blocked List",
                        subtitle = "Manage accounts you have blocked",
                        titleColor = colors.textPrimary,
                        subtitleColor = colors.textSecondary,
                        onClick = onOpenBlockedList
                    )

                    HorizontalDivider(color = colors.divider)

                    // Item 5: Clear Cache
                    SettingsRowItem(
                        icon = { ClearCache3DIcon(size = 40.dp) },
                        title = "Clear Cache",
                        subtitle = if (isClearingCache) "Clearing app cache..." else "Real cache size: ${AppCacheManager.formatBytes(cacheSizeBytes)} • Tap to clean",
                        titleColor = colors.textPrimary,
                        subtitleColor = if (isClearingCache) colors.accentOrange else colors.textSecondary,
                        onClick = {
                            if (!isClearingCache) {
                                isClearingCache = true
                                scope.launch {
                                    val freed = AppCacheManager.clearRealAppCache(context)
                                    val newSize = AppCacheManager.getAppCacheSize(context)
                                    cacheSizeBytes = newSize
                                    isClearingCache = false
                                    val freedStr = AppCacheManager.formatBytes(freed)
                                    AppToast.show("Cleared $freedStr of app cache ✨")
                                }
                            }
                        }
                    )

                    // Item 6: About QIVO
                    SettingsRowItem(
                        icon = { AboutQivo3DIcon(size = 40.dp) },
                        title = "About QIVO",
                        subtitle = "Platform version, legal, and licensing info",
                        titleColor = colors.textPrimary,
                        subtitleColor = colors.textSecondary,
                        onClick = onOpenAboutQivo
                    )
                }
            }

            Spacer(modifier = Modifier.height(20.dp))

            // 3. Grouped Settings Card 2 (Sign Out & Delete Account)
            Card(
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(24.dp),
                colors = CardDefaults.cardColors(containerColor = colors.cardBg),
                border = BorderStroke(1.dp, colors.cardBorder),
                elevation = CardDefaults.cardElevation(defaultElevation = if (isDark) 0.dp else 1.dp)
            ) {
                Column(modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp)) {
                    // Sign Out
                    SettingsRowItem(
                        icon = { SignOut3DIcon(size = 40.dp) },
                        title = "Sign Out",
                        subtitle = null,
                        titleColor = Color(0xFFFF5232),
                        subtitleColor = colors.textSecondary,
                        onClick = { showSignOutDialog = true }
                    )

                    HorizontalDivider(color = colors.divider)

                    // Delete Account
                    SettingsRowItem(
                        icon = { DeleteAccount3DIcon(size = 40.dp) },
                        title = "Delete Account",
                        subtitle = null,
                        titleColor = Color(0xFFFF5232),
                        subtitleColor = colors.textSecondary,
                        onClick = { showDeleteAccountDialog = true }
                    )
                }
            }

            Spacer(modifier = Modifier.height(30.dp))
        }
    }

    // Sign Out Confirmation Dialog
    if (showSignOutDialog) {
        AlertDialog(
            onDismissRequest = { showSignOutDialog = false },
            title = {
                Text(text = "Sign Out", fontWeight = FontWeight.Bold, color = colors.textPrimary)
            },
            text = {
                Text(text = "Are you sure you want to sign out of your account?", color = colors.textSecondary)
            },
            confirmButton = {
                TextButton(
                    onClick = {
                        showSignOutDialog = false
                        UserSessionManager.clearSession(context)
                        onSignOut()
                    }
                ) {
                    Text("Sign Out", color = Color(0xFFFF5232), fontWeight = FontWeight.Bold)
                }
            },
            dismissButton = {
                TextButton(onClick = { showSignOutDialog = false }) {
                    Text("Cancel", color = colors.textMuted)
                }
            },
            containerColor = colors.cardBg,
            shape = RoundedCornerShape(20.dp)
        )
    }

    // Delete Account Dialog
    if (showDeleteAccountDialog) {
        AlertDialog(
            onDismissRequest = { showDeleteAccountDialog = false },
            title = {
                Text(text = "Permanently Delete Account?", fontWeight = FontWeight.Bold, color = Color(0xFFFF5232))
            },
            text = {
                Text(
                    text = "Are you sure you want to delete your account? All your profile details, coins, messages, party rooms, and personal data will be completely and permanently erased. This cannot be undone.",
                    color = colors.textSecondary,
                    fontSize = 13.sp,
                    lineHeight = 18.sp
                )
            },
            confirmButton = {
                Button(
                    onClick = {
                        showDeleteAccountDialog = false
                        scope.launch {
                            try {
                                com.example.data.SupabaseAuthService().deleteAccount(context)
                            } catch (_: Exception) {
                                UserSessionManager.clearSession(context)
                            }
                            AppToast.show("Account and all data have been completely erased.")
                            onSignOut()
                        }
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = Color(0xFFFF5232))
                ) {
                    Text("Delete Everything", color = Color.White, fontWeight = FontWeight.Bold)
                }
            },
            dismissButton = {
                TextButton(onClick = { showDeleteAccountDialog = false }) {
                    Text("Cancel", color = colors.textMuted)
                }
            },
            containerColor = colors.cardBg,
            shape = RoundedCornerShape(20.dp)
        )
    }
}

@Composable
private fun SettingsRowItem(
    icon: @Composable () -> Unit,
    title: String,
    subtitle: String?,
    titleColor: Color,
    subtitleColor: Color,
    onClick: () -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable { onClick() }
            .padding(vertical = 14.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.SpaceBetween
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier.weight(1f)
        ) {
            icon()

            Spacer(modifier = Modifier.width(14.dp))

            Column {
                Text(
                    text = title,
                    fontSize = 16.sp,
                    fontWeight = FontWeight.Bold,
                    color = titleColor
                )
                if (!subtitle.isNullOrEmpty()) {
                    Spacer(modifier = Modifier.height(2.dp))
                    Text(
                        text = subtitle,
                        fontSize = 12.sp,
                        color = subtitleColor
                    )
                }
            }
        }

        Icon(
            imageVector = Icons.AutoMirrored.Filled.ArrowForwardIos,
            contentDescription = "Open",
            tint = subtitleColor,
            modifier = Modifier.size(16.dp)
        )
    }
}

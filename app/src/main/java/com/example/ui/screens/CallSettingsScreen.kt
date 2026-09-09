package com.example.ui.screens

import androidx.activity.compose.BackHandler
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
import androidx.compose.foundation.layout.navigationBarsPadding
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
import androidx.compose.material.icons.filled.Call
import androidx.compose.material.icons.filled.CallEnd
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.DoNotDisturbOn
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.Security
import androidx.compose.material.icons.filled.Videocam
import androidx.compose.material.icons.filled.VideocamOff
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
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
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.data.UserSessionManager
import com.example.ui.components.AppToast
import com.example.ui.theme.QivoOrange
import com.example.ui.theme.QivoYellow

/**
 * Call Settings Screen
 * Enables users to toggle Do Not Disturb (DND) for Voice and Video calls separately.
 * When DND is active, incoming calls of that type are blocked without ringing,
 * and the caller is notified that the user is on DND mode.
 */
@Composable
fun CallSettingsScreen(
    onBackClick: () -> Unit
) {
    val context = LocalContext.current
    BackHandler { onBackClick() }

    val dndVoiceFlowVal by UserSessionManager.dndVoiceFlow.collectAsState()
    val dndVideoFlowVal by UserSessionManager.dndVideoFlow.collectAsState()

    var isVoiceDnd by remember(dndVoiceFlowVal) {
        mutableStateOf(UserSessionManager.isDndVoiceEnabled(context))
    }
    var isVideoDnd by remember(dndVideoFlowVal) {
        mutableStateOf(UserSessionManager.isDndVideoEnabled(context))
    }

    val isDark = true
    val bgColor = if (isDark) Color(0xFF0F0E17) else Color(0xFFF8F9FA)
    val cardBg = if (isDark) Color(0xFF1B1A26) else Color.White
    val textPrimary = if (isDark) Color.White else Color(0xFF1E293B)
    val textSecondary = if (isDark) Color(0xFF94A3B8) else Color(0xFF64748B)

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(bgColor)
            .statusBarsPadding()
            .navigationBarsPadding()
            .testTag("call_settings_screen")
    ) {
        // Top Navigation Bar
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp, vertical = 12.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            IconButton(
                onClick = onBackClick,
                modifier = Modifier
                    .size(40.dp)
                    .clip(CircleShape)
                    .background(cardBg)
                    .testTag("btn_call_settings_back")
            ) {
                Icon(
                    imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                    contentDescription = "Back",
                    tint = textPrimary
                )
            }

            Spacer(modifier = Modifier.width(16.dp))

            Column {
                Text(
                    text = "Call Settings",
                    fontSize = 20.sp,
                    fontWeight = FontWeight.Bold,
                    color = textPrimary
                )
                Text(
                    text = "Do Not Disturb (DND) & privacy",
                    fontSize = 12.sp,
                    color = textSecondary
                )
            }
        }

        Column(
            modifier = Modifier
                .fillMaxSize()
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 16.dp, vertical = 8.dp)
        ) {
            // Header Info Banner
            Card(
                shape = RoundedCornerShape(16.dp),
                colors = CardDefaults.cardColors(
                    containerColor = Color(0xFF231F3D)
                ),
                border = BorderStroke(1.dp, Color(0xFF7C4DFF).copy(alpha = 0.35f)),
                modifier = Modifier.fillMaxWidth()
            ) {
                Row(
                    modifier = Modifier.padding(16.dp),
                    verticalAlignment = Alignment.Top
                ) {
                    Box(
                        modifier = Modifier
                            .size(38.dp)
                            .clip(CircleShape)
                            .background(Color(0xFF7C4DFF).copy(alpha = 0.2f)),
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(
                            imageVector = Icons.Default.DoNotDisturbOn,
                            contentDescription = null,
                            tint = Color(0xFFB388FF),
                            modifier = Modifier.size(22.dp)
                        )
                    }

                    Spacer(modifier = Modifier.width(12.dp))

                    Column {
                        Text(
                            text = "Granular Do Not Disturb",
                            fontSize = 15.sp,
                            fontWeight = FontWeight.Bold,
                            color = Color.White
                        )
                        Spacer(modifier = Modifier.height(4.dp))
                        Text(
                            text = "Toggle DND on voice and video calls independently. When enabled, incoming calls won't ring your phone, and callers will immediately be informed you are in DND mode.",
                            fontSize = 12.sp,
                            color = Color(0xFFCBD5E1),
                            lineHeight = 17.sp
                        )
                    }
                }
            }

            Spacer(modifier = Modifier.height(20.dp))

            Text(
                text = "INDEPENDENT CALL CONTROLS",
                fontSize = 12.sp,
                fontWeight = FontWeight.Bold,
                color = QivoOrange,
                letterSpacing = 1.sp,
                modifier = Modifier.padding(start = 4.dp, bottom = 8.dp)
            )

            // 1. Voice Call DND Setting Card
            DndCallSettingCard(
                title = "Voice Call DND",
                subtitle = "Silence and block all incoming 1-on-1 voice calls",
                activeNotice = "When active, voice callers are notified you're on DND and calls will not go through.",
                icon = if (isVoiceDnd) Icons.Default.CallEnd else Icons.Default.Call,
                iconColor = if (isVoiceDnd) Color(0xFFFF5252) else QivoYellow,
                isEnabled = isVoiceDnd,
                testTag = "switch_dnd_voice",
                cardBg = cardBg,
                textPrimary = textPrimary,
                textSecondary = textSecondary,
                onCheckedChange = { checked ->
                    isVoiceDnd = checked
                    UserSessionManager.setDndVoiceEnabled(context, checked)
                    if (checked) {
                        AppToast.show("Voice Call DND activated. Voice calls are now blocked.", isLong = false)
                    } else {
                        AppToast.show("Voice Call DND removed. You can now receive voice calls.", isLong = false)
                    }
                }
            )

            Spacer(modifier = Modifier.height(14.dp))

            // 2. Video Call DND Setting Card
            DndCallSettingCard(
                title = "Video Call DND",
                subtitle = "Silence and block all incoming 1-on-1 video calls",
                activeNotice = "When active, video callers are notified you're on DND and video calls will not go through.",
                icon = if (isVideoDnd) Icons.Default.VideocamOff else Icons.Default.Videocam,
                iconColor = if (isVideoDnd) Color(0xFFFF5252) else QivoOrange,
                isEnabled = isVideoDnd,
                testTag = "switch_dnd_video",
                cardBg = cardBg,
                textPrimary = textPrimary,
                textSecondary = textSecondary,
                onCheckedChange = { checked ->
                    isVideoDnd = checked
                    UserSessionManager.setDndVideoEnabled(context, checked)
                    if (checked) {
                        AppToast.show("Video Call DND activated. Video calls are now blocked.", isLong = false)
                    } else {
                        AppToast.show("Video Call DND removed. You can now receive video calls.", isLong = false)
                    }
                }
            )

            Spacer(modifier = Modifier.height(24.dp))

            Text(
                text = "HOW DND WORKS FOR CALLERS",
                fontSize = 12.sp,
                fontWeight = FontWeight.Bold,
                color = textSecondary,
                letterSpacing = 1.sp,
                modifier = Modifier.padding(start = 4.dp, bottom = 8.dp)
            )

            // Explanatory Card
            Card(
                shape = RoundedCornerShape(16.dp),
                colors = CardDefaults.cardColors(containerColor = cardBg),
                border = BorderStroke(1.dp, if (isDark) Color(0xFF2D2B3D) else Color(0xFFE2E8F0)),
                modifier = Modifier.fillMaxWidth()
            ) {
                Column(modifier = Modifier.padding(16.dp)) {
                    DndRuleBullet(
                        icon = Icons.Default.CheckCircle,
                        iconTint = Color(0xFF00E676),
                        title = "Separate Voice & Video Protection",
                        description = "You can block video calls while still staying available for voice calls, or block both for total privacy.",
                        textPrimary = textPrimary,
                        textSecondary = textSecondary
                    )

                    HorizontalDivider(
                        modifier = Modifier.padding(vertical = 12.dp),
                        color = if (isDark) Color(0xFF2D2B3D) else Color(0xFFF1F5F9)
                    )

                    DndRuleBullet(
                        icon = Icons.Default.Info,
                        iconTint = Color(0xFF64B5F6),
                        title = "Real-time Caller Notice",
                        description = "The caller's screen will instantly display: \"User is on Do Not Disturb mode\" and the call will immediately disconnect.",
                        textPrimary = textPrimary,
                        textSecondary = textSecondary
                    )

                    HorizontalDivider(
                        modifier = Modifier.padding(vertical = 12.dp),
                        color = if (isDark) Color(0xFF2D2B3D) else Color(0xFFF1F5F9)
                    )

                    DndRuleBullet(
                        icon = Icons.Default.Security,
                        iconTint = Color(0xFFFFB74D),
                        title = "Zero Interruption",
                        description = "Your device will not ring, vibrate, or display an incoming call screen when DND is active.",
                        textPrimary = textPrimary,
                        textSecondary = textSecondary
                    )
                }
            }

            Spacer(modifier = Modifier.height(30.dp))
        }
    }
}

@Composable
private fun DndCallSettingCard(
    title: String,
    subtitle: String,
    activeNotice: String,
    icon: ImageVector,
    iconColor: Color,
    isEnabled: Boolean,
    testTag: String,
    cardBg: Color,
    textPrimary: Color,
    textSecondary: Color,
    onCheckedChange: (Boolean) -> Unit
) {
    Card(
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(containerColor = cardBg),
        border = BorderStroke(
            1.dp,
            if (isEnabled) Color(0xFFFF5252).copy(alpha = 0.5f) else Color(0xFF2D2B3D)
        ),
        modifier = Modifier.fillMaxWidth()
    ) {
        Column(
            modifier = Modifier.padding(16.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier.weight(1f)
                ) {
                    Box(
                        modifier = Modifier
                            .size(44.dp)
                            .clip(RoundedCornerShape(12.dp))
                            .background(
                                if (isEnabled) Color(0xFFFF5252).copy(alpha = 0.15f)
                                else Color.White.copy(alpha = 0.08f)
                            ),
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(
                            imageVector = icon,
                            contentDescription = null,
                            tint = iconColor,
                            modifier = Modifier.size(24.dp)
                        )
                    }

                    Spacer(modifier = Modifier.width(14.dp))

                    Column {
                        Text(
                            text = title,
                            fontSize = 16.sp,
                            fontWeight = FontWeight.Bold,
                            color = textPrimary
                        )
                        Spacer(modifier = Modifier.height(2.dp))
                        Text(
                            text = subtitle,
                            fontSize = 12.sp,
                            color = textSecondary
                        )
                    }
                }

                Spacer(modifier = Modifier.width(8.dp))

                Switch(
                    checked = isEnabled,
                    onCheckedChange = onCheckedChange,
                    colors = SwitchDefaults.colors(
                        checkedThumbColor = Color.White,
                        checkedTrackColor = Color(0xFFFF5252),
                        uncheckedThumbColor = Color(0xFF94A3B8),
                        uncheckedTrackColor = Color(0xFF334155)
                    ),
                    modifier = Modifier.testTag(testTag)
                )
            }

            Spacer(modifier = Modifier.height(10.dp))

            // Status Indicator Pill
            Surface(
                shape = RoundedCornerShape(8.dp),
                color = if (isEnabled) Color(0xFFFF5252).copy(alpha = 0.12f) else Color(0xFF00E676).copy(alpha = 0.12f),
                border = BorderStroke(
                    1.dp,
                    if (isEnabled) Color(0xFFFF5252).copy(alpha = 0.3f) else Color(0xFF00E676).copy(alpha = 0.3f)
                )
            ) {
                Row(
                    modifier = Modifier.padding(horizontal = 10.dp, vertical = 6.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Box(
                        modifier = Modifier
                            .size(8.dp)
                            .clip(CircleShape)
                            .background(if (isEnabled) Color(0xFFFF5252) else Color(0xFF00E676))
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(
                        text = if (isEnabled) "DND ACTIVE • Calls are blocked" else "NORMAL • Ready to receive calls",
                        fontSize = 11.sp,
                        fontWeight = FontWeight.SemiBold,
                        color = if (isEnabled) Color(0xFFFF5252) else Color(0xFF00E676)
                    )
                }
            }

            if (isEnabled) {
                Spacer(modifier = Modifier.height(8.dp))
                Text(
                    text = activeNotice,
                    fontSize = 11.sp,
                    color = Color(0xFFFF8A80),
                    lineHeight = 15.sp
                )
            }
        }
    }
}

@Composable
private fun DndRuleBullet(
    icon: ImageVector,
    iconTint: Color,
    title: String,
    description: String,
    textPrimary: Color,
    textSecondary: Color
) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        verticalAlignment = Alignment.Top
    ) {
        Icon(
            imageVector = icon,
            contentDescription = null,
            tint = iconTint,
            modifier = Modifier
                .size(20.dp)
                .padding(top = 2.dp)
        )
        Spacer(modifier = Modifier.width(10.dp))
        Column {
            Text(
                text = title,
                fontSize = 14.sp,
                fontWeight = FontWeight.SemiBold,
                color = textPrimary
            )
            Spacer(modifier = Modifier.height(2.dp))
            Text(
                text = description,
                fontSize = 12.sp,
                color = textSecondary,
                lineHeight = 16.sp
            )
        }
    }
}

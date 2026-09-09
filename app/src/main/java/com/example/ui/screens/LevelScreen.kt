package com.example.ui.screens

import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
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
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.ElectricBolt
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.Shield
import androidx.compose.material.icons.filled.Visibility
import androidx.compose.material.icons.filled.WorkspacePremium
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.data.UserLevelManager
import com.example.data.UserSessionManager
import com.example.ui.components.Level3DIcon
import com.example.ui.theme.AppTheme
import com.example.ui.theme.QivoOrange
import com.example.ui.theme.QivoYellow

/**
 * Clean & Minimalist LevelScreen
 * Focuses purely on essential information:
 * - Current level status & EXP progress
 * - Level 4 Profile Visitors Radar unlock status
 * - 5 Key milestone tiers
 * - Quick EXP conversion guide
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun LevelScreen(
    currentUserId: String = "",
    onBackClick: () -> Unit,
    onNavigateToRecharge: () -> Unit = {},
    onOpenRecharge: () -> Unit = onNavigateToRecharge,
    onNavigateToVisitors: () -> Unit = {}
) {
    val context = LocalContext.current
    val colors = AppTheme.colors
    val isDark = colors.isDark

    val expState by UserSessionManager.expFlow.collectAsState()
    val session = remember { UserSessionManager.getSession(context) }
    val currentExp = expState ?: session?.exp ?: UserSessionManager.getExp(context)
    val levelInfo = remember(currentExp) { UserLevelManager.getLevelInfo(currentExp) }

    val animatedProgress by animateFloatAsState(
        targetValue = levelInfo.progressPercent,
        animationSpec = tween(durationMillis = 800, easing = FastOutSlowInEasing),
        label = "level_progress"
    )

    Scaffold(
        modifier = Modifier
            .fillMaxSize()
            .statusBarsPadding()
            .navigationBarsPadding(),
        topBar = {
            TopAppBar(
                title = {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text(
                            text = "Level & EXP",
                            fontWeight = FontWeight.Bold,
                            fontSize = 20.sp,
                            color = colors.textPrimary
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                        Surface(
                            shape = RoundedCornerShape(12.dp),
                            color = levelInfo.badgeColor.copy(alpha = 0.2f),
                            border = BorderStroke(1.dp, levelInfo.badgeColor.copy(alpha = 0.5f))
                        ) {
                            Text(
                                text = "Lv.${levelInfo.level}",
                                fontSize = 12.sp,
                                fontWeight = FontWeight.Bold,
                                color = levelInfo.badgeColor,
                                modifier = Modifier.padding(horizontal = 8.dp, vertical = 3.dp)
                            )
                        }
                    }
                },
                navigationIcon = {
                    IconButton(
                        onClick = onBackClick,
                        modifier = Modifier.testTag("btn_level_back")
                    ) {
                        Icon(
                            imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                            contentDescription = "Back",
                            tint = colors.textPrimary
                        )
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = colors.topBarBg
                )
            )
        },
        containerColor = colors.screenBg
    ) { innerPadding ->
        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding),
            contentPadding = PaddingValues(horizontal = 16.dp, vertical = 12.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp)
        ) {
            // 1. HERO PROGRESS CARD
            item {
                Card(
                    modifier = Modifier
                        .fillMaxWidth()
                        .testTag("card_level_hero"),
                    shape = RoundedCornerShape(22.dp),
                    colors = CardDefaults.cardColors(
                        containerColor = if (isDark) Color(0xFF1B1A26) else Color(0xFFFFFBEA)
                    ),
                    border = BorderStroke(1.5.dp, Brush.linearGradient(levelInfo.badgeGradient))
                ) {
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(20.dp),
                        horizontalAlignment = Alignment.CenterHorizontally
                    ) {
                        // 3D Level Badge Icon
                        Box(
                            modifier = Modifier
                                .size(80.dp)
                                .clip(CircleShape)
                                .background(
                                    Brush.radialGradient(
                                        listOf(levelInfo.badgeColor.copy(alpha = 0.3f), Color.Transparent)
                                    )
                                ),
                            contentAlignment = Alignment.Center
                        ) {
                            Level3DIcon(size = 68.dp, level = levelInfo.level)
                        }

                        Spacer(modifier = Modifier.height(8.dp))

                        // Title Chip
                        Surface(
                            shape = RoundedCornerShape(12.dp),
                            color = levelInfo.badgeColor.copy(alpha = 0.15f),
                            border = BorderStroke(1.dp, levelInfo.badgeColor.copy(alpha = 0.4f))
                        ) {
                            Row(
                                modifier = Modifier.padding(horizontal = 12.dp, vertical = 4.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Icon(
                                    imageVector = Icons.Default.WorkspacePremium,
                                    contentDescription = null,
                                    tint = levelInfo.badgeColor,
                                    modifier = Modifier.size(15.dp)
                                )
                                Spacer(modifier = Modifier.width(6.dp))
                                Text(
                                    text = "LEVEL ${levelInfo.level} • ${levelInfo.title}",
                                    fontSize = 13.sp,
                                    fontWeight = FontWeight.Bold,
                                    color = levelInfo.badgeColor
                                )
                            }
                        }

                        Spacer(modifier = Modifier.height(12.dp))

                        // Total EXP
                        Text(
                            text = "%,d EXP".format(levelInfo.currentExp),
                            fontSize = 30.sp,
                            fontWeight = FontWeight.Black,
                            color = colors.textPrimary
                        )

                        Text(
                            text = if (levelInfo.isMaxLevel) {
                                "Maximum level reached!"
                            } else {
                                "%,d EXP needed for Level %d".format(
                                    (levelInfo.expNeededInCurrentLevel - levelInfo.expInCurrentLevel).coerceAtLeast(0L),
                                    levelInfo.level + 1
                                )
                            },
                            fontSize = 12.sp,
                            color = colors.textSecondary,
                            modifier = Modifier.padding(top = 2.dp, bottom = 12.dp)
                        )

                        // Clean Progress Bar
                        Column(modifier = Modifier.fillMaxWidth()) {
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween
                            ) {
                                Text(
                                    text = "Lv.${levelInfo.level}",
                                    fontSize = 12.sp,
                                    fontWeight = FontWeight.Bold,
                                    color = levelInfo.badgeColor
                                )
                                Text(
                                    text = if (levelInfo.isMaxLevel) "MAX" else "${(animatedProgress * 100).toInt()}%",
                                    fontSize = 12.sp,
                                    fontWeight = FontWeight.Bold,
                                    color = colors.textPrimary
                                )
                                Text(
                                    text = if (levelInfo.isMaxLevel) "Lv.50" else "Lv.${levelInfo.level + 1}",
                                    fontSize = 12.sp,
                                    fontWeight = FontWeight.Bold,
                                    color = colors.textSecondary
                                )
                            }

                            Spacer(modifier = Modifier.height(6.dp))

                            LinearProgressIndicator(
                                progress = { animatedProgress },
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .height(8.dp)
                                    .clip(RoundedCornerShape(4.dp)),
                                color = QivoYellow,
                                trackColor = if (isDark) Color(0xFF2C2A3A) else Color(0xFFE2E8F0)
                            )
                        }

                        Spacer(modifier = Modifier.height(14.dp))

                        // Fast EXP Recharge CTA
                        Button(
                            onClick = onNavigateToRecharge,
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(44.dp)
                                .testTag("btn_level_get_coins"),
                            shape = RoundedCornerShape(12.dp),
                            colors = ButtonDefaults.buttonColors(
                                containerColor = QivoYellow,
                                contentColor = Color.Black
                            )
                        ) {
                            Icon(
                                imageVector = Icons.Default.ElectricBolt,
                                contentDescription = null,
                                modifier = Modifier.size(16.dp)
                            )
                            Spacer(modifier = Modifier.width(6.dp))
                            Text(
                                text = "Get Coins (1 Coin = 1 EXP)",
                                fontSize = 13.sp,
                                fontWeight = FontWeight.Bold
                            )
                        }
                    }
                }
            }

            // 2. ESSENTIAL PRIVILEGE: PROFILE VISITORS RADAR (LEVEL 4)
            item {
                val isUnlocked = levelInfo.isVisitorsUnlocked
                val expNeededForLevel4 = (UserLevelManager.getLevelThreshold(4) - levelInfo.currentExp).coerceAtLeast(0L)

                Card(
                    shape = RoundedCornerShape(18.dp),
                    colors = CardDefaults.cardColors(
                        containerColor = if (isUnlocked) {
                            if (isDark) Color(0xFF132B1A) else Color(0xFFE8F8EE)
                        } else {
                            if (isDark) Color(0xFF241D17) else Color(0xFFFFF3E0)
                        }
                    ),
                    border = BorderStroke(
                        1.dp,
                        if (isUnlocked) Color(0xFF00E676).copy(alpha = 0.5f) else Color(0xFFFF9800).copy(alpha = 0.5f)
                    ),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(16.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Surface(
                            modifier = Modifier.size(44.dp),
                            shape = CircleShape,
                            color = if (isUnlocked) Color(0xFF00E676).copy(alpha = 0.2f) else Color(0xFFFF9800).copy(alpha = 0.2f)
                        ) {
                            Box(contentAlignment = Alignment.Center) {
                                Icon(
                                    imageVector = if (isUnlocked) Icons.Default.Visibility else Icons.Default.Lock,
                                    contentDescription = null,
                                    tint = if (isUnlocked) Color(0xFF00E676) else Color(0xFFFF9800),
                                    modifier = Modifier.size(22.dp)
                                )
                            }
                        }

                        Spacer(modifier = Modifier.width(12.dp))

                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                text = if (isUnlocked) "Profile Visitors Radar Active" else "Profile Visitors Radar (Level 4)",
                                fontWeight = FontWeight.Bold,
                                fontSize = 14.sp,
                                color = colors.textPrimary
                            )
                            Spacer(modifier = Modifier.height(2.dp))
                            Text(
                                text = if (isUnlocked) {
                                    "Your Level ${levelInfo.level} grants full access to view who visits your profile."
                                } else {
                                    "Reach Level 4 (%,d more EXP needed) to unlock.".format(expNeededForLevel4)
                                },
                                fontSize = 12.sp,
                                color = colors.textSecondary
                            )
                        }

                        if (isUnlocked) {
                            Button(
                                onClick = onNavigateToVisitors,
                                shape = RoundedCornerShape(10.dp),
                                colors = ButtonDefaults.buttonColors(
                                    containerColor = Color(0xFF00E676),
                                    contentColor = Color.Black
                                ),
                                contentPadding = PaddingValues(horizontal = 12.dp, vertical = 6.dp)
                            ) {
                                Text("View", fontSize = 12.sp, fontWeight = FontWeight.Bold)
                            }
                        }
                    }
                }
            }

            // 3. MILESTONE TIERS (5 Clear Groups)
            item {
                Text(
                    text = "PROGRESSION TIERS",
                    fontSize = 12.sp,
                    fontWeight = FontWeight.Bold,
                    color = colors.textSecondary,
                    letterSpacing = 1.sp,
                    modifier = Modifier.padding(start = 4.dp, top = 4.dp)
                )
            }

            item {
                Card(
                    shape = RoundedCornerShape(18.dp),
                    colors = CardDefaults.cardColors(containerColor = colors.cardBg),
                    border = BorderStroke(1.dp, colors.divider),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Column(modifier = Modifier.padding(14.dp)) {
                        TierRow(
                            range = "Lv. 1 – 9",
                            title = "Bronze Novice",
                            badgeColor = Color(0xFFCD7F32),
                            isCurrent = levelInfo.level in 1..9,
                            isReached = levelInfo.level >= 1,
                            perk = "Standard badge & Level 4 Visitor Radar"
                        )
                        HorizontalDivider(modifier = Modifier.padding(vertical = 10.dp), color = colors.divider)
                        TierRow(
                            range = "Lv. 10 – 19",
                            title = "Silver Explorer",
                            badgeColor = Color(0xFFC0C0C0),
                            isCurrent = levelInfo.level in 10..19,
                            isReached = levelInfo.level >= 10,
                            perk = "Silver chat badge & room highlight"
                        )
                        HorizontalDivider(modifier = Modifier.padding(vertical = 10.dp), color = colors.divider)
                        TierRow(
                            range = "Lv. 20 – 29",
                            title = "Gold Champion",
                            badgeColor = Color(0xFFFFD700),
                            isCurrent = levelInfo.level in 20..29,
                            isReached = levelInfo.level >= 20,
                            perk = "Gold badge & prioritized matching"
                        )
                        HorizontalDivider(modifier = Modifier.padding(vertical = 10.dp), color = colors.divider)
                        TierRow(
                            range = "Lv. 30 – 39",
                            title = "Diamond Monarch",
                            badgeColor = Color(0xFF00E5FF),
                            isCurrent = levelInfo.level in 30..39,
                            isReached = levelInfo.level >= 30,
                            perk = "Cyan Diamond halo & special entry effect"
                        )
                        HorizontalDivider(modifier = Modifier.padding(vertical = 10.dp), color = colors.divider)
                        TierRow(
                            range = "Lv. 40 – 50",
                            title = "Sovereign Master",
                            badgeColor = Color(0xFFFF1744),
                            isCurrent = levelInfo.level in 40..50,
                            isReached = levelInfo.level >= 40,
                            perk = "Mythic crown & supreme room presence"
                        )
                    }
                }
            }

            // 4. HOW EXP WORKS (Concise & Clean)
            item {
                Card(
                    shape = RoundedCornerShape(18.dp),
                    colors = CardDefaults.cardColors(containerColor = colors.cardBg),
                    border = BorderStroke(1.dp, colors.divider),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Column(modifier = Modifier.padding(16.dp)) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Icon(
                                imageVector = Icons.Default.Info,
                                contentDescription = null,
                                tint = QivoYellow,
                                modifier = Modifier.size(18.dp)
                            )
                            Spacer(modifier = Modifier.width(8.dp))
                            Text(
                                text = "EXP Rules",
                                fontSize = 14.sp,
                                fontWeight = FontWeight.Bold,
                                color = colors.textPrimary
                            )
                        }

                        Spacer(modifier = Modifier.height(10.dp))

                        Row(verticalAlignment = Alignment.Top) {
                            Text("•", color = QivoYellow, fontWeight = FontWeight.Bold, fontSize = 14.sp)
                            Spacer(modifier = Modifier.width(8.dp))
                            Text(
                                text = "1 Coin = 1 EXP. Recharging coins or receiving gifts directly adds to your level.",
                                fontSize = 12.sp,
                                color = colors.textSecondary,
                                lineHeight = 16.sp
                            )
                        }

                        Spacer(modifier = Modifier.height(6.dp))

                        Row(verticalAlignment = Alignment.Top) {
                            Text("•", color = QivoYellow, fontWeight = FontWeight.Bold, fontSize = 14.sp)
                            Spacer(modifier = Modifier.width(8.dp))
                            Text(
                                text = "Reach Level 4 (35,000 EXP) to permanently unlock the Profile Visitors Radar.",
                                fontSize = 12.sp,
                                color = colors.textSecondary,
                                lineHeight = 16.sp
                            )
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun TierRow(
    range: String,
    title: String,
    badgeColor: Color,
    isCurrent: Boolean,
    isReached: Boolean,
    perk: String
) {
    val colors = AppTheme.colors
    Row(
        modifier = Modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Surface(
            shape = RoundedCornerShape(8.dp),
            color = badgeColor.copy(alpha = if (isReached) 0.25f else 0.1f),
            border = BorderStroke(1.dp, badgeColor.copy(alpha = if (isReached) 0.6f else 0.2f)),
            modifier = Modifier.width(68.dp)
        ) {
            Text(
                text = range,
                fontSize = 11.sp,
                fontWeight = FontWeight.Bold,
                color = if (isReached) badgeColor else Color.Gray,
                modifier = Modifier.padding(horizontal = 6.dp, vertical = 4.dp),
                textAlign = androidx.compose.ui.text.style.TextAlign.Center
            )
        }

        Spacer(modifier = Modifier.width(12.dp))

        Column(modifier = Modifier.weight(1f)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    text = title,
                    fontSize = 13.sp,
                    fontWeight = FontWeight.Bold,
                    color = if (isReached) colors.textPrimary else colors.textSecondary
                )
                if (isCurrent) {
                    Spacer(modifier = Modifier.width(6.dp))
                    Surface(
                        shape = RoundedCornerShape(6.dp),
                        color = QivoYellow
                    ) {
                        Text(
                            text = "CURRENT",
                            fontSize = 8.sp,
                            fontWeight = FontWeight.Black,
                            color = Color.Black,
                            modifier = Modifier.padding(horizontal = 5.dp, vertical = 1.dp)
                        )
                    }
                }
            }
            Text(
                text = perk,
                fontSize = 11.sp,
                color = colors.textSecondary
            )
        }

        if (isReached) {
            Icon(
                imageVector = Icons.Default.CheckCircle,
                contentDescription = "Achieved",
                tint = if (isCurrent) QivoYellow else Color(0xFF00E676),
                modifier = Modifier.size(18.dp)
            )
        } else {
            Icon(
                imageVector = Icons.Default.Lock,
                contentDescription = "Locked",
                tint = colors.textSecondary.copy(alpha = 0.4f),
                modifier = Modifier.size(16.dp)
            )
        }
    }
}

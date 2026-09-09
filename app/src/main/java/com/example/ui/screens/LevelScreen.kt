package com.example.ui.screens

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
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
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.ElectricBolt
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.Stars
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
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
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
 * LevelScreen: Full Level & EXP Progress Hub (Levels 1 to 50)
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
    val allMilestones = remember { UserLevelManager.getAllLevelMilestones() }

    var selectedTab by remember { mutableIntStateOf(0) } // 0: Milestones (1-50), 1: Perks & Rules

    val animatedProgress by animateFloatAsState(
        targetValue = levelInfo.progressPercent,
        animationSpec = tween(durationMillis = 900, easing = FastOutSlowInEasing),
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
                            text = "Level System",
                            fontWeight = FontWeight.Bold,
                            fontSize = 20.sp,
                            color = colors.textPrimary
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                        Surface(
                            shape = RoundedCornerShape(12.dp),
                            color = QivoYellow.copy(alpha = 0.2f)
                        ) {
                            Text(
                                text = "Lv.${levelInfo.level}",
                                fontSize = 12.sp,
                                fontWeight = FontWeight.Bold,
                                color = QivoYellow,
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
            contentPadding = PaddingValues(bottom = 32.dp)
        ) {
            // 1. HERO CURRENT LEVEL CARD
            item {
                Card(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 16.dp, vertical = 10.dp)
                        .testTag("card_level_hero"),
                    shape = RoundedCornerShape(24.dp),
                    colors = CardDefaults.cardColors(
                        containerColor = if (isDark) Color(0xFF1C1A24) else Color(0xFFFFF9E6)
                    ),
                    border = BorderStroke(
                        1.5.dp,
                        Brush.linearGradient(levelInfo.badgeGradient)
                    )
                ) {
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(20.dp),
                        horizontalAlignment = Alignment.CenterHorizontally
                    ) {
                        // 3D Level Trophy Icon
                        Box(
                            modifier = Modifier
                                .size(90.dp)
                                .clip(CircleShape)
                                .background(
                                    Brush.radialGradient(
                                        colors = listOf(
                                            levelInfo.badgeColor.copy(alpha = 0.35f),
                                            Color.Transparent
                                        )
                                    )
                                ),
                            contentAlignment = Alignment.Center
                        ) {
                            Level3DIcon(size = 76.dp, level = levelInfo.level)
                        }

                        Spacer(modifier = Modifier.height(10.dp))

                        // Level Badge Title
                        Surface(
                            shape = RoundedCornerShape(16.dp),
                            color = levelInfo.badgeColor.copy(alpha = 0.2f),
                            border = BorderStroke(1.dp, levelInfo.badgeColor.copy(alpha = 0.6f))
                        ) {
                            Row(
                                modifier = Modifier.padding(horizontal = 14.dp, vertical = 5.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Icon(
                                    imageVector = Icons.Default.WorkspacePremium,
                                    contentDescription = null,
                                    tint = levelInfo.badgeColor,
                                    modifier = Modifier.size(16.dp)
                                )
                                Spacer(modifier = Modifier.width(6.dp))
                                Text(
                                    text = "LEVEL ${levelInfo.level} • ${levelInfo.title}",
                                    fontSize = 13.sp,
                                    fontWeight = FontWeight.ExtraBold,
                                    color = levelInfo.badgeColor
                                )
                            }
                        }

                        Spacer(modifier = Modifier.height(14.dp))

                        // Current Total EXP Counter
                        Text(
                            text = "%,d EXP".format(levelInfo.currentExp),
                            fontSize = 28.sp,
                            fontWeight = FontWeight.Black,
                            color = colors.textPrimary
                        )

                        Text(
                            text = if (levelInfo.isMaxLevel) {
                                "Maximum Level Achieved! You are a Sovereign Master."
                            } else {
                                "%,d / %,d EXP needed for Level %d".format(
                                    levelInfo.expInCurrentLevel,
                                    levelInfo.expNeededInCurrentLevel,
                                    levelInfo.level + 1
                                )
                            },
                            fontSize = 13.sp,
                            color = colors.textSecondary,
                            modifier = Modifier.padding(top = 4.dp, bottom = 12.dp)
                        )

                        // Progress Bar
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
                                    .height(10.dp)
                                    .clip(RoundedCornerShape(5.dp)),
                                color = QivoYellow,
                                trackColor = if (isDark) Color(0xFF2C2C38) else Color(0xFFE2E8F0)
                            )
                        }

                        Spacer(modifier = Modifier.height(16.dp))

                        // Recharge / Earn EXP CTA Button
                        Button(
                            onClick = onNavigateToRecharge,
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(48.dp)
                                .testTag("btn_level_get_coins"),
                            shape = RoundedCornerShape(14.dp),
                            colors = ButtonDefaults.buttonColors(
                                containerColor = QivoYellow,
                                contentColor = Color.Black
                            )
                        ) {
                            Icon(
                                imageVector = Icons.Default.ElectricBolt,
                                contentDescription = null,
                                modifier = Modifier.size(18.dp)
                            )
                            Spacer(modifier = Modifier.width(6.dp))
                            Text(
                                text = "Get Coins to Gain 1:1 EXP (1,000 Coins = 1,000 EXP)",
                                fontSize = 13.sp,
                                fontWeight = FontWeight.Bold
                            )
                        }
                    }
                }
            }

            // 2. LEVEL 4 VISITOR PRIVILEGE HIGHLIGHT CARD
            item {
                val isUnlocked = levelInfo.isVisitorsUnlocked
                val expNeededForLevel4 = (UserLevelManager.getLevelThreshold(4) - levelInfo.currentExp).coerceAtLeast(0L)

                Card(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 16.dp, vertical = 6.dp)
                        .testTag("card_visitor_feature_privilege"),
                    shape = RoundedCornerShape(20.dp),
                    colors = CardDefaults.cardColors(
                        containerColor = if (isUnlocked) {
                            if (isDark) Color(0xFF132B1A) else Color(0xFFE8F8EE)
                        } else {
                            if (isDark) Color(0xFF2B1F13) else Color(0xFFFFF3E0)
                        }
                    ),
                    border = BorderStroke(
                        1.dp,
                        if (isUnlocked) Color(0xFF00E676).copy(alpha = 0.5f) else Color(0xFFFF9800).copy(alpha = 0.5f)
                    )
                ) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(16.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Surface(
                            modifier = Modifier.size(46.dp),
                            shape = CircleShape,
                            color = if (isUnlocked) Color(0xFF00E676).copy(alpha = 0.2f) else Color(0xFFFF9800).copy(alpha = 0.2f)
                        ) {
                            Box(contentAlignment = Alignment.Center) {
                                Icon(
                                    imageVector = if (isUnlocked) Icons.Default.Visibility else Icons.Default.Lock,
                                    contentDescription = null,
                                    tint = if (isUnlocked) Color(0xFF00E676) else Color(0xFFFF9800),
                                    modifier = Modifier.size(24.dp)
                                )
                            }
                        }

                        Spacer(modifier = Modifier.width(14.dp))

                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                text = if (isUnlocked) "Profile Visitors Radar Active" else "Profile Visitors (Unlocked at Level 4)",
                                fontWeight = FontWeight.Bold,
                                fontSize = 15.sp,
                                color = colors.textPrimary
                            )
                            Spacer(modifier = Modifier.height(2.dp))
                            Text(
                                text = if (isUnlocked) {
                                    "Your Level ${levelInfo.level} allows you to see everyone who visited your profile!"
                                } else {
                                    "Reach Level 4 (%,d more EXP needed) to see who viewed your profile.".format(expNeededForLevel4)
                                },
                                fontSize = 12.sp,
                                color = colors.textSecondary
                            )
                        }

                        if (isUnlocked) {
                            Button(
                                onClick = onNavigateToVisitors,
                                shape = RoundedCornerShape(10.dp),
                                colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF00E676), contentColor = Color.Black),
                                contentPadding = PaddingValues(horizontal = 12.dp, vertical = 6.dp)
                            ) {
                                Text("View", fontSize = 12.sp, fontWeight = FontWeight.Bold)
                            }
                        }
                    }
                }
            }

            // 3. TAB SELECTOR: ROADMAP (1-50) vs HOW IT WORKS
            item {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 16.dp, vertical = 12.dp)
                        .background(
                            color = if (isDark) Color(0xFF1E1E26) else Color(0xFFF1F5F9),
                            shape = RoundedCornerShape(14.dp)
                        )
                        .padding(4.dp)
                ) {
                    Box(
                        modifier = Modifier
                            .weight(1f)
                            .clip(RoundedCornerShape(10.dp))
                            .background(if (selectedTab == 0) QivoYellow else Color.Transparent)
                            .clickable { selectedTab = 0 }
                            .padding(vertical = 10.dp),
                        contentAlignment = Alignment.Center
                    ) {
                        Text(
                            text = "Level Roadmap (1 - 50)",
                            fontSize = 13.sp,
                            fontWeight = FontWeight.Bold,
                            color = if (selectedTab == 0) Color.Black else colors.textSecondary
                        )
                    }

                    Box(
                        modifier = Modifier
                            .weight(1f)
                            .clip(RoundedCornerShape(10.dp))
                            .background(if (selectedTab == 1) QivoYellow else Color.Transparent)
                            .clickable { selectedTab = 1 }
                            .padding(vertical = 10.dp),
                        contentAlignment = Alignment.Center
                    ) {
                        Text(
                            text = "How to Gain EXP",
                            fontSize = 13.sp,
                            fontWeight = FontWeight.Bold,
                            color = if (selectedTab == 1) Color.Black else colors.textSecondary
                        )
                    }
                }
            }

            // TAB CONTENT: ROADMAP
            if (selectedTab == 0) {
                item {
                    Text(
                        text = "50-LEVEL PROGRESSION TIERS",
                        fontSize = 12.sp,
                        fontWeight = FontWeight.Bold,
                        color = colors.textSecondary,
                        modifier = Modifier.padding(horizontal = 20.dp, vertical = 6.dp)
                    )
                }

                items(allMilestones) { milestone ->
                    val isReached = levelInfo.level >= milestone.level
                    val isCurrent = levelInfo.level == milestone.level

                    Card(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 16.dp, vertical = 5.dp)
                            .testTag("card_milestone_lvl_${milestone.level}"),
                        shape = RoundedCornerShape(16.dp),
                        colors = CardDefaults.cardColors(
                            containerColor = when {
                                isCurrent -> (if (isDark) Color(0xFF262015) else Color(0xFFFFFDE7))
                                isReached -> (if (isDark) Color(0xFF181822) else Color(0xFFFAFAFC))
                                else -> (if (isDark) Color(0xFF13131A) else Color(0xFFF3F4F6))
                            }
                        ),
                        border = BorderStroke(
                            width = if (isCurrent) 1.5.dp else 1.dp,
                            color = when {
                                isCurrent -> QivoYellow
                                isReached -> milestone.badgeColor.copy(alpha = 0.4f)
                                else -> colors.divider.copy(alpha = 0.5f)
                            }
                        )
                    ) {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(14.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            // Level Pill
                            Surface(
                                modifier = Modifier.size(44.dp),
                                shape = RoundedCornerShape(12.dp),
                                color = if (isReached) milestone.badgeColor.copy(alpha = 0.25f) else Color.Gray.copy(alpha = 0.15f),
                                border = BorderStroke(
                                    1.dp,
                                    if (isReached) milestone.badgeColor else Color.Gray.copy(alpha = 0.3f)
                                )
                            ) {
                                Box(contentAlignment = Alignment.Center) {
                                    Text(
                                        text = "Lv.${milestone.level}",
                                        fontSize = 13.sp,
                                        fontWeight = FontWeight.Black,
                                        color = if (isReached) milestone.badgeColor else Color.Gray
                                    )
                                }
                            }

                            Spacer(modifier = Modifier.width(14.dp))

                            Column(modifier = Modifier.weight(1f)) {
                                Row(verticalAlignment = Alignment.CenterVertically) {
                                    Text(
                                        text = milestone.title,
                                        fontSize = 14.sp,
                                        fontWeight = FontWeight.Bold,
                                        color = if (isReached) colors.textPrimary else colors.textSecondary
                                    )
                                    if (isCurrent) {
                                        Spacer(modifier = Modifier.width(6.dp))
                                        Surface(
                                            shape = RoundedCornerShape(8.dp),
                                            color = QivoYellow
                                        ) {
                                            Text(
                                                text = "CURRENT",
                                                fontSize = 9.sp,
                                                fontWeight = FontWeight.ExtraBold,
                                                color = Color.Black,
                                                modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                                            )
                                        }
                                    }
                                    if (milestone.level == 4) {
                                        Spacer(modifier = Modifier.width(6.dp))
                                        Surface(
                                            shape = RoundedCornerShape(8.dp),
                                            color = Color(0xFFFF9800)
                                        ) {
                                            Text(
                                                text = "VISITORS",
                                                fontSize = 9.sp,
                                                fontWeight = FontWeight.ExtraBold,
                                                color = Color.Black,
                                                modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                                            )
                                        }
                                    }
                                }

                                Spacer(modifier = Modifier.height(2.dp))

                                Text(
                                    text = if (milestone.level == 1) {
                                        "Starting Level (0 EXP)"
                                    } else {
                                        "Required: %,d EXP (+%,d from previous)".format(
                                            milestone.cumulativeExpReq,
                                            milestone.deltaExpReq
                                        )
                                    },
                                    fontSize = 12.sp,
                                    color = if (isCurrent) QivoOrange else colors.textSecondary
                                )

                                if (milestone.perks.isNotEmpty()) {
                                    Spacer(modifier = Modifier.height(4.dp))
                                    Text(
                                        text = "• ${milestone.perks.first()}",
                                        fontSize = 11.sp,
                                        color = if (isReached) colors.textPrimary.copy(alpha = 0.8f) else colors.textSecondary.copy(alpha = 0.6f)
                                    )
                                }
                            }

                            if (isReached) {
                                Icon(
                                    imageVector = Icons.Default.CheckCircle,
                                    contentDescription = "Achieved",
                                    tint = if (isCurrent) QivoYellow else Color(0xFF00E676),
                                    modifier = Modifier.size(20.dp)
                                )
                            } else {
                                Icon(
                                    imageVector = Icons.Default.Lock,
                                    contentDescription = "Locked",
                                    tint = colors.textSecondary.copy(alpha = 0.4f),
                                    modifier = Modifier.size(18.dp)
                                )
                            }
                        }
                    }
                }
            } else {
                // TAB CONTENT: HOW EXP WORKS
                item {
                    Card(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 16.dp, vertical = 8.dp),
                        shape = RoundedCornerShape(18.dp),
                        colors = CardDefaults.cardColors(containerColor = colors.cardBg),
                        border = BorderStroke(1.dp, colors.divider)
                    ) {
                        Column(modifier = Modifier.padding(18.dp)) {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Icon(
                                    imageVector = Icons.Default.Info,
                                    contentDescription = null,
                                    tint = QivoYellow,
                                    modifier = Modifier.size(22.dp)
                                )
                                Spacer(modifier = Modifier.width(10.dp))
                                Text(
                                    text = "How to Gain EXP & Level Up",
                                    fontSize = 16.sp,
                                    fontWeight = FontWeight.Bold,
                                    color = colors.textPrimary
                                )
                            }

                            Spacer(modifier = Modifier.height(14.dp))

                            ExpRuleItem(
                                title = "1,000 Coins = 1,000 EXP",
                                description = "Every coin you buy, recharge, or receive from coin seller transfers and awards directly converts to 1:1 EXP."
                            )

                            HorizontalDivider(color = colors.divider, modifier = Modifier.padding(vertical = 10.dp))

                            ExpRuleItem(
                                title = "Doubling Level Progression",
                                description = "Level 1 to 2 requires 5,000 EXP. Level 2 to 3 requires 10,000 EXP (doubled), and continues doubling with every level up to Level 50!"
                            )

                            HorizontalDivider(color = colors.divider, modifier = Modifier.padding(vertical = 10.dp))

                            ExpRuleItem(
                                title = "Level 4 Visitor Unlock",
                                description = "Reaching Level 4 (35,000 cumulative EXP) permanently unlocks your profile's Visitors Radar, allowing you to see everyone who visited your profile."
                            )

                            HorizontalDivider(color = colors.divider, modifier = Modifier.padding(vertical = 10.dp))

                            ExpRuleItem(
                                title = "Daily Check-Ins & Tasks",
                                description = "Completing daily missions in the Tasks Center and claiming check-in bonuses awards free coins that boost your EXP."
                            )
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun ExpRuleItem(
    title: String,
    description: String
) {
    val colors = AppTheme.colors
    Row(modifier = Modifier.fillMaxWidth()) {
        Surface(
            modifier = Modifier.size(8.dp).offset(y = 6.dp),
            shape = CircleShape,
            color = QivoYellow
        ) {}
        Spacer(modifier = Modifier.width(10.dp))
        Column {
            Text(
                text = title,
                fontSize = 14.sp,
                fontWeight = FontWeight.Bold,
                color = colors.textPrimary
            )
            Spacer(modifier = Modifier.height(2.dp))
            Text(
                text = description,
                fontSize = 12.sp,
                color = colors.textSecondary,
                lineHeight = 17.sp
            )
        }
    }
}

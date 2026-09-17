package com.example.ui.screens
import com.example.ui.components.AppToast

import android.widget.Toast
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Chat
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Clear
import androidx.compose.material.icons.filled.ElectricBolt
import androidx.compose.material.icons.filled.Favorite
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Visibility
import androidx.compose.material.icons.filled.WorkspacePremium
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.data.AvatarHelper
import com.example.data.FollowDataCacheStore
import com.example.data.SupabaseProfileService
import com.example.data.UserLevelManager
import com.example.data.UserProfile
import com.example.data.UserSessionManager
import com.example.ui.components.App3DMascotLoader
import com.example.ui.components.Level3DIcon
import com.example.ui.theme.AppTheme
import com.example.ui.theme.QivoYellow
import kotlinx.coroutines.launch

/**
 * Dedicated Visitors Screen: Displays recent profile visitors.
 * (Unlocked at Level 4+)
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun VisitorsScreen(
    currentUserId: String,
    targetUserId: String = "",
    onBackClick: () -> Unit,
    onOpenUserDetail: (UserProfile) -> Unit,
    onOpenConversation: (UserProfile) -> Unit,
    onNavigateToLevel: () -> Unit = {},
    onNavigateToRecharge: () -> Unit = {}
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val colors = AppTheme.colors
    val isDark = colors.isDark
    val profileService = remember { SupabaseProfileService() }

    val expState by UserSessionManager.expFlow.collectAsState()
    val currentExp = expState ?: UserSessionManager.getExp(context, currentUserId)
    val userLevelInfo = remember(currentExp) { UserLevelManager.getLevelInfo(currentExp) }
    val isVisitorsUnlocked = userLevelInfo.isVisitorsUnlocked

    val effectiveUserId = targetUserId.ifBlank { currentUserId }

    // Retrieve fast-path in-memory cached state
    val cachedInitialList = remember(effectiveUserId) {
        FollowDataCacheStore.getCachedList(effectiveUserId, "VISITORS") ?: emptyList()
    }
    val cachedInitialStats = remember(effectiveUserId) {
        FollowDataCacheStore.getCachedStats(effectiveUserId)
    }

    var searchQuery by remember { mutableStateOf("") }
    // Only show full-screen loader if we have zero cached items
    var isLoading by remember(effectiveUserId) { mutableStateOf(cachedInitialList.isEmpty()) }
    var isRefreshing by remember { mutableStateOf(false) }

    var visitorsCount by remember(effectiveUserId) {
        mutableIntStateOf(cachedInitialStats?.visitorsCount ?: cachedInitialList.size)
    }
    var visitorsList by remember(effectiveUserId) {
        mutableStateOf<List<UserProfile>>(cachedInitialList)
    }

    // Map to track who current logged-in user is following
    val followingStatusMap = remember { mutableStateMapOf<String, Boolean>() }

    // Pre-populate following status from cache if available
    LaunchedEffect(currentUserId) {
        val cachedFollowing = FollowDataCacheStore.getFollowingSet(currentUserId)
        if (cachedFollowing != null && followingStatusMap.isEmpty()) {
            visitorsList.forEach { u ->
                followingStatusMap[u.id] = cachedFollowing.contains(u.id)
            }
        }
    }

    fun loadData(isUserPullRefresh: Boolean = false) {
        if (!isVisitorsUnlocked) {
            isLoading = false
            return
        }
        scope.launch {
            // Never blank the screen if we already have items to display
            if (visitorsList.isEmpty()) {
                isLoading = true
            }
            if (isUserPullRefresh) {
                isRefreshing = true
            }
            try {
                val stats = profileService.fetchFollowStats(effectiveUserId)
                FollowDataCacheStore.putStats(effectiveUserId, stats)
                visitorsCount = stats.visitorsCount

                val list = profileService.fetchCategoryUsers(effectiveUserId, "VISITORS")
                FollowDataCacheStore.putList(effectiveUserId, "VISITORS", list)
                visitorsList = list

                val myFollowingIds = profileService.fetchFollowingIdSet(currentUserId)
                FollowDataCacheStore.putFollowingSet(currentUserId, myFollowingIds)
                followingStatusMap.clear()
                list.forEach { u ->
                    followingStatusMap[u.id] = myFollowingIds.contains(u.id)
                }
            } catch (e: Exception) {
                // Silently keep displaying existing cached data on network error
            } finally {
                isLoading = false
                isRefreshing = false
            }
        }
    }

    LaunchedEffect(effectiveUserId, isVisitorsUnlocked) {
        loadData()
    }

    val filteredVisitors = remember(visitorsList, searchQuery) {
        if (searchQuery.isBlank()) {
            visitorsList
        } else {
            val q = searchQuery.trim().lowercase()
            visitorsList.filter {
                it.name.lowercase().contains(q) ||
                it.numericId.toString().contains(q) ||
                it.country.lowercase().contains(q)
            }
        }
    }

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
                            text = "Visitors",
                            fontWeight = FontWeight.Bold,
                            fontSize = 20.sp,
                            color = colors.textPrimary
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                        if (isVisitorsUnlocked) {
                            Surface(
                                shape = RoundedCornerShape(12.dp),
                                color = QivoYellow.copy(alpha = 0.2f)
                            ) {
                                Text(
                                    text = "$visitorsCount",
                                    fontSize = 12.sp,
                                    fontWeight = FontWeight.Bold,
                                    color = QivoYellow,
                                    modifier = Modifier.padding(horizontal = 8.dp, vertical = 2.dp)
                                )
                            }
                        } else {
                            Surface(
                                shape = RoundedCornerShape(12.dp),
                                color = Color(0xFFFF9800).copy(alpha = 0.2f)
                            ) {
                                Text(
                                    text = "Lv.4 Lock",
                                    fontSize = 11.sp,
                                    fontWeight = FontWeight.Bold,
                                    color = Color(0xFFFF9800),
                                    modifier = Modifier.padding(horizontal = 8.dp, vertical = 2.dp)
                                )
                            }
                        }
                    }
                },
                navigationIcon = {
                    IconButton(
                        onClick = onBackClick,
                        modifier = Modifier.testTag("btn_visitors_back")
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
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
        ) {
            if (!isVisitorsUnlocked) {
                // LEVEL 4 LOCKED STATE UI
                val expNeededForLevel4 = (UserLevelManager.getLevelThreshold(4) - currentExp).coerceAtLeast(0L)
                val level4Progress = (currentExp.toFloat() / UserLevelManager.getLevelThreshold(4).toFloat()).coerceIn(0f, 1f)

                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(24.dp),
                    contentAlignment = Alignment.Center
                ) {
                    Card(
                        modifier = Modifier
                            .fillMaxWidth()
                            .testTag("card_visitors_locked_state"),
                        shape = RoundedCornerShape(24.dp),
                        colors = CardDefaults.cardColors(
                            containerColor = if (isDark) Color(0xFF1E1C27) else Color(0xFFFFFBF0)
                        ),
                        border = BorderStroke(1.5.dp, Color(0xFFFF9800).copy(alpha = 0.5f))
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
                                    .background(Color(0xFFFF9800).copy(alpha = 0.15f)),
                                contentAlignment = Alignment.Center
                            ) {
                                Level3DIcon(size = 60.dp, level = userLevelInfo.level)
                            }

                            Spacer(modifier = Modifier.height(16.dp))

                            Surface(
                                shape = RoundedCornerShape(12.dp),
                                color = Color(0xFFFF9800).copy(alpha = 0.2f)
                            ) {
                                Row(
                                    modifier = Modifier.padding(horizontal = 12.dp, vertical = 4.dp),
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Icon(
                                        imageVector = Icons.Default.Lock,
                                        contentDescription = null,
                                        tint = Color(0xFFFF9800),
                                        modifier = Modifier.size(14.dp)
                                    )
                                    Spacer(modifier = Modifier.width(6.dp))
                                    Text(
                                        text = "UNLOCKED AT LEVEL 4",
                                        fontSize = 11.sp,
                                        fontWeight = FontWeight.ExtraBold,
                                        color = Color(0xFFFF9800)
                                    )
                                }
                            }

                            Spacer(modifier = Modifier.height(12.dp))

                            Text(
                                text = "Profile Visitors Radar Locked",
                                fontSize = 19.sp,
                                fontWeight = FontWeight.Bold,
                                color = colors.textPrimary,
                                textAlign = TextAlign.Center
                            )

                            Spacer(modifier = Modifier.height(8.dp))

                            Text(
                                text = "You are currently Level ${userLevelInfo.level} (%,d EXP). Reach Level 4 (35,000 EXP) to unlock full visitor history and see who browsed your profile!".format(currentExp),
                                fontSize = 13.sp,
                                color = colors.textSecondary,
                                textAlign = TextAlign.Center,
                                lineHeight = 18.sp
                            )

                            Spacer(modifier = Modifier.height(16.dp))

                            // Level 4 Progress Indicator
                            Column(modifier = Modifier.fillMaxWidth()) {
                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.SpaceBetween
                                ) {
                                    Text(
                                        text = "Progress to Level 4",
                                        fontSize = 12.sp,
                                        fontWeight = FontWeight.Bold,
                                        color = colors.textPrimary
                                    )
                                    Text(
                                        text = "${(level4Progress * 100).toInt()}%",
                                        fontSize = 12.sp,
                                        fontWeight = FontWeight.Bold,
                                        color = Color(0xFFFF9800)
                                    )
                                }
                                Spacer(modifier = Modifier.height(6.dp))
                                LinearProgressIndicator(
                                    progress = { level4Progress },
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .height(8.dp)
                                        .clip(RoundedCornerShape(4.dp)),
                                    color = Color(0xFFFF9800),
                                    trackColor = if (isDark) Color(0xFF2E2A38) else Color(0xFFE2E8F0)
                                )
                                Spacer(modifier = Modifier.height(4.dp))
                                Text(
                                    text = "%,d more EXP needed".format(expNeededForLevel4),
                                    fontSize = 11.sp,
                                    color = colors.textSecondary,
                                    modifier = Modifier.align(Alignment.End)
                                )
                            }

                            Spacer(modifier = Modifier.height(20.dp))

                            Button(
                                onClick = onNavigateToLevel,
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .height(48.dp)
                                    .testTag("btn_visitors_view_level"),
                                shape = RoundedCornerShape(14.dp),
                                colors = ButtonDefaults.buttonColors(
                                    containerColor = QivoYellow,
                                    contentColor = Color.Black
                                )
                            ) {
                                Icon(
                                    imageVector = Icons.Default.WorkspacePremium,
                                    contentDescription = null,
                                    modifier = Modifier.size(18.dp)
                                )
                                Spacer(modifier = Modifier.width(8.dp))
                                Text(
                                    text = "View Level & Milestones",
                                    fontSize = 14.sp,
                                    fontWeight = FontWeight.Bold
                                )
                            }

                            Spacer(modifier = Modifier.height(10.dp))

                            OutlinedButton(
                                onClick = onNavigateToRecharge,
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .height(46.dp)
                                    .testTag("btn_visitors_recharge_coins"),
                                shape = RoundedCornerShape(14.dp),
                                border = BorderStroke(1.dp, QivoYellow)
                            ) {
                                Icon(
                                    imageVector = Icons.Default.ElectricBolt,
                                    contentDescription = null,
                                    tint = QivoYellow,
                                    modifier = Modifier.size(18.dp)
                                )
                                Spacer(modifier = Modifier.width(8.dp))
                                Text(
                                    text = "Get Coins (+1:1 EXP)",
                                    fontSize = 13.sp,
                                    fontWeight = FontWeight.Bold,
                                    color = colors.textPrimary
                                )
                            }
                        }
                    }
                }
            } else {
                // UNLOCKED VISITOR RADAR UI
                // Search Bar
                OutlinedTextField(
                    value = searchQuery,
                    onValueChange = { searchQuery = it },
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 16.dp, vertical = 8.dp)
                        .testTag("input_search_visitors"),
                    placeholder = {
                        Text("Search visitors by name or ID...", color = colors.textSecondary, fontSize = 14.sp)
                    },
                    leadingIcon = {
                        Icon(
                            imageVector = Icons.Default.Search,
                            contentDescription = "Search",
                            tint = colors.textSecondary
                        )
                    },
                    trailingIcon = {
                        if (searchQuery.isNotEmpty()) {
                            IconButton(onClick = { searchQuery = "" }) {
                                Icon(
                                    imageVector = Icons.Default.Clear,
                                    contentDescription = "Clear",
                                    tint = colors.textSecondary
                                )
                            }
                        }
                    },
                    singleLine = true,
                    shape = RoundedCornerShape(14.dp),
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedBorderColor = QivoYellow,
                        unfocusedBorderColor = colors.divider,
                        focusedContainerColor = colors.cardBg,
                        unfocusedContainerColor = colors.cardBg,
                        focusedTextColor = colors.textPrimary,
                        unfocusedTextColor = colors.textPrimary
                    )
                )

                if (isLoading && visitorsList.isEmpty()) {
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .weight(1f),
                        contentAlignment = Alignment.Center
                    ) {
                        App3DMascotLoader(message = "Loading visitors...")
                    }
                } else if (filteredVisitors.isEmpty() && !isLoading) {
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .weight(1f)
                            .padding(horizontal = 32.dp),
                        contentAlignment = Alignment.Center
                    ) {
                        Column(
                            horizontalAlignment = Alignment.CenterHorizontally,
                            verticalArrangement = Arrangement.Center
                        ) {
                            Surface(
                                modifier = Modifier.size(72.dp),
                                shape = CircleShape,
                                color = colors.cardBg,
                                border = BorderStroke(1.dp, colors.divider)
                            ) {
                                Box(contentAlignment = Alignment.Center) {
                                    Icon(
                                        imageVector = Icons.Default.Visibility,
                                        contentDescription = null,
                                        modifier = Modifier.size(36.dp),
                                        tint = QivoYellow
                                    )
                                }
                            }

                            Spacer(modifier = Modifier.height(16.dp))

                            Text(
                                text = if (searchQuery.isNotBlank()) "No Matching Visitors" else "No Recent Visitors",
                                fontSize = 18.sp,
                                fontWeight = FontWeight.Bold,
                                color = colors.textPrimary
                            )

                            Spacer(modifier = Modifier.height(8.dp))

                            Text(
                                text = if (searchQuery.isNotBlank()) "Try searching with a different name or numeric ID."
                                else "When other people view your profile, they will appear here.",
                                fontSize = 13.sp,
                                color = colors.textSecondary,
                                textAlign = TextAlign.Center,
                                lineHeight = 18.sp
                            )
                        }
                    }
                } else {
                    LazyColumn(
                        modifier = Modifier
                            .fillMaxSize()
                            .padding(horizontal = 16.dp),
                        verticalArrangement = Arrangement.spacedBy(10.dp),
                        contentPadding = PaddingValues(top = 8.dp, bottom = 24.dp)
                    ) {
                        items(filteredVisitors, key = { it.id }) { user ->
                            val isFollowed = followingStatusMap[user.id] == true
                            val isSelf = user.id == currentUserId

                            VisitorItemCard(
                                user = user,
                                isFollowed = isFollowed,
                                isSelf = isSelf,
                                onCardClick = { onOpenUserDetail(user) },
                                onChatClick = { onOpenConversation(user) },
                                onFollowToggle = {
                                    val newFollowState = !isFollowed
                                    followingStatusMap[user.id] = newFollowState
                                    FollowDataCacheStore.updateFollowStatus(currentUserId, user.id, newFollowState)
                                    scope.launch {
                                        val (success, actualState) = profileService.toggleFollow(currentUserId, user.id)
                                        if (success) {
                                            followingStatusMap[user.id] = actualState
                                            FollowDataCacheStore.updateFollowStatus(currentUserId, user.id, actualState)
                                            val msg = if (actualState) "Following ${user.name}" else "Unfollowed ${user.name}"
                                            AppToast.show(msg)
                                        } else {
                                            followingStatusMap[user.id] = isFollowed
                                            FollowDataCacheStore.updateFollowStatus(currentUserId, user.id, isFollowed)
                                            AppToast.show("Could not update follow status")
                                        }
                                    }
                                }
                            )
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun VisitorItemCard(
    user: UserProfile,
    isFollowed: Boolean,
    isSelf: Boolean,
    onCardClick: () -> Unit,
    onChatClick: () -> Unit,
    onFollowToggle: () -> Unit
) {
    val colors = AppTheme.colors

    Surface(
        modifier = Modifier
            .fillMaxWidth()
            .clickable { onCardClick() }
            .testTag("card_visitor_${user.numericId}"),
        shape = RoundedCornerShape(16.dp),
        color = colors.cardBg,
        border = BorderStroke(1.dp, colors.divider)
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(12.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            // User Avatar with Online Dot
            Box(modifier = Modifier.size(54.dp)) {
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .clip(CircleShape)
                ) {
                    AvatarHelper.UserAvatarImage(
                        avatarUrl = user.avatarUrl,
                        userId = user.id,
                        gender = user.gender,
                        numericId = user.numericId,
                        contentDescription = user.name,
                        modifier = Modifier.fillMaxSize(),
                        contentScale = ContentScale.Crop
                    )
                }

                if (user.isOnline) {
                    Box(
                        modifier = Modifier
                            .size(13.dp)
                            .align(Alignment.BottomEnd)
                            .clip(CircleShape)
                            .background(colors.cardBg)
                            .padding(2.dp)
                            .clip(CircleShape)
                            .background(Color(0xFF4CAF50))
                    )
                }
            }

            Spacer(modifier = Modifier.width(12.dp))

            // User Info
            Column(modifier = Modifier.weight(1f)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        text = user.name.ifBlank { "QIVO User" },
                        fontSize = 15.sp,
                        fontWeight = FontWeight.Bold,
                        color = colors.textPrimary,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )

                    Spacer(modifier = Modifier.width(6.dp))

                    Surface(
                        shape = RoundedCornerShape(6.dp),
                        color = Color(0xFF673AB7).copy(alpha = 0.2f)
                    ) {
                        Text(
                            text = "👀 Viewed",
                            fontSize = 10.sp,
                            fontWeight = FontWeight.Bold,
                            color = Color(0xFF9575CD),
                            modifier = Modifier.padding(horizontal = 5.dp, vertical = 1.dp)
                        )
                    }
                }

                Spacer(modifier = Modifier.height(3.dp))

                Row(verticalAlignment = Alignment.CenterVertically) {
                    val isFemale = user.gender.equals("Female", true)
                    Surface(
                        shape = RoundedCornerShape(6.dp),
                        color = if (isFemale) Color(0xFFE2C485) else Color(0xFFD4C8B8)
                    ) {
                        Text(
                            text = if (isFemale) "♀" else "♂",
                            fontSize = 10.sp,
                            fontWeight = FontWeight.Bold,
                            color = Color.Black,
                            modifier = Modifier.padding(horizontal = 5.dp, vertical = 1.dp)
                        )
                    }

                    Spacer(modifier = Modifier.width(6.dp))

                    Text(
                        text = "ID: ${user.numericId}",
                        fontSize = 11.sp,
                        color = colors.textSecondary
                    )

                    if (user.country.isNotBlank()) {
                        Spacer(modifier = Modifier.width(6.dp))
                        Text(
                            text = "• ${user.country}",
                            fontSize = 11.sp,
                            color = colors.textSecondary,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                    }
                }

                if (user.socialPreferences.isNotBlank() && user.socialPreferences != "Choose") {
                    Spacer(modifier = Modifier.height(2.dp))
                    Text(
                        text = user.socialPreferences,
                        fontSize = 11.sp,
                        color = QivoYellow,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                }
            }

            Spacer(modifier = Modifier.width(8.dp))

            // Actions: Follow Toggle & Chat Button
            Row(verticalAlignment = Alignment.CenterVertically) {
                Surface(
                    modifier = Modifier
                        .size(38.dp)
                        .clip(CircleShape)
                        .clickable { onChatClick() }
                        .testTag("btn_chat_visitor_${user.numericId}"),
                    color = colors.divider.copy(alpha = 0.6f)
                ) {
                    Box(contentAlignment = Alignment.Center) {
                        Icon(
                            imageVector = Icons.Default.Chat,
                            contentDescription = "Chat",
                            tint = colors.textPrimary,
                            modifier = Modifier.size(18.dp)
                        )
                    }
                }

                if (!isSelf) {
                    Spacer(modifier = Modifier.width(8.dp))

                    Button(
                        onClick = onFollowToggle,
                        modifier = Modifier
                            .height(36.dp)
                            .testTag("btn_follow_visitor_${user.numericId}"),
                        shape = RoundedCornerShape(18.dp),
                        colors = ButtonDefaults.buttonColors(
                            containerColor = if (isFollowed) Color(0xFF2A2A30) else Color(0xFFB3FF00),
                            contentColor = if (isFollowed) Color.White else Color.Black
                        ),
                        contentPadding = PaddingValues(horizontal = 12.dp, vertical = 0.dp),
                        elevation = ButtonDefaults.buttonElevation(defaultElevation = 0.dp)
                    ) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Icon(
                                imageVector = if (isFollowed) Icons.Default.Check else Icons.Default.Favorite,
                                contentDescription = null,
                                modifier = Modifier.size(13.dp),
                                tint = if (isFollowed) Color.White else Color.Black
                            )
                            Spacer(modifier = Modifier.width(4.dp))
                            Text(
                                text = if (isFollowed) "Following" else "Follow",
                                fontSize = 12.sp,
                                fontWeight = FontWeight.Bold
                            )
                        }
                    }
                }
            }
        }
    }
}

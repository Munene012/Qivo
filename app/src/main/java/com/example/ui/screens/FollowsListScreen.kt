package com.example.ui.screens
import com.example.ui.components.AppToast

import android.widget.Toast
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.background
import androidx.compose.foundation.border
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
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Chat
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Favorite
import androidx.compose.material.icons.filled.FavoriteBorder
import androidx.compose.material.icons.filled.Group
import androidx.compose.material.icons.filled.PersonAdd
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Visibility
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.ScrollableTabRow
import androidx.compose.material3.Surface
import androidx.compose.material3.Tab
import androidx.compose.material3.TabRowDefaults
import androidx.compose.material3.TabRowDefaults.tabIndicatorOffset
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.data.AvatarHelper
import com.example.data.FollowDataCacheStore
import com.example.data.FollowStats
import com.example.data.SupabaseProfileService
import com.example.data.UserProfile
import com.example.ui.theme.AppTheme
import com.example.ui.theme.QivoYellow
import kotlinx.coroutines.launch

enum class FollowTab(val title: String, val categoryKey: String) {
    FRIENDS("Friends", "FRIENDS"),
    FOLLOWING("Following", "FOLLOWING"),
    FOLLOWERS("Followers", "FOLLOWERS"),
    VISITORS("Visitors", "VISITORS")
}

@Composable
fun FollowsListScreen(
    currentUserId: String,
    targetUserId: String = "",
    initialTab: FollowTab = FollowTab.FOLLOWING,
    onBackClick: () -> Unit,
    onOpenUserDetail: (UserProfile) -> Unit,
    onOpenConversation: (UserProfile) -> Unit
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val colors = AppTheme.colors
    val profileService = remember { SupabaseProfileService() }

    val effectiveUserId = targetUserId.ifBlank { currentUserId }

    val cachedInitialList = remember(effectiveUserId, initialTab) {
        FollowDataCacheStore.getCachedList(effectiveUserId, initialTab.categoryKey) ?: emptyList()
    }
    val cachedInitialStats = remember(effectiveUserId) {
        FollowDataCacheStore.getCachedStats(effectiveUserId)
    }

    var selectedTab by remember { mutableStateOf(initialTab) }
    var searchQuery by remember { mutableStateOf("") }
    var isLoading by remember(effectiveUserId, initialTab) { mutableStateOf(cachedInitialList.isEmpty()) }
    var isRefreshing by remember { mutableStateOf(false) }

    var followStats by remember(effectiveUserId) { mutableStateOf(cachedInitialStats ?: FollowStats()) }
    var usersList by remember(effectiveUserId, initialTab) { mutableStateOf<List<UserProfile>>(cachedInitialList) }
    var isLoadingMore by remember { mutableStateOf(false) }
    var hasMore by remember { mutableStateOf(true) }
    val listState = rememberLazyListState()
    val pageSize = 30

    // Map to track who the current logged-in user is following in real-time
    val followingStatusMap = remember { mutableStateMapOf<String, Boolean>() }

    // Pre-fill following status from cache
    LaunchedEffect(currentUserId) {
        val cachedFollowing = FollowDataCacheStore.getFollowingSet(currentUserId)
        if (cachedFollowing != null && followingStatusMap.isEmpty()) {
            usersList.forEach { u ->
                followingStatusMap[u.id] = cachedFollowing.contains(u.id)
            }
        }
    }

    fun loadData(isUserPullRefresh: Boolean = false) {
        scope.launch {
            val cached = FollowDataCacheStore.getCachedList(effectiveUserId, selectedTab.categoryKey)
            if (cached != null && usersList.isEmpty()) {
                usersList = cached
                isLoading = false
            } else if (usersList.isEmpty()) {
                isLoading = true
            }
            if (isUserPullRefresh) {
                isRefreshing = true
            }
            hasMore = true
            try {
                // 1. Fetch counts
                val stats = profileService.fetchFollowStats(effectiveUserId)
                FollowDataCacheStore.putStats(effectiveUserId, stats)
                followStats = stats

                // 2. Fetch category user list
                val list = when (selectedTab) {
                    FollowTab.FOLLOWING -> profileService.fetchFollowing(effectiveUserId, offset = 0, limit = pageSize)
                    FollowTab.FOLLOWERS -> profileService.fetchFollowers(effectiveUserId, offset = 0, limit = pageSize)
                    FollowTab.VISITORS -> profileService.fetchCategoryUsers(effectiveUserId, "VISITORS")
                    FollowTab.FRIENDS -> profileService.fetchCategoryUsers(effectiveUserId, "FRIENDS")
                }
                FollowDataCacheStore.putList(effectiveUserId, selectedTab.categoryKey, list)
                usersList = list
                hasMore = if (selectedTab == FollowTab.FOLLOWING || selectedTab == FollowTab.FOLLOWERS) list.size >= pageSize else false

                // 3. Fetch set of IDs followed by current user
                val myFollowingIds = profileService.fetchFollowingIdSet(currentUserId)
                FollowDataCacheStore.putFollowingSet(currentUserId, myFollowingIds)
                followingStatusMap.clear()
                usersList.forEach { u ->
                    followingStatusMap[u.id] = myFollowingIds.contains(u.id)
                }
            } catch (_: Exception) {
            } finally {
                isLoading = false
                isRefreshing = false
            }
        }
    }

    fun loadMore() {
        if (!isLoadingMore && !isLoading && hasMore && (selectedTab == FollowTab.FOLLOWING || selectedTab == FollowTab.FOLLOWERS)) {
            scope.launch {
                isLoadingMore = true
                val next = when (selectedTab) {
                    FollowTab.FOLLOWING -> profileService.fetchFollowing(effectiveUserId, offset = usersList.size, limit = pageSize)
                    FollowTab.FOLLOWERS -> profileService.fetchFollowers(effectiveUserId, offset = usersList.size, limit = pageSize)
                    else -> emptyList()
                }
                if (next.isNotEmpty()) {
                    val existing = usersList.map { it.id }.toSet()
                    val distinctNew = next.filter { it.id !in existing }
                    usersList = usersList + distinctNew

                    val myFollowingIds = profileService.fetchFollowingIdSet(currentUserId)
                    distinctNew.forEach { u ->
                        followingStatusMap[u.id] = myFollowingIds.contains(u.id)
                    }
                }
                if (next.size < pageSize) {
                    hasMore = false
                }
                isLoadingMore = false
            }
        }
    }

    val shouldLoadMore by remember {
        androidx.compose.runtime.derivedStateOf {
            val total = listState.layoutInfo.totalItemsCount
            val last = listState.layoutInfo.visibleItemsInfo.lastOrNull()?.index ?: 0
            hasMore && !isLoadingMore && !isLoading && total > 0 && last >= total - 2 && searchQuery.isBlank()
        }
    }

    LaunchedEffect(shouldLoadMore) {
        if (shouldLoadMore) {
            loadMore()
        }
    }

    LaunchedEffect(selectedTab, effectiveUserId) {
        val cached = FollowDataCacheStore.getCachedList(effectiveUserId, selectedTab.categoryKey)
        if (cached != null) {
            usersList = cached
            isLoading = false
        }
        loadData()
    }

    val filteredUsers = remember(usersList, searchQuery) {
        if (searchQuery.isBlank()) {
            usersList
        } else {
            val q = searchQuery.trim().lowercase()
            usersList.filter {
                it.name.lowercase().contains(q) ||
                it.country.lowercase().contains(q) ||
                it.numericId.toString().contains(q)
            }
        }
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(colors.screenBg)
            .statusBarsPadding()
    ) {
        // 1. Top Bar
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 8.dp, vertical = 6.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            IconButton(
                onClick = onBackClick,
                modifier = Modifier.testTag("btn_back_follows_list")
            ) {
                Icon(
                    imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                    contentDescription = "Back",
                    tint = colors.textPrimary
                )
            }

            Text(
                text = "Connections & Social",
                fontSize = 19.sp,
                fontWeight = FontWeight.Bold,
                color = colors.textPrimary,
                modifier = Modifier.weight(1f)
            )

            IconButton(
                onClick = {
                    isRefreshing = true
                    loadData()
                },
                modifier = Modifier.testTag("btn_refresh_follows_list")
            ) {
                Icon(
                    imageVector = Icons.Default.Refresh,
                    contentDescription = "Refresh",
                    tint = colors.textPrimary
                )
            }
        }

        // 2. Scrollable / Paged Tabs Row with Live Counts
        ScrollableTabRow(
            selectedTabIndex = selectedTab.ordinal,
            containerColor = Color.Transparent,
            contentColor = Color(0xFFFFD600),
            edgePadding = 16.dp,
            indicator = { tabPositions ->
                if (selectedTab.ordinal < tabPositions.size) {
                    TabRowDefaults.SecondaryIndicator(
                        modifier = Modifier.tabIndicatorOffset(tabPositions[selectedTab.ordinal]),
                        color = Color(0xFFFFD600),
                        height = 3.dp
                    )
                }
            },
            divider = {}
        ) {
            FollowTab.entries.forEach { tab ->
                val isSelected = selectedTab == tab
                val count = when (tab) {
                    FollowTab.FRIENDS -> followStats.friendsCount
                    FollowTab.FOLLOWING -> followStats.followingCount
                    FollowTab.FOLLOWERS -> followStats.followersCount
                    FollowTab.VISITORS -> followStats.visitorsCount
                }

                Tab(
                    selected = isSelected,
                    onClick = {
                        if (selectedTab != tab) {
                            selectedTab = tab
                            val cached = FollowDataCacheStore.getCachedList(effectiveUserId, tab.categoryKey)
                            if (cached != null) {
                                usersList = cached
                                isLoading = false
                            }
                        }
                    },
                    modifier = Modifier.testTag("tab_follow_${tab.name.lowercase()}"),
                    text = {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.Center
                        ) {
                            Text(
                                text = tab.title,
                                fontSize = 14.sp,
                                fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Medium,
                                color = if (isSelected) Color(0xFFFFD600) else colors.textSecondary
                            )
                            Spacer(modifier = Modifier.width(5.dp))
                            Surface(
                                shape = RoundedCornerShape(10.dp),
                                color = if (isSelected) Color(0xFFFFD600).copy(alpha = 0.2f) else colors.cardBg
                            ) {
                                Text(
                                    text = "$count",
                                    fontSize = 11.sp,
                                    fontWeight = FontWeight.Bold,
                                    color = if (isSelected) Color(0xFFFFD600) else colors.textSecondary,
                                    modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                                )
                            }
                        }
                    }
                )
            }
        }

        Spacer(modifier = Modifier.height(10.dp))

        // 3. Search Field
        OutlinedTextField(
            value = searchQuery,
            onValueChange = { searchQuery = it },
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp)
                .testTag("input_search_follows"),
            placeholder = { Text("Search by name, ID, or country...", color = colors.textSecondary, fontSize = 13.sp) },
            leadingIcon = {
                Icon(
                    imageVector = Icons.Default.Search,
                    contentDescription = "Search",
                    tint = colors.textSecondary,
                    modifier = Modifier.size(18.dp)
                )
            },
            singleLine = true,
            shape = RoundedCornerShape(20.dp),
            colors = OutlinedTextFieldDefaults.colors(
                focusedContainerColor = colors.inputBg,
                unfocusedContainerColor = colors.inputBg,
                focusedBorderColor = Color(0xFFFFD600),
                unfocusedBorderColor = colors.divider
            )
        )

        Spacer(modifier = Modifier.height(10.dp))

        // 4. Content List / Loading / Empty States
        if (isLoading && usersList.isEmpty()) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(32.dp),
                contentAlignment = Alignment.Center
            ) {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    CircularProgressIndicator(
                        color = Color(0xFFFFD600),
                        modifier = Modifier.size(36.dp)
                    )
                    Spacer(modifier = Modifier.height(12.dp))
                    Text(
                        text = "Loading ${selectedTab.title}...",
                        fontSize = 13.sp,
                        color = colors.textSecondary
                    )
                }
            }
        } else if (filteredUsers.isEmpty() && !isLoading) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(32.dp),
                contentAlignment = Alignment.Center
            ) {
                Column(
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.Center
                ) {
                    Surface(
                        modifier = Modifier.size(72.dp),
                        shape = CircleShape,
                        color = Color(0xFFFFD600).copy(alpha = 0.15f)
                    ) {
                        Box(contentAlignment = Alignment.Center) {
                            Icon(
                                imageVector = when (selectedTab) {
                                    FollowTab.FRIENDS -> Icons.Default.Group
                                    FollowTab.FOLLOWING -> Icons.Default.FavoriteBorder
                                    FollowTab.FOLLOWERS -> Icons.Default.PersonAdd
                                    FollowTab.VISITORS -> Icons.Default.Visibility
                                },
                                contentDescription = null,
                                tint = Color(0xFFFFD600),
                                modifier = Modifier.size(36.dp)
                            )
                        }
                    }

                    Spacer(modifier = Modifier.height(16.dp))

                    Text(
                        text = when (selectedTab) {
                            FollowTab.FRIENDS -> "No Mutual Friends Yet"
                            FollowTab.FOLLOWING -> "You Aren't Following Anyone Yet"
                            FollowTab.FOLLOWERS -> "No Followers Yet"
                            FollowTab.VISITORS -> "No Visitors Found"
                        },
                        fontSize = 16.sp,
                        fontWeight = FontWeight.Bold,
                        color = colors.textPrimary
                    )

                    Spacer(modifier = Modifier.height(6.dp))

                    Text(
                        text = when (selectedTab) {
                            FollowTab.FRIENDS -> "Follow users who follow you back to establish mutual friendship!"
                            FollowTab.FOLLOWING -> "Discover interesting people in Party, Nearby & Explore, and tap Follow."
                            FollowTab.FOLLOWERS -> "Share your profile and chat in party rooms to gain followers!"
                            FollowTab.VISITORS -> "Active users and profile visitors will appear here."
                        },
                        fontSize = 13.sp,
                        color = colors.textSecondary,
                        modifier = Modifier.padding(horizontal = 24.dp),
                        textAlign = androidx.compose.ui.text.style.TextAlign.Center
                    )
                }
            }
        } else {
            LazyColumn(
                state = listState,
                modifier = Modifier
                    .fillMaxSize()
                    .padding(horizontal = 16.dp),
                verticalArrangement = Arrangement.spacedBy(10.dp),
                contentPadding = PaddingValues(top = 6.dp, bottom = 24.dp)
            ) {
                items(filteredUsers, key = { it.id }) { user ->
                    val isFollowed = followingStatusMap[user.id] == true
                    val isSelf = user.id == currentUserId

                    UserConnectionCard(
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
                                    // Refresh follow counts
                                    val freshStats = profileService.fetchFollowStats(effectiveUserId)
                                    FollowDataCacheStore.putStats(effectiveUserId, freshStats)
                                    followStats = freshStats
                                } else {
                                    // Revert
                                    followingStatusMap[user.id] = isFollowed
                                    FollowDataCacheStore.updateFollowStatus(currentUserId, user.id, isFollowed)
                                    AppToast.show("Failed to update follow status")
                                }
                            }
                        }
                    )
                }

                if (isLoadingMore) {
                    item(key = "follows_loading_more") {
                        Box(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(vertical = 12.dp),
                            contentAlignment = Alignment.Center
                        ) {
                            CircularProgressIndicator(
                                modifier = Modifier.size(16.dp),
                                strokeWidth = 2.dp,
                                color = colors.textSecondary
                            )
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun UserConnectionCard(
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
            .testTag("card_user_connection_${user.numericId}"),
        shape = RoundedCornerShape(16.dp),
        color = colors.cardBg,
        border = androidx.compose.foundation.BorderStroke(1.dp, colors.divider)
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(12.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            // User Avatar
            Box(
                modifier = Modifier
                    .size(54.dp)
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

            Spacer(modifier = Modifier.width(12.dp))

            // User Info
            Column(modifier = Modifier.weight(1f)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        text = user.name,
                        fontSize = 15.sp,
                        fontWeight = FontWeight.Bold,
                        color = colors.textPrimary,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                }

                Spacer(modifier = Modifier.height(3.dp))

                Row(verticalAlignment = Alignment.CenterVertically) {
                    // Gender & Age Badge
                    Surface(
                        shape = RoundedCornerShape(8.dp),
                        color = if (user.gender.equals("Female", true)) Color(0xFF7FFF00) else Color(0xFF2979FF)
                    ) {
                        Text(
                            text = "${if (user.gender.equals("Female", true)) "♀" else "♂"} 24",
                            fontSize = 10.sp,
                            fontWeight = FontWeight.Bold,
                            color = Color.White,
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
                        color = Color(0xFFFFD600),
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                }
            }

            Spacer(modifier = Modifier.width(8.dp))

            // Actions: Follow Toggle & Chat Button
            Row(verticalAlignment = Alignment.CenterVertically) {
                // Direct Chat button
                Surface(
                    modifier = Modifier
                        .size(38.dp)
                        .clip(CircleShape)
                        .clickable { onChatClick() }
                        .testTag("btn_chat_user_${user.numericId}"),
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

                    // Follow / Following Button
                    Button(
                        onClick = onFollowToggle,
                        modifier = Modifier
                            .height(38.dp)
                            .testTag("btn_follow_toggle_${user.numericId}"),
                        shape = RoundedCornerShape(19.dp),
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
                                modifier = Modifier.size(14.dp),
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

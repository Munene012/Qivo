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
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Chat
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Clear
import androidx.compose.material.icons.filled.Favorite
import androidx.compose.material.icons.filled.PersonAdd
import androidx.compose.material.icons.filled.Search
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
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.data.AvatarHelper
import com.example.data.FollowDataCacheStore
import com.example.data.SupabaseProfileService
import com.example.data.UserProfile
import com.example.ui.components.App3DInlineSpinner
import com.example.ui.components.App3DMascotLoader
import com.example.ui.theme.AppTheme
import com.example.ui.theme.QivoYellow
import kotlinx.coroutines.launch

/**
 * Dedicated Followers Screen: Displays users who follow this account.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun FollowersScreen(
    currentUserId: String,
    targetUserId: String = "",
    onBackClick: () -> Unit,
    onOpenUserDetail: (UserProfile) -> Unit,
    onOpenConversation: (UserProfile) -> Unit
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val colors = AppTheme.colors
    val profileService = remember { SupabaseProfileService() }

    val effectiveUserId = targetUserId.ifBlank { currentUserId }

    val cachedInitialList = remember(effectiveUserId) {
        FollowDataCacheStore.getCachedList(effectiveUserId, "FOLLOWERS") ?: emptyList()
    }
    val cachedInitialStats = remember(effectiveUserId) {
        FollowDataCacheStore.getCachedStats(effectiveUserId)
    }

    var searchQuery by remember { mutableStateOf("") }
    var isLoading by remember(effectiveUserId) { mutableStateOf(cachedInitialList.isEmpty()) }
    var isRefreshing by remember { mutableStateOf(false) }

    var followersCount by remember(effectiveUserId) {
        mutableIntStateOf(cachedInitialStats?.followersCount ?: cachedInitialList.size)
    }
    var followersList by remember(effectiveUserId) {
        mutableStateOf<List<UserProfile>>(cachedInitialList)
    }
    var isLoadingMore by remember { mutableStateOf(false) }
    var hasMore by remember { mutableStateOf(true) }
    val listState = rememberLazyListState()
    val pageSize = 30

    // Map to track who current logged-in user is following
    val followingStatusMap = remember { mutableStateMapOf<String, Boolean>() }

    // Pre-populate following status from cache
    LaunchedEffect(currentUserId) {
        val cachedFollowing = FollowDataCacheStore.getFollowingSet(currentUserId)
        if (cachedFollowing != null && followingStatusMap.isEmpty()) {
            followersList.forEach { u ->
                followingStatusMap[u.id] = cachedFollowing.contains(u.id)
            }
        }
    }

    fun loadData(isUserPullRefresh: Boolean = false) {
        scope.launch {
            if (followersList.isEmpty()) {
                isLoading = true
            }
            if (isUserPullRefresh) {
                isRefreshing = true
            }
            hasMore = true
            try {
                val stats = profileService.fetchFollowStats(effectiveUserId)
                FollowDataCacheStore.putStats(effectiveUserId, stats)
                followersCount = stats.followersCount

                val list = profileService.fetchFollowers(effectiveUserId, offset = 0, limit = pageSize)
                FollowDataCacheStore.putList(effectiveUserId, "FOLLOWERS", list)
                followersList = list
                hasMore = list.size >= pageSize

                val myFollowingIds = profileService.fetchFollowingIdSet(currentUserId)
                FollowDataCacheStore.putFollowingSet(currentUserId, myFollowingIds)
                followingStatusMap.clear()
                list.forEach { u ->
                    followingStatusMap[u.id] = myFollowingIds.contains(u.id)
                }
            } catch (_: Exception) {
            } finally {
                isLoading = false
                isRefreshing = false
            }
        }
    }

    fun loadMoreFollowers() {
        if (!isLoadingMore && !isLoading && hasMore) {
            scope.launch {
                isLoadingMore = true
                val next = profileService.fetchFollowers(effectiveUserId, offset = followersList.size, limit = pageSize)
                if (next.isNotEmpty()) {
                    val existing = followersList.map { it.id }.toSet()
                    val distinctNew = next.filter { it.id !in existing }
                    followersList = followersList + distinctNew

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
        derivedStateOf {
            val total = listState.layoutInfo.totalItemsCount
            val last = listState.layoutInfo.visibleItemsInfo.lastOrNull()?.index ?: 0
            hasMore && !isLoadingMore && !isLoading && total > 0 && last >= total - 2 && searchQuery.isBlank()
        }
    }

    LaunchedEffect(shouldLoadMore) {
        if (shouldLoadMore) {
            loadMoreFollowers()
        }
    }

    LaunchedEffect(effectiveUserId) {
        loadData()
    }

    val filteredFollowers = remember(followersList, searchQuery) {
        if (searchQuery.isBlank()) {
            followersList
        } else {
            val q = searchQuery.trim().lowercase()
            followersList.filter {
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
                            text = "Followers",
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
                                text = "$followersCount",
                                fontSize = 12.sp,
                                fontWeight = FontWeight.Bold,
                                color = QivoYellow,
                                modifier = Modifier.padding(horizontal = 8.dp, vertical = 2.dp)
                            )
                        }
                    }
                },
                navigationIcon = {
                    IconButton(
                        onClick = onBackClick,
                        modifier = Modifier.testTag("btn_followers_back")
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
            // Search Bar
            OutlinedTextField(
                value = searchQuery,
                onValueChange = { searchQuery = it },
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp, vertical = 8.dp)
                    .testTag("input_search_followers"),
                placeholder = {
                    Text("Search followers by name or ID...", color = colors.textSecondary, fontSize = 14.sp)
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

            if (isLoading && followersList.isEmpty()) {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .weight(1f),
                    contentAlignment = Alignment.Center
                ) {
                    App3DMascotLoader(message = "Loading followers...")
                }
            } else if (filteredFollowers.isEmpty() && !isLoading) {
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
                                    imageVector = Icons.Default.PersonAdd,
                                    contentDescription = null,
                                    modifier = Modifier.size(36.dp),
                                    tint = QivoYellow
                                )
                            }
                        }

                        Spacer(modifier = Modifier.height(16.dp))

                        Text(
                            text = if (searchQuery.isNotBlank()) "No Matching Followers" else "No Followers Yet",
                            fontSize = 18.sp,
                            fontWeight = FontWeight.Bold,
                            color = colors.textPrimary
                        )

                        Spacer(modifier = Modifier.height(8.dp))

                        Text(
                            text = if (searchQuery.isNotBlank()) "Try searching with a different name or numeric ID."
                            else "Share your profile and chat in party rooms to gain followers!",
                            fontSize = 13.sp,
                            color = colors.textSecondary,
                            textAlign = androidx.compose.ui.text.style.TextAlign.Center,
                            lineHeight = 18.sp
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
                    contentPadding = PaddingValues(top = 8.dp, bottom = 24.dp)
                ) {
                    items(filteredFollowers, key = { it.id }) { user ->
                        val isFollowed = followingStatusMap[user.id] == true
                        val isSelf = user.id == currentUserId

                        FollowerItemCard(
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

                    if (isLoadingMore) {
                        item(key = "followers_loading_more") {
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
}

@Composable
private fun FollowerItemCard(
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
            .testTag("card_follower_${user.numericId}"),
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
                Text(
                    text = user.name.ifBlank { "QIVO User" },
                    fontSize = 15.sp,
                    fontWeight = FontWeight.Bold,
                    color = colors.textPrimary,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )

                Spacer(modifier = Modifier.height(3.dp))

                Row(verticalAlignment = Alignment.CenterVertically) {
                    // Gender Badge
                    val isFemale = user.gender.equals("Female", true)
                    Surface(
                        shape = RoundedCornerShape(6.dp),
                        color = if (isFemale) Color(0xFFF8A4EC) else Color(0xFF72C2F8)
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
                        .testTag("btn_chat_follower_${user.numericId}"),
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
                            .testTag("btn_follow_follower_${user.numericId}"),
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
                                text = if (isFollowed) "Following" else "Follow Back",
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

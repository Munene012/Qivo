package com.example.ui.screens

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
import androidx.compose.material.icons.filled.Clear
import androidx.compose.material.icons.filled.Group
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
import com.example.ui.components.App3DMascotLoader
import com.example.ui.theme.AppTheme
import com.example.ui.theme.QivoYellow
import kotlinx.coroutines.launch

/**
 * Dedicated Friends Screen: Displays mutual friends (users who follow each other).
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun FriendsScreen(
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
        FollowDataCacheStore.getCachedList(effectiveUserId, "FRIENDS") ?: emptyList()
    }
    val cachedInitialStats = remember(effectiveUserId) {
        FollowDataCacheStore.getCachedStats(effectiveUserId)
    }

    var searchQuery by remember { mutableStateOf("") }
    var isLoading by remember(effectiveUserId) { mutableStateOf(cachedInitialList.isEmpty()) }
    var isRefreshing by remember { mutableStateOf(false) }

    var friendsCount by remember(effectiveUserId) {
        mutableIntStateOf(cachedInitialStats?.friendsCount ?: cachedInitialList.size)
    }
    var friendsList by remember(effectiveUserId) {
        mutableStateOf<List<UserProfile>>(cachedInitialList)
    }

    fun loadData(isUserPullRefresh: Boolean = false) {
        scope.launch {
            if (friendsList.isEmpty()) {
                isLoading = true
            }
            if (isUserPullRefresh) {
                isRefreshing = true
            }
            try {
                val stats = profileService.fetchFollowStats(effectiveUserId)
                FollowDataCacheStore.putStats(effectiveUserId, stats)
                friendsCount = stats.friendsCount

                val list = profileService.fetchCategoryUsers(effectiveUserId, "FRIENDS")
                FollowDataCacheStore.putList(effectiveUserId, "FRIENDS", list)
                friendsList = list
            } catch (_: Exception) {
            } finally {
                isLoading = false
                isRefreshing = false
            }
        }
    }

    LaunchedEffect(effectiveUserId) {
        loadData()
    }

    val filteredFriends = remember(friendsList, searchQuery) {
        if (searchQuery.isBlank()) {
            friendsList
        } else {
            val q = searchQuery.trim().lowercase()
            friendsList.filter {
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
                            text = "Friends",
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
                                text = "$friendsCount",
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
                        modifier = Modifier.testTag("btn_friends_back")
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
                    .testTag("input_search_friends"),
                placeholder = {
                    Text("Search mutual friends by name or ID...", color = colors.textSecondary, fontSize = 14.sp)
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

            if (isLoading && friendsList.isEmpty()) {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .weight(1f),
                    contentAlignment = Alignment.Center
                ) {
                    App3DMascotLoader(message = "Loading mutual friends...")
                }
            } else if (filteredFriends.isEmpty() && !isLoading) {
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
                                    imageVector = Icons.Default.Group,
                                    contentDescription = null,
                                    modifier = Modifier.size(36.dp),
                                    tint = QivoYellow
                                )
                            }
                        }

                        Spacer(modifier = Modifier.height(16.dp))

                        Text(
                            text = if (searchQuery.isNotBlank()) "No Matching Friends" else "No Mutual Friends Yet",
                            fontSize = 18.sp,
                            fontWeight = FontWeight.Bold,
                            color = colors.textPrimary
                        )

                        Spacer(modifier = Modifier.height(8.dp))

                        Text(
                            text = if (searchQuery.isNotBlank()) "Try searching with a different name or numeric ID."
                            else "Follow users who follow you back to become mutual friends!",
                            fontSize = 13.sp,
                            color = colors.textSecondary,
                            textAlign = androidx.compose.ui.text.style.TextAlign.Center,
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
                    items(filteredFriends, key = { it.id }) { user ->
                        FriendItemCard(
                            user = user,
                            onCardClick = { onOpenUserDetail(user) },
                            onChatClick = { onOpenConversation(user) }
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun FriendItemCard(
    user: UserProfile,
    onCardClick: () -> Unit,
    onChatClick: () -> Unit
) {
    val colors = AppTheme.colors

    Surface(
        modifier = Modifier
            .fillMaxWidth()
            .clickable { onCardClick() }
            .testTag("card_friend_${user.numericId}"),
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

                    // Mutual Friend Badge
                    Surface(
                        shape = RoundedCornerShape(6.dp),
                        color = Color(0xFF00C853).copy(alpha = 0.2f)
                    ) {
                        Text(
                            text = "🤝 Mutual",
                            fontSize = 10.sp,
                            fontWeight = FontWeight.Bold,
                            color = Color(0xFF00C853),
                            modifier = Modifier.padding(horizontal = 5.dp, vertical = 1.dp)
                        )
                    }
                }

                Spacer(modifier = Modifier.height(3.dp))

                Row(verticalAlignment = Alignment.CenterVertically) {
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

            // Direct Chat Button
            Button(
                onClick = onChatClick,
                modifier = Modifier
                    .height(36.dp)
                    .testTag("btn_chat_friend_${user.numericId}"),
                shape = RoundedCornerShape(18.dp),
                colors = ButtonDefaults.buttonColors(
                    containerColor = Color(0xFFB3FF00),
                    contentColor = Color.Black
                ),
                contentPadding = PaddingValues(horizontal = 14.dp, vertical = 0.dp),
                elevation = ButtonDefaults.buttonElevation(defaultElevation = 0.dp)
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(
                        imageVector = Icons.Default.Chat,
                        contentDescription = null,
                        modifier = Modifier.size(14.dp),
                        tint = Color.Black
                    )
                    Spacer(modifier = Modifier.width(5.dp))
                    Text(
                        text = "Chat",
                        fontSize = 12.sp,
                        fontWeight = FontWeight.Bold
                    )
                }
            }
        }
    }
}

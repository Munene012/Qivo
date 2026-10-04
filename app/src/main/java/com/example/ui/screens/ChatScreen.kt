package com.example.ui.screens

import android.content.Context
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Chat
import androidx.compose.material.icons.filled.ChatBubble
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Group
import androidx.compose.material.icons.filled.Headphones
import androidx.compose.material.icons.filled.Menu
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.SupportAgent
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import com.example.data.AvatarHelper
import com.example.data.UserProfile
import com.example.data.UserSessionManager
import com.example.ui.components.AppLoadingSpinner
import com.example.ui.components.AppToast
import com.example.ui.components.CustomRefreshHeaderItem
import com.example.ui.components.rememberCustomPullRefreshState
import com.example.ui.theme.AppTheme
import com.example.ui.theme.QivoOrange
import kotlinx.coroutines.launch

@OptIn(ExperimentalFoundationApi::class, ExperimentalMaterial3Api::class)
@Composable
fun ChatScreen(
    refreshTrigger: Long = 0L,
    listState: LazyListState = rememberLazyListState(),
    viewModel: ChatListViewModel = androidx.lifecycle.viewmodel.compose.viewModel(),
    onOpenUserDetail: (UserProfile) -> Unit = {},
    onOpenConversation: (UserProfile) -> Unit = {},
    onOpenAgencyGroupChat: () -> Unit = {},
    onOpenOfficialTeam: () -> Unit = {},
    onOpenFriends: () -> Unit = {},
    onOpenSupport: () -> Unit = {}
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()

    val session = remember { UserSessionManager.getSession(context) }
    val currentUserId = session?.userId ?: ""

    // Initialize ViewModel once with user ID
    LaunchedEffect(currentUserId) {
        if (currentUserId.isNotBlank()) {
            viewModel.initialize(currentUserId)
        }
    }

    val conversations by viewModel.conversations.collectAsState()
    val initialLoading by viewModel.initialLoading.collectAsState()
    val isRefreshing by viewModel.isRefreshing.collectAsState()
    val displayedLimit by viewModel.displayedLimit.collectAsState()
    val isLoadingMoreChats by viewModel.isLoadingMoreChats.collectAsState()
    val hasMoreServerChats by viewModel.hasMoreServerChats.collectAsState()
    val profilesMap by viewModel.profilesMap.collectAsState()
    val totalUnreadCount by viewModel.totalUnreadCount.collectAsState()

    var selectedTab by remember { mutableStateOf("All") } // "All", "Unread"
    var selectedChatForDelete by remember { mutableStateOf<ConversationItem?>(null) }

    val lifecycleOwner = LocalLifecycleOwner.current
    DisposableEffect(lifecycleOwner) {
        val observer = LifecycleEventObserver { _, event ->
            if (event == Lifecycle.Event.ON_RESUME) {
                viewModel.onScreenResumed()
            }
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose {
            lifecycleOwner.lifecycle.removeObserver(observer)
        }
    }

    val pullRefreshState = rememberCustomPullRefreshState(
        onRefresh = {
            viewModel.pullToRefresh()
        }
    )

    // Scroll to top and trigger refresh when refreshTrigger is activated
    LaunchedEffect(refreshTrigger) {
        if (refreshTrigger > 0L) {
            val isAtTop = listState.firstVisibleItemIndex == 0 && listState.firstVisibleItemScrollOffset <= 10
            if (!isAtTop) {
                listState.animateScrollToItem(0)
            }
            pullRefreshState.triggerRefresh(scope)
            viewModel.pullToRefresh()
        }
    }

    // Filter conversations based on selected tab
    val filteredConversations = remember(conversations, selectedTab, displayedLimit) {
        val base = conversations.take(displayedLimit)
        when (selectedTab) {
            "Unread" -> base.filter { it.isUnread || it.unreadCount > 0 }
            else -> base
        }
    }

    // Load more chats when scrolling near bottom
    val shouldLoadMoreChats by remember {
        derivedStateOf {
            val totalItems = listState.layoutInfo.totalItemsCount
            val lastVisibleItem = listState.layoutInfo.visibleItemsInfo.lastOrNull()?.index ?: 0
            totalItems > 0 && lastVisibleItem >= totalItems - 3 && (displayedLimit < conversations.size || (!isLoadingMoreChats && hasMoreServerChats))
        }
    }

    LaunchedEffect(shouldLoadMoreChats) {
        if (shouldLoadMoreChats && !isLoadingMoreChats) {
            viewModel.loadMoreChats()
        }
    }

    val welcomePrefs = remember { context.getSharedPreferences("qivo_prefs", Context.MODE_PRIVATE) }
    val hasClaimedWelcomeBonus by remember { 
        mutableStateOf(welcomePrefs.getBoolean("welcome_bonus_claimed", false)) 
    }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(Color(0xFF381A05)) // Matching warm espresso background
            .testTag("chat_screen_root")
    ) {
        // Atmospheric Top Sunset Orange-Yellow Glow Overlay (replaces teal/blue as requested)
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .height(260.dp)
                .background(
                    Brush.verticalGradient(
                        listOf(
                            Color(0x60E65100), // Vibrant deep sunset orange at the very top
                            Color(0x35FF9100), // Amber mid glow
                            Color(0x12FFD54F), // Soft gold aura
                            Color.Transparent
                        )
                    )
                )
        )

        Column(
            modifier = Modifier
                .fillMaxSize()
                .statusBarsPadding()
        ) {
            // 1. Top Bar: "Message" title, "Friends" capsule button, and Support Headphone button
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp, vertical = 12.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Text(
                    text = "Message",
                    fontSize = 28.sp,
                    fontWeight = FontWeight.Black,
                    fontStyle = FontStyle.Italic,
                    color = Color.White,
                    letterSpacing = 0.5.sp
                )

                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    // Friends Capsule Pill Button
                    Surface(
                        onClick = onOpenFriends,
                        shape = RoundedCornerShape(20.dp),
                        color = Color(0x331F212E),
                        border = androidx.compose.foundation.BorderStroke(1.dp, Color(0x40FFFFFF)),
                        modifier = Modifier.height(36.dp)
                    ) {
                        Row(
                            modifier = Modifier.padding(horizontal = 14.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Icon(
                                imageVector = Icons.Default.Group,
                                contentDescription = "Friends",
                                tint = Color.White,
                                modifier = Modifier.size(16.dp)
                            )
                            Spacer(modifier = Modifier.width(6.dp))
                            Text(
                                text = "Friends",
                                color = Color.White,
                                fontSize = 13.sp,
                                fontWeight = FontWeight.Bold
                            )
                        }
                    }

                }
            }

            // 2. Filter Tabs Row: [ All ] [ Unread ] [ Familiar Faces ]  [ ☰ ]
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp, vertical = 6.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    listOf("All", "Unread").forEach { tab ->
                        val isSelected = selectedTab == tab
                        Surface(
                            onClick = { selectedTab = tab },
                            shape = RoundedCornerShape(18.dp),
                            color = if (isSelected) Color(0xFF282A3A) else Color(0xFF131520),
                            border = if (isSelected) androidx.compose.foundation.BorderStroke(1.dp, Color(0x40FFFFFF)) else null,
                            modifier = Modifier.height(34.dp)
                        ) {
                            Box(
                                modifier = Modifier.padding(horizontal = 16.dp),
                                contentAlignment = Alignment.Center
                            ) {
                                Text(
                                    text = tab,
                                    fontSize = 13.sp,
                                    fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Medium,
                                    color = if (isSelected) Color.White else Color(0xFF8E92A4)
                                )
                            }
                        }
                    }
                }

                // Filter / Menu icon removed as requested
                /*
                IconButton(
                    onClick = {
                        selectedTab = if (selectedTab == "All") "Unread" else "All"
                    },
                    modifier = Modifier.size(34.dp)
                ) {
                    Icon(
                        imageVector = Icons.Default.Menu,
                        contentDescription = "Filter",
                        tint = Color(0xFF8E92A4),
                        modifier = Modifier.size(20.dp)
                    )
                }
                */
            }

            Spacer(modifier = Modifier.height(6.dp))

            // 3. Main Scrollable List
            LazyColumn(
                state = listState,
                modifier = Modifier
                    .fillMaxSize()
                    .weight(1f),
                contentPadding = PaddingValues(
                    start = 0.dp,
                    end = 0.dp,
                    top = 4.dp,
                    bottom = 90.dp
                ),
                verticalArrangement = Arrangement.spacedBy(2.dp)
            ) {
                // Pull to Refresh indicator
                item(key = "pull_refresh_item") {
                    CustomRefreshHeaderItem(pullRefreshState = pullRefreshState)
                }

                // Pinned Item 1: Niko / Qivo Team
                item(key = "pinned_qivo_team_item") {
                    PinnedChatRow(
                        title = "Qivo Team",
                        subtitle = if (hasClaimedWelcomeBonus) "[Welcome! 500 Coins Claimed]" else "[Welcome]",
                        timestamp = "9/27",
                        icon = {
                            QivoTeamAvatar(size = 54.dp)
                        },
                        onClick = onOpenOfficialTeam
                    )
                }

                // Initial loading state
                if (initialLoading && conversations.isEmpty()) {
                    item(key = "initial_loading_item") {
                        Box(
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(260.dp),
                            contentAlignment = Alignment.Center
                        ) {
                            AppLoadingSpinner(size = 36.dp)
                        }
                    }
                } else if (filteredConversations.isEmpty()) {
                    item(key = "empty_conversations_item") {
                        Column(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(horizontal = 24.dp)
                                .padding(top = 40.dp),
                            horizontalAlignment = Alignment.CenterHorizontally,
                            verticalArrangement = Arrangement.Center
                        ) {
                            Box(
                                modifier = Modifier
                                    .size(70.dp)
                                    .clip(CircleShape)
                                    .background(Color(0xFF1B1D2A)),
                                contentAlignment = Alignment.Center
                            ) {
                                Icon(
                                    imageVector = Icons.Default.Chat,
                                    contentDescription = "No Messages",
                                    tint = Color(0xFF6C728C),
                                    modifier = Modifier.size(34.dp)
                                )
                            }

                            Spacer(modifier = Modifier.height(16.dp))

                            Text(
                                text = if (selectedTab == "Unread") "No Unread Messages" else "No Messages Yet",
                                fontSize = 17.sp,
                                fontWeight = FontWeight.Bold,
                                color = Color.White,
                                textAlign = TextAlign.Center
                            )

                            Spacer(modifier = Modifier.height(6.dp))

                            Text(
                                text = "Your conversations and live messages will appear here.",
                                fontSize = 13.sp,
                                color = Color(0xFF8E92A4),
                                textAlign = TextAlign.Center
                            )
                        }
                    }
                } else {
                    // Conversation Items
                    items(
                        count = filteredConversations.size,
                        key = { index ->
                            val it = filteredConversations[index]
                            "conv_${it.partnerId}_${it.partnerNumericId}_$index"
                        },
                        contentType = { "conversation_item" }
                    ) { index ->
                        val item = filteredConversations[index]
                        val targetProfile = profilesMap[item.partnerId] ?: UserProfile(
                            id = item.partnerId,
                            numericId = item.partnerNumericId,
                            email = "",
                            name = item.partnerName,
                            gender = item.partnerGender,
                            birthDate = "2000-01-01",
                            country = item.partnerCountry,
                            avatarUrl = item.partnerAvatar,
                            userLevel = 0
                        )

                        ReferenceStyleConversationRow(
                            item = item,
                            profile = targetProfile,
                            onClick = {
                                onOpenConversation(targetProfile)
                            },
                            onLongClick = {
                                selectedChatForDelete = item
                            },
                            onAvatarClick = {
                                onOpenUserDetail(targetProfile)
                            }
                        )
                    }
                }

                if (displayedLimit < conversations.size || isLoadingMoreChats) {
                    item(key = "chat_list_loading_more") {
                        Box(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(vertical = 14.dp),
                            contentAlignment = Alignment.Center
                        ) {
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.Center
                            ) {
                                AppLoadingSpinner(size = 18.dp)
                                Spacer(modifier = Modifier.width(8.dp))
                                Text(
                                    text = "Loading more chats...",
                                    fontSize = 12.sp,
                                    color = Color(0xFF8E92A4)
                                )
                            }
                        }
                    }
                }
            }
        }
    }

    // Long Press Bottom Popup Modal for Soft Deleting Chat
    if (selectedChatForDelete != null) {
        val chatToDelete = selectedChatForDelete!!
        ModalBottomSheet(
            onDismissRequest = { selectedChatForDelete = null },
            containerColor = Color(0xFF191B26),
            shape = RoundedCornerShape(topStart = 20.dp, topEnd = 20.dp)
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 24.dp, vertical = 16.dp),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                Text(
                    text = "Delete Chat with ${chatToDelete.partnerName}?",
                    fontSize = 18.sp,
                    fontWeight = FontWeight.Bold,
                    color = Color.White,
                    textAlign = TextAlign.Center
                )
                Spacer(modifier = Modifier.height(6.dp))
                Text(
                    text = "This will delete this conversation on your device.",
                    fontSize = 13.sp,
                    color = Color(0xFF8E92A4),
                    textAlign = TextAlign.Center
                )
                Spacer(modifier = Modifier.height(20.dp))
                Button(
                    onClick = {
                        val partnerId = chatToDelete.partnerId
                        viewModel.deleteConversation(partnerId)
                        selectedChatForDelete = null
                        AppToast.show("Chat deleted")
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = Color(0xFFFF3D00)),
                    shape = RoundedCornerShape(12.dp),
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(48.dp)
                ) {
                    Icon(
                        imageVector = Icons.Default.Delete,
                        contentDescription = "Delete",
                        tint = Color.White,
                        modifier = Modifier.size(18.dp)
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    Text("Delete", color = Color.White, fontWeight = FontWeight.Bold, fontSize = 15.sp)
                }
                Spacer(modifier = Modifier.height(16.dp))
            }
        }
    }
}

/**
 * Pinned Item Row (e.g. Qivo Team, Strangers' Messages) matching reference design exactly
 */
@Composable
private fun PinnedChatRow(
    title: String,
    subtitle: String,
    timestamp: String,
    icon: @Composable () -> Unit,
    onClick: () -> Unit
) {
    Surface(
        onClick = onClick,
        color = Color.Transparent,
        modifier = Modifier.fillMaxWidth()
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp, vertical = 10.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            icon()

            Spacer(modifier = Modifier.width(14.dp))

            Column(modifier = Modifier.weight(1f)) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = title,
                        fontWeight = FontWeight.Bold,
                        fontSize = 16.sp,
                        color = Color.White,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )

                    if (timestamp.isNotBlank()) {
                        Text(
                            text = timestamp,
                            fontSize = 11.5.sp,
                            color = Color(0xFF6B7085)
                        )
                    }
                }

                Spacer(modifier = Modifier.height(4.dp))

                Text(
                    text = subtitle,
                    fontSize = 13.sp,
                    color = Color(0xFF8E92A4),
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
            }
        }
    }
}

/**
 * Conversation Row exactly matching the screenshot:
 * - Avatar with unread count badge at top-right
 * - Online green indicator at bottom-right of avatar
 * - User name + [ ♦ 0 ] diamond wealth level badge
 * - Message preview text
 * - Timestamp on the right
 */
@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun ReferenceStyleConversationRow(
    item: ConversationItem,
    profile: UserProfile,
    onClick: () -> Unit,
    onLongClick: () -> Unit,
    onAvatarClick: () -> Unit
) {
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .combinedClickable(
                onClick = onClick,
                onLongClick = onLongClick
            ),
        shape = RoundedCornerShape(0.dp),
        colors = CardDefaults.cardColors(containerColor = Color.Transparent),
        border = null
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp, vertical = 10.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            // Profile Photo / Avatar with Online Dot & Unread Badge
            Box(
                modifier = Modifier
                    .size(54.dp)
                    .clickable(onClick = onAvatarClick)
            ) {
                // Round Avatar
                AvatarHelper.UserAvatarImage(
                    avatarUrl = item.partnerAvatar,
                    userId = item.partnerId,
                    gender = item.partnerGender,
                    numericId = item.partnerNumericId,
                    contentDescription = item.partnerName,
                    showFrame = false,
                    shape = CircleShape,
                    modifier = Modifier
                        .fillMaxSize()
                        .clip(CircleShape),
                    contentScale = ContentScale.Crop
                )

                // Unread Badge removed as requested


                // Live Online Dot at BOTTOM-RIGHT of Avatar
                Box(
                    modifier = Modifier
                        .size(13.dp)
                        .align(Alignment.BottomEnd)
                        .clip(CircleShape)
                        .background(Color(0xFF090A10))
                        .padding(1.5.dp)
                        .clip(CircleShape)
                        .background(if (item.isOnline) Color(0xFF00E676) else Color(0xFF4A4E63))
                )
            }

            Spacer(modifier = Modifier.width(14.dp))

            // Text Content Column
            Column(modifier = Modifier.weight(1f)) {
                // Name + Diamond Wealth Level Badge + Timestamp
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        modifier = Modifier.weight(1f, fill = false)
                    ) {
                        Text(
                            text = item.partnerName.ifBlank { "User" },
                            fontSize = 16.sp,
                            fontWeight = FontWeight.Bold,
                            color = Color.White,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )

                        Spacer(modifier = Modifier.width(6.dp))

                        // Diamond Wealth / Level Badge [ ♦ 0 ] removed as requested
                        // WealthLevelDiamondBadge(level = profile.userLevel)
                    }

                    Spacer(modifier = Modifier.width(8.dp))

                    // Formatted Timestamp (e.g. 7:42 AM or 9/27)
                    Text(
                        text = formatConversationTimestamp(item.timestamp),
                        fontSize = 11.5.sp,
                        color = Color(0xFF6B7085)
                    )
                }

                Spacer(modifier = Modifier.height(4.dp))

                // Message Preview
                val previewSnippet = remember(item.latestMessage) {
                    val raw = item.latestMessage
                        .replace(Regex("^\\[GLOBAL BLAST\\]\\s*", RegexOption.IGNORE_CASE), "")
                        .replace(Regex("^GLOBAL BLAST:\\s*", RegexOption.IGNORE_CASE), "")
                        .replace(Regex("^Global Blast:\\s*", RegexOption.IGNORE_CASE), "")
                        .replace(Regex("^\\[BLAST\\]\\s*", RegexOption.IGNORE_CASE), "")
                        .trim()
                    if (raw.startsWith("Draft:", ignoreCase = true)) {
                        raw
                    } else {
                        val lower = raw.lowercase()
                        if (lower.startsWith("[voice]") || lower.contains("/voice/") || lower.endsWith(".m4a") || lower.endsWith(".aac")) {
                            "[Voice message]"
                        } else if (lower.startsWith("[image]") || lower.startsWith("[photo]") || lower.contains("/photos/")) {
                            "[Photo]"
                        } else if (lower.startsWith("[gift]")) {
                            val g = raw.removePrefix("[gift]").trim()
                            if (g.isNotEmpty()) "🎁 $g" else "🎁 Gift"
                        } else {
                            raw
                        }
                    }
                }

                Text(
                    text = previewSnippet.ifBlank { "Tap to chat" },
                    fontSize = 13.5.sp,
                    color = Color(0xFF8E92A4),
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
            }
        }
    }
}

/**
 * Diamond Wealth Level Badge: [ ♦ 0 ]
 * Styled exactly like the screenshot with metallic dark capsule and diamond symbol
 */
@Composable
fun WealthLevelDiamondBadge(
    level: Int,
    modifier: Modifier = Modifier
) {
    Surface(
        shape = RoundedCornerShape(10.dp),
        color = if (level > 0) Color(0xFF3B2D54) else Color(0xFF2E3240),
        border = androidx.compose.foundation.BorderStroke(
            0.5.dp,
            if (level > 0) Color(0xFF9D84FF) else Color(0x33FFFFFF)
        ),
        modifier = modifier
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 6.dp, vertical = 1.5.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(3.dp)
        ) {
            Text(
                text = "♦",
                fontSize = 9.sp,
                fontWeight = FontWeight.Bold,
                color = if (level > 0) Color(0xFFC4B5FD) else Color(0xFF9499AD)
            )
            Text(
                text = "$level",
                fontSize = 10.sp,
                fontWeight = FontWeight.Bold,
                color = Color.White
            )
        }
    }
}

/**
 * 3D Qivo Team Circular Avatar for Pinned Row
 */
@Composable
fun QivoTeamAvatar(modifier: Modifier = Modifier, size: Dp = 54.dp) {
    Box(
        modifier = modifier
            .size(size)
            .clip(CircleShape)
            .background(
                Brush.linearGradient(
                    listOf(Color(0xFFFFD54F), Color(0xFFFF9100), Color(0xFFFF3D00))
                )
            ),
        contentAlignment = Alignment.Center
    ) {
        Box(
            modifier = Modifier
                .size(size * 0.82f)
                .clip(CircleShape)
                .background(
                    Brush.radialGradient(
                        listOf(Color(0xFF281806), Color(0xFF140C03))
                    )
                ),
            contentAlignment = Alignment.Center
        ) {
            Text(
                text = "Qivo",
                fontSize = 13.sp,
                fontWeight = FontWeight.Black,
                color = Color(0xFFFFD54F)
            )
        }
    }
}

/**
 * 3D Glossy Pink/Orange Speech Bubble Avatar for Strangers' Messages Pinned Row
 */
@Composable
fun StrangersMessagesAvatar(modifier: Modifier = Modifier, size: Dp = 54.dp) {
    Box(
        modifier = modifier
            .size(size)
            .clip(CircleShape)
            .background(
                Brush.linearGradient(
                    listOf(Color(0xFFFF4081), Color(0xFFF50057), Color(0xFF880E4F))
                )
            ),
        contentAlignment = Alignment.Center
    ) {
        // Inner speech bubble graphic
        Box(
            modifier = Modifier
                .size(size * 0.65f)
                .clip(RoundedCornerShape(size * 0.25f))
                .background(Color.White.copy(alpha = 0.92f)),
            contentAlignment = Alignment.Center
        ) {
            Icon(
                imageVector = Icons.Default.ChatBubble,
                contentDescription = "Strangers",
                tint = Color(0xFFE91E63),
                modifier = Modifier.size(size * 0.40f)
            )
        }

        // Small orange notification dot at top right
        Box(
            modifier = Modifier
                .size(12.dp)
                .align(Alignment.TopEnd)
                .clip(CircleShape)
                .background(Color(0xFFFF9100))
                .border(2.dp, Color(0xFF090A10), CircleShape)
        )
    }
}

private fun formatConversationTimestamp(isoString: String): String {
    if (isoString.isBlank()) return "Just now"
    return try {
        val clean = isoString.substringBefore(".").substringBefore("+").removeSuffix("Z")
        val parsed = java.text.SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss", java.util.Locale.US).apply {
            timeZone = java.util.TimeZone.getTimeZone("UTC")
        }.parse(clean)
        if (parsed != null) {
            val now = System.currentTimeMillis()
            val diffMs = now - parsed.time
            val diffHours = diffMs / (1000 * 60 * 60)

            if (diffHours < 24) {
                java.text.SimpleDateFormat("h:mm a", java.util.Locale.US).format(parsed)
            } else if (diffHours < 48) {
                "Yesterday"
            } else {
                java.text.SimpleDateFormat("M/d", java.util.Locale.US).format(parsed)
            }
        } else {
            if (isoString.length >= 10) isoString.substring(5, 10).replace("-", "/") else "Just now"
        }
    } catch (_: Exception) {
        if (isoString.length >= 10) isoString.substring(5, 10).replace("-", "/") else "Just now"
    }
}

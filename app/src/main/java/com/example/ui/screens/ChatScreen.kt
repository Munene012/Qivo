package com.example.ui.screens
import com.example.ui.components.AppToast
import com.example.data.AppDataCacheManager

import android.content.Intent
import android.net.Uri
import android.os.Build
import android.provider.Settings
import android.widget.Toast
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
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
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.statusBars
import androidx.compose.foundation.layout.asPaddingValues
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.draw.clipToBounds
import com.example.ui.components.QivoBackgroundStamp
import com.example.ui.theme.QivoOrange
import com.example.ui.theme.QivoOrangeDark
import com.example.ui.theme.QivoGoldLight
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Chat
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.MarkChatUnread
import androidx.compose.material.icons.filled.NotificationsActive
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.app.NotificationManagerCompat
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import com.example.data.AvatarHelper
import com.example.data.ChatMessage
import com.example.data.SupabaseAdService
import com.example.data.AppAdvertisement
import com.example.data.SupabaseChatService
import com.example.data.SupabaseProfileService
import com.example.data.UserProfile
import com.example.data.UserSessionManager
import com.example.ui.components.ChatTopBannerAdCarousel
import com.example.ui.components.ChatTopBannerAd
import com.example.ui.components.CustomRefreshHeaderItem
import com.example.ui.components.rememberCustomPullRefreshState
import kotlinx.coroutines.launch

@OptIn(ExperimentalFoundationApi::class, ExperimentalMaterial3Api::class)
@Composable
fun ChatScreen(
    refreshTrigger: Long = 0L,
    listState: LazyListState = rememberLazyListState(),
    viewModel: ChatListViewModel = androidx.lifecycle.viewmodel.compose.viewModel(),
    onOpenUserDetail: (UserProfile) -> Unit = {},
    onOpenConversation: (UserProfile) -> Unit = {}
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()

    val session = remember { UserSessionManager.getSession(context) }
    val currentUserId = session?.userId ?: ""

    // Initialize ViewModel once with user ID; stays active throughout the user's session
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
    val chatBannerAds by viewModel.bannerAds.collectAsState()
    val profilesMap by viewModel.profilesMap.collectAsState()
    val totalUnreadCount by viewModel.totalUnreadCount.collectAsState()

    var selectedChatForDelete by remember { mutableStateOf<ConversationItem?>(null) }

    var isNotificationsEnabled by remember {
        mutableStateOf(NotificationManagerCompat.from(context).areNotificationsEnabled())
    }
    var hasDismissedNotificationPrompt by remember { mutableStateOf(false) }

    val lifecycleOwner = LocalLifecycleOwner.current
    DisposableEffect(lifecycleOwner) {
        val observer = LifecycleEventObserver { _, event ->
            if (event == Lifecycle.Event.ON_RESUME) {
                isNotificationsEnabled = NotificationManagerCompat.from(context).areNotificationsEnabled()
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

    // Scroll to top on first click if scrolled down; trigger refresh if already at top / second click
    LaunchedEffect(refreshTrigger) {
        if (refreshTrigger > 0L) {
            val isAtTop = listState.firstVisibleItemIndex == 0 && listState.firstVisibleItemScrollOffset <= 10
            if (!isAtTop) {
                listState.animateScrollToItem(0)
            } else {
                pullRefreshState.triggerRefresh(scope)
                viewModel.pullToRefresh()
            }
        }
    }

    val displayedConversations = remember(conversations, displayedLimit) {
        conversations.take(displayedLimit)
    }

    // Trigger loading more conversations when scrolling near bottom (20 items at a time)
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

    val colors = com.example.ui.theme.AppTheme.colors
    val isDark = colors.isDark

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(colors.screenBg)
            .testTag("chat_screen_root")
    ) {
        // Content Area (Chats scroll behind/under the header)
        Column(
            modifier = Modifier.fillMaxSize()
        ) {
            if (initialLoading && conversations.isEmpty()) {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .weight(1f),
                    contentAlignment = Alignment.Center
                ) {
                    com.example.ui.components.AppLoadingSpinner(size = 36.dp)
                }
            } else if (conversations.isEmpty()) {

                // Empty State with top padding for header
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .weight(1f)
                        .padding(horizontal = 24.dp)
                        .statusBarsPadding()
                        .padding(top = 70.dp),
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.Center
                ) {
                    Box(
                        modifier = Modifier
                            .size(90.dp)
                            .clip(CircleShape)
                            .background(if (isDark) Color(0xFF2E1742) else Color(0xFFF3E8FF)),
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(
                            imageVector = Icons.Default.Chat,
                            contentDescription = "No Messages",
                            tint = Color(0xFFC084FC),
                            modifier = Modifier.size(40.dp)
                        )
                    }

                    Spacer(modifier = Modifier.height(20.dp))

                    Text(
                        text = "No Messages Yet",
                        fontSize = 20.sp,
                        fontWeight = FontWeight.Bold,
                        color = colors.textPrimary,
                        textAlign = TextAlign.Center
                    )

                    Spacer(modifier = Modifier.height(8.dp))

                    Text(
                        text = "Your chats, voice messages, and interactions with real users will appear here.",
                        fontSize = 14.sp,
                        color = colors.textSecondary,
                        textAlign = TextAlign.Center,
                        lineHeight = 20.sp
                    )
                }
            } else {
                // Real Conversations List - scrolls under the header
                val statusBarTopInset = WindowInsets.statusBars.asPaddingValues().calculateTopPadding()
                LazyColumn(
                    state = listState,
                    modifier = Modifier
                        .fillMaxSize()
                        .nestedScroll(pullRefreshState.getNestedScrollConnection(scope)),
                    contentPadding = PaddingValues(
                        start = 16.dp,
                        end = 16.dp,
                        top = statusBarTopInset + 64.dp, // Header height + status bar
                        bottom = 100.dp
                    ),
                    verticalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    // Pull to Refresh indicator inside scroll area
                    item(key = "pull_refresh_item") {
                        CustomRefreshHeaderItem(pullRefreshState = pullRefreshState)
                    }

                    // Continuous Auto-Swiping Top Banner Ad Carousel (max 3, zero layout impact when empty)
                    item(key = "banner_ads_item") {
                        ChatTopBannerAdCarousel(ads = chatBannerAds)
                    }

                    items(
                        items = displayedConversations,
                        key = { it.partnerId },
                        contentType = { "conversation_item" }
                    ) { item ->
                        val targetProfile = profilesMap[item.partnerId] ?: UserProfile(
                            id = item.partnerId,
                            numericId = item.partnerNumericId,
                            email = "",
                            name = item.partnerName,
                            gender = item.partnerGender,
                            birthDate = "2000-01-01",
                            country = item.partnerCountry,
                            avatarUrl = item.partnerAvatar
                        )

                        ConversationItemRow(
                            item = item,
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
                                    com.example.ui.components.AppLoadingSpinner(size = 18.dp)
                                    Spacer(modifier = Modifier.width(8.dp))
                                    Text(
                                        text = "Loading more chats...",
                                        fontSize = 12.sp,
                                        color = colors.textSecondary
                                    )
                                }

                            }
                        }
                    }
                }
            }
        }

        // Top Fixed Header with sunset amber gradient (Chats scroll UNDER this header)
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .clipToBounds()
                .background(
                    if (isDark) {
                        Brush.verticalGradient(
                            listOf(
                                Color(0xFF09120B),
                                Color(0xFF050A06)
                            )
                        )
                    } else {
                        Brush.verticalGradient(
                            listOf(
                                Color(0xFF009639), // Deep Emerald
                                Color(0xFF00B04A), // Jewel Jade
                                Color(0xFF00C853), // Vivid Emerald
                                Color(0xFF26E06D)  // Mint Emerald
                            )
                        )
                    }
                )
        ) {
            QivoBackgroundStamp(
                modifier = Modifier.matchParentSize(),
                isDark = isDark
            )

            // Top Navigation Bar (Chat title with total unread counter & icon)
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .statusBarsPadding()
                    .padding(horizontal = 20.dp, vertical = 14.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Column(horizontalAlignment = Alignment.Start) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text(
                            text = "Chat",
                            fontSize = 24.sp,
                            fontWeight = FontWeight.ExtraBold,
                            color = Color.White
                        )
                        if (totalUnreadCount > 0) {
                            Spacer(modifier = Modifier.width(8.dp))
                            Box(
                                modifier = Modifier
                                    .height(20.dp)
                                    .widthIn(min = 20.dp)
                                    .clip(CircleShape)
                                    .background(Color(0xFFB3FF00))
                                    .padding(horizontal = 6.dp),
                                contentAlignment = Alignment.Center
                            ) {
                                Text(
                                    text = if (totalUnreadCount > 99) "99+" else "$totalUnreadCount",
                                    color = Color.Black,
                                    fontSize = 11.sp,
                                    fontWeight = FontWeight.Bold
                                )
                            }
                        }
                    }
                    Spacer(modifier = Modifier.height(4.dp))
                    Box(
                        modifier = Modifier
                            .width(28.dp)
                            .height(4.dp)
                            .clip(RoundedCornerShape(2.dp))
                            .background(Color(0xFFB3FF00)) // Lime Accent
                    )
                }

                if (totalUnreadCount > 0) {
                    Icon(
                        imageVector = Icons.Default.MarkChatUnread,
                        contentDescription = "Unread Messages",
                        tint = Color.White,
                        modifier = Modifier.size(24.dp)
                    )
                }
            }
        }

        // Floating Bottom Notification Permission Prompt (if notifications are disabled/denied)
        AnimatedVisibility(
            visible = !isNotificationsEnabled && !hasDismissedNotificationPrompt,
            enter = slideInVertically(initialOffsetY = { it }) + fadeIn(),
            exit = slideOutVertically(targetOffsetY = { it }) + fadeOut(),
            modifier = Modifier
                .align(Alignment.BottomCenter)
                .padding(horizontal = 14.dp, vertical = 12.dp)
        ) {
            Surface(
                shape = RoundedCornerShape(18.dp),
                color = if (isDark) Color(0xFF1E1B2E) else Color.White,
                shadowElevation = 10.dp,
                tonalElevation = 6.dp,
                modifier = Modifier
                    .fillMaxWidth()
                    .border(
                        width = 1.dp,
                        brush = Brush.horizontalGradient(
                            listOf(
                                QivoOrange.copy(alpha = 0.5f),
                                QivoGoldLight.copy(alpha = 0.5f)
                            )
                        ),
                        shape = RoundedCornerShape(18.dp)
                    )
                    .testTag("enable_notification_popup")
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 14.dp, vertical = 12.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Box(
                        modifier = Modifier
                            .size(42.dp)
                            .clip(CircleShape)
                            .background(
                                Brush.linearGradient(
                                    listOf(
                                        QivoOrange,
                                        QivoOrangeDark
                                    )
                                )
                            ),
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(
                            imageVector = Icons.Default.NotificationsActive,
                            contentDescription = "Notifications",
                            tint = Color.White,
                            modifier = Modifier.size(22.dp)
                        )
                    }

                    Spacer(modifier = Modifier.width(12.dp))

                    Column(
                        modifier = Modifier.weight(1f)
                    ) {
                        Text(
                            text = "Enable Notifications",
                            fontSize = 14.sp,
                            fontWeight = FontWeight.Bold,
                            color = if (isDark) Color.White else Color(0xFF111827)
                        )
                        Text(
                            text = "Get notified instantly when someone messages or calls you.",
                            fontSize = 11.5.sp,
                            color = if (isDark) Color(0xFF94A3B8) else Color(0xFF64748B),
                            lineHeight = 15.sp,
                            modifier = Modifier.padding(top = 2.dp)
                        )
                    }

                    Spacer(modifier = Modifier.width(8.dp))

                    Button(
                        onClick = {
                            try {
                                val intent = Intent().apply {
                                    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                                        action = Settings.ACTION_APP_NOTIFICATION_SETTINGS
                                        putExtra(Settings.EXTRA_APP_PACKAGE, context.packageName)
                                    } else {
                                        action = Settings.ACTION_APPLICATION_DETAILS_SETTINGS
                                        data = Uri.fromParts("package", context.packageName, null)
                                    }
                                    flags = Intent.FLAG_ACTIVITY_NEW_TASK
                                }
                                context.startActivity(intent)
                            } catch (_: Exception) {
                                try {
                                    val fallbackIntent = Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS).apply {
                                        data = Uri.fromParts("package", context.packageName, null)
                                        flags = Intent.FLAG_ACTIVITY_NEW_TASK
                                    }
                                    context.startActivity(fallbackIntent)
                                } catch (_: Exception) {}
                            }
                        },
                        shape = RoundedCornerShape(10.dp),
                        colors = ButtonDefaults.buttonColors(
                            containerColor = QivoOrange
                        ),
                        contentPadding = PaddingValues(horizontal = 12.dp, vertical = 6.dp),
                        modifier = Modifier.height(34.dp)
                    ) {
                        Text(
                            text = "Enable",
                            color = Color.White,
                            fontSize = 12.sp,
                            fontWeight = FontWeight.Bold
                        )
                    }

                    IconButton(
                        onClick = { hasDismissedNotificationPrompt = true },
                        modifier = Modifier
                            .size(28.dp)
                            .padding(start = 2.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Default.Close,
                            contentDescription = "Dismiss",
                            tint = if (isDark) Color(0xFF64748B) else Color(0xFF9CA3AF),
                            modifier = Modifier.size(16.dp)
                        )
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
            containerColor = colors.cardBg,
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
                    color = colors.textPrimary,
                    textAlign = TextAlign.Center
                )
                Spacer(modifier = Modifier.height(6.dp))
                Text(
                    text = "This will delete this conversation on your side.",
                    fontSize = 13.sp,
                    color = colors.textSecondary,
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

@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun ConversationItemRow(
    item: ConversationItem,
    onClick: () -> Unit,
    onLongClick: () -> Unit,
    onAvatarClick: () -> Unit
) {
    val colors = com.example.ui.theme.AppTheme.colors
    val isDark = colors.isDark

    Card(
        modifier = Modifier
            .fillMaxWidth()
            .combinedClickable(
                onClick = onClick,
                onLongClick = onLongClick
            ),
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(containerColor = colors.cardBg),
        border = null
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(12.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            // Profile Photo / Avatar with Green Online Indicator
            Box(
                modifier = Modifier
                    .size(54.dp)
                    .clickable(onClick = onAvatarClick)
            ) {
                Box(
                    modifier = Modifier.fillMaxSize(),
                    contentAlignment = Alignment.Center
                ) {
                    AvatarHelper.UserAvatarImage(
                        avatarUrl = item.partnerAvatar,
                        userId = item.partnerId,
                        gender = item.partnerGender,
                        numericId = item.partnerNumericId,
                        contentDescription = item.partnerName,
                        showFrame = true,
                        shape = CircleShape,
                        modifier = Modifier.fillMaxSize(),
                        contentScale = ContentScale.Crop
                    )
                }

                // Live Green Online Dot
                if (item.isOnline) {
                    Box(
                        modifier = Modifier
                            .size(13.dp)
                            .align(Alignment.BottomEnd)
                            .clip(CircleShape)
                            .background(if (isDark) Color(0xFF18181C) else Color.White)
                            .padding(2.dp)
                            .clip(CircleShape)
                            .background(Color(0xFF4CAF50))
                    )
                }
            }

            Spacer(modifier = Modifier.width(14.dp))

            Column(modifier = Modifier.weight(1f)) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = item.partnerName,
                        fontSize = 16.sp,
                        fontWeight = FontWeight.Bold,
                        color = colors.textPrimary,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )

                    val formattedDate = if (item.timestamp.length >= 10) {
                        item.timestamp.substring(5, 10).replace("-", "/")
                    } else {
                        "Just now"
                    }

                    Text(
                        text = formattedDate,
                        fontSize = 11.sp,
                        color = colors.textSecondary
                    )
                }

                Spacer(modifier = Modifier.height(4.dp))

                val previewText = remember(item.latestMessage) {
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
                        if (lower.startsWith("[voice]") ||
                            lower.startsWith("voice_") ||
                            lower.contains("/storage/v1/object/public/voice/") ||
                            lower.contains("/storage/v1/object/voice/") ||
                            lower.contains("/voice/") ||
                            lower.contains(".m4a") ||
                            lower.contains(".aac") ||
                            lower.contains(".mp3") ||
                            lower.contains(".wav") ||
                            lower.contains(".ogg") ||
                            lower.contains(".amr") ||
                            lower.contains(".3gp") ||
                            lower.contains(".opus")
                        ) {
                            "[voice]"
                        } else if (raw.startsWith("[image]", ignoreCase = true) ||
                            raw.startsWith("[photo]", ignoreCase = true) ||
                            raw.startsWith("content://", ignoreCase = true) ||
                            raw.startsWith("file://", ignoreCase = true) ||
                            ((raw.startsWith("http://", ignoreCase = true) || raw.startsWith("https://", ignoreCase = true)) &&
                            (raw.contains("/storage/") || raw.contains("/photos/") || raw.contains("/avatars/") ||
                             raw.endsWith(".jpg", ignoreCase = true) || raw.endsWith(".jpeg", ignoreCase = true) ||
                             raw.endsWith(".png", ignoreCase = true) || raw.endsWith(".webp", ignoreCase = true) ||
                             raw.endsWith(".gif", ignoreCase = true)))
                        ) {
                            "[photo]"
                        } else if (raw.startsWith("[gift]", ignoreCase = true)) {
                            val giftText = raw.removePrefix("[gift]").trim()
                            if (giftText.isNotEmpty()) "🎁 $giftText" else "🎁 Gift"
                        } else {
                            raw
                        }
                    }
                }

                val isDraft = previewText.startsWith("Draft:", ignoreCase = true)
                val displayMessageAnnotated = remember(previewText, item.isUnread, isDark, colors) {
                    if (isDraft) {
                        androidx.compose.ui.text.buildAnnotatedString {
                            withStyle(androidx.compose.ui.text.SpanStyle(
                                color = Color(0xFF00E676), // Vibrant green
                                fontWeight = FontWeight.Bold
                            )) {
                                append("Draft: ")
                            }
                            val draftContent = previewText.removePrefix("Draft:").removePrefix("draft:")
                            withStyle(androidx.compose.ui.text.SpanStyle(
                                color = if (item.isUnread) (if (isDark) Color.White else Color(0xFF0F172A)) else colors.textSecondary,
                                fontWeight = if (item.isUnread) FontWeight.Medium else FontWeight.Light
                            )) {
                                append(draftContent)
                            }
                        }
                    } else {
                        androidx.compose.ui.text.buildAnnotatedString {
                            withStyle(androidx.compose.ui.text.SpanStyle(
                                color = if (item.isUnread) (if (isDark) Color.White else Color(0xFF0F172A)) else colors.textSecondary,
                                fontWeight = if (item.isUnread) FontWeight.Medium else FontWeight.Light
                            )) {
                                append(previewText)
                            }
                        }
                    }
                }

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = displayMessageAnnotated,
                        fontSize = 13.sp,
                        letterSpacing = 0.15.sp,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        modifier = Modifier.weight(1f, fill = false)
                    )

                    // Unread Counter Badge and Unread Icon Indicator
                    if (item.unreadCount > 0) {
                        Spacer(modifier = Modifier.width(8.dp))
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(4.dp)
                        ) {
                            Box(
                                modifier = Modifier
                                    .size(8.dp)
                                    .clip(CircleShape)
                                    .background(Color(0xFF00E676))
                            )
                            Box(
                                modifier = Modifier
                                    .height(18.dp)
                                    .widthIn(min = 18.dp)
                                    .clip(CircleShape)
                                    .background(Color(0xFFFF3D00))
                                    .padding(horizontal = 5.dp),
                                contentAlignment = Alignment.Center
                            ) {
                                Text(
                                    text = if (item.unreadCount > 99) "99+" else "${item.unreadCount}",
                                    color = Color.White,
                                    fontSize = 10.sp,
                                    fontWeight = FontWeight.Bold
                                )
                            }
                        }
                    }
                }
            }
        }
    }
}

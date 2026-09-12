package com.example.ui.screens
import com.example.ui.components.AppToast

import android.widget.Toast
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.asPaddingValues
import androidx.compose.foundation.layout.statusBars
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.Send
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Chat
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Female
import androidx.compose.material.icons.filled.Gamepad
import androidx.compose.material.icons.filled.Male
import androidx.compose.material.icons.filled.Person
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Surface
import com.example.ui.components.Chat3DBadgeButton
import com.example.ui.components.GameCenter3DIcon
import com.example.ui.components.AppLoadingSpinner

import com.example.ui.components.MessageBlast3DIcon
import com.example.ui.components.QivoBackgroundStamp
import com.example.ui.components.TasksCenter3DIcon
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.lerp
import androidx.compose.ui.zIndex
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlin.math.roundToInt
import androidx.compose.ui.window.Dialog
import com.example.data.AvatarHelper
import com.example.data.AppAnalyticsService
import com.example.data.AppDataCacheManager
import com.example.data.NetworkUtils
import com.example.data.SupabaseChatService
import com.example.data.SupabaseProfileService
import com.example.data.UserProfile
import com.example.data.UserSessionManager
import com.example.ui.components.CustomRefreshHeaderItem
import com.example.ui.components.rememberCustomPullRefreshState
import com.example.ui.theme.QivoYellow
import kotlinx.coroutines.launch

object HomeScreenDataStore {
    val cachedProfilesByGender: MutableMap<String, List<UserProfile>> = mutableMapOf()
    val hasMoreByGender: MutableMap<String, Boolean> = mutableMapOf()
    var savedFirstVisibleItemIndex: Int = 0
    var savedFirstVisibleItemScrollOffset: Int = 0
    var selectedTab: String = "Recommend"

    fun resetScrollToTop() {
        savedFirstVisibleItemIndex = 0
        savedFirstVisibleItemScrollOffset = 0
    }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
fun HomeScreen(
    refreshTrigger: Long = 0L,
    listState: androidx.compose.foundation.lazy.LazyListState = rememberLazyListState(),
    userId: String = "",
    userEmail: String = "",
    userGender: String = "",
    userCountry: String = "",
    onOpenUserDetail: (UserProfile) -> Unit = {},
    onOpenConversation: (UserProfile) -> Unit = {},
    onOpenMessageBlast: () -> Unit = {},
    onOpenTaskCenter: () -> Unit = {},
    onOpenGameCenter: () -> Unit = {}
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()

    val profileService = remember { SupabaseProfileService() }
    val chatService = remember { SupabaseChatService() }
    val session = remember(userId) { UserSessionManager.getSession(context) }
    val effectiveUserId = if (userId.isNotBlank()) userId else session?.userId?.trim() ?: ""
    val effectiveEmail = if (userEmail.isNotBlank()) userEmail else session?.email?.trim() ?: ""
    val effectiveCountry = if (userCountry.isNotBlank()) userCountry else session?.country?.trim() ?: ""
    val currentGender = when {
        userGender.equals("female", ignoreCase = true) ||
        userGender.equals("f", ignoreCase = true) ||
        userGender.equals("woman", ignoreCase = true) ||
        userGender.equals("w", ignoreCase = true) -> "Female"

        userGender.equals("male", ignoreCase = true) ||
        userGender.equals("m", ignoreCase = true) ||
        userGender.equals("man", ignoreCase = true) -> "Male"

        session?.gender.equals("female", ignoreCase = true) ||
        session?.gender.equals("f", ignoreCase = true) ||
        session?.gender.equals("woman", ignoreCase = true) ||
        session?.gender.equals("w", ignoreCase = true) -> "Female"

        session?.gender.equals("male", ignoreCase = true) ||
        session?.gender.equals("m", ignoreCase = true) ||
        session?.gender.equals("man", ignoreCase = true) -> "Male"

        else -> ""
    }
    val effectiveGender = currentGender

    // Determine target opposite gender to query and display:
    // If current user is Female -> target is Male
    // If current user is Male -> target is Female
    val targetOppositeGender = remember(effectiveGender) {
        when (effectiveGender) {
            "Female" -> "Male"
            "Male" -> "Female"
            else -> "Male"
        }
    }

    var selectedTab by remember { mutableStateOf(HomeScreenDataStore.selectedTab) } // "Recommend" or "Nearby"
    LaunchedEffect(selectedTab) {
        HomeScreenDataStore.selectedTab = selectedTab
    }

    val pageSize = 15

    // Instant memory / cache retrieval to prevent blank screen or flickering loading states (strictly first 15)
    val initialCachedProfiles = remember(targetOppositeGender) {
        val cached = HomeScreenDataStore.cachedProfilesByGender[targetOppositeGender]
            ?: AppDataCacheManager.getCachedProfilesSync(context, category = "home_$targetOppositeGender")
        cached.take(pageSize)
    }
    var realProfiles by remember(targetOppositeGender) { mutableStateOf<List<UserProfile>>(initialCachedProfiles) }
    var isLoadingProfiles by remember(targetOppositeGender) { mutableStateOf(initialCachedProfiles.isEmpty()) }
    var isLoadingMore by remember { mutableStateOf(false) }
    var hasMoreProfiles by remember(targetOppositeGender) {
        mutableStateOf(HomeScreenDataStore.hasMoreByGender[targetOppositeGender] ?: true)
    }

    // Consistent spacing for buttons & profile cards (both horizontally & vertically)
    val cardSpacing = 8.dp

    // Function to reload profiles (Offline: loads cached data; Online: fetches realtime and saves cache)
    val loadProfiles: (isPullRefresh: Boolean) -> Unit = { isPullRefresh ->
        scope.launch {
            try {
                if (isPullRefresh || realProfiles.isEmpty()) {
                    isLoadingProfiles = realProfiles.isEmpty()
                }
                // 1. Immediately display cached profiles for instant offline/online UI (take initial 15)
                val cached = AppDataCacheManager.getCachedProfiles(context, category = "home_$targetOppositeGender")
                if (cached.isNotEmpty() && realProfiles.isEmpty()) {
                    val initialBatch = cached.take(pageSize)
                    realProfiles = initialBatch
                    HomeScreenDataStore.cachedProfilesByGender[targetOppositeGender] = initialBatch
                    isLoadingProfiles = false
                }

                // 2. If online, fetch fresh realtime profiles from Supabase and cache them
                if (NetworkUtils.isOnline(context)) {
                    val initial = profileService.fetchProfilesPaged(offset = 0, limit = pageSize, targetGender = targetOppositeGender)
                    if (initial.isNotEmpty()) {
                        val first15 = initial.take(pageSize)
                        realProfiles = first15
                        HomeScreenDataStore.cachedProfilesByGender[targetOppositeGender] = first15
                        hasMoreProfiles = initial.size >= pageSize
                        HomeScreenDataStore.hasMoreByGender[targetOppositeGender] = hasMoreProfiles
                        AppDataCacheManager.saveProfilesCache(context, first15, category = "home_$targetOppositeGender")
                    } else {
                        realProfiles = emptyList()
                        HomeScreenDataStore.cachedProfilesByGender[targetOppositeGender] = emptyList()
                        hasMoreProfiles = false
                        HomeScreenDataStore.hasMoreByGender[targetOppositeGender] = false
                        AppDataCacheManager.saveProfilesCache(context, emptyList(), category = "home_$targetOppositeGender")
                    }
                } else {
                    // Offline fallback: if no cached data was found yet
                    if (realProfiles.isEmpty() && cached.isNotEmpty()) {
                        val initialBatch = cached.take(pageSize)
                        realProfiles = initialBatch
                        HomeScreenDataStore.cachedProfilesByGender[targetOppositeGender] = initialBatch
                    }
                }
            } catch (e: Throwable) {
                android.util.Log.e("HomeScreen", "Error loading initial profiles: ${e.message}", e)
                // Fallback to cache on error
                val cached = AppDataCacheManager.getCachedProfiles(context, category = "home_$targetOppositeGender")
                if (cached.isNotEmpty() && realProfiles.isEmpty()) {
                    val initialBatch = cached.take(pageSize)
                    realProfiles = initialBatch
                    HomeScreenDataStore.cachedProfilesByGender[targetOppositeGender] = initialBatch
                }
            } finally {
                isLoadingProfiles = false
            }
        }
    }

    // Function to load the next page
    val loadMoreProfiles: () -> Unit = {
        if (!isLoadingMore && !isLoadingProfiles && hasMoreProfiles) {
            scope.launch {
                try {
                    isLoadingMore = true
                    if (NetworkUtils.isOnline(context)) {
                        val currentCount = realProfiles.size
                        val nextBatch = profileService.fetchProfilesPaged(offset = currentCount, limit = pageSize, targetGender = targetOppositeGender)
                        if (nextBatch.isNotEmpty()) {
                            val existingIds = realProfiles.map { it.id }.toSet()
                            val distinctNew = nextBatch.filter { it.id !in existingIds }
                            if (distinctNew.isNotEmpty()) {
                                val combined = realProfiles + distinctNew
                                realProfiles = combined
                                HomeScreenDataStore.cachedProfilesByGender[targetOppositeGender] = combined
                                AppDataCacheManager.saveProfilesCache(context, combined, category = "home_$targetOppositeGender")
                            }
                            if (nextBatch.size < pageSize || distinctNew.isEmpty()) {
                                hasMoreProfiles = false
                                HomeScreenDataStore.hasMoreByGender[targetOppositeGender] = false
                            }
                        } else {
                            hasMoreProfiles = false
                            HomeScreenDataStore.hasMoreByGender[targetOppositeGender] = false
                        }
                    }
                } catch (e: Throwable) {
                    android.util.Log.e("HomeScreen", "Error loading more profiles: ${e.message}", e)
                } finally {
                    isLoadingMore = false
                }
            }
        }
    }

    // Pull to refresh state
    val pullRefreshState = rememberCustomPullRefreshState(
        onRefresh = {
            HomeScreenDataStore.savedFirstVisibleItemIndex = 0
            HomeScreenDataStore.savedFirstVisibleItemScrollOffset = 0
            if (!NetworkUtils.isOnline(context)) {
                NetworkUtils.showToast(context, "Offline mode: Showing cached data")
                scope.launch {
                    val cached = AppDataCacheManager.getCachedProfiles(context, category = "home_$targetOppositeGender")
                    if (cached.isNotEmpty()) {
                        val initialBatch = cached.take(pageSize)
                        realProfiles = initialBatch
                        HomeScreenDataStore.cachedProfilesByGender[targetOppositeGender] = initialBatch
                    }
                }
            } else {
                try {
                    val fresh = profileService.fetchProfilesPaged(offset = 0, limit = pageSize, targetGender = targetOppositeGender)
                    if (fresh.isNotEmpty()) {
                        val first15 = fresh.take(pageSize)
                        realProfiles = first15
                        hasMoreProfiles = fresh.size >= pageSize
                        HomeScreenDataStore.cachedProfilesByGender[targetOppositeGender] = first15
                        HomeScreenDataStore.hasMoreByGender[targetOppositeGender] = hasMoreProfiles
                        AppDataCacheManager.saveProfilesCache(context, first15, category = "home_$targetOppositeGender")
                    } else {
                        realProfiles = emptyList()
                        HomeScreenDataStore.cachedProfilesByGender[targetOppositeGender] = emptyList()
                        hasMoreProfiles = false
                        HomeScreenDataStore.hasMoreByGender[targetOppositeGender] = false
                        AppDataCacheManager.saveProfilesCache(context, emptyList(), category = "home_$targetOppositeGender")
                    }
                } catch (e: Throwable) {
                    android.util.Log.e("HomeScreen", "Error refreshing profiles: ${e.message}", e)
                }
            }
        }
    )

    val currentUserId = effectiveUserId
    val currentUserEmail = effectiveEmail

    // Automatically claim Welcome Bonus (500 coins) immediately after landing in Home
    LaunchedEffect(currentUserId) {
        if (currentUserId.isNotBlank()) {
            val token = UserSessionManager.getValidAccessToken(context)
            try {
                val (claimedBonus, bonusAmount) = profileService.claimDeviceWelcomeBonus(
                    context = context,
                    userId = currentUserId,
                    accessToken = token
                )
                if (claimedBonus && bonusAmount > 0) {
                    AppToast.show("Welcome to QIVO! 🎉 $bonusAmount Free Welcome Coins credited!", isLong = true)
                }
            } catch (_: Exception) {}
        }
    }

    // Only load initial profiles if we don't have any cached profiles yet!
    // Stops auto refreshing on tab switch or when coming back to HomeScreen.
    LaunchedEffect(targetOppositeGender) {
        if (realProfiles.isEmpty()) {
            loadProfiles(false)
        }
    }

    // Keep user's online presence updated in real-time
    LaunchedEffect(currentUserId) {
        if (currentUserId.isNotEmpty()) {
            try {
                profileService.updateOnlineStatus(currentUserId, true)
                while (true) {
                    kotlinx.coroutines.delay(30_000L)
                    try {
                        profileService.updateOnlineStatus(currentUserId, true)
                    } catch (_: Exception) {}
                }
            } catch (_: Exception) {}
        }
    }

    // Pagination scroll detection: load next page as user scrolls down towards the bottom
    val shouldLoadMore by remember {
        derivedStateOf {
            val totalItems = listState.layoutInfo.totalItemsCount
            val lastVisibleItem = listState.layoutInfo.visibleItemsInfo.lastOrNull()?.index ?: 0
            hasMoreProfiles && !isLoadingMore && !isLoadingProfiles && totalItems > 0 && lastVisibleItem >= totalItems - 2
        }
    }

    LaunchedEffect(shouldLoadMore) {
        if (shouldLoadMore) {
            loadMoreProfiles()
        }
    }

    // Continuous scroll position tracking for the active session
    LaunchedEffect(listState) {
        androidx.compose.runtime.snapshotFlow {
            listState.firstVisibleItemIndex to listState.firstVisibleItemScrollOffset
        }.collect { (index, offset) ->
            if (index > 0 || offset > 0 || HomeScreenDataStore.savedFirstVisibleItemIndex > 0) {
                HomeScreenDataStore.savedFirstVisibleItemIndex = index
                HomeScreenDataStore.savedFirstVisibleItemScrollOffset = offset
            }
        }
    }

    // Handle click / re-selection on Home tab: if not at top -> scroll to top first; if at top -> refresh
    LaunchedEffect(refreshTrigger) {
        if (refreshTrigger > 0L) {
            val isAtTop = listState.firstVisibleItemIndex == 0 && listState.firstVisibleItemScrollOffset <= 10
            if (!isAtTop) {
                HomeScreenDataStore.savedFirstVisibleItemIndex = 0
                HomeScreenDataStore.savedFirstVisibleItemScrollOffset = 0
                listState.animateScrollToItem(0)
            } else {
                pullRefreshState.triggerRefresh(scope)
            }
        }
    }

    // Filter profiles based on selected tab
    // Requirement: male users to ONLY see female accounts and female users to ONLY see male accounts
    val displayedProfiles = remember(selectedTab, realProfiles, effectiveCountry, effectiveGender, currentUserId, currentUserEmail) {
        val cleanMyGender = effectiveGender.trim().lowercase()
        val isMyMale = cleanMyGender.startsWith("m") // male
        val isMyFemale = cleanMyGender.startsWith("f") || cleanMyGender.startsWith("w") // female/woman

        val otherProfiles = realProfiles.filter { user ->
            val uId = user.id.trim()
            val uEmail = user.email.trim()
            val notMe = (currentUserId.isEmpty() || !uId.equals(currentUserId, ignoreCase = true)) &&
                    (currentUserEmail.isEmpty() || !uEmail.equals(currentUserEmail, ignoreCase = true))
            val notBlocked = currentUserId.isEmpty() || !profileService.isUserBlocked(currentUserId, uId, context)
            notMe && notBlocked
        }

        val genderMatched = if (isMyMale) {
            // Male user -> ONLY see female accounts (fallback to all if no opposite gender exists yet)
            val femaleList = otherProfiles.filter { user ->
                val g = user.gender.trim().lowercase()
                g.startsWith("f") || g.startsWith("w")
            }
            if (femaleList.isNotEmpty()) femaleList else otherProfiles
        } else if (isMyFemale) {
            // Female user -> ONLY see male accounts (fallback to all if no opposite gender exists yet)
            val maleList = otherProfiles.filter { user ->
                val g = user.gender.trim().lowercase()
                g.startsWith("m")
            }
            if (maleList.isNotEmpty()) maleList else otherProfiles
        } else {
            otherProfiles
        }

        val filtered = if (selectedTab == "Nearby") {
            val nearbyList = genderMatched.filter { user ->
                user.country.trim().equals(effectiveCountry, ignoreCase = true)
            }
            if (nearbyList.isNotEmpty()) nearbyList else genderMatched
        } else {
            genderMatched
        }

        filtered
    }

    val chunkedProfiles = remember(displayedProfiles) {
        displayedProfiles.chunked(2)
    }

    // Restore scroll position when returning to Home screen during active session
    var hasRestoredScrollPosition by remember { mutableStateOf(false) }
    LaunchedEffect(chunkedProfiles.isNotEmpty()) {
        if (!hasRestoredScrollPosition && chunkedProfiles.isNotEmpty()) {
            val savedIndex = HomeScreenDataStore.savedFirstVisibleItemIndex
            val savedOffset = HomeScreenDataStore.savedFirstVisibleItemScrollOffset
            if (savedIndex > 0 || savedOffset > 0) {
                if (listState.firstVisibleItemIndex != savedIndex || listState.firstVisibleItemScrollOffset != savedOffset) {
                    listState.scrollToItem(savedIndex, savedOffset)
                }
            }
            hasRestoredScrollPosition = true
        }
    }

    // Dialog state to send a quick chat message to a user
    var targetChatUser by remember { mutableStateOf<UserProfile?>(null) }
    var chatMessageText by remember { mutableStateOf("") }
    var isSendingMessage by remember { mutableStateOf(false) }

    // Movable Floating Game Button offset
    var fabOffsetX by remember { mutableStateOf(0f) }
    var fabOffsetY by remember { mutableStateOf(0f) }

    val colors = com.example.ui.theme.AppTheme.colors
    val isDark = colors.isDark

    val isScrolled by remember {
        derivedStateOf {
            listState.firstVisibleItemIndex > 0 || listState.firstVisibleItemScrollOffset > 10
        }
    }

    BoxWithConstraints(
        modifier = Modifier
            .fillMaxSize()
            .background(colors.screenBg)
            .testTag("home_screen_root")
    ) {
        val density = LocalDensity.current
        val maxW = constraints.maxWidth.toFloat()
        val maxH = constraints.maxHeight.toFloat()
        val btnW = with(density) { 84.dp.toPx() }
        val btnH = with(density) { 36.dp.toPx() }
        val padEnd = with(density) { 16.dp.toPx() }
        val padBottom = with(density) { 16.dp.toPx() }
        val margin = with(density) { 8.dp.toPx() }
        val topInset = with(density) { 70.dp.toPx() }

        val minOffsetX = -(maxW - btnW - padEnd - margin)
        val maxOffsetX = padEnd - margin
        val minOffsetY = -(maxH - btnH - padBottom - topInset)
        val maxOffsetY = padBottom - margin
        val statusBarTopInset = WindowInsets.statusBars.asPaddingValues().calculateTopPadding()

        Column(
            modifier = Modifier.fillMaxSize()
        ) {
            // Fixed Top Status Bar Overlay (Seamlessly matches the exact top color of the signature sunset header)
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(statusBarTopInset)
                    .background(
                        if (isDark) {
                            Color(0xFF1E0F07)
                        } else {
                            Color(0xFFFF9E79) // Exact match with splash screen faded sunset coral peach
                        }
                    )
            )

            LazyColumn(
                state = listState,
                modifier = Modifier
                    .fillMaxWidth()
                    .weight(1f)
                    .nestedScroll(pullRefreshState.getNestedScrollConnection(scope)),
                contentPadding = PaddingValues(bottom = 88.dp)
            ) {
                // 1. TOP ACTION BUTTONS (Clean, elegant header with background extending 3/4 way down the cards)
                item(key = "top_buttons_row") {
                    Box(
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        // Top signature faded sunset amber & peach aesthetic banner extending 3/4 way behind the buttons
                        Box(
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(96.dp)
                                .clipToBounds()
                                .background(
                                    if (isDark) {
                                        Brush.verticalGradient(
                                            listOf(
                                                Color(0xFF1E0F07),
                                                Color(0xFF140B05)
                                            )
                                        )
                                    } else {
                                        Brush.verticalGradient(
                                            listOf(
                                                Color(0xFFFF9E79), // Soft Warm Coral Peach
                                                Color(0xFFFFAE8D), // Pale Amber Orange
                                                Color(0xFFFFBEA2), // Soft Apricot
                                                Color(0xFFFFCFAF)  // Luminous Pale Sunset
                                            )
                                        )
                                    }
                                )
                        ) {
                            QivoBackgroundStamp(isDark = isDark)
                        }

                        Column(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(top = 13.dp, bottom = 11.dp)
                        ) {
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(horizontal = 14.dp),
                                horizontalArrangement = Arrangement.spacedBy(cardSpacing)
                            ) {
                                // Card 1: Message Blast (Luxury Sunset Amber / Coral Glass Card)
                                Card(
                                    modifier = Modifier
                                        .weight(1f)
                                        .height(110.dp)
                                        .clickable { onOpenMessageBlast() },
                                    shape = RoundedCornerShape(14.dp),
                                    colors = CardDefaults.cardColors(
                                        containerColor = if (isDark) Color(0xFF38140B) else Color(0xFFFFF3EE)
                                    ),
                                    elevation = CardDefaults.cardElevation(defaultElevation = if (isDark) 4.dp else 2.5.dp)
                                ) {
                                    Box(
                                        modifier = Modifier
                                            .fillMaxSize()
                                            .background(
                                                Brush.verticalGradient(
                                                    colors = if (isDark) listOf(Color(0xFF6B1D0E), Color(0xFF3A0D05))
                                                    else listOf(Color(0xFFFFFAF8), Color(0xFFFFECE5))
                                                )
                                            )
                                            .padding(7.dp)
                                    ) {
                                        Column(modifier = Modifier.align(Alignment.TopStart).padding(start = 2.dp, top = 2.dp)) {
                                            Text(
                                                text = "Message\nBlast",
                                                color = if (isDark) Color(0xFFFFCCBC) else Color(0xFFD83818),
                                                fontSize = 12.sp,
                                                fontWeight = FontWeight.ExtraBold,
                                                lineHeight = 15.sp
                                            )
                                        }

                                        Box(
                                            modifier = Modifier.align(Alignment.BottomEnd)
                                        ) {
                                            MessageBlast3DIcon(size = 71.dp)
                                        }
                                    }
                                }

                                // Card 2: Game Center (Luxury 24k Gold Glass Card)
                                Card(
                                    modifier = Modifier
                                        .weight(1f)
                                        .height(110.dp)
                                        .clickable {
                                            if (NetworkUtils.requireOnline(context, "No internet connection. Cannot play games while offline.")) {
                                                onOpenGameCenter()
                                            }
                                        },
                                    shape = RoundedCornerShape(14.dp),
                                    colors = CardDefaults.cardColors(
                                        containerColor = if (isDark) Color(0xFF362805) else Color(0xFFFFFBEA)
                                    ),
                                    elevation = CardDefaults.cardElevation(defaultElevation = if (isDark) 4.dp else 2.5.dp)
                                ) {
                                    Box(
                                        modifier = Modifier
                                            .fillMaxSize()
                                            .background(
                                                Brush.verticalGradient(
                                                    colors = if (isDark) listOf(Color(0xFF684B05), Color(0xFF382602))
                                                    else listOf(Color(0xFFFFFDF5), Color(0xFFFFF8E1))
                                                )
                                            )
                                            .padding(7.dp)
                                    ) {
                                        Column(modifier = Modifier.align(Alignment.TopStart).padding(start = 2.dp, top = 2.dp)) {
                                            Text(
                                                text = "Game\nCenter",
                                                color = if (isDark) Color(0xFFFFECB3) else Color(0xFFB77900),
                                                fontSize = 12.sp,
                                                fontWeight = FontWeight.ExtraBold,
                                                lineHeight = 15.sp
                                            )
                                        }

                                        Box(
                                            modifier = Modifier.align(Alignment.BottomEnd)
                                        ) {
                                            GameCenter3DIcon(size = 71.dp)
                                        }
                                    }
                                }

                                // Card 3: Tasks Center (Radiant Electric Purple Glass Card)
                                Card(
                                    modifier = Modifier
                                        .weight(1f)
                                        .height(110.dp)
                                        .clickable { onOpenTaskCenter() },
                                    shape = RoundedCornerShape(14.dp),
                                    colors = CardDefaults.cardColors(
                                        containerColor = if (isDark) Color(0xFF28113D) else Color(0xFFFBF6FF)
                                    ),
                                    elevation = CardDefaults.cardElevation(defaultElevation = if (isDark) 4.dp else 2.5.dp)
                                ) {
                                    Box(
                                        modifier = Modifier
                                            .fillMaxSize()
                                            .background(
                                                Brush.verticalGradient(
                                                    colors = if (isDark) listOf(Color(0xFF561A82), Color(0xFF2A0B44))
                                                    else listOf(Color(0xFFFAF4FF), Color(0xFFF2E5FF))
                                                )
                                            )
                                            .padding(7.dp)
                                    ) {
                                        Column(modifier = Modifier.align(Alignment.TopStart).padding(start = 2.dp, top = 2.dp)) {
                                            Text(
                                                text = "Tasks\nCenter",
                                                color = if (isDark) Color(0xFFF3E5F5) else Color(0xFF7C3AED),
                                                fontSize = 12.sp,
                                                fontWeight = FontWeight.ExtraBold,
                                                lineHeight = 15.sp
                                            )
                                        }

                                        Box(
                                            modifier = Modifier.align(Alignment.BottomEnd)
                                        ) {
                                            TasksCenter3DIcon(size = 71.dp)
                                        }
                                    }
                                }
                            }
                        }
                    }
                }

                // 2. STICKY HEADER: RECOMMEND & NEARBY TABS (Retains clean white background with crisp tabs)
                stickyHeader(key = "sticky_recommend_nearby_header") {
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .shadow(
                                elevation = if (isScrolled) 3.dp else 0.dp,
                                spotColor = Color.Black.copy(alpha = 0.08f)
                            )
                            .background(colors.screenBg)
                    ) {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(start = 16.dp, end = 16.dp, top = 8.dp, bottom = 6.dp),
                            horizontalArrangement = Arrangement.Start,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            // Recommend Tab
                            Column(
                                horizontalAlignment = Alignment.CenterHorizontally,
                                modifier = Modifier
                                    .clickable { selectedTab = "Recommend" }
                                    .padding(end = 20.dp)
                            ) {
                                Text(
                                    text = "Recommend",
                                    fontSize = 20.sp,
                                    fontWeight = if (selectedTab == "Recommend") FontWeight.Bold else FontWeight.Medium,
                                    color = if (selectedTab == "Recommend") colors.textPrimary else colors.textSecondary
                                )
                                Spacer(modifier = Modifier.height(4.dp))
                                if (selectedTab == "Recommend") {
                                    Box(
                                        modifier = Modifier
                                            .width(32.dp)
                                            .height(4.dp)
                                            .clip(CircleShape)
                                            .background(QivoYellow)
                                    )
                                } else {
                                    Spacer(modifier = Modifier.height(4.dp))
                                }
                            }

                            // Nearby Tab
                            Column(
                                horizontalAlignment = Alignment.CenterHorizontally,
                                modifier = Modifier
                                    .clickable { selectedTab = "Nearby" }
                            ) {
                                Text(
                                    text = "Nearby",
                                    fontSize = 20.sp,
                                    fontWeight = if (selectedTab == "Nearby") FontWeight.Bold else FontWeight.Medium,
                                    color = if (selectedTab == "Nearby") colors.textPrimary else colors.textSecondary
                                )
                                Spacer(modifier = Modifier.height(4.dp))
                                if (selectedTab == "Nearby") {
                                    Box(
                                        modifier = Modifier
                                            .width(32.dp)
                                            .height(4.dp)
                                            .clip(CircleShape)
                                            .background(QivoYellow)
                                    )
                                } else {
                                    Spacer(modifier = Modifier.height(4.dp))
                                }
                            }
                        }
                    }
                }

                // 3. PULL TO REFRESH INDICATOR (Positioned right below Recommend & Nearby tabs)
                item(key = "pull_refresh_indicator") {
                    CustomRefreshHeaderItem(pullRefreshState = pullRefreshState)
                }

            // 4. REAL SUPABASE PROFILES (Scroll underneath sticky header with spacious padding)
            if (isLoadingProfiles && realProfiles.isEmpty()) {
                item(key = "loading_profiles") {
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(200.dp),
                        contentAlignment = Alignment.Center
                    ) {
                        Column(horizontalAlignment = Alignment.CenterHorizontally) {
                            AppLoadingSpinner(size = 36.dp)
                            Spacer(modifier = Modifier.height(12.dp))
                            Text("Loading profiles...", color = colors.textSecondary, fontSize = 14.sp)
                        }
                    }
                }
            } else if (displayedProfiles.isEmpty()) {

                item(key = "empty_profiles") {
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(24.dp),
                        contentAlignment = Alignment.Center
                    ) {
                        Column(horizontalAlignment = Alignment.CenterHorizontally) {
                            Icon(
                                imageVector = Icons.Default.Person,
                                contentDescription = "No Profiles",
                                tint = colors.textSecondary,
                                modifier = Modifier.size(48.dp)
                            )
                            Spacer(modifier = Modifier.height(12.dp))
                            Text(
                                text = if (selectedTab == "Nearby") "No nearby profiles found in $effectiveCountry yet." else "No profiles found yet.",
                                fontSize = 16.sp,
                                fontWeight = FontWeight.Bold,
                                color = colors.textPrimary,
                                textAlign = TextAlign.Center
                            )
                            Spacer(modifier = Modifier.height(4.dp))
                            Text(
                                text = if (selectedTab == "Nearby") "Switch to Recommend to view users from all countries!" else "Pull down to refresh or check back soon!",
                                fontSize = 13.sp,
                                color = colors.textSecondary,
                                textAlign = TextAlign.Center
                            )
                        }
                    }
                }
            } else {
                // Generous vertical spacing above the profile cards
                item(key = "profile_cards_top_spacer") {
                    Spacer(modifier = Modifier.height(10.dp))
                }

                // Profile items chunked in pairs of 2, with balanced vertical and horizontal spacing
                items(
                    items = chunkedProfiles,
                    key = { pair -> pair.joinToString("-") { it.id } }
                ) { rowProfiles ->
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 14.dp, vertical = 5.dp),
                        horizontalArrangement = Arrangement.spacedBy(cardSpacing)
                    ) {
                        rowProfiles.forEach { user ->
                            RealProfileCard(
                                user = user,
                                modifier = Modifier.weight(1f),
                                onCardClick = {
                                    if (profileService.isUserBlockedByMe(currentUserId, user.id, context)) {
                                        AppToast.show("You blocked this user.")
                                        return@RealProfileCard
                                    }
                                    if (profileService.isUserBlocked(currentUserId, user.id, context)) {
                                        AppToast.show("You have been blocked.")
                                        return@RealProfileCard
                                    }
                                    onOpenUserDetail(user)
                                },
                                onChatClick = {
                                    if (profileService.isUserBlockedByMe(currentUserId, user.id, context)) {
                                        AppToast.show("You blocked this user.")
                                        return@RealProfileCard
                                    }
                                    if (profileService.isUserBlocked(currentUserId, user.id, context)) {
                                        AppToast.show("You have been blocked.")
                                        return@RealProfileCard
                                    }
                                    onOpenConversation(user)
                                }
                            )
                        }
                        if (rowProfiles.size == 1) {
                            Spacer(modifier = Modifier.weight(1f))
                        }
                    }
                }

                // Generous vertical spacing below the profile cards
                item(key = "profile_cards_bottom_spacer") {
                    Spacer(modifier = Modifier.height(14.dp))
                }

                // Loading more profiles indicator at the bottom
                if (isLoadingMore) {
                    item(key = "loading_more_profiles_indicator") {
                        Box(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(16.dp),
                            contentAlignment = Alignment.Center
                        ) {
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.Center
                            ) {
                                AppLoadingSpinner(size = 20.dp)
                                Spacer(modifier = Modifier.width(8.dp))
                                Text(
                                    text = "Loading more profiles...",
                                    fontSize = 13.sp,
                                    color = colors.textSecondary
                                )
                            }
                        }
                    }
                }


                // End of profiles indicator when all profiles are loaded
                if (!hasMoreProfiles && displayedProfiles.isNotEmpty()) {
                    item(key = "end_of_profiles_indicator") {
                        Box(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(top = 16.dp, bottom = 24.dp),
                            contentAlignment = Alignment.Center
                        ) {
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.Center
                            ) {
                                Box(
                                    modifier = Modifier
                                        .width(36.dp)
                                        .height(1.dp)
                                        .background(colors.cardBorder)
                                )
                                Text(
                                    text = "  the end.  ",
                                    fontSize = 12.sp,
                                    color = colors.textSecondary,
                                    fontWeight = FontWeight.Medium
                                )
                                Box(
                                    modifier = Modifier
                                        .width(36.dp)
                                        .height(1.dp)
                                        .background(colors.cardBorder)
                                )
                            }
                        }
                    }
                }
            }
        }
    }
    }

    // Direct Chat Message Dialog
    targetChatUser?.let { target ->
        Dialog(onDismissRequest = { targetChatUser = null }) {
            Surface(
                shape = RoundedCornerShape(24.dp),
                color = colors.cardBg,
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(16.dp)
            ) {
                Column(
                    modifier = Modifier.padding(20.dp),
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    Text(
                        text = "Send Message to ${target.name}",
                        fontSize = 18.sp,
                        fontWeight = FontWeight.Bold,
                        color = colors.textPrimary
                    )

                    Spacer(modifier = Modifier.height(16.dp))

                    OutlinedTextField(
                        value = chatMessageText,
                        onValueChange = { chatMessageText = it },
                        placeholder = { Text("Type your message...", color = colors.textSecondary) },
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(100.dp),
                        shape = RoundedCornerShape(16.dp),
                        colors = OutlinedTextFieldDefaults.colors(
                            focusedTextColor = colors.textPrimary,
                            unfocusedTextColor = colors.textPrimary,
                            focusedBorderColor = QivoYellow,
                            unfocusedBorderColor = colors.cardBorder
                        )
                    )

                    Spacer(modifier = Modifier.height(20.dp))

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.End
                    ) {
                        TextButton(onClick = { targetChatUser = null }) {
                            Text("Cancel", color = colors.textSecondary)
                        }

                        Spacer(modifier = Modifier.width(8.dp))

                        Surface(
                            onClick = {
                                if (!NetworkUtils.requireOnline(context)) {
                                    return@Surface
                                }
                                if (chatMessageText.isBlank()) {
                                    AppToast.show("Message cannot be empty")
                                    return@Surface
                                }
                                val senderId = session?.userId ?: "guest_user"
                                val senderName = session?.name ?: "QIVO User"

                                if (profileService.isUserBlockedByMe(senderId, target.id, context)) {
                                    AppToast.show("You blocked this user.")
                                    return@Surface
                                }
                                if (profileService.isUserBlocked(senderId, target.id, context) ||
                                    profileService.isUserBlocked(target.id, senderId, context)) {
                                    AppToast.show("You have been blocked.")
                                    return@Surface
                                }

                                isSendingMessage = true
                                scope.launch {
                                    val success = chatService.sendMessage(
                                        senderId = senderId,
                                        senderName = senderName,
                                        senderAvatar = session?.avatarUrl ?: "",
                                        receiverId = target.id,
                                        receiverName = target.name,
                                        messageText = chatMessageText,
                                        context = context
                                    )
                                    isSendingMessage = false
                                    if (success) {
                                        AppToast.show("Message sent to ${target.name}!")
                                        chatMessageText = ""
                                        targetChatUser = null
                                    } else {
                                        AppToast.show("Failed to send message")
                                    }
                                }
                            },
                            shape = CircleShape,
                            color = Color(0xFFFF9E79),
                            enabled = !isSendingMessage
                        ) {
                            Box(
                                modifier = Modifier
                                    .background(
                                        Brush.horizontalGradient(
                                            listOf(
                                                Color(0xFFFF9E79), // Soft Warm Coral Peach
                                                Color(0xFFFFAE8D), // Pale Amber Orange
                                                Color(0xFFFF8A65)  // Rich Warm Sunset Coral
                                            )
                                        )
                                    )
                                    .padding(horizontal = 20.dp, vertical = 10.dp),
                                contentAlignment = Alignment.Center
                            ) {
                                if (isSendingMessage) {
                                    AppLoadingSpinner(size = 18.dp)
                                } else {
                                    Text("Send", color = Color.White, fontWeight = FontWeight.Bold)
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun RealProfileCard(
    user: UserProfile,
    modifier: Modifier = Modifier,
    onCardClick: () -> Unit,
    onChatClick: () -> Unit
) {
    val userAge = remember(user.birthDate) {
        if (user.birthDate.isBlank()) "20"
        else {
            try {
                val yearStr = user.birthDate.split("-", "/", " ", ".").firstOrNull { it.length == 4 }
                val birthYear = yearStr?.toIntOrNull() ?: 2005
                (2026 - birthYear).coerceIn(18, 99).toString()
            } catch (e: Exception) {
                "20"
            }
        }
    }

    val colors = com.example.ui.theme.AppTheme.colors

    Card(
        modifier = modifier
            .height(200.dp)
            .clickable { onCardClick() },
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(containerColor = colors.cardBg),
        border = null,
        elevation = CardDefaults.cardElevation(defaultElevation = 2.dp)
    ) {
        Box(modifier = Modifier.fillMaxSize()) {
            // Profile image or doll mascot - rendered with RoundedCornerShape to display photos in full without clipping
            AvatarHelper.UserAvatarImage(
                avatarUrl = user.avatarUrl,
                userId = user.id,
                gender = user.gender,
                numericId = user.numericId,
                showFrame = false,
                contentDescription = user.name,
                modifier = Modifier.fillMaxSize(),
                contentScale = ContentScale.Crop,
                shape = RoundedCornerShape(16.dp)
            )


            // Top Left Online Green Dot (Only when user is online)
            if (user.isOnline) {
                Box(
                    modifier = Modifier
                        .padding(top = 10.dp, start = 10.dp)
                        .size(11.dp)
                        .clip(CircleShape)
                        .background(Color(0xFF76FF03))
                        .align(Alignment.TopStart)
                )
            }

            // Top Right Custom Compact 3D CHAT Badge Button (Subtle & proportional accent)
            Chat3DBadgeButton(
                onClick = onChatClick,
                modifier = Modifier
                    .padding(top = 8.dp, end = 8.dp)
                    .align(Alignment.TopEnd)
            )

            // Bottom Content Overlay with smooth vertical gradient scrim & breathing room
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .align(Alignment.BottomStart)
                    .background(
                        Brush.verticalGradient(
                            colors = listOf(
                                Color.Transparent,
                                Color.Black.copy(alpha = 0.35f),
                                Color.Black.copy(alpha = 0.78f)
                            )
                        )
                    )
                    .padding(horizontal = 10.dp, vertical = 10.dp)
            ) {
                Column {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text(
                            text = user.name,
                            fontSize = 16.sp,
                            fontWeight = FontWeight.Bold,
                            color = Color.White
                        )
                        Spacer(modifier = Modifier.width(4.dp))
                        Icon(
                            imageVector = Icons.Default.CheckCircle,
                            contentDescription = "Verified",
                            tint = Color(0xFFFF9800),
                            modifier = Modifier.size(14.dp)
                        )
                    }

                    Spacer(modifier = Modifier.height(5.dp))

                    Row(
                        horizontalArrangement = Arrangement.spacedBy(5.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        val isFemale = user.gender.equals("Female", ignoreCase = true) || user.gender.equals("F", ignoreCase = true)
                        val genderBgColor = if (isFemale) Color(0xFFF8A4EC) else Color(0xFF72C2F8)

                        Surface(
                            shape = RoundedCornerShape(6.dp),
                            color = genderBgColor
                        ) {
                            Row(
                                modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.5.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Icon(
                                    imageVector = if (isFemale) Icons.Default.Female else Icons.Default.Male,
                                    contentDescription = "Gender",
                                    tint = Color.Black,
                                    modifier = Modifier.size(11.dp)
                                )
                                Spacer(modifier = Modifier.width(2.dp))
                                Text(
                                    text = userAge,
                                    color = Color.Black,
                                    fontSize = 10.sp,
                                    fontWeight = FontWeight.Bold
                                )
                            }
                        }

                        Surface(
                            shape = RoundedCornerShape(6.dp),
                            color = Color(0xFFC6FF00)
                        ) {
                            Text(
                                text = if (user.country.isNotBlank()) user.country else "Global",
                                color = Color.Black,
                                fontSize = 10.sp,
                                fontWeight = FontWeight.Bold,
                                modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.5.dp)
                            )
                        }
                    }
                }
            }
        }
    }
}

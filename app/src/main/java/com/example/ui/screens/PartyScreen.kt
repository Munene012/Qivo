package com.example.ui.screens

import android.widget.Toast
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.GridItemSpan
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.lazy.grid.rememberLazyGridState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Group
import androidx.compose.material.icons.filled.Mic
import androidx.compose.material.icons.filled.MusicNote
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.clipToBounds
import com.example.data.AppDataCacheManager
import com.example.data.NetworkUtils
import com.example.data.PartyRealtimeRelayManager
import com.example.ui.components.AnimatedFloatingMusicNotes
import com.example.ui.components.QivoBackgroundStamp
import com.example.ui.theme.QivoOrange
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil.compose.AsyncImage
import com.example.data.AppAnalyticsService
import com.example.data.PartyMusicManager
import com.example.data.PartyRoom
import com.example.data.PartyRoomSessionManager
import com.example.data.SupabasePartyService
import com.example.ui.theme.AppTheme
import com.example.ui.theme.QivoOrange
import com.example.ui.theme.QivoYellow
import kotlinx.coroutines.launch

object PartyScreenDataStore {
    val cachedRoomsByCategory: MutableMap<String, List<PartyRoom>> = mutableMapOf()
    val hasMoreByCategory: MutableMap<String, Boolean> = mutableMapOf()
    var selectedCategory: String = "All"
}

@Composable
fun PartyScreen(
    gridState: androidx.compose.foundation.lazy.grid.LazyGridState = rememberLazyGridState(),
    onOpenPartyRoom: (PartyRoom) -> Unit = {},
    onOpenCreateRoom: () -> Unit = {}
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val partyService = remember { SupabasePartyService() }
    val colors = AppTheme.colors

    var selectedCategory by remember { mutableStateOf(PartyScreenDataStore.selectedCategory) }
    LaunchedEffect(selectedCategory) {
        PartyScreenDataStore.selectedCategory = selectedCategory
    }

    val initialCachedRooms = remember(selectedCategory) {
        PartyScreenDataStore.cachedRoomsByCategory[selectedCategory]
            ?: AppDataCacheManager.getCachedPartyRoomsSync(context)
    }

    var roomsList by remember(selectedCategory) { mutableStateOf<List<PartyRoom>>(initialCachedRooms) }
    var isLoading by remember(selectedCategory) { mutableStateOf(initialCachedRooms.isEmpty()) }
    var isLoadingMore by remember { mutableStateOf(false) }
    var hasMoreRooms by remember(selectedCategory) {
        mutableStateOf(PartyScreenDataStore.hasMoreByCategory[selectedCategory] ?: true)
    }

    val pageSize = 20
    val categories = listOf("All", "Chat", "Music", "Gaming", "Dating", "Karaoke")

    fun loadRooms(forceRefresh: Boolean = false) {
        if (forceRefresh || roomsList.isEmpty()) {
            isLoading = roomsList.isEmpty()
        }
        hasMoreRooms = true
        scope.launch {
            try {
                // 1. Immediately display cached party rooms if list is empty
                val cached = AppDataCacheManager.getCachedPartyRooms(context)
                if (cached.isNotEmpty() && roomsList.isEmpty()) {
                    roomsList = cached
                    PartyScreenDataStore.cachedRoomsByCategory[selectedCategory] = cached
                    isLoading = false
                }

                // 2. If online, fetch realtime rooms and save cache
                if (NetworkUtils.isOnline(context)) {
                    val initial = partyService.fetchPartyRooms(category = selectedCategory, offset = 0, limit = pageSize)
                    roomsList = initial
                    hasMoreRooms = initial.size >= pageSize
                    PartyScreenDataStore.cachedRoomsByCategory[selectedCategory] = initial
                    PartyScreenDataStore.hasMoreByCategory[selectedCategory] = hasMoreRooms
                    AppDataCacheManager.savePartyRoomsCache(context, initial)
                } else if (roomsList.isEmpty() && cached.isNotEmpty()) {
                    roomsList = cached
                    PartyScreenDataStore.cachedRoomsByCategory[selectedCategory] = cached
                }
            } catch (e: Exception) {
                val cached = AppDataCacheManager.getCachedPartyRooms(context)
                if (cached.isNotEmpty() && roomsList.isEmpty()) {
                    roomsList = cached
                    PartyScreenDataStore.cachedRoomsByCategory[selectedCategory] = cached
                }
            } finally {
                isLoading = false
            }
        }
    }

    // Listen for room closed / deleted events in real-time
    LaunchedEffect(Unit) {
        PartyRealtimeRelayManager.roomClosedEvents.collect { closedEvent ->
            if (closedEvent.roomId.isNotBlank()) {
                roomsList = roomsList.filter { it.id != closedEvent.roomId }
                PartyScreenDataStore.cachedRoomsByCategory[selectedCategory] = roomsList
                AppDataCacheManager.removePartyRoomFromCache(context, closedEvent.roomId)
            }
        }
    }

    fun loadMoreRooms() {
        if (!isLoadingMore && !isLoading && hasMoreRooms) {
            scope.launch {
                try {
                    isLoadingMore = true
                    if (NetworkUtils.isOnline(context)) {
                        val nextBatch = partyService.fetchPartyRooms(
                            category = selectedCategory,
                            offset = roomsList.size,
                            limit = pageSize
                        )
                        if (nextBatch.isNotEmpty()) {
                            val existingIds = roomsList.map { it.id }.toSet()
                            val distinctNew = nextBatch.filter { it.id !in existingIds }
                            roomsList = roomsList + distinctNew
                            PartyScreenDataStore.cachedRoomsByCategory[selectedCategory] = roomsList
                            AppDataCacheManager.savePartyRoomsCache(context, roomsList)
                        }
                        if (nextBatch.size < pageSize) {
                            hasMoreRooms = false
                            PartyScreenDataStore.hasMoreByCategory[selectedCategory] = false
                        }
                    }
                } catch (e: Exception) {
                    e.printStackTrace()
                } finally {
                    isLoadingMore = false
                }
            }
        }
    }

    LaunchedEffect(selectedCategory) {
        if (roomsList.isEmpty()) {
            loadRooms(forceRefresh = false)
        }
    }

    // Detect when user scrolls near the bottom to trigger next page load
    val shouldLoadMore by remember {
        derivedStateOf {
            val totalItems = gridState.layoutInfo.totalItemsCount
            val lastVisibleItem = gridState.layoutInfo.visibleItemsInfo.lastOrNull()?.index ?: 0
            hasMoreRooms && !isLoadingMore && !isLoading && totalItems > 0 && lastVisibleItem >= totalItems - 2
        }
    }

    LaunchedEffect(shouldLoadMore) {
        if (shouldLoadMore) {
            loadMoreRooms()
        }
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(colors.screenBg)
            .testTag("party_screen_root")
    ) {
        // 1. Fixed Top Header with sunset amber gradient
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .clipToBounds()
                .background(
                    if (colors.isDark) {
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
                isDark = colors.isDark
            )

            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .statusBarsPadding()
                    .padding(horizontal = 16.dp, vertical = 12.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column {
                    Text(
                        text = "Voice Party",
                        fontSize = 24.sp,
                        fontWeight = FontWeight.ExtraBold,
                        color = Color.White
                    )
                    Text(
                        text = "Enjoy chatting and express yourself freely",
                        fontSize = 12.sp,
                        color = Color.White.copy(alpha = 0.9f)
                    )
                }

                // Create Party Room Button
                Button(
                    onClick = onOpenCreateRoom,
                    colors = ButtonDefaults.buttonColors(containerColor = Color.White),
                    shape = RoundedCornerShape(20.dp),
                    contentPadding = PaddingValues(horizontal = 14.dp, vertical = 8.dp),
                    modifier = Modifier.testTag("create_party_room_button")
                ) {
                    Icon(Icons.Default.Add, contentDescription = null, modifier = Modifier.size(18.dp), tint = Color(0xFFFF6D00))
                    Spacer(modifier = Modifier.width(4.dp))
                    Text("Create", fontSize = 13.sp, fontWeight = FontWeight.Bold, color = Color(0xFFFF6D00))
                }
            }
        }

        // 2. Persistent Category Filter Pills (Always visible across all tabs and empty states)
        Surface(
            color = colors.screenBg,
            modifier = Modifier.fillMaxWidth()
        ) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .horizontalScroll(rememberScrollState())
                    .padding(horizontal = 16.dp, vertical = 10.dp),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                categories.forEach { cat ->
                    val isSelected = selectedCategory.equals(cat, ignoreCase = true)
                    Surface(
                        modifier = Modifier
                            .clip(RoundedCornerShape(20.dp))
                            .clickable { selectedCategory = cat }
                            .testTag("filter_$cat"),
                        color = if (isSelected) QivoOrange else if (colors.isDark) Color(0xFF23201E) else Color(0xFFF1F1F1),
                        shape = RoundedCornerShape(20.dp),
                        border = if (!isSelected) androidx.compose.foundation.BorderStroke(1.dp, colors.divider) else null
                    ) {
                        Text(
                            text = cat,
                            fontSize = 13.sp,
                            fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Medium,
                            color = if (isSelected) Color.White else colors.textSecondary,
                            modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp)
                        )
                    }
                }
            }
        }

        // 3. Main Content: Grid of Party Rooms OR Clean Empty State
        if (isLoading && roomsList.isEmpty()) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .weight(1f),
                contentAlignment = Alignment.Center
            ) {
                CircularProgressIndicator(color = QivoOrange)
            }
        } else if (roomsList.isEmpty()) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .weight(1f)
                    .padding(horizontal = 24.dp),
                contentAlignment = Alignment.Center
            ) {
                Column(
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.Center
                ) {
                    Box(
                        modifier = Modifier
                            .size(72.dp)
                            .clip(CircleShape)
                            .background(QivoOrange.copy(alpha = 0.12f)),
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(
                            imageVector = Icons.Default.Mic,
                            contentDescription = null,
                            tint = QivoOrange,
                            modifier = Modifier.size(36.dp)
                        )
                    }
                    Spacer(modifier = Modifier.height(14.dp))
                    Text(
                        text = if (selectedCategory == "All") "No active party rooms right now" else "No $selectedCategory party rooms yet",
                        color = colors.textPrimary,
                        fontSize = 16.sp,
                        fontWeight = FontWeight.Bold
                    )
                    Spacer(modifier = Modifier.height(6.dp))
                    Text(
                        text = "Be the first to create a live voice party room in this category!",
                        color = colors.textSecondary,
                        fontSize = 13.sp,
                        textAlign = androidx.compose.ui.text.style.TextAlign.Center
                    )
                    Spacer(modifier = Modifier.height(18.dp))
                    Button(
                        onClick = onOpenCreateRoom,
                        colors = ButtonDefaults.buttonColors(containerColor = QivoOrange),
                        shape = RoundedCornerShape(16.dp),
                        contentPadding = PaddingValues(horizontal = 20.dp, vertical = 12.dp)
                    ) {
                        Icon(Icons.Default.Add, contentDescription = null, modifier = Modifier.size(18.dp))
                        Spacer(modifier = Modifier.width(6.dp))
                        Text("Create $selectedCategory Room 🎉", fontWeight = FontWeight.Bold)
                    }
                }
            }
        } else {
            LazyVerticalGrid(
                state = gridState,
                columns = GridCells.Fixed(2),
                horizontalArrangement = Arrangement.spacedBy(12.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp),
                contentPadding = PaddingValues(
                    start = 16.dp,
                    end = 16.dp,
                    top = 6.dp,
                    bottom = 90.dp
                ),
                modifier = Modifier
                    .fillMaxSize()
                    .weight(1f)
                    .testTag("party_rooms_grid")
            ) {
                items(roomsList, key = { it.id }) { room ->
                    PartyRoomCard(
                        room = room,
                        onClick = {
                            AppAnalyticsService.logJoinRoom(
                                roomId = room.id,
                                title = room.name,
                                category = "party_room"
                            )
                            onOpenPartyRoom(room)
                        }
                    )
                }

                if (isLoadingMore) {
                    item(
                        span = { GridItemSpan(2) },
                        key = "party_loading_more_indicator"
                    ) {
                        Box(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(vertical = 16.dp),
                            contentAlignment = Alignment.Center
                        ) {
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.Center
                            ) {
                                CircularProgressIndicator(
                                    modifier = Modifier.size(18.dp),
                                    strokeWidth = 2.dp,
                                    color = QivoOrange
                                )
                                Spacer(modifier = Modifier.width(8.dp))
                                Text(
                                    text = "Loading more rooms...",
                                    fontSize = 13.sp,
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
fun PartyRoomCard(
    room: PartyRoom,
    onClick: () -> Unit
) {
    val activeRoom by PartyRoomSessionManager.activeRoom.collectAsState()
    val isMusicPlaying by PartyMusicManager.isPlaying.collectAsState()
    val isMusicPlayingInThisRoom = isMusicPlaying && (activeRoom?.id == room.id || (activeRoom == null && room.category.equals("Music", ignoreCase = true)))

    Card(
        modifier = Modifier
            .fillMaxWidth()
            .height(200.dp)
            .clip(RoundedCornerShape(18.dp))
            .clickable { onClick() }
            .testTag("room_card_${room.id}"),
        shape = RoundedCornerShape(18.dp),
        colors = CardDefaults.cardColors(containerColor = Color(0xFF1E1C2A))
    ) {
        Box(modifier = Modifier.fillMaxSize()) {
            // Room Cover / Wallpaper
            AsyncImage(
                model = room.coverUrl.ifBlank { room.bgUrl },
                contentDescription = room.name,
                modifier = Modifier.fillMaxSize(),
                contentScale = ContentScale.Crop
            )

            // Dark gradient overlay for text readability
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .background(
                        Brush.verticalGradient(
                            listOf(
                                Color(0x66000000),
                                Color(0x22000000),
                                Color(0xEE090810)
                            )
                        )
                    )
            )

            // Top Badges: Category & Live Users / Room ID
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(8.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                // Category & Room ID Chip
                Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                    Box(
                        modifier = Modifier
                            .clip(RoundedCornerShape(8.dp))
                            .background(if (isMusicPlayingInThisRoom) Color(0xFFFF9100) else QivoOrange.copy(alpha = 0.9f))
                            .padding(horizontal = 7.dp, vertical = 3.dp)
                    ) {
                        Text(
                            text = if (isMusicPlayingInThisRoom) "🎵 Playing" else room.category,
                            fontSize = 10.sp,
                            fontWeight = FontWeight.Bold,
                            color = Color.White
                        )
                    }
                    if (room.roomNumber > 0L) {
                        Box(
                            modifier = Modifier
                                .clip(RoundedCornerShape(8.dp))
                                .background(Color(0x99000000))
                                .padding(horizontal = 6.dp, vertical = 3.dp)
                        ) {
                            Text(
                                text = "#${room.roomNumber}",
                                fontSize = 10.sp,
                                fontWeight = FontWeight.Bold,
                                color = QivoYellow
                            )
                        }
                    }
                }

                // Online count badge
                Box(
                    modifier = Modifier
                        .clip(CircleShape)
                        .background(Color(0x88000000))
                        .padding(horizontal = 8.dp, vertical = 3.dp)
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Box(
                            modifier = Modifier
                                .size(6.dp)
                                .clip(CircleShape)
                                .background(Color(0xFF00E676))
                        )
                        Spacer(modifier = Modifier.width(4.dp))
                        Text(
                            text = "${room.onlineCount}",
                            fontSize = 10.sp,
                            fontWeight = FontWeight.Bold,
                            color = Color.White
                        )
                    }
                }
            }

            // Bottom Section: Host Avatar, Name & Seats count
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .align(Alignment.BottomStart)
                    .padding(10.dp)
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .horizontalScroll(rememberScrollState())
                ) {
                    Text(
                        text = room.name,
                        fontSize = 14.sp,
                        fontWeight = FontWeight.Bold,
                        color = Color.White,
                        maxLines = 1,
                        softWrap = false
                    )
                }

                Spacer(modifier = Modifier.height(4.dp))

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Box(contentAlignment = Alignment.Center) {
                            if (isMusicPlayingInThisRoom) {
                                AnimatedFloatingMusicNotes(
                                    modifier = Modifier
                                        .size(36.dp)
                                        .offset(y = (-14).dp)
                                )
                            }
                            AsyncImage(
                                model = room.hostAvatarUrl,
                                contentDescription = room.hostName,
                                modifier = Modifier
                                    .size(22.dp)
                                    .clip(CircleShape)
                                    .border(
                                        width = if (isMusicPlayingInThisRoom) 2.dp else 1.dp,
                                        color = if (isMusicPlayingInThisRoom) Color(0xFFFFD600) else QivoYellow,
                                        shape = CircleShape
                                    ),
                                contentScale = ContentScale.Crop
                            )
                        }
                        Spacer(modifier = Modifier.width(6.dp))
                        Text(
                            text = room.hostName,
                            fontSize = 11.sp,
                            color = Color.White.copy(alpha = 0.85f),
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                            modifier = Modifier.widthIn(max = 70.dp)
                        )
                    }

                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(
                            Icons.Default.Mic,
                            contentDescription = "Mic seats",
                            tint = QivoYellow,
                            modifier = Modifier.size(12.dp)
                        )
                        Spacer(modifier = Modifier.width(2.dp))
                        Text(
                            text = "${room.seatsCount}",
                            fontSize = 11.sp,
                            color = QivoYellow,
                            fontWeight = FontWeight.Bold
                        )
                    }
                }
            }
        }
    }
}


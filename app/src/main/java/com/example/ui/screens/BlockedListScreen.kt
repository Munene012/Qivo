package com.example.ui.screens

import com.example.ui.theme.QivoOrange
import android.widget.Toast
import androidx.activity.compose.BackHandler
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
import androidx.compose.material.icons.filled.Block
import androidx.compose.material.icons.filled.Female
import androidx.compose.material.icons.filled.LockOpen
import androidx.compose.material.icons.filled.Male
import androidx.compose.material.icons.filled.Person
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.runtime.derivedStateOf
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
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil.compose.AsyncImage
import com.example.data.AvatarHelper
import com.example.data.NetworkUtils
import com.example.data.SupabaseProfileService
import com.example.data.UserProfile
import com.example.ui.theme.AppTheme
import kotlinx.coroutines.launch

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun BlockedListScreen(
    currentUserId: String,
    onBackClick: () -> Unit,
    onOpenProfile: (UserProfile) -> Unit = {}
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val colors = AppTheme.colors
    val isDark = colors.isDark
    val profileService = remember { SupabaseProfileService() }

    var isLoading by remember { mutableStateOf(true) }
    var blockedUsers by remember { mutableStateOf<List<UserProfile>>(emptyList()) }
    var isLoadingMore by remember { mutableStateOf(false) }
    var hasMore by remember { mutableStateOf(true) }
    val listState = rememberLazyListState()
    val pageSize = 30

    // Track unblocked status in the CURRENT session without removing the row immediately
    // Maps userId -> Boolean (true = unblocked in current view, false = blocked)
    val sessionUnblockState = remember { mutableStateMapOf<String, Boolean>() }

    fun loadBlockedUsers() {
        if (currentUserId.isNotBlank()) {
            scope.launch {
                isLoading = true
                hasMore = true
                val list = profileService.fetchBlockedUsers(currentUserId, context = context, offset = 0, limit = pageSize)
                blockedUsers = list
                hasMore = list.size >= pageSize
                isLoading = false
            }
        } else {
            isLoading = false
        }
    }

    fun loadMoreBlockedUsers() {
        if (!isLoadingMore && !isLoading && hasMore && currentUserId.isNotBlank()) {
            scope.launch {
                isLoadingMore = true
                val next = profileService.fetchBlockedUsers(currentUserId, context = context, offset = blockedUsers.size, limit = pageSize)
                if (next.isNotEmpty()) {
                    val existing = blockedUsers.map { it.id }.toSet()
                    blockedUsers = blockedUsers + next.filter { it.id !in existing }
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
            hasMore && !isLoadingMore && !isLoading && total > 0 && last >= total - 2
        }
    }

    LaunchedEffect(shouldLoadMore) {
        if (shouldLoadMore) {
            loadMoreBlockedUsers()
        }
    }

    // Load blocked users once when opening this screen
    LaunchedEffect(currentUserId) {
        loadBlockedUsers()
    }

    BackHandler {
        onBackClick()
    }

    Surface(
        modifier = Modifier
            .fillMaxSize()
            .testTag("blocked_list_screen"),
        color = colors.screenBg
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .statusBarsPadding()
                .navigationBarsPadding()
        ) {
            // Top App Bar
            TopAppBar(
                title = {
                    Text(
                        text = "Blocked Accounts",
                        fontWeight = FontWeight.Bold,
                        fontSize = 18.sp,
                        color = colors.textPrimary
                    )
                },
                navigationIcon = {
                    IconButton(
                        onClick = onBackClick,
                        modifier = Modifier.testTag("btn_blocked_list_back")
                    ) {
                        Icon(
                            imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                            contentDescription = "Back",
                            tint = colors.textPrimary
                        )
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = colors.screenBg
                )
            )

            HorizontalDivider(color = colors.divider)

            if (isLoading) {
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(32.dp),
                    contentAlignment = Alignment.Center
                ) {
                    CircularProgressIndicator(
                        color = Color(0xFFFFD600),
                        modifier = Modifier.size(36.dp)
                    )
                }
            } else if (blockedUsers.isEmpty()) {
                // Empty state
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(32.dp),
                    contentAlignment = Alignment.Center
                ) {
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        Surface(
                            shape = CircleShape,
                            color = if (isDark) Color(0xFF262626) else Color(0xFFF3F4F6),
                            modifier = Modifier.size(80.dp)
                        ) {
                            Box(contentAlignment = Alignment.Center) {
                                Icon(
                                    imageVector = Icons.Default.Block,
                                    contentDescription = null,
                                    tint = colors.textSecondary,
                                    modifier = Modifier.size(40.dp)
                                )
                            }
                        }
                        Spacer(modifier = Modifier.height(16.dp))
                        Text(
                            text = "No Blocked Accounts",
                            fontSize = 18.sp,
                            fontWeight = FontWeight.Bold,
                            color = colors.textPrimary
                        )
                        Spacer(modifier = Modifier.height(6.dp))
                        Text(
                            text = "You haven't blocked any accounts yet.",
                            fontSize = 14.sp,
                            color = colors.textSecondary,
                            textAlign = TextAlign.Center
                        )
                    }
                }
            } else {
                LazyColumn(
                    state = listState,
                    modifier = Modifier
                        .fillMaxSize()
                        .testTag("blocked_users_list"),
                    contentPadding = PaddingValues(16.dp),
                    verticalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    items(blockedUsers, key = { it.id }) { user ->
                        val isUnblockedInSession = sessionUnblockState[user.id] == true

                        BlockedUserRow(
                            user = user,
                            isUnblockedInSession = isUnblockedInSession,
                            onToggleBlock = {
                                if (isUnblockedInSession) {
                                    // Block again
                                    scope.launch {
                                        profileService.blockUser(currentUserId, user.id, context)
                                        sessionUnblockState[user.id] = false
                                        NetworkUtils.showToast(context, "${user.name} has been blocked")
                                    }
                                } else {
                                    // Unblock user: Keeps user displayed in this screen with option to block back!
                                    scope.launch {
                                        profileService.unblockUser(currentUserId, user.id, context)
                                        sessionUnblockState[user.id] = true
                                        NetworkUtils.showToast(context, "${user.name} has been unblocked")
                                    }
                                }
                            },
                            onProfileClick = {
                                onOpenProfile(user)
                            },
                            isDark = isDark,
                            textColor = colors.textPrimary,
                            subtextColor = colors.textSecondary,
                            cardBg = colors.cardBg,
                            cardBorder = colors.cardBorder
                        )
                    }

                    if (isLoadingMore) {
                        item(key = "blocked_users_loading_more") {
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
private fun BlockedUserRow(
    user: UserProfile,
    isUnblockedInSession: Boolean,
    onToggleBlock: () -> Unit,
    onProfileClick: () -> Unit,
    isDark: Boolean,
    textColor: Color,
    subtextColor: Color,
    cardBg: Color,
    cardBorder: Color
) {
    Surface(
        shape = RoundedCornerShape(16.dp),
        color = cardBg,
        border = androidx.compose.foundation.BorderStroke(1.dp, cardBorder),
        modifier = Modifier
            .fillMaxWidth()
            .testTag("blocked_user_row_${user.id}")
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .clickable { onProfileClick() }
                .padding(14.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            // Avatar
            Box(
                modifier = Modifier
                    .size(50.dp)
                    .clip(CircleShape)
                    .background(if (isDark) Color(0xFF333333) else Color(0xFFE5E7EB)),
                contentAlignment = Alignment.Center
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

            // Info (Name, ID, Country, Status)
            Column(modifier = Modifier.weight(1f)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        text = user.name,
                        fontWeight = FontWeight.Bold,
                        fontSize = 15.sp,
                        color = textColor,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                    Spacer(modifier = Modifier.width(6.dp))
                    // Gender Tag
                    val isMale = user.gender.equals("Male", ignoreCase = true)
                    Surface(
                        shape = RoundedCornerShape(10.dp),
                        color = if (isMale) Color(0xFF38521F) else QivoOrange
                    ) {
                        Row(
                            modifier = Modifier.padding(horizontal = 5.dp, vertical = 1.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Icon(
                                imageVector = if (isMale) Icons.Default.Male else Icons.Default.Female,
                                contentDescription = null,
                                tint = Color.White,
                                modifier = Modifier.size(10.dp)
                            )
                        }
                    }
                }

                Spacer(modifier = Modifier.height(2.dp))

                Text(
                    text = "ID: ${user.numericId} • ${user.country}",
                    fontSize = 12.sp,
                    color = subtextColor,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )

                if (isUnblockedInSession) {
                    Spacer(modifier = Modifier.height(2.dp))
                    Text(
                        text = "Unblocked (Leaves on exit)",
                        fontSize = 11.sp,
                        fontWeight = FontWeight.SemiBold,
                        color = Color(0xFF4CAF50)
                    )
                }
            }

            Spacer(modifier = Modifier.width(8.dp))

            // Action Button: Unblock vs Block
            if (isUnblockedInSession) {
                // If unblocked in this session, show button to re-block if needed
                Button(
                    onClick = onToggleBlock,
                    shape = RoundedCornerShape(20.dp),
                    colors = ButtonDefaults.buttonColors(
                        containerColor = Color(0xFFD32F2F)
                    ),
                    contentPadding = PaddingValues(horizontal = 14.dp, vertical = 6.dp),
                    modifier = Modifier.testTag("btn_reblock_${user.id}")
                ) {
                    Icon(
                        imageVector = Icons.Default.Block,
                        contentDescription = null,
                        tint = Color.White,
                        modifier = Modifier.size(14.dp)
                    )
                    Spacer(modifier = Modifier.width(4.dp))
                    Text(
                        text = "Block",
                        fontSize = 12.sp,
                        fontWeight = FontWeight.Bold,
                        color = Color.White
                    )
                }
            } else {
                // If blocked, show Unblock button
                OutlinedButton(
                    onClick = onToggleBlock,
                    shape = RoundedCornerShape(20.dp),
                    colors = ButtonDefaults.outlinedButtonColors(
                        contentColor = textColor
                    ),
                    border = androidx.compose.foundation.BorderStroke(
                        1.dp,
                        if (isDark) Color(0xFF555555) else Color(0xFFCCCCCC)
                    ),
                    contentPadding = PaddingValues(horizontal = 14.dp, vertical = 6.dp),
                    modifier = Modifier.testTag("btn_unblock_${user.id}")
                ) {
                    Icon(
                        imageVector = Icons.Default.LockOpen,
                        contentDescription = null,
                        tint = if (isDark) Color(0xFF4ADE80) else Color(0xFF16A34A),
                        modifier = Modifier.size(14.dp)
                    )
                    Spacer(modifier = Modifier.width(4.dp))
                    Text(
                        text = "Unblock",
                        fontSize = 12.sp,
                        fontWeight = FontWeight.Bold,
                        color = textColor
                    )
                }
            }
        }
    }
}

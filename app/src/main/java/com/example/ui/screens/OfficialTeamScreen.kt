package com.example.ui.screens

import android.content.Context
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
import androidx.compose.material.icons.automirrored.filled.ArrowForward
import androidx.compose.material.icons.filled.Campaign
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.PersonAdd
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Visibility
import androidx.compose.material.icons.filled.MonetizationOn
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
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.data.AvatarHelper
import com.example.data.CoinTransaction
import com.example.data.SupabaseConfig
import com.example.data.SupabaseProfileService
import com.example.data.UserProfile
import com.example.data.UserSessionManager
import com.example.ui.components.AppLoadingSpinner
import com.example.ui.theme.AppTheme
import com.example.ui.theme.QivoGold
import com.example.ui.theme.QivoOrange
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import org.json.JSONArray
import org.json.JSONObject
import java.text.SimpleDateFormat
import java.util.Locale
import java.util.TimeZone

data class OfficialUpdate(
    val id: String,
    val title: String,
    val description: String = "",
    val time: String,
    val timestampMs: Long = 0L,
    val type: String, // "FOLLOW", "VISITOR", "RECHARGE", "TRANSFER", "WELCOME_BONUS", "ANNOUNCEMENT", "INFO"
    val mentionedUser: UserProfile? = null
)

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun OfficialTeamScreen(
    onBackClick: () -> Unit,
    onOpenUserDetails: (UserProfile) -> Unit = {}
) {
    val colors = AppTheme.colors
    val isDark = colors.isDark
    val context = LocalContext.current
    val scope = rememberCoroutineScope()

    val session = remember { UserSessionManager.getSession(context) }
    val currentUserId = session?.userId ?: ""
    val currentUserName = session?.name?.ifBlank { "User" } ?: "User"

    val profileService = remember { SupabaseProfileService() }

    var isLoading by remember { mutableStateOf(true) }
    var isRefreshing by remember { mutableStateOf(false) }
    var realUpdates by remember { mutableStateOf<List<OfficialUpdate>>(emptyList()) }

    fun loadRealServerData(isPull: Boolean = false) {
        scope.launch {
            if (isPull) isRefreshing = true else isLoading = true
            val updates = fetchRealOfficialUpdates(context, currentUserId, currentUserName, profileService)
            realUpdates = updates
            isLoading = false
            isRefreshing = false
        }
    }

    LaunchedEffect(currentUserId) {
        loadRealServerData()
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Box(
                            modifier = Modifier
                                .size(32.dp)
                                .clip(CircleShape)
                                .background(
                                    Brush.linearGradient(
                                        listOf(Color(0xFFFFD54F), Color(0xFFFF9100), Color(0xFFFF3D00))
                                    )
                                ),
                            contentAlignment = Alignment.Center
                        ) {
                            Text(
                                text = "Q",
                                color = Color.White,
                                fontWeight = FontWeight.Black,
                                fontSize = 16.sp
                            )
                        }
                        Spacer(modifier = Modifier.width(10.dp))
                        Text(
                            text = "Official Team",
                            fontWeight = FontWeight.Bold,
                            fontSize = 18.sp,
                            color = colors.textPrimary
                        )
                    }
                },
                navigationIcon = {
                    IconButton(onClick = onBackClick) {
                        Icon(
                            imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                            contentDescription = "Back",
                            tint = colors.textPrimary
                        )
                    }
                },
                actions = {
                    IconButton(
                        onClick = { loadRealServerData(isPull = true) },
                        enabled = !isLoading && !isRefreshing
                    ) {
                        if (isRefreshing) {
                            CircularProgressIndicator(
                                modifier = Modifier.size(20.dp),
                                color = QivoOrange,
                                strokeWidth = 2.dp
                            )
                        } else {
                            Icon(
                                imageVector = Icons.Default.Refresh,
                                contentDescription = "Refresh",
                                tint = colors.textPrimary
                            )
                        }
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = Color(0xFF381A05),
                    titleContentColor = Color.White,
                    navigationIconContentColor = Color.White
                )
            )
        },
        containerColor = Color(0xFF381A05)
    ) { padding ->
        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
        ) {
            if (isLoading && realUpdates.isEmpty()) {
                Box(
                    modifier = Modifier.fillMaxSize(),
                    contentAlignment = Alignment.Center
                ) {
                    AppLoadingSpinner(size = 40.dp)
                }
            } else if (realUpdates.isEmpty()) {
                Box(
                    modifier = Modifier.fillMaxSize(),
                    contentAlignment = Alignment.Center
                ) {
                    Column(
                        horizontalAlignment = Alignment.CenterHorizontally,
                        modifier = Modifier.padding(24.dp)
                    ) {
                        Text(
                            text = "No updates yet",
                            fontWeight = FontWeight.Bold,
                            fontSize = 16.sp,
                            color = colors.textPrimary
                        )
                        Spacer(modifier = Modifier.height(6.dp))
                        Text(
                            text = "Official notifications and interactions will show up here.",
                            fontSize = 13.sp,
                            color = colors.textSecondary
                        )
                    }
                }
            } else {
                LazyColumn(
                    modifier = Modifier
                        .fillMaxSize()
                        .testTag("official_team_updates_list"),
                    contentPadding = PaddingValues(horizontal = 14.dp, vertical = 12.dp),
                    verticalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    items(
                        items = realUpdates,
                        key = { it.id }
                    ) { update ->
                        OfficialUpdateCard(
                            update = update,
                            isDark = isDark,
                            onUserClick = { user ->
                                onOpenUserDetails(user)
                            }
                        )
                    }
                }
            }
        }
    }
}

@Composable
fun OfficialUpdateCard(
    update: OfficialUpdate,
    isDark: Boolean,
    onUserClick: (UserProfile) -> Unit
) {
    val colors = AppTheme.colors
    val hasUser = update.mentionedUser != null

    Card(
        modifier = Modifier
            .fillMaxWidth()
            .then(
                if (hasUser) {
                    Modifier.clickable { onUserClick(update.mentionedUser!!) }
                } else {
                    Modifier
                }
            ),
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(
            containerColor = if (isDark) Color(0xFF1E1E28) else Color.White
        ),
        border = androidx.compose.foundation.BorderStroke(
            1.dp,
            if (hasUser) QivoOrange.copy(alpha = 0.25f) else if (isDark) Color(0x1FFFFFFF) else Color(0x10000000)
        ),
        elevation = CardDefaults.cardElevation(defaultElevation = 2.dp)
    ) {
        Row(
            modifier = Modifier
                .padding(14.dp)
                .fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically
        ) {
            // Icon or User Avatar
            if (update.mentionedUser != null) {
                Box(
                    modifier = Modifier
                        .size(46.dp)
                        .clip(CircleShape)
                        .clickable { onUserClick(update.mentionedUser) }
                ) {
                    AvatarHelper.UserAvatarImage(
                        avatarUrl = update.mentionedUser.avatarUrl,
                        userId = update.mentionedUser.id,
                        gender = update.mentionedUser.gender,
                        numericId = update.mentionedUser.numericId,
                        contentDescription = update.mentionedUser.name,
                        showFrame = true,
                        shape = CircleShape,
                        modifier = Modifier.fillMaxSize(),
                        contentScale = ContentScale.Crop
                    )

                    // Small indicator badge
                    Box(
                        modifier = Modifier
                            .size(16.dp)
                            .align(Alignment.BottomEnd)
                            .clip(CircleShape)
                            .background(
                                when (update.type) {
                                    "FOLLOW" -> Color(0xFF00E676)
                                    "VISITOR" -> Color(0xFF2979FF)
                                    else -> QivoOrange
                                }
                            ),
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(
                            imageVector = when (update.type) {
                                "FOLLOW" -> Icons.Default.PersonAdd
                                "VISITOR" -> Icons.Default.Visibility
                                else -> Icons.Default.CheckCircle
                            },
                            contentDescription = null,
                            tint = Color.White,
                            modifier = Modifier.size(10.dp)
                        )
                    }
                }
            } else {
                // Official 3D Style Badge
                Box(
                    modifier = Modifier
                        .size(46.dp)
                        .clip(CircleShape)
                        .background(
                            when (update.type) {
                                "COIN", "WELCOME_BONUS" -> Brush.linearGradient(
                                    listOf(Color(0xFFFFD54F), Color(0xFFFF9100), Color(0xFFFF6D00))
                                )
                                "ANNOUNCEMENT" -> Brush.linearGradient(
                                    listOf(Color(0xFF8B5CF6), Color(0xFF6D28D9))
                                )
                                else -> Brush.linearGradient(
                                    listOf(Color(0xFFFF9100), Color(0xFFFF3D00))
                                )
                            }
                        ),
                    contentAlignment = Alignment.Center
                ) {
                    when (update.type) {
                        "COIN", "WELCOME_BONUS" -> Icon(
                            imageVector = Icons.Default.MonetizationOn,
                            contentDescription = "Coins",
                            tint = Color.White,
                            modifier = Modifier.size(24.dp)
                        )
                        "ANNOUNCEMENT" -> Icon(
                            imageVector = Icons.Default.Campaign,
                            contentDescription = "Announcement",
                            tint = Color.White,
                            modifier = Modifier.size(24.dp)
                        )
                        else -> Text(
                            text = "Q",
                            color = Color.White,
                            fontWeight = FontWeight.Black,
                            fontSize = 22.sp
                        )
                    }
                }
            }

            Spacer(modifier = Modifier.width(14.dp))

            // Body text
            Column(modifier = Modifier.weight(1f)) {
                if (update.mentionedUser != null) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        // Clickable User Name
                        Text(
                            text = update.mentionedUser.name.ifBlank { "User" },
                            fontSize = 15.sp,
                            fontWeight = FontWeight.Bold,
                            color = QivoOrange,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                            modifier = Modifier
                                .clip(RoundedCornerShape(4.dp))
                                .clickable { onUserClick(update.mentionedUser) }
                        )

                        Text(
                            text = update.time,
                            fontSize = 11.sp,
                            color = colors.textSecondary
                        )
                    }

                    Spacer(modifier = Modifier.height(2.dp))

                    Text(
                        text = update.title,
                        fontSize = 13.5.sp,
                        fontWeight = FontWeight.Medium,
                        color = colors.textPrimary,
                        maxLines = 2,
                        overflow = TextOverflow.Ellipsis
                    )

                    if (update.description.isNotBlank()) {
                        Spacer(modifier = Modifier.height(2.dp))
                        Text(
                            text = update.description,
                            fontSize = 12.sp,
                            color = colors.textSecondary,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                    }
                } else {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            text = update.title,
                            fontSize = 14.5.sp,
                            fontWeight = FontWeight.Bold,
                            color = colors.textPrimary,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                            modifier = Modifier.weight(1f, fill = false)
                        )

                        Spacer(modifier = Modifier.width(8.dp))

                        Text(
                            text = update.time,
                            fontSize = 11.sp,
                            color = colors.textSecondary
                        )
                    }

                    if (update.description.isNotBlank()) {
                        Spacer(modifier = Modifier.height(3.dp))
                        Text(
                            text = update.description,
                            fontSize = 12.5.sp,
                            color = colors.textSecondary,
                            maxLines = 3,
                            overflow = TextOverflow.Ellipsis
                        )
                    }
                }
            }

            // If user mentioned, show a clickable navigation prompt
            if (hasUser) {
                Spacer(modifier = Modifier.width(8.dp))
                Surface(
                    onClick = { onUserClick(update.mentionedUser!!) },
                    shape = RoundedCornerShape(12.dp),
                    color = QivoOrange.copy(alpha = 0.12f),
                    border = androidx.compose.foundation.BorderStroke(1.dp, QivoOrange.copy(alpha = 0.35f))
                ) {
                    Row(
                        modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            text = "View",
                            fontSize = 11.sp,
                            fontWeight = FontWeight.Bold,
                            color = QivoOrange
                        )
                        Icon(
                            imageVector = Icons.AutoMirrored.Filled.ArrowForward,
                            contentDescription = "View Profile",
                            tint = QivoOrange,
                            modifier = Modifier.size(12.dp)
                        )
                    }
                }
            }
        }
    }
}

/**
 * Fetch authoritative real server data from Supabase:
 * 1. user_follows (real followers who followed current user)
 * 2. profile_visitors (real users who visited current user's profile)
 * 3. coin_transactions (real recharges and coin transfers)
 * 4. claimed_welcome_bonuses (real welcome bonus status)
 * 5. app_advertisements (real active system announcements)
 */
suspend fun fetchRealOfficialUpdates(
    context: Context,
    userId: String,
    userName: String,
    profileService: SupabaseProfileService
): List<OfficialUpdate> = withContext(Dispatchers.IO) {
    if (userId.isBlank()) return@withContext emptyList()

    val baseUrl = SupabaseConfig.supabaseUrl.trim().removeSuffix("/")
    val apiKey = SupabaseConfig.supabaseAnonKey.trim()
    val client = OkHttpClient()
    val authHeader = "Bearer $apiKey"

    val updates = mutableListOf<OfficialUpdate>()

    // 1. Fetch raw data first, then collect distinct user IDs
    val rawFollowers = mutableListOf<JSONObject>()
    val rawVisitors = mutableListOf<JSONObject>()
    val rawTxs = mutableListOf<com.example.data.CoinTransaction>()
    
    // 1. REAL FOLLOWERS (user_follows)
    try {
        val followUrl = "$baseUrl/rest/v1/user_follows?following_id=eq.$userId&select=follower_id,created_at&order=created_at.desc&limit=25"
        val req = Request.Builder().url(followUrl).addHeader("apikey", apiKey).addHeader("Authorization", authHeader).get().build()
        client.newCall(req).execute().use { res ->
            if (res.isSuccessful) {
                val body = res.body?.string() ?: "[]"
                val arr = JSONArray(body)
                for (i in 0 until arr.length()) rawFollowers.add(arr.getJSONObject(i))
            }
        }
    } catch (_: Exception) {}

    // 2. REAL VISITORS (profile_visitors)
    try {
        val visitorsUrl = "$baseUrl/rest/v1/profile_visitors?visited_id=eq.$userId&select=visitor_id,visitor_name,visitor_avatar,visited_at&order=visited_at.desc&limit=25"
        val req = Request.Builder().url(visitorsUrl).addHeader("apikey", apiKey).addHeader("Authorization", authHeader).get().build()
        client.newCall(req).execute().use { res ->
            if (res.isSuccessful) {
                val body = res.body?.string() ?: "[]"
                val arr = JSONArray(body)
                for (i in 0 until arr.length()) rawVisitors.add(arr.getJSONObject(i))
            }
        }
    } catch (_: Exception) {}

    // 3. REAL COIN TRANSACTIONS (coin_transactions)
    try {
        rawTxs.addAll(profileService.fetchCoinTransactions(userId))
    } catch (_: Exception) {}

    // Collect all relevant user IDs
    val relevantUserIds = mutableSetOf<String>()
    rawFollowers.forEach { relevantUserIds.add(it.optString("follower_id", "").trim()) }
    rawVisitors.forEach { relevantUserIds.add(it.optString("visitor_id", "").trim()) }
    rawTxs.forEach { if (it.referenceId.isNotBlank() && it.referenceId != userId) relevantUserIds.add(it.referenceId.trim()) }

    // Efficiently fetch only these profiles in one batch
    val profilesMap = if (relevantUserIds.isNotEmpty()) {
        profileService.fetchProfilesByIds(relevantUserIds.toList()).associateBy { it.id.trim() }
    } else emptyMap()

    val updates = mutableListOf<OfficialUpdate>()

    // Process Followers
    rawFollowers.forEachIndexed { i, obj ->
        val followerId = obj.optString("follower_id", "").trim()
        val createdAt = obj.optString("created_at", "")
        if (followerId.isNotBlank()) {
            val followerProfile = profilesMap[followerId]
            val name = followerProfile?.name?.ifBlank { "A user" } ?: "A user"
            updates.add(OfficialUpdate(
                id = "follow_${followerId}_$i",
                title = "$name followed you",
                description = "Started following your profile updates",
                time = formatTimeAgo(createdAt),
                timestampMs = parseIsoToMillis(createdAt),
                type = "FOLLOW",
                mentionedUser = followerProfile
            ))
        }
    }

    // Process Visitors
    rawVisitors.forEachIndexed { i, obj ->
        val visitorId = obj.optString("visitor_id", "").trim()
        val visitedAt = obj.optString("visited_at", "")
        if (visitorId.isNotBlank() && visitorId != userId) {
            val visitorProfile = profilesMap[visitorId] ?: UserProfile(
                id = visitorId,
                name = obj.optString("visitor_name", "Visitor"),
                gender = "Unknown", country = "Global", avatarUrl = obj.optString("visitor_avatar", "")
            )
            val name = visitorProfile.name.ifBlank { "Someone" }
            updates.add(OfficialUpdate(
                id = "visitor_${visitorId}_$i",
                title = "$name visited your profile",
                description = "Viewed your profile details and photos",
                time = formatTimeAgo(visitedAt),
                timestampMs = parseIsoToMillis(visitedAt),
                type = "VISITOR",
                mentionedUser = visitorProfile
            ))
        }
    }

    // Process Transactions
    for (tx in rawTxs) {
        val isRecharge = tx.type.equals("RECHARGE", ignoreCase = true) || tx.title.contains("Recharge", ignoreCase = true)
        val isWelcome = tx.type.equals("WELCOME_BONUS", ignoreCase = true) || tx.title.contains("Welcome", ignoreCase = true)
        val isGift = tx.type.equals("GIFT", ignoreCase = true) || tx.title.contains("Gift", ignoreCase = true)
        val isTransfer = tx.type.equals("TRANSFER", ignoreCase = true) || tx.title.contains("Transfer", ignoreCase = true)
        val partnerUser = if (tx.referenceId.isNotBlank() && tx.referenceId != userId) profilesMap[tx.referenceId.trim()] else null
        val titleText = when {
            isRecharge -> "Recharge of ${tx.amount} coins successful"
            isWelcome -> "Received ${tx.amount} Welcome Bonus coins!"
            tx.type.equals("COIN_RECEIVED", ignoreCase = true) -> if (partnerUser != null) "Received ${tx.amount} coins from ${partnerUser.name}" else "Received ${tx.amount} coins"
            isGift -> if (partnerUser != null) "Gift reward: +${tx.amount} coins from ${partnerUser.name}" else "Gift reward: +${tx.amount} coins"
            isTransfer -> if (partnerUser != null) "Transfer: ${tx.amount} coins with ${partnerUser.name}" else "Transfer: ${tx.amount} coins"
            else -> tx.title.ifBlank { "Transaction: ${tx.amount} coins" }
        }
        updates.add(OfficialUpdate(
            id = "tx_${tx.id}",
            title = titleText,
            description = tx.description.ifBlank { "Processed by QIVO Official System" },
            time = formatTimeAgo(tx.createdAt),
            timestampMs = parseIsoToMillis(tx.createdAt),
            type = if (isWelcome) "WELCOME_BONUS" else "COIN",
            mentionedUser = partnerUser
        ))
    }

    // 4. REAL WELCOME BONUS CLAIMS (claimed_welcome_bonuses)
    try {
        val claimUrl = "$baseUrl/rest/v1/claimed_welcome_bonuses?user_id=eq.$userId&select=bonus_coins,claimed_at"
        val req = Request.Builder()
            .url(claimUrl)
            .addHeader("apikey", apiKey)
            .addHeader("Authorization", authHeader)
            .get()
            .build()

        client.newCall(req).execute().use { res ->
            if (res.isSuccessful) {
                val body = res.body?.string() ?: "[]"
                val arr = JSONArray(body)
                if (arr.length() > 0) {
                    val obj = arr.getJSONObject(0)
                    val bonus = obj.optLong("bonus_coins", 500L)
                    val claimedAt = obj.optString("claimed_at", "")
                    val timeMs = parseIsoToMillis(claimedAt)

                    // Avoid duplicate if already present in coin transactions
                    if (updates.none { it.type == "WELCOME_BONUS" }) {
                        updates.add(
                            OfficialUpdate(
                                id = "welcome_bonus_claim",
                                title = "Welcome Bonus Claimed: +$bonus coins",
                                description = "Your device welcome bonus has been credited to your balance.",
                                time = formatTimeAgo(claimedAt),
                                timestampMs = timeMs,
                                type = "WELCOME_BONUS",
                                mentionedUser = null
                            )
                        )
                    }
                }
            }
        }
    } catch (_: Exception) {}

    // 5. REAL SYSTEM ANNOUNCEMENTS (app_advertisements)
    try {
        val adUrl = "$baseUrl/rest/v1/app_advertisements?is_active=eq.true&order=created_at.desc&limit=3"
        val req = Request.Builder()
            .url(adUrl)
            .addHeader("apikey", apiKey)
            .addHeader("Authorization", authHeader)
            .get()
            .build()

        client.newCall(req).execute().use { res ->
            if (res.isSuccessful) {
                val body = res.body?.string() ?: "[]"
                val arr = JSONArray(body)
                for (i in 0 until arr.length()) {
                    val obj = arr.getJSONObject(i)
                    val title = obj.optString("title", "").trim()
                    val desc = obj.optString("description", "").trim()
                    val createdAt = obj.optString("created_at", "")
                    if (title.isNotBlank()) {
                        updates.add(
                            OfficialUpdate(
                                id = "ad_$i",
                                title = title,
                                description = desc.ifBlank { "Official QIVO Announcement" },
                                time = formatTimeAgo(createdAt),
                                timestampMs = parseIsoToMillis(createdAt),
                                type = "ANNOUNCEMENT",
                                mentionedUser = null
                            )
                        )
                    }
                }
            }
        }
    } catch (_: Exception) {}

    // 6. ALWAYS INCLUDE OFFICIAL WELCOME TO QIVO
    updates.add(
        OfficialUpdate(
            id = "official_welcome_qivo",
            title = "Welcome to QIVO, $userName!",
            description = "Explore live voice rooms, share gifts, and connect with people from around the world.",
            time = "Permanent",
            timestampMs = 0L, // Kept at the bottom as welcome anchor
            type = "INFO",
            mentionedUser = null
        )
    )

    // Sort chronologically (newest on top, permanent welcome at end)
    return@withContext updates.sortedWith(
        compareByDescending<OfficialUpdate> { it.timestampMs }
            .thenBy { if (it.id == "official_welcome_qivo") 1 else 0 }
    )
}

private fun parseIsoToMillis(isoString: String): Long {
    if (isoString.isBlank()) return 0L
    return try {
        val clean = isoString.substringBefore(".").substringBefore("+").removeSuffix("Z")
        val sdf = SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss", Locale.US).apply {
            timeZone = TimeZone.getTimeZone("UTC")
        }
        sdf.parse(clean)?.time ?: 0L
    } catch (_: Exception) {
        0L
    }
}

private fun formatTimeAgo(isoString: String): String {
    if (isoString.isBlank()) return "Just now"
    val millis = parseIsoToMillis(isoString)
    if (millis <= 0L) return "Recently"

    val diffMs = System.currentTimeMillis() - millis
    val diffSec = diffMs / 1000
    val diffMin = diffSec / 60
    val diffHours = diffMin / 60
    val diffDays = diffHours / 24

    return when {
        diffSec < 60 -> "Just now"
        diffMin < 60 -> "${diffMin}m ago"
        diffHours < 24 -> "${diffHours}h ago"
        diffDays == 1L -> "Yesterday"
        diffDays < 7 -> "${diffDays}d ago"
        else -> SimpleDateFormat("MMM d", Locale.US).format(java.util.Date(millis))
    }
}

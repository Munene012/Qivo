package com.example.ui.screens
import com.example.ui.components.AppToast

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.widget.Toast
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
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
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.Chat
import androidx.compose.material.icons.filled.AutoAwesome
import androidx.compose.material.icons.filled.Bedtime
import androidx.compose.material.icons.filled.Block
import androidx.compose.material.icons.filled.Cake
import androidx.compose.material.icons.filled.Call
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.ChevronRight
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.ContentCopy
import androidx.compose.material.icons.filled.DirectionsRun
import androidx.compose.material.icons.filled.Diversity1
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.EditNote
import androidx.compose.material.icons.filled.Favorite
import androidx.compose.material.icons.filled.FavoriteBorder
import androidx.compose.material.icons.filled.Female
import androidx.compose.material.icons.filled.Flag
import androidx.compose.material.icons.filled.Height
import androidx.compose.material.icons.filled.Language
import androidx.compose.material.icons.filled.LocalBar
import androidx.compose.material.icons.filled.LockOpen
import androidx.compose.material.icons.filled.Male
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material.icons.filled.MusicNote
import androidx.compose.material.icons.filled.NightlightRound
import androidx.compose.material.icons.filled.Pets
import androidx.compose.material.icons.filled.Psychology
import androidx.compose.material.icons.filled.Public
import androidx.compose.material.icons.filled.Restaurant
import androidx.compose.material.icons.filled.School
import androidx.compose.material.icons.filled.SmokingRooms
import androidx.compose.material.icons.filled.Star
import androidx.compose.material.icons.filled.Videocam
import androidx.compose.material.icons.filled.Work
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.RadioButton
import androidx.compose.material3.RadioButtonDefaults
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
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
import androidx.compose.ui.graphics.vector.ImageVector
import com.example.ui.components.Follow3DIcon
import com.example.ui.components.ProfileCategory3DIcon
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import coil.compose.AsyncImage
import com.example.data.AvatarHelper
import com.example.data.AppDataCacheManager
import com.example.data.NetworkUtils
import com.example.data.SupabaseProfileService
import com.example.data.UserProfile
import com.example.data.UserSessionManager
import com.example.ui.components.InsufficientCoinsBottomSheet
import com.example.ui.theme.AppTheme
import androidx.compose.runtime.mutableLongStateOf
import kotlinx.coroutines.launch

@Composable
fun UserDetailScreen(
    targetUser: UserProfile,
    onBackClick: () -> Unit,
    onStartChat: (UserProfile) -> Unit,
    onOpenFollowsList: ((FollowTab, String) -> Unit)? = null,
    onOpenRechargeWallet: (() -> Unit)? = null,
    onOpenEditProfile: (() -> Unit)? = null
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val scrollState = rememberScrollState()
    val chipsScrollState = rememberScrollState()
    val profileService = remember { SupabaseProfileService() }
    val session = remember { UserSessionManager.getSession(context) }
    val currentUserId = session?.userId ?: ""

    var liveUser by remember { mutableStateOf(targetUser) }
    val isCurrentUser = remember(liveUser.id, targetUser.id, currentUserId, liveUser.numericId, session?.numericId) {
        (currentUserId.isNotBlank() && (liveUser.id == currentUserId || targetUser.id == currentUserId)) ||
        (session?.numericId != null && session.numericId > 0 && (liveUser.numericId == session.numericId || targetUser.numericId == session.numericId))
    }

    val cachedFollowing = remember(currentUserId, targetUser.id) {
        com.example.data.FollowDataCacheStore.getFollowingSet(currentUserId)?.contains(targetUser.id)
    }
    var isFollowing by remember(currentUserId, targetUser.id) { mutableStateOf(cachedFollowing ?: false) }
    var isBlockedByMe by remember { mutableStateOf(false) }
    var isBlockedByThem by remember { mutableStateOf(false) }
    var isBlocked by remember { mutableStateOf(false) }
    var showOptionsMenu by remember { mutableStateOf(false) }
    var showBlockConfirmDialog by remember { mutableStateOf(false) }
    var showReportDialog by remember { mutableStateOf(false) }
    var fullscreenPhotoUrl by remember { mutableStateOf<String?>(null) }
    var showInsufficientCoinsDialog by remember { mutableStateOf(false) }
    var insufficientCoinsRequired by remember { mutableLongStateOf(80L) }
    var userCurrentCoins by remember { mutableLongStateOf(session?.coins ?: 0L) }

    LaunchedEffect(targetUser.id, targetUser.numericId, currentUserId) {
        // 1. Immediately check local cache & in-memory profiles for instant offline rendering
        val candidateKeys = listOf(
            targetUser.id,
            if (targetUser.numericId > 0) targetUser.numericId.toString() else "",
            targetUser.email,
            if (currentUserId.isNotBlank() && (targetUser.id == currentUserId || targetUser.id.isBlank())) currentUserId else ""
        ).filter { it.isNotBlank() }

        for (k in candidateKeys) {
            val inMem = SupabaseProfileService.getInMemoryProfilesMap()[k]
            if (inMem != null) {
                liveUser = inMem
                break
            }
            val cached = AppDataCacheManager.getCachedUserDetail(context, k)
            if (cached != null) {
                liveUser = cached
                break
            }
        }

        if (currentUserId.isNotBlank() && targetUser.id.isNotBlank() && currentUserId != targetUser.id) {
            val cachedState = com.example.data.FollowDataCacheStore.getFollowingSet(currentUserId)?.contains(targetUser.id)
            if (cachedState != null) {
                isFollowing = cachedState
            }
            scope.launch {
                val remoteFollowing = profileService.isFollowing(currentUserId, targetUser.id)
                isFollowing = remoteFollowing
                com.example.data.FollowDataCacheStore.updateFollowStatus(currentUserId, targetUser.id, remoteFollowing)
            }
            isBlockedByMe = profileService.isUserBlockedByMe(currentUserId, targetUser.id, context)
            isBlockedByThem = profileService.isUserBlockedByThem(currentUserId, targetUser.id, context)
            isBlocked = isBlockedByMe || isBlockedByThem
            scope.launch {
                if (NetworkUtils.isOnline(context)) {
                    val remoteBlocked = profileService.checkIfBlockedBidirectionalRemote(currentUserId, targetUser.id, context)
                    if (remoteBlocked) {
                        isBlockedByMe = profileService.isUserBlockedByMe(currentUserId, targetUser.id, context)
                        isBlockedByThem = profileService.isUserBlockedByThem(currentUserId, targetUser.id, context)
                        isBlocked = true
                    }
                }
            }
            // Record profile visitor in background
            scope.launch {
                if (NetworkUtils.isOnline(context)) {
                    profileService.recordProfileVisit(currentUserId, targetUser.id)
                }
            }
        }
        while (true) {
            if (NetworkUtils.isOnline(context)) {
                val fullProfile = if (targetUser.id.isNotBlank()) {
                    profileService.fetchProfile(targetUser.id, targetUser.email) ?: profileService.fetchProfileById(targetUser.id)
                } else {
                    profileService.fetchProfileByNumericId(targetUser.numericId)
                }
                if (fullProfile != null) {
                    val merged = fullProfile.copy(
                        name = if (fullProfile.name.isNotBlank() && fullProfile.name != "User" && fullProfile.name != "QIVO User") fullProfile.name else liveUser.name,
                        gender = if (isFieldSet(fullProfile.gender)) fullProfile.gender else liveUser.gender,
                        country = if (isFieldSet(fullProfile.country)) fullProfile.country else liveUser.country,
                        avatarUrl = if (fullProfile.avatarUrl.isNotBlank()) fullProfile.avatarUrl else liveUser.avatarUrl,
                        socialPreferences = if (isFieldSet(fullProfile.socialPreferences)) fullProfile.socialPreferences else liveUser.socialPreferences,
                        exercise = if (isFieldSet(fullProfile.exercise)) fullProfile.exercise else liveUser.exercise,
                        education = if (isFieldSet(fullProfile.education)) fullProfile.education else liveUser.education,
                        height = if (isFieldSet(fullProfile.height)) fullProfile.height else liveUser.height,
                        relationshipStatus = if (isFieldSet(fullProfile.relationshipStatus)) fullProfile.relationshipStatus else liveUser.relationshipStatus,
                        occupation = if (isFieldSet(fullProfile.occupation)) fullProfile.occupation else liveUser.occupation,
                        languages = if (isFieldSet(fullProfile.languages)) fullProfile.languages else liveUser.languages,
                        hobbiesInterests = if (isFieldSet(fullProfile.hobbiesInterests)) fullProfile.hobbiesInterests else liveUser.hobbiesInterests,
                        musicPreference = if (isFieldSet(fullProfile.musicPreference)) fullProfile.musicPreference else liveUser.musicPreference,
                        dietaryHabit = if (isFieldSet(fullProfile.dietaryHabit)) fullProfile.dietaryHabit else liveUser.dietaryHabit,
                        sleepHabit = if (isFieldSet(fullProfile.sleepHabit)) fullProfile.sleepHabit else liveUser.sleepHabit,
                        smoking = if (isFieldSet(fullProfile.smoking)) fullProfile.smoking else liveUser.smoking,
                        liquor = if (isFieldSet(fullProfile.liquor)) fullProfile.liquor else liveUser.liquor,
                        superpower = if (isFieldSet(fullProfile.superpower)) fullProfile.superpower else liveUser.superpower,
                        pets = if (isFieldSet(fullProfile.pets)) fullProfile.pets else liveUser.pets,
                        personalityType = if (isFieldSet(fullProfile.personalityType)) fullProfile.personalityType else liveUser.personalityType,
                        horoscopes = if (isFieldSet(fullProfile.horoscopes)) fullProfile.horoscopes else liveUser.horoscopes,
                        albumPhotos = if (fullProfile.albumPhotos.isNotEmpty()) fullProfile.albumPhotos else liveUser.albumPhotos
                    )
                    liveUser = merged
                    AppDataCacheManager.saveUserDetailCache(context, merged)
                }
            }
            kotlinx.coroutines.delay(10_000L)
        }
    }

    // Report state
    val reportReasons = listOf(
        "Inappropriate content or avatar",
        "Harassment, hate or spam",
        "Fake profile or impersonation",
        "Scam, fraud or suspicious",
        "Underage user",
        "Other violation"
    )
    var selectedReportReason by remember { mutableStateOf(reportReasons[0]) }
    var reportDetailText by remember { mutableStateOf("") }
    var reportProofUrl by remember { mutableStateOf("") }
    var isSubmittingReport by remember { mutableStateOf(false) }

    BackHandler {
        if (fullscreenPhotoUrl != null) {
            fullscreenPhotoUrl = null
        } else {
            onBackClick()
        }
    }

    val isFemale = liveUser.gender.equals("Female", ignoreCase = true) || liveUser.gender.equals("F", ignoreCase = true)
    val mascotRes = AvatarHelper.getDefaultMascotRes(liveUser.id, liveUser.gender, liveUser.numericId)

    val userAge = remember(liveUser.birthDate) {
        if (liveUser.birthDate.isBlank()) "23"
        else {
            try {
                val yearStr = liveUser.birthDate.split("-", "/", " ", ".").firstOrNull { it.length == 4 }
                val birthYear = yearStr?.toIntOrNull() ?: 2003
                (2026 - birthYear).coerceIn(18, 99).toString()
            } catch (e: Exception) {
                "23"
            }
        }
    }

    // Main photo + up to 4 extra photos (strict limit of max 4 extra photos)
    val displayPhotos = remember(liveUser.avatarUrl, liveUser.albumPhotos) {
        val list = mutableListOf<String>()
        if (liveUser.avatarUrl.isNotBlank()) {
            list.add(liveUser.avatarUrl)
        }
        val extra = liveUser.albumPhotos.filter { it.isNotBlank() && it != liveUser.avatarUrl }.take(4)
        list.addAll(extra)
        if (list.isEmpty()) {
            list.add("") // placeholder for default avatar
        }
        list
    }
    val pagerState = rememberPagerState(pageCount = { displayPhotos.size })

    val colors = AppTheme.colors
    val isDark = colors.isDark

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(colors.screenBg)
            .testTag("user_detail_screen_root")
    ) {
        // Main Scrollable Container
        Column(
            modifier = Modifier
                .fillMaxSize()
                .verticalScroll(scrollState)
                .padding(bottom = 100.dp)
        ) {
            // 1. Large Hero Profile Picture View with Horizontal Swipe Gallery (Height: 390dp)
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(390.dp)
                    .background(if (isDark) Color(0xFF18181C) else Color(0xFFE0E0E0))
            ) {
                // Horizontal Pager for swiping between Avatar + up to 4 Extra Photos
                HorizontalPager(
                    state = pagerState,
                    modifier = Modifier.fillMaxSize()
                ) { pageIndex ->
                    val photoUrl = displayPhotos.getOrElse(pageIndex) { "" }
                    Box(
                        modifier = Modifier
                            .fillMaxSize()
                            .clickable {
                                fullscreenPhotoUrl = photoUrl.ifBlank { "default_avatar" }
                            }
                    ) {
                        if (photoUrl.isBlank() || (pageIndex == 0 && photoUrl == liveUser.avatarUrl)) {
                            AvatarHelper.UserAvatarImage(
                                avatarUrl = liveUser.avatarUrl,
                                userId = liveUser.id,
                                gender = liveUser.gender,
                                numericId = liveUser.numericId,
                                showFrame = false,
                                shape = androidx.compose.ui.graphics.RectangleShape,
                                contentDescription = "${liveUser.name} photo ${pageIndex + 1}",
                                modifier = Modifier.fillMaxSize(),
                                contentScale = ContentScale.Crop
                            )
                        } else {
                            AsyncImage(
                                model = photoUrl,
                                contentDescription = "${liveUser.name} photo ${pageIndex + 1}",
                                modifier = Modifier.fillMaxSize(),
                                contentScale = ContentScale.Crop
                            )
                        }
                    }
                }

                // Top Gradient Scrim for Status Bar & Buttons
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(110.dp)
                        .background(
                            Brush.verticalGradient(
                                colors = listOf(Color.Black.copy(alpha = 0.55f), Color.Transparent)
                            )
                        )
                )

                // Photo Swipe Counter Pill on bottom-right (e.g. 1/5)
                if (displayPhotos.size > 1) {
                    Surface(
                        shape = RoundedCornerShape(12.dp),
                        color = Color.Black.copy(alpha = 0.6f),
                        modifier = Modifier
                            .align(Alignment.BottomEnd)
                            .padding(end = 18.dp, bottom = 20.dp)
                    ) {
                        Text(
                            text = "${pagerState.currentPage + 1}/${displayPhotos.size}",
                            color = Color.White,
                            fontSize = 12.sp,
                            fontWeight = FontWeight.Bold,
                            modifier = Modifier.padding(horizontal = 10.dp, vertical = 5.dp)
                        )
                    }
                }

                // Bottom Gradient Scrim & User Name displayed on the profile picture
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .align(Alignment.BottomStart)
                        .background(
                            Brush.verticalGradient(
                                colors = listOf(Color.Transparent, Color.Black.copy(alpha = 0.85f))
                            )
                        )
                        .padding(horizontal = 20.dp, vertical = 18.dp)
                ) {
                    Column {
                        // User Name in bottom-left on profile picture
                        Text(
                            text = liveUser.name.ifBlank { "QIVO User" },
                            fontSize = 28.sp,
                            fontWeight = FontWeight.ExtraBold,
                            color = Color.White
                        )

                        Spacer(modifier = Modifier.height(8.dp))

                        // Quick Badges on the image
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            // Gender + Age Chip
                            val genderBgColor = if (isFemale) Color(0xFFF8A4EC) else Color(0xFF72C2F8)
                            Surface(
                                shape = RoundedCornerShape(6.dp),
                                color = genderBgColor
                            ) {
                                Row(
                                    modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp),
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Icon(
                                        imageVector = if (isFemale) Icons.Default.Female else Icons.Default.Male,
                                        contentDescription = "Gender",
                                        tint = Color.Black,
                                        modifier = Modifier.size(13.dp)
                                    )
                                    Spacer(modifier = Modifier.width(3.dp))
                                    Text(
                                        text = userAge,
                                        fontSize = 12.sp,
                                        fontWeight = FontWeight.Bold,
                                        color = Color.Black
                                    )
                                }
                            }

                            // Country / Global Chip
                            if (liveUser.country.isNotBlank()) {
                                Surface(
                                    shape = RoundedCornerShape(6.dp),
                                    color = Color(0xFFB3FF00)
                                ) {
                                    Text(
                                        text = liveUser.country,
                                        fontSize = 12.sp,
                                        fontWeight = FontWeight.Bold,
                                        color = Color.Black,
                                        modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp)
                                    )
                                }
                            }
                        }
                    }
                }
            }

            // 2. Lower Part (Spacious Scrollable Details Section)
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 18.dp, vertical = 16.dp)
            ) {
                // Badges / Tags Row: Info Chips on Left, Follow Button on the RIGHT
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    // Info Chips Scrollable on the Left
                    Row(
                        modifier = Modifier
                            .weight(1f, fill = false)
                            .horizontalScroll(chipsScrollState),
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        // ID Chip with Clipboard Copy
                        Surface(
                            shape = RoundedCornerShape(10.dp),
                            color = if (isDark) Color(0xFF1E1E24) else Color(0xFFEFEFEF),
                            onClick = {
                                val clipboard = context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
                                val clip = ClipData.newPlainText("Numeric ID", liveUser.numericId.toString())
                                clipboard.setPrimaryClip(clip)
                                AppToast.show("Copied ID: ${liveUser.numericId}")
                            }
                        ) {
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                modifier = Modifier.padding(horizontal = 10.dp, vertical = 6.dp)
                            ) {
                                Text(
                                    text = "ID: ${if (liveUser.numericId > 0) liveUser.numericId else 98002946}",
                                    fontSize = 12.sp,
                                    fontWeight = FontWeight.SemiBold,
                                    color = colors.textSecondary
                                )
                                Spacer(modifier = Modifier.width(4.dp))
                                Icon(
                                    imageVector = Icons.Default.ContentCopy,
                                    contentDescription = "Copy ID",
                                    tint = colors.textSecondary,
                                    modifier = Modifier.size(13.dp)
                                )
                            }
                        }

                        // Status Chip: Online (Only shown when user is actually online in real-time)
                        if (liveUser.isOnline) {
                            Surface(
                                shape = RoundedCornerShape(10.dp),
                                color = if (isDark) Color(0xFF16331C) else Color(0xFFE8F5E9)
                            ) {
                                Row(
                                    verticalAlignment = Alignment.CenterVertically,
                                    modifier = Modifier.padding(horizontal = 10.dp, vertical = 6.dp)
                                ) {
                                    Box(
                                        modifier = Modifier
                                            .size(7.dp)
                                            .clip(CircleShape)
                                            .background(Color(0xFF4CAF50))
                                    )
                                    Spacer(modifier = Modifier.width(5.dp))
                                    Text(
                                        text = "Online",
                                        fontSize = 12.sp,
                                        fontWeight = FontWeight.Bold,
                                        color = if (isDark) Color(0xFF81C784) else Color(0xFF2E7D32)
                                    )
                                }
                            }
                        }

                        // Coin Seller Badge (if applicable)
                        if (liveUser.isCoinSeller) {
                            Surface(
                                shape = RoundedCornerShape(10.dp),
                                color = Color(0xFF00C853)
                            ) {
                                Text(
                                    text = "💰 Coin Seller",
                                    fontSize = 12.sp,
                                    fontWeight = FontWeight.Bold,
                                    color = Color.White,
                                    modifier = Modifier.padding(horizontal = 10.dp, vertical = 6.dp)
                                )
                            }
                        }

                        // Official Agent Badge (if applicable)
                        if (liveUser.isAgent) {
                            Surface(
                                shape = RoundedCornerShape(10.dp),
                                color = Color(0xFF7C4DFF)
                            ) {
                                Text(
                                    text = "⭐ Official Agent",
                                    fontSize = 12.sp,
                                    fontWeight = FontWeight.Bold,
                                    color = Color.White,
                                    modifier = Modifier.padding(horizontal = 10.dp, vertical = 6.dp)
                                )
                            }
                        }
                    }

                    // Right side action: Edit Profile button for self, or Follow button for other users
                    if (isCurrentUser) {
                        if (onOpenEditProfile != null) {
                            Spacer(modifier = Modifier.width(8.dp))
                            Surface(
                                shape = RoundedCornerShape(12.dp),
                                color = Color(0xFFFFB300),
                                modifier = Modifier.testTag("btn_user_detail_edit_chip"),
                                onClick = { onOpenEditProfile() }
                            ) {
                                Row(
                                    verticalAlignment = Alignment.CenterVertically,
                                    modifier = Modifier.padding(horizontal = 14.dp, vertical = 7.dp)
                                ) {
                                    Icon(
                                        imageVector = Icons.Default.Edit,
                                        contentDescription = "Edit Profile",
                                        tint = Color.Black,
                                        modifier = Modifier.size(16.dp)
                                    )
                                    Spacer(modifier = Modifier.width(6.dp))
                                    Text(
                                        text = "Edit Profile",
                                        fontSize = 13.sp,
                                        fontWeight = FontWeight.Bold,
                                        color = Color.Black
                                    )
                                }
                            }
                        }
                    } else if (liveUser.id != currentUserId) {
                        Spacer(modifier = Modifier.width(8.dp))
                        Surface(
                            shape = RoundedCornerShape(12.dp),
                            color = if (isFollowing) (if (isDark) Color(0xFF2A2A30) else Color(0xFFE0E0E0)) else Color(0xFFFFB300),
                            modifier = Modifier.testTag("btn_user_detail_follow"),
                            onClick = {
                                val prevFollowing = isFollowing
                                isFollowing = !prevFollowing
                                com.example.data.FollowDataCacheStore.updateFollowStatus(currentUserId, liveUser.id, !prevFollowing)
                                scope.launch {
                                    val (success, actualState) = profileService.toggleFollow(currentUserId, liveUser.id)
                                    if (success) {
                                        isFollowing = actualState
                                        com.example.data.FollowDataCacheStore.updateFollowStatus(currentUserId, liveUser.id, actualState)
                                        val msg = if (actualState) "Following ${liveUser.name}" else "Unfollowed ${liveUser.name}"
                                        AppToast.show(msg)
                                    } else {
                                        isFollowing = prevFollowing
                                        com.example.data.FollowDataCacheStore.updateFollowStatus(currentUserId, liveUser.id, prevFollowing)
                                        AppToast.show("Could not update follow status")
                                    }
                                }
                            }
                        ) {
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                modifier = Modifier.padding(horizontal = 14.dp, vertical = 7.dp)
                            ) {
                                Follow3DIcon(
                                    isFollowing = isFollowing,
                                    size = 18.dp
                                )
                                Spacer(modifier = Modifier.width(6.dp))
                                Text(
                                    text = if (isFollowing) "Following" else "Follow",
                                    fontSize = 13.sp,
                                    fontWeight = FontWeight.Bold,
                                    color = if (isFollowing) (if (isDark) Color.White else Color.Black) else Color.Black
                                )
                            }
                        }
                    }
                }

                Spacer(modifier = Modifier.height(18.dp))

                // Extra Photos Album Section (Limit 4 extra photos, tap to view / swipe)
                val extraAlbumPhotos = remember(liveUser.albumPhotos) {
                    liveUser.albumPhotos.filter { it.isNotBlank() }.take(4)
                }
                if (extraAlbumPhotos.isNotEmpty() || (isCurrentUser && onOpenEditProfile != null)) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            text = "Photo Gallery (${extraAlbumPhotos.size}/4)",
                            fontSize = 16.sp,
                            fontWeight = FontWeight.Bold,
                            color = colors.textPrimary
                        )
                        if (isCurrentUser && onOpenEditProfile != null) {
                            Text(
                                text = "Manage",
                                fontSize = 13.sp,
                                fontWeight = FontWeight.SemiBold,
                                color = Color(0xFFFFB300),
                                modifier = Modifier.clickable { onOpenEditProfile() }
                            )
                        }
                    }
                    Spacer(modifier = Modifier.height(10.dp))
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .horizontalScroll(rememberScrollState()),
                        horizontalArrangement = Arrangement.spacedBy(12.dp)
                    ) {
                        extraAlbumPhotos.forEachIndexed { index, photoUrl ->
                            Surface(
                                modifier = Modifier
                                    .size(80.dp)
                                    .clip(RoundedCornerShape(14.dp))
                                    .clickable { fullscreenPhotoUrl = photoUrl }
                                    .testTag("album_photo_$index"),
                                color = if (isDark) Color(0xFF222228) else Color(0xFFE8E8E8),
                                border = BorderStroke(1.dp, if (isDark) Color(0xFF2E2E38) else Color(0xFFE0E0E6))
                            ) {
                                AsyncImage(
                                    model = photoUrl,
                                    contentDescription = "Photo ${index + 1}",
                                    contentScale = ContentScale.Crop,
                                    modifier = Modifier.fillMaxSize()
                                )
                            }
                        }

                        if (isCurrentUser && extraAlbumPhotos.size < 4 && onOpenEditProfile != null) {
                            Surface(
                                modifier = Modifier
                                    .size(80.dp)
                                    .clip(RoundedCornerShape(14.dp))
                                    .clickable { onOpenEditProfile() }
                                    .testTag("album_add_photo_btn"),
                                color = if (isDark) Color(0xFF1E1E26) else Color(0xFFF2F2F7),
                                border = BorderStroke(1.dp, if (isDark) Color(0xFF383848) else Color(0xFFD4D4DE))
                            ) {
                                Column(
                                    modifier = Modifier.fillMaxSize(),
                                    horizontalAlignment = Alignment.CenterHorizontally,
                                    verticalArrangement = Arrangement.Center
                                ) {
                                    Icon(
                                        imageVector = Icons.Default.EditNote,
                                        contentDescription = "Add Photo",
                                        tint = colors.textSecondary,
                                        modifier = Modifier.size(26.dp)
                                    )
                                    Spacer(modifier = Modifier.height(4.dp))
                                    Text(
                                        text = "Add",
                                        fontSize = 11.sp,
                                        fontWeight = FontWeight.SemiBold,
                                        color = colors.textSecondary
                                    )
                                }
                            }
                        }
                    }
                    Spacer(modifier = Modifier.height(20.dp))
                }

                // 1. Looking For / Social Preference Banner
                if (isFieldSet(liveUser.socialPreferences)) {
                    Card(
                        colors = CardDefaults.cardColors(
                            containerColor = if (isDark) Color(0xFF231B2B) else Color(0xFFFFF0F5)
                        ),
                        shape = RoundedCornerShape(16.dp),
                        border = BorderStroke(1.dp, if (isDark) Color(0xFF422748) else Color(0xFFFFD1DF)),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Row(
                            modifier = Modifier.padding(16.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Box(
                                modifier = Modifier
                                    .size(44.dp)
                                    .clip(CircleShape)
                                    .background(Brush.linearGradient(listOf(Color(0xFFFF4081), Color(0xFFFF6E40)))),
                                contentAlignment = Alignment.Center
                            ) {
                                Icon(
                                    imageVector = Icons.Default.Favorite,
                                    contentDescription = "Social Goal",
                                    tint = Color.White,
                                    modifier = Modifier.size(22.dp)
                                )
                            }
                            Spacer(modifier = Modifier.width(14.dp))
                            Column(modifier = Modifier.weight(1f)) {
                                Text(
                                    text = "LOOKING FOR",
                                    fontSize = 11.sp,
                                    fontWeight = FontWeight.ExtraBold,
                                    color = Color(0xFFFF4081),
                                    letterSpacing = 1.sp
                                )
                                Spacer(modifier = Modifier.height(3.dp))
                                Text(
                                    text = liveUser.socialPreferences,
                                    fontSize = 15.sp,
                                    fontWeight = FontWeight.Bold,
                                    color = colors.textPrimary
                                )
                            }
                        }
                    }
                    Spacer(modifier = Modifier.height(18.dp))
                }

                // 2. Hobbies & Interests Tag Cloud
                val hobbiesList = remember(liveUser.hobbiesInterests) {
                    if (isFieldSet(liveUser.hobbiesInterests)) {
                        liveUser.hobbiesInterests
                            .split(",", ";", "•", "、", "/")
                            .map { it.trim() }
                            .filter { isFieldSet(it) }
                    } else {
                        emptyList()
                    }
                }
                if (hobbiesList.isNotEmpty()) {
                    Text(
                        text = "Hobbies & Interests",
                        fontSize = 16.sp,
                        fontWeight = FontWeight.Bold,
                        color = colors.textPrimary
                    )
                    Spacer(modifier = Modifier.height(10.dp))
                    Card(
                        colors = CardDefaults.cardColors(containerColor = colors.cardBg),
                        shape = RoundedCornerShape(16.dp),
                        border = BorderStroke(1.dp, if (isDark) Color(0xFF282832) else Color(0xFFEEEEF4)),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Column(modifier = Modifier.padding(16.dp)) {
                            hobbiesList.chunked(3).forEach { chunk ->
                                Row(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .padding(vertical = 4.dp),
                                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                                ) {
                                    chunk.forEach { hobby ->
                                        Surface(
                                            shape = RoundedCornerShape(18.dp),
                                            color = if (isDark) Color(0xFF242430) else Color(0xFFF1F1F7),
                                            border = BorderStroke(1.dp, if (isDark) Color(0xFF353545) else Color(0xFFE2E2EC))
                                        ) {
                                            Row(
                                                modifier = Modifier.padding(horizontal = 12.dp, vertical = 7.dp),
                                                verticalAlignment = Alignment.CenterVertically
                                            ) {
                                                Box(
                                                    modifier = Modifier
                                                        .size(6.dp)
                                                        .clip(CircleShape)
                                                        .background(Color(0xFFFFB300))
                                                )
                                                Spacer(modifier = Modifier.width(6.dp))
                                                Text(
                                                    text = hobby,
                                                    fontSize = 13.sp,
                                                    fontWeight = FontWeight.SemiBold,
                                                    color = colors.textPrimary
                                                )
                                            }
                                        }
                                    }
                                }
                            }
                        }
                    }
                    Spacer(modifier = Modifier.height(18.dp))
                }

                // 3. Lifestyle & Habits Section
                val lifestyleDetails = remember(liveUser) { getLifestyleDetails(liveUser) }
                if (lifestyleDetails.isNotEmpty()) {
                    Text(
                        text = "Lifestyle & Habits",
                        fontSize = 16.sp,
                        fontWeight = FontWeight.Bold,
                        color = colors.textPrimary
                    )
                    Spacer(modifier = Modifier.height(10.dp))
                    Card(
                        colors = CardDefaults.cardColors(containerColor = colors.cardBg),
                        shape = RoundedCornerShape(16.dp),
                        border = BorderStroke(1.dp, if (isDark) Color(0xFF282832) else Color(0xFFEEEEF4)),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Column(modifier = Modifier.padding(horizontal = 16.dp, vertical = 10.dp)) {
                            lifestyleDetails.forEachIndexed { index, item ->
                                ProfileDetailItemRow(
                                    icon = item.icon,
                                    iconGradient = item.iconGradient,
                                    label = item.label,
                                    value = item.value,
                                    textColor = colors.textPrimary,
                                    subColor = colors.textSecondary
                                )
                                if (index < lifestyleDetails.size - 1) {
                                    HorizontalDivider(
                                        color = colors.divider,
                                        modifier = Modifier.padding(vertical = 4.dp)
                                    )
                                }
                            }
                        }
                    }
                    Spacer(modifier = Modifier.height(18.dp))
                }

                // 4. Basic Info & Background Section
                val backgroundDetails = remember(liveUser) { getBackgroundDetails(liveUser) }
                if (backgroundDetails.isNotEmpty()) {
                    Text(
                        text = "About & Background",
                        fontSize = 16.sp,
                        fontWeight = FontWeight.Bold,
                        color = colors.textPrimary
                    )
                    Spacer(modifier = Modifier.height(10.dp))
                    Card(
                        colors = CardDefaults.cardColors(containerColor = colors.cardBg),
                        shape = RoundedCornerShape(16.dp),
                        border = BorderStroke(1.dp, if (isDark) Color(0xFF282832) else Color(0xFFEEEEF4)),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Column(modifier = Modifier.padding(horizontal = 16.dp, vertical = 10.dp)) {
                            backgroundDetails.forEachIndexed { index, item ->
                                ProfileDetailItemRow(
                                    icon = item.icon,
                                    iconGradient = item.iconGradient,
                                    label = item.label,
                                    value = item.value,
                                    textColor = colors.textPrimary,
                                    subColor = colors.textSecondary
                                )
                                if (index < backgroundDetails.size - 1) {
                                    HorizontalDivider(
                                        color = colors.divider,
                                        modifier = Modifier.padding(vertical = 4.dp)
                                    )
                                }
                            }
                        }
                    }
                    Spacer(modifier = Modifier.height(18.dp))
                }

                // 5. Complete Profile Callout (Shown when viewing own profile and fields are not filled yet)
                if (isCurrentUser && lifestyleDetails.isEmpty() && backgroundDetails.isEmpty() && hobbiesList.isEmpty() && onOpenEditProfile != null) {
                    Card(
                        colors = CardDefaults.cardColors(
                            containerColor = if (isDark) Color(0xFF242018) else Color(0xFFFFF9E6)
                        ),
                        shape = RoundedCornerShape(16.dp),
                        border = BorderStroke(1.dp, if (isDark) Color(0xFF4D3F1E) else Color(0xFFFFE082)),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Column(
                            modifier = Modifier.padding(18.dp),
                            horizontalAlignment = Alignment.CenterHorizontally
                        ) {
                            Icon(
                                imageVector = Icons.Default.AutoAwesome,
                                contentDescription = null,
                                tint = Color(0xFFFFB300),
                                modifier = Modifier.size(36.dp)
                            )
                            Spacer(modifier = Modifier.height(8.dp))
                            Text(
                                text = "Personalize Your Profile",
                                fontSize = 16.sp,
                                fontWeight = FontWeight.Bold,
                                color = colors.textPrimary,
                                textAlign = TextAlign.Center
                            )
                            Spacer(modifier = Modifier.height(4.dp))
                            Text(
                                text = "Add your hobbies, lifestyle habits, and what you are looking for to attract more friends.",
                                fontSize = 13.sp,
                                color = colors.textSecondary,
                                textAlign = TextAlign.Center
                            )
                            Spacer(modifier = Modifier.height(14.dp))
                            Button(
                                onClick = onOpenEditProfile,
                                colors = ButtonDefaults.buttonColors(containerColor = Color(0xFFFFB300), contentColor = Color.Black),
                                shape = RoundedCornerShape(20.dp)
                            ) {
                                Icon(
                                    imageVector = Icons.Default.Edit,
                                    contentDescription = null,
                                    modifier = Modifier.size(16.dp)
                                )
                                Spacer(modifier = Modifier.width(6.dp))
                                Text("Complete Details", fontWeight = FontWeight.Bold)
                            }
                        }
                    }
                    Spacer(modifier = Modifier.height(18.dp))
                }
            }
        }

        // 3. Bottom Action Bar: If viewing own profile, show full-width Edit Profile button. If other user, Chat + Call buttons
        Surface(
            modifier = Modifier
                .align(Alignment.BottomCenter)
                .fillMaxWidth()
                .navigationBarsPadding()
                .padding(horizontal = 18.dp, vertical = 12.dp),
            color = Color.Transparent
        ) {
            if (isCurrentUser) {
                Surface(
                    onClick = { onOpenEditProfile?.invoke() },
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(52.dp)
                        .testTag("btn_user_detail_edit_profile"),
                    shape = RoundedCornerShape(26.dp),
                    color = Color.Transparent,
                    shadowElevation = 4.dp
                ) {
                    Box(
                        modifier = Modifier
                            .fillMaxSize()
                            .background(
                                Brush.horizontalGradient(listOf(Color(0xFFFFD54F), Color(0xFFFFB300), Color(0xFFFF8F00))),
                                shape = RoundedCornerShape(26.dp)
                            ),
                        contentAlignment = Alignment.Center
                    ) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Icon(
                                imageVector = Icons.Default.Edit,
                                contentDescription = "Edit Profile",
                                tint = Color.Black,
                                modifier = Modifier.size(20.dp)
                            )
                            Spacer(modifier = Modifier.width(8.dp))
                            Text(
                                text = "Edit Profile",
                                fontSize = 16.sp,
                                fontWeight = FontWeight.Black,
                                color = Color.Black
                            )
                        }
                    }
                }
            } else {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    // Chat Button on the LEFT side at the bottom
                    Surface(
                        onClick = {
                            if (isBlockedByMe) {
                                AppToast.show("You blocked this user.")
                            } else if (isBlocked || isBlockedByThem) {
                                AppToast.show("You have been blocked.")
                            } else {
                                onStartChat(liveUser)
                            }
                        },
                        modifier = Modifier
                            .weight(1f)
                            .height(52.dp)
                            .testTag("btn_user_detail_chat"),
                        shape = RoundedCornerShape(26.dp),
                        color = Color.Transparent,
                        shadowElevation = 4.dp
                    ) {
                        Box(
                            modifier = Modifier
                                .fillMaxSize()
                                .background(
                                    if (isBlocked) Brush.horizontalGradient(listOf(Color.Gray, Color.DarkGray))
                                    else Brush.horizontalGradient(listOf(Color(0xFFFFD54F), Color(0xFFFFB300), Color(0xFFFF8F00))),
                                    shape = RoundedCornerShape(26.dp)
                                ),
                            contentAlignment = Alignment.Center
                        ) {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Icon(
                                    imageVector = Icons.AutoMirrored.Filled.Chat,
                                    contentDescription = "Chat",
                                    tint = Color.Black,
                                    modifier = Modifier.size(20.dp)
                                )
                                Spacer(modifier = Modifier.width(8.dp))
                                Text(
                                    text = if (isBlockedByMe) "Blocked by you" else if (isBlocked) "Blocked" else "Chat",
                                    fontSize = 16.sp,
                                    fontWeight = FontWeight.Black,
                                    color = Color.Black
                                )
                            }
                        }
                    }

                    // Voice Call Button beside it on the right (Amber/Gold gradient)
                    Surface(
                        onClick = {
                            if (isBlockedByMe) {
                                AppToast.show("You blocked this user.")
                            } else if (isBlocked || isBlockedByThem) {
                                AppToast.show("You have been blocked.")
                            } else {
                                val myGender = session?.gender ?: "Male"
                                val isMale = myGender.equals("Male", ignoreCase = true)
                                if (isMale && userCurrentCoins < 80L) {
                                    insufficientCoinsRequired = 80L
                                    showInsufficientCoinsDialog = true
                                    AppToast.show("Insufficient coins. 80 coins required for voice call.")
                                } else {
                                    launchDirectCall(
                                        context = context,
                                        targetUser = liveUser,
                                        callType = CallType.VOICE,
                                        onInsufficientCoins = {
                                            insufficientCoinsRequired = 80L
                                            showInsufficientCoinsDialog = true
                                        }
                                    )
                                }
                            }
                        },
                        modifier = Modifier
                            .size(52.dp)
                            .testTag("btn_user_detail_voice_call"),
                        shape = CircleShape,
                        color = Color.Transparent,
                        shadowElevation = 4.dp
                    ) {
                        Box(
                            modifier = Modifier
                                .fillMaxSize()
                                .background(
                                    if (isBlocked) Brush.linearGradient(listOf(Color.Gray, Color.DarkGray))
                                    else Brush.linearGradient(listOf(Color(0xFF66BB6A), Color(0xFF2E7D32))),
                                    shape = CircleShape
                                ),
                            contentAlignment = Alignment.Center
                        ) {
                            Icon(
                                imageVector = Icons.Default.Call,
                                contentDescription = "Voice Call",
                                tint = Color.White,
                                modifier = Modifier.size(24.dp)
                            )
                        }
                    }

                    // Video Call Button beside it on the right (Sunset Amber gradient)
                    Surface(
                        onClick = {
                            if (isBlockedByMe) {
                                AppToast.show("You blocked this user.")
                            } else if (isBlocked || isBlockedByThem) {
                                AppToast.show("You have been blocked.")
                            } else {
                                val myGender = session?.gender ?: "Male"
                                val isMale = myGender.equals("Male", ignoreCase = true)
                                if (isMale && userCurrentCoins < 160L) {
                                    insufficientCoinsRequired = 160L
                                    showInsufficientCoinsDialog = true
                                    AppToast.show("Insufficient coins. 160 coins required for video call.")
                                } else {
                                    launchDirectCall(
                                        context = context,
                                        targetUser = liveUser,
                                        callType = CallType.VIDEO,
                                        onInsufficientCoins = {
                                            insufficientCoinsRequired = 160L
                                            showInsufficientCoinsDialog = true
                                        }
                                    )
                                }
                            }
                        },
                        modifier = Modifier
                            .size(52.dp)
                            .testTag("btn_user_detail_video_call"),
                        shape = CircleShape,
                        color = Color.Transparent,
                        shadowElevation = 4.dp
                    ) {
                        Box(
                            modifier = Modifier
                                .fillMaxSize()
                                .background(
                                    if (isBlocked) Brush.linearGradient(listOf(Color.Gray, Color.DarkGray))
                                    else Brush.linearGradient(listOf(Color(0xFFFF7A50), Color(0xFFFF5232))),
                                    shape = CircleShape
                                ),
                            contentAlignment = Alignment.Center
                        ) {
                            Icon(
                                imageVector = Icons.Default.Videocam,
                                contentDescription = "Video Call",
                                tint = Color.White,
                                modifier = Modifier.size(24.dp)
                            )
                        }
                    }
                }
            }
        }

        // Sticky Header: Back Button, User Name (on scroll), and 3 Dots Menu (never scrolls away)
        val scrollProgress = (scrollState.value / 180f).coerceIn(0f, 1f)
        val isScrolled = scrollProgress > 0.05f

        Surface(
            modifier = Modifier
                .fillMaxWidth()
                .align(Alignment.TopCenter),
            color = if (isScrolled) colors.screenBg.copy(alpha = scrollProgress) else Color.Transparent,
            shadowElevation = if (scrollProgress > 0.75f) (if (isDark) 4.dp else 2.dp) else 0.dp
        ) {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .statusBarsPadding()
                    .padding(horizontal = 16.dp, vertical = 8.dp)
            ) {
                // Back Button (Always fixed and accessible on the left)
                Surface(
                    onClick = onBackClick,
                    shape = CircleShape,
                    color = if (scrollProgress > 0.55f) Color.Transparent else Color.Black.copy(alpha = 0.45f),
                    modifier = Modifier
                        .size(42.dp)
                        .align(Alignment.CenterStart)
                        .testTag("btn_user_detail_back")
                ) {
                    Box(contentAlignment = Alignment.Center) {
                        Icon(
                            imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                            contentDescription = "Back",
                            tint = if (scrollProgress > 0.55f) colors.textPrimary else Color.White
                        )
                    }
                }

                // Centered User Name in Header (Fades in seamlessly as user scrolls down)
                if (scrollProgress > 0.12f) {
                    Text(
                        text = liveUser.name.ifBlank { "User Profile" },
                        fontSize = 18.sp,
                        fontWeight = FontWeight.Bold,
                        color = colors.textPrimary.copy(alpha = scrollProgress),
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        textAlign = TextAlign.Center,
                        modifier = Modifier
                            .align(Alignment.Center)
                            .padding(horizontal = 80.dp)
                    )
                }

                // Right actions (Edit Profile if isCurrentUser, plus 3-Dots Options)
                Row(
                    modifier = Modifier.align(Alignment.CenterEnd),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    if (isCurrentUser && onOpenEditProfile != null) {
                        Surface(
                            onClick = onOpenEditProfile,
                            shape = CircleShape,
                            color = if (scrollProgress > 0.55f) Color.Transparent else Color.Black.copy(alpha = 0.45f),
                            modifier = Modifier
                                .size(42.dp)
                                .testTag("btn_user_detail_edit_top")
                        ) {
                            Box(contentAlignment = Alignment.Center) {
                                Icon(
                                    imageVector = Icons.Default.Edit,
                                    contentDescription = "Edit Profile",
                                    tint = if (scrollProgress > 0.55f) colors.textPrimary else Color.White
                                )
                            }
                        }
                    }

                    // 3 Dots Options Button (Always fixed and accessible on the right)
                    Box {
                        Surface(
                            onClick = { showOptionsMenu = true },
                            shape = CircleShape,
                            color = if (scrollProgress > 0.55f) Color.Transparent else Color.Black.copy(alpha = 0.45f),
                            modifier = Modifier
                                .size(42.dp)
                                .testTag("btn_user_detail_options")
                        ) {
                            Box(contentAlignment = Alignment.Center) {
                                Icon(
                                    imageVector = Icons.Default.MoreVert,
                                    contentDescription = "Options",
                                    tint = if (scrollProgress > 0.55f) colors.textPrimary else Color.White
                                )
                            }
                        }

                        // 3 Dots Options Dropdown Menu
                        DropdownMenu(
                            expanded = showOptionsMenu,
                            onDismissRequest = { showOptionsMenu = false },
                            modifier = Modifier.background(colors.cardBg)
                        ) {
                            if (isCurrentUser) {
                                if (onOpenEditProfile != null) {
                                    DropdownMenuItem(
                                        text = {
                                            Row(verticalAlignment = Alignment.CenterVertically) {
                                                Icon(
                                                    imageVector = Icons.Default.Edit,
                                                    contentDescription = null,
                                                    tint = Color(0xFFFFB300),
                                                    modifier = Modifier.size(20.dp)
                                                )
                                                Spacer(modifier = Modifier.width(10.dp))
                                                Text("Edit Profile", color = colors.textPrimary, fontWeight = FontWeight.Bold)
                                            }
                                        },
                                        onClick = {
                                            showOptionsMenu = false
                                            onOpenEditProfile()
                                        }
                                    )
                                }
                                DropdownMenuItem(
                                    text = {
                                        Row(verticalAlignment = Alignment.CenterVertically) {
                                            Icon(
                                                imageVector = Icons.Default.ContentCopy,
                                                contentDescription = null,
                                                tint = colors.textPrimary,
                                                modifier = Modifier.size(20.dp)
                                            )
                                            Spacer(modifier = Modifier.width(10.dp))
                                            Text("Copy ID (${liveUser.numericId})", color = colors.textPrimary, fontWeight = FontWeight.SemiBold)
                                        }
                                    },
                                    onClick = {
                                        showOptionsMenu = false
                                        val clipboard = context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
                                        val clip = ClipData.newPlainText("Numeric ID", liveUser.numericId.toString())
                                        clipboard.setPrimaryClip(clip)
                                        AppToast.show("Copied ID: ${liveUser.numericId}")
                                    }
                                )
                            } else {
                                if (isBlocked) {
                                    DropdownMenuItem(
                                        text = {
                                            Row(verticalAlignment = Alignment.CenterVertically) {
                                                Icon(
                                                    imageVector = Icons.Default.LockOpen,
                                                    contentDescription = null,
                                                    tint = Color(0xFF00C853),
                                                    modifier = Modifier.size(20.dp)
                                                )
                                                Spacer(modifier = Modifier.width(10.dp))
                                                Text("Unblock User", color = Color(0xFF00C853), fontWeight = FontWeight.Bold)
                                            }
                                        },
                                        onClick = {
                                            showOptionsMenu = false
                                            scope.launch {
                                                profileService.unblockUser(currentUserId, liveUser.id, context)
                                                isBlocked = false
                                                AppToast.show("${liveUser.name} has been unblocked")
                                            }
                                        }
                                    )
                                } else {
                                    DropdownMenuItem(
                                        text = {
                                            Row(verticalAlignment = Alignment.CenterVertically) {
                                                Icon(
                                                    imageVector = Icons.Default.Block,
                                                    contentDescription = null,
                                                    tint = Color(0xFFD32F2F),
                                                    modifier = Modifier.size(20.dp)
                                                )
                                                Spacer(modifier = Modifier.width(10.dp))
                                                Text("Block User", color = Color(0xFFD32F2F), fontWeight = FontWeight.Bold)
                                            }
                                        },
                                        onClick = {
                                            showOptionsMenu = false
                                            showBlockConfirmDialog = true
                                        }
                                    )
                                }

                                DropdownMenuItem(
                                    text = {
                                        Row(verticalAlignment = Alignment.CenterVertically) {
                                            Icon(
                                                imageVector = Icons.Default.Flag,
                                                contentDescription = null,
                                                tint = colors.textPrimary,
                                                modifier = Modifier.size(20.dp)
                                            )
                                            Spacer(modifier = Modifier.width(10.dp))
                                            Text("Report User", color = colors.textPrimary, fontWeight = FontWeight.SemiBold)
                                        }
                                    },
                                    onClick = {
                                        showOptionsMenu = false
                                        showReportDialog = true
                                    }
                                )

                                DropdownMenuItem(
                                    text = {
                                        Row(verticalAlignment = Alignment.CenterVertically) {
                                            Icon(
                                                imageVector = Icons.Default.ContentCopy,
                                                contentDescription = null,
                                                tint = colors.textPrimary,
                                                modifier = Modifier.size(20.dp)
                                            )
                                            Spacer(modifier = Modifier.width(10.dp))
                                            Text("Copy User ID", color = colors.textPrimary, fontWeight = FontWeight.SemiBold)
                                        }
                                    },
                                    onClick = {
                                        showOptionsMenu = false
                                        val clipboard = context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
                                        val clip = ClipData.newPlainText("Numeric ID", liveUser.numericId.toString())
                                        clipboard.setPrimaryClip(clip)
                                        AppToast.show("Copied ID: ${liveUser.numericId}")
                                    }
                                )
                            }
                        }
                    }
                }
            }
        }
    }

    // Fullscreen Photo Viewer (Supports Extra Album Photos & Profile Avatar)
    val currentFullscreenPhoto = fullscreenPhotoUrl
    if (currentFullscreenPhoto != null) {
        Dialog(
            onDismissRequest = { fullscreenPhotoUrl = null },
            properties = DialogProperties(
                usePlatformDefaultWidth = false,
                decorFitsSystemWindows = false
            )
        ) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .background(Color.Black)
                    .clickable { fullscreenPhotoUrl = null }
            ) {
                if (currentFullscreenPhoto.startsWith("http") || currentFullscreenPhoto.startsWith("data:")) {
                    AsyncImage(
                        model = currentFullscreenPhoto,
                        contentDescription = "Full Screen Photo",
                        placeholder = painterResource(id = mascotRes),
                        error = painterResource(id = mascotRes),
                        modifier = Modifier.fillMaxSize(),
                        contentScale = ContentScale.Fit
                    )
                } else if (liveUser.avatarUrl.isNotEmpty()) {
                    AsyncImage(
                        model = liveUser.avatarUrl,
                        contentDescription = liveUser.name,
                        placeholder = painterResource(id = mascotRes),
                        error = painterResource(id = mascotRes),
                        modifier = Modifier.fillMaxSize(),
                        contentScale = ContentScale.Fit
                    )
                } else {
                    Image(
                        painter = painterResource(id = mascotRes),
                        contentDescription = liveUser.name,
                        modifier = Modifier.fillMaxSize(),
                        contentScale = ContentScale.Fit
                    )
                }

                // Close / Back Icon Button Top-Right
                IconButton(
                    onClick = { fullscreenPhotoUrl = null },
                    modifier = Modifier
                        .align(Alignment.TopEnd)
                        .statusBarsPadding()
                        .padding(16.dp)
                        .background(Color.Black.copy(alpha = 0.6f), CircleShape)
                        .testTag("btn_close_fullscreen_photo")
                ) {
                    Icon(
                        imageVector = Icons.Default.Close,
                        contentDescription = "Close Fullscreen",
                        tint = Color.White
                    )
                }
            }
        }
    }

    // Block User Confirmation Dialog
    if (showBlockConfirmDialog) {
        AlertDialog(
            onDismissRequest = { showBlockConfirmDialog = false },
            containerColor = colors.cardBg,
            title = {
                Text(text = "Block ${liveUser.name}?", fontWeight = FontWeight.Bold, color = colors.textPrimary)
            },
            text = {
                Text(text = "You will no longer receive messages or see posts from ${liveUser.name}.", color = colors.textSecondary)
            },
            confirmButton = {
                Button(
                    onClick = {
                        showBlockConfirmDialog = false
                        if (!NetworkUtils.requireOnline(context)) {
                            return@Button
                        }
                        scope.launch {
                            profileService.blockUser(currentUserId, liveUser.id, context)
                            isBlocked = true
                            AppToast.show("${liveUser.name} has been blocked")
                            onBackClick()
                        }
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = Color(0xFFD32F2F))
                ) {
                    Text("Block", color = Color.White, fontWeight = FontWeight.Bold)
                }
            },
            dismissButton = {
                TextButton(onClick = { showBlockConfirmDialog = false }) {
                    Text("Cancel", color = colors.textSecondary)
                }
            }
        )
    }

    // Report User Dialog
    if (showReportDialog) {
        AlertDialog(
            onDismissRequest = { showReportDialog = false },
            containerColor = colors.cardBg,
            title = {
                Text(text = "Report ${liveUser.name}", fontWeight = FontWeight.Bold, color = colors.textPrimary)
            },
            text = {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .verticalScroll(rememberScrollState())
                ) {
                    Text(
                        text = "Why are you reporting this user?",
                        fontSize = 14.sp,
                        color = colors.textSecondary
                    )
                    Spacer(modifier = Modifier.height(10.dp))

                    reportReasons.forEach { reason ->
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            modifier = Modifier
                                .fillMaxWidth()
                                .clickable { selectedReportReason = reason }
                                .padding(vertical = 4.dp)
                        ) {
                            RadioButton(
                                selected = (selectedReportReason == reason),
                                onClick = { selectedReportReason = reason },
                                colors = RadioButtonDefaults.colors(selectedColor = Color(0xFFFFD600))
                            )
                            Spacer(modifier = Modifier.width(6.dp))
                            Text(text = reason, fontSize = 14.sp, color = colors.textPrimary)
                        }
                    }

                    Spacer(modifier = Modifier.height(8.dp))

                    OutlinedTextField(
                        value = reportDetailText,
                        onValueChange = { reportDetailText = it },
                        placeholder = { Text("Additional details or explanation...", fontSize = 13.sp, color = colors.textSecondary) },
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(80.dp),
                        colors = OutlinedTextFieldDefaults.colors(
                            focusedTextColor = colors.textPrimary,
                            unfocusedTextColor = colors.textPrimary,
                            focusedBorderColor = Color(0xFFFFD600),
                            unfocusedBorderColor = colors.cardBorder
                        )
                    )

                    Spacer(modifier = Modifier.height(8.dp))

                    OutlinedTextField(
                        value = reportProofUrl,
                        onValueChange = { reportProofUrl = it },
                        placeholder = { Text("Proof link / screenshot URL (optional)...", fontSize = 13.sp, color = colors.textSecondary) },
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth(),
                        colors = OutlinedTextFieldDefaults.colors(
                            focusedTextColor = colors.textPrimary,
                            unfocusedTextColor = colors.textPrimary,
                            focusedBorderColor = Color(0xFFFFD600),
                            unfocusedBorderColor = colors.cardBorder
                        )
                    )
                }
            },
            confirmButton = {
                Button(
                    onClick = {
                        if (!NetworkUtils.requireOnline(context)) {
                            return@Button
                        }
                        isSubmittingReport = true
                        scope.launch {
                            profileService.reportUser(
                                reporterId = currentUserId,
                                reporterName = session?.name ?: "User",
                                targetUserId = liveUser.id,
                                targetUserName = liveUser.name,
                                reason = selectedReportReason,
                                details = reportDetailText,
                                proofUrl = reportProofUrl,
                                context = context
                            )
                            isSubmittingReport = false
                            showReportDialog = false
                            reportDetailText = ""
                            reportProofUrl = ""
                            AppToast.show("Report submitted. Thank you for keeping our community safe.", isLong = true)
                        }
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = Color(0xFFFFD600)),
                    enabled = !isSubmittingReport
                ) {
                    Text("Submit Report", color = Color.Black, fontWeight = FontWeight.Bold)
                }
            },
            dismissButton = {
                TextButton(onClick = { showReportDialog = false }) {
                    Text("Cancel", color = colors.textSecondary)
                }
            }
        )
    }

    // Insufficient Coins Bottom Popup with direct Recharge Navigation
    if (showInsufficientCoinsDialog) {
        InsufficientCoinsBottomSheet(
            currentCoins = userCurrentCoins,
            requiredCoins = insufficientCoinsRequired,
            onDismiss = { showInsufficientCoinsDialog = false },
            onOpenWallet = {
                showInsufficientCoinsDialog = false
                onOpenRechargeWallet?.invoke()
            },
            onCoinsUpdated = { updatedCoins ->
                userCurrentCoins = updatedCoins
                UserSessionManager.saveCoins(context, updatedCoins)
            }
        )
    }
}

// Helper Composable for displaying profile key-value details with modern gradient icon badges
@Composable
private fun ProfileDetailItemRow(
    icon: ImageVector,
    iconGradient: List<Color>,
    label: String,
    value: String,
    textColor: Color,
    subColor: Color
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 7.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.SpaceBetween
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier.weight(1f)
        ) {
            Box(
                modifier = Modifier
                    .size(32.dp)
                    .clip(RoundedCornerShape(9.dp))
                    .background(Brush.linearGradient(iconGradient)),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    imageVector = icon,
                    contentDescription = label,
                    tint = Color.White,
                    modifier = Modifier.size(17.dp)
                )
            }
            Spacer(modifier = Modifier.width(12.dp))
            Text(
                text = label,
                fontSize = 14.sp,
                fontWeight = FontWeight.Medium,
                color = subColor
            )
        }
        Text(
            text = value,
            fontSize = 14.sp,
            fontWeight = FontWeight.SemiBold,
            color = textColor,
            textAlign = TextAlign.End,
            modifier = Modifier.padding(start = 8.dp)
        )
    }
}

// Data class representing a profile item to show
private data class ProfileDetailItem(
    val icon: ImageVector,
    val iconGradient: List<Color>,
    val label: String,
    val value: String
)

private fun isFieldSet(value: String?): Boolean {
    if (value.isNullOrBlank()) return false
    val trimmed = value.trim()
    return trimmed.isNotEmpty() &&
            !trimmed.equals("Choose", ignoreCase = true) &&
            !trimmed.equals("Not set", ignoreCase = true) &&
            !trimmed.equals("None", ignoreCase = true) &&
            !trimmed.equals("Select", ignoreCase = true)
}

// Lifestyle & Habits list
private fun getLifestyleDetails(user: UserProfile): List<ProfileDetailItem> {
    val items = mutableListOf<ProfileDetailItem>()

    if (isFieldSet(user.exercise)) {
        items.add(ProfileDetailItem(Icons.Default.DirectionsRun, listOf(Color(0xFF26A69A), Color(0xFF00897B)), "Exercise", user.exercise))
    }
    if (isFieldSet(user.dietaryHabit)) {
        items.add(ProfileDetailItem(Icons.Default.Restaurant, listOf(Color(0xFF66BB6A), Color(0xFF388E3C)), "Dietary Habit", user.dietaryHabit))
    }
    if (isFieldSet(user.sleepHabit)) {
        items.add(ProfileDetailItem(Icons.Default.NightlightRound, listOf(Color(0xFF5C6BC0), Color(0xFF3949AB)), "Sleep Habit", user.sleepHabit))
    }
    if (isFieldSet(user.pets)) {
        items.add(ProfileDetailItem(Icons.Default.Pets, listOf(Color(0xFFFF7043), Color(0xFFF4511E)), "Pets", user.pets))
    }
    if (isFieldSet(user.smoking)) {
        items.add(ProfileDetailItem(Icons.Default.SmokingRooms, listOf(Color(0xFF78909C), Color(0xFF455A64)), "Smoking", user.smoking))
    }
    if (isFieldSet(user.liquor)) {
        items.add(ProfileDetailItem(Icons.Default.LocalBar, listOf(Color(0xFFAB47BC), Color(0xFF7B1FA2)), "Liquor", user.liquor))
    }
    if (isFieldSet(user.musicPreference)) {
        items.add(ProfileDetailItem(Icons.Default.MusicNote, listOf(Color(0xFFEC407A), Color(0xFFC2185B)), "Music", user.musicPreference))
    }
    if (isFieldSet(user.superpower)) {
        items.add(ProfileDetailItem(Icons.Default.AutoAwesome, listOf(Color(0xFFFFB300), Color(0xFFF57C00)), "Superpower", user.superpower))
    }

    return items
}

// Basic background and personal attributes list
private fun getBackgroundDetails(user: UserProfile): List<ProfileDetailItem> {
    val items = mutableListOf<ProfileDetailItem>()

    if (isFieldSet(user.country)) {
        items.add(ProfileDetailItem(Icons.Default.Public, listOf(Color(0xFF42A5F5), Color(0xFF1E88E5)), "Country / Region", user.country))
    }
    if (isFieldSet(user.birthDate)) {
        val ageVal = try {
            val yearStr = user.birthDate.split("-", "/", " ", ".").firstOrNull { it.length == 4 }
            val birthYear = yearStr?.toIntOrNull() ?: 2003
            (2026 - birthYear).coerceIn(18, 99).toString()
        } catch (e: Exception) {
            "23"
        }
        items.add(ProfileDetailItem(Icons.Default.Cake, listOf(Color(0xFFFF5722), Color(0xFFE64A19)), "Age", "$ageVal years"))
    }
    if (isFieldSet(user.gender)) {
        items.add(
            ProfileDetailItem(
                if (user.gender.equals("Female", true)) Icons.Default.Female else Icons.Default.Male,
                if (user.gender.equals("Female", true)) listOf(Color(0xFFEC407A), Color(0xFFD81B60)) else listOf(Color(0xFF42A5F5), Color(0xFF1976D2)),
                "Gender",
                user.gender
            )
        )
    }
    if (isFieldSet(user.height)) {
        items.add(ProfileDetailItem(Icons.Default.Height, listOf(Color(0xFF26C6DA), Color(0xFF00ACC1)), "Height", user.height))
    }
    if (isFieldSet(user.languages)) {
        items.add(ProfileDetailItem(Icons.Default.Language, listOf(Color(0xFF7E57C2), Color(0xFF5E35B1)), "Languages", user.languages))
    }
    if (isFieldSet(user.relationshipStatus)) {
        items.add(ProfileDetailItem(Icons.Default.Favorite, listOf(Color(0xFFEF5350), Color(0xFFE53935)), "Relationship", user.relationshipStatus))
    }
    if (isFieldSet(user.occupation)) {
        items.add(ProfileDetailItem(Icons.Default.Work, listOf(Color(0xFFFFA726), Color(0xFFFB8C00)), "Occupation", user.occupation))
    }
    if (isFieldSet(user.education)) {
        items.add(ProfileDetailItem(Icons.Default.School, listOf(Color(0xFF5C6BC0), Color(0xFF3F51B5)), "Education", user.education))
    }
    if (isFieldSet(user.personalityType)) {
        items.add(ProfileDetailItem(Icons.Default.Psychology, listOf(Color(0xFF8D6E63), Color(0xFF5D4037)), "Personality", user.personalityType))
    }
    if (isFieldSet(user.horoscopes)) {
        items.add(ProfileDetailItem(Icons.Default.Star, listOf(Color(0xFFFFCA28), Color(0xFFFFB300)), "Horoscope", user.horoscopes))
    }

    return items
}

private fun getSetProfileDetails(user: UserProfile): List<ProfileDetailItem> {
    return getBackgroundDetails(user) + getLifestyleDetails(user)
}

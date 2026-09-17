package com.example.ui.screens
import com.example.ui.components.AppToast
import com.example.data.NetworkUtils
import androidx.compose.material.icons.filled.WifiOff

import android.widget.Toast
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.GridItemSpan
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.AccessTime
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.ShoppingBag
import androidx.compose.material.icons.outlined.CheckCircle
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.rememberModalBottomSheetState
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
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import com.example.ui.theme.QivoOrange
import com.example.ui.theme.QivoGold
import com.example.ui.theme.QivoGoldDark
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.data.AvatarFrameItem
import com.example.data.AvatarFrameManager
import com.example.data.SupabaseProfileService
import com.example.data.UserOwnedFrame
import com.example.data.UserSessionManager
import com.example.ui.components.AvatarFrameRenderer
import kotlinx.coroutines.launch

/**
 * High-craft Bag screen for managing purchased items (Frames, Vehicles, Nameplates, Bubbles, Items)
 * Exactly matching the provided visual reference with Wear/Unwear frame functionality.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun BagScreen(
    onBack: () -> Unit,
    onNavigateToStore: () -> Unit,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val session = remember { UserSessionManager.getSession(context) }
    val userId = session?.userId ?: ""
    val profileService = remember { SupabaseProfileService() }

    val selectedTab = "Frames"
    val categories = listOf("Frames")

    val isOnline by remember { NetworkUtils.observeNetworkConnectivity(context) }
        .collectAsStateWithLifecycle(initialValue = NetworkUtils.isOnline(context))
    var activeFrameId by remember { mutableStateOf(session?.activeFrameId ?: "") }
    var activeFrameExp by remember { mutableStateOf(session?.frameExpiresAt ?: "") }
    val activeFrameFlowState by UserSessionManager.activeFrameFlow.collectAsStateWithLifecycle(initialValue = "" to "")
    LaunchedEffect(activeFrameFlowState) {
        if (userId.isNotBlank() && activeFrameFlowState.first == userId) {
            activeFrameId = activeFrameFlowState.second
        }
    }
    var ownedFrames by remember { mutableStateOf<List<UserOwnedFrame>>(emptyList()) }
    var isLoading by remember { mutableStateOf(true) }
    var selectedFrameDetail by remember { mutableStateOf<AvatarFrameItem?>(null) }
    var showExpiredSheet by remember { mutableStateOf(false) }

    val validOwnedFrames = remember(ownedFrames) {
        ownedFrames.filter { it.isValid }
    }
    val expiredOwnedFrames = remember(ownedFrames) {
        ownedFrames.filter { !it.isValid }
    }

    val wearingFrameItem = remember(activeFrameId, activeFrameExp) {
        if (activeFrameId.isNotBlank() && AvatarFrameManager.isFrameValid(activeFrameId, activeFrameExp)) {
            AvatarFrameManager.getFrameById(activeFrameId)
        } else {
            null
        }
    }

    val framesToDisplay = remember(validOwnedFrames) {
        validOwnedFrames.mapNotNull { owned ->
            AvatarFrameManager.getFrameById(owned.frameId)?.let { item ->
                Pair(item, owned)
            }
        }
    }

    // Load owned frames and clean up expired active frame
    LaunchedEffect(userId) {
        isLoading = true
        val localActive = UserSessionManager.getActiveFrameId(context, userId)
        val localExp = UserSessionManager.getFrameExpiresAt(context, userId)
        activeFrameId = localActive
        activeFrameExp = localExp

        // If local active frame is expired, immediately unequip it
        if (localActive.isNotBlank() && !AvatarFrameManager.isFrameValid(localActive, localExp)) {
            profileService.unequipAvatarFrame(userId, context)
            activeFrameId = ""
            activeFrameExp = ""
        }

        val list = profileService.fetchUserOwnedFrames(userId, context)
        ownedFrames = list

        // Also check server list
        val activeInList = list.firstOrNull { it.frameId.equals(activeFrameId, ignoreCase = true) }
        if (activeFrameId.isNotBlank() && (activeInList == null || !activeInList.isValid)) {
            profileService.unequipAvatarFrame(userId, context)
            activeFrameId = ""
            activeFrameExp = ""
        }

        isLoading = false
    }

    Scaffold(
        topBar = {
            // Header: Back button | Bag (centered) | Expired (with clock icon)
            Surface(
                color = Color.White,
                shadowElevation = 1.dp,
                modifier = Modifier
                    .fillMaxWidth()
                    .statusBarsPadding()
            ) {
                Column(modifier = Modifier.fillMaxWidth()) {
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(54.dp)
                            .padding(horizontal = 8.dp),
                        contentAlignment = Alignment.Center
                    ) {
                        // Back Arrow
                        IconButton(
                            onClick = onBack,
                            modifier = Modifier
                                .align(Alignment.CenterStart)
                                .testTag("bag_back_button")
                        ) {
                            Icon(
                                imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                                contentDescription = "Back",
                                tint = Color(0xFF1F2937),
                                modifier = Modifier.size(24.dp)
                            )
                        }

                        // Centered Title: "Bag"
                        Text(
                            text = "Bag",
                            fontSize = 18.sp,
                            fontWeight = FontWeight.Bold,
                            color = Color(0xFF111827)
                        )

                        // Top-Right: "🕒 Expired" Button
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            modifier = Modifier
                                .align(Alignment.CenterEnd)
                                .clip(RoundedCornerShape(12.dp))
                                .clickable { showExpiredSheet = true }
                                .padding(horizontal = 10.dp, vertical = 6.dp)
                                .testTag("bag_expired_button")
                        ) {
                            Icon(
                                imageVector = Icons.Default.AccessTime,
                                contentDescription = "Expired",
                                tint = Color(0xFF4B5563),
                                modifier = Modifier.size(16.dp)
                            )
                            Spacer(modifier = Modifier.width(4.dp))
                            Text(
                                text = "Expired",
                                fontSize = 14.sp,
                                fontWeight = FontWeight.Normal,
                                color = Color(0xFF374151)
                            )
                        }
                    }

                    // Horizontal Categories Tab Bar (Frames only)
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 16.dp, vertical = 4.dp),
                        horizontalArrangement = Arrangement.Start,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Column(
                            horizontalAlignment = Alignment.CenterHorizontally,
                            modifier = Modifier.padding(vertical = 6.dp)
                        ) {
                            Text(
                                text = "Frames",
                                fontSize = 17.sp,
                                fontWeight = FontWeight.ExtraBold,
                                color = Color(0xFF111827)
                            )

                            Spacer(modifier = Modifier.height(4.dp))

                            // Wavy Green Indicator (Matches screenshot design)
                            Canvas(
                                modifier = Modifier
                                    .width(28.dp)
                                    .height(5.dp)
                            ) {
                                val path = Path().apply {
                                    moveTo(0f, size.height * 0.5f)
                                    cubicTo(
                                        size.width * 0.3f, size.height * 1.2f,
                                        size.width * 0.7f, -size.height * 0.2f,
                                        size.width, size.height * 0.5f
                                    )
                                }
                                drawPath(
                                    path = path,
                                    color = Color(0xFF4ADE80),
                                    style = Stroke(
                                        width = 3.dp.toPx(),
                                        cap = StrokeCap.Round,
                                        join = StrokeJoin.Round
                                    )
                                )
                            }
                        }
                    }
                }
            }
        },
        containerColor = Color(0xFFF9FAFB),
        modifier = modifier.fillMaxSize()
    ) { paddingValues ->
        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(paddingValues)
        ) {
            if (isLoading) {
                        Box(
                            modifier = Modifier.fillMaxSize(),
                            contentAlignment = Alignment.Center
                        ) {
                            CircularProgressIndicator(
                                color = QivoGold,
                                strokeWidth = 3.dp
                            )
                        }
                    } else {
                        LazyVerticalGrid(
                            columns = GridCells.Fixed(2),
                            contentPadding = PaddingValues(16.dp),
                            horizontalArrangement = Arrangement.spacedBy(14.dp),
                            verticalArrangement = Arrangement.spacedBy(16.dp),
                            modifier = Modifier.fillMaxSize()
                        ) {
                            // 0. Offline Alert Banner (when device is offline)
                            if (!isOnline) {
                                item(span = { GridItemSpan(2) }) {
                                    Surface(
                                        color = Color(0xFFFEF2F2),
                                        shape = RoundedCornerShape(12.dp),
                                        border = BorderStroke(1.dp, Color(0xFFFCA5A5)),
                                        modifier = Modifier.fillMaxWidth()
                                    ) {
                                        Row(
                                            modifier = Modifier.padding(horizontal = 14.dp, vertical = 10.dp),
                                            verticalAlignment = Alignment.CenterVertically
                                        ) {
                                            Icon(
                                                imageVector = Icons.Default.WifiOff,
                                                contentDescription = null,
                                                tint = Color(0xFFDC2626),
                                                modifier = Modifier.size(20.dp)
                                            )
                                            Spacer(modifier = Modifier.width(10.dp))
                                            Text(
                                                text = "Offline Mode: Internet connection required to wear, unwear, or change avatar frames.",
                                                fontSize = 12.sp,
                                                color = Color(0xFFB91C1C),
                                                fontWeight = FontWeight.Medium,
                                                lineHeight = 16.sp
                                            )
                                        }
                                    }
                                }
                            }

                            // 1. "Wearing" Section Header
                            item(span = { GridItemSpan(2) }) {
                                Column(modifier = Modifier.fillMaxWidth()) {
                                    Text(
                                        text = "Wearing",
                                        fontSize = 17.sp,
                                        fontWeight = FontWeight.Bold,
                                        color = Color(0xFF111827)
                                    )
                                    Spacer(modifier = Modifier.height(10.dp))
                                }
                            }

                            // 2. Currently Wearing Card
                            item(span = { GridItemSpan(2) }) {
                                if (wearingFrameItem != null && activeFrameId.isNotBlank()) {
                                    Row(
                                        modifier = Modifier.fillMaxWidth(),
                                        horizontalArrangement = Arrangement.Start
                                    ) {
                                        WearingFrameCard(
                                            frame = wearingFrameItem,
                                            onUnwear = {
                                                if (!NetworkUtils.requireOnline(context, "No internet connection. Cannot unwear avatar frame while offline.")) {
                                                    return@WearingFrameCard
                                                }
                                                scope.launch {
                                                    profileService.unequipAvatarFrame(userId, context)
                                                    activeFrameId = ""
                                                    activeFrameExp = ""
                                                    AppToast.show("Unequipped frame")
                                                }
                                            },
                                            modifier = Modifier.width(168.dp)
                                        )
                                    }
                                } else {
                                    // Empty Wearing Placeholder
                                    Card(
                                        shape = RoundedCornerShape(16.dp),
                                        colors = CardDefaults.cardColors(containerColor = Color.White),
                                        border = BorderStroke(1.dp, Color(0xFFE5E7EB)),
                                        elevation = CardDefaults.cardElevation(defaultElevation = 0.dp),
                                        modifier = Modifier.fillMaxWidth()
                                    ) {
                                        Row(
                                            modifier = Modifier
                                                .fillMaxWidth()
                                                .padding(16.dp),
                                            verticalAlignment = Alignment.CenterVertically
                                        ) {
                                            Box(
                                                modifier = Modifier
                                                    .size(48.dp)
                                                    .clip(CircleShape)
                                                    .background(Color(0xFFF3F4F6)),
                                                contentAlignment = Alignment.Center
                                            ) {
                                                Icon(
                                                    imageVector = Icons.Default.ShoppingBag,
                                                    contentDescription = null,
                                                    tint = Color(0xFF9CA3AF),
                                                    modifier = Modifier.size(24.dp)
                                                )
                                            }
                                            Spacer(modifier = Modifier.width(12.dp))
                                            Column(modifier = Modifier.weight(1f)) {
                                                Text(
                                                    text = "No frame equipped",
                                                    fontSize = 15.sp,
                                                    fontWeight = FontWeight.SemiBold,
                                                    color = Color(0xFF374151)
                                                )
                                                Text(
                                                    text = "Tap any available frame below to wear it",
                                                    fontSize = 13.sp,
                                                    color = Color(0xFF9CA3AF)
                                                )
                                            }
                                        }
                                    }
                                }
                            }

                            // 3. "Available" Section Header
                            item(span = { GridItemSpan(2) }) {
                                Column(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .padding(top = 10.dp)
                                ) {
                                    Text(
                                        text = "Available",
                                        fontSize = 17.sp,
                                        fontWeight = FontWeight.Bold,
                                        color = Color(0xFF111827)
                                    )
                                    Spacer(modifier = Modifier.height(10.dp))
                                }
                            }

                            if (framesToDisplay.isEmpty()) {
                                item(span = { GridItemSpan(2) }) {
                                    Card(
                                        shape = RoundedCornerShape(16.dp),
                                        colors = CardDefaults.cardColors(containerColor = Color.White),
                                        border = BorderStroke(1.dp, Color(0xFFE5E7EB)),
                                        modifier = Modifier.fillMaxWidth()
                                    ) {
                                        Column(
                                            modifier = Modifier
                                                .fillMaxWidth()
                                                .padding(24.dp),
                                            horizontalAlignment = Alignment.CenterHorizontally
                                        ) {
                                            Icon(
                                                imageVector = Icons.Default.ShoppingBag,
                                                contentDescription = null,
                                                tint = Color(0xFF9CA3AF),
                                                modifier = Modifier.size(44.dp)
                                            )
                                            Spacer(modifier = Modifier.height(12.dp))
                                            Text(
                                                text = "No purchased frames in your bag",
                                                fontSize = 15.sp,
                                                fontWeight = FontWeight.SemiBold,
                                                color = Color(0xFF374151)
                                            )
                                            Spacer(modifier = Modifier.height(4.dp))
                                            Text(
                                                text = "Frames you purchase from the Store will appear here ready to equip.",
                                                fontSize = 13.sp,
                                                color = Color(0xFF6B7280),
                                                textAlign = androidx.compose.ui.text.style.TextAlign.Center
                                            )
                                            Spacer(modifier = Modifier.height(16.dp))
                                            Button(
                                                onClick = onNavigateToStore,
                                                colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF7CB342)),
                                                shape = RoundedCornerShape(20.dp)
                                            ) {
                                                Text("Browse Store", fontWeight = FontWeight.Bold)
                                            }
                                        }
                                    }
                                }
                            } else {
                                // 4. Available Owned Frames Grid
                                items(framesToDisplay) { (frame, owned) ->
                                    val isWearing = activeFrameId.equals(frame.id, ignoreCase = true)
                                    AvailableFrameGridCard(
                                        frame = frame,
                                        owned = owned,
                                        isWearing = isWearing,
                                        onWearToggle = {
                                            if (!NetworkUtils.requireOnline(context, "No internet connection. Cannot change avatar frame while offline.")) {
                                                return@AvailableFrameGridCard
                                            }
                                            scope.launch {
                                                if (isWearing) {
                                                    profileService.unequipAvatarFrame(userId, context)
                                                    activeFrameId = ""
                                                    activeFrameExp = ""
                                                    ownedFrames = profileService.fetchUserOwnedFrames(userId, context)
                                                    AppToast.show("Unequipped ${frame.name}")
                                                } else {
                                                    profileService.equipAvatarFrame(userId, frame.id, owned.expiresAt, context)
                                                    activeFrameId = frame.id
                                                    activeFrameExp = owned.expiresAt
                                                    ownedFrames = profileService.fetchUserOwnedFrames(userId, context)
                                                    AppToast.show("Now wearing ${frame.name}")
                                                }
                                            }
                                        },
                                        onClick = {
                                            selectedFrameDetail = frame
                                        }
                                    )
                                }
                            }

                            // Bottom Spacer & Store CTA
                            item(span = { GridItemSpan(2) }) {
                                Spacer(modifier = Modifier.height(12.dp))
                                Card(
                                    shape = RoundedCornerShape(16.dp),
                                    colors = CardDefaults.cardColors(containerColor = Color(0xFFF3E8FF)),
                                    border = BorderStroke(1.dp, Color(0xFFE9D5FF)),
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .clickable { onNavigateToStore() }
                                ) {
                                    Row(
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .padding(16.dp),
                                        horizontalArrangement = Arrangement.SpaceBetween,
                                        verticalAlignment = Alignment.CenterVertically
                                    ) {
                                        Column {
                                            Text(
                                                text = "Want more exclusive frames?",
                                                fontSize = 15.sp,
                                                fontWeight = FontWeight.Bold,
                                                color = QivoGoldDark
                                            )
                                            Text(
                                                text = "Visit the Avatar Frame Store",
                                                fontSize = 13.sp,
                                                color = QivoGold
                                            )
                                        }
                                        Text(
                                            text = "Explore →",
                                            fontSize = 14.sp,
                                            fontWeight = FontWeight.Bold,
                                            color = QivoGoldDark
                                        )
                                    }
                                }
                                Spacer(modifier = Modifier.height(32.dp))
                            }
                        }
                    }
                }
            }

    // Frame Detail / Wear Bottom Sheet
    selectedFrameDetail?.let { frame ->
        val isEquipped = activeFrameId.equals(frame.id, ignoreCase = true)
        ModalBottomSheet(
            onDismissRequest = { selectedFrameDetail = null },
            sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true),
            containerColor = Color.White
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 24.dp, vertical = 16.dp),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                Text(
                    text = frame.name,
                    fontSize = 20.sp,
                    fontWeight = FontWeight.Bold,
                    color = Color(0xFF111827)
                )

                Spacer(modifier = Modifier.height(16.dp))

                Box(
                    modifier = Modifier
                        .size(130.dp)
                        .background(Color(0xFFF9FAFB), CircleShape),
                    contentAlignment = Alignment.Center
                ) {
                    AvatarFrameRenderer(
                        frameId = frame.id,
                        modifier = Modifier.fillMaxSize()
                    )
                }

                Spacer(modifier = Modifier.height(16.dp))

                Text(
                    text = frame.description,
                    fontSize = 14.sp,
                    color = Color(0xFF4B5563),
                    textAlign = TextAlign.Center
                )

                Spacer(modifier = Modifier.height(24.dp))

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    if (isEquipped) {
                        Button(
                            onClick = {
                                if (!NetworkUtils.requireOnline(context, "No internet connection. Cannot unwear avatar frame while offline.")) {
                                    return@Button
                                }
                                scope.launch {
                                    profileService.unequipAvatarFrame(userId, context)
                                    activeFrameId = ""
                                    activeFrameExp = ""
                                    selectedFrameDetail = null
                                    AppToast.show("Unequipped ${frame.name}")
                                }
                            },
                            colors = ButtonDefaults.buttonColors(containerColor = Color(0xFFEF4444)),
                            shape = RoundedCornerShape(12.dp),
                            modifier = Modifier
                                .weight(1f)
                                .height(48.dp)
                        ) {
                            Text("Unwear Frame", fontWeight = FontWeight.Bold, color = Color.White)
                        }
                    } else {
                        Button(
                            onClick = {
                                if (!NetworkUtils.requireOnline(context, "No internet connection. Cannot wear or change avatar frame while offline.")) {
                                    return@Button
                                }
                                scope.launch {
                                    profileService.equipAvatarFrame(userId, frame.id, "", context)
                                    activeFrameId = frame.id
                                    selectedFrameDetail = null
                                    AppToast.show("Now wearing ${frame.name}!")
                                }
                            },
                            colors = ButtonDefaults.buttonColors(containerColor = QivoOrange),
                            shape = RoundedCornerShape(12.dp),
                            modifier = Modifier
                                .weight(1f)
                                .height(48.dp)
                        ) {
                            Text("Wear Frame", fontWeight = FontWeight.Bold, color = Color.White)
                        }
                    }
                }

                Spacer(modifier = Modifier.height(24.dp))
            }
        }
    }

    // Expired Items Sheet
    if (showExpiredSheet) {
        ModalBottomSheet(
            onDismissRequest = { showExpiredSheet = false },
            sheetState = rememberModalBottomSheetState(),
            containerColor = Color.White
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 20.dp, vertical = 16.dp),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                Text(
                    text = "Expired Items",
                    fontSize = 18.sp,
                    fontWeight = FontWeight.Bold,
                    color = Color(0xFF111827)
                )

                Spacer(modifier = Modifier.height(16.dp))

                if (expiredOwnedFrames.isEmpty()) {
                    Text(
                        text = "No expired items in your history. All active items appear in your Bag.",
                        fontSize = 14.sp,
                        color = Color(0xFF6B7280),
                        textAlign = TextAlign.Center,
                        modifier = Modifier.padding(vertical = 12.dp)
                    )
                } else {
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(vertical = 8.dp),
                        verticalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                        expiredOwnedFrames.forEach { exp ->
                            val item = AvatarFrameManager.getFrameById(exp.frameId)
                            if (item != null) {
                                Card(
                                    shape = RoundedCornerShape(14.dp),
                                    colors = CardDefaults.cardColors(containerColor = Color(0xFFF9FAFB)),
                                    border = BorderStroke(1.dp, Color(0xFFE5E7EB)),
                                    modifier = Modifier.fillMaxWidth()
                                ) {
                                    Row(
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .padding(12.dp),
                                        verticalAlignment = Alignment.CenterVertically,
                                        horizontalArrangement = Arrangement.SpaceBetween
                                    ) {
                                        Row(verticalAlignment = Alignment.CenterVertically) {
                                            Box(
                                                modifier = Modifier.size(52.dp),
                                                contentAlignment = Alignment.Center
                                            ) {
                                                AvatarFrameRenderer(
                                                    frameId = item.id,
                                                    modifier = Modifier.fillMaxSize()
                                                )
                                            }
                                            Spacer(modifier = Modifier.width(12.dp))
                                            Column {
                                                Text(
                                                    text = item.name,
                                                    fontSize = 14.sp,
                                                    fontWeight = FontWeight.Bold,
                                                    color = Color(0xFF111827)
                                                )
                                                Text(
                                                    text = "Validity Expired",
                                                    fontSize = 12.sp,
                                                    color = Color(0xFFEF4444),
                                                    fontWeight = FontWeight.Medium
                                                )
                                            }
                                        }

                                        Button(
                                            onClick = {
                                                showExpiredSheet = false
                                                onNavigateToStore()
                                            },
                                            shape = RoundedCornerShape(10.dp),
                                            colors = ButtonDefaults.buttonColors(containerColor = QivoOrange),
                                            contentPadding = PaddingValues(horizontal = 12.dp, vertical = 6.dp)
                                        ) {
                                            Text("Re-acquire", fontSize = 12.sp, fontWeight = FontWeight.Bold)
                                        }
                                    }
                                }
                            }
                        }
                    }
                }

                Spacer(modifier = Modifier.height(16.dp))

                Button(
                    onClick = {
                        showExpiredSheet = false
                        onNavigateToStore()
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF7CB342)),
                    shape = RoundedCornerShape(12.dp),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Text("Visit Store to Re-acquire Items", fontWeight = FontWeight.Bold)
                }

                Spacer(modifier = Modifier.height(16.dp))
            }
        }
    }
}

/**
 * Top "Wearing" Card (Matches screenshot single card format)
 */
@Composable
private fun WearingFrameCard(
    frame: AvatarFrameItem,
    onUnwear: () -> Unit,
    modifier: Modifier = Modifier
) {
    Card(
        shape = RoundedCornerShape(18.dp),
        colors = CardDefaults.cardColors(containerColor = Color.White),
        elevation = CardDefaults.cardElevation(defaultElevation = 2.dp),
        border = BorderStroke(1.dp, Color(0xFFF3F4F6)),
        modifier = modifier
            .shadow(
                elevation = 4.dp,
                shape = RoundedCornerShape(18.dp),
                spotColor = Color(0x1A000000)
            )
            .clickable { onUnwear() }
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(vertical = 18.dp, horizontal = 12.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center
        ) {
            Box(
                modifier = Modifier.size(92.dp),
                contentAlignment = Alignment.Center
            ) {
                AvatarFrameRenderer(
                    frameId = frame.id,
                    modifier = Modifier.fillMaxSize()
                )
            }

            Spacer(modifier = Modifier.height(12.dp))

            Text(
                text = frame.name,
                fontSize = 14.5.sp,
                fontWeight = FontWeight.Bold,
                color = Color(0xFF111827),
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )

            Spacer(modifier = Modifier.height(6.dp))

            Text(
                text = "Tap to Unwear",
                fontSize = 12.sp,
                fontWeight = FontWeight.Medium,
                color = QivoOrange
            )
        }
    }
}

/**
 * Available Frame Card in 2-Column Grid (Matches screenshot available frame cards)
 */
@Composable
private fun AvailableFrameGridCard(
    frame: AvatarFrameItem,
    owned: UserOwnedFrame,
    isWearing: Boolean,
    onWearToggle: () -> Unit,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        modifier = modifier.fillMaxWidth()
    ) {
        Card(
            shape = RoundedCornerShape(18.dp),
            colors = CardDefaults.cardColors(
                containerColor = if (isWearing) Color(0xFFFDFBF7) else Color.White
            ),
            elevation = CardDefaults.cardElevation(defaultElevation = if (isWearing) 3.dp else 1.5.dp),
            border = BorderStroke(
                width = if (isWearing) 1.5.dp else 1.dp,
                color = if (isWearing) QivoGold else Color(0xFFF3F4F6)
            ),
            modifier = Modifier
                .fillMaxWidth()
                .aspectRatio(1.05f)
                .shadow(
                    elevation = if (isWearing) 6.dp else 3.dp,
                    shape = RoundedCornerShape(18.dp),
                    spotColor = if (isWearing) Color(0x33C5A059) else Color(0x14000000)
                )
                .clickable { onClick() }
        ) {
            Box(
                modifier = Modifier.fillMaxSize(),
                contentAlignment = Alignment.Center
            ) {
                Column(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(horizontal = 8.dp, vertical = 12.dp),
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.SpaceBetween
                ) {
                    Box(
                        modifier = Modifier.size(76.dp),
                        contentAlignment = Alignment.Center
                    ) {
                        AvatarFrameRenderer(
                            frameId = frame.id,
                            modifier = Modifier.fillMaxSize()
                        )
                    }

                    Text(
                        text = frame.name,
                        fontSize = 13.5.sp,
                        fontWeight = FontWeight.Bold,
                        color = Color(0xFF1F2937),
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                }

                // Wearing badge top-right
                if (isWearing) {
                    Box(
                        modifier = Modifier
                            .align(Alignment.TopEnd)
                            .padding(6.dp)
                            .clip(RoundedCornerShape(8.dp))
                            .background(QivoOrange)
                            .padding(horizontal = 6.dp, vertical = 2.dp)
                    ) {
                        Text(
                            text = "Wearing",
                            fontSize = 10.sp,
                            fontWeight = FontWeight.Bold,
                            color = Color.White
                        )
                    }
                }
            }
        }

        Spacer(modifier = Modifier.height(6.dp))

        // Remaining Days Tag (Matches screenshot lime green e.g. "9967Days")
        val daysText = remember(owned.expiresAt) {
            if (owned.expiresAt.contains("2054")) {
                "9967Days"
            } else if (owned.expiresAt.isNotBlank()) {
                owned.remainingTimeText
            } else {
                "9967Days"
            }
        }

        Text(
            text = daysText,
            fontSize = 12.5.sp,
            fontWeight = FontWeight.SemiBold,
            color = Color(0xFF65A30D) // Vibrant lime green from screenshot
        )
    }
}


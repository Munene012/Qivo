package com.example.ui.screens
import com.example.ui.components.AppToast
import com.example.data.NetworkUtils
import androidx.compose.material.icons.filled.WifiOff

import android.widget.Toast
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
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
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.ShoppingBag
import androidx.compose.material.icons.filled.Star
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
import androidx.compose.material3.Scaffold
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.lazy.grid.GridItemSpan
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
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
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.data.AvatarFrameItem
import com.example.data.AvatarFrameManager
import com.example.data.AvatarHelper
import com.example.data.SupabaseProfileService
import com.example.data.UserSessionManager
import com.example.ui.components.AvatarFrameRenderer
import kotlinx.coroutines.launch

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun StoreScreen(
    onBackClick: () -> Unit = {},
    onNavigateToRecharge: () -> Unit = {},
    onNavigateToBag: () -> Unit = {}
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val profileService = remember { SupabaseProfileService() }
    val isOnline by remember { NetworkUtils.observeNetworkConnectivity(context) }
        .collectAsStateWithLifecycle(initialValue = NetworkUtils.isOnline(context))

    var session by remember { mutableStateOf(UserSessionManager.getSession(context)) }
    var currentCoins by remember { mutableStateOf(session?.coins ?: 0L) }
    var activeFrameId by remember { mutableStateOf(session?.activeFrameId ?: "") }
    var frameExpiresAt by remember { mutableStateOf(session?.frameExpiresAt ?: "") }
    val activeFrameFlowState by UserSessionManager.activeFrameFlow.collectAsStateWithLifecycle(initialValue = "" to "")
    LaunchedEffect(activeFrameFlowState) {
        val s = session
        if (s != null && s.userId.isNotBlank() && activeFrameFlowState.first == s.userId) {
            activeFrameId = activeFrameFlowState.second
        }
    }

    var selectedFrameForBuy by remember { mutableStateOf<AvatarFrameItem?>(null) }
    var isBuying by remember { mutableStateOf(false) }
    var showBagSheet by remember { mutableStateOf(false) }
    var ownedFrames by remember { mutableStateOf<List<com.example.data.UserOwnedFrame>>(emptyList()) }

    // Sync live profile stats and owned frames on load
    LaunchedEffect(Unit) {
        val s = UserSessionManager.getSession(context)
        session = s
        if (s != null && s.userId.isNotBlank()) {
            val liveCoins = profileService.fetchCoins(s.userId)
            if (liveCoins > 0) {
                currentCoins = liveCoins
                UserSessionManager.saveCoins(context, liveCoins)
            }
            val localActive = UserSessionManager.getActiveFrameId(context, s.userId)
            val localExp = UserSessionManager.getFrameExpiresAt(context, s.userId)
            activeFrameId = localActive
            frameExpiresAt = localExp

            val profile = profileService.fetchProfileById(s.userId)
            if (profile != null) {
                activeFrameId = profile.activeFrameId
                frameExpiresAt = profile.frameExpiresAt
                UserSessionManager.saveActiveFrame(context, profile.activeFrameId, profile.frameExpiresAt, targetUserId = s.userId)
            }
            val fetchedOwned = profileService.fetchUserOwnedFrames(s.userId, context)
            ownedFrames = fetchedOwned
        }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Text(
                        text = "Store",
                        fontSize = 18.sp,
                        fontWeight = FontWeight.Bold,
                        color = Color(0xFF1E293B)
                    )
                },
                navigationIcon = {
                    IconButton(onClick = onBackClick) {
                        Icon(
                            imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                            contentDescription = "Back",
                            tint = Color(0xFF1E293B)
                        )
                    }
                },
                actions = {
                    // Green Bag Pill Button -> Navigates directly to Bag screen
                    Box(
                        modifier = Modifier
                            .padding(end = 16.dp)
                            .clip(RoundedCornerShape(20.dp))
                            .background(
                                brush = Brush.horizontalGradient(
                                    listOf(Color(0xFF7CB342), Color(0xFF558B2F))
                                )
                            )
                            .clickable { onNavigateToBag() }
                            .padding(horizontal = 14.dp, vertical = 6.dp)
                    ) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.Center
                        ) {
                            Icon(
                                imageVector = Icons.Default.ShoppingBag,
                                contentDescription = "Bag",
                                tint = Color.White,
                                modifier = Modifier.size(16.dp)
                            )
                            Spacer(modifier = Modifier.width(6.dp))
                            Text(
                                text = "Bag",
                                fontSize = 14.sp,
                                fontWeight = FontWeight.Bold,
                                color = Color.White
                            )
                        }
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = Color.White
                )
            )
        },
        containerColor = Color(0xFFF8FAFC)
    ) { paddingValues ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(paddingValues)
        ) {
            // Category Header: "Frames" (Active indicator)
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .background(Color.White)
                    .padding(horizontal = 20.dp, vertical = 12.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column(
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    Text(
                        text = "Frames",
                        fontSize = 20.sp,
                        fontWeight = FontWeight.ExtraBold,
                        color = Color(0xFF1E293B)
                    )
                    Spacer(modifier = Modifier.height(4.dp))
                    // Wavy/pill green underline for selected tab
                    Box(
                        modifier = Modifier
                            .width(36.dp)
                            .height(4.dp)
                            .clip(RoundedCornerShape(2.dp))
                            .background(Color(0xFF7CB342))
                    )
                }
            }

            Spacer(modifier = Modifier.height(8.dp))

            // 2-Column Grid of Avatar Frames
            LazyVerticalGrid(
                columns = GridCells.Fixed(2),
                contentPadding = PaddingValues(12.dp),
                horizontalArrangement = Arrangement.spacedBy(12.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp),
                modifier = Modifier.fillMaxSize()
            ) {
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
                                    text = "Offline Mode: Internet connection required to purchase or equip avatar frames.",
                                    fontSize = 12.sp,
                                    color = Color(0xFFB91C1C),
                                    fontWeight = FontWeight.Medium,
                                    lineHeight = 16.sp
                                )
                            }
                        }
                    }
                }

                items(AvatarFrameManager.STORE_FRAMES) { frame ->
                    val isCurrentEquipped = activeFrameId.equals(frame.id, ignoreCase = true)
                    val ownedFrame = ownedFrames.firstOrNull { it.frameId.equals(frame.id, ignoreCase = true) && it.isValid }
                    val isPurchased = ownedFrame != null

                    StoreFrameCard(
                        frame = frame,
                        isEquipped = isCurrentEquipped,
                        isPurchased = isPurchased,
                        remainingTimeText = ownedFrame?.remainingTimeText,
                        avatarUrl = session?.avatarUrl ?: "",
                        userId = session?.userId ?: "",
                        gender = session?.gender ?: "Male",
                        onClick = { selectedFrameForBuy = frame }
                    )
                }
            }
        }
    }

    // Purchase Confirmation Preview Dialog
    selectedFrameForBuy?.let { frame ->
        val isCurrentEquipped = activeFrameId.equals(frame.id, ignoreCase = true)
        val ownedFrame = ownedFrames.firstOrNull { it.frameId.equals(frame.id, ignoreCase = true) && it.isValid }
        val isPurchased = ownedFrame != null

        ModalBottomSheet(
            onDismissRequest = { if (!isBuying) selectedFrameForBuy = null },
            containerColor = Color.White,
            shape = RoundedCornerShape(topStart = 24.dp, topEnd = 24.dp)
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 24.dp, vertical = 16.dp),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                // Header Title & Close
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = "Frame Preview",
                        fontSize = 18.sp,
                        fontWeight = FontWeight.Bold,
                        color = Color(0xFF1E293B)
                    )
                    IconButton(
                        onClick = { selectedFrameForBuy = null },
                        enabled = !isBuying
                    ) {
                        Icon(
                            imageVector = Icons.Default.Close,
                            contentDescription = "Close",
                            tint = Color(0xFF64748B)
                        )
                    }
                }

                Spacer(modifier = Modifier.height(16.dp))

                // Large Avatar Live Preview with Frame
                Box(
                    modifier = Modifier.size(130.dp),
                    contentAlignment = Alignment.Center
                ) {
                    AvatarHelper.UserAvatarImage(
                        avatarUrl = session?.avatarUrl ?: "",
                        userId = session?.userId ?: "",
                        gender = session?.gender ?: "Male",
                        frameId = frame.id,
                        modifier = Modifier.fillMaxSize()
                    )
                }

                Spacer(modifier = Modifier.height(16.dp))

                Text(
                    text = frame.name,
                    fontSize = 20.sp,
                    fontWeight = FontWeight.Bold,
                    color = Color(0xFF1E293B)
                )

                Spacer(modifier = Modifier.height(4.dp))

                Text(
                    text = frame.description,
                    fontSize = 13.sp,
                    color = Color(0xFF64748B),
                    textAlign = TextAlign.Center,
                    modifier = Modifier.padding(horizontal = 16.dp)
                )

                Spacer(modifier = Modifier.height(16.dp))

                if (isPurchased) {
                    // Frame is already purchased and active/valid
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(12.dp))
                            .background(Color(0xFFE8F5E9))
                            .padding(horizontal = 16.dp, vertical = 12.dp),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Icon(
                                imageVector = Icons.Default.CheckCircle,
                                contentDescription = null,
                                tint = Color(0xFF2E7D32),
                                modifier = Modifier.size(20.dp)
                            )
                            Spacer(modifier = Modifier.width(8.dp))
                            Text(
                                text = "Purchased",
                                fontSize = 15.sp,
                                fontWeight = FontWeight.Bold,
                                color = Color(0xFF2E7D32)
                            )
                        }
                        Text(
                            text = ownedFrame?.remainingTimeText ?: "Active",
                            fontSize = 13.sp,
                            fontWeight = FontWeight.SemiBold,
                            color = Color(0xFF2E7D32)
                        )
                    }
                } else {
                    // Price & Validity Bar
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(12.dp))
                            .background(Color(0xFFFFF8E1))
                            .padding(horizontal = 16.dp, vertical = 12.dp),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Box(
                                modifier = Modifier
                                    .size(24.dp)
                                    .clip(CircleShape)
                                    .background(Color(0xFFFFB300)),
                                contentAlignment = Alignment.Center
                            ) {
                                Text(
                                    text = "S",
                                    fontSize = 13.sp,
                                    fontWeight = FontWeight.Black,
                                    color = Color.White
                                )
                            }
                            Spacer(modifier = Modifier.width(8.dp))
                            Text(
                                text = "%,d Coins".format(frame.priceCoins),
                                fontSize = 16.sp,
                                fontWeight = FontWeight.Bold,
                                color = Color(0xFFD84315)
                            )
                        }

                        Text(
                            text = "Valid for 7 Days",
                            fontSize = 13.sp,
                            fontWeight = FontWeight.Medium,
                            color = Color(0xFFF57F17)
                        )
                    }

                    Spacer(modifier = Modifier.height(12.dp))

                    // Current User Coins Balance Row
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            text = "Your Coin Balance:",
                            fontSize = 14.sp,
                            color = Color(0xFF64748B)
                        )
                        Text(
                            text = "%,d Coins".format(currentCoins),
                            fontSize = 14.sp,
                            fontWeight = FontWeight.Bold,
                            color = if (currentCoins >= frame.priceCoins) Color(0xFF2E7D32) else Color(0xFFC62828)
                        )
                    }
                }

                Spacer(modifier = Modifier.height(24.dp))

                // Action Buttons
                if (isCurrentEquipped) {
                    Column(modifier = Modifier.fillMaxWidth()) {
                        Box(
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(50.dp)
                                .clip(RoundedCornerShape(25.dp))
                                .background(Color(0xFFE8F5E9)),
                            contentAlignment = Alignment.Center
                        ) {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Icon(
                                    imageVector = Icons.Default.CheckCircle,
                                    contentDescription = null,
                                    tint = Color(0xFF2E7D32),
                                    modifier = Modifier.size(20.dp)
                                )
                                Spacer(modifier = Modifier.width(8.dp))
                                Text(
                                    text = "Currently Equipped",
                                    fontSize = 15.sp,
                                    fontWeight = FontWeight.Bold,
                                    color = Color(0xFF2E7D32)
                                )
                            }
                        }
                    }
                } else if (isPurchased) {
                    // Already purchased: show Equip button
                    Button(
                        onClick = {
                            if (!NetworkUtils.requireOnline(context, "No internet connection. Cannot equip avatar frame while offline.")) {
                                return@Button
                            }
                            scope.launch {
                                isBuying = true
                                val ok = profileService.equipAvatarFrame(
                                    userId = session?.userId ?: "",
                                    frameId = frame.id,
                                    context = context
                                )
                                isBuying = false
                                if (ok) {
                                    activeFrameId = frame.id
                                    selectedFrameForBuy = null
                                    AppToast.show("${frame.name} equipped! ✨")
                                } else {
                                    AppToast.show("Could not equip frame. Please try again.")
                                }
                            }
                        },
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(50.dp),
                        enabled = !isBuying,
                        shape = RoundedCornerShape(25.dp),
                        colors = ButtonDefaults.buttonColors(
                            containerColor = Color(0xFF7CB342)
                        )
                    ) {
                        if (isBuying) {
                            CircularProgressIndicator(
                                color = Color.White,
                                modifier = Modifier.size(24.dp),
                                strokeWidth = 2.dp
                            )
                        } else {
                            Text(
                                text = "Equip Frame",
                                fontSize = 16.sp,
                                fontWeight = FontWeight.Bold,
                                color = Color.White
                            )
                        }
                    }
                } else {
                    // Not purchased or expired: Show Buy Now button
                    Button(
                        onClick = {
                            if (!NetworkUtils.requireOnline(context, "No internet connection. Cannot purchase avatar frame while offline.")) {
                                return@Button
                            }
                            if (currentCoins < frame.priceCoins) {
                                AppToast.show("Insufficient coin balance! Please recharge.")
                                onNavigateToRecharge()
                                return@Button
                            }
                            scope.launch {
                                isBuying = true
                                val result = profileService.purchaseAvatarFrame(
                                    userId = session?.userId ?: "",
                                    frameId = frame.id,
                                    priceCoins = frame.priceCoins,
                                    validityDays = frame.validityDays,
                                    context = context
                                )
                                isBuying = false
                                if (result.first) {
                                    currentCoins = result.third
                                    activeFrameId = frame.id
                                    // Refresh owned frames
                                    val refreshedOwned = profileService.fetchUserOwnedFrames(session?.userId ?: "", context)
                                    ownedFrames = refreshedOwned
                                    selectedFrameForBuy = null
                                    AppToast.show(result.second, isLong = true)
                                } else {
                                    AppToast.show(result.second, isLong = true)
                                }
                            }
                        },
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(50.dp),
                        enabled = !isBuying,
                        shape = RoundedCornerShape(25.dp),
                        colors = ButtonDefaults.buttonColors(
                            containerColor = Color(0xFF7CB342)
                        )
                    ) {
                        if (isBuying) {
                            CircularProgressIndicator(
                                color = Color.White,
                                modifier = Modifier.size(24.dp),
                                strokeWidth = 2.dp
                            )
                        } else {
                            Text(
                                text = "Buy Now (%,d Coins)".format(frame.priceCoins),
                                fontSize = 16.sp,
                                fontWeight = FontWeight.Bold,
                                color = Color.White
                            )
                        }
                    }
                }

                Spacer(modifier = Modifier.height(20.dp))
            }
        }
    }

    // My Bag Sheet Modal
    if (showBagSheet) {
        val equippedFrame = AvatarFrameManager.getFrameById(activeFrameId)
        val expText = remember(frameExpiresAt) {
            val userFrame = com.example.data.UserOwnedFrame(
                userId = session?.userId ?: "",
                frameId = activeFrameId,
                expiresAt = frameExpiresAt
            )
            userFrame.remainingTimeText
        }

        ModalBottomSheet(
            onDismissRequest = { showBagSheet = false },
            containerColor = Color.White,
            shape = RoundedCornerShape(topStart = 24.dp, topEnd = 24.dp)
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 24.dp, vertical = 16.dp),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = "My Inventory Bag",
                        fontSize = 18.sp,
                        fontWeight = FontWeight.Bold,
                        color = Color(0xFF1E293B)
                    )
                    IconButton(onClick = { showBagSheet = false }) {
                        Icon(imageVector = Icons.Default.Close, contentDescription = "Close", tint = Color(0xFF64748B))
                    }
                }

                Spacer(modifier = Modifier.height(16.dp))

                if (equippedFrame != null) {
                    Box(
                        modifier = Modifier.size(110.dp),
                        contentAlignment = Alignment.Center
                    ) {
                        AvatarHelper.UserAvatarImage(
                            avatarUrl = session?.avatarUrl ?: "",
                            userId = session?.userId ?: "",
                            gender = session?.gender ?: "Male",
                            frameId = equippedFrame.id,
                            modifier = Modifier.fillMaxSize()
                        )
                    }

                    Spacer(modifier = Modifier.height(12.dp))

                    Text(
                        text = equippedFrame.name,
                        fontSize = 18.sp,
                        fontWeight = FontWeight.Bold,
                        color = Color(0xFF1E293B)
                    )

                    Spacer(modifier = Modifier.height(4.dp))

                    Box(
                        modifier = Modifier
                            .clip(RoundedCornerShape(12.dp))
                            .background(Color(0xFFE8F5E9))
                            .padding(horizontal = 12.dp, vertical = 4.dp)
                    ) {
                        Text(
                            text = "Active • $expText",
                            fontSize = 13.sp,
                            fontWeight = FontWeight.Bold,
                            color = Color(0xFF2E7D32)
                        )
                    }

                    Spacer(modifier = Modifier.height(16.dp))

                    Button(
                        onClick = {
                            if (!NetworkUtils.requireOnline(context, "No internet connection. Cannot unequip avatar frame while offline.")) {
                                return@Button
                            }
                            val uId = session?.userId ?: ""
                            if (uId.isNotBlank()) {
                                scope.launch {
                                    profileService.unequipAvatarFrame(uId, context)
                                    activeFrameId = ""
                                    frameExpiresAt = ""
                                    AppToast.show("Unequipped frame")
                                }
                            }
                        },
                        colors = ButtonDefaults.buttonColors(containerColor = Color(0xFFEF4444)),
                        shape = RoundedCornerShape(12.dp)
                    ) {
                        Text("Unequip Frame", fontWeight = FontWeight.Bold, color = Color.White)
                    }
                } else {
                    Text(
                        text = "You currently have no active avatar frame equipped.",
                        fontSize = 14.sp,
                        color = Color(0xFF64748B),
                        textAlign = TextAlign.Center,
                        modifier = Modifier.padding(vertical = 24.dp)
                    )
                }

                Spacer(modifier = Modifier.height(24.dp))
            }
        }
    }
}

@Composable
private fun StoreFrameCard(
    frame: AvatarFrameItem,
    isEquipped: Boolean,
    isPurchased: Boolean = false,
    remainingTimeText: String? = null,
    avatarUrl: String,
    userId: String,
    gender: String,
    onClick: () -> Unit
) {
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(16.dp))
            .clickable { onClick() },
        colors = CardDefaults.cardColors(containerColor = Color.White),
        elevation = CardDefaults.cardElevation(defaultElevation = 2.dp)
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(14.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            // Circle Avatar Preview with Frame Overlay
            Box(
                modifier = Modifier
                    .size(90.dp)
                    .aspectRatio(1f),
                contentAlignment = Alignment.Center
            ) {
                AvatarHelper.UserAvatarImage(
                    avatarUrl = avatarUrl,
                    userId = userId,
                    gender = gender,
                    frameId = frame.id,
                    modifier = Modifier.fillMaxSize()
                )
            }

            Spacer(modifier = Modifier.height(10.dp))

            // Frame Name
            Text(
                text = frame.name,
                fontSize = 15.sp,
                fontWeight = FontWeight.Bold,
                color = Color(0xFF1E293B),
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )

            Spacer(modifier = Modifier.height(6.dp))

            if (isEquipped) {
                Box(
                    modifier = Modifier
                        .clip(RoundedCornerShape(10.dp))
                        .background(Color(0xFFE8F5E9))
                        .padding(horizontal = 8.dp, vertical = 2.dp)
                ) {
                    Text(
                        text = "Equipped",
                        fontSize = 11.sp,
                        fontWeight = FontWeight.Bold,
                        color = Color(0xFF2E7D32)
                    )
                }
            } else if (isPurchased) {
                Box(
                    modifier = Modifier
                        .clip(RoundedCornerShape(10.dp))
                        .background(Color(0xFFE8F5E9))
                        .padding(horizontal = 8.dp, vertical = 2.dp)
                ) {
                    Text(
                        text = if (!remainingTimeText.isNullOrBlank()) "Purchased • $remainingTimeText" else "Purchased",
                        fontSize = 10.sp,
                        fontWeight = FontWeight.Bold,
                        color = Color(0xFF2E7D32),
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                }
            } else {
                // Price tag matching screenshot: Coin Icon + Price /7Days
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.Center
                ) {
                    // Gold coin icon circle with 'S'
                    Box(
                        modifier = Modifier
                            .size(16.dp)
                            .clip(CircleShape)
                            .background(Color(0xFFFFB300)),
                        contentAlignment = Alignment.Center
                    ) {
                        Text(
                            text = "S",
                            fontSize = 10.sp,
                            fontWeight = FontWeight.Black,
                            color = Color.White
                        )
                    }

                    Spacer(modifier = Modifier.width(4.dp))

                    Text(
                        text = "%,d /7Days".format(frame.priceCoins),
                        fontSize = 13.sp,
                        fontWeight = FontWeight.Bold,
                        color = Color(0xFF64748B)
                    )
                }
            }
        }
    }
}

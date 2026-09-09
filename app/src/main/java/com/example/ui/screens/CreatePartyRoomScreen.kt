package com.example.ui.screens
import com.example.ui.components.AppToast

import android.net.Uri
import android.util.Log
import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.AddPhotoAlternate
import androidx.compose.material.icons.filled.CameraAlt
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.ErrorOutline
import androidx.compose.material.icons.filled.Image
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
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil.compose.AsyncImage
import com.example.data.AppAnalyticsService
import com.example.data.NetworkUtils
import com.example.data.PartyRoom
import com.example.data.SupabasePartyService
import com.example.data.SupabaseProfileService
import com.example.data.UserSessionManager
import com.example.ui.theme.AppTheme
import com.example.ui.theme.QivoOrange
import com.example.ui.theme.QivoYellow
import kotlinx.coroutines.launch

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun CreatePartyRoomScreen(
    currentUserId: String,
    currentUserName: String,
    currentUserAvatar: String,
    onBackClick: () -> Unit,
    onRoomCreated: (PartyRoom) -> Unit
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val partyService = remember { SupabasePartyService() }
    val profileService = remember { SupabaseProfileService() }
    val colors = AppTheme.colors

    var userCoins by remember { mutableLongStateOf(UserSessionManager.getCoins(context)) }

    LaunchedEffect(currentUserId) {
        if (currentUserId.isNotBlank()) {
            val fresh = profileService.fetchCoins(currentUserId)
            if (fresh > 0L || userCoins == 0L) {
                userCoins = fresh
                UserSessionManager.saveCoins(context, fresh)
            }
        }
    }

    var roomName by remember { mutableStateOf("") }
    var roomDescription by remember { mutableStateOf("") }
    var selectedCategory by remember { mutableStateOf("Chat") }
    var selectedSeatsCount by remember { mutableIntStateOf(8) }

    var coverImageUri by remember { mutableStateOf<Uri?>(null) }
    var bgImageUri by remember { mutableStateOf<Uri?>(null) }
    var isCreating by remember { mutableStateOf(false) }
    var creationErrorMessage by remember { mutableStateOf<String?>(null) }

    val coverPickerLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.GetContent()
    ) { uri: Uri? ->
        if (uri != null) {
            coverImageUri = uri
        }
    }

    val bgPickerLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.GetContent()
    ) { uri: Uri? ->
        if (uri != null) {
            bgImageUri = uri
        }
    }

    val categories = listOf("Chat", "Music", "Gaming", "Dating", "Karaoke", "Chill")
    val seatOptions = listOf(4, 6, 8, 10)

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Text(
                        "Create Party Room",
                        fontSize = 20.sp,
                        fontWeight = FontWeight.Bold,
                        color = colors.textPrimary
                    )
                },
                navigationIcon = {
                    IconButton(
                        onClick = onBackClick,
                        modifier = Modifier.testTag("back_button")
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
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 20.dp, vertical = 16.dp),
            verticalArrangement = Arrangement.spacedBy(20.dp)
        ) {
            // 1. Photos Section: Cover & Background (Required)
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(6.dp)
            ) {
                Text(
                    "Room Photos",
                    fontSize = 15.sp,
                    fontWeight = FontWeight.Bold,
                    color = colors.textPrimary
                )
                Text(
                    "* Required",
                    fontSize = 12.sp,
                    fontWeight = FontWeight.Bold,
                    color = Color(0xFFEF5350)
                )
            }

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(16.dp)
            ) {
                // Room Profile / Cover Image Picker
                Column(
                    horizontalAlignment = Alignment.CenterHorizontally,
                    modifier = Modifier.weight(1f)
                ) {
                    Box(
                        modifier = Modifier
                            .size(110.dp)
                            .clip(RoundedCornerShape(20.dp))
                            .background(colors.cardBg)
                            .border(
                                2.dp,
                                if (coverImageUri != null) QivoOrange else Color(0xFFEF5350),
                                RoundedCornerShape(20.dp)
                            )
                            .clickable { coverPickerLauncher.launch("image/*") }
                            .testTag("upload_cover_button"),
                        contentAlignment = Alignment.Center
                    ) {
                        if (coverImageUri != null) {
                            AsyncImage(
                                model = coverImageUri,
                                contentDescription = "Cover Image",
                                modifier = Modifier.fillMaxSize(),
                                contentScale = ContentScale.Crop
                            )
                        } else {
                            Column(
                                horizontalAlignment = Alignment.CenterHorizontally,
                                verticalArrangement = Arrangement.Center,
                                modifier = Modifier.padding(8.dp)
                            ) {
                                Icon(
                                    imageVector = Icons.Default.CameraAlt,
                                    contentDescription = "Upload Cover",
                                    tint = QivoOrange,
                                    modifier = Modifier.size(30.dp)
                                )
                                Spacer(modifier = Modifier.height(4.dp))
                                Text(
                                    "Room Avatar *",
                                    fontSize = 11.sp,
                                    fontWeight = FontWeight.Bold,
                                    color = colors.textPrimary
                                )
                                Text(
                                    "Tap to choose",
                                    fontSize = 10.sp,
                                    color = colors.textSecondary
                                )
                            }
                        }
                    }
                    Spacer(modifier = Modifier.height(6.dp))
                    Text("Room Profile *", fontSize = 12.sp, fontWeight = FontWeight.Medium, color = colors.textSecondary)
                }

                // Room Background Image Picker
                Column(
                    horizontalAlignment = Alignment.CenterHorizontally,
                    modifier = Modifier.weight(1f)
                ) {
                    Box(
                        modifier = Modifier
                            .size(110.dp)
                            .clip(RoundedCornerShape(20.dp))
                            .background(colors.cardBg)
                            .border(
                                2.dp,
                                if (bgImageUri != null) QivoYellow else Color(0xFFEF5350),
                                RoundedCornerShape(20.dp)
                            )
                            .clickable { bgPickerLauncher.launch("image/*") }
                            .testTag("upload_bg_button"),
                        contentAlignment = Alignment.Center
                    ) {
                        if (bgImageUri != null) {
                            AsyncImage(
                                model = bgImageUri,
                                contentDescription = "Background Image",
                                modifier = Modifier.fillMaxSize(),
                                contentScale = ContentScale.Crop
                            )
                        } else {
                            Column(
                                horizontalAlignment = Alignment.CenterHorizontally,
                                verticalArrangement = Arrangement.Center,
                                modifier = Modifier.padding(8.dp)
                            ) {
                                Icon(
                                    imageVector = Icons.Default.Image,
                                    contentDescription = "Upload Background",
                                    tint = QivoYellow,
                                    modifier = Modifier.size(30.dp)
                                )
                                Spacer(modifier = Modifier.height(4.dp))
                                Text(
                                    "Wallpaper *",
                                    fontSize = 11.sp,
                                    fontWeight = FontWeight.Bold,
                                    color = colors.textPrimary
                                )
                                Text(
                                    "Tap to choose",
                                    fontSize = 10.sp,
                                    color = colors.textSecondary
                                )
                            }
                        }
                    }
                    Spacer(modifier = Modifier.height(6.dp))
                    Text("Room Wallpaper *", fontSize = 12.sp, fontWeight = FontWeight.Medium, color = colors.textSecondary)
                }
            }

            // 2. Room Name Input (Required)
            Column {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    Text(
                        "Party Room Name",
                        fontSize = 14.sp,
                        fontWeight = FontWeight.SemiBold,
                        color = colors.textPrimary
                    )
                    Text(
                        "* Required",
                        fontSize = 11.sp,
                        fontWeight = FontWeight.Bold,
                        color = Color(0xFFEF5350)
                    )
                }
                Spacer(modifier = Modifier.height(8.dp))
                OutlinedTextField(
                    value = roomName,
                    onValueChange = { if (it.length <= 40) roomName = it },
                    placeholder = { Text("e.g. Night Chat & Music 🎶", color = colors.textSecondary) },
                    modifier = Modifier
                        .fillMaxWidth()
                        .testTag("room_name_input"),
                    shape = RoundedCornerShape(14.dp),
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedBorderColor = QivoOrange,
                        unfocusedBorderColor = colors.cardBorder,
                        focusedContainerColor = colors.inputBg,
                        unfocusedContainerColor = colors.inputBg,
                        focusedTextColor = colors.textPrimary,
                        unfocusedTextColor = colors.textPrimary
                    ),
                    singleLine = true
                )
                Text(
                    "${roomName.length}/40",
                    fontSize = 11.sp,
                    color = colors.textSecondary,
                    modifier = Modifier.align(Alignment.End).padding(top = 4.dp)
                )
            }

            // 3. Room Description / Announcement Input (Optional)
            Column {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    Text(
                        "Room Description & Topic",
                        fontSize = 14.sp,
                        fontWeight = FontWeight.SemiBold,
                        color = colors.textPrimary
                    )
                    Text(
                        "(Optional)",
                        fontSize = 12.sp,
                        fontWeight = FontWeight.Normal,
                        color = colors.textSecondary
                    )
                }
                Spacer(modifier = Modifier.height(8.dp))
                OutlinedTextField(
                    value = roomDescription,
                    onValueChange = { if (it.length <= 160) roomDescription = it },
                    placeholder = { Text("Optional: Share what your party room is about! (Leave empty if not needed)", color = colors.textSecondary) },
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(100.dp)
                        .testTag("room_desc_input"),
                    shape = RoundedCornerShape(14.dp),
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedBorderColor = QivoOrange,
                        unfocusedBorderColor = colors.cardBorder,
                        focusedContainerColor = colors.inputBg,
                        unfocusedContainerColor = colors.inputBg,
                        focusedTextColor = colors.textPrimary,
                        unfocusedTextColor = colors.textPrimary
                    ),
                    maxLines = 4
                )
            }

            // 4. Category Selector (Required)
            Column {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    Text(
                        "Party Room Category",
                        fontSize = 14.sp,
                        fontWeight = FontWeight.SemiBold,
                        color = colors.textPrimary
                    )
                    Text(
                        "* Required",
                        fontSize = 11.sp,
                        fontWeight = FontWeight.Bold,
                        color = Color(0xFFEF5350)
                    )
                }
                Spacer(modifier = Modifier.height(10.dp))
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    categories.take(3).forEach { cat ->
                        val isSelected = selectedCategory == cat
                        Surface(
                            modifier = Modifier
                                .weight(1f)
                                .height(42.dp)
                                .clip(RoundedCornerShape(12.dp))
                                .clickable { selectedCategory = cat }
                                .testTag("cat_$cat"),
                            color = if (isSelected) QivoOrange else colors.cardBg,
                            shape = RoundedCornerShape(12.dp)
                        ) {
                            Box(contentAlignment = Alignment.Center) {
                                Text(
                                    cat,
                                    fontSize = 13.sp,
                                    fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Normal,
                                    color = if (isSelected) Color.White else colors.textPrimary
                                )
                            }
                        }
                    }
                }
                Spacer(modifier = Modifier.height(8.dp))
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    categories.drop(3).forEach { cat ->
                        val isSelected = selectedCategory == cat
                        Surface(
                            modifier = Modifier
                                .weight(1f)
                                .height(42.dp)
                                .clip(RoundedCornerShape(12.dp))
                                .clickable { selectedCategory = cat }
                                .testTag("cat_$cat"),
                            color = if (isSelected) QivoOrange else colors.cardBg,
                            shape = RoundedCornerShape(12.dp)
                        ) {
                            Box(contentAlignment = Alignment.Center) {
                                Text(
                                    cat,
                                    fontSize = 13.sp,
                                    fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Normal,
                                    color = if (isSelected) Color.White else colors.textPrimary
                                )
                            }
                        }
                    }
                }
            }

            // 5. Seat Capacity Selector (Required)
            Column {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    Text(
                        "Microphone Seats Count",
                        fontSize = 14.sp,
                        fontWeight = FontWeight.SemiBold,
                        color = colors.textPrimary
                    )
                    Text(
                        "* Required",
                        fontSize = 11.sp,
                        fontWeight = FontWeight.Bold,
                        color = Color(0xFFEF5350)
                    )
                }
                Spacer(modifier = Modifier.height(10.dp))
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    seatOptions.forEach { seats ->
                        val isSelected = selectedSeatsCount == seats
                        Surface(
                            modifier = Modifier
                                .weight(1f)
                                .height(44.dp)
                                .clip(RoundedCornerShape(12.dp))
                                .clickable { selectedSeatsCount = seats }
                                .testTag("seats_$seats"),
                            color = if (isSelected) QivoYellow else colors.cardBg,
                            shape = RoundedCornerShape(12.dp)
                        ) {
                            Box(contentAlignment = Alignment.Center) {
                                Text(
                                    "$seats Seats",
                                    fontSize = 13.sp,
                                    fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Normal,
                                    color = if (isSelected) Color.Black else colors.textPrimary
                                )
                            }
                        }
                    }
                }
            }

            // Error Banner if RPC fails
            if (!creationErrorMessage.isNullOrBlank()) {
                Surface(
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(12.dp),
                    color = Color(0xFFFFEBEE),
                    border = androidx.compose.foundation.BorderStroke(1.dp, Color(0xFFEF5350))
                ) {
                    Row(
                        modifier = Modifier.padding(14.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Icon(
                            imageVector = Icons.Default.ErrorOutline,
                            contentDescription = "Error",
                            tint = Color(0xFFD32F2F),
                            modifier = Modifier.size(24.dp)
                        )
                        Spacer(modifier = Modifier.width(10.dp))
                        Text(
                            text = creationErrorMessage!!,
                            fontSize = 13.sp,
                            color = Color(0xFFC62828),
                            fontWeight = FontWeight.Medium
                        )
                    }
                }
            }

            // 6. Coin Fee & User Balance Info Card
            Surface(
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(16.dp),
                color = if (userCoins >= 5000L) Color(0xFF1E293B) else Color(0xFF3B1D22),
                border = BorderStroke(
                    1.dp,
                    if (userCoins >= 5000L) Color(0xFFFFD700).copy(alpha = 0.5f) else Color(0xFFEF5350).copy(alpha = 0.6f)
                )
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 16.dp, vertical = 12.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                        Text(
                            text = "🪙",
                            fontSize = 24.sp
                        )
                        Column {
                            Text(
                                text = "Creation Fee: 5,000 Coins",
                                fontSize = 14.sp,
                                fontWeight = FontWeight.Bold,
                                color = Color.White
                            )
                            Text(
                                text = if (userCoins >= 5000L) "Your Balance: $userCoins Coins" else "Insufficient Coins: $userCoins / 5,000",
                                fontSize = 12.sp,
                                color = if (userCoins >= 5000L) Color(0xFFFFD700) else Color(0xFFFF8A80),
                                fontWeight = FontWeight.Medium
                            )
                        }
                    }

                    if (userCoins < 5000L) {
                        Surface(
                            shape = RoundedCornerShape(12.dp),
                            color = Color(0xFFEF5350)
                        ) {
                            Text(
                                text = "Low Coins",
                                fontSize = 11.sp,
                                fontWeight = FontWeight.Bold,
                                color = Color.White,
                                modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp)
                            )
                        }
                    } else {
                        Surface(
                            shape = RoundedCornerShape(12.dp),
                            color = Color(0xFF2E7D32)
                        ) {
                            Text(
                                text = "Ready",
                                fontSize = 11.sp,
                                fontWeight = FontWeight.Bold,
                                color = Color.White,
                                modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp)
                            )
                        }
                    }
                }
            }

            Spacer(modifier = Modifier.height(8.dp))

            // 7. Save & Create Button
            Button(
                onClick = {
                    if (userCoins < 5000L) {
                        val msg = "Insufficient coins! You need 5,000 coins to create a Party Room. Current balance: $userCoins coins."
                        creationErrorMessage = msg
                        AppToast.show(msg, isLong = true)
                        return@Button
                    }
                    if (coverImageUri == null) {
                        AppToast.show("Please upload a Room Profile photo (* Required)")
                        return@Button
                    }
                    if (bgImageUri == null) {
                        AppToast.show("Please upload a Room Wallpaper photo (* Required)")
                        return@Button
                    }
                    if (roomName.isBlank()) {
                        AppToast.show("Please enter a Party Room name (* Required)")
                        return@Button
                    }
                    if (selectedCategory.isBlank()) {
                        AppToast.show("Please select a category (* Required)")
                        return@Button
                    }
                    if (selectedSeatsCount <= 0) {
                        AppToast.show("Please select seat capacity (* Required)")
                        return@Button
                    }

                    creationErrorMessage = null
                    isCreating = true
                    scope.launch {
                        // 1. Upload cover image to party storage bucket
                        var uploadedCoverUrl = ""
                        if (coverImageUri != null) {
                            val url = partyService.uploadPartyImage(context, coverImageUri!!, "covers")
                            if (!url.isNullOrBlank()) {
                                uploadedCoverUrl = url
                            }
                        }
                        if (uploadedCoverUrl.isBlank()) {
                            uploadedCoverUrl = currentUserAvatar.ifBlank {
                                "https://images.unsplash.com/photo-1516450360452-9312f5e86fc7?w=600"
                            }
                        }

                        // 2. Upload background image to party storage bucket
                        var uploadedBgUrl = ""
                        if (bgImageUri != null) {
                            val url = partyService.uploadPartyImage(context, bgImageUri!!, "backgrounds")
                            if (!url.isNullOrBlank()) {
                                uploadedBgUrl = url
                            }
                        }
                        if (uploadedBgUrl.isBlank()) {
                            uploadedBgUrl = "https://images.unsplash.com/photo-1534447677768-be436bb09401?w=800"
                        }

                        // 3. Create room row via Supabase RPC create_party_room (deducts 5000 coins)
                        Log.d("CreatePartyRoomScreen", "[CreateFlow] Initiating party room creation. RoomName='${roomName.trim()}', Seats=$selectedSeatsCount, Category='$selectedCategory'")
                        val result = partyService.createPartyRoom(
                            name = roomName.trim(),
                            description = roomDescription.trim(),
                            category = selectedCategory,
                            coverUrl = uploadedCoverUrl,
                            bgUrl = uploadedBgUrl,
                            hostUserId = currentUserId,
                            hostName = currentUserName,
                            hostAvatarUrl = currentUserAvatar,
                            seatsCount = selectedSeatsCount,
                            context = context
                        )

                        isCreating = false
                        if (result.room != null && result.errorMessage == null && result.room.id.isNotBlank()) {
                            Log.d("CreatePartyRoomScreen", "[CreateFlow] Party Room creation SUCCESS! Database Room UUID: ${result.room.id}")
                            userCoins = (userCoins - 5000L).coerceAtLeast(0L)
                            AppAnalyticsService.logPartyRoomCreated(
                                roomId = result.room.id,
                                title = result.room.name,
                                category = result.room.category,
                                feeCoins = 5000.0
                            )
                            AppToast.show("🎉 Party Room Created! (5,000 Coins used)", isLong = true)
                            onRoomCreated(result.room)
                        } else {
                            val rawError = result.errorMessage ?: "Failed to create party room"
                            val isOffline = !NetworkUtils.isOnline(context)
                            val errorText = when {
                                isOffline -> "No internet connection"
                                else -> NetworkUtils.sanitizeErrorMessage(context, rawError)
                            }
                            Log.e("CreatePartyRoomScreen", "[CreateFlow] Party Room creation FAILED! Error: $errorText (raw: $rawError, isOffline: $isOffline)")
                            creationErrorMessage = errorText
                            NetworkUtils.showToast(context, errorText, true)
                        }
                    }
                },
                modifier = Modifier
                    .fillMaxWidth()
                    .height(54.dp)
                    .testTag("save_party_room_button"),
                colors = ButtonDefaults.buttonColors(
                    containerColor = if (userCoins >= 5000L) QivoOrange else Color(0xFF616161)
                ),
                shape = RoundedCornerShape(16.dp),
                enabled = !isCreating
            ) {
                if (isCreating) {
                    CircularProgressIndicator(color = Color.White, modifier = Modifier.size(24.dp))
                    Spacer(modifier = Modifier.width(10.dp))
                    Text("Creating Party Room & Uploading...", fontSize = 15.sp, fontWeight = FontWeight.Bold)
                } else if (userCoins < 5000L) {
                    Text("Need 5,000 Coins to Create (Current: $userCoins)", fontSize = 15.sp, fontWeight = FontWeight.Bold, color = Color.White)
                } else {
                    Text("Create Party Room (5,000 Coins)", fontSize = 16.sp, fontWeight = FontWeight.Bold)
                }
            }

            Spacer(modifier = Modifier.height(24.dp))
        }
    }
}

package com.example.ui.screens
import com.example.ui.components.AppToast

import android.net.Uri
import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.core.*
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AddAPhoto
import androidx.compose.material.icons.filled.ArrowForward
import androidx.compose.material.icons.filled.CameraAlt
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.PhotoLibrary
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.scale
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil.compose.AsyncImage
import coil.request.ImageRequest
import com.example.data.AvatarHelper
import com.example.data.SupabaseProfileService
import com.example.data.UserSessionManager
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/**
 * High-craft Account Creation Step 2: Upload Avatar Screen
 * Guides user to select and crop a circular avatar photo from device photos or pick a mascot,
 * and then takes them directly to Home.
 */
@Composable
fun UploadAvatarScreen(
    userEmail: String,
    userId: String,
    userName: String,
    userGender: String,
    userCountry: String,
    initialAvatarUrl: String = "",
    numericId: Long = 0L,
    onCompleteAndGoHome: (avatarUrl: String) -> Unit
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val scrollState = rememberScrollState()
    val profileService = remember { SupabaseProfileService() }

    var avatarUrl by remember { mutableStateOf(initialAvatarUrl) }
    var selectedMascotKey by remember { mutableStateOf<String?>(null) }
    var isUploading by remember { mutableStateOf(false) }
    var showGalleryAndCrop by remember { mutableStateOf(false) }

    // Fallback direct system photo picker
    val photoPickerLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.PickVisualMedia()
    ) { uri: Uri? ->
        if (uri != null) {
            isUploading = true
            scope.launch {
                val token = UserSessionManager.getValidAccessToken(context)
                val uploaded = profileService.uploadProfilePhoto(context, userId, uri, accessToken = token)
                isUploading = false
                if (uploaded != null) {
                    avatarUrl = uploaded
                    selectedMascotKey = null
                    // Immediately update in Supabase database so all screens see it
                    launch(Dispatchers.IO) {
                        try {
                            profileService.updateAvatarUrl(
                                userId = userId,
                                avatarUrl = uploaded,
                                context = context,
                                accessToken = token
                            )
                        } catch (_: Exception) {}
                    }
                    UserSessionManager.saveSession(
                        context = context,
                        email = userEmail,
                        userId = userId,
                        name = userName,
                        gender = userGender,
                        country = userCountry,
                        avatarUrl = uploaded,
                        numericId = numericId,
                        accessToken = token
                    )
                    AppToast.show("Avatar uploaded successfully! 🎉")
                } else {
                    AppToast.show("Could not upload photo. Please try again.")
                }
            }
        }
    }

    if (showGalleryAndCrop) {
        GalleryAndCropScreen(
            userId = userId,
            profileService = profileService,
            oldAvatarUrl = avatarUrl,
            enableCrop = true,
            title = "Choose Profile Avatar",
            onBackClick = { showGalleryAndCrop = false },
            onPhotoCroppedAndUploaded = { uploadedUrl ->
                showGalleryAndCrop = false
                avatarUrl = uploadedUrl
                selectedMascotKey = null
                scope.launch {
                    val token = UserSessionManager.getValidAccessToken(context)
                    // Immediately update in Supabase database
                    launch(Dispatchers.IO) {
                        try {
                            profileService.updateAvatarUrl(
                                userId = userId,
                                avatarUrl = uploadedUrl,
                                context = context,
                                accessToken = token
                            )
                        } catch (_: Exception) {}
                    }
                    UserSessionManager.saveSession(
                        context = context,
                        email = userEmail,
                        userId = userId,
                        name = userName,
                        gender = userGender,
                        country = userCountry,
                        avatarUrl = uploadedUrl,
                        numericId = numericId,
                        accessToken = token
                    )
                    // Auto advance to home on successful avatar upload
                    onCompleteAndGoHome(uploadedUrl)
                }
            }
        )
        return
    }

    // Gentle pulse animation for the circular avatar placeholder
    val infiniteTransition = rememberInfiniteTransition(label = "pulse")
    val glowScale by infiniteTransition.animateFloat(
        initialValue = 1.0f,
        targetValue = 1.05f,
        animationSpec = infiniteRepeatable(
            animation = tween(1200, easing = FastOutSlowInEasing),
            repeatMode = RepeatMode.Reverse
        ),
        label = "glowScale"
    )

    val defaultMascotRes = remember(userGender, userId, numericId) {
        AvatarHelper.getDefaultMascotRes(userId, userGender, numericId)
    }

    val availableMascots = remember(userGender) {
        if (userGender.equals("Female", ignoreCase = true)) {
            listOf(
                "doll_mascot_3",
                "doll_mascot_4",
                "doll_mascot_5",
                "doll_mascot_6",
                "doll_mascot_1",
                "doll_mascot_2"
            )
        } else {
            listOf(
                "doll_mascot_1",
                "doll_mascot_2",
                "doll_mascot_7",
                "doll_mascot_8",
                "doll_mascot_3",
                "doll_mascot_4"
            )
        }
    }

    val handleSkip: () -> Unit = {
        val fallbackAvatar = selectedMascotKey ?: if (avatarUrl.isNotBlank()) avatarUrl else ""
        scope.launch {
            val token = UserSessionManager.getValidAccessToken(context)
            if (fallbackAvatar.isNotBlank()) {
                try {
                    profileService.updateAvatarUrl(
                        userId = userId,
                        avatarUrl = fallbackAvatar,
                        context = context,
                        accessToken = token
                    )
                } catch (_: Exception) {}
            }
            UserSessionManager.saveSession(
                context = context,
                email = userEmail,
                userId = userId,
                name = userName,
                gender = userGender,
                country = userCountry,
                avatarUrl = fallbackAvatar,
                numericId = numericId,
                accessToken = token
            )
            AppToast.show("Welcome to QIVO, $userName! 🎉")
            onCompleteAndGoHome(fallbackAvatar)
        }
    }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(Color.White)
            .statusBarsPadding()
            .navigationBarsPadding()
            .testTag("upload_avatar_screen_root")
    ) {
        Column(
            modifier = Modifier.fillMaxSize()
        ) {
            // Sticky Top Header with Step indicator and prominent Skip button
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 20.dp, vertical = 12.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Surface(
                    color = Color(0xFFF3F4F6),
                    shape = CircleShape
                ) {
                    Text(
                        text = "Step 2 of 2 • Profile Picture",
                        fontSize = 12.5.sp,
                        fontWeight = FontWeight.SemiBold,
                        color = Color(0xFF4B5563),
                        modifier = Modifier.padding(horizontal = 14.dp, vertical = 6.dp)
                    )
                }

                Surface(
                    onClick = handleSkip,
                    shape = CircleShape,
                    color = Color(0xFFF3F4F6),
                    modifier = Modifier.testTag("top_skip_avatar_button")
                ) {
                    Row(
                        modifier = Modifier.padding(horizontal = 16.dp, vertical = 7.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            text = "Skip",
                            fontSize = 13.5.sp,
                            fontWeight = FontWeight.Bold,
                            color = Color(0xFF1F2937)
                        )
                        Spacer(modifier = Modifier.width(4.dp))
                        Icon(
                            imageVector = Icons.Default.ArrowForward,
                            contentDescription = "Skip",
                            tint = Color(0xFF1F2937),
                            modifier = Modifier.size(14.dp)
                        )
                    }
                }
            }

            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .verticalScroll(scrollState)
                    .padding(horizontal = 24.dp),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                Spacer(modifier = Modifier.height(10.dp))

                // Headline
                Text(
                    text = "Choose Your Avatar",
                    fontSize = 28.sp,
                    fontWeight = FontWeight.ExtraBold,
                    color = Color.Black,
                    textAlign = TextAlign.Center,
                    letterSpacing = (-0.5).sp
                )

            Spacer(modifier = Modifier.height(8.dp))

            Text(
                text = "Add a real photo from your device to stand out and help friends recognize you.",
                fontSize = 14.sp,
                fontWeight = FontWeight.Normal,
                color = Color(0xFF6B7280),
                textAlign = TextAlign.Center,
                lineHeight = 20.sp,
                modifier = Modifier.padding(horizontal = 16.dp)
            )

            Spacer(modifier = Modifier.height(36.dp))

            // Big Round Circular Avatar with glowing ring and camera badge
            Box(
                modifier = Modifier
                    .size(160.dp)
                    .clickable {
                        showGalleryAndCrop = true
                    },
                contentAlignment = Alignment.Center
            ) {
                // Outer subtle glowing aura ring
                Box(
                    modifier = Modifier
                        .size(156.dp)
                        .scale(glowScale)
                        .clip(CircleShape)
                        .background(
                            Brush.sweepGradient(
                                listOf(
                                    Color(0xFFB3FF00).copy(alpha = 0.4f),
                                    Color(0xFF00E5FF).copy(alpha = 0.4f),
                                    Color(0xFFB3FF00).copy(alpha = 0.4f)
                                )
                            )
                        )
                )

                // Main Circular Avatar
                Surface(
                    shape = CircleShape,
                    color = Color(0xFFF3F4F6),
                    modifier = Modifier
                        .size(140.dp)
                        .shadow(8.dp, CircleShape)
                        .border(3.dp, Color.White, CircleShape)
                ) {
                    Box(
                        modifier = Modifier.fillMaxSize(),
                        contentAlignment = Alignment.Center
                    ) {
                        if (isUploading) {
                            CircularProgressIndicator(
                                color = Color.Black,
                                modifier = Modifier.size(36.dp),
                                strokeWidth = 3.dp
                            )
                        } else {
                            val activeMascot = selectedMascotKey
                            if (activeMascot != null) {
                                val res = AvatarHelper.getDrawableForMascotKey(activeMascot)
                                if (res != null) {
                                    Image(
                                        painter = painterResource(id = res),
                                        contentDescription = "Selected Mascot Avatar",
                                        modifier = Modifier
                                            .fillMaxSize()
                                            .clip(CircleShape),
                                        contentScale = ContentScale.Crop
                                    )
                                }
                            } else if (avatarUrl.isNotBlank()) {
                                AsyncImage(
                                    model = ImageRequest.Builder(context)
                                        .data(avatarUrl)
                                        .crossfade(true)
                                        .error(defaultMascotRes)
                                        .build(),
                                    contentDescription = "Profile Avatar",
                                    modifier = Modifier
                                        .fillMaxSize()
                                        .clip(CircleShape),
                                    contentScale = ContentScale.Crop
                                )
                            } else {
                                Image(
                                    painter = painterResource(id = defaultMascotRes),
                                    contentDescription = "Default Avatar",
                                    modifier = Modifier
                                        .fillMaxSize()
                                        .clip(CircleShape),
                                    contentScale = ContentScale.Crop
                                )
                            }
                        }
                    }
                }

                // Camera FAB Badge at bottom right of circular avatar
                Surface(
                    shape = CircleShape,
                    color = Color.Black,
                    modifier = Modifier
                        .size(42.dp)
                        .align(Alignment.BottomEnd)
                        .offset(x = (-4).dp, y = (-4).dp)
                        .shadow(4.dp, CircleShape)
                        .border(2.dp, Color.White, CircleShape)
                        .clickable { showGalleryAndCrop = true }
                ) {
                    Box(
                        modifier = Modifier.fillMaxSize(),
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(
                            imageVector = Icons.Default.CameraAlt,
                            contentDescription = "Select Photo",
                            tint = Color(0xFFB3FF00),
                            modifier = Modifier.size(20.dp)
                        )
                    }
                }
            }

            Spacer(modifier = Modifier.height(32.dp))

            // Primary Button: "Upload from Photos / Gallery"
            Button(
                onClick = { showGalleryAndCrop = true },
                modifier = Modifier
                    .fillMaxWidth()
                    .height(54.dp)
                    .testTag("upload_from_photos_button"),
                shape = CircleShape,
                colors = ButtonDefaults.buttonColors(
                    containerColor = Color.Black,
                    contentColor = Color(0xFFB3FF00)
                )
            ) {
                Icon(
                    imageVector = Icons.Default.PhotoLibrary,
                    contentDescription = null,
                    modifier = Modifier.size(20.dp)
                )
                Spacer(modifier = Modifier.width(10.dp))
                Text(
                    text = "Upload from Photos",
                    fontSize = 16.sp,
                    fontWeight = FontWeight.Bold
                )
            }

            Spacer(modifier = Modifier.height(14.dp))

            // Secondary option: Or pick a mascot avatar
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(vertical = 12.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                HorizontalDivider(modifier = Modifier.weight(1f), color = Color(0xFFE5E7EB))
                Text(
                    text = "  OR PICK AN AVATAR  ",
                    fontSize = 11.sp,
                    fontWeight = FontWeight.Bold,
                    color = Color(0xFF9CA3AF),
                    letterSpacing = 1.sp
                )
                HorizontalDivider(modifier = Modifier.weight(1f), color = Color(0xFFE5E7EB))
            }

            Spacer(modifier = Modifier.height(8.dp))

            // Horizontal row of round cute mascot avatars
            LazyRow(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(14.dp),
                contentPadding = PaddingValues(horizontal = 4.dp, vertical = 6.dp)
            ) {
                items(availableMascots) { mascotKey ->
                    val mascotRes = AvatarHelper.getDrawableForMascotKey(mascotKey)
                    val isSelected = selectedMascotKey == mascotKey || (selectedMascotKey == null && avatarUrl == mascotKey)

                    if (mascotRes != null) {
                        Box(
                            modifier = Modifier
                                .size(64.dp)
                                .clip(CircleShape)
                                .border(
                                    width = if (isSelected) 3.dp else 1.dp,
                                    color = if (isSelected) Color(0xFFB3FF00) else Color(0xFFE5E7EB),
                                    shape = CircleShape
                                )
                                .clickable {
                                    selectedMascotKey = mascotKey
                                    avatarUrl = mascotKey
                                    scope.launch {
                                        val token = UserSessionManager.getValidAccessToken(context)
                                        profileService.updateAvatarUrl(
                                            userId = userId,
                                            avatarUrl = mascotKey,
                                            context = context,
                                            accessToken = token
                                        )
                                        UserSessionManager.saveSession(
                                            context = context,
                                            email = userEmail,
                                            userId = userId,
                                            name = userName,
                                            gender = userGender,
                                            country = userCountry,
                                            avatarUrl = mascotKey,
                                            numericId = numericId,
                                            accessToken = token
                                        )
                                    }
                                },
                            contentAlignment = Alignment.Center
                        ) {
                            Image(
                                painter = painterResource(id = mascotRes),
                                contentDescription = mascotKey,
                                modifier = Modifier
                                    .fillMaxSize()
                                    .clip(CircleShape),
                                contentScale = ContentScale.Crop
                            )
                            if (isSelected) {
                                Box(
                                    modifier = Modifier
                                        .size(20.dp)
                                        .align(Alignment.BottomEnd)
                                        .clip(CircleShape)
                                        .background(Color.Black),
                                    contentAlignment = Alignment.Center
                                ) {
                                    Icon(
                                        imageVector = Icons.Default.Check,
                                        contentDescription = "Selected",
                                        tint = Color(0xFFB3FF00),
                                        modifier = Modifier.size(12.dp)
                                    )
                                }
                            }
                        }
                    }
                }
            }

            Spacer(modifier = Modifier.height(32.dp))

            // "Continue to Home" Button
            Button(
                onClick = {
                    val finalAvatar = selectedMascotKey ?: avatarUrl
                    scope.launch {
                        val token = UserSessionManager.getValidAccessToken(context)
                        if (finalAvatar.isNotBlank()) {
                            profileService.updateAvatarUrl(
                                userId = userId,
                                avatarUrl = finalAvatar,
                                context = context,
                                accessToken = token
                            )
                        }
                        UserSessionManager.saveSession(
                            context = context,
                            email = userEmail,
                            userId = userId,
                            name = userName,
                            gender = userGender,
                            country = userCountry,
                            avatarUrl = finalAvatar,
                            numericId = numericId,
                            accessToken = token
                        )
                        AppToast.show("Welcome to QIVO, $userName! 🎉")
                        onCompleteAndGoHome(finalAvatar)
                    }
                },
                enabled = !isUploading,
                modifier = Modifier
                    .fillMaxWidth()
                    .height(54.dp)
                    .testTag("continue_to_home_button"),
                shape = CircleShape,
                colors = ButtonDefaults.buttonColors(
                    containerColor = Color(0xFFF3F4F6),
                    contentColor = Color.Black
                )
            ) {
                Text(
                    text = "Continue to Home",
                    fontSize = 16.sp,
                    fontWeight = FontWeight.Bold
                )
                Spacer(modifier = Modifier.width(8.dp))
                Icon(
                    imageVector = Icons.Default.ArrowForward,
                    contentDescription = null,
                    modifier = Modifier.size(18.dp)
                )
            }

            Spacer(modifier = Modifier.height(16.dp))

            // "Skip for now" Button
            OutlinedButton(
                onClick = handleSkip,
                modifier = Modifier
                    .fillMaxWidth()
                    .height(52.dp)
                    .testTag("skip_avatar_button"),
                shape = CircleShape,
                border = BorderStroke(1.5.dp, Color(0xFFD1D5DB)),
                colors = ButtonDefaults.outlinedButtonColors(
                    contentColor = Color(0xFF374151)
                )
            ) {
                Text(
                    text = "Skip for now",
                    fontSize = 15.sp,
                    fontWeight = FontWeight.SemiBold
                )
            }

            Spacer(modifier = Modifier.height(24.dp))
        }
    }
}
}

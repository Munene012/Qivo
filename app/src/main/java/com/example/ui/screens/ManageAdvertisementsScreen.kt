package com.example.ui.screens
import com.example.ui.components.AppToast

import android.graphics.Bitmap
import android.graphics.ImageDecoder
import android.net.Uri
import android.os.Build
import android.provider.MediaStore
import android.widget.Toast
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.AddPhotoAlternate
import androidx.compose.material.icons.filled.Campaign
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Image
import androidx.compose.material.icons.filled.Preview
import androidx.compose.material.icons.filled.Save
import androidx.compose.material.icons.filled.ViewCarousel
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.Tab
import androidx.compose.material3.TabRow
import androidx.compose.material3.TabRowDefaults
import androidx.compose.material3.TabRowDefaults.tabIndicatorOffset
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
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
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil.compose.AsyncImage
import com.example.data.AppAdvertisement
import com.example.data.SupabaseAdService
import com.example.ui.components.AppOpenAdPopup
import com.example.ui.components.ChatTopBannerAd
import com.example.ui.theme.AppTheme
import com.example.ui.theme.QivoOrange
import com.example.ui.theme.QivoYellow
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

enum class AdTab(val title: String) {
    FULLSCREEN_OPEN("App-Open Interstitial"),
    CHAT_BANNER("Chat Top Banner")
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ManageAdvertisementsScreen(
    currentUserId: String,
    onBackClick: () -> Unit
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val adService = remember { SupabaseAdService() }
    val colors = AppTheme.colors
    val isDark = colors.isDark

    var selectedTab by remember { mutableStateOf(AdTab.FULLSCREEN_OPEN) }
    var isLoading by remember { mutableStateOf(true) }
    var isSaving by remember { mutableStateOf(false) }
    var showLivePreview by remember { mutableStateOf(false) }

    // Full-screen Ad State
    var fullScreenAd by remember {
        mutableStateOf(
            AppAdvertisement(
                adType = AppAdvertisement.AD_TYPE_FULLSCREEN,
                title = "New Mini Game: Ludo Arena 🎲",
                description = "Play with friends in voice party rooms and win massive coin pools!",
                actionButtonText = "Play Now",
                targetLink = "qivo://party",
                isActive = true,
                skipSeconds = 5
            )
        )
    }

    // Chat Top Banner Ad State
    var chatBannerAd by remember {
        mutableStateOf(
            AppAdvertisement(
                adType = AppAdvertisement.AD_TYPE_CHAT_BANNER,
                title = "Special Weekend Recharge Bonus! 🔥",
                description = "Get up to 20% extra coins on every recharge via authorized sellers.",
                actionButtonText = "Recharge Now",
                targetLink = "qivo://wallet",
                isActive = false
            )
        )
    }

    // Local picked bitmaps for upload
    var pendingBitmap by remember { mutableStateOf<Bitmap?>(null) }
    var previewLocalUri by remember { mutableStateOf<Uri?>(null) }

    // Gallery Picker Launcher
    val galleryLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.GetContent()
    ) { uri: Uri? ->
        if (uri != null) {
            previewLocalUri = uri
            try {
                val bitmap = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
                    ImageDecoder.decodeBitmap(ImageDecoder.createSource(context.contentResolver, uri))
                } else {
                    @Suppress("DEPRECATION")
                    MediaStore.Images.Media.getBitmap(context.contentResolver, uri)
                }
                pendingBitmap = bitmap
                AppToast.show("Image selected. Tap 'Save & Publish' to upload to advertising storage")
            } catch (e: Exception) {
                AppToast.show("Could not load image. Please choose another image.")
            }
        }
    }

    // Load active ads from Supabase / local cache
    LaunchedEffect(Unit) {
        isLoading = true
        val allAds = adService.fetchAllAds(context)
        val fs = allAds.firstOrNull { it.adType == AppAdvertisement.AD_TYPE_FULLSCREEN }
        if (fs != null) fullScreenAd = fs
        val cb = allAds.firstOrNull { it.adType == AppAdvertisement.AD_TYPE_CHAT_BANNER }
        if (cb != null) chatBannerAd = cb
        isLoading = false
    }

    BackHandler {
        onBackClick()
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Box(
                            modifier = Modifier
                                .size(36.dp)
                                .clip(CircleShape)
                                .background(QivoOrange.copy(alpha = 0.2f)),
                            contentAlignment = Alignment.Center
                        ) {
                            Icon(
                                imageVector = Icons.Default.Campaign,
                                contentDescription = null,
                                tint = QivoOrange,
                                modifier = Modifier.size(20.dp)
                            )
                        }
                        Spacer(modifier = Modifier.width(10.dp))
                        Column {
                            Text(
                                text = "Advertising & Events Hub",
                                fontSize = 17.sp,
                                fontWeight = FontWeight.Bold,
                                color = colors.textPrimary
                            )
                            Text(
                                text = "Admin Promotion Controls",
                                fontSize = 11.sp,
                                color = colors.textSecondary
                            )
                        }
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
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = colors.cardBg
                )
            )
        },
        containerColor = colors.screenBg
    ) { paddingValues ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(paddingValues)
                .navigationBarsPadding()
        ) {
            // Tabs: Full-screen App-Open vs Chat Top Banner
            TabRow(
                selectedTabIndex = selectedTab.ordinal,
                containerColor = colors.cardBg,
                contentColor = QivoOrange,
                indicator = { tabPositions ->
                    TabRowDefaults.SecondaryIndicator(
                        modifier = Modifier.tabIndicatorOffset(tabPositions[selectedTab.ordinal]),
                        color = QivoOrange
                    )
                }
            ) {
                AdTab.entries.forEach { tab ->
                    Tab(
                        selected = selectedTab == tab,
                        onClick = {
                            selectedTab = tab
                            pendingBitmap = null
                            previewLocalUri = null
                        },
                        text = {
                            Text(
                                text = tab.title,
                                fontWeight = if (selectedTab == tab) FontWeight.Bold else FontWeight.Medium,
                                color = if (selectedTab == tab) QivoOrange else colors.textSecondary,
                                fontSize = 13.sp
                            )
                        }
                    )
                }
            }

            if (isLoading) {
                Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    CircularProgressIndicator(color = QivoOrange)
                }
            } else {
                val currentAd = if (selectedTab == AdTab.FULLSCREEN_OPEN) fullScreenAd else chatBannerAd
                val updateCurrentAd: (AppAdvertisement) -> Unit = { updated ->
                    if (selectedTab == AdTab.FULLSCREEN_OPEN) {
                        fullScreenAd = updated
                    } else {
                        chatBannerAd = updated
                    }
                }

                Column(
                    modifier = Modifier
                        .fillMaxSize()
                        .verticalScroll(rememberScrollState())
                        .padding(16.dp),
                    verticalArrangement = Arrangement.spacedBy(16.dp)
                ) {
                    // 1. Status Activation Card
                    Card(
                        shape = RoundedCornerShape(18.dp),
                        colors = CardDefaults.cardColors(containerColor = colors.cardBg),
                        border = BorderStroke(1.dp, if (currentAd.isActive) QivoOrange.copy(alpha = 0.5f) else colors.cardBorder)
                    ) {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(16.dp),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Column(modifier = Modifier.weight(1f)) {
                                Row(verticalAlignment = Alignment.CenterVertically) {
                                    Text(
                                        text = if (currentAd.isActive) "Active (Displayed to Users)" else "Disabled (Normal App Flow)",
                                        fontSize = 15.sp,
                                        fontWeight = FontWeight.Bold,
                                        color = if (currentAd.isActive) Color(0xFF00E676) else colors.textSecondary
                                    )
                                }
                                Spacer(modifier = Modifier.height(2.dp))
                                Text(
                                    text = if (selectedTab == AdTab.FULLSCREEN_OPEN) {
                                        "When enabled, a full-screen skippable announcement appears upon opening the app."
                                    } else {
                                        "When enabled, a clean banner is pinned at the top of the Chat list."
                                    },
                                    fontSize = 11.sp,
                                    color = colors.textSecondary
                                )
                            }
                            Switch(
                                checked = currentAd.isActive,
                                onCheckedChange = { isActive ->
                                    updateCurrentAd(currentAd.copy(isActive = isActive))
                                },
                                colors = SwitchDefaults.colors(
                                    checkedThumbColor = Color.White,
                                    checkedTrackColor = QivoOrange
                                )
                            )
                        }
                    }

                    // 2. Photo Upload & Preview Section (Storage: 'advertising')
                    Card(
                        shape = RoundedCornerShape(18.dp),
                        colors = CardDefaults.cardColors(containerColor = colors.cardBg),
                        border = BorderStroke(1.dp, colors.cardBorder)
                    ) {
                        Column(modifier = Modifier.padding(16.dp)) {
                            Text(
                                text = "Promotion Graphic (Storage: advertising)",
                                fontSize = 14.sp,
                                fontWeight = FontWeight.Bold,
                                color = colors.textPrimary
                            )
                            Text(
                                text = if (selectedTab == AdTab.FULLSCREEN_OPEN) {
                                    "Recommended ratio: 4:5 or 9:16 vertical poster"
                                } else {
                                    "Recommended ratio: 16:5 or 3.2:1 horizontal banner"
                                },
                                fontSize = 11.sp,
                                color = colors.textSecondary
                            )

                            Spacer(modifier = Modifier.height(12.dp))

                            // Preview Frame
                            Box(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .aspectRatio(if (selectedTab == AdTab.FULLSCREEN_OPEN) 1.2f else 3.2f)
                                    .clip(RoundedCornerShape(14.dp))
                                    .background(if (isDark) Color(0xFF14121E) else Color(0xFFECEFF1))
                                    .border(1.dp, Color(0x33FFFFFF), RoundedCornerShape(14.dp))
                                    .clickable { galleryLauncher.launch("image/*") },
                                contentAlignment = Alignment.Center
                            ) {
                                val currentImg = previewLocalUri?.toString() ?: currentAd.imageUrl
                                if (currentImg.isNotBlank()) {
                                    AsyncImage(
                                        model = currentImg,
                                        contentDescription = "Ad Preview",
                                        modifier = Modifier.fillMaxSize(),
                                        contentScale = ContentScale.Crop
                                    )
                                } else {
                                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                                        Icon(
                                            imageVector = Icons.Default.AddPhotoAlternate,
                                            contentDescription = null,
                                            tint = QivoOrange,
                                            modifier = Modifier.size(36.dp)
                                        )
                                        Spacer(modifier = Modifier.height(6.dp))
                                        Text(
                                            text = "Tap to Pick from Gallery",
                                            fontSize = 12.sp,
                                            fontWeight = FontWeight.Bold,
                                            color = colors.textPrimary
                                        )
                                    }
                                }
                            }

                            Spacer(modifier = Modifier.height(10.dp))

                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.spacedBy(8.dp)
                            ) {
                                OutlinedButton(
                                    onClick = { galleryLauncher.launch("image/*") },
                                    shape = RoundedCornerShape(10.dp),
                                    modifier = Modifier.weight(1f)
                                ) {
                                    Icon(Icons.Default.Image, contentDescription = null, modifier = Modifier.size(16.dp), tint = QivoOrange)
                                    Spacer(modifier = Modifier.width(6.dp))
                                    Text("Change Photo", fontSize = 12.sp, color = colors.textPrimary)
                                }

                                if (currentAd.imageUrl.isNotBlank() || previewLocalUri != null) {
                                    OutlinedButton(
                                        onClick = {
                                            previewLocalUri = null
                                            pendingBitmap = null
                                            updateCurrentAd(currentAd.copy(imageUrl = ""))
                                        },
                                        shape = RoundedCornerShape(10.dp),
                                        colors = ButtonDefaults.outlinedButtonColors(contentColor = Color(0xFFFF5252))
                                    ) {
                                        Icon(Icons.Default.Delete, contentDescription = null, modifier = Modifier.size(16.dp), tint = Color(0xFFFF5252))
                                        Spacer(modifier = Modifier.width(4.dp))
                                        Text("Remove", fontSize = 12.sp, color = Color(0xFFFF5252))
                                    }
                                }
                            }
                        }
                    }

                    // 3. Ad Content & Details Form
                    Card(
                        shape = RoundedCornerShape(18.dp),
                        colors = CardDefaults.cardColors(containerColor = colors.cardBg),
                        border = BorderStroke(1.dp, colors.cardBorder)
                    ) {
                        Column(
                            modifier = Modifier.padding(16.dp),
                            verticalArrangement = Arrangement.spacedBy(12.dp)
                        ) {
                            Text(
                                text = "Announcement Copy & Action",
                                fontSize = 14.sp,
                                fontWeight = FontWeight.Bold,
                                color = colors.textPrimary
                            )

                            // Title Field
                            OutlinedTextField(
                                value = currentAd.title,
                                onValueChange = { updateCurrentAd(currentAd.copy(title = it)) },
                                label = { Text("Headline Title") },
                                placeholder = { Text("e.g. New Game Released!") },
                                modifier = Modifier.fillMaxWidth(),
                                singleLine = true,
                                shape = RoundedCornerShape(12.dp),
                                colors = OutlinedTextFieldDefaults.colors(
                                    focusedBorderColor = QivoOrange,
                                    focusedLabelColor = QivoOrange
                                )
                            )

                            // Description Field
                            OutlinedTextField(
                                value = currentAd.description,
                                onValueChange = { updateCurrentAd(currentAd.copy(description = it)) },
                                label = { Text("Short Subtitle / Description") },
                                placeholder = { Text("e.g. Join the tournament room and win prizes.") },
                                modifier = Modifier.fillMaxWidth(),
                                maxLines = 3,
                                shape = RoundedCornerShape(12.dp),
                                colors = OutlinedTextFieldDefaults.colors(
                                    focusedBorderColor = QivoOrange,
                                    focusedLabelColor = QivoOrange
                                )
                            )

                            // Action Button Text
                            OutlinedTextField(
                                value = currentAd.actionButtonText,
                                onValueChange = { updateCurrentAd(currentAd.copy(actionButtonText = it)) },
                                label = { Text("Action Button Label") },
                                placeholder = { Text("e.g. Play Now, Join Room, Recharge") },
                                modifier = Modifier.fillMaxWidth(),
                                singleLine = true,
                                shape = RoundedCornerShape(12.dp),
                                colors = OutlinedTextFieldDefaults.colors(
                                    focusedBorderColor = QivoOrange,
                                    focusedLabelColor = QivoOrange
                                )
                            )

                            // Target Destination Link Field
                            OutlinedTextField(
                                value = currentAd.targetLink,
                                onValueChange = { updateCurrentAd(currentAd.copy(targetLink = it)) },
                                label = { Text("Target Link / Route") },
                                placeholder = { Text("qivo://party, qivo://wallet, or https://...") },
                                modifier = Modifier.fillMaxWidth(),
                                singleLine = true,
                                shape = RoundedCornerShape(12.dp),
                                colors = OutlinedTextFieldDefaults.colors(
                                    focusedBorderColor = QivoOrange,
                                    focusedLabelColor = QivoOrange
                                )
                            )

                            // Preset Quick Route Chips
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.spacedBy(6.dp)
                            ) {
                                listOf(
                                    "🎉 Party Rooms" to "qivo://party",
                                    "🪙 Wallet" to "qivo://wallet",
                                    "🎲 Ludo Game" to "qivo://party"
                                ).forEach { (label, link) ->
                                    Surface(
                                        shape = RoundedCornerShape(8.dp),
                                        color = if (currentAd.targetLink == link) QivoOrange else colors.cardBorder,
                                        modifier = Modifier.clickable {
                                            updateCurrentAd(currentAd.copy(targetLink = link))
                                        }
                                    ) {
                                        Text(
                                            text = label,
                                            fontSize = 11.sp,
                                            fontWeight = FontWeight.Bold,
                                            color = if (currentAd.targetLink == link) Color.White else colors.textSecondary,
                                            modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp)
                                        )
                                    }
                                }
                            }
                        }
                    }

                    // 4. Action Buttons: Preview & Save
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                        // Live Preview Button
                        OutlinedButton(
                            onClick = { showLivePreview = true },
                            shape = RoundedCornerShape(14.dp),
                            modifier = Modifier
                                .weight(1f)
                                .height(50.dp)
                        ) {
                            Icon(Icons.Default.Preview, contentDescription = null, tint = QivoOrange)
                            Spacer(modifier = Modifier.width(6.dp))
                            Text("Preview Ad", fontWeight = FontWeight.Bold, color = colors.textPrimary)
                        }

                        // Save & Publish Button
                        Button(
                            onClick = {
                                scope.launch {
                                    isSaving = true
                                    try {
                                        var finalImageUrl = currentAd.imageUrl

                                        // If a new bitmap is selected, upload to Supabase Storage bucket 'advertising'
                                        if (pendingBitmap != null) {
                                            AppToast.show("Uploading image to advertising storage...")
                                            val uploaded = adService.uploadAdImage(
                                                context = context,
                                                bitmap = pendingBitmap!!,
                                                adType = currentAd.adType,
                                                userId = currentUserId
                                            )
                                            if (uploaded != null) {
                                                finalImageUrl = uploaded
                                            }
                                        }

                                        val adToSave = currentAd.copy(imageUrl = finalImageUrl)
                                        val ok = adService.saveAd(context, adToSave, currentUserId)
                                        if (ok) {
                                            updateCurrentAd(adToSave)
                                            pendingBitmap = null
                                            previewLocalUri = null
                                            AppToast.show("Advertisement published successfully! 🚀", isLong = true)
                                        } else {
                                            AppToast.show("Saved to cache")
                                        }
                                    } catch (e: Exception) {
                                        val raw = e.message ?: ""
                                        if (raw.contains("host", ignoreCase = true) ||
                                            raw.contains("resolve", ignoreCase = true) ||
                                            raw.contains("network", ignoreCase = true) ||
                                            raw.contains("connection", ignoreCase = true) ||
                                            raw.contains("timeout", ignoreCase = true)
                                        ) {
                                            AppToast.show("Network error, check your internet connection.")
                                        } else {
                                            AppToast.show("Failed to publish advertisement. Please try again.")
                                        }
                                    } finally {
                                        isSaving = false
                                    }
                                }
                            },
                            enabled = !isSaving,
                            colors = ButtonDefaults.buttonColors(containerColor = QivoOrange),
                            shape = RoundedCornerShape(14.dp),
                            modifier = Modifier
                                .weight(1.2f)
                                .height(50.dp)
                                .testTag("save_ad_button")
                        ) {
                            if (isSaving) {
                                CircularProgressIndicator(color = Color.White, modifier = Modifier.size(20.dp))
                            } else {
                                Icon(Icons.Default.Save, contentDescription = null, tint = Color.White)
                                Spacer(modifier = Modifier.width(6.dp))
                                Text("Save & Publish", fontWeight = FontWeight.Bold, color = Color.White)
                            }
                        }
                    }

                    Spacer(modifier = Modifier.height(24.dp))
                }
            }
        }

        // Live Preview Modal
        if (showLivePreview) {
            val previewAd = if (selectedTab == AdTab.FULLSCREEN_OPEN) {
                fullScreenAd.copy(imageUrl = previewLocalUri?.toString() ?: fullScreenAd.imageUrl)
            } else {
                chatBannerAd.copy(imageUrl = previewLocalUri?.toString() ?: chatBannerAd.imageUrl)
            }

            if (selectedTab == AdTab.FULLSCREEN_OPEN) {
                AppOpenAdPopup(
                    ad = previewAd,
                    onDismiss = { showLivePreview = false },
                    onNavigateAction = {
                        showLivePreview = false
                        AppToast.show("Action tapped: $it")
                    }
                )
            } else {
                // Banner preview inside dialog
                androidx.compose.ui.window.Dialog(onDismissRequest = { showLivePreview = false }) {
                    Card(
                        shape = RoundedCornerShape(20.dp),
                        colors = CardDefaults.cardColors(containerColor = Color(0xFF161424)),
                        modifier = Modifier.fillMaxWidth().padding(12.dp)
                    ) {
                        Column(modifier = Modifier.padding(16.dp)) {
                            Text("Chat Screen Top Banner Preview:", fontSize = 13.sp, color = Color.White.copy(alpha = 0.7f))
                            Spacer(modifier = Modifier.height(10.dp))
                            ChatTopBannerAd(
                                ad = previewAd,
                                onNavigateAction = {
                                    showLivePreview = false
                                    AppToast.show("Banner tapped: $it")
                                }
                            )
                            Spacer(modifier = Modifier.height(12.dp))
                            Button(
                                onClick = { showLivePreview = false },
                                colors = ButtonDefaults.buttonColors(containerColor = QivoOrange),
                                modifier = Modifier.fillMaxWidth()
                            ) {
                                Text("Close Preview", color = Color.White, fontWeight = FontWeight.Bold)
                            }
                        }
                    }
                }
            }
        }
    }
}

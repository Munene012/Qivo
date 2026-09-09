package com.example.ui.screens
import com.example.ui.components.AppToast

import android.Manifest
import android.content.ContentUris
import android.content.Context
import android.content.pm.PackageManager
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Matrix
import android.graphics.Paint
import android.net.Uri
import android.os.Build
import android.provider.MediaStore
import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectTransformGestures
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Face
import androidx.compose.material.icons.filled.PhotoLibrary
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.ZoomIn
import androidx.compose.material.icons.filled.ZoomOut
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.PathOperation
import androidx.compose.ui.graphics.asImageBitmap
import com.example.data.UserSessionManager
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.content.ContextCompat
import coil.compose.AsyncImage
import com.example.data.AvatarHelper
import com.example.data.SupabaseProfileService
import com.example.ui.theme.AppTheme
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

enum class GalleryCropStep {
    SELECT_PHOTO,
    CROP_PHOTO
}

enum class GalleryTab {
    DEVICE_PHOTOS,
    DOLL_MASCOTS
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun GalleryAndCropScreen(
    userId: String,
    profileService: SupabaseProfileService,
    oldAvatarUrl: String = "",
    enableCrop: Boolean = true,
    title: String = if (enableCrop) "Select Avatar" else "Select Photo",
    onBackClick: () -> Unit,
    onPhotoCroppedAndUploaded: (String) -> Unit
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val colors = AppTheme.colors

    var currentStep by remember { mutableStateOf(GalleryCropStep.SELECT_PHOTO) }
    var activeTab by remember { mutableStateOf(GalleryTab.DEVICE_PHOTOS) }
    var selectedImageUri by remember { mutableStateOf<Uri?>(null) }
    var mediaStorePhotos by remember { mutableStateOf<List<Uri>>(emptyList()) }
    var isLoadingPhotos by remember { mutableStateOf(true) }
    var isUploading by remember { mutableStateOf(false) }

    // Permission launcher for accessing media photos directly
    val permissionsToRequest = remember {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            arrayOf(Manifest.permission.READ_MEDIA_IMAGES)
        } else {
            arrayOf(Manifest.permission.READ_EXTERNAL_STORAGE)
        }
    }

    val permissionLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestMultiplePermissions()
    ) { _ ->
        scope.launch(Dispatchers.IO) {
            val photos = fetchMediaStoreImages(context)
            withContext(Dispatchers.Main) {
                mediaStorePhotos = photos
                isLoadingPhotos = false
            }
        }
    }

    // Auto-request permission and load all device photos on start
    LaunchedEffect(Unit) {
        val hasPermission = permissionsToRequest.all { perm ->
            ContextCompat.checkSelfPermission(context, perm) == PackageManager.PERMISSION_GRANTED
        }

        if (hasPermission) {
            scope.launch(Dispatchers.IO) {
                val photos = fetchMediaStoreImages(context)
                withContext(Dispatchers.Main) {
                    mediaStorePhotos = photos
                    isLoadingPhotos = false
                }
            }
        } else {
            permissionLauncher.launch(permissionsToRequest)
        }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Text(
                        text = if (currentStep == GalleryCropStep.SELECT_PHOTO) title else "Square Crop Photo",
                        fontWeight = FontWeight.Bold,
                        fontSize = 18.sp,
                        color = colors.textPrimary
                    )
                },
                navigationIcon = {
                    IconButton(onClick = {
                        if (currentStep == GalleryCropStep.CROP_PHOTO) {
                            currentStep = GalleryCropStep.SELECT_PHOTO
                        } else {
                            onBackClick()
                        }
                    }) {
                        Icon(
                            imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                            contentDescription = "Back",
                            tint = colors.textPrimary
                        )
                    }
                },
                // Removed camera and other top icons as requested
                actions = {},
                colors = TopAppBarDefaults.topAppBarColors(containerColor = colors.cardBg)
            )
        },
        containerColor = colors.screenBg
    ) { innerPadding ->
        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
        ) {
            if (currentStep == GalleryCropStep.SELECT_PHOTO) {
                Column(modifier = Modifier.fillMaxSize()) {
                    // Segmented Tabs: Only show if enableCrop is true (avatar selection), otherwise pure gallery
                    if (enableCrop) {
                        TabRow(
                            selectedTabIndex = if (activeTab == GalleryTab.DEVICE_PHOTOS) 0 else 1,
                            containerColor = colors.cardBg,
                            contentColor = Color(0xFFFFD600)
                        ) {
                            Tab(
                                selected = activeTab == GalleryTab.DEVICE_PHOTOS,
                                onClick = { activeTab = GalleryTab.DEVICE_PHOTOS },
                                text = {
                                    Row(verticalAlignment = Alignment.CenterVertically) {
                                        Icon(
                                            imageVector = Icons.Default.PhotoLibrary,
                                            contentDescription = null,
                                            modifier = Modifier.size(16.dp),
                                            tint = if (activeTab == GalleryTab.DEVICE_PHOTOS) Color(0xFFFFD600) else colors.textSecondary
                                        )
                                        Spacer(modifier = Modifier.width(6.dp))
                                        Text(
                                            "Device Photos",
                                            fontWeight = FontWeight.Bold,
                                            color = if (activeTab == GalleryTab.DEVICE_PHOTOS) Color(0xFFFFD600) else colors.textSecondary
                                        )
                                    }
                                }
                            )

                            Tab(
                                selected = activeTab == GalleryTab.DOLL_MASCOTS,
                                onClick = { activeTab = GalleryTab.DOLL_MASCOTS },
                                text = {
                                    Row(verticalAlignment = Alignment.CenterVertically) {
                                        Icon(
                                            imageVector = Icons.Default.Face,
                                            contentDescription = null,
                                            modifier = Modifier.size(16.dp),
                                            tint = if (activeTab == GalleryTab.DOLL_MASCOTS) Color(0xFFFFD600) else colors.textSecondary
                                        )
                                        Spacer(modifier = Modifier.width(6.dp))
                                        Text(
                                            "Doll Mascots",
                                            fontWeight = FontWeight.Bold,
                                            color = if (activeTab == GalleryTab.DOLL_MASCOTS) Color(0xFFFFD600) else colors.textSecondary
                                        )
                                    }
                                }
                            )
                        }
                    }

                    if (activeTab == GalleryTab.DEVICE_PHOTOS || !enableCrop) {
                        // DEVICE PHOTOS GRID (All device photos)
                        if (isLoadingPhotos) {
                            Box(
                                modifier = Modifier.fillMaxSize(),
                                contentAlignment = Alignment.Center
                            ) {
                                CircularProgressIndicator(color = Color(0xFFFFD600))
                            }
                        } else if (mediaStorePhotos.isEmpty()) {
                            Column(
                                modifier = Modifier
                                    .fillMaxSize()
                                    .padding(24.dp),
                                horizontalAlignment = Alignment.CenterHorizontally,
                                verticalArrangement = Arrangement.Center
                            ) {
                                Icon(
                                    imageVector = Icons.Default.PhotoLibrary,
                                    contentDescription = null,
                                    tint = colors.textSecondary,
                                    modifier = Modifier.size(64.dp)
                                )
                                Spacer(modifier = Modifier.height(16.dp))
                                Text(
                                    text = "No device photos found in local gallery",
                                    color = colors.textPrimary,
                                    fontSize = 15.sp,
                                    fontWeight = FontWeight.SemiBold
                                )
                                Spacer(modifier = Modifier.height(8.dp))
                                Text(
                                    text = "Please ensure photos are available on this device.",
                                    color = colors.textSecondary,
                                    fontSize = 13.sp
                                )
                            }
                        } else {
                            LazyVerticalGrid(
                                columns = GridCells.Fixed(3),
                                contentPadding = PaddingValues(8.dp),
                                horizontalArrangement = Arrangement.spacedBy(6.dp),
                                verticalArrangement = Arrangement.spacedBy(6.dp),
                                modifier = Modifier.fillMaxSize()
                            ) {
                                items(mediaStorePhotos) { uri ->
                                    Box(
                                        modifier = Modifier
                                            .aspectRatio(1f)
                                            .clip(RoundedCornerShape(8.dp))
                                            .background(colors.cardBg)
                                            .clickable {
                                                if (enableCrop) {
                                                    // Profile photo: Needs cropping
                                                    selectedImageUri = uri
                                                    currentStep = GalleryCropStep.CROP_PHOTO
                                                } else {
                                                    // Conversation photo or extra album photo: No cropping!
                                                    onPhotoCroppedAndUploaded(uri.toString())
                                                }
                                            }
                                    ) {
                                        AsyncImage(
                                            model = uri,
                                            contentDescription = "Device Photo",
                                            modifier = Modifier.fillMaxSize(),
                                            contentScale = ContentScale.Crop
                                        )
                                    }
                                }
                            }
                        }
                    } else {
                        // 3D AVATARS & MASCOTS GRID (For avatar selection)
                        Column(
                            modifier = Modifier
                                .fillMaxSize()
                                .padding(12.dp)
                        ) {
                            Text(
                                text = "Choose a 3D Profile Avatar or Doll Mascot",
                                color = colors.textSecondary,
                                fontSize = 14.sp,
                                fontWeight = FontWeight.Medium,
                                modifier = Modifier.padding(bottom = 12.dp, start = 4.dp)
                            )

                            val allAvatars = remember {
                                val list = mutableListOf<Pair<String, Pair<String, Int>>>()
                                AvatarHelper.MALE_AVATARS.forEach { 
                                    list.add("avatar:${it.id}" to (it.name to it.drawableRes)) 
                                }
                                AvatarHelper.FEMALE_AVATARS.forEach { 
                                    list.add("avatar:${it.id}" to (it.name to it.drawableRes)) 
                                }
                                AvatarHelper.DOLL_MASCOTS.forEach { 
                                    list.add("mascot:${it.id}" to (it.name to it.drawableRes)) 
                                }
                                list
                            }

                            LazyVerticalGrid(
                                columns = GridCells.Fixed(2),
                                contentPadding = PaddingValues(bottom = 24.dp),
                                horizontalArrangement = Arrangement.spacedBy(12.dp),
                                verticalArrangement = Arrangement.spacedBy(12.dp),
                                modifier = Modifier.fillMaxSize()
                            ) {
                                items(allAvatars) { (key, info) ->
                                    val (name, drawableRes) = info
                                    Card(
                                        colors = CardDefaults.cardColors(containerColor = colors.cardBg),
                                        shape = RoundedCornerShape(16.dp),
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .clickable {
                                                if (oldAvatarUrl.isNotBlank() && oldAvatarUrl.contains("/storage/v1/object/public/photos/")) {
                                                    scope.launch(Dispatchers.IO) {
                                                        profileService.deleteOldAvatar(userId, oldAvatarUrl)
                                                    }
                                                }
                                                onPhotoCroppedAndUploaded(key)
                                            }
                                    ) {
                                        Column(
                                            modifier = Modifier
                                                .fillMaxWidth()
                                                .padding(14.dp),
                                            horizontalAlignment = Alignment.CenterHorizontally
                                        ) {
                                            Box(
                                                modifier = Modifier
                                                    .size(90.dp)
                                                    .clip(RoundedCornerShape(12.dp))
                                                    .background(Color.White)
                                                    .padding(2.dp)
                                            ) {
                                                Image(
                                                    painter = painterResource(id = drawableRes),
                                                    contentDescription = name,
                                                    modifier = Modifier.fillMaxSize(),
                                                    contentScale = ContentScale.Crop
                                                )
                                            }

                                            Spacer(modifier = Modifier.height(10.dp))

                                            Text(
                                                text = name,
                                                color = colors.textPrimary,
                                                fontSize = 13.sp,
                                                fontWeight = FontWeight.Bold,
                                                maxLines = 1
                                            )

                                            Spacer(modifier = Modifier.height(6.dp))

                                            Surface(
                                                shape = RoundedCornerShape(10.dp),
                                                color = Color(0xFFFFD600)
                                            ) {
                                                Text(
                                                    text = "Select Avatar",
                                                    fontSize = 11.sp,
                                                    fontWeight = FontWeight.Bold,
                                                    color = Color.Black,
                                                    modifier = Modifier.padding(horizontal = 10.dp, vertical = 4.dp)
                                                )
                                            }
                                        }
                                    }
                                }
                            }
                        }
                    }
                }
            } else {
                // SQUARE CROP VIEW MODE (ONLY FOR PROFILE PHOTO)
                val imageUri = selectedImageUri
                if (imageUri != null) {
                    SquareCropImageContent(
                        context = context,
                        imageUri = imageUri,
                        isUploading = isUploading,
                        onCropAndUpload = { croppedBitmap ->
                            isUploading = true
                            scope.launch {
                                val userToken = UserSessionManager.getValidAccessToken(context)
                                val uploadedUrl = profileService.uploadProfileBitmap(userId, croppedBitmap, accessToken = userToken)
                                isUploading = false
                                if (uploadedUrl != null) {
                                    if (oldAvatarUrl.isNotBlank() && oldAvatarUrl != uploadedUrl && oldAvatarUrl.contains("/storage/v1/object/public/photos/")) {
                                        launch(Dispatchers.IO) {
                                            profileService.deleteOldAvatar(userId, oldAvatarUrl)
                                        }
                                    }
                                    AppToast.show("Avatar uploaded successfully!")
                                    onPhotoCroppedAndUploaded(uploadedUrl)
                                } else {
                                    AppToast.show("Upload failed")
                                }
                            }
                        },
                        onCancel = { currentStep = GalleryCropStep.SELECT_PHOTO }
                    )
                }
            }

            // Uploading Indicator Overlay
            if (isUploading) {
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .background(Color.Black.copy(alpha = 0.75f)),
                    contentAlignment = Alignment.Center
                ) {
                    Card(
                        colors = CardDefaults.cardColors(containerColor = colors.cardBg),
                        shape = RoundedCornerShape(16.dp)
                    ) {
                        Column(
                            modifier = Modifier.padding(24.dp),
                            horizontalAlignment = Alignment.CenterHorizontally
                        ) {
                            CircularProgressIndicator(color = Color(0xFFFFD600))
                            Spacer(modifier = Modifier.height(16.dp))
                            Text(
                                text = "Uploading avatar...",
                                color = colors.textPrimary,
                                fontWeight = FontWeight.Bold,
                                fontSize = 15.sp
                            )
                        }
                    }
                }
            }
        }
    }
}

@Composable
fun SquareCropImageContent(
    context: Context,
    imageUri: Uri,
    isUploading: Boolean,
    onCropAndUpload: (Bitmap) -> Unit,
    onCancel: () -> Unit
) {
    val colors = AppTheme.colors
    var userZoom by remember { mutableFloatStateOf(1f) }
    var panOffset by remember { mutableStateOf(Offset.Zero) }
    var sourceBitmap by remember { mutableStateOf<Bitmap?>(null) }

    LaunchedEffect(imageUri) {
        withContext(Dispatchers.IO) {
            try {
                context.contentResolver.openInputStream(imageUri)?.use { stream ->
                    val decoded = BitmapFactory.decodeStream(stream)
                    sourceBitmap = decoded
                }
            } catch (e: Exception) {
                e.printStackTrace()
            }
        }
    }

    val bmp = sourceBitmap

    if (bmp == null) {
        Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
            CircularProgressIndicator(color = Color(0xFFFFD600))
        }
        return
    }

    Column(
        modifier = Modifier.fillMaxSize(),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        // Main Square Crop Canvas Viewport
        BoxWithConstraints(
            modifier = Modifier
                .weight(1f)
                .fillMaxWidth()
                .background(Color.Black),
            contentAlignment = Alignment.Center
        ) {
            val density = androidx.compose.ui.platform.LocalDensity.current
            val viewportWidthPx = with(density) { maxWidth.toPx() }
            val viewportHeightPx = with(density) { maxHeight.toPx() }

            val cropBoxSizePx = Math.min(viewportWidthPx, viewportHeightPx) * 0.82f
            val cropBoxLeft = (viewportWidthPx - cropBoxSizePx) / 2f
            val cropBoxTop = (viewportHeightPx - cropBoxSizePx) / 2f

            val srcW = bmp.width.toFloat()
            val srcH = bmp.height.toFloat()

            val baseScale = Math.max(cropBoxSizePx / srcW, cropBoxSizePx / srcH)
            val currentScale = baseScale * userZoom

            val dispW = srcW * currentScale
            val dispH = srcH * currentScale

            val maxPanX = Math.max(0f, (dispW - cropBoxSizePx) / 2f)
            val maxPanY = Math.max(0f, (dispH - cropBoxSizePx) / 2f)

            val clampedOffset = Offset(
                x = panOffset.x.coerceIn(-maxPanX, maxPanX),
                y = panOffset.y.coerceIn(-maxPanY, maxPanY)
            )

            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .pointerInput(bmp) {
                        detectTransformGestures { _, pan, zoom, _ ->
                            userZoom = (userZoom * zoom).coerceIn(1.0f, 4.0f)
                            panOffset = Offset(
                                x = (panOffset.x + pan.x).coerceIn(-maxPanX, maxPanX),
                                y = (panOffset.y + pan.y).coerceIn(-maxPanY, maxPanY)
                            )
                        }
                    },
                contentAlignment = Alignment.Center
            ) {
                // Render Transformed Image
                Image(
                    bitmap = bmp.asImageBitmap(),
                    contentDescription = "To Crop",
                    modifier = Modifier
                        .size(with(density) { (srcW * baseScale).toDp() }, with(density) { (srcH * baseScale).toDp() })
                        .graphicsLayer(
                            scaleX = userZoom,
                            scaleY = userZoom,
                            translationX = clampedOffset.x,
                            translationY = clampedOffset.y
                        ),
                    contentScale = ContentScale.FillBounds
                )

                // Overlay Square Crop Frame Mask (1:1 Ratio)
                Canvas(modifier = Modifier.fillMaxSize()) {
                    val boxSize = size.minDimension * 0.82f
                    val left = (size.width - boxSize) / 2f
                    val top = (size.height - boxSize) / 2f
                    val squareRect = Rect(left, top, left + boxSize, top + boxSize)

                    val outerPath = androidx.compose.ui.graphics.Path().apply {
                        addRect(Rect(0f, 0f, size.width, size.height))
                    }
                    val innerSquarePath = androidx.compose.ui.graphics.Path().apply {
                        addRect(squareRect)
                    }

                    val overlayPath = androidx.compose.ui.graphics.Path.combine(
                        PathOperation.Difference,
                        outerPath,
                        innerSquarePath
                    )

                    // Dark semi-transparent scrim around square
                    drawPath(
                        path = overlayPath,
                        color = Color.Black.copy(alpha = 0.72f)
                    )

                    // Square Crop Guide Border
                    drawRect(
                        color = Color(0xFFFFD600),
                        topLeft = Offset(left, top),
                        size = Size(boxSize, boxSize),
                        style = androidx.compose.ui.graphics.drawscope.Stroke(width = 3.dp.toPx())
                    )

                    // Corner guides
                    val cornerLen = 24.dp.toPx()
                    val strokeW = 4.dp.toPx()

                    // Top-Left Corner
                    drawLine(Color.White, Offset(left - 2, top), Offset(left + cornerLen, top), strokeWidth = strokeW)
                    drawLine(Color.White, Offset(left, top - 2), Offset(left, top + cornerLen), strokeWidth = strokeW)

                    // Top-Right Corner
                    drawLine(Color.White, Offset(left + boxSize + 2, top), Offset(left + boxSize - cornerLen, top), strokeWidth = strokeW)
                    drawLine(Color.White, Offset(left + boxSize, top - 2), Offset(left + boxSize - cornerLen, top), strokeWidth = strokeW)

                    // Bottom-Left Corner
                    drawLine(Color.White, Offset(left - 2, top + boxSize), Offset(left + cornerLen, top + boxSize), strokeWidth = strokeW)
                    drawLine(Color.White, Offset(left, top + boxSize + 2), Offset(left, top + boxSize - cornerLen), strokeWidth = strokeW)

                    // Bottom-Right Corner
                    drawLine(Color.White, Offset(left + boxSize + 2, top + boxSize), Offset(left + boxSize - cornerLen, top + boxSize), strokeWidth = strokeW)
                    drawLine(Color.White, Offset(left + boxSize, top + boxSize + 2), Offset(left + boxSize, top + boxSize - cornerLen), strokeWidth = strokeW)
                }
            }
        }

        // Controls and Action Bar
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .background(colors.cardBg)
                .padding(16.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Text(
                text = "Pinch or drag within photo boundaries",
                color = colors.textSecondary,
                fontSize = 13.sp,
                modifier = Modifier.padding(bottom = 12.dp)
            )

            // Zoom Controls Row
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceEvenly,
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(bottom = 16.dp)
            ) {
                IconButton(onClick = { userZoom = (userZoom - 0.2f).coerceAtLeast(1.0f) }) {
                    Icon(imageVector = Icons.Default.ZoomOut, contentDescription = "Zoom Out", tint = colors.textPrimary)
                }

                Slider(
                    value = userZoom,
                    onValueChange = { userZoom = it },
                    valueRange = 1.0f..3.5f,
                    colors = SliderDefaults.colors(
                        thumbColor = Color(0xFFFFD600),
                        activeTrackColor = Color(0xFFFFD600),
                        inactiveTrackColor = colors.cardBorder
                    ),
                    modifier = Modifier.weight(1f)
                )

                IconButton(onClick = { userZoom = (userZoom + 0.2f).coerceAtMost(3.5f) }) {
                    Icon(imageVector = Icons.Default.ZoomIn, contentDescription = "Zoom In", tint = colors.textPrimary)
                }

                IconButton(onClick = {
                    userZoom = 1f
                    panOffset = Offset.Zero
                }) {
                    Icon(imageVector = Icons.Default.Refresh, contentDescription = "Reset", tint = colors.textPrimary)
                }
            }

            // Bottom Buttons
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                OutlinedButton(
                    onClick = onCancel,
                    modifier = Modifier
                        .weight(1f)
                        .height(48.dp),
                    colors = ButtonDefaults.outlinedButtonColors(contentColor = colors.textPrimary),
                    border = androidx.compose.foundation.BorderStroke(1.dp, colors.cardBorder)
                ) {
                    Text("Cancel", fontWeight = FontWeight.Bold)
                }

                Button(
                    onClick = {
                        if (!isUploading) {
                            val cropped = createCroppedSquareBitmap(bmp, userZoom, panOffset)
                            onCropAndUpload(cropped)
                        }
                    },
                    modifier = Modifier
                        .weight(1f)
                        .height(48.dp),
                    colors = ButtonDefaults.buttonColors(containerColor = Color(0xFFFFD600))
                ) {
                    Icon(imageVector = Icons.Default.Check, contentDescription = null, tint = Color.Black)
                    Spacer(modifier = Modifier.width(6.dp))
                    Text("Apply & Upload", color = Color.Black, fontWeight = FontWeight.Bold)
                }
            }
        }
    }
}

/**
 * Creates a clean 1:1 square cropped 512x512 Bitmap strictly bounded within photo dimensions
 */
private fun createCroppedSquareBitmap(source: Bitmap, userZoom: Float, panOffset: Offset): Bitmap {
    val srcW = source.width.toFloat()
    val srcH = source.height.toFloat()

    val zoom = userZoom.coerceAtLeast(1.0f)
    val baseDimension = Math.min(srcW, srcH)
    val cropSize = (baseDimension / zoom).coerceIn(1f, baseDimension)

    val baseScale = 1f / baseDimension
    val currentScale = baseScale * zoom

    val dispW = srcW * currentScale
    val dispH = srcH * currentScale

    val maxPanX = Math.max(0f, (dispW - 1f) / 2f)
    val maxPanY = Math.max(0f, (dispH - 1f) / 2f)

    val normPanX = if (maxPanX > 0f) (panOffset.x / 500f).coerceIn(-maxPanX, maxPanX) else 0f
    val normPanY = if (maxPanY > 0f) (panOffset.y / 500f).coerceIn(-maxPanY, maxPanY) else 0f

    val centerX = srcW / 2f - (normPanX * srcW)
    val centerY = srcH / 2f - (normPanY * srcH)

    val cropLeft = (centerX - cropSize / 2f).toInt().coerceIn(0, (srcW - cropSize).toInt().coerceAtLeast(0))
    val cropTop = (centerY - cropSize / 2f).toInt().coerceIn(0, (srcH - cropSize).toInt().coerceAtLeast(0))
    val finalCropSize = cropSize.toInt().coerceIn(1, Math.min(source.width - cropLeft, source.height - cropTop))

    val cropped = Bitmap.createBitmap(source, cropLeft, cropTop, finalCropSize, finalCropSize)
    return Bitmap.createScaledBitmap(cropped, 512, 512, true)
}

/**
 * Query MediaStore for ALL device gallery photos directly (no 100-limit)
 */
private fun fetchMediaStoreImages(context: Context): List<Uri> {
    val result = mutableListOf<Uri>()
    val projection = arrayOf(MediaStore.Images.Media._ID)
    val sortOrder = "${MediaStore.Images.Media.DATE_ADDED} DESC"

    try {
        val query = context.contentResolver.query(
            MediaStore.Images.Media.EXTERNAL_CONTENT_URI,
            projection,
            null,
            null,
            sortOrder
        )
        query?.use { cursor ->
            val idColumn = cursor.getColumnIndexOrThrow(MediaStore.Images.Media._ID)
            while (cursor.moveToNext()) {
                val id = cursor.getLong(idColumn)
                val contentUri = ContentUris.withAppendedId(
                    MediaStore.Images.Media.EXTERNAL_CONTENT_URI, id
                )
                result.add(contentUri)
            }
        }
    } catch (e: Exception) {
        e.printStackTrace()
    }
    return result
}

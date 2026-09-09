package com.example.ui.screens

import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.CameraAlt
import androidx.compose.material.icons.filled.Face
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.ui.theme.AppColors
import com.example.ui.theme.AppTheme

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun VerificationScreen(
    onBackClick: () -> Unit,
    onVerificationSuccess: () -> Unit = {}
) {
    val context = LocalContext.current
    val colors = AppTheme.colors
    val isDark = colors.isDark
    val scrollState = rememberScrollState()
    var showInAppCamera by remember { mutableStateOf(false) }

    if (showInAppCamera) {
        FaceVerificationCameraScreen(
            onBackClick = { showInAppCamera = false },
            onVerificationCompleted = {
                showInAppCamera = false
                onVerificationSuccess()
                onBackClick()
            }
        )
        return
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = {},
                navigationIcon = {
                    IconButton(onClick = onBackClick) {
                        Icon(
                            imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                            contentDescription = "Back",
                            tint = colors.textPrimary
                        )
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(containerColor = colors.topBarBg)
            )
        },
        bottomBar = {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .background(colors.bottomNavBg)
                    .padding(horizontal = 18.dp, vertical = 16.dp)
            ) {
                Button(
                    onClick = {
                        if (com.example.data.NetworkUtils.requireOnline(context)) {
                            showInAppCamera = true
                        }
                    },
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(54.dp),
                    colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF00C853)),
                    shape = RoundedCornerShape(16.dp)
                ) {
                    Icon(
                        imageVector = Icons.Default.CameraAlt,
                        contentDescription = null,
                        tint = Color.White,
                        modifier = Modifier.size(20.dp)
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(
                        text = "Verify Now",
                        color = Color.White,
                        fontWeight = FontWeight.Bold,
                        fontSize = 16.sp
                    )
                }
            }
        },
        containerColor = colors.screenBg
    ) { innerPadding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
                .padding(horizontal = 18.dp)
                .verticalScroll(scrollState)
        ) {
            Spacer(modifier = Modifier.height(12.dp))

            // Hero Header Card (Royal Blue Gradient)
            Card(
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(24.dp),
                colors = CardDefaults.cardColors(containerColor = Color.Transparent)
            ) {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .background(
                            Brush.verticalGradient(
                                colors = if (isDark) {
                                    listOf(Color(0xFF1E3A8A), Color(0xFF1D4ED8))
                                } else {
                                    listOf(Color(0xFF2962FF), Color(0xFF3D5AFE))
                                }
                            )
                        )
                        .padding(24.dp),
                    contentAlignment = Alignment.Center
                ) {
                    Column(
                        horizontalAlignment = Alignment.CenterHorizontally
                    ) {
                        // Circular Face Icon
                        Box(
                            modifier = Modifier
                                .size(64.dp)
                                .clip(CircleShape)
                                .background(Color.White.copy(alpha = 0.25f)),
                            contentAlignment = Alignment.Center
                        ) {
                            Icon(
                                imageVector = Icons.Default.Face,
                                contentDescription = "Face Verification",
                                tint = Color.White,
                                modifier = Modifier.size(36.dp)
                            )
                        }

                        Spacer(modifier = Modifier.height(16.dp))

                        Text(
                            text = "Verify You Are a Real Person",
                            color = Color.White,
                            fontWeight = FontWeight.ExtraBold,
                            fontSize = 22.sp,
                            textAlign = TextAlign.Center
                        )

                        Spacer(modifier = Modifier.height(8.dp))

                        Text(
                            text = "Take a live selfie to verify your identity. AI will verify that your profile photo is a real person and matches your live selfie.",
                            color = Color.White.copy(alpha = 0.9f),
                            fontSize = 13.sp,
                            textAlign = TextAlign.Center,
                            lineHeight = 18.sp,
                            modifier = Modifier.padding(horizontal = 12.dp)
                        )
                    }
                }
            }

            Spacer(modifier = Modifier.height(24.dp))

            Text(
                text = "Verification Guidelines",
                fontWeight = FontWeight.Bold,
                fontSize = 17.sp,
                color = colors.textPrimary
            )

            Spacer(modifier = Modifier.height(12.dp))

            // Guideline 1: Profile Photo Requirement
            VerificationGuidelineCard(
                stepNumber = "1",
                title = "Profile Photo Requirement",
                description = "Your profile photo MUST be a real picture of yourself. Nature, trees, objects, or animals will fail verification.",
                colors = colors
            )

            Spacer(modifier = Modifier.height(10.dp))

            // Guideline 2: Live Camera Selfie
            VerificationGuidelineCard(
                stepNumber = "2",
                title = "Live Camera Selfie",
                description = "When you click 'Verify Now', your camera will open inside Qivo. Take a clear, well-lit selfie of your face.",
                colors = colors
            )

            Spacer(modifier = Modifier.height(10.dp))

            // Guideline 3: AI Face Matching
            VerificationGuidelineCard(
                stepNumber = "3",
                title = "AI Face Matching",
                description = "Our AI checks if your profile photo is a real person and compares it with your selfie. If they match, you get a verified tick!",
                colors = colors
            )

            Spacer(modifier = Modifier.height(24.dp))
        }
    }
}

@Composable
private fun VerificationGuidelineCard(
    stepNumber: String,
    title: String,
    description: String,
    colors: AppColors
) {
    val isDark = colors.isDark
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(20.dp),
        colors = CardDefaults.cardColors(containerColor = colors.cardBg),
        border = BorderStroke(1.dp, colors.cardBorder),
        elevation = CardDefaults.cardElevation(defaultElevation = 0.dp)
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp, vertical = 16.dp),
            verticalAlignment = Alignment.Top
        ) {
            Box(
                modifier = Modifier
                    .size(36.dp)
                    .clip(CircleShape)
                    .background(if (isDark) Color(0xFF1E293B) else Color(0xFFE3F2FD)),
                contentAlignment = Alignment.Center
            ) {
                Text(
                    text = stepNumber,
                    color = if (isDark) Color(0xFF60A5FA) else Color(0xFF1976D2),
                    fontWeight = FontWeight.ExtraBold,
                    fontSize = 16.sp
                )
            }

            Spacer(modifier = Modifier.width(14.dp))

            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = title,
                    fontWeight = FontWeight.Bold,
                    fontSize = 15.sp,
                    color = colors.textPrimary
                )
                Spacer(modifier = Modifier.height(4.dp))
                Text(
                    text = description,
                    fontSize = 13.sp,
                    color = colors.textSecondary,
                    lineHeight = 18.sp
                )
            }
        }
    }
}

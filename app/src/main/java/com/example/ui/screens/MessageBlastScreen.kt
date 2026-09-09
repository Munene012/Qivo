package com.example.ui.screens
import com.example.ui.components.AppToast

import android.widget.Toast
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.ArrowDropDown
import androidx.compose.material.icons.filled.Send
import com.example.ui.components.Coin3DIcon
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.data.NetworkUtils
import com.example.data.SupabaseChatService
import com.example.data.SupabaseConfig
import com.example.data.SupabaseProfileService
import com.example.data.UserSessionManager
import com.example.ui.components.InsufficientCoinsBottomSheet
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import org.json.JSONObject

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun MessageBlastScreen(
    onBackClick: () -> Unit
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val scrollState = rememberScrollState()
    val profileService = remember { SupabaseProfileService() }

    BackHandler {
        onBackClick()
    }

    val session = UserSessionManager.getSession(context)
    val userId = session?.userId ?: "guest_user"
    val userEmail = session?.email ?: ""
    val myName = session?.name?.ifBlank { "User" } ?: "User"
    val myAvatarUrl = session?.avatarUrl ?: ""
    val myGender = session?.gender ?: "Male"
    val chatService = remember { SupabaseChatService() }

    var userCoins by remember { mutableLongStateOf(UserSessionManager.getCoins(context)) }
    var messageText by remember { mutableStateOf("") }
    var targetUsersCount by remember { mutableIntStateOf(10) }
    var isDropdownExpanded by remember { mutableStateOf(false) }
    var isBlasting by remember { mutableStateOf(false) }
    var showInsufficientCoinsSheet by remember { mutableStateOf(false) }

    // Fetch real live coin balance from Supabase
    LaunchedEffect(userId) {
        if (NetworkUtils.isOnline(context) && userId.isNotEmpty()) {
            val liveProfile = profileService.fetchProfile(userId, userEmail)
            if (liveProfile != null) {
                userCoins = liveProfile.coins
                UserSessionManager.saveCoins(context, liveProfile.coins)
            }
        }
    }

    val totalCost = targetUsersCount * 10
    val targetOptions = listOf(1, 3, 5, 10, 15, 20)

    val colors = com.example.ui.theme.AppTheme.colors
    val isDark = colors.isDark

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
                actions = {
                    // Top-right pill: Real Coin Balance
                    Surface(
                        shape = RoundedCornerShape(50.dp),
                        color = if (isDark) Color(0xFF3B151E) else Color(0xFFFFF0F3),
                        border = androidx.compose.foundation.BorderStroke(1.dp, if (isDark) Color(0xFF6B1D2C) else Color(0xFFFFCDD2)),
                        modifier = Modifier.padding(end = 16.dp)
                    ) {
                        Row(
                            modifier = Modifier.padding(horizontal = 12.dp, vertical = 6.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Coin3DIcon(size = 18.dp)
                            Spacer(modifier = Modifier.width(6.dp))
                            Text(
                                text = "$userCoins COINS",
                                fontSize = 12.sp,
                                fontWeight = FontWeight.Bold,
                                color = if (isDark) Color(0xFFFF8A80) else Color(0xFFD32F2F)
                            )
                        }
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(containerColor = colors.screenBg)
            )
        },
        containerColor = colors.screenBg
    ) { innerPadding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
                .padding(horizontal = 20.dp)
                .verticalScroll(scrollState),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Spacer(modifier = Modifier.height(10.dp))

            // Header Title: MESSAGE BLAST
            Text(
                text = "MESSAGE BLAST",
                fontSize = 24.sp,
                fontWeight = FontWeight.Black,
                color = if (isDark) Color(0xFFFF5252) else Color(0xFFB71C1C),
                letterSpacing = 1.sp
            )

            Spacer(modifier = Modifier.height(4.dp))

            Text(
                text = "TARGETED GLOBAL TRANSMISSION",
                fontSize = 11.sp,
                fontWeight = FontWeight.Bold,
                color = colors.textSecondary,
                letterSpacing = 0.8.sp
            )

            Spacer(modifier = Modifier.height(28.dp))

            // White Card: The Broadcast
            Card(
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(28.dp),
                colors = CardDefaults.cardColors(containerColor = colors.cardBg),
                border = if (isDark) androidx.compose.foundation.BorderStroke(1.dp, colors.cardBorder) else null,
                elevation = CardDefaults.cardElevation(defaultElevation = 0.dp)
            ) {
                Column(
                    modifier = Modifier.padding(20.dp)
                ) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Column {
                            Text(
                                text = "The Broadcast",
                                fontSize = 20.sp,
                                fontWeight = FontWeight.ExtraBold,
                                color = colors.textPrimary
                            )
                            Spacer(modifier = Modifier.height(2.dp))
                            Text(
                                text = "SENT GLOBALLY TO SELECTED USERS",
                                fontSize = 10.sp,
                                fontWeight = FontWeight.Bold,
                                color = colors.textSecondary
                            )
                        }

                        // Badge: "10 🪙 / User"
                        Surface(
                            shape = RoundedCornerShape(50.dp),
                            color = Color(0xFFFFD600)
                        ) {
                            Row(
                                modifier = Modifier.padding(horizontal = 10.dp, vertical = 6.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Text(
                                    text = "10 ",
                                    fontSize = 12.sp,
                                    fontWeight = FontWeight.ExtraBold,
                                    color = Color.Black
                                )
                                Coin3DIcon(size = 14.dp)
                                Text(
                                    text = " / User",
                                    fontSize = 12.sp,
                                    fontWeight = FontWeight.Bold,
                                    color = Color.Black
                                )
                            }
                        }
                    }

                    Spacer(modifier = Modifier.height(20.dp))

                    // Text Field Container
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(160.dp)
                            .clip(RoundedCornerShape(20.dp))
                            .background(if (isDark) Color(0xFF1E1E24) else Color(0xFFF1F5F9))
                            .padding(16.dp)
                    ) {
                        OutlinedTextField(
                            value = messageText,
                            onValueChange = { messageText = it },
                            placeholder = {
                                Text(
                                    text = "Broadcast your mood,\na question, or a\ngreeting...",
                                    color = colors.textSecondary,
                                    fontSize = 15.sp,
                                    lineHeight = 22.sp
                                )
                            },
                            modifier = Modifier.fillMaxSize(),
                            colors = OutlinedTextFieldDefaults.colors(
                                focusedTextColor = colors.textPrimary,
                                unfocusedTextColor = colors.textPrimary,
                                focusedBorderColor = Color.Transparent,
                                unfocusedBorderColor = Color.Transparent,
                                focusedContainerColor = Color.Transparent,
                                unfocusedContainerColor = Color.Transparent
                            )
                        )
                    }
                }
            }

            Spacer(modifier = Modifier.height(28.dp))

            // Subtitle: GLOBAL RECIPIENT REACH
            Text(
                text = "GLOBAL RECIPIENT REACH (UP TO 20 USERS)",
                fontSize = 11.sp,
                fontWeight = FontWeight.Bold,
                color = colors.textSecondary,
                letterSpacing = 0.5.sp,
                modifier = Modifier.fillMaxWidth(),
                textAlign = TextAlign.Start
            )

            Spacer(modifier = Modifier.height(10.dp))

            // Target Audience Dropdown Selector
            Box(modifier = Modifier.fillMaxWidth()) {
                Surface(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clickable { isDropdownExpanded = true },
                    shape = RoundedCornerShape(20.dp),
                    color = colors.cardBg,
                    border = androidx.compose.foundation.BorderStroke(1.dp, colors.cardBorder)
                ) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 20.dp, vertical = 18.dp),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Text(
                                text = "Target Audience: ",
                                fontSize = 14.sp,
                                fontWeight = FontWeight.SemiBold,
                                color = colors.textSecondary
                            )
                            Text(
                                text = "$targetUsersCount Users",
                                fontSize = 14.sp,
                                fontWeight = FontWeight.Bold,
                                color = Color(0xFFFF6D00)
                            )
                        }

                        Icon(
                            imageVector = Icons.Default.ArrowDropDown,
                            contentDescription = "Select Audience",
                            tint = colors.textPrimary
                        )
                    }
                }

                DropdownMenu(
                    expanded = isDropdownExpanded,
                    onDismissRequest = { isDropdownExpanded = false }
                ) {
                    targetOptions.forEach { count ->
                        DropdownMenuItem(
                            text = {
                                Text(
                                    text = "$count Users (${count * 10} Coins)",
                                    fontWeight = if (count == targetUsersCount) FontWeight.Bold else FontWeight.Normal
                                )
                            },
                            onClick = {
                                targetUsersCount = count
                                isDropdownExpanded = false
                            }
                        )
                    }
                }
            }

            Spacer(modifier = Modifier.height(36.dp))

            // Action Button: INITIATE BLAST
            Button(
                onClick = {
                    if (!NetworkUtils.requireOnline(context)) {
                        return@Button
                    }

                    if (messageText.trim().isEmpty()) {
                        AppToast.show("Please enter a broadcast message.")
                        return@Button
                    }
                    if (userCoins < totalCost) {
                        showInsufficientCoinsSheet = true
                        return@Button
                    }

                    scope.launch {
                        isBlasting = true
                        val newBalance = (userCoins - totalCost).coerceAtLeast(0L)
                        userCoins = newBalance
                        UserSessionManager.saveCoins(context, newBalance)

                        // 1. Record coin deduction
                        profileService.recordCoinTransaction(
                            userId = userId,
                            amount = -totalCost.toLong(),
                            type = "MESSAGE_BLAST",
                            title = "Message Blast ($targetUsersCount Users)",
                            description = "Broadcast message sent to $targetUsersCount users (-$totalCost coins)"
                        )

                        // 2. Query target opposite-gender profiles to distribute individual direct messages
                        val targetGender = if (myGender.equals("Male", ignoreCase = true)) "Female" else "Male"
                        val recipientProfiles = try {
                            val list = profileService.fetchProfilesPaged(limit = targetUsersCount * 2 + 20, targetGender = targetGender)
                            if (list.isNotEmpty()) list else profileService.fetchProfilesPaged(limit = targetUsersCount * 2 + 20, targetGender = null)
                        } catch (_: Exception) {
                            emptyList()
                        }

                        // Send direct messages to target recipients so conversations appear in chat list (skip any blocked user)
                        val cleanMessage = messageText.trim()
                        val validRecipients = recipientProfiles
                            .filter { recipient ->
                                recipient.id != userId &&
                                !profileService.isUserBlocked(userId, recipient.id, context)
                            }
                            .take(targetUsersCount)

                        validRecipients.forEach { recipient ->
                            chatService.sendMessage(
                                senderId = userId,
                                senderName = myName,
                                senderAvatar = myAvatarUrl,
                                receiverId = recipient.id,
                                receiverName = recipient.name,
                                messageText = cleanMessage,
                                context = context
                            )
                        }

                        // 3. Notify Edge Function
                        invokeMessageBlastEdgeFunction(
                            userId = userId,
                            message = cleanMessage,
                            targetUserCount = targetUsersCount,
                            totalCoinsDeducted = totalCost
                        )

                        isBlasting = false
                        AppToast.show("Message Blast Sent to $targetUsersCount Users! (-$totalCost Coins)", isLong = true)
                        messageText = ""
                        onBackClick()
                    }
                },
                modifier = Modifier
                    .fillMaxWidth()
                    .height(56.dp),
                enabled = !isBlasting,
                colors = ButtonDefaults.buttonColors(
                    containerColor = Color(0xFFFF5232), // Vibrant Broadcast Red-Orange
                    disabledContainerColor = Color(0xFFCBD5E1)
                ),
                shape = RoundedCornerShape(28.dp)
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.Center
                ) {
                    if (isBlasting) {
                        CircularProgressIndicator(
                            color = Color.White,
                            modifier = Modifier.size(20.dp),
                            strokeWidth = 2.dp
                        )
                        Spacer(modifier = Modifier.width(10.dp))
                        Text(
                            text = "SENDING BLAST...",
                            color = Color.White,
                            fontWeight = FontWeight.Bold,
                            fontSize = 15.sp
                        )
                    } else {
                        Icon(
                            imageVector = Icons.Default.Send,
                            contentDescription = "Send",
                            tint = Color.White,
                            modifier = Modifier.size(18.dp)
                        )
                        Spacer(modifier = Modifier.width(10.dp))
                        Text(
                            text = "INITIATE BLAST (${totalCost} COINS)",
                            color = Color.White,
                            fontWeight = FontWeight.Bold,
                            fontSize = 15.sp,
                            letterSpacing = 0.8.sp
                        )
                    }
                }
            }

            Spacer(modifier = Modifier.height(24.dp))
        }
    }

    if (showInsufficientCoinsSheet) {
        InsufficientCoinsBottomSheet(
            currentCoins = userCoins,
            requiredCoins = totalCost.toLong(),
            onDismiss = { showInsufficientCoinsSheet = false },
            onCoinsUpdated = { updatedCoins ->
                userCoins = updatedCoins
                UserSessionManager.saveCoins(context, updatedCoins)
            }
        )
    }
}

/**
 * Invokes Supabase Edge Function: message-blast
 */
private suspend fun invokeMessageBlastEdgeFunction(
    userId: String,
    message: String,
    targetUserCount: Int,
    totalCoinsDeducted: Int
): Boolean {
    return withContext(Dispatchers.IO) {
        try {
            val baseUrl = SupabaseConfig.supabaseUrl.trim().removeSuffix("/")
            val apiKey = SupabaseConfig.supabaseAnonKey.trim()

            if (baseUrl.isEmpty() || apiKey.isEmpty()) return@withContext true

            val client = com.example.data.SupabaseHttpClient.client
            val endpoint = "$baseUrl/functions/v1/message-blast"

            val jsonBody = JSONObject().apply {
                put("user_id", userId)
                put("message", message)
                put("target_count", targetUserCount)
                put("coins_deducted", totalCoinsDeducted)
            }.toString()

            val authHeader = UserSessionManager.getAuthHeader()
            val request = Request.Builder()
                .url(endpoint)
                .addHeader("apikey", apiKey)
                .addHeader("Authorization", authHeader)
                .addHeader("Content-Type", "application/json")
                .post(jsonBody.toRequestBody("application/json".toMediaType()))
                .build()

            client.newCall(request).execute().use { response ->
                response.isSuccessful || response.code == 200 || response.code == 201
            }
        } catch (e: Exception) {
            e.printStackTrace()
            true
        }
    }
}

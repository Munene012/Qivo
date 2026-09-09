package com.example.ui.screens
import com.example.ui.components.AppToast

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.net.Uri
import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.*
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
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import coil.compose.AsyncImage
import com.example.data.*
import com.example.ui.theme.AppTheme
import com.example.ui.theme.QivoOrange
import com.example.ui.theme.QivoYellow
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import com.example.ui.theme.AppColors

enum class AgencyTab {
    GROUP_CHAT,
    MEMBERS,
    APPLICATIONS,
    SETTINGS
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AgencyCenterScreen(
    currentUserId: String,
    currentNumericId: Long,
    currentUserName: String,
    currentUserAvatar: String,
    currentUserGender: String,
    isAgent: Boolean,
    onBackClick: () -> Unit
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val colors = AppTheme.colors
    val agencyService = remember { SupabaseAgencyService() }

    // Loading & state
    var isLoading by remember { mutableStateOf(true) }
    var myAgency by remember { mutableStateOf<Agency?>(null) }
    var myMembership by remember { mutableStateOf<AgencyMember?>(null) }
    var myLatestApplication by remember { mutableStateOf<AgencyApplication?>(null) }

    // Agent specific lists
    var membersList by remember { mutableStateOf<List<AgencyMember>>(emptyList()) }
    var applicationsList by remember { mutableStateOf<List<AgencyApplication>>(emptyList()) }
    var selectedTab by remember { mutableStateOf(AgencyTab.MEMBERS) }

    // Create Agency Form State
    var createName by remember { mutableStateOf("") }
    var createDesc by remember { mutableStateOf("") }
    var createLogoUri by remember { mutableStateOf<Uri?>(null) }
    var isCreating by remember { mutableStateOf(false) }

    // Join Agency Form State
    var joinCodeInput by remember { mutableStateOf("") }
    var searchedAgency by remember { mutableStateOf<Agency?>(null) }
    var isSearchingAgency by remember { mutableStateOf(false) }
    var isSubmittingApplication by remember { mutableStateOf(false) }

    // Dialog state for confirming member removal
    var memberToRemove by remember { mutableStateOf<AgencyMember?>(null) }

    val isFemaleAccount = currentUserGender.equals("Female", ignoreCase = true) ||
        currentUserGender.startsWith("f", ignoreCase = true) ||
        currentUserGender.startsWith("w", ignoreCase = true)

    val photoPickerLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.GetContent()
    ) { uri: Uri? ->
        if (uri != null) {
            createLogoUri = uri
        }
    }

    fun copyToClipboard(text: String, label: String = "Agency Code") {
        val clipboard = context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
        val clip = ClipData.newPlainText(label, text)
        clipboard.setPrimaryClip(clip)
        AppToast.show("$label copied to clipboard")
    }

    suspend fun refreshData() {
        isLoading = true
        if (isAgent) {
            val agency = agencyService.fetchAgencyByOwner(currentUserId)
            myAgency = agency
            if (agency != null) {
                membersList = agencyService.fetchAgencyMembers(agency.id)
                applicationsList = agencyService.fetchAgencyApplications(agency.id)
            }
        } else {
            val membership = agencyService.fetchUserAgencyMembership(currentUserId)
            myMembership = membership
            if (membership != null) {
                myAgency = agencyService.fetchAgencyById(membership.agencyId)
                if (myAgency != null) {
                    membersList = agencyService.fetchAgencyMembers(myAgency!!.id)
                }
            } else {
                myLatestApplication = agencyService.fetchUserLatestApplication(currentUserId)
            }
        }
        isLoading = false
    }

    LaunchedEffect(currentUserId, isAgent) {
        refreshData()
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Text(
                        text = if (isAgent) "Agency Management Panel" else "Agency Center",
                        fontWeight = FontWeight.Bold,
                        fontSize = 18.sp,
                        color = colors.textPrimary
                    )
                },
                navigationIcon = {
                    IconButton(
                        onClick = onBackClick,
                        modifier = Modifier.testTag("agency_back_button")
                    ) {
                        Icon(
                            imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                            contentDescription = "Back",
                            tint = colors.textPrimary
                        )
                    }
                },
                actions = {
                    IconButton(onClick = {
                        scope.launch { refreshData() }
                    }) {
                        Icon(Icons.Default.Refresh, contentDescription = "Refresh", tint = colors.textPrimary)
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = colors.topBarBg
                )
            )
        },
        containerColor = colors.screenBg
    ) { paddingValues ->
        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(paddingValues)
        ) {
            if (isLoading) {
                Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    CircularProgressIndicator(color = QivoOrange)
                }
            } else if (isAgent && myAgency == null) {
                // ==========================================
                // AGENT VIEW: CREATE AGENCY FORM
                // ==========================================
                Column(
                    modifier = Modifier
                        .fillMaxSize()
                        .verticalScroll(rememberScrollState())
                        .padding(20.dp),
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    Surface(
                        shape = CircleShape,
                        color = QivoYellow.copy(alpha = 0.15f),
                        modifier = Modifier.size(80.dp)
                    ) {
                        Box(contentAlignment = Alignment.Center) {
                            Icon(
                                imageVector = Icons.Default.Business,
                                contentDescription = null,
                                tint = QivoYellow,
                                modifier = Modifier.size(44.dp)
                            )
                        }
                    }

                    Spacer(modifier = Modifier.height(14.dp))
                    Text(
                        text = "Create Your Official Agency",
                        fontSize = 20.sp,
                        fontWeight = FontWeight.Bold,
                        color = colors.textPrimary
                    )
                    Spacer(modifier = Modifier.height(6.dp))
                    Text(
                        text = "As an appointed Agent, you can establish your creator agency, recruit members with a unique Agency Code, and manage applications.",
                        fontSize = 13.sp,
                        color = colors.textSecondary,
                        textAlign = androidx.compose.ui.text.style.TextAlign.Center
                    )

                    Spacer(modifier = Modifier.height(24.dp))

                    // Agency Logo Picker
                    Text(
                        text = "Agency Profile Logo *",
                        fontSize = 13.sp,
                        fontWeight = FontWeight.SemiBold,
                        color = colors.textPrimary,
                        modifier = Modifier.align(Alignment.Start)
                    )
                    Spacer(modifier = Modifier.height(8.dp))

                    Box(
                        modifier = Modifier
                            .size(110.dp)
                            .clip(CircleShape)
                            .background(colors.cardBg)
                            .border(2.dp, QivoOrange, CircleShape)
                            .clickable { photoPickerLauncher.launch("image/*") }
                            .testTag("agency_logo_picker"),
                        contentAlignment = Alignment.Center
                    ) {
                        if (createLogoUri != null) {
                            AsyncImage(
                                model = createLogoUri,
                                contentDescription = "Agency Logo",
                                modifier = Modifier.fillMaxSize(),
                                contentScale = ContentScale.Crop
                            )
                        } else {
                            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                                Icon(Icons.Default.AddAPhoto, contentDescription = null, tint = QivoOrange, modifier = Modifier.size(32.dp))
                                Spacer(modifier = Modifier.height(4.dp))
                                Text("Upload Logo", fontSize = 11.sp, color = colors.textSecondary)
                            }
                        }
                    }

                    Spacer(modifier = Modifier.height(20.dp))

                    // Agency Name Input
                    OutlinedTextField(
                        value = createName,
                        onValueChange = { createName = it },
                        label = { Text("Agency Name") },
                        placeholder = { Text("e.g. Apex Royals Agency") },
                        singleLine = true,
                        modifier = Modifier
                            .fillMaxWidth()
                            .testTag("agency_name_input"),
                        colors = OutlinedTextFieldDefaults.colors(
                            focusedTextColor = colors.textPrimary,
                            unfocusedTextColor = colors.textPrimary,
                            focusedBorderColor = QivoOrange,
                            unfocusedBorderColor = colors.cardBorder
                        )
                    )

                    Spacer(modifier = Modifier.height(14.dp))

                    // Agency Description Input
                    OutlinedTextField(
                        value = createDesc,
                        onValueChange = { createDesc = it },
                        label = { Text("Agency Bio / Description") },
                        placeholder = { Text("Describe your agency goals, requirements, or creator perks...") },
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(100.dp)
                            .testTag("agency_desc_input"),
                        colors = OutlinedTextFieldDefaults.colors(
                            focusedTextColor = colors.textPrimary,
                            unfocusedTextColor = colors.textPrimary,
                            focusedBorderColor = QivoOrange,
                            unfocusedBorderColor = colors.cardBorder
                        )
                    )

                    Spacer(modifier = Modifier.height(26.dp))

                    Button(
                        onClick = {
                            if (createName.isBlank()) {
                                AppToast.show("Please enter an agency name")
                                return@Button
                            }
                            if (createLogoUri == null) {
                                AppToast.show("Please upload an agency logo")
                                return@Button
                            }

                            isCreating = true
                            scope.launch {
                                // 1. Upload logo to Supabase storage bucket 'agency'
                                val uploadedLogoUrl = agencyService.uploadAgencyLogo(
                                    context = context,
                                    imageUri = createLogoUri!!,
                                    agencyIdOrOwnerId = currentUserId
                                ) ?: ""

                                // 2. Create Agency in database
                                val (ok, newAgency) = agencyService.createAgency(
                                    ownerId = currentUserId,
                                    agencyName = createName,
                                    description = createDesc,
                                    logoUrl = uploadedLogoUrl,
                                    agentProfile = UserProfile(
                                        id = currentUserId,
                                        numericId = currentNumericId,
                                        name = currentUserName,
                                        gender = currentUserGender,
                                        country = "",
                                        avatarUrl = currentUserAvatar
                                    )
                                )

                                isCreating = false
                                if (ok && newAgency != null) {
                                    AppToast.show("Agency created successfully! Agency Code: ${newAgency.agencyCode}", isLong = true)
                                    refreshData()
                                } else {
                                    AppToast.show("Failed to create agency. Please try again.")
                                }
                            }
                        },
                        enabled = !isCreating,
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(52.dp)
                            .testTag("create_agency_submit_button"),
                        shape = RoundedCornerShape(16.dp),
                        colors = ButtonDefaults.buttonColors(
                            containerColor = QivoOrange
                        )
                    ) {
                        if (isCreating) {
                            CircularProgressIndicator(color = Color.White, modifier = Modifier.size(20.dp))
                        } else {
                            Icon(Icons.Default.Check, contentDescription = null)
                            Spacer(modifier = Modifier.width(8.dp))
                            Text("Create Agency", fontWeight = FontWeight.Bold, fontSize = 16.sp)
                        }
                    }
                }
            } else if (isAgent && myAgency != null) {
                // ==========================================
                // AGENT VIEW: DASHBOARD (Active Agency)
                // ==========================================
                val agency = myAgency!!
                Column(modifier = Modifier.fillMaxSize()) {
                    // Agency Hero Header
                    Surface(
                        color = colors.cardBg,
                        border = androidx.compose.foundation.BorderStroke(1.dp, colors.cardBorder),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Column(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(16.dp)
                        ) {
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                if (agency.logoUrl.isNotBlank()) {
                                    AsyncImage(
                                        model = agency.logoUrl,
                                        contentDescription = agency.agencyName,
                                        modifier = Modifier
                                            .size(64.dp)
                                            .clip(CircleShape)
                                            .border(2.dp, QivoOrange, CircleShape),
                                        contentScale = ContentScale.Crop
                                    )
                                } else {
                                    Surface(
                                        shape = CircleShape,
                                        color = QivoOrange.copy(alpha = 0.2f),
                                        modifier = Modifier.size(64.dp)
                                    ) {
                                        Box(contentAlignment = Alignment.Center) {
                                            Icon(Icons.Default.Business, contentDescription = null, tint = QivoOrange, modifier = Modifier.size(32.dp))
                                        }
                                    }
                                }

                                Spacer(modifier = Modifier.width(14.dp))

                                Column(modifier = Modifier.weight(1f)) {
                                    Row(verticalAlignment = Alignment.CenterVertically) {
                                        Text(
                                            text = agency.agencyName,
                                            fontSize = 18.sp,
                                            fontWeight = FontWeight.Bold,
                                            color = colors.textPrimary
                                        )
                                        Spacer(modifier = Modifier.width(6.dp))
                                        Surface(
                                            shape = RoundedCornerShape(4.dp),
                                            color = Color(0xFF4CAF50).copy(alpha = 0.2f)
                                        ) {
                                            Text(
                                                text = "ACTIVE",
                                                color = Color(0xFF4CAF50),
                                                fontSize = 9.sp,
                                                fontWeight = FontWeight.Bold,
                                                modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                                            )
                                        }
                                    }

                                    if (agency.description.isNotBlank()) {
                                        Spacer(modifier = Modifier.height(4.dp))
                                        Text(
                                            text = agency.description,
                                            fontSize = 12.sp,
                                            color = colors.textSecondary,
                                            maxLines = 2
                                        )
                                    }

                                    Spacer(modifier = Modifier.height(8.dp))

                                    // Agency Unique Code Pill with 1-Tap Copy
                                    Row(
                                        verticalAlignment = Alignment.CenterVertically,
                                        modifier = Modifier
                                            .clip(RoundedCornerShape(8.dp))
                                            .background(QivoYellow.copy(alpha = 0.15f))
                                            .clickable { copyToClipboard(agency.agencyCode, "Agency Code") }
                                            .padding(horizontal = 8.dp, vertical = 4.dp)
                                    ) {
                                        Icon(Icons.Default.VpnKey, contentDescription = null, tint = QivoYellow, modifier = Modifier.size(14.dp))
                                        Spacer(modifier = Modifier.width(4.dp))
                                        Text(
                                            text = "Code: ${agency.agencyCode}",
                                            fontSize = 12.sp,
                                            fontWeight = FontWeight.Bold,
                                            color = colors.textPrimary
                                        )
                                        Spacer(modifier = Modifier.width(6.dp))
                                        Icon(Icons.Default.ContentCopy, contentDescription = "Copy", tint = colors.textSecondary, modifier = Modifier.size(12.dp))
                                    }
                                }
                            }
                        }
                    }

                    // Navigation Tabs: Group Chat | Members | Pending Applications | Settings
                    TabRow(
                        selectedTabIndex = selectedTab.ordinal,
                        containerColor = colors.topBarBg,
                        contentColor = QivoOrange
                    ) {
                        Tab(
                            selected = selectedTab == AgencyTab.GROUP_CHAT,
                            onClick = { selectedTab = AgencyTab.GROUP_CHAT },
                            text = { Text("Chat 💬", fontWeight = FontWeight.Bold) }
                        )
                        Tab(
                            selected = selectedTab == AgencyTab.MEMBERS,
                            onClick = { selectedTab = AgencyTab.MEMBERS },
                            text = { Text("Members (${membersList.size})", fontWeight = FontWeight.Bold) }
                        )
                        Tab(
                            selected = selectedTab == AgencyTab.APPLICATIONS,
                            onClick = { selectedTab = AgencyTab.APPLICATIONS },
                            text = {
                                val pendingCount = applicationsList.count { it.status == "PENDING" }
                                Row(verticalAlignment = Alignment.CenterVertically) {
                                    Text("Applications", fontWeight = FontWeight.Bold)
                                    if (pendingCount > 0) {
                                        Spacer(modifier = Modifier.width(4.dp))
                                        Surface(
                                            shape = CircleShape,
                                            color = Color(0xFFFF3D00),
                                            modifier = Modifier.size(18.dp)
                                        ) {
                                            Box(contentAlignment = Alignment.Center) {
                                                Text("$pendingCount", fontSize = 10.sp, color = Color.White, fontWeight = FontWeight.Bold)
                                            }
                                        }
                                    }
                                }
                            }
                        )
                        Tab(
                            selected = selectedTab == AgencyTab.SETTINGS,
                            onClick = { selectedTab = AgencyTab.SETTINGS },
                            text = { Text("Info ⚙️", fontWeight = FontWeight.Bold) }
                        )
                    }

                    // Tab Content
                    when (selectedTab) {
                        AgencyTab.GROUP_CHAT -> {
                            AgencyGroupChatView(
                                agencyId = agency.id,
                                currentUserId = currentUserId,
                                currentNumericId = currentNumericId,
                                currentUserName = currentUserName,
                                currentUserAvatar = currentUserAvatar,
                                currentUserGender = currentUserGender,
                                currentUserRole = "AGENT",
                                colors = colors
                            )
                        }
                        AgencyTab.MEMBERS -> {
                            LazyColumn(
                                modifier = Modifier
                                    .fillMaxSize()
                                    .padding(14.dp),
                                verticalArrangement = Arrangement.spacedBy(10.dp)
                            ) {
                                if (membersList.isEmpty()) {
                                    item {
                                        Box(
                                            modifier = Modifier
                                                .fillMaxWidth()
                                                .padding(top = 40.dp),
                                            contentAlignment = Alignment.Center
                                        ) {
                                            Text("No members in agency yet", color = colors.textSecondary)
                                        }
                                    }
                                } else {
                                    items(membersList, key = { it.id }) { member ->
                                        AgencyMemberCard(
                                            member = member,
                                            isCurrentAgent = member.userId == currentUserId,
                                            colors = colors,
                                            onRemoveClick = { memberToRemove = member }
                                        )
                                    }
                                }
                            }
                        }

                        AgencyTab.APPLICATIONS -> {
                            LazyColumn(
                                modifier = Modifier
                                    .fillMaxSize()
                                    .padding(14.dp),
                                verticalArrangement = Arrangement.spacedBy(10.dp)
                            ) {
                                val pending = applicationsList.filter { it.status == "PENDING" }
                                if (pending.isEmpty()) {
                                    item {
                                        Box(
                                            modifier = Modifier
                                                .fillMaxWidth()
                                                .padding(top = 40.dp),
                                            contentAlignment = Alignment.Center
                                        ) {
                                            Text("No pending join applications", color = colors.textSecondary)
                                        }
                                    }
                                } else {
                                    items(pending, key = { it.id }) { app ->
                                        AgencyApplicationCard(
                                            application = app,
                                            colors = colors,
                                            onApprove = {
                                                scope.launch {
                                                    val ok = agencyService.reviewApplication(app, approve = true)
                                                    if (ok) {
                                                        AppToast.show("${app.userName} approved to join agency! 🎉")
                                                        refreshData()
                                                    }
                                                }
                                            },
                                            onReject = {
                                                scope.launch {
                                                    val ok = agencyService.reviewApplication(app, approve = false)
                                                    if (ok) {
                                                        AppToast.show("${app.userName}'s application rejected.")
                                                        refreshData()
                                                    }
                                                }
                                            }
                                        )
                                    }
                                }

                                // History of processed applications
                                val processed = applicationsList.filter { it.status != "PENDING" }
                                if (processed.isNotEmpty()) {
                                    item {
                                        Spacer(modifier = Modifier.height(14.dp))
                                        Text("Recent Application History", fontSize = 13.sp, fontWeight = FontWeight.Bold, color = colors.textSecondary)
                                    }
                                    items(processed, key = { "history_${it.id}" }) { app ->
                                        Surface(
                                            shape = RoundedCornerShape(12.dp),
                                            color = colors.cardBg,
                                            border = androidx.compose.foundation.BorderStroke(1.dp, colors.cardBorder),
                                            modifier = Modifier.fillMaxWidth()
                                        ) {
                                            Row(
                                                modifier = Modifier.padding(12.dp),
                                                verticalAlignment = Alignment.CenterVertically,
                                                horizontalArrangement = Arrangement.SpaceBetween
                                            ) {
                                                Column {
                                                    Text(app.userName, fontSize = 14.sp, fontWeight = FontWeight.Bold, color = colors.textPrimary)
                                                    Text("ID: ${app.userNumericId}", fontSize = 11.sp, color = colors.textSecondary)
                                                }
                                                Surface(
                                                    shape = RoundedCornerShape(6.dp),
                                                    color = if (app.status == "APPROVED") Color(0xFF4CAF50).copy(alpha = 0.2f) else Color(0xFFFF5252).copy(alpha = 0.2f)
                                                ) {
                                                    Text(
                                                        text = app.status,
                                                        color = if (app.status == "APPROVED") Color(0xFF4CAF50) else Color(0xFFFF5252),
                                                        fontSize = 11.sp,
                                                        fontWeight = FontWeight.Bold,
                                                        modifier = Modifier.padding(horizontal = 8.dp, vertical = 3.dp)
                                                    )
                                                }
                                            }
                                        }
                                    }
                                }
                            }
                        }

                        AgencyTab.SETTINGS -> {
                            var editName by remember { mutableStateOf(agency.agencyName) }
                            var editDesc by remember { mutableStateOf(agency.description) }
                            var isSavingSettings by remember { mutableStateOf(false) }

                            Column(
                                modifier = Modifier
                                    .fillMaxSize()
                                    .verticalScroll(rememberScrollState())
                                    .padding(16.dp)
                            ) {
                                Text("Edit Agency Profile", fontSize = 16.sp, fontWeight = FontWeight.Bold, color = colors.textPrimary)
                                Spacer(modifier = Modifier.height(14.dp))

                                OutlinedTextField(
                                    value = editName,
                                    onValueChange = { editName = it },
                                    label = { Text("Agency Name") },
                                    modifier = Modifier.fillMaxWidth(),
                                    colors = OutlinedTextFieldDefaults.colors(
                                        focusedTextColor = colors.textPrimary,
                                        unfocusedTextColor = colors.textPrimary,
                                        focusedBorderColor = QivoOrange,
                                        unfocusedBorderColor = colors.cardBorder
                                    )
                                )

                                Spacer(modifier = Modifier.height(12.dp))

                                OutlinedTextField(
                                    value = editDesc,
                                    onValueChange = { editDesc = it },
                                    label = { Text("Agency Description") },
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .height(100.dp),
                                    colors = OutlinedTextFieldDefaults.colors(
                                        focusedTextColor = colors.textPrimary,
                                        unfocusedTextColor = colors.textPrimary,
                                        focusedBorderColor = QivoOrange,
                                        unfocusedBorderColor = colors.cardBorder
                                    )
                                )

                                Spacer(modifier = Modifier.height(20.dp))

                                Button(
                                    onClick = {
                                        isSavingSettings = true
                                        scope.launch {
                                            val ok = agencyService.updateAgencyInfo(
                                                agencyId = agency.id,
                                                name = editName,
                                                description = editDesc,
                                                logoUrl = agency.logoUrl
                                            )
                                            isSavingSettings = false
                                            if (ok) {
                                                AppToast.show("Agency info updated!")
                                                refreshData()
                                            }
                                        }
                                    },
                                    enabled = !isSavingSettings,
                                    modifier = Modifier.fillMaxWidth(),
                                    shape = RoundedCornerShape(12.dp),
                                    colors = ButtonDefaults.buttonColors(containerColor = QivoOrange)
                                ) {
                                    if (isSavingSettings) {
                                        CircularProgressIndicator(color = Color.White, modifier = Modifier.size(18.dp))
                                    } else {
                                        Text("Save Changes", fontWeight = FontWeight.Bold)
                                    }
                                }
                            }
                        }
                    }
                }
            } else {
                // ==========================================
                // REGULAR USER VIEW: JOIN AGENCY / MY AGENCY
                // ==========================================
                val membership = myMembership
                val activeAgency = myAgency
                val application = myLatestApplication

                if (membership != null && activeAgency != null) {
                    // User is an approved Member of an Agency: Show Tabs (Chat | Members | Agency Info)
                    var memberSelectedTab by remember { mutableStateOf(AgencyTab.GROUP_CHAT) }

                    Column(modifier = Modifier.fillMaxSize()) {
                        TabRow(
                            selectedTabIndex = when (memberSelectedTab) {
                                AgencyTab.GROUP_CHAT -> 0
                                AgencyTab.MEMBERS -> 1
                                else -> 2
                            },
                            containerColor = colors.topBarBg,
                            contentColor = QivoOrange
                        ) {
                            Tab(
                                selected = memberSelectedTab == AgencyTab.GROUP_CHAT,
                                onClick = { memberSelectedTab = AgencyTab.GROUP_CHAT },
                                text = { Text("Chat 💬", fontWeight = FontWeight.Bold) }
                            )
                            Tab(
                                selected = memberSelectedTab == AgencyTab.MEMBERS,
                                onClick = { memberSelectedTab = AgencyTab.MEMBERS },
                                text = { Text("Members (${membersList.size})", fontWeight = FontWeight.Bold) }
                            )
                            Tab(
                                selected = memberSelectedTab == AgencyTab.SETTINGS,
                                onClick = { memberSelectedTab = AgencyTab.SETTINGS },
                                text = { Text("Agency Info ℹ️", fontWeight = FontWeight.Bold) }
                            )
                        }

                        when (memberSelectedTab) {
                            AgencyTab.GROUP_CHAT -> {
                                AgencyGroupChatView(
                                    agencyId = activeAgency.id,
                                    currentUserId = currentUserId,
                                    currentNumericId = currentNumericId,
                                    currentUserName = currentUserName,
                                    currentUserAvatar = currentUserAvatar,
                                    currentUserGender = currentUserGender,
                                    currentUserRole = "MEMBER",
                                    colors = colors
                                )
                            }
                            AgencyTab.MEMBERS -> {
                                LazyColumn(
                                    modifier = Modifier
                                        .fillMaxSize()
                                        .padding(14.dp),
                                    verticalArrangement = Arrangement.spacedBy(10.dp)
                                ) {
                                    items(membersList, key = { it.id }) { m ->
                                        AgencyMemberCard(
                                            member = m,
                                            isCurrentAgent = false,
                                            colors = colors,
                                            onRemoveClick = {}
                                        )
                                    }
                                }
                            }
                            else -> {
                                Column(
                                    modifier = Modifier
                                        .fillMaxSize()
                                        .verticalScroll(rememberScrollState())
                                        .padding(16.dp),
                                    horizontalAlignment = Alignment.CenterHorizontally
                                ) {
                                    Surface(
                                        shape = RoundedCornerShape(18.dp),
                                        color = colors.cardBg,
                                        border = androidx.compose.foundation.BorderStroke(1.dp, colors.cardBorder),
                                        modifier = Modifier.fillMaxWidth()
                                    ) {
                                        Column(modifier = Modifier.padding(20.dp), horizontalAlignment = Alignment.CenterHorizontally) {
                                            if (activeAgency.logoUrl.isNotBlank()) {
                                                AsyncImage(
                                                    model = activeAgency.logoUrl,
                                                    contentDescription = activeAgency.agencyName,
                                                    modifier = Modifier
                                                        .size(80.dp)
                                                        .clip(CircleShape)
                                                        .border(2.dp, QivoOrange, CircleShape),
                                                    contentScale = ContentScale.Crop
                                                )
                                            } else {
                                                Surface(
                                                    shape = CircleShape,
                                                    color = QivoOrange.copy(alpha = 0.2f),
                                                    modifier = Modifier.size(80.dp)
                                                ) {
                                                    Box(contentAlignment = Alignment.Center) {
                                                        Icon(Icons.Default.Business, contentDescription = null, tint = QivoOrange, modifier = Modifier.size(40.dp))
                                                    }
                                                }
                                            }

                                            Spacer(modifier = Modifier.height(12.dp))
                                            Text(activeAgency.agencyName, fontSize = 20.sp, fontWeight = FontWeight.Bold, color = colors.textPrimary)
                                            Spacer(modifier = Modifier.height(4.dp))
                                            Text("Agency Code: ${activeAgency.agencyCode}", fontSize = 13.sp, color = QivoYellow, fontWeight = FontWeight.SemiBold)

                                            if (activeAgency.description.isNotBlank()) {
                                                Spacer(modifier = Modifier.height(8.dp))
                                                Text(
                                                    activeAgency.description,
                                                    fontSize = 13.sp,
                                                    color = colors.textSecondary,
                                                    textAlign = androidx.compose.ui.text.style.TextAlign.Center
                                                )
                                            }

                                            Spacer(modifier = Modifier.height(16.dp))

                                            Surface(
                                                shape = RoundedCornerShape(10.dp),
                                                color = Color(0xFF4CAF50).copy(alpha = 0.15f)
                                            ) {
                                                Row(
                                                    modifier = Modifier.padding(horizontal = 12.dp, vertical = 6.dp),
                                                    verticalAlignment = Alignment.CenterVertically
                                                ) {
                                                    Icon(Icons.Default.CheckCircle, contentDescription = null, tint = Color(0xFF4CAF50), modifier = Modifier.size(16.dp))
                                                    Spacer(modifier = Modifier.width(6.dp))
                                                    Text("Official Agency Member", color = Color(0xFF4CAF50), fontSize = 12.sp, fontWeight = FontWeight.Bold)
                                                }
                                            }
                                        }
                                    }
                                }
                            }
                        }
                    }
                } else {
                    Column(
                        modifier = Modifier
                            .fillMaxSize()
                            .verticalScroll(rememberScrollState())
                            .padding(16.dp),
                        horizontalAlignment = Alignment.CenterHorizontally
                    ) {
                        // User is NOT yet in an agency: Show Application Status & Join Form

                        // 1. If user has a pending or rejected application
                        if (application != null) {
                            if (application.status == "PENDING") {
                                Surface(
                                    shape = RoundedCornerShape(16.dp),
                                    color = Color(0xFFFF9800).copy(alpha = 0.15f),
                                    border = androidx.compose.foundation.BorderStroke(1.dp, QivoOrange),
                                    modifier = Modifier.fillMaxWidth()
                                ) {
                                    Column(modifier = Modifier.padding(16.dp)) {
                                        Row(verticalAlignment = Alignment.CenterVertically) {
                                            Icon(Icons.Default.HourglassTop, contentDescription = null, tint = QivoOrange)
                                            Spacer(modifier = Modifier.width(8.dp))
                                            Text("Application Under Review", fontWeight = FontWeight.Bold, fontSize = 15.sp, color = colors.textPrimary)
                                        }
                                        Spacer(modifier = Modifier.height(8.dp))
                                        Text(
                                            "Your application to join ${application.agencyName} (Code: ${application.agencyCode}) is pending Agent review.",
                                            fontSize = 13.sp,
                                            color = colors.textSecondary
                                        )
                                    }
                                }
                                Spacer(modifier = Modifier.height(20.dp))
                            } else if (application.status == "REJECTED") {
                                Surface(
                                    shape = RoundedCornerShape(16.dp),
                                    color = Color(0xFFFF5252).copy(alpha = 0.15f),
                                    border = androidx.compose.foundation.BorderStroke(1.dp, Color(0xFFFF5252)),
                                    modifier = Modifier.fillMaxWidth()
                                ) {
                                    Column(modifier = Modifier.padding(16.dp)) {
                                        Row(verticalAlignment = Alignment.CenterVertically) {
                                            Icon(Icons.Default.Cancel, contentDescription = null, tint = Color(0xFFFF5252))
                                            Spacer(modifier = Modifier.width(8.dp))
                                            Text("Application Declined", fontWeight = FontWeight.Bold, fontSize = 15.sp, color = Color(0xFFFF5252))
                                        }
                                        Spacer(modifier = Modifier.height(8.dp))
                                        Text(
                                            "Your previous application to join ${application.agencyName} was declined by the Agent. You can apply again or join a different agency with a valid Agency Code below.",
                                            fontSize = 13.sp,
                                            color = colors.textSecondary
                                        )
                                    }
                                }
                                Spacer(modifier = Modifier.height(20.dp))
                            }
                        }

                        // 2. Join Agency with Code Section (Exclusively for female accounts)
                        if (isFemaleAccount) {
                            Surface(
                                shape = RoundedCornerShape(18.dp),
                                color = colors.cardBg,
                                border = androidx.compose.foundation.BorderStroke(1.dp, colors.cardBorder),
                                modifier = Modifier.fillMaxWidth()
                            ) {
                                Column(modifier = Modifier.padding(20.dp)) {
                                    Text(
                                        "Join an Agency",
                                        fontSize = 18.sp,
                                        fontWeight = FontWeight.Bold,
                                        color = colors.textPrimary
                                    )
                                    Spacer(modifier = Modifier.height(4.dp))
                                    Text(
                                        "Enter the unique Agency Code.",
                                        fontSize = 13.sp,
                                        color = colors.textSecondary
                                    )

                                Spacer(modifier = Modifier.height(16.dp))

                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    OutlinedTextField(
                                        value = joinCodeInput,
                                        onValueChange = {
                                            joinCodeInput = it.uppercase()
                                            searchedAgency = null
                                        },
                                        placeholder = { Text("e.g. AG739104") },
                                        singleLine = true,
                                        modifier = Modifier
                                            .weight(1f)
                                            .testTag("join_agency_code_input"),
                                        colors = OutlinedTextFieldDefaults.colors(
                                            focusedTextColor = colors.textPrimary,
                                            unfocusedTextColor = colors.textPrimary,
                                            focusedBorderColor = QivoOrange,
                                            unfocusedBorderColor = colors.cardBorder
                                        )
                                    )
                                    Spacer(modifier = Modifier.width(8.dp))
                                    Button(
                                        onClick = {
                                            if (joinCodeInput.isBlank()) {
                                                AppToast.show("Enter an Agency Code")
                                                return@Button
                                            }
                                            isSearchingAgency = true
                                            scope.launch {
                                                val found = agencyService.fetchAgencyByCode(joinCodeInput)
                                                isSearchingAgency = false
                                                if (found != null) {
                                                    searchedAgency = found
                                                } else {
                                                    AppToast.show("No agency found with code: $joinCodeInput")
                                                }
                                            }
                                        },
                                        shape = RoundedCornerShape(12.dp),
                                        colors = ButtonDefaults.buttonColors(containerColor = QivoOrange)
                                    ) {
                                        if (isSearchingAgency) {
                                            CircularProgressIndicator(color = Color.White, modifier = Modifier.size(16.dp))
                                        } else {
                                            Text("Search", fontWeight = FontWeight.Bold)
                                        }
                                    }
                                }

                                // If agency found, show preview and Apply button
                                val agency = searchedAgency
                                if (agency != null) {
                                    Spacer(modifier = Modifier.height(16.dp))
                                    Surface(
                                        shape = RoundedCornerShape(14.dp),
                                        color = colors.screenBg,
                                        border = androidx.compose.foundation.BorderStroke(1.dp, QivoOrange),
                                        modifier = Modifier.fillMaxWidth()
                                    ) {
                                        Column(modifier = Modifier.padding(14.dp)) {
                                            Row(verticalAlignment = Alignment.CenterVertically) {
                                                if (agency.logoUrl.isNotBlank()) {
                                                    AsyncImage(
                                                        model = agency.logoUrl,
                                                        contentDescription = agency.agencyName,
                                                        modifier = Modifier
                                                            .size(50.dp)
                                                            .clip(CircleShape)
                                                            .border(1.5.dp, QivoOrange, CircleShape),
                                                        contentScale = ContentScale.Crop
                                                    )
                                                } else {
                                                    Surface(
                                                        shape = CircleShape,
                                                        color = QivoOrange.copy(alpha = 0.2f),
                                                        modifier = Modifier.size(50.dp)
                                                    ) {
                                                        Box(contentAlignment = Alignment.Center) {
                                                            Icon(Icons.Default.Business, contentDescription = null, tint = QivoOrange)
                                                        }
                                                    }
                                                }

                                                Spacer(modifier = Modifier.width(12.dp))

                                                Column(modifier = Modifier.weight(1f)) {
                                                    Text(agency.agencyName, fontWeight = FontWeight.Bold, fontSize = 15.sp, color = colors.textPrimary)
                                                    Text("Code: ${agency.agencyCode}", fontSize = 12.sp, color = QivoYellow, fontWeight = FontWeight.SemiBold)
                                                }
                                            }

                                            if (agency.description.isNotBlank()) {
                                                Spacer(modifier = Modifier.height(8.dp))
                                                Text(agency.description, fontSize = 12.sp, color = colors.textSecondary)
                                            }

                                            Spacer(modifier = Modifier.height(12.dp))

                                            Button(
                                                onClick = {
                                                    isSubmittingApplication = true
                                                    scope.launch {
                                                        val (ok, msg) = agencyService.applyToAgency(
                                                            agency = agency,
                                                            user = UserProfile(
                                                                id = currentUserId,
                                                                numericId = currentNumericId,
                                                                name = currentUserName,
                                                                gender = currentUserGender,
                                                                country = "",
                                                                avatarUrl = currentUserAvatar
                                                            )
                                                        )
                                                        isSubmittingApplication = false
                                                        AppToast.show(msg, isLong = true)
                                                        if (ok) {
                                                            searchedAgency = null
                                                            joinCodeInput = ""
                                                            refreshData()
                                                        }
                                                    }
                                                },
                                                enabled = !isSubmittingApplication,
                                                modifier = Modifier.fillMaxWidth(),
                                                shape = RoundedCornerShape(12.dp),
                                                colors = ButtonDefaults.buttonColors(containerColor = QivoOrange)
                                            ) {
                                                if (isSubmittingApplication) {
                                                    CircularProgressIndicator(color = Color.White, modifier = Modifier.size(16.dp))
                                                } else {
                                                    Icon(Icons.Default.Send, contentDescription = null)
                                                    Spacer(modifier = Modifier.width(6.dp))
                                                    Text("Apply to Join", fontWeight = FontWeight.Bold)
                                                }
                                            }
                                        }
                                    }
                                }
                            }
                        }
                    }
                }
            }
        }
        }

        // ==========================================
        // DIALOG: CONFIRM MEMBER REMOVAL (Agent only)
        // ==========================================
        if (memberToRemove != null) {
            val m = memberToRemove!!
            AlertDialog(
                onDismissRequest = { memberToRemove = null },
                title = { Text("Remove Member?", fontWeight = FontWeight.Bold, color = colors.textPrimary) },
                text = { Text("Are you sure you want to remove ${m.userName} (ID: ${m.userNumericId}) from the agency?", color = colors.textSecondary) },
                confirmButton = {
                    Button(
                        onClick = {
                            val agencyId = myAgency?.id ?: ""
                            memberToRemove = null
                            scope.launch {
                                agencyService.removeMember(agencyId, m.userId)
                                AppToast.show("${m.userName} removed from agency")
                                refreshData()
                            }
                        },
                        colors = ButtonDefaults.buttonColors(containerColor = Color(0xFFD32F2F))
                    ) {
                        Text("Remove", color = Color.White, fontWeight = FontWeight.Bold)
                    }
                },
                dismissButton = {
                    TextButton(onClick = { memberToRemove = null }) {
                        Text("Cancel", color = colors.textSecondary)
                    }
                },
                containerColor = colors.cardBg,
                shape = RoundedCornerShape(16.dp)
            )
        }
    }
}

@Composable
private fun AgencyMemberCard(
    member: AgencyMember,
    isCurrentAgent: Boolean,
    colors: com.example.ui.theme.AppColors,
    onRemoveClick: () -> Unit
) {
    Surface(
        shape = RoundedCornerShape(14.dp),
        color = colors.cardBg,
        border = androidx.compose.foundation.BorderStroke(1.dp, colors.cardBorder),
        modifier = Modifier.fillMaxWidth()
    ) {
        Row(
            modifier = Modifier.padding(12.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            val mascotRes = AvatarHelper.getDrawableForMascotKey(member.userAvatarUrl)
            if (mascotRes != null) {
                Image(
                    painter = painterResource(id = mascotRes),
                    contentDescription = member.userName,
                    modifier = Modifier
                        .size(46.dp)
                        .clip(CircleShape)
                        .border(1.5.dp, QivoOrange, CircleShape),
                    contentScale = ContentScale.Crop
                )
            } else if (member.userAvatarUrl.isNotBlank()) {
                AsyncImage(
                    model = member.userAvatarUrl,
                    contentDescription = member.userName,
                    modifier = Modifier
                        .size(46.dp)
                        .clip(CircleShape)
                        .border(1.5.dp, QivoOrange, CircleShape),
                    contentScale = ContentScale.Crop
                )
            } else {
                val defRes = AvatarHelper.getDefaultMascotRes(userId = member.userId, gender = member.userGender)
                Image(
                    painter = painterResource(id = defRes),
                    contentDescription = member.userName,
                    modifier = Modifier
                        .size(46.dp)
                        .clip(CircleShape)
                        .border(1.5.dp, QivoOrange, CircleShape),
                    contentScale = ContentScale.Crop
                )
            }

            Spacer(modifier = Modifier.width(12.dp))

            Column(modifier = Modifier.weight(1f)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        text = member.userName,
                        fontWeight = FontWeight.Bold,
                        fontSize = 14.sp,
                        color = colors.textPrimary
                    )
                    if (member.role == "OWNER" || member.role == "AGENT") {
                        Spacer(modifier = Modifier.width(6.dp))
                        Surface(
                            shape = RoundedCornerShape(4.dp),
                            color = QivoOrange.copy(alpha = 0.2f)
                        ) {
                            Text(
                                text = "AGENT / HOST",
                                color = QivoOrange,
                                fontSize = 9.sp,
                                fontWeight = FontWeight.Bold,
                                modifier = Modifier.padding(horizontal = 4.dp, vertical = 1.dp)
                            )
                        }
                    }
                }
                Spacer(modifier = Modifier.height(2.dp))
                Text(
                    text = "ID: ${member.userNumericId}",
                    fontSize = 11.sp,
                    color = colors.textSecondary
                )
            }

            if (isCurrentAgent && member.role != "OWNER" && member.role != "AGENT") {
                IconButton(onClick = onRemoveClick) {
                    Icon(Icons.Default.PersonRemove, contentDescription = "Remove", tint = Color(0xFFFF5252), modifier = Modifier.size(20.dp))
                }
            }
        }
    }
}

@Composable
private fun AgencyApplicationCard(
    application: AgencyApplication,
    colors: com.example.ui.theme.AppColors,
    onApprove: () -> Unit,
    onReject: () -> Unit
) {
    Surface(
        shape = RoundedCornerShape(14.dp),
        color = colors.cardBg,
        border = androidx.compose.foundation.BorderStroke(1.dp, colors.cardBorder),
        modifier = Modifier.fillMaxWidth()
    ) {
        Row(
            modifier = Modifier.padding(12.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            val mascotRes = AvatarHelper.getDrawableForMascotKey(application.userAvatarUrl)
            if (mascotRes != null) {
                Image(
                    painter = painterResource(id = mascotRes),
                    contentDescription = application.userName,
                    modifier = Modifier
                        .size(46.dp)
                        .clip(CircleShape)
                        .border(1.5.dp, QivoOrange, CircleShape),
                    contentScale = ContentScale.Crop
                )
            } else if (application.userAvatarUrl.isNotBlank()) {
                AsyncImage(
                    model = application.userAvatarUrl,
                    contentDescription = application.userName,
                    modifier = Modifier
                        .size(46.dp)
                        .clip(CircleShape)
                        .border(1.5.dp, QivoOrange, CircleShape),
                    contentScale = ContentScale.Crop
                )
            } else {
                val defRes = AvatarHelper.getDefaultMascotRes(userId = application.userId, gender = application.userGender)
                Image(
                    painter = painterResource(id = defRes),
                    contentDescription = application.userName,
                    modifier = Modifier
                        .size(46.dp)
                        .clip(CircleShape)
                        .border(1.5.dp, QivoOrange, CircleShape),
                    contentScale = ContentScale.Crop
                )
            }

            Spacer(modifier = Modifier.width(12.dp))

            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = application.userName,
                    fontWeight = FontWeight.Bold,
                    fontSize = 14.sp,
                    color = colors.textPrimary
                )
                Spacer(modifier = Modifier.height(2.dp))
                Text(
                    text = "ID: ${application.userNumericId}",
                    fontSize = 11.sp,
                    color = colors.textSecondary
                )
            }

            // Approve & Reject Buttons
            Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                Surface(
                    onClick = onReject,
                    shape = CircleShape,
                    color = Color(0xFFFF5252).copy(alpha = 0.2f),
                    modifier = Modifier.size(36.dp)
                ) {
                    Box(contentAlignment = Alignment.Center) {
                        Icon(Icons.Default.Close, contentDescription = "Reject", tint = Color(0xFFFF5252), modifier = Modifier.size(18.dp))
                    }
                }

                Surface(
                    onClick = onApprove,
                    shape = CircleShape,
                    color = Color(0xFF4CAF50).copy(alpha = 0.2f),
                    modifier = Modifier.size(36.dp)
                ) {
                    Box(contentAlignment = Alignment.Center) {
                        Icon(Icons.Default.Check, contentDescription = "Approve", tint = Color(0xFF4CAF50), modifier = Modifier.size(18.dp))
                    }
                }
            }
        }
    }
}

@Composable
fun AgencyGroupChatView(
    agencyId: String,
    currentUserId: String,
    currentNumericId: Long,
    currentUserName: String,
    currentUserAvatar: String,
    currentUserGender: String,
    currentUserRole: String,
    colors: AppColors
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val agencyService = remember { SupabaseAgencyService() }
    var messages by remember { mutableStateOf<List<AgencyGroupMessage>>(emptyList()) }
    var messageText by remember { mutableStateOf("") }
    var isSending by remember { mutableStateOf(false) }
    val listState = androidx.compose.foundation.lazy.rememberLazyListState()

    // Real-time polling
    LaunchedEffect(agencyId) {
        while (isActive) {
            val list = agencyService.fetchAgencyGroupMessages(agencyId, context)
            if (list != messages) {
                messages = list
            }
            kotlinx.coroutines.delay(3000L)
        }
    }

    // Scroll to bottom when new messages arrive
    LaunchedEffect(messages.size) {
        if (messages.isNotEmpty()) {
            listState.animateScrollToItem(messages.size - 1)
        }
    }

    fun sendMessage() {
        val text = messageText.trim()
        if (text.isBlank() || isSending) return
        isSending = true
        messageText = ""
        scope.launch {
            val (ok, _) = agencyService.sendAgencyGroupMessage(
                agencyId = agencyId,
                senderId = currentUserId,
                senderNumericId = currentNumericId,
                senderName = currentUserName,
                senderAvatar = currentUserAvatar,
                senderRole = currentUserRole,
                senderGender = currentUserGender,
                messageText = text,
                context = context
            )
            isSending = false
            if (ok) {
                val updated = agencyService.fetchAgencyGroupMessages(agencyId, context)
                messages = updated
            }
        }
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(colors.screenBg)
    ) {
        // Free Chat Header Banner
        Surface(
            color = QivoOrange.copy(alpha = 0.12f),
            modifier = Modifier.fillMaxWidth()
        ) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 14.dp, vertical = 8.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Icon(
                    imageVector = Icons.Default.Lock,
                    contentDescription = null,
                    tint = QivoOrange,
                    modifier = Modifier.size(16.dp)
                )
                Spacer(modifier = Modifier.width(8.dp))
                Text(
                    text = "Free Agency Chat • All Members Chat For Free (0 Coins)",
                    fontSize = 12.sp,
                    fontWeight = FontWeight.Bold,
                    color = QivoOrange
                )
            }
        }

        // Messages List
        LazyColumn(
            state = listState,
            modifier = Modifier
                .weight(1f)
                .fillMaxWidth()
                .padding(horizontal = 14.dp, vertical = 8.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            if (messages.isEmpty()) {
                item {
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(top = 60.dp),
                        contentAlignment = Alignment.Center
                    ) {
                        Column(horizontalAlignment = Alignment.CenterHorizontally) {
                            Icon(
                                Icons.Default.ChatBubbleOutline,
                                contentDescription = null,
                                tint = colors.textSecondary,
                                modifier = Modifier.size(48.dp)
                            )
                            Spacer(modifier = Modifier.height(10.dp))
                            Text(
                                "No messages yet",
                                fontSize = 16.sp,
                                fontWeight = FontWeight.Bold,
                                color = colors.textPrimary
                            )
                            Spacer(modifier = Modifier.height(4.dp))
                            Text(
                                "Say hello to your agency team for free!",
                                fontSize = 12.sp,
                                color = colors.textSecondary
                            )
                        }
                    }
                }
            } else {
                items(messages, key = { it.id }) { msg ->
                    val isMe = msg.senderId == currentUserId
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = if (isMe) Arrangement.End else Arrangement.Start,
                        verticalAlignment = Alignment.Bottom
                    ) {
                        if (!isMe) {
                            if (msg.senderAvatar.isNotBlank()) {
                                AsyncImage(
                                    model = msg.senderAvatar,
                                    contentDescription = msg.senderName,
                                    modifier = Modifier
                                        .size(32.dp)
                                        .clip(CircleShape)
                                        .border(1.dp, QivoOrange, CircleShape),
                                    contentScale = ContentScale.Crop
                                )
                            } else {
                                Surface(
                                    shape = CircleShape,
                                    color = QivoOrange.copy(alpha = 0.2f),
                                    modifier = Modifier.size(32.dp)
                                ) {
                                    Box(contentAlignment = Alignment.Center) {
                                        Text(
                                            msg.senderName.take(1).uppercase(),
                                            fontWeight = FontWeight.Bold,
                                            fontSize = 13.sp,
                                            color = QivoOrange
                                        )
                                    }
                                }
                            }
                            Spacer(modifier = Modifier.width(8.dp))
                        }

                        Column(
                            horizontalAlignment = if (isMe) Alignment.End else Alignment.Start,
                            modifier = Modifier.widthIn(max = 280.dp)
                        ) {
                            if (!isMe) {
                                Row(
                                    verticalAlignment = Alignment.CenterVertically,
                                    modifier = Modifier.padding(bottom = 2.dp)
                                ) {
                                    Text(
                                        text = msg.senderName,
                                        fontSize = 11.sp,
                                        fontWeight = FontWeight.Bold,
                                        color = colors.textSecondary
                                    )
                                    Spacer(modifier = Modifier.width(4.dp))
                                    if (msg.senderRole.equals("AGENT", ignoreCase = true) || msg.senderRole.equals("OWNER", ignoreCase = true)) {
                                        Surface(
                                            shape = RoundedCornerShape(3.dp),
                                            color = QivoOrange
                                        ) {
                                            Text(
                                                text = "AGENT",
                                                color = Color.White,
                                                fontSize = 8.sp,
                                                fontWeight = FontWeight.ExtraBold,
                                                modifier = Modifier.padding(horizontal = 4.dp, vertical = 1.dp)
                                            )
                                        }
                                    } else {
                                        Surface(
                                            shape = RoundedCornerShape(3.dp),
                                            color = Color(0xFF4CAF50).copy(alpha = 0.2f)
                                        ) {
                                            Text(
                                                text = "MEMBER",
                                                color = Color(0xFF4CAF50),
                                                fontSize = 8.sp,
                                                fontWeight = FontWeight.Bold,
                                                modifier = Modifier.padding(horizontal = 4.dp, vertical = 1.dp)
                                            )
                                        }
                                    }
                                }
                            }

                            Surface(
                                shape = RoundedCornerShape(
                                    topStart = 16.dp,
                                    topEnd = 16.dp,
                                    bottomStart = if (isMe) 16.dp else 4.dp,
                                    bottomEnd = if (isMe) 4.dp else 16.dp
                                ),
                                color = if (isMe) QivoOrange else colors.cardBg,
                                border = if (isMe) null else androidx.compose.foundation.BorderStroke(1.dp, colors.cardBorder)
                            ) {
                                Text(
                                    text = msg.message,
                                    color = if (isMe) Color.White else colors.textPrimary,
                                    fontSize = 14.sp,
                                    fontWeight = FontWeight.Light,
                                    letterSpacing = 0.2.sp,
                                    modifier = Modifier.padding(horizontal = 12.dp, vertical = 8.dp)
                                )
                            }
                        }
                    }
                }
            }
        }

        // Message Input Field Bar
        Surface(
            color = colors.cardBg,
            border = androidx.compose.foundation.BorderStroke(1.dp, colors.cardBorder),
            modifier = Modifier.fillMaxWidth()
        ) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 12.dp, vertical = 8.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                OutlinedTextField(
                    value = messageText,
                    onValueChange = { messageText = it },
                    placeholder = {
                        Text(
                            "Type a free group message...",
                            color = colors.textSecondary,
                            fontSize = 14.sp
                        )
                    },
                    singleLine = false,
                    maxLines = 3,
                    shape = RoundedCornerShape(20.dp),
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedContainerColor = colors.inputBg,
                        unfocusedContainerColor = colors.inputBg,
                        focusedBorderColor = QivoOrange,
                        unfocusedBorderColor = colors.cardBorder,
                        focusedTextColor = colors.textPrimary,
                        unfocusedTextColor = colors.textPrimary
                    ),
                    modifier = Modifier
                        .weight(1f)
                        .padding(end = 8.dp)
                        .testTag("agency_group_chat_input")
                )

                IconButton(
                    onClick = { sendMessage() },
                    enabled = messageText.isNotBlank() && !isSending,
                    modifier = Modifier
                        .size(44.dp)
                        .background(
                            if (messageText.isNotBlank()) QivoOrange else Color.Gray.copy(alpha = 0.3f),
                            CircleShape
                        )
                        .testTag("agency_group_chat_send_button")
                ) {
                    if (isSending) {
                        CircularProgressIndicator(
                            color = Color.White,
                            modifier = Modifier.size(18.dp),
                            strokeWidth = 2.dp
                        )
                    } else {
                        Icon(
                            Icons.Default.Send,
                            contentDescription = "Send",
                            tint = Color.White,
                            modifier = Modifier.size(20.dp)
                        )
                    }
                }
            }
        }
    }
}

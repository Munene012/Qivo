package com.example.ui.screens
import com.example.ui.components.AppToast
import com.example.ui.components.GiftPadBottomSheet

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.net.Uri
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.ArrowForward
import androidx.compose.material.icons.automirrored.filled.Send
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
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
import com.example.data.*
import com.example.ui.theme.AppTheme
import com.example.ui.theme.QivoOrange
import com.example.ui.theme.QivoYellow
import com.example.ui.components.QivoAgency3DHeroBadge
import kotlinx.coroutines.launch

enum class AgencyFullscreenView {
    NONE,
    GROUP_CHAT,
    APPLICATIONS,
    MEMBERS,
    INFO
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
    initialView: AgencyFullscreenView = AgencyFullscreenView.NONE,
    onBackClick: () -> Unit,
    onOpenRechargeWallet: () -> Unit = {},
    onAgentStatusChanged: (Boolean) -> Unit = {}
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

    // Agent specific lists & group chat messages
    var membersList by remember { mutableStateOf<List<AgencyMember>>(emptyList()) }
    var applicationsList by remember { mutableStateOf<List<AgencyApplication>>(emptyList()) }
    var groupMessagesList by remember { mutableStateOf<List<AgencyGroupMessage>>(emptyList()) }

    // Active Fullscreen view state
    var activeFullscreenView by remember { mutableStateOf(initialView) }

    // Intercept system back button when in fullscreen agency sub-views (returns to agency dashboard)
    BackHandler(enabled = activeFullscreenView != AgencyFullscreenView.NONE) {
        activeFullscreenView = AgencyFullscreenView.NONE
    }

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
                groupMessagesList = agencyService.fetchAgencyGroupMessages(agency.id)
            }
        } else {
            val membership = agencyService.fetchUserAgencyMembership(currentUserId)
            myMembership = membership
            if (membership != null) {
                myAgency = agencyService.fetchAgencyById(membership.agencyId)
                val targetAgencyId = myAgency?.id ?: membership.agencyId
                if (targetAgencyId.isNotBlank()) {
                    membersList = agencyService.fetchAgencyMembers(targetAgencyId)
                    groupMessagesList = agencyService.fetchAgencyGroupMessages(targetAgencyId)
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

    val activeAgencyObj = myAgency ?: myMembership
    val agencyIdStr = when (activeAgencyObj) {
        is Agency -> activeAgencyObj.id
        is AgencyMember -> activeAgencyObj.agencyId
        else -> ""
    }
    val agencyNameStr = myAgency?.agencyName ?: "Agency"

    // =========================================================================
    // DEDICATED FULLSCREEN VIEWS FOR EACH AGENCY OPTION
    // =========================================================================
    if (activeFullscreenView != AgencyFullscreenView.NONE) {
        when (activeFullscreenView) {
            AgencyFullscreenView.GROUP_CHAT -> {
                Scaffold(
                    topBar = {
                        TopAppBar(
                            title = {
                                Column {
                                    Text(agencyNameStr, fontWeight = FontWeight.Bold, color = colors.textPrimary, fontSize = 16.sp)
                                    Text("Official Group Chat • ${groupMessagesList.size} messages", fontSize = 11.sp, color = colors.textSecondary)
                                }
                            },
                            navigationIcon = {
                                IconButton(onClick = { activeFullscreenView = AgencyFullscreenView.NONE }) {
                                    Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back", tint = colors.textPrimary)
                                }
                            },
                            actions = {
                                IconButton(onClick = { scope.launch { refreshData() } }) {
                                    Icon(Icons.Default.Refresh, contentDescription = "Refresh", tint = colors.textPrimary)
                                }
                            },
                            colors = TopAppBarDefaults.topAppBarColors(containerColor = colors.topBarBg)
                        )
                    },
                    containerColor = colors.screenBg
                ) { padding ->
                    Box(modifier = Modifier.fillMaxSize().padding(padding)) {
                        AgencyGroupChatView(
                            agencyId = agencyIdStr,
                            currentUserId = currentUserId,
                            currentNumericId = currentNumericId,
                            currentUserName = currentUserName,
                            currentUserAvatar = currentUserAvatar,
                            currentUserGender = currentUserGender,
                            currentUserRole = if (isAgent) "AGENT" else "MEMBER",
                            colors = colors
                        )
                    }
                }
                return
            }

            AgencyFullscreenView.MEMBERS -> {
                Scaffold(
                    topBar = {
                        TopAppBar(
                            title = {
                                Text("Agency Members (${membersList.size})", fontWeight = FontWeight.Bold, color = colors.textPrimary, fontSize = 17.sp)
                            },
                            navigationIcon = {
                                IconButton(onClick = { activeFullscreenView = AgencyFullscreenView.NONE }) {
                                    Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back", tint = colors.textPrimary)
                                }
                            },
                            colors = TopAppBarDefaults.topAppBarColors(containerColor = colors.topBarBg)
                        )
                    },
                    containerColor = colors.screenBg
                ) { padding ->
                    Box(modifier = Modifier.fillMaxSize().padding(padding)) {
                        LazyColumn(
                            modifier = Modifier
                                .fillMaxSize()
                                .padding(16.dp),
                            verticalArrangement = Arrangement.spacedBy(10.dp)
                        ) {
                            if (membersList.isEmpty()) {
                                item {
                                    Box(
                                        modifier = Modifier.fillMaxWidth().padding(top = 40.dp),
                                        contentAlignment = Alignment.Center
                                    ) {
                                        Text("No members in agency yet", color = colors.textSecondary)
                                    }
                                }
                            } else {
                                items(membersList, key = { it.id }) { member ->
                                    AgencyMemberCard(
                                        member = member,
                                        isCurrentAgent = isAgent,
                                        colors = colors,
                                        onRemoveClick = { memberToRemove = member }
                                    )
                                }
                            }
                        }
                    }
                }
                return
            }

            AgencyFullscreenView.APPLICATIONS -> {
                val pendingCount = applicationsList.count { it.status == "PENDING" }
                Scaffold(
                    topBar = {
                        TopAppBar(
                            title = {
                                Text("Join Applications ($pendingCount Pending)", fontWeight = FontWeight.Bold, color = colors.textPrimary, fontSize = 17.sp)
                            },
                            navigationIcon = {
                                IconButton(onClick = { activeFullscreenView = AgencyFullscreenView.NONE }) {
                                    Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back", tint = colors.textPrimary)
                                }
                            },
                            colors = TopAppBarDefaults.topAppBarColors(containerColor = colors.topBarBg)
                        )
                    },
                    containerColor = colors.screenBg
                ) { padding ->
                    Box(modifier = Modifier.fillMaxSize().padding(padding)) {
                        LazyColumn(
                            modifier = Modifier
                                .fillMaxSize()
                                .padding(16.dp),
                            verticalArrangement = Arrangement.spacedBy(10.dp)
                        ) {
                            val pending = applicationsList.filter { it.status == "PENDING" }
                            if (pending.isEmpty()) {
                                item {
                                    Box(
                                        modifier = Modifier.fillMaxWidth().padding(top = 40.dp),
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

                            // Processed application history
                            val processed = applicationsList.filter { it.status != "PENDING" }
                            if (processed.isNotEmpty()) {
                                item {
                                    Spacer(modifier = Modifier.height(16.dp))
                                    Text("Application History", fontSize = 14.sp, fontWeight = FontWeight.Bold, color = colors.textSecondary)
                                }
                                items(processed, key = { "hist_${it.id}" }) { app ->
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
                                                color = if (app.status == "APPROVED") Color(0xFFFF6500).copy(alpha = 0.2f) else Color(0xFFFF5252).copy(alpha = 0.2f)
                                            ) {
                                                Text(
                                                    text = app.status,
                                                    color = if (app.status == "APPROVED") Color(0xFFFF6500) else Color(0xFFFF5252),
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
                }
                return
            }

            AgencyFullscreenView.INFO -> {
                val agency = myAgency
                var editName by remember(agency) { mutableStateOf(agency?.agencyName ?: "") }
                var editDesc by remember(agency) { mutableStateOf(agency?.description ?: "") }
                var isSavingSettings by remember { mutableStateOf(false) }

                Scaffold(
                    topBar = {
                        TopAppBar(
                            title = {
                                Text("Agency Information & Settings", fontWeight = FontWeight.Bold, color = colors.textPrimary, fontSize = 17.sp)
                            },
                            navigationIcon = {
                                IconButton(onClick = { activeFullscreenView = AgencyFullscreenView.NONE }) {
                                    Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back", tint = colors.textPrimary)
                                }
                            },
                            colors = TopAppBarDefaults.topAppBarColors(containerColor = colors.topBarBg)
                        )
                    },
                    containerColor = colors.screenBg
                ) { padding ->
                    Box(modifier = Modifier.fillMaxSize().padding(padding)) {
                        Column(
                            modifier = Modifier
                                .fillMaxSize()
                                .verticalScroll(rememberScrollState())
                                .padding(16.dp)
                        ) {
                            if (agency != null) {
                                Surface(
                                    shape = RoundedCornerShape(18.dp),
                                    color = colors.cardBg,
                                    border = androidx.compose.foundation.BorderStroke(1.dp, colors.cardBorder),
                                    modifier = Modifier.fillMaxWidth()
                                ) {
                                    Column(modifier = Modifier.padding(20.dp), horizontalAlignment = Alignment.CenterHorizontally) {
                                        if (agency.logoUrl.isNotBlank()) {
                                            AsyncImage(
                                                model = agency.logoUrl,
                                                contentDescription = agency.agencyName,
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
                                        Text(agency.agencyName, fontSize = 20.sp, fontWeight = FontWeight.Bold, color = colors.textPrimary)
                                        Spacer(modifier = Modifier.height(4.dp))
                                        Text("Agency Code: ${agency.agencyCode}", fontSize = 13.sp, color = QivoYellow, fontWeight = FontWeight.SemiBold)

                                        if (agency.description.isNotBlank()) {
                                            Spacer(modifier = Modifier.height(8.dp))
                                            Text(
                                                agency.description,
                                                fontSize = 13.sp,
                                                color = colors.textSecondary,
                                                textAlign = androidx.compose.ui.text.style.TextAlign.Center
                                            )
                                        }
                                    }
                                }

                                if (isAgent) {
                                    Spacer(modifier = Modifier.height(24.dp))
                                    Text("Edit Agency Profile", fontSize = 16.sp, fontWeight = FontWeight.Bold, color = colors.textPrimary)
                                    Spacer(modifier = Modifier.height(12.dp))

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
                                        modifier = Modifier.fillMaxWidth().height(100.dp),
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

                                    Spacer(modifier = Modifier.height(24.dp))
                                    var showDeleteConfirm by remember { mutableStateOf(false) }
                                    var isDeletingAgency by remember { mutableStateOf(false) }

                                    if (!showDeleteConfirm) {
                                        Button(
                                            onClick = { showDeleteConfirm = true },
                                            modifier = Modifier.fillMaxWidth(),
                                            shape = RoundedCornerShape(12.dp),
                                            colors = ButtonDefaults.buttonColors(containerColor = Color.Red.copy(alpha = 0.85f))
                                        ) {
                                            Icon(Icons.Default.Delete, contentDescription = null, tint = Color.White, modifier = Modifier.size(18.dp))
                                            Spacer(modifier = Modifier.width(8.dp))
                                            Text("Delete Agency", fontWeight = FontWeight.Bold, color = Color.White)
                                        }
                                    } else {
                                        Surface(
                                            shape = RoundedCornerShape(12.dp),
                                            color = colors.cardBg,
                                            border = androidx.compose.foundation.BorderStroke(1.dp, Color.Red),
                                            modifier = Modifier.fillMaxWidth()
                                        ) {
                                            Column(modifier = Modifier.padding(14.dp), horizontalAlignment = Alignment.CenterHorizontally) {
                                                Text(
                                                    text = "Are you sure you want to permanently delete your agency? This action cannot be undone and will disband all members.",
                                                    color = colors.textPrimary,
                                                    fontSize = 13.sp,
                                                    textAlign = androidx.compose.ui.text.style.TextAlign.Center
                                                )
                                                Spacer(modifier = Modifier.height(12.dp))
                                                Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                                                    OutlinedButton(
                                                        onClick = { showDeleteConfirm = false },
                                                        shape = RoundedCornerShape(8.dp)
                                                    ) {
                                                        Text("Cancel", color = colors.textPrimary)
                                                    }

                                                    Button(
                                                        onClick = {
                                                            isDeletingAgency = true
                                                            scope.launch {
                                                                val ok = agencyService.deleteAgency(agency.id, context)
                                                                isDeletingAgency = false
                                                                if (ok) {
                                                                    AppToast.show("Agency deleted successfully!")
                                                                    onAgentStatusChanged(false)
                                                                    activeFullscreenView = AgencyFullscreenView.NONE
                                                                    refreshData()
                                                                } else {
                                                                    AppToast.show("Failed to delete agency")
                                                                }
                                                            }
                                                        },
                                                        shape = RoundedCornerShape(8.dp),
                                                        colors = ButtonDefaults.buttonColors(containerColor = Color.Red),
                                                        enabled = !isDeletingAgency
                                                    ) {
                                                        if (isDeletingAgency) {
                                                            CircularProgressIndicator(color = Color.White, modifier = Modifier.size(16.dp))
                                                        } else {
                                                            Text("Confirm Delete", fontWeight = FontWeight.Bold, color = Color.White)
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
                return
            }

            AgencyFullscreenView.NONE -> {}
        }
    }

    // =========================================================================
    // MAIN AGENCY HUB SCREEN (SINGLE CLEAN HUB WITH FULLSCREEN ACTION BUTTONS)
    // =========================================================================
    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Text(
                        text = if (isAgent) "Agency Management Panel" else if (myAgency != null || myMembership != null) "Agency Center" else "Join Agency",
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
                        text = "As an appointed Agent, establish your creator agency, recruit members with a unique Agency Code, and manage applications.",
                        fontSize = 13.sp,
                        color = colors.textSecondary,
                        textAlign = androidx.compose.ui.text.style.TextAlign.Center
                    )

                    Spacer(modifier = Modifier.height(24.dp))

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

                    OutlinedTextField(
                        value = createName,
                        onValueChange = { createName = it },
                        label = { Text("Agency Name") },
                        placeholder = { Text("e.g. Apex Royals Agency") },
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth().testTag("agency_name_input"),
                        colors = OutlinedTextFieldDefaults.colors(
                            focusedTextColor = colors.textPrimary,
                            unfocusedTextColor = colors.textPrimary,
                            focusedBorderColor = QivoOrange,
                            unfocusedBorderColor = colors.cardBorder
                        )
                    )

                    Spacer(modifier = Modifier.height(14.dp))

                    OutlinedTextField(
                        value = createDesc,
                        onValueChange = { createDesc = it },
                        label = { Text("Agency Bio / Description") },
                        placeholder = { Text("Describe your agency goals or creator perks...") },
                        modifier = Modifier.fillMaxWidth().height(100.dp).testTag("agency_desc_input"),
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
                                val uploadedLogoUrl = agencyService.uploadAgencyLogo(
                                    context = context,
                                    imageUri = createLogoUri!!,
                                    agencyIdOrOwnerId = currentUserId
                                ) ?: ""

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
                                    onAgentStatusChanged(true)
                                    refreshData()
                                } else {
                                    AppToast.show("Failed to create agency on server. Please try again.")
                                }
                            }
                        },
                        enabled = !isCreating,
                        modifier = Modifier.fillMaxWidth().height(52.dp).testTag("create_agency_submit_button"),
                        shape = RoundedCornerShape(16.dp),
                        colors = ButtonDefaults.buttonColors(containerColor = QivoOrange)
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
            } else if (myAgency != null || myMembership != null) {
                // ==========================================
                // AGENCY MEMBER / AGENT HUB DASHBOARD
                // ==========================================
                val activeAgency = myAgency ?: Agency(agencyName = myMembership?.agencyName ?: "Agency", agencyCode = myMembership?.agencyCode ?: "")
                val pendingCount = applicationsList.count { it.status == "PENDING" }

                Column(
                    modifier = Modifier
                        .fillMaxSize()
                        .verticalScroll(rememberScrollState())
                        .padding(16.dp)
                ) {
                    // Agency Hero Header Card
                    Surface(
                        color = colors.cardBg,
                        shape = RoundedCornerShape(20.dp),
                        border = androidx.compose.foundation.BorderStroke(1.dp, colors.cardBorder),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Column(modifier = Modifier.padding(18.dp)) {
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                if (activeAgency.logoUrl.isNotBlank()) {
                                    AsyncImage(
                                        model = activeAgency.logoUrl,
                                        contentDescription = activeAgency.agencyName,
                                        modifier = Modifier
                                            .size(68.dp)
                                            .clip(CircleShape)
                                            .border(2.dp, QivoOrange, CircleShape),
                                        contentScale = ContentScale.Crop
                                    )
                                } else {
                                    Surface(
                                        shape = CircleShape,
                                        color = QivoOrange.copy(alpha = 0.2f),
                                        modifier = Modifier.size(68.dp)
                                    ) {
                                        Box(contentAlignment = Alignment.Center) {
                                            Icon(Icons.Default.Business, contentDescription = null, tint = QivoOrange, modifier = Modifier.size(36.dp))
                                        }
                                    }
                                }

                                Spacer(modifier = Modifier.width(14.dp))

                                Column(modifier = Modifier.weight(1f)) {
                                    Row(verticalAlignment = Alignment.CenterVertically) {
                                        Text(
                                            text = activeAgency.agencyName,
                                            fontSize = 19.sp,
                                            fontWeight = FontWeight.ExtraBold,
                                            color = colors.textPrimary
                                        )
                                        Spacer(modifier = Modifier.width(6.dp))
                                        Surface(
                                            shape = RoundedCornerShape(4.dp),
                                            color = Color(0xFFFF9100).copy(alpha = 0.2f)
                                        ) {
                                            Text(
                                                text = if (isAgent) "AGENT" else "MEMBER",
                                                color = Color(0xFFFF9100),
                                                fontSize = 9.sp,
                                                fontWeight = FontWeight.Bold,
                                                modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                                            )
                                        }
                                    }

                                    if (activeAgency.description.isNotBlank()) {
                                        Spacer(modifier = Modifier.height(4.dp))
                                        Text(
                                            text = activeAgency.description,
                                            fontSize = 12.sp,
                                            color = colors.textSecondary,
                                            maxLines = 2
                                        )
                                    }

                                    Spacer(modifier = Modifier.height(8.dp))

                                    Row(
                                        verticalAlignment = Alignment.CenterVertically,
                                        modifier = Modifier
                                            .clip(RoundedCornerShape(8.dp))
                                            .background(QivoYellow.copy(alpha = 0.15f))
                                            .clickable { copyToClipboard(activeAgency.agencyCode, "Agency Code") }
                                            .padding(horizontal = 8.dp, vertical = 4.dp)
                                    ) {
                                        Icon(Icons.Default.VpnKey, contentDescription = null, tint = QivoYellow, modifier = Modifier.size(14.dp))
                                        Spacer(modifier = Modifier.width(4.dp))
                                        Text(
                                            text = "Code: ${activeAgency.agencyCode}",
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

                    Spacer(modifier = Modifier.height(20.dp))
                    Text(
                        text = "Agency Workspaces (Full Screen)",
                        fontSize = 15.sp,
                        fontWeight = FontWeight.Bold,
                        color = colors.textPrimary
                    )
                    Spacer(modifier = Modifier.height(12.dp))

                    // ==========================================
                    // 4 FULLSCREEN ACTION BUTTONS
                    // ==========================================
                    Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {

                        // 1. Group Chat Fullscreen Button (with message counter & red dot)
                        Surface(
                            onClick = { activeFullscreenView = AgencyFullscreenView.GROUP_CHAT },
                            shape = RoundedCornerShape(16.dp),
                            color = colors.cardBg,
                            border = androidx.compose.foundation.BorderStroke(1.dp, QivoOrange),
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Row(
                                modifier = Modifier.fillMaxWidth().padding(16.dp),
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.SpaceBetween
                            ) {
                                Row(verticalAlignment = Alignment.CenterVertically) {
                                    Box(
                                        modifier = Modifier
                                            .size(46.dp)
                                            .clip(CircleShape)
                                            .background(QivoOrange.copy(alpha = 0.15f)),
                                        contentAlignment = Alignment.Center
                                    ) {
                                        Icon(Icons.Default.ChatBubble, contentDescription = null, tint = QivoOrange, modifier = Modifier.size(24.dp))
                                        if (groupMessagesList.isNotEmpty()) {
                                            Box(
                                                modifier = Modifier
                                                    .align(Alignment.TopEnd)
                                                    .size(10.dp)
                                                    .clip(CircleShape)
                                                    .background(Color.Red)
                                                    .border(1.dp, Color.White, CircleShape)
                                            )
                                        }
                                    }
                                    Spacer(modifier = Modifier.width(14.dp))
                                    Column {
                                        Text("Agency Group Chat", fontSize = 16.sp, fontWeight = FontWeight.Bold, color = colors.textPrimary)
                                        Text("${groupMessagesList.size} messages in chat • Open Fullscreen", fontSize = 12.sp, color = colors.textSecondary)
                                    }
                                }
                                Surface(
                                    shape = CircleShape,
                                    color = QivoOrange
                                ) {
                                    Text(
                                        text = "${groupMessagesList.size} 💬",
                                        color = Color.White,
                                        fontSize = 12.sp,
                                        fontWeight = FontWeight.Bold,
                                        modifier = Modifier.padding(horizontal = 10.dp, vertical = 5.dp)
                                    )
                                }
                            }
                        }

                        // 2. Members Fullscreen Button
                        Surface(
                            onClick = { activeFullscreenView = AgencyFullscreenView.MEMBERS },
                            shape = RoundedCornerShape(16.dp),
                            color = colors.cardBg,
                            border = androidx.compose.foundation.BorderStroke(1.dp, colors.cardBorder),
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Row(
                                modifier = Modifier.fillMaxWidth().padding(16.dp),
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.SpaceBetween
                            ) {
                                Row(verticalAlignment = Alignment.CenterVertically) {
                                    Box(
                                        modifier = Modifier
                                            .size(46.dp)
                                            .clip(CircleShape)
                                            .background(Color(0xFF2196F3).copy(alpha = 0.15f)),
                                        contentAlignment = Alignment.Center
                                    ) {
                                        Icon(Icons.Default.Group, contentDescription = null, tint = Color(0xFF2196F3), modifier = Modifier.size(24.dp))
                                    }
                                    Spacer(modifier = Modifier.width(14.dp))
                                    Column {
                                        Text("Agency Members", fontSize = 16.sp, fontWeight = FontWeight.Bold, color = colors.textPrimary)
                                        Text("${membersList.size} members • Open Fullscreen", fontSize = 12.sp, color = colors.textSecondary)
                                    }
                                }
                                Surface(
                                    shape = CircleShape,
                                    color = Color(0xFF2196F3)
                                ) {
                                    Text(
                                        text = "${membersList.size} 👥",
                                        color = Color.White,
                                        fontSize = 12.sp,
                                        fontWeight = FontWeight.Bold,
                                        modifier = Modifier.padding(horizontal = 10.dp, vertical = 5.dp)
                                    )
                                }
                            }
                        }

                        // 3. Applications Fullscreen Button (if Agent)
                        if (isAgent) {
                            Surface(
                                onClick = { activeFullscreenView = AgencyFullscreenView.APPLICATIONS },
                                shape = RoundedCornerShape(16.dp),
                                color = colors.cardBg,
                                border = androidx.compose.foundation.BorderStroke(1.dp, if (pendingCount > 0) Color.Red else colors.cardBorder),
                                modifier = Modifier.fillMaxWidth()
                            ) {
                                Row(
                                    modifier = Modifier.fillMaxWidth().padding(16.dp),
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.SpaceBetween
                                ) {
                                    Row(verticalAlignment = Alignment.CenterVertically) {
                                        Box(
                                            modifier = Modifier
                                                .size(46.dp)
                                                .clip(CircleShape)
                                                .background(Color(0xFFFF9800).copy(alpha = 0.15f)),
                                            contentAlignment = Alignment.Center
                                        ) {
                                            Icon(Icons.Default.Assignment, contentDescription = null, tint = Color(0xFFFF9800), modifier = Modifier.size(24.dp))
                                            if (pendingCount > 0) {
                                                Box(
                                                    modifier = Modifier
                                                        .align(Alignment.TopEnd)
                                                        .size(10.dp)
                                                        .clip(CircleShape)
                                                        .background(Color.Red)
                                                        .border(1.dp, Color.White, CircleShape)
                                                )
                                            }
                                        }
                                        Spacer(modifier = Modifier.width(14.dp))
                                        Column {
                                            Text("Member Applications", fontSize = 16.sp, fontWeight = FontWeight.Bold, color = colors.textPrimary)
                                            Text("${pendingCount} pending requests • Open Fullscreen", fontSize = 12.sp, color = colors.textSecondary)
                                        }
                                    }
                                    Surface(
                                        shape = CircleShape,
                                        color = if (pendingCount > 0) Color.Red else Color.Gray.copy(alpha = 0.3f)
                                    ) {
                                        Text(
                                            text = "$pendingCount 📋",
                                            color = Color.White,
                                            fontSize = 12.sp,
                                            fontWeight = FontWeight.Bold,
                                            modifier = Modifier.padding(horizontal = 10.dp, vertical = 5.dp)
                                        )
                                    }
                                }
                            }
                        }

                        // 4. Agency Info & Settings Fullscreen Button
                        Surface(
                            onClick = { activeFullscreenView = AgencyFullscreenView.INFO },
                            shape = RoundedCornerShape(16.dp),
                            color = colors.cardBg,
                            border = androidx.compose.foundation.BorderStroke(1.dp, colors.cardBorder),
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Row(
                                modifier = Modifier.fillMaxWidth().padding(16.dp),
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.SpaceBetween
                            ) {
                                Row(verticalAlignment = Alignment.CenterVertically) {
                                    Box(
                                        modifier = Modifier
                                            .size(46.dp)
                                            .clip(CircleShape)
                                            .background(Color(0xFF9C27B0).copy(alpha = 0.15f)),
                                        contentAlignment = Alignment.Center
                                    ) {
                                        Icon(Icons.Default.Info, contentDescription = null, tint = Color(0xFF9C27B0), modifier = Modifier.size(24.dp))
                                    }
                                    Spacer(modifier = Modifier.width(14.dp))
                                    Column {
                                        Text("Agency Info & Settings", fontSize = 16.sp, fontWeight = FontWeight.Bold, color = colors.textPrimary)
                                        Text("View details & edit profile • Open Fullscreen", fontSize = 12.sp, color = colors.textSecondary)
                                    }
                                }
                                Icon(Icons.AutoMirrored.Filled.ArrowForward, contentDescription = null, tint = colors.textSecondary)
                            }
                        }
                    }
                }
            } else {
                // ==========================================
                // REGULAR USER VIEW: JOIN AGENCY SCREEN (MATCHES SCREENSHOT 1)
                // ==========================================
                val application = myLatestApplication
                Column(
                    modifier = Modifier
                        .fillMaxSize()
                        .background(Color(0xFF381A05))
                        .verticalScroll(rememberScrollState())
                ) {
                    // Top Hero Banner with Atmospheric Sunset Glow & QIVO 3D Badge
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .background(
                                androidx.compose.ui.graphics.Brush.verticalGradient(
                                    listOf(
                                        Color(0xFF3D1E04),
                                        Color(0xFF261202),
                                        Color(0xFF381A05)
                                    )
                                )
                            )
                            .padding(horizontal = 18.dp, vertical = 20.dp)
                    ) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            Column(modifier = Modifier.weight(1f).padding(end = 12.dp)) {
                                Text(
                                    text = "Join Qivo Agency",
                                    fontSize = 22.sp,
                                    fontWeight = FontWeight.ExtraBold,
                                    color = Color(0xFFFFF7ED),
                                    letterSpacing = 0.3.sp
                                )
                                Spacer(modifier = Modifier.height(6.dp))
                                Text(
                                    text = "Unlock more ways to make money",
                                    fontSize = 13.sp,
                                    fontWeight = FontWeight.Medium,
                                    color = Color.White.copy(alpha = 0.80f)
                                )
                            }

                            QivoAgency3DHeroBadge(size = 88.dp)
                        }
                    }

                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 16.dp, vertical = 10.dp)
                    ) {
                        if (application != null && application.status == "PENDING") {
                            Surface(
                                shape = RoundedCornerShape(16.dp),
                                color = Color(0xFFFF9800).copy(alpha = 0.15f),
                                border = androidx.compose.foundation.BorderStroke(1.dp, QivoOrange),
                                modifier = Modifier.fillMaxWidth()
                            ) {
                                Column(modifier = Modifier.padding(16.dp), horizontalAlignment = Alignment.CenterHorizontally) {
                                    Icon(Icons.Default.HourglassTop, contentDescription = null, tint = QivoOrange, modifier = Modifier.size(32.dp))
                                    Spacer(modifier = Modifier.height(6.dp))
                                    Text("Join Application Under Review", fontWeight = FontWeight.Bold, fontSize = 15.sp, color = Color.White)
                                    Spacer(modifier = Modifier.height(4.dp))
                                    Text("Your request to join ${application.agencyName} (${application.agencyCode}) is awaiting review by the Agent.", fontSize = 12.sp, color = Color.White.copy(alpha = 0.7f), textAlign = androidx.compose.ui.text.style.TextAlign.Center)
                                }
                            }
                            Spacer(modifier = Modifier.height(18.dp))
                        }

                        Text(
                            text = "Choose Method 1 or Method 2",
                            fontSize = 17.sp,
                            fontWeight = FontWeight.ExtraBold,
                            color = Color.White
                        )

                        Spacer(modifier = Modifier.height(14.dp))

                        // =============================================================
                        // METHOD 1 CARD (Enter agency's User ID to join)
                        // =============================================================
                        Surface(
                            shape = RoundedCornerShape(20.dp),
                            color = Color(0xFF141726),
                            border = androidx.compose.foundation.BorderStroke(1.dp, Color(0x2AFFFFFF)),
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Column(modifier = Modifier.padding(18.dp)) {
                                // Method 1 Badge Pill
                                Surface(
                                    shape = RoundedCornerShape(8.dp),
                                    color = Color(0xFF00E676)
                                ) {
                                    Text(
                                        text = "Method 1",
                                        color = Color.Black,
                                        fontSize = 11.5.sp,
                                        fontWeight = FontWeight.Black,
                                        modifier = Modifier.padding(horizontal = 9.dp, vertical = 3.dp)
                                    )
                                }

                                Spacer(modifier = Modifier.height(12.dp))

                                Text(
                                    text = "Enter agency's User ID to join",
                                    fontSize = 16.sp,
                                    fontWeight = FontWeight.Bold,
                                    color = Color.White
                                )

                                HorizontalDivider(
                                    color = Color(0x1AFFFFFF),
                                    modifier = Modifier.padding(vertical = 12.dp)
                                )

                                Text(
                                    text = "Agency's User ID (provided by agency)",
                                    fontSize = 13.sp,
                                    color = Color(0xFF8E95A5),
                                    fontWeight = FontWeight.Medium
                                )

                                Spacer(modifier = Modifier.height(10.dp))

                                OutlinedTextField(
                                    value = joinCodeInput,
                                    onValueChange = { joinCodeInput = it },
                                    placeholder = {
                                        Text(
                                            text = "Please enter",
                                            color = Color(0xFF5B6275),
                                            fontSize = 14.sp
                                        )
                                    },
                                    singleLine = true,
                                    shape = RoundedCornerShape(14.dp),
                                    colors = OutlinedTextFieldDefaults.colors(
                                        focusedTextColor = Color.White,
                                        unfocusedTextColor = Color.White,
                                        focusedBorderColor = Color(0xFF00E676),
                                        unfocusedBorderColor = Color(0x22FFFFFF),
                                        focusedContainerColor = Color(0xFF1C2033),
                                        unfocusedContainerColor = Color(0xFF1C2033)
                                    ),
                                    modifier = Modifier.fillMaxWidth()
                                )

                                Spacer(modifier = Modifier.height(14.dp))

                                Button(
                                    onClick = {
                                        if (joinCodeInput.isBlank()) {
                                            AppToast.show("Please enter agency User ID or Code")
                                            return@Button
                                        }
                                        isSearchingAgency = true
                                        scope.launch {
                                            searchedAgency = agencyService.fetchAgencyByCodeOrUserId(joinCodeInput)
                                            isSearchingAgency = false
                                            if (searchedAgency == null) {
                                                AppToast.show("Agency not found with User ID/Code: $joinCodeInput")
                                            }
                                        }
                                    },
                                    enabled = !isSearchingAgency,
                                    shape = RoundedCornerShape(24.dp),
                                    colors = ButtonDefaults.buttonColors(
                                        containerColor = Color(0xFF00C896),
                                        contentColor = Color.White
                                    ),
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .height(48.dp)
                                ) {
                                    if (isSearchingAgency) {
                                        CircularProgressIndicator(color = Color.White, modifier = Modifier.size(20.dp))
                                    } else {
                                        Text(
                                            text = "Search",
                                            fontSize = 15.sp,
                                            fontWeight = FontWeight.Bold
                                        )
                                    }
                                }

                                // Searched Agency Result Card
                                if (searchedAgency != null) {
                                    val sa = searchedAgency!!
                                    Spacer(modifier = Modifier.height(16.dp))
                                    Surface(
                                        shape = RoundedCornerShape(14.dp),
                                        color = Color(0xFF1A1F30),
                                        border = androidx.compose.foundation.BorderStroke(1.dp, Color(0xFF00C896)),
                                        modifier = Modifier.fillMaxWidth()
                                    ) {
                                        Column(modifier = Modifier.padding(14.dp), horizontalAlignment = Alignment.CenterHorizontally) {
                                            if (sa.logoUrl.isNotBlank()) {
                                                AsyncImage(
                                                    model = sa.logoUrl,
                                                    contentDescription = sa.agencyName,
                                                    modifier = Modifier.size(54.dp).clip(CircleShape).border(2.dp, Color(0xFF00C896), CircleShape),
                                                    contentScale = ContentScale.Crop
                                                )
                                            } else {
                                                Surface(shape = CircleShape, color = Color(0xFF00C896).copy(alpha = 0.2f), modifier = Modifier.size(54.dp)) {
                                                    Box(contentAlignment = Alignment.Center) {
                                                        Icon(Icons.Default.Business, contentDescription = null, tint = Color(0xFF00C896), modifier = Modifier.size(28.dp))
                                                    }
                                                }
                                            }
                                            Spacer(modifier = Modifier.height(8.dp))
                                            Text(sa.agencyName, fontSize = 16.sp, fontWeight = FontWeight.Bold, color = Color.White)
                                            Text("Agency Code: ${sa.agencyCode}", fontSize = 12.sp, color = Color(0xFFFFD54F), fontWeight = FontWeight.SemiBold)
                                            if (sa.description.isNotBlank()) {
                                                Spacer(modifier = Modifier.height(4.dp))
                                                Text(sa.description, fontSize = 12.sp, color = Color(0xFF8E95A5), textAlign = androidx.compose.ui.text.style.TextAlign.Center)
                                            }
                                            Spacer(modifier = Modifier.height(12.dp))
                                            Button(
                                                onClick = {
                                                    isSubmittingApplication = true
                                                    scope.launch {
                                                        val (ok, msg) = agencyService.applyToAgency(
                                                            agency = sa,
                                                            user = UserProfile(
                                                                id = currentUserId,
                                                                numericId = currentNumericId,
                                                                name = currentUserName,
                                                                avatarUrl = currentUserAvatar,
                                                                gender = currentUserGender,
                                                                country = ""
                                                            )
                                                        )
                                                        isSubmittingApplication = false
                                                        if (ok) {
                                                            AppToast.show(msg)
                                                            refreshData()
                                                        } else {
                                                            AppToast.show(msg.ifBlank { "Failed to submit application. Please try again." })
                                                        }
                                                    }
                                                },
                                                enabled = !isSubmittingApplication,
                                                modifier = Modifier.fillMaxWidth(),
                                                shape = RoundedCornerShape(20.dp),
                                                colors = ButtonDefaults.buttonColors(containerColor = QivoOrange)
                                            ) {
                                                if (isSubmittingApplication) {
                                                    CircularProgressIndicator(color = Color.White, modifier = Modifier.size(18.dp))
                                                } else {
                                                    Text("Submit Join Application", fontWeight = FontWeight.Bold)
                                                }
                                            }
                                        }
                                    }
                                }
                            }
                        }

                        Spacer(modifier = Modifier.height(18.dp))

                        // =============================================================
                        // METHOD 2 CARD (Waiting for Agency invitation - User ID Only)
                        // =============================================================
                        Surface(
                            shape = RoundedCornerShape(20.dp),
                            color = Color(0xFF141726),
                            border = androidx.compose.foundation.BorderStroke(1.dp, Color(0x2AFFFFFF)),
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Column(modifier = Modifier.padding(18.dp)) {
                                // Method 2 Badge Pill
                                Surface(
                                    shape = RoundedCornerShape(8.dp),
                                    color = Color(0xFF00E676)
                                ) {
                                    Text(
                                        text = "Method 2",
                                        color = Color.Black,
                                        fontSize = 11.5.sp,
                                        fontWeight = FontWeight.Black,
                                        modifier = Modifier.padding(horizontal = 9.dp, vertical = 3.dp)
                                    )
                                }

                                Spacer(modifier = Modifier.height(12.dp))

                                Text(
                                    text = "Waiting for Agency invitation",
                                    fontSize = 16.sp,
                                    fontWeight = FontWeight.Bold,
                                    color = Color.White
                                )

                                HorizontalDivider(
                                    color = Color(0x1AFFFFFF),
                                    modifier = Modifier.padding(vertical = 12.dp)
                                )

                                Text(
                                    text = "You are required to provide your ID to the agency.",
                                    fontSize = 13.sp,
                                    color = Color(0xFF8E95A5),
                                    fontWeight = FontWeight.Medium
                                )

                                Spacer(modifier = Modifier.height(12.dp))

                                // Inner ID Pill Container (Only User ID - No host ID)
                                Surface(
                                    shape = RoundedCornerShape(14.dp),
                                    color = Color(0xFF1C2033),
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .clickable {
                                            copyToClipboard(currentNumericId.toString(), "User ID")
                                        }
                                ) {
                                    Row(
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .padding(horizontal = 16.dp, vertical = 14.dp),
                                        verticalAlignment = Alignment.CenterVertically,
                                        horizontalArrangement = Arrangement.SpaceBetween
                                    ) {
                                        Row(verticalAlignment = Alignment.CenterVertically) {
                                            Text(
                                                text = "User ID: ",
                                                color = Color(0xFF8E95A5),
                                                fontSize = 14.sp,
                                                fontWeight = FontWeight.Medium
                                            )
                                            Text(
                                                text = "$currentNumericId",
                                                color = Color(0xFF00E676),
                                                fontSize = 15.sp,
                                                fontWeight = FontWeight.Bold
                                            )
                                        }

                                        Icon(
                                            imageVector = Icons.Default.ContentCopy,
                                            contentDescription = "Copy User ID",
                                            tint = Color(0xFF00E676),
                                            modifier = Modifier.size(18.dp)
                                        )
                                    }
                                }
                            }
                        }

                        Spacer(modifier = Modifier.height(30.dp))
                    }
                }
            }
        }
    }
}

@Composable
fun AgencyMemberCard(
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
            modifier = Modifier.padding(14.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                if (member.userAvatarUrl.isNotBlank()) {
                    AsyncImage(
                        model = member.userAvatarUrl,
                        contentDescription = member.userName,
                        modifier = Modifier
                            .size(44.dp)
                            .clip(CircleShape)
                            .border(1.5.dp, QivoOrange, CircleShape),
                        contentScale = ContentScale.Crop
                    )
                } else {
                    Surface(
                        shape = CircleShape,
                        color = QivoOrange.copy(alpha = 0.2f),
                        modifier = Modifier.size(44.dp)
                    ) {
                        Box(contentAlignment = Alignment.Center) {
                            Text(
                                member.userName.take(1).uppercase(),
                                fontWeight = FontWeight.Bold,
                                color = QivoOrange,
                                fontSize = 16.sp
                            )
                        }
                    }
                }

                Spacer(modifier = Modifier.width(12.dp))

                Column {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text(member.userName, fontSize = 15.sp, fontWeight = FontWeight.Bold, color = colors.textPrimary)
                        Spacer(modifier = Modifier.width(6.dp))
                        if (member.role.equals("OWNER", ignoreCase = true) || member.role.equals("AGENT", ignoreCase = true)) {
                            Surface(shape = RoundedCornerShape(4.dp), color = QivoOrange) {
                                Text("AGENT", color = Color.White, fontSize = 8.sp, fontWeight = FontWeight.Bold, modifier = Modifier.padding(horizontal = 5.dp, vertical = 1.dp))
                            }
                        } else {
                            Surface(shape = RoundedCornerShape(4.dp), color = QivoYellow.copy(alpha = 0.2f)) {
                                Text("MEMBER", color = QivoYellow, fontSize = 8.sp, fontWeight = FontWeight.Bold, modifier = Modifier.padding(horizontal = 5.dp, vertical = 1.dp))
                            }
                        }
                    }
                    Text("ID: ${member.userNumericId}", fontSize = 12.sp, color = colors.textSecondary)
                }
            }

            if (isCurrentAgent && !member.role.equals("OWNER", ignoreCase = true)) {
                IconButton(onClick = onRemoveClick) {
                    Icon(Icons.Default.PersonRemove, contentDescription = "Remove Member", tint = Color(0xFFFF5252))
                }
            }
        }
    }
}

@Composable
fun AgencyApplicationCard(
    application: AgencyApplication,
    colors: com.example.ui.theme.AppColors,
    onApprove: () -> Unit,
    onReject: () -> Unit
) {
    Surface(
        shape = RoundedCornerShape(14.dp),
        color = colors.cardBg,
        border = androidx.compose.foundation.BorderStroke(1.dp, QivoOrange.copy(alpha = 0.5f)),
        modifier = Modifier.fillMaxWidth()
    ) {
        Column(modifier = Modifier.padding(14.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                if (application.userAvatarUrl.isNotBlank()) {
                    AsyncImage(
                        model = application.userAvatarUrl,
                        contentDescription = application.userName,
                        modifier = Modifier
                            .size(44.dp)
                            .clip(CircleShape)
                            .border(1.5.dp, QivoOrange, CircleShape),
                        contentScale = ContentScale.Crop
                    )
                } else {
                    Surface(
                        shape = CircleShape,
                        color = QivoOrange.copy(alpha = 0.2f),
                        modifier = Modifier.size(44.dp)
                    ) {
                        Box(contentAlignment = Alignment.Center) {
                            Text(
                                application.userName.take(1).uppercase(),
                                fontWeight = FontWeight.Bold,
                                color = QivoOrange,
                                fontSize = 16.sp
                            )
                        }
                    }
                }

                Spacer(modifier = Modifier.width(12.dp))

                Column {
                    Text(application.userName, fontSize = 15.sp, fontWeight = FontWeight.Bold, color = colors.textPrimary)
                    Text("ID: ${application.userNumericId}", fontSize = 12.sp, color = colors.textSecondary)
                }
            }

            Spacer(modifier = Modifier.height(12.dp))

            Row(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.fillMaxWidth()) {
                Button(
                    onClick = onApprove,
                    modifier = Modifier.weight(1f).height(40.dp),
                    shape = RoundedCornerShape(10.dp),
                    colors = ButtonDefaults.buttonColors(containerColor = QivoOrange)
                ) {
                    Text("Approve", fontWeight = FontWeight.Bold, fontSize = 13.sp)
                }

                OutlinedButton(
                    onClick = onReject,
                    modifier = Modifier.weight(1f).height(40.dp),
                    shape = RoundedCornerShape(10.dp),
                    colors = ButtonDefaults.outlinedButtonColors(contentColor = Color(0xFFFF5252))
                ) {
                    Text("Reject", fontWeight = FontWeight.Bold, fontSize = 13.sp)
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
    colors: com.example.ui.theme.AppColors,
    onOpenRechargeWallet: () -> Unit = {}
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val agencyService = remember { SupabaseAgencyService() }
    val profileService = remember { SupabaseProfileService() }
    val listState = rememberLazyListState()

    var messages by remember { mutableStateOf<List<AgencyGroupMessage>>(emptyList()) }
    var messageText by remember { mutableStateOf("") }
    var isSending by remember { mutableStateOf(false) }
    var showGiftPadBottomSheet by remember { mutableStateOf(false) }
    var userCurrentCoins by remember { mutableStateOf(UserSessionManager.getCoins(context)) }

    // Connect WebSocket when screen opens, disconnect when screen leaves or agency changes
    DisposableEffect(agencyId, currentUserId) {
        if (agencyId.isNotBlank()) {
            AgencyRealtimeRelayManager.connect(context, agencyId, currentUserId)
        }
        onDispose {
            AgencyRealtimeRelayManager.disconnect()
        }
    }

    // Perform ONE initial fetch
    LaunchedEffect(agencyId) {
        if (agencyId.isNotBlank()) {
            val initialMsgs = agencyService.fetchAgencyGroupMessages(agencyId)
            messages = initialMsgs
            if (initialMsgs.isNotEmpty()) {
                listState.scrollToItem(initialMsgs.size - 1)
                val maxId = initialMsgs.maxOf { it.id }
                AgencyUnreadManager.markAsRead(context, agencyId, currentUserId, maxId)
            }
        }
    }

    // Subscribe to realtime messages via AgencyRealtimeRelayManager
    LaunchedEffect(agencyId) {
        AgencyRealtimeRelayManager.messages.collect { newMsg ->
            if (newMsg.agencyId == agencyId) {
                if (messages.none { it.id == newMsg.id }) {
                    messages = messages + newMsg
                    kotlinx.coroutines.delay(100)
                    if (messages.isNotEmpty()) {
                        listState.animateScrollToItem(messages.size - 1)
                    }
                    AgencyUnreadManager.markAsRead(context, agencyId, currentUserId, newMsg.id)
                }
            }
        }
    }

    // Mark messages as read whenever user is viewing
    LaunchedEffect(messages) {
        if (messages.isNotEmpty()) {
            val maxId = messages.maxOf { it.id }
            AgencyUnreadManager.markAsRead(context, agencyId, currentUserId, maxId)
        }
    }

    Column(modifier = Modifier.fillMaxSize().imePadding()) {
        LazyColumn(
            state = listState,
            modifier = Modifier.weight(1f).padding(horizontal = 14.dp, vertical = 8.dp),
            reverseLayout = false,
            verticalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            if (messages.isEmpty()) {
                item {
                    Box(modifier = Modifier.fillMaxWidth().padding(top = 60.dp), contentAlignment = Alignment.Center) {
                        Column(horizontalAlignment = Alignment.CenterHorizontally) {
                            Icon(Icons.Default.ChatBubbleOutline, contentDescription = null, tint = colors.textSecondary, modifier = Modifier.size(48.dp))
                            Spacer(modifier = Modifier.height(10.dp))
                            Text("No messages yet", fontSize = 16.sp, fontWeight = FontWeight.Bold, color = colors.textPrimary)
                            Spacer(modifier = Modifier.height(4.dp))
                            Text("Say hello to your agency team for free!", fontSize = 12.sp, color = colors.textSecondary)
                        }
                    }
                }
            } else {
                items(messages, key = { msg -> "${msg.id}_${msg.createdAt}_${msg.senderId}_${msg.message.hashCode()}" }) { msg ->
                    val isMe = msg.senderId == currentUserId
                    val senderTagText = "@${msg.senderName} "

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = if (isMe) Arrangement.End else Arrangement.Start,
                        verticalAlignment = Alignment.Bottom
                    ) {
                        if (!isMe) {
                            Box(
                                modifier = Modifier
                                    .size(32.dp)
                                    .clip(CircleShape)
                                    .clickable {
                                        if (!messageText.contains(senderTagText)) {
                                            messageText = "$messageText$senderTagText"
                                        }
                                    }
                            ) {
                                if (msg.senderAvatar.isNotBlank()) {
                                    AsyncImage(
                                        model = msg.senderAvatar,
                                        contentDescription = msg.senderName,
                                        modifier = Modifier.fillMaxSize().clip(CircleShape).border(1.dp, QivoOrange, CircleShape),
                                        contentScale = ContentScale.Crop
                                    )
                                } else {
                                    Surface(shape = CircleShape, color = QivoOrange.copy(alpha = 0.2f), modifier = Modifier.fillMaxSize()) {
                                        Box(contentAlignment = Alignment.Center) {
                                            Text(msg.senderName.take(1).uppercase(), fontWeight = FontWeight.Bold, fontSize = 13.sp, color = QivoOrange)
                                        }
                                    }
                                }
                            }
                            Spacer(modifier = Modifier.width(8.dp))
                        }

                        Column(horizontalAlignment = if (isMe) Alignment.End else Alignment.Start, modifier = Modifier.widthIn(max = 280.dp)) {
                            if (!isMe) {
                                Text(
                                    text = msg.senderName,
                                    fontSize = 11.sp,
                                    fontWeight = FontWeight.Bold,
                                    color = QivoOrange,
                                    modifier = Modifier
                                        .clip(RoundedCornerShape(4.dp))
                                        .clickable {
                                            if (!messageText.contains(senderTagText)) {
                                                messageText = "$messageText$senderTagText"
                                            }
                                        }
                                        .padding(bottom = 2.dp)
                                )
                            }
                            Surface(
                                shape = RoundedCornerShape(
                                    topStart = 16.dp, topEnd = 16.dp,
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
                                    modifier = Modifier.padding(horizontal = 12.dp, vertical = 8.dp)
                                )
                            }
                        }
                    }
                }
            }
        }

        Surface(color = colors.cardBg, border = androidx.compose.foundation.BorderStroke(1.dp, colors.cardBorder), modifier = Modifier.fillMaxWidth()) {
            Row(modifier = Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 8.dp), verticalAlignment = Alignment.CenterVertically) {
                // Gift Button 🎁
                IconButton(
                    onClick = {
                        userCurrentCoins = UserSessionManager.getCoins(context)
                        showGiftPadBottomSheet = true
                    },
                    modifier = Modifier
                        .padding(end = 6.dp)
                        .size(40.dp)
                        .background(Color(0xFFE040FB).copy(alpha = 0.15f), CircleShape)
                ) {
                    Icon(
                        imageVector = Icons.Default.CardGiftcard,
                        contentDescription = "Send Gift",
                        tint = Color(0xFFE040FB),
                        modifier = Modifier.size(20.dp)
                    )
                }

                OutlinedTextField(
                    value = messageText,
                    onValueChange = { messageText = it },
                    placeholder = { Text("Type a free group message...", color = colors.textSecondary, fontSize = 14.sp) },
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
                    modifier = Modifier.weight(1f).padding(end = 8.dp)
                )

                IconButton(
                    onClick = {
                        if (messageText.isNotBlank() && !isSending) {
                            isSending = true
                            val msgToSend = messageText.trim()
                            messageText = ""
                            scope.launch {
                                val sentMsg = agencyService.sendAgencyGroupMessage(
                                    agencyId = agencyId,
                                    senderId = currentUserId,
                                    senderNumericId = currentNumericId,
                                    senderName = currentUserName,
                                    senderAvatar = currentUserAvatar,
                                    senderRole = currentUserRole,
                                    senderGender = currentUserGender,
                                    messageText = msgToSend,
                                    context = context
                                )
                                if (sentMsg != null) {
                                    if (messages.none { it.id == sentMsg.id }) {
                                        messages = messages + sentMsg
                                        kotlinx.coroutines.delay(50)
                                        listState.animateScrollToItem(messages.size - 1)
                                    }
                                }
                                isSending = false
                            }
                        }
                    },
                    enabled = messageText.isNotBlank() && !isSending,
                    modifier = Modifier.size(44.dp).background(if (messageText.isNotBlank()) QivoOrange else Color.Gray.copy(alpha = 0.3f), CircleShape)
                ) {
                    if (isSending) {
                        CircularProgressIndicator(color = Color.White, modifier = Modifier.size(18.dp), strokeWidth = 2.dp)
                    } else {
                        Icon(Icons.AutoMirrored.Filled.Send, contentDescription = "Send", tint = Color.White, modifier = Modifier.size(20.dp))
                    }
                }
            }
        }
    }

    // Gift Pad Bottom Sheet for Agency Group Chat
    if (showGiftPadBottomSheet) {
        GiftPadBottomSheet(
            currentCoins = userCurrentCoins,
            targetRecipientName = "Agency Team",
            onDismiss = { showGiftPadBottomSheet = false },
            onRechargeClick = {
                showGiftPadBottomSheet = false
                onOpenRechargeWallet()
            },
            onSendGift = { gift ->
                showGiftPadBottomSheet = false
                scope.launch {
                    val (success, newBalance) = profileService.deductGiftCoins(
                        senderId = currentUserId,
                        giftName = gift.name,
                        coins = gift.coins,
                        receiverId = agencyId,
                        receiverName = "Agency Group"
                    )
                    if (success) {
                        userCurrentCoins = newBalance
                        UserSessionManager.saveCoins(context, newBalance)
                        val sentMsg = agencyService.sendAgencyGroupMessage(
                            agencyId = agencyId,
                            senderId = currentUserId,
                            senderNumericId = currentNumericId,
                            senderName = currentUserName,
                            senderAvatar = currentUserAvatar,
                            senderRole = currentUserRole,
                            senderGender = currentUserGender,
                            messageText = "🎁 Sent ${gift.name} ${gift.emoji}",
                            context = context
                        )
                        if (sentMsg != null) {
                            if (messages.none { it.id == sentMsg.id }) {
                                messages = messages + sentMsg
                                kotlinx.coroutines.delay(50)
                                listState.animateScrollToItem(messages.size - 1)
                            }
                        }
                        AppToast.show("Sent ${gift.name} ${gift.emoji} to group!")
                    } else {
                        AppToast.show("Insufficient coins to send ${gift.name}")
                        onOpenRechargeWallet()
                    }
                }
            },
            onInsufficientCoins = { gift ->
                showGiftPadBottomSheet = false
                AppToast.show("Insufficient coins to send ${gift.name}")
                onOpenRechargeWallet()
            }
        )
    }
}

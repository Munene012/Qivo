package com.example.ui.screens
import com.example.ui.components.AppToast
import com.example.data.AppDataCacheManager

import android.net.Uri
import android.widget.Toast
import androidx.activity.compose.BackHandler
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
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.CameraAlt
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.ChevronRight
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.DirectionsRun
import androidx.compose.material.icons.filled.Diversity1
import androidx.compose.material.icons.filled.Height
import androidx.compose.material.icons.filled.Language
import androidx.compose.material.icons.filled.LocalBar
import androidx.compose.material.icons.filled.MusicNote
import androidx.compose.material.icons.filled.NightlightRound
import androidx.compose.material.icons.filled.Palette
import androidx.compose.material.icons.filled.Pets
import androidx.compose.material.icons.filled.Psychology
import androidx.compose.material.icons.filled.Public
import androidx.compose.material.icons.filled.Restaurant
import androidx.compose.material.icons.filled.School
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.SmokingRooms
import androidx.compose.material.icons.filled.Star
import androidx.compose.material.icons.filled.Work
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil.compose.AsyncImage
import com.example.data.CountryData
import com.example.data.NetworkUtils
import com.example.data.SupabaseProfileService
import com.example.data.UserProfile
import com.example.data.UserSessionManager
import com.example.ui.components.AddPhoto3DIcon
import com.example.ui.components.Camera3DIcon
import com.example.ui.components.ProfileCategory3DIcon
import com.example.ui.theme.AppTheme
import kotlinx.coroutines.launch

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun PersonalInformationScreen(
    currentProfile: UserProfile,
    onBackClick: () -> Unit,
    onProfileSaved: (UserProfile) -> Unit
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val scrollState = rememberScrollState()
    val profileService = remember { SupabaseProfileService() }
    val colors = AppTheme.colors

    // Live Profile Fields State
    var nameState by remember { mutableStateOf(currentProfile.name) }
    var birthDateState by remember { mutableStateOf(currentProfile.birthDate.ifBlank { "2000-01-01" }) }
    val calculatedAge = remember(birthDateState) {
        if (birthDateState.isBlank()) "23"
        else {
            try {
                val yearStr = birthDateState.split("-", "/", " ", ".").firstOrNull { it.length == 4 }
                val birthYear = yearStr?.toIntOrNull() ?: 2003
                (2026 - birthYear).coerceIn(18, 99).toString()
            } catch (e: Exception) {
                "23"
            }
        }
    }
    // Gender selection (Male / Female)
    var genderState by remember(currentProfile.id, currentProfile.gender) {
        mutableStateOf(
            when {
                currentProfile.gender.equals("female", ignoreCase = true) ||
                currentProfile.gender.equals("f", ignoreCase = true) ||
                currentProfile.gender.equals("woman", ignoreCase = true) ||
                currentProfile.gender.equals("w", ignoreCase = true) -> "Female"

                currentProfile.gender.equals("male", ignoreCase = true) ||
                currentProfile.gender.equals("m", ignoreCase = true) ||
                currentProfile.gender.equals("man", ignoreCase = true) -> "Male"

                else -> ""
            }
        )
    }
    var countryState by remember(currentProfile.id, currentProfile.country) { mutableStateOf(currentProfile.country.ifBlank { "United States" }) }
    var avatarUrlState by remember(currentProfile.id, currentProfile.avatarUrl) { mutableStateOf(currentProfile.avatarUrl) }

    var socialPrefState by remember(currentProfile.id) { mutableStateOf(currentProfile.socialPreferences) }
    var exerciseState by remember(currentProfile.id) { mutableStateOf(currentProfile.exercise) }
    var educationState by remember(currentProfile.id) { mutableStateOf(currentProfile.education) }
    var heightState by remember(currentProfile.id) { mutableStateOf(currentProfile.height) }
    var relationshipState by remember(currentProfile.id) { mutableStateOf(currentProfile.relationshipStatus) }
    var occupationState by remember(currentProfile.id) { mutableStateOf(currentProfile.occupation) }
    var languagesState by remember(currentProfile.id) { mutableStateOf(currentProfile.languages) }
    var hobbiesState by remember(currentProfile.id) { mutableStateOf(currentProfile.hobbiesInterests) }
    var musicState by remember(currentProfile.id) { mutableStateOf(currentProfile.musicPreference) }
    var dietaryState by remember(currentProfile.id) { mutableStateOf(currentProfile.dietaryHabit) }
    var sleepState by remember(currentProfile.id) { mutableStateOf(currentProfile.sleepHabit) }
    var smokingState by remember(currentProfile.id) { mutableStateOf(currentProfile.smoking) }
    var liquorState by remember(currentProfile.id) { mutableStateOf(currentProfile.liquor) }
    var superpowerState by remember(currentProfile.id) { mutableStateOf(currentProfile.superpower) }
    var petsState by remember(currentProfile.id) { mutableStateOf(currentProfile.pets) }
    var personalityState by remember(currentProfile.id) { mutableStateOf(currentProfile.personalityType) }
    var horoscopesState by remember(currentProfile.id) { mutableStateOf(currentProfile.horoscopes) }

    val albumPhotos = remember(currentProfile.id) {
        mutableStateListOf<String>().apply {
            addAll(currentProfile.albumPhotos)
        }
    }

    LaunchedEffect(currentProfile.id) {
        if (currentProfile.id.isNotBlank()) {
            val cached = AppDataCacheManager.getCachedUserDetail(context, currentProfile.id)
            if (cached != null) {
                if (cached.socialPreferences.isNotBlank() && cached.socialPreferences != "Choose" && (socialPrefState.isBlank() || socialPrefState == "Choose")) socialPrefState = cached.socialPreferences
                if (cached.exercise.isNotBlank() && cached.exercise != "Choose" && (exerciseState.isBlank() || exerciseState == "Choose")) exerciseState = cached.exercise
                if (cached.education.isNotBlank() && cached.education != "Choose" && (educationState.isBlank() || educationState == "Choose")) educationState = cached.education
                if (cached.height.isNotBlank() && cached.height != "Choose" && (heightState.isBlank() || heightState == "Choose")) heightState = cached.height
                if (cached.relationshipStatus.isNotBlank() && cached.relationshipStatus != "Choose" && (relationshipState.isBlank() || relationshipState == "Choose")) relationshipState = cached.relationshipStatus
                if (cached.occupation.isNotBlank() && cached.occupation != "Choose" && (occupationState.isBlank() || occupationState == "Choose")) occupationState = cached.occupation
                if (cached.languages.isNotBlank() && cached.languages != "Choose" && (languagesState.isBlank() || languagesState == "Choose")) languagesState = cached.languages
                if (cached.hobbiesInterests.isNotBlank() && cached.hobbiesInterests != "Choose" && (hobbiesState.isBlank() || hobbiesState == "Choose")) hobbiesState = cached.hobbiesInterests
                if (cached.musicPreference.isNotBlank() && cached.musicPreference != "Choose" && (musicState.isBlank() || musicState == "Choose")) musicState = cached.musicPreference
                if (cached.dietaryHabit.isNotBlank() && cached.dietaryHabit != "Choose" && (dietaryState.isBlank() || dietaryState == "Choose")) dietaryState = cached.dietaryHabit
                if (cached.sleepHabit.isNotBlank() && cached.sleepHabit != "Choose" && (sleepState.isBlank() || sleepState == "Choose")) sleepState = cached.sleepHabit
                if (cached.smoking.isNotBlank() && cached.smoking != "Choose" && (smokingState.isBlank() || smokingState == "Choose")) smokingState = cached.smoking
                if (cached.liquor.isNotBlank() && cached.liquor != "Choose" && (liquorState.isBlank() || liquorState == "Choose")) liquorState = cached.liquor
                if (cached.superpower.isNotBlank() && cached.superpower != "Choose" && (superpowerState.isBlank() || superpowerState == "Choose")) superpowerState = cached.superpower
                if (cached.pets.isNotBlank() && cached.pets != "Choose" && (petsState.isBlank() || petsState == "Choose")) petsState = cached.pets
                if (cached.personalityType.isNotBlank() && cached.personalityType != "Choose" && (personalityState.isBlank() || personalityState == "Choose")) personalityState = cached.personalityType
                if (cached.horoscopes.isNotBlank() && cached.horoscopes != "Choose" && (horoscopesState.isBlank() || horoscopesState == "Choose")) horoscopesState = cached.horoscopes
                if (albumPhotos.isEmpty() && cached.albumPhotos.isNotEmpty()) {
                    albumPhotos.addAll(cached.albumPhotos)
                }
            }
        }
    }

    var isSaving by remember { mutableStateOf(false) }
    var isUploadingPhoto by remember { mutableStateOf(false) }

    // Gallery and Crop Screen State: Avatar (cropping true) vs Album photo (cropping false)
    var showAvatarGalleryScreen by remember { mutableStateOf(false) }
    var showAlbumGalleryScreen by remember { mutableStateOf(false) }

    // Picker Dialogs State
    var showDobPicker by remember { mutableStateOf(false) }
    var showCountryPicker by remember { mutableStateOf(false) }

    var activeCategoryTitle by remember { mutableStateOf<String?>(null) }
    var activeCategoryOptions by remember { mutableStateOf<List<String>>(emptyList()) }
    var activeCategorySelectedValue by remember { mutableStateOf("") }
    var onOptionChosenCallback by remember { mutableStateOf<((String) -> Unit)?>(null) }

    // Text Edit Dialog State (for Name)
    var showTextEditDialog by remember { mutableStateOf(false) }
    var textEditTitle by remember { mutableStateOf("") }
    var textEditValue by remember { mutableStateOf("") }
    var textEditCallback by remember { mutableStateOf<((String) -> Unit)?>(null) }

    BackHandler {
        when {
            showAvatarGalleryScreen -> showAvatarGalleryScreen = false
            showAlbumGalleryScreen -> showAlbumGalleryScreen = false
            showDobPicker -> showDobPicker = false
            showCountryPicker -> showCountryPicker = false
            activeCategoryTitle != null -> activeCategoryTitle = null
            showTextEditDialog -> showTextEditDialog = false
            else -> onBackClick()
        }
    }

    // 1. Full-screen Avatar Gallery & Crop (ONLY profile photo needs crop)
    if (showAvatarGalleryScreen) {
        GalleryAndCropScreen(
            userId = currentProfile.id,
            profileService = profileService,
            oldAvatarUrl = avatarUrlState.ifBlank { currentProfile.avatarUrl },
            enableCrop = true,
            title = "Select Avatar",
            onBackClick = { showAvatarGalleryScreen = false },
            onPhotoCroppedAndUploaded = { newUrl ->
                avatarUrlState = newUrl
                showAvatarGalleryScreen = false
                AppToast.show("New avatar selected! Click 'Save Updates' to apply.")
            }
        )
        return
    }

    // 2. Full-screen Album Extra Photos Gallery (NO cropping!)
    if (showAlbumGalleryScreen) {
        GalleryAndCropScreen(
            userId = currentProfile.id,
            profileService = profileService,
            enableCrop = false,
            title = "Select Album Photo",
            onBackClick = { showAlbumGalleryScreen = false },
            onPhotoCroppedAndUploaded = { selectedUriString ->
                if (albumPhotos.size < 4) {
                    albumPhotos.add(selectedUriString)
                    AppToast.show("Photo added to album (${albumPhotos.size}/4)")
                } else {
                    AppToast.show("Album is full (max 4 photos)")
                }
                showAlbumGalleryScreen = false
            }
        )
        return
    }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(colors.screenBg)
            .testTag("personal_information_screen_root")
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .statusBarsPadding()
                .navigationBarsPadding()
        ) {
            // 1. Top Bar Header
            Surface(
                modifier = Modifier.fillMaxWidth(),
                color = colors.cardBg,
                shadowElevation = 1.dp
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 12.dp, vertical = 12.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    IconButton(
                        onClick = onBackClick,
                        modifier = Modifier.testTag("personal_info_back_button")
                    ) {
                        Icon(
                            imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                            contentDescription = "Back",
                            tint = colors.textPrimary
                        )
                    }

                    Spacer(modifier = Modifier.width(8.dp))

                    Text(
                        text = "Personal information",
                        fontSize = 20.sp,
                        fontWeight = FontWeight.ExtraBold,
                        color = colors.textPrimary
                    )
                }
            }

            // 2. Scrollable Body Content
            Column(
                modifier = Modifier
                    .weight(1f)
                    .verticalScroll(scrollState)
                    .padding(horizontal = 16.dp, vertical = 16.dp)
            ) {
                // Profile Avatar Photo Container
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(vertical = 16.dp),
                    contentAlignment = Alignment.Center
                ) {
                    Box(
                        modifier = Modifier.size(140.dp),
                        contentAlignment = Alignment.Center
                    ) {
                        // Outer Ring Yellow Border
                        Box(
                            modifier = Modifier
                                .size(140.dp)
                                .clip(CircleShape)
                                .background(Color(0xFFFFD600))
                                .padding(4.dp)
                                .clip(CircleShape)
                                .background(Color.White)
                                .padding(2.dp)
                                .clip(CircleShape)
                                .clickable {
                                    showAvatarGalleryScreen = true
                                }
                        ) {
                            com.example.data.AvatarHelper.UserAvatarImage(
                                avatarUrl = avatarUrlState,
                                userId = currentProfile.id,
                                gender = genderState,
                                numericId = currentProfile.numericId,
                                showFrame = false,
                                frameId = "none",
                                contentDescription = "User Avatar",
                                modifier = Modifier.fillMaxSize(),
                                contentScale = ContentScale.Crop
                            )
                        }

                        // Camera Button Badge at Bottom Right
                        Box(
                            modifier = Modifier
                                .align(Alignment.BottomEnd)
                                .size(40.dp)
                                .clickable {
                                    showAvatarGalleryScreen = true
                                },
                            contentAlignment = Alignment.Center
                        ) {
                            if (isUploadingPhoto) {
                                CircularProgressIndicator(
                                    color = Color.Black,
                                    modifier = Modifier.size(18.dp),
                                    strokeWidth = 2.dp
                                )
                            } else {
                                Camera3DIcon(size = 38.dp)
                            }
                        }
                    }
                }

                Spacer(modifier = Modifier.height(16.dp))

                // 3. "My album (0/20)" Card
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(20.dp),
                    colors = CardDefaults.cardColors(containerColor = colors.cardBg),
                    elevation = CardDefaults.cardElevation(defaultElevation = 1.dp)
                ) {
                    Column(modifier = Modifier.padding(16.dp)) {
                        Text(
                            text = "My album (${albumPhotos.size}/4)",
                            fontSize = 15.sp,
                            fontWeight = FontWeight.Bold,
                            color = colors.textPrimary
                        )

                        Spacer(modifier = Modifier.height(12.dp))

                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(10.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            // Plus Square Button -> opens custom gallery directly (no crop!)
                            Box(
                                modifier = Modifier
                                    .size(72.dp)
                                    .clip(RoundedCornerShape(14.dp))
                                    .background(if (colors.isDark) Color(0xFF1E1E1E) else Color(0xFFF1F3F5))
                                    .clickable {
                                        if (albumPhotos.size < 4) {
                                            showAlbumGalleryScreen = true
                                        } else {
                                            AppToast.show("Album full (max 4 photos)")
                                        }
                                    },
                                contentAlignment = Alignment.Center
                            ) {
                                AddPhoto3DIcon(size = 38.dp)
                            }

                            // Display uploaded album photos (up to 4)
                            albumPhotos.take(4).forEach { photoUri ->
                                Box(
                                    modifier = Modifier
                                        .size(72.dp)
                                        .clip(RoundedCornerShape(14.dp))
                                        .background(Color.LightGray)
                                ) {
                                    AsyncImage(
                                        model = photoUri,
                                        contentDescription = "Album Photo",
                                        modifier = Modifier.fillMaxSize(),
                                        contentScale = ContentScale.Crop
                                    )
                                }
                            }
                        }
                    }
                }

                Spacer(modifier = Modifier.height(16.dp))

                // 4. "Basic information" Card (Gender removed per user instruction)
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(20.dp),
                    colors = CardDefaults.cardColors(containerColor = colors.cardBg),
                    elevation = CardDefaults.cardElevation(defaultElevation = 1.dp)
                ) {
                    Column(modifier = Modifier.padding(vertical = 8.dp)) {
                        Text(
                            text = "Basic information",
                            fontSize = 15.sp,
                            fontWeight = FontWeight.ExtraBold,
                            color = colors.textPrimary,
                            modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp)
                        )

                        // Name
                        InfoRow(
                            label = "Name",
                            value = nameState.ifBlank { "User" },
                            onClick = {
                                textEditTitle = "Edit Name"
                                textEditValue = nameState
                                textEditCallback = { newValue -> nameState = newValue }
                                showTextEditDialog = true
                            }
                        )
                        HorizontalDivider(modifier = Modifier.padding(horizontal = 16.dp), color = colors.divider)

                        // Age (Set day, month, year separately)
                        InfoRow(
                            label = "Age",
                            value = "$calculatedAge yrs",
                            onClick = {
                                showDobPicker = true
                            }
                        )
                        HorizontalDivider(modifier = Modifier.padding(horizontal = 16.dp), color = colors.divider)

                        // Gender
                        InfoRow(
                            label = "Gender",
                            value = genderState,
                            onClick = {
                                activeCategoryTitle = "Gender"
                                activeCategoryOptions = listOf("Male", "Female")
                                activeCategorySelectedValue = genderState
                                onOptionChosenCallback = { chosen ->
                                    genderState = chosen
                                }
                            }
                        )
                        HorizontalDivider(modifier = Modifier.padding(horizontal = 16.dp), color = colors.divider)

                        // Country (User can tap and select from list of all countries)
                        InfoRow(
                            label = "Country",
                            value = countryState,
                            editable = true,
                            onClick = {
                                showCountryPicker = true
                            }
                        )
                        HorizontalDivider(modifier = Modifier.padding(horizontal = 16.dp), color = colors.divider)

                        // Social Preferences
                        InfoRow(
                            label = "Social Preferences",
                            value = socialPrefState.ifBlank { "Choose" },
                            onClick = {
                                activeCategoryTitle = "Social Preferences"
                                activeCategoryOptions = listOf(
                                    "Friendship & Discovery",
                                    "Dating & Romance",
                                    "Long-Term Relationship",
                                    "Casual Chat & Hangouts",
                                    "Late Night Talks",
                                    "Gaming & Streaming",
                                    "Business & Networking",
                                    "Language & Cultural Exchange",
                                    "Travel & Adventure Partners",
                                    "Study & Career Mentorship",
                                    "Voice Party & Karaoke",
                                    "Creative Collaboration",
                                    "Fitness & Gym Buddies",
                                    "Music Jamming",
                                    "Philosophical & Deep Talks",
                                    "Just Browsing"
                                )
                                activeCategorySelectedValue = socialPrefState
                                onOptionChosenCallback = { socialPrefState = it }
                            }
                        )
                    }
                }

                Spacer(modifier = Modifier.height(16.dp))

                // 5. "More about me" Card - Wide variety of choices
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(20.dp),
                    colors = CardDefaults.cardColors(containerColor = colors.cardBg),
                    elevation = CardDefaults.cardElevation(defaultElevation = 1.dp)
                ) {
                    Column(modifier = Modifier.padding(vertical = 8.dp)) {
                        Text(
                            text = "More about me",
                            fontSize = 15.sp,
                            fontWeight = FontWeight.ExtraBold,
                            color = colors.textPrimary,
                            modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp)
                        )

                        // Exercise
                        CategoryRow(
                            icon = Icons.Default.DirectionsRun,
                            label = "Exercise",
                            value = exerciseState,
                            onClick = {
                                activeCategoryTitle = "Exercise"
                                activeCategoryOptions = listOf(
                                    "Choose",
                                    "Daily intense workout",
                                    "3-5 times a week",
                                    "1-2 times a week",
                                    "Occasionally / Weekends",
                                    "Daily walking / Steps",
                                    "Yoga & Meditation",
                                    "Pilates & Barre",
                                    "Gym & Weightlifting",
                                    "CrossFit & HIIT",
                                    "Running & Cardio",
                                    "Cycling & Spinning",
                                    "Swimming & Water sports",
                                    "Martial Arts & Boxing",
                                    "Team Sports (Football, Basketball)",
                                    "Outdoor Hiking & Climbing",
                                    "Rarely / Couch potato",
                                    "Prefer not to say"
                                )
                                activeCategorySelectedValue = exerciseState
                                onOptionChosenCallback = { exerciseState = it }
                            }
                        )
                        HorizontalDivider(modifier = Modifier.padding(horizontal = 16.dp), color = colors.divider)

                        // Education
                        CategoryRow(
                            icon = Icons.Default.School,
                            label = "Education",
                            value = educationState,
                            onClick = {
                                activeCategoryTitle = "Education"
                                activeCategoryOptions = listOf(
                                    "Choose",
                                    "High School / Secondary",
                                    "In College / University",
                                    "Vocational / Trade School",
                                    "Associate Degree",
                                    "Bachelor's Degree",
                                    "Postgraduate Diploma",
                                    "Master's Degree",
                                    "Doctorate / PhD",
                                    "Medical Degree (MD/MBBS)",
                                    "Law Degree (JD/LLB)",
                                    "Self-Taught / Bootcamp",
                                    "Other"
                                )
                                activeCategorySelectedValue = educationState
                                onOptionChosenCallback = { educationState = it }
                            }
                        )
                        HorizontalDivider(modifier = Modifier.padding(horizontal = 16.dp), color = colors.divider)

                        // Height (Wide variety of choices from 120 cm to 220 cm)
                        CategoryRow(
                            icon = Icons.Default.Height,
                            label = "Height",
                            value = heightState,
                            onClick = {
                                activeCategoryTitle = "Height"
                                activeCategoryOptions = listOf(
                                    "Choose",
                                    "120 cm", "125 cm", "130 cm", "135 cm", "140 cm", "142 cm", "145 cm",
                                    "148 cm", "150 cm", "152 cm", "154 cm", "156 cm", "158 cm", "160 cm",
                                    "162 cm", "164 cm", "166 cm", "168 cm", "170 cm", "172 cm", "174 cm",
                                    "176 cm", "178 cm", "180 cm", "182 cm", "184 cm", "186 cm", "188 cm",
                                    "190 cm", "192 cm", "194 cm", "196 cm", "198 cm", "200 cm", "205 cm",
                                    "210 cm", "215 cm", "220 cm+"
                                )
                                activeCategorySelectedValue = heightState
                                onOptionChosenCallback = { heightState = it }
                            }
                        )
                        HorizontalDivider(modifier = Modifier.padding(horizontal = 16.dp), color = colors.divider)

                        // Relationship Status
                        CategoryRow(
                            icon = Icons.Default.Diversity1,
                            label = "Relationship Status",
                            value = relationshipState,
                            onClick = {
                                activeCategoryTitle = "Relationship Status"
                                activeCategoryOptions = listOf(
                                    "Choose",
                                    "Single",
                                    "In a relationship",
                                    "Engaged",
                                    "Married",
                                    "Divorced",
                                    "Widowed",
                                    "It's complicated",
                                    "Open relationship",
                                    "Polyamorous",
                                    "Focusing on myself",
                                    "Separated",
                                    "Prefer not to say"
                                )
                                activeCategorySelectedValue = relationshipState
                                onOptionChosenCallback = { relationshipState = it }
                            }
                        )
                        HorizontalDivider(modifier = Modifier.padding(horizontal = 16.dp), color = colors.divider)

                        // Occupation
                        CategoryRow(
                            icon = Icons.Default.Work,
                            label = "Occupation",
                            value = occupationState,
                            onClick = {
                                activeCategoryTitle = "Occupation"
                                activeCategoryOptions = listOf(
                                    "Choose",
                                    "Technology & Software",
                                    "AI & Data Science",
                                    "Healthcare & Medicine",
                                    "Nursing & Therapy",
                                    "Business, Finance & Banking",
                                    "Marketing & Advertising",
                                    "Sales & Real Estate",
                                    "Creative Arts & Design",
                                    "Film & Photography",
                                    "Music & Entertainment",
                                    "Architecture & Interior Design",
                                    "Civil & Mechanical Engineering",
                                    "Education & Teaching",
                                    "Academic Research & Science",
                                    "Law & Legal Services",
                                    "Government & Public Service",
                                    "Hospitality, Food & Culinary",
                                    "Travel & Aviation",
                                    "Fitness, Sports & Coaching",
                                    "Fashion & Beauty",
                                    "Retail & Customer Service",
                                    "Trade & Construction",
                                    "Agriculture & Farming",
                                    "Student",
                                    "Freelancer & Self-Employed",
                                    "Entrepreneur & Founder",
                                    "Homemaker",
                                    "Retired",
                                    "Other"
                                )
                                activeCategorySelectedValue = occupationState
                                onOptionChosenCallback = { occupationState = it }
                            }
                        )
                        HorizontalDivider(modifier = Modifier.padding(horizontal = 16.dp), color = colors.divider)

                        // Languages
                        CategoryRow(
                            icon = Icons.Default.Language,
                            label = "Languages",
                            value = languagesState,
                            onClick = {
                                activeCategoryTitle = "Languages"
                                activeCategoryOptions = listOf(
                                    "Choose",
                                    "English", "Spanish", "Mandarin Chinese", "Hindi", "Arabic",
                                    "French", "Bengali", "Portuguese", "Russian", "Urdu",
                                    "Indonesian", "German", "Japanese", "Swahili", "Marathi",
                                    "Telugu", "Turkish", "Tamil", "Cantonese", "Vietnamese",
                                    "Tagalog / Filipino", "Korean", "Italian", "Polish", "Ukrainian",
                                    "Persian / Farsi", "Dutch", "Greek", "Hebrew", "Thai",
                                    "Swedish", "Norwegian", "Danish", "Finnish", "Hungarian",
                                    "Czech", "Romanian", "Malay", "Amharic", "Somali",
                                    "Yoruba", "Igbo", "Hausa", "Zulu", "Afrikaans"
                                )
                                activeCategorySelectedValue = languagesState
                                onOptionChosenCallback = { languagesState = it }
                            }
                        )
                        HorizontalDivider(modifier = Modifier.padding(horizontal = 16.dp), color = colors.divider)

                        // Hobbies & Interests
                        CategoryRow(
                            icon = Icons.Default.Palette,
                            label = "Hobbies & Interests",
                            value = hobbiesState,
                            onClick = {
                                activeCategoryTitle = "Hobbies & Interests"
                                activeCategoryOptions = listOf(
                                    "Choose",
                                    "Gaming & Esports",
                                    "Travel & Adventure",
                                    "Photography & Video",
                                    "Music & Singing",
                                    "Reading & Writing",
                                    "Cooking & Baking",
                                    "Art & Illustration",
                                    "Tech & Coding",
                                    "Fitness & Bodybuilding",
                                    "Anime & Manga",
                                    "Movies & TV Series",
                                    "Fashion & Styling",
                                    "Board Games & Chess",
                                    "Dancing",
                                    "Gardening & Nature",
                                    "Cars & Motorsports",
                                    "Podcasts & Audiobooks",
                                    "Astronomy & Science",
                                    "Meditation & Mindfulness",
                                    "Coffee & Cafes",
                                    "Pets & Animal care"
                                )
                                activeCategorySelectedValue = hobbiesState
                                onOptionChosenCallback = { hobbiesState = it }
                            }
                        )
                        HorizontalDivider(modifier = Modifier.padding(horizontal = 16.dp), color = colors.divider)

                        // Music Preference
                        CategoryRow(
                            icon = Icons.Default.MusicNote,
                            label = "Music Preference",
                            value = musicState,
                            onClick = {
                                activeCategoryTitle = "Music Preference"
                                activeCategoryOptions = listOf(
                                    "Choose",
                                    "Pop", "Hip-Hop / Rap", "Rock", "R&B / Soul", "EDM / House",
                                    "Techno & Trance", "Afrobeat & Amapiano", "Latin & Reggaeton",
                                    "K-Pop", "J-Pop", "Country & Folk", "Classical & Symphony",
                                    "Jazz & Blues", "Metal & Hard Rock", "Indie & Alternative",
                                    "Reggae & Dancehall", "Lo-Fi & Chillhop", "Gospel & Spiritual",
                                    "Ambient & Soundscapes", "Acoustic & Singer-Songwriter", "Drill & Trap"
                                )
                                activeCategorySelectedValue = musicState
                                onOptionChosenCallback = { musicState = it }
                            }
                        )
                        HorizontalDivider(modifier = Modifier.padding(horizontal = 16.dp), color = colors.divider)

                        // Dietary Habit
                        CategoryRow(
                            icon = Icons.Default.Restaurant,
                            label = "Dietary Habit",
                            value = dietaryState,
                            onClick = {
                                activeCategoryTitle = "Dietary Habit"
                                activeCategoryOptions = listOf(
                                    "Choose",
                                    "Omnivore",
                                    "Vegetarian",
                                    "Vegan",
                                    "Pescatarian",
                                    "Flexitarian",
                                    "Keto / Low Carb",
                                    "Carnivore",
                                    "Halal",
                                    "Kosher",
                                    "Gluten-Free",
                                    "Dairy-Free",
                                    "Mediterranean",
                                    "Intermittent Fasting",
                                    "Organic / Clean Eating",
                                    "Raw Food",
                                    "No Restrictions"
                                )
                                activeCategorySelectedValue = dietaryState
                                onOptionChosenCallback = { dietaryState = it }
                            }
                        )
                        HorizontalDivider(modifier = Modifier.padding(horizontal = 16.dp), color = colors.divider)

                        // Sleep Habit
                        CategoryRow(
                            icon = Icons.Default.NightlightRound,
                            label = "Sleep Habit",
                            value = sleepState,
                            onClick = {
                                activeCategoryTitle = "Sleep Habit"
                                activeCategoryOptions = listOf(
                                    "Choose",
                                    "Early bird (5 AM - 9 PM)",
                                    "Morning person (6 AM - 10 PM)",
                                    "Balanced schedule (7 AM - 11 PM)",
                                    "Night owl (2 AM - 10 AM)",
                                    "Late night creature (3 AM+)",
                                    "Polyphasic / Power napper",
                                    "Flexible / Irregular",
                                    "Light sleeper",
                                    "Deep 8+ hours dreamer",
                                    "Insomniac / Night thinker"
                                )
                                activeCategorySelectedValue = sleepState
                                onOptionChosenCallback = { sleepState = it }
                            }
                        )
                        HorizontalDivider(modifier = Modifier.padding(horizontal = 16.dp), color = colors.divider)

                        // Smoking
                        CategoryRow(
                            icon = Icons.Default.SmokingRooms,
                            label = "Smoking",
                            value = smokingState,
                            onClick = {
                                activeCategoryTitle = "Smoking"
                                activeCategoryOptions = listOf(
                                    "Choose",
                                    "Non-smoker",
                                    "Never smoked",
                                    "Social smoker",
                                    "Occasional when drinking",
                                    "Regular cigarette smoker",
                                    "Cigar & Pipe enthusiast",
                                    "Hookah / Shisha lover",
                                    "Vape / E-cigarette user",
                                    "In the process of quitting"
                                )
                                activeCategorySelectedValue = smokingState
                                onOptionChosenCallback = { smokingState = it }
                            }
                        )
                        HorizontalDivider(modifier = Modifier.padding(horizontal = 16.dp), color = colors.divider)

                        // Liquor
                        CategoryRow(
                            icon = Icons.Default.LocalBar,
                            label = "Liquor",
                            value = liquorState,
                            onClick = {
                                activeCategoryTitle = "Liquor"
                                activeCategoryOptions = listOf(
                                    "Choose",
                                    "Non-drinker / Teetotaler",
                                    "Rarely / Special occasions only",
                                    "Social drinker",
                                    "Craft beer enthusiast",
                                    "Fine wine connoisseur",
                                    "Whiskey & Bourbon fan",
                                    "Gin & Cocktail lover",
                                    "Tequila & Mezcal fan",
                                    "Champagne & Celebratory",
                                    "Trying to drink less"
                                )
                                activeCategorySelectedValue = liquorState
                                onOptionChosenCallback = { liquorState = it }
                            }
                        )
                        HorizontalDivider(modifier = Modifier.padding(horizontal = 16.dp), color = colors.divider)

                        // Superpower
                        CategoryRow(
                            icon = Icons.Default.Star,
                            label = "Superpower",
                            value = superpowerState,
                            onClick = {
                                activeCategoryTitle = "Superpower"
                                activeCategoryOptions = listOf(
                                    "Choose",
                                    "Teleportation",
                                    "Invisibility",
                                    "Flight / Flying",
                                    "Time Travel & Manipulation",
                                    "Mind Reading & Telepathy",
                                    "Super Strength",
                                    "Super Speed",
                                    "Telekinesis",
                                    "Omnilingualism (Speak all languages)",
                                    "Immortality & Instant Healing",
                                    "Shapeshifting",
                                    "Photographic Memory",
                                    "Weather Control",
                                    "Precognition (Future vision)",
                                    "Invulnerability",
                                    "Infinite Energy"
                                )
                                activeCategorySelectedValue = superpowerState
                                onOptionChosenCallback = { superpowerState = it }
                            }
                        )
                        HorizontalDivider(modifier = Modifier.padding(horizontal = 16.dp), color = colors.divider)

                        // Pets
                        CategoryRow(
                            icon = Icons.Default.Pets,
                            label = "Pets",
                            value = petsState,
                            onClick = {
                                activeCategoryTitle = "Pets"
                                activeCategoryOptions = listOf(
                                    "Choose",
                                    "Dog parent / Dog lover",
                                    "Cat parent / Cat lover",
                                    "Both Dogs & Cats",
                                    "Bird lover",
                                    "Fish & Aquariums",
                                    "Reptiles & Amphibians",
                                    "Hamsters & Small mammals",
                                    "Horses & Farm animals",
                                    "Exotic animals",
                                    "Love all animals",
                                    "No pets currently",
                                    "Allergic to pets",
                                    "Prefer no pets"
                                )
                                activeCategorySelectedValue = petsState
                                onOptionChosenCallback = { petsState = it }
                            }
                        )
                        HorizontalDivider(modifier = Modifier.padding(horizontal = 16.dp), color = colors.divider)

                        // Personality type
                        CategoryRow(
                            icon = Icons.Default.Psychology,
                            label = "Personality type",
                            value = personalityState,
                            onClick = {
                                activeCategoryTitle = "Personality type"
                                activeCategoryOptions = listOf(
                                    "Choose",
                                    "INTJ (Architect)",
                                    "INTP (Logician)",
                                    "ENTJ (Commander)",
                                    "ENTP (Debater)",
                                    "INFJ (Advocate)",
                                    "INFP (Mediator)",
                                    "ENFJ (Protagonist)",
                                    "ENFP (Campaigner)",
                                    "ISTJ (Logistician)",
                                    "ISFJ (Defender)",
                                    "ESTJ (Executive)",
                                    "ESFJ (Consul)",
                                    "ISTP (Virtuoso)",
                                    "ISFP (Adventurer)",
                                    "ESTP (Entrepreneur)",
                                    "ESFP (Entertainer)",
                                    "Ambivert",
                                    "Extrovert",
                                    "Introvert"
                                )
                                activeCategorySelectedValue = personalityState
                                onOptionChosenCallback = { personalityState = it }
                            }
                        )
                        HorizontalDivider(modifier = Modifier.padding(horizontal = 16.dp), color = colors.divider)

                        // Horoscopes
                        CategoryRow(
                            icon = Icons.Default.Star,
                            label = "Horoscopes",
                            value = horoscopesState,
                            onClick = {
                                activeCategoryTitle = "Horoscopes"
                                activeCategoryOptions = listOf(
                                    "Choose",
                                    "Aries ♈ (Mar 21 - Apr 19)",
                                    "Taurus ♉ (Apr 20 - May 20)",
                                    "Gemini ♊ (May 21 - Jun 20)",
                                    "Cancer ♋ (Jun 21 - Jul 22)",
                                    "Leo ♌ (Jul 23 - Aug 22)",
                                    "Virgo ♍ (Aug 23 - Sep 22)",
                                    "Libra ♎ (Sep 23 - Oct 22)",
                                    "Scorpio ♏ (Oct 23 - Nov 21)",
                                    "Sagittarius ♐ (Nov 22 - Dec 21)",
                                    "Capricorn ♑ (Dec 22 - Jan 19)",
                                    "Aquarius ♒ (Jan 20 - Feb 18)",
                                    "Pisces ♓ (Feb 19 - Mar 20)",
                                    "Ophiuchus ⛎"
                                )
                                activeCategorySelectedValue = horoscopesState
                                onOptionChosenCallback = { horoscopesState = it }
                            }
                        )
                    }
                }

                Spacer(modifier = Modifier.height(24.dp))
            }

            // 6. Bottom Yellow "✔ Save Changes" Button
            Surface(
                modifier = Modifier.fillMaxWidth(),
                color = colors.cardBg,
                shadowElevation = 6.dp
            ) {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 16.dp, vertical = 12.dp)
                ) {
                    Button(
                        onClick = {
                            if (!NetworkUtils.requireOnline(context)) {
                                return@Button
                            }
                            if (isSaving) return@Button
                            isSaving = true

                            val updatedProfile = currentProfile.copy(
                                name = nameState.trim(),
                                birthDate = birthDateState,
                                gender = genderState,
                                country = countryState,
                                avatarUrl = avatarUrlState,
                                socialPreferences = socialPrefState,
                                exercise = exerciseState,
                                education = educationState,
                                height = heightState,
                                relationshipStatus = relationshipState,
                                occupation = occupationState,
                                languages = languagesState,
                                hobbiesInterests = hobbiesState,
                                musicPreference = musicState,
                                dietaryHabit = dietaryState,
                                sleepHabit = sleepState,
                                smoking = smokingState,
                                liquor = liquorState,
                                superpower = superpowerState,
                                pets = petsState,
                                personalityType = personalityState,
                                horoscopes = horoscopesState,
                                albumPhotos = albumPhotos.toList()
                            )

                            isSaving = true
                            scope.launch {
                                // 1. Always save locally immediately so User Details displays changes instantly
                                AppDataCacheManager.saveUserDetailCache(context, updatedProfile)
                                SupabaseProfileService.updateInMemoryProfile(updatedProfile)

                                val token = UserSessionManager.getAccessToken(context)
                                UserSessionManager.saveSession(
                                    context = context,
                                    email = updatedProfile.email,
                                    userId = updatedProfile.id,
                                    name = updatedProfile.name,
                                    gender = updatedProfile.gender,
                                    country = updatedProfile.country,
                                    avatarUrl = updatedProfile.avatarUrl,
                                    numericId = updatedProfile.numericId,
                                    coins = updatedProfile.coins,
                                    accessToken = token
                                )

                                // 2. Delete old avatar if photo changed
                                if (currentProfile.avatarUrl.isNotBlank() && currentProfile.avatarUrl != avatarUrlState) {
                                    profileService.deleteOldAvatar(currentProfile.id, currentProfile.avatarUrl)
                                }

                                // 3. Sync to Supabase server if online
                                if (NetworkUtils.isOnline(context)) {
                                    try {
                                        profileService.saveFullProfile(updatedProfile, accessToken = token)
                                    } catch (_: Exception) {}
                                }

                                isSaving = false
                                AppToast.show("Profile updated successfully!")
                                onProfileSaved(updatedProfile)
                            }
                        },
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(52.dp)
                            .testTag("save_profile_button"),
                        shape = RoundedCornerShape(26.dp),
                        colors = ButtonDefaults.buttonColors(containerColor = Color(0xFFFFD600))
                    ) {
                        if (isSaving) {
                            CircularProgressIndicator(color = Color.Black, modifier = Modifier.size(20.dp))
                        } else {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Icon(
                                    imageVector = Icons.Default.Check,
                                    contentDescription = "Save",
                                    tint = Color.Black,
                                    modifier = Modifier.size(20.dp)
                                )
                                Spacer(modifier = Modifier.width(8.dp))
                                Text(
                                    text = "Save Updates",
                                    fontSize = 17.sp,
                                    fontWeight = FontWeight.ExtraBold,
                                    color = Color.Black
                                )
                            }
                        }
                    }
                }
            }
        }
    }

    // 7. Date of Birth Picker Dialog (Day, Month, Year separate selectors)
    if (showDobPicker) {
        DayMonthYearPickerDialog(
            currentBirthDate = birthDateState,
            onDismiss = { showDobPicker = false },
            onDateSelected = { newDate ->
                birthDateState = newDate
                showDobPicker = false
            }
        )
    }

    // 8. Countries Bottom Popup (Scrollable list of all world countries, no Global, App Background color)
    if (showCountryPicker) {
        CountryPickerBottomSheet(
            selectedCountry = countryState,
            onDismiss = { showCountryPicker = false },
            onCountrySelected = { chosen ->
                countryState = chosen
                showCountryPicker = false
            }
        )
    }

    // 9. Category Options Picker Dialog (App background color, searchable)
    if (activeCategoryTitle != null && onOptionChosenCallback != null) {
        OptionSelectionDialog(
            title = activeCategoryTitle!!,
            options = activeCategoryOptions,
            selectedValue = activeCategorySelectedValue,
            onOptionSelected = { chosen ->
                onOptionChosenCallback?.invoke(chosen)
                activeCategoryTitle = null
                onOptionChosenCallback = null
            },
            onDismiss = {
                activeCategoryTitle = null
                onOptionChosenCallback = null
            }
        )
    }

    // 10. Text Edit Dialog (Name)
    if (showTextEditDialog && textEditCallback != null) {
        AlertDialog(
            onDismissRequest = { showTextEditDialog = false },
            containerColor = colors.cardBg,
            title = { Text(textEditTitle, fontWeight = FontWeight.Bold, fontSize = 18.sp, color = colors.textPrimary) },
            text = {
                OutlinedTextField(
                    value = textEditValue,
                    onValueChange = { textEditValue = it },
                    singleLine = true,
                    shape = RoundedCornerShape(12.dp),
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedTextColor = colors.textPrimary,
                        unfocusedTextColor = colors.textPrimary,
                        focusedBorderColor = Color(0xFFFFD600),
                        unfocusedBorderColor = colors.cardBorder
                    ),
                    modifier = Modifier.fillMaxWidth()
                )
            },
            confirmButton = {
                Button(
                    onClick = {
                        textEditCallback?.invoke(textEditValue.trim())
                        showTextEditDialog = false
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = Color(0xFFFFD600))
                ) {
                    Text("OK", color = Color.Black, fontWeight = FontWeight.Bold)
                }
            },
            dismissButton = {
                TextButton(onClick = { showTextEditDialog = false }) {
                    Text("Cancel", color = colors.textSecondary)
                }
            }
        )
    }
}

/**
 * Custom Day, Month, Year separate selector Dialog
 * Styled with App background color (NOT black)
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun DayMonthYearPickerDialog(
    currentBirthDate: String,
    onDismiss: () -> Unit,
    onDateSelected: (String) -> Unit
) {
    val colors = AppTheme.colors

    // Parse initial year, month, day
    val parts = currentBirthDate.split("-")
    val initialYear = parts.getOrNull(0)?.toIntOrNull() ?: 2000
    val initialMonth = parts.getOrNull(1)?.toIntOrNull() ?: 1
    val initialDay = parts.getOrNull(2)?.toIntOrNull() ?: 1

    var selectedDay by remember { mutableIntStateOf(initialDay.coerceIn(1, 31)) }
    var selectedMonth by remember { mutableIntStateOf(initialMonth.coerceIn(1, 12)) }
    var selectedYear by remember { mutableIntStateOf(initialYear.coerceIn(1940, 2024)) }

    val monthNames = remember {
        listOf(
            "01 - Jan", "02 - Feb", "03 - Mar", "04 - Apr", "05 - May", "06 - Jun",
            "07 - Jul", "08 - Aug", "09 - Sep", "10 - Oct", "11 - Nov", "12 - Dec"
        )
    }

    val daysInMonth = remember(selectedMonth, selectedYear) {
        when (selectedMonth) {
            2 -> if ((selectedYear % 4 == 0 && selectedYear % 100 != 0) || (selectedYear % 400 == 0)) 29 else 28
            4, 6, 9, 11 -> 30
            else -> 31
        }
    }

    LaunchedEffect(daysInMonth) {
        if (selectedDay > daysInMonth) {
            selectedDay = daysInMonth
        }
    }

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        containerColor = colors.cardBg,
        dragHandle = {
            Box(
                modifier = Modifier
                    .padding(vertical = 10.dp)
                    .width(36.dp)
                    .height(4.dp)
                    .clip(RoundedCornerShape(2.dp))
                    .background(colors.textSecondary.copy(alpha = 0.4f))
            )
        }
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 20.dp, vertical = 8.dp)
        ) {
            Text(
                text = "Select Date of Birth",
                fontSize = 18.sp,
                fontWeight = FontWeight.Bold,
                color = colors.textPrimary
            )
            Text(
                text = "Choose day, month, and year separately",
                fontSize = 13.sp,
                color = colors.textSecondary,
                modifier = Modifier.padding(top = 2.dp, bottom = 16.dp)
            )

            // 3 Column Pickers: Day, Month, Year
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(200.dp),
                horizontalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                // 1. Day Column
                Column(
                    modifier = Modifier
                        .weight(1f)
                        .fillMaxHeight()
                        .clip(RoundedCornerShape(12.dp))
                        .background(colors.screenBg)
                        .padding(4.dp)
                ) {
                    Text(
                        text = "Day",
                        fontSize = 12.sp,
                        fontWeight = FontWeight.Bold,
                        color = colors.textSecondary,
                        modifier = Modifier
                            .align(Alignment.CenterHorizontally)
                            .padding(vertical = 4.dp)
                    )
                    LazyColumn(modifier = Modifier.fillMaxSize()) {
                        items((1..daysInMonth).toList()) { day ->
                            val isSelected = day == selectedDay
                            Box(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(vertical = 2.dp)
                                    .clip(RoundedCornerShape(8.dp))
                                    .background(if (isSelected) Color(0xFFFFD600) else Color.Transparent)
                                    .clickable { selectedDay = day }
                                    .padding(vertical = 8.dp),
                                contentAlignment = Alignment.Center
                            ) {
                                Text(
                                    text = String.format("%02d", day),
                                    fontSize = 14.sp,
                                    fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Normal,
                                    color = if (isSelected) Color.Black else colors.textPrimary
                                )
                            }
                        }
                    }
                }

                // 2. Month Column
                Column(
                    modifier = Modifier
                        .weight(1.3f)
                        .fillMaxHeight()
                        .clip(RoundedCornerShape(12.dp))
                        .background(colors.screenBg)
                        .padding(4.dp)
                ) {
                    Text(
                        text = "Month",
                        fontSize = 12.sp,
                        fontWeight = FontWeight.Bold,
                        color = colors.textSecondary,
                        modifier = Modifier
                            .align(Alignment.CenterHorizontally)
                            .padding(vertical = 4.dp)
                    )
                    LazyColumn(modifier = Modifier.fillMaxSize()) {
                        items((1..12).toList()) { month ->
                            val isSelected = month == selectedMonth
                            Box(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(vertical = 2.dp)
                                    .clip(RoundedCornerShape(8.dp))
                                    .background(if (isSelected) Color(0xFFFFD600) else Color.Transparent)
                                    .clickable { selectedMonth = month }
                                    .padding(vertical = 8.dp),
                                contentAlignment = Alignment.Center
                            ) {
                                Text(
                                    text = monthNames[month - 1],
                                    fontSize = 13.sp,
                                    fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Normal,
                                    color = if (isSelected) Color.Black else colors.textPrimary
                                )
                            }
                        }
                    }
                }

                // 3. Year Column
                Column(
                    modifier = Modifier
                        .weight(1.2f)
                        .fillMaxHeight()
                        .clip(RoundedCornerShape(12.dp))
                        .background(colors.screenBg)
                        .padding(4.dp)
                ) {
                    Text(
                        text = "Year",
                        fontSize = 12.sp,
                        fontWeight = FontWeight.Bold,
                        color = colors.textSecondary,
                        modifier = Modifier
                            .align(Alignment.CenterHorizontally)
                            .padding(vertical = 4.dp)
                    )
                    LazyColumn(modifier = Modifier.fillMaxSize()) {
                        items((2024 downTo 1940).toList()) { year ->
                            val isSelected = year == selectedYear
                            Box(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(vertical = 2.dp)
                                    .clip(RoundedCornerShape(8.dp))
                                    .background(if (isSelected) Color(0xFFFFD600) else Color.Transparent)
                                    .clickable { selectedYear = year }
                                    .padding(vertical = 8.dp),
                                contentAlignment = Alignment.Center
                            ) {
                                Text(
                                    text = year.toString(),
                                    fontSize = 14.sp,
                                    fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Normal,
                                    color = if (isSelected) Color.Black else colors.textPrimary
                                )
                            }
                        }
                    }
                }
            }

            Spacer(modifier = Modifier.height(16.dp))

            // Action Buttons
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(bottom = 16.dp),
                horizontalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                OutlinedButton(
                    onClick = onDismiss,
                    modifier = Modifier
                        .weight(1f)
                        .height(48.dp),
                    shape = RoundedCornerShape(24.dp),
                    border = androidx.compose.foundation.BorderStroke(1.dp, colors.cardBorder),
                    colors = ButtonDefaults.outlinedButtonColors(contentColor = colors.textPrimary)
                ) {
                    Text("Cancel", fontWeight = FontWeight.Bold)
                }

                Button(
                    onClick = {
                        val formatted = String.format("%04d-%02d-%02d", selectedYear, selectedMonth, selectedDay)
                        onDateSelected(formatted)
                    },
                    modifier = Modifier
                        .weight(1f)
                        .height(48.dp),
                    shape = RoundedCornerShape(24.dp),
                    colors = ButtonDefaults.buttonColors(containerColor = Color(0xFFFFD600))
                ) {
                    Text("Confirm Date", color = Color.Black, fontWeight = FontWeight.Bold)
                }
            }
        }
    }
}

/**
 * Scrollable Bottom Popup listing ALL countries of the world without "Global"
 * Styled in the App background color (NOT black)
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun CountryPickerBottomSheet(
    selectedCountry: String,
    onDismiss: () -> Unit,
    onCountrySelected: (String) -> Unit
) {
    val colors = AppTheme.colors
    var searchQuery by remember { mutableStateOf("") }

    val allCountries = remember { CountryData.worldCountries }
    val filteredCountries = remember(searchQuery) {
        if (searchQuery.isBlank()) {
            allCountries
        } else {
            allCountries.filter { it.contains(searchQuery.trim(), ignoreCase = true) }
        }
    }

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        containerColor = colors.cardBg,
        dragHandle = {
            Box(
                modifier = Modifier
                    .padding(vertical = 10.dp)
                    .width(36.dp)
                    .height(4.dp)
                    .clip(RoundedCornerShape(2.dp))
                    .background(colors.textSecondary.copy(alpha = 0.4f))
            )
        }
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 20.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Text(
                    text = "Select Country",
                    fontSize = 18.sp,
                    fontWeight = FontWeight.Bold,
                    color = colors.textPrimary
                )
                IconButton(onClick = onDismiss) {
                    Icon(
                        imageVector = Icons.Default.Close,
                        contentDescription = "Close",
                        tint = colors.textSecondary
                    )
                }
            }

            Spacer(modifier = Modifier.height(8.dp))

            // Search Box
            OutlinedTextField(
                value = searchQuery,
                onValueChange = { searchQuery = it },
                placeholder = { Text("Search country...", color = colors.textSecondary, fontSize = 14.sp) },
                leadingIcon = {
                    Icon(
                        imageVector = Icons.Default.Search,
                        contentDescription = "Search",
                        tint = colors.textSecondary,
                        modifier = Modifier.size(20.dp)
                    )
                },
                singleLine = true,
                shape = RoundedCornerShape(16.dp),
                colors = OutlinedTextFieldDefaults.colors(
                    focusedTextColor = colors.textPrimary,
                    unfocusedTextColor = colors.textPrimary,
                    focusedBorderColor = Color(0xFFFFD600),
                    unfocusedBorderColor = colors.cardBorder,
                    focusedContainerColor = colors.screenBg,
                    unfocusedContainerColor = colors.screenBg
                ),
                modifier = Modifier.fillMaxWidth()
            )

            Spacer(modifier = Modifier.height(12.dp))

            // Scrollable Countries List
            LazyColumn(
                modifier = Modifier
                    .fillMaxWidth()
                    .heightIn(max = 380.dp)
                    .padding(bottom = 16.dp)
            ) {
                items(filteredCountries) { country ->
                    val isSelected = country.equals(selectedCountry, ignoreCase = true)
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(12.dp))
                            .clickable { onCountrySelected(country) }
                            .padding(vertical = 12.dp, horizontal = 10.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Icon(
                                imageVector = Icons.Default.Public,
                                contentDescription = null,
                                tint = if (isSelected) Color(0xFFFFD600) else colors.textSecondary,
                                modifier = Modifier.size(18.dp)
                            )
                            Spacer(modifier = Modifier.width(12.dp))
                            Text(
                                text = country,
                                fontSize = 15.sp,
                                fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Normal,
                                color = if (isSelected) colors.textPrimary else colors.textPrimary
                            )
                        }

                        if (isSelected) {
                            Icon(
                                imageVector = Icons.Default.Check,
                                contentDescription = "Selected",
                                tint = Color(0xFFFFD600),
                                modifier = Modifier.size(20.dp)
                            )
                        }
                    }
                    HorizontalDivider(color = colors.divider.copy(alpha = 0.5f))
                }
            }
        }
    }
}

@Composable
private fun InfoRow(
    label: String,
    value: String,
    editable: Boolean = true,
    onClick: (() -> Unit)? = null
) {
    val colors = AppTheme.colors
    val rowModifier = if (editable && onClick != null) {
        Modifier
            .fillMaxWidth()
            .clickable { onClick() }
            .padding(horizontal = 16.dp, vertical = 12.dp)
    } else {
        Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 12.dp)
    }

    Row(
        modifier = rowModifier,
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.SpaceBetween
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier.weight(1f)
        ) {
            ProfileCategory3DIcon(categoryKey = label, size = 26.dp)
            Spacer(modifier = Modifier.width(12.dp))
            Text(
                text = label,
                fontSize = 15.sp,
                fontWeight = FontWeight.Medium,
                color = colors.textPrimary
            )
        }

        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(
                text = value,
                fontSize = 15.sp,
                fontWeight = FontWeight.Bold,
                color = colors.textPrimary
            )
            if (editable && onClick != null) {
                Spacer(modifier = Modifier.width(6.dp))
                Icon(
                    imageVector = Icons.Default.ChevronRight,
                    contentDescription = null,
                    tint = colors.textSecondary,
                    modifier = Modifier.size(18.dp)
                )
            }
        }
    }
}

@Composable
private fun CategoryRow(
    icon: ImageVector,
    label: String,
    value: String,
    onClick: () -> Unit
) {
    val colors = AppTheme.colors
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable { onClick() }
            .padding(horizontal = 16.dp, vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.SpaceBetween
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier.weight(1f)
        ) {
            ProfileCategory3DIcon(categoryKey = label, size = 26.dp)
            Spacer(modifier = Modifier.width(12.dp))
            Text(
                text = label,
                fontSize = 15.sp,
                fontWeight = FontWeight.Medium,
                color = colors.textPrimary
            )
        }

        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(
                text = value.ifBlank { "Choose" },
                fontSize = 15.sp,
                fontWeight = FontWeight.Bold,
                color = if (value == "Choose" || value.isBlank()) colors.textSecondary else colors.textPrimary
            )
            Spacer(modifier = Modifier.width(6.dp))
            Icon(
                imageVector = Icons.Default.ChevronRight,
                contentDescription = null,
                tint = colors.textSecondary,
                modifier = Modifier.size(18.dp)
            )
        }
    }
}

/**
 * Selection Dialog for Options & Categories
 * Styled with App background color (NOT black), with instant search filter if list is long
 */
@Composable
private fun OptionSelectionDialog(
    title: String,
    options: List<String>,
    selectedValue: String,
    onOptionSelected: (String) -> Unit,
    onDismiss: () -> Unit
) {
    val colors = AppTheme.colors
    var searchFilter by remember { mutableStateOf("") }

    val filtered = remember(searchFilter, options) {
        if (searchFilter.isBlank()) options
        else options.filter { it.contains(searchFilter.trim(), ignoreCase = true) }
    }

    AlertDialog(
        onDismissRequest = onDismiss,
        containerColor = colors.cardBg,
        title = {
            Text(
                text = "Select $title",
                fontWeight = FontWeight.Bold,
                fontSize = 18.sp,
                color = colors.textPrimary
            )
        },
        text = {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .heightIn(max = 340.dp)
            ) {
                // Show search if options > 8
                if (options.size > 8) {
                    OutlinedTextField(
                        value = searchFilter,
                        onValueChange = { searchFilter = it },
                        placeholder = { Text("Search...", color = colors.textSecondary, fontSize = 13.sp) },
                        singleLine = true,
                        shape = RoundedCornerShape(12.dp),
                        colors = OutlinedTextFieldDefaults.colors(
                            focusedTextColor = colors.textPrimary,
                            unfocusedTextColor = colors.textPrimary,
                            focusedBorderColor = Color(0xFFFFD600),
                            unfocusedBorderColor = colors.cardBorder,
                            focusedContainerColor = colors.screenBg,
                            unfocusedContainerColor = colors.screenBg
                        ),
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(bottom = 8.dp)
                    )
                }

                LazyColumn(modifier = Modifier.fillMaxWidth()) {
                    items(filtered) { option ->
                        val isSelected = (option == selectedValue)
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clip(RoundedCornerShape(10.dp))
                                .clickable { onOptionSelected(option) }
                                .padding(vertical = 10.dp, horizontal = 4.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            RadioButton(
                                selected = isSelected,
                                onClick = { onOptionSelected(option) },
                                colors = RadioButtonDefaults.colors(
                                    selectedColor = Color(0xFFFFD600),
                                    unselectedColor = colors.textSecondary
                                )
                            )
                            Spacer(modifier = Modifier.width(8.dp))
                            Text(
                                text = option,
                                fontSize = 15.sp,
                                fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Normal,
                                color = colors.textPrimary
                            )
                        }
                    }
                }
            }
        },
        confirmButton = {
            TextButton(onClick = onDismiss) {
                Text("Cancel", color = colors.textSecondary, fontWeight = FontWeight.Bold)
            }
        }
    )
}

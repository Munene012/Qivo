package com.example.ui.screens
import com.example.ui.components.AppToast

import android.widget.Toast
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowDropDown
import androidx.compose.material.icons.filled.CalendarMonth
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Female
import androidx.compose.material.icons.filled.Male
import androidx.compose.material.icons.filled.Public
import androidx.compose.material.icons.filled.Search
import androidx.compose.foundation.BorderStroke
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
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
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.R
import com.example.data.CountryData
import com.example.data.CountryDetector
import com.example.data.NetworkUtils
import com.example.data.SupabaseProfileService
import com.example.data.UserSessionManager
import com.example.ui.theme.QivoTextMuted
import kotlinx.coroutines.launch
import java.util.Calendar

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun CreateAccountScreen(
    userEmail: String,
    userId: String,
    initialName: String = "",
    initialAvatarUrl: String = "",
    initialCountry: String = "",
    onSaveDetails: (name: String, gender: String, birthDate: String, country: String, avatarUrl: String, numericId: Long) -> Unit,
    onSkipToHome: ((name: String, gender: String, birthDate: String, country: String, numericId: Long) -> Unit)? = null
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val scrollState = rememberScrollState()
    val profileService = remember { SupabaseProfileService() }

    // User inputs
    val defaultName = remember {
        if (initialName.isNotBlank() && initialName != "QIVO User" && initialName != "User") {
            initialName
        } else if (userEmail.contains("@")) {
            val prefix = userEmail.substringBefore("@")
            if (prefix.isNotBlank()) prefix else ""
        } else {
            ""
        }
    }
    var nameInput by remember { mutableStateOf(defaultName) }
    var avatarUrlInput by remember { mutableStateOf(initialAvatarUrl) }
    
    // Day, Month, Year separate DOB selection
    var selectedDay by remember { mutableStateOf<Int?>(null) }
    var selectedMonth by remember { mutableStateOf<Int?>(null) }
    var selectedYear by remember { mutableStateOf<Int?>(null) }
    var isDayDropdownExpanded by remember { mutableStateOf(false) }
    var isMonthDropdownExpanded by remember { mutableStateOf(false) }
    var isYearDropdownExpanded by remember { mutableStateOf(false) }
    val monthList = remember { listOf("Jan", "Feb", "Mar", "Apr", "May", "Jun", "Jul", "Aug", "Sep", "Oct", "Nov", "Dec") }

    // Real-time country detection with user selectable country
    var selectedCountry by remember { mutableStateOf(if (initialCountry.isNotBlank() && initialCountry != "United States") initialCountry else "") }
    var showCountryPicker by remember { mutableStateOf(false) }

    LaunchedEffect(Unit) {
        val detected = CountryDetector.detectCountry(context)
        if (detected != null && selectedCountry.isBlank()) {
            selectedCountry = detected.first
        } else if (selectedCountry.isBlank()) {
            selectedCountry = if (initialCountry.isNotBlank()) initialCountry else "Kenya"
        }
    }
    
    // Gender selection (default empty)
    var selectedGender by remember { mutableStateOf("") }

    var isSavingProfile by remember { mutableStateOf(false) }

    // Generated numeric ID (6-9 digits)
    val numericId = remember { profileService.generateNumericId() }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(Color.White)
            .statusBarsPadding()
            .navigationBarsPadding()
            .testTag("create_account_screen_root")
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .verticalScroll(scrollState)
                .padding(horizontal = 24.dp)
        ) {
            Spacer(modifier = Modifier.height(12.dp))

            // Header Bar
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(48.dp),
                contentAlignment = Alignment.Center
            ) {
                Text(
                    text = "Create Account",
                    fontSize = 18.sp,
                    fontWeight = FontWeight.Bold,
                    color = Color.Black
                )
            }

            Spacer(modifier = Modifier.height(16.dp))

            // Main Title:
            // "Introduce"
            // "YourSelf"
            Text(
                text = "Introduce",
                fontSize = 32.sp,
                fontWeight = FontWeight.Bold,
                color = Color.Black,
                letterSpacing = (-0.5).sp
            )
            Text(
                text = "YourSelf",
                fontSize = 36.sp,
                fontWeight = FontWeight.ExtraBold,
                fontStyle = FontStyle.Italic,
                color = Color.Black,
                letterSpacing = (-0.5).sp
            )

            Spacer(modifier = Modifier.height(28.dp))

            // Input 1: Name Field (Pill shape)
            OutlinedTextField(
                value = nameInput,
                onValueChange = { nameInput = it },
                placeholder = {
                    Text(
                        text = "Name",
                        color = Color(0xFFB0B0B0),
                        fontSize = 16.sp
                    )
                },
                singleLine = true,
                shape = RoundedCornerShape(24.dp),
                colors = OutlinedTextFieldDefaults.colors(
                    focusedContainerColor = Color(0xFFF3F4F6),
                    unfocusedContainerColor = Color(0xFFF3F4F6),
                    focusedBorderColor = Color.Transparent,
                    unfocusedBorderColor = Color.Transparent,
                    focusedTextColor = Color.Black,
                    unfocusedTextColor = Color.Black
                ),
                modifier = Modifier
                    .fillMaxWidth()
                    .height(58.dp)
                    .testTag("name_input_field")
            )

            Spacer(modifier = Modifier.height(16.dp))

            // Input 2: Date of Birth (Age) - Day, Month, Year separate dropdown selectors
            Text(
                text = "Date of Birth (Age)",
                fontSize = 14.sp,
                fontWeight = FontWeight.Bold,
                color = Color.Black
            )
            Spacer(modifier = Modifier.height(6.dp))

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                // Day Dropdown
                Box(modifier = Modifier.weight(1f)) {
                    Surface(
                        shape = RoundedCornerShape(20.dp),
                        color = Color(0xFFF3F4F6),
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(54.dp)
                            .clickable { isDayDropdownExpanded = true }
                    ) {
                        Row(
                            modifier = Modifier
                                .fillMaxSize()
                                .padding(horizontal = 12.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            Text(
                                text = if (selectedDay != null) String.format("%02d", selectedDay) else "Day",
                                fontSize = 15.sp,
                                fontWeight = FontWeight.Medium,
                                color = if (selectedDay != null) Color.Black else Color(0xFFB0B0B0)
                            )
                            Icon(
                                imageVector = Icons.Default.ArrowDropDown,
                                contentDescription = null,
                                tint = Color.Black,
                                modifier = Modifier.size(20.dp)
                            )
                        }
                    }
                    DropdownMenu(
                        expanded = isDayDropdownExpanded,
                        onDismissRequest = { isDayDropdownExpanded = false },
                        modifier = Modifier
                            .height(260.dp)
                            .background(Color.White)
                    ) {
                        (1..31).forEach { day ->
                            DropdownMenuItem(
                                text = { Text(String.format("%02d", day), fontSize = 14.sp, color = Color.Black) },
                                onClick = {
                                    selectedDay = day
                                    isDayDropdownExpanded = false
                                }
                            )
                        }
                    }
                }

                // Month Dropdown
                Box(modifier = Modifier.weight(1.2f)) {
                    Surface(
                        shape = RoundedCornerShape(20.dp),
                        color = Color(0xFFF3F4F6),
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(54.dp)
                            .clickable { isMonthDropdownExpanded = true }
                    ) {
                        Row(
                            modifier = Modifier
                                .fillMaxSize()
                                .padding(horizontal = 12.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            Text(
                                text = if (selectedMonth != null) monthList[selectedMonth!! - 1] else "Month",
                                fontSize = 15.sp,
                                fontWeight = FontWeight.Medium,
                                color = if (selectedMonth != null) Color.Black else Color(0xFFB0B0B0)
                            )
                            Icon(
                                imageVector = Icons.Default.ArrowDropDown,
                                contentDescription = null,
                                tint = Color.Black,
                                modifier = Modifier.size(20.dp)
                            )
                        }
                    }
                    DropdownMenu(
                        expanded = isMonthDropdownExpanded,
                        onDismissRequest = { isMonthDropdownExpanded = false },
                        modifier = Modifier
                            .height(260.dp)
                            .background(Color.White)
                    ) {
                        monthList.forEachIndexed { index, mName ->
                            DropdownMenuItem(
                                text = { Text("${index + 1} - $mName", fontSize = 14.sp, color = Color.Black) },
                                onClick = {
                                    selectedMonth = index + 1
                                    isMonthDropdownExpanded = false
                                }
                            )
                        }
                    }
                }

                // Year Dropdown
                Box(modifier = Modifier.weight(1.2f)) {
                    Surface(
                        shape = RoundedCornerShape(20.dp),
                        color = Color(0xFFF3F4F6),
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(54.dp)
                            .clickable { isYearDropdownExpanded = true }
                    ) {
                        Row(
                            modifier = Modifier
                                .fillMaxSize()
                                .padding(horizontal = 12.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            Text(
                                text = if (selectedYear != null) selectedYear.toString() else "Year",
                                fontSize = 15.sp,
                                fontWeight = FontWeight.Medium,
                                color = if (selectedYear != null) Color.Black else Color(0xFFB0B0B0)
                            )
                            Icon(
                                imageVector = Icons.Default.ArrowDropDown,
                                contentDescription = null,
                                tint = Color.Black,
                                modifier = Modifier.size(20.dp)
                            )
                        }
                    }
                    DropdownMenu(
                        expanded = isYearDropdownExpanded,
                        onDismissRequest = { isYearDropdownExpanded = false },
                        modifier = Modifier
                            .height(260.dp)
                            .background(Color.White)
                    ) {
                        (2008 downTo 1940).forEach { year ->
                            DropdownMenuItem(
                                text = { Text(year.toString(), fontSize = 14.sp, color = Color.Black) },
                                onClick = {
                                    selectedYear = year
                                    isYearDropdownExpanded = false
                                }
                            )
                        }
                    }
                }
            }

            Spacer(modifier = Modifier.height(16.dp))

            // Input 3: Country / Region Selector with Flag & Search BottomSheet
            Text(
                text = "Country / Region",
                fontSize = 14.sp,
                fontWeight = FontWeight.Bold,
                color = Color.Black
            )
            Spacer(modifier = Modifier.height(6.dp))

            Surface(
                shape = RoundedCornerShape(20.dp),
                color = Color(0xFFF3F4F6),
                modifier = Modifier
                    .fillMaxWidth()
                    .height(56.dp)
                    .clickable { showCountryPicker = true }
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(horizontal = 16.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(
                            imageVector = Icons.Default.Public,
                            contentDescription = "Country Icon",
                            tint = Color(0xFF4285F4),
                            modifier = Modifier.size(22.dp)
                        )
                        Spacer(modifier = Modifier.width(10.dp))
                        Text(
                            text = if (selectedCountry.isNotBlank()) selectedCountry else "Select your country",
                            fontSize = 16.sp,
                            fontWeight = FontWeight.Medium,
                            color = if (selectedCountry.isNotBlank()) Color.Black else Color(0xFFB0B0B0)
                        )
                    }

                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text(
                            text = "Change",
                            fontSize = 13.sp,
                            fontWeight = FontWeight.Bold,
                            color = Color(0xFF4285F4)
                        )
                        Icon(
                            imageVector = Icons.Default.ArrowDropDown,
                            contentDescription = null,
                            tint = Color(0xFF4285F4),
                            modifier = Modifier.size(20.dp)
                        )
                    }
                }
            }

            Spacer(modifier = Modifier.height(24.dp))

            // Gender Cards Section: Male & Female with 3D Mascots
            Text(
                text = "Gender",
                fontSize = 14.sp,
                fontWeight = FontWeight.Bold,
                color = Color.Black
            )
            Spacer(modifier = Modifier.height(6.dp))

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(16.dp)
            ) {
                // Male Card with 3D Green Dinosaur Mascot
                val isMaleSelected = selectedGender == "Male"
                Surface(
                    onClick = { selectedGender = "Male" },
                    modifier = Modifier
                        .weight(1f)
                        .height(180.dp)
                        .then(
                            if (isMaleSelected) Modifier.border(2.5.dp, Color(0xFF4CAF50), RoundedCornerShape(28.dp))
                            else Modifier
                        ),
                    shape = RoundedCornerShape(28.dp),
                    color = Color(0xFFF3F4F6)
                ) {
                    Column(
                        modifier = Modifier
                            .fillMaxSize()
                            .padding(14.dp),
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.Center
                    ) {
                        Image(
                            painter = painterResource(id = R.drawable.img_male_mascot),
                            contentDescription = "Male Mascot",
                            modifier = Modifier
                                .size(105.dp)
                                .clip(RoundedCornerShape(22.dp)),
                            contentScale = ContentScale.Crop
                        )

                        Spacer(modifier = Modifier.height(10.dp))

                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Icon(
                                imageVector = Icons.Default.Male,
                                contentDescription = "Male",
                                tint = Color(0xFF2E7D32),
                                modifier = Modifier.size(20.dp)
                            )
                            Spacer(modifier = Modifier.width(4.dp))
                            Text(
                                text = "Male",
                                fontSize = 16.sp,
                                fontWeight = FontWeight.Bold,
                                color = Color.Black
                            )
                        }
                    }
                }

                // Female Card with 3D Sleeping Bunny Mascot
                val isFemaleSelected = selectedGender == "Female"
                Surface(
                    onClick = { selectedGender = "Female" },
                    modifier = Modifier
                        .weight(1f)
                        .height(180.dp)
                        .then(
                            if (isFemaleSelected) Modifier.border(2.5.dp, Color(0xFFE91E63), RoundedCornerShape(28.dp))
                            else Modifier
                        ),
                    shape = RoundedCornerShape(28.dp),
                    color = Color(0xFFF3F4F6)
                ) {
                    Column(
                        modifier = Modifier
                            .fillMaxSize()
                            .padding(14.dp),
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.Center
                    ) {
                        Image(
                            painter = painterResource(id = R.drawable.img_female_mascot),
                            contentDescription = "Female Mascot",
                            modifier = Modifier
                                .size(105.dp)
                                .clip(RoundedCornerShape(22.dp)),
                            contentScale = ContentScale.Crop
                        )

                        Spacer(modifier = Modifier.height(10.dp))

                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Icon(
                                imageVector = Icons.Default.Female,
                                contentDescription = "Female",
                                tint = Color(0xFFE91E63),
                                modifier = Modifier.size(20.dp)
                            )
                            Spacer(modifier = Modifier.width(4.dp))
                            Text(
                                text = "Female",
                                fontSize = 16.sp,
                                fontWeight = FontWeight.Bold,
                                color = Color.Black
                            )
                        }
                    }
                }
            }

            Spacer(modifier = Modifier.height(16.dp))

            Text(
                text = "Gender cannot be changed once selected",
                fontSize = 13.sp,
                color = QivoTextMuted,
                textAlign = TextAlign.Center,
                modifier = Modifier.fillMaxWidth()
            )

            Spacer(modifier = Modifier.height(36.dp))

            // Action handler for saving details (either advancing to profile picture step or skipping directly to home)
            val handleSaveOrSkip: (Boolean) -> Unit = { skipPhoto ->
                if (nameInput.isBlank()) {
                    AppToast.show("Please enter your name")
                } else if (selectedDay == null || selectedMonth == null || selectedYear == null) {
                    AppToast.show("Please select your Day, Month, and Year of birth")
                } else if (selectedCountry.isBlank()) {
                    AppToast.show("Please select your country")
                } else if (selectedGender.isBlank()) {
                    AppToast.show("Please select your gender (Male or Female)")
                } else {
                    val formattedBirthDate = String.format("%04d-%02d-%02d", selectedYear, selectedMonth, selectedDay)
                    val calculatedAge = 2026 - selectedYear!!

                    if (calculatedAge < 18) {
                        AppToast.show("Registration restricted: You must be at least 18 years old to join QIVO.", isLong = true)
                    } else if (!NetworkUtils.isOnline(context)) {
                        AppToast.show("No internet connection. Please connect to the internet to save your details.", isLong = true)
                    } else {
                        val finalName = nameInput.trim()
                        isSavingProfile = true
                        scope.launch {
                            val resolvedCountry = if (selectedCountry.isNotBlank()) {
                                selectedCountry
                            } else {
                                CountryDetector.detectCountry(context)?.first ?: "Kenya"
                            }

                            val token = UserSessionManager.getValidAccessToken(context)
                            val refreshToken = UserSessionManager.getRefreshToken(context)
                            val finalAvatar = if (skipPhoto) "" else avatarUrlInput
                            val saveSuccessful = profileService.saveProfile(
                                userId = userId,
                                email = userEmail,
                                name = finalName,
                                gender = selectedGender,
                                birthDate = formattedBirthDate,
                                country = resolvedCountry,
                                avatarUrl = finalAvatar,
                                numericId = numericId,
                                accessToken = token
                            )

                            if (!saveSuccessful) {
                                isSavingProfile = false
                                AppToast.show("Unable to save details to server. Please check your internet connection and try again.", isLong = true)
                                return@launch
                            }

                            // Apply updates only when saved to Supabase server successfully
                            UserSessionManager.saveSession(
                                context = context,
                                email = userEmail,
                                userId = userId,
                                name = finalName,
                                gender = selectedGender,
                                country = resolvedCountry,
                                avatarUrl = finalAvatar,
                                numericId = numericId,
                                accessToken = token,
                                refreshToken = refreshToken
                            )

                            isSavingProfile = false
                            if (skipPhoto && onSkipToHome != null) {
                                AppToast.show("Welcome to QIVO, $finalName! 🎉")
                                onSkipToHome(finalName, selectedGender, formattedBirthDate, resolvedCountry, numericId)
                            } else {
                                onSaveDetails(finalName, selectedGender, formattedBirthDate, resolvedCountry, finalAvatar, numericId)
                            }
                        }
                    }
                }
            }

            // Primary Button: "Next: Profile Picture"
            Button(
                onClick = { handleSaveOrSkip(false) },
                enabled = !isSavingProfile,
                modifier = Modifier
                    .fillMaxWidth()
                    .height(56.dp)
                    .testTag("save_details_button"),
                shape = CircleShape,
                colors = ButtonDefaults.buttonColors(
                    containerColor = Color.Black,
                    contentColor = Color(0xFFB3FF00) // Neon Yellow/Green text
                )
            ) {
                if (isSavingProfile) {
                    CircularProgressIndicator(color = Color(0xFFB3FF00), modifier = Modifier.size(24.dp))
                } else {
                    Text(
                        text = "Next: Profile Picture",
                        fontSize = 17.sp,
                        fontWeight = FontWeight.Bold
                    )
                }
            }

            Spacer(modifier = Modifier.height(12.dp))

            // Secondary Option: "Skip Photo & Complete Setup"
            OutlinedButton(
                onClick = { handleSaveOrSkip(true) },
                enabled = !isSavingProfile,
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(bottom = 28.dp)
                    .height(52.dp)
                    .testTag("skip_photo_create_account_button"),
                shape = CircleShape,
                border = BorderStroke(1.5.dp, Color(0xFFD1D5DB)),
                colors = ButtonDefaults.outlinedButtonColors(
                    contentColor = Color(0xFF374151)
                )
            ) {
                Text(
                    text = "Skip Photo & Complete Setup",
                    fontSize = 15.sp,
                    fontWeight = FontWeight.SemiBold
                )
            }
        }

        // Country Selection Bottom Sheet
        if (showCountryPicker) {
            CountrySelectionBottomSheet(
                selectedCountry = selectedCountry,
                onDismiss = { showCountryPicker = false },
                onCountrySelected = { chosen ->
                    selectedCountry = chosen
                    showCountryPicker = false
                }
            )
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun CountrySelectionBottomSheet(
    selectedCountry: String,
    onDismiss: () -> Unit,
    onCountrySelected: (String) -> Unit
) {
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
        containerColor = Color.White,
        dragHandle = {
            Box(
                modifier = Modifier
                    .padding(vertical = 10.dp)
                    .width(36.dp)
                    .height(4.dp)
                    .clip(RoundedCornerShape(2.dp))
                    .background(Color.LightGray)
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
                    text = "Select Country / Region",
                    fontSize = 18.sp,
                    fontWeight = FontWeight.Bold,
                    color = Color.Black
                )
                IconButton(onClick = onDismiss) {
                    Icon(
                        imageVector = Icons.Default.Close,
                        contentDescription = "Close",
                        tint = Color.DarkGray
                    )
                }
            }

            Spacer(modifier = Modifier.height(8.dp))

            // Search Box
            OutlinedTextField(
                value = searchQuery,
                onValueChange = { searchQuery = it },
                placeholder = { Text("Search country...", color = Color.Gray, fontSize = 14.sp) },
                leadingIcon = {
                    Icon(
                        imageVector = Icons.Default.Search,
                        contentDescription = "Search",
                        tint = Color.Gray,
                        modifier = Modifier.size(20.dp)
                    )
                },
                singleLine = true,
                shape = RoundedCornerShape(16.dp),
                colors = OutlinedTextFieldDefaults.colors(
                    focusedTextColor = Color.Black,
                    unfocusedTextColor = Color.Black,
                    focusedBorderColor = Color(0xFF4285F4),
                    unfocusedBorderColor = Color(0xFFE5E7EB),
                    focusedContainerColor = Color(0xFFF9FAFB),
                    unfocusedContainerColor = Color(0xFFF9FAFB)
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
                                tint = if (isSelected) Color(0xFF4285F4) else Color.Gray,
                                modifier = Modifier.size(18.dp)
                            )
                            Spacer(modifier = Modifier.width(12.dp))
                            Text(
                                text = country,
                                fontSize = 15.sp,
                                fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Normal,
                                color = if (isSelected) Color(0xFF4285F4) else Color.Black
                            )
                        }

                        if (isSelected) {
                            Icon(
                                imageVector = Icons.Default.Check,
                                contentDescription = "Selected",
                                tint = Color(0xFF4285F4),
                                modifier = Modifier.size(18.dp)
                            )
                        }
                    }
                }
            }
        }
    }
}


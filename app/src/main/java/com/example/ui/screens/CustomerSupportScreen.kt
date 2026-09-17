package com.example.ui.screens
import com.example.ui.components.AppToast

import android.content.Context
import android.content.Intent
import android.net.Uri
import android.widget.Toast
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.ArrowForwardIos
import androidx.compose.material.icons.filled.Call
import androidx.compose.material.icons.filled.Email
import androidx.compose.material.icons.filled.Headset
import androidx.compose.material.icons.filled.HelpOutline
import androidx.compose.material.icons.filled.Security
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.ui.components.EmailHelpdesk3DIcon
import com.example.ui.components.FaqHelpCenter3DIcon
import com.example.ui.components.SafetyCenter3DIcon
import com.example.ui.components.SupportHeadset3DIcon
import com.example.ui.components.WhatsApp3DIcon
import com.example.ui.theme.AppTheme

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun CustomerSupportScreen(
    onBackClick: () -> Unit
) {
    val context = LocalContext.current
    val scrollState = rememberScrollState()
    val colors = AppTheme.colors
    val isDark = colors.isDark

    Scaffold(
        topBar = {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .background(
                        if (isDark) {
                            Brush.verticalGradient(
                                listOf(
                                    Color(0xFF09120B),
                                    Color(0xFF0E1A11),
                                    Color(0xFF050A06)
                                )
                            )
                        } else {
                            Brush.verticalGradient(
                                listOf(
                                    Color(0xFF009639), // Deep Emerald
                                    Color(0xFF00B04A), // Jewel Jade
                                    Color(0xFF00C853), // Vivid Emerald
                                    Color(0xFF26E06D)  // Mint Emerald
                                )
                            )
                        }
                    )
                    .statusBarsPadding()
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 8.dp, vertical = 10.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    IconButton(onClick = onBackClick) {
                        Icon(
                            imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                            contentDescription = "Back",
                            tint = Color.White
                        )
                    }
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(
                        text = "Customer Support",
                        fontWeight = FontWeight.Bold,
                        fontSize = 20.sp,
                        color = Color.White
                    )
                }
            }
        },
        bottomBar = {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .background(colors.screenBg)
                    .padding(horizontal = 18.dp, vertical = 16.dp)
            ) {
                Button(
                    onClick = { openWhatsAppSupport(context, "254713934404") },
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(54.dp),
                    colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF00C853)),
                    shape = RoundedCornerShape(16.dp)
                ) {
                    WhatsApp3DIcon(size = 28.dp)
                    Spacer(modifier = Modifier.width(10.dp))
                    Text(
                        text = "Open Live WhatsApp Chat",
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

            // Hero Header Card (Emerald Gradient with 3D Headset)
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
                                colors = listOf(
                                    Color(0xFF00C853),
                                    Color(0xFF00897B)
                                )
                            )
                        )
                        .padding(24.dp),
                    contentAlignment = Alignment.Center
                ) {
                    Column(
                        horizontalAlignment = Alignment.CenterHorizontally
                    ) {
                        // 3D Headset Icon
                        SupportHeadset3DIcon(size = 72.dp)

                        Spacer(modifier = Modifier.height(14.dp))

                        Text(
                            text = "We're Here to Help!",
                            color = Color.White,
                            fontWeight = FontWeight.ExtraBold,
                            fontSize = 22.sp,
                            textAlign = TextAlign.Center
                        )

                        Spacer(modifier = Modifier.height(8.dp))

                        Text(
                            text = "Get 24/7 instant live assistance via WhatsApp or Official Line.",
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
                text = "Contact Channels",
                fontWeight = FontWeight.Bold,
                fontSize = 17.sp,
                color = colors.textPrimary
            )

            Spacer(modifier = Modifier.height(12.dp))

            // Channel 1: Official WhatsApp Support
            SupportChannelCard(
                icon = { WhatsApp3DIcon(size = 46.dp) },
                title = "Official WhatsApp Support",
                subtitle = "+254 713 934 404 (Live 24/7)",
                onClick = { openWhatsAppSupport(context, "254713934404") }
            )

            Spacer(modifier = Modifier.height(10.dp))

            // Channel 2: Email Helpdesk
            SupportChannelCard(
                icon = { EmailHelpdesk3DIcon(size = 46.dp) },
                title = "Email Helpdesk",
                subtitle = "mnene5060@gmail.com",
                onClick = {
                    try {
                        val emailIntent = Intent(Intent.ACTION_SENDTO).apply {
                            data = Uri.parse("mailto:mnene5060@gmail.com")
                        }
                        context.startActivity(emailIntent)
                    } catch (e: Exception) {
                        AppToast.show("mnene5060@gmail.com")
                    }
                }
            )

            Spacer(modifier = Modifier.height(10.dp))

            // Channel 3: Help Center & FAQs
            SupportChannelCard(
                icon = { FaqHelpCenter3DIcon(size = 46.dp) },
                title = "Help Center & FAQs",
                subtitle = "Browse frequently asked questions",
                onClick = {
                    AppToast.show("Opening FAQ Center...")
                }
            )

            Spacer(modifier = Modifier.height(10.dp))

            // Channel 4: Account & Safety Center
            SupportChannelCard(
                icon = { SafetyCenter3DIcon(size = 46.dp) },
                title = "Account & Safety Center",
                subtitle = "Report an issue or account safety query",
                onClick = {
                    AppToast.show("Account & Safety Queries")
                }
            )

            Spacer(modifier = Modifier.height(24.dp))
        }
    }
}

@Composable
private fun SupportChannelCard(
    icon: @Composable () -> Unit,
    title: String,
    subtitle: String,
    onClick: () -> Unit
) {
    val colors = AppTheme.colors
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .clickable { onClick() },
        shape = RoundedCornerShape(20.dp),
        colors = CardDefaults.cardColors(containerColor = colors.cardBg),
        elevation = CardDefaults.cardElevation(defaultElevation = 0.dp)
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp, vertical = 14.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier.weight(1f)
            ) {
                icon()

                Spacer(modifier = Modifier.width(14.dp))

                Column {
                    Text(
                        text = title,
                        fontWeight = FontWeight.Bold,
                        fontSize = 15.sp,
                        color = colors.textPrimary
                    )
                    Spacer(modifier = Modifier.height(2.dp))
                    Text(
                        text = subtitle,
                        fontSize = 13.sp,
                        color = colors.textSecondary
                    )
                }
            }

            Icon(
                imageVector = Icons.AutoMirrored.Filled.ArrowForwardIos,
                contentDescription = "Go",
                tint = colors.textSecondary,
                modifier = Modifier.size(16.dp)
            )
        }
    }
}

fun openWhatsAppSupport(context: Context, phoneNumber: String = "254713934404") {
    if (!com.example.data.NetworkUtils.requireOnline(context)) {
        return
    }
    val cleanNumber = phoneNumber.replace("+", "").replace(" ", "").trim()
    try {
        val intent = Intent(Intent.ACTION_VIEW).apply {
            data = Uri.parse("https://api.whatsapp.com/send?phone=$cleanNumber")
            setPackage("com.whatsapp")
        }
        context.startActivity(intent)
    } catch (e: Exception) {
        try {
            val webIntent = Intent(Intent.ACTION_VIEW, Uri.parse("https://wa.me/$cleanNumber"))
            context.startActivity(webIntent)
        } catch (e2: Exception) {
            AppToast.show("Could not open WhatsApp.")
        }
    }
}

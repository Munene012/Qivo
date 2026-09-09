package com.example.ui.components
import com.example.ui.components.AppToast

import android.widget.Toast
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Key
import androidx.compose.material.icons.filled.NotificationsActive
import androidx.compose.material.icons.filled.Security
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.data.PesapalConfig
import com.example.data.PesapalPaymentService
import com.example.ui.theme.AppTheme
import kotlinx.coroutines.launch

@Composable
fun PesapalSettingsDialog(
    onDismiss: () -> Unit
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val colors = AppTheme.colors
    val isDark = colors.isDark

    val pesapalService = remember { PesapalPaymentService(context) }

    var isLive by remember { mutableStateOf(PesapalConfig.isLive) }
    var consumerKey by remember { mutableStateOf(PesapalConfig.consumerKey) }
    var consumerSecret by remember { mutableStateOf(PesapalConfig.consumerSecret) }
    var ipnUrl by remember { mutableStateOf(PesapalConfig.ipnUrl) }
    var ipnId by remember { mutableStateOf(PesapalConfig.ipnId) }

    var isRegisteringIpn by remember { mutableStateOf(false) }
    var ipnStatusMsg by remember { mutableStateOf("") }
    val scrollState = rememberScrollState()

    AlertDialog(
        onDismissRequest = onDismiss,
        title = {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(
                    imageVector = Icons.Default.Security,
                    contentDescription = null,
                    tint = Color(0xFFFF5722),
                    modifier = Modifier.size(24.dp)
                )
                Spacer(modifier = Modifier.width(8.dp))
                Text(
                    text = "PesaPal Live & IPN Settings",
                    fontSize = 18.sp,
                    fontWeight = FontWeight.Bold,
                    color = colors.textPrimary
                )
            }
        },
        text = {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .verticalScroll(scrollState)
                    .padding(top = 4.dp)
            ) {
                Text(
                    text = "Configure your official PesaPal v3 credentials to process real M-Pesa, Airtel Money, and Card payments in the in-app WebView.",
                    fontSize = 12.sp,
                    color = colors.textSecondary
                )

                Spacer(modifier = Modifier.height(14.dp))

                // Live Mode Toggle
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .background(
                            if (isDark) Color(0xFF1E1E24) else Color(0xFFF5F5F5),
                            shape = RoundedCornerShape(12.dp)
                        )
                        .padding(horizontal = 12.dp, vertical = 8.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Column {
                        Text(
                            text = if (isLive) "PesaPal Live Mode (Active)" else "Sandbox / Test Mode",
                            fontSize = 13.sp,
                            fontWeight = FontWeight.Bold,
                            color = if (isLive) Color(0xFFFF5722) else colors.textPrimary
                        )
                        Text(
                            text = if (isLive) "pay.pesapal.com/v3" else "cybqa.pesapal.com/pesapalv3",
                            fontSize = 11.sp,
                            color = colors.textSecondary
                        )
                    }

                    Switch(
                        checked = isLive,
                        onCheckedChange = { isLive = it },
                        colors = SwitchDefaults.colors(
                            checkedThumbColor = Color.White,
                            checkedTrackColor = Color(0xFFFF5722)
                        )
                    )
                }

                Spacer(modifier = Modifier.height(14.dp))

                // Consumer Key
                OutlinedTextField(
                    value = consumerKey,
                    onValueChange = { consumerKey = it },
                    label = { Text("Consumer Key") },
                    placeholder = { Text("Enter your PesaPal Consumer Key") },
                    singleLine = true,
                    modifier = Modifier
                        .fillMaxWidth()
                        .testTag("input_pesapal_key"),
                    shape = RoundedCornerShape(12.dp),
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedBorderColor = Color(0xFFFF5722),
                        unfocusedBorderColor = colors.divider
                    )
                )

                Spacer(modifier = Modifier.height(10.dp))

                // Consumer Secret
                OutlinedTextField(
                    value = consumerSecret,
                    onValueChange = { consumerSecret = it },
                    label = { Text("Consumer Secret") },
                    placeholder = { Text("Enter your PesaPal Consumer Secret") },
                    singleLine = true,
                    modifier = Modifier
                        .fillMaxWidth()
                        .testTag("input_pesapal_secret"),
                    shape = RoundedCornerShape(12.dp),
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedBorderColor = Color(0xFFFF5722),
                        unfocusedBorderColor = colors.divider
                    )
                )

                Spacer(modifier = Modifier.height(10.dp))

                // IPN Notification URL
                OutlinedTextField(
                    value = ipnUrl,
                    onValueChange = { ipnUrl = it },
                    label = { Text("IPN Webhook URL") },
                    placeholder = { Text("https://qivo.app/api/pesapal/ipn") },
                    singleLine = true,
                    modifier = Modifier
                        .fillMaxWidth()
                        .testTag("input_pesapal_ipn_url"),
                    shape = RoundedCornerShape(12.dp),
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedBorderColor = Color(0xFFFF5722),
                        unfocusedBorderColor = colors.divider
                    )
                )

                Spacer(modifier = Modifier.height(10.dp))

                // Registered IPN ID
                OutlinedTextField(
                    value = ipnId,
                    onValueChange = { ipnId = it },
                    label = { Text("Registered IPN ID (notification_id)") },
                    placeholder = { Text("Auto-filled when registering IPN") },
                    singleLine = true,
                    modifier = Modifier
                        .fillMaxWidth()
                        .testTag("input_pesapal_ipn_id"),
                    shape = RoundedCornerShape(12.dp),
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedBorderColor = Color(0xFFFF5722),
                        unfocusedBorderColor = colors.divider
                    )
                )

                Spacer(modifier = Modifier.height(12.dp))

                // Action: Register IPN button
                Button(
                    onClick = {
                        if (consumerKey.isBlank() || consumerSecret.isBlank()) {
                            AppToast.show("Please enter Consumer Key and Secret first")
                            return@Button
                        }
                        isRegisteringIpn = true
                        ipnStatusMsg = ""
                        // Temporarily update config so service uses new values
                        PesapalConfig.consumerKey = consumerKey.trim()
                        PesapalConfig.consumerSecret = consumerSecret.trim()
                        PesapalConfig.isLive = isLive
                        PesapalConfig.ipnUrl = ipnUrl.trim().ifEmpty { PesapalConfig.DEFAULT_IPN_URL }

                        scope.launch {
                            val res = pesapalService.registerIpn(PesapalConfig.ipnUrl)
                            if (res.isSuccess) {
                                val registeredId = res.getOrThrow()
                                ipnId = registeredId
                                ipnStatusMsg = "✓ IPN registered successfully: $registeredId"
                                PesapalConfig.saveRegisteredIpnId(context, registeredId, PesapalConfig.ipnUrl)
                                AppToast.show("IPN Registered Successfully!")
                            } else {
                                val err = res.exceptionOrNull()?.message ?: "Failed to register IPN"
                                ipnStatusMsg = "Error: $err"
                                com.example.data.NetworkUtils.showToast(context, err, true)
                            }
                            isRegisteringIpn = false
                        }
                    },
                    enabled = !isRegisteringIpn,
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(44.dp)
                        .testTag("btn_register_ipn"),
                    shape = RoundedCornerShape(12.dp),
                    colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF00C853))
                ) {
                    if (isRegisteringIpn) {
                        CircularProgressIndicator(
                            color = Color.White,
                            modifier = Modifier.size(18.dp),
                            strokeWidth = 2.dp
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                        Text("Registering IPN with PesaPal...", fontSize = 13.sp, color = Color.White)
                    } else {
                        Icon(
                            imageVector = Icons.Default.NotificationsActive,
                            contentDescription = null,
                            modifier = Modifier.size(16.dp),
                            tint = Color.White
                        )
                        Spacer(modifier = Modifier.width(6.dp))
                        Text("Register IPN ID Now", fontWeight = FontWeight.Bold, fontSize = 13.sp, color = Color.White)
                    }
                }

                if (ipnStatusMsg.isNotBlank()) {
                    Spacer(modifier = Modifier.height(8.dp))
                    Text(
                        text = ipnStatusMsg,
                        fontSize = 11.sp,
                        color = if (ipnStatusMsg.startsWith("✓")) Color(0xFF00C853) else Color(0xFFFF5252)
                    )
                }
            }
        },
        confirmButton = {
            Button(
                onClick = {
                    PesapalConfig.saveConfig(
                        context = context,
                        key = consumerKey,
                        secret = consumerSecret,
                        live = isLive,
                        customIpnUrl = ipnUrl,
                        customIpnId = ipnId
                    )
                    AppToast.show("PesaPal configuration saved!")
                    onDismiss()
                },
                colors = ButtonDefaults.buttonColors(containerColor = Color(0xFFFF5722)),
                shape = RoundedCornerShape(12.dp),
                modifier = Modifier.testTag("btn_save_pesapal_config")
            ) {
                Text("Save", fontWeight = FontWeight.Bold)
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text("Cancel", color = colors.textSecondary)
            }
        },
        containerColor = colors.cardBg,
        shape = RoundedCornerShape(20.dp)
    )
}

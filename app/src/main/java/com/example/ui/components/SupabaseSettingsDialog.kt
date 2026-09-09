package com.example.ui.components

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.data.SupabaseConfig
import com.example.ui.theme.QivoDarkCharcoal
import com.example.ui.theme.QivoOrange

@Composable
fun SupabaseSettingsDialog(
    onDismiss: () -> Unit
) {
    val context = LocalContext.current
    var url by remember { mutableStateOf(SupabaseConfig.supabaseUrl) }
    var apiKey by remember { mutableStateOf(SupabaseConfig.supabaseApiKey) }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = {
            Text(
                text = "Supabase Auth Settings",
                fontSize = 18.sp,
                fontWeight = FontWeight.Bold
            )
        },
        text = {
            Column(modifier = Modifier.padding(top = 8.dp)) {
                Text(
                    text = "Configure your Supabase Project URL and Anon API Key for authenticated access.",
                    fontSize = 13.sp,
                    color = androidx.compose.ui.graphics.Color.Gray
                )
                Spacer(modifier = Modifier.height(16.dp))

                OutlinedTextField(
                    value = url,
                    onValueChange = { url = it },
                    label = { Text("Supabase Project URL") },
                    placeholder = { Text("https://your-project.supabase.co") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )

                Spacer(modifier = Modifier.height(12.dp))

                OutlinedTextField(
                    value = apiKey,
                    onValueChange = { apiKey = it },
                    label = { Text("Supabase Anon Key") },
                    placeholder = { Text("eyJhbGciOiJIUzI1NiI...") },
                    modifier = Modifier.fillMaxWidth()
                )
            }
        },
        confirmButton = {
            Button(
                onClick = {
                    SupabaseConfig.save(
                        context = context,
                        url = url,
                        apiKey = apiKey
                    )
                    onDismiss()
                },
                colors = ButtonDefaults.buttonColors(containerColor = QivoOrange),
                shape = RoundedCornerShape(20.dp)
            ) {
                Text("Save Configuration")
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text("Cancel", color = QivoDarkCharcoal)
            }
        },
        shape = RoundedCornerShape(24.dp)
    )
}

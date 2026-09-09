package com.example.ui.components

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.offset
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.ui.theme.PacificoFontFamily

/**
 * Stylish, bubbly Pacifico cursive "Qivo" background stamp.
 * Rendered behind UI elements in the top colored banner of Home, Party Room, and Chat List.
 * Tilted at -8 degrees with artistic flowing cursive curves and soft opacity.
 * Fixed in exact position, size, rotation, and alignment across all screens.
 */
@Composable
fun QivoBackgroundStamp(
    modifier: Modifier = Modifier,
    isDark: Boolean = false
) {
    Box(
        modifier = modifier.fillMaxSize(),
        contentAlignment = Alignment.Center
    ) {
        Text(
            text = "Qivo",
            fontSize = 76.sp,
            fontFamily = PacificoFontFamily,
            color = if (isDark) Color.White.copy(alpha = 0.12f) else Color.White.copy(alpha = 0.22f),
            modifier = Modifier
                .offset(x = 14.dp, y = (-4).dp)
                .rotate(-8f)
        )
    }
}

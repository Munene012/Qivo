package com.example.ui.theme

import android.content.Context
import android.content.SharedPreferences
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.ReadOnlyComposable
import androidx.compose.runtime.compositionLocalOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.ui.graphics.Color

object AppThemeManager {
    private const val PREFS_NAME = "qivo_theme_prefs"
    private const val KEY_DARK_MODE = "key_dark_mode_enabled"

    val isDarkModeState = mutableStateOf(false)

    fun init(context: Context) {
        val prefs = getPrefs(context)
        isDarkModeState.value = prefs.getBoolean(KEY_DARK_MODE, false)
    }

    fun setDarkMode(context: Context, enabled: Boolean) {
        isDarkModeState.value = enabled
        getPrefs(context).edit().putBoolean(KEY_DARK_MODE, enabled).apply()
    }

    fun isDarkMode(context: Context): Boolean {
        return getPrefs(context).getBoolean(KEY_DARK_MODE, false)
    }

    private fun getPrefs(context: Context): SharedPreferences {
        return context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
    }
}

data class AppColors(
    val isDark: Boolean,
    val screenBg: Color,
    val surfaceBg: Color,
    val cardBg: Color,
    val cardBgElevated: Color,
    val cardBorder: Color,
    val cardGoldBorder: Color,
    val textPrimary: Color,
    val textSecondary: Color,
    val textMuted: Color,
    val divider: Color,
    val inputBg: Color,
    val bottomNavBg: Color,
    val bottomNavBorder: Color,
    val topBarBg: Color,
    val accentGold: Color = QivoGold,
    val accentGoldLight: Color = QivoGoldLight,
    val accentGoldGlow: Color = QivoGoldGlow,
    val accentAmber: Color = QivoOrange,
    val accentOrange: Color = QivoOrange,
    val accentCoral: Color = QivoSunsetCoral,
    val accentGreen: Color = QivoOrange,
    val accentNeon: Color = QivoNeon
)

val LocalAppColors = compositionLocalOf {
    lightAppColors()
}

fun lightAppColors() = AppColors(
    isDark = false,
    screenBg = Color(0xFFF7FAF8), // Crisp clean canvas with subtle jade undertone
    surfaceBg = Color.White,
    cardBg = Color.White,
    cardBgElevated = Color(0xFFF0F6F2),
    cardBorder = Color(0xFFE0ECE4),
    cardGoldBorder = Color(0xFFA5D6A7),
    textPrimary = Color(0xFF111813),
    textSecondary = Color(0xFF4D5E53),
    textMuted = Color(0xFF7D8F83),
    divider = Color(0xFFE8F0EB),
    inputBg = Color(0xFFEFF5F1),
    bottomNavBg = Color.White,
    bottomNavBorder = Color(0xFFE0ECE4),
    topBarBg = Color(0xFFF7FAF8)
)

fun darkAppColors() = AppColors(
    isDark = true,
    screenBg = Color(0xFF0B100D),         // Deep rich onyx canvas
    surfaceBg = Color(0xFF101913),        // Deep jade surface
    cardBg = Color(0xFF152219),           // Refined dark jade card
    cardBgElevated = Color(0xFF1B2D21),   // Elevated card container
    cardBorder = Color(0xFF233A2B),       // Emerald rim border
    cardGoldBorder = Color(0xFF1F4A2E),   // Rich emerald accent border
    textPrimary = Color(0xFFFFFFFF),      // Pure crisp white
    textSecondary = Color(0xFFA2B5A8),    // Clean legible muted jade grey
    textMuted = Color(0xFF6B8072),        // Muted text
    divider = Color(0xFF1B2B20),          // Dark divider
    inputBg = Color(0xFF132017),          // Dark input field background
    bottomNavBg = Color(0xFF0B100D),      // Obsidian bottom navigation bar
    bottomNavBorder = Color(0xFF1B2B20),
    topBarBg = Color(0xFF0B100D)          // Obsidian top header bar
)

object AppTheme {
    val colors: AppColors
        @Composable
        @ReadOnlyComposable
        get() = LocalAppColors.current
}

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
    val accentGreen: Color = Color(0xFF00C853),
    val accentNeon: Color = Color(0xFFB3FF00)
)

val LocalAppColors = compositionLocalOf {
    lightAppColors()
}

fun lightAppColors() = AppColors(
    isDark = false,
    screenBg = Color.White,
    surfaceBg = Color.White,
    cardBg = Color.White,
    cardBgElevated = Color(0xFFFFFBF7),
    cardBorder = Color(0xFFEBEBF0),
    cardGoldBorder = Color(0xFFFFE0B2),
    textPrimary = Color(0xFF111827),
    textSecondary = Color(0xFF6B7280),
    textMuted = Color(0xFF9CA3AF),
    divider = Color(0xFFF3F4F6),
    inputBg = Color(0xFFF3F4F6),
    bottomNavBg = Color.White,
    bottomNavBorder = Color(0xFFEBEBF0),
    topBarBg = Color.White
)

fun darkAppColors() = AppColors(
    isDark = true,
    screenBg = Color(0xFF0A0A0E),         // Deep obsidian canvas
    surfaceBg = Color(0xFF101015),        // Deep luxury surface
    cardBg = Color(0xFF16161D),           // Sleek obsidian card container
    cardBgElevated = Color(0xFF1E1E28),   // Elevated obsidian surface
    cardBorder = Color(0xFF282834),       // Subtle dark metallic border
    cardGoldBorder = Color(0xFF4A3E26),   // Subtle warm gold border
    textPrimary = Color(0xFFFFFFFF),      // Crisp white
    textSecondary = Color(0xFFA1A1AA),    // High-legibility neutral grey
    textMuted = Color(0xFF71717A),        // Muted grey
    divider = Color(0xFF20202A),          // Dark divider
    inputBg = Color(0xFF1A1A22),          // Dark input field background
    bottomNavBg = Color(0xFF0B0B0F),      // Pitch black bottom navigation bar
    bottomNavBorder = Color(0xFF1E1E26),
    topBarBg = Color(0xFF0A0A0E)          // Pitch black top header bar
)

object AppTheme {
    val colors: AppColors
        @Composable
        @ReadOnlyComposable
        get() = LocalAppColors.current
}

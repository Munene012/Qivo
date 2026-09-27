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
    screenBg = Color(0xFFF9F9FB), // Crisp clean pearl canvas
    surfaceBg = Color.White,
    cardBg = Color.White,
    cardBgElevated = Color(0xFFF4F4F6),
    cardBorder = Color(0xFFE5E5EA),
    cardGoldBorder = Color(0xFFFFD54F),
    textPrimary = Color(0xFF141318),
    textSecondary = Color(0xFF5A5A60),
    textMuted = Color(0xFF8E8E93),
    divider = Color(0xFFEFEFF4),
    inputBg = Color(0xFFF2F2F7),
    bottomNavBg = Color.White,
    bottomNavBorder = Color(0xFFE5E5EA),
    topBarBg = Color(0xFFF9F9FB)
)

fun darkAppColors() = AppColors(
    isDark = true,
    screenBg = Color(0xFF0C0A12),         // Rich dark onyx canvas with subtle violet tint
    surfaceBg = Color(0xFF13101C),        // Obsidian surface
    cardBg = Color(0xFF1A1626),           // Refined dark obsidian card
    cardBgElevated = Color(0xFF231D33),   // Elevated card container
    cardBorder = Color(0xFF322A45),       // Elegant rim border
    cardGoldBorder = Color(0xFF4A3525),   // Warm Gold accent border
    textPrimary = Color(0xFFFFFFFF),      // Pure crisp white
    textSecondary = Color(0xFFB0ACC0),    // Clean legible muted grey-violet
    textMuted = Color(0xFF7A748B),        // Muted text
    divider = Color(0xFF231D33),          // Dark divider
    inputBg = Color(0xFF161321),          // Dark input field background
    bottomNavBg = Color(0xFF0C0A12),      // Obsidian bottom navigation bar
    bottomNavBorder = Color(0xFF231D33),
    topBarBg = Color(0xFF0C0A12)          // Obsidian top header bar
)

object AppTheme {
    val colors: AppColors
        @Composable
        @ReadOnlyComposable
        get() = LocalAppColors.current
}

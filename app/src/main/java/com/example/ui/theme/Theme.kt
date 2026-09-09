package com.example.ui.theme

import android.app.Activity
import android.os.Build
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.LocalTextStyle
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.dynamicDarkColorScheme
import androidx.compose.material3.dynamicLightColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.SideEffect
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.text.TextStyle
import androidx.core.view.WindowCompat

private val DarkColorScheme =
  darkColorScheme(
    primary = QivoOrange,
    secondary = QivoYellow,
    tertiary = Pink80,
    background = Color(0xFF000000),
    surface = Color(0xFF0D0D0D),
    onPrimary = Color.White,
    onSecondary = Color.Black,
    onTertiary = Color.White,
    onBackground = Color.White,
    onSurface = Color.White
  )

private val LightColorScheme =
  lightColorScheme(
    primary = QivoOrange,
    secondary = PurpleGrey40,
    tertiary = Pink40,
    background = Color(0xFFFAFAFA),
    surface = Color.White,
    onPrimary = Color.White,
    onSecondary = Color.White,
    onTertiary = Color.White,
    onBackground = Color(0xFF111827),
    onSurface = Color(0xFF111827)
  )

private fun android.content.Context.findActivity(): Activity? {
  var currentContext = this
  while (currentContext is android.content.ContextWrapper) {
    if (currentContext is Activity) {
      return currentContext
    }
    currentContext = currentContext.baseContext
  }
  return null
}

@Composable
fun MyApplicationTheme(
  darkTheme: Boolean = AppThemeManager.isDarkModeState.value,
  // Dynamic color is available on Android 12+
  dynamicColor: Boolean = false,
  content: @Composable () -> Unit,
) {
  val colorScheme = if (darkTheme) DarkColorScheme else LightColorScheme
  val appColors = if (darkTheme) darkAppColors() else lightAppColors()

  val view = LocalView.current
  if (!view.isInEditMode) {
    SideEffect {
      try {
        val window = view.context.findActivity()?.window
        if (window != null) {
          WindowCompat.getInsetsController(window, view).apply {
            isAppearanceLightStatusBars = !darkTheme
            isAppearanceLightNavigationBars = !darkTheme
          }
        }
      } catch (_: Throwable) {}
    }
  }

  CompositionLocalProvider(
    LocalAppColors provides appColors,
    LocalTextStyle provides TextStyle(fontFamily = AppFontFamily)
  ) {
    MaterialTheme(colorScheme = colorScheme, typography = Typography, content = content)
  }
}


package com.example

import android.content.Context
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.test.core.app.ApplicationProvider
import com.example.data.SupabaseConfig
import com.example.data.UserSessionManager
import com.example.ui.screens.MainBottomNavScaffold
import com.example.ui.screens.SplashScreen
import com.example.ui.screens.WelcomeScreen
import com.example.ui.theme.MyApplicationTheme
import org.junit.Assert.assertEquals
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

import org.robolectric.Robolectric
import androidx.compose.ui.test.onNodeWithTag

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class ExampleRobolectricTest {

  @get:Rule
  val composeTestRule = createComposeRule()

  @Before
  fun setup() {
    val context = ApplicationProvider.getApplicationContext<Context>()
    SupabaseConfig.init(context)
    UserSessionManager.init(context)
  }

  @Test
  fun `read string from context`() {
    val context = ApplicationProvider.getApplicationContext<Context>()
    val appName = context.getString(R.string.app_name)
    assertEquals("QIVO", appName)
  }

  @Test
  fun `test splash screen rendering`() {
    composeTestRule.setContent {
      MyApplicationTheme {
        SplashScreen(onSplashFinished = {})
      }
    }
    composeTestRule.onNodeWithTag("splash_screen_root").assertExists()
  }

  @Test
  fun `test welcome screen rendering`() {
    composeTestRule.mainClock.autoAdvance = false
    composeTestRule.setContent {
      MyApplicationTheme {
        WelcomeScreen(
          onNavigateToEmail = {},
          onGoogleSignInClick = {}
        )
      }
    }
    composeTestRule.mainClock.advanceTimeBy(100)
    composeTestRule.onNodeWithTag("welcome_screen_root").assertExists()
    composeTestRule.mainClock.autoAdvance = true
  }

  @Test
  fun `test main bottom nav scaffold rendering`() {
    composeTestRule.mainClock.autoAdvance = false
    composeTestRule.setContent {
      MyApplicationTheme {
        MainBottomNavScaffold(
          userEmail = "test@example.com",
          userId = "user_123",
          userName = "Test User",
          userGender = "Male",
          userCountry = "United States",
          onSignOut = {}
        )
      }
    }
    composeTestRule.mainClock.advanceTimeBy(100)
    composeTestRule.mainClock.autoAdvance = true
  }

  @Test
  fun `test QivoApp transition from splash to welcome`() {
    composeTestRule.mainClock.autoAdvance = false
    composeTestRule.setContent {
      MyApplicationTheme {
        QivoApp()
      }
    }
    composeTestRule.mainClock.advanceTimeBy(100)
    composeTestRule.onNodeWithTag("splash_screen_root").assertExists()
    composeTestRule.mainClock.advanceTimeBy(2000)
    composeTestRule.onNodeWithTag("welcome_screen_root").assertExists()
    composeTestRule.mainClock.autoAdvance = true
  }

  @Test
  fun `test MainActivity lifecycle`() {
    val controller = Robolectric.buildActivity(MainActivity::class.java).setup()
    val activity = controller.get()
    org.junit.Assert.assertNotNull(activity)
  }
}


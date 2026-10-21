package com.example.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.ScrollState
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.material3.MaterialTheme
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.onRoot
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import androidx.compose.ui.unit.Density
import androidx.test.core.app.ApplicationProvider
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.ViewModelStore
import androidx.lifecycle.ViewModelStoreOwner
import android.app.Application
import android.content.Context
import com.example.data.PlexSettingsManager
import com.example.ui.theme.MyApplicationTheme
import com.github.takahirom.roborazzi.captureRoboImage
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.After
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
class SettingsAppearanceVisualTest {
    @get:Rule
    val composeRule = createComposeRule()

    private lateinit var context: Context
    private lateinit var settings: PlexSettingsManager
    private var originalTheme: String = "Default Dark"
    private var viewModelStore: ViewModelStore? = null

    @Before
    fun setUp() {
        context = ApplicationProvider.getApplicationContext()
        settings = PlexSettingsManager(context)
        originalTheme = settings.activeTheme
        settings.apply {
            clearCompanionCredential()
            baseUrl = ""
            token = ""
            offlineOnly = false
            sectionId = ""
            activeTheme = "Warm Light"
        }
    }

    @After
    fun tearDown() {
        viewModelStore?.clear()
        viewModelStore = null
        settings.activeTheme = originalTheme
    }

    @Test
    @Config(qualifiers = "w320dp-h640dp", sdk = [36])
    fun warmLightSettingsAtDefaultTextRemainReadableAndReachable() = captureWarmLightSettings(
        fontScale = 1f,
        overviewScreenshot = "src/test/screenshots/settings-warm-light-compact-100.png",
        playbackScreenshot = "src/test/screenshots/settings-warm-light-playback-100.png",
        discoveryScreenshot = "src/test/screenshots/settings-warm-light-discovery-100.png",
        appearanceScreenshot = "src/test/screenshots/settings-warm-light-appearance-100.png"
    )

    @Test
    @Config(qualifiers = "w320dp-h640dp", sdk = [36])
    fun warmLightSettingsAtLargeTextRemainReadableAndReachable() = captureWarmLightSettings(
        fontScale = 1.3f,
        overviewScreenshot = "src/test/screenshots/settings-warm-light-compact-130.png",
        playbackScreenshot = "src/test/screenshots/settings-warm-light-playback-130.png",
        discoveryScreenshot = "src/test/screenshots/settings-warm-light-discovery-130.png",
        appearanceScreenshot = "src/test/screenshots/settings-warm-light-appearance-130.png"
    )

    private fun captureWarmLightSettings(
        fontScale: Float,
        overviewScreenshot: String,
        playbackScreenshot: String,
        discoveryScreenshot: String,
        appearanceScreenshot: String
    ) {
        val viewModel = createSettingsViewModel()
        viewModel.updateTheme("Warm Light")
        assertEquals("Warm Light", viewModel.activeThemeFlow.value)
        lateinit var settingsScrollState: ScrollState
        composeRule.setContent {
            val currentDensity = LocalDensity.current
            val scrollState = rememberScrollState()
            settingsScrollState = scrollState
            CompositionLocalProvider(
                LocalDensity provides Density(currentDensity.density, fontScale)
            ) {
                MyApplicationTheme(activeTheme = "Warm Light") {
                    Box(
                        modifier = Modifier
                            .fillMaxSize()
                            .background(MaterialTheme.colorScheme.background)
                    ) {
                        SettingsScreen(viewModel = viewModel, settingsScrollState = scrollState)
                    }
                }
            }
        }

        composeRule.onNodeWithTag("settings_heading_connection").assertIsDisplayed()
        composeRule.onRoot().captureRoboImage(filePath = overviewScreenshot)

        composeRule.onNodeWithTag("settings_section_playback").performClick()
        composeRule.waitForIdle()
        composeRule.onNodeWithTag("settings_heading_playback").assertIsDisplayed()
        composeRule.onRoot().captureRoboImage(filePath = playbackScreenshot)

        runBlocking { settingsScrollState.scrollTo(0) }
        composeRule.waitForIdle()
        composeRule.onNodeWithTag("settings_section_discovery").performClick()
        composeRule.waitForIdle()
        composeRule.onNodeWithTag("settings_heading_discovery").assertIsDisplayed()
        composeRule.onRoot().captureRoboImage(filePath = discoveryScreenshot)

        runBlocking { settingsScrollState.scrollTo(0) }
        composeRule.waitForIdle()
        composeRule.onNodeWithTag("settings_section_appearance").performClick()
        composeRule.waitForIdle()
        composeRule.onNodeWithTag("settings_heading_appearance").assertIsDisplayed()
        composeRule.onRoot().captureRoboImage(filePath = appearanceScreenshot)

        runBlocking { settingsScrollState.scrollTo(0) }
        composeRule.waitForIdle()
        composeRule.onNodeWithTag("settings_section_advanced").performClick()
        composeRule.waitForIdle()
        composeRule.onNodeWithTag("settings_heading_advanced").assertIsDisplayed()
        composeRule.onNodeWithTag("settings_library_tools_disclosure").performClick()
        composeRule.waitForIdle()
        composeRule.onNodeWithText(context.getString(com.example.R.string.settings_library_review_title))
            .performScrollTo()
            .assertIsDisplayed()
        composeRule.waitForIdle()
        composeRule.onRoot().captureRoboImage(filePath = appearanceScreenshot.replace("appearance", "advanced"))
    }

    private fun createSettingsViewModel(): MusicViewModel {
        val application = context.applicationContext as Application
        val isolatedStore = ViewModelStore()
        viewModelStore = isolatedStore
        val owner = object : ViewModelStoreOwner {
            override val viewModelStore: ViewModelStore = isolatedStore
        }
        return ViewModelProvider(
            owner,
            ViewModelProvider.AndroidViewModelFactory.getInstance(application)
        )[MusicViewModel::class.java]
    }
}

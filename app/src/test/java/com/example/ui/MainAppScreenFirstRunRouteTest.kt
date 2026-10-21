package com.example.ui

import com.example.R
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class MainAppScreenFirstRunRouteTest {
    @Test
    fun restoredFirstRunSetupReturnsToSettingsFromHome() {
        assertEquals(
            Screen.Settings,
            firstRunSetupRoute(Screen.Home, showingFirstRunSetup = true)
        )
    }

    @Test
    fun restoredFirstRunSetupDoesNotDuplicateSettingsRoute() {
        assertEquals(
            Screen.Settings,
            firstRunSetupRoute(Screen.Settings, showingFirstRunSetup = true)
        )
    }

    @Test
    fun completedFirstRunDoesNotRedirectTheCurrentScreen() {
        assertEquals(
            Screen.Home,
            firstRunSetupRoute(Screen.Home, showingFirstRunSetup = false)
        )
    }

    @Test
    fun searchConnectionSettingsRoutePreservesTheQueryForBackNavigation() {
        val navigationStack = mutableListOf<Screen>(Screen.Home, Screen.Search())

        navigateSearchToConnectionSettings(navigationStack, "Björk")

        assertEquals(
            listOf(Screen.Home, Screen.Search("Björk"), Screen.Settings),
            navigationStack
        )
    }

    @Test
    fun bottomNavigationUsesCompactLabelsForLargeTextWithoutRemovingNormalLabels() {
        assertTrue(alwaysShowBottomNavigationLabels(fontScale = 1.0f))
        assertTrue(alwaysShowBottomNavigationLabels(fontScale = 1.19f))
        assertFalse(alwaysShowBottomNavigationLabels(fontScale = 1.2f))
        assertFalse(alwaysShowBottomNavigationLabels(fontScale = 1.3f))
        assertEquals(R.string.nav_downloads, bottomNavigationDownloadsLabelResource(fontScale = 1.0f))
        assertEquals(R.string.nav_downloads, bottomNavigationDownloadsLabelResource(fontScale = 1.19f))
        assertEquals(R.string.nav_downloads_compact, bottomNavigationDownloadsLabelResource(fontScale = 1.2f))
        assertEquals(R.string.nav_downloads_compact, bottomNavigationDownloadsLabelResource(fontScale = 1.3f))
    }
}

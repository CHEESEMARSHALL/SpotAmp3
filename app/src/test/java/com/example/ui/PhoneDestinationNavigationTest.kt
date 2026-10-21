package com.example.ui

import org.junit.Assert.assertEquals
import org.junit.Test

class PhoneDestinationNavigationTest {
    @Test
    fun searchAndItsResultsBelongToDiscover() {
        assertEquals(PhoneDestination.DISCOVER, phoneDestinationForScreen(Screen.Discover))
        assertEquals(PhoneDestination.DISCOVER, phoneDestinationForScreen(Screen.Search("M83")))
        assertEquals(PhoneDestination.DISCOVER, phoneDestinationForScreen(Screen.SearchResults("albums", emptyList())))
        assertEquals(
            PhoneDestination.DISCOVER,
            selectedPhoneDestination(listOf(Screen.Discover, Screen.Search("M83"), Screen.SearchResults("albums", emptyList())))
        )
    }

    @Test
    fun detailReturnRoutesKeepThePreviousTopLevelDestinationSelected() {
        assertEquals(
            PhoneDestination.LIBRARY,
            selectedPhoneDestination(listOf(Screen.Library, Screen.ArtistDetail("artist-1", "Artist")))
        )
        assertEquals(
            PhoneDestination.DISCOVER,
            selectedPhoneDestination(listOf(Screen.Discover, Screen.Search(), Screen.AlbumDetail("album-1", "Album")))
        )
        assertEquals(
            PhoneDestination.DOWNLOADS,
            selectedPhoneDestination(listOf(Screen.Downloads, Screen.DownloadedAlbumDetail("Artist", "Album")))
        )
    }

    @Test
    fun compactDownloadsNavigationLabelRemainsReadable() {
        assertEquals(com.example.R.string.nav_downloads, bottomNavigationDownloadsLabelResource(1.0f))
        assertEquals(com.example.R.string.nav_downloads_compact, bottomNavigationDownloadsLabelResource(1.3f))
    }
}

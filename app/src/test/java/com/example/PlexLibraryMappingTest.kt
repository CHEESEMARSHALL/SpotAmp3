package com.example

import com.example.data.PlexDirectory
import com.example.data.musicLibraries
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class PlexLibraryMappingTest {
    @Test
    fun keepsMusicSectionsAndExcludesNonMusicSections() {
        val sections = listOf(
            PlexDirectory("1", "Movies", "movie"),
            PlexDirectory("2", "Music", "artist"),
            PlexDirectory("3", "Concert Audio", "MUSIC"),
            PlexDirectory("4", "TV", "show")
        )

        val result = sections.musicLibraries()

        assertEquals(listOf("2", "3"), result.map { it.key })
        assertTrue(result.all { it.type.equals("artist", true) || it.type.equals("music", true) })
    }
}

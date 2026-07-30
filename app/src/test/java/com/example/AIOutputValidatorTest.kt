package com.example

import com.example.data.AIOutputValidator
import com.example.data.AppCommand
import com.example.data.PlaylistIntent
import com.example.data.SmartSearchIntent
import com.example.data.PlaybackCommandExecutor
import org.junit.Assert.assertFalse
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class AIOutputValidatorTest {
    @Test
    fun `rejects invalid playlist intent`() {
        assertFalse(AIOutputValidator.playlistIntent(PlaylistIntent(targetTrackCount = 0)).isValid)
    }

    @Test
    fun `accepts supported app command`() {
        assertTrue(AIOutputValidator.appCommand(AppCommand("START_RADIO")).isValid)
    }

    @Test
    fun `normalizes safe app command actions before execution`() {
        val result = AIOutputValidator.appCommand(AppCommand("  next ", "  queue  "))
        assertTrue(result.isValid)
        assertEquals("NEXT", result.value?.action)
        assertEquals("queue", result.value?.query)
    }

    @Test
    fun `rejects malformed smart search constraints`() {
        assertFalse(AIOutputValidator.smartSearch(SmartSearchIntent(decade = 2005)).isValid)
        assertFalse(AIOutputValidator.smartSearch(SmartSearchIntent(minPlayCount = 1_000_001)).isValid)
    }

    @Test
    fun `rejects oversized and blank playlist intent lists`() {
        assertFalse(AIOutputValidator.playlistIntent(PlaylistIntent(seedArtists = List(51) { "Artist" })).isValid)
        assertFalse(AIOutputValidator.playlistIntent(PlaylistIntent(genres = listOf(""))).isValid)
    }

    @Test
    fun `playback command executor routes only supported actions`() {
        val calls = mutableListOf<String>()
        val executor = PlaybackCommandExecutor(
            onPlay = { calls += "play" },
            onPause = { calls += "pause" },
            onNext = { calls += "next" },
            onPrevious = { calls += "previous" },
            onSearch = { calls += "search:$it" },
            onStartRadio = { calls += "radio:${it.orEmpty()}" }
        )

        assertTrue(executor.execute(AppCommand(" next ")))
        assertTrue(executor.execute(AppCommand("SEARCH", "  jazz  ")))
        assertFalse(executor.execute(AppCommand("SEARCH")))
        assertFalse(executor.execute(AppCommand("ADD_TO_QUEUE", "unknown")))
        assertEquals(listOf("next", "search:jazz"), calls)
    }
}

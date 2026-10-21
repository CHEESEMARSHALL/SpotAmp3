package com.example.playback

import org.junit.Assert.assertEquals
import org.junit.Test

class LyricsModelsTest {
    @Test
    fun `active lyric follows forward and backward playback seeks`() {
        val lyrics = Lyrics(
            lines = listOf(
                LyricLine(0L, "first"),
                LyricLine(5_000L, "second"),
                LyricLine(10_000L, "third")
            ),
            isSynced = true
        )

        assertEquals(-1, lyrics.activeLineIndex(-1L))
        assertEquals(0, lyrics.activeLineIndex(2_000L))
        assertEquals(2, lyrics.activeLineIndex(12_000L))
        assertEquals(0, lyrics.activeLineIndex(2_000L))
        assertEquals(0, lyrics.activeLineIndex(0L))
        assertEquals(-1, Lyrics(listOf(LyricLine(null, "plain")), isSynced = false).activeLineIndex(10_000L))
    }
}
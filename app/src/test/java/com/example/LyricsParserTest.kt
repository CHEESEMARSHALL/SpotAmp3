package com.example

import com.example.playback.LyricsParser
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class LyricsParserTest {
    @Test
    fun plainTextLyrics_dropBlankLinesAndRemainUnsynced() {
        val lyrics = LyricsParser.parse(
            """
            First line

            Second line
            """.trimIndent(),
            isLrc = false
        )

        assertFalse(lyrics.isSynced)
        assertEquals(listOf("First line", "Second line"), lyrics.lines.map { it.text })
        assertTrue(lyrics.lines.all { it.timestampMs == null })
    }

    @Test
    fun lrcLyrics_expandMultipleTimestampsAndSortChronologically() {
        val lyrics = LyricsParser.parse(
            """
            [00:04.20][00:02.5]Second
            [00:01.00]First
            [ar:Fixture Artist]
            """.trimIndent(),
            isLrc = true
        )

        assertTrue(lyrics.isSynced)
        assertEquals(listOf(1000L, 2500L, 4200L), lyrics.lines.map { it.timestampMs })
        assertEquals(listOf("First", "Second", "Second"), lyrics.lines.map { it.text })
    }

    @Test
    fun enhancedLrcRemovesWordTimingTagsWithoutDroppingLyricText() {
        val lyrics = LyricsParser.parse("[00:01.00]<00:01.10>Word <00:01.50>timing", isLrc = false)

        assertTrue(lyrics.isSynced)
        assertEquals("Word timing", lyrics.lines.single().text)
        assertEquals(1000L, lyrics.lines.single().timestampMs)
    }

    @Test
    fun metadataAndUnparseableLinesDoNotBecomeFakeSyncedRows() {
        val lyrics = LyricsParser.parse(
            """
            [ti:Fixture]
            [00:01.00]Real line
            [not-a-timestamp]
            """.trimIndent(),
            isLrc = true
        )

        assertEquals(1, lyrics.lines.size)
        assertEquals("Real line", lyrics.lines.single().text)
        assertEquals(1000L, lyrics.lines.single().timestampMs)
    }
}


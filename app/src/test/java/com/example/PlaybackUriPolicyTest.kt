package com.example

import com.example.playback.PlaybackUriPolicy
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class PlaybackUriPolicyTest {
    @Test
    fun availableLocalFileWinsInOfflineMode() {
        assertEquals("file:///qa/song.mp3", PlaybackUriPolicy.resolve("file:///qa/song.mp3", "https://production/song", true))
    }

    @Test
    fun missingLocalFileIsBlockedInOfflineMode() {
        assertNull(PlaybackUriPolicy.resolve(null, "https://production/song", true))
        assertNull(PlaybackUriPolicy.resolve("", "https://production/song", true))
    }

    @Test
    fun onlineModeMayUseRemoteUriWhenNoLocalFileExists() {
        assertEquals("https://qa/song", PlaybackUriPolicy.resolve(null, "https://qa/song", false))
    }
}

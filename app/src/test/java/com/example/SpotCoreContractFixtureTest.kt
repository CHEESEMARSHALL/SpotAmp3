package com.example

import com.example.data.BackendEnvelope
import com.example.data.BackendHomeFeedResponse
import com.example.data.BackendHomeMapper
import com.example.data.HomeFeedSource
import com.example.data.HomeFeedState
import com.example.data.BackendLyricsDto
import com.example.data.BackendMusicIntelligencePage
import com.example.data.BackendPlaybackEventRequest
import com.example.data.DownloadPresentationState
import com.example.data.downloadPresentationState
import com.example.data.CompanionPairingClaimResponse
import com.example.data.CompanionPairingRevokeResponse
import com.squareup.moshi.Moshi
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test

class SpotCoreContractFixtureTest {
    private val moshi = Moshi.Builder().build()

    private fun fixture(name: String): String = requireNotNull(
        javaClass.classLoader?.getResource("companion/v1.4/$name")
    ) { "Missing SpotCore v1.4 fixture: $name" }.readText()

    private fun binaryFixture(name: String): ByteArray = requireNotNull(
        javaClass.classLoader?.getResourceAsStream("companion/v1.4/$name")
    ) { "Missing SpotCore v1.4 binary fixture: $name" }.use { it.readBytes() }

    @Test
    fun `v1 point 4 fixtures preserve ids units nullable artwork and revisions`() {
        val pairing = moshi.adapter(CompanionPairingClaimResponse::class.java)
            .fromJson(fixture("pairing-response.json"))
        assertEquals("1.4", pairing?.contractVersion)
        assertEquals("fixture-device-token", pairing?.deviceToken)

        val revoke = moshi.adapter(CompanionPairingRevokeResponse::class.java)
            .fromJson(fixture("pairing-revoke-response.json"))
        assertEquals("1.4", revoke?.contractVersion)
        assertEquals(true, revoke?.revoked)

        val page = moshi.adapter(BackendEnvelope::class.java).fromJson(fixture("track-page.json"))
        assertEquals(1, page?.total)
        val track = page?.tracks?.single()
        assertEquals("track-42", track?.id)
        assertEquals(210_250.0, track?.duration ?: -1.0, 0.001)
        assertEquals(null, track?.coverUrl)
        assertEquals("Northstar", track?.albumArtist)

        val home = moshi.adapter(BackendHomeFeedResponse::class.java).fromJson(fixture("home-feed.json"))
        assertEquals("track-42", home?.recentlyPlayed?.single()?.id)
        assertEquals(210_250.0, home?.recentlyPlayed?.single()?.duration ?: -1.0, 0.001)
        val album = home?.recentlyAdded?.single()
        assertEquals("album-night-drive", album?.id)
        assertEquals("track-42", album?.tracks?.single()?.id)

        val playlist = moshi.adapter(BackendEnvelope::class.java)
            .fromJson(fixture("playlist-detail.json"))?.playlist
        assertEquals("playlist-fixture-1", playlist?.id)
        assertEquals(1_700_000_000_042L, playlist?.updatedAt)
        assertEquals("track-42", playlist?.tracks?.single()?.id)

        val intelligence = moshi.adapter(BackendMusicIntelligencePage::class.java)
            .fromJson(fixture("intelligence-page.json"))?.tracks?.single()
        assertNotNull(intelligence)
        assertEquals(123.5, intelligence?.features?.bpm ?: 0.0, 0.001)
        assertEquals("warm", intelligence?.labels?.single()?.value)
        assertEquals("taxonomy-7", intelligence?.labels?.single()?.taxonomyVersion)

        val lyrics = moshi.adapter(BackendLyricsDto::class.java).fromJson(fixture("lyrics.json"))
        assertEquals(true, lyrics?.synced)
        assertEquals("fixture-sha256-revision", lyrics?.revision)

        val event = moshi.adapter(BackendPlaybackEventRequest::class.java)
            .fromJson(fixture("playback-event-request.json"))
        assertEquals("fixture-event-1", event?.eventId)
        assertEquals(1_700_000_000_123L, event?.playedAt)
        assertEquals(45_000L, event?.listenedMs)

        val accepted = moshi.adapter(BackendEnvelope::class.java)
            .fromJson(fixture("playback-event-accepted.json"))
        assertEquals(true, accepted?.accepted)
        assertEquals("track-42", accepted?.trackId)
        assertEquals(false, accepted?.duplicate)

        val artwork = binaryFixture("artwork.png")
        assertTrue("Shared artwork fixture is empty", artwork.size > 8)
        assertEquals(
            byteArrayOf(0x89.toByte(), 0x50, 0x4e, 0x47, 0x0d, 0x0a, 0x1a, 0x0a).toList(),
            artwork.take(8)
        )
    }

    @Test
    fun `v1 point 4 saved library metadata does not imply phone audio is downloaded`() {
        val page = moshi.adapter(BackendEnvelope::class.java).fromJson(fixture("track-page.json"))
        assertEquals("track-42", page?.tracks?.single()?.id)

        // The contract supplies library metadata only. Offline audio remains a
        // phone-local download state and starts as available until a file exists.
        assertEquals(
            DownloadPresentationState.AVAILABLE,
            downloadPresentationState(track = null, fileExists = { false })
        )
    }

    @Test
    fun `Discover recommendations continue to use the existing v1 point 4 home feed`() {
        val response = moshi.adapter(BackendHomeFeedResponse::class.java)
            .fromJson(fixture("home-feed.json"))
        val mapped = BackendHomeMapper.merge(
            response = requireNotNull(response),
            fallback = HomeFeedState(),
            artworkBaseUrl = "https://spotcore.example"
        )

        assertEquals(HomeFeedSource.SPOTCORE, mapped.source)
        assertEquals("Blue Signal", mapped.recentPlays.single().title)
        assertEquals("Night Drive", mapped.recentlyAdded.single().title)
    }
}

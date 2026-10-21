package com.example

import androidx.compose.ui.test.SemanticsMatcher
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.hasContentDescription
import androidx.compose.ui.test.hasAnyAncestor
import androidx.compose.ui.test.hasTestTag
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onAllNodesWithTag
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.assertTextContains
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import androidx.compose.ui.test.performScrollToNode
import androidx.compose.ui.test.performTextInput
import androidx.compose.ui.test.performTextClearance
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.espresso.Espresso
import androidx.test.platform.app.InstrumentationRegistry
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.SemanticsProperties
import com.example.data.PlexSettingsManager
import com.example.data.BackendClientManager
import com.example.data.BackendAddPlaylistTrackRequest
import com.example.data.BackendCreatePlaylistRequest
import com.example.data.BackendReorderPlaylistRequest
import com.example.data.BackendRenamePlaylistRequest
import com.example.data.PlaylistSyncState
import com.example.data.BackendIntelligenceJson
import com.example.data.LocalMetadataTagService
import com.example.data.RadioRequest
import com.example.data.RadioService
import com.example.data.RadioType
import com.example.data.MusicDatabase
import com.example.data.MusicDao
import com.example.data.SyncAccountIsolationPolicy
import com.example.data.storageKey
import com.example.data.normalizeCompanionArtistArtworkUrl
import com.example.data.resolveArtworkReference
import com.example.playback.LyricsParser
import com.example.playback.Lyrics
import com.example.data.BackendApiService
import com.example.playback.activeLineIndex
import com.example.data.MusicRepository
import com.example.data.companionPlaylistCreateMutationId
import com.example.data.isNonEmptyRegularFile
import com.example.data.parseCompanionPairingPayload
import com.example.data.toTrackItem
import com.example.playback.PlaybackManager
import com.example.ui.MusicViewModel
import com.example.ui.authenticatedArtworkRequest
import androidx.lifecycle.ViewModelProvider
import androidx.work.WorkManager
import coil.Coil
import coil.request.SuccessResult
import java.io.File
import java.util.concurrent.TimeUnit
import java.net.URLDecoder
import java.net.HttpURLConnection
import java.net.URL
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.first
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import org.json.JSONObject
import org.junit.runner.RunWith

/** Level-B proof against the current SpotCore Qt companion implementation. */
@RunWith(AndroidJUnit4::class)
class CompanionRealPlaybackTest {
    @get:org.junit.Rule
    val composeRule = createAndroidComposeRule<MainActivity>()

    @Test(timeout = 360_000L)
    fun realSpotCorePairLibraryStreamPlaybackSeekTransitionAndBackground() {
        runBlocking {
        fun checkpoint(message: String) = android.util.Log.i("CompanionRealQA", message)
        checkpoint("start")
        val args = InstrumentationRegistry.getArguments()
        val baseUrl = requireArgument(args, "companionBaseUrl")
        val controlUrl = requireArgument(args, "companionControlUrl")
        val pairingPayload = URLDecoder.decode(
            requireArgument(args, "companionPairingPayloadEncoded"),
            Charsets.UTF_8.name()
        )
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        val settings = PlexSettingsManager(context)
        val repository = MusicRepository(context)
        val manager = PlaybackManager.getInstance(context)
        val oldUrl = settings.companionBackendUrl
        val oldToken = settings.companionBackendToken
        val oldDeviceId = settings.companionDeviceId
        val oldSectionId = settings.sectionId
        val oldLibraryName = settings.libraryName
        val oldOfflineOnly = settings.offlineOnly
        val companionApi = BackendClientManager.getApiService(baseUrl)
        val viewModel = ViewModelProvider(composeRule.activity)[MusicViewModel::class.java]
        val musicDatabase = MusicDatabase.getDatabase(context)
        val musicDao = musicDatabase.musicDao()
        val initialSyncBaseline = musicDao.getSyncState("spotcore")?.lastSyncId
        suspend fun syncAndAwait(label: String) {
            val previousSyncId = musicDao.getSyncState("spotcore")?.lastSyncId
            runOnMain { viewModel.syncLibraryCache() }
            var completed = false
            val syncDeadline = System.nanoTime() + TimeUnit.SECONDS.toNanos(60)
            while (System.nanoTime() < syncDeadline) {
                val state = musicDao.getSyncState("spotcore")
                if (state?.lastSyncId != previousSyncId && state?.status == "completed") {
                    completed = true
                    break
                }
                // WorkManager may publish failed while a transient retry is scheduled.
                delay(100L)
            }
            val finalState = musicDao.getSyncState("spotcore")
            assertTrue("$label (syncStatus=${finalState?.status}, error=${finalState?.lastError})", completed)
        }

        var sharedPlaylistId: Int? = null
        var offlineFixtureRatingKey: String? = null
        var offlineFixtureLocalPath: String? = null
        var retryFixtureRatingKey: String? = null
        var retryFixtureLocalPath: String? = null
        var embeddedQaLyrics: Lyrics? = null

        try {
            val librarySnapshot = prepareAndValidateLibrarySnapshot(
                pairingPayload, baseUrl, controlUrl, context, settings, repository, manager,
                viewModel, musicDao, initialSyncBaseline
            ) { label -> syncAndAwait(label) }
            val expectedSpotCoreTracks = librarySnapshot.first
            val indexed = librarySnapshot.second

            val authorization = "Bearer ${settings.companionBackendToken}"
            val api = BackendClientManager.getApiService(baseUrl)
            val qaTrackC = indexed.single { it.ratingKey == "qa-track-c" }
            val externalArtwork = "$controlUrl/art/qa-cover.png"
            assertTrue("QA Track C did not start with local artwork",
                qaTrackC.thumb.startsWith(baseUrl + "/api/v1/media/art/qa-track-c"))
            assertEquals(200, postControl(controlUrl, "set-external-artwork"))
            val externalTrack = api.getLibraryTracks(limit = 100, offset = 0, userAuthToken = authorization)
                .tracks.orEmpty().single { it.id == "qa-track-c" }
            assertEquals("External artwork reference was not shared unchanged", externalArtwork, externalTrack.coverUrl)
            syncAndAwait("External artwork sync did not complete")
            assertEquals("External artwork URL was not retained in the Android library cache",
                externalArtwork, musicDao.getTrackByRatingKey("qa-track-c")?.thumb)
            val externalArtworkResult = Coil.imageLoader(context).execute(
                authenticatedArtworkRequest(
                    context,
                    "$externalArtwork?qa=${System.nanoTime()}",
                    settings.companionBackendToken
                )
            )
            assertTrue("SpotAmp could not fetch and decode the shared external artwork bytes",
                externalArtworkResult is SuccessResult)
            val externalArtworkDrawable = (externalArtworkResult as SuccessResult).drawable
            assertTrue("Shared external artwork decoded without usable image dimensions",
                externalArtworkDrawable.intrinsicWidth > 0 && externalArtworkDrawable.intrinsicHeight > 0)
            assertTrue("No request reached the cross-origin artwork fixture",
                controlStatus(controlUrl).optInt("externalArtworkFetchRequests") > 0)
            checkpoint("external-artwork-bytes-fetched-and-rendered")
            checkpoint("external-artwork-shared-verified")

            assertEquals(200, postControl(controlUrl, "set-missing-artwork"))
            val missingArtworkTrack = api.getLibraryTracks(limit = 100, offset = 0, userAuthToken = authorization)
                .tracks.orEmpty().single { it.id == "qa-track-c" }
            assertEquals("Qt advertised artwork after its source file disappeared", null, missingArtworkTrack.coverUrl)
            assertEquals("Missing artwork route did not fail explicitly", 404,
                authenticatedGet(baseUrl + "/api/v1/media/art/qa-track-c", settings.companionBackendToken).first)
            syncAndAwait("Missing artwork sync did not complete")
            assertEquals("Missing artwork left a stale local image reference", "",
                musicDao.getTrackByRatingKey("qa-track-c")?.thumb)
            checkpoint("missing-artwork-cleared-stale-cache-verified")

            assertEquals(200, postControl(controlUrl, "restore-track-artwork"))
            syncAndAwait("Restored artwork sync did not complete")
            assertTrue("Restored artwork did not replace the missing reference",
                musicDao.getTrackByRatingKey("qa-track-c")?.thumb
                    ?.startsWith(baseUrl + "/api/v1/media/art/qa-track-c?v=") == true)
            checkpoint("missing-artwork-restored-verified")

            val qaTrackA = indexed.single { it.ratingKey == "qa-track-a" }
            assertTrue("SpotCore stream route was not resolved against the paired origin", qaTrackA.key.startsWith("$baseUrl/api/v1/media/stream/"))
            assertTrue("SpotCore artwork route was not resolved against the paired origin", qaTrackA.thumb.startsWith("$baseUrl/api/v1/media/art/"))

            val (streamStatus, streamPayload) = authenticatedGet(qaTrackA.key, settings.companionBackendToken, "bytes=0-3")
            assertEquals("SpotCore stream range request failed on-device", 206, streamStatus)
            assertEquals("RIFF", String(streamPayload.second.take(4).toByteArray(), Charsets.US_ASCII))
            assertTrue("SpotCore stream response had no audio MIME type", streamPayload.first.startsWith("audio/"))

            val (artworkStatus, artworkPayload) = authenticatedGet(qaTrackA.thumb, settings.companionBackendToken)
            assertEquals("Authenticated track artwork failed on-device", 200, artworkStatus)
            assertTrue("Artwork response was not a PNG", artworkPayload.first.startsWith("image/png"))
            assertTrue("Artwork payload was empty", artworkPayload.second.size > 8)
            val initialArtworkValidator = authenticatedArtworkEtag(qaTrackA.thumb, settings.companionBackendToken)
            assertEquals("Initial artwork validator request failed", 200, initialArtworkValidator.first)
            val initialEtag = requireNotNull(initialArtworkValidator.second) { "SpotCore omitted the artwork ETag" }
            assertEquals("Unchanged artwork did not validate from its ETag", 304,
                authenticatedArtworkEtag(qaTrackA.thumb, settings.companionBackendToken, initialEtag).first)
            assertEquals(200, postControl(controlUrl, "replace-artwork"))
            val updatedTrack = BackendClientManager.getApiService(baseUrl)
                .getLibraryTracks(limit = 100, offset = 0, userAuthToken = authorization)
                .tracks.orEmpty().single { it.id == "qa-track-a" }
            val updatedArtworkUrl = resolveArtworkReference(baseUrl, requireNotNull(updatedTrack.coverUrl))
            assertTrue("SpotCore did not issue a new artwork cache key after the source image changed",
                updatedArtworkUrl != qaTrackA.thumb)
            val updatedArtworkValidator = authenticatedArtworkEtag(
                updatedArtworkUrl, settings.companionBackendToken, initialEtag
            )
            assertEquals("Changed artwork was incorrectly served as unchanged", 200, updatedArtworkValidator.first)
            assertTrue("Changed artwork kept the old ETag", updatedArtworkValidator.second != initialEtag)
            val updatedArtwork = authenticatedGet(updatedArtworkUrl, settings.companionBackendToken)
            assertTrue("Changed artwork content was not fetched", !artworkPayload.second.contentEquals(updatedArtwork.second.second))
            checkpoint("artwork-cache-invalidation-verified")
            verifyHashedProductionArtwork(controlUrl, baseUrl, settings.companionBackendToken) { label -> syncAndAwait(label) }
            checkpoint("track-artwork-and-stream-verified")

            val lyricResults = verifyTimedAndEmbeddedLyrics(companionApi, authorization, controlUrl, manager, indexed.single { it.ratingKey == "qa-track-a" }.toTrackItem())
            val parsedLyrics = lyricResults.first
            embeddedQaLyrics = lyricResults.second

            val homeFeed = api.getHomeFeed(authorization)
            val qaAlbum = homeFeed.recentlyAdded.orEmpty().singleOrNull { it.title == "QA Album" }
            assertTrue("SpotCore Home did not sync the QA album with all three tracks", qaAlbum?.tracks?.size == 3)
            assertTrue("SpotCore Home album artwork reference was missing", !qaAlbum?.coverUrl.isNullOrBlank())
            val albumArtworkUrl = resolveArtworkReference(baseUrl, requireNotNull(qaAlbum?.coverUrl))
            val (albumArtworkStatus, albumArtworkPayload) = authenticatedGet(albumArtworkUrl, settings.companionBackendToken)
            assertEquals("Authenticated album artwork failed on-device", 200, albumArtworkStatus)
            assertTrue("Album artwork payload was empty", albumArtworkPayload.second.size > 8)
            val qaArtist = api.getArtists(userAuthToken = authorization).artists.orEmpty().singleOrNull { it.artist == "QA Artist" }
            assertTrue("SpotCore artist artwork reference was missing", !qaArtist?.coverUrl.isNullOrBlank())
            val artistArtworkUrl = normalizeCompanionArtistArtworkUrl(baseUrl, qaArtist?.coverUrl)
            assertTrue("SpotCore artist artwork URL could not be resolved", !artistArtworkUrl.isNullOrBlank())
            val (artistArtworkStatus, _) = authenticatedGet(requireNotNull(artistArtworkUrl), settings.companionBackendToken)
            assertEquals("Authenticated artist artwork failed on-device", 200, artistArtworkStatus)
            checkpoint("home-album-and-artist-artwork-verified")

            await("CLAP and Essentia data sync into the Android library", timeoutMs = 30_000L) {
                runBlocking {
                    musicDatabase.musicDao().getTrackByRatingKey("qa-track-a")?.musicIntelligenceJson?.let { it != "{}" } == true
                }
            }
            val intelligenceTrack = requireNotNull(musicDatabase.musicDao().getTrackByRatingKey("qa-track-a"))
            val intelligence = requireNotNull(BackendIntelligenceJson.decode(intelligenceTrack.musicIntelligenceJson))
            assertEquals(123.5, intelligence.features?.bpm ?: 0.0, 0.01)
            assertTrue("CLAP mood label was missing from Android storage", intelligence.labels.any { it.type == "mood" && it.value == "warm" })
            assertTrue("CLAP style label was missing from Android storage", intelligence.labels.any { it.type == "genre" && it.value == "synth-pop" })
            val musicTags = LocalMetadataTagService().tag(intelligenceTrack)
            assertTrue("CLAP mood was not exposed to Android music facets", "warm" in musicTags.moods)
            assertTrue("Essentia energy was not exposed to Android music facets", musicTags.energy == "high")
            checkpoint("clap-and-essentia-sync-verified")
            verifyAnalysisSearchRadioAndStates(controlUrl, musicDao) { label -> syncAndAwait(label) }
            verifyUnanalyzedAnalysisUi(controlUrl, musicDao) { label -> syncAndAwait(label) }
            checkpoint("unanalyzed-analysis-ui-verified")


            verifySharedPlaylistSync(
                repository, api, authorization, qaTrackA, qaTrackC, controlUrl,
                syncAndAwait = { label -> syncAndAwait(label) },
                onPlaylistCreated = { sharedPlaylistId = it },
                onPlaylistDeleted = { sharedPlaylistId = null }
            )

            val tracks = indexed.sortedBy { it.title }.map { it.toTrackItem() }
            assertTrue("Real SpotCore stream URL was not returned", tracks.first().key.contains("/api/v1/media/stream/"))

            val first = tracks[0]
            val second = tracks[1]
            val third = tracks[2]
            val historyRowsBeforePlayback = controlStatus(controlUrl).optInt("playHistoryRows")
            val playbackRowsBeforePlayback = controlStatus(controlUrl).optInt("playbackEventRows")
            runOnMain { manager.playTrack(first, tracks) }
            checkpoint("play-requested")
            await("Track A reaches READY and progresses") {
                manager.currentTrack.value?.ratingKey == first.ratingKey &&
                    manager.duration.value > 0L && manager.isPlaying.value && manager.progress.value > 150L
            }
            checkpoint("track-a-playing")

            runOnMain { manager.pause() }
            await("pause settles") { !manager.isPlaying.value }
            checkpoint("paused")
            val pausedPosition = manager.progress.value
            Thread.sleep(900L)
            assertTrue("Paused position moved unexpectedly", kotlin.math.abs(manager.progress.value - pausedPosition) < 350L)

            runOnMain { manager.play() }
            await("resume advances") { manager.isPlaying.value && manager.progress.value > pausedPosition + 150L }
            checkpoint("resumed")

            runOnMain { manager.seekTo(10_000L) }
            await("forward seek converges") { manager.progress.value in 8_500L..13_500L }
            assertEquals("Timed lyric did not move with the forward seek", 1, parsedLyrics.activeLineIndex(manager.progress.value))
            runOnMain { manager.seekTo(2_000L) }
            await("backward seek converges") { manager.progress.value in 1_000L..4_500L }
            assertEquals("Timed lyric did not move with the backward seek", 0, parsedLyrics.activeLineIndex(manager.progress.value))
            checkpoint("seeked")

            val offlineFixture = first.copy(
                ratingKey = "spotamp-offline-qa-${System.currentTimeMillis()}"
            )
            offlineFixtureRatingKey = offlineFixture.ratingKey
            val savedFixture = repository.downloadTrack(offlineFixture)
            offlineFixtureLocalPath = savedFixture.localPath
            assertTrue("Downloaded QA track has no completed local file", savedFixture.fileSize > 0L)
            assertTrue("Downloaded QA track file is missing", manager.getLocalAudioFile(offlineFixture) != null)
            checkpoint("offline-fixture-downloaded")

            val retryFixture = first.copy(
                ratingKey = "spotamp-retry-qa-${System.currentTimeMillis()}",
                title = "QA Retry Fixture"
            )
            retryFixtureRatingKey = retryFixture.ratingKey
            repository.queueDownload(retryFixture)
            repository.markDownloadStatus(retryFixture.ratingKey, "failed", "HTTP 503 from QA retry setup")
            val failedRetryFixture = requireNotNull(
                repository.downloadedTracks.first().firstOrNull { it.ratingKey == retryFixture.ratingKey }
            )
            composeRule.onNode(navigationTab("Downloads")).performClick()
            val downloadedAlbumTitle = first.album
            assertTrue("The SpotCore fixture track has no album title", downloadedAlbumTitle.isNotBlank())
            composeRule.waitUntil(15_000L) {
                composeRule.onAllNodesWithText(downloadedAlbumTitle)
                    .fetchSemanticsNodes().isNotEmpty()
            }
            composeRule.onNodeWithTag("downloads_list")
                .performScrollToNode(hasText(downloadedAlbumTitle))
            composeRule.onNodeWithText(downloadedAlbumTitle).performClick()
            composeRule.onNodeWithText(retryFixture.title).performScrollTo()
            composeRule.onNodeWithTag("downloaded_album_track_action_${retryFixture.ratingKey}").performClick()
            val retryDeadline = System.nanoTime() + TimeUnit.SECONDS.toNanos(25)
            var retriedFixture = repository.downloadedTracks.first()
                .firstOrNull { it.ratingKey == retryFixture.ratingKey }
            while (System.nanoTime() < retryDeadline &&
                (retriedFixture?.status != "completed" || retriedFixture?.localPath.isNullOrBlank() ||
                    retriedFixture?.localPath?.let(::isNonEmptyRegularFile) != true)
            ) {
                delay(100L)
                retriedFixture = repository.downloadedTracks.first()
                    .firstOrNull { it.ratingKey == retryFixture.ratingKey }
            }
            assertEquals("Downloads Retry did not complete the failed download", "completed", retriedFixture?.status)
            assertTrue("Retried fixture has no saved file", !retriedFixture?.localPath.isNullOrBlank())
            retryFixtureLocalPath = retriedFixture?.localPath
            assertTrue("Retried fixture file is empty or missing", retryFixtureLocalPath?.let(::isNonEmptyRegularFile) == true)
            assertTrue("Retried fixture is not marked as downloaded", repository.isTrackDownloaded(retryFixture.ratingKey))
            repository.deleteDownloadedTrack(retryFixture.ratingKey)
            assertTrue(
                "Temporary retry fixture row remained",
                repository.downloadedTracks.first().none { it.ratingKey == retryFixture.ratingKey }
            )
            assertTrue(
                "Temporary retry fixture file was not removed",
                retryFixtureLocalPath?.let { !File(it).exists() } == true
            )
            retryFixtureRatingKey = null
            checkpoint("failed-download-retry-recovery-verified")

            runOnMain { manager.pause() }
            await("pause before connection-loss probe settles") { !manager.isPlaying.value }
            assertEquals(200, postControl(controlUrl, "pause"))
            checkpoint("companion-paused")

            settings.offlineOnly = true
            runOnMain { manager.playTrack(offlineFixture, listOf(offlineFixture)) }
            await("downloaded track plays from local storage while SpotCore is unavailable") {
                manager.currentTrack.value?.ratingKey == offlineFixture.ratingKey &&
                    manager.isPlaying.value && manager.progress.value > 150L
            }
            checkpoint("offline-cache-playback-during-outage-verified")
            runOnMain { manager.pause() }
            await("cached track pause settles") { !manager.isPlaying.value }
            val cachedPausePosition = manager.progress.value
            assertEquals(200, postControl(controlUrl, "resume"))
            checkpoint("companion-restored-for-cached-resume")
            runOnMain { manager.play() }
            await("same downloaded track resumes after SpotCore returns") {
                manager.currentTrack.value?.ratingKey == offlineFixture.ratingKey &&
                    manager.isPlaying.value && manager.progress.value > cachedPausePosition + 150L
            }
            checkpoint("offline-cache-resumed-after-reconnect")
            runOnMain { manager.pause() }
            await("cached track pause before cleanup settles") { !manager.isPlaying.value }
            settings.offlineOnly = false
            repository.deleteDownloadedTrack(offlineFixture.ratingKey)
            assertTrue("Temporary QA download remained in the app library", !repository.isTrackDownloaded(offlineFixture.ratingKey))
            assertTrue(
                "Temporary QA download file was not removed",
                offlineFixtureLocalPath?.let { !File(it).exists() } == true
            )
            offlineFixtureRatingKey = null

            assertEquals(200, postControl(controlUrl, "pause"))
            checkpoint("companion-paused-for-stream-retry")
            runOnMain { manager.playTrack(second, tracks) }
            await("connection loss is surfaced as retryable playback error", timeoutMs = 25_000L) {
                manager.playbackError.value != null && manager.playbackErrorRetryable.value
            }
            assertTrue("Failed stream unexpectedly remained playing", !manager.isPlaying.value)
            checkpoint("retryable-error-observed")
            assertEquals(200, postControl(controlUrl, "resume"))
            checkpoint("companion-resumed")
            runOnMain { manager.play() }
            await("retry recovers Track B playback") {
                manager.currentTrack.value?.ratingKey == second.ratingKey &&
                    manager.playbackError.value == null && manager.isPlaying.value && manager.progress.value > 150L
            }
            checkpoint("recovery-verified")
            embeddedQaLyrics?.let { lyrics -> verifyEmbeddedLyricsSeekAlignment(lyrics, manager) }

            runOnMain { manager.next() }
            checkpoint("next-requested")
            await("Track C real transition reaches READY and progresses") {
                manager.currentTrack.value?.ratingKey == third.ratingKey &&
                    manager.isPlaying.value && manager.progress.value > 150L
            }
            checkpoint("track-c-playing")
            await("SpotAmp listening events reach canonical SpotCore history with duration", timeoutMs = 30_000L) {
                val status = controlStatus(controlUrl)
                status.optInt("playHistoryRows") > historyRowsBeforePlayback &&
                    status.optInt("playbackEventRows") > playbackRowsBeforePlayback &&
                    status.optInt("positiveListenedHistoryRows") > 0 &&
                    status.optLong("maxListenedMs") > 0L
            }
            val historyStatus = controlStatus(controlUrl)
            assertTrue("Mobile playback was not added to canonical SpotCore history", historyStatus.optInt("playHistoryRows") > historyRowsBeforePlayback)
            assertTrue("Mobile playback was not added to the idempotency ledger", historyStatus.optInt("playbackEventRows") > playbackRowsBeforePlayback)
            assertTrue(
                "Canonical listening duration was not recorded",
                historyStatus.optInt("positiveListenedHistoryRows") > 0 && historyStatus.optLong("maxListenedMs") > 0L
            )
            var historyHome = api.getHomeFeed(authorization)
            val historyHomeDeadline = System.nanoTime() + TimeUnit.SECONDS.toNanos(20)
            while (historyHome.recentlyPlayed.orEmpty().none { it.id == "qa-track-b" } &&
                System.nanoTime() < historyHomeDeadline
            ) {
                delay(250L)
                historyHome = api.getHomeFeed(authorization)
            }
            assertTrue(
                "SpotCore listening history did not round-trip into SpotAmp Home",
                historyHome.recentlyPlayed.orEmpty().any { it.id == "qa-track-b" }
            )
            checkpoint("canonical-history-sync-verified")



            verifyPlaybackEventOutageRetry(context, settings, manager, first, tracks, controlUrl)


            verifyPairingRevokeAndRepair(settings, viewModel, api, baseUrl, controlUrl, manager, third, tracks)

            runOnMain { manager.playTrack(third, tracks) }
            await("Track C restarts after the phone re-pairs") {
                manager.currentTrack.value?.ratingKey == third.ratingKey &&
                    manager.isPlaying.value && manager.progress.value > 150L
            }
            checkpoint("activity-finishing")
            runOnMain { composeRule.activity.finish() }
            val backgroundPosition = manager.progress.value
            Thread.sleep(1_200L)
            assertTrue("Playback did not progress while the activity was backgrounded", manager.progress.value > backgroundPosition + 150L)
            assertTrue("Playback state was not coherent while the activity was backgrounded", manager.currentTrack.value?.ratingKey == third.ratingKey)
            checkpoint("background-verified")
        } finally {
            checkpoint("cleanup-start")
            runCatching { postControl(controlUrl, "restore-timed-lyrics") }
            runCatching { postControl(controlUrl, "restore-plain-lyrics") }
            runCatching { postControl(controlUrl, "restore-hashed-artwork") }
            runCatching { runOnMain { manager.pause(); manager.release() } }
            checkpoint("cleanup-finished")
            offlineFixtureRatingKey?.let { ratingKey ->
                runCatching { repository.deleteDownloadedTrack(ratingKey) }
            }
            retryFixtureRatingKey?.let { ratingKey ->
                runCatching { WorkManager.getInstance(context).cancelUniqueWork("download_$ratingKey").result.get(2, TimeUnit.SECONDS) }
                runCatching { repository.deleteDownloadedTrack(ratingKey) }
                retryFixtureLocalPath?.let { path -> runCatching { File(path).delete() } }
            }
            sharedPlaylistId?.let { id -> runCatching { repository.deletePlaylist(id) } }
            settings.clearCompanionCredential()
            settings.sectionId = oldSectionId
            settings.libraryName = oldLibraryName
            if (oldUrl.isNotBlank() && oldToken.isNotBlank()) {
                settings.saveCompanionCredential(oldUrl, oldDeviceId, oldToken)
            }
            viewModel.selectLibrary(oldSectionId, oldLibraryName)
            settings.offlineOnly = oldOfflineOnly
        }
        }
    }

    private suspend fun verifyAnalysisSearchRadioAndStates(
        controlUrl: String,
        musicDao: MusicDao,
        syncAndAwait: suspend (String) -> Unit
    ) {
            val searchViewModel = ViewModelProvider(composeRule.activity)[MusicViewModel::class.java]
            verifyCompleteAnalysisSearchAndRadio(musicDao, searchViewModel)
            android.util.Log.i("CompanionRealQA", "complete-analysis-search-radio-behavior-verified")

            composeRule.onNode(navigationTab("Library")).performClick()
            composeRule.onNodeWithTag("library_categories_list")
                .performScrollToNode(hasText("Styles"))
            composeRule.onNodeWithText("Styles").performClick()
            composeRule.onNodeWithText("synth-pop").assertIsDisplayed()
            android.util.Log.i("CompanionRealQA", "complete-analysis-style-visible-in-library-ui-verified")

            assertEquals(200, postControl(controlUrl, "set-analysis-failed"))
            syncAndAwait("Failed analysis state sync did not complete")
            val staleIntelligenceTrack = requireNotNull(musicDao.getTrackByRatingKey("qa-track-a"))
            val staleTags = LocalMetadataTagService().tag(staleIntelligenceTrack)
            assertEquals("Failed Essentia data was exposed as current energy", "unknown", staleTags.energy)
            assertTrue("Stale CLAP label was exposed as a current style facet",
                "synth-pop" !in staleTags.styleTags)
            assertTrue("A manually locked label was discarded with stale model output",
                "warm" in staleTags.moods)
            android.util.Log.i("CompanionRealQA", "stale-analysis-filtered-from-facets-verified")

            assertEquals(200, postControl(controlUrl, "set-analysis-partial"))
            syncAndAwait("Partial analysis state sync did not complete")
            val partialTrack = requireNotNull(musicDao.getTrackByRatingKey("qa-track-a"))
            val partialIntelligence = requireNotNull(
                BackendIntelligenceJson.decode(partialTrack.musicIntelligenceJson)
            )
            assertEquals(
                "Essentia should remain complete while CLAP is running",
                "Complete",
                partialIntelligence.components.first { it.component == "essentia" }.state
            )
            assertEquals(
                "CLAP running state was not retained on Android",
                "Running",
                partialIntelligence.components.first { it.component == "clap" }.state
            )
            val partialTags = LocalMetadataTagService().tag(partialTrack)
            assertEquals("Completed Essentia energy should remain available", "high", partialTags.energy)
            assertTrue("A manually locked mood was lost during partial analysis", "warm" in partialTags.moods)
            assertTrue(
                "A style from running CLAP analysis was exposed as current",
                "synth-pop" !in partialTags.styleTags
            )
            composeRule.waitUntil(10_000L) {
                composeRule.onAllNodesWithText("synth-pop").fetchSemanticsNodes().isEmpty()
            }
            composeRule.onNodeWithText("synth-pop").assertDoesNotExist()

            composeRule.onNodeWithContentDescription("Back to Library").performClick()
            composeRule.onNodeWithTag("library_categories_list")
                .performScrollToNode(hasText("Tracks"))
            composeRule.onNodeWithText("Tracks").performClick()
            val downloadedOnlyFilter = composeRule.onNodeWithText(composeRule.activity.getString(R.string.downloaded_only_filter))
            val downloadedOnlySelected = runCatching {
                downloadedOnlyFilter.fetchSemanticsNode().config[SemanticsProperties.Selected]
            }.getOrDefault(false)
            if (downloadedOnlySelected) {
                downloadedOnlyFilter.performClick()
                composeRule.waitForIdle()
            }
            assertFalse(
                "Downloaded-only mode must be off for the mood-filtered library assertion",
                downloadedOnlyFilter.fetchSemanticsNode().config[SemanticsProperties.Selected]
            )
            composeRule.onNodeWithContentDescription("Back to Library").performClick()
            composeRule.onNodeWithTag("library_categories_list")
                .performScrollToNode(hasText("Moods"))
            composeRule.onNodeWithText("Moods").performClick()
            composeRule.onNodeWithText("warm").assertIsDisplayed()
            composeRule.onNodeWithText("warm").performClick()
            composeRule.onNodeWithTag("library_tracks_list").performScrollToNode(hasText("QA Track A"))
        composeRule.onNode(
            hasText("QA Track A") and hasAnyAncestor(hasTestTag("library_tracks_list"))
        ).assertIsDisplayed()
            composeRule.onNodeWithText("QA Track B").assertDoesNotExist()
            composeRule.onNodeWithContentDescription(composeRule.activity.getString(R.string.library_clear_filter_accessibility)).performClick()
            composeRule.onNodeWithContentDescription("Back to Library").performClick()
            verifyPartialAnalysisSearchAndRadio(musicDao, searchViewModel)
            android.util.Log.i("CompanionRealQA", "partial-analysis-facet-behavior-verified")
            android.util.Log.i("CompanionRealQA", "analysis-search-radio-behavior-verified")

            assertEquals(200, postControl(controlUrl, "restore-analysis"))
            syncAndAwait("Restored analysis state sync did not complete")
            val restoredTags = LocalMetadataTagService().tag(
                requireNotNull(musicDao.getTrackByRatingKey("qa-track-a"))
            )
            assertEquals("high", restoredTags.energy)
            assertTrue("Restored CLAP label did not return to facets", "synth-pop" in restoredTags.styleTags)
            android.util.Log.i("CompanionRealQA", "analysis-state-restored-verified")
    }

    private fun openSearchFromDiscover(query: String) {
        composeRule.onNode(navigationTab("Discover")).performClick()
        val discoverSearch = composeRule.onNodeWithTag("discover_search_input")
        discoverSearch.performTextClearance()
        discoverSearch.performTextInput(query)
        composeRule.onNodeWithTag("discover_open_search").performClick()
        composeRule.onNodeWithTag("search_text_input").assertTextContains(query)
    }

    private suspend fun verifyCompleteAnalysisSearchAndRadio(musicDao: MusicDao, viewModel: MusicViewModel) {
        assertTrue("Local smart search did not match the synced CLAP style", viewModel.repository.smartSearch("synth-pop").any { it.ratingKey == "qa-track-a" })
        val completeCachedTracks = musicDao.getCachedTracksList()
        assertEquals(
            listOf("qa-track-a"),
            RadioService().generate(
                RadioRequest(RadioType.MOOD_RADIO, mood = "warm", trackCount = 10, recentCooldownDays = 0),
                completeCachedTracks
            ).map { it.ratingKey }
        )
        assertEquals(
            listOf("qa-track-a"),
            RadioService().generate(
                RadioRequest(RadioType.STYLE_RADIO, style = "synth-pop", trackCount = 10, recentCooldownDays = 0),
                completeCachedTracks
            ).map { it.ratingKey }
        )
        openSearchFromDiscover("synth-pop")
        composeRule.onNodeWithTag("search_text_input").assertTextContains("synth-pop")
        composeRule.waitUntil(20_000L) {
            viewModel.searchResults.value.any { it.ratingKey == "qa-track-a" }
        }
        val resultsList = composeRule.onNodeWithTag("search_results_list")
        composeRule.waitUntil(20_000L) {
            runCatching { resultsList.fetchSemanticsNode() }.isSuccess
        }
        resultsList.performScrollToNode(hasText("QA Track A"))
        composeRule.onNode(
            hasText("QA Track A") and hasAnyAncestor(hasTestTag("search_results_list"))
        ).assertIsDisplayed()
        android.util.Log.i("CompanionRealQA", "complete-analysis-search-radio-behavior-verified")
    }

    private suspend fun verifyPartialAnalysisSearchAndRadio(musicDao: MusicDao, viewModel: MusicViewModel) {
        val partialCachedTracks = musicDao.getCachedTracksList()
        assertTrue(
            "Running CLAP style tags remained available to style radio",
            RadioService().generate(
                RadioRequest(RadioType.STYLE_RADIO, style = "synth-pop", trackCount = 10, recentCooldownDays = 0),
                partialCachedTracks
            ).none { it.ratingKey == "qa-track-a" }
        )
        assertTrue(
            "Manually locked mood was removed from mood radio",
            RadioService().generate(
                RadioRequest(RadioType.MOOD_RADIO, mood = "warm", trackCount = 10, recentCooldownDays = 0),
                partialCachedTracks
            ).any { it.ratingKey == "qa-track-a" }
        )
        openSearchFromDiscover("warm")
        val resultsList = composeRule.onNodeWithTag("search_results_list")
        composeRule.waitUntil(20_000L) {
            runCatching { resultsList.fetchSemanticsNode() }.isSuccess
        }
        resultsList.performScrollToNode(hasText("QA Track A"))
        composeRule.onNode(
            hasText("QA Track A") and hasAnyAncestor(hasTestTag("search_results_list"))
        ).assertIsDisplayed()
        composeRule.onNodeWithTag("search_text_input").performTextClearance()
        composeRule.onNodeWithTag("search_text_input").performTextInput("synth-pop")
        composeRule.onNodeWithTag("search_text_input").assertTextContains("synth-pop")
        composeRule.waitUntil(20_000L) {
            composeRule.onAllNodesWithText("No matches found", substring = true).fetchSemanticsNodes().isNotEmpty()
        }
        android.util.Log.i("CompanionRealQA", "analysis-search-radio-behavior-verified")
    }

    private suspend fun verifyUnanalyzedAnalysisUi(
        controlUrl: String,
        musicDao: MusicDao,
        syncAndAwait: suspend (String) -> Unit
    ) {
        assertEquals(200, postControl(controlUrl, "set-analysis-unanalyzed"))
        syncAndAwait("Unanalyzed fixture state did not sync")
        val unanalyzedTrack = requireNotNull(musicDao.getTrackByRatingKey("qa-track-a"))
        val unanalyzedTags = LocalMetadataTagService().tag(unanalyzedTrack)
        assertEquals("unknown", unanalyzedTags.energy)
        val unanalyzedIntelligence = requireNotNull(BackendIntelligenceJson.decode(unanalyzedTrack.musicIntelligenceJson))
        assertTrue(unanalyzedIntelligence.components.all { it.state.equals("Pending", ignoreCase = true) })
        assertTrue(unanalyzedIntelligence.labels.isEmpty())
        assertTrue(unanalyzedTags.moods.isEmpty())
        assertTrue(!unanalyzedTags.styleTags.contains("synth-pop"))
        assertTrue(
            "Unanalyzed tracks appeared in synced mood radio",
            RadioService().generate(
                RadioRequest(RadioType.MOOD_RADIO, mood = "warm", trackCount = 10, recentCooldownDays = 0),
                musicDao.getCachedTracksList()
            ).none { it.ratingKey == "qa-track-a" }
        )
        openSearchFromDiscover("warm")
        composeRule.waitUntil(20_000L) {
            composeRule.onAllNodesWithText("No matches found", substring = true).fetchSemanticsNodes().isNotEmpty()
        }
        Espresso.closeSoftKeyboard()
        composeRule.onNode(navigationTab("Library")).performClick()
        composeRule.onNodeWithTag("library_categories_list")
            .performScrollToNode(hasText("Moods"))
        composeRule.onNodeWithText("Moods").performClick()
        val libraryMoods = com.example.data.buildLibraryMetadataFacets(
            emptyList(),
            emptyList(),
            musicDao.getCachedTracksList()
        ).moods
        android.util.Log.i("CompanionRealQA", "unanalyzed-library-moods=${libraryMoods.joinToString("|")}")
        assertTrue("Unanalyzed fixture should leave no mood facets, found $libraryMoods", libraryMoods.isEmpty())
        val moodEmptyState = composeRule.onNodeWithText("No mood details are available for this library.")
        composeRule.waitUntil(10_000L) {
            runCatching { moodEmptyState.assertIsDisplayed() }.isSuccess
        }
        moodEmptyState.assertIsDisplayed()
        composeRule.onNodeWithContentDescription("Back to Library").performClick()
        composeRule.onNodeWithTag("library_categories_list")
            .performScrollToNode(hasText("Styles"))
        composeRule.onNodeWithText("Styles").performClick()
        composeRule.onNodeWithText("Electronic").assertIsDisplayed()
        composeRule.onNodeWithText("synth-pop").assertDoesNotExist()
        composeRule.onNodeWithContentDescription("Back to Library").performClick()
        android.util.Log.i("CompanionRealQA", "unanalyzed-analysis-ui-verified")
    }
    private suspend fun verifySharedPlaylistSync(
        repository: MusicRepository,
        api: com.example.data.BackendApiService,
        authorization: String,
        qaTrackA: com.example.data.CachedTrack,
        qaTrackC: com.example.data.CachedTrack,
        controlUrl: String,
        syncAndAwait: suspend (String) -> Unit,
        onPlaylistCreated: (Int) -> Unit,
        onPlaylistDeleted: () -> Unit
    ) {
            // Exercise playlist creation and deletion through the real library UI and the real
            // SpotCore playlist API. This profile and server contain only disposable QA data.
            var sharedPlaylistId: Int? = null
            var playlistName = "QA shared playlist ${System.currentTimeMillis()}"
            composeRule.onNode(navigationTab("Library")).performClick()
            composeRule.onNodeWithTag("library_categories_list")
                .performScrollToNode(hasText("Playlists"))
            composeRule.onNodeWithText("Playlists").performClick()
            composeRule.onNodeWithTag("library_new_playlist_action").performScrollTo().performClick()
            composeRule.onNodeWithTag("library_playlist_name_input").performTextInput(playlistName)
            composeRule.onNodeWithTag("library_create_playlist_confirm").performClick()
            composeRule.waitUntil(15_000L) {
                runBlocking { repository.playlists.first().any { it.name == playlistName } }
            }
            sharedPlaylistId = repository.playlists.first().first { it.name == playlistName }.id
            onPlaylistCreated(requireNotNull(sharedPlaylistId))
            composeRule.waitUntil(15_000L) {
                runBlocking {
                    repository.playlists.first().firstOrNull { it.id == sharedPlaylistId }?.remoteId != null
                }
            }
            val sharedPlaylist = repository.playlists.first().first { it.id == sharedPlaylistId }
            assertTrue("New playlist was not shared with SpotCore", sharedPlaylist.remoteId != null)
            val createdRemoteId = requireNotNull(sharedPlaylist.remoteId)
            val replayedCreation = requireNotNull(
                api.createPlaylist(
                    BackendCreatePlaylistRequest(
                        name = sharedPlaylist.name,
                        clientMutationId = companionPlaylistCreateMutationId(sharedPlaylist.id, sharedPlaylist.createdAt)
                    ),
                    userAuthToken = authorization
                ).playlist
            )
            assertEquals("Retrying playlist creation created a different remote playlist", createdRemoteId, replayedCreation.id)
            assertEquals(
                "Retrying playlist creation duplicated the playlist",
                1,
                api.getPlaylists(userAuthToken = authorization).playlists.orEmpty().count { it.id == createdRemoteId }
            )
            checkpoint("shared-playlist-create-retry-idempotent")
            checkpoint("shared-playlist-created")

            val playlistLocalId = requireNotNull(sharedPlaylistId)
            val remotePlaylistId = requireNotNull(sharedPlaylist.remoteId)
            repository.addTrackToPlaylist(playlistLocalId, qaTrackA.toTrackItem())
            val androidWrittenPlaylist = requireNotNull(
                api.getPlaylist(remotePlaylistId, userAuthToken = authorization).playlist
            )
            assertEquals("SpotAmp track addition did not reach SpotCore", listOf("qa-track-a"), androidWrittenPlaylist.tracks.map { it.id })

            val desktopWrittenPlaylist = requireNotNull(
                api.addPlaylistTrack(
                    remotePlaylistId,
                    BackendAddPlaylistTrackRequest("qa-track-b", androidWrittenPlaylist.updatedAt),
                    userAuthToken = authorization
                ).playlist
            )
            assertEquals("Second client track addition did not reach SpotCore", listOf("qa-track-a", "qa-track-b"), desktopWrittenPlaylist.tracks.map { it.id })
            repository.syncSharedPlaylists()
            await("SpotAmp pulls a playlist edit made by another client") {
                runBlocking {
                    repository.getPlaylistTracksList(playlistLocalId).map { it.ratingKey } == listOf("qa-track-a", "qa-track-b")
                }
            }
            checkpoint("shared-playlist-bidirectional-sync-verified")

            val phoneReorderSource = repository.getPlaylistTracksList(playlistLocalId)
            assertEquals(listOf("qa-track-a", "qa-track-b"), phoneReorderSource.map { it.ratingKey })
            repository.reorderPlaylistTracks(phoneReorderSource.reversed())
            val phoneReorderedPlaylist = requireNotNull(api.getPlaylist(remotePlaylistId, userAuthToken = authorization).playlist)
            assertEquals("SpotAmp reorder did not reach SpotCore", listOf("qa-track-b", "qa-track-a"), phoneReorderedPlaylist.tracks.map { it.id })
            checkpoint("shared-playlist-phone-reorder-verified")

            val phoneReorderRevision = requireNotNull(phoneReorderedPlaylist.updatedAt)
            val desktopReorderedPlaylist = requireNotNull(
                api.reorderPlaylist(
                    remotePlaylistId,
                    BackendReorderPlaylistRequest(
                        fromIndex = 1,
                        toIndex = 0,
                        baseUpdatedAt = phoneReorderRevision
                    ),
                    userAuthToken = authorization
                ).playlist
            )
            assertEquals("SpotCore reorder did not apply", listOf("qa-track-a", "qa-track-b"), desktopReorderedPlaylist.tracks.map { it.id })
            repository.syncSharedPlaylists()
            await("SpotAmp pulls the SpotCore playlist reorder") {
                runBlocking {
                    repository.getPlaylistTracksList(playlistLocalId).map { it.ratingKey } == listOf("qa-track-a", "qa-track-b")
                }
            }
            checkpoint("shared-playlist-desktop-reorder-sync-verified")

            repository.addTrackToPlaylist(playlistLocalId, qaTrackC.toTrackItem())
            val playlistBeforeSongDelete = requireNotNull(api.getPlaylist(remotePlaylistId, userAuthToken = authorization).playlist)
            val playlistRevisionBeforeSongDelete = playlistBeforeSongDelete.updatedAt
                ?: error("The shared playlist did not report its revision before track deletion")
            assertEquals(listOf("qa-track-a", "qa-track-b", "qa-track-c"), playlistBeforeSongDelete.tracks.map { it.id })
            try {
                assertEquals(200, postControl(controlUrl, "remove-track-from-playlist-sync"))
                val playlistAfterSongDelete = requireNotNull(api.getPlaylist(remotePlaylistId, userAuthToken = authorization).playlist)
                val playlistRevisionAfterSongDelete = playlistAfterSongDelete.updatedAt
                    ?: error("The shared playlist did not report its revision after track deletion")
                assertTrue("Deleting a library track did not advance the shared-playlist revision", playlistRevisionAfterSongDelete > playlistRevisionBeforeSongDelete)
                assertEquals(listOf("qa-track-a", "qa-track-b"), playlistAfterSongDelete.tracks.map { it.id })
                repository.syncSharedPlaylists()
                await("SpotAmp removes a library-deleted track from its shared playlist") {
                    runBlocking {
                        repository.getPlaylistTracksList(playlistLocalId).map { it.ratingKey } == listOf("qa-track-a", "qa-track-b")
                    }
                }
                checkpoint("shared-playlist-deleted-track-refresh-verified")
            } finally {
                assertEquals(200, postControl(controlUrl, "restore-track-from-playlist-sync"))
                syncAndAwait("Restored QA Track C did not return to the SpotAmp library")
            }

            val firstConflictRemote = requireNotNull(api.getPlaylist(remotePlaylistId, userAuthToken = authorization).playlist)
            val firstDesktopName = "$playlistName SpotCore edit"
            api.renamePlaylist(
                remotePlaylistId,
                BackendRenamePlaylistRequest(firstDesktopName, firstConflictRemote.updatedAt),
                userAuthToken = authorization
            )
            val firstPhoneName = "$playlistName SpotAmp edit"
            repository.renamePlaylist(playlistLocalId, firstPhoneName)
            composeRule.waitUntil(15_000L) {
                runBlocking {
                    repository.playlists.first().any {
                        it.id == playlistLocalId && it.name == firstPhoneName && it.syncState == PlaylistSyncState.CONFLICT
                    }
                }
            }
            composeRule.onNodeWithContentDescription("Resolve sync conflict for $firstPhoneName").performScrollTo().performClick()
            composeRule.onNodeWithTag("library_playlist_conflict_dialog").assertIsDisplayed()
            composeRule.onNodeWithTag("library_playlist_conflict_keep_local").performClick()
            composeRule.waitUntil(15_000L) {
                runBlocking {
                    repository.playlists.first().any {
                        it.id == playlistLocalId && it.name == firstPhoneName && it.syncState == PlaylistSyncState.SYNCED
                    }
                }
            }
            assertEquals(
                "Choosing phone edits did not reapply them to SpotCore",
                firstPhoneName,
                api.getPlaylist(remotePlaylistId, userAuthToken = authorization).playlist?.name
            )
            playlistName = firstPhoneName
            checkpoint("playlist-conflict-keep-local-verified")

            val secondConflictRemote = requireNotNull(api.getPlaylist(remotePlaylistId, userAuthToken = authorization).playlist)
            val secondDesktopName = "$playlistName SpotCore chosen"
            api.renamePlaylist(
                remotePlaylistId,
                BackendRenamePlaylistRequest(secondDesktopName, secondConflictRemote.updatedAt),
                userAuthToken = authorization
            )
            val secondPhoneName = "$playlistName SpotAmp stale"
            repository.renamePlaylist(playlistLocalId, secondPhoneName)
            composeRule.waitUntil(15_000L) {
                runBlocking {
                    repository.playlists.first().any {
                        it.id == playlistLocalId && it.name == secondPhoneName && it.syncState == PlaylistSyncState.CONFLICT
                    }
                }
            }
            composeRule.onNodeWithContentDescription("Resolve sync conflict for $secondPhoneName").performScrollTo().performClick()
            composeRule.onNodeWithTag("library_playlist_conflict_dialog").assertIsDisplayed()
            composeRule.onNodeWithTag("library_playlist_conflict_use_remote").performClick()
            composeRule.waitUntil(15_000L) {
                runBlocking {
                    repository.playlists.first().any {
                        it.id == playlistLocalId && it.name == secondDesktopName && it.syncState == PlaylistSyncState.SYNCED
                    }
                }
            }
            playlistName = secondDesktopName
            checkpoint("playlist-conflict-use-remote-verified")

            composeRule.onNodeWithContentDescription("Delete $playlistName").performScrollTo().performClick()
            composeRule.onNodeWithTag("library_delete_playlist_dialog").assertIsDisplayed()
            composeRule.onNodeWithText(
                "Delete “$playlistName” from this device and SpotCore? This can’t be undone."
            ).assertIsDisplayed()
            composeRule.onNodeWithTag("library_delete_playlist_cancel").performClick()
            assertTrue(
                "Canceling shared-playlist deletion removed the local row",
                repository.playlists.first().any { it.id == sharedPlaylistId && it.remoteId != null }
            )
            checkpoint("shared-playlist-delete-cancelled")


            composeRule.onNodeWithContentDescription("Delete $playlistName").performScrollTo().performClick()
            composeRule.onNodeWithTag("library_delete_playlist_confirm").performClick()
            composeRule.waitUntil(15_000L) {
                runBlocking { repository.playlists.first().none { it.id == sharedPlaylistId } }
            }
            composeRule.onNodeWithText(playlistName).assertDoesNotExist()
            sharedPlaylistId = null
            onPlaylistDeleted()
            repository.syncSharedPlaylists()
            assertTrue(
                "Deleted playlist still exists on SpotCore and was re-imported",
                repository.playlists.first().none { it.name == playlistName }
            )
            checkpoint("shared-playlist-delete-confirmed-remote-and-local")

    }

    private suspend fun verifyPlaybackEventOutageRetry(
        context: android.content.Context,
        settings: PlexSettingsManager,
        manager: PlaybackManager,
        first: com.example.playback.TrackItem,
        tracks: List<com.example.playback.TrackItem>,
        controlUrl: String
    ) {            val account = requireNotNull(
                SyncAccountIsolationPolicy.identity(settings.companionBackendUrl, settings.companionDeviceId)
            )
            val accountIdentity = "${account.backendOrigin}|${account.accountId}"
            val playDao = MusicDatabase.getDatabase(context).musicDao()
            suspend fun pendingPlays() = playDao.getPendingCompanionPlays(accountIdentity, 50)
            var pendingBeforeOutage = pendingPlays()
            val initialDrainDeadline = System.nanoTime() + TimeUnit.SECONDS.toNanos(30)
            while (pendingBeforeOutage.isNotEmpty() && System.nanoTime() < initialDrainDeadline) {
                delay(100L)
                pendingBeforeOutage = pendingPlays()
            }
            assertTrue("Earlier SpotCore play events did not drain before the outage probe", pendingBeforeOutage.isEmpty())

            runOnMain { manager.playTrack(first, tracks) }
            await("Track A restarts for the play-event outage probe") {
                manager.currentTrack.value?.ratingKey == first.ratingKey &&
                    manager.isPlaying.value && manager.progress.value > 1_000L
            }
            var pendingAfterTrackChange = pendingPlays()
            val trackChangeDrainDeadline = System.nanoTime() + TimeUnit.SECONDS.toNanos(30)
            while (pendingAfterTrackChange.isNotEmpty() && System.nanoTime() < trackChangeDrainDeadline) {
                delay(100L)
                pendingAfterTrackChange = pendingPlays()
            }
            assertTrue("The preceding track event did not drain before the outage probe", pendingAfterTrackChange.isEmpty())
            val playRowsBeforeOutage = controlStatus(controlUrl).optInt("playHistoryRows")
            val eventRowsBeforeOutage = controlStatus(controlUrl).optInt("playbackEventRows")
            assertEquals(200, postControl(controlUrl, "pause"))
            runOnMain { manager.clearQueue() }

            var outagePending = pendingPlays().firstOrNull { it.trackId == "qa-track-a" }
            val queuedDeadline = System.nanoTime() + TimeUnit.SECONDS.toNanos(15)
            while (outagePending == null && System.nanoTime() < queuedDeadline) {
                delay(100L)
                outagePending = pendingPlays().firstOrNull { it.trackId == "qa-track-a" }
            }
            val outageEvent = requireNotNull(outagePending) { "Track A play was not retained in the Android outbox during the Qt outage" }
            assertTrue("The outage event had no measured listen duration", (outageEvent.listenedMs ?: 0L) > 0L)
            checkpoint("play-event-retained-during-outage")
            assertEquals(200, postControl(controlUrl, "resume"))

            var recoveredStatus = controlStatus(controlUrl)
            var pendingAfterRecovery = pendingPlays()
            val recoveryDeadline = System.nanoTime() + TimeUnit.SECONDS.toNanos(50)
            while ((recoveredStatus.optInt("playbackEventRows") != eventRowsBeforeOutage + 1 ||
                    pendingAfterRecovery.any { it.eventId == outageEvent.eventId }) &&
                System.nanoTime() < recoveryDeadline
            ) {
                delay(250L)
                recoveredStatus = controlStatus(controlUrl)
                pendingAfterRecovery = pendingPlays()
            }
            assertEquals("The retained event was not delivered exactly once after SpotCore returned",
                eventRowsBeforeOutage + 1, recoveredStatus.optInt("playbackEventRows"))
            assertEquals("The retry did not enter canonical SpotCore history exactly once",
                playRowsBeforeOutage + 1, recoveredStatus.optInt("playHistoryRows"))
            assertEquals("The retried event did not preserve Track A identity", "qa-track-a", recoveredStatus.optString("latestTrackId"))
            assertTrue("The retried play event remained in the Android outbox",
                pendingAfterRecovery.none { it.eventId == outageEvent.eventId })
            delay(1_000L)
            assertEquals("A retry duplicated the canonical play event", eventRowsBeforeOutage + 1,
                controlStatus(controlUrl).optInt("playbackEventRows"))
            checkpoint("event-outage-retry-verified")    }

    private suspend fun verifyPairingRevokeAndRepair(
        settings: PlexSettingsManager,
        viewModel: MusicViewModel,
        api: com.example.data.BackendApiService,
        baseUrl: String,
        controlUrl: String,
        manager: PlaybackManager,
        third: com.example.playback.TrackItem,
        tracks: List<com.example.playback.TrackItem>
    ) {            val revokedToken = settings.companionBackendToken
            runOnMain { viewModel.disconnectCompanion() }
            await("SpotAmp clears local credentials after revocation") { !settings.isCompanionConfigured }
            val revokedTokenStatus = runCatching {
                api.getLibraryTracks(userAuthToken = "Bearer $revokedToken")
                200
            }.getOrElse { (it as? retrofit2.HttpException)?.code() ?: -1 }
            assertEquals("SpotCore still accepted the revoked phone credential", 401, revokedTokenStatus)

            val nextPairing = postControlJson(controlUrl, "new-pairing")
            val nextPayload = parseCompanionPairingPayload(
                URLDecoder.decode(nextPairing.getString("pairingPayloadEncoded"), Charsets.UTF_8.name())
            )
            val rePairSucceeded = java.util.concurrent.atomic.AtomicBoolean(false)
            val rePairFailure = java.util.concurrent.atomic.AtomicReference<String?>(null)
            runOnMain {
                viewModel.claimCompanionPairing(
                    payload = nextPayload,
                    deviceName = "SpotAmp real QA re-pair",
                    onSuccess = { rePairSucceeded.set(true) },
                    onFailure = { rePairFailure.set(it) }
                )
            }
            await("SpotAmp re-pairs after credential revocation") { rePairSucceeded.get() || rePairFailure.get() != null }
            assertTrue("SpotAmp re-pair failed after revocation: ${rePairFailure.get()}", rePairSucceeded.get())
            assertTrue("Re-pair reused the revoked phone token", settings.companionBackendToken != revokedToken)
            val rePairedPage = api.getLibraryTracks(limit = 3, offset = 0,
                userAuthToken = "Bearer ${settings.companionBackendToken}")
            assertEquals(setOf("qa-track-a", "qa-track-b", "qa-track-c"), rePairedPage.tracks.orEmpty().map { it.id }.toSet())
            checkpoint("pairing-revocation-and-repair-verified")    }

    private suspend fun prepareAndValidateLibrarySnapshot(
        pairingPayload: String,
        baseUrl: String,
        controlUrl: String,
        context: android.content.Context,
        settings: PlexSettingsManager,
        repository: MusicRepository,
        manager: PlaybackManager,
        viewModel: MusicViewModel,
        musicDao: MusicDao,
        initialSyncBaseline: Long?,
        syncAndAwait: suspend (String) -> Unit
    ): Pair<Int, List<com.example.data.CachedTrack>> {            settings.clearCompanionCredential()
            settings.offlineOnly = false
            val qrPayload = parseCompanionPairingPayload(pairingPayload)
            assertEquals("QR endpoint did not match the companion service", baseUrl, qrPayload.baseUrl)
            val claimSucceeded = java.util.concurrent.atomic.AtomicBoolean(false)
            val claimFailure = java.util.concurrent.atomic.AtomicReference<String?>(null)
            runOnMain {
                viewModel.claimCompanionPairing(
                    payload = qrPayload,
                    deviceName = "SpotAmp real QA device",
                    onSuccess = { claimSucceeded.set(true) },
                    onFailure = { claimFailure.set(it) }
                )
            }
            await("production pairing claim completes") {
                claimSucceeded.get() || claimFailure.get() != null
            }
            assertTrue("Production pairing claim failed: ${claimFailure.get()}", claimSucceeded.get())
            assertTrue("Pairing did not save a device token", settings.companionBackendToken.isNotBlank())
            assertEquals(baseUrl, settings.companionBackendUrl)
            assertTrue("Pairing did not save a device ID", settings.companionDeviceId.isNotBlank())
            manager.setCredentials("", "")
            assertTrue("Production pairing handler did not configure SpotCore", viewModel.companionConfigured.value)
            checkpoint("paired")

            checkpoint("sync-started")
            val indexed = awaitValue("SpotCore library index completes") {
                repository.getCachedTracksList().filter { it.title in setOf("QA Track A", "QA Track B", "QA Track C") }
            }
            assertEquals(listOf("QA Track A", "QA Track B", "QA Track C"), indexed.map { it.title }.sorted())
            checkpoint("indexed")
            val expectedSpotCoreTracks = controlStatus(controlUrl).optInt("libraryTrackRows")
            assertTrue("The Qt fixture did not contain enough tracks to cross a library page boundary", expectedSpotCoreTracks > 500)
            var indexedSpotCoreTracks = repository.getCachedTracksList().count { it.ratingKey.startsWith("qa-") }
            val fullIndexDeadline = System.nanoTime() + TimeUnit.SECONDS.toNanos(45)
            while (indexedSpotCoreTracks < expectedSpotCoreTracks && System.nanoTime() < fullIndexDeadline) {
                delay(200L)
                indexedSpotCoreTracks = repository.getCachedTracksList().count { it.ratingKey.startsWith("qa-") }
            }
            assertEquals("SpotAmp did not finish the multi-page SpotCore library index", expectedSpotCoreTracks, indexedSpotCoreTracks)
            assertEquals("The second library page was not fully indexed", 500,
                repository.getCachedTracksList().count { it.ratingKey.startsWith("qa-page-") })
            var freshInitialSyncCompleted = false
            val initialSyncDeadline = System.nanoTime() + TimeUnit.SECONDS.toNanos(60)
            while (System.nanoTime() < initialSyncDeadline) {
                val state = musicDao.getSyncState("spotcore")
                if (state?.lastSyncId != initialSyncBaseline && state?.status == "completed") {
                    freshInitialSyncCompleted = true
                    break
                }
                delay(100L)
            }
            val finalInitialSyncState = musicDao.getSyncState("spotcore")
            assertTrue("Fresh pairing sync did not complete before follow-up syncs (status=${finalInitialSyncState?.status}, error=${finalInitialSyncState?.lastError})", freshInitialSyncCompleted)
            checkpoint("multi-page-library-sync-verified")
            assertEquals("Pairing did not select the SpotCore library", "spotcore", viewModel.selectedSectionId.value)

            verifyFavoriteRatingRoundTrip(context, settings, repository, musicDao, viewModel, controlUrl) { label -> syncAndAwait(label) }

            assertEquals("The complete library cache was not indexed before the empty snapshot probe",
                expectedSpotCoreTracks, repository.getCachedTracksList().count { it.ratingKey.startsWith("qa-") })
            assertEquals(200, postControl(controlUrl, "update-track-metadata"))
            assertEquals(200, postControl(controlUrl, "remove-track-for-sync"))
            assertEquals(expectedSpotCoreTracks - 1, controlStatus(controlUrl).optInt("libraryTrackRows"))
            syncAndAwait("Changed track metadata and individual removal did not sync")
            val updatedCachedTrack = requireNotNull(musicDao.getTrackByRatingKey("qa-track-a"))
            assertEquals("QA Track A Updated", updatedCachedTrack.title)
            assertEquals("QA Artist Updated", updatedCachedTrack.artist)
            assertEquals("QA Album Updated", updatedCachedTrack.album)
            assertEquals(2025, updatedCachedTrack.year)
            assertEquals("Ambient", updatedCachedTrack.genres)
            val afterIndividualRemoval = repository.getCachedTracksList().filter { it.ratingKey.startsWith("qa-") }
            assertEquals(expectedSpotCoreTracks - 1, afterIndividualRemoval.size)
            assertTrue("An individually removed desktop track remained cached",
                afterIndividualRemoval.none { it.ratingKey == "qa-page-500" })
            checkpoint("library-metadata-update-and-individual-removal-verified")
            assertEquals(200, postControl(controlUrl, "restore-track-metadata"))
            assertEquals(200, postControl(controlUrl, "restore-track-for-sync"))
            syncAndAwait("Restored metadata and individually removed track did not sync")
            val restoredMetadata = requireNotNull(musicDao.getTrackByRatingKey("qa-track-a"))
            assertEquals("QA Track A", restoredMetadata.title)
            assertEquals("QA Artist", restoredMetadata.artist)
            assertEquals("QA Album", restoredMetadata.album)
            assertEquals(2024, restoredMetadata.year)
            assertEquals("Electronic", restoredMetadata.genres)
            val afterRemovalRestore = repository.getCachedTracksList().filter { it.ratingKey.startsWith("qa-") }
            assertEquals(expectedSpotCoreTracks, afterRemovalRestore.size)
            assertTrue("The restored desktop track did not return to the Android cache",
                afterRemovalRestore.any { it.ratingKey == "qa-page-500" })
            checkpoint("library-metadata-update-and-individual-removal-restored")
            assertEquals(200, postControl(controlUrl, "begin-empty-library"))
            assertEquals("Qt did not expose the intentionally empty snapshot", 0,
                controlStatus(controlUrl).optInt("libraryTrackRows"))
            syncAndAwait("Empty library snapshot did not complete")
            assertEquals("A confirmed empty SpotCore library did not clear the SpotAmp cache", 0,
                repository.getCachedTracksList().count { it.ratingKey.startsWith("qa-") })
            checkpoint("empty-library-snapshot-cleared")
            assertEquals(200, postControl(controlUrl, "restore-library"))
            assertEquals(expectedSpotCoreTracks, controlStatus(controlUrl).optInt("libraryTrackRows"))
            syncAndAwait("Restored SpotCore library did not repopulate the Android cache")
            assertEquals("Restored desktop tracks did not sync back to SpotAmp", expectedSpotCoreTracks,
                repository.getCachedTracksList().count { it.ratingKey.startsWith("qa-") })
            checkpoint("empty-library-snapshot-cleared-and-restored")

            assertEquals(200, postControl(controlUrl, "fail-next-library-page"))
            syncAndAwait("Library sync did not recover after a second-page failure")
            assertEquals("Qt did not inject exactly one failure at the second-page boundary", 1,
                controlStatus(controlUrl).optInt("failedLibraryPageRequests"))
            val tracksAfterPageRetry = repository.getCachedTracksList().filter { it.ratingKey.startsWith("qa-") }
            assertEquals("A failed page retry lost or duplicated SpotCore cache rows", expectedSpotCoreTracks, tracksAfterPageRetry.size)
            assertEquals("The final second-page track was missing after retry", 1,
                tracksAfterPageRetry.count { it.ratingKey == "qa-page-500" })
            checkpoint("failed-page-retry-verified")
            val revisionAuthorization = "Bearer " + settings.companionBackendToken
            val revisionBeforeDrift = requireNotNull(
                BackendClientManager.getApiService(baseUrl)
                    .getLibraryTracks(limit = 1, offset = 0, userAuthToken = revisionAuthorization)
                    .libraryRevision
            )
            val previousRevisionSyncId = musicDao.getSyncState("spotcore")?.lastSyncId
            assertEquals(200, postControl(controlUrl, "mutate-next-library-page"))
            runOnMain { viewModel.syncLibraryCache() }
            var revisionDriftError: String? = null
            val revisionDriftDeadline = System.nanoTime() + TimeUnit.SECONDS.toNanos(25)
            while (System.nanoTime() < revisionDriftDeadline && revisionDriftError == null) {
                val state = musicDao.getSyncState("spotcore")
                if (state?.lastSyncId != previousRevisionSyncId && state?.status == "failed") {
                    revisionDriftError = state.lastError
                    break
                }
                delay(100L)
            }
            assertEquals("Library changed during pagination; retrying a fresh snapshot", revisionDriftError)
            assertEquals(
                "A changing library snapshot pruned previously cached rows",
                expectedSpotCoreTracks,
                repository.getCachedTracksList().count { it.ratingKey.startsWith("qa-") }
            )
            val changedSnapshot = BackendClientManager.getApiService(baseUrl)
                .getLibraryTracks(limit = 500, offset = 0, userAuthToken = revisionAuthorization)
            assertTrue(
                "SpotCore did not advance the library revision when its track changed",
                requireNotNull(changedSnapshot.libraryRevision) > revisionBeforeDrift
            )
            assertEquals(
                "The mid-pagination QA mutation was not applied",
                "Revision Drifted",
                changedSnapshot.tracks.orEmpty().single { it.id == "qa-track-a" }.genre
            )
            checkpoint("library-revision-drift-preserved-cache-verified")
            syncAndAwait("SpotAmp did not recover after the library revision changed during pagination")
            assertEquals(
                expectedSpotCoreTracks,
                repository.getCachedTracksList().count { it.ratingKey.startsWith("qa-") }
            )
            assertEquals(
                "Revision Drifted",
                musicDao.getTrackByRatingKey("qa-track-a")?.genres
            )
            checkpoint("library-revision-drift-retry-verified")
            assertEquals(200, postControl(controlUrl, "restore-library-revision-drift"))
            syncAndAwait("The restored library metadata did not sync after revision-drift QA")
            assertEquals(
                "Electronic",
                musicDao.getTrackByRatingKey("qa-track-a")?.genres
            )
        return expectedSpotCoreTracks to indexed
    }
    private suspend fun verifyFavoriteRatingRoundTrip(
        context: android.content.Context,
        settings: PlexSettingsManager,
        repository: MusicRepository,
        musicDao: MusicDao,
        viewModel: MusicViewModel,
        controlUrl: String,
        syncAndAwait: suspend (String) -> Unit
    ) {
        val authorization = "Bearer ${settings.companionBackendToken}"
        val api = BackendClientManager.getApiService(settings.companionBackendUrl)
        suspend fun awaitRemoteTrackState(favorite: Boolean, rating: Int) {
            val deadline = System.nanoTime() + TimeUnit.SECONDS.toNanos(70)
            var observed: com.example.data.BackendTrackDto? = null
            while (System.nanoTime() < deadline) {
                observed = api.getLibraryTracks(limit = 500, offset = 0, userAuthToken = authorization)
                    .tracks.orEmpty().firstOrNull { it.id == "qa-track-a" }
                if (observed?.favorite == favorite && observed?.rating == rating) return
                delay(250L)
            }
            assertEquals("SpotCore favorite did not converge", favorite, observed?.favorite)
            assertEquals("SpotCore rating did not converge", rating, observed?.rating)
        }

        assertEquals(200, postControl(controlUrl, "set-track-state"))
        syncAndAwait("SpotCore favorite and rating did not sync to Android")
        var state = requireNotNull(musicDao.getTrackByRatingKey("qa-track-a"))
        assertTrue("SpotCore favorite was not cached on Android", state.companionFavorite)
        assertEquals(5, state.companionRating)
        checkpoint("companion-favorite-rating-desktop-to-phone-verified")

        composeRule.onNode(navigationTab("Library")).performClick()
        composeRule.waitUntil(20_000L) {
            viewModel.cachedTracks.value.any { it.ratingKey == "qa-track-a" } &&
                composeRule.onAllNodesWithTag("library_categories_list").fetchSemanticsNodes().isNotEmpty()
        }
        composeRule.onNodeWithTag("library_categories_list").performScrollToNode(hasText("Tracks"))
        composeRule.onNodeWithText("Tracks").performClick()
        composeRule.waitUntil(20_000L) {
            composeRule.onAllNodesWithTag("library_tracks_list").fetchSemanticsNodes().isNotEmpty()
        }
        composeRule.onNodeWithTag("library_tracks_list").performScrollToNode(hasText("QA Track A"))
        composeRule.onNode(
            hasText("QA Track A") and hasAnyAncestor(hasTestTag("library_tracks_list"))
        ).assertIsDisplayed()
        composeRule.onNodeWithContentDescription("Remove SpotCore favorite").performClick()
        awaitRemoteTrackState(favorite = false, rating = 5)
        composeRule.onNodeWithText("★5").performClick()
        composeRule.onNodeWithText("2 stars").performClick()
        awaitRemoteTrackState(favorite = false, rating = 2)
        state = requireNotNull(musicDao.getTrackByRatingKey("qa-track-a"))
        assertFalse(state.companionFavorite)
        assertEquals(2, state.companionRating)
        checkpoint("companion-favorite-rating-phone-to-desktop-verified")

        assertEquals(200, postControl(controlUrl, "set-track-state"))
        syncAndAwait("The later SpotCore favorite/rating edit did not reach Android")
        state = requireNotNull(musicDao.getTrackByRatingKey("qa-track-a"))
        assertTrue(state.companionFavorite)
        assertEquals(5, state.companionRating)
        checkpoint("companion-favorite-rating-desktop-last-write-wins")

        assertEquals(200, postControl(controlUrl, "pause"))
        repository.setCompanionFavorite("qa-track-a", false)
        repository.setCompanionRating("qa-track-a", 3)
        val account = requireNotNull(
            SyncAccountIsolationPolicy.identity(settings.companionBackendUrl, settings.companionDeviceId)
        ).storageKey()
        var pending = musicDao.getPendingCompanionTrackStates(account)
        assertEquals("Offline favorite/rating changes were not retained", 2, pending.size)
        val retryDeadline = System.nanoTime() + TimeUnit.SECONDS.toNanos(20)
        var retried = false
        while (System.nanoTime() < retryDeadline && !retried) {
            retried = WorkManager.getInstance(context).getWorkInfosForUniqueWork("companion_track_state_sync")
                .get(5, TimeUnit.SECONDS).any { it.runAttemptCount > 0 }
            if (!retried) delay(250L)
        }
        assertTrue("SpotAmp did not retry its favorite/rating outbox while Qt was unavailable", retried)
        assertEquals(200, postControl(controlUrl, "resume"))
        awaitRemoteTrackState(favorite = false, rating = 3)
        val recoveryDeadline = System.nanoTime() + TimeUnit.SECONDS.toNanos(15)
        while (musicDao.getPendingCompanionTrackStates(account).isNotEmpty() &&
            System.nanoTime() < recoveryDeadline) delay(100L)
        pending = musicDao.getPendingCompanionTrackStates(account)
        assertTrue("Acknowledged favorite/rating changes remained in the phone outbox", pending.isEmpty())
        checkpoint("companion-favorite-rating-offline-retry-verified")

        assertEquals(200, postControl(controlUrl, "restore-track-state"))
        syncAndAwait("Restored SpotCore favorite/rating values did not reach Android")
        state = requireNotNull(musicDao.getTrackByRatingKey("qa-track-a"))
        assertFalse(state.companionFavorite)
        assertEquals(0, state.companionRating)
        checkpoint("companion-favorite-rating-state-restored")
    }

    private suspend fun verifyHashedProductionArtwork(
        controlUrl: String,
        baseUrl: String,
        token: String,
        syncAndAwait: suspend (String) -> Unit
    ) {
        val hash = postControlJson(controlUrl, "set-hashed-artwork").getString("hash")
        val url = "$baseUrl/api/v1/artwork/$hash"
        try {
            assertTrue("SpotCore returned an invalid production artwork digest", hash.matches(Regex("[0-9a-f]{64}")))
            val (status, payload) = authenticatedGet(url, token)
            assertEquals("Hashed production artwork route failed on the Android device", 200, status)
            assertTrue("Hashed artwork response was not a PNG", payload.first.startsWith("image/png"))
            assertTrue("Hashed artwork payload was empty", payload.second.size > 8)
            val etag = requireNotNull(authenticatedArtworkEtag(url, token).second) {
                "Hashed artwork response omitted its ETag"
            }
            assertEquals("Hashed artwork ETag did not validate", 304, authenticatedArtworkEtag(url, token, etag).first)
            checkpoint("hashed-production-artwork-cache-verified")
        } finally {
            assertEquals(200, postControl(controlUrl, "restore-hashed-artwork"))
        }
        syncAndAwait("Restoring the track art after hashed-cache QA did not sync")
    }

    private suspend fun verifyTimedAndEmbeddedLyrics(
        api: BackendApiService,
        authorization: String,
        controlUrl: String,
        manager: PlaybackManager,
        timedTrack: com.example.playback.TrackItem
    ): Pair<Lyrics, Lyrics?> {
        val timed = api.getLyrics("qa-track-a", authorization)
        assertTrue("Timed lyrics were not marked synchronized", timed.synced)
        assertTrue("Timed lyrics did not contain the fixture text", timed.text.contains("QA Track A lyric one"))
        assertTrue("Lyrics response did not have a cache revision", !timed.revision.isNullOrBlank())
        val parsedTimed = LyricsParser.parse(timed.text, timed.synced)
        assertTrue("SpotAmp did not parse synchronized lyric timestamps", parsedTimed.isSynced)
        assertEquals(5_000L, parsedTimed.lines.single { it.text == "QA Track A lyric two" }.timestampMs)
        assertEquals(200, postControl(controlUrl, "update-timed-lyrics"))
        val refreshed = api.getLyrics("qa-track-a", authorization)
        assertTrue("Changed sidecar did not produce a new lyrics revision", refreshed.revision != timed.revision)
        assertTrue("SpotCore did not return the changed timed lyric text", refreshed.text.contains("QA Track A refreshed lyric one"))
        runOnMain { manager.loadLyricsForTrack(timedTrack) }
        await("SpotAmp did not publish the refreshed paired lyrics") {
            manager.currentLyrics.value?.lines?.any { it.text == "QA Track A refreshed lyric one" } == true
        }
        assertEquals(200, postControl(controlUrl, "restore-timed-lyrics"))
        checkpoint("lyrics-revision-refresh-verified")
        val plain = api.getLyrics("qa-track-b", authorization)
        assertTrue("Plain lyrics were incorrectly marked synchronized", !plain.synced)
        assertTrue("Plain lyric sidecar did not sync to Android", plain.text.contains("QA Track B plain lyric"))
        checkpoint("timed-and-plain-lyrics-verified")

        val setupStatus = postControl(controlUrl, "set-embedded-lyrics")
        if (setupStatus != 200) {
            assertEquals("TagLib-disabled Qt hosts must report unavailable embedded-lyrics support", 501, setupStatus)
            checkpoint("embedded-lyrics-blocked")
            return parsedTimed to null
        }
        val embedded = api.getLyrics("qa-track-b", authorization)
        assertTrue("Embedded timed lyrics were not marked synchronized", embedded.synced)
        assertTrue("Embedded timed lyrics did not reach Android", embedded.text.contains("Embedded QA lyric one"))
        val parsed = LyricsParser.parse(embedded.text, embedded.synced)
        assertEquals(1_250L, requireNotNull(parsed.lines.firstOrNull { it.text == "Embedded QA lyric one" }?.timestampMs))
        assertEquals(5_000L, requireNotNull(parsed.lines.firstOrNull { it.text == "Embedded QA lyric two" }?.timestampMs))
        checkpoint("embedded-lyrics-phone-sync-verified")
        return parsedTimed to parsed
    }

    private fun verifyEmbeddedLyricsSeekAlignment(lyrics: Lyrics, manager: PlaybackManager) {
        runOnMain { manager.seekTo(7_000L) }
        await("Embedded lyric seek advances Track B") { manager.progress.value in 6_000L..9_000L }
        assertEquals("Embedded lyrics did not follow the forward seek", 1, lyrics.activeLineIndex(manager.progress.value))
        runOnMain { manager.seekTo(2_000L) }
        await("Embedded lyric seek returns to the first line") { manager.progress.value in 1_000L..3_500L }
        assertEquals("Embedded lyrics did not follow the backward seek", 0, lyrics.activeLineIndex(manager.progress.value))
        checkpoint("embedded-lyrics-seek-alignment-verified")
    }
    private fun checkpoint(message: String) = android.util.Log.i("CompanionRealQA", message)

    private fun requireArgument(args: android.os.Bundle, key: String): String =
        args.getString(key)?.takeIf { it.isNotBlank() } ?: error("Missing instrumentation argument: $key")

    private fun navigationTab(label: String) =
        SemanticsMatcher.expectValue(SemanticsProperties.Role, Role.Tab) and
            (hasText(label) or hasContentDescription(label))

    private fun authenticatedArtworkEtag(url: String, token: String, ifNoneMatch: String? = null): Pair<Int, String?> {
        val connection = URL(url).openConnection() as HttpURLConnection
        return try {
            connection.requestMethod = "GET"
            connection.connectTimeout = 15_000
            connection.readTimeout = 15_000
            connection.setRequestProperty("Authorization", "Bearer $token")
            ifNoneMatch?.let { connection.setRequestProperty("If-None-Match", it) }
            connection.responseCode to connection.getHeaderField("ETag")
        } finally {
            connection.disconnect()
        }
    }
    private fun authenticatedGet(url: String, token: String, range: String? = null): Pair<Int, Pair<String, ByteArray>> {
        val connection = URL(url).openConnection() as HttpURLConnection
        return try {
            connection.requestMethod = "GET"
            connection.connectTimeout = 15_000
            connection.readTimeout = 15_000
            connection.setRequestProperty("Authorization", "Bearer $token")
            range?.let { connection.setRequestProperty("Range", it) }
            val status = connection.responseCode
            val response = (if (status in 200..299) connection.inputStream else connection.errorStream)
                ?.use { it.readBytes() } ?: ByteArray(0)
            status to (connection.contentType.orEmpty() to response)
        } finally {
            connection.disconnect()
        }
    }

    private fun postControlJson(baseUrl: String, action: String): JSONObject {
        val connection = URL("${baseUrl.trimEnd('/')}/$action").openConnection() as HttpURLConnection
        return try {
            connection.requestMethod = "POST"
            connection.connectTimeout = 15_000
            connection.readTimeout = 15_000
            connection.doOutput = true
            connection.outputStream.use { }
            val status = connection.responseCode
            check(status == 200) { "SpotCore QA control action $action returned HTTP $status" }
            JSONObject(connection.inputStream.bufferedReader().use { it.readText() })
        } finally {
            connection.disconnect()
        }
    }
    private fun controlStatus(baseUrl: String): JSONObject {
        val connection = URL("${baseUrl.trimEnd('/')}/status").openConnection() as HttpURLConnection
        return try {
            connection.requestMethod = "GET"
            connection.connectTimeout = 15_000
            connection.readTimeout = 15_000
            val status = connection.responseCode
            check(status == 200) { "SpotCore QA status endpoint returned HTTP $status" }
            JSONObject(connection.inputStream.bufferedReader().use { it.readText() })
        } finally {
            connection.disconnect()
        }
    }
    private fun postControl(baseUrl: String, action: String): Int {
        val connection = URL("${baseUrl.trimEnd('/')}/$action").openConnection() as HttpURLConnection
        return try {
            connection.requestMethod = "POST"
            connection.connectTimeout = 15_000
            connection.readTimeout = 15_000
            connection.doOutput = true
            connection.outputStream.use { }
            connection.responseCode
        } finally {
            connection.disconnect()
        }
    }

    private fun runOnMain(action: () -> Unit) {
        InstrumentationRegistry.getInstrumentation().runOnMainSync(action)
    }

    private fun await(label: String, timeoutMs: Long = 15_000L, condition: () -> Boolean) {
        val deadline = System.nanoTime() + TimeUnit.MILLISECONDS.toNanos(timeoutMs)
        while (System.nanoTime() < deadline) {
            if (condition()) return
            Thread.sleep(100L)
        }
        assertTrue(label, condition())
    }

    private suspend fun <T> awaitValue(label: String, timeoutMs: Long = 30_000L, supplier: suspend () -> T): T {
        val deadline = System.nanoTime() + TimeUnit.MILLISECONDS.toNanos(timeoutMs)
        var value = supplier()
        while (System.nanoTime() < deadline) {
            if (value is Collection<*> && value.isNotEmpty()) return value
            Thread.sleep(250L)
            value = supplier()
        }
        assertTrue(label, value is Collection<*> && value.isNotEmpty())
        return value
    }

    private fun isNonEmptyRegularFile(path: String): Boolean {
        val file = File(path)
        return file.isFile && file.length() > 0L
    }
}

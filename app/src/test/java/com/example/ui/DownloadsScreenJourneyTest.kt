package com.example.ui

import android.app.Application
import android.content.Context
import androidx.compose.ui.test.assertCountEquals
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onAllNodesWithTag
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.ViewModelStore
import androidx.lifecycle.ViewModelStoreOwner
import androidx.test.core.app.ApplicationProvider
import androidx.work.Configuration
import androidx.work.WorkManager
import androidx.compose.runtime.mutableStateOf
import com.example.R
import com.example.data.DownloadedTrackEntity
import com.example.data.MusicDatabase
import com.example.data.PlexSettingsManager
import com.example.playback.PlaybackManager
import com.example.ui.theme.MyApplicationTheme
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import java.io.File
import java.util.UUID

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35])
class DownloadsScreenJourneyTest {
    @get:Rule
    val composeRule = createComposeRule()

    private val context: Context
        get() = ApplicationProvider.getApplicationContext()
    private var viewModelStore: ViewModelStore? = null

    @Before
    fun setUp() {
        runCatching { WorkManager.getInstance(context) }.getOrElse {
            WorkManager.initialize(context, Configuration.Builder().build())
        }
        clearSavedConnection()
        clearDownloadedTracks()
    }

    @After
    fun tearDown() {
        viewModelStore?.clear()
        viewModelStore = null
        PlaybackManager.getInstance(context).release()
        clearDownloadedTracks()
        clearSavedConnection()
        composeRule.waitForIdle()
    }

    @Test
    fun missingAudioRecoveryLivesInsideTheDownloadedAlbumDetail() {
        val title = "Midnight City"
        val artist = "M83"
        val missingPath = File(context.cacheDir, "missing-${UUID.randomUUID()}.mp3").absolutePath
        assertEquals(false, File(missingPath).exists())
        runBlocking(Dispatchers.IO) {
            MusicDatabase.getDatabase(context).musicDao().insertDownloadedTrack(
                DownloadedTrackEntity(
                    ratingKey = "missing-audio",
                    title = title,
                    artist = artist,
                    album = "Hurry Up, We're Dreaming",
                    key = "/library/parts/midnight-city",
                    thumb = "",
                    duration = 241_000L,
                    fileSize = 4_000_000L,
                    localPath = missingPath,
                    status = "completed"
                )
            )
        }
        val viewModel = createViewModel()
        val selectedAlbum = mutableStateOf<Pair<String, String>?>(null)
        composeRule.setContent {
            MyApplicationTheme {
                val album = selectedAlbum.value
                if (album == null) {
                    DownloadsScreen(
                        viewModel = viewModel,
                        onNavigateToDownloadedAlbum = { albumArtist, albumName ->
                            selectedAlbum.value = albumArtist to albumName
                        }
                    )
                } else {
                    DownloadedAlbumDetailScreen(
                        albumArtist = album.first,
                        albumName = album.second,
                        viewModel = viewModel,
                        onBack = { selectedAlbum.value = null },
                        onNavigateToArtist = { _, _ -> }
                    )
                }
            }
        }

        val status = context.getString(
            R.string.downloads_track_status,
            artist,
            context.getString(R.string.downloads_missing_file_status)
        )
        composeRule.waitUntil(timeoutMillis = 10_000) {
            composeRule.onAllNodesWithTag("download_album_hurry up, we're dreaming")
                .fetchSemanticsNodes().isNotEmpty()
        }

        // The top-level Downloads surface stays collection-first; individual tracks are
        // visible after opening the album.
        composeRule.onNodeWithTag("download_album_hurry up, we're dreaming").assertIsDisplayed()
        composeRule.onAllNodesWithText(title).assertCountEquals(0)
        composeRule.onNodeWithTag("download_album_hurry up, we're dreaming").performClick()
        composeRule.onNodeWithTag("downloaded_album_detail").assertIsDisplayed()
        composeRule.onNodeWithText(title).assertIsDisplayed()
        composeRule.onNodeWithText(status).assertIsDisplayed()
        composeRule.onNodeWithText(context.getString(R.string.downloads_missing_file_recovery)).assertIsDisplayed()
        composeRule.onNodeWithContentDescription(
            context.getString(R.string.downloads_retry_track_accessibility, title, artist)
        ).assertIsDisplayed()
        composeRule.onAllNodesWithText(context.getString(R.string.downloads_available_offline))
            .assertCountEquals(0)
        composeRule.onAllNodesWithText(context.getString(R.string.downloads_failure_generic))
            .assertCountEquals(0)
        composeRule.onAllNodesWithText(context.getString(R.string.downloads_empty_title))
            .assertCountEquals(0)
    }

    @Test
    fun emptyDownloadedOnlyModeOffersOneTapToAllowOnlineMusic() {
        PlexSettingsManager(context).offlineOnly = true
        val viewModel = createViewModel()
        composeRule.setContent {
            MyApplicationTheme { DownloadsScreen(viewModel = viewModel) }
        }

        val emptyTitle = context.getString(R.string.downloads_empty_title)
        composeRule.waitUntil(timeoutMillis = 10_000) {
            composeRule.onAllNodesWithText(emptyTitle).fetchSemanticsNodes().isNotEmpty()
        }
        composeRule.onNodeWithText(emptyTitle).assertIsDisplayed()
        composeRule.onNodeWithText(context.getString(R.string.downloads_downloaded_only_on)).assertIsDisplayed()
        composeRule.onNodeWithTag("downloads_allow_online_from_empty")
            .assertIsDisplayed()
            .performClick()

        composeRule.onNodeWithText(context.getString(R.string.downloads_downloaded_only_off)).assertIsDisplayed()
        composeRule.onAllNodesWithText(context.getString(R.string.downloads_allow_online_music))
            .assertCountEquals(0)
        assertEquals(false, PlexSettingsManager(context).offlineOnly)
    }

    private fun createViewModel(): MusicViewModel {
        val application = ApplicationProvider.getApplicationContext<Application>()
        val store = ViewModelStore().also { viewModelStore = it }
        val owner = object : ViewModelStoreOwner {
            override val viewModelStore: ViewModelStore = store
        }
        val factory = object : ViewModelProvider.Factory {
            @Suppress("UNCHECKED_CAST")
            override fun <T : ViewModel> create(modelClass: Class<T>): T {
                require(modelClass.isAssignableFrom(MusicViewModel::class.java))
                return MusicViewModel(application) as T
            }
        }
        return ViewModelProvider(owner, factory)[MusicViewModel::class.java]
    }

    private fun clearDownloadedTracks() {
        runBlocking(Dispatchers.IO) {
            MusicDatabase.getDatabase(context).musicDao().clearDownloadedTracks()
        }
    }

    private fun clearSavedConnection() {
        PlexSettingsManager(context).apply {
            clearCompanionCredential()
            baseUrl = ""
            token = ""
            sectionId = ""
            offlineOnly = false
        }
    }
}

package com.example

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onRoot
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performScrollToIndex
import androidx.compose.ui.test.assertIsDisplayed
import com.example.data.CachedTrack
import com.example.data.PlexSettingsManager
import com.example.playback.PlaybackManager
import com.example.playback.TrackItem
import com.example.ui.MusicViewModel
import com.example.ui.NowPlayingScreen
import com.example.ui.QaLabScreen
import com.example.ui.TrackRow
import com.example.ui.theme.MyApplicationTheme
import com.github.takahirom.roborazzi.RobolectricDeviceQualifiers
import com.github.takahirom.roborazzi.captureRoboImage
import org.junit.Rule
import org.junit.After
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(qualifiers = RobolectricDeviceQualifiers.Pixel8, sdk = [36])
class ProductStateScreenshotTest {

  @get:Rule val composeTestRule = createComposeRule()
  private lateinit var playbackManager: PlaybackManager

  @Before
  fun prepareProductionPlayer() {
    val context = androidx.test.core.app.ApplicationProvider.getApplicationContext<android.content.Context>()
    PlexSettingsManager(context).offlineOnly = false
    playbackManager = PlaybackManager.getInstance(context)
    playbackManager.clearPlaybackError()
    playbackManager.replaceQueueForQa(
      listOf(
        TrackItem("qa-unknown", "Midnight Signals", "The Testers", "Companion Sessions", "/qa/unknown", "", 0L),
        TrackItem("qa-next", "Glass Satellites", "The Testers", "Companion Sessions", "/qa/next", "", 198_000L)
      )
    )
  }

  @After
  fun releaseProductionPlayer() {
    PlexSettingsManager(androidx.test.core.app.ApplicationProvider.getApplicationContext()).offlineOnly = false
    playbackManager.release()
  }

  @Test
  fun nowPlayingQueue_screenshot() {
    val queue = listOf(
      CachedTrack("qa-1", "Midnight Signals", "The Testers", "Companion Sessions", "/library/metadata/qa-1", "", 214_000),
      CachedTrack("qa-2", "Glass Satellites", "The Testers", "Companion Sessions", "/library/metadata/qa-2", "", 198_000),
      CachedTrack("qa-3", "Afterimage", "The Testers", "Companion Sessions", "/library/metadata/qa-3", "", 241_000)
    )

    composeTestRule.setContent {
      MyApplicationTheme {
        Surface(color = MaterialTheme.colorScheme.background) {
          Column(
            modifier = Modifier.fillMaxSize().padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
          ) {
            Text("Now Playing", style = MaterialTheme.typography.headlineSmall)
            Text("Desktop companion • Connected", color = MaterialTheme.colorScheme.primary)
            Card(modifier = Modifier.fillMaxWidth()) {
              Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text(queue.first().title, style = MaterialTheme.typography.titleLarge)
                Text("${queue.first().artist} • ${queue.first().album}")
                Spacer(modifier = Modifier.height(4.dp))
                Text("01:12 / 03:34", fontSize = 12.sp)
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                  Button(onClick = {}) { Text("Pause") }
                  Button(onClick = {}) { Text("Next") }
                }
              }
            }
            Text("Queue", style = MaterialTheme.typography.titleMedium)
            queue.forEachIndexed { index, track ->
              TrackRow(
                track = track,
                baseUrl = "",
                token = "",
                onMoreClick = {},
                onClick = {},
                isPlaying = index == 0
              )
            }
          }
        }
      }
    }

    composeTestRule.onRoot().captureRoboImage(filePath = "src/test/screenshots/now-playing-queue.png")
  }

  @Test
  fun productionNowPlayingUnknownDurationAndMissingArtwork_screenshot() {
    composeTestRule.setContent {
      MyApplicationTheme {
        NowPlayingScreen(
          viewModel = androidx.lifecycle.viewmodel.compose.viewModel<MusicViewModel>(),
          baseUrl = "",
          token = "",
          onCollapse = {},
          onNavigateToArtist = { _, _ -> },
          onNavigateToAlbum = { _, _ -> },
          onNavigateToDownloads = {}
        )
      }
    }

    composeTestRule.onRoot().captureRoboImage(filePath = "src/test/screenshots/now-playing-production-unknown-duration.png")
  }

  @Test
  fun productionNowPlayingKnownDuration_screenshot() {
    playbackManager.replaceQueueForQa(
      listOf(
        TrackItem("qa-known", "Midnight Signals", "The Testers", "Companion Sessions", "/qa/known", "", 214_000L),
        TrackItem("qa-next", "Glass Satellites", "The Testers", "Companion Sessions", "/qa/next", "", 198_000L)
      )
    )
    composeTestRule.setContent {
      MyApplicationTheme {
        NowPlayingScreen(
          viewModel = androidx.lifecycle.viewmodel.compose.viewModel<MusicViewModel>(),
          baseUrl = "",
          token = "",
          onCollapse = {},
          onNavigateToArtist = { _, _ -> },
          onNavigateToAlbum = { _, _ -> },
          onNavigateToDownloads = {}
        )
      }
    }

    composeTestRule.onRoot().captureRoboImage(filePath = "src/test/screenshots/now-playing-production-known-duration.png")
  }

  @Test
  fun productionNowPlayingMultiTrackQueue_screenshot() {
    composeTestRule.setContent {
      MyApplicationTheme {
        NowPlayingScreen(
          viewModel = androidx.lifecycle.viewmodel.compose.viewModel<MusicViewModel>(),
          baseUrl = "",
          token = "",
          onCollapse = {},
          onNavigateToArtist = { _, _ -> },
          onNavigateToAlbum = { _, _ -> },
          onNavigateToDownloads = {}
        )
      }
    }
    openQueue()

    composeTestRule.onRoot().captureRoboImage(filePath = "src/test/screenshots/now-playing-production-queue.png")
  }

  @Test
  fun productionNowPlayingSingleTrackQueue_screenshot() {
    playbackManager.replaceQueueForQa(
      listOf(TrackItem("qa-single", "Midnight Signals", "The Testers", "Companion Sessions", "/qa/single", "", 214_000L))
    )
    composeTestRule.setContent {
      MyApplicationTheme {
        NowPlayingScreen(
          viewModel = androidx.lifecycle.viewmodel.compose.viewModel<MusicViewModel>(),
          baseUrl = "",
          token = "",
          onCollapse = {},
          onNavigateToArtist = { _, _ -> },
          onNavigateToAlbum = { _, _ -> },
          onNavigateToDownloads = {}
        )
      }
    }
    openQueue()
    composeTestRule.onNodeWithText("Nothing up next").assertIsDisplayed()

    composeTestRule.onRoot().captureRoboImage(filePath = "src/test/screenshots/now-playing-production-single-track-queue.png")
  }

  @Test
  fun qaLabUsesReadableDarkSurfaceColors_screenshot() {
    composeTestRule.setContent {
      MyApplicationTheme {
        QaLabScreen(viewModel = androidx.lifecycle.viewmodel.compose.viewModel<MusicViewModel>())
      }
    }
    composeTestRule.onNodeWithText("QA Lab").assertIsDisplayed()
    composeTestRule.onRoot().captureRoboImage(filePath = "src/test/screenshots/qa-lab-readable-dark-surface.png")
  }

  private fun openQueue() {
    composeTestRule.onNodeWithTag("now_playing_scroll_container").performScrollToIndex(1)
  }
}

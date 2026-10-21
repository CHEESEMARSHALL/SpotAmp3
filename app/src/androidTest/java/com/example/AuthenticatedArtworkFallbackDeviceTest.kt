package com.example

import android.view.ViewGroup
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.toPixelMap
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.platform.ComposeView
import androidx.compose.ui.test.captureToImage
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.unit.dp
import com.example.ui.MiniPlayerArtwork
import com.example.ui.authenticatedArtworkRequest
import java.util.concurrent.atomic.AtomicBoolean
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import androidx.test.ext.junit.runners.AndroidJUnit4

@RunWith(AndroidJUnit4::class)
class AuthenticatedArtworkFallbackDeviceTest {
    @get:Rule
    val composeRule = createAndroidComposeRule<MainActivity>()

    @Test(timeout = 30_000L)
    fun failedArtworkRendersTheNeutralAlbumTile() {
        val requestFailed = AtomicBoolean(false)
        val contentRoot = composeRule.activity.findViewById<ViewGroup>(android.R.id.content)
        val artworkView = (0 until contentRoot.childCount)
            .mapNotNull { contentRoot.getChildAt(it) as? ComposeView }
            .single()

        composeRule.runOnUiThread {
            artworkView.setContent {
                val context = LocalContext.current
                val request = remember(context) {
                    authenticatedArtworkRequest(
                        context = context,
                        imageUrl = "http://127.0.0.1:1/missing-cover-${System.nanoTime()}.jpg",
                        plexToken = ""
                    )
                }
                MiniPlayerArtwork(
                    imageRequest = request,
                    modifier = Modifier.testTag("artwork_fallback_capture"),
                    size = 192.dp,
                    onImageError = { requestFailed.set(true) }
                )
            }
        }

        composeRule.waitUntil(15_000L) { requestFailed.get() }
        val pixels = composeRule.onNodeWithTag("artwork_fallback_capture").captureToImage().toPixelMap()
        assertTrue("The fallback image should have a rendered surface", pixels.width > 0 && pixels.height > 0)
        assertEquals(Color(0xFF211F27), pixels[pixels.width / 8, pixels.height / 8])
        assertEquals(Color(0xFFE6E2FF), pixels[pixels.width / 2, pixels.height / 2])
    }

    @Test(timeout = 30_000L)
    fun absentMiniPlayerArtworkUsesTheSameNeutralAlbumTile() {
        val contentRoot = composeRule.activity.findViewById<ViewGroup>(android.R.id.content)
        val artworkView = (0 until contentRoot.childCount)
            .mapNotNull { contentRoot.getChildAt(it) as? ComposeView }
            .single()

        composeRule.runOnUiThread {
            artworkView.setContent {
                MiniPlayerArtwork(
                    imageRequest = null,
                    modifier = Modifier.testTag("artwork_fallback_capture"),
                    size = 192.dp
                )
            }
        }

        val pixels = composeRule.onNodeWithTag("artwork_fallback_capture").captureToImage().toPixelMap()
        assertTrue("The fallback image should have a rendered surface", pixels.width > 0 && pixels.height > 0)
        assertEquals(Color(0xFF211F27), pixels[pixels.width / 8, pixels.height / 8])
        assertEquals(Color(0xFFE6E2FF), pixels[pixels.width / 2, pixels.height / 2])
    }
}

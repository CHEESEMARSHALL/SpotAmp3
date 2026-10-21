package com.example.ui

import android.graphics.drawable.VectorDrawable
import androidx.test.core.app.ApplicationProvider
import android.content.Context
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35])
class AuthenticatedArtworkRequestTest {
    @Test
    fun loadingAndFailedArtworkUseTheNeutralAlbumTile() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val request = authenticatedArtworkRequest(
            context = context,
            imageUrl = "https://artwork.example.test/missing-cover.jpg",
            plexToken = ""
        )

        assertTrue("Loading artwork should use the branded album tile", request.placeholder is VectorDrawable)
        assertTrue("Failed artwork should not show a system warning icon", request.error is VectorDrawable)
    }
}

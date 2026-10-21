package com.example.ui

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import org.junit.Assert.assertEquals
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [36])
class AlbumPlaytimeCopyTest {
    private val context: Context
        get() = ApplicationProvider.getApplicationContext()

    @Test
    fun listeningTimeUsesNaturalUnitsAndSingularForms() {
        assertEquals("Listening time: 0 minutes", albumPlaytimeLabel(context, 0L))
        assertEquals("Listening time: 1 minute", albumPlaytimeLabel(context, 60_000L))
        assertEquals("Listening time: 1 hour", albumPlaytimeLabel(context, 3_600_000L))
        assertEquals("Listening time: 1 hour 5 minutes", albumPlaytimeLabel(context, 3_900_000L))
        assertEquals("Listening time: 2 hours", albumPlaytimeLabel(context, 7_200_000L))
    }

    @Test
    fun negativeDurationClampsToZero() {
        assertEquals("Listening time: 0 minutes", albumPlaytimeLabel(context, -1L))
    }
}

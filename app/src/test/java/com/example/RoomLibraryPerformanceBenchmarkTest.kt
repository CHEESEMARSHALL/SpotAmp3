package com.example

import android.content.Context
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import com.example.data.CachedTrack
import com.example.data.MusicDatabase
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import kotlin.system.measureNanoTime
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

@RunWith(RobolectricTestRunner::class)
class RoomLibraryPerformanceBenchmarkTest {
    @Test
    fun realRoomCachedLibraryInsertAndFlowEmissionHasRepeatableBaseline() = runBlocking {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val database = Room.inMemoryDatabaseBuilder(context, MusicDatabase::class.java)
            .allowMainThreadQueries()
            .build()
        try {
            val tracks = (0 until 2_000).map { index ->
                CachedTrack(
                    ratingKey = "room-benchmark-" + index,
                    title = "Track " + index,
                    artist = "Artist " + (index % 100),
                    album = "Album " + (index % 250),
                    key = "/track/" + index,
                    thumb = "",
                    duration = 180_000L,
                    year = 2020 + (index % 5),
                    addedAt = index.toLong(),
                    playCount = index % 7,
                    genres = if (index % 2 == 0) "Rock" else "Jazz",
                    collections = "",
                    syncId = 1L,
                    albumArtist = "Artist " + (index % 100),
                    index = (index % 12) + 1,
                    discNumber = (index % 2) + 1
                )
            }

            val elapsedNanos = measureNanoTime {
                database.musicDao().insertCachedTracks(tracks)
                val emitted = database.musicDao().getAllCachedTracks().first()
                assertEquals(tracks.size, emitted.size)
                assertTrue(emitted.first().ratingKey.startsWith("room-benchmark-"))
            }

            val elapsedMs = elapsedNanos / 1_000_000.0
            println("ROOM_LIBRARY_BENCHMARK tracks=" + tracks.size + " elapsedMs=" + elapsedMs)
        } finally {
            database.close()
        }
    }
}
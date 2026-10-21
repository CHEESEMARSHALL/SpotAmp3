package com.example

import androidx.test.core.app.ApplicationProvider
import com.example.data.CachedTrack
import com.example.data.MusicDatabase
import com.example.data.MusicRepository
import com.example.data.albumNavigationKey
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [36])
class CachedAlbumMetadataPresentationTest {
    @Test
    fun blankCachedAlbumArtistUsesSharedUnknownArtistResource() = runBlocking {
        val context = ApplicationProvider.getApplicationContext<android.content.Context>()
        val repository = MusicRepository(context)
        val settings = repository.settings
        val database = MusicDatabase.getDatabase(context)
        val previousUrl = settings.companionBackendUrl
        val previousToken = settings.companionBackendToken
        val ratingKey = "qa-missing-album-artist-${System.nanoTime()}"
        val albumName = "QA missing artist album ${System.nanoTime()}"
        val unknownArtist = context.getString(R.string.common_unknown_artist)

        try {
            settings.companionBackendUrl = "http://127.0.0.1:4182"
            settings.companionBackendToken = "QA_ALBUM_METADATA_TOKEN_DO_NOT_USE"
            database.musicDao().insertCachedTracks(
                listOf(
                    CachedTrack(
                        ratingKey = ratingKey,
                        title = "Fixture track",
                        artist = "",
                        album = albumName,
                        key = "/fixture/$ratingKey",
                        thumb = "",
                        duration = 60_000L,
                        albumArtist = ""
                    )
                )
            )

            val cachedAlbum = repository.getAlbums("spotcore").single { it.title == albumName }

            assertEquals(unknownArtist, cachedAlbum.parentTitle)
            assertEquals(albumNavigationKey(unknownArtist, albumName), cachedAlbum.ratingKey)
        } finally {
            database.openHelper.writableDatabase.execSQL(
                "DELETE FROM cached_tracks WHERE ratingKey = ?",
                arrayOf(ratingKey)
            )
            settings.companionBackendUrl = previousUrl
            settings.companionBackendToken = previousToken
        }
    }
}

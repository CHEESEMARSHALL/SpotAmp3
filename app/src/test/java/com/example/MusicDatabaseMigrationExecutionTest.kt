package com.example

import android.content.Context
import androidx.sqlite.db.SupportSQLiteDatabase
import androidx.sqlite.db.SupportSQLiteOpenHelper
import androidx.sqlite.db.framework.FrameworkSQLiteOpenHelperFactory
import org.robolectric.RuntimeEnvironment
import com.example.data.MusicDatabase
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import java.io.File

@RunWith(RobolectricTestRunner::class)
class MusicDatabaseMigrationExecutionTest {
    @Test
    fun migration14To15_preservesCachedTrackAndCreatesLookupIndex() {
        val context = RuntimeEnvironment.getApplication()
        val databaseFile = File.createTempFile("spotamp-migration-", ".db", context.cacheDir)
        try {
            openVersion14(context, databaseFile).use { helper ->
                helper.writableDatabase.execSQL(
                    "INSERT INTO cached_tracks " +
                        "(ratingKey,title,artist,album,key,thumb,duration,year,addedAt,playCount,lastPlayedAt,genres,collections,syncId,albumArtist,`index`,discNumber) " +
                        "VALUES ('track-1','Song','Artist','Album','/stream','','180000',2024,1,3,NULL,'Rock','',7,'Artist',1,1)"
                )
            }

            openVersion15(context, databaseFile).use { helper ->
                val database = helper.writableDatabase
                database.query("SELECT title, playCount FROM cached_tracks WHERE ratingKey = 'track-1'").use { cursor ->
                    assertTrue(cursor.moveToFirst())
                    assertEquals("Song", cursor.getString(0))
                    assertEquals(3, cursor.getInt(1))
                }
                database.query(
                    "SELECT name FROM sqlite_master WHERE type = 'index' AND name = 'index_cached_tracks_album_albumArtist'"
                ).use { cursor -> assertTrue(cursor.moveToFirst()) }
            }
        } finally {
            databaseFile.delete()
        }
    }


    @Test
    fun migration6To9_chainCreatesTablesAndReopensWithData() {
        val context = RuntimeEnvironment.getApplication()
        val databaseFile = File.createTempFile("spotamp-migration-6-", ".db", context.cacheDir)
        try {
            openVersion6(context, databaseFile).use { it.writableDatabase }
            openVersion9(context, databaseFile).use { helper ->
                val database = helper.writableDatabase
                database.query("SELECT name FROM sqlite_master WHERE type = 'table' AND name IN ('listening_history', 'pending_companion_plays')").use { cursor ->
                    var tables = 0
                    while (cursor.moveToNext()) tables++
                    assertEquals(2, tables)
                }
                database.execSQL("INSERT INTO listening_history (ratingKey, title, artist, album) VALUES ('track-1', 'Song', 'Artist', 'Album')")
                database.execSQL("INSERT INTO pending_companion_plays (eventId, trackId, playedAt) VALUES ('event-1', 'track-1', 123)")
            }
            openVersion9(context, databaseFile).use { helper ->
                helper.writableDatabase.query("SELECT ratingKey, title FROM listening_history").use { cursor ->
                    assertTrue(cursor.moveToFirst())
                    assertEquals("track-1", cursor.getString(0))
                    assertEquals("Song", cursor.getString(1))
                }
                helper.writableDatabase.query("SELECT eventId, trackId, playedAt FROM pending_companion_plays").use { cursor ->
                    assertTrue(cursor.moveToFirst())
                    assertEquals("event-1", cursor.getString(0))
                    assertEquals("track-1", cursor.getString(1))
                    assertEquals(123L, cursor.getLong(2))
                }
            }
        } finally {
            databaseFile.delete()
        }
    }
    @Test
    fun migration9To16_chainPreservesRowsAndAddsAllExpectedColumns() {
        val context = RuntimeEnvironment.getApplication()
        val databaseFile = File.createTempFile("spotamp-migration-9-", ".db", context.cacheDir)
        try {
            openVersion9WithLaterTables(context, databaseFile).use { helper ->
                val database = helper.writableDatabase
                database.execSQL("INSERT INTO cached_tracks (ratingKey,title,artist,album,`key`,thumb,duration,year,addedAt,playCount,lastPlayedAt,genres,collections,syncId) VALUES ('track-1','Song','Artist','Album','/stream','',180000,2024,1,2,NULL,'Rock','',7)")
                database.execSQL("INSERT INTO recently_played (ratingKey,title,artist,album,duration,playedAt) VALUES ('track-1','Song','Artist','Album',180000,123)")
                database.execSQL("INSERT INTO playlists (id,name) VALUES ('playlist-1','Mix')")
                database.execSQL("INSERT INTO pending_companion_plays (eventId,trackId,playedAt) VALUES ('event-1','track-1',123)")
            }
            openVersion16(context, databaseFile).use { helper ->
                val database = helper.writableDatabase
                database.query("SELECT title, playCount, albumArtist, `index`, discNumber FROM cached_tracks WHERE ratingKey = 'track-1'").use { cursor ->
                    assertTrue(cursor.moveToFirst())
                    assertEquals("Song", cursor.getString(0))
                    assertEquals(2, cursor.getInt(1))
                    assertEquals("", cursor.getString(2))
                    assertEquals(0, cursor.getInt(3))
                    assertEquals(1, cursor.getInt(4))
                }
                database.query("SELECT albumArtist FROM recently_played WHERE ratingKey = 'track-1'").use { cursor ->
                    assertTrue(cursor.moveToFirst())
                    assertEquals("", cursor.getString(0))
                }
                database.query("SELECT remoteId, syncState FROM playlists WHERE id = 'playlist-1'").use { cursor ->
                    assertTrue(cursor.moveToFirst())
                    assertEquals(null, cursor.getString(0))
                    assertEquals("local", cursor.getString(1))
                }
                database.query("PRAGMA table_info(pending_companion_plays)").use { cursor ->
                    val columns = mutableSetOf<String>()
                    while (cursor.moveToNext()) columns += cursor.getString(cursor.getColumnIndexOrThrow("name"))
                    assertTrue(columns.contains("accountIdentity"))
                }
                database.query("SELECT eventId, trackId, accountIdentity FROM pending_companion_plays WHERE eventId = 'event-1'").use { cursor ->
                    assertTrue(cursor.moveToFirst())
                    assertEquals("event-1", cursor.getString(0))
                    assertEquals("track-1", cursor.getString(1))
                    assertEquals(null, cursor.getString(2))
                }
            }
        } finally {
            databaseFile.delete()
        }
    }

    private fun openVersion9WithLaterTables(context: Context, file: File): SupportSQLiteOpenHelper =
        FrameworkSQLiteOpenHelperFactory().create(configuration(context, file, 9, object : SupportSQLiteOpenHelper.Callback(9) {
            override fun onCreate(db: SupportSQLiteDatabase) {
                db.execSQL("CREATE TABLE cached_tracks (ratingKey TEXT NOT NULL PRIMARY KEY, title TEXT NOT NULL, artist TEXT NOT NULL, album TEXT NOT NULL, `key` TEXT NOT NULL, thumb TEXT NOT NULL, duration INTEGER NOT NULL, year INTEGER, addedAt INTEGER, playCount INTEGER NOT NULL, lastPlayedAt INTEGER, genres TEXT NOT NULL, collections TEXT NOT NULL, syncId INTEGER NOT NULL)")
                db.execSQL("CREATE TABLE recently_played (ratingKey TEXT NOT NULL PRIMARY KEY, title TEXT NOT NULL, artist TEXT NOT NULL, album TEXT NOT NULL, duration INTEGER NOT NULL, playedAt INTEGER NOT NULL)")
                db.execSQL("CREATE TABLE playlists (id TEXT NOT NULL PRIMARY KEY, name TEXT NOT NULL)")
                db.execSQL("CREATE TABLE pending_companion_plays (eventId TEXT NOT NULL PRIMARY KEY, trackId TEXT NOT NULL, playedAt INTEGER NOT NULL)")
            }
            override fun onUpgrade(db: SupportSQLiteDatabase, oldVersion: Int, newVersion: Int) = Unit
        }))

    private fun openVersion16(context: Context, file: File): SupportSQLiteOpenHelper =
        FrameworkSQLiteOpenHelperFactory().create(configuration(context, file, 16, object : SupportSQLiteOpenHelper.Callback(16) {
            override fun onCreate(db: SupportSQLiteDatabase) = Unit
            override fun onUpgrade(db: SupportSQLiteDatabase, oldVersion: Int, newVersion: Int) {
                MusicDatabase.MIGRATION_9_10.migrate(db)
                MusicDatabase.MIGRATION_10_11.migrate(db)
                MusicDatabase.MIGRATION_11_12.migrate(db)
                MusicDatabase.MIGRATION_12_13.migrate(db)
                MusicDatabase.MIGRATION_13_14.migrate(db)
                MusicDatabase.MIGRATION_14_15.migrate(db)
                MusicDatabase.MIGRATION_15_16.migrate(db)
            }
        }))
    private fun openVersion6(context: Context, file: File): SupportSQLiteOpenHelper =
        FrameworkSQLiteOpenHelperFactory().create(configuration(context, file, 6, object : SupportSQLiteOpenHelper.Callback(6) {
            override fun onCreate(db: SupportSQLiteDatabase) = Unit
            override fun onUpgrade(db: SupportSQLiteDatabase, oldVersion: Int, newVersion: Int) = Unit
        }))

    private fun openVersion9(context: Context, file: File): SupportSQLiteOpenHelper =
        FrameworkSQLiteOpenHelperFactory().create(configuration(context, file, 9, object : SupportSQLiteOpenHelper.Callback(9) {
            override fun onCreate(db: SupportSQLiteDatabase) = Unit
            override fun onUpgrade(db: SupportSQLiteDatabase, oldVersion: Int, newVersion: Int) {
                MusicDatabase.MIGRATION_6_7.migrate(db)
                MusicDatabase.MIGRATION_7_8.migrate(db)
                MusicDatabase.MIGRATION_8_9.migrate(db)
            }
        }))
    private fun openVersion14(context: Context, file: File): SupportSQLiteOpenHelper =
        FrameworkSQLiteOpenHelperFactory().create(configuration(context, file, 14, object : SupportSQLiteOpenHelper.Callback(14) {
            override fun onCreate(db: SupportSQLiteDatabase) {
                db.execSQL("CREATE TABLE cached_tracks (ratingKey TEXT NOT NULL PRIMARY KEY, title TEXT NOT NULL, artist TEXT NOT NULL, album TEXT NOT NULL, `key` TEXT NOT NULL, thumb TEXT NOT NULL, duration INTEGER NOT NULL, year INTEGER, addedAt INTEGER, playCount INTEGER NOT NULL, lastPlayedAt INTEGER, genres TEXT NOT NULL, collections TEXT NOT NULL, syncId INTEGER NOT NULL, albumArtist TEXT NOT NULL, `index` INTEGER NOT NULL, discNumber INTEGER NOT NULL)")
            }
            override fun onUpgrade(db: SupportSQLiteDatabase, oldVersion: Int, newVersion: Int) = Unit
        }))

    private fun openVersion15(context: Context, file: File): SupportSQLiteOpenHelper =
        FrameworkSQLiteOpenHelperFactory().create(configuration(context, file, 15, object : SupportSQLiteOpenHelper.Callback(15) {
            override fun onCreate(db: SupportSQLiteDatabase) = Unit
            override fun onUpgrade(db: SupportSQLiteDatabase, oldVersion: Int, newVersion: Int) {
                MusicDatabase.MIGRATION_14_15.migrate(db)
            }
        }))

    private fun configuration(
        context: Context,
        file: File,
        version: Int,
        callback: SupportSQLiteOpenHelper.Callback
    ) = SupportSQLiteOpenHelper.Configuration.builder(context)
        .name(file.absolutePath)
        .callback(callback)
        .build()
}

package com.example

import android.content.Context
import androidx.sqlite.db.SupportSQLiteDatabase
import androidx.sqlite.db.SupportSQLiteOpenHelper
import androidx.sqlite.db.framework.FrameworkSQLiteOpenHelperFactory
import com.example.data.MusicDatabase
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RuntimeEnvironment
import org.robolectric.RobolectricTestRunner
import java.io.File

@RunWith(RobolectricTestRunner::class)
class MusicDatabaseMigration18To19Test {
    @Test
    fun migrationAddsRemoteTrackStateAndOutboxWithoutDroppingCachedRows() {
        val context = RuntimeEnvironment.getApplication()
        val databaseFile = File.createTempFile("spotamp-mig-", ".db", File(requireNotNull(System.getProperty("java.io.tmpdir"))))
        try {
            openVersion18(context, databaseFile).use { helper ->
                helper.writableDatabase.execSQL(
                    "INSERT INTO cached_tracks (ratingKey,title,artist,album,`key`,thumb,duration) " +
                        "VALUES ('track-1','Fixture','Artist','Album','stream','',120000)"
                )
            }

            openVersion19(context, databaseFile).use { helper ->
                val db = helper.writableDatabase
                db.query("SELECT ratingKey,title,companionFavorite,companionRating FROM cached_tracks").use { cursor ->
                    assertTrue(cursor.moveToFirst())
                    assertEquals("track-1", cursor.getString(0))
                    assertEquals("Fixture", cursor.getString(1))
                    assertEquals(0, cursor.getInt(2))
                    assertEquals(0, cursor.getInt(3))
                }
                db.execSQL(
                    "INSERT INTO pending_companion_track_states " +
                        "(accountIdentity,trackId,field,desiredValue,mutationId,updatedAt) " +
                        "VALUES ('origin|device','track-1','favorite','true','mutation-1',1)"
                )
                db.query("SELECT mutationId FROM pending_companion_track_states").use { cursor ->
                    assertTrue(cursor.moveToFirst())
                    assertEquals("mutation-1", cursor.getString(0))
                }
            }
        } finally {
            databaseFile.delete()
        }
    }

    private fun openVersion18(context: Context, file: File): SupportSQLiteOpenHelper =
        FrameworkSQLiteOpenHelperFactory().create(configuration(context, file, 18, object : SupportSQLiteOpenHelper.Callback(18) {
            override fun onCreate(db: SupportSQLiteDatabase) {
                db.execSQL(
                    "CREATE TABLE cached_tracks (ratingKey TEXT NOT NULL PRIMARY KEY, title TEXT NOT NULL, " +
                        "artist TEXT NOT NULL, album TEXT NOT NULL, `key` TEXT NOT NULL, thumb TEXT NOT NULL, duration INTEGER NOT NULL)"
                )
            }
            override fun onUpgrade(db: SupportSQLiteDatabase, oldVersion: Int, newVersion: Int) = Unit
        }))

    private fun openVersion19(context: Context, file: File): SupportSQLiteOpenHelper =
        FrameworkSQLiteOpenHelperFactory().create(configuration(context, file, 19, object : SupportSQLiteOpenHelper.Callback(19) {
            override fun onCreate(db: SupportSQLiteDatabase) = Unit
            override fun onUpgrade(db: SupportSQLiteDatabase, oldVersion: Int, newVersion: Int) {
                assertEquals(18, oldVersion)
                assertEquals(19, newVersion)
                MusicDatabase.MIGRATION_18_19.migrate(db)
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

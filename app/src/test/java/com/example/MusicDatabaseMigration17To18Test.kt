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
class MusicDatabaseMigration17To18Test {
    @Test
    fun migration17To18AddsAnalysisJsonAndPreservesCachedTrack() {
        val context = RuntimeEnvironment.getApplication()
        val databaseFile = File.createTempFile("spotamp-migration-17-", ".db", context.cacheDir)
        try {
            openVersion17(context, databaseFile).use { helper ->
                helper.writableDatabase.execSQL(
                    "INSERT INTO cached_tracks (ratingKey,title,artist,album,key,thumb,duration) " +
                        "VALUES ('track-1','Fixture','Artist','Album','stream','',120000)"
                )
            }
            openVersion18(context, databaseFile).use { helper ->
                helper.writableDatabase.query(
                    "SELECT ratingKey,title,musicIntelligenceJson FROM cached_tracks"
                ).use { cursor ->
                    assertTrue(cursor.moveToFirst())
                    assertEquals("track-1", cursor.getString(0))
                    assertEquals("Fixture", cursor.getString(1))
                    assertEquals("{}", cursor.getString(2))
                }
            }
        } finally {
            databaseFile.delete()
        }
    }

    private fun openVersion17(context: Context, file: File): SupportSQLiteOpenHelper =
        FrameworkSQLiteOpenHelperFactory().create(configuration(context, file, 17, object : SupportSQLiteOpenHelper.Callback(17) {
            override fun onCreate(db: SupportSQLiteDatabase) {
                db.execSQL(
                    "CREATE TABLE cached_tracks (ratingKey TEXT NOT NULL PRIMARY KEY, title TEXT NOT NULL, " +
                        "artist TEXT NOT NULL, album TEXT NOT NULL, `key` TEXT NOT NULL, thumb TEXT NOT NULL, duration INTEGER NOT NULL)"
                )
            }
            override fun onUpgrade(db: SupportSQLiteDatabase, oldVersion: Int, newVersion: Int) = Unit
        }))

    private fun openVersion18(context: Context, file: File): SupportSQLiteOpenHelper =
        FrameworkSQLiteOpenHelperFactory().create(configuration(context, file, 18, object : SupportSQLiteOpenHelper.Callback(18) {
            override fun onCreate(db: SupportSQLiteDatabase) = Unit
            override fun onUpgrade(db: SupportSQLiteDatabase, oldVersion: Int, newVersion: Int) {
                MusicDatabase.MIGRATION_17_18.migrate(db)
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
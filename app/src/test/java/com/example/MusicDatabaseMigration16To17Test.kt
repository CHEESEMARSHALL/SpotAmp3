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
class MusicDatabaseMigration16To17Test {
    @Test
    fun migration16To17AddsNullableDurationAndPreservesPendingEvent() {
        val context = RuntimeEnvironment.getApplication()
        val databaseFile = File.createTempFile("spotamp-migration-16-", ".db", context.cacheDir)
        try {
            openVersion16(context, databaseFile).use { helper ->
                helper.writableDatabase.execSQL(
                    "INSERT INTO pending_companion_plays (eventId, trackId, playedAt, accountIdentity) " +
                        "VALUES ('event-1', 'track-1', 123, 'backend|device')"
                )
            }
            openVersion17(context, databaseFile).use { helper ->
                helper.writableDatabase.query(
                    "SELECT eventId, trackId, playedAt, accountIdentity, listenedMs FROM pending_companion_plays"
                ).use { cursor ->
                    assertTrue(cursor.moveToFirst())
                    assertEquals("event-1", cursor.getString(0))
                    assertEquals("track-1", cursor.getString(1))
                    assertEquals(123L, cursor.getLong(2))
                    assertEquals("backend|device", cursor.getString(3))
                    assertTrue(cursor.isNull(4))
                }
            }
        } finally {
            databaseFile.delete()
        }
    }

    private fun openVersion16(context: Context, file: File): SupportSQLiteOpenHelper =
        FrameworkSQLiteOpenHelperFactory().create(configuration(context, file, 16, object : SupportSQLiteOpenHelper.Callback(16) {
            override fun onCreate(db: SupportSQLiteDatabase) {
                db.execSQL(
                    "CREATE TABLE pending_companion_plays (eventId TEXT NOT NULL PRIMARY KEY, " +
                        "trackId TEXT NOT NULL, playedAt INTEGER NOT NULL, accountIdentity TEXT)"
                )
            }
            override fun onUpgrade(db: SupportSQLiteDatabase, oldVersion: Int, newVersion: Int) = Unit
        }))

    private fun openVersion17(context: Context, file: File): SupportSQLiteOpenHelper =
        FrameworkSQLiteOpenHelperFactory().create(configuration(context, file, 17, object : SupportSQLiteOpenHelper.Callback(17) {
            override fun onCreate(db: SupportSQLiteDatabase) = Unit
            override fun onUpgrade(db: SupportSQLiteDatabase, oldVersion: Int, newVersion: Int) {
                MusicDatabase.MIGRATION_16_17.migrate(db)
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

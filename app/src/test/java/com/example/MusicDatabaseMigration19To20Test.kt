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
class MusicDatabaseMigration19To20Test {
    @Test
    fun migrationAddsPlaybackIntentWithoutDroppingSavedQueue() {
        val context = RuntimeEnvironment.getApplication()
        val databaseFile = File.createTempFile("spotamp-mig-", ".db", File(requireNotNull(System.getProperty("java.io.tmpdir"))))
        try {
            openVersion19(context, databaseFile).use { helper ->
                helper.writableDatabase.execSQL(
                    "CREATE TABLE playback_state (id INTEGER NOT NULL PRIMARY KEY, queueJson TEXT NOT NULL, " +
                        "currentIndex INTEGER NOT NULL, positionMs INTEGER NOT NULL, shuffleEnabled INTEGER NOT NULL, " +
                        "repeatMode INTEGER NOT NULL, updatedAt INTEGER NOT NULL)"
                )
                helper.writableDatabase.execSQL(
                    "INSERT INTO playback_state (id, queueJson, currentIndex, positionMs, shuffleEnabled, repeatMode, updatedAt) " +
                        "VALUES (1, '[]', 0, 1200, 1, 2, 3)"
                )
            }

            openVersion20(context, databaseFile).use { helper ->
                val db = helper.writableDatabase
                db.query("SELECT queueJson,positionMs,shuffleEnabled,repeatMode,playWhenReady FROM playback_state").use { cursor ->
                    assertTrue(cursor.moveToFirst())
                    assertEquals("[]", cursor.getString(0))
                    assertEquals(1200L, cursor.getLong(1))
                    assertEquals(1, cursor.getInt(2))
                    assertEquals(2, cursor.getInt(3))
                    assertEquals(0, cursor.getInt(4))
                }
            }
        } finally {
            databaseFile.delete()
        }
    }

    private fun openVersion19(context: Context, file: File): SupportSQLiteOpenHelper =
        FrameworkSQLiteOpenHelperFactory().create(configuration(context, file, 19, object : SupportSQLiteOpenHelper.Callback(19) {
            override fun onCreate(db: SupportSQLiteDatabase) = Unit
            override fun onUpgrade(db: SupportSQLiteDatabase, oldVersion: Int, newVersion: Int) = Unit
        }))

    private fun openVersion20(context: Context, file: File): SupportSQLiteOpenHelper =
        FrameworkSQLiteOpenHelperFactory().create(configuration(context, file, 20, object : SupportSQLiteOpenHelper.Callback(20) {
            override fun onCreate(db: SupportSQLiteDatabase) = Unit
            override fun onUpgrade(db: SupportSQLiteDatabase, oldVersion: Int, newVersion: Int) {
                assertEquals(19, oldVersion)
                assertEquals(20, newVersion)
                MusicDatabase.MIGRATION_19_20.migrate(db)
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

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
class MusicDatabaseMigration7To8Test {
    @Test
    fun migration7To8_isNoOpAndPreservesExistingRows() {
        val context = RuntimeEnvironment.getApplication()
        val databaseFile = File.createTempFile("spotamp-migration-7-", ".db", context.cacheDir)
        try {
            openVersion7(context, databaseFile).use { helper ->
                helper.writableDatabase.execSQL(
                    "INSERT INTO fixture (id, title) VALUES ('track-7', 'Existing track')"
                )
            }
            openVersion8(context, databaseFile).use { helper ->
                helper.writableDatabase.query("SELECT title FROM fixture WHERE id = 'track-7'").use { cursor ->
                    assertTrue(cursor.moveToFirst())
                    assertEquals("Existing track", cursor.getString(0))
                }
            }
        } finally {
            databaseFile.delete()
        }
    }

    private fun openVersion7(context: Context, file: File): SupportSQLiteOpenHelper =
        FrameworkSQLiteOpenHelperFactory().create(configuration(context, file, 7, object : SupportSQLiteOpenHelper.Callback(7) {
            override fun onCreate(db: SupportSQLiteDatabase) {
                db.execSQL("CREATE TABLE fixture (id TEXT NOT NULL PRIMARY KEY, title TEXT NOT NULL)")
            }

            override fun onUpgrade(db: SupportSQLiteDatabase, oldVersion: Int, newVersion: Int) = Unit
        }))

    private fun openVersion8(context: Context, file: File): SupportSQLiteOpenHelper =
        FrameworkSQLiteOpenHelperFactory().create(configuration(context, file, 8, object : SupportSQLiteOpenHelper.Callback(8) {
            override fun onCreate(db: SupportSQLiteDatabase) = Unit
            override fun onUpgrade(db: SupportSQLiteDatabase, oldVersion: Int, newVersion: Int) {
                MusicDatabase.MIGRATION_7_8.migrate(db)
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

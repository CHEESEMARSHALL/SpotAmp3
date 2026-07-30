package com.example

import com.example.data.MusicDatabase
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class MusicDatabaseMigrationTest {
    @Test
    fun currentSchemaUsesExplicitSixToSevenMigration() {
        assertEquals(6, MusicDatabase.MIGRATION_6_7.startVersion)
        assertEquals(7, MusicDatabase.MIGRATION_6_7.endVersion)
        assertTrue(MusicDatabase.MIGRATION_6_7.javaClass.name.isNotEmpty())
    }
}

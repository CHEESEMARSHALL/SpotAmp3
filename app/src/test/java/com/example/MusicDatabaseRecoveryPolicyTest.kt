package com.example

import com.example.data.MusicDatabase
import org.junit.Assert.assertEquals
import org.junit.Test

class MusicDatabaseRecoveryPolicyTest {
    @Test
    fun recoveryUsesCanonicalDatabaseName() {
        assertEquals("music_database", MusicDatabase.DATABASE_NAME)
    }
}
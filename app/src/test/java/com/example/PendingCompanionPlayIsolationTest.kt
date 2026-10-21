package com.example

import android.content.Context
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import com.example.data.MusicDatabase
import com.example.data.PendingCompanionPlayEntity
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

@RunWith(RobolectricTestRunner::class)
class PendingCompanionPlayIsolationTest {
    @Test
    fun outboxQueryReturnsOnlyEventsForTheCurrentAccount() = runBlocking {
        val database = Room.inMemoryDatabaseBuilder(
            ApplicationProvider.getApplicationContext<Context>(),
            MusicDatabase::class.java
        ).allowMainThreadQueries().build()
        try {
            val dao = database.musicDao()
            dao.insertPendingCompanionPlay(PendingCompanionPlayEntity("event-a", "track-a", 10, ACCOUNT_A, 9000))
            dao.insertPendingCompanionPlay(PendingCompanionPlayEntity("event-b", "track-b", 20, ACCOUNT_B))
            dao.insertPendingCompanionPlay(PendingCompanionPlayEntity("event-a2", "track-a2", 30, ACCOUNT_A))

            val events = dao.getPendingCompanionPlays(ACCOUNT_A)

            assertEquals(listOf("event-a", "event-a2"), events.map { it.eventId })
            assertTrue(events.all { it.accountIdentity == ACCOUNT_A })
            assertEquals(9000L, events.first().listenedMs)
        } finally {
            database.close()
        }
    }

    @Test
    fun unscopedLegacyOutboxRowsAreNotClaimedByAnAccountWorker() = runBlocking {
        val database = Room.inMemoryDatabaseBuilder(
            ApplicationProvider.getApplicationContext<Context>(),
            MusicDatabase::class.java
        ).allowMainThreadQueries().build()
        try {
            val dao = database.musicDao()
            dao.insertPendingCompanionPlay(PendingCompanionPlayEntity("legacy", "track-legacy", 10, null))
            dao.insertPendingCompanionPlay(PendingCompanionPlayEntity("current", "track-current", 20, ACCOUNT_A))

            val events = dao.getPendingCompanionPlays(ACCOUNT_A)

            assertEquals(listOf("current"), events.map { it.eventId })
        } finally {
            database.close()
        }
    }

    private companion object {
        const val ACCOUNT_A = "https://spotcore-a.example|device-a"
        const val ACCOUNT_B = "https://spotcore-a.example|device-b"
    }
}
package com.example

import android.content.Context
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import com.example.data.CachedTrack
import com.example.data.CompanionTrackStateField
import com.example.data.MusicDatabase
import com.example.data.MusicDao
import com.example.data.MusicRepository
import com.example.data.PendingCompanionTrackStateEntity
import com.example.data.PlexSettingsManager
import com.example.data.SyncAccountIsolationPolicy
import com.example.data.storageKey
import com.sun.net.httpserver.HttpExchange
import com.sun.net.httpserver.HttpServer
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import java.net.InetSocketAddress
import java.nio.charset.StandardCharsets
import java.util.UUID

@RunWith(RobolectricTestRunner::class)
class CompanionTrackStateRepositoryTest {
    private val context = ApplicationProvider.getApplicationContext<Context>()
    private lateinit var repository: MusicRepository
    private lateinit var database: MusicDatabase
    private lateinit var dao: MusicDao
    private lateinit var server: HttpServer
    private lateinit var trackId: String
    private lateinit var accountKey: String
    private lateinit var foreignAccountKey: String
    private var remoteFavorite = false
    private var remoteRating = 0
    private var failNextAfterApply = false
    private val requests = mutableListOf<String>()

    @Before
    fun setUp() {
        database = Room.inMemoryDatabaseBuilder(context, MusicDatabase::class.java).build()
        dao = database.musicDao()
        repository = MusicRepository(context, dao)
        trackId = "state-test-${UUID.randomUUID()}"
        server = HttpServer.create(InetSocketAddress("127.0.0.1", 0), 0)
        server.createContext("/api/v1/library/tracks/") { exchange -> handleTrackState(exchange) }
        server.start()

        val settings = PlexSettingsManager(context)
        settings.companionBackendUrl = "http://127.0.0.1:${server.address.port}"
        settings.companionBackendToken = "QA_TRACK_STATE_TOKEN_DO_NOT_USE"
        settings.companionDeviceId = "device-${server.address.port}"
        val identity = requireNotNull(
            SyncAccountIsolationPolicy.identity(settings.companionBackendUrl, settings.companionDeviceId)
        )
        accountKey = identity.storageKey()
        foreignAccountKey = "$accountKey-foreign"
    }

    @After
    fun tearDown() = runBlocking {
        (dao.getPendingCompanionTrackStates(accountKey) + dao.getPendingCompanionTrackStates(foreignAccountKey))
            .forEach { dao.deletePendingCompanionTrackState(it.accountIdentity, it.trackId, it.field, it.mutationId) }
        PlexSettingsManager(context).clearCompanionCredential()
        database.close()
        server.stop(0)
    }

    @Test
    fun latestPhoneIntentIsVisibleAndACompletedSnapshotAdoptsSpotCoreAfterAcknowledgement() = runBlocking {
        dao.insertCachedTracks(listOf(cachedTrack()))
        dao.enqueueCompanionTrackState(mutation(CompanionTrackStateField.FAVORITE, "true", "favorite-old"))
        dao.enqueueCompanionTrackState(mutation(CompanionTrackStateField.FAVORITE, "false", "favorite-new"))

        assertEquals(1, dao.getPendingCompanionTrackStateCount(accountKey))
        assertEquals("favorite-new", dao.getPendingCompanionTrackStates(accountKey).single().mutationId)
        assertFalse(requireNotNull(dao.getTrackByRatingKey(trackId)).companionFavorite)

        val remoteSnapshot = cachedTrack(favorite = true, rating = 3)
        dao.insertSyncedCachedTracks(listOf(remoteSnapshot), accountKey)
        val overlaid = requireNotNull(dao.getTrackByRatingKey(trackId))
        assertFalse(overlaid.companionFavorite)
        assertEquals(3, overlaid.companionRating)

        assertTrue(repository.syncPendingCompanionTrackStates())
        assertFalse(remoteFavorite)
        assertEquals(listOf("favorite false"), requests)
        assertEquals(0, dao.getPendingCompanionTrackStateCount(accountKey))

        dao.insertSyncedCachedTracks(listOf(cachedTrack(favorite = true, rating = 4)), accountKey)
        val reconciled = requireNotNull(dao.getTrackByRatingKey(trackId))
        assertTrue(reconciled.companionFavorite)
        assertEquals(4, reconciled.companionRating)
    }

    @Test
    fun failedResponseKeepsTheSameIdempotentMutationForRetry() = runBlocking {
        dao.insertCachedTracks(listOf(cachedTrack()))
        val queued = mutation(CompanionTrackStateField.FAVORITE, "true", "favorite-retry")
        dao.enqueueCompanionTrackState(queued)
        failNextAfterApply = true

        val firstAttempt = runCatching { repository.syncPendingCompanionTrackStates() }
        assertTrue(firstAttempt.isFailure)
        assertEquals(1, dao.getPendingCompanionTrackStateCount(accountKey))
        assertEquals(queued.mutationId, dao.getPendingCompanionTrackStates(accountKey).single().mutationId)
        assertTrue(remoteFavorite)

        assertTrue(repository.syncPendingCompanionTrackStates())
        assertEquals(2, requests.size)
        assertTrue(requests.all { it.endsWith("favorite true") })
        assertTrue(remoteFavorite)
        assertEquals(0, dao.getPendingCompanionTrackStateCount(accountKey))
    }

    @Test
    fun pendingWritesAreAccountScopedAndRepeatedRatingChangesCoalesce() = runBlocking {
        dao.insertCachedTracks(listOf(cachedTrack()))
        dao.enqueueCompanionTrackState(mutation(CompanionTrackStateField.RATING, "2", "rating-old"))
        dao.enqueueCompanionTrackState(mutation(CompanionTrackStateField.RATING, "5", "rating-new"))
        dao.upsertPendingCompanionTrackState(
            PendingCompanionTrackStateEntity(
                accountIdentity = foreignAccountKey,
                trackId = trackId,
                field = CompanionTrackStateField.FAVORITE,
                desiredValue = "true",
                mutationId = "other-account",
                updatedAt = 1
            )
        )

        assertEquals(1, dao.getPendingCompanionTrackStateCount(accountKey))
        assertEquals("5", dao.getPendingCompanionTrackStates(accountKey).single().desiredValue)
        assertTrue(repository.syncPendingCompanionTrackStates())
        assertEquals(5, remoteRating)
        assertEquals(1, dao.getPendingCompanionTrackStateCount(foreignAccountKey))
        assertEquals(1, requests.size)
        assertTrue(requests.single().endsWith("rating 5"))
    }

    private fun mutation(field: String, value: String, mutationId: String) =
        PendingCompanionTrackStateEntity(
            accountIdentity = accountKey,
            trackId = trackId,
            field = field,
            desiredValue = value,
            mutationId = mutationId,
            updatedAt = System.currentTimeMillis()
        )

    private fun cachedTrack(favorite: Boolean = false, rating: Int = 0) = CachedTrack(
        ratingKey = trackId,
        title = "State test track",
        artist = "QA artist",
        album = "QA album",
        key = "http://127.0.0.1/stream",
        thumb = "",
        duration = 1000,
        companionFavorite = favorite,
        companionRating = rating
    )

    private fun handleTrackState(exchange: HttpExchange) {
        val body = exchange.requestBody.bufferedReader().use { it.readText() }
        val path = exchange.requestURI.rawPath
        val field: String
        when {
            path.endsWith("/favorite") -> {
                field = "favorite"
                remoteFavorite = body.contains("\"favorite\":true")
            }
            path.endsWith("/rating") -> {
                field = "rating"
                remoteRating = Regex("\\\"rating\\\":(\\d+)").find(body)?.groupValues?.get(1)?.toInt() ?: -1
            }
            else -> {
                exchange.sendResponseHeaders(404, -1)
                exchange.close()
                return
            }
        }
        val value = if (field == "favorite") remoteFavorite else remoteRating
        requests += "$field $value"
        val status = if (failNextAfterApply) {
            failNextAfterApply = false
            503
        } else 200
        val response = "{\"contractVersion\":\"1.4\",\"trackId\":\"$trackId\"}"
            .toByteArray(StandardCharsets.UTF_8)
        exchange.sendResponseHeaders(status, response.size.toLong())
        exchange.responseBody.use { it.write(response) }
    }
}

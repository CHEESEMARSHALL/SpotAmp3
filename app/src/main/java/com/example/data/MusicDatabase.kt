package com.example.data

import android.content.Context
import androidx.room.*
import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase
import kotlinx.coroutines.flow.Flow
import com.example.playback.TrackItem

@Entity(tableName = "sync_state")
data class SyncStateEntity(
    @PrimaryKey val sectionId: String,
    val currentOffset: Int = 0,
    val totalTracks: Int = 0,
    val lastSyncId: Long = 0,
    val status: String = "idle",
    val lastError: String? = null
)

@Entity(
    tableName = "cached_tracks",
    indices = [
        Index(value = ["album", "albumArtist"]),
        Index(value = ["album", "artist"])
    ]
)
data class CachedTrack(
    @PrimaryKey val ratingKey: String,
    val title: String,
    val artist: String,
    val album: String,
    val key: String,
    val thumb: String,
    val duration: Long,
    val year: Int? = null,
    val addedAt: Long? = null,
    val playCount: Int = 0,
    val lastPlayedAt: Long? = null,
    val genres: String = "",
    val collections: String = "",
    val syncId: Long = 0, // Track which sync indexed this
    val albumArtist: String = "",
    val index: Int = 0,
    val discNumber: Int = 1,
    val musicIntelligenceJson: String = "{}",
    @ColumnInfo(defaultValue = "0") val companionFavorite: Boolean = false,
    @ColumnInfo(defaultValue = "0") val companionRating: Int = 0
)

@Entity(tableName = "recently_played")
data class RecentTrack(
    @PrimaryKey(autoGenerate = true) val id: Int = 0,
    val ratingKey: String,
    val title: String,
    val artist: String,
    val album: String,
    val key: String,
    val thumb: String,
    val timestamp: Long = System.currentTimeMillis(),
    val albumArtist: String = ""
)

@Entity(
    tableName = "playlists",
    indices = [Index(value = ["remoteId"], unique = true)]
)
data class PlaylistEntity(
    @PrimaryKey(autoGenerate = true) val id: Int = 0,
    val name: String,
    val createdAt: Long = System.currentTimeMillis(),
    val remoteId: String? = null,
    val remoteUpdatedAt: Long? = null,
    val syncState: String = PlaylistSyncState.LOCAL
)

object PlaylistSyncState {
    const val LOCAL = "local"
    const val SYNCED = "synced"
    const val DIRTY = "dirty"
    const val CONFLICT = "conflict"
}

@Entity(tableName = "playlist_tracks")
data class PlaylistTrackEntity(
    @PrimaryKey(autoGenerate = true) val id: Int = 0,
    val playlistId: Int,
    val ratingKey: String,
    val title: String,
    val artist: String,
    val album: String,
    val key: String,
    val thumb: String,
    val duration: Long,
    val addedAt: Long = System.currentTimeMillis()
)

@Entity(tableName = "downloaded_tracks")
data class DownloadedTrackEntity(
    @PrimaryKey val ratingKey: String,
    val title: String,
    val artist: String,
    val album: String,
    val key: String,
    val thumb: String,
    val duration: Long,
    val fileSize: Long,
    val localPath: String? = null,
    val status: String = "completed",
    val errorMessage: String? = null,
    val downloadedAt: Long = System.currentTimeMillis()
)

@Entity(tableName = "liked_tracks")
data class LikedTrackEntity(
    @PrimaryKey val ratingKey: String,
    val title: String,
    val artist: String,
    val album: String,
    val key: String,
    val thumb: String,
    val duration: Long,
    val likedAt: Long = System.currentTimeMillis()
)

@Entity(tableName = "playback_state")
data class PlaybackStateEntity(
    @PrimaryKey val id: Int = 1,
    val queueJson: String,
    val currentIndex: Int,
    val positionMs: Long,
    val shuffleEnabled: Boolean,
    val repeatMode: Int,
    @ColumnInfo(defaultValue = "0") val playWhenReady: Boolean = false,
    val updatedAt: Long = System.currentTimeMillis()
)

@Entity(tableName = "listening_history")
data class ListeningHistoryEntity(
    @PrimaryKey val ratingKey: String,
    val title: String,
    val artist: String,
    val album: String,
    val playCount: Int = 0,
    val completedCount: Int = 0,
    val skipCount: Int = 0,
    val lastPlayedAt: Long? = null,
    val lastCompletedAt: Long? = null,
    val lastSkippedAt: Long? = null
)

@Entity(tableName = "pending_companion_plays")
data class PendingCompanionPlayEntity(
    @PrimaryKey val eventId: String,
    val trackId: String,
    val playedAt: Long,
    val accountIdentity: String? = null,
    val listenedMs: Long? = null
)

@Entity(
    tableName = "pending_companion_track_states",
    primaryKeys = ["accountIdentity", "trackId", "field"],
    indices = [Index(value = ["accountIdentity", "updatedAt"])]
)
data class PendingCompanionTrackStateEntity(
    val accountIdentity: String,
    val trackId: String,
    val field: String,
    val desiredValue: String,
    val mutationId: String,
    val updatedAt: Long = System.currentTimeMillis()
)

data class CachedSummary(
    val ratingKey: String,
    val title: String,
    val parentTitle: String? = null,
    val thumb: String? = null,
    val year: Int? = null,
    val addedAt: Long? = null,
    val genres: String? = null,
    val type: String
)

@Dao
interface MusicDao {
    // Cached tracks for AI indexing
    @Query("""SELECT * FROM cached_tracks ORDER BY
        albumArtist COLLATE NOCASE,
        album COLLATE NOCASE,
        CASE WHEN discNumber <= 0 THEN 1 ELSE discNumber END,
        CASE WHEN `index` <= 0 THEN 2147483647 ELSE `index` END,
        title COLLATE NOCASE,
        ratingKey""")
    fun getAllCachedTracks(): Flow<List<CachedTrack>>

    @Query("""SELECT * FROM cached_tracks ORDER BY
        albumArtist COLLATE NOCASE,
        album COLLATE NOCASE,
        CASE WHEN discNumber <= 0 THEN 1 ELSE discNumber END,
        CASE WHEN `index` <= 0 THEN 2147483647 ELSE `index` END,
        title COLLATE NOCASE,
        ratingKey""")
    suspend fun getCachedTracksList(): List<CachedTrack>

    @Query("SELECT * FROM cached_tracks WHERE title LIKE '%' || :query || '%' OR artist LIKE '%' || :query || '%' OR album LIKE '%' || :query || '%' LIMIT 100")
    suspend fun searchCachedTracks(query: String): List<CachedTrack>

    @Query("SELECT * FROM cached_tracks WHERE ratingKey = :ratingKey LIMIT 1")
    suspend fun getTrackByRatingKey(ratingKey: String): CachedTrack?

    @Query("SELECT * FROM cached_tracks WHERE albumArtist = :artistName GROUP BY album ORDER BY year DESC, addedAt DESC")
    suspend fun getAlbumsByArtist(artistName: String): List<CachedTrack>

    @Query("""
        SELECT MIN(ratingKey) as ratingKey,
            COALESCE(NULLIF(albumArtist, ''), artist) as title,
            CAST(NULL AS TEXT) as parentTitle,
            MAX(thumb) as thumb,
            CAST(NULL AS INTEGER) as year,
            CAST(NULL AS INTEGER) as addedAt,
            GROUP_CONCAT(DISTINCT NULLIF(genres, '')) as genres,
            'artist' as type
        FROM cached_tracks
        GROUP BY COALESCE(NULLIF(albumArtist, ''), artist)
        ORDER BY title COLLATE NOCASE
    """)
    suspend fun getCachedArtistsSummary(): List<CachedSummary>

    @Query("""
        SELECT MIN(ratingKey) as ratingKey,
            album as title,
            COALESCE(NULLIF(albumArtist, ''), artist) as parentTitle,
            MAX(thumb) as thumb,
            MAX(year) as year,
            MAX(addedAt) as addedAt,
            GROUP_CONCAT(DISTINCT NULLIF(genres, '')) as genres,
            'album' as type
        FROM cached_tracks
        GROUP BY COALESCE(NULLIF(albumArtist, ''), artist), album
        ORDER BY title COLLATE NOCASE
    """)
    suspend fun getCachedAlbumsSummary(): List<CachedSummary>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertCachedTracks(tracks: List<CachedTrack>)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertCachedTrack(track: CachedTrack)

    @Transaction
    suspend fun insertSyncedCachedTracks(tracks: List<CachedTrack>, accountIdentity: String) {
        if (tracks.isEmpty()) return
        val pendingByTrack = getPendingCompanionTrackStatesForTracks(
            accountIdentity,
            tracks.map { it.ratingKey }
        ).groupBy { it.trackId }
        insertCachedTracks(tracks.map { incoming ->
            val pending = pendingByTrack[incoming.ratingKey].orEmpty().associateBy { it.field }
            incoming.copy(
                companionFavorite = pending[CompanionTrackStateField.FAVORITE]
                    ?.desiredValue?.toBooleanStrictOrNull() ?: incoming.companionFavorite,
                companionRating = pending[CompanionTrackStateField.RATING]
                    ?.desiredValue?.toIntOrNull() ?: incoming.companionRating
            )
        })
    }

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsertPendingCompanionTrackState(state: PendingCompanionTrackStateEntity)

    @Query("SELECT * FROM pending_companion_track_states WHERE accountIdentity = :accountIdentity ORDER BY updatedAt, trackId, field")
    suspend fun getPendingCompanionTrackStates(accountIdentity: String): List<PendingCompanionTrackStateEntity>

    @Query("SELECT * FROM pending_companion_track_states WHERE accountIdentity = :accountIdentity AND trackId IN (:trackIds)")
    suspend fun getPendingCompanionTrackStatesForTracks(
        accountIdentity: String,
        trackIds: List<String>
    ): List<PendingCompanionTrackStateEntity>

    @Query("DELETE FROM pending_companion_track_states WHERE accountIdentity = :accountIdentity AND trackId = :trackId AND field = :field AND mutationId = :mutationId")
    suspend fun deletePendingCompanionTrackState(
        accountIdentity: String,
        trackId: String,
        field: String,
        mutationId: String
    )

    @Query("UPDATE cached_tracks SET companionFavorite = :favorite WHERE ratingKey = :trackId")
    suspend fun setCachedCompanionFavorite(trackId: String, favorite: Boolean)

    @Query("UPDATE cached_tracks SET companionRating = :rating WHERE ratingKey = :trackId")
    suspend fun setCachedCompanionRating(trackId: String, rating: Int)

    @Transaction
    suspend fun enqueueCompanionTrackState(state: PendingCompanionTrackStateEntity) {
        upsertPendingCompanionTrackState(state)
        when (state.field) {
            CompanionTrackStateField.FAVORITE ->
                setCachedCompanionFavorite(state.trackId, state.desiredValue.toBooleanStrict())
            CompanionTrackStateField.RATING ->
                setCachedCompanionRating(state.trackId, state.desiredValue.toInt())
        }
    }

    @Query("SELECT COUNT(*) FROM pending_companion_track_states WHERE accountIdentity = :accountIdentity")
    suspend fun getPendingCompanionTrackStateCount(accountIdentity: String): Int

    @Query("SELECT * FROM cached_tracks WHERE ratingKey IN (:ratingKeys)")
    suspend fun getCachedTracksByRatingKeys(ratingKeys: List<String>): List<CachedTrack>

    @Query("UPDATE cached_tracks SET musicIntelligenceJson = :json WHERE ratingKey = :ratingKey")
    suspend fun updateCachedTrackMusicIntelligence(ratingKey: String, json: String)


    @Query("SELECT * FROM cached_tracks WHERE artist = :artistName OR albumArtist = :artistName")
    suspend fun getTracksByArtist(artistName: String): List<CachedTrack>

    @Query(
        """
        SELECT * FROM cached_tracks
        WHERE LOWER(TRIM(album)) = LOWER(TRIM(:albumName))
          AND (
              LOWER(TRIM(albumArtist)) = LOWER(TRIM(:artistName))
              OR TRIM(albumArtist) = ''
              OR LOWER(TRIM(artist)) = LOWER(TRIM(:artistName))
          )
        """
    )
    suspend fun getTracksByAlbum(artistName: String, albumName: String): List<CachedTrack>

    // Sync State
    @Query("SELECT * FROM sync_state WHERE sectionId = :sectionId")
    fun getSyncStateFlow(sectionId: String): Flow<SyncStateEntity?>

    @Query("SELECT * FROM sync_state WHERE sectionId = :sectionId")
    suspend fun getSyncState(sectionId: String): SyncStateEntity?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertSyncState(state: SyncStateEntity)

    @Query("DELETE FROM cached_tracks WHERE syncId != :syncId")
    suspend fun deleteStaleTracks(syncId: Long)

    @Query("SELECT COUNT(*) FROM cached_tracks")
    suspend fun getCachedTracksCount(): Int

    @Query("SELECT COUNT(*) FROM cached_tracks WHERE TRIM(albumArtist) = ''")
    suspend fun getMissingAlbumArtistCount(): Int

    @Query("SELECT COUNT(*) FROM cached_tracks WHERE thumb NOT LIKE 'http%' AND thumb != ''")
    suspend fun getNonCanonicalArtworkCount(): Int

    @Query(
        """
        SELECT COUNT(*) FROM cached_tracks
        WHERE TRIM(thumb) = ''
           OR LOWER(TRIM(thumb)) LIKE 'data:%'
           OR LOWER(TRIM(thumb)) LIKE 'spotcore-artwork:%'
           OR LOWER(TRIM(thumb)) LIKE 'file:%'
           OR LOWER(TRIM(thumb)) LIKE '%library/metadata/%'
           OR LOWER(TRIM(thumb)) LIKE '%/photo/:/transcode%'
        """
    )
    suspend fun getLegacyArtworkCount(): Int

    @Query(
        """
        SELECT COUNT(*) FROM (
            SELECT album, COALESCE(year, -1)
            FROM cached_tracks
            GROUP BY LOWER(TRIM(album)), COALESCE(year, -1)
            HAVING COUNT(DISTINCT LOWER(TRIM(albumArtist))) > 1
               AND SUM(CASE
                   WHEN TRIM(albumArtist) = '' OR LOWER(TRIM(albumArtist)) = LOWER(TRIM(artist)) THEN 0
                   ELSE 1
               END) = 0
        )
        """
    )
    suspend fun getSongArtistAlbumGroupCount(): Int

    // Recently played history
    @Query("SELECT recently_played.* FROM recently_played INNER JOIN cached_tracks ON cached_tracks.ratingKey = recently_played.ratingKey ORDER BY timestamp DESC LIMIT 30")
    fun getRecentlyPlayed(): Flow<List<RecentTrack>>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertRecentTrack(track: RecentTrack)

    @Query("DELETE FROM recently_played WHERE ratingKey = :ratingKey")
    suspend fun deleteRecentTrackByKey(ratingKey: String)

    // Playlists
    @Query("SELECT * FROM playlists ORDER BY createdAt DESC")
    fun getAllPlaylists(): Flow<List<PlaylistEntity>>

    @Query("SELECT * FROM playlists")
    suspend fun getAllPlaylistsList(): List<PlaylistEntity>

    @Query("SELECT * FROM playlists WHERE id = :playlistId LIMIT 1")
    suspend fun getPlaylist(playlistId: Int): PlaylistEntity?

    @Query("SELECT * FROM playlists WHERE remoteId = :remoteId LIMIT 1")
    suspend fun getPlaylistByRemoteId(remoteId: String): PlaylistEntity?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertPlaylist(playlist: PlaylistEntity): Long

    @Query("UPDATE playlists SET name = :name, remoteId = :remoteId, remoteUpdatedAt = :remoteUpdatedAt, syncState = :syncState WHERE id = :playlistId")
    suspend fun updatePlaylistSyncMetadata(
        playlistId: Int,
        name: String,
        remoteId: String?,
        remoteUpdatedAt: Long?,
        syncState: String
    )

    @Query("UPDATE playlists SET remoteUpdatedAt = :remoteUpdatedAt, syncState = :syncState WHERE id = :playlistId")
    suspend fun updatePlaylistSyncState(playlistId: Int, remoteUpdatedAt: Long?, syncState: String)

    @Query("DELETE FROM playlists WHERE id = :playlistId")
    suspend fun deletePlaylist(playlistId: Int)

    @Query("SELECT playlist_tracks.* FROM playlist_tracks WHERE playlistId = :playlistId ORDER BY addedAt ASC, id ASC")
    fun getTracksForPlaylist(playlistId: Int): Flow<List<PlaylistTrackEntity>>

    @Query("SELECT playlist_tracks.* FROM playlist_tracks WHERE playlistId = :playlistId ORDER BY addedAt ASC, id ASC")
    suspend fun getTracksForPlaylistList(playlistId: Int): List<PlaylistTrackEntity>

    @Query("SELECT COALESCE(MAX(addedAt), -1) + 1 FROM playlist_tracks WHERE playlistId = :playlistId")
    suspend fun getNextPlaylistPosition(playlistId: Int): Long

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertPlaylistTrack(track: PlaylistTrackEntity)

    @Query("UPDATE playlist_tracks SET addedAt = :position WHERE id = :trackId")
    suspend fun updatePlaylistTrackPosition(trackId: Int, position: Long)

    @Query("DELETE FROM playlist_tracks WHERE playlistId = :playlistId AND ratingKey = :ratingKey")
    suspend fun removeTrackFromPlaylist(playlistId: Int, ratingKey: String)

    @Query("DELETE FROM playlist_tracks WHERE playlistId = :playlistId")
    suspend fun clearPlaylistTracks(playlistId: Int)

    @Transaction
    suspend fun replacePlaylistTracks(playlistId: Int, tracks: List<PlaylistTrackEntity>) {
        clearPlaylistTracks(playlistId)
        tracks.forEach { insertPlaylistTrack(it.copy(id = 0, playlistId = playlistId)) }
    }

    // Downloads
    @Query("SELECT * FROM downloaded_tracks ORDER BY downloadedAt DESC")
    fun getAllDownloadedTracks(): Flow<List<DownloadedTrackEntity>>

    @Query("SELECT * FROM downloaded_tracks")
    suspend fun getDownloadedTracksList(): List<DownloadedTrackEntity>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertDownloadedTrack(track: DownloadedTrackEntity)

    /** Atomically prevents duplicate requests from replacing a completed or active download. */
    @Transaction
    suspend fun queueDownloadIfNeeded(track: DownloadedTrackEntity): Boolean {
        val current = getDownloadedTrack(track.ratingKey)
        if (current?.status == "completed" || current?.status?.let { it in setOf("queued", "downloading", "retrying") } == true) {
            return false
        }
        insertDownloadedTrack(track)
        return true
    }

    @Query("DELETE FROM downloaded_tracks WHERE ratingKey = :ratingKey")
    suspend fun deleteDownloadedTrack(ratingKey: String)

    @Query("SELECT EXISTS(SELECT 1 FROM downloaded_tracks WHERE ratingKey = :ratingKey)")
    fun isTrackDownloadedFlow(ratingKey: String): Flow<Boolean>

    @Query("SELECT EXISTS(SELECT 1 FROM downloaded_tracks WHERE ratingKey = :ratingKey AND status = 'completed')")
    suspend fun isTrackDownloaded(ratingKey: String): Boolean

    @Query("SELECT * FROM downloaded_tracks WHERE ratingKey = :ratingKey LIMIT 1")
    suspend fun getDownloadedTrack(ratingKey: String): DownloadedTrackEntity?

    @Query("UPDATE downloaded_tracks SET status = :status, errorMessage = :errorMessage WHERE ratingKey = :ratingKey")
    suspend fun updateDownloadStatus(ratingKey: String, status: String, errorMessage: String? = null)

    @Query("DELETE FROM downloaded_tracks")
    suspend fun clearDownloadedTracks()

    @Query("SELECT * FROM playback_state WHERE id = 1")
    suspend fun getPlaybackState(): PlaybackStateEntity?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun savePlaybackState(state: PlaybackStateEntity)

    @Query("SELECT * FROM listening_history WHERE ratingKey = :ratingKey LIMIT 1")
    suspend fun getListeningHistory(ratingKey: String): ListeningHistoryEntity?

    @Query("SELECT * FROM listening_history")
    fun getAllListeningHistory(): Flow<List<ListeningHistoryEntity>>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun saveListeningHistory(history: ListeningHistoryEntity)

    @Query("DELETE FROM listening_history WHERE ratingKey = :ratingKey")
    suspend fun deleteListeningHistoryByKey(ratingKey: String)

    @Insert(onConflict = OnConflictStrategy.IGNORE)
    suspend fun insertPendingCompanionPlay(event: PendingCompanionPlayEntity): Long

    @Query("SELECT * FROM pending_companion_plays WHERE accountIdentity = :accountIdentity ORDER BY playedAt ASC LIMIT :limit")
    suspend fun getPendingCompanionPlays(accountIdentity: String, limit: Int = 50): List<PendingCompanionPlayEntity>

    @Query("DELETE FROM pending_companion_plays WHERE eventId = :eventId")
    suspend fun deletePendingCompanionPlay(eventId: String)

    // Liked tracks
    @Query("SELECT * FROM liked_tracks ORDER BY likedAt DESC")
    fun getAllLikedTracks(): Flow<List<LikedTrackEntity>>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertLikedTrack(track: LikedTrackEntity)

    @Query("DELETE FROM liked_tracks WHERE ratingKey = :ratingKey")
    suspend fun deleteLikedTrack(ratingKey: String)

    @Query("SELECT EXISTS(SELECT 1 FROM liked_tracks WHERE ratingKey = :ratingKey)")
    fun isTrackLikedFlow(ratingKey: String): Flow<Boolean>

    @Query("SELECT EXISTS(SELECT 1 FROM liked_tracks WHERE ratingKey = :ratingKey)")
    suspend fun isTrackLiked(ratingKey: String): Boolean
}

@Database(
    entities = [
        CachedTrack::class,
        RecentTrack::class,
        PlaylistEntity::class,
        PlaylistTrackEntity::class,
        DownloadedTrackEntity::class,
        LikedTrackEntity::class,
        PlaybackStateEntity::class,
        ListeningHistoryEntity::class,
        PendingCompanionPlayEntity::class,
        PendingCompanionTrackStateEntity::class,
        SyncStateEntity::class
    ],
    version = 20,
    exportSchema = true
)
abstract class MusicDatabase : RoomDatabase() {
    abstract fun musicDao(): MusicDao

    companion object {
        const val DATABASE_NAME = "music_database"
        val MIGRATION_6_7 = object : Migration(6, 7) {
            override fun migrate(database: SupportSQLiteDatabase) {
                database.execSQL(
                    """CREATE TABLE IF NOT EXISTS listening_history (
                        ratingKey TEXT NOT NULL,
                        title TEXT NOT NULL,
                        artist TEXT NOT NULL,
                        album TEXT NOT NULL,
                        playCount INTEGER NOT NULL DEFAULT 0,
                        completedCount INTEGER NOT NULL DEFAULT 0,
                        skipCount INTEGER NOT NULL DEFAULT 0,
                        lastPlayedAt INTEGER,
                        lastCompletedAt INTEGER,
                        lastSkippedAt INTEGER,
                        PRIMARY KEY(ratingKey)
                    )""".trimIndent()
                )
            }
        }
        val MIGRATION_7_8 = object : Migration(7, 8) {
            override fun migrate(database: SupportSQLiteDatabase) = Unit
        }

        val MIGRATION_8_9 = object : Migration(8, 9) {
            override fun migrate(database: SupportSQLiteDatabase) {
                database.execSQL(
                    """CREATE TABLE IF NOT EXISTS pending_companion_plays (
                        eventId TEXT NOT NULL,
                        trackId TEXT NOT NULL,
                        playedAt INTEGER NOT NULL,
                        PRIMARY KEY(eventId)
                    )""".trimIndent()
                )
            }
        }

        val MIGRATION_9_10 = object : Migration(9, 10) {
            override fun migrate(database: SupportSQLiteDatabase) {
                database.execSQL("ALTER TABLE cached_tracks ADD COLUMN albumArtist TEXT NOT NULL DEFAULT ''")
            }
        }

        val MIGRATION_10_11 = object : Migration(10, 11) {
            override fun migrate(database: SupportSQLiteDatabase) {
                database.execSQL("ALTER TABLE recently_played ADD COLUMN albumArtist TEXT NOT NULL DEFAULT ''")
            }
        }

        val MIGRATION_11_12 = object : Migration(11, 12) {
            override fun migrate(database: SupportSQLiteDatabase) {
                database.execSQL("ALTER TABLE cached_tracks ADD COLUMN `index` INTEGER NOT NULL DEFAULT 0")
            }
        }

        val MIGRATION_12_13 = object : Migration(12, 13) {
            override fun migrate(database: SupportSQLiteDatabase) {
                database.execSQL("ALTER TABLE cached_tracks ADD COLUMN discNumber INTEGER NOT NULL DEFAULT 1")
            }
        }

        val MIGRATION_13_14 = object : Migration(13, 14) {
            override fun migrate(database: SupportSQLiteDatabase) {
                database.execSQL("ALTER TABLE playlists ADD COLUMN remoteId TEXT")
                database.execSQL("ALTER TABLE playlists ADD COLUMN remoteUpdatedAt INTEGER")
                database.execSQL("ALTER TABLE playlists ADD COLUMN syncState TEXT NOT NULL DEFAULT 'local'")
                database.execSQL("CREATE UNIQUE INDEX IF NOT EXISTS index_playlists_remoteId ON playlists(remoteId)")
            }
        }

        val MIGRATION_15_16 = object : Migration(15, 16) {
            override fun migrate(database: SupportSQLiteDatabase) {
                database.execSQL("ALTER TABLE pending_companion_plays ADD COLUMN accountIdentity TEXT")
            }
        }

        val MIGRATION_14_15 = object : Migration(14, 15) {
            override fun migrate(database: SupportSQLiteDatabase) {
                database.execSQL(
                    "CREATE INDEX IF NOT EXISTS index_cached_tracks_album_albumArtist " +
                        "ON cached_tracks(album, albumArtist)"
                )
                database.execSQL(
                    "CREATE INDEX IF NOT EXISTS index_cached_tracks_album_artist " +
                        "ON cached_tracks(album, artist)"
                )
            }
        }

        val MIGRATION_17_18 = object : Migration(17, 18) {
            override fun migrate(database: SupportSQLiteDatabase) {
                database.execSQL("ALTER TABLE cached_tracks ADD COLUMN musicIntelligenceJson TEXT NOT NULL DEFAULT '{}' ")
            }
        }

        val MIGRATION_18_19 = object : Migration(18, 19) {
            override fun migrate(database: SupportSQLiteDatabase) {
                database.execSQL("ALTER TABLE cached_tracks ADD COLUMN companionFavorite INTEGER NOT NULL DEFAULT 0")
                database.execSQL("ALTER TABLE cached_tracks ADD COLUMN companionRating INTEGER NOT NULL DEFAULT 0")
                database.execSQL(
                    "CREATE TABLE IF NOT EXISTS pending_companion_track_states (" +
                        "accountIdentity TEXT NOT NULL, trackId TEXT NOT NULL, field TEXT NOT NULL, " +
                        "desiredValue TEXT NOT NULL, mutationId TEXT NOT NULL, updatedAt INTEGER NOT NULL, " +
                        "PRIMARY KEY(accountIdentity, trackId, field))"
                )
                database.execSQL(
                    "CREATE INDEX IF NOT EXISTS index_pending_companion_track_states_accountIdentity_updatedAt " +
                        "ON pending_companion_track_states(accountIdentity, updatedAt)"
                )
            }
        }

        val MIGRATION_19_20 = object : Migration(19, 20) {
            override fun migrate(database: SupportSQLiteDatabase) {
                database.execSQL(
                    "ALTER TABLE playback_state ADD COLUMN playWhenReady INTEGER NOT NULL DEFAULT 0"
                )
            }
        }

        val MIGRATION_16_17 = object : Migration(16, 17) {
            override fun migrate(database: SupportSQLiteDatabase) {
                database.execSQL("ALTER TABLE pending_companion_plays ADD COLUMN listenedMs INTEGER")
            }
        }

        internal val ALL_MIGRATIONS: Array<Migration> = arrayOf(
            MIGRATION_6_7, MIGRATION_7_8, MIGRATION_8_9, MIGRATION_9_10,
            MIGRATION_10_11, MIGRATION_11_12, MIGRATION_12_13, MIGRATION_13_14,
            MIGRATION_14_15, MIGRATION_15_16, MIGRATION_16_17, MIGRATION_17_18,
            MIGRATION_18_19, MIGRATION_19_20
        )

        @Volatile
        private var INSTANCE: MusicDatabase? = null

        fun getDatabase(context: Context): MusicDatabase {
            return INSTANCE ?: synchronized(this) {
                Room.databaseBuilder(
                    context.applicationContext,
                    MusicDatabase::class.java,
                    DATABASE_NAME
                )
                    .addMigrations(*ALL_MIGRATIONS)
                    .build()
                    .also { INSTANCE = it }
            }
        }
    }
}

fun PlaylistTrackEntity.toTrackItem(): TrackItem {
    return TrackItem(
        ratingKey = this.ratingKey,
        title = this.title,
        artist = this.artist,
        album = this.album,
        key = this.key,
        thumb = this.thumb,
        duration = this.duration,
        albumRatingKey = albumNavigationKey(this.artist, this.album),
        artistRatingKey = artistNavigationKey(this.artist)
    )
}

fun DownloadedTrackEntity.toTrackItem(): TrackItem {
    return TrackItem(
        ratingKey = this.ratingKey,
        title = this.title,
        artist = this.artist,
        album = this.album,
        key = this.key,
        thumb = this.thumb,
        duration = this.duration,
        localPath = this.localPath,
        albumRatingKey = albumNavigationKey(this.artist, this.album),
        artistRatingKey = artistNavigationKey(this.artist)
    )
}

fun LikedTrackEntity.toTrackItem(): TrackItem {
    return TrackItem(
        ratingKey = this.ratingKey,
        title = this.title,
        artist = this.artist,
        album = this.album,
        key = this.key,
        thumb = this.thumb,
        duration = this.duration,
        albumRatingKey = albumNavigationKey(this.artist, this.album),
        artistRatingKey = artistNavigationKey(this.artist)
    )
}

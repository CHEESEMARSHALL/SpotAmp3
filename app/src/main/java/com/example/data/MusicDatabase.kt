package com.example.data

import android.content.Context
import androidx.room.*
import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase
import kotlinx.coroutines.flow.Flow

@Entity(tableName = "sync_state")
data class SyncStateEntity(
    @PrimaryKey val sectionId: String,
    val currentOffset: Int = 0,
    val totalTracks: Int = 0,
    val lastSyncId: Long = 0,
    val status: String = "idle",
    val lastError: String? = null
)

@Entity(tableName = "cached_tracks")
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
    val syncId: Long = 0 // Track which sync indexed this
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
    val timestamp: Long = System.currentTimeMillis()
)

@Entity(tableName = "playlists")
data class PlaylistEntity(
    @PrimaryKey(autoGenerate = true) val id: Int = 0,
    val name: String,
    val createdAt: Long = System.currentTimeMillis()
)

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

@Dao
interface MusicDao {
    // Cached tracks for AI indexing
    @Query("SELECT * FROM cached_tracks")
    fun getAllCachedTracks(): Flow<List<CachedTrack>>

    @Query("SELECT * FROM cached_tracks")
    suspend fun getCachedTracksList(): List<CachedTrack>

    @Query("SELECT * FROM cached_tracks WHERE title LIKE '%' || :query || '%' OR artist LIKE '%' || :query || '%' OR album LIKE '%' || :query || '%' LIMIT 100")
    suspend fun searchCachedTracks(query: String): List<CachedTrack>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertCachedTracks(tracks: List<CachedTrack>)

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

    // Recently played history
    @Query("SELECT * FROM recently_played ORDER BY timestamp DESC LIMIT 30")
    fun getRecentlyPlayed(): Flow<List<RecentTrack>>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertRecentTrack(track: RecentTrack)

    @Query("DELETE FROM recently_played WHERE ratingKey = :ratingKey")
    suspend fun deleteRecentTrackByKey(ratingKey: String)

    // Playlists
    @Query("SELECT * FROM playlists ORDER BY createdAt DESC")
    fun getAllPlaylists(): Flow<List<PlaylistEntity>>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertPlaylist(playlist: PlaylistEntity): Long

    @Query("DELETE FROM playlists WHERE id = :playlistId")
    suspend fun deletePlaylist(playlistId: Int)

    @Query("SELECT * FROM playlist_tracks WHERE playlistId = :playlistId ORDER BY addedAt ASC")
    fun getTracksForPlaylist(playlistId: Int): Flow<List<PlaylistTrackEntity>>

    @Query("SELECT * FROM playlist_tracks WHERE playlistId = :playlistId ORDER BY addedAt ASC")
    suspend fun getTracksForPlaylistList(playlistId: Int): List<PlaylistTrackEntity>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertPlaylistTrack(track: PlaylistTrackEntity)

    @Query("DELETE FROM playlist_tracks WHERE playlistId = :playlistId AND ratingKey = :ratingKey")
    suspend fun removeTrackFromPlaylist(playlistId: Int, ratingKey: String)

    @Query("DELETE FROM playlist_tracks WHERE playlistId = :playlistId")
    suspend fun clearPlaylistTracks(playlistId: Int)

    // Downloads
    @Query("SELECT * FROM downloaded_tracks ORDER BY downloadedAt DESC")
    fun getAllDownloadedTracks(): Flow<List<DownloadedTrackEntity>>

    @Query("SELECT * FROM downloaded_tracks")
    suspend fun getDownloadedTracksList(): List<DownloadedTrackEntity>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertDownloadedTrack(track: DownloadedTrackEntity)

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

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun saveListeningHistory(history: ListeningHistoryEntity)

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
        SyncStateEntity::class
    ],
    version = 8,
    exportSchema = false
)
abstract class MusicDatabase : RoomDatabase() {
    abstract fun musicDao(): MusicDao

    companion object {
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

        @Volatile
        private var INSTANCE: MusicDatabase? = null

        fun getDatabase(context: Context): MusicDatabase {
            return INSTANCE ?: synchronized(this) {
                val instance = Room.databaseBuilder(
                    context.applicationContext,
                    MusicDatabase::class.java,
                    "music_database"
                )
                .addMigrations(MIGRATION_6_7)
                .fallbackToDestructiveMigrationFrom(1, 2, 3, 4, 5)
                .build()
                INSTANCE = instance
                instance
            }
        }
    }
}

fun PlaylistTrackEntity.toTrackItem(): com.example.playback.TrackItem {
    return com.example.playback.TrackItem(
        ratingKey = this.ratingKey,
        title = this.title,
        artist = this.artist,
        album = this.album,
        key = this.key,
        thumb = this.thumb,
        duration = this.duration
    )
}

fun DownloadedTrackEntity.toTrackItem(): com.example.playback.TrackItem {
    return com.example.playback.TrackItem(
        ratingKey = this.ratingKey,
        title = this.title,
        artist = this.artist,
        album = this.album,
        key = this.key,
        thumb = this.thumb,
        duration = this.duration,
        localPath = this.localPath
    )
}

fun LikedTrackEntity.toTrackItem(): com.example.playback.TrackItem {
    return com.example.playback.TrackItem(
        ratingKey = this.ratingKey,
        title = this.title,
        artist = this.artist,
        album = this.album,
        key = this.key,
        thumb = this.thumb,
        duration = this.duration
    )
}

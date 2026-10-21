package com.example.data

import android.content.Context
import android.os.Build
import android.util.Log
import com.example.R
import com.example.playback.TrackItem
import com.example.sync.PlaylistSyncScheduler
import com.example.sync.spotCoreTrackId
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.FlowPreview
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.debounce
import kotlinx.coroutines.withContext
import okhttp3.HttpUrl.Companion.toHttpUrlOrNull
import okhttp3.Request
import retrofit2.HttpException
import java.io.File
import java.nio.file.Files
import java.nio.file.StandardCopyOption
import java.util.UUID
import java.util.concurrent.TimeUnit

internal fun <T> deterministicTrackSample(items: List<T>, maxItems: Int): List<T> {
    if (maxItems <= 0 || items.isEmpty()) return emptyList()
    val ordered = items.toList()
    if (ordered.size <= maxItems) return ordered
    if (maxItems == 1) return listOf(ordered.first())
    return (0 until maxItems).map { index ->
        ordered[index * ordered.lastIndex / (maxItems - 1)]
    }
}

internal fun companionPlaylistCreateMutationId(localPlaylistId: Int, createdAt: Long): String =
    "spotamp-playlist:$localPlaylistId:$createdAt"

class MusicRepository(
    private val context: Context,
    private val musicDao: MusicDao = MusicDatabase.getDatabase(context).musicDao()
) {
    val settings = PlexSettingsManager(context)
    private val downloadClient by lazy {
        okhttp3.OkHttpClient.Builder()
            .connectTimeout(30, TimeUnit.SECONDS)
            .readTimeout(120, TimeUnit.SECONDS)
            .writeTimeout(30, TimeUnit.SECONDS)
            .followRedirects(false)
            .followSslRedirects(false)
            .addInterceptor(QaNetworkInterceptor())
            .build()
    }

    // Flow for recently played tracks
    val recentlyPlayed: Flow<List<RecentTrack>> = musicDao.getRecentlyPlayed()

    // Actual per-track app listening totals, used for album-level statistics.
    val listeningHistory: Flow<List<ListeningHistoryEntity>> = musicDao.getAllListeningHistory()

    // Flow for cached tracks count (to see if indexed)
    // A sync inserts a page at a time.  Do not make Compose reload the entire
    // library for every page; large libraries otherwise spend the whole sync
    // repeatedly materializing and recomposing the same list.
    @OptIn(FlowPreview::class)
    val cachedTracksCount: Flow<List<CachedTrack>> = musicDao.getAllCachedTracks()
        .debounce(1500)

    fun getSyncStateFlow(sectionId: String): Flow<SyncStateEntity?> {
        return musicDao.getSyncStateFlow(sectionId)
    }

    suspend fun getCachedCount(): Int = withContext(Dispatchers.IO) {
        musicDao.getCachedTracksCount()
    }

    suspend fun hasLegacyAlbumArtistRows(): Boolean = withContext(Dispatchers.IO) {
        musicDao.getMissingAlbumArtistCount() > 0 || musicDao.getSongArtistAlbumGroupCount() > 0
    }

    suspend fun hasNonCanonicalCacheRows(): Boolean = withContext(Dispatchers.IO) {
        musicDao.getMissingAlbumArtistCount() > 0 ||
            musicDao.getSongArtistAlbumGroupCount() > 0 ||
            musicDao.getNonCanonicalArtworkCount() > 0
    }

    suspend fun getCachedTracksList(): List<CachedTrack> = withContext(Dispatchers.IO) {
        musicDao.getCachedTracksList()
    }

    suspend fun setCompanionFavorite(trackId: String, favorite: Boolean) = withContext(Dispatchers.IO) {
        queueCompanionTrackState(trackId, CompanionTrackStateField.FAVORITE, favorite.toString())
    }

    suspend fun setCompanionRating(trackId: String, rating: Int) = withContext(Dispatchers.IO) {
        require(rating in 0..5) { "SpotCore rating must be between 0 and 5" }
        queueCompanionTrackState(trackId, CompanionTrackStateField.RATING, rating.toString())
    }

    private suspend fun queueCompanionTrackState(trackId: String, field: String, value: String) {
        require(settings.isCompanionConfigured) { "SpotCore Companion is not configured" }
        val normalizedTrackId = trackId.trim()
        require(normalizedTrackId.isNotEmpty()) { "SpotCore track ID is required" }
        val identity = SyncAccountIsolationPolicy.identity(
            settings.companionBackendUrl,
            settings.companionDeviceId
        ) ?: throw IllegalStateException("SpotCore paired-device identity is unavailable")
        musicDao.enqueueCompanionTrackState(
            PendingCompanionTrackStateEntity(
                accountIdentity = identity.storageKey(),
                trackId = normalizedTrackId,
                field = field,
                desiredValue = value,
                mutationId = UUID.randomUUID().toString()
            )
        )
        CompanionTrackStateSyncScheduler.enqueue(context)
    }

    /**
     * Applies the active account's durable outbox. The desired-value POSTs are idempotent;
     * an ambiguous timeout leaves the same mutation queued for a safe retry.
     */
    internal suspend fun syncPendingCompanionTrackStates(): Boolean = withContext(Dispatchers.IO) {
        if (!settings.isCompanionConfigured) return@withContext true
        val workerIdentity = SyncAccountIsolationPolicy.identity(
            settings.companionBackendUrl,
            settings.companionDeviceId
        ) ?: return@withContext true
        val accountKey = workerIdentity.storageKey()
        val pending = musicDao.getPendingCompanionTrackStates(accountKey)
        if (pending.isEmpty()) return@withContext true

        val companion = BackendClientManager.getApiService(settings.companionBackendUrl)
        for (mutation in pending) {
            val currentIdentity = SyncAccountIsolationPolicy.identity(
                settings.companionBackendUrl,
                settings.companionDeviceId
            )
            if (currentIdentity != workerIdentity) return@withContext true

            when (mutation.field) {
                CompanionTrackStateField.FAVORITE -> companion.setLibraryTrackFavorite(
                    trackId = mutation.trackId,
                    request = BackendFavoriteRequest(mutation.desiredValue.toBooleanStrict()),
                    userAuthToken = "Bearer ${settings.companionBackendToken}"
                )
                CompanionTrackStateField.RATING -> companion.setLibraryTrackRating(
                    trackId = mutation.trackId,
                    request = BackendRatingRequest(mutation.desiredValue.toInt().also { require(it in 0..5) }),
                    userAuthToken = "Bearer ${settings.companionBackendToken}"
                )
                else -> throw IllegalStateException("Unknown pending SpotCore track-state field")
            }

            val identityAfterWrite = SyncAccountIsolationPolicy.identity(
                settings.companionBackendUrl,
                settings.companionDeviceId
            )
            if (identityAfterWrite != workerIdentity) return@withContext true
            // A newer local edit may have replaced this account/track/field row
            // while the request was in flight. Delete only the acknowledged ID.
            musicDao.deletePendingCompanionTrackState(
                accountIdentity = accountKey,
                trackId = mutation.trackId,
                field = mutation.field,
                mutationId = mutation.mutationId
            )
        }
        true
    }

    suspend fun isKnownAnalysisLabelQuery(query: String): Boolean = withContext(Dispatchers.IO) {
        SmartSearchService().isKnownAnalysisLabelQuery(query, musicDao.getCachedTracksList())
    }

    // Playlists
    val playlists: Flow<List<PlaylistEntity>> = musicDao.getAllPlaylists()

    suspend fun createPlaylist(name: String): Long = withContext(Dispatchers.IO) {
        val id = musicDao.insertPlaylist(PlaylistEntity(name = name))
        if (settings.isCompanionConfigured) {
            runCatching { sharePlaylistInternal(id.toInt()) }
                .onFailure { Log.w("MusicRepository", "New playlist saved locally; SpotCore share will need retry (${it::class.simpleName})") }
        }
        id
    }

    suspend fun deletePlaylist(playlistId: Int): Boolean = withContext(Dispatchers.IO) {
        val local = musicDao.getPlaylist(playlistId)
        val remoteId = local?.remoteId
        if (remoteId != null) {
            if (!settings.isCompanionConfigured) return@withContext false
            try {
                companionService().deletePlaylist(remoteId, BackendPlaylistRevisionRequest(local.remoteUpdatedAt), companionPlexToken(), companionAuthorization())
            } catch (error: CancellationException) {
                throw error
            } catch (error: HttpException) {
                if (error.code() == 409) markPlaylistConflict(local, null, companionService())
                Log.e("MusicRepository", "Could not delete shared playlist from SpotCore (HTTP ${error.code()})")
                return@withContext false
            } catch (error: Exception) {
                Log.e("MusicRepository", "Could not delete shared playlist from SpotCore (${error::class.simpleName})")
                return@withContext false
            }
        }
        musicDao.deletePlaylist(playlistId)
        musicDao.clearPlaylistTracks(playlistId)
        true
    }

    suspend fun sharePlaylist(playlistId: Int): Boolean = withContext(Dispatchers.IO) {
        runCatching { sharePlaylistInternal(playlistId) }
            .onFailure { Log.e("MusicRepository", "Could not share playlist with SpotCore (${it::class.simpleName})") }
            .getOrDefault(false)
    }

    suspend fun syncSharedPlaylists() = withContext(Dispatchers.IO) {
        if (!settings.isCompanionConfigured) return@withContext
        val service = companionService()
        val remotePlaylists = service.getPlaylists(companionPlexToken(), companionAuthorization()).playlists.orEmpty()
        val remoteById = remotePlaylists.associateBy { it.id }
        val localPlaylists = musicDao.getAllPlaylistsList()

        remotePlaylists.forEach { remote ->
            val local = musicDao.getPlaylistByRemoteId(remote.id)
            if (local == null) {
                val localId = musicDao.insertPlaylist(
                    PlaylistEntity(
                        name = remote.name,
                        createdAt = remote.createdAt ?: System.currentTimeMillis(),
                        remoteId = remote.id,
                        remoteUpdatedAt = remote.updatedAt,
                        syncState = PlaylistSyncState.SYNCED
                    )
                ).toInt()
                syncRemotePlaylist(localId, remote.id, service)
            } else {
                when (local.syncState) {
                    PlaylistSyncState.DIRTY -> {
                        if (remote.updatedAt != null && remote.updatedAt != local.remoteUpdatedAt) {
                            markPlaylistConflict(local, remote.updatedAt, service)
                        } else {
                            pushPlaylistInternal(local.id, service)
                        }
                    }
                    PlaylistSyncState.CONFLICT -> Unit
                    else -> {
                        if (local.remoteUpdatedAt == null || remote.updatedAt != local.remoteUpdatedAt) {
                            syncRemotePlaylist(local.id, remote.id, service)
                        }
                    }
                }
            }
        }

        localPlaylists
            .filter { it.remoteId != null && it.remoteId !in remoteById.keys }
            .forEach { local ->
                if (local.syncState == PlaylistSyncState.SYNCED) {
                    musicDao.deletePlaylist(local.id)
                    musicDao.clearPlaylistTracks(local.id)
                } else if (local.syncState != PlaylistSyncState.CONFLICT) {
                    musicDao.updatePlaylistSyncState(local.id, local.remoteUpdatedAt, PlaylistSyncState.CONFLICT)
                }
            }
    }

    suspend fun refreshSharedPlaylist(playlistId: Int) = withContext(Dispatchers.IO) {
        val local = musicDao.getPlaylist(playlistId) ?: return@withContext
        val remoteId = local.remoteId ?: return@withContext
        if (!settings.isCompanionConfigured) return@withContext
        syncRemotePlaylist(playlistId, remoteId, companionService())
    }

    suspend fun keepLocalPlaylistChangesAfterConflict(playlistId: Int): Boolean = withContext(Dispatchers.IO) {
        val local = musicDao.getPlaylist(playlistId) ?: return@withContext false
        val remoteId = local.remoteId ?: return@withContext false
        if (local.syncState != PlaylistSyncState.CONFLICT || !settings.isCompanionConfigured) return@withContext false
        val service = companionService()
        val current = try {
            service.getPlaylist(remoteId, companionPlexToken(), companionAuthorization()).playlist
        } catch (error: CancellationException) {
            throw error
        } catch (error: Exception) {
            Log.w("MusicRepository", "Could not read the current SpotCore playlist revision (${error::class.simpleName})")
            return@withContext false
        } ?: return@withContext false
        val revision = current.updatedAt ?: return@withContext false
        musicDao.updatePlaylistSyncMetadata(
            playlistId = local.id,
            name = local.name,
            remoteId = remoteId,
            remoteUpdatedAt = revision,
            syncState = PlaylistSyncState.DIRTY
        )
        pushPlaylistInternal(playlistId, service)
    }

    fun getPlaylistTracks(playlistId: Int): Flow<List<PlaylistTrackEntity>> {
        return musicDao.getTracksForPlaylist(playlistId)
    }

    suspend fun getPlaylistTracksList(playlistId: Int): List<PlaylistTrackEntity> = withContext(Dispatchers.IO) {
        musicDao.getTracksForPlaylistList(playlistId)
    }

    suspend fun addTrackToPlaylist(playlistId: Int, track: TrackItem) = withContext(Dispatchers.IO) {
        val position = musicDao.getNextPlaylistPosition(playlistId)
        musicDao.insertPlaylistTrack(
            PlaylistTrackEntity(
                playlistId = playlistId,
                ratingKey = track.ratingKey,
                title = track.title,
                artist = track.artist,
                album = track.album,
                key = track.key,
                thumb = track.thumb,
                duration = track.duration,
                addedAt = position
            )
        )
        if (musicDao.getPlaylist(playlistId)?.remoteId != null) {
            musicDao.updatePlaylistSyncState(playlistId, musicDao.getPlaylist(playlistId)?.remoteUpdatedAt, PlaylistSyncState.DIRTY)
            if (settings.isCompanionConfigured) pushPlaylistInternal(playlistId, companionService())
        }
    }

    suspend fun removeTrackFromPlaylist(playlistId: Int, ratingKey: String) = withContext(Dispatchers.IO) {
        musicDao.removeTrackFromPlaylist(playlistId, ratingKey)
        if (musicDao.getPlaylist(playlistId)?.remoteId != null) {
            musicDao.updatePlaylistSyncState(playlistId, musicDao.getPlaylist(playlistId)?.remoteUpdatedAt, PlaylistSyncState.DIRTY)
            if (settings.isCompanionConfigured) pushPlaylistInternal(playlistId, companionService())
        }
    }

    suspend fun reorderPlaylistTracks(tracks: List<PlaylistTrackEntity>) = withContext(Dispatchers.IO) {
        tracks.forEachIndexed { index, track ->
            musicDao.updatePlaylistTrackPosition(track.id, index.toLong())
        }
        tracks.firstOrNull()?.let { first ->
            val playlist = musicDao.getPlaylist(first.playlistId)
            if (playlist?.remoteId != null) {
                musicDao.updatePlaylistSyncState(playlist.id, playlist.remoteUpdatedAt, PlaylistSyncState.DIRTY)
                if (settings.isCompanionConfigured) pushPlaylistInternal(playlist.id, companionService())
            }
        }
    }

    suspend fun renamePlaylist(playlistId: Int, name: String) = withContext(Dispatchers.IO) {
        val local = musicDao.getPlaylist(playlistId) ?: return@withContext
        val remoteId = local.remoteId
        if (remoteId == null) {
            musicDao.updatePlaylistSyncMetadata(
                playlistId = local.id,
                name = name,
                remoteId = null,
                remoteUpdatedAt = local.remoteUpdatedAt,
                syncState = local.syncState
            )
            return@withContext
        }

        val dirty = local.copy(name = name, syncState = PlaylistSyncState.DIRTY)
        musicDao.updatePlaylistSyncMetadata(
            playlistId = local.id,
            name = name,
            remoteId = remoteId,
            remoteUpdatedAt = local.remoteUpdatedAt,
            syncState = PlaylistSyncState.DIRTY
        )
        if (!settings.isCompanionConfigured) {
            PlaylistSyncScheduler.enqueue(context)
            return@withContext
        }

        val service = companionService()
        try {
            val remote = service
                .renamePlaylist(
                    remoteId,
                    BackendRenamePlaylistRequest(name = name, baseUpdatedAt = local.remoteUpdatedAt),
                    companionPlexToken(),
                    companionAuthorization()
                )
                .playlist
                ?: throw IllegalStateException("SpotCore did not return the renamed playlist")
            applyRemotePlaylist(local.id, remote)
        } catch (error: HttpException) {
            if (error.code() == 409) {
                markPlaylistConflict(dirty, null, service)
            } else {
                PlaylistSyncScheduler.enqueue(context)
                Log.w("MusicRepository", "SpotCore playlist rename failed with HTTP ${error.code()}")
            }
        } catch (error: Exception) {
            PlaylistSyncScheduler.enqueue(context)
            Log.w("MusicRepository", "SpotCore playlist rename deferred (${error::class.simpleName})")
        }
    }

    private fun companionService(): BackendApiService {
        if (!settings.isCompanionConfigured) throw IllegalStateException("SpotCore Companion is not configured")
        return BackendClientManager.getApiService(settings.companionBackendUrl)
    }

    // A SpotCore device credential is never a Plex credential. Keep the legacy
    // Retrofit parameter empty so it cannot be serialized as X-Plex-Token.
    private fun companionPlexToken(): String = ""

    private fun companionAuthorization(): String = "Bearer ${settings.companionBackendToken}"

    private suspend fun sharePlaylistInternal(playlistId: Int): Boolean {
        val local = musicDao.getPlaylist(playlistId) ?: return false
        if (local.remoteId != null) return pushPlaylistInternal(playlistId, companionService())

        val remote = companionService()
            .createPlaylist(BackendCreatePlaylistRequest(local.name, companionPlaylistCreateMutationId(local.id, local.createdAt)), companionPlexToken(), companionAuthorization())
            .playlist
            ?: throw IllegalStateException("SpotCore did not return the created playlist")
        musicDao.updatePlaylistSyncMetadata(
            playlistId = local.id,
            name = remote.name,
            remoteId = remote.id,
            remoteUpdatedAt = remote.updatedAt,
            syncState = PlaylistSyncState.SYNCED
        )
        return if (musicDao.getTracksForPlaylistList(playlistId).isEmpty()) {
            true
        } else {
            pushPlaylistInternal(playlistId, companionService())
        }
    }

    private suspend fun pushPlaylistInternal(playlistId: Int, service: BackendApiService): Boolean {
        val local = musicDao.getPlaylist(playlistId) ?: return false
        val remoteId = local.remoteId ?: return false
        val tracks = musicDao.getTracksForPlaylistList(playlistId)
        val trackIds = tracks.map { track ->
            track.toTrackItem().spotCoreTrackId()
        }
        if (trackIds.any { it == null }) {
            Log.w(
                "MusicRepository",
                "SpotCore playlist sync skipped because it contains a track that was not sourced from SpotCore"
            )
            return false
        }
        musicDao.updatePlaylistSyncState(playlistId, local.remoteUpdatedAt, PlaylistSyncState.DIRTY)
        return try {
            val remote = service.replacePlaylist(
                remoteId,
                BackendReplacePlaylistRequest(
                    name = local.name,
                    trackIds = trackIds.filterNotNull(),
                    baseUpdatedAt = local.remoteUpdatedAt
                ),
                companionPlexToken(),
                companionAuthorization()
            ).playlist ?: throw IllegalStateException("SpotCore did not return the updated playlist")
            applyRemotePlaylist(playlistId, remote)
            true
        } catch (error: HttpException) {
            if (error.code() == 409) {
                markPlaylistConflict(local, local.remoteUpdatedAt, service)
            } else {
                Log.w("MusicRepository", "SpotCore playlist write failed with HTTP ${error.code()}")
                PlaylistSyncScheduler.enqueue(context)
            }
            false
        } catch (error: Exception) {
            Log.w("MusicRepository", "SpotCore playlist write deferred (${error::class.simpleName})")
            PlaylistSyncScheduler.enqueue(context)
            false
        }
    }

    private suspend fun markPlaylistConflict(local: PlaylistEntity, remoteRevision: Long?, service: BackendApiService) {
        val revision = runCatching {
            service.getPlaylist(local.remoteId ?: return@runCatching null, companionPlexToken(), companionAuthorization())
                .playlist?.updatedAt
        }.getOrNull() ?: remoteRevision
        musicDao.updatePlaylistSyncState(local.id, revision, PlaylistSyncState.CONFLICT)
    }

    private suspend fun syncRemotePlaylist(playlistId: Int, remoteId: String, service: BackendApiService) {
        val remote = service.getPlaylist(remoteId, companionPlexToken(), companionAuthorization()).playlist
            ?: throw IllegalStateException("SpotCore did not return playlist $remoteId")
        applyRemotePlaylist(playlistId, remote)
    }

    private suspend fun applyRemotePlaylist(playlistId: Int, remote: BackendPlaylistDto) {
        val sourceTracks = remote.tracks
        val orderedTracks = if (sourceTracks.any { it.position != null }) {
            sourceTracks.withIndex()
                .sortedWith(compareBy({ it.value.position ?: Int.MAX_VALUE }, { it.index }))
                .map { it.value }
        } else {
            sourceTracks
        }
        val tracks = orderedTracks.mapIndexed { index, track -> track.toPlaylistTrack(playlistId, index) }
        musicDao.replacePlaylistTracks(playlistId, tracks)
        musicDao.updatePlaylistSyncMetadata(
            playlistId = playlistId,
            name = remote.name,
            remoteId = remote.id,
            remoteUpdatedAt = remote.updatedAt ?: System.currentTimeMillis(),
            syncState = PlaylistSyncState.SYNCED
        )
    }

    private fun BackendTrackDto.toPlaylistTrack(playlistId: Int, position: Int): PlaylistTrackEntity {
        return PlaylistTrackEntity(
            playlistId = playlistId,
            ratingKey = id,
            title = title,
            artist = artist,
            album = album,
            key = resolveCompanionMediaReference(settings.companionBackendUrl, streamUrl),
            thumb = normalizeCompanionArtworkUrl(
                companionBaseUrl = settings.companionBackendUrl,
                trackId = id,
                coverUrl = coverUrl
            ),
            duration = duration.toLong(),
            addedAt = position.toLong()
        )
    }

    // Downloads
    val downloadedTracks: Flow<List<DownloadedTrackEntity>> = musicDao.getAllDownloadedTracks()

    suspend fun isTrackDownloaded(ratingKey: String): Boolean = withContext(Dispatchers.IO) {
        musicDao.isTrackDownloaded(ratingKey)
    }

    suspend fun isDownloadInFlight(ratingKey: String): Boolean = withContext(Dispatchers.IO) {
        musicDao.getDownloadedTrack(ratingKey)?.status in setOf("queued", "downloading", "retrying")
    }

    // Liked Songs
    val likedTracks: Flow<List<LikedTrackEntity>> = musicDao.getAllLikedTracks()

    suspend fun isTrackLiked(ratingKey: String): Boolean = withContext(Dispatchers.IO) {
        musicDao.isTrackLiked(ratingKey)
    }

    fun isTrackLikedFlow(ratingKey: String): Flow<Boolean> {
        return musicDao.isTrackLikedFlow(ratingKey)
    }

    suspend fun addLikedTrack(track: TrackItem) = withContext(Dispatchers.IO) {
        musicDao.insertLikedTrack(
            LikedTrackEntity(
                ratingKey = track.ratingKey,
                title = track.title,
                artist = track.artist,
                album = track.album,
                key = track.key,
                thumb = track.thumb,
                duration = track.duration
            )
        )
    }

    suspend fun deleteLikedTrack(ratingKey: String) = withContext(Dispatchers.IO) {
        musicDao.deleteLikedTrack(ratingKey)
    }

    suspend fun addDownloadedTrack(track: TrackItem, fileSize: Long, localPath: String) = withContext(Dispatchers.IO) {
        musicDao.insertDownloadedTrack(
            DownloadedTrackEntity(
                ratingKey = track.ratingKey,
                title = track.title,
                artist = track.artist,
                album = track.album,
                key = track.key,
                thumb = track.thumb,
                duration = track.duration,
                fileSize = fileSize,
                localPath = localPath
            )
        )
    }

    suspend fun deleteDownloadedTrack(ratingKey: String) = withContext(Dispatchers.IO) {
        musicDao.getDownloadedTrack(ratingKey)?.localPath?.let { File(it).delete() }
        musicDao.deleteDownloadedTrack(ratingKey)
    }

    suspend fun deleteAllDownloads() = withContext(Dispatchers.IO) {
        musicDao.getDownloadedTracksList().forEach { it.localPath?.let { path -> File(path).delete() } }
        musicDao.clearDownloadedTracks()
    }

    suspend fun queueDownload(track: TrackItem): Boolean = withContext(Dispatchers.IO) {
        musicDao.queueDownloadIfNeeded(
            DownloadedTrackEntity(
                ratingKey = track.ratingKey,
                title = track.title,
                artist = track.artist,
                album = track.album,
                key = track.key,
                thumb = track.thumb,
                duration = track.duration,
                fileSize = 0L,
                status = "queued"
            )
        )
    }

    suspend fun markDownloadStatus(ratingKey: String, status: String, errorMessage: String? = null) = withContext(Dispatchers.IO) {
        musicDao.updateDownloadStatus(ratingKey, status, TokenSafety.redact(errorMessage))
    }

    suspend fun downloadTrack(track: TrackItem, onProgress: (Float) -> Unit = {}): DownloadedTrackEntity = withContext(Dispatchers.IO) {
        if (!settings.isPlexConfigured && !settings.isCompanionConfigured) throw IllegalStateException("No music backend is configured.")
        val targetDir = context.getExternalFilesDir("music") ?: File(context.filesDir, "music")
        targetDir.mkdirs()
        val fileStem = DownloadPathPolicy.fileStem(track.ratingKey)
            ?: throw IllegalArgumentException("Track identifier is invalid.")
        val lastPathSegment = track.key.substringBefore('?').substringBefore('#').substringAfterLast('/')
        val extension = lastPathSegment
            .substringAfterLast('.', "")
            .lowercase()
            .takeIf { it.matches(Regex("[a-z0-9]{2,5}")) }
            ?: "mp3"
        val target = File(targetDir, "$fileStem.$extension")
        val temporary = File(targetDir, ".$fileStem.$extension.part")
        val isAbsoluteUrl = track.key.startsWith("http://") || track.key.startsWith("https://")
        val downloadBaseUrl = if (track.spotCoreTrackId() != null && settings.companionBackendUrl.isNotBlank()) settings.companionBackendUrl else settings.baseUrl
        val url = if (isAbsoluteUrl) track.key else resolveCompanionMediaReference(downloadBaseUrl, track.key)
        var completed = false
        try {
            executeDownloadRequest(url).use { response ->
                if (!response.isSuccessful) throw IllegalStateException("Music server returned HTTP ${response.code} for the download.")
                val body = response.body ?: throw IllegalStateException("Music server returned an empty audio file.")
                val total = body.contentLength()
                body.byteStream().use { input -> temporary.outputStream().use { output ->
                    val buffer = ByteArray(DEFAULT_BUFFER_SIZE)
                    var copied = 0L
                    var lastReportedPercent = -1
                    while (true) {
                        val read = input.read(buffer)
                        if (read < 0) break
                        output.write(buffer, 0, read)
                        copied += read
                        if (total > 0) {
                            val fraction = (copied.toFloat() / total).coerceIn(0f, 1f)
                            val percent = (fraction * 100).toInt()
                            if (percent != lastReportedPercent) {
                                lastReportedPercent = percent
                                onProgress(fraction)
                            }
                        }
                    }
                }}
            }
            if (!temporary.exists() || temporary.length() == 0L) error("Downloaded audio file is empty")
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                runCatching {
                    Files.move(temporary.toPath(), target.toPath(), StandardCopyOption.REPLACE_EXISTING, StandardCopyOption.ATOMIC_MOVE)
                }.getOrElse {
                    Files.move(temporary.toPath(), target.toPath(), StandardCopyOption.REPLACE_EXISTING)
                }
            } else {
                // java.nio.file is unavailable below API 26. The body is complete here,
                // so replace the target only after a successful download.
                if (target.exists() && !target.delete()) {
                    error("Unable to replace existing downloaded file.")
                }
                if (!temporary.renameTo(target)) {
                    error("Unable to promote downloaded file.")
                }
            }
            addDownloadedTrack(track, target.length(), target.absolutePath)
            completed = true
            musicDao.getDownloadedTrack(track.ratingKey) ?: error("Downloaded track was not indexed")
        } finally {
            if (!completed) temporary.delete()
        }
    }

    /** Follows media redirects one hop at a time and re-evaluates credentials for each origin. */
    private fun executeDownloadRequest(initialUrl: String): okhttp3.Response {
        var currentUrl = initialUrl.toHttpUrlOrNull()
            ?: throw IllegalArgumentException("Music server returned an invalid download URL.")

        repeat(MAX_DOWNLOAD_REDIRECTS + 1) { redirectCount ->
            val credentials = originCredentialsForUrl(
                currentUrl,
                settings.companionBackendUrl,
                settings.companionBackendToken,
                settings.baseUrl,
                settings.token
            )
            val requestBuilder = Request.Builder().url(currentUrl)
            credentials.headers.forEach { (name, value) -> requestBuilder.header(name, value) }
            val response = downloadClient.newCall(requestBuilder.build()).execute()
            val location = response.header("Location")
            val isRedirect = response.code in REDIRECT_STATUS_CODES && !location.isNullOrBlank()
            if (!isRedirect) return response

            val redirectedUrl = response.request.url.resolve(location!!)
            response.close()
            if (redirectedUrl == null) {
                throw IllegalStateException("Music server returned an invalid download redirect.")
            }
            if (currentUrl.isHttps && !redirectedUrl.isHttps) {
                throw IllegalStateException("Music server redirected the download to an insecure connection.")
            }
            if (redirectCount == MAX_DOWNLOAD_REDIRECTS) {
                throw IllegalStateException("Music server redirected the download too many times.")
            }
            currentUrl = redirectedUrl
        }

        throw IllegalStateException("Music server redirected the download too many times.")
    }

    private companion object {
        const val MAX_DOWNLOAD_REDIRECTS = 5
        val REDIRECT_STATUS_CODES = setOf(300, 301, 302, 303, 307, 308)
    }

    private fun getService(): PlexApiService {
        if (!settings.isPlexConfigured) {
            throw IllegalStateException("Plex server URL and token are not configured.")
        }
        return PlexClientManager.getApiService(settings.baseUrl)
    }

    suspend fun getLibraries(): List<PlexDirectory> = withContext(Dispatchers.IO) {
        try {
            val response = getService().getLibraries(settings.token)
            val directories = response.mediaContainer.directory.orEmpty()
            Log.d("MusicRepository", "Plex library response parsed: ${directories.size} sections")
            // Plex normally reports music sections as type "artist". Some
            // compatible servers expose the same section as "music".
            directories.musicLibraries()
        } catch (e: Exception) {
            Log.e("MusicRepository", "Error fetching libraries (${e::class.simpleName})")
            // Preserve the failure so the UI can distinguish an unreachable
            // server from a reachable server with no music sections.
            throw e
        }
    }

    suspend fun getArtists(sectionId: String): List<PlexMetadata> = withContext(Dispatchers.IO) {
        if (settings.isCompanionConfigured && sectionId == "spotcore") {
            return@withContext getCompanionArtists()
        }
        val response = getService().getLibraryItems(sectionId, "8", token = settings.token)
        response.mediaContainer.metadata ?: emptyList()
    }

    suspend fun getAlbums(sectionId: String): List<PlexMetadata> = withContext(Dispatchers.IO) {
        if (settings.isCompanionConfigured && sectionId == "spotcore") {
            return@withContext getCachedAlbums()
        }
        val response = getService().getLibraryItems(sectionId, "9", token = settings.token)
        response.mediaContainer.metadata ?: emptyList()
    }

    suspend fun getRecentlyAddedAlbums(sectionId: String): List<PlexMetadata> = withContext(Dispatchers.IO) {
        if (settings.isCompanionConfigured && sectionId == "spotcore") {
            // The companion library endpoint does not currently expose a
            // trustworthy addedAt value. Do not turn sync order into a fake
            // Recently Added shelf; the authoritative server Home endpoint
            // supplies this row.
            return@withContext emptyList()
        }
        val response = getService().getRecentlyAddedAlbums(sectionId, "9", settings.token)
        response.mediaContainer.metadata ?: emptyList()
    }

    suspend fun getMetadataDetail(ratingKey: String): PlexMetadata? = withContext(Dispatchers.IO) {
        if (settings.isCompanionConfigured) {
            musicDao.getTrackByRatingKey(ratingKey)?.let {
                return@withContext it.toPlexMetadata()
            }
            when {
                ratingKey.startsWith("spotcore-artist:") -> {
                    val artist = ratingKey.removePrefix("spotcore-artist:")
                    return@withContext getCachedArtists().firstOrNull { it.title == artist }
                }
                parseAlbumNavigationKey(ratingKey) != null -> {
                    val (artist, album) = parseAlbumNavigationKey(ratingKey) ?: return@withContext null
                    return@withContext getCachedAlbums().firstOrNull { it.title == album && it.parentTitle == artist }
                }
            }
        }
        try {
            val response = getService().getMetadataDetail(ratingKey, settings.token)
            response.mediaContainer.metadata?.firstOrNull()
        } catch (e: Exception) {
            Log.e("MusicRepository", "Error fetching metadata detail (${e::class.simpleName})")
            null
        }
    }

    suspend fun getArtistAlbums(artistId: String): List<PlexMetadata> = withContext(Dispatchers.IO) {
        try {
            getArtistAlbumsFromSource(artistId)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            Log.e("MusicRepository", "Error fetching artist albums (${e::class.simpleName})")
            emptyList()
        }
    }

    /** Detail screens need to distinguish a real empty catalogue from a failed request. */
    suspend fun getArtistAlbumsForDetail(artistId: String): List<PlexMetadata> = withContext(Dispatchers.IO) {
        getArtistAlbumsFromSource(artistId)
    }

    private suspend fun getArtistAlbumsFromSource(artistId: String): List<PlexMetadata> {
        if (settings.isCompanionConfigured && artistId.startsWith("spotcore-artist:")) {
            val artist = artistId.removePrefix("spotcore-artist:")
            return sortArtistAlbumsChronologically(
                musicDao.getAlbumsByArtist(artist).map { track ->
                    PlexMetadata(
                        ratingKey = albumNavigationKey(artist, track.album),
                        key = albumNavigationKey(artist, track.album),
                        title = track.album,
                        type = "album",
                        thumb = track.thumb.takeIf { it.isNotBlank() },
                        parentTitle = artist,
                        year = track.year,
                        addedAt = track.addedAt
                    )
                }
            )
        }
        val response = getService().getChildren(artistId, settings.token)
        return sortArtistAlbumsChronologically(response.mediaContainer.metadata.orEmpty())
    }

    suspend fun getAlbumTracks(albumId: String): List<PlexMetadata> = withContext(Dispatchers.IO) {
        try {
            getAlbumTracksFromSource(albumId)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            Log.e("MusicRepository", "Error fetching album tracks (${e::class.simpleName})")
            emptyList()
        }
    }

    /** Detail screens need to distinguish a real empty catalogue from a failed request. */
    suspend fun getAlbumTracksForDetail(albumId: String): List<PlexMetadata> = withContext(Dispatchers.IO) {
        getAlbumTracksFromSource(albumId)
    }

    private suspend fun getAlbumTracksFromSource(albumId: String): List<PlexMetadata> {
        parseAlbumNavigationKey(albumId)?.let { (artist, album) ->
            return findCachedAlbumTracks(musicDao.getTracksByAlbum(artist, album), artist, album)
                .map { it.toPlexMetadata() }
        }
        val response = getService().getChildren(albumId, settings.token)
        return sortAlbumTracksInPlaybackOrder(response.mediaContainer.metadata.orEmpty())
    }

    suspend fun getArtistTracks(artistName: String): List<PlexMetadata> = withContext(Dispatchers.IO) {
        musicDao.getTracksByArtist(artistName).map { it.toPlexMetadata() }
    }

    suspend fun searchPlex(query: String): List<PlexMetadata> = withContext(Dispatchers.IO) {
        if (query.isBlank()) return@withContext emptyList()
        try {
            if (!settings.isPlexConfigured) return@withContext searchCachedTracks(query)
            val response = getService().globalSearch(query, settings.token)
            response.mediaContainer.metadata?.filter { 
                it.type == "artist" || it.type == "album" || it.type == "track" 
            } ?: searchCachedTracks(query)
        } catch (e: Exception) {
            Log.e("MusicRepository", "Error searching music library (${e::class.simpleName})")
            searchCachedTracks(query)
        }
    }

    private suspend fun getCompanionArtists(): List<PlexMetadata> {
        val cachedArtists = getCachedArtists()
        val cachedByName = cachedArtists.associateBy { it.title.trim().lowercase() }
        val profiles = try {
            BackendClientManager
                .getApiService(settings.companionBackendUrl)
                .getArtists(userAuthToken = "Bearer ${settings.companionBackendToken}")
                .artists
                .orEmpty()
        } catch (cancellationException: CancellationException) {
            throw cancellationException
        } catch (e: Exception) {
            if (cachedArtists.isEmpty()) throw e
            Log.w("MusicRepository", "Could not fetch SpotCore artist profiles; using saved artists (${e::class.simpleName})")
            emptyList()
        }

        val profileArtists = profiles.mapNotNull { profile ->
            val title = profile.artist.trim().takeIf { it.isNotEmpty() } ?: return@mapNotNull null
            val cached = cachedByName[title.lowercase()]
            PlexMetadata(
                ratingKey = "spotcore-artist:$title",
                key = "spotcore-artist:$title",
                title = title,
                type = "artist",
                thumb = normalizeCompanionArtistArtworkUrl(
                    companionBaseUrl = settings.companionBackendUrl,
                    artworkReference = profile.coverUrl
                ) ?: cached?.thumb,
                grandparentTitle = title,
                genres = profile.styles
                    .filter { it.isNotBlank() }
                    .distinctBy { it.lowercase() }
                    .map(::PlexTag)
                    .ifEmpty { cached?.genres.orEmpty() }
            )
        }

        return (profileArtists + cachedArtists)
            .distinctBy { it.title.trim().lowercase() }
            .sortedWith(compareBy<PlexMetadata> { it.title.lowercase() }.thenBy { it.ratingKey })
    }

    private suspend fun getCachedArtists(includeArtwork: Boolean = true): List<PlexMetadata> =
        musicDao.getCachedArtistsSummary().map { summary ->
            PlexMetadata(
                ratingKey = "spotcore-artist:${summary.title}",
                key = "spotcore-artist:${summary.title}",
                title = summary.title,
                type = "artist",
                thumb = if (includeArtwork) summary.thumb else null,
                grandparentTitle = summary.title,
                genres = cachedSummaryTags(summary.genres)
            )
        }

    private suspend fun getCachedAlbums(): List<PlexMetadata> =
        musicDao.getCachedAlbumsSummary().map { summary ->
            val artist = summary.parentTitle?.takeIf { it.isNotBlank() }
                ?: context.getString(R.string.common_unknown_artist)
            val album = summary.title
            PlexMetadata(
                ratingKey = albumNavigationKey(artist, album),
                key = albumNavigationKey(artist, album),
                title = album,
                type = "album",
                thumb = summary.thumb,
                parentTitle = artist,
                year = summary.year,
                addedAt = summary.addedAt,
                genres = cachedSummaryTags(summary.genres)
            )
        }

    private fun cachedSummaryTags(value: String?): List<PlexTag> =
        value.orEmpty()
            .split(Regex("[|,;]"))
            .map(String::trim)
            .filter(String::isNotEmpty)
            .distinctBy { it.lowercase() }
            .map(::PlexTag)

    /**
     * Searches the SpotCore companion library contract. The returned model is
     * intentionally mapped into the existing PlexMetadata shape so callers
     * and offline queues remain unchanged during the migration.
     */
    suspend fun searchCompanion(query: String): List<PlexMetadata> = withContext(Dispatchers.IO) {
        if (!settings.isCompanionConfigured || query.isBlank()) return@withContext emptyList()
        val response = BackendClientManager
            .getApiService(settings.companionBackendUrl)
            .searchLibrary(query, userAuthToken = "Bearer ${settings.companionBackendToken}")
        response.tracks.orEmpty().map { it.toPlexMetadata() }
    }

    private fun BackendTrackDto.toPlexMetadata(): PlexMetadata {
        val albumArtist = effectiveAlbumArtist(albumArtist, artist)
        return PlexMetadata(
            ratingKey = id,
            key = resolveCompanionMediaReference(settings.companionBackendUrl, streamUrl),
            title = title,
            type = "track",
            thumb = normalizeCompanionArtworkUrl(
                companionBaseUrl = settings.companionBackendUrl,
                trackId = id,
                coverUrl = coverUrl
            ),
            parentTitle = album,
            grandparentTitle = artist,
            albumArtist = albumArtist,
            index = resolvedTrackNumber().takeIf { it > 0 },
            parentIndex = resolvedDiscNumber(),
            duration = duration.toLong(),
            year = year,
            parentRatingKey = albumNavigationKey(albumArtist, album),
            grandparentRatingKey = artistNavigationKey(artist),
            genres = genre?.let { listOf(PlexTag(it)) },
            media = listOf(PlexMedia(listOf(PlexPart(resolveCompanionMediaReference(settings.companionBackendUrl, streamUrl)))))
        )
    }

    private fun CachedTrack.toPlexMetadata(): PlexMetadata = PlexMetadata(
        ratingKey = ratingKey,
        key = key,
        title = title,
        type = "track",
        thumb = thumb.takeIf { it.isNotBlank() },
        parentTitle = album,
        grandparentTitle = artist,
        albumArtist = effectiveAlbumArtist(),
        index = index.takeIf { it > 0 },
        parentIndex = discNumber.takeIf { it > 0 },
        duration = duration,
        year = year,
        viewCount = playCount,
        lastViewedAt = lastPlayedAt,
        parentRatingKey = albumNavigationKey(effectiveAlbumArtist(), album),
        grandparentRatingKey = "spotcore-artist:${effectiveAlbumArtist()}",
        genres = genres.split('|').filter { it.isNotBlank() }.map { PlexTag(it) },
        collections = collections.split('|').filter { it.isNotBlank() }.map { PlexTag(it) },
        media = listOf(PlexMedia(listOf(PlexPart(key))))
    )

    suspend fun smartSearch(query: String): List<PlexMetadata> = withContext(Dispatchers.IO) {
        val cached = musicDao.getCachedTracksList()
        val downloaded = musicDao.getDownloadedTracksList().filter { it.status == "completed" }.map { it.ratingKey }.toSet()
        val service = SmartSearchService()
        service.filter(service.parse(query, cached), cached, downloaded).map { track ->
            PlexMetadata(
                ratingKey = track.ratingKey,
                key = track.key,
                title = track.title,
                type = "track",
                thumb = track.thumb,
                parentTitle = track.album,
                grandparentTitle = track.artist,
                albumArtist = track.effectiveAlbumArtist(),
                duration = track.duration,
                parentRatingKey = albumNavigationKey(track.effectiveAlbumArtist(), track.album),
                media = listOf(PlexMedia(listOf(PlexPart(track.key))))
            )
        }
    }

    private suspend fun searchCachedTracks(query: String): List<PlexMetadata> =
        musicDao.searchCachedTracks(query).map { track ->
            PlexMetadata(
                ratingKey = track.ratingKey,
                key = track.key,
                title = track.title,
                type = "track",
                thumb = track.thumb,
                parentTitle = track.album,
                grandparentTitle = track.artist,
                albumArtist = track.effectiveAlbumArtist(),
                duration = track.duration,
                parentRatingKey = albumNavigationKey(track.effectiveAlbumArtist(), track.album),
                media = listOf(PlexMedia(listOf(PlexPart(track.key))))
            )
        }

    private suspend fun <T> retryIO(
        times: Int = 3,
        initialDelayMs: Long = 1000,
        factor: Double = 2.0,
        block: suspend () -> T
    ): T {
        var currentDelay = initialDelayMs
        repeat(times - 1) { attempt ->
            try {
                return block()
            } catch (e: Exception) {
                Log.w("MusicRepository", "Music library request failed (${e::class.simpleName}; attempt ${attempt + 1}/$times). Retrying in ${currentDelay}ms...")
                delay(currentDelay)
                currentDelay = (currentDelay * factor).toLong()
            }
        }
        return block()
    }

    suspend fun startSync(sectionId: String, restart: Boolean = false) = withContext(Dispatchers.IO) {
        val workManager = androidx.work.WorkManager.getInstance(context)
        val data = androidx.work.workDataOf(
            "sectionId" to sectionId,
            "restart" to restart
        )
        val request = androidx.work.OneTimeWorkRequestBuilder<LibrarySyncWorker>()
            .setInputData(data)
            .setConstraints(
                androidx.work.Constraints.Builder()
                    .setRequiredNetworkType(androidx.work.NetworkType.CONNECTED)
                    .build()
            )
            .build()
        workManager.enqueueUniqueWork(
            "sync_$sectionId",
            if (restart) androidx.work.ExistingWorkPolicy.REPLACE else androidx.work.ExistingWorkPolicy.KEEP,
            request
        )
        if (sectionId == "spotcore" && settings.isCompanionConfigured) {
            CompanionTrackStateSyncScheduler.enqueue(context)
        }
    }

    fun enqueuePlaylistSync() {
        if (settings.isCompanionConfigured) PlaylistSyncScheduler.enqueue(context)
    }

    fun enqueueCompanionTrackStateSync() {
        if (settings.isCompanionConfigured) CompanionTrackStateSyncScheduler.enqueue(context)
    }

    suspend fun scanServerLibrary() = withContext(Dispatchers.IO) {
        val companionUrl = settings.companionBackendUrl
        val companionToken = settings.companionBackendToken

        if (companionUrl.isBlank() || companionToken.isBlank()) {
            throw IllegalStateException("SpotCore Companion is not configured. Server scan is only supported via SpotCore.")
        }

        Log.d("MusicRepository", "Explicitly triggering SpotCore scan")

        // The device credential is sent only as Authorization.
        BackendClientManager.getApiService(companionUrl)
            .triggerLibraryScan(
                plexTokenHeader = null,
                userAuthToken = "Bearer $companionToken"
            )

        // The caller owns the local indexing request separately so a WorkManager
        // enqueue failure cannot be mistaken for a failed SpotCore connection.
    }

    suspend fun generateAiPlaylist(prompt: String): List<TrackItem> = withContext(Dispatchers.Default) {
        val cachedTracks = musicDao.getCachedTracksList()
        if (cachedTracks.isEmpty()) return@withContext emptyList()
        val intent = LocalAIProvider().generatePlaylistIntent(prompt, cachedTracks)
        RecommendationEngine(context).buildRecommendationQueue(intent, cachedTracks)
    }
}

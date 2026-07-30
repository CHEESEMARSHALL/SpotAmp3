package com.example.data

import android.content.Context
import android.util.Log
import com.example.BuildConfig
import com.example.playback.TrackItem
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.withContext
import okhttp3.Request
import java.io.File

internal fun <T> deterministicTrackSample(items: List<T>, maxItems: Int): List<T> {
    if (maxItems <= 0 || items.isEmpty()) return emptyList()
    val ordered = items.toList()
    if (ordered.size <= maxItems) return ordered
    if (maxItems == 1) return listOf(ordered.first())
    return (0 until maxItems).map { index ->
        ordered[index * ordered.lastIndex / (maxItems - 1)]
    }
}

class MusicRepository(private val context: Context) {
    private val musicDao = MusicDatabase.getDatabase(context).musicDao()
    val settings = PlexSettingsManager(context)

    // Flow for recently played tracks
    val recentlyPlayed: Flow<List<RecentTrack>> = musicDao.getRecentlyPlayed()

    // Flow for cached tracks count (to see if indexed)
    val cachedTracksCount: Flow<List<CachedTrack>> = musicDao.getAllCachedTracks()

    fun getSyncStateFlow(sectionId: String): Flow<SyncStateEntity?> {
        return musicDao.getSyncStateFlow(sectionId)
    }

    suspend fun getCachedCount(): Int = withContext(Dispatchers.IO) {
        musicDao.getCachedTracksCount()
    }

    suspend fun getCachedTracksList(): List<CachedTrack> = withContext(Dispatchers.IO) {
        musicDao.getCachedTracksList()
    }

    // Playlists
    val playlists: Flow<List<PlaylistEntity>> = musicDao.getAllPlaylists()

    suspend fun createPlaylist(name: String): Long = withContext(Dispatchers.IO) {
        musicDao.insertPlaylist(PlaylistEntity(name = name))
    }

    suspend fun deletePlaylist(playlistId: Int) = withContext(Dispatchers.IO) {
        musicDao.deletePlaylist(playlistId)
        musicDao.clearPlaylistTracks(playlistId)
    }

    fun getPlaylistTracks(playlistId: Int): Flow<List<PlaylistTrackEntity>> {
        return musicDao.getTracksForPlaylist(playlistId)
    }

    suspend fun getPlaylistTracksList(playlistId: Int): List<PlaylistTrackEntity> = withContext(Dispatchers.IO) {
        musicDao.getTracksForPlaylistList(playlistId)
    }

    suspend fun addTrackToPlaylist(playlistId: Int, track: TrackItem) = withContext(Dispatchers.IO) {
        musicDao.insertPlaylistTrack(
            PlaylistTrackEntity(
                playlistId = playlistId,
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

    suspend fun removeTrackFromPlaylist(playlistId: Int, ratingKey: String) = withContext(Dispatchers.IO) {
        musicDao.removeTrackFromPlaylist(playlistId, ratingKey)
    }

    // Downloads
    val downloadedTracks: Flow<List<DownloadedTrackEntity>> = musicDao.getAllDownloadedTracks()

    suspend fun isTrackDownloaded(ratingKey: String): Boolean = withContext(Dispatchers.IO) {
        musicDao.isTrackDownloaded(ratingKey)
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

    suspend fun queueDownload(track: TrackItem) = withContext(Dispatchers.IO) {
        musicDao.insertDownloadedTrack(
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
        musicDao.updateDownloadStatus(ratingKey, status, errorMessage)
    }

    suspend fun downloadTrack(track: TrackItem, onProgress: (Float) -> Unit = {}): DownloadedTrackEntity = withContext(Dispatchers.IO) {
        if (!settings.isConfigured) throw IllegalStateException("Plex server is not configured.")
        val targetDir = context.getExternalFilesDir("music") ?: File(context.filesDir, "music")
        targetDir.mkdirs()
        val extension = track.key.substringAfterLast('.', "mp3").takeIf { it.length in 2..5 } ?: "mp3"
        val target = File(targetDir, "${track.ratingKey}.$extension")
        val url = "${settings.baseUrl.trimEnd('/')}${track.key}"
        val request = Request.Builder().url(url).header("X-Plex-Token", settings.token).build()
        var completed = false
        try {
            okhttp3.OkHttpClient().newCall(request).execute().use { response ->
                if (!response.isSuccessful) throw IllegalStateException("Plex download failed: HTTP ${response.code}")
                val body = response.body ?: throw IllegalStateException("Plex returned an empty audio file.")
                val total = body.contentLength()
                body.byteStream().use { input -> target.outputStream().use { output ->
                    val buffer = ByteArray(DEFAULT_BUFFER_SIZE)
                    var copied = 0L
                    while (true) {
                        val read = input.read(buffer)
                        if (read < 0) break
                        output.write(buffer, 0, read)
                        copied += read
                        if (total > 0) onProgress(copied.toFloat() / total)
                    }
                }}
            }
            addDownloadedTrack(track, target.length(), target.absolutePath)
            completed = true
            musicDao.getDownloadedTrack(track.ratingKey) ?: error("Downloaded track was not indexed")
        } finally {
            if (!completed) target.delete()
        }
    }

    private fun getService(): PlexApiService {
        if (!settings.isConfigured) {
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
            Log.e("MusicRepository", "Error fetching libraries", e)
            // Preserve the failure so the UI can distinguish an unreachable
            // server from a reachable server with no music sections.
            throw e
        }
    }

    suspend fun getArtists(sectionId: String): List<PlexMetadata> = withContext(Dispatchers.IO) {
        try {
            val response = getService().getLibraryItems(sectionId, "8", token = settings.token)
            response.mediaContainer.metadata ?: emptyList()
        } catch (e: Exception) {
            Log.e("MusicRepository", "Error fetching artists", e)
            emptyList()
        }
    }

    suspend fun getAlbums(sectionId: String): List<PlexMetadata> = withContext(Dispatchers.IO) {
        try {
            val response = getService().getLibraryItems(sectionId, "9", token = settings.token)
            response.mediaContainer.metadata ?: emptyList()
        } catch (e: Exception) {
            Log.e("MusicRepository", "Error fetching albums", e)
            emptyList()
        }
    }

    suspend fun getRecentlyAddedAlbums(sectionId: String): List<PlexMetadata> = withContext(Dispatchers.IO) {
        try {
            val response = getService().getRecentlyAddedAlbums(sectionId, "9", settings.token)
            response.mediaContainer.metadata ?: emptyList()
        } catch (e: Exception) {
            Log.e("MusicRepository", "Error fetching recently added", e)
            emptyList()
        }
    }

    suspend fun getMetadataDetail(ratingKey: String): PlexMetadata? = withContext(Dispatchers.IO) {
        try {
            val response = getService().getMetadataDetail(ratingKey, settings.token)
            response.mediaContainer.metadata?.firstOrNull()
        } catch (e: Exception) {
            Log.e("MusicRepository", "Error fetching metadata detail", e)
            null
        }
    }

    suspend fun getArtistAlbums(artistId: String): List<PlexMetadata> = withContext(Dispatchers.IO) {
        try {
            val response = getService().getChildren(artistId, settings.token)
            response.mediaContainer.metadata ?: emptyList()
        } catch (e: Exception) {
            Log.e("MusicRepository", "Error fetching artist albums", e)
            emptyList()
        }
    }

    suspend fun getAlbumTracks(albumId: String): List<PlexMetadata> = withContext(Dispatchers.IO) {
        try {
            val response = getService().getChildren(albumId, settings.token)
            response.mediaContainer.metadata ?: emptyList()
        } catch (e: Exception) {
            Log.e("MusicRepository", "Error fetching album tracks", e)
            emptyList()
        }
    }

    suspend fun searchPlex(query: String): List<PlexMetadata> = withContext(Dispatchers.IO) {
        if (query.isBlank()) return@withContext emptyList()
        try {
            if (!settings.isConfigured) return@withContext searchCachedTracks(query)
            val response = getService().globalSearch(query, settings.token)
            response.mediaContainer.metadata?.filter { 
                it.type == "artist" || it.type == "album" || it.type == "track" 
            } ?: searchCachedTracks(query)
        } catch (e: Exception) {
            Log.e("MusicRepository", "Error searching Plex", e)
            searchCachedTracks(query)
        }
    }

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
                duration = track.duration,
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
                duration = track.duration,
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
                Log.w("MusicRepository", "Plex API call failed (attempt ${attempt + 1}/$times): ${e.localizedMessage}. Retrying in ${currentDelay}ms...")
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
    }

    suspend fun generateAiPlaylist(prompt: String): List<TrackItem> = withContext(Dispatchers.IO) {
        val cachedTracks = musicDao.getCachedTracksList()
        if (cachedTracks.isEmpty()) {
            throw IllegalStateException("Your music library has not been indexed yet. Please go to Settings to index your music library.")
        }

        // Deterministic sampling to respect token limit. Max 200 tracks.
        // The model may only choose from this context, so the same library state
        // must produce the same candidate set across retries and app restarts.
        val sampledTracks = deterministicTrackSample(
            cachedTracks.sortedBy { it.ratingKey },
            maxItems = 200
        )

        // Build list for the model prompt
        val tracksText = sampledTracks.joinToString("\n") { 
            "[${it.ratingKey}] ${it.artist} - ${it.title} (${it.album})"
        }

        val systemInstructionText = """
            You are an expert music playlist curator for a self-hosted audio streaming application.
            Your task is to review the list of available tracks provided by the user, and select between 8 and 15 tracks that match their thematic, genre, or mood request.
            
            CRITICAL DIRECTIVES:
            1. You MUST ONLY select tracks from the provided list. Do NOT invent or hallucinate songs.
            2. Return ONLY a plain JSON array of strings containing the selected track ratingKeys.
            3. Do not include any explanation, intro/outro, conversational filler, markdown formatting, or triple backticks.
            4. Example correct response: ["101", "204", "150"]
        """.trimIndent()

        val userPrompt = """
            User Request: "$prompt"
            
            Available Tracks in Library:
            $tracksText
        """.trimIndent()

        val geminiApiKey = BuildConfig.GEMINI_API_KEY
        if (geminiApiKey.isEmpty() || geminiApiKey.contains("GEMINI_API_KEY")) {
            throw IllegalStateException("Gemini API key is not configured in the Secrets panel.")
        }

        val request = GeminiRequest(
            contents = listOf(GeminiContent(parts = listOf(GeminiPart(text = userPrompt)))),
            systemInstruction = GeminiContent(parts = listOf(GeminiPart(text = systemInstructionText))),
            generationConfig = GeminiGenerationConfig(
                responseMimeType = "application/json",
                temperature = 0.5f
            )
        )

        try {
            val response = GeminiClient.service.generateContent(geminiApiKey, request)
            val jsonText = response.candidates?.firstOrNull()?.content?.parts?.firstOrNull()?.text ?: ""
            Log.d("MusicRepository", "Gemini response received")

            // Parse response: extract ratingKeys
            val ratingKeys = parseRatingKeysFromJson(jsonText)
            
            // Map keys back to original entities, maintaining the order of Gemini's selection
            val selectedTracks = ratingKeys.mapNotNull { key ->
                cachedTracks.find { it.ratingKey == key }
            }

            selectedTracks.map { 
                TrackItem(
                    ratingKey = it.ratingKey,
                    title = it.title,
                    artist = it.artist,
                    album = it.album,
                    key = it.key,
                    thumb = it.thumb,
                    duration = it.duration
                )
            }
        } catch (e: Exception) {
            Log.e("MusicRepository", "Error generating AI playlist", e)
            throw e
        }
    }

    private fun parseRatingKeysFromJson(jsonText: String): List<String> {
        val clean = jsonText.trim()
            .removeSurrounding("```json", "```")
            .removeSurrounding("```", "```")
            .trim()
            .removeSurrounding("[", "]")

        if (clean.isEmpty()) return emptyList()

        return clean.split(",")
            .map { it.trim().removeSurrounding("\"").removeSurrounding("'") }
            .filter { it.isNotEmpty() }
    }
}

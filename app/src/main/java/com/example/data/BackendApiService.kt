package com.example.data

import com.squareup.moshi.Json
import com.squareup.moshi.JsonClass
import com.squareup.moshi.Moshi
import okhttp3.OkHttpClient
import retrofit2.Retrofit
import okhttp3.ResponseBody
import retrofit2.Call
import retrofit2.converter.moshi.MoshiConverterFactory
import retrofit2.http.*
import java.util.concurrent.TimeUnit

// ==========================================
// Proxy Backend Data Transfer Objects (DTOs)
// ==========================================

@JsonClass(generateAdapter = true)
data class BackendHomeFeedResponse(
    @Json(name = "recentlyPlayed") val recentlyPlayed: List<BackendTrackDto>? = null,
    @Json(name = "recentlyAdded") val recentlyAdded: List<BackendAlbumDto>? = null,
    @Json(name = "dailyMixes") val dailyMixes: List<BackendDailyMixDto>? = null,
    @Json(name = "stations") val stations: List<BackendStationDto>? = null,
    @Json(name = "madeForYou") val madeForYou: List<BackendMadeForYouDto>? = null,
    @Json(name = "onThisDay") val onThisDay: BackendOnThisDayDto? = null
)

@JsonClass(generateAdapter = true)
data class BackendPlaylistDto(
    @Json(name = "id") val id: String,
    @Json(name = "name") val name: String,
    @Json(name = "trackCount") val trackCount: Int? = null,
    @Json(name = "songCount") val songCount: Int? = null,
    @Json(name = "createdAt") val createdAt: Long? = null,
    @Json(name = "updatedAt") val updatedAt: Long? = null,
    @Json(name = "totalDuration") val totalDuration: Double? = null,
    @Json(name = "coverUrl") val coverUrl: String? = "",
    @Json(name = "tracks") val tracks: List<BackendTrackDto> = emptyList()
)

@JsonClass(generateAdapter = true)
data class BackendArtistProfileDto(
    @Json(name = "id") val id: String? = null,
    @Json(name = "artist") val artist: String,
    @Json(name = "name") val name: String? = null,
    @Json(name = "bio") val bio: String? = null,
    @Json(name = "coverUrl") val coverUrl: String? = "",
    @Json(name = "similarArtists") val similarArtists: List<BackendSimilarArtistDto> = emptyList(),
    @Json(name = "styles") val styles: List<String> = emptyList(),
    @Json(name = "source") val source: String? = null,
    @Json(name = "sourceUrl") val sourceUrl: String? = null,
    @Json(name = "updatedAt") val updatedAt: Long? = null
)

@JsonClass(generateAdapter = true)
data class BackendSimilarArtistDto(
    @Json(name = "name") val name: String,
    @Json(name = "style") val style: String = ""
)

@JsonClass(generateAdapter = true)
data class BackendCreatePlaylistRequest(
    @Json(name = "name") val name: String,
    @Json(name = "clientMutationId") val clientMutationId: String? = null
)

@JsonClass(generateAdapter = true)
data class BackendRenamePlaylistRequest(
    @Json(name = "name") val name: String,
    @Json(name = "baseUpdatedAt") val baseUpdatedAt: Long? = null
)

@JsonClass(generateAdapter = true)
data class BackendReplacePlaylistRequest(
    @Json(name = "name") val name: String? = null,
    @Json(name = "trackIds") val trackIds: List<String>,
    @Json(name = "baseUpdatedAt") val baseUpdatedAt: Long? = null
)

@JsonClass(generateAdapter = true)
data class BackendAddPlaylistTrackRequest(
    @Json(name = "trackId") val trackId: String,
    @Json(name = "baseUpdatedAt") val baseUpdatedAt: Long? = null
)

@JsonClass(generateAdapter = true)
data class BackendPlaylistRevisionRequest(
    @Json(name = "baseUpdatedAt") val baseUpdatedAt: Long? = null
)

@JsonClass(generateAdapter = true)
data class BackendReorderPlaylistRequest(
    @Json(name = "fromIndex") val fromIndex: Int,
    @Json(name = "toIndex") val toIndex: Int,
    @Json(name = "baseUpdatedAt") val baseUpdatedAt: Long? = null
)

@JsonClass(generateAdapter = true)
data class BackendTrackDto(
    @Json(name = "id") val id: String,
    @Json(name = "title") val title: String,
    @Json(name = "artist") val artist: String,
    @Json(name = "album") val album: String,
    @Json(name = "streamUrl") val streamUrl: String, // Secure proxy stream URL (Plex token is embedded by backend)
    @Json(name = "coverUrl") val coverUrl: String? = "", // Authenticated local path, direct image URL, or explicit null when absent
    // Wire duration may be fractional milliseconds; consumers convert to the app's whole-ms type.
    @Json(name = "duration") val duration: Double,
    @Json(name = "year") val year: Int? = null,
    @Json(name = "genre") val genre: String? = null,
    @Json(name = "albumArtist") val albumArtist: String? = null,
    @Json(name = "index") val index: Any? = null,
    @Json(name = "track") val track: Any? = null,
    @Json(name = "trackIndex") val trackIndex: Any? = null,
    @Json(name = "trackNumber") val trackNumber: Any? = null,
    @Json(name = "discNumber") val discNumber: Any? = null,
    @Json(name = "disc") val disc: Any? = null,
    @Json(name = "addedAt") val addedAt: Long? = null,
    @Json(name = "playCount") val playCount: Int? = null,
    @Json(name = "lastPlayedAt") val lastPlayedAt: Long? = null,
    @Json(name = "position") val position: Int? = null,
    @Json(name = "favorite") val favorite: Boolean? = null,
    @Json(name = "rating") val rating: Int? = null
)

@JsonClass(generateAdapter = true)
data class BackendMusicIntelligencePage(
    @Json(name = "contractVersion") val contractVersion: String? = null,
    @Json(name = "tracks") val tracks: List<BackendTrackIntelligenceDto> = emptyList()
)

@JsonClass(generateAdapter = true)
data class BackendTrackIntelligenceDto(
    @Json(name = "trackId") val trackId: String,
    @Json(name = "components") val components: List<BackendAnalysisComponentDto> = emptyList(),
    @Json(name = "features") val features: BackendTrackFeaturesDto? = null,
    @Json(name = "labels") val labels: List<BackendIntelligenceLabelDto> = emptyList()
)

@JsonClass(generateAdapter = true)
data class BackendAnalysisComponentDto(
    @Json(name = "component") val component: String,
    @Json(name = "state") val state: String,
    @Json(name = "analysisVersion") val analysisVersion: String,
    @Json(name = "updatedAt") val updatedAt: Long? = null,
    @Json(name = "analyzedAt") val analyzedAt: Long? = null
)

@JsonClass(generateAdapter = true)
data class BackendTrackFeaturesDto(
    @Json(name = "analysisVersion") val analysisVersion: String? = null,
    @Json(name = "bpm") val bpm: Double? = null,
    @Json(name = "bpmConfidence") val bpmConfidence: Double? = null,
    @Json(name = "musicalKey") val musicalKey: String? = null,
    @Json(name = "keyConfidence") val keyConfidence: Double? = null,
    @Json(name = "rhythmConfidence") val rhythmConfidence: Double? = null,
    @Json(name = "scale") val scale: String? = null,
    @Json(name = "loudnessIntegrated") val loudnessIntegrated: Double? = null,
    @Json(name = "dynamicRange") val dynamicRange: Double? = null,
    @Json(name = "energy") val energy: Double? = null,
    @Json(name = "danceability") val danceability: Double? = null,
    @Json(name = "instrumentalProbability") val instrumentalProbability: Double? = null,
    @Json(name = "vocalProbability") val vocalProbability: Double? = null,
    @Json(name = "analyzedAt") val analyzedAt: Long? = null,
    @Json(name = "extractorId") val extractorId: String? = null,
    @Json(name = "extractorVersion") val extractorVersion: String? = null
)

@JsonClass(generateAdapter = true)
data class BackendIntelligenceLabelDto(
    @Json(name = "type") val type: String,
    @Json(name = "value") val value: String,
    @Json(name = "score") val score: Double? = null,
    @Json(name = "source") val source: String? = null,
    @Json(name = "modelId") val modelId: String? = null,
    @Json(name = "modelVersion") val modelVersion: String? = null,
    @Json(name = "taxonomyVersion") val taxonomyVersion: String? = null,
    @Json(name = "reviewStatus") val reviewStatus: String = "unreviewed",
    @Json(name = "manuallyLocked") val manuallyLocked: Boolean = false,
    @Json(name = "updatedAt") val updatedAt: Long? = null
)

internal fun BackendTrackIntelligenceDto.currentFeatures(): BackendTrackFeaturesDto? {
    val value = features ?: return null
    if (components.isEmpty()) return value
    val essentia = components.firstOrNull { it.component.equals("essentia", ignoreCase = true) } ?: return null
    if (!essentia.state.equals("Complete", ignoreCase = true)) return null
    val componentVersion = essentia.analysisVersion.trim()
    val featureVersion = value.analysisVersion?.trim().orEmpty()
    if (componentVersion.isNotEmpty() && featureVersion.isNotEmpty() && componentVersion != featureVersion) return null
    return value
}

internal fun BackendTrackIntelligenceDto.currentLabels(): List<BackendIntelligenceLabelDto> {
    if (components.isEmpty()) return labels.filterNot { it.reviewStatus.equals("rejected", ignoreCase = true) }
    return labels.filter { label ->
        if (label.reviewStatus.equals("rejected", ignoreCase = true)) return@filter false
        if (label.manuallyLocked) return@filter true
        val source = "${label.source.orEmpty()} ${label.modelId.orEmpty()}".lowercase()
        val componentName = when {
            "clap" in source -> "clap"
            "essentia" in source -> "essentia"
            else -> null
        }
        if (componentName == null) {
            components.any { it.state.equals("Complete", ignoreCase = true) }
        } else {
            components.any {
                it.component.equals(componentName, ignoreCase = true) &&
                    it.state.equals("Complete", ignoreCase = true)
            }
        }
    }
}
internal object BackendIntelligenceJson {
    private val adapter = Moshi.Builder().build().adapter(BackendTrackIntelligenceDto::class.java)

    fun encode(track: BackendTrackIntelligenceDto): String = adapter.toJson(track)

    fun decode(json: String): BackendTrackIntelligenceDto? = adapter.fromJson(json)
}
@JsonClass(generateAdapter = true)
data class BackendLyricsDto(
    @Json(name = "text") val text: String,
    @Json(name = "synced") val synced: Boolean = false,
    @Json(name = "revision") val revision: String? = null
)

@JsonClass(generateAdapter = true)
data class BackendPlaybackEventRequest(
    @Json(name = "trackId") val trackId: String,
    @Json(name = "playedAt") val playedAt: Long,
    @Json(name = "eventId") val eventId: String,
    @Json(name = "listenedMs") val listenedMs: Long? = null,
    @Json(name = "source") val source: String = "spotamp"
)

@JsonClass(generateAdapter = true)
data class BackendFavoriteRequest(
    @Json(name = "favorite") val favorite: Boolean
)

@JsonClass(generateAdapter = true)
data class BackendRatingRequest(
    @Json(name = "rating") val rating: Int
)

@JsonClass(generateAdapter = true)
data class CompanionPairingClaimRequest(
    @Json(name = "pairingId") val pairingId: String,
    @Json(name = "secret") val secret: String,
    @Json(name = "deviceName") val deviceName: String
)

@JsonClass(generateAdapter = true)
data class CompanionPairingClaimResponse(
    @Json(name = "device") val device: CompanionPairingDeviceDto,
    @Json(name = "token") val deviceToken: String,
    @Json(name = "contractVersion") val contractVersion: String? = null
)

@JsonClass(generateAdapter = true)
data class CompanionPairingDeviceDto(
    @Json(name = "id") val id: String,
    @Json(name = "name") val name: String? = null
)

@JsonClass(generateAdapter = true)
data class CompanionPairingRevokeResponse(
    @Json(name = "revoked") val revoked: Boolean,
    @Json(name = "contractVersion") val contractVersion: String? = null
)

@JsonClass(generateAdapter = true)
data class BackendAlbumDto(
    @Json(name = "id") val id: String,
    @Json(name = "title") val title: String,
    @Json(name = "artist") val artist: String,
    @Json(name = "coverUrl") val coverUrl: String? = "",
    @Json(name = "year") val year: Int? = null,
    @Json(name = "trackCount") val trackCount: Int? = null,
    @Json(name = "tracks") val tracks: List<BackendTrackDto> = emptyList(),
    @Json(name = "albumArtist") val albumArtist: String? = null
)

@JsonClass(generateAdapter = true)
data class BackendDailyMixDto(
    @Json(name = "id") val id: String,
    @Json(name = "title") val title: String,
    @Json(name = "reason") val reason: String,
    @Json(name = "colors") val colors: List<String> = emptyList(), // Hex list e.g. ["#4F46E5", "#06B6D4"]
    @Json(name = "tracks") val tracks: List<BackendTrackDto>,
    @Json(name = "albums") val albums: List<BackendAlbumDto> = emptyList()
)

@JsonClass(generateAdapter = true)
data class BackendStationDto(
    @Json(name = "id") val id: String,
    @Json(name = "title") val title: String,
    @Json(name = "subtitle") val subtitle: String,
    @Json(name = "type") val type: String,
    @Json(name = "colors") val colors: List<String>
)

@JsonClass(generateAdapter = true)
data class BackendMadeForYouDto(
    @Json(name = "id") val id: String,
    @Json(name = "title") val title: String,
    @Json(name = "description") val description: String,
    @Json(name = "artists") val artists: List<String>,
    @Json(name = "tracks") val tracks: List<BackendTrackDto>,
    @Json(name = "coverUrl") val coverUrl: String = "",
    @Json(name = "albums") val albums: List<BackendAlbumDto> = emptyList()
)

@JsonClass(generateAdapter = true)
data class BackendOnThisDayDto(
    @Json(name = "albumTitle") val albumTitle: String,
    @Json(name = "artist") val artist: String,
    @Json(name = "year") val year: Int,
    @Json(name = "timeAgo") val timeAgo: String,
    @Json(name = "coverUrl") val coverUrl: String = "",
    @Json(name = "matchReason") val matchReason: String,
    @Json(name = "tracks") val tracks: List<BackendTrackDto>
)

// ==========================================
// Custom Proxy Backend API Client Interface
// ==========================================

interface BackendApiService {

    /** The sole unauthenticated endpoint; the QR secret is single-use and short-lived. */
    @POST("api/v1/pairing/claim")
    suspend fun claimPairing(@Body request: CompanionPairingClaimRequest): CompanionPairingClaimResponse

    @POST("api/v1/pairing/revoke")
    suspend fun revokePairing(@Header("Authorization") userAuthToken: String): CompanionPairingRevokeResponse

    @GET("api/v1/lyrics/{trackId}")
    suspend fun getLyrics(@Path("trackId") trackId: String, @Header("Authorization") userAuthToken: String? = null): BackendLyricsDto

    // The companion's normalized library contract. These are the first
    // endpoints used to replace direct Plex reads in MusicRepository.
    @GET("api/v1/library/tracks")
    suspend fun getLibraryTracks(
        @Query("limit") limit: Int = 100,
        @Query("offset") offset: Int = 0,
        @Query("skip") skip: Int? = null,
        @Query("page") page: Int? = null,
        @Query("start") start: Int? = null,
        @Query("sort") sort: String? = null,
        @Query("cb") cacheBust: Long? = null,
        @Query("X-Plex-Container-Start") plexStart: Int? = null,
        @Query("X-Plex-Container-Size") plexLimit: Int? = null,
        @Header("X-Plex-Container-Start") plexStartHeader: Int? = null,
        @Header("X-Plex-Container-Size") plexLimitHeader: Int? = null,
        @Header("X-Plex-Token") plexTokenHeader: String? = null,
        @Header("Authorization") userAuthToken: String? = null
    ): BackendEnvelope

    @POST("api/v1/library/tracks/{trackId}/favorite")
    suspend fun setLibraryTrackFavorite(
        @Path("trackId") trackId: String,
        @Body request: BackendFavoriteRequest,
        @Header("Authorization") userAuthToken: String? = null
    ): BackendEnvelope

    @POST("api/v1/library/tracks/{trackId}/rating")
    suspend fun setLibraryTrackRating(
        @Path("trackId") trackId: String,
        @Body request: BackendRatingRequest,
        @Header("Authorization") userAuthToken: String? = null
    ): BackendEnvelope

    @GET("api/v1/music/intelligence")
    suspend fun getMusicIntelligence(
        @Query("limit") limit: Int = 500,
        @Query("offset") offset: Int = 0,
        @Header("Authorization") userAuthToken: String? = null
    ): BackendMusicIntelligencePage

    @GET("api/v1/library/search")
    suspend fun searchLibrary(
        @Query("q") query: String,
        @Query("limit") limit: Int = 100,
        @Query("offset") offset: Int = 0,
        @Header("X-Plex-Container-Start") plexStartHeader: Int? = null,
        @Header("X-Plex-Container-Size") plexLimitHeader: Int? = null,
        @Header("X-Plex-Token") plexTokenHeader: String? = null,
        @Header("Authorization") userAuthToken: String? = null
    ): BackendEnvelope

    @GET("api/v1/library/artists/{artistName}")
    suspend fun getArtistProfile(
        @Path("artistName") artistName: String,
        @Header("X-Plex-Token") plexTokenHeader: String? = null,
        @Header("Authorization") userAuthToken: String? = null
    ): BackendEnvelope

    @GET("api/v1/library/artists")
    suspend fun getArtists(
        @Header("X-Plex-Token") plexTokenHeader: String? = null,
        @Header("Authorization") userAuthToken: String? = null
    ): BackendEnvelope

    @GET("api/v1/playlists")
    suspend fun getPlaylists(
        @Header("X-Plex-Token") plexTokenHeader: String? = null,
        @Header("Authorization") userAuthToken: String? = null
    ): BackendEnvelope

    @GET("api/v1/playlists/{playlistId}")
    suspend fun getPlaylist(
        @Path("playlistId") playlistId: String,
        @Header("X-Plex-Token") plexTokenHeader: String? = null,
        @Header("Authorization") userAuthToken: String? = null
    ): BackendEnvelope

    @POST("api/v1/playlists")
    suspend fun createPlaylist(
        @Body request: BackendCreatePlaylistRequest,
        @Header("X-Plex-Token") plexTokenHeader: String? = null,
        @Header("Authorization") userAuthToken: String? = null
    ): BackendEnvelope

    @PATCH("api/v1/playlists/{playlistId}")
    suspend fun renamePlaylist(
        @Path("playlistId") playlistId: String,
        @Body request: BackendRenamePlaylistRequest,
        @Header("X-Plex-Token") plexTokenHeader: String? = null,
        @Header("Authorization") userAuthToken: String? = null
    ): BackendEnvelope

    @PUT("api/v1/playlists/{playlistId}")
    suspend fun replacePlaylist(
        @Path("playlistId") playlistId: String,
        @Body request: BackendReplacePlaylistRequest,
        @Header("X-Plex-Token") plexTokenHeader: String? = null,
        @Header("Authorization") userAuthToken: String? = null
    ): BackendEnvelope

    @HTTP(method = "DELETE", path = "api/v1/playlists/{playlistId}", hasBody = true)
    suspend fun deletePlaylist(
        @Path("playlistId") playlistId: String,
        @Body request: BackendPlaylistRevisionRequest,
        @Header("X-Plex-Token") plexTokenHeader: String? = null,
        @Header("Authorization") userAuthToken: String? = null
    ): BackendEnvelope

    @POST("api/v1/playlists/{playlistId}/tracks")
    suspend fun addPlaylistTrack(
        @Path("playlistId") playlistId: String,
        @Body request: BackendAddPlaylistTrackRequest,
        @Header("X-Plex-Token") plexTokenHeader: String? = null,
        @Header("Authorization") userAuthToken: String? = null
    ): BackendEnvelope

    @HTTP(method = "DELETE", path = "api/v1/playlists/{playlistId}/tracks/{trackId}", hasBody = true)
    suspend fun removePlaylistTrack(
        @Path("playlistId") playlistId: String,
        @Path("trackId") trackId: String,
        @Body request: BackendPlaylistRevisionRequest,
        @Header("X-Plex-Token") plexTokenHeader: String? = null,
        @Header("Authorization") userAuthToken: String? = null
    ): BackendEnvelope

    @POST("api/v1/playlists/{playlistId}/reorder")
    suspend fun reorderPlaylist(
        @Path("playlistId") playlistId: String,
        @Body request: BackendReorderPlaylistRequest,
        @Header("X-Plex-Token") plexTokenHeader: String? = null,
        @Header("Authorization") userAuthToken: String? = null
    ): BackendEnvelope

    @POST("api/v1/events/playback")
    suspend fun recordPlaybackEvent(
        @Body event: BackendPlaybackEventRequest,
        @Header("X-Plex-Token") plexTokenHeader: String? = null,
        @Header("Authorization") userAuthToken: String? = null
    ): BackendEnvelope

    @POST("api/v1/library/scan")
    suspend fun triggerLibraryScan(
        @Header("X-Plex-Token") plexTokenHeader: String? = null,
        @Header("Authorization") userAuthToken: String? = null
    ): BackendEnvelope

    // GET personalized home feed from your proxy backend
    @GET("api/v1/music/home")
    suspend fun getHomeFeed(
        @Header("Authorization") userAuthToken: String? = null // Optional client-to-backend authentication
    ): BackendHomeFeedResponse

    @GET("api/v1/music/home")
    fun checkHomeFeedReachability(
        @Header("Authorization") userAuthToken: String? = null,
        @Header("X-SpotAmp-Reachability-Check") reachabilityCheck: String? = null
    ): Call<ResponseBody>

    // GET Daily Mix details
    @GET("api/v1/music/mixes/{id}")
    suspend fun getDailyMixDetail(
        @Path("id") mixId: String,
        @Header("Authorization") userAuthToken: String? = null
    ): BackendDailyMixDto

    // GET Radio Station queue
    @GET("api/v1/music/stations/{id}/queue")
    suspend fun getStationQueue(
        @Path("id") stationId: String,
        @Query("seedArtistId") seedArtistId: String? = null,
        @Header("Authorization") userAuthToken: String? = null
    ): List<BackendTrackDto>

    // GET On This Day throwback content
    @GET("api/v1/music/on-this-day")
    suspend fun getOnThisDayContent(
        @Header("Authorization") userAuthToken: String? = null
    ): BackendOnThisDayDto
}

@JsonClass(generateAdapter = true)
data class BackendEnvelope(
    @Json(name = "contractVersion") val contractVersion: String? = null,
    @Json(name = "libraryRevision") val libraryRevision: Long? = null,
    @Json(name = "tracks") val tracks: List<BackendTrackDto>? = null,
    @Json(name = "total") val total: Int? = null,
    @Json(name = "artists") val artists: List<BackendArtistProfileDto>? = null,
    @Json(name = "playlists") val playlists: List<BackendPlaylistDto>? = null,
    @Json(name = "artist") val artist: BackendArtistProfileDto? = null,
    @Json(name = "playlist") val playlist: BackendPlaylistDto? = null,
    @Json(name = "conflict") val conflict: Boolean? = null,
    @Json(name = "accepted") val accepted: Boolean? = null,
    @Json(name = "duplicate") val duplicate: Boolean? = null,
    @Json(name = "eventId") val eventId: String? = null,
    @Json(name = "trackId") val trackId: String? = null,
    @Json(name = "deleted") val deleted: Boolean? = null,
    @Json(name = "playlistId") val playlistId: String? = null
)

// ==========================================
// Retrofit Client Manager for Backend Proxy
// ==========================================

object BackendClientManager {
    private var cachedService: BackendApiService? = null
    private var currentBackendUrl: String? = null

    private val okHttpClient = OkHttpClient.Builder()
        .connectTimeout(30, TimeUnit.SECONDS)
        .readTimeout(30, TimeUnit.SECONDS)
        // Do not forward an Authorization header to an untrusted redirect target.
        // SpotCore URLs are origin-stable, so callers can retry the configured origin explicitly.
        .followRedirects(false)
        .followSslRedirects(false)
        .addInterceptor(QaNetworkInterceptor())
        .build()

    /** Retrieves a Retrofit client for an explicitly configured backend URL. */
    fun getApiService(backendUrl: String): BackendApiService {
        val normalizedOrigin = normalizeCompanionUrl(
            if (backendUrl.trim().contains("://")) backendUrl else "http://${backendUrl.trim()}",
            allowPrivateHttp = true
        )
        val normalizedUrl = "$normalizedOrigin/"

        if (normalizedUrl == currentBackendUrl && cachedService != null) {
            return cachedService!!
        }

        val retrofit = Retrofit.Builder()
            .baseUrl(normalizedUrl)
            .client(okHttpClient)
            .addConverterFactory(MoshiConverterFactory.create())
            .build()

        val service = retrofit.create(BackendApiService::class.java)
        currentBackendUrl = normalizedUrl
        cachedService = service
        return service
    }
}

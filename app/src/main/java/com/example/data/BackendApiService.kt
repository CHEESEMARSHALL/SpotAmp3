package com.example.data

import com.squareup.moshi.Json
import com.squareup.moshi.JsonClass
import okhttp3.OkHttpClient
import retrofit2.Retrofit
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
data class BackendTrackDto(
    @Json(name = "id") val id: String,
    @Json(name = "title") val title: String,
    @Json(name = "artist") val artist: String,
    @Json(name = "album") val album: String,
    @Json(name = "streamUrl") val streamUrl: String, // Secure proxy stream URL (Plex token is embedded by backend)
    @Json(name = "coverUrl") val coverUrl: String, // Secure proxy cover URL
    @Json(name = "duration") val duration: Long,
    @Json(name = "year") val year: Int? = null,
    @Json(name = "genre") val genre: String? = null
)

@JsonClass(generateAdapter = true)
data class BackendAlbumDto(
    @Json(name = "id") val id: String,
    @Json(name = "title") val title: String,
    @Json(name = "artist") val artist: String,
    @Json(name = "coverUrl") val coverUrl: String,
    @Json(name = "year") val year: Int? = null,
    @Json(name = "trackCount") val trackCount: Int? = null
)

@JsonClass(generateAdapter = true)
data class BackendDailyMixDto(
    @Json(name = "id") val id: String,
    @Json(name = "title") val title: String,
    @Json(name = "reason") val reason: String,
    @Json(name = "colors") val colors: List<String>, // Hex list e.g. ["#4F46E5", "#06B6D4"]
    @Json(name = "tracks") val tracks: List<BackendTrackDto>
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
    @Json(name = "coverUrl") val coverUrl: String
)

@JsonClass(generateAdapter = true)
data class BackendOnThisDayDto(
    @Json(name = "albumTitle") val albumTitle: String,
    @Json(name = "artist") val artist: String,
    @Json(name = "year") val year: Int,
    @Json(name = "timeAgo") val timeAgo: String,
    @Json(name = "coverUrl") val coverUrl: String,
    @Json(name = "matchReason") val matchReason: String,
    @Json(name = "tracks") val tracks: List<BackendTrackDto>
)

// ==========================================
// Custom Proxy Backend API Client Interface
// ==========================================

interface BackendApiService {
    
    // GET personalized home feed from your proxy backend
    @GET("api/v1/music/home")
    suspend fun getHomeFeed(
        @Header("Authorization") userAuthToken: String? = null // Optional client-to-backend authentication
    ): BackendHomeFeedResponse

    // GET Daily Mix details
    @GET("api/v1/music/mixes/{id}")
    suspend fun getDailyMixDetail(
        @Path("id") mixId: String
    ): BackendDailyMixDto

    // GET Radio Station queue
    @GET("api/v1/music/stations/{id}/queue")
    suspend fun getStationQueue(
        @Path("id") stationId: String,
        @Query("seedArtistId") seedArtistId: String? = null
    ): List<BackendTrackDto>

    // GET On This Day throwback content
    @GET("api/v1/music/on-this-day")
    suspend fun getOnThisDayContent(): BackendOnThisDayDto
}

// ==========================================
// Retrofit Client Manager for Backend Proxy
// ==========================================

object BackendClientManager {
    private var cachedService: BackendApiService? = null
    private var currentBackendUrl: String? = null

    private val okHttpClient = OkHttpClient.Builder()
        .connectTimeout(15, TimeUnit.SECONDS)
        .readTimeout(15, TimeUnit.SECONDS)
        .build()

    /** Retrieves a Retrofit client for an explicitly configured backend URL. */
    fun getApiService(backendUrl: String): BackendApiService {
        val normalizedUrl = if (backendUrl.endsWith("/")) backendUrl else "$backendUrl/"
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

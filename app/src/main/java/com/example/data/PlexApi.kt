package com.example.data

import com.squareup.moshi.Json
import com.squareup.moshi.JsonClass
import okhttp3.OkHttpClient
import retrofit2.Retrofit
import retrofit2.converter.moshi.MoshiConverterFactory
import retrofit2.http.*
import java.util.concurrent.TimeUnit

@JsonClass(generateAdapter = true)
data class PlexEnvelope<T>(
    @Json(name = "MediaContainer") val mediaContainer: T
)

@JsonClass(generateAdapter = true)
data class PlexLibrariesContainer(
    @Json(name = "Directory") val directory: List<PlexDirectory>? = null
)

@JsonClass(generateAdapter = true)
data class PlexDirectory(
    @Json(name = "key") val key: String,
    @Json(name = "title") val title: String,
    @Json(name = "type") val type: String
)

fun List<PlexDirectory>.musicLibraries(): List<PlexDirectory> = filter {
    it.type.equals("artist", ignoreCase = true) ||
        it.type.equals("music", ignoreCase = true)
}

@JsonClass(generateAdapter = true)
data class PlexMetadataContainer(
    @Json(name = "totalSize") val totalSize: Int? = null,
    @Json(name = "Metadata") val metadata: List<PlexMetadata>? = null
)

@JsonClass(generateAdapter = true)
data class PlexMetadata(
    @Json(name = "ratingKey") val ratingKey: String,
    @Json(name = "key") val key: String? = null,
    @Json(name = "title") val title: String,
    @Json(name = "type") val type: String? = null,
    @Json(name = "thumb") val thumb: String? = null,
    @Json(name = "parentTitle") val parentTitle: String? = null, // artist or album
    @Json(name = "grandparentTitle") val grandparentTitle: String? = null, // track artist
    @Json(name = "index") val index: Int? = null,
    @Json(name = "duration") val duration: Long? = null,
    @Json(name = "Media") val media: List<PlexMedia>? = null,
    @Json(name = "year") val year: Int? = null
    ,@Json(name = "addedAt") val addedAt: Long? = null
    ,@Json(name = "viewCount") val viewCount: Int? = null
    ,@Json(name = "lastViewedAt") val lastViewedAt: Long? = null
    ,@Json(name = "Genre") val genres: List<PlexTag>? = null
    ,@Json(name = "Collection") val collections: List<PlexTag>? = null
    ,@Json(name = "grandparentRatingKey") val grandparentRatingKey: String? = null
    ,@Json(name = "parentRatingKey") val parentRatingKey: String? = null
)

@JsonClass(generateAdapter = true)
data class PlexTag(@Json(name = "tag") val tag: String)

@JsonClass(generateAdapter = true)
data class PlexMedia(
    @Json(name = "Part") val part: List<PlexPart>? = null
)

@JsonClass(generateAdapter = true)
data class PlexPart(
    @Json(name = "key") val key: String? = null,
    @Json(name = "Stream") val streams: List<PlexStream>? = null
)

@JsonClass(generateAdapter = true)
data class PlexStream(
    @Json(name = "id") val id: String? = null,
    @Json(name = "streamType") val streamType: Int? = null,
    @Json(name = "key") val key: String? = null,
    @Json(name = "codec") val codec: String? = null,
    @Json(name = "format") val format: String? = null,
    @Json(name = "selected") val selected: Boolean? = null
)

interface PlexApiService {
    @GET("library/sections")
    suspend fun getLibraries(
        @Header("X-Plex-Token") token: String,
        @Header("Accept") accept: String = "application/json"
    ): PlexEnvelope<PlexLibrariesContainer>

    @GET("library/sections/{sectionId}/all")
    suspend fun getLibraryItems(
        @Path("sectionId") sectionId: String,
        @Query("type") type: String, // "8" for artists, "9" for albums, "10" for tracks
        @Query("X-Plex-Container-Start") offset: Int = 0,
        @Query("X-Plex-Container-Size") limit: Int = 200,
        @Header("X-Plex-Token") token: String,
        @Header("Accept") accept: String = "application/json"
    ): PlexEnvelope<PlexMetadataContainer>

    @GET("library/sections/{sectionId}/recentlyAdded")
    suspend fun getRecentlyAddedAlbums(
        @Path("sectionId") sectionId: String,
        @Query("type") type: String = "9",
        @Header("X-Plex-Token") token: String,
        @Header("Accept") accept: String = "application/json"
    ): PlexEnvelope<PlexMetadataContainer>

    @GET("library/metadata/{ratingKey}/children")
    suspend fun getChildren(
        @Path("ratingKey") ratingKey: String, // artistId -> albums or albumId -> tracks
        @Header("X-Plex-Token") token: String,
        @Header("Accept") accept: String = "application/json"
    ): PlexEnvelope<PlexMetadataContainer>

    @GET("library/metadata/{ratingKey}")
    suspend fun getMetadataDetail(
        @Path("ratingKey") ratingKey: String,
        @Header("X-Plex-Token") token: String,
        @Header("Accept") accept: String = "application/json"
    ): PlexEnvelope<PlexMetadataContainer>

    @GET("search")
    suspend fun globalSearch(
        @Query("query") query: String,
        @Header("X-Plex-Token") token: String,
        @Header("Accept") accept: String = "application/json"
    ): PlexEnvelope<PlexMetadataContainer>
}

object PlexClientManager {
    private var currentUrl: String? = null
    private var cachedService: PlexApiService? = null

    private val okHttpClient = OkHttpClient.Builder()
        .connectTimeout(30, TimeUnit.SECONDS)
        .readTimeout(30, TimeUnit.SECONDS)
        .writeTimeout(30, TimeUnit.SECONDS)
        .build()

    fun getApiService(baseUrl: String): PlexApiService {
        val normalizedUrl = if (baseUrl.endsWith("/")) baseUrl else "$baseUrl/"
        if (normalizedUrl == currentUrl && cachedService != null) {
            return cachedService!!
        }

        val retrofit = Retrofit.Builder()
            .baseUrl(normalizedUrl)
            .client(okHttpClient)
            .addConverterFactory(MoshiConverterFactory.create())
            .build()

        val service = retrofit.create(PlexApiService::class.java)
        currentUrl = normalizedUrl
        cachedService = service
        return service
    }
}

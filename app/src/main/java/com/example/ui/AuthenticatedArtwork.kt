package com.example.ui

import android.content.Context
import coil.request.ImageRequest
import com.example.R
import com.example.data.resolveArtworkReference
import com.example.data.PlexSettingsManager
import com.example.data.originCredentialsForUrl

fun resolveArtworkUrl(baseUrl: String, path: String): String {
    return resolveArtworkReference(baseUrl, path)
}

private object ArtworkSettingsCache {
    private val lock = Any()
    private var applicationContext: Context? = null
    private var settings: PlexSettingsManager? = null

    fun get(context: Context): PlexSettingsManager {
        val appContext = context.applicationContext
        synchronized(lock) {
            if (settings == null || applicationContext !== appContext) {
                applicationContext = appContext
                settings = PlexSettingsManager(appContext)
            }
            return settings!!
        }
    }
}

fun authenticatedArtworkRequest(
    context: Context,
    imageUrl: String,
    plexToken: String
): ImageRequest {
    val settings = ArtworkSettingsCache.get(context)
    val normalizedUrl = imageUrl.replace(" ", "%20")
    val credentials = originCredentialsForUrl(
        normalizedUrl,
        settings.companionBackendUrl,
        settings.companionBackendToken,
        settings.baseUrl,
        plexToken
    )

    // Keep the URL used by Coil consistent with the URL used for origin
    // matching. Plex artwork paths frequently contain spaces or other
    // characters that need encoding before the request is built.
    val fullUrl = normalizedUrl

    return ImageRequest.Builder(context.applicationContext)
        .data(fullUrl)
        .apply {
            credentials.headers
                .forEach { (name, value) -> addHeader(name, value) }
        }
        .placeholder(R.drawable.ic_artwork_placeholder)
        .error(R.drawable.ic_artwork_placeholder)
        .memoryCacheKey(fullUrl)
        .diskCacheKey(fullUrl)
        // Large lazy lists should not animate every thumbnail as it enters the
        // viewport. Full-screen surfaces still get the same decoded image.
        .crossfade(false)
        .build()
}

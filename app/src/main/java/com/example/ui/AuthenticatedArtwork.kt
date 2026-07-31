package com.example.ui

import android.content.Context
import coil.request.ImageRequest
import com.example.data.PlexSettingsManager

fun resolveArtworkUrl(baseUrl: String, path: String): String {
    val trimmed = path.trim()
    if (trimmed.startsWith("http://") || trimmed.startsWith("https://")) return trimmed
    return "${baseUrl.trimEnd('/')}$trimmed"
}

fun authenticatedArtworkRequest(
    context: Context,
    imageUrl: String,
    plexToken: String
): ImageRequest {
    val settings = PlexSettingsManager(context)
    val companionBase = settings.companionBackendUrl.trimEnd('/')
    val isCompanionUrl = companionBase.isNotBlank() &&
        (imageUrl == companionBase || imageUrl.startsWith("$companionBase/"))
    return ImageRequest.Builder(context)
        .data(imageUrl)
        .apply {
            if (isCompanionUrl && settings.companionBackendToken.isNotBlank()) {
                addHeader("Authorization", "Bearer ${settings.companionBackendToken}")
            } else if (plexToken.isNotBlank()) {
                addHeader("X-Plex-Token", plexToken)
            }
        }
        .crossfade(true)
        .build()
}

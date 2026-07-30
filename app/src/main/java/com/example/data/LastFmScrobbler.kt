package com.example.data

import android.content.Context
import android.util.Log
import android.widget.Toast
import com.example.playback.TrackItem
import com.example.BuildConfig
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import okhttp3.FormBody
import okhttp3.OkHttpClient
import okhttp3.Request
import java.security.MessageDigest

object LastFmScrobbler {
    private const val TAG = "LastFmScrobbler"
    private const val BASE_URL = "https://ws.audioscrobbler.com/2.0/"

    private val client = OkHttpClient()
    private val scope = CoroutineScope(Dispatchers.IO)

    fun updateNowPlaying(context: Context, settings: PlexSettingsManager, track: TrackItem) {
        if (!isConfigured(settings)) return

        scope.launch {
            Log.d(TAG, "Updating Now Playing on Last.fm for track: ${track.title} by ${track.artist}")
            val sessionKey = settings.lastFmSessionKey
            if (sessionKey.isEmpty()) {
                showToast(context, "Last.fm: Now playing '${track.title}'")
                return@launch
            }

            try {
                val params = mutableMapOf(
                    "method" to "track.updateNowPlaying",
                    "artist" to track.artist,
                    "track" to track.title,
                    "album" to track.album,
                    "api_key" to BuildConfig.LASTFM_API_KEY,
                    "sk" to sessionKey
                )
                val signature = calculateSignature(params)
                params["api_sig"] = signature
                params["format"] = "json"

                val bodyBuilder = FormBody.Builder()
                params.forEach { (k, v) -> bodyBuilder.add(k, v) }

                val request = Request.Builder()
                    .url(BASE_URL)
                    .post(bodyBuilder.build())
                    .build()

                client.newCall(request).execute().use { response ->
                    response.body?.close()
                    Log.d(TAG, "UpdateNowPlaying completed: HTTP ${response.code}")
                    if (response.isSuccessful) {
                        showToast(context, "Last.fm: Updated Now Playing")
                    } else {
                        Log.e(TAG, "Failed updateNowPlaying: HTTP ${response.code}")
                    }
                }
            } catch (e: Exception) {
                Log.e(TAG, "Error updating now playing", e)
            }
        }
    }

    fun scrobble(context: Context, settings: PlexSettingsManager, track: TrackItem) {
        if (!isConfigured(settings)) return

        scope.launch {
            Log.d(TAG, "Scrobbling to Last.fm: ${track.title} by ${track.artist}")
            val sessionKey = settings.lastFmSessionKey
            if (sessionKey.isEmpty()) {
                showToast(context, "Last.fm: Scrobbling '${track.title}'")
                return@launch
            }

            try {
                val timestamp = (System.currentTimeMillis() / 1000).toString()
                val params = mutableMapOf(
                    "method" to "track.scrobble",
                    "artist" to track.artist,
                    "track" to track.title,
                    "album" to track.album,
                    "timestamp" to timestamp,
                    "api_key" to BuildConfig.LASTFM_API_KEY,
                    "sk" to sessionKey
                )
                val signature = calculateSignature(params)
                params["api_sig"] = signature
                params["format"] = "json"

                val bodyBuilder = FormBody.Builder()
                params.forEach { (k, v) -> bodyBuilder.add(k, v) }

                val request = Request.Builder()
                    .url(BASE_URL)
                    .post(bodyBuilder.build())
                    .build()

                client.newCall(request).execute().use { response ->
                    response.body?.close()
                    Log.d(TAG, "Scrobble completed: HTTP ${response.code}")
                    if (response.isSuccessful) {
                        showToast(context, "Last.fm scrobble successful!")
                    } else {
                        Log.e(TAG, "Failed scrobble: HTTP ${response.code}")
                    }
                }
            } catch (e: Exception) {
                Log.e(TAG, "Error scrobbling", e)
            }
        }
    }

    private fun calculateSignature(params: Map<String, String>): String {
        val sortedKeys = params.keys.sorted()
        val signatureBuilder = StringBuilder()
        for (key in sortedKeys) {
            signatureBuilder.append(key).append(params[key])
        }
        signatureBuilder.append(BuildConfig.LASTFM_API_SECRET)
        return md5(signatureBuilder.toString())
    }

    private fun isConfigured(settings: PlexSettingsManager): Boolean {
        return settings.lastFmEnabled &&
            settings.lastFmUsername.isNotEmpty() &&
            BuildConfig.LASTFM_API_KEY.isNotBlank() &&
            !BuildConfig.LASTFM_API_KEY.contains("YOUR_LASTFM") &&
            BuildConfig.LASTFM_API_SECRET.isNotBlank() &&
            !BuildConfig.LASTFM_API_SECRET.contains("YOUR_LASTFM")
    }

    private fun md5(input: String): String {
        val md = MessageDigest.getInstance("MD5")
        val bytes = md.digest(input.toByteArray())
        return bytes.joinToString("") { "%02x".format(it) }
    }

    private fun showToast(context: Context, message: String) {
        scope.launch {
            withContext(Dispatchers.Main) {
                Toast.makeText(context, message, Toast.LENGTH_SHORT).show()
            }
        }
    }
}

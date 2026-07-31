package com.example.data

import android.content.Context
import android.util.Log
import android.widget.Toast
import com.example.playback.TrackItem
import android.net.Uri
import org.json.JSONObject
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
        if (!isConfigured(settings) || !settings.lastFmNowPlayingEnabled || settings.lastFmPrivateSession) return

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
                    "api_key" to settings.lastFmApiKey,
                    "sk" to sessionKey
                )
                val signature = calculateSignature(params, settings.lastFmApiSecret)
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
        if (!isConfigured(settings) || !settings.lastFmScrobbleEnabled || settings.lastFmPrivateSession) return

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
                    "api_key" to settings.lastFmApiKey,
                    "sk" to sessionKey
                )
                val signature = calculateSignature(params, settings.lastFmApiSecret)
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

    private fun calculateSignature(params: Map<String, String>, secret: String): String {
        val sortedKeys = params.keys.sorted()
        val signatureBuilder = StringBuilder()
        for (key in sortedKeys) {
            signatureBuilder.append(key).append(params[key])
        }
        signatureBuilder.append(secret)
        return md5(signatureBuilder.toString())
    }

    private fun isConfigured(settings: PlexSettingsManager): Boolean {
        return settings.lastFmEnabled &&
            settings.lastFmUsername.isNotEmpty() &&
            settings.lastFmApiKey.isNotBlank() && settings.lastFmApiSecret.isNotBlank() &&
            settings.lastFmSessionKey.isNotBlank()
    }

    fun authorizationUrl(settings: PlexSettingsManager): String? {
        if (settings.lastFmApiKey.isBlank()) return null
        val token = settings.lastFmPendingToken
        if (token.isBlank()) return null
        return Uri.parse("https://www.last.fm/api/auth/").buildUpon()
            .appendQueryParameter("api_key", settings.lastFmApiKey)
            .appendQueryParameter("token", token).build().toString()
    }

    suspend fun requestToken(settings: PlexSettingsManager): Result<String> = withContext(Dispatchers.IO) {
        requestAuth(settings, mapOf("method" to "auth.getToken", "api_key" to settings.lastFmApiKey))
            .map { token -> settings.lastFmPendingToken = token; token }
    }

    suspend fun completeAuthorization(settings: PlexSettingsManager): Result<String> = withContext(Dispatchers.IO) {
        val token = settings.lastFmPendingToken
        if (token.isBlank()) return@withContext Result.failure(IllegalStateException("Start authorization first"))
        requestAuth(settings, mapOf("method" to "auth.getSession", "api_key" to settings.lastFmApiKey, "token" to token))
            .map { raw ->
                val session = JSONObject(raw).getJSONObject("session")
                settings.lastFmUsername = session.getString("name")
                settings.lastFmSessionKey = session.getString("key")
                settings.lastFmPendingToken = ""
                settings.lastFmEnabled = true
                settings.lastFmUsername
            }
    }

    private fun requestAuth(settings: PlexSettingsManager, values: Map<String, String>): Result<String> {
        if (settings.lastFmApiKey.isBlank() || settings.lastFmApiSecret.isBlank()) return Result.failure(IllegalStateException("Enter API key and shared secret"))
        return try {
            val params = values.toMutableMap()
            params["api_sig"] = calculateSignature(params, settings.lastFmApiSecret)
            params["format"] = "json"
            val body = FormBody.Builder().apply { params.forEach { (k, v) -> add(k, v) } }.build()
            client.newCall(Request.Builder().url(BASE_URL).post(body).build()).execute().use { response ->
                val raw = response.body?.string().orEmpty()
                if (!response.isSuccessful) Result.failure(IllegalStateException("Last.fm HTTP ${response.code}"))
                else if (JSONObject(raw).has("error")) Result.failure(IllegalStateException(JSONObject(raw).optString("message", "Last.fm authorization failed")))
                else Result.success(if (values["method"] == "auth.getToken") JSONObject(raw).getString("token") else raw)
            }
        } catch (e: Exception) { Result.failure(e) }
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

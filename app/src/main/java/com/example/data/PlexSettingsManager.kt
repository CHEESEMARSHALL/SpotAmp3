package com.example.data

import android.content.Context
import android.content.SharedPreferences
import com.example.BuildConfig
import androidx.security.crypto.EncryptedSharedPreferences
import androidx.security.crypto.MasterKeys

class PlexSettingsManager(context: Context) {
    private val prefs: SharedPreferences

    companion object {
        private const val PREFS_NAME = "plex_settings_secure"
        private const val LEGACY_PREFS_NAME = "plex_settings"
        private const val KEY_BASE_URL = "plex_base_url"
        private const val KEY_TOKEN = "plex_token"
        private const val KEY_COMPANION_URL = "companion_backend_url"
        private const val KEY_COMPANION_TOKEN = "companion_backend_token"
        private const val KEY_COMPANION_DEVICE_ID = "companion_backend_device_id"
        private const val KEY_SECTION_ID = "plex_section_id"
        private const val KEY_LIBRARY_NAME = "plex_library_name"
        
        private const val KEY_THEME = "app_theme"
        private const val KEY_LASTFM_ENABLED = "lastfm_enabled"
        private const val KEY_LASTFM_USERNAME = "lastfm_username"
        private const val KEY_LASTFM_SESSION_KEY = "lastfm_session_key"
        private const val KEY_LASTFM_API_KEY = "lastfm_api_key"
        private const val KEY_LASTFM_API_SECRET = "lastfm_api_secret"
        private const val KEY_LASTFM_NOW_PLAYING = "lastfm_now_playing"
        private const val KEY_LASTFM_SCROBBLE = "lastfm_scrobble_enabled"
        private const val KEY_LASTFM_PRIVATE = "lastfm_private_session"
        private const val KEY_LASTFM_PENDING_TOKEN = "lastfm_pending_token"
        
        private const val KEY_GAPLESS = "gapless_enabled"
        private const val KEY_EQ_ENABLED = "eq_enabled"
        private const val KEY_EQ_PRESET = "eq_preset"
        private const val KEY_NORMALIZATION = "normalization_enabled"
        
        private const val KEY_AI_PROVIDER = "ai_provider"
        private const val KEY_OFFLINE_ONLY = "offline_only"
        private const val KEY_AI_MODEL_PATH = "ai_model_path"
        private const val KEY_AI_TIMEOUT = "ai_timeout_ms"
        private const val KEY_SYNC_INTERVAL = "sync_interval_hours"
        private const val KEY_GGUF_MODEL_SIZE = "gguf_model_size"
    }

    init {
        val legacy = context.getSharedPreferences(LEGACY_PREFS_NAME, Context.MODE_PRIVATE)
        
        var securedPrefs: SharedPreferences? = null
        try {
            val masterKey = MasterKeys.getOrCreate(MasterKeys.AES256_GCM_SPEC)
            securedPrefs = EncryptedSharedPreferences.create(
                PREFS_NAME,
                masterKey,
                context,
                EncryptedSharedPreferences.PrefKeyEncryptionScheme.AES256_SIV,
                EncryptedSharedPreferences.PrefValueEncryptionScheme.AES256_GCM
            )
        } catch (e: Exception) {
            android.util.Log.e("PlexSettingsManager", "Failed to initialize EncryptedSharedPreferences, falling back to legacy", e)
            // Fallback to legacy or a regular shared preference if encryption fails
            // This is common on some devices with corrupted keystores or no lock screen
        }
        
        prefs = securedPrefs ?: legacy

        if (securedPrefs != null && prefs.getString(KEY_TOKEN, null).isNullOrEmpty() && legacy.getString(KEY_TOKEN, null).orEmpty().isNotEmpty()) {
            try {
                val migration = prefs.edit()
                listOf(KEY_BASE_URL, KEY_TOKEN, KEY_COMPANION_URL, KEY_COMPANION_TOKEN, KEY_COMPANION_DEVICE_ID, KEY_SECTION_ID, KEY_LIBRARY_NAME, KEY_LASTFM_USERNAME, KEY_LASTFM_SESSION_KEY, KEY_LASTFM_API_KEY, KEY_LASTFM_API_SECRET, KEY_LASTFM_PENDING_TOKEN, KEY_THEME, KEY_EQ_PRESET, KEY_AI_PROVIDER).forEach { key ->
                    try { legacy.getString(key, null)?.let { migration.putString(key, it) } } catch (e: Exception) {}
                }
                listOf(KEY_LASTFM_ENABLED, KEY_LASTFM_NOW_PLAYING, KEY_LASTFM_SCROBBLE, KEY_LASTFM_PRIVATE, KEY_GAPLESS, KEY_EQ_ENABLED, KEY_NORMALIZATION).forEach { key ->
                    try { if (legacy.contains(key)) migration.putBoolean(key, legacy.getBoolean(key, false)) } catch (e: Exception) {}
                }
                migration.apply()
            } catch (e: Exception) {
                android.util.Log.e("PlexSettingsManager", "Error during preferences migration", e)
            }
        }
    }

    var activeTheme: String
        get() = prefs.getString(KEY_THEME, "Default Dark") ?: "Default Dark"
        set(value) = prefs.edit().putString(KEY_THEME, value).apply()

    var lastFmEnabled: Boolean
        get() = prefs.getBoolean(KEY_LASTFM_ENABLED, false)
        set(value) = prefs.edit().putBoolean(KEY_LASTFM_ENABLED, value).apply()

    var lastFmUsername: String
        get() = prefs.getString(KEY_LASTFM_USERNAME, "") ?: ""
        set(value) = prefs.edit().putString(KEY_LASTFM_USERNAME, value).apply()

    var lastFmSessionKey: String
        get() = prefs.getString(KEY_LASTFM_SESSION_KEY, "") ?: ""
        set(value) = prefs.edit().putString(KEY_LASTFM_SESSION_KEY, value).apply()

    var lastFmApiKey: String
        get() = prefs.getString(KEY_LASTFM_API_KEY, "") ?: ""
        set(value) = prefs.edit().putString(KEY_LASTFM_API_KEY, value.trim()).apply()
    var lastFmApiSecret: String
        get() = prefs.getString(KEY_LASTFM_API_SECRET, "") ?: ""
        set(value) = prefs.edit().putString(KEY_LASTFM_API_SECRET, value.trim()).apply()
    var lastFmNowPlayingEnabled: Boolean
        get() = prefs.getBoolean(KEY_LASTFM_NOW_PLAYING, true)
        set(value) = prefs.edit().putBoolean(KEY_LASTFM_NOW_PLAYING, value).apply()
    var lastFmScrobbleEnabled: Boolean
        get() = prefs.getBoolean(KEY_LASTFM_SCROBBLE, true)
        set(value) = prefs.edit().putBoolean(KEY_LASTFM_SCROBBLE, value).apply()
    var lastFmPrivateSession: Boolean
        get() = prefs.getBoolean(KEY_LASTFM_PRIVATE, false)
        set(value) = prefs.edit().putBoolean(KEY_LASTFM_PRIVATE, value).apply()
    var lastFmPendingToken: String
        get() = prefs.getString(KEY_LASTFM_PENDING_TOKEN, "") ?: ""
        set(value) = prefs.edit().putString(KEY_LASTFM_PENDING_TOKEN, value).apply()

    var gaplessEnabled: Boolean
        get() = prefs.getBoolean(KEY_GAPLESS, true)
        set(value) = prefs.edit().putBoolean(KEY_GAPLESS, value).apply()

    var equalizerEnabled: Boolean
        get() = prefs.getBoolean(KEY_EQ_ENABLED, false)
        set(value) = prefs.edit().putBoolean(KEY_EQ_ENABLED, value).apply()

    var equalizerPreset: String
        get() = prefs.getString(KEY_EQ_PRESET, "Flat") ?: "Flat"
        set(value) = prefs.edit().putString(KEY_EQ_PRESET, value).apply()

    var normalizationEnabled: Boolean
        get() = prefs.getBoolean(KEY_NORMALIZATION, false)
        set(value) = prefs.edit().putBoolean(KEY_NORMALIZATION, value).apply()

    var aiProvider: String
        get() = prefs.getString(KEY_AI_PROVIDER, "LocalAIProvider") ?: "LocalAIProvider"
        set(value) = prefs.edit().putString(KEY_AI_PROVIDER, value).apply()

    var offlineOnly: Boolean
        get() = prefs.getBoolean(KEY_OFFLINE_ONLY, false)
        set(value) = prefs.edit().putBoolean(KEY_OFFLINE_ONLY, value).apply()


    var aiModelPath: String
        get() = prefs.getString(KEY_AI_MODEL_PATH, "") ?: ""
        set(value) = prefs.edit().putString(KEY_AI_MODEL_PATH, value).apply()

    var aiTimeoutMs: Long
        get() = prefs.getLong(KEY_AI_TIMEOUT, 15_000L)
        set(value) = prefs.edit().putLong(KEY_AI_TIMEOUT, value).apply()

    var syncIntervalHours: Long
        get() = prefs.getLong(KEY_SYNC_INTERVAL, 0L) // 0 means disabled
        set(value) = prefs.edit().putLong(KEY_SYNC_INTERVAL, value).apply()

    var ggufModelSize: String
        get() = prefs.getString(KEY_GGUF_MODEL_SIZE, "2B") ?: "2B"
        set(value) = prefs.edit().putString(KEY_GGUF_MODEL_SIZE, value).apply()

    var baseUrl: String
        get() = prefs.getString(KEY_BASE_URL, "") ?: ""
        set(value) = prefs.edit().putString(KEY_BASE_URL, value).apply()

    var token: String
        get() = prefs.getString(KEY_TOKEN, "") ?: ""
        set(value) = prefs.edit().putString(KEY_TOKEN, value).apply()

    var companionBackendUrl: String
        get() = prefs.getString(KEY_COMPANION_URL, "") ?: ""
        set(value) = prefs.edit().putString(KEY_COMPANION_URL, value).apply()

    var companionBackendToken: String
        get() = prefs.getString(KEY_COMPANION_TOKEN, "") ?: ""
        set(value) = prefs.edit().putString(KEY_COMPANION_TOKEN, value).apply()

    var companionDeviceId: String
        get() = prefs.getString(KEY_COMPANION_DEVICE_ID, "") ?: ""
        set(value) = prefs.edit().putString(KEY_COMPANION_DEVICE_ID, value).apply()

    /** Stores a newly claimed credential as one preference transaction. */
    fun saveCompanionCredential(url: String, deviceId: String, token: String) {
        val normalizedUrl = normalizeCompanionUrl(url, allowPrivateHttp = true)
        val oldOrigin = companionOrigin(companionBackendUrl)
        val newOrigin = companionOrigin(normalizedUrl)
        val originChanged = oldOrigin != null && newOrigin != null && oldOrigin != newOrigin
        if (originChanged && token.trim() == companionBackendToken.trim()) {
            clearCompanionCredential()
            throw IllegalArgumentException("SpotCore endpoint changed; scan a fresh Add Device QR code")
        }
        prefs.edit()
            .putString(KEY_COMPANION_URL, normalizedUrl)
            .putString(KEY_COMPANION_DEVICE_ID, deviceId.trim().ifBlank { companionDeviceIdFromToken(token).orEmpty() })
            .putString(KEY_COMPANION_TOKEN, token.trim())
            .apply()
    }

    fun clearCompanionCredential() {
        prefs.edit()
            .remove(KEY_COMPANION_URL)
            .remove(KEY_COMPANION_DEVICE_ID)
            .remove(KEY_COMPANION_TOKEN)
            .apply()
    }

    val isCompanionConfigured: Boolean
        get() = companionBackendUrl.isNotBlank() && companionBackendToken.isNotBlank()

    val isPlexConfigured: Boolean
        get() = baseUrl.isNotEmpty() && token.isNotEmpty()

    var sectionId: String
        get() {
            val saved = prefs.getString(KEY_SECTION_ID, "") ?: ""
            if (saved.isNotEmpty()) return saved
            return if (isCompanionConfigured) "spotcore" else ""
        }
        set(value) = prefs.edit().putString(KEY_SECTION_ID, value).apply()

    var libraryName: String
        get() {
            val saved = prefs.getString(KEY_LIBRARY_NAME, "") ?: ""
            if (saved.isNotEmpty()) return saved
            return if (isCompanionConfigured) "SpotCore" else ""
        }
        set(value) = prefs.edit().putString(KEY_LIBRARY_NAME, value).apply()

    var lyricsDirectory: String
        get() = prefs.getString("lyrics_directory", "") ?: ""
        set(value) = prefs.edit().putString("lyrics_directory", value).apply()

    val isConfigured: Boolean
        get() = (baseUrl.isNotEmpty() && token.isNotEmpty()) || isCompanionConfigured
}

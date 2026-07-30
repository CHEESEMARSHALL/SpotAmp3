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
        private const val KEY_SECTION_ID = "plex_section_id"
        private const val KEY_LIBRARY_NAME = "plex_library_name"
        
        private const val KEY_THEME = "app_theme"
        private const val KEY_LASTFM_ENABLED = "lastfm_enabled"
        private const val KEY_LASTFM_USERNAME = "lastfm_username"
        private const val KEY_LASTFM_SESSION_KEY = "lastfm_session_key"
        
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
        val masterKey = MasterKeys.getOrCreate(MasterKeys.AES256_GCM_SPEC)
        prefs = EncryptedSharedPreferences.create(
            PREFS_NAME,
            masterKey,
            context,
            EncryptedSharedPreferences.PrefKeyEncryptionScheme.AES256_SIV,
            EncryptedSharedPreferences.PrefValueEncryptionScheme.AES256_GCM
        )
        if (prefs.getString(KEY_TOKEN, null).isNullOrEmpty() && legacy.getString(KEY_TOKEN, null).orEmpty().isNotEmpty()) {
            val migration = prefs.edit()
            listOf(KEY_BASE_URL, KEY_TOKEN, KEY_SECTION_ID, KEY_LIBRARY_NAME, KEY_LASTFM_USERNAME, KEY_LASTFM_SESSION_KEY, KEY_THEME, KEY_EQ_PRESET, KEY_AI_PROVIDER).forEach { key ->
                legacy.getString(key, null)?.let { migration.putString(key, it) }
            }
            listOf(KEY_LASTFM_ENABLED, KEY_GAPLESS, KEY_EQ_ENABLED, KEY_NORMALIZATION).forEach { key ->
                if (legacy.contains(key)) migration.putBoolean(key, legacy.getBoolean(key, false))
            }
            migration.apply()
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
        get() = prefs.getString(KEY_AI_PROVIDER, "CloudAIProvider") ?: "CloudAIProvider"
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
        get() {
            val saved = prefs.getString(KEY_BASE_URL, "") ?: ""
            if (saved.isNotEmpty()) return saved
            // Fallback to BuildConfig if configured and is not default placeholder
            val envUrl = BuildConfig.PLEX_BASE_URL
            return if (envUrl.isNotEmpty() && !envUrl.contains("localhost")) envUrl else ""
        }
        set(value) = prefs.edit().putString(KEY_BASE_URL, value).apply()

    var token: String
        get() {
            val saved = prefs.getString(KEY_TOKEN, "") ?: ""
            if (saved.isNotEmpty()) return saved
            // Fallback to BuildConfig if configured and is not default placeholder
            val envToken = BuildConfig.PLEX_TOKEN
            return if (envToken.isNotEmpty() && !envToken.contains("YOUR_PLEX_TOKEN")) envToken else ""
        }
        set(value) = prefs.edit().putString(KEY_TOKEN, value).apply()

    var sectionId: String
        get() = prefs.getString(KEY_SECTION_ID, "") ?: ""
        set(value) = prefs.edit().putString(KEY_SECTION_ID, value).apply()

    var libraryName: String
        get() = prefs.getString(KEY_LIBRARY_NAME, "") ?: ""
        set(value) = prefs.edit().putString(KEY_LIBRARY_NAME, value).apply()

    var lyricsDirectory: String
        get() = prefs.getString("lyrics_directory", "") ?: ""
        set(value) = prefs.edit().putString("lyrics_directory", value).apply()

    val isConfigured: Boolean
        get() = baseUrl.isNotEmpty() && token.isNotEmpty()
}

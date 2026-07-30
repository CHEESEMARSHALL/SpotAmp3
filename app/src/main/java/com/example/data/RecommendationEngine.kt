package com.example.data

import android.content.Context
import android.util.Log
import com.example.BuildConfig
import com.example.playback.TrackItem
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.coroutines.flow.firstOrNull
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import java.util.Locale

// ==============================================================
// 1. Sonic Sage Structured PlaylistIntent JSON Schema
// ==============================================================
@Serializable
data class PlaylistIntent(
    val seedArtists: List<String> = emptyList(),
    val seedAlbums: List<String> = emptyList(),
    val seedTracks: List<String> = emptyList(),
    val genres: List<String> = emptyList(),
    val moods: List<String> = emptyList(),
    val energyLevel: String? = null, // "high", "medium", "low"
    val decadeRange: String? = null, // e.g. "2000s", "1990s"
    val includeCollections: Boolean = true,
    val excludeGenres: List<String> = emptyList(),
    val excludeArtists: List<String> = emptyList(),
    val preferFamiliar: Boolean = true,
    val preferDeepCuts: Boolean = false,
    val targetTrackCount: Int = 30,
    val sortStrategy: String? = null,
    val explanation: String? = null
)

@Serializable
data class SmartSearchIntent(
    val textTerms: List<String> = emptyList(),
    val artists: List<String> = emptyList(),
    val albums: List<String> = emptyList(),
    val genres: List<String> = emptyList(),
    val collections: List<String> = emptyList(),
    val decade: Int? = null,
    val downloadedOnly: Boolean = false,
    val recentlyPlayed: Boolean = false,
    val recentlyAdded: Boolean = false,
    val instrumentalOnly: Boolean = false,
    val deepCutsOnly: Boolean = false,
    val minPlayCount: Int? = null
)

@Serializable
data class AppCommand(val action: String, val query: String? = null, val ratingKey: String? = null)

@Serializable
data class MoodTagResult(val moods: List<String> = emptyList(), val energy: String = "unknown", val styleTags: List<String> = emptyList())

@Serializable
data class RecommendationExplanationRequest(val shelf: String, val ratingKeys: List<String>)

@Serializable
data class LibraryCleanupSuggestion(val ratingKey: String, val issue: String, val confidence: Float = 0f, val suggestion: String? = null)

@Serializable
data class CollectionSuggestion(val name: String, val ratingKeys: List<String> = emptyList(), val reason: String = "")

@Serializable
data class DJBlurbRequest(val ratingKey: String, val context: String = "now-playing")

data class ValidationResult<T>(val value: T?, val errors: List<String>) {
    val isValid: Boolean get() = value != null && errors.isEmpty()
}

object AIOutputValidator {
    fun playlistIntent(intent: PlaylistIntent): ValidationResult<PlaylistIntent> {
        val errors = mutableListOf<String>()
        if (intent.targetTrackCount !in 1..200) errors += "targetTrackCount must be between 1 and 200"
        if (intent.energyLevel != null && intent.energyLevel !in setOf("low", "medium", "high", "any")) errors += "energyLevel is invalid"
        val collections = listOf(
            "seedArtists" to intent.seedArtists,
            "seedAlbums" to intent.seedAlbums,
            "seedTracks" to intent.seedTracks,
            "genres" to intent.genres,
            "moods" to intent.moods,
            "excludeArtists" to intent.excludeArtists,
            "excludeGenres" to intent.excludeGenres
        )
        collections.forEach { (name, values) ->
            if (values.size > 50) errors += "$name contains too many values"
            if (values.any { it.isBlank() }) errors += "$name contains a blank value"
        }
        return ValidationResult(intent.takeIf { errors.isEmpty() }, errors)
    }

    fun smartSearch(intent: SmartSearchIntent): ValidationResult<SmartSearchIntent> {
        val errors = mutableListOf<String>()
        if (intent.decade != null && intent.decade !in 1900..2100) errors += "decade is invalid"
        if (intent.decade != null && intent.decade % 10 != 0) errors += "decade must start on a decade boundary"
        if (intent.minPlayCount != null && intent.minPlayCount < 0) errors += "minPlayCount cannot be negative"
        if (intent.minPlayCount != null && intent.minPlayCount > 1_000_000) errors += "minPlayCount is too large"
        return ValidationResult(intent.takeIf { errors.isEmpty() }, errors)
    }

    fun appCommand(command: AppCommand): ValidationResult<AppCommand> {
        val allowed = setOf("PLAY", "PAUSE", "NEXT", "PREVIOUS", "SEARCH", "START_RADIO", "ADD_TO_QUEUE")
        val normalizedAction = command.action.trim().uppercase(Locale.ROOT)
        val errors = if (normalizedAction in allowed) emptyList() else listOf("action is not supported")
        val normalized = command.copy(action = normalizedAction, query = command.query?.trim())
        return ValidationResult(normalized.takeIf { errors.isEmpty() }, errors)
    }
}

class SmartSearchService {
    private val stopWords = setOf("the", "and", "from", "with", "show", "find", "music", "songs", "albums", "tracks", "that", "have", "been", "only")

    fun isStructuredQuery(query: String): Boolean {
        val normalized = query.lowercase(Locale.ROOT)
        return listOf(
            "downloaded", "offline", "recently played", "recent plays", "played lately",
            "recently added", "newly added", "instrumental", "without vocals",
            "deep cut", "deep cuts", "plays", "times"
        ).any(normalized::contains) || Regex("(19|20)\\d0s").containsMatchIn(normalized)
    }

    fun parse(query: String, cachedTracks: List<CachedTrack>): SmartSearchIntent {
        val normalized = query.lowercase(Locale.ROOT)
        val artists = cachedTracks.map { it.artist }.distinct().filter { normalized.contains(it.lowercase(Locale.ROOT)) }
        val albums = cachedTracks.map { it.album }.distinct().filter { normalized.contains(it.lowercase(Locale.ROOT)) }
        val genreWords = listOf("rock", "metal", "metalcore", "pop", "jazz", "classical", "rap", "hip hop", "electronic", "ambient", "soundtrack", "anime")
            .filter { normalized.contains(it) }
        val collectionWords = cachedTracks.flatMap { it.collections.split('|') }.filter { it.isNotBlank() }.distinct()
            .filter { normalized.contains(it.lowercase(Locale.ROOT)) }
        val decade = Regex("(19|20)\\d0s").find(normalized)?.value?.take(4)?.toIntOrNull()
        val minPlayCount = Regex("(\\d+)\\s*(?:plays|times)").find(normalized)?.groupValues?.getOrNull(1)?.toIntOrNull()
        return SmartSearchIntent(
            textTerms = query.lowercase(Locale.ROOT).split(Regex("\\s+"))
                .map { it.trim(',', '.', '!', '?') }
                .filter { it.length > 2 && it !in stopWords && !it.matches(Regex("(19|20)\\d0s")) },
            artists = artists,
            albums = albums,
            genres = genreWords,
            collections = collectionWords,
            decade = decade,
            downloadedOnly = normalized.contains("downloaded") || normalized.contains("offline"),
            recentlyPlayed = normalized.contains("recently played") || normalized.contains("recent plays") || normalized.contains("played lately"),
            recentlyAdded = normalized.contains("recently added") || normalized.contains("newly added"),
            instrumentalOnly = normalized.contains("instrumental") || normalized.contains("without vocals"),
            deepCutsOnly = normalized.contains("deep cut") || normalized.contains("deep cuts"),
            minPlayCount = minPlayCount
        )
    }

    fun filter(intent: SmartSearchIntent, tracks: List<CachedTrack>, downloadedKeys: Set<String> = emptySet()): List<CachedTrack> {
        return tracks.map { track ->
            var score = 0
            val haystack = "${track.title} ${track.artist} ${track.album} ${track.genres} ${track.collections}".lowercase(Locale.ROOT)
            val genreMatch = intent.genres.isEmpty() || intent.genres.any { haystack.contains(it) }
            if (!genreMatch) return@map track to -1
            if (intent.decade != null && track.year !in intent.decade..(intent.decade + 9)) return@map track to -1
            if (intent.recentlyPlayed && track.lastPlayedAt == null) return@map track to -1
            if (intent.recentlyAdded && (track.addedAt == null || track.addedAt < System.currentTimeMillis() / 1000L - 30L * 24L * 60L * 60L)) return@map track to -1
            if (intent.instrumentalOnly && !haystack.contains("instrumental") && !haystack.contains("classical") && !haystack.contains("ambient")) return@map track to -1
            if (intent.deepCutsOnly && track.playCount > 0) return@map track to -1
            if (intent.minPlayCount != null && track.playCount < intent.minPlayCount) return@map track to -1
            if (intent.artists.any { track.artist.equals(it, true) }) score += 100
            if (intent.albums.any { track.album.equals(it, true) }) score += 100
            if (intent.genres.any { haystack.contains(it) }) score += 40
            if (intent.collections.any { track.collections.contains(it, true) }) score += 40
            if (intent.decade != null && track.year?.let { it in intent.decade..(intent.decade + 9) } == true) score += 30
            if (intent.recentlyPlayed && track.lastPlayedAt != null) score += 30
            if (intent.minPlayCount != null && track.playCount >= intent.minPlayCount) score += 30
            intent.textTerms.filter { haystack.contains(it.lowercase(Locale.ROOT)) }.forEach { score += 5 }
            if (intent.downloadedOnly && track.ratingKey !in downloadedKeys) score = -1
            track to score
        }.filter { it.second >= 0 && (it.second > 0 || intent.textTerms.isEmpty()) }
            .sortedWith(compareByDescending<Pair<CachedTrack, Int>> { it.second }.thenBy { it.first.ratingKey })
            .take(100)
            .map { it.first }
    }
}

// ==============================================================
// 2. AIProvider Interface & Implementations (Cloud, Local, NoAI)
// ==============================================================
interface AIProvider {
    val name: String
    suspend fun generatePlaylistIntent(prompt: String, cachedTracks: List<CachedTrack>): PlaylistIntent
}

data class LocalAIStatus(
    val loaded: Boolean,
    val modelPath: String,
    val backend: String = "Rules-based fallback",
    val lastError: String? = null
)

class HybridAIProvider : AIProvider {
    private val local = LocalAIProvider()
    override val name = "Hybrid AI (Local first)"
    override suspend fun generatePlaylistIntent(prompt: String, cachedTracks: List<CachedTrack>): PlaylistIntent =
        local.generatePlaylistIntent(prompt, cachedTracks)
}

class CloudAIProvider : AIProvider {
    override val name = "Cloud AI Provider"

    override suspend fun generatePlaylistIntent(prompt: String, cachedTracks: List<CachedTrack>): PlaylistIntent = withContext(Dispatchers.IO) {
        val geminiApiKey = BuildConfig.GEMINI_API_KEY
        if (geminiApiKey.isEmpty() || geminiApiKey.contains("GEMINI_API_KEY")) {
            throw IllegalStateException("Gemini API key is not configured in the Secrets panel.")
        }

        // We summarize artists and genres to help Gemini select correct seed info
        val availableArtists = cachedTracks.map { it.artist }.distinct().take(100).joinToString(", ")
        val availableAlbums = cachedTracks.map { it.album }.distinct().take(50).joinToString(", ")

        val systemInstructionText = """
            You are a Sonic Sage AI music interpreter. You translate natural language user queries into a structured playlist intent JSON object.
            You must analyze the user prompt and extract seeds, genres, and styles.
            
            IMPORTANT:
            - Never invent track titles from memory.
            - Only reference the provided lists of available artists and albums if possible.
            - Ensure output strictly matches the PlaylistIntent JSON schema.
            - Do not return any markdown formatting, backticks, or other text. Only valid raw JSON.
            
            AVAILABLE ARTISTS SAMPLE: $availableArtists
            AVAILABLE ALBUMS SAMPLE: $availableAlbums
            
            JSON Schema:
            {
              "seedArtists": ["ArtistName"],
              "seedAlbums": ["AlbumName"],
              "seedTracks": [],
              "genres": ["metalcore", "soundtrack"],
              "moods": ["dark", "energetic"],
              "energyLevel": "high",
              "decadeRange": "2000s",
              "includeCollections": true,
              "excludeGenres": [],
              "excludeArtists": [],
              "preferFamiliar": true,
              "preferDeepCuts": false,
              "targetTrackCount": 30,
              "sortStrategy": "high_energy",
              "explanation": "Brief explanation of interpretation"
            }
        """.trimIndent()

        val request = GeminiRequest(
            contents = listOf(GeminiContent(parts = listOf(GeminiPart(text = prompt)))),
            systemInstruction = GeminiContent(parts = listOf(GeminiPart(text = systemInstructionText))),
            generationConfig = GeminiGenerationConfig(
                responseMimeType = "application/json",
                temperature = 0.2f
            )
        )

        try {
            val response = GeminiClient.service.generateContent(geminiApiKey, request)
            val jsonText = response.candidates?.firstOrNull()?.content?.parts?.firstOrNull()?.text ?: ""
            Log.d("CloudAIProvider", "Gemini response received (${jsonText.length} chars)")

            val json = Json { 
                ignoreUnknownKeys = true
                coerceInputValues = true
            }
            json.decodeFromString<PlaylistIntent>(jsonText)
        } catch (e: Exception) {
            Log.e("CloudAIProvider", "Error parsing Gemini response, falling back to Local parser", e)
            // Fallback to local parser on parse error or network error
            LocalAIProvider().generatePlaylistIntent(prompt, cachedTracks)
        }
    }
}

class LocalAIProvider : AIProvider {
    override val name = "Local AI (On-Device Parser)"

    // -------------------------------------------------------------------------------------
    // NOTE: ON-DEVICE LLM INTEGRATION ARCHITECTURE COMMENTS
    // -------------------------------------------------------------------------------------
    // To implement a fully local offline LLM model later:
    // 1. MediaPipe LLM Inference API:
    //    Add 'com.google.mediapipe:tasks-genai:0.10.14' to dependencies.
    //    Load a quantized Gemma 2B or Llama 3 8B (int4) `.bin` or `.task` file from assets or external storage.
    //    Use LlmInference.createFromOptions(context, options) and call inference.generateResponse(prompt).
    // 2. llama.cpp / ONNX Runtime Mobile:
    //    Package native shared libraries (.so files) via CMake or JNI.
    //    Expose native method: external fun nativeGenerate(prompt: String): String
    //    Load standard GGUF model files.
    // 3. MLC LLM:
    //    Use mlc-app framework to host local LLM engines in WebGPU / OpenCL Vulkan pipelines.
    // -------------------------------------------------------------------------------------

    override suspend fun generatePlaylistIntent(prompt: String, cachedTracks: List<CachedTrack>): PlaylistIntent = withContext(Dispatchers.Default) {
        Log.d("LocalAIProvider", "Using fast, private local rules-based engine")
        
        val lowercasePrompt = prompt.lowercase(Locale.ROOT)
        
        // Simple regex/keyword matching for seeds
        val matchedArtists = mutableListOf<String>()
        val matchedAlbums = mutableListOf<String>()
        val matchedGenres = mutableListOf<String>()
        val matchedMoods = mutableListOf<String>()
        var energy = "medium"
        var decade: String? = null

        // Scan library artists & albums to see if they are explicitly mentioned
        cachedTracks.forEach { track ->
            val art = track.artist
            if (lowercasePrompt.contains(art.lowercase(Locale.ROOT)) && !matchedArtists.contains(art)) {
                matchedArtists.add(art)
            }
            val alb = track.album
            if (lowercasePrompt.contains(alb.lowercase(Locale.ROOT)) && !matchedAlbums.contains(alb)) {
                matchedAlbums.add(alb)
            }
        }

        // Genre matcher
        val genreKeywords = listOf("rock", "metal", "metalcore", "pop", "jazz", "classical", "rap", "hip hop", "soundtrack", "anime", "electronic", "dance", "ambient")
        for (genre in genreKeywords) {
            if (lowercasePrompt.contains(genre)) {
                matchedGenres.add(genre)
            }
        }

        // Mood / Energy matcher
        if (lowercasePrompt.contains("workout") || lowercasePrompt.contains("energetic") || lowercasePrompt.contains("fast") || lowercasePrompt.contains("intense") || lowercasePrompt.contains("heavy")) {
            energy = "high"
            matchedMoods.add("intense")
            matchedMoods.add("energetic")
        } else if (lowercasePrompt.contains("relax") || lowercasePrompt.contains("chill") || lowercasePrompt.contains("calm") || lowercasePrompt.contains("sleep") || lowercasePrompt.contains("ambient")) {
            energy = "low"
            matchedMoods.add("calm")
            matchedMoods.add("peaceful")
        }

        if (lowercasePrompt.contains("dark") || lowercasePrompt.contains("sad") || lowercasePrompt.contains("melancholic")) {
            matchedMoods.add("dark")
        }

        // Decade matcher
        val decadeRegex = "(\\d{4})s|(\\d{4})".toRegex()
        val matchResult = decadeRegex.find(lowercasePrompt)
        if (matchResult != null) {
            val valStr = matchResult.value
            decade = if (valStr.endsWith("s")) valStr else "${valStr.take(3)}0s"
        }

        PlaylistIntent(
            seedArtists = matchedArtists,
            seedAlbums = matchedAlbums,
            genres = matchedGenres,
            moods = matchedMoods,
            energyLevel = energy,
            decadeRange = decade,
            explanation = "Local intent parsing extracted ${matchedArtists.size} artists, ${matchedGenres.size} genres, and ${matchedMoods.size} moods dynamically.",
            targetTrackCount = 25
        )
    }
}

// ==============================================================
// 2b. Native llama.cpp / GGUF Local LLM Bindings Bridge
// ==============================================================
object LlamaCppBridge {
    private var isLoaded = false
    private var loadError: String? = null

    init {
        try {
            System.loadLibrary("llama-android")
            isLoaded = true
        } catch (e: UnsatisfiedLinkError) {
            loadError = e.message
            isLoaded = false
        } catch (e: Exception) {
            loadError = e.message
            isLoaded = false
        }
    }

    fun isNativeLibraryLoaded(): Boolean = isLoaded
    fun getLoadError(): String? = loadError

    // Native functions mirroring llama.cpp's standard simple interface
    external fun initModel(modelPath: String, contextSize: Int, temp: Float): Long
    external fun generate(modelPtr: Long, prompt: String, systemPrompt: String, maxTokens: Int, temp: Float): String
    external fun freeModel(modelPtr: Long)
}

class LlamaCppAIProvider(
    private val modelPath: String,
    private val contextSize: Int = 2048,
    private val temperature: Float = 0.7f,
    private val modelPresetSize: String = "2B" // e.g. "1.5B", "2B", "4B"
) : AIProvider {
    override val name = "Local GGUF via llama.cpp ($modelPresetSize)"

    override suspend fun generatePlaylistIntent(prompt: String, cachedTracks: List<CachedTrack>): PlaylistIntent = withContext(Dispatchers.Default) {
        Log.d("LlamaCppAIProvider", "Invoking local GGUF LLM at $modelPath (Profile: $modelPresetSize)")
        
        if (modelPath.isBlank()) {
            val fallbackIntent = LocalAIProvider().generatePlaylistIntent(prompt, cachedTracks)
            return@withContext fallbackIntent.copy(
                explanation = "GGUF model path not configured in Settings. [Fallback to local rules engine] " + (fallbackIntent.explanation ?: "")
            )
        }

        if (!LlamaCppBridge.isNativeLibraryLoaded()) {
            val fallbackIntent = LocalAIProvider().generatePlaylistIntent(prompt, cachedTracks)
            val errMsg = LlamaCppBridge.getLoadError() ?: "libllama-android.so library not loaded"
            return@withContext fallbackIntent.copy(
                explanation = "llama.cpp JNI bindings (.so) not compiled for this architecture. Running offline rules engine fallback.\n" +
                        "Model config: $modelPresetSize at $modelPath\n" +
                        "Status: $errMsg\n\n" + (fallbackIntent.explanation ?: "")
            )
        }

        try {
            val availableArtists = cachedTracks.map { it.artist }.distinct().take(100).joinToString(", ")
            val systemPrompt = """
                You are a Sonic Sage AI music interpreter. You translate natural language user queries into a structured playlist intent JSON object.
                You must analyze the user prompt and extract seeds, genres, and styles. Only output RAW JSON according to this schema:
                {
                  "seedArtists": ["ArtistName"],
                  "seedAlbums": ["AlbumName"],
                  "genres": ["metalcore", "soundtrack"],
                  "moods": ["dark", "energetic"],
                  "energyLevel": "high"
                }
                Artists Sample: $availableArtists
            """.trimIndent()

            val modelPtr = LlamaCppBridge.initModel(modelPath, contextSize, temperature)
            if (modelPtr == 0L) {
                throw IllegalStateException("Failed to load GGUF model at $modelPath")
            }

            val jsonText = try {
                LlamaCppBridge.generate(modelPtr, prompt, systemPrompt, maxTokens = 512, temp = temperature)
            } finally {
                LlamaCppBridge.freeModel(modelPtr)
            }

            Log.d("LlamaCppAIProvider", "Received native GGUF response: $jsonText")

            val json = Json { 
                ignoreUnknownKeys = true
                coerceInputValues = true
            }
            json.decodeFromString<PlaylistIntent>(jsonText)
        } catch (e: Exception) {
            Log.e("LlamaCppAIProvider", "Error in native GGUF generation", e)
            val fallbackIntent = LocalAIProvider().generatePlaylistIntent(prompt, cachedTracks)
            fallbackIntent.copy(
                explanation = "Failed to run GGUF model ($modelPresetSize) at path '$modelPath': ${e.localizedMessage}. [Fell back to local rules engine]\n\n" + (fallbackIntent.explanation ?: "")
            )
        }
    }
}

class NoAIProvider : AIProvider {
    override val name = "No AI (Pure Algorithms)"

    override suspend fun generatePlaylistIntent(prompt: String, cachedTracks: List<CachedTrack>): PlaylistIntent = withContext(Dispatchers.Default) {
        Log.d("NoAIProvider", "No AI fallback activated. Using deterministic local library ranking.")
        PlaylistIntent(
            explanation = "Pure local algorithmic fallback selection.",
            targetTrackCount = 20,
            preferFamiliar = true
        )
    }
}

// ==============================================================
// 3. Robust RecommendationEngine Scoring and Pipeline
// ==============================================================
class RecommendationEngine(private val context: Context) {
    private val database = MusicDatabase.getDatabase(context)
    private val musicDao = database.musicDao()

    suspend fun buildRecommendationQueue(intent: PlaylistIntent, cachedTracks: List<CachedTrack>): List<TrackItem> = withContext(Dispatchers.Default) {
        val recentlyPlayed = try {
            musicDao.getRecentlyPlayed().firstOrNull() ?: emptyList()
        } catch (e: Exception) {
            emptyList()
        }

        val trackScores = mutableListOf<Pair<CachedTrack, Int>>()

        for (track in cachedTracks) {
            var score = 0
            val trackArtistLower = track.artist.lowercase(Locale.ROOT)
            val trackAlbumLower = track.album.lowercase(Locale.ROOT)
            val trackTitleLower = track.title.lowercase(Locale.ROOT)

            // Exclude rules (negative constraints have priority!)
            var excluded = false
            for (exArtist in intent.excludeArtists) {
                if (trackArtistLower.contains(exArtist.lowercase(Locale.ROOT))) {
                    excluded = true
                    break
                }
            }
            if (excluded) continue

            // 1. Seed Artists (+100)
            for (artist in intent.seedArtists) {
                if (trackArtistLower.contains(artist.lowercase(Locale.ROOT))) {
                    score += 100
                }
            }

            // 2. Seed Albums (+100)
            for (album in intent.seedAlbums) {
                if (trackAlbumLower.contains(album.lowercase(Locale.ROOT))) {
                    score += 100
                }
            }

            // 3. Genre matching (+40 per match)
            for (genre in intent.genres) {
                val genLower = genre.lowercase(Locale.ROOT)
                if (trackArtistLower.contains(genLower) || trackAlbumLower.contains(genLower) || trackTitleLower.contains(genLower)) {
                    score += 40
                }
            }

            // 4. Mood matching (+30 per match)
            for (mood in intent.moods) {
                val moodLower = mood.lowercase(Locale.ROOT)
                if (trackTitleLower.contains(moodLower) || trackAlbumLower.contains(moodLower) || trackArtistLower.contains(moodLower)) {
                    score += 30
                }
            }

            // 5. Energy alignment (+30)
            if (intent.energyLevel == "high") {
                val highEnergyKeywords = listOf("rock", "metal", "intense", "loud", "heavy", "workout", "action", "epic", "battle", "drum", "bass")
                if (highEnergyKeywords.any { trackTitleLower.contains(it) || trackAlbumLower.contains(it) }) {
                    score += 30
                }
            } else if (intent.energyLevel == "low") {
                val lowEnergyKeywords = listOf("relax", "chill", "calm", "sleep", "acoustic", "piano", "ambient", "quiet", "soft", "slow", "peace")
                if (lowEnergyKeywords.any { trackTitleLower.contains(it) || trackAlbumLower.contains(it) }) {
                    score += 30
                }
            }

            // 6. Familiarity vs. Deep Cuts
            val isFamiliar = recentlyPlayed.any { it.ratingKey == track.ratingKey }
            if (intent.preferFamiliar && isFamiliar) {
                score += 25
            } else if (intent.preferDeepCuts && !isFamiliar) {
                score += 30
            }

            // If prompt matched anything or we have no custom filters, add to candidates
            if (score > 0 || (intent.seedArtists.isEmpty() && intent.genres.isEmpty() && intent.moods.isEmpty())) {
                trackScores.add(track to score)
            }
        }

        // Sort candidates by score descending
        val sortedCandidates = trackScores.sortedByDescending { it.second }.map { it.first }

        // Remove duplicates & cap artist tracks (max 3 tracks per artist to keep the mix varied!)
        val artistCounters = mutableMapOf<String, Int>()
        val finalSelection = mutableListOf<CachedTrack>()

        for (track in sortedCandidates) {
            val count = artistCounters.getOrDefault(track.artist, 0)
            val isSeededArtist = intent.seedArtists.any { it.lowercase(Locale.ROOT) == track.artist.lowercase(Locale.ROOT) }
            
            // Allow more tracks if the user explicitly seeded this artist
            val maxLimit = if (isSeededArtist) 8 else 3

            if (count < maxLimit) {
                finalSelection.add(track)
                artistCounters[track.artist] = count + 1
            }

            if (finalSelection.size >= intent.targetTrackCount) {
                break
            }
        }

        // If selection is too small, fill it from the remaining cached library in stable order.
        if (finalSelection.size < intent.targetTrackCount && cachedTracks.isNotEmpty()) {
            val extraTracks = cachedTracks
                .asSequence()
                .filterNot { candidate -> finalSelection.contains(candidate) }
                .sortedWith(compareByDescending<CachedTrack> { it.playCount }
                    .thenByDescending { it.lastPlayedAt ?: 0L }
                    .thenBy { it.artist.lowercase(Locale.ROOT) }
                    .thenBy { it.album.lowercase(Locale.ROOT) }
                    .thenBy { it.ratingKey })
            for (track in extraTracks) {
                finalSelection.add(track)
                if (finalSelection.size >= intent.targetTrackCount) {
                    break
                }
            }
        }

        finalSelection.shuffled().map { track ->
            TrackItem(
                ratingKey = track.ratingKey,
                title = track.title,
                artist = track.artist,
                album = track.album,
                key = track.key,
                thumb = track.thumb,
                duration = track.duration
            )
        }
    }
}


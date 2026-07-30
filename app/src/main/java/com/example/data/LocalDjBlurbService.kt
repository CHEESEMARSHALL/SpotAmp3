package com.example.data

import com.example.playback.TrackItem

/** Deterministic, local-only Now Playing narration. */
class LocalDjBlurbService {
    fun describe(track: TrackItem, queuePosition: Int, queueSize: Int): String {
        val position = if (queueSize > 0) "Track ${queuePosition.coerceAtLeast(0) + 1} of $queueSize" else "Now playing"
        val genre = track.genres.firstOrNull()?.let { " / $it" } ?: ""
        return "$position / ${track.artist} from ${track.album}$genre"
    }
}

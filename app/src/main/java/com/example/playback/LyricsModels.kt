package com.example.playback

data class LyricLine(
    val timestampMs: Long?,
    val text: String
)

data class Lyrics(
    val lines: List<LyricLine>,
    val isSynced: Boolean
)

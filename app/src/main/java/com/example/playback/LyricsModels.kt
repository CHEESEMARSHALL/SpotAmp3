package com.example.playback

data class LyricLine(
    val timestampMs: Long?,
    val text: String
)

data class Lyrics(
    val lines: List<LyricLine>,
    val isSynced: Boolean
)

fun Lyrics.activeLineIndex(positionMs: Long): Int = if (isSynced) {
    lines.indexOfLast { line -> line.timestampMs != null && line.timestampMs <= positionMs }
} else {
    -1
}

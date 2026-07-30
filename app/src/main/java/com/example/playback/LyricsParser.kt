package com.example.playback

object LyricsParser {
    fun parse(content: String, isLrc: Boolean): Lyrics {
        val hasTimestamps = content.contains(Regex("""\[\d+:\d{2}"""))
        val isLrcFinal = isLrc || hasTimestamps

        if (!isLrcFinal) {
            // Plain txt lyrics
            val lines = content.lines()
                .map { it.trim() }
                .filter { it.isNotEmpty() }
                .map { LyricLine(null, it) }
            return Lyrics(lines, isSynced = false)
        }
        
        // LRC / Enhanced LRC parser
        val lyricLines = mutableListOf<LyricLine>()
        val timestampRegex = Regex("""\[(\d+):(\d{2})(?:\.(\d+))?\]""")
        
        content.lines().forEach { rawLine ->
            val line = rawLine.trim()
            if (line.isEmpty()) return@forEach
            
            // Find all matches of timestamps in this line
            val matches = timestampRegex.findAll(line).toList()
            if (matches.isEmpty()) {
                // It's a metadata line or plain text line in LRC.
                if (!line.startsWith("[") || !line.contains(":")) {
                    lyricLines.add(LyricLine(null, line))
                }
                return@forEach
            }
            
            // Extract the lyric text (which is after all the timestamps)
            val lastMatchEnd = matches.last().range.last + 1
            var text = if (lastMatchEnd < line.length) line.substring(lastMatchEnd).trim() else ""
            
            // Enhanced LRC cleanup: remove any <00:12.34> or <00:12:34> word-level tags from text
            val wordTagRegex = Regex("""<\d+:\d{2}(?:\.\d+)?>""")
            text = text.replace(wordTagRegex, "").trim()
            
            // Create a LyricLine for each timestamp found in the line
            for (match in matches) {
                val min = match.groupValues[1].toLongOrNull() ?: 0L
                val sec = match.groupValues[2].toLongOrNull() ?: 0L
                val msStr = match.groupValues.getOrNull(3) ?: ""
                val ms = when (msStr.length) {
                    1 -> (msStr.toLongOrNull() ?: 0L) * 100
                    2 -> (msStr.toLongOrNull() ?: 0L) * 10
                    3 -> msStr.toLongOrNull() ?: 0L
                    else -> 0L
                }
                val timestampMs = (min * 60 + sec) * 1000 + ms
                lyricLines.add(LyricLine(timestampMs, text))
            }
        }
        
        // Sort synced lyrics by timestamp
        val sortedLines = lyricLines.sortedWith { a, b ->
            val timeA = a.timestampMs ?: Long.MAX_VALUE
            val timeB = b.timestampMs ?: Long.MAX_VALUE
            timeA.compareTo(timeB)
        }
        
        val hasAnySynced = sortedLines.any { it.timestampMs != null }
        return Lyrics(sortedLines, isSynced = hasAnySynced)
    }
}

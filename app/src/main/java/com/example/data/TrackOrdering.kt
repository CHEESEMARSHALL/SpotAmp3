package com.example.data

internal fun positiveTrackPosition(value: Any?): Int? {
    val text = when (value) {
        is Number -> value.toInt().toString()
        is String -> value.trim().substringBefore('/')
        else -> return null
    }
    return text.toIntOrNull()?.takeIf { it > 0 }
}

fun BackendTrackDto.resolvedTrackNumber(): Int = sequenceOf(index, track, trackIndex, trackNumber)
    .mapNotNull(::positiveTrackPosition)
    .firstOrNull()
    ?: 0

fun BackendTrackDto.resolvedDiscNumber(): Int = sequenceOf(discNumber, disc)
    .mapNotNull(::positiveTrackPosition)
    .firstOrNull()
    ?: 1

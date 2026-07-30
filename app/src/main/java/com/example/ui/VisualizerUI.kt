package com.example.ui

import androidx.compose.animation.*
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.unit.dp
import coil.compose.AsyncImage

enum class PlaybackArtworkMode {
    ARTWORK,
    SPECTRUM,
    WAVEFORM,
    RADIAL
}

@Composable
fun SpectrumVisualizer(
    spectrum: FloatArray,
    modifier: Modifier = Modifier
) {
    var displayedBars by remember {
        mutableStateOf(FloatArray(spectrum.size))
    }

    LaunchedEffect(spectrum) {
        if (displayedBars.size != spectrum.size) {
            displayedBars = FloatArray(spectrum.size)
        }

        displayedBars = FloatArray(spectrum.size) { index ->
            val oldValue = displayedBars.getOrElse(index) { 0f }
            val newValue = spectrum[index]

            // Fast attack, slower release.
            if (newValue > oldValue) {
                oldValue + ((newValue - oldValue) * 0.75f)
            } else {
                oldValue + ((newValue - oldValue) * 0.18f)
            }
        }
    }

    val primaryColor = MaterialTheme.colorScheme.primary
    val tertiaryColor = MaterialTheme.colorScheme.tertiary

    Canvas(modifier = modifier) {
        if (displayedBars.isEmpty()) return@Canvas

        val gap = size.width * 0.006f
        val availableWidth =
            size.width - gap * (displayedBars.size - 1)

        val barWidth =
            (availableWidth / displayedBars.size).coerceAtLeast(1f)

        displayedBars.forEachIndexed { index, magnitude ->
            val minimumHeight = size.height * 0.015f
            val barHeight =
                (size.height * magnitude).coerceAtLeast(minimumHeight)

            val left = index * (barWidth + gap)
            val top = size.height - barHeight

            drawRoundRect(
                brush = Brush.verticalGradient(
                    listOf(primaryColor, tertiaryColor)
                ),
                topLeft = Offset(left, top),
                size = Size(barWidth, barHeight),
                cornerRadius = CornerRadius(
                    x = barWidth / 2f,
                    y = barWidth / 2f
                )
            )
        }
    }
}

// Minimal placeholder for Waveform/Radial until fully implemented.
@Composable
fun WaveformVisualizer(waveform: FloatArray, modifier: Modifier = Modifier) {
    Box(modifier = modifier.background(MaterialTheme.colorScheme.surface))
}

@Composable
fun RadialSpectrumVisualizer(spectrum: FloatArray, modifier: Modifier = Modifier) {
    Box(modifier = modifier.background(MaterialTheme.colorScheme.surface))
}

@Composable
fun PlaybackVisualSurface(
    artworkUrl: String?,
    spectrum: FloatArray,
    waveform: FloatArray,
    mode: PlaybackArtworkMode,
    onModeChange: (PlaybackArtworkMode) -> Unit,
    modifier: Modifier = Modifier,
    onClick: (() -> Unit)? = null
) {
    val nextMode = {
        val next = when (mode) {
            PlaybackArtworkMode.ARTWORK ->
                PlaybackArtworkMode.SPECTRUM

            PlaybackArtworkMode.SPECTRUM ->
                PlaybackArtworkMode.WAVEFORM

            PlaybackArtworkMode.WAVEFORM ->
                PlaybackArtworkMode.RADIAL

            PlaybackArtworkMode.RADIAL ->
                PlaybackArtworkMode.ARTWORK
        }

        onModeChange(next)
    }

    Box(
        modifier = modifier
            .clip(RoundedCornerShape(24.dp))
            .clickable(onClick = onClick ?: nextMode)
    ) {
        AnimatedContent(
            targetState = mode,
            label = "Playback visual mode"
        ) { currentMode ->
            when (currentMode) {
                PlaybackArtworkMode.ARTWORK -> {
                    AsyncImage(
                        model = artworkUrl,
                        contentDescription = "Album artwork",
                        contentScale = ContentScale.Crop,
                        modifier = Modifier.fillMaxSize()
                    )
                }

                PlaybackArtworkMode.SPECTRUM -> {
                    SpectrumVisualizer(
                        spectrum = spectrum,
                        modifier = Modifier
                            .fillMaxSize()
                            .background(MaterialTheme.colorScheme.surface)
                            .padding(20.dp)
                    )
                }

                PlaybackArtworkMode.WAVEFORM -> {
                    WaveformVisualizer(
                        waveform = waveform,
                        modifier = Modifier.fillMaxSize()
                    )
                }

                PlaybackArtworkMode.RADIAL -> {
                    RadialSpectrumVisualizer(
                        spectrum = spectrum,
                        modifier = Modifier.fillMaxSize()
                    )
                }
            }
        }
    }
}

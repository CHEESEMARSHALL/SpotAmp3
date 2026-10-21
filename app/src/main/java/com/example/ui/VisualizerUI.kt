package com.example.ui

import androidx.compose.animation.*
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.rounded.GraphicEq
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Album
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.role
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import coil.compose.AsyncImage
import com.example.R
import kotlin.math.cos

enum class PlaybackArtworkMode {
    ARTWORK,
    SPECTRUM,
    WAVEFORM,
    RADIAL
}

internal fun nextPlaybackArtworkMode(mode: PlaybackArtworkMode): PlaybackArtworkMode = when (mode) {
    PlaybackArtworkMode.ARTWORK -> PlaybackArtworkMode.SPECTRUM
    PlaybackArtworkMode.SPECTRUM -> PlaybackArtworkMode.WAVEFORM
    PlaybackArtworkMode.WAVEFORM -> PlaybackArtworkMode.RADIAL
    PlaybackArtworkMode.RADIAL -> PlaybackArtworkMode.ARTWORK
}

@androidx.annotation.StringRes
internal fun playbackArtworkModeLabel(mode: PlaybackArtworkMode): Int = when (mode) {
    PlaybackArtworkMode.ARTWORK -> R.string.player_visual_mode_artwork
    PlaybackArtworkMode.SPECTRUM -> R.string.player_visual_mode_spectrum
    PlaybackArtworkMode.WAVEFORM -> R.string.player_visual_mode_waveform
    PlaybackArtworkMode.RADIAL -> R.string.player_visual_mode_radial
}

internal fun isLowResolutionArtwork(width: Int, height: Int): Boolean =
    width in 1..511 || height in 1..511

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

    Canvas(modifier = modifier.testTag("player_visualizer_spectrum")) {
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

@Composable
fun WaveformVisualizer(waveform: FloatArray, modifier: Modifier = Modifier) {
    val primaryColor = MaterialTheme.colorScheme.primary
    val tertiaryColor = MaterialTheme.colorScheme.tertiary

    Canvas(
        modifier = modifier
            .background(MaterialTheme.colorScheme.surface)
            .testTag("player_visualizer_waveform")
    ) {
        if (waveform.isEmpty() || size.width <= 0f || size.height <= 0f) return@Canvas

        val centerY = size.height / 2f
        drawLine(
            color = Color.White.copy(alpha = 0.12f),
            start = Offset(0f, centerY),
            end = Offset(size.width, centerY),
            strokeWidth = 1.dp.toPx()
        )

        val amplitude = size.height * 0.38f
        val path = Path()
        waveform.forEachIndexed { index, sample ->
            val x = if (waveform.size == 1) size.width / 2f
            else index.toFloat() / (waveform.size - 1) * size.width
            val y = centerY - sample.coerceIn(-1f, 1f) * amplitude
            if (index == 0) path.moveTo(x, y) else path.lineTo(x, y)
        }
        drawPath(
            path = path,
            color = primaryColor.copy(alpha = 0.18f),
            style = Stroke(width = 12.dp.toPx(), cap = StrokeCap.Round)
        )
        drawPath(
            path = path,
            brush = Brush.horizontalGradient(listOf(primaryColor, tertiaryColor)),
            style = Stroke(width = 2.5.dp.toPx(), cap = StrokeCap.Round)
        )
    }
}

@Composable
fun RadialSpectrumVisualizer(spectrum: FloatArray, modifier: Modifier = Modifier) {
    val primaryColor = MaterialTheme.colorScheme.primary
    val tertiaryColor = MaterialTheme.colorScheme.tertiary

    Canvas(
        modifier = modifier
            .background(MaterialTheme.colorScheme.surface)
            .testTag("player_visualizer_radial")
    ) {
        if (size.minDimension <= 0f) return@Canvas

        val center = Offset(size.width / 2f, size.height / 2f)
        val baseRadius = size.minDimension * 0.2f
        val maximumBarLength = size.minDimension * 0.2f
        drawCircle(
            color = tertiaryColor.copy(alpha = 0.08f),
            radius = baseRadius + 16.dp.toPx(),
            center = center
        )
        drawCircle(
            color = Color.White.copy(alpha = 0.16f),
            radius = baseRadius,
            center = center,
            style = Stroke(width = 1.dp.toPx())
        )
        if (spectrum.isEmpty()) return@Canvas

        spectrum.forEachIndexed { index, rawMagnitude ->
            val angle = -Math.PI / 2.0 + 2.0 * Math.PI * index / spectrum.size
            val magnitude = rawMagnitude.coerceIn(0f, 1f)
            val innerRadius = baseRadius + 5.dp.toPx()
            val outerRadius = innerRadius + maximumBarLength * maxOf(0.04f, magnitude)
            val start = Offset(
                x = center.x + cos(angle).toFloat() * innerRadius,
                y = center.y + kotlin.math.sin(angle).toFloat() * innerRadius
            )
            val end = Offset(
                x = center.x + cos(angle).toFloat() * outerRadius,
                y = center.y + kotlin.math.sin(angle).toFloat() * outerRadius
            )
            drawLine(
                color = if (index % 2 == 0) primaryColor else tertiaryColor,
                start = start,
                end = end,
                strokeWidth = 3.dp.toPx(),
                cap = StrokeCap.Round
            )
        }
    }
}

@Composable
fun PlaybackVisualSurface(
    artworkUrl: String?,
    spectrum: FloatArray,
    waveform: FloatArray,
    mode: PlaybackArtworkMode,
    onModeChange: (PlaybackArtworkMode) -> Unit,
    modifier: Modifier = Modifier,
    onClick: (() -> Unit)? = null,
    onClickLabel: String? = null,
    token: String = ""
) {
    val context = LocalContext.current
    val artworkRequest = remember(artworkUrl, token) {
        artworkUrl?.let { authenticatedArtworkRequest(context, it, token) }
    }
    // A refreshed credential can make the same URL load successfully after an
    // earlier 401, so image-result state must follow the full request identity.
    var artworkFailed by remember(artworkUrl, token) { mutableStateOf(false) }
    var artworkLowResolution by remember(artworkUrl, token) { mutableStateOf(false) }
    val nextMode = { onModeChange(nextPlaybackArtworkMode(mode)) }
    val modeLabel = stringResource(playbackArtworkModeLabel(mode))
    val modeButtonDescription = stringResource(
        R.string.player_change_visual_mode_accessibility,
        modeLabel
    )

    Box(
        modifier = modifier
            .clip(RoundedCornerShape(24.dp))
            .clickable(
                onClickLabel = onClickLabel,
                role = if (onClick != null) Role.Button else null,
                onClick = onClick ?: nextMode
            )
    ) {
        AnimatedContent(
            targetState = mode,
            modifier = Modifier.fillMaxSize(),
            label = "Playback visual mode"
        ) { currentMode ->
            when (currentMode) {
                PlaybackArtworkMode.ARTWORK -> {
                    Box(
                        modifier = Modifier.fillMaxSize().background(
                            Brush.linearGradient(
                                listOf(
                                    MaterialTheme.colorScheme.primary.copy(alpha = 0.22f),
                                    MaterialTheme.colorScheme.surface,
                                    Color(0xFF101018)
                                )
                            )
                        ),
                        contentAlignment = Alignment.Center
                    ) {
                        if (artworkRequest != null && !artworkFailed) {
                            AsyncImage(
                                model = artworkRequest,
                                contentDescription = stringResource(R.string.player_album_artwork),
                                contentScale = ContentScale.Fit,
                                modifier = Modifier.fillMaxSize(if (artworkLowResolution) 0.72f else 1f),
                                onSuccess = { state ->
                                    val drawable = state.result.drawable
                                    artworkLowResolution = isLowResolutionArtwork(
                                        drawable.intrinsicWidth,
                                        drawable.intrinsicHeight
                                    )
                                },
                                onError = { artworkFailed = true }
                            )
                        } else {
                            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                                Icon(
                                    imageVector = Icons.Rounded.Album,
                                    contentDescription = null,
                                    tint = Color.White.copy(alpha = 0.55f),
                                    modifier = Modifier.size(72.dp)
                                )
                                Text(
                                    stringResource(R.string.player_artwork_unavailable),
                                    color = Color.White.copy(alpha = 0.58f),
                                    style = MaterialTheme.typography.labelMedium
                                )
                            }
                        }
                    }
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

        Surface(
            onClick = nextMode,
            modifier = Modifier
                .align(Alignment.BottomEnd)
                .padding(12.dp)
                .testTag("player_visual_mode_button")
                .semantics {
                    contentDescription = modeButtonDescription
                    role = Role.Button
                },
            shape = RoundedCornerShape(50),
            color = Color.Black.copy(alpha = 0.68f),
            contentColor = Color.White
        ) {
            Row(
                modifier = Modifier.padding(horizontal = 12.dp, vertical = 8.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(6.dp)
            ) {
                Icon(
                    imageVector = Icons.Rounded.GraphicEq,
                    contentDescription = null,
                    modifier = Modifier.size(16.dp)
                )
                Text(
                    text = modeLabel,
                    style = MaterialTheme.typography.labelMedium
                )
            }
        }
    }
}

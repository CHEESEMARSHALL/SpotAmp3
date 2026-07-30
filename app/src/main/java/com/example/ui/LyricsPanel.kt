package com.example.ui

import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.MusicNote
import androidx.compose.material.icons.rounded.Refresh
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.playback.Lyrics
import com.example.playback.LyricLine

@Composable
fun LyricsPanel(
    lyrics: Lyrics?,
    loading: Boolean,
    positionMs: Long,
    onSeek: (Long) -> Unit,
    onClose: () -> Unit,
    onReload: () -> Unit,
    modifier: Modifier = Modifier
) {
    val activeThemeColor = MaterialTheme.colorScheme.primary
    val backgroundColor = MaterialTheme.colorScheme.surface.copy(alpha = 0.95f)
    val onSurfaceColor = MaterialTheme.colorScheme.onSurface

    Box(
        modifier = modifier
            .background(backgroundColor)
            .clickable { onClose() }
            .testTag("lyrics_panel")
    ) {
        if (loading) {
            Box(
                modifier = Modifier.fillMaxSize(),
                contentAlignment = Alignment.Center
            ) {
                CircularProgressIndicator(
                    color = activeThemeColor,
                    strokeWidth = 3.dp
                )
            }
        } else if (lyrics == null || lyrics.lines.isEmpty()) {
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .clickable(enabled = false) {} // Prevent click-to-close inside content area
                    .padding(24.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.Center
            ) {
                Icon(
                    imageVector = Icons.Rounded.MusicNote,
                    contentDescription = "No Lyrics",
                    tint = onSurfaceColor.copy(alpha = 0.3f),
                    modifier = Modifier.size(48.dp)
                )
                Spacer(modifier = Modifier.height(16.dp))
                Text(
                    text = "No Lyrics Available.",
                    style = MaterialTheme.typography.bodyLarge.copy(
                        color = onSurfaceColor.copy(alpha = 0.6f),
                        fontWeight = FontWeight.Medium
                    ),
                    textAlign = TextAlign.Center
                )
                Spacer(modifier = Modifier.height(24.dp))
                OutlinedButton(
                    onClick = onReload,
                    colors = ButtonDefaults.outlinedButtonColors(
                        contentColor = activeThemeColor
                    ),
                    border = BorderStroke(1.dp, activeThemeColor.copy(alpha = 0.4f)),
                    shape = RoundedCornerShape(12.dp)
                ) {
                    Icon(
                        imageVector = Icons.Rounded.Refresh,
                        contentDescription = "Reload",
                        modifier = Modifier.size(18.dp)
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(
                        text = "Scan / Reload",
                        style = MaterialTheme.typography.labelLarge.copy(fontWeight = FontWeight.Bold)
                    )
                }
            }
        } else {
            val listState = rememberLazyListState()
            
            // For synced lyrics: find the active line
            val activeIndex = if (lyrics.isSynced) {
                lyrics.lines.indexOfLast {
                    it.timestampMs != null && it.timestampMs <= positionMs
                }
            } else {
                -1
            }

            // Auto scroll to active index when it changes
            if (lyrics.isSynced && activeIndex >= 0) {
                LaunchedEffect(activeIndex) {
                    listState.animateScrollToItem(activeIndex)
                }
            }

            Box(modifier = Modifier.fillMaxSize()) {
                LazyColumn(
                    state = listState,
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(horizontal = 16.dp),
                    contentPadding = PaddingValues(vertical = 40.dp),
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    itemsIndexed(
                        items = lyrics.lines,
                        key = { index, line -> "${line.timestampMs ?: index}_$index" }
                    ) { index, line ->
                        val isActive = index == activeIndex
                        
                        val fontSize by animateFloatAsState(
                            targetValue = if (isActive) 20f else 16f,
                            label = "lyric_font_size"
                        )
                        
                        val textColor by animateColorAsState(
                            targetValue = if (isActive) activeThemeColor else onSurfaceColor.copy(alpha = 0.5f),
                            label = "lyric_text_color"
                        )

                        val fontWeight = if (isActive) FontWeight.Bold else FontWeight.Normal

                        val itemModifier = Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(8.dp))
                            .then(
                                if (lyrics.isSynced && line.timestampMs != null) {
                                    Modifier.clickable { onSeek(line.timestampMs) }
                                } else {
                                    Modifier
                                }
                            )
                            .padding(vertical = 12.dp, horizontal = 16.dp)

                        Text(
                            text = line.text,
                            style = MaterialTheme.typography.bodyLarge.copy(
                                fontSize = fontSize.sp,
                                color = textColor,
                                fontWeight = fontWeight,
                                textAlign = TextAlign.Center,
                                lineHeight = (fontSize * 1.4f).sp
                            ),
                            modifier = itemModifier,
                            textAlign = TextAlign.Center
                        )
                    }
                }

                // Add a subtle top and bottom fade effect matching the custom surface color
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(30.dp)
                        .background(
                            Brush.verticalGradient(
                                colors = listOf(MaterialTheme.colorScheme.surface, Color.Transparent)
                            )
                        )
                        .align(Alignment.TopCenter)
                )
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(30.dp)
                        .background(
                            Brush.verticalGradient(
                                colors = listOf(Color.Transparent, MaterialTheme.colorScheme.surface)
                            )
                        )
                        .align(Alignment.BottomCenter)
                )
            }
        }
    }
}

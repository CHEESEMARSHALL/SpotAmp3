package com.example.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.AssistChip
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Slider
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import com.example.R
import com.example.data.DiscoveryTuning

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun DiscoveryTuningControls(
    tuning: DiscoveryTuning,
    onTuningChange: (DiscoveryTuning) -> Unit,
    modifier: Modifier = Modifier
) {
    var artistInput by remember { mutableStateOf("") }
    var genreInput by remember { mutableStateOf("") }
    val normalized = tuning.normalized()
    val familiarityLabel = stringResource(R.string.discover_familiarity_label)
    val depthLabel = stringResource(R.string.discover_depth_label)

    Card(
        modifier = modifier.fillMaxWidth().testTag("discover_tuning_controls"),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.44f))
    ) {
        Column(
            modifier = Modifier.fillMaxWidth().padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            Text(stringResource(R.string.discover_tuning_title), style = MaterialTheme.typography.titleMedium)
            Text(
                stringResource(R.string.discover_tuning_body),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )

            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                Text(stringResource(R.string.discover_familiarity_label))
                Text(discoveryFamiliarityLabel(normalized.familiarity), color = MaterialTheme.colorScheme.primary)
            }
            Slider(
                value = normalized.familiarity.toFloat(),
                onValueChange = { onTuningChange(normalized.copy(familiarity = it.toInt())) },
                valueRange = 0f..100f,
                modifier = Modifier
                    .fillMaxWidth()
                    .semantics { contentDescription = "$familiarityLabel ${normalized.familiarity}" }
                    .testTag("discover_familiarity_slider")
            )

            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                Text(stringResource(R.string.discover_depth_label))
                Text(stringResource(R.string.discover_depth_value, normalized.depth), color = MaterialTheme.colorScheme.primary)
            }
            Slider(
                value = normalized.depth.toFloat(),
                onValueChange = { onTuningChange(normalized.copy(depth = it.toInt())) },
                valueRange = 0f..100f,
                modifier = Modifier
                    .fillMaxWidth()
                    .semantics { contentDescription = "$depthLabel ${normalized.depth}" }
                    .testTag("discover_depth_slider")
            )

            Text(stringResource(R.string.discover_exclusions_title), style = MaterialTheme.typography.titleSmall)
            OutlinedTextField(
                value = artistInput,
                onValueChange = { artistInput = it },
                label = { Text(stringResource(R.string.discover_exclude_artist_label)) },
                singleLine = true,
                modifier = Modifier.fillMaxWidth().testTag("discover_exclude_artist_input")
            )
            Button(
                onClick = {
                    val value = artistInput.trim()
                    if (value.isNotEmpty()) {
                        onTuningChange(normalized.copy(excludedArtists = normalized.excludedArtists + value).normalized())
                        artistInput = ""
                    }
                },
                enabled = artistInput.isNotBlank(),
                modifier = Modifier.testTag("discover_add_excluded_artist")
            ) { Text(stringResource(R.string.discover_add_exclusion)) }
            if (normalized.excludedArtists.isNotEmpty()) {
                Text(stringResource(R.string.discover_excluded_artists_heading), style = MaterialTheme.typography.labelMedium)
                FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                    normalized.excludedArtists.forEach { artist ->
                        AssistChip(
                            onClick = {
                                onTuningChange(normalized.copy(excludedArtists = normalized.excludedArtists - artist))
                            },
                            label = { Text(stringResource(R.string.discover_remove_exclusion, artist)) },
                            modifier = Modifier.testTag("discover_excluded_artist_${artist.hashCode()}")
                        )
                    }
                }
            }

            OutlinedTextField(
                value = genreInput,
                onValueChange = { genreInput = it },
                label = { Text(stringResource(R.string.discover_exclude_genre_label)) },
                singleLine = true,
                modifier = Modifier.fillMaxWidth().testTag("discover_exclude_genre_input")
            )
            Button(
                onClick = {
                    val value = genreInput.trim()
                    if (value.isNotEmpty()) {
                        onTuningChange(normalized.copy(excludedGenres = normalized.excludedGenres + value).normalized())
                        genreInput = ""
                    }
                },
                enabled = genreInput.isNotBlank(),
                modifier = Modifier.testTag("discover_add_excluded_genre")
            ) { Text(stringResource(R.string.discover_add_exclusion)) }
            if (normalized.excludedGenres.isNotEmpty()) {
                Text(stringResource(R.string.discover_excluded_genres_heading), style = MaterialTheme.typography.labelMedium)
                FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                    normalized.excludedGenres.forEach { genre ->
                        AssistChip(
                            onClick = {
                                onTuningChange(normalized.copy(excludedGenres = normalized.excludedGenres - genre))
                            },
                            label = { Text(stringResource(R.string.discover_remove_exclusion, genre)) },
                            modifier = Modifier.testTag("discover_excluded_genre_${genre.hashCode()}")
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun discoveryFamiliarityLabel(value: Int): String = stringResource(
    when (value.coerceIn(0, 100)) {
        in 0..33 -> R.string.discover_familiarity_deep
        in 67..100 -> R.string.discover_familiarity_familiar
        else -> R.string.discover_familiarity_balanced
    }
)

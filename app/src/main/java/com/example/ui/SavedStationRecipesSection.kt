package com.example.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.MoreVert
import androidx.compose.material.icons.rounded.PlayArrow
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.example.R
import com.example.data.SavedStationRecipe

@Composable
fun SavedStationRecipesSection(
    recipes: List<SavedStationRecipe>,
    onPlay: (SavedStationRecipe) -> Unit,
    onRename: (String, String) -> Unit,
    onDelete: (String) -> Unit,
    modifier: Modifier = Modifier
) {
    var renameTarget by remember { mutableStateOf<SavedStationRecipe?>(null) }
    var deleteTarget by remember { mutableStateOf<SavedStationRecipe?>(null) }

    Column(
        modifier = modifier.fillMaxWidth().testTag("saved_station_recipes_section"),
        verticalArrangement = Arrangement.spacedBy(10.dp)
    ) {
        Text(
            text = stringResource(R.string.discover_saved_stations_title),
            style = MaterialTheme.typography.titleLarge.copy(fontWeight = FontWeight.Bold)
        )
        if (recipes.isEmpty()) {
            Text(
                text = stringResource(R.string.discover_saved_stations_empty),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.testTag("saved_station_recipes_empty")
            )
        } else {
            recipes.forEach { recipe ->
                SavedStationRecipeRow(
                    recipe = recipe,
                    onPlay = { onPlay(recipe) },
                    onRename = { renameTarget = recipe },
                    onDelete = { deleteTarget = recipe }
                )
            }
        }
    }

    renameTarget?.let { recipe ->
        var name by remember(recipe.id) { mutableStateOf(recipe.name) }
        AlertDialog(
            modifier = Modifier.testTag("saved_station_rename_dialog"),
            onDismissRequest = { renameTarget = null },
            title = { Text(stringResource(R.string.discover_rename_station_title)) },
            text = {
                OutlinedTextField(
                    value = name,
                    onValueChange = { name = it },
                    singleLine = true,
                    label = { Text(stringResource(R.string.discover_station_name_label)) },
                    modifier = Modifier.fillMaxWidth().testTag("saved_station_name_input")
                )
            },
            confirmButton = {
                TextButton(
                    onClick = {
                        onRename(recipe.id, name.trim())
                        renameTarget = null
                    },
                    enabled = name.isNotBlank(),
                    modifier = Modifier.testTag("saved_station_rename_confirm")
                ) { Text(stringResource(R.string.discover_station_save_name)) }
            },
            dismissButton = {
                TextButton(onClick = { renameTarget = null }) {
                    Text(stringResource(R.string.station_cancel_action))
                }
            }
        )
    }

    deleteTarget?.let { recipe ->
        AlertDialog(
            modifier = Modifier.testTag("saved_station_delete_dialog"),
            onDismissRequest = { deleteTarget = null },
            title = { Text(stringResource(R.string.discover_delete_station_title)) },
            text = { Text(stringResource(R.string.discover_delete_station_message, recipe.name)) },
            confirmButton = {
                TextButton(
                    onClick = {
                        onDelete(recipe.id)
                        deleteTarget = null
                    },
                    modifier = Modifier.testTag("saved_station_delete_confirm")
                ) { Text(stringResource(R.string.discover_delete_station_confirm)) }
            },
            dismissButton = {
                TextButton(onClick = { deleteTarget = null }) {
                    Text(stringResource(R.string.station_cancel_action))
                }
            }
        )
    }
}

@Composable
private fun SavedStationRecipeRow(
    recipe: SavedStationRecipe,
    onPlay: () -> Unit,
    onRename: () -> Unit,
    onDelete: () -> Unit
) {
    var menuExpanded by remember(recipe.id) { mutableStateOf(false) }
    Card(
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.46f)),
        shape = RoundedCornerShape(16.dp),
        modifier = Modifier.fillMaxWidth().testTag("saved_station_${recipe.id}")
    ) {
        Row(
            modifier = Modifier.fillMaxWidth().padding(start = 16.dp, end = 8.dp, top = 10.dp, bottom = 10.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = recipe.name,
                    style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.SemiBold),
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                Text(
                    text = savedStationRecipeSummary(recipe),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis
                )
                Text(
                    text = stringResource(R.string.discover_saved_on_this_phone),
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.primary
                )
            }
            Spacer(Modifier.width(4.dp))
            IconButton(
                onClick = onPlay,
                modifier = Modifier.size(48.dp).testTag("saved_station_restart_${recipe.id}")
            ) {
                Icon(
                    imageVector = Icons.Rounded.PlayArrow,
                    contentDescription = stringResource(R.string.discover_restart_station, recipe.name),
                    tint = MaterialTheme.colorScheme.primary
                )
            }
            androidx.compose.foundation.layout.Box {
                IconButton(
                    onClick = { menuExpanded = true },
                    modifier = Modifier.size(48.dp).testTag("saved_station_options_${recipe.id}")
                ) {
                    Icon(
                        imageVector = Icons.Rounded.MoreVert,
                        contentDescription = stringResource(R.string.discover_station_options, recipe.name)
                    )
                }
                DropdownMenu(expanded = menuExpanded, onDismissRequest = { menuExpanded = false }) {
                    DropdownMenuItem(
                        text = { Text(stringResource(R.string.discover_rename_station_action)) },
                        onClick = { menuExpanded = false; onRename() }
                    )
                    DropdownMenuItem(
                        text = { Text(stringResource(R.string.discover_delete_station_action)) },
                        onClick = { menuExpanded = false; onDelete() }
                    )
                }
            }
        }
    }
}

internal fun savedStationRecipeSummary(recipe: SavedStationRecipe): String {
    val seed = recipe.seedValues.joinToString(", ").takeIf(String::isNotBlank)
    val type = recipe.radioType.lowercase().replace('_', ' ')
    return when {
        seed != null -> "$type • $seed • ${recipe.trackCount} tracks"
        recipe.decadeStart != null -> "$type • ${recipe.decadeStart}s • ${recipe.trackCount} tracks"
        else -> "$type • ${recipe.trackCount} tracks"
    }
}

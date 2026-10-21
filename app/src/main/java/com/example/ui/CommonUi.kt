package com.example.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.QueueMusic
import androidx.compose.material.icons.rounded.Add
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.example.R
import com.example.data.PlaylistEntity

@Composable
internal fun localizedArtistPresentation(value: String?): String {
    val normalized = value?.trim()
    return when {
        normalized.isNullOrEmpty() || normalized.equals("Unknown Artist", ignoreCase = true) ->
            stringResource(R.string.common_unknown_artist)
        normalized.equals("Various Artists", ignoreCase = true) ->
            stringResource(R.string.common_various_artists)
        else -> normalized
    }
}

@Composable
internal fun localizedAlbumPresentation(value: String?): String {
    val normalized = value?.trim()
    return if (normalized.isNullOrEmpty() || normalized.equals("Unknown Album", ignoreCase = true)) {
        stringResource(R.string.common_unknown_album)
    } else {
        normalized
    }
}

@Composable
internal fun localizedArtistAlbumSubtitle(artist: String?, album: String?): String =
    stringResource(
        R.string.common_artist_album_subtitle,
        localizedArtistPresentation(artist),
        localizedAlbumPresentation(album)
    )

@Composable
internal fun localizedPlayCountLabel(count: Int): String =
    pluralStringResource(R.plurals.listener_play_count, count, count)

@Composable
fun FeedbackIconButton(
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    content: @Composable () -> Unit
) {
    val interactionSource = remember { MutableInteractionSource() }
    val isPressed by interactionSource.collectIsPressedAsState()
    IconButton(
        onClick = onClick,
        modifier = Modifier
            .sizeIn(minWidth = 48.dp, minHeight = 48.dp)
            .then(modifier)
            .background(
                color = if (isPressed) Color.White.copy(alpha = 0.1f) else Color.Transparent,
                shape = CircleShape
            ),
        interactionSource = interactionSource,
        enabled = enabled
    ) {
        content()
    }
}

@Composable
fun PlaylistSelectionDialog(
    playlists: List<PlaylistEntity>,
    onDismiss: () -> Unit,
    onPlaylistSelected: (Int) -> Unit,
    onCreateNewPlaylist: (String, () -> Unit, () -> Unit) -> Unit
) {
    var showCreateDialog by remember { mutableStateOf(false) }
    var newPlaylistName by remember { mutableStateOf("") }
    var isCreatingPlaylist by remember { mutableStateOf(false) }
    var createPlaylistError by remember { mutableStateOf(false) }

    if (showCreateDialog) {
        LibraryCreatePlaylistDialog(
            name = newPlaylistName,
            isCreating = isCreatingPlaylist,
            showError = createPlaylistError,
            onNameChange = {
                newPlaylistName = it
                createPlaylistError = false
            },
            onDismiss = {
                if (isCreatingPlaylist) return@LibraryCreatePlaylistDialog
                showCreateDialog = false
                newPlaylistName = ""
                createPlaylistError = false
            },
            onCreate = { name ->
                isCreatingPlaylist = true
                createPlaylistError = false
                onCreateNewPlaylist(
                    name,
                    {
                        isCreatingPlaylist = false
                        createPlaylistError = true
                    },
                    {
                        isCreatingPlaylist = false
                        showCreateDialog = false
                        newPlaylistName = ""
                        createPlaylistError = false
                        onDismiss()
                    }
                )
            }
        )
    } else {
        AlertDialog(
            onDismissRequest = onDismiss,
            title = { Text(stringResource(R.string.library_add_to_playlist)) },
            text = {
                Column {
                    ListItem(
                        headlineContent = { Text(stringResource(R.string.library_create_new_playlist)) },
                        leadingContent = { Icon(Icons.Rounded.Add, contentDescription = null) },
                        modifier = Modifier
                            .clickable(role = Role.Button) {
                                newPlaylistName = ""
                                createPlaylistError = false
                                showCreateDialog = true
                            }
                            .testTag("playlist_selection_create_new")
                    )
                    HorizontalDivider(color = Color.White.copy(alpha = 0.1f))
                    if (playlists.isEmpty()) {
                        Text(
                            text = stringResource(R.string.library_context_empty_playlists),
                            style = MaterialTheme.typography.bodyMedium,
                            modifier = Modifier.padding(horizontal = 16.dp, vertical = 20.dp)
                        )
                    } else {
                        LazyColumn(modifier = Modifier.heightIn(max = 300.dp)) {
                            itemsIndexed(playlists) { _, playlist ->
                                ListItem(
                                    headlineContent = { Text(playlist.name) },
                                    leadingContent = {
                                        Icon(Icons.AutoMirrored.Rounded.QueueMusic, contentDescription = null)
                                    },
                                    modifier = Modifier.clickable(role = Role.Button) {
                                        onPlaylistSelected(playlist.id)
                                        onDismiss()
                                    }
                                )
                            }
                        }
                    }
                }
            },
            confirmButton = {},
            dismissButton = {
                TextButton(onClick = onDismiss) {
                    Text(stringResource(R.string.library_cancel_playlist_creation))
                }
            }
        )
    }
}

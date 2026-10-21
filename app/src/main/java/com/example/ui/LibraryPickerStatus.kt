package com.example.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.error
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import com.example.R

internal enum class LibraryPickerContent { ARTISTS, ALBUMS, STATION_OPTIONS }

internal enum class LibraryPickerStatus { LOADING, LOAD_FAILED, LIBRARY_EMPTY, NO_FILTER_RESULTS }

@Composable
internal fun LibraryPickerStatusView(
    status: LibraryPickerStatus,
    content: LibraryPickerContent,
    onRetry: () -> Unit,
    messageOverride: Int? = null,
    modifier: Modifier = Modifier
) {
    val messageId = when (content) {
        LibraryPickerContent.ARTISTS -> when (status) {
            LibraryPickerStatus.LOADING -> R.string.library_picker_loading_artists
            LibraryPickerStatus.LOAD_FAILED -> R.string.library_picker_failed_artists
            LibraryPickerStatus.LIBRARY_EMPTY -> R.string.library_picker_empty_artists
            LibraryPickerStatus.NO_FILTER_RESULTS -> R.string.library_picker_no_matching_artists
        }
        LibraryPickerContent.ALBUMS -> when (status) {
            LibraryPickerStatus.LOADING -> R.string.library_picker_loading_albums
            LibraryPickerStatus.LOAD_FAILED -> R.string.library_picker_failed_albums
            LibraryPickerStatus.LIBRARY_EMPTY -> R.string.library_picker_empty_albums
            LibraryPickerStatus.NO_FILTER_RESULTS -> R.string.library_picker_no_matching_albums
        }
        LibraryPickerContent.STATION_OPTIONS -> when (status) {
            LibraryPickerStatus.LOADING -> R.string.library_picker_loading_options
            LibraryPickerStatus.LOAD_FAILED -> R.string.library_picker_failed_options
            LibraryPickerStatus.LIBRARY_EMPTY -> R.string.library_picker_empty_options
            LibraryPickerStatus.NO_FILTER_RESULTS -> R.string.library_picker_no_matching_options
        }
    }
    val message = stringResource(messageOverride ?: messageId)

    Column(
        modifier = modifier.padding(24.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        if (status == LibraryPickerStatus.LOADING) {
            CircularProgressIndicator()
            Spacer(Modifier.height(16.dp))
        }
        Text(
            text = message,
            color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.72f),
            style = MaterialTheme.typography.bodyMedium,
            modifier = Modifier.semantics {
                if (status == LibraryPickerStatus.LOAD_FAILED) {
                    liveRegion = LiveRegionMode.Polite
                    error(message)
                }
            }
        )
        if (status == LibraryPickerStatus.LOAD_FAILED) {
            Spacer(Modifier.height(8.dp))
            TextButton(onClick = onRetry) { Text(stringResource(R.string.library_picker_retry)) }
        }
    }
}

package com.example.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.example.R
import com.example.data.LibrarySyncState
import com.example.playback.TrackItem
import kotlinx.coroutines.launch

/**
 * Debug/QA-only surface. This file is included only in debug and QA variants.
 * It deliberately exposes bounded fixtures and observability, not production
 * credentials or destructive production controls.
 */
@Composable
fun QaLabSettingsAction(onClick: () -> Unit) {
    OutlinedButton(
        onClick = onClick,
        modifier = Modifier.fillMaxWidth().padding(bottom = 16.dp).testTag("open-qa-lab")
    ) {
        Text(stringResource(R.string.settings_open_qa_lab))
    }
}

@Composable
@OptIn(ExperimentalLayoutApi::class)
fun QaLabScreen(
    viewModel: MusicViewModel,
    modifier: Modifier = Modifier
) {
    val queueSize = viewModel.playbackManager.queue.collectAsStateWithLifecycle().value.size
    val downloadedCount = viewModel.downloadedTracks.collectAsStateWithLifecycle().value.size
    val syncState = viewModel.syncState.collectAsStateWithLifecycle().value
    var actionResult by remember { mutableStateOf("No QA action run") }
    val scope = rememberCoroutineScope()

    fun fixture(size: Int, unicode: Boolean = false, duplicates: Boolean = false): List<TrackItem> =
        List(size) { index ->
            val id = if (duplicates) "duplicate-${index % 10}" else "qa-$index"
            TrackItem(
                ratingKey = id,
                title = if (unicode) "QA \u65e5\u672c\u8a9e \u266b $index" else "QA Track $index",
                artist = "QA Artist",
                album = "QA Fixture",
                key = "/qa/$id",
                thumb = "",
                duration = 180_000L,
                index = index
            )
        }

    fun loadFixture(size: Int, unicode: Boolean = false, duplicates: Boolean = false) {
        viewModel.playbackManager.replaceQueueForQa(fixture(size, unicode, duplicates))
        actionResult = "Loaded $size track fixture"
    }

    Surface(
        modifier = modifier.fillMaxSize(),
        color = MaterialTheme.colorScheme.background,
        contentColor = MaterialTheme.colorScheme.onBackground
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .verticalScroll(rememberScrollState())
                .padding(20.dp)
                .testTag("qa-lab"),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Text("QA Lab", style = MaterialTheme.typography.headlineSmall)
            Text("Debug and QA builds only. No production credentials or endpoints are shown.")
            Card(modifier = Modifier.testTag("qa-lab-state")) {
                Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    Text("Runtime state", style = MaterialTheme.typography.titleMedium)
                    Text("Queue items: $queueSize")
                    Text("Downloaded tracks: $downloadedCount")
                    Text("Sync state: ${qaSyncStateLabel(syncState)}")
                }
            }
            Text("Fixtures", style = MaterialTheme.typography.titleMedium)
            FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Button(onClick = { loadFixture(100) }, modifier = Modifier.testTag("qa-fixture-100")) { Text("100") }
                Button(onClick = { loadFixture(1_000) }, modifier = Modifier.testTag("qa-fixture-1k")) { Text("1K") }
                Button(onClick = { loadFixture(10_000) }, modifier = Modifier.testTag("qa-fixture-10k")) { Text("10K") }
                Button(onClick = { loadFixture(50_000) }, modifier = Modifier.testTag("qa-fixture-50k")) { Text("50K") }
                Button(onClick = { loadFixture(100_000) }, modifier = Modifier.testTag("qa-fixture-100k")) { Text("100K") }
            }
            FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Button(onClick = { loadFixture(100, unicode = true) }, modifier = Modifier.testTag("qa-fixture-unicode")) { Text("Unicode") }
                Button(onClick = { loadFixture(100, duplicates = true) }, modifier = Modifier.testTag("qa-fixture-duplicates")) { Text("Duplicates") }
                Button(onClick = { viewModel.playbackManager.clearQueue(); actionResult = "QA queue reset" }, modifier = Modifier.testTag("qa-reset")) { Text("Reset") }
            }
            Button(
                onClick = {
                    scope.launch {
                        repeat(100) { viewModel.playbackManager.toggleShuffleForQa() }
                        repeat(100) { viewModel.playbackManager.toggleRepeat() }
                        actionResult = "Playback smoke complete: 200 deterministic toggles"
                    }
                },
                modifier = Modifier.testTag("qa-playback-smoke")
            ) { Text("Playback smoke x100") }
            Text("Playback actions", style = MaterialTheme.typography.titleMedium)
            FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Button(onClick = { repeat(100) { viewModel.playbackManager.next() }; actionResult = "Next x100 complete" }, modifier = Modifier.testTag("qa-next-100")) { Text("Next x100") }
                Button(onClick = { repeat(100) { viewModel.playbackManager.prev() }; actionResult = "Previous x100 complete" }, modifier = Modifier.testTag("qa-prev-100")) { Text("Previous x100") }
                Button(onClick = { repeat(100) { viewModel.playbackManager.toggleShuffle() }; actionResult = "Shuffle x100 complete" }, modifier = Modifier.testTag("qa-shuffle-100")) { Text("Shuffle x100") }
            }
            FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Button(onClick = { repeat(100) { viewModel.playbackManager.toggleRepeat() }; actionResult = "Repeat x100 complete" }, modifier = Modifier.testTag("qa-repeat-100")) { Text("Repeat x100") }
                Button(onClick = { repeat(100) { viewModel.playbackManager.seekTo((it * 1_000L) % 180_000L) }; actionResult = "Seek x100 complete" }, modifier = Modifier.testTag("qa-seek-100")) { Text("Seek x100") }
                Button(onClick = { viewModel.playbackManager.addToQueue(fixture(1).first()); viewModel.playbackManager.removeFromQueue(viewModel.playbackManager.queue.value.lastIndex); actionResult = "Queue mutate x100-ready complete" }, modifier = Modifier.testTag("qa-queue-mutate")) { Text("Queue mutate") }
            }
            Text(actionResult, modifier = Modifier.testTag("qa-action-result"))
            Text("Device-only journeys remain under the UI tier and are never treated as passed when no device is connected.")
        }
    }
}

private fun qaSyncStateLabel(state: LibrarySyncState): String = when (state) {
    LibrarySyncState.Idle -> "Idle"
    LibrarySyncState.Connecting -> "Connecting"
    is LibrarySyncState.Fetching -> "Loading page ${state.page} (${state.receivedCount} tracks)"
    is LibrarySyncState.Processing -> "Processing ${state.totalReceived} tracks"
    is LibrarySyncState.Saving -> "Saving ${state.validCount} tracks"
    is LibrarySyncState.Complete -> "Ready: ${state.indexedCount} tracks (${state.skippedCount} skipped)"
    is LibrarySyncState.Failed -> "Failed: ${state.userMessage}"
}

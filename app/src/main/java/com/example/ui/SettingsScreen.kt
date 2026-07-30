package com.example.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.example.data.LibraryCleanupSuggestion
import com.example.data.LibraryCleanupSuggestionService
import com.example.data.LibrarySyncState

@Composable
fun SettingsScreen(
    viewModel: MusicViewModel,
    modifier: Modifier = Modifier
) {
    val isConfigured by viewModel.isConfigured.collectAsStateWithLifecycle()
    val libraries by viewModel.libraries.collectAsStateWithLifecycle()
    val selectedSectionId by viewModel.selectedSectionId.collectAsStateWithLifecycle()
    val selectedLibraryName by viewModel.selectedLibraryName.collectAsStateWithLifecycle()
    val cachedCount by viewModel.cachedCount.collectAsStateWithLifecycle()
    val downloadedTracks by viewModel.downloadedTracks.collectAsStateWithLifecycle()
    val queue by viewModel.playbackManager.queue.collectAsStateWithLifecycle()
    val activeTheme by viewModel.activeThemeFlow.collectAsStateWithLifecycle()
    
    val isLoading by viewModel.isLoading.collectAsStateWithLifecycle()
    val syncState by viewModel.syncState.collectAsStateWithLifecycle()
    val errorMessage by viewModel.errorMessage.collectAsStateWithLifecycle()

    var baseUrlInput by remember { mutableStateOf(viewModel.repository.settings.baseUrl) }
    var tokenInput by remember { mutableStateOf(viewModel.repository.settings.token) }
    var lyricsDirInput by remember { mutableStateOf(viewModel.repository.settings.lyricsDirectory) }
    var hideToken by remember { mutableStateOf(true) }

    val cleanupSuggestions by produceState<List<LibraryCleanupSuggestion>>(emptyList(), cachedCount, selectedSectionId) {
        value = runCatching {
            LibraryCleanupSuggestionService().suggest(viewModel.repository.getCachedTracksList())
        }.getOrDefault(emptyList())
    }
    val collectionSuggestions by produceState<List<com.example.data.CollectionSuggestion>>(emptyList(), cachedCount, selectedSectionId) {
        value = runCatching {
            com.example.data.LocalCollectionSuggestionService().suggest(viewModel.repository.getCachedTracksList())
        }.getOrDefault(emptyList())
    }

    LaunchedEffect(isConfigured) {
        if (isConfigured && libraries.isEmpty()) {
            viewModel.loadLibraries()
        }
    }

    val scrollState = rememberScrollState()

    Column(
        modifier = modifier
            .fillMaxSize()
            .background(Color.Transparent)
            .verticalScroll(scrollState)
            .padding(16.dp)
    ) {
        Text(
            text = "Settings",
            style = MaterialTheme.typography.headlineMedium.copy(
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.onBackground
            ),
            modifier = Modifier.padding(bottom = 24.dp)
        )

        Card(
            modifier = Modifier.fillMaxWidth().padding(bottom = 24.dp),
            shape = RoundedCornerShape(24.dp),
            colors = CardDefaults.cardColors(containerColor = Color.White.copy(alpha = 0.04f)),
            border = androidx.compose.foundation.BorderStroke(1.dp, Color.White.copy(alpha = 0.08f))
        ) {
            Column(modifier = Modifier.padding(16.dp)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(Icons.Rounded.HealthAndSafety, contentDescription = "Device readiness", tint = MaterialTheme.colorScheme.primary)
                    Spacer(Modifier.width(12.dp))
                    Text("Device Readiness", color = MaterialTheme.colorScheme.onSurface, fontWeight = FontWeight.Bold, style = MaterialTheme.typography.titleMedium)
                }
                Spacer(Modifier.height(12.dp))
                DiagnosticRow("Plex connection", if (isConfigured) "Configured" else "Not configured", isConfigured)
                DiagnosticRow("Active library", selectedLibraryName.ifBlank { "Not selected" }, selectedSectionId.isNotBlank())
                DiagnosticRow("Local index", if (cachedCount > 0) "$cachedCount tracks" else "Empty", cachedCount > 0)
                DiagnosticRow("Offline downloads", "${downloadedTracks.count { it.status == "completed" }} ready", downloadedTracks.any { it.status == "completed" })
                DiagnosticRow("Playback queue", "${queue.size} tracks", queue.isNotEmpty())
                DiagnosticRow("Background playback", "Media service enabled", true)
                DiagnosticRow("AI provider", viewModel.repository.settings.aiProvider.removeSuffix("Provider"), true)
                Text(
                    "Diagnostics never display Plex tokens or secret values. Physical-device checks still required for Bluetooth, lock-screen controls, and battery optimization.",
                    color = Color.White.copy(alpha = 0.45f),
                    style = MaterialTheme.typography.bodySmall,
                    modifier = Modifier.padding(top = 10.dp)
                )
            }
        }

        Card(
            modifier = Modifier.fillMaxWidth().padding(bottom = 24.dp),
            shape = RoundedCornerShape(24.dp),
            colors = CardDefaults.cardColors(containerColor = Color.White.copy(alpha = 0.04f)),
            border = androidx.compose.foundation.BorderStroke(1.dp, Color.White.copy(alpha = 0.08f))
        ) {
            Column(modifier = Modifier.padding(16.dp)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(Icons.Rounded.ContentPasteSearch, contentDescription = "Metadata review", tint = Color(0xFFFBBF24))
                    Spacer(Modifier.width(12.dp))
                    Text("Metadata Review", color = Color.White, fontWeight = FontWeight.Bold, style = MaterialTheme.typography.titleMedium)
                }
                Spacer(Modifier.height(8.dp))
                Text(
                    if (cleanupSuggestions.isEmpty()) "No obvious metadata issues found in the local Plex cache."
                    else "${cleanupSuggestions.size} review-only suggestions found in cached Plex metadata.",
                    color = Color.White.copy(alpha = 0.65f),
                    style = MaterialTheme.typography.bodySmall
                )
                cleanupSuggestions.take(3).forEach { suggestion ->
                    Text(
                        "• ${suggestion.issue} (${suggestion.ratingKey})",
                        color = Color(0xFFFBBF24).copy(alpha = 0.85f),
                        style = MaterialTheme.typography.bodySmall,
                        modifier = Modifier.padding(top = 5.dp)
                    )
                }
                Text(
                    "Suggestions are review-only; SpotAmp never edits Plex metadata automatically.",
                    color = Color.White.copy(alpha = 0.4f),
                    style = MaterialTheme.typography.bodySmall,
                    modifier = Modifier.padding(top = 8.dp)
                )
            }
        }

        if (collectionSuggestions.isNotEmpty()) {
            Card(
                modifier = Modifier.fillMaxWidth().padding(bottom = 24.dp),
                shape = RoundedCornerShape(24.dp),
                colors = CardDefaults.cardColors(containerColor = Color.White.copy(alpha = 0.04f)),
                border = androidx.compose.foundation.BorderStroke(1.dp, Color.White.copy(alpha = 0.08f))
            ) {
                Column(modifier = Modifier.padding(16.dp)) {
                    Text("Collection Ideas", color = Color.White, fontWeight = FontWeight.Bold, style = MaterialTheme.typography.titleMedium)
                    Text("Review-only groups derived from real Plex metadata.", color = Color.White.copy(alpha = 0.6f), style = MaterialTheme.typography.bodySmall, modifier = Modifier.padding(top = 6.dp))
                    collectionSuggestions.take(5).forEach { suggestion ->
                        Text("${suggestion.name} · ${suggestion.ratingKeys.size} tracks", color = Color(0xFF818CF8), style = MaterialTheme.typography.bodySmall, modifier = Modifier.padding(top = 6.dp))
                    }
                    Text("SpotAmp will not create or edit Plex collections automatically.", color = Color.White.copy(alpha = 0.4f), style = MaterialTheme.typography.bodySmall, modifier = Modifier.padding(top = 8.dp))
                }
            }
        }

        // Plex Server Configuration Card
        val isLightTheme = activeTheme == "Warm Light"
        val labelColor = if (isLightTheme) MaterialTheme.colorScheme.onSurface else Color.White
        val subLabelColor = if (isLightTheme) MaterialTheme.colorScheme.onSurface.copy(alpha = 0.8f) else Color.White.copy(alpha = 0.8f)
        val placeholderColor = if (isLightTheme) MaterialTheme.colorScheme.onSurface.copy(alpha = 0.4f) else Color.White.copy(alpha = 0.4f)
        val cardBgColor = if (isLightTheme) MaterialTheme.colorScheme.surface else Color.White.copy(alpha = 0.05f)
        val cardBorder = androidx.compose.foundation.BorderStroke(1.dp, if (isLightTheme) MaterialTheme.colorScheme.outline.copy(alpha = 0.2f) else Color.White.copy(alpha = 0.10f))
        
        val inputFocusedText = if (isLightTheme) MaterialTheme.colorScheme.onSurface else Color.White
        val inputUnfocusedText = if (isLightTheme) MaterialTheme.colorScheme.onSurface else Color.White
        val inputFocusedContainer = if (isLightTheme) MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f) else Color.Black.copy(alpha = 0.4f)
        val inputUnfocusedContainer = if (isLightTheme) MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.3f) else Color.Black.copy(alpha = 0.4f)
        val inputFocusedBorder = if (isLightTheme) MaterialTheme.colorScheme.primary else Color(0xFF6366F1).copy(alpha = 0.5f)
        val inputUnfocusedBorder = if (isLightTheme) MaterialTheme.colorScheme.outline.copy(alpha = 0.3f) else Color.White.copy(alpha = 0.05f)

        Card(
            modifier = Modifier
                .fillMaxWidth()
                .padding(bottom = 24.dp),
            shape = RoundedCornerShape(24.dp),
            colors = CardDefaults.cardColors(containerColor = cardBgColor),
            border = cardBorder,
            elevation = CardDefaults.cardElevation(defaultElevation = 0.dp)
        ) {
            Column(modifier = Modifier.padding(16.dp)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(
                        imageVector = Icons.Rounded.CloudQueue,
                        contentDescription = "Plex Connection",
                        tint = MaterialTheme.colorScheme.primary
                    )
                    Spacer(modifier = Modifier.width(12.dp))
                    Text(
                        text = "Plex Media Server Settings",
                        style = MaterialTheme.typography.titleMedium.copy(
                            fontWeight = FontWeight.Bold,
                            color = labelColor
                        )
                    )
                }

                Spacer(modifier = Modifier.height(18.dp))

                // Server URL Input
                Text(
                    text = "Plex Server URL",
                    style = MaterialTheme.typography.bodyMedium.copy(
                        fontWeight = FontWeight.Bold,
                        color = subLabelColor
                    ),
                    modifier = Modifier.padding(bottom = 6.dp)
                )
                OutlinedTextField(
                    value = baseUrlInput,
                    onValueChange = { baseUrlInput = it },
                    placeholder = { Text("e.g. http://192.168.1.50:32400", color = placeholderColor) },
                    singleLine = true,
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedTextColor = inputFocusedText,
                        unfocusedTextColor = inputUnfocusedText,
                        focusedBorderColor = inputFocusedBorder,
                        unfocusedBorderColor = inputUnfocusedBorder,
                        focusedContainerColor = inputFocusedContainer,
                        unfocusedContainerColor = inputUnfocusedContainer
                    ),
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(bottom = 16.dp)
                        .testTag("plex_url_input"),
                    shape = RoundedCornerShape(16.dp)
                )

                // Access Token Input
                Text(
                    text = "X-Plex-Token Access Key",
                    style = MaterialTheme.typography.bodyMedium.copy(
                        fontWeight = FontWeight.Bold,
                        color = subLabelColor
                    ),
                    modifier = Modifier.padding(bottom = 6.dp)
                )
                OutlinedTextField(
                    value = tokenInput,
                    onValueChange = { tokenInput = it },
                    placeholder = { Text("Enter your secret Plex Token", color = placeholderColor) },
                    singleLine = true,
                    visualTransformation = if (hideToken) PasswordVisualTransformation() else VisualTransformation.None,
                    trailingIcon = {
                        IconButton(onClick = { hideToken = !hideToken }) {
                            Icon(
                                imageVector = if (hideToken) Icons.Rounded.VisibilityOff else Icons.Rounded.Visibility,
                                contentDescription = "Toggle token visibility",
                                tint = labelColor.copy(alpha = 0.5f)
                            )
                        }
                    },
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedTextColor = inputFocusedText,
                        unfocusedTextColor = inputUnfocusedText,
                        focusedBorderColor = inputFocusedBorder,
                        unfocusedBorderColor = inputUnfocusedBorder,
                        focusedContainerColor = inputFocusedContainer,
                        unfocusedContainerColor = inputUnfocusedContainer
                    ),
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(bottom = 16.dp)
                        .testTag("plex_token_input"),
                    shape = RoundedCornerShape(16.dp)
                )

                // Lyrics Directory Input
                Text(
                    text = "Local Lyrics Directory Path (Optional)",
                    style = MaterialTheme.typography.bodyMedium.copy(
                        fontWeight = FontWeight.Bold,
                        color = subLabelColor
                    ),
                    modifier = Modifier.padding(bottom = 6.dp)
                )
                OutlinedTextField(
                    value = lyricsDirInput,
                    onValueChange = { lyricsDirInput = it },
                    placeholder = { Text("e.g. /storage/emulated/0/Music/Lyrics", color = placeholderColor) },
                    singleLine = true,
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedTextColor = inputFocusedText,
                        unfocusedTextColor = inputUnfocusedText,
                        focusedBorderColor = inputFocusedBorder,
                        unfocusedBorderColor = inputUnfocusedBorder,
                        focusedContainerColor = inputFocusedContainer,
                        unfocusedContainerColor = inputUnfocusedContainer
                    ),
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(bottom = 16.dp)
                        .testTag("lyrics_directory_input"),
                    shape = RoundedCornerShape(16.dp)
                )

                // Save Action
                Button(
                    onClick = {
                        viewModel.saveCredentials(baseUrlInput, tokenInput, lyricsDirInput)
                    },
                    enabled = baseUrlInput.isNotBlank() && tokenInput.isNotBlank() && !isLoading,
                    colors = ButtonDefaults.buttonColors(
                        containerColor = Color(0xFF6366F1),
                        contentColor = Color.White,
                        disabledContainerColor = Color(0xFF6366F1).copy(alpha = 0.3f),
                        disabledContentColor = Color.White.copy(alpha = 0.4f)
                    ),
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(48.dp)
                        .testTag("save_credentials_button"),
                    shape = RoundedCornerShape(16.dp)
                ) {
                    if (isLoading) {
                        CircularProgressIndicator(color = Color.White, modifier = Modifier.size(20.dp), strokeWidth = 2.dp)
                    } else {
                        Text("Connect & Save", fontWeight = FontWeight.Bold)
                    }
                }

                errorMessage?.let { error ->
                    Spacer(modifier = Modifier.height(12.dp))
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Icon(imageVector = Icons.Rounded.Error, contentDescription = "Error", tint = Color(0xFFE57373))
                        Spacer(modifier = Modifier.width(8.dp))
                        Text(
                            text = error,
                            style = MaterialTheme.typography.bodySmall.copy(color = Color(0xFFE57373)),
                            modifier = Modifier.weight(1f)
                        )
                        IconButton(onClick = { viewModel.clearErrorMessage() }) {
                            Icon(imageVector = Icons.Rounded.Close, contentDescription = "Dismiss", tint = Color.White.copy(alpha = 0.5f))
                        }
                    }
                }
            }
        }

        // Library Selection Card (Appears if configured)
        if (isConfigured) {
            Card(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(bottom = 24.dp),
                shape = RoundedCornerShape(24.dp),
                colors = CardDefaults.cardColors(containerColor = Color.White.copy(alpha = 0.05f)),
                border = androidx.compose.foundation.BorderStroke(1.dp, Color.White.copy(alpha = 0.10f)),
                elevation = CardDefaults.cardElevation(defaultElevation = 0.dp)
            ) {
                Column(modifier = Modifier.padding(16.dp)) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(
                            imageVector = Icons.Rounded.LibraryMusic,
                            contentDescription = "Library Selector",
                            tint = Color(0xFF818CF8)
                        )
                        Spacer(modifier = Modifier.width(12.dp))
                        Text(
                            text = "Select Music Library",
                            style = MaterialTheme.typography.titleMedium.copy(
                                fontWeight = FontWeight.Bold,
                                color = Color.White
                            )
                        )
                    }

                    Spacer(modifier = Modifier.height(14.dp))

                    if (isLoading && libraries.isEmpty()) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            CircularProgressIndicator(
                                modifier = Modifier.size(20.dp),
                                strokeWidth = 2.dp,
                                color = Color(0xFF818CF8)
                            )
                            Spacer(modifier = Modifier.width(10.dp))
                            Text(
                                "Loading libraries from Plex…",
                                color = Color.White.copy(alpha = 0.7f),
                                style = MaterialTheme.typography.bodyMedium
                            )
                        }
                    } else if (libraries.isEmpty()) {
                        Text(
                            if (errorMessage != null) {
                                "Plex did not return the library list. Check the server connection and try again."
                            } else {
                                "No music libraries were returned by Plex. Ensure the server has a Music Section."
                            },
                            color = Color.White.copy(alpha = 0.5f),
                            style = MaterialTheme.typography.bodyMedium
                        )
                        Spacer(modifier = Modifier.height(10.dp))
                        OutlinedButton(
                            onClick = { viewModel.loadLibraries() },
                            colors = ButtonDefaults.outlinedButtonColors(contentColor = Color.White),
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Icon(imageVector = Icons.Rounded.Refresh, contentDescription = null)
                            Spacer(modifier = Modifier.width(8.dp))
                            Text("Refresh Libraries")
                        }
                    } else {
                        Text(
                            "Choose the active Plex music library to browse and play:",
                            color = Color.White.copy(alpha = 0.6f),
                            style = MaterialTheme.typography.bodySmall,
                            modifier = Modifier.padding(bottom = 12.dp)
                        )

                        libraries.forEach { lib ->
                            val isActive = lib.key == selectedSectionId
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(vertical = 4.dp)
                                    .clip(RoundedCornerShape(12.dp))
                                    .background(if (isActive) Color(0xFF6366F1).copy(alpha = 0.15f) else Color.Transparent)
                                    .clickable { viewModel.selectLibrary(lib.key, lib.title) }
                                    .padding(12.dp),
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.SpaceBetween
                            ) {
                                Row(verticalAlignment = Alignment.CenterVertically) {
                                    Icon(
                                        imageVector = Icons.Rounded.MusicNote,
                                        contentDescription = null,
                                        tint = if (isActive) Color(0xFF818CF8) else Color.White.copy(alpha = 0.5f)
                                    )
                                    Spacer(modifier = Modifier.width(12.dp))
                                    Text(
                                        text = lib.title,
                                        style = MaterialTheme.typography.bodyLarge.copy(
                                            fontWeight = if (isActive) FontWeight.Bold else FontWeight.Normal,
                                            color = if (isActive) Color(0xFF818CF8) else Color.White
                                        )
                                    )
                                }
                                if (isActive) {
                                    Icon(
                                        imageVector = Icons.Rounded.CheckCircle,
                                        contentDescription = "Active Library",
                                        tint = Color(0xFF818CF8)
                                    )
                                }
                            }
                        }
                    }
                }
            }

            // Local Index Caching Card (Appears if library is selected)
            if (selectedSectionId.isNotEmpty()) {
                Card(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(bottom = 24.dp),
                    shape = RoundedCornerShape(24.dp),
                    colors = CardDefaults.cardColors(containerColor = Color.White.copy(alpha = 0.05f)),
                    border = androidx.compose.foundation.BorderStroke(1.dp, Color.White.copy(alpha = 0.10f)),
                    elevation = CardDefaults.cardElevation(defaultElevation = 0.dp)
                ) {
                    Column(modifier = Modifier.padding(16.dp)) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Icon(
                                imageVector = Icons.Rounded.AutoAwesome,
                                contentDescription = "Indexing",
                                tint = Color(0xFF818CF8)
                            )
                            Spacer(modifier = Modifier.width(12.dp))
                            Text(
                                text = "AI Library Indexing",
                                style = MaterialTheme.typography.titleMedium.copy(
                                    fontWeight = FontWeight.Bold,
                                    color = Color.White
                                )
                            )
                        }

                        Spacer(modifier = Modifier.height(8.dp))

                        Text(
                            text = "To let the optional cloud assistant interpret playlist requests without sending Plex credentials, the app creates a secure local metadata cache of your track list inside Room.",
                            color = Color.White.copy(alpha = 0.6f),
                            style = MaterialTheme.typography.bodySmall
                        )

                        Spacer(modifier = Modifier.height(16.dp))

                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Column {
                                Text(
                                    "Indexed Songs",
                                    color = MaterialTheme.colorScheme.onSurface,
                                    fontWeight = FontWeight.Bold,
                                    fontSize = 15.sp
                                )
                                Text(
                                    when (val state = syncState) {
                                        is LibrarySyncState.Fetching -> "Fetching page ${state.page}..."
                                        is LibrarySyncState.Processing -> "Processed ${state.validCount} tracks..."
                                        is LibrarySyncState.Saving -> "Saving ${state.validCount} tracks..."
                                        is LibrarySyncState.Complete -> "${state.indexedCount} tracks indexed"
                                        is LibrarySyncState.Failed -> "Sync failed: ${state.userMessage}"
                                        else -> "$cachedCount tracks cached"
                                    },
                                    color = MaterialTheme.colorScheme.primary,
                                    fontSize = 13.sp,
                                    fontWeight = FontWeight.Medium
                                )
                            }

                            Button(
                                onClick = { viewModel.syncLibraryCache() },
                                enabled = syncState is LibrarySyncState.Idle || syncState is LibrarySyncState.Complete || syncState is LibrarySyncState.Failed,
                                colors = ButtonDefaults.buttonColors(
                                    containerColor = if (cachedCount > 0) Color.White.copy(alpha = 0.1f) else Color(0xFF8B5CF6),
                                    contentColor = if (cachedCount > 0) Color.White else Color.White
                                ),
                                shape = RoundedCornerShape(10.dp),
                                modifier = Modifier.testTag("sync_index_button")
                            ) {
                                if (syncState is LibrarySyncState.Connecting || syncState is LibrarySyncState.Fetching || syncState is LibrarySyncState.Processing || syncState is LibrarySyncState.Saving) {
                                    CircularProgressIndicator(
                                        color = if (cachedCount > 0) Color.White else Color.Black,
                                        modifier = Modifier.size(18.dp),
                                        strokeWidth = 2.dp
                                    )
                                    Spacer(modifier = Modifier.width(8.dp))
                                    Text("Syncing...", fontSize = 13.sp)
                                } else {
                                    Icon(imageVector = Icons.Rounded.Sync, contentDescription = null, modifier = Modifier.size(16.dp))
                                    Spacer(modifier = Modifier.width(6.dp))
                                    Text(if (cachedCount > 0) "Update Index" else "Index Library", fontSize = 13.sp, fontWeight = FontWeight.Bold)
                                }
                            }
                        }
                    }
                }
            }

            // Visual Customization: Color Themes
            Card(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(bottom = 24.dp),
                shape = RoundedCornerShape(24.dp),
                colors = CardDefaults.cardColors(containerColor = if (activeTheme == "Warm Light") MaterialTheme.colorScheme.surface else Color.White.copy(alpha = 0.05f)),
                border = androidx.compose.foundation.BorderStroke(1.dp, if (activeTheme == "Warm Light") MaterialTheme.colorScheme.outline.copy(alpha = 0.2f) else Color.White.copy(alpha = 0.10f)),
                elevation = CardDefaults.cardElevation(defaultElevation = 0.dp)
            ) {
                Column(modifier = Modifier.padding(16.dp)) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(
                            imageVector = Icons.Rounded.Palette,
                            contentDescription = "Visual Customization",
                            tint = MaterialTheme.colorScheme.primary
                        )
                        Spacer(modifier = Modifier.width(12.dp))
                        Text(
                            text = "App Color Theme",
                            style = MaterialTheme.typography.titleMedium.copy(
                                fontWeight = FontWeight.Bold,
                                color = if (activeTheme == "Warm Light") MaterialTheme.colorScheme.onSurface else Color.White
                            )
                        )
                    }

                    Spacer(modifier = Modifier.height(14.dp))

                    var expanded by remember { mutableStateOf(false) }

                    Box(modifier = Modifier.fillMaxWidth()) {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clip(RoundedCornerShape(12.dp))
                                .background(if (activeTheme == "Warm Light") MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f) else Color.White.copy(alpha = 0.08f))
                                .clickable { expanded = true }
                                .padding(horizontal = 16.dp, vertical = 14.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            Column {
                                Text(
                                    "Active Theme",
                                    fontSize = 11.sp,
                                    color = (if (activeTheme == "Warm Light") MaterialTheme.colorScheme.onSurface else Color.White).copy(alpha = 0.6f)
                                )
                                Text(
                                    text = activeTheme.ifEmpty { "Default Dark" },
                                    fontSize = 16.sp,
                                    fontWeight = FontWeight.Bold,
                                    color = if (activeTheme == "Warm Light") MaterialTheme.colorScheme.onSurface else Color.White
                                )
                            }
                            Icon(
                                imageVector = if (expanded) Icons.Rounded.KeyboardArrowUp else Icons.Rounded.KeyboardArrowDown,
                                contentDescription = "Choose theme",
                                tint = if (activeTheme == "Warm Light") MaterialTheme.colorScheme.primary else Color.White
                            )
                        }

                        DropdownMenu(
                            expanded = expanded,
                            onDismissRequest = { expanded = false },
                            modifier = Modifier
                                .fillMaxWidth(0.9f)
                                .background(if (activeTheme == "Warm Light") MaterialTheme.colorScheme.surface else Color(0xFF1E1E2E))
                        ) {
                            val themesList = listOf(
                                "Default Dark",
                                "Emerald Aurora",
                                "Cyberpunk Neon",
                                "Sunset Glow",
                                "Cosmic Obsidian",
                                "Midnight Blue",
                                "Deep Amber",
                                "Rose Gold",
                                "Matrix Green",
                                "Nordic Frost",
                                "Solarized Dark",
                                "Warm Light"
                            )
                            themesList.forEach { themeName ->
                                val isSelected = themeName == activeTheme
                                DropdownMenuItem(
                                    text = {
                                        Text(
                                            text = themeName,
                                            fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Normal,
                                            color = if (isSelected) MaterialTheme.colorScheme.primary else if (activeTheme == "Warm Light") MaterialTheme.colorScheme.onSurface else Color.White
                                        )
                                    },
                                    onClick = {
                                        viewModel.updateTheme(themeName)
                                        expanded = false
                                    },
                                    leadingIcon = {
                                        Icon(
                                            imageVector = Icons.Rounded.Palette,
                                            contentDescription = null,
                                            tint = if (isSelected) MaterialTheme.colorScheme.primary else Color.White.copy(alpha = 0.4f),
                                            modifier = Modifier.size(18.dp)
                                        )
                                    },
                                    modifier = Modifier.background(
                                        if (isSelected) {
                                            MaterialTheme.colorScheme.primary.copy(alpha = 0.1f)
                                        } else {
                                            Color.Transparent
                                        }
                                    )
                                )
                            }
                        }
                    }
                }
            }

            // Advanced Audio Preferences
            val gaplessEnabled by viewModel.gaplessEnabledFlow.collectAsStateWithLifecycle()
            val eqEnabled by viewModel.equalizerEnabledFlow.collectAsStateWithLifecycle()
            val eqPreset by viewModel.equalizerPresetFlow.collectAsStateWithLifecycle()
            val normEnabled by viewModel.normalizationEnabledFlow.collectAsStateWithLifecycle()

            Card(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(bottom = 24.dp),
                shape = RoundedCornerShape(24.dp),
                colors = CardDefaults.cardColors(containerColor = Color.White.copy(alpha = 0.05f)),
                border = androidx.compose.foundation.BorderStroke(1.dp, Color.White.copy(alpha = 0.10f)),
                elevation = CardDefaults.cardElevation(defaultElevation = 0.dp)
            ) {
                Column(modifier = Modifier.padding(16.dp)) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(
                            imageVector = Icons.Rounded.Tune,
                            contentDescription = "Audio Engine Preferences",
                            tint = Color(0xFF818CF8)
                        )
                        Spacer(modifier = Modifier.width(12.dp))
                        Text(
                            text = "Advanced Audio Settings",
                            style = MaterialTheme.typography.titleMedium.copy(
                                fontWeight = FontWeight.Bold,
                                color = Color.White
                            )
                        )
                    }

                    Spacer(modifier = Modifier.height(14.dp))

                    // Gapless Playback Toggle
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(vertical = 4.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Column(modifier = Modifier.weight(1f)) {
                            Text("Gapless Playback", color = Color.White, fontWeight = FontWeight.SemiBold)
                            Text("Pre-buffer next song for instant, gap-free transitions", color = Color.White.copy(alpha = 0.6f), fontSize = 11.sp)
                        }
                        Switch(
                            checked = gaplessEnabled,
                            onCheckedChange = { viewModel.updateTheme(if (activeTheme == "") "Default Dark" else activeTheme); viewModel.updateEqualizerEnabled(eqEnabled); viewModel.repository.settings.gaplessEnabled = it; viewModel.playbackManager.refreshAudioSettings() },
                            colors = SwitchDefaults.colors(checkedThumbColor = Color(0xFF818CF8))
                        )
                    }

                    Spacer(modifier = Modifier.height(12.dp))

                    // Volume Normalization
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(vertical = 4.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Column(modifier = Modifier.weight(1f)) {
                            Text("Volume Normalization", color = Color.White, fontWeight = FontWeight.SemiBold)
                            Text("Auto-levels tracks to avoid sudden volume changes", color = Color.White.copy(alpha = 0.6f), fontSize = 11.sp)
                        }
                        Switch(
                            checked = normEnabled,
                            onCheckedChange = { viewModel.updateNormalizationEnabled(it) },
                            colors = SwitchDefaults.colors(checkedThumbColor = Color(0xFF818CF8))
                        )
                    }

                    Spacer(modifier = Modifier.height(12.dp))

                    // Equalizer and Presets
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(vertical = 4.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Column(modifier = Modifier.weight(1f)) {
                            Text("Equalizer", color = Color.White, fontWeight = FontWeight.SemiBold)
                            Text("Apply audio signal processing with preset values", color = Color.White.copy(alpha = 0.6f), fontSize = 11.sp)
                        }
                        Switch(
                            checked = eqEnabled,
                            onCheckedChange = { viewModel.updateEqualizerEnabled(it) },
                            colors = SwitchDefaults.colors(checkedThumbColor = Color(0xFF818CF8))
                        )
                    }

                    if (eqEnabled) {
                        Spacer(modifier = Modifier.height(12.dp))
                        Text("Equalizer Presets", color = Color.White, fontSize = 13.sp, fontWeight = FontWeight.Bold)
                        val presets = listOf("Flat", "Bass Booster", "Vocal Booster", "Treble Booster", "Electronic")
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(top = 6.dp),
                            horizontalArrangement = Arrangement.spacedBy(6.dp)
                        ) {
                            presets.forEach { preset ->
                                val isSelected = preset == eqPreset
                                AssistChip(
                                    onClick = { viewModel.updateEqualizerPreset(preset) },
                                    label = { Text(preset, fontSize = 11.sp) },
                                    colors = AssistChipDefaults.assistChipColors(
                                        containerColor = if (isSelected) Color(0xFF6366F1).copy(alpha = 0.2f) else Color.Transparent,
                                        labelColor = if (isSelected) Color(0xFF818CF8) else Color.White
                                    )
                                )
                            }
                        }
                    }
                }
            }

            // AI Modes Selection Card
            val activeAiProvider by viewModel.aiProviderFlow.collectAsStateWithLifecycle()
            val ggufModelSize by viewModel.ggufModelSizeFlow.collectAsStateWithLifecycle()
            
            // NEW: Background Sync Card
            val syncInterval by viewModel.syncIntervalHoursFlow.collectAsStateWithLifecycle()
            
            Card(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(bottom = 24.dp),
                shape = RoundedCornerShape(24.dp),
                colors = CardDefaults.cardColors(containerColor = Color.White.copy(alpha = 0.05f)),
                border = androidx.compose.foundation.BorderStroke(1.dp, Color.White.copy(alpha = 0.10f)),
                elevation = CardDefaults.cardElevation(defaultElevation = 0.dp)
            ) {
                Column(modifier = Modifier.padding(16.dp)) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(
                            imageVector = Icons.Rounded.Sync,
                            contentDescription = "Background Sync",
                            tint = Color(0xFF818CF8)
                        )
                        Spacer(modifier = Modifier.width(12.dp))
                        Text(
                            text = "Background Sync",
                            style = MaterialTheme.typography.titleMedium.copy(
                                fontWeight = FontWeight.Bold,
                                color = Color.White
                            )
                        )
                    }

                    Spacer(modifier = Modifier.height(8.dp))
                    Text(
                        text = "Automatically check for new music at periodic intervals, even when the app is closed:",
                        color = Color.White.copy(alpha = 0.6f),
                        style = MaterialTheme.typography.bodySmall
                    )

                    Spacer(modifier = Modifier.height(12.dp))

                    val intervals = listOf(0L, 1L, 6L, 12L, 24L)
                    intervals.forEach { hours ->
                        val isSelected = hours == syncInterval
                        val label = when (hours) {
                            0L -> "Disabled"
                            1L -> "Every 1 hour"
                            else -> "Every $hours hours"
                        }
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(vertical = 4.dp)
                                .clip(RoundedCornerShape(12.dp))
                                .background(if (isSelected) Color(0xFF6366F1).copy(alpha = 0.15f) else Color.Transparent)
                                .clickable { viewModel.updateSyncInterval(hours) }
                                .padding(12.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            Text(
                                text = label,
                                style = MaterialTheme.typography.bodyLarge.copy(
                                    fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Normal,
                                    color = if (isSelected) Color(0xFF818CF8) else Color.White
                                )
                            )
                            RadioButton(
                                selected = isSelected,
                                onClick = { viewModel.updateSyncInterval(hours) },
                                colors = RadioButtonDefaults.colors(selectedColor = Color(0xFF818CF8))
                            )
                        }
                    }
                }
            }

            var modelPathInput by remember { mutableStateOf(viewModel.repository.settings.aiModelPath) }
            Card(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(bottom = 24.dp),
                shape = RoundedCornerShape(24.dp),
                colors = CardDefaults.cardColors(containerColor = Color.White.copy(alpha = 0.05f)),
                border = androidx.compose.foundation.BorderStroke(1.dp, Color.White.copy(alpha = 0.10f)),
                elevation = CardDefaults.cardElevation(defaultElevation = 0.dp)
            ) {
                Column(modifier = Modifier.padding(16.dp)) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(
                            imageVector = Icons.Rounded.Memory,
                            contentDescription = "AI Mode",
                            tint = Color(0xFF818CF8)
                        )
                        Spacer(modifier = Modifier.width(12.dp))
                        Text(
                            text = "AI Mode Settings",
                            style = MaterialTheme.typography.titleMedium.copy(
                                fontWeight = FontWeight.Bold,
                                color = Color.White
                            )
                        )
                    }

                    Spacer(modifier = Modifier.height(8.dp))
                    Text(
                        text = "Customize how the playlist recommendations are computed:",
                        color = Color.White.copy(alpha = 0.6f),
                        style = MaterialTheme.typography.bodySmall
                    )

                    Spacer(modifier = Modifier.height(12.dp))

                    // Mode 1: Local-only
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(vertical = 4.dp)
                            .clip(RoundedCornerShape(12.dp))
                            .background(if (activeAiProvider == "LocalAIProvider") Color(0xFF6366F1).copy(alpha = 0.15f) else Color.Transparent)
                            .clickable { viewModel.updateAiProvider("LocalAIProvider") }
                            .padding(12.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Column(modifier = Modifier.weight(1f)) {
                            Text("Mode 1: Local Only AI", color = if (activeAiProvider == "LocalAIProvider") Color(0xFF818CF8) else Color.White, fontWeight = FontWeight.Bold)
                            Text("Private, offline, zero API latency. Rules-based intent parsing and moods tagging.", color = Color.White.copy(alpha = 0.6f), fontSize = 11.sp)
                        }
                        RadioButton(
                            selected = activeAiProvider == "LocalAIProvider",
                            onClick = { viewModel.updateAiProvider("LocalAIProvider") },
                            colors = RadioButtonDefaults.colors(selectedColor = Color(0xFF818CF8))
                        )
                    }

                    // Mode 2: Cloud AI Optional
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(vertical = 4.dp)
                            .clip(RoundedCornerShape(12.dp))
                            .background(if (activeAiProvider == "CloudAIProvider") Color(0xFF6366F1).copy(alpha = 0.15f) else Color.Transparent)
                            .clickable { viewModel.updateAiProvider("CloudAIProvider") }
                            .padding(12.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Column(modifier = Modifier.weight(1f)) {
                            Text("Mode 2: Cloud AI Optional", color = if (activeAiProvider == "CloudAIProvider") Color(0xFF818CF8) else Color.White, fontWeight = FontWeight.Bold)
                            Text("Optional cloud interpretation for richer playlists, library summaries, and DJ explanations.", color = Color.White.copy(alpha = 0.6f), fontSize = 11.sp)
                            Text("When used, a summarized library context is sent to the configured cloud provider; Plex credentials are never sent.", color = Color(0xFFFBBF24).copy(alpha = 0.8f), fontSize = 10.sp)
                        }
                        RadioButton(
                            selected = activeAiProvider == "CloudAIProvider",
                            onClick = { viewModel.updateAiProvider("CloudAIProvider") },
                            colors = RadioButtonDefaults.colors(selectedColor = Color(0xFF818CF8))
                        )
                    }

                    // Mode 3: No AI fallback
                    Row(
                        modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp).clip(RoundedCornerShape(12.dp))
                            .background(if (activeAiProvider == "HybridAIProvider") Color(0xFF6366F1).copy(alpha = 0.15f) else Color.Transparent)
                            .clickable { viewModel.updateAiProvider("HybridAIProvider") }.padding(12.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Column(modifier = Modifier.weight(1f)) {
                            Text("Mode 3: Hybrid AI", color = if (activeAiProvider == "HybridAIProvider") Color(0xFF818CF8) else Color.White, fontWeight = FontWeight.Bold)
                            Text("Prefer local interpretation, with safe deterministic fallback behavior.", color = Color.White.copy(alpha = 0.6f), fontSize = 11.sp)
                        }
                        RadioButton(selected = activeAiProvider == "HybridAIProvider", onClick = { viewModel.updateAiProvider("HybridAIProvider") }, colors = RadioButtonDefaults.colors(selectedColor = Color(0xFF818CF8)))
                    }

                    if (activeAiProvider == "LocalAIProvider" || activeAiProvider == "HybridAIProvider") {
                        Spacer(Modifier.height(10.dp))
                        OutlinedTextField(
                            value = modelPathInput,
                            onValueChange = { modelPathInput = it; viewModel.repository.settings.aiModelPath = it },
                            label = { Text("Local model path (optional)") },
                            placeholder = { Text("Not loaded; rules-based fallback active") },
                            singleLine = true,
                            colors = OutlinedTextFieldDefaults.colors(focusedTextColor = Color.White, unfocusedTextColor = Color.White, focusedBorderColor = Color(0xFF6366F1), unfocusedBorderColor = Color.White.copy(alpha = 0.1f)),
                            modifier = Modifier.fillMaxWidth()
                        )
                        Text("Model loading is experimental; current local provider remains deterministic and library-only.", color = Color.White.copy(alpha = 0.45f), fontSize = 11.sp, modifier = Modifier.padding(top = 6.dp))
                    }

                    // Mode 4: No AI fallback
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(vertical = 4.dp)
                            .clip(RoundedCornerShape(12.dp))
                            .background(if (activeAiProvider == "NoAIProvider") Color(0xFF6366F1).copy(alpha = 0.15f) else Color.Transparent)
                            .clickable { viewModel.updateAiProvider("NoAIProvider") }
                            .padding(12.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Column(modifier = Modifier.weight(1f)) {
                            Text("Mode 4: No AI Fallback", color = if (activeAiProvider == "NoAIProvider") Color(0xFF818CF8) else Color.White, fontWeight = FontWeight.Bold)
                            Text("Works without any model using deterministic local library ranking.", color = Color.White.copy(alpha = 0.6f), fontSize = 11.sp)
                        }
                        RadioButton(
                            selected = activeAiProvider == "NoAIProvider",
                            onClick = { viewModel.updateAiProvider("NoAIProvider") },
                            colors = RadioButtonDefaults.colors(selectedColor = Color(0xFF818CF8))
                        )
                    }

                    // Mode 5: Local GGUF LLM (llama.cpp)
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(vertical = 4.dp)
                            .clip(RoundedCornerShape(12.dp))
                            .background(if (activeAiProvider == "LlamaCppAIProvider") Color(0xFF6366F1).copy(alpha = 0.15f) else Color.Transparent)
                            .clickable { viewModel.updateAiProvider("LlamaCppAIProvider") }
                            .padding(12.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Column(modifier = Modifier.weight(1f)) {
                            Text("Mode 5: Local GGUF LLM (llama.cpp)", color = if (activeAiProvider == "LlamaCppAIProvider") Color(0xFF818CF8) else Color.White, fontWeight = FontWeight.Bold)
                            Text("Run private, offline LLMs on-device via native llama.cpp bindings.", color = Color.White.copy(alpha = 0.6f), fontSize = 11.sp)
                        }
                        RadioButton(
                            selected = activeAiProvider == "LlamaCppAIProvider",
                            onClick = { viewModel.updateAiProvider("LlamaCppAIProvider") },
                            colors = RadioButtonDefaults.colors(selectedColor = Color(0xFF818CF8))
                        )
                    }

                    if (activeAiProvider == "LlamaCppAIProvider") {
                        Spacer(Modifier.height(10.dp))
                        Text("Model Parameter Size Selection", color = Color.White, fontSize = 12.sp, fontWeight = FontWeight.Bold)
                        Text("Select a model target profile suitable for your device's memory capacity:", color = Color.White.copy(alpha = 0.6f), fontSize = 11.sp, modifier = Modifier.padding(bottom = 6.dp))
                        
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(6.dp)
                        ) {
                            val sizes = listOf("1.5B", "2B", "4B", "Custom")
                            sizes.forEach { size ->
                                val isSizeSelected = size == ggufModelSize
                                AssistChip(
                                    onClick = { viewModel.updateGgufModelSize(size) },
                                    label = { Text(size, fontSize = 11.sp) },
                                    colors = AssistChipDefaults.assistChipColors(
                                        containerColor = if (isSizeSelected) Color(0xFF6366F1).copy(alpha = 0.2f) else Color.Transparent,
                                        labelColor = if (isSizeSelected) Color(0xFF818CF8) else Color.White
                                    )
                                )
                            }
                        }
                        
                        Spacer(Modifier.height(8.dp))
                        OutlinedTextField(
                            value = modelPathInput,
                            onValueChange = { modelPathInput = it; viewModel.repository.settings.aiModelPath = it },
                            label = { Text("Local GGUF model path") },
                            placeholder = { Text("e.g. /storage/emulated/0/Download/gemma-3-4b.gguf") },
                            singleLine = true,
                            colors = OutlinedTextFieldDefaults.colors(focusedTextColor = Color.White, unfocusedTextColor = Color.White, focusedBorderColor = Color(0xFF6366F1), unfocusedBorderColor = Color.White.copy(alpha = 0.1f)),
                            modifier = Modifier.fillMaxWidth()
                        )
                        Spacer(Modifier.height(4.dp))
                        val isBridgeLoaded = com.example.data.LlamaCppBridge.isNativeLibraryLoaded()
                        Text(
                            text = if (isBridgeLoaded) "✓ llama.cpp JNI bindings successfully loaded." else "ℹ llama.cpp JNI bindings not loaded in this dev build. Falling back dynamically to secure rules-based parser on-device.",
                            color = if (isBridgeLoaded) Color(0xFF34D399) else Color(0xFFFBBF24),
                            fontSize = 11.sp,
                            modifier = Modifier.padding(bottom = 12.dp)
                        )
                    }
                }
            }

            // Last.fm Scrobbler Integration
            val lastFmEnabled by viewModel.lastFmEnabledFlow.collectAsStateWithLifecycle()
            val lastFmUsername by viewModel.lastFmUsernameFlow.collectAsStateWithLifecycle()
            val lastFmSessionKey by viewModel.lastFmSessionKeyFlow.collectAsStateWithLifecycle()

            var lastFmUserText by remember { mutableStateOf(lastFmUsername) }
            var lastFmSessionText by remember { mutableStateOf(lastFmSessionKey) }

            Card(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(bottom = 24.dp),
                shape = RoundedCornerShape(24.dp),
                colors = CardDefaults.cardColors(containerColor = Color.White.copy(alpha = 0.05f)),
                border = androidx.compose.foundation.BorderStroke(1.dp, Color.White.copy(alpha = 0.10f)),
                elevation = CardDefaults.cardElevation(defaultElevation = 0.dp)
            ) {
                Column(modifier = Modifier.padding(16.dp)) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween,
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Icon(
                                imageVector = Icons.Rounded.Share,
                                contentDescription = "Last.fm scrobbler",
                                tint = Color(0xFF818CF8)
                            )
                            Spacer(modifier = Modifier.width(12.dp))
                            Text(
                                text = "Last.fm Scrobbler",
                                style = MaterialTheme.typography.titleMedium.copy(
                                    fontWeight = FontWeight.Bold,
                                    color = Color.White
                                )
                            )
                        }
                        Switch(
                            checked = lastFmEnabled,
                            onCheckedChange = { viewModel.updateLastFmEnabled(it) },
                            colors = SwitchDefaults.colors(checkedThumbColor = Color(0xFF818CF8))
                        )
                    }

                    Spacer(modifier = Modifier.height(8.dp))
                    Text(
                        "Scrobble played tracks automatically to Last.fm database",
                        color = Color.White.copy(alpha = 0.6f),
                        fontSize = 11.sp
                    )

                    if (lastFmEnabled) {
                        Spacer(modifier = Modifier.height(14.dp))
                        OutlinedTextField(
                            value = lastFmUserText,
                            onValueChange = { lastFmUserText = it; viewModel.updateLastFmUsername(it) },
                            label = { Text("Last.fm Username", color = Color.White.copy(alpha = 0.6f)) },
                            singleLine = true,
                            colors = OutlinedTextFieldDefaults.colors(
                                focusedTextColor = Color.White,
                                unfocusedTextColor = Color.White,
                                focusedContainerColor = Color.Black.copy(alpha = 0.3f),
                                unfocusedContainerColor = Color.Black.copy(alpha = 0.3f)
                            ),
                            modifier = Modifier.fillMaxWidth()
                        )

                        Spacer(modifier = Modifier.height(10.dp))

                        OutlinedTextField(
                            value = lastFmSessionText,
                            onValueChange = { lastFmSessionText = it; viewModel.updateLastFmSessionKey(it) },
                            label = { Text("Last.fm Session Key", color = Color.White.copy(alpha = 0.6f)) },
                            singleLine = true,
                            visualTransformation = PasswordVisualTransformation(),
                            colors = OutlinedTextFieldDefaults.colors(
                                focusedTextColor = Color.White,
                                unfocusedTextColor = Color.White,
                                focusedContainerColor = Color.Black.copy(alpha = 0.3f),
                                unfocusedContainerColor = Color.Black.copy(alpha = 0.3f)
                            ),
                            modifier = Modifier.fillMaxWidth()
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun DiagnosticRow(label: String, value: String, healthy: Boolean) {
    Row(modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp), horizontalArrangement = Arrangement.SpaceBetween) {
        Text(label, color = Color.White.copy(alpha = 0.65f), style = MaterialTheme.typography.bodySmall)
        Text(value, color = if (healthy) Color(0xFF6EE7B7) else Color(0xFFFCA5A5), style = MaterialTheme.typography.bodySmall, fontWeight = FontWeight.SemiBold)
    }
}

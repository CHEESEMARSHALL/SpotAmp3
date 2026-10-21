package com.example.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.ScrollState
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.selection.selectableGroup
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.layout.LayoutCoordinates
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.layout.positionInRoot
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.stateDescription
import androidx.compose.ui.semantics.error
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.role
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlin.math.roundToInt
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.example.data.LibraryCleanupSuggestion
import com.example.data.LibraryCleanupSuggestionService
import com.example.data.LibrarySyncState
import com.example.data.CompanionConnectionPresentation
import com.example.data.CompanionConnectionStatus
import com.example.data.isNonEmptyRegularFile
import com.example.data.locallyAvailableDownloadRatingKeys
import com.example.BuildConfig
import com.example.R
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

private data class CleanupReviewData(
    val suggestions: List<LibraryCleanupSuggestion> = emptyList(),
    val trackTitles: Map<String, String> = emptyMap()
)

private data class LibraryReviewData(
    val cleanup: CleanupReviewData = CleanupReviewData(),
    val collectionSuggestions: List<com.example.data.CollectionSuggestion> = emptyList()
)

@Composable
internal fun isLightSettingsTheme(): Boolean = MaterialTheme.colorScheme.background.luminance() > 0.5f

@Composable
internal fun settingsPrimaryTextColor(): Color =
    if (isLightSettingsTheme()) MaterialTheme.colorScheme.onSurface else Color.White

@Composable
internal fun settingsSecondaryTextColor(darkAlpha: Float = 0.65f): Color =
    if (isLightSettingsTheme()) MaterialTheme.colorScheme.onSurfaceVariant else Color.White.copy(alpha = darkAlpha)

@Composable
internal fun settingsPanelColor(darkAlpha: Float = 0.05f): Color =
    if (isLightSettingsTheme()) MaterialTheme.colorScheme.surface else Color.White.copy(alpha = darkAlpha)

@Composable
internal fun settingsPanelBorderColor(darkAlpha: Float = 0.1f): Color =
    if (isLightSettingsTheme()) MaterialTheme.colorScheme.outline.copy(alpha = 0.2f)
    else Color.White.copy(alpha = darkAlpha)

@Composable
internal fun settingsAccentColor(darkColor: Color = Color(0xFF818CF8)): Color =
    if (isLightSettingsTheme()) MaterialTheme.colorScheme.primary else darkColor

internal fun cleanupIssueString(issue: String): Int = when (issue) {
    "Missing artist" -> R.string.settings_metadata_issue_missing_artist
    "Missing album" -> R.string.settings_metadata_issue_missing_album
    "Missing duration" -> R.string.settings_metadata_issue_missing_duration
    else -> R.string.settings_metadata_issue_generic
}

@Composable
internal fun SettingsConnectionErrorDismissButton(
    onDismiss: () -> Unit,
    modifier: Modifier = Modifier
) {
    IconButton(
        onClick = onDismiss,
        modifier = modifier
            .sizeIn(minWidth = 48.dp, minHeight = 48.dp)
            .testTag("settings_connection_error_dismiss")
    ) {
        Icon(
            imageVector = Icons.Rounded.Close,
            contentDescription = stringResource(R.string.settings_dismiss_connection_error),
            tint = settingsSecondaryTextColor(darkAlpha = 0.5f)
        )
    }
}

@Composable
internal fun SettingsTokenVisibilityButton(
    isHidden: Boolean,
    onToggle: () -> Unit,
    modifier: Modifier = Modifier
) {
    IconButton(
        onClick = onToggle,
        modifier = modifier
            .sizeIn(minWidth = 48.dp, minHeight = 48.dp)
            .testTag("settings_device_token_visibility")
    ) {
        Icon(
            imageVector = if (isHidden) Icons.Rounded.VisibilityOff else Icons.Rounded.Visibility,
            contentDescription = stringResource(
                if (isHidden) R.string.settings_show_device_token
                else R.string.settings_hide_device_token
            )
        )
    }
}

@Composable
internal fun SettingsConnectionErrorDetailsToggle(
    expanded: Boolean,
    onToggle: () -> Unit,
    modifier: Modifier = Modifier
) {
    val detailsStateDescription = stringResource(
        if (expanded) R.string.recovery_details_expanded
        else R.string.recovery_details_collapsed
    )
    TextButton(
        onClick = onToggle,
        contentPadding = PaddingValues(horizontal = 0.dp, vertical = 0.dp),
        modifier = modifier
            .defaultMinSize(minHeight = 48.dp)
            .testTag("settings_connection_error_details_toggle")
            .semantics { stateDescription = detailsStateDescription }
    ) {
        Text(
            stringResource(
                if (expanded) R.string.recovery_hide_details
                else R.string.recovery_show_details
            )
        )
    }
}

@Composable
internal fun CleanupSuggestionLabel(trackTitle: String?, issue: String) {
    Text(
        stringResource(
            R.string.settings_metadata_review_item,
            trackTitle?.takeIf { it.isNotBlank() }
                ?: stringResource(R.string.settings_metadata_review_unknown_track),
            stringResource(cleanupIssueString(issue))
        ),
        color = settingsAccentColor(Color(0xFFFBBF24)),
        style = MaterialTheme.typography.bodySmall,
        modifier = Modifier.padding(top = 5.dp)
    )
}

@Composable
internal fun CollectionSuggestionLabel(name: String, trackCount: Int) {
    Text(
        pluralStringResource(
            R.plurals.settings_collection_suggestion_track_count,
            trackCount,
            name,
            trackCount
        ),
        color = settingsAccentColor(),
        style = MaterialTheme.typography.bodySmall,
        modifier = Modifier.padding(top = 6.dp)
    )
}

@Composable
internal fun SettingsScreenTitle() {
    Text(
        text = stringResource(R.string.nav_settings),
        style = MaterialTheme.typography.headlineMedium.copy(
            fontWeight = FontWeight.Bold,
            color = MaterialTheme.colorScheme.onBackground
        ),
        modifier = Modifier.padding(bottom = 24.dp)
    )
}

@OptIn(ExperimentalLayoutApi::class, ExperimentalFoundationApi::class)
@Composable
fun SettingsScreen(
    viewModel: MusicViewModel,
    modifier: Modifier = Modifier,
    onOpenQaLab: (() -> Unit)? = null,
    onRequestPairing: () -> Unit = {},
    settingsScrollState: ScrollState? = null,
    showFirstRunSetup: Boolean = false,
    onFinishFirstRunSetup: () -> Unit = {},
    onDismissFirstRunSetup: () -> Unit = {}
) {
    val isConfigured by viewModel.isConfigured.collectAsStateWithLifecycle()
    val libraries by viewModel.libraries.collectAsStateWithLifecycle()
    val selectedSectionId by viewModel.selectedSectionId.collectAsStateWithLifecycle()
    val selectedLibraryName by viewModel.selectedLibraryName.collectAsStateWithLifecycle()
    val cachedCount by viewModel.cachedCount.collectAsStateWithLifecycle()
    val cachedLibraryRevision by viewModel.cachedLibraryRevision.collectAsStateWithLifecycle()
    val downloadedTracks by viewModel.downloadedTracks.collectAsStateWithLifecycle()
    val availableDownloadCount by produceState(0, downloadedTracks) {
        value = withContext(Dispatchers.IO) {
            locallyAvailableDownloadRatingKeys(downloadedTracks, ::isNonEmptyRegularFile).size
        }
    }
    val activeTheme by viewModel.activeThemeFlow.collectAsStateWithLifecycle()
    val companionConfigured by viewModel.companionConfigured.collectAsStateWithLifecycle()
    val companionConnectionStatus by viewModel.companionConnectionStatus.collectAsStateWithLifecycle()
    val connectedServiceName = if (companionConfigured) "SpotCore" else "music server"
    
    val isLoading by viewModel.isLoading.collectAsStateWithLifecycle()
    val libraryLoadError by viewModel.libraryLoadError.collectAsStateWithLifecycle()
    val syncState by viewModel.syncState.collectAsStateWithLifecycle()
    val isRefreshingServerLibrary by viewModel.isRefreshingServerLibrary.collectAsStateWithLifecycle()
    val syncDiagnosticInfo by viewModel.syncDiagnosticInfo.collectAsStateWithLifecycle()
    val connectionErrorMessage by viewModel.connectionErrorMessage.collectAsStateWithLifecycle()
    val connectionErrorTechnicalDetails by viewModel.connectionErrorTechnicalDetails.collectAsStateWithLifecycle()
    val errorMessage by viewModel.errorMessage.collectAsStateWithLifecycle()
    val settingsErrorMessage = connectionErrorMessage
        ?: errorMessage?.takeIf {
            it.contains("SpotCore", ignoreCase = true) && !(showFirstRunSetup && it == libraryLoadError)
        }

    var companionUrlInput by remember { mutableStateOf(viewModel.repository.settings.companionBackendUrl) }
    var companionTokenInput by remember { mutableStateOf(viewModel.repository.settings.companionBackendToken) }
    var hideCompanionToken by remember { mutableStateOf(true) }
    var showManualConnectionSettings by remember { mutableStateOf(false) }
    var showSyncDiagnostics by remember { mutableStateOf(false) }
    var showLibraryTools by remember { mutableStateOf(false) }
    var showConnectionErrorDetails by remember(settingsErrorMessage, connectionErrorTechnicalDetails) { mutableStateOf(false) }
    var offlineOnly by remember { mutableStateOf(viewModel.repository.settings.offlineOnly) }
    val sectionCoordinates = remember { mutableMapOf<String, LayoutCoordinates>() }
    var scrollViewportTopPx by remember { mutableFloatStateOf(0f) }
    val settingsScope = rememberCoroutineScope()

    val libraryReview by produceState(LibraryReviewData(), cachedLibraryRevision) {
        val tracks = runCatching { viewModel.repository.getCachedTracksList() }
            .getOrDefault(emptyList())
        value = withContext(Dispatchers.Default) {
            LibraryReviewData(
                cleanup = CleanupReviewData(
                    suggestions = LibraryCleanupSuggestionService().suggest(tracks),
                    trackTitles = tracks.associate { it.ratingKey to it.title }
                ),
                collectionSuggestions = com.example.data.LocalCollectionSuggestionService().suggest(tracks)
            )
        }
    }
    val cleanupReview = libraryReview.cleanup
    val collectionSuggestions = libraryReview.collectionSuggestions

    LaunchedEffect(isConfigured) {
        if (isConfigured && libraries.isEmpty()) {
            viewModel.loadLibraries()
        }
    }

    LaunchedEffect(companionConfigured) {
        if (companionConfigured) viewModel.ensureCompanionReachabilityChecked()
    }

    val scrollState = settingsScrollState ?: rememberScrollState()
    val sectionTopInsetPx = with(LocalDensity.current) { 12.dp.toPx() }

    fun sectionAnchorModifier(section: String): Modifier = Modifier.onGloballyPositioned {
        sectionCoordinates[section] = it
    }

    fun navigateToSection(section: String) {
        val coordinates = sectionCoordinates[section] ?: return
        val contentOffset = coordinates.positionInRoot().y - scrollViewportTopPx + scrollState.value
        val targetOffset = (contentOffset - sectionTopInsetPx).roundToInt().coerceIn(0, scrollState.maxValue)
        settingsScope.launch { scrollState.animateScrollTo(targetOffset) }
    }

    Column(
        modifier = modifier
            .fillMaxSize()
            .background(Color.Transparent)
            .onGloballyPositioned { scrollViewportTopPx = it.positionInRoot().y }
            .verticalScroll(scrollState)
            .padding(16.dp)
            .imePadding()
    ) {
        SettingsScreenTitle()

        FlowRow(
            modifier = Modifier.fillMaxWidth().padding(bottom = 16.dp),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            verticalArrangement = Arrangement.spacedBy(4.dp)
        ) {
            listOf(
                "Connection" to R.string.settings_group_connection,
                "Library" to R.string.settings_section_library,
                "Playback" to R.string.settings_group_playback,
                "DownloadsOffline" to R.string.settings_section_downloads_offline,
                "Discovery" to R.string.settings_section_discovery,
                "Appearance" to R.string.settings_group_appearance,
                "Advanced" to R.string.settings_group_advanced
            ).forEach { (section, labelRes) ->
                AssistChip(
                    onClick = { navigateToSection(section) },
                    label = { Text(stringResource(labelRes)) },
                    modifier = Modifier.testTag("settings_section_${section.lowercase()}")
                )
            }
        }

        if (showFirstRunSetup) {
            FirstRunSetupCard(
                syncState = syncState,
                cachedCount = cachedCount,
                syncEnqueueError = libraryLoadError,
                companionConnectionStatus = companionConnectionStatus,
                onRetry = viewModel::syncLibraryCache,
                onFinish = onFinishFirstRunSetup,
                onDismiss = onDismissFirstRunSetup
            )
        }

        SettingsGroupHeading(
            title = stringResource(R.string.settings_group_connection),
            modifier = Modifier
                .testTag("settings_heading_connection")
                .then(if (companionConfigured) sectionAnchorModifier("Connection") else Modifier)
        )
        Card(
            modifier = Modifier
                .fillMaxWidth()
                .padding(bottom = 24.dp),
            shape = RoundedCornerShape(24.dp),
            colors = CardDefaults.cardColors(containerColor = settingsPanelColor(darkAlpha = 0.04f)),
            border = androidx.compose.foundation.BorderStroke(1.dp, settingsPanelBorderColor(darkAlpha = 0.08f))
        ) {
            Column(modifier = Modifier.padding(16.dp)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(Icons.Rounded.HealthAndSafety, contentDescription = null, tint = MaterialTheme.colorScheme.primary)
                    Spacer(Modifier.width(12.dp))
                    Text(stringResource(R.string.settings_setup_summary), color = MaterialTheme.colorScheme.onSurface, fontWeight = FontWeight.Bold, style = MaterialTheme.typography.titleMedium)
                }
                Spacer(Modifier.height(12.dp))
                DiagnosticRow(
                    stringResource(R.string.settings_music_connection),
                    when {
                        companionConfigured -> stringResource(connectionStatusLabelResource(companionConnectionStatus))
                        isConfigured -> stringResource(R.string.connection_library_configured)
                        else -> stringResource(R.string.connection_not_paired)
                    },
                    companionConfigured && CompanionConnectionPresentation.isHealthy(companionConnectionStatus)
                )
                if (
                    companionConfigured &&
                    (companionConnectionStatus == CompanionConnectionStatus.CHECKING ||
                        companionConnectionStatus == CompanionConnectionStatus.TEMPORARILY_UNAVAILABLE ||
                        companionConnectionStatus == CompanionConnectionStatus.OFFLINE_LIBRARY_AVAILABLE)
                ) {
                    TextButton(
                        onClick = viewModel::retryCompanionConnection,
                        enabled = companionConnectionStatus != CompanionConnectionStatus.CHECKING,
                        modifier = Modifier
                            .align(Alignment.End)
                            .defaultMinSize(minHeight = 48.dp)
                            .testTag("settings-retry-connection")
                    ) {
                        if (companionConnectionStatus == CompanionConnectionStatus.CHECKING) {
                            CircularProgressIndicator(
                                modifier = Modifier.size(16.dp),
                                strokeWidth = 2.dp
                            )
                        } else {
                            Icon(Icons.Rounded.Refresh, contentDescription = null, modifier = Modifier.size(16.dp))
                        }
                        Spacer(Modifier.width(4.dp))
                        Text(
                            stringResource(
                                if (companionConnectionStatus == CompanionConnectionStatus.CHECKING) {
                                    R.string.settings_checking_connection
                                } else {
                                    R.string.settings_check_connection
                                }
                            )
                        )
                    }
                }
                DiagnosticRow(stringResource(R.string.settings_active_library), selectedLibraryName.ifBlank { stringResource(R.string.settings_not_selected) }, selectedSectionId.isNotBlank())
                DiagnosticRow(stringResource(R.string.settings_local_index), if (cachedCount > 0) pluralStringResource(R.plurals.settings_indexed_tracks, cachedCount, cachedCount) else stringResource(R.string.settings_index_empty), cachedCount > 0)
                DiagnosticRow(stringResource(R.string.settings_offline_downloads), pluralStringResource(R.plurals.settings_downloads_ready, availableDownloadCount, availableDownloadCount), availableDownloadCount > 0)
                Text(
                    stringResource(R.string.settings_offline_storage_note),
                    color = settingsSecondaryTextColor(darkAlpha = 0.65f),
                    style = MaterialTheme.typography.bodySmall,
                    modifier = Modifier.padding(top = 10.dp)
                )
            }
        }

        // Connection configuration
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
                .then(if (companionConfigured) Modifier else sectionAnchorModifier("Connection"))
                .padding(bottom = 24.dp),
            shape = RoundedCornerShape(24.dp),
            colors = CardDefaults.cardColors(containerColor = cardBgColor),
            border = cardBorder,
            elevation = CardDefaults.cardElevation(defaultElevation = 0.dp)
        ) {
            Column(modifier = Modifier.padding(16.dp)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(
                        imageVector = Icons.Rounded.Hub,
                        contentDescription = null,
                        tint = settingsAccentColor(Color(0xFF22D3EE))
                    )
                    Spacer(modifier = Modifier.width(12.dp))
                    Column {
                        Text(
                            text = stringResource(R.string.settings_spotcore_heading),
                            style = MaterialTheme.typography.titleMedium,
                            color = labelColor,
                            fontWeight = FontWeight.Bold
                        )
                        Text(
                            text = stringResource(
                                spotcoreConnectionSummaryString(companionConfigured, companionConnectionStatus)
                            ),
                            style = MaterialTheme.typography.bodySmall,
                            color = subLabelColor
                        )
                    }
                }

                Spacer(modifier = Modifier.height(16.dp))
                Button(
                    onClick = onRequestPairing,
                    modifier = Modifier
                        .fillMaxWidth()
                        .defaultMinSize(minHeight = 48.dp)
                        .testTag("settings_connection_pairing_action"),
                    shape = RoundedCornerShape(16.dp)
                ) {
                    Icon(Icons.Rounded.QrCodeScanner, contentDescription = null)
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(stringResource(R.string.settings_pair_spotcore_qr), fontWeight = FontWeight.Bold)
                }
                TextButton(
                    onClick = { showManualConnectionSettings = !showManualConnectionSettings },
                    modifier = Modifier
                        .fillMaxWidth()
                        .defaultMinSize(minHeight = 48.dp)
                        .testTag("manual_connection_toggle")
                ) {
                    Icon(
                        imageVector = if (showManualConnectionSettings) Icons.Rounded.ExpandLess else Icons.Rounded.ExpandMore,
                        contentDescription = null
                    )
                    Spacer(modifier = Modifier.width(6.dp))
                    Text(
                        stringResource(
                            if (showManualConnectionSettings) R.string.connection_hide_manual_options
                            else R.string.connection_show_manual_options
                        )
                    )
                }

                if (showManualConnectionSettings) {
                Spacer(modifier = Modifier.height(4.dp))
                OutlinedTextField(
                    value = companionUrlInput,
                    onValueChange = { companionUrlInput = it },
                    label = { Text(stringResource(R.string.settings_spotcore_url_label)) },
                    placeholder = { Text(stringResource(R.string.settings_spotcore_url_placeholder), color = placeholderColor) },
                    supportingText = { Text(stringResource(R.string.settings_spotcore_url_supporting)) },
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
                        .testTag("companion_url_input"),
                    shape = RoundedCornerShape(16.dp)
                )
                Spacer(modifier = Modifier.height(12.dp))
                OutlinedTextField(
                    value = companionTokenInput,
                    onValueChange = { companionTokenInput = it },
                    label = { Text(stringResource(R.string.settings_device_token)) },
                    singleLine = true,
                    visualTransformation = if (hideCompanionToken) PasswordVisualTransformation() else VisualTransformation.None,
                    trailingIcon = {
                        SettingsTokenVisibilityButton(
                            isHidden = hideCompanionToken,
                            onToggle = { hideCompanionToken = !hideCompanionToken }
                        )
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
                        .testTag("companion_token_input"),
                    shape = RoundedCornerShape(16.dp)
                )
                Spacer(modifier = Modifier.height(14.dp))
                Button(
                    onClick = {
                        if (viewModel.repository.settings.isCompanionConfigured && companionUrlInput.isBlank() && companionTokenInput.isBlank()) {
                            viewModel.disconnectCompanion()
                        } else {
                            viewModel.saveCompanionBackend(companionUrlInput, companionTokenInput)
                        }
                    },
                    enabled = (companionUrlInput.isBlank() && companionTokenInput.isBlank()) ||
                        (companionUrlInput.isNotBlank() && companionTokenInput.isNotBlank()),
                    modifier = Modifier
                        .fillMaxWidth()
                        .defaultMinSize(minHeight = 48.dp)
                        .testTag("save_companion_button"),
                    shape = RoundedCornerShape(16.dp)
                ) {
                    Text(
                        if (companionUrlInput.isBlank() && companionTokenInput.isBlank()) {
                            stringResource(
                                if (viewModel.repository.settings.isCompanionConfigured) R.string.connection_disconnect_spotcore
                                else R.string.connection_clear_manual_details
                            )
                        } else {
                            stringResource(R.string.connection_save_manual_details)
                        },
                        fontWeight = FontWeight.Bold
                    )
                }
                }

                if (viewModel.repository.settings.isCompanionConfigured) {
                    Spacer(modifier = Modifier.height(12.dp))
                    SettingsLibraryActions(
                        syncEnabled = syncState is LibrarySyncState.Idle ||
                            syncState is LibrarySyncState.Complete ||
                            syncState is LibrarySyncState.Failed,
                        syncIdle = syncState is LibrarySyncState.Idle,
                        serverRefreshInProgress = isRefreshingServerLibrary,
                        onRefreshServer = { viewModel.triggerServerRescan() },
                        onSyncLibrary = { viewModel.syncLibraryCache() }
                    )
                }

                settingsErrorMessage?.let { error ->
                    Spacer(modifier = Modifier.height(12.dp))
                    Column(modifier = Modifier.fillMaxWidth()) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Icon(imageVector = Icons.Rounded.Error, contentDescription = null, tint = MaterialTheme.colorScheme.error)
                            Spacer(modifier = Modifier.width(8.dp))
                            Text(
                                text = error,
                                style = MaterialTheme.typography.bodySmall.copy(color = MaterialTheme.colorScheme.error),
                                modifier = Modifier.weight(1f)
                                    .testTag("settings_connection_error")
                                    .semantics {
                                        liveRegion = androidx.compose.ui.semantics.LiveRegionMode.Polite
                                        error(error)
                                    }
                            )
                            SettingsConnectionErrorDismissButton(
                                onDismiss = { viewModel.clearErrorMessage() }
                            )
                        }
                        if (!connectionErrorTechnicalDetails.isNullOrBlank()) {
                            SettingsConnectionErrorDetailsToggle(
                                expanded = showConnectionErrorDetails,
                                onToggle = { showConnectionErrorDetails = !showConnectionErrorDetails }
                            )
                            if (showConnectionErrorDetails) {
                                Text(
                                    text = connectionErrorTechnicalDetails.orEmpty(),
                                    color = settingsSecondaryTextColor(darkAlpha = 0.55f),
                                    style = MaterialTheme.typography.labelSmall,
                                    modifier = Modifier.testTag("settings_connection_error_technical_details")
                                )
                            }
                        }
                    }
                }
            }
        }

        SettingsGroupHeading(
            title = stringResource(R.string.settings_group_library),
            modifier = Modifier
                .testTag("settings_heading_library")
                .then(sectionAnchorModifier("Library"))
        )
        if (isConfigured) {
            Card(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(bottom = 24.dp),
                shape = RoundedCornerShape(24.dp),
                colors = CardDefaults.cardColors(containerColor = settingsPanelColor()),
                border = androidx.compose.foundation.BorderStroke(1.dp, settingsPanelBorderColor()),
                elevation = CardDefaults.cardElevation(defaultElevation = 0.dp)
            ) {
                Column(modifier = Modifier.padding(16.dp)) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(
                            imageVector = Icons.Rounded.LibraryMusic,
                            contentDescription = null,
                            tint = settingsAccentColor()
                        )
                        Spacer(modifier = Modifier.width(12.dp))
                        Text(
                            text = stringResource(R.string.settings_select_music_library),
                            style = MaterialTheme.typography.titleMedium.copy(
                                fontWeight = FontWeight.Bold,
                                color = settingsPrimaryTextColor()
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
                                color = settingsAccentColor()
                            )
                            Spacer(modifier = Modifier.width(10.dp))
                            Text(
                                stringResource(R.string.settings_loading_library, connectedServiceName),
                                color = settingsSecondaryTextColor(darkAlpha = 0.7f),
                                style = MaterialTheme.typography.bodyMedium
                            )
                        }
                    } else if (libraries.isEmpty()) {
                        Text(
                            if (libraryLoadError != null) {
                                stringResource(R.string.settings_library_load_failed, connectedServiceName)
                            } else {
                                stringResource(R.string.settings_no_music_libraries, connectedServiceName)
                            },
                            color = settingsSecondaryTextColor(darkAlpha = 0.5f),
                            style = MaterialTheme.typography.bodyMedium
                        )
                        Spacer(modifier = Modifier.height(10.dp))
                        OutlinedButton(
                            onClick = { viewModel.loadLibraries() },
                            colors = ButtonDefaults.outlinedButtonColors(contentColor = settingsPrimaryTextColor()),
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Icon(imageVector = Icons.Rounded.Refresh, contentDescription = null)
                            Spacer(modifier = Modifier.width(8.dp))
                            Text(stringResource(R.string.settings_refresh_libraries))
                        }
                    } else {
                        Text(
                            stringResource(R.string.settings_choose_library_body),
                            color = settingsSecondaryTextColor(darkAlpha = 0.6f),
                            style = MaterialTheme.typography.bodySmall,
                            modifier = Modifier.padding(bottom = 12.dp)
                        )

                        Column(Modifier.selectableGroup()) {
                            libraries.forEach { lib ->
                                val isActive = lib.key == selectedSectionId
                                SettingsLibraryOption(
                                    title = lib.title,
                                    selected = isActive,
                                    onClick = { viewModel.selectLibrary(lib.key, lib.title) }
                                )
                            }
                        }
                    }
                }
            }

            // Local Index Caching Card (Appears if library is selected)
            if (selectedSectionId.isNotEmpty()) {
                SettingsLibraryDetailsCard(
                    syncState = syncState,
                    cachedCount = cachedCount,
                    onSync = viewModel::syncLibraryCache
                )
            }

        } else {
            Card(
                modifier = Modifier.fillMaxWidth().padding(bottom = 24.dp),
                shape = RoundedCornerShape(24.dp),
                colors = CardDefaults.cardColors(containerColor = settingsPanelColor()),
                border = androidx.compose.foundation.BorderStroke(1.dp, settingsPanelBorderColor())
            ) {
                Column(
                    modifier = Modifier.fillMaxWidth().padding(18.dp),
                    verticalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    Icon(Icons.Rounded.LibraryMusic, contentDescription = null, tint = MaterialTheme.colorScheme.primary)
                    Text(
                        text = stringResource(R.string.settings_library_pair_title),
                        color = settingsPrimaryTextColor(),
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold
                    )
                    Text(
                        text = stringResource(R.string.settings_library_pair_body),
                        color = settingsSecondaryTextColor(darkAlpha = 0.68f),
                        style = MaterialTheme.typography.bodyMedium
                    )
                    Button(onClick = onRequestPairing, modifier = Modifier.fillMaxWidth()) {
                        Icon(Icons.Rounded.QrCodeScanner, contentDescription = null)
                        Spacer(Modifier.width(8.dp))
                        Text(stringResource(R.string.home_pairing_cta))
                    }
                }
            }
        }

            SettingsGroupHeading(
                title = stringResource(R.string.settings_group_playback),
                modifier = Modifier
                    .testTag("settings_heading_playback")
                    .then(sectionAnchorModifier("Playback"))
            )
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
                colors = CardDefaults.cardColors(containerColor = settingsPanelColor()),
                border = androidx.compose.foundation.BorderStroke(1.dp, settingsPanelBorderColor()),
                elevation = CardDefaults.cardElevation(defaultElevation = 0.dp)
            ) {
                Column(modifier = Modifier.padding(16.dp)) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(
                            imageVector = Icons.Rounded.Tune,
                            contentDescription = null,
                            tint = settingsAccentColor()
                        )
                        Spacer(modifier = Modifier.width(12.dp))
                        Text(
                            text = stringResource(R.string.settings_sound_heading),
                            style = MaterialTheme.typography.titleMedium.copy(
                                fontWeight = FontWeight.Bold,
                                color = settingsPrimaryTextColor()
                            )
                        )
                    }

                    Spacer(modifier = Modifier.height(14.dp))

                    // Gapless Playback Toggle
                    SettingsToggleRow(
                        label = stringResource(R.string.settings_gapless_title),
                        description = stringResource(R.string.settings_gapless_body),
                        checked = gaplessEnabled,
                        onCheckedChange = viewModel::updateGaplessEnabled
                    )

                    Spacer(modifier = Modifier.height(12.dp))

                    // Volume Normalization
                    SettingsToggleRow(
                        label = stringResource(R.string.settings_volume_normalization_title),
                        description = stringResource(R.string.settings_volume_normalization_body),
                        checked = normEnabled,
                        onCheckedChange = viewModel::updateNormalizationEnabled
                    )

                    Spacer(modifier = Modifier.height(12.dp))

                    // Equalizer and Presets
                    SettingsToggleRow(
                        label = stringResource(R.string.settings_equalizer_title),
                        description = stringResource(R.string.settings_equalizer_body),
                        checked = eqEnabled,
                        onCheckedChange = viewModel::updateEqualizerEnabled
                    )

                    if (eqEnabled) {
                        Spacer(modifier = Modifier.height(12.dp))
                        Text(stringResource(R.string.settings_equalizer_presets), color = settingsPrimaryTextColor(), fontSize = 13.sp, fontWeight = FontWeight.Bold)
                        val presets = listOf("Flat", "Bass Booster", "Vocal Booster", "Treble Booster", "Electronic")
                        FlowRow(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(top = 6.dp),
                            horizontalArrangement = Arrangement.spacedBy(6.dp),
                            verticalArrangement = Arrangement.spacedBy(6.dp)
                        ) {
                            presets.forEach { preset ->
                                val isSelected = preset == eqPreset
                                AssistChip(
                                    onClick = { viewModel.updateEqualizerPreset(preset) },
                                    label = { Text(preset, fontSize = 11.sp) },
                                    colors = AssistChipDefaults.assistChipColors(
                                        containerColor = if (isSelected) settingsAccentColor(Color(0xFF6366F1)).copy(alpha = 0.2f) else Color.Transparent,
                                        labelColor = if (isSelected) settingsAccentColor() else settingsPrimaryTextColor()
                                    )
                                )
                            }
                        }
                    }
                }
            }

            SettingsGroupHeading(
                title = stringResource(R.string.settings_section_downloads_offline),
                modifier = Modifier
                    .testTag("settings_heading_downloads_offline")
                    .then(sectionAnchorModifier("DownloadsOffline"))
            )
            Card(
                modifier = Modifier.fillMaxWidth().padding(bottom = 24.dp),
                shape = RoundedCornerShape(20.dp),
                colors = CardDefaults.cardColors(containerColor = settingsPanelColor()),
                border = androidx.compose.foundation.BorderStroke(1.dp, settingsPanelBorderColor())
            ) {
                SettingsToggleRow(
                    label = stringResource(R.string.settings_downloaded_only_title),
                    description = stringResource(
                        if (offlineOnly) R.string.settings_downloaded_only_on_summary
                        else R.string.settings_downloaded_only_off_summary
                    ),
                    checked = offlineOnly,
                    onCheckedChange = { newValue ->
                        viewModel.repository.settings.offlineOnly = newValue
                        offlineOnly = newValue
                    },
                    modifier = Modifier.padding(16.dp),
                    tag = "settings_downloaded_only_row"
                )
            }

            // AI Modes Selection Card
            val activeAiProvider by viewModel.aiProviderFlow.collectAsStateWithLifecycle()
            val ggufModelSize by viewModel.ggufModelSizeFlow.collectAsStateWithLifecycle()

            SettingsGroupHeading(
                title = stringResource(R.string.settings_group_discovery),
                modifier = Modifier
                    .testTag("settings_heading_discovery")
                    .then(sectionAnchorModifier("Discovery"))
            )
            // Background library refresh
            val syncInterval by viewModel.syncIntervalHoursFlow.collectAsStateWithLifecycle()

            BackgroundLibraryUpdatesCard(
                intervalHours = syncInterval,
                onIntervalSelected = viewModel::updateSyncInterval
            )

            var modelPathInput by remember { mutableStateOf(viewModel.repository.settings.aiModelPath) }
            Card(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(bottom = 24.dp),
                shape = RoundedCornerShape(24.dp),
                colors = CardDefaults.cardColors(containerColor = settingsPanelColor()),
                border = androidx.compose.foundation.BorderStroke(1.dp, settingsPanelBorderColor()),
                elevation = CardDefaults.cardElevation(defaultElevation = 0.dp)
            ) {
                Column(modifier = Modifier.padding(16.dp)) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(
                            imageVector = Icons.Rounded.Memory,
                            contentDescription = null,
                            tint = settingsAccentColor()
                        )
                        Spacer(modifier = Modifier.width(12.dp))
                        Text(
                            text = stringResource(R.string.settings_playlist_suggestions_title),
                            style = MaterialTheme.typography.titleMedium.copy(
                                fontWeight = FontWeight.Bold,
                                color = settingsPrimaryTextColor()
                            )
                        )
                    }

                    Spacer(modifier = Modifier.height(8.dp))
                    Text(
                        text = stringResource(R.string.settings_playlist_matching_summary),
                        color = settingsSecondaryTextColor(darkAlpha = 0.6f),
                        style = MaterialTheme.typography.bodySmall
                    )

                    Spacer(modifier = Modifier.height(12.dp))

                    // On-device library matching
                    Column(Modifier.selectableGroup()) {
                        val localMatchingSelected = activeAiProvider == "LocalAIProvider" || activeAiProvider == "HybridAIProvider"
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(vertical = 4.dp)
                                .clip(RoundedCornerShape(12.dp))
                                .background(if (localMatchingSelected) settingsAccentColor(Color(0xFF6366F1)).copy(alpha = 0.15f) else Color.Transparent)
                                .selectable(
                                    selected = localMatchingSelected,
                                    role = Role.RadioButton,
                                    onClick = { viewModel.updateAiProvider("LocalAIProvider") }
                                )
                                .padding(12.dp)
                                .testTag("settings_ai_provider_on_device"),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            Column(modifier = Modifier.weight(1f)) {
                                Text(stringResource(R.string.settings_on_device_matching_title), color = if (localMatchingSelected) settingsAccentColor() else settingsPrimaryTextColor(), fontWeight = FontWeight.Bold)
                                Text(stringResource(R.string.settings_on_device_matching_body), color = settingsSecondaryTextColor(darkAlpha = 0.6f), fontSize = 11.sp)
                            }
                            RadioButton(
                                selected = localMatchingSelected,
                                onClick = null,
                                colors = RadioButtonDefaults.colors(selectedColor = settingsAccentColor())
                            )
                        }

                        // Mode 4: No AI fallback
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(vertical = 4.dp)
                                .clip(RoundedCornerShape(12.dp))
                                .background(if (activeAiProvider == "NoAIProvider") settingsAccentColor(Color(0xFF6366F1)).copy(alpha = 0.15f) else Color.Transparent)
                                .selectable(
                                    selected = activeAiProvider == "NoAIProvider",
                                    role = Role.RadioButton,
                                    onClick = { viewModel.updateAiProvider("NoAIProvider") }
                                )
                                .padding(12.dp)
                                .testTag("settings_ai_provider_library_ranking"),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            Column(modifier = Modifier.weight(1f)) {
                                Text(stringResource(R.string.settings_library_ranking_title), color = if (activeAiProvider == "NoAIProvider") settingsAccentColor() else settingsPrimaryTextColor(), fontWeight = FontWeight.Bold)
                                Text(stringResource(R.string.settings_library_ranking_body), color = settingsSecondaryTextColor(darkAlpha = 0.6f), fontSize = 11.sp)
                            }
                            RadioButton(
                                selected = activeAiProvider == "NoAIProvider",
                                onClick = null,
                                colors = RadioButtonDefaults.colors(selectedColor = settingsAccentColor())
                            )
                        }

                        // Optional experimental model
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(vertical = 4.dp)
                                .clip(RoundedCornerShape(12.dp))
                                .background(if (activeAiProvider == "LlamaCppAIProvider") settingsAccentColor(Color(0xFF6366F1)).copy(alpha = 0.15f) else Color.Transparent)
                                .selectable(
                                    selected = activeAiProvider == "LlamaCppAIProvider",
                                    role = Role.RadioButton,
                                    onClick = { viewModel.updateAiProvider("LlamaCppAIProvider") }
                                )
                                .padding(12.dp)
                                .testTag("settings_ai_provider_experimental"),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            Column(modifier = Modifier.weight(1f)) {
                                Text(stringResource(R.string.settings_experimental_model_title), color = if (activeAiProvider == "LlamaCppAIProvider") settingsAccentColor() else settingsPrimaryTextColor(), fontWeight = FontWeight.Bold)
                                Text(stringResource(R.string.settings_experimental_model_body), color = settingsSecondaryTextColor(darkAlpha = 0.6f), fontSize = 11.sp)
                            }
                            RadioButton(
                                selected = activeAiProvider == "LlamaCppAIProvider",
                                onClick = null,
                                colors = RadioButtonDefaults.colors(selectedColor = settingsAccentColor())
                            )
                        }
                    }

                    if (activeAiProvider == "LlamaCppAIProvider") {
                        Spacer(Modifier.height(10.dp))
                        Text(stringResource(R.string.settings_model_size_profile), color = settingsPrimaryTextColor(), fontSize = 12.sp, fontWeight = FontWeight.Bold)
                        Text(stringResource(R.string.settings_model_size_profile_help), color = settingsSecondaryTextColor(darkAlpha = 0.6f), fontSize = 11.sp, modifier = Modifier.padding(bottom = 6.dp))
                        
                        FlowRow(
                            modifier = Modifier.fillMaxWidth().selectableGroup(),
                            horizontalArrangement = Arrangement.spacedBy(6.dp),
                            verticalArrangement = Arrangement.spacedBy(6.dp)
                        ) {
                            val sizes = listOf("1.5B", "2B", "4B", "Custom")
                            sizes.forEach { size ->
                                val isSizeSelected = size == ggufModelSize
                                FilterChip(
                                    selected = isSizeSelected,
                                    onClick = { viewModel.updateGgufModelSize(size) },
                                    label = { Text(size, fontSize = 11.sp) },
                                    colors = FilterChipDefaults.filterChipColors(
                                        containerColor = Color.Transparent,
                                        labelColor = settingsPrimaryTextColor(),
                                        selectedContainerColor = settingsAccentColor(Color(0xFF6366F1)).copy(alpha = 0.2f),
                                        selectedLabelColor = settingsAccentColor()
                                    ),
                                    modifier = Modifier.testTag("settings_model_profile_$size")
                                )
                            }
                        }
                        
                        Spacer(Modifier.height(8.dp))
                        OutlinedTextField(
                            value = modelPathInput,
                            onValueChange = { modelPathInput = it; viewModel.repository.settings.aiModelPath = it },
                            label = { Text(stringResource(R.string.settings_model_file_path)) },
                            placeholder = { Text(stringResource(R.string.settings_model_file_placeholder)) },
                            singleLine = true,
                            colors = OutlinedTextFieldDefaults.colors(focusedTextColor = settingsPrimaryTextColor(), unfocusedTextColor = settingsPrimaryTextColor(), focusedBorderColor = settingsAccentColor(Color(0xFF6366F1)), unfocusedBorderColor = settingsPanelBorderColor()),
                            modifier = Modifier.fillMaxWidth()
                        )
                        Spacer(Modifier.height(4.dp))
                        if (BuildConfig.DEBUG) {
                            val isBridgeLoaded = com.example.data.LlamaCppBridge.isNativeLibraryLoaded()
                            Text(
                                text = if (isBridgeLoaded) "✓ On-device model support is available in this build." else "ℹ On-device model support is unavailable in this build; SpotAmp will use its local fallback.",
                                color = if (isBridgeLoaded) Color(0xFF166534) else settingsAccentColor(Color(0xFFFBBF24)),
                                fontSize = 11.sp,
                                modifier = Modifier.padding(bottom = 12.dp)
                            )
                        }
                    }
                }
            }

            // Last.fm Scrobbler Integration
            val lastFmEnabled by viewModel.lastFmEnabledFlow.collectAsStateWithLifecycle()
            val lastFmUsername by viewModel.lastFmUsernameFlow.collectAsStateWithLifecycle()
            val lastFmApiKey by viewModel.lastFmApiKeyFlow.collectAsStateWithLifecycle()
            val lastFmApiSecret by viewModel.lastFmApiSecretFlow.collectAsStateWithLifecycle()
            val lastFmAuthorizationPending by viewModel.lastFmAuthorizationPendingFlow.collectAsStateWithLifecycle()
            val lastFmNowPlaying by viewModel.lastFmNowPlayingFlow.collectAsStateWithLifecycle()
            val lastFmScrobble by viewModel.lastFmScrobbleFlow.collectAsStateWithLifecycle()
            val lastFmPrivate by viewModel.lastFmPrivateFlow.collectAsStateWithLifecycle()
            val lastFmAuthMessage by viewModel.lastFmAuthMessage.collectAsStateWithLifecycle()
            val lastFmAuthFailed by viewModel.lastFmAuthFailed.collectAsStateWithLifecycle()
            var lastFmApiKeyText by remember(lastFmApiKey) { mutableStateOf(lastFmApiKey) }
            var lastFmApiSecretText by remember(lastFmApiSecret) { mutableStateOf(lastFmApiSecret) }

            Card(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(bottom = 24.dp),
                shape = RoundedCornerShape(24.dp),
                colors = CardDefaults.cardColors(containerColor = settingsPanelColor()),
                border = androidx.compose.foundation.BorderStroke(1.dp, settingsPanelBorderColor()),
                elevation = CardDefaults.cardElevation(defaultElevation = 0.dp)
            ) {
                Column(modifier = Modifier.padding(16.dp)) {
                    SettingsToggleRow(
                        label = stringResource(R.string.settings_lastfm_title),
                        description = stringResource(R.string.settings_lastfm_body),
                        checked = lastFmEnabled,
                        onCheckedChange = viewModel::updateLastFmEnabled,
                        leadingContent = {
                            Icon(Icons.Rounded.Share, contentDescription = null, tint = settingsAccentColor())
                        }
                    )
                    SettingsLastFmSharingHint(
                        lastFmEnabled = lastFmEnabled,
                        hasConnectedAccount = lastFmUsername.isNotBlank()
                    )

                    Spacer(modifier = Modifier.height(12.dp))
                    @Composable fun settingToggle(label: String, checked: Boolean, onChange: (Boolean) -> Unit) {
                        SettingsToggleRow(label = label, checked = checked, onCheckedChange = onChange)
                    }
                    settingToggle(stringResource(R.string.settings_lastfm_now_playing), lastFmNowPlaying, viewModel::updateLastFmNowPlaying)
                    settingToggle(stringResource(R.string.settings_lastfm_scrobble_listens), lastFmScrobble, viewModel::updateLastFmScrobble)
                    settingToggle(stringResource(R.string.settings_lastfm_private_session), lastFmPrivate, viewModel::updateLastFmPrivate)
                    Spacer(modifier = Modifier.height(10.dp))
                    OutlinedTextField(lastFmApiKeyText, { lastFmApiKeyText = it }, label = { Text(stringResource(R.string.settings_lastfm_api_key)) }, singleLine = true, modifier = Modifier.fillMaxWidth())
                    Spacer(modifier = Modifier.height(8.dp))
                    OutlinedTextField(lastFmApiSecretText, { lastFmApiSecretText = it }, label = { Text(stringResource(R.string.settings_lastfm_api_secret)) }, singleLine = true, visualTransformation = PasswordVisualTransformation(), modifier = Modifier.fillMaxWidth())
                    Spacer(modifier = Modifier.height(8.dp))
                    Text(
                        if (lastFmUsername.isBlank()) stringResource(R.string.settings_lastfm_not_connected)
                        else stringResource(R.string.settings_lastfm_connected_as, lastFmUsername),
                        color = settingsSecondaryTextColor(darkAlpha = 0.75f),
                        fontSize = 12.sp
                    )
                    LastFmAuthorizationStatusText(lastFmAuthMessage, lastFmAuthFailed)
                    SettingsLastFmAuthorizationActions(
                        hasConnectedAccount = lastFmUsername.isNotBlank(),
                        authorizationPending = lastFmAuthorizationPending,
                        onSaveCredentials = {
                            viewModel.updateLastFmApiKey(lastFmApiKeyText)
                            viewModel.updateLastFmApiSecret(lastFmApiSecretText)
                        },
                        onStartAuthorization = viewModel::startLastFmAuthorization,
                        onCompleteAuthorization = viewModel::completeLastFmAuthorization,
                        onDisconnect = viewModel::disconnectLastFm
                    )
                }
            }
        SettingsAppearanceSection(
            activeTheme = activeTheme,
            onThemeSelected = viewModel::updateTheme,
            modifier = sectionAnchorModifier("Appearance")
        )

        SettingsGroupHeading(
            title = stringResource(R.string.settings_group_advanced),
            modifier = Modifier
                .then(sectionAnchorModifier("Advanced"))
                .testTag("settings_heading_advanced")
        )
        val libraryToolsState = stringResource(
            if (showLibraryTools) R.string.settings_disclosure_expanded else R.string.settings_disclosure_collapsed
        )
        Card(
            modifier = Modifier.fillMaxWidth().padding(bottom = 16.dp),
            shape = RoundedCornerShape(18.dp),
            colors = CardDefaults.cardColors(containerColor = settingsPanelColor(darkAlpha = 0.035f))
        ) {
            Row(
                modifier = Modifier.fillMaxWidth()
                    .clickable { showLibraryTools = !showLibraryTools }
                    .semantics {
                        role = Role.Button
                        stateDescription = libraryToolsState
                    }
                    .testTag("settings_library_tools_disclosure")
                    .padding(16.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Text(stringResource(R.string.settings_library_tools_disclosure_title), color = settingsPrimaryTextColor(), fontWeight = FontWeight.SemiBold)
                    Text(stringResource(R.string.settings_library_tools_disclosure_summary), color = settingsSecondaryTextColor(darkAlpha = 0.55f), style = MaterialTheme.typography.bodySmall)
                }
                Icon(
                    imageVector = if (showLibraryTools) Icons.Rounded.ExpandLess else Icons.Rounded.ExpandMore,
                    contentDescription = null,
                    tint = settingsSecondaryTextColor(darkAlpha = 0.7f)
                )
            }
        }

        if (showLibraryTools) {
            Card(
                modifier = Modifier.fillMaxWidth().padding(bottom = 24.dp),
                shape = RoundedCornerShape(24.dp),
                colors = CardDefaults.cardColors(containerColor = settingsPanelColor(darkAlpha = 0.04f)),
                border = androidx.compose.foundation.BorderStroke(1.dp, settingsPanelBorderColor(darkAlpha = 0.08f))
            ) {
                Column(modifier = Modifier.padding(16.dp)) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(Icons.Rounded.ContentPasteSearch, contentDescription = stringResource(R.string.settings_library_review_title), tint = settingsAccentColor(Color(0xFFFBBF24)))
                        Spacer(Modifier.width(12.dp))
                        Text(stringResource(R.string.settings_library_review_title), color = settingsPrimaryTextColor(), fontWeight = FontWeight.Bold, style = MaterialTheme.typography.titleMedium)
                    }
                    Spacer(Modifier.height(8.dp))
                    Text(
                        if (cleanupReview.suggestions.isEmpty()) stringResource(R.string.settings_metadata_review_empty)
                        else stringResource(R.string.settings_metadata_review_count, cleanupReview.suggestions.size),
                        color = settingsSecondaryTextColor(darkAlpha = 0.65f),
                        style = MaterialTheme.typography.bodySmall
                    )
                    cleanupReview.suggestions.take(3).forEach { suggestion ->
                        CleanupSuggestionLabel(
                            trackTitle = cleanupReview.trackTitles[suggestion.ratingKey],
                            issue = suggestion.issue
                        )
                    }
                    Text(
                        stringResource(R.string.settings_library_review_footer),
                        color = settingsSecondaryTextColor(darkAlpha = 0.4f),
                        style = MaterialTheme.typography.bodySmall,
                        modifier = Modifier.padding(top = 8.dp)
                    )
                }
            }

            if (collectionSuggestions.isNotEmpty()) {
                Card(
                    modifier = Modifier.fillMaxWidth().padding(bottom = 24.dp),
                    shape = RoundedCornerShape(24.dp),
                    colors = CardDefaults.cardColors(containerColor = settingsPanelColor(darkAlpha = 0.04f)),
                    border = androidx.compose.foundation.BorderStroke(1.dp, settingsPanelBorderColor(darkAlpha = 0.08f))
                ) {
                    Column(modifier = Modifier.padding(16.dp)) {
                        Text(stringResource(R.string.settings_collection_suggestions_title), color = settingsPrimaryTextColor(), fontWeight = FontWeight.Bold, style = MaterialTheme.typography.titleMedium)
                        Text(stringResource(R.string.settings_collection_suggestions_summary), color = settingsSecondaryTextColor(darkAlpha = 0.6f), style = MaterialTheme.typography.bodySmall, modifier = Modifier.padding(top = 6.dp))
                        collectionSuggestions.take(5).forEach { suggestion ->
                            CollectionSuggestionLabel(suggestion.name, suggestion.ratingKeys.size)
                        }
                        Text(stringResource(R.string.settings_collection_suggestions_footer), color = settingsSecondaryTextColor(darkAlpha = 0.4f), style = MaterialTheme.typography.bodySmall, modifier = Modifier.padding(top = 8.dp))
                    }
                }
            }
        }

        val syncDiagnosticsState = stringResource(
            if (showSyncDiagnostics) R.string.settings_disclosure_expanded else R.string.settings_disclosure_collapsed
        )
        if (syncDiagnosticInfo.lastSyncAttempt > 0 || syncState !is LibrarySyncState.Idle) {
            Card(
                modifier = Modifier.fillMaxWidth().padding(bottom = 24.dp),
                shape = RoundedCornerShape(24.dp),
                colors = CardDefaults.cardColors(
                    containerColor = if (isLightSettingsTheme()) settingsPanelColor() else Color.Black.copy(alpha = 0.4f)
                ),
                border = androidx.compose.foundation.BorderStroke(
                    1.dp,
                    if (isLightSettingsTheme()) settingsPanelBorderColor() else Color(0xFF818CF8).copy(alpha = 0.3f)
                )
            ) {
                Column(modifier = Modifier.padding(16.dp)) {
                    Row(
                        modifier = Modifier.fillMaxWidth()
                            .clickable { showSyncDiagnostics = !showSyncDiagnostics }
                            .semantics {
                                role = Role.Button
                                stateDescription = syncDiagnosticsState
                            }
                            .testTag("settings_sync_diagnostics_disclosure"),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Icon(Icons.Rounded.BugReport, contentDescription = null, tint = settingsAccentColor())
                        Spacer(Modifier.width(12.dp))
                        Text(stringResource(R.string.settings_sync_diagnostics_title), color = settingsPrimaryTextColor(), fontWeight = FontWeight.Bold, style = MaterialTheme.typography.titleMedium, modifier = Modifier.weight(1f))
                        Icon(
                            imageVector = if (showSyncDiagnostics) Icons.Rounded.ExpandLess else Icons.Rounded.ExpandMore,
                            contentDescription = null,
                            tint = settingsSecondaryTextColor(darkAlpha = 0.75f)
                        )
                    }
                    if (showSyncDiagnostics) {
                        Spacer(Modifier.height(12.dp))
                        DiagnosticRow(
                            stringResource(R.string.settings_sync_diagnostic_status),
                            syncDiagnosticInfo.lastSyncStatus,
                            true
                        )
                        DiagnosticRow(
                            stringResource(R.string.settings_sync_diagnostic_total_processed),
                            pluralStringResource(
                                R.plurals.settings_sync_diagnostic_tracks_processed,
                                syncDiagnosticInfo.totalProcessed,
                                syncDiagnosticInfo.totalProcessed
                            ),
                            true
                        )
                        DiagnosticRow(
                            stringResource(R.string.settings_sync_diagnostic_last_page_items),
                            syncDiagnosticInfo.itemsInLastPage.toString(),
                            true
                        )
                        DiagnosticRow(
                            stringResource(R.string.settings_sync_diagnostic_current_page),
                            syncDiagnosticInfo.currentPage.toString(),
                            true
                        )
                        syncDiagnosticInfo.lastError?.let {
                            Text(
                                stringResource(R.string.settings_sync_diagnostic_last_error, it),
                                color = if (isLightSettingsTheme()) MaterialTheme.colorScheme.error else Color(0xFFFCA5A5),
                                style = MaterialTheme.typography.bodySmall,
                                modifier = Modifier.padding(top = 8.dp)
                            )
                        }
                        Spacer(Modifier.height(12.dp))
                        Text(
                            stringResource(R.string.settings_sync_diagnostic_log_title),
                            color = settingsPrimaryTextColor(),
                            style = MaterialTheme.typography.labelMedium
                        )
                        Box(
                            modifier = Modifier.fillMaxWidth().height(150.dp).padding(top = 4.dp)
                                .background(if (isLightSettingsTheme()) MaterialTheme.colorScheme.surfaceVariant else Color.Black.copy(alpha = 0.5f))
                                .padding(8.dp)
                        ) {
                            androidx.compose.foundation.lazy.LazyColumn {
                                items(syncDiagnosticInfo.log) { logEntry ->
                                    Text(logEntry, color = settingsSecondaryTextColor(darkAlpha = 0.7f), style = MaterialTheme.typography.labelSmall)
                                }
                            }
                        }
                    }
                }
            }
        }

        if (BuildConfig.DEBUG && onOpenQaLab != null) {
            QaLabSettingsAction(onClick = onOpenQaLab)
        }
    }

}

@Composable
internal fun SettingsLibraryActions(
    syncEnabled: Boolean,
    syncIdle: Boolean,
    serverRefreshInProgress: Boolean = false,
    onRefreshServer: () -> Unit,
    onSyncLibrary: () -> Unit
) {
    BoxWithConstraints(modifier = Modifier.fillMaxWidth()) {
        val actionsEnabled = syncEnabled && !serverRefreshInProgress
        if (maxWidth < 520.dp) {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                SettingsRefreshSpotCoreButton(
                    modifier = Modifier.fillMaxWidth(),
                    enabled = actionsEnabled,
                    serverRefreshInProgress = serverRefreshInProgress,
                    onClick = onRefreshServer
                )
                SettingsUpdatePhoneLibraryButton(
                    modifier = Modifier.fillMaxWidth(),
                    enabled = actionsEnabled,
                    syncIdle = syncIdle,
                    onClick = onSyncLibrary
                )
            }
        } else {
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                SettingsRefreshSpotCoreButton(
                    modifier = Modifier.weight(1f),
                    enabled = actionsEnabled,
                    serverRefreshInProgress = serverRefreshInProgress,
                    onClick = onRefreshServer
                )
                SettingsUpdatePhoneLibraryButton(
                    modifier = Modifier.weight(1f),
                    enabled = actionsEnabled,
                    syncIdle = syncIdle,
                    onClick = onSyncLibrary
                )
            }
        }
    }
}

@Composable
private fun SettingsRefreshSpotCoreButton(
    modifier: Modifier,
    enabled: Boolean,
    serverRefreshInProgress: Boolean,
    onClick: () -> Unit
) {
    OutlinedButton(
        onClick = onClick,
        enabled = enabled,
        modifier = modifier
            .defaultMinSize(minHeight = 48.dp)
            .testTag("refresh_server_library_button"),
        shape = RoundedCornerShape(16.dp),
        colors = ButtonDefaults.outlinedButtonColors(contentColor = settingsAccentColor(Color(0xFF22D3EE)))
    ) {
        if (serverRefreshInProgress) {
            CircularProgressIndicator(
                modifier = Modifier.size(18.dp),
                strokeWidth = 2.dp,
                color = settingsAccentColor(Color(0xFF22D3EE))
            )
        } else {
            Icon(Icons.Rounded.Refresh, contentDescription = null, modifier = Modifier.size(18.dp))
        }
        Spacer(modifier = Modifier.width(8.dp))
        Text(
            stringResource(
                if (serverRefreshInProgress) R.string.settings_refreshing_spotcore_library
                else R.string.settings_scan_spotcore_library
            )
        )
    }
}

@Composable
private fun SettingsUpdatePhoneLibraryButton(
    modifier: Modifier,
    enabled: Boolean,
    syncIdle: Boolean,
    onClick: () -> Unit
) {
    OutlinedButton(
        onClick = onClick,
        enabled = enabled,
        modifier = modifier
            .defaultMinSize(minHeight = 48.dp)
            .testTag("update_phone_library_button"),
        shape = RoundedCornerShape(16.dp),
        colors = ButtonDefaults.outlinedButtonColors(contentColor = settingsAccentColor())
    ) {
        Icon(
            imageVector = if (syncIdle) Icons.Rounded.Sync else Icons.Rounded.SyncProblem,
            contentDescription = null,
            modifier = Modifier.size(18.dp)
        )
        Spacer(modifier = Modifier.width(8.dp))
        Text(stringResource(R.string.settings_update_phone_index))
    }
}

@Composable
internal fun SettingsLibraryDetailsCard(
    syncState: LibrarySyncState,
    cachedCount: Int,
    onSync: () -> Unit,
    modifier: Modifier = Modifier
) {
    val largeTextScale = LocalDensity.current.fontScale >= 1.15f
    val trackCountCopy = when (syncState) {
        LibrarySyncState.Connecting -> stringResource(R.string.setup_library_connecting)
        is LibrarySyncState.Fetching -> stringResource(R.string.setup_library_fetching)
        is LibrarySyncState.Processing -> stringResource(R.string.setup_library_processing)
        is LibrarySyncState.Saving -> stringResource(R.string.setup_library_saving)
        is LibrarySyncState.Complete -> pluralStringResource(
            R.plurals.settings_synced_tracks,
            maxOf(syncState.indexedCount, cachedCount),
            maxOf(syncState.indexedCount, cachedCount)
        )
        is LibrarySyncState.Failed -> stringResource(R.string.setup_library_failed)
        else -> pluralStringResource(R.plurals.settings_synced_tracks, cachedCount, cachedCount)
    }
    Card(
        modifier = modifier.fillMaxWidth().padding(bottom = 24.dp),
        shape = RoundedCornerShape(24.dp),
        colors = CardDefaults.cardColors(containerColor = settingsPanelColor()),
        border = androidx.compose.foundation.BorderStroke(1.dp, settingsPanelBorderColor()),
        elevation = CardDefaults.cardElevation(defaultElevation = 0.dp)
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(Icons.Rounded.AutoAwesome, contentDescription = null, tint = settingsAccentColor())
                Spacer(modifier = Modifier.width(12.dp))
                Text(
                    text = stringResource(R.string.settings_library_details_heading),
                    style = MaterialTheme.typography.titleMedium.copy(
                        fontWeight = FontWeight.Bold,
                        color = settingsPrimaryTextColor()
                    )
                )
            }

            Spacer(modifier = Modifier.height(8.dp))
            Text(
                text = stringResource(R.string.settings_library_details_body),
                color = settingsSecondaryTextColor(darkAlpha = 0.6f),
                style = MaterialTheme.typography.bodySmall
            )
            Spacer(modifier = Modifier.height(16.dp))

            if (largeTextScale) {
                Column(
                    modifier = Modifier.fillMaxWidth(),
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Column(
                        modifier = Modifier.fillMaxWidth(),
                        verticalArrangement = Arrangement.spacedBy(2.dp)
                    ) {
                        Text(
                            text = stringResource(R.string.settings_library_details_tracks),
                            modifier = Modifier.fillMaxWidth(),
                            color = settingsPrimaryTextColor(),
                            fontWeight = FontWeight.Bold,
                            fontSize = 15.sp
                        )
                        Text(
                            text = trackCountCopy,
                            modifier = Modifier.fillMaxWidth(),
                            color = settingsAccentColor(),
                            fontSize = 13.sp,
                            fontWeight = FontWeight.Medium
                        )
                    }
                    LibraryIndexSyncButton(
                        syncState = syncState,
                        cachedCount = cachedCount,
                        onSync = onSync,
                        modifier = Modifier.align(Alignment.Start)
                    )
                }
            } else {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            stringResource(R.string.settings_library_details_tracks),
                            color = settingsPrimaryTextColor(),
                            fontWeight = FontWeight.Bold,
                            fontSize = 15.sp
                        )
                        Text(
                            text = trackCountCopy,
                            color = settingsAccentColor(),
                            fontSize = 13.sp,
                            fontWeight = FontWeight.Medium
                        )
                    }
                    LibraryIndexSyncButton(
                        syncState = syncState,
                        cachedCount = cachedCount,
                        onSync = onSync
                    )
                }
            }
        }
    }
}

@Composable
internal fun SettingsLibraryOption(
    title: String,
    selected: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    Row(
        modifier = modifier
            .fillMaxWidth()
            .padding(vertical = 4.dp)
            .clip(RoundedCornerShape(12.dp))
                .background(if (selected) settingsAccentColor(Color(0xFF6366F1)).copy(alpha = 0.15f) else Color.Transparent)
            .selectable(selected = selected, role = Role.RadioButton, onClick = onClick)
            .padding(12.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.SpaceBetween
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Icon(
                imageVector = Icons.Rounded.MusicNote,
                contentDescription = null,
                tint = if (selected) settingsAccentColor() else settingsSecondaryTextColor(darkAlpha = 0.5f)
            )
            Spacer(modifier = Modifier.width(12.dp))
            Text(
                text = title,
                style = MaterialTheme.typography.bodyLarge.copy(
                    fontWeight = if (selected) FontWeight.Bold else FontWeight.Normal,
                    color = if (selected) settingsAccentColor() else settingsPrimaryTextColor()
                )
            )
        }
        if (selected) {
            Icon(
                imageVector = Icons.Rounded.CheckCircle,
                contentDescription = null,
                tint = settingsAccentColor()
            )
        }
    }
}

internal fun spotcoreConnectionSummaryString(
    isPaired: Boolean,
    status: CompanionConnectionStatus
): Int = when {
    !isPaired || status == CompanionConnectionStatus.NOT_PAIRED -> R.string.settings_spotcore_pairing_summary
    else -> when (status) {
        CompanionConnectionStatus.NOT_PAIRED -> R.string.settings_spotcore_pairing_summary
        CompanionConnectionStatus.CHECKING -> R.string.settings_spotcore_checking_summary
        CompanionConnectionStatus.CONNECTED -> R.string.settings_spotcore_connected_summary
        CompanionConnectionStatus.TEMPORARILY_UNAVAILABLE -> R.string.settings_spotcore_unavailable_summary
        CompanionConnectionStatus.OFFLINE_LIBRARY_AVAILABLE -> R.string.settings_spotcore_offline_summary
    }
}

@Composable
private fun SettingsGroupHeading(title: String, modifier: Modifier = Modifier) {
    Text(
        text = title,
        style = MaterialTheme.typography.titleSmall.copy(
            color = MaterialTheme.colorScheme.primary,
            fontWeight = FontWeight.Bold,
            letterSpacing = 0.3.sp
        ),
        modifier = modifier.padding(start = 4.dp, top = 4.dp, bottom = 8.dp)
    )
}

private data class SettingsThemeOption(val key: String, val labelRes: Int)

private val settingsThemeOptions = listOf(
    SettingsThemeOption("Default Dark", R.string.settings_theme_default_dark),
    SettingsThemeOption("Emerald Aurora", R.string.settings_theme_emerald_aurora),
    SettingsThemeOption("Cyberpunk Neon", R.string.settings_theme_cyberpunk_neon),
    SettingsThemeOption("Sunset Glow", R.string.settings_theme_sunset_glow),
    SettingsThemeOption("Cosmic Obsidian", R.string.settings_theme_cosmic_obsidian),
    SettingsThemeOption("Midnight Blue", R.string.settings_theme_midnight_blue),
    SettingsThemeOption("Deep Amber", R.string.settings_theme_deep_amber),
    SettingsThemeOption("Rose Gold", R.string.settings_theme_rose_gold),
    SettingsThemeOption("Matrix Green", R.string.settings_theme_matrix_green),
    SettingsThemeOption("Nordic Frost", R.string.settings_theme_nordic_frost),
    SettingsThemeOption("Solarized Dark", R.string.settings_theme_solarized_dark),
    SettingsThemeOption("Warm Light", R.string.settings_theme_warm_light)
)

@Composable
internal fun SettingsAppearanceSection(
    activeTheme: String,
    onThemeSelected: (String) -> Unit,
    modifier: Modifier = Modifier
) {
    val currentThemeOption = settingsThemeOptions.firstOrNull { it.key == activeTheme }
        ?: settingsThemeOptions.first()
    val isWarmLight = currentThemeOption.key == "Warm Light"
    SettingsGroupHeading(
        title = stringResource(R.string.settings_group_appearance),
        modifier = modifier
            .testTag("settings_heading_appearance")
    )
    Card(
        modifier = Modifier.fillMaxWidth().padding(bottom = 24.dp),
        shape = RoundedCornerShape(24.dp),
        colors = CardDefaults.cardColors(
            containerColor = if (isWarmLight) MaterialTheme.colorScheme.surface else Color.White.copy(alpha = 0.05f)
        ),
        border = androidx.compose.foundation.BorderStroke(
            1.dp,
            if (isWarmLight) MaterialTheme.colorScheme.outline.copy(alpha = 0.2f) else Color.White.copy(alpha = 0.10f)
        ),
        elevation = CardDefaults.cardElevation(defaultElevation = 0.dp)
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(Icons.Rounded.Palette, contentDescription = null, tint = MaterialTheme.colorScheme.primary)
                Spacer(Modifier.width(12.dp))
                Text(
                    text = stringResource(R.string.settings_theme_title),
                    style = MaterialTheme.typography.titleMedium.copy(
                        fontWeight = FontWeight.Bold,
                        color = if (isWarmLight) MaterialTheme.colorScheme.onSurface else Color.White
                    )
                )
            }
            Spacer(Modifier.height(14.dp))
            var expanded by remember { mutableStateOf(false) }
            Box(Modifier.fillMaxWidth()) {
                Row(
                    modifier = Modifier.fillMaxWidth()
                        .clip(RoundedCornerShape(12.dp))
                        .background(
                            if (isWarmLight) MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f)
                            else Color.White.copy(alpha = 0.08f)
                        )
                        .clickable { expanded = true }
                        .padding(horizontal = 16.dp, vertical = 14.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Column {
                        Text(
                            stringResource(R.string.settings_theme_current),
                            fontSize = 11.sp,
                            color = (if (isWarmLight) MaterialTheme.colorScheme.onSurface else Color.White).copy(alpha = 0.6f)
                        )
                        Text(
                            text = stringResource(currentThemeOption.labelRes),
                            fontSize = 16.sp,
                            fontWeight = FontWeight.Bold,
                            color = if (isWarmLight) MaterialTheme.colorScheme.onSurface else Color.White
                        )
                    }
                    Icon(
                        imageVector = if (expanded) Icons.Rounded.KeyboardArrowUp else Icons.Rounded.KeyboardArrowDown,
                        contentDescription = stringResource(R.string.settings_choose_theme),
                        tint = if (isWarmLight) MaterialTheme.colorScheme.primary else Color.White
                    )
                }
                DropdownMenu(
                    expanded = expanded,
                    onDismissRequest = { expanded = false },
                    modifier = Modifier.fillMaxWidth(0.9f)
                        .background(if (isWarmLight) MaterialTheme.colorScheme.surface else Color(0xFF1E1E2E))
                ) {
                    settingsThemeOptions.forEach { themeOption ->
                        val isSelected = themeOption.key == currentThemeOption.key
                        DropdownMenuItem(
                            text = {
                                Text(
                                    text = stringResource(themeOption.labelRes),
                                    fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Normal,
                                    color = if (isSelected) MaterialTheme.colorScheme.primary
                                    else if (isWarmLight) MaterialTheme.colorScheme.onSurface else Color.White
                                )
                            },
                            onClick = {
                                onThemeSelected(themeOption.key)
                                expanded = false
                            },
                            leadingIcon = {
                                Icon(
                                    Icons.Rounded.Palette,
                                    contentDescription = null,
                                    tint = if (isSelected) MaterialTheme.colorScheme.primary else Color.White.copy(alpha = 0.4f),
                                    modifier = Modifier.size(18.dp)
                                )
                            },
                            modifier = Modifier.background(
                                if (isSelected) MaterialTheme.colorScheme.primary.copy(alpha = 0.1f) else Color.Transparent
                            )
                        )
                    }
                }
            }
        }
    }
}

@Composable
internal fun BackgroundLibraryUpdatesCard(
    intervalHours: Long,
    onIntervalSelected: (Long) -> Unit
) {
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .padding(bottom = 24.dp),
        shape = RoundedCornerShape(24.dp),
        colors = CardDefaults.cardColors(containerColor = settingsPanelColor()),
        border = androidx.compose.foundation.BorderStroke(1.dp, settingsPanelBorderColor()),
        elevation = CardDefaults.cardElevation(defaultElevation = 0.dp)
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(
                    imageVector = Icons.Rounded.Sync,
                    contentDescription = null,
                    tint = settingsAccentColor()
                )
                Spacer(modifier = Modifier.width(12.dp))
                Text(
                    text = stringResource(R.string.settings_library_updates_title),
                    style = MaterialTheme.typography.titleMedium.copy(
                        fontWeight = FontWeight.Bold,
                        color = settingsPrimaryTextColor()
                    )
                )
            }

            Spacer(modifier = Modifier.height(8.dp))
            Text(
                text = stringResource(R.string.settings_library_updates_body),
                color = settingsSecondaryTextColor(darkAlpha = 0.6f),
                style = MaterialTheme.typography.bodySmall
            )

            Spacer(modifier = Modifier.height(12.dp))

            Column(modifier = Modifier.selectableGroup()) {
                listOf(0L, 1L, 6L, 12L, 24L).forEach { hours ->
                    val isSelected = hours == intervalHours
                    val label = if (hours == 0L) {
                        stringResource(R.string.settings_library_updates_off)
                    } else {
                        pluralStringResource(R.plurals.settings_library_update_interval, hours.toInt(), hours.toInt())
                    }
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(vertical = 4.dp)
                            .clip(RoundedCornerShape(12.dp))
                            .background(if (isSelected) settingsAccentColor(Color(0xFF6366F1)).copy(alpha = 0.15f) else Color.Transparent)
                            .selectable(
                                selected = isSelected,
                                role = Role.RadioButton,
                                onClick = { onIntervalSelected(hours) }
                            )
                            .padding(12.dp)
                            .testTag("settings_library_update_interval_$hours"),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Text(
                            text = label,
                            style = MaterialTheme.typography.bodyLarge.copy(
                                fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Normal,
                                color = if (isSelected) settingsAccentColor() else settingsPrimaryTextColor()
                            )
                        )
                        RadioButton(
                            selected = isSelected,
                            onClick = null,
                            colors = RadioButtonDefaults.colors(selectedColor = settingsAccentColor())
                        )
                    }
                }
            }
        }
    }
}

@Composable
internal fun SettingsLastFmSharingHint(
    lastFmEnabled: Boolean,
    hasConnectedAccount: Boolean
) {
    val message = lastFmSharingHintResource(lastFmEnabled, hasConnectedAccount) ?: return
    Text(
        text = stringResource(message),
        style = MaterialTheme.typography.bodySmall,
        color = settingsSecondaryTextColor(darkAlpha = 0.65f),
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 4.dp)
            .testTag("lastfm_sharing_hint")
    )
}

internal fun lastFmSharingHintResource(lastFmEnabled: Boolean, hasConnectedAccount: Boolean): Int? = when {
    lastFmEnabled -> null
    hasConnectedAccount -> R.string.settings_lastfm_sharing_paused
    else -> R.string.settings_lastfm_preferences_inactive
}

@Composable
@OptIn(ExperimentalLayoutApi::class)
internal fun SettingsLastFmAuthorizationActions(
    hasConnectedAccount: Boolean,
    authorizationPending: Boolean,
    onSaveCredentials: () -> Unit,
    onStartAuthorization: () -> Unit,
    onCompleteAuthorization: () -> Unit,
    onDisconnect: () -> Unit
) {
    FlowRow(
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp),
        modifier = Modifier.fillMaxWidth().padding(top = 10.dp)
    ) {
        Button(onClick = onSaveCredentials) {
            Text(stringResource(R.string.settings_lastfm_save_credentials))
        }
        when {
            hasConnectedAccount -> OutlinedButton(onClick = onDisconnect) {
                Text(stringResource(R.string.settings_lastfm_disconnect))
            }
            authorizationPending -> OutlinedButton(onClick = onCompleteAuthorization) {
                Text(stringResource(R.string.settings_lastfm_complete_authorization))
            }
            else -> OutlinedButton(onClick = onStartAuthorization) {
                Text(stringResource(R.string.settings_lastfm_start_authorization))
            }
        }
    }
}

@Composable
internal fun LastFmAuthorizationStatusText(message: String, isError: Boolean) {
    if (message.isNotBlank()) {
        Text(
            text = message,
            color = if (isError) MaterialTheme.colorScheme.error
            else if (isLightSettingsTheme()) Color(0xFF166534) else Color(0xFF6EE7B7),
            fontSize = 12.sp,
            modifier = Modifier
                .testTag("lastfm_authorization_status")
                .semantics {
                    liveRegion = androidx.compose.ui.semantics.LiveRegionMode.Polite
                    stateDescription = if (isError) "Error" else "Status"
                    if (isError) error(message)
                }
        )
    }
}

@Composable
@OptIn(ExperimentalLayoutApi::class)
internal fun FirstRunSetupCard(
    syncState: LibrarySyncState,
    cachedCount: Int,
    syncEnqueueError: String? = null,
    companionConnectionStatus: CompanionConnectionStatus = CompanionConnectionStatus.CHECKING,
    onRetry: () -> Unit,
    onFinish: () -> Unit,
    onDismiss: () -> Unit
) {
    val indexedCount = when (syncState) {
        is LibrarySyncState.Complete -> maxOf(syncState.indexedCount, cachedCount)
        else -> cachedCount
    }
    val isWorking = syncState is LibrarySyncState.Connecting ||
        syncState is LibrarySyncState.Fetching ||
        syncState is LibrarySyncState.Processing ||
        syncState is LibrarySyncState.Saving
    val syncFinished = syncState is LibrarySyncState.Complete
    val isComplete = syncFinished && indexedCount > 0
    val isEmptyLibrary = syncFinished && indexedCount == 0
    val isFailed = syncState is LibrarySyncState.Failed
    val statusMessage = when (syncState) {
        LibrarySyncState.Idle -> stringResource(R.string.setup_library_waiting)
        LibrarySyncState.Connecting -> stringResource(R.string.setup_library_connecting)
        is LibrarySyncState.Fetching -> stringResource(R.string.setup_library_fetching)
        is LibrarySyncState.Processing -> stringResource(R.string.setup_library_processing)
        is LibrarySyncState.Saving -> stringResource(R.string.setup_library_saving)
        is LibrarySyncState.Complete -> if (indexedCount > 0) {
            stringResource(R.string.setup_library_ready, indexedCount)
        } else {
            stringResource(R.string.setup_library_ready_empty)
        }
        is LibrarySyncState.Failed -> stringResource(
            when (companionConnectionStatus) {
                CompanionConnectionStatus.CONNECTED -> R.string.setup_library_failed_connected
                CompanionConnectionStatus.TEMPORARILY_UNAVAILABLE,
                CompanionConnectionStatus.OFFLINE_LIBRARY_AVAILABLE -> R.string.setup_library_failed_unavailable
                CompanionConnectionStatus.NOT_PAIRED,
                CompanionConnectionStatus.CHECKING -> R.string.setup_library_failed
            }
        )
    }

    Card(
        modifier = Modifier.fillMaxWidth().padding(bottom = 20.dp).testTag("first_run_setup_card"),
        shape = RoundedCornerShape(22.dp),
        colors = CardDefaults.cardColors(
            containerColor = if (isLightSettingsTheme()) settingsPanelColor() else Color(0xFF3730A3).copy(alpha = 0.24f)
        ),
        border = androidx.compose.foundation.BorderStroke(
            1.dp,
            if (isLightSettingsTheme()) settingsPanelBorderColor() else MaterialTheme.colorScheme.primary.copy(alpha = 0.35f)
        )
    ) {
        Column(modifier = Modifier.padding(18.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                Icon(
                    imageVector = if (isComplete) Icons.Rounded.CheckCircle else Icons.Rounded.LibraryMusic,
                    contentDescription = null,
                    tint = if (isComplete && isLightSettingsTheme()) Color(0xFF166534)
                    else if (isComplete) Color(0xFF6EE7B7) else settingsAccentColor()
                )
                Text(
            text = if (isComplete) stringResource(R.string.setup_library_ready_title) else stringResource(R.string.setup_library_title),
                    style = MaterialTheme.typography.titleMedium,
                    color = settingsPrimaryTextColor(),
                    fontWeight = FontWeight.Bold
                )
            }
            Text(
                statusMessage,
                color = settingsSecondaryTextColor(darkAlpha = 0.78f),
                style = MaterialTheme.typography.bodyMedium,
                modifier = Modifier
                    .testTag("first_run_setup_status")
                    .semantics { liveRegion = androidx.compose.ui.semantics.LiveRegionMode.Polite }
            )

            syncEnqueueError?.let { message ->
                Text(
                    text = message,
                    color = MaterialTheme.colorScheme.error,
                    style = MaterialTheme.typography.bodySmall,
                    modifier = Modifier.testTag("first_run_sync_enqueue_error")
                        .semantics {
                            liveRegion = androidx.compose.ui.semantics.LiveRegionMode.Polite
                            error(message)
                        }
                )
            }

            if (isComplete) {
                Text(
                    text = stringResource(R.string.setup_library_ready_next_step),
                    color = settingsSecondaryTextColor(darkAlpha = 0.72f),
                    style = MaterialTheme.typography.bodySmall,
                    modifier = Modifier.testTag("first_run_setup_next_step")
                )
            }

            if (isWorking) {
                LinearProgressIndicator(modifier = Modifier.fillMaxWidth())
            }
            if (indexedCount > 0 && !isComplete) {
                Text(
                    text = pluralStringResource(R.plurals.setup_library_cached_tracks, indexedCount, indexedCount),
                    color = settingsSecondaryTextColor(darkAlpha = 0.62f),
                    style = MaterialTheme.typography.bodySmall
                )
            }

            FlowRow(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                when {
                    syncState == LibrarySyncState.Idle -> Button(
                        onClick = onRetry,
                        modifier = Modifier.testTag(if (syncEnqueueError == null) "first_run_start_index" else "first_run_retry_index")
                    ) {
                        Icon(Icons.Rounded.Sync, contentDescription = null)
                        Spacer(Modifier.width(6.dp))
                        Text(stringResource(if (syncEnqueueError == null) R.string.setup_start_index else R.string.setup_retry_index))
                    }
                    isFailed -> Button(onClick = onRetry, modifier = Modifier.testTag("first_run_retry_index")) {
                        Icon(Icons.Rounded.Refresh, contentDescription = null)
                        Spacer(Modifier.width(6.dp))
                        Text(stringResource(R.string.setup_retry_index))
                    }
                    isEmptyLibrary -> Button(onClick = onRetry, modifier = Modifier.testTag("first_run_check_library_again")) {
                        Icon(Icons.Rounded.Refresh, contentDescription = null)
                        Spacer(Modifier.width(6.dp))
                        Text(stringResource(R.string.setup_check_library_again))
                    }
                    else -> Button(onClick = onFinish, modifier = Modifier.testTag("first_run_continue_home")) {
                        Text(if (isComplete) stringResource(R.string.setup_open_home) else stringResource(R.string.setup_continue_background))
                    }
                }
                if (isFailed || isEmptyLibrary || syncState == LibrarySyncState.Idle) {
                    TextButton(onClick = onDismiss) { Text(stringResource(R.string.setup_not_now)) }
                }
            }
        }
    }
}

@Composable
internal fun LibraryIndexSyncButton(
    syncState: LibrarySyncState,
    cachedCount: Int,
    onSync: () -> Unit,
    modifier: Modifier = Modifier
) {
    val isSyncing = syncState is LibrarySyncState.Connecting ||
        syncState is LibrarySyncState.Fetching ||
        syncState is LibrarySyncState.Processing ||
        syncState is LibrarySyncState.Saving
    Button(
        onClick = onSync,
        enabled = syncState is LibrarySyncState.Idle ||
            syncState is LibrarySyncState.Complete ||
            syncState is LibrarySyncState.Failed,
        colors = ButtonDefaults.buttonColors(
            containerColor = if (cachedCount > 0) settingsPanelColor(darkAlpha = 0.1f) else settingsAccentColor(Color(0xFF8B5CF6)),
            contentColor = if (cachedCount > 0) settingsPrimaryTextColor() else MaterialTheme.colorScheme.onPrimary
        ),
        shape = RoundedCornerShape(10.dp),
        modifier = modifier.defaultMinSize(minHeight = 48.dp).testTag("sync_index_button")
    ) {
        if (isSyncing) {
            CircularProgressIndicator(
                color = if (cachedCount > 0) settingsPrimaryTextColor() else MaterialTheme.colorScheme.onPrimary,
                modifier = Modifier.size(18.dp),
                strokeWidth = 2.dp
            )
            Spacer(modifier = Modifier.width(8.dp))
            Text(stringResource(R.string.settings_library_update_in_progress), fontSize = 13.sp)
        } else {
            Icon(imageVector = Icons.Rounded.Sync, contentDescription = null, modifier = Modifier.size(16.dp))
            Spacer(modifier = Modifier.width(6.dp))
            val labelRes = when {
                syncState is LibrarySyncState.Failed -> R.string.setup_retry_index
                cachedCount > 0 -> R.string.settings_update_phone_index
                else -> R.string.setup_start_index
            }
            Text(stringResource(labelRes), fontSize = 13.sp, fontWeight = FontWeight.Bold)
        }
    }
}

@Composable
internal fun DiagnosticRow(label: String, value: String, healthy: Boolean) {
    val modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp)
    val labelColor = settingsSecondaryTextColor(darkAlpha = 0.65f)
    val valueColor = if (healthy) {
        if (isLightSettingsTheme()) Color(0xFF166534) else Color(0xFF6EE7B7)
    } else {
        if (isLightSettingsTheme()) settingsSecondaryTextColor(darkAlpha = 0.65f) else Color(0xFFFCA5A5)
    }
    if (LocalDensity.current.fontScale >= 1.15f) {
        Column(modifier = modifier, verticalArrangement = Arrangement.spacedBy(2.dp)) {
            Text(label, color = labelColor, style = MaterialTheme.typography.bodySmall)
            Text(value, color = valueColor, style = MaterialTheme.typography.bodySmall, fontWeight = FontWeight.SemiBold)
        }
    } else {
        Row(modifier = modifier, horizontalArrangement = Arrangement.SpaceBetween) {
            Text(label, color = labelColor, style = MaterialTheme.typography.bodySmall)
            Text(value, color = valueColor, style = MaterialTheme.typography.bodySmall, fontWeight = FontWeight.SemiBold)
        }
    }
}

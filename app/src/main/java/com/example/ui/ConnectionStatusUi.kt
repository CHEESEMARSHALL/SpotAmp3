package com.example.ui

import androidx.annotation.StringRes
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.defaultMinSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Refresh
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.example.data.CompanionConnectionStatus
import com.example.R

/** Shared status wording for Home, Settings, and recovery notices on other routes. */
@StringRes
internal fun connectionStatusLabelResource(status: CompanionConnectionStatus): Int = when (status) {
    CompanionConnectionStatus.NOT_PAIRED -> R.string.connection_not_paired
    CompanionConnectionStatus.CHECKING -> R.string.connection_checking
    CompanionConnectionStatus.CONNECTED -> R.string.connection_available
    CompanionConnectionStatus.TEMPORARILY_UNAVAILABLE -> R.string.connection_unavailable
    CompanionConnectionStatus.OFFLINE_LIBRARY_AVAILABLE -> R.string.connection_offline_library
}

internal fun shouldShowCompanionConnectionNotice(
    companionConfigured: Boolean,
    status: CompanionConnectionStatus
): Boolean = companionConfigured && (
    status == CompanionConnectionStatus.CHECKING ||
        status == CompanionConnectionStatus.TEMPORARILY_UNAVAILABLE ||
        status == CompanionConnectionStatus.OFFLINE_LIBRARY_AVAILABLE
    )

internal fun shouldShowNowPlayingConnectionRecovery(
    playbackFailed: Boolean,
    companionConfigured: Boolean,
    status: CompanionConnectionStatus
): Boolean = playbackFailed && shouldShowCompanionConnectionNotice(companionConfigured, status)

@StringRes
internal fun connectionStatusNoticeBodyResource(
    status: CompanionConnectionStatus,
    @StringRes offlineBodyResource: Int,
    @StringRes unavailableBodyResource: Int
): Int? = when (status) {
    CompanionConnectionStatus.CHECKING -> R.string.connection_checking_body
    CompanionConnectionStatus.TEMPORARILY_UNAVAILABLE -> unavailableBodyResource
    CompanionConnectionStatus.OFFLINE_LIBRARY_AVAILABLE -> offlineBodyResource
    CompanionConnectionStatus.NOT_PAIRED,
    CompanionConnectionStatus.CONNECTED -> null
}

@Composable
internal fun CompanionDetailConnectionNotice(
    companionConfigured: Boolean,
    status: CompanionConnectionStatus,
    onCheckConnection: () -> Unit,
    testTag: String
) {
    val bodyResource = if (shouldShowCompanionConnectionNotice(companionConfigured, status)) {
        connectionStatusNoticeBodyResource(
            status = status,
            offlineBodyResource = R.string.connection_offline_body,
            unavailableBodyResource = R.string.connection_unavailable_body
        )
    } else null

    if (bodyResource != null) {
        CompanionConnectionNotice(
            status = status,
            bodyResource = bodyResource,
            onCheckConnection = onCheckConnection,
            modifier = Modifier.padding(bottom = 12.dp),
            testTag = testTag
        )
    }
}

@Composable
internal fun CompanionConnectionNotice(
    status: CompanionConnectionStatus,
    @StringRes bodyResource: Int,
    onCheckConnection: () -> Unit,
    modifier: Modifier = Modifier,
    testTag: String
) {
    val isChecking = status == CompanionConnectionStatus.CHECKING
    val accentColor = when (status) {
        CompanionConnectionStatus.OFFLINE_LIBRARY_AVAILABLE -> Color(0xFFFFD180)
        CompanionConnectionStatus.TEMPORARILY_UNAVAILABLE -> MaterialTheme.colorScheme.error
        else -> MaterialTheme.colorScheme.primary
    }
    val containerColor = when (status) {
        CompanionConnectionStatus.OFFLINE_LIBRARY_AVAILABLE -> Color(0xFF2B2416)
        CompanionConnectionStatus.TEMPORARILY_UNAVAILABLE -> MaterialTheme.colorScheme.error.copy(alpha = 0.10f)
        else -> MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.22f)
    }

    Card(
        modifier = modifier
            .fillMaxWidth()
            .semantics { liveRegion = LiveRegionMode.Polite }
            .testTag(testTag),
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(containerColor = containerColor),
        border = BorderStroke(1.dp, accentColor.copy(alpha = 0.28f))
    ) {
        Column(
            modifier = Modifier.fillMaxWidth().padding(12.dp),
            verticalArrangement = Arrangement.spacedBy(4.dp)
        ) {
            Text(
                text = stringResource(connectionStatusLabelResource(status)),
                style = MaterialTheme.typography.titleSmall,
                color = accentColor,
                fontWeight = FontWeight.SemiBold
            )
            Text(
                text = stringResource(bodyResource),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.86f)
            )
            TextButton(
                onClick = onCheckConnection,
                enabled = !isChecking,
                modifier = Modifier
                    .align(Alignment.End)
                    .defaultMinSize(minHeight = 48.dp)
                    .testTag("${testTag}_check_connection")
            ) {
                if (isChecking) {
                    CircularProgressIndicator(modifier = Modifier.size(16.dp), strokeWidth = 2.dp)
                } else {
                    Icon(Icons.Rounded.Refresh, contentDescription = null, modifier = Modifier.size(16.dp))
                }
                Spacer(Modifier.size(4.dp))
                Text(
                    stringResource(
                        if (isChecking) R.string.settings_checking_connection
                        else R.string.settings_check_connection
                    )
                )
            }
        }
    }
}

@Composable
internal fun DetailLoadFailureNotice(
    message: String,
    isRetrying: Boolean,
    onRetry: () -> Unit,
    testTag: String
) {
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .semantics { liveRegion = LiveRegionMode.Polite }
            .testTag(testTag),
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.error.copy(alpha = 0.10f)
        ),
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.error.copy(alpha = 0.28f))
    ) {
        Column(
            modifier = Modifier.fillMaxWidth().padding(12.dp),
            verticalArrangement = Arrangement.spacedBy(4.dp)
        ) {
            Text(
                text = message,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.9f)
            )
            TextButton(
                onClick = onRetry,
                enabled = !isRetrying,
                modifier = Modifier
                    .align(Alignment.End)
                    .defaultMinSize(minHeight = 48.dp)
                    .testTag("${testTag}_retry")
            ) {
                if (isRetrying) {
                    CircularProgressIndicator(modifier = Modifier.size(16.dp), strokeWidth = 2.dp)
                    Spacer(Modifier.size(8.dp))
                }
                Text(stringResource(if (isRetrying) R.string.home_retrying else R.string.home_retry))
            }
        }
    }
}

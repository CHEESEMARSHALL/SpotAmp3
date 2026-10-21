package com.example.ui

import androidx.compose.material3.Switch
import androidx.compose.runtime.Composable
import androidx.compose.foundation.layout.sizeIn
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.stateDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import com.example.R

@Composable
internal fun DownloadedOnlyToggle(
    checked: Boolean,
    onCheckedChange: (Boolean) -> Unit,
    modifier: Modifier = Modifier
) {
    val label = stringResource(R.string.search_downloaded_only_accessibility)
    val accessibilityState = stringResource(
        if (checked) R.string.search_downloaded_only_state_on else R.string.search_downloaded_only_state_off
    )
    Switch(
        checked = checked,
        onCheckedChange = onCheckedChange,
        modifier = modifier.sizeIn(minWidth = 48.dp, minHeight = 48.dp).semantics {
            contentDescription = label
            stateDescription = accessibilityState
        }
    )
}

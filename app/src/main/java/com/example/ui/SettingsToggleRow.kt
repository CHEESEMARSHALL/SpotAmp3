package com.example.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.selection.toggleable
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp

/** A full-width preference target whose accessible name, description, role, and state stay together. */
@Composable
internal fun SettingsToggleRow(
    label: String,
    checked: Boolean,
    onCheckedChange: (Boolean) -> Unit,
    description: String? = null,
    modifier: Modifier = Modifier,
    tag: String? = null,
    labelColor: Color? = null,
    descriptionColor: Color? = null,
    leadingContent: (@Composable RowScope.() -> Unit)? = null
) {
    val lightSurface = MaterialTheme.colorScheme.background.luminance() > 0.5f
    val resolvedLabelColor = labelColor ?: if (lightSurface) MaterialTheme.colorScheme.onSurface else Color.White
    val resolvedDescriptionColor = descriptionColor ?: if (lightSurface) {
        MaterialTheme.colorScheme.onSurfaceVariant
    } else {
        Color.White.copy(alpha = 0.65f)
    }
    var rowModifier = modifier
        .fillMaxWidth()
        .toggleable(value = checked, role = Role.Switch, onValueChange = onCheckedChange)
        .semantics(mergeDescendants = true) {}
    if (tag != null) rowModifier = rowModifier.testTag(tag)

    Row(
        modifier = rowModifier.padding(vertical = 4.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        leadingContent?.invoke(this)
        Column(modifier = Modifier.weight(1f)) {
            Text(label, color = resolvedLabelColor, style = MaterialTheme.typography.titleSmall)
            if (description != null) {
                Text(description, color = resolvedDescriptionColor, style = MaterialTheme.typography.bodySmall)
            }
        }
        Switch(
            checked = checked,
            onCheckedChange = null,
            modifier = Modifier.clearAndSetSemantics {},
            colors = SwitchDefaults.colors(checkedThumbColor = MaterialTheme.colorScheme.primary)
        )
    }
}

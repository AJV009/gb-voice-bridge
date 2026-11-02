package com.ajv009.gbvoice.ui.webhook

import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExposedDropdownMenuAnchorType
import androidx.compose.material3.ExposedDropdownMenuBox
import androidx.compose.material3.ExposedDropdownMenuDefaults
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import com.ajv009.gbvoice.routing.Hook

/**
 * Drop-down of "Nothing" (selects null) and all saved webhooks, reachable or not.
 * A selected id that no longer exists shows as "Deleted webhook".
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun HookPicker(label: String, hooks: List<Hook>, selectedId: String?, modifier: Modifier = Modifier, onSelect: (String?) -> Unit) {
    var open by remember { mutableStateOf(false) }
    val shown = when {
        selectedId == null -> Hook.NOTHING
        else -> hooks.firstOrNull { it.id == selectedId }?.label ?: "Deleted webhook, choose another"
    }
    ExposedDropdownMenuBox(expanded = open, onExpandedChange = { open = it }, modifier = modifier) {
        OutlinedTextField(
            shown, {}, readOnly = true, singleLine = true, label = { Text(label) },
            trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded = open) },
            modifier = Modifier.menuAnchor(ExposedDropdownMenuAnchorType.PrimaryNotEditable).fillMaxWidth(),
        )
        ExposedDropdownMenu(expanded = open, onDismissRequest = { open = false }) {
            DropdownMenuItem(text = { Text(Hook.NOTHING) }, onClick = { onSelect(null); open = false })
            hooks.forEach { hook ->
                DropdownMenuItem(text = { Text(hook.label) }, onClick = { onSelect(hook.id); open = false })
            }
            if (hooks.isEmpty()) DropdownMenuItem(text = { Text("Add webhooks under Saved webhooks") }, onClick = {}, enabled = false)
        }
    }
}

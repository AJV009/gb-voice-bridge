package com.ajv009.gbvoice.ui.webhook

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.unit.dp
import com.ajv009.gbvoice.routing.Hook
import com.ajv009.gbvoice.routing.Route

/** One keyword route, read-only: its keywords, the webhook it goes to, an on/off switch and Edit. */
@Composable
fun RouteSummary(route: Route, hooks: List<Hook>, onToggle: (Boolean) -> Unit, onEdit: () -> Unit) {
    val hook = hooks.firstOrNull { it.id == route.hookId }
    Row(Modifier.padding(start = 16.dp, top = 8.dp, bottom = 8.dp, end = 4.dp), verticalAlignment = Alignment.CenterVertically) {
        Column(Modifier.weight(1f)) {
            Text(route.keywordList.joinToString(", ").ifBlank { "(no keywords)" }, style = MaterialTheme.typography.titleMedium)
            Text(
                when {
                    route.hookId == null -> "→ ${Hook.NOTHING} (this route is ignored)"
                    hook == null -> "→ deleted webhook: edit to pick another"
                    else -> "→ ${hook.label}"
                },
                style = MaterialTheme.typography.bodyMedium,
                color = if (hook == null) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        Switch(checked = route.enabled, onCheckedChange = onToggle)
        IconButton(onClick = onEdit) { Icon(Icons.Filled.Edit, "Edit") }
    }
}

/** Keywords (comma-separated) and the webhook, picked from the saved ones. [onDelete] is null for a new route. */
@Composable
fun RouteEditor(route: Route, hooks: List<Hook>, onSave: (Route) -> Unit, onCancel: () -> Unit, onDelete: (() -> Unit)?) {
    val focus = LocalFocusManager.current
    var keywords by rememberSaveable(route.id) { mutableStateOf(route.keywords) }
    var hookId by rememberSaveable(route.id) { mutableStateOf(route.hookId) }
    Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
        OutlinedTextField(
            keywords, { keywords = it }, label = { Text("Keywords, comma-separated") }, placeholder = { Text("organizer, organiser") },
            singleLine = true, modifier = Modifier.fillMaxWidth(),
        )
        HookPicker("Send to", hooks, hookId, Modifier.fillMaxWidth()) { hookId = it }
        EditorActions(
            saveEnabled = keywords.isNotBlank(),
            onSave = { focus.clearFocus(); onSave(route.copy(keywords = keywords.trim(), hookId = hookId)) },
            onCancel = { focus.clearFocus(); onCancel() },
            onDelete = onDelete,
        )
    }
}

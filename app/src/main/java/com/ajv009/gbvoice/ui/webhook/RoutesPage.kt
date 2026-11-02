package com.ajv009.gbvoice.ui.webhook

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material3.Card
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.ajv009.gbvoice.App
import com.ajv009.gbvoice.data.SettingsState
import com.ajv009.gbvoice.routing.Route
import com.ajv009.gbvoice.routing.Router
import com.ajv009.gbvoice.ui.Hint
import com.ajv009.gbvoice.ui.Page
import com.ajv009.gbvoice.ui.Section
import com.ajv009.gbvoice.ui.SwitchRow

/** Keyword routes: on/off, one card per route (keywords → a saved webhook), "Add", and a sentence tester. */
@Composable
fun RoutesPage(onOpenHooks: () -> Unit) {
    val app = App.get(LocalContext.current)
    val s by app.settings.state.collectAsStateWithLifecycle()
    var editingId by rememberSaveable { mutableStateOf<String?>(null) }
    var adding by rememberSaveable { mutableStateOf(false) }
    var newRoute by rememberSaveable(adding) { mutableStateOf(Route().id) }

    fun save(route: Route) = app.settings.update { st ->
        st.copy(routes = if (st.routes.any { it.id == route.id }) st.routes.map { if (it.id == route.id) route else it } else st.routes + route)
    }

    Page {
        Section("Keyword routes") {
            SwitchRow(
                "Route by first word",
                "Start a dictation with a keyword, like \"Organizer, add milk\", to send it to that keyword's webhook. " +
                    "The keyword is taken off, so the server gets \"Add milk\".",
                s.routesEnabled,
            ) { on -> app.settings.update { it.copy(routesEnabled = on) } }
            Hint(
                "Case and punctuation around the keyword don't matter (\"organizer.\", \"Organizer:\", \"Hey organizer,\"). " +
                    "The word itself must match: add other spellings as extra keywords, for example \"organizer, organiser\".",
            )
        }
        Text("Webhooks show up here once you add them under Saved webhooks.",
            style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.padding(horizontal = 4.dp))
        if (s.hooks.isEmpty()) TextButton(onClick = onOpenHooks) { Text("Open Saved webhooks") }
        s.routes.forEach { route ->
            EditableCard(editingId == route.id, onOpen = { editingId = route.id }, editor = {
                RouteEditor(route, s.hooks, onSave = { save(it); editingId = null }, onCancel = { editingId = null },
                    onDelete = { app.settings.update { st -> st.copy(routes = st.routes - route) }; editingId = null })
            }, summary = {
                RouteSummary(route, s.hooks, onToggle = { on -> save(route.copy(enabled = on)) }, onEdit = { editingId = route.id })
            })
        }
        if (adding) {
            Card(Modifier.fillMaxWidth()) {
                RouteEditor(Route(id = newRoute), s.hooks, onSave = { save(it); adding = false }, onCancel = { adding = false }, onDelete = null)
            }
        } else {
            OutlinedButton(onClick = { adding = true }) {
                Icon(Icons.Filled.Add, null)
                Text("  Add a keyword route")
            }
        }
        if (s.routes.isNotEmpty()) Section("Try a sentence") { SentenceCheck(s) }
    }
}

/** Type what you would say; shows where it would go and what the server would get. */
@Composable
private fun SentenceCheck(s: SettingsState) {
    var text by rememberSaveable { mutableStateOf("") }
    Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
        OutlinedTextField(
            text, { text = it }, label = { Text("What you would say") },
            placeholder = { Text("Organizer, add milk to Q1") }, modifier = Modifier.fillMaxWidth(),
        )
        if (text.isNotBlank()) Text(describe(text, s), style = MaterialTheme.typography.bodyMedium)
    }
}

internal fun describe(text: String, s: SettingsState): String {
    val target = Router.target(text, s)
    val match = target?.match
    return when {
        match != null -> "Goes to ${match.hook.label} (heard \"${match.heard}\"), which gets: \"${match.text}\""
        target != null -> (if (s.routesEnabled) "No keyword matched." else "Keyword routes are off.") +
            " Goes to the default webhook (${target.hook.label}) as it is."
        else -> (if (s.routesEnabled) "No keyword matched" else "Keyword routes are off") +
            " and the default webhook is Nothing, so the watch just shows the text."
    }
}

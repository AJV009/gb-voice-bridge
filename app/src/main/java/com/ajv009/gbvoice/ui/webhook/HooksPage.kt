package com.ajv009.gbvoice.ui.webhook

import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material3.Card
import androidx.compose.material3.Icon
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.ajv009.gbvoice.App
import com.ajv009.gbvoice.routing.Hook
import com.ajv009.gbvoice.ui.Hint
import com.ajv009.gbvoice.ui.Page
import kotlinx.coroutines.launch

/**
 * Saved webhooks: every URL + secret you use, kept in one place (also as a backup to copy them from).
 * Each opening checks all connections.
 */
@Composable
fun HooksPage() {
    val app = App.get(LocalContext.current)
    val s by app.settings.state.collectAsStateWithLifecycle()
    val checks by HookChecks.state.collectAsStateWithLifecycle()
    val scope = rememberCoroutineScope()
    var editingId by rememberSaveable { mutableStateOf<String?>(null) }
    var adding by rememberSaveable { mutableStateOf(false) }
    var newHook by rememberSaveable(adding) { mutableStateOf(Hook().id) }

    LaunchedEffect(Unit) { HookChecks.checkAll(app.settings.current.hooks) }

    fun save(hook: Hook) {
        app.settings.update { st ->
            st.copy(hooks = if (st.hooks.any { it.id == hook.id }) st.hooks.map { if (it.id == hook.id) hook else it } else st.hooks + hook)
        }
        scope.launch { HookChecks.check(hook) }
    }

    Page {
        Hint(
            "Add each server once. Keyword routes and the default webhook pick from this list by name. " +
                "Secrets are encrypted on this phone and sent only to their own URL. Tap a card to edit it.",
        )
        Hint("Names like \"Nothing\", \"None\" or \"No webhook\" can't be used: the lists use \"Nothing\" for no webhook.")
        s.hooks.forEach { hook ->
            EditableCard(editingId == hook.id, onOpen = { editingId = hook.id }, editor = {
                HookEditor(hook, s.hooks - hook, onSave = { save(it); editingId = null }, onCancel = { editingId = null },
                    onDelete = {
                        app.settings.update { st -> st.copy(hooks = st.hooks - hook, defaultHookId = st.defaultHookId.takeIf { it != hook.id }) }
                        editingId = null
                    })
            }, summary = {
                HookSummary(hook, checks[hook.id], onEdit = { editingId = hook.id })
            })
        }
        if (adding) {
            Card(Modifier.fillMaxWidth()) {
                HookEditor(Hook(id = newHook), s.hooks, onSave = { save(it); adding = false }, onCancel = { adding = false }, onDelete = null)
            }
        } else {
            OutlinedButton(onClick = { adding = true }) {
                Icon(Icons.Filled.Add, null)
                Text("  Add a webhook")
            }
        }
    }
}

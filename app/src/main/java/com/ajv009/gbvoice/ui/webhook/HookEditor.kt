package com.ajv009.gbvoice.ui.webhook

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Clear
import androidx.compose.material.icons.filled.Visibility
import androidx.compose.material.icons.filled.VisibilityOff
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.unit.dp
import com.ajv009.gbvoice.routing.Hook

/**
 * Name, URL and secret of one saved webhook. The secret field starts empty: empty keeps the saved secret, so nobody
 * has to retype a long one. [onDelete] is null for a webhook that isn't saved yet; deleting asks first.
 */
@Composable
fun HookEditor(hook: Hook, others: List<Hook>, onSave: (Hook) -> Unit, onCancel: () -> Unit, onDelete: (() -> Unit)?) {
    val focus = LocalFocusManager.current
    var name by rememberSaveable(hook.id) { mutableStateOf(hook.name) }
    var url by rememberSaveable(hook.id) { mutableStateOf(hook.url) }
    var token by rememberSaveable(hook.id) { mutableStateOf("") }
    var removeToken by rememberSaveable(hook.id) { mutableStateOf(false) }
    var showToken by rememberSaveable(hook.id) { mutableStateOf(false) }
    var confirmDelete by rememberSaveable(hook.id) { mutableStateOf(false) }
    val nameProblem = Hook.nameProblem(name, others)
    val wide = Modifier.fillMaxWidth()

    Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
        OutlinedTextField(
            name, { name = it }, label = { Text("Name") }, placeholder = { Text("TickTick Organizer") }, singleLine = true,
            isError = name.isNotEmpty() && nameProblem != null, modifier = wide,
            // Quiet until the name won't work; the rules are on the page, above the list
            supportingText = nameProblem?.takeIf { name.isNotEmpty() }?.let { { Text(it) } },
        )
        OutlinedTextField(
            url, { url = it }, label = { Text("URL") }, placeholder = { Text("https://example.com/voice") }, singleLine = true,
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Uri, imeAction = ImeAction.Next), modifier = wide,
        )
        if (url.trim().startsWith("http://")) Text(
            "http:// is not encrypted. Use it only inside a private network such as Tailscale.",
            style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.error,
        )
        OutlinedTextField(
            token, { token = it; removeToken = false }, label = { Text("Secret (bearer token)") }, singleLine = true,
            enabled = !removeToken, modifier = wide,
            placeholder = { Text(if (hook.token.isBlank()) "Optional" else "Leave empty to keep the saved secret") },
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Password, imeAction = ImeAction.Done),
            visualTransformation = if (showToken) VisualTransformation.None else PasswordVisualTransformation(),
            trailingIcon = {
                Row {
                    IconButton(onClick = { showToken = !showToken }) {
                        Icon(if (showToken) Icons.Filled.VisibilityOff else Icons.Filled.Visibility, "Show secret")
                    }
                    if (hook.token.isNotBlank() && !removeToken) IconButton(onClick = { removeToken = true; token = "" }) {
                        Icon(Icons.Filled.Clear, "Remove the saved secret")
                    }
                }
            },
            supportingText = if (removeToken) { { Text("The saved secret is removed when you save.") } } else null,
        )
        EditorActions(
            saveEnabled = nameProblem == null && url.isNotBlank(),
            onSave = {
                focus.clearFocus()
                val secret = when {
                    removeToken -> ""
                    token.isNotBlank() -> token.trim()
                    else -> hook.token
                }
                onSave(hook.copy(name = name.trim(), url = url.trim(), token = secret))
            },
            onCancel = { focus.clearFocus(); onCancel() },
            onDelete = onDelete?.let { { confirmDelete = true } },
        )
    }
    if (confirmDelete && onDelete != null) AlertDialog(
        onDismissRequest = { confirmDelete = false },
        title = { Text("Delete ${hook.label}?") },
        text = { Text("Its URL and secret are removed from this phone. Keyword routes that use it stop until you pick another webhook.") },
        confirmButton = { TextButton(onClick = { confirmDelete = false; onDelete() }) { Text("Delete") } },
        dismissButton = { TextButton(onClick = { confirmDelete = false }) { Text("Cancel") } },
    )
}

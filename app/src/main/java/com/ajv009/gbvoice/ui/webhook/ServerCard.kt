package com.ajv009.gbvoice.ui.webhook

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.Visibility
import androidx.compose.material.icons.filled.VisibilityOff
import androidx.compose.material3.Button
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
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
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.unit.dp
import com.ajv009.gbvoice.data.Settings
import com.ajv009.gbvoice.data.SettingsState

/**
 * Webhook URL and token. Read-only until "Edit", so a stray tap can't change them; "Save" stores both at once.
 * The token field starts empty while editing: empty keeps the saved token, so nobody has to edit a long secret.
 */
@Composable
fun ServerCard(s: SettingsState, settings: Settings) {
    var editing by rememberSaveable { mutableStateOf(s.webhookUrl.isBlank()) }
    if (editing) {
        ServerEditor(s, onDone = { url, token, removeToken ->
            settings.update {
                it.copy(
                    webhookUrl = url.trim(),
                    webhookToken = when {
                        removeToken -> ""
                        token.isNotBlank() -> token.trim()
                        else -> it.webhookToken
                    },
                )
            }
            editing = false
        }, onCancel = { editing = false }.takeIf { s.webhookUrl.isNotBlank() })
    } else {
        ServerSummary(s, onEdit = { editing = true })
    }
}

@Composable
private fun ServerSummary(s: SettingsState, onEdit: () -> Unit) {
    Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
        Text("URL", style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
        Text(s.webhookUrl.ifBlank { "Not set" }, style = MaterialTheme.typography.bodyLarge)
        Spacer(Modifier.padding(2.dp))
        Text("Bearer token", style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
        Text(if (s.webhookToken.isBlank()) "None" else "Saved (encrypted on this phone)", style = MaterialTheme.typography.bodyLarge)
        HttpWarning(s.webhookUrl)
        OutlinedButton(onClick = onEdit, modifier = Modifier.padding(top = 8.dp)) {
            Icon(Icons.Filled.Edit, null)
            Text("  Edit")
        }
    }
}

@Composable
private fun ServerEditor(s: SettingsState, onDone: (String, String, Boolean) -> Unit, onCancel: (() -> Unit)?) {
    val focus = LocalFocusManager.current
    var url by rememberSaveable { mutableStateOf(s.webhookUrl) }
    var token by rememberSaveable { mutableStateOf("") }
    var removeToken by rememberSaveable { mutableStateOf(false) }
    var showToken by rememberSaveable { mutableStateOf(false) }
    val save = {
        focus.clearFocus()
        onDone(url, token, removeToken)
    }

    Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
        OutlinedTextField(
            url, { url = it },
            label = { Text("URL") }, placeholder = { Text("https://example.com/voice") }, singleLine = true,
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Uri, imeAction = ImeAction.Next),
            modifier = Modifier.fillMaxWidth(),
        )
        HttpWarning(url)
        OutlinedTextField(
            token, { token = it; removeToken = false },
            label = { Text("Bearer token") },
            placeholder = { Text(if (s.webhookToken.isBlank()) "Optional" else "Leave empty to keep the saved token") },
            singleLine = true, enabled = !removeToken,
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Password, imeAction = ImeAction.Done),
            keyboardActions = KeyboardActions(onDone = { save() }),
            visualTransformation = if (showToken) VisualTransformation.None else PasswordVisualTransformation(),
            trailingIcon = {
                IconButton(onClick = { showToken = !showToken }) {
                    Icon(if (showToken) Icons.Filled.VisibilityOff else Icons.Filled.Visibility, "Show token")
                }
            },
            modifier = Modifier.fillMaxWidth(),
        )
        Text(
            if (removeToken) "The saved token will be removed when you save."
            else "The token is encrypted on this phone and only sent to this URL, as an Authorization header.",
            style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            Button(onClick = save, enabled = url.isNotBlank()) { Text("Save") }
            onCancel?.let { cancel ->
                OutlinedButton(onClick = { focus.clearFocus(); cancel() }) { Text("Cancel") }
            }
            if (s.webhookToken.isNotBlank() && !removeToken) {
                TextButton(onClick = { removeToken = true; token = "" }) { Text("Remove token") }
            }
        }
    }
}

@Composable
private fun HttpWarning(url: String) {
    if (url.trim().startsWith("http://")) Text(
        "http:// is not encrypted. Use it only inside a private network such as Tailscale.",
        style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.error,
    )
}

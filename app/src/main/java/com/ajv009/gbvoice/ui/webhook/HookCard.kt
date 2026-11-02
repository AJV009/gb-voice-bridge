package com.ajv009.gbvoice.ui.webhook

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.os.PersistableBundle
import android.widget.Toast
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.ContentCopy
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.ErrorOutline
import androidx.compose.material.icons.filled.Visibility
import androidx.compose.material.icons.filled.VisibilityOff
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.unit.dp
import com.ajv009.gbvoice.routing.Hook

/** A saved webhook as a read-only card: connection status, URL and secret with copy buttons, and Edit. */
@Composable
fun HookSummary(hook: Hook, check: Check?, onEdit: () -> Unit) {
    val context = LocalContext.current
    val muted = MaterialTheme.colorScheme.onSurfaceVariant
    var showSecret by rememberSaveable(hook.id) { mutableStateOf(false) }
    Column(Modifier.padding(start = 16.dp, top = 8.dp, bottom = 8.dp, end = 4.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Column(Modifier.weight(1f)) {
                Text(hook.label, style = MaterialTheme.typography.titleMedium)
                CheckLine(check)
            }
            IconButton(onClick = onEdit) { Icon(Icons.Filled.Edit, "Edit") }
        }
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(hook.url, style = MaterialTheme.typography.bodySmall, color = muted, modifier = Modifier.weight(1f))
            IconButton(onClick = { copy(context, "Webhook URL", hook.url, secret = false) }) { Icon(Icons.Filled.ContentCopy, "Copy URL") }
        }
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(
                when {
                    hook.token.isEmpty() -> "No secret"
                    showSecret -> hook.token
                    else -> "•".repeat(12)
                },
                style = MaterialTheme.typography.bodySmall, color = muted, fontFamily = FontFamily.Monospace,
                modifier = Modifier.weight(1f),
            )
            if (hook.token.isNotEmpty()) {
                IconButton(onClick = { showSecret = !showSecret }) {
                    Icon(if (showSecret) Icons.Filled.VisibilityOff else Icons.Filled.Visibility, if (showSecret) "Hide secret" else "Show secret")
                }
                IconButton(onClick = { copy(context, "Webhook secret", hook.token, secret = true) }) {
                    Icon(Icons.Filled.ContentCopy, "Copy secret")
                }
            }
        }
    }
}

/** Light green check + "Connected", red mark + the reason, or a small spinner while checking. */
@Composable
private fun CheckLine(check: Check?) {
    check ?: return
    val green = if (isSystemInDarkTheme()) Color(0xFF81C784) else Color(0xFF2E7D32)
    val color = when (check.state) {
        CheckState.OK -> green
        CheckState.FAILED -> MaterialTheme.colorScheme.error
        CheckState.CHECKING -> MaterialTheme.colorScheme.onSurfaceVariant
    }
    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(4.dp)) {
        val small = Modifier.size(14.dp)
        when (check.state) {
            CheckState.OK -> Icon(Icons.Filled.CheckCircle, null, tint = color, modifier = small)
            CheckState.FAILED -> Icon(Icons.Filled.ErrorOutline, null, tint = color, modifier = small)
            CheckState.CHECKING -> CircularProgressIndicator(modifier = small, strokeWidth = 2.dp)
        }
        Text(check.text, style = MaterialTheme.typography.labelSmall, color = color)
    }
}

/** Copies to the clipboard; secrets are flagged so Android hides them in the clipboard preview. */
private fun copy(context: Context, label: String, text: String, secret: Boolean) {
    val clip = ClipData.newPlainText(label, text)
    if (secret) clip.description.extras = PersistableBundle().apply { putBoolean("android.content.extra.IS_SENSITIVE", true) }
    context.getSystemService(ClipboardManager::class.java).setPrimaryClip(clip)
    Toast.makeText(context, "$label copied", Toast.LENGTH_SHORT).show()
}

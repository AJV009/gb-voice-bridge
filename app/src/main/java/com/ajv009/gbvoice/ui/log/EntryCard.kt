package com.ajv009.gbvoice.ui.log

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.widget.Toast
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ContentCopy
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.PhoneAndroid
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Watch
import androidx.compose.material3.ElevatedCard
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.ajv009.gbvoice.App
import com.ajv009.gbvoice.asr.ModelCatalog
import com.ajv009.gbvoice.audio.OpusFrames
import com.ajv009.gbvoice.audio.Player
import com.ajv009.gbvoice.data.Delivery
import com.ajv009.gbvoice.data.Entry
import com.ajv009.gbvoice.data.Source
import com.ajv009.gbvoice.ui.Format
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch

/** One dictation. Tap to expand: what the watch showed, the server's answer, errors, and actions. */
@Composable
fun EntryCard(entry: Entry) {
    val context = LocalContext.current
    val app = App.get(context)
    val scope = rememberCoroutineScope()
    var expanded by rememberSaveable(entry.id) { mutableStateOf(false) }
    val headline = entry.transcript?.ifBlank { "(nothing heard)" }
        ?: entry.error ?: if (entry.delivery != Delivery.OFF) "(audio sent to webhook)" else "(no transcript)"

    ElevatedCard(onClick = { expanded = !expanded }, modifier = Modifier.fillMaxWidth()) {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
            Text(
                headline,
                style = MaterialTheme.typography.bodyLarge,
                maxLines = if (expanded) Int.MAX_VALUE else 3,
                overflow = TextOverflow.Ellipsis,
            )
            if (expanded) Details(entry)
            MetaRow(entry)
            if (expanded) Row {
                IconButton(onClick = { copy(context, entry.transcript ?: entry.shown) }) {
                    Icon(Icons.Filled.ContentCopy, "Copy")
                }
                if (entry.hasAudio) IconButton(onClick = {
                    scope.launch(Dispatchers.IO) {
                        runCatching { Player.play(OpusFrames.decode(OpusFrames.read(app.history.audioFile(entry.id)))) }
                    }
                }) { Icon(Icons.Filled.PlayArrow, "Play") }
                if (entry.source == Source.WATCH && app.settings.current.webhookEnabled) IconButton(onClick = {
                    scope.launch(Dispatchers.IO) { app.pipeline.resend(entry.id) }
                }) { Icon(Icons.Filled.Refresh, "Send to webhook again") }
                IconButton(onClick = { app.history.delete(entry.id) }) { Icon(Icons.Filled.Delete, "Delete") }
            }
        }
    }
}

@Composable
private fun Details(entry: Entry) {
    val small = MaterialTheme.typography.bodySmall
    val muted = MaterialTheme.colorScheme.onSurfaceVariant
    if (entry.shown.isNotBlank() && entry.shown != entry.transcript) {
        Text("Shown: ${entry.shown}", style = small, color = muted)
    }
    entry.serverReply?.let { Text("Server replied: $it", style = small, color = muted) }
    entry.error?.takeIf { entry.transcript != null || entry.delivery == Delivery.FAILED }?.let {
        Text(it, style = small, color = MaterialTheme.colorScheme.error)
    }
    if (entry.transcribeMs > 0) {
        Text("Transcribed in %.1f s".format(entry.transcribeMs / 1000f), style = small, color = muted)
    }
}

@Composable
private fun MetaRow(entry: Entry) {
    val muted = MaterialTheme.colorScheme.onSurfaceVariant
    val style = MaterialTheme.typography.labelMedium
    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
        Icon(
            if (entry.source == Source.WATCH) Icons.Filled.Watch else Icons.Filled.PhoneAndroid,
            contentDescription = if (entry.source == Source.WATCH) "From the watch" else "Phone mic test",
            tint = muted, modifier = Modifier.size(16.dp),
        )
        val engine = entry.engine?.let { ModelCatalog.byId(it)?.title ?: it }
        Text(listOfNotNull(Format.time(entry.at), Format.duration(entry.durationMs), engine).joinToString(" · "),
            style = style, color = muted)
        DeliveryLabel(entry, style)
    }
}

@Composable
private fun DeliveryLabel(entry: Entry, style: androidx.compose.ui.text.TextStyle) {
    val (text, color) = when (entry.delivery) {
        Delivery.OFF -> return
        Delivery.SENDING -> "· sending…" to MaterialTheme.colorScheme.onSurfaceVariant
        Delivery.SENT -> "· sent" to MaterialTheme.colorScheme.primary
        Delivery.FAILED -> "· not sent" to MaterialTheme.colorScheme.error
    }
    Text(text, style = style, color = if (color == Color.Unspecified) MaterialTheme.colorScheme.onSurface else color)
}

private fun copy(context: Context, text: String) {
    val clipboard = context.getSystemService(ClipboardManager::class.java)
    clipboard.setPrimaryClip(ClipData.newPlainText("Dictation", text))
    Toast.makeText(context, "Copied", Toast.LENGTH_SHORT).show()
}

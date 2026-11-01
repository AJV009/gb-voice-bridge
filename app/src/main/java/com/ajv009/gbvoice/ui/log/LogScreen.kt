package com.ajv009.gbvoice.ui.log

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.ajv009.gbvoice.App
import com.ajv009.gbvoice.asr.ModelCatalog
import com.ajv009.gbvoice.bridge.Caller

/** Setup prompts on top (only when something needs doing), then every dictation, newest first. */
@Composable
fun LogScreen(onOpenSpeech: () -> Unit, onOpenSetup: () -> Unit) {
    val context = LocalContext.current
    val app = App.get(context)
    val entries by app.history.entries.collectAsStateWithLifecycle()
    val pending by app.callers.pending.collectAsStateWithLifecycle()
    val allowed by app.callers.allowed.collectAsStateWithLifecycle()
    val settings by app.settings.state.collectAsStateWithLifecycle()
    val model = ModelCatalog.byId(settings.modelId)
    val modelMissing = settings.transcribeOnPhone && (model == null || !model.isReady(context))
    var confirmClear by remember { mutableStateOf(false) }

    LazyColumn(
        contentPadding = PaddingValues(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        items(pending, key = { "pending-${it.pkg}-${it.cert}" }) { caller ->
            PendingCallerCard(caller, onAllow = { app.callers.allow(caller) }, onDismiss = { app.callers.dismiss(caller) })
        }
        if (allowed.isEmpty() && pending.isEmpty()) item {
            Prompt("Connect Gadgetbridge", "Point Gadgetbridge's voice service at this app, then use the assistant on the watch once.",
                "How to", onOpenSetup)
        }
        if (modelMissing) item {
            Prompt("Download a speech model", "Transcription on the phone needs a model. Parakeet v2 is the fast choice for English.",
                "Choose a model", onOpenSpeech)
        }
        if (!settings.isUsable) item {
            Prompt("Nothing to do with the audio", "Turn on transcription, or set up the webhook to receive the audio.",
                "Speech settings", onOpenSpeech)
        }
        if (entries.isEmpty()) item {
            Text(
                "No dictations yet. Talk to the watch's assistant, or test the engine with the phone mic in the Speech tab.",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(8.dp),
            )
        } else item {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(if (entries.size == 1) "1 dictation" else "${entries.size} dictations", style = MaterialTheme.typography.titleSmall, modifier = Modifier.weight(1f))
                TextButton(onClick = { confirmClear = true }) { Text("Clear all") }
            }
        }
        items(entries, key = { it.id }) { EntryCard(it) }
    }

    if (confirmClear) AlertDialog(
        onDismissRequest = { confirmClear = false },
        title = { Text("Clear the log?") },
        text = { Text("Deletes every dictation and its audio from this phone. Copies already sent to your webhook stay there.") },
        confirmButton = { TextButton(onClick = { app.history.clear(); confirmClear = false }) { Text("Clear") } },
        dismissButton = { TextButton(onClick = { confirmClear = false }) { Text("Cancel") } },
    )
}

@Composable
private fun PendingCallerCard(caller: Caller, onAllow: () -> Unit, onDismiss: () -> Unit) {
    Card(colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.tertiaryContainer)) {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
            Text("${caller.label} wants to send voice", style = MaterialTheme.typography.titleMedium)
            Text(caller.pkg, style = MaterialTheme.typography.bodySmall)
            Text("Signing certificate ${caller.cert.take(23)}…", style = MaterialTheme.typography.bodySmall)
            Text(
                "Allow only the Gadgetbridge you installed. The app is remembered by its signing certificate, " +
                    "so another app using the same name is still refused.",
                style = MaterialTheme.typography.bodySmall,
            )
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Button(onClick = onAllow) { Text("Allow") }
                OutlinedButton(onClick = onDismiss) { Text("Dismiss") }
            }
        }
    }
}

@Composable
private fun Prompt(title: String, body: String, action: String, onAction: () -> Unit) {
    Card(colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.secondaryContainer)) {
        Column(Modifier.fillMaxWidth().padding(16.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
            Text(title, style = MaterialTheme.typography.titleMedium)
            Text(body, style = MaterialTheme.typography.bodyMedium)
            Spacer(Modifier.padding(2.dp))
            OutlinedButton(onClick = onAction) { Text(action) }
        }
    }
}

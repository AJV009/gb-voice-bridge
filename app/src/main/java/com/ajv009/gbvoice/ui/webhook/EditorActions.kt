package com.ajv009.gbvoice.ui.webhook

import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp

/** The bottom row of an editing card: Delete on the left (when there is something to delete), Cancel and Save on the right. */
@Composable
fun EditorActions(saveEnabled: Boolean, onSave: () -> Unit, onCancel: () -> Unit, onDelete: (() -> Unit)?) {
    Row(Modifier.fillMaxWidth().padding(top = 4.dp), verticalAlignment = Alignment.CenterVertically) {
        onDelete?.let { TextButton(onClick = it) { Text("Delete", color = MaterialTheme.colorScheme.error) } }
        Spacer(Modifier.weight(1f))
        TextButton(onClick = onCancel) { Text("Cancel") }
        Spacer(Modifier.width(8.dp))
        Button(onClick = onSave, enabled = saveEnabled) { Text("Save") }
    }
}

/** A list card that opens its editor when tapped; while editing it is a plain card (no ripple on the form). */
@Composable
fun EditableCard(editing: Boolean, onOpen: () -> Unit, editor: @Composable () -> Unit, summary: @Composable () -> Unit) {
    if (editing) Card(Modifier.fillMaxWidth()) { editor() }
    else Card(onClick = onOpen, modifier = Modifier.fillMaxWidth()) { summary() }
}

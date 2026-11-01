package com.ajv009.gbvoice.ui.speech

import android.Manifest
import android.os.Build
import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Download
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.ListItem
import androidx.compose.material3.ListItemDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import com.ajv009.gbvoice.asr.DownloadState
import com.ajv009.gbvoice.asr.Downloads
import com.ajv009.gbvoice.asr.Recognizer
import com.ajv009.gbvoice.asr.SpeechModel
import com.ajv009.gbvoice.ui.Format

/** One model: radio to select (once downloaded), size and note, download progress, download/cancel/delete. */
@Composable
fun ModelRow(model: SpeechModel, selected: Boolean, onSelect: () -> Unit) {
    val context = LocalContext.current
    val state by remember(model.id) { Downloads.state(context, model) }.collectAsState(DownloadState.Idle)
    var diskVersion by remember { mutableIntStateOf(0) }
    val ready = remember(state, diskVersion) { model.isReady(context) }
    var confirmDelete by remember { mutableStateOf(false) }
    val askNotifications = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) {
        Downloads.start(context, model)
    }
    val startDownload = {
        val free = context.filesDir.usableSpace
        if (free < model.bytes - model.bytesOnDisk(context) + SPARE_BYTES) {
            Toast.makeText(context, "Not enough space: ${Format.bytes(model.bytes)} needed", Toast.LENGTH_LONG).show()
        } else if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            askNotifications.launch(Manifest.permission.POST_NOTIFICATIONS)
        } else {
            Downloads.start(context, model)
        }
    }

    ListItem(
        headlineContent = { Text(model.title) },
        supportingContent = {
            Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                Text("${Format.bytes(model.bytes)} · ${model.note}")
                when (val st = state) {
                    is DownloadState.Running -> {
                        val done = maxOf(st.done, 0)
                        LinearProgressIndicator(
                            progress = { (done.toFloat() / st.total).coerceIn(0f, 1f) },
                            modifier = Modifier.fillMaxWidth(),
                        )
                        Text("${Format.bytes(done)} of ${Format.bytes(st.total)}")
                    }
                    is DownloadState.Failed -> if (!ready) {
                        Text("Download failed: ${st.reason}", color = MaterialTheme.colorScheme.error)
                    }
                    DownloadState.Idle -> Unit
                }
            }
        },
        leadingContent = { RadioButton(selected = selected && ready, onClick = null, enabled = ready) },
        trailingContent = {
            when {
                state is DownloadState.Running -> IconButton(onClick = { Downloads.cancel(context, model) }) {
                    Icon(Icons.Filled.Close, "Cancel download")
                }
                ready -> IconButton(onClick = { confirmDelete = true }) { Icon(Icons.Filled.Delete, "Delete model") }
                else -> IconButton(onClick = startDownload) { Icon(Icons.Filled.Download, "Download") }
            }
        },
        colors = ListItemDefaults.colors(containerColor = Color.Transparent),
        modifier = Modifier.clickable(enabled = ready, onClick = onSelect),
    )

    if (confirmDelete) AlertDialog(
        onDismissRequest = { confirmDelete = false },
        title = { Text("Delete ${model.title}?") },
        text = { Text("Frees ${Format.bytes(model.bytes)}. You can download it again later.") },
        confirmButton = {
            TextButton(onClick = {
                Recognizer.release()
                model.delete(context)
                diskVersion++
                confirmDelete = false
            }) { Text("Delete") }
        },
        dismissButton = { TextButton(onClick = { confirmDelete = false }) { Text("Cancel") } },
    )
}

private const val SPARE_BYTES = 100_000_000L

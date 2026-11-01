package com.ajv009.gbvoice.ui.speech

import android.Manifest
import android.content.pm.PackageManager
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Mic
import androidx.compose.material.icons.filled.Stop
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import com.ajv009.gbvoice.App
import com.ajv009.gbvoice.audio.MicRecorder
import com.ajv009.gbvoice.data.Entry

/** Records up to 15 s from the phone mic and transcribes it with the selected model (no watch needed). */
@Composable
fun MicTestCard() {
    val context = LocalContext.current
    val app = App.get(context)
    val recorder = remember { MicRecorder() }
    var phase by remember { mutableStateOf(Phase.IDLE) }
    var result by remember { mutableStateOf<Entry?>(null) }

    val start = {
        phase = Phase.RECORDING
        result = null
        recorder.start { samples ->
            phase = Phase.TRANSCRIBING
            result = app.pipeline.processMic(samples)
            phase = Phase.IDLE
        }
    }
    val askMic = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { granted ->
        if (granted) start()
    }
    DisposableEffect(Unit) { onDispose { recorder.stop() } }

    Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            when (phase) {
                Phase.IDLE -> Button(onClick = {
                    val granted = ContextCompat.checkSelfPermission(context, Manifest.permission.RECORD_AUDIO) ==
                        PackageManager.PERMISSION_GRANTED
                    if (granted) start() else askMic.launch(Manifest.permission.RECORD_AUDIO)
                }) {
                    Icon(Icons.Filled.Mic, null)
                    Text("  Record")
                }
                Phase.RECORDING -> Button(
                    onClick = { recorder.stop() },
                    colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.error),
                ) {
                    Icon(Icons.Filled.Stop, null)
                    Text("  Stop")
                }
                Phase.TRANSCRIBING -> Button(onClick = {}, enabled = false) { Text("Transcribing…") }
            }
            Text(
                when (phase) {
                    Phase.IDLE -> "Speak for up to ${MicRecorder.MAX_SECONDS} s, like on the watch"
                    Phase.RECORDING -> "Listening… tap Stop when done"
                    Phase.TRANSCRIBING -> "The first run loads the model, which takes a few seconds"
                },
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        result?.let { r ->
            Text(r.shown, style = MaterialTheme.typography.bodyLarge)
            if (r.transcribeMs > 0) Text(
                "%.1f s of audio transcribed in %.1f s".format(r.durationMs / 1000f, r.transcribeMs / 1000f),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

private enum class Phase { IDLE, RECORDING, TRANSCRIBING }

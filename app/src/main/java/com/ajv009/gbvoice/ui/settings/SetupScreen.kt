package com.ajv009.gbvoice.ui.settings

import android.content.ClipData
import android.content.ClipboardManager
import android.widget.Toast
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ContentCopy
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.ListItem
import androidx.compose.material3.ListItemDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.ajv009.gbvoice.App
import com.ajv009.gbvoice.VoiceService
import com.ajv009.gbvoice.ui.Hint
import com.ajv009.gbvoice.ui.Page
import com.ajv009.gbvoice.ui.Section
import com.ajv009.gbvoice.ui.SwitchRow

/** Connecting Gadgetbridge, the apps allowed to send audio, log options and licences. */
@Composable
fun SetupScreen() {
    val context = LocalContext.current
    val app = App.get(context)
    val s by app.settings.state.collectAsStateWithLifecycle()
    val allowed by app.callers.allowed.collectAsStateWithLifecycle()
    val copy = { label: String, value: String ->
        context.getSystemService(ClipboardManager::class.java).setPrimaryClip(ClipData.newPlainText(label, value))
        Toast.makeText(context, "Copied", Toast.LENGTH_SHORT).show()
    }

    Page {
        Section("Connect Gadgetbridge") {
            Hint(
                "In Gadgetbridge, open your watch's settings → Developer settings → Voice Assistant, and fill in " +
                    "these two values. Then use the assistant on the watch once and allow Gadgetbridge in the Log tab.",
            )
            CopyRow("Voice service package", context.packageName, copy)
            CopyRow("Voice service class", VoiceService::class.java.name, copy)
        }

        Section("Apps allowed to send audio") {
            if (allowed.isEmpty()) Hint("None yet. Apps that try appear in the Log tab for you to allow.")
            allowed.forEach { caller ->
                ListItem(
                    headlineContent = { Text(caller.label) },
                    supportingContent = { Text("${caller.pkg}\nCertificate ${caller.cert.take(23)}…") },
                    trailingContent = { TextButton(onClick = { app.callers.revoke(caller) }) { Text("Remove") } },
                    colors = ListItemDefaults.colors(containerColor = Color.Transparent),
                )
            }
        }

        Section("Log") {
            SwitchRow(
                "Keep watch audio",
                "About 30 KB per dictation, so you can play it back or resend it.",
                s.keepAudio,
            ) { on -> app.settings.update { it.copy(keepAudio = on) } }
        }

        Section("About") {
            Hint(
                "GB Voice Bridge ${versionName(context)}. Speech recognition runs on the phone with sherpa-onnx " +
                    "(Apache-2.0). Parakeet models © NVIDIA, CC-BY-4.0. Whisper models © OpenAI, MIT. " +
                    "Opus decoding by Concentus (BSD). Models are downloaded from Hugging Face.",
            )
        }
    }
}

@Composable
private fun CopyRow(label: String, value: String, copy: (String, String) -> Unit) {
    ListItem(
        headlineContent = { Text(value) },
        overlineContent = { Text(label) },
        trailingContent = { IconButton(onClick = { copy(label, value) }) { Icon(Icons.Filled.ContentCopy, "Copy") } },
        colors = ListItemDefaults.colors(containerColor = Color.Transparent),
    )
}

private fun versionName(context: android.content.Context): String =
    runCatching { context.packageManager.getPackageInfo(context.packageName, 0).versionName }.getOrNull() ?: ""

package com.ajv009.gbvoice.ui.webhook

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Visibility
import androidx.compose.material.icons.filled.VisibilityOff
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.ajv009.gbvoice.App
import com.ajv009.gbvoice.data.Entry
import com.ajv009.gbvoice.data.Source
import com.ajv009.gbvoice.ui.Hint
import com.ajv009.gbvoice.ui.Page
import com.ajv009.gbvoice.ui.Section
import com.ajv009.gbvoice.ui.SwitchRow
import com.ajv009.gbvoice.webhook.Webhook
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.json.JSONObject
import java.util.UUID

/** Send each dictation (text, and optionally audio) to your own server; its answer is shown on the watch. */
@Composable
fun WebhookScreen() {
    val app = App.get(LocalContext.current)
    val s by app.settings.state.collectAsStateWithLifecycle()
    val scope = rememberCoroutineScope()
    var showToken by remember { mutableStateOf(false) }
    var testResult by remember { mutableStateOf<String?>(null) }

    Page {
        Section("Webhook") {
            SwitchRow("Send dictations to a webhook", "Each dictation is POSTed as JSON to your server.", s.webhookEnabled) { on ->
                app.settings.update { it.copy(webhookEnabled = on) }
            }
        }

        Section("Server") {
            Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                OutlinedTextField(
                    s.webhookUrl, { url -> app.settings.update { it.copy(webhookUrl = url) } },
                    label = { Text("URL") }, placeholder = { Text("https://example.com/voice") },
                    singleLine = true, keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Uri),
                    modifier = Modifier.fillMaxWidth(),
                )
                if (s.webhookUrl.startsWith("http://")) Text(
                    "http:// is not encrypted. Use it only inside a private network such as Tailscale.",
                    style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.error,
                )
                OutlinedTextField(
                    s.webhookToken, { token -> app.settings.update { it.copy(webhookToken = token) } },
                    label = { Text("Bearer token (optional)") }, singleLine = true,
                    visualTransformation = if (showToken) VisualTransformation.None else PasswordVisualTransformation(),
                    trailingIcon = {
                        IconButton(onClick = { showToken = !showToken }) {
                            Icon(if (showToken) Icons.Filled.VisibilityOff else Icons.Filled.Visibility, "Show token")
                        }
                    },
                    modifier = Modifier.fillMaxWidth(),
                )
                Text(
                    "The token is encrypted on this phone and only sent to this URL, as an Authorization header.",
                    style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }

        Section("What to send") {
            SwitchRow(
                "Include the audio",
                if (s.transcribeOnPhone) "Adds the recording as WAV (about 450 KB for 14 s)."
                else "Always on: the Speech tab is set to transcribe on your server.",
                checked = s.webhookSendAudio || !s.transcribeOnPhone,
                enabled = s.transcribeOnPhone,
            ) { on -> app.settings.update { it.copy(webhookSendAudio = on) } }
        }

        Section("Test") {
            Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                OutlinedButton(enabled = s.webhookUrl.isNotBlank(), onClick = {
                    testResult = "Sending…"
                    scope.launch {
                        testResult = withContext(Dispatchers.IO) { sendTest(s.webhookUrl, s.webhookToken) }
                    }
                }) { Text("Send a test dictation") }
                testResult?.let { Text(it, style = MaterialTheme.typography.bodyMedium) }
            }
        }

        Section("What your server receives") {
            Text(
                JSONObject(Webhook.payload(sample(), null)).toString(2).replace("\\/", "/"),
                style = MaterialTheme.typography.bodySmall, fontFamily = FontFamily.Monospace,
                modifier = Modifier.padding(16.dp),
            )
            Hint(
                "With audio, an \"audio\" object adds content_type, sample_rate and base64 \"data\". " +
                    "Answer with {\"title\": …, \"text\": …} or plain text within about 12 seconds to show it " +
                    "on the watch; later answers are kept in the log. The Idempotency-Key header repeats the id, " +
                    "so a resend can be recognised.",
            )
        }
    }
}

private fun sample() = Entry(
    id = UUID.nameUUIDFromBytes("sample".toByteArray()).toString(), at = 1_793_500_000_000,
    source = Source.WATCH, durationMs = 14_000, engine = "parakeet-0.6b-v2", transcript = "Add milk to the shopping list",
)

private fun sendTest(url: String, token: String): String = try {
    val test = sample().copy(id = UUID.randomUUID().toString(), at = System.currentTimeMillis(),
        source = Source.PHONE_MIC, transcript = "Test from GB Voice Bridge")
    // "test": true lets the server answer without acting on it (see WEBHOOK.md)
    val body = JSONObject(Webhook.payload(test, null)).put("test", true).toString()
    val result = Webhook.post(url, token, test.id, body, 15_000)
    "HTTP ${result.code}. " + (result.reply?.let { "The watch would show: $it" } ?: "No reply text, so the watch would show the transcript.")
} catch (e: Exception) {
    "Failed: ${e.message ?: e.javaClass.simpleName}"
}

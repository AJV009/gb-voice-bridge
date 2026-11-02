package com.ajv009.gbvoice.ui.webhook

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material3.Icon
import androidx.compose.material3.ListItem
import androidx.compose.material3.ListItemDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.ajv009.gbvoice.App
import com.ajv009.gbvoice.routing.Router
import com.ajv009.gbvoice.ui.Hint
import com.ajv009.gbvoice.ui.Page
import com.ajv009.gbvoice.ui.Section
import com.ajv009.gbvoice.ui.SwitchRow
import com.ajv009.gbvoice.webhook.Webhook
import org.json.JSONObject

/** The Webhook tab's own pages: the overview, the saved webhooks list, and the keyword routes. */
enum class WebhookPage(val title: String) { MAIN("Webhook"), HOOKS("Saved webhooks"), ROUTES("Keyword routes") }

/** Overview: links to the two sub-pages, the default webhook, what to send, and the request format. */
@Composable
fun WebhookScreen(open: (WebhookPage) -> Unit) {
    val app = App.get(LocalContext.current)
    val s by app.settings.state.collectAsStateWithLifecycle()
    val routeCount = Router.usable(s.routes, s.hooks).size

    Page {
        Section("Webhooks") {
            NavRow("Saved webhooks", "${s.hooks.size} saved · names, URLs and secrets; checks connections") { open(WebhookPage.HOOKS) }
            NavRow(
                "Keyword routes",
                if (s.routesEnabled) "On · $routeCount active ${if (routeCount == 1) "route" else "routes"}" else "Off",
            ) { open(WebhookPage.ROUTES) }
        }

        Section("Default webhook") {
            Hint("Gets every dictation that no keyword route takes. With \"Nothing\", the watch just shows the text.")
            HookPicker("Default webhook", s.hooks, s.defaultHookId, Modifier.fillMaxWidth().padding(horizontal = 16.dp)) { id ->
                app.settings.update { it.copy(defaultHookId = id) }
            }
            Hint("Webhooks show up here once you add them under Saved webhooks.")
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

        Section("What your server receives") {
            Text(
                JSONObject(Webhook.payload(sampleEntry(), null)).toString(2).replace("\\/", "/"),
                style = MaterialTheme.typography.bodySmall, fontFamily = FontFamily.Monospace,
                modifier = Modifier.padding(16.dp),
            )
            Hint(
                "With audio, an \"audio\" object adds content_type, sample_rate and base64 \"data\". A keyword route " +
                    "sends the text after the keyword and adds a \"route\" object (see WEBHOOK.md). " +
                    "Answer with {\"title\": …, \"text\": …} or plain text within about 12 seconds to show it " +
                    "on the watch; later answers are kept in the log. The Idempotency-Key header repeats the id, " +
                    "so a resend can be recognised.",
            )
        }
    }
}

@Composable
private fun NavRow(title: String, subtitle: String, onClick: () -> Unit) {
    ListItem(
        headlineContent = { Text(title) },
        supportingContent = { Text(subtitle) },
        trailingContent = { Icon(Icons.AutoMirrored.Filled.KeyboardArrowRight, null) },
        colors = ListItemDefaults.colors(containerColor = Color.Transparent),
        modifier = Modifier.clickable(onClick = onClick),
    )
}

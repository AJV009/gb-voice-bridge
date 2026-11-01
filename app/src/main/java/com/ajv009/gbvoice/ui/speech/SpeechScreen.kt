package com.ajv009.gbvoice.ui.speech

import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.platform.LocalContext
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.ajv009.gbvoice.App
import com.ajv009.gbvoice.asr.Family
import com.ajv009.gbvoice.asr.ModelCatalog
import com.ajv009.gbvoice.ui.Hint
import com.ajv009.gbvoice.ui.Page
import com.ajv009.gbvoice.ui.RadioRow
import com.ajv009.gbvoice.ui.Section

/** Where speech becomes text: on the phone (pick and download a model) or on your server (audio to webhook). */
@Composable
fun SpeechScreen() {
    val app = App.get(LocalContext.current)
    val s by app.settings.state.collectAsStateWithLifecycle()

    Page {
        Section("Test with the phone mic") { MicTestCard() }

        Section("Speech engine") {
            RadioRow(
                "On this phone",
                "Private: the audio never leaves the phone. Needs a downloaded model.",
                selected = s.transcribeOnPhone,
            ) { app.settings.update { it.copy(transcribeOnPhone = true) } }
            RadioRow(
                "On your server",
                "The phone skips transcription and sends the audio to your webhook.",
                selected = !s.transcribeOnPhone,
            ) { app.settings.update { it.copy(transcribeOnPhone = false) } }
            if (!s.transcribeOnPhone && !(s.webhookEnabled && s.webhookUrl.isNotBlank())) {
                Hint("Set up the webhook in the Webhook tab, otherwise the watch gets nothing back.")
            }
        }

        if (s.transcribeOnPhone) {
            Family.entries.forEach { family ->
                Section(family.title) {
                    Hint(family.blurb)
                    ModelCatalog.all.filter { it.family == family }.forEach { model ->
                        ModelRow(model, selected = s.modelId == model.id) {
                            app.settings.update { it.copy(modelId = model.id) }
                        }
                    }
                }
            }
        }

        Section("Word replacements") { ReplacementsEditor() }
    }
}

package com.ajv009.gbvoice.data

import android.content.Context
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import org.json.JSONArray
import org.json.JSONObject

/** Everything the user can change. Immutable; change it with [Settings.update]. */
data class SettingsState(
    /** true: transcribe on this phone. false: skip transcription and send the audio to the webhook. */
    val transcribeOnPhone: Boolean = true,
    val modelId: String = "whisper-small.en",
    val webhookEnabled: Boolean = false,
    val webhookUrl: String = "",
    val webhookToken: String = "",
    val webhookSendAudio: Boolean = false,
    /** Keep the watch audio of each entry so it can be played back or resent. */
    val keepAudio: Boolean = true,
    val replacements: List<Replacement> = emptyList(),
) {
    /** Audio goes to the webhook when the user asked for it or when the phone does not transcribe. */
    val sendsAudio: Boolean get() = webhookEnabled && (webhookSendAudio || !transcribeOnPhone)

    /** Without transcription and without a webhook, nothing useful can happen. */
    val isUsable: Boolean get() = transcribeOnPhone || (webhookEnabled && webhookUrl.isNotBlank())
}

/** Settings stored in SharedPreferences; the webhook token is encrypted with [SecretBox]. */
class Settings(context: Context) {
    private val prefs = context.getSharedPreferences("settings", Context.MODE_PRIVATE)
    private val secrets = SecretBox()
    private val flow = MutableStateFlow(read())

    val state: StateFlow<SettingsState> = flow
    val current: SettingsState get() = flow.value

    @Synchronized
    fun update(change: (SettingsState) -> SettingsState) {
        val next = change(flow.value)
        write(next)
        flow.value = next
    }

    private fun read(): SettingsState {
        val defaults = SettingsState()
        return SettingsState(
            transcribeOnPhone = prefs.getBoolean(TRANSCRIBE, defaults.transcribeOnPhone),
            modelId = prefs.getString(MODEL, defaults.modelId) ?: defaults.modelId,
            webhookEnabled = prefs.getBoolean(WEBHOOK_ON, false),
            webhookUrl = prefs.getString(WEBHOOK_URL, "") ?: "",
            webhookToken = prefs.getString(WEBHOOK_TOKEN, null)?.let { secrets.decryptOrNull(it) } ?: "",
            webhookSendAudio = prefs.getBoolean(WEBHOOK_AUDIO, false),
            keepAudio = prefs.getBoolean(KEEP_AUDIO, defaults.keepAudio),
            replacements = Replacement.listFromJson(prefs.getString(REPLACEMENTS, "[]") ?: "[]"),
        )
    }

    private fun write(s: SettingsState) {
        prefs.edit()
            .putBoolean(TRANSCRIBE, s.transcribeOnPhone)
            .putString(MODEL, s.modelId)
            .putBoolean(WEBHOOK_ON, s.webhookEnabled)
            .putString(WEBHOOK_URL, s.webhookUrl.trim())
            .putString(WEBHOOK_TOKEN, if (s.webhookToken.isEmpty()) null else secrets.encrypt(s.webhookToken))
            .putBoolean(WEBHOOK_AUDIO, s.webhookSendAudio)
            .putBoolean(KEEP_AUDIO, s.keepAudio)
            .putString(REPLACEMENTS, Replacement.listToJson(s.replacements))
            .apply()
    }

    private companion object {
        const val TRANSCRIBE = "transcribe_on_phone"
        const val MODEL = "model_id"
        const val WEBHOOK_ON = "webhook_enabled"
        const val WEBHOOK_URL = "webhook_url"
        const val WEBHOOK_TOKEN = "webhook_token_enc"
        const val WEBHOOK_AUDIO = "webhook_send_audio"
        const val KEEP_AUDIO = "keep_audio"
        const val REPLACEMENTS = "replacements"
    }
}

/** "heard" -> "replace with", applied to transcripts before they go anywhere. */
data class Replacement(val from: String, val to: String) {
    companion object {
        /** Case-insensitive, whole words only (so "art" does not change "start"). */
        fun apply(text: String, rules: List<Replacement>): String =
            rules.filter { it.from.isNotBlank() }.fold(text) { acc, rule ->
                val pattern = Regex.escape(rule.from.trim())
                val start = if (rule.from.trim().first().isLetterOrDigit()) "\\b" else ""
                val end = if (rule.from.trim().last().isLetterOrDigit()) "\\b" else ""
                acc.replace(Regex(start + pattern + end, RegexOption.IGNORE_CASE), Regex.escapeReplacement(rule.to))
            }

        fun listToJson(list: List<Replacement>): String =
            JSONArray(list.map { JSONObject().put("from", it.from).put("to", it.to) }).toString()

        fun listFromJson(json: String): List<Replacement> = runCatching {
            val array = JSONArray(json)
            (0 until array.length()).map {
                val o = array.getJSONObject(it)
                Replacement(o.getString("from"), o.getString("to"))
            }
        }.getOrDefault(emptyList())
    }
}

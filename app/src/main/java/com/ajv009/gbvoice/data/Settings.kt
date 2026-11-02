package com.ajv009.gbvoice.data

import android.content.Context
import com.ajv009.gbvoice.routing.Hook
import com.ajv009.gbvoice.routing.Route
import com.ajv009.gbvoice.routing.Router
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import org.json.JSONArray
import org.json.JSONObject

/** Everything the user can change. Immutable; change it with [Settings.update]. */
data class SettingsState(
    /** true: transcribe on this phone. false: skip transcription and send the audio to the webhook. */
    val transcribeOnPhone: Boolean = true,
    val modelId: String = "whisper-small.en",
    /** Saved webhooks (name, URL, secret); the default webhook and keyword routes point at them by id. */
    val hooks: List<Hook> = emptyList(),
    /** Gets every dictation that no keyword route takes; null = none (the watch just shows the text). */
    val defaultHookId: String? = null,
    val webhookSendAudio: Boolean = false,
    /** Keep the watch audio of each entry so it can be played back or resent. */
    val keepAudio: Boolean = true,
    val replacements: List<Replacement> = emptyList(),
    /** Keyword routes: a dictation starting with a route's keyword goes to that route's webhook. */
    val routesEnabled: Boolean = false,
    val routes: List<Route> = emptyList(),
) {
    fun hook(id: String?): Hook? = hooks.firstOrNull { it.id == id }

    /** The default webhook, when one is chosen and it has a URL. */
    val defaultHook: Hook? get() = hook(defaultHookId)?.takeIf { it.url.isNotBlank() }

    /** Whether anything (default webhook or a keyword route) can receive dictations. */
    val anyWebhook: Boolean get() = defaultHook != null || (routesEnabled && Router.usable(routes, hooks).isNotEmpty())

    /** Audio goes to the webhook when the user asked for it or when the phone does not transcribe. */
    val sendsAudio: Boolean get() = anyWebhook && (webhookSendAudio || !transcribeOnPhone)

    /** Without transcription and without a default webhook, nothing useful can happen. */
    val isUsable: Boolean get() = transcribeOnPhone || defaultHook != null
}

/** Settings stored in SharedPreferences; webhook secrets are encrypted with [SecretBox]. */
class Settings(context: Context) {
    private val prefs = context.getSharedPreferences("settings", Context.MODE_PRIVATE)
    private val secrets = SecretBox()
    private val flow = MutableStateFlow(read())

    val state: StateFlow<SettingsState> = flow
    val current: SettingsState get() = flow.value

    init {
        // Versions before saved webhooks kept one URL + token: store the migrated list right away
        if (!prefs.contains(HOOKS)) write(flow.value)
    }

    @Synchronized
    fun update(change: (SettingsState) -> SettingsState) {
        val next = change(flow.value)
        write(next)
        flow.value = next
    }

    private fun read(): SettingsState {
        val defaults = SettingsState()
        val (hooks, defaultId) = if (prefs.contains(HOOKS)) {
            Hook.listFromJson(prefs.getString(HOOKS, "[]") ?: "[]") { secrets.decryptOrNull(it) } to
                prefs.getString(DEFAULT_HOOK, null)
        } else legacyHook()
        return SettingsState(
            transcribeOnPhone = prefs.getBoolean(TRANSCRIBE, defaults.transcribeOnPhone),
            modelId = prefs.getString(MODEL, defaults.modelId) ?: defaults.modelId,
            hooks = hooks,
            defaultHookId = defaultId,
            webhookSendAudio = prefs.getBoolean(WEBHOOK_AUDIO, false),
            keepAudio = prefs.getBoolean(KEEP_AUDIO, defaults.keepAudio),
            replacements = Replacement.listFromJson(prefs.getString(REPLACEMENTS, "[]") ?: "[]"),
            routesEnabled = prefs.getBoolean(ROUTES_ON, false),
            routes = Route.listFromJson(prefs.getString(ROUTES, "[]") ?: "[]"),
        )
    }

    /** The single webhook of older versions, as a saved webhook (the default when it was switched on). */
    private fun legacyHook(): Pair<List<Hook>, String?> {
        val url = prefs.getString(LEGACY_URL, "")?.trim().orEmpty()
        if (url.isEmpty()) return emptyList<Hook>() to null
        val token = prefs.getString(LEGACY_TOKEN, null)?.let { secrets.decryptOrNull(it) } ?: ""
        val hook = Hook(url = url, token = token)
        return listOf(hook) to hook.id.takeIf { prefs.getBoolean(LEGACY_ON, false) }
    }

    private fun write(s: SettingsState) {
        prefs.edit()
            .putBoolean(TRANSCRIBE, s.transcribeOnPhone)
            .putString(MODEL, s.modelId)
            .putString(HOOKS, Hook.listToJson(s.hooks) { secrets.encrypt(it) })
            .putString(DEFAULT_HOOK, s.defaultHookId)
            .putBoolean(WEBHOOK_AUDIO, s.webhookSendAudio)
            .putBoolean(KEEP_AUDIO, s.keepAudio)
            .putString(REPLACEMENTS, Replacement.listToJson(s.replacements))
            .putBoolean(ROUTES_ON, s.routesEnabled)
            .putString(ROUTES, Route.listToJson(s.routes))
            .remove(LEGACY_ON).remove(LEGACY_URL).remove(LEGACY_TOKEN)
            .apply()
    }

    private companion object {
        const val TRANSCRIBE = "transcribe_on_phone"
        const val MODEL = "model_id"
        const val HOOKS = "hooks"
        const val DEFAULT_HOOK = "default_hook"
        const val LEGACY_ON = "webhook_enabled"
        const val LEGACY_URL = "webhook_url"
        const val LEGACY_TOKEN = "webhook_token_enc"
        const val WEBHOOK_AUDIO = "webhook_send_audio"
        const val KEEP_AUDIO = "keep_audio"
        const val REPLACEMENTS = "replacements"
        const val ROUTES_ON = "routes_enabled"
        const val ROUTES = "routes"
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

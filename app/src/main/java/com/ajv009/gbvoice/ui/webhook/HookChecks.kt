package com.ajv009.gbvoice.ui.webhook

import com.ajv009.gbvoice.data.Entry
import com.ajv009.gbvoice.data.Source
import com.ajv009.gbvoice.routing.Hook
import com.ajv009.gbvoice.webhook.Webhook
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.json.JSONObject
import java.util.UUID

enum class CheckState { CHECKING, OK, FAILED }

/** Result of the last connection check of one saved webhook. */
data class Check(val state: CheckState, val text: String)

/**
 * Connection checks for saved webhooks: a test dictation with "test": true (see WEBHOOK.md), which servers answer
 * without acting on it. Results live in memory only, keyed by webhook id.
 */
object HookChecks {
    private val flow = MutableStateFlow<Map<String, Check>>(emptyMap())
    val state: StateFlow<Map<String, Check>> = flow

    /** Checks all webhooks at the same time. */
    suspend fun checkAll(hooks: List<Hook>) = coroutineScope { hooks.forEach { launch { check(it) } } }

    suspend fun check(hook: Hook) {
        flow.update { it + (hook.id to Check(CheckState.CHECKING, "Checking…")) }
        val result = withContext(Dispatchers.IO) { probe(hook) }
        flow.update { it + (hook.id to result) }
    }

    private fun probe(hook: Hook): Check = try {
        val test = sampleEntry().copy(id = UUID.randomUUID().toString(), at = System.currentTimeMillis(),
            source = Source.PHONE_MIC, transcript = "Test from GB Voice Bridge")
        val body = JSONObject(Webhook.payload(test, null)).put("test", true).toString()
        Webhook.post(hook.url, hook.token, test.id, body, TIMEOUT_MS)
        Check(CheckState.OK, "Connected")
    } catch (e: Exception) {
        Check(CheckState.FAILED, "Not connected: ${e.message ?: e.javaClass.simpleName}")
    }

    private const val TIMEOUT_MS = 10_000
}

/** The example dictation shown under "What your server receives", also the body of a connection check. */
internal fun sampleEntry() = Entry(
    id = UUID.nameUUIDFromBytes("sample".toByteArray()).toString(), at = 1_793_500_000_000,
    source = Source.WATCH, durationMs = 14_000, engine = "parakeet-0.6b-v2", transcript = "Add milk to the shopping list",
)

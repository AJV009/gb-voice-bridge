package com.ajv009.gbvoice.webhook

import android.util.Base64
import com.ajv009.gbvoice.audio.Wav
import com.ajv009.gbvoice.data.Entry
import com.ajv009.gbvoice.routing.RouteMatch
import org.json.JSONObject
import java.io.IOException
import java.net.HttpURLConnection
import java.net.URL
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.TimeZone

/** What the server answered; [reply] is what the watch should show, if anything. */
data class WebhookResult(val code: Int, val reply: String?)

/**
 * POSTs one dictation as JSON (format "gbvoice/1", documented in WEBHOOK.md):
 * id, recorded_at, source, duration_ms, engine, transcript and, optionally, audio as base64 WAV.
 * When a keyword route picked the webhook, transcript is the text after the keyword and "route" tells what was cut.
 * The server may answer {"title": "...", "text": "..."} or plain text, which is shown on the watch.
 */
object Webhook {
    const val FORMAT = "gbvoice/1"
    private const val MAX_REPLY = 1000

    fun payload(entry: Entry, audio: FloatArray?, match: RouteMatch? = null, encode: (ByteArray) -> String = ::base64): String {
        val json = JSONObject()
            .put("format", FORMAT)
            .put("id", entry.id)
            .put("recorded_at", iso(entry.at))
            .put("source", entry.source.name.lowercase(Locale.ROOT))
            .put("duration_ms", entry.durationMs)
            .put("engine", entry.engine ?: JSONObject.NULL)
            .put("transcript", match?.text ?: entry.transcript ?: JSONObject.NULL)
        if (match != null) {
            json.put("route", JSONObject()
                .put("webhook", match.hook.label)
                .put("keyword", match.heard)
                .put("full_transcript", entry.transcript))
        }
        if (audio != null) {
            json.put("audio", JSONObject()
                .put("content_type", "audio/wav")
                .put("sample_rate", 16000)
                .put("data", encode(Wav.encode(audio))))
        }
        return json.toString()
    }

    /** Blocking POST; throws IOException on network errors and non-2xx answers. */
    fun post(url: String, token: String, id: String, body: String, timeoutMs: Int): WebhookResult {
        val conn = URL(url).openConnection() as HttpURLConnection
        conn.connectTimeout = minOf(timeoutMs, 10_000)
        conn.readTimeout = timeoutMs
        conn.requestMethod = "POST"
        conn.doOutput = true
        conn.setRequestProperty("Content-Type", "application/json; charset=utf-8")
        conn.setRequestProperty("Idempotency-Key", id)
        conn.setRequestProperty("User-Agent", "GB-Voice-Bridge")
        if (token.isNotBlank()) conn.setRequestProperty("Authorization", "Bearer ${token.trim()}")
        try {
            conn.outputStream.use { it.write(body.toByteArray(Charsets.UTF_8)) }
            val code = conn.responseCode
            if (code !in 200..299) throw IOException("HTTP $code")
            val text = conn.inputStream.use { it.readBytes().toString(Charsets.UTF_8) }
            return WebhookResult(code, parseReply(text, conn.contentType))
        } finally {
            conn.disconnect()
        }
    }

    /** {"title","text"} JSON, or plain text; null when the server had nothing to say. */
    fun parseReply(body: String, contentType: String?): String? {
        val trimmed = body.trim()
        if (trimmed.isEmpty()) return null
        val looksJson = contentType?.contains("json") == true || trimmed.startsWith("{")
        val text = if (looksJson) {
            val o = runCatching { JSONObject(trimmed) }.getOrNull() ?: return null
            listOf("title", "text").mapNotNull { o.optString(it).takeIf(String::isNotBlank) }.joinToString("\n")
        } else {
            trimmed
        }
        return text.take(MAX_REPLY).ifBlank { null }
    }

    private fun iso(at: Long): String =
        SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss.SSS'Z'", Locale.ROOT)
            .apply { timeZone = TimeZone.getTimeZone("UTC") }
            .format(Date(at))

    private fun base64(bytes: ByteArray) = Base64.encodeToString(bytes, Base64.NO_WRAP)
}

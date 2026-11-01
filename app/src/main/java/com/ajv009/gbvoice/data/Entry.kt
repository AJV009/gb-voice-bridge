package com.ajv009.gbvoice.data

import org.json.JSONObject

/** Where an entry's recording came from. */
enum class Source { WATCH, PHONE_MIC }

/** State of the webhook delivery for one entry. */
enum class Delivery { OFF, SENDING, SENT, FAILED }

/** One dictation: what was heard, what the watch showed, and what happened to the webhook call. */
data class Entry(
    val id: String,
    val at: Long,
    val source: Source,
    val durationMs: Long,
    /** Model id, or null when the phone did not transcribe. */
    val engine: String? = null,
    val transcript: String? = null,
    /** Text shown on the watch (or on the phone for mic tests). */
    val shown: String = "",
    val delivery: Delivery = Delivery.OFF,
    val httpCode: Int = 0,
    val serverReply: String? = null,
    val error: String? = null,
    val hasAudio: Boolean = false,
    val transcribeMs: Long = 0,
) {
    fun toJson(): JSONObject = JSONObject()
        .put("id", id).put("at", at).put("source", source.name).put("durationMs", durationMs)
        .put("engine", engine).put("transcript", transcript).put("shown", shown)
        .put("delivery", delivery.name).put("httpCode", httpCode).put("serverReply", serverReply)
        .put("error", error).put("hasAudio", hasAudio).put("transcribeMs", transcribeMs)

    companion object {
        fun fromJson(o: JSONObject) = Entry(
            id = o.getString("id"),
            at = o.getLong("at"),
            source = Source.valueOf(o.optString("source", Source.WATCH.name)),
            durationMs = o.optLong("durationMs"),
            engine = o.optStringOrNull("engine"),
            transcript = o.optStringOrNull("transcript"),
            shown = o.optString("shown"),
            delivery = Delivery.valueOf(o.optString("delivery", Delivery.OFF.name)),
            httpCode = o.optInt("httpCode"),
            serverReply = o.optStringOrNull("serverReply"),
            error = o.optStringOrNull("error"),
            hasAudio = o.optBoolean("hasAudio"),
            transcribeMs = o.optLong("transcribeMs"),
        )

        private fun JSONObject.optStringOrNull(key: String): String? =
            if (isNull(key)) null else optString(key)
    }
}

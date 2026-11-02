package com.ajv009.gbvoice.routing

import org.json.JSONArray
import org.json.JSONObject
import java.util.UUID

/**
 * A keyword route: a dictation that starts with one of [keywords] (comma-separated, "organizer, organiser")
 * goes to the saved webhook [hookId] instead of the default one, with the keyword taken off the front.
 */
data class Route(
    val id: String = UUID.randomUUID().toString(),
    val keywords: String = "",
    val hookId: String? = null,
    val enabled: Boolean = true,
) {
    /** The keywords to look for. */
    val keywordList: List<String>
        get() = keywords.split(',', ';', '\n').map(String::trim).filter(String::isNotEmpty)

    companion object {
        fun listToJson(list: List<Route>): String = JSONArray(list.map {
            JSONObject().put("id", it.id).put("keywords", it.keywords).put("hook", it.hookId ?: "").put("enabled", it.enabled)
        }).toString()

        fun listFromJson(json: String): List<Route> = runCatching {
            val array = JSONArray(json)
            (0 until array.length()).map {
                val o = array.getJSONObject(it)
                Route(
                    id = o.optString("id").ifBlank { UUID.randomUUID().toString() },
                    keywords = o.optString("keywords"),
                    hookId = o.optString("hook").ifBlank { null },
                    enabled = o.optBoolean("enabled", true),
                )
            }
        }.getOrDefault(emptyList())
    }
}

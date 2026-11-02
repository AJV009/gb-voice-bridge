package com.ajv009.gbvoice.routing

import org.json.JSONArray
import org.json.JSONObject
import java.net.URI
import java.util.UUID

/**
 * A saved webhook: a name, a URL and an optional bearer token (the secret). Keyword routes and the default
 * webhook point at one by [id], so the long URL and secret are entered once and picked by name afterwards.
 */
data class Hook(
    val id: String = UUID.randomUUID().toString(),
    val name: String = "",
    val url: String = "",
    val token: String = "",
) {
    /** The name, or the URL's host when the name is blank. */
    val label: String get() = name.ifBlank { runCatching { URI(url.trim()).host }.getOrNull() ?: url.ifBlank { "Unnamed" } }

    companion object {
        /** The drop-downs' "no webhook" choice; no saved webhook may use a name that reads like it. */
        const val NOTHING = "Nothing"
        private val RESERVED = setOf("nothing", "none", "nowebhook", "noone", "null", "nil", "empty", "na")

        /** Why [name] can't be used (null = fine): blank, reserved, or already used by another webhook. */
        fun nameProblem(name: String, others: List<Hook>): String? {
            val squashed = squash(name)
            return when {
                squashed.isEmpty() -> "Give it a name"
                squashed in RESERVED -> "\"${name.trim()}\" won't work: it reads like \"$NOTHING\", the lists' choice for no webhook"
                others.any { squash(it.name) == squashed } -> "Another webhook already has this name"
                else -> null
            }
        }

        private fun squash(s: String) = s.lowercase().filter(Char::isLetterOrDigit)

        /** [seal] encrypts each token for storage; tokens are never stored in plain text. */
        fun listToJson(list: List<Hook>, seal: (String) -> String): String = JSONArray(list.map {
            JSONObject().put("id", it.id).put("name", it.name).put("url", it.url)
                .put("token", if (it.token.isEmpty()) "" else seal(it.token))
        }).toString()

        /** [open] decrypts a stored token; null (unreadable, e.g. after a restore) becomes no token. */
        fun listFromJson(json: String, open: (String) -> String?): List<Hook> = runCatching {
            val array = JSONArray(json)
            (0 until array.length()).map {
                val o = array.getJSONObject(it)
                Hook(
                    id = o.optString("id").ifBlank { UUID.randomUUID().toString() },
                    name = o.optString("name"),
                    url = o.optString("url"),
                    token = o.optString("token").takeIf(String::isNotEmpty)?.let(open) ?: "",
                )
            }
        }.getOrDefault(emptyList())
    }
}

package com.ajv009.gbvoice.routing

import com.ajv009.gbvoice.data.SettingsState
import java.util.Locale

/** A dictation that starts with a route's keyword. [text] is what follows the keyword, ready to send. */
data class RouteMatch(val route: Route, val hook: Hook, val heard: String, val text: String)

/** Where one dictation goes: a keyword route's webhook, or the default webhook when [match] is null. */
data class Target(val hook: Hook, val match: RouteMatch?)

/**
 * Picks the webhook for a transcript by its first word(s). Speech-to-text punctuates freely, so the match is lenient
 * about everything around the keyword but strict about the keyword itself:
 * - case, and any punctuation before, after or inside it, are ignored: "Organizer, add milk", "organizer. Add milk",
 *   "…Organizer: add milk", "Organizer - add milk" all match "organizer";
 * - spacing too: "to do", "to-do" and "todo" are the same keyword;
 * - up to two filler words in front are skipped: "Hey organizer, …", "Okay, so organizer …";
 * - the keyword must be a whole word: "Organizers" or "Organizing" do not match "organizer" (add them as keywords).
 */
object Router {
    private val WORD = Regex("[\\p{L}\\p{N}]+")
    private val LEADING_JUNK = Regex("^[\\s\\p{P}]+")
    private val FILLERS = setOf("hey", "hi", "hello", "ok", "okay", "um", "umm", "uh", "uhh", "er", "erm", "so", "oh", "yo", "and")
    private const val MAX_FILLERS = 2
    private const val MAX_KEYWORD_WORDS = 4

    fun target(transcript: String?, s: SettingsState): Target? {
        if (s.routesEnabled && transcript != null) {
            match(transcript, s.routes, s.hooks)?.let { return Target(it.hook, it) }
        }
        return s.defaultHook?.let { Target(it, null) }
    }

    /** Routes that are on, have keywords, and point at a saved webhook that still exists. */
    fun usable(routes: List<Route>, hooks: List<Hook>): List<Pair<Route, Hook>> = routes.filter { it.enabled }
        .mapNotNull { r -> hooks.firstOrNull { it.id == r.hookId && it.url.isNotBlank() }?.let { r to it } }
        .filter { it.first.keywordList.isNotEmpty() }

    fun match(transcript: String, routes: List<Route>, hooks: List<Hook>): RouteMatch? {
        val words = WORD.findAll(transcript).take(MAX_FILLERS + MAX_KEYWORD_WORDS).toList()
        // Longest keyword first across all routes, so "to do list" wins over another route's "to do"
        val keywords = usable(routes, hooks)
            .flatMap { (r, h) -> r.keywordList.map { Triple(r, h, squash(it)) } }.sortedByDescending { it.third.length }
        for (skip in 0..minOf(MAX_FILLERS, words.size - 1)) {
            if (skip > 0 && words[skip - 1].value.lowercase(Locale.ROOT) !in FILLERS) break
            for ((route, hook, keyword) in keywords) {
                val end = matchAt(words, skip, keyword) ?: continue
                val rest = transcript.substring(words[end].range.last + 1).replace(LEADING_JUNK, "")
                val heard = transcript.substring(words[skip].range.first, words[end].range.last + 1)
                return RouteMatch(route, hook, heard, rest.replaceFirstChar { it.titlecase(Locale.ROOT) })
            }
        }
        return null
    }

    /** Index of the last word when the words from [start] spell [want] exactly, else null. */
    private fun matchAt(words: List<MatchResult>, start: Int, want: String): Int? {
        if (want.isEmpty()) return null
        var have = ""
        for (i in start until minOf(words.size, start + MAX_KEYWORD_WORDS)) {
            have += words[i].value.lowercase(Locale.ROOT)
            if (have == want) return i
            if (!want.startsWith(have)) return null
        }
        return null
    }

    /** "To-Do list" -> "todolist": lower case, letters and digits only. */
    private fun squash(keyword: String) = WORD.findAll(keyword.lowercase(Locale.ROOT)).joinToString("") { it.value }
}

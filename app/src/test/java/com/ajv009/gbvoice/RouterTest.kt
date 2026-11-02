package com.ajv009.gbvoice

import com.ajv009.gbvoice.data.Entry
import com.ajv009.gbvoice.data.SettingsState
import com.ajv009.gbvoice.data.Source
import com.ajv009.gbvoice.routing.Hook
import com.ajv009.gbvoice.routing.Route
import com.ajv009.gbvoice.routing.Router
import com.ajv009.gbvoice.webhook.Webhook
import org.json.JSONObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Test

/** Keyword routing: lenient about punctuation, case and fillers around the keyword, strict about the word. */
class RouterTest {
    private val org = Hook(id = "org", name = "Organizer", url = "https://org", token = "t1")
    private val todoHook = Hook(id = "todo", name = "Todo", url = "https://todo")
    private val listHook = Hook(id = "list", name = "List", url = "https://list")
    private val hooks = listOf(org, todoHook, listHook)
    private val routes = listOf(
        Route(id = "r1", keywords = "organizer, organiser", hookId = "org"),
        Route(id = "r2", keywords = "to do", hookId = "todo"),
        Route(id = "r3", keywords = "to do list", hookId = "list"),
    )

    private fun text(input: String) = Router.match(input, routes, hooks)?.text
    private fun name(input: String) = Router.match(input, routes, hooks)?.hook?.name

    @Test
    fun punctuationAndCaseAroundTheKeyword() {
        listOf(
            "Organizer, add milk to Q1.", "organizer add milk to Q1.", "Organizer. Add milk to Q1.",
            "ORGANIZER: add milk to Q1.", "Organizer - add milk to Q1.", "Organizer... add milk to Q1.",
            "  \"Organizer,\" add milk to Q1.", "...organizer; add milk to Q1.", "Organizer — add milk to Q1.",
            "Organizer!? Add milk to Q1.", "Organiser, add milk to Q1.",
        ).forEach { assertEquals(it, "Add milk to Q1.", text(it)) }
    }

    @Test
    fun fillersInFrontAreSkipped() {
        assertEquals("Add milk", text("Hey organizer, add milk"))
        assertEquals("Add milk", text("Okay, so organizer. Add milk"))
        assertEquals("Add milk", text("Um, organizer, add milk"))
        assertNull(text("Hey hey hey organizer add milk")) // at most two fillers
        assertNull(text("Please organizer add milk"))      // not a filler
    }

    @Test
    fun theWordItselfMustMatch() {
        assertNull(text("Organizers, add milk"))
        assertNull(text("Organizing the shelf"))
        assertNull(text("Add milk, organizer"))
        assertNull(text("The organizer should add milk"))
        assertNull(text(""))
        assertNull(text("..."))
    }

    @Test
    fun multiWordKeywordsAndLongestWins() {
        assertEquals("Todo", name("To-do, buy bread"))
        assertEquals("Buy bread", text("Todo: buy bread"))
        assertEquals("List", name("To do list. Buy bread"))
        assertEquals("Buy bread", text("To do list. Buy bread"))
        assertEquals("Todo", name("To do. Lists are great"))
    }

    @Test
    fun keywordAloneGivesEmptyText() {
        assertEquals("", text("Organizer."))
        assertEquals("Organizer", Router.match("Organizer.", routes, hooks)?.heard)
    }

    @Test
    fun routesThatAreOffOrPointNowhereAreIgnored() {
        val r = routes.first()
        assertNull(Router.match("Organizer, add milk", listOf(r.copy(enabled = false)), hooks))
        assertNull(Router.match("Organizer, add milk", listOf(r.copy(hookId = null)), hooks))      // "Nothing"
        assertNull(Router.match("Organizer, add milk", listOf(r.copy(hookId = "gone")), hooks))    // deleted webhook
        assertNull(Router.match("Organizer, add milk", listOf(r), listOf(org.copy(url = ""))))
    }

    @Test
    fun targetFallsBackToTheDefaultWebhook() {
        val def = Hook(id = "def", name = "Default", url = "https://default")
        val s = SettingsState(hooks = hooks + def, defaultHookId = "def", routesEnabled = true, routes = routes)
        assertEquals("https://org", Router.target("Organizer, add milk", s)?.hook?.url)
        assertEquals("t1", Router.target("Organizer, add milk", s)?.hook?.token)
        assertEquals("https://default", Router.target("Add milk", s)?.hook?.url)
        assertNull(Router.target("Add milk", s)?.match)
        assertEquals("https://default", Router.target("Organizer, add milk", s.copy(routesEnabled = false))?.hook?.url)
        assertNull(Router.target("Add milk", s.copy(defaultHookId = null)))
        assertNull(Router.target("Add milk", s.copy(defaultHookId = "gone")))
        assertEquals("https://org", Router.target("Organizer, add milk", s.copy(defaultHookId = null))?.hook?.url)
        assertNull(Router.target(null, s.copy(defaultHookId = null)))
    }

    @Test
    fun routedPayloadCarriesTheStrippedTextAndTheOriginal() {
        val entry = Entry("id", 0, Source.WATCH, 1000, transcript = "Organizer, add milk")
        val json = JSONObject(Webhook.payload(entry, null, Router.match(entry.transcript!!, routes, hooks)))
        assertEquals("Add milk", json.getString("transcript"))
        assertEquals("Organizer", json.getJSONObject("route").getString("webhook"))
        assertEquals("Organizer", json.getJSONObject("route").getString("keyword"))
        assertEquals("Organizer, add milk", json.getJSONObject("route").getString("full_transcript"))
        assertEquals(false, JSONObject(Webhook.payload(entry, null)).has("route"))
    }

    @Test
    fun hooksAndRoutesSurviveJsonWithSealedTokens() {
        val json = Hook.listToJson(hooks) { "sealed:$it" }
        assertEquals(true, json.contains("sealed:t1"))
        assertEquals(false, json.contains("\"t1\""))
        assertEquals(hooks, Hook.listFromJson(json) { it.removePrefix("sealed:") })
        assertEquals("", Hook.listFromJson(json) { null }.first().token) // unreadable token -> none
        assertEquals(routes + Route(id = "n", keywords = "x"), Route.listFromJson(Route.listToJson(routes + Route(id = "n", keywords = "x"))))
    }

    @Test
    fun webhookNamesThatReadLikeNothingAreRefused() {
        listOf("Nothing", " nothing ", "NONE", "No webhook", "no-webhook", "N/A", "null", "").forEach {
            assertNotNull(it, Hook.nameProblem(it, emptyList()))
        }
        assertNotNull(Hook.nameProblem("organizer", hooks))    // duplicate, any case
        assertNull(Hook.nameProblem("Gmail", hooks))
        assertNull(Hook.nameProblem("Nothing Phone", hooks))   // only the whole name is reserved
        assertEquals("l3400.example.net", Hook(url = "https://l3400.example.net:8805/api/voice").label)
    }
}

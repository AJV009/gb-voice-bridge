package com.ajv009.gbvoice

import com.ajv009.gbvoice.audio.OpusFrames
import com.ajv009.gbvoice.audio.Wav
import com.ajv009.gbvoice.data.Entry
import com.ajv009.gbvoice.data.Replacement
import com.ajv009.gbvoice.data.SettingsState
import com.ajv009.gbvoice.data.Source
import com.ajv009.gbvoice.webhook.Webhook
import org.json.JSONObject
import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File

/** Plain-JVM tests for the parts that do not need a phone. */
class LogicTest {
    @Test
    fun replacementsMatchWholeWordsAnyCase() {
        val rules = listOf(Replacement("al fonts", "Alphons"), Replacement("art", "ART"))
        assertEquals("Hi Alphons, start ART", Replacement.apply("Hi Al Fonts, start art", rules))
    }

    @Test
    fun replacementsKeepDollarSignsLiteral() {
        assertEquals("costs $5", Replacement.apply("costs five", listOf(Replacement("five", "$5"))))
    }

    @Test
    fun replacementsSurviveJson() {
        val rules = listOf(Replacement("a \"b\"", "c"))
        assertEquals(rules, Replacement.listFromJson(Replacement.listToJson(rules)))
        assertEquals(emptyList<Replacement>(), Replacement.listFromJson("not json"))
    }

    @Test
    fun audioIsSentWhenAskedOrWhenThePhoneDoesNotTranscribe() {
        assertFalse(SettingsState(webhookEnabled = true).sendsAudio)
        assertTrue(SettingsState(webhookEnabled = true, webhookSendAudio = true).sendsAudio)
        assertTrue(SettingsState(webhookEnabled = true, transcribeOnPhone = false).sendsAudio)
        assertFalse(SettingsState(transcribeOnPhone = false).isUsable)
        assertTrue(SettingsState(transcribeOnPhone = false, webhookEnabled = true, webhookUrl = "https://x").isUsable)
    }

    @Test
    fun payloadHasTheDocumentedFields() {
        val entry = Entry("id-1", 0, Source.WATCH, 14_000, engine = "whisper-small.en", transcript = "hello")
        val json = JSONObject(Webhook.payload(entry, FloatArray(16)) { "B64:${it.size}" })
        assertEquals("gbvoice/1", json.getString("format"))
        assertEquals("1970-01-01T00:00:00.000Z", json.getString("recorded_at"))
        assertEquals("watch", json.getString("source"))
        assertEquals("hello", json.getString("transcript"))
        assertEquals("B64:76", json.getJSONObject("audio").getString("data")) // 44-byte header + 16 samples * 2
        assertTrue(JSONObject(Webhook.payload(entry.copy(transcript = null), null)).isNull("transcript"))
    }

    @Test
    fun replyParsing() {
        assertEquals("Added\nMilk", Webhook.parseReply("""{"title":"Added","text":"Milk"}""", "application/json"))
        assertEquals("ok", Webhook.parseReply(" ok \n", "text/plain"))
        assertNull(Webhook.parseReply("", null))
        assertNull(Webhook.parseReply("{\"other\":1}", "application/json"))
    }

    @Test
    fun entrySurvivesJson() {
        val e = Entry("x", 5, Source.PHONE_MIC, 1000, transcript = null, error = "boom", hasAudio = true)
        assertEquals(e, Entry.fromJson(e.toJson()))
    }

    @Test
    fun framesRoundTripThroughAFile() {
        val file = File.createTempFile("frames", ".bin")
        val frames = listOf(byteArrayOf(1, 2, 3), ByteArray(300) { it.toByte() }, byteArrayOf())
        OpusFrames.write(file, frames)
        val back = OpusFrames.read(file)
        assertEquals(frames.size, back.size)
        frames.indices.forEach { assertArrayEquals(frames[it], back[it]) }
        file.delete()
    }

    @Test
    fun wavHeader() {
        val wav = Wav.encode(floatArrayOf(0f, 1f, -1f))
        assertEquals("RIFF", String(wav, 0, 4))
        assertEquals(44 + 6, wav.size)
    }
}

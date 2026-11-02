package com.ajv009.gbvoice.pipeline

import android.content.Context
import android.util.Log
import com.ajv009.gbvoice.asr.ModelCatalog
import com.ajv009.gbvoice.asr.Recognizer
import com.ajv009.gbvoice.audio.OpusFrames
import com.ajv009.gbvoice.data.Delivery
import com.ajv009.gbvoice.data.Entry
import com.ajv009.gbvoice.data.HistoryStore
import com.ajv009.gbvoice.data.Replacement
import com.ajv009.gbvoice.data.Settings
import com.ajv009.gbvoice.data.SettingsState
import com.ajv009.gbvoice.data.Source
import com.ajv009.gbvoice.routing.Router
import com.ajv009.gbvoice.routing.Target
import com.ajv009.gbvoice.webhook.Webhook
import com.ajv009.gbvoice.webhook.WebhookResult
import java.util.UUID
import java.util.concurrent.Callable
import java.util.concurrent.ExecutionException
import java.util.concurrent.Executors
import java.util.concurrent.TimeUnit
import java.util.concurrent.TimeoutException

/** Text for the watch; [error] makes Gadgetbridge show it as an error. */
data class WatchText(val text: String, val error: Boolean = false)

/**
 * One recording in, one line for the watch out: transcribe (unless the user sends audio only), apply word
 * replacements, pick the webhook (a keyword route or the default, see [Router]), call it, and record everything.
 */
class Pipeline(private val context: Context, private val settings: Settings, private val history: HistoryStore) {
    private val network = Executors.newCachedThreadPool()

    /** Blocking; run on a worker thread. [endedAt] is when the watch stopped recording. */
    fun processWatch(frames: List<ByteArray>, endedAt: Long): WatchText {
        val s = settings.current
        val samples = runCatching { OpusFrames.decode(frames) }.getOrElse {
            Log.e(TAG, "Opus decode failed", it)
            return WatchText("Could not decode the watch audio", error = true)
        }
        val durationMs = samples.size * 1000L / OpusFrames.SAMPLE_RATE
        val id = UUID.randomUUID().toString()
        val keep = s.keepAudio && frames.isNotEmpty()
        if (keep) OpusFrames.write(history.audioFile(id), frames)
        val entry = Entry(id, endedAt - durationMs, Source.WATCH, durationMs, hasAudio = keep)
        return finish(transcribe(entry, samples, s), samples, s, endedAt + REPLY_BUDGET_MS)
    }

    /** Phone-mic test: transcribe with the selected model and log it; never calls the webhook. */
    fun processMic(samples: FloatArray): Entry {
        val s = settings.current.copy(transcribeOnPhone = true)
        val durationMs = samples.size * 1000L / OpusFrames.SAMPLE_RATE
        val entry = transcribe(Entry(UUID.randomUUID().toString(), System.currentTimeMillis(), Source.PHONE_MIC, durationMs), samples, s)
        val done = entry.copy(shown = entry.transcript?.ifEmpty { NOTHING_HEARD } ?: entry.error.orEmpty())
        history.add(done)
        return done
    }

    /** Sends an entry again, routed by today's settings (with its stored audio when audio is sent). Blocking. */
    fun resend(id: String) {
        val entry = history.get(id) ?: return
        val s = settings.current
        val target = Router.target(entry.transcript, s) ?: return
        val audio = if (sendsAudio(s) && entry.hasAudio) {
            runCatching { OpusFrames.decode(OpusFrames.read(history.audioFile(id))) }.getOrNull()
        } else null
        history.update(id) { it.copy(delivery = Delivery.SENDING, error = null, route = target.match?.hook?.label) }
        runCatching { deliver(entry, audio, target) }
    }

    /** Audio goes along when the user asked for it or when the phone does not transcribe. */
    private fun sendsAudio(s: SettingsState) = s.webhookSendAudio || !s.transcribeOnPhone

    private fun transcribe(entry: Entry, samples: FloatArray, s: SettingsState): Entry {
        if (!s.transcribeOnPhone) return entry
        val model = ModelCatalog.byId(s.modelId)
            ?: return entry.copy(error = "No speech model selected")
        return try {
            val start = System.currentTimeMillis()
            val raw = Recognizer.transcribe(context, model, samples)
            entry.copy(
                engine = model.id,
                transcript = Replacement.apply(raw, s.replacements),
                transcribeMs = System.currentTimeMillis() - start,
            )
        } catch (e: Exception) {
            Log.e(TAG, "Transcription failed", e)
            entry.copy(engine = model.id, error = "Transcription failed: ${e.message}")
        }
    }

    private fun finish(entry: Entry, samples: FloatArray, s: SettingsState, deadline: Long): WatchText {
        val transcript = entry.transcript?.ifBlank { null }
        val target = Router.target(entry.transcript, s)
        if (target == null) {
            val shown = when {
                transcript != null -> transcript
                entry.transcript != null -> NOTHING_HEARD
                else -> entry.error ?: "Turn on transcription or the webhook in GB Voice Bridge"
            }
            history.add(entry.copy(shown = shown))
            return WatchText(shown, error = entry.transcript == null)
        }
        val sending = entry.copy(delivery = Delivery.SENDING, route = target.match?.hook?.label)
        history.add(sending)
        val audio = if (sendsAudio(s)) samples else null
        val call = network.submit(Callable { deliver(sending, audio, target) })
        val shown = try {
            val result = call.get(maxOf(deadline - System.currentTimeMillis(), 0), TimeUnit.MILLISECONDS)
            WatchText(result.reply ?: transcript ?: "Sent")
        } catch (_: TimeoutException) {
            WatchText(transcript ?: "Sent; the server is still working")
        } catch (e: ExecutionException) {
            val why = e.cause?.message ?: "error"
            val what = target.match?.hook?.label ?: "webhook"
            if (transcript != null) WatchText("$transcript\n($what failed: $why)") else WatchText("Webhook failed: $why", true)
        }
        history.update(entry.id) { it.copy(shown = shown.text) }
        return shown
    }

    /** POSTs and records the outcome; the call may outlive the watch's wait, the history still gets it. */
    private fun deliver(entry: Entry, audio: FloatArray?, target: Target): WebhookResult {
        try {
            val body = Webhook.payload(entry, audio, target.match)
            val result = Webhook.post(target.hook.url, target.hook.token, entry.id, body, WEBHOOK_TIMEOUT_MS)
            history.update(entry.id) {
                it.copy(delivery = Delivery.SENT, httpCode = result.code, serverReply = result.reply, error = null)
            }
            return result
        } catch (e: Exception) {
            Log.w(TAG, "Webhook failed: ${e.message}")
            history.update(entry.id) { it.copy(delivery = Delivery.FAILED, error = "Webhook: ${e.message}") }
            throw e
        }
    }

    private companion object {
        const val TAG = "GbVoicePipeline"
        /** Gadgetbridge gives up 15 s after the recording ends; answer a little before that. */
        const val REPLY_BUDGET_MS = 12_500L
        const val WEBHOOK_TIMEOUT_MS = 60_000
        const val NOTHING_HEARD = "(nothing heard)"
    }
}

package com.ajv009.gbvoice.asr

import android.content.Context
import android.util.Log
import com.ajv009.gbvoice.audio.OpusFrames
import com.k2fsa.sherpa.onnx.OfflineModelConfig
import com.k2fsa.sherpa.onnx.OfflineRecognizer
import com.k2fsa.sherpa.onnx.OfflineRecognizerConfig
import com.k2fsa.sherpa.onnx.OfflineTransducerModelConfig
import com.k2fsa.sherpa.onnx.OfflineWhisperModelConfig
import java.util.concurrent.Executors
import java.util.concurrent.ScheduledFuture
import java.util.concurrent.TimeUnit

/**
 * Keeps one model loaded (loading takes seconds) and turns 16 kHz samples into text. The model holds hundreds of
 * MB of RAM, so it is unloaded [IDLE_MS] after the last use; the voice service preloads it again on the next session.
 */
object Recognizer {
    private const val TAG = "GbVoiceStt"
    private const val IDLE_MS = 120_000L
    private var loadedId: String? = null
    private var recognizer: OfflineRecognizer? = null
    private val idle = Executors.newSingleThreadScheduledExecutor { r -> Thread(r, "stt-idle").apply { isDaemon = true } }
    private var unload: ScheduledFuture<*>? = null

    /** (Re)starts the idle timer; called after every load and transcription. */
    @Synchronized
    private fun touch() {
        unload?.cancel(false)
        unload = idle.schedule({ release(); Log.i(TAG, "Model unloaded after idle") }, IDLE_MS, TimeUnit.MILLISECONDS)
    }

    /** Loads [model] unless it is already loaded; slow, so call it off the main thread and early. */
    @Synchronized
    fun load(context: Context, model: SpeechModel): OfflineRecognizer {
        recognizer?.takeIf { loadedId == model.id }?.let { touch(); return it }
        check(model.isReady(context)) { "${model.title} is not downloaded" }
        release()
        val start = System.currentTimeMillis()
        val path = { f: ModelFile -> model.file(context, f).path }
        val config = OfflineModelConfig().apply {
            tokens = path(model.tokens)
            numThreads = 4
            when (model.family) {
                Family.WHISPER -> {
                    whisper = OfflineWhisperModelConfig().apply {
                        encoder = path(model.encoder)
                        decoder = path(model.decoder)
                        language = "en"
                        task = "transcribe"
                    }
                    modelType = "whisper"
                }
                Family.PARAKEET -> {
                    transducer = OfflineTransducerModelConfig().apply {
                        encoder = path(model.encoder)
                        decoder = path(model.decoder)
                        joiner = path(model.joiner!!)
                    }
                    modelType = "nemo_transducer"
                }
            }
        }
        val created = OfflineRecognizer(null, OfflineRecognizerConfig().apply {
            modelConfig = config
            decodingMethod = "greedy_search"
        })
        Log.i(TAG, "${model.id} loaded in ${System.currentTimeMillis() - start} ms")
        recognizer = created
        loadedId = model.id
        touch()
        return created
    }

    fun transcribe(context: Context, model: SpeechModel, samples: FloatArray): String {
        val stt = load(context, model)
        val start = System.currentTimeMillis()
        val stream = stt.createStream()
        try {
            stream.acceptWaveform(samples, OpusFrames.SAMPLE_RATE)
            stt.decode(stream)
            val text = stt.getResult(stream).text.trim()
            touch()
            Log.i(TAG, "%s: %.1fs audio -> %d ms".format(model.id, samples.size / 16000f, System.currentTimeMillis() - start))
            return text
        } finally {
            stream.release()
        }
    }

    @Synchronized
    fun release() {
        recognizer?.release()
        recognizer = null
        loadedId = null
    }
}

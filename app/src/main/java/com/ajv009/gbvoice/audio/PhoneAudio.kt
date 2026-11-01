package com.ajv009.gbvoice.audio

import android.annotation.SuppressLint
import android.media.AudioAttributes
import android.media.AudioFormat
import android.media.AudioRecord
import android.media.AudioTrack
import android.media.MediaRecorder
import kotlin.concurrent.thread

/** Records up to [MAX_SECONDS] from the phone mic at 16 kHz, to test an engine without the watch. */
class MicRecorder {
    @Volatile
    private var running = false
    private var worker: Thread? = null

    /** Caller must hold RECORD_AUDIO. [onDone] runs on a background thread with the recorded samples. */
    @SuppressLint("MissingPermission")
    fun start(onDone: (FloatArray) -> Unit) {
        if (running) return
        running = true
        worker = thread(name = "mic-test") {
            val rate = OpusFrames.SAMPLE_RATE
            val minBuf = AudioRecord.getMinBufferSize(rate, AudioFormat.CHANNEL_IN_MONO, AudioFormat.ENCODING_PCM_FLOAT)
            val record = AudioRecord(
                MediaRecorder.AudioSource.VOICE_RECOGNITION, rate,
                AudioFormat.CHANNEL_IN_MONO, AudioFormat.ENCODING_PCM_FLOAT, maxOf(minBuf, rate),
            )
            val out = FloatArray(rate * MAX_SECONDS)
            var size = 0
            try {
                record.startRecording()
                while (running && size < out.size) {
                    val n = record.read(out, size, minOf(rate / 10, out.size - size), AudioRecord.READ_BLOCKING)
                    if (n <= 0) break
                    size += n
                }
            } finally {
                record.release()
                running = false
            }
            onDone(out.copyOf(size))
        }
    }

    fun stop() {
        running = false
    }

    companion object {
        const val MAX_SECONDS = 15
    }
}

/** Plays one clip at a time on the phone speaker. */
object Player {
    private var track: AudioTrack? = null

    @Synchronized
    fun play(samples: FloatArray, sampleRate: Int = OpusFrames.SAMPLE_RATE) {
        stop()
        if (samples.isEmpty()) return
        val t = AudioTrack.Builder()
            .setAudioAttributes(AudioAttributes.Builder().setUsage(AudioAttributes.USAGE_MEDIA).build())
            .setAudioFormat(
                AudioFormat.Builder().setEncoding(AudioFormat.ENCODING_PCM_FLOAT)
                    .setSampleRate(sampleRate).setChannelMask(AudioFormat.CHANNEL_OUT_MONO).build()
            )
            .setTransferMode(AudioTrack.MODE_STATIC)
            .setBufferSizeInBytes(samples.size * 4)
            .build()
        t.write(samples, 0, samples.size, AudioTrack.WRITE_BLOCKING)
        t.play()
        track = t
    }

    @Synchronized
    fun stop() {
        track?.run {
            runCatching { stop() }
            release()
        }
        track = null
    }
}

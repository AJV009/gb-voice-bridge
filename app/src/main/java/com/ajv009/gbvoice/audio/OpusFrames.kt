package com.ajv009.gbvoice.audio

import org.concentus.OpusDecoder
import java.io.ByteArrayOutputStream
import java.io.DataInputStream
import java.io.DataOutputStream
import java.io.EOFException
import java.io.File
import java.nio.ByteBuffer
import java.nio.ByteOrder

/** The watch sends 16 kHz mono Opus, one frame per message. Helpers to decode, store and convert it. */
object OpusFrames {
    const val SAMPLE_RATE = 16000
    private const val MAX_FRAME = 6 * 960

    /** Decodes Opus frames to mono float PCM in [-1, 1]. */
    fun decode(frames: List<ByteArray>): FloatArray {
        val decoder = OpusDecoder(SAMPLE_RATE, 1)
        val pcm = ShortArray(MAX_FRAME)
        var out = FloatArray(SAMPLE_RATE * 10)
        var size = 0
        for (frame in frames) {
            val n = decoder.decode(frame, 0, frame.size, pcm, 0, MAX_FRAME, false)
            if (size + n > out.size) out = out.copyOf(maxOf(out.size * 2, size + n))
            for (i in 0 until n) out[size++] = pcm[i] / 32768f
        }
        return out.copyOf(size)
    }

    /** Stores frames as [u16 length][bytes]... so they can be played or resent later. */
    fun write(file: File, frames: List<ByteArray>) {
        DataOutputStream(file.outputStream().buffered()).use { out ->
            for (frame in frames) {
                out.writeShort(frame.size)
                out.write(frame)
            }
        }
    }

    fun read(file: File): List<ByteArray> {
        val frames = ArrayList<ByteArray>()
        DataInputStream(file.inputStream().buffered()).use { input ->
            while (true) {
                val size = try {
                    input.readUnsignedShort()
                } catch (_: EOFException) {
                    break
                }
                frames += ByteArray(size).also { input.readFully(it) }
            }
        }
        return frames
    }
}

/** 16-bit PCM WAV, the most widely readable format for a webhook server. */
object Wav {
    fun encode(samples: FloatArray, sampleRate: Int = OpusFrames.SAMPLE_RATE): ByteArray {
        val data = ByteBuffer.allocate(samples.size * 2).order(ByteOrder.LITTLE_ENDIAN)
        for (s in samples) data.putShort((s.coerceIn(-1f, 1f) * Short.MAX_VALUE).toInt().toShort())
        val header = ByteBuffer.allocate(44).order(ByteOrder.LITTLE_ENDIAN).apply {
            put("RIFF".toByteArray()); putInt(36 + data.capacity()); put("WAVE".toByteArray())
            put("fmt ".toByteArray()); putInt(16); putShort(1); putShort(1)
            putInt(sampleRate); putInt(sampleRate * 2); putShort(2); putShort(16)
            put("data".toByteArray()); putInt(data.capacity())
        }
        return ByteArrayOutputStream(44 + data.capacity()).apply {
            write(header.array()); write(data.array())
        }.toByteArray()
    }
}

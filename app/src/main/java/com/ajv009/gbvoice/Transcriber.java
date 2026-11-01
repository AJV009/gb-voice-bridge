package com.ajv009.gbvoice;

import android.content.Context;
import android.util.Log;

import com.k2fsa.sherpa.onnx.OfflineModelConfig;
import com.k2fsa.sherpa.onnx.OfflineRecognizer;
import com.k2fsa.sherpa.onnx.OfflineRecognizerConfig;
import com.k2fsa.sherpa.onnx.OfflineStream;
import com.k2fsa.sherpa.onnx.OfflineWhisperModelConfig;

import org.concentus.OpusDecoder;
import org.concentus.OpusException;

import java.util.List;

/** Opus frames from the watch -> text, fully on the phone (Whisper small.en via sherpa-onnx). */
final class Transcriber {
    private static final String TAG = "GbVoiceStt";
    static final int SAMPLE_RATE = 16000;
    private static final int MAX_FRAME = 6 * 960;

    private static OfflineRecognizer recognizer;

    private Transcriber() {
    }

    /** Loads the model once per process; slow (seconds), so call it early and off the main thread. */
    static synchronized OfflineRecognizer load(final Context context) {
        if (recognizer != null) {
            return recognizer;
        }
        final ModelStore store = new ModelStore(context);
        if (!store.isReady()) {
            throw new IllegalStateException("model not downloaded");
        }
        final OfflineWhisperModelConfig whisper = new OfflineWhisperModelConfig();
        whisper.setEncoder(store.file(ModelStore.ENCODER).getPath());
        whisper.setDecoder(store.file(ModelStore.DECODER).getPath());
        whisper.setLanguage("en");
        whisper.setTask("transcribe");

        final OfflineModelConfig model = new OfflineModelConfig();
        model.setWhisper(whisper);
        model.setTokens(store.file(ModelStore.TOKENS).getPath());
        model.setModelType("whisper");
        model.setNumThreads(4);

        final OfflineRecognizerConfig config = new OfflineRecognizerConfig();
        config.setModelConfig(model);
        config.setDecodingMethod("greedy_search");

        final long start = System.currentTimeMillis();
        recognizer = new OfflineRecognizer(null, config);
        Log.i(TAG, "Model loaded in " + (System.currentTimeMillis() - start) + " ms");
        return recognizer;
    }

    static String transcribe(final Context context, final List<byte[]> frames) throws OpusException {
        final float[] samples = decode(frames);
        final OfflineRecognizer stt = load(context);
        final long start = System.currentTimeMillis();
        final OfflineStream stream = stt.createStream();
        try {
            stream.acceptWaveform(samples, SAMPLE_RATE);
            stt.decode(stream);
            final String text = stt.getResult(stream).getText().trim();
            Log.i(TAG, String.format("%.1fs audio -> %d ms: %s",
                    samples.length / (float) SAMPLE_RATE, System.currentTimeMillis() - start, text));
            return text;
        } finally {
            stream.release();
        }
    }

    /** Decodes Opus frames to mono float PCM in [-1, 1]. */
    static float[] decode(final List<byte[]> frames) throws OpusException {
        final OpusDecoder decoder = new OpusDecoder(SAMPLE_RATE, 1);
        final short[] pcm = new short[MAX_FRAME];
        float[] out = new float[SAMPLE_RATE * 10];
        int size = 0;
        for (final byte[] frame : frames) {
            final int n = decoder.decode(frame, 0, frame.length, pcm, 0, MAX_FRAME, false);
            if (size + n > out.length) {
                out = java.util.Arrays.copyOf(out, Math.max(out.length * 2, size + n));
            }
            for (int i = 0; i < n; i++) {
                out[size++] = pcm[i] / 32768f;
            }
        }
        return java.util.Arrays.copyOf(out, size);
    }
}

package com.ajv009.gbvoice;

import android.content.Context;

import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.HttpURLConnection;
import java.net.URL;

/** Downloads and locates the Whisper small.en (int8) model files in app-private storage. */
final class ModelStore {
    private static final String BASE =
            "https://huggingface.co/csukuangfj/sherpa-onnx-whisper-small.en/resolve/main/";
    static final String ENCODER = "small.en-encoder.int8.onnx";
    static final String DECODER = "small.en-decoder.int8.onnx";
    static final String TOKENS = "small.en-tokens.txt";
    private static final String[] FILES = {TOKENS, ENCODER, DECODER};

    interface Progress {
        void onProgress(String file, long done, long total);
    }

    private final File dir;

    ModelStore(final Context context) {
        dir = new File(context.getFilesDir(), "whisper-small.en");
    }

    File file(final String name) {
        return new File(dir, name);
    }

    boolean isReady() {
        for (final String name : FILES) {
            if (!file(name).isFile()) {
                return false;
            }
        }
        return true;
    }

    /** Blocking; run off the main thread. Files are written to .part and renamed when complete. */
    void download(final Progress progress) throws IOException {
        if (!dir.isDirectory() && !dir.mkdirs()) {
            throw new IOException("cannot create " + dir);
        }
        for (final String name : FILES) {
            if (!file(name).isFile()) {
                fetch(name, progress);
            }
        }
    }

    private void fetch(final String name, final Progress progress) throws IOException {
        final File part = new File(dir, name + ".part");
        final HttpURLConnection conn = (HttpURLConnection) new URL(BASE + name).openConnection();
        conn.setInstanceFollowRedirects(true);
        conn.setConnectTimeout(15_000);
        conn.setReadTimeout(60_000);
        try {
            if (conn.getResponseCode() != 200) {
                throw new IOException(name + ": HTTP " + conn.getResponseCode());
            }
            final long total = conn.getContentLengthLong();
            try (InputStream in = conn.getInputStream(); OutputStream out = new FileOutputStream(part)) {
                final byte[] buf = new byte[1 << 16];
                long done = 0;
                int n;
                while ((n = in.read(buf)) > 0) {
                    out.write(buf, 0, n);
                    done += n;
                    progress.onProgress(name, done, total);
                }
            }
            if (!part.renameTo(file(name))) {
                throw new IOException("cannot finish " + name);
            }
        } finally {
            conn.disconnect();
        }
    }
}

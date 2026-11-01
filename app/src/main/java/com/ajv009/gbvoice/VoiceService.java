package com.ajv009.gbvoice;

import android.app.Service;
import android.content.Context;
import android.content.Intent;
import android.os.Handler;
import android.os.IBinder;
import android.os.Looper;
import android.os.Message;
import android.os.Messenger;
import android.os.RemoteException;
import android.util.Log;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

/**
 * Bound service Gadgetbridge talks to during a watch voice session. Collects the Opus frames,
 * transcribes them on the phone when the watch stops recording, and replies with the text,
 * which Gadgetbridge shows on the watch.
 */
public class VoiceService extends Service {
    private static final String TAG = "GbVoiceService";

    // Contract with Gadgetbridge (ZeppOsVoiceServiceBridge)
    static final int MSG_VOICE_DATA = 0;
    static final int MSG_VOICE_END = 1;
    static final int MSG_REPLY = 100;
    static final int MSG_ERROR = 101;

    /** Only Gadgetbridge builds may send us audio. */
    private static final String GB_PACKAGE_PREFIX = "nodomain.freeyourgadget.gadgetbridge";
    static final String PREFS = "transcripts";

    private final ExecutorService worker = Executors.newSingleThreadExecutor();
    private final List<byte[]> frames = new ArrayList<>();
    private final Messenger messenger = new Messenger(new Handler(Looper.getMainLooper(), this::handle));

    @Override
    public IBinder onBind(final Intent intent) {
        Log.i(TAG, "Bound for a voice session");
        frames.clear();
        // Load the model while the user is still speaking
        worker.execute(() -> {
            try {
                Transcriber.load(this);
            } catch (final RuntimeException e) {
                Log.w(TAG, "Model not loaded: " + e.getMessage());
            }
        });
        return messenger.getBinder();
    }

    @Override
    public void onDestroy() {
        worker.shutdown();
        super.onDestroy();
    }

    private boolean handle(final Message msg) {
        if (!fromGadgetbridge(msg)) {
            Log.w(TAG, "Ignoring message from uid " + msg.sendingUid);
            return true;
        }
        switch (msg.what) {
            case MSG_VOICE_DATA:
                final byte[] frame = msg.getData().getByteArray("VOICE_DATA");
                if (frame != null) {
                    frames.add(frame);
                }
                return true;
            case MSG_VOICE_END:
                final List<byte[]> session = new ArrayList<>(frames);
                frames.clear();
                final Messenger replyTo = msg.replyTo;
                Log.i(TAG, "Voice ended, " + session.size() + " frames");
                worker.execute(() -> reply(replyTo, transcribe(session)));
                return true;
            default:
                return false;
        }
    }

    private Message transcribe(final List<byte[]> session) {
        try {
            final String text = Transcriber.transcribe(this, session);
            remember(this, text);
            final Message reply = Message.obtain(null, MSG_REPLY);
            reply.getData().putString("TEXT", text.isEmpty() ? "(nothing heard)" : text);
            return reply;
        } catch (final Exception e) {
            Log.e(TAG, "Transcription failed", e);
            final Message error = Message.obtain(null, MSG_ERROR);
            error.getData().putString("TEXT", "Voice bridge: " + e.getMessage());
            return error;
        }
    }

    private static void reply(final Messenger replyTo, final Message reply) {
        if (replyTo == null) {
            return;
        }
        try {
            replyTo.send(reply);
        } catch (final RemoteException e) {
            Log.e(TAG, "Gadgetbridge went away before the reply", e);
        }
    }

    /** Keeps the last transcript for the app screen. */
    private static void remember(final Context context, final String text) {
        context.getSharedPreferences(PREFS, MODE_PRIVATE).edit()
                .putString("last", text)
                .putLong("at", System.currentTimeMillis())
                .apply();
    }

    private boolean fromGadgetbridge(final Message msg) {
        final String[] packages = getPackageManager().getPackagesForUid(msg.sendingUid);
        if (packages == null) {
            return false;
        }
        for (final String pkg : packages) {
            if (pkg.startsWith(GB_PACKAGE_PREFIX)) {
                return true;
            }
        }
        return false;
    }
}

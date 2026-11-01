package com.ajv009.gbvoice

import android.app.PendingIntent
import android.app.Service
import android.content.Intent
import android.os.Handler
import android.os.IBinder
import android.os.Looper
import android.os.Message
import android.os.Messenger
import android.os.RemoteException
import android.util.Log
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import com.ajv009.gbvoice.asr.ModelCatalog
import com.ajv009.gbvoice.asr.Recognizer
import com.ajv009.gbvoice.ui.MainActivity
import java.util.concurrent.Executors

/**
 * Bound service Gadgetbridge talks to during a watch voice session (contract: Gadgetbridge's
 * ZeppOsVoiceServiceBridge). Collects the Opus frames, hands them to the pipeline when the recording
 * ends, and replies with the text for the watch. Kept at this class name so existing Gadgetbridge
 * settings (com.ajv009.gbvoice.VoiceService) keep working.
 */
class VoiceService : Service() {
    private val worker = Executors.newSingleThreadExecutor()
    private val frames = ArrayList<ByteArray>()
    private val messenger = Messenger(Handler(Looper.getMainLooper(), ::handle))
    private val app get() = App.get(this)

    override fun onBind(intent: Intent): IBinder {
        Log.i(TAG, "Bound for a voice session")
        frames.clear()
        val s = app.settings.current
        val model = ModelCatalog.byId(s.modelId)
        if (s.transcribeOnPhone && model != null && model.isReady(this)) {
            // Load the model while the user is still speaking
            worker.execute { runCatching { Recognizer.load(this, model) }.onFailure { Log.w(TAG, "Preload failed", it) } }
        }
        return messenger.binder
    }

    override fun onDestroy() {
        worker.shutdown()
        super.onDestroy()
    }

    private fun handle(msg: Message): Boolean {
        if (!app.callers.isAllowed(msg.sendingUid)) {
            frames.clear()
            if (msg.what == MSG_VOICE_END) {
                Log.w(TAG, "Rejected a session from uid ${msg.sendingUid}")
                notifyPending()
                reply(msg.replyTo, error("Open GB Voice Bridge on the phone and allow this app"))
            }
            return true
        }
        when (msg.what) {
            MSG_VOICE_DATA -> msg.data.getByteArray(VOICE_DATA)?.let { frames += it }
            MSG_VOICE_END -> {
                val session = ArrayList(frames)
                frames.clear()
                val endedAt = System.currentTimeMillis()
                val replyTo = msg.replyTo
                Log.i(TAG, "Voice ended, ${session.size} frames")
                worker.execute {
                    val text = app.pipeline.processWatch(session, endedAt)
                    reply(replyTo, if (text.error) error(text.text) else Message.obtain(null, MSG_REPLY).apply {
                        data.putString(TEXT, text.text)
                    })
                }
            }
            else -> return false
        }
        return true
    }

    private fun error(text: String) = Message.obtain(null, MSG_ERROR).apply { data.putString(TEXT, text) }

    private fun reply(replyTo: Messenger?, message: Message) {
        try {
            replyTo?.send(message)
        } catch (e: RemoteException) {
            Log.e(TAG, "Gadgetbridge went away before the reply", e)
        }
    }

    /** Tells the user an unknown app tried to send audio, so they can allow it. */
    private fun notifyPending() {
        val caller = app.callers.latestPending() ?: return
        val open = PendingIntent.getActivity(
            this, 0, Intent(this, MainActivity::class.java), PendingIntent.FLAG_IMMUTABLE,
        )
        val notification = NotificationCompat.Builder(this, App.CHANNEL_ALERTS)
            .setSmallIcon(android.R.drawable.ic_btn_speak_now)
            .setContentTitle("${caller.label} wants to send voice")
            .setContentText("Open GB Voice Bridge to allow or dismiss it")
            .setContentIntent(open)
            .setAutoCancel(true)
            .build()
        runCatching { NotificationManagerCompat.from(this).notify(NOTIFY_PENDING, notification) }
    }

    private companion object {
        const val TAG = "GbVoiceService"
        // Contract with Gadgetbridge (ZeppOsVoiceServiceBridge)
        const val MSG_VOICE_DATA = 0
        const val MSG_VOICE_END = 1
        const val MSG_REPLY = 100
        const val MSG_ERROR = 101
        const val VOICE_DATA = "VOICE_DATA"
        const val TEXT = "TEXT"
        const val NOTIFY_PENDING = 1
    }
}

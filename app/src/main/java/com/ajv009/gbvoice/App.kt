package com.ajv009.gbvoice

import android.app.Application
import android.app.NotificationChannel
import android.app.NotificationManager
import android.content.Context
import com.ajv009.gbvoice.bridge.CallerGuard
import com.ajv009.gbvoice.data.HistoryStore
import com.ajv009.gbvoice.data.Settings
import com.ajv009.gbvoice.pipeline.Pipeline

/** Holds the app-wide singletons; the voice service and the UI run in this one process. */
class App : Application() {
    lateinit var settings: Settings
        private set
    lateinit var history: HistoryStore
        private set
    lateinit var callers: CallerGuard
        private set
    lateinit var pipeline: Pipeline
        private set

    override fun onCreate() {
        super.onCreate()
        settings = Settings(this)
        history = HistoryStore(filesDir.resolve("history"))
        callers = CallerGuard(this)
        pipeline = Pipeline(this, settings, history)
        getSystemService(NotificationManager::class.java).createNotificationChannels(listOf(
            NotificationChannel(CHANNEL_DOWNLOADS, "Model downloads", NotificationManager.IMPORTANCE_LOW),
            NotificationChannel(CHANNEL_ALERTS, "Setup alerts", NotificationManager.IMPORTANCE_DEFAULT),
        ))
    }

    companion object {
        const val CHANNEL_DOWNLOADS = "downloads"
        const val CHANNEL_ALERTS = "alerts"

        fun get(context: Context): App = context.applicationContext as App
    }
}

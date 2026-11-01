package com.ajv009.gbvoice.asr

import android.app.Notification
import android.content.Context
import android.content.pm.ServiceInfo
import androidx.core.app.NotificationCompat
import androidx.work.Constraints
import androidx.work.CoroutineWorker
import androidx.work.ExistingWorkPolicy
import androidx.work.ForegroundInfo
import androidx.work.NetworkType
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.WorkInfo
import androidx.work.WorkManager
import androidx.work.WorkerParameters
import androidx.work.workDataOf
import com.ajv009.gbvoice.App
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.withContext
import java.io.File
import java.io.FileOutputStream
import java.io.IOException
import java.net.HttpURLConnection
import java.net.URL
import java.security.MessageDigest

/** Download state of one model as the UI sees it. */
sealed interface DownloadState {
    data object Idle : DownloadState
    data class Running(val done: Long, val total: Long) : DownloadState
    data class Failed(val reason: String) : DownloadState
}

/** Starts, cancels and observes model downloads (WorkManager, so they survive Doze and app restarts). */
object Downloads {
    private fun name(id: String) = "download-$id"

    fun start(context: Context, model: SpeechModel) {
        val request = OneTimeWorkRequestBuilder<DownloadWorker>()
            .setInputData(workDataOf(DownloadWorker.MODEL to model.id))
            .setConstraints(Constraints.Builder().setRequiredNetworkType(NetworkType.CONNECTED).build())
            .build()
        WorkManager.getInstance(context).enqueueUniqueWork(name(model.id), ExistingWorkPolicy.KEEP, request)
    }

    fun cancel(context: Context, model: SpeechModel) {
        WorkManager.getInstance(context).cancelUniqueWork(name(model.id))
    }

    fun state(context: Context, model: SpeechModel): Flow<DownloadState> =
        WorkManager.getInstance(context).getWorkInfosForUniqueWorkFlow(name(model.id)).map { infos ->
            val info = infos.lastOrNull() ?: return@map DownloadState.Idle
            when (info.state) {
                WorkInfo.State.RUNNING, WorkInfo.State.ENQUEUED, WorkInfo.State.BLOCKED -> DownloadState.Running(
                    info.progress.getLong(DownloadWorker.DONE, model.bytesOnDisk(context)),
                    model.bytes,
                )
                WorkInfo.State.FAILED -> DownloadState.Failed(info.outputData.getString(DownloadWorker.ERROR) ?: "failed")
                else -> DownloadState.Idle
            }
        }
}

/** Downloads every file of one model with resume (HTTP Range), checks SHA-256, then renames into place. */
class DownloadWorker(context: Context, params: WorkerParameters) : CoroutineWorker(context, params) {

    override suspend fun doWork(): Result = withContext(Dispatchers.IO) {
        val model = ModelCatalog.byId(inputData.getString(MODEL) ?: "") ?: return@withContext Result.failure()
        runCatching { setForeground(foreground(model, 0)) }
        try {
            model.dir(applicationContext).mkdirs()
            var before = 0L
            for (file in model.files) {
                val target = model.file(applicationContext, file)
                if (target.length() != file.bytes) {
                    fetch(model, file, target) { got ->
                        val done = before + got
                        setProgress(workDataOf(DONE to done))
                        setForeground(foreground(model, (done * 100 / model.bytes).toInt()))
                    }
                }
                before += file.bytes
            }
            Result.success()
        } catch (e: IOException) {
            if (runAttemptCount < 3) Result.retry() else Result.failure(workDataOf(ERROR to (e.message ?: "download failed")))
        }
    }

    private suspend fun fetch(model: SpeechModel, file: ModelFile, target: File, progress: suspend (Long) -> Unit) {
        val part = File(target.path + ".part")
        if (part.length() > file.bytes) part.delete()
        val conn = URL(model.url(file)).openConnection() as HttpURLConnection
        conn.connectTimeout = 15_000
        conn.readTimeout = 60_000
        if (part.length() > 0) conn.setRequestProperty("Range", "bytes=${part.length()}-")
        try {
            val append = when (conn.responseCode) {
                206 -> true
                200 -> false
                else -> throw IOException("${file.name}: HTTP ${conn.responseCode}")
            }
            var got = if (append) part.length() else 0L
            var lastReport = 0L
            conn.inputStream.use { input ->
                FileOutputStream(part, append).use { out ->
                    val buf = ByteArray(1 shl 16)
                    while (true) {
                        if (isStopped) throw IOException("cancelled")
                        val n = input.read(buf)
                        if (n < 0) break
                        out.write(buf, 0, n)
                        got += n
                        if (got - lastReport > 4_000_000) {
                            lastReport = got
                            progress(got)
                        }
                    }
                }
            }
        } finally {
            conn.disconnect()
        }
        verify(file, part)
        if (!part.renameTo(target)) throw IOException("cannot finish ${file.name}")
    }

    private fun verify(file: ModelFile, part: File) {
        if (part.length() != file.bytes) {
            part.delete()
            throw IOException("${file.name}: size mismatch")
        }
        val expected = file.sha256 ?: return
        val digest = MessageDigest.getInstance("SHA-256")
        part.inputStream().use { input ->
            val buf = ByteArray(1 shl 16)
            while (true) {
                val n = input.read(buf)
                if (n < 0) break
                digest.update(buf, 0, n)
            }
        }
        val actual = digest.digest().joinToString("") { "%02x".format(it) }
        if (actual != expected) {
            part.delete()
            throw IOException("${file.name}: checksum mismatch")
        }
    }

    private fun foreground(model: SpeechModel, percent: Int): ForegroundInfo {
        val notification: Notification = NotificationCompat.Builder(applicationContext, App.CHANNEL_DOWNLOADS)
            .setSmallIcon(android.R.drawable.stat_sys_download)
            .setContentTitle("Downloading ${model.title}")
            .setProgress(100, percent, false)
            .setOngoing(true)
            .setSilent(true)
            .build()
        return ForegroundInfo(model.id.hashCode(), notification, ServiceInfo.FOREGROUND_SERVICE_TYPE_DATA_SYNC)
    }

    companion object {
        const val MODEL = "model"
        const val DONE = "done"
        const val ERROR = "error"
    }
}

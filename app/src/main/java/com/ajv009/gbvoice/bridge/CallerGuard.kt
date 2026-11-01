package com.ajv009.gbvoice.bridge

import android.content.Context
import android.content.pm.PackageManager
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import org.json.JSONArray
import org.json.JSONObject
import java.security.MessageDigest

/** An app that tried to talk to the voice service, identified by package name and signing certificate. */
data class Caller(val pkg: String, val label: String, val cert: String) {
    fun matches(other: Caller) = pkg == other.pkg && cert == other.cert
}

/**
 * Decides which apps may send audio. A package name alone can be faked by any app, so an app is only
 * trusted when both its package name and its signing certificate (SHA-256) match one the user allowed.
 * Unknown callers are listed as pending until the user allows or dismisses them.
 */
class CallerGuard(private val context: Context) {
    private val prefs = context.getSharedPreferences("callers", Context.MODE_PRIVATE)
    private val allowedFlow = MutableStateFlow(read(ALLOWED))
    private val pendingFlow = MutableStateFlow(read(PENDING))
    private val verdicts = HashMap<Int, Boolean>()

    val allowed: StateFlow<List<Caller>> = allowedFlow
    val pending: StateFlow<List<Caller>> = pendingFlow

    /** Called for every message, so the answer is cached per uid until the lists change. */
    @Synchronized
    fun isAllowed(uid: Int): Boolean {
        verdicts[uid]?.let { return it }
        val callers = identify(uid)
        val ok = callers.any { c -> allowedFlow.value.any { it.matches(c) } }
        if (!ok) callers.filter { c -> pendingFlow.value.none { it.matches(c) } }
            .takeIf { it.isNotEmpty() }
            ?.let { save(PENDING, pendingFlow.value + it) }
        verdicts[uid] = ok
        return ok
    }

    /** The newest pending caller, for the "allow this app?" notification. */
    fun latestPending(): Caller? = pendingFlow.value.lastOrNull()

    @Synchronized
    fun allow(caller: Caller) {
        save(ALLOWED, allowedFlow.value.filterNot { it.matches(caller) } + caller)
        save(PENDING, pendingFlow.value.filterNot { it.matches(caller) })
    }

    @Synchronized
    fun dismiss(caller: Caller) = save(PENDING, pendingFlow.value.filterNot { it.matches(caller) })

    @Synchronized
    fun revoke(caller: Caller) = save(ALLOWED, allowedFlow.value.filterNot { it.matches(caller) })

    private fun identify(uid: Int): List<Caller> {
        val pm = context.packageManager
        return pm.getPackagesForUid(uid).orEmpty().mapNotNull { pkg ->
            runCatching {
                val info = pm.getPackageInfo(pkg, PackageManager.GET_SIGNING_CERTIFICATES)
                val signers = info.signingInfo?.apkContentsSigners.orEmpty()
                val cert = signers.map { sha256(it.toByteArray()) }.sorted().joinToString(",")
                val label = info.applicationInfo?.loadLabel(pm)?.toString() ?: pkg
                Caller(pkg, label, cert)
            }.getOrNull()
        }
    }

    private fun save(key: String, list: List<Caller>) {
        prefs.edit().putString(key, JSONArray(list.map {
            JSONObject().put("pkg", it.pkg).put("label", it.label).put("cert", it.cert)
        }).toString()).apply()
        if (key == ALLOWED) allowedFlow.value = list else pendingFlow.value = list
        verdicts.clear()
    }

    private fun read(key: String): List<Caller> = runCatching {
        val array = JSONArray(prefs.getString(key, "[]"))
        (0 until array.length()).map {
            val o = array.getJSONObject(it)
            Caller(o.getString("pkg"), o.getString("label"), o.getString("cert"))
        }
    }.getOrDefault(emptyList())

    companion object {
        private const val ALLOWED = "allowed"
        private const val PENDING = "pending"

        fun sha256(bytes: ByteArray): String =
            MessageDigest.getInstance("SHA-256").digest(bytes).joinToString(":") { "%02X".format(it) }
    }
}

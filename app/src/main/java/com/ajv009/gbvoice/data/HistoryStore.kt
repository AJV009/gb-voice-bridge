package com.ajv009.gbvoice.data

import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import org.json.JSONArray
import java.io.File

/**
 * The dictation log: a JSON file of [Entry] (newest first) plus one audio file per entry that kept its audio.
 * Small enough (a few hundred entries) to rewrite on every change.
 */
class HistoryStore(private val dir: File) {
    private val file = File(dir, "history.json")
    private val audioDir = File(dir, "audio")
    private val flow = MutableStateFlow(load())

    val entries: StateFlow<List<Entry>> = flow

    @Synchronized
    fun add(entry: Entry) {
        val next = (listOf(entry) + flow.value.filter { it.id != entry.id })
        val kept = next.take(LIMIT)
        next.drop(LIMIT).forEach { audioFile(it.id).delete() }
        save(kept)
    }

    /** Applies [change] to the entry with [id], if it still exists. */
    @Synchronized
    fun update(id: String, change: (Entry) -> Entry) {
        save(flow.value.map { if (it.id == id) change(it) else it })
    }

    @Synchronized
    fun delete(id: String) {
        audioFile(id).delete()
        save(flow.value.filter { it.id != id })
    }

    @Synchronized
    fun clear() {
        audioDir.listFiles()?.forEach { it.delete() }
        save(emptyList())
    }

    fun get(id: String): Entry? = flow.value.firstOrNull { it.id == id }

    /** Where the watch's Opus frames for [id] are kept (see OpusFrames.write). */
    fun audioFile(id: String): File = File(audioDir.apply { mkdirs() }, "$id.frames")

    private fun save(list: List<Entry>) {
        dir.mkdirs()
        val tmp = File(dir, "history.json.tmp")
        tmp.writeText(JSONArray(list.map { it.toJson() }).toString())
        tmp.renameTo(file)
        flow.value = list
    }

    private fun load(): List<Entry> = runCatching {
        val array = JSONArray(file.readText())
        (0 until array.length()).map { Entry.fromJson(array.getJSONObject(it)) }
    }.getOrDefault(emptyList())

    private companion object {
        const val LIMIT = 500
    }
}

package com.ajv009.gbvoice.asr

import android.content.Context
import java.io.File

enum class Family(val title: String, val blurb: String) {
    PARAKEET("Parakeet", "NVIDIA's fast recogniser. About 4 times faster than Whisper Small at similar accuracy."),
    WHISPER("Whisper", "OpenAI's open speech model. Smaller sizes download faster but make more mistakes."),
}

/** One file of a model; [sha256] is checked after download when known. */
data class ModelFile(val name: String, val bytes: Long, val sha256: String? = null)

/** A downloadable sherpa-onnx model (int8 builds from huggingface.co/csukuangfj). */
data class SpeechModel(
    val id: String,
    val family: Family,
    val title: String,
    val note: String,
    val englishOnly: Boolean,
    val repo: String,
    val encoder: ModelFile,
    val decoder: ModelFile,
    val joiner: ModelFile?,
    val tokens: ModelFile,
    val licence: String,
) {
    val files: List<ModelFile> get() = listOfNotNull(tokens, joiner, decoder, encoder)
    val bytes: Long get() = files.sumOf { it.bytes }

    fun url(file: ModelFile) = "https://huggingface.co/$repo/resolve/main/${file.name}"

    /** Folder per model id; the prototype's Whisper small.en folder is reused as is. */
    fun dir(context: Context) = File(context.filesDir, id)

    fun file(context: Context, f: ModelFile) = File(dir(context), f.name)

    fun isReady(context: Context) = files.all { file(context, it).length() == it.bytes }

    /** Bytes already on disk, including unfinished downloads. */
    fun bytesOnDisk(context: Context): Long =
        files.sumOf { f -> file(context, f).length().takeIf { it > 0 } ?: File(dir(context), f.name + ".part").length() }

    fun delete(context: Context) {
        dir(context).deleteRecursively()
    }
}

object ModelCatalog {
    private const val CC_BY = "CC-BY-4.0 (NVIDIA)"
    private const val MIT = "MIT (OpenAI)"

    val all: List<SpeechModel> = listOf(
        SpeechModel(
            "parakeet-0.6b-v2", Family.PARAKEET, "Parakeet 0.6B v2", "Fast, very accurate", true,
            "csukuangfj/sherpa-onnx-nemo-parakeet-tdt-0.6b-v2-int8",
            encoder = ModelFile("encoder.int8.onnx", 652184296, "a32b12d17bbbc309d0686fbbcc2987b5e9b8333a7da83fa6b089f0a2acd651ab"),
            decoder = ModelFile("decoder.int8.onnx", 7257753, "b6bb64963457237b900e496ee9994b59294526439fbcc1fecf705b31a15c6b4e"),
            joiner = ModelFile("joiner.int8.onnx", 1739080, "7946164367946e7f9f29a122407c3252b680dbae9a51343eb2488d057c3c43d2"),
            tokens = ModelFile("tokens.txt", 9384), licence = CC_BY,
        ),
        SpeechModel(
            "parakeet-0.6b-v3", Family.PARAKEET, "Parakeet 0.6B v3", "Fast, 25 European languages", false,
            "csukuangfj/sherpa-onnx-nemo-parakeet-tdt-0.6b-v3-int8",
            encoder = ModelFile("encoder.int8.onnx", 652184281, "acfc2b4456377e15d04f0243af540b7fe7c992f8d898d751cf134c3a55fd2247"),
            decoder = ModelFile("decoder.int8.onnx", 11845275, "179e50c43d1a9de79c8a24149a2f9bac6eb5981823f2a2ed88d655b24248db4e"),
            joiner = ModelFile("joiner.int8.onnx", 6355277, "3164c13fc2821009440d20fcb5fdc78bff28b4db2f8d0f0b329101719c0948b3"),
            tokens = ModelFile("tokens.txt", 93939), licence = CC_BY,
        ),
        whisper("tiny.en", "Tiny", "Fastest, lower accuracy",
            12937772, "0ce578b827c94a961aacb8fa14b02f096504b337e5c94be37c36238cbe3e8bc6",
            89853865, "06c0e6ff6348d427e51839219d1c886c18cfdf411e629e33f5e1679bff9c1527"),
        whisper("base.en", "Base", "Fast, good accuracy",
            29120534, "ef6b936f4c9b1d90a3b68634b60c4ed8576b26172b33c2535ec0e933c9edb823",
            130669978, "f7162ad6db2dbef16cfaeaa7f945b9d7dd9c1b8d472f6aca82f2273d185e4d41"),
        whisper("small.en", "Small", "Slower, great accuracy",
            112442483, "8bdac288f369aa94ee2194059238c465ed82ea9d47ee8fa4a8c0a891873e462f",
            262223042, "710ccf890e10f3faa15f51ec346081a2723c9f3adb6e4da81c6573a5a6f877fb"),
    )

    fun byId(id: String): SpeechModel? = all.firstOrNull { it.id == id }

    private fun whisper(size: String, title: String, note: String, encBytes: Long, encSha: String, decBytes: Long, decSha: String) =
        SpeechModel(
            "whisper-$size", Family.WHISPER, "Whisper $title (English)", note, true,
            "csukuangfj/sherpa-onnx-whisper-$size",
            encoder = ModelFile("$size-encoder.int8.onnx", encBytes, encSha),
            decoder = ModelFile("$size-decoder.int8.onnx", decBytes, decSha),
            joiner = null,
            tokens = ModelFile("$size-tokens.txt", 835554), licence = MIT,
        )
}

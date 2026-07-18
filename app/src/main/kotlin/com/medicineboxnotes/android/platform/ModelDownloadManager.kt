package com.medicineboxnotes.android.platform

import android.content.Context
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import okhttp3.OkHttpClient
import okhttp3.Request
import java.io.File
import java.io.FileInputStream
import java.io.RandomAccessFile
import java.security.MessageDigest
import java.util.concurrent.TimeUnit

sealed interface ModelDownloadState {
    data object NotDownloaded : ModelDownloadState
    data class Downloading(val bytes: Long, val total: Long?) : ModelDownloadState
    data class Paused(val bytes: Long) : ModelDownloadState
    data class Ready(val file: File) : ModelDownloadState
    data class Failed(val message: String) : ModelDownloadState
}

class ModelDownloadManager(private val context: Context, private val onReady: (File) -> Unit) {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private val client = OkHttpClient.Builder().connectTimeout(30, TimeUnit.SECONDS).readTimeout(45, TimeUnit.SECONDS).build()
    private val dir = context.filesDir.resolve("models").apply { mkdirs() }
    private val finalFile = dir.resolve("gemma-4-E2B-it.litertlm")
    private val partialFile = dir.resolve("gemma-4-E2B-it.litertlm.part")
    private val preferences = context.getSharedPreferences("model_download", Context.MODE_PRIVATE)
    private var job: Job? = null
    private val _state = MutableStateFlow<ModelDownloadState>(bootstrap())
    val state: StateFlow<ModelDownloadState> = _state.asStateFlow()

    private fun bootstrap(): ModelDownloadState = if (finalFile.isFile && finalFile.length() >= MIN_MODEL_BYTES) {
        onReady(finalFile); ModelDownloadState.Ready(finalFile)
    } else ModelDownloadState.NotDownloaded

    fun start() {
        if (job?.isActive == true) return
        if (dir.usableSpace < REQUIRED_FREE_BYTES) { _state.value = ModelDownloadState.Failed("至少需要 6GB 可用空间"); return }
        job = scope.launch {
            val offset = partialFile.length()
            _state.value = ModelDownloadState.Downloading(offset, null)
            try {
                val request = Request.Builder().url(HUGGING_FACE_URL).apply {
                    if (offset > 0) {
                        header("Range", "bytes=$offset-")
                        preferences.getString(KEY_ETAG, null)?.let { header("If-Range", it) }
                    }
                }.build()
                client.newCall(request).execute().use { response ->
                    check(response.isSuccessful || response.code == 206) { "下载失败：HTTP ${response.code}" }
                    val append = offset > 0 && response.code == 206
                    if (!append && partialFile.exists()) partialFile.delete()
                    response.header("ETag")?.let { preferences.edit().putString(KEY_ETAG, it).apply() }
                    val total = response.body?.contentLength()?.takeIf { it >= 0 }?.let { it + if (append) offset else 0 }
                    RandomAccessFile(partialFile, "rw").use { output ->
                        if (append) output.seek(offset) else output.setLength(0)
                        val input = requireNotNull(response.body).byteStream(); val buffer = ByteArray(1024 * 256); var downloaded = if (append) offset else 0L
                        while (currentCoroutineContext().isActive) {
                            val read = input.read(buffer); if (read < 0) break
                            output.write(buffer, 0, read); downloaded += read
                            _state.value = ModelDownloadState.Downloading(downloaded, total)
                        }
                    }
                }
                currentCoroutineContext().ensureActive()
                check(partialFile.length() >= MIN_MODEL_BYTES) { "模型文件不完整" }
                check(partialFile.sha256() == HUGGING_FACE_SHA256) { "模型 SHA-256 校验失败，请删除后重新下载" }
                if (finalFile.exists()) finalFile.delete()
                check(partialFile.renameTo(finalFile)) { "模型落盘失败" }
                _state.value = ModelDownloadState.Ready(finalFile); onReady(finalFile)
            } catch (_: CancellationException) { _state.value = ModelDownloadState.Paused(partialFile.length()) }
            catch (error: Throwable) { _state.value = ModelDownloadState.Failed(error.message ?: "下载失败") }
        }
    }

    fun pause() { job?.cancel() }
    fun delete() { pause(); partialFile.delete(); finalFile.delete(); preferences.edit().remove(KEY_ETAG).apply(); _state.value = ModelDownloadState.NotDownloaded }

    companion object {
        const val HUGGING_FACE_URL = "https://huggingface.co/litert-community/gemma-4-E2B-it-litert-lm/resolve/main/gemma-4-E2B-it.litertlm"
        const val HUGGING_FACE_SHA256 = "181938105e0eefd105961417e8da75903eacda102c4fce9ce90f50b97139a63c"
        private const val KEY_ETAG = "gemma_4_e2b_etag"
        private const val MIN_MODEL_BYTES = 2_000_000_000L
        private const val REQUIRED_FREE_BYTES = 6_000_000_000L
    }
}

private fun File.sha256(): String {
    val digest = MessageDigest.getInstance("SHA-256")
    FileInputStream(this).use { input ->
        val buffer = ByteArray(1024 * 1024)
        while (true) {
            val read = input.read(buffer)
            if (read < 0) break
            digest.update(buffer, 0, read)
        }
    }
    return digest.digest().joinToString("") { "%02x".format(it) }
}

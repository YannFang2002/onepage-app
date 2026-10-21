package com.onepage.companion.ui.screens.files

import android.app.Application
import android.net.Uri
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import com.onepage.companion.OnePageApp
import com.onepage.companion.core.model.ApiException
import com.onepage.companion.core.model.FileEntry
import com.onepage.companion.core.net.DeviceApi
import com.onepage.companion.core.net.WsUploader
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import java.io.File
import java.io.IOException

enum class UploadState { Queued, Uploading, Done, Failed }

data class UploadTask(
    val id: String,
    val name: String,
    val size: Long,
    val sent: Long = 0,
    val state: UploadState = UploadState.Queued,
    val message: String? = null,
) {
    val fraction: Float get() = if (size <= 0) 0f else (sent.toFloat() / size).coerceIn(0f, 1f)
}

data class FilesUiState(
    val path: String = "/",
    val entries: List<FileEntry> = emptyList(),
    val loading: Boolean = true,
    val error: String? = null,
    val uploads: List<UploadTask> = emptyList(),
    val busy: Boolean = false,
)

class FilesViewModel(app: Application, private val api: DeviceApi) : AndroidViewModel(app) {

    private val http = (app as OnePageApp).http
    private val uploader = WsUploader(http)
    private val uploadLock = Mutex()

    private val _state = MutableStateFlow(FilesUiState())
    val state: StateFlow<FilesUiState> = _state.asStateFlow()

    private var loadJob: Job? = null

    init {
        open("/")
    }

    fun open(path: String) {
        val normalized = normalize(path)
        loadJob?.cancel()
        _state.update { it.copy(path = normalized, loading = true, error = null) }
        loadJob = viewModelScope.launch {
            runCatching { api.listFiles(normalized) }
                .onSuccess { entries ->
                    _state.update { it.copy(entries = sort(entries), loading = false) }
                }
                .onFailure { t ->
                    _state.update { it.copy(loading = false, error = describe(t)) }
                }
        }
    }

    fun refresh() = open(_state.value.path)

    fun up() {
        val current = _state.value.path
        if (current == "/") return
        val parent = current.substringBeforeLast('/', "")
        open(if (parent.isBlank()) "/" else parent)
    }

    fun createFolder(name: String) = mutate { api.createFolder(_state.value.path, name) }

    fun rename(entry: FileEntry, newName: String) = mutate { api.rename(joinPath(entry.name), newName) }

    fun delete(entry: FileEntry) = mutate { api.delete(joinPath(entry.name)) }

    fun move(entry: FileEntry, dest: String) = mutate { api.move(joinPath(entry.name), dest) }

    private fun mutate(block: suspend () -> Unit) {
        viewModelScope.launch {
            _state.update { it.copy(busy = true, error = null) }
            runCatching { block() }
                .onFailure { t -> _state.update { it.copy(error = describe(t)) } }
            _state.update { it.copy(busy = false) }
            runBlockingList()
        }
    }

    private suspend fun runBlockingList() {
        runCatching { api.listFiles(_state.value.path) }
            .onSuccess { entries -> _state.update { it.copy(entries = sort(entries)) } }
    }

    fun dismissError() = _state.update { it.copy(error = null) }

    fun clearFinishedUploads() {
        _state.update { s -> s.copy(uploads = s.uploads.filter { it.state == UploadState.Uploading || it.state == UploadState.Queued }) }
    }

    /** Enqueues the picked documents and uploads them one at a time. */
    fun upload(uris: List<Uri>) {
        if (uris.isEmpty()) return
        val resolver = getApplication<Application>().contentResolver
        val tasks = uris.mapNotNull { uri ->
            val meta = queryMeta(uri) ?: return@mapNotNull null
            UploadTask(
                id = uri.toString() + System.nanoTime(),
                name = meta.first,
                size = meta.second,
            ) to uri
        }
        if (tasks.isEmpty()) return
        _state.update { it.copy(uploads = it.uploads + tasks.map { t -> t.first }) }

        viewModelScope.launch {
            tasks.forEach { (task, uri) ->
                uploadOne(task, uri)
            }
            runBlockingList()
        }
    }

    private suspend fun uploadOne(task: UploadTask, uri: Uri) {
        uploadLock.withLock {
            val dir = _state.value.path
            updateTask(task.id) { it.copy(state = UploadState.Uploading, sent = 0) }
            if (task.size <= 0) {
                updateTask(task.id) {
                    it.copy(state = UploadState.Failed, message = "无法读取文件大小")
                }
                return@withLock
            }
            val resolver = getApplication<Application>().contentResolver
            runCatching {
                uploader.upload(
                    wsUrl = api.wsUrl,
                    filename = task.name,
                    size = task.size,
                    path = dir,
                    openStream = { resolver.openInputStream(uri) ?: throw IOException("无法读取文件") },
                ) { progress ->
                    updateTask(task.id) { it.copy(sent = progress.sent, size = progress.total) }
                }
            }.onSuccess {
                updateTask(task.id) { it.copy(state = UploadState.Done, sent = it.size) }
            }.onFailure { t ->
                updateTask(task.id) { it.copy(state = UploadState.Failed, message = describe(t)) }
            }
        }
    }

    private fun updateTask(id: String, transform: (UploadTask) -> UploadTask) {
        _state.update { s -> s.copy(uploads = s.uploads.map { if (it.id == id) transform(it) else it }) }
    }

    /** Downloads to app-private storage and returns the file for sharing. */
    suspend fun download(entry: FileEntry): Result<File> = withContext(Dispatchers.IO) {
        runCatching {
            val dir = File(getApplication<Application>().getExternalFilesDir(null), "downloads")
            dir.mkdirs()
            val target = File(dir, entry.name)
            api.download(joinPath(entry.name), target)
        }
    }

    private fun queryMeta(uri: Uri): Pair<String, Long>? {
        val resolver = getApplication<Application>().contentResolver
        var name = uri.lastPathSegment ?: return null
        var size = 0L
        runCatching {
            resolver.query(uri, null, null, null, null)?.use { cursor ->
                val nameIdx = cursor.getColumnIndex(android.provider.OpenableColumns.DISPLAY_NAME)
                val sizeIdx = cursor.getColumnIndex(android.provider.OpenableColumns.SIZE)
                if (cursor.moveToFirst()) {
                    if (nameIdx >= 0) name = cursor.getString(nameIdx) ?: name
                    if (sizeIdx >= 0) size = cursor.getLong(sizeIdx)
                }
            }
        }
        return name to size
    }

    private fun joinPath(name: String): String {
        val base = _state.value.path
        return if (base == "/") "/$name" else "$base/$name"
    }

    private fun normalize(path: String): String {
        var p = path.trim()
        if (p.isEmpty()) p = "/"
        if (!p.startsWith("/")) p = "/$p"
        if (p.length > 1 && p.endsWith("/")) p = p.dropLast(1)
        return p
    }

    private fun sort(entries: List<FileEntry>): List<FileEntry> =
        entries.sortedWith(
            compareByDescending<FileEntry> { it.isDirectory }
                .thenBy(String.CASE_INSENSITIVE_ORDER) { it.name }
        )

    private fun describe(t: Throwable): String = when (t) {
        is ApiException -> t.message ?: "设备返回 ${t.statusCode}"
        is IOException -> t.message ?: "网络错误"
        else -> t.message ?: "操作失败"
    }

    companion object {
        fun factory(api: DeviceApi) = viewModelFactory {
            initializer { FilesViewModel(OnePageApp.instance, api) }
        }
    }
}

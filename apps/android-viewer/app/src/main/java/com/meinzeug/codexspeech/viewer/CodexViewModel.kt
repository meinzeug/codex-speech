
package com.meinzeug.codexspeech.viewer

import android.util.Log
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import okhttp3.HttpUrl
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.MediaType.Companion.toMediaTypeOrNull
import okhttp3.MultipartBody
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.asRequestBody
import okhttp3.RequestBody.Companion.toRequestBody
import okhttp3.Response
import okhttp3.WebSocket
import okhttp3.WebSocketListener
import okio.ByteString
import org.json.JSONObject
import java.io.File
import java.util.concurrent.TimeUnit

data class DirectoryListing(val base: String, val dirs: List<String>)
data class RunnerDetection(val cwd: String, val projectType: String?, val androidPackage: String?)
data class RunnerProject(val path: String, val projectType: String, val androidPackage: String?)
data class RunnerDevice(
    val id: String,
    val model: String,
    val product: String,
    val device: String
)
data class RunnerStatus(
    val projectType: String?,
    val cwd: String?,
    val deviceId: String?,
    val mode: String?,
    val metroPort: Int?,
    val metroRunning: Boolean,
    val appRunning: Boolean,
    val flutterRunning: Boolean,
    val lastError: String?
)
data class RunnerLogs(
    val metro: List<String>,
    val app: List<String>,
    val flutter: List<String>
)

data class CodexStatus(
    val running: Boolean,
    val cwd: String?,
    val startedAt: Long?
)

data class SessionState(
    val workingDirectory: String?,
    val codex: CodexStatus?,
    val runner: RunnerStatus?
)

data class GitStatus(
    val isRepo: Boolean,
    val root: String?,
    val branch: String?,
    val dirty: Boolean,
    val changes: List<String>,
    val lastCommit: String?,
    val remote: String?,
    val upstream: String?,
    val ahead: Int?,
    val behind: Int?
)

data class FileEntry(
    val name: String,
    val path: String,
    val isDir: Boolean,
    val size: Long?,
    val mtime: Long?
)

data class FileList(
    val base: String,
    val entries: List<FileEntry>
)

data class FileRead(
    val path: String,
    val text: String,
    val truncated: Boolean,
    val size: Long,
    val isBinary: Boolean
)

data class SearchResult(
    val file: String,
    val line: Int,
    val column: Int,
    val text: String
)

data class ReplaceSummary(
    val files: List<String>,
    val replacements: Int
)
data class Pm2Process(
    val name: String?,
    val status: String?,
    val pid: Int?,
    val uptime: Long?,
    val restartTime: Int?,
    val cpu: Double?,
    val memory: Long?
)
data class AdminStatus(
    val repoRoot: String?,
    val viewerApk: String?,
    val liveApk: String?,
    val devices: List<RunnerDevice>,
    val backendPort: String?,
    val settingsPort: String?,
    val pm2: List<Pm2Process>
)
data class AdminPackageInfo(
    val installed: Boolean,
    val versionName: String?,
    val versionCode: String?
)
data class AdminDeviceInfo(
    val device: RunnerDevice?,
    val model: String?,
    val screenOn: Boolean?,
    val awake: Boolean?,
    val viewer: AdminPackageInfo,
    val live: AdminPackageInfo
)

private const val DEFAULT_BACKEND_PORT = 17500

class CodexViewModel : ViewModel() {
    private val wsClient = OkHttpClient()
    private val sttClient = OkHttpClient.Builder()
        .connectTimeout(30, TimeUnit.SECONDS)
        .readTimeout(5, TimeUnit.MINUTES)
        .writeTimeout(5, TimeUnit.MINUTES)
        .callTimeout(5, TimeUnit.MINUTES)
        .build()
    private val httpClient = OkHttpClient.Builder()
        .connectTimeout(10, TimeUnit.SECONDS)
        .readTimeout(30, TimeUnit.SECONDS)
        .writeTimeout(30, TimeUnit.SECONDS)
        .callTimeout(30, TimeUnit.SECONDS)
        .build()
    private var webSocket: WebSocket? = null
    private var lastHost: String? = null
    private var lastPort: Int? = null
    private val jsonMediaType = "application/json; charset=utf-8".toMediaType()

    private val _terminalOutput = MutableSharedFlow<String>(extraBufferCapacity = 256)
    val terminalOutput = _terminalOutput.asSharedFlow()

    private val _connectionStatus = MutableStateFlow("Disconnected")
    val connectionStatus = _connectionStatus.asStateFlow()

    private val _sttStatus = MutableStateFlow("Idle")
    val sttStatus = _sttStatus.asStateFlow()

    private val _runnerStatus = MutableStateFlow<RunnerStatus?>(null)
    val runnerStatus = _runnerStatus.asStateFlow()

    private val _runnerLogs = MutableStateFlow<RunnerLogs?>(null)
    val runnerLogs = _runnerLogs.asStateFlow()

    private val _codexStatus = MutableStateFlow<CodexStatus?>(null)
    val codexStatus = _codexStatus.asStateFlow()

    private val _sessionState = MutableStateFlow<SessionState?>(null)
    val sessionState = _sessionState.asStateFlow()

    private val _gitStatus = MutableStateFlow<GitStatus?>(null)
    val gitStatus = _gitStatus.asStateFlow()

    fun connectToBackend(ip: String, port: String = "17500", workingDir: String? = null) {
        try {
            _connectionStatus.value = "Connecting..."
            val normalizedHost = normalizeHost(ip)
            val portValue = port.toIntOrNull() ?: DEFAULT_BACKEND_PORT
            lastHost = normalizedHost
            lastPort = portValue
            val urlBuilder = HttpUrl.Builder()
                .scheme("http")
                .host(normalizedHost)
                .port(portValue)
                .addPathSegment("ws")
            if (!workingDir.isNullOrBlank()) {
                urlBuilder.addQueryParameter("cwd", workingDir)
            }
            val request = Request.Builder().url(urlBuilder.build()).build()
            webSocket = wsClient.newWebSocket(request, object : WebSocketListener() {
                override fun onOpen(webSocket: WebSocket, response: Response) {
                    viewModelScope.launch { _connectionStatus.value = "Connected" }
                }

                override fun onMessage(webSocket: WebSocket, text: String) {
                    _terminalOutput.tryEmit(text)
                }

                override fun onMessage(webSocket: WebSocket, bytes: ByteString) {
                    _terminalOutput.tryEmit(bytes.utf8())
                }

                override fun onClosing(webSocket: WebSocket, code: Int, reason: String) {
                    viewModelScope.launch { _connectionStatus.value = "Closing: $reason" }
                    webSocket.close(1000, null)
                }

                override fun onFailure(webSocket: WebSocket, t: Throwable, response: Response?) {
                    viewModelScope.launch { _connectionStatus.value = "Error: ${t.message}" }
                }
            })
        } catch (e: Exception) {
            _connectionStatus.value = "Failed: ${e.message}"
        }
    }

    fun sendCommand(text: String) {
        webSocket?.send(text)
    }

    fun sendRaw(text: String) {
        webSocket?.send(text)
    }
    
    fun disconnect() {
        webSocket?.close(1000, "User disconnected")
        webSocket = null
        _connectionStatus.value = "Disconnected"
        _sessionState.value = null
    }

    suspend fun fetchDirectories(host: String, port: String, query: String): Result<DirectoryListing> {
        return withContext(Dispatchers.IO) {
            try {
                val url = HttpUrl.Builder()
                    .scheme("http")
                    .host(normalizeHost(host))
                    .port(port.toIntOrNull() ?: DEFAULT_BACKEND_PORT)
                    .addPathSegment("dirs")
                    .addQueryParameter("path", query)
                    .build()
                val request = Request.Builder().url(url).get().build()
                val response = httpClient.newCall(request).execute()
                val body = response.body?.string().orEmpty()
                if (!response.isSuccessful) {
                    throw IllegalStateException("Dir list failed: ${response.code} ${response.message}")
                }
                val json = JSONObject(body)
                val arr = json.optJSONArray("dirs")
                val list = mutableListOf<String>()
                if (arr != null) {
                    for (i in 0 until arr.length()) {
                        list.add(arr.getString(i))
                    }
                }
                Result.success(
                    DirectoryListing(
                        base = json.optString("base", ""),
                        dirs = list
                    )
                )
            } catch (e: Exception) {
                Result.failure(e)
            }
        }
    }

    suspend fun createDirectory(host: String, port: String, path: String): Result<Unit> {
        return postDirAction(host, port, "/dirs/create", JSONObject().put("path", path))
    }

    suspend fun renameDirectory(host: String, port: String, src: String, dst: String): Result<Unit> {
        return postDirAction(
            host,
            port,
            "/dirs/rename",
            JSONObject().put("src", src).put("dst", dst)
        )
    }

    suspend fun deleteDirectory(host: String, port: String, path: String, recursive: Boolean): Result<Unit> {
        return postDirAction(
            host,
            port,
            "/dirs/delete",
            JSONObject().put("path", path).put("recursive", recursive)
        )
    }

    private suspend fun postDirAction(
        host: String,
        port: String,
        path: String,
        payload: JSONObject
    ): Result<Unit> {
        return withContext(Dispatchers.IO) {
            try {
                val url = HttpUrl.Builder()
                    .scheme("http")
                    .host(normalizeHost(host))
                    .port(port.toIntOrNull() ?: DEFAULT_BACKEND_PORT)
                    .encodedPath(path)
                    .build()
                val body = payload.toString().toRequestBody(jsonMediaType)
                val request = Request.Builder().url(url).post(body).build()
                val response = httpClient.newCall(request).execute()
                if (!response.isSuccessful) {
                    val respBody = response.body?.string().orEmpty()
                    throw IllegalStateException("Dir action failed: ${response.code} ${response.message} $respBody")
                }
                Result.success(Unit)
            } catch (e: Exception) {
                Result.failure(e)
            }
        }
    }

    suspend fun transcribeAudio(file: File, language: String? = null): Result<String> {
        val host = lastHost
        val port = lastPort
        if (host.isNullOrBlank() || port == null) {
            return Result.failure(IllegalStateException("No backend connected"))
        }
        return withContext(Dispatchers.IO) {
            try {
                _sttStatus.value = "Transcribing..."
                Log.d("CodexSpeech", "STT start: ${file.name} (${file.length()} bytes)")
                val url = HttpUrl.Builder()
                    .scheme("http")
                    .host(host)
                    .port(port)
                    .addPathSegment("stt")
                    .build()

                val mediaType = "audio/*".toMediaTypeOrNull()
                val fileBody = file.asRequestBody(mediaType)
                val formBuilder = MultipartBody.Builder()
                    .setType(MultipartBody.FORM)
                    .addFormDataPart("file", file.name, fileBody)
                if (!language.isNullOrBlank()) {
                    formBuilder.addFormDataPart("language", language)
                }
                val request = Request.Builder()
                    .url(url)
                    .post(formBuilder.build())
                    .build()
                val response = sttClient.newCall(request).execute()
                val body = response.body?.string().orEmpty()
                if (!response.isSuccessful) {
                    throw IllegalStateException("STT failed: ${response.code} ${response.message} $body")
                }
                val json = JSONObject(body)
                val text = json.optString("text", "")
                _sttStatus.value = "Idle"
                Log.d("CodexSpeech", "STT success (${text.length} chars)")
                Result.success(text)
            } catch (e: Exception) {
                _sttStatus.value = "Error: ${e.message}"
                Log.e("CodexSpeech", "STT error: ${e.message}", e)
                Result.failure(e)
            }
        }
    }

    suspend fun detectRunner(host: String, port: String, path: String?): Result<RunnerDetection> {
        return withContext(Dispatchers.IO) {
            try {
                val builder = HttpUrl.Builder()
                    .scheme("http")
                    .host(normalizeHost(host))
                    .port(port.toIntOrNull() ?: DEFAULT_BACKEND_PORT)
                    .addPathSegment("runner")
                    .addPathSegment("detect")
                if (!path.isNullOrBlank()) {
                    builder.addQueryParameter("path", path)
                }
                val request = Request.Builder().url(builder.build()).get().build()
                val response = httpClient.newCall(request).execute()
                val body = response.body?.string().orEmpty()
                if (!response.isSuccessful) {
                    throw IllegalStateException("Runner detect failed: ${response.code} ${response.message}")
                }
                val json = JSONObject(body)
                Result.success(
                    RunnerDetection(
                        cwd = json.optString("cwd", ""),
                        projectType = json.optString("project_type").takeIf { it.isNotBlank() },
                        androidPackage = json.optString("android_package").takeIf { it.isNotBlank() }
                    )
                )
            } catch (e: Exception) {
                Result.failure(e)
            }
        }
    }

    suspend fun scanRunnerProjects(
        host: String,
        port: String,
        path: String?,
        depth: Int
    ): Result<List<RunnerProject>> {
        return withContext(Dispatchers.IO) {
            try {
                val builder = HttpUrl.Builder()
                    .scheme("http")
                    .host(normalizeHost(host))
                    .port(port.toIntOrNull() ?: DEFAULT_BACKEND_PORT)
                    .addPathSegment("runner")
                    .addPathSegment("scan")
                    .addQueryParameter("depth", depth.toString())
                if (!path.isNullOrBlank()) {
                    builder.addQueryParameter("path", path)
                }
                val request = Request.Builder().url(builder.build()).get().build()
                val response = httpClient.newCall(request).execute()
                val body = response.body?.string().orEmpty()
                if (!response.isSuccessful) {
                    throw IllegalStateException("Runner scan failed: ${response.code} ${response.message}")
                }
                val json = JSONObject(body)
                val arr = json.optJSONArray("projects")
                val list = mutableListOf<RunnerProject>()
                if (arr != null) {
                    for (i in 0 until arr.length()) {
                        val item = arr.getJSONObject(i)
                        list.add(
                            RunnerProject(
                                path = item.optString("path"),
                                projectType = item.optString("project_type"),
                                androidPackage = item.optString("android_package").takeIf { it.isNotBlank() }
                            )
                        )
                    }
                }
                Result.success(list)
            } catch (e: Exception) {
                Result.failure(e)
            }
        }
    }

    suspend fun fetchRunnerDevices(host: String, port: String): Result<List<RunnerDevice>> {
        return withContext(Dispatchers.IO) {
            try {
                val url = HttpUrl.Builder()
                    .scheme("http")
                    .host(normalizeHost(host))
                    .port(port.toIntOrNull() ?: DEFAULT_BACKEND_PORT)
                    .addPathSegment("runner")
                    .addPathSegment("devices")
                    .build()
                val request = Request.Builder().url(url).get().build()
                val response = httpClient.newCall(request).execute()
                val body = response.body?.string().orEmpty()
                if (!response.isSuccessful) {
                    throw IllegalStateException("Runner devices failed: ${response.code} ${response.message}")
                }
                val json = JSONObject(body)
                val arr = json.optJSONArray("devices")
                val list = mutableListOf<RunnerDevice>()
                if (arr != null) {
                    for (i in 0 until arr.length()) {
                        val item = arr.getJSONObject(i)
                        list.add(
                            RunnerDevice(
                                id = item.optString("id"),
                                model = item.optString("model"),
                                product = item.optString("product"),
                                device = item.optString("device")
                            )
                        )
                    }
                }
                Result.success(list)
            } catch (e: Exception) {
                Result.failure(e)
            }
        }
    }

    suspend fun fetchLiveSnapshot(
        host: String,
        port: String,
        deviceId: String?,
        format: String,
        quality: Int
    ): Result<ByteArray> {
        return withContext(Dispatchers.IO) {
            try {
                val builder = HttpUrl.Builder()
                    .scheme("http")
                    .host(normalizeHost(host))
                    .port(port.toIntOrNull() ?: DEFAULT_BACKEND_PORT)
                    .addPathSegment("live")
                    .addPathSegment("snapshot")
                    .addQueryParameter("format", format)
                    .addQueryParameter("quality", quality.toString())
                if (!deviceId.isNullOrBlank()) {
                    builder.addQueryParameter("device_id", deviceId)
                }
                val request = Request.Builder().url(builder.build()).get().build()
                val response = httpClient.newCall(request).execute()
                if (!response.isSuccessful) {
                    throw IllegalStateException("Live snapshot failed: ${response.code} ${response.message}")
                }
                val bytes = response.body?.bytes() ?: ByteArray(0)
                if (bytes.isEmpty()) {
                    throw IllegalStateException("Live snapshot returned empty image")
                }
                Result.success(bytes)
            } catch (e: Exception) {
                Result.failure(e)
            }
        }
    }

    suspend fun installLiveHelper(host: String, port: String, deviceId: String?): Result<Unit> {
        return withContext(Dispatchers.IO) {
            try {
                val payload = JSONObject()
                if (!deviceId.isNullOrBlank()) {
                    payload.put("device_id", deviceId)
                }
                postJson(host, port, "/live/install", payload)
                Result.success(Unit)
            } catch (e: Exception) {
                Result.failure(e)
            }
        }
    }

    suspend fun openLiveHelper(host: String, port: String, deviceId: String?): Result<Unit> {
        return withContext(Dispatchers.IO) {
            try {
                val payload = JSONObject()
                if (!deviceId.isNullOrBlank()) {
                    payload.put("device_id", deviceId)
                }
                postJson(host, port, "/live/open", payload)
                Result.success(Unit)
            } catch (e: Exception) {
                Result.failure(e)
            }
        }
    }

    suspend fun sendLiveTap(host: String, port: String, deviceId: String?, x: Int, y: Int): Result<Unit> {
        return withContext(Dispatchers.IO) {
            try {
                val payload = JSONObject()
                    .put("x", x)
                    .put("y", y)
                if (!deviceId.isNullOrBlank()) {
                    payload.put("device_id", deviceId)
                }
                postJson(host, port, "/live/tap", payload)
                Result.success(Unit)
            } catch (e: Exception) {
                Result.failure(e)
            }
        }
    }

    suspend fun sendLiveSwipe(
        host: String,
        port: String,
        deviceId: String?,
        x1: Int,
        y1: Int,
        x2: Int,
        y2: Int,
        durationMs: Int
    ): Result<Unit> {
        return withContext(Dispatchers.IO) {
            try {
                val payload = JSONObject()
                    .put("x1", x1)
                    .put("y1", y1)
                    .put("x2", x2)
                    .put("y2", y2)
                    .put("duration_ms", durationMs)
                if (!deviceId.isNullOrBlank()) {
                    payload.put("device_id", deviceId)
                }
                postJson(host, port, "/live/swipe", payload)
                Result.success(Unit)
            } catch (e: Exception) {
                Result.failure(e)
            }
        }
    }

    suspend fun sendLiveLongPress(host: String, port: String, deviceId: String?, x: Int, y: Int): Result<Unit> {
        return withContext(Dispatchers.IO) {
            try {
                val payload = JSONObject()
                    .put("x", x)
                    .put("y", y)
                if (!deviceId.isNullOrBlank()) {
                    payload.put("device_id", deviceId)
                }
                postJson(host, port, "/live/longpress", payload)
                Result.success(Unit)
            } catch (e: Exception) {
                Result.failure(e)
            }
        }
    }

    suspend fun sendLiveText(host: String, port: String, deviceId: String?, text: String): Result<Unit> {
        return withContext(Dispatchers.IO) {
            try {
                val payload = JSONObject()
                    .put("text", text)
                if (!deviceId.isNullOrBlank()) {
                    payload.put("device_id", deviceId)
                }
                postJson(host, port, "/live/text", payload)
                Result.success(Unit)
            } catch (e: Exception) {
                Result.failure(e)
            }
        }
    }

    suspend fun sendLiveKey(host: String, port: String, deviceId: String?, keyCode: Int): Result<Unit> {
        return withContext(Dispatchers.IO) {
            try {
                val payload = JSONObject()
                    .put("keycode", keyCode)
                if (!deviceId.isNullOrBlank()) {
                    payload.put("device_id", deviceId)
                }
                postJson(host, port, "/live/key", payload)
                Result.success(Unit)
            } catch (e: Exception) {
                Result.failure(e)
            }
        }
    }

    suspend fun wakeLiveDevice(host: String, port: String, deviceId: String?): Result<Unit> {
        return withContext(Dispatchers.IO) {
            try {
                val payload = JSONObject()
                if (!deviceId.isNullOrBlank()) {
                    payload.put("device_id", deviceId)
                }
                postJson(host, port, "/live/wake", payload)
                Result.success(Unit)
            } catch (e: Exception) {
                Result.failure(e)
            }
        }
    }

    suspend fun startRunner(
        host: String,
        port: String,
        path: String?,
        projectType: String?,
        deviceId: String?,
        mode: String,
        metroPort: Int
    ): Result<RunnerStatus> {
        return withContext(Dispatchers.IO) {
            try {
                val payload = JSONObject()
                    .put("path", path)
                    .put("project_type", projectType)
                    .put("device_id", deviceId)
                    .put("mode", mode)
                    .put("metro_port", metroPort)
                val response = postJson(host, port, "/runner/start", payload)
                val status = parseRunnerStatus(response)
                _runnerStatus.value = status
                Result.success(status)
            } catch (e: Exception) {
                Result.failure(e)
            }
        }
    }

    suspend fun openRunnerApp(
        host: String,
        port: String,
        packageName: String?,
        path: String?,
        deviceId: String?,
        projectType: String? = null,
        mode: String? = null,
        metroPort: Int? = null
    ): Result<Unit> {
        return withContext(Dispatchers.IO) {
            try {
                val payload = JSONObject()
                if (!packageName.isNullOrBlank()) {
                    payload.put("package", packageName)
                }
                if (!path.isNullOrBlank()) {
                    payload.put("path", path)
                }
                if (!deviceId.isNullOrBlank()) {
                    payload.put("device_id", deviceId)
                }
                if (!projectType.isNullOrBlank()) {
                    payload.put("project_type", projectType)
                }
                if (!mode.isNullOrBlank()) {
                    payload.put("mode", mode)
                }
                if (metroPort != null) {
                    payload.put("metro_port", metroPort)
                }
                postJson(host, port, "/runner/open", payload)
                Result.success(Unit)
            } catch (e: Exception) {
                Result.failure(e)
            }
        }
    }

    suspend fun installRunnerApp(
        host: String,
        port: String,
        path: String?,
        projectType: String?,
        deviceId: String?,
        metroPort: Int
    ): Result<Unit> {
        return withContext(Dispatchers.IO) {
            try {
                val payload = JSONObject()
                    .put("path", path)
                    .put("project_type", projectType)
                    .put("device_id", deviceId)
                    .put("metro_port", metroPort)
                postJson(host, port, "/runner/install", payload)
                Result.success(Unit)
            } catch (e: Exception) {
                Result.failure(e)
            }
        }
    }

    suspend fun stopRunner(host: String, port: String): Result<Unit> {
        return withContext(Dispatchers.IO) {
            try {
                postJson(host, port, "/runner/stop", JSONObject())
                _runnerStatus.value = null
                Result.success(Unit)
            } catch (e: Exception) {
                Result.failure(e)
            }
        }
    }

    suspend fun reloadRunner(host: String, port: String, type: String): Result<Unit> {
        return withContext(Dispatchers.IO) {
            try {
                postJson(host, port, "/runner/reload", JSONObject().put("type", type))
                Result.success(Unit)
            } catch (e: Exception) {
                Result.failure(e)
            }
        }
    }

    suspend fun openDevMenu(host: String, port: String, deviceId: String?): Result<Unit> {
        return withContext(Dispatchers.IO) {
            try {
                val urlBuilder = HttpUrl.Builder()
                    .scheme("http")
                    .host(normalizeHost(host))
                    .port(port.toIntOrNull() ?: DEFAULT_BACKEND_PORT)
                    .addPathSegment("runner")
                    .addPathSegment("devmenu")
                if (!deviceId.isNullOrBlank()) {
                    urlBuilder.addQueryParameter("device_id", deviceId)
                }
                val request = Request.Builder()
                    .url(urlBuilder.build())
                    .post("{}".toRequestBody(jsonMediaType))
                    .build()
                val response = httpClient.newCall(request).execute()
                if (!response.isSuccessful) {
                    throw IllegalStateException("Dev menu failed: ${response.code} ${response.message}")
                }
                Result.success(Unit)
            } catch (e: Exception) {
                Result.failure(e)
            }
        }
    }

    suspend fun setReactNativeHost(
        host: String,
        port: String,
        path: String?,
        packageName: String?,
        deviceId: String?,
        metroHost: String,
        metroPort: Int
    ): Result<Unit> {
        return withContext(Dispatchers.IO) {
            try {
                val payload = JSONObject()
                    .put("path", path)
                    .put("package", packageName)
                    .put("device_id", deviceId)
                    .put("host", metroHost)
                    .put("port", metroPort)
                postJson(host, port, "/runner/rn/host", payload)
                Result.success(Unit)
            } catch (e: Exception) {
                Result.failure(e)
            }
        }
    }

    suspend fun reloadReactNative(host: String, port: String, deviceId: String?): Result<Unit> {
        return withContext(Dispatchers.IO) {
            try {
                val payload = JSONObject().put("device_id", deviceId)
                postJson(host, port, "/runner/rn/reload", payload)
                Result.success(Unit)
            } catch (e: Exception) {
                Result.failure(e)
            }
        }
    }

    suspend fun refreshRunnerStatus(host: String, port: String): Result<RunnerStatus> {
        return withContext(Dispatchers.IO) {
            try {
                val url = HttpUrl.Builder()
                    .scheme("http")
                    .host(normalizeHost(host))
                    .port(port.toIntOrNull() ?: DEFAULT_BACKEND_PORT)
                    .addPathSegment("runner")
                    .addPathSegment("status")
                    .build()
                val request = Request.Builder().url(url).get().build()
                val response = httpClient.newCall(request).execute()
                val body = response.body?.string().orEmpty()
                if (!response.isSuccessful) {
                    throw IllegalStateException("Runner status failed: ${response.code} ${response.message}")
                }
                val status = parseRunnerStatus(body)
                _runnerStatus.value = status
                Result.success(status)
            } catch (e: Exception) {
                Result.failure(e)
            }
        }
    }

    suspend fun refreshRunnerLogs(host: String, port: String): Result<RunnerLogs> {
        return withContext(Dispatchers.IO) {
            try {
                val url = HttpUrl.Builder()
                    .scheme("http")
                    .host(normalizeHost(host))
                    .port(port.toIntOrNull() ?: DEFAULT_BACKEND_PORT)
                    .addPathSegment("runner")
                    .addPathSegment("logs")
                    .build()
                val request = Request.Builder().url(url).get().build()
                val response = httpClient.newCall(request).execute()
                val body = response.body?.string().orEmpty()
                if (!response.isSuccessful) {
                    throw IllegalStateException("Runner logs failed: ${response.code} ${response.message}")
                }
                val json = JSONObject(body)
                val logs = RunnerLogs(
                    metro = json.optJSONArray("metro")?.let { array ->
                        (0 until array.length()).map { array.optString(it) }
                    } ?: emptyList(),
                    app = json.optJSONArray("app")?.let { array ->
                        (0 until array.length()).map { array.optString(it) }
                    } ?: emptyList(),
                    flutter = json.optJSONArray("flutter")?.let { array ->
                        (0 until array.length()).map { array.optString(it) }
                    } ?: emptyList()
                )
                _runnerLogs.value = logs
                Result.success(logs)
            } catch (e: Exception) {
                Result.failure(e)
            }
        }
    }

    suspend fun fetchCodexStatus(host: String, port: String): Result<CodexStatus> {
        return withContext(Dispatchers.IO) {
            try {
                val url = HttpUrl.Builder()
                    .scheme("http")
                    .host(normalizeHost(host))
                    .port(port.toIntOrNull() ?: DEFAULT_BACKEND_PORT)
                    .addPathSegment("codex")
                    .addPathSegment("status")
                    .build()
                val request = Request.Builder().url(url).get().build()
                val response = httpClient.newCall(request).execute()
                val body = response.body?.string().orEmpty()
                if (!response.isSuccessful) {
                    throw IllegalStateException("Codex status failed: ${response.code} ${response.message}")
                }
                val json = JSONObject(body)
                val cwdValue = json.opt("cwd") as? String
                val status = CodexStatus(
                    running = json.optBoolean("running", false),
                    cwd = cwdValue,
                    startedAt = if (json.has("started_at")) json.optLong("started_at") else null
                )
                _codexStatus.value = status
                Result.success(status)
            } catch (e: Exception) {
                Result.failure(e)
            }
        }
    }

    suspend fun fetchSessionState(host: String, port: String): Result<SessionState> {
        return withContext(Dispatchers.IO) {
            try {
                val url = HttpUrl.Builder()
                    .scheme("http")
                    .host(normalizeHost(host))
                    .port(port.toIntOrNull() ?: DEFAULT_BACKEND_PORT)
                    .addPathSegment("session")
                    .addPathSegment("state")
                    .build()
                val request = Request.Builder().url(url).get().build()
                val response = httpClient.newCall(request).execute()
                val body = response.body?.string().orEmpty()
                if (!response.isSuccessful) {
                    throw IllegalStateException("Session state failed: ${response.code} ${response.message}")
                }
                val state = parseSessionState(body)
                _sessionState.value = state
                state.codex?.let { _codexStatus.value = it }
                state.runner?.let { _runnerStatus.value = it }
                Result.success(state)
            } catch (e: Exception) {
                Result.failure(e)
            }
        }
    }

    suspend fun updateSessionWorkingDir(host: String, port: String, workingDir: String): Result<SessionState> {
        return withContext(Dispatchers.IO) {
            try {
                val payload = JSONObject().put("working_directory", workingDir)
                val response = postJson(host, port, "/session/state", payload)
                val state = parseSessionState(response)
                _sessionState.value = state
                state.codex?.let { _codexStatus.value = it }
                state.runner?.let { _runnerStatus.value = it }
                Result.success(state)
            } catch (e: Exception) {
                Result.failure(e)
            }
        }
    }

    suspend fun startCodex(host: String, port: String, cwd: String?): Result<CodexStatus> {
        return withContext(Dispatchers.IO) {
            try {
                val payload = JSONObject()
                if (!cwd.isNullOrBlank()) {
                    payload.put("cwd", cwd)
                }
                val response = postJson(host, port, "/codex/start", payload)
                val status = parseCodexStatus(response)
                _codexStatus.value = status
                Result.success(status)
            } catch (e: Exception) {
                Result.failure(e)
            }
        }
    }

    suspend fun stopCodex(host: String, port: String): Result<Unit> {
        return withContext(Dispatchers.IO) {
            try {
                postJson(host, port, "/codex/stop", JSONObject())
                _codexStatus.value = CodexStatus(running = false, cwd = null, startedAt = null)
                Result.success(Unit)
            } catch (e: Exception) {
                Result.failure(e)
            }
        }
    }

    suspend fun fetchGitStatus(host: String, port: String, path: String?): Result<GitStatus> {
        return withContext(Dispatchers.IO) {
            try {
                val urlBuilder = HttpUrl.Builder()
                    .scheme("http")
                    .host(normalizeHost(host))
                    .port(port.toIntOrNull() ?: DEFAULT_BACKEND_PORT)
                    .addPathSegment("git")
                    .addPathSegment("status")
                if (!path.isNullOrBlank()) {
                    urlBuilder.addQueryParameter("path", path)
                }
                val request = Request.Builder().url(urlBuilder.build()).get().build()
                val response = httpClient.newCall(request).execute()
                val body = response.body?.string().orEmpty()
                if (!response.isSuccessful) {
                    throw IllegalStateException("Git status failed: ${response.code} ${response.message}")
                }
                val json = JSONObject(body)
                val changes = json.optJSONArray("changes")?.let { arr ->
                    (0 until arr.length()).map { arr.optString(it) }
                } ?: emptyList()
                val rootValue = json.opt("root") as? String
                val branchValue = json.opt("branch") as? String
                val lastCommitValue = json.opt("last_commit") as? String
                val remoteValue = json.opt("remote") as? String
                val upstreamValue = json.opt("upstream") as? String
                val aheadValue = if (json.has("ahead") && !json.isNull("ahead")) json.optInt("ahead") else null
                val behindValue = if (json.has("behind") && !json.isNull("behind")) json.optInt("behind") else null
                val status = GitStatus(
                    isRepo = json.optBoolean("is_repo", false),
                    root = rootValue,
                    branch = branchValue,
                    dirty = json.optBoolean("dirty", false),
                    changes = changes,
                    lastCommit = lastCommitValue,
                    remote = remoteValue,
                    upstream = upstreamValue,
                    ahead = aheadValue,
                    behind = behindValue
                )
                _gitStatus.value = status
                Result.success(status)
            } catch (e: Exception) {
                Result.failure(e)
            }
        }
    }

    suspend fun gitPull(host: String, port: String, path: String?): Result<String> {
        return withContext(Dispatchers.IO) {
            try {
                val urlBuilder = HttpUrl.Builder()
                    .scheme("http")
                    .host(normalizeHost(host))
                    .port(port.toIntOrNull() ?: DEFAULT_BACKEND_PORT)
                    .addPathSegment("git")
                    .addPathSegment("pull")
                if (!path.isNullOrBlank()) {
                    urlBuilder.addQueryParameter("path", path)
                }
                val request = Request.Builder().url(urlBuilder.build()).post("{}".toRequestBody(jsonMediaType)).build()
                val response = httpClient.newCall(request).execute()
                val body = response.body?.string().orEmpty()
                if (!response.isSuccessful) {
                    throw IllegalStateException("Git pull failed: ${response.code} ${response.message} $body")
                }
                Result.success(body)
            } catch (e: Exception) {
                Result.failure(e)
            }
        }
    }

    suspend fun gitPush(host: String, port: String, path: String?): Result<String> {
        return withContext(Dispatchers.IO) {
            try {
                val urlBuilder = HttpUrl.Builder()
                    .scheme("http")
                    .host(normalizeHost(host))
                    .port(port.toIntOrNull() ?: DEFAULT_BACKEND_PORT)
                    .addPathSegment("git")
                    .addPathSegment("push")
                if (!path.isNullOrBlank()) {
                    urlBuilder.addQueryParameter("path", path)
                }
                val request = Request.Builder().url(urlBuilder.build()).post("{}".toRequestBody(jsonMediaType)).build()
                val response = httpClient.newCall(request).execute()
                val body = response.body?.string().orEmpty()
                if (!response.isSuccessful) {
                    throw IllegalStateException("Git push failed: ${response.code} ${response.message} $body")
                }
                Result.success(body)
            } catch (e: Exception) {
                Result.failure(e)
            }
        }
    }

    suspend fun gitCommit(host: String, port: String, path: String?, message: String, addAll: Boolean): Result<String> {
        return withContext(Dispatchers.IO) {
            try {
                val payload = JSONObject()
                    .put("path", path)
                    .put("message", message)
                    .put("add_all", addAll)
                val response = postJson(host, port, "/git/commit", payload)
                Result.success(response)
            } catch (e: Exception) {
                Result.failure(e)
            }
        }
    }

    suspend fun gitDiff(host: String, port: String, path: String?, file: String): Result<String> {
        return withContext(Dispatchers.IO) {
            try {
                val urlBuilder = HttpUrl.Builder()
                    .scheme("http")
                    .host(normalizeHost(host))
                    .port(port.toIntOrNull() ?: DEFAULT_BACKEND_PORT)
                    .addPathSegment("git")
                    .addPathSegment("diff")
                    .addQueryParameter("file", file)
                if (!path.isNullOrBlank()) {
                    urlBuilder.addQueryParameter("path", path)
                }
                val request = Request.Builder().url(urlBuilder.build()).get().build()
                val response = httpClient.newCall(request).execute()
                val body = response.body?.string().orEmpty()
                if (!response.isSuccessful) {
                    throw IllegalStateException("Git diff failed: ${response.code} ${response.message}")
                }
                val json = JSONObject(body)
                Result.success(json.optString("diff", ""))
            } catch (e: Exception) {
                Result.failure(e)
            }
        }
    }

    suspend fun gitFetch(host: String, port: String, path: String?): Result<String> {
        return withContext(Dispatchers.IO) {
            try {
                val urlBuilder = HttpUrl.Builder()
                    .scheme("http")
                    .host(normalizeHost(host))
                    .port(port.toIntOrNull() ?: DEFAULT_BACKEND_PORT)
                    .addPathSegment("git")
                    .addPathSegment("fetch")
                if (!path.isNullOrBlank()) {
                    urlBuilder.addQueryParameter("path", path)
                }
                val request = Request.Builder().url(urlBuilder.build()).post("{}".toRequestBody(jsonMediaType)).build()
                val response = httpClient.newCall(request).execute()
                val body = response.body?.string().orEmpty()
                if (!response.isSuccessful) {
                    throw IllegalStateException("Git fetch failed: ${response.code} ${response.message} $body")
                }
                Result.success(body)
            } catch (e: Exception) {
                Result.failure(e)
            }
        }
    }

    suspend fun gitBranches(host: String, port: String, path: String?): Result<Pair<List<String>, String?>> {
        return withContext(Dispatchers.IO) {
            try {
                val urlBuilder = HttpUrl.Builder()
                    .scheme("http")
                    .host(normalizeHost(host))
                    .port(port.toIntOrNull() ?: DEFAULT_BACKEND_PORT)
                    .addPathSegment("git")
                    .addPathSegment("branches")
                if (!path.isNullOrBlank()) {
                    urlBuilder.addQueryParameter("path", path)
                }
                val request = Request.Builder().url(urlBuilder.build()).get().build()
                val response = httpClient.newCall(request).execute()
                val body = response.body?.string().orEmpty()
                if (!response.isSuccessful) {
                    throw IllegalStateException("Git branches failed: ${response.code} ${response.message} $body")
                }
                val json = JSONObject(body)
                val branches = json.optJSONArray("branches")?.let { arr ->
                    (0 until arr.length()).map { arr.optString(it) }.filter { it.isNotBlank() }
                } ?: emptyList()
                val current = json.opt("current") as? String
                Result.success(branches to current)
            } catch (e: Exception) {
                Result.failure(e)
            }
        }
    }

    suspend fun gitCheckout(host: String, port: String, path: String?, branch: String, create: Boolean): Result<String> {
        return withContext(Dispatchers.IO) {
            try {
                val payload = JSONObject()
                    .put("path", path)
                    .put("branch", branch)
                    .put("create", create)
                val response = postJson(host, port, "/git/checkout", payload)
                Result.success(response)
            } catch (e: Exception) {
                Result.failure(e)
            }
        }
    }

    suspend fun gitLog(host: String, port: String, path: String?, limit: Int = 10): Result<List<String>> {
        return withContext(Dispatchers.IO) {
            try {
                val urlBuilder = HttpUrl.Builder()
                    .scheme("http")
                    .host(normalizeHost(host))
                    .port(port.toIntOrNull() ?: DEFAULT_BACKEND_PORT)
                    .addPathSegment("git")
                    .addPathSegment("log")
                    .addQueryParameter("limit", limit.coerceIn(1, 50).toString())
                if (!path.isNullOrBlank()) {
                    urlBuilder.addQueryParameter("path", path)
                }
                val request = Request.Builder().url(urlBuilder.build()).get().build()
                val response = httpClient.newCall(request).execute()
                val body = response.body?.string().orEmpty()
                if (!response.isSuccessful) {
                    throw IllegalStateException("Git log failed: ${response.code} ${response.message} $body")
                }
                val json = JSONObject(body)
                val entries = json.optJSONArray("entries")?.let { arr ->
                    (0 until arr.length()).map { arr.optString(it) }.filter { it.isNotBlank() }
                } ?: emptyList()
                Result.success(entries)
            } catch (e: Exception) {
                Result.failure(e)
            }
        }
    }

    suspend fun fetchFileList(host: String, port: String, path: String?, showHidden: Boolean): Result<FileList> {
        return withContext(Dispatchers.IO) {
            try {
                val builder = HttpUrl.Builder()
                    .scheme("http")
                    .host(normalizeHost(host))
                    .port(port.toIntOrNull() ?: DEFAULT_BACKEND_PORT)
                    .addPathSegment("files")
                    .addPathSegment("list")
                if (!path.isNullOrBlank()) {
                    builder.addQueryParameter("path", path)
                }
                if (showHidden) {
                    builder.addQueryParameter("show_hidden", "true")
                }
                val request = Request.Builder().url(builder.build()).get().build()
                val response = httpClient.newCall(request).execute()
                val body = response.body?.string().orEmpty()
                if (!response.isSuccessful) {
                    throw IllegalStateException("File list failed: ${response.code} ${response.message}")
                }
                val json = JSONObject(body)
                val entries = json.optJSONArray("entries")?.let { arr ->
                    (0 until arr.length()).mapNotNull { idx ->
                        val item = arr.optJSONObject(idx) ?: return@mapNotNull null
                        FileEntry(
                            name = item.optString("name"),
                            path = item.optString("path"),
                            isDir = item.optBoolean("is_dir", false),
                            size = if (item.isNull("size")) null else item.optLong("size"),
                            mtime = if (item.isNull("mtime")) null else item.optLong("mtime")
                        )
                    }
                } ?: emptyList()
                Result.success(FileList(json.optString("base", ""), entries))
            } catch (e: Exception) {
                Result.failure(e)
            }
        }
    }

    suspend fun readFile(host: String, port: String, path: String): Result<FileRead> {
        return withContext(Dispatchers.IO) {
            try {
                val builder = HttpUrl.Builder()
                    .scheme("http")
                    .host(normalizeHost(host))
                    .port(port.toIntOrNull() ?: DEFAULT_BACKEND_PORT)
                    .addPathSegment("files")
                    .addPathSegment("read")
                    .addQueryParameter("path", path)
                val request = Request.Builder().url(builder.build()).get().build()
                val response = httpClient.newCall(request).execute()
                val body = response.body?.string().orEmpty()
                if (!response.isSuccessful) {
                    throw IllegalStateException("File read failed: ${response.code} ${response.message}")
                }
                val json = JSONObject(body)
                Result.success(
                    FileRead(
                        path = json.optString("path"),
                        text = json.optString("text"),
                        truncated = json.optBoolean("truncated", false),
                        size = json.optLong("size", 0L),
                        isBinary = json.optBoolean("is_binary", false)
                    )
                )
            } catch (e: Exception) {
                Result.failure(e)
            }
        }
    }

    suspend fun writeFile(host: String, port: String, path: String, content: String): Result<Unit> {
        return withContext(Dispatchers.IO) {
            try {
                val payload = JSONObject().put("path", path).put("content", content)
                postJson(host, port, "/files/write", payload)
                Result.success(Unit)
            } catch (e: Exception) {
                Result.failure(e)
            }
        }
    }

    suspend fun createPath(host: String, port: String, path: String, kind: String): Result<Unit> {
        return withContext(Dispatchers.IO) {
            try {
                val payload = JSONObject().put("path", path).put("kind", kind)
                postJson(host, port, "/files/create", payload)
                Result.success(Unit)
            } catch (e: Exception) {
                Result.failure(e)
            }
        }
    }

    suspend fun deletePath(host: String, port: String, path: String, recursive: Boolean): Result<Unit> {
        return withContext(Dispatchers.IO) {
            try {
                val payload = JSONObject().put("path", path).put("recursive", recursive)
                postJson(host, port, "/files/delete", payload)
                Result.success(Unit)
            } catch (e: Exception) {
                Result.failure(e)
            }
        }
    }

    suspend fun renamePath(host: String, port: String, src: String, dst: String): Result<Unit> {
        return withContext(Dispatchers.IO) {
            try {
                val payload = JSONObject().put("src", src).put("dst", dst)
                postJson(host, port, "/files/rename", payload)
                Result.success(Unit)
            } catch (e: Exception) {
                Result.failure(e)
            }
        }
    }

    suspend fun uploadFile(host: String, port: String, destDir: String?, filename: String, bytes: ByteArray): Result<Unit> {
        return withContext(Dispatchers.IO) {
            try {
                val builder = MultipartBody.Builder().setType(MultipartBody.FORM)
                if (!destDir.isNullOrBlank()) {
                    builder.addFormDataPart("dest", destDir)
                }
                builder.addFormDataPart(
                    "file",
                    filename,
                    bytes.toRequestBody("application/octet-stream".toMediaTypeOrNull())
                )
                val request = Request.Builder()
                    .url(
                        HttpUrl.Builder()
                            .scheme("http")
                            .host(normalizeHost(host))
                            .port(port.toIntOrNull() ?: DEFAULT_BACKEND_PORT)
                            .addPathSegment("files")
                            .addPathSegment("upload")
                            .build()
                    )
                    .post(builder.build())
                    .build()
                val response = httpClient.newCall(request).execute()
                val body = response.body?.string().orEmpty()
                if (!response.isSuccessful) {
                    throw IllegalStateException("Upload failed: ${response.code} ${response.message} $body")
                }
                Result.success(Unit)
            } catch (e: Exception) {
                Result.failure(e)
            }
        }
    }

    suspend fun downloadFile(host: String, port: String, path: String): Result<ByteArray> {
        return withContext(Dispatchers.IO) {
            try {
                val url = HttpUrl.Builder()
                    .scheme("http")
                    .host(normalizeHost(host))
                    .port(port.toIntOrNull() ?: DEFAULT_BACKEND_PORT)
                    .addPathSegment("files")
                    .addPathSegment("download")
                    .addQueryParameter("path", path)
                    .build()
                val request = Request.Builder().url(url).get().build()
                val response = httpClient.newCall(request).execute()
                val body = response.body?.bytes()
                if (!response.isSuccessful || body == null) {
                    throw IllegalStateException("Download failed: ${response.code} ${response.message}")
                }
                Result.success(body)
            } catch (e: Exception) {
                Result.failure(e)
            }
        }
    }

    suspend fun searchProject(
        host: String,
        port: String,
        query: String,
        path: String?,
        caseSensitive: Boolean,
        regex: Boolean,
        glob: String?
    ): Result<List<SearchResult>> {
        return withContext(Dispatchers.IO) {
            try {
                val payload = JSONObject()
                    .put("query", query)
                    .put("case_sensitive", caseSensitive)
                    .put("regex", regex)
                if (!path.isNullOrBlank()) payload.put("path", path)
                if (!glob.isNullOrBlank()) payload.put("glob", glob)
                val response = postJson(host, port, "/search", payload)
                val json = JSONObject(response)
                val resultsArray = json.optJSONArray("results")
                val list = buildList {
                    if (resultsArray != null) {
                        for (i in 0 until resultsArray.length()) {
                            val item = resultsArray.getJSONObject(i)
                            add(
                                SearchResult(
                                    file = item.optString("file"),
                                    line = item.optInt("line"),
                                    column = item.optInt("column"),
                                    text = item.optString("text")
                                )
                            )
                        }
                    }
                }
                Result.success(list)
            } catch (e: Exception) {
                Result.failure(e)
            }
        }
    }

    suspend fun replaceProject(
        host: String,
        port: String,
        query: String,
        replacement: String,
        path: String?,
        caseSensitive: Boolean,
        regex: Boolean,
        glob: String?
    ): Result<ReplaceSummary> {
        return withContext(Dispatchers.IO) {
            try {
                val payload = JSONObject()
                    .put("query", query)
                    .put("replace", replacement)
                    .put("case_sensitive", caseSensitive)
                    .put("regex", regex)
                if (!path.isNullOrBlank()) payload.put("path", path)
                if (!glob.isNullOrBlank()) payload.put("glob", glob)
                val response = postJson(host, port, "/replace", payload)
                val json = JSONObject(response)
                val filesArray = json.optJSONArray("files")
                val files = buildList {
                    if (filesArray != null) {
                        for (i in 0 until filesArray.length()) {
                            add(filesArray.optString(i))
                        }
                    }
                }
                val replacements = json.optInt("replacements")
                Result.success(ReplaceSummary(files = files, replacements = replacements))
            } catch (e: Exception) {
                Result.failure(e)
            }
        }
    }

    private fun parseCodexStatus(body: String): CodexStatus {
        val json = JSONObject(body)
        return parseCodexStatusObject(json)
    }

    private fun parseCodexStatusObject(json: JSONObject): CodexStatus {
        val cwdValue = json.opt("cwd") as? String
        return CodexStatus(
            running = json.optBoolean("running", false),
            cwd = cwdValue,
            startedAt = if (json.has("started_at")) json.optLong("started_at") else null
        )
    }

    private fun parseRunnerStatusObject(json: JSONObject): RunnerStatus {
        return RunnerStatus(
            projectType = json.optString("project_type").takeIf { it.isNotBlank() },
            cwd = json.optString("cwd").takeIf { it.isNotBlank() },
            deviceId = json.optString("device_id").takeIf { it.isNotBlank() },
            mode = json.optString("mode").takeIf { it.isNotBlank() },
            metroPort = json.optInt("metro_port").takeIf { it > 0 },
            metroRunning = json.optBoolean("metro_running", false),
            appRunning = json.optBoolean("app_running", false),
            flutterRunning = json.optBoolean("flutter_running", false),
            lastError = json.optString("last_error").takeIf { it.isNotBlank() }
        )
    }

    private fun parseSessionState(body: String): SessionState {
        val json = JSONObject(body)
        val workingDir = json.optString("working_directory").takeIf { it.isNotBlank() }
        val codex = json.optJSONObject("codex")?.let { parseCodexStatusObject(it) }
        val runner = json.optJSONObject("runner")?.let { parseRunnerStatusObject(it) }
        return SessionState(
            workingDirectory = workingDir,
            codex = codex,
            runner = runner
        )
    }

    suspend fun fetchAdminStatus(host: String, port: String): Result<AdminStatus> {
        return withContext(Dispatchers.IO) {
            try {
                val url = HttpUrl.Builder()
                    .scheme("http")
                    .host(normalizeHost(host))
                    .port(port.toIntOrNull() ?: DEFAULT_BACKEND_PORT)
                    .addPathSegment("api")
                    .addPathSegment("admin")
                    .addPathSegment("status")
                    .build()
                val request = Request.Builder().url(url).get().build()
                val response = httpClient.newCall(request).execute()
                val body = response.body?.string().orEmpty()
                if (!response.isSuccessful) {
                    throw IllegalStateException("Admin status failed: ${response.code} ${response.message}")
                }
                Result.success(parseAdminStatus(body))
            } catch (e: Exception) {
                Result.failure(e)
            }
        }
    }

    suspend fun fetchAdminDeviceInfo(host: String, port: String, deviceId: String): Result<AdminDeviceInfo> {
        return withContext(Dispatchers.IO) {
            try {
                val url = HttpUrl.Builder()
                    .scheme("http")
                    .host(normalizeHost(host))
                    .port(port.toIntOrNull() ?: DEFAULT_BACKEND_PORT)
                    .addPathSegment("api")
                    .addPathSegment("admin")
                    .addPathSegment("device-info")
                    .addQueryParameter("device_id", deviceId)
                    .build()
                val request = Request.Builder().url(url).get().build()
                val response = httpClient.newCall(request).execute()
                val body = response.body?.string().orEmpty()
                if (!response.isSuccessful) {
                    throw IllegalStateException("Admin device info failed: ${response.code} ${response.message}")
                }
                Result.success(parseAdminDeviceInfo(body))
            } catch (e: Exception) {
                Result.failure(e)
            }
        }
    }

    suspend fun adminRestart(host: String, port: String, target: String): Result<Unit> {
        return withContext(Dispatchers.IO) {
            try {
                val url = HttpUrl.Builder()
                    .scheme("http")
                    .host(normalizeHost(host))
                    .port(port.toIntOrNull() ?: DEFAULT_BACKEND_PORT)
                    .addPathSegment("api")
                    .addPathSegment("admin")
                    .addPathSegment("pm2-restart")
                    .addQueryParameter("target", target)
                    .build()
                val request = Request.Builder()
                    .url(url)
                    .post("{}".toRequestBody(jsonMediaType))
                    .build()
                val response = httpClient.newCall(request).execute()
                val body = response.body?.string().orEmpty()
                if (!response.isSuccessful) {
                    throw IllegalStateException("PM2 restart failed: ${response.code} ${response.message} $body")
                }
                Result.success(Unit)
            } catch (e: Exception) {
                Result.failure(e)
            }
        }
    }

    suspend fun adminBuild(host: String, port: String, target: String): Result<Unit> {
        return withContext(Dispatchers.IO) {
            try {
                val url = HttpUrl.Builder()
                    .scheme("http")
                    .host(normalizeHost(host))
                    .port(port.toIntOrNull() ?: DEFAULT_BACKEND_PORT)
                    .addPathSegment("api")
                    .addPathSegment("admin")
                    .addPathSegment("build")
                    .addQueryParameter("target", target)
                    .build()
                val request = Request.Builder()
                    .url(url)
                    .post("{}".toRequestBody(jsonMediaType))
                    .build()
                val response = httpClient.newCall(request).execute()
                val body = response.body?.string().orEmpty()
                if (!response.isSuccessful) {
                    throw IllegalStateException("Build failed: ${response.code} ${response.message} $body")
                }
                Result.success(Unit)
            } catch (e: Exception) {
                Result.failure(e)
            }
        }
    }

    suspend fun adminInstall(host: String, port: String, target: String, deviceId: String): Result<Unit> {
        return withContext(Dispatchers.IO) {
            try {
                val url = HttpUrl.Builder()
                    .scheme("http")
                    .host(normalizeHost(host))
                    .port(port.toIntOrNull() ?: DEFAULT_BACKEND_PORT)
                    .addPathSegment("api")
                    .addPathSegment("admin")
                    .addPathSegment("install")
                    .addQueryParameter("target", target)
                    .addQueryParameter("device_id", deviceId)
                    .build()
                val request = Request.Builder()
                    .url(url)
                    .post("{}".toRequestBody(jsonMediaType))
                    .build()
                val response = httpClient.newCall(request).execute()
                val body = response.body?.string().orEmpty()
                if (!response.isSuccessful) {
                    throw IllegalStateException("Install failed: ${response.code} ${response.message} $body")
                }
                Result.success(Unit)
            } catch (e: Exception) {
                Result.failure(e)
            }
        }
    }

    private fun postJson(host: String, port: String, path: String, payload: JSONObject): String {
        val url = HttpUrl.Builder()
            .scheme("http")
            .host(normalizeHost(host))
            .port(port.toIntOrNull() ?: DEFAULT_BACKEND_PORT)
            .encodedPath(path)
            .build()
        val body = payload.toString().toRequestBody(jsonMediaType)
        val request = Request.Builder().url(url).post(body).build()
        val response = httpClient.newCall(request).execute()
        val responseBody = response.body?.string().orEmpty()
        if (!response.isSuccessful) {
            val detail = extractErrorDetail(responseBody)
            if (!detail.isNullOrBlank()) {
                throw IllegalStateException(detail)
            }
            throw IllegalStateException("Request failed: ${response.code} ${response.message} $responseBody")
        }
        return responseBody
    }

    private fun extractErrorDetail(responseBody: String): String? {
        if (responseBody.isBlank()) return null
        return try {
            val json = JSONObject(responseBody)
            json.optString("detail").takeIf { it.isNotBlank() }
        } catch (_: Exception) {
            null
        }
    }

    private fun parseRunnerStatus(body: String): RunnerStatus {
        val json = JSONObject(body)
        return RunnerStatus(
            projectType = json.optString("project_type").takeIf { it.isNotBlank() },
            cwd = json.optString("cwd").takeIf { it.isNotBlank() },
            deviceId = json.optString("device_id").takeIf { it.isNotBlank() },
            mode = json.optString("mode").takeIf { it.isNotBlank() },
            metroPort = json.optInt("metro_port").takeIf { it > 0 },
            metroRunning = json.optBoolean("metro_running", false),
            appRunning = json.optBoolean("app_running", false),
            flutterRunning = json.optBoolean("flutter_running", false),
            lastError = json.optString("last_error").takeIf { it.isNotBlank() }
        )
    }

    private fun parseAdminStatus(body: String): AdminStatus {
        val json = JSONObject(body)
        val devices = parseRunnerDevices(json.optJSONArray("devices"))
        val pm2 = parsePm2List(json.optJSONArray("pm2"))
        return AdminStatus(
            repoRoot = json.optString("repo_root").takeIf { it.isNotBlank() },
            viewerApk = json.optString("viewer_apk").takeIf { it.isNotBlank() },
            liveApk = json.optString("live_apk").takeIf { it.isNotBlank() },
            devices = devices,
            backendPort = json.optString("backend_port").takeIf { it.isNotBlank() },
            settingsPort = json.optString("settings_port").takeIf { it.isNotBlank() },
            pm2 = pm2
        )
    }

    private fun parseAdminDeviceInfo(body: String): AdminDeviceInfo {
        val json = JSONObject(body)
        val deviceJson = json.optJSONObject("device")
        val device = deviceJson?.let { parseRunnerDevice(it) }
        val viewerInfo = parsePackageInfo(json.optJSONObject("viewer"))
        val liveInfo = parsePackageInfo(json.optJSONObject("live"))
        val screenOn = if (json.has("screen_on")) json.optBoolean("screen_on") else null
        val awake = if (json.has("awake")) json.optBoolean("awake") else null
        return AdminDeviceInfo(
            device = device,
            model = json.optString("model").takeIf { it.isNotBlank() },
            screenOn = screenOn,
            awake = awake,
            viewer = viewerInfo,
            live = liveInfo
        )
    }

    private fun parsePackageInfo(json: JSONObject?): AdminPackageInfo {
        if (json == null) {
            return AdminPackageInfo(false, null, null)
        }
        return AdminPackageInfo(
            installed = json.optBoolean("installed", false),
            versionName = json.optString("version_name").takeIf { it.isNotBlank() },
            versionCode = json.optString("version_code").takeIf { it.isNotBlank() }
        )
    }

    private fun parseRunnerDevices(array: org.json.JSONArray?): List<RunnerDevice> {
        if (array == null) return emptyList()
        val list = mutableListOf<RunnerDevice>()
        for (i in 0 until array.length()) {
            val obj = array.optJSONObject(i) ?: continue
            list.add(parseRunnerDevice(obj))
        }
        return list
    }

    private fun parseRunnerDevice(obj: JSONObject): RunnerDevice {
        return RunnerDevice(
            id = obj.optString("id"),
            model = obj.optString("model"),
            product = obj.optString("product"),
            device = obj.optString("device")
        )
    }

    private fun parsePm2List(array: org.json.JSONArray?): List<Pm2Process> {
        if (array == null) return emptyList()
        val list = mutableListOf<Pm2Process>()
        for (i in 0 until array.length()) {
            val obj = array.optJSONObject(i) ?: continue
            list.add(
                Pm2Process(
                    name = obj.optString("name").takeIf { it.isNotBlank() },
                    status = obj.optString("status").takeIf { it.isNotBlank() },
                    pid = obj.optInt("pid").takeIf { it > 0 },
                    uptime = obj.optLong("uptime").takeIf { it > 0 },
                    restartTime = obj.optInt("restart_time").takeIf { it >= 0 },
                    cpu = obj.optDouble("cpu").takeIf { !it.isNaN() },
                    memory = obj.optLong("memory").takeIf { it > 0 }
                )
            )
        }
        return list
    }

    override fun onCleared() {
        super.onCleared()
        disconnect()
    }

    private fun normalizeHost(host: String): String {
        return host
            .trim()
            .removePrefix("http://")
            .removePrefix("https://")
            .removeSuffix("/")
    }
}

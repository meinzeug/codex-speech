
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

    fun connectToBackend(ip: String, port: String = "8000", workingDir: String? = null) {
        try {
            _connectionStatus.value = "Connecting..."
            val normalizedHost = normalizeHost(ip)
            val portValue = port.toIntOrNull() ?: 8000
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
    }

    suspend fun fetchDirectories(host: String, port: String, query: String): Result<DirectoryListing> {
        return withContext(Dispatchers.IO) {
            try {
                val url = HttpUrl.Builder()
                    .scheme("http")
                    .host(normalizeHost(host))
                    .port(port.toIntOrNull() ?: 8000)
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
                    .port(port.toIntOrNull() ?: 8000)
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
                    .port(port.toIntOrNull() ?: 8000)
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
                    .port(port.toIntOrNull() ?: 8000)
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
                    .port(port.toIntOrNull() ?: 8000)
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
                    .port(port.toIntOrNull() ?: 8000)
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
        deviceId: String?
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
                postJson(host, port, "/runner/open", payload)
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
                    .port(port.toIntOrNull() ?: 8000)
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
                    .port(port.toIntOrNull() ?: 8000)
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
                    .port(port.toIntOrNull() ?: 8000)
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

    private fun postJson(host: String, port: String, path: String, payload: JSONObject): String {
        val url = HttpUrl.Builder()
            .scheme("http")
            .host(normalizeHost(host))
            .port(port.toIntOrNull() ?: 8000)
            .encodedPath(path)
            .build()
        val body = payload.toString().toRequestBody(jsonMediaType)
        val request = Request.Builder().url(url).post(body).build()
        val response = httpClient.newCall(request).execute()
        val responseBody = response.body?.string().orEmpty()
        if (!response.isSuccessful) {
            throw IllegalStateException("Request failed: ${response.code} ${response.message} $responseBody")
        }
        return responseBody
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

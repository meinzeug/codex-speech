
package com.example.codexmobile

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import okhttp3.HttpUrl
import okhttp3.MultipartBody
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.Response
import okhttp3.WebSocket
import okhttp3.WebSocketListener
import okhttp3.MediaType.Companion.toMediaTypeOrNull
import okhttp3.RequestBody.Companion.asRequestBody
import okio.ByteString
import org.json.JSONObject
import java.io.File

class CodexViewModel : ViewModel() {
    private val client = OkHttpClient()
    private var webSocket: WebSocket? = null
    private var lastHost: String? = null
    private var lastPort: Int? = null

    private val _terminalOutput = MutableSharedFlow<String>(extraBufferCapacity = 256)
    val terminalOutput = _terminalOutput.asSharedFlow()

    private val _connectionStatus = MutableStateFlow("Disconnected")
    val connectionStatus = _connectionStatus.asStateFlow()

    private val _sttStatus = MutableStateFlow("Idle")
    val sttStatus = _sttStatus.asStateFlow()

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
            webSocket = client.newWebSocket(request, object : WebSocketListener() {
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

    suspend fun transcribeAudio(file: File, language: String? = null): Result<String> {
        val host = lastHost
        val port = lastPort
        if (host.isNullOrBlank() || port == null) {
            return Result.failure(IllegalStateException("No backend connected"))
        }
        return withContext(Dispatchers.IO) {
            try {
                _sttStatus.value = "Transcribing..."
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
                val response = client.newCall(request).execute()
                val body = response.body?.string().orEmpty()
                if (!response.isSuccessful) {
                    throw IllegalStateException("STT failed: ${response.code} ${response.message} $body")
                }
                val json = JSONObject(body)
                val text = json.optString("text", "")
                _sttStatus.value = "Idle"
                Result.success(text)
            } catch (e: Exception) {
                _sttStatus.value = "Error: ${e.message}"
                Result.failure(e)
            }
        }
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

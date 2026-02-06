package com.meinzeug.codexspeech.viewer

import android.Manifest
import android.content.pm.PackageManager
import android.graphics.BitmapFactory
import android.content.res.Configuration
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.clickable
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.expandVertically
import androidx.compose.animation.shrinkVertically
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.animation.core.tween
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.background
import androidx.compose.foundation.Image
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.FiberManualRecord
import androidx.compose.material.icons.filled.Mic
import androidx.compose.material.icons.filled.ArrowDropDown
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.KeyboardArrowDown
import androidx.compose.material.icons.filled.KeyboardArrowUp
import androidx.compose.material.icons.filled.Menu
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.Stop
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Divider
import androidx.compose.material3.Button
import androidx.compose.material3.DrawerValue
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalDrawerSheet
import androidx.compose.material3.ModalNavigationDrawer
import androidx.compose.material3.NavigationDrawerItem
import androidx.compose.material3.NavigationDrawerItemDefaults
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Slider
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.material3.rememberDrawerState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.input.pointer.changedToUp
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.input.pointer.positionChanged
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalViewConfiguration
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.compose.ui.window.Dialog
import androidx.core.content.ContextCompat
import androidx.lifecycle.viewmodel.compose.viewModel
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlin.math.roundToInt
import java.io.File
import java.util.Locale

private enum class RecordingMode {
    MANUAL,
    AUTO
}

private enum class AppScreen {
    HOME,
    RUNNER,
    LIVE_PHONE
}

private val CodexLightColors = lightColorScheme(
    primary = Color(0xFF1D4ED8),
    onPrimary = Color.White,
    secondary = Color(0xFF0EA5E9),
    onSecondary = Color.White,
    background = Color(0xFFF4F6FA),
    onBackground = Color(0xFF111827),
    surface = Color(0xFFFFFFFF),
    onSurface = Color(0xFF111827),
    surfaceVariant = Color(0xFFE5E7EB),
    onSurfaceVariant = Color(0xFF111827),
    error = Color(0xFFB91C1C),
    onError = Color.White
)

private val CodexDarkColors = darkColorScheme(
    primary = Color(0xFF60A5FA),
    onPrimary = Color(0xFF0B1220),
    secondary = Color(0xFF38BDF8),
    onSecondary = Color(0xFF0B1220),
    background = Color(0xFF0B1220),
    onBackground = Color(0xFFE5E7EB),
    surface = Color(0xFF111827),
    onSurface = Color(0xFFE5E7EB),
    surfaceVariant = Color(0xFF1F2937),
    onSurfaceVariant = Color(0xFFE5E7EB),
    error = Color(0xFFF87171),
    onError = Color(0xFF0B1220)
)

@Composable
private fun CodexSpeechTheme(content: @Composable () -> Unit) {
    val colors = if (isSystemInDarkTheme()) CodexDarkColors else CodexLightColors
    MaterialTheme(colorScheme = colors, content = content)
}

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent {
            CodexSpeechApp()
        }
    }
}

@Composable
@OptIn(ExperimentalMaterial3Api::class)
private fun CodexSpeechApp(viewModel: CodexViewModel = viewModel()) {
    val connectionStatus by viewModel.connectionStatus.collectAsState()
    val sttStatus by viewModel.sttStatus.collectAsState()
    val runnerStatus by viewModel.runnerStatus.collectAsState()
    val runnerLogs by viewModel.runnerLogs.collectAsState()

    var ip by remember { mutableStateOf("") }
    var port by remember { mutableStateOf("17500") }
    var command by remember { mutableStateOf("") }
    var workingDir by remember { mutableStateOf("") }
    val configuration = LocalConfiguration.current
    val isLandscape = configuration.orientation == Configuration.ORIENTATION_LANDSCAPE
    val contentPadding = if (isLandscape) 8.dp else 16.dp

    val isConnected = connectionStatus == "Connected"
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val terminalController = remember { TerminalController(context, viewModel::sendRaw) }
    val audioRecorder = remember { AudioRecorder(context) }
    var recordingMode by remember { mutableStateOf<RecordingMode?>(null) }
    var pendingRecordingMode by remember { mutableStateOf<RecordingMode?>(null) }

    val serverStore = remember { ServerStore(context) }
    var servers by remember { mutableStateOf(serverStore.load()) }
    var selectedServerId by remember { mutableStateOf(serverStore.loadSelectedServerId()) }

    var showServerManager by remember { mutableStateOf(false) }
    var editingServer by remember { mutableStateOf<ServerProfile?>(null) }
    var showWorkingDirDialog by remember { mutableStateOf(false) }
    var autoFit by remember { mutableStateOf(terminalController.isAutoFitEnabled()) }
    var currentScreen by remember { mutableStateOf(AppScreen.HOME) }
    val drawerState = rememberDrawerState(DrawerValue.Closed)
    var serverPanelExpanded by remember { mutableStateOf(true) }
    var runnerTypeChoice by remember { mutableStateOf("auto") }
    var runnerDetectedType by remember { mutableStateOf<String?>(null) }
    var runnerPackageName by remember { mutableStateOf<String?>(null) }
    var runnerProjects by remember { mutableStateOf<List<RunnerProject>>(emptyList()) }
    var selectedRunnerProjectPath by remember { mutableStateOf<String?>(null) }
    var runnerScanDepth by remember { mutableStateOf(2) }
    var runnerDevices by remember { mutableStateOf<List<RunnerDevice>>(emptyList()) }
    var selectedRunnerDeviceId by remember { mutableStateOf<String?>(null) }
    var runnerMetroPort by remember { mutableStateOf("8081") }
    var runnerMode by remember { mutableStateOf("adb") }
    var runnerMessage by remember { mutableStateOf<String?>(null) }
    var liveDevices by remember { mutableStateOf<List<RunnerDevice>>(emptyList()) }
    var selectedLiveDeviceId by remember { mutableStateOf<String?>(null) }
    var liveFrame by remember { mutableStateOf<ImageBitmap?>(null) }
    var liveStreaming by remember { mutableStateOf(false) }
    var liveMessage by remember { mutableStateOf<String?>(null) }
    var liveFps by remember { mutableStateOf(5) }
    var liveFormat by remember { mutableStateOf("jpeg") }
    var liveJpegQuality by remember { mutableStateOf(70) }
    var livePreviewSize by remember { mutableStateOf(IntSize.Zero) }
    var liveText by remember { mutableStateOf("") }

    LaunchedEffect(isConnected) {
        serverPanelExpanded = !isConnected
        if (!isConnected) {
            liveStreaming = false
        }
    }

    LaunchedEffect(Unit) {
        viewModel.terminalOutput.collectLatest { chunk ->
            terminalController.writeOutput(chunk)
        }
    }

    LaunchedEffect(selectedServerId, servers) {
        val server = servers.firstOrNull { it.id == selectedServerId }
        if (server != null) {
            ip = server.host
            port = server.port
            workingDir = server.workingDir
        }
    }

    fun refreshRunnerDevices() {
        if (!isConnected || ip.isBlank()) return
        scope.launch {
            val result = viewModel.fetchRunnerDevices(ip.trim(), port.trim())
            if (result.isSuccess) {
                runnerDevices = result.getOrDefault(emptyList())
                if (selectedRunnerDeviceId == null && runnerDevices.isNotEmpty()) {
                    selectedRunnerDeviceId = runnerDevices.first().id
                }
            } else {
                runnerMessage = result.exceptionOrNull()?.message
                runnerPackageName = null
            }
        }
    }

    fun refreshLiveDevices() {
        if (!isConnected || ip.isBlank()) return
        scope.launch {
            val result = viewModel.fetchRunnerDevices(ip.trim(), port.trim())
            if (result.isSuccess) {
                liveDevices = result.getOrDefault(emptyList())
                if (selectedLiveDeviceId == null && liveDevices.isNotEmpty()) {
                    selectedLiveDeviceId = liveDevices.first().id
                }
            } else {
                liveMessage = result.exceptionOrNull()?.message
            }
        }
    }

    fun installLiveHelper() {
        if (!isConnected || ip.isBlank()) return
        scope.launch {
            val result = viewModel.installLiveHelper(ip.trim(), port.trim(), selectedLiveDeviceId)
            liveMessage = if (result.isSuccess) "Live helper installed." else result.exceptionOrNull()?.message
        }
    }

    fun openLiveHelper() {
        if (!isConnected || ip.isBlank()) return
        scope.launch {
            val result = viewModel.openLiveHelper(ip.trim(), port.trim(), selectedLiveDeviceId)
            liveMessage = if (result.isSuccess) "Live helper opened." else result.exceptionOrNull()?.message
        }
    }

    fun mapLivePoint(point: Offset): Pair<Int, Int>? {
        val frame = liveFrame ?: return null
        if (livePreviewSize.width == 0 || livePreviewSize.height == 0) return null
        val x = (point.x / livePreviewSize.width * frame.width).roundToInt().coerceIn(0, frame.width - 1)
        val y = (point.y / livePreviewSize.height * frame.height).roundToInt().coerceIn(0, frame.height - 1)
        return x to y
    }

    fun sendLiveTap(point: Offset) {
        val mapped = mapLivePoint(point) ?: return
        if (!isConnected || ip.isBlank()) return
        scope.launch {
            viewModel.sendLiveTap(ip.trim(), port.trim(), selectedLiveDeviceId, mapped.first, mapped.second)
        }
    }

    fun sendLiveSwipe(start: Offset, end: Offset) {
        val mappedStart = mapLivePoint(start) ?: return
        val mappedEnd = mapLivePoint(end) ?: return
        if (!isConnected || ip.isBlank()) return
        scope.launch {
            viewModel.sendLiveSwipe(
                ip.trim(),
                port.trim(),
                selectedLiveDeviceId,
                mappedStart.first,
                mappedStart.second,
                mappedEnd.first,
                mappedEnd.second,
                280
            )
        }
    }

    fun sendLiveLongPress(point: Offset) {
        val mapped = mapLivePoint(point) ?: return
        if (!isConnected || ip.isBlank()) return
        scope.launch {
            viewModel.sendLiveLongPress(ip.trim(), port.trim(), selectedLiveDeviceId, mapped.first, mapped.second)
        }
    }

    fun sendLiveKey(keyCode: Int) {
        if (!isConnected || ip.isBlank()) return
        scope.launch {
            viewModel.sendLiveKey(ip.trim(), port.trim(), selectedLiveDeviceId, keyCode)
        }
    }

    fun sendLiveText() {
        val text = liveText.trim()
        if (text.isBlank()) return
        if (!isConnected || ip.isBlank()) return
        scope.launch {
            val result = viewModel.sendLiveText(ip.trim(), port.trim(), selectedLiveDeviceId, text)
            if (result.isSuccess) {
                liveText = ""
            } else {
                liveMessage = result.exceptionOrNull()?.message
            }
        }
    }

    fun wakeLiveDevice() {
        if (!isConnected || ip.isBlank()) return
        scope.launch {
            val result = viewModel.wakeLiveDevice(ip.trim(), port.trim(), selectedLiveDeviceId)
            liveMessage = if (result.isSuccess) "Wake sent." else result.exceptionOrNull()?.message
        }
    }

    LaunchedEffect(currentScreen) {
        if (currentScreen == AppScreen.LIVE_PHONE) {
            refreshLiveDevices()
        } else if (currentScreen != AppScreen.LIVE_PHONE && liveStreaming) {
            liveStreaming = false
        }
    }

    LaunchedEffect(liveStreaming, selectedLiveDeviceId, ip, port, liveFps, liveFormat, liveJpegQuality) {
        if (!liveStreaming || ip.isBlank()) return@LaunchedEffect
        while (liveStreaming) {
            val result = viewModel.fetchLiveSnapshot(
                ip.trim(),
                port.trim(),
                selectedLiveDeviceId,
                liveFormat,
                liveJpegQuality
            )
            if (result.isSuccess) {
                val bytes = result.getOrDefault(ByteArray(0))
                val bitmap = BitmapFactory.decodeByteArray(bytes, 0, bytes.size)
                if (bitmap != null) {
                    liveFrame = bitmap.asImageBitmap()
                }
                liveMessage = null
            } else {
                liveMessage = result.exceptionOrNull()?.message
            }
            val interval = (1000f / liveFps.coerceAtLeast(1)).toLong().coerceAtLeast(120L)
            delay(interval)
        }
    }

    fun detectRunnerProject() {
        if (!isConnected || ip.isBlank()) return
        scope.launch {
            val result = viewModel.scanRunnerProjects(
                ip.trim(),
                port.trim(),
                workingDir.trim().ifBlank { null },
                runnerScanDepth
            )
            if (result.isSuccess) {
                val projects = result.getOrDefault(emptyList())
                runnerProjects = projects
                val selected = when {
                    projects.isEmpty() -> null
                    projects.size == 1 -> projects.first()
                    selectedRunnerProjectPath != null -> projects.firstOrNull { it.path == selectedRunnerProjectPath }
                    else -> null
                }
                selectedRunnerProjectPath = selected?.path
                runnerDetectedType = selected?.projectType
                runnerPackageName = selected?.androidPackage
                runnerMessage = if (projects.isEmpty()) "No projects found." else null
            } else {
                runnerMessage = result.exceptionOrNull()?.message
                runnerPackageName = null
                runnerProjects = emptyList()
                selectedRunnerProjectPath = null
                runnerDetectedType = null
            }
        }
    }

    fun startRunner() {
        if (!isConnected || ip.isBlank()) return
        val selectedType = if (runnerTypeChoice == "auto") runnerDetectedType else runnerTypeChoice
        val deviceId = selectedRunnerDeviceId ?: runnerDevices.firstOrNull()?.id
        if (deviceId != null && selectedRunnerDeviceId == null) {
            selectedRunnerDeviceId = deviceId
        }
        val runnerPath = selectedRunnerProjectPath ?: workingDir.trim().ifBlank { null }
        scope.launch {
            val result = viewModel.startRunner(
                host = ip.trim(),
                port = port.trim(),
                path = runnerPath,
                projectType = selectedType,
                deviceId = deviceId,
                mode = runnerMode,
                metroPort = runnerMetroPort.toIntOrNull() ?: 8081
            )
            if (result.isFailure) {
                runnerMessage = result.exceptionOrNull()?.message
            } else {
                runnerMessage = null
                if (runnerMode == "lan" && selectedType == "react-native") {
                    val hostResult = viewModel.setReactNativeHost(
                        host = ip.trim(),
                        port = port.trim(),
                        path = runnerPath,
                        packageName = runnerPackageName,
                        deviceId = deviceId,
                        metroHost = ip.trim(),
                        metroPort = runnerMetroPort.toIntOrNull() ?: 8081
                    )
                    if (hostResult.isFailure) {
                        runnerMessage = hostResult.exceptionOrNull()?.message
                    } else {
                        runnerMessage = "Debug host set."
                    }
                }
                val openResult = viewModel.openRunnerApp(
                    host = ip.trim(),
                    port = port.trim(),
                    packageName = runnerPackageName,
                    path = runnerPath,
                    deviceId = deviceId
                )
                if (openResult.isFailure) {
                    val msg = openResult.exceptionOrNull()?.message
                    runnerMessage = if (!runnerMessage.isNullOrBlank()) {
                        "${runnerMessage} Open app failed: $msg"
                    } else {
                        msg
                    }
                }
            }
        }
    }

    fun stopRunner() {
        if (!isConnected || ip.isBlank()) return
        scope.launch {
            val result = viewModel.stopRunner(ip.trim(), port.trim())
            if (result.isFailure) {
                runnerMessage = result.exceptionOrNull()?.message
            } else {
                runnerMessage = null
            }
        }
    }

    fun reloadRunner(type: String) {
        if (!isConnected || ip.isBlank()) return
        scope.launch {
            val result = viewModel.reloadRunner(ip.trim(), port.trim(), type)
            if (result.isFailure) {
                runnerMessage = result.exceptionOrNull()?.message
            }
        }
    }

    fun openDevMenu() {
        if (!isConnected || ip.isBlank()) return
        scope.launch {
            val result = viewModel.openDevMenu(ip.trim(), port.trim(), selectedRunnerDeviceId)
            if (result.isFailure) {
                runnerMessage = result.exceptionOrNull()?.message
            }
        }
    }

    fun openRunnerApp() {
        if (!isConnected || ip.isBlank()) return
        scope.launch {
            val runnerPath = selectedRunnerProjectPath ?: workingDir.trim().ifBlank { null }
            val result = viewModel.openRunnerApp(
                ip.trim(),
                port.trim(),
                runnerPackageName,
                runnerPath,
                selectedRunnerDeviceId
            )
            if (result.isFailure) {
                runnerMessage = result.exceptionOrNull()?.message
            }
        }
    }

    fun setReactNativeHost() {
        if (!isConnected || ip.isBlank()) return
        val runnerPath = selectedRunnerProjectPath ?: workingDir.trim().ifBlank { null }
        scope.launch {
            val result = viewModel.setReactNativeHost(
                host = ip.trim(),
                port = port.trim(),
                path = runnerPath,
                packageName = runnerPackageName,
                deviceId = selectedRunnerDeviceId,
                metroHost = ip.trim(),
                metroPort = runnerMetroPort.toIntOrNull() ?: 8081
            )
            if (result.isFailure) {
                runnerMessage = result.exceptionOrNull()?.message
            } else {
                runnerMessage = "Debug host set."
            }
        }
    }

    fun reloadReactNative() {
        if (!isConnected || ip.isBlank()) return
        scope.launch {
            val result = viewModel.reloadReactNative(ip.trim(), port.trim(), selectedRunnerDeviceId)
            if (result.isFailure) {
                runnerMessage = result.exceptionOrNull()?.message
            }
        }
    }

    fun applyRunnerProject(project: RunnerProject?) {
        if (project == null) {
            selectedRunnerProjectPath = null
            runnerDetectedType = null
            runnerPackageName = null
            return
        }
        selectedRunnerProjectPath = project.path
        runnerDetectedType = project.projectType
        runnerPackageName = project.androidPackage
    }

    LaunchedEffect(isConnected, workingDir) {
        if (isConnected) {
            detectRunnerProject()
            refreshRunnerDevices()
        }
    }

    val runnerActive = runnerStatus?.let { it.metroRunning || it.appRunning || it.flutterRunning } == true
    LaunchedEffect(isConnected, runnerActive) {
        if (!isConnected || !runnerActive) return@LaunchedEffect
        while (true) {
            viewModel.refreshRunnerStatus(ip.trim(), port.trim())
            val result = viewModel.refreshRunnerLogs(ip.trim(), port.trim())
            if (result.isFailure) {
                runnerMessage = result.exceptionOrNull()?.message
            }
            delay(2000)
        }
    }

    DisposableEffect(Unit) {
        onDispose {
            terminalController.dispose()
        }
    }

    fun startRecording(mode: RecordingMode) {
        val file = audioRecorder.start()
        if (file != null) {
            recordingMode = mode
        }
    }

    val permissionLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { granted ->
        if (granted) {
            pendingRecordingMode?.let { startRecording(it) }
        }
        pendingRecordingMode = null
    }

    fun ensureRecordingPermission(mode: RecordingMode) {
        val granted = ContextCompat.checkSelfPermission(
            context,
            Manifest.permission.RECORD_AUDIO
        ) == PackageManager.PERMISSION_GRANTED
        if (granted) {
            startRecording(mode)
        } else {
            pendingRecordingMode = mode
            permissionLauncher.launch(Manifest.permission.RECORD_AUDIO)
        }
    }

    fun sendWithEnter(text: String) {
        val payload = text.trimEnd('\r', '\n')
        if (payload.isBlank()) return
        scope.launch {
            viewModel.sendCommand(payload)
            delay(40)
            viewModel.sendCommand("\r")
        }
    }

    fun stopRecordingAndTranscribe(mode: RecordingMode) {
        val file = audioRecorder.stop()
        recordingMode = null
        if (file == null) return
        scope.launch {
            val language = Locale.getDefault().language
            val result = viewModel.transcribeAudio(file, language = language)
            file.safeDelete()
            if (result.isSuccess) {
                val text = result.getOrDefault("").trim()
                if (text.isNotBlank()) {
                    if (mode == RecordingMode.AUTO && isConnected) {
                        sendWithEnter(text)
                        command = ""
                    } else {
                        command = text
                    }
                }
            }
        }
    }

    fun toggleManualRecording() {
        if (recordingMode == RecordingMode.MANUAL) {
            stopRecordingAndTranscribe(RecordingMode.MANUAL)
        } else if (recordingMode == null) {
            ensureRecordingPermission(RecordingMode.MANUAL)
        }
    }

    fun toggleAutoRecording() {
        if (recordingMode == RecordingMode.AUTO) {
            stopRecordingAndTranscribe(RecordingMode.AUTO)
        } else if (recordingMode == null) {
            ensureRecordingPermission(RecordingMode.AUTO)
        }
    }

    fun applyServer(server: ServerProfile) {
        selectedServerId = server.id
        serverStore.saveSelectedServerId(server.id)
        ip = server.host
        port = server.port
        workingDir = server.workingDir
    }

    fun selectServer(server: ServerProfile?) {
        if (server == null) {
            selectedServerId = null
            serverStore.saveSelectedServerId(null)
            return
        }
        applyServer(server)
    }

    val statusColor = when {
        connectionStatus.startsWith("Connected") -> Color(0xFF16A34A)
        connectionStatus.startsWith("Connecting") -> Color(0xFFF59E0B)
        else -> Color(0xFFDC2626)
    }

    CodexSpeechTheme {
        ModalNavigationDrawer(
            drawerState = drawerState,
            drawerContent = {
                ModalDrawerSheet {
                    Spacer(modifier = Modifier.height(12.dp))
                    NavigationDrawerItem(
                        label = { Text("Home") },
                        selected = currentScreen == AppScreen.HOME,
                        onClick = {
                            currentScreen = AppScreen.HOME
                            scope.launch { drawerState.close() }
                        },
                        modifier = Modifier.padding(NavigationDrawerItemDefaults.ItemPadding)
                    )
                    NavigationDrawerItem(
                        label = { Text("App Hotload") },
                        selected = currentScreen == AppScreen.RUNNER,
                        onClick = {
                            currentScreen = AppScreen.RUNNER
                            scope.launch { drawerState.close() }
                        },
                        modifier = Modifier.padding(NavigationDrawerItemDefaults.ItemPadding)
                    )
                    NavigationDrawerItem(
                        label = { Text("Live Phone") },
                        selected = currentScreen == AppScreen.LIVE_PHONE,
                        onClick = {
                            currentScreen = AppScreen.LIVE_PHONE
                            scope.launch { drawerState.close() }
                        },
                        modifier = Modifier.padding(NavigationDrawerItemDefaults.ItemPadding)
                    )
                }
            }
        ) {
            Scaffold(
                topBar = {
                    Column {
                        androidx.compose.material3.TopAppBar(
                            title = {
                                Row(
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                                ) {
                                    Image(
                                        painter = painterResource(id = R.drawable.ic_launcher_foreground),
                                        contentDescription = "Codex Speech",
                                        modifier = Modifier.height(24.dp)
                                    )
                                    Text("Codex Speech")
                                }
                            },
                            navigationIcon = {
                                IconButton(onClick = { scope.launch { drawerState.open() } }) {
                                    Icon(Icons.Default.Menu, contentDescription = "Menu")
                                }
                            },
                            actions = {
                                Box(
                                    modifier = Modifier
                                        .padding(end = 8.dp)
                                        .height(12.dp)
                                        .width(12.dp)
                                        .background(statusColor, CircleShape)
                                )
                                IconButton(onClick = { serverPanelExpanded = !serverPanelExpanded }) {
                                    Icon(
                                        imageVector = if (serverPanelExpanded) Icons.Default.KeyboardArrowUp else Icons.Default.KeyboardArrowDown,
                                        contentDescription = if (serverPanelExpanded) "Collapse server panel" else "Expand server panel"
                                    )
                                }
                            }
                        )
                        AnimatedVisibility(
                            visible = serverPanelExpanded,
                            enter = slideInVertically(
                                animationSpec = tween(durationMillis = 220),
                                initialOffsetY = { -it }
                            ) + expandVertically(expandFrom = Alignment.Top),
                            exit = slideOutVertically(
                                animationSpec = tween(durationMillis = 180),
                                targetOffsetY = { -it }
                            ) + shrinkVertically(shrinkTowards = Alignment.Top)
                        ) {
                            Surface(
                                tonalElevation = 2.dp,
                                modifier = Modifier.fillMaxWidth()
                            ) {
                                Column(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .padding(contentPadding),
                                    verticalArrangement = Arrangement.spacedBy(8.dp)
                                ) {
                                    ConnectionSection(
                                        ip = ip,
                                        onIpChange = { ip = it },
                                        port = port,
                                        onPortChange = { port = it },
                                        isConnected = isConnected,
                                        onToggleConnection = {
                                            if (isConnected) {
                                                viewModel.disconnect()
                                            } else {
                                                viewModel.connectToBackend(
                                                    ip.trim(),
                                                    port.trim(),
                                                    workingDir.trim().ifBlank { null }
                                                )
                                            }
                                        },
                                        servers = servers,
                                        selectedServerId = selectedServerId,
                                        onSelectServer = { selectServer(it) },
                                        onManageServers = { showServerManager = true },
                                        onAddServer = {
                                            editingServer = ServerProfile(
                                                name = "",
                                                host = ip,
                                                port = port,
                                                workingDir = workingDir
                                            )
                                        },
                                        onOpenWorkingDir = { showWorkingDirDialog = true },
                                        workingDir = workingDir,
                                        stacked = true
                                    )
                                }
                            }
                        }
                    }
                }
            ) { innerPadding ->
                Surface(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(innerPadding),
                    color = MaterialTheme.colorScheme.background
                ) {
                    when (currentScreen) {
                        AppScreen.HOME -> {
                            if (isLandscape) {
                                Row(
                                    modifier = Modifier
                                        .fillMaxSize()
                                        .padding(contentPadding)
                                        .imePadding(),
                                    horizontalArrangement = Arrangement.spacedBy(12.dp)
                                ) {
                                    Column(
                                        modifier = Modifier
                                            .weight(0.45f)
                                            .fillMaxSize()
                                            .verticalScroll(rememberScrollState()),
                                        verticalArrangement = Arrangement.spacedBy(12.dp)
                                    ) {
                                        CommandSection(
                                            command = command,
                                            onCommandChange = { command = it },
                                            isConnected = isConnected,
                                            onSend = {
                                                sendWithEnter(command)
                                                command = ""
                                            },
                                            isRecordingManual = recordingMode == RecordingMode.MANUAL,
                                            isRecordingAuto = recordingMode == RecordingMode.AUTO,
                                            onToggleManualRecording = ::toggleManualRecording,
                                            onToggleAutoRecording = ::toggleAutoRecording,
                                            sttStatus = sttStatus
                                        )
                                    }
                                    Column(
                                        modifier = Modifier
                                            .weight(0.55f)
                                            .fillMaxSize()
                                    ) {
                                        TerminalSection(
                                            terminalController = terminalController,
                                            modifier = Modifier.fillMaxSize(),
                                            fillHeight = true,
                                            autoFit = autoFit,
                                            onAutoFitChanged = { autoFit = it }
                                        )
                                    }
                                }
                            } else {
                                Column(
                                    modifier = Modifier
                                        .fillMaxSize()
                                        .padding(contentPadding)
                                        .imePadding()
                                ) {
                                    TerminalSection(
                                        terminalController = terminalController,
                                        modifier = Modifier.weight(1f),
                                        fillHeight = true,
                                        autoFit = autoFit,
                                        onAutoFitChanged = { autoFit = it }
                                    )

                                    Spacer(modifier = Modifier.height(16.dp))

                                    CommandSection(
                                        command = command,
                                        onCommandChange = { command = it },
                                        isConnected = isConnected,
                                        onSend = {
                                            sendWithEnter(command)
                                            command = ""
                                        },
                                        isRecordingManual = recordingMode == RecordingMode.MANUAL,
                                        isRecordingAuto = recordingMode == RecordingMode.AUTO,
                                        onToggleManualRecording = ::toggleManualRecording,
                                        onToggleAutoRecording = ::toggleAutoRecording,
                                        sttStatus = sttStatus
                                    )
                                }
                            }
                        }
                        AppScreen.RUNNER -> {
                            Column(
                                modifier = Modifier
                                    .fillMaxSize()
                                    .padding(contentPadding)
                                    .verticalScroll(rememberScrollState()),
                                verticalArrangement = Arrangement.spacedBy(12.dp)
                            ) {
                                Text(text = "App Hotload", style = MaterialTheme.typography.titleMedium)
                                Divider()
                                RunnerSection(
                                    isConnected = isConnected,
                                    serverHost = ip.trim(),
                                    workingDir = workingDir,
                                    detectedType = runnerDetectedType,
                                    packageName = runnerPackageName,
                                    projects = runnerProjects,
                                    selectedProjectPath = selectedRunnerProjectPath,
                                    onSelectProject = ::applyRunnerProject,
                                    scanDepth = runnerScanDepth,
                                    onScanDepthChange = { runnerScanDepth = it },
                                    typeChoice = runnerTypeChoice,
                                    onTypeChoice = { runnerTypeChoice = it },
                                    devices = runnerDevices,
                                    selectedDeviceId = selectedRunnerDeviceId,
                                    onSelectDevice = { selectedRunnerDeviceId = it },
                                    onRefreshDevices = ::refreshRunnerDevices,
                                    status = runnerStatus,
                                    logs = runnerLogs,
                                    runnerMessage = runnerMessage,
                                    onDetect = ::detectRunnerProject,
                                    onStart = ::startRunner,
                                    onStop = ::stopRunner,
                                    onReload = { reloadRunner("hot") },
                                    onRestart = { reloadRunner("restart") },
                                    onDevMenu = ::openDevMenu,
                                    onOpenRunner = ::openRunnerApp,
                                    onSetDebugHost = ::setReactNativeHost,
                                    onReloadJs = ::reloadReactNative,
                                    metroPort = runnerMetroPort,
                                    onMetroPortChange = { runnerMetroPort = it },
                                    mode = runnerMode,
                                    onModeChange = { runnerMode = it }
                                )
                            }
                        }
                        AppScreen.LIVE_PHONE -> {
                            Column(
                                modifier = Modifier
                                    .fillMaxSize()
                                    .padding(contentPadding)
                                    .verticalScroll(rememberScrollState()),
                                verticalArrangement = Arrangement.spacedBy(12.dp)
                            ) {
                                Text(text = "Live Phone", style = MaterialTheme.typography.titleMedium)
                                Divider()

                                DeviceDropdown(
                                    devices = liveDevices,
                                    selectedDeviceId = selectedLiveDeviceId,
                                    onSelectDevice = { selectedLiveDeviceId = it },
                                    modifier = Modifier.fillMaxWidth()
                                )
                                Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                                    Text(text = "FPS: $liveFps", style = MaterialTheme.typography.bodySmall)
                                    Slider(
                                        value = liveFps.toFloat(),
                                        onValueChange = { liveFps = it.roundToInt().coerceIn(1, 20) },
                                        valueRange = 1f..20f,
                                        steps = 18
                                    )
                                }
                                FormatDropdown(
                                    format = liveFormat,
                                    onFormatChange = { liveFormat = it },
                                    modifier = Modifier.fillMaxWidth()
                                )
                                if (liveFormat == "jpeg") {
                                    Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                                        Text(text = "JPEG Quality: $liveJpegQuality", style = MaterialTheme.typography.bodySmall)
                                        Slider(
                                            value = liveJpegQuality.toFloat(),
                                            onValueChange = { liveJpegQuality = it.roundToInt().coerceIn(30, 100) },
                                            valueRange = 30f..100f,
                                            steps = 69
                                        )
                                    }
                                }
                                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                    OutlinedButton(
                                        onClick = { refreshLiveDevices() },
                                        enabled = isConnected
                                    ) { Text("Refresh Devices") }
                                    OutlinedButton(
                                        onClick = { installLiveHelper() },
                                        enabled = isConnected
                                    ) { Text("Install Live APK") }
                                }
                                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                    OutlinedButton(
                                        onClick = { openLiveHelper() },
                                        enabled = isConnected
                                    ) { Text("Open Live APK") }
                                    Button(
                                        onClick = { liveStreaming = true },
                                        enabled = isConnected
                                    ) { Text("Start Stream") }
                                    OutlinedButton(
                                        onClick = { liveStreaming = false },
                                        enabled = liveStreaming
                                    ) { Text("Stop") }
                                }
                                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                    OutlinedButton(
                                        onClick = { wakeLiveDevice() },
                                        enabled = isConnected
                                    ) { Text("Wake up") }
                                    OutlinedButton(
                                        onClick = { sendLiveKey(4) },
                                        enabled = isConnected
                                    ) { Text("Back") }
                                    OutlinedButton(
                                        onClick = { sendLiveKey(3) },
                                        enabled = isConnected
                                    ) { Text("Home") }
                                    OutlinedButton(
                                        onClick = { sendLiveKey(187) },
                                        enabled = isConnected
                                    ) { Text("Overview") }
                                }
                                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                    OutlinedButton(
                                        onClick = { sendLiveKey(24) },
                                        enabled = isConnected
                                    ) { Text("Vol +") }
                                    OutlinedButton(
                                        onClick = { sendLiveKey(25) },
                                        enabled = isConnected
                                    ) { Text("Vol -") }
                                }
                                Row(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
                                    OutlinedTextField(
                                        value = liveText,
                                        onValueChange = { liveText = it },
                                        label = { Text("Text input") },
                                        singleLine = true,
                                        modifier = Modifier.weight(1f)
                                    )
                                    Button(
                                        onClick = { sendLiveText() },
                                        enabled = isConnected && liveText.isNotBlank()
                                    ) { Text("Send") }
                                }

                                val viewConfig = LocalViewConfiguration.current
                                val frameAspect = liveFrame?.let { it.width.toFloat() / it.height.toFloat() } ?: (9f / 16f)
                                Box(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .aspectRatio(frameAspect)
                                        .background(MaterialTheme.colorScheme.surfaceVariant)
                                        .onSizeChanged { livePreviewSize = it }
                                        .pointerInput(liveFrame, livePreviewSize, isConnected) {
                                            awaitEachGesture {
                                                val down = awaitFirstDown()
                                                val start = down.position
                                                var current = start
                                                var dragDetected = false
                                                var longPressTriggered = false
                                                val startTime = down.uptimeMillis
                                                val longPressTimeout = viewConfig.longPressTimeoutMillis.toLong()
                                                while (true) {
                                                    val event = awaitPointerEvent()
                                                    val change = event.changes.firstOrNull() ?: break
                                                    if (!longPressTriggered && !dragDetected) {
                                                        val elapsed = change.uptimeMillis - startTime
                                                        if (elapsed >= longPressTimeout) {
                                                            longPressTriggered = true
                                                            sendLiveLongPress(start)
                                                        }
                                                    }
                                                    if (change.positionChanged()) {
                                                        current = change.position
                                                        val distance = (current - start).getDistance()
                                                        if (distance > viewConfig.touchSlop) {
                                                            dragDetected = true
                                                        }
                                                    }
                                                    if (change.changedToUp()) {
                                                        break
                                                    }
                                                }
                                                when {
                                                    dragDetected && !longPressTriggered -> sendLiveSwipe(start, current)
                                                    !longPressTriggered -> sendLiveTap(start)
                                                }
                                            }
                                        }
                                ) {
                                    if (liveFrame != null) {
                                        Image(
                                            bitmap = liveFrame!!,
                                            contentDescription = "Live phone",
                                            modifier = Modifier.fillMaxSize(),
                                            contentScale = ContentScale.FillBounds
                                        )
                                    } else {
                                        Text(
                                            text = "No live feed yet",
                                            modifier = Modifier.align(Alignment.Center),
                                            style = MaterialTheme.typography.bodySmall
                                        )
                                    }
                                }
                                if (!liveMessage.isNullOrBlank()) {
                                    Text(text = liveMessage ?: "", style = MaterialTheme.typography.bodySmall)
                                }
                            }
                        }
                    }
                }
            }

            if (showServerManager) {
                ServerManagerDialog(
                    servers = servers,
                    selectedServerId = selectedServerId,
                    onUse = {
                        applyServer(it)
                        showServerManager = false
                    },
                    onEdit = { server ->
                        editingServer = server
                        showServerManager = false
                    },
                    onDelete = { server ->
                        val newList = servers.filter { it.id != server.id }
                        servers = newList
                        serverStore.save(newList)
                        if (selectedServerId == server.id) {
                            selectedServerId = null
                            serverStore.saveSelectedServerId(null)
                        }
                    },
                    onAdd = {
                        editingServer = ServerProfile(
                            name = "",
                            host = "",
                            port = "17500",
                            workingDir = ""
                        )
                        showServerManager = false
                    },
                    onDismiss = { showServerManager = false }
                )
            }

            if (editingServer != null) {
                ServerEditorDialog(
                    initial = editingServer!!,
                    isNew = servers.none { it.id == editingServer!!.id },
                    onSave = { updated ->
                        val exists = servers.any { it.id == updated.id }
                        val newList = if (exists) {
                            servers.map { if (it.id == updated.id) updated else it }
                        } else {
                            servers + updated
                        }
                        servers = newList
                        serverStore.save(newList)
                        val shouldSelect = !exists || selectedServerId == updated.id || selectedServerId == null
                        if (shouldSelect) {
                            applyServer(updated)
                        }
                        editingServer = null
                    },
                    onDismiss = { editingServer = null }
                )
            }

            if (showWorkingDirDialog) {
                WorkingDirDialog(
                    currentDir = workingDir,
                    host = ip,
                    port = port,
                    onSave = {
                        workingDir = it
                        showWorkingDirDialog = false
                    },
                    onDismiss = { showWorkingDirDialog = false }
                )
            }
        }
    }

}

private data class TerminalKey(val label: String, val sequence: String)

private val TERMINAL_KEYS = listOf(
    TerminalKey("↑", "\u001B[A"),
    TerminalKey("↓", "\u001B[B"),
    TerminalKey("←", "\u001B[D"),
    TerminalKey("→", "\u001B[C"),
    TerminalKey("Enter", "\r"),
    TerminalKey("Ctrl+C", "\u0003")
)

@Composable
private fun ServerPanel(
    expanded: Boolean,
    onToggle: () -> Unit,
    content: @Composable () -> Unit
) {
    Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.End,
            verticalAlignment = Alignment.CenterVertically
        ) {
            IconButton(onClick = onToggle) {
                Icon(
                    imageVector = if (expanded) Icons.Default.KeyboardArrowUp else Icons.Default.KeyboardArrowDown,
                    contentDescription = if (expanded) "Collapse server panel" else "Expand server panel"
                )
            }
        }
        AnimatedVisibility(
            visible = expanded,
            enter = slideInVertically(
                animationSpec = tween(durationMillis = 220),
                initialOffsetY = { -it }
            ) + expandVertically(expandFrom = Alignment.Top),
            exit = slideOutVertically(
                animationSpec = tween(durationMillis = 180),
                targetOffsetY = { -it }
            ) + shrinkVertically(shrinkTowards = Alignment.Top)
        ) {
            content()
        }
    }
}

@Composable
private fun RunnerSection(
    isConnected: Boolean,
    serverHost: String,
    workingDir: String,
    detectedType: String?,
    packageName: String?,
    projects: List<RunnerProject>,
    selectedProjectPath: String?,
    onSelectProject: (RunnerProject?) -> Unit,
    scanDepth: Int,
    onScanDepthChange: (Int) -> Unit,
    typeChoice: String,
    onTypeChoice: (String) -> Unit,
    devices: List<RunnerDevice>,
    selectedDeviceId: String?,
    onSelectDevice: (String?) -> Unit,
    onRefreshDevices: () -> Unit,
    status: RunnerStatus?,
    logs: RunnerLogs?,
    runnerMessage: String?,
    onDetect: () -> Unit,
    onStart: () -> Unit,
    onStop: () -> Unit,
    onReload: () -> Unit,
    onRestart: () -> Unit,
    onDevMenu: () -> Unit,
    onOpenRunner: () -> Unit,
    onSetDebugHost: () -> Unit,
    onReloadJs: () -> Unit,
    metroPort: String,
    onMetroPortChange: (String) -> Unit,
    mode: String,
    onModeChange: (String) -> Unit
) {
    val resolvedType = if (typeChoice == "auto") detectedType else typeChoice
    val hasDevice = selectedDeviceId != null || devices.isNotEmpty()
    val canStart = isConnected && !resolvedType.isNullOrBlank() && hasDevice
    val canOpenRunner = isConnected && hasDevice
    val isReactNative = resolvedType == "react-native"
    val isFlutter = resolvedType == "flutter"

    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Text(text = "Flow:", style = MaterialTheme.typography.bodySmall)
        Text(text = "1) Detect a project and device.", style = MaterialTheme.typography.bodySmall)
        Text(text = "2) Start builds/installs via ADB and opens the app.", style = MaterialTheme.typography.bodySmall)
        Text(text = "3) Keep Metro/Flutter running for hot reload.", style = MaterialTheme.typography.bodySmall)
        Text(
            text = "RN LAN/VPN: set Debug Host. Flutter hot reload needs USB/Wireless ADB.",
            style = MaterialTheme.typography.bodySmall
        )
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            ScanDepthDropdown(
                depth = scanDepth,
                onDepthChange = onScanDepthChange,
                modifier = Modifier.weight(1f)
            )
            OutlinedButton(onClick = onDetect, enabled = isConnected) {
                Text("Scan")
            }
        }
        ProjectDropdown(
            projects = projects,
            selectedPath = selectedProjectPath,
            onSelect = onSelectProject,
            modifier = Modifier.fillMaxWidth()
        )
        ProjectTypeDropdown(
            choice = typeChoice,
            detectedType = detectedType,
            onChoice = onTypeChoice
        )
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            DeviceDropdown(
                devices = devices,
                selectedDeviceId = selectedDeviceId,
                onSelectDevice = onSelectDevice,
                modifier = Modifier.weight(1f)
            )
            IconButton(onClick = onRefreshDevices, enabled = isConnected) {
                Icon(Icons.Default.Refresh, contentDescription = "Refresh devices")
            }
            OutlinedButton(onClick = onDetect, enabled = isConnected) {
                Text("Detect")
            }
        }
        if (workingDir.isNotBlank()) {
            Text(text = "Working dir: $workingDir", style = MaterialTheme.typography.bodySmall)
        }
        if (isReactNative) {
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
                OutlinedTextField(
                    value = metroPort,
                    onValueChange = onMetroPortChange,
                    label = { Text("Metro port") },
                    singleLine = true,
                    modifier = Modifier.width(140.dp)
                )
                ModeDropdown(mode = mode, onModeChange = onModeChange, modifier = Modifier.weight(1f))
            }
            Text(
                text = if (mode == "adb") {
                    "ADB mode uses adb reverse (USB or wireless ADB)."
                } else {
                    "LAN/VPN mode requires Debug server host to be set."
                },
                style = MaterialTheme.typography.bodySmall
            )
            if (packageName != null) {
                Text(text = "Package: $packageName", style = MaterialTheme.typography.bodySmall)
            } else if (isReactNative) {
                Text(
                text = "Package not detected (set Debug host manually in Dev Menu).",
                style = MaterialTheme.typography.bodySmall
            )
        }
        if (mode == "lan" && serverHost.isNotBlank()) {
            Text(
                text = "Set Debug server host to $serverHost:$metroPort in Dev Menu.",
                style = MaterialTheme.typography.bodySmall
            )
        }
    }
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            Button(onClick = onStart, enabled = canStart) {
                Text("Start")
            }
            OutlinedButton(onClick = onStop, enabled = status != null) {
                Text("Stop")
            }
            OutlinedButton(onClick = onOpenRunner, enabled = canOpenRunner) {
                Text("Open App")
            }
            if (isFlutter) {
                OutlinedButton(onClick = onReload, enabled = status?.flutterRunning == true) {
                    Text("Hot Reload")
                }
                OutlinedButton(onClick = onRestart, enabled = status?.flutterRunning == true) {
                    Text("Hot Restart")
                }
            } else if (isReactNative) {
                OutlinedButton(onClick = onDevMenu, enabled = status?.appRunning == true) {
                    Text("Dev Menu")
                }
                OutlinedButton(onClick = onReloadJs, enabled = status?.appRunning == true) {
                    Text("Reload JS")
                }
                OutlinedButton(
                    onClick = onSetDebugHost,
                    enabled = status?.appRunning == true && !packageName.isNullOrBlank()
                ) {
                    Text("Set Debug Host")
                }
            }
        }
        val statusLine = status?.let {
            val running = if (it.flutterRunning || it.metroRunning || it.appRunning) "running" else "stopped"
            "Status: $running"
        } ?: "Status: idle"
        Text(text = statusLine, style = MaterialTheme.typography.bodySmall)
        if (!runnerMessage.isNullOrBlank()) {
            Text(text = runnerMessage, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.error)
        }

        val logLines = remember(logs) {
            val combined = mutableListOf<String>()
            logs?.metro?.forEach { combined.add("[metro] $it") }
            logs?.app?.forEach { combined.add("[app] $it") }
            logs?.flutter?.forEach { combined.add("[flutter] $it") }
            if (combined.size > 120) combined.takeLast(120) else combined
        }
        if (logLines.isNotEmpty()) {
            Surface(
                tonalElevation = 2.dp,
                modifier = Modifier.fillMaxWidth()
            ) {
                Box(
                    modifier = Modifier
                        .heightIn(min = 120.dp, max = 220.dp)
                        .padding(8.dp)
                ) {
                    LazyColumn {
                        items(logLines) { line ->
                            Text(text = line, style = MaterialTheme.typography.bodySmall)
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun ProjectTypeDropdown(
    choice: String,
    detectedType: String?,
    onChoice: (String) -> Unit
) {
    var expanded by remember { mutableStateOf(false) }
    val label = when (choice) {
        "auto" -> "Auto (${detectedType ?: "unknown"})"
        "react-native" -> "React Native"
        "flutter" -> "Flutter"
        else -> "Auto"
    }
    Box {
        OutlinedTextField(
            value = label,
            onValueChange = {},
            readOnly = true,
            label = { Text("Project") },
            trailingIcon = {
                Icon(
                    imageVector = Icons.Default.ArrowDropDown,
                    contentDescription = "Select project type"
                )
            },
            modifier = Modifier
                .fillMaxWidth()
                .clickable { expanded = true }
        )
        DropdownMenu(
            expanded = expanded,
            onDismissRequest = { expanded = false }
        ) {
            DropdownMenuItem(
                text = { Text("Auto (${detectedType ?: "unknown"})") },
                onClick = {
                    expanded = false
                    onChoice("auto")
                }
            )
            DropdownMenuItem(
                text = { Text("React Native") },
                onClick = {
                    expanded = false
                    onChoice("react-native")
                }
            )
            DropdownMenuItem(
                text = { Text("Flutter") },
                onClick = {
                    expanded = false
                    onChoice("flutter")
                }
            )
        }
    }
}

@Composable
private fun ScanDepthDropdown(
    depth: Int,
    onDepthChange: (Int) -> Unit,
    modifier: Modifier = Modifier
) {
    var expanded by remember { mutableStateOf(false) }
    val label = "Depth: $depth"
    Box(modifier = modifier) {
        OutlinedTextField(
            value = label,
            onValueChange = {},
            readOnly = true,
            label = { Text("Scan depth") },
            trailingIcon = {
                Icon(
                    imageVector = Icons.Default.ArrowDropDown,
                    contentDescription = "Select scan depth"
                )
            },
            modifier = Modifier
                .fillMaxWidth()
                .clickable { expanded = true }
        )
        DropdownMenu(
            expanded = expanded,
            onDismissRequest = { expanded = false }
        ) {
            for (value in 0..5) {
                DropdownMenuItem(
                    text = { Text(value.toString()) },
                    onClick = {
                        expanded = false
                        onDepthChange(value)
                    }
                )
            }
        }
    }
}

@Composable
private fun ProjectDropdown(
    projects: List<RunnerProject>,
    selectedPath: String?,
    onSelect: (RunnerProject?) -> Unit,
    modifier: Modifier = Modifier
) {
    var expanded by remember { mutableStateOf(false) }
    val selected = projects.firstOrNull { it.path == selectedPath }
    val label = when {
        selected != null -> "${selected.projectType} • ${File(selected.path).name}"
        projects.isEmpty() -> "No projects found"
        else -> "Select project"
    }
    Box(modifier = modifier) {
        OutlinedTextField(
            value = label,
            onValueChange = {},
            readOnly = true,
            label = { Text("Project") },
            trailingIcon = {
                Icon(
                    imageVector = Icons.Default.ArrowDropDown,
                    contentDescription = "Select project"
                )
            },
            modifier = Modifier
                .fillMaxWidth()
                .clickable { expanded = true }
        )
        DropdownMenu(
            expanded = expanded,
            onDismissRequest = { expanded = false }
        ) {
            if (projects.isEmpty()) {
                DropdownMenuItem(
                    text = { Text("No projects") },
                    onClick = { expanded = false }
                )
            } else {
                projects.forEach { project ->
                    DropdownMenuItem(
                        text = { Text("${project.projectType} • ${project.path}") },
                        onClick = {
                            expanded = false
                            onSelect(project)
                        }
                    )
                }
            }
        }
    }
}

@Composable
private fun ModeDropdown(
    mode: String,
    onModeChange: (String) -> Unit,
    modifier: Modifier = Modifier
) {
    var expanded by remember { mutableStateOf(false) }
    val label = when (mode) {
        "lan" -> "LAN/VPN (manual host)"
        else -> "ADB (USB / wireless)"
    }
    Box(modifier = modifier) {
        OutlinedTextField(
            value = label,
            onValueChange = {},
            readOnly = true,
            label = { Text("Mode") },
            trailingIcon = {
                Icon(
                    imageVector = Icons.Default.ArrowDropDown,
                    contentDescription = "Select runner mode"
                )
            },
            modifier = Modifier
                .fillMaxWidth()
                .clickable { expanded = true }
        )
        DropdownMenu(
            expanded = expanded,
            onDismissRequest = { expanded = false }
        ) {
            DropdownMenuItem(
                text = { Text("ADB (USB / wireless)") },
                onClick = {
                    expanded = false
                    onModeChange("adb")
                }
            )
            DropdownMenuItem(
                text = { Text("LAN/VPN (manual host)") },
                onClick = {
                    expanded = false
                    onModeChange("lan")
                }
            )
        }
    }
}

@Composable
private fun DeviceDropdown(
    devices: List<RunnerDevice>,
    selectedDeviceId: String?,
    onSelectDevice: (String?) -> Unit,
    modifier: Modifier = Modifier
) {
    var expanded by remember { mutableStateOf(false) }
    val selected = devices.firstOrNull { it.id == selectedDeviceId }
    val label = when {
        selected != null -> {
            val model = selected.model.ifBlank { selected.device.ifBlank { "Device" } }
            "$model (${selected.id})"
        }
        devices.isEmpty() -> "No devices"
        else -> "Select device"
    }
    Box(modifier = modifier) {
        OutlinedTextField(
            value = label,
            onValueChange = {},
            readOnly = true,
            label = { Text("Device") },
            trailingIcon = {
                Icon(
                    imageVector = Icons.Default.ArrowDropDown,
                    contentDescription = "Select device"
                )
            },
            modifier = Modifier
                .fillMaxWidth()
                .clickable { expanded = true }
        )
        DropdownMenu(
            expanded = expanded,
            onDismissRequest = { expanded = false }
        ) {
            if (devices.isEmpty()) {
                DropdownMenuItem(
                    text = { Text("No devices") },
                    onClick = { expanded = false }
                )
            } else {
                devices.forEach { device ->
                    val model = device.model.ifBlank { device.device.ifBlank { "Device" } }
                    DropdownMenuItem(
                        text = { Text("$model (${device.id})") },
                        onClick = {
                            expanded = false
                            onSelectDevice(device.id)
                        }
                    )
                }
            }
        }
    }
}

@Composable
private fun ConnectionSection(
    ip: String,
    onIpChange: (String) -> Unit,
    port: String,
    onPortChange: (String) -> Unit,
    isConnected: Boolean,
    onToggleConnection: () -> Unit,
    servers: List<ServerProfile>,
    selectedServerId: String?,
    onSelectServer: (ServerProfile?) -> Unit,
    onManageServers: () -> Unit,
    onAddServer: () -> Unit,
    onOpenWorkingDir: () -> Unit,
    workingDir: String,
    stacked: Boolean
) {
    val serverLabel = servers.firstOrNull { it.id == selectedServerId }?.name ?: "Custom"
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Box(modifier = Modifier.weight(1f)) {
                ServerDropdown(
                    serverLabel = serverLabel,
                    servers = servers,
                    selectedServerId = selectedServerId,
                    onSelectServer = onSelectServer,
                    onManageServers = onManageServers
                )
            }
            IconButton(onClick = onManageServers) {
                Icon(Icons.Default.Settings, contentDescription = "Manage servers")
            }
            IconButton(onClick = onAddServer) {
                Icon(Icons.Default.Add, contentDescription = "Add server")
            }
        }
        if (workingDir.isNotBlank()) {
            Text(text = "Working dir: $workingDir", style = MaterialTheme.typography.bodySmall)
        }

        if (stacked) {
            OutlinedTextField(
                value = ip,
                onValueChange = onIpChange,
                label = { Text("Backend IP") },
                singleLine = true,
                modifier = Modifier.fillMaxWidth()
            )
            OutlinedTextField(
                value = port,
                onValueChange = onPortChange,
                label = { Text("Port") },
                singleLine = true,
                modifier = Modifier.fillMaxWidth()
            )
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Button(
                    onClick = onToggleConnection,
                    enabled = ip.isNotBlank() || isConnected
                ) {
                    Text(if (isConnected) "Disconnect" else "Connect")
                }
                IconButton(onClick = onOpenWorkingDir) {
                    Icon(Icons.Default.Settings, contentDescription = "Working directory")
                }
            }
        } else {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                OutlinedTextField(
                    value = ip,
                    onValueChange = onIpChange,
                    label = { Text("Backend IP") },
                    singleLine = true,
                    modifier = Modifier.weight(1f)
                )
                OutlinedTextField(
                    value = port,
                    onValueChange = onPortChange,
                    label = { Text("Port") },
                    singleLine = true,
                    modifier = Modifier.width(110.dp)
                )
                Button(
                    onClick = onToggleConnection,
                    enabled = ip.isNotBlank() || isConnected
                ) {
                    Text(if (isConnected) "Disconnect" else "Connect")
                }
                IconButton(onClick = onOpenWorkingDir) {
                    Icon(Icons.Default.Settings, contentDescription = "Working directory")
                }
            }
        }
    }
}

@Composable
private fun ServerDropdown(
    serverLabel: String,
    servers: List<ServerProfile>,
    selectedServerId: String?,
    onSelectServer: (ServerProfile?) -> Unit,
    onManageServers: () -> Unit
) {
    var expanded by remember { mutableStateOf(false) }
    Box {
        OutlinedTextField(
            value = serverLabel,
            onValueChange = {},
            readOnly = true,
            label = { Text("Server") },
            trailingIcon = {
                Icon(
                    imageVector = Icons.Default.ArrowDropDown,
                    contentDescription = "Select server"
                )
            },
            modifier = Modifier
                .fillMaxWidth()
                .clickable { expanded = true }
        )
        DropdownMenu(
            expanded = expanded,
            onDismissRequest = { expanded = false }
        ) {
            DropdownMenuItem(
                text = { Text("Custom") },
                onClick = {
                    expanded = false
                    onSelectServer(null)
                }
            )
            servers.forEach { server ->
                val selected = server.id == selectedServerId
                DropdownMenuItem(
                    text = { Text(if (selected) "${server.name} ✓" else server.name) },
                    onClick = {
                        expanded = false
                        onSelectServer(server)
                    }
                )
            }
            DropdownMenuItem(
                text = { Text("Manage servers…") },
                onClick = {
                    expanded = false
                    onManageServers()
                }
            )
        }
    }
}

@Composable
private fun FpsDropdown(
    fps: Int,
    onFpsChange: (Int) -> Unit,
    modifier: Modifier = Modifier
) {
    var expanded by remember { mutableStateOf(false) }
    val options = listOf(1, 2, 5, 10, 15)
    val label = "${fps} FPS"
    Box(modifier = modifier) {
        OutlinedTextField(
            value = label,
            onValueChange = {},
            readOnly = true,
            label = { Text("Live FPS") },
            trailingIcon = {
                Icon(
                    imageVector = Icons.Default.ArrowDropDown,
                    contentDescription = "Select FPS"
                )
            },
            modifier = Modifier
                .fillMaxWidth()
                .clickable { expanded = true }
        )
        DropdownMenu(
            expanded = expanded,
            onDismissRequest = { expanded = false }
        ) {
            options.forEach { value ->
                DropdownMenuItem(
                    text = { Text("$value FPS") },
                    onClick = {
                        expanded = false
                        onFpsChange(value)
                    }
                )
            }
        }
    }
}

@Composable
private fun FormatDropdown(
    format: String,
    onFormatChange: (String) -> Unit,
    modifier: Modifier = Modifier
) {
    var expanded by remember { mutableStateOf(false) }
    val label = if (format == "jpeg") "JPEG" else "PNG"
    Box(modifier = modifier) {
        OutlinedTextField(
            value = label,
            onValueChange = {},
            readOnly = true,
            label = { Text("Image Format") },
            trailingIcon = {
                Icon(
                    imageVector = Icons.Default.ArrowDropDown,
                    contentDescription = "Select image format"
                )
            },
            modifier = Modifier
                .fillMaxWidth()
                .clickable { expanded = true }
        )
        DropdownMenu(
            expanded = expanded,
            onDismissRequest = { expanded = false }
        ) {
            DropdownMenuItem(
                text = { Text("JPEG (smaller, lossy)") },
                onClick = {
                    expanded = false
                    onFormatChange("jpeg")
                }
            )
            DropdownMenuItem(
                text = { Text("PNG (lossless)") },
                onClick = {
                    expanded = false
                    onFormatChange("png")
                }
            )
        }
    }
}

@Composable
private fun TerminalSection(
    terminalController: TerminalController,
    modifier: Modifier = Modifier,
    fillHeight: Boolean = false,
    autoFit: Boolean,
    onAutoFitChanged: (Boolean) -> Unit
) {
    Column(modifier = modifier.fillMaxWidth()) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(text = "Terminal", style = MaterialTheme.typography.titleSmall)
            Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                TextButton(onClick = {
                    terminalController.decreaseFontSize()
                    onAutoFitChanged(terminalController.isAutoFitEnabled())
                }) {
                    Text("A-")
                }
                TextButton(onClick = {
                    terminalController.increaseFontSize()
                    onAutoFitChanged(terminalController.isAutoFitEnabled())
                }) {
                    Text("A+")
                }
                TextButton(onClick = {
                    val next = !autoFit
                    terminalController.setAutoFit(next)
                    onAutoFitChanged(next)
                }) {
                    Text(if (autoFit) "Fit✓" else "Fit")
                }
            }
        }
        Spacer(modifier = Modifier.height(8.dp))
        val boxModifier = if (fillHeight) {
            Modifier
                .fillMaxWidth()
                .heightIn(min = 240.dp)
                .weight(1f)
        } else {
            Modifier
                .fillMaxWidth()
                .heightIn(min = 240.dp)
        }
        Box(modifier = boxModifier.clipToBounds()) {
            AndroidView(
                factory = { terminalController.createView() },
                modifier = Modifier.fillMaxSize()
            )
        }
        Spacer(modifier = Modifier.height(8.dp))
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .horizontalScroll(rememberScrollState()),
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            TERMINAL_KEYS.forEach { key ->
                OutlinedButton(
                    onClick = { terminalController.sendKeySequence(key.sequence) },
                    contentPadding = PaddingValues(horizontal = 12.dp, vertical = 6.dp)
                ) {
                    Text(key.label)
                }
            }
        }
    }
}

@Composable
private fun CommandSection(
    command: String,
    onCommandChange: (String) -> Unit,
    isConnected: Boolean,
    onSend: () -> Unit,
    isRecordingManual: Boolean,
    isRecordingAuto: Boolean,
    onToggleManualRecording: () -> Unit,
    onToggleAutoRecording: () -> Unit,
    sttStatus: String
) {
    Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Button(
                onClick = onToggleManualRecording,
                enabled = !isRecordingAuto
            ) {
                Icon(
                    imageVector = if (isRecordingManual) Icons.Default.Stop else Icons.Default.FiberManualRecord,
                    contentDescription = null
                )
                Spacer(modifier = Modifier.width(6.dp))
                Text(if (isRecordingManual) "Stop" else "Record")
            }
            IconButton(
                onClick = onToggleAutoRecording,
                enabled = !isRecordingManual
            ) {
                Icon(
                    imageVector = if (isRecordingAuto) Icons.Default.Stop else Icons.Default.Mic,
                    contentDescription = "Mic"
                )
            }
            OutlinedTextField(
                value = command,
                onValueChange = onCommandChange,
                label = { Text("Command") },
                singleLine = true,
                modifier = Modifier.weight(1f)
            )
            Button(
                onClick = onSend,
                enabled = isConnected && command.isNotBlank()
            ) {
                Text("Send")
            }
        }
        if (sttStatus != "Idle") {
            Text(text = sttStatus, style = MaterialTheme.typography.bodySmall)
        }
    }
}

@Composable
private fun ServerManagerDialog(
    servers: List<ServerProfile>,
    selectedServerId: String?,
    onUse: (ServerProfile) -> Unit,
    onEdit: (ServerProfile) -> Unit,
    onDelete: (ServerProfile) -> Unit,
    onAdd: () -> Unit,
    onDismiss: () -> Unit
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Servers") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                if (servers.isEmpty()) {
                    Text("No servers saved yet")
                }
                servers.forEach { server ->
                    Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                        val selected = server.id == selectedServerId
                        Text(
                            text = "${server.name} ${if (selected) "(active)" else ""}",
                            style = MaterialTheme.typography.titleSmall
                        )
                        Text(text = "${server.host}:${server.port}")
                        if (server.workingDir.isNotBlank()) {
                            Text(text = "Dir: ${server.workingDir}")
                        }
                        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            TextButton(onClick = { onUse(server) }) { Text("Use") }
                            TextButton(onClick = { onEdit(server) }) { Text("Edit") }
                            TextButton(onClick = { onDelete(server) }) { Text("Delete") }
                        }
                    }
                }
            }
        },
        confirmButton = {
            TextButton(onClick = onAdd) { Text("Add") }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text("Close") }
        }
    )
}

@Composable
private fun ServerEditorDialog(
    initial: ServerProfile,
    isNew: Boolean,
    onSave: (ServerProfile) -> Unit,
    onDismiss: () -> Unit
) {
    var name by remember { mutableStateOf(initial.name) }
    var host by remember { mutableStateOf(initial.host) }
    var port by remember { mutableStateOf(initial.port) }
    var workingDir by remember { mutableStateOf(initial.workingDir) }
    val canSave = name.isNotBlank() && host.isNotBlank() && port.isNotBlank()
    val viewModel: CodexViewModel = viewModel()
    val hostReady = host.isNotBlank() && port.isNotBlank()
    var showDirManager by remember { mutableStateOf(false) }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(if (isNew) "Add Server" else "Edit Server") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                OutlinedTextField(
                    value = name,
                    onValueChange = { name = it },
                    label = { Text("Name") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )
                OutlinedTextField(
                    value = host,
                    onValueChange = { host = it },
                    label = { Text("Host/IP") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )
                OutlinedTextField(
                    value = port,
                    onValueChange = { port = it },
                    label = { Text("Port") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    OutlinedTextField(
                        value = workingDir,
                        onValueChange = { workingDir = it },
                        label = { Text("Working directory") },
                        singleLine = true,
                        modifier = Modifier.weight(1f)
                    )
                    OutlinedButton(
                        onClick = { showDirManager = true },
                        enabled = hostReady
                    ) {
                        Text("Browse")
                    }
                }
            }
        },
        confirmButton = {
            TextButton(
                onClick = {
                    onSave(
                        initial.copy(
                            name = name.trim(),
                            host = host.trim(),
                            port = port.trim(),
                            workingDir = workingDir.trim()
                        )
                    )
                },
                enabled = canSave
            ) {
                Text("Save")
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text("Cancel") }
        }
    )

    if (showDirManager) {
        DirectoryManagerDialog(
            title = "Directory Manager",
            host = host,
            port = port,
            initialPath = workingDir,
            viewModel = viewModel,
            onUse = {
                workingDir = it
                showDirManager = false
            },
            onDismiss = { showDirManager = false }
        )
    }
}

@Composable
private fun WorkingDirDialog(
    currentDir: String,
    host: String,
    port: String,
    onSave: (String) -> Unit,
    onDismiss: () -> Unit
) {
    var value by remember { mutableStateOf(currentDir) }
    val viewModel: CodexViewModel = viewModel()
    val hostReady = host.isNotBlank() && port.isNotBlank()
    var showDirManager by remember { mutableStateOf(false) }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Working Directory") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    OutlinedTextField(
                        value = value,
                        onValueChange = { value = it },
                        label = { Text("Directory") },
                        singleLine = true,
                        modifier = Modifier.weight(1f)
                    )
                    OutlinedButton(
                        onClick = { showDirManager = true },
                        enabled = hostReady
                    ) {
                        Text("Browse")
                    }
                }
                Text(
                    text = "Leave empty to use the backend default.",
                    style = MaterialTheme.typography.bodySmall
                )
            }
        },
        confirmButton = {
            TextButton(onClick = { onSave(value.trim()) }) { Text("Save") }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text("Cancel") }
        }
    )

    if (showDirManager) {
        DirectoryManagerDialog(
            title = "Directory Manager",
            host = host,
            port = port,
            initialPath = value,
            viewModel = viewModel,
            onUse = {
                value = it
                showDirManager = false
            },
            onDismiss = { showDirManager = false }
        )
    }
}

@Composable
private fun DirectoryManagerDialog(
    title: String,
    host: String,
    port: String,
    initialPath: String,
    viewModel: CodexViewModel,
    onUse: (String) -> Unit,
    onDismiss: () -> Unit
) {
    var browsePath by remember { mutableStateOf(initialPath) }
    var listing by remember { mutableStateOf<DirectoryListing?>(null) }
    var loading by remember { mutableStateOf(false) }
    var error by remember { mutableStateOf<String?>(null) }
    var status by remember { mutableStateOf<String?>(null) }
    var refreshKey by remember { mutableStateOf(0) }
    var showCreateDialog by remember { mutableStateOf(false) }
    var showRenameDialog by remember { mutableStateOf(false) }
    var showDeleteDialog by remember { mutableStateOf(false) }
    var selectedPath by remember { mutableStateOf<String?>(null) }
    val scope = rememberCoroutineScope()
    val hostReady = host.isNotBlank() && port.isNotBlank()

    LaunchedEffect(browsePath, host, port, refreshKey) {
        if (!hostReady) {
            listing = null
            error = "Set host and port to browse directories."
            loading = false
            return@LaunchedEffect
        }
        delay(200)
        loading = true
        val result = viewModel.fetchDirectories(host, port, browsePath.trim())
        loading = false
        if (result.isSuccess) {
            val value = result.getOrNull()
            listing = value
            error = null
            if (browsePath.isBlank() && !value?.base.isNullOrBlank()) {
                browsePath = value?.base.orEmpty()
            }
        } else {
            listing = null
            error = result.exceptionOrNull()?.message
        }
    }

    val currentPath = when {
        browsePath.isNotBlank() -> browsePath
        listing?.base?.isNotBlank() == true -> listing?.base.orEmpty()
        else -> ""
    }
    val actionTarget = selectedPath?.ifBlank { null } ?: currentPath

    Dialog(onDismissRequest = onDismiss) {
        Surface(
            modifier = Modifier.fillMaxWidth(),
            shape = MaterialTheme.shapes.large,
            tonalElevation = 2.dp
        ) {
            Column(
                modifier = Modifier
                    .padding(16.dp)
                    .fillMaxWidth()
                    .heightIn(min = 360.dp, max = 680.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                Text(text = title, style = MaterialTheme.typography.titleMedium)
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    OutlinedTextField(
                        value = browsePath,
                        onValueChange = { browsePath = it },
                        label = { Text("Path") },
                        singleLine = true,
                        modifier = Modifier.weight(1f)
                    )
                    OutlinedButton(onClick = { refreshKey += 1 }, enabled = hostReady) {
                        Text("Go")
                    }
                }
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    OutlinedButton(
                        onClick = {
                            val parent = File(currentPath).parentFile?.absolutePath.orEmpty()
                            if (parent.isNotBlank()) {
                                browsePath = parent
                            }
                        },
                        enabled = currentPath.isNotBlank()
                    ) {
                        Text("Up")
                    }
                    OutlinedButton(
                        onClick = { browsePath = "" },
                        enabled = hostReady
                    ) {
                        Text("Home")
                    }
                    Button(
                        onClick = { onUse(currentPath) },
                        enabled = currentPath.isNotBlank()
                    ) {
                        Text("Use this folder")
                    }
                }
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    OutlinedButton(
                        onClick = { showCreateDialog = true },
                        enabled = hostReady && currentPath.isNotBlank()
                    ) {
                        Icon(Icons.Default.Add, contentDescription = null)
                        Spacer(modifier = Modifier.width(6.dp))
                        Text("New")
                    }
                    OutlinedButton(
                        onClick = { showRenameDialog = true },
                        enabled = hostReady && actionTarget.isNotBlank()
                    ) {
                        Icon(Icons.Default.Edit, contentDescription = null)
                        Spacer(modifier = Modifier.width(6.dp))
                        Text("Rename")
                    }
                }
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    OutlinedButton(
                        onClick = { showDeleteDialog = true },
                        enabled = hostReady && actionTarget.isNotBlank()
                    ) {
                        Icon(Icons.Default.Delete, contentDescription = null)
                        Spacer(modifier = Modifier.width(6.dp))
                        Text("Delete")
                    }
                }
                if (loading) {
                    Text("Loading directories…", style = MaterialTheme.typography.bodySmall)
                } else if (error != null) {
                    Text("Directory list error: $error", style = MaterialTheme.typography.bodySmall)
                }
                if (status != null) {
                    Text(status!!, style = MaterialTheme.typography.bodySmall)
                }
                Text(text = "Directories", style = MaterialTheme.typography.titleSmall)
                val dirs = listing?.dirs.orEmpty()
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .heightIn(min = 160.dp, max = 360.dp)
                ) {
                    LazyColumn {
                        items(dirs) { dir ->
                            val selected = dir == selectedPath
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .background(
                                        if (selected) MaterialTheme.colorScheme.surfaceVariant else MaterialTheme.colorScheme.surface
                                    )
                                    .padding(vertical = 8.dp, horizontal = 6.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Column(
                                    modifier = Modifier
                                        .weight(1f)
                                        .clickable {
                                            selectedPath = dir
                                        }
                                ) {
                                    val name = dir.substringAfterLast(File.separatorChar)
                                    Text(text = if (name.isNotBlank()) name else dir)
                                    Text(text = dir, style = MaterialTheme.typography.bodySmall)
                                }
                                TextButton(onClick = { browsePath = dir }) { Text("Open") }
                                TextButton(onClick = { onUse(dir) }) { Text("Use") }
                            }
                            Divider()
                        }
                    }
                }
                if (selectedPath != null) {
                    Text(
                        text = "Selected: ${selectedPath}",
                        style = MaterialTheme.typography.bodySmall
                    )
                }
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.End
                ) {
                    TextButton(onClick = onDismiss) { Text("Close") }
                }
            }
        }
    }

    if (showCreateDialog) {
        val suggested = if (currentPath.isNotBlank()) {
            "${currentPath.trimEnd('/')}/new-folder"
        } else {
            "new-folder"
        }
        DirectoryActionDialog(
            title = "Create directory",
            confirmLabel = "Create",
            initialValue = suggested,
            onConfirm = { path ->
                scope.launch {
                    val result = viewModel.createDirectory(host, port, path)
                    status = result.fold(
                        onSuccess = {
                            browsePath = path
                            refreshKey += 1
                            "Created: $path"
                        },
                        onFailure = { "Create failed: ${it.message}" }
                    )
                }
            },
            onDismiss = { showCreateDialog = false }
        )
    }

    if (showRenameDialog) {
        DirectoryActionDialog(
            title = "Rename directory",
            confirmLabel = "Rename",
            initialValue = actionTarget,
            onConfirm = { newPath ->
                val oldPath = actionTarget
                scope.launch {
                    val result = viewModel.renameDirectory(host, port, oldPath, newPath)
                    status = result.fold(
                    onSuccess = {
                        browsePath = newPath
                        selectedPath = newPath
                        refreshKey += 1
                        "Renamed to: $newPath"
                    },
                    onFailure = { "Rename failed: ${it.message}" }
                )
                }
            },
            onDismiss = { showRenameDialog = false }
        )
    }

    if (showDeleteDialog) {
        ConfirmDeleteDialog(
            target = actionTarget,
            onConfirm = {
                val target = actionTarget
                scope.launch {
                    val result = viewModel.deleteDirectory(host, port, target, recursive = true)
                    status = result.fold(
                        onSuccess = {
                            browsePath = File(target).parentFile?.absolutePath.orEmpty()
                            selectedPath = null
                            refreshKey += 1
                            "Deleted: $target"
                        },
                        onFailure = { "Delete failed: ${it.message}" }
                    )
                }
            },
            onDismiss = { showDeleteDialog = false }
        )
    }
}

@Composable
private fun DirectoryActionDialog(
    title: String,
    confirmLabel: String,
    initialValue: String,
    onConfirm: (String) -> Unit,
    onDismiss: () -> Unit
) {
    var value by remember { mutableStateOf(initialValue) }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(title) },
        text = {
            OutlinedTextField(
                value = value,
                onValueChange = { value = it },
                label = { Text("Path") },
                singleLine = true,
                modifier = Modifier.fillMaxWidth()
            )
        },
        confirmButton = {
            TextButton(
                onClick = {
                    onConfirm(value.trim())
                    onDismiss()
                },
                enabled = value.isNotBlank()
            ) {
                Text(confirmLabel)
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text("Cancel") }
        }
    )
}

@Composable
private fun ConfirmDeleteDialog(
    target: String,
    onConfirm: () -> Unit,
    onDismiss: () -> Unit
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Delete directory") },
        text = { Text("Delete $target and its contents?") },
        confirmButton = {
            TextButton(onClick = {
                onConfirm()
                onDismiss()
            }) {
                Text("Delete")
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text("Cancel") }
        }
    )
}

private fun File.safeDelete() {
    try {
        delete()
    } catch (_: Exception) {
        // ignore
    }
}

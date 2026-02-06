package com.meinzeug.codexspeech.viewer

import android.Manifest
import android.content.pm.PackageManager
import android.content.res.Configuration
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.clickable
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.expandVertically
import androidx.compose.animation.shrinkVertically
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.animation.core.tween
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
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.background
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.FiberManualRecord
import androidx.compose.material.icons.filled.Mic
import androidx.compose.material.icons.filled.ArrowDropDown
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.KeyboardArrowDown
import androidx.compose.material.icons.filled.KeyboardArrowUp
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.Stop
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Divider
import androidx.compose.material3.Button
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
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
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.compose.ui.window.Dialog
import androidx.core.content.ContextCompat
import androidx.lifecycle.viewmodel.compose.viewModel
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import java.io.File
import java.util.Locale

private enum class RecordingMode {
    MANUAL,
    AUTO
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
private fun CodexSpeechApp(viewModel: CodexViewModel = viewModel()) {
    val connectionStatus by viewModel.connectionStatus.collectAsState()
    val sttStatus by viewModel.sttStatus.collectAsState()
    val runnerStatus by viewModel.runnerStatus.collectAsState()
    val runnerLogs by viewModel.runnerLogs.collectAsState()

    var ip by remember { mutableStateOf("") }
    var port by remember { mutableStateOf("8000") }
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
    var serverPanelExpanded by remember { mutableStateOf(true) }
    var runnerPanelExpanded by remember { mutableStateOf(true) }
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

    LaunchedEffect(isConnected) {
        serverPanelExpanded = !isConnected
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

    MaterialTheme {
        Surface(modifier = Modifier.fillMaxSize(), color = MaterialTheme.colorScheme.background) {
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
                        Text(text = "Status: $connectionStatus")
                        ServerPanel(
                            expanded = serverPanelExpanded,
                            onToggle = { serverPanelExpanded = !serverPanelExpanded }
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
                        RunnerPanel(
                            expanded = runnerPanelExpanded,
                            onToggle = { runnerPanelExpanded = !runnerPanelExpanded }
                        ) {
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
                                onSetDebugHost = ::setReactNativeHost,
                                onReloadJs = ::reloadReactNative,
                                metroPort = runnerMetroPort,
                                onMetroPortChange = { runnerMetroPort = it },
                                mode = runnerMode,
                                onModeChange = { runnerMode = it }
                            )
                        }
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
                    Text(text = "Status: $connectionStatus")
                    ServerPanel(
                        expanded = serverPanelExpanded,
                        onToggle = { serverPanelExpanded = !serverPanelExpanded }
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

                    RunnerPanel(
                        expanded = runnerPanelExpanded,
                        onToggle = { runnerPanelExpanded = !runnerPanelExpanded }
                    ) {
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
                            onSetDebugHost = ::setReactNativeHost,
                            onReloadJs = ::reloadReactNative,
                            metroPort = runnerMetroPort,
                            onMetroPortChange = { runnerMetroPort = it },
                            mode = runnerMode,
                            onModeChange = { runnerMode = it }
                        )
                    }

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
                            port = "8000",
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
private fun RunnerPanel(
    expanded: Boolean,
    onToggle: () -> Unit,
    content: @Composable () -> Unit
) {
    Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(text = "Runner", style = MaterialTheme.typography.titleSmall)
            IconButton(onClick = onToggle) {
                Icon(
                    imageVector = if (expanded) Icons.Default.KeyboardArrowUp else Icons.Default.KeyboardArrowDown,
                    contentDescription = if (expanded) "Collapse runner panel" else "Expand runner panel"
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
    onSetDebugHost: () -> Unit,
    onReloadJs: () -> Unit,
    metroPort: String,
    onMetroPortChange: (String) -> Unit,
    mode: String,
    onModeChange: (String) -> Unit
) {
    val resolvedType = if (typeChoice == "auto") detectedType else typeChoice
    val canStart = isConnected && !resolvedType.isNullOrBlank() && (selectedDeviceId != null || devices.isNotEmpty())
    val isReactNative = resolvedType == "react-native"
    val isFlutter = resolvedType == "flutter"

    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
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
        Box(modifier = boxModifier) {
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

package com.example.codexmobile

import android.Manifest
import android.content.pm.PackageManager
import android.content.res.Configuration
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
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
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.FiberManualRecord
import androidx.compose.material.icons.filled.Mic
import androidx.compose.material.icons.filled.ArrowDropDown
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.Stop
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
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
import androidx.core.content.ContextCompat
import androidx.lifecycle.viewmodel.compose.viewModel
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.launch
import java.io.File

private enum class RecordingMode {
    MANUAL,
    AUTO
}

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent {
            CodexMobileApp()
        }
    }
}

@Composable
private fun CodexMobileApp(viewModel: CodexViewModel = viewModel()) {
    val connectionStatus by viewModel.connectionStatus.collectAsState()
    val sttStatus by viewModel.sttStatus.collectAsState()

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

    fun stopRecordingAndTranscribe(mode: RecordingMode) {
        val file = audioRecorder.stop()
        recordingMode = null
        if (file == null) return
        scope.launch {
            val result = viewModel.transcribeAudio(file)
            file.safeDelete()
            if (result.isSuccess) {
                val text = result.getOrDefault("").trim()
                if (text.isNotBlank()) {
                    command = text
                    if (mode == RecordingMode.AUTO && isConnected) {
                        val payload = if (text.endsWith("\n")) text else "$text\n"
                        viewModel.sendCommand(payload)
                        command = ""
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
                            onOpenWorkingDir = { showWorkingDirDialog = true },
                            workingDir = workingDir,
                            stacked = true
                        )
                        CommandSection(
                            command = command,
                            onCommandChange = { command = it },
                            isConnected = isConnected,
                            onSend = {
                                val payload = if (command.endsWith("\n")) command else "$command\n"
                                viewModel.sendCommand(payload)
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
                    Spacer(modifier = Modifier.height(16.dp))

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
                        onOpenWorkingDir = { showWorkingDirDialog = true },
                        workingDir = workingDir,
                        stacked = true
                    )

                    Spacer(modifier = Modifier.height(16.dp))

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
                            val payload = if (command.endsWith("\n")) command else "$command\n"
                            viewModel.sendCommand(payload)
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
    onOpenWorkingDir: () -> Unit,
    workingDir: String,
    stacked: Boolean
) {
    val serverLabel = servers.firstOrNull { it.id == selectedServerId }?.name ?: "Custom"
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        ServerDropdown(
            serverLabel = serverLabel,
            servers = servers,
            selectedServerId = selectedServerId,
            onSelectServer = onSelectServer,
            onManageServers = onManageServers
        )
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
                OutlinedTextField(
                    value = workingDir,
                    onValueChange = { workingDir = it },
                    label = { Text("Working directory") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )
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
}

@Composable
private fun WorkingDirDialog(
    currentDir: String,
    onSave: (String) -> Unit,
    onDismiss: () -> Unit
) {
    var value by remember { mutableStateOf(currentDir) }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Working Directory") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                OutlinedTextField(
                    value = value,
                    onValueChange = { value = it },
                    label = { Text("Directory") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )
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
}

private fun File.safeDelete() {
    try {
        delete()
    } catch (_: Exception) {
        // ignore
    }
}

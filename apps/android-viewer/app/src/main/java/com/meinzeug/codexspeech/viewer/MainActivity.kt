@file:OptIn(ExperimentalMaterial3Api::class)

package com.meinzeug.codexspeech.viewer

import android.Manifest
import android.content.pm.PackageManager
import android.graphics.BitmapFactory
import android.content.res.Configuration
import android.os.Build
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
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.background
import androidx.compose.foundation.Image
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.FiberManualRecord
import androidx.compose.material.icons.filled.Mic
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.ArrowUpward
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Code
import androidx.compose.material.icons.filled.CompareArrows
import androidx.compose.material.icons.filled.ContentCopy
import androidx.compose.material.icons.filled.CreateNewFolder
import androidx.compose.material.icons.filled.Build
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Download
import androidx.compose.material.icons.filled.DriveFileRenameOutline
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.Error
import androidx.compose.material.icons.filled.FilterAlt
import androidx.compose.material.icons.filled.FindReplace
import androidx.compose.material.icons.filled.Folder
import androidx.compose.material.icons.filled.FolderOpen
import androidx.compose.material.icons.filled.Fullscreen
import androidx.compose.material.icons.filled.FitScreen
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.filled.InsertDriveFile
import androidx.compose.material.icons.filled.KeyboardArrowDown
import androidx.compose.material.icons.filled.KeyboardArrowUp
import androidx.compose.material.icons.filled.KeyboardArrowLeft
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Menu
import androidx.compose.material.icons.filled.NoteAdd
import androidx.compose.material.icons.filled.OpenInNew
import androidx.compose.material.icons.filled.PhoneAndroid
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.PowerSettingsNew
import androidx.compose.material.icons.filled.RadioButtonUnchecked
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material.icons.filled.Save
import androidx.compose.material.icons.filled.Send
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.Sort
import androidx.compose.material.icons.filled.Star
import androidx.compose.material.icons.filled.StarBorder
import androidx.compose.material.icons.filled.Stop
import androidx.compose.material.icons.filled.UploadFile
import androidx.compose.material.icons.filled.Visibility
import androidx.compose.material.icons.filled.VisibilityOff
import androidx.compose.material.icons.filled.Tag
import androidx.compose.material.icons.filled.ViewCarousel
import androidx.compose.material.icons.filled.VolumeDown
import androidx.compose.material.icons.filled.VolumeUp
import androidx.compose.material.icons.filled.KeyboardArrowRight
import androidx.compose.material.icons.filled.ZoomIn
import androidx.compose.material.icons.filled.ZoomOut
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.AssistChip
import androidx.compose.material3.BottomAppBar
import androidx.compose.material3.Button
import androidx.compose.material3.Divider
import androidx.compose.material3.DrawerValue
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExposedDropdownMenuBox
import androidx.compose.material3.ExposedDropdownMenuDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilledTonalIconButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.IconToggleButton
import androidx.compose.material3.ListItem
import androidx.compose.material3.ListItemDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalDrawerSheet
import androidx.compose.material3.ModalNavigationDrawer
import androidx.compose.material3.NavigationDrawerItem
import androidx.compose.material3.NavigationDrawerItemDefaults
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Slider
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.TopAppBarDefaults
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
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateMapOf
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
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalViewConfiguration
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import androidx.core.content.ContextCompat
import androidx.lifecycle.viewmodel.compose.viewModel
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import java.io.File
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import kotlin.math.roundToInt

private enum class RecordingMode {
    MANUAL,
    AUTO
}

private enum class AppScreen {
    HOME,
    WORKSPACE,
    RUNNER,
    LIVE_PHONE,
    FILE_MANAGER,
    GITHUB,
    SYSTEM
}

private val CodexLightColors = lightColorScheme(
    primary = Color(0xFF2F6BFF),
    onPrimary = Color.White,
    secondary = Color(0xFF38BDF8),
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
    primary = Color(0xFF4C6FFF),
    onPrimary = Color(0xFF0B1220),
    secondary = Color(0xFF7DD3FC),
    onSecondary = Color(0xFF0B1220),
    background = Color(0xFF0B0F1A),
    onBackground = Color(0xFFE5E7EB),
    surface = Color(0xFF0F172A),
    onSurface = Color(0xFFE5E7EB),
    surfaceVariant = Color(0xFF111C36),
    onSurfaceVariant = Color(0xFFE5E7EB),
    error = Color(0xFFF87171),
    onError = Color(0xFF0B1220)
)

@Composable
private fun CodexSpeechTheme(content: @Composable () -> Unit) {
    // Always use the dark palette to match the coding-focused visual style.
    MaterialTheme(colorScheme = CodexDarkColors, content = content)
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
    val codexStatus by viewModel.codexStatus.collectAsState()
    val gitStatus by viewModel.gitStatus.collectAsState()
    val sessionState by viewModel.sessionState.collectAsState()

    var ip by remember { mutableStateOf("") }
    var port by remember { mutableStateOf("17500") }
    var command by remember { mutableStateOf("") }
    var workingDir by remember { mutableStateOf("") }
    val configuration = LocalConfiguration.current
    val isLandscape = configuration.orientation == Configuration.ORIENTATION_LANDSCAPE
    val contentPadding = if (isLandscape) 8.dp else 16.dp

    val isConnected = connectionStatus == "Connected"
    val codexRunning = codexStatus?.running == true
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val terminalController = remember { TerminalController(context, viewModel::sendRaw) }
    val audioRecorder = remember { AudioRecorder(context) }
    var recordingMode by remember { mutableStateOf<RecordingMode?>(null) }
    var pendingRecordingMode by remember { mutableStateOf<RecordingMode?>(null) }
    val fileManagerStore = remember { FileManagerStore(context) }
    val workspaceStore = remember { WorkspaceStore(context) }

    val serverStore = remember { ServerStore(context) }
    var servers by remember { mutableStateOf(serverStore.load()) }
    var selectedServerId by remember { mutableStateOf(serverStore.loadSelectedServerId()) }
    var autoConnect by remember { mutableStateOf(workspaceStore.loadAutoConnect()) }
    var autoStartCodex by remember { mutableStateOf(workspaceStore.loadAutoStartCodex()) }
    var workingDirOverride by remember { mutableStateOf<String?>(null) }

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
    var runnerDeviceManual by remember { mutableStateOf(false) }
    var runnerMetroPort by remember { mutableStateOf("8081") }
    var runnerMode by remember { mutableStateOf("adb") }
    var runnerMessage by remember { mutableStateOf<String?>(null) }
    var codexMessage by remember { mutableStateOf<String?>(null) }
    var showRunnerInstallPrompt by remember { mutableStateOf(false) }
    var runnerInstallReason by remember { mutableStateOf<String?>(null) }
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
    var liveFullscreen by remember { mutableStateOf(false) }
    var terminalFullscreen by remember { mutableStateOf(false) }
    var gitMessage by remember { mutableStateOf<String?>(null) }
    var gitCommitMessage by remember { mutableStateOf("") }
    var gitAddAll by remember { mutableStateOf(true) }
    var adminStatus by remember { mutableStateOf<AdminStatus?>(null) }
    var adminDeviceInfo by remember { mutableStateOf<AdminDeviceInfo?>(null) }
    var adminMessage by remember { mutableStateOf<String?>(null) }
    var selectedAdminDeviceId by remember { mutableStateOf<String?>(null) }

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
        workingDirOverride = workspaceStore.loadWorkingDir(selectedServerId)
        if (server != null) {
            ip = server.host
            port = server.port
            workingDir = workingDirOverride?.ifBlank { null } ?: server.workingDir
        }
    }

    LaunchedEffect(autoConnect, connectionStatus, ip, port, workingDir) {
        if (!autoConnect) return@LaunchedEffect
        if (ip.isBlank() || port.isBlank()) return@LaunchedEffect
        val status = connectionStatus.lowercase()
        if (status.startsWith("connected") || status.startsWith("connecting")) return@LaunchedEffect
        delay(1500)
        if (autoConnect && !connectionStatus.lowercase().startsWith("connected")) {
            viewModel.connectToBackend(ip.trim(), port.trim(), workingDir.trim().ifBlank { null })
        }
    }

    LaunchedEffect(isConnected) {
        if (isConnected && ip.isNotBlank()) {
            viewModel.fetchSessionState(ip.trim(), port.trim())
            val codexResult = viewModel.fetchCodexStatus(ip.trim(), port.trim())
            codexMessage = if (codexResult.isSuccess) null else codexResult.exceptionOrNull()?.message
            val gitResult = viewModel.fetchGitStatus(ip.trim(), port.trim(), workingDir.trim().ifBlank { null })
            gitMessage = if (gitResult.isSuccess) null else gitResult.exceptionOrNull()?.message
        }
    }

    LaunchedEffect(isConnected, workingDir) {
        if (isConnected && ip.isNotBlank() && workingDir.isNotBlank()) {
            viewModel.updateSessionWorkingDir(ip.trim(), port.trim(), workingDir.trim())
        }
    }

    fun refreshRunnerDevices() {
        if (!isConnected || ip.isBlank()) return
        scope.launch {
            val result = viewModel.fetchRunnerDevices(ip.trim(), port.trim())
            if (result.isSuccess) {
                runnerDevices = result.getOrDefault(emptyList())
                val preferred = selectPreferredDevice(runnerDevices)
                val selectedStillValid = selectedRunnerDeviceId?.let { id ->
                    runnerDevices.any { it.id == id }
                } ?: false
                if (!selectedStillValid) {
                    runnerDeviceManual = false
                }
                if (!runnerDeviceManual) {
                    selectedRunnerDeviceId = preferred?.id ?: runnerDevices.firstOrNull()?.id
                } else if (selectedRunnerDeviceId == null && runnerDevices.isNotEmpty()) {
                    selectedRunnerDeviceId = preferred?.id ?: runnerDevices.first().id
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

    fun refreshCodexStatus() {
        if (!isConnected || ip.isBlank()) return
        scope.launch {
            val result = viewModel.fetchCodexStatus(ip.trim(), port.trim())
            codexMessage = if (result.isSuccess) null else result.exceptionOrNull()?.message
        }
    }

    fun refreshGitStatus() {
        if (!isConnected || ip.isBlank()) return
        scope.launch {
            val result = viewModel.fetchGitStatus(ip.trim(), port.trim(), workingDir.trim().ifBlank { null })
            if (result.isFailure) {
                gitMessage = result.exceptionOrNull()?.message
            } else {
                gitMessage = null
            }
        }
    }

    fun gitPull() {
        if (!isConnected || ip.isBlank()) return
        scope.launch {
            gitMessage = "Pulling..."
            val result = viewModel.gitPull(ip.trim(), port.trim(), workingDir.trim().ifBlank { null })
            gitMessage = result.fold(
                onSuccess = { "Pull ok" },
                onFailure = { "Pull failed: ${it.message}" }
            )
            refreshGitStatus()
        }
    }

    fun gitPush() {
        if (!isConnected || ip.isBlank()) return
        scope.launch {
            gitMessage = "Pushing..."
            val result = viewModel.gitPush(ip.trim(), port.trim(), workingDir.trim().ifBlank { null })
            gitMessage = result.fold(
                onSuccess = { "Push ok" },
                onFailure = { "Push failed: ${it.message}" }
            )
            refreshGitStatus()
        }
    }

    fun gitCommit() {
        if (!isConnected || ip.isBlank()) return
        val msg = gitCommitMessage.trim()
        if (msg.isBlank()) {
            gitMessage = "Enter a commit message."
            return
        }
        scope.launch {
            gitMessage = "Committing..."
            val result = viewModel.gitCommit(
                host = ip.trim(),
                port = port.trim(),
                path = workingDir.trim().ifBlank { null },
                message = msg,
                addAll = gitAddAll
            )
            gitMessage = result.fold(
                onSuccess = { "Commit ok" },
                onFailure = { "Commit failed: ${it.message}" }
            )
            if (result.isSuccess) {
                gitCommitMessage = ""
            }
            refreshGitStatus()
        }
    }

    fun startCodex() {
        if (!isConnected || ip.isBlank()) {
            codexMessage = "Connect to backend first."
            return
        }
        scope.launch {
            codexMessage = "Starting Codex..."
            val result = viewModel.startCodex(
                host = ip.trim(),
                port = port.trim(),
                cwd = workingDir.trim().ifBlank { null }
            )
            codexMessage = if (result.isSuccess) null else result.exceptionOrNull()?.message
        }
    }

    fun stopCodex() {
        if (!isConnected || ip.isBlank()) return
        scope.launch {
            val result = viewModel.stopCodex(ip.trim(), port.trim())
            codexMessage = if (result.isSuccess) "Codex stopped." else result.exceptionOrNull()?.message
        }
    }

    LaunchedEffect(isConnected, autoStartCodex, codexRunning) {
        if (isConnected && autoStartCodex && !codexRunning) {
            startCodex()
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

    fun refreshAdminDeviceInfo() {
        val deviceId = selectedAdminDeviceId ?: return
        if (ip.isBlank() || port.isBlank()) {
            adminMessage = "Set a server host and port first."
            return
        }
        scope.launch {
            val result = viewModel.fetchAdminDeviceInfo(ip.trim(), port.trim(), deviceId)
            adminDeviceInfo = result.getOrNull()
            adminMessage = result.exceptionOrNull()?.message
        }
    }

    fun refreshAdminStatus() {
        if (ip.isBlank() || port.isBlank()) {
            adminMessage = "Set a server host and port first."
            return
        }
        scope.launch {
            val result = viewModel.fetchAdminStatus(ip.trim(), port.trim())
            adminStatus = result.getOrNull()
            adminMessage = result.exceptionOrNull()?.message
            if (selectedAdminDeviceId == null) {
                selectedAdminDeviceId = adminStatus?.devices?.firstOrNull()?.id
            }
            refreshAdminDeviceInfo()
        }
    }

    fun restartService(target: String) {
        if (ip.isBlank() || port.isBlank()) {
            adminMessage = "Set a server host and port first."
            return
        }
        scope.launch {
            val result = viewModel.adminRestart(ip.trim(), port.trim(), target)
            adminMessage = if (result.isSuccess) "Restarted $target." else result.exceptionOrNull()?.message
            refreshAdminStatus()
        }
    }

    fun buildApk(target: String) {
        if (ip.isBlank() || port.isBlank()) {
            adminMessage = "Set a server host and port first."
            return
        }
        scope.launch {
            val result = viewModel.adminBuild(ip.trim(), port.trim(), target)
            adminMessage = if (result.isSuccess) "Build $target completed." else result.exceptionOrNull()?.message
        }
    }

    fun installApk(target: String) {
        val deviceId = selectedAdminDeviceId
        if (deviceId.isNullOrBlank()) {
            adminMessage = "Select a device first."
            return
        }
        if (ip.isBlank() || port.isBlank()) {
            adminMessage = "Set a server host and port first."
            return
        }
        scope.launch {
            val result = viewModel.adminInstall(ip.trim(), port.trim(), target, deviceId)
            adminMessage = if (result.isSuccess) "Installed $target on $deviceId." else result.exceptionOrNull()?.message
            refreshAdminDeviceInfo()
        }
    }

    LaunchedEffect(currentScreen) {
        if (currentScreen == AppScreen.LIVE_PHONE) {
            refreshLiveDevices()
        } else if (currentScreen != AppScreen.LIVE_PHONE && liveStreaming) {
            liveStreaming = false
        }
        if (currentScreen == AppScreen.SYSTEM) {
            refreshAdminStatus()
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

    fun maybePromptRunnerInstall(message: String?): Boolean {
        if (message.isNullOrBlank()) return false
        val needsInstall = message.contains("not installed", ignoreCase = true)
        if (!needsInstall) return false
        runnerInstallReason = message
        showRunnerInstallPrompt = true
        return true
    }

    fun installRunnerApp() {
        if (!isConnected || ip.isBlank()) {
            runnerMessage = "Connect to backend first."
            return
        }
        val selectedType = if (runnerTypeChoice == "auto") runnerDetectedType else runnerTypeChoice
        val deviceId = selectedRunnerDeviceId ?: selectPreferredDevice(runnerDevices)?.id
        if (deviceId != null && selectedRunnerDeviceId == null) {
            selectedRunnerDeviceId = deviceId
        }
        if (selectedType.isNullOrBlank()) {
            runnerMessage = "Select a project type first."
            return
        }
        if (deviceId.isNullOrBlank()) {
            runnerMessage = "Select a device first."
            return
        }
        val runnerPath = selectedRunnerProjectPath ?: workingDir.trim().ifBlank { null }
        scope.launch {
            runnerMessage = "Installing app on device..."
            val result = viewModel.installRunnerApp(
                host = ip.trim(),
                port = port.trim(),
                path = runnerPath,
                projectType = selectedType,
                deviceId = deviceId,
                metroPort = runnerMetroPort.toIntOrNull() ?: 8081
            )
            if (result.isFailure) {
                runnerMessage = result.exceptionOrNull()?.message
            } else {
                runnerMessage = "Install completed."
                val openResult = viewModel.openRunnerApp(
                    host = ip.trim(),
                    port = port.trim(),
                    packageName = runnerPackageName,
                    path = runnerPath,
                    deviceId = deviceId,
                    projectType = selectedType,
                    mode = runnerMode,
                    metroPort = runnerMetroPort.toIntOrNull() ?: 8081
                )
                if (openResult.isFailure) {
                    runnerMessage = openResult.exceptionOrNull()?.message
                }
            }
        }
    }

    fun startRunner() {
        if (!isConnected || ip.isBlank()) return
        val selectedType = if (runnerTypeChoice == "auto") runnerDetectedType else runnerTypeChoice
        val deviceId = selectedRunnerDeviceId ?: selectPreferredDevice(runnerDevices)?.id
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
                    deviceId = deviceId,
                    projectType = selectedType,
                    mode = runnerMode,
                    metroPort = runnerMetroPort.toIntOrNull() ?: 8081
                )
                if (openResult.isFailure) {
                    val msg = openResult.exceptionOrNull()?.message
                    val prefixed = if (!runnerMessage.isNullOrBlank()) {
                        "${runnerMessage} Open app failed: $msg"
                    } else {
                        msg
                    }
                    if (!maybePromptRunnerInstall(prefixed)) {
                        runnerMessage = prefixed
                    } else {
                        runnerMessage = prefixed
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
            val selectedType = if (runnerTypeChoice == "auto") runnerDetectedType else runnerTypeChoice
            val result = viewModel.openRunnerApp(
                ip.trim(),
                port.trim(),
                runnerPackageName,
                runnerPath,
                selectedRunnerDeviceId,
                projectType = selectedType,
                mode = runnerMode,
                metroPort = runnerMetroPort.toIntOrNull() ?: 8081
            )
            if (result.isFailure) {
                val msg = result.exceptionOrNull()?.message
                if (!maybePromptRunnerInstall(msg)) {
                    runnerMessage = msg
                } else {
                    runnerMessage = msg
                }
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
            refreshCodexStatus()
            refreshGitStatus()
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
        val override = workspaceStore.loadWorkingDir(server.id)
        workingDirOverride = override
        workingDir = override?.ifBlank { null } ?: server.workingDir
    }

    fun selectServer(server: ServerProfile?) {
        if (server == null) {
            selectedServerId = null
            serverStore.saveSelectedServerId(null)
            workingDirOverride = workspaceStore.loadWorkingDir(null)
            if (!workingDirOverride.isNullOrBlank()) {
                workingDir = workingDirOverride.orEmpty()
            }
            return
        }
        applyServer(server)
    }

    fun applyWorkingDir(path: String) {
        val trimmed = path.trim()
        workingDir = trimmed
        workingDirOverride = trimmed.ifBlank { null }
        workspaceStore.saveWorkingDir(selectedServerId, trimmed.ifBlank { null })
        if (isConnected && ip.isNotBlank()) {
            scope.launch {
                viewModel.updateSessionWorkingDir(ip.trim(), port.trim(), trimmed)
            }
        }
    }

    fun useServerDefaultWorkingDir() {
        val server = servers.firstOrNull { it.id == selectedServerId }
        val defaultDir = server?.workingDir?.trim().orEmpty()
        workingDirOverride = null
        workspaceStore.saveWorkingDir(selectedServerId, null)
        workingDir = defaultDir
        if (isConnected && ip.isNotBlank() && defaultDir.isNotBlank()) {
            scope.launch {
                viewModel.updateSessionWorkingDir(ip.trim(), port.trim(), defaultDir)
            }
        }
    }

    fun applySessionWorkingDir() {
        val sessionDir = sessionState?.workingDirectory?.trim().orEmpty()
        if (sessionDir.isNotBlank()) {
            applyWorkingDir(sessionDir)
        }
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
                        label = { Text("Workspace") },
                        selected = currentScreen == AppScreen.WORKSPACE,
                        onClick = {
                            currentScreen = AppScreen.WORKSPACE
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
                    NavigationDrawerItem(
                        label = { Text("Code") },
                        selected = currentScreen == AppScreen.FILE_MANAGER,
                        onClick = {
                            currentScreen = AppScreen.FILE_MANAGER
                            scope.launch { drawerState.close() }
                        },
                        modifier = Modifier.padding(NavigationDrawerItemDefaults.ItemPadding)
                    )
                    NavigationDrawerItem(
                        label = { Text("Github") },
                        selected = currentScreen == AppScreen.GITHUB,
                        onClick = {
                            currentScreen = AppScreen.GITHUB
                            scope.launch { drawerState.close() }
                        },
                        modifier = Modifier.padding(NavigationDrawerItemDefaults.ItemPadding)
                    )
                    NavigationDrawerItem(
                        label = { Text("System") },
                        selected = currentScreen == AppScreen.SYSTEM,
                        onClick = {
                            currentScreen = AppScreen.SYSTEM
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
                            colors = TopAppBarDefaults.topAppBarColors(
                                containerColor = Color(0xFF0B1326),
                                titleContentColor = Color(0xFFE6ECFF),
                                navigationIconContentColor = Color(0xFFE6ECFF),
                                actionIconContentColor = Color(0xFFE6ECFF)
                            ),
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
                                                if (autoConnect) {
                                                    autoConnect = false
                                                    workspaceStore.saveAutoConnect(false)
                                                }
                                            } else {
                                                viewModel.connectToBackend(
                                                    ip.trim(),
                                                    port.trim(),
                                                    workingDir.trim().ifBlank { null }
                                                )
                                            }
                                        },
                                        autoConnect = autoConnect,
                                        onAutoConnectChange = {
                                            autoConnect = it
                                            workspaceStore.saveAutoConnect(it)
                                        },
                                        autoStartCodex = autoStartCodex,
                                        onAutoStartCodexChange = {
                                            autoStartCodex = it
                                            workspaceStore.saveAutoStartCodex(it)
                                        },
                                        codexRunning = codexRunning,
                                        onToggleCodex = {
                                            if (codexRunning) {
                                                stopCodex()
                                            } else {
                                                startCodex()
                                            }
                                        },
                                        codexMessage = codexMessage,
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
                    Column(modifier = Modifier.fillMaxSize()) {
                        WorkspaceStatusBar(
                            serverName = servers.firstOrNull { it.id == selectedServerId }?.name ?: "Custom",
                            host = ip.trim(),
                            port = port.trim(),
                            workingDir = sessionState?.workingDirectory ?: workingDir,
                            isConnected = isConnected,
                            codexRunning = codexRunning,
                            runnerStatus = runnerStatus
                        )
                        Box(modifier = Modifier.weight(1f)) {
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
                                            onAutoFitChanged = { autoFit = it },
                                            onOpenLivePhone = {
                                                currentScreen = AppScreen.LIVE_PHONE
                                                liveStreaming = true
                                                liveFullscreen = true
                                            },
                                            onOpenRunner = ::openRunnerApp,
                                            onReloadAndOpenRunner = {
                                                reloadReactNative()
                                                openRunnerApp()
                                            },
                                            onFullscreen = { terminalFullscreen = true }
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
                                        onAutoFitChanged = { autoFit = it },
                                        onOpenLivePhone = {
                                            currentScreen = AppScreen.LIVE_PHONE
                                            liveStreaming = true
                                            liveFullscreen = true
                                        },
                                        onOpenRunner = ::openRunnerApp,
                                        onReloadAndOpenRunner = {
                                            reloadReactNative()
                                            openRunnerApp()
                                        },
                                        onFullscreen = { terminalFullscreen = true }
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
                        AppScreen.WORKSPACE -> {
                            WorkspaceScreen(
                                ip = ip,
                                onIpChange = { ip = it },
                                port = port,
                                onPortChange = { port = it },
                                isConnected = isConnected,
                                connectionStatus = connectionStatus,
                                onToggleConnection = {
                                    if (isConnected) {
                                        viewModel.disconnect()
                                        if (autoConnect) {
                                            autoConnect = false
                                            workspaceStore.saveAutoConnect(false)
                                        }
                                    } else {
                                        viewModel.connectToBackend(
                                            ip.trim(),
                                            port.trim(),
                                            workingDir.trim().ifBlank { null }
                                        )
                                    }
                                },
                                autoConnect = autoConnect,
                                onAutoConnectChange = {
                                    autoConnect = it
                                    workspaceStore.saveAutoConnect(it)
                                },
                                autoStartCodex = autoStartCodex,
                                onAutoStartCodexChange = {
                                    autoStartCodex = it
                                    workspaceStore.saveAutoStartCodex(it)
                                },
                                codexRunning = codexRunning,
                                onToggleCodex = {
                                    if (codexRunning) stopCodex() else startCodex()
                                },
                                codexMessage = codexMessage,
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
                                sessionWorkingDir = sessionState?.workingDirectory,
                                onUseServerDefault = ::useServerDefaultWorkingDir,
                                onUseSessionDir = ::applySessionWorkingDir,
                                runnerStatus = runnerStatus
                            )
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
                                Text(
                                    text = "Pro-config hotload console · compact layout",
                                    style = MaterialTheme.typography.labelMedium,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
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
                                    onSelectDevice = {
                                        selectedRunnerDeviceId = it
                                        runnerDeviceManual = true
                                    },
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
                                verticalArrangement = Arrangement.spacedBy(10.dp)
                            ) {
                                var showStreamSettings by remember { mutableStateOf(false) }
                                var showControlPanel by remember { mutableStateOf(false) }
                                var showInputPanel by remember { mutableStateOf(false) }
                                val selectedLiveDevice = liveDevices.firstOrNull { it.id == selectedLiveDeviceId }
                                val deviceLabel = selectedLiveDevice?.model ?: "No device"
                                val frameAspect = liveFrame?.let { it.width.toFloat() / it.height.toFloat() } ?: (9f / 16f)

                                Text(text = "Live Phone", style = MaterialTheme.typography.labelLarge)
                                Text(
                                    text = "Pro-config live stream console",
                                    style = MaterialTheme.typography.labelMedium,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                                Divider()

                                Surface(tonalElevation = 1.dp, modifier = Modifier.fillMaxWidth()) {
                                    Column(
                                        modifier = Modifier.padding(10.dp),
                                        verticalArrangement = Arrangement.spacedBy(8.dp)
                                    ) {
                                        Text(text = "Quick", style = MaterialTheme.typography.labelLarge)
                                        Row(
                                            modifier = Modifier
                                                .fillMaxWidth()
                                                .horizontalScroll(rememberScrollState()),
                                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                                        ) {
                                            AssistChip(
                                                onClick = { liveStreaming = true },
                                                enabled = isConnected,
                                                label = { Text("Start") },
                                                leadingIcon = { Icon(Icons.Default.PlayArrow, contentDescription = null) }
                                            )
                                            AssistChip(
                                                onClick = { liveStreaming = false },
                                                enabled = liveStreaming,
                                                label = { Text("Stop") },
                                                leadingIcon = { Icon(Icons.Default.Stop, contentDescription = null) }
                                            )
                                            AssistChip(
                                                onClick = { liveFullscreen = true },
                                                enabled = isConnected,
                                                label = { Text("Fullsize") },
                                                leadingIcon = { Icon(Icons.Default.Fullscreen, contentDescription = null) }
                                            )
                                        }
                                        Text(
                                            text = "Device: $deviceLabel · FPS $liveFps · ${liveFormat.uppercase()}",
                                            style = MaterialTheme.typography.labelSmall,
                                            color = MaterialTheme.colorScheme.onSurfaceVariant
                                        )
                                    }
                                }
                                Surface(tonalElevation = 1.dp, modifier = Modifier.fillMaxWidth()) {
                                    Column(
                                        modifier = Modifier.padding(10.dp),
                                        verticalArrangement = Arrangement.spacedBy(6.dp)
                                    ) {
                                        Text(text = "Preview", style = MaterialTheme.typography.labelLarge)
                                        LivePreviewBox(
                                            liveFrame = liveFrame,
                                            isConnected = isConnected,
                                            livePreviewSize = livePreviewSize,
                                            onSizeChanged = { livePreviewSize = it },
                                            onTap = ::sendLiveTap,
                                            onSwipe = ::sendLiveSwipe,
                                            onLongPress = ::sendLiveLongPress,
                                            modifier = Modifier
                                                .fillMaxWidth()
                                                .aspectRatio(frameAspect)
                                        )
                                        if (!liveMessage.isNullOrBlank()) {
                                            Text(text = liveMessage ?: "", style = MaterialTheme.typography.labelSmall)
                                        }
                                    }
                                }

                                Surface(tonalElevation = 1.dp, modifier = Modifier.fillMaxWidth()) {
                                    Column(
                                        modifier = Modifier.padding(10.dp),
                                        verticalArrangement = Arrangement.spacedBy(8.dp)
                                    ) {
                                        Row(
                                            modifier = Modifier.fillMaxWidth(),
                                            horizontalArrangement = Arrangement.SpaceBetween,
                                            verticalAlignment = Alignment.CenterVertically
                                        ) {
                                            Text(text = "Stream Settings", style = MaterialTheme.typography.labelLarge)
                                            FilterChip(
                                                selected = showStreamSettings,
                                                onClick = { showStreamSettings = !showStreamSettings },
                                                label = { Text(if (showStreamSettings) "Shown" else "Hidden") },
                                                leadingIcon = { Icon(Icons.Default.Settings, contentDescription = null) }
                                            )
                                        }
                                        AnimatedVisibility(visible = showStreamSettings) {
                                            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                                                Row(
                                                    modifier = Modifier.fillMaxWidth(),
                                                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                                                    verticalAlignment = Alignment.CenterVertically
                                                ) {
                                                    DeviceDropdown(
                                                        devices = liveDevices,
                                                        selectedDeviceId = selectedLiveDeviceId,
                                                        onSelectDevice = { selectedLiveDeviceId = it },
                                                        modifier = Modifier.weight(1f)
                                                    )
                                                    IconButton(onClick = { refreshLiveDevices() }, enabled = isConnected) {
                                                        Icon(Icons.Default.Refresh, contentDescription = "Refresh devices")
                                                    }
                                                }
                                                Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                                                    Text(text = "FPS: $liveFps", style = MaterialTheme.typography.labelSmall)
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
                                                        Text(text = "JPEG Quality: $liveJpegQuality", style = MaterialTheme.typography.labelSmall)
                                                        Slider(
                                                            value = liveJpegQuality.toFloat(),
                                                            onValueChange = { liveJpegQuality = it.roundToInt().coerceIn(30, 100) },
                                                            valueRange = 30f..100f,
                                                            steps = 69
                                                        )
                                                    }
                                                }
                                                Row(
                                                    modifier = Modifier
                                                        .fillMaxWidth()
                                                        .horizontalScroll(rememberScrollState()),
                                                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                                                ) {
                                                    AssistChip(
                                                        onClick = { installLiveHelper() },
                                                        enabled = isConnected,
                                                        label = { Text("Install") },
                                                        leadingIcon = { Icon(Icons.Default.Download, contentDescription = null) }
                                                    )
                                                    AssistChip(
                                                        onClick = { openLiveHelper() },
                                                        enabled = isConnected,
                                                        label = { Text("Open") },
                                                        leadingIcon = { Icon(Icons.Default.OpenInNew, contentDescription = null) }
                                                    )
                                                }
                                            }
                                        }
                                    }
                                }
                                Surface(tonalElevation = 1.dp, modifier = Modifier.fillMaxWidth()) {
                                    Column(
                                        modifier = Modifier.padding(10.dp),
                                        verticalArrangement = Arrangement.spacedBy(8.dp)
                                    ) {
                                        Row(
                                            modifier = Modifier.fillMaxWidth(),
                                            horizontalArrangement = Arrangement.SpaceBetween,
                                            verticalAlignment = Alignment.CenterVertically
                                        ) {
                                            Text(text = "Controls", style = MaterialTheme.typography.labelLarge)
                                            FilterChip(
                                                selected = showControlPanel,
                                                onClick = { showControlPanel = !showControlPanel },
                                                label = { Text(if (showControlPanel) "Shown" else "Hidden") },
                                                leadingIcon = { Icon(Icons.Default.Settings, contentDescription = null) }
                                            )
                                        }
                                        AnimatedVisibility(visible = showControlPanel) {
                                            Row(
                                                modifier = Modifier
                                                    .fillMaxWidth()
                                                    .horizontalScroll(rememberScrollState()),
                                                horizontalArrangement = Arrangement.spacedBy(8.dp)
                                            ) {
                                                AssistChip(
                                                    onClick = { wakeLiveDevice() },
                                                    enabled = isConnected,
                                                    label = { Text("Wake") },
                                                    leadingIcon = { Icon(Icons.Default.PowerSettingsNew, contentDescription = null) }
                                                )
                                                AssistChip(
                                                    onClick = { sendLiveKey(4) },
                                                    enabled = isConnected,
                                                    label = { Text("Back") },
                                                    leadingIcon = { Icon(Icons.Default.ArrowBack, contentDescription = null) }
                                                )
                                                AssistChip(
                                                    onClick = { sendLiveKey(3) },
                                                    enabled = isConnected,
                                                    label = { Text("Home") },
                                                    leadingIcon = { Icon(Icons.Default.Home, contentDescription = null) }
                                                )
                                                AssistChip(
                                                    onClick = { sendLiveKey(187) },
                                                    enabled = isConnected,
                                                    label = { Text("Overview") },
                                                    leadingIcon = { Icon(Icons.Default.ViewCarousel, contentDescription = null) }
                                                )
                                                AssistChip(
                                                    onClick = { sendLiveKey(24) },
                                                    enabled = isConnected,
                                                    label = { Text("Vol +") },
                                                    leadingIcon = { Icon(Icons.Default.VolumeUp, contentDescription = null) }
                                                )
                                                AssistChip(
                                                    onClick = { sendLiveKey(25) },
                                                    enabled = isConnected,
                                                    label = { Text("Vol -") },
                                                    leadingIcon = { Icon(Icons.Default.VolumeDown, contentDescription = null) }
                                                )
                                            }
                                        }
                                    }
                                }
                                Surface(tonalElevation = 1.dp, modifier = Modifier.fillMaxWidth()) {
                                    Column(
                                        modifier = Modifier.padding(10.dp),
                                        verticalArrangement = Arrangement.spacedBy(8.dp)
                                    ) {
                                        Row(
                                            modifier = Modifier.fillMaxWidth(),
                                            horizontalArrangement = Arrangement.SpaceBetween,
                                            verticalAlignment = Alignment.CenterVertically
                                        ) {
                                            Text(text = "Input", style = MaterialTheme.typography.labelLarge)
                                            FilterChip(
                                                selected = showInputPanel,
                                                onClick = { showInputPanel = !showInputPanel },
                                                label = { Text(if (showInputPanel) "Shown" else "Hidden") },
                                                leadingIcon = { Icon(Icons.Default.Mic, contentDescription = null) }
                                            )
                                        }
                                        AnimatedVisibility(visible = showInputPanel) {
                                            Row(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
                                                OutlinedTextField(
                                                    value = liveText,
                                                    onValueChange = { liveText = it },
                                                    label = { Text("Text input") },
                                                    singleLine = true,
                                                    modifier = Modifier.weight(1f)
                                                )
                                                FilledTonalIconButton(
                                                    onClick = { sendLiveText() },
                                                    enabled = isConnected && liveText.isNotBlank()
                                                ) {
                                                    Icon(Icons.Default.Send, contentDescription = "Send")
                                                }
                                            }
                                        }
                                    }
                                }
                            }
                        }
                        AppScreen.FILE_MANAGER -> {
                            Column(
                                modifier = Modifier
                                    .fillMaxSize()
                                    .padding(contentPadding),
                                verticalArrangement = Arrangement.spacedBy(12.dp)
                            ) {
                                Text(text = "Code", style = MaterialTheme.typography.labelLarge)
                                Text(
                                    text = "Pro-config file ops & search",
                                    style = MaterialTheme.typography.labelMedium,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                                Divider()
                                FileManagerScreen(
                                    host = ip.trim(),
                                    port = port.trim(),
                                    workingDir = workingDir,
                                    isConnected = isConnected,
                                    viewModel = viewModel,
                                    store = fileManagerStore,
                                    gitStatus = gitStatus,
                                    onRefreshGit = ::refreshGitStatus,
                                    onUseWorkingDir = { applyWorkingDir(it) }
                                )
                            }
                        }
                        AppScreen.GITHUB -> {
                            Column(
                                modifier = Modifier
                                    .fillMaxSize()
                                    .padding(contentPadding)
                                    .verticalScroll(rememberScrollState()),
                                verticalArrangement = Arrangement.spacedBy(12.dp)
                            ) {
                                Text(text = "Github", style = MaterialTheme.typography.labelLarge)
                                Text(
                                    text = "Pro-config git controls",
                                    style = MaterialTheme.typography.labelMedium,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                                Divider()
                                GitHubManagerScreen(
                                    host = ip.trim(),
                                    port = port.trim(),
                                    isConnected = isConnected,
                                    workingDir = workingDir,
                                    status = gitStatus,
                                    commitMessage = gitCommitMessage,
                                    onCommitMessageChange = { gitCommitMessage = it },
                                    addAll = gitAddAll,
                                    onToggleAddAll = { gitAddAll = it },
                                    onRefresh = ::refreshGitStatus,
                                    onPull = ::gitPull,
                                    onPush = ::gitPush,
                                    onCommit = ::gitCommit,
                                    message = gitMessage,
                                    viewModel = viewModel
                                )
                            }
                        }
                        AppScreen.SYSTEM -> {
                            Column(
                                modifier = Modifier
                                    .fillMaxSize()
                                    .padding(contentPadding)
                                    .verticalScroll(rememberScrollState()),
                                verticalArrangement = Arrangement.spacedBy(12.dp)
                            ) {
                                Text(text = "System Status", style = MaterialTheme.typography.labelLarge)
                                Text(
                                    text = "Pro-config service & device console",
                                    style = MaterialTheme.typography.labelMedium,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                                Divider()
                                SystemStatusSection(
                                    connectionStatus = connectionStatus,
                                    host = ip.trim(),
                                    port = port.trim(),
                                    workingDir = workingDir,
                                    adminStatus = adminStatus,
                                    deviceInfo = adminDeviceInfo,
                                    selectedDeviceId = selectedAdminDeviceId,
                                    onSelectDevice = {
                                        selectedAdminDeviceId = it
                                        refreshAdminDeviceInfo()
                                    },
                                    onRefresh = { refreshAdminStatus() },
                                    onRestartBackend = { restartService("codex-backend") },
                                    onRestartWeb = { restartService("codex-web") },
                                    onBuildViewer = { buildApk("viewer") },
                                    onBuildLive = { buildApk("live") },
                                    onInstallViewer = { installApk("viewer") },
                                    onInstallLive = { installApk("live") },
                                    message = adminMessage
                                )
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

            if (liveFullscreen) {
                Dialog(
                    onDismissRequest = { liveFullscreen = false },
                    properties = DialogProperties(usePlatformDefaultWidth = false)
                ) {
                    Box(
                        modifier = Modifier
                            .fillMaxSize()
                            .background(Color.Black)
                    ) {
                        LivePreviewBox(
                            liveFrame = liveFrame,
                            isConnected = isConnected,
                            livePreviewSize = livePreviewSize,
                            onSizeChanged = { livePreviewSize = it },
                            onTap = ::sendLiveTap,
                            onSwipe = ::sendLiveSwipe,
                            onLongPress = ::sendLiveLongPress,
                            modifier = Modifier.fillMaxSize()
                        )
                        IconButton(
                            onClick = { liveFullscreen = false },
                            modifier = Modifier
                                .align(Alignment.TopStart)
                                .padding(12.dp)
                                .background(Color(0xAA000000), CircleShape)
                        ) {
                            Icon(
                                imageVector = Icons.Default.Close,
                                contentDescription = "Close",
                                tint = Color.White
                            )
                        }
                    }
                }
            }

            if (terminalFullscreen) {
                Dialog(
                    onDismissRequest = { terminalFullscreen = false },
                    properties = DialogProperties(usePlatformDefaultWidth = false)
                ) {
                    Box(
                        modifier = Modifier
                            .fillMaxSize()
                            .background(MaterialTheme.colorScheme.background)
                    ) {
                        Column(
                            modifier = Modifier
                                .fillMaxSize()
                                .padding(12.dp)
                                .imePadding(),
                            verticalArrangement = Arrangement.spacedBy(12.dp)
                        ) {
                            TerminalSection(
                                terminalController = terminalController,
                                modifier = Modifier.weight(1f),
                                fillHeight = true,
                                autoFit = autoFit,
                                onAutoFitChanged = { autoFit = it },
                                onOpenLivePhone = null,
                                onOpenRunner = null,
                                onReloadAndOpenRunner = null,
                                onFullscreen = null
                            )
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
                        IconButton(
                            onClick = { terminalFullscreen = false },
                            modifier = Modifier
                                .align(Alignment.TopStart)
                                .padding(12.dp)
                                .background(Color(0xAA000000), CircleShape)
                        ) {
                            Icon(
                                imageVector = Icons.Default.Close,
                                contentDescription = "Close",
                                tint = Color.White
                            )
                        }
                    }
                }
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
                        applyWorkingDir(it)
                        showWorkingDirDialog = false
                    },
                    onDismiss = { showWorkingDirDialog = false }
                )
            }

            if (showRunnerInstallPrompt) {
                AlertDialog(
                    onDismissRequest = { showRunnerInstallPrompt = false },
                    title = { Text("App installieren?") },
                    text = {
                        Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                            Text("Die App ist auf dem Zielgerät nicht installiert.")
                            if (!runnerInstallReason.isNullOrBlank()) {
                                Text(
                                    text = runnerInstallReason ?: "",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                            Text("App installieren auf Gerät?")
                        }
                    },
                    confirmButton = {
                        TextButton(
                            onClick = {
                                showRunnerInstallPrompt = false
                                installRunnerApp()
                            }
                        ) { Text("Ja") }
                    },
                    dismissButton = {
                        TextButton(onClick = { showRunnerInstallPrompt = false }) {
                            Text("Nein")
                        }
                    }
                )
            }
        }
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
    val selectedDevice = devices.firstOrNull { it.id == selectedDeviceId } ?: selectPreferredDevice(devices)
    val deviceLabel = selectedDevice?.model ?: "No device"
    val projectName = selectedProjectPath?.substringAfterLast("/") ?: "Auto"
    val typeLabel = when (resolvedType) {
        "react-native" -> "RN"
        "flutter" -> "Flutter"
        null -> "Unknown"
        else -> resolvedType
    }
    val statusBits = buildList {
        if (status?.appRunning == true) add("App")
        if (status?.metroRunning == true) add("Metro")
        if (status?.flutterRunning == true) add("Flutter")
    }
    val statusLine = if (statusBits.isEmpty()) "Idle" else statusBits.joinToString(" · ")
    var showAdvanced by remember { mutableStateOf(true) }
    val sanitizedRunnerMessage = runnerMessage?.takeIf { it.isNotBlank() && it.lowercase() != "null" }

    Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
        Surface(tonalElevation = 1.dp, modifier = Modifier.fillMaxWidth()) {
            Column(modifier = Modifier.padding(10.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                Text(text = "Overview", style = MaterialTheme.typography.labelLarge)
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .horizontalScroll(rememberScrollState()),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    AssistChip(
                        onClick = {},
                        label = { Text(if (isConnected) "Connected" else "Offline") },
                        leadingIcon = {
                            Icon(
                                imageVector = if (isConnected) Icons.Default.CheckCircle else Icons.Default.Close,
                                contentDescription = null
                            )
                        }
                    )
                    AssistChip(
                        onClick = {},
                        label = { Text(typeLabel) },
                        leadingIcon = { Icon(Icons.Default.Code, contentDescription = null) }
                    )
                    AssistChip(
                        onClick = {},
                        label = { Text(deviceLabel) },
                        leadingIcon = { Icon(Icons.Default.PhoneAndroid, contentDescription = null) }
                    )
                    AssistChip(
                        onClick = {},
                        label = { Text("Mode ${mode.uppercase()}") },
                        leadingIcon = { Icon(Icons.Default.CompareArrows, contentDescription = null) }
                    )
                }
                Text(text = "Status: $statusLine", style = MaterialTheme.typography.labelSmall)
                Text(
                    text = "Project: $projectName",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                status?.lastError?.takeIf { it.isNotBlank() }?.let {
                    Text(
                        text = it,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.error
                    )
                }
                if (!sanitizedRunnerMessage.isNullOrBlank()) {
                    Text(
                        text = sanitizedRunnerMessage,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.error
                    )
                }
            }
        }

        Surface(tonalElevation = 1.dp, modifier = Modifier.fillMaxWidth()) {
            Column(modifier = Modifier.padding(10.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text(text = "Project", style = MaterialTheme.typography.labelLarge)
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
                    FilledTonalButton(onClick = onDetect, enabled = isConnected) {
                        Icon(Icons.Default.Search, contentDescription = null)
                        Spacer(modifier = Modifier.width(6.dp))
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
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
                    Icon(Icons.Default.FolderOpen, contentDescription = null, tint = MaterialTheme.colorScheme.primary)
                    Text(text = projectName, style = MaterialTheme.typography.bodySmall)
                }
                if (workingDir.isNotBlank()) {
                    Text(text = "Working dir: $workingDir", style = MaterialTheme.typography.labelSmall)
                }
            }
        }

        Surface(tonalElevation = 1.dp, modifier = Modifier.fillMaxWidth()) {
            Column(modifier = Modifier.padding(10.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text(text = "Device", style = MaterialTheme.typography.labelLarge)
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
                }
                Text(
                    text = selectedDevice?.let { "Target: ${it.model} (${it.id})" } ?: "No device selected",
                    style = MaterialTheme.typography.labelSmall
                )
            }
        }

        Surface(tonalElevation = 1.dp, modifier = Modifier.fillMaxWidth()) {
            Column(modifier = Modifier.padding(10.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text(text = "Controls", style = MaterialTheme.typography.labelLarge)
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .horizontalScroll(rememberScrollState()),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    FilledTonalButton(onClick = onStart, enabled = canStart) {
                        Icon(Icons.Default.PlayArrow, contentDescription = null)
                        Spacer(modifier = Modifier.width(6.dp))
                        Text("Start")
                    }
                    OutlinedButton(onClick = onStop, enabled = status != null) {
                        Icon(Icons.Default.Stop, contentDescription = null)
                        Spacer(modifier = Modifier.width(6.dp))
                        Text("Stop")
                    }
                    OutlinedButton(onClick = onOpenRunner, enabled = canOpenRunner) {
                        Icon(Icons.Default.OpenInNew, contentDescription = null)
                        Spacer(modifier = Modifier.width(6.dp))
                        Text("Open App")
                    }
                }
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .horizontalScroll(rememberScrollState()),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    if (isReactNative) {
                        AssistChip(
                            onClick = onReloadJs,
                            label = { Text("Reload JS") },
                            leadingIcon = { Icon(Icons.Default.Refresh, contentDescription = null) },
                            enabled = status?.appRunning == true
                        )
                        AssistChip(
                            onClick = onDevMenu,
                            label = { Text("Dev Menu") },
                            leadingIcon = { Icon(Icons.Default.Settings, contentDescription = null) },
                            enabled = status?.appRunning == true
                        )
                        AssistChip(
                            onClick = onSetDebugHost,
                            label = { Text("Debug Host") },
                            leadingIcon = { Icon(Icons.Default.CompareArrows, contentDescription = null) },
                            enabled = status?.appRunning == true && !packageName.isNullOrBlank()
                        )
                    }
                    if (isFlutter) {
                        AssistChip(
                            onClick = onReload,
                            label = { Text("Hot Reload") },
                            leadingIcon = { Icon(Icons.Default.Refresh, contentDescription = null) },
                            enabled = status?.flutterRunning == true
                        )
                        AssistChip(
                            onClick = onRestart,
                            label = { Text("Hot Restart") },
                            leadingIcon = { Icon(Icons.Default.Refresh, contentDescription = null) },
                            enabled = status?.flutterRunning == true
                        )
                    }
                }
            }
        }

        Surface(tonalElevation = 2.dp, modifier = Modifier.fillMaxWidth()) {
            Column(modifier = Modifier.padding(10.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(text = "Advanced", style = MaterialTheme.typography.labelLarge)
                    FilterChip(
                        selected = showAdvanced,
                        onClick = { showAdvanced = !showAdvanced },
                        label = { Text(if (showAdvanced) "Shown" else "Hidden") },
                        leadingIcon = { Icon(Icons.Default.Settings, contentDescription = null) }
                    )
                }
                AnimatedVisibility(visible = showAdvanced) {
                    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        if (isReactNative) {
                            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
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
                                style = MaterialTheme.typography.labelSmall
                            )
                            if (packageName != null) {
                                Text(text = "Package: $packageName", style = MaterialTheme.typography.labelSmall)
                            } else {
                                Text(
                                    text = "Package not detected (set Debug host manually in Dev Menu).",
                                    style = MaterialTheme.typography.labelSmall
                                )
                            }
                            if (mode == "lan" && serverHost.isNotBlank()) {
                                Text(
                                    text = "Set Debug server host to $serverHost:$metroPort in Dev Menu.",
                                    style = MaterialTheme.typography.labelSmall
                                )
                            }
                        } else {
                            Text(text = "Advanced network options are available for React Native.", style = MaterialTheme.typography.labelSmall)
                        }
                    }
                }
            }
        }

        val logLines = remember(logs) {
            val combined = mutableListOf<String>()
            logs?.metro?.forEach { combined.add("[metro] $it") }
            logs?.app?.forEach { combined.add("[app] $it") }
            logs?.flutter?.forEach { combined.add("[flutter] $it") }
            if (combined.size > 120) combined.takeLast(120) else combined
        }
        if (logLines.isNotEmpty()) {
            Surface(tonalElevation = 1.dp, modifier = Modifier.fillMaxWidth()) {
                Column(modifier = Modifier.padding(10.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    Text(text = "Logs", style = MaterialTheme.typography.labelLarge)
                    Box(modifier = Modifier.heightIn(min = 120.dp, max = 220.dp)) {
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
}

@Composable
private fun SystemStatusSection(
    connectionStatus: String,
    host: String,
    port: String,
    workingDir: String,
    adminStatus: AdminStatus?,
    deviceInfo: AdminDeviceInfo?,
    selectedDeviceId: String?,
    onSelectDevice: (String?) -> Unit,
    onRefresh: () -> Unit,
    onRestartBackend: () -> Unit,
    onRestartWeb: () -> Unit,
    onBuildViewer: () -> Unit,
    onBuildLive: () -> Unit,
    onInstallViewer: () -> Unit,
    onInstallLive: () -> Unit,
    message: String?
) {
    val pm2List = adminStatus?.pm2 ?: emptyList()
    val backend = pm2List.firstOrNull { it.name == "codex-backend" }
    val web = pm2List.firstOrNull { it.name == "codex-web" }
    val devices = adminStatus?.devices ?: emptyList()
    val viewerInfo = deviceInfo?.viewer
    val liveInfo = deviceInfo?.live

    Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
        Row(
            modifier = Modifier.horizontalScroll(rememberScrollState()),
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            AssistChip(
                onClick = onRefresh,
                label = { Text("Refresh") },
                leadingIcon = { Icon(Icons.Default.Refresh, contentDescription = null) }
            )
            AssistChip(
                onClick = onRestartBackend,
                label = { Text("Restart Backend") },
                leadingIcon = { Icon(Icons.Default.Refresh, contentDescription = null) }
            )
            AssistChip(
                onClick = onRestartWeb,
                label = { Text("Restart Web") },
                leadingIcon = { Icon(Icons.Default.Refresh, contentDescription = null) }
            )
        }

        Surface(tonalElevation = 1.dp, modifier = Modifier.fillMaxWidth()) {
            Column(modifier = Modifier.padding(10.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                Text(text = "Connection", style = MaterialTheme.typography.labelLarge)
                Text(text = "Status: $connectionStatus", style = MaterialTheme.typography.labelSmall)
                Text(text = "Server: ${host.ifBlank { "-" }}:${port.ifBlank { "-" }}", style = MaterialTheme.typography.labelSmall)
                Text(text = "Working dir: ${workingDir.ifBlank { "-" }}", style = MaterialTheme.typography.labelSmall)
                Text(text = "Repo: ${adminStatus?.repoRoot ?: "-"}", style = MaterialTheme.typography.labelSmall)
                Text(text = "Ports: backend ${adminStatus?.backendPort ?: "-"}, web ${adminStatus?.settingsPort ?: "-"}", style = MaterialTheme.typography.labelSmall)
            }
        }

        Surface(tonalElevation = 1.dp, modifier = Modifier.fillMaxWidth()) {
            Column(modifier = Modifier.padding(10.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                Text(text = "Services", style = MaterialTheme.typography.labelLarge)
                ServiceStatusRow(label = "codex-backend", status = backend?.status)
                ServiceStatusRow(label = "codex-web", status = web?.status)
            }
        }

        Surface(tonalElevation = 1.dp, modifier = Modifier.fillMaxWidth()) {
            Column(modifier = Modifier.padding(10.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text(text = "Device", style = MaterialTheme.typography.labelLarge)
                DeviceDropdown(
                    devices = devices,
                    selectedDeviceId = selectedDeviceId,
                    onSelectDevice = onSelectDevice,
                    modifier = Modifier.fillMaxWidth()
                )
                Text(text = "Model: ${deviceInfo?.model ?: deviceInfo?.device?.model ?: "-"}", style = MaterialTheme.typography.labelSmall)
                val screenStatus = when (deviceInfo?.screenOn) {
                    true -> "On"
                    false -> "Off"
                    null -> "Unknown"
                }
                val awakeStatus = when (deviceInfo?.awake) {
                    true -> "Awake"
                    false -> "Asleep"
                    null -> "Unknown"
                }
                Text(text = "Screen: $screenStatus · $awakeStatus", style = MaterialTheme.typography.labelSmall)
            }
        }

        Surface(tonalElevation = 1.dp, modifier = Modifier.fillMaxWidth()) {
            Column(modifier = Modifier.padding(10.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text(text = "APK Status", style = MaterialTheme.typography.labelLarge)
                Text(text = "Viewer: ${formatPackage(viewerInfo)}", style = MaterialTheme.typography.labelSmall)
                Text(text = "Live: ${formatPackage(liveInfo)}", style = MaterialTheme.typography.labelSmall)
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .horizontalScroll(rememberScrollState()),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    AssistChip(onClick = onBuildViewer, label = { Text("Build Viewer") }, leadingIcon = {
                        Icon(Icons.Default.Build, contentDescription = null)
                    })
                    AssistChip(onClick = onBuildLive, label = { Text("Build Live") }, leadingIcon = {
                        Icon(Icons.Default.Build, contentDescription = null)
                    })
                    AssistChip(
                        onClick = onInstallViewer,
                        enabled = selectedDeviceId != null,
                        label = { Text("Install Viewer") },
                        leadingIcon = { Icon(Icons.Default.Download, contentDescription = null) }
                    )
                    AssistChip(
                        onClick = onInstallLive,
                        enabled = selectedDeviceId != null,
                        label = { Text("Install Live") },
                        leadingIcon = { Icon(Icons.Default.Download, contentDescription = null) }
                    )
                }
            }
        }

        if (!message.isNullOrBlank()) {
            Text(text = message, color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.labelSmall)
        }
    }
}

@Composable
private fun ServiceStatusRow(label: String, status: String?) {
    val color = when (status?.lowercase()) {
        "online" -> Color(0xFF16A34A)
        "stopped", "errored", "stopping" -> Color(0xFFDC2626)
        "launching", "one-launch-status", "waiting" -> Color(0xFFF59E0B)
        else -> Color(0xFF94A3B8)
    }
    Row(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
        Box(
            modifier = Modifier
                .height(10.dp)
                .width(10.dp)
                .background(color, CircleShape)
        )
        Text(text = label, style = MaterialTheme.typography.bodySmall)
        Text(text = status ?: "unknown", style = MaterialTheme.typography.bodySmall)
    }
}

private fun formatPackage(info: AdminPackageInfo?): String {
    if (info == null) return "Unknown"
    if (!info.installed) return "Not installed"
    val version = listOfNotNull(info.versionName, info.versionCode?.let { "($it)" })
        .joinToString(" ")
    return if (version.isBlank()) "Installed" else "Installed $version"
}

@Composable
private fun DropdownAnchorField(
    value: String,
    label: String,
    expanded: Boolean,
    modifier: Modifier = Modifier
) {
    OutlinedTextField(
        value = value,
        onValueChange = {},
        readOnly = true,
        label = { Text(label) },
        trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded = expanded) },
        modifier = modifier
    )
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
    ExposedDropdownMenuBox(
        expanded = expanded,
        onExpandedChange = { expanded = !expanded }
    ) {
        DropdownAnchorField(
            value = label,
            label = "Project",
            expanded = expanded,
            modifier = Modifier
                .fillMaxWidth()
                .menuAnchor()
        )
        ExposedDropdownMenu(
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
    ExposedDropdownMenuBox(
        expanded = expanded,
        onExpandedChange = { expanded = !expanded },
        modifier = modifier
    ) {
        DropdownAnchorField(
            value = label,
            label = "Scan depth",
            expanded = expanded,
            modifier = Modifier
                .fillMaxWidth()
                .menuAnchor()
        )
        ExposedDropdownMenu(
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
    ExposedDropdownMenuBox(
        expanded = expanded,
        onExpandedChange = { expanded = !expanded },
        modifier = modifier
    ) {
        DropdownAnchorField(
            value = label,
            label = "Project",
            expanded = expanded,
            modifier = Modifier
                .fillMaxWidth()
                .menuAnchor()
        )
        ExposedDropdownMenu(
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
    ExposedDropdownMenuBox(
        expanded = expanded,
        onExpandedChange = { expanded = !expanded },
        modifier = modifier
    ) {
        DropdownAnchorField(
            value = label,
            label = "Mode",
            expanded = expanded,
            modifier = Modifier
                .fillMaxWidth()
                .menuAnchor()
        )
        ExposedDropdownMenu(
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
    ExposedDropdownMenuBox(
        expanded = expanded,
        onExpandedChange = { expanded = !expanded },
        modifier = modifier
    ) {
        DropdownAnchorField(
            value = label,
            label = "Device",
            expanded = expanded,
            modifier = Modifier
                .fillMaxWidth()
                .menuAnchor()
        )
        ExposedDropdownMenu(
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
private fun WorkspaceStatusBar(
    serverName: String,
    host: String,
    port: String,
    workingDir: String,
    isConnected: Boolean,
    codexRunning: Boolean,
    runnerStatus: RunnerStatus?
) {
    val connectionLabel = if (isConnected) "Connected" else "Disconnected"
    val codexLabel = if (codexRunning) "Codex: running" else "Codex: stopped"
    val runnerLabel = runnerStatus?.projectType?.let { "Hotload: $it" } ?: "Hotload: -"
    val workdirLabel = workingDir.ifBlank { "(not set)" }
    Surface(tonalElevation = 1.dp, modifier = Modifier.fillMaxWidth()) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp, vertical = 8.dp),
            verticalArrangement = Arrangement.spacedBy(4.dp)
        ) {
            Text(
                text = "Workspace: $serverName • $host:$port",
                style = MaterialTheme.typography.bodySmall
            )
            Text(
                text = "Dir: $workdirLabel",
                style = MaterialTheme.typography.bodySmall
            )
            Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                Text(text = connectionLabel, style = MaterialTheme.typography.bodySmall)
                Text(text = codexLabel, style = MaterialTheme.typography.bodySmall)
                Text(text = runnerLabel, style = MaterialTheme.typography.bodySmall)
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
    autoConnect: Boolean,
    onAutoConnectChange: (Boolean) -> Unit,
    autoStartCodex: Boolean,
    onAutoStartCodexChange: (Boolean) -> Unit,
    codexRunning: Boolean,
    onToggleCodex: () -> Unit,
    codexMessage: String?,
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
    Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
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
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .horizontalScroll(rememberScrollState()),
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            AssistChip(
                onClick = {},
                label = { Text(if (isConnected) "Connected" else "Disconnected") },
                leadingIcon = {
                    Icon(
                        imageVector = if (isConnected) Icons.Default.CheckCircle else Icons.Default.Close,
                        contentDescription = null
                    )
                }
            )
            AssistChip(
                onClick = {},
                label = { Text(if (codexRunning) "Codex running" else "Codex stopped") },
                leadingIcon = {
                    Icon(
                        imageVector = if (codexRunning) Icons.Default.Code else Icons.Default.Stop,
                        contentDescription = null
                    )
                }
            )
            if (workingDir.isNotBlank()) {
                AssistChip(
                    onClick = onOpenWorkingDir,
                    label = { Text("Dir") },
                    leadingIcon = { Icon(Icons.Default.FolderOpen, contentDescription = null) }
                )
            }
        }
        if (!codexMessage.isNullOrBlank()) {
            Text(
                text = codexMessage,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.error
            )
        }
        ListItem(
            headlineContent = { Text("Auto-connect") },
            supportingContent = { Text("Reconnect automatically when the app starts") },
            trailingContent = {
                Switch(checked = autoConnect, onCheckedChange = onAutoConnectChange)
            }
        )
        ListItem(
            headlineContent = { Text("Auto-start Codex") },
            supportingContent = { Text("Start Codex automatically after connect") },
            trailingContent = {
                Switch(checked = autoStartCodex, onCheckedChange = onAutoStartCodexChange)
            }
        )

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
                FilledTonalButton(
                    onClick = onToggleConnection,
                    enabled = ip.isNotBlank() || isConnected
                ) {
                    Icon(
                        imageVector = if (isConnected) Icons.Default.Close else Icons.Default.Refresh,
                        contentDescription = null
                    )
                    Spacer(modifier = Modifier.width(6.dp))
                    Text(if (isConnected) "Disconnect" else "Connect")
                }
                OutlinedButton(onClick = onToggleCodex, enabled = isConnected) {
                    Icon(
                        imageVector = if (codexRunning) Icons.Default.Stop else Icons.Default.Code,
                        contentDescription = null
                    )
                    Spacer(modifier = Modifier.width(6.dp))
                    Text(if (codexRunning) "Stop Codex" else "Start Codex")
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
                FilledTonalButton(
                    onClick = onToggleConnection,
                    enabled = ip.isNotBlank() || isConnected
                ) {
                    Icon(
                        imageVector = if (isConnected) Icons.Default.Close else Icons.Default.Refresh,
                        contentDescription = null
                    )
                    Spacer(modifier = Modifier.width(6.dp))
                    Text(if (isConnected) "Disconnect" else "Connect")
                }
                OutlinedButton(onClick = onToggleCodex, enabled = isConnected) {
                    Icon(
                        imageVector = if (codexRunning) Icons.Default.Stop else Icons.Default.Code,
                        contentDescription = null
                    )
                    Spacer(modifier = Modifier.width(6.dp))
                    Text(if (codexRunning) "Stop Codex" else "Start Codex")
                }
                IconButton(onClick = onOpenWorkingDir) {
                    Icon(Icons.Default.Settings, contentDescription = "Working directory")
                }
            }
        }
    }
}

@Composable
private fun WorkspaceScreen(
    ip: String,
    onIpChange: (String) -> Unit,
    port: String,
    onPortChange: (String) -> Unit,
    isConnected: Boolean,
    connectionStatus: String,
    onToggleConnection: () -> Unit,
    autoConnect: Boolean,
    onAutoConnectChange: (Boolean) -> Unit,
    autoStartCodex: Boolean,
    onAutoStartCodexChange: (Boolean) -> Unit,
    codexRunning: Boolean,
    onToggleCodex: () -> Unit,
    codexMessage: String?,
    servers: List<ServerProfile>,
    selectedServerId: String?,
    onSelectServer: (ServerProfile?) -> Unit,
    onManageServers: () -> Unit,
    onAddServer: () -> Unit,
    onOpenWorkingDir: () -> Unit,
    workingDir: String,
    sessionWorkingDir: String?,
    onUseServerDefault: () -> Unit,
    onUseSessionDir: () -> Unit,
    runnerStatus: RunnerStatus?
) {
    val serverDefault = servers.firstOrNull { it.id == selectedServerId }?.workingDir?.ifBlank { null }
    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(12.dp)
            .verticalScroll(rememberScrollState()),
        verticalArrangement = Arrangement.spacedBy(10.dp)
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(text = "Workspace", style = MaterialTheme.typography.labelLarge)
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                AssistChip(
                    onClick = {},
                    label = { Text(if (isConnected) "Connected" else "Offline") },
                    leadingIcon = {
                        Icon(
                            imageVector = if (isConnected) Icons.Default.CheckCircle else Icons.Default.Close,
                            contentDescription = null
                        )
                    }
                )
                AssistChip(
                    onClick = {},
                    label = { Text(if (codexRunning) "Codex" else "Codex off") },
                    leadingIcon = { Icon(Icons.Default.Code, contentDescription = null) }
                )
            }
        }
        Text(
            text = "Server: ${servers.firstOrNull { it.id == selectedServerId }?.name ?: "Custom"} • $ip:$port",
            style = MaterialTheme.typography.labelSmall
        )

        Surface(tonalElevation = 1.dp, modifier = Modifier.fillMaxWidth()) {
            Column(
                modifier = Modifier.padding(10.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                Text(text = "Connection", style = MaterialTheme.typography.labelLarge)
                ConnectionSection(
                    ip = ip,
                    onIpChange = onIpChange,
                    port = port,
                    onPortChange = onPortChange,
                    isConnected = isConnected,
                    onToggleConnection = onToggleConnection,
                    autoConnect = autoConnect,
                    onAutoConnectChange = onAutoConnectChange,
                    autoStartCodex = autoStartCodex,
                    onAutoStartCodexChange = onAutoStartCodexChange,
                    codexRunning = codexRunning,
                    onToggleCodex = onToggleCodex,
                    codexMessage = codexMessage,
                    servers = servers,
                    selectedServerId = selectedServerId,
                    onSelectServer = onSelectServer,
                    onManageServers = onManageServers,
                    onAddServer = onAddServer,
                    onOpenWorkingDir = onOpenWorkingDir,
                    workingDir = workingDir,
                    stacked = true
                )
            }
        }

        Surface(tonalElevation = 1.dp, modifier = Modifier.fillMaxWidth()) {
            Column(
                modifier = Modifier.padding(10.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                Text(text = "Working Directory", style = MaterialTheme.typography.labelLarge)
                ListItem(
                    headlineContent = { Text("Current") },
                    supportingContent = { Text(workingDir.ifBlank { "(not set)" }) },
                    leadingContent = { Icon(Icons.Default.FolderOpen, contentDescription = null) }
                )
                if (!sessionWorkingDir.isNullOrBlank()) {
                    ListItem(
                        headlineContent = { Text("Session") },
                        supportingContent = { Text(sessionWorkingDir) },
                        leadingContent = { Icon(Icons.Default.Tag, contentDescription = null) }
                    )
                }
                if (!serverDefault.isNullOrBlank()) {
                    ListItem(
                        headlineContent = { Text("Server default") },
                        supportingContent = { Text(serverDefault) },
                        leadingContent = { Icon(Icons.Default.Home, contentDescription = null) }
                    )
                }
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .horizontalScroll(rememberScrollState()),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    AssistChip(
                        onClick = onOpenWorkingDir,
                        label = { Text("Pick") },
                        leadingIcon = { Icon(Icons.Default.FolderOpen, contentDescription = null) }
                    )
                    AssistChip(
                        onClick = onUseServerDefault,
                        label = { Text("Use Default") },
                        leadingIcon = { Icon(Icons.Default.Home, contentDescription = null) },
                        enabled = !serverDefault.isNullOrBlank()
                    )
                    AssistChip(
                        onClick = onUseSessionDir,
                        label = { Text("Use Session") },
                        leadingIcon = { Icon(Icons.Default.Tag, contentDescription = null) },
                        enabled = !sessionWorkingDir.isNullOrBlank()
                    )
                }
            }
        }

        Surface(tonalElevation = 1.dp, modifier = Modifier.fillMaxWidth()) {
            Column(
                modifier = Modifier.padding(10.dp),
                verticalArrangement = Arrangement.spacedBy(6.dp)
            ) {
                Text(text = "Status", style = MaterialTheme.typography.labelLarge)
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .horizontalScroll(rememberScrollState()),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    AssistChip(
                        onClick = {},
                        label = { Text(connectionStatus) },
                        leadingIcon = {
                            Icon(
                                imageVector = if (isConnected) Icons.Default.CheckCircle else Icons.Default.Close,
                                contentDescription = null
                            )
                        }
                    )
                    AssistChip(
                        onClick = {},
                        label = { Text(if (codexRunning) "Codex running" else "Codex stopped") },
                        leadingIcon = { Icon(Icons.Default.Code, contentDescription = null) }
                    )
                    AssistChip(
                        onClick = {},
                        label = { Text(runnerStatus?.projectType?.let { "Hotload: $it" } ?: "Hotload: -") },
                        leadingIcon = { Icon(Icons.Default.Tag, contentDescription = null) }
                    )
                }
            }
        }
    }
}

@Composable
private fun LivePreviewBox(
    liveFrame: ImageBitmap?,
    isConnected: Boolean,
    livePreviewSize: IntSize,
    onSizeChanged: (IntSize) -> Unit,
    onTap: (Offset) -> Unit,
    onSwipe: (Offset, Offset) -> Unit,
    onLongPress: (Offset) -> Unit,
    modifier: Modifier = Modifier
) {
    val viewConfig = LocalViewConfiguration.current
    Box(
        modifier = modifier
            .background(MaterialTheme.colorScheme.surfaceVariant)
            .onSizeChanged { onSizeChanged(it) }
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
                                onLongPress(start)
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
                        dragDetected && !longPressTriggered -> onSwipe(start, current)
                        !longPressTriggered -> onTap(start)
                    }
                }
            }
    ) {
        if (liveFrame != null) {
            Image(
                bitmap = liveFrame,
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
    ExposedDropdownMenuBox(
        expanded = expanded,
        onExpandedChange = { expanded = !expanded }
    ) {
        DropdownAnchorField(
            value = serverLabel,
            label = "Server",
            expanded = expanded,
            modifier = Modifier
                .fillMaxWidth()
                .menuAnchor()
        )
        ExposedDropdownMenu(
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
    ExposedDropdownMenuBox(
        expanded = expanded,
        onExpandedChange = { expanded = !expanded },
        modifier = modifier
    ) {
        DropdownAnchorField(
            value = label,
            label = "Live FPS",
            expanded = expanded,
            modifier = Modifier
                .fillMaxWidth()
                .menuAnchor()
        )
        ExposedDropdownMenu(
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
    ExposedDropdownMenuBox(
        expanded = expanded,
        onExpandedChange = { expanded = !expanded },
        modifier = modifier
    ) {
        DropdownAnchorField(
            value = label,
            label = "Image Format",
            expanded = expanded,
            modifier = Modifier
                .fillMaxWidth()
                .menuAnchor()
        )
        ExposedDropdownMenu(
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
    onAutoFitChanged: (Boolean) -> Unit,
    onOpenLivePhone: (() -> Unit)? = null,
    onOpenRunner: (() -> Unit)? = null,
    onReloadAndOpenRunner: (() -> Unit)? = null,
    onFullscreen: (() -> Unit)? = null
) {
    Surface(
        tonalElevation = 1.dp,
        shape = MaterialTheme.shapes.large,
        modifier = modifier.fillMaxWidth()
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(10.dp),
            verticalArrangement = Arrangement.spacedBy(6.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(text = "Codex", style = MaterialTheme.typography.labelLarge)
                Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                    if (onOpenLivePhone != null) {
                        IconButton(onClick = onOpenLivePhone) {
                            Icon(Icons.Default.PhoneAndroid, contentDescription = "Live Phone")
                        }
                    }
                    if (onOpenRunner != null) {
                        IconButton(onClick = onOpenRunner) {
                            Icon(Icons.Default.OpenInNew, contentDescription = "Open App")
                        }
                    }
                    if (onReloadAndOpenRunner != null) {
                        IconButton(onClick = onReloadAndOpenRunner) {
                            Icon(Icons.Default.Refresh, contentDescription = "Reload JS + Open App")
                        }
                    }
                    if (onFullscreen != null) {
                        IconButton(onClick = onFullscreen) {
                            Icon(Icons.Default.Fullscreen, contentDescription = "Fullscreen")
                        }
                    }
                    IconButton(onClick = {
                        terminalController.decreaseFontSize()
                        onAutoFitChanged(terminalController.isAutoFitEnabled())
                    }) {
                        Icon(Icons.Default.ZoomOut, contentDescription = "Smaller text")
                    }
                    IconButton(onClick = {
                        terminalController.increaseFontSize()
                        onAutoFitChanged(terminalController.isAutoFitEnabled())
                    }) {
                        Icon(Icons.Default.ZoomIn, contentDescription = "Larger text")
                    }
                    IconButton(onClick = {
                        val next = !autoFit
                        terminalController.setAutoFit(next)
                        onAutoFitChanged(next)
                    }) {
                        Icon(Icons.Default.FitScreen, contentDescription = "Fit to width")
                    }
                }
            }
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
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .horizontalScroll(rememberScrollState()),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                TERMINAL_KEYS.forEach { key ->
                    AssistChip(
                        onClick = { terminalController.sendKeySequence(key.sequence) },
                        label = { Text(key.label) },
                        leadingIcon = {
                            when (key.label) {
                                "↑" -> Icon(Icons.Default.ArrowUpward, contentDescription = null)
                                "↓" -> Icon(Icons.Default.KeyboardArrowDown, contentDescription = null)
                                "←" -> Icon(Icons.Default.KeyboardArrowLeft, contentDescription = null)
                                "→" -> Icon(Icons.Default.KeyboardArrowRight, contentDescription = null)
                            }
                        }
                    )
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
    Surface(
        tonalElevation = 1.dp,
        shape = MaterialTheme.shapes.large,
        modifier = Modifier.fillMaxWidth()
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(10.dp),
            verticalArrangement = Arrangement.spacedBy(6.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(text = "Command", style = MaterialTheme.typography.labelLarge)
                if (sttStatus != "Idle") {
                    AssistChip(
                        onClick = {},
                        label = { Text(sttStatus) },
                        leadingIcon = { Icon(Icons.Default.Mic, contentDescription = null) }
                    )
                }
            }
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .horizontalScroll(rememberScrollState()),
                horizontalArrangement = Arrangement.spacedBy(12.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                ActionIconButton(
                    label = if (isRecordingManual) "Stop" else "Record",
                    icon = if (isRecordingManual) Icons.Default.Stop else Icons.Default.FiberManualRecord,
                    enabled = !isRecordingAuto,
                    showLabel = false,
                    onClick = onToggleManualRecording
                )
                ActionIconButton(
                    label = if (isRecordingAuto) "Stop" else "Mic",
                    icon = if (isRecordingAuto) Icons.Default.Stop else Icons.Default.Mic,
                    enabled = !isRecordingManual,
                    showLabel = false,
                    onClick = onToggleAutoRecording
                )
                OutlinedTextField(
                    value = command,
                    onValueChange = onCommandChange,
                    label = { Text("Type or speak…") },
                    singleLine = true,
                    modifier = Modifier.weight(1f)
                )
                FilledTonalIconButton(
                    onClick = onSend,
                    enabled = isConnected && command.isNotBlank()
                ) {
                    Icon(Icons.Default.Send, contentDescription = "Send")
                }
            }
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

private fun selectPreferredDevice(devices: List<RunnerDevice>): RunnerDevice? {
    if (devices.isEmpty()) return null
    val model = Build.MODEL?.lowercase()?.replace(" ", "")
    if (!model.isNullOrBlank()) {
        val match = devices.firstOrNull { device ->
            val deviceModel = device.model.lowercase().replace(" ", "")
            deviceModel == model || deviceModel.contains(model)
        }
        if (match != null) return match
    }
    return devices.first()
}

private fun File.safeDelete() {
    try {
        delete()
    } catch (_: Exception) {
        // ignore
    }
}

private fun formatBytes(size: Long?): String {
    if (size == null) return "-"
    if (size < 1024L) return "${size} B"
    val units = arrayOf("KB", "MB", "GB", "TB")
    var value = size.toDouble()
    var index = 0
    while (value >= 1024 && index < units.lastIndex) {
        value /= 1024
        index += 1
    }
    return String.format(Locale.US, "%.1f %s", value, units[index])
}

private fun formatEpoch(millis: Long?): String? {
    if (millis == null) return null
    return try {
        val formatter = SimpleDateFormat("yyyy-MM-dd HH:mm", Locale.getDefault())
        formatter.format(Date(millis))
    } catch (_: Exception) {
        null
    }
}

private fun relativeToBase(base: String?, target: String): String? {
    if (base.isNullOrBlank()) return null
    return try {
        File(target).relativeToOrNull(File(base))?.path
    } catch (_: Exception) {
        null
    }
}

@Composable
private fun FileManagerScreen(
    host: String,
    port: String,
    workingDir: String,
    isConnected: Boolean,
    viewModel: CodexViewModel,
    store: FileManagerStore,
    gitStatus: GitStatus?,
    onRefreshGit: () -> Unit,
    onUseWorkingDir: (String) -> Unit
) {
    var browsePath by remember { mutableStateOf(workingDir) }
    var listing by remember { mutableStateOf<FileList?>(null) }
    var loading by remember { mutableStateOf(false) }
    var error by remember { mutableStateOf<String?>(null) }
    var status by remember { mutableStateOf<String?>(null) }
    var refreshKey by remember { mutableStateOf(0) }
    var showCreateDialog by remember { mutableStateOf(false) }
    var showCreateFileDialog by remember { mutableStateOf(false) }
    var showRenameDialog by remember { mutableStateOf(false) }
    var showDeleteDialog by remember { mutableStateOf(false) }
    var selectedPath by remember { mutableStateOf<String?>(null) }
    var searchQuery by remember { mutableStateOf("") }
    var filterMode by remember { mutableStateOf("all") }
    var sortMode by remember { mutableStateOf("name") }
    var showHidden by remember { mutableStateOf(false) }
    var selectionMode by remember { mutableStateOf(false) }
    var selectedItems by remember { mutableStateOf(setOf<String>()) }
    var favoritesOnly by remember { mutableStateOf(false) }
    var previewFile by remember { mutableStateOf<FileRead?>(null) }
    var previewText by remember { mutableStateOf("") }
    var previewDirty by remember { mutableStateOf(false) }
    var showCommandPalette by remember { mutableStateOf(false) }
    var showProjectSearch by remember { mutableStateOf(false) }
    var showProjectReplace by remember { mutableStateOf(false) }
    var projectQuery by remember { mutableStateOf("") }
    var projectReplace by remember { mutableStateOf("") }
    var projectGlob by remember { mutableStateOf("") }
    var projectCaseSensitive by remember { mutableStateOf(false) }
    var projectRegex by remember { mutableStateOf(false) }
    var searchResults by remember { mutableStateOf<List<SearchResult>>(emptyList()) }
    var searchLoading by remember { mutableStateOf(false) }
    var searchMessage by remember { mutableStateOf<String?>(null) }
    var replaceMessage by remember { mutableStateOf<String?>(null) }
    var showOverflow by remember { mutableStateOf(false) }
    val openTabs = remember { mutableStateListOf<String>() }
    val editorBuffers = remember { mutableStateMapOf<String, String>() }
    val editorDirty = remember { mutableStateMapOf<String, Boolean>() }
    var activeEditorPath by remember { mutableStateOf<String?>(null) }
    var diffContent by remember { mutableStateOf<String?>(null) }
    var pendingDownload by remember { mutableStateOf<Pair<String, ByteArray>?>(null) }
    var favorites by remember { mutableStateOf(store.loadFavorites()) }
    var recents by remember { mutableStateOf(store.loadRecents()) }
    val scope = rememberCoroutineScope()
    val hostReady = isConnected && host.isNotBlank() && port.isNotBlank()
    val context = LocalContext.current
    val clipboard = LocalClipboardManager.current
    val configuration = LocalConfiguration.current
    val isLandscape = configuration.orientation == Configuration.ORIENTATION_LANDSCAPE
    val showToolbarLabels = configuration.screenWidthDp >= 480
    val gitMap = remember(gitStatus) {
        val map = mutableMapOf<String, String>()
        val root = gitStatus?.root
        gitStatus?.changes?.forEach { line ->
            if (line.length < 3) return@forEach
            val statusCode = line.substring(0, 2).trim()
            var path = line.substring(3).trim()
            if (path.contains("->")) {
                path = path.substringAfter("->").trim()
            }
            val abs = if (root != null && !path.startsWith("/")) {
                File(root, path).absolutePath
            } else {
                path
            }
            map[abs] = statusCode
        }
        map
    }

    val uploadLauncher = rememberLauncherForActivityResult(ActivityResultContracts.GetContent()) { uri ->
        if (uri == null) return@rememberLauncherForActivityResult
        scope.launch {
            val bytes = context.contentResolver.openInputStream(uri)?.readBytes()
            if (bytes == null) {
                status = "Upload failed: could not read file"
                return@launch
            }
            val name = uri.lastPathSegment?.substringAfterLast('/') ?: "upload.bin"
            val result = viewModel.uploadFile(host, port, browsePath, name, bytes)
            status = result.fold(
                onSuccess = { "Uploaded: $name" },
                onFailure = { "Upload failed: ${it.message}" }
            )
            refreshKey += 1
        }
    }

    fun openEditorFile(path: String) {
        scope.launch {
            val result = viewModel.readFile(host, port, path)
            if (result.isSuccess) {
                val file = result.getOrNull()
                if (file != null) {
                    if (!openTabs.contains(path)) {
                        openTabs.add(path)
                    }
                    editorBuffers[path] = file.text
                    editorDirty[path] = false
                    activeEditorPath = path
                    previewFile = file
                    previewText = file.text
                    previewDirty = false
                    store.addRecent(path)
                    recents = store.loadRecents()
                }
            } else {
                status = "Read failed: ${result.exceptionOrNull()?.message}"
            }
        }
    }

    fun closeEditorTab(path: String) {
        openTabs.remove(path)
        editorBuffers.remove(path)
        editorDirty.remove(path)
        if (activeEditorPath == path) {
            activeEditorPath = openTabs.lastOrNull()
        }
    }

    fun saveActiveEditor() {
        val target = activeEditorPath ?: return
        val content = editorBuffers[target] ?: return
        scope.launch {
            val result = viewModel.writeFile(host, port, target, content)
            status = result.fold(
                onSuccess = {
                    editorDirty[target] = false
                    "Saved: $target"
                },
                onFailure = { "Save failed: ${it.message}" }
            )
        }
    }

    fun deleteSelectedItems() {
        if (selectedItems.isEmpty()) return
        scope.launch {
            selectedItems.forEach { path ->
                viewModel.deletePath(host, port, path, recursive = true)
            }
            status = "Deleted ${selectedItems.size} items"
            selectedItems = emptySet()
            refreshKey += 1
        }
    }
    val downloadLauncher = rememberLauncherForActivityResult(ActivityResultContracts.CreateDocument("*/*")) { uri ->
        val payload = pendingDownload ?: return@rememberLauncherForActivityResult
        if (uri == null) return@rememberLauncherForActivityResult
        try {
            context.contentResolver.openOutputStream(uri)?.use { it.write(payload.second) }
            status = "Saved: ${payload.first}"
        } catch (e: Exception) {
            status = "Save failed: ${e.message}"
        } finally {
            pendingDownload = null
        }
    }

    fun downloadSelected() {
        val target = selectedPath ?: return
        scope.launch {
            val result = viewModel.downloadFile(host, port, target)
            if (result.isSuccess) {
                val filename = File(target).name.ifBlank { "download.bin" }
                pendingDownload = filename to result.getOrThrow()
                downloadLauncher.launch(filename)
            } else {
                status = "Download failed: ${result.exceptionOrNull()?.message}"
            }
        }
    }

    LaunchedEffect(workingDir) {
        if (workingDir.isNotBlank()) {
            browsePath = workingDir
        }
    }

    LaunchedEffect(browsePath, host, port, refreshKey, showHidden) {
        if (!hostReady) {
            listing = null
            error = "Set host and port to browse files."
            loading = false
            return@LaunchedEffect
        }
        delay(200)
        loading = true
        val result = viewModel.fetchFileList(host, port, browsePath.trim(), showHidden)
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
    val entries = listing?.entries.orEmpty()
    val filtered = entries.filter { entry ->
        val matchesQuery = searchQuery.isBlank() || entry.name.contains(searchQuery, ignoreCase = true)
        val matchesFilter = when (filterMode) {
            "files" -> !entry.isDir
            "dirs" -> entry.isDir
            else -> true
        }
        val matchesFavorites = !favoritesOnly || favorites.contains(entry.path)
        matchesQuery && matchesFilter && matchesFavorites
    }
    val sorted = filtered.sortedWith { a, b ->
        val dirOrder = if (a.isDir == b.isDir) 0 else if (a.isDir) -1 else 1
        if (dirOrder != 0) return@sortedWith dirOrder
        when (sortMode) {
            "mtime" -> (b.mtime ?: 0L).compareTo(a.mtime ?: 0L)
            "size" -> (b.size ?: 0L).compareTo(a.size ?: 0L)
            else -> a.name.lowercase().compareTo(b.name.lowercase())
        }
    }

    Box(modifier = Modifier.fillMaxSize()) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .verticalScroll(rememberScrollState())
                .padding(bottom = 76.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            val activePath = activeEditorPath
            val hasDirty = activePath != null && editorDirty[activePath] == true
            Surface(
                tonalElevation = 1.dp,
                shape = MaterialTheme.shapes.large,
                modifier = Modifier.fillMaxWidth()
            ) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(10.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Icon(
                            imageVector = Icons.Default.FolderOpen,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                        OutlinedTextField(
                            value = browsePath,
                            onValueChange = { browsePath = it },
                            label = { Text("Path") },
                            singleLine = true,
                            modifier = Modifier.weight(1f)
                        )
                        FilledTonalIconButton(onClick = { refreshKey += 1 }, enabled = hostReady) {
                            Icon(Icons.Default.Refresh, contentDescription = "Go")
                        }
                    }
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .horizontalScroll(rememberScrollState()),
                        horizontalArrangement = Arrangement.spacedBy(12.dp)
                    ) {
                        ActionIconButton("Command", Icons.Default.Code, enabled = hostReady, showLabel = showToolbarLabels) {
                            showCommandPalette = true
                        }
                        ActionIconButton("Find", Icons.Default.Search, enabled = hostReady, showLabel = showToolbarLabels) {
                            showProjectSearch = true
                        }
                        ActionIconButton("Replace", Icons.Default.FindReplace, enabled = hostReady, showLabel = showToolbarLabels) {
                            showProjectReplace = true
                        }
                        ActionIconButton("Save", Icons.Default.Save, enabled = hasDirty, showLabel = showToolbarLabels) {
                            saveActiveEditor()
                        }
                        ActionIconButton("Upload", Icons.Default.UploadFile, enabled = hostReady, showLabel = showToolbarLabels) {
                            uploadLauncher.launch("*/*")
                        }
                        Box {
                            IconButton(onClick = { showOverflow = true }) {
                                Icon(Icons.Default.MoreVert, contentDescription = "More actions")
                            }
                            DropdownMenu(expanded = showOverflow, onDismissRequest = { showOverflow = false }) {
                                DropdownMenuItem(
                                    text = { Text("New Folder") },
                                    enabled = hostReady && currentPath.isNotBlank(),
                                    onClick = { showOverflow = false; showCreateDialog = true }
                                )
                                DropdownMenuItem(
                                    text = { Text("New File") },
                                    enabled = hostReady && currentPath.isNotBlank(),
                                    onClick = { showOverflow = false; showCreateFileDialog = true }
                                )
                                DropdownMenuItem(
                                    text = { Text("Rename") },
                                    enabled = hostReady && actionTarget.isNotBlank(),
                                    onClick = { showOverflow = false; showRenameDialog = true }
                                )
                                DropdownMenuItem(
                                    text = { Text("Delete") },
                                    enabled = hostReady && actionTarget.isNotBlank(),
                                    onClick = { showOverflow = false; showDeleteDialog = true }
                                )
                                DropdownMenuItem(
                                    text = { Text("Download") },
                                    enabled = hostReady && selectedPath != null && File(selectedPath ?: "").isFile,
                                    onClick = {
                                        showOverflow = false
                                        downloadSelected()
                                    }
                                )
                                DropdownMenuItem(
                                    text = { Text("Delete Selected") },
                                    enabled = hostReady && selectedItems.isNotEmpty(),
                                    onClick = {
                                        showOverflow = false
                                        deleteSelectedItems()
                                    }
                                )
                            }
                        }
                    }
                    OutlinedTextField(
                        value = searchQuery,
                        onValueChange = { searchQuery = it },
                        label = { Text("Search in project") },
                        singleLine = true,
                        leadingIcon = { Icon(Icons.Default.Search, contentDescription = null) },
                        modifier = Modifier.fillMaxWidth()
                    )
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        FilterDropdown(selected = filterMode, onSelect = { filterMode = it })
                        SortDropdown(selected = sortMode, onSelect = { sortMode = it })
                    }
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .horizontalScroll(rememberScrollState()),
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        FilterChip(
                            selected = showHidden,
                            onClick = { showHidden = !showHidden },
                            label = { Text("Dotfiles") },
                            leadingIcon = {
                                Icon(
                                    imageVector = if (showHidden) Icons.Default.VisibilityOff else Icons.Default.Visibility,
                                    contentDescription = null
                                )
                            }
                        )
                        FilterChip(
                            selected = selectionMode,
                            onClick = { selectionMode = !selectionMode },
                            label = { Text(if (selectionMode) "Selecting" else "Select") },
                            leadingIcon = { Icon(Icons.Default.CheckCircle, contentDescription = null) }
                        )
                        FilterChip(
                            selected = favoritesOnly,
                            onClick = { favoritesOnly = !favoritesOnly },
                            label = { Text(if (favoritesOnly) "Favorites" else "All files") },
                            leadingIcon = {
                                Icon(
                                    imageVector = if (favoritesOnly) Icons.Default.Star else Icons.Default.StarBorder,
                                    contentDescription = null
                                )
                            }
                        )
                    }
                }
            }
    if (currentPath.isNotBlank()) {
        val segments = remember(currentPath) {
            currentPath.split(File.separatorChar).filter { it.isNotBlank() }
        }
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .horizontalScroll(rememberScrollState()),
            verticalAlignment = Alignment.CenterVertically
        ) {
            TextButton(onClick = { browsePath = File.separator }) { Text("/") }
            var acc = ""
            segments.forEach { seg ->
                acc = if (acc.isBlank()) "${File.separator}$seg" else "$acc${File.separator}$seg"
                Text(text = " > ", style = MaterialTheme.typography.bodySmall)
                TextButton(onClick = { browsePath = acc }) { Text(seg) }
            }
        }
    }
    if (gitStatus != null && gitStatus.isRepo) {
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
            Text(
                text = "Git: ${gitStatus.branch ?: "-"} • ${if (gitStatus.dirty) "dirty" else "clean"}",
                style = MaterialTheme.typography.bodySmall
            )
            OutlinedButton(onClick = onRefreshGit, enabled = hostReady) { Text("Refresh Git") }
        }
    }
    if (favorites.isNotEmpty()) {
        Text(text = "Favorites", style = MaterialTheme.typography.titleSmall)
        Row(
            modifier = Modifier.horizontalScroll(rememberScrollState()),
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            favorites.forEach { fav ->
                OutlinedButton(onClick = { browsePath = fav }) {
                    Text(fav.substringAfterLast(File.separatorChar))
                }
            }
        }
    }
    if (recents.isNotEmpty()) {
        Text(text = "Recent", style = MaterialTheme.typography.titleSmall)
        Row(
            modifier = Modifier.horizontalScroll(rememberScrollState()),
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            recents.take(8).forEach { recent ->
                OutlinedButton(onClick = { browsePath = recent }) {
                    Text(recent.substringAfterLast(File.separatorChar))
                }
            }
        }
    }
    if (selectionMode) {
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            AssistChip(
                onClick = { selectedItems = sorted.map { it.path }.toSet() },
                label = { Text("Select All") }
            )
            AssistChip(
                onClick = { selectedItems = emptySet() },
                label = { Text("Clear") }
            )
        }
        Text(text = "Selected items: ${selectedItems.size}", style = MaterialTheme.typography.bodySmall)
    }
    if (loading) {
        Text("Loading files…", style = MaterialTheme.typography.bodySmall)
    } else if (error != null) {
        Text("File list error: $error", style = MaterialTheme.typography.bodySmall)
    }
    if (status != null) {
        Text(status!!, style = MaterialTheme.typography.bodySmall)
    }
    Text(
        text = "Entries (${sorted.size})",
        style = MaterialTheme.typography.titleSmall
    )

    val listContent: @Composable () -> Unit = {
        Surface(
            tonalElevation = 1.dp,
            shape = MaterialTheme.shapes.large,
            modifier = Modifier
                .fillMaxWidth()
                .heightIn(min = 240.dp, max = 480.dp)
        ) {
            LazyColumn {
                items(sorted) { entry ->
                    val selected = entry.path == selectedPath
                    val isSelected = selectedItems.contains(entry.path)
                    val extension = if (entry.isDir) null else entry.name.substringAfterLast('.', "").takeIf { it.isNotBlank() }
                    val metaLine = buildList {
                        if (entry.isDir) {
                            add("Dir")
                        } else {
                            add(formatBytes(entry.size))
                        }
                        formatEpoch(entry.mtime)?.let { add(it) }
                        gitMap[entry.path]?.let { add("Git:$it") }
                    }.joinToString(" • ")
                    ListItem(
                        headlineContent = {
                            Text(text = entry.name.ifBlank { entry.path })
                        },
                        supportingContent = if (selected) {
                            {
                                Column {
                                    Text(text = metaLine, style = MaterialTheme.typography.bodySmall)
                                    if (entry.path != entry.name) {
                                        Text(text = entry.path, style = MaterialTheme.typography.bodySmall)
                                    }
                                }
                            }
                        } else {
                            null
                        },
                        leadingContent = {
                            if (selectionMode) {
                                IconToggleButton(
                                    checked = isSelected,
                                    onCheckedChange = {
                                        selectedItems = if (isSelected) {
                                            selectedItems - entry.path
                                        } else {
                                            selectedItems + entry.path
                                        }
                                    }
                                ) {
                                    Icon(
                                        imageVector = if (isSelected) Icons.Default.CheckCircle else Icons.Default.RadioButtonUnchecked,
                                        contentDescription = null
                                    )
                                }
                            } else {
                                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                                    Icon(
                                        imageVector = if (entry.isDir) Icons.Default.Folder else Icons.Default.InsertDriveFile,
                                        contentDescription = null
                                    )
                                    if (extension != null) {
                                        Surface(
                                            color = MaterialTheme.colorScheme.primary.copy(alpha = 0.15f),
                                            shape = RoundedCornerShape(6.dp)
                                        ) {
                                            Text(
                                                text = extension.uppercase(Locale.getDefault()).take(4),
                                                style = MaterialTheme.typography.labelSmall,
                                                modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                                            )
                                        }
                                    }
                                }
                            }
                        },
                        trailingContent = {
                            Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                                if (entry.isDir) {
                                    IconButton(onClick = { browsePath = entry.path }) {
                                        Icon(Icons.Default.KeyboardArrowRight, contentDescription = "Open folder")
                                    }
                                } else {
                                    IconButton(onClick = { openEditorFile(entry.path) }) {
                                        Icon(Icons.Default.OpenInNew, contentDescription = "Open file")
                                    }
                                    if (gitMap.containsKey(entry.path)) {
                                        IconButton(onClick = {
                                            scope.launch {
                                                val result = viewModel.gitDiff(
                                                    host,
                                                    port,
                                                    workingDir.trim().ifBlank { null },
                                                    entry.path
                                                )
                                                diffContent = result.getOrElse { "Diff failed: ${it.message}" }
                                            }
                                        }) {
                                            Icon(Icons.Default.CompareArrows, contentDescription = "Diff")
                                        }
                                    }
                                }
                                IconButton(onClick = {
                                    if (favorites.contains(entry.path)) {
                                        favorites = favorites - entry.path
                                    } else {
                                        favorites = favorites + entry.path
                                    }
                                    store.saveFavorites(favorites)
                                }) {
                                    Icon(
                                        imageVector = if (favorites.contains(entry.path)) Icons.Default.Star else Icons.Default.StarBorder,
                                        contentDescription = "Favorite"
                                    )
                                }
                            }
                        },
                        colors = ListItemDefaults.colors(
                            containerColor = if (selected) MaterialTheme.colorScheme.surfaceVariant else MaterialTheme.colorScheme.surface
                        ),
                        modifier = Modifier.clickable { selectedPath = entry.path }
                    )
                    Divider()
                }
            }
        }
    }

    val editorContent: @Composable () -> Unit = {
        Column(
            modifier = Modifier.fillMaxWidth(),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            if (openTabs.isNotEmpty()) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .horizontalScroll(rememberScrollState()),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    openTabs.forEach { path ->
                        val name = path.substringAfterLast(File.separatorChar)
                        val active = path == activeEditorPath
                        val accent = if (active) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.surfaceVariant
                        val ext = name.substringAfterLast('.', "").takeIf { it.isNotBlank() }
                        val dirty = editorDirty[path] == true
                        Column(
                            modifier = Modifier
                                .background(Color.Transparent)
                                .clickable { activeEditorPath = path }
                        ) {
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                modifier = Modifier
                                    .background(
                                        color = if (active) MaterialTheme.colorScheme.surfaceVariant else MaterialTheme.colorScheme.surface,
                                        shape = RoundedCornerShape(10.dp)
                                    )
                                    .padding(horizontal = 10.dp, vertical = 6.dp)
                            ) {
                                if (ext != null) {
                                    Surface(
                                        color = MaterialTheme.colorScheme.primary.copy(alpha = 0.18f),
                                        shape = RoundedCornerShape(6.dp)
                                    ) {
                                        Text(
                                            text = ext.uppercase(Locale.getDefault()).take(4),
                                            style = MaterialTheme.typography.labelSmall,
                                            modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                                        )
                                    }
                                    Spacer(modifier = Modifier.width(6.dp))
                                }
                                Text(text = name, style = MaterialTheme.typography.labelLarge)
                                if (dirty) {
                                    Spacer(modifier = Modifier.width(6.dp))
                                    Box(
                                        modifier = Modifier
                                            .size(8.dp)
                                            .background(MaterialTheme.colorScheme.primary, CircleShape)
                                    )
                                }
                                Spacer(modifier = Modifier.width(6.dp))
                                IconButton(
                                    onClick = { closeEditorTab(path) },
                                    modifier = Modifier.size(22.dp)
                                ) {
                                    Icon(Icons.Default.Close, contentDescription = "Close tab", modifier = Modifier.size(16.dp))
                                }
                            }
                            Box(
                                modifier = Modifier
                                    .padding(top = 2.dp)
                                    .height(3.dp)
                                    .fillMaxWidth()
                                    .background(accent, RoundedCornerShape(2.dp))
                            )
                        }
                    }
                }
            }
            val active = activeEditorPath
            if (active != null) {
                val buffer = editorBuffers[active] ?: ""
                OutlinedTextField(
                    value = buffer,
                    onValueChange = {
                        editorBuffers[active] = it
                        editorDirty[active] = true
                    },
                    label = { Text("Editor: ${active.substringAfterLast(File.separatorChar)}") },
                    modifier = Modifier
                        .fillMaxWidth()
                        .heightIn(min = 200.dp, max = 520.dp),
                    textStyle = MaterialTheme.typography.bodySmall.copy(fontFamily = androidx.compose.ui.text.font.FontFamily.Monospace)
                )
            } else {
                Text(
                    text = "Open a file to edit.",
                    style = MaterialTheme.typography.bodySmall
                )
            }
        }
    }

    if (isLandscape) {
        Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            Column(modifier = Modifier.weight(0.45f)) { listContent() }
            Column(modifier = Modifier.weight(0.55f)) { editorContent() }
        }
    } else {
        listContent()
        Spacer(modifier = Modifier.height(8.dp))
        editorContent()
    }
    if (selectedPath != null) {
        val selected = selectedPath ?: ""
        val relative = relativeToBase(gitStatus?.root ?: workingDir.ifBlank { null }, selected)
        Text(text = "Selected: $selected", style = MaterialTheme.typography.bodySmall)
        if (!relative.isNullOrBlank()) {
            Text(text = "Relative: $relative", style = MaterialTheme.typography.bodySmall)
        }
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            AssistChip(
                onClick = {
                    clipboard.setText(AnnotatedString(selected))
                    status = "Copied full path"
                },
                label = { Text("Copy Path") },
                leadingIcon = { Icon(Icons.Default.ContentCopy, contentDescription = null) }
            )
            AssistChip(
                onClick = {
                    if (!relative.isNullOrBlank()) {
                        clipboard.setText(AnnotatedString(relative))
                        status = "Copied relative path"
                    } else {
                        status = "No relative path"
                    }
                },
                label = { Text("Copy Rel") },
                leadingIcon = { Icon(Icons.Default.ContentCopy, contentDescription = null) }
            )
            if (File(selected).isDirectory) {
                AssistChip(
                    onClick = { browsePath = selected },
                    label = { Text("Open") },
                    leadingIcon = { Icon(Icons.Default.FolderOpen, contentDescription = null) }
                )
                AssistChip(
                    onClick = { onUseWorkingDir(selected) },
                    label = { Text("Use Dir") },
                    leadingIcon = { Icon(Icons.Default.FolderOpen, contentDescription = null) }
                )
            }
        }
    }
        }
        BottomAppBar(
            modifier = Modifier.align(Alignment.BottomCenter),
            containerColor = MaterialTheme.colorScheme.surfaceVariant,
            tonalElevation = 4.dp
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceEvenly,
                verticalAlignment = Alignment.CenterVertically
            ) {
                BottomBarButton(
                    label = "Up",
                    icon = Icons.Default.ArrowUpward,
                    enabled = currentPath.isNotBlank()
                ) {
                    val parent = File(currentPath).parentFile?.absolutePath.orEmpty()
                    if (parent.isNotBlank()) browsePath = parent
                }
                BottomBarButton(
                    label = "Home",
                    icon = Icons.Default.Home,
                    enabled = hostReady
                ) { browsePath = "" }
                BottomBarButton(
                    label = "Use Dir",
                    icon = Icons.Default.FolderOpen,
                    enabled = currentPath.isNotBlank()
                ) { onUseWorkingDir(currentPath) }
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
                    val result = viewModel.createPath(host, port, path, "dir")
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

    if (showCreateFileDialog) {
        val suggested = if (currentPath.isNotBlank()) {
            "${currentPath.trimEnd('/')}/new-file.txt"
        } else {
            "new-file.txt"
        }
        DirectoryActionDialog(
            title = "Create file",
            confirmLabel = "Create",
            initialValue = suggested,
            onConfirm = { path ->
                scope.launch {
                    val result = viewModel.createPath(host, port, path, "file")
                    status = result.fold(
                        onSuccess = {
                            refreshKey += 1
                            "Created file: $path"
                        },
                        onFailure = { "Create failed: ${it.message}" }
                    )
                }
            },
            onDismiss = { showCreateFileDialog = false }
        )
    }

    if (showRenameDialog) {
        DirectoryActionDialog(
            title = "Rename",
            confirmLabel = "Rename",
            initialValue = actionTarget,
            onConfirm = { newPath ->
                val oldPath = actionTarget
                scope.launch {
                    val result = viewModel.renamePath(host, port, oldPath, newPath)
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
                    val result = viewModel.deletePath(host, port, target, recursive = true)
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

    if (previewFile != null) {
        FilePreviewDialog(
            file = previewFile!!,
            content = previewText,
            onContentChange = {
                previewText = it
                previewDirty = true
            },
            onSave = {
                val target = previewFile?.path ?: return@FilePreviewDialog
                scope.launch {
                    val result = viewModel.writeFile(host, port, target, previewText)
                    status = result.fold(
                        onSuccess = {
                            previewDirty = false
                            "Saved: $target"
                        },
                        onFailure = { "Save failed: ${it.message}" }
                    )
                }
            },
            onDismiss = {
                previewFile = null
                previewText = ""
                previewDirty = false
            },
            canSave = previewDirty && !(previewFile?.isBinary ?: false)
        )
    }

    if (diffContent != null) {
        DiffDialog(
            content = diffContent ?: "",
            onDismiss = { diffContent = null }
        )
    }

    if (showCommandPalette) {
        CommandPaletteDialog(
            onDismiss = { showCommandPalette = false },
            onRunCommand = { commandLabel ->
                when (commandLabel) {
                    "Open working dir" -> {
                        if (workingDir.isNotBlank()) browsePath = workingDir
                    }
                    "Refresh files" -> refreshKey += 1
                    "New file" -> showCreateFileDialog = true
                    "New folder" -> showCreateDialog = true
                    "Find in project" -> showProjectSearch = true
                    "Replace in project" -> showProjectReplace = true
                    "Save active file" -> saveActiveEditor()
                    "Refresh git" -> onRefreshGit()
                }
                showCommandPalette = false
            }
        )
    }

    if (showProjectSearch) {
        ProjectSearchDialog(
            query = projectQuery,
            onQueryChange = { projectQuery = it },
            glob = projectGlob,
            onGlobChange = { projectGlob = it },
            caseSensitive = projectCaseSensitive,
            onCaseSensitiveChange = { projectCaseSensitive = it },
            regex = projectRegex,
            onRegexChange = { projectRegex = it },
            loading = searchLoading,
            message = searchMessage,
            results = searchResults,
            onSearch = {
                if (!hostReady) return@ProjectSearchDialog
                searchLoading = true
                searchMessage = null
                scope.launch {
                    val result = viewModel.searchProject(
                        host,
                        port,
                        projectQuery.trim(),
                        workingDir.trim().ifBlank { null },
                        projectCaseSensitive,
                        projectRegex,
                        projectGlob.trim().ifBlank { null }
                    )
                    searchLoading = false
                    if (result.isSuccess) {
                        searchResults = result.getOrDefault(emptyList())
                        searchMessage = "Found ${searchResults.size} result(s)"
                    } else {
                        searchMessage = result.exceptionOrNull()?.message
                        searchResults = emptyList()
                    }
                }
            },
            onOpenResult = { result ->
                openEditorFile(result.file)
                showProjectSearch = false
            },
            onDismiss = { showProjectSearch = false }
        )
    }

    if (showProjectReplace) {
        ProjectReplaceDialog(
            query = projectQuery,
            onQueryChange = { projectQuery = it },
            replace = projectReplace,
            onReplaceChange = { projectReplace = it },
            glob = projectGlob,
            onGlobChange = { projectGlob = it },
            caseSensitive = projectCaseSensitive,
            onCaseSensitiveChange = { projectCaseSensitive = it },
            regex = projectRegex,
            onRegexChange = { projectRegex = it },
            message = replaceMessage,
            onReplace = {
                if (!hostReady) return@ProjectReplaceDialog
                replaceMessage = null
                scope.launch {
                    val result = viewModel.replaceProject(
                        host,
                        port,
                        projectQuery.trim(),
                        projectReplace,
                        workingDir.trim().ifBlank { null },
                        projectCaseSensitive,
                        projectRegex,
                        projectGlob.trim().ifBlank { null }
                    )
                    replaceMessage = result.fold(
                        onSuccess = { summary ->
                            "Replaced ${summary.replacements} occurrence(s) in ${summary.files.size} file(s)"
                        },
                        onFailure = { it.message ?: "Replace failed" }
                    )
                }
            },
            onDismiss = { showProjectReplace = false }
        )
    }
}

@Composable
private fun FilterDropdown(
    selected: String,
    onSelect: (String) -> Unit
) {
    var expanded by remember { mutableStateOf(false) }
    val label = when (selected) {
        "files" -> "Files"
        "dirs" -> "Dirs"
        else -> "All"
    }
    Box {
        AssistChip(
            onClick = { expanded = true },
            label = { Text("Filter: $label") },
            leadingIcon = { Icon(Icons.Default.FilterAlt, contentDescription = null) }
        )
        DropdownMenu(expanded = expanded, onDismissRequest = { expanded = false }) {
            DropdownMenuItem(text = { Text("All") }, onClick = { expanded = false; onSelect("all") })
            DropdownMenuItem(text = { Text("Files") }, onClick = { expanded = false; onSelect("files") })
            DropdownMenuItem(text = { Text("Dirs") }, onClick = { expanded = false; onSelect("dirs") })
        }
    }
}

@Composable
private fun SortDropdown(
    selected: String,
    onSelect: (String) -> Unit
) {
    var expanded by remember { mutableStateOf(false) }
    val label = when (selected) {
        "mtime" -> "Modified"
        "size" -> "Size"
        else -> "Name"
    }
    Box {
        AssistChip(
            onClick = { expanded = true },
            label = { Text("Sort: $label") },
            leadingIcon = { Icon(Icons.Default.Sort, contentDescription = null) }
        )
        DropdownMenu(expanded = expanded, onDismissRequest = { expanded = false }) {
            DropdownMenuItem(text = { Text("Name") }, onClick = { expanded = false; onSelect("name") })
            DropdownMenuItem(text = { Text("Modified") }, onClick = { expanded = false; onSelect("mtime") })
            DropdownMenuItem(text = { Text("Size") }, onClick = { expanded = false; onSelect("size") })
        }
    }
}

@Composable
private fun ActionIconButton(
    label: String,
    icon: ImageVector,
    enabled: Boolean = true,
    showLabel: Boolean = true,
    onClick: () -> Unit
) {
    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        FilledTonalIconButton(
            onClick = onClick,
            enabled = enabled,
            modifier = Modifier.size(48.dp)
        ) {
            Icon(icon, contentDescription = label)
        }
        if (showLabel) {
            Text(text = label, style = MaterialTheme.typography.labelSmall)
        }
    }
}

@Composable
private fun BottomBarButton(
    label: String,
    icon: ImageVector,
    enabled: Boolean = true,
    onClick: () -> Unit
) {
    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        modifier = Modifier.padding(horizontal = 6.dp)
    ) {
        IconButton(onClick = onClick, enabled = enabled) {
            Icon(icon, contentDescription = label)
        }
        Text(text = label, style = MaterialTheme.typography.labelSmall)
    }
}

@Composable
private fun FilePreviewDialog(
    file: FileRead,
    content: String,
    onContentChange: (String) -> Unit,
    onSave: () -> Unit,
    onDismiss: () -> Unit,
    canSave: Boolean
) {
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
                    .heightIn(min = 360.dp, max = 700.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                Text(text = "File: ${file.path}", style = MaterialTheme.typography.titleMedium)
                if (file.isBinary) {
                    Text("Binary file preview not supported.", style = MaterialTheme.typography.bodySmall)
                } else {
                    OutlinedTextField(
                        value = content,
                        onValueChange = onContentChange,
                        modifier = Modifier
                            .fillMaxWidth()
                            .heightIn(min = 200.dp, max = 420.dp),
                        label = { Text("Content") }
                    )
                }
                if (file.truncated) {
                    Text("Preview truncated.", style = MaterialTheme.typography.bodySmall)
                }
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.End
                ) {
                    TextButton(onClick = onDismiss) { Text("Close") }
                    Button(onClick = onSave, enabled = canSave) { Text("Save") }
                }
            }
        }
    }
}

@Composable
private fun DiffDialog(
    content: String,
    onDismiss: () -> Unit
) {
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
                    .heightIn(min = 300.dp, max = 700.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                Text(text = "Git Diff", style = MaterialTheme.typography.titleMedium)
                Surface(tonalElevation = 1.dp, modifier = Modifier.fillMaxWidth()) {
                    Column(
                        modifier = Modifier
                            .padding(8.dp)
                            .verticalScroll(rememberScrollState())
                            .heightIn(min = 200.dp)
                    ) {
                        Text(
                            text = if (content.isBlank()) "No diff." else content,
                            style = MaterialTheme.typography.bodySmall
                        )
                    }
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
}

@Composable
private fun CommandPaletteDialog(
    onDismiss: () -> Unit,
    onRunCommand: (String) -> Unit
) {
    val commands = listOf(
        "Open working dir",
        "Refresh files",
        "New file",
        "New folder",
        "Find in project",
        "Replace in project",
        "Save active file",
        "Refresh git"
    )
    var query by remember { mutableStateOf("") }
    val filtered = commands.filter { it.contains(query, ignoreCase = true) }
    Dialog(onDismissRequest = onDismiss) {
        Surface(
            modifier = Modifier.fillMaxWidth(),
            shape = MaterialTheme.shapes.large,
            tonalElevation = 3.dp
        ) {
            Column(
                modifier = Modifier
                    .padding(16.dp)
                    .fillMaxWidth(),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                Text(text = "Command Palette", style = MaterialTheme.typography.titleMedium)
                OutlinedTextField(
                    value = query,
                    onValueChange = { query = it },
                    label = { Text("Type command") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )
                if (filtered.isEmpty()) {
                    Text(text = "No matches", style = MaterialTheme.typography.bodySmall)
                } else {
                    Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                        filtered.take(8).forEach { cmd ->
                            TextButton(onClick = { onRunCommand(cmd) }) {
                                Text(cmd)
                            }
                        }
                    }
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
}

@Composable
private fun ProjectSearchDialog(
    query: String,
    onQueryChange: (String) -> Unit,
    glob: String,
    onGlobChange: (String) -> Unit,
    caseSensitive: Boolean,
    onCaseSensitiveChange: (Boolean) -> Unit,
    regex: Boolean,
    onRegexChange: (Boolean) -> Unit,
    loading: Boolean,
    message: String?,
    results: List<SearchResult>,
    onSearch: () -> Unit,
    onOpenResult: (SearchResult) -> Unit,
    onDismiss: () -> Unit
) {
    Dialog(onDismissRequest = onDismiss) {
        Surface(
            modifier = Modifier.fillMaxWidth(),
            shape = MaterialTheme.shapes.large,
            tonalElevation = 3.dp
        ) {
            Column(
                modifier = Modifier
                    .padding(16.dp)
                    .fillMaxWidth()
                    .heightIn(min = 300.dp, max = 720.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                Text(text = "Find in Project", style = MaterialTheme.typography.titleMedium)
                OutlinedTextField(
                    value = query,
                    onValueChange = onQueryChange,
                    label = { Text("Search") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )
                OutlinedTextField(
                    value = glob,
                    onValueChange = onGlobChange,
                    label = { Text("Glob (optional)") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )
                Row(horizontalArrangement = Arrangement.spacedBy(12.dp), verticalAlignment = Alignment.CenterVertically) {
                    Text(text = "Case sensitive", style = MaterialTheme.typography.bodySmall)
                    Switch(checked = caseSensitive, onCheckedChange = onCaseSensitiveChange)
                    Text(text = "Regex", style = MaterialTheme.typography.bodySmall)
                    Switch(checked = regex, onCheckedChange = onRegexChange)
                }
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    Button(onClick = onSearch) { Text("Search") }
                    OutlinedButton(onClick = onDismiss) { Text("Close") }
                }
                if (loading) {
                    Text("Searching…", style = MaterialTheme.typography.bodySmall)
                } else if (!message.isNullOrBlank()) {
                    Text(message, style = MaterialTheme.typography.bodySmall)
                }
                if (results.isNotEmpty()) {
                    Surface(tonalElevation = 1.dp, modifier = Modifier.fillMaxWidth()) {
                        Column(
                            modifier = Modifier
                                .padding(8.dp)
                                .verticalScroll(rememberScrollState()),
                            verticalArrangement = Arrangement.spacedBy(6.dp)
                        ) {
                            results.take(50).forEach { result ->
                                TextButton(onClick = { onOpenResult(result) }) {
                                    Text("${result.file}:${result.line}:${result.column}  ${result.text}")
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun ProjectReplaceDialog(
    query: String,
    onQueryChange: (String) -> Unit,
    replace: String,
    onReplaceChange: (String) -> Unit,
    glob: String,
    onGlobChange: (String) -> Unit,
    caseSensitive: Boolean,
    onCaseSensitiveChange: (Boolean) -> Unit,
    regex: Boolean,
    onRegexChange: (Boolean) -> Unit,
    message: String?,
    onReplace: () -> Unit,
    onDismiss: () -> Unit
) {
    Dialog(onDismissRequest = onDismiss) {
        Surface(
            modifier = Modifier.fillMaxWidth(),
            shape = MaterialTheme.shapes.large,
            tonalElevation = 3.dp
        ) {
            Column(
                modifier = Modifier
                    .padding(16.dp)
                    .fillMaxWidth()
                    .heightIn(min = 260.dp, max = 680.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                Text(text = "Replace in Project", style = MaterialTheme.typography.titleMedium)
                OutlinedTextField(
                    value = query,
                    onValueChange = onQueryChange,
                    label = { Text("Find") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )
                OutlinedTextField(
                    value = replace,
                    onValueChange = onReplaceChange,
                    label = { Text("Replace with") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )
                OutlinedTextField(
                    value = glob,
                    onValueChange = onGlobChange,
                    label = { Text("Glob (optional)") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )
                Row(horizontalArrangement = Arrangement.spacedBy(12.dp), verticalAlignment = Alignment.CenterVertically) {
                    Text(text = "Case sensitive", style = MaterialTheme.typography.bodySmall)
                    Switch(checked = caseSensitive, onCheckedChange = onCaseSensitiveChange)
                    Text(text = "Regex", style = MaterialTheme.typography.bodySmall)
                    Switch(checked = regex, onCheckedChange = onRegexChange)
                }
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    Button(onClick = onReplace) { Text("Replace All") }
                    OutlinedButton(onClick = onDismiss) { Text("Close") }
                }
                if (!message.isNullOrBlank()) {
                    Text(message, style = MaterialTheme.typography.bodySmall)
                }
            }
        }
    }
}

@Composable
private fun GitHubManagerScreen(
    host: String,
    port: String,
    isConnected: Boolean,
    workingDir: String,
    status: GitStatus?,
    commitMessage: String,
    onCommitMessageChange: (String) -> Unit,
    addAll: Boolean,
    onToggleAddAll: (Boolean) -> Unit,
    onRefresh: () -> Unit,
    onPull: () -> Unit,
    onPush: () -> Unit,
    onCommit: () -> Unit,
    message: String?,
    viewModel: CodexViewModel
) {
    val scope = rememberCoroutineScope()
    val clipboard = LocalClipboardManager.current
    var branches by remember { mutableStateOf<List<String>>(emptyList()) }
    var activeBranch by remember { mutableStateOf<String?>(null) }
    var branchMenuExpanded by remember { mutableStateOf(false) }
    var newBranch by remember { mutableStateOf("") }
    var logEntries by remember { mutableStateOf<List<String>>(emptyList()) }
    var actionMessage by remember { mutableStateOf<String?>(null) }
    var diffText by remember { mutableStateOf<String?>(null) }
    var showDiff by remember { mutableStateOf(false) }

    fun refreshBranches() {
        scope.launch {
            val result = viewModel.gitBranches(host, port, workingDir)
            if (result.isSuccess) {
                val (items, current) = result.getOrThrow()
                branches = items
                activeBranch = current ?: status?.branch
            } else {
                actionMessage = result.exceptionOrNull()?.message
            }
        }
    }

    fun refreshLog() {
        scope.launch {
            val result = viewModel.gitLog(host, port, workingDir, limit = 8)
            if (result.isSuccess) {
                logEntries = result.getOrThrow()
            } else {
                actionMessage = result.exceptionOrNull()?.message
            }
        }
    }

    fun fetchRemote() {
        scope.launch {
            val result = viewModel.gitFetch(host, port, workingDir)
            actionMessage = result.fold(
                onSuccess = { "Fetch complete" },
                onFailure = { it.message }
            )
            refreshBranches()
        }
    }

    fun checkoutBranch(branch: String, create: Boolean) {
        scope.launch {
            val result = viewModel.gitCheckout(host, port, workingDir, branch, create)
            actionMessage = result.fold(
                onSuccess = { if (create) "Created $branch" else "Switched to $branch" },
                onFailure = { it.message }
            )
            refreshBranches()
        }
    }

    LaunchedEffect(isConnected, workingDir, status?.branch) {
        if (isConnected && workingDir.isNotBlank()) {
            refreshBranches()
            refreshLog()
        }
    }

    val changeItems = status?.changes?.mapNotNull { line ->
        val trimmed = line.trim()
        if (trimmed.isBlank()) return@mapNotNull null
        val code = trimmed.take(2)
        val pathPart = trimmed.drop(3).trim()
        val path = pathPart.substringAfter("->").trim()
        val index = code.getOrNull(0) ?: ' '
        val work = code.getOrNull(1) ?: ' '
        Triple(path, index, work)
    } ?: emptyList()

    Surface(tonalElevation = 1.dp, modifier = Modifier.fillMaxWidth()) {
        Column(modifier = Modifier.padding(10.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Text(text = "Repository", style = MaterialTheme.typography.labelLarge)
            Text(text = workingDir.ifBlank { "(not set)" }, style = MaterialTheme.typography.labelSmall)
            Row(
                modifier = Modifier.horizontalScroll(rememberScrollState()),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                AssistChip(onClick = onRefresh, enabled = isConnected, label = { Text("Refresh") }, leadingIcon = {
                    Icon(Icons.Default.Refresh, contentDescription = null)
                })
                AssistChip(onClick = { fetchRemote() }, enabled = isConnected, label = { Text("Fetch") }, leadingIcon = {
                    Icon(Icons.Default.Download, contentDescription = null)
                })
                AssistChip(onClick = onPull, enabled = isConnected, label = { Text("Pull") }, leadingIcon = {
                    Icon(Icons.Default.Download, contentDescription = null)
                })
                AssistChip(onClick = onPush, enabled = isConnected, label = { Text("Push") }, leadingIcon = {
                    Icon(Icons.Default.UploadFile, contentDescription = null)
                })
            }
        }
    }

    Surface(tonalElevation = 1.dp, modifier = Modifier.fillMaxWidth()) {
        Column(modifier = Modifier.padding(10.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Text(text = "Branch", style = MaterialTheme.typography.labelLarge)
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Box(modifier = Modifier.weight(1f)) {
                    OutlinedTextField(
                        value = activeBranch ?: status?.branch ?: "-",
                        onValueChange = {},
                        readOnly = true,
                        label = { Text("Current") },
                        trailingIcon = {
                            IconButton(onClick = { branchMenuExpanded = true }) {
                                Icon(Icons.Default.KeyboardArrowDown, contentDescription = "Branches")
                            }
                        },
                        modifier = Modifier.fillMaxWidth()
                    )
                    DropdownMenu(expanded = branchMenuExpanded, onDismissRequest = { branchMenuExpanded = false }) {
                        branches.forEach { branch ->
                            DropdownMenuItem(
                                text = { Text(branch) },
                                onClick = {
                                    branchMenuExpanded = false
                                    checkoutBranch(branch, create = false)
                                }
                            )
                        }
                    }
                }
                IconButton(onClick = { refreshBranches() }, enabled = isConnected) {
                    Icon(Icons.Default.Refresh, contentDescription = "Refresh branches")
                }
            }
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .horizontalScroll(rememberScrollState()),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                AssistChip(
                    onClick = {},
                    label = { Text(status?.upstream ?: "no upstream") },
                    leadingIcon = { Icon(Icons.Default.CompareArrows, contentDescription = null) }
                )
                if (status?.ahead != null) {
                    AssistChip(
                        onClick = {},
                        label = { Text("ahead ${status.ahead}") },
                        leadingIcon = { Icon(Icons.Default.ArrowUpward, contentDescription = null) }
                    )
                }
                if (status?.behind != null) {
                    AssistChip(
                        onClick = {},
                        label = { Text("behind ${status.behind}") },
                        leadingIcon = { Icon(Icons.Default.KeyboardArrowDown, contentDescription = null) }
                    )
                }
            }
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
                OutlinedTextField(
                    value = newBranch,
                    onValueChange = { newBranch = it },
                    label = { Text("New branch") },
                    singleLine = true,
                    modifier = Modifier.weight(1f)
                )
                FilledTonalIconButton(
                    onClick = {
                        val name = newBranch.trim()
                        if (name.isNotBlank()) {
                            checkoutBranch(name, create = true)
                            newBranch = ""
                        }
                    },
                    enabled = isConnected && newBranch.isNotBlank()
                ) {
                    Icon(Icons.Default.Add, contentDescription = "Create branch")
                }
            }
        }
    }

    Surface(tonalElevation = 1.dp, modifier = Modifier.fillMaxWidth()) {
        Column(modifier = Modifier.padding(10.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Text(text = "Changes", style = MaterialTheme.typography.labelLarge)
            if (status == null) {
                Text("No status yet.", style = MaterialTheme.typography.labelSmall)
            } else if (!status.isRepo) {
                Text("Not a git repository.", style = MaterialTheme.typography.labelSmall)
            } else if (changeItems.isEmpty()) {
                Text("No changes.", style = MaterialTheme.typography.labelSmall)
            } else {
                changeItems.forEach { (path, index, work) ->
                    val icon = when {
                        index == 'A' || work == 'A' -> Icons.Default.Add
                        index == 'D' || work == 'D' -> Icons.Default.Delete
                        index == 'R' || work == 'R' -> Icons.Default.DriveFileRenameOutline
                        index == 'M' || work == 'M' -> Icons.Default.Edit
                        index == '?' || work == '?' -> Icons.Default.NoteAdd
                        else -> Icons.Default.InsertDriveFile
                    }
                    ListItem(
                        headlineContent = { Text(path) },
                        supportingContent = { Text("Index: $index · Worktree: $work") },
                        leadingContent = { Icon(icon, contentDescription = null) },
                        trailingContent = {
                            Row {
                                IconButton(onClick = {
                                    scope.launch {
                                        val result = viewModel.gitDiff(host, port, workingDir, path)
                                        diffText = result.fold(
                                            onSuccess = { it.ifBlank { "(no diff)" } },
                                            onFailure = { it.message ?: "Diff failed" }
                                        )
                                        showDiff = true
                                    }
                                }) {
                                    Icon(Icons.Default.CompareArrows, contentDescription = "Diff")
                                }
                                IconButton(onClick = {
                                    clipboard.setText(AnnotatedString(path))
                                    actionMessage = "Copied path"
                                }) {
                                    Icon(Icons.Default.ContentCopy, contentDescription = "Copy path")
                                }
                            }
                        }
                    )
                }
            }
        }
    }

    Surface(tonalElevation = 1.dp, modifier = Modifier.fillMaxWidth()) {
        Column(modifier = Modifier.padding(10.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Text(text = "Recent commits", style = MaterialTheme.typography.labelLarge)
            if (logEntries.isEmpty()) {
                Text("No log entries.", style = MaterialTheme.typography.labelSmall)
            } else {
                logEntries.forEach { line ->
                    Text(line, style = MaterialTheme.typography.bodySmall)
                }
            }
        }
    }

    Surface(tonalElevation = 1.dp, modifier = Modifier.fillMaxWidth()) {
        Column(modifier = Modifier.padding(10.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Text(text = "Commit", style = MaterialTheme.typography.labelLarge)
            OutlinedTextField(
                value = commitMessage,
                onValueChange = onCommitMessageChange,
                label = { Text("Commit message") },
                singleLine = true,
                modifier = Modifier.fillMaxWidth()
            )
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .horizontalScroll(rememberScrollState()),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                AssistChip(
                    onClick = { onToggleAddAll(!addAll) },
                    enabled = isConnected,
                    label = { Text(if (addAll) "Add all ✓" else "Add all") },
                    leadingIcon = { Icon(Icons.Default.Add, contentDescription = null) }
                )
                AssistChip(
                    onClick = onCommit,
                    enabled = isConnected,
                    label = { Text("Commit") },
                    leadingIcon = { Icon(Icons.Default.Save, contentDescription = null) }
                )
            }
        }
    }
    if (!message.isNullOrBlank()) {
        Text(message, style = MaterialTheme.typography.labelSmall)
    }
    if (!actionMessage.isNullOrBlank()) {
        Text(actionMessage ?: "", style = MaterialTheme.typography.labelSmall)
    }

    if (showDiff) {
        Dialog(onDismissRequest = { showDiff = false }) {
            Surface(
                modifier = Modifier.fillMaxWidth(),
                shape = MaterialTheme.shapes.large,
                tonalElevation = 2.dp
            ) {
                Column(
                    modifier = Modifier.padding(12.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Text(text = "Diff", style = MaterialTheme.typography.labelLarge)
                    Box(modifier = Modifier.heightIn(min = 160.dp, max = 420.dp)) {
                        LazyColumn {
                            items(diffText?.lines().orEmpty()) { line ->
                                Text(
                                    text = line,
                                    style = MaterialTheme.typography.bodySmall,
                                    fontFamily = FontFamily.Monospace
                                )
                            }
                        }
                    }
                    Row(horizontalArrangement = Arrangement.End, modifier = Modifier.fillMaxWidth()) {
                        TextButton(onClick = { showDiff = false }) { Text("Close") }
                    }
                }
            }
        }
    }
}

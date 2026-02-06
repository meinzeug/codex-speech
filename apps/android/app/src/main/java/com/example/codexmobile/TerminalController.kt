package com.example.codexmobile

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.graphics.Color
import android.graphics.Paint
import android.graphics.Typeface
import android.util.Log
import android.view.KeyEvent
import android.view.MotionEvent
import android.view.View
import com.termux.terminal.KeyHandler
import com.termux.terminal.TerminalEmulator
import com.termux.terminal.TerminalSession
import com.termux.terminal.TerminalSessionClient
import com.termux.view.TerminalView
import com.termux.view.TerminalViewClient
import kotlin.math.roundToInt

class TerminalController(
    private val context: Context,
    private val sendInput: (String) -> Unit,
) {
    private val prefs = context.getSharedPreferences("codex_ui", Context.MODE_PRIVATE)
    private val pendingOutput = StringBuilder()
    private var terminalView: TerminalView? = null
    private val scaledDensity = context.resources.displayMetrics.scaledDensity
    private val minFontPx = (12f * scaledDensity).roundToInt()
    private val maxFontPx = (36f * scaledDensity).roundToInt()
    private val fontStepPx = (2f * scaledDensity).roundToInt().coerceAtLeast(1)
    private var fontSizePx = prefs.getInt(KEY_FONT_SIZE, (22f * scaledDensity).roundToInt())
        .coerceIn(minFontPx, maxFontPx)
    private var autoFitEnabled = prefs.getBoolean(KEY_AUTO_FIT, false)
    private val autoFitColumns = 60
    private val measurePaint = Paint().apply { typeface = Typeface.MONOSPACE }

    private val sessionClient = object : TerminalSessionClient {
        override fun onTextChanged(changedSession: TerminalSession) {
            terminalView?.invalidate()
        }

        override fun onTitleChanged(changedSession: TerminalSession) {
            // No-op
        }

        override fun onSessionFinished(finishedSession: TerminalSession) {
            // No-op
        }

        override fun onCopyTextToClipboard(session: TerminalSession, text: String) {
            val clipboard = context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
            clipboard.setPrimaryClip(ClipData.newPlainText("terminal", text))
        }

        override fun onPasteTextFromClipboard(session: TerminalSession?) {
            val clipboard = context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
            val clip = clipboard.primaryClip
            if (clip != null && clip.itemCount > 0) {
                val paste = clip.getItemAt(0).coerceToText(context).toString()
                if (paste.isNotEmpty()) {
                    sendInput(paste)
                }
            }
        }

        override fun onBell(session: TerminalSession) {
            // No-op
        }

        override fun onColorsChanged(session: TerminalSession) {
            terminalView?.invalidate()
        }

        override fun onTerminalCursorStateChange(state: Boolean) {
            // No-op
        }

        override fun setTerminalShellPid(session: TerminalSession, pid: Int) {
            // No-op
        }

        override fun getTerminalCursorStyle(): Int? = null

        override fun logError(tag: String, message: String) {
            Log.e(tag, message)
        }

        override fun logWarn(tag: String, message: String) {
            Log.w(tag, message)
        }

        override fun logInfo(tag: String, message: String) {
            Log.i(tag, message)
        }

        override fun logDebug(tag: String, message: String) {
            Log.d(tag, message)
        }

        override fun logVerbose(tag: String, message: String) {
            Log.v(tag, message)
        }

        override fun logStackTraceWithMessage(tag: String, message: String, e: Exception) {
            Log.e(tag, message, e)
        }

        override fun logStackTrace(tag: String, e: Exception) {
            Log.e(tag, "", e)
        }
    }

    private val session = TerminalSession(
        "/system/bin/sh",
        "/",
        arrayOf("-c", "cat"),
        arrayOf("TERM=xterm-256color", "LANG=C.UTF-8"),
        10000,
        sessionClient,
    )

    private val viewClient = object : TerminalViewClient {
        override fun onScale(scale: Float): Float {
            if (scale < 0.97f || scale > 1.03f) {
                updateFontSize(
                    if (scale > 1f) fontSizePx + fontStepPx else fontSizePx - fontStepPx,
                    manual = true
                )
                return 1.0f
            }
            return scale
        }

        override fun onSingleTapUp(e: MotionEvent) {
            terminalView?.requestFocus()
        }

        override fun shouldBackButtonBeMappedToEscape(): Boolean = false

        override fun shouldEnforceCharBasedInput(): Boolean = false

        override fun shouldUseCtrlSpaceWorkaround(): Boolean = false

        override fun isTerminalViewSelected(): Boolean = true

        override fun copyModeChanged(copyMode: Boolean) {
            // No-op
        }

        override fun onKeyDown(keyCode: Int, e: KeyEvent, session: TerminalSession): Boolean {
            val emulator = session.emulator ?: return true
            val keyMod = buildKeyMod(e)
            val code = KeyHandler.getCode(
                keyCode,
                keyMod,
                emulator.isCursorKeysApplicationMode,
                emulator.isKeypadApplicationMode,
            )
            if (code != null) {
                sendInput(code)
                return true
            }

            val chars = e.characters
            if (!chars.isNullOrEmpty()) {
                sendInput(chars)
                return true
            }

            val unicode = e.unicodeChar
            if (unicode != 0) {
                sendInput(String(Character.toChars(unicode)))
            }
            return true
        }

        override fun onKeyUp(keyCode: Int, e: KeyEvent): Boolean = true

        override fun onLongPress(event: MotionEvent): Boolean = false

        override fun readControlKey(): Boolean = false

        override fun readAltKey(): Boolean = false

        override fun readShiftKey(): Boolean = false

        override fun readFnKey(): Boolean = false

        override fun onCodePoint(codePoint: Int, ctrlDown: Boolean, session: TerminalSession): Boolean {
            val out = if (ctrlDown) {
                val ctrl = codePoint and 0x1f
                ctrl.toChar().toString()
            } else {
                String(Character.toChars(codePoint))
            }
            sendInput(out)
            return true
        }

        override fun onEmulatorSet() {
            flushPendingOutput()
        }

        override fun logError(tag: String, message: String) {
            Log.e(tag, message)
        }

        override fun logWarn(tag: String, message: String) {
            Log.w(tag, message)
        }

        override fun logInfo(tag: String, message: String) {
            Log.i(tag, message)
        }

        override fun logDebug(tag: String, message: String) {
            Log.d(tag, message)
        }

        override fun logVerbose(tag: String, message: String) {
            Log.v(tag, message)
        }

        override fun logStackTraceWithMessage(tag: String, message: String, e: Exception) {
            Log.e(tag, message, e)
        }

        override fun logStackTrace(tag: String, e: Exception) {
            Log.e(tag, "", e)
        }
    }

    fun createView(): TerminalView {
        val view = TerminalView(context, null)
        view.setTerminalViewClient(viewClient)
        view.setBackgroundColor(Color.BLACK)
        view.setTextSize(fontSizePx)
        view.attachSession(session)
        view.isFocusable = true
        view.isFocusableInTouchMode = true
        terminalView = view
        view.addOnLayoutChangeListener { v, _, _, _, _, _, _, _, _ ->
            maybeApplyAutoFit(v)
        }
        view.post { maybeApplyAutoFit(view) }
        return view
    }

    fun increaseFontSize() {
        updateFontSize(fontSizePx + fontStepPx, manual = true)
    }

    fun decreaseFontSize() {
        updateFontSize(fontSizePx - fontStepPx, manual = true)
    }

    fun setAutoFit(enabled: Boolean) {
        autoFitEnabled = enabled
        prefs.edit().putBoolean(KEY_AUTO_FIT, autoFitEnabled).apply()
        if (autoFitEnabled) {
            terminalView?.let { maybeApplyAutoFit(it) }
        }
    }

    fun isAutoFitEnabled(): Boolean = autoFitEnabled

    fun sendKeySequence(sequence: String) {
        if (sequence.isNotEmpty()) {
            sendInput(sequence)
        }
    }

    fun writeOutput(text: String) {
        val view = terminalView
        if (view == null) {
            synchronized(pendingOutput) {
                pendingOutput.append(text)
            }
            return
        }
        view.post {
            val emulator: TerminalEmulator? = session.emulator
            if (emulator == null) {
                synchronized(pendingOutput) {
                    pendingOutput.append(text)
                }
                return@post
            }
            val bytes = text.toByteArray(Charsets.UTF_8)
            emulator.append(bytes, bytes.size)
            view.invalidate()
        }
    }

    fun dispose() {
        session.finishIfRunning()
    }

    private fun flushPendingOutput() {
        val buffered = synchronized(pendingOutput) {
            if (pendingOutput.isEmpty()) return
            val text = pendingOutput.toString()
            pendingOutput.clear()
            text
        }
        writeOutput(buffered)
    }

    private fun buildKeyMod(e: KeyEvent): Int {
        var mod = 0
        if (e.isCtrlPressed) mod = mod or KeyHandler.KEYMOD_CTRL
        if (e.isAltPressed) mod = mod or KeyHandler.KEYMOD_ALT
        if (e.isShiftPressed) mod = mod or KeyHandler.KEYMOD_SHIFT
        return mod
    }

    private fun updateFontSize(newSizePx: Int, manual: Boolean) {
        fontSizePx = newSizePx.coerceIn(minFontPx, maxFontPx)
        if (manual && autoFitEnabled) {
            autoFitEnabled = false
            prefs.edit().putBoolean(KEY_AUTO_FIT, false).apply()
        }
        prefs.edit().putInt(KEY_FONT_SIZE, fontSizePx).apply()
        terminalView?.let { view ->
            view.setTextSize(fontSizePx)
            view.invalidate()
        }
    }

    private fun maybeApplyAutoFit(view: View) {
        if (!autoFitEnabled) return
        if (view.width <= 0) return
        val target = computeFontSizeForColumns(view.width, autoFitColumns)
        if (target != fontSizePx) {
            updateFontSize(target, manual = false)
        }
    }

    private fun computeFontSizeForColumns(widthPx: Int, columns: Int): Int {
        var low = minFontPx
        var high = maxFontPx
        var best = minFontPx
        while (low <= high) {
            val mid = (low + high) / 2
            measurePaint.textSize = mid.toFloat()
            val charWidth = measurePaint.measureText("X").coerceAtLeast(1f)
            val fit = (widthPx / charWidth).toInt()
            if (fit >= columns) {
                best = mid
                low = mid + 1
            } else {
                high = mid - 1
            }
        }
        return best
    }

    companion object {
        private const val KEY_FONT_SIZE = "terminal_font_px"
        private const val KEY_AUTO_FIT = "terminal_auto_fit"
    }
}

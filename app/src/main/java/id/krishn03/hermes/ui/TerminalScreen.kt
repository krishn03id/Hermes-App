package id.krishn03.hermes.ui

import android.content.Context
import android.os.SystemClock
import android.util.Log
import android.view.KeyEvent
import android.view.MotionEvent
import android.view.View
import android.view.ViewGroup
import android.view.inputmethod.InputMethodManager
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.KeyboardArrowDown
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import com.termux.terminal.TerminalSession
import com.termux.terminal.TerminalSessionClient
import com.termux.view.TerminalView
import com.termux.view.TerminalViewClient
import id.krishn03.hermes.terminal.TermuxBootstrap

private enum class TermPhase { CHOOSE, INSTALLING, BOOTSTRAP, BASIC }

/**
 * A real PTY terminal, embedded via Termux's terminal-view + terminal-emulator.
 * On first open it offers to install a Termux bootstrap (bash, apt, pkg, ~250
 * tools) into the app sandbox; you can also skip to a basic system shell.
 */
@Composable
fun TerminalScreen(onBack: () -> Unit) {
    val context = LocalContext.current
    var phase by remember {
        mutableStateOf(if (TermuxBootstrap.isInstalled(context)) TermPhase.BOOTSTRAP else TermPhase.CHOOSE)
    }

    when (phase) {
        TermPhase.CHOOSE -> InstallGate(
            onInstall = { phase = TermPhase.INSTALLING },
            onSkip = { phase = TermPhase.BASIC },
            onBack = onBack,
        )
        TermPhase.INSTALLING -> InstallProgress(
            onDone = { phase = TermPhase.BOOTSTRAP },
            onFailed = { phase = TermPhase.CHOOSE },
            onBack = onBack,
        )
        TermPhase.BOOTSTRAP -> TerminalContent(onBack = onBack, useBootstrap = true)
        TermPhase.BASIC -> TerminalContent(onBack = onBack, useBootstrap = false)
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun InstallGate(onInstall: () -> Unit, onSkip: () -> Unit, onBack: () -> Unit) {
    TermScaffold("Terminal", onBack) { padding ->
        Column(
            Modifier.fillMaxSize().padding(padding).padding(28.dp),
            verticalArrangement = Arrangement.Center,
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            Text(
                "Set up the terminal",
                style = MaterialTheme.typography.headlineSmall,
                fontFamily = FontFamily.Monospace,
                fontWeight = FontWeight.SemiBold,
            )
            Spacer(Modifier.height(12.dp))
            Text(
                "Install a Linux environment (bash, apt, pkg, and ~250 tools) into " +
                    "Hermes' private storage. This downloads ~30 MB once. No root needed.",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                textAlign = TextAlign.Center,
            )
            Spacer(Modifier.height(24.dp))
            Button(onClick = onInstall) { Text("Install environment") }
            Spacer(Modifier.height(8.dp))
            TextButton(onClick = onSkip) { Text("Skip — use basic system shell") }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun InstallProgress(onDone: () -> Unit, onFailed: () -> Unit, onBack: () -> Unit) {
    val context = LocalContext.current
    var status by remember { mutableStateOf("Starting…") }
    var error by remember { mutableStateOf<String?>(null) }

    LaunchedEffect(Unit) {
        runCatching { TermuxBootstrap.install(context) { status = it } }
            .onSuccess { onDone() }
            .onFailure { error = it.message ?: "Install failed" }
    }

    TermScaffold("Installing", onBack) { padding ->
        Column(
            Modifier.fillMaxSize().padding(padding).padding(28.dp),
            verticalArrangement = Arrangement.Center,
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            if (error == null) {
                CircularProgressIndicator()
                Spacer(Modifier.height(20.dp))
                Text(status, color = MaterialTheme.colorScheme.onSurfaceVariant)
            } else {
                Text(
                    "Install failed",
                    style = MaterialTheme.typography.titleMedium,
                    color = MaterialTheme.colorScheme.error,
                )
                Spacer(Modifier.height(8.dp))
                Text(
                    error!!,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    textAlign = TextAlign.Center,
                )
                Spacer(Modifier.height(20.dp))
                Button(onClick = onFailed) { Text("Back") }
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun TermScaffold(
    title: String,
    onBack: () -> Unit,
    content: @Composable (androidx.compose.foundation.layout.PaddingValues) -> Unit,
) {
    Scaffold(
        containerColor = MaterialTheme.colorScheme.background,
        topBar = {
            TopAppBar(
                title = { Text(title, fontFamily = FontFamily.Monospace) },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.background,
                ),
            )
        },
        content = content,
    )
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun TerminalContent(onBack: () -> Unit, useBootstrap: Boolean) {
    val context = LocalContext.current
    val logTag = "HermesTerminal"

    // Shared one-shot Ctrl/Alt/Shift state (extra-keys row <-> view client).
    val mods = remember { TermModifiers() }
    val viewHolder = remember { object { var view: TerminalView? = null } }

    val session = remember {
        val sessionClient = object : TerminalSessionClient {
            override fun onTextChanged(changedSession: TerminalSession) {
                viewHolder.view?.onScreenUpdated()
            }
            override fun onTitleChanged(changedSession: TerminalSession) {}
            override fun onSessionFinished(finishedSession: TerminalSession) {}
            override fun onCopyTextToClipboard(session: TerminalSession, text: String?) {}
            override fun onPasteTextFromClipboard(session: TerminalSession?) {}
            override fun onBell(session: TerminalSession) {}
            override fun onColorsChanged(session: TerminalSession) {}
            override fun onTerminalCursorStateChange(state: Boolean) {}
            override fun getTerminalCursorStyle(): Int? = null
            override fun logError(tag: String?, message: String?) { Log.e(logTag, "$message") }
            override fun logWarn(tag: String?, message: String?) { Log.w(logTag, "$message") }
            override fun logInfo(tag: String?, message: String?) { Log.i(logTag, "$message") }
            override fun logDebug(tag: String?, message: String?) { Log.d(logTag, "$message") }
            override fun logVerbose(tag: String?, message: String?) { Log.v(logTag, "$message") }
            override fun logStackTraceWithMessage(tag: String?, message: String?, e: Exception?) {
                Log.e(logTag, "$message", e)
            }
            override fun logStackTrace(tag: String?, e: Exception?) { Log.e(logTag, "", e) }
        }
        if (useBootstrap) {
            TerminalSession(
                TermuxBootstrap.linkerPath(),
                TermuxBootstrap.home(context).absolutePath,
                TermuxBootstrap.launchArgs(context),
                TermuxBootstrap.buildEnv(context),
                2000,
                sessionClient,
            )
        } else {
            val home = context.filesDir.absolutePath
            TerminalSession(
                "/system/bin/sh",
                home,
                arrayOf("/system/bin/sh"),
                arrayOf(
                    "HOME=$home",
                    "TMPDIR=${context.cacheDir.absolutePath}",
                    "PATH=/system/bin:/system/xbin",
                    "TERM=xterm-256color",
                    "LANG=en_US.UTF-8",
                ),
                2000,
                sessionClient,
            )
        }
    }

    DisposableEffect(Unit) {
        onDispose {
            session.finishIfRunning()
            viewHolder.view = null
        }
    }

    Scaffold(
        containerColor = MaterialTheme.colorScheme.background,
        topBar = {
            TopAppBar(
                title = { Text("Terminal", fontFamily = FontFamily.Monospace) },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
                    }
                },
                actions = {
                    IconButton(onClick = { viewHolder.view?.let { showKeyboard(it) } }) {
                        Icon(Icons.Filled.KeyboardArrowDown, contentDescription = "Keyboard")
                    }
                    IconButton(onClick = { session.write("\u0003") }) {
                        Icon(Icons.Filled.Close, contentDescription = "Send Ctrl+C")
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.background,
                ),
            )
        },
    ) { padding ->
        Column(Modifier.fillMaxSize().padding(padding)) {
            AndroidView(
                modifier = Modifier.fillMaxWidth().weight(1f),
                factory = { ctx ->
                    TerminalView(ctx, null).apply {
                        viewHolder.view = this
                        setTerminalViewClient(HermesTerminalViewClient(this, mods, logTag, fontSize = 38))
                        setTextSize(38)
                        keepScreenOn = true
                        isFocusable = true
                        isFocusableInTouchMode = true
                        layoutParams = ViewGroup.LayoutParams(
                            ViewGroup.LayoutParams.MATCH_PARENT,
                            ViewGroup.LayoutParams.MATCH_PARENT,
                        )
                        attachSession(session)
                        post { showKeyboard(this) }
                    }
                },
            )
            ExtraKeysRow(
                mods = mods,
                onKey = { keyCode -> viewHolder.view?.let { dispatchKey(it, keyCode); mods.consume() } },
                onText = { s -> session.write(s); mods.consume() },
            )
        }
    }
}

/** One-shot Ctrl/Alt/Shift toggles shared between the extra-keys row and client. */
private class TermModifiers {
    var ctrl by mutableStateOf(false)
    var alt by mutableStateOf(false)
    var shift by mutableStateOf(false)
    fun consume() { ctrl = false; alt = false; shift = false }
}

private fun showKeyboard(view: View) {
    view.requestFocus()
    val imm = view.context.getSystemService(Context.INPUT_METHOD_SERVICE) as InputMethodManager
    imm.showSoftInput(view, InputMethodManager.SHOW_IMPLICIT)
}

private fun dispatchKey(view: TerminalView, keyCode: Int) {
    val t = SystemClock.uptimeMillis()
    view.dispatchKeyEvent(KeyEvent(t, t, KeyEvent.ACTION_DOWN, keyCode, 0))
    view.dispatchKeyEvent(KeyEvent(t, t, KeyEvent.ACTION_UP, keyCode, 0))
}

/** Termux-style extra-keys: ESC/CTRL/ALT/SHIFT/TAB, arrows, HOME/END/PGUP/PGDN, symbols. */
@Composable
private fun ExtraKeysRow(mods: TermModifiers, onKey: (Int) -> Unit, onText: (String) -> Unit) {
    Surface(color = MaterialTheme.colorScheme.surfaceContainer) {
        Column(Modifier.fillMaxWidth().padding(vertical = 6.dp)) {
            Row(
                Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()).padding(horizontal = 6.dp),
                horizontalArrangement = Arrangement.spacedBy(6.dp),
            ) {
                KeyCap("ESC") { onKey(KeyEvent.KEYCODE_ESCAPE) }
                KeyCap("TAB") { onKey(KeyEvent.KEYCODE_TAB) }
                KeyCap("CTRL", active = mods.ctrl) { mods.ctrl = !mods.ctrl }
                KeyCap("ALT", active = mods.alt) { mods.alt = !mods.alt }
                KeyCap("⇧", active = mods.shift) { mods.shift = !mods.shift }
                KeyCap("-") { onText("-") }
                KeyCap("/") { onText("/") }
                KeyCap("|") { onText("|") }
                KeyCap("~") { onText("~") }
            }
            Row(
                Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()).padding(horizontal = 6.dp, vertical = 6.dp),
                horizontalArrangement = Arrangement.spacedBy(6.dp),
            ) {
                KeyCap("←") { onKey(KeyEvent.KEYCODE_DPAD_LEFT) }
                KeyCap("↑") { onKey(KeyEvent.KEYCODE_DPAD_UP) }
                KeyCap("↓") { onKey(KeyEvent.KEYCODE_DPAD_DOWN) }
                KeyCap("→") { onKey(KeyEvent.KEYCODE_DPAD_RIGHT) }
                KeyCap("HOME") { onKey(KeyEvent.KEYCODE_MOVE_HOME) }
                KeyCap("END") { onKey(KeyEvent.KEYCODE_MOVE_END) }
                KeyCap("PGUP") { onKey(KeyEvent.KEYCODE_PAGE_UP) }
                KeyCap("PGDN") { onKey(KeyEvent.KEYCODE_PAGE_DOWN) }
            }
        }
    }
}

@Composable
private fun KeyCap(label: String, active: Boolean = false, onClick: () -> Unit) {
    val bg = if (active) MaterialTheme.colorScheme.primary
    else MaterialTheme.colorScheme.surfaceContainerHighest
    val fg = if (active) MaterialTheme.colorScheme.onPrimary
    else MaterialTheme.colorScheme.onSurface
    Box(
        modifier = Modifier
            .size(width = 52.dp, height = 38.dp)
            .background(bg, RoundedCornerShape(8.dp))
            .clickable(onClick = onClick),
        contentAlignment = Alignment.Center,
    ) {
        Text(label, color = fg, fontSize = 14.sp, fontWeight = FontWeight.Medium)
    }
}

/**
 * Minimal [TerminalViewClient]: shows the IME on tap, reports the extra-keys
 * modifier toggles, and resets one-shot modifiers after a typed code point.
 */
private class HermesTerminalViewClient(
    private val view: TerminalView,
    private val mods: TermModifiers,
    private val logTag: String,
    fontSize: Int,
) : TerminalViewClient {
    private var fontSize: Int = fontSize
    override fun onScale(scale: Float): Float {
        if (scale < 0.9f || scale > 1.1f) {
            fontSize = (fontSize * scale).toInt().coerceIn(12, 96)
            view.setTextSize(fontSize)
        }
        return fontSize.toFloat()
    }
    override fun onSingleTapUp(e: MotionEvent?) { showKeyboard(view) }
    override fun shouldBackButtonBeMappedToEscape(): Boolean = false
    override fun shouldEnforceCharBasedInput(): Boolean = true
    override fun shouldUseCtrlSpaceWorkaround(): Boolean = false
    override fun isTerminalViewSelected(): Boolean = true
    override fun copyModeChanged(copyMode: Boolean) {}
    override fun onKeyDown(keyCode: Int, e: KeyEvent?, session: TerminalSession?): Boolean = false
    override fun onKeyUp(keyCode: Int, e: KeyEvent?): Boolean = false
    override fun onLongPress(event: MotionEvent?): Boolean = false
    override fun readControlKey(): Boolean = mods.ctrl
    override fun readAltKey(): Boolean = mods.alt
    override fun readShiftKey(): Boolean = mods.shift
    override fun readFnKey(): Boolean = false
    override fun onCodePoint(codePoint: Int, ctrlDown: Boolean, session: TerminalSession?): Boolean {
        mods.consume()
        return false
    }
    override fun onEmulatorSet() {}
    override fun logError(tag: String?, message: String?) { Log.e(logTag, "$message") }
    override fun logWarn(tag: String?, message: String?) { Log.w(logTag, "$message") }
    override fun logInfo(tag: String?, message: String?) { Log.i(logTag, "$message") }
    override fun logDebug(tag: String?, message: String?) { Log.d(logTag, "$message") }
    override fun logVerbose(tag: String?, message: String?) { Log.v(logTag, "$message") }
    override fun logStackTraceWithMessage(tag: String?, message: String?, e: Exception?) {
        Log.e(logTag, "$message", e)
    }
    override fun logStackTrace(tag: String?, e: Exception?) { Log.e(logTag, "", e) }
}

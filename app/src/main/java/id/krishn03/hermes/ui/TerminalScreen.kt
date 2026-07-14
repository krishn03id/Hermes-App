package id.krishn03.hermes.ui

import android.util.Log
import android.view.KeyEvent
import android.view.MotionEvent
import android.view.ViewGroup
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Close
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.viewinterop.AndroidView
import com.termux.terminal.TerminalSession
import com.termux.terminal.TerminalSessionClient
import com.termux.view.TerminalView
import com.termux.view.TerminalViewClient

/**
 * A real PTY terminal, embedded via Termux's terminal-view + terminal-emulator.
 * Runs the device's system shell inside Hermes' app sandbox (no root): you get
 * `/system/bin/sh`, coreutils, your app's files — but not Termux's apt packages,
 * which live in the standalone Termux app's own bootstrap.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun TerminalScreen(onBack: () -> Unit) {
    val context = LocalContext.current
    val logTag = "HermesTerminal"

    // Holds the view once created so session callbacks (which arrive on the
    // main thread) can trigger repaints. The session client is built before the
    // view exists, hence the indirection.
    val viewHolder = remember { object { var view: TerminalView? = null } }

    // The session owns the shell subprocess; created once, torn down on exit.
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
        val home = context.filesDir.absolutePath
        val env = arrayOf(
            "HOME=$home",
            "TMPDIR=${context.cacheDir.absolutePath}",
            "PATH=/system/bin:/system/xbin",
            "TERM=xterm-256color",
            "LANG=en_US.UTF-8",
        )
        TerminalSession(
            /* shellPath   = */ "/system/bin/sh",
            /* cwd         = */ home,
            /* args        = */ arrayOf("/system/bin/sh"),
            /* env         = */ env,
            /* transcriptRows = */ 2000,
            /* client      = */ sessionClient,
        )
    }

    // Kill the shell when leaving the screen.
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
                    // Send an interrupt (Ctrl+C, ETX) to the foreground process.
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
        AndroidView(
            modifier = Modifier.fillMaxSize().padding(padding),
            factory = { ctx ->
                TerminalView(ctx, null).apply {
                    viewHolder.view = this
                    setTerminalViewClient(HermesTerminalViewClient(this, logTag, fontSize = 38))
                    setTextSize(38)
                    keepScreenOn = true
                    layoutParams = ViewGroup.LayoutParams(
                        ViewGroup.LayoutParams.MATCH_PARENT,
                        ViewGroup.LayoutParams.MATCH_PARENT,
                    )
                    // Attaching starts the shell once the view gets a size.
                    attachSession(session)
                    requestFocus()
                }
            },
        )
    }
}

/**
 * Minimal [TerminalViewClient]: returns false from the input hooks so the
 * TerminalView performs its own default handling (writing to the session),
 * and routes logging to logcat. No extra keys row / modifiers.
 */
private class HermesTerminalViewClient(
    private val view: TerminalView,
    private val logTag: String,
    fontSize: Int,
) : TerminalViewClient {
    // Track font size ourselves (TerminalView has no getter) to drive pinch-zoom.
    private var fontSize: Int = fontSize
    override fun onScale(scale: Float): Float {
        if (scale < 0.9f || scale > 1.1f) {
            fontSize = (fontSize * scale).toInt().coerceIn(12, 96)
            view.setTextSize(fontSize)
        }
        return fontSize.toFloat()
    }
    override fun onSingleTapUp(e: MotionEvent?) { view.requestFocus() }
    override fun shouldBackButtonBeMappedToEscape(): Boolean = false
    override fun shouldEnforceCharBasedInput(): Boolean = true
    override fun shouldUseCtrlSpaceWorkaround(): Boolean = false
    override fun isTerminalViewSelected(): Boolean = true
    override fun copyModeChanged(copyMode: Boolean) {}
    override fun onKeyDown(keyCode: Int, e: KeyEvent?, session: TerminalSession?): Boolean = false
    override fun onKeyUp(keyCode: Int, e: KeyEvent?): Boolean = false
    override fun onLongPress(event: MotionEvent?): Boolean = false
    override fun readControlKey(): Boolean = false
    override fun readAltKey(): Boolean = false
    override fun readShiftKey(): Boolean = false
    override fun readFnKey(): Boolean = false
    override fun onCodePoint(codePoint: Int, ctrlDown: Boolean, session: TerminalSession?): Boolean = false
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

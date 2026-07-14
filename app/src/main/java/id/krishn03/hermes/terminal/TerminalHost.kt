package id.krishn03.hermes.terminal

import android.content.Context
import com.termux.terminal.TerminalSession
import com.termux.terminal.TerminalSessionClient
import com.termux.view.TerminalView

/**
 * Process-scoped holder for the one live terminal session, so it survives
 * leaving and re-entering the Terminal screen (back button no longer kills the
 * shell). The PTY is inherently a single process-global resource, so a plain
 * object is the right scope: it lives until the app process dies or the user
 * explicitly ends the session.
 */
object TerminalHost {

    /** The live shell, or null if none is running. */
    var session: TerminalSession? = null
        private set

    /** Whether [session] was launched with the Termux bootstrap. */
    var isBootstrap: Boolean = false
        private set

    /** Remembered font size (px) so zoom persists across re-entry. */
    var fontSize: Int = 38

    /**
     * The currently attached view. The session client repaints through this;
     * re-attaching a session to a fresh view just updates this reference.
     */
    var view: TerminalView? = null

    fun isAlive(): Boolean = session != null

    /** Returns the live session, creating one if none exists. If a session is
     *  alive but was launched in a different mode than requested, it's ended
     *  and replaced so the caller always gets the mode it asked for. */
    fun getOrCreate(context: Context, useBootstrap: Boolean): TerminalSession {
        session?.let { existing ->
            if (isBootstrap == useBootstrap) return existing
            existing.finishIfRunning()
            clear()
        }
        val client = object : TerminalSessionClient {
            override fun onTextChanged(changedSession: TerminalSession) { view?.onScreenUpdated() }
            override fun onTitleChanged(changedSession: TerminalSession) {}
            // Shell exited on its own (e.g. `exit`). Forget the session, but
            // leave the still-mounted view alone — the screen's onDispose owns it.
            override fun onSessionFinished(finishedSession: TerminalSession) {
                session = null
                isBootstrap = false
            }
            override fun onCopyTextToClipboard(session: TerminalSession, text: String?) {}
            override fun onPasteTextFromClipboard(session: TerminalSession?) {}
            override fun onBell(session: TerminalSession) {}
            override fun onColorsChanged(session: TerminalSession) {}
            override fun onTerminalCursorStateChange(state: Boolean) {}
            override fun getTerminalCursorStyle(): Int? = null
            override fun logError(tag: String?, message: String?) {}
            override fun logWarn(tag: String?, message: String?) {}
            override fun logInfo(tag: String?, message: String?) {}
            override fun logDebug(tag: String?, message: String?) {}
            override fun logVerbose(tag: String?, message: String?) {}
            override fun logStackTraceWithMessage(tag: String?, message: String?, e: Exception?) {}
            override fun logStackTrace(tag: String?, e: Exception?) {}
        }
        val ctx = context.applicationContext
        val created = if (useBootstrap) {
            TerminalSession(
                TermuxBootstrap.linkerPath(),
                TermuxBootstrap.home(ctx).absolutePath,
                TermuxBootstrap.launchArgs(ctx),
                TermuxBootstrap.buildEnv(ctx),
                2000,
                client,
            )
        } else {
            val home = ctx.filesDir.absolutePath
            TerminalSession(
                "/system/bin/sh",
                home,
                arrayOf("/system/bin/sh"),
                arrayOf(
                    "HOME=$home",
                    "TMPDIR=${ctx.cacheDir.absolutePath}",
                    "PATH=/system/bin:/system/xbin",
                    "TERM=xterm-256color",
                    "LANG=en_US.UTF-8",
                ),
                2000,
                client,
            )
        }
        session = created
        isBootstrap = useBootstrap
        return created
    }

    /** Ends the shell and forgets it (used by the explicit "kill" action). */
    fun kill() {
        session?.finishIfRunning()
        clear()
    }

    private fun clear() {
        session = null
        view = null
    }
}

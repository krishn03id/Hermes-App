package id.krishn03.hermes.terminal

import android.content.Context
import android.os.Build
import android.system.Os
import android.util.Log
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import java.io.File
import java.util.zip.ZipInputStream

/**
 * Installs and launches a Termux bootstrap (bash, apt, dpkg, coreutils, ~250
 * tools) inside Hermes' app sandbox — no root, no separate Termux app.
 *
 * How it runs under our package (Termux hardcodes `/data/data/com.termux/...`):
 *  - The binaries' ELF interpreter is `/system/bin/linker64`, so they load fine.
 *  - Android (targetSdk 35) blocks execve() of app-data files, so the FIRST
 *    shell is launched as `linker64 <bash>` (the system-linker-exec workaround).
 *  - `libtermux-exec-ld-preload.so` (LD_PRELOAD) then rewrites child shebangs
 *    and interpreter paths from `com.termux` to our prefix, so `pkg`/`apt` work.
 *  - apt/dpkg are relocated to our prefix via APT_CONFIG + DPKG_ADMINDIR.
 */
object TermuxBootstrap {

    private const val TAG = "TermuxBootstrap"
    // Pinned bootstrap release ('+' must be %2B in the URL).
    private const val RELEASE =
        "https://github.com/termux/termux-packages/releases/download/bootstrap-2026.07.12-r1%2Bapt.android-7"

    fun rootfs(ctx: Context): File = ctx.filesDir            // .../files
    fun prefix(ctx: Context): File = File(rootfs(ctx), "usr") // .../files/usr
    fun home(ctx: Context): File = File(rootfs(ctx), "home")

    fun isInstalled(ctx: Context): Boolean =
        File(prefix(ctx), "bin/bash").exists()

    /** aarch64 / arm / x86_64 / i686, matching the bootstrap asset names. */
    private fun arch(): String = when (val abi = Build.SUPPORTED_ABIS.firstOrNull()) {
        "arm64-v8a" -> "aarch64"
        "armeabi-v7a" -> "arm"
        "x86_64" -> "x86_64"
        "x86" -> "i686"
        else -> throw RuntimeException("Unsupported CPU ABI: $abi")
    }

    private fun is64Bit(): Boolean = arch() == "aarch64" || arch() == "x86_64"

    /** The system linker used to exec app-data binaries (W^X workaround). */
    fun linkerPath(): String = if (is64Bit()) "/system/bin/linker64" else "/system/bin/linker"

    /**
     * Downloads + installs the bootstrap atomically. Safe to call repeatedly:
     * a leftover staging dir from a crashed install is cleared first (this is
     * the "bootstrap lock" that otherwise wedges a reinstall).
     */
    suspend fun install(ctx: Context, onStatus: (String) -> Unit) = withContext(Dispatchers.IO) {
        val prefix = prefix(ctx)
        val staging = File(rootfs(ctx), "usr-staging")
        // Clear any stale state from a previous interrupted install.
        staging.deleteRecursively()

        onStatus("Downloading bootstrap (${arch()})…")
        val zipFile = File(ctx.cacheDir, "bootstrap.zip")
        downloadTo("$RELEASE/bootstrap-${arch()}.zip", zipFile)

        onStatus("Extracting…")
        staging.mkdirs()
        val symlinks = ArrayList<Pair<String, String>>() // target -> linkPath
        ZipInputStream(zipFile.inputStream().buffered()).use { zin ->
            var entry = zin.nextEntry
            while (entry != null) {
                val name = entry.name
                if (name == "SYMLINKS.txt") {
                    zin.readBytes().decodeToString().lineSequence().forEach { line ->
                        val parts = line.split("←")
                        if (parts.size == 2) symlinks.add(parts[0] to parts[1])
                    }
                } else if (entry.isDirectory) {
                    File(staging, name).mkdirs()
                } else {
                    val out = File(staging, name)
                    out.parentFile?.mkdirs()
                    out.outputStream().buffered().use { zin.copyTo(it) }
                    out.setExecutable(true, false)
                    out.setReadable(true, false)
                }
                entry = zin.nextEntry
            }
        }
        zipFile.delete()

        onStatus("Linking…")
        symlinks.forEach { (target, linkRel) ->
            val link = File(staging, linkRel)
            link.parentFile?.mkdirs()
            runCatching {
                if (link.exists()) link.delete()
                Os.symlink(target, link.absolutePath)
            }.onFailure { Log.w(TAG, "symlink $linkRel -> $target failed: ${it.message}") }
        }

        onStatus("Finalising…")
        prefix.deleteRecursively()
        if (!staging.renameTo(prefix)) {
            throw RuntimeException("Could not move bootstrap into place")
        }
        home(ctx).mkdirs()
        File(prefix, "tmp").mkdirs()
        writeReloc(ctx)
        writeStorageScript(ctx)
        onStatus("Ready")
    }

    private fun downloadTo(url: String, dest: File) {
        val client = OkHttpClient()
        val req = Request.Builder().url(url).build()
        client.newCall(req).execute().use { resp ->
            if (!resp.isSuccessful) throw RuntimeException("Download failed: HTTP ${resp.code}")
            val body = resp.body ?: throw RuntimeException("Empty download body")
            dest.outputStream().buffered().use { out -> body.byteStream().copyTo(out) }
        }
    }

    /** Env for launching the shell — mirrors Termux's own, plus apt/dpkg reloc. */
    fun buildEnv(ctx: Context): Array<String> {
        val prefix = prefix(ctx).absolutePath
        val rootfs = rootfs(ctx).absolutePath
        val dataDir = ctx.applicationInfo.dataDir // /data/data/id.krishn03.hermes
        return arrayOf(
            "PREFIX=$prefix",
            "HOME=${home(ctx).absolutePath}",
            "PATH=$prefix/bin:/system/bin:/system/xbin",
            "LD_LIBRARY_PATH=$prefix/lib",
            "LD_PRELOAD=$prefix/lib/libtermux-exec-ld-preload.so",
            "TMPDIR=$prefix/tmp",
            "TERM=xterm-256color",
            "LANG=en_US.UTF-8",
            "TERMUX__PREFIX=$prefix",
            "TERMUX__ROOTFS=$rootfs",
            "TERMUX_APP__DATA_DIR=$dataDir",
            "TERMUX_EXEC__SYSTEM_LINKER_EXEC__MODE=enable",
            "APT_CONFIG=$prefix/etc/apt/apt.conf",
            "DPKG_ADMINDIR=$prefix/var/lib/dpkg",
        )
    }

    /** argv for the first exec: linker64 loads bash (bypasses the exec block). */
    fun launchArgs(ctx: Context): Array<String> = arrayOf(
        linkerPath(),
        "${prefix(ctx).absolutePath}/bin/bash",
        "-l",
    )

    /** apt/dpkg data dirs live under com.termux by default — relocate them. */
    private fun writeReloc(ctx: Context) {
        val prefix = prefix(ctx).absolutePath
        val conf = File(prefix, "etc/apt/apt.conf")
        conf.parentFile?.mkdirs()
        conf.writeText(
            """
            Dir "$prefix";
            Dir::State "$prefix/var/lib/apt";
            Dir::State::status "$prefix/var/lib/dpkg/status";
            Dir::State::lists "$prefix/var/lib/apt/lists";
            Dir::Cache "$prefix/var/cache/apt";
            Dir::Cache::archives "$prefix/var/cache/apt/archives";
            Dir::Etc "$prefix/etc/apt";
            Dir::Etc::sourcelist "$prefix/etc/apt/sources.list";
            Dir::Etc::sourceparts "$prefix/etc/apt/sources.list.d";
            Dir::Etc::trusted "$prefix/etc/apt/trusted.gpg";
            Dir::Etc::trustedparts "$prefix/etc/apt/trusted.gpg.d";
            Dir::Log "$prefix/var/log/apt";
            Dir::Bin::methods "$prefix/lib/apt/methods";
            Dir::Bin::solvers "$prefix/lib/apt/solvers";
            Dir::Bin::planners "$prefix/lib/apt/planners";
            Dir::Bin::dpkg "$prefix/bin/dpkg";
            Dir::Bin::apt-key "$prefix/bin/apt-key";
            Dir::Bin::gzip "$prefix/bin/gzip";
            Dir::Bin::bzip2 "$prefix/bin/bzip2";
            Dir::Bin::xz "$prefix/bin/xz";
            Dir::Bin::lz4 "$prefix/bin/lz4";
            Dir::Bin::zstd "$prefix/bin/zstd";
            APT::Architecture "${dpkgArch()}";
            """.trimIndent() + "\n",
        )
    }

    private fun dpkgArch(): String = when (arch()) {
        "aarch64" -> "aarch64"
        "arm" -> "arm"
        "x86_64" -> "x86_64"
        else -> "i686"
    }

    /** Rewrites bundled config + helper scripts — call on launch so existing
     *  installs pick up the fixed apt.conf / scripts without a full reinstall. */
    fun refreshScripts(ctx: Context) {
        if (isInstalled(ctx)) runCatching {
            writeReloc(ctx)
            writeStorageScript(ctx)
        }
    }

    /** `hermes-setup-storage`: request storage access, then symlink ~/storage. */
    private fun writeStorageScript(ctx: Context) {
        val prefix = prefix(ctx).absolutePath
        val ext = ctx.getExternalFilesDir(null)?.absolutePath ?: "/sdcard"
        val pkg = ctx.packageName
        val script = File(prefix, "bin/hermes-setup-storage")
        script.parentFile?.mkdirs()
        script.writeText(
            """
            #!$prefix/bin/bash
            # Create ~/storage with links to shared + app-scoped storage.
            set -e

            if [ ! -r /sdcard ] || ! ls /sdcard >/dev/null 2>&1; then
                echo "No shared-storage access yet."
                echo "Tap the folder icon in the terminal's top bar to grant Hermes"
                echo "'All files access', then run hermes-setup-storage again."
                # Best-effort: also try to open the settings screen directly.
                am start -a android.settings.MANAGE_APP_ALL_FILES_ACCESS_PERMISSION \
                    -d "package:$pkg" >/dev/null 2>&1 || true
            fi

            mkdir -p "${'$'}HOME/storage"
            ln -sfn /sdcard "${'$'}HOME/storage/shared" 2>/dev/null || true
            ln -sfn /sdcard/Download "${'$'}HOME/storage/downloads" 2>/dev/null || true
            ln -sfn /sdcard/DCIM "${'$'}HOME/storage/dcim" 2>/dev/null || true
            ln -sfn /sdcard/Pictures "${'$'}HOME/storage/pictures" 2>/dev/null || true
            ln -sfn /sdcard/Music "${'$'}HOME/storage/music" 2>/dev/null || true
            ln -sfn /sdcard/Movies "${'$'}HOME/storage/movies" 2>/dev/null || true
            ln -sfn "$ext" "${'$'}HOME/storage/external" 2>/dev/null || true
            echo "Storage set up at ~/storage"
            """.trimIndent() + "\n",
        )
        script.setExecutable(true, false)
    }
}

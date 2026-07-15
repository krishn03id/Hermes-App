package id.krishn03.hermes.terminal

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class TermuxBootstrapTest {

    private val hermesPrefix = "/data/user/0/id.krishn03.hermes/files/usr"

    @Test
    fun `relocates the exact Termux prefix`() {
        assertEquals(
            hermesPrefix,
            TermuxBootstrap.relocateSymlinkTarget(
                "/data/data/com.termux/files/usr",
                hermesPrefix,
            ),
        )
    }

    @Test
    fun `relocates a path below the Termux prefix`() {
        assertEquals(
            "$hermesPrefix/share/termux-keyring/termux-autobuilds.gpg",
            TermuxBootstrap.relocateSymlinkTarget(
                "/data/data/com.termux/files/usr/share/termux-keyring/termux-autobuilds.gpg",
                hermesPrefix,
            ),
        )
    }

    @Test
    fun `does not rewrite unrelated or relative targets`() {
        assertEquals(
            "/data/data/com.termux/files/usr-local/bin/tool",
            TermuxBootstrap.relocateSymlinkTarget(
                "/data/data/com.termux/files/usr-local/bin/tool",
                hermesPrefix,
            ),
        )
        assertEquals(
            "../lib/libtool.so",
            TermuxBootstrap.relocateSymlinkTarget("../lib/libtool.so", hermesPrefix),
        )
    }

    @Test
    fun `trusts Termux APT keys but not the Pacman key`() {
        assertTrue(TermuxBootstrap.isAptKeyring("termux-autobuilds.gpg"))
        assertFalse(TermuxBootstrap.isAptKeyring("termux-pacman.gpg"))
        assertFalse(TermuxBootstrap.isAptKeyring("termux-autobuilds.asc"))
    }

    @Test
    fun `uses the bootstrap manifest's utility alias direction`() {
        assertEquals(
            mapOf(
                "bzcmp" to "bzdiff",
                "bzless" to "bzmore",
                "zipinfo" to "unzip",
            ),
            TermuxBootstrap.bootstrapBinAliases,
        )
    }

    @Test
    fun `apt key wrapper bypasses the package's hardcoded Termux shebang`() {
        val script = TermuxBootstrap.aptKeyWrapperScript(hermesPrefix)

        assertTrue(script.startsWith("#!$hermesPrefix/bin/bash\n"))
        assertTrue(script.contains("\"$hermesPrefix/bin/bash\" \"$hermesPrefix/bin/hermes-refresh-bootstrap-links\""))
        assertTrue(script.contains("exec \"$hermesPrefix/bin/bash\" \"$hermesPrefix/bin/apt-key\" \"${'$'}@\""))
    }

    @Test
    fun `dpkg wrapper relocates package paths and bypasses Android path checks`() {
        val installRoot = "/data/user/0/id.krishn03.hermes/files/dpkg-root"
        val script = TermuxBootstrap.dpkgWrapperScript(hermesPrefix, installRoot)

        assertTrue(script.contains("--force-bad-path --force-script-chrootless"))
        assertTrue(script.contains("--instdir=\"$installRoot\""))
        assertTrue(script.contains("s|${'$'}old_prefix|${'$'}prefix|g"))
        assertTrue(script.contains("!= \"$hermesPrefix/bin/hermes-dpkg\""))
        assertTrue(script.contains("\"${'$'}old_prefix\"|\"${'$'}old_prefix\"/*)"))
        assertTrue(script.contains("hermes-refresh-bootstrap-links\" || true"))
    }
}

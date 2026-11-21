package de.fgchamburg.vodhelper.core.tools

import java.io.IOException
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertTrue
import kotlin.time.Duration.Companion.seconds

class SystemProcessRunnerTest {
    private val java = System.getProperty("java.home") + "/bin/" +
        if (System.getProperty("os.name").startsWith("Windows")) "java.exe" else "java"

    @Test
    fun `runs a real process and captures output`() {
        val result = SystemProcessRunner().run(listOf(java, "-version"), 30.seconds)
        assertEquals(0, result.exitCode)
        assertTrue(result.output.contains("version"), result.output)
    }

    @Test
    fun `a missing program throws IOException`() {
        assertFailsWith<IOException> {
            SystemProcessRunner().run(listOf("/definitely/not/a/program"), 5.seconds)
        }
    }
}

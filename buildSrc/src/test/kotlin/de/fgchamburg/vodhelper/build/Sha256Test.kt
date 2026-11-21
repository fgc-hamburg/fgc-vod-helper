package de.fgchamburg.vodhelper.build

import org.gradle.api.GradleException
import java.io.File
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class Sha256Test {
    private val abc = "ba7816bf8f01cfea414140de5dae2223b00361a396177a9cb410ff61f20015ad"

    private fun abcFile(): File = File.createTempFile("abc", ".bin").apply { deleteOnExit(); writeText("abc") }

    @Test fun `hash of abc`() = assertEquals(abc, sha256Hex(abcFile()))

    @Test fun `matching hash keeps the file`() {
        val f = abcFile()
        verifySha256(f, abc)
        assertTrue(f.exists())
    }

    @Test fun `wrong hash throws and deletes the file`() {
        val f = abcFile()
        val e = assertFailsWith<GradleException> { verifySha256(f, "00") }
        assertTrue(e.message!!.contains(f.name))
        assertFalse(f.exists())
    }
}

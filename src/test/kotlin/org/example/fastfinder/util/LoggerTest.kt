package org.example.fastfinder.util

import org.junit.jupiter.api.io.TempDir
import java.nio.file.Files
import java.nio.file.Path
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class LoggerTest {

    @Test
    fun `leaves a small file alone`(@TempDir tempDir: Path) {
        val file = tempDir.resolve("fastfinder.log")
        Files.writeString(file, "small")

        Logger.rotateIfTooLarge(file, maxSizeBytes = 1024)

        assertEquals("small", Files.readString(file))
        assertFalse(Files.exists(file.resolveSibling("fastfinder.log.1")))
    }

    @Test
    fun `moves an oversized file to a backup and leaves nothing at the original path`(@TempDir tempDir: Path) {
        val file = tempDir.resolve("fastfinder.log")
        Files.writeString(file, "0123456789")

        Logger.rotateIfTooLarge(file, maxSizeBytes = 5)

        assertFalse(Files.exists(file), "The oversized file should have been moved out of the way")
        assertEquals("0123456789", Files.readString(file.resolveSibling("fastfinder.log.1")))
    }

    @Test
    fun `overwrites a pre-existing backup rather than keeping more than one generation`(@TempDir tempDir: Path) {
        val file = tempDir.resolve("fastfinder.log")
        val backup = file.resolveSibling("fastfinder.log.1")
        Files.writeString(backup, "stale backup")
        Files.writeString(file, "0123456789")

        Logger.rotateIfTooLarge(file, maxSizeBytes = 5)

        assertTrue(Files.exists(backup))
        assertEquals("0123456789", Files.readString(backup))
    }
}

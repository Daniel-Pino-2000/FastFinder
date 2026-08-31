package org.example.fastfinder.index

import org.junit.jupiter.api.io.TempDir
import java.io.File
import java.nio.file.Path
import kotlin.test.Test
import kotlin.test.assertEquals

/**
 * Guards the safety mechanism that keeps USN journal catch-up from ever touching a real volume
 * during a test: only a genuine filesystem root (no parent) counts, so the synthetic
 * subdirectory "roots" the indexing tests pass (to scope a walk to a small tree) are always
 * excluded, no matter what real drive they happen to sit on.
 */
class RealDriveLettersOfTest {

    @Test
    fun `a synthetic subdirectory root is not treated as a real drive`(@TempDir tempDir: Path) {
        val syntheticRoot = File(tempDir.toFile(), "root").apply { mkdirs() }

        assertEquals(emptyList(), realDriveLettersOf(listOf(syntheticRoot)))
    }

    @Test
    fun `an actual filesystem root is recognized by its drive letter`() {
        val actualRoot = File.listRoots().first()

        assertEquals(listOf(actualRoot.absolutePath.first().uppercaseChar()), realDriveLettersOf(listOf(actualRoot)))
    }

    @Test
    fun `duplicate roots on the same drive are deduplicated`() {
        val root = File.listRoots().first()

        assertEquals(1, realDriveLettersOf(listOf(root, root)).size)
    }
}

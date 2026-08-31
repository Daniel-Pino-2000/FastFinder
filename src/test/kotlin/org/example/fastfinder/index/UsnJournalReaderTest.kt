package org.example.fastfinder.index

import org.junit.jupiter.api.Assumptions.assumeTrue
import java.io.File
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/**
 * Exercises [UsnJournalReader] against the real C: volume's real USN journal - there's no way
 * to sandbox this, since the journal is inherently volume-global, not something scoped to a
 * temp directory. Reading it requires an elevated process (see [org.example.fastfinder.Elevation]),
 * so this test skips itself rather than failing when the current process isn't elevated - that's
 * the expected, normal case for CI and most local runs.
 */
class UsnJournalReaderTest {

    @Test
    fun `catchUp reports a file created after the checkpoint, with its correct path`() {
        assumeTrue(isRunningElevated(), "Requires an elevated process to read the USN journal - skipping.")

        val reader = UsnJournalReader('C')
        val (journalId, checkpointUsn) = reader.currentCheckpointOrNull()
            ?: run { assumeTrue(false, "No active USN journal on C: - skipping."); return }

        val testFile = File("C:/Users/Public/fastfinder_usn_reader_test_${System.nanoTime()}.txt")
        try {
            testFile.writeText("usn journal reader test")

            val outcome = reader.catchUp(journalId, checkpointUsn)
            check(outcome is CatchUpOutcome.Success) { "Expected a successful catch-up, got $outcome" }

            val change = outcome.changes.find { it.path.equals(testFile.absolutePath, ignoreCase = true) }
            assertTrue(change != null, "Expected a change for ${testFile.absolutePath}, got ${outcome.changes}")
            assertEquals(ChangeKind.CREATED, change.kind)
        } finally {
            testFile.delete()
        }
    }
}

/** Mirrors the check in [org.example.fastfinder.Elevation] - `net session` fails fast for a non-admin. */
private fun isRunningElevated(): Boolean = try {
    ProcessBuilder("net", "session")
        .redirectErrorStream(true)
        .redirectOutput(ProcessBuilder.Redirect.DISCARD)
        .start()
        .waitFor() == 0
} catch (e: java.io.IOException) {
    System.err.println("Could not determine elevation status via 'net session': ${e.message}")
    false
}

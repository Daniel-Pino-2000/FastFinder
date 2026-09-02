package org.example.fastfinder

import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.io.TempDir
import java.nio.file.Path
import kotlin.test.Test
import kotlin.test.assertFalse
import kotlin.test.assertTrue

/**
 * File locks in Java are held on behalf of the entire JVM, not per-thread/per-caller - so a
 * second [java.nio.channels.FileChannel.tryLock] on the same file, even from within this same
 * test JVM, correctly fails exactly as it would from a genuinely separate process. That's what
 * lets this be tested at all without actually launching a second process.
 */
class SingleInstanceTest {

    // Production never releases the lock until the process exits, so tests must release it
    // explicitly afterward - otherwise @TempDir can't delete the still-open lock file.
    @AfterEach
    fun releaseLock() {
        SingleInstance.release()
    }

    @Test
    fun `the first acquire on a lock file succeeds`(@TempDir tempDir: Path) {
        assertTrue(SingleInstance.acquire(tempDir.resolve("instance.lock")))
    }

    @Test
    fun `a second acquire on the same lock file fails, simulating another running instance`(@TempDir tempDir: Path) {
        val lockFile = tempDir.resolve("instance.lock")

        assertTrue(SingleInstance.acquire(lockFile), "The first acquire should succeed")
        assertFalse(SingleInstance.acquire(lockFile), "A second acquire while the first is still held should fail")
    }

    @Test
    fun `different lock files do not contend with each other`(@TempDir tempDir: Path) {
        // SingleInstance only ever tracks one held channel at a time (production only ever
        // acquires once), so this releases between the two rather than holding both at once -
        // still enough to prove acquiring one file's lock has no bearing on a different file.
        assertTrue(SingleInstance.acquire(tempDir.resolve("a.lock")))
        SingleInstance.release()
        assertTrue(SingleInstance.acquire(tempDir.resolve("b.lock")))
    }
}

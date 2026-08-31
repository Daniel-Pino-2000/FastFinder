package org.example.fastfinder

import org.example.fastfinder.util.AppPaths
import org.example.fastfinder.util.Logger
import java.io.IOException
import java.io.RandomAccessFile
import java.nio.channels.FileChannel
import java.nio.channels.OverlappingFileLockException
import java.nio.file.Files
import java.nio.file.Path

/**
 * Prevents more than one FastFinder instance from running at once.
 *
 * The elevation relaunch (see [Elevation]) makes it easy to accidentally end up with several
 * instances running concurrently - e.g. re-launching from an IDE without closing the previous
 * run triggers a brand new elevated relaunch every time, since nothing tracks whether one is
 * already up. Multiple instances independently indexing/watching the same on-disk index don't
 * coordinate at all: overlapping full rebuilds compete for the same disk I/O (and can overlap
 * with each other so badly that neither ever finishes at the previously-normal speed), and their
 * USN checkpoints and watchers step on each other.
 *
 * Uses an OS-level exclusive file lock rather than a "is a process with this name running"
 * check: the lock is released automatically the instant a process exits or is killed, with no
 * stale-lock file to clean up - a name/PID-based check would need to handle a leftover file from
 * a crashed process itself.
 */
object SingleInstance {
    private const val LOCK_FILE_NAME = "instance.lock"

    // Held for the process's entire lifetime; deliberately never explicitly closed/released in
    // production - closing it would release the lock while this process is still running. The
    // OS releases it when the process exits, however it exits.
    private var heldChannel: FileChannel? = null

    /** True if this process acquired the lock (no other instance is running) and should proceed. */
    fun acquire(lockFile: Path = AppPaths.root.resolve(LOCK_FILE_NAME)): Boolean {
        return try {
            Files.createDirectories(lockFile.parent)
            val channel = RandomAccessFile(lockFile.toFile(), "rw").channel
            // A second tryLock on the same file from within this same JVM (as opposed to a
            // genuinely separate process) throws rather than returning null - both mean the
            // same thing here: something already holds this lock.
            val lock = try {
                channel.tryLock()
            } catch (@Suppress("SwallowedException") e: OverlappingFileLockException) {
                // Expected, not an error: this specific exception type only ever means the
                // same thing tryLock() returning null means elsewhere - something already
                // holds this lock. Nothing about e is more informative than that.
                null
            }
            if (lock == null) {
                channel.close()
                false
            } else {
                heldChannel = channel
                true
            }
        } catch (e: IOException) {
            // Best-effort: a broken lock check shouldn't block startup entirely over it.
            Logger.warn("Could not check for another running instance: ${e.message}")
            true
        }
    }

    /** Test-only: releases whatever lock this process currently holds, so a test's @TempDir can clean up. */
    internal fun releaseForTesting() {
        heldChannel?.close()
        heldChannel = null
    }
}

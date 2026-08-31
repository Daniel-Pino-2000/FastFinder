package org.example.fastfinder

import org.example.fastfinder.util.Logger
import java.io.IOException
import java.util.concurrent.TimeUnit

private const val ELEVATED_RELAUNCH_FLAG = "--elevated-relaunch"
private const val RELAUNCH_TIMEOUT_SECONDS = 60L

/**
 * FastFinder always runs elevated so it can use the NTFS USN journal to catch up on filesystem
 * changes made while it was closed (see [org.example.fastfinder.index.UsnJournalReader]) -
 * opening a volume handle for that is restricted to administrators.
 *
 * Returns true if this process should continue starting the UI (it's already elevated, or
 * elevation wasn't possible and it's falling back to running without it); false if it just
 * launched an elevated relaunch of itself and this process should exit immediately instead,
 * so only one window ends up running.
 */
fun ensureElevated(args: Array<String>): Boolean {
    // Already relaunched once (or genuinely already elevated) - never try again either way,
    // so a persistent detection failure can't turn into an infinite relaunch loop.
    if (ELEVATED_RELAUNCH_FLAG in args || isRunningElevated()) return true

    Logger.info("Not running elevated; attempting to relaunch as Administrator.")
    val relaunched = relaunchElevated()
    if (!relaunched) {
        Logger.warn(
            "Could not relaunch elevated; continuing without Administrator privileges " +
                "(USN journal catch-up will be unavailable)."
        )
    }
    return !relaunched
}

/** `net session` fails fast for a non-admin and succeeds for an admin - a standard, well-known Windows check. */
private fun isRunningElevated(): Boolean = try {
    ProcessBuilder("net", "session")
        .redirectErrorStream(true)
        .redirectOutput(ProcessBuilder.Redirect.DISCARD)
        .start()
        .waitFor() == 0
} catch (e: IOException) {
    Logger.warn("Could not determine elevation status via 'net session': ${e.message}")
    false
}

/**
 * Relaunches this same process (same executable, same arguments, plus [ELEVATED_RELAUNCH_FLAG])
 * via PowerShell's `Start-Process -Verb RunAs`, which triggers the UAC consent prompt.
 *
 * Waits for that PowerShell invocation to finish (it returns as soon as the elevated process is
 * launched, or throws if the user declines UAC) rather than firing-and-forgetting - otherwise a
 * declined prompt would silently leave no FastFinder window open at all, and the user would see
 * nothing happen when they double-click the app.
 *
 * Only attempted when [command] is the packaged native launcher, not a bare `java`/`javaw` - an
 * IDE run configuration or `gradlew run` launches java.exe with a classpath that's sometimes a
 * temporary argfile/jar IntelliJ deletes once this (the original) process exits. Relaunching
 * that faithfully isn't reliable: `Start-Process` only confirms the child *started*, not that it
 * stayed running, so a relaunch that spawns and then immediately dies from a missing classpath
 * would still be reported as "succeeded" here - and by then this process has already exited too,
 * leaving no window open at all. The packaged launcher has no such fragility: it's a real,
 * self-contained executable, not java.exe plus a pile of easily-invalidated arguments.
 */
private fun relaunchElevated(): Boolean {
    val info = ProcessHandle.current().info()
    val command = info.command().orElse(null)
    if (command == null || isJavaLauncherExecutable(command)) {
        Logger.info(
            "No relaunchable command, or running via java(w).exe (IDE/gradle run) - skipping elevated relaunch."
        )
        return false
    }
    val relaunchArgs = info.arguments().orElse(emptyArray()).toList() + ELEVATED_RELAUNCH_FLAG

    return try {
        val quotedCommand = command.replace("'", "''")
        val quotedArgs = relaunchArgs.joinToString(",") { "'${it.replace("'", "''")}'" }
        val psCommand = "Start-Process -FilePath '$quotedCommand' -ArgumentList $quotedArgs -Verb RunAs"
        val process = ProcessBuilder("powershell", "-NoProfile", "-WindowStyle", "Hidden", "-Command", psCommand)
            .start()
        val finished = process.waitFor(RELAUNCH_TIMEOUT_SECONDS, TimeUnit.SECONDS)
        finished && process.exitValue() == 0
    } catch (e: IOException) {
        Logger.warn("Failed to launch elevated relaunch process: ${e.message}")
        false
    }
}

internal fun isJavaLauncherExecutable(command: String): Boolean =
    command.substringAfterLast('\\').lowercase() in setOf("java.exe", "javaw.exe")

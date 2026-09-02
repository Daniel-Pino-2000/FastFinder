package org.example.fastfinder

import org.example.fastfinder.util.Logger
import java.io.IOException
import java.nio.file.Paths
import java.util.concurrent.TimeUnit

private const val ELEVATED_RELAUNCH_FLAG = "--elevated-relaunch"
private const val RELAUNCH_TIMEOUT_SECONDS = 60L
private const val MAIN_CLASS = "org.example.fastfinder.MainKt"

/*
 * FastFinder runs unelevated by default; elevating (see [ensureElevated]) is opt-in, gated by the
 * user turning on "fast update tracking" in the settings rail (see FastFinderApp/FilterRail) so
 * it can use the NTFS USN journal to catch up on filesystem changes instantly instead of a full
 * rescan (see [org.example.fastfinder.index.UsnJournalReader]) - opening a volume handle for that
 * is restricted to administrators. Main.kt only calls [ensureElevated] at all when that
 * preference is already on (a returning user) or args carries [ELEVATED_RELAUNCH_FLAG] (this
 * process *is* the elevated relaunch); the settings toggle calls it directly, at runtime, the
 * first time a user opts in.
 */

/** True if [args] mark this process as the elevated relaunch of an earlier, unelevated one. */
internal fun isElevatedRelaunch(args: Array<String>): Boolean = ELEVATED_RELAUNCH_FLAG in args

/** Whether this process is currently running with Administrator privileges. */
internal fun isProcessElevated(): Boolean = isRunningElevated()

/**
 * Returns true if this process should continue starting the UI (it's already elevated, or
 * elevation wasn't possible and it's falling back to running without it); false if it just
 * launched an elevated relaunch of itself and this process should exit immediately instead,
 * so only one window ends up running.
 */
fun ensureElevated(args: Array<String>): Boolean {
    // Already relaunched once (or genuinely already elevated) - never try again either way,
    // so a persistent detection failure can't turn into an infinite relaunch loop.
    if (isElevatedRelaunch(args) || isRunningElevated()) return true

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
 * Relaunches this same app via PowerShell's `Start-Process -Verb RunAs`, which triggers the UAC
 * consent prompt.
 *
 * Waits for that PowerShell invocation to finish (it returns as soon as the elevated process is
 * launched, or throws if the user declines UAC) rather than firing-and-forgetting - otherwise a
 * declined prompt would silently leave no FastFinder window open at all, and the user would see
 * nothing happen when they double-click the app.
 *
 * The command to relaunch depends on how this process itself was launched (see
 * [buildRelaunchCommand]): the packaged native launcher's own argv is replayed as-is, since it's
 * a stable, self-contained executable, but an IDE run configuration or `gradlew run` launches
 * java.exe/javaw.exe with a classpath that's sometimes a temporary argfile/jar the IDE deletes
 * once this (the original) process exits - replaying that faithfully isn't reliable, so that
 * case is instead rebuilt from this running JVM's own already-resolved `java.class.path`, which
 * is never a reference to a temp file.
 */
private fun relaunchElevated(): Boolean {
    val (executable, relaunchArgs) = buildRelaunchCommand() ?: return false

    return try {
        val quotedCommand = executable.replace("'", "''")
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

/** The executable and arguments to relaunch with, or null if this process isn't relaunchable at all. */
internal fun buildRelaunchCommand(): Pair<String, List<String>>? {
    val info = ProcessHandle.current().info()
    val command = info.command().orElse(null)

    return if (command != null && !isJavaLauncherExecutable(command)) {
        // The packaged native launcher: a stable, self-contained executable, safe to replay as-is.
        val originalArgs = info.arguments().orElse(emptyArray()).toList()
        command to (originalArgs + ELEVATED_RELAUNCH_FLAG)
    } else {
        // An IDE run configuration or `gradlew run` (or a command we couldn't determine at all):
        // rebuild from this JVM's own already-resolved classpath instead of replaying the
        // original argv, which might point at a temporary argfile/jar the IDE deletes once this
        // process exits.
        val javaHome = System.getProperty("java.home")
        val classpath = System.getProperty("java.class.path")
        if (javaHome == null || classpath == null) {
            null
        } else {
            val javaExecutable = Paths.get(javaHome, "bin", "javaw.exe").toString()
            javaExecutable to listOf("-cp", classpath, MAIN_CLASS, ELEVATED_RELAUNCH_FLAG)
        }
    }
}

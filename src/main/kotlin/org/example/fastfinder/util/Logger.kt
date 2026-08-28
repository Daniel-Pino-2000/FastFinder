package org.example.fastfinder.util

import java.nio.file.Files
import java.nio.file.Path
import java.nio.file.StandardOpenOption
import java.time.LocalDateTime
import java.time.format.DateTimeFormatter

/**
 * Logs to stdout (useful when run from a console or IDE) and, best-effort, to a
 * log file under [AppPaths.root]. The file matters most for a packaged build:
 * launched from a desktop shortcut, there's no console for println to reach.
 */
object Logger {
    private const val MAX_LOG_SIZE_BYTES = 5L * 1024 * 1024

    private val timestampFormat = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss")
    private val logFile: Path? = runCatching {
        val logsDir = AppPaths.root.resolve("logs")
        Files.createDirectories(logsDir)
        logsDir.resolve("fastfinder.log")
    }.getOrNull()

    fun info(message: String) = log("INFO", message)
    fun warn(message: String) = log("WARN", message)

    fun error(message: String, throwable: Throwable? = null) {
        log("ERROR", message)
        throwable?.let { log("ERROR", it.stackTraceToString()) }
    }

    @Synchronized
    private fun log(level: String, message: String) {
        val line = "${LocalDateTime.now().format(timestampFormat)} [$level] $message"
        println(line)
        logFile?.let { file ->
            runCatching {
                rotateIfTooLarge(file)
                Files.writeString(
                    file,
                    line + System.lineSeparator(),
                    StandardOpenOption.CREATE, StandardOpenOption.APPEND
                )
            }
        }
    }

    /**
     * Keeps the log file from growing forever across a long-running app session: once it
     * crosses [maxSizeBytes], the current file is kept as a single ".1" backup (overwriting
     * whatever backup already existed) and logging continues into a fresh file.
     */
    internal fun rotateIfTooLarge(file: Path, maxSizeBytes: Long = MAX_LOG_SIZE_BYTES) {
        if (!Files.exists(file) || Files.size(file) < maxSizeBytes) return
        val backup = file.resolveSibling("${file.fileName}.1")
        Files.deleteIfExists(backup)
        Files.move(file, backup)
    }
}

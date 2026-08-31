package org.example.fastfinder.index

import org.example.fastfinder.util.AppPaths
import org.example.fastfinder.util.Logger
import java.io.IOException
import java.nio.file.Files
import java.nio.file.Path
import java.util.Properties

internal data class UsnCheckpoint(val journalId: Long, val usn: Long)

/**
 * Persists, per volume, the last USN journal position FastFinder has caught up to - so next
 * startup can ask the journal for only what changed since then, rather than a full rebuild.
 * Best-effort, like [org.example.fastfinder.util.AppPreferencesStore]: a missing or unreadable
 * file just means no volume has a usable checkpoint yet.
 */
internal object UsnCheckpointStore {
    private val defaultFile: Path = AppPaths.root.resolve("usn_checkpoints.properties")

    fun load(file: Path = defaultFile): Map<Char, UsnCheckpoint> {
        if (!Files.exists(file)) return emptyMap()
        val props = Properties()
        return try {
            Files.newBufferedReader(file).use(props::load)
            props.stringPropertyNames().mapNotNull { driveLetter ->
                val parts = props.getProperty(driveLetter)?.split(",")
                val journalId = parts?.getOrNull(0)?.toLongOrNull()
                val usn = parts?.getOrNull(1)?.toLongOrNull()
                if (driveLetter.length == 1 && journalId != null && usn != null) {
                    driveLetter[0] to UsnCheckpoint(journalId, usn)
                } else {
                    null
                }
            }.toMap()
        } catch (e: IOException) {
            Logger.warn("Could not read USN checkpoints, treating every volume as uncatchable-up: ${e.message}")
            emptyMap()
        }
    }

    fun save(checkpoints: Map<Char, UsnCheckpoint>, file: Path = defaultFile) {
        val props = Properties().apply {
            checkpoints.forEach { (driveLetter, checkpoint) ->
                setProperty(driveLetter.toString(), "${checkpoint.journalId},${checkpoint.usn}")
            }
        }
        try {
            Files.createDirectories(file.toAbsolutePath().parent)
            Files.newBufferedWriter(file).use { writer ->
                props.store(writer, "FastFinder USN journal checkpoints, per volume")
            }
        } catch (e: IOException) {
            Logger.warn("Could not save USN checkpoints: ${e.message}")
        }
    }
}

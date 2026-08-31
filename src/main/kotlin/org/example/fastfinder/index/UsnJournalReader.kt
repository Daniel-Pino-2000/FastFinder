package org.example.fastfinder.index

import com.sun.jna.Memory
import com.sun.jna.platform.win32.WinBase
import com.sun.jna.platform.win32.WinNT.HANDLE
import com.sun.jna.ptr.IntByReference
import org.example.fastfinder.util.Logger

enum class ChangeKind { CREATED, MODIFIED, DELETED }

data class VolumeChange(val path: String, val kind: ChangeKind)

sealed interface CatchUpOutcome {
    data class Success(val changes: List<VolumeChange>, val journalId: Long, val checkpointUsn: Long) : CatchUpOutcome
    data object Unavailable : CatchUpOutcome
}

/**
 * Reads NTFS USN change journal records for a single volume (e.g. `C`) and resolves them into
 * absolute paths, so [DBManager] can catch up on filesystem changes made while the app was
 * closed without a full rebuild.
 *
 * Requires the process to be elevated - opening a volume handle for FSCTL_QUERY_USN_JOURNAL and
 * FSCTL_READ_USN_JOURNAL is restricted to administrators. When that's not the case (or the
 * volume isn't NTFS, has no active journal, or its journal was reset since the last checkpoint),
 * catch-up is simply [CatchUpOutcome.Unavailable] - there is no partial/unsafe fallback, callers
 * just keep whatever index state they already have and rely on the live watcher going forward.
 */
internal class UsnJournalReader(private val driveLetter: Char) {
    private val k32 = ExtraKernel32.INSTANCE

    /** Reads every change since [previousJournalId]/[previousCheckpointUsn] and resolves each to an absolute path. */
    fun catchUp(previousJournalId: Long?, previousCheckpointUsn: Long?): CatchUpOutcome {
        val volumeHandle = openVolume() ?: return CatchUpOutcome.Unavailable
        return try {
            val journal = queryJournal(volumeHandle)
            val usableCheckpoint = previousCheckpointUsn?.takeIf {
                journal != null && previousJournalId == journal.journalId && it >= journal.lowestValidUsn
            }

            if (journal == null || usableCheckpoint == null) {
                Logger.info("No usable USN journal checkpoint for $driveLetter: - skipping catch-up this run.")
                CatchUpOutcome.Unavailable
            } else {
                val records = readRecords(volumeHandle, journal.journalId, usableCheckpoint, journal.nextUsn)
                CatchUpOutcome.Success(resolveChanges(volumeHandle, records), journal.journalId, journal.nextUsn)
            }
        } finally {
            k32.CloseHandle(volumeHandle)
        }
    }

    /** The volume's current (journalId, nextUsn), to checkpoint against for next time - reads no records. */
    fun currentCheckpointOrNull(): Pair<Long, Long>? {
        val volumeHandle = openVolume() ?: return null
        return try {
            queryJournal(volumeHandle)?.let { it.journalId to it.nextUsn }
        } finally {
            k32.CloseHandle(volumeHandle)
        }
    }

    private fun openVolume(): HANDLE? {
        val handle = k32.CreateFile(
            "\\\\.\\$driveLetter:",
            Win32.GENERIC_READ,
            Win32.FILE_SHARE_READ or Win32.FILE_SHARE_WRITE,
            null,
            Win32.OPEN_EXISTING,
            Win32.FILE_FLAG_BACKUP_SEMANTICS,
            null,
        )
        if (handle == WinBase.INVALID_HANDLE_VALUE) {
            val error = k32.GetLastError()
            Logger.info(
                "Could not open volume $driveLetter: for USN journal access (error $error); catch-up unavailable."
            )
            return null
        }
        return handle
    }

    private fun queryJournal(volumeHandle: HANDLE): Win32.JournalData? {
        val outBuffer = Memory(Win32.JOURNAL_DATA_SIZE_BYTES)
        val bytesReturned = IntByReference()
        val size = Win32.JOURNAL_DATA_SIZE_BYTES.toInt()
        val ok = k32.DeviceIoControl(
            volumeHandle, Win32.FSCTL_QUERY_USN_JOURNAL, null, 0, outBuffer, size, bytesReturned, null,
        )
        if (!ok) {
            val error = k32.GetLastError()
            Logger.info("No active USN journal on $driveLetter: (error $error); catch-up unavailable.")
            return null
        }
        return Win32.JournalData(outBuffer)
    }

    /** Reads every record from [startUsn] up to [endUsn], following each buffer's next-start USN until caught up. */
    private fun readRecords(
        volumeHandle: HANDLE,
        journalId: Long,
        startUsn: Long,
        endUsn: Long,
    ): List<Win32.UsnRecord> {
        val records = mutableListOf<Win32.UsnRecord>()
        var cursor = startUsn
        val outBuffer = Memory(READ_BUFFER_SIZE.toLong())
        val bytesReturned = IntByReference()

        while (cursor < endUsn) {
            val input = Win32.buildReadJournalInput(cursor, journalId)
            val inputSize = Win32.READ_JOURNAL_INPUT_SIZE_BYTES
            val ok = k32.DeviceIoControl(
                volumeHandle,
                Win32.FSCTL_READ_USN_JOURNAL,
                input,
                inputSize,
                outBuffer,
                READ_BUFFER_SIZE,
                bytesReturned,
                null,
            )
            if (!ok) {
                val error = k32.GetLastError()
                Logger.warn("FSCTL_READ_USN_JOURNAL failed on $driveLetter: (error $error); stopping catch-up early.")
            }

            val nextStartUsn = if (ok) outBuffer.getLong(0) else cursor
            val total = if (ok) bytesReturned.value.toLong() else 0L
            val hasMoreRecords = ok && total > Win32.READ_JOURNAL_RECORDS_START_OFFSET && nextStartUsn > cursor
            if (!hasMoreRecords) break

            appendRecords(outBuffer, total, records)
            cursor = nextStartUsn
        }
        return records
    }

    private fun appendRecords(buffer: Memory, total: Long, into: MutableList<Win32.UsnRecord>) {
        var offset = Win32.READ_JOURNAL_RECORDS_START_OFFSET
        while (offset < total) {
            val record = Win32.UsnRecord(buffer, offset)
            if (record.recordLength <= 0) break
            into.add(record)
            offset += record.recordLength
        }
    }

    /**
     * Resolves each record to an absolute path and a [ChangeKind], deduplicated so only the
     * last (chronologically latest) outcome per file survives - a file that changed several
     * times while the app was closed only needs re-indexing once.
     */
    private fun resolveChanges(volumeHandle: HANDLE, records: List<Win32.UsnRecord>): List<VolumeChange> {
        val deletesByFrn = records
            .filter { it.reason and Win32.USN_REASON_FILE_DELETE != 0 }
            .associateBy { it.fileReferenceNumber }
        val livePathCache = mutableMapOf<Long, String?>()

        val outcomes = LinkedHashMap<Long, VolumeChange>()
        for (record in records) {
            val isDelete = record.reason and Win32.USN_REASON_FILE_DELETE != 0
            val path = if (isDelete) {
                resolveDeletedPath(volumeHandle, record, deletesByFrn, livePathCache, depth = 0)
            } else {
                resolveLivePath(volumeHandle, record.fileReferenceNumber, livePathCache)
            }
            if (path == null) continue

            val kind = when {
                isDelete -> ChangeKind.DELETED
                record.reason and Win32.USN_REASON_FILE_CREATE != 0 -> ChangeKind.CREATED
                else -> ChangeKind.MODIFIED
            }
            outcomes[record.fileReferenceNumber] = VolumeChange(path, kind)
        }
        return outcomes.values.toList()
    }

    /** A currently-existing file's path, resolved live and authoritatively (including across renames) by its FRN. */
    private fun resolveLivePath(volumeHandle: HANDLE, frn: Long, cache: MutableMap<Long, String?>): String? =
        cache.getOrPut(frn) {
            val fileId = Win32.buildFileIdDescriptor(frn)
            val shareMode = Win32.FILE_SHARE_READ or Win32.FILE_SHARE_WRITE or Win32.FILE_SHARE_DELETE
            val fileHandle = k32.OpenFileById(
                volumeHandle, fileId, 0, shareMode, null, Win32.FILE_FLAG_BACKUP_SEMANTICS,
            )
            if (fileHandle == WinBase.INVALID_HANDLE_VALUE) return@getOrPut null
            try {
                val pathBuffer = CharArray(MAX_PATH_CHARS)
                val length = k32.GetFinalPathNameByHandleW(fileHandle, pathBuffer, pathBuffer.size, 0)
                if (length !in 1 until pathBuffer.size) {
                    null
                } else {
                    String(pathBuffer, 0, length).removePrefix(EXTENDED_PATH_PREFIX)
                }
            } finally {
                k32.CloseHandle(fileHandle)
            }
        }

    /**
     * A deleted file's former path, which can't be resolved live (it no longer exists to open) -
     * rebuilt instead from the record's own embedded file name plus its parent's resolved path,
     * walking upward through parent FRNs and falling back to other delete records in this same
     * batch when an ancestor was deleted too, so a whole subtree deleted while the app was
     * closed still resolves correctly.
     */
    private fun resolveDeletedPath(
        volumeHandle: HANDLE,
        record: Win32.UsnRecord,
        deletesByFrn: Map<Long, Win32.UsnRecord>,
        cache: MutableMap<Long, String?>,
        depth: Int,
    ): String? {
        if (depth > MAX_PARENT_CHAIN_DEPTH) return null
        val parentPath = resolveLivePath(volumeHandle, record.parentFileReferenceNumber, cache)
            ?: deletesByFrn[record.parentFileReferenceNumber]
                ?.let { resolveDeletedPath(volumeHandle, it, deletesByFrn, cache, depth + 1) }
        return parentPath?.let { "$it\\${record.fileName}" }
    }

    companion object {
        private const val READ_BUFFER_SIZE = 65536
        private const val MAX_PATH_CHARS = 4096
        private const val MAX_PARENT_CHAIN_DEPTH = 64
        private const val EXTENDED_PATH_PREFIX = "\\\\?\\"
    }
}

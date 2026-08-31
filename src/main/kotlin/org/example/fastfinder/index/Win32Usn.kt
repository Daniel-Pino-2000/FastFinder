package org.example.fastfinder.index

import com.sun.jna.Memory
import com.sun.jna.Native
import com.sun.jna.Pointer
import com.sun.jna.platform.win32.Kernel32
import com.sun.jna.platform.win32.WinNT.HANDLE
import com.sun.jna.win32.W32APIOptions

/**
 * Raw Win32 bindings for reading the NTFS USN change journal - the JDK has no API for this.
 *
 * Structures below are read/written as raw byte offsets into [Memory] buffers rather than as
 * JNA [com.sun.jna.Structure] subclasses. That's deliberate: a [com.sun.jna.Structure]'s field
 * layout is computed by JNA's own reflection-based alignment rules, and a mismatch between that
 * and the real native layout wouldn't throw a catchable exception - it corrupts memory or
 * crashes the JVM outright. Every offset here is instead a literal copied from the Microsoft
 * documentation for the corresponding struct, so it can be checked directly against the source.
 */
internal interface ExtraKernel32 : Kernel32 {
    // Method names and parameter order must match the native kernel32.dll exports exactly -
    // JNA's Native.load dispatches by name, and these two Win32 APIs aren't part of the
    // interface jna-platform's own Kernel32 already declares.
    @Suppress("FunctionNaming", "LongParameterList")
    fun OpenFileById(
        hVolumeHint: HANDLE,
        lpFileId: Pointer,
        dwDesiredAccess: Int,
        dwShareMode: Int,
        lpSecurityAttributes: Pointer?,
        dwFlagsAndAttributes: Int,
    ): HANDLE

    @Suppress("FunctionNaming")
    fun GetFinalPathNameByHandleW(hFile: HANDLE, lpszFilePath: CharArray, cchFilePath: Int, dwFlags: Int): Int

    companion object {
        val INSTANCE: ExtraKernel32 by lazy {
            Native.load("kernel32", ExtraKernel32::class.java, W32APIOptions.DEFAULT_OPTIONS)
        }
    }
}

internal object Win32 {
    const val FSCTL_QUERY_USN_JOURNAL = 0x000900f4
    const val FSCTL_READ_USN_JOURNAL = 0x000900bb

    const val GENERIC_READ = -0x80000000 // 0x80000000
    const val FILE_SHARE_READ = 0x00000001
    const val FILE_SHARE_WRITE = 0x00000002
    const val FILE_SHARE_DELETE = 0x00000004
    const val OPEN_EXISTING = 3
    const val FILE_FLAG_BACKUP_SEMANTICS = 0x02000000

    const val FILE_ATTRIBUTE_DIRECTORY = 0x00000010

    // USN_RECORD_V2.Reason bits actually consulted here (see winioctl.h for the full list).
    const val USN_REASON_FILE_CREATE = 0x00000100
    const val USN_REASON_FILE_DELETE = 0x00000200

    /** Size of the [FSCTL_QUERY_USN_JOURNAL]-filled buffer that [JournalData] parses. */
    const val JOURNAL_DATA_SIZE_BYTES = 56L

    /** Size of the input buffer [buildReadJournalInput] builds, for FSCTL_READ_USN_JOURNAL. */
    const val READ_JOURNAL_INPUT_SIZE_BYTES = 40

    /** Where each returned FSCTL_READ_USN_JOURNAL buffer's records begin, after its leading next-USN field. */
    const val READ_JOURNAL_RECORDS_START_OFFSET = 8L

    // Byte offsets below are field-for-field copies of the Microsoft-documented struct layouts
    // (see each class's doc link), not arbitrary numbers - naming them ties each one back to
    // the specific struct field it represents.
    private const val JOURNAL_DATA_FIRST_USN_OFFSET = 8L
    private const val JOURNAL_DATA_NEXT_USN_OFFSET = 16L
    private const val JOURNAL_DATA_LOWEST_VALID_USN_OFFSET = 24L

    private const val READ_INPUT_REASON_MASK_OFFSET = 8L
    private const val READ_INPUT_RETURN_ONLY_ON_CLOSE_OFFSET = 12L
    private const val READ_INPUT_TIMEOUT_OFFSET = 16L
    private const val READ_INPUT_BYTES_TO_WAIT_FOR_OFFSET = 24L
    private const val READ_INPUT_JOURNAL_ID_OFFSET = 32L

    private const val RECORD_FILE_REFERENCE_NUMBER_OFFSET = 8L
    private const val RECORD_PARENT_FILE_REFERENCE_NUMBER_OFFSET = 16L
    private const val RECORD_USN_OFFSET = 24L
    private const val RECORD_REASON_OFFSET = 40L
    private const val RECORD_FILE_ATTRIBUTES_OFFSET = 52L
    private const val RECORD_FILE_NAME_LENGTH_OFFSET = 56L
    private const val RECORD_FILE_NAME_OFFSET_OFFSET = 58L

    private const val FILE_ID_DESCRIPTOR_SIZE_BYTES = 24
    private const val FILE_ID_DESCRIPTOR_TYPE_OFFSET = 4L
    private const val FILE_ID_DESCRIPTOR_TYPE_FILE_ID = 0
    private const val FILE_ID_DESCRIPTOR_FILE_ID_OFFSET = 8L

    /**
     * USN_JOURNAL_DATA_V0 (winioctl.h):
     * https://learn.microsoft.com/windows/win32/api/winioctl/ns-winioctl-usn_journal_data_v0
     */
    class JournalData(buffer: Memory) {
        val journalId: Long = buffer.getLong(0)
        val firstUsn: Long = buffer.getLong(JOURNAL_DATA_FIRST_USN_OFFSET)
        val nextUsn: Long = buffer.getLong(JOURNAL_DATA_NEXT_USN_OFFSET)
        val lowestValidUsn: Long = buffer.getLong(JOURNAL_DATA_LOWEST_VALID_USN_OFFSET)
    }

    /**
     * Builds the input buffer for READ_USN_JOURNAL_DATA_V0 (ntifs.h):
     * https://learn.microsoft.com/windows-hardware/drivers/ddi/ntifs/ns-ntifs-read_usn_journal_data_v0
     */
    fun buildReadJournalInput(startUsn: Long, journalId: Long): Memory {
        val buffer = Memory(READ_JOURNAL_INPUT_SIZE_BYTES.toLong())
        buffer.clear()
        buffer.setLong(0, startUsn)
        buffer.setInt(READ_INPUT_REASON_MASK_OFFSET, -1) // all reasons
        buffer.setInt(READ_INPUT_RETURN_ONLY_ON_CLOSE_OFFSET, 0)
        buffer.setLong(READ_INPUT_TIMEOUT_OFFSET, 0)
        buffer.setLong(READ_INPUT_BYTES_TO_WAIT_FOR_OFFSET, 0)
        buffer.setLong(READ_INPUT_JOURNAL_ID_OFFSET, journalId)
        return buffer
    }

    /**
     * One parsed USN_RECORD_V2 entry starting at [offset] in [buffer]:
     * https://learn.microsoft.com/windows/win32/api/winioctl/ns-winioctl-usn_record_v2
     */
    class UsnRecord(buffer: Memory, offset: Long) {
        val recordLength: Int = buffer.getInt(offset)
        val fileReferenceNumber: Long = buffer.getLong(offset + RECORD_FILE_REFERENCE_NUMBER_OFFSET)
        val parentFileReferenceNumber: Long = buffer.getLong(offset + RECORD_PARENT_FILE_REFERENCE_NUMBER_OFFSET)
        val usn: Long = buffer.getLong(offset + RECORD_USN_OFFSET)
        val reason: Int = buffer.getInt(offset + RECORD_REASON_OFFSET)
        val fileAttributes: Int = buffer.getInt(offset + RECORD_FILE_ATTRIBUTES_OFFSET)
        val fileName: String

        init {
            val fileNameLength = java.lang.Short.toUnsignedInt(buffer.getShort(offset + RECORD_FILE_NAME_LENGTH_OFFSET))
            val fileNameOffset = java.lang.Short.toUnsignedInt(buffer.getShort(offset + RECORD_FILE_NAME_OFFSET_OFFSET))
            val nameBytes = buffer.getByteArray(offset + fileNameOffset, fileNameLength)
            fileName = String(nameBytes, Charsets.UTF_16LE)
        }

        val isDirectory: Boolean get() = fileAttributes and FILE_ATTRIBUTE_DIRECTORY != 0
    }

    /**
     * FILE_ID_DESCRIPTOR, Type=FileIdType(0):
     * https://learn.microsoft.com/windows/win32/api/winbase/ns-winbase-file_id_descriptor
     */
    fun buildFileIdDescriptor(fileReferenceNumber: Long): Memory {
        val buffer = Memory(FILE_ID_DESCRIPTOR_SIZE_BYTES.toLong())
        buffer.clear()
        buffer.setInt(0, FILE_ID_DESCRIPTOR_SIZE_BYTES) // dwSize
        buffer.setInt(FILE_ID_DESCRIPTOR_TYPE_OFFSET, FILE_ID_DESCRIPTOR_TYPE_FILE_ID)
        buffer.setLong(FILE_ID_DESCRIPTOR_FILE_ID_OFFSET, fileReferenceNumber)
        return buffer
    }
}

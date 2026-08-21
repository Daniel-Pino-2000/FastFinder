package org.example.fastfinder.util

import org.example.fastfinder.model.SearchFilter
import org.example.fastfinder.model.SearchMode
import org.example.fastfinder.model.SystemItem
import java.io.File
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class FileTypeUtilsTest {

    @Test
    fun `getFileType recognizes each category`() {
        assertEquals(SearchFilter.VIDEO, getFileType(File("movie.mp4")))
        assertEquals(SearchFilter.AUDIO, getFileType(File("song.mp3")))
        assertEquals(SearchFilter.IMAGE, getFileType(File("photo.png")))
        assertEquals(SearchFilter.DOCUMENT, getFileType(File("report.pdf")))
        assertEquals(SearchFilter.EXECUTABLE, getFileType(File("setup.exe")))
    }

    @Test
    fun `getFileType is case-insensitive and defaults to ALL for unknown extensions`() {
        assertEquals(SearchFilter.IMAGE, getFileType(File("photo.PNG")))
        assertEquals(SearchFilter.ALL, getFileType(File("notes.unknownext")))
        assertEquals(SearchFilter.ALL, getFileType(File("no_extension")))
    }

    @Test
    fun `formatSize picks the right unit`() {
        assertEquals("0.00 B", formatSize(0))
        assertEquals("512.00 B", formatSize(512))
        assertEquals("1.00 KB", formatSize(1024))
        assertEquals("1.50 KB", formatSize(1536))
        assertEquals("1.00 MB", formatSize(1024L * 1024))
        assertEquals("1.00 GB", formatSize(1024L * 1024 * 1024))
        assertEquals("1.00 TB", formatSize(1024L * 1024 * 1024 * 1024))
    }

    @Test
    fun `formatSize does not overflow past TB`() {
        val huge = 1024L * 1024 * 1024 * 1024 * 1024 // 1 PB
        assertTrue(formatSize(huge).endsWith("TB"))
    }

    @Test
    fun `isVisible in ALL mode ignores the result filter`() {
        val file = SystemItem("C:\\video.mp4", isFile = true, itemSize = 10)
        val dir = SystemItem("C:\\folder", isFile = false, itemSize = null)
        assertTrue(file.isVisible(SearchMode.ALL, SearchFilter.DOCUMENT))
        assertTrue(dir.isVisible(SearchMode.ALL, SearchFilter.DOCUMENT))
    }

    @Test
    fun `isVisible in DIRECTORIES mode only shows directories`() {
        val file = SystemItem("C:\\video.mp4", isFile = true, itemSize = 10)
        val dir = SystemItem("C:\\folder", isFile = false, itemSize = null)
        assertFalse(file.isVisible(SearchMode.DIRECTORIES, SearchFilter.ALL))
        assertTrue(dir.isVisible(SearchMode.DIRECTORIES, SearchFilter.ALL))
    }

    @Test
    fun `isVisible in FILES mode applies the result filter`() {
        val video = SystemItem("C:\\clip.mp4", isFile = true, itemSize = 10)
        val doc = SystemItem("C:\\report.pdf", isFile = true, itemSize = 10)
        val dir = SystemItem("C:\\folder", isFile = false, itemSize = null)

        assertTrue(video.isVisible(SearchMode.FILES, SearchFilter.VIDEO))
        assertFalse(doc.isVisible(SearchMode.FILES, SearchFilter.VIDEO))
        assertTrue(doc.isVisible(SearchMode.FILES, SearchFilter.ALL))
        assertFalse(dir.isVisible(SearchMode.FILES, SearchFilter.ALL))
    }
}

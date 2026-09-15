package org.example.fastfinder.util

import org.example.fastfinder.model.SearchFilter
import org.example.fastfinder.model.SearchMode
import org.example.fastfinder.model.SizeFilter
import org.example.fastfinder.model.SortBy
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
        assertEquals(SearchFilter.ARCHIVE, getFileType(File("backup.zip")))
        assertEquals(SearchFilter.CODE, getFileType(File("Main.kt")))
    }

    @Test
    fun `getFileType is case-insensitive and defaults to OTHER for unknown extensions`() {
        assertEquals(SearchFilter.IMAGE, getFileType(File("photo.PNG")))
        assertEquals(SearchFilter.CODE, getFileType(File("Main.KT")))
        assertEquals(SearchFilter.OTHER, getFileType(File("notes.unknownext")))
        assertEquals(SearchFilter.OTHER, getFileType(File("no_extension")))
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
    fun `formatDate returns a placeholder for null`() {
        assertEquals("—", formatDate(null))
    }

    @Test
    fun `formatDate renders a non-null epoch millis value`() {
        assertFalse(formatDate(0L) == "—")
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

    @Test
    fun `isVisible in FILES mode applies the size filter`() {
        val tiny = SystemItem("C:\\tiny.txt", isFile = true, itemSize = 500)
        val medium = SystemItem("C:\\medium.txt", isFile = true, itemSize = 5 * 1024 * 1024)
        val huge = SystemItem("C:\\huge.txt", isFile = true, itemSize = 2L * 1024 * 1024 * 1024)

        assertTrue(tiny.isVisible(SearchMode.FILES, SearchFilter.ALL, SizeFilter.UNDER_10KB))
        assertFalse(medium.isVisible(SearchMode.FILES, SearchFilter.ALL, SizeFilter.UNDER_10KB))

        assertTrue(medium.isVisible(SearchMode.FILES, SearchFilter.ALL, SizeFilter.MB1_TO_MB100))
        assertFalse(tiny.isVisible(SearchMode.FILES, SearchFilter.ALL, SizeFilter.MB1_TO_MB100))

        assertTrue(huge.isVisible(SearchMode.FILES, SearchFilter.ALL, SizeFilter.OVER_1GB))
        assertFalse(medium.isVisible(SearchMode.FILES, SearchFilter.ALL, SizeFilter.OVER_1GB))

        // SizeFilter.ANY never excludes anything
        assertTrue(tiny.isVisible(SearchMode.FILES, SearchFilter.ALL, SizeFilter.ANY))
        assertTrue(huge.isVisible(SearchMode.FILES, SearchFilter.ALL, SizeFilter.ANY))
    }

    private fun names(items: List<SystemItem>) = items.map { it.itemPath.substringAfterLast('\\') }

    @Test
    fun `systemItemComparator always orders folders before files, regardless of sort key or direction`() {
        val file = SystemItem("C:\\a_first_alphabetically.txt", isFile = true, itemSize = 999)
        val dir = SystemItem("C:\\z_last_alphabetically", isFile = false, itemSize = null)

        for (sortBy in SortBy.entries) {
            for (ascending in listOf(true, false)) {
                val sorted = listOf(file, dir).sortedWith(systemItemComparator(sortBy, ascending))
                assertEquals(listOf("z_last_alphabetically", "a_first_alphabetically.txt"), names(sorted),
                    "folders should sort first for sortBy=$sortBy ascending=$ascending")
            }
        }
    }

    @Test
    fun `systemItemComparator sorts by name case-insensitively`() {
        val items = listOf(
            SystemItem("C:\\banana.txt", isFile = true, itemSize = 1),
            SystemItem("C:\\Apple.txt", isFile = true, itemSize = 1),
            SystemItem("C:\\cherry.txt", isFile = true, itemSize = 1),
        )

        assertEquals(listOf("Apple.txt", "banana.txt", "cherry.txt"), names(items.sortedWith(systemItemComparator(SortBy.NAME, ascending = true))))
        assertEquals(listOf("cherry.txt", "banana.txt", "Apple.txt"), names(items.sortedWith(systemItemComparator(SortBy.NAME, ascending = false))))
    }

    @Test
    fun `systemItemComparator sorts by size`() {
        val items = listOf(
            SystemItem("C:\\medium.txt", isFile = true, itemSize = 500),
            SystemItem("C:\\small.txt", isFile = true, itemSize = 10),
            SystemItem("C:\\large.txt", isFile = true, itemSize = 5000),
        )

        assertEquals(listOf("small.txt", "medium.txt", "large.txt"), names(items.sortedWith(systemItemComparator(SortBy.SIZE, ascending = true))))
        assertEquals(listOf("large.txt", "medium.txt", "small.txt"), names(items.sortedWith(systemItemComparator(SortBy.SIZE, ascending = false))))
    }

    @Test
    fun `systemItemComparator sorts by type`() {
        val items = listOf(
            SystemItem("C:\\video.mp4", isFile = true, itemSize = 1),
            SystemItem("C:\\doc.pdf", isFile = true, itemSize = 1),
            SystemItem("C:\\audio.mp3", isFile = true, itemSize = 1),
        )

        // AUDIO < DOCUMENT < VIDEO alphabetically by enum name
        assertEquals(listOf("audio.mp3", "doc.pdf", "video.mp4"), names(items.sortedWith(systemItemComparator(SortBy.TYPE, ascending = true))))
    }

    @Test
    fun `systemItemComparator sorts by date modified`() {
        val items = listOf(
            SystemItem("C:\\medium.txt", isFile = true, itemSize = 1, itemModifiedDate = 2000L),
            SystemItem("C:\\oldest.txt", isFile = true, itemSize = 1, itemModifiedDate = 1000L),
            SystemItem("C:\\newest.txt", isFile = true, itemSize = 1, itemModifiedDate = 3000L),
        )

        assertEquals(
            listOf("oldest.txt", "medium.txt", "newest.txt"),
            names(items.sortedWith(systemItemComparator(SortBy.DATE, ascending = true))),
        )
        assertEquals(
            listOf("newest.txt", "medium.txt", "oldest.txt"),
            names(items.sortedWith(systemItemComparator(SortBy.DATE, ascending = false))),
        )
    }

    @Test
    fun `systemItemComparator sorts by date created, independently of date modified`() {
        val items = listOf(
            // Modified order (2000/1000/3000) deliberately doesn't match created order, so this
            // only passes if DATE_CREATED actually reads itemCreatedDate, not itemModifiedDate.
            SystemItem("C:\\medium.txt", isFile = true, itemSize = 1, itemModifiedDate = 2000L, itemCreatedDate = 20L),
            SystemItem("C:\\oldest.txt", isFile = true, itemSize = 1, itemModifiedDate = 1000L, itemCreatedDate = 10L),
            SystemItem("C:\\newest.txt", isFile = true, itemSize = 1, itemModifiedDate = 3000L, itemCreatedDate = 30L),
        )

        assertEquals(
            listOf("oldest.txt", "medium.txt", "newest.txt"),
            names(items.sortedWith(systemItemComparator(SortBy.DATE_CREATED, ascending = true))),
        )
        assertEquals(
            listOf("newest.txt", "medium.txt", "oldest.txt"),
            names(items.sortedWith(systemItemComparator(SortBy.DATE_CREATED, ascending = false))),
        )
    }
}

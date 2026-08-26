package org.example.fastfinder.util

import org.example.fastfinder.model.SearchFilter
import org.example.fastfinder.model.SearchMode
import org.example.fastfinder.model.SizeFilter
import org.example.fastfinder.model.SortBy
import org.example.fastfinder.model.SystemItem
import java.io.File

private val VIDEO_EXTENSIONS = setOf(
    "mp4", "mkv", "avi", "mov", "wmv", "flv", "webm", "mpeg", "mpg", "m4v",
    "3gp", "3g2", "vob", "ogv", "m2ts", "ts", "mts", "rm", "rmvb", "asf", "divx"
)

private val AUDIO_EXTENSIONS = setOf(
    "mp3", "wav", "aac", "flac", "ogg", "m4a", "wma", "alac", "aiff", "opus",
    "amr", "mid", "midi", "ac3", "ape", "au", "ra", "mka", "tta", "dts"
)

private val IMAGE_EXTENSIONS = setOf(
    "jpg", "jpeg", "png", "gif", "bmp", "webp", "svg", "tiff", "tif", "ico",
    "heic", "heif", "psd", "raw", "cr2", "nef", "orf", "sr2", "ai", "eps", "jfif",
    "pbm", "pgm", "ppm"
)

private val DOCUMENT_EXTENSIONS = setOf(
    "pdf", "doc", "docx", "txt", "xls", "xlsx", "ppt", "pptx", "odt", "rtf",
    "csv", "tex", "md", "odp", "ods", "epub", "mobi", "azw", "fb2", "djvu",
    "xps", "oxps", "log", "pages", "numbers", "key", "dot", "dotx", "pps", "ppsx"
)

private val EXECUTABLE_EXTENSIONS = setOf(
    "exe", "msi", "bat", "sh", "app", "apk", "jar", "dmg", "pkg", "deb", "rpm",
    "run", "bin", "com", "gadget", "wsf", "cgi", "ipa", "xap", "vb", "vbs",
    "out", "elf", "dll", "so", "class"
)

fun getFileType(file: File): SearchFilter = when (file.extension.lowercase()) {
    in VIDEO_EXTENSIONS -> SearchFilter.VIDEO
    in AUDIO_EXTENSIONS -> SearchFilter.AUDIO
    in IMAGE_EXTENSIONS -> SearchFilter.IMAGE
    in DOCUMENT_EXTENSIONS -> SearchFilter.DOCUMENT
    in EXECUTABLE_EXTENSIONS -> SearchFilter.EXECUTABLE
    else -> SearchFilter.ALL
}

fun formatSize(size: Long): String {
    val units = listOf("B", "KB", "MB", "GB", "TB")
    var value = size.toDouble()
    var unitIndex = 0
    while (value >= 1024 && unitIndex < units.lastIndex) {
        value /= 1024
        unitIndex++
    }
    return "%.2f %s".format(value, units[unitIndex])
}

/** Whether this item should be shown for the given search mode / result filter combination. */
fun SystemItem.isVisible(searchMode: SearchMode, resultFilter: SearchFilter, sizeFilter: SizeFilter = SizeFilter.ANY): Boolean = when (searchMode) {
    SearchMode.ALL -> true
    SearchMode.DIRECTORIES -> !isFile
    SearchMode.FILES -> isFile &&
        (resultFilter == SearchFilter.ALL || getFileType(File(itemPath)) == resultFilter) &&
        (itemSize ?: 0L) in sizeFilter.minBytes..sizeFilter.maxBytes
}

private fun SystemItem.name(): String = itemPath.substringAfterLast(File.separatorChar).lowercase()

/** Orders folders before files (matching Explorer/Finder convention), then by [sortBy] within each group. */
fun systemItemComparator(sortBy: SortBy, ascending: Boolean): Comparator<SystemItem> {
    val withinGroup: Comparator<SystemItem> = when (sortBy) {
        SortBy.NAME -> compareBy { it.name() }
        SortBy.SIZE -> compareBy { it.itemSize ?: 0L }
        SortBy.TYPE -> compareBy { if (it.isFile) getFileType(File(it.itemPath)).name else "" }
    }
    val directed = if (ascending) withinGroup else withinGroup.reversed()
    return compareBy<SystemItem> { it.isFile }.then(directed)
}

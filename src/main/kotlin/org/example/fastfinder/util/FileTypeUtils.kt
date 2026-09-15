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

private val ARCHIVE_EXTENSIONS = setOf(
    "zip", "rar", "7z", "tar", "gz", "bz2", "xz", "iso", "cab", "tgz", "tbz2",
    "lz", "lzma", "z", "arj", "war", "img", "vhd", "vhdx"
)

private val CODE_EXTENSIONS = setOf(
    "kt", "kts", "java", "py", "js", "jsx", "ts", "tsx", "html", "htm", "css",
    "scss", "less", "json", "xml", "yaml", "yml", "c", "cc", "cpp", "cxx", "h",
    // "sh" deliberately isn't here - it's classified as Executable below, alongside "bat",
    // matching how both are launched (run directly) rather than opened in an editor.
    "hpp", "cs", "go", "rs", "php", "rb", "swift", "sql", "ps1", "gradle",
    "dart", "lua", "r", "scala", "pl", "groovy", "toml", "ini", "cfg", "conf",
    "vue", "svelte", "sass", "makefile", "cmake", "gitignore", "editorconfig",
)

fun getFileType(file: File): SearchFilter = when (file.extension.lowercase()) {
    in VIDEO_EXTENSIONS -> SearchFilter.VIDEO
    in AUDIO_EXTENSIONS -> SearchFilter.AUDIO
    in IMAGE_EXTENSIONS -> SearchFilter.IMAGE
    in DOCUMENT_EXTENSIONS -> SearchFilter.DOCUMENT
    in EXECUTABLE_EXTENSIONS -> SearchFilter.EXECUTABLE
    in ARCHIVE_EXTENSIONS -> SearchFilter.ARCHIVE
    in CODE_EXTENSIONS -> SearchFilter.CODE
    else -> SearchFilter.OTHER
}

val SearchMode.label: String
    get() = when (this) {
        SearchMode.FILES -> "Files"
        SearchMode.DIRECTORIES -> "Folders"
        SearchMode.ALL -> "All"
    }

val SearchFilter.label: String
    get() = when (this) {
        SearchFilter.IMAGE -> "Images"
        SearchFilter.DOCUMENT -> "Documents"
        SearchFilter.VIDEO -> "Videos"
        SearchFilter.AUDIO -> "Audios"
        SearchFilter.EXECUTABLE -> "Executables"
        SearchFilter.ARCHIVE -> "Archives"
        SearchFilter.CODE -> "Code"
        SearchFilter.OTHER -> "Other"
        SearchFilter.ALL -> "All Files"
    }

/** The file's category, singular - used in the results grid's Type column ("Folder" for directories). */
val SearchFilter.singularLabel: String
    get() = when (this) {
        SearchFilter.IMAGE -> "Image"
        SearchFilter.DOCUMENT -> "Document"
        SearchFilter.VIDEO -> "Video"
        SearchFilter.AUDIO -> "Audio"
        SearchFilter.EXECUTABLE -> "Executable"
        SearchFilter.ARCHIVE -> "Archive"
        SearchFilter.CODE -> "Code"
        SearchFilter.OTHER -> "Other"
        SearchFilter.ALL -> "File"
    }

val SortBy.label: String
    get() = when (this) {
        SortBy.NAME -> "Name"
        SortBy.SIZE -> "Size"
        SortBy.TYPE -> "Type"
    }

val SizeFilter.label: String
    get() = when (this) {
        SizeFilter.ANY -> "Any Size"
        SizeFilter.UNDER_10KB -> "< 10 KB"
        SizeFilter.KB10_TO_MB1 -> "10 KB - 1 MB"
        SizeFilter.MB1_TO_MB100 -> "1 MB - 100 MB"
        SizeFilter.MB100_TO_GB1 -> "100 MB - 1 GB"
        SizeFilter.OVER_1GB -> "> 1 GB"
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

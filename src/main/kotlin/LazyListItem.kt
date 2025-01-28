import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.padding
import androidx.compose.material.Icon
import androidx.compose.material.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.input.pointer.PointerEventType
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.unit.dp
import java.awt.Desktop
import java.io.File

// Displays each item in the list with hover effect
@Composable
fun LazyListItem(item: SystemItem, searchMode: SearchMode, resultMode: SearchFilter) {

    var isHovered by remember { mutableStateOf(false) } // State to track hover
    val fileType : SearchFilter = getFileType(File(item.itemPath))

    // Only display the item if it matches the resultMode and the searchMode
    if (searchMode != SearchMode.ALL) {
        if (item.isFile && searchMode == SearchMode.DIRECTORIES) {
            return
        } else if (!item.isFile && searchMode == SearchMode.FILES) {
            return
        } else if (resultMode != SearchFilter.ALL && fileType != resultMode && searchMode != SearchMode.DIRECTORIES) {
            // If the searchMode is Folders then don't apply the files filter.
            return
        }
    }


    // Determine icon for the type of file or folder
    val icon: ImageVector = if (item.isFile) {
        when (getFileType(File(item.itemPath))) {
            SearchFilter.IMAGE -> ThemeElements().imageFileIcon
            SearchFilter.VIDEO -> ThemeElements().videoFileIcon
            SearchFilter.DOCUMENT -> ThemeElements().documentFileIcon
            SearchFilter.AUDIO -> ThemeElements().audioFileIcon
            SearchFilter.EXECUTABLE -> ThemeElements().executableFileIcon
            else -> {ThemeElements().fileIcon}
        }
    } else {
        ThemeElements().folderIcon // Assign folder icon to `icon`
    }

    // Formats the size
    val size = item.itemSize?.let { formatSize(it) }

    Row(modifier = Modifier.pointerInput(Unit) {
        // Detect hover using PointerEventType
        awaitPointerEventScope {
            while (true) {
                val event = awaitPointerEvent()
                when (event.type) {
                    PointerEventType.Enter -> isHovered = true
                    PointerEventType.Exit -> isHovered = false
                }
            }
        }
    }
        .background(color = if (isHovered) Color.LightGray else ThemeElements().lazyColumnColor) // Highlights the row if it is being hovered
        .padding(8.dp)) {

        Icon(
            icon,
            contentDescription = null,
            modifier = Modifier.padding(8.dp)
        )

        Text(
            text = item.itemPath,
            modifier = Modifier.padding(8.dp).weight(1f)
        )

        // Display the size
        if (size != null) {
            Text(
                text = size,
                modifier = Modifier.padding(8.dp)
            )
        }


        if (isHovered) { // Displays the clickable icon if it is hovered
            // If clicked, open the location of the file or the folder
            Icon(
                ThemeElements().openFolderIcon,
                contentDescription = null,
                modifier = Modifier
                    .padding(8.dp)
                    .clickable {
                        // Ensure item.itemPath is a valid path, then open in file explorer
                        val file = File(item.itemPath)
                        if (file.exists()) {
                            val parentDir = file.parentFile
                            if (parentDir != null && parentDir.exists()) {
                                // Open the parent directory in the file explorer
                                Desktop.getDesktop().browse(parentDir.toURI())
                            }
                        }
                    }
            )
        }
    }
}

// Function to check the type of the file
fun getFileType(file: File): SearchFilter {
    val extension = file.extension.lowercase()
    return when (extension) {
        // Video formats
        "mp4", "mkv", "avi", "mov", "wmv", "flv", "webm", "mpeg", "mpg", "m4v", "3gp", "3g2", "vob", "ogv", "m2ts", "ts", "mts", "rm", "rmvb", "asf", "divx" -> SearchFilter.VIDEO

        // Audio formats
        "mp3", "wav", "aac", "flac", "ogg", "m4a", "wma", "alac", "aiff", "opus", "amr", "mid", "midi", "ac3", "ape", "au", "ra", "mka", "tta", "dts" -> SearchFilter.AUDIO

        // Image formats
        "jpg", "jpeg", "png", "gif", "bmp", "webp", "svg", "tiff", "tif", "ico", "heic", "heif", "psd", "raw", "cr2", "nef", "orf", "sr2", "ai", "eps", "jfif", "pbm", "pgm", "ppm" -> SearchFilter.IMAGE

        // Document formats
        "pdf", "doc", "docx", "txt", "xls", "xlsx", "ppt", "pptx", "odt", "rtf", "csv", "tex", "md", "odp", "ods", "epub", "mobi", "azw", "fb2", "djvu", "xps", "oxps", "log", "pages", "numbers", "key", "dot", "dotx", "pps", "ppsx" -> SearchFilter.DOCUMENT

        // Executable formats
        "exe", "msi", "bat", "sh", "app", "apk", "jar", "dmg", "pkg", "deb", "rpm", "run", "bin", "com", "gadget", "wsf", "cgi", "ipa", "xap", "vb", "vbs", "out", "elf", "dll", "so", "class" -> SearchFilter.EXECUTABLE

        // Default case
        else -> SearchFilter.ALL
    }
}


fun formatSize(size: Long): String {
    val units = listOf("B", "KB", "MB", "GB", "TB")
    var sizeInUnits = size.toDouble()
    var unitIndex = 0

    while (sizeInUnits >= 1024 && unitIndex < units.size - 1) {
        sizeInUnits /= 1024
        unitIndex++
    }

    return "%.2f %s".format(sizeInUnits, units[unitIndex])
}

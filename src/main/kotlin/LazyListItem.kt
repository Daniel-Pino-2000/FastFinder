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
fun LazyListItem(item: SystemItem, resultMode: SearchFilter) {

    var isHovered by remember { mutableStateOf(false) } // State to track hover
    val fileType : SearchFilter = getFileType(File(item.itemPath))

    // Only display the item if it matches the resultMode or if resultMode is ALL
    if (resultMode != SearchFilter.ALL && fileType != resultMode) {
        return
    }

    // Determine icon for file or folder
    val icon: ImageVector = if (item.isFile) {
        when (getFileType(File(item.itemPath))) {
            SearchFilter.ALL -> ThemeElements().fileIcon
            SearchFilter.IMAGE -> ThemeElements().imageFileIcon
            SearchFilter.VIDEO -> ThemeElements().videoFileIcon
            SearchFilter.DOCUMENT -> ThemeElements().documentFileIcon
            SearchFilter.AUDIO -> ThemeElements().audioFileIcon
            SearchFilter.EXECUTABLE -> ThemeElements().executableFileIcon
        }
    } else {
        ThemeElements().folderIcon // Assign folder icon to `icon`
    }

    // Calculate and format the size
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
        "mp4", "mkv", "avi", "mov", "wmv", "flv", "webm" -> SearchFilter.VIDEO
        // Audio formats
        "mp3", "wav", "aac", "flac", "ogg", "m4a" -> SearchFilter.AUDIO
        // Image formats
        "jpg", "jpeg", "png", "gif", "bmp", "webp", "svg" -> SearchFilter.IMAGE
        // Document formats
        "pdf", "doc", "docx", "txt", "xls", "xlsx", "ppt", "pptx", "odt", "rtf" -> SearchFilter.DOCUMENT
        // Executable formats
        "exe", "msi", "bat", "sh", "app", "apk" -> SearchFilter.EXECUTABLE
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

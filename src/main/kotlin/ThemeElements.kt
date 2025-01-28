
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.InsertDriveFile
import androidx.compose.material.icons.filled.*
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector

// Data class to hold theme-related elements
data class ThemeElements(
    val buttonColor: Color = Color(0xFF0A5EB0),
    val backgroundColor: Color = Color(0xFFF1F0E8),
    val lazyColumnColor: Color = Color(0xFFE5E1DA),

    val fileIcon: ImageVector = Icons.AutoMirrored.Filled.InsertDriveFile,
    val audioFileIcon: ImageVector = Icons.Default.AudioFile,
    val videoFileIcon: ImageVector = Icons.Default.VideoFile,
    val documentFileIcon: ImageVector = Icons.Default.Description,
    val imageFileIcon: ImageVector = Icons.Default.Image,
    val executableFileIcon: ImageVector = Icons.Default.FileOpen,
    val folderIcon: ImageVector = Icons.Default.Folder,
    val openFolderIcon: ImageVector = Icons.Default.FolderOpen
)
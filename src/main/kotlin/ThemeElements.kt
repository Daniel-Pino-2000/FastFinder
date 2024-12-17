import androidx.compose.material.Icon
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector

// Data class to hold theme-related elements
data class ThemeElements(
    val buttonColor: Color = Color(0xFF0A5EB0),
    val backgroundColor: Color = Color(0xFFF1F0E8),
    val lazyColumnColor: Color = Color(0xFFE5E1DA),

    val fileIcon: ImageVector = Icons.Default.InsertDriveFile,
    val folderIcon: ImageVector = Icons.Default.Folder,
    val openFolderIcon: ImageVector = Icons.Default.FolderOpen
)
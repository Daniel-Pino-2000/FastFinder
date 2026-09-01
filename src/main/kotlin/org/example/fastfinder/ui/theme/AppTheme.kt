package org.example.fastfinder.ui.theme

import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.InsertDriveFile
import androidx.compose.material.icons.filled.*
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector

/** Icons don't vary by theme - only colors do (see [AppColors]/[LocalAppColors]). */
object AppTheme {
    val fileIcon: ImageVector = Icons.AutoMirrored.Filled.InsertDriveFile
    val audioFileIcon: ImageVector = Icons.Default.AudioFile
    val videoFileIcon: ImageVector = Icons.Default.VideoFile
    val documentFileIcon: ImageVector = Icons.Default.Description
    val imageFileIcon: ImageVector = Icons.Default.Image
    val executableFileIcon: ImageVector = Icons.Default.FileOpen
    val archiveFileIcon: ImageVector = Icons.Default.FolderZip
    val codeFileIcon: ImageVector = Icons.Default.Code
    val folderIcon: ImageVector = Icons.Default.Folder
    val openFolderIcon: ImageVector = Icons.Default.FolderOpen
    val copyIcon: ImageVector = Icons.Default.ContentCopy
}

data class AppColors(
    val buttonColor: Color,
    val backgroundColor: Color,
    val lazyColumnColor: Color,
    val hoverColor: Color,
    val errorBackground: Color,
    val errorForeground: Color,
)

val LightAppColors = AppColors(
    buttonColor = Color(0xFF0A5EB0),
    backgroundColor = Color(0xFFF1F0E8),
    lazyColumnColor = Color(0xFFE5E1DA),
    hoverColor = Color(0xFFD3D3D3),
    errorBackground = Color(0xFFFCE8E6),
    errorForeground = Color(0xFFB3261E),
)

val DarkAppColors = AppColors(
    buttonColor = Color(0xFF4C8DC9),
    backgroundColor = Color(0xFF1E1E1E),
    lazyColumnColor = Color(0xFF2B2B2B),
    hoverColor = Color(0xFF3D3D3D),
    errorBackground = Color(0xFF442726),
    errorForeground = Color(0xFFF2B8B5),
)

/** Provided by [org.example.fastfinder.ui.FastFinderApp] based on the current light/dark toggle. */
val LocalAppColors = staticCompositionLocalOf { LightAppColors }

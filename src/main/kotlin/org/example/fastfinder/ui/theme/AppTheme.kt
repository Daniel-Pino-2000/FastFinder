package org.example.fastfinder.ui.theme

import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.InsertDriveFile
import androidx.compose.material.icons.filled.*
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import org.example.fastfinder.model.SearchFilter

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
    val lightModeIcon: ImageVector = Icons.Default.WbSunny
    val darkModeIcon: ImageVector = Icons.Default.NightlightRound
    val refreshIcon: ImageVector = Icons.Default.Refresh
    val customSearchIcon: ImageVector = Icons.Default.CreateNewFolder
    val sortDirectionUpIcon: ImageVector = Icons.Default.ArrowUpward
    val sortDirectionDownIcon: ImageVector = Icons.Default.ArrowDownward
    val chevronIcon: ImageVector = Icons.Default.ArrowDropDown
    val adminSyncIcon: ImageVector = Icons.Default.AdminPanelSettings
}

/**
 * The app's full color palette - one flat set of semantic tokens (surfaces, text hierarchy,
 * accent, per-file-type tints) rather than a handful of ad-hoc colors, so every screen pulls
 * from the same system and light/dark stay visually consistent with each other.
 */
data class AppColors(
    val accent: Color,
    val onAccent: Color,
    val background: Color,
    val surface: Color,
    val surfaceAlt: Color,
    val border: Color,
    val textPrimary: Color,
    val textSecondary: Color,
    val textTertiary: Color,
    val hoverColor: Color,
    val errorBackground: Color,
    val errorForeground: Color,
    val typeFolder: Color,
    val typeDocument: Color,
    val typeImage: Color,
    val typeVideo: Color,
    val typeAudio: Color,
    val typeExecutable: Color,
    val typeArchive: Color,
    val typeCode: Color,
    val typeOther: Color,
)

@Suppress("MagicNumber") // A palette's colors are self-documenting hex literals, not magic numbers.
val LightAppColors = AppColors(
    accent = Color(0xFF5B4FE0),
    onAccent = Color(0xFFFFFFFF),
    background = Color(0xFFF7F6FB),
    surface = Color(0xFFFFFFFF),
    surfaceAlt = Color(0xFFEFEDF7),
    border = Color(0xFFDEDBEC),
    textPrimary = Color(0xFF201C2E),
    textSecondary = Color(0xFF5B5570),
    textTertiary = Color(0xFF8A8499),
    hoverColor = Color(0xFFF0EEF9),
    errorBackground = Color(0xFFFCE8E6),
    errorForeground = Color(0xFFB3261E),
    typeFolder = Color(0xFFC08A2E),
    typeDocument = Color(0xFF3B6FC4),
    typeImage = Color(0xFFC23B7A),
    typeVideo = Color(0xFF7C4FD1),
    typeAudio = Color(0xFF1F9D6B),
    typeExecutable = Color(0xFFC24A3B),
    typeArchive = Color(0xFFC4762E),
    typeCode = Color(0xFF2E8FB0),
    typeOther = Color(0xFF6B6478),
)

@Suppress("MagicNumber") // A palette's colors are self-documenting hex literals, not magic numbers.
val DarkAppColors = AppColors(
    accent = Color(0xFF9B8CFF),
    onAccent = Color(0xFF1B1730),
    background = Color(0xFF17151F),
    surface = Color(0xFF1D1A29),
    surfaceAlt = Color(0xFF242030),
    border = Color(0xFF322C42),
    textPrimary = Color(0xFFF1EFF9),
    textSecondary = Color(0xFFB4AEC4),
    textTertiary = Color(0xFF847E96),
    hoverColor = Color(0xFF2A2538),
    errorBackground = Color(0xFF442726),
    errorForeground = Color(0xFFF2B8B5),
    typeFolder = Color(0xFFE0B44D),
    typeDocument = Color(0xFF7FA6EE),
    typeImage = Color(0xFFE37FB3),
    typeVideo = Color(0xFFB39AF0),
    typeAudio = Color(0xFF5FD1A0),
    typeExecutable = Color(0xFFE38A7F),
    typeArchive = Color(0xFFE3A46E),
    typeCode = Color(0xFF7FCBE3),
    typeOther = Color(0xFFA39CB0),
)

/** The tint used for a file's icon and Type-column text in the results grid. */
fun AppColors.colorFor(filter: SearchFilter): Color = when (filter) {
    SearchFilter.DOCUMENT -> typeDocument
    SearchFilter.IMAGE -> typeImage
    SearchFilter.VIDEO -> typeVideo
    SearchFilter.AUDIO -> typeAudio
    SearchFilter.EXECUTABLE -> typeExecutable
    SearchFilter.ARCHIVE -> typeArchive
    SearchFilter.CODE -> typeCode
    SearchFilter.ALL, SearchFilter.OTHER -> typeOther
}

/** Provided by [org.example.fastfinder.ui.FastFinderApp] based on the current light/dark toggle. */
val LocalAppColors = staticCompositionLocalOf { LightAppColors }

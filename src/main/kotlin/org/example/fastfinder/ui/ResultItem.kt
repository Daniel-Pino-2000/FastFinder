package org.example.fastfinder.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.padding
import androidx.compose.material.Icon
import androidx.compose.material.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.ExperimentalComposeUiApi
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.input.pointer.PointerEventType
import androidx.compose.ui.input.pointer.onPointerEvent
import androidx.compose.ui.unit.dp
import org.example.fastfinder.model.SearchFilter
import org.example.fastfinder.model.SystemItem
import org.example.fastfinder.ui.theme.AppTheme
import org.example.fastfinder.ui.theme.LocalAppColors
import org.example.fastfinder.util.formatSize
import org.example.fastfinder.util.getFileType
import java.awt.Desktop
import java.io.File

@OptIn(ExperimentalComposeUiApi::class, ExperimentalFoundationApi::class)
@Composable
fun ResultItem(item: SystemItem) {
    var isHovered by remember { mutableStateOf(false) }
    val icon = remember(item.isFile, item.itemPath) { iconFor(item) }
    val sizeLabel = remember(item.itemSize) { item.itemSize?.let(::formatSize) }
    val appColors = LocalAppColors.current

    Row(
        modifier = Modifier
            .onPointerEvent(PointerEventType.Enter) { isHovered = true }
            .onPointerEvent(PointerEventType.Exit) { isHovered = false }
            .background(color = if (isHovered) appColors.hoverColor else appColors.lazyColumnColor)
            .combinedClickable(
                interactionSource = remember { MutableInteractionSource() },
                indication = null,
                onClick = {},
                onDoubleClick = { openItem(item.itemPath) },
            )
            .padding(8.dp)
    ) {
        Icon(icon, contentDescription = null, modifier = Modifier.padding(8.dp))

        Text(text = item.itemPath, modifier = Modifier.padding(8.dp).weight(1f))

        sizeLabel?.let { Text(text = it, modifier = Modifier.padding(8.dp)) }

        if (isHovered) {
            Icon(
                AppTheme.openFolderIcon,
                contentDescription = "Open containing folder",
                modifier = Modifier.padding(8.dp).clickable { openContainingFolder(item.itemPath) }
            )
        }
    }
}

private fun iconFor(item: SystemItem): ImageVector {
    if (!item.isFile) return AppTheme.folderIcon
    return when (getFileType(File(item.itemPath))) {
        SearchFilter.IMAGE -> AppTheme.imageFileIcon
        SearchFilter.VIDEO -> AppTheme.videoFileIcon
        SearchFilter.DOCUMENT -> AppTheme.documentFileIcon
        SearchFilter.AUDIO -> AppTheme.audioFileIcon
        SearchFilter.EXECUTABLE -> AppTheme.executableFileIcon
        SearchFilter.ARCHIVE -> AppTheme.archiveFileIcon
        SearchFilter.CODE -> AppTheme.codeFileIcon
        SearchFilter.ALL, SearchFilter.OTHER -> AppTheme.fileIcon
    }
}

private fun openContainingFolder(path: String) {
    val parent = File(path).parentFile
    if (parent != null && parent.exists()) {
        Desktop.getDesktop().browse(parent.toURI())
    }
}

/** Opens a file with its default associated app, or a directory in Explorer - same as double-clicking it there. */
private fun openItem(path: String) {
    val file = File(path)
    if (file.exists()) {
        Desktop.getDesktop().open(file)
    }
}

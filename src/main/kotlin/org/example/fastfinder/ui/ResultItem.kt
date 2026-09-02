package org.example.fastfinder.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.Icon
import androidx.compose.material.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.ExperimentalComposeUiApi
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.input.pointer.PointerEventType
import androidx.compose.ui.input.pointer.onPointerEvent
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import org.example.fastfinder.model.SearchFilter
import org.example.fastfinder.model.SystemItem
import org.example.fastfinder.ui.theme.AppTheme
import org.example.fastfinder.ui.theme.LocalAppColors
import org.example.fastfinder.ui.theme.colorFor
import org.example.fastfinder.util.Logger
import org.example.fastfinder.util.formatSize
import org.example.fastfinder.util.getFileType
import org.example.fastfinder.util.singularLabel
import java.awt.Desktop
import java.awt.Toolkit
import java.awt.datatransfer.StringSelection
import java.io.File

@OptIn(ExperimentalComposeUiApi::class, ExperimentalFoundationApi::class)
@Composable
fun ResultItem(item: SystemItem) {
    var isHovered by remember { mutableStateOf(false) }
    val fileType = remember(item.isFile, item.itemPath) { if (item.isFile) getFileType(File(item.itemPath)) else null }
    val icon = remember(item.isFile, item.itemPath) { iconFor(item) }
    val sizeLabel = remember(item.itemSize) { item.itemSize?.let(::formatSize) ?: "—" }
    val appColors = LocalAppColors.current
    val iconTint = fileType?.let { appColors.colorFor(it) } ?: appColors.typeFolder

    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = Modifier
            .onPointerEvent(PointerEventType.Enter) { isHovered = true }
            .onPointerEvent(PointerEventType.Exit) { isHovered = false }
            .background(color = if (isHovered) appColors.hoverColor else Color.Transparent)
            .combinedClickable(
                interactionSource = remember { MutableInteractionSource() },
                indication = null,
                onClick = {},
                onDoubleClick = { openItem(item.itemPath) },
            )
            .padding(horizontal = 12.dp, vertical = 7.dp)
    ) {
        Box(modifier = Modifier.width(ICON_COLUMN_WIDTH)) {
            Icon(icon, contentDescription = null, tint = iconTint, modifier = Modifier.width(18.dp))
        }

        Text(
            text = item.itemPath.substringAfterLast(File.separatorChar),
            color = appColors.textPrimary,
            fontSize = 12.5.sp,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.weight(NAME_COLUMN_WEIGHT).padding(end = 8.dp),
        )
        Text(
            text = item.itemPath,
            color = appColors.textSecondary,
            fontSize = 11.5.sp,
            fontFamily = FontFamily.Monospace,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.weight(PATH_COLUMN_WEIGHT).padding(end = 8.dp),
        )
        Text(
            text = sizeLabel,
            color = appColors.textPrimary,
            fontSize = 12.sp,
            maxLines = 1,
            modifier = Modifier.width(SIZE_COLUMN_WIDTH),
        )
        Text(
            text = fileType?.singularLabel ?: "Folder",
            color = appColors.textSecondary,
            fontSize = 12.sp,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.width(TYPE_COLUMN_WIDTH).padding(start = 8.dp),
        )

        // Both icons are always emitted (never conditionally, just made invisible/inert via
        // RowActionIcon's own `visible` flag) so this Row's height - and with it the whole row's
        // height, since Row sizes itself to its tallest child - never changes between hovered and
        // not. It used to only emit them while isHovered, which made the actions column taller
        // than empty space the instant the mouse entered, growing the row and re-centering every
        // other column's already-centered content right under the cursor.
        Row(modifier = Modifier.width(ACTIONS_COLUMN_WIDTH), horizontalArrangement = Arrangement.spacedBy(4.dp)) {
            RowActionIcon(
                icon = AppTheme.copyIcon,
                contentDescription = "Copy path",
                visible = isHovered,
                onClick = { copyPathToClipboard(item.itemPath) },
            )
            RowActionIcon(
                icon = AppTheme.openFolderIcon,
                contentDescription = "Open containing folder",
                visible = isHovered,
                onClick = { openContainingFolder(item.itemPath) },
            )
        }
    }
}

/**
 * A per-row hover action icon - sized well past its 14dp glyph (28dp clickable box, with its own
 * hover highlight) since the glyph alone was too small a target to click reliably.
 *
 * Always occupies its 28dp box regardless of [visible] - only its own contents (icon, background,
 * click handling) are conditional - so the row it sits in never resizes depending on whether it's
 * shown, which is the whole point of [visible] existing instead of the caller simply not emitting
 * this composable at all while hidden.
 */
@OptIn(ExperimentalComposeUiApi::class)
@Composable
private fun RowActionIcon(icon: ImageVector, contentDescription: String, visible: Boolean, onClick: () -> Unit) {
    var isHovered by remember { mutableStateOf(false) }
    val appColors = LocalAppColors.current

    Box(
        modifier = Modifier
            .width(28.dp)
            .padding(2.dp)
            .clip(CircleShape)
            .background(if (isHovered) appColors.background else Color.Transparent)
            .onPointerEvent(PointerEventType.Enter) { isHovered = true }
            .onPointerEvent(PointerEventType.Exit) { isHovered = false }
            .then(if (visible) Modifier.clickable(onClick = onClick) else Modifier)
            .padding(5.dp),
        contentAlignment = Alignment.Center,
    ) {
        if (visible) {
            Icon(
                icon,
                contentDescription = contentDescription,
                tint = appColors.textSecondary,
                modifier = Modifier.width(16.dp),
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

/** Best-effort: a locked clipboard (another process briefly holding it) throws IllegalStateException. */
internal fun copyPathToClipboard(path: String) {
    runCatching {
        Toolkit.getDefaultToolkit().systemClipboard.setContents(StringSelection(path), null)
    }.onFailure { Logger.warn("Could not copy path to clipboard: ${it.message}") }
}

/**
 * Best-effort: Desktop.browse/open throw IOException for very ordinary cases - no shell handler
 * registered, the file locked by another process - which would otherwise be an uncaught exception
 * on the composition thread from a single click.
 */
private fun openContainingFolder(path: String) {
    val parent = File(path).parentFile
    if (parent != null && parent.exists()) {
        runCatching { Desktop.getDesktop().browse(parent.toURI()) }
            .onFailure { Logger.warn("Could not open containing folder for $path: ${it.message}") }
    }
}

/** Opens a file with its default associated app, or a directory in Explorer - same as double-clicking it there. */
private fun openItem(path: String) {
    val file = File(path)
    if (file.exists()) {
        runCatching { Desktop.getDesktop().open(file) }
            .onFailure { Logger.warn("Could not open $path: ${it.message}") }
    }
}

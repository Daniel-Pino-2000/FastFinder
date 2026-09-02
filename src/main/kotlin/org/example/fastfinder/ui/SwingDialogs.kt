package org.example.fastfinder.ui

import java.io.File
import javax.swing.JFileChooser
import javax.swing.JOptionPane
import javax.swing.filechooser.FileSystemView

fun showDirectoryPicker(): File? {
    val fileChooser = JFileChooser(FileSystemView.getFileSystemView().homeDirectory).apply {
        dialogTitle = "Select Directory"
        fileSelectionMode = JFileChooser.DIRECTORIES_ONLY
        isAcceptAllFileFilterUsed = false
    }

    return if (fileChooser.showOpenDialog(null) == JFileChooser.APPROVE_OPTION) {
        fileChooser.selectedFile
    } else {
        null
    }
}

fun showIndexNotReadyMessage() {
    JOptionPane.showMessageDialog(
        null,
        "The database is currently being created. Please wait until indexing is complete, or use Custom Search for a specific folder.",
        "Index Creation In Progress",
        JOptionPane.INFORMATION_MESSAGE
    )
}

/**
 * Shown when this launch loses the single-instance lock (see [org.example.fastfinder.SingleInstance])
 * to another already-running copy, so a user who double-clicked the app again sees why nothing
 * new opened instead of appearing to do nothing at all. The other copy may still be mid-startup
 * (e.g. waiting on its own UAC prompt) rather than fully open yet, hence "starting or running"
 * rather than pointing straight at the taskbar.
 */
fun showAlreadyRunningMessage() {
    JOptionPane.showMessageDialog(
        null,
        "FastFinder is already starting or running. Check your taskbar, or the desktop for a pending Administrator prompt.",
        "FastFinder Already Running",
        JOptionPane.INFORMATION_MESSAGE
    )
}

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

/**
 * Shown once ever, right before the very first Windows UAC prompt this install triggers (see
 * [org.example.fastfinder.Elevation]) - without it, a new user's first encounter with FastFinder
 * would be an unexplained admin-access prompt with no context for why a search tool needs it.
 */
fun showElevationExplanationMessage() {
    JOptionPane.showMessageDialog(
        null,
        "FastFinder is about to request Administrator access.\n\n" +
            "This lets it read the Windows NTFS USN Journal, so it can instantly catch up on " +
            "files that changed while it wasn't running - instead of rescanning your drives " +
            "from scratch every time it starts.\n\n" +
            "If you choose \"No\" on the prompt that follows, FastFinder still works normally - " +
            "it just won't be able to catch up on those offline changes as precisely.\n\n" +
            "You'll only see this explanation once.",
        "Why FastFinder Requests Administrator Access",
        JOptionPane.INFORMATION_MESSAGE
    )
}

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
        "FastFinder is already starting or running. Check your taskbar, or the desktop for a " +
            "pending Administrator prompt.",
        "FastFinder Already Running",
        JOptionPane.INFORMATION_MESSAGE
    )
}

/**
 * Confirms before the settings toggle triggers an elevated relaunch - flipping it doesn't just
 * flip a setting silently, it restarts the whole app with Administrator access, which deserves a
 * clear "this is about to happen" moment rather than the window just closing without warning.
 */
fun confirmEnableFastSync(): Boolean {
    val choice = JOptionPane.showConfirmDialog(
        null,
        "Enabling fast update tracking restarts FastFinder with Administrator access.\n\n" +
            "This lets it catch up on file changes instantly instead of a full rescan. " +
            "Windows will ask you to confirm this.\n\n" +
            "Restart now?",
        "Restart Required",
        JOptionPane.YES_NO_OPTION,
        JOptionPane.QUESTION_MESSAGE,
    )
    return choice == JOptionPane.YES_OPTION
}

/**
 * Shown when the user opts into fast update tracking (see FilterRail's settings toggle) but then
 * declines the Administrator prompt that follows, or elevation otherwise fails - confirms the
 * setting stayed off rather than leaving them wondering whether it silently took effect.
 */
fun showElevationDeclinedMessage() {
    JOptionPane.showMessageDialog(
        null,
        "Administrator access wasn't granted, so fast update tracking stays off. " +
            "FastFinder still works normally - you can try enabling it again any time.",
        "Fast Update Tracking Not Enabled",
        JOptionPane.INFORMATION_MESSAGE
    )
}

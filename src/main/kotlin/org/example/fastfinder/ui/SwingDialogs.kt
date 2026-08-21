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

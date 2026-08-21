package org.example.fastfinder

import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Window
import androidx.compose.ui.window.application
import androidx.compose.ui.window.rememberWindowState
import org.example.fastfinder.index.DBManager
import org.example.fastfinder.ui.FastFinderApp
import java.awt.Dimension

fun main() = application {
    val dbManager = remember { DBManager() }
    val windowState = rememberWindowState(width = 1030.dp, height = 700.dp)

    LaunchedEffect(dbManager) {
        dbManager.createOrUpdateIndex(forceIndexCreation = false)
    }

    Window(
        onCloseRequest = ::exitApplication,
        title = "FastFinder",
        state = windowState,
    ) {
        window.minimumSize = Dimension(800, 600)
        FastFinderApp(dbManager)
    }
}

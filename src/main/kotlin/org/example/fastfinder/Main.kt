package org.example.fastfinder

import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.ApplicationScope
import androidx.compose.ui.window.Window
import androidx.compose.ui.window.application
import androidx.compose.ui.window.rememberWindowState
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.withContext
import org.example.fastfinder.index.DBManager
import org.example.fastfinder.ui.FastFinderApp
import org.example.fastfinder.util.AppPreferencesStore
import java.awt.Dimension

private const val WINDOW_SIZE_SAVE_DEBOUNCE_MS = 500L

fun main(args: Array<String>) {
    if (!ensureElevated(args)) return

    application {
        runApp()
    }
}

@Composable
private fun ApplicationScope.runApp() {
    val dbManager = remember { DBManager() }
    val initialPreferences = remember { AppPreferencesStore.load() }
    val windowState = rememberWindowState(
        width = initialPreferences.windowWidth.dp,
        height = initialPreferences.windowHeight.dp,
    )

    LaunchedEffect(dbManager) {
        dbManager.createOrUpdateIndex(forceIndexCreation = false)
    }

    DisposableEffect(dbManager) {
        onDispose { dbManager.close() }
    }

    // Persists window size across restarts, debounced so a drag-resize doesn't write on
    // every intermediate frame. Reads the current file before writing so this doesn't
    // clobber the theme/filter/sort preferences FastFinderApp saves independently.
    LaunchedEffect(windowState.size) {
        delay(WINDOW_SIZE_SAVE_DEBOUNCE_MS)
        withContext(Dispatchers.IO) {
            AppPreferencesStore.save(
                AppPreferencesStore.load().copy(
                    windowWidth = windowState.size.width.value.toInt(),
                    windowHeight = windowState.size.height.value.toInt(),
                )
            )
        }
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

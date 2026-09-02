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
import org.example.fastfinder.ui.showAlreadyRunningMessage
import org.example.fastfinder.util.AppPreferencesStore
import org.example.fastfinder.util.Logger
import java.awt.Dimension
import java.awt.GraphicsEnvironment

private const val WINDOW_SIZE_SAVE_DEBOUNCE_MS = 500L
private const val MIN_WINDOW_WIDTH = 800
private const val MIN_WINDOW_HEIGHT = 600

// A first-ever launch sizes the window as a fraction of the current screen instead of a flat
// pixel default - a flat default only ever gets clamped *down* to fit a small screen (never
// scaled to look proportionate on one), so on a common laptop display it used to read as nearly
// full-screen despite not being maximized. These match roughly what VS Code/JetBrains IDEs open
// at on first run: comfortably below full screen on any display, not just large ones.
private const val DEFAULT_WINDOW_WIDTH_FRACTION = 0.7
private const val DEFAULT_WINDOW_HEIGHT_FRACTION = 0.75

fun main(args: Array<String>) {
    // Acquired before elevation, not after: a process that hasn't elevated yet still holds this
    // lock while its UAC prompt is up, so a near-simultaneous second launch (e.g. an impatient
    // double-click) is turned away here instead of firing its own redundant UAC prompt. See
    // SingleInstance's class doc for the full rationale.
    if (!SingleInstance.acquire()) {
        Logger.info("Another FastFinder instance is already running; exiting.")
        showAlreadyRunningMessage()
        return
    }

    if (!ensureElevated(args)) {
        // This process only relaunched itself elevated and is about to exit - release the lock
        // explicitly rather than waiting on process exit, so the elevated child it just spawned
        // doesn't fail its own acquire() while this one is still shutting down.
        SingleInstance.release()
        return
    }

    application {
        runApp()
    }
}

@Composable
private fun ApplicationScope.runApp() {
    val dbManager = remember { DBManager() }
    val initialPreferences = remember { AppPreferencesStore.load() }
    // Clamp a persisted size to [MIN_WINDOW_WIDTH/HEIGHT, current screen's usable area]. The
    // upper bound guards against a size saved on a larger/different monitor reopening oversized
    // or partly off-screen; the lower bound guards against a degenerate persisted value (a
    // hand-edited or corrupted preferences file) reopening a near-invisible, unusable window -
    // with no other running instance to fall back to (see SingleInstance), that would otherwise
    // leave no way to recover short of editing the file directly.
    val screenBounds = remember { GraphicsEnvironment.getLocalGraphicsEnvironment().maximumWindowBounds }
    val windowState = rememberWindowState(
        width = (initialPreferences.windowWidth ?: (screenBounds.width * DEFAULT_WINDOW_WIDTH_FRACTION).toInt())
            .clampToScreen(MIN_WINDOW_WIDTH, screenBounds.width).dp,
        height = (initialPreferences.windowHeight ?: (screenBounds.height * DEFAULT_WINDOW_HEIGHT_FRACTION).toInt())
            .clampToScreen(MIN_WINDOW_HEIGHT, screenBounds.height).dp,
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
            AppPreferencesStore.update {
                it.copy(
                    windowWidth = windowState.size.width.value.toInt(),
                    windowHeight = windowState.size.height.value.toInt(),
                )
            }
        }
    }

    Window(
        onCloseRequest = ::exitApplication,
        title = "FastFinder",
        state = windowState,
    ) {
        window.minimumSize = Dimension(MIN_WINDOW_WIDTH, MIN_WINDOW_HEIGHT)
        FastFinderApp(dbManager)
    }
}

/**
 * Clamps a persisted window dimension to [[minimum], the current screen's usable size] -
 * coerceAtLeast on [screenMax] guards against coerceIn throwing if an unusually small/virtual
 * display's usable area ends up below [minimum].
 */
private fun Int.clampToScreen(minimum: Int, screenMax: Int): Int = coerceIn(minimum, screenMax.coerceAtLeast(minimum))

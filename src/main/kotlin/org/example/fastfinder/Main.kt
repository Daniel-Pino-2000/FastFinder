package org.example.fastfinder

import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.ApplicationScope
import androidx.compose.ui.window.Window
import androidx.compose.ui.window.application
import androidx.compose.ui.window.rememberWindowState
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.example.fastfinder.index.DBManager
import org.example.fastfinder.ui.FastFinderApp
import org.example.fastfinder.ui.FastSyncState
import org.example.fastfinder.ui.showAlreadyRunningMessage
import org.example.fastfinder.ui.showElevationDeclinedMessage
import org.example.fastfinder.util.AppPreferencesStore
import org.example.fastfinder.util.Logger
import java.awt.Dimension
import java.awt.GraphicsEnvironment
import javax.swing.UIManager

private const val WINDOW_SIZE_SAVE_DEBOUNCE_MS = 500L

// The smallest size the fixed parts of the layout actually need to render without clipping or
// collapsing: the 224dp filter rail plus the results table's icon/size/type/actions columns and
// their paddings (fixed, ~324dp total) plus a still-legible floor for the weighted Name/Path
// columns (~230dp) - go narrower than this and there's nowhere left to put Name/Path text at all.
// The filter rail can scroll internally as a fallback (see FilterRail) if it ever doesn't fit, but
// this height is set generously enough (verified by actually launching the app and measuring the
// exact height needed) to clear the rail's natural content - every filter section, the sync row,
// and all three action buttons - with room to spare, so scrolling should rarely if ever actually
// be needed in normal use.
private const val STRUCTURAL_MIN_WIDTH = 780
private const val STRUCTURAL_MIN_HEIGHT = 830

// A hard floor below STRUCTURAL_MIN_WIDTH/HEIGHT, only reached on a screen too small to offer the
// structural minimum at all (e.g. a small secondary/virtual display) - the window still needs
// *some* usable size rather than being forced larger than the screen itself, even though content
// will then need scrolling to fit.
private const val ABSOLUTE_FLOOR_WIDTH = 480
private const val ABSOLUTE_FLOOR_HEIGHT = 360

// A first-ever launch sizes the window as a fraction of the current screen instead of a flat
// pixel default - a flat default only ever gets clamped *down* to fit a small screen (never
// scaled to look proportionate on one), so on a common laptop display it used to read as nearly
// full-screen despite not being maximized. These match roughly what VS Code/JetBrains IDEs open
// at on first run: comfortably below full screen on any display, not just large ones.
private const val DEFAULT_WINDOW_WIDTH_FRACTION = 0.7
private const val DEFAULT_WINDOW_HEIGHT_FRACTION = 0.75

fun main(args: Array<String>) {
    // Without this, Swing's dialogs (the folder picker, the elevation/error prompts) render with
    // whichever look-and-feel the launching JVM happens to default to - which differs by JDK
    // vendor/build (e.g. JetBrains Runtime vs. a plain OpenJDK build), so the same code can show
    // a native-looking Windows folder picker under one JVM and the plain cross-platform "Metal"
    // look under another. Forcing it here makes every Swing dialog match the OS consistently
    // regardless of which JVM launched the app. Best-effort: a failure here just leaves Swing's
    // default in place rather than blocking startup over a cosmetic setting.
    runCatching { UIManager.setLookAndFeel(UIManager.getSystemLookAndFeelClassName()) }

    // Acquired before elevation, not after: a process that hasn't elevated yet still holds this
    // lock while its UAC prompt is up, so a near-simultaneous second launch (e.g. an impatient
    // double-click) is turned away here instead of firing its own redundant UAC prompt. See
    // SingleInstance's class doc for the full rationale.
    if (!SingleInstance.acquire()) {
        Logger.info("Another FastFinder instance is already running; exiting.")
        showAlreadyRunningMessage()
        return
    }

    // Elevation is opt-in (see Elevation.kt's file doc) - only attempted here at all if this
    // *is* the elevated relaunch of an earlier process, or a returning user already turned on
    // fast update tracking last session. A first-ever launch skips this entirely: no UAC prompt,
    // no "why is this app asking for admin" moment. Enabling the setting for the first time is
    // instead handled at runtime, from the settings toggle in FastFinderApp/FilterRail.
    val wantsElevation = isElevatedRelaunch(args) || AppPreferencesStore.load().elevationEnabled
    if (wantsElevation && !ensureElevated(args)) {
        // This process only relaunched itself elevated and is about to exit - release the lock
        // explicitly rather than waiting on process exit, so the elevated child it just spawned
        // doesn't fail its own acquire() while this one is still shutting down.
        SingleInstance.release()
        return
    }

    application {
        runApp(args)
    }
}

@Composable
private fun ApplicationScope.runApp(args: Array<String>) {
    val dbManager = remember { DBManager() }
    val initialPreferences = remember { AppPreferencesStore.load() }
    val coroutineScope = rememberCoroutineScope()
    // Whether this process itself is currently elevated - fixed for the process's whole
    // lifetime, unlike fastSyncEnabled below: a successful elevation always exits this process to
    // hand off to a freshly-relaunched elevated one (see enableFastSync), so there's no "still
    // this same session, but now elevated" state to react to - only ever computed once, at
    // startup. A user can also end up elevated without ever touching the setting (e.g. manually
    // running the exe "as Administrator"); this correctly reflects that either way.
    val isElevated = remember { isProcessElevated() }
    // The setting as the user currently intends it, which - unlike isElevated - a toggle can
    // freely flip in either direction without restarting anything: turning it on requires
    // elevating (a real restart, handled by enableFastSync), but turning it off never does,
    // since "don't auto-elevate next time" needs no privilege at all to take effect. Starts true
    // whenever this process is already elevated, even if the preference itself hadn't caught up
    // yet (e.g. the user launched "as Administrator" manually).
    var fastSyncEnabled by remember { mutableStateOf(isElevated || initialPreferences.elevationEnabled) }
    var isAwaitingElevation by remember { mutableStateOf(false) }

    // Handles the settings toggle's "enable fast update tracking" action. If this session is
    // already elevated (the toggle was switched off then straight back on, or the user launched
    // "as Administrator" manually), there's nothing to restart - just re-arm the preference for
    // future launches. Otherwise this attempts elevation right now, at runtime, rather than only
    // ever at startup: a relaunch that succeeds spawns a brand new elevated process that
    // immediately tries to acquire the very same single-instance lock this one is still holding -
    // release it first, before anything else here, so that new process doesn't lose the race and
    // mistake this (about-to-exit) one for a second instance and quit with no window ever shown.
    // dbManager.close() then releases this process's own hold on the index (the watcher's
    // write.lock) too, but that failure mode is the graceful one: the new process just logs a
    // warning and runs without live updates for this session if it loses that particular race,
    // instead of not opening at all.
    fun enableFastSync() {
        if (isAwaitingElevation) return
        if (isElevated) {
            fastSyncEnabled = true
            coroutineScope.launch {
                withContext(Dispatchers.IO) { AppPreferencesStore.update { it.copy(elevationEnabled = true) } }
            }
            return
        }
        isAwaitingElevation = true
        coroutineScope.launch {
            val relaunching = withContext(Dispatchers.IO) { !ensureElevated(args) }
            if (relaunching) {
                AppPreferencesStore.update { it.copy(elevationEnabled = true) }
                SingleInstance.release()
                withContext(Dispatchers.IO) { dbManager.close() }
                exitApplication()
            } else {
                isAwaitingElevation = false
                showElevationDeclinedMessage()
            }
        }
    }

    // The other direction needs none of enableFastSync's restart machinery: Windows never lets a
    // running process drop privileges it already has, so a session that's actually elevated stays
    // elevated regardless of this setting - only the *next* launch reads it, to decide whether to
    // auto-elevate at all. Flipping it off here just stops that next launch from doing so.
    fun disableFastSync() {
        fastSyncEnabled = false
        coroutineScope.launch {
            withContext(Dispatchers.IO) { AppPreferencesStore.update { it.copy(elevationEnabled = false) } }
        }
    }

    val screenBounds = remember { GraphicsEnvironment.getLocalGraphicsEnvironment().maximumWindowBounds }
    // Adapts to the current screen rather than a flat constant: normally the structural minimum
    // the layout needs, but never forced larger than the screen itself has room for - see
    // STRUCTURAL_MIN_WIDTH/HEIGHT's doc.
    val minWidth = remember(screenBounds) {
        STRUCTURAL_MIN_WIDTH.coerceAtMost(screenBounds.width).coerceAtLeast(ABSOLUTE_FLOOR_WIDTH)
    }
    val minHeight = remember(screenBounds) {
        STRUCTURAL_MIN_HEIGHT.coerceAtMost(screenBounds.height).coerceAtLeast(ABSOLUTE_FLOOR_HEIGHT)
    }
    val windowState = rememberWindowState(
        width = resolveWindowDimension(
            initialPreferences.windowWidth, minWidth, screenBounds.width, DEFAULT_WINDOW_WIDTH_FRACTION,
        ).dp,
        height = resolveWindowDimension(
            initialPreferences.windowHeight, minHeight, screenBounds.height, DEFAULT_WINDOW_HEIGHT_FRACTION,
        ).dp,
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
        window.minimumSize = Dimension(minWidth, minHeight)
        FastFinderApp(
            dbManager = dbManager,
            fastSync = FastSyncState(
                isElevated = isElevated,
                fastSyncEnabled = fastSyncEnabled,
                isAwaitingElevation = isAwaitingElevation,
                onEnable = ::enableFastSync,
                onDisable = ::disableFastSync,
            ),
        )
    }
}

/**
 * A persisted window dimension is honored as-is only if it still fits this screen
 * ([minimum]..[screenMax]) - otherwise (never set, hand-edited/corrupted, or saved on a larger or
 * differently-scaled display that no longer applies here) this recomputes a fresh
 * [fraction]-of-screen default rather than merely clamping the stale value down to the screen's
 * exact edge, which would otherwise make an oversized leftover value from another display look
 * essentially full-screen here instead of comfortably smaller than it, defeating the point of a
 * proportional default. coerceAtLeast on [screenMax] guards against coerceIn throwing if an
 * unusually small/virtual display's usable area ends up below [minimum].
 */
private fun resolveWindowDimension(persisted: Int?, minimum: Int, screenMax: Int, fraction: Double): Int {
    val fitsScreen = persisted != null && persisted in minimum..screenMax
    val value = if (fitsScreen) persisted!! else (screenMax * fraction).toInt()
    return value.coerceIn(minimum, screenMax.coerceAtLeast(minimum))
}

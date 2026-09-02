package org.example.fastfinder.util

import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import org.example.fastfinder.model.SearchFilter
import org.example.fastfinder.model.SearchMode
import org.example.fastfinder.model.SizeFilter
import org.example.fastfinder.model.SortBy
import java.io.IOException
import java.nio.file.Files
import java.nio.file.Path
import java.util.Properties

/** User-facing settings that persist across app restarts - everything but the search query itself. */
data class AppPreferences(
    val darkTheme: Boolean = false,
    val searchMode: SearchMode = SearchMode.ALL,
    val resultFilter: SearchFilter = SearchFilter.ALL,
    val sizeFilter: SizeFilter = SizeFilter.ANY,
    val sortBy: SortBy = SortBy.NAME,
    val sortAscending: Boolean = true,
    // Null (rather than some fixed default like 1030x700) until the window is first resized/shown
    // - a flat pixel default would only ever get clamped *down* to fit a small screen, never
    // scaled to look proportionate on one, so it'd end up nearly filling a small/laptop display
    // on first launch. Main.kt is where the actual screen-relative default gets computed, since
    // this file has no access to the current screen's bounds.
    val windowWidth: Int? = null,
    val windowHeight: Int? = null,
    // Gates the one-time dialog explaining why FastFinder requests Administrator access (see
    // Elevation.kt) - read/written directly from main(), before elevation and before Compose
    // (and this store's own mutex-guarded update()) exist, so it must be a plain field here
    // rather than a separate file with its own concurrency story.
    val hasSeenElevationExplanation: Boolean = false,
)

/**
 * Loads/saves [AppPreferences] to a small properties file under [AppPaths.root]. Best-effort:
 * a missing or unreadable file just falls back to defaults, and a failed save is logged but
 * never surfaced to the user - losing a saved preference isn't worth interrupting anything for.
 */
object AppPreferencesStore {
    private val defaultFile: Path = AppPaths.root.resolve("preferences.properties")
    // Guards read-modify-write cycles against the file: Main.kt (window size) and FastFinderApp.kt
    // (theme/filter/sort) each independently load-copy-save it on their own trigger, with no
    // shared state between them - without this, a resize landing near a filter/theme change could
    // have one's load() read a snapshot from before the other's save() lands, then silently
    // overwrite that change when it saves its own.
    private val mutex = Mutex()

    fun load(file: Path = defaultFile): AppPreferences {
        if (!Files.exists(file)) return AppPreferences()

        val props = Properties()
        return try {
            Files.newBufferedReader(file).use(props::load)
            AppPreferences(
                darkTheme = props.getProperty("darkTheme").toBooleanOr(false),
                searchMode = props.getProperty("searchMode").toEnumOr(SearchMode.ALL),
                resultFilter = props.getProperty("resultFilter").toEnumOr(SearchFilter.ALL),
                sizeFilter = props.getProperty("sizeFilter").toEnumOr(SizeFilter.ANY),
                sortBy = props.getProperty("sortBy").toEnumOr(SortBy.NAME),
                sortAscending = props.getProperty("sortAscending").toBooleanOr(true),
                windowWidth = props.getProperty("windowWidth")?.toIntOrNull(),
                windowHeight = props.getProperty("windowHeight")?.toIntOrNull(),
                hasSeenElevationExplanation = props.getProperty("hasSeenElevationExplanation").toBooleanOr(false),
            )
        } catch (e: IOException) {
            Logger.warn("Could not read preferences file, falling back to defaults: ${e.message}")
            AppPreferences()
        }
    }

    fun save(preferences: AppPreferences, file: Path = defaultFile) {
        val props = Properties().apply {
            setProperty("darkTheme", preferences.darkTheme.toString())
            setProperty("searchMode", preferences.searchMode.name)
            setProperty("resultFilter", preferences.resultFilter.name)
            setProperty("sizeFilter", preferences.sizeFilter.name)
            setProperty("sortBy", preferences.sortBy.name)
            setProperty("sortAscending", preferences.sortAscending.toString())
            // Left unset rather than writing a placeholder if the window was never actually
            // shown/resized yet (see the field doc) - lets load() keep telling "never set" apart
            // from "explicitly set to some value" on the next read.
            preferences.windowWidth?.let { setProperty("windowWidth", it.toString()) }
            preferences.windowHeight?.let { setProperty("windowHeight", it.toString()) }
            setProperty("hasSeenElevationExplanation", preferences.hasSeenElevationExplanation.toString())
        }
        try {
            Files.createDirectories(file.toAbsolutePath().parent)
            Files.newBufferedWriter(file).use { writer -> props.store(writer, "FastFinder user preferences") }
        } catch (e: IOException) {
            Logger.warn("Could not save preferences: ${e.message}")
        }
    }

    /** Atomically applies [transform] to the currently-saved preferences - see [mutex]. */
    suspend fun update(file: Path = defaultFile, transform: (AppPreferences) -> AppPreferences) {
        mutex.withLock { save(transform(load(file)), file) }
    }
}

private fun String?.toBooleanOr(default: Boolean): Boolean = this?.toBooleanStrictOrNull() ?: default

private inline fun <reified T : Enum<T>> String?.toEnumOr(default: T): T =
    this?.let { name -> runCatching { enumValueOf<T>(name) }.getOrNull() } ?: default

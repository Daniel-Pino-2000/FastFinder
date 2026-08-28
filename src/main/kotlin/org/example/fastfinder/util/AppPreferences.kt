package org.example.fastfinder.util

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
    val windowWidth: Int = 1030,
    val windowHeight: Int = 700,
)

/**
 * Loads/saves [AppPreferences] to a small properties file under [AppPaths.root]. Best-effort:
 * a missing or unreadable file just falls back to defaults, and a failed save is logged but
 * never surfaced to the user - losing a saved preference isn't worth interrupting anything for.
 */
object AppPreferencesStore {
    private val defaultFile: Path = AppPaths.root.resolve("preferences.properties")

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
                windowWidth = props.getProperty("windowWidth")?.toIntOrNull() ?: 1030,
                windowHeight = props.getProperty("windowHeight")?.toIntOrNull() ?: 700,
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
            setProperty("windowWidth", preferences.windowWidth.toString())
            setProperty("windowHeight", preferences.windowHeight.toString())
        }
        try {
            Files.createDirectories(file.toAbsolutePath().parent)
            Files.newBufferedWriter(file).use { writer -> props.store(writer, "FastFinder user preferences") }
        } catch (e: IOException) {
            Logger.warn("Could not save preferences: ${e.message}")
        }
    }
}

private fun String?.toBooleanOr(default: Boolean): Boolean = this?.toBooleanStrictOrNull() ?: default

private inline fun <reified T : Enum<T>> String?.toEnumOr(default: T): T =
    this?.let { name -> runCatching { enumValueOf<T>(name) }.getOrNull() } ?: default

package org.example.fastfinder.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material.MaterialTheme
import androidx.compose.material.darkColors
import androidx.compose.material.lightColors
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.example.fastfinder.index.DBManager
import org.example.fastfinder.model.SearchFilter
import org.example.fastfinder.model.SearchMode
import org.example.fastfinder.model.SizeFilter
import org.example.fastfinder.model.SortBy
import org.example.fastfinder.model.SystemItem
import org.example.fastfinder.search.Search
import org.example.fastfinder.ui.theme.DarkAppColors
import org.example.fastfinder.ui.theme.LightAppColors
import org.example.fastfinder.ui.theme.LocalAppColors
import org.example.fastfinder.util.AppPreferencesStore
import java.io.File

@Composable
fun FastFinderApp(dbManager: DBManager) {
    val search = remember(dbManager) { Search(dbManager) }
    DisposableEffect(search) { onDispose { search.close() } }

    val coroutineScope = rememberCoroutineScope()
    val isIndexing by dbManager.isIndexing.collectAsState()
    val lastError by dbManager.lastError.collectAsState()
    val indexedCount by dbManager.indexedCount.collectAsState()

    val initialPreferences = remember { AppPreferencesStore.load() }
    var searchQuery by remember { mutableStateOf("") }
    var searchMode by remember { mutableStateOf(initialPreferences.searchMode) }
    var resultFilter by remember { mutableStateOf(initialPreferences.resultFilter) }
    var sizeFilter by remember { mutableStateOf(initialPreferences.sizeFilter) }
    var sortBy by remember { mutableStateOf(initialPreferences.sortBy) }
    var sortAscending by remember { mutableStateOf(initialPreferences.sortAscending) }
    var results by remember { mutableStateOf(emptyList<SystemItem>()) }

    var showCustomSearchDialog by remember { mutableStateOf(false) }
    var customSearchDirectory by remember { mutableStateOf<File?>(null) }
    var isDarkTheme by remember { mutableStateOf(initialPreferences.darkTheme) }

    // Persists theme/filter/sort choices across restarts. Reads the current file before
    // writing so this doesn't clobber the window size Main.kt saves independently into the
    // same preferences file.
    LaunchedEffect(isDarkTheme, searchMode, resultFilter, sizeFilter, sortBy, sortAscending) {
        withContext(Dispatchers.IO) {
            AppPreferencesStore.save(
                AppPreferencesStore.load().copy(
                    darkTheme = isDarkTheme,
                    searchMode = searchMode,
                    resultFilter = resultFilter,
                    sizeFilter = sizeFilter,
                    sortBy = sortBy,
                    sortAscending = sortAscending,
                )
            )
        }
    }

    // Tracks whichever search is currently in flight so a newer search always cancels an
    // older one - otherwise a slow custom-directory search (runSearch) can finish after a
    // faster debounced live search and clobber `results` with stale data, or vice versa.
    var searchJob by remember { mutableStateOf<Job?>(null) }

    fun launchSearch(query: String, directory: File? = null, debounce: Boolean) {
        searchJob?.cancel()
        searchJob = coroutineScope.launch {
            if (debounce) delay(250)
            val searchResults = withContext(Dispatchers.IO) {
                search.search(query, directory, searchMode, resultFilter, sizeFilter)
            }
            results = searchResults
        }
    }

    fun runSearch(directory: File? = null) {
        if (dbManager.isFirstIndexCreation) {
            showIndexNotReadyMessage()
            return
        }
        launchSearch(searchQuery, directory, debounce = false)
    }

    // Live search: re-runs (debounced) whenever the query text or an index-query filter
    // changes. Deliberately doesn't check isFirstIndexCreation/show a dialog here (unlike
    // runSearch): Search.search() already no-ops safely while the index isn't ready, and
    // popping a modal on every keystroke would be a real bug, not just noise.
    LaunchedEffect(searchQuery, searchMode, resultFilter, sizeFilter) {
        launchSearch(searchQuery, debounce = true)
    }

    val appColors = if (isDarkTheme) DarkAppColors else LightAppColors

    CompositionLocalProvider(LocalAppColors provides appColors) {
        MaterialTheme(colors = if (isDarkTheme) darkColors(primary = appColors.buttonColor) else lightColors(primary = appColors.buttonColor)) {
            Column(modifier = Modifier.fillMaxSize().background(color = appColors.backgroundColor)) {
                lastError?.let { message ->
                    ErrorBanner(message = message, onDismiss = dbManager::clearLastError)
                }

                Spacer(modifier = Modifier.height(8.dp))

                SearchControls(
                    searchQuery = searchQuery,
                    onSearchQueryChange = { searchQuery = it },
                    onSearch = { runSearch() },
                    searchMode = searchMode,
                    onSearchModeChange = { searchMode = it },
                    resultFilter = resultFilter,
                    onResultFilterChange = { resultFilter = it },
                    sizeFilter = sizeFilter,
                    onSizeFilterChange = { sizeFilter = it },
                    sortBy = sortBy,
                    onSortByChange = { sortBy = it },
                    sortAscending = sortAscending,
                    onToggleSortDirection = { sortAscending = !sortAscending },
                )

                ResultsList(
                    items = results,
                    searchMode = searchMode,
                    resultFilter = resultFilter,
                    sizeFilter = sizeFilter,
                    sortBy = sortBy,
                    sortAscending = sortAscending,
                    modifier = Modifier.weight(1f).fillMaxWidth().fillMaxHeight().padding(horizontal = 8.dp)
                )

                StatusBar(
                    isIndexing = isIndexing,
                    indexedCount = indexedCount,
                    isDarkTheme = isDarkTheme,
                    onToggleTheme = { isDarkTheme = !isDarkTheme },
                    onCustomSearch = {
                        val directory = showDirectoryPicker()
                        if (directory != null) {
                            customSearchDirectory = directory
                            showCustomSearchDialog = true
                        }
                    },
                    onUpdateDatabase = { dbManager.createOrUpdateIndex(forceIndexCreation = true) }
                )
            }

            if (showCustomSearchDialog) {
                CustomSearchDialog(
                    query = searchQuery,
                    onQueryChange = { searchQuery = it },
                    onConfirm = {
                        showCustomSearchDialog = false
                        customSearchDirectory?.let { runSearch(it) }
                    },
                    onDismiss = { showCustomSearchDialog = false }
                )
            }
        }
    }
}

package org.example.fastfinder.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material.Icon
import androidx.compose.material.MaterialTheme
import androidx.compose.material.Text
import androidx.compose.material.darkColors
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
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
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
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
import org.example.fastfinder.ui.theme.AppTheme
import org.example.fastfinder.ui.theme.DarkAppColors
import org.example.fastfinder.ui.theme.LightAppColors
import org.example.fastfinder.ui.theme.LocalAppColors
import org.example.fastfinder.util.AppPreferencesStore
import java.io.File

@Composable
fun FastFinderApp(
    dbManager: DBManager,
    isElevated: Boolean,
    isAwaitingElevation: Boolean,
    onEnableFastSync: () -> Unit,
) {
    val search = remember(dbManager) { Search(dbManager) }
    DisposableEffect(search) { onDispose { search.close() } }

    val coroutineScope = rememberCoroutineScope()
    val isIndexing by dbManager.isIndexing.collectAsState()
    val lastError by dbManager.lastError.collectAsState()
    val indexedCount by dbManager.indexedCount.collectAsState()

    // Releases the directory/reader Search keeps open across queries before a rebuild starts -
    // otherwise its still-open (possibly memory-mapped) reader can make Windows refuse to rename
    // the live index directory aside when the rebuild finishes, failing with "Failed to finalize
    // the new index". search.search() lazily reopens a fresh one on the next query.
    LaunchedEffect(isIndexing) {
        if (isIndexing) search.close()
    }

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
    // The dialog's own draft query - kept separate from searchQuery so typing into the dialog
    // doesn't also feed the main search bar underneath it and fire a live search behind the
    // modal. Only copied into searchQuery once the user confirms.
    var customSearchQuery by remember { mutableStateOf("") }
    // The folder a confirmed custom search is currently scoped to - distinct from
    // customSearchDirectory (which is just whatever the picker last chose, valid only while the
    // dialog above is open). This one persists after the dialog closes so live search-as-you-type
    // keeps searching that folder instead of silently falling back to the full index.
    var activeCustomSearchDirectory by remember { mutableStateOf<File?>(null) }
    var isDarkTheme by remember { mutableStateOf(initialPreferences.darkTheme) }

    // Persists theme/filter/sort choices across restarts. Reads the current file before
    // writing so this doesn't clobber the window size Main.kt saves independently into the
    // same preferences file.
    LaunchedEffect(isDarkTheme, searchMode, resultFilter, sizeFilter, sortBy, sortAscending) {
        withContext(Dispatchers.IO) {
            AppPreferencesStore.update {
                it.copy(
                    darkTheme = isDarkTheme,
                    searchMode = searchMode,
                    resultFilter = resultFilter,
                    sizeFilter = sizeFilter,
                    sortBy = sortBy,
                    sortAscending = sortAscending,
                )
            }
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

    // Live search: re-runs (debounced) whenever the query text, an index-query filter, or the
    // active custom-search scope changes. Deliberately doesn't check isFirstIndexCreation/show a
    // dialog here (unlike runSearch): Search.search() already no-ops safely while the index isn't
    // ready, and popping a modal on every keystroke would be a real bug, not just noise.
    LaunchedEffect(searchQuery, searchMode, resultFilter, sizeFilter, activeCustomSearchDirectory) {
        launchSearch(searchQuery, activeCustomSearchDirectory, debounce = true)
    }

    val appColors = if (isDarkTheme) DarkAppColors else LightAppColors

    CompositionLocalProvider(LocalAppColors provides appColors) {
        MaterialTheme(colors = if (isDarkTheme) darkColors(primary = appColors.accent) else lightColors(primary = appColors.accent)) {
            Row(modifier = Modifier.fillMaxSize().background(color = appColors.background)) {
                FilterRail(
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
                    isDarkTheme = isDarkTheme,
                    onToggleTheme = { isDarkTheme = !isDarkTheme },
                    onCustomSearch = {
                        val directory = showDirectoryPicker()
                        if (directory != null) {
                            customSearchDirectory = directory
                            customSearchQuery = searchQuery
                            showCustomSearchDialog = true
                        }
                    },
                    onUpdateDatabase = { dbManager.createOrUpdateIndex(forceIndexCreation = true) },
                    isElevated = isElevated,
                    isAwaitingElevation = isAwaitingElevation,
                    onEnableFastSync = onEnableFastSync,
                )

                Column(modifier = Modifier.weight(1f).fillMaxHeight()) {
                    lastError?.let { message ->
                        ErrorBanner(message = message, onDismiss = dbManager::clearLastError)
                    }

                    SearchBar(
                        searchQuery = searchQuery,
                        onSearchQueryChange = { searchQuery = it },
                        onSearch = { runSearch(activeCustomSearchDirectory) },
                        modifier = Modifier.padding(12.dp),
                    )

                    activeCustomSearchDirectory?.let { directory ->
                        CustomSearchIndicator(directory = directory, onClear = { activeCustomSearchDirectory = null })
                    }

                    ResultsList(
                        items = results,
                        searchMode = searchMode,
                        resultFilter = resultFilter,
                        sizeFilter = sizeFilter,
                        sortBy = sortBy,
                        sortAscending = sortAscending,
                        showIndexingNotice = shouldShowIndexingNotice(
                            isIndexing, searchQuery, activeCustomSearchDirectory,
                        ),
                        modifier = Modifier.weight(1f).fillMaxWidth(),
                    )

                    StatusBar(
                        isIndexing = isIndexing,
                        indexedCount = indexedCount,
                        resultCount = results.size,
                    )
                }
            }

            if (showCustomSearchDialog) {
                customSearchDirectory?.let { directory ->
                    CustomSearchDialog(
                        directory = directory,
                        query = customSearchQuery,
                        onQueryChange = { customSearchQuery = it },
                        onConfirm = {
                            showCustomSearchDialog = false
                            // Setting these two - not calling runSearch directly - is deliberate:
                            // both are keys of the live-search LaunchedEffect below, so changing
                            // them already triggers a search. A direct runSearch call here used to
                            // race that effect restart (which always cancels the in-flight job
                            // first) and lose, silently re-querying after another 250ms for nothing.
                            searchQuery = customSearchQuery
                            activeCustomSearchDirectory = directory
                        },
                        onDismiss = { showCustomSearchDialog = false }
                    )
                }
            }
        }
    }
}

/**
 * Whole-index search silently returns nothing while the index isn't ready (see
 * Search.searchIndex), which otherwise looks identical to "no matches" - true only for a
 * non-blank query against the whole index, since Custom Search walks the filesystem directly
 * and is never affected by indexing state.
 */
private fun shouldShowIndexingNotice(
    isIndexing: Boolean,
    searchQuery: String,
    activeCustomSearchDirectory: File?,
): Boolean = isIndexing && searchQuery.isNotBlank() && activeCustomSearchDirectory == null

/** Shown whenever search is scoped to a folder, so it's never a mystery why results look narrower than expected. */
@Composable
private fun CustomSearchIndicator(directory: File, onClear: () -> Unit) {
    val appColors = LocalAppColors.current
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .background(appColors.accent.copy(alpha = 0.12f))
            .padding(horizontal = 16.dp, vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Icon(AppTheme.folderIcon, contentDescription = null, tint = appColors.accent, modifier = Modifier.size(15.dp))
        Text(
            text = "Searching in",
            color = appColors.accent,
            fontSize = 12.sp,
            fontWeight = FontWeight.SemiBold,
            modifier = Modifier.padding(start = 8.dp, end = 6.dp),
        )
        Text(
            text = directory.absolutePath,
            color = appColors.textPrimary,
            fontSize = 12.sp,
            fontFamily = FontFamily.Monospace,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.weight(1f),
        )
        Icon(
            Icons.Default.Close,
            contentDescription = "Exit custom search",
            tint = appColors.accent,
            modifier = Modifier.size(14.dp).clickable(onClick = onClear),
        )
    }
}

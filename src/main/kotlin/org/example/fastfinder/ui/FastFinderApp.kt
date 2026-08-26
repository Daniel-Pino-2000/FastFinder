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
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.example.fastfinder.index.DBManager
import org.example.fastfinder.model.SearchFilter
import org.example.fastfinder.model.SearchMode
import org.example.fastfinder.model.SystemItem
import org.example.fastfinder.search.Search
import org.example.fastfinder.ui.theme.AppTheme
import java.io.File

@Composable
fun FastFinderApp(dbManager: DBManager) {
    val search = remember(dbManager) { Search(dbManager) }
    DisposableEffect(search) { onDispose { search.close() } }

    val coroutineScope = rememberCoroutineScope()
    val isIndexing by dbManager.isIndexing.collectAsState()
    val lastError by dbManager.lastError.collectAsState()

    var searchQuery by remember { mutableStateOf("") }
    var searchMode by remember { mutableStateOf(SearchMode.ALL) }
    var resultFilter by remember { mutableStateOf(SearchFilter.ALL) }
    var results by remember { mutableStateOf(emptyList<SystemItem>()) }

    var showCustomSearchDialog by remember { mutableStateOf(false) }
    var customSearchDirectory by remember { mutableStateOf<File?>(null) }

    fun runSearch(directory: File? = null) {
        if (dbManager.isFirstIndexCreation) {
            showIndexNotReadyMessage()
            return
        }
        val query = searchQuery
        searchQuery = ""
        coroutineScope.launch {
            results = withContext(Dispatchers.IO) { search.search(query, directory, searchMode, resultFilter) }
        }
    }

    MaterialTheme {
        Column(modifier = Modifier.fillMaxSize().background(color = AppTheme.backgroundColor)) {
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
            )

            ResultsList(
                items = results,
                searchMode = searchMode,
                resultFilter = resultFilter,
                modifier = Modifier.weight(1f).fillMaxWidth().fillMaxHeight().padding(horizontal = 8.dp)
            )

            StatusBar(
                isIndexing = isIndexing,
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

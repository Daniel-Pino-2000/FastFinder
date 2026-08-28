package org.example.fastfinder.ui

import androidx.compose.foundation.VerticalScrollbar
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.rememberScrollbarAdapter
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import org.example.fastfinder.model.SearchFilter
import org.example.fastfinder.model.SearchMode
import org.example.fastfinder.model.SizeFilter
import org.example.fastfinder.model.SortBy
import org.example.fastfinder.model.SystemItem
import org.example.fastfinder.ui.theme.LocalAppColors
import org.example.fastfinder.util.isVisible
import org.example.fastfinder.util.systemItemComparator

@Composable
fun ResultsList(
    items: List<SystemItem>,
    searchMode: SearchMode,
    resultFilter: SearchFilter,
    sizeFilter: SizeFilter,
    sortBy: SortBy,
    sortAscending: Boolean,
    modifier: Modifier = Modifier,
) {
    val visibleItems = remember(items, searchMode, resultFilter, sizeFilter, sortBy, sortAscending) {
        items.filter { it.isVisible(searchMode, resultFilter, sizeFilter) }.sortedWith(systemItemComparator(sortBy, sortAscending))
    }
    val listState = rememberLazyListState()

    // The list is fully re-filtered/re-sorted whenever these inputs change, so the
    // previous scroll position (an index into the old ordering) no longer points at
    // anything meaningful - without this it can land the viewport in the middle of
    // unrelated rows instead of showing the new results from the top.
    LaunchedEffect(items, searchMode, resultFilter, sizeFilter, sortBy, sortAscending) {
        listState.scrollToItem(0)
    }

    Box(modifier = modifier.background(color = LocalAppColors.current.lazyColumnColor, shape = RoundedCornerShape(5.dp))) {
        LazyColumn(
            state = listState,
            contentPadding = PaddingValues(8.dp),
            modifier = Modifier.fillMaxSize()
        ) {
            items(visibleItems, key = { it.itemPath }) { item -> ResultItem(item) }
        }

        VerticalScrollbar(
            adapter = rememberScrollbarAdapter(listState),
            modifier = Modifier
                .align(Alignment.CenterEnd)
                .fillMaxHeight()
                .padding(end = 8.dp, top = 24.dp, bottom = 24.dp)
        )
    }
}

package org.example.fastfinder.ui

import androidx.compose.foundation.VerticalScrollbar
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.rememberScrollbarAdapter
import androidx.compose.material.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import org.example.fastfinder.model.SearchFilter
import org.example.fastfinder.model.SearchMode
import org.example.fastfinder.model.SizeFilter
import org.example.fastfinder.model.SortBy
import org.example.fastfinder.model.SystemItem
import org.example.fastfinder.ui.theme.LocalAppColors
import org.example.fastfinder.util.isVisible
import org.example.fastfinder.util.systemItemComparator

/** Column widths shared between [ColumnHeader] and each [ResultItem] row so they line up. */
internal const val NAME_COLUMN_WEIGHT = 2f
internal const val PATH_COLUMN_WEIGHT = 3f
internal val SIZE_COLUMN_WIDTH = 84.dp
internal val TYPE_COLUMN_WIDTH = 100.dp
internal val ACTIONS_COLUMN_WIDTH = 64.dp
internal val ICON_COLUMN_WIDTH = 28.dp

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

    Column(modifier = modifier) {
        ColumnHeader()
        Box(modifier = Modifier.weight(1f).fillMaxWidth()) {
            LazyColumn(
                state = listState,
                contentPadding = PaddingValues(vertical = 4.dp),
                modifier = Modifier.fillMaxSize()
            ) {
                items(visibleItems, key = { it.itemPath }) { item -> ResultItem(item) }
            }

            VerticalScrollbar(
                adapter = rememberScrollbarAdapter(listState),
                modifier = Modifier.align(Alignment.CenterEnd).fillMaxHeight().padding(end = 2.dp)
            )
        }
    }
}

@Composable
private fun ColumnHeader() {
    val appColors = LocalAppColors.current
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .background(appColors.surfaceAlt)
            .padding(horizontal = 12.dp, vertical = 8.dp),
    ) {
        Box(modifier = Modifier.width(ICON_COLUMN_WIDTH))
        HeaderLabel("Name", Modifier.weight(NAME_COLUMN_WEIGHT))
        HeaderLabel("Path", Modifier.weight(PATH_COLUMN_WEIGHT))
        HeaderLabel("Size", Modifier.width(SIZE_COLUMN_WIDTH))
        HeaderLabel("Type", Modifier.width(TYPE_COLUMN_WIDTH))
        Box(modifier = Modifier.width(ACTIONS_COLUMN_WIDTH))
    }
}

@Composable
private fun HeaderLabel(text: String, modifier: Modifier) {
    Text(
        text = text.uppercase(),
        color = LocalAppColors.current.textTertiary,
        fontSize = 10.sp,
        fontWeight = FontWeight.SemiBold,
        letterSpacing = 0.5.sp,
        modifier = modifier,
    )
}

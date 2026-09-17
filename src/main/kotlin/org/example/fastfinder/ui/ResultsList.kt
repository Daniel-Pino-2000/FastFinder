package org.example.fastfinder.ui

import androidx.compose.foundation.HorizontalScrollbar
import androidx.compose.foundation.VerticalScrollbar
import androidx.compose.foundation.background
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
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
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.rememberScrollbarAdapter
import androidx.compose.material.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
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
internal val MODIFIED_COLUMN_WIDTH = 120.dp
internal val CREATED_COLUMN_WIDTH = 120.dp
internal val ACTIONS_COLUMN_WIDTH = 64.dp
internal val ICON_COLUMN_WIDTH = 28.dp
private val ROW_HORIZONTAL_PADDING = 24.dp // matches ColumnHeader/ResultItem's own `padding(horizontal = 12.dp)`

/** A still-legible floor for Name+Path combined - below this they're compressed past usefulness. */
private val MIN_WEIGHTED_COLUMNS_WIDTH = 260.dp

/**
 * Below this width the table's columns no longer all fit - rather than let the fixed-width ones
 * (Size/Type/Modified/Created/Actions) keep squeezing Name/Path towards unreadable and eventually
 * off the edge of the window with no way back, [ResultsList] switches the whole table into a
 * fixed-width, horizontally-scrollable layout at exactly this width once the viewport drops below
 * it - the same tradeoff Explorer's/JetBrains' own details views make.
 */
internal val MIN_TABLE_CONTENT_WIDTH = ROW_HORIZONTAL_PADDING + ICON_COLUMN_WIDTH + MIN_WEIGHTED_COLUMNS_WIDTH +
    SIZE_COLUMN_WIDTH + TYPE_COLUMN_WIDTH + MODIFIED_COLUMN_WIDTH + CREATED_COLUMN_WIDTH + ACTIONS_COLUMN_WIDTH

@Composable
fun ResultsList(
    items: List<SystemItem>,
    searchMode: SearchMode,
    resultFilter: SearchFilter,
    sizeFilter: SizeFilter,
    sortBy: SortBy,
    sortAscending: Boolean,
    // Only relevant to the empty-state message below: whole-index search silently returns
    // nothing while the index isn't ready (see Search.searchIndex), which otherwise looks
    // identical to "no matches" - these let the empty state tell the two apart. Custom search
    // walks the filesystem directly and isn't affected, so this never applies while it's active.
    showIndexingNotice: Boolean = false,
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
        BoxWithConstraints(modifier = Modifier.weight(1f).fillMaxWidth()) {
            // Below MIN_TABLE_CONTENT_WIDTH, the table stops flexing to fit and scrolls
            // horizontally instead, at a fixed width - see that constant's doc. Header and rows
            // share a single horizontalScroll wrapper (not two separate ones over the same
            // ScrollState): a ScrollState's maxValue is derived from whichever scrollable content
            // under it was measured most recently, so two independently-measured containers
            // (the header's fixed-width Row vs. the LazyColumn, whose own measured content width
            // is 0 with no rows composed) fight over it and the emptier one wins, pinning maxValue
            // at 0 and hiding the scrollbar even though the header genuinely overflows.
            val needsHorizontalScroll = maxWidth < MIN_TABLE_CONTENT_WIDTH
            val horizontalScrollState = rememberScrollState()
            val tableWidthModifier = if (needsHorizontalScroll) Modifier.width(MIN_TABLE_CONTENT_WIDTH) else Modifier.fillMaxWidth()
            val density = LocalDensity.current
            var headerHeightPx by remember { mutableStateOf(0) }

            Column(
                modifier = Modifier.fillMaxSize().let {
                    if (needsHorizontalScroll) it.horizontalScroll(horizontalScrollState) else it
                },
            ) {
                ColumnHeader(modifier = tableWidthModifier.onSizeChanged { headerHeightPx = it.height })

                Box(modifier = Modifier.weight(1f).then(tableWidthModifier)) {
                    if (showIndexingNotice && visibleItems.isEmpty()) {
                        IndexingNotice(modifier = Modifier.align(Alignment.Center))
                    }

                    LazyColumn(
                        state = listState,
                        contentPadding = PaddingValues(vertical = 4.dp),
                        modifier = Modifier.fillMaxSize(),
                    ) {
                        items(visibleItems, key = { it.itemPath }) { item -> ResultItem(item) }
                    }
                }
            }

            // Pinned to the viewport's own right edge, outside the horizontally-scrolling content
            // above - not the table's, which would otherwise carry it out of view sideways along
            // with the columns. Same "frozen scrollbar, scrolling columns" split Explorer's
            // details view uses. Offset below the header by its measured height so it only ever
            // overlaps the row area, never the header itself.
            VerticalScrollbar(
                adapter = rememberScrollbarAdapter(listState),
                modifier = Modifier
                    .align(Alignment.TopEnd)
                    .padding(top = with(density) { headerHeightPx.toDp() })
                    .fillMaxHeight()
                    .padding(end = 2.dp),
            )

            if (needsHorizontalScroll) {
                HorizontalScrollbar(
                    adapter = rememberScrollbarAdapter(horizontalScrollState),
                    modifier = Modifier.align(Alignment.BottomStart).fillMaxWidth(),
                )
            }
        }
    }
}

@Composable
private fun IndexingNotice(modifier: Modifier = Modifier) {
    val appColors = LocalAppColors.current
    Column(modifier = modifier.padding(24.dp), horizontalAlignment = Alignment.CenterHorizontally) {
        Text(
            text = "Index is still being built",
            color = appColors.textPrimary,
            fontSize = 13.sp,
            fontWeight = FontWeight.SemiBold,
        )
        Text(
            text = "Whole-drive search results may be incomplete until indexing finishes.\n" +
                "Use Custom Search to search a specific folder right now.",
            color = appColors.textSecondary,
            fontSize = 12.sp,
            textAlign = TextAlign.Center,
            modifier = Modifier.padding(top = 4.dp),
        )
    }
}

@Composable
private fun ColumnHeader(modifier: Modifier = Modifier) {
    val appColors = LocalAppColors.current
    Row(
        modifier = modifier
            .background(appColors.surfaceAlt)
            .padding(horizontal = 12.dp, vertical = 8.dp),
    ) {
        Box(modifier = Modifier.width(ICON_COLUMN_WIDTH))
        HeaderLabel("Name", Modifier.weight(NAME_COLUMN_WEIGHT))
        HeaderLabel("Path", Modifier.weight(PATH_COLUMN_WEIGHT))
        HeaderLabel("Size", Modifier.width(SIZE_COLUMN_WIDTH))
        HeaderLabel("Type", Modifier.width(TYPE_COLUMN_WIDTH))
        HeaderLabel("Modified", Modifier.width(MODIFIED_COLUMN_WIDTH))
        HeaderLabel("Created", Modifier.width(CREATED_COLUMN_WIDTH))
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

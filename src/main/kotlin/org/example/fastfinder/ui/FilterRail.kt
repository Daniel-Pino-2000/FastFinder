package org.example.fastfinder.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.CircularProgressIndicator
import androidx.compose.material.DropdownMenu
import androidx.compose.material.DropdownMenuItem
import androidx.compose.material.Icon
import androidx.compose.material.Switch
import androidx.compose.material.SwitchDefaults
import androidx.compose.material.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import org.example.fastfinder.model.SearchFilter
import org.example.fastfinder.model.SearchMode
import org.example.fastfinder.model.SizeFilter
import org.example.fastfinder.model.SortBy
import org.example.fastfinder.ui.theme.AppTheme
import org.example.fastfinder.ui.theme.LocalAppColors
import org.example.fastfinder.util.label

private val RAIL_WIDTH = 224.dp

/** Type-filter dropdown order: ALL ("All Files") first, then every other category as declared. */
private val typeFilterOptions: List<SearchFilter> =
    listOf(SearchFilter.ALL) + SearchFilter.entries.filter { it != SearchFilter.ALL }

/**
 * The persistent left rail: every filter, sort, and app-level action always visible - no
 * dropdown-hunting for the controls used on nearly every search, unlike the old top toolbar.
 */
@Composable
fun FilterRail(
    searchMode: SearchMode,
    onSearchModeChange: (SearchMode) -> Unit,
    resultFilter: SearchFilter,
    onResultFilterChange: (SearchFilter) -> Unit,
    sizeFilter: SizeFilter,
    onSizeFilterChange: (SizeFilter) -> Unit,
    sortBy: SortBy,
    onSortByChange: (SortBy) -> Unit,
    sortAscending: Boolean,
    onToggleSortDirection: () -> Unit,
    isDarkTheme: Boolean,
    onToggleTheme: () -> Unit,
    onCustomSearch: () -> Unit,
    onUpdateDatabase: () -> Unit,
    fastSync: FastSyncState,
    modifier: Modifier = Modifier,
) {
    val appColors = LocalAppColors.current
    val filtersEnabled = searchMode == SearchMode.FILES

    // No scrolling here at all - the whole point of the adaptive STRUCTURAL_MIN_HEIGHT in Main.kt
    // is that this rail's natural content always fits without it. Deliberately no weight() used
    // anywhere in this Column, not even a Spacer, to pin the buttons to the bottom edge - direct
    // testing found that ANY weight()ed child in a Column here (a Spacer, a scrollable region, it
    // didn't matter which) intermittently hides the sibling that follows it at certain window
    // heights in this app's Compose Desktop version. That's not limited to this rail either: the
    // exact same weight() shape already existed on the main pane's ResultsList/StatusBar pair
    // (FastFinderApp.kt) before this file was ever touched, and it reproduces there too - a
    // pre-existing measurement bug in this Compose version, not something introduced here. Every
    // section, including the buttons, just flows top to bottom with no stretching; the tradeoff is
    // blank space below the buttons on a tall window instead of them staying pinned to the edge.
    Column(
        modifier = modifier
            .width(RAIL_WIDTH)
            .fillMaxHeight()
            .background(appColors.surfaceAlt)
            .padding(12.dp),
    ) {
        RailSection(title = "Show") {
            ShowSegmentedControl(searchMode, onSearchModeChange)
        }

        RailSection(title = "Type") {
            RailDropdown(
                label = resultFilter.label,
                enabled = filtersEnabled,
                content = { close ->
                    // ALL first, matching every other "no filter" convention in the app (e.g. Search Mode's "All").
                    typeFilterOptions.forEach { filter ->
                        DropdownMenuItem(onClick = { onResultFilterChange(filter); close() }) { Text(filter.label) }
                    }
                }
            )
        }

        RailSection(title = "Size") {
            RailDropdown(
                label = sizeFilter.label,
                enabled = filtersEnabled,
                content = { close ->
                    SizeFilter.entries.forEach { filter ->
                        DropdownMenuItem(onClick = { onSizeFilterChange(filter); close() }) { Text(filter.label) }
                    }
                }
            )
        }

        RailSection(title = "Sort") {
            Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                Box(modifier = Modifier.weight(1f)) {
                    RailDropdown(
                        label = sortBy.label,
                        enabled = true,
                        content = { close ->
                            SortBy.entries.forEach { option ->
                                DropdownMenuItem(onClick = { onSortByChange(option); close() }) {
                                    Text(option.label)
                                }
                            }
                        }
                    )
                }
                RailIconButton(
                    icon = if (sortAscending) AppTheme.sortDirectionUpIcon else AppTheme.sortDirectionDownIcon,
                    contentDescription = if (sortAscending) "Sorted ascending" else "Sorted descending",
                    onClick = onToggleSortDirection,
                )
            }
        }

        FastSyncRow(fastSync)

        // Absorbs whatever vertical space the sections above don't use, so the buttons below
        // stay pinned to the bottom edge - safe here (see the comment above) since this Spacer
        // has no content of its own to measure, unlike the verticalScroll() that triggered the bug.
        Spacer(modifier = Modifier.height(4.dp))

        RailActionButton(
            icon = AppTheme.customSearchIcon,
            label = "Custom Search",
            onClick = onCustomSearch,
            filled = false,
        )
        Spacer(modifier = Modifier.height(6.dp))
        RailActionButton(
            icon = AppTheme.refreshIcon,
            label = "Update Database",
            onClick = onUpdateDatabase,
            filled = true,
        )
        Spacer(modifier = Modifier.height(6.dp))
        RailActionButton(
            icon = if (isDarkTheme) AppTheme.lightModeIcon else AppTheme.darkModeIcon,
            label = if (isDarkTheme) "Light Mode" else "Dark Mode",
            onClick = onToggleTheme,
            filled = false,
        )
    }
}

@Composable
private fun RailSection(title: String, content: @Composable () -> Unit) {
    val appColors = LocalAppColors.current
    Column(modifier = Modifier.padding(bottom = 14.dp)) {
        Text(
            text = title.uppercase(),
            color = appColors.textTertiary,
            fontSize = 10.sp,
            fontWeight = FontWeight.SemiBold,
            letterSpacing = 0.6.sp,
            modifier = Modifier.padding(start = 2.dp, bottom = 6.dp),
        )
        content()
    }
}

@Composable
private fun ShowSegmentedControl(selected: SearchMode, onSelect: (SearchMode) -> Unit) {
    val appColors = LocalAppColors.current
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .background(appColors.surface, RoundedCornerShape(6.dp))
            .padding(2.dp),
    ) {
        SearchMode.entries.forEach { mode ->
            val isSelected = mode == selected
            Box(
                modifier = Modifier
                    .weight(1f)
                    .clip(RoundedCornerShape(5.dp))
                    .background(if (isSelected) appColors.accent else Color.Transparent)
                    .clickable { onSelect(mode) }
                    .padding(vertical = 6.dp),
                contentAlignment = Alignment.Center,
            ) {
                Text(
                    text = mode.label,
                    color = if (isSelected) appColors.onAccent else appColors.textSecondary,
                    fontSize = 11.5.sp,
                    fontWeight = if (isSelected) FontWeight.SemiBold else FontWeight.Normal,
                )
            }
        }
    }
}

@Composable
private fun RailDropdown(label: String, enabled: Boolean, content: @Composable (close: () -> Unit) -> Unit) {
    val appColors = LocalAppColors.current
    var expanded by remember { mutableStateOf(false) }

    Box {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .background(appColors.surface, RoundedCornerShape(6.dp))
                .then(if (enabled) Modifier.clickable { expanded = true } else Modifier)
                .padding(horizontal = 10.dp, vertical = 8.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(
                text = label,
                color = if (enabled) appColors.textPrimary else appColors.textTertiary,
                fontSize = 12.5.sp,
                modifier = Modifier.weight(1f),
            )
            Icon(
                AppTheme.chevronIcon,
                contentDescription = null,
                tint = appColors.textTertiary,
                modifier = Modifier.height(16.dp),
            )
        }

        DropdownMenu(expanded = expanded, onDismissRequest = { expanded = false }) {
            content { expanded = false }
        }
    }
}

@Composable
private fun RailIconButton(icon: ImageVector, contentDescription: String, onClick: () -> Unit) {
    val appColors = LocalAppColors.current
    Box(
        modifier = Modifier
            .height(34.dp)
            .width(34.dp)
            .background(appColors.surface, RoundedCornerShape(6.dp))
            .clickable(onClick = onClick),
        contentAlignment = Alignment.Center,
    ) {
        Icon(
            icon,
            contentDescription = contentDescription,
            tint = appColors.textSecondary,
            modifier = Modifier.height(16.dp),
        )
    }
}

@Composable
private fun RailActionButton(icon: ImageVector, label: String, onClick: () -> Unit, filled: Boolean) {
    val appColors = LocalAppColors.current
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .background(
                if (filled) appColors.accent else appColors.surface,
                RoundedCornerShape(6.dp),
            )
            .clickable(onClick = onClick)
            .padding(horizontal = 10.dp, vertical = 9.dp),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Icon(
            icon,
            contentDescription = null,
            tint = if (filled) appColors.onAccent else appColors.textSecondary,
            modifier = Modifier.height(15.dp),
        )
        Text(
            text = label,
            color = if (filled) appColors.onAccent else appColors.textPrimary,
            fontSize = 12.5.sp,
            fontWeight = FontWeight.SemiBold,
        )
    }
}

/**
 * The opt-in toggle for auto-elevating on launch (see [org.example.fastfinder.Elevation]'s file
 * doc) - off by default, so a new user's first launch never triggers an unexplained admin prompt.
 * The two directions are deliberately asymmetric, because Windows makes them genuinely different
 * operations: turning it on (while not already elevated) restarts the whole app elevated, so this
 * confirms that first (see [confirmEnableFastSync]) before triggering the one and only UAC prompt
 * the user will ever see, in direct response to something they clicked. Turning it off never
 * restarts anything - a running process can't give back privileges it already has, so this only
 * ever changes whether the *next* launch auto-elevates; [FastSyncState.isElevated] (this
 * session's actual, unchangeable state) is reflected separately, in the caption below, rather
 * than by fighting the switch back on. A plain [Switch] rather than [RailActionButton] here since
 * this genuinely is an on/off setting, not a one-shot action like the buttons below it.
 */
@Composable
private fun FastSyncRow(fastSync: FastSyncState) {
    val appColors = LocalAppColors.current
    RailSection(title = "Sync") {
        Row(modifier = Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
            Text(
                text = "Fast Update Tracking",
                color = appColors.textPrimary,
                fontSize = 12.5.sp,
                modifier = Modifier.weight(1f),
            )
            if (fastSync.isAwaitingElevation) {
                CircularProgressIndicator(
                    modifier = Modifier.size(16.dp),
                    strokeWidth = 1.5.dp,
                    color = appColors.accent,
                )
            } else {
                Switch(
                    checked = fastSync.fastSyncEnabled,
                    onCheckedChange = { turningOn ->
                        when {
                            !turningOn -> fastSync.onDisable()
                            fastSync.isElevated || confirmEnableFastSync() -> fastSync.onEnable()
                        }
                    },
                    colors = SwitchDefaults.colors(
                        checkedThumbColor = appColors.accent,
                        checkedTrackColor = appColors.accent,
                    ),
                )
            }
        }
        Text(
            text = when {
                fastSync.isAwaitingElevation -> "Waiting for the Administrator prompt…"
                fastSync.isElevated && !fastSync.fastSyncEnabled -> "Off next launch - this " +
                    "session keeps its current Administrator access until you restart FastFinder."
                fastSync.fastSyncEnabled -> "On - catching up on changes instantly instead of a full rescan."
                else -> "Requires Administrator access. Instantly catches up on changes made " +
                    "while FastFinder was closed, instead of a full rescan."
            },
            color = appColors.textTertiary,
            fontSize = 10.5.sp,
            lineHeight = 14.sp,
            modifier = Modifier.padding(top = 6.dp, start = 2.dp, end = 2.dp),
        )
    }
}

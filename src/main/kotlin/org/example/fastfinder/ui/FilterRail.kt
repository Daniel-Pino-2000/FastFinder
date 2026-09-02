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
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.DropdownMenu
import androidx.compose.material.DropdownMenuItem
import androidx.compose.material.Icon
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
    isElevated: Boolean,
    isAwaitingElevation: Boolean,
    onEnableFastSync: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val appColors = LocalAppColors.current
    val filtersEnabled = searchMode == SearchMode.FILES

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
                                DropdownMenuItem(onClick = { onSortByChange(option); close() }) { Text(option.label) }
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

        Spacer(modifier = Modifier.weight(1f))

        FastSyncRow(isElevated, isAwaitingElevation, onEnableFastSync)
        Spacer(modifier = Modifier.height(10.dp))

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
private fun RailActionButton(
    icon: ImageVector,
    label: String,
    onClick: () -> Unit,
    filled: Boolean,
    enabled: Boolean = true,
) {
    val appColors = LocalAppColors.current
    val contentColor = when {
        !enabled -> appColors.textTertiary
        filled -> appColors.onAccent
        else -> appColors.textPrimary
    }
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .background(
                if (filled && enabled) appColors.accent else appColors.surface,
                RoundedCornerShape(6.dp),
            )
            .then(if (enabled) Modifier.clickable(onClick = onClick) else Modifier)
            .padding(horizontal = 10.dp, vertical = 9.dp),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Icon(icon, contentDescription = null, tint = contentColor, modifier = Modifier.height(15.dp))
        Text(text = label, color = contentColor, fontSize = 12.5.sp, fontWeight = FontWeight.SemiBold)
    }
}

/**
 * The opt-in toggle for auto-elevating on launch (see [org.example.fastfinder.Elevation]'s file
 * doc) - off by default, so a new user's first launch never triggers an unexplained admin prompt;
 * enabling it here is what triggers the one and only UAC prompt they'll ever see, in direct
 * response to something they clicked, rather than automatically at startup.
 */
@Composable
private fun FastSyncRow(isElevated: Boolean, isAwaitingElevation: Boolean, onEnableFastSync: () -> Unit) {
    val appColors = LocalAppColors.current
    RailSection(title = "Sync") {
        RailActionButton(
            icon = AppTheme.adminSyncIcon,
            label = when {
                isElevated -> "Fast Update Tracking: On"
                isAwaitingElevation -> "Waiting for Admin Approval…"
                else -> "Enable Fast Update Tracking"
            },
            onClick = onEnableFastSync,
            filled = false,
            enabled = !isElevated && !isAwaitingElevation,
        )
        if (!isElevated) {
            Text(
                text = "Requires Administrator access. Instantly catches up on changes made " +
                    "while FastFinder was closed, instead of a full rescan.",
                color = appColors.textTertiary,
                fontSize = 10.5.sp,
                lineHeight = 14.sp,
                modifier = Modifier.padding(top = 6.dp, start = 2.dp, end = 2.dp),
            )
        }
    }
}

package org.example.fastfinder.ui

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.Button
import androidx.compose.material.ButtonDefaults
import androidx.compose.material.DropdownMenu
import androidx.compose.material.DropdownMenuItem
import androidx.compose.material.Icon
import androidx.compose.material.OutlinedTextField
import androidx.compose.material.Text
import androidx.compose.material.TextFieldDefaults
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowDropDown
import androidx.compose.material.icons.filled.ArrowUpward
import androidx.compose.material.icons.filled.ArrowDownward
import androidx.compose.material.icons.filled.Search
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.key.*
import androidx.compose.ui.unit.dp
import org.example.fastfinder.model.SearchFilter
import org.example.fastfinder.model.SearchMode
import org.example.fastfinder.model.SizeFilter
import org.example.fastfinder.model.SortBy
import org.example.fastfinder.ui.theme.LocalAppColors

@Composable
fun SearchControls(
    searchQuery: String,
    onSearchQueryChange: (String) -> Unit,
    onSearch: () -> Unit,
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
) {
    Row(modifier = Modifier.fillMaxWidth().padding(bottom = 8.dp)) {
        Spacer(modifier = Modifier.width(8.dp))

        OutlinedTextField(
            value = searchQuery,
            onValueChange = onSearchQueryChange,
            colors = TextFieldDefaults.outlinedTextFieldColors(focusedBorderColor = LocalAppColors.current.buttonColor),
            singleLine = true,
            modifier = Modifier
                .weight(1f)
                .onKeyEvent { event ->
                    if (event.type == KeyEventType.KeyUp && event.key == Key.Enter) {
                        onSearch()
                        true
                    } else {
                        false
                    }
                }
        )

        Box {
            Button(
                onClick = onSearch,
                colors = ButtonDefaults.buttonColors(backgroundColor = LocalAppColors.current.buttonColor, contentColor = Color.White),
                shape = RoundedCornerShape(topStart = 0.dp, topEnd = 20.dp, bottomStart = 0.dp, bottomEnd = 20.dp),
                modifier = Modifier.height(57.dp).offset(x = (-2).dp)
            ) {
                Icon(Icons.Default.Search, contentDescription = "Search")
            }
        }

        Spacer(modifier = Modifier.width(16.dp))

        SearchModeDropdown(selected = searchMode, onSelect = onSearchModeChange)

        ResultFilterDropdown(
            selected = resultFilter,
            onSelect = onResultFilterChange,
            enabled = searchMode == SearchMode.FILES
        )

        SizeFilterDropdown(
            selected = sizeFilter,
            onSelect = onSizeFilterChange,
            enabled = searchMode == SearchMode.FILES
        )

        Spacer(modifier = Modifier.width(16.dp))

        SortByDropdown(selected = sortBy, onSelect = onSortByChange)

        Button(
            onClick = onToggleSortDirection,
            shape = RoundedCornerShape(5.dp),
            colors = ButtonDefaults.buttonColors(backgroundColor = LocalAppColors.current.buttonColor, contentColor = Color.White),
            modifier = Modifier.height(56.dp)
        ) {
            Icon(
                if (sortAscending) Icons.Default.ArrowUpward else Icons.Default.ArrowDownward,
                contentDescription = if (sortAscending) "Sorted ascending" else "Sorted descending"
            )
        }
    }
}

private val SearchMode.label: String
    get() = when (this) {
        SearchMode.FILES -> "Files"
        SearchMode.DIRECTORIES -> "Folders"
        SearchMode.ALL -> "All"
    }

private val SearchFilter.label: String
    get() = when (this) {
        SearchFilter.IMAGE -> "Images"
        SearchFilter.DOCUMENT -> "Documents"
        SearchFilter.VIDEO -> "Videos"
        SearchFilter.AUDIO -> "Audios"
        SearchFilter.EXECUTABLE -> "Executables"
        SearchFilter.ARCHIVE -> "Archives"
        SearchFilter.CODE -> "Code"
        SearchFilter.OTHER -> "Other"
        SearchFilter.ALL -> "All Files"
    }

private val SortBy.label: String
    get() = when (this) {
        SortBy.NAME -> "Name"
        SortBy.SIZE -> "Size"
        SortBy.TYPE -> "Type"
    }

private val SizeFilter.label: String
    get() = when (this) {
        SizeFilter.ANY -> "Any Size"
        SizeFilter.UNDER_10KB -> "< 10 KB"
        SizeFilter.KB10_TO_MB1 -> "10 KB - 1 MB"
        SizeFilter.MB1_TO_MB100 -> "1 MB - 100 MB"
        SizeFilter.MB100_TO_GB1 -> "100 MB - 1 GB"
        SizeFilter.OVER_1GB -> "> 1 GB"
    }

@Composable
private fun SearchModeDropdown(selected: SearchMode, onSelect: (SearchMode) -> Unit) {
    var expanded by remember { mutableStateOf(false) }

    Box {
        Button(
            onClick = { expanded = true },
            shape = RoundedCornerShape(5.dp),
            colors = ButtonDefaults.buttonColors(backgroundColor = LocalAppColors.current.buttonColor, contentColor = Color.White),
            modifier = Modifier.height(56.dp).padding(end = 8.dp)
        ) {
            Text(text = selected.label)
            Icon(Icons.Default.ArrowDropDown, contentDescription = null)
        }

        DropdownMenu(expanded = expanded, onDismissRequest = { expanded = false }) {
            SearchMode.entries.forEach { mode ->
                DropdownMenuItem(onClick = { onSelect(mode); expanded = false }) {
                    Text(mode.label)
                }
            }
        }
    }
}

@Composable
private fun SortByDropdown(selected: SortBy, onSelect: (SortBy) -> Unit) {
    var expanded by remember { mutableStateOf(false) }

    Box {
        Button(
            onClick = { expanded = true },
            shape = RoundedCornerShape(5.dp),
            colors = ButtonDefaults.buttonColors(backgroundColor = LocalAppColors.current.buttonColor, contentColor = Color.White),
            modifier = Modifier.height(56.dp).padding(end = 8.dp)
        ) {
            Text(text = "Sort: ${selected.label}")
            Icon(Icons.Default.ArrowDropDown, contentDescription = null)
        }

        DropdownMenu(expanded = expanded, onDismissRequest = { expanded = false }) {
            SortBy.entries.forEach { option ->
                DropdownMenuItem(onClick = { onSelect(option); expanded = false }) {
                    Text(option.label)
                }
            }
        }
    }
}

@Composable
private fun ResultFilterDropdown(selected: SearchFilter, onSelect: (SearchFilter) -> Unit, enabled: Boolean) {
    var expanded by remember { mutableStateOf(false) }
    val selectableFilters = remember { SearchFilter.entries.filter { it != SearchFilter.ALL } }

    Box {
        Button(
            onClick = { expanded = true },
            shape = RoundedCornerShape(5.dp),
            colors = ButtonDefaults.buttonColors(backgroundColor = LocalAppColors.current.buttonColor, contentColor = Color.White),
            modifier = Modifier.height(56.dp).padding(end = 12.dp),
            enabled = enabled
        ) {
            Text(text = selected.label)
            Icon(Icons.Default.ArrowDropDown, contentDescription = null)
        }

        DropdownMenu(expanded = expanded, onDismissRequest = { expanded = false }) {
            selectableFilters.forEach { filter ->
                DropdownMenuItem(onClick = { onSelect(filter); expanded = false }) {
                    Text(filter.label)
                }
            }
        }
    }
}

@Composable
private fun SizeFilterDropdown(selected: SizeFilter, onSelect: (SizeFilter) -> Unit, enabled: Boolean) {
    var expanded by remember { mutableStateOf(false) }

    Box {
        Button(
            onClick = { expanded = true },
            shape = RoundedCornerShape(5.dp),
            colors = ButtonDefaults.buttonColors(backgroundColor = LocalAppColors.current.buttonColor, contentColor = Color.White),
            modifier = Modifier.height(56.dp).padding(end = 8.dp),
            enabled = enabled
        ) {
            Text(text = selected.label)
            Icon(Icons.Default.ArrowDropDown, contentDescription = null)
        }

        DropdownMenu(expanded = expanded, onDismissRequest = { expanded = false }) {
            SizeFilter.entries.forEach { filter ->
                DropdownMenuItem(onClick = { onSelect(filter); expanded = false }) {
                    Text(filter.label)
                }
            }
        }
    }
}

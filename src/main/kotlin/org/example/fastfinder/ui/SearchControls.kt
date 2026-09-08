package org.example.fastfinder.ui

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.Icon
import androidx.compose.material.OutlinedTextField
import androidx.compose.material.TextFieldDefaults
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Search
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.input.key.*
import androidx.compose.ui.unit.dp
import org.example.fastfinder.ui.theme.LocalAppColors

/** The search field plus its explicit-search button - the rest of the toolbar lives in [FilterRail]. */
@Composable
fun SearchBar(
    searchQuery: String,
    onSearchQueryChange: (String) -> Unit,
    onSearch: () -> Unit,
    modifier: Modifier = Modifier,
    focusRequester: FocusRequester? = null,
) {
    val appColors = LocalAppColors.current

    Row(modifier = modifier.fillMaxWidth().padding(bottom = 8.dp)) {
        OutlinedTextField(
            value = searchQuery,
            onValueChange = onSearchQueryChange,
            colors = TextFieldDefaults.outlinedTextFieldColors(
                focusedBorderColor = appColors.accent,
                textColor = appColors.textPrimary,
            ),
            shape = RoundedCornerShape(8.dp),
            singleLine = true,
            leadingIcon = { Icon(Icons.Default.Search, contentDescription = null, tint = appColors.textTertiary) },
            trailingIcon = { ClearSearchButton(searchQuery, onSearchQueryChange) },
            modifier = Modifier
                .weight(1f)
                .let { if (focusRequester != null) it.focusRequester(focusRequester) else it }
                .onKeyEvent { event ->
                    if (event.type == KeyEventType.KeyUp && event.key == Key.Enter) {
                        onSearch()
                        true
                    } else {
                        false
                    }
                }
        )
    }
}

/** A small "X" that clears the search query in one click - only shown once there's something to clear. */
@Composable
private fun ClearSearchButton(searchQuery: String, onSearchQueryChange: (String) -> Unit) {
    if (searchQuery.isEmpty()) return
    Icon(
        Icons.Default.Close,
        contentDescription = "Clear search",
        tint = LocalAppColors.current.textTertiary,
        modifier = Modifier.clickable { onSearchQueryChange("") }
    )
}

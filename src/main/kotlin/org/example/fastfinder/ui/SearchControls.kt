package org.example.fastfinder.ui

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.defaultMinSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.Checkbox
import androidx.compose.material.CheckboxDefaults
import androidx.compose.material.Icon
import androidx.compose.material.OutlinedTextField
import androidx.compose.material.Text
import androidx.compose.material.TextFieldDefaults
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Search
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.input.key.*
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import org.example.fastfinder.ui.theme.LocalAppColors

/** The search field plus its exact-match toggle - the rest of the toolbar lives in [FilterRail]. */
@Composable
fun SearchBar(
    searchQuery: String,
    onSearchQueryChange: (String) -> Unit,
    onSearch: () -> Unit,
    exactMatch: Boolean,
    onExactMatchChange: (Boolean) -> Unit,
    modifier: Modifier = Modifier,
    focusRequester: FocusRequester? = null,
) {
    val appColors = LocalAppColors.current

    Row(
        modifier = modifier.fillMaxWidth().padding(bottom = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        // weight(1f, fill = false), not the usual fill = true (equivalent to fillMaxWidth() on a
        // weighted slot): asking this specific OutlinedTextField to fill an exact/maximum-width
        // constraint - via weight's default fill = true, or via a wrapping Box + fillMaxWidth(),
        // tried first - made it misbehave badly enough that the toggle beside it stopped
        // receiving any layout space at all, vanishing entirely rather than just rendering oddly.
        // fill = false only gives it an upper bound (so it still grows on a wider window) without
        // forcing it to that exact width, which sidesteps whatever this measurement quirk is.
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
                .weight(1f, fill = false)
                .defaultMinSize(minWidth = 320.dp)
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
        ExactMatchToggle(
            exactMatch = exactMatch,
            onExactMatchChange = onExactMatchChange,
            modifier = Modifier.padding(start = 10.dp),
        )
    }
}

/**
 * A bordered toggle chip echoing the search field's own corner radius, so it reads as part of
 * the same control rather than a stray checkbox - checked state fills with a tinted
 * background/border (the same "selected" treatment [ShowSegmentedControl] uses), so the state is
 * legible even at a glance, not just from the checkbox glyph itself.
 */
@Composable
private fun ExactMatchToggle(
    exactMatch: Boolean,
    onExactMatchChange: (Boolean) -> Unit,
    modifier: Modifier = Modifier,
) {
    val appColors = LocalAppColors.current
    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = modifier
            .clip(RoundedCornerShape(8.dp))
            .background(if (exactMatch) appColors.accent.copy(alpha = 0.12f) else appColors.surface)
            .border(
                BorderStroke(1.dp, if (exactMatch) appColors.accent else appColors.border),
                RoundedCornerShape(8.dp),
            )
            .clickable { onExactMatchChange(!exactMatch) }
            .padding(horizontal = 12.dp),
    ) {
        Checkbox(
            checked = exactMatch,
            // null, not onExactMatchChange: the enclosing Row is already clickable (so clicking
            // the label toggles it too, not just the small checkbox glyph) - a Checkbox with its
            // own onCheckedChange would fire a second, independent toggle on top of that one and
            // cancel it back out on every click.
            onCheckedChange = null,
            colors = CheckboxDefaults.colors(checkedColor = appColors.accent),
        )
        Text(
            text = "Exact match",
            color = if (exactMatch) appColors.textPrimary else appColors.textSecondary,
            fontSize = 12.5.sp,
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

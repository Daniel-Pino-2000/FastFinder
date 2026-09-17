package org.example.fastfinder.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
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
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import org.example.fastfinder.ui.theme.LocalAppColors

/** The search field plus its explicit-search button - the rest of the toolbar lives in [FilterRail]. */
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

    Row(modifier = modifier.fillMaxWidth().padding(bottom = 8.dp), verticalAlignment = Alignment.CenterVertically) {
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
        ExactMatchToggle(exactMatch, onExactMatchChange)
    }
}

/**
 * "Match exactly as typed" - the name/path must equal the query, not merely contain it (the
 * default). Mirrors the same opt-in exact/whole-word toggle every comparable search tool
 * (Everything, Listary) offers alongside its default substring search.
 *
 * Styled as a bordered chip rather than a bare checkbox+label, matching the tinted "selected"
 * treatment [ShowSegmentedControl][org.example.fastfinder.ui.FilterRail] already uses elsewhere
 * in the app - checked state gets an accent-tinted background/border so it reads as "on" at a
 * glance, not just via the small checkbox glyph. Deliberately *not* wrapped in a weighted Box or
 * given its own `weight()` (see the search field's `Modifier.weight(1f)` above): that combination
 * previously made Compose's OutlinedTextField misbehave badly enough that this chip stopped
 * receiving any layout space at all and simply never rendered.
 */
@Composable
private fun ExactMatchToggle(exactMatch: Boolean, onExactMatchChange: (Boolean) -> Unit) {
    val appColors = LocalAppColors.current
    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = Modifier
            .padding(start = 8.dp)
            .clip(RoundedCornerShape(8.dp))
            .background(if (exactMatch) appColors.accent.copy(alpha = 0.12f) else appColors.surface)
            .border(
                width = 1.dp,
                color = if (exactMatch) appColors.accent else appColors.border,
                shape = RoundedCornerShape(8.dp),
            )
            .clickable { onExactMatchChange(!exactMatch) }
            .padding(start = 10.dp, end = 12.dp, top = 6.dp, bottom = 6.dp),
    ) {
        Checkbox(
            checked = exactMatch,
            // null, not onExactMatchChange: the enclosing Row is already clickable (so clicking
            // the label toggles it too, not just the small checkbox glyph) - a Checkbox with its
            // own onCheckedChange would fire a second, independent toggle on top of that one and
            // cancel it back out on every click.
            onCheckedChange = null,
            colors = CheckboxDefaults.colors(checkedColor = appColors.accent),
            // Material's Checkbox defaults to a 40dp touch target well past its ~20dp glyph, which
            // left a lopsided gap between the box and "Exact match" - as if the two weren't part of
            // the same control. Shrinking it to a bit past the glyph's own size lines its edge up
            // with the text that follows, the same tight spacing [ShowSegmentedControl] and every
            // other label-plus-control pairing in the rail already uses. Text's line box carries
            // more leading above the glyphs than below (a Skia text-metrics quirk, not fixable by
            // lineHeight alone - see StatusBar.kt), so a box-centered checkbox reads as sitting too
            // high next to it - nudge down slightly to land on the text's visual center.
            modifier = Modifier.size(16.dp).offset(y = 1.dp),
        )
        Spacer(modifier = Modifier.width(6.dp))
        Text(
            text = "Exact match",
            color = if (exactMatch) appColors.accent else appColors.textSecondary,
            fontSize = 12.5.sp,
            fontWeight = if (exactMatch) FontWeight.SemiBold else FontWeight.Normal,
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

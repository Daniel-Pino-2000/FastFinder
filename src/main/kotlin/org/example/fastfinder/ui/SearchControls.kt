package org.example.fastfinder.ui

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.scaleOut
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.Icon
import androidx.compose.material.OutlinedTextField
import androidx.compose.material.Text
import androidx.compose.material.TextFieldDefaults
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Search
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.ExperimentalComposeUiApi
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.key.*
import androidx.compose.ui.input.pointer.PointerEventType
import androidx.compose.ui.input.pointer.onPointerEvent
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
 * glance, not just via the small checkbox glyph.
 */
@OptIn(ExperimentalComposeUiApi::class)
@Composable
private fun ExactMatchToggle(exactMatch: Boolean, onExactMatchChange: (Boolean) -> Unit) {
    val appColors = LocalAppColors.current
    var isHovered by remember { mutableStateOf(false) }

    // Every color here animates instead of snapping, and the check glyph pops in/out with a
    // fade+scale rather than just appearing - small motion that makes toggling read as a single
    // deliberate action instead of two unrelated elements (box, text) changing color at once.
    val transitionSpec = tween<Color>(durationMillis = 120)
    val chipBackground by animateColorAsState(
        targetValue = when {
            exactMatch -> appColors.accent.copy(alpha = 0.12f)
            isHovered -> appColors.hoverColor
            else -> appColors.surface
        },
        animationSpec = transitionSpec,
    )
    val chipBorder by animateColorAsState(if (exactMatch) appColors.accent else appColors.border, transitionSpec)
    val boxFill by animateColorAsState(if (exactMatch) appColors.accent else Color.Transparent, transitionSpec)
    val boxBorder by animateColorAsState(
        targetValue = when {
            exactMatch -> appColors.accent
            isHovered -> appColors.textSecondary
            else -> appColors.textTertiary
        },
        animationSpec = transitionSpec,
    )
    val labelColor by animateColorAsState(if (exactMatch) appColors.accent else appColors.textSecondary, transitionSpec)

    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = Modifier
            .padding(start = 8.dp)
            .clip(RoundedCornerShape(8.dp))
            .background(chipBackground)
            .border(width = 1.dp, color = chipBorder, shape = RoundedCornerShape(8.dp))
            .onPointerEvent(PointerEventType.Enter) { isHovered = true }
            .onPointerEvent(PointerEventType.Exit) { isHovered = false }
            .clickable { onExactMatchChange(!exactMatch) }
            .padding(start = 10.dp, end = 12.dp, top = 6.dp, bottom = 6.dp),
    ) {
        AnimatedCheckbox(checked = exactMatch, fill = boxFill, border = boxBorder, checkTint = appColors.onAccent)
        Spacer(modifier = Modifier.width(6.dp))
        // Fixed weight, not state-dependent: SemiBold is wider than Normal, and since the search
        // field next to this chip is weight(1f)'d against the remaining row space, swapping
        // weights on toggle used to make the search field visibly resize. Checked state is
        // already communicated by the chip's tinted background/border/text color.
        Text(
            text = "Exact match",
            color = labelColor,
            fontSize = 12.5.sp,
            fontWeight = FontWeight.Medium,
        )
    }
}

/**
 * A hand-drawn box instead of Material's Checkbox: that composable hardcodes its glyph to a fixed
 * ~20dp via an internal requiredSize() that overrides whatever size the caller asks for, so it
 * couldn't actually be shrunk this way - and its own internal padding fights any attempt to nudge
 * it into alignment with the text next to it. Text's line box carries more leading above the
 * glyph than below (a Skia text-metrics quirk, not fixable by lineHeight alone - see
 * StatusBar.kt), so this is nudged down slightly to land on the text's true visual center rather
 * than its own geometric center.
 */
@Composable
private fun AnimatedCheckbox(checked: Boolean, fill: Color, border: Color, checkTint: Color) {
    val enterMillis = 100
    val exitMillis = 80
    val checkGlyphInitialScale = 0.5f

    Box(
        modifier = Modifier
            .size(14.dp)
            .offset(y = 3.dp)
            .clip(RoundedCornerShape(3.dp))
            .background(fill)
            .border(width = 1.5.dp, color = border, shape = RoundedCornerShape(3.dp)),
        contentAlignment = Alignment.Center,
    ) {
        AnimatedVisibility(
            visible = checked,
            enter = fadeIn(tween(enterMillis)) + scaleIn(tween(enterMillis), initialScale = checkGlyphInitialScale),
            exit = fadeOut(tween(exitMillis)) + scaleOut(tween(exitMillis), targetScale = checkGlyphInitialScale),
        ) {
            Icon(Icons.Default.Check, contentDescription = null, tint = checkTint, modifier = Modifier.size(10.dp))
        }
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

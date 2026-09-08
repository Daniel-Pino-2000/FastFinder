package org.example.fastfinder.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.Icon
import androidx.compose.material.OutlinedTextField
import androidx.compose.material.Text
import androidx.compose.material.TextFieldDefaults
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Search
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.key
import androidx.compose.ui.input.key.onKeyEvent
import androidx.compose.ui.input.key.type
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import org.example.fastfinder.ui.theme.AppTheme
import org.example.fastfinder.ui.theme.LocalAppColors
import java.io.File

/**
 * Built on the bare [Dialog] primitive rather than Material's [androidx.compose.material.AlertDialog] -
 * the latter brings its own default elevation/typography/button styling that doesn't match the flat,
 * custom-clipped look the rest of the app (rail, search bar) uses, so it stood out as visibly "stock".
 */
@Composable
fun CustomSearchDialog(
    directory: File,
    query: String,
    onQueryChange: (String) -> Unit,
    onConfirm: () -> Unit,
    onDismiss: () -> Unit,
) {
    val appColors = LocalAppColors.current
    val focusRequester = remember { FocusRequester() }

    Dialog(onDismissRequest = onDismiss) {
        Column(
            modifier = Modifier
                .width(420.dp)
                .clip(RoundedCornerShape(12.dp))
                .background(appColors.surface)
                .border(1.dp, appColors.border, RoundedCornerShape(12.dp))
                .padding(20.dp),
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Box(
                    modifier = Modifier
                        .size(32.dp)
                        .clip(RoundedCornerShape(8.dp))
                        .background(appColors.accent.copy(alpha = 0.12f)),
                    contentAlignment = Alignment.Center,
                ) {
                    Icon(
                        AppTheme.folderIcon,
                        contentDescription = null,
                        tint = appColors.accent,
                        modifier = Modifier.size(17.dp),
                    )
                }
                Text(
                    text = "Custom Search",
                    color = appColors.textPrimary,
                    fontSize = 15.sp,
                    fontWeight = FontWeight.SemiBold,
                    modifier = Modifier.padding(start = 10.dp),
                )
            }

            Text(
                text = "SEARCHING IN",
                color = appColors.textTertiary,
                fontSize = 10.sp,
                fontWeight = FontWeight.SemiBold,
                letterSpacing = 0.6.sp,
                modifier = Modifier.padding(top = 18.dp, bottom = 6.dp, start = 2.dp),
            )
            Text(
                text = directory.absolutePath,
                color = appColors.textSecondary,
                fontSize = 12.sp,
                fontFamily = FontFamily.Monospace,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier
                    .fillMaxWidth()
                    .background(appColors.surfaceAlt, RoundedCornerShape(8.dp))
                    .padding(horizontal = 10.dp, vertical = 8.dp),
            )

            OutlinedTextField(
                value = query,
                onValueChange = onQueryChange,
                singleLine = true,
                placeholder = { Text("Search query", fontSize = 13.sp, color = appColors.textTertiary) },
                leadingIcon = {
                    Icon(Icons.Default.Search, contentDescription = null, tint = appColors.textTertiary)
                },
                shape = RoundedCornerShape(8.dp),
                colors = TextFieldDefaults.outlinedTextFieldColors(
                    focusedBorderColor = appColors.accent,
                    unfocusedBorderColor = appColors.border,
                    textColor = appColors.textPrimary,
                    backgroundColor = appColors.surface,
                    cursorColor = appColors.accent,
                ),
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(top = 14.dp)
                    .focusRequester(focusRequester)
                    // Enter confirms, matching the main search bar's Enter-to-search behavior.
                    .onKeyEvent { event ->
                        if (event.type == KeyEventType.KeyUp && event.key == Key.Enter) {
                            onConfirm()
                            true
                        } else {
                            false
                        }
                    },
            )

            Row(
                horizontalArrangement = Arrangement.End,
                modifier = Modifier.fillMaxWidth().padding(top = 20.dp),
            ) {
                DialogButton(text = "Cancel", filled = false, onClick = onDismiss)
                Spacer(modifier = Modifier.width(8.dp))
                DialogButton(text = "Search", filled = true, onClick = onConfirm)
            }
        }
    }

    LaunchedEffect(Unit) { focusRequester.requestFocus() }
}

/** Matches [FilterRail]'s `RailActionButton`/dropdown treatment: flat, clip-then-clickable, no Material elevation. */
@Composable
private fun DialogButton(text: String, filled: Boolean, onClick: () -> Unit) {
    val appColors = LocalAppColors.current
    Box(
        modifier = Modifier
            // clip before clickable: without it, the ripple ignores this shape and visibly
            // overshoots the rounded corners in a square instead of following them.
            .clip(RoundedCornerShape(6.dp))
            .background(if (filled) appColors.accent else appColors.surfaceAlt)
            .clickable(onClick = onClick)
            .padding(horizontal = 16.dp, vertical = 9.dp),
        contentAlignment = Alignment.Center,
    ) {
        Text(
            text = text,
            color = if (filled) appColors.onAccent else appColors.textSecondary,
            fontSize = 12.5.sp,
            fontWeight = FontWeight.SemiBold,
        )
    }
}

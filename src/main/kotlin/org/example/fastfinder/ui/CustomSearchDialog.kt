package org.example.fastfinder.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.AlertDialog
import androidx.compose.material.Button
import androidx.compose.material.ButtonDefaults
import androidx.compose.material.Icon
import androidx.compose.material.OutlinedButton
import androidx.compose.material.OutlinedTextField
import androidx.compose.material.Text
import androidx.compose.material.TextFieldDefaults
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Search
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import org.example.fastfinder.ui.theme.AppTheme
import org.example.fastfinder.ui.theme.LocalAppColors
import java.io.File

@Composable
fun CustomSearchDialog(
    directory: File,
    query: String,
    onQueryChange: (String) -> Unit,
    onConfirm: () -> Unit,
    onDismiss: () -> Unit,
) {
    val appColors = LocalAppColors.current

    AlertDialog(
        onDismissRequest = onDismiss,
        backgroundColor = appColors.surface,
        shape = RoundedCornerShape(12.dp),
        title = {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(AppTheme.folderIcon, contentDescription = null, tint = appColors.accent)
                Text(text = "Custom Search", color = appColors.textPrimary, modifier = Modifier.padding(start = 8.dp))
            }
        },
        text = {
            Column {
                Text(
                    text = "Searching in",
                    color = appColors.textTertiary,
                    fontSize = 11.sp,
                    modifier = Modifier.padding(bottom = 4.dp),
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
                        .background(appColors.surfaceAlt, RoundedCornerShape(6.dp))
                        .padding(horizontal = 10.dp, vertical = 8.dp),
                )

                OutlinedTextField(
                    value = query,
                    onValueChange = onQueryChange,
                    singleLine = true,
                    placeholder = { Text("Search query") },
                    leadingIcon = {
                        Icon(Icons.Default.Search, contentDescription = null, tint = appColors.textTertiary)
                    },
                    colors = TextFieldDefaults.outlinedTextFieldColors(
                        focusedBorderColor = appColors.accent,
                        textColor = appColors.textPrimary,
                    ),
                    modifier = Modifier.fillMaxWidth().padding(top = 14.dp),
                )
            }
        },
        confirmButton = {
            Button(
                onClick = onConfirm,
                colors = ButtonDefaults.buttonColors(
                    backgroundColor = appColors.accent,
                    contentColor = appColors.onAccent,
                ),
                shape = RoundedCornerShape(6.dp),
            ) { Text("Search") }
        },
        dismissButton = {
            OutlinedButton(
                onClick = onDismiss,
                colors = ButtonDefaults.outlinedButtonColors(contentColor = appColors.textSecondary),
                shape = RoundedCornerShape(6.dp),
            ) { Text("Cancel") }
        },
    )
}

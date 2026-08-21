package org.example.fastfinder.ui

import androidx.compose.foundation.layout.Column
import androidx.compose.material.AlertDialog
import androidx.compose.material.Button
import androidx.compose.material.Text
import androidx.compose.material.TextField
import androidx.compose.runtime.Composable

@Composable
fun CustomSearchDialog(
    query: String,
    onQueryChange: (String) -> Unit,
    onConfirm: () -> Unit,
    onDismiss: () -> Unit,
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(text = "Enter Search Query") },
        text = {
            Column {
                TextField(
                    value = query,
                    onValueChange = onQueryChange,
                    singleLine = true,
                    label = { Text("Search Query") }
                )
            }
        },
        confirmButton = { Button(onClick = onConfirm) { Text("OK") } },
        dismissButton = { Button(onClick = onDismiss) { Text("Cancel") } }
    )
}

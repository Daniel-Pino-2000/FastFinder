package org.example.fastfinder.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material.Icon
import androidx.compose.material.Text
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Warning
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import org.example.fastfinder.ui.theme.LocalAppColors

@Composable
fun ErrorBanner(message: String, onDismiss: () -> Unit) {
    val appColors = LocalAppColors.current
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .background(appColors.errorBackground)
            .padding(horizontal = 12.dp, vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Icon(Icons.Default.Warning, contentDescription = null, tint = appColors.errorForeground)
        Text(
            text = message,
            color = appColors.errorForeground,
            modifier = Modifier.padding(horizontal = 8.dp).weight(1f)
        )
        Icon(
            Icons.Default.Close,
            contentDescription = "Dismiss",
            tint = appColors.errorForeground,
            modifier = Modifier.clickable(onClick = onDismiss)
        )
    }
}

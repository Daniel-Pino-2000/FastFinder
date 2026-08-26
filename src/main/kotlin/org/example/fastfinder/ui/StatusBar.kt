package org.example.fastfinder.ui

import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.Button
import androidx.compose.material.ButtonDefaults
import androidx.compose.material.CircularProgressIndicator
import androidx.compose.material.Icon
import androidx.compose.material.MaterialTheme
import androidx.compose.material.Text
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Brightness4
import androidx.compose.material.icons.filled.Brightness7
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import org.example.fastfinder.ui.theme.LocalAppColors

@Composable
fun StatusBar(
    isIndexing: Boolean,
    indexedCount: Int,
    isDarkTheme: Boolean,
    onToggleTheme: () -> Unit,
    onCustomSearch: () -> Unit,
    onUpdateDatabase: () -> Unit,
) {
    val appColors = LocalAppColors.current
    Row(
        modifier = Modifier.fillMaxWidth().padding(8.dp),
        verticalAlignment = Alignment.Bottom,
    ) {
        Row(
            modifier = Modifier.weight(1f).padding(start = 5.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            if (isIndexing) {
                Text(
                    text = "Indexing Database" + if (indexedCount > 0) " - %,d items indexed".format(indexedCount) else "",
                    style = MaterialTheme.typography.body2.copy(
                        fontSize = 14.sp,
                        color = MaterialTheme.colors.onSurface.copy(alpha = 0.8f)
                    )
                )
                Spacer(modifier = Modifier.width(8.dp))
                CircularProgressIndicator(
                    modifier = Modifier.size(20.dp),
                    strokeWidth = 2.dp,
                    color = MaterialTheme.colors.primary
                )
            }
        }

        Button(
            onClick = onToggleTheme,
            shape = RoundedCornerShape(5.dp),
            colors = ButtonDefaults.buttonColors(contentColor = Color.White, backgroundColor = appColors.buttonColor),
            modifier = Modifier.height(56.dp)
        ) {
            Icon(
                if (isDarkTheme) Icons.Default.Brightness7 else Icons.Default.Brightness4,
                contentDescription = if (isDarkTheme) "Switch to light theme" else "Switch to dark theme"
            )
        }

        Spacer(modifier = Modifier.width(12.dp))

        Button(
            onClick = onCustomSearch,
            shape = RoundedCornerShape(5.dp),
            colors = ButtonDefaults.buttonColors(contentColor = Color.White, backgroundColor = appColors.buttonColor),
            modifier = Modifier.height(56.dp)
        ) {
            Text(text = "Custom Search")
        }

        Spacer(modifier = Modifier.width(12.dp))

        Button(
            onClick = onUpdateDatabase,
            shape = RoundedCornerShape(5.dp),
            colors = ButtonDefaults.buttonColors(contentColor = Color.White, backgroundColor = appColors.buttonColor),
            modifier = Modifier.height(56.dp)
        ) {
            Text(text = "Update\nDatabase")
            Icon(Icons.Default.Refresh, contentDescription = null)
        }
    }
}

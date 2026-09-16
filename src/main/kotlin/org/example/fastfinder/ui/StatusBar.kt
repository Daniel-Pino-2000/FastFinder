package org.example.fastfinder.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.material.CircularProgressIndicator
import androidx.compose.material.Icon
import androidx.compose.material.Text
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import org.example.fastfinder.ui.theme.LocalAppColors
import org.example.fastfinder.util.appVersion

/**
 * The main pane's bottom status row - result count on the left, indexing state and the app's own
 * version on the right, in that order (version last, since it's the least actionable/urgent of
 * the three - matches version numbers typically sitting at the very edge of a status bar, e.g.
 * VS Code's, rather than competing for attention nearer the center).
 */
@Composable
fun StatusBar(
    isIndexing: Boolean,
    indexedCount: Int,
    resultCount: Int,
    version: String? = appVersion,
    modifier: Modifier = Modifier,
) {
    val appColors = LocalAppColors.current
    Row(
        modifier = modifier
            .fillMaxWidth()
            .background(appColors.surfaceAlt)
            .padding(horizontal = 16.dp, vertical = 7.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            text = "%,d result%s".format(resultCount, if (resultCount == 1) "" else "s"),
            color = appColors.textSecondary,
            fontSize = 11.5.sp,
            lineHeight = 11.5.sp,
        )

        Spacer(modifier = Modifier.weight(1f))

        if (isIndexing) {
            Text(
                text = "Indexing Database" + if (indexedCount > 0) " - %,d items indexed".format(indexedCount) else "",
                color = appColors.textSecondary,
                fontSize = 11.5.sp,
                lineHeight = 11.5.sp,
            )
            Spacer(modifier = Modifier.width(8.dp))
            CircularProgressIndicator(
                modifier = Modifier.size(13.dp),
                strokeWidth = 1.5.dp,
                color = appColors.accent,
            )
        } else {
            // Text's line box carries more leading above the glyphs than below (a Skia text-metrics
            // quirk, not fixable by lineHeight alone), so a box-centered icon reads as sitting too
            // high next to it - nudge the icon down slightly to land on the text's visual center.
            Icon(
                Icons.Default.CheckCircle,
                contentDescription = null,
                tint = appColors.accent,
                modifier = Modifier.size(12.dp).offset(y = 1.dp),
            )
            Spacer(modifier = Modifier.width(5.dp))
            Text(text = "Index up to date", color = appColors.accent, fontSize = 11.5.sp, lineHeight = 11.5.sp)
        }

        // Muted and last in reading order - identifying info for a bug report, not something
        // that needs to compete with the indexing status for attention on every glance down here.
        version?.let {
            Spacer(modifier = Modifier.width(12.dp))
            Text(text = "v$it", color = appColors.textTertiary, fontSize = 11.sp, lineHeight = 11.sp)
        }
    }
}

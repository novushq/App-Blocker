package appblocker.appblocker.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.unit.dp

data class FGChartEntry(
    val xLabel: String,
    val value: Float
)

@Composable
fun FGBarChart(
    entries: List<FGChartEntry>,
    modifier: Modifier = Modifier,
    highlightedIndex: Int? = null,
    maxVisibleLabels: Int = 4
) {
    if (entries.isEmpty()) {
        Box(modifier = modifier, contentAlignment = Alignment.Center) {
            Text("No data")
        }
        return
    }

    val max = entries.maxOf { it.value }.takeIf { it > 0f } ?: 1f
    val labelStep = (entries.size / maxVisibleLabels).coerceAtLeast(1)

    Column(modifier = modifier) {
        Row(
            modifier = Modifier
                .weight(1f)
                .fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(3.dp),
            verticalAlignment = Alignment.Bottom
        ) {
            entries.forEachIndexed { index, entry ->
                val fraction = (entry.value / max).coerceIn(0f, 1f)
                val isHighlighted = highlightedIndex == index
                Box(
                    modifier = Modifier
                        .weight(1f)
                        .fillMaxHeight(fraction.coerceAtLeast(0.03f))
                        .clip(RoundedCornerShape(topStart = 3.dp, topEnd = 3.dp))
                        .background(
                            if (isHighlighted) MaterialTheme.colorScheme.error.copy(alpha = 0.7f)
                            else MaterialTheme.colorScheme.primary.copy(alpha = 0.65f)
                        )
                )
            }
        }

        Spacer(Modifier.height(8.dp))
        BoxWithConstraints(Modifier.fillMaxWidth()) {
            entries.forEachIndexed { index, entry ->
                if (index % labelStep != 0 && index != entries.lastIndex) return@forEachIndexed
                val x = if (entries.size == 1) 0f else index.toFloat() / (entries.lastIndex).toFloat()
                Text(
                    text = entry.xLabel,
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier
                        .align(Alignment.CenterStart)
                        .padding(start = maxWidth * x)
                )
            }
        }
    }
}


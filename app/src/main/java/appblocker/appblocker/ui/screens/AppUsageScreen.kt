package appblocker.appblocker.ui.screens

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.EaseOutCubic
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ChevronRight
import androidx.compose.material.icons.filled.Clear
import androidx.compose.material.icons.filled.Schedule
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import appblocker.appblocker.data.repository.UsageStatsRepository.AppUsageStat
import appblocker.appblocker.data.repository.UsageStatsRepository.UsageSummary
import appblocker.appblocker.ui.components.AppIconImage
import appblocker.appblocker.ui.components.FGBarChart
import appblocker.appblocker.ui.components.FGChartEntry
import appblocker.appblocker.ui.components.FGInlineProgress
import appblocker.appblocker.ui.components.FGSectionHeader
import appblocker.appblocker.ui.viewmodel.AppUsageViewModel
import appblocker.appblocker.ui.viewmodel.InstalledApp
import appblocker.appblocker.ui.viewmodel.UsageRange

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AppUsageScreen(
    vm: AppUsageViewModel,
    onOpenApp: (String) -> Unit,
    modifier: Modifier = Modifier
) {
    val state by vm.state.collectAsState()
    val searchQuery by vm.searchQuery.collectAsState()
    val searchResults by vm.searchResults.collectAsState()

    val contentAlpha = remember { Animatable(0f) }
    LaunchedEffect(state.isLoading) {
        if (!state.isLoading) {
            contentAlpha.animateTo(1f, animationSpec = tween(400, easing = EaseOutCubic))
        }
    }

    Column(modifier = modifier.fillMaxSize()) {

        // ── Search bar ────────────────────────────────────────────────────────
        OutlinedTextField(
            value         = searchQuery,
            onValueChange = vm::setSearchQuery,
            placeholder = {
                Text(
                    "Search any app…",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.6f)
                )
            },
            leadingIcon = {
                Icon(
                    Icons.Default.Search,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.size(20.dp)
                )
            },
            trailingIcon = {
                if (searchQuery.isNotBlank()) {
                    IconButton(onClick = { vm.setSearchQuery("") }) {
                        Box(
                            Modifier
                                .size(28.dp)
                                .clip(CircleShape)
                                .background(MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.7f)),
                            contentAlignment = Alignment.Center
                        ) {
                            Icon(
                                Icons.Default.Clear,
                                contentDescription = "Clear",
                                modifier = Modifier.size(14.dp),
                                tint = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }
                }
            },
            singleLine = true,
            shape = RoundedCornerShape(16.dp),
            colors = OutlinedTextFieldDefaults.colors(
                unfocusedBorderColor = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.4f),
                focusedBorderColor = MaterialTheme.colorScheme.primary.copy(alpha = 0.6f),
                unfocusedContainerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.3f),
                focusedContainerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.4f)
            ),
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp, vertical = 10.dp)
        )

        // ── Search mode ───────────────────────────────────────────────────────
        if (searchQuery.isNotBlank()) {
            if (searchResults.isEmpty()) {
                Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    Column(
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        Text("🔍", fontSize = 32.sp)
                        Text(
                            text = "No apps matching $searchQuery",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
            } else {
                LazyColumn(
                    contentPadding = PaddingValues(horizontal = 16.dp, vertical = 4.dp),
                    verticalArrangement = Arrangement.spacedBy(4.dp)
                ) {
                    itemsIndexed(searchResults, key = { index, _ -> index }) { _, app ->
                        SearchResultRow(app = app, onClick = { onOpenApp(app.packageName) })
                    }
                }
            }
            return@Column
        }

        // ── Range picker ──────────────────────────────────────────────────────
        if (state.customRangeLabel == null) {
            LazyRow(
                modifier = Modifier.padding(horizontal = 16.dp),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                contentPadding = PaddingValues(horizontal = 2.dp)
            ) {
                items(enumValues<UsageRange>().toList()) { range ->
                    FilterChip(
                        selected = state.selectedRange == range,
                        onClick = { vm.loadUsage(range) },
                        label = {
                            Text(
                                range.label,
                                style = MaterialTheme.typography.labelMedium,
                                fontWeight = if (state.selectedRange == range) FontWeight.SemiBold else FontWeight.Normal
                            )
                        },
                        shape = RoundedCornerShape(10.dp),
                        colors = FilterChipDefaults.filterChipColors(
                            selectedContainerColor = MaterialTheme.colorScheme.primary,
                            selectedLabelColor = MaterialTheme.colorScheme.onPrimary
                        )
                    )
                }
            }
        } else {
            Row(
                modifier = Modifier.padding(horizontal = 16.dp, vertical = 4.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                Text(
                    "${state.customRangeLabel}",
                    style = MaterialTheme.typography.titleSmall,
                    fontWeight = FontWeight.SemiBold
                )
                FilterChip(
                    selected = false,
                    onClick = { vm.loadUsage(UsageRange.TODAY) },
                    label = { Text("Back to today", style = MaterialTheme.typography.labelSmall) },
                    shape = RoundedCornerShape(10.dp)
                )
            }
        }

        // ── Loading ───────────────────────────────────────────────────────────
        if (state.isLoading) {
            Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                Column(
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.spacedBy(14.dp)
                ) {
                    CircularProgressIndicator(
                        modifier = Modifier.size(40.dp),
                        strokeWidth = 2.5.dp,
                        color = MaterialTheme.colorScheme.primary
                    )
                    Text(
                        "Crunching your data…",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
            return@Column
        }

        // ── Error ─────────────────────────────────────────────────────────────
        if (state.error != null) {
            Box(Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp, vertical = 8.dp)) {
                Card(
                    shape = RoundedCornerShape(12.dp),
                    colors = CardDefaults.cardColors(
                        containerColor = MaterialTheme.colorScheme.errorContainer.copy(alpha = 0.4f)
                    )
                ) {
                    Text(
                        state.error ?: "",
                        modifier = Modifier.padding(12.dp),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.error
                    )
                }
            }
        }

        val summary = state.summary ?: return@Column
        val maxMs = summary.appStats.maxOfOrNull { it.totalTimeMs } ?: 1L
        val isMultiDay = summary.dailyBuckets.size > 1
        val chartEntries: List<FGChartEntry> = if (isMultiDay) {
            summary.dailyBuckets.map { FGChartEntry(it.dayLabel, it.totalTimeMs.toFloat()) }
        } else {
            summary.hourlyBuckets.map { b ->
                FGChartEntry(
                    xLabel = when (b.hourOfDay) {
                        6 -> "6a"; 12 -> "12p"; 18 -> "6p"; 23 -> "12a"; else -> ""
                    },
                    value = b.totalTimeMs.toFloat()
                )
            }
        }

        LazyColumn(
            modifier = Modifier.graphicsLayer { alpha = contentAlpha.value },
            contentPadding = PaddingValues(start = 16.dp, end = 16.dp, bottom = 96.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {

            // ── Hero ──────────────────────────────────────────────────────────
            item { Spacer(Modifier.height(4.dp)) }
            item { UsageHeroCard(summary = summary, range = state.selectedRange) }

            // ── Quick stats row ───────────────────────────────────────────────
            item {
                val appCount = summary.appStats.size
                val sessionCount = summary.appStats.sumOf { it.sessions.size }
                val topApp = summary.appStats.firstOrNull()

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    UsageStatTile(
                        label = "Apps used",
                        value = appCount.toString(),
                        modifier = Modifier.weight(1f)
                    )
                    UsageStatTile(
                        label = "Sessions",
                        value = sessionCount.toString(),
                        modifier = Modifier.weight(1f)
                    )
                    UsageStatTile(
                        label = "Top app",
                        value = topApp?.appName?.take(8) ?: "—",
                        modifier = Modifier.weight(1f)
                    )
                }
            }

            // ── Chart ─────────────────────────────────────────────────────────
            item {
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(20.dp),
                    colors = CardDefaults.cardColors(
                        containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.4f)
                    ),
                    elevation = CardDefaults.cardElevation(0.dp)
                ) {
                    Column(
                        Modifier.padding(18.dp),
                        verticalArrangement = Arrangement.spacedBy(14.dp)
                    ) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Column {
                                Text(
                                    if (isMultiDay) "Daily breakdown" else "Hourly breakdown",
                                    style = MaterialTheme.typography.titleSmall,
                                    fontWeight = FontWeight.SemiBold
                                )
                                Text(
                                    state.selectedRange.label,
                                    style = MaterialTheme.typography.labelSmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                            Icon(
                                Icons.Default.Schedule,
                                contentDescription = null,
                                tint = MaterialTheme.colorScheme.primary.copy(alpha = 0.7f),
                                modifier = Modifier.size(20.dp)
                            )
                        }
                        FGBarChart(
                            entries = chartEntries,
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(130.dp),
                            maxVisibleLabels = if (isMultiDay) 7 else 4
                        )
                    }
                }
            }

            // ── Top apps header ───────────────────────────────────────────────
            item {
                FGSectionHeader(
                    title = "Top apps",
                    subtitle = if (summary.appStats.isEmpty()) null else "Tap any app for detailed sessions"
                )
            }

            // ── Empty state ───────────────────────────────────────────────────
            if (summary.appStats.isEmpty()) {
                item {
                    Box(
                        Modifier
                            .fillMaxWidth()
                            .padding(vertical = 32.dp),
                        contentAlignment = Alignment.Center
                    ) {
                        Column(
                            horizontalAlignment = Alignment.CenterHorizontally,
                            verticalArrangement = Arrangement.spacedBy(6.dp)
                        ) {
                            Text("📭", fontSize = 32.sp)
                            Text(
                                "No app usage found for this range",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }
                }
            } else {
                itemsIndexed(summary.appStats, key = { _, it -> it.packageName }) { index, app ->
                    UsageAppRow(
                        app = app,
                        rank = index + 1,
                        maxMs = maxMs,
                        onClick = { onOpenApp(app.packageName) }
                    )
                }
            }
        }
    }
}

// ── Search result row ─────────────────────────────────────────────────────────

@Composable
private fun SearchResultRow(app: InstalledApp, onClick: () -> Unit) {
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .clickable { onClick() },
        shape = RoundedCornerShape(14.dp),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.35f)
        ),
        elevation = CardDefaults.cardElevation(0.dp)
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 14.dp, vertical = 12.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            AppIconImage(
                packageName = app.packageName,
                appName = app.label,
                modifier = Modifier.size(40.dp),
                cornerRadius = 10.dp
            )
            Spacer(Modifier.width(12.dp))
            Text(
                app.label,
                style = MaterialTheme.typography.bodyMedium,
                fontWeight = FontWeight.Medium,
                modifier = Modifier.weight(1f),
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
            Icon(
                Icons.Default.ChevronRight,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.5f),
                modifier = Modifier.size(18.dp)
            )
        }
    }
}

// ── Usage hero card ───────────────────────────────────────────────────────────

@Composable
private fun UsageHeroCard(summary: UsageSummary, range: UsageRange) {
    val h = summary.totalScreenTimeMs / 3_600_000
    val m = (summary.totalScreenTimeMs % 3_600_000) / 60_000

    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(24.dp),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.35f)
        ),
        elevation = CardDefaults.cardElevation(0.dp)
    ) {
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .background(
                    Brush.verticalGradient(
                        listOf(
                            MaterialTheme.colorScheme.primary.copy(alpha = 0.12f),
                            Color.Transparent
                        )
                    )
                )
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(24.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(6.dp)
            ) {
                Text(
                    "Total screen time",
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                // Big time display
                Row(
                    verticalAlignment = Alignment.Bottom,
                    horizontalArrangement = Arrangement.Center
                ) {
                    if (h > 0) {
                        Text(
                            "$h",
                            fontSize = 52.sp,
                            fontWeight = FontWeight.ExtraBold,
                            color = MaterialTheme.colorScheme.onSurface,
                            lineHeight = 56.sp
                        )
                        Text(
                            "h ",
                            fontSize = 20.sp,
                            fontWeight = FontWeight.Medium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.padding(bottom = 8.dp)
                        )
                    }
                    Text(
                        "$m",
                        fontSize = 52.sp,
                        fontWeight = FontWeight.ExtraBold,
                        color = MaterialTheme.colorScheme.onSurface,
                        lineHeight = 56.sp
                    )
                    Text(
                        "m",
                        fontSize = 20.sp,
                        fontWeight = FontWeight.Medium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.padding(bottom = 8.dp)
                    )
                }
                Text(
                    range.label,
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.7f)
                )
            }
        }
    }
}

// ── Quick stat tile ───────────────────────────────────────────────────────────

@Composable
private fun UsageStatTile(label: String, value: String, modifier: Modifier = Modifier) {
    Card(
        modifier = modifier,
        shape = RoundedCornerShape(18.dp),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.45f)
        ),
        elevation = CardDefaults.cardElevation(0.dp)
    ) {
        Column(
            Modifier
                .fillMaxWidth()
                .padding(12.dp),
            verticalArrangement = Arrangement.spacedBy(4.dp),
            horizontalAlignment = Alignment.Start
        ) {
            Text(
                value,
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.onSurface,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
            Text(
                label,
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    }
}

// ── App usage row ─────────────────────────────────────────────────────────────

@Composable
private fun UsageAppRow(app: AppUsageStat, rank: Int, maxMs: Long, onClick: () -> Unit) {
    val h = app.totalTimeMs / 3_600_000
    val m = (app.totalTimeMs % 3_600_000) / 60_000
    val s = (app.totalTimeMs % 60_000) / 1_000
    val timeLabel = when {
        h > 0 -> "${h}h ${m}m"; m > 0 -> "${m}m ${s}s"; else -> "${s}s"
    }
    val fraction = app.totalTimeMs.toFloat() / maxMs.toFloat()

    // Top 3 get a subtle accent tint
    val accentAlpha = when (rank) {
        1 -> 0.10f; 2 -> 0.06f; 3 -> 0.03f; else -> 0f
    }

    val containerColor = if (accentAlpha > 0f)
        MaterialTheme.colorScheme.primary.copy(alpha = accentAlpha)
    else
        MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.35f)

    Card(
        modifier = Modifier
            .fillMaxWidth()
            .clickable { onClick() },
        shape = RoundedCornerShape(18.dp),
        colors = CardDefaults.cardColors(containerColor = containerColor),
        elevation = CardDefaults.cardElevation(0.dp)
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(14.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            // Rank badge
            Box(
                modifier = Modifier
                    .size(22.dp)
                    .clip(CircleShape)
                    .background(
                        if (rank <= 3) MaterialTheme.colorScheme.primary.copy(alpha = 0.15f)
                        else MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.6f)
                    ),
                contentAlignment = Alignment.Center
            ) {
                Text(
                    "$rank",
                    style = MaterialTheme.typography.labelSmall,
                    fontWeight = FontWeight.Bold,
                    color = if (rank <= 3) MaterialTheme.colorScheme.primary
                    else MaterialTheme.colorScheme.onSurfaceVariant
                )
            }

            Spacer(Modifier.width(10.dp))

            AppIconImage(
                packageName = app.packageName,
                appName = app.appName,
                modifier = Modifier.size(42.dp),
                cornerRadius = 11.dp
            )

            Spacer(Modifier.width(12.dp))

            Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(5.dp)) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        app.appName,
                        style = MaterialTheme.typography.bodyMedium,
                        fontWeight = FontWeight.SemiBold,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        modifier = Modifier.weight(1f)
                    )
                    Spacer(Modifier.width(8.dp))
                    Text(
                        timeLabel,
                        style = MaterialTheme.typography.labelMedium,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.primary
                    )
                }
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        "${app.sessions.size} session${if (app.sessions.size != 1) "s" else ""}",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Text(
                        "${(fraction * 100).toInt()}%",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
                FGInlineProgress(progress = fraction)
            }

            Spacer(Modifier.width(8.dp))
            Icon(
                Icons.Default.ChevronRight,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.4f),
                modifier = Modifier.size(16.dp)
            )
        }
    }
}

private val UsageRange.label: String
    get() = when (this) {
        UsageRange.TODAY       -> "Today"
        UsageRange.YESTERDAY   -> "Yesterday"
        UsageRange.LAST_7_DAYS -> "7 Days"
    }
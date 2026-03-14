package appblocker.appblocker.ui.screens

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
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ChevronRight
import androidx.compose.material.icons.filled.Clear
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.Card
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
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

@Composable
fun AppUsageScreen(
    vm: AppUsageViewModel,
    onOpenApp: (String) -> Unit,
    modifier: Modifier = Modifier
) {
    val state        by vm.state.collectAsState()
    val searchQuery  by vm.searchQuery.collectAsState()
    val searchResults by vm.searchResults.collectAsState()

    Column(modifier = modifier.fillMaxSize()) {
        // ── Search bar ───────────────────────────────────────────────────────
        OutlinedTextField(
            value         = searchQuery,
            onValueChange = vm::setSearchQuery,
            placeholder   = { Text("Search any app…") },
            leadingIcon   = { Icon(Icons.Default.Search, null) },
            trailingIcon  = {
                if (searchQuery.isNotBlank()) {
                    IconButton(onClick = { vm.setSearchQuery("") }) {
                        Icon(Icons.Default.Clear, null)
                    }
                }
            },
            singleLine    = true,
            shape         = CircleShape,
            modifier      = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp, vertical = 8.dp)
        )

        if (searchQuery.isNotBlank()) {
            // ── Search results mode ──────────────────────────────────────────
            if (searchResults.isEmpty()) {
                Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    Text("No apps matching $searchQuery", color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            } else {
                LazyColumn(contentPadding = PaddingValues(horizontal = 16.dp, vertical = 4.dp)) {
                    items(searchResults, key = { it.packageName }) { app ->
                        SearchResultRow(app = app, onClick = { onOpenApp(app.packageName) })
                    }
                }
            }
        } else {
            // ── Normal usage mode ────────────────────────────────────────────
            if (state.customRangeLabel == null) {
                Row(
                    modifier = Modifier.padding(horizontal = 16.dp, vertical = 4.dp),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    UsageRange.entries.forEach { range ->
                        FilterChip(
                            selected = state.selectedRange == range,
                            onClick  = { vm.loadUsage(range) },
                            label    = { Text(range.label) }
                        )
                    }
                }
            } else {
                Row(
                    modifier = Modifier.padding(horizontal = 16.dp, vertical = 4.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text("${state.customRangeLabel}", style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.SemiBold)
                    Spacer(Modifier.width(8.dp))
                    FilterChip(selected = false, onClick = { vm.loadUsage(UsageRange.TODAY) }, label = { Text("Back to today") })
                }
            }

            if (state.isLoading) {
                Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    Column(horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(10.dp)) {
                        CircularProgressIndicator()
                        Text("Loading usage…", color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                }
                return@Column
            }

            if (state.error != null) {
                Box(Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 8.dp)) {
                    Text(state.error ?: "", style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.error)
                }
            }

            val summary = state.summary ?: return@Column
            val maxMs   = summary.appStats.maxOfOrNull { it.totalTimeMs } ?: 1L
            val isMultiDay = summary.dailyBuckets.size > 1
            val chartEntries: List<FGChartEntry> = if (isMultiDay) {
                summary.dailyBuckets.map { FGChartEntry(it.dayLabel, it.totalTimeMs.toFloat()) }
            } else {
                summary.hourlyBuckets.map { b ->
                    FGChartEntry(
                        xLabel = when (b.hourOfDay) { 6 -> "6a"; 12 -> "12p"; 18 -> "6p"; 23 -> "12a"; else -> "" },
                        value  = b.totalTimeMs.toFloat()
                    )
                }
            }

            LazyColumn(contentPadding = PaddingValues(start = 16.dp, end = 16.dp, bottom = 96.dp)) {
                item {
                    FGSectionHeader("App usage", "Screen time overview")
                    Spacer(Modifier.height(8.dp))
                }

                item { UsageHeroCard(summary) }

                item {
                    Card(modifier = Modifier.fillMaxWidth().padding(top = 12.dp), shape = RoundedCornerShape(20.dp)) {
                        Column(Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                            Text(if (isMultiDay) "Daily breakdown" else "Today timeline", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
                            FGBarChart(entries = chartEntries, modifier = Modifier.fillMaxWidth().height(130.dp), maxVisibleLabels = if (isMultiDay) 7 else 4)
                        }
                    }
                }

                item {
                    Spacer(Modifier.height(16.dp))
                    FGSectionHeader("Top apps", "Tap any app for detailed sessions")
                    Spacer(Modifier.height(10.dp))
                }

                if (summary.appStats.isEmpty()) {
                    item {
                        Box(Modifier.fillMaxWidth().padding(vertical = 24.dp), contentAlignment = Alignment.Center) {
                            Text("No app usage found for this range.", color = MaterialTheme.colorScheme.onSurfaceVariant)
                        }
                    }
                } else {
                    items(summary.appStats, key = { it.packageName }) { app ->
                        UsageAppRow(app = app, maxMs = maxMs, onClick = { onOpenApp(app.packageName) })
                    }
                }
            }
        }
    }
}

@Composable
private fun SearchResultRow(app: InstalledApp, onClick: () -> Unit) {
    Row(
        modifier = Modifier.fillMaxWidth().clickable { onClick() }.padding(vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        AppIconImage(packageName = app.packageName, appName = app.label, modifier = Modifier.size(40.dp), cornerRadius = 10.dp)
        Spacer(Modifier.width(12.dp))
        Text(app.label, style = MaterialTheme.typography.bodyLarge)
        Spacer(Modifier.weight(1f))
        Icon(Icons.Default.ChevronRight, null, tint = MaterialTheme.colorScheme.onSurfaceVariant)
    }
    HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.3f))
}

@Composable
private fun UsageHeroCard(summary: UsageSummary) {
    val h = summary.totalScreenTimeMs / 3_600_000
    val m = (summary.totalScreenTimeMs % 3_600_000) / 60_000
    Card(modifier = Modifier.fillMaxWidth(), shape = RoundedCornerShape(22.dp)) {
        Column(
            modifier = Modifier.fillMaxWidth().padding(18.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(4.dp)
        ) {
            Text(if (h > 0) "${h}h ${m}m" else "${m}m", fontSize = 42.sp, fontWeight = FontWeight.Bold)
            Text("Total screen time", style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
    }
}

@Composable
private fun UsageAppRow(app: AppUsageStat, maxMs: Long, onClick: () -> Unit) {
    Card(modifier = Modifier.fillMaxWidth().padding(bottom = 10.dp).clickable { onClick() }, shape = RoundedCornerShape(18.dp)) {
        Row(modifier = Modifier.fillMaxWidth().padding(14.dp), verticalAlignment = Alignment.CenterVertically) {
            AppIconImage(packageName = app.packageName, appName = app.appName, modifier = Modifier.size(44.dp), cornerRadius = 10.dp)
            Spacer(Modifier.width(12.dp))
            Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                Text(app.appName, fontWeight = FontWeight.SemiBold)
                val h = app.totalTimeMs / 3_600_000; val m = (app.totalTimeMs % 3_600_000) / 60_000; val s = (app.totalTimeMs % 60_000) / 1_000
                Text(when { h > 0 -> "${h}h ${m}m"; m > 0 -> "${m}m ${s}s"; else -> "${s}s" }, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                FGInlineProgress(progress = app.totalTimeMs.toFloat() / maxMs.toFloat())
            }
            Icon(Icons.Default.ChevronRight, null, tint = MaterialTheme.colorScheme.onSurfaceVariant)
        }
    }
}

private val UsageRange.label: String
    get() = when (this) {
        UsageRange.TODAY       -> "Today"
        UsageRange.YESTERDAY   -> "Yesterday"
        UsageRange.LAST_7_DAYS -> "7 Days"
    }

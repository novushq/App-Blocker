package appblocker.appblocker.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import appblocker.appblocker.data.repository.UsageStatsRepository.AppSession
import appblocker.appblocker.data.repository.UsageStatsRepository.AppUsageStat
import appblocker.appblocker.data.repository.UsageStatsRepository.HourlyBucket
import appblocker.appblocker.data.repository.UsageStatsRepository.UsageSummary
import appblocker.appblocker.ui.viewmodel.AppUsageUiState
import appblocker.appblocker.ui.viewmodel.AppUsageViewModel
import appblocker.appblocker.ui.viewmodel.UsageRange

@Composable
fun AppUsageScreen(vm: AppUsageViewModel) {
    val state by vm.state.collectAsState()

    // Drill-down: if an app is selected, show its session detail
    if (state.drillDownApp != null) {
        AppDetailScreen(
            app     = state.drillDownApp!!,
            onBack  = { vm.clearDrillDown() }
        )
        return
    }

    UsageListScreen(state = state, onRangeChange = vm::loadUsage, onAppClick = vm::drillInto)
}

// ── Main list screen ──────────────────────────────────────────────────────────

@Composable
fun UsageListScreen(
    state: AppUsageUiState,
    onRangeChange: (UsageRange) -> Unit,
    onAppClick: (AppUsageStat) -> Unit
) {
    Column(modifier = Modifier.fillMaxSize()) {

        // Range selector chips
        Row(
            modifier = Modifier.padding(horizontal = 16.dp, vertical = 12.dp),
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            UsageRange.entries.forEach { range ->
                FilterChip(
                    selected = state.selectedRange == range,
                    onClick  = { onRangeChange(range) },
                    label    = { Text(range.label) }
                )
            }
        }

        if (state.isLoading) {
            Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                CircularProgressIndicator()
            }
            return@Column
        }

        val summary = state.summary ?: return@Column

        LazyColumn(contentPadding = PaddingValues(bottom = 80.dp)) {

            // Total screen time header
            item {
                TotalScreenTimeHeader(summary)
            }

            // Hourly bar chart
            item {
                HourlyBarChart(
                    buckets  = summary.hourlyBuckets,
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(120.dp)
                        .padding(horizontal = 16.dp, vertical = 8.dp)
                )
            }

            // Per-app rows
            items(summary.appStats, key = { it.packageName }) { app ->
                AppUsageRow(app = app, onClick = { onAppClick(app) })
            }
        }
    }
}

// ── Total screen time header ──────────────────────────────────────────────────

@Composable
fun TotalScreenTimeHeader(summary: UsageSummary) {
    val hours   = summary.totalScreenTimeMs / 3_600_000
    val minutes = (summary.totalScreenTimeMs % 3_600_000) / 60_000

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 8.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Text(
            text = buildString {
                if (hours > 0) append("${hours}h ")
                append("${minutes}m")
            },
            fontSize = 40.sp,
            fontWeight = FontWeight.Bold
        )
    }
}

// ── Hourly bar chart ──────────────────────────────────────────────────────────

@Composable
fun HourlyBarChart(
    buckets: List<HourlyBucket>,
    modifier: Modifier = Modifier
) {
    val maxMs = buckets.maxOfOrNull { it.totalTimeMs }?.takeIf { it > 0 } ?: 1L

    Column(modifier = modifier) {
        // Bars
        Row(
            modifier             = Modifier.weight(1f).fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(2.dp),
            verticalAlignment    = Alignment.Bottom
        ) {
            buckets.forEach { bucket ->
                val fraction = (bucket.totalTimeMs.toFloat() / maxMs).coerceIn(0f, 1f)
                Box(
                    modifier = Modifier
                        .weight(1f)
                        .fillMaxHeight(fraction.coerceAtLeast(0.02f))
                        .clip(RoundedCornerShape(topStart = 2.dp, topEnd = 2.dp))
                        .background(MaterialTheme.colorScheme.primary.copy(alpha = 0.7f))
                )
            }
        }

        // X-axis labels: only 6am, 12pm, 6pm, 12am
        Spacer(Modifier.height(4.dp))
        Box(modifier = Modifier.fillMaxWidth()) {
            listOf(6 to "6:00 am", 12 to "12:00 pm", 18 to "6:00 pm", 23 to "12:00 am")
                .forEach { (hour, label) ->
                    val fraction = hour / 23f
                    Text(
                        text     = label,
                        fontSize = 9.sp,
                        color    = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier
                            .align(Alignment.CenterStart)
                            .offset(x = (fraction * 1f * 300).dp)  // approximation; works for most screen widths
                    )
                }
        }
    }
}

// ── Per-app row ───────────────────────────────────────────────────────────────

@Composable
fun AppUsageRow(app: AppUsageStat, onClick: () -> Unit) {
    val hours   = app.totalTimeMs / 3_600_000
    val minutes = (app.totalTimeMs % 3_600_000) / 60_000
    val seconds = (app.totalTimeMs % 60_000) / 1_000

    val timeLabel = when {
        hours > 0   -> "${hours}h ${minutes}m"
        minutes > 0 -> "${minutes}m ${seconds}s"
        else        -> "${seconds}s"
    }

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable { onClick() }
            .padding(horizontal = 16.dp, vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        // App icon placeholder (replace with Coil AsyncImage if you want real icons)
        Box(
            modifier = Modifier
                .size(44.dp)
                .clip(RoundedCornerShape(10.dp))
                .background(MaterialTheme.colorScheme.surfaceVariant),
            contentAlignment = Alignment.Center
        ) {
            Text(
                text     = app.appName.take(1).uppercase(),
                fontSize = 18.sp,
                fontWeight = FontWeight.Bold,
                color    = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }

        Spacer(Modifier.width(12.dp))

        Column(modifier = Modifier.weight(1f)) {
            Text(app.appName, fontWeight = FontWeight.Medium)
            // Usage bar
            // Usage bar – thin line like in screenshot
            Spacer(Modifier.height(4.dp))
            Text(timeLabel, style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant)
        }

        Icon(
            imageVector   = Icons.Default.ArrowBack,
            contentDescription = null,
            modifier      = Modifier
                .size(16.dp)
                .graphicsLayer(rotationZ = 180f),
            tint          = MaterialTheme.colorScheme.onSurfaceVariant
        )
    }

    HorizontalDivider(modifier = Modifier.padding(horizontal = 16.dp))
}

// ── App detail / drill-down screen ────────────────────────────────────────────

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AppDetailScreen(app: AppUsageStat, onBack: () -> Unit) {
    val hours   = app.totalTimeMs / 3_600_000
    val minutes = (app.totalTimeMs % 3_600_000) / 60_000

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(app.appName) },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.Default.ArrowBack, contentDescription = "Back")
                    }
                }
            )
        }
    ) { padding ->
        LazyColumn(
            contentPadding = PaddingValues(
                start  = 16.dp,
                end    = 16.dp,
                top    = padding.calculateTopPadding() + 8.dp,
                bottom = 80.dp
            ),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            // Total time for this app
            item {
                Column(
                    modifier            = Modifier.fillMaxWidth(),
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    // Icon placeholder
                    Box(
                        modifier = Modifier
                            .size(64.dp)
                            .clip(RoundedCornerShape(16.dp))
                            .background(MaterialTheme.colorScheme.surfaceVariant),
                        contentAlignment = Alignment.Center
                    ) {
                        Text(app.appName.take(1).uppercase(), fontSize = 28.sp,
                            fontWeight = FontWeight.Bold)
                    }
                    Spacer(Modifier.height(8.dp))
                    Text(
                        text = "${hours}h ${minutes}m",
                        fontSize = 36.sp,
                        fontWeight = FontWeight.Bold
                    )
                }
            }

            // App stats section (matches screenshot: "Times blocked", "Notifications blocked")
            item {
                Text("App stats", style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.SemiBold)
            }

            item {
                Card(modifier = Modifier.fillMaxWidth()) {
                    Column(modifier = Modifier.padding(16.dp)) {
                        StatRow(label = "Sessions today", value = "${app.sessions.size}")
                        HorizontalDivider(modifier = Modifier.padding(vertical = 8.dp))
                        StatRow(label = "Longest session",
                            value = formatDuration(app.sessions.maxOfOrNull { it.durationMs } ?: 0L))
                        HorizontalDivider(modifier = Modifier.padding(vertical = 8.dp))
                        StatRow(label = "Average session",
                            value = if (app.sessions.isEmpty()) "—"
                                    else formatDuration(app.totalTimeMs / app.sessions.size))
                    }
                }
            }

            // Sessions list
            if (app.sessions.isNotEmpty()) {
                item {
                    Text("Sessions", style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.SemiBold)
                }
                items(app.sessions) { session ->
                    SessionRow(session)
                }
            }
        }
    }
}

@Composable
fun StatRow(label: String, value: String) {
    Row(
        modifier            = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment   = Alignment.CenterVertically
    ) {
        Text(label, style = MaterialTheme.typography.bodyMedium)
        Text(value, style = MaterialTheme.typography.bodyMedium,
            fontWeight = FontWeight.Medium)
    }
}

@Composable
fun SessionRow(session: AppSession) {
    val startCal = java.util.Calendar.getInstance().apply { timeInMillis = session.startTime }
    val endCal   = java.util.Calendar.getInstance().apply { timeInMillis = session.endTime }

    Row(
        modifier            = Modifier
            .fillMaxWidth()
            .padding(vertical = 6.dp),
        horizontalArrangement = Arrangement.SpaceBetween
    ) {
        Text(
            text  = "%02d:%02d → %02d:%02d".format(
                startCal.get(java.util.Calendar.HOUR_OF_DAY),
                startCal.get(java.util.Calendar.MINUTE),
                endCal.get(java.util.Calendar.HOUR_OF_DAY),
                endCal.get(java.util.Calendar.MINUTE)
            ),
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
        Text(
            text  = formatDuration(session.durationMs),
            style = MaterialTheme.typography.bodySmall,
            fontWeight = FontWeight.Medium
        )
    }
    HorizontalDivider()
}

// ── Helpers ───────────────────────────────────────────────────────────────────

private fun formatDuration(ms: Long): String {
    val h = ms / 3_600_000
    val m = (ms % 3_600_000) / 60_000
    val s = (ms % 60_000) / 1_000
    return when {
        h > 0   -> "${h}h ${m}m"
        m > 0   -> "${m}m ${s}s"
        else    -> "${s}s"
    }
}

private val UsageRange.label get() = when (this) {
    UsageRange.TODAY       -> "Today"
    UsageRange.YESTERDAY   -> "Yesterday"
    UsageRange.LAST_7_DAYS -> "7 Days"
}



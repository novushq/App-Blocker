package appblocker.appblocker.ui.screens

import android.content.Intent
import android.net.Uri
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
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.DeleteForever
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import appblocker.appblocker.data.repository.UsageStatsRepository.AppSession
import appblocker.appblocker.data.repository.UsageStatsRepository.AppUsageStat
import appblocker.appblocker.ui.components.AppIconImage
import appblocker.appblocker.ui.components.FGBarChart
import appblocker.appblocker.ui.components.FGChartEntry
import appblocker.appblocker.ui.components.FGInlineProgress
import appblocker.appblocker.ui.components.FGKeyValueRow
import appblocker.appblocker.ui.components.FGSectionHeader
import appblocker.appblocker.ui.viewmodel.AppDetailRange
import appblocker.appblocker.ui.viewmodel.AppDetailViewModel

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AppDetailScreen(
    vm: AppDetailViewModel,
    onBack: () -> Unit
) {
    val state by vm.state.collectAsState()
    val context = LocalContext.current

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(state.appName.ifBlank { state.packageName }) },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
                    }
                }
            )
        }
    ) { padding ->
        if (state.isLoading) {
            Box(Modifier.fillMaxSize().padding(padding), contentAlignment = Alignment.Center) {
                Column(horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    CircularProgressIndicator()
                    Text("Loading…", color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            }
            return@Scaffold
        }

        if (state.error != null) {
            Box(Modifier.fillMaxSize().padding(padding), contentAlignment = Alignment.Center) {
                Text(state.error ?: "", color = MaterialTheme.colorScheme.error)
            }
            return@Scaffold
        }

        LazyColumn(
            contentPadding = PaddingValues(
                start = 16.dp, end = 16.dp,
                top   = padding.calculateTopPadding() + 8.dp,
                bottom = 100.dp
            ),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {

            // ── Hero ──────────────────────────────────────────────────────────
            item {
                Card(shape = RoundedCornerShape(22.dp), modifier = Modifier.fillMaxWidth()) {
                    Column(
                        modifier = Modifier.fillMaxWidth().padding(20.dp),
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        AppIconImage(
                            packageName  = state.packageName,
                            appName      = state.appName,
                            modifier     = Modifier.size(68.dp),
                            cornerRadius = 18.dp
                        )
                        Text(state.appName, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
                        val h = (state.appStat?.totalTimeMs ?: 0L) / 3_600_000
                        val m = ((state.appStat?.totalTimeMs ?: 0L) % 3_600_000) / 60_000
                        Text(
                            if (h > 0) "${h}h ${m}m" else "${m}m",
                            fontSize = 36.sp, fontWeight = FontWeight.Bold
                        )
                        Text(state.selectedRange.label, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                }
            }

            // ── Range picker ──────────────────────────────────────────────────
            item {
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    AppDetailRange.entries.forEach { range ->
                        FilterChip(
                            selected = state.selectedRange == range,
                            onClick  = { vm.loadRange(range) },
                            label    = { Text(range.label) }
                        )
                    }
                }
            }

            // ── Chart ─────────────────────────────────────────────────────────
            item {
                val isMultiDay = state.dailyBuckets.size > 1
                val entries: List<FGChartEntry> = if (isMultiDay) {
                    state.dailyBuckets.map { FGChartEntry(it.dayLabel, it.totalTimeMs.toFloat()) }
                } else {
                    state.hourlyBuckets.map { b ->
                        FGChartEntry(
                            xLabel = when (b.hourOfDay) { 6 -> "6a"; 12 -> "12p"; 18 -> "6p"; 23 -> "12a"; else -> "" },
                            value  = b.totalTimeMs.toFloat()
                        )
                    }
                }
                Card(modifier = Modifier.fillMaxWidth(), shape = RoundedCornerShape(18.dp)) {
                    Column(Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                        Text(
                            if (isMultiDay) "Daily usage breakdown" else "Usage by hour",
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.SemiBold
                        )
                        FGBarChart(
                            entries          = entries,
                            modifier         = Modifier.fillMaxWidth().height(140.dp),
                            maxVisibleLabels = if (isMultiDay) entries.size else 4
                        )
                    }
                }
            }

            // ── Stats ─────────────────────────────────────────────────────────
            item { FGSectionHeader("App stats") }

            item {
                val appStat = state.appStat
                Card(modifier = Modifier.fillMaxWidth(), shape = RoundedCornerShape(18.dp)) {
                    Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                        FGKeyValueRow("Sessions",  (appStat?.sessions?.size ?: 0).toString())
                        HorizontalDivider()
                        FGKeyValueRow("Longest session",
                            formatDuration(appStat?.sessions?.maxOfOrNull { it.durationMs } ?: 0L))
                        HorizontalDivider()
                        FGKeyValueRow("Average session",
                            if (appStat == null || appStat.sessions.isEmpty()) "-"
                            else formatDuration(appStat.totalTimeMs / appStat.sessions.size))
                    }
                }
            }

            // ── Sessions ──────────────────────────────────────────────────────
            if (!state.appStat?.sessions.isNullOrEmpty()) {
                item { FGSectionHeader("Sessions", "Latest to oldest") }
                items(state.appStat!!.sessions) { session -> DetailSessionRow(session) }
            } else {
                item {
                    Box(Modifier.fillMaxWidth().padding(vertical = 16.dp), contentAlignment = Alignment.Center) {
                        Text("No sessions recorded for this range.", color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                }
            }

            // ── Uninstall ─────────────────────────────────────────────────────
            item {
                if (vm.isInstalled()) {
                    Spacer(Modifier.height(4.dp))
                    Button(
                        onClick = {
                            context.startActivity(
                                Intent(Intent.ACTION_DELETE).apply {
                                    data = Uri.parse("package:${state.packageName}")
                                }
                            )
                        },
                        modifier = Modifier.fillMaxWidth().height(52.dp),
                        shape    = RoundedCornerShape(14.dp),
                        colors   = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.error)
                    ) {
                        Icon(Icons.Default.DeleteForever, null)
                        Spacer(Modifier.width(8.dp))
                        Text("Uninstall", fontWeight = FontWeight.Bold)
                    }
                }
            }
        }
    }
}

@Composable
private fun DetailSessionRow(session: AppSession) {
    val startCal = java.util.Calendar.getInstance().apply { timeInMillis = session.startTime }
    val endCal   = java.util.Calendar.getInstance().apply { timeInMillis = session.endTime }
    Card(modifier = Modifier.fillMaxWidth(), shape = RoundedCornerShape(14.dp)) {
        Row(
            modifier = Modifier.fillMaxWidth().padding(horizontal = 14.dp, vertical = 12.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment     = Alignment.CenterVertically
        ) {
            Text(
                "%02d:%02d → %02d:%02d".format(
                    startCal.get(java.util.Calendar.HOUR_OF_DAY), startCal.get(java.util.Calendar.MINUTE),
                    endCal.get(java.util.Calendar.HOUR_OF_DAY),   endCal.get(java.util.Calendar.MINUTE)
                ),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            Text(formatDuration(session.durationMs), style = MaterialTheme.typography.bodySmall, fontWeight = FontWeight.SemiBold)
        }
    }
}

private fun formatDuration(ms: Long): String {
    val h = ms / 3_600_000; val m = (ms % 3_600_000) / 60_000; val s = (ms % 60_000) / 1_000
    return when { h > 0 -> "${h}h ${m}m"; m > 0 -> "${m}m ${s}s"; else -> "${s}s" }
}


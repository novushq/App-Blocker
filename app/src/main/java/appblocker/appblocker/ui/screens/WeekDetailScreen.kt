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
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.ChevronRight
import androidx.compose.material3.Card
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import appblocker.appblocker.data.repository.UsageStatsRepository.AppUsageStat
import appblocker.appblocker.ui.components.AppIconImage
import appblocker.appblocker.ui.components.FGBarChart
import appblocker.appblocker.ui.components.FGChartEntry
import appblocker.appblocker.ui.components.FGInlineProgress
import appblocker.appblocker.ui.components.FGSectionHeader
import appblocker.appblocker.ui.viewmodel.WeekDetailViewModel

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun WeekDetailScreen(
    vm: WeekDetailViewModel,
    onBack: () -> Unit,
    onOpenApp: (String) -> Unit
) {
    val state by vm.state.collectAsState()

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(state.weekLabel.ifBlank { "Week detail" }) },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
                    }
                }
            )
        }
    ) { padding ->
        when {
            state.isLoading -> {
                Box(Modifier.fillMaxSize().padding(padding), contentAlignment = Alignment.Center) {
                    Column(horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(10.dp)) {
                        CircularProgressIndicator()
                        Text("Loading week…", color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                }
            }

            state.error != null -> {
                Box(Modifier.fillMaxSize().padding(padding), contentAlignment = Alignment.Center) {
                    Text(state.error ?: "", color = MaterialTheme.colorScheme.error)
                }
            }

            else -> {
                val maxApp = state.appStats.maxOfOrNull { it.totalTimeMs } ?: 1L

                LazyColumn(
                    contentPadding = PaddingValues(
                        start  = 16.dp, end = 16.dp,
                        top    = padding.calculateTopPadding() + 8.dp,
                        bottom = 32.dp
                    ),
                    verticalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    // Hero total
                    item {
                        Card(shape = RoundedCornerShape(22.dp), modifier = Modifier.fillMaxWidth()) {
                            Column(
                                modifier = Modifier.fillMaxWidth().padding(18.dp),
                                horizontalAlignment = Alignment.CenterHorizontally,
                                verticalArrangement = Arrangement.spacedBy(4.dp)
                            ) {
                                val h = state.totalScreenTimeMs / 3_600_000
                                val m = (state.totalScreenTimeMs % 3_600_000) / 60_000
                                Text(
                                    if (h > 0) "${h}h ${m}m" else "${m}m",
                                    fontSize = 42.sp, fontWeight = FontWeight.Bold
                                )
                                Text("Total screen time this week", style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                            }
                        }
                    }

                    // Daily chart
                    if (state.dailyBuckets.isNotEmpty()) {
                        item {
                            Card(modifier = Modifier.fillMaxWidth(), shape = RoundedCornerShape(18.dp)) {
                                Column(Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                                    Text("Daily breakdown", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
                                    FGBarChart(
                                        entries = state.dailyBuckets.map { FGChartEntry(it.dayLabel, it.totalTimeMs.toFloat()) },
                                        modifier = Modifier.fillMaxWidth().height(140.dp),
                                        maxVisibleLabels = 7
                                    )
                                }
                            }
                        }
                    }

                    // App list
                    item {
                        Spacer(Modifier.height(4.dp))
                        FGSectionHeader("Top apps", "Tap any app to see full details")
                    }

                    if (state.appStats.isEmpty()) {
                        item {
                            Box(Modifier.fillMaxWidth().padding(vertical = 24.dp), contentAlignment = Alignment.Center) {
                                Text("No app usage found for this week.", color = MaterialTheme.colorScheme.onSurfaceVariant)
                            }
                        }
                    } else {
                        items(state.appStats, key = { it.packageName }) { app ->
                            WeekAppRow(app = app, maxMs = maxApp, onClick = { onOpenApp(app.packageName) })
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun WeekAppRow(app: AppUsageStat, maxMs: Long, onClick: () -> Unit) {
    Card(
        modifier = Modifier.fillMaxWidth().padding(bottom = 8.dp).clickable { onClick() },
        shape    = RoundedCornerShape(16.dp)
    ) {
        Row(
            modifier = Modifier.fillMaxWidth().padding(14.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            AppIconImage(packageName = app.packageName, appName = app.appName, modifier = Modifier.size(42.dp), cornerRadius = 10.dp)
            Spacer(Modifier.width(12.dp))
            Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                Text(app.appName, fontWeight = FontWeight.SemiBold)
                val h = app.totalTimeMs / 3_600_000; val m = (app.totalTimeMs % 3_600_000) / 60_000; val s = (app.totalTimeMs % 60_000) / 1_000
                Text(
                    when { h > 0 -> "${h}h ${m}m"; m > 0 -> "${m}m ${s}s"; else -> "${s}s" },
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                FGInlineProgress(progress = app.totalTimeMs.toFloat() / maxMs.toFloat())
            }
            Icon(Icons.Default.ChevronRight, null, tint = MaterialTheme.colorScheme.onSurfaceVariant)
        }
    }
}




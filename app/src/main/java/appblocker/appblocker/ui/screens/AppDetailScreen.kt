package appblocker.appblocker.ui.screens

import android.content.Intent
import android.net.Uri
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.EaseOutCubic
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
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
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.DeleteForever
import androidx.compose.material.icons.filled.Schedule
import androidx.compose.material.icons.filled.ShowChart
import androidx.compose.material.icons.filled.TouchApp
import androidx.compose.material.icons.filled.Timer
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
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
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import appblocker.appblocker.data.repository.UsageStatsRepository.AppSession
import appblocker.appblocker.ui.components.AppIconImage
import appblocker.appblocker.ui.components.FGBarChart
import appblocker.appblocker.ui.components.FGChartEntry
import appblocker.appblocker.ui.components.FGKeyValueRow
import appblocker.appblocker.ui.components.FGSectionHeader
import appblocker.appblocker.ui.viewmodel.AppDetailRange
import appblocker.appblocker.ui.viewmodel.AppDetailUiState
import appblocker.appblocker.ui.viewmodel.AppDetailViewModel

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AppDetailScreen(
    vm: AppDetailViewModel,
    onBack: () -> Unit
) {
    val state by vm.state.collectAsState()
    val context = LocalContext.current

    // Fade-in animation for the whole screen content
    val contentAlpha = remember { Animatable(0f) }
    LaunchedEffect(state.isLoading) {
        if (!state.isLoading) {
            contentAlpha.animateTo(1f, animationSpec = tween(500, easing = EaseOutCubic))
        }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Text(
                        text = state.appName.ifBlank { state.packageName },
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.SemiBold
                    )
                },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Box(
                            modifier = Modifier
                                .size(36.dp)
                                .clip(CircleShape)
                                .background(MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.6f)),
                            contentAlignment = Alignment.Center
                        ) {
                            Icon(
                                Icons.AutoMirrored.Filled.ArrowBack,
                                contentDescription = "Back",
                                modifier = Modifier.size(18.dp)
                            )
                        }
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.background
                )
            )
        },
        containerColor = MaterialTheme.colorScheme.background
    ) { padding ->

        // ── Loading ──────────────────────────────────────────────────────────
        if (state.isLoading) {
            Box(Modifier.fillMaxSize().padding(padding), contentAlignment = Alignment.Center) {
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
            return@Scaffold
        }

        // ── Error ────────────────────────────────────────────────────────────
        if (state.error != null) {
            Box(Modifier.fillMaxSize().padding(padding), contentAlignment = Alignment.Center) {
                Text(state.error ?: "", color = MaterialTheme.colorScheme.error)
            }
            return@Scaffold
        }

        LazyColumn(
            modifier = Modifier.graphicsLayer { alpha = contentAlpha.value },
            contentPadding = PaddingValues(
                start = 16.dp, end = 16.dp,
                top   = padding.calculateTopPadding() + 8.dp,
                bottom = 100.dp
            ),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {

            // ── Hero Card ────────────────────────────────────────────────────
            item {
                HeroCard(state = state)
            }

            // ── Range Picker ─────────────────────────────────────────────────
            item {
                LazyRow(
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    contentPadding = PaddingValues(horizontal = 2.dp)
                ) {
                    items(enumValues<AppDetailRange>().toList()) { range ->
                        FilterChip(
                            selected = state.selectedRange == range,
                            onClick  = { vm.loadRange(range) },
                            label    = {
                                Text(
                                    range.label,
                                    style = MaterialTheme.typography.labelMedium,
                                    fontWeight = if (state.selectedRange == range) FontWeight.SemiBold else FontWeight.Normal
                                )
                            },
                            shape = RoundedCornerShape(10.dp),
                            colors = FilterChipDefaults.filterChipColors(
                                selectedContainerColor = MaterialTheme.colorScheme.primary,
                                selectedLabelColor     = MaterialTheme.colorScheme.onPrimary
                            )
                        )
                    }
                }
            }

            // ── Quick Stat Pills ─────────────────────────────────────────────
            item {
                val appStat   = state.appStat
                val sessions  = appStat?.sessions ?: emptyList()
                val totalMs   = appStat?.totalTimeMs ?: 0L
                val avgMs     = if (sessions.isEmpty()) 0L else totalMs / sessions.size
                val longestMs = sessions.maxOfOrNull { it.durationMs } ?: 0L

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    QuickStatCard(
                        icon    = Icons.Default.TouchApp,
                        label   = "Sessions",
                        value   = sessions.size.toString(),
                        modifier = Modifier.weight(1f)
                    )
                    QuickStatCard(
                        icon    = Icons.Default.Timer,
                        label   = "Avg session",
                        value   = formatDuration(avgMs),
                        modifier = Modifier.weight(1f)
                    )
                    QuickStatCard(
                        icon    = Icons.Default.ShowChart,
                        label   = "Longest",
                        value   = formatDuration(longestMs),
                        modifier = Modifier.weight(1f)
                    )
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

                Card(
                    modifier = Modifier.fillMaxWidth(),
                    shape    = RoundedCornerShape(20.dp),
                    colors   = CardDefaults.cardColors(
                        containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.4f)
                    ),
                    elevation = CardDefaults.cardElevation(defaultElevation = 0.dp)
                ) {
                    Column(Modifier.padding(18.dp), verticalArrangement = Arrangement.spacedBy(14.dp)) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Column {
                                Text(
                                    if (isMultiDay) "Daily breakdown" else "Hourly breakdown",
                                    style      = MaterialTheme.typography.titleSmall,
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
                                tint     = MaterialTheme.colorScheme.primary.copy(alpha = 0.7f),
                                modifier = Modifier.size(20.dp)
                            )
                        }
                        FGBarChart(
                            entries          = entries,
                            modifier         = Modifier.fillMaxWidth().height(150.dp),
                            maxVisibleLabels = if (isMultiDay) 7 else 4
                        )
                    }
                }
            }

            // ── Usage Intensity Bar ──────────────────────────────────────────
            item {
                val totalMs  = state.appStat?.totalTimeMs ?: 0L
                // Max "healthy" daily usage ~2h, scaled for range
                val rangeMultiplier = when (state.selectedRange) {
                    AppDetailRange.TODAY     -> 1
                    AppDetailRange.YESTERDAY -> 1
                    AppDetailRange.LAST_7    -> 7
                    AppDetailRange.LAST_30   -> 30
                }
                val maxMs    = 2 * 3_600_000L * rangeMultiplier
                val fraction = (totalMs.toFloat() / maxMs).coerceIn(0f, 1f)
                val color    = when {
                    fraction < 0.4f -> MaterialTheme.colorScheme.tertiary
                    fraction < 0.75f -> MaterialTheme.colorScheme.primary
                    else            -> MaterialTheme.colorScheme.error
                }
                val label = when {
                    fraction < 0.4f -> "Light usage · looking good 👌"
                    fraction < 0.75f -> "Moderate usage · stay mindful"
                    else            -> "Heavy usage · consider a limit"
                }

                Card(
                    modifier  = Modifier.fillMaxWidth(),
                    shape     = RoundedCornerShape(20.dp),
                    colors    = CardDefaults.cardColors(containerColor = color.copy(alpha = 0.08f)),
                    elevation = CardDefaults.cardElevation(0.dp)
                ) {
                    Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            Text(
                                "Usage intensity",
                                style      = MaterialTheme.typography.titleSmall,
                                fontWeight = FontWeight.SemiBold
                            )
                            Text(
                                "${(fraction * 100).toInt()}%",
                                style      = MaterialTheme.typography.titleSmall,
                                fontWeight = FontWeight.Bold,
                                color      = color
                            )
                        }
                        // Segmented progress bar
                        Box(
                            Modifier
                                .fillMaxWidth()
                                .height(8.dp)
                                .clip(RoundedCornerShape(999.dp))
                                .background(MaterialTheme.colorScheme.surfaceVariant)
                        ) {
                            Box(
                                Modifier
                                    .fillMaxWidth(fraction)
                                    .height(8.dp)
                                    .clip(RoundedCornerShape(999.dp))
                                    .background(
                                        Brush.horizontalGradient(
                                            listOf(color.copy(alpha = 0.6f), color)
                                        )
                                    )
                            )
                        }
                        Text(
                            label,
                            style = MaterialTheme.typography.labelSmall,
                            color = color.copy(alpha = 0.85f)
                        )
                    }
                }
            }

            // ── Detailed Stats ────────────────────────────────────────────────
            item {
                FGSectionHeader("Statistics")
            }

            item {
                val appStat = state.appStat
                Card(
                    modifier  = Modifier.fillMaxWidth(),
                    shape     = RoundedCornerShape(20.dp),
                    colors    = CardDefaults.cardColors(
                        containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.4f)
                    ),
                    elevation = CardDefaults.cardElevation(0.dp)
                ) {
                    Column(Modifier.padding(18.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                        FGKeyValueRow("Total sessions",    (appStat?.sessions?.size ?: 0).toString())
                        HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f))
                        FGKeyValueRow("Longest session",
                            formatDuration(appStat?.sessions?.maxOfOrNull { it.durationMs } ?: 0L))
                        HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f))
                        FGKeyValueRow("Average session",
                            if (appStat == null || appStat.sessions.isEmpty()) "—"
                            else formatDuration(appStat.totalTimeMs / appStat.sessions.size))
                        HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f))
                        val daysActive = state.dailyBuckets.count { it.totalTimeMs > 0 }
                        FGKeyValueRow("Active days", daysActive.toString())
                        if (daysActive > 0) {
                            HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f))
                            FGKeyValueRow(
                                "Avg per active day",
                                formatDuration((appStat?.totalTimeMs ?: 0L) / daysActive)
                            )
                        }
                    }
                }
            }

            // ── Uninstall ─────────────────────────────────────────────────────
            if (vm.isInstalled()) {
                item {
                    OutlinedButton(
                        onClick = {
                            context.startActivity(
                                Intent(Intent.ACTION_DELETE).apply {
                                    data = Uri.parse("package:${state.packageName}")
                                }
                            )
                        },
                        modifier = Modifier.fillMaxWidth().height(52.dp),
                        shape    = RoundedCornerShape(14.dp),
                        colors   = ButtonDefaults.outlinedButtonColors(
                            contentColor = MaterialTheme.colorScheme.error
                        ),
                        border   = androidx.compose.foundation.BorderStroke(
                            1.dp, MaterialTheme.colorScheme.error.copy(alpha = 0.4f)
                        )
                    ) {
                        Icon(Icons.Default.DeleteForever, null, modifier = Modifier.size(18.dp))
                        Spacer(Modifier.width(8.dp))
                        Text("Uninstall app", fontWeight = FontWeight.SemiBold)
                    }
                }
            }

            // ── Sessions ──────────────────────────────────────────────────────
            if (!state.appStat?.sessions.isNullOrEmpty()) {
                item {
                    FGSectionHeader("Sessions", "Latest to oldest")
                }
                items(state.appStat!!.sessions) { session ->
                    DetailSessionRow(session)
                }
            } else {
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
                                "No sessions in this range",
                                style     = MaterialTheme.typography.bodySmall,
                                color     = MaterialTheme.colorScheme.onSurfaceVariant,
                                textAlign = TextAlign.Center
                            )
                        }
                    }
                }
            }
        }
    }
}

// ── Hero Card ─────────────────────────────────────────────────────────────────

@Composable
private fun HeroCard(state: AppDetailUiState) {
    val totalMs = state.appStat?.totalTimeMs ?: 0L
    val h = totalMs / 3_600_000
    val m = (totalMs % 3_600_000) / 60_000

    Card(
        shape     = RoundedCornerShape(24.dp),
        modifier  = Modifier.fillMaxWidth(),
        colors    = CardDefaults.cardColors(
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
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                // Icon with glowing ring
                Box(contentAlignment = Alignment.Center) {
                    Box(
                        Modifier
                            .size(80.dp)
                            .clip(RoundedCornerShape(22.dp))
                            .background(
                                MaterialTheme.colorScheme.primary.copy(alpha = 0.15f)
                            )
                    )
                    AppIconImage(
                        packageName  = state.packageName,
                        appName      = state.appName,
                        modifier     = Modifier.size(68.dp),
                        cornerRadius = 18.dp
                    )
                }

                Spacer(Modifier.height(4.dp))

                Text(
                    state.appName,
                    style      = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.SemiBold,
                    color      = MaterialTheme.colorScheme.onSurface
                )

                // Big time display
                Row(
                    verticalAlignment = Alignment.Bottom,
                    horizontalArrangement = Arrangement.Center
                ) {
                    if (h > 0) {
                        Text(
                            "$h",
                            fontSize   = 52.sp,
                            fontWeight = FontWeight.ExtraBold,
                            color      = MaterialTheme.colorScheme.onSurface,
                            lineHeight = 56.sp
                        )
                        Text(
                            "h ",
                            fontSize   = 20.sp,
                            fontWeight = FontWeight.Medium,
                            color      = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier   = Modifier.padding(bottom = 8.dp)
                        )
                    }
                    Text(
                        "$m",
                        fontSize   = 52.sp,
                        fontWeight = FontWeight.ExtraBold,
                        color      = MaterialTheme.colorScheme.onSurface,
                        lineHeight = 56.sp
                    )
                    Text(
                        "m",
                        fontSize   = 20.sp,
                        fontWeight = FontWeight.Medium,
                        color      = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier   = Modifier.padding(bottom = 8.dp)
                    )
                }

                Text(
                    "screen time · ${state.selectedRange.label}",
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }
    }
}

// ── Quick stat tile ───────────────────────────────────────────────────────────

@Composable
private fun QuickStatCard(
    icon: ImageVector,
    label: String,
    value: String,
    modifier: Modifier = Modifier
) {
    Card(
        modifier  = modifier,
        shape     = RoundedCornerShape(18.dp),
        colors    = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.45f)
        ),
        elevation = CardDefaults.cardElevation(0.dp)
    ) {
        Column(
            Modifier
                .fillMaxWidth()
                .padding(12.dp),
            verticalArrangement   = Arrangement.spacedBy(6.dp),
            horizontalAlignment   = Alignment.Start
        ) {
            Icon(
                icon,
                contentDescription = null,
                tint     = MaterialTheme.colorScheme.primary,
                modifier = Modifier.size(18.dp)
            )
            Text(
                value,
                style      = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold,
                color      = MaterialTheme.colorScheme.onSurface
            )
            Text(
                label,
                style  = MaterialTheme.typography.labelSmall,
                color  = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    }
}

// ── Session row ───────────────────────────────────────────────────────────────

@Composable
private fun DetailSessionRow(session: AppSession) {
    val startCal = java.util.Calendar.getInstance().apply { timeInMillis = session.startTime }
    val endCal   = java.util.Calendar.getInstance().apply { timeInMillis = session.endTime }

    Card(
        modifier  = Modifier.fillMaxWidth(),
        shape     = RoundedCornerShape(14.dp),
        colors    = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.35f)
        ),
        elevation = CardDefaults.cardElevation(0.dp)
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp, vertical = 13.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment     = Alignment.CenterVertically
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                Box(
                    Modifier
                        .size(6.dp)
                        .clip(CircleShape)
                        .background(MaterialTheme.colorScheme.primary.copy(alpha = 0.6f))
                )
                Text(
                    "%02d:%02d → %02d:%02d".format(
                        startCal.get(java.util.Calendar.HOUR_OF_DAY),
                        startCal.get(java.util.Calendar.MINUTE),
                        endCal.get(java.util.Calendar.HOUR_OF_DAY),
                        endCal.get(java.util.Calendar.MINUTE)
                    ),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
            Text(
                formatDuration(session.durationMs),
                style      = MaterialTheme.typography.bodySmall,
                fontWeight = FontWeight.SemiBold,
                color      = MaterialTheme.colorScheme.onSurface
            )
        }
    }
}

private fun formatDuration(ms: Long): String {
    val h = ms / 3_600_000; val m = (ms % 3_600_000) / 60_000; val s = (ms % 60_000) / 1_000
    return when { h > 0 -> "${h}h ${m}m"; m > 0 -> "${m}m ${s}s"; else -> "${s}s" }
}
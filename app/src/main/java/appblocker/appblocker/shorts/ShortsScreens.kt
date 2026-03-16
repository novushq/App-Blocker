package appblocker.appblocker.shorts

import android.net.Uri
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.AutoAwesome
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.filled.Bolt
import androidx.compose.material.icons.filled.CalendarMonth
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Shield
import androidx.compose.material.icons.filled.Timer
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import appblocker.appblocker.ui.screens.onboardingResearchInsights
import appblocker.appblocker.ui.screens.quoteForIndex
import kotlinx.coroutines.delay
import org.koin.androidx.compose.koinViewModel
import java.text.SimpleDateFormat
import java.util.*
import kotlin.math.roundToInt

// ── Color palette (matches dark green from screenshots) ──────────
private val BG        = Color(0xFF0D1512)
private val SURFACE   = Color(0xFF1A2820)
private val CARD      = Color(0xFF1E2D24)
private val ACCENT    = Color(0xFF4CAF84)
private val ACCENT_DIM = Color(0xFF2E6B50)
private val DANGER    = Color(0xFFC0392B)
private val DANGER_BG = Color(0xFF3B1A18)
private val TXT       = Color(0xFFE8F5EE)
private val TXT2      = Color(0xFF8AB8A0)
private val TXT3      = Color(0xFF4A7A62)
private val DIV       = Color(0xFF1F3028)

private fun platformColor(label: String) = when {
    label.contains("YouTube", ignoreCase = true)   -> Color(0xFFFF0000)
    label.contains("Instagram", ignoreCase = true) -> Color(0xFFE1306C)
    label.contains("TikTok", ignoreCase = true)    -> Color(0xFF69C9D0)
    label.contains("Facebook", ignoreCase = true)  -> Color(0xFF1877F2)
    label.contains("Snap", ignoreCase = true)      -> Color(0xFFFFD60A)
    else -> ACCENT
}

private fun minToStr(m: Int) = "%02d:%02d".format(m / 60, m % 60)

@Composable
private fun rememberShortsServiceEnabled(vm: ShortsViewModel): Boolean {
    val enabled by produceState(initialValue = vm.isShortsAccessibilityEnabled(), vm) {
        while (true) {
            value = vm.isShortsAccessibilityEnabled()
            delay(1_000)
        }
    }
    return enabled
}

private fun remainingPauseMinutes(pausedUntilMs: Long, nowMs: Long = System.currentTimeMillis()): Long =
    ((pausedUntilMs - nowMs).coerceAtLeast(0L) + 59_999L) / 60_000L

private data class ScheduleGroupUi(
    val packageName: String,
    val platformLabel: String,
    val schedules: List<ScheduleUi>,
    val mergedWindows: List<ScheduleWindowUi>
)

private data class ScheduleWindowUi(
    val dayOfWeek: Int?,
    val startMin: Int,
    val endMin: Int,
    val sourceIds: List<Long>,
    val isEnabled: Boolean,
    val pausedUntilMs: Long,
    val alwaysBlock: Boolean,
    val ruleNames: List<String>
)

private data class QuickPlanTemplate(
    val title: String,
    val subtitle: String,
    val days: List<Int>,
    val startMin: Int,
    val endMin: Int,
    val alwaysBlock: Boolean = false
)

private val scheduleDayOrder = listOf(2, 3, 4, 5, 6, 7, 1)

private fun dayShort(day: Int): String = when (day) {
    2 -> "Mon"
    3 -> "Tue"
    4 -> "Wed"
    5 -> "Thu"
    6 -> "Fri"
    7 -> "Sat"
    1 -> "Sun"
    else -> "Day"
}

private fun summarizeDays(days: List<Int>): String {
    val normalized = days.distinct().sortedBy { scheduleDayOrder.indexOf(it) }
    if (normalized.isEmpty()) return "No days"
    val weekdays = listOf(2, 3, 4, 5, 6)
    val weekend = listOf(7, 1)
    return when {
        normalized == weekdays -> "Weekdays"
        normalized == weekend -> "Weekend"
        normalized == scheduleDayOrder -> "Every day"
        else -> normalized.joinToString(" • ") { dayShort(it) }
    }
}

private fun mergeScheduleGroups(schedules: List<ScheduleUi>): List<ScheduleGroupUi> {
    return schedules
        .groupBy { it.packageName }
        .map { (packageName, items) ->
            ScheduleGroupUi(
                packageName = packageName,
                platformLabel = items.firstOrNull()?.platformLabel ?: packageName.ifBlank { "All Platforms" },
                schedules = items.sortedBy { it.startMin },
                mergedWindows = mergeScheduleWindows(items)
            )
        }
        .sortedBy { it.platformLabel.lowercase() }
}

private fun mergeScheduleWindows(schedules: List<ScheduleUi>): List<ScheduleWindowUi> {
    val windows = mutableListOf<ScheduleWindowUi>()

    val alwaysBlocks = schedules.filter { it.alwaysBlock }
    if (alwaysBlocks.isNotEmpty()) {
        windows += ScheduleWindowUi(
            dayOfWeek = null,
            startMin = 0,
            endMin = 0,
            sourceIds = alwaysBlocks.map(ScheduleUi::id),
            isEnabled = alwaysBlocks.any { it.enabled },
            pausedUntilMs = if (alwaysBlocks.all { remainingPauseMinutes(it.pausedUntilMs) > 0L }) alwaysBlocks.maxOf { it.pausedUntilMs } else 0L,
            alwaysBlock = true,
            ruleNames = alwaysBlocks.mapNotNull { it.ruleName.takeIf(String::isNotBlank) }.distinct()
        )
    }

    schedules.filterNot { it.alwaysBlock }
        .flatMap { schedule -> schedule.days.map { day -> day to schedule } }
        .groupBy({ it.first }, { it.second })
        .toSortedMap()
        .forEach { (day, daySchedules) ->
            val sorted = daySchedules.sortedBy { it.startMin }
            var bucket = mutableListOf<ScheduleUi>()
            var currentStart = -1
            var currentEnd = -1

            fun flush() {
                if (bucket.isEmpty()) return
                windows += ScheduleWindowUi(
                    dayOfWeek = day,
                    startMin = currentStart,
                    endMin = currentEnd,
                    sourceIds = bucket.map(ScheduleUi::id),
                    isEnabled = bucket.any { it.enabled },
                    pausedUntilMs = if (bucket.all { remainingPauseMinutes(it.pausedUntilMs) > 0L }) bucket.maxOf { it.pausedUntilMs } else 0L,
                    alwaysBlock = false,
                    ruleNames = bucket.mapNotNull { it.ruleName.takeIf(String::isNotBlank) }.distinct()
                )
                bucket = mutableListOf()
            }

            sorted.forEach { schedule ->
                if (bucket.isEmpty()) {
                    bucket += schedule
                    currentStart = schedule.startMin
                    currentEnd = schedule.endMin
                } else if (schedule.startMin <= currentEnd) {
                    bucket += schedule
                    currentEnd = maxOf(currentEnd, schedule.endMin)
                } else {
                    flush()
                    bucket += schedule
                    currentStart = schedule.startMin
                    currentEnd = schedule.endMin
                }
            }
            flush()
        }

    return windows
}

// ── Nav entry point — add this as your bottom-nav tab composable ─

@Composable
fun ShortsFeature() {
    val nav = rememberNavController()
    NavHost(nav, startDestination = "dashboard") {
        composable("dashboard") {
            DashboardScreen(
                onBlocksClick = { nav.navigate("blocks") },
                onPlatformClick = { label -> nav.navigate("sessions/${Uri.encode(label)}") }
            )
        }
        composable("blocks") {
            BlocksScreen(
                onBack = { nav.popBackStack() },
                onAdd = { pkg -> nav.navigate("add/$pkg") }
            )
        }
        composable("add/{pkg}") { back ->
            val pkg = back.arguments?.getString("pkg") ?: ""
            AddScheduleScreen(preselectedPkg = pkg, onBack = { nav.popBackStack() })
        }
        composable("sessions/{label}") { back ->
            val label = Uri.decode(back.arguments?.getString("label") ?: "")
            SessionsDetailScreen(platformLabel = label, onBack = { nav.popBackStack() })
        }
        composable("permission") {
            PermissionScreen(onDone = { nav.popBackStack() })
        }
    }
}

// ═══════════════════════════════════════════════════════════════════
// SCREEN 1 — DASHBOARD
// ═══════════════════════════════════════════════════════════════════

@Composable
fun DashboardScreen(
    onBlocksClick: () -> Unit,
    onPlatformClick: (String) -> Unit = {},
    vm: ShortsViewModel = koinViewModel()
) {
    val ui by vm.dashboard.collectAsState()
    val serviceOn = rememberShortsServiceEnabled(vm)

    LazyColumn(
        modifier = Modifier.fillMaxSize().background(BG),
        contentPadding = PaddingValues(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        // Header
        item {
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically) {
                Text("Short Video Tracker", color = TXT, fontSize = 22.sp, fontWeight = FontWeight.Bold)
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    Box(Modifier.size(8.dp).clip(CircleShape)
                        .background(if (serviceOn) ACCENT else DANGER))
                    Text(if (serviceOn) "Tracking" else "Off",
                        color = if (serviceOn) ACCENT else DANGER, fontSize = 13.sp)
                }
            }
        }

        // Time card
        item {
            Card(Modifier.fillMaxWidth(),
                colors = CardDefaults.cardColors(containerColor = SURFACE),
                shape = RoundedCornerShape(16.dp)) {
                Column(Modifier.padding(20.dp).fillMaxWidth(),
                    horizontalAlignment = Alignment.CenterHorizontally) {
                    Text("Total screen time", color = TXT2, fontSize = 14.sp)
                    Spacer(Modifier.height(8.dp))
                    val h = ui.totalSecToday / 3600
                    val m = (ui.totalSecToday % 3600) / 60
                    val s = ui.totalSecToday % 60
                    Row(verticalAlignment = Alignment.Bottom) {
                        if (h > 0) {
                            Text("$h", color = TXT, fontSize = 52.sp, fontWeight = FontWeight.ExtraBold, lineHeight = 52.sp)
                            Text("h", color = TXT2, fontSize = 26.sp, modifier = Modifier.padding(bottom = 6.dp, start = 2.dp, end = 8.dp))
                        }
                        Text("$m", color = TXT, fontSize = 52.sp, fontWeight = FontWeight.ExtraBold, lineHeight = 52.sp)
                        Text("m", color = TXT2, fontSize = 26.sp, modifier = Modifier.padding(bottom = 6.dp, start = 2.dp, end = 8.dp))
                        Text("$s", color = TXT, fontSize = 52.sp, fontWeight = FontWeight.ExtraBold, lineHeight = 52.sp)
                        Text("s", color = TXT2, fontSize = 26.sp, modifier = Modifier.padding(bottom = 6.dp, start = 2.dp))
                    }
                    Text("Today", color = TXT2, fontSize = 13.sp)
                }
            }
        }

        // Stats row
        item {
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                StatCard("${ui.sessionCount}", "Sessions", Modifier.weight(1f))
                StatCard("${ui.blockedCount}", "Blocked", Modifier.weight(1f), DANGER)
                StatCard(
                    ui.topPlatform.replace("YouTube Shorts", "YT Shorts")
                        .replace("Instagram Reels", "IG Reels"),
                    "Top app", Modifier.weight(1f)
                )
            }
        }

        // Hourly chart
        item { HourlyChart(ui.hourlyBars) }

        // Weekly bars
        item { WeeklyChart(ui.weekBars) }

        // Platform breakdown
        if (ui.platformMap.isNotEmpty()) {
            item { PlatformBreakdown(ui.platformMap, onPlatformClick) }
        }

        // Blocks quick-access
        item {
            Card(Modifier.fillMaxWidth().clickable(onClick = onBlocksClick),
                colors = CardDefaults.cardColors(containerColor = DANGER_BG),
                shape = RoundedCornerShape(16.dp)) {
                Row(Modifier.padding(16.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                    Text("⛔", fontSize = 22.sp)
                    Column(Modifier.weight(1f)) {
                        Text("Manage Blocks", color = TXT, fontWeight = FontWeight.SemiBold)
                        Text("Schedule blocking windows", color = TXT2, fontSize = 12.sp)
                    }
                    Text("›", color = DANGER, fontSize = 22.sp, fontWeight = FontWeight.Bold)
                }
            }
        }
    }
}

@Composable
private fun StatCard(value: String, label: String, modifier: Modifier, valueColor: Color = TXT) {
    Card(modifier, colors = CardDefaults.cardColors(containerColor = CARD),
        shape = RoundedCornerShape(16.dp)) {
        Column(Modifier.padding(12.dp)) {
            Text(value, color = valueColor, fontSize = 22.sp, fontWeight = FontWeight.Bold)
            Text(label, color = TXT2, fontSize = 11.sp)
        }
    }
}

@Composable
private fun HourlyChart(bars: List<Long>) {
    val max = bars.maxOrNull()?.coerceAtLeast(1L) ?: 1L
    Card(Modifier.fillMaxWidth(), colors = CardDefaults.cardColors(containerColor = SURFACE),
        shape = RoundedCornerShape(16.dp)) {
        Column(Modifier.padding(16.dp)) {
            Text("Hourly breakdown", color = TXT, fontWeight = FontWeight.SemiBold)
            Text("Today", color = TXT2, fontSize = 12.sp)
            Spacer(Modifier.height(14.dp))
            Row(Modifier.fillMaxWidth().height(72.dp),
                horizontalArrangement = Arrangement.spacedBy(2.dp),
                verticalAlignment = Alignment.Bottom) {
                bars.forEach { v ->
                    val frac = (v.toFloat() / max).coerceAtLeast(0.03f)
                    Box(Modifier.weight(1f).fillMaxHeight(frac)
                        .clip(RoundedCornerShape(topStart = 3.dp, topEnd = 3.dp))
                        .background(if (frac > 0.6f) ACCENT else ACCENT_DIM))
                }
            }
            Spacer(Modifier.height(4.dp))
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                listOf("12a", "6a", "12p", "6p", "12a").forEach {
                    Text(it, color = TXT3, fontSize = 10.sp)
                }
            }
        }
    }
}

@Composable
private fun WeeklyChart(bars: List<Long>) {
    val days = listOf("Mon","Tue","Wed","Thu","Fri","Sat","Sun")
    // Map from 7-slot list (0=6daysAgo) to Mon-Sun display order
    // bars[0..6] are oldest-to-today; we just label Mon..Sun positionally for simplicity
    val max = bars.maxOrNull()?.coerceAtLeast(1L) ?: 1L
    Card(Modifier.fillMaxWidth(), colors = CardDefaults.cardColors(containerColor = CARD),
        shape = RoundedCornerShape(16.dp)) {
        Column(Modifier.padding(16.dp)) {
            Text("7-Day Overview", color = TXT, fontWeight = FontWeight.SemiBold)
            Spacer(Modifier.height(12.dp))
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.Bottom) {
                bars.forEachIndexed { i, v ->
                    val frac = (v.toFloat() / max).coerceAtLeast(0.04f)
                    val isToday = i == 6
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        Box(Modifier.width(28.dp).height((60 * frac).dp)
                            .clip(RoundedCornerShape(6.dp))
                            .background(if (isToday) ACCENT else ACCENT_DIM))
                        Spacer(Modifier.height(4.dp))
                        Text(days.getOrElse(i) { "" },
                            color = if (isToday) ACCENT else TXT3, fontSize = 10.sp)
                    }
                }
            }
        }
    }
}

@Composable
private fun PlatformBreakdown(map: Map<String, Long>, onPlatformClick: (String) -> Unit = {}) {
    val total = map.values.sum().coerceAtLeast(1L)
    Card(Modifier.fillMaxWidth(), colors = CardDefaults.cardColors(containerColor = SURFACE),
        shape = RoundedCornerShape(16.dp)) {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            Text("Top apps", color = TXT, fontWeight = FontWeight.SemiBold)
            Text("Tap any app for detailed sessions", color = TXT2, fontSize = 12.sp)
            map.entries.sortedByDescending { it.value }.forEachIndexed { idx, (label, secs) ->
                val frac = secs.toFloat() / total
                val color = platformColor(label)
                val m = secs / 60; val s = secs % 60
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(8.dp))
                        .clickable { onPlatformClick(label) }
                        .padding(vertical = 4.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    Box(Modifier.size(28.dp).clip(CircleShape).background(CARD),
                        contentAlignment = Alignment.Center) {
                        Text("${idx+1}", color = TXT2, fontSize = 11.sp, fontWeight = FontWeight.Bold)
                    }
                    Column(Modifier.weight(1f)) {
                        Text(label, color = TXT, fontWeight = FontWeight.Medium, fontSize = 13.sp)
                        Spacer(Modifier.height(3.dp))
                        LinearProgressIndicator(progress = { frac },
                            modifier = Modifier.fillMaxWidth().height(4.dp).clip(RoundedCornerShape(50)),
                            color = color, trackColor = CARD)
                    }
                    Column(horizontalAlignment = Alignment.End) {
                        Text(if (m > 0) "${m}m ${s}s" else "${s}s", color = color, fontSize = 13.sp, fontWeight = FontWeight.Bold)
                        Text("${(frac*100).toInt()}%", color = TXT2, fontSize = 11.sp)
                    }
                }
                if (idx < map.size - 1) HorizontalDivider(color = DIV, thickness = 0.5.dp)
            }
        }
    }
}

// ═══════════════════════════════════════════════════════════════════
// SCREEN 2 — BLOCKS
// ═══════════════════════════════════════════════════════════════════

@Composable
fun BlocksScreen(
    onBack: () -> Unit,
    onAdd: (pkg: String) -> Unit,
    pauseConfirmDelaySec: Int = 30,
    pauseDialogQuote: String = quoteForIndex(0),
    vm: ShortsViewModel = koinViewModel()
) {
    val schedules by vm.schedules.collectAsState()
    val serviceOn = rememberShortsServiceEnabled(vm)
    val groups = remember(schedules) { mergeScheduleGroups(schedules) }
    val quickTemplates = remember {
        listOf(
            QuickPlanTemplate(
                title = "Work mode",
                subtitle = "Weekdays, 09:00 - 18:00",
                days = listOf(2, 3, 4, 5, 6),
                startMin = 9 * 60,
                endMin = 18 * 60
            ),
            QuickPlanTemplate(
                title = "Evening reset",
                subtitle = "Every day, 20:00 - 23:30",
                days = (1..7).toList(),
                startMin = 20 * 60,
                endMin = 23 * 60 + 30
            ),
            QuickPlanTemplate(
                title = "Always block",
                subtitle = "All day, every day",
                days = (1..7).toList(),
                startMin = 0,
                endMin = 0,
                alwaysBlock = true
            )
        )
    }

    val activeWindows = groups.sumOf { group ->
        group.mergedWindows.count { it.isEnabled && remainingPauseMinutes(it.pausedUntilMs) == 0L }
    }

    Box(Modifier.fillMaxSize().background(BG)) {
        LazyColumn(
            Modifier.fillMaxSize(),
            contentPadding = PaddingValues(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            item {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    IconButton(onClick = onBack) {
                        Icon(Icons.Default.ArrowBack, null, tint = TXT)
                    }
                    Text("Protection Plans", color = TXT, fontSize = 22.sp, fontWeight = FontWeight.Bold)
                }
            }

            item {
                Card(
                    Modifier.fillMaxWidth(),
                    colors = CardDefaults.cardColors(containerColor = SURFACE),
                    shape = RoundedCornerShape(16.dp)
                ) {
                    Column(
                        Modifier.padding(16.dp),
                        verticalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(10.dp)
                        ) {
                            Box(
                                Modifier
                                    .size(34.dp)
                                    .clip(CircleShape)
                                    .background(if (serviceOn) ACCENT_DIM else DANGER_BG),
                                contentAlignment = Alignment.Center
                            ) {
                                Icon(
                                    imageVector = Icons.Default.Shield,
                                    contentDescription = null,
                                    tint = if (serviceOn) ACCENT else DANGER
                                )
                            }
                            Column(Modifier.weight(1f)) {
                                Text(
                                    if (serviceOn) "Blocking is active" else "Blocking is paused",
                                    color = TXT,
                                    fontWeight = FontWeight.SemiBold
                                )
                                Text(
                                    if (serviceOn) "$activeWindows active windows across ${groups.size} platform groups"
                                    else "Enable accessibility to start blocking shorts and reels",
                                    color = TXT2,
                                    fontSize = 12.sp
                                )
                            }
                        }
                        if (!serviceOn) {
                            Button(
                                onClick = { vm.openAccessibilitySettings() },
                                modifier = Modifier.fillMaxWidth(),
                                colors = ButtonDefaults.buttonColors(containerColor = DANGER)
                            ) {
                                Text("Enable Accessibility", color = TXT, fontWeight = FontWeight.SemiBold)
                            }
                        }
                    }
                }
            }

            item {
                Text("Quick plans", color = TXT, fontWeight = FontWeight.SemiBold)
                Text(
                    "Inspired by focus app presets: one tap to create a baseline rule.",
                    color = TXT2,
                    fontSize = 12.sp
                )
                Spacer(Modifier.height(8.dp))
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    quickTemplates.forEach { template ->
                        Card(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clickable {
                                    vm.addSchedule(
                                        packageName = "",
                                        days = template.days,
                                        startMin = template.startMin,
                                        endMin = template.endMin,
                                        ruleName = template.title,
                                        alwaysBlock = template.alwaysBlock
                                    )
                                },
                            colors = CardDefaults.cardColors(containerColor = CARD),
                            shape = RoundedCornerShape(12.dp)
                        ) {
                            Row(
                                Modifier
                                    .fillMaxWidth()
                                    .padding(horizontal = 14.dp, vertical = 12.dp),
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(10.dp)
                            ) {
                                Icon(
                                    imageVector = if (template.alwaysBlock) Icons.Default.Bolt else Icons.Default.Timer,
                                    contentDescription = null,
                                    tint = if (template.alwaysBlock) DANGER else ACCENT
                                )
                                Column(Modifier.weight(1f)) {
                                    Text(template.title, color = TXT, fontWeight = FontWeight.Medium)
                                    Text(template.subtitle, color = TXT2, fontSize = 12.sp)
                                }
                                Text("Add", color = ACCENT, fontWeight = FontWeight.SemiBold, fontSize = 12.sp)
                            }
                        }
                    }
                }
            }

            item {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Text("Your rules", color = TXT, fontWeight = FontWeight.SemiBold)
                    Text("${groups.size} groups", color = TXT2, fontSize = 12.sp)
                }
            }

            if (groups.isEmpty()) {
                item {
                    Card(Modifier.fillMaxWidth(),
                        colors = CardDefaults.cardColors(containerColor = CARD),
                        shape = RoundedCornerShape(16.dp)) {
                        Column(Modifier.padding(32.dp).fillMaxWidth(),
                            horizontalAlignment = Alignment.CenterHorizontally,
                            verticalArrangement = Arrangement.spacedBy(8.dp)) {
                            Icon(Icons.Default.AutoAwesome, contentDescription = null, tint = ACCENT)
                            Text("No rules yet", color = TXT, fontWeight = FontWeight.SemiBold)
                            Text("Add your first schedule or use a quick plan above.", color = TXT2, fontSize = 13.sp)
                        }
                    }
                }
            } else {
                items(groups, key = { it.packageName }) { group ->
                    ScheduleCard(
                        group = group,
                        onToggleMany = vm::toggleSchedules,
                        onDeleteMany = vm::deleteSchedules,
                        onPauseMany = vm::pauseSchedules,
                        onResumeMany = vm::resumeSchedules,
                        pauseConfirmDelaySec = pauseConfirmDelaySec,
                        pauseDialogQuote = pauseDialogQuote
                    )
                }
            }
        }

        FloatingActionButton(
            onClick = { onAdd("") },
            modifier = Modifier.align(Alignment.BottomEnd)
                .padding(end = 16.dp, bottom = 24.dp).navigationBarsPadding(),
            containerColor = ACCENT, contentColor = BG
        ) { Icon(Icons.Default.Add, null) }
    }
}

@Composable
private fun ScheduleCard(
    group: ScheduleGroupUi,
    onToggleMany: (List<Long>, Boolean) -> Unit,
    onDeleteMany: (List<Long>) -> Unit,
    onPauseMany: (List<Long>, Int) -> Unit,
    onResumeMany: (List<Long>) -> Unit,
    pauseConfirmDelaySec: Int,
    pauseDialogQuote: String
) {
    var pendingDeleteIds by remember { mutableStateOf<List<Long>?>(null) }
    var pendingPauseIds by remember { mutableStateOf<List<Long>?>(null) }
    val platformColor = platformColor(group.platformLabel)
    val pausedMinutes = group.schedules.maxOfOrNull { remainingPauseMinutes(it.pausedUntilMs) } ?: 0L
    val isPaused = pausedMinutes > 0
    val activeCount = group.mergedWindows.count { it.isEnabled && remainingPauseMinutes(it.pausedUntilMs) == 0L }
    val allIds = remember(group.schedules) { group.schedules.map(ScheduleUi::id) }

    Card(
        Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(containerColor = if (activeCount > 0) SURFACE else CARD),
        shape = RoundedCornerShape(14.dp)
    ) {
        Column(Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Box(Modifier.size(8.dp).clip(CircleShape).background(platformColor))
                Column(Modifier.weight(1f)) {
                    Text(group.platformLabel, color = TXT, fontWeight = FontWeight.SemiBold, fontSize = 14.sp)
                    Text("${group.mergedWindows.size} combined windows", color = TXT2, fontSize = 12.sp)
                }
                Box(
                    Modifier
                        .clip(RoundedCornerShape(8.dp))
                        .background(if (isPaused) DANGER_BG else ACCENT_DIM)
                        .padding(horizontal = 8.dp, vertical = 4.dp)
                ) {
                    Text(
                        text = if (isPaused) "Paused" else "Active",
                        color = if (isPaused) DANGER else ACCENT,
                        fontSize = 11.sp,
                        fontWeight = FontWeight.SemiBold
                    )
                }
            }
            if (isPaused) {
                Text("Paused for $pausedMinutes more min", color = TXT2, fontSize = 12.sp)
            }
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                if (isPaused) {
                    FilledTonalButton(onClick = { onResumeMany(allIds) }) { Text("Resume", color = ACCENT) }
                } else {
                    OutlinedButton(onClick = { pendingPauseIds = allIds }) {
                        Text("Pause", color = TXT)
                    }
                }
                OutlinedButton(onClick = { onToggleMany(allIds, activeCount == 0) }) {
                    Text(if (activeCount == 0) "Enable all" else "Disable all", color = TXT)
                }
            }
            group.mergedWindows.forEachIndexed { index, window ->
                HorizontalDivider(color = if (index == 0) Color.Transparent else DIV, thickness = if (index == 0) 0.dp else 0.5.dp)
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                        Text(
                            text = if (window.alwaysBlock) "Always blocked"
                            else "${dayShort(window.dayOfWeek ?: 1)}  ${minToStr(window.startMin)} - ${minToStr(window.endMin)}",
                            color = if (window.isEnabled) TXT else TXT3,
                            fontWeight = FontWeight.SemiBold,
                            fontSize = 14.sp
                        )
                        if (window.ruleNames.isNotEmpty()) {
                            Text(window.ruleNames.joinToString(" • "), color = TXT2, fontSize = 11.sp)
                        }
                        if (remainingPauseMinutes(window.pausedUntilMs) > 0L) {
                            Text("Paused ${remainingPauseMinutes(window.pausedUntilMs)} min", color = TXT2, fontSize = 11.sp)
                        }
                    }
                    Switch(
                        checked = window.isEnabled,
                        onCheckedChange = { onToggleMany(window.sourceIds, it) },
                        colors = SwitchDefaults.colors(checkedThumbColor = BG, checkedTrackColor = ACCENT)
                    )
                    val windowPaused = remainingPauseMinutes(window.pausedUntilMs) > 0L
                    TextButton(
                        onClick = {
                            if (windowPaused) onResumeMany(window.sourceIds)
                            else pendingPauseIds = window.sourceIds
                        }
                    ) {
                        Text(if (windowPaused) "Resume" else "Pause", color = TXT2, fontSize = 12.sp)
                    }
                    IconButton(onClick = { pendingDeleteIds = window.sourceIds }) {
                        Icon(Icons.Default.Delete, null, tint = DANGER)
                    }
                }
            }
        }
    }

    if (pendingDeleteIds != null) {
        AlertDialog(onDismissRequest = { pendingDeleteIds = null },
            title = { Text("Delete rule?", color = TXT) },
            text = { Text("This blocking window will be removed.", color = TXT2) },
            confirmButton = {
                TextButton(onClick = {
                    onDeleteMany(pendingDeleteIds.orEmpty())
                    pendingDeleteIds = null
                }) { Text("Delete", color = DANGER) }
            },
            dismissButton = {
                TextButton(onClick = { pendingDeleteIds = null }) { Text("Cancel", color = TXT2) }
            },
            containerColor = SURFACE)
    }

    if (pendingPauseIds != null) {
        PauseRuleDialog(
            onDismiss = { pendingPauseIds = null },
            delaySec = pauseConfirmDelaySec,
            quote = pauseDialogQuote,
            onConfirm = {
                onPauseMany(pendingPauseIds.orEmpty(), it)
                pendingPauseIds = null
            }
        )
    }
}

// ═══════════════════════════════════════════════════════════════════
// SCREEN 3 — ADD SCHEDULE
// ═══════════════════════════════════════════════════════════════════

@Composable
fun AddScheduleScreen(
    preselectedPkg: String,
    onBack: () -> Unit,
    vm: ShortsViewModel = koinViewModel()
) {
    var selectedPkg by remember { mutableStateOf(preselectedPkg) }
    var selectedDays by remember { mutableStateOf(setOf(2, 3, 4, 5, 6)) }
    var startWindowMin by remember { mutableStateOf(9 * 60) }
    var endWindowMin by remember { mutableStateOf(17 * 60) }
    var ruleName by remember { mutableStateOf("") }
    var alwaysBlock by remember { mutableStateOf(false) }

    val hasValidWindow = startWindowMin < endWindowMin
    val isValid = if (alwaysBlock) true else selectedDays.isNotEmpty() && hasValidWindow
    val selectedLabel = ShortsPlatforms.forPackage(selectedPkg)?.label ?: if (selectedPkg.isBlank()) "All Platforms" else selectedPkg

    Box(Modifier.fillMaxSize().background(BG)) {
        LazyColumn(
            Modifier.fillMaxSize(),
            contentPadding = PaddingValues(start = 16.dp, end = 16.dp, top = 8.dp, bottom = 120.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            item {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    IconButton(onClick = onBack) { Icon(Icons.Default.ArrowBack, null, tint = TXT) }
                    Text("Create Protection Rule", color = TXT, fontSize = 22.sp, fontWeight = FontWeight.Bold)
                }
            }

            item {
                SectionLabel("1. Choose platform")
                Card(Modifier.fillMaxWidth(),
                    colors = CardDefaults.cardColors(containerColor = SURFACE),
                    shape = RoundedCornerShape(12.dp)) {
                    Column(Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                        PlatformRow("", "All Platforms", selectedPkg) { selectedPkg = it }
                        HorizontalDivider(color = DIV, thickness = 0.5.dp)
                        ShortsPlatforms.ALL.distinctBy { it.label }.forEach { p ->
                            PlatformRow(p.packageName, p.label, selectedPkg) {
                                selectedPkg = it
                            }
                        }
                    }
                }
            }

            item {
                SectionLabel("2. Block mode")
                Card(Modifier.fillMaxWidth(),
                    colors = CardDefaults.cardColors(containerColor = SURFACE),
                    shape = RoundedCornerShape(12.dp)) {
                    Row(
                        Modifier.fillMaxWidth().padding(16.dp),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Column(Modifier.weight(1f)) {
                            Text("Always block", color = TXT, fontWeight = FontWeight.SemiBold)
                            Text(
                                "Blocks all day on all selected days.",
                                color = TXT2,
                                fontSize = 12.sp
                            )
                        }
                        Switch(
                            checked = alwaysBlock,
                            onCheckedChange = { alwaysBlock = it },
                            enabled = selectedPkg.isNotBlank(),
                            colors = SwitchDefaults.colors(checkedThumbColor = BG, checkedTrackColor = ACCENT)
                        )
                    }
                }
            }

            if (!alwaysBlock) item {
                SectionLabel("3. Time window")
                Card(Modifier.fillMaxWidth(),
                    colors = CardDefaults.cardColors(containerColor = SURFACE),
                    shape = RoundedCornerShape(12.dp)) {
                    Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            PresetChip("Morning") {
                                startWindowMin = 6 * 60
                                endWindowMin = 12 * 60
                            }
                            PresetChip("Workday") {
                                startWindowMin = 9 * 60
                                endWindowMin = 18 * 60
                            }
                            PresetChip("Night") {
                                startWindowMin = 20 * 60
                                endWindowMin = 23 * 60 + 30
                            }
                        }

                        Text("Start: ${minToStr(startWindowMin)}", color = TXT, fontSize = 13.sp)
                        Slider(
                            value = startWindowMin.toFloat(),
                            onValueChange = { value ->
                                startWindowMin = ((value.toInt() / 5) * 5).coerceIn(0, endWindowMin - 5)
                            },
                            valueRange = 0f..1435f,
                            colors = SliderDefaults.colors(activeTrackColor = ACCENT, thumbColor = ACCENT)
                        )

                        Text("End: ${minToStr(endWindowMin)}", color = TXT, fontSize = 13.sp)
                        Slider(
                            value = endWindowMin.toFloat(),
                            onValueChange = { value ->
                                endWindowMin = ((value.toInt() / 5) * 5).coerceIn(startWindowMin + 5, 1440)
                            },
                            valueRange = 5f..1440f,
                            colors = SliderDefaults.colors(activeTrackColor = ACCENT, thumbColor = ACCENT)
                        )

                        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            Icon(Icons.Default.CalendarMonth, contentDescription = null, tint = TXT2)
                            Text("${minToStr(startWindowMin)} - ${minToStr(endWindowMin)}", color = TXT2, fontSize = 12.sp)
                        }
                        if (!hasValidWindow && selectedDays.isNotEmpty()) {
                            Spacer(Modifier.height(4.dp))
                            Text("End must be after start", color = DANGER, fontSize = 12.sp)
                        }
                    }
                }
            }

            if (!alwaysBlock) item {
                SectionLabel("4. Days")
                Card(Modifier.fillMaxWidth(),
                    colors = CardDefaults.cardColors(containerColor = SURFACE),
                    shape = RoundedCornerShape(12.dp)) {
                    Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                        val dayLabels = listOf("M", "T", "W", "T", "F", "S", "S")
                        val dayValues = listOf(2, 3, 4, 5, 6, 7, 1)
                        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                            dayLabels.zip(dayValues).forEach { (lbl, day) ->
                                val on = day in selectedDays
                                Box(Modifier.size(38.dp).clip(CircleShape)
                                    .background(if (on) ACCENT else CARD)
                                    .border(1.dp, if (on) ACCENT else DIV, CircleShape)
                                    .clickable {
                                        selectedDays = if (on) selectedDays - day else selectedDays + day
                                    },
                                    contentAlignment = Alignment.Center) {
                                    Text(lbl, color = if (on) BG else TXT2, fontWeight = FontWeight.Bold, fontSize = 13.sp)
                                }
                            }
                        }
                        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            PresetChip("Weekdays") { selectedDays = setOf(2, 3, 4, 5, 6) }
                            PresetChip("Weekend") { selectedDays = setOf(1, 7) }
                            PresetChip("All") { selectedDays = (1..7).toSet() }
                        }
                    }
                }
            }

            item {
                SectionLabel("5. Rule label (optional)")
                Card(Modifier.fillMaxWidth(),
                    colors = CardDefaults.cardColors(containerColor = SURFACE),
                    shape = RoundedCornerShape(12.dp)) {
                    OutlinedTextField(
                        value = ruleName,
                        onValueChange = { ruleName = it },
                        placeholder = { Text("e.g. Work hours", color = TXT3) },
                        modifier = Modifier.fillMaxWidth().padding(4.dp),
                        singleLine = true,
                        colors = OutlinedTextFieldDefaults.colors(
                            focusedBorderColor = ACCENT, unfocusedBorderColor = DIV,
                            focusedTextColor = TXT, unfocusedTextColor = TXT, cursorColor = ACCENT
                        )
                    )
                }
            }

            item {
                SectionLabel("Preview")
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    colors = CardDefaults.cardColors(containerColor = CARD),
                    shape = RoundedCornerShape(12.dp)
                ) {
                    Row(
                        Modifier.padding(14.dp),
                        horizontalArrangement = Arrangement.spacedBy(10.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Icon(Icons.Default.Shield, contentDescription = null, tint = ACCENT)
                        Column {
                            Text(selectedLabel, color = TXT, fontWeight = FontWeight.SemiBold)
                            Text(
                                if (alwaysBlock) "Always blocked"
                                else "${summarizeDays(selectedDays.toList())} • ${minToStr(startWindowMin)}-${minToStr(endWindowMin)}",
                                color = TXT2,
                                fontSize = 12.sp
                            )
                        }
                    }
                }
            }
        }

        Button(
            onClick = {
                vm.addSchedule(
                    packageName = selectedPkg,
                    days = if (alwaysBlock) (1..7).toList() else selectedDays.toList(),
                    startMin = if (alwaysBlock) 0 else startWindowMin,
                    endMin = if (alwaysBlock) 0 else endWindowMin,
                    ruleName = ruleName.trim(),
                    alwaysBlock = alwaysBlock
                )
                onBack()
            },
            enabled = isValid,
            modifier = Modifier.align(Alignment.BottomCenter).fillMaxWidth()
                .padding(horizontal = 16.dp, vertical = 24.dp).navigationBarsPadding(),
            colors = ButtonDefaults.buttonColors(containerColor = ACCENT, disabledContainerColor = ACCENT_DIM),
            shape = RoundedCornerShape(12.dp)
        ) {
            Text("Save protection rule", color = BG, fontWeight = FontWeight.Bold, fontSize = 16.sp,
                modifier = Modifier.padding(vertical = 6.dp))
        }
    }
}

@Composable
private fun SectionLabel(text: String) {
    Text(text, color = TXT2, fontSize = 13.sp,
        fontWeight = FontWeight.Medium, modifier = Modifier.padding(bottom = 6.dp))
}

@Composable
private fun PlatformRow(pkg: String, label: String, selected: String, onSelect: (String) -> Unit) {
    Row(
        Modifier.fillMaxWidth().clip(RoundedCornerShape(8.dp))
            .background(if (pkg == selected) ACCENT_DIM.copy(alpha = 0.4f) else Color.Transparent)
            .clickable { onSelect(pkg) }.padding(10.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        Box(
            Modifier
                .size(22.dp)
                .clip(CircleShape)
                .background(platformColor(label).copy(alpha = 0.25f)),
            contentAlignment = Alignment.Center
        ) {
            Icon(Icons.Default.Bolt, contentDescription = null, tint = platformColor(label), modifier = Modifier.size(14.dp))
        }
        Text(label, color = TXT, modifier = Modifier.weight(1f))
        if (pkg == selected) Text("✓", color = ACCENT, fontWeight = FontWeight.Bold)
    }
}

@Composable
private fun PresetChip(label: String, onClick: () -> Unit) {
    Box(Modifier.clip(RoundedCornerShape(50.dp)).background(CARD)
        .clickable(onClick = onClick).padding(horizontal = 12.dp, vertical = 6.dp)) {
        Text(label, color = ACCENT, fontSize = 12.sp, fontWeight = FontWeight.Medium)
    }
}

// ═══════════════════════════════════════════════════════════════════
// SCREEN 4 — SESSION DETAIL (per platform, today's sessions)
// ═══════════════════════════════════════════════════════════════════

@Composable
fun SessionsDetailScreen(
    platformLabel: String,
    onBack: () -> Unit,
    vm: ShortsViewModel = koinViewModel()
) {
    val sessions by vm.sessionsForPlatform(platformLabel).collectAsState(initial = emptyList())
    val color = platformColor(platformLabel)
    val fmt = remember { SimpleDateFormat("hh:mm a", Locale.getDefault()) }
    val nowMs by produceState(initialValue = System.currentTimeMillis()) {
        while (true) {
            delay(1_000)
            value = System.currentTimeMillis()
        }
    }

    val totalSec = sessions.sumOf { it.actualDurationSec(nowMs) }
    val avgSec = if (sessions.isNotEmpty()) totalSec / sessions.size else 0L
    val longestSec = sessions.maxOfOrNull { it.actualDurationSec(nowMs) } ?: 0L
    val blockedCount = sessions.count { it.wasBlocked }

    Box(Modifier.fillMaxSize().background(BG)) {
        LazyColumn(
            Modifier.fillMaxSize(),
            contentPadding = PaddingValues(16.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            // Header
            item {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    IconButton(onClick = onBack) {
                        Icon(Icons.Default.ArrowBack, null, tint = TXT)
                    }
                    Column {
                        Text(platformLabel, color = TXT, fontSize = 20.sp, fontWeight = FontWeight.Bold)
                        Text("Today's sessions", color = TXT2, fontSize = 12.sp)
                    }
                }
            }

            // Stats strip
            item {
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    MiniStatCard("Total", fmtDuration(totalSec), color, Modifier.weight(1f))
                    MiniStatCard("Avg", fmtDuration(avgSec), TXT2, Modifier.weight(1f))
                    MiniStatCard("Longest", fmtDuration(longestSec), TXT2, Modifier.weight(1f))
                    MiniStatCard("Blocked", "$blockedCount", if (blockedCount > 0) DANGER else TXT2, Modifier.weight(1f))
                }
            }

            // Session list
            if (sessions.isEmpty()) {
                item {
                    Card(Modifier.fillMaxWidth(),
                        colors = CardDefaults.cardColors(containerColor = CARD),
                        shape = RoundedCornerShape(14.dp)) {
                        Column(Modifier.padding(32.dp).fillMaxWidth(),
                            horizontalAlignment = Alignment.CenterHorizontally) {
                            Text("🎉", fontSize = 32.sp)
                            Spacer(Modifier.height(8.dp))
                            Text("No sessions today", color = TXT, fontWeight = FontWeight.SemiBold)
                            Text("Stay focused!", color = TXT2, fontSize = 13.sp)
                        }
                    }
                }
            } else {
                item {
                    Text("${sessions.size} sessions", color = TXT2, fontSize = 13.sp,
                        modifier = Modifier.padding(top = 4.dp))
                }
                items(sessions, key = { it.id }) { s ->
                    SessionRow(s, color, fmt, nowMs)
                }
            }
        }
    }
}

@Composable
private fun MiniStatCard(label: String, value: String, valueColor: Color, modifier: Modifier) {
    Card(modifier, colors = CardDefaults.cardColors(containerColor = SURFACE),
        shape = RoundedCornerShape(12.dp)) {
        Column(Modifier.padding(10.dp)) {
            Text(value, color = valueColor, fontSize = 15.sp, fontWeight = FontWeight.Bold)
            Text(label, color = TXT3, fontSize = 10.sp)
        }
    }
}

@Composable
private fun SessionRow(s: SessionEntity, platformColor: Color, fmt: SimpleDateFormat, nowMs: Long) {
    val startStr = fmt.format(Date(s.startMs))
    val endStr = if (s.endMs > 0) fmt.format(Date(s.endMs)) else "ongoing"
    val isBlocked = s.wasBlocked
    val durationSec = s.actualDurationSec(nowMs)

    Card(Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(
            containerColor = if (isBlocked) DANGER_BG else SURFACE),
        shape = RoundedCornerShape(12.dp)) {
        Row(Modifier.padding(14.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            // Timeline dot + line
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                Box(Modifier.size(10.dp).clip(CircleShape)
                    .background(if (isBlocked) DANGER else platformColor))
            }
            Column(Modifier.weight(1f)) {
                Text("$startStr  →  $endStr",
                    color = if (isBlocked) DANGER else TXT,
                    fontSize = 14.sp, fontWeight = FontWeight.Medium)
                Spacer(Modifier.height(2.dp))
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalAlignment = Alignment.CenterVertically) {
                    Text(fmtDuration(durationSec), color = TXT2, fontSize = 12.sp)
                    if (isBlocked) {
                        Box(Modifier.clip(RoundedCornerShape(4.dp))
                            .background(DANGER.copy(alpha = 0.2f))
                            .padding(horizontal = 6.dp, vertical = 2.dp)) {
                            Text("BLOCKED", color = DANGER, fontSize = 10.sp, fontWeight = FontWeight.Bold)
                        }
                    }
                }
            }
            // Duration badge
            Box(Modifier.clip(RoundedCornerShape(8.dp))
                .background(if (isBlocked) DANGER.copy(alpha = 0.15f) else CARD)
                .padding(horizontal = 10.dp, vertical = 6.dp)) {
                Text(fmtDuration(durationSec),
                    color = if (isBlocked) DANGER else platformColor,
                    fontSize = 13.sp, fontWeight = FontWeight.Bold)
            }
        }
    }
}

private fun fmtDuration(sec: Long): String = when {
    sec >= 3600 -> "${sec / 3600}h ${(sec % 3600) / 60}m"
    sec >= 60   -> "${sec / 60}m ${sec % 60}s"
    else        -> "${sec}s"
}

// ═══════════════════════════════════════════════════════════════════
// SCREEN 5 — PERMISSION / ONBOARDING
// Shown when accessibility service is not enabled.
// Can be navigated to from anywhere or used as a gate screen.
// ═══════════════════════════════════════════════════════════════════

@Composable
fun PermissionScreen(
    onDone: () -> Unit,
    vm: ShortsViewModel = koinViewModel()
) {
    var granted by remember { mutableStateOf(vm.isShortsAccessibilityEnabled()) }
    LaunchedEffect(Unit) {
        while (!granted) {
            delay(1000)
            granted = vm.isShortsAccessibilityEnabled()
        }
    }
    LaunchedEffect(granted) {
        if (granted) onDone()
    }

    Box(Modifier.fillMaxSize().background(BG), contentAlignment = Alignment.Center) {
        Column(
            Modifier.fillMaxWidth().padding(32.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(18.dp)
        ) {
            Box(Modifier.size(100.dp).clip(CircleShape)
                .background(SURFACE),
                contentAlignment = Alignment.Center) {
                Icon(Icons.Default.Shield, contentDescription = null, tint = ACCENT, modifier = Modifier.size(42.dp))
            }

            Column(horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text("Enable blocking permission",
                    color = TXT, fontSize = 22.sp, fontWeight = FontWeight.Bold)
                Text(
                    "Accessibility is required to detect short-form feeds and show the blocker instantly.",
                    color = TXT2, fontSize = 14.sp,
                    textAlign = TextAlign.Center,
                    lineHeight = 22.sp
                )
            }

            Card(Modifier.fillMaxWidth(),
                colors = CardDefaults.cardColors(containerColor = SURFACE),
                shape = RoundedCornerShape(16.dp)) {
                Column(Modifier.padding(20.dp), verticalArrangement = Arrangement.spacedBy(14.dp)) {
                    PermissionStep("1", "Open Accessibility Settings", ACCENT)
                    PermissionStep("2", "Select FocusGuard Shorts Blocker", TXT2)
                    PermissionStep("3", "Turn it ON and return", TXT2)
                }
            }

            Card(
                modifier = Modifier.fillMaxWidth(),
                colors = CardDefaults.cardColors(containerColor = CARD),
                shape = RoundedCornerShape(16.dp)
            ) {
                Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text("Science snapshot", color = TXT, fontWeight = FontWeight.SemiBold)
                    onboardingResearchInsights.take(2).forEachIndexed { index, insight ->
                        Text("${index + 1}. ${insight.headline}", color = TXT, fontSize = 13.sp, fontWeight = FontWeight.Medium)
                        Text(insight.detail, color = TXT2, fontSize = 12.sp, lineHeight = 17.sp)
                    }
                }
            }

            Button(
                onClick = { vm.openAccessibilitySettings() },
                modifier = Modifier.fillMaxWidth(),
                colors = ButtonDefaults.buttonColors(containerColor = ACCENT),
                shape = RoundedCornerShape(12.dp)
            ) {
                Text("Open Accessibility Settings",
                    color = BG, fontWeight = FontWeight.Bold, fontSize = 15.sp,
                    modifier = Modifier.padding(vertical = 6.dp))
            }

            TextButton(onClick = {
                granted = vm.isShortsAccessibilityEnabled()
                if (granted) onDone()
            }) {
                Text("Check again", color = TXT3, fontSize = 13.sp)
            }

            Row(verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                Box(Modifier.size(8.dp).clip(CircleShape)
                    .background(if (granted) ACCENT else DANGER))
                Text(
                    if (granted) "Service active" else "Waiting for permission",
                    color = if (granted) ACCENT else TXT3,
                    fontSize = 12.sp
                )
            }
        }
    }
}

@Composable
private fun PauseRuleDialog(
    onDismiss: () -> Unit,
    delaySec: Int,
    quote: String,
    onConfirm: (Int) -> Unit
) {
    var sliderValue by remember { mutableFloatStateOf(30f) }
    val minutes = (sliderValue / 5f).roundToInt() * 5
    var countdownSec by remember(delaySec) { mutableIntStateOf(delaySec.coerceIn(0, 180)) }

    LaunchedEffect(delaySec) {
        countdownSec = delaySec.coerceIn(0, 180)
        while (countdownSec > 0) {
            delay(1_000)
            countdownSec -= 1
        }
    }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Pause blocking", color = TXT) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                Text(
                    if (countdownSec > 0) "Wait ${countdownSec}s to confirm pause."
                    else "Pause this restriction for $minutes minutes.",
                    color = TXT2
                )
                Text("\"$quote\"", color = TXT3, fontSize = 12.sp)
                Slider(
                    value = sliderValue,
                    onValueChange = { sliderValue = it },
                    valueRange = 5f..240f,
                    steps = 46
                )
            }
        },
        confirmButton = {
            TextButton(
                onClick = { onConfirm(minutes) },
                enabled = countdownSec == 0
            ) {
                Text(if (countdownSec == 0) "Pause" else "Pause (${countdownSec}s)", color = ACCENT)
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text("Cancel", color = TXT2) }
        },
        containerColor = SURFACE
    )
}

@Composable
private fun PermissionStep(num: String, text: String, numColor: Color) {
    Row(horizontalArrangement = Arrangement.spacedBy(14.dp),
        verticalAlignment = Alignment.CenterVertically) {
        Box(Modifier.size(28.dp).clip(CircleShape).background(CARD),
            contentAlignment = Alignment.Center) {
            Text(num, color = numColor, fontWeight = FontWeight.Bold, fontSize = 13.sp)
        }
        Text(text, color = TXT, fontSize = 14.sp, modifier = Modifier.weight(1f))
    }
}

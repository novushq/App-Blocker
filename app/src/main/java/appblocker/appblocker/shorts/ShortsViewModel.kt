package appblocker.appblocker.shorts

import android.app.Application
import android.content.ComponentName
import android.content.Intent
import android.provider.Settings
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch
import java.util.Calendar

// ─────────────────────────────────────────────────────────────────
// KOIN MODULE — wire up DB + ViewModel
// In your Application.onCreate():  startKoin { modules(shortsModule) }
// OR just call shortsKoinModule() from your existing Koin setup
// ─────────────────────────────────────────────────────────────────


// ─────────────────────────────────────────────────────────────────
// UI DATA MODELS — simple, flat, no domain layer
// ─────────────────────────────────────────────────────────────────

data class DashboardUiState(
    val totalSecToday: Long = 0L,
    val sessionCount: Int = 0,
    val blockedCount: Int = 0,
    val topPlatform: String = "—",
    val platformMap: Map<String, Long> = emptyMap(),   // label → seconds
    val hourlyBars: List<Long> = List(24) { 0L },      // index = hour 0..23
    val weekBars: List<Long> = List(7) { 0L }          // index 0 = 6 days ago, 6 = today
)

data class ScheduleUi(
    val id: Long,
    val packageName: String,
    val platformLabel: String,
    val ruleName: String,
    val days: List<Int>,       // Calendar.DAY_OF_WEEK list
    val startMin: Int,
    val endMin: Int,
    val enabled: Boolean,
    val alwaysBlock: Boolean,
    val pausedUntilMs: Long,
    val mergedWindows: List<Interval> // after merge-intervals, for today
)

// ─────────────────────────────────────────────────────────────────
// VIEWMODEL
// ─────────────────────────────────────────────────────────────────

class ShortsViewModel(
    app: Application,
    private val sessionDao: SessionDao,
    private val scheduleDao: ScheduleDao
) : AndroidViewModel(app) {

    // Ticker keeps live session totals and "today" boundaries accurate.
    private val ticker = flow {
        while (true) {
            emit(Unit)
            delay(1_000)
        }
    }.shareIn(viewModelScope, SharingStarted.WhileSubscribed(), replay = 1)

    // ── Dashboard ─────────────────────────────────────────────────

    val dashboard: StateFlow<DashboardUiState> = ticker.flatMapLatest {
        val (from, to) = todayRange()
        val sevenDaysAgo = System.currentTimeMillis() - 7 * 86_400_000L
        val nowMs = System.currentTimeMillis()
        combine(
            sessionDao.platformTotals(from, to, nowMs),
            sessionDao.hourly(from, to, nowMs),
            sessionDao.dailyTotals(sevenDaysAgo, nowMs),
            sessionDao.blockedCount(from, to),
            sessionDao.sessionsInRange(from, to)
        ) { totals, hourly, daily, blocked, sessions ->
            val platformMap = totals.associate { it.label to it.total }
            val hourBars = LongArray(24).apply {
                hourly.forEach { this[it.hour.coerceIn(0, 23)] = it.secs }
            }.toList()
            val today = System.currentTimeMillis() / 86_400_000L
            val dayMap = daily.associate { it.day to it.secs }
            val weekBars = (6 downTo 0).map { daysAgo -> dayMap[today - daysAgo] ?: 0L }
            DashboardUiState(
                totalSecToday = platformMap.values.sum(),
                sessionCount = sessions.size,
                blockedCount = blocked,
                topPlatform = platformMap.maxByOrNull { it.value }?.key ?: "—",
                platformMap = platformMap,
                hourlyBars = hourBars,
                weekBars = weekBars
            )
        }
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), DashboardUiState())

    // ── Schedules ─────────────────────────────────────────────────

    val schedules: StateFlow<List<ScheduleUi>> = scheduleDao.all()
        .map { list ->
            val today = Calendar.getInstance().get(Calendar.DAY_OF_WEEK)
            list.map { e ->
                val days = e.daysOfWeek.split(",").mapNotNull { it.trim().toIntOrNull() }
                val mergedToday = if (today in days)
                    mergeIntervals(listOf(e.startMin to e.endMin))
                else emptyList()
                ScheduleUi(
                    id = e.id,
                    packageName = e.packageName,
                    platformLabel = if (e.packageName.isEmpty()) "All Platforms"
                                    else ShortsPlatforms.forPackage(e.packageName)?.label ?: e.packageName,
                    ruleName = e.ruleName,
                    days = days,
                    startMin = e.startMin,
                    endMin = e.endMin,
                    enabled = e.enabled,
                    alwaysBlock = e.alwaysBlock,
                    pausedUntilMs = e.pausedUntilMs,
                    mergedWindows = mergedToday
                )
            }
        }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    // ── Session detail (per platform) ────────────────────────────

    fun sessionsForPlatform(label: String): Flow<List<SessionEntity>> {
        val (from, to) = todayRange()
        return sessionDao.sessionsForPlatform(label, from, to)
    }

    // All-time streak for a platform: consecutive days with any usage
    suspend fun allTimeTotalSec(label: String): Long {
        // reuse 7-day flow but query directly for all-time
        val (_, _) = todayRange() // unused; just for illustration
        return 0L // placeholder — implement if needed
    }

    

    fun addSchedule(
        packageName: String,
        days: List<Int>,
        startMin: Int,
        endMin: Int,
        ruleName: String = "",
        alwaysBlock: Boolean = false
    ) = viewModelScope.launch(Dispatchers.IO) {
        days.distinct().sorted().forEach { day ->
            mergeAndInsertScheduleDay(
                packageName = packageName,
                day = day,
                startMin = startMin,
                endMin = endMin,
                ruleName = ruleName,
                alwaysBlock = alwaysBlock
            )
        }
    }

    fun deleteSchedule(id: Long) = viewModelScope.launch(Dispatchers.IO) {
        scheduleDao.deleteById(id)
    }

    fun deleteSchedules(ids: List<Long>) = viewModelScope.launch(Dispatchers.IO) {
        for (id in ids.distinct()) {
            scheduleDao.deleteById(id)
        }
    }

    fun toggleSchedule(id: Long, on: Boolean) = viewModelScope.launch(Dispatchers.IO) {
        scheduleDao.setEnabled(id, on)
    }

    fun toggleSchedules(ids: List<Long>, on: Boolean) = viewModelScope.launch(Dispatchers.IO) {
        ids.distinct().forEach { scheduleDao.setEnabled(it, on) }
    }

    fun pauseSchedule(id: Long, minutes: Int) = viewModelScope.launch(Dispatchers.IO) {
        val until = System.currentTimeMillis() + minutes.coerceAtLeast(1) * 60_000L
        scheduleDao.setPausedUntil(id, until)
    }

    fun pauseSchedules(ids: List<Long>, minutes: Int) = viewModelScope.launch(Dispatchers.IO) {
        val until = System.currentTimeMillis() + minutes.coerceAtLeast(1) * 60_000L
        ids.distinct().forEach { scheduleDao.setPausedUntil(it, until) }
    }

    fun resumeSchedule(id: Long) = viewModelScope.launch(Dispatchers.IO) {
        scheduleDao.setPausedUntil(id, 0L)
    }

    fun resumeSchedules(ids: List<Long>) = viewModelScope.launch(Dispatchers.IO) {
        ids.distinct().forEach { scheduleDao.setPausedUntil(it, 0L) }
    }

    fun isShortsAccessibilityEnabled(): Boolean =
        isAccessibilityServiceEnabled(ShortVideoService::class.java)

    fun isWebsiteAccessibilityEnabled(): Boolean =
        isAccessibilityServiceEnabled(appblocker.appblocker.service.FocusAccessibilityService::class.java)

    private fun isAccessibilityServiceEnabled(serviceClass: Class<*>): Boolean {
        val ctx = getApplication<Application>()
        val enabledServices = Settings.Secure.getString(
            ctx.contentResolver, Settings.Secure.ENABLED_ACCESSIBILITY_SERVICES
        ) ?: return false
        val expected = ComponentName(ctx, serviceClass).flattenToString()
        return enabledServices.split(':').any { it.equals(expected, ignoreCase = true) }
    }

    fun openAccessibilitySettings() {
        getApplication<Application>().startActivity(
            Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS)
                .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        )
    }

    private suspend fun mergeAndInsertScheduleDay(
        packageName: String,
        day: Int,
        startMin: Int,
        endMin: Int,
        ruleName: String,
        alwaysBlock: Boolean
    ) {
        val allSchedules = scheduleDao.all().first()
        val sameDay = allSchedules.filter { schedule ->
            schedule.packageName == packageName &&
                day in schedule.daysList() &&
                schedule.alwaysBlock == alwaysBlock
        }

        val overlaps = if (alwaysBlock || startMin >= endMin) {
            sameDay
        } else {
            sameDay.filter {
                it.startMin < it.endMin && startMin <= it.endMin && it.startMin <= endMin
            }
        }

        overlaps.forEach { schedule ->
            scheduleDao.deleteById(schedule.id)
            val remainingDays = schedule.daysList().filterNot { it == day }
            if (remainingDays.isNotEmpty()) {
                scheduleDao.insert(
                    schedule.copy(
                        id = 0,
                        daysOfWeek = remainingDays.sorted().joinToString(",")
                    )
                )
            }
        }

        val mergedStart = if (alwaysBlock || startMin >= endMin) 0 else minOf(startMin, overlaps.minOfOrNull { it.startMin } ?: startMin)
        val mergedEnd = if (alwaysBlock || startMin >= endMin) 0 else maxOf(endMin, overlaps.maxOfOrNull { it.endMin } ?: endMin)

        scheduleDao.insert(
            ScheduleEntity(
                packageName = packageName,
                label = if (packageName.isEmpty()) "All Platforms"
                else ShortsPlatforms.forPackage(packageName)?.label ?: packageName,
                daysOfWeek = day.toString(),
                startMin = mergedStart,
                endMin = mergedEnd,
                ruleName = ruleName,
                alwaysBlock = alwaysBlock
            )
        )
    }
}

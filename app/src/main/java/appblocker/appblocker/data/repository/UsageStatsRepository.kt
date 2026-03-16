package appblocker.appblocker.data.repository

import android.app.usage.UsageEvents
import android.app.usage.UsageStats
import android.app.usage.UsageStatsManager
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Date
import java.util.Locale
import java.util.concurrent.ConcurrentHashMap

class UsageStatsRepository private constructor(private val context: Context) {

    private val usageStatsManager =
        context.getSystemService(Context.USAGE_STATS_SERVICE) as UsageStatsManager
    private val packageManager = context.packageManager
    private val excludedPackages: Set<String> by lazy {
        val homeIntent = Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_HOME)
        val launchers = packageManager.queryIntentActivities(homeIntent, 0)
            .mapNotNull { it.activityInfo?.packageName }
            .toSet()
        launchers + setOf(context.packageName, "com.android.systemui")
    }

    private data class CacheEntry(val fetchedAt: Long, val summary: UsageSummary)
    private val cache = ConcurrentHashMap<String, CacheEntry>()
    private val CACHE_TTL_MS = 5 * 60_000L

    private fun cacheKey(start: Long, end: Long): String {
        val bucket = 5 * 60_000L
        return "$start-${(end / bucket) * bucket}"
    }

    private fun cachedSummary(start: Long, end: Long): UsageSummary? {
        val entry = cache[cacheKey(start, end)] ?: return null
        return if (System.currentTimeMillis() - entry.fetchedAt < CACHE_TTL_MS) entry.summary else null
    }

    private fun storeSummary(start: Long, end: Long, summary: UsageSummary) {
        cache[cacheKey(start, end)] = CacheEntry(System.currentTimeMillis(), summary)
    }

    fun preloadCommonRanges() {
        listOf(
            todayRange(),
            yesterdayRange(),
            lastNDaysRange(7),
            lastNDaysRange(30)
        ).forEach { (s, e) -> runCatching { getUsageSummary(s, e) } }
    }

    data class AppUsageStat(
        val packageName: String,
        val appName: String,
        val totalTimeMs: Long,
        val lastUsed: Long,
        val sessions: List<AppSession>
    ) {
        val totalTimeSec get() = totalTimeMs / 1000
        val totalTimeMin get() = totalTimeMs / 60_000
    }

    data class AppSession(
        val packageName: String,
        val startTime: Long,
        val endTime: Long,
    ) {
        val durationMs get() = endTime - startTime
        val durationMin get() = durationMs / 60_000
    }

    data class HourlyBucket(
        val hourOfDay: Int,
        val totalTimeMs: Long
    )

    data class DailyBucket(
        val dayIndex: Int,
        val dayLabel: String,
        val dayStart: Long,
        val totalTimeMs: Long
    )

    data class UsageSummary(
        val totalScreenTimeMs: Long,
        val appStats: List<AppUsageStat>,
        val hourlyBuckets: List<HourlyBucket>,
        val dailyBuckets: List<DailyBucket>,
        val rangeStart: Long,
        val rangeEnd: Long
    ) {
        val totalScreenTimeSec get() = totalScreenTimeMs / 1000
        val totalScreenTimeMin get() = totalScreenTimeMs / 60_000
        val totalScreenTimeHours get() = totalScreenTimeMs / 3_600_000.0
    }

    fun getUsageSummary(rangeStart: Long, rangeEnd: Long): UsageSummary {
        cachedSummary(rangeStart, rangeEnd)?.let { return it }

        val completedSessions = mutableListOf<AppSession>()
        val openSessions = mutableMapOf<String, Long>()
        val lastUsedMap = mutableMapOf<String, Long>()

        // ── TIER 1: HIGH PRECISION EVENTS (Recent ~7 Days) ───────────────────
        val events = usageStatsManager.queryEvents(rangeStart, rangeEnd)
        val event = UsageEvents.Event()

        while (events.hasNextEvent()) {
            events.getNextEvent(event)
            val ts = event.timeStamp
            val type = event.eventType
            val pkg = event.packageName ?: continue

            when (type) {
                1 -> { // ACTIVITY_RESUMED / MOVE_TO_FOREGROUND
                    // Android only allows 1 foreground app. If an app opens (even a launcher), close all others.
                    val iterator = openSessions.iterator()
                    while (iterator.hasNext()) {
                        val (openPkg, openStart) = iterator.next()
                        if (openPkg != pkg) {
                            if (openPkg !in excludedPackages && ts > openStart) {
                                completedSessions.add(AppSession(openPkg, openStart, ts))
                            }
                            iterator.remove()
                        }
                    }

                    // Only start tracking if it is NOT an excluded package (like your launcher)
                    if (pkg !in excludedPackages) {
                        if (!openSessions.containsKey(pkg)) {
                            openSessions[pkg] = ts
                        }
                        lastUsedMap[pkg] = maxOf(lastUsedMap[pkg] ?: 0L, ts)
                    }
                }
                2 -> { // ACTIVITY_PAUSED / MOVE_TO_BACKGROUND
                    val start = openSessions.remove(pkg)
                    if (start != null && ts > start) {
                        if (pkg !in excludedPackages) {
                            completedSessions.add(AppSession(pkg, start, ts))
                        }
                    }
                }
                16, 17, 26 -> { // SCREEN_NON_INTERACTIVE, KEYGUARD_SHOWN, DEVICE_SHUTDOWN
                    // Screen turned off or locked -> pause all active tracking immediately
                    openSessions.forEach { (openPkg, openStart) ->
                        if (openPkg !in excludedPackages && ts > openStart) {
                            completedSessions.add(AppSession(openPkg, openStart, ts))
                        }
                    }
                    openSessions.clear()
                }
            }
        }

        // Close any apps still running at the end of the query range
        openSessions.forEach { (pkg, start) ->
            if (pkg !in excludedPackages && rangeEnd > start) {
                completedSessions.add(AppSession(pkg, start, rangeEnd))
            }
        }

        // ── TIER 2: HISTORICAL FALLBACK (Older than ~6 Days) ─────────────────
        // UsageEvents are strictly deleted by the OS after ~7 days.
        // For older ranges, we cleanly fallback to daily interval buckets to prevent overlap.
        val eventRetentionStart = System.currentTimeMillis() - (6L * 86_400_000L)
        val aggregateTotals = mutableMapOf<String, Long>()
        val aggregateLastUsed = mutableMapOf<String, Long>()

        if (rangeStart < eventRetentionStart) {
            val stats = usageStatsManager.queryUsageStats(UsageStatsManager.INTERVAL_DAILY, rangeStart, eventRetentionStart)
            stats?.forEach { stat ->
                // Ensure we only count buckets that fall entirely in the historical period to prevent double counting
                if (stat.firstTimeStamp >= rangeStart && stat.firstTimeStamp < eventRetentionStart) {
                    if (stat.packageName !in excludedPackages && stat.totalTimeInForeground > 0) {
                        aggregateTotals[stat.packageName] = (aggregateTotals[stat.packageName] ?: 0L) + stat.totalTimeInForeground
                        aggregateLastUsed[stat.packageName] = maxOf(aggregateLastUsed[stat.packageName] ?: 0L, stat.lastTimeUsed)
                    }
                }
            }
        }

        // ── MERGE TIERS ──────────────────────────────────────────────────────
        val sessionsByApp = completedSessions.groupBy { it.packageName }
        val allPackages = (aggregateTotals.keys + sessionsByApp.keys).toSet()

        val appStats = allPackages.mapNotNull { pkg ->
            val sessionMs = sessionsByApp[pkg]?.sumOf { it.durationMs } ?: 0L
            val aggMs = aggregateTotals[pkg] ?: 0L
            val totalMs = aggMs + sessionMs

            if (totalMs <= 0) return@mapNotNull null

            val lu = maxOf(lastUsedMap[pkg] ?: 0L, aggregateLastUsed[pkg] ?: 0L)
            AppUsageStat(
                packageName = pkg,
                appName = resolveAppName(pkg),
                totalTimeMs = totalMs,
                lastUsed = lu,
                sessions = sessionsByApp[pkg]?.sortedByDescending { it.startTime } ?: emptyList()
            )
        }.sortedByDescending { it.totalTimeMs }

        val hourlyBuckets = buildHourlyBuckets(completedSessions, rangeStart, rangeEnd)
        val dailyBuckets = buildDailyBuckets(completedSessions, rangeStart, rangeEnd, eventRetentionStart)
        val totalMs = appStats.sumOf { it.totalTimeMs }

        val summary = UsageSummary(
            totalScreenTimeMs = totalMs,
            appStats = appStats,
            hourlyBuckets = hourlyBuckets,
            dailyBuckets = dailyBuckets,
            rangeStart = rangeStart,
            rangeEnd = rangeEnd
        )

        storeSummary(rangeStart, rangeEnd, summary)
        return summary
    }

    fun computeAppHourlyBuckets(
        sessions: List<AppSession>,
        rangeStart: Long,
        rangeEnd: Long
    ): List<HourlyBucket> = buildHourlyBuckets(sessions, rangeStart, rangeEnd)

    fun todayRange(): Pair<Long, Long> {
        val cal = Calendar.getInstance().apply {
            set(Calendar.HOUR_OF_DAY, 0)
            set(Calendar.MINUTE, 0)
            set(Calendar.SECOND, 0)
            set(Calendar.MILLISECOND, 0)
        }
        return cal.timeInMillis to System.currentTimeMillis()
    }

    fun yesterdayRange(): Pair<Long, Long> {
        val cal = Calendar.getInstance().apply {
            add(Calendar.DAY_OF_YEAR, -1)
            set(Calendar.HOUR_OF_DAY, 0)
            set(Calendar.MINUTE, 0)
            set(Calendar.SECOND, 0)
            set(Calendar.MILLISECOND, 0)
        }
        val start = cal.timeInMillis
        return start to (start + 86_400_000L)
    }

    fun lastNDaysRange(n: Int): Pair<Long, Long> {
        val cal = Calendar.getInstance().apply {
            add(Calendar.DAY_OF_YEAR, -n)
            set(Calendar.HOUR_OF_DAY, 0)
            set(Calendar.MINUTE, 0)
            set(Calendar.SECOND, 0)
            set(Calendar.MILLISECOND, 0)
        }
        return cal.timeInMillis to System.currentTimeMillis()
    }

    fun isoWeekRange(weeksAgo: Int): Pair<Long, Long> {
        val cal = Calendar.getInstance().apply {
            firstDayOfWeek = Calendar.MONDAY
            set(Calendar.DAY_OF_WEEK, Calendar.MONDAY)
            set(Calendar.HOUR_OF_DAY, 0)
            set(Calendar.MINUTE, 0)
            set(Calendar.SECOND, 0)
            set(Calendar.MILLISECOND, 0)
        }
        cal.add(Calendar.WEEK_OF_YEAR, -weeksAgo)
        val start = cal.timeInMillis
        val end = if (weeksAgo == 0) System.currentTimeMillis() else start + 7L * 86_400_000L
        return start to end
    }

    fun getTotalScreenTimeToday(): Long {
        val (start, end) = todayRange()
        return getUsageSummary(start, end).totalScreenTimeMs
    }

    fun getTopAppsToday(limit: Int = 10): List<AppUsageStat> {
        val (start, end) = todayRange()
        return getUsageSummary(start, end).appStats.take(limit)
    }

    fun getAppSessionsToday(packageName: String): List<AppSession> {
        val (start, end) = todayRange()
        return getUsageSummary(start, end)
            .appStats.firstOrNull { it.packageName == packageName }
            ?.sessions ?: emptyList()
    }

    fun getHourlyBucketsToday(): List<HourlyBucket> {
        val (start, end) = todayRange()
        return getUsageSummary(start, end).hourlyBuckets
    }

    fun getAppUsageTodayMs(packageName: String): Long {
        val (start, end) = todayRange()
        return getUsageSummary(start, end)
            .appStats.firstOrNull { it.packageName == packageName }
            ?.totalTimeMs ?: 0L
    }

    private fun buildHourlyBuckets(
        sessions: List<AppSession>,
        rangeStart: Long,
        rangeEnd: Long
    ): List<HourlyBucket> {
        val buckets = LongArray(24) { 0L }

        for (session in sessions) {
            val sStart = session.startTime.coerceAtLeast(rangeStart)
            val sEnd = session.endTime.coerceAtMost(rangeEnd)
            if (sEnd <= sStart) continue

            var cursor = sStart
            while (cursor < sEnd) {
                val cal = Calendar.getInstance().apply { timeInMillis = cursor }
                val hourIndex = cal.get(Calendar.HOUR_OF_DAY)

                val nextHour = Calendar.getInstance().apply {
                    timeInMillis = cursor
                    set(Calendar.MINUTE, 0); set(Calendar.SECOND, 0)
                    set(Calendar.MILLISECOND, 0)
                    add(Calendar.HOUR_OF_DAY, 1)
                }
                val sliceEnd = minOf(sEnd, nextHour.timeInMillis)
                buckets[hourIndex] += sliceEnd - cursor
                cursor = sliceEnd
            }
        }

        return buckets.mapIndexed { hour, ms -> HourlyBucket(hour, ms) }
    }

    private fun buildDailyBuckets(
        sessions: List<AppSession>,
        rangeStart: Long,
        rangeEnd: Long,
        eventRetentionStart: Long
    ): List<DailyBucket> {
        val sdf = SimpleDateFormat("EEE", Locale.getDefault())
        val days = mutableListOf<Triple<Long, Long, String>>()

        val cal = Calendar.getInstance().apply {
            timeInMillis = rangeStart
            set(Calendar.HOUR_OF_DAY, 0); set(Calendar.MINUTE, 0)
            set(Calendar.SECOND, 0); set(Calendar.MILLISECOND, 0)
        }

        while (cal.timeInMillis < rangeEnd) {
            val dayStart = cal.timeInMillis
            cal.add(Calendar.DAY_OF_YEAR, 1)
            val dayEnd = minOf(cal.timeInMillis, rangeEnd)
            days.add(Triple(dayStart, dayEnd, sdf.format(Date(dayStart))))
        }

        return days.mapIndexed { index, (dayStart, dayEnd, label) ->
            val totalMs = if (dayEnd <= eventRetentionStart) {
                // High-efficiency fetch for historical older dates using precise INTERVAL_DAILY matching
                var dailyTotal = 0L
                val stats = usageStatsManager.queryUsageStats(UsageStatsManager.INTERVAL_DAILY, dayStart, dayEnd)
                if (stats != null) {
                    for (stat in stats) {
                        // Crucial: Only sum buckets that specifically belong to this exact day
                        if (stat.firstTimeStamp >= dayStart && stat.firstTimeStamp < dayEnd) {
                            if (stat.packageName !in excludedPackages) {
                                dailyTotal += stat.totalTimeInForeground
                            }
                        }
                    }
                }
                dailyTotal
            } else {
                // High-precision fetch utilizing exact recorded sessions for the current week
                sessions.sumOf { session ->
                    val sStart = session.startTime.coerceAtLeast(dayStart)
                    val sEnd = session.endTime.coerceAtMost(dayEnd)
                    if (sEnd > sStart) sEnd - sStart else 0L
                }
            }
            DailyBucket(index, label, dayStart, totalMs)
        }
    }

    private fun resolveAppName(packageName: String): String {
        return try {
            packageManager.getApplicationLabel(
                packageManager.getApplicationInfo(packageName, PackageManager.GET_META_DATA)
            ).toString()
        } catch (_: PackageManager.NameNotFoundException) {
            packageName
        }
    }

    companion object {
        @Volatile
        private var INSTANCE: UsageStatsRepository? = null

        fun get(context: Context): UsageStatsRepository =
            INSTANCE ?: synchronized(this) {
                INSTANCE ?: UsageStatsRepository(context.applicationContext).also { INSTANCE = it }
            }
    }
}
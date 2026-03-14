package appblocker.appblocker.data.repository

import android.app.usage.UsageEvents
import android.app.usage.UsageStatsManager
import android.content.Context
import android.content.pm.PackageManager
import java.text.SimpleDateFormat
import java.util.Locale
import java.util.concurrent.ConcurrentHashMap

/**
 * Single source of truth for ALL usage stats data in the app.
 *
 * Every screen that needs usage data — total screen time, per-app breakdown,
 * hourly chart buckets, per-app sessions — calls this class.
 * Nothing else touches UsageStatsManager directly.
 *
 * Requires: android.permission.PACKAGE_USAGE_STATS (user-granted special permission)
 *
 * ------------------------------------------------------------------
 * Data model:
 *
 *  AppUsageStat       — one app's total usage for a time range
 *  HourlyBucket       — total usage across all apps for one hour slot (for bar chart)
 *  AppSession         — a single continuous foreground session for one app
 * ------------------------------------------------------------------
 */
class UsageStatsRepository private constructor(private val context: Context) {

    private val usageStatsManager =
        context.getSystemService(Context.USAGE_STATS_SERVICE) as UsageStatsManager
    private val packageManager = context.packageManager

    // ── In-memory cache (keyed by normalised start/end, 5-min TTL) ──────────
    private data class CacheEntry(val fetchedAt: Long, val summary: UsageSummary)
    private val cache = ConcurrentHashMap<String, CacheEntry>()
    private val CACHE_TTL_MS = 5 * 60_000L

    private fun cacheKey(start: Long, end: Long): String {
        // Round end to the nearest 5-min bucket so "now" keys stay stable
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

    /** Pre-warm the most common ranges so the UI is instant. Call from a background thread. */
    fun preloadCommonRanges() {
        listOf(
            todayRange(),
            yesterdayRange(),
            lastNDaysRange(7),
            lastNDaysRange(30)
        ).forEach { (s, e) -> runCatching { getUsageSummary(s, e) } }
    }

    // ─────────────────────────────────────────────────────────────────────────
    // Public data models
    // ─────────────────────────────────────────────────────────────────────────

    data class AppUsageStat(
        val packageName: String,
        val appName: String,
        val totalTimeMs: Long,          // total foreground time in range
        val lastUsed: Long,             // epoch ms of last foreground event
        val sessions: List<AppSession>  // individual open→close sessions
    ) {
        val totalTimeSec get() = totalTimeMs / 1000
        val totalTimeMin get() = totalTimeMs / 60_000
    }

    data class AppSession(
        val packageName: String,
        val startTime: Long,   // epoch ms
        val endTime: Long,     // epoch ms
    ) {
        val durationMs get() = endTime - startTime
        val durationMin get() = durationMs / 60_000
    }

    data class HourlyBucket(
        val hourOfDay: Int,     // 0–23
        val totalTimeMs: Long   // sum of all apps in this hour
    )

    data class DailyBucket(
        val dayIndex: Int,      // 0 = oldest day in range
        val dayLabel: String,   // e.g. "Mon", "Mar 9"
        val dayStart: Long,     // epoch ms midnight
        val totalTimeMs: Long
    )

    data class UsageSummary(
        val totalScreenTimeMs: Long,
        val appStats: List<AppUsageStat>,       // sorted by totalTimeMs desc
        val hourlyBuckets: List<HourlyBucket>,  // 24 entries, index = hour
        val dailyBuckets: List<DailyBucket>,    // one per calendar day in range
        val rangeStart: Long,
        val rangeEnd: Long
    ) {
        val totalScreenTimeSec get() = totalScreenTimeMs / 1000
        val totalScreenTimeMin get() = totalScreenTimeMs / 60_000
        val totalScreenTimeHours get() = totalScreenTimeMs / 3_600_000.0
    }

    // ─────────────────────────────────────────────────────────────────────────
    // Primary API — everything the UI needs comes from here
    // ─────────────────────────────────────────────────────────────────────────

    /**
     * Fetch complete usage summary for [rangeStart]…[rangeEnd].
     *
     * Internally walks the raw UsageEvents stream once and builds:
     *   - per-app session list
     *   - per-app total time
     *   - 24 hourly buckets for the bar chart
     *
     * This is the only place that calls queryEvents().
     */
    fun getUsageSummary(rangeStart: Long, rangeEnd: Long): UsageSummary {
        cachedSummary(rangeStart, rangeEnd)?.let { return it }

        val openSessions     = mutableMapOf<String, Long>()          // pkg → resume time
        val completedSessions = mutableListOf<AppSession>()
        val lastUsedMap      = mutableMapOf<String, Long>()           // pkg → last event time

        val events = usageStatsManager.queryEvents(rangeStart, rangeEnd)
        val event  = UsageEvents.Event()

        while (events?.hasNextEvent() == true) {
            events.getNextEvent(event)
            val pkg = event.packageName ?: continue

            when (event.eventType) {
                UsageEvents.Event.ACTIVITY_RESUMED -> {
                    openSessions[pkg] = event.timeStamp
                    lastUsedMap[pkg]  = event.timeStamp
                }
                UsageEvents.Event.ACTIVITY_PAUSED,
                UsageEvents.Event.ACTIVITY_STOPPED -> {
                    val start = openSessions.remove(pkg)
                    if (start != null && event.timeStamp > start)
                        completedSessions.add(AppSession(pkg, start, event.timeStamp))
                    lastUsedMap[pkg] = event.timeStamp
                }
            }
        }

        // Close any sessions still open at rangeEnd (app still in foreground)
        openSessions.forEach { (pkg, start) ->
            if (rangeEnd > start) completedSessions.add(AppSession(pkg, start, rangeEnd))
        }

        // Build per-app stats
        val sessionsByApp = completedSessions.groupBy { it.packageName }
        val appStats = sessionsByApp.map { (pkg, sessions) ->
            AppUsageStat(
                packageName = pkg,
                appName     = resolveAppName(pkg),
                totalTimeMs = sessions.sumOf { it.durationMs },
                lastUsed    = lastUsedMap[pkg] ?: 0L,
                sessions    = sessions.sortedByDescending { it.startTime }
            )
        }.sortedByDescending { it.totalTimeMs }

        // Build 24 hourly buckets
        val hourlyBuckets  = buildHourlyBuckets(completedSessions, rangeStart, rangeEnd)
        val dailyBuckets   = buildDailyBuckets(completedSessions, rangeStart, rangeEnd)
        val totalMs        = appStats.sumOf { it.totalTimeMs }

        val summary = UsageSummary(
            totalScreenTimeMs = totalMs,
            appStats          = appStats,
            hourlyBuckets     = hourlyBuckets,
            dailyBuckets      = dailyBuckets,
            rangeStart        = rangeStart,
            rangeEnd          = rangeEnd
        )

        storeSummary(rangeStart, rangeEnd, summary)
        return summary
    }

    // ─────────────────────────────────────────────────────────────────────────
    // Public helper — per-app hourly chart usable from UI layer
    // ─────────────────────────────────────────────────────────────────────────

    /** Compute 24-bucket hourly usage for a single app from its already-fetched sessions. */
    fun computeAppHourlyBuckets(
        sessions: List<AppSession>,
        rangeStart: Long,
        rangeEnd: Long
    ): List<HourlyBucket> = buildHourlyBuckets(sessions, rangeStart, rangeEnd)

    // ─────────────────────────────────────────────────────────────────────────
    // Convenience range builders
    // ─────────────────────────────────────────────────────────────────────────

    /** Today: midnight → now */
    fun todayRange(): Pair<Long, Long> {
        val cal = java.util.Calendar.getInstance().apply {
            set(java.util.Calendar.HOUR_OF_DAY, 0)
            set(java.util.Calendar.MINUTE, 0)
            set(java.util.Calendar.SECOND, 0)
            set(java.util.Calendar.MILLISECOND, 0)
        }
        return cal.timeInMillis to System.currentTimeMillis()
    }

    /** Yesterday: midnight–midnight */
    fun yesterdayRange(): Pair<Long, Long> {
        val cal = java.util.Calendar.getInstance().apply {
            add(java.util.Calendar.DAY_OF_YEAR, -1)
            set(java.util.Calendar.HOUR_OF_DAY, 0)
            set(java.util.Calendar.MINUTE, 0)
            set(java.util.Calendar.SECOND, 0)
            set(java.util.Calendar.MILLISECOND, 0)
        }
        val start = cal.timeInMillis
        return start to (start + 86_400_000L)
    }

    /** Last N days: N days ago midnight → now */
    fun lastNDaysRange(n: Int): Pair<Long, Long> {
        val cal = java.util.Calendar.getInstance().apply {
            add(java.util.Calendar.DAY_OF_YEAR, -n)
            set(java.util.Calendar.HOUR_OF_DAY, 0)
            set(java.util.Calendar.MINUTE, 0)
            set(java.util.Calendar.SECOND, 0)
            set(java.util.Calendar.MILLISECOND, 0)
        }
        return cal.timeInMillis to System.currentTimeMillis()
    }

    /** Returns (weekStart, weekEnd) for the ISO-week that is [weeksAgo] weeks before the current one.
     *  weeksAgo = 0 → current week (Mon 00:00 → now)
     *  weeksAgo = 1 → last week (Mon 00:00 → Sun 23:59:59) etc.
     */
    fun isoWeekRange(weeksAgo: Int): Pair<Long, Long> {
        val cal = java.util.Calendar.getInstance().apply {
            firstDayOfWeek = java.util.Calendar.MONDAY
            set(java.util.Calendar.DAY_OF_WEEK, java.util.Calendar.MONDAY)
            set(java.util.Calendar.HOUR_OF_DAY, 0)
            set(java.util.Calendar.MINUTE, 0)
            set(java.util.Calendar.SECOND, 0)
            set(java.util.Calendar.MILLISECOND, 0)
        }
        cal.add(java.util.Calendar.WEEK_OF_YEAR, -weeksAgo)
        val start = cal.timeInMillis
        val end   = if (weeksAgo == 0) System.currentTimeMillis() else start + 7L * 86_400_000L
        return start to end
    }

    // ─────────────────────────────────────────────────────────────────────────
    // Focused queries
    // ─────────────────────────────────────────────────────────────────────────

    /** Total screen time for today in ms */
    fun getTotalScreenTimeToday(): Long {
        val (start, end) = todayRange()
        return getUsageSummary(start, end).totalScreenTimeMs
    }

    /** Top N apps by usage today */
    fun getTopAppsToday(limit: Int = 10): List<AppUsageStat> {
        val (start, end) = todayRange()
        return getUsageSummary(start, end).appStats.take(limit)
    }

    /** All sessions for a specific app today */
    fun getAppSessionsToday(packageName: String): List<AppSession> {
        val (start, end) = todayRange()
        return getUsageSummary(start, end)
            .appStats.firstOrNull { it.packageName == packageName }
            ?.sessions ?: emptyList()
    }

    /** Hourly bar chart data for today */
    fun getHourlyBucketsToday(): List<HourlyBucket> {
        val (start, end) = todayRange()
        return getUsageSummary(start, end).hourlyBuckets
    }

    /** Single app's total time today in ms */
    fun getAppUsageTodayMs(packageName: String): Long {
        val (start, end) = todayRange()
        return getUsageSummary(start, end)
            .appStats.firstOrNull { it.packageName == packageName }
            ?.totalTimeMs ?: 0L
    }

    // ─────────────────────────────────────────────────────────────────────────
    // Internal helpers
    // ─────────────────────────────────────────────────────────────────────────

    /**
     * Distribute session durations into 24 one-hour buckets keyed by hour-of-day (0–23).
     * Uses Calendar.HOUR_OF_DAY so it works correctly across day boundaries and multi-day ranges.
     */
    private fun buildHourlyBuckets(
        sessions: List<AppSession>,
        rangeStart: Long,
        rangeEnd: Long
    ): List<HourlyBucket> {
        val buckets = LongArray(24) { 0L }

        for (session in sessions) {
            val sStart = session.startTime.coerceAtLeast(rangeStart)
            val sEnd   = session.endTime.coerceAtMost(rangeEnd)
            if (sEnd <= sStart) continue

            var cursor = sStart
            while (cursor < sEnd) {
                val cal = java.util.Calendar.getInstance().apply { timeInMillis = cursor }
                val hourIndex = cal.get(java.util.Calendar.HOUR_OF_DAY)

                // Advance to start of next hour
                val nextHour = java.util.Calendar.getInstance().apply {
                    timeInMillis = cursor
                    set(java.util.Calendar.MINUTE, 0); set(java.util.Calendar.SECOND, 0)
                    set(java.util.Calendar.MILLISECOND, 0)
                    add(java.util.Calendar.HOUR_OF_DAY, 1)
                }
                val sliceEnd = minOf(sEnd, nextHour.timeInMillis)
                buckets[hourIndex] += sliceEnd - cursor
                cursor = sliceEnd
            }
        }

        return buckets.mapIndexed { hour, ms -> HourlyBucket(hour, ms) }
    }

    /**
     * Build one [DailyBucket] per calendar day that falls within [rangeStart]..[rangeEnd].
     */
    private fun buildDailyBuckets(
        sessions: List<AppSession>,
        rangeStart: Long,
        rangeEnd: Long
    ): List<DailyBucket> {
        val sdf  = SimpleDateFormat("EEE", Locale.getDefault())
        val days = mutableListOf<Triple<Long, Long, String>>() // (dayStart, dayEnd, label)

        val cal = java.util.Calendar.getInstance().apply {
            timeInMillis = rangeStart
            set(java.util.Calendar.HOUR_OF_DAY, 0); set(java.util.Calendar.MINUTE, 0)
            set(java.util.Calendar.SECOND, 0);       set(java.util.Calendar.MILLISECOND, 0)
        }

        while (cal.timeInMillis < rangeEnd) {
            val dayStart = cal.timeInMillis
            cal.add(java.util.Calendar.DAY_OF_YEAR, 1)
            val dayEnd = minOf(cal.timeInMillis, rangeEnd)
            days.add(Triple(dayStart, dayEnd, sdf.format(java.util.Date(dayStart))))
        }

        return days.mapIndexed { index, (dayStart, dayEnd, label) ->
            val totalMs = sessions.sumOf { session ->
                val sStart = session.startTime.coerceAtLeast(dayStart)
                val sEnd   = session.endTime.coerceAtMost(dayEnd)
                if (sEnd > sStart) sEnd - sStart else 0L
            }
            DailyBucket(index, label, dayStart, totalMs)
        }
    }

    private fun resolveAppName(packageName: String): String {
        return try {
            packageManager.getApplicationLabel(
                packageManager.getApplicationInfo(packageName, 0)
            ).toString()
        } catch (_: PackageManager.NameNotFoundException) {
            packageName
        }
    }

    // ─────────────────────────────────────────────────────────────────────────
    // Singleton
    // ─────────────────────────────────────────────────────────────────────────

    companion object {
        @Volatile private var INSTANCE: UsageStatsRepository? = null
        fun get(context: Context): UsageStatsRepository =
            INSTANCE ?: synchronized(this) {
                INSTANCE ?: UsageStatsRepository(context.applicationContext).also { INSTANCE = it }
            }
    }
}

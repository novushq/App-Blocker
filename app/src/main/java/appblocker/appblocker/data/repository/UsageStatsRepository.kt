package appblocker.appblocker.data.repository

import android.app.usage.UsageEvents
import android.app.usage.UsageStatsManager
import android.content.Context
import android.content.pm.PackageManager

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

    data class UsageSummary(
        val totalScreenTimeMs: Long,
        val appStats: List<AppUsageStat>,       // sorted by totalTimeMs desc
        val hourlyBuckets: List<HourlyBucket>,  // 24 entries, index = hour
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
        // Map of packageName → list of open sessions (endTime filled in when ACTIVITY_PAUSED)
        val openSessions = mutableMapOf<String, Long>()          // pkg → resume time
        val completedSessions = mutableListOf<AppSession>()
        val lastUsedMap = mutableMapOf<String, Long>()           // pkg → last event time

        val events = usageStatsManager.queryEvents(rangeStart, rangeEnd)
        val event = UsageEvents.Event()

        while (events?.hasNextEvent() == true) {
            events.getNextEvent(event)
            val pkg = event.packageName ?: continue

            when (event.eventType) {
                UsageEvents.Event.ACTIVITY_RESUMED -> {
                    openSessions[pkg] = event.timeStamp
                    lastUsedMap[pkg] = event.timeStamp
                }
                UsageEvents.Event.ACTIVITY_PAUSED,
                UsageEvents.Event.ACTIVITY_STOPPED -> {
                    val start = openSessions.remove(pkg)
                    if (start != null && event.timeStamp > start) {
                        completedSessions.add(AppSession(pkg, start, event.timeStamp))
                    }
                    lastUsedMap[pkg] = event.timeStamp
                }
            }
        }

        // Close any sessions still open at rangeEnd (app still in foreground)
        openSessions.forEach { (pkg, start) ->
            if (rangeEnd > start) {
                completedSessions.add(AppSession(pkg, start, rangeEnd))
            }
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
        val hourlyBuckets = buildHourlyBuckets(completedSessions, rangeStart, rangeEnd)

        val totalMs = appStats.sumOf { it.totalTimeMs }

        return UsageSummary(
            totalScreenTimeMs = totalMs,
            appStats          = appStats,
            hourlyBuckets     = hourlyBuckets,
            rangeStart        = rangeStart,
            rangeEnd          = rangeEnd
        )
    }

    // ─────────────────────────────────────────────────────────────────────────
    // Convenience range builders — pass directly to getUsageSummary()
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

    // ─────────────────────────────────────────────────────────────────────────
    // Focused queries — built on top of getUsageSummary, no extra DB calls
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
     * Distribute session durations into 24 one-hour buckets.
     * A session that spans multiple hours is split proportionally.
     */
    private fun buildHourlyBuckets(
        sessions: List<AppSession>,
        rangeStart: Long,
        rangeEnd: Long
    ): List<HourlyBucket> {
        val buckets = LongArray(24) { 0L }

        // Find the calendar day start for rangeStart to compute hour offsets
        val dayCal = java.util.Calendar.getInstance().apply {
            timeInMillis = rangeStart
            set(java.util.Calendar.HOUR_OF_DAY, 0)
            set(java.util.Calendar.MINUTE, 0)
            set(java.util.Calendar.SECOND, 0)
            set(java.util.Calendar.MILLISECOND, 0)
        }
        val dayStart = dayCal.timeInMillis

        for (session in sessions) {
            val sStart = session.startTime.coerceAtLeast(rangeStart)
            val sEnd   = session.endTime.coerceAtMost(rangeEnd)
            if (sEnd <= sStart) continue

            // Walk through each hour the session touches
            var cursor = sStart
            while (cursor < sEnd) {
                val offsetMs   = cursor - dayStart
                val hourIndex  = (offsetMs / 3_600_000L).toInt().coerceIn(0, 23)
                val hourEnd    = dayStart + (hourIndex + 1) * 3_600_000L
                val sliceEnd   = minOf(sEnd, hourEnd)
                buckets[hourIndex] += sliceEnd - cursor
                cursor = sliceEnd
            }
        }

        return buckets.mapIndexed { hour, ms -> HourlyBucket(hour, ms) }
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

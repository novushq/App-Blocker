package appblocker.appblocker.shorts

import android.content.Context
import androidx.room.*
import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase
import kotlinx.coroutines.flow.Flow
import java.util.Calendar
import java.util.Locale

// ─────────────────────────────────────────────────────────────────
// CONSTANTS — view IDs detected via layout inspector / UIAutomator
// These are the real resource IDs that appear in the UI tree.
// Instagram changes theirs occasionally; update as needed.
// ─────────────────────────────────────────────────────────────────
object ShortsPlatforms {
    data class Platform(
        val packageName: String,
        val label: String,
        // ANY of these viewIdResourceName substrings = we're in shorts/reels
        val viewIds: List<String>,
        // fallback: contentDescription keywords
        val contentDescKeywords: List<String> = emptyList(),
        val alwaysShortFormApp: Boolean = false
    )

    val ALL = listOf(
        Platform(
            packageName = "com.google.android.youtube",
            label = "YouTube Shorts",
            viewIds = listOf(
                "reel_watch_fragment_root",   // primary shorts player container
                "reel_player_page",
                "shorts_player"
            ),
            contentDescKeywords = listOf("Shorts", "shorts", "short video")
        ),
        Platform(
            packageName = "com.instagram.android",
            label = "Instagram Reels",
            viewIds = listOf(
                "clips_viewer_view_pager",    // full-screen reels pager
                "unified_clips_controller",
            ),
            contentDescKeywords = listOf("Reels", "reels")
        ),
        Platform(
            packageName = "com.zhiliaoapp.musically",  // TikTok
            label = "TikTok",
            viewIds = listOf(
                "main_container",     // TikTok full-screen = always shorts
                "feed_container"
            ),
            contentDescKeywords = listOf("Following", "For You"),
            alwaysShortFormApp = true
        ),
        Platform(
            packageName = "com.ss.android.ugc.trill",  // TikTok alternate pkg
            label = "TikTok",
            viewIds = listOf("main_container", "feed_container"),
            contentDescKeywords = listOf("Following", "For You"),
            alwaysShortFormApp = true
        ),
        Platform(
            packageName = "com.facebook.katana",
            label = "Facebook Reels",
            viewIds = listOf(
                "reels_viewer_container"
            ),
            contentDescKeywords = listOf("Reels", "reels")
        ),
        Platform(
            packageName = "com.snapchat.android",
            label = "Snapchat Spotlight",
            viewIds = listOf("spotlight_feed_container"),
            contentDescKeywords = listOf("Spotlight", "spotlight")
        )
    )

    val PACKAGES: Set<String> = ALL.map { it.packageName }.toSet()
    fun forPackage(pkg: String) = ALL.find { it.packageName == pkg }
}

fun hasStrictShortsKeywordSignal(rawValue: String, keywords: List<String>): Boolean {
    if (rawValue.isBlank() || keywords.isEmpty()) return false

    val value = rawValue.lowercase(Locale.ROOT).trim()
    // Avoid false positives from bottom-tab labels that are always visible.
    val genericTabLabels = setOf("reels", "shorts", "clips", "spotlight", "for you")
    if (value in genericTabLabels) return false

    val hasKeyword = keywords.any { value.contains(it.lowercase(Locale.ROOT)) }
    val likelyViewerContext = value.contains("viewer") || value.contains("watch") || value.contains("player")
    return hasKeyword && likelyViewerContext
}

// ─────────────────────────────────────────────────────────────────
// ROOM — Entities
// ─────────────────────────────────────────────────────────────────

@Entity(tableName = "sessions")
data class SessionEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val packageName: String,
    val label: String,
    val startMs: Long,
    val endMs: Long = 0L,
    val durationSec: Long = 0L,
    val wasBlocked: Boolean = false
) {
    fun actualDurationSec(nowMs: Long = System.currentTimeMillis()): Long =
        if (endMs == 0L) ((nowMs - startMs).coerceAtLeast(0L) / 1000L) else durationSec
}

@Entity(tableName = "schedules")
data class ScheduleEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val packageName: String,    // "" = all platforms
    val label: String,
    val daysOfWeek: String,     // CSV of Calendar.DAY_OF_WEEK: "2,3,4,5,6"
    val startMin: Int,          // minutes from midnight e.g. 540 = 09:00
    val endMin: Int,            // e.g. 1020 = 17:00
    val enabled: Boolean = true,
    val ruleName: String = "",
    val alwaysBlock: Boolean = false,
    val pausedUntilMs: Long = 0L
) {
    fun daysList(): List<Int> = daysOfWeek.split(",").mapNotNull { it.trim().toIntOrNull() }

    fun isPausedAt(nowMs: Long = System.currentTimeMillis()): Boolean = pausedUntilMs > nowMs

    fun appliesTo(packageName: String): Boolean =
        this.packageName.isBlank() || this.packageName == packageName

    fun isBlockingAt(packageName: String, dayOfWeek: Int, nowMin: Int, nowMs: Long): Boolean {
        if (!enabled || isPausedAt(nowMs) || !appliesTo(packageName)) return false
        if (alwaysBlock) return true
        if (dayOfWeek !in daysList()) return false
        return if (startMin < endMin) nowMin in startMin until endMin
        else nowMin >= startMin || nowMin < endMin
    }
}

// ─────────────────────────────────────────────────────────────────
// ROOM — Type Converters (none needed — we store days as CSV string)
// ─────────────────────────────────────────────────────────────────

// ─────────────────────────────────────────────────────────────────
// ROOM — DAOs
// ─────────────────────────────────────────────────────────────────

@Dao
interface SessionDao {
    @Insert suspend fun insert(s: SessionEntity): Long
    @Update suspend fun update(s: SessionEntity)

    @Query("SELECT * FROM sessions WHERE startMs >= :from AND startMs < :to ORDER BY startMs DESC")
    fun sessionsInRange(from: Long, to: Long): Flow<List<SessionEntity>>

    // open session (endMs=0) — at most 1 should exist
    @Query("SELECT * FROM sessions WHERE endMs = 0 ORDER BY startMs DESC LIMIT 1")
    suspend fun openSession(): SessionEntity?

    // aggregate: total seconds per label for a day
    @Query("""
        SELECT label,
               SUM(CASE WHEN endMs = 0 THEN ((:nowMs - startMs) / 1000) ELSE durationSec END) as total
        FROM sessions WHERE startMs >= :from AND startMs < :to
        GROUP BY label
    """)
    fun platformTotals(from: Long, to: Long, nowMs: Long): Flow<List<PlatformTotal>>

    // hourly breakdown for bar chart
    @Query("""
        SELECT (startMs / 3600000) % 24 as hour,
               SUM(CASE WHEN endMs = 0 THEN ((:nowMs - startMs) / 1000) ELSE durationSec END) as secs
        FROM sessions WHERE startMs >= :from AND startMs < :to
        GROUP BY hour ORDER BY hour
    """)
    fun hourly(from: Long, to: Long, nowMs: Long): Flow<List<HourSlot>>

    // 7-day daily totals
    @Query("""
        SELECT (startMs / 86400000) as day,
               SUM(CASE WHEN endMs = 0 THEN ((:nowMs - startMs) / 1000) ELSE durationSec END) as secs
        FROM sessions WHERE startMs >= :from
        GROUP BY day ORDER BY day
    """)
    fun dailyTotals(from: Long, nowMs: Long): Flow<List<DaySlot>>

    @Query("SELECT COUNT(*) FROM sessions WHERE startMs >= :from AND startMs < :to AND wasBlocked = 1")
    fun blockedCount(from: Long, to: Long): Flow<Int>

    // Sessions for a specific platform — used in SessionsDetailScreen
    @Query("SELECT * FROM sessions WHERE label = :label AND startMs >= :from AND startMs < :to ORDER BY startMs DESC")
    fun sessionsForPlatform(label: String, from: Long, to: Long): Flow<List<SessionEntity>>

    // All-time longest session per platform
    @Query("SELECT * FROM sessions WHERE label = :label ORDER BY durationSec DESC LIMIT 1")
    suspend fun longestSession(label: String): SessionEntity?
}

data class PlatformTotal(val label: String, val total: Long)
data class HourSlot(val hour: Int, val secs: Long)
data class DaySlot(val day: Long, val secs: Long)

@Dao
interface ScheduleDao {
    @Insert(onConflict = OnConflictStrategy.REPLACE) suspend fun insert(s: ScheduleEntity): Long
    @Query("SELECT * FROM schedules ORDER BY startMin ASC") fun all(): Flow<List<ScheduleEntity>>
    @Query("SELECT * FROM schedules WHERE enabled = 1") suspend fun active(): List<ScheduleEntity>
    @Query("UPDATE schedules SET enabled = :on WHERE id = :id") suspend fun setEnabled(id: Long, on: Boolean)
    @Query("UPDATE schedules SET pausedUntilMs = :pausedUntilMs WHERE id = :id") suspend fun setPausedUntil(id: Long, pausedUntilMs: Long)
    @Query("DELETE FROM schedules WHERE id = :id") suspend fun deleteById(id: Long)
}

// ─────────────────────────────────────────────────────────────────
// ROOM — Database
// ─────────────────────────────────────────────────────────────────

@Database(entities = [SessionEntity::class, ScheduleEntity::class], version = 2, exportSchema = false)
abstract class ShortsDb : RoomDatabase() {
    abstract fun sessions(): SessionDao
    abstract fun schedules(): ScheduleDao
    companion object {
        @Volatile private var INSTANCE: ShortsDb? = null
        fun get(ctx: Context) = INSTANCE ?: synchronized(this) {
            INSTANCE ?: Room.databaseBuilder(ctx.applicationContext, ShortsDb::class.java, "shorts.db")
                .addMigrations(MIGRATION_1_2)
                .build().also { INSTANCE = it }
        }

        private val MIGRATION_1_2 = object : Migration(1, 2) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL(
                    "ALTER TABLE schedules ADD COLUMN alwaysBlock INTEGER NOT NULL DEFAULT 0"
                )
                db.execSQL(
                    "ALTER TABLE schedules ADD COLUMN pausedUntilMs INTEGER NOT NULL DEFAULT 0"
                )
            }
        }
    }
}

// ─────────────────────────────────────────────────────────────────
// MERGE INTERVALS — pure function, no class needed
// Input: list of (start, end) minute pairs → merged non-overlapping list
// ─────────────────────────────────────────────────────────────────

data class Interval(val start: Int, val end: Int) {
    fun containsNow(): Boolean {
        val cal = Calendar.getInstance()
        val nowMin = cal.get(Calendar.HOUR_OF_DAY) * 60 + cal.get(Calendar.MINUTE)
        return nowMin in start until end
    }
    fun display() = "%02d:%02d–%02d:%02d".format(start / 60, start % 60, end / 60, end % 60)
}

fun mergeIntervals(pairs: List<Pair<Int, Int>>): List<Interval> {
    if (pairs.isEmpty()) return emptyList()
    val sorted = pairs.sortedBy { it.first }
    val result = mutableListOf(sorted[0].first to sorted[0].second)
    for ((s, e) in sorted.drop(1)) {
        val last = result.last()
        if (s <= last.second) result[result.lastIndex] = last.first to maxOf(last.second, e)
        else result.add(s to e)
    }
    return result.map { Interval(it.first, it.second) }
}

// Checks if packageName is blocked RIGHT NOW given list of active ScheduleEntities
fun isBlockedNow(schedules: List<ScheduleEntity>, packageName: String): Boolean {
    val nowMs = System.currentTimeMillis()
    val cal = Calendar.getInstance()
    return isBlockedNow(
        schedules = schedules,
        packageName = packageName,
        dayOfWeek = cal.get(Calendar.DAY_OF_WEEK),
        nowMin = cal.get(Calendar.HOUR_OF_DAY) * 60 + cal.get(Calendar.MINUTE),
        nowMs = nowMs
    )
}

fun isBlockedNow(
    schedules: List<ScheduleEntity>,
    packageName: String,
    dayOfWeek: Int,
    nowMin: Int,
    nowMs: Long
): Boolean {
    return schedules.any { it.isBlockingAt(packageName, dayOfWeek, nowMin, nowMs) }
}

fun isAlwaysBlockedNow(schedules: List<ScheduleEntity>, packageName: String, nowMs: Long = System.currentTimeMillis()): Boolean {
    return schedules.any {
        it.enabled && !it.isPausedAt(nowMs) && it.alwaysBlock && it.appliesTo(packageName)
    }
}

fun todayRange(): Pair<Long, Long> {
    val cal = Calendar.getInstance().apply {
        set(Calendar.HOUR_OF_DAY, 0); set(Calendar.MINUTE, 0)
        set(Calendar.SECOND, 0); set(Calendar.MILLISECOND, 0)
    }
    val s = cal.timeInMillis
    return s to s + 86_400_000L
}

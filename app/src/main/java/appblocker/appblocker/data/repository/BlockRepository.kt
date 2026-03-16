package appblocker.appblocker.data.repository

import android.content.Context
import android.util.Log
import appblocker.appblocker.data.database.AppDatabase
import appblocker.appblocker.data.entities.BlockRule
import appblocker.appblocker.data.entities.BlockRule.Companion.fromCalDay
import appblocker.appblocker.data.entities.BlockRule.Companion.normaliseDomain
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import java.util.Calendar

class BlockRepository private constructor(context: Context) {

    private val dao = AppDatabase.get(context).blockRuleDao()

    // ── In-memory cache so the service's 500 ms loop doesn't hammer the DB ───
    // Rules for the current day are cached and refreshed every 60 seconds.
    // When a mutation happens (add/delete/toggle) we invalidate immediately.

    @Volatile private var cachedDay:   Int             = -1
    @Volatile private var cachedRules: List<BlockRule> = emptyList()
    @Volatile private var cacheTime:   Long            = 0L
    private val CACHE_TTL_MS = 60_000L  // refresh rule list every 60 s

    private suspend fun rulesForToday(): List<BlockRule> {
        val cal  = Calendar.getInstance()
        val day  = fromCalDay(cal.get(Calendar.DAY_OF_WEEK))
        val now  = System.currentTimeMillis()

        if (day != cachedDay || now - cacheTime > CACHE_TTL_MS) {
            cachedRules = dao.enabledRulesForDay(day)
            cachedDay   = day
            cacheTime   = now
            Log.d("FocusGuard", "Rules refreshed: ${cachedRules.size} rules for day $day")
        }
        return cachedRules
    }

    private fun invalidateCache() {
        cacheTime = 0L   // next call to rulesForToday() will re-query
    }

    // ── Flow for UI ───────────────────────────────────────────────────────────

    fun allRules(): Flow<List<BlockRule>> = dao.allRules()

    // ── Mutations ─────────────────────────────────────────────────────────────

    suspend fun addRule(rule: BlockRule): Long {
        val toInsert = if (rule.domain != null)
            rule.copy(domain = normaliseDomain(rule.domain)) else rule

        val start = toInsert.startHour * 60 + toInsert.startMinute
        val end = toInsert.endHour * 60 + toInsert.endMinute

        val id = if (start >= end) {
            dao.insert(toInsert)
        } else {
            val overlaps = dao.allRules().first().filter { existing ->
                existing.id != toInsert.id &&
                    existing.dayOfWeek == toInsert.dayOfWeek &&
                    sameTarget(existing, toInsert) &&
                    !isOvernight(existing) &&
                    overlapsOrTouches(existing, toInsert)
            }

            if (overlaps.isEmpty()) {
                dao.insert(toInsert)
            } else {
                overlaps.forEach { dao.deleteById(it.id) }
                val mergedStart = minOf(start, overlaps.minOf { it.startHour * 60 + it.startMinute })
                val mergedEnd = maxOf(end, overlaps.maxOf { it.endHour * 60 + it.endMinute })
                dao.insert(
                    toInsert.copy(
                        id = 0,
                        label = listOf(toInsert.label, overlaps.firstNotNullOfOrNull { it.label.takeIf(String::isNotBlank) })
                            .firstOrNull { it!!.isNotBlank() }
                            .orEmpty(),
                        startHour = mergedStart / 60,
                        startMinute = mergedStart % 60,
                        endHour = mergedEnd / 60,
                        endMinute = mergedEnd % 60,
                        isEnabled = true,
                        pausedUntilMs = 0L
                    )
                )
            }
        }

        invalidateCache()
        return id
    }

    suspend fun deleteRule(id: Long) { dao.deleteById(id); invalidateCache() }

    suspend fun setRuleEnabled(id: Long, enabled: Boolean) {
        dao.setEnabled(id, enabled); invalidateCache()
    }

    suspend fun setRulePausedUntil(id: Long, pausedUntilMs: Long) {
        dao.setPausedUntil(id, pausedUntilMs)
        invalidateCache()
    }

    // ── Hot-path checks (called every 500 ms by AppMonitorService) ────────────

    suspend fun isAppBlockedNow(packageName: String): Boolean {
        val cal   = Calendar.getInstance()
        val nowMs = System.currentTimeMillis()
        val h     = cal.get(Calendar.HOUR_OF_DAY)
        val m     = cal.get(Calendar.MINUTE)
        val rules = rulesForToday()
        val hit   = rules.firstOrNull {
            it.packageName == packageName && it.isBlockingAt(h, m, nowMs)
        }
        if (hit != null) Log.d("FocusGuard", "BLOCKING $packageName (rule ${hit.id})")
        return hit != null
    }

    suspend fun isWebsiteBlockedNow(rawUrl: String): Boolean {
        val domain = normaliseDomain(rawUrl)
        val cal    = Calendar.getInstance()
        val nowMs  = System.currentTimeMillis()
        val h      = cal.get(Calendar.HOUR_OF_DAY)
        val m      = cal.get(Calendar.MINUTE)
        return rulesForToday().any { rule ->
            rule.domain != null
                && (domain == rule.domain || domain.endsWith(".${rule.domain}"))
                && rule.isBlockingAt(h, m, nowMs)
        }
    }

    companion object {
        @Volatile private var INSTANCE: BlockRepository? = null
        fun get(context: Context): BlockRepository =
            INSTANCE ?: synchronized(this) {
                INSTANCE ?: BlockRepository(context).also { INSTANCE = it }
            }
    }

    private fun sameTarget(a: BlockRule, b: BlockRule): Boolean =
        when {
            a.packageName != null && b.packageName != null -> a.packageName == b.packageName
            a.domain != null && b.domain != null -> a.domain == b.domain
            else -> false
        }

    private fun isOvernight(rule: BlockRule): Boolean {
        val start = rule.startHour * 60 + rule.startMinute
        val end = rule.endHour * 60 + rule.endMinute
        return start >= end
    }

    private fun overlapsOrTouches(a: BlockRule, b: BlockRule): Boolean {
        val aStart = a.startHour * 60 + a.startMinute
        val aEnd = a.endHour * 60 + a.endMinute
        val bStart = b.startHour * 60 + b.startMinute
        val bEnd = b.endHour * 60 + b.endMinute
        return aStart <= bEnd && bStart <= aEnd
    }
}

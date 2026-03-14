package appblocker.appblocker.data.repository

import android.content.Context
import android.util.Log
import appblocker.appblocker.data.database.AppDatabase
import appblocker.appblocker.data.entities.BlockRule
import appblocker.appblocker.data.entities.BlockRule.Companion.fromCalDay
import appblocker.appblocker.data.entities.BlockRule.Companion.normaliseDomain
import kotlinx.coroutines.flow.Flow
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
        val id = dao.insert(toInsert)
        invalidateCache()
        return id
    }

    suspend fun deleteRule(id: Long) { dao.deleteById(id); invalidateCache() }

    suspend fun setRuleEnabled(id: Long, enabled: Boolean) {
        dao.setEnabled(id, enabled); invalidateCache()
    }

    // ── Hot-path checks (called every 500 ms by AppMonitorService) ────────────

    suspend fun isAppBlockedNow(packageName: String): Boolean {
        val cal   = Calendar.getInstance()
        val h     = cal.get(Calendar.HOUR_OF_DAY)
        val m     = cal.get(Calendar.MINUTE)
        val rules = rulesForToday()
        val hit   = rules.firstOrNull { it.packageName == packageName && it.isActiveAt(h, m) }
        if (hit != null) Log.d("FocusGuard", "BLOCKING $packageName (rule ${hit.id})")
        return hit != null
    }

    suspend fun isWebsiteBlockedNow(rawUrl: String): Boolean {
        val domain = normaliseDomain(rawUrl)
        val cal    = Calendar.getInstance()
        val h      = cal.get(Calendar.HOUR_OF_DAY)
        val m      = cal.get(Calendar.MINUTE)
        return rulesForToday().any { rule ->
            rule.domain != null
                && (domain == rule.domain || domain.endsWith(".${rule.domain}"))
                && rule.isActiveAt(h, m)
        }
    }

    companion object {
        @Volatile private var INSTANCE: BlockRepository? = null
        fun get(context: Context): BlockRepository =
            INSTANCE ?: synchronized(this) {
                INSTANCE ?: BlockRepository(context).also { INSTANCE = it }
            }
    }
}

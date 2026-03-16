package appblocker.appblocker.data.dao

import androidx.room.*
import appblocker.appblocker.data.entities.BlockRule
import kotlinx.coroutines.flow.Flow

@Dao
interface BlockRuleDao {

    // ── UI consumption ────────────────────────────────────────────────────────
    @Query("SELECT * FROM block_rules ORDER BY dayOfWeek, startHour, startMinute")
    fun allRules(): Flow<List<BlockRule>>

    @Query("SELECT * FROM block_rules WHERE packageName = :pkg ORDER BY dayOfWeek, startHour")
    fun rulesForApp(pkg: String): Flow<List<BlockRule>>

    @Query("SELECT * FROM block_rules WHERE domain = :domain ORDER BY dayOfWeek, startHour")
    fun rulesForWebsite(domain: String): Flow<List<BlockRule>>

    // ── Service hot-path (called every ~1 s) ──────────────────────────────────
    @Query("SELECT * FROM block_rules WHERE isEnabled = 1 AND dayOfWeek = :day")
    suspend fun enabledRulesForDay(day: Int): List<BlockRule>

    // ── Mutations ─────────────────────────────────────────────────────────────
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insert(rule: BlockRule): Long

    @Update
    suspend fun update(rule: BlockRule)

    @Query("DELETE FROM block_rules WHERE id = :id")
    suspend fun deleteById(id: Long)

    @Query("UPDATE block_rules SET isEnabled = :enabled WHERE id = :id")
    suspend fun setEnabled(id: Long, enabled: Boolean)

    @Query("UPDATE block_rules SET pausedUntilMs = :pausedUntilMs WHERE id = :id")
    suspend fun setPausedUntil(id: Long, pausedUntilMs: Long)
}

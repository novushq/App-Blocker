package appblocker.appblocker.data.entities

import androidx.room.Entity
import androidx.room.PrimaryKey
import java.util.Calendar

@Entity(tableName = "block_rules")
data class BlockRule(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val packageName: String? = null,   // e.g. "com.instagram.android"
    val domain: String? = null,        // e.g. "instagram.com" (no www / scheme)
    val label: String = "",
    val dayOfWeek: Int,                // 1=Mon … 7=Sun
    val startHour: Int,                // 0–23
    val startMinute: Int,              // 0–59
    val endHour: Int,
    val endMinute: Int,
    val isEnabled: Boolean = true,
    val createdAt: Long = System.currentTimeMillis()
) {
    val isAppRule     get() = packageName != null
    val isWebsiteRule get() = domain != null

    /**
     * Returns true if [hour]:[minute] is inside the block window.
     *
     * Normal window  (start < end):  start ≤ now < end
     *   e.g. 09:00–17:00: blocks 09:00, 09:59, 16:59 — does NOT block 17:00
     *
     * Overnight window (start > end): now ≥ start  OR  now < end
     *   e.g. 22:00–06:00: blocks 22:00, 23:59, 00:00, 05:59 — does NOT block 06:00
     *
     * Using strict less-than for end so the end minute is the first UNBLOCKED minute.
     */
    fun isActiveAt(hour: Int, minute: Int): Boolean {
        if (!isEnabled) return false
        val now   = hour * 60 + minute
        val start = startHour * 60 + startMinute
        val end   = endHour   * 60 + endMinute
        return if (start < end)  now >= start && now < end     // normal
               else              now >= start || now < end      // overnight
    }

    fun timeLabel() = "%02d:%02d – %02d:%02d".format(startHour, startMinute, endHour, endMinute)

    companion object {
        val DAY_LABELS = listOf("Mon","Tue","Wed","Thu","Fri","Sat","Sun")

        fun normaliseDomain(raw: String): String =
            raw.trim().lowercase()
                .removePrefix("https://").removePrefix("http://")
                .removePrefix("www.").substringBefore("/")

        /** Calendar.DAY_OF_WEEK (Sun=1) → our 1=Mon … 7=Sun */
        fun fromCalDay(calDay: Int): Int = when (calDay) {
            Calendar.MONDAY    -> 1; Calendar.TUESDAY   -> 2
            Calendar.WEDNESDAY -> 3; Calendar.THURSDAY  -> 4
            Calendar.FRIDAY    -> 5; Calendar.SATURDAY  -> 6
            else               -> 7  // SUNDAY
        }
    }
}

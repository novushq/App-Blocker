package appblocker.appblocker

import appblocker.appblocker.data.entities.BlockRule
import appblocker.appblocker.shorts.mergeIntervals
import appblocker.appblocker.shorts.ScheduleEntity
import appblocker.appblocker.shorts.isAlwaysBlockedNow
import appblocker.appblocker.shorts.isBlockedNow
import appblocker.appblocker.shorts.hasStrictShortsKeywordSignal
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class BlockingLogicTest {

    @Test
    fun `strict shorts keyword signal rejects generic tab labels`() {
        assertFalse(hasStrictShortsKeywordSignal("Reels", listOf("reels")))
        assertFalse(hasStrictShortsKeywordSignal("Shorts", listOf("shorts")))
        assertFalse(hasStrictShortsKeywordSignal("For You", listOf("for you")))
    }

    @Test
    fun `strict shorts keyword signal accepts viewer context`() {
        assertTrue(hasStrictShortsKeywordSignal("Reels viewer", listOf("reels")))
        assertTrue(hasStrictShortsKeywordSignal("Watch shorts player", listOf("shorts")))
    }

    @Test
    fun `block rule respects pause window`() {
        val nowMs = 1_000_000L
        val rule = BlockRule(
            packageName = "com.instagram.android",
            label = "Instagram",
            dayOfWeek = 1,
            startHour = 9,
            startMinute = 0,
            endHour = 17,
            endMinute = 0,
            pausedUntilMs = nowMs + 60_000L
        )

        assertFalse(rule.isBlockingAt(hour = 10, minute = 30, nowMs = nowMs))
        assertTrue(rule.isBlockingAt(hour = 10, minute = 30, nowMs = nowMs + 120_000L))
    }

    @Test
    fun `shorts schedule blocks active timed window`() {
        val schedules = listOf(
            ScheduleEntity(
                packageName = "com.instagram.android",
                label = "Instagram Reels",
                daysOfWeek = "2,3,4,5,6",
                startMin = 9 * 60,
                endMin = 17 * 60
            )
        )

        assertTrue(
            isBlockedNow(
                schedules = schedules,
                packageName = "com.instagram.android",
                dayOfWeek = 2,
                nowMin = 10 * 60,
                nowMs = 1_000L
            )
        )
        assertFalse(
            isBlockedNow(
                schedules = schedules,
                packageName = "com.instagram.android",
                dayOfWeek = 7,
                nowMin = 10 * 60,
                nowMs = 1_000L
            )
        )
    }

    @Test
    fun `shorts always block ignores time but respects pause`() {
        val nowMs = 5_000L
        val paused = ScheduleEntity(
            packageName = "com.google.android.youtube",
            label = "YouTube Shorts",
            daysOfWeek = "1,2,3,4,5,6,7",
            startMin = 0,
            endMin = 0,
            alwaysBlock = true,
            pausedUntilMs = nowMs + 60_000L
        )
        val active = paused.copy(id = 2, pausedUntilMs = 0L)

        assertFalse(isAlwaysBlockedNow(listOf(paused), "com.google.android.youtube", nowMs))
        assertTrue(isAlwaysBlockedNow(listOf(active), "com.google.android.youtube", nowMs))
    }

    @Test
    fun `merge intervals combines overlaps and touching windows`() {
        val merged = mergeIntervals(
            listOf(
                14 * 60 to 16 * 60,
                15 * 60 to 17 * 60,
                17 * 60 to 18 * 60
            )
        )

        assertEquals(1, merged.size)
        assertEquals(14 * 60, merged.first().start)
        assertEquals(18 * 60, merged.first().end)
    }
}


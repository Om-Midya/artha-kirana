package com.artha.kirana.util

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.util.Calendar

class TimeRangeTest {

    private fun at(year: Int, month0: Int, day: Int, hour: Int, min: Int): Long =
        Calendar.getInstance().apply {
            set(year, month0, day, hour, min, 30)
            set(Calendar.MILLISECOND, 0)
        }.timeInMillis

    private val oneDay = 24L * 60 * 60 * 1000

    @Test
    fun startOfWeekIsMidnightOnOrBeforeToday_withinSevenDays() {
        val now = at(2026, Calendar.JUNE, 17, 14, 5) // a Wednesday
        val wk = TimeRange.startOfWeek(now)
        val today = TimeRange.startOfToday(now)
        assertTrue("week start <= today start", wk <= today)
        assertTrue("within 7 days", today - wk < 7 * oneDay)
        assertEquals(wk, TimeRange.startOfToday(wk))
    }

    @Test
    fun startOfMonthIsFirstDayMidnight() {
        val now = at(2026, Calendar.JUNE, 17, 14, 5)
        val m = TimeRange.startOfMonth(now)
        val cal = Calendar.getInstance().apply { timeInMillis = m }
        assertEquals(1, cal.get(Calendar.DAY_OF_MONTH))
        assertEquals(Calendar.JUNE, cal.get(Calendar.MONTH))
        assertEquals(0, cal.get(Calendar.HOUR_OF_DAY))
        assertEquals(m, TimeRange.startOfToday(m))
    }

    @Test
    fun `startOfDay is idempotent`() {
        val now = System.currentTimeMillis()
        val s = TimeRange.startOfDay(now)
        assertEquals(s, TimeRange.startOfDay(s))
    }

    @Test
    fun `next then prev returns same day`() {
        val s = TimeRange.startOfDay(System.currentTimeMillis())
        assertEquals(s, TimeRange.prevDayStart(TimeRange.nextDayStart(s)))
    }

    @Test
    fun `nextDayStart is a later day start`() {
        val s = TimeRange.startOfDay(System.currentTimeMillis())
        val n = TimeRange.nextDayStart(s)
        assertTrue(n > s)
        assertEquals(n, TimeRange.startOfDay(n))
    }

    @Test
    fun `localDayStartFromUtcMillis yields a local midnight`() {
        val out = TimeRange.localDayStartFromUtcMillis(System.currentTimeMillis())
        assertEquals(out, TimeRange.startOfDay(out))
    }

    @Test
    fun last7DayBucketsAreContiguousAndEndToday() {
        val now = at(2026, Calendar.JUNE, 17, 14, 5)
        val buckets = TimeRange.last7DayBuckets(now)
        assertEquals(7, buckets.size)
        for (i in 0 until buckets.size - 1) {
            assertEquals(buckets[i].endExclusive, buckets[i + 1].start)
            assertTrue(buckets[i].start < buckets[i + 1].start)
        }
        assertEquals(TimeRange.startOfToday(now), buckets.last().start)
        assertTrue(TimeRange.startOfToday(now) - buckets.first().start in (5 * oneDay)..(7 * oneDay))
    }
}

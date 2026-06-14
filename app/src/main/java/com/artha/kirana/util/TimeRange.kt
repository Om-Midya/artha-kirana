package com.artha.kirana.util

import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Locale
import java.util.TimeZone

/** A single local-day window [start, endExclusive) with a short weekday label. */
data class DayBucket(val start: Long, val endExclusive: Long, val label: String)

object TimeRange {
    /** Epoch millis for 00:00:00.000 of the day containing [now]. */
    fun startOfToday(now: Long = System.currentTimeMillis()): Long {
        val cal = Calendar.getInstance().apply {
            timeInMillis = now
            set(Calendar.HOUR_OF_DAY, 0)
            set(Calendar.MINUTE, 0)
            set(Calendar.SECOND, 0)
            set(Calendar.MILLISECOND, 0)
        }
        return cal.timeInMillis
    }

    /** Midnight of the Monday on or before [now] (ISO week, locale-independent). */
    fun startOfWeek(now: Long = System.currentTimeMillis()): Long {
        val cal = Calendar.getInstance().apply { timeInMillis = startOfToday(now) }
        val daysFromMonday = (cal.get(Calendar.DAY_OF_WEEK) - Calendar.MONDAY + 7) % 7
        cal.add(Calendar.DAY_OF_MONTH, -daysFromMonday)
        return cal.timeInMillis
    }

    /** Midnight of the first day of the month containing [now]. */
    fun startOfMonth(now: Long = System.currentTimeMillis()): Long {
        val cal = Calendar.getInstance().apply {
            timeInMillis = startOfToday(now)
            set(Calendar.DAY_OF_MONTH, 1)
        }
        return cal.timeInMillis
    }

    /** Local midnight of the day containing [millis] (alias of startOfToday for any day). */
    fun startOfDay(millis: Long): Long = startOfToday(millis)

    /** Local midnight of the day AFTER the day starting at [dayStart] (DST-safe). */
    fun nextDayStart(dayStart: Long): Long =
        Calendar.getInstance().apply { timeInMillis = dayStart; add(Calendar.DAY_OF_MONTH, 1) }.timeInMillis

    /** Local midnight of the day BEFORE [dayStart] (DST-safe). */
    fun prevDayStart(dayStart: Long): Long =
        Calendar.getInstance().apply { timeInMillis = dayStart; add(Calendar.DAY_OF_MONTH, -1) }.timeInMillis

    /** Material3 DatePicker returns a UTC-midnight epoch; convert it to the LOCAL day start. */
    fun localDayStartFromUtcMillis(utcMillis: Long): Long {
        val utc = Calendar.getInstance(TimeZone.getTimeZone("UTC")).apply { timeInMillis = utcMillis }
        return Calendar.getInstance().apply {
            clear()
            set(utc.get(Calendar.YEAR), utc.get(Calendar.MONTH), utc.get(Calendar.DAY_OF_MONTH), 0, 0, 0)
        }.timeInMillis
    }

    /** Short Hindi label for a day, e.g. "सोम · 14 जून". */
    fun dayLabel(dayStart: Long): String =
        SimpleDateFormat("EEE · d MMM", Locale("hi", "IN")).format(dayStart)

    /** The 7 local-day buckets ending with today, oldest first. */
    fun last7DayBuckets(now: Long = System.currentTimeMillis()): List<DayBucket> {
        val labelFmt = SimpleDateFormat("EEE", Locale.getDefault())
        val today = startOfToday(now)
        return (6 downTo 0).map { back ->
            val startCal = Calendar.getInstance().apply {
                timeInMillis = today
                add(Calendar.DAY_OF_MONTH, -back)
            }
            val start = startCal.timeInMillis
            val end = Calendar.getInstance().apply {
                timeInMillis = start
                add(Calendar.DAY_OF_MONTH, 1)
            }.timeInMillis
            DayBucket(start = start, endExclusive = end, label = labelFmt.format(start))
        }
    }
}

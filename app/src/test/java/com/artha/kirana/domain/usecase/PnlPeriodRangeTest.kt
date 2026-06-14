package com.artha.kirana.domain.usecase

import com.artha.kirana.domain.model.PnlPeriod
import com.artha.kirana.util.TimeRange
import org.junit.Assert.assertEquals
import org.junit.Test

class PnlPeriodRangeTest {
    private val now = 1_700_000_000_000L

    @Test
    fun mapsEachPeriodToItsStart() {
        assertEquals(TimeRange.startOfToday(now), PnlPeriod.TODAY.startFrom(now))
        assertEquals(TimeRange.startOfWeek(now), PnlPeriod.THIS_WEEK.startFrom(now))
        assertEquals(TimeRange.startOfMonth(now), PnlPeriod.THIS_MONTH.startFrom(now))
    }
}

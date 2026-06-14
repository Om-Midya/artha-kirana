package com.artha.kirana.domain.usecase

import com.artha.kirana.domain.model.PnlPeriod
import org.junit.Assert.assertEquals
import org.junit.Test

class PnlPeriodDetectorTest {

    @Test
    fun defaultsToToday() {
        assertEquals(PnlPeriod.TODAY, PnlPeriodDetector.detect("आज की कमाई कितनी हुई"))
        assertEquals(PnlPeriod.TODAY, PnlPeriodDetector.detect("kitna kamaya"))
    }

    @Test
    fun detectsWeek() {
        assertEquals(PnlPeriod.THIS_WEEK, PnlPeriodDetector.detect("इस हफ्ते का मुनाफा"))
        assertEquals(PnlPeriod.THIS_WEEK, PnlPeriodDetector.detect("इस सप्ताह की बिक्री"))
        assertEquals(PnlPeriod.THIS_WEEK, PnlPeriodDetector.detect("this week profit"))
    }

    @Test
    fun detectsMonth() {
        assertEquals(PnlPeriod.THIS_MONTH, PnlPeriodDetector.detect("इस महीने की कमाई"))
        assertEquals(PnlPeriod.THIS_MONTH, PnlPeriodDetector.detect("this month sales"))
    }

    @Test
    fun reportDefaultsToMonthButHonorsExplicitTodayAndWeek() {
        // bare "what sold most" → month (not today, which is often empty)
        assertEquals(PnlPeriod.THIS_MONTH, PnlPeriodDetector.detectForReport("सबसे ज्यादा क्या बिका"))
        assertEquals(PnlPeriod.THIS_MONTH, PnlPeriodDetector.detectForReport("इस महीने के टॉप आइटम"))
        assertEquals(PnlPeriod.TODAY, PnlPeriodDetector.detectForReport("आज सबसे ज्यादा क्या बिका"))
        assertEquals(PnlPeriod.THIS_WEEK, PnlPeriodDetector.detectForReport("इस हफ्ते के टॉप आइटम"))
    }
}

package com.artha.kirana.domain.usecase

import com.artha.kirana.data.db.entity.SaleEntity
import org.junit.Assert.assertEquals
import org.junit.Test
import java.util.Calendar
import java.util.GregorianCalendar

class DayOfWeekBucketingTest {

    private fun tsOn(year: Int, month0: Int, day: Int): Long =
        GregorianCalendar(year, month0, day, 12, 0, 0).timeInMillis

    private fun sale(ts: Long, amount: Double, type: String = "cash") =
        SaleEntity(amount = amount, type = type, inputMethod = "typed", timestamp = ts)

    @Test
    fun bucketsRevenueByWeekdaySundayFirstAndExcludesRepayments() {
        // 2024-06-14 is a Friday (Calendar.FRIDAY = 6), 2024-06-16 is a Sunday (1).
        val friday = tsOn(2024, Calendar.JUNE, 14)
        val sunday = tsOn(2024, Calendar.JUNE, 16)
        val sales = listOf(
            sale(friday, 100.0),
            sale(friday, 50.0),
            sale(sunday, 30.0),
            sale(friday, 999.0, type = "repayment"), // excluded
        )

        val buckets = bucketRevenueByWeekday(sales)

        assertEquals(7, buckets.size)
        assertEquals(30.0, buckets[0], 0.001)   // index 0 = Sunday
        assertEquals(150.0, buckets[5], 0.001)  // index 5 = Friday
        assertEquals(0.0, buckets[1], 0.001)    // Monday empty
    }
}

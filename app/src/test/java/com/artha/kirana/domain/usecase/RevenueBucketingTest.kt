package com.artha.kirana.domain.usecase

import com.artha.kirana.data.db.entity.SaleEntity
import com.artha.kirana.util.DayBucket
import org.junit.Assert.assertEquals
import org.junit.Test

class RevenueBucketingTest {

    private fun bucket(start: Long, label: String) =
        DayBucket(start = start, endExclusive = start + 1000, label = label)

    private fun sale(amount: Double, type: String, ts: Long) =
        SaleEntity(amount = amount, type = type, inputMethod = "typed", timestamp = ts)

    @Test
    fun sumsSalesIntoMatchingBucketAndExcludesRepayments() {
        val buckets = listOf(bucket(0, "Mon"), bucket(1000, "Tue"))
        val sales = listOf(
            sale(100.0, "cash", 10),
            sale(50.0, "credit", 20),
            sale(30.0, "repayment", 30),
            sale(70.0, "cash", 1500),
        )
        val result = bucketDailyRevenue(sales, buckets)
        assertEquals(2, result.size)
        assertEquals(150.0, result[0].amount, 0.001)
        assertEquals("Mon", result[0].dayLabel)
        assertEquals(70.0, result[1].amount, 0.001)
    }

    @Test
    fun emptyBucketsAreZero() {
        val buckets = listOf(bucket(0, "Mon"))
        val result = bucketDailyRevenue(emptyList(), buckets)
        assertEquals(1, result.size)
        assertEquals(0.0, result[0].amount, 0.001)
    }
}

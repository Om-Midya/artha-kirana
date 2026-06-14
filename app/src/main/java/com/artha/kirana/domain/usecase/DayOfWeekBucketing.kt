package com.artha.kirana.domain.usecase

import com.artha.kirana.data.db.entity.SaleEntity
import java.util.Calendar

/**
 * Sums non-repayment sale amounts into 7 weekday buckets. Index 0 = Sunday … 6 = Saturday
 * (matching Calendar.DAY_OF_WEEK - 1), using the device's default timezone — consistent with
 * the rest of the app's time handling (see RevenueBucketing).
 */
internal fun bucketRevenueByWeekday(sales: List<SaleEntity>): DoubleArray {
    val buckets = DoubleArray(7)
    val cal = Calendar.getInstance()
    for (s in sales) {
        if (s.type == "repayment") continue
        cal.timeInMillis = s.timestamp
        val idx = cal.get(Calendar.DAY_OF_WEEK) - 1 // SUNDAY(1)->0 … SATURDAY(7)->6
        buckets[idx] += s.amount
    }
    return buckets
}

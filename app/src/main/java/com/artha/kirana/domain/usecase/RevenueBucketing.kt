package com.artha.kirana.domain.usecase

import com.artha.kirana.data.db.entity.SaleEntity
import com.artha.kirana.domain.model.DailyRevenue
import com.artha.kirana.util.DayBucket

/**
 * Sums non-repayment sale amounts into their local-day [buckets].
 * Repayments are not new revenue and are excluded (matches SalesDao.revenueBetween).
 */
fun bucketDailyRevenue(sales: List<SaleEntity>, buckets: List<DayBucket>): List<DailyRevenue> =
    buckets.map { b ->
        val total = sales
            .filter { it.type != "repayment" && it.timestamp >= b.start && it.timestamp < b.endExclusive }
            .sumOf { it.amount }
        DailyRevenue(dayLabel = b.label, amount = total)
    }

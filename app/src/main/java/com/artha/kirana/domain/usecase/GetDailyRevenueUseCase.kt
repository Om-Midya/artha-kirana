package com.artha.kirana.domain.usecase

import com.artha.kirana.domain.model.DailyRevenue
import com.artha.kirana.domain.repository.SalesRepository
import com.artha.kirana.util.TimeRange
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import javax.inject.Inject

/** Last-7-days revenue as chart bars, bucketed by local day. */
class GetDailyRevenueUseCase @Inject constructor(
    private val sales: SalesRepository,
) {
    operator fun invoke(now: Long = System.currentTimeMillis()): Flow<List<DailyRevenue>> {
        val buckets = TimeRange.last7DayBuckets(now)
        return sales.observeSince(buckets.first().start).map { bucketDailyRevenue(it, buckets) }
    }
}

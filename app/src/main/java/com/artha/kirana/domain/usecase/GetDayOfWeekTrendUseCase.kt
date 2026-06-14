package com.artha.kirana.domain.usecase

import com.artha.kirana.domain.repository.SalesRepository
import javax.inject.Inject

/**
 * Revenue summed into 7 weekday buckets (index 0 = Sunday) over [start, end].
 * Reuses [bucketRevenueByWeekday]; SalesRepository.between returns the window.
 */
class GetDayOfWeekTrendUseCase @Inject constructor(
    private val sales: SalesRepository,
) {
    suspend operator fun invoke(start: Long, end: Long): DoubleArray =
        bucketRevenueByWeekday(sales.between(start, end))
}

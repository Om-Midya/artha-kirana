package com.artha.kirana.domain.usecase

import com.artha.kirana.domain.model.TopSellerRow
import com.artha.kirana.domain.repository.SalesRepository
import javax.inject.Inject

class GetTopSellersUseCase @Inject constructor(
    private val sales: SalesRepository,
) {
    suspend operator fun invoke(start: Long, end: Long): List<TopSellerRow> =
        sales.topSellers(start, end)
}

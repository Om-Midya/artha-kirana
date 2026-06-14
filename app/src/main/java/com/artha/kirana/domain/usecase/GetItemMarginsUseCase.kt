package com.artha.kirana.domain.usecase

import com.artha.kirana.domain.model.ItemMarginRow
import com.artha.kirana.domain.repository.SalesRepository
import javax.inject.Inject

class GetItemMarginsUseCase @Inject constructor(
    private val sales: SalesRepository,
) {
    suspend operator fun invoke(start: Long, end: Long): List<ItemMarginRow> =
        sales.itemMargins(start, end)
}

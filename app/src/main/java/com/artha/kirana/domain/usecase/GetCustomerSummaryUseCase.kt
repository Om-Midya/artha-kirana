package com.artha.kirana.domain.usecase

import com.artha.kirana.domain.model.CustomerSummary
import com.artha.kirana.domain.repository.KhataRepository
import com.artha.kirana.domain.repository.SalesRepository
import javax.inject.Inject

class GetCustomerSummaryUseCase @Inject constructor(
    private val sales: SalesRepository,
    private val khata: KhataRepository,
) {
    suspend operator fun invoke(customerId: Long): CustomerSummary =
        CustomerSummary(
            customerId = customerId,
            lifetimeValue = sales.lifetimeValue(customerId),
            outstanding = khata.balanceForCustomer(customerId),
        )
}

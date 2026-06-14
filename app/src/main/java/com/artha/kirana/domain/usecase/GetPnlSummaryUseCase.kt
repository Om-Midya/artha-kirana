package com.artha.kirana.domain.usecase

import com.artha.kirana.domain.model.PnlPeriod
import com.artha.kirana.domain.model.PnlSummary
import com.artha.kirana.domain.repository.KhataRepository
import com.artha.kirana.domain.repository.SalesRepository
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.combine
import javax.inject.Inject

/** Live P&L for a period: combines revenue, COGS, cash, and outstanding into one summary. */
class GetPnlSummaryUseCase @Inject constructor(
    private val sales: SalesRepository,
    private val khata: KhataRepository,
) {
    operator fun invoke(
        period: PnlPeriod,
        now: Long = System.currentTimeMillis(),
    ): Flow<PnlSummary> {
        val start = period.startFrom(now)
        val end = Long.MAX_VALUE
        return combine(
            sales.revenueBetween(start, end),
            sales.cogsBetween(start, end),
            sales.cashBetween(start, end),
            khata.totalOutstanding(),
        ) { revenue, cogs, cash, outstanding ->
            PnlSummary(
                grossRevenue = revenue,
                cogs = cogs,
                grossProfit = revenue - cogs,
                cashCollected = cash,
                totalOutstanding = outstanding,
                period = period,
            )
        }
    }
}

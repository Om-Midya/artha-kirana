package com.artha.kirana.domain.model

/** What [com.artha.kirana.domain.usecase.RouteAssistantUseCase] produced for one utterance. */
sealed interface AssistantResult {
    /** A sale (or multi-item sale) to confirm before writing. */
    data class SaleDraft(val entries: List<SaleEntry>) : AssistantResult

    /** A khata repayment to confirm before writing. */
    data class PaymentDraft(val party: String?, val amount: Double?) : AssistantResult

    /** A read-only P&L answer (no confirmation needed). */
    data class PnlAnswer(val summary: PnlSummary) : AssistantResult

    /** Read-only top-sellers ranking for a period. */
    data class TopSellersAnswer(val period: PnlPeriod, val rows: List<TopSellerRow>) : AssistantResult

    /** Read-only one-customer summary. */
    data class CustomerAnswer(val name: String, val summary: CustomerSummary) : AssistantResult

    /** Read-only revenue-by-weekday (index 0 = Sunday). */
    data class DayTrendAnswer(val period: PnlPeriod, val buckets: DoubleArray) : AssistantResult

    /** A plain text reply (acks, "didn't understand", greetings). */
    data class Reply(val text: String) : AssistantResult

    /** The on-device LLM server was unreachable. */
    data object Unavailable : AssistantResult
}

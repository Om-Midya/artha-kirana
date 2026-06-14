package com.artha.kirana.domain.usecase

import com.artha.kirana.data.llm.IntentRouter
import com.artha.kirana.data.llm.LlmEngine
import com.artha.kirana.domain.model.AssistantIntent
import com.artha.kirana.domain.model.AssistantResult
import com.artha.kirana.domain.repository.CustomerRepository
import com.artha.kirana.util.HindiNumbers
import kotlinx.coroutines.flow.first
import javax.inject.Inject

/**
 * Stateless per-message router: classify the utterance, then dispatch to the matching
 * extractor (sale/payment) or read-only query (P&L). Returns an [AssistantResult] the UI
 * renders as a chat message. Reuses ParseSaleEntryUseCase, LlmEngine, GetPnlSummaryUseCase.
 */
class RouteAssistantUseCase @Inject constructor(
    private val intentRouter: IntentRouter,
    private val parseSale: ParseSaleEntryUseCase,
    private val engine: LlmEngine,
    private val getPnl: GetPnlSummaryUseCase,
    private val getTopSellers: GetTopSellersUseCase,
    private val getCustomerSummary: GetCustomerSummaryUseCase,
    private val getDayTrend: GetDayOfWeekTrendUseCase,
    private val customers: CustomerRepository,
) {
    suspend operator fun invoke(text: String): AssistantResult {
        val intent = intentRouter.classify(text).getOrElse { return AssistantResult.Unavailable }
        return when (intent) {
            AssistantIntent.LOG_SALE -> parseSale(text).fold(
                onSuccess = { entries ->
                    if (entries.isEmpty()) AssistantResult.Reply(COULD_NOT_UNDERSTAND)
                    else AssistantResult.SaleDraft(entries)
                },
                onFailure = { AssistantResult.Unavailable },
            )

            AssistantIntent.RECORD_PAYMENT -> engine.parsePayment(HindiNumbers.normalize(text)).fold(
                onSuccess = { p ->
                    if (p.party == null && p.amount == null) AssistantResult.Reply(COULD_NOT_UNDERSTAND)
                    else AssistantResult.PaymentDraft(p.party, p.amount)
                },
                onFailure = { AssistantResult.Unavailable },
            )

            AssistantIntent.QUERY_PNL ->
                AssistantResult.PnlAnswer(getPnl(PnlPeriodDetector.detect(text)).first())

            AssistantIntent.QUERY_TOP_SELLERS -> {
                val (period, start) = periodWindow(text)
                AssistantResult.TopSellersAnswer(period, getTopSellers(start, Long.MAX_VALUE))
            }

            AssistantIntent.QUERY_DAY_TREND -> {
                val (period, start) = periodWindow(text)
                AssistantResult.DayTrendAnswer(period, getDayTrend(start, Long.MAX_VALUE))
            }

            AssistantIntent.QUERY_CUSTOMER -> engine.extractCustomerName(text).fold(
                onSuccess = { name ->
                    if (name.isNullOrBlank()) AssistantResult.Reply(ASK_WHICH_CUSTOMER)
                    else customers.findByName(name)?.let { c ->
                        AssistantResult.CustomerAnswer(c.name, getCustomerSummary(c.id))
                    } ?: AssistantResult.Reply(customerNotFound(name))
                },
                onFailure = { AssistantResult.Unavailable },
            )

            AssistantIntent.UNKNOWN -> AssistantResult.Reply(COULD_NOT_UNDERSTAND)
        }
    }

    /**
     * Detected period + its window-start timestamp (end is always Long.MAX_VALUE). Uses
     * [PnlPeriodDetector.detectForReport] so a bare ranking question (no time word) defaults to
     * THIS_MONTH rather than TODAY — "what sold most" / "busiest day" means recent/overall.
     */
    private fun periodWindow(text: String): Pair<com.artha.kirana.domain.model.PnlPeriod, Long> {
        val period = PnlPeriodDetector.detectForReport(text)
        return period to period.startFrom(System.currentTimeMillis())
    }

    /** Preload the on-device LLM (intent prefix cache) so the first message is fast. Safe if offline. */
    suspend fun warmUp() = intentRouter.warmUp()

    companion object {
        const val COULD_NOT_UNDERSTAND = "समझ नहीं आया — दोबारा कहें (जैसे: 'दो किलो चावल अस्सी का')।"
        const val ASK_WHICH_CUSTOMER = "किस ग्राहक का हिसाब? नाम बताएँ।"
        fun customerNotFound(name: String) = "ग्राहक '$name' नहीं मिला।"
    }
}

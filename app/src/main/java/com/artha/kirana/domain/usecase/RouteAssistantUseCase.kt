package com.artha.kirana.domain.usecase

import com.artha.kirana.data.llm.IntentRouter
import com.artha.kirana.data.llm.LlmEngine
import com.artha.kirana.domain.model.AssistantIntent
import com.artha.kirana.domain.model.AssistantResult
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

            AssistantIntent.UNKNOWN -> AssistantResult.Reply(COULD_NOT_UNDERSTAND)
        }
    }

    companion object {
        const val COULD_NOT_UNDERSTAND = "समझ नहीं आया — दोबारा कहें (जैसे: 'दो किलो चावल अस्सी का')।"
    }
}

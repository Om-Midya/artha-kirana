package com.artha.kirana.ui.assistant

import com.artha.kirana.domain.model.AgentVisual
import com.artha.kirana.domain.model.PnlSummary
import com.artha.kirana.domain.model.SaleEntry

/** Lifecycle of an inline confirm card. */
enum class DraftStatus { Pending, Confirmed, Cancelled }

/** One row in the Assistant chat thread. */
sealed interface ChatMessage {
    val id: Long

    data class User(override val id: Long, val text: String) : ChatMessage
    data class Reply(override val id: Long, val text: String) : ChatMessage
    data class SaleDraft(
        override val id: Long,
        val entries: List<SaleEntry>,
        val status: DraftStatus = DraftStatus.Pending,
    ) : ChatMessage
    data class PaymentDraft(
        override val id: Long,
        val party: String?,
        val amount: Double?,
        val status: DraftStatus = DraftStatus.Pending,
    ) : ChatMessage
    data class PnlAnswer(override val id: Long, val summary: PnlSummary) : ChatMessage
    /** An agent text answer with optional structured visuals (charts/cards). */
    data class AgentAnswer(override val id: Long, val text: String, val visuals: List<AgentVisual>) : ChatMessage
}

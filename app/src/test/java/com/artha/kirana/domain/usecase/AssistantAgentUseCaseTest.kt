package com.artha.kirana.domain.usecase

import com.artha.kirana.data.llm.ShopDataTools
import com.artha.kirana.data.remote.CloudChatClient
import com.artha.kirana.data.remote.dto.AgentFunctionCall
import com.artha.kirana.data.remote.dto.AgentMessage
import com.artha.kirana.data.remote.dto.AgentToolCall
import com.artha.kirana.domain.model.AssistantResult
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.every
import io.mockk.mockk
import kotlinx.coroutines.test.runTest
import kotlinx.serialization.json.buildJsonArray
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class AssistantAgentUseCaseTest {

    private val cloud = mockk<CloudChatClient>()
    private val shopTools = mockk<ShopDataTools>()

    private val useCase = AssistantAgentUseCase(cloud, shopTools)

    init {
        every { shopTools.definitions } returns buildJsonArray { }
    }

    // ── helpers ───────────────────────────────────────────────────────────────

    private fun toolTurn(name: String, args: String) = AgentMessage(
        role = "assistant",
        content = null,
        toolCalls = listOf(AgentToolCall(id = "c1", function = AgentFunctionCall(name = name, arguments = args))),
    )

    private fun textTurn(t: String) = AgentMessage(role = "assistant", content = t)

    // ── tests ─────────────────────────────────────────────────────────────────

    @Test
    fun `final text immediately returns AgentAnswer`() = runTest {
        coEvery { cloud.completeWithTools(any(), any(), any()) } returns textTurn("आज ₹350 की बिक्री हुई।")

        val result = useCase.run(emptyList(), "आज कितना बिका?")

        assertTrue(result is AssistantResult.AgentAnswer)
        assertEquals("आज ₹350 की बिक्री हुई।", (result as AssistantResult.AgentAnswer).text)
    }

    @Test
    fun `read tool then final text returns AgentAnswer and invokes tool`() = runTest {
        coEvery { shopTools.execute("get_pnl", any()) } returns """{"profit":170}"""
        coEvery { cloud.completeWithTools(any(), any(), any()) } returnsMany listOf(
            toolTurn("get_pnl", """{"period":"today"}"""),
            textTurn("आज का मुनाफा ₹170 है।"),
        )

        val result = useCase.run(emptyList(), "आज का मुनाफा बताओ")

        assertTrue(result is AssistantResult.AgentAnswer)
        assertEquals("आज का मुनाफा ₹170 है।", (result as AssistantResult.AgentAnswer).text)
        coVerify { shopTools.execute("get_pnl", any()) }
    }

    @Test
    fun `propose_sale terminates loop and returns SaleDraft`() = runTest {
        val args = """{"entries":[{"item":"chawal","qty":"2 kg","amount":80,"type":"credit","party":"Ramesh"}]}"""
        coEvery { cloud.completeWithTools(any(), any(), any()) } returns toolTurn("propose_sale", args)

        val result = useCase.run(emptyList(), "2 kg chawal 80 rupees udhaar Ramesh")

        assertTrue(result is AssistantResult.SaleDraft)
        val draft = result as AssistantResult.SaleDraft
        assertEquals(1, draft.entries.size)
        assertEquals("chawal", draft.entries[0].item)
        assertEquals("2 kg", draft.entries[0].qty)
        assertEquals(80.0, draft.entries[0].amount!!, 0.001)
        assertEquals("credit", draft.entries[0].type)
        assertEquals("Ramesh", draft.entries[0].party)
        // Must terminate after exactly one cloud call — no second round-trip
        coVerify(exactly = 1) { cloud.completeWithTools(any(), any(), any()) }
    }

    @Test
    fun `propose_payment terminates loop and returns PaymentDraft`() = runTest {
        val args = """{"party":"Ramesh","amount":50}"""
        coEvery { cloud.completeWithTools(any(), any(), any()) } returns toolTurn("propose_payment", args)

        val result = useCase.run(emptyList(), "Ramesh ne 50 diye")

        assertTrue(result is AssistantResult.PaymentDraft)
        val draft = result as AssistantResult.PaymentDraft
        assertEquals("Ramesh", draft.party)
        assertEquals(50.0, draft.amount!!, 0.001)
        coVerify(exactly = 1) { cloud.completeWithTools(any(), any(), any()) }
    }
}

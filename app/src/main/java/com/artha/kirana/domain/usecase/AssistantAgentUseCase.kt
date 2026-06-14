package com.artha.kirana.domain.usecase

import com.artha.kirana.data.llm.ShopDataTools
import com.artha.kirana.data.remote.CloudChatClient
import com.artha.kirana.data.remote.dto.AgentMessage
import com.artha.kirana.domain.model.AgentVisual
import com.artha.kirana.domain.model.AgentVisuals
import com.artha.kirana.domain.model.AssistantResult
import com.artha.kirana.domain.model.SaleEntry
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.add
import kotlinx.serialization.json.addJsonObject
import kotlinx.serialization.json.buildJsonArray
import kotlinx.serialization.json.put
import kotlinx.serialization.json.putJsonArray
import kotlinx.serialization.json.putJsonObject
import javax.inject.Inject

/**
 * Agentic Assistant: gives a cloud model (Haiku) a toolbox of read-only shop-data tools plus two
 * action-draft tools, runs the OpenAI-style tool-calling loop, and returns an [AssistantResult].
 * Cloud-only — propagates [com.artha.kirana.data.remote.LlmUnavailableException] so the router can
 * fall back to the on-device intent classifier. Reads never mutate; the two action tools terminate
 * the loop into a confirm card (the actual write happens only when the user taps Confirm).
 */
class AssistantAgentUseCase @Inject constructor(
    private val cloud: CloudChatClient,
    private val shopTools: ShopDataTools,
) {
    private val json = Json { ignoreUnknownKeys = true; isLenient = true; coerceInputValues = true }

    /** [history] = prior turns (system excluded); [userText] = the new message. */
    suspend fun run(history: List<AgentMessage>, userText: String): AssistantResult {
        val messages = mutableListOf<AgentMessage>()
        messages += AgentMessage(role = "system", content = SYSTEM_PROMPT)
        messages += history
        messages += AgentMessage(role = "user", content = userText)
        val tools = combinedTools()
        // Accumulate visuals keyed by tool name; later calls overwrite earlier ones (same tool).
        val visuals = LinkedHashMap<String, AgentVisual>()

        repeat(MAX_HOPS) { hop ->
            val reply = cloud.completeWithTools(messages, tools)
            val calls = reply.toolCalls
            timber.log.Timber.i("Artha-agent hop=%d calls=%s content=%s", hop, calls?.joinToString { it.function.name }, reply.content?.take(60))
            if (calls.isNullOrEmpty()) {
                val text = reply.content?.trim().orEmpty().ifEmpty { FALLBACK_REPLY }
                return AssistantResult.AgentAnswer(text, visuals.values.toList().takeLast(3))
            }
            // Action tools terminate the loop BEFORE echoing (so we never leave a tool_call unanswered).
            calls.firstOrNull { it.function.name == TOOL_PROPOSE_SALE }?.let { call ->
                val entries = parseSaleEntries(call.function.arguments)
                return if (entries.isNotEmpty()) AssistantResult.SaleDraft(entries)
                else AssistantResult.Reply(COULD_NOT_UNDERSTAND)
            }
            calls.firstOrNull { it.function.name == TOOL_PROPOSE_PAYMENT }?.let { call ->
                val p = parsePayment(call.function.arguments)
                return if (p != null) AssistantResult.PaymentDraft(p.first, p.second)
                else AssistantResult.Reply(COULD_NOT_UNDERSTAND)
            }
            // Read tools: echo the assistant turn, then add ONE tool result per call.
            messages += reply
            for (call in calls) {
                val result = shopTools.execute(call.function.name, call.function.arguments)
                timber.log.Timber.i("Artha-agent tool=%s args=%s -> %s", call.function.name, call.function.arguments.take(40), result.take(100))
                AgentVisuals.fromTool(call.function.name, result)?.let { visuals[call.function.name] = it }
                messages += AgentMessage(role = "tool", toolCallId = call.id, content = result)
            }
        }
        // Hops exhausted (model kept exploring) — force ONE final text answer from the data already
        // gathered (tool_choice=none blocks further tool calls), so the user always gets a real reply.
        val forcedResult = runCatching {
            cloud.completeWithTools(messages, tools, toolChoice = "none")
        }
        timber.log.Timber.i("Artha-agent forced: err=%s content=%s", forcedResult.exceptionOrNull()?.message, forcedResult.getOrNull()?.content?.take(80))
        val forced = forcedResult.getOrNull()?.content?.trim()
        return AssistantResult.AgentAnswer(
            forced?.takeIf { it.isNotEmpty() } ?: FALLBACK_REPLY,
            visuals.values.toList().takeLast(3),
        )
    }

    private fun combinedTools(): JsonArray = buildJsonArray {
        shopTools.definitions.forEach { add(it) }
        addJsonObject {
            put("type", "function")
            putJsonObject("function") {
                put("name", TOOL_PROPOSE_SALE)
                put("description", "Draft a sale/credit entry for the shopkeeper to confirm. Use when they want to RECORD a sale or udhaar (not when asking a question). type: cash | credit | repayment.")
                putJsonObject("parameters") {
                    put("type", "object")
                    putJsonObject("properties") {
                        putJsonObject("entries") {
                            put("type", "array")
                            putJsonObject("items") {
                                put("type", "object")
                                putJsonObject("properties") {
                                    putJsonObject("item") { put("type", "string") }
                                    putJsonObject("qty") { put("type", "string") }
                                    putJsonObject("amount") { put("type", "number") }
                                    putJsonObject("type") { putJsonArray("enum") { add("cash"); add("credit"); add("repayment") } }
                                    putJsonObject("party") { put("type", "string") }
                                }
                            }
                        }
                    }
                    putJsonArray("required") { add("entries") }
                }
            }
        }
        addJsonObject {
            put("type", "function")
            putJsonObject("function") {
                put("name", TOOL_PROPOSE_PAYMENT)
                put("description", "Draft a customer repayment (money a customer pays back) for the shopkeeper to confirm.")
                putJsonObject("parameters") {
                    put("type", "object")
                    putJsonObject("properties") {
                        putJsonObject("party") { put("type", "string") }
                        putJsonObject("amount") { put("type", "number") }
                    }
                    putJsonArray("required") { add("party"); add("amount") }
                }
            }
        }
    }

    private fun parseSaleEntries(args: String): List<SaleEntry> = try {
        json.decodeFromString(ProposeSaleArgs.serializer(), args).entries.map {
            SaleEntry(item = it.item, qty = it.qty, amount = it.amount, type = it.type, party = it.party)
        }
    } catch (t: Throwable) { emptyList() }

    private fun parsePayment(args: String): Pair<String?, Double?>? = try {
        val p = json.decodeFromString(ProposePaymentArgs.serializer(), args)
        if (p.party == null && p.amount == null) null else p.party to p.amount
    } catch (t: Throwable) { null }

    @Serializable private data class ProposeSaleArgs(val entries: List<EntryArg> = emptyList())
    @Serializable private data class EntryArg(
        val item: String? = null, val qty: String? = null, val amount: Double? = null,
        val type: String = "cash", val party: String? = null,
    )
    @Serializable private data class ProposePaymentArgs(val party: String? = null, val amount: Double? = null)

    companion object {
        const val MAX_HOPS = 6
        const val TOOL_PROPOSE_SALE = "propose_sale"
        const val TOOL_PROPOSE_PAYMENT = "propose_payment"
        const val FALLBACK_REPLY = "अभी जवाब नहीं बना — थोड़ा अलग तरीके से पूछें।"
        const val COULD_NOT_UNDERSTAND = "समझ नहीं आया — दोबारा कहें।"
        val SYSTEM_PROMPT = """
            You are Artha, an assistant for an Indian kirana (corner-shop) owner. The shopkeeper writes
            in Hindi/Hinglish. Answer in short, friendly Hindi/Hinglish with concrete numbers.
            Use the tools to READ shop data before answering money/stock/customer questions — never guess
            figures. Call the FEWEST tools needed (often just ONE). As soon as a tool result gives you
            enough to answer, STOP calling tools and reply immediately — do not re-check, verify, or call
            extra tools. To RECORD a sale/udhaar call propose_sale; to record a customer repayment call
            propose_payment — the shopkeeper confirms before it saves.
            Keep replies to 1-3 short lines. Rupees as ₹.
        """.trimIndent()
    }
}

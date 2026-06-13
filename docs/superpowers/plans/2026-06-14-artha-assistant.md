# Artha Conversational Assistant — Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Add a chat-thread Assistant tab that routes natural Hindi/Hinglish utterances to the existing use-cases (log a sale, record a payment, answer a P&L question) via a stateless two-stage intent router.

**Architecture:** Two LLM stages on the on-device llama-server: (1) `IntentRouter.classify()` picks an intent via an enum-only `json_schema` grammar; (2) an intent-specific extractor fills typed args (reusing the proven `SaleParser` for sales, a new `PaymentParser` for payments; P&L needs no second call). `RouteAssistantUseCase` orchestrates and returns an `AssistantResult`. The UI is a `LazyColumn` chat thread; mutations render inline confirm cards (reusing `EditableEntryCard`) and only write on Confirm. All additive — the only modified existing files are `ArthaApp.kt` (nav) and `SaleEntryScreen.kt` (extract the shared card). The §18-stable sale path is untouched.

**Tech Stack:** Kotlin, Jetpack Compose + Material3, Hilt, Coroutines/StateFlow, Ktor (`LlmHttpClient`), kotlinx.serialization, Room (via existing repos), JUnit4 + MockK + kotlinx-coroutines-test. Toolchain pinned: AGP 8.13 / Gradle 8.13 / JDK 17 / Vico 2.1.3 — **do not upgrade**.

**Spec:** `docs/superpowers/specs/2026-06-14-artha-assistant-design.md`

---

## File Structure

**Create (domain/model):**
- `domain/model/AssistantIntent.kt` — the intent enum
- `domain/model/AssistantResult.kt` — sealed router output

**Create (data/llm):**
- `data/llm/IntentRouter.kt` — Call 1 (classify); owns `INTENT_SYSTEM_PROMPT` + grammar
- `data/llm/PaymentParser.kt` — parses `{party, amount}` JSON → `ParsedPayment`

**Modify (data/llm):**
- `data/llm/LlmEngine.kt` — add `parsePayment()` + `PAYMENT_SYSTEM_PROMPT`/`PAYMENT_RESPONSE_FORMAT`; inject `PaymentParser`

**Create (domain/usecase):**
- `domain/usecase/PnlPeriodDetector.kt` — Kotlin keyword → `PnlPeriod`
- `domain/usecase/RouteAssistantUseCase.kt` — the orchestrator

**Create (ui/assistant):**
- `ui/assistant/ChatMessage.kt` — thread message model + `DraftStatus`
- `ui/assistant/AssistantMessages.kt` — message composables (bubbles + draft cards + P&L card)
- `ui/assistant/AssistantViewModel.kt` — state, send, confirm/cancel, voice
- `ui/assistant/AssistantScreen.kt` — the chat screen

**Create (ui/common):**
- `ui/common/EditableEntryCard.kt` — the sale confirm card, extracted from `SaleEntryScreen`

**Modify (ui):**
- `ui/entry/SaleEntryScreen.kt` — remove the private `EditableEntryCard`, import the shared one
- `ui/ArthaApp.kt` — add Assistant route + protruding-center bottom bar

**Create (scripts):**
- `scripts/validate-intent-prompt.py` — live-server intent validation harness

**Tests (create):**
- `app/src/test/java/com/artha/kirana/data/llm/PaymentParserTest.kt`
- `app/src/test/java/com/artha/kirana/data/llm/IntentRouterTest.kt`
- `app/src/test/java/com/artha/kirana/domain/usecase/PnlPeriodDetectorTest.kt`
- `app/src/test/java/com/artha/kirana/domain/usecase/RouteAssistantUseCaseTest.kt`

**DI note:** every new class uses constructor `@Inject` (and `@HiltViewModel` for the VM); all dependencies (`LlmHttpClient`, repos, existing use-cases, `AudioRecorder`, `WhisperEngine`) are already provided. **No Hilt module changes are needed.**

---

## Task 1: Domain models (intent + result)

**Files:**
- Create: `app/src/main/java/com/artha/kirana/domain/model/AssistantIntent.kt`
- Create: `app/src/main/java/com/artha/kirana/domain/model/AssistantResult.kt`

These are pure type declarations (no behavior) — no unit test; verified by compilation in later tasks.

- [ ] **Step 1: Create `AssistantIntent.kt`**

```kotlin
package com.artha.kirana.domain.model

/** Intents the Assistant can route to. Thin slice: the rest are added as use-cases are wired. */
enum class AssistantIntent { LOG_SALE, RECORD_PAYMENT, QUERY_PNL, UNKNOWN }
```

- [ ] **Step 2: Create `AssistantResult.kt`**

```kotlin
package com.artha.kirana.domain.model

/** What [com.artha.kirana.domain.usecase.RouteAssistantUseCase] produced for one utterance. */
sealed interface AssistantResult {
    /** A sale (or multi-item sale) to confirm before writing. */
    data class SaleDraft(val entries: List<SaleEntry>) : AssistantResult

    /** A khata repayment to confirm before writing. */
    data class PaymentDraft(val party: String?, val amount: Double?) : AssistantResult

    /** A read-only P&L answer (no confirmation needed). */
    data class PnlAnswer(val summary: PnlSummary) : AssistantResult

    /** A plain text reply (acks, "didn't understand", greetings). */
    data class Reply(val text: String) : AssistantResult

    /** The on-device LLM server was unreachable. */
    data object Unavailable : AssistantResult
}
```

- [ ] **Step 3: Compile-check**

Run: `./gradlew :app:compileDebugKotlin`
Expected: BUILD SUCCESSFUL.

- [ ] **Step 4: Commit**

```bash
git add app/src/main/java/com/artha/kirana/domain/model/AssistantIntent.kt app/src/main/java/com/artha/kirana/domain/model/AssistantResult.kt
git commit -m "feat(assistant): add AssistantIntent + AssistantResult models"
```

---

## Task 2: PaymentParser (TDD)

Mirrors `SaleParser`: turns raw LLM output into typed args; never throws (returns null on failure).

**Files:**
- Create: `app/src/main/java/com/artha/kirana/data/llm/PaymentParser.kt`
- Test: `app/src/test/java/com/artha/kirana/data/llm/PaymentParserTest.kt`

- [ ] **Step 1: Write the failing test**

```kotlin
package com.artha.kirana.data.llm

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class PaymentParserTest {

    private val parser = PaymentParser()

    @Test
    fun parsesPartyAndAmount() {
        val raw = """{"party":"रमेश","amount":50}"""
        val result = parser.parse(raw)!!
        assertEquals("रमेश", result.party)
        assertEquals(50.0, result.amount!!, 0.001)
    }

    @Test
    fun parsesMarkdownFencedJson() {
        val raw = "```json\n{\"party\":\"Priya\",\"amount\":100}\n```"
        val result = parser.parse(raw)!!
        assertEquals("Priya", result.party)
        assertEquals(100.0, result.amount!!, 0.001)
    }

    @Test
    fun normalizesLiteralNullPartyToNull() {
        val raw = """{"party":"null","amount":75}"""
        val result = parser.parse(raw)!!
        assertNull(result.party)
        assertEquals(75.0, result.amount!!, 0.001)
    }

    @Test
    fun returnsNullOnGarbage() {
        assertNull(parser.parse("the model refused to answer"))
    }
}
```

- [ ] **Step 2: Run test to verify it fails**

Run: `./gradlew :app:testDebugUnitTest --tests "com.artha.kirana.data.llm.PaymentParserTest"`
Expected: FAIL — `PaymentParser` / `ParsedPayment` unresolved.

- [ ] **Step 3: Write minimal implementation**

```kotlin
package com.artha.kirana.data.llm

import com.artha.kirana.util.JsonParser
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import javax.inject.Inject

/** Extracted khata-repayment args from the LLM. */
data class ParsedPayment(val party: String?, val amount: Double?)

@Serializable
private data class PaymentDto(
    val party: String? = null,
    val amount: Double? = null,
)

/**
 * Turns raw LLM output into a [ParsedPayment]. Never throws: on any extraction/parse failure
 * it returns null so the caller can fall back to a manual confirm card (CLAUDE.md §6).
 * Mirrors [SaleParser].
 */
class PaymentParser @Inject constructor() {

    private val json = Json {
        ignoreUnknownKeys = true
        isLenient = true
        coerceInputValues = true
    }

    fun parse(rawContent: String): ParsedPayment? {
        val jsonStr = JsonParser.extractJson(rawContent) ?: return null
        return try {
            val dto = json.decodeFromString(PaymentDto.serializer(), jsonStr)
            ParsedPayment(party = dto.party.clean(), amount = dto.amount)
        } catch (t: Throwable) {
            null
        }
    }

    /** The model sometimes emits the literal strings "null"/"none" or blanks instead of JSON null. */
    private fun String?.clean(): String? =
        this?.trim()?.takeUnless { it.isEmpty() || it.equals("null", true) || it.equals("none", true) }
}
```

- [ ] **Step 4: Run test to verify it passes**

Run: `./gradlew :app:testDebugUnitTest --tests "com.artha.kirana.data.llm.PaymentParserTest"`
Expected: PASS (4 tests).

- [ ] **Step 5: Commit**

```bash
git add app/src/main/java/com/artha/kirana/data/llm/PaymentParser.kt app/src/test/java/com/artha/kirana/data/llm/PaymentParserTest.kt
git commit -m "feat(assistant): add PaymentParser (party+amount) with tests"
```

---

## Task 3: PnlPeriodDetector (TDD)

Pure Kotlin keyword detection — no LLM call. Stems (`महीन`, `हफ्त`) match inflections (महीने/महीना, हफ्ते/हफ्ता).

**Files:**
- Create: `app/src/main/java/com/artha/kirana/domain/usecase/PnlPeriodDetector.kt`
- Test: `app/src/test/java/com/artha/kirana/domain/usecase/PnlPeriodDetectorTest.kt`

- [ ] **Step 1: Write the failing test**

```kotlin
package com.artha.kirana.domain.usecase

import com.artha.kirana.domain.model.PnlPeriod
import org.junit.Assert.assertEquals
import org.junit.Test

class PnlPeriodDetectorTest {

    @Test
    fun defaultsToToday() {
        assertEquals(PnlPeriod.TODAY, PnlPeriodDetector.detect("आज की कमाई कितनी हुई"))
        assertEquals(PnlPeriod.TODAY, PnlPeriodDetector.detect("kitna kamaya"))
    }

    @Test
    fun detectsWeek() {
        assertEquals(PnlPeriod.THIS_WEEK, PnlPeriodDetector.detect("इस हफ्ते का मुनाफा"))
        assertEquals(PnlPeriod.THIS_WEEK, PnlPeriodDetector.detect("इस सप्ताह की बिक्री"))
        assertEquals(PnlPeriod.THIS_WEEK, PnlPeriodDetector.detect("this week profit"))
    }

    @Test
    fun detectsMonth() {
        assertEquals(PnlPeriod.THIS_MONTH, PnlPeriodDetector.detect("इस महीने की कमाई"))
        assertEquals(PnlPeriod.THIS_MONTH, PnlPeriodDetector.detect("this month sales"))
    }
}
```

- [ ] **Step 2: Run test to verify it fails**

Run: `./gradlew :app:testDebugUnitTest --tests "com.artha.kirana.domain.usecase.PnlPeriodDetectorTest"`
Expected: FAIL — `PnlPeriodDetector` unresolved.

- [ ] **Step 3: Write minimal implementation**

```kotlin
package com.artha.kirana.domain.usecase

import com.artha.kirana.domain.model.PnlPeriod

/**
 * Picks a [PnlPeriod] from a P&L question by keyword. Deterministic, so it lives in code
 * (no second LLM call). Month is checked before week so "इस महीने" never matches a week stem.
 * Stems match inflections: महीन→महीना/महीने, हफ्त→हफ्ता/हफ्ते.
 */
object PnlPeriodDetector {
    fun detect(text: String): PnlPeriod {
        val t = text.lowercase()
        return when {
            t.contains("महीन") || t.contains("month") || t.contains("mahin") || t.contains("mahee") ->
                PnlPeriod.THIS_MONTH
            t.contains("हफ्त") || t.contains("हफ़्त") || t.contains("सप्ताह") || t.contains("week") || t.contains("haft") ->
                PnlPeriod.THIS_WEEK
            else -> PnlPeriod.TODAY
        }
    }
}
```

- [ ] **Step 4: Run test to verify it passes**

Run: `./gradlew :app:testDebugUnitTest --tests "com.artha.kirana.domain.usecase.PnlPeriodDetectorTest"`
Expected: PASS (3 tests).

- [ ] **Step 5: Commit**

```bash
git add app/src/main/java/com/artha/kirana/domain/usecase/PnlPeriodDetector.kt app/src/test/java/com/artha/kirana/domain/usecase/PnlPeriodDetectorTest.kt
git commit -m "feat(assistant): add PnlPeriodDetector with tests"
```

---

## Task 4: IntentRouter (TDD the parse, wire the call)

Call 1 of the router. `classify()` does the HTTP call; `parseIntent()` (pure) maps content → enum and is unit-tested. Owns the prompt + grammar as the single source of truth (mirrors `LlmEngine`).

**Files:**
- Create: `app/src/main/java/com/artha/kirana/data/llm/IntentRouter.kt`
- Test: `app/src/test/java/com/artha/kirana/data/llm/IntentRouterTest.kt`

- [ ] **Step 1: Write the failing test**

```kotlin
package com.artha.kirana.data.llm

import com.artha.kirana.domain.model.AssistantIntent
import io.mockk.mockk
import org.junit.Assert.assertEquals
import org.junit.Test

class IntentRouterTest {

    private val router = IntentRouter(client = mockk(relaxed = true))

    @Test
    fun parsesEachKnownIntent() {
        assertEquals(AssistantIntent.LOG_SALE, router.parseIntent("""{"intent":"log_sale"}"""))
        assertEquals(AssistantIntent.RECORD_PAYMENT, router.parseIntent("""{"intent":"record_payment"}"""))
        assertEquals(AssistantIntent.QUERY_PNL, router.parseIntent("""{"intent":"query_pnl"}"""))
    }

    @Test
    fun parsesMarkdownFenced() {
        assertEquals(
            AssistantIntent.QUERY_PNL,
            router.parseIntent("```json\n{\"intent\":\"query_pnl\"}\n```"),
        )
    }

    @Test
    fun unknownStringFallsBackToUnknown() {
        assertEquals(AssistantIntent.UNKNOWN, router.parseIntent("""{"intent":"frobnicate"}"""))
    }

    @Test
    fun garbageFallsBackToUnknown() {
        assertEquals(AssistantIntent.UNKNOWN, router.parseIntent("not json at all"))
    }
}
```

- [ ] **Step 2: Run test to verify it fails**

Run: `./gradlew :app:testDebugUnitTest --tests "com.artha.kirana.data.llm.IntentRouterTest"`
Expected: FAIL — `IntentRouter` unresolved.

- [ ] **Step 3: Write minimal implementation**

```kotlin
package com.artha.kirana.data.llm

import com.artha.kirana.data.remote.LlmHttpClient
import com.artha.kirana.data.remote.LlmUnavailableException
import com.artha.kirana.domain.model.AssistantIntent
import com.artha.kirana.util.JsonParser
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.add
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put
import kotlinx.serialization.json.putJsonArray
import kotlinx.serialization.json.putJsonObject
import javax.inject.Inject
import javax.inject.Singleton

@Serializable
private data class IntentDto(val intent: String = "unknown")

/**
 * Stage 1 of the Assistant router: classifies an utterance into an [AssistantIntent] with one
 * grammar-constrained LLM call (enum-only json_schema → reliable for Qwen 3B). Holds the prompt
 * + grammar as the single source of truth — keep scripts/validate-intent-prompt.py in sync.
 */
@Singleton
class IntentRouter @Inject constructor(
    private val client: LlmHttpClient,
) {
    private val json = Json { ignoreUnknownKeys = true; isLenient = true }

    suspend fun classify(text: String): Result<AssistantIntent> = try {
        Result.success(parseIntent(client.chat(INTENT_SYSTEM_PROMPT, text, INTENT_RESPONSE_FORMAT)))
    } catch (e: LlmUnavailableException) {
        Result.failure(e)
    }

    /** Pure mapping of raw LLM content → intent; never throws (unknown/garbage → UNKNOWN). */
    fun parseIntent(rawContent: String): AssistantIntent {
        val jsonStr = JsonParser.extractJson(rawContent) ?: return AssistantIntent.UNKNOWN
        return try {
            when (json.decodeFromString(IntentDto.serializer(), jsonStr).intent.trim().lowercase()) {
                "log_sale" -> AssistantIntent.LOG_SALE
                "record_payment" -> AssistantIntent.RECORD_PAYMENT
                "query_pnl" -> AssistantIntent.QUERY_PNL
                else -> AssistantIntent.UNKNOWN
            }
        } catch (t: Throwable) {
            AssistantIntent.UNKNOWN
        }
    }

    companion object {
        const val INTENT_SYSTEM_PROMPT = """You are a router for a kirana shop assistant. Read the shopkeeper's message (Hindi/Hinglish) and output ONLY which action it wants, as JSON.
Return ONLY: {"intent": one of "log_sale" | "record_payment" | "query_pnl" | "unknown"}
No explanation. No markdown. Just the raw JSON object.

Meaning:
- log_sale = recording a sale/purchase of goods (items + quantity, cash or उधार/credit). e.g. selling rice, sugar, soap.
- record_payment = a customer PAID BACK money they owed (दिए / चुकाए / चुका दिया / जमा / paid). No goods involved.
- query_pnl = asking about earnings/profit/sales totals (कमाई, मुनाफा, बिक्री, कितना कमाया; today/week/month).
- unknown = anything else.

Examples:
Input: दो किलो चावल अस्सी रुपये उधार रमेश को
{"intent":"log_sale"}
Input: तीन साबुन बीस बीस के
{"intent":"log_sale"}
Input: रमेश ने पचास रुपये दिए
{"intent":"record_payment"}
Input: प्रिया ने अपना उधार चुका दिया सौ रुपये
{"intent":"record_payment"}
Input: आज की कमाई कितनी हुई
{"intent":"query_pnl"}
Input: इस हफ्ते का मुनाफा बताओ
{"intent":"query_pnl"}
Input: नमस्ते
{"intent":"unknown"}"""

        val INTENT_RESPONSE_FORMAT = buildJsonObject {
            put("type", "json_schema")
            putJsonObject("json_schema") {
                put("name", "intent")
                putJsonObject("schema") {
                    put("type", "object")
                    putJsonObject("properties") {
                        putJsonObject("intent") {
                            putJsonArray("enum") {
                                add("log_sale"); add("record_payment"); add("query_pnl"); add("unknown")
                            }
                        }
                    }
                    putJsonArray("required") { add("intent") }
                }
            }
        }
    }
}
```

- [ ] **Step 4: Run test to verify it passes**

Run: `./gradlew :app:testDebugUnitTest --tests "com.artha.kirana.data.llm.IntentRouterTest"`
Expected: PASS (4 tests).

- [ ] **Step 5: Commit**

```bash
git add app/src/main/java/com/artha/kirana/data/llm/IntentRouter.kt app/src/test/java/com/artha/kirana/data/llm/IntentRouterTest.kt
git commit -m "feat(assistant): add IntentRouter (stage-1 classifier) with tests"
```

---

## Task 5: LlmEngine.parsePayment (Call 2 for payments)

Add the payment extraction call to `LlmEngine`, mirroring `parseSale`. Inject `PaymentParser`. No new unit test (mirrors the untested `parseSale`; covered by `PaymentParserTest` + the live validate script + on-device check).

**Files:**
- Modify: `app/src/main/java/com/artha/kirana/data/llm/LlmEngine.kt`

- [ ] **Step 1: Inject `PaymentParser` into the constructor**

Change the constructor (around `LlmEngine.kt:20-23`) from:

```kotlin
class LlmEngine @Inject constructor(
    private val client: LlmHttpClient,
    private val saleParser: SaleParser,
) {
```

to:

```kotlin
class LlmEngine @Inject constructor(
    private val client: LlmHttpClient,
    private val saleParser: SaleParser,
    private val paymentParser: PaymentParser,
) {
```

- [ ] **Step 2: Add `parsePayment` after `parseSale` (after `LlmEngine.kt:31`)**

```kotlin
    /** Extracts khata-repayment args ({party, amount}) from already-normalized text. */
    suspend fun parsePayment(text: String): Result<ParsedPayment> = try {
        val content = client.chat(PAYMENT_SYSTEM_PROMPT, text, PAYMENT_RESPONSE_FORMAT)
        Result.success(paymentParser.parse(content) ?: ParsedPayment(party = null, amount = null))
    } catch (e: LlmUnavailableException) {
        Result.failure(e)
    }
```

- [ ] **Step 3: Add the payment prompt + grammar to the `companion object`** (after `SALE_RESPONSE_FORMAT`, before the closing braces near `LlmEngine.kt:111`)

```kotlin
        // Keep in sync with scripts/validate-intent-prompt.py if a payment section is added there.
        const val PAYMENT_SYSTEM_PROMPT = """You are a kirana store assistant. The shopkeeper is recording a customer paying back money they owed. Extract JSON only.
Return ONLY: {"party": string|null, "amount": number|null}
No explanation. No markdown. Just the raw JSON object.
- party = the person's name ONLY. Remove को/ko/ने/ne/से/se tokens before AND after it (e.g. "रमेश ने"→"रमेश").
- amount = the rupee amount as a plain number in digits.

Examples:
Input: रमेश ने पचास रुपये दिए
{"party":"रमेश","amount":50}
Input: प्रिया ने सौ रुपये चुकाए
{"party":"प्रिया","amount":100}
Input: दो सौ जमा किए सुरेश ने
{"party":"सुरेश","amount":200}"""

        val PAYMENT_RESPONSE_FORMAT = buildJsonObject {
            put("type", "json_schema")
            putJsonObject("json_schema") {
                put("name", "payment")
                putJsonObject("schema") {
                    put("type", "object")
                    putJsonObject("properties") {
                        putJsonObject("party") { putJsonArray("type") { add("string"); add("null") } }
                        putJsonObject("amount") { putJsonArray("type") { add("number"); add("null") } }
                    }
                    putJsonArray("required") { add("party"); add("amount") }
                }
            }
        }
```

- [ ] **Step 4: Compile + run the full existing unit suite (no regressions)**

Run: `./gradlew :app:testDebugUnitTest`
Expected: BUILD SUCCESSFUL; all existing tests still green.

- [ ] **Step 5: Commit**

```bash
git add app/src/main/java/com/artha/kirana/data/llm/LlmEngine.kt
git commit -m "feat(assistant): add LlmEngine.parsePayment (stage-2 payment extractor)"
```

---

## Task 6: RouteAssistantUseCase (TDD the dispatch)

The orchestrator: classify → dispatch to the right extractor / query → `AssistantResult`.

**Files:**
- Create: `app/src/main/java/com/artha/kirana/domain/usecase/RouteAssistantUseCase.kt`
- Test: `app/src/test/java/com/artha/kirana/domain/usecase/RouteAssistantUseCaseTest.kt`

- [ ] **Step 1: Write the failing test**

```kotlin
package com.artha.kirana.domain.usecase

import com.artha.kirana.data.llm.IntentRouter
import com.artha.kirana.data.llm.LlmEngine
import com.artha.kirana.data.llm.ParsedPayment
import com.artha.kirana.data.remote.LlmUnavailableException
import com.artha.kirana.domain.model.AssistantIntent
import com.artha.kirana.domain.model.AssistantResult
import com.artha.kirana.domain.model.PnlPeriod
import com.artha.kirana.domain.model.PnlSummary
import com.artha.kirana.domain.model.SaleEntry
import io.mockk.coEvery
import io.mockk.every
import io.mockk.mockk
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class RouteAssistantUseCaseTest {

    private val intentRouter = mockk<IntentRouter>()
    private val parseSale = mockk<ParseSaleEntryUseCase>()
    private val engine = mockk<LlmEngine>()
    private val getPnl = mockk<GetPnlSummaryUseCase>()
    private val useCase = RouteAssistantUseCase(intentRouter, parseSale, engine, getPnl)

    private val summary = PnlSummary(0.0, 0.0, 0.0, 0.0, 0.0, PnlPeriod.TODAY)

    @Test
    fun logSaleWithEntriesProducesSaleDraft() = runTest {
        coEvery { intentRouter.classify(any()) } returns Result.success(AssistantIntent.LOG_SALE)
        val entry = SaleEntry(item = "चावल", qty = "2 kg", amount = 80.0, type = "cash", party = null)
        coEvery { parseSale(any()) } returns Result.success(listOf(entry))

        val result = useCase("दो किलो चावल अस्सी रुपये")

        assertTrue(result is AssistantResult.SaleDraft)
        assertEquals(listOf(entry), (result as AssistantResult.SaleDraft).entries)
    }

    @Test
    fun logSaleWithNoEntriesProducesReply() = runTest {
        coEvery { intentRouter.classify(any()) } returns Result.success(AssistantIntent.LOG_SALE)
        coEvery { parseSale(any()) } returns Result.success(emptyList())

        assertTrue(useCase("blah") is AssistantResult.Reply)
    }

    @Test
    fun recordPaymentProducesPaymentDraft() = runTest {
        coEvery { intentRouter.classify(any()) } returns Result.success(AssistantIntent.RECORD_PAYMENT)
        coEvery { engine.parsePayment(any()) } returns Result.success(ParsedPayment("रमेश", 50.0))

        val result = useCase("रमेश ने पचास रुपये दिए")

        assertTrue(result is AssistantResult.PaymentDraft)
        result as AssistantResult.PaymentDraft
        assertEquals("रमेश", result.party)
        assertEquals(50.0, result.amount!!, 0.001)
    }

    @Test
    fun queryPnlUsesDetectedPeriodAndProducesAnswer() = runTest {
        coEvery { intentRouter.classify(any()) } returns Result.success(AssistantIntent.QUERY_PNL)
        every { getPnl(PnlPeriod.THIS_WEEK, any()) } returns flowOf(summary)

        val result = useCase("इस हफ्ते का मुनाफा")

        assertTrue(result is AssistantResult.PnlAnswer)
        assertEquals(summary, (result as AssistantResult.PnlAnswer).summary)
    }

    @Test
    fun classifyFailureProducesUnavailable() = runTest {
        coEvery { intentRouter.classify(any()) } returns Result.failure(LlmUnavailableException(null))

        assertEquals(AssistantResult.Unavailable, useCase("anything"))
    }

    @Test
    fun unknownIntentProducesReply() = runTest {
        coEvery { intentRouter.classify(any()) } returns Result.success(AssistantIntent.UNKNOWN)

        assertTrue(useCase("नमस्ते") is AssistantResult.Reply)
    }
}
```

- [ ] **Step 2: Run test to verify it fails**

Run: `./gradlew :app:testDebugUnitTest --tests "com.artha.kirana.domain.usecase.RouteAssistantUseCaseTest"`
Expected: FAIL — `RouteAssistantUseCase` unresolved.

- [ ] **Step 3: Write minimal implementation**

```kotlin
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
```

- [ ] **Step 4: Run test to verify it passes**

Run: `./gradlew :app:testDebugUnitTest --tests "com.artha.kirana.domain.usecase.RouteAssistantUseCaseTest"`
Expected: PASS (6 tests).

- [ ] **Step 5: Commit**

```bash
git add app/src/main/java/com/artha/kirana/domain/usecase/RouteAssistantUseCase.kt app/src/test/java/com/artha/kirana/domain/usecase/RouteAssistantUseCaseTest.kt
git commit -m "feat(assistant): add RouteAssistantUseCase orchestrator with tests"
```

---

## Task 7: ChatMessage model

The thread's message types. Pure declarations — verified by compilation.

**Files:**
- Create: `app/src/main/java/com/artha/kirana/ui/assistant/ChatMessage.kt`

- [ ] **Step 1: Create `ChatMessage.kt`**

```kotlin
package com.artha.kirana.ui.assistant

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
}
```

- [ ] **Step 2: Compile-check**

Run: `./gradlew :app:compileDebugKotlin`
Expected: BUILD SUCCESSFUL.

- [ ] **Step 3: Commit**

```bash
git add app/src/main/java/com/artha/kirana/ui/assistant/ChatMessage.kt
git commit -m "feat(assistant): add ChatMessage thread model"
```

---

## Task 8: Extract EditableEntryCard to a shared file

So the Assistant's sale-draft card reuses the exact, proven editor. Mechanical move — no behavior change.

**Files:**
- Create: `app/src/main/java/com/artha/kirana/ui/common/EditableEntryCard.kt`
- Modify: `app/src/main/java/com/artha/kirana/ui/entry/SaleEntryScreen.kt` (delete the private copy, import the shared one)

- [ ] **Step 1: Create the shared file** (verbatim copy of the existing private composable, made public)

```kotlin
package com.artha.kirana.ui.common

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.Card
import androidx.compose.material3.FilterChip
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import com.artha.kirana.domain.model.SaleEntry

/** Editable card for one parsed [SaleEntry]. Shared by Sale Entry and the Assistant. */
@Composable
fun EditableEntryCard(entry: SaleEntry, onChange: (SaleEntry) -> Unit) {
    Card(Modifier.fillMaxWidth()) {
        Column(Modifier.padding(16.dp)) {
            OutlinedTextField(
                value = entry.item ?: "",
                onValueChange = { onChange(entry.copy(item = it.ifBlank { null })) },
                label = { Text("Item · वस्तु") },
                modifier = Modifier.fillMaxWidth(),
                singleLine = true,
            )
            Spacer(Modifier.height(8.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                OutlinedTextField(
                    value = entry.qty ?: "",
                    onValueChange = { onChange(entry.copy(qty = it.ifBlank { null })) },
                    label = { Text("Qty") },
                    modifier = Modifier.weight(1f),
                    singleLine = true,
                )
                OutlinedTextField(
                    value = entry.amount?.toLong()?.toString() ?: "",
                    onValueChange = { onChange(entry.copy(amount = it.toDoubleOrNull())) },
                    label = { Text("₹ Amount") },
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                    modifier = Modifier.weight(1f),
                    singleLine = true,
                )
            }
            Spacer(Modifier.height(8.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                listOf("cash", "credit", "repayment").forEach { t ->
                    FilterChip(
                        selected = entry.type == t,
                        onClick = { onChange(entry.copy(type = t)) },
                        label = { Text(t) },
                    )
                }
            }
            Spacer(Modifier.height(8.dp))
            OutlinedTextField(
                value = entry.party ?: "",
                onValueChange = { onChange(entry.copy(party = it.ifBlank { null })) },
                label = { Text("Party · ग्राहक (optional)") },
                modifier = Modifier.fillMaxWidth(),
                singleLine = true,
            )
        }
    }
}
```

- [ ] **Step 2: Delete the private `EditableEntryCard` from `SaleEntryScreen.kt`**

Remove the entire `@Composable private fun EditableEntryCard(...) { ... }` block (the function shown at `SaleEntryScreen.kt:236`–end of that function). Then add this import near the other `com.artha.kirana` imports at the top of `SaleEntryScreen.kt`:

```kotlin
import com.artha.kirana.ui.common.EditableEntryCard
```

After removing, check whether any imports in `SaleEntryScreen.kt` are now unused (e.g. `FilterChip`, `KeyboardOptions`, `KeyboardType`) — if Android Studio / the compiler flags them as unused, leave them only if still used by other code in the file; otherwise remove. (Unused imports are warnings, not errors — do not block on them.)

- [ ] **Step 3: Build to verify the move compiles and the sale screen still works**

Run: `./gradlew :app:compileDebugKotlin`
Expected: BUILD SUCCESSFUL.

- [ ] **Step 4: Commit**

```bash
git add app/src/main/java/com/artha/kirana/ui/common/EditableEntryCard.kt app/src/main/java/com/artha/kirana/ui/entry/SaleEntryScreen.kt
git commit -m "refactor(ui): extract shared EditableEntryCard for reuse by Assistant"
```

---

## Task 9: Assistant message composables

The bubbles + inline cards. Pure UI (build-verified; visual check happens in Task 13).

**Files:**
- Create: `app/src/main/java/com/artha/kirana/ui/assistant/AssistantMessages.kt`

- [ ] **Step 1: Create `AssistantMessages.kt`**

```kotlin
package com.artha.kirana.ui.assistant

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.artha.kirana.domain.model.PnlSummary
import com.artha.kirana.domain.model.SaleEntry
import com.artha.kirana.ui.common.EditableEntryCard
import com.artha.kirana.ui.theme.AccentGreen
import com.artha.kirana.ui.theme.BrandGold

@Composable
fun UserBubble(text: String) {
    Box(Modifier.fillMaxWidth(), contentAlignment = Alignment.CenterEnd) {
        Surface(
            color = BrandGold,
            shape = RoundedCornerShape(16.dp),
            modifier = Modifier.padding(vertical = 4.dp),
        ) {
            Text(text, Modifier.padding(horizontal = 14.dp, vertical = 10.dp))
        }
    }
}

@Composable
fun ReplyBubble(text: String) {
    Box(Modifier.fillMaxWidth(), contentAlignment = Alignment.CenterStart) {
        Surface(
            shape = RoundedCornerShape(16.dp),
            tonalElevation = 2.dp,
            modifier = Modifier.padding(vertical = 4.dp),
        ) {
            Text(text, Modifier.padding(horizontal = 14.dp, vertical = 10.dp))
        }
    }
}

/** Sale confirm card. Editable while Pending; collapses to a status line once acted on. */
@Composable
fun SaleDraftBubble(
    entries: List<SaleEntry>,
    status: DraftStatus,
    onConfirm: (List<SaleEntry>) -> Unit,
    onCancel: () -> Unit,
) {
    if (status != DraftStatus.Pending) {
        ReplyBubble(if (status == DraftStatus.Confirmed) "✓ बिक्री दर्ज" else "रद्द किया")
        return
    }
    var edited by remember { mutableStateOf(entries) }
    Card(Modifier.fillMaxWidth().padding(vertical = 4.dp)) {
        Column(Modifier.padding(12.dp)) {
            Text("इसे दर्ज करें?", fontWeight = FontWeight.Bold)
            Spacer(Modifier.height(8.dp))
            edited.forEachIndexed { i, e ->
                EditableEntryCard(e) { updated -> edited = edited.toMutableList().also { it[i] = updated } }
                Spacer(Modifier.height(8.dp))
            }
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                OutlinedButton(onClick = onCancel) { Text("Cancel") }
                Button(onClick = { onConfirm(edited) }) { Text("Confirm ✓") }
            }
        }
    }
}

/** Payment confirm card. Editable party/amount while Pending. */
@Composable
fun PaymentDraftBubble(
    party: String?,
    amount: Double?,
    status: DraftStatus,
    onConfirm: (String?, Double?) -> Unit,
    onCancel: () -> Unit,
) {
    if (status != DraftStatus.Pending) {
        ReplyBubble(if (status == DraftStatus.Confirmed) "✓ भुगतान दर्ज" else "रद्द किया")
        return
    }
    var partyText by remember { mutableStateOf(party ?: "") }
    var amountText by remember { mutableStateOf(amount?.toLong()?.toString() ?: "") }
    Card(Modifier.fillMaxWidth().padding(vertical = 4.dp)) {
        Column(Modifier.padding(12.dp)) {
            Text("भुगतान दर्ज करें?", fontWeight = FontWeight.Bold)
            Spacer(Modifier.height(8.dp))
            androidx.compose.material3.OutlinedTextField(
                value = partyText,
                onValueChange = { partyText = it },
                label = { Text("Party · ग्राहक") },
                singleLine = true,
                modifier = Modifier.fillMaxWidth(),
            )
            Spacer(Modifier.height(8.dp))
            androidx.compose.material3.OutlinedTextField(
                value = amountText,
                onValueChange = { amountText = it },
                label = { Text("₹ Amount") },
                singleLine = true,
                keyboardOptions = androidx.compose.foundation.text.KeyboardOptions(
                    keyboardType = androidx.compose.ui.text.input.KeyboardType.Number,
                ),
                modifier = Modifier.fillMaxWidth(),
            )
            Spacer(Modifier.height(8.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                OutlinedButton(onClick = onCancel) { Text("Cancel") }
                Button(onClick = {
                    onConfirm(partyText.ifBlank { null }, amountText.toDoubleOrNull())
                }) { Text("Confirm ✓") }
            }
        }
    }
}

/** Read-only P&L answer. */
@Composable
fun PnlAnswerBubble(summary: PnlSummary) {
    Card(
        Modifier.fillMaxWidth().padding(vertical = 4.dp),
        colors = CardDefaults.cardColors(),
    ) {
        Column(Modifier.padding(16.dp)) {
            Text("आज का हिसाब", fontWeight = FontWeight.Bold)
            Spacer(Modifier.height(8.dp))
            Text("बिक्री (Revenue): ₹${summary.grossRevenue.toLong()}")
            Text("मुनाफा (Profit): ₹${summary.grossProfit.toLong()}", color = AccentGreen)
            Text("नकद (Cash): ₹${summary.cashCollected.toLong()}")
            Text("बकाया (Outstanding): ₹${summary.totalOutstanding.toLong()}")
        }
    }
}
```

- [ ] **Step 2: Build-check**

Run: `./gradlew :app:compileDebugKotlin`
Expected: BUILD SUCCESSFUL.

- [ ] **Step 3: Commit**

```bash
git add app/src/main/java/com/artha/kirana/ui/assistant/AssistantMessages.kt
git commit -m "feat(assistant): add chat message composables (bubbles + draft cards)"
```

---

## Task 10: AssistantViewModel

State + send + confirm/cancel + voice (lifted from `SaleEntryViewModel`).

**Files:**
- Create: `app/src/main/java/com/artha/kirana/ui/assistant/AssistantViewModel.kt`

- [ ] **Step 1: Create `AssistantViewModel.kt`**

```kotlin
package com.artha.kirana.ui.assistant

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.artha.kirana.data.voice.AudioRecorder
import com.artha.kirana.data.voice.WhisperEngine
import com.artha.kirana.domain.model.AssistantResult
import com.artha.kirana.domain.model.SaleEntry
import com.artha.kirana.domain.repository.KhataRepository
import com.artha.kirana.domain.usecase.LogSaleUseCase
import com.artha.kirana.domain.usecase.RouteAssistantUseCase
import com.artha.kirana.ui.entry.VoiceState
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import timber.log.Timber
import java.util.concurrent.atomic.AtomicBoolean
import java.util.concurrent.atomic.AtomicLong
import javax.inject.Inject

@HiltViewModel
class AssistantViewModel @Inject constructor(
    private val route: RouteAssistantUseCase,
    private val logSale: LogSaleUseCase,
    private val khata: KhataRepository,
    private val audioRecorder: AudioRecorder,
    private val whisper: WhisperEngine,
) : ViewModel() {

    private val _messages = MutableStateFlow<List<ChatMessage>>(emptyList())
    val messages = _messages.asStateFlow()

    private val _routing = MutableStateFlow(false)
    val routing = _routing.asStateFlow()

    private val _voice = MutableStateFlow<VoiceState>(VoiceState.Idle)
    val voice = _voice.asStateFlow()

    /** Emitted when a recording is transcribed — the screen drops this into the input field. */
    private val _transcript = MutableSharedFlow<String>(extraBufferCapacity = 1)
    val transcript = _transcript.asSharedFlow()

    val voiceEnabled: Boolean get() = whisper.voiceEnabled

    private val ids = AtomicLong(0)
    private val recording = AtomicBoolean(false)

    fun send(text: String) {
        val trimmed = text.trim()
        if (trimmed.isEmpty()) return
        append(ChatMessage.User(ids.incrementAndGet(), trimmed))
        _routing.value = true
        viewModelScope.launch {
            val result = route(trimmed)
            _routing.value = false
            append(result.toMessage(ids.incrementAndGet()))
        }
    }

    fun confirmSale(messageId: Long, entries: List<SaleEntry>) {
        viewModelScope.launch {
            entries.forEach { logSale(it, inputMethod = "typed", rawInput = null) }
            updateStatus(messageId, DraftStatus.Confirmed)
            append(ChatMessage.Reply(ids.incrementAndGet(), "✓ बिक्री दर्ज हो गई।"))
        }
    }

    fun confirmPayment(messageId: Long, party: String?, amount: Double?) {
        viewModelScope.launch {
            if (party.isNullOrBlank() || amount == null) {
                append(ChatMessage.Reply(ids.incrementAndGet(), "नाम और रकम भरें।"))
                return@launch
            }
            khata.applyRepayment(party, amount, null)
            updateStatus(messageId, DraftStatus.Confirmed)
            append(ChatMessage.Reply(ids.incrementAndGet(), "✓ $party का ₹${amount.toLong()} भुगतान दर्ज।"))
        }
    }

    fun cancel(messageId: Long) = updateStatus(messageId, DraftStatus.Cancelled)

    // ---- voice (lifted from SaleEntryViewModel) ----

    fun warmUpVoice() {
        if (!whisper.voiceEnabled) return
        viewModelScope.launch { runCatching { whisper.warmUp() } }
    }

    fun toggleVoice() {
        when (_voice.value) {
            VoiceState.Recording -> recording.set(false)
            VoiceState.Transcribing -> Unit
            else -> startRecording()
        }
    }

    private fun startRecording() {
        if (!whisper.isModelPresent()) {
            _voice.value = VoiceState.Error("Voice model missing — push the ggml model to the phone.")
            return
        }
        recording.set(true)
        _voice.value = VoiceState.Recording
        viewModelScope.launch {
            try {
                val samples = audioRecorder.record(maxSeconds = 12) { recording.get() }
                if (samples.isEmpty()) {
                    _voice.value = VoiceState.Error("सुनाई नहीं दिया — फिर से बोलें")
                    return@launch
                }
                _voice.value = VoiceState.Transcribing
                val text = whisper.transcribe(samples, lang = "hi")
                _transcript.emit(text)
                _voice.value = VoiceState.Idle
            } catch (t: Throwable) {
                Timber.e(t, "voice transcription failed")
                _voice.value = VoiceState.Error(t.message ?: "Voice failed")
            }
        }
    }

    // ---- helpers ----

    private fun append(message: ChatMessage) {
        _messages.value = _messages.value + message
    }

    private fun updateStatus(messageId: Long, status: DraftStatus) {
        _messages.value = _messages.value.map { m ->
            when (m) {
                is ChatMessage.SaleDraft -> if (m.id == messageId) m.copy(status = status) else m
                is ChatMessage.PaymentDraft -> if (m.id == messageId) m.copy(status = status) else m
                else -> m
            }
        }
    }

    private fun AssistantResult.toMessage(id: Long): ChatMessage = when (this) {
        is AssistantResult.SaleDraft -> ChatMessage.SaleDraft(id, entries)
        is AssistantResult.PaymentDraft -> ChatMessage.PaymentDraft(id, party, amount)
        is AssistantResult.PnlAnswer -> ChatMessage.PnlAnswer(id, summary)
        is AssistantResult.Reply -> ChatMessage.Reply(id, text)
        AssistantResult.Unavailable -> ChatMessage.Reply(id, "सर्वर बंद है — llama-server चालू करें।")
    }
}
```

- [ ] **Step 2: Build-check**

Run: `./gradlew :app:compileDebugKotlin`
Expected: BUILD SUCCESSFUL. (`VoiceState` is reused from `com.artha.kirana.ui.entry`.)

- [ ] **Step 3: Commit**

```bash
git add app/src/main/java/com/artha/kirana/ui/assistant/AssistantViewModel.kt
git commit -m "feat(assistant): add AssistantViewModel (route, confirm, voice)"
```

---

## Task 11: AssistantScreen

The chat screen: thread + input row (text + mic + send) + empty-state example chips.

**Files:**
- Create: `app/src/main/java/com/artha/kirana/ui/assistant/AssistantScreen.kt`

- [ ] **Step 1: Create `AssistantScreen.kt`**

```kotlin
package com.artha.kirana.ui.assistant

import android.Manifest
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.Send
import androidx.compose.material.icons.filled.Mic
import androidx.compose.material.icons.filled.Stop
import androidx.compose.material3.AssistChip
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.artha.kirana.ui.entry.VoiceState

private val EXAMPLES = listOf("दो किलो चावल ₹80", "रमेश ने ₹50 दिए", "आज की कमाई?")

@Composable
fun AssistantScreen(viewModel: AssistantViewModel = hiltViewModel()) {
    val messages by viewModel.messages.collectAsStateWithLifecycle()
    val routing by viewModel.routing.collectAsStateWithLifecycle()
    val voice by viewModel.voice.collectAsStateWithLifecycle()
    var input by remember { mutableStateOf("") }
    val listState = rememberLazyListState()

    val micPermLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestPermission(),
    ) { granted -> if (granted) viewModel.toggleVoice() }

    LaunchedEffect(Unit) { viewModel.warmUpVoice() }
    LaunchedEffect(Unit) { viewModel.transcript.collect { input = it } }
    LaunchedEffect(messages.size) {
        if (messages.isNotEmpty()) listState.animateScrollToItem(messages.size - 1)
    }

    Column(Modifier.fillMaxSize().padding(horizontal = 12.dp)) {
        Box(Modifier.weight(1f).fillMaxWidth()) {
            if (messages.isEmpty()) {
                Column(Modifier.align(Alignment.Center), horizontalAlignment = Alignment.CenterHorizontally) {
                    Text("नमस्ते 🙏 कुछ भी पूछें या बोलें")
                    Spacer(Modifier.height(12.dp))
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        EXAMPLES.forEach { ex ->
                            AssistChip(onClick = { viewModel.send(ex) }, label = { Text(ex) })
                        }
                    }
                }
            } else {
                LazyColumn(state = listState, modifier = Modifier.fillMaxSize()) {
                    items(messages, key = { it.id }) { m ->
                        when (m) {
                            is ChatMessage.User -> UserBubble(m.text)
                            is ChatMessage.Reply -> ReplyBubble(m.text)
                            is ChatMessage.SaleDraft -> SaleDraftBubble(
                                entries = m.entries,
                                status = m.status,
                                onConfirm = { viewModel.confirmSale(m.id, it) },
                                onCancel = { viewModel.cancel(m.id) },
                            )
                            is ChatMessage.PaymentDraft -> PaymentDraftBubble(
                                party = m.party,
                                amount = m.amount,
                                status = m.status,
                                onConfirm = { p, a -> viewModel.confirmPayment(m.id, p, a) },
                                onCancel = { viewModel.cancel(m.id) },
                            )
                            is ChatMessage.PnlAnswer -> PnlAnswerBubble(m.summary)
                        }
                    }
                }
            }
        }

        if (routing || voice is VoiceState.Transcribing) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                CircularProgressIndicator(Modifier.height(16.dp).padding(end = 8.dp))
                Text(if (voice is VoiceState.Transcribing) "सुन रहा हूँ…" else "सोच रहा हूँ…")
            }
        }
        (voice as? VoiceState.Error)?.let { Text(it.message) }

        Row(
            Modifier.fillMaxWidth().padding(vertical = 8.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(4.dp),
        ) {
            OutlinedTextField(
                value = input,
                onValueChange = { input = it },
                modifier = Modifier.weight(1f),
                placeholder = { Text("लिखें या बोलें…") },
            )
            if (viewModel.voiceEnabled) {
                IconButton(onClick = { micPermLauncher.launch(Manifest.permission.RECORD_AUDIO) }) {
                    Icon(
                        if (voice is VoiceState.Recording) Icons.Filled.Stop else Icons.Filled.Mic,
                        contentDescription = "Voice",
                    )
                }
            }
            IconButton(onClick = {
                viewModel.send(input)
                input = ""
            }) {
                Icon(Icons.AutoMirrored.Filled.Send, contentDescription = "Send")
            }
        }
    }
}
```

- [ ] **Step 2: Build-check**

Run: `./gradlew :app:compileDebugKotlin`
Expected: BUILD SUCCESSFUL.

- [ ] **Step 3: Commit**

```bash
git add app/src/main/java/com/artha/kirana/ui/assistant/AssistantScreen.kt
git commit -m "feat(assistant): add AssistantScreen (chat thread + input + voice)"
```

---

## Task 12: Wire nav — Assistant route + protruding-center bottom bar

Replace `ArthaApp.kt` so the bottom bar has 4 corner items with a raised brand-gold center FAB that opens the Assistant.

**Files:**
- Modify: `app/src/main/java/com/artha/kirana/ui/ArthaApp.kt`

- [ ] **Step 1: Replace the whole file** `ArthaApp.kt` with:

```kotlin
package com.artha.kirana.ui

import android.Manifest
import android.os.Build
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.MenuBook
import androidx.compose.material.icons.filled.AutoAwesome
import androidx.compose.material.icons.filled.BarChart
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.filled.Inventory2
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.unit.dp
import androidx.navigation.NavController
import androidx.navigation.NavGraph.Companion.findStartDestination
import androidx.navigation.NavType
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import androidx.navigation.navArgument
import com.artha.kirana.ui.assistant.AssistantScreen
import com.artha.kirana.ui.entry.SaleEntryScreen
import com.artha.kirana.ui.home.HomeScreen
import com.artha.kirana.ui.inventory.InventoryScreen
import com.artha.kirana.ui.khata.KhataPartyDetail
import com.artha.kirana.ui.khata.KhataScreen
import com.artha.kirana.ui.pnl.PnlScreen
import com.artha.kirana.ui.theme.BrandGold

/** The four corner destinations (the center slot is the raised Assistant FAB). */
enum class TopDest(val route: String, val label: String, val icon: ImageVector) {
    Home("home", "Home", Icons.Filled.Home),
    Inventory("inventory", "Inventory", Icons.Filled.Inventory2),
    Khata("khata", "Khata", Icons.AutoMirrored.Filled.MenuBook),
    Pnl("pnl", "P&L", Icons.Filled.BarChart),
}

const val ROUTE_SALE_ENTRY = "sale_entry"
const val ROUTE_KHATA_DETAIL = "khata" // full pattern: khata/{partyId}
const val ROUTE_ASSISTANT = "assistant"

@Composable
fun ArthaApp() {
    val navController = rememberNavController()
    val navBackStackEntry by navController.currentBackStackEntryAsState()
    val currentRoute = navBackStackEntry?.destination?.route
    val barRoutes = TopDest.entries.map { it.route } + ROUTE_ASSISTANT

    val notifPermLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestPermission(),
    ) { /* result ignored — alerts simply stay silent if denied */ }
    LaunchedEffect(Unit) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            notifPermLauncher.launch(Manifest.permission.POST_NOTIFICATIONS)
        }
    }

    Scaffold(
        bottomBar = {
            if (currentRoute in barRoutes) {
                ArthaBottomBar(navController, currentRoute)
            }
        },
    ) { innerPadding ->
        NavHost(
            navController = navController,
            startDestination = TopDest.Home.route,
            modifier = Modifier.padding(innerPadding),
        ) {
            composable(TopDest.Home.route) { HomeScreen() }
            composable(TopDest.Inventory.route) { InventoryScreen() }
            composable(TopDest.Khata.route) {
                KhataScreen(onParty = { id -> navController.navigate("$ROUTE_KHATA_DETAIL/$id") })
            }
            composable(
                route = "$ROUTE_KHATA_DETAIL/{partyId}",
                arguments = listOf(navArgument("partyId") { type = NavType.LongType }),
            ) { KhataPartyDetail() }
            composable(TopDest.Pnl.route) { PnlScreen() }
            composable(ROUTE_ASSISTANT) { AssistantScreen() }
            composable(ROUTE_SALE_ENTRY) {
                SaleEntryScreen(onDone = { navController.popBackStack() })
            }
        }
    }
}

@Composable
private fun ArthaBottomBar(navController: NavController, currentRoute: String?) {
    fun go(route: String) {
        navController.navigate(route) {
            popUpTo(navController.graph.findStartDestination().id) { saveState = true }
            launchSingleTop = true
            restoreState = true
        }
    }
    Box(Modifier.fillMaxWidth()) {
        NavigationBar {
            BarItem(TopDest.Home, currentRoute, ::go)
            BarItem(TopDest.Inventory, currentRoute, ::go)
            Spacer(Modifier.weight(1f)) // center gap for the raised Assistant FAB
            BarItem(TopDest.Khata, currentRoute, ::go)
            BarItem(TopDest.Pnl, currentRoute, ::go)
        }
        FloatingActionButton(
            onClick = { go(ROUTE_ASSISTANT) },
            containerColor = BrandGold,
            modifier = Modifier
                .align(Alignment.TopCenter)
                .offset(y = (-24).dp)
                .size(64.dp),
        ) {
            Icon(Icons.Filled.AutoAwesome, contentDescription = "Assistant")
        }
    }
}

@Composable
private fun RowScope.BarItem(
    dest: TopDest,
    currentRoute: String?,
    onGo: (String) -> Unit,
) {
    NavigationBarItem(
        selected = currentRoute == dest.route,
        onClick = { onGo(dest.route) },
        icon = { Icon(dest.icon, contentDescription = dest.label) },
        label = { Text(dest.label) },
    )
}
```

- [ ] **Step 2: Build-check**

Run: `./gradlew :app:compileDebugKotlin`
Expected: BUILD SUCCESSFUL.
(Note: the old Home "New Sale" FAB is removed — the existing `ROUTE_SALE_ENTRY` screen is still reachable; Home can re-add its own FAB later if desired. This is intentional, not a regression of the sale data flow.)

- [ ] **Step 3: Commit**

```bash
git add app/src/main/java/com/artha/kirana/ui/ArthaApp.kt
git commit -m "feat(assistant): add Assistant route + protruding-center bottom nav"
```

---

## Task 13: validate-intent-prompt.py (live-server harness)

Mirrors `validate-sale-prompt.py`; asserts the classifier picks the right intent. Keep `SYSTEM_PROMPT` synced with `IntentRouter.INTENT_SYSTEM_PROMPT`.

**Files:**
- Create: `scripts/validate-intent-prompt.py`

- [ ] **Step 1: Create the script**

```python
#!/usr/bin/env python3
# Validate the intent-router prompt against the live llama-server.
# Setup:  ./scripts/start-llama-server.sh  &&  adb forward tcp:8080 tcp:8080
# Run:    python3 scripts/validate-intent-prompt.py
# IMPORTANT: keep SYSTEM_PROMPT below in sync with IntentRouter.INTENT_SYSTEM_PROMPT.
import json, urllib.request

URL = "http://localhost:8080/v1/chat/completions"

SYSTEM_PROMPT = """You are a router for a kirana shop assistant. Read the shopkeeper's message (Hindi/Hinglish) and output ONLY which action it wants, as JSON.
Return ONLY: {"intent": one of "log_sale" | "record_payment" | "query_pnl" | "unknown"}
No explanation. No markdown. Just the raw JSON object.

Meaning:
- log_sale = recording a sale/purchase of goods (items + quantity, cash or उधार/credit). e.g. selling rice, sugar, soap.
- record_payment = a customer PAID BACK money they owed (दिए / चुकाए / चुका दिया / जमा / paid). No goods involved.
- query_pnl = asking about earnings/profit/sales totals (कमाई, मुनाफा, बिक्री, कितना कमाया; today/week/month).
- unknown = anything else.

Examples:
Input: दो किलो चावल अस्सी रुपये उधार रमेश को
{"intent":"log_sale"}
Input: तीन साबुन बीस बीस के
{"intent":"log_sale"}
Input: रमेश ने पचास रुपये दिए
{"intent":"record_payment"}
Input: प्रिया ने अपना उधार चुका दिया सौ रुपये
{"intent":"record_payment"}
Input: आज की कमाई कितनी हुई
{"intent":"query_pnl"}
Input: इस हफ्ते का मुनाफा बताओ
{"intent":"query_pnl"}
Input: नमस्ते
{"intent":"unknown"}"""

RESPONSE_FORMAT = {
    "type": "json_schema",
    "json_schema": {
        "name": "intent",
        "schema": {
            "type": "object",
            "properties": {"intent": {"enum": ["log_sale", "record_payment", "query_pnl", "unknown"]}},
            "required": ["intent"],
        },
    },
}

# (input, expected_intent)
CASES = [
    ("दो किलो चावल अस्सी रुपये उधार रमेश को", "log_sale"),
    ("teen soap bees-bees ke", "log_sale"),
    ("पाँच किलो चीनी दो सौ रुपये", "log_sale"),
    ("रमेश ने पचास रुपये दिए", "record_payment"),
    ("प्रिया ने अपना उधार चुका दिया", "record_payment"),
    ("सुरेश ने दो सौ जमा किए", "record_payment"),
    ("आज की कमाई कितनी हुई", "query_pnl"),
    ("इस हफ्ते का मुनाफा बताओ", "query_pnl"),
    ("इस महीने की बिक्री", "query_pnl"),
    ("नमस्ते कैसे हो", "unknown"),
]

def extract_json(raw):
    s = raw.strip()
    for p in ("```json", "```"):
        if s.startswith(p): s = s[len(p):]
    s = s.rstrip("`").strip()
    a, b = s.find("{"), s.rfind("}")
    return s[a:b+1] if a != -1 and b > a else None

def call(text):
    body = json.dumps({
        "messages": [{"role": "system", "content": SYSTEM_PROMPT},
                     {"role": "user", "content": text}],
        "temperature": 0.1, "max_tokens": 64, "response_format": RESPONSE_FORMAT,
    }).encode()
    req = urllib.request.Request(URL, body, {"Content-Type": "application/json"})
    resp = json.loads(urllib.request.urlopen(req, timeout=60).read())
    return resp["choices"][0]["message"]["content"]

passed = 0
print(f"{'input':<42} {'got':<16} {'exp':<16} OK")
print("-" * 80)
for text, exp in CASES:
    try:
        js = extract_json(call(text))
        got = (json.loads(js).get("intent") if js else "?")
        ok = got == exp
        passed += ok
        print(f"{text[:40]:<42} {str(got):<16} {exp:<16} {'PASS' if ok else 'FAIL'}")
    except Exception as ex:
        print(f"{text[:40]:<42} ERROR: {ex}")
print("-" * 80)
print(f"RESULT: {passed}/{len(CASES)} passed")
```

- [ ] **Step 2: Run it against the live server**

Run:
```bash
./scripts/start-llama-server.sh
adb forward tcp:8080 tcp:8080
python3 scripts/validate-intent-prompt.py
```
Expected: a results table; aim for **10/10** (or ≥9/10). If an intent case fails, tighten `INTENT_SYSTEM_PROMPT` (add/adjust a few-shot example) and re-sync both copies, then re-run — do not change temperature first (same discipline as the sale prompt, CLAUDE.md §6/§18).

- [ ] **Step 3: Commit**

```bash
git add scripts/validate-intent-prompt.py
git commit -m "test(assistant): add validate-intent-prompt.py live-server harness"
```

---

## Task 14: On-device checkpoint (thin-slice exit criteria)

Full unit suite + device verification of the three slice flows. Per HANDOFF, the **human drives the UI** (adb-driving Compose is flaky).

**Files:** none (verification only).

- [ ] **Step 1: Run the full unit suite**

Run: `./gradlew :app:testDebugUnitTest`
Expected: BUILD SUCCESSFUL; all tests green (new + existing).

- [ ] **Step 2: Install on the iQOO with the server running**

Run:
```bash
adb devices                     # expect 10BFBG0CEL001DB
./scripts/start-llama-server.sh
./gradlew :app:installDebug
adb shell am start -n com.artha.kirana/.MainActivity
```
Expected: app launches; bottom bar shows Home · Inventory · [gold Assistant FAB] · Khata · P&L.

- [ ] **Step 3: Human verifies the three slice flows on the device**

1. Tap the center Assistant FAB → type **"दो किलो चावल अस्सी रुपये उधार रमेश को"** → expect a sale confirm card (item चावल, qty 2 kg, ₹80, credit, रमेश) → tap **Confirm** → expect "✓ बिक्री दर्ज" and the ledger/Home/P&L reflect it.
2. Type **"रमेश ने पचास रुपये दिए"** → expect a payment card (रमेश, ₹50) → **Confirm** → expect "✓ ... भुगतान दर्ज" and Ramesh's khata balance drops by ₹50 (check Khata tab).
3. Type **"आज की कमाई?"** → expect a P&L answer card with today's revenue/profit/cash/outstanding.
4. (Voice) Tap the mic, speak a sale in Hindi → transcript fills the input → tap Send → same flow as (1).
5. (Offline) Type anything with the server stopped (`adb shell "pkill -f llama-server"`) → expect "सर्वर बंद है — llama-server चालू करें।" (no crash).

- [ ] **Step 4: Update status docs**

Edit `docs/STATUS.md`: add an "Assistant (thin slice)" row marking the three intents working on-device; note the next step is expanding intents (`add_item`, `restock`, `query_khata`, `query_low_stock`, `market_insights`).

- [ ] **Step 5: Commit**

```bash
git add docs/STATUS.md
git commit -m "docs: mark Assistant thin slice verified on-device"
```

---

## Post-slice expansion (not in this plan)

After the slice proves out, add intents one at a time, each reusing an existing use-case and following the same pattern: add the enum value, extend `INTENT_SYSTEM_PROMPT` + the validate cases, add an extractor (if it needs args) or a query branch, map to a `ChatMessage`/`AssistantResult`, render a bubble. Targets: `add_item`, `restock` (`LogPurchaseUseCase`), `query_khata` (`GetKhataListUseCase`/observers), `query_low_stock` (`ItemsDao.lowStock`), `market_insights` (`GetMarketInsightsUseCase`, Phase 5).

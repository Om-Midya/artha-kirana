# Analytics-in-Chat + Demo Seeder Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Seed a rich dated demo dataset, and wire three analytics use-cases (top-sellers, per-customer, day-of-week) into the Assistant chat as new intents rendered as text bubbles.

**Architecture:** Extend the existing two-stage Assistant router (`IntentRouter` classify → `RouteAssistantUseCase` dispatch → `AssistantResult` → `AssistantViewModel.toMessage` → `ChatMessage.Reply`). Period detection stays deterministic (`PnlPeriodDetector` + a new shared `PnlPeriod.startFrom`); only the per-customer intent adds one LLM call (`LlmEngine.extractCustomerName`). A debug-only `DemoDataSeeder` inserts backdated sales directly via `SalesDao` so analytics have signal.

**Tech Stack:** Kotlin, Room (KSP), Hilt, kotlinx-serialization, JUnit4 + MockK + coroutines-test. Toolchain pinned: AGP 8.13 / Gradle 8.13 / JDK 17.

**Spec:** `docs/superpowers/specs/2026-06-14-analytics-chat-seeder-design.md`

**Commands:** Unit tests `./gradlew :app:testDebugUnitTest` · Build `./gradlew :app:assembleDebug` · Install `./gradlew :app:installDebug` · Device `10BFBG0CEL001DB`.

---

## Task 1: `DemoDataSeeder` (debug-only, idempotent) + MainActivity trigger

**Files:**
- Create: `app/src/main/java/com/artha/kirana/data/seed/DemoDataSeeder.kt`
- Modify: `app/src/main/java/com/artha/kirana/MainActivity.kt`

No unit test — DAO/integration code, verified by build + on-device (Task 7). Single commit.

- [ ] **Step 1: Create the seeder**

```kotlin
// app/src/main/java/com/artha/kirana/data/seed/DemoDataSeeder.kt
package com.artha.kirana.data.seed

import com.artha.kirana.data.db.dao.ItemsDao
import com.artha.kirana.data.db.dao.PurchasesDao
import com.artha.kirana.data.db.dao.SalesDao
import com.artha.kirana.data.db.entity.ItemEntity
import com.artha.kirana.data.db.entity.PurchaseEntity
import com.artha.kirana.data.db.entity.SaleEntity
import com.artha.kirana.domain.repository.CustomerRepository
import com.artha.kirana.domain.repository.KhataRepository
import javax.inject.Inject

/**
 * Seeds a realistic, dated demo dataset on a fresh (destructively-migrated) DB so every
 * analytic and the P&L screens have signal. Idempotent: no-ops once items exist. Debug-only —
 * the only caller is [com.artha.kirana.MainActivity] guarded by BuildConfig.DEBUG.
 *
 * Sales are inserted directly via [SalesDao] with backdated timestamps (the real LogSaleUseCase
 * would stamp now()); khata balances use the real applyCredit/applyRepayment so the
 * customer-keyed ledger path is exercised. [now] is injectable for determinism.
 */
class DemoDataSeeder @Inject constructor(
    private val itemsDao: ItemsDao,
    private val salesDao: SalesDao,
    private val purchasesDao: PurchasesDao,
    private val customers: CustomerRepository,
    private val khata: KhataRepository,
) {
    private val day = 24L * 60 * 60 * 1000

    suspend fun seedIfEmpty(now: Long = System.currentTimeMillis()) {
        if (itemsDao.getAllOnce().isNotEmpty()) return

        // --- items (varied margins; parle-g below its reorder threshold) ---
        val chawal = Item(itemsDao.insert(ItemEntity(name = "Chawal", nameHi = "चावल", qtyInStock = 42.0, unit = "kg", costPrice = 35.0, sellPrice = 45.0, reorderThreshold = 8.0, category = "Grains")), "चावल", 45.0, 35.0)
        val cheeni = Item(itemsDao.insert(ItemEntity(name = "Cheeni", nameHi = "चीनी", qtyInStock = 26.0, unit = "kg", costPrice = 40.0, sellPrice = 48.0, reorderThreshold = 5.0, category = "Grains")), "चीनी", 48.0, 40.0)
        val tel = Item(itemsDao.insert(ItemEntity(name = "Tel", nameHi = "तेल", qtyInStock = 14.0, unit = "litre", costPrice = 110.0, sellPrice = 130.0, reorderThreshold = 3.0, category = "Oil")), "तेल", 130.0, 110.0)
        val sabun = Item(itemsDao.insert(ItemEntity(name = "Sabun", nameHi = "साबुन", qtyInStock = 33.0, unit = "piece", costPrice = 18.0, sellPrice = 25.0, reorderThreshold = 6.0, category = "Toiletries")), "साबुन", 25.0, 18.0)
        val parle = Item(itemsDao.insert(ItemEntity(name = "Parle-G", nameHi = "पारले-जी", qtyInStock = 4.0, unit = "packet", costPrice = 8.0, sellPrice = 10.0, reorderThreshold = 6.0, category = "Biscuits")), "पारले-जी", 10.0, 8.0)

        // --- customers (romanized so the LLM-extracted name resolves via findByName) ---
        val ramesh = customers.resolveOrCreate("Ramesh")
        val priya = customers.resolveOrCreate("Priya")
        val suresh = customers.resolveOrCreate("Suresh")
        val anil = customers.resolveOrCreate("Anil")

        // --- sales: (daysAgo, item, qty, type, customerId, party) ---
        // Spread across ~4 weeks and varied weekdays; Fri/Sat heavier. Mix cash/credit/repayment.
        val rows = listOf(
            S(26, chawal, 5.0, "cash", null, null),
            S(25, tel, 2.0, "cash", null, null),
            S(24, sabun, 4.0, "credit", ramesh, "Ramesh"),
            S(23, chawal, 3.0, "cash", null, null),
            S(22, cheeni, 2.0, "cash", anil, "Anil"),
            S(21, parle, 6.0, "cash", null, null),
            S(20, tel, 1.0, "credit", priya, "Priya"),
            S(19, chawal, 4.0, "cash", null, null),
            S(18, sabun, 3.0, "cash", null, null),
            S(17, cheeni, 3.0, "credit", suresh, "Suresh"),
            S(15, chawal, 6.0, "cash", null, null),
            S(14, tel, 2.0, "cash", anil, "Anil"),
            S(13, parle, 5.0, "cash", null, null),
            S(12, sabun, 5.0, "credit", ramesh, "Ramesh"),
            S(11, chawal, 4.0, "cash", null, null),
            S(10, cheeni, 2.0, "cash", null, null),
            S(9, tel, 1.0, "cash", null, null),
            S(8, chawal, 5.0, "cash", priya, "Priya"),
            S(7, sabun, 6.0, "cash", null, null),
            S(6, parle, 8.0, "cash", null, null),
            S(5, chawal, 7.0, "cash", null, null),
            S(4, tel, 3.0, "credit", suresh, "Suresh"),
            S(3, cheeni, 4.0, "cash", null, null),
            S(2, sabun, 4.0, "cash", anil, "Anil"),
            S(1, chawal, 5.0, "cash", null, null),
            // repayments (no goods)
            S(16, null, 0.0, "repayment", ramesh, "Ramesh", amountOverride = 100.0),
            S(7, null, 0.0, "repayment", suresh, "Suresh", amountOverride = 120.0),
            S(2, null, 0.0, "repayment", priya, "Priya", amountOverride = 80.0),
        )
        for (r in rows) {
            val amount = r.amountOverride ?: (r.item!!.sell * r.qty)
            val id = salesDao.insert(
                SaleEntity(
                    itemId = r.item?.id,
                    itemName = r.item?.nameHi,
                    customerId = r.customerId,
                    qtySold = r.qty,
                    amount = amount,
                    unitPrice = r.item?.sell,
                    unitCost = r.item?.cost,
                    type = r.type,
                    party = r.party,
                    inputMethod = "seed",
                    timestamp = now - r.daysAgo * day,
                ),
            )
            when (r.type) {
                "credit" -> khata.applyCredit(r.party!!, amount, id)
                "repayment" -> khata.applyRepayment(r.party!!, amount, id)
            }
        }

        // --- purchases (supplier restocks) ---
        purchasesDao.insert(PurchaseEntity(itemId = tel.id, qty = 10.0, cost = 1100.0, supplier = "Gupta Traders", timestamp = now - 20 * day))
        purchasesDao.insert(PurchaseEntity(itemId = sabun.id, qty = 20.0, cost = 360.0, supplier = "Local Wholesaler", timestamp = now - 9 * day))
    }

    private data class Item(val id: Long, val nameHi: String, val sell: Double, val cost: Double)
    private data class S(
        val daysAgo: Int,
        val item: Item?,
        val qty: Double,
        val type: String,
        val customerId: Long?,
        val party: String?,
        val amountOverride: Double? = null,
    )
}
```

- [ ] **Step 2: Trigger it from `MainActivity` (debug-only)**

Replace `MainActivity.kt` with (adds the injected seeder + a `BuildConfig.DEBUG`-guarded call):
```kotlin
package com.artha.kirana

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.lifecycle.lifecycleScope
import com.artha.kirana.data.seed.DemoDataSeeder
import com.artha.kirana.ui.ArthaApp
import com.artha.kirana.ui.theme.ArthaTheme
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.launch
import javax.inject.Inject

@AndroidEntryPoint
class MainActivity : ComponentActivity() {

    @Inject lateinit var seeder: DemoDataSeeder

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        if (BuildConfig.DEBUG) {
            lifecycleScope.launch { seeder.seedIfEmpty() }
        }
        enableEdgeToEdge()
        setContent {
            ArthaTheme {
                ArthaApp()
            }
        }
    }
}
```
Note: `BuildConfig` is `com.artha.kirana.BuildConfig` (same package — no import needed). `ItemsDao`, `SalesDao`, `PurchasesDao` are Hilt-provided in `DatabaseModule`; `CustomerRepository`/`KhataRepository` are bound in `RepositoryModule` — so `DemoDataSeeder`'s `@Inject` constructor resolves with no module change.

- [ ] **Step 3: Build-verify**

Run: `./gradlew :app:assembleDebug`
Expected: BUILD SUCCESSFUL.

- [ ] **Step 4: Install + confirm seed on device**

Run: `./gradlew :app:installDebug && adb -s 10BFBG0CEL001DB shell am start -n com.artha.kirana/.MainActivity`
Expected: Home shows non-zero today/week revenue; Inventory shows 5 items with Parle-G flagged low (stock 4 < threshold 6); Khata shows Ramesh/Priya/Suresh with balances. (If the DB already had data from a prior run, uninstall first: `adb -s 10BFBG0CEL001DB uninstall com.artha.kirana` then reinstall.)

- [ ] **Step 5: Commit**

```bash
git add app/src/main/java/com/artha/kirana/data/seed/DemoDataSeeder.kt app/src/main/java/com/artha/kirana/MainActivity.kt
git commit -m "feat(seed): debug-only DemoDataSeeder with dated demo data; trigger from MainActivity"
```

---

## Task 2: Shared `PnlPeriod.startFrom` helper (refactor, TDD-safe)

**Files:**
- Create: `app/src/main/java/com/artha/kirana/domain/usecase/PnlPeriodRange.kt`
- Modify: `app/src/main/java/com/artha/kirana/domain/usecase/GetPnlSummaryUseCase.kt`
- Test: `app/src/test/java/com/artha/kirana/domain/usecase/PnlPeriodRangeTest.kt`

- [ ] **Step 1: Write the failing test**

```kotlin
// app/src/test/java/com/artha/kirana/domain/usecase/PnlPeriodRangeTest.kt
package com.artha.kirana.domain.usecase

import com.artha.kirana.domain.model.PnlPeriod
import com.artha.kirana.util.TimeRange
import org.junit.Assert.assertEquals
import org.junit.Test

class PnlPeriodRangeTest {
    private val now = 1_700_000_000_000L

    @Test
    fun mapsEachPeriodToItsStart() {
        assertEquals(TimeRange.startOfToday(now), PnlPeriod.TODAY.startFrom(now))
        assertEquals(TimeRange.startOfWeek(now), PnlPeriod.THIS_WEEK.startFrom(now))
        assertEquals(TimeRange.startOfMonth(now), PnlPeriod.THIS_MONTH.startFrom(now))
    }
}
```

- [ ] **Step 2: Run test to verify it fails**

Run: `./gradlew :app:testDebugUnitTest --tests "com.artha.kirana.domain.usecase.PnlPeriodRangeTest"`
Expected: FAIL — `Unresolved reference: startFrom`.

- [ ] **Step 3: Create the helper**

```kotlin
// app/src/main/java/com/artha/kirana/domain/usecase/PnlPeriodRange.kt
package com.artha.kirana.domain.usecase

import com.artha.kirana.domain.model.PnlPeriod
import com.artha.kirana.util.TimeRange

/** Start-of-window timestamp for a [PnlPeriod]. Single source for period→range mapping. */
fun PnlPeriod.startFrom(now: Long): Long = when (this) {
    PnlPeriod.TODAY -> TimeRange.startOfToday(now)
    PnlPeriod.THIS_WEEK -> TimeRange.startOfWeek(now)
    PnlPeriod.THIS_MONTH -> TimeRange.startOfMonth(now)
}
```

- [ ] **Step 4: Refactor `GetPnlSummaryUseCase` to use it**

In `GetPnlSummaryUseCase.kt`, replace the inline `val start = when (period) { ... }` block with:
```kotlin
        val start = period.startFrom(now)
```
(Keep `val end = Long.MAX_VALUE` and everything else. Remove the now-unused `TimeRange` import only if the compiler flags it.)

- [ ] **Step 5: Run tests to verify**

Run: `./gradlew :app:testDebugUnitTest --tests "com.artha.kirana.domain.usecase.PnlPeriodRangeTest" --tests "com.artha.kirana.domain.usecase.GetPnlSummaryUseCaseTest"`
Expected: PASS (new mapping test + existing P&L tests unchanged behavior).

- [ ] **Step 6: Commit**

```bash
git add app/src/main/java/com/artha/kirana/domain/usecase/PnlPeriodRange.kt app/src/main/java/com/artha/kirana/domain/usecase/GetPnlSummaryUseCase.kt app/src/test/java/com/artha/kirana/domain/usecase/PnlPeriodRangeTest.kt
git commit -m "refactor(pnl): extract shared PnlPeriod.startFrom range helper"
```

---

## Task 3: Add 3 analytics intents to the router (TDD)

**Files:**
- Modify: `app/src/main/java/com/artha/kirana/domain/model/AssistantIntent.kt`
- Modify: `app/src/main/java/com/artha/kirana/data/llm/IntentRouter.kt`
- Modify: `app/src/test/java/com/artha/kirana/data/llm/IntentRouterTest.kt`
- Modify: `scripts/validate-intent-prompt.py`

- [ ] **Step 1: Extend the enum**

Replace `AssistantIntent.kt` body:
```kotlin
package com.artha.kirana.domain.model

enum class AssistantIntent {
    LOG_SALE, RECORD_PAYMENT, QUERY_PNL,
    QUERY_TOP_SELLERS, QUERY_CUSTOMER, QUERY_DAY_TREND,
    UNKNOWN,
}
```

- [ ] **Step 2: Write the failing test** (extend `IntentRouterTest`)

Add to `IntentRouterTest.kt` inside the class:
```kotlin
    @Test
    fun parsesNewAnalyticsIntents() {
        assertEquals(AssistantIntent.QUERY_TOP_SELLERS, router.parseIntent("""{"intent":"query_top_sellers"}"""))
        assertEquals(AssistantIntent.QUERY_CUSTOMER, router.parseIntent("""{"intent":"query_customer"}"""))
        assertEquals(AssistantIntent.QUERY_DAY_TREND, router.parseIntent("""{"intent":"query_day_trend"}"""))
    }
```

- [ ] **Step 3: Run test to verify it fails**

Run: `./gradlew :app:testDebugUnitTest --tests "com.artha.kirana.data.llm.IntentRouterTest"`
Expected: FAIL — `query_top_sellers` etc. currently map to `UNKNOWN`.

- [ ] **Step 4: Update `parseIntent`, the response-format enum, and the prompt**

In `IntentRouter.kt`:

(a) `parseIntent` `when` — add the three branches before `else`:
```kotlin
                "log_sale" -> AssistantIntent.LOG_SALE
                "record_payment" -> AssistantIntent.RECORD_PAYMENT
                "query_pnl" -> AssistantIntent.QUERY_PNL
                "query_top_sellers" -> AssistantIntent.QUERY_TOP_SELLERS
                "query_customer" -> AssistantIntent.QUERY_CUSTOMER
                "query_day_trend" -> AssistantIntent.QUERY_DAY_TREND
                else -> AssistantIntent.UNKNOWN
```

(b) `INTENT_RESPONSE_FORMAT` enum array — replace the `putJsonArray("enum")` line with:
```kotlin
                            putJsonArray("enum") {
                                add("log_sale"); add("record_payment"); add("query_pnl")
                                add("query_top_sellers"); add("query_customer"); add("query_day_trend")
                                add("unknown")
                            }
```

(c) `INTENT_SYSTEM_PROMPT` — replace the whole constant with:
```kotlin
        const val INTENT_SYSTEM_PROMPT = """You are a router for a kirana shop assistant. Read the shopkeeper's message (Hindi/Hinglish) and output ONLY which action it wants, as JSON.
Return ONLY: {"intent": one of "log_sale" | "record_payment" | "query_pnl" | "query_top_sellers" | "query_customer" | "query_day_trend" | "unknown"}
No explanation. No markdown. Just the raw JSON object.

Meaning:
- log_sale = recording a sale/purchase of goods (items + quantity, cash or उधार/credit). e.g. selling rice, sugar, soap.
- record_payment = a customer PAID BACK money they owed (दिए / चुकाए / चुका दिया / जमा / paid). No goods involved.
- query_pnl = asking about TOTAL earnings/profit/sales over a period (कमाई, मुनाफा, बिक्री, कितना कमाया; today/week/month).
- query_top_sellers = asking WHICH ITEMS sell the most (सबसे ज्यादा क्या बिका, बेस्ट सेलर, टॉप आइटम, कौन सा सामान ज्यादा बिकता है). A per-item ranking, NOT a total.
- query_customer = asking about ONE customer's account: how much they owe, their total, their history (रमेश का हिसाब, प्रिया कितना बकाया है, सुरेश ने कुल कितना लिया). Names a person.
- query_day_trend = asking WHICH DAY/weekday is busiest or sells most (कौन सा दिन सबसे busy, किस दिन सबसे ज्यादा बिक्री, सबसे अच्छा दिन).
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
Input: सबसे ज्यादा क्या बिका
{"intent":"query_top_sellers"}
Input: इस महीने के टॉप आइटम कौन से हैं
{"intent":"query_top_sellers"}
Input: रमेश का हिसाब बताओ
{"intent":"query_customer"}
Input: प्रिया कितना बकाया है
{"intent":"query_customer"}
Input: कौन सा दिन सबसे busy रहता है
{"intent":"query_day_trend"}
Input: किस दिन सबसे ज्यादा बिक्री होती है
{"intent":"query_day_trend"}
Input: नमस्ते
{"intent":"unknown"}"""
```

- [ ] **Step 5: Run the test to verify it passes**

Run: `./gradlew :app:testDebugUnitTest --tests "com.artha.kirana.data.llm.IntentRouterTest"`
Expected: PASS (incl. existing `parsesEachKnownIntent`, `unknownStringFallsBackToUnknown`).

- [ ] **Step 6: Sync the validation script**

In `scripts/validate-intent-prompt.py`: copy the new `INTENT_SYSTEM_PROMPT` text into its `SYSTEM_PROMPT`, add the three new enum values to its response-format enum, and add test cases:
```python
    ("सबसे ज्यादा क्या बिका", "query_top_sellers"),
    ("इस महीने के टॉप आइटम", "query_top_sellers"),
    ("रमेश का हिसाब", "query_customer"),
    ("प्रिया कितना बकाया है", "query_customer"),
    ("कौन सा दिन सबसे busy रहता है", "query_day_trend"),
    ("किस दिन सबसे ज्यादा बिक्री", "query_day_trend"),
```
(Match the file's existing case-list structure — read it first to mirror the exact tuple/format.)

- [ ] **Step 7: Build-verify + commit**

Run: `./gradlew :app:assembleDebug` → BUILD SUCCESSFUL

```bash
git add app/src/main/java/com/artha/kirana/domain/model/AssistantIntent.kt app/src/main/java/com/artha/kirana/data/llm/IntentRouter.kt app/src/test/java/com/artha/kirana/data/llm/IntentRouterTest.kt scripts/validate-intent-prompt.py
git commit -m "feat(assistant): add top-sellers/customer/day-trend intents to router"
```

---

## Task 4: `LlmEngine.extractCustomerName` (stage-2 name extractor, TDD)

**Files:**
- Modify: `app/src/main/java/com/artha/kirana/data/llm/LlmEngine.kt`
- Test: `app/src/test/java/com/artha/kirana/data/llm/LlmEngineCustomerNameTest.kt`

The pure parse step (`parseCustomerName`) is unit-tested; the suspend `extractCustomerName` wraps it like `parsePayment`.

- [ ] **Step 1: Write the failing test**

```kotlin
// app/src/test/java/com/artha/kirana/data/llm/LlmEngineCustomerNameTest.kt
package com.artha.kirana.data.llm

import io.mockk.mockk
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class LlmEngineCustomerNameTest {
    private val engine = LlmEngine(client = mockk(relaxed = true), saleParser = SaleParser(), paymentParser = PaymentParser())

    @Test
    fun parsesCustomerName() {
        assertEquals("Ramesh", engine.parseCustomerName("""{"customer":"Ramesh"}"""))
    }

    @Test
    fun trimsAndStripsNullLiterals() {
        assertEquals("Priya", engine.parseCustomerName("""{"customer":"  Priya  "}"""))
        assertNull(engine.parseCustomerName("""{"customer":"null"}"""))
        assertNull(engine.parseCustomerName("""{"customer":null}"""))
    }

    @Test
    fun garbageReturnsNull() {
        assertNull(engine.parseCustomerName("not json"))
    }
}
```
Note: confirm `SaleParser()` / `PaymentParser()` have no-arg constructors (they do — `@Inject constructor()`). If a constructor differs, read the file and adjust the test's engine construction.

- [ ] **Step 2: Run test to verify it fails**

Run: `./gradlew :app:testDebugUnitTest --tests "com.artha.kirana.data.llm.LlmEngineCustomerNameTest"`
Expected: FAIL — `Unresolved reference: parseCustomerName`.

- [ ] **Step 3: Implement in `LlmEngine`**

(a) Add imports at the top of `LlmEngine.kt`:
```kotlin
import com.artha.kirana.data.remote.LlmUnavailableException
import com.artha.kirana.util.JsonParser
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
```
(`LlmUnavailableException` is already imported — don't duplicate.)

(b) Add a private json + DTO inside the class (top of the class body):
```kotlin
    private val json = Json { ignoreUnknownKeys = true; isLenient = true }
```
and at the bottom of the file (top-level, after the class) or as a private nested type:
```kotlin
@Serializable
private data class CustomerNameDto(val customer: String? = null)
```

(c) Add the suspend method + pure parser (next to `parsePayment`):
```kotlin
    /** Extracts the customer/person name mentioned in a query (romanized), or null. */
    suspend fun extractCustomerName(text: String): Result<String?> = try {
        val content = client.chat(CUSTOMER_NAME_SYSTEM_PROMPT, text, CUSTOMER_NAME_RESPONSE_FORMAT)
        Result.success(parseCustomerName(content))
    } catch (e: LlmUnavailableException) {
        Result.failure(e)
    }

    /** Pure: raw LLM content → customer name (trimmed) or null. Never throws. */
    fun parseCustomerName(raw: String): String? {
        val jsonStr = JsonParser.extractJson(raw) ?: return null
        return try {
            json.decodeFromString(CustomerNameDto.serializer(), jsonStr).customer
                ?.trim()
                ?.takeUnless { it.isEmpty() || it.equals("null", true) || it.equals("none", true) }
        } catch (t: Throwable) {
            null
        }
    }
```

(d) Add the prompt + response format to the `companion object`:
```kotlin
        const val CUSTOMER_NAME_SYSTEM_PROMPT = """You are a kirana shop assistant. The shopkeeper is asking about ONE customer's account. Extract just that customer's name as JSON.
Return ONLY: {"customer": string|null}
No explanation. No markdown. Just the raw JSON object.
- customer = the person's name ONLY, romanized to English letters (रमेश→Ramesh, प्रिया→Priya, सुरेश→Suresh). Remove का/को/ने/से tokens.
- If no person is named, return {"customer": null}.

Examples:
Input: रमेश का हिसाब बताओ
{"customer":"Ramesh"}
Input: प्रिया कितना बकाया है
{"customer":"Priya"}
Input: सुरेश ने कुल कितना लिया
{"customer":"Suresh"}
Input: आज की कमाई
{"customer":null}"""

        val CUSTOMER_NAME_RESPONSE_FORMAT = buildJsonObject {
            put("type", "json_schema")
            putJsonObject("json_schema") {
                put("name", "customer")
                putJsonObject("schema") {
                    put("type", "object")
                    putJsonObject("properties") {
                        putJsonObject("customer") { putJsonArray("type") { add("string"); add("null") } }
                    }
                    putJsonArray("required") { add("customer") }
                }
            }
        }
```

- [ ] **Step 4: Run test to verify it passes**

Run: `./gradlew :app:testDebugUnitTest --tests "com.artha.kirana.data.llm.LlmEngineCustomerNameTest"`
Expected: PASS (4).

- [ ] **Step 5: Build-verify + commit**

Run: `./gradlew :app:assembleDebug` → BUILD SUCCESSFUL

```bash
git add app/src/main/java/com/artha/kirana/data/llm/LlmEngine.kt app/src/test/java/com/artha/kirana/data/llm/LlmEngineCustomerNameTest.kt
git commit -m "feat(assistant): LlmEngine.extractCustomerName for per-customer queries"
```

---

## Task 5: Result variants + `RouteAssistantUseCase` dispatch (TDD)

**Files:**
- Modify: `app/src/main/java/com/artha/kirana/domain/model/AssistantResult.kt`
- Modify: `app/src/main/java/com/artha/kirana/domain/usecase/RouteAssistantUseCase.kt`
- Modify: `app/src/test/java/com/artha/kirana/domain/usecase/RouteAssistantUseCaseTest.kt`

- [ ] **Step 1: Add the result variants**

In `AssistantResult.kt`, add imports + variants:
```kotlin
import com.artha.kirana.domain.model.PnlPeriod  // if not already in-package; AssistantResult is in domain.model so no import needed
```
Add inside the sealed interface (PnlPeriod, TopSellerRow, CustomerSummary are all in `domain.model`, same package — no imports needed):
```kotlin
    /** Read-only top-sellers ranking for a period. */
    data class TopSellersAnswer(val period: PnlPeriod, val rows: List<TopSellerRow>) : AssistantResult

    /** Read-only one-customer summary. */
    data class CustomerAnswer(val name: String, val summary: CustomerSummary) : AssistantResult

    /** Read-only revenue-by-weekday (index 0 = Sunday). */
    data class DayTrendAnswer(val period: PnlPeriod, val buckets: DoubleArray) : AssistantResult
```
Note: `DoubleArray` in a data class triggers an IDE warning about equals/hashCode; that is acceptable here (these are transient UI messages, never compared). Do not add custom equals.

- [ ] **Step 2: Write the failing tests** (extend `RouteAssistantUseCaseTest`)

First read `RouteAssistantUseCaseTest.kt` to mirror its existing mock setup (it mocks `IntentRouter`, `ParseSaleEntryUseCase`, `LlmEngine`, `GetPnlSummaryUseCase`). Add the new collaborators to the constructor and these tests:
```kotlin
    @Test
    fun topSellersIntentReturnsRankingForDetectedPeriod() = runTest {
        coEvery { intentRouter.classify(any()) } returns Result.success(AssistantIntent.QUERY_TOP_SELLERS)
        val rows = listOf(TopSellerRow(1L, "चावल", 9.0, 450.0))
        coEvery { getTopSellers(any(), any()) } returns rows

        val result = useCase("इस हफ्ते सबसे ज्यादा क्या बिका")

        assertTrue(result is AssistantResult.TopSellersAnswer)
        result as AssistantResult.TopSellersAnswer
        assertEquals(PnlPeriod.THIS_WEEK, result.period)
        assertEquals(rows, result.rows)
    }

    @Test
    fun customerIntentResolvesNameToSummary() = runTest {
        coEvery { intentRouter.classify(any()) } returns Result.success(AssistantIntent.QUERY_CUSTOMER)
        coEvery { engine.extractCustomerName(any()) } returns Result.success("Ramesh")
        coEvery { customers.findByName("Ramesh") } returns CustomerEntity(id = 3, name = "Ramesh")
        coEvery { getCustomerSummary(3L) } returns CustomerSummary(3L, 500.0, 120.0)

        val result = useCase("रमेश का हिसाब")

        assertTrue(result is AssistantResult.CustomerAnswer)
        result as AssistantResult.CustomerAnswer
        assertEquals("Ramesh", result.name)
        assertEquals(120.0, result.summary.outstanding, 0.001)
    }

    @Test
    fun customerIntentNotFoundReplies() = runTest {
        coEvery { intentRouter.classify(any()) } returns Result.success(AssistantIntent.QUERY_CUSTOMER)
        coEvery { engine.extractCustomerName(any()) } returns Result.success("Mystery")
        coEvery { customers.findByName("Mystery") } returns null

        val result = useCase("मिस्ट्री का हिसाब")

        assertTrue(result is AssistantResult.Reply)
    }

    @Test
    fun customerIntentNullNameAsksWhich() = runTest {
        coEvery { intentRouter.classify(any()) } returns Result.success(AssistantIntent.QUERY_CUSTOMER)
        coEvery { engine.extractCustomerName(any()) } returns Result.success(null)

        val result = useCase("हिसाब बताओ")

        assertTrue(result is AssistantResult.Reply)
    }

    @Test
    fun dayTrendIntentReturnsBuckets() = runTest {
        coEvery { intentRouter.classify(any()) } returns Result.success(AssistantIntent.QUERY_DAY_TREND)
        coEvery { getDayTrend(any(), any()) } returns DoubleArray(7) { it.toDouble() }

        val result = useCase("कौन सा दिन busy")

        assertTrue(result is AssistantResult.DayTrendAnswer)
    }
```
Add imports to the test: `CustomerEntity`, `CustomerSummary`, `TopSellerRow`, `PnlPeriod`, `assertTrue`, `assertEquals`.

- [ ] **Step 3: Run tests to verify they fail**

Run: `./gradlew :app:testDebugUnitTest --tests "com.artha.kirana.domain.usecase.RouteAssistantUseCaseTest"`
Expected: FAIL — constructor arity / new branches missing.

- [ ] **Step 4: Update `RouteAssistantUseCase`**

Add the new collaborators to the constructor and the three `when` branches. The constructor becomes:
```kotlin
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
```
Add imports: `com.artha.kirana.domain.repository.CustomerRepository` (the 3 analytics use-cases are in the same `domain.usecase` package as this file, and `startFrom` is the top-level helper from Task 2 also in `domain.usecase` — so no imports needed for those). In the `when (intent)` add before `AssistantIntent.UNKNOWN`:
```kotlin
            AssistantIntent.QUERY_TOP_SELLERS -> {
                val period = PnlPeriodDetector.detect(text)
                AssistantResult.TopSellersAnswer(period, getTopSellers(period.startFrom(System.currentTimeMillis()), Long.MAX_VALUE))
            }

            AssistantIntent.QUERY_DAY_TREND -> {
                val period = PnlPeriodDetector.detect(text)
                AssistantResult.DayTrendAnswer(period, getDayTrend(period.startFrom(System.currentTimeMillis()), Long.MAX_VALUE))
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
```
Add to the `companion object`:
```kotlin
        const val ASK_WHICH_CUSTOMER = "किस ग्राहक का हिसाब? नाम बताएँ।"
        fun customerNotFound(name: String) = "ग्राहक '$name' नहीं मिला।"
```

- [ ] **Step 5: Run tests + build**

Run: `./gradlew :app:testDebugUnitTest --tests "com.artha.kirana.domain.usecase.RouteAssistantUseCaseTest"` → PASS
Run: `./gradlew :app:assembleDebug` → BUILD SUCCESSFUL (Hilt resolves the new use-case/repo params — all already bound).

- [ ] **Step 6: Commit**

```bash
git add app/src/main/java/com/artha/kirana/domain/model/AssistantResult.kt app/src/main/java/com/artha/kirana/domain/usecase/RouteAssistantUseCase.kt app/src/test/java/com/artha/kirana/domain/usecase/RouteAssistantUseCaseTest.kt
git commit -m "feat(assistant): dispatch top-sellers/customer/day-trend to analytics use-cases"
```

---

## Task 6: `AnalyticsChatFormatter` + ViewModel rendering (TDD)

**Files:**
- Create: `app/src/main/java/com/artha/kirana/ui/assistant/AnalyticsChatFormatter.kt`
- Modify: `app/src/main/java/com/artha/kirana/ui/assistant/AssistantViewModel.kt`
- Test: `app/src/test/java/com/artha/kirana/ui/assistant/AnalyticsChatFormatterTest.kt`

- [ ] **Step 1: Write the failing test**

```kotlin
// app/src/test/java/com/artha/kirana/ui/assistant/AnalyticsChatFormatterTest.kt
package com.artha.kirana.ui.assistant

import com.artha.kirana.domain.model.CustomerSummary
import com.artha.kirana.domain.model.PnlPeriod
import com.artha.kirana.domain.model.TopSellerRow
import org.junit.Assert.assertTrue
import org.junit.Test

class AnalyticsChatFormatterTest {

    @Test
    fun topSellersListsRankedRows() {
        val text = AnalyticsChatFormatter.topSellers(
            PnlPeriod.THIS_WEEK,
            listOf(TopSellerRow(1, "चावल", 9.0, 450.0), TopSellerRow(2, "तेल", 3.0, 390.0)),
        )
        assertTrue(text.contains("चावल"))
        assertTrue(text.contains("450"))
        assertTrue(text.contains("1.")) // ranked
    }

    @Test
    fun topSellersEmptyShowsNoData() {
        val text = AnalyticsChatFormatter.topSellers(PnlPeriod.TODAY, emptyList())
        assertTrue(text.contains("कोई"))
    }

    @Test
    fun customerShowsNameOutstandingAndTotal() {
        val text = AnalyticsChatFormatter.customer("Ramesh", CustomerSummary(3, 500.0, 120.0))
        assertTrue(text.contains("Ramesh"))
        assertTrue(text.contains("500"))
        assertTrue(text.contains("120"))
    }

    @Test
    fun dayTrendMarksBusiestDay() {
        val buckets = DoubleArray(7).also { it[5] = 300.0; it[0] = 50.0 } // Friday busiest
        val text = AnalyticsChatFormatter.dayTrend(PnlPeriod.THIS_MONTH, buckets)
        assertTrue(text.contains("शुक्र")) // Friday label present
        assertTrue(text.contains("300"))
    }
}
```

- [ ] **Step 2: Run test to verify it fails**

Run: `./gradlew :app:testDebugUnitTest --tests "com.artha.kirana.ui.assistant.AnalyticsChatFormatterTest"`
Expected: FAIL — `Unresolved reference: AnalyticsChatFormatter`.

- [ ] **Step 3: Implement the formatter**

```kotlin
// app/src/main/java/com/artha/kirana/ui/assistant/AnalyticsChatFormatter.kt
package com.artha.kirana.ui.assistant

import com.artha.kirana.domain.model.CustomerSummary
import com.artha.kirana.domain.model.PnlPeriod
import com.artha.kirana.domain.model.TopSellerRow

/** Formats read-only analytics results into Hindi/Hinglish chat-bubble text. Pure + testable. */
object AnalyticsChatFormatter {

    private fun periodLabel(p: PnlPeriod): String = when (p) {
        PnlPeriod.TODAY -> "आज"
        PnlPeriod.THIS_WEEK -> "इस हफ्ते"
        PnlPeriod.THIS_MONTH -> "इस महीने"
    }

    private val weekdays = listOf("रवि", "सोम", "मंगल", "बुध", "गुरु", "शुक्र", "शनि")

    private fun rupees(v: Double): String = "₹${v.toLong()}"

    fun topSellers(period: PnlPeriod, rows: List<TopSellerRow>): String {
        if (rows.isEmpty()) return "${periodLabel(period)} की कोई बिक्री नहीं मिली।"
        val lines = rows.take(5).mapIndexed { i, r ->
            "${i + 1}. ${r.itemName ?: "अन्य"} — ${rupees(r.revenue)} (${r.qty.toLong()})"
        }
        return "📊 ${periodLabel(period)} के टॉप आइटम:\n" + lines.joinToString("\n")
    }

    fun customer(name: String, summary: CustomerSummary): String =
        "👤 $name\nकुल खरीदा: ${rupees(summary.lifetimeValue)}\nबकाया: ${rupees(summary.outstanding)}"

    fun dayTrend(period: PnlPeriod, buckets: DoubleArray): String {
        if (buckets.all { it == 0.0 }) return "${periodLabel(period)} का कोई डेटा नहीं।"
        val maxIdx = buckets.indices.maxByOrNull { buckets[it] } ?: 0
        val lines = buckets.indices.map { i ->
            val mark = if (i == maxIdx) " ⭐" else ""
            "${weekdays[i]}: ${rupees(buckets[i])}$mark"
        }
        return "📅 ${periodLabel(period)} (दिन के हिसाब से):\n" + lines.joinToString("\n")
    }
}
```

- [ ] **Step 4: Run test to verify it passes**

Run: `./gradlew :app:testDebugUnitTest --tests "com.artha.kirana.ui.assistant.AnalyticsChatFormatterTest"`
Expected: PASS (4).

- [ ] **Step 5: Wire into `AssistantViewModel.toMessage`**

In `AssistantViewModel.kt`, add the three cases to the `AssistantResult.toMessage(id)` `when` (before the `Unavailable` case), and import `AnalyticsChatFormatter` (same package — no import needed) plus the result types are in `domain.model` (already importable via `AssistantResult`):
```kotlin
        is AssistantResult.TopSellersAnswer -> ChatMessage.Reply(id, AnalyticsChatFormatter.topSellers(period, rows))
        is AssistantResult.CustomerAnswer -> ChatMessage.Reply(id, AnalyticsChatFormatter.customer(name, summary))
        is AssistantResult.DayTrendAnswer -> ChatMessage.Reply(id, AnalyticsChatFormatter.dayTrend(period, buckets))
```

- [ ] **Step 6: Run full suite + build**

Run: `./gradlew :app:testDebugUnitTest` → all green
Run: `./gradlew :app:assembleDebug` → BUILD SUCCESSFUL

- [ ] **Step 7: Commit**

```bash
git add app/src/main/java/com/artha/kirana/ui/assistant/AnalyticsChatFormatter.kt app/src/main/java/com/artha/kirana/ui/assistant/AssistantViewModel.kt app/src/test/java/com/artha/kirana/ui/assistant/AnalyticsChatFormatterTest.kt
git commit -m "feat(assistant): render analytics answers as chat text bubbles"
```

---

## Task 7: On-device verification (manual gate)

**No code.** Requires the iQOO + llama-server (`./scripts/start-llama-server.sh`).

- [ ] **Step 1: Install (fresh)** — `adb -s 10BFBG0CEL001DB uninstall com.artha.kirana; ./gradlew :app:installDebug; adb -s 10BFBG0CEL001DB shell am start -n com.artha.kirana/.MainActivity`. Confirm seeded data appears (Home revenue, Inventory 5 items / Parle-G low, Khata balances).
- [ ] **Step 2: Live intent validation** — `adb -s 10BFBG0CEL001DB forward tcp:8080 tcp:8080 && python3 scripts/validate-intent-prompt.py`. Expect the 4 existing + 3 new cases to classify correctly. If a new intent misclassifies, tighten the prompt examples in `IntentRouter.INTENT_SYSTEM_PROMPT` (+ sync the script) and re-run — do NOT add LLM stages.
- [ ] **Step 3: Chat-test the 3 new intents** in the Assistant tab:
  - "सबसे ज्यादा क्या बिका" → top-sellers list bubble.
  - "इस हफ्ते के टॉप आइटम" → week-scoped list.
  - "रमेश का हिसाब" → Ramesh summary (कुल खरीदा + बकाया).
  - "कौन सा दिन सबसे busy" → weekday list with ⭐ on the busiest.
- [ ] **Step 4: Non-regression** — confirm "दो किलो चावल अस्सी का" (log_sale), "रमेश ने सौ रुपये दिए" (record_payment), "आज की कमाई" (query_pnl) still work.
- [ ] **Step 5: Update STATUS** — note analytics-in-chat + seeder done & device-verified; commit `docs/STATUS.md`.

---

## Self-review notes (for the executor)

- **Spec coverage:** §4.1 router=Task 3; §4.2 extractor=Task 4; §4.3 dispatch=Task 5; §4.4 variants+formatter=Tasks 5–6; §5 seeder=Task 1; §6 startFrom helper=Task 2; §7 testing across tasks; §8 order honored (seeder first).
- **Type consistency:** `period.startFrom(now)` (Task 2) used in Task 5; `AnalyticsChatFormatter.{topSellers,customer,dayTrend}` (Task 6) match the `AssistantResult.{TopSellersAnswer(period,rows),CustomerAnswer(name,summary),DayTrendAnswer(period,buckets)}` variants (Task 5); `TopSellerRow(itemId,itemName,qty,revenue)` and `CustomerSummary(customerId,lifetimeValue,outstanding)` match their definitions; `parseCustomerName` (Task 4) used by `extractCustomerName`.
- **Seeder writes `itemName = nameHi`** (Devanagari) so top-sellers bubbles show Hindi item names; sales `inputMethod="seed"`. Khata via real `applyCredit/applyRepayment` → correct balances.
- **No DI module changes** anywhere — every new injected type (`DemoDataSeeder`, the 3 analytics use-cases, `CustomerRepository`) is already provided/bound.

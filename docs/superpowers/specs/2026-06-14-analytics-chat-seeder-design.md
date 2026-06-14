# Analytics-in-Chat + Demo Seeder — Design Spec

**Date:** 2026-06-14 · **Status:** ✅ Approved (brainstorm complete) — ready for plan.
**Branch:** new branch off `main` (data-layer v3 already merged at `6d81b41`).
**Related:** `CLAUDE.md` (§5 prompts, §6 seeder, §13 UI, §16 standards), the data-layer v3 spec (`2026-06-14-data-layer-restructure-design.md`), `docs/STATUS.md`.

---

## 1. Goal & motivation

The four analytics use-cases built in data-layer v3 (`GetTopSellersUseCase`, `GetItemMarginsUseCase`, `GetCustomerSummaryUseCase`, `GetDayOfWeekTrendUseCase`) are data-layer building blocks **referenced nowhere** — they cannot be exercised from the app. The Assistant chat understands only `LOG_SALE`, `RECORD_PAYMENT`, `QUERY_PNL`, `UNKNOWN`. And there is no seeded data, so even existing P&L queries return zeros on a fresh (destructively-migrated) v3 DB.

This spec makes analytics testable conversationally and gives the app realistic data:
1. **Seed** a rich, dated demo dataset so every analytic has signal.
2. **Wire** three analytics use-cases into the Assistant as new intents, rendered as text bubbles.

## 2. Resolved decisions (from brainstorming)

| Question | Decision |
|---|---|
| Which analytics in chat | **Curated 3:** top-sellers, per-customer, day-of-week trend. **Margins dropped** (most confusable with `query_pnl` on the 3B; the use-case still exists for a later verify readout). |
| Rendering | **Formatted text bubbles** — reuse `ChatMessage.Reply`; no new Compose cards. |
| Seed data richness | **Rich & dated** — ~28 sales over ~4 weeks / varied weekdays, 5 items with varied margins, 4 customers mixing cash + credit, repayments, 1 near-threshold item. |
| Router-growth risk (4→7 intents) | Accepted; fallback if `validate-intent-prompt.py` regresses = **tighten prompt examples**, not add LLM stages. |
| Customer-name match | Rely on the LLM romanizing names (е.g. "रमेश"→"Ramesh", as the §18 sale path already does); seed customer names **romanized** so `findByName` (COLLATE NOCASE) hits. |

## 3. Current architecture (grounding)

- `AssistantIntent` (`domain/model`): `enum { LOG_SALE, RECORD_PAYMENT, QUERY_PNL, UNKNOWN }`.
- `IntentRouter` (`data/llm`): stage-1 classify via one enum `json_schema` LLM call. Holds `INTENT_SYSTEM_PROMPT`, `INTENT_RESPONSE_FORMAT` (enum array), `parseIntent(raw): AssistantIntent`. Kept in sync with `scripts/validate-intent-prompt.py`. 10/10 live today.
- `RouteAssistantUseCase` (`domain/usecase`): `invoke(text): AssistantResult` — `intentRouter.classify` then `when(intent)` dispatch. Has `parseSale`, `engine` (`LlmEngine`), `getPnl`. Uses `PnlPeriodDetector.detect(text)` for P&L period. `warmUp()` primes the cache.
- `AssistantResult` (`domain/model`): sealed interface — `SaleDraft`, `PaymentDraft`, `PnlAnswer(summary)`, `Reply(text)`, `Unavailable`.
- `ChatMessage` (`ui/assistant`): sealed interface — `User`, `Reply`, `SaleDraft`, `PaymentDraft`, `PnlAnswer`. `AssistantViewModel.toMessage(id)` maps `AssistantResult` → `ChatMessage`. `AssistantMessages.kt` renders them.
- `PnlPeriodDetector.detect(text): PnlPeriod` — deterministic keyword → `{TODAY, THIS_WEEK, THIS_MONTH}`.
- `TimeRange` (`util`): `startOfToday/startOfWeek/startOfMonth(now): Long`.
- Analytics use-cases (all `suspend`, one-shot): `GetTopSellersUseCase(start,end): List<TopSellerRow>`; `GetCustomerSummaryUseCase(customerId): CustomerSummary`; `GetDayOfWeekTrendUseCase(start,end): DoubleArray` (index 0=Sun).
- `CustomerRepository.findByName(name): CustomerEntity?` (COLLATE NOCASE). `ItemsDao.getAllOnce(): List<ItemEntity>` (seeder guard), `ItemsDao.insert`, `SalesDao.insert`, `PurchasesDao.insert`, `KhataRepository.applyCredit/applyRepayment`.
- `MainActivity` (`com.artha.kirana`): `@AndroidEntryPoint ComponentActivity`, no injected fields yet. `ArthaApplication` is `@HiltAndroidApp`.

## 4. Feature A — three new analytics intents

### 4.1 Enum + router (keep all four in lockstep)
`AssistantIntent` → add `QUERY_TOP_SELLERS`, `QUERY_CUSTOMER`, `QUERY_DAY_TREND`.

`IntentRouter`:
- `INTENT_RESPONSE_FORMAT` enum array → add `"query_top_sellers"`, `"query_customer"`, `"query_day_trend"`.
- `parseIntent` `when` → map those three strings; everything else falls to `UNKNOWN`.
- `INTENT_SYSTEM_PROMPT` → add meanings + 2 examples each:
  - `query_top_sellers` = which items sell most (बेस्ट सेलर, सबसे ज्यादा क्या बिका, टॉप आइटम). NOT total profit.
  - `query_customer` = one customer's account: balance owed / total taken / history (रमेश का हिसाब, प्रिया कितना बकाया है). Names a person.
  - `query_day_trend` = which weekday is busiest (कौन सा दिन सबसे busy, किस दिन सबसे ज्यादा बिक्री).
  - Disambiguation line: `query_pnl` is the TOTAL (कमाई/मुनाफा/बिक्री); `query_top_sellers` is per-item ranking; `query_customer` names a person; `query_day_trend` asks about a day.
- `scripts/validate-intent-prompt.py` → mirror the prompt + add the 3 new cases to its test set. Target: the 4 existing cases still classify correctly **and** the 3 new ones do.

### 4.2 Customer-name extractor (stage 2 for `query_customer`)
Add to `LlmEngine`: `suspend fun extractCustomerName(text: String): Result<String?>` — one `json_schema` call, schema `{customer: string|null}`, system prompt instructing: extract the customer/person name mentioned, romanize Devanagari names (रमेश→Ramesh), return null if none. Reuses `LlmHttpClient` + `JsonParser` like `parsePayment`. (Keep small; this is the only new LLM call.)

### 4.3 Dispatch (`RouteAssistantUseCase`)
Add three `when` branches (inject `GetTopSellersUseCase`, `GetCustomerSummaryUseCase`, `GetDayOfWeekTrendUseCase`, `CustomerRepository`):

```kotlin
AssistantIntent.QUERY_TOP_SELLERS -> {
    val period = PnlPeriodDetector.detect(text)
    AssistantResult.TopSellersAnswer(period, getTopSellers(period.startFrom(now()), Long.MAX_VALUE))
}
AssistantIntent.QUERY_DAY_TREND -> {
    val period = PnlPeriodDetector.detect(text)
    AssistantResult.DayTrendAnswer(period, getDayTrend(period.startFrom(now()), Long.MAX_VALUE))
}
AssistantIntent.QUERY_CUSTOMER -> engine.extractCustomerName(text).fold(
    onSuccess = { name ->
        if (name.isNullOrBlank()) AssistantResult.Reply(ASK_WHICH_CUSTOMER)
        else customers.findByName(name)?.let {
            AssistantResult.CustomerAnswer(it.name, getCustomerSummary(it.id))
        } ?: AssistantResult.Reply(customerNotFound(name))
    },
    onFailure = { AssistantResult.Unavailable },
)
```
- `period.startFrom(now())` uses the shared `PnlPeriod.startFrom` helper (§6); `now()` is `System.currentTimeMillis()`. Same mapping `GetPnlSummaryUseCase` uses. `it.name` is the resolved `CustomerEntity.name` (the romanized stored name).
- `extractCustomerName` wrapped so an unreachable server → `Unavailable` (consistent with other branches).
- New companion strings: `ASK_WHICH_CUSTOMER = "किस ग्राहक का हिसाब?"`, `customerNotFound(name) = "ग्राहक '$name' नहीं मिला।"`.

### 4.4 Result variants + rendering (text bubbles)
`AssistantResult` → add data-carrying variants (domain stays data-only, like `PnlAnswer`):
```kotlin
data class TopSellersAnswer(val period: PnlPeriod, val rows: List<TopSellerRow>) : AssistantResult
data class CustomerAnswer(val name: String, val summary: CustomerSummary) : AssistantResult
data class DayTrendAnswer(val period: PnlPeriod, val buckets: DoubleArray) : AssistantResult
```
Rendering: a new presentation formatter `AnalyticsChatFormatter` (`ui/assistant`) with pure functions:
- `topSellers(period, rows): String` → e.g. `"📊 इस हफ्ते के टॉप आइटम:\n1. चावल — ₹450 (9 किलो)\n2. तेल — ₹390 ..."`; empty → "अभी कोई बिक्री डेटा नहीं।"
- `customer(name, summary): String` → e.g. `"👤 Ramesh\nकुल खरीदा: ₹X\nबकाया: ₹Y"`.
- `dayTrend(period, buckets): String` → list 7 weekday labels (रवि…शनि) with revenue, mark the max; empty → no-data line.

`AssistantViewModel.toMessage` maps the three new results → `ChatMessage.Reply(id, AnalyticsChatFormatter.x(...))`. **No new `ChatMessage` types, no new composables.** `AnalyticsChatFormatter` is pure → unit-testable.

## 5. Feature B — `DemoDataSeeder` (debug-only, idempotent)

New `data/seed/DemoDataSeeder.kt`, `@Inject` constructor with `ItemsDao`, `SalesDao`, `PurchasesDao`, `CustomerRepository`, `KhataRepository`. One public `suspend fun seedIfEmpty()`:
1. Guard: `if (itemsDao.getAllOnce().isNotEmpty()) return` (idempotent — only seeds a fresh/wiped DB).
2. **Items** (insert, capture ids): chawal (cost 35 / sell 45, stock 40, unit किलो, reorder 8), cheeni (40/48, 25, किलो, 5), tel (110/130, 12, लीटर, 3), sabun (18/25, 30, piece, 6), parle-g (8/10, **4**, piece, reorder **6** → near/below threshold).
3. **Customers** (via `customers.resolveOrCreate`, romanized): Ramesh, Priya, Suresh, Anil → capture ids.
4. **Sales** (~28) inserted **directly via `SalesDao.insert`** with **backdated `timestamp`** (a deterministic spread: a base `now` passed in, minus offsets across the last ~28 days and varied weekdays). Each sale sets `itemId`, `itemName`, `customerId` (for the named ones; null for anonymous cash), `qtySold`, `amount = sellPrice*qty`, `unitPrice`/`unitCost` snapshots from the item, `type`, `party`, `inputMethod = "seed"`. Mix: ~18 cash, ~7 credit (across Ramesh/Priya/Suresh/Anil), ~3 repayment.
5. **Khata balances**: for each credit sale call `khata.applyCredit(party, amount, saleId)`; for each repayment `khata.applyRepayment(party, amount, saleId)` — using the inserted sale's returned id. This exercises the real customer-keyed path and yields correct outstanding balances. (Khata txn timestamps = now; only sale timestamps drive P&L/analytics, so this is fine.)
6. **Stock**: insert items with their post-sale stock directly (no per-sale decrement needed — seed controls final numbers).
7. **Purchases** (2): via `PurchasesDao.insert` (e.g. a tel restock, a sabun restock) with backdated timestamps.

Determinism: `seedIfEmpty(now: Long = System.currentTimeMillis())` takes `now` so timestamps are computed from it (no hidden clock reads scattered around). `Math.random()`/varied data is hard-coded, not random.

**Trigger:** in `MainActivity`, add `@Inject lateinit var seeder: DemoDataSeeder` and in `onCreate`:
```kotlin
if (BuildConfig.DEBUG) {
    lifecycleScope.launch { seeder.seedIfEmpty() }
}
```
(`@AndroidEntryPoint` already present → field injection works.) Runs off the main thread inside the seeder (DAOs are `suspend`). Because the v3 migration is destructive, first launch after install starts empty → seeder fills it; subsequent launches no-op via the guard.

## 6. Shared period→range helper (small refactor)
`RouteAssistantUseCase` (top-sellers/day-trend) and `GetPnlSummaryUseCase` both map `PnlPeriod` → start timestamp. Extract a single helper to avoid duplication:
```kotlin
// domain/usecase/PnlPeriod range mapping (e.g. in TimeRange or a small extension)
fun PnlPeriod.startFrom(now: Long): Long = when (this) {
    PnlPeriod.TODAY -> TimeRange.startOfToday(now)
    PnlPeriod.THIS_WEEK -> TimeRange.startOfWeek(now)
    PnlPeriod.THIS_MONTH -> TimeRange.startOfMonth(now)
}
```
`GetPnlSummaryUseCase` refactors to use it (behavior identical — verify its existing tests still pass). `RouteAssistantUseCase` uses it for the two period-based analytics.

## 7. Testing

- **Unit (pure / mockk):**
  - `IntentRouter.parseIntent` → the 3 new strings map to the new enums; garbage → UNKNOWN (extend `IntentRouterTest`).
  - `AnalyticsChatFormatter` → fixed rows/summary/buckets → expected strings incl. empty-data lines (new test).
  - `RouteAssistantUseCase` (mockk) → `QUERY_CUSTOMER`: found → CustomerAnswer; not-found → Reply(notFound); null name → Reply(ask); extractor failure → Unavailable. `QUERY_TOP_SELLERS`/`QUERY_DAY_TREND` → correct use-case called with the detected period's range (verify period mapping).
  - `PnlPeriod.startFrom` mapping + `GetPnlSummaryUseCase` existing tests still green.
- **Live prompt:** `scripts/validate-intent-prompt.py` against the running llama-server — 4 existing + 3 new cases classify correctly. If a new intent misclassifies, tighten the prompt examples and re-run (do not add LLM stages).
- **Build / on-device:** `assembleDebug`; install; confirm seeder populates (Home shows revenue, Inventory shows 5 items with parle-g low, Khata shows Ramesh/Priya/Suresh balances); then chat-test the 3 new intents + confirm `query_pnl`/`log_sale`/`record_payment` still work.

## 8. Build order (subagent-driven, spec + code-quality gates per task)
1. **Seeder** (`DemoDataSeeder` + `MainActivity` trigger) → install → confirm data on device. (Unblocks all manual testing.)
2. **Period helper** refactor (`PnlPeriod.startFrom`; `GetPnlSummaryUseCase` uses it) — TDD-safe, tests green.
3. **Intent enum + router + validate script** (no dispatch yet) — unit-test `parseIntent`; run validate script live.
4. **Customer-name extractor** (`LlmEngine.extractCustomerName`).
5. **Dispatch + result variants** (`RouteAssistantUseCase` branches, `AssistantResult` variants) — mockk tests.
6. **Formatter + rendering** (`AnalyticsChatFormatter`, `AssistantViewModel.toMessage`) — formatter unit tests.
7. **On-device chat test** (manual, owner-assisted): the 3 new intents + non-regression of existing intents.

## 9. Constraints / non-goals
- **Don't regress** the 10/10 intent router, the §18 sale path, or `query_pnl`/`log_sale`/`record_payment`. All share `IntentRouter`/`RouteAssistantUseCase` — re-verify.
- **Margins stays out of chat** (use-case remains; no intent).
- **Seeder is debug-only** (`BuildConfig.DEBUG`) — never seeds release builds.
- LLM stays parse/classify-only — analytics are ordinary Room queries; the only new LLM call is the customer-name extractor.
- No new Compose card components (text bubbles only). No structured analytics cards, no margins intent, no customer *list* screen — all out of scope.
- Toolchain pinned (AGP 8.13 / JDK 17). Room stays v3 (no schema change — seeder only writes rows).

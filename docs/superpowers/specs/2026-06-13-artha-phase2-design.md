# Artha Kirana — Phase 2 Design: Inventory · Khata · P&L

**Date:** 2026-06-13 · **Branch:** `feat/phase0-foundation` · **Status:** Approved, pre-implementation

This spec is the delta over `CLAUDE.md` for Phase 2 (CLAUDE.md §15). Where it disagrees with
`CLAUDE.md`, this spec wins (it reflects the actual working codebase). Phase 0 + Phase 1 are
done and device-verified; the data layer (entities, DAOs, repositories) already exists —
**extend it, do not duplicate.**

---

## Goal

Complete the shop-management loop: sale in → stock down → khata updated → profit shown.
Three real screens (Inventory, Khata, P&L) replace their placeholders, plus a background
low-stock alert worker and a 7-day revenue chart.

**Checkpoint (CLAUDE.md §15):** log 10 varied sales → inventory counts down correctly →
khata balances correct → P&L arithmetic verified → low-stock notification fires and
auto-clears on restock.

---

## Architectural Decisions

### 1. Entity-direct UI; domain models only for computed values
Existing code renders Room entities straight in Compose (`HomeScreen` uses `SaleEntity`).
We follow that working pattern — no `Item`/`KhataEntry`/`Purchase` mapping layer (CLAUDE.md
§3 lists them, but the working code skips them; YAGNI for the hackathon). We add domain
models **only** for values that have no backing entity:
- `PnlSummary(grossRevenue, cogs, grossProfit, cashCollected, totalOutstanding, period)`
- `PnlPeriod { TODAY, THIS_WEEK, THIS_MONTH }`
- `DailyRevenue(dayLabel: String, amount: Double)`

### 2. P&L = SQL scalar aggregates + Kotlin day-bucketing (hybrid)
The four headline numbers (gross revenue, COGS, cash collected, total outstanding) are
single-value SQL `SUM` queries exposed as `Flow<Double>` and `combine`d in the use-case:
`grossProfit = grossRevenue − cogs`. The 7-day chart buckets sales by **local** day, which
is awkward in SQLite (`timestamp/86400000` is UTC-day and timezone-buggy), so we bucket in
**Kotlin** from the sales list — pure, deterministic, TDD-able.

### 3. WorkManager via Hilt (`@HiltWorker`)
Coding standard §16 is "Hilt for everything." `InventoryAlertWorker` is a `@HiltWorker` with
injected `ItemsDao` + `NotificationHelper`. `ArthaApplication` implements
`Configuration.Provider` exposing `HiltWorkerFactory`. New deps: `androidx.hilt:hilt-work` +
`androidx.hilt:hilt-compiler` (KSP).

### UX decisions (user-approved)
- **Low-stock alert auto-clears on restock.** Stable per-item notification id = `item.id`.
  Each worker run fires/refreshes notifications for low items and **cancels** notifications
  for items now back above threshold.
- **Khata Party Detail has a "Record payment" button.** Reuses existing
  `KhataRepository.applyRepayment(party, amount, saleId = null)` (khata-only event; does not
  create a sale row — consistent with P&L, which counts cash from sales only and excludes
  repayments from revenue).
- **Inventory supports restock + edit.** Tapping a card opens a `ModalBottomSheet` reused for
  add / edit / restock.

---

## Components

### Inventory
- **`ItemsDao`** — no new query needed for the list (`observeAll()` exists). Restock + edit
  both go through the existing `update(item)`. `lowStock()` already exists (used by worker).
- **`InventoryRepository` / `InventoryRepositoryImpl`** — add `updateItem(item: ItemEntity)`
  (covers edit and restock). `observeAll()` / `addItem()` / `findByName()` / `decrementStock()`
  exist.
- **`ui/inventory/`**
  - `InventoryScreen` — `LazyColumn` of `ItemCard`; rows where `reorderThreshold > 0 &&
    qtyInStock < reorderThreshold` are highlighted (warning color). Empty-state text when no
    items. A FAB on the Inventory tab opens `AddItemSheet` in add-mode.
  - `ItemCard` — name (+ Hindi name), stock + unit, sell price; low-stock badge. Tap →
    edit/restock sheet.
  - `AddItemSheet` — `ModalBottomSheet`, fields: name, nameHi?, unit, costPrice, sellPrice,
    reorderThreshold, plus a "restock (add qty)" field in edit mode. Prefilled when editing.
  - `InventoryViewModel` — `observeAll()` → `StateFlow<List<ItemEntity>>`; `addItem`,
    `saveItem` (update), `restock(id, qty)` as `suspend` writes in `viewModelScope`.

### Low-stock alert (auto-clear)
- **`data/notification/NotificationHelper.kt`** — creates channel `INVENTORY_ALERTS`;
  `notifyLowStock(item)` with notification id = `item.id.toInt()`; `cancel(itemId)`.
- **`data/worker/InventoryAlertWorker.kt`** (`@HiltWorker`) — injects `ItemsDao` +
  `NotificationHelper`. Each run: `val low = dao.lowStock()`; fire/refresh a notification for
  each low item; **cancel** notifications for items no longer low (track via the worker's view
  of all items vs the low set). Returns `Result.success()`. No-op when `low` is empty.
- **Scheduling** — `PeriodicWorkRequest` every 30 min, enqueued with
  `ExistingPeriodicWorkPolicy.KEEP` from `ArthaApplication.onCreate()`.
- Manifest already declares `POST_NOTIFICATIONS`. OriginOS battery caveat documented in
  CLAUDE.md §11 / SPIKE C — out of scope to re-solve here.

### Khata
- **`KhataDao`** — add `observeById(id: Long): Flow<KhataEntity?>`. `observeAll()` /
  `totalOutstanding()` / `findByName()` exist; `KhataTransactionDao.observeForParty(partyId)`
  exists.
- **`KhataRepository` / impl** — add `observeParty(id): Flow<KhataEntity?>` and
  `observeTransactions(id): Flow<List<KhataTransactionEntity>>`. "Record payment" reuses
  existing `applyRepayment(party, amount, saleId = null)`.
- **`ui/khata/`**
  - `KhataScreen` — party list; balance colored (red = party owes us, i.e. `balance > 0`);
    header shows total outstanding. Tap a party → detail. Empty-state text.
  - `KhataPartyDetail` + `KhataPartyDetailViewModel` — `SavedStateHandle` carries `partyId`;
    shows balance header + transaction history (credit/repayment rows, colored); a
    **"Record payment"** button opens an amount dialog → `applyRepayment`.
  - `KhataViewModel` — `observeAll()` + `totalOutstanding()` → state.
  - **Nav**: new route `khata/{partyId}` (sub-route; `currentRoute` not in `topRoutes` already
    hides the bottom bar, matching the existing `sale_entry` pattern).

### P&L
- **`SalesDao`** — add
  `cogsBetween(start, end): Flow<Double>` =
  `SELECT COALESCE(SUM(s.qtySold * i.costPrice), 0) FROM sales s JOIN items i ON s.itemId = i.id
   WHERE s.type != 'repayment' AND s.timestamp BETWEEN :start AND :end`.
  `revenueBetween` / `cashBetween` exist. Chart reads `observeSince(sevenDaysAgo)`.
- **`util/TimeRange.kt`** — add `startOfWeek()`, `startOfMonth()`, and `last7DayBuckets(now)`
  returning the 7 local-day boundaries + labels. All TDD'd alongside the existing
  `startOfToday()`.
- **`domain/model/PnlSummary.kt`** — `PnlSummary`, `PnlPeriod`, `DailyRevenue` (see §1).
- **`domain/usecase/GetPnlSummaryUseCase.kt`** — `operator fun invoke(period): Flow<PnlSummary>`;
  resolves start/end from `TimeRange`, `combine`s revenue/cogs/cash/outstanding flows into a
  `PnlSummary`.
- **`ui/pnl/`**
  - `PnlScreen` — today/week/month tab row; metric cards (gross revenue, COGS, gross profit,
    cash collected, outstanding).
  - `ProfitChart` — Vico column chart, last-7-days revenue.
  - `PnlViewModel` — selected `PnlPeriod` drives `GetPnlSummaryUseCase`; daily-revenue state
    for the chart (Kotlin bucketing of `observeSince`).

### Cross-cutting
- **`gradle/libs.versions.toml`** — Vico `3.1.0 → 2.1.x` (Kotlin-2.0 compatible; **re-verify the
  exact `CartesianChartModelProducer` constructor via Context7 at the chart task** — API shifted
  2.0→2.1). Add `vico-compose` (core) alongside existing `vico-compose-m3`. Add
  `androidx-hilt-work` + `androidx-hilt-compiler`.
- **`ArthaApp.kt`** — swap placeholders for real screens; add `khata/{partyId}` route; add the
  Inventory-tab add-item FAB.
- **`ArthaApplication.kt`** — `Configuration.Provider` + inject `HiltWorkerFactory`; enqueue the
  periodic worker.

---

## Data Flow

Room `Flow` → repository → `stateIn(viewModelScope, WhileSubscribed(5_000), …)` in the
ViewModel → `collectAsStateWithLifecycle()` in Compose. All mutations (add/edit/restock,
record-payment) are `suspend` writes launched in `viewModelScope`; the UI updates reactively
because every read is a Room `Flow` — no manual refresh. Matches the Phase 1 `HomeViewModel`
pattern exactly.

---

## Error Handling

- Every screen has explicit empty-state text — no blank screens (§16).
- Writes wrapped; failures surface as a transient UI message (snackbar/inline), never a crash.
- `COALESCE(..., 0)` on all aggregates; sales with null `itemId`/`costPrice` simply contribute
  0 to COGS, so P&L degrades gracefully on partial data.
- Worker no-ops when `lowStock()` is empty; notification posting guarded for `POST_NOTIFICATIONS`.

---

## Testing Strategy

**TDD the pure logic** (cheap, deterministic), mirroring the existing `LogSaleUseCaseTest`
fake-repository pattern:
- `TimeRange` — `startOfWeek` / `startOfMonth` boundaries, `last7DayBuckets`.
- 7-day revenue bucketing (sales list → `List<DailyRevenue>`).
- `PnlSummary` math (`grossProfit = grossRevenue − cogs`).
- `GetPnlSummaryUseCase` `combine` behavior with fake repos.

**Build-verify + on-device checkpoint** for Compose / Room / Worker / Hilt wiring (can't be
meaningfully unit-tested in the hackathon budget): install on the iQOO, screenshot, run the
§15 checkpoint flow. Commit per task.

---

## Out of Scope (Phase 2)

Bill scanning (Phase 3), voice/whisper (Phase 4), Claude market insights (Phase 5), demo
hardening + seeding (Phase 6), and everything in CLAUDE.md §17. SQLCipher stays deferred.
Purchase-history UI is not required for the Phase 2 loop and is omitted.

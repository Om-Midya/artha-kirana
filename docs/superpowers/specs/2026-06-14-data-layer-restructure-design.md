# Data-Layer Restructure — Design Spec

**Date:** 2026-06-14 · **Status:** ✅ Approved (brainstorm complete) — ready for plan.
**Supersedes:** the direction brief `2026-06-14-data-layer-direction.md` (open questions now resolved).
**Branch:** Feature A lands on `feat/assistant-layer`; schema work (B/C/E) branches off `main` after that branch merges.
**Related:** `CLAUDE.md` (§4 schema, §10 P&L, §16 standards, §17 out-of-scope), `HANDOFF.md`, `docs/STATUS.md`.

---

## 1. Goal

Make the data model do three things the shopkeeper's words imply but the schema can't support today:

1. **Auto-price** — give the LLM only an item + quantity and the app computes `amount = sellPrice × qty` from inventory, so a sale like "दो किलो चावल" (no price) still produces a total.
2. **First-class customers** — `sales.party` is free text today, so "all sales for customer X" cannot be joined. Introduce a `customers` identity table that both `sales` and `khata` reference by id.
3. **Analytics-easy schema** — snapshot prices on each sale line (drift-free margins), add the indices that range/group-by reports need, and ship the four reports the user cares about.

The on-device LLM stays **parse-only**. Every "search the database" operation is an ordinary Room DAO query — the LLM never generates SQL (offline determinism + safety).

## 2. Resolved decisions (from brainstorming)

| Question | Decision |
|---|---|
| "User profiles" means? | **Customer** profiles only. No shop/settings table (CLAUDE.md §17 rules out auth/multi-shop). |
| Customer linking shape | **New `customers` table + FK.** `sales.customerId`, `khata.customerId` → `customers`. Keep `party`/`partyName` denormalized for display + fallback. NOT promoting khata into the customer table. |
| Price snapshot | Snapshot **both `unitPrice` and `unitCost`** on each sale line at log time. |
| Auto-price aggressiveness | Fill **only when `amount == null`** AND item resolves in inventory AND `sellPrice > 0`. A spoken/typed explicit price always wins. |
| Analytics targets | **All four:** top sellers, per-customer history+outstanding, margin-by-item, day-of-week trends. |
| Migration | `fallbackToDestructiveMigration` (DB v2→v3, wipes dev data — re-enter by hand; no `DemoDataSeeder` yet). |
| Auto-price location | **Fold enrichment into `ParseSaleEntryUseCase`** (inject `InventoryRepository`). Both Sale Entry and Assistant call it → one chokepoint, zero call-site wiring. |
| `khata.partyName` | Keep denormalized (matches `sales.itemName` pattern; minimizes KhataScreen churn). |

## 3. Current state (grounding)

- `ParseSaleEntryUseCase` (`domain/usecase/`) — pure: `engine.parseSale(HindiNumbers.normalize(text))` → `Result<List<SaleEntry>>`. Called by **`SaleEntryViewModel`** and **`RouteAssistantUseCase`** (Assistant `LOG_SALE`).
- `LogSaleUseCase` — writes `SaleEntity(amount = entry.amount ?: 0.0, party = entry.party, …)`, decrements stock, applies khata for credit/repayment. `EditSaleUseCase` mirrors it (reverse + re-apply).
- `KhataRepositoryImpl.adjust(party, …)` — resolves the party via `khataDao.findByName(name COLLATE NOCASE)` else inserts a `KhataEntity`. `reverseSaleEffect(saleId)` walks `khata_transactions` by saleId.
- `SaleEntity` — `id, itemId: Long?, itemName: String?, qtySold, amount, type, party: String?, inputMethod, rawInput, timestamp`. No declared FK on `itemId`, no indices.
- `KhataEntity` — `id, partyName, balance, lastUpdated`. `KhataTransactionEntity` already declares FKs (partyId→khata, saleId→sales SET_NULL) + indices.
- `SalesDao` — `revenueBetween`, `cashBetween`, `cogsBetween` (JOIN items on costPrice), `observeSince`, `between`.
- `ArthaDatabase` — `version = 2`, `exportSchema = false`, 5 entities.
- `parseLeadingQty(qty: String?): Double` — pulls first number, null→0.0.

## 4. Feature A — Auto-price (no schema change, ships first)

**Where:** inside `ParseSaleEntryUseCase`. Inject `InventoryRepository`. After the LLM returns `List<SaleEntry>`, map each entry through an enrich step.

**Pure calc (TDD this):**
```kotlin
// domain/usecase/AutoPrice.kt
/**
 * Computes a sale line total from inventory when the LLM gave a quantity but no amount.
 * Returns null (leave amount unfilled) unless: amount is null AND item resolved AND
 * sellPrice > 0 AND qty > 0. A spoken/typed price always wins (we only fill nulls).
 */
internal fun computeAutoPrice(entry: SaleEntry, item: ItemEntity?): Double? {
    if (entry.amount != null) return entry.amount       // explicit price wins
    if (item == null || item.sellPrice <= 0.0) return null
    val qty = parseLeadingQty(entry.qty)
    if (qty <= 0.0) return null
    return item.sellPrice * qty
}
```

**Use-case wiring:**
```kotlin
class ParseSaleEntryUseCase @Inject constructor(
    private val engine: LlmEngine,
    private val inventory: InventoryRepository,
) {
    suspend operator fun invoke(text: String): Result<List<SaleEntry>> =
        engine.parseSale(HindiNumbers.normalize(text)).map { entries ->
            entries.map { e ->
                val item = e.item?.let { inventory.findByName(it) }
                e.copy(amount = computeAutoPrice(e, item))
            }
        }
}
```

**Behavior table:**

| amount (LLM) | item in inventory? | sellPrice | qty | Result |
|---|---|---|---|---|
| 80 | any | any | any | 80 (explicit wins) |
| null | yes | 40 | 2 | 80 (computed) |
| null | yes | 0 / unset | 2 | null (left unfilled — user edits) |
| null | no | — | 2 | null (as today) |
| null | yes | 40 | 0/null | null (no qty to multiply) |

The computed amount lands on the **editable confirm card** (both Sale Entry and Assistant `SaleDraft`), so the shopkeeper sees and can override it before saving.

**Verification:** unit tests for `computeAutoPrice`; on-device check that "दो किलो चावल" (item `chawal` with sellPrice set) shows a computed total on the confirm card via **both** the Sale Entry mic/typed path and the Assistant chat. Must not regress §18 (those cases all carry explicit amounts → unchanged).

## 5. Features B + C + snapshot — one schema migration (DB v3)

All schema changes ship as a single bump to keep one destructive migration.

### 5.1 New `customers` table (identity hub)
```kotlin
@Entity(
    tableName = "customers",
    indices = [Index(value = ["name"], unique = true)],
)
data class CustomerEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val name: String,                  // matched COLLATE NOCASE on lookup
    val nameHi: String? = null,
    val phone: String? = null,
    val createdAt: Long = System.currentTimeMillis(),
)
```
> Note: the unique index is on raw `name`; case-insensitive dedup is enforced in `resolveOrCreate` (lookup uses `COLLATE NOCASE`), mirroring how khata already does name lookups. Acceptable for the hackathon; a `COLLATE NOCASE` unique index is a later hardening.

### 5.2 `SaleEntity` additions
```kotlin
@Entity(
    tableName = "sales",
    foreignKeys = [
        ForeignKey(entity = ItemEntity::class, parentColumns = ["id"], childColumns = ["itemId"], onDelete = ForeignKey.SET_NULL),
        ForeignKey(entity = CustomerEntity::class, parentColumns = ["id"], childColumns = ["customerId"], onDelete = ForeignKey.SET_NULL),
    ],
    indices = [Index("timestamp"), Index("itemId"), Index("customerId")],
)
data class SaleEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val itemId: Long? = null,
    val itemName: String? = null,
    val customerId: Long? = null,      // NEW — FK→customers, null for anonymous cash sales
    val qtySold: Double = 0.0,
    val amount: Double,
    val unitPrice: Double? = null,     // NEW — snapshot of sellPrice at sale time
    val unitCost: Double? = null,      // NEW — snapshot of costPrice at sale time
    val type: String,
    val party: String? = null,         // kept denormalized (display + fallback)
    val inputMethod: String,
    val rawInput: String? = null,
    val timestamp: Long = System.currentTimeMillis(),
)
```

### 5.3 `KhataEntity` addition
```kotlin
@Entity(
    tableName = "khata",
    foreignKeys = [ForeignKey(entity = CustomerEntity::class, parentColumns = ["id"], childColumns = ["customerId"], onDelete = ForeignKey.CASCADE)],
    indices = [Index(value = ["customerId"], unique = true)],   // one ledger row per customer
)
data class KhataEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val customerId: Long,              // NEW — FK→customers
    val partyName: String,             // kept denormalized for display
    val balance: Double = 0.0,
    val lastUpdated: Long = System.currentTimeMillis(),
)
```

### 5.4 `ArthaDatabase`
Add `CustomerEntity::class` to `entities`, `version = 3`, add `customersDao()`. Keep `fallbackToDestructiveMigration` in `DatabaseModule`.

### 5.5 Customer resolution (the linking glue)
A single idempotent resolver so name→id is computed in exactly one place (no duplicate customer rows):
```kotlin
// domain/repository/CustomerRepository.kt  + data/repository/CustomerRepositoryImpl.kt
interface CustomerRepository {
    suspend fun resolveOrCreate(name: String): Long   // findByName NOCASE else insert
    suspend fun findByName(name: String): CustomerEntity?
    fun observeAll(): Flow<List<CustomerEntity>>
}
```
`CustomersDao.findByName` uses `WHERE name = :name COLLATE NOCASE LIMIT 1` (same as `KhataDao`).

**`LogSaleUseCase` changes:**
```kotlin
val item = entry.item?.let { inventory.findByName(it) }
val qty = parseLeadingQty(entry.qty)
val customerId = entry.party?.takeIf { it.isNotBlank() }?.let { customers.resolveOrCreate(it) }

val saleId = sales.logSale(SaleEntity(
    itemId = item?.id, itemName = entry.item, customerId = customerId,
    qtySold = qty, amount = entry.amount ?: 0.0,
    unitPrice = item?.sellPrice, unitCost = item?.costPrice,   // snapshots
    type = entry.type, party = entry.party,
    inputMethod = inputMethod, rawInput = rawInput,
))
// stock + khata as before, but khata now keyed by customerId
```
> Resolving a customer for **any** named party (not just credit) means cash sales with a name also attribute to per-customer analytics. Anonymous cash sales (party null) stay `customerId = null`.

**`KhataRepositoryImpl.adjust`** changes from name-keyed to customer-keyed: it calls `customers.resolveOrCreate(name)` to get `customerId`, then upserts the khata row by `customerId` (unique index), storing `partyName` denormalized. `applyCredit`/`applyRepayment`/`reverseSaleEffect` keep their signatures; `reverseSaleEffect` is unchanged (walks `khata_transactions` by saleId, which still point at `khata.id`).

**`EditSaleUseCase`** resolves `customerId` the same way and re-snapshots `unitPrice`/`unitCost` from the (possibly changed) item on re-apply.

## 6. Feature E — Analytics queries (DAO `@Query` + use-cases)

All four as Room queries feeding small use-cases (return `Flow` or suspend lists per existing convention). LLM not involved.

1. **Top sellers** (`SalesDao`):
   ```sql
   SELECT itemId, itemName, SUM(qtySold) AS qty, SUM(amount) AS revenue
   FROM sales WHERE type != 'repayment' AND timestamp BETWEEN :start AND :end
   GROUP BY itemId ORDER BY revenue DESC
   ```
   → projection data class `TopSellerRow(itemId, itemName, qty, revenue)`.

2. **Per-customer history & outstanding**:
   - history: `SELECT * FROM sales WHERE customerId = :id ORDER BY timestamp DESC`
   - outstanding: `SELECT balance FROM khata WHERE customerId = :id`
   - lifetime value: `SELECT COALESCE(SUM(amount),0) FROM sales WHERE customerId = :id AND type != 'repayment'`

3. **Margin by item** (uses snapshots — drift-free):
   ```sql
   SELECT itemId, itemName,
          COALESCE(SUM((unitPrice - unitCost) * qtySold), 0) AS margin,
          COALESCE(SUM(amount), 0) AS revenue
   FROM sales
   WHERE type != 'repayment' AND unitPrice IS NOT NULL AND unitCost IS NOT NULL
     AND timestamp BETWEEN :start AND :end
   GROUP BY itemId ORDER BY margin ASC      -- ascending surfaces low-margin/high-volume
   ```

4. **Day-of-week trends** — bucket revenue by weekday. Prefer doing the bucketing in Kotlin (reuse the `RevenueBucketing` approach already unit-tested for the P&L chart) over SQLite `strftime` to keep timezone handling consistent with the rest of the app: query `between(start, end)` then group by `Calendar` weekday.

Each query is unit-testable with an in-memory Room DB (Robolectric) or a pure bucketing test where logic is in Kotlin.

## 7. Build order & process

1. **A — Auto-price** on `feat/assistant-layer` (no schema change). TDD `computeAutoPrice`; wire into `ParseSaleEntryUseCase`; on-device verify Sale Entry + Assistant; commit.
2. **Merge `feat/assistant-layer` → main** (after its pending human on-device walkthrough — Assistant 3 flows + voice + offline, and the edit-entry 4 exit criteria). Auto-price rides along.
3. **B + C + snapshot — DB v3** off `main`: `CustomerEntity` + dao + repo, `SaleEntity`/`KhataEntity` changes, `ArthaDatabase` v3, `resolveOrCreate`, `LogSaleUseCase`/`EditSaleUseCase`/`KhataRepositoryImpl` rewiring. Build-verify; on-device re-test the full sale + credit + repayment + edit flows (data wiped — re-enter). Commit.
4. **E — Analytics** queries + use-cases. TDD; commit. (UI surfacing of these reports is out of scope here — they're DAO+use-case building blocks; wiring into P&L/Insights screens is a follow-up.)

Each task: subagent-driven with spec + code-quality review gates (the pattern used for the Assistant + edit features this session).

## 8. Constraints / non-goals

- **Don't regress** §18 sale path, the Assistant `log_sale`/`record_payment`/`query_pnl` flows, or the edit-recent-entries flow — all share `ParseSaleEntryUseCase` and the khata path. Re-verify after A and after C.
- **Don't touch** Phase 3 OCR files (`BillParser`, `BillScanScreen`, CameraX, ML Kit) — collaborator-owned. `PurchaseEntity` left as-is (could gain a `supplierId` later — out of scope).
- **LLM parse-only** — no LLM-generated SQL.
- Toolchain pinned (AGP 8.13 / Gradle 8.13 / JDK 17 / Vico 2.1.3) — don't upgrade.
- No `DemoDataSeeder` yet — destructive migration means manual re-entry of test data.
- Surfacing analytics in the UI (charts/screens beyond what P&L already has) and a shop/settings profile are explicit non-goals for this spec.

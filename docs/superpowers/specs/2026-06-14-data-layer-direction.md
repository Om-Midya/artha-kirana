# Data-Layer Restructure — Direction Brief (for the next agent)

**Date:** 2026-06-14 · **Status:** 🔜 DIRECTION ONLY — not designed/approved yet. **Next agent: brainstorm this (resolve the Open Questions) → spec → plan → build.** Do NOT jump to code.
**Branch:** continue on `feat/assistant-layer` (or merge it to `main` first, then branch). The Assistant + edit features live there.
**Related:** `CLAUDE.md` (§4 schema, §10 P&L, §17 out-of-scope), `HANDOFF.md`, `docs/STATUS.md`.

---

## 1. What the user asked for (verbatim)

> "we need to structure our data very well, like lets say we have our inventory and selling price is listed there so on telling the llm the qty, it should automatically calculate the price, may use sql query to search stuff etc or whatever database we have and similarly everything should be linked according to our schema, like user profiles, so that data analytics is easy to do from the db"

Decoded into goals:
1. **Auto-price:** speak/type only a quantity (+ item) → the app computes `amount = sellPrice × qty` from inventory instead of requiring the price.
2. **Tighter linking:** entities related via real keys so cross-entity queries (joins) are clean.
3. **"User profiles":** some notion of profiles linked into the schema (see Open Question 1 — likely *customer* profiles).
4. **Analytics-easy DB:** schema + indices that make reporting/queries straightforward.

## 2. Current schema (Room v2, `fallbackToDestructiveMigration`)

| Entity | Key fields | Links |
|---|---|---|
| `ItemEntity` (items) | id, name, nameHi, qtyInStock, unit, **costPrice, sellPrice**, reorderThreshold, category, createdAt | referenced by sales.itemId, purchases.itemId |
| `SaleEntity` (sales) | id, **itemId: Long?**, itemName (denorm), qtySold, **amount** (total, not unit price), type (cash/credit/repayment), **party: String?** (FREE TEXT), inputMethod, rawInput, timestamp | itemId→items (no declared FK); party is a *string*, not linked to khata |
| `PurchaseEntity` (purchases) | id, itemId, qty, cost, supplier, billScanUri, timestamp | itemId→items |
| `KhataEntity` (khata) | id, **partyName**, balance, lastUpdated | the customer ledger; keyed by name lookup |
| `KhataTransactionEntity` (khata_transactions) | id, **partyId** (FK→khata), amount, type, **saleId** (FK→sales, onDelete SET_NULL), note, timestamp | links khata↔sales |

**Gaps that hurt analytics today:**
- `sales.party` is **free text**, not a key → can't reliably join "all sales for customer X" (only khata *transactions* link to a party, and only for credit/repayment).
- `sales` stores only `amount` (line total), no **unit price** snapshot → if `sellPrice` changes later, historical margins drift (COGS already snapshots via `qtySold × items.costPrice` JOIN, which has the same drift risk).
- No declared **indices** on `timestamp`, `itemId`, or `party` → range/group-by analytics scan.
- No **shop/settings** record (vernacular pref etc. live in DataStore, if anywhere).
- `itemId` is nullable and `itemName` denormalized — intentional (CLAUDE.md DB v2) but means joins must tolerate nulls.

## 3. Proposed decomposition (each is its own spec → plan → build)

**A. Auto-price from inventory (DO FIRST — small, high-value, NO schema change).**
When a parsed sale has an item that resolves in inventory, a quantity, and `amount == null`, compute `amount = items.sellPrice × parseLeadingQty(qty)` *before showing the confirm card* (so the shopkeeper sees/edits the computed total). Natural home: an enrich step after `ParseSaleEntryUseCase` (or inside it), reusing the existing `parseLeadingQty` and `InventoryRepository.findByName`. Applies to BOTH the Sale Entry screen and the Assistant `log_sale` path (both go through `ParseSaleEntryUseCase`). TDD the calc. Edge cases in Open Q2.

**B. Indices + linking for analytics (schema bump → v3).** Add Room `@Index` on `sales.timestamp`, `sales.itemId`, `sales.party` (and/or new `customerId`), declare the `sales.itemId`→`items` FK. Keep `fallbackToDestructiveMigration` for the hackathon (wipes dev data; re-seed).

**C. Customer as first-class (bigger).** Give `sales` a `customerId: Long?` referencing the khata party (or a dedicated `customers` table that khata also points to), so cash sales can also attribute to a customer and per-customer analytics joins cleanly. Decide vs Open Q1/Q3.

**D. Shop/settings profile.** A single-row settings table (or DataStore) for shop name, owner, default language/vernacular. Only if "profiles" means the shop (Open Q1).

**E. Analytics queries/use-cases.** Once B/C land: top items by volume/margin, per-customer outstanding & history, daily/weekly trends — as DAO `@Query`s + use-cases feeding P&L/Insights. Depends on the linking from B/C.

**Recommended order:** A → B → (C and/or D per Open Q1) → E.

## 4. Open questions (resolve in brainstorming BEFORE designing)

1. **"User profiles" = which?** (a) *Customer* profiles — make khata parties first-class and link sales to them; (b) *Shop/owner* profile — single shop settings record; (c) both. NOTE: `CLAUDE.md §17` rules out user **authentication/login** and **multi-shop** — so this is data modelling, not auth. Confirm intent.
2. **Auto-price behavior:** only fill when `amount` is null, or always compute and let the user override? What when `sellPrice == 0`/unset (skip? prompt?)? What when the item isn't in inventory (leave null, as today)? Does a spoken explicit price always win over the computed one? (Recommend: compute only when amount null AND sellPrice>0; spoken price always wins.)
3. **Customer linking shape:** add `sales.customerId` FK and migrate `party` strings → customer rows, or keep `party` denormalized and add the id alongside? Affects the migration + existing khata-by-name logic (`KhataRepositoryImpl.adjust` looks up `findByName`).
4. **Historical price accuracy:** snapshot `unitPrice` (and maybe `unitCost`) on each sale line so analytics/margins are stable when catalog prices change later? (Recommend yes — cheap, removes drift.)
5. **Which analytics specifically?** Top sellers, margins, per-customer, day-of-week trends, dead stock? This drives which indices/joins matter. (Phase 5 "market insights" already wants item+qty aggregates.)
6. **Migration:** OK to bump to DB v3 with `fallbackToDestructiveMigration` (wipes dev data, re-seed via `DemoDataSeeder` in Phase 6)? Or write real migrations? (Hackathon → destructive is fine.)

## 5. Constraints / gotchas (carry forward)

- Toolchain pinned: AGP 8.13 / Gradle 8.13 / JDK 17 / Vico 2.1.3. Don't upgrade.
- Room v2 + `fallbackToDestructiveMigration` (schema change wipes dev data — fine for hackathon, re-seed).
- Don't touch Phase 3 OCR files (`BillParser`/`BillScanScreen`/CameraX/ML Kit) — collaborator-owned.
- Don't regress the §18-stable sale path or the new Assistant/edit flows. Auto-price (A) routes through `ParseSaleEntryUseCase`, which both Sale Entry and the Assistant reuse — so verify both.
- The on-device LLM (llama-server) is for parsing only; the "SQL query to search stuff" the user mentions = ordinary Room DAO queries (the app does NOT have the LLM write SQL — keep it that way for safety/offline-determinism).

## 6. Pointers

- Schema: `CLAUDE.md §4`; entities in `app/src/main/java/com/artha/kirana/data/db/entity/`; DAOs in `…/dao/`; `ArthaDatabase.kt`.
- Auto-price touch points: `domain/usecase/ParseSaleEntryUseCase.kt`, `LogSaleUseCase.kt`, `domain/usecase/QtyParsing.kt` (`parseLeadingQty`), `InventoryRepository.findByName`.
- P&L/COGS reference for analytics: `GetPnlSummaryUseCase.kt`, `SalesDao` (`revenueBetween`/`cogsBetween`/`cashBetween`).
- Process: superpowers brainstorming → writing-plans → subagent-driven-development (the pattern used for the Assistant + edit features this session; see those specs/plans as templates).

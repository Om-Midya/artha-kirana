# Artha Kirana — Edit Saved Sales from Home "Recent Entries" (Design Spec)

**Date:** 2026-06-14 · **Status:** Approved (brainstorm) → ready for implementation plan
**Branch baseline:** `feat/assistant-layer` (reuses the shared `EditableEntryCard`; lands with the Assistant slice)
**Related:** `CLAUDE.md`, `docs/STATUS.md`, memory `artha-edit-recent-entries`

---

## 1. Goal

The Home screen's **"Recent entries"** list (today's sales) is read-only. Let the shopkeeper
**tap a recent sale and edit it** — correcting a wrong item, quantity, amount, type, or party —
with the inventory and khata side-effects of the original entry correctly reconciled.

## 2. Approved decisions (from brainstorming)

| Decision | Choice |
|---|---|
| **Surface** | Home "Recent entries" rows → tap → editor bottom sheet. |
| **Edit scope** | **Full edit**: item, qty, amount, type (cash/credit/repayment), party. |
| **Reconcile** | Reverse the original entry's inventory + khata effects, then apply the edited entry's effects. |
| **Khata history** | **Clean rewrite** — delete the original khata transaction(s) for that sale and undo their balance impact, then record the new one. No correction/audit entries. |
| **Identity** | **Edit in place** — keep the sale's `id`, `timestamp`, `inputMethod`, `rawInput`. |
| **Reconcile location** | A new standalone `EditSaleUseCase`; the §18-stable `LogSaleUseCase` is NOT modified. |

## 3. Architecture

Additive. No existing behavior changes except new methods added to DAOs/repositories and the
Home row becoming tappable. The §18-stable add-sale path (`LogSaleUseCase`) is untouched.

### 3.1 The reconcile use-case — `EditSaleUseCase(old: SaleEntity, edited: SaleEntry)`

```
1. Reverse old stock:   if (old.itemId != null && old.qtySold > 0)
                            inventory.incrementStock(old.itemId, old.qtySold)
2. Reverse old khata:   khata.reverseSaleEffect(old.id)
3. Resolve new item:    val newItem = edited.item?.let { inventory.findByName(it) }
                        val newQty  = parseLeadingNumber(edited.qty)
4. Update row in place: sales.updateSale(old.copy(
                            itemId = newItem?.id, itemName = edited.item, qtySold = newQty,
                            amount = edited.amount ?: 0.0, type = edited.type, party = edited.party))
                        // id, timestamp, inputMethod, rawInput preserved by copy()
5. Apply new stock:     if (newItem != null && newQty > 0)
                            inventory.decrementStock(newItem.id, newQty)
6. Apply new khata:     when (edited.type) {
                            "credit"    -> edited.party?.let { khata.applyCredit(it, edited.amount ?: 0.0, old.id) }
                            "repayment" -> edited.party?.let { khata.applyRepayment(it, edited.amount ?: 0.0, old.id) }
                        }
```

This single path correctly handles every transition: cash↔credit↔repayment, item change
(including known→unknown name), qty change, amount change, and party change (old party's
balance is reversed in step 2; new party's balance is applied in step 6).

`parseLeadingNumber(qty: String?): Double` pulls the leading number out of a qty string
("2 kg" → 2.0; null/none → 0.0) — identical semantics to the private helper in
`LogSaleUseCase`. To avoid duplication, extract it to a shared internal helper
(`domain/usecase/QtyParsing.kt`, an internal top-level `fun parseLeadingQty(qty: String?): Double`)
and have BOTH `LogSaleUseCase` and `EditSaleUseCase` call it. (This is the one targeted
touch to `LogSaleUseCase` — replacing its private duplicate with the shared call, no behavior
change, covered by the existing `LogSaleUseCaseTest`.)

### 3.2 New plumbing (additions only)

- `ItemsDao.incrementStock(id, qty)` → `@Query("UPDATE items SET qtyInStock = qtyInStock + :qty WHERE id = :id")`; `InventoryRepository.incrementStock(id, qty)` + impl.
- `SalesDao.update(sale: SaleEntity)` → `@Update`; `SalesRepository.updateSale(sale)` + impl.
- `KhataTransactionDao.findBySaleId(saleId: Long): List<KhataTransactionEntity>` and `deleteBySaleId(saleId: Long)`.
- `KhataDao.findById(id: Long): KhataEntity?` (suspend) — add if not present.
- `KhataRepository.reverseSaleEffect(saleId: Long)` + impl in `KhataRepositoryImpl`:
  ```
  for (t in txnDao.findBySaleId(saleId)) {
      val delta = if (t.type == "credit") t.amount else -t.amount   // original balance impact
      khataDao.findById(t.partyId)?.let { p ->
          khataDao.update(p.copy(balance = p.balance - delta, lastUpdated = System.currentTimeMillis()))
      }
  }
  txnDao.deleteBySaleId(saleId)
  ```
  (A sale produces at most one khata txn, but the loop is robust to any count.)

### 3.3 SaleEntity ↔ SaleEntry mapping (for the editor)

The editor reuses `EditableEntryCard`, which edits a `SaleEntry(item, qty, amount, type, party)`.
Map the tapped `SaleEntity` → `SaleEntry`:
- `item = itemName`
- `qty = qtySold` formatted as a plain string with no trailing ".0" (e.g. 2.0 → "2", 2.5 → "2.5")
- `amount = amount` (Double; the card shows it as a whole number)
- `type = type`, `party = party`

On Save, `HomeViewModel.editSale(old: SaleEntity, edited: SaleEntry)` calls `EditSaleUseCase`.

## 4. UI

- In `HomeScreen`, each "Recent entries" row gets an `onClick` that opens a `ModalBottomSheet`
  holding the `EditableEntryCard` (pre-filled via the mapping above) plus **Cancel / Save** buttons.
  Sheet state (which sale is being edited, or none) is local Compose state in `HomeScreen`.
- On Save: call `viewModel.editSale(old, edited)`, dismiss the sheet. Room `Flow`s drive the
  reactive refresh of Home revenue/recent list, Inventory, Khata, and P&L automatically.
- On Cancel / dismiss: close the sheet, no write.
- The existing read-only row rendering is otherwise unchanged.

## 5. Error handling

- Edit is a sequence of suspend repo calls on `Dispatchers.IO` (Room). Wrap `editSale` in
  `viewModelScope.launch`; repos already run on IO via Room. No user-facing error state beyond
  the existing screens (a failed write would simply not update — acceptable for the hackathon;
  the operations are local DB writes that don't fail under normal use).
- If the edited item name doesn't match any inventory item, `itemId` becomes null and no stock
  change is applied for the new side (same graceful behavior as `LogSaleUseCase`).

## 6. Testing

- **Unit (TDD):** `EditSaleUseCase` with MockK repositories. Cases:
  1. credit→cash: reverses old credit (reverseSaleEffect), no new khata applied, sale updated.
  2. item swap (rice→sugar): increments old item stock, decrements new item stock, updates row.
  3. qty change same item: increments old qty back, decrements new qty.
  4. party/amount change on credit: reverseSaleEffect(old.id) then applyCredit(newParty, newAmount, old.id).
  5. unknown new item name: no decrement on the new side.
  Assert `updateSale` is called with a copy that preserves `id` and `timestamp` and carries the new fields.
- **Unit:** `parseLeadingQty` shared helper (move the existing `LogSaleUseCase` cases or add a small test); existing `LogSaleUseCaseTest` must stay green after the helper extraction.
- **UI / integration:** build-verify (`./gradlew :app:installDebug`) + on-device (human-driven): tap a recent sale, change a field, Save → Home/Inventory/Khata/P&L reflect the change correctly.

## 7. Exit criteria (on-device)

1. Tap a recent **cash** sale, change its amount → Home revenue + P&L update by the delta.
2. Tap a recent **credit** sale (e.g. Ramesh ₹80), change amount to ₹50 → Ramesh's khata balance
   adjusts to the corrected value and his transaction history shows the corrected entry only
   (no leftover ₹80 + reversal noise).
3. Tap a recent sale of a stocked item, change the qty → that item's stock reflects the corrected
   quantity (old qty restored, new qty deducted).
4. Change a sale's **type** (e.g. credit → cash) → the party's khata is reduced and the sale no
   longer contributes to khata.

## 8. Constraints / gotchas

- Toolchain pinned: AGP 8.13 / Gradle 8.13 / JDK 17. Do not upgrade.
- Room is v2 with `fallbackToDestructiveMigration`; these are new DAO methods only (no schema
  change), so no migration concern.
- Driving Compose via adb is flaky — human drives the on-device UI verification.
- Don't touch Phase 3 (OCR) files.

## 9. Out of scope

Editing non-today entries (only today's "Recent entries" are listed today); deleting entries;
editing from the Khata or Inventory screens; an undo of the edit itself; multi-item edit in one row.

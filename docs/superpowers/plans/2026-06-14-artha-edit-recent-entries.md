# Edit Saved Sales from Home "Recent Entries" — Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Let the shopkeeper tap a sale in the Home "Recent entries" list and fully edit it (item/qty/amount/type/party), correctly reversing the original entry's inventory + khata side-effects and applying the edited ones, in place.

**Architecture:** A new `EditSaleUseCase` does *reverse-old → apply-new* using the repositories. Khata history is rewritten cleanly via a new `KhataRepository.reverseSaleEffect(saleId)` (delete the sale's khata txns + undo their balance). Small additive DAO/repo methods (`incrementStock`, `updateSale`, khata reversal). The Home recent rows become tappable, opening a `ModalBottomSheet` that reuses the shared `EditableEntryCard`. The §18-stable `LogSaleUseCase` add path is unchanged except for sharing a tiny `parseLeadingQty` helper.

**Tech Stack:** Kotlin, Room, Hilt, Jetpack Compose + Material3, Coroutines/StateFlow, JUnit4 + MockK + kotlinx-coroutines-test. Toolchain pinned: AGP 8.13 / Gradle 8.13 / JDK 17 — do NOT upgrade.

**Spec:** `docs/superpowers/specs/2026-06-14-artha-edit-recent-entries-design.md`
**Branch:** `feat/assistant-layer` (reuses `ui/common/EditableEntryCard`; lands with the Assistant slice).

---

## File Structure

**Modify (data layer — additive methods):**
- `data/db/dao/ItemsDao.kt` — add `incrementStock`
- `data/repository/InventoryRepositoryImpl.kt` + `domain/repository/InventoryRepository.kt` — add `incrementStock`
- `data/db/dao/SalesDao.kt` — add `@Update update`
- `data/repository/SalesRepositoryImpl.kt` + `domain/repository/SalesRepository.kt` — add `updateSale`
- `data/db/dao/KhataDao.kt` — add suspend `findById`
- `data/db/dao/KhataTransactionDao.kt` — add `findBySaleId`, `deleteBySaleId`
- `data/repository/KhataRepositoryImpl.kt` + `domain/repository/KhataRepository.kt` — add `reverseSaleEffect`

**Create (domain):**
- `domain/usecase/QtyParsing.kt` — shared `parseLeadingQty` helper
- `domain/usecase/EditSaleUseCase.kt` — the reconcile orchestrator

**Modify (domain):**
- `domain/usecase/LogSaleUseCase.kt` — use the shared `parseLeadingQty` (remove its private duplicate)

**Modify (ui):**
- `ui/home/HomeViewModel.kt` — inject `EditSaleUseCase`, add `editSale`
- `ui/home/HomeScreen.kt` — tappable rows + edit `ModalBottomSheet` reusing `EditableEntryCard`

**Tests (create):**
- `app/src/test/java/com/artha/kirana/data/repository/KhataRepositoryImplTest.kt`
- `app/src/test/java/com/artha/kirana/domain/usecase/QtyParsingTest.kt`
- `app/src/test/java/com/artha/kirana/domain/usecase/EditSaleUseCaseTest.kt`

**DI note:** `EditSaleUseCase` is `@Inject constructor`; all repos already bound. No Hilt module changes.

---

## Task 1: Inventory incrementStock

**Files:**
- Modify: `app/src/main/java/com/artha/kirana/data/db/dao/ItemsDao.kt`
- Modify: `app/src/main/java/com/artha/kirana/domain/repository/InventoryRepository.kt`
- Modify: `app/src/main/java/com/artha/kirana/data/repository/InventoryRepositoryImpl.kt`

- [ ] **Step 1: Add `incrementStock` to `ItemsDao.kt`** (next to the existing `decrementStock`)

```kotlin
    @Query("UPDATE items SET qtyInStock = qtyInStock + :qty WHERE id = :id")
    suspend fun incrementStock(id: Long, qty: Double)
```

- [ ] **Step 2: Add to `InventoryRepository.kt`** (interface, next to `decrementStock`)

```kotlin
    suspend fun incrementStock(id: Long, qty: Double)
```

- [ ] **Step 3: Add the override to `InventoryRepositoryImpl.kt`** (next to `decrementStock`)

```kotlin
    override suspend fun incrementStock(id: Long, qty: Double) = dao.incrementStock(id, qty)
```

- [ ] **Step 4: Compile**

Run: `./gradlew :app:compileDebugKotlin`
Expected: BUILD SUCCESSFUL.

- [ ] **Step 5: Commit**

```bash
git add app/src/main/java/com/artha/kirana/data/db/dao/ItemsDao.kt app/src/main/java/com/artha/kirana/domain/repository/InventoryRepository.kt app/src/main/java/com/artha/kirana/data/repository/InventoryRepositoryImpl.kt
git commit -m "feat(edit): add InventoryRepository.incrementStock"
```

---

## Task 2: Sales updateSale

**Files:**
- Modify: `app/src/main/java/com/artha/kirana/data/db/dao/SalesDao.kt`
- Modify: `app/src/main/java/com/artha/kirana/domain/repository/SalesRepository.kt`
- Modify: `app/src/main/java/com/artha/kirana/data/repository/SalesRepositoryImpl.kt`

- [ ] **Step 1: Add `@Update` to `SalesDao.kt`**

Add the import `import androidx.room.Update` (next to the other Room imports), and this method next to `insert`:
```kotlin
    @Update
    suspend fun update(sale: SaleEntity)
```

- [ ] **Step 2: Add to `SalesRepository.kt`** (interface, next to `logSale`)

```kotlin
    suspend fun updateSale(sale: SaleEntity)
```

- [ ] **Step 3: Add the override to `SalesRepositoryImpl.kt`**

First READ `SalesRepositoryImpl.kt` to confirm the DAO property name (it delegates `logSale` to that dao's `insert`). Then add, mirroring `logSale`'s delegation (replace `dao` with the actual property name if different):
```kotlin
    override suspend fun updateSale(sale: SaleEntity) = dao.update(sale)
```

- [ ] **Step 4: Compile**

Run: `./gradlew :app:compileDebugKotlin`
Expected: BUILD SUCCESSFUL.

- [ ] **Step 5: Commit**

```bash
git add app/src/main/java/com/artha/kirana/data/db/dao/SalesDao.kt app/src/main/java/com/artha/kirana/domain/repository/SalesRepository.kt app/src/main/java/com/artha/kirana/data/repository/SalesRepositoryImpl.kt
git commit -m "feat(edit): add SalesRepository.updateSale"
```

---

## Task 3: Khata reverseSaleEffect (TDD the balance-undo)

**Files:**
- Modify: `app/src/main/java/com/artha/kirana/data/db/dao/KhataDao.kt`
- Modify: `app/src/main/java/com/artha/kirana/data/db/dao/KhataTransactionDao.kt`
- Modify: `app/src/main/java/com/artha/kirana/domain/repository/KhataRepository.kt`
- Modify: `app/src/main/java/com/artha/kirana/data/repository/KhataRepositoryImpl.kt`
- Test: `app/src/test/java/com/artha/kirana/data/repository/KhataRepositoryImplTest.kt`

- [ ] **Step 1: Write the failing test `KhataRepositoryImplTest.kt`**

```kotlin
package com.artha.kirana.data.repository

import com.artha.kirana.data.db.dao.KhataDao
import com.artha.kirana.data.db.dao.KhataTransactionDao
import com.artha.kirana.data.db.entity.KhataEntity
import com.artha.kirana.data.db.entity.KhataTransactionEntity
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.mockk
import kotlinx.coroutines.test.runTest
import org.junit.Test

class KhataRepositoryImplTest {

    private val khataDao = mockk<KhataDao>(relaxed = true)
    private val txnDao = mockk<KhataTransactionDao>(relaxed = true)
    private val repo = KhataRepositoryImpl(khataDao, txnDao)

    @Test
    fun reverseSaleEffectUndoesCreditBalanceAndDeletesTxns() = runTest {
        val party = KhataEntity(id = 1, partyName = "Ramesh", balance = 80.0)
        coEvery { txnDao.findBySaleId(5) } returns listOf(
            KhataTransactionEntity(id = 1, partyId = 1, amount = 80.0, type = "credit", saleId = 5),
        )
        coEvery { khataDao.findById(1) } returns party

        repo.reverseSaleEffect(5)

        // credit originally added +80 → reversing subtracts 80 → balance 0
        coVerify(exactly = 1) { khataDao.update(match { it.id == 1L && it.balance == 0.0 }) }
        coVerify(exactly = 1) { txnDao.deleteBySaleId(5) }
    }

    @Test
    fun reverseSaleEffectUndoesRepaymentBalance() = runTest {
        val party = KhataEntity(id = 2, partyName = "Priya", balance = 0.0)
        coEvery { txnDao.findBySaleId(9) } returns listOf(
            KhataTransactionEntity(id = 1, partyId = 2, amount = 50.0, type = "repayment", saleId = 9),
        )
        coEvery { khataDao.findById(2) } returns party

        repo.reverseSaleEffect(9)

        // repayment originally subtracted -50 → reversing adds 50 → balance 50
        coVerify(exactly = 1) { khataDao.update(match { it.id == 2L && it.balance == 50.0 }) }
        coVerify(exactly = 1) { txnDao.deleteBySaleId(9) }
    }

    @Test
    fun reverseSaleEffectWithNoTxnsJustDeletes() = runTest {
        coEvery { txnDao.findBySaleId(3) } returns emptyList()

        repo.reverseSaleEffect(3)

        coVerify(exactly = 0) { khataDao.update(any()) }
        coVerify(exactly = 1) { txnDao.deleteBySaleId(3) }
    }
}
```

- [ ] **Step 2: Run to verify it fails**

Run: `./gradlew :app:testDebugUnitTest --tests "com.artha.kirana.data.repository.KhataRepositoryImplTest"`
Expected: FAIL — `reverseSaleEffect` / `findBySaleId` / `deleteBySaleId` / `findById` unresolved.

- [ ] **Step 3: Add suspend `findById` to `KhataDao.kt`** (next to the existing `observeById`)

```kotlin
    @Query("SELECT * FROM khata WHERE id = :id")
    suspend fun findById(id: Long): KhataEntity?
```

- [ ] **Step 4: Add to `KhataTransactionDao.kt`** (next to the existing methods; ensure `import androidx.room.Query` is present — it is)

```kotlin
    @Query("SELECT * FROM khata_transactions WHERE saleId = :saleId")
    suspend fun findBySaleId(saleId: Long): List<KhataTransactionEntity>

    @Query("DELETE FROM khata_transactions WHERE saleId = :saleId")
    suspend fun deleteBySaleId(saleId: Long)
```

- [ ] **Step 5: Add to `KhataRepository.kt`** (interface)

```kotlin
    /** Remove the khata transaction(s) created by [saleId] and undo their balance impact. */
    suspend fun reverseSaleEffect(saleId: Long)
```

- [ ] **Step 6: Implement in `KhataRepositoryImpl.kt`** (add this override method to the class)

```kotlin
    override suspend fun reverseSaleEffect(saleId: Long) {
        txnDao.findBySaleId(saleId).forEach { t ->
            val delta = if (t.type == "credit") t.amount else -t.amount // original balance impact
            khataDao.findById(t.partyId)?.let { p ->
                khataDao.update(p.copy(balance = p.balance - delta, lastUpdated = System.currentTimeMillis()))
            }
        }
        txnDao.deleteBySaleId(saleId)
    }
```

- [ ] **Step 7: Run to verify it passes**

Run: `./gradlew :app:testDebugUnitTest --tests "com.artha.kirana.data.repository.KhataRepositoryImplTest"`
Expected: PASS (3 tests).

- [ ] **Step 8: Commit**

```bash
git add app/src/main/java/com/artha/kirana/data/db/dao/KhataDao.kt app/src/main/java/com/artha/kirana/data/db/dao/KhataTransactionDao.kt app/src/main/java/com/artha/kirana/domain/repository/KhataRepository.kt app/src/main/java/com/artha/kirana/data/repository/KhataRepositoryImpl.kt app/src/test/java/com/artha/kirana/data/repository/KhataRepositoryImplTest.kt
git commit -m "feat(edit): add KhataRepository.reverseSaleEffect with tests"
```

---

## Task 4: Shared parseLeadingQty helper

**Files:**
- Create: `app/src/main/java/com/artha/kirana/domain/usecase/QtyParsing.kt`
- Test: `app/src/test/java/com/artha/kirana/domain/usecase/QtyParsingTest.kt`
- Modify: `app/src/main/java/com/artha/kirana/domain/usecase/LogSaleUseCase.kt`

- [ ] **Step 1: Write the failing test `QtyParsingTest.kt`**

```kotlin
package com.artha.kirana.domain.usecase

import org.junit.Assert.assertEquals
import org.junit.Test

class QtyParsingTest {

    @Test
    fun parsesLeadingNumberWithUnit() {
        assertEquals(2.0, parseLeadingQty("2 kg"), 0.001)
    }

    @Test
    fun parsesDecimal() {
        assertEquals(2.5, parseLeadingQty("2.5 kg"), 0.001)
    }

    @Test
    fun nullIsZero() {
        assertEquals(0.0, parseLeadingQty(null), 0.001)
    }

    @Test
    fun noDigitsIsZero() {
        assertEquals(0.0, parseLeadingQty("kuch"), 0.001)
    }
}
```

- [ ] **Step 2: Run to verify it fails**

Run: `./gradlew :app:testDebugUnitTest --tests "com.artha.kirana.domain.usecase.QtyParsingTest"`
Expected: FAIL — `parseLeadingQty` unresolved.

- [ ] **Step 3: Create `QtyParsing.kt`**

```kotlin
package com.artha.kirana.domain.usecase

/** Pulls the first number out of a qty string ("2 kg" -> 2.0). null / no digits -> 0.0. */
internal fun parseLeadingQty(qty: String?): Double {
    if (qty == null) return 0.0
    val match = Regex("""\d+(\.\d+)?""").find(qty) ?: return 0.0
    return match.value.toDoubleOrNull() ?: 0.0
}
```

- [ ] **Step 4: Refactor `LogSaleUseCase.kt` to use the shared helper**

In `LogSaleUseCase.kt`: replace the call `val qty = parseLeadingNumber(entry.qty)` with `val qty = parseLeadingQty(entry.qty)`, and DELETE the private `parseLeadingNumber` function at the bottom of the class (the one with the `Regex("""\d+(\.\d+)?""")` body). Leave everything else unchanged.

- [ ] **Step 5: Run the helper test AND the existing LogSaleUseCase test (no regression)**

Run: `./gradlew :app:testDebugUnitTest --tests "com.artha.kirana.domain.usecase.QtyParsingTest" --tests "com.artha.kirana.domain.usecase.LogSaleUseCaseTest"`
Expected: PASS (QtyParsing 4 tests + LogSaleUseCase 3 tests all green).

- [ ] **Step 6: Commit**

```bash
git add app/src/main/java/com/artha/kirana/domain/usecase/QtyParsing.kt app/src/test/java/com/artha/kirana/domain/usecase/QtyParsingTest.kt app/src/main/java/com/artha/kirana/domain/usecase/LogSaleUseCase.kt
git commit -m "refactor(edit): extract shared parseLeadingQty helper"
```

---

## Task 5: EditSaleUseCase (TDD the reconcile)

**Files:**
- Create: `app/src/main/java/com/artha/kirana/domain/usecase/EditSaleUseCase.kt`
- Test: `app/src/test/java/com/artha/kirana/domain/usecase/EditSaleUseCaseTest.kt`

- [ ] **Step 1: Write the failing test `EditSaleUseCaseTest.kt`**

```kotlin
package com.artha.kirana.domain.usecase

import com.artha.kirana.data.db.entity.ItemEntity
import com.artha.kirana.data.db.entity.SaleEntity
import com.artha.kirana.domain.model.SaleEntry
import com.artha.kirana.domain.repository.InventoryRepository
import com.artha.kirana.domain.repository.KhataRepository
import com.artha.kirana.domain.repository.SalesRepository
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.mockk
import kotlinx.coroutines.test.runTest
import org.junit.Test

class EditSaleUseCaseTest {

    private val sales = mockk<SalesRepository>(relaxed = true)
    private val inventory = mockk<InventoryRepository>(relaxed = true)
    private val khata = mockk<KhataRepository>(relaxed = true)
    private val useCase = EditSaleUseCase(sales, inventory, khata)

    @Test
    fun creditToCashReversesOldKhataAndAppliesNoNewKhata() = runTest {
        coEvery { inventory.findByName(any()) } returns null
        val old = SaleEntity(
            id = 5, itemId = null, itemName = "चावल", qtySold = 0.0, amount = 80.0,
            type = "credit", party = "Ramesh", inputMethod = "typed", rawInput = null, timestamp = 111L,
        )
        val edited = SaleEntry(item = "चावल", qty = null, amount = 80.0, type = "cash", party = null)

        useCase(old, edited)

        coVerify(exactly = 1) { khata.reverseSaleEffect(5) }
        coVerify(exactly = 0) { khata.applyCredit(any(), any(), any()) }
        coVerify(exactly = 0) { khata.applyRepayment(any(), any(), any()) }
        coVerify(exactly = 1) {
            sales.updateSale(match { it.id == 5L && it.type == "cash" && it.party == null && it.timestamp == 111L })
        }
    }

    @Test
    fun itemSwapIncrementsOldStockAndDecrementsNewStock() = runTest {
        val sugar = ItemEntity(id = 2, name = "sugar", qtyInStock = 5.0)
        coEvery { inventory.findByName("sugar") } returns sugar
        val old = SaleEntity(
            id = 7, itemId = 1, itemName = "rice", qtySold = 2.0, amount = 80.0,
            type = "cash", party = null, inputMethod = "typed", rawInput = null, timestamp = 0L,
        )
        val edited = SaleEntry(item = "sugar", qty = "3", amount = 90.0, type = "cash", party = null)

        useCase(old, edited)

        coVerify(exactly = 1) { inventory.incrementStock(1, 2.0) } // reverse old
        coVerify(exactly = 1) { inventory.decrementStock(2, 3.0) } // apply new
        coVerify(exactly = 1) { khata.reverseSaleEffect(7) }
        coVerify(exactly = 1) {
            sales.updateSale(match { it.itemId == 2L && it.itemName == "sugar" && it.qtySold == 3.0 })
        }
    }

    @Test
    fun partyAndAmountChangeOnCreditAppliesNewCredit() = runTest {
        coEvery { inventory.findByName(any()) } returns null
        val old = SaleEntity(
            id = 9, itemId = null, itemName = null, qtySold = 0.0, amount = 80.0,
            type = "credit", party = "Ramesh", inputMethod = "typed", rawInput = null, timestamp = 0L,
        )
        val edited = SaleEntry(item = null, qty = null, amount = 50.0, type = "credit", party = "Priya")

        useCase(old, edited)

        coVerify(exactly = 1) { khata.reverseSaleEffect(9) }
        coVerify(exactly = 1) { khata.applyCredit("Priya", 50.0, 9) }
    }

    @Test
    fun unknownNewItemSkipsDecrement() = runTest {
        coEvery { inventory.findByName(any()) } returns null
        val old = SaleEntity(
            id = 3, itemId = null, itemName = null, qtySold = 0.0, amount = 20.0,
            type = "cash", party = null, inputMethod = "typed", rawInput = null, timestamp = 0L,
        )
        val edited = SaleEntry(item = "mystery", qty = "2", amount = 20.0, type = "cash", party = null)

        useCase(old, edited)

        coVerify(exactly = 0) { inventory.decrementStock(any(), any()) }
        coVerify(exactly = 1) { sales.updateSale(match { it.itemName == "mystery" && it.itemId == null }) }
    }
}
```

- [ ] **Step 2: Run to verify it fails**

Run: `./gradlew :app:testDebugUnitTest --tests "com.artha.kirana.domain.usecase.EditSaleUseCaseTest"`
Expected: FAIL — `EditSaleUseCase` unresolved.

- [ ] **Step 3: Implement `EditSaleUseCase.kt`**

```kotlin
package com.artha.kirana.domain.usecase

import com.artha.kirana.data.db.entity.SaleEntity
import com.artha.kirana.domain.model.SaleEntry
import com.artha.kirana.domain.repository.InventoryRepository
import com.artha.kirana.domain.repository.KhataRepository
import com.artha.kirana.domain.repository.SalesRepository
import javax.inject.Inject

/**
 * Edits a saved sale in place: reverses the original entry's inventory + khata effects, then
 * applies the edited entry's effects. Keeps the sale's id, timestamp, inputMethod, rawInput.
 * The §18-stable add path ([LogSaleUseCase]) is untouched.
 */
class EditSaleUseCase @Inject constructor(
    private val sales: SalesRepository,
    private val inventory: InventoryRepository,
    private val khata: KhataRepository,
) {
    suspend operator fun invoke(old: SaleEntity, edited: SaleEntry) {
        // 1. reverse old stock
        if (old.itemId != null && old.qtySold > 0.0) {
            inventory.incrementStock(old.itemId, old.qtySold)
        }
        // 2. reverse old khata (clean rewrite)
        khata.reverseSaleEffect(old.id)

        // 3. resolve new item + qty
        val newItem = edited.item?.let { inventory.findByName(it) }
        val newQty = parseLeadingQty(edited.qty)

        // 4. update the row in place (id, timestamp, inputMethod, rawInput preserved by copy)
        sales.updateSale(
            old.copy(
                itemId = newItem?.id,
                itemName = edited.item,
                qtySold = newQty,
                amount = edited.amount ?: 0.0,
                type = edited.type,
                party = edited.party,
            ),
        )

        // 5. apply new stock
        if (newItem != null && newQty > 0.0) {
            inventory.decrementStock(newItem.id, newQty)
        }

        // 6. apply new khata
        when (edited.type) {
            "credit" -> edited.party?.let { khata.applyCredit(it, edited.amount ?: 0.0, old.id) }
            "repayment" -> edited.party?.let { khata.applyRepayment(it, edited.amount ?: 0.0, old.id) }
        }
    }
}
```

- [ ] **Step 4: Run to verify it passes**

Run: `./gradlew :app:testDebugUnitTest --tests "com.artha.kirana.domain.usecase.EditSaleUseCaseTest"`
Expected: PASS (4 tests).

- [ ] **Step 5: Commit**

```bash
git add app/src/main/java/com/artha/kirana/domain/usecase/EditSaleUseCase.kt app/src/test/java/com/artha/kirana/domain/usecase/EditSaleUseCaseTest.kt
git commit -m "feat(edit): add EditSaleUseCase (reverse-old + apply-new) with tests"
```

---

## Task 6: HomeViewModel.editSale

**Files:**
- Modify: `app/src/main/java/com/artha/kirana/ui/home/HomeViewModel.kt`

- [ ] **Step 1: Replace the contents of `HomeViewModel.kt`** with:

```kotlin
package com.artha.kirana.ui.home

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.artha.kirana.data.db.entity.SaleEntity
import com.artha.kirana.domain.model.SaleEntry
import com.artha.kirana.domain.repository.SalesRepository
import com.artha.kirana.domain.usecase.EditSaleUseCase
import com.artha.kirana.util.TimeRange
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import javax.inject.Inject

@HiltViewModel
class HomeViewModel @Inject constructor(
    sales: SalesRepository,
    private val editSaleUseCase: EditSaleUseCase,
) : ViewModel() {

    private val startOfToday = TimeRange.startOfToday()

    val todayRevenue: StateFlow<Double> =
        sales.revenueBetween(startOfToday, Long.MAX_VALUE)
            .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), 0.0)

    val recentSales: StateFlow<List<SaleEntity>> =
        sales.observeSince(startOfToday)
            .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    /** Apply an edit to a saved sale (reverses old effects, applies new). Reactive via Room flows. */
    fun editSale(old: SaleEntity, edited: SaleEntry) {
        viewModelScope.launch { editSaleUseCase(old, edited) }
    }
}
```

- [ ] **Step 2: Compile**

Run: `./gradlew :app:compileDebugKotlin`
Expected: BUILD SUCCESSFUL.

- [ ] **Step 3: Commit**

```bash
git add app/src/main/java/com/artha/kirana/ui/home/HomeViewModel.kt
git commit -m "feat(edit): HomeViewModel.editSale wired to EditSaleUseCase"
```

---

## Task 7: HomeScreen edit sheet

**Files:**
- Modify: `app/src/main/java/com/artha/kirana/ui/home/HomeScreen.kt`

- [ ] **Step 1: Replace the contents of `HomeScreen.kt`** with:

```kotlin
package com.artha.kirana.ui.home

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.artha.kirana.data.db.entity.SaleEntity
import com.artha.kirana.domain.model.SaleEntry
import com.artha.kirana.ui.common.EditableEntryCard
import com.artha.kirana.ui.theme.AccentGreen
import com.artha.kirana.ui.theme.AccentRed
import com.artha.kirana.util.formatRupees

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun HomeScreen(vm: HomeViewModel = hiltViewModel()) {
    val revenue by vm.todayRevenue.collectAsStateWithLifecycle()
    val sales by vm.recentSales.collectAsStateWithLifecycle()
    var editing by remember { mutableStateOf<SaleEntity?>(null) }
    val sheetState = rememberModalBottomSheetState()

    Column(
        Modifier
            .fillMaxSize()
            .padding(16.dp),
    ) {
        Card(
            Modifier.fillMaxWidth(),
            colors = CardDefaults.cardColors(
                containerColor = MaterialTheme.colorScheme.primary,
                contentColor = MaterialTheme.colorScheme.onPrimary,
            ),
        ) {
            Column(Modifier.padding(20.dp)) {
                Text("आज की बिक्री · Today's sales", style = MaterialTheme.typography.titleMedium)
                Spacer(Modifier.height(6.dp))
                Text(
                    formatRupees(revenue),
                    style = MaterialTheme.typography.displaySmall,
                    fontWeight = FontWeight.Bold,
                )
            }
        }

        Spacer(Modifier.height(20.dp))
        Text("Recent entries", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
        Spacer(Modifier.height(8.dp))

        if (sales.isEmpty()) {
            Text(
                "No sales yet today. Tap “New Sale” to add one.",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        } else {
            LazyColumn(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                items(sales) { sale -> SaleRow(sale, onEdit = { editing = it }) }
            }
        }
    }

    val sale = editing
    if (sale != null) {
        var draft by remember(sale.id) { mutableStateOf(sale.toEntry()) }
        ModalBottomSheet(onDismissRequest = { editing = null }, sheetState = sheetState) {
            Column(Modifier.padding(start = 16.dp, end = 16.dp, bottom = 24.dp)) {
                Text(
                    "बदलें · Edit entry",
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.SemiBold,
                )
                Spacer(Modifier.height(12.dp))
                EditableEntryCard(draft) { draft = it }
                Spacer(Modifier.height(12.dp))
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    OutlinedButton(onClick = { editing = null }) { Text("Cancel") }
                    Button(onClick = {
                        vm.editSale(sale, draft)
                        editing = null
                    }) { Text("Save") }
                }
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun SaleRow(sale: SaleEntity, onEdit: (SaleEntity) -> Unit) {
    Card(onClick = { onEdit(sale) }, modifier = Modifier.fillMaxWidth()) {
        Row(
            Modifier
                .fillMaxWidth()
                .padding(16.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Column(Modifier.weight(1f)) {
                Text(
                    sale.itemName ?: "Sale",
                    style = MaterialTheme.typography.bodyLarge,
                    fontWeight = FontWeight.Medium,
                )
                Text(
                    saleSubtitle(sale),
                    style = MaterialTheme.typography.bodySmall,
                    color = when (sale.type) {
                        "credit" -> AccentRed
                        "repayment" -> AccentGreen
                        else -> MaterialTheme.colorScheme.onSurfaceVariant
                    },
                )
            }
            Text(
                formatRupees(sale.amount),
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold,
            )
        }
    }
}

/** "Cash sale" / "Credit · Ramesh" / "Repayment · Ramesh". Customer appended only when present. */
private fun saleSubtitle(sale: SaleEntity): String {
    val typeLabel = when (sale.type) {
        "cash" -> "Cash sale"
        "credit" -> "Credit"
        "repayment" -> "Repayment"
        else -> sale.type.replaceFirstChar { it.uppercase() }
    }
    return sale.party?.let { "$typeLabel · $it" } ?: typeLabel
}

/** Map a saved sale to the editable [SaleEntry] form for the edit sheet. */
private fun SaleEntity.toEntry(): SaleEntry = SaleEntry(
    item = itemName,
    qty = when {
        qtySold == 0.0 -> null
        qtySold % 1.0 == 0.0 -> qtySold.toLong().toString()
        else -> qtySold.toString()
    },
    amount = amount,
    type = type,
    party = party,
)
```

- [ ] **Step 2: Compile**

Run: `./gradlew :app:compileDebugKotlin`
Expected: BUILD SUCCESSFUL. (If the compiler reports that `Card(onClick = ...)` or `ModalBottomSheet`/`rememberModalBottomSheetState` requires an opt-in not already applied, the `@OptIn(ExperimentalMaterial3Api::class)` annotations on both `HomeScreen` and `SaleRow` cover it. Do NOT add new gradle dependencies — `ModalBottomSheet` is in the existing material3 dep, already used by the Inventory `AddItemSheet`.)

- [ ] **Step 3: Commit**

```bash
git add app/src/main/java/com/artha/kirana/ui/home/HomeScreen.kt
git commit -m "feat(edit): tap a Home recent entry to edit it in a bottom sheet"
```

---

## Task 8: On-device checkpoint (exit criteria)

**Files:** none (verification only).

- [ ] **Step 1: Full unit suite**

Run: `./gradlew :app:testDebugUnitTest`
Expected: BUILD SUCCESSFUL; all green (new KhataRepositoryImpl/QtyParsing/EditSaleUseCase tests + existing).

- [ ] **Step 2: Install on the iQOO**

Run:
```bash
adb devices                     # expect 10BFBG0CEL001DB
./gradlew :app:installDebug
adb shell am start -n com.artha.kirana/.MainActivity
```
Expected: app launches.

- [ ] **Step 3: Human verifies the exit criteria on-device** (adb-driving Compose is flaky — human drives)

1. Log a cash sale, then tap it in "Recent entries" → edit the amount → Save → Home revenue + P&L update by the delta.
2. Log a credit sale for Ramesh ₹80 → tap it → change amount to ₹50 → Save → Khata: Ramesh's balance is the corrected value and his transaction history shows only the corrected entry (no ₹80 + reversal noise).
3. Add a stocked item, log a sale of it, tap it → change qty → Save → that item's stock reflects the corrected quantity (old qty restored, new deducted).
4. Tap a credit sale → change type to cash → Save → the party's khata is reduced and the sale no longer contributes to khata.

- [ ] **Step 4: Update STATUS.md**

Edit `docs/STATUS.md`: note that Home "Recent entries" are now editable with full inventory+khata reconcile (on `feat/assistant-layer`).

- [ ] **Step 5: Commit**

```bash
git add docs/STATUS.md
git commit -m "docs: Home recent entries editable with reconcile (on-device verified)"
```

# Data-Layer Restructure Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Auto-compute sale prices from inventory, make customers first-class (FK-linked), snapshot per-line prices for drift-free margins, and ship four analytics queries — all without letting the LLM touch SQL.

**Architecture:** Three parts. **Part 1 (Feature A)** folds an auto-price enrich step into `ParseSaleEntryUseCase` (no schema change) so both Sale Entry and the Assistant get it. **Merge checkpoint.** **Part 2 (B+C+snapshot)** bumps Room to v3: a new `customers` identity table that `sales` and `khata` reference by FK, plus snapshotted `unitPrice`/`unitCost` on sales and the indices analytics need. **Part 3 (E)** adds DAO queries + use-cases for top sellers, per-customer history, margin-by-item, and day-of-week trends.

**Tech Stack:** Kotlin, Room (KSP), Hilt, kotlinx-coroutines. Tests: JUnit4 + MockK + `kotlinx-coroutines-test` (existing convention — DAO SQL is validated by the Room compiler + on-device, pure logic is unit-tested). Toolchain pinned: AGP 8.13 / Gradle 8.13 / JDK 17.

**Spec:** `docs/superpowers/specs/2026-06-14-data-layer-restructure-design.md`

**Test/build commands:**
- Unit tests: `./gradlew :app:testDebugUnitTest`
- Single test class: `./gradlew :app:testDebugUnitTest --tests "com.artha.kirana.domain.usecase.AutoPriceTest"`
- Build (validates Room schema via KSP): `./gradlew :app:assembleDebug`

---

## Part 1 — Feature A: Auto-price (branch: `feat/assistant-layer`, NO schema change)

### Task 1: Pure `computeAutoPrice` function (TDD)

**Files:**
- Create: `app/src/main/java/com/artha/kirana/domain/usecase/AutoPrice.kt`
- Test: `app/src/test/java/com/artha/kirana/domain/usecase/AutoPriceTest.kt`

- [ ] **Step 1: Write the failing test**

```kotlin
// app/src/test/java/com/artha/kirana/domain/usecase/AutoPriceTest.kt
package com.artha.kirana.domain.usecase

import com.artha.kirana.data.db.entity.ItemEntity
import com.artha.kirana.domain.model.SaleEntry
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class AutoPriceTest {

    private fun entry(amount: Double?, qty: String?, item: String? = "rice") =
        SaleEntry(item = item, qty = qty, amount = amount, type = "cash", party = null)

    private fun item(sellPrice: Double) = ItemEntity(id = 1, name = "rice", sellPrice = sellPrice)

    @Test
    fun explicitAmountAlwaysWins() {
        assertEquals(80.0, computeAutoPrice(entry(amount = 80.0, qty = "2 kg"), item(40.0)))
    }

    @Test
    fun computesFromSellPriceTimesQtyWhenAmountNull() {
        assertEquals(80.0, computeAutoPrice(entry(amount = null, qty = "2 kg"), item(40.0)))
    }

    @Test
    fun nullWhenItemNotResolved() {
        assertNull(computeAutoPrice(entry(amount = null, qty = "2 kg"), null))
    }

    @Test
    fun nullWhenSellPriceZeroOrUnset() {
        assertNull(computeAutoPrice(entry(amount = null, qty = "2 kg"), item(0.0)))
    }

    @Test
    fun nullWhenQtyMissingOrZero() {
        assertNull(computeAutoPrice(entry(amount = null, qty = null), item(40.0)))
        assertNull(computeAutoPrice(entry(amount = null, qty = "0"), item(40.0)))
    }
}
```

- [ ] **Step 2: Run test to verify it fails**

Run: `./gradlew :app:testDebugUnitTest --tests "com.artha.kirana.domain.usecase.AutoPriceTest"`
Expected: FAIL — `Unresolved reference: computeAutoPrice`.

- [ ] **Step 3: Write minimal implementation**

```kotlin
// app/src/main/java/com/artha/kirana/domain/usecase/AutoPrice.kt
package com.artha.kirana.domain.usecase

import com.artha.kirana.data.db.entity.ItemEntity
import com.artha.kirana.domain.model.SaleEntry

/**
 * Computes a sale line total from inventory when the LLM gave a quantity but no amount.
 * Returns the entry's existing amount when present (an explicit spoken/typed price always
 * wins). Returns null — leaving the amount unfilled for the user to edit — unless the item
 * resolved in inventory with sellPrice > 0 and a positive quantity.
 */
internal fun computeAutoPrice(entry: SaleEntry, item: ItemEntity?): Double? {
    if (entry.amount != null) return entry.amount
    if (item == null || item.sellPrice <= 0.0) return null
    val qty = parseLeadingQty(entry.qty)
    if (qty <= 0.0) return null
    return item.sellPrice * qty
}
```

- [ ] **Step 4: Run test to verify it passes**

Run: `./gradlew :app:testDebugUnitTest --tests "com.artha.kirana.domain.usecase.AutoPriceTest"`
Expected: PASS (5 tests).

- [ ] **Step 5: Commit**

```bash
git add app/src/main/java/com/artha/kirana/domain/usecase/AutoPrice.kt app/src/test/java/com/artha/kirana/domain/usecase/AutoPriceTest.kt
git commit -m "feat(auto-price): pure computeAutoPrice from inventory sellPrice x qty"
```

---

### Task 2: Wire auto-price into `ParseSaleEntryUseCase`

**Files:**
- Modify: `app/src/main/java/com/artha/kirana/domain/usecase/ParseSaleEntryUseCase.kt`
- Test: `app/src/test/java/com/artha/kirana/domain/usecase/ParseSaleEntryUseCaseTest.kt` (create)

Note: `ParseSaleEntryUseCase` uses `@Inject constructor`; `InventoryRepository` is already bound in `RepositoryModule`, so Hilt wires the new param automatically — no DI module change. `RouteAssistantUseCaseTest` mocks `ParseSaleEntryUseCase` as a whole, so it is unaffected.

- [ ] **Step 1: Write the failing test**

```kotlin
// app/src/test/java/com/artha/kirana/domain/usecase/ParseSaleEntryUseCaseTest.kt
package com.artha.kirana.domain.usecase

import com.artha.kirana.data.db.entity.ItemEntity
import com.artha.kirana.data.llm.LlmEngine
import com.artha.kirana.domain.model.SaleEntry
import com.artha.kirana.domain.repository.InventoryRepository
import io.mockk.coEvery
import io.mockk.mockk
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Test

class ParseSaleEntryUseCaseTest {

    private val engine = mockk<LlmEngine>()
    private val inventory = mockk<InventoryRepository>(relaxed = true)
    private val useCase = ParseSaleEntryUseCase(engine, inventory)

    @Test
    fun fillsAmountFromInventoryWhenNull() = runTest {
        coEvery { engine.parseSale(any()) } returns Result.success(
            listOf(SaleEntry(item = "rice", qty = "2 kg", amount = null, type = "cash", party = null)),
        )
        coEvery { inventory.findByName("rice") } returns ItemEntity(id = 1, name = "rice", sellPrice = 40.0)

        val result = useCase("do kilo chawal").getOrThrow()

        assertEquals(80.0, result.single().amount)
    }

    @Test
    fun leavesExplicitAmountUntouched() = runTest {
        coEvery { engine.parseSale(any()) } returns Result.success(
            listOf(SaleEntry(item = "rice", qty = "2 kg", amount = 100.0, type = "cash", party = null)),
        )
        coEvery { inventory.findByName("rice") } returns ItemEntity(id = 1, name = "rice", sellPrice = 40.0)

        val result = useCase("do kilo chawal sau rupaye").getOrThrow()

        assertEquals(100.0, result.single().amount)
    }
}
```

- [ ] **Step 2: Run test to verify it fails**

Run: `./gradlew :app:testDebugUnitTest --tests "com.artha.kirana.domain.usecase.ParseSaleEntryUseCaseTest"`
Expected: FAIL — constructor takes 1 arg, not 2 (`too many arguments`).

- [ ] **Step 3: Modify the use-case**

```kotlin
// app/src/main/java/com/artha/kirana/domain/usecase/ParseSaleEntryUseCase.kt
package com.artha.kirana.domain.usecase

import com.artha.kirana.data.llm.LlmEngine
import com.artha.kirana.domain.model.SaleEntry
import com.artha.kirana.domain.repository.InventoryRepository
import com.artha.kirana.util.HindiNumbers
import javax.inject.Inject

/**
 * Parses free Hindi/Hinglish sale text into [SaleEntry]s via the on-device LLM, then
 * enriches each entry with an auto-computed price from inventory when the shopkeeper gave a
 * quantity but no amount (see [computeAutoPrice]). Both Sale Entry and the Assistant call
 * this, so both get auto-price for free.
 */
class ParseSaleEntryUseCase @Inject constructor(
    private val engine: LlmEngine,
    private val inventory: InventoryRepository,
) {
    suspend operator fun invoke(text: String): Result<List<SaleEntry>> =
        // Convert Hindi number-words to digits first — the 3B reliably reads digits but flubs
        // spoken Hindi numbers (पचास→40) when a quantity number is nearby.
        engine.parseSale(HindiNumbers.normalize(text)).map { entries ->
            entries.map { e ->
                val item = e.item?.let { inventory.findByName(it) }
                e.copy(amount = computeAutoPrice(e, item))
            }
        }
}
```

- [ ] **Step 4: Run the full unit suite (ensures no regression in callers)**

Run: `./gradlew :app:testDebugUnitTest`
Expected: PASS — including `ParseSaleEntryUseCaseTest`, `RouteAssistantUseCaseTest`, `LogSaleUseCaseTest`, `EditSaleUseCaseTest`.

- [ ] **Step 5: Build-verify the app compiles (Hilt graph)**

Run: `./gradlew :app:assembleDebug`
Expected: BUILD SUCCESSFUL.

- [ ] **Step 6: Commit**

```bash
git add app/src/main/java/com/artha/kirana/domain/usecase/ParseSaleEntryUseCase.kt app/src/test/java/com/artha/kirana/domain/usecase/ParseSaleEntryUseCaseTest.kt
git commit -m "feat(auto-price): enrich parsed sales with inventory price in ParseSaleEntryUseCase"
```

---

### Task 3: On-device verification of auto-price (manual gate)

**No code.** Requires the iQOO + llama-server (`./scripts/start-llama-server.sh`).

- [ ] **Step 1: Install and confirm an item with a sell price exists**

Run: `./gradlew :app:installDebug && adb shell am start -n com.artha.kirana/.MainActivity`
In Inventory, add (or confirm) `chawal` with sellPrice = 40.

- [ ] **Step 2: Sale Entry path** — type/speak "दो किलो चावल" (no price). Confirm card shows amount **₹80** (auto-computed). Edit works.

- [ ] **Step 3: Assistant path** — open the Assistant tab, send "दो किलो चावल". The `SaleDraft` confirm card shows **₹80**.

- [ ] **Step 4: §18 non-regression** — run the 5 §18 cases (all carry explicit amounts); confirm amounts unchanged and 5/5 still parse. Reference: `docs/demo-runbook.md`.

- [ ] **Step 5: Update STATUS** — note auto-price done & device-verified, then commit the doc.

```bash
git add docs/STATUS.md
git commit -m "docs: auto-price (Feature A) done & device-verified"
```

---

### CHECKPOINT — Merge `feat/assistant-layer` → `main`

> **Human-gated.** Before Part 2, the Assistant + edit-recent-entries features on `feat/assistant-layer` need their pending on-device walkthroughs (Assistant: 3 intent flows + voice + offline; edit: the 4 exit criteria — see `docs/STATUS.md`). Once those pass, merge to `main`. Feature A rides along in the merge. Part 2 branches off the updated `main` (e.g. `git checkout main && git pull && git checkout -b feat/data-layer-v3`).

---

## Part 2 — Features B + C + snapshot: DB v3 (branch off `main`)

### Task 4: `CustomerEntity` + `CustomersDao` + database v3

**Files:**
- Create: `app/src/main/java/com/artha/kirana/data/db/entity/CustomerEntity.kt`
- Create: `app/src/main/java/com/artha/kirana/data/db/dao/CustomersDao.kt`
- Modify: `app/src/main/java/com/artha/kirana/data/db/ArthaDatabase.kt`
- Modify: `app/src/main/java/com/artha/kirana/di/DatabaseModule.kt`

- [ ] **Step 1: Create the entity**

```kotlin
// app/src/main/java/com/artha/kirana/data/db/entity/CustomerEntity.kt
package com.artha.kirana.data.db.entity

import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey

@Entity(
    tableName = "customers",
    indices = [Index(value = ["name"], unique = true)],
)
data class CustomerEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val name: String,                  // looked up COLLATE NOCASE in resolveOrCreate
    val nameHi: String? = null,
    val phone: String? = null,
    val createdAt: Long = System.currentTimeMillis(),
)
```

- [ ] **Step 2: Create the DAO**

```kotlin
// app/src/main/java/com/artha/kirana/data/db/dao/CustomersDao.kt
package com.artha.kirana.data.db.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.Query
import com.artha.kirana.data.db.entity.CustomerEntity
import kotlinx.coroutines.flow.Flow

@Dao
interface CustomersDao {
    @Insert
    suspend fun insert(customer: CustomerEntity): Long

    @Query("SELECT * FROM customers WHERE name = :name COLLATE NOCASE LIMIT 1")
    suspend fun findByName(name: String): CustomerEntity?

    @Query("SELECT * FROM customers ORDER BY name COLLATE NOCASE")
    fun observeAll(): Flow<List<CustomerEntity>>
}
```

- [ ] **Step 3: Register in `ArthaDatabase` (bump to v3)**

In `ArthaDatabase.kt`: add `import com.artha.kirana.data.db.dao.CustomersDao` and `import com.artha.kirana.data.db.entity.CustomerEntity`; add `CustomerEntity::class` to the `entities` array; change `version = 2` → `version = 3`; add `abstract fun customersDao(): CustomersDao`.

- [ ] **Step 4: Provide the DAO in `DatabaseModule`**

Add to `DatabaseModule.kt` (import `CustomersDao`):

```kotlin
    @Provides
    fun provideCustomersDao(db: ArthaDatabase): CustomersDao = db.customersDao()
```

- [ ] **Step 5: Build-verify (Room KSP validates the v3 schema)**

Run: `./gradlew :app:assembleDebug`
Expected: BUILD SUCCESSFUL (Room generates `CustomersDao_Impl`, accepts version 3).

- [ ] **Step 6: Commit**

```bash
git add app/src/main/java/com/artha/kirana/data/db/entity/CustomerEntity.kt app/src/main/java/com/artha/kirana/data/db/dao/CustomersDao.kt app/src/main/java/com/artha/kirana/data/db/ArthaDatabase.kt app/src/main/java/com/artha/kirana/di/DatabaseModule.kt
git commit -m "feat(db): add customers table, CustomersDao, bump Room to v3"
```

---

### Task 5: `CustomerRepository.resolveOrCreate` (TDD)

**Files:**
- Create: `app/src/main/java/com/artha/kirana/domain/repository/CustomerRepository.kt`
- Create: `app/src/main/java/com/artha/kirana/data/repository/CustomerRepositoryImpl.kt`
- Modify: `app/src/main/java/com/artha/kirana/di/RepositoryModule.kt`
- Test: `app/src/test/java/com/artha/kirana/data/repository/CustomerRepositoryImplTest.kt`

- [ ] **Step 1: Write the failing test**

```kotlin
// app/src/test/java/com/artha/kirana/data/repository/CustomerRepositoryImplTest.kt
package com.artha.kirana.data.repository

import com.artha.kirana.data.db.dao.CustomersDao
import com.artha.kirana.data.db.entity.CustomerEntity
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.mockk
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Test

class CustomerRepositoryImplTest {

    private val dao = mockk<CustomersDao>(relaxed = true)
    private val repo = CustomerRepositoryImpl(dao)

    @Test
    fun returnsExistingIdWhenNameFound() = runTest {
        coEvery { dao.findByName("Ramesh") } returns CustomerEntity(id = 7, name = "Ramesh")

        assertEquals(7L, repo.resolveOrCreate("Ramesh"))
        coVerify(exactly = 0) { dao.insert(any()) }
    }

    @Test
    fun insertsAndReturnsNewIdWhenNameNotFound() = runTest {
        coEvery { dao.findByName("Priya") } returns null
        coEvery { dao.insert(any()) } returns 12L

        assertEquals(12L, repo.resolveOrCreate("Priya"))
        coVerify(exactly = 1) { dao.insert(match { it.name == "Priya" }) }
    }
}
```

- [ ] **Step 2: Run test to verify it fails**

Run: `./gradlew :app:testDebugUnitTest --tests "com.artha.kirana.data.repository.CustomerRepositoryImplTest"`
Expected: FAIL — `Unresolved reference: CustomerRepositoryImpl`.

- [ ] **Step 3: Create the interface**

```kotlin
// app/src/main/java/com/artha/kirana/domain/repository/CustomerRepository.kt
package com.artha.kirana.domain.repository

import com.artha.kirana.data.db.entity.CustomerEntity
import kotlinx.coroutines.flow.Flow

interface CustomerRepository {
    /** Returns the id of the customer named [name], inserting a new row if none exists. */
    suspend fun resolveOrCreate(name: String): Long
    suspend fun findByName(name: String): CustomerEntity?
    fun observeAll(): Flow<List<CustomerEntity>>
}
```

- [ ] **Step 4: Create the implementation**

```kotlin
// app/src/main/java/com/artha/kirana/data/repository/CustomerRepositoryImpl.kt
package com.artha.kirana.data.repository

import com.artha.kirana.data.db.dao.CustomersDao
import com.artha.kirana.data.db.entity.CustomerEntity
import com.artha.kirana.domain.repository.CustomerRepository
import kotlinx.coroutines.flow.Flow
import javax.inject.Inject

class CustomerRepositoryImpl @Inject constructor(
    private val dao: CustomersDao,
) : CustomerRepository {

    override suspend fun resolveOrCreate(name: String): Long =
        dao.findByName(name)?.id ?: dao.insert(CustomerEntity(name = name))

    override suspend fun findByName(name: String): CustomerEntity? = dao.findByName(name)

    override fun observeAll(): Flow<List<CustomerEntity>> = dao.observeAll()
}
```

- [ ] **Step 5: Bind in `RepositoryModule`**

Add to `RepositoryModule.kt` (imports `CustomerRepositoryImpl` / `CustomerRepository`):

```kotlin
    @Binds
    @Singleton
    abstract fun bindCustomerRepository(impl: CustomerRepositoryImpl): CustomerRepository
```

- [ ] **Step 6: Run test + build to verify**

Run: `./gradlew :app:testDebugUnitTest --tests "com.artha.kirana.data.repository.CustomerRepositoryImplTest"` → PASS
Run: `./gradlew :app:assembleDebug` → BUILD SUCCESSFUL

- [ ] **Step 7: Commit**

```bash
git add app/src/main/java/com/artha/kirana/domain/repository/CustomerRepository.kt app/src/main/java/com/artha/kirana/data/repository/CustomerRepositoryImpl.kt app/src/main/java/com/artha/kirana/di/RepositoryModule.kt app/src/test/java/com/artha/kirana/data/repository/CustomerRepositoryImplTest.kt
git commit -m "feat(customers): CustomerRepository.resolveOrCreate (idempotent name->id)"
```

---

### Task 6: `SaleEntity` — customerId + price snapshots + FKs + indices

**Files:**
- Modify: `app/src/main/java/com/artha/kirana/data/db/entity/SaleEntity.kt`

- [ ] **Step 1: Replace the entity**

```kotlin
// app/src/main/java/com/artha/kirana/data/db/entity/SaleEntity.kt
package com.artha.kirana.data.db.entity

import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.PrimaryKey

@Entity(
    tableName = "sales",
    foreignKeys = [
        ForeignKey(
            entity = ItemEntity::class,
            parentColumns = ["id"],
            childColumns = ["itemId"],
            onDelete = ForeignKey.SET_NULL,
        ),
        ForeignKey(
            entity = CustomerEntity::class,
            parentColumns = ["id"],
            childColumns = ["customerId"],
            onDelete = ForeignKey.SET_NULL,
        ),
    ],
    indices = [Index("timestamp"), Index("itemId"), Index("customerId")],
)
data class SaleEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val itemId: Long? = null,          // null when the item isn't a tracked inventory item
    val itemName: String? = null,      // denormalized name of what was sold (for display)
    val customerId: Long? = null,      // FK→customers; null for anonymous cash sales
    val qtySold: Double = 0.0,
    val amount: Double,
    val unitPrice: Double? = null,     // snapshot of items.sellPrice at sale time
    val unitCost: Double? = null,      // snapshot of items.costPrice at sale time
    val type: String,                  // "cash" | "credit" | "repayment"
    val party: String? = null,         // denormalized customer name (display + fallback)
    val inputMethod: String,           // "voice" | "scan" | "typed"
    val rawInput: String? = null,
    val timestamp: Long = System.currentTimeMillis(),
)
```

- [ ] **Step 2: Build-verify (Room validates the new FKs/indices/columns)**

Run: `./gradlew :app:assembleDebug`
Expected: BUILD SUCCESSFUL. (Existing call sites still compile — new fields have defaults.)

- [ ] **Step 3: Commit**

```bash
git add app/src/main/java/com/artha/kirana/data/db/entity/SaleEntity.kt
git commit -m "feat(db): sales gains customerId FK, unitPrice/unitCost snapshots, indices"
```

---

### Task 7: `KhataEntity` — customerId FK + DAO lookup

**Files:**
- Modify: `app/src/main/java/com/artha/kirana/data/db/entity/KhataEntity.kt`
- Modify: `app/src/main/java/com/artha/kirana/data/db/dao/KhataDao.kt`

- [ ] **Step 1: Replace the entity**

```kotlin
// app/src/main/java/com/artha/kirana/data/db/entity/KhataEntity.kt
package com.artha.kirana.data.db.entity

import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.PrimaryKey

@Entity(
    tableName = "khata",
    foreignKeys = [
        ForeignKey(
            entity = CustomerEntity::class,
            parentColumns = ["id"],
            childColumns = ["customerId"],
            onDelete = ForeignKey.CASCADE,
        ),
    ],
    indices = [Index(value = ["customerId"], unique = true)],  // one ledger row per customer
)
data class KhataEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val customerId: Long,              // FK→customers
    val partyName: String,             // denormalized for display
    val balance: Double = 0.0,         // positive = they owe us
    val lastUpdated: Long = System.currentTimeMillis(),
)
```

- [ ] **Step 2: Add a customer-keyed lookup to `KhataDao`**

Add to `KhataDao.kt` (keep the existing `findByName` — it is still used by no-customer callers? No: replace its use, but leaving the query compiles harmlessly. Add the new query):

```kotlin
    @Query("SELECT * FROM khata WHERE customerId = :customerId LIMIT 1")
    suspend fun findByCustomerId(customerId: Long): KhataEntity?
```

- [ ] **Step 3: Build-verify**

Run: `./gradlew :app:assembleDebug`
Expected: FAIL to compile in `KhataRepositoryImpl.adjust` — `KhataEntity(...)` now requires `customerId`. That is fixed in Task 8. (If you want a green build here, do Tasks 7 + 8 as one commit.)

- [ ] **Step 4: Commit (with Task 8) — see Task 8 Step 6.**

---

### Task 8: `KhataRepositoryImpl.adjust` — customer-keyed (TDD)

**Files:**
- Modify: `app/src/main/java/com/artha/kirana/data/repository/KhataRepositoryImpl.kt`
- Test: `app/src/test/java/com/artha/kirana/data/repository/KhataRepositoryImplTest.kt`

The public `KhataRepository` API stays name-based (`applyCredit(party: String, …)`) so `KhataScreen` record-payment is unchanged. The impl resolves the name to a `customerId` via `CustomerRepository` (idempotent — safe even though `LogSaleUseCase` also resolved it), then upserts the khata row by `customerId`.

- [ ] **Step 1: Update the existing tests + add a resolve test**

Replace the body of `KhataRepositoryImplTest.kt` with (adds the `customers` mock + a new-party test; keeps the reverse tests):

```kotlin
// app/src/test/java/com/artha/kirana/data/repository/KhataRepositoryImplTest.kt
package com.artha.kirana.data.repository

import com.artha.kirana.data.db.dao.KhataDao
import com.artha.kirana.data.db.dao.KhataTransactionDao
import com.artha.kirana.data.db.entity.KhataEntity
import com.artha.kirana.data.db.entity.KhataTransactionEntity
import com.artha.kirana.domain.repository.CustomerRepository
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.mockk
import kotlinx.coroutines.test.runTest
import org.junit.Test

class KhataRepositoryImplTest {

    private val khataDao = mockk<KhataDao>(relaxed = true)
    private val txnDao = mockk<KhataTransactionDao>(relaxed = true)
    private val customers = mockk<CustomerRepository>(relaxed = true)
    private val repo = KhataRepositoryImpl(khataDao, txnDao, customers)

    @Test
    fun applyCreditCreatesLedgerKeyedByResolvedCustomer() = runTest {
        coEvery { customers.resolveOrCreate("Ramesh") } returns 7L
        coEvery { khataDao.findByCustomerId(7L) } returns null

        repo.applyCredit("Ramesh", amount = 80.0, saleId = 5L)

        coVerify(exactly = 1) {
            khataDao.insert(match { it.customerId == 7L && it.partyName == "Ramesh" && it.balance == 80.0 })
        }
        coVerify(exactly = 1) { txnDao.insert(match { it.amount == 80.0 && it.type == "credit" && it.saleId == 5L }) }
    }

    @Test
    fun applyRepaymentUpdatesExistingLedger() = runTest {
        coEvery { customers.resolveOrCreate("Ramesh") } returns 7L
        coEvery { khataDao.findByCustomerId(7L) } returns
            KhataEntity(id = 1, customerId = 7L, partyName = "Ramesh", balance = 80.0)

        repo.applyRepayment("Ramesh", amount = 30.0, saleId = 9L)

        coVerify(exactly = 1) { khataDao.update(match { it.id == 1L && it.balance == 50.0 }) }
        coVerify(exactly = 1) { txnDao.insert(match { it.amount == 30.0 && it.type == "repayment" }) }
    }

    @Test
    fun reverseSaleEffectUndoesCreditBalanceAndDeletesTxns() = runTest {
        val party = KhataEntity(id = 1, customerId = 7L, partyName = "Ramesh", balance = 80.0)
        coEvery { txnDao.findBySaleId(5) } returns listOf(
            KhataTransactionEntity(id = 1, partyId = 1, amount = 80.0, type = "credit", saleId = 5),
        )
        coEvery { khataDao.findById(1) } returns party

        repo.reverseSaleEffect(5)

        coVerify(exactly = 1) { khataDao.update(match { it.id == 1L && it.balance == 0.0 }) }
        coVerify(exactly = 1) { txnDao.deleteBySaleId(5) }
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

- [ ] **Step 2: Run test to verify it fails**

Run: `./gradlew :app:testDebugUnitTest --tests "com.artha.kirana.data.repository.KhataRepositoryImplTest"`
Expected: FAIL — `KhataRepositoryImpl` constructor takes 2 args, not 3.

- [ ] **Step 3: Update `KhataRepositoryImpl`**

```kotlin
// app/src/main/java/com/artha/kirana/data/repository/KhataRepositoryImpl.kt
package com.artha.kirana.data.repository

import com.artha.kirana.data.db.dao.KhataDao
import com.artha.kirana.data.db.dao.KhataTransactionDao
import com.artha.kirana.data.db.entity.KhataEntity
import com.artha.kirana.data.db.entity.KhataTransactionEntity
import com.artha.kirana.domain.repository.CustomerRepository
import com.artha.kirana.domain.repository.KhataRepository
import kotlinx.coroutines.flow.Flow
import javax.inject.Inject

class KhataRepositoryImpl @Inject constructor(
    private val khataDao: KhataDao,
    private val txnDao: KhataTransactionDao,
    private val customers: CustomerRepository,
) : KhataRepository {

    override fun observeAll(): Flow<List<KhataEntity>> = khataDao.observeAll()
    override fun totalOutstanding(): Flow<Double> = khataDao.totalOutstanding()
    override fun observeParty(id: Long): Flow<KhataEntity?> = khataDao.observeById(id)
    override fun observeTransactions(id: Long): Flow<List<KhataTransactionEntity>> =
        txnDao.observeForParty(id)

    override suspend fun applyCredit(party: String, amount: Double, saleId: Long?) =
        adjust(party, delta = amount, type = "credit", saleId = saleId, amount = amount)

    override suspend fun applyRepayment(party: String, amount: Double, saleId: Long?) =
        adjust(party, delta = -amount, type = "repayment", saleId = saleId, amount = amount)

    override suspend fun reverseSaleEffect(saleId: Long) {
        txnDao.findBySaleId(saleId).forEach { t ->
            val delta = if (t.type == "credit") t.amount else -t.amount // original balance impact
            khataDao.findById(t.partyId)?.let { p ->
                khataDao.update(p.copy(balance = p.balance - delta, lastUpdated = System.currentTimeMillis()))
            }
        }
        txnDao.deleteBySaleId(saleId)
    }

    private suspend fun adjust(party: String, delta: Double, type: String, saleId: Long?, amount: Double) {
        val customerId = customers.resolveOrCreate(party)
        val existing = khataDao.findByCustomerId(customerId)
        val partyId = if (existing == null) {
            khataDao.insert(KhataEntity(customerId = customerId, partyName = party, balance = delta))
        } else {
            khataDao.update(
                existing.copy(
                    balance = existing.balance + delta,
                    lastUpdated = System.currentTimeMillis(),
                ),
            )
            existing.id
        }
        txnDao.insert(
            KhataTransactionEntity(partyId = partyId, amount = amount, type = type, saleId = saleId),
        )
    }
}
```

- [ ] **Step 4: Run the test to verify it passes**

Run: `./gradlew :app:testDebugUnitTest --tests "com.artha.kirana.data.repository.KhataRepositoryImplTest"`
Expected: PASS (4 tests).

- [ ] **Step 5: Build-verify (Tasks 7 + 8 together now compile)**

Run: `./gradlew :app:assembleDebug`
Expected: BUILD SUCCESSFUL.

- [ ] **Step 6: Commit Tasks 7 + 8**

```bash
git add app/src/main/java/com/artha/kirana/data/db/entity/KhataEntity.kt app/src/main/java/com/artha/kirana/data/db/dao/KhataDao.kt app/src/main/java/com/artha/kirana/data/repository/KhataRepositoryImpl.kt app/src/test/java/com/artha/kirana/data/repository/KhataRepositoryImplTest.kt
git commit -m "feat(customers): khata keyed by customerId; adjust resolves customer"
```

---

### Task 9: `LogSaleUseCase` — resolve customer + snapshot prices (TDD)

**Files:**
- Modify: `app/src/main/java/com/artha/kirana/domain/usecase/LogSaleUseCase.kt`
- Test: `app/src/test/java/com/artha/kirana/domain/usecase/LogSaleUseCaseTest.kt`

- [ ] **Step 1: Update the test (inject CustomerRepository; assert customerId + snapshots)**

```kotlin
// app/src/test/java/com/artha/kirana/domain/usecase/LogSaleUseCaseTest.kt
package com.artha.kirana.domain.usecase

import com.artha.kirana.data.db.entity.ItemEntity
import com.artha.kirana.domain.model.SaleEntry
import com.artha.kirana.domain.repository.CustomerRepository
import com.artha.kirana.domain.repository.InventoryRepository
import com.artha.kirana.domain.repository.KhataRepository
import com.artha.kirana.domain.repository.SalesRepository
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.mockk
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Test

class LogSaleUseCaseTest {

    private val sales = mockk<SalesRepository>(relaxed = true)
    private val inventory = mockk<InventoryRepository>(relaxed = true)
    private val khata = mockk<KhataRepository>(relaxed = true)
    private val customers = mockk<CustomerRepository>(relaxed = true)
    private val useCase = LogSaleUseCase(sales, inventory, khata, customers)

    @Test
    fun creditSaleSnapshotsPriceLinksCustomerAndAppliesCredit() = runTest {
        val item = ItemEntity(id = 7, name = "rice", qtyInStock = 10.0, sellPrice = 40.0, costPrice = 30.0)
        coEvery { inventory.findByName("rice") } returns item
        coEvery { customers.resolveOrCreate("Ramesh") } returns 3L
        coEvery { sales.logSale(any()) } returns 42L

        val entry = SaleEntry(item = "rice", qty = "2 kg", amount = 80.0, type = "credit", party = "Ramesh")
        val id = useCase(entry, inputMethod = "typed", rawInput = "raw")

        assertEquals(42L, id)
        coVerify(exactly = 1) {
            sales.logSale(
                match {
                    it.type == "credit" && it.amount == 80.0 && it.party == "Ramesh" &&
                        it.customerId == 3L && it.itemId == 7L && it.itemName == "rice" &&
                        it.qtySold == 2.0 && it.unitPrice == 40.0 && it.unitCost == 30.0 &&
                        it.inputMethod == "typed"
                },
            )
        }
        coVerify(exactly = 1) { inventory.decrementStock(7L, 2.0) }
        coVerify(exactly = 1) { khata.applyCredit("Ramesh", 80.0, 42L) }
    }

    @Test
    fun anonymousCashSaleHasNoCustomerId() = runTest {
        coEvery { inventory.findByName(any()) } returns null
        coEvery { sales.logSale(any()) } returns 1L

        val entry = SaleEntry(item = null, qty = null, amount = 50.0, type = "cash", party = null)
        useCase(entry, inputMethod = "typed", rawInput = null)

        coVerify(exactly = 0) { customers.resolveOrCreate(any()) }
        coVerify(exactly = 1) { sales.logSale(match { it.customerId == null && it.unitPrice == null }) }
        coVerify(exactly = 0) { inventory.decrementStock(any(), any()) }
        coVerify(exactly = 0) { khata.applyCredit(any(), any(), any()) }
    }

    @Test
    fun repaymentAppliesRepayment() = runTest {
        coEvery { inventory.findByName(any()) } returns null
        coEvery { customers.resolveOrCreate("Ramesh") } returns 4L
        coEvery { sales.logSale(any()) } returns 9L

        val entry = SaleEntry(item = null, qty = null, amount = 50.0, type = "repayment", party = "Ramesh")
        useCase(entry, inputMethod = "typed", rawInput = null)

        coVerify(exactly = 1) { khata.applyRepayment("Ramesh", 50.0, 9L) }
    }
}
```

- [ ] **Step 2: Run test to verify it fails**

Run: `./gradlew :app:testDebugUnitTest --tests "com.artha.kirana.domain.usecase.LogSaleUseCaseTest"`
Expected: FAIL — constructor takes 3 args, not 4.

- [ ] **Step 3: Update `LogSaleUseCase`**

```kotlin
// app/src/main/java/com/artha/kirana/domain/usecase/LogSaleUseCase.kt
package com.artha.kirana.domain.usecase

import com.artha.kirana.data.db.entity.SaleEntity
import com.artha.kirana.domain.model.SaleEntry
import com.artha.kirana.domain.repository.CustomerRepository
import com.artha.kirana.domain.repository.InventoryRepository
import com.artha.kirana.domain.repository.KhataRepository
import com.artha.kirana.domain.repository.SalesRepository
import javax.inject.Inject

/**
 * Persists a parsed [SaleEntry]: resolves the named customer (if any), snapshots the item's
 * unit price/cost, writes the sale, decrements stock when the item is known, and updates the
 * party's khata balance for credit/repayment. Returns the new sale's row id.
 */
class LogSaleUseCase @Inject constructor(
    private val sales: SalesRepository,
    private val inventory: InventoryRepository,
    private val khata: KhataRepository,
    private val customers: CustomerRepository,
) {
    suspend operator fun invoke(
        entry: SaleEntry,
        inputMethod: String,
        rawInput: String?,
    ): Long {
        val item = entry.item?.let { inventory.findByName(it) }
        val qty = parseLeadingQty(entry.qty)
        val customerId = entry.party?.takeIf { it.isNotBlank() }?.let { customers.resolveOrCreate(it) }

        val saleId = sales.logSale(
            SaleEntity(
                itemId = item?.id,
                itemName = entry.item,
                customerId = customerId,
                qtySold = qty,
                amount = entry.amount ?: 0.0,
                unitPrice = item?.sellPrice,
                unitCost = item?.costPrice,
                type = entry.type,
                party = entry.party,
                inputMethod = inputMethod,
                rawInput = rawInput,
            ),
        )

        if (item != null && qty > 0.0) {
            inventory.decrementStock(item.id, qty)
        }

        when (entry.type) {
            "credit" -> entry.party?.let { khata.applyCredit(it, entry.amount ?: 0.0, saleId) }
            "repayment" -> entry.party?.let { khata.applyRepayment(it, entry.amount ?: 0.0, saleId) }
        }

        return saleId
    }
}
```

- [ ] **Step 4: Run test + suite**

Run: `./gradlew :app:testDebugUnitTest --tests "com.artha.kirana.domain.usecase.LogSaleUseCaseTest"` → PASS
Run: `./gradlew :app:testDebugUnitTest` → PASS (full suite)

- [ ] **Step 5: Commit**

```bash
git add app/src/main/java/com/artha/kirana/domain/usecase/LogSaleUseCase.kt app/src/test/java/com/artha/kirana/domain/usecase/LogSaleUseCaseTest.kt
git commit -m "feat(customers): LogSaleUseCase resolves customerId and snapshots unitPrice/unitCost"
```

---

### Task 10: `EditSaleUseCase` — resolve customer + re-snapshot prices (TDD)

**Files:**
- Modify: `app/src/main/java/com/artha/kirana/domain/usecase/EditSaleUseCase.kt`
- Test: `app/src/test/java/com/artha/kirana/domain/usecase/EditSaleUseCaseTest.kt`

- [ ] **Step 1: Replace the test file** (adds the `customers` mock + 4th ctor arg; the item-swap test now asserts re-snapshotted prices, and the party-change test stubs the resolver)

```kotlin
// app/src/test/java/com/artha/kirana/domain/usecase/EditSaleUseCaseTest.kt
package com.artha.kirana.domain.usecase

import com.artha.kirana.data.db.entity.ItemEntity
import com.artha.kirana.data.db.entity.SaleEntity
import com.artha.kirana.domain.model.SaleEntry
import com.artha.kirana.domain.repository.CustomerRepository
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
    private val customers = mockk<CustomerRepository>(relaxed = true)
    private val useCase = EditSaleUseCase(sales, inventory, khata, customers)

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
            sales.updateSale(match { it.id == 5L && it.type == "cash" && it.party == null && it.customerId == null && it.timestamp == 111L })
        }
    }

    @Test
    fun itemSwapReSnapshotsPriceAndAdjustsStock() = runTest {
        val sugar = ItemEntity(id = 2, name = "sugar", qtyInStock = 5.0, sellPrice = 30.0, costPrice = 22.0)
        coEvery { inventory.findByName("sugar") } returns sugar
        val old = SaleEntity(
            id = 7, itemId = 1, itemName = "rice", qtySold = 2.0, amount = 80.0,
            type = "cash", party = null, inputMethod = "typed", rawInput = null, timestamp = 0L,
        )
        val edited = SaleEntry(item = "sugar", qty = "3", amount = 90.0, type = "cash", party = null)

        useCase(old, edited)

        coVerify(exactly = 1) { inventory.incrementStock(1, 2.0) }
        coVerify(exactly = 1) { inventory.decrementStock(2, 3.0) }
        coVerify(exactly = 1) { khata.reverseSaleEffect(7) }
        coVerify(exactly = 1) {
            sales.updateSale(match {
                it.itemId == 2L && it.itemName == "sugar" && it.qtySold == 3.0 &&
                    it.unitPrice == 30.0 && it.unitCost == 22.0
            })
        }
    }

    @Test
    fun partyAndAmountChangeOnCreditLinksNewCustomerAndAppliesCredit() = runTest {
        coEvery { inventory.findByName(any()) } returns null
        coEvery { customers.resolveOrCreate("Priya") } returns 8L
        val old = SaleEntity(
            id = 9, itemId = null, itemName = null, qtySold = 0.0, amount = 80.0,
            type = "credit", party = "Ramesh", inputMethod = "typed", rawInput = null, timestamp = 0L,
        )
        val edited = SaleEntry(item = null, qty = null, amount = 50.0, type = "credit", party = "Priya")

        useCase(old, edited)

        coVerify(exactly = 1) { khata.reverseSaleEffect(9) }
        coVerify(exactly = 1) { khata.applyCredit("Priya", 50.0, 9) }
        coVerify(exactly = 1) { sales.updateSale(match { it.customerId == 8L && it.party == "Priya" }) }
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

- [ ] **Step 2: Run test to verify it fails**

Run: `./gradlew :app:testDebugUnitTest --tests "com.artha.kirana.domain.usecase.EditSaleUseCaseTest"`
Expected: FAIL — constructor arity / missing `customerId` on the updated row.

- [ ] **Step 4: Update `EditSaleUseCase`**

```kotlin
// app/src/main/java/com/artha/kirana/domain/usecase/EditSaleUseCase.kt
package com.artha.kirana.domain.usecase

import com.artha.kirana.data.db.entity.SaleEntity
import com.artha.kirana.domain.model.SaleEntry
import com.artha.kirana.domain.repository.CustomerRepository
import com.artha.kirana.domain.repository.InventoryRepository
import com.artha.kirana.domain.repository.KhataRepository
import com.artha.kirana.domain.repository.SalesRepository
import javax.inject.Inject

/**
 * Edits a saved sale in place: reverses the original entry's inventory + khata effects, then
 * applies the edited entry's effects — re-resolving the customer and re-snapshotting unit
 * price/cost from the (possibly changed) item. Keeps the sale's id, timestamp, inputMethod,
 * rawInput. The §18-stable add path ([LogSaleUseCase]) is untouched.
 */
class EditSaleUseCase @Inject constructor(
    private val sales: SalesRepository,
    private val inventory: InventoryRepository,
    private val khata: KhataRepository,
    private val customers: CustomerRepository,
) {
    suspend operator fun invoke(old: SaleEntity, edited: SaleEntry) {
        // 1. reverse old stock
        if (old.itemId != null && old.qtySold > 0.0) {
            inventory.incrementStock(old.itemId, old.qtySold)
        }
        // 2. reverse old khata (clean rewrite)
        khata.reverseSaleEffect(old.id)

        // 3. resolve new item + qty + customer
        val newItem = edited.item?.let { inventory.findByName(it) }
        val newQty = parseLeadingQty(edited.qty)
        val customerId = edited.party?.takeIf { it.isNotBlank() }?.let { customers.resolveOrCreate(it) }

        // 4. update the row in place (id, timestamp, inputMethod, rawInput preserved by copy)
        sales.updateSale(
            old.copy(
                itemId = newItem?.id,
                itemName = edited.item,
                customerId = customerId,
                qtySold = newQty,
                amount = edited.amount ?: 0.0,
                unitPrice = newItem?.sellPrice,
                unitCost = newItem?.costPrice,
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

- [ ] **Step 5: Run test + full suite + build**

Run: `./gradlew :app:testDebugUnitTest` → PASS
Run: `./gradlew :app:assembleDebug` → BUILD SUCCESSFUL

- [ ] **Step 6: Commit**

```bash
git add app/src/main/java/com/artha/kirana/domain/usecase/EditSaleUseCase.kt app/src/test/java/com/artha/kirana/domain/usecase/EditSaleUseCaseTest.kt
git commit -m "feat(customers): EditSaleUseCase re-resolves customer and re-snapshots prices"
```

---

### Task 11: On-device verification of the v3 schema (manual gate)

**No code.** Destructive migration wiped dev data — re-enter by hand.

- [ ] **Step 1:** `./gradlew :app:installDebug` (first launch recreates the DB at v3). Start llama-server.
- [ ] **Step 2:** Add item `chawal` (sellPrice 40, costPrice 30). Log a **cash** sale "do kilo chawal" → ₹80, Home revenue updates.
- [ ] **Step 3:** Log a **credit** sale to "Ramesh" → Khata shows Ramesh owes the amount; the sale row links (party "Ramesh").
- [ ] **Step 4:** Record a **repayment** from Ramesh → balance drops; Khata detail shows the green row.
- [ ] **Step 5:** Edit a recent entry (amount + qty) from Home → revenue/P&L + stock reconcile (the 4 edit exit criteria from STATUS).
- [ ] **Step 6:** Assistant tab — `log_sale`, `record_payment`, `query_pnl` all still work.
- [ ] **Step 7:** Update `docs/STATUS.md` + `CLAUDE.md §4` (note DB v3: customers table, sales.customerId/unitPrice/unitCost, khata.customerId) and commit.

```bash
git add docs/STATUS.md CLAUDE.md
git commit -m "docs: DB v3 (customers + price snapshots) device-verified"
```

---

## Part 3 — Feature E: Analytics queries + use-cases (branch off `main` / continue on `feat/data-layer-v3`)

> DAO SQL is validated by the Room compiler and exercised on-device; pure Kotlin logic (day-of-week bucketing) is unit-tested. Each use-case returns data only — wiring into P&L/Insights screens is a separate follow-up (out of scope).

### Task 12: Top sellers by volume/revenue

**Files:**
- Modify: `app/src/main/java/com/artha/kirana/data/db/dao/SalesDao.kt`
- Create: `app/src/main/java/com/artha/kirana/domain/model/TopSellerRow.kt`
- Modify: `app/src/main/java/com/artha/kirana/domain/repository/SalesRepository.kt` + `app/src/main/java/com/artha/kirana/data/repository/SalesRepositoryImpl.kt`
- Create: `app/src/main/java/com/artha/kirana/domain/usecase/GetTopSellersUseCase.kt`

- [ ] **Step 1: Add the projection model**

```kotlin
// app/src/main/java/com/artha/kirana/domain/model/TopSellerRow.kt
package com.artha.kirana.domain.model

/** One item's aggregate sales over a period (excludes repayments). */
data class TopSellerRow(
    val itemId: Long?,
    val itemName: String?,
    val qty: Double,
    val revenue: Double,
)
```

- [ ] **Step 2: Add the DAO query** (append to `SalesDao`, import `TopSellerRow`):

```kotlin
    @Query(
        "SELECT itemId, itemName, COALESCE(SUM(qtySold),0) AS qty, COALESCE(SUM(amount),0) AS revenue " +
            "FROM sales WHERE type != 'repayment' AND timestamp BETWEEN :start AND :end " +
            "GROUP BY itemId ORDER BY revenue DESC",
    )
    suspend fun topSellers(start: Long, end: Long): List<TopSellerRow>
```

- [ ] **Step 3: Expose via repository** — add to `SalesRepository`: `suspend fun topSellers(start: Long, end: Long): List<TopSellerRow>`; implement in `SalesRepositoryImpl` delegating to `dao.topSellers(start, end)` (import `TopSellerRow`).

- [ ] **Step 4: Add the use-case**

```kotlin
// app/src/main/java/com/artha/kirana/domain/usecase/GetTopSellersUseCase.kt
package com.artha.kirana.domain.usecase

import com.artha.kirana.domain.model.TopSellerRow
import com.artha.kirana.domain.repository.SalesRepository
import javax.inject.Inject

class GetTopSellersUseCase @Inject constructor(
    private val sales: SalesRepository,
) {
    suspend operator fun invoke(start: Long, end: Long): List<TopSellerRow> =
        sales.topSellers(start, end)
}
```

- [ ] **Step 5: Build-verify**

Run: `./gradlew :app:assembleDebug`
Expected: BUILD SUCCESSFUL (Room compiles the aggregate query into `TopSellerRow`).

- [ ] **Step 6: Commit**

```bash
git add app/src/main/java/com/artha/kirana/domain/model/TopSellerRow.kt app/src/main/java/com/artha/kirana/data/db/dao/SalesDao.kt app/src/main/java/com/artha/kirana/domain/repository/SalesRepository.kt app/src/main/java/com/artha/kirana/data/repository/SalesRepositoryImpl.kt app/src/main/java/com/artha/kirana/domain/usecase/GetTopSellersUseCase.kt
git commit -m "feat(analytics): top sellers by volume/revenue"
```

---

### Task 13: Margin by item (uses snapshots)

**Files:**
- Modify: `app/src/main/java/com/artha/kirana/data/db/dao/SalesDao.kt`
- Create: `app/src/main/java/com/artha/kirana/domain/model/ItemMarginRow.kt`
- Modify: `SalesRepository` + `SalesRepositoryImpl`
- Create: `app/src/main/java/com/artha/kirana/domain/usecase/GetItemMarginsUseCase.kt`

- [ ] **Step 1: Add the projection model**

```kotlin
// app/src/main/java/com/artha/kirana/domain/model/ItemMarginRow.kt
package com.artha.kirana.domain.model

/** One item's margin (sellPrice−costPrice snapshots × qty) and revenue over a period. */
data class ItemMarginRow(
    val itemId: Long?,
    val itemName: String?,
    val margin: Double,
    val revenue: Double,
)
```

- [ ] **Step 2: Add the DAO query** (append to `SalesDao`, import `ItemMarginRow`):

```kotlin
    @Query(
        "SELECT itemId, itemName, " +
            "COALESCE(SUM((unitPrice - unitCost) * qtySold),0) AS margin, " +
            "COALESCE(SUM(amount),0) AS revenue " +
            "FROM sales WHERE type != 'repayment' AND unitPrice IS NOT NULL AND unitCost IS NOT NULL " +
            "AND timestamp BETWEEN :start AND :end " +
            "GROUP BY itemId ORDER BY margin ASC",
    )
    suspend fun itemMargins(start: Long, end: Long): List<ItemMarginRow>
```

- [ ] **Step 3: Expose via repository** — add `suspend fun itemMargins(start: Long, end: Long): List<ItemMarginRow>` to `SalesRepository` + impl.

- [ ] **Step 4: Add the use-case**

```kotlin
// app/src/main/java/com/artha/kirana/domain/usecase/GetItemMarginsUseCase.kt
package com.artha.kirana.domain.usecase

import com.artha.kirana.domain.model.ItemMarginRow
import com.artha.kirana.domain.repository.SalesRepository
import javax.inject.Inject

class GetItemMarginsUseCase @Inject constructor(
    private val sales: SalesRepository,
) {
    suspend operator fun invoke(start: Long, end: Long): List<ItemMarginRow> =
        sales.itemMargins(start, end)
}
```

- [ ] **Step 5: Build-verify + commit**

Run: `./gradlew :app:assembleDebug` → BUILD SUCCESSFUL

```bash
git add app/src/main/java/com/artha/kirana/domain/model/ItemMarginRow.kt app/src/main/java/com/artha/kirana/data/db/dao/SalesDao.kt app/src/main/java/com/artha/kirana/domain/repository/SalesRepository.kt app/src/main/java/com/artha/kirana/data/repository/SalesRepositoryImpl.kt app/src/main/java/com/artha/kirana/domain/usecase/GetItemMarginsUseCase.kt
git commit -m "feat(analytics): margin-by-item from price snapshots"
```

---

### Task 14: Per-customer history, outstanding & lifetime value

**Files:**
- Modify: `app/src/main/java/com/artha/kirana/data/db/dao/SalesDao.kt`
- Modify: `app/src/main/java/com/artha/kirana/data/db/dao/KhataDao.kt`
- Modify: `SalesRepository` + `SalesRepositoryImpl`, `KhataRepository` + `KhataRepositoryImpl`
- Create: `app/src/main/java/com/artha/kirana/domain/usecase/GetCustomerSummaryUseCase.kt`
- Create: `app/src/main/java/com/artha/kirana/domain/model/CustomerSummary.kt`

- [ ] **Step 1: Add DAO queries**

Append to `SalesDao`:
```kotlin
    @Query("SELECT * FROM sales WHERE customerId = :customerId ORDER BY timestamp DESC")
    fun observeForCustomer(customerId: Long): Flow<List<SaleEntity>>

    @Query("SELECT COALESCE(SUM(amount),0) FROM sales WHERE customerId = :customerId AND type != 'repayment'")
    suspend fun lifetimeValue(customerId: Long): Double
```
Append to `KhataDao`:
```kotlin
    @Query("SELECT COALESCE(balance, 0) FROM khata WHERE customerId = :customerId")
    suspend fun balanceForCustomer(customerId: Long): Double
```

- [ ] **Step 2: Add the summary model**

```kotlin
// app/src/main/java/com/artha/kirana/domain/model/CustomerSummary.kt
package com.artha.kirana.domain.model

/** Aggregate view of one customer for analytics. */
data class CustomerSummary(
    val customerId: Long,
    val lifetimeValue: Double,   // sum of non-repayment sale amounts
    val outstanding: Double,     // current khata balance (positive = owes us)
)
```

- [ ] **Step 3: Expose via repositories** — `SalesRepository`: `fun observeForCustomer(customerId: Long): Flow<List<SaleEntity>>` and `suspend fun lifetimeValue(customerId: Long): Double`. `KhataRepository`: `suspend fun balanceForCustomer(customerId: Long): Double`. Implement each by delegating to the DAO.

- [ ] **Step 4: Add the use-case**

```kotlin
// app/src/main/java/com/artha/kirana/domain/usecase/GetCustomerSummaryUseCase.kt
package com.artha.kirana.domain.usecase

import com.artha.kirana.domain.model.CustomerSummary
import com.artha.kirana.domain.repository.KhataRepository
import com.artha.kirana.domain.repository.SalesRepository
import javax.inject.Inject

class GetCustomerSummaryUseCase @Inject constructor(
    private val sales: SalesRepository,
    private val khata: KhataRepository,
) {
    suspend operator fun invoke(customerId: Long): CustomerSummary =
        CustomerSummary(
            customerId = customerId,
            lifetimeValue = sales.lifetimeValue(customerId),
            outstanding = khata.balanceForCustomer(customerId),
        )
}
```

- [ ] **Step 5: Build-verify + commit**

Run: `./gradlew :app:assembleDebug` → BUILD SUCCESSFUL

```bash
git add app/src/main/java/com/artha/kirana/domain/model/CustomerSummary.kt app/src/main/java/com/artha/kirana/domain/usecase/GetCustomerSummaryUseCase.kt app/src/main/java/com/artha/kirana/data/db/dao/SalesDao.kt app/src/main/java/com/artha/kirana/data/db/dao/KhataDao.kt app/src/main/java/com/artha/kirana/domain/repository/SalesRepository.kt app/src/main/java/com/artha/kirana/data/repository/SalesRepositoryImpl.kt app/src/main/java/com/artha/kirana/domain/repository/KhataRepository.kt app/src/main/java/com/artha/kirana/data/repository/KhataRepositoryImpl.kt
git commit -m "feat(analytics): per-customer history, outstanding & lifetime value"
```

---

### Task 15: Day-of-week revenue trends (pure bucketing, TDD)

**Files:**
- Create: `app/src/main/java/com/artha/kirana/domain/usecase/DayOfWeekBucketing.kt`
- Create: `app/src/main/java/com/artha/kirana/domain/usecase/GetDayOfWeekTrendUseCase.kt`
- Test: `app/src/test/java/com/artha/kirana/domain/usecase/DayOfWeekBucketingTest.kt`

- [ ] **Step 1: Write the failing test**

```kotlin
// app/src/test/java/com/artha/kirana/domain/usecase/DayOfWeekBucketingTest.kt
package com.artha.kirana.domain.usecase

import com.artha.kirana.data.db.entity.SaleEntity
import org.junit.Assert.assertEquals
import org.junit.Test
import java.util.Calendar
import java.util.GregorianCalendar

class DayOfWeekBucketingTest {

    // Build a timestamp for a known weekday at noon local time.
    private fun tsOn(year: Int, month0: Int, day: Int): Long =
        GregorianCalendar(year, month0, day, 12, 0, 0).timeInMillis

    private fun sale(ts: Long, amount: Double, type: String = "cash") =
        SaleEntity(amount = amount, type = type, inputMethod = "typed", timestamp = ts)

    @Test
    fun bucketsRevenueByWeekdaySundayFirstAndExcludesRepayments() {
        // 2024-06-14 is a Friday (Calendar.FRIDAY = 6), 2024-06-16 is a Sunday (1).
        val friday = tsOn(2024, Calendar.JUNE, 14)
        val sunday = tsOn(2024, Calendar.JUNE, 16)
        val sales = listOf(
            sale(friday, 100.0),
            sale(friday, 50.0),
            sale(sunday, 30.0),
            sale(friday, 999.0, type = "repayment"), // excluded
        )

        val buckets = bucketRevenueByWeekday(sales)

        assertEquals(7, buckets.size)
        assertEquals(30.0, buckets[0], 0.001)   // index 0 = Sunday
        assertEquals(150.0, buckets[5], 0.001)  // index 5 = Friday
        assertEquals(0.0, buckets[1], 0.001)    // Monday empty
    }
}
```

- [ ] **Step 2: Run test to verify it fails**

Run: `./gradlew :app:testDebugUnitTest --tests "com.artha.kirana.domain.usecase.DayOfWeekBucketingTest"`
Expected: FAIL — `Unresolved reference: bucketRevenueByWeekday`.

- [ ] **Step 3: Implement the pure bucketing**

```kotlin
// app/src/main/java/com/artha/kirana/domain/usecase/DayOfWeekBucketing.kt
package com.artha.kirana.domain.usecase

import com.artha.kirana.data.db.entity.SaleEntity
import java.util.Calendar

/**
 * Sums non-repayment sale amounts into 7 weekday buckets. Index 0 = Sunday … 6 = Saturday
 * (matching Calendar.DAY_OF_WEEK − 1), using the device's default timezone — consistent with
 * the rest of the app's time handling (see RevenueBucketing).
 */
internal fun bucketRevenueByWeekday(sales: List<SaleEntity>): DoubleArray {
    val buckets = DoubleArray(7)
    val cal = Calendar.getInstance()
    for (s in sales) {
        if (s.type == "repayment") continue
        cal.timeInMillis = s.timestamp
        val idx = cal.get(Calendar.DAY_OF_WEEK) - 1 // SUNDAY(1)->0 … SATURDAY(7)->6
        buckets[idx] += s.amount
    }
    return buckets
}
```

- [ ] **Step 4: Run test to verify it passes**

Run: `./gradlew :app:testDebugUnitTest --tests "com.artha.kirana.domain.usecase.DayOfWeekBucketingTest"`
Expected: PASS.

- [ ] **Step 5: Add the use-case that feeds it from Room**

```kotlin
// app/src/main/java/com/artha/kirana/domain/usecase/GetDayOfWeekTrendUseCase.kt
package com.artha.kirana.domain.usecase

import com.artha.kirana.domain.repository.SalesRepository
import javax.inject.Inject

/**
 * Revenue summed into 7 weekday buckets (index 0 = Sunday) over [start, end].
 * Reuses [bucketRevenueByWeekday]; SalesRepository.between already returns the window.
 */
class GetDayOfWeekTrendUseCase @Inject constructor(
    private val sales: SalesRepository,
) {
    suspend operator fun invoke(start: Long, end: Long): DoubleArray =
        bucketRevenueByWeekday(sales.between(start, end))
}
```

- [ ] **Step 6: Expose `between` on the repository if not already present**

`SalesDao.between(start, end): List<SaleEntity>` already exists. Add to `SalesRepository`: `suspend fun between(start: Long, end: Long): List<SaleEntity>` and implement in `SalesRepositoryImpl` delegating to `dao.between(start, end)`. (Skip if already exposed.)

- [ ] **Step 7: Run suite + build**

Run: `./gradlew :app:testDebugUnitTest` → PASS
Run: `./gradlew :app:assembleDebug` → BUILD SUCCESSFUL

- [ ] **Step 8: Commit**

```bash
git add app/src/main/java/com/artha/kirana/domain/usecase/DayOfWeekBucketing.kt app/src/main/java/com/artha/kirana/domain/usecase/GetDayOfWeekTrendUseCase.kt app/src/test/java/com/artha/kirana/domain/usecase/DayOfWeekBucketingTest.kt app/src/main/java/com/artha/kirana/domain/repository/SalesRepository.kt app/src/main/java/com/artha/kirana/data/repository/SalesRepositoryImpl.kt
git commit -m "feat(analytics): day-of-week revenue bucketing + use-case"
```

---

### Task 16: Final verification + docs

- [ ] **Step 1: Full suite + build**

Run: `./gradlew :app:testDebugUnitTest` → all green
Run: `./gradlew :app:assembleDebug` → BUILD SUCCESSFUL

- [ ] **Step 2: Update `docs/STATUS.md`** — mark the data-layer restructure (A–E) done; note DB v3 schema and the four analytics use-cases (DAO/use-case building blocks; UI surfacing is a follow-up).

- [ ] **Step 3: Update `CLAUDE.md §4`** — reflect v3: `customers` table; `sales.customerId/unitPrice/unitCost`; `khata.customerId`.

- [ ] **Step 4: Commit docs**

```bash
git add docs/STATUS.md CLAUDE.md
git commit -m "docs: data-layer restructure (A–E) complete; schema v3 + analytics use-cases"
```

- [ ] **Step 5: Finish the branch** — use superpowers:finishing-a-development-branch to decide merge/PR.

---

## Self-review notes (for the executor)

- **Spec coverage:** A=Tasks 1–3; B (indices/FK)=Tasks 6–7; C (customers)=Tasks 4–5, 7–10; snapshot=Tasks 6, 9–10; E (all four analytics)=Tasks 12–15. Merge checkpoint between A and B/C per the spec's branch decision.
- **Tasks 7 + 8 share one green build** (the entity change breaks `KhataRepositoryImpl` until the repo is updated) — that is intentional and called out; commit them together.
- **`KhataRepository` public API stays name-based** so `KhataScreen` record-payment and `RouteAssistantUseCase` payment path need no change; the double `resolveOrCreate` (LogSale + khata.adjust) is harmless because it is idempotent.
- **Type consistency:** `CustomerRepository.resolveOrCreate(name): Long`, `KhataDao.findByCustomerId(customerId): KhataEntity?`, projection rows (`TopSellerRow`, `ItemMarginRow`, `CustomerSummary`) all match between their defining task and their consumers.
- **No DI module change for auto-price** (Task 2): `InventoryRepository` is already bound; only `CustomerRepository` needs a new `@Binds` (Task 5).

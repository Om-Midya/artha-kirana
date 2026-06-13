# Phase 2 — Inventory · Khata · P&L Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Complete the shop-management loop — real Inventory, Khata, and P&L screens, a 7-day revenue chart, and a background low-stock alert that auto-clears on restock.

**Architecture:** Entity-direct Compose UI (no domain-model mapping layer; domain models only for computed P&L values). P&L headline numbers are SQL `SUM` flows `combine`d in a use-case; the 7-day chart buckets sales by local day in pure Kotlin (TDD'd). The low-stock worker is a Hilt `@HiltWorker` driven by WorkManager. All reads are Room `Flow`s surfaced via `stateIn` + `collectAsStateWithLifecycle`.

**Tech Stack:** Kotlin, Jetpack Compose + Material3, Room 2.6.1, Hilt 2.52, Navigation Compose, WorkManager 2.9.1 + `androidx.hilt:hilt-work`, Vico 2.1.x (charts). JUnit4 + MockK + kotlinx-coroutines-test for unit tests.

**Spec:** `docs/superpowers/specs/2026-06-13-artha-phase2-design.md`. Where it disagrees with `CLAUDE.md`, the spec wins.

**Conventions reused from Phase 1 (do not deviate):**
- ViewModels: `@HiltViewModel`, expose `StateFlow` via `stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), <initial>)`.
- Composables: `collectAsStateWithLifecycle()`, `hiltViewModel()`, Material3 `Card`/`LazyColumn`.
- Tests mirror `app/src/test/java/com/artha/kirana/domain/usecase/LogSaleUseCaseTest.kt` (MockK + `runTest`).
- Run unit tests with: `./gradlew :app:testDebugUnitTest`. Build/install with: `./gradlew :app:installDebug`.
- Commit per task. Branch: `feat/phase0-foundation`.

---

## File Structure

| File | Responsibility | Action |
|---|---|---|
| `gradle/libs.versions.toml` | Vico 2.1.x + androidx hilt-work deps | Modify |
| `app/build.gradle.kts` | Wire new deps | Modify |
| `util/TimeRange.kt` | week/month starts + `last7DayBuckets` + `DayBucket` | Modify |
| `domain/model/PnlSummary.kt` | `PnlSummary`, `PnlPeriod`, `DailyRevenue` | Create |
| `domain/usecase/RevenueBucketing.kt` | pure `bucketDailyRevenue()` | Create |
| `data/db/dao/SalesDao.kt` | `cogsBetween` | Modify |
| `data/db/dao/ItemsDao.kt` | `getAllOnce` | Modify |
| `data/db/dao/KhataDao.kt` | `observeById` | Modify |
| `domain/repository/SalesRepository.kt` + impl | `cashBetween`, `cogsBetween` | Modify |
| `domain/repository/InventoryRepository.kt` + impl | `updateItem` | Modify |
| `domain/repository/KhataRepository.kt` + impl | `observeParty`, `observeTransactions` | Modify |
| `domain/usecase/GetPnlSummaryUseCase.kt` | combine flows → `PnlSummary` | Create |
| `domain/usecase/GetDailyRevenueUseCase.kt` | sales → `List<DailyRevenue>` | Create |
| `ui/inventory/InventoryViewModel.kt` + `InventoryScreen.kt` + `ItemCard.kt` + `AddItemSheet.kt` | Inventory feature | Create/Modify |
| `ui/khata/KhataViewModel.kt` + `KhataScreen.kt` + `KhataPartyDetail.kt` | Khata feature | Create/Modify |
| `ui/pnl/PnlViewModel.kt` + `PnlScreen.kt` + `ProfitChart.kt` | P&L feature | Create/Modify |
| `data/notification/NotificationHelper.kt` | channel + post/cancel | Create |
| `data/worker/InventoryAlertWorker.kt` | `@HiltWorker` low-stock check | Create |
| `ArthaApplication.kt` | `Configuration.Provider` + schedule worker | Modify |
| `AndroidManifest.xml` | remove default WorkManager initializer | Modify |
| `ui/ArthaApp.kt` | real screens + `khata/{partyId}` route + POST_NOTIFICATIONS request | Modify |

---

## Task 1: Add dependencies (Vico 2.1.x, androidx hilt-work)

**Files:**
- Modify: `gradle/libs.versions.toml`
- Modify: `app/build.gradle.kts:80-82`

- [ ] **Step 1: Bump Vico + add versions in the catalog**

In `gradle/libs.versions.toml`, in `[versions]`, change `vico` and add `androidxHilt`:

```toml
vico = "2.1.3"
androidxHilt = "1.2.0"
```

- [ ] **Step 2: Add the library coordinates**

In `[libraries]`, replace the existing `vico-compose-m3` line and add three new lines:

```toml
vico-compose = { group = "com.patrykandpatrick.vico", name = "compose", version.ref = "vico" }
vico-compose-m3 = { group = "com.patrykandpatrick.vico", name = "compose-m3", version.ref = "vico" }
androidx-hilt-work = { group = "androidx.hilt", name = "hilt-work", version.ref = "androidxHilt" }
androidx-hilt-compiler = { group = "androidx.hilt", name = "hilt-compiler", version.ref = "androidxHilt" }
```

- [ ] **Step 3: Wire deps in `app/build.gradle.kts`**

Replace lines 80-82 (the commented-out Vico block + the work-runtime line) with:

```kotlin
    implementation(libs.vico.compose)
    implementation(libs.vico.compose.m3)
    implementation(libs.androidx.work.runtime.ktx)
    implementation(libs.androidx.hilt.work)
    ksp(libs.androidx.hilt.compiler)
```

- [ ] **Step 4: Sync + build to verify resolution**

Run: `./gradlew :app:assembleDebug`
Expected: BUILD SUCCESSFUL. If Vico `2.1.3` fails to resolve or trips a Kotlin-metadata error, fall back to `vico = "2.0.2"` and re-run (both are Kotlin-2.0 line). Do NOT go to 3.x (needs Kotlin 2.3).

- [ ] **Step 5: Commit**

```bash
git add gradle/libs.versions.toml app/build.gradle.kts
git commit -m "build: add Vico 2.1.x charts + androidx hilt-work for Phase 2"
```

---

## Task 2: TimeRange — week/month starts + 7-day buckets (TDD)

**Files:**
- Modify: `app/src/main/java/com/artha/kirana/util/TimeRange.kt`
- Test: `app/src/test/java/com/artha/kirana/util/TimeRangeTest.kt`

- [ ] **Step 1: Write the failing test**

Create `app/src/test/java/com/artha/kirana/util/TimeRangeTest.kt`:

```kotlin
package com.artha.kirana.util

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.util.Calendar

class TimeRangeTest {

    private fun at(year: Int, month0: Int, day: Int, hour: Int, min: Int): Long =
        Calendar.getInstance().apply {
            set(year, month0, day, hour, min, 30)
            set(Calendar.MILLISECOND, 0)
        }.timeInMillis

    private val oneDay = 24L * 60 * 60 * 1000

    @Test
    fun startOfWeekIsMidnightOnOrBeforeToday_withinSevenDays() {
        val now = at(2026, Calendar.JUNE, 17, 14, 5) // a Wednesday
        val wk = TimeRange.startOfWeek(now)
        val today = TimeRange.startOfToday(now)
        assertTrue("week start <= today start", wk <= today)
        assertTrue("within 7 days", today - wk < 7 * oneDay)
        // midnight: equals startOfToday of itself
        assertEquals(wk, TimeRange.startOfToday(wk))
    }

    @Test
    fun startOfMonthIsFirstDayMidnight() {
        val now = at(2026, Calendar.JUNE, 17, 14, 5)
        val m = TimeRange.startOfMonth(now)
        val cal = Calendar.getInstance().apply { timeInMillis = m }
        assertEquals(1, cal.get(Calendar.DAY_OF_MONTH))
        assertEquals(Calendar.JUNE, cal.get(Calendar.MONTH))
        assertEquals(0, cal.get(Calendar.HOUR_OF_DAY))
        assertEquals(m, TimeRange.startOfToday(m))
    }

    @Test
    fun last7DayBucketsAreContiguousAndEndToday() {
        val now = at(2026, Calendar.JUNE, 17, 14, 5)
        val buckets = TimeRange.last7DayBuckets(now)
        assertEquals(7, buckets.size)
        // ascending + contiguous: each end == next start
        for (i in 0 until buckets.size - 1) {
            assertEquals(buckets[i].endExclusive, buckets[i + 1].start)
            assertTrue(buckets[i].start < buckets[i + 1].start)
        }
        // last bucket is today
        assertEquals(TimeRange.startOfToday(now), buckets.last().start)
        // first bucket starts 6 days before today
        assertTrue(TimeRange.startOfToday(now) - buckets.first().start in (5 * oneDay)..(7 * oneDay))
    }
}
```

- [ ] **Step 2: Run it to verify failure**

Run: `./gradlew :app:testDebugUnitTest --tests "com.artha.kirana.util.TimeRangeTest"`
Expected: FAIL — `startOfWeek` / `startOfMonth` / `last7DayBuckets` / `DayBucket` unresolved.

- [ ] **Step 3: Implement**

Replace the contents of `util/TimeRange.kt` with:

```kotlin
package com.artha.kirana.util

import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Locale

/** A single local-day window [start, endExclusive) with a short weekday label. */
data class DayBucket(val start: Long, val endExclusive: Long, val label: String)

object TimeRange {
    /** Epoch millis for 00:00:00.000 of the day containing [now]. */
    fun startOfToday(now: Long = System.currentTimeMillis()): Long {
        val cal = Calendar.getInstance().apply {
            timeInMillis = now
            set(Calendar.HOUR_OF_DAY, 0)
            set(Calendar.MINUTE, 0)
            set(Calendar.SECOND, 0)
            set(Calendar.MILLISECOND, 0)
        }
        return cal.timeInMillis
    }

    /** Midnight of the Monday on or before [now] (ISO week, locale-independent). */
    fun startOfWeek(now: Long = System.currentTimeMillis()): Long {
        val cal = Calendar.getInstance().apply { timeInMillis = startOfToday(now) }
        val daysFromMonday = (cal.get(Calendar.DAY_OF_WEEK) - Calendar.MONDAY + 7) % 7
        cal.add(Calendar.DAY_OF_MONTH, -daysFromMonday)
        return cal.timeInMillis
    }

    /** Midnight of the first day of the month containing [now]. */
    fun startOfMonth(now: Long = System.currentTimeMillis()): Long {
        val cal = Calendar.getInstance().apply {
            timeInMillis = startOfToday(now)
            set(Calendar.DAY_OF_MONTH, 1)
        }
        return cal.timeInMillis
    }

    /** The 7 local-day buckets ending with today, oldest first. */
    fun last7DayBuckets(now: Long = System.currentTimeMillis()): List<DayBucket> {
        val labelFmt = SimpleDateFormat("EEE", Locale.getDefault())
        val today = startOfToday(now)
        return (6 downTo 0).map { back ->
            val startCal = Calendar.getInstance().apply {
                timeInMillis = today
                add(Calendar.DAY_OF_MONTH, -back)
            }
            val start = startCal.timeInMillis
            val end = Calendar.getInstance().apply {
                timeInMillis = start
                add(Calendar.DAY_OF_MONTH, 1)
            }.timeInMillis
            DayBucket(start = start, endExclusive = end, label = labelFmt.format(start))
        }
    }
}
```

- [ ] **Step 4: Run tests to verify pass**

Run: `./gradlew :app:testDebugUnitTest --tests "com.artha.kirana.util.TimeRangeTest"`
Expected: PASS (3 tests).

- [ ] **Step 5: Commit**

```bash
git add app/src/main/java/com/artha/kirana/util/TimeRange.kt app/src/test/java/com/artha/kirana/util/TimeRangeTest.kt
git commit -m "feat: TimeRange week/month starts + 7-day buckets (TDD)"
```

---

## Task 3: P&L domain models + daily-revenue bucketing (TDD)

**Files:**
- Create: `app/src/main/java/com/artha/kirana/domain/model/PnlSummary.kt`
- Create: `app/src/main/java/com/artha/kirana/domain/usecase/RevenueBucketing.kt`
- Test: `app/src/test/java/com/artha/kirana/domain/usecase/RevenueBucketingTest.kt`

- [ ] **Step 1: Create the domain models**

Create `domain/model/PnlSummary.kt`:

```kotlin
package com.artha.kirana.domain.model

enum class PnlPeriod { TODAY, THIS_WEEK, THIS_MONTH }

data class PnlSummary(
    val grossRevenue: Double,
    val cogs: Double,
    val grossProfit: Double,
    val cashCollected: Double,
    val totalOutstanding: Double,
    val period: PnlPeriod,
)

/** One bar of the 7-day revenue chart. */
data class DailyRevenue(val dayLabel: String, val amount: Double)
```

- [ ] **Step 2: Write the failing test**

Create `app/src/test/java/com/artha/kirana/domain/usecase/RevenueBucketingTest.kt`:

```kotlin
package com.artha.kirana.domain.usecase

import com.artha.kirana.data.db.entity.SaleEntity
import com.artha.kirana.util.DayBucket
import org.junit.Assert.assertEquals
import org.junit.Test

class RevenueBucketingTest {

    private fun bucket(start: Long, label: String) =
        DayBucket(start = start, endExclusive = start + 1000, label = label)

    private fun sale(amount: Double, type: String, ts: Long) =
        SaleEntity(amount = amount, type = type, inputMethod = "typed", timestamp = ts)

    @Test
    fun sumsSalesIntoMatchingBucketAndExcludesRepayments() {
        val buckets = listOf(bucket(0, "Mon"), bucket(1000, "Tue"))
        val sales = listOf(
            sale(100.0, "cash", 10),        // bucket 0
            sale(50.0, "credit", 20),       // bucket 0 (credit counts as revenue)
            sale(30.0, "repayment", 30),    // bucket 0 — EXCLUDED
            sale(70.0, "cash", 1500),       // bucket 1
        )
        val result = bucketDailyRevenue(sales, buckets)
        assertEquals(2, result.size)
        assertEquals(150.0, result[0].amount, 0.001)
        assertEquals("Mon", result[0].dayLabel)
        assertEquals(70.0, result[1].amount, 0.001)
    }

    @Test
    fun emptyBucketsAreZero() {
        val buckets = listOf(bucket(0, "Mon"))
        val result = bucketDailyRevenue(emptyList(), buckets)
        assertEquals(1, result.size)
        assertEquals(0.0, result[0].amount, 0.001)
    }
}
```

- [ ] **Step 3: Run it to verify failure**

Run: `./gradlew :app:testDebugUnitTest --tests "com.artha.kirana.domain.usecase.RevenueBucketingTest"`
Expected: FAIL — `bucketDailyRevenue` unresolved.

- [ ] **Step 4: Implement**

Create `domain/usecase/RevenueBucketing.kt`:

```kotlin
package com.artha.kirana.domain.usecase

import com.artha.kirana.data.db.entity.SaleEntity
import com.artha.kirana.domain.model.DailyRevenue
import com.artha.kirana.util.DayBucket

/**
 * Sums non-repayment sale amounts into their local-day [buckets].
 * Repayments are not new revenue and are excluded (matches SalesDao.revenueBetween).
 */
fun bucketDailyRevenue(sales: List<SaleEntity>, buckets: List<DayBucket>): List<DailyRevenue> =
    buckets.map { b ->
        val total = sales
            .filter { it.type != "repayment" && it.timestamp >= b.start && it.timestamp < b.endExclusive }
            .sumOf { it.amount }
        DailyRevenue(dayLabel = b.label, amount = total)
    }
```

- [ ] **Step 5: Run tests to verify pass**

Run: `./gradlew :app:testDebugUnitTest --tests "com.artha.kirana.domain.usecase.RevenueBucketingTest"`
Expected: PASS (2 tests).

- [ ] **Step 6: Commit**

```bash
git add app/src/main/java/com/artha/kirana/domain/model/PnlSummary.kt app/src/main/java/com/artha/kirana/domain/usecase/RevenueBucketing.kt app/src/test/java/com/artha/kirana/domain/usecase/RevenueBucketingTest.kt
git commit -m "feat: P&L domain models + daily-revenue bucketing (TDD)"
```

---

## Task 4: DAO + repository extensions for P&L and worker

**Files:**
- Modify: `data/db/dao/SalesDao.kt`
- Modify: `data/db/dao/ItemsDao.kt`
- Modify: `data/db/dao/KhataDao.kt`
- Modify: `domain/repository/SalesRepository.kt` + `data/repository/SalesRepositoryImpl.kt`
- Modify: `domain/repository/InventoryRepository.kt` + `data/repository/InventoryRepositoryImpl.kt`
- Modify: `domain/repository/KhataRepository.kt` + `data/repository/KhataRepositoryImpl.kt`

This task is Room/Hilt wiring (not unit-testable in the hackathon budget) — verified by compile + the Task 13 on-device checkpoint.

- [ ] **Step 1: Add `cogsBetween` to `SalesDao`**

In `data/db/dao/SalesDao.kt`, add after `cashBetween`:

```kotlin
    /** Cost of goods sold = sum of qty*costPrice for non-repayment sales joined to items. */
    @Query(
        "SELECT COALESCE(SUM(s.qtySold * i.costPrice), 0) FROM sales s " +
            "JOIN items i ON s.itemId = i.id " +
            "WHERE s.type != 'repayment' AND s.timestamp BETWEEN :start AND :end",
    )
    fun cogsBetween(start: Long, end: Long): Flow<Double>
```

- [ ] **Step 2: Add `getAllOnce` to `ItemsDao`**

In `data/db/dao/ItemsDao.kt`, add after `lowStock`:

```kotlin
    @Query("SELECT * FROM items")
    suspend fun getAllOnce(): List<ItemEntity>
```

- [ ] **Step 3: Add `observeById` to `KhataDao`**

In `data/db/dao/KhataDao.kt`, add after `findByName`:

```kotlin
    @Query("SELECT * FROM khata WHERE id = :id")
    fun observeById(id: Long): Flow<KhataEntity?>
```

- [ ] **Step 4: Extend `SalesRepository` + impl**

In `domain/repository/SalesRepository.kt`, add to the interface:

```kotlin
    fun cashBetween(start: Long, end: Long): Flow<Double>
    fun cogsBetween(start: Long, end: Long): Flow<Double>
```

In `data/repository/SalesRepositoryImpl.kt`, add the overrides:

```kotlin
    override fun cashBetween(start: Long, end: Long): Flow<Double> = dao.cashBetween(start, end)
    override fun cogsBetween(start: Long, end: Long): Flow<Double> = dao.cogsBetween(start, end)
```

- [ ] **Step 5: Extend `InventoryRepository` + impl**

In `domain/repository/InventoryRepository.kt`, add:

```kotlin
    suspend fun updateItem(item: ItemEntity)
```

In `data/repository/InventoryRepositoryImpl.kt`, add:

```kotlin
    override suspend fun updateItem(item: ItemEntity) = dao.update(item)
```

- [ ] **Step 6: Extend `KhataRepository` + impl**

In `domain/repository/KhataRepository.kt`, add the imports for `KhataTransactionEntity` and the methods:

```kotlin
    fun observeParty(id: Long): Flow<KhataEntity?>
    fun observeTransactions(id: Long): Flow<List<KhataTransactionEntity>>
```

In `data/repository/KhataRepositoryImpl.kt`, add (the impl already injects `txnDao`):

```kotlin
    override fun observeParty(id: Long): Flow<KhataEntity?> = khataDao.observeById(id)
    override fun observeTransactions(id: Long): Flow<List<KhataTransactionEntity>> =
        txnDao.observeForParty(id)
```

- [ ] **Step 7: Compile-verify**

Run: `./gradlew :app:assembleDebug`
Expected: BUILD SUCCESSFUL.

- [ ] **Step 8: Commit**

```bash
git add app/src/main/java/com/artha/kirana/data/db/dao app/src/main/java/com/artha/kirana/domain/repository app/src/main/java/com/artha/kirana/data/repository
git commit -m "feat: DAO+repo extensions (cogsBetween, getAllOnce, khata observe, updateItem)"
```

---

## Task 5: P&L use-cases (TDD the combine)

**Files:**
- Create: `domain/usecase/GetPnlSummaryUseCase.kt`
- Create: `domain/usecase/GetDailyRevenueUseCase.kt`
- Test: `app/src/test/java/com/artha/kirana/domain/usecase/GetPnlSummaryUseCaseTest.kt`

- [ ] **Step 1: Write the failing test**

Create `app/src/test/java/com/artha/kirana/domain/usecase/GetPnlSummaryUseCaseTest.kt`:

```kotlin
package com.artha.kirana.domain.usecase

import com.artha.kirana.domain.model.PnlPeriod
import com.artha.kirana.domain.repository.KhataRepository
import com.artha.kirana.domain.repository.SalesRepository
import io.mockk.every
import io.mockk.mockk
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Test

class GetPnlSummaryUseCaseTest {

    private val sales = mockk<SalesRepository>()
    private val khata = mockk<KhataRepository>()
    private val useCase = GetPnlSummaryUseCase(sales, khata)

    @Test
    fun grossProfitIsRevenueMinusCogs_andFieldsMapThrough() = runTest {
        every { sales.revenueBetween(any(), any()) } returns flowOf(500.0)
        every { sales.cogsBetween(any(), any()) } returns flowOf(300.0)
        every { sales.cashBetween(any(), any()) } returns flowOf(420.0)
        every { khata.totalOutstanding() } returns flowOf(80.0)

        val summary = useCase(PnlPeriod.TODAY).first()

        assertEquals(500.0, summary.grossRevenue, 0.001)
        assertEquals(300.0, summary.cogs, 0.001)
        assertEquals(200.0, summary.grossProfit, 0.001)
        assertEquals(420.0, summary.cashCollected, 0.001)
        assertEquals(80.0, summary.totalOutstanding, 0.001)
        assertEquals(PnlPeriod.TODAY, summary.period)
    }
}
```

- [ ] **Step 2: Run it to verify failure**

Run: `./gradlew :app:testDebugUnitTest --tests "com.artha.kirana.domain.usecase.GetPnlSummaryUseCaseTest"`
Expected: FAIL — `GetPnlSummaryUseCase` unresolved.

- [ ] **Step 3: Implement `GetPnlSummaryUseCase`**

Create `domain/usecase/GetPnlSummaryUseCase.kt`:

```kotlin
package com.artha.kirana.domain.usecase

import com.artha.kirana.domain.model.PnlPeriod
import com.artha.kirana.domain.model.PnlSummary
import com.artha.kirana.domain.repository.KhataRepository
import com.artha.kirana.domain.repository.SalesRepository
import com.artha.kirana.util.TimeRange
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.combine
import javax.inject.Inject

/** Live P&L for a period: combines revenue, COGS, cash, and outstanding into one summary. */
class GetPnlSummaryUseCase @Inject constructor(
    private val sales: SalesRepository,
    private val khata: KhataRepository,
) {
    operator fun invoke(
        period: PnlPeriod,
        now: Long = System.currentTimeMillis(),
    ): Flow<PnlSummary> {
        val start = when (period) {
            PnlPeriod.TODAY -> TimeRange.startOfToday(now)
            PnlPeriod.THIS_WEEK -> TimeRange.startOfWeek(now)
            PnlPeriod.THIS_MONTH -> TimeRange.startOfMonth(now)
        }
        val end = Long.MAX_VALUE
        return combine(
            sales.revenueBetween(start, end),
            sales.cogsBetween(start, end),
            sales.cashBetween(start, end),
            khata.totalOutstanding(),
        ) { revenue, cogs, cash, outstanding ->
            PnlSummary(
                grossRevenue = revenue,
                cogs = cogs,
                grossProfit = revenue - cogs,
                cashCollected = cash,
                totalOutstanding = outstanding,
                period = period,
            )
        }
    }
}
```

- [ ] **Step 4: Implement `GetDailyRevenueUseCase`**

Create `domain/usecase/GetDailyRevenueUseCase.kt`:

```kotlin
package com.artha.kirana.domain.usecase

import com.artha.kirana.domain.model.DailyRevenue
import com.artha.kirana.domain.repository.SalesRepository
import com.artha.kirana.util.TimeRange
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import javax.inject.Inject

/** Last-7-days revenue as chart bars, bucketed by local day. */
class GetDailyRevenueUseCase @Inject constructor(
    private val sales: SalesRepository,
) {
    operator fun invoke(now: Long = System.currentTimeMillis()): Flow<List<DailyRevenue>> {
        val buckets = TimeRange.last7DayBuckets(now)
        return sales.observeSince(buckets.first().start).map { bucketDailyRevenue(it, buckets) }
    }
}
```

- [ ] **Step 5: Run tests to verify pass**

Run: `./gradlew :app:testDebugUnitTest --tests "com.artha.kirana.domain.usecase.GetPnlSummaryUseCaseTest"`
Expected: PASS.

- [ ] **Step 6: Commit**

```bash
git add app/src/main/java/com/artha/kirana/domain/usecase/GetPnlSummaryUseCase.kt app/src/main/java/com/artha/kirana/domain/usecase/GetDailyRevenueUseCase.kt app/src/test/java/com/artha/kirana/domain/usecase/GetPnlSummaryUseCaseTest.kt
git commit -m "feat: GetPnlSummaryUseCase + GetDailyRevenueUseCase (TDD)"
```

---

## Task 6: Inventory ViewModel

**Files:**
- Create: `ui/inventory/InventoryViewModel.kt`

- [ ] **Step 1: Implement the ViewModel**

Create `ui/inventory/InventoryViewModel.kt`:

```kotlin
package com.artha.kirana.ui.inventory

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.artha.kirana.data.db.entity.ItemEntity
import com.artha.kirana.domain.repository.InventoryRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import javax.inject.Inject

@HiltViewModel
class InventoryViewModel @Inject constructor(
    private val inventory: InventoryRepository,
) : ViewModel() {

    val items: StateFlow<List<ItemEntity>> =
        inventory.observeAll()
            .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    fun addItem(item: ItemEntity) = viewModelScope.launch { inventory.addItem(item) }

    fun saveItem(item: ItemEntity) = viewModelScope.launch { inventory.updateItem(item) }

    fun restock(item: ItemEntity, addQty: Double) = viewModelScope.launch {
        inventory.updateItem(item.copy(qtyInStock = item.qtyInStock + addQty))
    }
}
```

- [ ] **Step 2: Compile-verify**

Run: `./gradlew :app:assembleDebug`
Expected: BUILD SUCCESSFUL.

- [ ] **Step 3: Commit**

```bash
git add app/src/main/java/com/artha/kirana/ui/inventory/InventoryViewModel.kt
git commit -m "feat: InventoryViewModel (observe items, add/save/restock)"
```

---

## Task 7: Inventory UI (screen, card, add/edit sheet)

**Files:**
- Create: `ui/inventory/ItemCard.kt`
- Create: `ui/inventory/AddItemSheet.kt`
- Modify (replace placeholder): `ui/inventory/InventoryScreen.kt`

- [ ] **Step 1: Create `ItemCard`**

Create `ui/inventory/ItemCard.kt`:

```kotlin
package com.artha.kirana.ui.inventory

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.artha.kirana.data.db.entity.ItemEntity
import com.artha.kirana.ui.theme.AccentRed
import com.artha.kirana.util.formatRupees

/** True when the item has a threshold set and has dropped below it. */
fun ItemEntity.isLowStock(): Boolean = reorderThreshold > 0 && qtyInStock < reorderThreshold

@Composable
fun ItemCard(item: ItemEntity, onClick: () -> Unit) {
    val low = item.isLowStock()
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick),
        colors = if (low) {
            CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.errorContainer)
        } else {
            CardDefaults.cardColors()
        },
    ) {
        Row(
            Modifier
                .fillMaxWidth()
                .padding(16.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Column(Modifier.weight(1f)) {
                Text(item.name, style = MaterialTheme.typography.bodyLarge, fontWeight = FontWeight.Medium)
                Text(
                    "${trimQty(item.qtyInStock)} ${item.unit} in stock" + if (low) " · low!" else "",
                    style = MaterialTheme.typography.bodySmall,
                    color = if (low) AccentRed else MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            Text(formatRupees(item.sellPrice), style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
        }
    }
}

/** "2" not "2.0"; "1.5" stays "1.5". */
fun trimQty(q: Double): String = if (q % 1.0 == 0.0) q.toLong().toString() else q.toString()
```

- [ ] **Step 2: Create `AddItemSheet`**

Create `ui/inventory/AddItemSheet.kt`:

```kotlin
package com.artha.kirana.ui.inventory

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.ui.unit.dp
import com.artha.kirana.data.db.entity.ItemEntity

/**
 * Add/edit/restock sheet. [existing] == null → add mode; otherwise edit mode with a restock field.
 * Emits the assembled item via [onSave]; restock qty (edit mode only) via [onRestock].
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AddItemSheet(
    existing: ItemEntity?,
    onDismiss: () -> Unit,
    onSave: (ItemEntity) -> Unit,
    onRestock: (ItemEntity, Double) -> Unit,
) {
    var name by remember { mutableStateOf(existing?.name ?: "") }
    var nameHi by remember { mutableStateOf(existing?.nameHi ?: "") }
    var unit by remember { mutableStateOf(existing?.unit ?: "piece") }
    var cost by remember { mutableStateOf(existing?.costPrice?.takeIf { it > 0 }?.toString() ?: "") }
    var sell by remember { mutableStateOf(existing?.sellPrice?.takeIf { it > 0 }?.toString() ?: "") }
    var threshold by remember { mutableStateOf(existing?.reorderThreshold?.takeIf { it > 0 }?.toString() ?: "") }
    var qty by remember { mutableStateOf(existing?.qtyInStock?.takeIf { it > 0 }?.toString() ?: "") }
    var restockAmt by remember { mutableStateOf("") }

    val num = KeyboardOptions(keyboardType = KeyboardType.Decimal, imeAction = ImeAction.Next)

    ModalBottomSheet(onDismissRequest = onDismiss) {
        Column(
            Modifier
                .fillMaxWidth()
                .verticalScroll(rememberScrollState())
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            Text(if (existing == null) "Add item" else "Edit ${existing.name}")
            OutlinedTextField(name, { name = it }, label = { Text("Name") }, modifier = Modifier.fillMaxWidth())
            OutlinedTextField(nameHi, { nameHi = it }, label = { Text("Hindi name (optional)") }, modifier = Modifier.fillMaxWidth())
            OutlinedTextField(unit, { unit = it }, label = { Text("Unit (kg/litre/piece/dozen)") }, modifier = Modifier.fillMaxWidth())
            OutlinedTextField(qty, { qty = it }, label = { Text("Stock qty") }, keyboardOptions = num, modifier = Modifier.fillMaxWidth())
            OutlinedTextField(cost, { cost = it }, label = { Text("Cost price ₹") }, keyboardOptions = num, modifier = Modifier.fillMaxWidth())
            OutlinedTextField(sell, { sell = it }, label = { Text("Sell price ₹") }, keyboardOptions = num, modifier = Modifier.fillMaxWidth())
            OutlinedTextField(threshold, { threshold = it }, label = { Text("Reorder threshold") }, keyboardOptions = num, modifier = Modifier.fillMaxWidth())

            if (existing != null) {
                OutlinedTextField(restockAmt, { restockAmt = it }, label = { Text("Restock: add qty") }, keyboardOptions = num, modifier = Modifier.fillMaxWidth())
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.End) {
                    TextButton(onClick = {
                        val add = restockAmt.toDoubleOrNull()
                        if (add != null && add > 0) onRestock(existing, add)
                    }) { Text("Restock") }
                }
            }

            Spacer(Modifier.height(4.dp))
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.End) {
                TextButton(onClick = onDismiss) { Text("Cancel") }
                Button(
                    enabled = name.isNotBlank(),
                    onClick = {
                        val item = (existing ?: ItemEntity(name = name)).copy(
                            name = name.trim(),
                            nameHi = nameHi.trim().ifBlank { null },
                            unit = unit.trim().ifBlank { "piece" },
                            qtyInStock = qty.toDoubleOrNull() ?: (existing?.qtyInStock ?: 0.0),
                            costPrice = cost.toDoubleOrNull() ?: 0.0,
                            sellPrice = sell.toDoubleOrNull() ?: 0.0,
                            reorderThreshold = threshold.toDoubleOrNull() ?: 0.0,
                        )
                        onSave(item)
                    },
                ) { Text("Save") }
            }
        }
    }
}
```

- [ ] **Step 3: Replace `InventoryScreen`**

Replace the contents of `ui/inventory/InventoryScreen.kt`:

```kotlin
package com.artha.kirana.ui.inventory

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.artha.kirana.data.db.entity.ItemEntity

@Composable
fun InventoryScreen(vm: InventoryViewModel = hiltViewModel()) {
    val items by vm.items.collectAsStateWithLifecycle()

    // null = sheet closed; sentinel ADD item (id 0) = add mode; real item = edit mode.
    var sheetItem by remember { mutableStateOf<ItemEntity?>(null) }
    var showAdd by remember { mutableStateOf(false) }

    Box(Modifier.fillMaxSize()) {
        if (items.isEmpty()) {
            Text(
                "No items yet. Tap + to add stock.",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.align(Alignment.Center),
            )
        } else {
            LazyColumn(
                Modifier
                    .fillMaxSize()
                    .padding(16.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                items(items, key = { it.id }) { item ->
                    ItemCard(item = item, onClick = { sheetItem = item; showAdd = false })
                }
            }
        }

        FloatingActionButton(
            onClick = { showAdd = true; sheetItem = null },
            modifier = Modifier
                .align(Alignment.BottomEnd)
                .padding(16.dp),
        ) { Icon(Icons.Filled.Add, contentDescription = "Add item") }
    }

    if (showAdd || sheetItem != null) {
        AddItemSheet(
            existing = sheetItem,
            onDismiss = { showAdd = false; sheetItem = null },
            onSave = { item ->
                if (item.id == 0L) vm.addItem(item) else vm.saveItem(item)
                showAdd = false; sheetItem = null
            },
            onRestock = { item, add ->
                vm.restock(item, add)
                showAdd = false; sheetItem = null
            },
        )
    }
}
```

- [ ] **Step 4: Update `ArthaApp` to use the new InventoryScreen signature**

In `ui/ArthaApp.kt`, the existing `composable(TopDest.Inventory.route) { InventoryScreen() }` line already calls `InventoryScreen()` with no args — the new default-arg signature is compatible. No change needed here yet (the nav additions come in Task 9/10). Confirm it still compiles in the next step.

- [ ] **Step 5: Build + install + eyeball**

Run: `./gradlew :app:installDebug`
Then: `adb shell am start -n com.artha.kirana/.MainActivity`
Expected: Inventory tab shows empty-state + FAB; tapping FAB opens the add sheet; saving an item makes it appear; tapping a card opens edit/restock. Screenshot to confirm.

- [ ] **Step 6: Commit**

```bash
git add app/src/main/java/com/artha/kirana/ui/inventory
git commit -m "feat: Inventory UI — list, low-stock highlight, add/edit/restock sheet"
```

---

## Task 8: Khata ViewModels

**Files:**
- Create: `ui/khata/KhataViewModel.kt`
- Create: `ui/khata/KhataPartyDetailViewModel.kt`

- [ ] **Step 1: Create `KhataViewModel`**

Create `ui/khata/KhataViewModel.kt`:

```kotlin
package com.artha.kirana.ui.khata

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.artha.kirana.data.db.entity.KhataEntity
import com.artha.kirana.domain.repository.KhataRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.stateIn
import javax.inject.Inject

@HiltViewModel
class KhataViewModel @Inject constructor(
    khata: KhataRepository,
) : ViewModel() {

    val parties: StateFlow<List<KhataEntity>> =
        khata.observeAll()
            .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    val totalOutstanding: StateFlow<Double> =
        khata.totalOutstanding()
            .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), 0.0)
}
```

- [ ] **Step 2: Create `KhataPartyDetailViewModel`**

Create `ui/khata/KhataPartyDetailViewModel.kt`:

```kotlin
package com.artha.kirana.ui.khata

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.artha.kirana.data.db.entity.KhataEntity
import com.artha.kirana.data.db.entity.KhataTransactionEntity
import com.artha.kirana.domain.repository.KhataRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import javax.inject.Inject

@HiltViewModel
class KhataPartyDetailViewModel @Inject constructor(
    private val khata: KhataRepository,
    savedStateHandle: SavedStateHandle,
) : ViewModel() {

    private val partyId: Long = savedStateHandle.get<Long>("partyId") ?: 0L

    val party: StateFlow<KhataEntity?> =
        khata.observeParty(partyId)
            .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), null)

    val transactions: StateFlow<List<KhataTransactionEntity>> =
        khata.observeTransactions(partyId)
            .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    fun recordPayment(partyName: String, amount: Double) = viewModelScope.launch {
        khata.applyRepayment(partyName, amount, saleId = null)
    }
}
```

- [ ] **Step 3: Compile-verify**

Run: `./gradlew :app:assembleDebug`
Expected: BUILD SUCCESSFUL.

- [ ] **Step 4: Commit**

```bash
git add app/src/main/java/com/artha/kirana/ui/khata/KhataViewModel.kt app/src/main/java/com/artha/kirana/ui/khata/KhataPartyDetailViewModel.kt
git commit -m "feat: Khata ViewModels (party list + detail with record-payment)"
```

---

## Task 9: Khata UI + nav route

**Files:**
- Modify (replace placeholder): `ui/khata/KhataScreen.kt`
- Create: `ui/khata/KhataPartyDetail.kt`
- Modify: `ui/ArthaApp.kt`

- [ ] **Step 1: Replace `KhataScreen`**

Replace the contents of `ui/khata/KhataScreen.kt`:

```kotlin
package com.artha.kirana.ui.khata

import androidx.compose.foundation.clickable
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
import androidx.compose.material3.Card
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.artha.kirana.data.db.entity.KhataEntity
import com.artha.kirana.ui.theme.AccentGreen
import com.artha.kirana.ui.theme.AccentRed
import com.artha.kirana.util.formatRupees

@Composable
fun KhataScreen(
    onParty: (Long) -> Unit,
    vm: KhataViewModel = hiltViewModel(),
) {
    val parties by vm.parties.collectAsStateWithLifecycle()
    val total by vm.totalOutstanding.collectAsStateWithLifecycle()

    Column(
        Modifier
            .fillMaxSize()
            .padding(16.dp),
    ) {
        Card(Modifier.fillMaxWidth()) {
            Column(Modifier.padding(20.dp)) {
                Text("कुल उधार · Total outstanding", style = MaterialTheme.typography.titleMedium)
                Spacer(Modifier.height(6.dp))
                Text(formatRupees(total), style = MaterialTheme.typography.headlineMedium, fontWeight = FontWeight.Bold)
            }
        }
        Spacer(Modifier.height(20.dp))

        if (parties.isEmpty()) {
            Text(
                "No khata entries yet. Credit sales appear here.",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        } else {
            LazyColumn(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                items(parties, key = { it.id }) { party -> PartyRow(party, onClick = { onParty(party.id) }) }
            }
        }
    }
}

@Composable
private fun PartyRow(party: KhataEntity, onClick: () -> Unit) {
    Card(Modifier.fillMaxWidth().clickable(onClick = onClick)) {
        Row(
            Modifier.fillMaxWidth().padding(16.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(party.partyName, style = MaterialTheme.typography.bodyLarge, fontWeight = FontWeight.Medium)
            Text(
                formatRupees(party.balance),
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold,
                color = if (party.balance > 0) AccentRed else AccentGreen,
            )
        }
    }
}
```

- [ ] **Step 2: Create `KhataPartyDetail`**

Create `ui/khata/KhataPartyDetail.kt`:

```kotlin
package com.artha.kirana.ui.khata

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
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.artha.kirana.data.db.entity.KhataTransactionEntity
import com.artha.kirana.ui.theme.AccentGreen
import com.artha.kirana.ui.theme.AccentRed
import com.artha.kirana.util.formatRupees
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

@Composable
fun KhataPartyDetail(vm: KhataPartyDetailViewModel = hiltViewModel()) {
    val party by vm.party.collectAsStateWithLifecycle()
    val txns by vm.transactions.collectAsStateWithLifecycle()
    var showPayDialog by remember { mutableStateOf(false) }

    val p = party
    if (p == null) {
        Text(
            "Loading…",
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.padding(16.dp),
        )
        return
    }

    Column(
        Modifier
            .fillMaxSize()
            .padding(16.dp),
    ) {
        Card(Modifier.fillMaxWidth()) {
            Column(Modifier.padding(20.dp)) {
                Text(p.partyName, style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold)
                Spacer(Modifier.height(6.dp))
                Text(
                    "Balance: ${formatRupees(p.balance)}",
                    style = MaterialTheme.typography.titleMedium,
                    color = if (p.balance > 0) AccentRed else AccentGreen,
                )
            }
        }
        Spacer(Modifier.height(12.dp))
        Button(onClick = { showPayDialog = true }, modifier = Modifier.fillMaxWidth()) {
            Text("Record payment")
        }
        Spacer(Modifier.height(16.dp))
        Text("History", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
        Spacer(Modifier.height(8.dp))

        if (txns.isEmpty()) {
            Text("No transactions yet.", color = MaterialTheme.colorScheme.onSurfaceVariant)
        } else {
            LazyColumn(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                items(txns, key = { it.id }) { TxnRow(it) }
            }
        }
    }

    if (showPayDialog) {
        var amount by remember { mutableStateOf("") }
        AlertDialog(
            onDismissRequest = { showPayDialog = false },
            title = { Text("Record payment") },
            text = {
                OutlinedTextField(
                    value = amount,
                    onValueChange = { amount = it },
                    label = { Text("Amount ₹") },
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                )
            },
            confirmButton = {
                TextButton(onClick = {
                    val amt = amount.toDoubleOrNull()
                    val name = party?.partyName
                    if (amt != null && amt > 0 && name != null) vm.recordPayment(name, amt)
                    showPayDialog = false
                }) { Text("Save") }
            },
            dismissButton = { TextButton(onClick = { showPayDialog = false }) { Text("Cancel") } },
        )
    }
}

@Composable
private fun TxnRow(txn: KhataTransactionEntity) {
    Card(Modifier.fillMaxWidth()) {
        Row(
            Modifier.fillMaxWidth().padding(16.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
        ) {
            Column {
                Text(
                    if (txn.type == "repayment") "Repayment" else "Credit",
                    fontWeight = FontWeight.Medium,
                    color = if (txn.type == "repayment") AccentGreen else AccentRed,
                )
                Text(
                    SimpleDateFormat("d MMM, h:mm a", Locale.getDefault()).format(Date(txn.timestamp)),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            Text(formatRupees(txn.amount), fontWeight = FontWeight.Bold)
        }
    }
}
```

- [ ] **Step 3: Wire the nav route in `ArthaApp`**

In `ui/ArthaApp.kt`:

1. Add imports near the other `androidx.navigation` imports:

```kotlin
import androidx.navigation.NavType
import androidx.navigation.navArgument
import com.artha.kirana.ui.khata.KhataPartyDetail
```

2. Add the route constant next to `ROUTE_SALE_ENTRY`:

```kotlin
const val ROUTE_KHATA_DETAIL = "khata" // full pattern: khata/{partyId}
```

3. Replace the `composable(TopDest.Khata.route) { KhataScreen() }` line with:

```kotlin
            composable(TopDest.Khata.route) {
                KhataScreen(onParty = { id -> navController.navigate("$ROUTE_KHATA_DETAIL/$id") })
            }
            composable(
                route = "$ROUTE_KHATA_DETAIL/{partyId}",
                arguments = listOf(navArgument("partyId") { type = NavType.LongType }),
            ) { KhataPartyDetail() }
```

- [ ] **Step 4: Build + install + verify**

Run: `./gradlew :app:installDebug`
Then log a credit sale (e.g. `2 kilo chawal assi rupaye udhaar Ramesh ko`), open Khata → Ramesh shows red balance → tap → detail shows history + "Record payment". Record a payment → balance drops. Screenshot.

- [ ] **Step 5: Commit**

```bash
git add app/src/main/java/com/artha/kirana/ui/khata app/src/main/java/com/artha/kirana/ui/ArthaApp.kt
git commit -m "feat: Khata UI — party list + detail with record-payment + nav route"
```

---

## Task 10: P&L UI (screen, chart, ViewModel)

**Files:**
- Create: `ui/pnl/PnlViewModel.kt`
- Create: `ui/pnl/ProfitChart.kt`
- Modify (replace placeholder): `ui/pnl/PnlScreen.kt`

> **Context7 gate (spec requirement):** before writing `ProfitChart.kt`, confirm the Vico 2.1.x API — specifically whether the model producer is constructed via `CartesianChartModelProducer()` (2.1.x) and the exact import paths for `CartesianChartHost`, `rememberCartesianChart`, `rememberColumnCartesianLayer`, `VerticalAxis.rememberStart`, `HorizontalAxis.rememberBottom`, `columnSeries`. Resolve `/patrykandpatrick/vico` and query for the version matching the resolved Gradle version. Adjust the snippet below to match.

- [ ] **Step 1: Create `PnlViewModel`**

Create `ui/pnl/PnlViewModel.kt`:

```kotlin
package com.artha.kirana.ui.pnl

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.artha.kirana.domain.model.DailyRevenue
import com.artha.kirana.domain.model.PnlPeriod
import com.artha.kirana.domain.model.PnlSummary
import com.artha.kirana.domain.usecase.GetDailyRevenueUseCase
import com.artha.kirana.domain.usecase.GetPnlSummaryUseCase
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.stateIn
import javax.inject.Inject

@OptIn(ExperimentalCoroutinesApi::class)
@HiltViewModel
class PnlViewModel @Inject constructor(
    getPnl: GetPnlSummaryUseCase,
    getDaily: GetDailyRevenueUseCase,
) : ViewModel() {

    private val _period = MutableStateFlow(PnlPeriod.TODAY)
    val period: StateFlow<PnlPeriod> = _period

    val summary: StateFlow<PnlSummary?> =
        _period.flatMapLatest { getPnl(it) }
            .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), null)

    val daily: StateFlow<List<DailyRevenue>> =
        getDaily().stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    fun selectPeriod(p: PnlPeriod) { _period.value = p }
}
```

- [ ] **Step 2: Create `ProfitChart` (confirm API via Context7 first)**

Create `ui/pnl/ProfitChart.kt`:

```kotlin
package com.artha.kirana.ui.pnl

import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import com.artha.kirana.domain.model.DailyRevenue
import com.patrykandpatrick.vico.compose.cartesian.CartesianChartHost
import com.patrykandpatrick.vico.compose.cartesian.axis.rememberBottom
import com.patrykandpatrick.vico.compose.cartesian.axis.rememberStart
import com.patrykandpatrick.vico.compose.cartesian.layer.rememberColumnCartesianLayer
import com.patrykandpatrick.vico.compose.cartesian.rememberCartesianChart
import com.patrykandpatrick.vico.core.cartesian.axis.HorizontalAxis
import com.patrykandpatrick.vico.core.cartesian.axis.VerticalAxis
import com.patrykandpatrick.vico.core.cartesian.data.CartesianChartModelProducer
import com.patrykandpatrick.vico.core.cartesian.data.columnSeries

@Composable
fun ProfitChart(daily: List<DailyRevenue>, modifier: Modifier = Modifier) {
    val modelProducer = remember { CartesianChartModelProducer() }
    LaunchedEffect(daily) {
        if (daily.isNotEmpty()) {
            modelProducer.runTransaction {
                columnSeries { series(daily.map { it.amount }) }
            }
        }
    }
    CartesianChartHost(
        chart = rememberCartesianChart(
            rememberColumnCartesianLayer(),
            startAxis = VerticalAxis.rememberStart(),
            bottomAxis = HorizontalAxis.rememberBottom(),
        ),
        modelProducer = modelProducer,
        modifier = modifier,
    )
}
```

If Context7 shows the resolved version differs (e.g. `CartesianChartModelProducer.build()` in older 2.0.x, or different axis import paths), adjust imports/constructor accordingly before moving on.

- [ ] **Step 3: Replace `PnlScreen`**

Replace the contents of `ui/pnl/PnlScreen.kt`:

```kotlin
package com.artha.kirana.ui.pnl

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Card
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Tab
import androidx.compose.material3.TabRow
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.artha.kirana.domain.model.PnlPeriod
import com.artha.kirana.domain.model.PnlSummary
import com.artha.kirana.util.formatRupees

@Composable
fun PnlScreen(vm: PnlViewModel = hiltViewModel()) {
    val period by vm.period.collectAsStateWithLifecycle()
    val summary by vm.summary.collectAsStateWithLifecycle()
    val daily by vm.daily.collectAsStateWithLifecycle()

    val tabs = listOf(PnlPeriod.TODAY to "Today", PnlPeriod.THIS_WEEK to "Week", PnlPeriod.THIS_MONTH to "Month")

    Column(
        Modifier
            .fillMaxSize()
            .padding(16.dp),
    ) {
        TabRow(selectedTabIndex = tabs.indexOfFirst { it.first == period }) {
            tabs.forEach { (p, label) ->
                Tab(selected = period == p, onClick = { vm.selectPeriod(p) }, text = { Text(label) })
            }
        }
        Spacer(Modifier.height(16.dp))

        val s = summary
        if (s == null) {
            Text("Loading…", color = MaterialTheme.colorScheme.onSurfaceVariant)
        } else {
            MetricCards(s)
            Spacer(Modifier.height(20.dp))
            Text("Last 7 days revenue", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
            Spacer(Modifier.height(8.dp))
            if (daily.any { it.amount > 0 }) {
                ProfitChart(daily = daily, modifier = Modifier.fillMaxWidth().height(200.dp))
            } else {
                Text("No revenue in the last 7 days.", color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        }
    }
}

@Composable
private fun MetricCards(s: PnlSummary) {
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Card(
            Modifier.fillMaxWidth(),
            colors = androidx.compose.material3.CardDefaults.cardColors(
                containerColor = MaterialTheme.colorScheme.primary,
                contentColor = MaterialTheme.colorScheme.onPrimary,
            ),
        ) {
            Column(Modifier.padding(20.dp)) {
                Text("Gross profit", style = MaterialTheme.typography.titleMedium)
                Spacer(Modifier.height(4.dp))
                Text(formatRupees(s.grossProfit), style = MaterialTheme.typography.headlineMedium, fontWeight = FontWeight.Bold)
            }
        }
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            MetricMini("Revenue", s.grossRevenue, Modifier.weight(1f))
            MetricMini("COGS", s.cogs, Modifier.weight(1f))
        }
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            MetricMini("Cash collected", s.cashCollected, Modifier.weight(1f))
            MetricMini("Outstanding", s.totalOutstanding, Modifier.weight(1f))
        }
    }
}

@Composable
private fun MetricMini(label: String, amount: Double, modifier: Modifier = Modifier) {
    Card(modifier) {
        Column(Modifier.padding(16.dp)) {
            Text(label, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            Spacer(Modifier.height(4.dp))
            Text(formatRupees(amount), style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold)
        }
    }
}
```

- [ ] **Step 4: Build + install + verify P&L arithmetic**

Run: `./gradlew :app:installDebug`
Log a cash sale of a known item with cost/sell prices set, then open P&L → Today: Revenue, COGS, Gross profit = revenue − cogs, Cash collected populate; the chart shows a bar for today. Switch tabs. Screenshot.

- [ ] **Step 5: Commit**

```bash
git add app/src/main/java/com/artha/kirana/ui/pnl
git commit -m "feat: P&L UI — period tabs, metric cards, Vico 7-day revenue chart"
```

---

## Task 11: Notifications + low-stock worker

**Files:**
- Create: `data/notification/NotificationHelper.kt`
- Create: `data/worker/InventoryAlertWorker.kt`

- [ ] **Step 1: Create `NotificationHelper`**

Create `data/notification/NotificationHelper.kt`:

```kotlin
package com.artha.kirana.data.notification

import android.Manifest
import android.app.NotificationChannel
import android.app.NotificationManager
import android.content.Context
import android.content.pm.PackageManager
import android.os.Build
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import androidx.core.content.ContextCompat
import com.artha.kirana.data.db.entity.ItemEntity
import dagger.hilt.android.qualifiers.ApplicationContext
import javax.inject.Inject
import javax.inject.Singleton

/** Posts/cancels low-stock notifications. Notification id == item id (stable → auto-clearable). */
@Singleton
class NotificationHelper @Inject constructor(
    @ApplicationContext private val context: Context,
) {
    companion object {
        const val CHANNEL_ID = "INVENTORY_ALERTS"
    }

    init {
        val mgr = context.getSystemService(NotificationManager::class.java)
        val channel = NotificationChannel(
            CHANNEL_ID,
            "Inventory alerts",
            NotificationManager.IMPORTANCE_DEFAULT,
        ).apply { description = "Low-stock reminders" }
        mgr.createNotificationChannel(channel)
    }

    fun notifyLowStock(item: ItemEntity) {
        if (!hasPermission()) return
        val notification = NotificationCompat.Builder(context, CHANNEL_ID)
            .setSmallIcon(android.R.drawable.stat_sys_warning)
            .setContentTitle("Low stock: ${item.name}")
            .setContentText("Only ${item.qtyInStock} ${item.unit} left — reorder soon.")
            .setAutoCancel(true)
            .build()
        NotificationManagerCompat.from(context).notify(item.id.toInt(), notification)
    }

    fun cancel(itemId: Long) {
        NotificationManagerCompat.from(context).cancel(itemId.toInt())
    }

    private fun hasPermission(): Boolean =
        Build.VERSION.SDK_INT < Build.VERSION_CODES.TIRAMISU ||
            ContextCompat.checkSelfPermission(context, Manifest.permission.POST_NOTIFICATIONS) ==
            PackageManager.PERMISSION_GRANTED
}
```

> Note: `androidx.core:core-ktx` (already a dependency) provides `NotificationManagerCompat`/`NotificationCompat`/`ContextCompat`. No new dependency needed.

- [ ] **Step 2: Create `InventoryAlertWorker`**

Create `data/worker/InventoryAlertWorker.kt`:

```kotlin
package com.artha.kirana.data.worker

import android.content.Context
import androidx.hilt.work.HiltWorker
import androidx.work.CoroutineWorker
import androidx.work.WorkerParameters
import com.artha.kirana.data.db.dao.ItemsDao
import com.artha.kirana.data.notification.NotificationHelper
import dagger.assisted.Assisted
import dagger.assisted.AssistedInject

/**
 * Periodic low-stock check. Fires a notification per low item and CANCELS notifications for
 * items now back above threshold (auto-clear on restock — notification id == item id).
 */
@HiltWorker
class InventoryAlertWorker @AssistedInject constructor(
    @Assisted context: Context,
    @Assisted params: WorkerParameters,
    private val itemsDao: ItemsDao,
    private val notifications: NotificationHelper,
) : CoroutineWorker(context, params) {

    override suspend fun doWork(): Result {
        val low = itemsDao.lowStock()
        val lowIds = low.map { it.id }.toSet()
        // Auto-clear: cancel notifications for items no longer low.
        itemsDao.getAllOnce().forEach { item ->
            if (item.id !in lowIds) notifications.cancel(item.id)
        }
        low.forEach { notifications.notifyLowStock(it) }
        return Result.success()
    }
}
```

- [ ] **Step 3: Compile-verify**

Run: `./gradlew :app:assembleDebug`
Expected: BUILD SUCCESSFUL (Hilt generates the worker factory entry).

- [ ] **Step 4: Commit**

```bash
git add app/src/main/java/com/artha/kirana/data/notification app/src/main/java/com/artha/kirana/data/worker
git commit -m "feat: NotificationHelper + InventoryAlertWorker (HiltWorker, auto-clear)"
```

---

## Task 12: WorkManager Hilt wiring + scheduling + notification permission

**Files:**
- Modify: `ArthaApplication.kt`
- Modify: `app/src/main/AndroidManifest.xml`
- Modify: `ui/ArthaApp.kt`

- [ ] **Step 1: Make `ArthaApplication` a `Configuration.Provider` and schedule the worker**

Replace the contents of `ArthaApplication.kt`:

```kotlin
package com.artha.kirana

import android.app.Application
import androidx.hilt.work.HiltWorkerFactory
import androidx.work.Configuration
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkManager
import com.artha.kirana.data.worker.InventoryAlertWorker
import dagger.hilt.android.HiltAndroidApp
import timber.log.Timber
import java.util.concurrent.TimeUnit
import javax.inject.Inject

@HiltAndroidApp
class ArthaApplication : Application(), Configuration.Provider {

    @Inject lateinit var workerFactory: HiltWorkerFactory

    override val workManagerConfiguration: Configuration
        get() = Configuration.Builder().setWorkerFactory(workerFactory).build()

    override fun onCreate() {
        super.onCreate()
        if (BuildConfig.DEBUG) {
            Timber.plant(Timber.DebugTree())
        }
        scheduleInventoryAlerts()
    }

    private fun scheduleInventoryAlerts() {
        val request = PeriodicWorkRequestBuilder<InventoryAlertWorker>(30, TimeUnit.MINUTES).build()
        WorkManager.getInstance(this).enqueueUniquePeriodicWork(
            "inventory_alerts",
            ExistingPeriodicWorkPolicy.KEEP,
            request,
        )
    }
}
```

- [ ] **Step 2: Remove the default WorkManager initializer in the manifest**

Hilt-provided `Configuration` requires on-demand initialization. In `app/src/main/AndroidManifest.xml`, ensure the root `<manifest>` tag has `xmlns:tools="http://schemas.android.com/tools"`, then add this `<provider>` inside `<application>` (anywhere among its children):

```xml
        <provider
            android:name="androidx.startup.InitializationProvider"
            android:authorities="${applicationId}.androidx-startup"
            android:exported="false"
            tools:node="merge">
            <meta-data
                android:name="androidx.work.WorkManagerInitializer"
                android:value="androidx.startup"
                tools:node="remove" />
        </provider>
```

- [ ] **Step 3: Request POST_NOTIFICATIONS at runtime (API 33+)**

In `ui/ArthaApp.kt`, add imports:

```kotlin
import android.Manifest
import android.os.Build
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.runtime.LaunchedEffect
```

Then inside `ArthaApp()`, right after `val topRoutes = ...`, add:

```kotlin
    val notifPermLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestPermission(),
    ) { /* result ignored — alerts simply stay silent if denied */ }
    LaunchedEffect(Unit) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            notifPermLauncher.launch(Manifest.permission.POST_NOTIFICATIONS)
        }
    }
```

- [ ] **Step 4: Build + install + verify worker runs and auto-clears**

Run: `./gradlew :app:installDebug` then launch the app (grant the notification prompt).

Force the periodic worker to run immediately for testing:
```bash
adb shell cmd jobscheduler run -f com.artha.kirana $(adb shell dumpsys jobscheduler | grep -m1 "com.artha.kirana" | grep -o '#[0-9]*/[0-9]*' | head -1 | grep -o '[0-9]*$')
```
If that id lookup is fiddly, instead: add an item with `reorderThreshold = 5`, `qtyInStock = 2` via the Inventory sheet, then toggle airplane/relaunch and wait, or temporarily lower the period to verify. Confirm a "Low stock" notification appears. Then restock the item above 5 and re-run the worker → the notification disappears (auto-clear).

Expected: low-stock notification fires; clears after restock.

- [ ] **Step 5: Commit**

```bash
git add app/src/main/java/com/artha/kirana/ArthaApplication.kt app/src/main/AndroidManifest.xml app/src/main/java/com/artha/kirana/ui/ArthaApp.kt
git commit -m "feat: WorkManager Hilt config + 30-min low-stock schedule + POST_NOTIFICATIONS request"
```

---

## Task 13: Phase 2 checkpoint (on-device, CLAUDE.md §15)

**Files:** none (verification only).

- [ ] **Step 1: Full unit-test pass**

Run: `./gradlew :app:testDebugUnitTest`
Expected: all unit tests green (Phase 1 + the new TimeRange/RevenueBucketing/GetPnlSummaryUseCase tests).

- [ ] **Step 2: Clean build + install on the iQOO**

Run: `./scripts/start-llama-server.sh` then `./gradlew :app:installDebug`
Then: `adb shell am start -n com.artha.kirana/.MainActivity`

- [ ] **Step 3: Seed the inventory**

Add 5 items via the Inventory FAB with cost/sell prices and one item with a reorder threshold above its stock (e.g. rice, sugar, oil, soap, biscuits). Set soap `qtyInStock = 2`, `reorderThreshold = 5`.

- [ ] **Step 4: Run the §15 checkpoint flow**

Log ~10 varied sales (cash + credit for Ramesh/Priya + a repayment) matching the items, then verify:
- **Inventory** counts decremented correctly for sold items.
- **Khata** balances correct (credit raises balance red; recorded/voiced repayment lowers it).
- **P&L** Today: gross profit == revenue − COGS; cash collected == sum of cash sales; outstanding == sum of positive khata balances. Week/Month tabs aggregate wider. Chart shows today's bar.
- **Low-stock**: trigger the worker (Task 12 Step 4) → soap notification fires → restock soap above 5 → re-run worker → notification clears.

- [ ] **Step 5: Update STATUS + commit**

Update `docs/STATUS.md` Phase 2 row to ✅ with a one-line "what works" summary, then:

```bash
git add docs/STATUS.md
git commit -m "docs: Phase 2 (Inventory/Khata/P&L) done & device-verified"
```

---

## Self-Review Notes (addressed)

- **Spec coverage:** Inventory list+low-stock+add/edit/restock (T6–7), worker auto-clear (T11–12), Khata list+detail+record-payment (T8–9), `GetPnlSummaryUseCase`+§10 queries (T4–5), PnlScreen tabs+chart (T10), Vico 2.1.x + Hilt-work deps (T1), TimeRange+models+bucketing TDD (T2–3). All spec components map to a task.
- **Type consistency:** `bucketDailyRevenue(sales, buckets)`, `DayBucket(start, endExclusive, label)`, `DailyRevenue(dayLabel, amount)`, `PnlSummary(grossRevenue, cogs, grossProfit, cashCollected, totalOutstanding, period)`, `InventoryRepository.updateItem`, `KhataRepository.observeParty/observeTransactions/applyRepayment(party, amount, saleId)`, `SalesRepository.cashBetween/cogsBetween/revenueBetween/observeSince` — names used identically across producing and consuming tasks.
- **Known follow-up (not blocking Phase 2):** forcing the periodic worker via `jobscheduler run` is fiddly on OriginOS; if needed for the demo, a debug-only one-shot enqueue button can be added in Phase 6 hardening.
```

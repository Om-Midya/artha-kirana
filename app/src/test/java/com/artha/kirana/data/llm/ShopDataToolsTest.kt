package com.artha.kirana.data.llm

import com.artha.kirana.data.db.entity.CustomerEntity
import com.artha.kirana.data.db.entity.ItemEntity
import com.artha.kirana.data.db.entity.KhataEntity
import com.artha.kirana.domain.model.CustomerSummary
import com.artha.kirana.domain.model.PnlPeriod
import com.artha.kirana.domain.model.PnlSummary
import com.artha.kirana.domain.model.TopSellerRow
import com.artha.kirana.domain.repository.CustomerRepository
import com.artha.kirana.domain.repository.InventoryRepository
import com.artha.kirana.domain.repository.KhataRepository
import com.artha.kirana.domain.usecase.GetCustomerSummaryUseCase
import com.artha.kirana.domain.usecase.GetDayOfWeekTrendUseCase
import com.artha.kirana.domain.usecase.GetItemMarginsUseCase
import com.artha.kirana.domain.usecase.GetPnlSummaryUseCase
import com.artha.kirana.domain.usecase.GetTopSellersUseCase
import io.mockk.coEvery
import io.mockk.every
import io.mockk.mockk
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.test.runTest
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class ShopDataToolsTest {

    // ── Mocks ─────────────────────────────────────────────────────────────────

    private val getPnl = mockk<GetPnlSummaryUseCase>(relaxed = true)
    private val getTopSellers = mockk<GetTopSellersUseCase>(relaxed = true)
    private val getCustomerSummary = mockk<GetCustomerSummaryUseCase>(relaxed = true)
    private val getDayTrend = mockk<GetDayOfWeekTrendUseCase>(relaxed = true)
    private val getItemMargins = mockk<GetItemMarginsUseCase>(relaxed = true)
    private val inventory = mockk<InventoryRepository>(relaxed = true)
    private val customers = mockk<CustomerRepository>(relaxed = true)
    private val khata = mockk<KhataRepository>(relaxed = true)

    private val tools = ShopDataTools(
        getPnl = getPnl,
        getTopSellers = getTopSellers,
        getCustomerSummary = getCustomerSummary,
        getDayTrend = getDayTrend,
        getItemMargins = getItemMargins,
        inventory = inventory,
        customers = customers,
        khata = khata,
    )

    // ── Test 1: definitions contains all 8 tool names ─────────────────────────

    @Test
    fun definitionsContainsAllEightToolNames() {
        val names = tools.definitions.map { element ->
            element.jsonObject["function"]!!.jsonObject["name"]!!.jsonPrimitive.content
        }.toSet()

        assertEquals(
            setOf(
                "get_pnl",
                "get_top_sellers",
                "get_customer",
                "get_day_trend",
                "get_item_margins",
                "get_low_stock",
                "list_customers",
                "get_inventory",
            ),
            names,
        )
    }

    // ── Test 2: get_pnl returns profit json ───────────────────────────────────

    @Test
    fun getPnlReturnsProfitJson() = runTest {
        val summary = PnlSummary(
            grossRevenue = 1000.0,
            cogs = 830.0,
            grossProfit = 170.0,
            cashCollected = 800.0,
            totalOutstanding = 200.0,
            period = PnlPeriod.THIS_MONTH,
        )
        every { getPnl(any(), any()) } returns flowOf(summary)

        val result = tools.execute("get_pnl", """{"period":"month"}""")

        assertTrue("result should contain profit:170", result.contains("\"profit\":170"))
        assertTrue("result should contain revenue", result.contains("\"revenue\":1000"))
        assertTrue("result should contain outstanding", result.contains("\"outstanding\":200"))
    }

    // ── Test 3: get_low_stock filters below threshold ─────────────────────────

    @Test
    fun getLowStockFiltersOnlyBelowThreshold() = runTest {
        val lowItem = ItemEntity(
            id = 1L,
            name = "चावल",
            qtyInStock = 2.0,
            unit = "kg",
            reorderThreshold = 10.0,
        )
        val okItem = ItemEntity(
            id = 2L,
            name = "चीनी",
            qtyInStock = 15.0,
            unit = "kg",
            reorderThreshold = 5.0,
        )
        every { inventory.observeAll() } returns flowOf(listOf(lowItem, okItem))

        val result = tools.execute("get_low_stock", "{}")

        assertTrue("result should include low-stock item", result.contains("चावल"))
        assertTrue("result should NOT include ok-stock item", !result.contains("चीनी"))
    }

    // ── Test 4: unknown tool returns error json ───────────────────────────────

    @Test
    fun unknownToolNameReturnsError() = runTest {
        val result = tools.execute("frobnicate_the_ledger", "{}")
        assertTrue("result should contain error key", result.contains("\"error\""))
    }

    // ── Test 5: get_customer returns not-found for unknown name ───────────────

    @Test
    fun getCustomerReturnsNotFoundWhenMissing() = runTest {
        coEvery { customers.findByName(any()) } returns null

        val result = tools.execute("get_customer", """{"name":"Nobody"}""")

        assertTrue("result should contain not found error", result.contains("not found"))
    }

    // ── Test 6: get_top_sellers uses take(8) cap ──────────────────────────────

    @Test
    fun getTopSellersCappsAtEight() = runTest {
        val rows = (1..12).map { i ->
            TopSellerRow(itemId = i.toLong(), itemName = "Item$i", qty = i.toDouble(), revenue = i * 10.0)
        }
        coEvery { getTopSellers(any(), any()) } returns rows
        every { getPnl(any(), any()) } returns flowOf(
            PnlSummary(0.0, 0.0, 0.0, 0.0, 0.0, PnlPeriod.THIS_MONTH)
        )

        val result = tools.execute("get_top_sellers", """{"period":"month"}""")

        // Count "name" occurrences — each item object has exactly one "name" field
        val itemCount = result.split("\"name\"").size - 1
        assertTrue("should have at most 8 items, got $itemCount", itemCount <= 8)
    }

    // ── Test 7: list_customers sorts by outstanding desc ──────────────────────

    @Test
    fun listCustomersSortsByOutstandingDescending() = runTest {
        val parties = listOf(
            KhataEntity(id = 1L, customerId = 1L, partyName = "Ramesh", balance = 50.0),
            KhataEntity(id = 2L, customerId = 2L, partyName = "Priya", balance = 200.0),
            KhataEntity(id = 3L, customerId = 3L, partyName = "Suresh", balance = 100.0),
        )
        every { khata.observeAll() } returns flowOf(parties)

        val result = tools.execute("list_customers", "{}")

        // Priya (200) should appear before Suresh (100) before Ramesh (50)
        val priyaIdx = result.indexOf("Priya")
        val sureshIdx = result.indexOf("Suresh")
        val rameshIdx = result.indexOf("Ramesh")
        assertTrue("Priya should be first", priyaIdx < sureshIdx && priyaIdx < rameshIdx)
        assertTrue("Suresh before Ramesh", sureshIdx < rameshIdx)
    }

    // ── Test 8: get_inventory returns all items ────────────────────────────────

    @Test
    fun getInventoryReturnsAllItems() = runTest {
        val items = listOf(
            ItemEntity(id = 1L, name = "Soap", qtyInStock = 10.0, unit = "piece", sellPrice = 20.0, costPrice = 15.0),
            ItemEntity(id = 2L, name = "Oil", qtyInStock = 5.0, unit = "litre", sellPrice = 100.0, costPrice = 80.0),
        )
        every { inventory.observeAll() } returns flowOf(items)

        val result = tools.execute("get_inventory", "{}")

        assertTrue("result contains Soap", result.contains("Soap"))
        assertTrue("result contains Oil", result.contains("Oil"))
        assertTrue("result contains sellPrice", result.contains("sellPrice"))
    }
}

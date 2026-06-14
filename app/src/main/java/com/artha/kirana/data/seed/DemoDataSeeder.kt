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

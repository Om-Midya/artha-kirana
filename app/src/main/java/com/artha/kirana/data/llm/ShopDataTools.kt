package com.artha.kirana.data.llm

import com.artha.kirana.domain.model.PnlPeriod
import com.artha.kirana.domain.repository.CustomerRepository
import com.artha.kirana.domain.repository.InventoryRepository
import com.artha.kirana.domain.repository.KhataRepository
import com.artha.kirana.domain.usecase.GetCustomerSummaryUseCase
import com.artha.kirana.domain.usecase.GetDayOfWeekTrendUseCase
import com.artha.kirana.domain.usecase.GetItemMarginsUseCase
import com.artha.kirana.domain.usecase.GetPnlSummaryUseCase
import com.artha.kirana.domain.usecase.GetTopSellersUseCase
import com.artha.kirana.domain.usecase.startFrom
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.add
import kotlinx.serialization.json.addJsonObject
import kotlinx.serialization.json.buildJsonArray
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.json.put
import kotlinx.serialization.json.putJsonArray
import kotlinx.serialization.json.putJsonObject
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Exposes 8 read-only shop-data tool specs (OpenAI function-calling format) and
 * a [execute] dispatcher that runs the matching Room use-case and returns a compact
 * JSON string suitable for feeding back to the LLM.
 *
 * All use-cases run on [Dispatchers.IO]. [execute] never throws — failures surface as
 * `{"error":"..."}` so the agent loop always gets a tool result.
 */
@Singleton
class ShopDataTools @Inject constructor(
    private val getPnl: GetPnlSummaryUseCase,
    private val getTopSellers: GetTopSellersUseCase,
    private val getCustomerSummary: GetCustomerSummaryUseCase,
    private val getDayTrend: GetDayOfWeekTrendUseCase,
    private val getItemMargins: GetItemMarginsUseCase,
    private val inventory: InventoryRepository,
    private val customers: CustomerRepository,
    private val khata: KhataRepository,
) {

    private val json = Json { ignoreUnknownKeys = true }

    // ── Tool definitions (OpenAI function-calling spec) ──────────────────────

    val definitions: JsonArray = buildJsonArray {
        add(toolDef(
            name = "get_pnl",
            description = "Get P&L summary (revenue, cost, profit, cash collected, outstanding) for a period.",
            properties = buildJsonObject {
                putJsonObject("period") {
                    put("type", "string")
                    put("enum", buildJsonArray { add("today"); add("week"); add("month") })
                    put("description", "Time period: today / week / month (default: month)")
                }
            },
            required = buildJsonArray {},
        ))
        add(toolDef(
            name = "get_top_sellers",
            description = "Get the top selling items by revenue for a period (up to 8 items).",
            properties = buildJsonObject {
                putJsonObject("period") {
                    put("type", "string")
                    put("enum", buildJsonArray { add("today"); add("week"); add("month") })
                    put("description", "Time period: today / week / month (default: month)")
                }
            },
            required = buildJsonArray {},
        ))
        add(toolDef(
            name = "get_customer",
            description = "Get lifetime value and outstanding khata balance for a named customer.",
            properties = buildJsonObject {
                putJsonObject("name") {
                    put("type", "string")
                    put("description", "Customer name to look up")
                }
            },
            required = buildJsonArray { add("name") },
        ))
        add(toolDef(
            name = "get_day_trend",
            description = "Get revenue broken down by weekday (Sun–Sat) for a period.",
            properties = buildJsonObject {
                putJsonObject("period") {
                    put("type", "string")
                    put("enum", buildJsonArray { add("today"); add("week"); add("month") })
                    put("description", "Time period: today / week / month (default: month)")
                }
            },
            required = buildJsonArray {},
        ))
        add(toolDef(
            name = "get_item_margins",
            description = "Get item margin data sorted by lowest margin first (up to 8 items).",
            properties = buildJsonObject {
                putJsonObject("period") {
                    put("type", "string")
                    put("enum", buildJsonArray { add("today"); add("week"); add("month") })
                    put("description", "Time period: today / week / month (default: month)")
                }
            },
            required = buildJsonArray {},
        ))
        add(toolDef(
            name = "get_low_stock",
            description = "Get items whose stock is below their reorder threshold.",
            properties = buildJsonObject {},
            required = buildJsonArray {},
        ))
        add(toolDef(
            name = "list_customers",
            description = "List all customers with outstanding khata balances, sorted by highest balance first.",
            properties = buildJsonObject {},
            required = buildJsonArray {},
        ))
        add(toolDef(
            name = "get_inventory",
            description = "Get the full inventory list with stock levels and prices.",
            properties = buildJsonObject {},
            required = buildJsonArray {},
        ))
    }

    // ── Dispatcher ───────────────────────────────────────────────────────────

    suspend fun execute(name: String, argumentsJson: String): String =
        withContext(Dispatchers.IO) {
            try {
                val args = parseArgs(argumentsJson)
                when (name) {
                    "get_pnl" -> execGetPnl(args)
                    "get_top_sellers" -> execGetTopSellers(args)
                    "get_customer" -> execGetCustomer(args)
                    "get_day_trend" -> execGetDayTrend(args)
                    "get_item_margins" -> execGetItemMargins(args)
                    "get_low_stock" -> execGetLowStock()
                    "list_customers" -> execListCustomers()
                    "get_inventory" -> execGetInventory()
                    else -> """{"error":"unknown tool"}"""
                }
            } catch (e: Exception) {
                """{"error":${jsonString(e.message ?: "unknown error")}}"""
            }
        }

    // ── Private executors ────────────────────────────────────────────────────

    private suspend fun execGetPnl(args: JsonObject): String {
        val period = periodOf(args)
        val summary = getPnl(period).first()
        return buildJsonObject {
            put("period", period.name)
            put("revenue", summary.grossRevenue)
            put("cost", summary.cogs)
            put("profit", summary.grossProfit)
            put("cashCollected", summary.cashCollected)
            put("outstanding", summary.totalOutstanding)
        }.toString()
    }

    private suspend fun execGetTopSellers(args: JsonObject): String {
        val period = periodOf(args)
        val start = startOf(period)
        val rows = getTopSellers(start, Long.MAX_VALUE).take(8)
        return buildJsonObject {
            put("period", period.name)
            putJsonArray("items") {
                rows.forEach { row ->
                    add(buildJsonObject {
                        put("name", row.itemName ?: "Unknown")
                        put("revenue", row.revenue)
                        put("qty", row.qty)
                    })
                }
            }
        }.toString()
    }

    private suspend fun execGetCustomer(args: JsonObject): String {
        val name = args["name"]?.jsonPrimitive?.content
            ?: return """{"error":"name is required"}"""
        val customer = customers.findByName(name)
            ?: return """{"error":"not found"}"""
        val summary = getCustomerSummary(customer.id)
        return buildJsonObject {
            put("name", customer.name)
            put("lifetimeValue", summary.lifetimeValue)
            put("outstanding", summary.outstanding)
        }.toString()
    }

    private suspend fun execGetDayTrend(args: JsonObject): String {
        val period = periodOf(args)
        val start = startOf(period)
        val buckets = getDayTrend(start, Long.MAX_VALUE)
        val days = listOf("Sun", "Mon", "Tue", "Wed", "Thu", "Fri", "Sat")
        return buildJsonObject {
            put("period", period.name)
            putJsonObject("byWeekday") {
                days.forEachIndexed { i, day -> put(day, buckets[i]) }
            }
        }.toString()
    }

    private suspend fun execGetItemMargins(args: JsonObject): String {
        val period = periodOf(args)
        val start = startOf(period)
        val rows = getItemMargins(start, Long.MAX_VALUE)
            .sortedBy { it.margin }
            .take(8)
        return buildJsonObject {
            put("period", period.name)
            putJsonArray("items") {
                rows.forEach { row ->
                    add(buildJsonObject {
                        put("name", row.itemName ?: "Unknown")
                        put("margin", row.margin)
                        put("revenue", row.revenue)
                    })
                }
            }
        }.toString()
    }

    private suspend fun execGetLowStock(): String {
        val items = inventory.observeAll().first()
            .filter { it.reorderThreshold > 0 && it.qtyInStock < it.reorderThreshold }
        return buildJsonObject {
            putJsonArray("items") {
                items.forEach { item ->
                    add(buildJsonObject {
                        put("name", item.name)
                        put("qty", item.qtyInStock)
                        put("unit", item.unit)
                        put("threshold", item.reorderThreshold)
                    })
                }
            }
        }.toString()
    }

    private suspend fun execListCustomers(): String {
        val parties = khata.observeAll().first()
            .filter { it.balance > 0 }
            .sortedByDescending { it.balance }
        return buildJsonObject {
            putJsonArray("customers") {
                parties.forEach { party ->
                    add(buildJsonObject {
                        put("name", party.partyName)
                        put("outstanding", party.balance)
                    })
                }
            }
        }.toString()
    }

    private suspend fun execGetInventory(): String {
        val items = inventory.observeAll().first()
        return buildJsonObject {
            putJsonArray("items") {
                items.forEach { item ->
                    add(buildJsonObject {
                        put("name", item.name)
                        put("qty", item.qtyInStock)
                        put("unit", item.unit)
                        put("sellPrice", item.sellPrice)
                        put("costPrice", item.costPrice)
                    })
                }
            }
        }.toString()
    }

    // ── Helpers ───────────────────────────────────────────────────────────────

    private fun parseArgs(argumentsJson: String): JsonObject =
        if (argumentsJson.isBlank() || argumentsJson == "{}") {
            buildJsonObject {}
        } else {
            json.parseToJsonElement(argumentsJson).jsonObject
        }

    private fun periodOf(args: JsonObject): PnlPeriod =
        when (args["period"]?.jsonPrimitive?.content?.lowercase()) {
            "today" -> PnlPeriod.TODAY
            "week" -> PnlPeriod.THIS_WEEK
            else -> PnlPeriod.THIS_MONTH
        }

    private fun startOf(period: PnlPeriod): Long =
        period.startFrom(System.currentTimeMillis())

    /** Escape a string for embedding in a hand-built JSON literal (error paths only). */
    private fun jsonString(value: String): String =
        "\"${value.replace("\\", "\\\\").replace("\"", "\\\"")}\""

    // ── Builder helper ────────────────────────────────────────────────────────

    private fun toolDef(
        name: String,
        description: String,
        properties: JsonObject,
        required: JsonArray,
    ): JsonObject = buildJsonObject {
        put("type", "function")
        putJsonObject("function") {
            put("name", name)
            put("description", description)
            putJsonObject("parameters") {
                put("type", "object")
                put("properties", properties)
                put("required", required)
            }
        }
    }
}

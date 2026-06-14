package com.artha.kirana.domain.model

import kotlinx.serialization.json.Json
import kotlinx.serialization.json.doubleOrNull
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlin.math.roundToLong

/**
 * Pure mapper: given a ShopDataTools tool name and its JSON result string, produces an
 * [AgentVisual] the chat UI can render, or null if no visual is appropriate.
 *
 * Stateless object — no I/O, no coroutines, safe to call on any thread.
 */
object AgentVisuals {

    private val json = Json { ignoreUnknownKeys = true; isLenient = true }

    /**
     * Returns an [AgentVisual] for the given tool result, or null if:
     * - the tool has no visual (e.g. get_inventory),
     * - the result is an error payload,
     * - the result list is empty, or
     * - JSON parsing fails for any reason.
     */
    fun fromTool(toolName: String, resultJson: String): AgentVisual? = try {
        val root = json.parseToJsonElement(resultJson).jsonObject
        when (toolName) {
            "get_top_sellers" -> topSellers(root)
            "get_item_margins" -> itemMargins(root)
            "get_day_trend" -> dayTrend(root)
            "get_pnl" -> pnl(root)
            "get_customer" -> customer(root)
            "get_low_stock" -> lowStock(root)
            "list_customers" -> listCustomers(root)
            "get_inventory" -> null  // too long for a card
            else -> null
        }
    } catch (_: Throwable) { null }

    // ── individual mappers ───────────────────────────────────────────────────

    private fun topSellers(root: kotlinx.serialization.json.JsonObject): AgentVisual? {
        val items = root["items"]?.jsonArray ?: return null
        val bars = items.take(6).mapNotNull { el ->
            val obj = el.jsonObject
            val name = obj["name"]?.jsonPrimitive?.content ?: return@mapNotNull null
            val revenue = obj["revenue"]?.jsonPrimitive?.doubleOrNull ?: return@mapNotNull null
            Bar(label = name, value = revenue)
        }
        if (bars.isEmpty()) return null
        return AgentVisual.BarChart(title = "टॉप आइटम · TOP SELLERS", bars = bars)
    }

    private fun itemMargins(root: kotlinx.serialization.json.JsonObject): AgentVisual? {
        val items = root["items"]?.jsonArray ?: return null
        val bars = items.take(6).mapNotNull { el ->
            val obj = el.jsonObject
            val name = obj["name"]?.jsonPrimitive?.content ?: return@mapNotNull null
            val margin = obj["margin"]?.jsonPrimitive?.doubleOrNull ?: return@mapNotNull null
            Bar(label = name, value = margin)
        }
        if (bars.isEmpty()) return null
        return AgentVisual.BarChart(title = "मार्जिन · MARGIN/ITEM", bars = bars)
    }

    private fun dayTrend(root: kotlinx.serialization.json.JsonObject): AgentVisual? {
        val byWeekday = root["byWeekday"]?.jsonObject ?: return null
        val dayOrder = listOf("Sun", "Mon", "Tue", "Wed", "Thu", "Fri", "Sat")
        val bars = dayOrder.map { day ->
            val value = byWeekday[day]?.jsonPrimitive?.doubleOrNull ?: 0.0
            Bar(label = day, value = value)
        }
        val maxVal = bars.maxOfOrNull { it.value } ?: 0.0
        if (maxVal <= 0.0) return null
        val highlighted = bars.map { it.copy(highlight = it.value == maxVal) }
        return AgentVisual.BarChart(title = "दिन के हिसाब · BY DAY", bars = highlighted)
    }

    private fun pnl(root: kotlinx.serialization.json.JsonObject): AgentVisual {
        val period = root["period"]?.jsonPrimitive?.content ?: "TODAY"
        val revenue = root["revenue"]?.jsonPrimitive?.doubleOrNull ?: 0.0
        val profit = root["profit"]?.jsonPrimitive?.doubleOrNull ?: 0.0
        val cashCollected = root["cashCollected"]?.jsonPrimitive?.doubleOrNull ?: 0.0
        val outstanding = root["outstanding"]?.jsonPrimitive?.doubleOrNull ?: 0.0
        val periodShort = periodShort(period)
        return AgentVisual.Stats(
            title = "मुनाफ़ा · P&L ($periodShort)",
            rows = listOf(
                Stat("कमाई · REVENUE", rupees(revenue), StatTone.IN),
                Stat("मुनाफ़ा · PROFIT", rupees(profit), if (profit >= 0) StatTone.IN else StatTone.OUT),
                Stat("कैश · CASH IN", rupees(cashCollected), StatTone.IN),
                Stat("उधार · UDHAAR", rupees(outstanding), StatTone.UDHAAR),
            ),
        )
    }

    private fun customer(root: kotlinx.serialization.json.JsonObject): AgentVisual? {
        if (root.containsKey("error")) return null
        val name = root["name"]?.jsonPrimitive?.content ?: return null
        val lifetimeValue = root["lifetimeValue"]?.jsonPrimitive?.doubleOrNull ?: 0.0
        val outstanding = root["outstanding"]?.jsonPrimitive?.doubleOrNull ?: 0.0
        return AgentVisual.Stats(
            title = name,
            rows = listOf(
                Stat("कुल खरीद · LIFETIME", rupees(lifetimeValue), StatTone.NEUTRAL),
                Stat("बकाया · OUTSTANDING", rupees(outstanding), StatTone.UDHAAR),
            ),
        )
    }

    private fun lowStock(root: kotlinx.serialization.json.JsonObject): AgentVisual? {
        val items = root["items"]?.jsonArray ?: return null
        val stats = items.take(8).mapNotNull { el ->
            val obj = el.jsonObject
            val name = obj["name"]?.jsonPrimitive?.content ?: return@mapNotNull null
            val qty = obj["qty"]?.jsonPrimitive?.doubleOrNull ?: 0.0
            val unit = obj["unit"]?.jsonPrimitive?.content ?: ""
            Stat(label = name, value = "${trimQty(qty)} $unit".trim(), tone = StatTone.OUT)
        }
        if (stats.isEmpty()) return null
        return AgentVisual.Stats(title = "कम स्टॉक · LOW STOCK", rows = stats)
    }

    private fun listCustomers(root: kotlinx.serialization.json.JsonObject): AgentVisual? {
        val customers = root["customers"]?.jsonArray ?: return null
        val stats = customers.take(6).mapNotNull { el ->
            val obj = el.jsonObject
            val name = obj["name"]?.jsonPrimitive?.content ?: return@mapNotNull null
            val outstanding = obj["outstanding"]?.jsonPrimitive?.doubleOrNull ?: 0.0
            Stat(label = name, value = rupees(outstanding), tone = StatTone.UDHAAR)
        }
        if (stats.isEmpty()) return null
        return AgentVisual.Stats(title = "बकाया · WHO OWES", rows = stats)
    }

    // ── helpers ──────────────────────────────────────────────────────────────

    private fun rupees(d: Double): String = "₹${d.roundToLong()}"

    private fun trimQty(d: Double): String =
        if (d == d.toLong().toDouble()) d.toLong().toString() else d.toString()

    private fun periodShort(period: String): String = when (period) {
        "THIS_MONTH" -> "महीना"
        "THIS_WEEK" -> "हफ्ता"
        else -> "आज"
    }
}

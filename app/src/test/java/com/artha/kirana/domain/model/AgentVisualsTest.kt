package com.artha.kirana.domain.model

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class AgentVisualsTest {
    @Test fun topSellers_returnsBarChart() {
        val v = AgentVisuals.fromTool("get_top_sellers", """{"period":"THIS_MONTH","items":[{"name":"Parle-G","revenue":500.0,"qty":50.0},{"name":"Rice","revenue":300.0,"qty":4.0}]}""")
        assertTrue(v is AgentVisual.BarChart)
        v as AgentVisual.BarChart
        assertEquals(2, v.bars.size)
        assertEquals("Parle-G", v.bars[0].label)
        assertEquals(500.0, v.bars[0].value, 0.0)
    }
    @Test fun pnl_returnsStatsWithProfit() {
        val v = AgentVisuals.fromTool("get_pnl", """{"period":"TODAY","revenue":3395.0,"cost":62062.0,"profit":-58667.0,"cashCollected":2350.0,"outstanding":589.0}""")
        assertTrue(v is AgentVisual.Stats)
        v as AgentVisual.Stats
        assertTrue(v.rows.any { it.value.contains("58667") || it.value.contains("58,667") })
    }
    @Test fun dayTrend_barChartHighlightsMax() {
        val v = AgentVisuals.fromTool("get_day_trend", """{"period":"THIS_MONTH","byWeekday":{"Sun":10.0,"Mon":5.0,"Tue":0.0,"Wed":0.0,"Thu":0.0,"Fri":40.0,"Sat":0.0}}""")
        assertTrue(v is AgentVisual.BarChart)
        v as AgentVisual.BarChart
        assertEquals(7, v.bars.size)
        assertEquals("Fri", v.bars.first { it.highlight }.label)
    }
    @Test fun customerError_returnsNull() {
        assertNull(AgentVisuals.fromTool("get_customer", """{"error":"not found"}"""))
    }
    @Test fun unknownTool_returnsNull() {
        assertNull(AgentVisuals.fromTool("get_inventory_blah", "{}"))
    }
}

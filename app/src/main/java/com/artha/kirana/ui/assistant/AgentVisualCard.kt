package com.artha.kirana.ui.assistant

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.artha.kirana.domain.model.AgentVisual
import com.artha.kirana.domain.model.Bar
import com.artha.kirana.domain.model.Stat
import com.artha.kirana.domain.model.StatTone
import com.artha.kirana.ui.theme.Card
import com.artha.kirana.ui.theme.HazardWhite
import com.artha.kirana.ui.theme.HotPink
import com.artha.kirana.ui.theme.Kicker
import com.artha.kirana.ui.theme.Mint
import com.artha.kirana.ui.theme.Rule
import com.artha.kirana.ui.theme.TextMuted
import com.artha.kirana.ui.theme.TileYellow
import kotlin.math.roundToLong

@Composable
fun AgentVisualCard(visual: AgentVisual, modifier: Modifier = Modifier) {
    when (visual) {
        is AgentVisual.BarChart -> BarChartCard(visual, modifier)
        is AgentVisual.Stats -> StatsCard(visual, modifier)
    }
}

@Composable
private fun BarChartCard(chart: AgentVisual.BarChart, modifier: Modifier = Modifier) {
    Card(modifier = modifier.padding(vertical = 4.dp)) {
        Kicker(chart.title, color = Mint)
        Spacer(Modifier.height(10.dp))
        Rule(color = Mint, thickness = 1)
        Spacer(Modifier.height(12.dp))
        val maxValue = chart.bars.maxOfOrNull { it.value }?.coerceAtLeast(1.0) ?: 1.0
        Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
            chart.bars.forEach { bar ->
                BarRow(bar = bar, maxValue = maxValue)
            }
        }
    }
}

@Composable
private fun BarRow(bar: Bar, maxValue: Double) {
    val barColor = if (bar.highlight) TileYellow else Mint
    val fraction = (bar.value / maxValue).toFloat().coerceIn(0.02f, 1f)
    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = Modifier.fillMaxWidth(),
    ) {
        // Label column — fixed width
        Text(
            text = bar.label,
            style = MaterialTheme.typography.bodySmall,
            color = HazardWhite,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.width(110.dp),
        )
        Spacer(Modifier.width(8.dp))
        // Bar track
        Box(
            modifier = Modifier
                .weight(1f)
                .height(18.dp),
        ) {
            Box(
                modifier = Modifier
                    .fillMaxWidth(fraction)
                    .fillMaxHeight()
                    .background(barColor, RoundedCornerShape(3.dp)),
            )
        }
        Spacer(Modifier.width(8.dp))
        // Value column — fixed width, end-aligned
        Text(
            text = "₹${bar.value.roundToLong()}",
            style = MaterialTheme.typography.labelMedium,
            color = barColor,
            textAlign = TextAlign.End,
            modifier = Modifier.width(64.dp),
        )
    }
}

@Composable
private fun StatsCard(stats: AgentVisual.Stats, modifier: Modifier = Modifier) {
    Card(modifier = modifier.padding(vertical = 4.dp)) {
        Kicker(stats.title, color = Mint)
        Spacer(Modifier.height(10.dp))
        Rule(color = Mint, thickness = 1)
        Spacer(Modifier.height(12.dp))
        Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
            stats.rows.forEach { stat ->
                StatRow(stat)
            }
        }
    }
}

@Composable
private fun StatRow(stat: Stat) {
    val valueColor: Color = when (stat.tone) {
        StatTone.NEUTRAL -> HazardWhite
        StatTone.IN -> Mint
        StatTone.OUT -> HotPink
        StatTone.UDHAAR -> TileYellow
    }
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            text = stat.label.uppercase(),
            style = MaterialTheme.typography.labelMedium,
            color = TextMuted,
            modifier = Modifier.weight(1f),
        )
        Text(
            text = stat.value,
            style = MaterialTheme.typography.titleLarge,
            fontWeight = FontWeight.Bold,
            color = valueColor,
        )
    }
}

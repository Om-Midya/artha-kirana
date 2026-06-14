package com.artha.kirana.ui.pnl

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.artha.kirana.domain.model.PnlPeriod
import com.artha.kirana.domain.model.PnlSummary
import com.artha.kirana.ui.theme.Canvas
import com.artha.kirana.ui.theme.Card
import com.artha.kirana.ui.theme.HotPink
import com.artha.kirana.ui.theme.Kicker
import com.artha.kirana.ui.theme.Mint
import com.artha.kirana.ui.theme.Rule
import com.artha.kirana.ui.theme.SectionTitle
import com.artha.kirana.ui.theme.TextMuted
import com.artha.kirana.ui.theme.TileYellow
import com.artha.kirana.ui.theme.Ultraviolet
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
            .background(Canvas)
            .padding(horizontal = 16.dp),
    ) {
        Spacer(Modifier.height(12.dp))

        // Screen title kicker
        Kicker("नफा-नुकसान · P&L", color = Ultraviolet)
        Spacer(Modifier.height(10.dp))
        Rule(color = Ultraviolet)
        Spacer(Modifier.height(16.dp))

        // Period tab strip — mono UPPERCASE, selected = Mint, unselected = TextMuted
        Row(
            Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            tabs.forEach { (p, label) ->
                val selected = period == p
                Text(
                    text = label.uppercase(),
                    style = MaterialTheme.typography.labelLarge,
                    color = if (selected) Mint else TextMuted,
                    modifier = Modifier
                        .clickable { vm.selectPeriod(p) }
                        .padding(vertical = 8.dp, horizontal = 4.dp),
                )
            }
        }
        Spacer(Modifier.height(16.dp))

        val s = summary
        if (s == null) {
            Card {
                Text(
                    "Loading…",
                    style = MaterialTheme.typography.bodyMedium,
                    color = TextMuted,
                )
            }
        } else {
            MetricCards(s)
            Spacer(Modifier.height(20.dp))
            SectionTitle("LAST 7 DAYS · REVENUE")
            Spacer(Modifier.height(10.dp))
            if (daily.any { it.amount > 0 }) {
                ProfitChart(daily = daily, modifier = Modifier.fillMaxWidth().height(200.dp))
            } else {
                Card {
                    Text(
                        "No revenue in the last 7 days.".uppercase(),
                        style = MaterialTheme.typography.labelSmall,
                        color = TextMuted,
                    )
                }
            }
        }
    }
}

@Composable
private fun MetricCards(s: PnlSummary) {
    val profitColor = if (s.grossProfit >= 0) Mint else HotPink

    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        // Hero profit card
        Card(feature = true) {
            Kicker("GROSS PROFIT", color = profitColor)
            Spacer(Modifier.height(10.dp))
            Text(
                formatRupees(s.grossProfit),
                style = MaterialTheme.typography.displayMedium,
                color = profitColor,
            )
        }

        // Two-column secondary metrics
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            // Revenue = money IN → Mint
            MetricMini(
                label = "REVENUE",
                amount = s.grossRevenue,
                amountColor = Mint,
                modifier = Modifier.weight(1f),
            )
            // COGS = money OUT → HotPink
            MetricMini(
                label = "COGS",
                amount = s.cogs,
                amountColor = HotPink,
                modifier = Modifier.weight(1f),
            )
        }
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            // Cash collected = money IN → Mint
            MetricMini(
                label = "CASH IN",
                amount = s.cashCollected,
                amountColor = Mint,
                modifier = Modifier.weight(1f),
            )
            // Outstanding udhaar → TileYellow
            MetricMini(
                label = "UDHAAR",
                amount = s.totalOutstanding,
                amountColor = TileYellow,
                modifier = Modifier.weight(1f),
            )
        }
    }
}

@Composable
private fun MetricMini(
    label: String,
    amount: Double,
    amountColor: Color,
    modifier: Modifier = Modifier,
) {
    Card(modifier = modifier) {
        SectionTitle(label)
        Spacer(Modifier.height(6.dp))
        Text(
            formatRupees(amount),
            style = MaterialTheme.typography.displaySmall,
            color = amountColor,
        )
    }
}

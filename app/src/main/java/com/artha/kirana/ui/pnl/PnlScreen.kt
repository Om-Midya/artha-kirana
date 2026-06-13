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
import androidx.compose.material3.CardDefaults
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
            colors = CardDefaults.cardColors(
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

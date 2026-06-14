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
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.artha.kirana.data.db.entity.KhataEntity
import com.artha.kirana.ui.theme.Card
import com.artha.kirana.ui.theme.CardTone
import com.artha.kirana.ui.theme.HazardWhite
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
fun KhataScreen(
    onParty: (Long) -> Unit,
    vm: KhataViewModel = hiltViewModel(),
) {
    val parties by vm.parties.collectAsStateWithLifecycle()
    val total by vm.totalOutstanding.collectAsStateWithLifecycle()

    Column(
        Modifier
            .fillMaxSize()
            .padding(horizontal = 16.dp),
    ) {
        Spacer(Modifier.height(12.dp))

        // Screen wordmark row
        Row(
            Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text("KHATA", style = MaterialTheme.typography.displaySmall, color = HazardWhite)
        }
        Spacer(Modifier.height(10.dp))
        Rule(color = TileYellow)
        Spacer(Modifier.height(16.dp))

        // Hero card — total outstanding in TileYellow (they owe us = UDHAAR)
        Card(feature = true, tone = CardTone.YELLOW) {
            Kicker("कुल उधार · TOTAL OUTSTANDING", color = Ultraviolet)
            Spacer(Modifier.height(10.dp))
            Text(
                formatRupees(total),
                style = MaterialTheme.typography.displayLarge,
                color = Ultraviolet,
            )
            Spacer(Modifier.height(4.dp))
            SectionTitle("AMOUNT CUSTOMERS OWE YOU", color = Ultraviolet)
        }

        Spacer(Modifier.height(24.dp))
        SectionTitle("PARTIES")
        Spacer(Modifier.height(10.dp))

        if (parties.isEmpty()) {
            Card {
                Text(
                    "अभी कोई उधार नहीं · No credit entries yet.",
                    style = MaterialTheme.typography.bodyMedium,
                    color = TextMuted,
                )
            }
        } else {
            LazyColumn(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                items(parties, key = { it.id }) { party ->
                    PartyRow(party, onClick = { onParty(party.id) })
                }
            }
        }
    }
}

@Composable
private fun PartyRow(party: KhataEntity, onClick: () -> Unit) {
    Card(modifier = Modifier.clickable(onClick = onClick)) {
        Row(
            Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Column(Modifier.weight(1f)) {
                Text(
                    party.partyName,
                    style = MaterialTheme.typography.titleMedium,
                    color = HazardWhite,
                )
                Spacer(Modifier.height(2.dp))
                Text(
                    balanceLabel(party.balance).uppercase(),
                    style = MaterialTheme.typography.labelSmall,
                    color = balanceColor(party.balance),
                )
            }
            Text(
                formatRupees(party.balance),
                style = MaterialTheme.typography.titleLarge,
                color = balanceColor(party.balance),
            )
        }
    }
}

/** UDHAAR (they owe us, positive) → TileYellow. We owe them (rare, negative) → HotPink. */
private fun balanceColor(balance: Double) = when {
    balance > 0 -> TileYellow
    balance < 0 -> HotPink
    else -> Mint
}

private fun balanceLabel(balance: Double) = when {
    balance > 0 -> "Udhaar · owes you"
    balance < 0 -> "You owe"
    else -> "Settled"
}

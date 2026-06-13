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

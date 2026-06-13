package com.artha.kirana.ui.home

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
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.artha.kirana.data.db.entity.SaleEntity
import com.artha.kirana.domain.model.SaleEntry
import com.artha.kirana.ui.common.EditableEntryCard
import com.artha.kirana.ui.theme.AccentGreen
import com.artha.kirana.ui.theme.AccentRed
import com.artha.kirana.util.formatRupees

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun HomeScreen(vm: HomeViewModel = hiltViewModel()) {
    val revenue by vm.todayRevenue.collectAsStateWithLifecycle()
    val sales by vm.recentSales.collectAsStateWithLifecycle()
    var editing by remember { mutableStateOf<SaleEntity?>(null) }
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)

    Column(
        Modifier
            .fillMaxSize()
            .padding(16.dp),
    ) {
        Card(
            Modifier.fillMaxWidth(),
            colors = CardDefaults.cardColors(
                containerColor = MaterialTheme.colorScheme.primary,
                contentColor = MaterialTheme.colorScheme.onPrimary,
            ),
        ) {
            Column(Modifier.padding(20.dp)) {
                Text("आज की बिक्री · Today's sales", style = MaterialTheme.typography.titleMedium)
                Spacer(Modifier.height(6.dp))
                Text(
                    formatRupees(revenue),
                    style = MaterialTheme.typography.displaySmall,
                    fontWeight = FontWeight.Bold,
                )
            }
        }

        Spacer(Modifier.height(20.dp))
        Text("Recent entries", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
        Spacer(Modifier.height(8.dp))

        if (sales.isEmpty()) {
            Text(
                "No sales yet today. Tap “New Sale” to add one.",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        } else {
            LazyColumn(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                items(sales) { sale -> SaleRow(sale, onEdit = { editing = it }) }
            }
        }
    }

    val sale = editing
    if (sale != null) {
        var draft by remember(sale.id) { mutableStateOf(sale.toEntry()) }
        ModalBottomSheet(onDismissRequest = { editing = null }, sheetState = sheetState) {
            Column(Modifier.padding(start = 16.dp, end = 16.dp, bottom = 24.dp)) {
                Text(
                    "बदलें · Edit entry",
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.SemiBold,
                )
                Spacer(Modifier.height(12.dp))
                EditableEntryCard(draft) { draft = it }
                Spacer(Modifier.height(12.dp))
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    OutlinedButton(onClick = { editing = null }) { Text("Cancel") }
                    Button(onClick = {
                        vm.editSale(sale, draft)
                        editing = null
                    }) { Text("Save") }
                }
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun SaleRow(sale: SaleEntity, onEdit: (SaleEntity) -> Unit) {
    Card(onClick = { onEdit(sale) }, modifier = Modifier.fillMaxWidth()) {
        Row(
            Modifier
                .fillMaxWidth()
                .padding(16.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Column(Modifier.weight(1f)) {
                Text(
                    sale.itemName ?: "Sale",
                    style = MaterialTheme.typography.bodyLarge,
                    fontWeight = FontWeight.Medium,
                )
                Text(
                    saleSubtitle(sale),
                    style = MaterialTheme.typography.bodySmall,
                    color = when (sale.type) {
                        "credit" -> AccentRed
                        "repayment" -> AccentGreen
                        else -> MaterialTheme.colorScheme.onSurfaceVariant
                    },
                )
            }
            Text(
                formatRupees(sale.amount),
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold,
            )
        }
    }
}

/** "Cash sale" / "Credit · Ramesh" / "Repayment · Ramesh". Customer appended only when present. */
private fun saleSubtitle(sale: SaleEntity): String {
    val typeLabel = when (sale.type) {
        "cash" -> "Cash sale"
        "credit" -> "Credit"
        "repayment" -> "Repayment"
        else -> sale.type.replaceFirstChar { it.uppercase() }
    }
    return sale.party?.let { "$typeLabel · $it" } ?: typeLabel
}

/** Map a saved sale to the editable [SaleEntry] form for the edit sheet. */
private fun SaleEntity.toEntry(): SaleEntry = SaleEntry(
    item = itemName,
    qty = when {
        qtySold == 0.0 -> null
        qtySold % 1.0 == 0.0 -> qtySold.toLong().toString()
        else -> qtySold.toString()
    },
    amount = amount,
    type = type,
    party = party,
)

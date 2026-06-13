package com.artha.kirana.ui.khata

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
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.artha.kirana.data.db.entity.KhataTransactionEntity
import com.artha.kirana.ui.theme.AccentGreen
import com.artha.kirana.ui.theme.AccentRed
import com.artha.kirana.util.formatRupees
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

@Composable
fun KhataPartyDetail(vm: KhataPartyDetailViewModel = hiltViewModel()) {
    val party by vm.party.collectAsStateWithLifecycle()
    val txns by vm.transactions.collectAsStateWithLifecycle()
    var showPayDialog by remember { mutableStateOf(false) }

    val p = party
    if (p == null) {
        Text(
            "Loading…",
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.padding(16.dp),
        )
        return
    }

    Column(
        Modifier
            .fillMaxSize()
            .padding(16.dp),
    ) {
        Card(Modifier.fillMaxWidth()) {
            Column(Modifier.padding(20.dp)) {
                Text(p.partyName, style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold)
                Spacer(Modifier.height(6.dp))
                Text(
                    "Balance: ${formatRupees(p.balance)}",
                    style = MaterialTheme.typography.titleMedium,
                    color = if (p.balance > 0) AccentRed else AccentGreen,
                )
            }
        }
        Spacer(Modifier.height(12.dp))
        Button(onClick = { showPayDialog = true }, modifier = Modifier.fillMaxWidth()) {
            Text("Record payment")
        }
        Spacer(Modifier.height(16.dp))
        Text("History", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
        Spacer(Modifier.height(8.dp))

        if (txns.isEmpty()) {
            Text("No transactions yet.", color = MaterialTheme.colorScheme.onSurfaceVariant)
        } else {
            LazyColumn(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                items(txns, key = { it.id }) { TxnRow(it) }
            }
        }
    }

    if (showPayDialog) {
        var amount by remember { mutableStateOf("") }
        AlertDialog(
            onDismissRequest = { showPayDialog = false },
            title = { Text("Record payment") },
            text = {
                OutlinedTextField(
                    value = amount,
                    onValueChange = { amount = it },
                    label = { Text("Amount ₹") },
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                )
            },
            confirmButton = {
                TextButton(onClick = {
                    val amt = amount.toDoubleOrNull()
                    val name = party?.partyName
                    if (amt != null && amt > 0 && name != null) vm.recordPayment(name, amt)
                    showPayDialog = false
                }) { Text("Save") }
            },
            dismissButton = { TextButton(onClick = { showPayDialog = false }) { Text("Cancel") } },
        )
    }
}

@Composable
private fun TxnRow(txn: KhataTransactionEntity) {
    Card(Modifier.fillMaxWidth()) {
        Row(
            Modifier.fillMaxWidth().padding(16.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
        ) {
            Column {
                Text(
                    if (txn.type == "repayment") "Repayment" else "Credit",
                    fontWeight = FontWeight.Medium,
                    color = if (txn.type == "repayment") AccentGreen else AccentRed,
                )
                Text(
                    SimpleDateFormat("d MMM, h:mm a", Locale.getDefault()).format(Date(txn.timestamp)),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            Text(formatRupees(txn.amount), fontWeight = FontWeight.Bold)
        }
    }
}

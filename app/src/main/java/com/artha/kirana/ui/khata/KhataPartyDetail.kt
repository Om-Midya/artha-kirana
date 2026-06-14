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
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.artha.kirana.data.db.entity.KhataTransactionEntity
import com.artha.kirana.ui.theme.Card
import com.artha.kirana.ui.theme.CardTone
import com.artha.kirana.ui.theme.HazardWhite
import com.artha.kirana.ui.theme.HotPink
import com.artha.kirana.ui.theme.Kicker
import com.artha.kirana.ui.theme.Line
import com.artha.kirana.ui.theme.Mint
import com.artha.kirana.ui.theme.PrimaryButton
import com.artha.kirana.ui.theme.Rule
import com.artha.kirana.ui.theme.SectionTitle
import com.artha.kirana.ui.theme.Slate
import com.artha.kirana.ui.theme.TextMuted
import com.artha.kirana.ui.theme.TileYellow
import com.artha.kirana.ui.theme.Ultraviolet
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
            color = TextMuted,
            modifier = Modifier.padding(16.dp),
        )
        return
    }

    Column(
        Modifier
            .fillMaxSize()
            .padding(horizontal = 16.dp),
    ) {
        Spacer(Modifier.height(12.dp))

        // Party name wordmark + accent bar
        Text(
            p.partyName.uppercase(),
            style = MaterialTheme.typography.displaySmall,
            color = HazardWhite,
        )
        Spacer(Modifier.height(10.dp))
        Rule(color = TileYellow)
        Spacer(Modifier.height(16.dp))

        // Balance hero card — TileYellow when they owe us, HotPink if we owe them
        val balColor = when {
            p.balance > 0 -> Ultraviolet
            p.balance < 0 -> HotPink
            else -> Mint
        }
        val tileBalTone = when {
            p.balance > 0 -> CardTone.YELLOW
            p.balance < 0 -> CardTone.NONE
            else -> CardTone.MINT
        }
        Card(feature = true, tone = tileBalTone) {
            Kicker(
                if (p.balance > 0) "उधार · OUTSTANDING BALANCE"
                else if (p.balance < 0) "हमारा उधार · YOU OWE THEM"
                else "SETTLED",
                color = balColor,
            )
            Spacer(Modifier.height(10.dp))
            Text(
                formatRupees(p.balance),
                style = MaterialTheme.typography.displayMedium,
                color = balColor,
            )
        }

        Spacer(Modifier.height(16.dp))

        // Record payment CTA
        PrimaryButton(
            text = "Record Payment",
            onClick = { showPayDialog = true },
            modifier = Modifier.fillMaxWidth(),
        )

        Spacer(Modifier.height(24.dp))
        SectionTitle("TRANSACTION HISTORY")
        Spacer(Modifier.height(10.dp))

        if (txns.isEmpty()) {
            Card {
                Text(
                    "कोई लेनदेन नहीं · No transactions yet.",
                    style = MaterialTheme.typography.bodyMedium,
                    color = TextMuted,
                )
            }
        } else {
            LazyColumn(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                items(txns, key = { it.id }) { TxnRow(it) }
            }
        }
    }

    if (showPayDialog) {
        var amount by remember { mutableStateOf("") }
        AlertDialog(
            onDismissRequest = { showPayDialog = false },
            title = {
                Kicker("भुगतान दर्ज करें · RECORD PAYMENT", color = Mint)
            },
            text = {
                OutlinedTextField(
                    value = amount,
                    onValueChange = { amount = it },
                    label = {
                        Text(
                            "AMOUNT ₹",
                            style = MaterialTheme.typography.labelMedium,
                            color = TextMuted,
                        )
                    },
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                    textStyle = MaterialTheme.typography.titleMedium.copy(color = HazardWhite),
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedBorderColor = Mint,
                        unfocusedBorderColor = Line,
                        cursorColor = Mint,
                        focusedContainerColor = Slate,
                        unfocusedContainerColor = Slate,
                    ),
                )
            },
            confirmButton = {
                TextButton(onClick = {
                    val amt = amount.toDoubleOrNull()
                    val name = party?.partyName
                    if (amt != null && amt > 0 && name != null) vm.recordPayment(name, amt)
                    showPayDialog = false
                }) {
                    Text("SAVE".uppercase(), style = MaterialTheme.typography.labelLarge, color = Mint)
                }
            },
            dismissButton = {
                TextButton(onClick = { showPayDialog = false }) {
                    Text("CANCEL".uppercase(), style = MaterialTheme.typography.labelLarge, color = TextMuted)
                }
            },
            containerColor = androidx.compose.ui.graphics.Color(0xFF131313),
            titleContentColor = Mint,
            textContentColor = HazardWhite,
        )
    }
}

@Composable
private fun TxnRow(txn: KhataTransactionEntity) {
    val isRepayment = txn.type == "repayment"
    // Repayment = money in → Mint; Credit = they owe more → TileYellow
    val typeColor = if (isRepayment) Mint else TileYellow
    val typeLabel = if (isRepayment) "REPAYMENT" else "CREDIT"

    Card {
        Row(
            Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
        ) {
            Column(Modifier.weight(1f)) {
                Text(
                    typeLabel,
                    style = MaterialTheme.typography.labelMedium,
                    color = typeColor,
                )
                Spacer(Modifier.height(2.dp))
                Text(
                    SimpleDateFormat("d MMM, h:mm a", Locale.getDefault())
                        .format(Date(txn.timestamp))
                        .uppercase(),
                    style = MaterialTheme.typography.labelSmall,
                    color = TextMuted,
                )
                txn.note?.let { note ->
                    Spacer(Modifier.height(2.dp))
                    Text(
                        note,
                        style = MaterialTheme.typography.bodySmall,
                        color = TextMuted,
                    )
                }
            }
            Text(
                formatRupees(txn.amount),
                style = MaterialTheme.typography.titleLarge,
                color = typeColor,
            )
        }
    }
}

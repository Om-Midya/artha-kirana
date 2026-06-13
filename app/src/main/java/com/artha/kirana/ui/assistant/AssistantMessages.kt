package com.artha.kirana.ui.assistant

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.artha.kirana.domain.model.PnlSummary
import com.artha.kirana.domain.model.SaleEntry
import com.artha.kirana.ui.common.EditableEntryCard
import com.artha.kirana.ui.theme.AccentGreen
import com.artha.kirana.ui.theme.BrandGold

@Composable
fun UserBubble(text: String) {
    Box(Modifier.fillMaxWidth(), contentAlignment = Alignment.CenterEnd) {
        Surface(
            color = BrandGold,
            shape = RoundedCornerShape(16.dp),
            modifier = Modifier.padding(vertical = 4.dp),
        ) {
            Text(text, Modifier.padding(horizontal = 14.dp, vertical = 10.dp))
        }
    }
}

@Composable
fun ReplyBubble(text: String) {
    Box(Modifier.fillMaxWidth(), contentAlignment = Alignment.CenterStart) {
        Surface(
            shape = RoundedCornerShape(16.dp),
            tonalElevation = 2.dp,
            modifier = Modifier.padding(vertical = 4.dp),
        ) {
            Text(text, Modifier.padding(horizontal = 14.dp, vertical = 10.dp))
        }
    }
}

/** Sale confirm card. Editable while Pending; collapses to a status line once acted on. */
@Composable
fun SaleDraftBubble(
    entries: List<SaleEntry>,
    status: DraftStatus,
    onConfirm: (List<SaleEntry>) -> Unit,
    onCancel: () -> Unit,
) {
    if (status != DraftStatus.Pending) {
        ReplyBubble(if (status == DraftStatus.Confirmed) "✓ बिक्री दर्ज" else "रद्द किया")
        return
    }
    var edited by remember { mutableStateOf(entries) }
    Card(Modifier.fillMaxWidth().padding(vertical = 4.dp)) {
        Column(Modifier.padding(12.dp)) {
            Text("इसे दर्ज करें?", fontWeight = FontWeight.Bold)
            Spacer(Modifier.height(8.dp))
            edited.forEachIndexed { i, e ->
                EditableEntryCard(e) { updated -> edited = edited.toMutableList().also { it[i] = updated } }
                Spacer(Modifier.height(8.dp))
            }
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                OutlinedButton(onClick = onCancel) { Text("Cancel") }
                Button(onClick = { onConfirm(edited) }) { Text("Confirm ✓") }
            }
        }
    }
}

/** Payment confirm card. Editable party/amount while Pending. */
@Composable
fun PaymentDraftBubble(
    party: String?,
    amount: Double?,
    status: DraftStatus,
    onConfirm: (String?, Double?) -> Unit,
    onCancel: () -> Unit,
) {
    if (status != DraftStatus.Pending) {
        ReplyBubble(if (status == DraftStatus.Confirmed) "✓ भुगतान दर्ज" else "रद्द किया")
        return
    }
    var partyText by remember { mutableStateOf(party ?: "") }
    var amountText by remember { mutableStateOf(amount?.toLong()?.toString() ?: "") }
    Card(Modifier.fillMaxWidth().padding(vertical = 4.dp)) {
        Column(Modifier.padding(12.dp)) {
            Text("भुगतान दर्ज करें?", fontWeight = FontWeight.Bold)
            Spacer(Modifier.height(8.dp))
            androidx.compose.material3.OutlinedTextField(
                value = partyText,
                onValueChange = { partyText = it },
                label = { Text("Party · ग्राहक") },
                singleLine = true,
                modifier = Modifier.fillMaxWidth(),
            )
            Spacer(Modifier.height(8.dp))
            androidx.compose.material3.OutlinedTextField(
                value = amountText,
                onValueChange = { amountText = it },
                label = { Text("₹ Amount") },
                singleLine = true,
                keyboardOptions = androidx.compose.foundation.text.KeyboardOptions(
                    keyboardType = androidx.compose.ui.text.input.KeyboardType.Number,
                ),
                modifier = Modifier.fillMaxWidth(),
            )
            Spacer(Modifier.height(8.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                OutlinedButton(onClick = onCancel) { Text("Cancel") }
                Button(onClick = {
                    onConfirm(partyText.ifBlank { null }, amountText.toDoubleOrNull())
                }) { Text("Confirm ✓") }
            }
        }
    }
}

/** Read-only P&L answer. */
@Composable
fun PnlAnswerBubble(summary: PnlSummary) {
    Card(
        Modifier.fillMaxWidth().padding(vertical = 4.dp),
        colors = CardDefaults.cardColors(),
    ) {
        Column(Modifier.padding(16.dp)) {
            Text("आज का हिसाब", fontWeight = FontWeight.Bold)
            Spacer(Modifier.height(8.dp))
            Text("बिक्री (Revenue): ₹${summary.grossRevenue.toLong()}")
            Text("मुनाफा (Profit): ₹${summary.grossProfit.toLong()}", color = AccentGreen)
            Text("नकद (Cash): ₹${summary.cashCollected.toLong()}")
            Text("बकाया (Outstanding): ₹${summary.totalOutstanding.toLong()}")
        }
    }
}

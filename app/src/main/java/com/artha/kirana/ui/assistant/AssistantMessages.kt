package com.artha.kirana.ui.assistant

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import com.artha.kirana.domain.model.PnlPeriod
import com.artha.kirana.domain.model.PnlSummary
import com.artha.kirana.domain.model.SaleEntry
import com.artha.kirana.ui.common.EditableEntryCard
import com.artha.kirana.ui.theme.Card
import com.artha.kirana.ui.theme.GhostButton
import com.artha.kirana.ui.theme.HazardWhite
import com.artha.kirana.ui.theme.Ink
import com.artha.kirana.ui.theme.Kicker
import com.artha.kirana.ui.theme.Mint
import com.artha.kirana.ui.theme.PrimaryButton
import com.artha.kirana.ui.theme.Rule
import com.artha.kirana.ui.theme.SectionTitle
import com.artha.kirana.ui.theme.Slate
import com.artha.kirana.ui.theme.TextMuted
import com.artha.kirana.ui.theme.TileYellow

@Composable
fun UserBubble(text: String) {
    Box(Modifier.fillMaxWidth(), contentAlignment = Alignment.CenterEnd) {
        Box(
            modifier = Modifier
                .padding(vertical = 4.dp)
                .background(Mint, RoundedCornerShape(18.dp))
                .padding(horizontal = 16.dp, vertical = 10.dp),
        ) {
            Text(
                text,
                style = MaterialTheme.typography.bodyMedium,
                color = Ink,
            )
        }
    }
}

@Composable
fun ReplyBubble(text: String) {
    Box(Modifier.fillMaxWidth(), contentAlignment = Alignment.CenterStart) {
        Box(
            modifier = Modifier
                .padding(vertical = 4.dp)
                .background(Slate, RoundedCornerShape(18.dp))
                .padding(horizontal = 16.dp, vertical = 10.dp),
        ) {
            Text(
                text,
                style = MaterialTheme.typography.bodyMedium,
                color = HazardWhite,
            )
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
    Card(modifier = Modifier.padding(vertical = 4.dp)) {
        Kicker("इसे दर्ज करें? · CONFIRM SALE")
        Spacer(Modifier.height(10.dp))
        Rule(color = Mint, thickness = 1)
        Spacer(Modifier.height(10.dp))
        edited.forEachIndexed { i, e ->
            EditableEntryCard(e) { updated -> edited = edited.toMutableList().also { it[i] = updated } }
            Spacer(Modifier.height(8.dp))
        }
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            GhostButton("Cancel", onClick = onCancel, modifier = Modifier.weight(1f))
            PrimaryButton("Confirm", onClick = { onConfirm(edited) }, modifier = Modifier.weight(1f))
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
    Card(modifier = Modifier.padding(vertical = 4.dp)) {
        Kicker("भुगतान दर्ज करें? · LOG PAYMENT")
        Spacer(Modifier.height(10.dp))
        Rule(color = TileYellow, thickness = 1)
        Spacer(Modifier.height(10.dp))
        OutlinedTextField(
            value = partyText,
            onValueChange = { partyText = it },
            label = { Text("Party · ग्राहक", color = TextMuted) },
            singleLine = true,
            modifier = Modifier.fillMaxWidth(),
        )
        Spacer(Modifier.height(8.dp))
        OutlinedTextField(
            value = amountText,
            onValueChange = { amountText = it },
            label = { Text("₹ Amount", color = TextMuted) },
            singleLine = true,
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
            modifier = Modifier.fillMaxWidth(),
        )
        Spacer(Modifier.height(12.dp))
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            GhostButton("Cancel", onClick = onCancel, modifier = Modifier.weight(1f))
            PrimaryButton("Confirm", onClick = {
                onConfirm(partyText.ifBlank { null }, amountText.toDoubleOrNull())
            }, modifier = Modifier.weight(1f))
        }
    }
}

/** Read-only P&L answer bubble. */
@Composable
fun PnlAnswerBubble(summary: PnlSummary) {
    Card(modifier = Modifier.padding(vertical = 4.dp)) {
        val header = when (summary.period) {
            PnlPeriod.TODAY -> "आज का हिसाब · TODAY"
            PnlPeriod.THIS_WEEK -> "इस हफ़्ते का हिसाब · THIS WEEK"
            PnlPeriod.THIS_MONTH -> "इस महीने का हिसाब · THIS MONTH"
        }
        Kicker(header)
        Spacer(Modifier.height(10.dp))
        Rule(color = Mint, thickness = 1)
        Spacer(Modifier.height(12.dp))
        // Revenue hero — Anton display
        Text(
            "₹${summary.grossRevenue.toLong()}",
            style = MaterialTheme.typography.displaySmall,
            color = Mint,
        )
        SectionTitle("GROSS REVENUE")
        Spacer(Modifier.height(12.dp))
        PnlRow("PROFIT · मुनाफा", "₹${summary.grossProfit.toLong()}", Mint)
        Spacer(Modifier.height(4.dp))
        PnlRow("CASH COLLECTED · नकद", "₹${summary.cashCollected.toLong()}", HazardWhite)
        Spacer(Modifier.height(4.dp))
        PnlRow("OUTSTANDING · बकाया", "₹${summary.totalOutstanding.toLong()}", TileYellow)
    }
}

@Composable
private fun PnlRow(label: String, value: String, valueColor: androidx.compose.ui.graphics.Color) {
    Row(
        Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            label.uppercase(),
            style = MaterialTheme.typography.labelSmall,
            color = TextMuted,
        )
        Text(
            value,
            style = MaterialTheme.typography.titleMedium,
            color = valueColor,
        )
    }
}

package com.artha.kirana.ui.common

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.Card
import androidx.compose.material3.FilterChip
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import com.artha.kirana.domain.model.SaleEntry

/** Editable card for one parsed [SaleEntry]. Shared by Sale Entry and the Assistant. */
@Composable
fun EditableEntryCard(entry: SaleEntry, onChange: (SaleEntry) -> Unit) {
    Card(Modifier.fillMaxWidth()) {
        Column(Modifier.padding(16.dp)) {
            OutlinedTextField(
                value = entry.item ?: "",
                onValueChange = { onChange(entry.copy(item = it.ifBlank { null })) },
                label = { Text("Item · वस्तु") },
                modifier = Modifier.fillMaxWidth(),
                singleLine = true,
            )
            Spacer(Modifier.height(8.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                OutlinedTextField(
                    value = entry.qty ?: "",
                    onValueChange = { onChange(entry.copy(qty = it.ifBlank { null })) },
                    label = { Text("Qty") },
                    modifier = Modifier.weight(1f),
                    singleLine = true,
                )
                OutlinedTextField(
                    value = entry.amount?.toLong()?.toString() ?: "",
                    onValueChange = { onChange(entry.copy(amount = it.toDoubleOrNull())) },
                    label = { Text("₹ Amount") },
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                    modifier = Modifier.weight(1f),
                    singleLine = true,
                )
            }
            Spacer(Modifier.height(8.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                listOf("cash", "credit", "repayment").forEach { t ->
                    FilterChip(
                        selected = entry.type == t,
                        onClick = { onChange(entry.copy(type = t)) },
                        label = { Text(t) },
                    )
                }
            }
            Spacer(Modifier.height(8.dp))
            OutlinedTextField(
                value = entry.party ?: "",
                onValueChange = { onChange(entry.copy(party = it.ifBlank { null })) },
                label = { Text("Party · ग्राहक (optional)") },
                modifier = Modifier.fillMaxWidth(),
                singleLine = true,
            )
        }
    }
}

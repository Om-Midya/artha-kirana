package com.artha.kirana.ui.inventory

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import com.artha.kirana.data.db.entity.ItemEntity
import com.artha.kirana.ui.theme.Canvas
import com.artha.kirana.ui.theme.GhostButton
import com.artha.kirana.ui.theme.HazardWhite
import com.artha.kirana.ui.theme.Kicker
import com.artha.kirana.ui.theme.Line
import com.artha.kirana.ui.theme.Mint
import com.artha.kirana.ui.theme.PrimaryButton
import com.artha.kirana.ui.theme.Rule
import com.artha.kirana.ui.theme.SectionTitle
import com.artha.kirana.ui.theme.Slate
import com.artha.kirana.ui.theme.TextMuted

/**
 * Add/edit/restock sheet. [existing] == null → add mode; otherwise edit mode with a restock field.
 * Emits the assembled item via [onSave]; restock qty (edit mode only) via [onRestock].
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AddItemSheet(
    existing: ItemEntity?,
    onDismiss: () -> Unit,
    onSave: (ItemEntity) -> Unit,
    onRestock: (ItemEntity, Double) -> Unit,
) {
    var name by remember { mutableStateOf(existing?.name ?: "") }
    var nameHi by remember { mutableStateOf(existing?.nameHi ?: "") }
    var unit by remember { mutableStateOf(existing?.unit ?: "piece") }
    var cost by remember { mutableStateOf(existing?.costPrice?.takeIf { it > 0 }?.toString() ?: "") }
    var sell by remember { mutableStateOf(existing?.sellPrice?.takeIf { it > 0 }?.toString() ?: "") }
    var threshold by remember { mutableStateOf(existing?.reorderThreshold?.takeIf { it > 0 }?.toString() ?: "") }
    var qty by remember { mutableStateOf(existing?.qtyInStock?.takeIf { it > 0 }?.toString() ?: "") }
    var restockAmt by remember { mutableStateOf("") }

    val num = KeyboardOptions(keyboardType = KeyboardType.Decimal, imeAction = ImeAction.Next)

    val fieldColors = OutlinedTextFieldDefaults.colors(
        focusedContainerColor = Slate,
        unfocusedContainerColor = Slate,
        disabledContainerColor = Slate,
        focusedBorderColor = Mint,
        unfocusedBorderColor = Line,
        focusedLabelColor = Mint,
        unfocusedLabelColor = TextMuted,
        focusedTextColor = HazardWhite,
        unfocusedTextColor = HazardWhite,
        cursorColor = Mint,
    )

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        containerColor = Canvas,
    ) {
        Column(
            Modifier
                .fillMaxWidth()
                .verticalScroll(rememberScrollState())
                .padding(start = 16.dp, end = 16.dp, bottom = 32.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Kicker(
                text = if (existing == null) "ADD ITEM" else "EDIT · ${existing.name.uppercase()}",
                color = Mint,
            )
            Rule(color = Mint)
            Spacer(Modifier.height(4.dp))

            SectionTitle("ITEM DETAILS")

            OutlinedTextField(
                value = name,
                onValueChange = { name = it },
                label = { Text("Name", style = MaterialTheme.typography.labelSmall, color = TextMuted) },
                modifier = Modifier.fillMaxWidth(),
                colors = fieldColors,
                shape = RoundedCornerShape(12.dp),
                textStyle = MaterialTheme.typography.bodyMedium,
            )
            OutlinedTextField(
                value = nameHi,
                onValueChange = { nameHi = it },
                label = { Text("Hindi name (optional)", style = MaterialTheme.typography.labelSmall, color = TextMuted) },
                modifier = Modifier.fillMaxWidth(),
                colors = fieldColors,
                shape = RoundedCornerShape(12.dp),
                textStyle = MaterialTheme.typography.bodyMedium,
            )
            OutlinedTextField(
                value = unit,
                onValueChange = { unit = it },
                label = { Text("Unit (kg/litre/piece/dozen)", style = MaterialTheme.typography.labelSmall, color = TextMuted) },
                modifier = Modifier.fillMaxWidth(),
                colors = fieldColors,
                shape = RoundedCornerShape(12.dp),
                textStyle = MaterialTheme.typography.bodyMedium,
            )

            Spacer(Modifier.height(2.dp))
            SectionTitle("STOCK & PRICING")

            OutlinedTextField(
                value = qty,
                onValueChange = { qty = it },
                label = { Text("Stock qty", style = MaterialTheme.typography.labelSmall, color = TextMuted) },
                keyboardOptions = num,
                modifier = Modifier.fillMaxWidth(),
                colors = fieldColors,
                shape = RoundedCornerShape(12.dp),
                textStyle = MaterialTheme.typography.bodyMedium,
            )
            OutlinedTextField(
                value = cost,
                onValueChange = { cost = it },
                label = { Text("Cost price ₹", style = MaterialTheme.typography.labelSmall, color = TextMuted) },
                keyboardOptions = num,
                modifier = Modifier.fillMaxWidth(),
                colors = fieldColors,
                shape = RoundedCornerShape(12.dp),
                textStyle = MaterialTheme.typography.bodyMedium,
            )
            OutlinedTextField(
                value = sell,
                onValueChange = { sell = it },
                label = { Text("Sell price ₹", style = MaterialTheme.typography.labelSmall, color = TextMuted) },
                keyboardOptions = num,
                modifier = Modifier.fillMaxWidth(),
                colors = fieldColors,
                shape = RoundedCornerShape(12.dp),
                textStyle = MaterialTheme.typography.bodyMedium,
            )
            OutlinedTextField(
                value = threshold,
                onValueChange = { threshold = it },
                label = { Text("Reorder threshold", style = MaterialTheme.typography.labelSmall, color = TextMuted) },
                keyboardOptions = num,
                modifier = Modifier.fillMaxWidth(),
                colors = fieldColors,
                shape = RoundedCornerShape(12.dp),
                textStyle = MaterialTheme.typography.bodyMedium,
            )

            if (existing != null) {
                Spacer(Modifier.height(2.dp))
                SectionTitle("RESTOCK")
                OutlinedTextField(
                    value = restockAmt,
                    onValueChange = { restockAmt = it },
                    label = { Text("Add qty to stock", style = MaterialTheme.typography.labelSmall, color = TextMuted) },
                    keyboardOptions = num,
                    modifier = Modifier.fillMaxWidth(),
                    colors = fieldColors,
                    shape = RoundedCornerShape(12.dp),
                    textStyle = MaterialTheme.typography.bodyMedium,
                )
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.End) {
                    GhostButton(
                        text = "Restock",
                        onClick = {
                            val add = restockAmt.toDoubleOrNull()
                            if (add != null && add > 0) onRestock(existing, add)
                        },
                    )
                }
            }

            Spacer(Modifier.height(4.dp))
            Row(
                Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                GhostButton(
                    text = "Cancel",
                    onClick = onDismiss,
                    modifier = Modifier.weight(1f),
                )
                PrimaryButton(
                    text = "Save",
                    enabled = name.isNotBlank(),
                    onClick = {
                        val item = (existing ?: ItemEntity(name = name)).copy(
                            name = name.trim(),
                            nameHi = nameHi.trim().ifBlank { null },
                            unit = unit.trim().ifBlank { "piece" },
                            qtyInStock = qty.toDoubleOrNull() ?: (existing?.qtyInStock ?: 0.0),
                            costPrice = cost.toDoubleOrNull() ?: 0.0,
                            sellPrice = sell.toDoubleOrNull() ?: 0.0,
                            reorderThreshold = threshold.toDoubleOrNull() ?: 0.0,
                        )
                        onSave(item)
                    },
                    modifier = Modifier.weight(1f),
                )
            }
        }
    }
}

package com.artha.kirana.ui.inventory

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.ui.unit.dp
import com.artha.kirana.data.db.entity.ItemEntity

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

    ModalBottomSheet(onDismissRequest = onDismiss) {
        Column(
            Modifier
                .fillMaxWidth()
                .verticalScroll(rememberScrollState())
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            Text(if (existing == null) "Add item" else "Edit ${existing.name}")
            OutlinedTextField(name, { name = it }, label = { Text("Name") }, modifier = Modifier.fillMaxWidth())
            OutlinedTextField(nameHi, { nameHi = it }, label = { Text("Hindi name (optional)") }, modifier = Modifier.fillMaxWidth())
            OutlinedTextField(unit, { unit = it }, label = { Text("Unit (kg/litre/piece/dozen)") }, modifier = Modifier.fillMaxWidth())
            OutlinedTextField(qty, { qty = it }, label = { Text("Stock qty") }, keyboardOptions = num, modifier = Modifier.fillMaxWidth())
            OutlinedTextField(cost, { cost = it }, label = { Text("Cost price ₹") }, keyboardOptions = num, modifier = Modifier.fillMaxWidth())
            OutlinedTextField(sell, { sell = it }, label = { Text("Sell price ₹") }, keyboardOptions = num, modifier = Modifier.fillMaxWidth())
            OutlinedTextField(threshold, { threshold = it }, label = { Text("Reorder threshold") }, keyboardOptions = num, modifier = Modifier.fillMaxWidth())

            if (existing != null) {
                OutlinedTextField(restockAmt, { restockAmt = it }, label = { Text("Restock: add qty") }, keyboardOptions = num, modifier = Modifier.fillMaxWidth())
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.End) {
                    TextButton(onClick = {
                        val add = restockAmt.toDoubleOrNull()
                        if (add != null && add > 0) onRestock(existing, add)
                    }) { Text("Restock") }
                }
            }

            Spacer(Modifier.height(4.dp))
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.End) {
                TextButton(onClick = onDismiss) { Text("Cancel") }
                Button(
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
                ) { Text("Save") }
            }
        }
    }
}

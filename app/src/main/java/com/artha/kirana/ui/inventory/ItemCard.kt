package com.artha.kirana.ui.inventory

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.artha.kirana.data.db.entity.ItemEntity
import com.artha.kirana.ui.theme.AccentRed
import com.artha.kirana.util.formatRupees

/** True when the item has a threshold set and has dropped below it. */
fun ItemEntity.isLowStock(): Boolean = reorderThreshold > 0 && qtyInStock < reorderThreshold

@Composable
fun ItemCard(item: ItemEntity, onClick: () -> Unit) {
    val low = item.isLowStock()
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick),
        colors = if (low) {
            CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.errorContainer)
        } else {
            CardDefaults.cardColors()
        },
    ) {
        Row(
            Modifier
                .fillMaxWidth()
                .padding(16.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Column(Modifier.weight(1f)) {
                Text(item.name, style = MaterialTheme.typography.bodyLarge, fontWeight = FontWeight.Medium)
                Text(
                    "${trimQty(item.qtyInStock)} ${item.unit} in stock" + if (low) " · low!" else "",
                    style = MaterialTheme.typography.bodySmall,
                    color = if (low) AccentRed else MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            Text(formatRupees(item.sellPrice), style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
        }
    }
}

/** "2" not "2.0"; "1.5" stays "1.5". */
fun trimQty(q: Double): String = if (q % 1.0 == 0.0) q.toLong().toString() else q.toString()
